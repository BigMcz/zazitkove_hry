package cz.mibcon.zazitky.cardprint;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.mibcon.zazitky.common.BadRequestException;

/**
 * Serial numbers chosen for a reprint, e.g. {@code "42, 57, 100-110"}: numbers and ranges separated by commas or
 * spaces, a range also with an en dash or the other way round ({@code 110-100}); duplicates are dropped.
 *
 * @param serials   the chosen numbers in ascending order
 * @param canonical the same written shortly for the audit, {@code "42,57,100-110"}
 */
public record SerialSelection(SortedSet<Integer> serials, String canonical) {

    private static final Pattern PART = Pattern.compile("(\\d{1,6})(?:\\s*[-–]\\s*(\\d{1,6}))?");

    /** @param quantity the batch has serial numbers 1…quantity */
    public static SerialSelection parse(String input, int quantity) {
        if (input == null || input.isBlank()) {
            throw new BadRequestException("Zadejte pořadová čísla kartiček k dotisku, např. 42, 57, 100-110.");
        }
        SortedSet<Integer> serials = new TreeSet<>();
        for (String part : input.strip().replaceAll("\\s*[-–]\\s*", "-").split("[,;\\s]+")) {
            if (part.isEmpty()) {
                continue;
            }
            Matcher matcher = PART.matcher(part);
            if (!matcher.matches()) {
                throw new BadRequestException("„" + part + "“ není pořadové číslo ani rozsah (např. 100-110).");
            }
            int from = Integer.parseInt(matcher.group(1));
            int to = matcher.group(2) == null ? from : Integer.parseInt(matcher.group(2));
            for (int serial = Math.min(from, to); serial <= Math.max(from, to); serial++) {
                if (serial < 1 || serial > quantity) {
                    throw new BadRequestException("Kartička s pořadovým číslem " + serial + " v dávce není, dávka má čísla 1 až "
                            + quantity + ".");
                }
                serials.add(serial);
            }
        }
        if (serials.isEmpty()) {
            throw new BadRequestException("Zadejte pořadová čísla kartiček k dotisku, např. 42, 57, 100-110.");
        }
        return new SerialSelection(serials, canonical(serials));
    }

    private static String canonical(SortedSet<Integer> serials) {
        List<String> parts = new ArrayList<>();
        Integer start = null;
        Integer previous = null;
        for (int serial : serials) {
            if (previous != null && serial == previous + 1) {
                previous = serial;
                continue;
            }
            if (start != null) {
                parts.add(range(start, previous));
            }
            start = serial;
            previous = serial;
        }
        parts.add(range(start, previous));
        return String.join(",", parts);
    }

    private static String range(int from, int to) {
        return from == to ? String.valueOf(from) : from + "-" + to;
    }
}
