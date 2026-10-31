package cz.mibcon.zazitky.session;

import java.util.UUID;

import cz.mibcon.zazitky.session.domain.SessionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionEventRepository extends JpaRepository<SessionEvent, UUID> {
}
