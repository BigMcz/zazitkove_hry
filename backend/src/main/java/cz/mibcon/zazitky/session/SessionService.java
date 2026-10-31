package cz.mibcon.zazitky.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import cz.mibcon.zazitky.accesscode.AccessCodeService;
import cz.mibcon.zazitky.accesscode.CodeExpiredException;
import cz.mibcon.zazitky.accesscode.domain.AccessCode;
import cz.mibcon.zazitky.common.InvalidStateException;
import cz.mibcon.zazitky.common.NotFoundException;
import cz.mibcon.zazitky.game.domain.Game;
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
import cz.mibcon.zazitky.session.SessionDtos.Puzzle;
import cz.mibcon.zazitky.session.SessionDtos.RevealedHint;
import cz.mibcon.zazitky.session.SessionDtos.SessionState;
import cz.mibcon.zazitky.session.SessionDtos.SolvedStation;
import cz.mibcon.zazitky.session.SessionDtos.SyncEvent;
import cz.mibcon.zazitky.session.domain.AnswerAttempt;
import cz.mibcon.zazitky.session.domain.GameSession;
import cz.mibcon.zazitky.session.domain.ProgressStatus;
import cz.mibcon.zazitky.session.domain.SessionEvent;
import cz.mibcon.zazitky.session.domain.SessionProgress;
import cz.mibcon.zazitky.session.domain.SessionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Game rules of a session (docs/02-architektura.md, "Herní logika"). Stations are played in
 * {@code order_no}; exactly one station is ACTIVE while the session is IN_PROGRESS.
 */
@Service
// The EXPIRED status (of a session or a code) must be stored even though the request then fails with 410.
@Transactional(noRollbackFor = {SessionExpiredException.class, CodeExpiredException.class})
public class SessionService {

    static final String WRONG_ANSWER_MESSAGE = "To není ono. Zkus to znovu.";
    static final String NOT_A_NUMBER_MESSAGE = "Odpověď musí obsahovat číslo.";

    private final AccessCodeService accessCodes;
    private final GameSessionRepository sessions;
    private final AnswerAttemptRepository attempts;
    private final SessionEventRepository events;
    private final AnswerEvaluator evaluator;
    private final GamePackages packages;
    private final Clock clock;

    public SessionService(AccessCodeService accessCodes, GameSessionRepository sessions,
            AnswerAttemptRepository attempts, SessionEventRepository events, AnswerEvaluator evaluator,
            GamePackages packages, Clock clock) {
        this.accessCodes = accessCodes;
        this.sessions = sessions;
        this.attempts = attempts;
        this.events = events;
        this.evaluator = evaluator;
        this.packages = packages;
        this.clock = clock;
    }

    /**
     * Starts a game with an access code. A code already in use continues its game (also on another
     * device) unless that game was abandoned or the batch starts a new game on every entry.
     */
    public StartResult start(String rawCode, String teamName) {
        AccessCode code = accessCodes.findPlayable(rawCode);
        Instant now = clock.instant();
        accessCodes.activateIfNew(code, now);

        if (!code.getBatch().isMultiSession()) {
            Optional<GameSession> running = sessions.findFirstByAccessCodeIdOrderByStartedAtDesc(code.getId())
                    .filter(session -> isResumable(session, now));
            if (running.isPresent()) {
                return new StartResult(toState(running.get(), true), false);
            }
        }

        Game game = code.getBatch().getGame();
        Station firstStation = game.getStations().stream().findFirst()
                .orElseThrow(() -> new InvalidStateException("Hra nemá žádná stanoviště."));
        Instant expiresAt = now.plus(Duration.ofHours(game.getSessionValidityHours()));
        if (code.getValidUntil() != null && code.getValidUntil().isBefore(expiresAt)) {
            expiresAt = code.getValidUntil();
        }

        GameSession session = new GameSession();
        session.setGame(game);
        session.setAccessCode(code);
        session.setToken(UUID.randomUUID());
        session.setTeamName(teamName == null || teamName.isBlank() ? null : teamName.strip());
        session.setStartedAt(now);
        session.setExpiresAt(expiresAt);
        session.addProgress(activate(firstStation, now));
        sessions.save(session);

        return new StartResult(toState(session, true), true);
    }

    public record StartResult(SessionState state, boolean created) {
    }

    private static boolean isResumable(GameSession session, Instant now) {
        return switch (session.getStatus()) {
            case COMPLETED -> true;
            case IN_PROGRESS -> now.isBefore(session.getExpiresAt());
            case ABANDONED, EXPIRED -> false;
        };
    }

    public SessionState getState(UUID token) {
        GameSession session = sessions.findByToken(token).orElseThrow(InvalidTokenException::new);
        checkNotExpired(session);
        return toState(session, false);
    }

    /**
     * Offline package of a game (docs/02-architektura.md, "Offline režim"); also of a completed one, so that a phone
     * that played offline can catch up with a game finished on another phone.
     */
    public SessionPackage getPackage(UUID token) {
        GameSession session = sessions.findByToken(token).orElseThrow(InvalidTokenException::new);
        checkNotExpired(session);
        if (session.getStatus() != SessionStatus.IN_PROGRESS && session.getStatus() != SessionStatus.COMPLETED) {
            throw new InvalidStateException("Hra není rozehraná.");
        }
        return packages.forSession(session);
    }

    public AnswerResult submitAnswer(UUID token, UUID stationId, String rawAnswer) {
        GameSession session = loadForUpdate(token);
        return applyAnswer(session, activeProgress(session, stationId), rawAnswer, clock.instant());
    }

    private AnswerResult applyAnswer(GameSession session, SessionProgress progress, String rawAnswer, Instant now) {
        Station station = progress.getStation();
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
        return AnswerResult.solved(station.getRewardText(), station.getRewardAudioUrl(), station.getNextText(),
                next.map(Station::getId).orElse(null));
    }

    public HintsResult revealHint(UUID token, UUID stationId) {
        GameSession session = loadForUpdate(token);
        SessionProgress progress = activeProgress(session, stationId);
        List<Hint> hints = progress.getStation().getHints();
        if (progress.getHintsUsed() >= hints.size()) {
            throw new InvalidStateException("Žádná další nápověda už není k dispozici.");
        }
        applyHint(session, progress);

        return new HintsResult(
                revealedHints(progress),
                hints.size() - progress.getHintsUsed(),
                nextHintPenalty(progress),
                session.getPenaltySeconds());
    }

    private static void applyHint(GameSession session, SessionProgress progress) {
        Hint hint = progress.getStation().getHints().get(progress.getHintsUsed());
        progress.setHintsUsed(progress.getHintsUsed() + 1);
        session.setPenaltySeconds(session.getPenaltySeconds() + hint.getPenaltySeconds());
    }

    /**
     * Applies actions played in the phone without a signal (docs/02-architektura.md, "Offline režim") with the same
     * rules as the single requests, at the time they were played. An action already received (same id) is skipped;
     * one the rules no longer allow (station solved on another phone, no hint left) is recorded without effect.
     * Actions played while the code was valid count even when they arrive after it expired.
     */
    public SessionState sync(UUID token, List<SyncEvent> played) {
        GameSession session = sessions.findByTokenForUpdate(token).orElseThrow(InvalidTokenException::new);
        Instant now = clock.instant();
        if (session.getStatus() == SessionStatus.EXPIRED) {
            // Expiry is decided again below, after the actions from the time the code was valid
            session.setStatus(SessionStatus.IN_PROGRESS);
        }

        Instant previous = session.getStartedAt();
        for (SyncEvent event : played) {
            if (events.existsById(event.id())) {
                continue;
            }
            // In the order played, never before the start of the game or in the future (phone clocks are off)
            Instant at = event.at().isBefore(previous) ? previous : event.at().isAfter(now) ? now : event.at();
            previous = at;
            events.save(new SessionEvent(event.id(), session, event.type(), at, now));
            if (session.getStatus() != SessionStatus.IN_PROGRESS || !at.isBefore(session.getExpiresAt())) {
                continue;
            }
            Optional<SessionProgress> progress = findActiveProgress(session, event.stationId());
            if (progress.isEmpty()) {
                continue;
            }
            switch (event.type()) {
                case ANSWER -> applyAnswer(session, progress.get(), event.answer() == null ? "" : event.answer(), at);
                case HINT -> {
                    if (progress.get().getHintsUsed() < progress.get().getStation().getHints().size()) {
                        applyHint(session, progress.get());
                    }
                }
            }
        }

        checkNotExpired(session);
        return toState(session, false);
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
        return findActiveProgress(session, stationId)
                .orElseThrow(() -> new InvalidStateException("Toto stanoviště teď není aktivní."));
    }

    private static Optional<SessionProgress> findActiveProgress(GameSession session, UUID stationId) {
        return session.getProgress().stream()
                .filter(progress -> progress.getStation().getId().equals(stationId))
                .filter(progress -> progress.getStatus() == ProgressStatus.ACTIVE)
                .findFirst();
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
                                station.getId(), station.getOrderNo(), station.getTitle(), station.getRewardText(),
                                station.getRewardAudioUrl(), station.getNextText())))
                .toList();

        CurrentStation current = session.getStatus() != SessionStatus.IN_PROGRESS ? null : progress.stream()
                .filter(item -> item.getStatus() == ProgressStatus.ACTIVE)
                .findFirst()
                .map(SessionService::toCurrentStation)
                .orElse(null);

        Set<UUID> solvedIds = progress.stream()
                .filter(item -> item.getStatus() == ProgressStatus.SOLVED)
                .map(item -> item.getStation().getId())
                .collect(Collectors.toSet());
        UUID lastSolvedId = solved.isEmpty() ? null : solved.getLast().id();
        Puzzle puzzle = PuzzleLetters.of(session.getId(), stations, solvedIds, lastSolvedId);

        Finale finale = session.getStatus() != SessionStatus.COMPLETED ? null : new Finale(
                game.getOutroText(),
                game.getOutroAudioUrl(),
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
                puzzle,
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
                station.getStoryAudioUrl(),
                station.isListenOnly(),
                station.getImageUrl(),
                station.getImageAlt(),
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
