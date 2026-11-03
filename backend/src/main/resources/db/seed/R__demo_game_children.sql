-- =========================================================
-- Dětská demo hra „Poklad mistra Husse“ (docs/06-prototypova-detska-hra.md)
-- Repeatable migrace, běží jen v profilu dev, po R__demo_game.sql (ta zakládá Demo partnera a lokalitu
-- Hrad Loket, na které obě hry stojí). Idempotentní: nejprve smaže dětskou hru podle pevných UUID
-- (kaskádně i stanoviště a rozehrané relace), pak ji vloží znovu.
--
-- Pevná UUID (druhá skupina 0001 odlišuje dětskou hru od dospělé):
--   00000000-0000-0001-0000-000000000003  hra
--   00000000-0000-0001-0001-0000000000SS  stanoviště SS
--   00000000-0000-0001-0002-000000SSNNNN  nápověda NN stanoviště SS
--   00000000-0000-0001-0003-000000SSNNNN  přijímaná odpověď NN stanoviště SS
--   00000000-0000-0001-0004-000000SSNNNN  možnost výběru NN stanoviště SS
--   00000000-0000-0001-0005-000000000001  vývojová dávka kódů, 00000000-0000-0001-0006-000000000001 její kód
--
-- Hra pro děti (for_children, V12): písmena v pořadí, bez časů, diplom; Kryštofova hláška po špatné odpovědi.
-- Nápovědy jsou bez penalizace.
-- accepted_answer.normalized_value se vyplňuje ručně podle TextNormalizer; shodu hlídá DemoGameSeedTest.
-- =========================================================

DELETE FROM game_session WHERE game_id = '00000000-0000-0001-0000-000000000003';
DELETE FROM access_code WHERE batch_id IN
    (SELECT id FROM access_code_batch WHERE game_id = '00000000-0000-0001-0000-000000000003');
DELETE FROM access_code_batch WHERE game_id = '00000000-0000-0001-0000-000000000003';
DELETE FROM game WHERE id = '00000000-0000-0001-0000-000000000003';

-- ---------------------------------------------------------
-- Hra
-- ---------------------------------------------------------
INSERT INTO game (id, location_id, slug, title, subtitle, intro_text, outro_text,
                  estimated_minutes, session_validity_hours, language, status, for_children, wrong_answer_text,
                  wrong_answer_audio_url,
                  intro_audio_url, outro_audio_url, cover_image_url, diploma_title, diploma_image_url,
                  outro_image_url)
VALUES ('00000000-0000-0001-0000-000000000003', '00000000-0000-0000-0000-000000000002',
        'deti-poklad-mistra-husse',
        'Poklad mistra Husse',
        'Hra pro děti od 6 let s krkavcem Kryštofem',
        'Hrad Loket, roku 1835. Na nádvoří stojí starý pán s laskavýma očima. Jmenuje se Karl Huss.

„Dobrý den, malý písaři! Když jsem byl mladší, byl jsem katem města Chebu. Trestal jsem zločince, když tak rozhodl soud. Dnes sbírám zajímavé věci do muzea a všechno zapisuji do kroniky. Přijel jsem si pro starou truhlu. Jenže někdo mi sebral klíček!“

„Krá! To já! Jsem krkavec Kryštof a lesklé věci mám nejradši na světě. Klíček vrátím, ale jen tomu, kdo vyřeší všechny moje hádanky. Krá krá!“

Za každou hádanku ti Kryštof dá jedno písmenko. Na konci z nich složíš kouzelné slovo, které otevře truhlu.',
        '„Poklad! Krá! Tak tady máš ten klíček… i když byl moc hezky lesklý.“

„Děkuji, písaři. Podívejme, co je v truhle… Staré mince a kamínky do mé sbírky. A tady dole čistá kniha. Do té začneme psát novou kroniku. A první zápis bude o tobě, můj nejšikovnější pomocníku.“

Karl Huss opravdu žil. Psal kroniku a staral se o muzeum na zámku Kynžvart. Krkavce Kryštofa jsme si vymysleli. Ale skuteční krkavci jsou moc chytří ptáci a umí napodobit spoustu zvuků.

„Krá! A ještě něco! Tohle se ti povedlo perfektně. Všechny moje hádanky máš vyřešené, to se mi ještě nestalo! Za to od nás dostáváš diplom pomocníka kronikáře. Najdeš ho hned tady dole. Ukaž ho doma všem! Krá krá!“',
        30, 24, 'cs', 'PUBLISHED', TRUE, 'Krá, to ne! Zkus to znovu.', '/audio/deti/spatne.mp3',
        -- Nahrávky z audio/deti-poklad-mistra-husse/ (ElevenLabs), obrázky z grafika/deti-poklad-mistra-husse/
        -- (ChatGPT, převod tools/grafika/web_images.py); ostatní se doplní postupně
        '/audio/deti/intro.mp3', '/audio/deti/zaver.mp3', '/images/deti/cover.webp', 'Pomocník kronikáře',
        '/images/deti/diplom.webp', '/images/deti/zaver.webp');

-- Prodejní cena kartičky (V18)
INSERT INTO game_price (game_id, valid_from, price_czk)
VALUES ('00000000-0000-0001-0000-000000000003', DATE '2026-01-01', 149);

-- ---------------------------------------------------------
-- Kroky (stanoviště)
-- ---------------------------------------------------------
INSERT INTO station (id, game_id, order_no, title, story_text, task_text, task_type, reward_text, next_text,
                     story_audio_url, reward_audio_url, image_url, image_alt, listen_only, puzzle_letter) VALUES
('00000000-0000-0001-0001-000000000001', '00000000-0000-0001-0000-000000000003', 1,
 'Vyhlídka nad Ohří',
 'Huss: „Podívej se dolů. Vidíš řeku? Jmenuje se Ohře. Teče kolem skály a ohýbá se, jako když ohneš ruku. Zkus to taky! Ohni ruku. Ta špička uprostřed je tvůj loket.“

Kryštof: „Krá! A teď moje hádanka!“',
 'Proč se hrad jmenuje Loket?',
 'CHOICE',
 'Krá! Správně! Řeka dělá loket, a proto se hrad jmenuje Loket. Tady máš první písmenko: P. P jako písař!',
 'Dojdi k hradní bráně. Kryštof už tam na tebe čeká.',
 '/audio/deti/st01-pribeh.mp3', '/audio/deti/st01-odmena.mp3',
 '/images/deti/st01.webp', 'Krkavec Kryštof letí nad hradem na skále v ohybu řeky',
 FALSE, 'P'),
('00000000-0000-0001-0001-000000000002', '00000000-0000-0001-0000-000000000003', 2,
 'Hradní brána',
 'Huss: „Když jsem sem přijel poprvé, už tu nebydleli páni. Z hradu se stalo vězení. U brány stál žalářník, který hlídal vězně. Na kruhu měl spoustu klíčů a ty cinkaly: zlatý, stříbrný, zlatý, stříbrný, zlatý…“

Kryštof: „Krá! A jaký klíč přijde teď?“',
 'Zlatý, stříbrný, zlatý, stříbrný, zlatý… Jaký klíč je na řadě?',
 'CHOICE',
 'Krá! Stříbrný! Druhé písmenko je O.',
 'Projdi na nádvoří. Kryštof si pro tebe připravil zvukovou hádanku.',
 '/audio/deti/st02-pribeh.mp3', '/audio/deti/st02-odmena.mp3',
 '/images/deti/st02.webp', 'Vlídný žalářník se zlatými a stříbrnými klíči a Kryštofem na rameni',
 FALSE, 'O'),
('00000000-0000-0001-0001-000000000003', '00000000-0000-0001-0000-000000000003', 3,
 'Kdo se ozval?',
 'Kryštof: „Krá! Umím napodobit každé zvíře, které kdy na hradě žilo. Poslouchej pozorně!

I-ha-há! … Haf, haf! … Kykyryký! … Mňau!

Krá!“',
 'Kolik různých zvířat Kryštof napodobil?',
 'NUMBER',
 'Krá! Čtyři! Kůň, pes, kohout a kočka. Koně tahali vozy a kočky chytaly myši. Písmenko K.',
 'Sestup do sklepení k vězení. Neboj, dnes už je prázdné.',
 '/audio/deti/st03-pribeh.mp3', '/audio/deti/st03-odmena.mp3',
 '/images/deti/st03.webp', 'Kryštof zpívá na okraji studny na hradním nádvoří',
 TRUE, 'K'),
('00000000-0000-0001-0001-000000000004', '00000000-0000-0001-0000-000000000003', 4,
 'Mříže',
 'Huss: „Tady bývalo vězení. Zavírali sem lidi, kteří udělali něco hodně zlého. Aby neutekli, měla okna silné železné mříže.“

Kryštof: „Krá! Tři tyče jdou shora dolů a dvě tyče zleva doprava.“',
 'Kolik železných tyčí má mříž dohromady?',
 'NUMBER',
 'Krá! Pět! Přes takovou mříž neproletí ani krkavec. Písmenko L.',
 'Vyjdi nahoru do hradního paláce. Tam Huss psal svou kroniku.',
 '/audio/deti/st04-pribeh.mp3', '/audio/deti/st04-odmena.mp3',
 '/images/deti/st04.webp', 'Kryštof nakukuje do prosvíceného sklepa oknem s mříží',
 FALSE, 'L'),
('00000000-0000-0001-0001-000000000005', '00000000-0000-0001-0000-000000000003', 5,
 'Husí brk',
 'Huss: „Všechno, co se stalo, jsem si zapisoval do kroniky. To je kniha, do které se píše, co se kde přihodilo. Tenkrát se nepsalo propiskou ani tužkou, ale ptačím pírkem. Říká se mu brk. Namočil jsem ho do inkoustu a psal.“

Kryštof: „Krá! A z kterého ptáka bylo Hussovo pero? Napovím: zní to skoro jako jeho jméno!“',
 'Z kterého ptáka bylo pero, kterým Huss psal?',
 'CHOICE',
 'Krá! Z husy! Husí brky se opravdu používaly nejčastěji. Huss a husa, to se pamatuje samo. Písmenko A.',
 'Vystoupej na hradní věž. Tam má Kryštof své hnízdo.',
 '/audio/deti/st05-pribeh.mp3', '/audio/deti/st05-odmena.mp3',
 '/images/deti/st05.webp', 'Pan Huss píše husím brkem do kroniky, Kryštof ho pozoruje z knihovny',
 FALSE, 'A'),
('00000000-0000-0001-0001-000000000006', '00000000-0000-0001-0000-000000000003', 6,
 'Kryštofovo hnízdo',
 'Kryštof: „Krá! Tady nahoře je moje hnízdo. Mám tu schované lesklé knoflíky. Poslouchej, jak je počítám:

cink… cink… cink… cink… cink… cink!

Krá!“',
 'Kolik lesklých knoflíků má Kryštof v hnízdě?',
 'NUMBER',
 'Krá! Šest! A ten nejlesklejší poklad je klíček od truhly. Dám ti ho, až řekneš tajné slovo. Poslední písmenko je D.',
 'Vrať se na nádvoří k truhle. Huss tam čeká.',
 '/audio/deti/st06-pribeh.mp3', '/audio/deti/st06-odmena.mp3',
 '/images/deti/st06.webp', 'Kryštof sedí v hnízdě plném lesklých knoflíků a se zlatým klíčkem na hradní věži',
 TRUE, 'D'),
('00000000-0000-0001-0001-000000000007', '00000000-0000-0001-0000-000000000003', 7,
 'Truhla',
 'Huss stojí u staré truhly. Na víku je zámek a nad ním šest prázdných políček. Máš šest písmenek: P, O, K, L, A, D.

Kryštof: „Krá! Přečti je za sebou a řekni tajné slovo!“',
 'Jaké slovo otevře truhlu?',
 'TEXT',
 'Cvak! Zámek povolil a víko truhly se pomalu otevírá.',
 NULL,
 '/audio/deti/st07-pribeh.mp3', '/audio/deti/st07-odmena.mp3',
 '/images/deti/st07.webp', 'Kryštof podává panu Hussovi klíček u otevírající se truhly',
 FALSE, NULL);

-- ---------------------------------------------------------
-- Nápovědy (bez penalizace, u dětí nechceme stres z času)
-- ---------------------------------------------------------
INSERT INTO hint (id, station_id, order_no, text, penalty_seconds) VALUES
('00000000-0000-0001-0002-000000010001', '00000000-0000-0001-0001-000000000001', 1, 'Ohni ruku a podívej se na řeku. Nejsou si podobné?', 0),
('00000000-0000-0001-0002-000000020001', '00000000-0000-0001-0001-000000000002', 1, 'Klíče se pořád střídají. Co přijde vždycky po zlatém?', 0),
('00000000-0000-0001-0002-000000030001', '00000000-0000-0001-0001-000000000003', 1, 'Poslechni si to znovu. Na každé zvíře zvedni jeden prst.', 0),
('00000000-0000-0001-0002-000000040001', '00000000-0000-0001-0001-000000000004', 1, 'Zvedni tři prsty na jedné ruce a dva na druhé. Kolik je to dohromady?', 0),
('00000000-0000-0001-0002-000000050001', '00000000-0000-0001-0001-000000000005', 1, 'Huss… Hu… Který pták zní podobně?', 0),
('00000000-0000-0001-0002-000000060001', '00000000-0000-0001-0001-000000000006', 1, 'Poslechni si to znovu. Na každé cinknutí zvedni jeden prst. Budeš potřebovat obě ruce.', 0),
('00000000-0000-0001-0002-000000070001', '00000000-0000-0001-0001-000000000007', 1, 'P-O-K-L-A-D. Co se schovává v truhlách?', 0);

-- ---------------------------------------------------------
-- Přijímané odpovědi
-- ---------------------------------------------------------
INSERT INTO accepted_answer (id, station_id, value, normalized_value) VALUES
('00000000-0000-0001-0003-000000010001', '00000000-0000-0001-0001-000000000001', 'Řeka se ohýbá jako ruka', 'reka se ohyba jako ruka'),
('00000000-0000-0001-0003-000000020001', '00000000-0000-0001-0001-000000000002', 'Stříbrný', 'stribrny'),
('00000000-0000-0001-0003-000000030001', '00000000-0000-0001-0001-000000000003', '4', '4'),
('00000000-0000-0001-0003-000000040001', '00000000-0000-0001-0001-000000000004', '5', '5'),
('00000000-0000-0001-0003-000000050001', '00000000-0000-0001-0001-000000000005', 'Z husy', 'z husy'),
('00000000-0000-0001-0003-000000060001', '00000000-0000-0001-0001-000000000006', '6', '6'),
('00000000-0000-0001-0003-000000070001', '00000000-0000-0001-0001-000000000007', 'poklad', 'poklad');

-- ---------------------------------------------------------
-- Možnosti výběru
-- ---------------------------------------------------------
INSERT INTO station_choice (id, station_id, order_no, text) VALUES
('00000000-0000-0001-0004-000000010001', '00000000-0000-0001-0001-000000000001', 1, 'Řeka se ohýbá jako ruka'),
('00000000-0000-0001-0004-000000010002', '00000000-0000-0001-0001-000000000001', 2, 'Bydlel tu pan Loket'),
('00000000-0000-0001-0004-000000010003', '00000000-0000-0001-0001-000000000001', 3, 'Hrad má tvar boty'),
('00000000-0000-0001-0004-000000020001', '00000000-0000-0001-0001-000000000002', 1, 'Zlatý'),
('00000000-0000-0001-0004-000000020002', '00000000-0000-0001-0001-000000000002', 2, 'Stříbrný'),
('00000000-0000-0001-0004-000000020003', '00000000-0000-0001-0001-000000000002', 3, 'Dřevěný'),
('00000000-0000-0001-0004-000000050001', '00000000-0000-0001-0001-000000000005', 1, 'Z husy'),
('00000000-0000-0001-0004-000000050002', '00000000-0000-0001-0001-000000000005', 2, 'Ze slepice'),
('00000000-0000-0001-0004-000000050003', '00000000-0000-0001-0001-000000000005', 3, 'Z krkavce');

-- ---------------------------------------------------------
-- Vývojový přístupový kód DEMO-DETI: nevyprší, každé zadání založí novou hru, dávka je rovnou „předaná“
-- ---------------------------------------------------------
INSERT INTO access_code_batch (id, partner_id, game_id, quantity, validity_hours, multi_session, note, handed_over_at)
VALUES ('00000000-0000-0001-0005-000000000001', '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0001-0000-000000000003', 1, NULL, TRUE, 'Vývojový kód (bez expirace, každé zadání = nová hra)',
        now());

INSERT INTO access_code (id, batch_id, code)
VALUES ('00000000-0000-0001-0006-000000000001', '00000000-0000-0001-0005-000000000001', 'DEMODETI');
