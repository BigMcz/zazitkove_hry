package cz.mibcon.zazitky.cardprint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import cz.mibcon.zazitky.cardprint.domain.CardTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardTemplateRepository extends JpaRepository<CardTemplate, UUID> {

    /** The valid version: the highest one. */
    Optional<CardTemplate> findFirstByGameIdOrderByVersionDesc(UUID gameId);

    List<CardTemplate> findByGameIdOrderByVersionDesc(UUID gameId);
}
