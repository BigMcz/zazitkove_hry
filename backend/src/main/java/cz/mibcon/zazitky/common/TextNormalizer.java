package cz.mibcon.zazitky.common;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes answers so that player input and stored accepted answers can be compared by equality.
 * The same rules must be used for both sides, see docs/02-architektura.md.
 */
public final class TextNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern PUNCTUATION = Pattern.compile("[^\\p{L}\\p{N} ]");
    private static final Pattern NON_DIGITS = Pattern.compile("[^0-9]");

    private TextNormalizer() {
    }

    /** Trim, lower case, strip diacritics and punctuation, collapse whitespace: {@code " Díra! "} → {@code "dira"}. */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String text = input.strip().toLowerCase(Locale.ROOT);
        text = COMBINING_MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        // Whitespace first, so that a newline or tab between words is not removed as punctuation.
        text = WHITESPACE.matcher(text).replaceAll(" ");
        text = PUNCTUATION.matcher(text).replaceAll("");
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }

    /** Keeps only ASCII digits: {@code "56 schodů"} → {@code "56"}. */
    public static String normalizeNumber(String input) {
        if (input == null) {
            return "";
        }
        return NON_DIGITS.matcher(input).replaceAll("");
    }
}
