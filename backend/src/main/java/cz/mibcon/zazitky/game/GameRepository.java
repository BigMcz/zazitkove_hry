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

    List<Game> findByMainGameIdAndStatusOrderByLanguage(UUID mainGameId, GameStatus status);

    /** Main games of the partner by title. */
    @Query("select g from Game g join g.location l where l.partner.id = :partnerId and g.mainGame is null order by g.title")
    List<Game> findMainByPartnerId(UUID partnerId);

    /** Main games only: codes are made for them, the language versions share them. */
    @Query("select g from Game g join fetch g.location l join fetch l.partner where g.mainGame is null order by g.title")
    List<Game> findAllMainWithPartner();
}
