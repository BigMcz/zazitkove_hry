package cz.mibcon.zazitky.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import cz.mibcon.zazitky.game.domain.Station;
import cz.mibcon.zazitky.session.SessionDtos.Puzzle;

/**
 * Letters of the final puzzle collected so far, in a scrambled order. The order is derived from the
 * session id, so it stays the same for the whole game (also after a reload or on another device).
 */
final class PuzzleLetters {

    private PuzzleLetters() {
    }

    /** @return null when no station of the game gives a letter */
    static Puzzle of(UUID sessionId, List<Station> stations, Set<UUID> solvedStationIds, UUID lastSolvedStationId) {
        List<Station> withLetter = stations.stream().filter(station -> station.getPuzzleLetter() != null).toList();
        if (withLetter.isEmpty()) {
            return null;
        }

        List<Integer> slotOf = scrambledSlots(sessionId, withLetter.size());
        List<String> slots = new ArrayList<>(Collections.nCopies(withLetter.size(), (String) null));
        Integer latestSlot = null;
        for (int i = 0; i < withLetter.size(); i++) {
            Station station = withLetter.get(i);
            if (solvedStationIds.contains(station.getId())) {
                slots.set(slotOf.get(i), station.getPuzzleLetter());
                if (station.getId().equals(lastSolvedStationId)) {
                    latestSlot = slotOf.get(i);
                }
            }
        }
        return new Puzzle(slots, latestSlot);
    }

    /** Slot of the i-th letter; never the original order, which would give the word away. */
    static List<Integer> scrambledSlots(UUID sessionId, int size) {
        List<Integer> slots = new ArrayList<>(IntStream.range(0, size).boxed().toList());
        Collections.shuffle(slots, new Random(sessionId.getMostSignificantBits() ^ sessionId.getLeastSignificantBits()));
        if (size > 1 && IntStream.range(0, size).allMatch(i -> slots.get(i) == i)) {
            Collections.rotate(slots, 1);
        }
        return slots;
    }
}
