package cz.mibcon.zazitky.cardprint;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import cz.mibcon.zazitky.accesscode.AccessCodeAdminService;
import cz.mibcon.zazitky.accesscode.AccessCodeBatchRepository;
import cz.mibcon.zazitky.accesscode.AccessCodeFormat;
import cz.mibcon.zazitky.accesscode.AccessCodeRepository;
import cz.mibcon.zazitky.accesscode.domain.AccessCode;
import cz.mibcon.zazitky.accesscode.domain.AccessCodeBatch;
import cz.mibcon.zazitky.cardprint.CardPdfRenderer.PrintedCode;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.ExportDto;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.ExportRequest;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.Preview;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.PrintStatus;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.Side;
import cz.mibcon.zazitky.cardprint.domain.CardTemplate;
import cz.mibcon.zazitky.common.BadRequestException;
import cz.mibcon.zazitky.common.InvalidStateException;
import cz.mibcon.zazitky.common.NotFoundException;
import cz.mibcon.zazitky.game.domain.Game;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Printing a batch of codes: the batch is tied to a version of the card template of its game (the first export takes
 * the latest one), every download is audited. The PDFs are generated on request and not kept; the back sides hold
 * codes that work once the batch is handed over.
 */
@Service
@Transactional
public class CardPrintService {

    /** Kinds of downloads in the audit (card_print_export.kind). */
    public enum ExportKind {
        FRONT,
        BACK,
        ZIP
    }

    /** A generated file in a temporary file, deleted after it is sent. */
    public record PrintFile(Path path, String fileName, String contentType) {
    }

    static final long WAIT_SECONDS = 30;

    /** One generation at a time: a batch of 5 000 cards takes seconds and the server memory is small. */
    private final Semaphore generating = new Semaphore(1);

    private final AccessCodeBatchRepository batches;
    private final AccessCodeRepository codes;
    private final AccessCodeAdminService accessCodes;
    private final CardTemplateService templates;
    private final CardPdfRenderer renderer;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public CardPrintService(AccessCodeBatchRepository batches, AccessCodeRepository codes,
            AccessCodeAdminService accessCodes, CardTemplateService templates, CardPdfRenderer renderer,
            JdbcTemplate jdbc, Clock clock) {
        this.batches = batches;
        this.codes = codes;
        this.accessCodes = accessCodes;
        this.templates = templates;
        this.renderer = renderer;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PrintStatus status(UUID batchId) {
        AccessCodeBatch batch = findBatch(batchId);
        Game game = batch.getGame();
        List<ExportDto> exports = jdbc.query("""
                        SELECT e.kind, e.serials, e.codes_count, e.crop_marks, e.username, t.version, e.created_at
                        FROM card_print_export e JOIN card_template t ON t.id = e.card_template_id
                        WHERE e.batch_id = ?
                        ORDER BY e.created_at DESC
                        LIMIT 20
                        """,
                (rs, row) -> new ExportDto(rs.getString("kind"), rs.getString("serials"), rs.getInt("codes_count"),
                        rs.getBoolean("crop_marks"), rs.getString("username"), rs.getInt("version"),
                        rs.getTimestamp("created_at").toInstant()),
                batchId);
        String sharedCode = batch.isMultiSession()
                ? codes.findByBatchIdOrderBySerialNo(batchId).stream().findFirst()
                        .map(code -> AccessCodeFormat.format(code.getCode())).orElse(null)
                : null;
        return new PrintStatus(batch.getId(), batch.getBatchNo(), game.getId(), game.getTitle(),
                batch.getPartner().getId(), batch.getPartner().getName(), batch.getQuantity(), batch.isMultiSession(),
                batch.isFree(), sharedCode, batch.getHandedOverAt(), batch.getPrintedAt(),
                batch.getCardTemplateId() == null ? null : templates.toDto(templates.find(batch.getCardTemplateId())),
                templates.latestOf(game.getId()).map(templates::toDto).orElse(null),
                exports);
    }

    /** Prints the batch with another version, only while it is in production (the printed cards must match). */
    public PrintStatus bind(UUID batchId, UUID templateId) {
        AccessCodeBatch batch = findBatch(batchId);
        CardTemplate template = templates.find(templateId);
        if (!template.getGameId().equals(batch.getGame().getId())) {
            throw new BadRequestException("Šablona patří k jiné hře.");
        }
        if (batch.getHandedOverAt() != null && !templateId.equals(batch.getCardTemplateId())) {
            throw new InvalidStateException("Dávka už je předaná, dotisk musí vypadat jako vytištěné kartičky.");
        }
        batch.setCardTemplateId(templateId);
        batches.flush();
        return status(batchId);
    }

    /** A side of the card with the first code of the batch, by the version the batch is printed with. */
    @Transactional(readOnly = true)
    public Preview preview(UUID batchId, Side side, String publicUrl) {
        AccessCodeBatch batch = findBatch(batchId);
        CardTemplate template = templateOf(batch);
        CardLayout layout = template.layout();
        PrintedCode code = null;
        if (side == Side.BACK) {
            List<AccessCode> first = codes.findByBatchIdOrderBySerialNo(batchId).stream().limit(1).toList();
            code = first.isEmpty() ? null : printed(batch, layout, first.getFirst(), publicUrl);
        }
        return templates.preview(layout,
                templates.graphic(side == Side.FRONT ? template.getFrontFileId() : template.getBackFileId()), code,
                List.of());
    }

    /**
     * Print files of a batch; a batch without a version gets the latest one. The back sides (also in the ZIP) mark
     * the batch as printed.
     */
    public PrintFile export(UUID batchId, ExportKind kind, ExportRequest request, String username, String publicUrl) {
        AccessCodeBatch batch = findBatch(batchId);
        CardTemplate template = templateOf(batch);
        if (batch.getCardTemplateId() == null) {
            batch.setCardTemplateId(template.getId());
        }
        CardLayout layout = template.layout();
        List<AccessCode> batchCodes = codes.findByBatchIdOrderBySerialNo(batchId);
        SerialSelection selection = kind == ExportKind.BACK && request.serials() != null && !request.serials().isBlank()
                ? SerialSelection.parse(request.serials(), batchCodes.size())
                : null;
        List<PrintedCode> selected = batchCodes.stream()
                .filter(code -> selection == null || selection.serials().contains(code.getSerialNo()))
                .map(code -> printed(batch, layout, code, publicUrl))
                .toList();
        // A demo code goes on as many cards as needed, all the same
        List<PrintedCode> printed = batch.isMultiSession() && request.copies() != null && !selected.isEmpty()
                ? Collections.nCopies(request.copies(), selected.getFirst())
                : selected;
        if (kind != ExportKind.FRONT && batch.getPrintedAt() == null) {
            batch.setPrintedAt(clock.instant());
        }
        batches.flush();
        jdbc.update("""
                INSERT INTO card_print_export (batch_id, card_template_id, kind, serials, codes_count, crop_marks, username)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, batchId, template.getId(), kind.name(), selection == null ? null : selection.canonical(),
                kind == ExportKind.FRONT ? 0 : printed.size(), request.withCropMarks(), username);

        String name = "storyhold_" + batch.getGame().getSlug() + "_D" + batch.getBatchNo();
        String frontName = name + "_predni.pdf";
        String backName = name + (selection == null ? "_zadni_" : "_dotisk_") + printed.size() + "ks.pdf";
        return generate(switch (kind) {
            case FRONT -> new PrintFile(null, frontName, "application/pdf");
            case BACK -> new PrintFile(null, backName, "application/pdf");
            case ZIP -> new PrintFile(null, name + ".zip", "application/zip");
        }, out -> {
            switch (kind) {
                case FRONT -> renderer.front(layout, templates.graphic(template.getFrontFileId()), request.withCropMarks(), out);
                case BACK -> renderer.back(layout, templates.graphic(template.getBackFileId()), printed,
                        request.withCropMarks(), out);
                case ZIP -> {
                    try (ZipOutputStream zip = new ZipOutputStream(out)) {
                        zip.putNextEntry(new ZipEntry(frontName));
                        renderer.front(layout, templates.graphic(template.getFrontFileId()), request.withCropMarks(), zip);
                        zip.closeEntry();
                        zip.putNextEntry(new ZipEntry(backName));
                        renderer.back(layout, templates.graphic(template.getBackFileId()), printed,
                                request.withCropMarks(), zip);
                        zip.closeEntry();
                        zip.putNextEntry(new ZipEntry("codes.csv"));
                        zip.write(accessCodes.exportCsv(batchId, publicUrl, true).getBytes(StandardCharsets.UTF_8));
                        zip.closeEntry();
                    }
                }
            }
        });
    }

    /**
     * A4 with both sides of a version in real size and a random code in the right format, not stored: its QR code
     * opens the app with the message about an invalid code, enough to check it reads and leads to the right address.
     */
    @Transactional(readOnly = true)
    public PrintFile testPrint(UUID templateId, String publicUrl) {
        CardTemplate template = templates.find(templateId);
        CardLayout layout = template.layout();
        PrintedCode sample = CardTemplateService.sampleCode(layout, AccessCodeFormat.generate(), publicUrl);
        String slug = jdbc.queryForObject("SELECT slug FROM game WHERE id = ?", String.class, template.getGameId());
        return generate(new PrintFile(null, "storyhold_" + slug + "_sablona_v" + template.getVersion() + "_test_A4.pdf",
                        "application/pdf"),
                out -> renderer.testPrint(layout, templates.graphic(template.getFrontFileId()),
                        templates.graphic(template.getBackFileId()), sample, out));
    }

    private PrintedCode printed(AccessCodeBatch batch, CardLayout layout, AccessCode code, String publicUrl) {
        return new PrintedCode(AccessCodeFormat.format(code.getCode()),
                AccessCodeAdminService.codeLink(publicUrl, code.getCode()),
                layout.hasSerial() ? CardTemplateService.serialLabel(batch.getBatchNo(), code.getSerialNo()) : null);
    }

    private CardTemplate templateOf(AccessCodeBatch batch) {
        if (batch.getCardTemplateId() != null) {
            return templates.find(batch.getCardTemplateId());
        }
        return templates.latestOf(batch.getGame().getId())
                .orElseThrow(() -> new InvalidStateException("Hra nemá šablonu kartičky. Vytvořte ji v editoru kartičky."));
    }

    private AccessCodeBatch findBatch(UUID batchId) {
        return batches.findById(batchId).orElseThrow(() -> new NotFoundException("Dávka nebyla nalezena."));
    }

    private interface Writer {
        void write(OutputStream out) throws IOException;
    }

    /** Writes the file into a temporary file, so that a failure does not send half a PDF. */
    private PrintFile generate(PrintFile target, Writer writer) {
        try {
            if (!generating.tryAcquire(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new PrintBusyException();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PrintBusyException();
        }
        Path path = null;
        try {
            path = Files.createTempFile("storyhold-print-", ".tmp");
            try (OutputStream out = Files.newOutputStream(path)) {
                writer.write(out);
            }
            return new PrintFile(path, target.fileName(), target.contentType());
        } catch (IOException | RuntimeException e) {
            deleteQuietly(path);
            throw e instanceof IOException io ? new UncheckedIOException(io) : (RuntimeException) e;
        } finally {
            generating.release();
        }
    }

    static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // a leftover temporary file, the system cleans it up
        }
    }
}
