-- =========================================================
-- Namluvené texty: odkaz na zvukový soubor (MP3) k textům hry.
-- NULL = text bez zvuku. Cesta je relativní k webu (např. /audio/demo/intro-vypravec.mp3).
-- =========================================================
ALTER TABLE game
    ADD COLUMN intro_audio_url VARCHAR(500),
    ADD COLUMN outro_audio_url VARCHAR(500);

ALTER TABLE station
    ADD COLUMN story_audio_url  VARCHAR(500),
    ADD COLUMN reward_audio_url VARCHAR(500);
