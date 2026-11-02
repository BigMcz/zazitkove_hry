-- =========================================================
-- Vlastní obrázek na diplom hry pro děti.
-- diploma_image_url: cesta k obrázku na webu, NULL = na diplomu je obrázek hry (cover_image_url).
-- =========================================================
ALTER TABLE game
    ADD COLUMN diploma_image_url VARCHAR(500);
