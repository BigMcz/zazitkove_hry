package cz.mibcon.zazitky.game;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import cz.mibcon.zazitky.game.domain.Game;
import cz.mibcon.zazitky.game.domain.GameStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameRepository extends JpaRepository<Game, UUID> {

    Optional<Game> findBySlug(String slug);

    Optional<Game> findByIdAndStatus(UUID id, GameStatus status);

    @Query("select g from Game g join fetch g.location l join fetch l.partner order by g.title")
    List<Game> findAllWithPartner();
}
