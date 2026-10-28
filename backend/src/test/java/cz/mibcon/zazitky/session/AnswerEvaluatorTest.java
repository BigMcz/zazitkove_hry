package cz.mibcon.zazitky.session;

import static org.assertj.core.api.Assertions.assertThat;

import cz.mibcon.zazitky.game.domain.AcceptedAnswer;
import cz.mibcon.zazitky.game.domain.Station;
import cz.mibcon.zazitky.game.domain.TaskType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AnswerEvaluatorTest {

    private final AnswerEvaluator evaluator = new AnswerEvaluator();

    @ParameterizedTest
    @ValueSource(strings = {"jáma", "JÁMA", " jama ", "Jáma!", "díra", "DÍRA?"})
    void acceptsAnyVariantOfTextAnswer(String answer) {
        Station station = station(TaskType.TEXT, "jama", "dira");

        assertThat(evaluator.evaluate(station, answer).correct()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"kámen", "jámy", "jama dira", "", "?!"})
    void rejectsWrongTextAnswer(String answer) {
        Station station = station(TaskType.TEXT, "jama", "dira");

        assertThat(evaluator.evaluate(station, answer).correct()).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "56          | true",
            "' 56 '      | true",
            "56 schodů   | true",
            "60          | false",
            "5 6 0       | false",
            "padesát šest | false",
    })
    void comparesNumbersByDigitsOnly(String answer, boolean expected) {
        Station station = station(TaskType.NUMBER, "56");

        assertThat(evaluator.evaluate(station, answer).correct()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Lucernu | true",
            "lucerna | true",
            "Meč     | false",
            "Klíč    | false",
    })
    void evaluatesChoiceByText(String answer, boolean expected) {
        Station station = station(TaskType.CHOICE, "lucernu", "lucerna");

        assertThat(evaluator.evaluate(station, answer).correct()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "TEXT   | ' Díra! ' | dira",
            "NUMBER | 56 schodů | 56",
            "CHOICE | Lucernu   | lucernu",
    })
    void returnsNormalizedAnswerForAttemptLog(TaskType type, String answer, String normalized) {
        Station station = station(type, "x");

        assertThat(evaluator.evaluate(station, answer).normalizedAnswer()).isEqualTo(normalized);
    }

    private static Station station(TaskType type, String... normalizedValues) {
        Station station = new Station();
        station.setTaskType(type);
        for (String value : normalizedValues) {
            AcceptedAnswer answer = new AcceptedAnswer();
            answer.setValue(value);
            answer.setNormalizedValue(value);
            station.addAcceptedAnswer(answer);
        }
        return station;
    }
}
