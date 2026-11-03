-- =========================================================
-- Kartičky zdarma: dávka jednorázových kódů, které se nepočítají do tržby, fakturace ani skladu hradu
-- (dárky, soutěže, novináři). Sdílený demo kód (jeden kód na mnoha kartičkách) je dávka s multi_session.
-- =========================================================
ALTER TABLE access_code_batch
    ADD COLUMN free BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_batch_free_not_shared CHECK (NOT (free AND multi_session));
