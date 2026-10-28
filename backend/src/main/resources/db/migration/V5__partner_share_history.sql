-- =========================================================
-- Podíl partnera s platností od data (historie podílů).
-- Aktivovaný kód se vyúčtuje podle podílu platného v den aktivace (Europe/Prague):
-- řádek s nejvyšším valid_from <= datum aktivace. Nahrazuje partner.revenue_share_pct
-- a podíl zafixovaný na dávce (V4).
-- =========================================================
CREATE TABLE partner_share (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_id  UUID         NOT NULL REFERENCES partner(id) ON DELETE CASCADE,
    valid_from  DATE         NOT NULL,
    share_pct   NUMERIC(5,2) NOT NULL CHECK (share_pct BETWEEN 0 AND 100),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_partner_share_from UNIQUE (partner_id, valid_from)
);

-- Dosavadní podíl platí od založení partnera
INSERT INTO partner_share (partner_id, valid_from, share_pct)
SELECT id, (created_at AT TIME ZONE 'Europe/Prague')::date, revenue_share_pct
FROM partner
WHERE revenue_share_pct IS NOT NULL;

ALTER TABLE access_code_batch DROP COLUMN revenue_share_pct;
ALTER TABLE partner DROP COLUMN revenue_share_pct;
