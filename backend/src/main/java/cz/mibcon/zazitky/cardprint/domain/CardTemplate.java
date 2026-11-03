package cz.mibcon.zazitky.cardprint.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import cz.mibcon.zazitky.cardprint.CardLayout;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One version of the card template of a game: graphics of both sides and the layout of the back. Editing creates
 * a new version, a version never changes, so a batch printed with it can be reprinted the same way.
 */
@Entity
@Table(name = "card_template")
public class CardTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID gameId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private UUID frontFileId;

    @Column(nullable = false)
    private UUID backFileId;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal widthMm;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal heightMm;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal bleedMm;

    @Column(name = "qr_x_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal qrXMm;

    @Column(name = "qr_y_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal qrYMm;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal qrSizeMm;

    @Column(nullable = false)
    private boolean qrWhiteBox;

    @Column(name = "code_x_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal codeXMm;

    @Column(name = "code_y_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal codeYMm;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal codeFontPt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private CodeAlign codeAlign;

    @Column(nullable = false, length = 7)
    private String codeColor;

    @Column(name = "serial_x_mm", precision = 6, scale = 2)
    private BigDecimal serialXMm;

    @Column(name = "serial_y_mm", precision = 6, scale = 2)
    private BigDecimal serialYMm;

    @Column(precision = 4, scale = 1)
    private BigDecimal serialFontPt;

    @Column(length = 300)
    private String note;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public CardLayout layout() {
        return new CardLayout(widthMm.doubleValue(), heightMm.doubleValue(), bleedMm.doubleValue(),
                qrXMm.doubleValue(), qrYMm.doubleValue(), qrSizeMm.doubleValue(), qrWhiteBox,
                codeXMm.doubleValue(), codeYMm.doubleValue(), codeFontPt.doubleValue(), codeAlign, codeColor,
                toDouble(serialXMm), toDouble(serialYMm), toDouble(serialFontPt));
    }

    public void setLayout(CardLayout layout) {
        widthMm = mm(layout.widthMm());
        heightMm = mm(layout.heightMm());
        bleedMm = mm(layout.bleedMm());
        qrXMm = mm(layout.qrXMm());
        qrYMm = mm(layout.qrYMm());
        qrSizeMm = mm(layout.qrSizeMm());
        qrWhiteBox = layout.qrWhiteBox();
        codeXMm = mm(layout.codeXMm());
        codeYMm = mm(layout.codeYMm());
        codeFontPt = pt(layout.codeFontPt());
        codeAlign = layout.codeAlign();
        codeColor = layout.codeColor().toUpperCase();
        boolean serial = layout.hasSerial();
        serialXMm = serial ? mm(layout.serialXMm()) : null;
        serialYMm = serial ? mm(layout.serialYMm()) : null;
        serialFontPt = serial ? pt(layout.serialFontPt()) : null;
    }

    private static BigDecimal mm(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal pt(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    public UUID getId() {
        return id;
    }

    public UUID getGameId() {
        return gameId;
    }

    public void setGameId(UUID gameId) {
        this.gameId = gameId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public UUID getFrontFileId() {
        return frontFileId;
    }

    public void setFrontFileId(UUID frontFileId) {
        this.frontFileId = frontFileId;
    }

    public UUID getBackFileId() {
        return backFileId;
    }

    public void setBackFileId(UUID backFileId) {
        this.backFileId = backFileId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
