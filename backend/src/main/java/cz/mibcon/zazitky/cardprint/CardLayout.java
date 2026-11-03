package cz.mibcon.zazitky.cardprint;

import cz.mibcon.zazitky.cardprint.domain.CodeAlign;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Size of a card and where the QR code, the code and the serial number are printed on its back. Millimetres and
 * points; positions are measured from the top-left corner of the trim (the finished card), not of the bleed.
 *
 * @param qrSizeMm     the whole QR square including its white margin (quiet zone of 4 modules)
 * @param codeYMm      baseline of the code; codeXMm is its left end, middle or right end by codeAlign
 * @param serialXMm    the serial number "D12 · 0042" (same alignment as the code); all three null = not printed
 */
public record CardLayout(
        @NotNull @DecimalMin("30") @DecimalMax("300") Double widthMm,
        @NotNull @DecimalMin("30") @DecimalMax("300") Double heightMm,
        @NotNull @DecimalMin("0") @DecimalMax("10") Double bleedMm,
        @NotNull @DecimalMin("0") @DecimalMax("300") Double qrXMm,
        @NotNull @DecimalMin("0") @DecimalMax("300") Double qrYMm,
        @NotNull @DecimalMin("18") @DecimalMax("300") Double qrSizeMm,
        @NotNull Boolean qrWhiteBox,
        @NotNull @DecimalMin("0") @DecimalMax("300") Double codeXMm,
        @NotNull @DecimalMin("0") @DecimalMax("300") Double codeYMm,
        @NotNull @DecimalMin("6") @DecimalMax("48") Double codeFontPt,
        @NotNull CodeAlign codeAlign,
        @NotNull @Pattern(regexp = "#[0-9A-Fa-f]{6}") String codeColor,
        @DecimalMin("0") @DecimalMax("300") Double serialXMm,
        @DecimalMin("0") @DecimalMax("300") Double serialYMm,
        @DecimalMin("4") @DecimalMax("24") Double serialFontPt) {

    /** Minimal distance of the printed elements from the trim, cutting may be off by a millimetre or two. */
    public static final double SAFE_ZONE_MM = 3;

    /**
     * Czech business card 90 × 50 mm on its side with 2 mm bleed, the default of the editor: the back of
     * grafika/karticky/karticky-prompty.md (QR at the top left, the code and the serial number right of it).
     */
    public static CardLayout businessCard() {
        return new CardLayout(90.0, 50.0, 2.0, 5.0, 6.0, 30.0, true, 60.0, 32.0, 16.0, CodeAlign.CENTER, "#000000",
                60.0, 37.5, 6.0);
    }

    public boolean hasSerial() {
        return serialXMm != null && serialYMm != null && serialFontPt != null;
    }

    /** Some of the serial fields are set, others not. */
    public boolean serialIncomplete() {
        return !hasSerial() && (serialXMm != null || serialYMm != null || serialFontPt != null);
    }

    /** The graphic covers the trim and the bleed on each side. */
    public double bleedWidthMm() {
        return widthMm + 2 * bleedMm;
    }

    public double bleedHeightMm() {
        return heightMm + 2 * bleedMm;
    }
}
