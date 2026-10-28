package cz.mibcon.zazitky.session;

import java.util.UUID;

import cz.mibcon.zazitky.session.domain.SessionProgress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionProgressRepository extends JpaRepository<SessionProgress, UUID> {
}
