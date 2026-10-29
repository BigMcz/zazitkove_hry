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
        Game game = games.findBySlug("demo-katova-povinnost").orElseThrow();

        assertThat(game.getId()).isEqualTo(DEMO_GAME_ID);
        assertThat(game.getStatus()).isEqualTo(GameStatus.PUBLISHED);
        assertThat(game.getStations()).extracting(Station::getOrderNo).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(game.getStations()).extracting(Station::getTaskType).containsExactly(
                TaskType.CHOICE, TaskType.CHOICE, TaskType.TEXT, TaskType.TEXT, TaskType.CHOICE,
                TaskType.CHOICE, TaskType.CHOICE, TaskType.NUMBER, TaskType.NUMBER, TaskType.TEXT);
        assertThat(game.getStations()).extracting(station -> station.getHints().size())
                .containsExactly(1, 1, 2, 1, 1, 1, 1, 1, 2, 1);
        int totalPenalty = game.getStations().stream()
                .flatMap(station -> station.getHints().stream())
                .mapToInt(hint -> hint.getPenaltySeconds())
                .sum();
        assertThat(totalPenalty).isEqualTo(840);
        assertThat(game.getStations()).filteredOn(Station::isListenOnly).extracting(Station::getOrderNo)
                .containsExactly(4, 7);
        // Every station but the last tells the player where to go next
        assertThat(game.getStations()).extracting(station -> station.getNextText() != null)
                .containsExactly(true, true, true, true, true, true, true, true, true, false);
    }

    @Test
    void everyChoiceStationAcceptsOneOfItsChoices() {
        List<Station> choiceStations = games.findBySlug("demo-katova-povinnost").orElseThrow().getStations().stream()
                .filter(station -> station.getTaskType() == TaskType.CHOICE)
                .toList();

        assertThat(choiceStations).hasSize(5).allSatisfy(station -> {
            List<String> choices = station.getChoices().stream()
                    .map(StationChoice::getText)
                    .map(TextNormalizer::normalize)
                    .toList();
            List<String> accepted = station.getAcceptedAnswers().stream()
                    .map(AcceptedAnswer::getNormalizedValue)
                    .toList();
            assertThat(choices).hasSize(4);
            assertThat(accepted).isNotEmpty().allSatisfy(answer -> assertThat(choices).contains(answer));
        });
    }
}
