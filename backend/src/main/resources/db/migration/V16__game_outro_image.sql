-- =========================================================
-- Obrázek k závěru hry (nahoře na stránce finále).
-- outro_image_url: cesta k obrázku na webu, NULL = závěr bez obrázku.
-- =========================================================
ALTER TABLE game
    ADD COLUMN outro_image_url VARCHAR(500);
