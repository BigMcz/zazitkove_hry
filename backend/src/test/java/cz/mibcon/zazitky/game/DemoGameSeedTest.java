package cz.mibcon.zazitky.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import cz.mibcon.zazitky.IntegrationTest;
import cz.mibcon.zazitky.common.TextNormalizer;
import cz.mibcon.zazitky.game.domain.AcceptedAnswer;
import cz.mibcon.zazitky.game.domain.Game;
import cz.mibcon.zazitky.game.domain.GameStatus;
import cz.mibcon.zazitky.game.domain.Station;
import cz.mibcon.zazitky.game.domain.StationChoice;
import cz.mibcon.zazitky.game.domain.TaskType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class DemoGameSeedTest {

    static final UUID DEMO_GAME_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    GameRepository games;

    @Autowired
    AcceptedAnswerRepository acceptedAnswers;

    @Test
    void everyNormalizedValueMatchesTextNormalizer() {
        List<AcceptedAnswer> answers = acceptedAnswers.findAll();

        assertThat(answers).isNotEmpty().allSatisfy(answer -> {
            String expected = answer.getStation().getTaskType() == TaskType.NUMBER
                    ? TextNormalizer.normalizeNumber(answer.getValue())
                    : TextNormalizer.normalize(answer.getValue());
            assertThat(answer.getNormalizedValue())
                    .as("normalized_value of '%s'", answer.getValue())
                    .isEqualTo(expected);
        });
    }

    @Test
    void demoGameMatchesSpecification() {
        Game game = games.findBySlug("demo-katuv-klic").orElseThrow();

        assertThat(game.getId()).isEqualTo(DEMO_GAME_ID);
        assertThat(game.getStatus()).isEqualTo(GameStatus.PUBLISHED);
        assertThat(game.getStations()).extracting(Station::getOrderNo).containsExactly(1, 2, 3, 4, 5);
        assertThat(game.getStations()).extracting(Station::getTaskType).containsExactly(
                TaskType.TEXT, TaskType.NUMBER, TaskType.CHOICE, TaskType.TEXT, TaskType.TEXT);
        assertThat(game.getStations()).extracting(station -> station.getHints().size())
                .containsExactly(2, 2, 1, 2, 1);
        int totalPenalty = game.getStations().stream()
                .flatMap(station -> station.getHints().stream())
                .mapToInt(hint -> hint.getPenaltySeconds())
                .sum();
        assertThat(totalPenalty).isEqualTo(660);
    }

    @Test
    void correctChoiceIsAcceptedAnswer() {
        Station cellar = games.findBySlug("demo-katuv-klic").orElseThrow().getStations().get(2);
        List<String> choices = cellar.getChoices().stream()
                .map(StationChoice::getText)
                .map(TextNormalizer::normalize)
                .toList();
        List<String> accepted = cellar.getAcceptedAnswers().stream()
                .map(AcceptedAnswer::getNormalizedValue)
                .toList();

        assertThat(choices).containsExactly("mec", "provaz", "lucernu", "klic");
        assertThat(accepted).containsAnyElementsOf(choices);
    }
}
