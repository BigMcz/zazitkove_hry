package cz.mibcon.zazitky.cardprint;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/** Graphics of a card side as a designer would send them. */
final class TestGraphics {

    private TestGraphics() {
    }

    /** A vector PDF page of the given size with a light background and a frame. */
    static byte[] pdf(double widthMm, double heightMm) {
        try (PDDocument doc = new PDDocument()) {
            float width = CardPdfRenderer.mm(widthMm);
            float height = CardPdfRenderer.mm(heightMm);
            PDPage page = new PDPage(new PDRectangle(width, height));
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.setNonStrokingColor(new Color(0xF4, 0xEB, 0xD9));
                stream.addRect(0, 0, width, height);
                stream.fill();
                stream.setStrokingColor(new Color(0x7A, 0x4E, 0x2D));
                stream.setLineWidth(2);
                stream.addRect(20, 20, width - 40, height - 40);
                stream.stroke();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A PNG of the given size in millimetres at the given resolution. */
    static byte[] png(double widthMm, double heightMm, int dpi) {
        BufferedImage image = new BufferedImage((int) Math.round(widthMm / 25.4 * dpi),
                (int) Math.round(heightMm / 25.4 * dpi), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0xDD, 0xE8, 0xF0));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
