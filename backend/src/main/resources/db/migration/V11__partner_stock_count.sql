-- Stock count of the cards of one game entered by the castle (docs/03-datovy-model.md). We know only
-- the activated cards; a card sold but never activated is still counted as in stock, so the castle can
-- lower the number to what it really has. The stock then goes on from the latest count of the game:
-- later deliveries are added, later activations subtracted, never above the number computed from the
-- activations.
CREATE TABLE partner_stock_count (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_id         UUID        NOT NULL REFERENCES partner(id) ON DELETE CASCADE,
    game_id            UUID        NOT NULL REFERENCES game(id) ON DELETE CASCADE,
    counted_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    cards_in_stock     INT         NOT NULL CHECK (cards_in_stock >= 0),
    -- our number at the time of the count, for checking afterwards
    computed_in_stock  INT         NOT NULL CHECK (computed_in_stock >= cards_in_stock),
    partner_user_id    UUID        REFERENCES partner_user(id) ON DELETE SET NULL
);
CREATE INDEX ix_stock_count_partner_game ON partner_stock_count(partner_id, game_id, counted_at DESC);
