-- =========================================================
-- Podíl partnera zafixovaný na dávce kódů (jako cena za kód).
-- Změna partner.revenue_share_pct se tak projeví jen u nových dávek a nepřepíše
-- zpětně měsíční přehledy, podle kterých se už fakturovalo.
-- =========================================================
ALTER TABLE access_code_batch
    ADD COLUMN revenue_share_pct NUMERIC(5,2) CHECK (revenue_share_pct BETWEEN 0 AND 100);

UPDATE access_code_batch b
SET revenue_share_pct = p.revenue_share_pct
FROM partner p
WHERE p.id = b.partner_id;
