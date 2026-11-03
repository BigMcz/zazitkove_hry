-- =========================================================
-- Prodejní cena hry s platností od data (stejně jako podíl hradu, partner_share). Tržba za aktivovaný kód
-- je cena hlavní hry platná v den aktivace (Europe/Prague); cena na dávce kartiček se ruší.
-- Ceny ze stávajících dávek se převedou: každá dávka s cenou dá cenu hry platnou ode dne svého vytvoření
-- (víc dávek téže hry v jeden den: platí cena té poslední).
-- =========================================================
CREATE TABLE game_price (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id     UUID        NOT NULL REFERENCES game(id) ON DELETE CASCADE,
    valid_from  DATE        NOT NULL,
    price_czk   INT         NOT NULL CHECK (price_czk >= 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_game_price_game_valid_from UNIQUE (game_id, valid_from)
);

INSERT INTO game_price (game_id, valid_from, price_czk)
SELECT DISTINCT ON (b.game_id, (b.created_at AT TIME ZONE 'Europe/Prague')::date)
       b.game_id, (b.created_at AT TIME ZONE 'Europe/Prague')::date, b.unit_price_czk
FROM access_code_batch b
WHERE b.unit_price_czk IS NOT NULL
ORDER BY b.game_id, (b.created_at AT TIME ZONE 'Europe/Prague')::date, b.created_at DESC;

ALTER TABLE access_code_batch
    DROP COLUMN unit_price_czk;
