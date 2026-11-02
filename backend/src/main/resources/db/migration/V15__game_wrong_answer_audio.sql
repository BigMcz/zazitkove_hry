-- =========================================================
-- Namluvená hláška po špatné odpovědi (hra pro děti).
-- wrong_answer_audio_url: cesta k nahrávce na webu, NULL = hláška jen jako text.
-- =========================================================
ALTER TABLE game
    ADD COLUMN wrong_answer_audio_url VARCHAR(500);
