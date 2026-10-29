package cz.mibcon.zazitky.session;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import cz.mibcon.zazitky.game.domain.TaskType;
import cz.mibcon.zazitky.session.domain.SessionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies of {@code /api/v1/sessions}. Never contains accepted answers
 * or hints the player has not revealed yet.
 */
public final class SessionDtos {

    private SessionDtos() {
    }

    public record StartSessionRequest(
            @NotBlank @Size(max = 30) String code,
            @Size(max = 100) String teamName) {
    }

    public record SubmitAnswerRequest(
            @NotBlank @Size(max = 200) String answer) {
    }

    public record SessionState(
            /** Only in the response to {@code POST /sessions}. */
            @JsonInclude(JsonInclude.Include.NON_NULL) UUID token,
            SessionStatus status,
            GameRef game,
            String teamName,
            Instant startedAt,
            int penaltySeconds,
            Progress progress,
            CurrentStation currentStation,
            List<SolvedStation> solvedStations,
            Finale finale) {
    }

    public record GameRef(UUID id, String title) {
    }

    public record Progress(int solved, int total) {
    }

    public record CurrentStation(
            UUID id,
            int orderNo,
            String title,
            String storyText,
            /** Spoken story, null = text only. */
            String storyAudioUrl,
            /** The story is a listening task: show its text only on request. */
            boolean listenOnly,
            /** Picture to the story / task, null = none. */
            String imageUrl,
            String imageAlt,
            String taskText,
            TaskType taskType,
            /** Texts to choose from for {@code CHOICE}, otherwise null. */
            List<String> choices,
            /** Number of hints not revealed yet. */
            int hintsAvailable,
            /** Penalty for revealing the next hint, null when there is none left. */
            Integer nextHintPenaltySeconds,
            List<RevealedHint> revealedHints) {
    }

    public record RevealedHint(int orderNo, String text, int penaltySeconds) {
    }

    public record SolvedStation(
            UUID id, int orderNo, String title, String rewardText, String rewardAudioUrl, String nextText) {
    }

    public record Finale(String outroText, String outroAudioUrl, long totalSeconds, int hintsUsed) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AnswerResult(
            boolean correct,
            String message,
            String rewardText,
            String rewardAudioUrl,
            /** Where to go next, null = no direction. */
            String nextText,
            UUID nextStationId,
            Boolean gameCompleted) {

        static AnswerResult wrong(String message) {
            return new AnswerResult(false, message, null, null, null, null, null);
        }

        static AnswerResult solved(String rewardText, String rewardAudioUrl, String nextText, UUID nextStationId) {
            return new AnswerResult(true, null, rewardText, rewardAudioUrl, nextText, nextStationId,
                    nextStationId == null);
        }
    }

    public record HintsResult(
            List<RevealedHint> revealedHints,
            int hintsAvailable,
            Integer nextHintPenaltySeconds,
            int penaltySeconds) {
    }
}
