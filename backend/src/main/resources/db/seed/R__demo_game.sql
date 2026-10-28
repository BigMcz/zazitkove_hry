-- =========================================================
-- Demo hra „Tajemství katova klíče“ (docs/04-prototypova-hra.md)
-- Repeatable migrace, běží jen v profilu dev. Idempotentní: nejprve smaže demo data
-- podle pevných UUID (kaskádně i stanoviště a rozehrané relace demo hry), pak je vloží znovu.
--
-- Pevná UUID:
--   00000000-0000-0000-0000-00000000000X  partner (1), lokalita (2), hra (3)
--   00000000-0000-0000-0001-00000000000S  stanoviště S
--   00000000-0000-0000-0002-0000000000SN  nápověda N stanoviště S
--   00000000-0000-0000-0003-0000000000SN  přijímaná odpověď N stanoviště S
--   00000000-0000-0000-0004-0000000000SN  možnost výběru N stanoviště S
--   00000000-0000-0000-0005-000000000001  vývojová dávka kódů, 00000000-0000-0000-0006-000000000001 její kód
--   00000000-0000-0000-0007-000000000001  vývojový účet partnera (smaže se kaskádně s partnerem)
--   00000000-0000-0000-0008-000000000001  podíl partnera (smaže se kaskádně s partnerem)
--
-- accepted_answer.normalized_value se vyplňuje ručně podle TextNormalizer (TEXT/CHOICE: normalize,
-- NUMBER: normalizeNumber); shodu hlídá test DemoGameSeedTest.
-- =========================================================

-- Relace a kódy demo hry (i dávky vytvořené v dev administraci), aby šla smazat hra
DELETE FROM game_session WHERE game_id = '00000000-0000-0000-0000-000000000003';
DELETE FROM access_code WHERE batch_id IN
    (SELECT id FROM access_code_batch WHERE game_id = '00000000-0000-0000-0000-000000000003');
DELETE FROM access_code_batch WHERE game_id = '00000000-0000-0000-0000-000000000003';
DELETE FROM game WHERE id = '00000000-0000-0000-0000-000000000003';
DELETE FROM location WHERE id = '00000000-0000-0000-0000-000000000002';
DELETE FROM partner WHERE id = '00000000-0000-0000-0000-000000000001';

INSERT INTO partner (id, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'Demo partner');

-- Podíl Demo partnera 50 % od začátku roku 2026 (historie podílů, viz partner_share)
INSERT INTO partner_share (id, partner_id, valid_from, share_pct)
VALUES ('00000000-0000-0000-0008-000000000001', '00000000-0000-0000-0000-000000000001', DATE '2026-01-01', 50.00);

INSERT INTO location (id, partner_id, name, city)
VALUES ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'Demo hrad', 'Loket');

INSERT INTO game (id, location_id, slug, title, subtitle, intro_text, outro_text,
                  estimated_minutes, session_validity_hours, language, status)
VALUES ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002',
        'demo-katuv-klic',
        'Tajemství katova klíče',
        'Demo hra — vyzkoušej si, jak to funguje',
        'Píše se rok 1621. Městský kat mistr Jakub zmizel beze stopy a s ním i klíč od hradního žaláře. Purkrabí tvrdí, že kat utekl. Jenže v katově světnici se našel lístek: „Kdo chce klíč najít, musí jít po mých stopách. Každá stopa skrývá jedno písmeno.“

Projdi pět míst, kudy kat naposledy šel, a slož z písmen, kde klíč ukryl.',
        'Klíč ležel celou dobu tam, kde by ho nikdo nehledal — pod prahem katovy vlastní světnice. Mistr Jakub neutekl. Ukryl klíč, aby žalář nemohl otevřít nikdo, kdo by chtěl osvobodit vězně dřív, než soud rozhodne o jejich vině. Díky tobě se klíč vrací na hrad a příběh mistra Jakuba může být konečně vyprávěn celý.',
        20, 24, 'cs', 'PUBLISHED');

-- ---------------------------------------------------------
-- Stanoviště
-- ---------------------------------------------------------
INSERT INTO station (id, game_id, order_no, title, story_text, task_text, task_type, reward_text) VALUES
('00000000-0000-0000-0001-000000000001', '00000000-0000-0000-0000-000000000003', 1,
 'Katova světnice',
 'Ve světnici je cítit kouř z vyhaslého krbu. Na stole leží lístek psaný úhledným písmem.',
 'Na lístku stojí hádanka: „Čím víc z ní bereš, tím je větší. Co je to?“',
 'TEXT',
 'Správně! Pod lístkem je do stolu vyryto písmeno K. Kat si zjevně dal záležet, aby po sobě nechal stopu. Další stopa vede do hradní věže.'),

('00000000-0000-0000-0001-000000000002', '00000000-0000-0000-0000-000000000003', 2,
 'Hradní věž',
 'Strážný u věže vzpomíná: „Kat tudy šel včera v noci a schody počítal nahlas, jako by si je chtěl zapamatovat.“',
 'Věž má 5 pater. Do každého patra vede 12 schodů, jen do posledního jsou o 4 schody méně. Kolik schodů kat vystoupal až nahoru?',
 'NUMBER',
 'Přesně tolik! Na 56. schodu je do kamene vyryto písmeno L. Z okna věže je vidět sklepení pod nádvořím.'),

('00000000-0000-0000-0001-000000000003', '00000000-0000-0000-0000-000000000003', 3,
 'Sklepení',
 'Ve sklepení je tma. Žalářník vypráví: „Kat sem přišel s mečem, provazem a lucernou. Odcházel ale potmě — meč měl u pasu a provaz přes rameno.“',
 'Co kat ve sklepení zanechal?',
 'CHOICE',
 'Ano! Na lucerně je sazemi napsané písmeno Í. Pod lucernou leží útržek papíru se zvláštním nápisem.'),

('00000000-0000-0000-0001-000000000004', '00000000-0000-0000-0000-000000000003', 4,
 'Šifra na útržku',
 'Na útržku je napsáno slovo, které na první pohled nedává smysl: CSBOB. Pod ním drobným písmem: „O jedno zpět.“',
 'Rozlušti, kam kat šel dál. Každé písmeno je v abecedě posunuté o jedno dál.',
 'TEXT',
 'Brána! Na pantu hradní brány je vyryto písmeno Č. Teď máš všechna písmena.'),

('00000000-0000-0000-0001-000000000005', '00000000-0000-0000-0000-000000000003', 5,
 'Tajenka',
 'Stojíš u brány a v hlavě skládáš písmena, která jsi cestou našel.',
 'Slož písmena ze všech stop v pořadí, v jakém jsi je našel. Co kat ukryl?',
 'TEXT',
 'Klíč! A teď už víš, kde ho hledat…');

-- ---------------------------------------------------------
-- Nápovědy
-- ---------------------------------------------------------
INSERT INTO hint (id, station_id, order_no, text, penalty_seconds) VALUES
('00000000-0000-0000-0002-000000000011', '00000000-0000-0000-0001-000000000001', 1, 'Myslí se něco, co vzniká kopáním.', 60),
('00000000-0000-0000-0002-000000000012', '00000000-0000-0000-0001-000000000001', 2, 'Začíná na „J“ a hrobník ji kope každý den.', 120),
('00000000-0000-0000-0002-000000000021', '00000000-0000-0000-0001-000000000002', 1, 'Spočítej nejdřív, kolik by to bylo, kdyby všechna patra měla 12 schodů.', 60),
('00000000-0000-0000-0002-000000000022', '00000000-0000-0000-0001-000000000002', 2, '5 × 12 = 60. A teď odečti rozdíl u posledního patra.', 120),
('00000000-0000-0000-0002-000000000031', '00000000-0000-0000-0001-000000000003', 1, 'Když odcházel potmě, něco mu chybělo.', 60),
('00000000-0000-0000-0002-000000000041', '00000000-0000-0000-0001-000000000004', 1, 'Místo C napiš písmeno, které je v abecedě těsně před ním.', 60),
('00000000-0000-0000-0002-000000000042', '00000000-0000-0000-0001-000000000004', 2, 'C → B, S → R… dokonči zbytek.', 120),
('00000000-0000-0000-0002-000000000051', '00000000-0000-0000-0001-000000000005', 1, 'Písmena byla K, L, Í, Č.', 60);

-- ---------------------------------------------------------
-- Přijímané odpovědi
-- ---------------------------------------------------------
INSERT INTO accepted_answer (id, station_id, value, normalized_value) VALUES
('00000000-0000-0000-0003-000000000011', '00000000-0000-0000-0001-000000000001', 'jáma', 'jama'),
('00000000-0000-0000-0003-000000000012', '00000000-0000-0000-0001-000000000001', 'díra', 'dira'),
('00000000-0000-0000-0003-000000000021', '00000000-0000-0000-0001-000000000002', '56', '56'),
('00000000-0000-0000-0003-000000000031', '00000000-0000-0000-0001-000000000003', 'Lucernu', 'lucernu'),
('00000000-0000-0000-0003-000000000032', '00000000-0000-0000-0001-000000000003', 'lucerna', 'lucerna'),
('00000000-0000-0000-0003-000000000041', '00000000-0000-0000-0001-000000000004', 'brána', 'brana'),
('00000000-0000-0000-0003-000000000051', '00000000-0000-0000-0001-000000000005', 'klíč', 'klic');

-- ---------------------------------------------------------
-- Možnosti výběru (stanoviště 3)
-- ---------------------------------------------------------
INSERT INTO station_choice (id, station_id, order_no, text) VALUES
('00000000-0000-0000-0004-000000000031', '00000000-0000-0000-0001-000000000003', 1, 'Meč'),
('00000000-0000-0000-0004-000000000032', '00000000-0000-0000-0001-000000000003', 2, 'Provaz'),
('00000000-0000-0000-0004-000000000033', '00000000-0000-0000-0001-000000000003', 3, 'Lucernu'),
('00000000-0000-0000-0004-000000000034', '00000000-0000-0000-0001-000000000003', 4, 'Klíč');

-- ---------------------------------------------------------
-- Vývojový přístupový kód DEMO-KATA: nevyprší, každé zadání založí novou hru
-- (kód se ukládá bez pomlčky, viz AccessCodeFormat)
-- ---------------------------------------------------------
INSERT INTO access_code_batch (id, partner_id, game_id, quantity, validity_hours, multi_session, note)
VALUES ('00000000-0000-0000-0005-000000000001', '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000003', 1, NULL, TRUE, 'Vývojový kód (bez expirace, každé zadání = nová hra)');

INSERT INTO access_code (id, batch_id, code)
VALUES ('00000000-0000-0000-0006-000000000001', '00000000-0000-0000-0005-000000000001', 'DEMOKATA');

-- ---------------------------------------------------------
-- Vývojový účet partnera pro administraci hradu: hrad / hrad-loket (jen lokální vývoj)
-- ---------------------------------------------------------
INSERT INTO partner_user (id, partner_id, username, password_hash, display_name)
VALUES ('00000000-0000-0000-0007-000000000001', '00000000-0000-0000-0000-000000000001', 'hrad',
        '{bcrypt}$2a$10$xgzBVRPfBX5BRJLbpvvpbO77bHzUG9KWgs.O/bI7GcStYQPUUaT/m', 'Pokladna Demo hradu');
