-- Actions played in the phone without a signal and sent later (POST /sessions/current/sync,
-- docs/02-architektura.md "Offline režim"). The id comes from the phone: an action sent twice
-- (the answer of the first sending got lost) is applied only once.
CREATE TABLE session_event (
    id           UUID PRIMARY KEY,
    session_id   UUID        NOT NULL REFERENCES game_session(id) ON DELETE CASCADE,
    type         VARCHAR(10) NOT NULL CHECK (type IN ('ANSWER','HINT')),
    played_at    TIMESTAMPTZ NOT NULL,
    received_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_session_event_session ON session_event(session_id);
