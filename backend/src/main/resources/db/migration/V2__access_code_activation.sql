-- =========================================================
-- Přístupové kódy: platnost od aktivace, vývojové kódy, více her na jeden kód
-- =========================================================

-- Platnost kódu v hodinách od aktivace (valid_until = activated_at + validity_hours).
-- NULL = kód nevyprší (vývojové kódy).
ALTER TABLE access_code_batch
    ADD COLUMN validity_hours INT DEFAULT 5 CHECK (validity_hours > 0);

-- Každé zadání kódu založí novou hru místo pokračování v té rozehrané (vývojové kódy pro opakované testování).
ALTER TABLE access_code_batch
    ADD COLUMN multi_session BOOLEAN NOT NULL DEFAULT FALSE;

-- Jeden kód může mít víc relací: vývojový kód, nebo nová hra po opuštění té předchozí během platnosti kódu.
ALTER TABLE game_session DROP CONSTRAINT game_session_access_code_id_key;
CREATE INDEX ix_session_access_code ON game_session(access_code_id);

-- Měsíční přehled aktivací pro fakturaci.
CREATE INDEX ix_access_code_activated_at ON access_code(activated_at);
