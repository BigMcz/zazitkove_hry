package cz.mibcon.zazitky.accesscode;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * Access codes are 8 characters from an alphabet without easily confused characters (no 0/O, 1/I/L).
 * They are stored without the dash ({@code ABCD2345}) and printed with it ({@code ABCD-2345});
 * players may type them in any case, with or without the dash and spaces.
 */
public final class AccessCodeFormat {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 8;

    private static final RandomGenerator RANDOM = new SecureRandom();

    private AccessCodeFormat() {
    }

    /** Canonical stored form: upper case, only letters and digits. {@code " abcd-2345 "} → {@code "ABCD2345"}. */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    /** Printed form with a dash in the middle: {@code "ABCD2345"} → {@code "ABCD-2345"}. */
    public static String format(String code) {
        if (code.length() != LENGTH) {
            return code;
        }
        return code.substring(0, LENGTH / 2) + "-" + code.substring(LENGTH / 2);
    }

    /** New random code in canonical form (cryptographically secure, ~40 bits). */
    public static String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
