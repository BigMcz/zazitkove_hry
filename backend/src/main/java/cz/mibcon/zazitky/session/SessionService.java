package cz.mibcon.zazitky.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import cz.mibcon.zazitky.common.InvalidStateException;
import cz.mibcon.zazitky.common.NotFoundException;
import cz.mibcon.zazitky.game.GameRepository;
import cz.mibcon.zazitky.game.domain.Game;
import cz.mibcon.zazitky.game.domain.GameStatus;
import cz.mibcon.zazitky.game.domain.Hint;
import cz.mibcon.zazitky.game.domain.Station;
import cz.mibcon.zazitky.game.domain.StationChoice;
import cz.mibcon.zazitky.game.domain.TaskType;
import cz.mibcon.zazitky.session.SessionDtos.AnswerResult;
import cz.mibcon.zazitky.session.SessionDtos.CurrentStation;
import cz.mibcon.zazitky.session.SessionDtos.Finale;
import cz.mibcon.zazitky.session.SessionDtos.GameRef;
import cz.mibcon.zazitky.session.SessionDtos.HintsResult;
import cz.mibcon.zazitky.session.SessionDtos.Progress;
import cz.mibcon.zazitky.session.SessionDtos.RevealedHint;
import cz.mibcon.zazitky.session.SessionDtos.SessionState;
import cz.mibcon.zazitky.session.SessionDtos.SolvedStation;
import cz.mibcon.zazitky.session.domain.AnswerAttempt;
import cz.mibcon.zazitky.session.domain.GameSession;
import cz.mibcon.zazitky.session.domain.ProgressStatus;
import cz.mibcon.zazitky.session.domain.SessionProgress;
import cz.mibcon.zazitky.session.domain.SessionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Game rules of a session (docs/02-architektura.md, "Herní logika"). Stations are played in
 * {@code order_no}; exactly one station is ACTIVE while the session is IN_PROGRESS.
 */
@Service
// The EXPIRED status must be stored even though the request then fails with 410.
@Transactional(noRollbackFor = SessionExpiredException.class)
public class SessionService {

    static final String WRONG_ANSWER_MESSAGE = "To není ono. Zkus to znovu.";
    static final String NOT_A_NUMBER_MESSAGE = "Odpověď musí obsahovat číslo.";

    private final GameRepository games;
    private final GameSessionRepository sessions;
    private final AnswerAttemptRepository attempts;
    private final AnswerEvaluator evaluator;
    private final Clock clock;

    public SessionService(GameRepository games, GameSessionRepository sessions, AnswerAttemptRepository attempts,
            AnswerEvaluator evaluator, Clock clock) {
        this.games = games;
        this.sessions = sessions;
        this.attempts = attempts;
        this.evaluator = evaluator;
        this.clock = clock;
    }

    public SessionState start(UUID gameId, String teamName) {
        Game game = games.findByIdAndStatus(gameId, GameStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Hra nebyla nalezena."));
        Station firstStation = game.getStations().stream().findFirst()
                .orElseThrow(() -> new InvalidStateException("Hra nemá žádná stanoviště."));
        Instant now = clock.instant();

        GameSession session = new GameSession();
        session.setGame(game);
        session.setToken(UUID.randomUUID());
        session.setTeamName(teamName == null || teamName.isBlank() ? null : teamName.strip());
        session.setStartedAt(now);
        session.setExpiresAt(now.plus(Duration.ofHours(game.getSessionValidityHours())));
        session.addProgress(activate(firstStation, now));
        sessions.save(session);

        return toState(session, true);
    }

    public SessionState getState(UUID token) {
        GameSession session = sessions.findByToken(token).orElseThrow(InvalidTokenException::new);
        checkNotExpired(session);
        return toState(session, false);
    }

    public AnswerResult submitAnswer(UUID token, UUID stationId, String rawAnswer) {
        GameSession session = loadForUpdate(token);
        SessionProgress progress = activeProgress(session, stationId);
        Station station = progress.getStation();
        Instant now = clock.instant();

        AnswerEvaluator.Evaluation evaluation = evaluator.evaluate(station, rawAnswer);
        attempts.save(attempt(session, station, rawAnswer, evaluation, now));
        progress.setAttemptsCount(progress.getAttemptsCount() + 1);

        if (!evaluation.correct()) {
            boolean missingNumber = station.getTaskType() == TaskType.NUMBER && evaluation.normalizedAnswer().isEmpty();
            return AnswerResult.wrong(missingNumber ? NOT_A_NUMBER_MESSAGE : WRONG_ANSWER_MESSAGE);
        }

        progress.setStatus(ProgressStatus.SOLVED);
        progress.setSolvedAt(now);
        Optional<Station> next = nextStation(session.getGame(), station);
        if (next.isPresent()) {
            session.addProgress(activate(next.get(), now));
        } else {
            session.setStatus(SessionStatus.COMPLETED);
            session.setFinishedAt(now);
        }
        return AnswerResult.solved(station.getRewardText(), next.map(Station::getId).orElse(null));
    }

    public HintsResult revealHint(UUID token, UUID stationId) {
        GameSession session = loadForUpdate(token);
        SessionProgress progress = activeProgress(session, stationId);
        List<Hint> hints = progress.getStation().getHints();
        if (progress.getHintsUsed() >= hints.size()) {
            throw new InvalidStateException("Žádná další nápověda už není k dispozici.");
        }

        Hint hint = hints.get(progress.getHintsUsed());
        progress.setHintsUsed(progress.getHintsUsed() + 1);
        session.setPenaltySeconds(session.getPenaltySeconds() + hint.getPenaltySeconds());

        return new HintsResult(
                revealedHints(progress),
                hints.size() - progress.getHintsUsed(),
                nextHintPenalty(progress),
                session.getPenaltySeconds());
    }

    public void abandon(UUID token) {
        GameSession session = loadForUpdate(token);
        switch (session.getStatus()) {
            case IN_PROGRESS -> session.setStatus(SessionStatus.ABANDONED);
            case ABANDONED -> {
                // already abandoned, nothing to do
            }
            default -> throw new InvalidStateException("Hru už nelze opustit, protože není rozehraná.");
        }
    }

    private GameSession loadForUpdate(UUID token) {
        GameSession session = sessions.findByTokenForUpdate(token).orElseThrow(InvalidTokenException::new);
        checkNotExpired(session);
        return session;
    }

    private void checkNotExpired(GameSession session) {
        if (session.getStatus() == SessionStatus.IN_PROGRESS && !clock.instant().isBefore(session.getExpiresAt())) {
            session.setStatus(SessionStatus.EXPIRED);
        }
        if (session.getStatus() == SessionStatus.EXPIRED) {
            throw new SessionExpiredException();
        }
    }

    private SessionProgress activeProgress(GameSession session, UUID stationId) {
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new InvalidStateException("Hra není rozehraná.");
        }
        boolean stationInGame = session.getGame().getStations().stream()
                .anyMatch(station -> station.getId().equals(stationId));
        if (!stationInGame) {
            throw new NotFoundException("Stanoviště nebylo nalezeno.");
        }
        return session.getProgress().stream()
                .filter(progress -> progress.getStation().getId().equals(stationId))
                .filter(progress -> progress.getStatus() == ProgressStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new InvalidStateException("Toto stanoviště teď není aktivní."));
    }

    private static Optional<Station> nextStation(Game game, Station current) {
        return game.getStations().stream()
                .filter(station -> station.getOrderNo() > current.getOrderNo())
                .findFirst();
    }

    private static SessionProgress activate(Station station, Instant now) {
        SessionProgress progress = new SessionProgress();
        progress.setStation(station);
        progress.setStatus(ProgressStatus.ACTIVE);
        progress.setActivatedAt(now);
        return progress;
    }

    private static AnswerAttempt attempt(GameSession session, Station station, String rawAnswer,
            AnswerEvaluator.Evaluation evaluation, Instant now) {
        AnswerAttempt attempt = new AnswerAttempt();
        attempt.setSession(session);
        attempt.setStation(station);
        attempt.setRawAnswer(rawAnswer);
        attempt.setNormalizedAnswer(evaluation.normalizedAnswer());
        attempt.setCorrect(evaluation.correct());
        attempt.setCreatedAt(now);
        return attempt;
    }

    private SessionState toState(GameSession session, boolean includeToken) {
        Game game = session.getGame();
        List<Station> stations = game.getStations();
        List<SessionProgress> progress = session.getProgress();

        List<SolvedStation> solved = stations.stream()
                .flatMap(station -> progress.stream()
                        .filter(item -> item.getStation().getId().equals(station.getId()))
                        .filter(item -> item.getStatus() == ProgressStatus.SOLVED)
                        .map(item -> new SolvedStation(
                                station.getId(), station.getOrderNo(), station.getTitle(), station.getRewardText())))
                .toList();

        CurrentStation current = session.getStatus() != SessionStatus.IN_PROGRESS ? null : progress.stream()
                .filter(item -> item.getStatus() == ProgressStatus.ACTIVE)
                .findFirst()
                .map(SessionService::toCurrentStation)
                .orElse(null);

        Finale finale = session.getStatus() != SessionStatus.COMPLETED ? null : new Finale(
                game.getOutroText(),
                Duration.between(session.getStartedAt(), session.getFinishedAt()).toSeconds()
                        + session.getPenaltySeconds(),
                progress.stream().mapToInt(SessionProgress::getHintsUsed).sum());

        return new SessionState(
                includeToken ? session.getToken() : null,
                session.getStatus(),
                new GameRef(game.getId(), game.getTitle()),
                session.getTeamName(),
                session.getStartedAt(),
                session.getPenaltySeconds(),
                new Progress(solved.size(), stations.size()),
                current,
                solved,
                finale);
    }

    private static CurrentStation toCurrentStation(SessionProgress progress) {
        Station station = progress.getStation();
        List<String> choices = station.getTaskType() != TaskType.CHOICE ? null
                : station.getChoices().stream().map(StationChoice::getText).toList();
        return new CurrentStation(
                station.getId(),
                station.getOrderNo(),
                station.getTitle(),
                station.getStoryText(),
                station.getTaskText(),
                station.getTaskType(),
                choices,
                station.getHints().size() - progress.getHintsUsed(),
                nextHintPenalty(progress),
                revealedHints(progress));
    }

    /** Only the hints the player has already paid for. */
    private static List<RevealedHint> revealedHints(SessionProgress progress) {
        return progress.getStation().getHints().stream()
                .limit(progress.getHintsUsed())
                .map(hint -> new RevealedHint(hint.getOrderNo(), hint.getText(), hint.getPenaltySeconds()))
                .toList();
    }

    private static Integer nextHintPenalty(SessionProgress progress) {
        List<Hint> hints = progress.getStation().getHints();
        return progress.getHintsUsed() < hints.size() ? hints.get(progress.getHintsUsed()).getPenaltySeconds() : null;
    }
}
