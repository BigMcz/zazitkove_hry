package cz.mibcon.zazitky.game.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Partner's share of the revenue valid from a date (Europe/Prague) until the next entry.
 * An activated code is settled with the share valid on the day of its activation.
 */
@Entity
@Table(name = "partner_share")
public class PartnerShare {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Column(nullable = false)
    private LocalDate validFrom;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal sharePct;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public Partner getPartner() {
        return partner;
    }

    public void setPartner(Partner partner) {
        this.partner = partner;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public BigDecimal getSharePct() {
        return sharePct;
    }

    public void setSharePct(BigDecimal sharePct) {
        this.sharePct = sharePct;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
