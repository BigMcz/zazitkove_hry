package cz.mibcon.zazitky.billing;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import cz.mibcon.zazitky.billing.domain.PartnerInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerInvoiceRepository extends JpaRepository<PartnerInvoice, UUID> {

    Optional<PartnerInvoice> findByPartnerIdAndMonth(UUID partnerId, LocalDate month);

    List<PartnerInvoice> findByPartnerId(UUID partnerId);
}
