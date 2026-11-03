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
import org.hibernate.annotations.Generated;

@Entity
@Table(name = "access_code_batch")
public class AccessCodeBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Short number for people (D12), assigned by the database. */
    @Generated
    @Column(nullable = false, insertable = false, updatable = false)
    private Integer batchNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(nullable = false)
    private int quantity;

    @Column(length = 300)
    private String note;

    /** Validity of a code from its activation; null = the code never expires (development codes). */
    private Integer validityHours = 5;

    /** Every entry of a code starts a new game instead of resuming the running one (development codes). */
    @Column(nullable = false)
    private boolean multiSession;

    /** Cards given away: not counted in the revenue, invoicing or stock of the castle. */
    @Column(nullable = false)
    private boolean free;

    /**
     * When the printed cards were handed over to the partner. Until then the codes are in production
     * and cannot be used; the partner sees only handed-over batches.
     */
    private Instant handedOverAt;

    /** Version of the card template the batch is printed with (card_template); set by the first print export. */
    private UUID cardTemplateId;

    /** First export of the back sides (the codes); only informative. */
    private Instant printedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public Integer getBatchNo() {
        return batchNo;
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

    public boolean isFree() {
        return free;
    }

    public void setFree(boolean free) {
        this.free = free;
    }

    public Instant getHandedOverAt() {
        return handedOverAt;
    }

    public void setHandedOverAt(Instant handedOverAt) {
        this.handedOverAt = handedOverAt;
    }

    public UUID getCardTemplateId() {
        return cardTemplateId;
    }

    public void setCardTemplateId(UUID cardTemplateId) {
        this.cardTemplateId = cardTemplateId;
    }

    public Instant getPrintedAt() {
        return printedAt;
    }

    public void setPrintedAt(Instant printedAt) {
        this.printedAt = printedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
