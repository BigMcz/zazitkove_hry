package cz.mibcon.zazitky.game;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import cz.mibcon.zazitky.game.domain.PartnerShare;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerShareRepository extends JpaRepository<PartnerShare, UUID> {

    List<PartnerShare> findByPartnerIdOrderByValidFrom(UUID partnerId);

    List<PartnerShare> findAllByOrderByValidFrom();

    Optional<PartnerShare> findByPartnerIdAndValidFrom(UUID partnerId, LocalDate validFrom);

    Optional<PartnerShare> findByIdAndPartnerId(UUID id, UUID partnerId);
}
