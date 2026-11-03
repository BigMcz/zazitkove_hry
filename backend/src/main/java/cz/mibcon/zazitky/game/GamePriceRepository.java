package cz.mibcon.zazitky.game;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import cz.mibcon.zazitky.game.domain.GamePrice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GamePriceRepository extends JpaRepository<GamePrice, UUID> {

    List<GamePrice> findByGameIdInOrderByValidFrom(Collection<UUID> gameIds);

    Optional<GamePrice> findByGameIdAndValidFrom(UUID gameId, LocalDate validFrom);

    Optional<GamePrice> findByIdAndGameId(UUID id, UUID gameId);
}
