-- =========================================================
-- Hra pro děti (docs/06-prototypova-detska-hra.md).
-- for_children: písmena tajenky v pořadí (nezpřeházená), nápovědy bez údaje o penalizaci,
--               finále bez časů s diplomem.
-- wrong_answer_text: hláška po špatné odpovědi, NULL = výchozí „To není ono. Zkus to znovu.“
-- =========================================================
ALTER TABLE game
    ADD COLUMN for_children      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN wrong_answer_text VARCHAR(300);
