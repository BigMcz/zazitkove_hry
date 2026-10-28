package cz.mibcon.zazitky.game;

import java.util.UUID;

import cz.mibcon.zazitky.game.domain.StationChoice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StationChoiceRepository extends JpaRepository<StationChoice, UUID> {
}
