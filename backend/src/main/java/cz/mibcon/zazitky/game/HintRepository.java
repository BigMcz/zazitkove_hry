package cz.mibcon.zazitky.game;

import java.util.UUID;

import cz.mibcon.zazitky.game.domain.Hint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HintRepository extends JpaRepository<Hint, UUID> {
}
