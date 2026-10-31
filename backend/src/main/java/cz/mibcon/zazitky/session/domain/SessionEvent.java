package cz.mibcon.zazitky.session.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** An action played in the phone and received later; its id from the phone makes repeated sending harmless. */
@Entity
@Table(name = "session_event")
public class SessionEvent {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SessionEventType type;

    @Column(nullable = false)
    private Instant playedAt;

    @Column(nullable = false)
    private Instant receivedAt;

    protected SessionEvent() {
    }

    public SessionEvent(UUID id, GameSession session, SessionEventType type, Instant playedAt, Instant receivedAt) {
        this.id = id;
        this.session = session;
        this.type = type;
        this.playedAt = playedAt;
        this.receivedAt = receivedAt;
    }

    public UUID getId() {
        return id;
    }

    public GameSession getSession() {
        return session;
    }

    public SessionEventType getType() {
        return type;
    }

    public Instant getPlayedAt() {
        return playedAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
