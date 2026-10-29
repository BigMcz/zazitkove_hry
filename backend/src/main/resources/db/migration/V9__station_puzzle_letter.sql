-- =========================================================
-- Písmeno do tajenky, které hráč získá vyřešením stanoviště. NULL = stanoviště písmeno nedává.
-- Aplikace sbírá písmena vyřešených stanovišť a zobrazuje je zpřeházená (pořadí je pevné pro každou hru).
-- =========================================================
ALTER TABLE station
    ADD COLUMN puzzle_letter VARCHAR(5);
