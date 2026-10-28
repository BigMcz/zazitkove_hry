package cz.mibcon.zazitky.game;

import java.util.UUID;

import cz.mibcon.zazitky.game.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, UUID> {
}
