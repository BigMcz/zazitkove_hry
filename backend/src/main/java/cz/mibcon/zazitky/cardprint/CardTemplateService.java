package cz.mibcon.zazitky.cardprint;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import cz.mibcon.zazitky.accesscode.AccessCodeAdminService;
import cz.mibcon.zazitky.accesscode.AccessCodeFormat;
import cz.mibcon.zazitky.cardprint.CardPdfRenderer.Graphic;
import cz.mibcon.zazitky.cardprint.CardPdfRenderer.PrintedCode;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.FileDto;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.Preview;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.SavedTemplate;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.Side;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.TemplateDto;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.TemplateRequest;
import cz.mibcon.zazitky.cardprint.CardTemplateFileRepository.FileInfo;
import cz.mibcon.zazitky.cardprint.domain.CardTemplate;
import cz.mibcon.zazitky.cardprint.domain.CardTemplateFile;
import cz.mibcon.zazitky.common.BadRequestException;
import cz.mibcon.zazitky.common.NotFoundException;
import cz.mibcon.zazitky.game.GameRepository;
import cz.mibcon.zazitky.game.domain.Game;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Card templates of the main games: uploaded graphics (each file stored once), versions that never change and
 * previews of the editor. Language versions share the cards of their main game.
 */
@Service
@Transactional
public class CardTemplateService {

    static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    static final double SIZE_TOLERANCE_MM = 0.5;
    static final double RATIO_TOLERANCE = 0.01;
    static final int MIN_DPI = 300;
    /** Sample of the editor preview, the code is not valid. */
    static final String SAMPLE_CODE = "ABCD2345";
    static final String SAMPLE_SERIAL = serialLabel(12, 42);

    private static final Locale CZECH = Locale.forLanguageTag("cs-CZ");

    private final GameRepository games;
    private final CardTemplateRepository templates;
    private final CardTemplateFileRepository files;
    private final CardPdfRenderer renderer;
    private final JdbcTemplate jdbc;

    public CardTemplateService(GameRepository games, CardTemplateRepository templates, CardTemplateFileRepository files,
            CardPdfRenderer renderer, JdbcTemplate jdbc) {
        this.games = games;
        this.templates = templates;
        this.files = files;
        this.renderer = renderer;
        this.jdbc = jdbc;
    }

    /** "D12 · 0042" printed on the card. */
    static String serialLabel(int batchNo, int serialNo) {
        return "D" + batchNo + " · " + String.format("%04d", serialNo);
    }

    /**
     * Stores an uploaded graphic of a card side; the same content is stored once. Its size is checked against the
     * template when it is saved, the template may still change.
     */
    public FileDto upload(MultipartFile upload) {
        byte[] content;
        try {
            content = upload.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (content.length == 0) {
            throw new BadRequestException("Soubor je prázdný.");
        }
        if (content.length > MAX_FILE_BYTES) {
            throw new BadRequestException("Soubor je větší než 20 MB.");
        }
        String contentType = detectType(content);
        if (contentType.equals("application/pdf")) {
            checkReadablePdf(content);
        } else {
            imageSize(content);
        }
        String sha256 = sha256(content);
        Optional<FileInfo> existing = files.findInfoBySha256(sha256);
        if (existing.isPresent()) {
            return toDto(existing.get());
        }
        CardTemplateFile file = new CardTemplateFile();
        file.setFileName(fileName(upload.getOriginalFilename(), contentType));
        file.setContentType(contentType);
        file.setSha256(sha256);
        file.setContent(content);
        files.saveAndFlush(file);
        return new FileDto(file.getId(), file.getFileName(), file.getContentType(), file.getSizeBytes());
    }

    @Transactional(readOnly = true)
    public TemplateDto latest(UUID gameId) {
        mainGame(gameId);
        return templates.findFirstByGameIdOrderByVersionDesc(gameId)
                .map(this::toDto)
                .orElseThrow(() -> new NotFoundException("Hra zatím nemá šablonu kartičky."));
    }

    @Transactional(readOnly = true)
    public List<TemplateDto> versions(UUID gameId) {
        mainGame(gameId);
        return toDtos(templates.findByGameIdOrderByVersionDesc(gameId));
    }

    /**
     * A new version; a missing graphic is taken from the previous version. A graphic of a wrong size is refused,
     * a low resolution and elements close to the trim are only warnings.
     */
    public SavedTemplate create(UUID gameId, TemplateRequest request) {
        mainGame(gameId);
        CardLayout layout = checkedLayout(request);
        Optional<CardTemplate> previous = templates.findFirstByGameIdOrderByVersionDesc(gameId);
        UUID frontId = fileId(request.frontFileId(), previous.map(CardTemplate::getFrontFileId))
                .orElseThrow(() -> new BadRequestException("Nahrajte grafiku přední strany."));
        UUID backId = fileId(request.backFileId(), previous.map(CardTemplate::getBackFileId))
                .orElseThrow(() -> new BadRequestException("Nahrajte grafiku zadní strany."));

        List<String> warnings = new ArrayList<>();
        for (GraphicCheck check : List.of(checkGraphic(layout, findFile(frontId), Side.FRONT),
                checkGraphic(layout, findFile(backId), Side.BACK))) {
            if (!check.errors().isEmpty()) {
                throw new BadRequestException(String.join(" ", check.errors()));
            }
            warnings.addAll(check.warnings());
        }
        warnings.addAll(renderer.safeZoneWarnings(layout));

        CardTemplate template = new CardTemplate();
        template.setGameId(gameId);
        template.setVersion(previous.map(version -> version.getVersion() + 1).orElse(1));
        template.setFrontFileId(frontId);
        template.setBackFileId(backId);
        template.setLayout(layout);
        template.setNote(request.note() == null || request.note().isBlank() ? null : request.note().strip());
        templates.saveAndFlush(template);

        Integer olderInProduction = jdbc.queryForObject("""
                SELECT count(*) FROM access_code_batch
                WHERE game_id = ? AND card_template_id IS NOT NULL AND card_template_id <> ? AND handed_over_at IS NULL
                """, Integer.class, gameId, template.getId());
        if (olderInProduction != null && olderInProduction > 0) {
            warnings.add("Dávky ve výrobě podle starší verze šablony: " + olderInProduction
                    + ". Na stránce tisku dávky je můžete přepnout na novou verzi.");
        }
        return new SavedTemplate(toDto(template), warnings);
    }

    /**
     * Preview of the editor before saving: a side with a sample code. Problems of the graphic are warnings here, the
     * editor shows them while the admin works.
     */
    @Transactional(readOnly = true)
    public Preview preview(UUID gameId, TemplateRequest request, Side side, String publicUrl) {
        mainGame(gameId);
        CardLayout layout = checkedLayout(request);
        Optional<CardTemplate> previous = templates.findFirstByGameIdOrderByVersionDesc(gameId);
        Optional<UUID> fileId = side == Side.FRONT
                ? fileId(request.frontFileId(), previous.map(CardTemplate::getFrontFileId))
                : fileId(request.backFileId(), previous.map(CardTemplate::getBackFileId));
        List<String> warnings = new ArrayList<>();
        Graphic graphic = null;
        if (fileId.isPresent()) {
            CardTemplateFile file = findFile(fileId.get());
            GraphicCheck check = checkGraphic(layout, file, side);
            warnings.addAll(check.errors());
            warnings.addAll(check.warnings());
            graphic = new Graphic(file.getContent(), file.getContentType());
        }
        PrintedCode code = null;
        if (side == Side.BACK) {
            warnings.addAll(renderer.safeZoneWarnings(layout));
            code = sampleCode(layout, SAMPLE_CODE, publicUrl);
        }
        return preview(layout, graphic, code, warnings);
    }

    Preview preview(CardLayout layout, Graphic graphic, PrintedCode code, List<String> warnings) {
        try {
            byte[] png = renderer.preview(layout, graphic, code);
            return new Preview("data:image/png;base64," + Base64.getEncoder().encodeToString(png), warnings);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static PrintedCode sampleCode(CardLayout layout, String code, String publicUrl) {
        return new PrintedCode(AccessCodeFormat.format(code),
                AccessCodeAdminService.codeLink(publicUrl, code), layout.hasSerial() ? SAMPLE_SERIAL : null);
    }

    /** The original uploaded file of a side of a version. */
    @Transactional(readOnly = true)
    public CardTemplateFile file(UUID templateId, Side side) {
        CardTemplate template = find(templateId);
        return findFile(side == Side.FRONT ? template.getFrontFileId() : template.getBackFileId());
    }

    CardTemplate find(UUID templateId) {
        return templates.findById(templateId).orElseThrow(() -> new NotFoundException("Šablona kartičky nebyla nalezena."));
    }

    Optional<CardTemplate> latestOf(UUID gameId) {
        return templates.findFirstByGameIdOrderByVersionDesc(gameId);
    }

    Graphic graphic(UUID fileId) {
        CardTemplateFile file = findFile(fileId);
        return new Graphic(file.getContent(), file.getContentType());
    }

    TemplateDto toDto(CardTemplate template) {
        return toDtos(List.of(template)).getFirst();
    }

    List<TemplateDto> toDtos(List<CardTemplate> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, FileDto> fileInfos = files.findInfoByIdIn(list.stream()
                        .flatMap(template -> Stream.of(template.getFrontFileId(), template.getBackFileId()))
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(FileInfo::getId, CardTemplateService::toDto));
        Map<UUID, Long> batchCounts = batchCounts(list.stream().map(CardTemplate::getId).toList());
        return list.stream()
                .map(template -> new TemplateDto(template.getId(), template.getGameId(), template.getVersion(),
                        template.layout(), fileInfos.get(template.getFrontFileId()),
                        fileInfos.get(template.getBackFileId()), template.getNote(), template.getCreatedAt(),
                        batchCounts.getOrDefault(template.getId(), 0L)))
                .toList();
    }

    private Map<UUID, Long> batchCounts(Collection<UUID> templateIds) {
        return jdbc.query("""
                        SELECT card_template_id, count(*) AS batches FROM access_code_batch
                        WHERE card_template_id = ANY (?) GROUP BY card_template_id
                        """,
                        (rs, row) -> Map.entry(rs.getObject("card_template_id", UUID.class), rs.getLong("batches")),
                        (Object) templateIds.toArray(UUID[]::new)).stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Game mainGame(UUID gameId) {
        Game game = games.findById(gameId).orElseThrow(() -> new NotFoundException("Hra nebyla nalezena."));
        if (game.getMainGame() != null) {
            throw new BadRequestException("Kartičky má hlavní hra, jazykové verze je sdílejí.");
        }
        return game;
    }

    private static CardLayout checkedLayout(TemplateRequest request) {
        CardLayout layout = request.layout();
        if (layout.serialIncomplete()) {
            throw new BadRequestException("U pořadového čísla vyplňte pozici i velikost písma, nebo ho vypněte.");
        }
        return layout;
    }

    private static Optional<UUID> fileId(UUID uploaded, Optional<UUID> previous) {
        return uploaded != null ? Optional.of(uploaded) : previous;
    }

    private CardTemplateFile findFile(UUID fileId) {
        return files.findById(fileId).orElseThrow(() -> new NotFoundException("Soubor s grafikou nebyl nalezen."));
    }

    /** Problems that prevent saving (wrong size) and warnings (more pages, low resolution). */
    record GraphicCheck(List<String> errors, List<String> warnings) {
    }

    /** The graphic must cover the trim and the bleed: a PDF of that size, an image of that aspect ratio. */
    GraphicCheck checkGraphic(CardLayout layout, CardTemplateFile file, Side side) {
        String label = side == Side.FRONT ? "Přední strana" : "Zadní strana";
        double width = layout.bleedWidthMm();
        double height = layout.bleedHeightMm();
        String expected = mm(width) + " × " + mm(height) + " mm (ořez " + mm(layout.widthMm()) + " × "
                + mm(layout.heightMm()) + " mm a spadávka " + mm(layout.bleedMm()) + " mm na každé straně)";
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (file.getContentType().equals("application/pdf")) {
            try (PDDocument pdf = Loader.loadPDF(file.getContent())) {
                PDPage page = pdf.getPage(0);
                PDRectangle box = page.getBleedBox();
                boolean turned = page.getRotation() % 180 != 0;
                double pageWidth = (turned ? box.getHeight() : box.getWidth()) / CardPdfRenderer.PT_PER_MM;
                double pageHeight = (turned ? box.getWidth() : box.getHeight()) / CardPdfRenderer.PT_PER_MM;
                if (Math.abs(pageWidth - width) > SIZE_TOLERANCE_MM || Math.abs(pageHeight - height) > SIZE_TOLERANCE_MM) {
                    errors.add(label + ": PDF má rozměr " + mm(pageWidth) + " × " + mm(pageHeight) + " mm, šablona potřebuje "
                            + expected + ".");
                }
                if (pdf.getNumberOfPages() > 1) {
                    warnings.add(label + ": PDF má " + pdf.getNumberOfPages() + " stran, použije se první.");
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            int[] size = imageSize(file.getContent());
            double ratio = (double) size[0] / size[1];
            if (Math.abs(ratio / (width / height) - 1) > RATIO_TOLERANCE) {
                errors.add(label + ": obrázek " + size[0] + " × " + size[1] + " px má jiný poměr stran, šablona potřebuje "
                        + expected + ".");
            }
            long dpi = Math.round(size[0] / (width / 25.4));
            if (dpi < MIN_DPI) {
                warnings.add(label + ": obrázek má jen " + dpi + " DPI, pro ostrý tisk je potřeba aspoň " + MIN_DPI
                        + " DPI (" + Math.round(width / 25.4 * MIN_DPI) + " px na šířku).");
            }
        }
        return new GraphicCheck(errors, warnings);
    }

    /** By the content, not by the name or the type the browser sent. */
    private static String detectType(byte[] content) {
        String head = new String(content, 0, Math.min(content.length, 1024), StandardCharsets.ISO_8859_1);
        if (head.contains("%PDF-")) {
            return "application/pdf";
        }
        if (content.length > 8 && (content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G') {
            return "image/png";
        }
        if (content.length > 3 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        throw new BadRequestException("Grafika musí být PDF, PNG nebo JPG.");
    }

    private static void checkReadablePdf(byte[] content) {
        try (PDDocument pdf = Loader.loadPDF(content)) {
            if (pdf.isEncrypted()) {
                throw new BadRequestException("PDF je zabezpečené, uložte ho prosím bez zabezpečení.");
            }
            if (pdf.getNumberOfPages() == 0) {
                throw new BadRequestException("PDF nemá žádnou stranu.");
            }
        } catch (InvalidPasswordException e) {
            throw new BadRequestException("PDF je chráněné heslem, uložte ho prosím bez zabezpečení.");
        } catch (IOException e) {
            throw new BadRequestException("Soubor se nepodařilo přečíst jako PDF.");
        }
    }

    /** Width and height in pixels, read from the header without decoding the image (works for CMYK JPEG too). */
    private static int[] imageSize(byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BadRequestException("Obrázek se nepodařilo přečíst.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BadRequestException("Obrázek se nepodařilo přečíst.");
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String fileName(String original, String contentType) {
        String name = original == null ? "" : original.replaceAll(".*[/\\\\]", "").strip();
        if (name.isEmpty()) {
            name = "grafika." + contentType.substring(contentType.indexOf('/') + 1);
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static FileDto toDto(FileInfo info) {
        return new FileDto(info.getId(), info.getFileName(), info.getContentType(), info.getSizeBytes());
    }

    /** 96 or 96,5 */
    static String mm(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.format(CZECH, "%.1f", rounded);
    }
}
