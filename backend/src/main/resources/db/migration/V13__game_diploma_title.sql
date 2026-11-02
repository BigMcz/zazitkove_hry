-- =========================================================
-- Diplom na konci hry pro děti (game.for_children, V12).
-- diploma_title: titul na diplomu, např. „Pomocník kronikáře“; NULL = diplom bez titulu.
-- =========================================================
ALTER TABLE game
    ADD COLUMN diploma_title VARCHAR(100);
