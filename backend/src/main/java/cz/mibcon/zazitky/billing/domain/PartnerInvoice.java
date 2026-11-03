package cz.mibcon.zazitky.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import cz.mibcon.zazitky.game.domain.Partner;
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

/** A month of the partner marked as invoiced by the operator. */
@Entity
@Table(name = "partner_invoice")
public class PartnerInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    /** First day of the invoiced month. */
    @Column(nullable = false)
    private LocalDate month;

    @Column(nullable = false)
    private LocalDate invoicedOn;

    /** Amount to invoice when the month was marked. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amountCzk;

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

    public LocalDate getMonth() {
        return month;
    }

    public void setMonth(LocalDate month) {
        this.month = month;
    }

    public LocalDate getInvoicedOn() {
        return invoicedOn;
    }

    public void setInvoicedOn(LocalDate invoicedOn) {
        this.invoicedOn = invoicedOn;
    }

    public BigDecimal getAmountCzk() {
        return amountCzk;
    }

    public void setAmountCzk(BigDecimal amountCzk) {
        this.amountCzk = amountCzk;
    }
}
