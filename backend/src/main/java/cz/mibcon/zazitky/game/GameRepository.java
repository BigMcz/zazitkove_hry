package cz.mibcon.zazitky.game;

import java.util.Optional;
import java.util.UUID;

import cz.mibcon.zazitky.game.domain.Game;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, UUID> {

    Optional<Game> findBySlug(String slug);
}
