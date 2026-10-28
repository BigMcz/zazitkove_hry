package cz.mibcon.zazitky.accesscode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import cz.mibcon.zazitky.accesscode.domain.AccessCode;
import cz.mibcon.zazitky.accesscode.domain.AccessCodeBatch;
import cz.mibcon.zazitky.accesscode.domain.AccessCodeStatus;
import cz.mibcon.zazitky.game.GameDetailDto;
import cz.mibcon.zazitky.game.GameService;
import cz.mibcon.zazitky.game.domain.GameStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Codes from the player's point of view. A code is NEW until a player first starts a game with it;
 * then it is ACTIVATED (= sold) and valid for {@code validity_hours} of its batch.
 */
@Service
// The EXPIRED status must be stored even though the request then fails with 410.
@Transactional(noRollbackFor = CodeExpiredException.class)
public class AccessCodeService {

    private final AccessCodeRepository codes;
    private final GameService gameService;
    private final Clock clock;

    public AccessCodeService(AccessCodeRepository codes, GameService gameService, Clock clock) {
        this.codes = codes;
        this.gameService = gameService;
        this.clock = clock;
    }

    /** What the home page needs to know after the player typed a code; does not activate it. */
    public CodeCheckDto check(String rawCode) {
        AccessCode code = findPlayable(rawCode);
        boolean resumes = code.getStatus() == AccessCodeStatus.ACTIVATED && !code.getBatch().isMultiSession();
        GameDetailDto game = gameService.getPublished(code.getBatch().getGame().getId());
        return new CodeCheckDto(AccessCodeFormat.format(code.getCode()), game, resumes, code.getValidUntil());
    }

    /**
     * The code a game can be played with right now, otherwise 404 (unknown, cancelled, or still in
     * production — not handed over to the partner yet) or 410 (expired).
     */
    public AccessCode findPlayable(String rawCode) {
        AccessCode code = codes.findByCode(AccessCodeFormat.normalize(rawCode))
                .orElseThrow(InvalidCodeException::new);
        if (code.getStatus() == AccessCodeStatus.CANCELLED
                || code.getBatch().getHandedOverAt() == null
                || code.getBatch().getGame().getStatus() != GameStatus.PUBLISHED) {
            throw new InvalidCodeException();
        }
        if (code.getStatus() == AccessCodeStatus.ACTIVATED && code.getValidUntil() != null
                && !clock.instant().isBefore(code.getValidUntil())) {
            code.setStatus(AccessCodeStatus.EXPIRED);
        }
        if (code.getStatus() == AccessCodeStatus.EXPIRED) {
            throw new CodeExpiredException();
        }
        return code;
    }

    /** First use of the code: from now on it counts as sold and its validity starts. */
    public void activateIfNew(AccessCode code, Instant now) {
        if (code.getStatus() == AccessCodeStatus.ACTIVATED) {
            return;
        }
        AccessCodeBatch batch = code.getBatch();
        code.setStatus(AccessCodeStatus.ACTIVATED);
        code.setActivatedAt(now);
        code.setValidUntil(batch.getValidityHours() == null
                ? null
                : now.plus(Duration.ofHours(batch.getValidityHours())));
    }
}
