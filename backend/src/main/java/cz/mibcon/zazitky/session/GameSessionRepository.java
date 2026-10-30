package cz.mibcon.zazitky.session;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import cz.mibcon.zazitky.session.domain.GameSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {

    Optional<GameSession> findByToken(UUID token);

    Optional<GameSession> findFirstByAccessCodeIdOrderByStartedAtDesc(UUID accessCodeId);

    /**
     * Loads the session for a change and always bumps its version on commit, so that two
     * concurrent requests for the same session cannot both succeed (the second one fails
     * with an optimistic locking error).
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select s from GameSession s where s.token = :token")
    Optional<GameSession> findByTokenForUpdate(UUID token);

    /** Removes team names (the only personal data of a game) from sessions that expired before the given time. */
    @Modifying
    @Query("update GameSession s set s.teamName = null where s.teamName is not null and s.expiresAt < :before")
    int clearTeamNamesExpiredBefore(Instant before);
}
