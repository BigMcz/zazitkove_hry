-- =========================================================
-- Obrázky, poslechové úlohy a text „Kam dál“.
-- cover_image_url / image_url: cesta k obrázku na webu, NULL = bez obrázku (station.image_url je už z V1).
-- listen_only: příběh je úloha na poslech, text se zobrazí až na vyžádání (přepis).
-- next_text: rada vypravěče, kam má hráč jít po vyřešení kroku; zobrazí se pod odměnou.
-- =========================================================
ALTER TABLE game
    ADD COLUMN cover_image_url VARCHAR(500);

ALTER TABLE station
    ADD COLUMN image_alt   VARCHAR(300),
    ADD COLUMN listen_only BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN next_text   TEXT;
