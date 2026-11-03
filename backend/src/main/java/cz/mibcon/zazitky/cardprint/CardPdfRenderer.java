package cz.mibcon.zazitky.cardprint;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import cz.mibcon.zazitky.cardprint.domain.CodeAlign;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

/**
 * Print PDFs of the cards, without the database: a template (layout and graphics) and codes in, a PDF out.
 * A page is one card of the size trim + bleed (plus a margin for crop marks); the graphic of a side is put into
 * the document once and every page refers to it, an uploaded PDF stays vector. The QR code is drawn as vector
 * rectangles, black is pure CMYK black so that the printer does not mix it from four inks.
 */
@Component
public class CardPdfRenderer {

    static final float PT_PER_MM = 72f / 25.4f;
    /** Margin around the bleed for crop marks. */
    static final double CROP_MARGIN_MM = 8;
    static final double CROP_OFFSET_MM = 3;
    static final double CROP_LENGTH_MM = 5;
    static final float PREVIEW_DPI = 150;
    static final int QR_QUIET_ZONE = 4;

    /** Liberation Sans (SIL Open Font License) shipped with PDFBox; the code alphabet has no easily confused characters. */
    private static final String FONT = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";
    private static final byte[] FONT_BYTES = readFont();

    /** Uploaded graphic of one side: PDF, PNG or JPG. */
    public record Graphic(byte[] content, String contentType) {
    }

    /**
     * What is printed on the back of one card.
     *
     * @param code   with the dash, ABCD-2345
     * @param link   content of the QR code
     * @param serial "D12 · 0042"; null = not printed
     */
    public record PrintedCode(String code, String link, String serial) {
    }

    /** One page with the front side, the same for all cards. */
    public void front(CardLayout layout, Graphic graphic, boolean cropMarks, OutputStream out) throws IOException {
        try (Canvas canvas = new Canvas()) {
            canvas.addCard(layout, canvas.embed(graphic), null, cropMarks);
            canvas.doc.save(out);
        }
    }

    /** One page per code with the back side. */
    public void back(CardLayout layout, Graphic graphic, List<PrintedCode> codes, boolean cropMarks, OutputStream out)
            throws IOException {
        try (Canvas canvas = new Canvas()) {
            Drawable background = canvas.embed(graphic);
            for (PrintedCode code : codes) {
                canvas.addCard(layout, background, code, cropMarks);
            }
            canvas.doc.save(out);
        }
    }

    /**
     * PNG of one side with the trim, the bleed (lighter, it is cut off) and the safe zone marked.
     *
     * @param graphic null = white card (the editor before a graphic is uploaded)
     * @param code    null = front side
     */
    public byte[] preview(CardLayout layout, Graphic graphic, PrintedCode code) throws IOException {
        try (Canvas canvas = new Canvas()) {
            canvas.addCard(layout, graphic == null ? null : canvas.embed(graphic), code, false);
            canvas.drawGuides(layout);
            // Rendered from the saved PDF: the font subset is embedded only when saving, without it PDFBox would
            // look for a system font
            ByteArrayOutputStream pdf = new ByteArrayOutputStream();
            canvas.doc.save(pdf);
            try (PDDocument saved = Loader.loadPDF(pdf.toByteArray())) {
                BufferedImage image = new PDFRenderer(saved).renderImageWithDPI(0, PREVIEW_DPI, ImageType.RGB);
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(image, "png", png);
                return png.toByteArray();
            }
        }
    }

    /**
     * A4 to check the scale and the QR code on an ordinary printer: both sides in real size with crop marks, a 50 mm
     * scale and instructions. Cards too big for one A4 together get a page each.
     */
    public void testPrint(CardLayout layout, Graphic front, Graphic back, PrintedCode sample, OutputStream out)
            throws IOException {
        try (Canvas canvas = new Canvas()) {
            new TestSheet(canvas, layout).draw(canvas.embed(front), canvas.embed(back), sample);
            canvas.doc.save(out);
        }
    }

    /**
     * Elements of the back closer to the trim than the safe zone; the code and the serial number are measured with
     * the widest code of the alphabet.
     */
    public List<String> safeZoneWarnings(CardLayout layout) {
        List<String> warnings = new ArrayList<>();
        double safe = CardLayout.SAFE_ZONE_MM;
        if (outside(layout, layout.qrXMm(), layout.qrYMm(), layout.qrXMm() + layout.qrSizeMm(),
                layout.qrYMm() + layout.qrSizeMm())) {
            warnings.add("QR kód leží blíž než " + (int) safe + " mm od okraje kartičky, při řezání ho může nůž oříznout.");
        }
        try (PDDocument doc = new PDDocument()) {
            PDFont font = PDType0Font.load(doc, new ByteArrayInputStream(FONT_BYTES));
            if (textOutside(layout, font, "WWWW-WWWW", layout.codeXMm(), layout.codeYMm(), layout.codeFontPt())) {
                warnings.add("Kód leží blíž než " + (int) safe + " mm od okraje kartičky nebo z ní přečnívá.");
            }
            if (layout.hasSerial()
                    && textOutside(layout, font, "D9999 · 9999", layout.serialXMm(), layout.serialYMm(), layout.serialFontPt())) {
                warnings.add("Pořadové číslo leží blíž než " + (int) safe + " mm od okraje kartičky nebo z ní přečnívá.");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return warnings;
    }

    private static boolean textOutside(CardLayout layout, PDFont font, String text, double xMm, double baselineMm,
            double sizePt) throws IOException {
        double widthMm = font.getStringWidth(text) / 1000 * sizePt / PT_PER_MM;
        double capHeightMm = font.getFontDescriptor().getCapHeight() / 1000 * sizePt / PT_PER_MM;
        double left = switch (layout.codeAlign()) {
            case LEFT -> xMm;
            case CENTER -> xMm - widthMm / 2;
            case RIGHT -> xMm - widthMm;
        };
        return outside(layout, left, baselineMm - capHeightMm, left + widthMm, baselineMm);
    }

    private static boolean outside(CardLayout layout, double left, double top, double right, double bottom) {
        double safe = CardLayout.SAFE_ZONE_MM;
        return left < safe || top < safe || right > layout.widthMm() - safe || bottom > layout.heightMm() - safe;
    }

    static float mm(double millimetres) {
        return (float) (millimetres * PT_PER_MM);
    }

    private static byte[] readFont() {
        try (InputStream font = CardPdfRenderer.class.getResourceAsStream(FONT)) {
            if (font == null) {
                throw new IllegalStateException("Font " + FONT + " is missing");
            }
            return font.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A graphic put into the document, drawn over a rectangle (points, from the bottom-left corner of the page). */
    private interface Drawable {
        void draw(PDPageContentStream stream, float x, float y, float width, float height) throws IOException;
    }

    /** The document being built; keeps the uploaded PDFs open until it is saved, its pages refer to them. */
    private static final class Canvas implements Closeable {

        final PDDocument doc = new PDDocument(IOUtils.createTempFileOnlyStreamCache());
        private final List<PDDocument> sources = new ArrayList<>();
        private final Map<Graphic, Drawable> embedded = new IdentityHashMap<>();
        private PDFont font;

        PDFont font() throws IOException {
            if (font == null) {
                font = PDType0Font.load(doc, new ByteArrayInputStream(FONT_BYTES));
            }
            return font;
        }

        Drawable embed(Graphic graphic) throws IOException {
            Drawable drawable = embedded.get(graphic);
            if (drawable == null) {
                drawable = "application/pdf".equals(graphic.contentType()) ? embedPdf(graphic) : embedImage(graphic);
                embedded.put(graphic, drawable);
            }
            return drawable;
        }

        /** The first page as a form XObject (stays vector); its bleed box is stretched over the target. */
        private Drawable embedPdf(Graphic graphic) throws IOException {
            PDDocument source = Loader.loadPDF(graphic.content());
            sources.add(source);
            PDRectangle box = source.getPage(0).getBleedBox();
            PDFormXObject form = new LayerUtility(doc).importPageAsForm(source, 0);
            // The form may move or turn the page (crop box, rotation): where the bleed box ends up
            Matrix matrix = form.getMatrix();
            Point2D.Float a = matrix.transformPoint(box.getLowerLeftX(), box.getLowerLeftY());
            Point2D.Float b = matrix.transformPoint(box.getUpperRightX(), box.getUpperRightY());
            float minX = Math.min(a.x, b.x);
            float minY = Math.min(a.y, b.y);
            float boxWidth = Math.abs(b.x - a.x);
            float boxHeight = Math.abs(b.y - a.y);
            return (stream, x, y, width, height) -> {
                stream.saveGraphicsState();
                stream.addRect(x, y, width, height);
                stream.clip();
                stream.transform(Matrix.getTranslateInstance(x, y));
                stream.transform(Matrix.getScaleInstance(width / boxWidth, height / boxHeight));
                stream.transform(Matrix.getTranslateInstance(-minX, -minY));
                stream.drawForm(form);
                stream.restoreGraphicsState();
            };
        }

        private Drawable embedImage(Graphic graphic) throws IOException {
            PDImageXObject image = PDImageXObject.createFromByteArray(doc, graphic.content(), "graphic");
            return (stream, x, y, width, height) -> stream.drawImage(image, x, y, width, height);
        }

        /** A page with one card: the bleed box filled with the graphic, the code on the back. */
        void addCard(CardLayout layout, Drawable background, PrintedCode code, boolean cropMarks) throws IOException {
            float margin = cropMarks ? mm(CROP_MARGIN_MM) : 0;
            float bleedWidth = mm(layout.bleedWidthMm());
            float bleedHeight = mm(layout.bleedHeightMm());
            PDPage page = new PDPage(new PDRectangle(bleedWidth + 2 * margin, bleedHeight + 2 * margin));
            page.setBleedBox(new PDRectangle(margin, margin, bleedWidth, bleedHeight));
            float bleed = mm(layout.bleedMm());
            page.setTrimBox(new PDRectangle(margin + bleed, margin + bleed, mm(layout.widthMm()), mm(layout.heightMm())));
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                drawCard(stream, layout, background, code, margin, margin);
                if (cropMarks) {
                    drawCropMarks(stream, layout, margin + bleed, margin + bleed);
                }
            }
        }

        /** The card with its bleed box from (x, y) in points. */
        void drawCard(PDPageContentStream stream, CardLayout layout, Drawable background, PrintedCode code, float x,
                float y) throws IOException {
            if (background != null) {
                background.draw(stream, x, y, mm(layout.bleedWidthMm()), mm(layout.bleedHeightMm()));
            }
            if (code == null) {
                return;
            }
            // Positions of the template are from the top-left corner of the trim
            float trimLeft = x + mm(layout.bleedMm());
            float trimTop = y + mm(layout.bleedMm() + layout.heightMm());
            drawQr(stream, code.link(), trimLeft + mm(layout.qrXMm()), trimTop - mm(layout.qrYMm()),
                    mm(layout.qrSizeMm()), layout.qrWhiteBox());
            drawText(stream, code.code(), trimLeft + mm(layout.codeXMm()), trimTop - mm(layout.codeYMm()),
                    layout.codeFontPt().floatValue(), layout.codeAlign(), layout.codeColor());
            if (code.serial() != null && layout.hasSerial()) {
                drawText(stream, code.serial(), trimLeft + mm(layout.serialXMm()), trimTop - mm(layout.serialYMm()),
                        layout.serialFontPt().floatValue(), layout.codeAlign(), layout.codeColor());
            }
        }

        /** Runs of dark modules in a row as one rectangle; the size includes the quiet zone. */
        private void drawQr(PDPageContentStream stream, String content, float left, float top, float size,
                boolean whiteBox) throws IOException {
            BitMatrix matrix;
            try {
                matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                        EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                        EncodeHintType.MARGIN, QR_QUIET_ZONE,
                        EncodeHintType.CHARACTER_SET, "UTF-8"));
            } catch (WriterException e) {
                throw new IllegalArgumentException("QR code cannot hold " + content, e);
            }
            int modules = matrix.getWidth();
            float module = size / modules;
            if (whiteBox) {
                stream.setNonStrokingColor(0f, 0f, 0f, 0f);
                stream.addRect(left, top - size, size, size);
                stream.fill();
            }
            stream.setNonStrokingColor(0f, 0f, 0f, 1f);
            for (int row = 0; row < modules; row++) {
                int column = 0;
                while (column < modules) {
                    if (!matrix.get(column, row)) {
                        column++;
                        continue;
                    }
                    int start = column;
                    while (column < modules && matrix.get(column, row)) {
                        column++;
                    }
                    stream.addRect(left + start * module, top - (row + 1) * module, (column - start) * module, module);
                }
            }
            stream.fill();
        }

        void drawText(PDPageContentStream stream, String text, float x, float baseline, float sizePt, CodeAlign align,
                String color) throws IOException {
            PDFont textFont = font();
            float width = textFont.getStringWidth(text) / 1000 * sizePt;
            float left = switch (align) {
                case LEFT -> x;
                case CENTER -> x - width / 2;
                case RIGHT -> x - width;
            };
            if ("#000000".equals(color)) {
                stream.setNonStrokingColor(0f, 0f, 0f, 1f);
            } else {
                stream.setNonStrokingColor(Color.decode(color));
            }
            stream.beginText();
            stream.setFont(textFont, sizePt);
            stream.newLineAtOffset(left, baseline);
            stream.showText(text);
            stream.endText();
        }

        /** Hairlines in all four inks (registration) at each corner of the trim, outside the bleed. */
        void drawCropMarks(PDPageContentStream stream, CardLayout layout, float trimLeft, float trimBottom)
                throws IOException {
            float offset = mm(Math.max(CROP_OFFSET_MM, layout.bleedMm()));
            float length = mm(CROP_LENGTH_MM);
            float right = trimLeft + mm(layout.widthMm());
            float top = trimBottom + mm(layout.heightMm());
            stream.setLineWidth(0.25f);
            stream.setStrokingColor(1f, 1f, 1f, 1f);
            for (float y : new float[] {trimBottom, top}) {
                line(stream, trimLeft - offset - length, y, trimLeft - offset, y);
                line(stream, right + offset, y, right + offset + length, y);
            }
            for (float x : new float[] {trimLeft, right}) {
                line(stream, x, trimBottom - offset - length, x, trimBottom - offset);
                line(stream, x, top + offset, x, top + offset + length);
            }
            stream.stroke();
        }

        private static void line(PDPageContentStream stream, float x1, float y1, float x2, float y2) throws IOException {
            stream.moveTo(x1, y1);
            stream.lineTo(x2, y2);
        }

        /** Preview only: the bleed lightened, the trim as a line and the safe zone dashed. */
        void drawGuides(CardLayout layout) throws IOException {
            PDPage page = doc.getPage(0);
            float width = page.getMediaBox().getWidth();
            float height = page.getMediaBox().getHeight();
            float bleed = mm(layout.bleedMm());
            float safe = mm(CardLayout.SAFE_ZONE_MM);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND,
                    true, true)) {
                PDExtendedGraphicsState translucent = new PDExtendedGraphicsState();
                translucent.setNonStrokingAlphaConstant(0.55f);
                stream.saveGraphicsState();
                stream.setGraphicsStateParameters(translucent);
                stream.setNonStrokingColor(Color.WHITE);
                stream.addRect(0, 0, width, bleed);
                stream.addRect(0, height - bleed, width, bleed);
                stream.addRect(0, bleed, bleed, height - 2 * bleed);
                stream.addRect(width - bleed, bleed, bleed, height - 2 * bleed);
                stream.fill();
                stream.restoreGraphicsState();

                stream.setLineWidth(0.75f);
                stream.setStrokingColor(new Color(0xD0, 0x00, 0x7A));
                stream.addRect(bleed, bleed, width - 2 * bleed, height - 2 * bleed);
                stream.stroke();
                stream.setStrokingColor(new Color(0x00, 0x8C, 0xC8));
                stream.setLineDashPattern(new float[] {3, 2}, 0);
                stream.addRect(bleed + safe, bleed + safe, width - 2 * (bleed + safe), height - 2 * (bleed + safe));
                stream.stroke();
            }
        }

        @Override
        public void close() throws IOException {
            try {
                doc.close();
            } finally {
                for (PDDocument source : sources) {
                    source.close();
                }
            }
        }
    }

    /** A4 portrait for the test print. */
    private static final class TestSheet {

        private static final float MARGIN = mm(15);
        private static final float LINE = 14;

        private final Canvas canvas;
        private final CardLayout layout;
        private final float cardWidth;
        private final float cardHeight;

        TestSheet(Canvas canvas, CardLayout layout) {
            this.canvas = canvas;
            this.layout = layout;
            // A card with its bleed and the room for crop marks
            this.cardWidth = mm(layout.bleedWidthMm() + 2 * CROP_MARGIN_MM);
            this.cardHeight = mm(layout.bleedHeightMm() + 2 * CROP_MARGIN_MM);
        }

        void draw(Drawable front, Drawable back, PrintedCode sample) throws IOException {
            float headerHeight = 5 * LINE + mm(10);
            float labelHeight = LINE + 4;
            boolean together = headerHeight + 2 * (labelHeight + cardHeight) + 2 * MARGIN <= PDRectangle.A4.getHeight();
            PDPage page = newPage();
            try (PDPageContentStream stream = new PDPageContentStream(canvas.doc, page)) {
                float y = header(stream, sample);
                y = card(stream, "Přední strana", front, null, y - labelHeight);
                if (together) {
                    card(stream, "Zadní strana", back, sample, y - labelHeight);
                }
            }
            if (!together) {
                PDPage second = newPage();
                try (PDPageContentStream stream = new PDPageContentStream(canvas.doc, second)) {
                    card(stream, "Zadní strana", back, sample, PDRectangle.A4.getHeight() - MARGIN - labelHeight);
                }
            }
        }

        private PDPage newPage() {
            PDPage page = new PDPage(PDRectangle.A4);
            canvas.doc.addPage(page);
            return page;
        }

        /** Instructions and the 50 mm scale; returns the y below them. */
        private float header(PDPageContentStream stream, PrintedCode sample) throws IOException {
            float y = PDRectangle.A4.getHeight() - MARGIN - LINE;
            text(stream, "Testovací tisk kartičky", 14, y);
            y -= LINE * 1.4f;
            text(stream, "Tiskněte v měřítku 100 % (bez přizpůsobení stránce). Úsečka níže musí měřit přesně 50 mm.", 9, y);
            y -= LINE;
            text(stream, "Ukázkový kód " + sample.code() + " nefunguje: po načtení QR se otevře aplikace s hláškou o neplatném kódu.", 9, y);
            y -= LINE;
            text(stream, "Ořez " + number(layout.widthMm()) + " × " + number(layout.heightMm()) + " mm, spadávka "
                    + number(layout.bleedMm()) + " mm.", 9, y);
            y -= mm(6);
            float scaleLeft = MARGIN;
            float scaleRight = MARGIN + mm(50);
            stream.setLineWidth(0.5f);
            stream.setStrokingColor(0f, 0f, 0f, 1f);
            line(stream, scaleLeft, y, scaleRight, y);
            for (int tick = 0; tick <= 50; tick += 10) {
                float x = scaleLeft + mm(tick);
                line(stream, x, y, x, y + (tick % 50 == 0 ? mm(3) : mm(1.5)));
            }
            stream.stroke();
            text(stream, "50 mm", 9, scaleRight + mm(3), y);
            return y - mm(4);
        }

        /** Label and the card with crop marks below it; returns the y below the card. */
        private float card(PDPageContentStream stream, String label, Drawable graphic, PrintedCode code, float labelY)
                throws IOException {
            text(stream, label, 10, labelY);
            float bottom = labelY - 4 - cardHeight;
            float x = MARGIN + mm(CROP_MARGIN_MM);
            float y = bottom + mm(CROP_MARGIN_MM);
            canvas.drawCard(stream, layout, graphic, code, x, y);
            float bleed = mm(layout.bleedMm());
            canvas.drawCropMarks(stream, layout, x + bleed, y + bleed);
            return bottom;
        }

        private void text(PDPageContentStream stream, String text, float size, float y) throws IOException {
            text(stream, text, size, MARGIN, y);
        }

        private void text(PDPageContentStream stream, String text, float size, float x, float y) throws IOException {
            canvas.drawText(stream, text, x, y, size, CodeAlign.LEFT, "#000000");
        }

        private static void line(PDPageContentStream stream, float x1, float y1, float x2, float y2) throws IOException {
            stream.moveTo(x1, y1);
            stream.lineTo(x2, y2);
        }

        private static String number(double value) {
            return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value).replace('.', ',');
        }
    }
}
