package cz.mibcon.zazitky.cardprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import cz.mibcon.zazitky.cardprint.CardPdfRenderer.Graphic;
import cz.mibcon.zazitky.cardprint.CardPdfRenderer.PrintedCode;
import cz.mibcon.zazitky.cardprint.domain.CodeAlign;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

class CardPdfRendererTest {

    static final CardLayout CARD = CardLayout.businessCard();
    static final Graphic PDF_GRAPHIC = new Graphic(TestGraphics.pdf(94, 54), "application/pdf");

    final CardPdfRenderer renderer = new CardPdfRenderer();

    @Test
    void frontHasOnePageOfTheTrimWithBleed() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(front(false))) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            assertSize(pdf.getPage(0).getMediaBox(), 94, 54);
            assertSize(pdf.getPage(0).getTrimBox(), 90, 50);
        }
    }

    @Test
    void cropMarksAddAMargin() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(front(true))) {
            assertSize(pdf.getPage(0).getMediaBox(), 110, 70);
            assertSize(pdf.getPage(0).getBleedBox(), 94, 54);
        }
    }

    @Test
    void backHasAPagePerCode() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(back(PDF_GRAPHIC, codes(7), false))) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(7);
        }
    }

    @Test
    void theQrCodeReadsAsTheLinkWithTheCode() throws Exception {
        PrintedCode code = new PrintedCode("ABCD-2345", "https://storyhold.cz/?kod=ABCD-2345", "D12 · 0042");
        try (PDDocument pdf = Loader.loadPDF(back(PDF_GRAPHIC, List.of(code), true))) {
            BufferedImage image = new PDFRenderer(pdf).renderImageWithDPI(0, 300);
            int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                    new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)));
            assertThat(new QRCodeReader().decode(bitmap).getText()).isEqualTo("https://storyhold.cz/?kod=ABCD-2345");
        }
    }

    @Test
    void theGraphicIsInTheDocumentOnce() throws IOException {
        byte[] back = back(PDF_GRAPHIC, codes(100), false);
        assertThat(back.length).isLessThan(PDF_GRAPHIC.content().length + 100 * 10_000);
        try (PDDocument pdf = Loader.loadPDF(back)) {
            PDXObject first = graphicOf(pdf.getPage(0));
            PDXObject last = graphicOf(pdf.getPage(99));
            assertThat(first.getCOSObject()).isSameAs(last.getCOSObject());
        }
    }

    @Test
    void aPdfGraphicStaysVector() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(front(false))) {
            assertThat(graphicOf(pdf.getPage(0))).isInstanceOf(PDFormXObject.class);
        }
    }

    @Test
    void anImageGraphicIsPlacedOverTheBleed() throws IOException {
        Graphic png = new Graphic(TestGraphics.png(94, 54, 300), "image/png");
        try (PDDocument pdf = Loader.loadPDF(back(png, codes(2), false))) {
            assertThat(graphicOf(pdf.getPage(0))).isInstanceOf(PDImageXObject.class);
        }
    }

    @Test
    void theDefaultBusinessCardIsInTheSafeZone() {
        assertThat(renderer.safeZoneWarnings(CARD)).isEmpty();
    }

    @Test
    void warnsAboutElementsNearTheTrim() {
        CardLayout layout = new CardLayout(90.0, 50.0, 3.0, 1.0, 13.0, 24.0, true, 88.0, 27.0, 16.0, CodeAlign.CENTER,
                "#000000", 57.0, 49.0, 7.0);
        assertThat(renderer.safeZoneWarnings(layout)).hasSize(3);
    }

    @Test
    void previewIsAPng() throws IOException {
        byte[] png = renderer.preview(CARD, null, codes(1).getFirst());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        // 94 mm at 150 DPI
        assertThat(image.getWidth()).isBetween(553, 557);
    }

    @Test
    void testPrintIsOneA4() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        renderer.testPrint(CARD, PDF_GRAPHIC, PDF_GRAPHIC, codes(1).getFirst(), out);
        try (PDDocument pdf = Loader.loadPDF(out.toByteArray())) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            assertThat(pdf.getPage(0).getMediaBox().getWidth()).isEqualTo(PDRectangle.A4.getWidth());
        }
    }

    private byte[] front(boolean cropMarks) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        renderer.front(CARD, PDF_GRAPHIC, cropMarks, out);
        return out.toByteArray();
    }

    private byte[] back(Graphic graphic, List<PrintedCode> codes, boolean cropMarks) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        renderer.back(CARD, graphic, codes, cropMarks, out);
        return out.toByteArray();
    }

    private static List<PrintedCode> codes(int count) {
        List<PrintedCode> codes = new ArrayList<>();
        IntStream.rangeClosed(1, count).forEach(serial -> codes.add(new PrintedCode("ABCD-" + (2000 + serial),
                "https://storyhold.cz/?kod=ABCD-" + (2000 + serial), CardTemplateService.serialLabel(12, serial))));
        return codes;
    }

    private static PDXObject graphicOf(PDPage page) throws IOException {
        PDResources resources = page.getResources();
        for (COSName name : resources.getXObjectNames()) {
            return resources.getXObject(name);
        }
        throw new AssertionError("no graphic on the page");
    }

    private static void assertSize(PDRectangle box, double widthMm, double heightMm) {
        assertThat(box.getWidth() / CardPdfRenderer.PT_PER_MM).isCloseTo((float) widthMm, offset(0.01f));
        assertThat(box.getHeight() / CardPdfRenderer.PT_PER_MM).isCloseTo((float) heightMm, offset(0.01f));
    }
}
