package cz.mibcon.zazitky.session;

import java.util.UUID;

import cz.mibcon.zazitky.session.domain.AnswerAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerAttemptRepository extends JpaRepository<AnswerAttempt, UUID> {
}
