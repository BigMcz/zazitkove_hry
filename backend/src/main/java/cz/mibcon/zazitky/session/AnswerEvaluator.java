package cz.mibcon.zazitky.session;

import cz.mibcon.zazitky.common.TextNormalizer;
import cz.mibcon.zazitky.game.domain.AcceptedAnswer;
import cz.mibcon.zazitky.game.domain.Station;
import cz.mibcon.zazitky.game.domain.TaskType;
import org.springframework.stereotype.Component;

/** Normalizes the player's answer the same way as the stored accepted answers and compares them. */
@Component
public class AnswerEvaluator {

    public Evaluation evaluate(Station station, String rawAnswer) {
        String normalized = station.getTaskType() == TaskType.NUMBER
                ? TextNormalizer.normalizeNumber(rawAnswer)
                : TextNormalizer.normalize(rawAnswer);
        boolean correct = !normalized.isEmpty() && station.getAcceptedAnswers().stream()
                .map(AcceptedAnswer::getNormalizedValue)
                .anyMatch(normalized::equals);
        return new Evaluation(normalized, correct);
    }

    public record Evaluation(String normalizedAnswer, boolean correct) {
    }
}
