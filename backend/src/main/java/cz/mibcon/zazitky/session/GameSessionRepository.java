package cz.mibcon.zazitky.session;

import java.util.Optional;
import java.util.UUID;

import cz.mibcon.zazitky.session.domain.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {

    Optional<GameSession> findByToken(UUID token);
}
