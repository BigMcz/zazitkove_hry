-- =========================================================
-- Jazykové verze hry (Q5): hra v dalším jazyce je samostatný řádek game se svými stanovišti, nápovědami,
-- odpověďmi a nahrávkami, navázaný na hlavní hru. Kódy a dávky patří vždy hlavní hře, jazyk si hráč
-- vybere před startem a relace (game_session.game_id) ukazuje na verzi, kterou opravdu hraje.
-- main_game_id: hlavní hra, NULL = hra sama je hlavní.
-- =========================================================
ALTER TABLE game
    ADD COLUMN main_game_id UUID REFERENCES game(id) ON DELETE CASCADE;

CREATE INDEX ix_game_main_game ON game(main_game_id);

ALTER TABLE game
    ADD CONSTRAINT uq_game_main_language UNIQUE (main_game_id, language);
