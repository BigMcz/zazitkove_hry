package cz.mibcon.zazitky.game;

import java.util.UUID;

import cz.mibcon.zazitky.game.domain.Station;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StationRepository extends JpaRepository<Station, UUID> {
}
