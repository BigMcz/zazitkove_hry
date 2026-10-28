package cz.mibcon.zazitky.accesscode.domain;

import java.time.Instant;
import java.util.UUID;

import cz.mibcon.zazitky.game.domain.Game;
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

@Entity
@Table(name = "access_code_batch")
public class AccessCodeBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(nullable = false)
    private int quantity;

    private Integer unitPriceCzk;

    @Column(length = 300)
    private String note;

    /** Validity of a code from its activation; null = the code never expires (development codes). */
    private Integer validityHours = 5;

    /** Every entry of a code starts a new game instead of resuming the running one (development codes). */
    @Column(nullable = false)
    private boolean multiSession;

    /**
     * When the printed cards were handed over to the partner. Until then the codes are in production
     * and cannot be used; the partner sees only handed-over batches.
     */
    private Instant handedOverAt;

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

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public Integer getUnitPriceCzk() {
        return unitPriceCzk;
    }

    public void setUnitPriceCzk(Integer unitPriceCzk) {
        this.unitPriceCzk = unitPriceCzk;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getValidityHours() {
        return validityHours;
    }

    public void setValidityHours(Integer validityHours) {
        this.validityHours = validityHours;
    }

    public boolean isMultiSession() {
        return multiSession;
    }

    public void setMultiSession(boolean multiSession) {
        this.multiSession = multiSession;
    }

    public Instant getHandedOverAt() {
        return handedOverAt;
    }

    public void setHandedOverAt(Instant handedOverAt) {
        this.handedOverAt = handedOverAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
