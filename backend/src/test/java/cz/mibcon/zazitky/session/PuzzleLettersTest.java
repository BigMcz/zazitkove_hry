package cz.mibcon.zazitky.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import cz.mibcon.zazitky.game.domain.Station;
import cz.mibcon.zazitky.session.SessionDtos.Puzzle;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PuzzleLettersTest {

    @RepeatedTest(20)
    void slotsAreAScrambledPermutationStableForTheSession() {
        UUID sessionId = UUID.randomUUID();

        List<Integer> slots = PuzzleLetters.scrambledSlots(sessionId, 9);

        assertThat(slots).containsExactlyInAnyOrderElementsOf(IntStream.range(0, 9).boxed().toList());
        assertThat(slots).isNotEqualTo(IntStream.range(0, 9).boxed().toList());
        assertThat(PuzzleLetters.scrambledSlots(sessionId, 9)).isEqualTo(slots);
    }

    @Test
    void onlyLettersOfSolvedStationsAreShownAndTheLatestIsMarked() {
        Station first = station("P");
        Station second = station("O");
        Station third = station("V");
        Station last = station(null);
        UUID sessionId = UUID.randomUUID();

        Puzzle puzzle = PuzzleLetters.of(sessionId, List.of(first, second, third, last),
                Set.of(first.getId(), second.getId()), second.getId());

        assertThat(puzzle.slots()).hasSize(3).containsOnlyOnce("P", "O").containsOnlyOnce((String) null);
        assertThat(puzzle.slots().get(puzzle.latestSlot())).isEqualTo("O");
    }

    @Test
    void noPuzzleWithoutLetters() {
        assertThat(PuzzleLetters.of(UUID.randomUUID(), List.of(station(null)), Set.of(), null)).isNull();
    }

    private static Station station(String letter) {
        Station station = new Station();
        ReflectionTestUtils.setField(station, "id", UUID.randomUUID());
        station.setPuzzleLetter(letter);
        return station;
    }
}
