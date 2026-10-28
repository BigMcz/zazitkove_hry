package cz.mibcon.zazitky.game;

import java.util.UUID;

import cz.mibcon.zazitky.game.domain.AcceptedAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcceptedAnswerRepository extends JpaRepository<AcceptedAnswer, UUID> {
}
