-- =========================================================
-- Demo hra „Katova povinnost“ (docs/04-prototypova-hra.md) a její německá verze „Die Pflicht des Henkers“
-- Repeatable migrace, běží jen v profilu dev. Idempotentní: nejprve smaže demo hru podle pevných UUID
-- (kaskádně i stanoviště a rozehrané relace), pak ji vloží znovu. Partnera, lokalitu a vývojový účet
-- jen přepíše, protože na stejné lokalitě stojí i dětská hra (R__demo_game_children.sql, běží po této).
--
-- Pevná UUID:
--   00000000-0000-0000-0000-00000000000X  partner (1), lokalita (2), hra (3)
--   00000000-0000-0000-0001-0000000000SS  stanoviště SS
--   00000000-0000-0000-0002-000000SSNNNN  nápověda NN stanoviště SS
--   00000000-0000-0000-0003-000000SSNNNN  přijímaná odpověď NN stanoviště SS
--   00000000-0000-0000-0004-000000SSNNNN  možnost výběru NN stanoviště SS
--   00000000-0000-0000-0005-000000000001  vývojová dávka kódů, 00000000-0000-0000-0006-000000000001 její kód
--   00000000-0000-0000-0007-000000000001  vývojový účet partnera (smaže se kaskádně s partnerem)
--   00000000-0000-0000-0008-000000000001  podíl partnera (smaže se kaskádně s partnerem)
--   00000000-0000-0002-…                  německá verze, stejné schéma s druhou skupinou 0002
--
-- Německá verze je v tomto souboru, ne ve vlastním: smaže se kaskádně s hlavní hrou (main_game_id), takže
-- se musí vložit znovu pokaždé, když se tento soubor spustí.
--
-- accepted_answer.normalized_value se vyplňuje ručně podle TextNormalizer (TEXT/CHOICE: normalize,
-- NUMBER: normalizeNumber); shodu hlídá test DemoGameSeedTest.
-- =========================================================

-- Relace a kódy demo hry (i dávky vytvořené v dev administraci), aby šla smazat hra
DELETE FROM game_session WHERE game_id IN
    (SELECT id FROM game WHERE id = '00000000-0000-0000-0000-000000000003'
                            OR main_game_id = '00000000-0000-0000-0000-000000000003');
DELETE FROM access_code WHERE batch_id IN
    (SELECT id FROM access_code_batch WHERE game_id = '00000000-0000-0000-0000-000000000003');
DELETE FROM access_code_batch WHERE game_id = '00000000-0000-0000-0000-000000000003';
DELETE FROM game WHERE id = '00000000-0000-0000-0000-000000000003';

INSERT INTO partner (id, name, contact_person, contact_email)
VALUES ('00000000-0000-0000-0000-000000000001', 'Demo partner', 'Jan Kastelán', 'demo@example.cz')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, contact_person = EXCLUDED.contact_person,
                               contact_email = EXCLUDED.contact_email;

-- Podíl Demo partnera 50 % od začátku roku 2026 (historie podílů, viz partner_share)
INSERT INTO partner_share (id, partner_id, valid_from, share_pct)
VALUES ('00000000-0000-0000-0008-000000000001', '00000000-0000-0000-0000-000000000001', DATE '2026-01-01', 50.00)
ON CONFLICT (partner_id, valid_from) DO UPDATE SET share_pct = EXCLUDED.share_pct;

INSERT INTO location (id, partner_id, name, city)
VALUES ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'Hrad Loket (demo)', 'Loket')
ON CONFLICT (id) DO UPDATE SET partner_id = EXCLUDED.partner_id, name = EXCLUDED.name, city = EXCLUDED.city;

-- ---------------------------------------------------------
-- Hra
-- ---------------------------------------------------------
INSERT INTO game (id, location_id, slug, title, subtitle, intro_text, outro_text,
                  estimated_minutes, session_validity_hours, language, status,
                  intro_audio_url, outro_audio_url, cover_image_url)
VALUES ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002',
        'demo-katova-povinnost',
        'Katova povinnost',
        'Demo hra — skutečný příběh posledního chebského kata',
        'Zámek Kynžvart, roku 1835. Starý pán Karl Huss, kustod zámeckého muzea, drží v ruce úřední dopis. Centrální účetní kancelář mu píše, že mu kdysi špatně spočítali odměnu za popravu na hradě Loket — a že má vrátit přeplatek.

„Kdysi jsem byl posledním katem města Chebu. Všechno jsem si zapisoval do kroniky — každý den, každý krejcar. Pojď, písaři. Projdeme spolu ještě jednou cestu z léta 1822. Na každém místě ti povím, co se tam stalo, a ty mi pomůžeš ověřit, že jsem nic nezapomněl.“

Na každém místě získáš jedno písmeno. Dohromady dají slovo, podle kterého mistr Huss žil.',
        '„Povinnost. Lidé si o katech vyprávějí hrůzy, ale já jsem nikdy nedělal víc, než mi soud uložil. Psal jsem kroniku, aby si i jiní mohli přečíst, jak to bylo. Přeplatek tedy vrátím. Pořádek musí být. A tobě, písaři, děkuji. Prošel jsi se mnou celou cestu, nic jsi nevynechal a na každou hádanku jsi přišel. Kdybych tě měl tenkrát v Chebu, byla by moje kronika hotová o rok dřív.“

Karl Huss byl posledním chebským katem. Jeho kronika se dostala do sbírek zámku Kynžvart, kde od roku 1827 působil jako kustod muzea. Díky ní dnes známe i rozhovor, který vedl v loketském žaláři s Karlem Hainsem. A dopis z účtárny? Ten přišel opravdu — třináct let po popravě.

Gratulujeme, dohrál jsi Katovu povinnost! Teď víš o posledním chebském katovi víc než většina návštěvníků hradu. Až půjdeš kolem loketské věže, vzpomeň si na mistra Husse.',
        30, 24, 'cs', 'PUBLISHED',
        -- Nahrávky a obrázky podle docs/nahravky.md a docs/grafika.md; dokud soubor chybí, aplikace přehrávač
        -- i obrázek skryje. Soubory jsou ve frontend/public/audio/demo/ a frontend/public/images/demo/.
        '/audio/demo/intro.mp3', '/audio/demo/zaver.mp3', '/images/demo/cover.webp');

-- Prodejní cena kartičky (V18); německá verze ji sdílí
INSERT INTO game_price (game_id, valid_from, price_czk)
VALUES ('00000000-0000-0000-0000-000000000003', DATE '2026-01-01', 249);

-- ---------------------------------------------------------
-- Kroky (stanoviště)
-- ---------------------------------------------------------
INSERT INTO station (id, game_id, order_no, title, story_text, task_text, task_type, reward_text, next_text,
                     story_audio_url, reward_audio_url, image_url, image_alt, listen_only, puzzle_letter) VALUES
('00000000-0000-0000-0001-000000000001', '00000000-0000-0000-0000-000000000003', 1,
 'Vyhlídka nad Ohří',
 'Když jsem v srpnu 1822 přijížděl od Chebu, uviděl jsem hrad na skále a řeku Ohři, jak ho obtáčí skoro ze všech stran. Jako by ho držela v ohnuté paži.',
 'Podle čeho dostal hrad i město jméno Loket?',
 'CHOICE',
 'Správně. Řeka Ohře tu opisuje ohyb jako loket ohnuté paže — odtud jméno hradu i města. Do tajenky získáváš písmeno P.',
 'Vystoupej k hradní bráně. Tudy Huss před dvěma sty lety vjížděl na hrad.',
 '/audio/demo/st01-pribeh.mp3', '/audio/demo/st01-odmena.mp3',
 '/images/demo/st01.webp', 'Hrad Loket na skále v ohybu řeky Ohře', FALSE, 'P'),
('00000000-0000-0000-0001-000000000002', '00000000-0000-0000-0000-000000000003', 2,
 'Hradní brána',
 'Ve své kronice jsem Loket nazval šlikovským hradem — kdysi tu vládli mocní Šlikové. Ale v roce, kdy jsem přijel, tu už nesídlili páni. Za branou na mě čekal žalářník se svazkem klíčů.',
 'K čemu sloužil hrad Loket od roku 1822?',
 'CHOICE',
 'Ano. Od roku 1822 sloužil hrad jako věznice, a to až do poloviny 20. století. Právě tady čekal Karl Hains na svůj rozsudek. Písmeno O.',
 'Projdi na nádvoří. Tam se dozvíš, proč Huss na Loket vůbec přijel.',
 '/audio/demo/st02-pribeh.mp3', '/audio/demo/st02-odmena.mp3',
 '/images/demo/st02.webp', 'Žalářník se svazkem klíčů v hradní bráně', FALSE, 'O'),
('00000000-0000-0000-0001-000000000003', '00000000-0000-0000-0000-000000000003', 3,
 'Rozsypaná kronika',
 'Na jaře 1822 zavraždil Karl Hains svého souseda, právního úředníka Matyáše Gerstnera, kterému dlužil peníze. Mistr Huss všechno zapsal do kroniky, ale listy se ti rozsypaly:

A — Soud vynesl rozsudek smrti (24. května)

B — Hains zavraždil Matyáše Gerstnera (16. dubna)

C — Rozsudek byl vykonán (19. srpna)

D — Kat navštívil odsouzeného v cele (18. srpna)',
 'Seřaď listy podle data a napiš jejich písmena za sebou (např. ABCD).',
 'TEXT',
 'Přesně tak. Od vraždy k rozsudku uběhlo jen 38 dní, k popravě 125 dní. Tehdejší soudy byly rychlé. Písmeno V.',
 'Sestup do žaláře. Tam stanul Huss poprvé tváří v tvář odsouzenému.',
 '/audio/demo/st03-pribeh.mp3', '/audio/demo/st03-odmena.mp3',
 '/images/demo/st03.webp', 'Čtyři rozsypané listy staré kroniky', FALSE, 'V'),
('00000000-0000-0000-0001-000000000004', '00000000-0000-0000-0000-000000000003', 4,
 'Rozhovor v cele',
 'Huss: „Buď pochválen Pán Ježíš Kristus.“

Hains: „Na věky věkův amen.“

Huss: „Milý Karle, přicházím, abych ho navštívil a se mu ukázal. Jsem ten, který musí na něm rozsudek smrti vykonati. Jestlipak se také on k svému konci náležitě připravil?“

Hains: „Ano. Toto je obraz mého Spasitele, který mi, jak doufám, bude milosrdný. Uznávám své velké hříchy a zvláště lituji svého zločinu, který jsem spáchal na nevinném Gerstnerovi.“',
 'Poslechni si rozhovor kata s odsouzeným. Co ukázal Hains katovi, když se ho ptal, jestli je připravený?',
 'TEXT',
 'Ano, obraz Spasitele. Tenhle rozhovor není vymyšlený — Huss ho zapsal do čtvrtého dílu své kroniky města Chebu a dodnes si ho můžeme přečíst. Písmeno I.',
 'Projdi do mučírny. Huss ti tam chce něco vysvětlit o katech.',
 '/audio/demo/st04-pribeh.mp3', '/audio/demo/st04-odmena.mp3',
 '/images/demo/st04.webp', 'Kat a odsouzený v klenuté cele při svíčce', TRUE, 'I'),
('00000000-0000-0000-0001-000000000005', '00000000-0000-0000-0000-000000000003', 5,
 'Mučírna',
 'Lidé si o katech vyprávějí hrůzy. I Hains se v cele bál, že ho budu trýznit. Odpověděl jsem mu tak, jak jsem to myslel.',
 'Co Huss odsouzenému slíbil?',
 'CHOICE',
 '„Krutost je mi cizí. Vykonám jen to, co mi soudní úřad ukládá. Nic víc a nic méně.“ Mučení při vyšetřování zrušila Marie Terezie už v roce 1776, Huss tedy mučírnu znal jen z vyprávění. Písmeno N.',
 'Vrať se do hradního paláce. Tam Huss trávil s odsouzeným poslední dny.',
 '/audio/demo/st05-pribeh.mp3', '/audio/demo/st05-odmena.mp3',
 '/images/demo/st05.webp', 'Opuštěná mučírna s pokrytými nástroji', FALSE, 'N'),
('00000000-0000-0000-0001-000000000006', '00000000-0000-0000-0000-000000000003', 6,
 'Poslední role',
 'Posledních dní před popravou neměl Hains mnoho času na černé myšlenky. Huss mu dal do ruky papír se slovy, která měl na své poslední cestě pronést. Několikrát s ním text zkoušel, opravoval ho a vysvětloval — jako režisér s hercem.',
 'Proč to Huss dělal?',
 'CHOICE',
 'Ano. Historik Miloš Říha to nazývá psychologicky velmi chytrým způsobem, jak odsouzeného zaměstnat. Huss byl hluboce věřící člověk. Odsouzeného nezabíjel, spíš ho vypravoval na poslední cestu. Písmeno N.',
 'Zamiř k románské rotundě, nejstarší stavbě hradu. Tam na Hainse čekal kněz.',
 '/audio/demo/st06-pribeh.mp3', '/audio/demo/st06-odmena.mp3',
 '/images/demo/st06.webp', 'Kat učí odsouzeného text z listu papíru', FALSE, 'N'),
('00000000-0000-0000-0001-000000000007', '00000000-0000-0000-0000-000000000003', 7,
 'Poslední cesta',
 'Ráno 19. srpna 1822 připravil Hainse k poslední cestě velebný pán Seifert. Justiční rada pak odsouzeného předal katovi. A Hains promluvil:

„Prosím, abych směl svoji poslední cestu vykonati pěšky, jelikož se cítím dostatečně silným. Vykupitel Ježíš Kristus musel také až na popraviště, nesa těžký kříž, pěšky. A já, velký zločinec, bych měl jeti?“',
 'Poslechni si Hainsovu prosbu. Proč chtěl jít pěšky?',
 'CHOICE',
 'Ano. Průvod se v doprovodu vojska a mnoha lidí z okolí vydal pěšky na nedaleký vrch, vybraný jako popravní místo. Písmeno O.',
 'Vystoupej na hradní věž. Odtud je vidět krajina, kudy průvod šel.',
 '/audio/demo/st07-pribeh.mp3', '/audio/demo/st07-odmena.mp3',
 '/images/demo/st07.webp', 'Kněz u vchodu do románské rotundy za ranního světla', TRUE, 'O'),
('00000000-0000-0000-0001-000000000008', '00000000-0000-0000-0000-000000000003', 8,
 'Hradní věž',
 'Z věže vidíš kopce kolem Lokte. Tím dnem pro mě celý případ skončil. Jenže úřad ho uzavřel mnohem později: dopis z účtárny, který teď držím v ruce, přišel roku 1835.',
 'Kolik let uplynulo od popravy na Lokti do dopisu z účtárny?',
 'NUMBER',
 'Třináct let! Huss mezitím odložil katovský meč a od roku 1827 se staral o sbírky zámeckého muzea v Kynžvartu. Tam se dostala i jeho kronika. Písmeno S.',
 'Sestup k hradní pokladně — tam, kde dnes kupuješ vstupenky. Je čas spočítat peníze.',
 '/audio/demo/st08-pribeh.mp3', '/audio/demo/st08-odmena.mp3',
 '/images/demo/st08.webp', 'Výhled z hradní věže na kopce kolem Lokte', FALSE, 'S'),
('00000000-0000-0000-0001-000000000009', '00000000-0000-0000-0000-000000000003', 9,
 'Účet z roku 1835',
 'Za svou námahu jsem tehdy dostal 47 zlatých konvenční měny. A teď mi píšou, že mám vrátit šest zlatých a dvacet šest krejcarů! Jeden zlatý má šedesát krejcarů. Spočítej mi to, písaři, ať vím, kolik drobných mám odpočítat.',
 'Kolik krejcarů musí Huss vrátit?',
 'NUMBER',
 '386 krejcarů, správně. Úřad Hussovi po třinácti letech přepočítal odměnu, cestovné i diety. Historik Říha to glosuje: „Záviděníhodný pořádek vládl ve starém Rakousku!“ Písmeno T.',
 'Vrať se na nádvoří a slož tajenku.',
 '/audio/demo/st09-pribeh.mp3', '/audio/demo/st09-odmena.mp3',
 '/images/demo/st09.webp', 'Úřední dopis z roku 1835 s pečetí a hromádka mincí', FALSE, 'T'),
('00000000-0000-0000-0001-000000000010', '00000000-0000-0000-0000-000000000003', 10,
 'Tajenka',
 'Prošel jsi celou cestu mistra Husse. V tajence máš devět písmen, jenže jsou zpřeházená. Přeskládej je a najdeš slovo, podle kterého Huss žil.',
 'Jaké slovo řekl Huss justičnímu radovi, když si přebíral odsouzeného?',
 'TEXT',
 'Povinnost. „Vykonám svoji povinnost co nejbedlivěji, podle moci a práva mi propůjčeného,“ řekl tehdy Huss.',
 NULL,
 '/audio/demo/st10-pribeh.mp3', '/audio/demo/st10-odmena.mp3',
 '/images/demo/st10.webp', 'Otevřená kronika s brkem a zapečetěným dopisem', FALSE, NULL);

-- ---------------------------------------------------------
-- Nápovědy
-- ---------------------------------------------------------
INSERT INTO hint (id, station_id, order_no, text, penalty_seconds) VALUES
('00000000-0000-0000-0002-000000010001', '00000000-0000-0000-0001-000000000001', 1, 'Podívej se, co dělá řeka kolem skály.', 60),
('00000000-0000-0000-0002-000000020001', '00000000-0000-0000-0001-000000000002', 1, 'Kdo čekal za branou a co měl v ruce?', 60),
('00000000-0000-0000-0002-000000030001', '00000000-0000-0000-0001-000000000003', 1, 'Začni dubnem, skonči srpnem.', 60),
('00000000-0000-0000-0002-000000030002', '00000000-0000-0000-0001-000000000003', 2, 'Pořadí začíná B, A…', 120),
('00000000-0000-0000-0002-000000040001', '00000000-0000-0000-0001-000000000004', 1, 'Byl to obrázek. Koho na něm měl?', 60),
('00000000-0000-0000-0002-000000050001', '00000000-0000-0000-0001-000000000005', 1, 'Kat byl jen vykonavatel. Kdo rozhodoval o trestu?', 60),
('00000000-0000-0000-0002-000000060001', '00000000-0000-0000-0001-000000000006', 1, 'Na co by Hains myslel, kdyby neměl co dělat?', 60),
('00000000-0000-0000-0002-000000070001', '00000000-0000-0000-0001-000000000007', 1, 'Koho Hains ve své prosbě zmínil?', 60),
('00000000-0000-0000-0002-000000080001', '00000000-0000-0000-0001-000000000008', 1, 'Poprava byla v roce 1822.', 60),
('00000000-0000-0000-0002-000000090001', '00000000-0000-0000-0001-000000000009', 1, 'Nejdřív převeď zlaté: 6 × 60.', 60),
('00000000-0000-0000-0002-000000090002', '00000000-0000-0000-0001-000000000009', 2, '360 krejcarů a ještě 26.', 120),
('00000000-0000-0000-0002-000000100001', '00000000-0000-0000-0001-000000000010', 1, 'Písmena byla P, O, V, I, N, N, O, S, T.', 60);

-- ---------------------------------------------------------
-- Přijímané odpovědi
-- ---------------------------------------------------------
INSERT INTO accepted_answer (id, station_id, value, normalized_value) VALUES
('00000000-0000-0000-0003-000000010001', '00000000-0000-0000-0001-000000000001', 'Ohyb řeky jako ohnutá paže', 'ohyb reky jako ohnuta paze'),
('00000000-0000-0000-0003-000000020001', '00000000-0000-0000-0001-000000000002', 'Věznice', 'veznice'),
('00000000-0000-0000-0003-000000030001', '00000000-0000-0000-0001-000000000003', 'BADC', 'badc'),
('00000000-0000-0000-0003-000000030002', '00000000-0000-0000-0001-000000000003', 'B A D C', 'b a d c'),
('00000000-0000-0000-0003-000000040001', '00000000-0000-0000-0001-000000000004', 'obraz Spasitele', 'obraz spasitele'),
('00000000-0000-0000-0003-000000040002', '00000000-0000-0000-0001-000000000004', 'obraz', 'obraz'),
('00000000-0000-0000-0003-000000040003', '00000000-0000-0000-0001-000000000004', 'obrázek', 'obrazek'),
('00000000-0000-0000-0003-000000040004', '00000000-0000-0000-0001-000000000004', 'Spasitele', 'spasitele'),
('00000000-0000-0000-0003-000000040005', '00000000-0000-0000-0001-000000000004', 'Spasitel', 'spasitel'),
('00000000-0000-0000-0003-000000040006', '00000000-0000-0000-0001-000000000004', 'obraz Krista', 'obraz krista'),
('00000000-0000-0000-0003-000000040007', '00000000-0000-0000-0001-000000000004', 'Krista', 'krista'),
('00000000-0000-0000-0003-000000040008', '00000000-0000-0000-0001-000000000004', 'Kristus', 'kristus'),
('00000000-0000-0000-0003-000000040009', '00000000-0000-0000-0001-000000000004', 'Ježíše', 'jezise'),
('00000000-0000-0000-0003-000000040010', '00000000-0000-0000-0001-000000000004', 'Ježíš', 'jezis'),
('00000000-0000-0000-0003-000000050001', '00000000-0000-0000-0001-000000000005', 'Žádné mučení, jen co nařídil soud', 'zadne muceni jen co naridil soud'),
('00000000-0000-0000-0003-000000060001', '00000000-0000-0000-0001-000000000006', 'Aby odsouzeného zaměstnal a uklidnil', 'aby odsouzeneho zamestnal a uklidnil'),
('00000000-0000-0000-0003-000000070001', '00000000-0000-0000-0001-000000000007', 'Protože i Kristus šel pěšky', 'protoze i kristus sel pesky'),
('00000000-0000-0000-0003-000000080001', '00000000-0000-0000-0001-000000000008', '13', '13'),
('00000000-0000-0000-0003-000000090001', '00000000-0000-0000-0001-000000000009', '386', '386'),
('00000000-0000-0000-0003-000000100001', '00000000-0000-0000-0001-000000000010', 'povinnost', 'povinnost');

-- ---------------------------------------------------------
-- Možnosti výběru
-- ---------------------------------------------------------
INSERT INTO station_choice (id, station_id, order_no, text) VALUES
('00000000-0000-0000-0004-000000010001', '00000000-0000-0000-0001-000000000001', 1, 'Ohyb řeky jako ohnutá paže'),
('00000000-0000-0000-0004-000000010002', '00000000-0000-0000-0001-000000000001', 2, 'Jméno prvního majitele'),
('00000000-0000-0000-0004-000000010003', '00000000-0000-0000-0001-000000000001', 3, 'Stará délková míra'),
('00000000-0000-0000-0004-000000010004', '00000000-0000-0000-0001-000000000001', 4, 'Tvar hradní věže'),
('00000000-0000-0000-0004-000000020001', '00000000-0000-0000-0001-000000000002', 1, 'Věznice'),
('00000000-0000-0000-0004-000000020002', '00000000-0000-0000-0001-000000000002', 2, 'Pivovar'),
('00000000-0000-0000-0004-000000020003', '00000000-0000-0000-0001-000000000002', 3, 'Kasárna'),
('00000000-0000-0000-0004-000000020004', '00000000-0000-0000-0001-000000000002', 4, 'Škola'),
('00000000-0000-0000-0004-000000050001', '00000000-0000-0000-0001-000000000005', 1, 'Žádné mučení, jen co nařídil soud'),
('00000000-0000-0000-0004-000000050002', '00000000-0000-0000-0001-000000000005', 2, 'Že mu pomůže utéct'),
('00000000-0000-0000-0004-000000050003', '00000000-0000-0000-0001-000000000005', 3, 'Že požádá o milost'),
('00000000-0000-0000-0004-000000050004', '00000000-0000-0000-0001-000000000005', 4, 'Že ho popraví tajně'),
('00000000-0000-0000-0004-000000060001', '00000000-0000-0000-0001-000000000006', 1, 'Aby odsouzeného zaměstnal a uklidnil'),
('00000000-0000-0000-0004-000000060002', '00000000-0000-0000-0001-000000000006', 2, 'Aby se Hains naučil číst'),
('00000000-0000-0000-0004-000000060003', '00000000-0000-0000-0001-000000000006', 3, 'Aby mohl hrát divadlo pro hosty'),
('00000000-0000-0000-0004-000000060004', '00000000-0000-0000-0001-000000000006', 4, 'Aby Hainse vyslechl'),
('00000000-0000-0000-0004-000000070001', '00000000-0000-0000-0001-000000000007', 1, 'Protože i Kristus šel pěšky'),
('00000000-0000-0000-0004-000000070002', '00000000-0000-0000-0001-000000000007', 2, 'Protože se bál koní'),
('00000000-0000-0000-0004-000000070003', '00000000-0000-0000-0001-000000000007', 3, 'Aby mohl utéct'),
('00000000-0000-0000-0004-000000070004', '00000000-0000-0000-0001-000000000007', 4, 'Protože byl vůz rozbitý');

-- =========================================================
-- Německá verze „Die Pflicht des Henkers“ (jazyková verze hlavní hry, V17): stejné obrázky, vlastní texty,
-- odpovědi, nápovědy a nahrávky (frontend/public/audio/demo-de/). Tajenka PFLICHTEN (9 písmen jako POVINNOST).
-- Pevná UUID mají druhou skupinu 0002 (stanoviště 00000000-0000-0002-0001-0000000000SS atd.).
-- =========================================================
INSERT INTO game (id, location_id, main_game_id, slug, title, subtitle, intro_text, outro_text,
                  estimated_minutes, session_validity_hours, language, status,
                  intro_audio_url, outro_audio_url, cover_image_url)
VALUES ('00000000-0000-0002-0000-000000000003', '00000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000003',
        'demo-katova-povinnost-de',
        'Die Pflicht des Henkers',
        'Demo-Spiel: die wahre Geschichte des letzten Scharfrichters von Eger',
        'Schloss Königswart, im Jahr 1835. Der alte Karl Huss, Kustos des Schlossmuseums, hält einen amtlichen Brief in der Hand. Die Zentralbuchhaltung schreibt ihm, man habe ihm einst den Lohn für eine Hinrichtung auf der Burg Elbogen falsch berechnet. Nun soll er die Überzahlung zurückgeben.

„Einst war ich der letzte Scharfrichter der Stadt Eger. Alles habe ich in meiner Chronik festgehalten, jeden Tag, jeden Kreuzer. Komm, Schreiber. Gehen wir noch einmal gemeinsam den Weg aus dem Sommer 1822. An jedem Ort erzähle ich dir, was dort geschah, und du hilfst mir zu prüfen, dass ich nichts vergessen habe.“

An jedem Ort bekommst du einen Buchstaben. Zusammen ergeben sie das Wort, nach dem Meister Huss lebte.',
        '„Pflichten. Die Leute erzählen sich Schauergeschichten über Henker, aber ich habe nie mehr getan, als mir das Gericht auferlegt hat. Ich schrieb meine Chronik, damit auch andere lesen können, wie es war. Die Überzahlung gebe ich also zurück. Ordnung muss sein. Und dir, Schreiber, danke ich. Du bist den ganzen Weg mit mir gegangen, hast nichts ausgelassen und jedes Rätsel gelöst. Hätte ich dich damals in Eger gehabt, wäre meine Chronik ein Jahr früher fertig gewesen.“

Karl Huss war der letzte Scharfrichter von Eger. Seine Chronik gelangte in die Sammlungen von Schloss Königswart, wo er ab 1827 als Kustos des Museums wirkte. Dank ihr kennen wir heute auch das Gespräch, das er im Elbogener Kerker mit Karl Hains führte. Und der Brief aus der Buchhaltung? Den gab es wirklich, dreizehn Jahre nach der Hinrichtung.

Glückwunsch, du hast „Die Pflicht des Henkers“ geschafft! Jetzt weißt du über den letzten Scharfrichter von Eger mehr als die meisten Besucher der Burg. Wenn du am Elbogener Turm vorbeigehst, denk an Meister Huss.',
        30, 24, 'de', 'PUBLISHED',
        '/audio/demo-de/intro.mp3', '/audio/demo-de/zaver.mp3', '/images/demo/cover.webp');

INSERT INTO station (id, game_id, order_no, title, story_text, task_text, task_type, reward_text, next_text,
                     story_audio_url, reward_audio_url, image_url, image_alt, listen_only, puzzle_letter) VALUES
('00000000-0000-0002-0001-000000000001', '00000000-0000-0002-0000-000000000003', 1,
 'Aussicht über die Eger',
 'Als ich im August 1822 von Eger her kam, sah ich die Burg auf dem Felsen und den Fluss Eger, der sie fast von allen Seiten umschlingt. Als hielte er sie in einem angewinkelten Arm.',
 'Woher haben Burg und Stadt den Namen Elbogen (tschechisch Loket)?',
 'CHOICE',
 'Richtig. Die Eger beschreibt hier eine Biegung wie ein angewinkelter Arm, wie ein Ellbogen. Daher der Name der Burg und der Stadt, auch das tschechische Loket bedeutet Ellbogen. Für das Rätselwort bekommst du den Buchstaben P.',
 'Steig hinauf zum Burgtor. Hier fuhr Huss vor zweihundert Jahren in die Burg ein.',
 '/audio/demo-de/st01-pribeh.mp3', '/audio/demo-de/st01-odmena.mp3',
 '/images/demo/st01.webp', 'Burg Elbogen auf dem Felsen in der Biegung der Eger', FALSE, 'P'),
('00000000-0000-0002-0001-000000000002', '00000000-0000-0002-0000-000000000003', 2,
 'Das Burgtor',
 'In meiner Chronik nannte ich Elbogen die Schlicksche Burg, denn einst herrschten hier die mächtigen Grafen Schlick. Doch in dem Jahr, als ich kam, wohnten hier keine Herren mehr. Hinter dem Tor wartete der Kerkermeister mit einem Schlüsselbund auf mich.',
 'Wozu diente die Burg Elbogen ab 1822?',
 'CHOICE',
 'Ja. Ab 1822 diente die Burg als Gefängnis, und zwar bis Mitte des 20. Jahrhunderts. Genau hier wartete Karl Hains auf sein Urteil. Buchstabe F.',
 'Geh weiter in den Burghof. Dort erfährst du, warum Huss überhaupt nach Elbogen kam.',
 '/audio/demo-de/st02-pribeh.mp3', '/audio/demo-de/st02-odmena.mp3',
 '/images/demo/st02.webp', 'Kerkermeister mit einem Schlüsselbund im Burgtor', FALSE, 'F'),
('00000000-0000-0002-0001-000000000003', '00000000-0000-0002-0000-000000000003', 3,
 'Die verstreute Chronik',
 'Im Frühjahr 1822 ermordete Karl Hains seinen Nachbarn, den Gerichtsbeamten Matthias Gerstner, dem er Geld schuldete. Meister Huss hielt alles in seiner Chronik fest, aber dir sind die Blätter durcheinandergefallen:

A: Das Gericht fällte das Todesurteil (24. Mai)

B: Hains ermordete Matthias Gerstner (16. April)

C: Das Urteil wurde vollstreckt (19. August)

D: Der Henker besuchte den Verurteilten in der Zelle (18. August)',
 'Ordne die Blätter nach dem Datum und schreib ihre Buchstaben hintereinander (z. B. ABCD).',
 'TEXT',
 'Genau. Vom Mord bis zum Urteil vergingen nur 38 Tage, bis zur Hinrichtung 125 Tage. Die Gerichte waren damals schnell. Buchstabe L.',
 'Steig hinab in den Kerker. Dort stand Huss dem Verurteilten zum ersten Mal gegenüber.',
 '/audio/demo-de/st03-pribeh.mp3', '/audio/demo-de/st03-odmena.mp3',
 '/images/demo/st03.webp', 'Vier verstreute Blätter einer alten Chronik', FALSE, 'L'),
('00000000-0000-0002-0001-000000000004', '00000000-0000-0002-0000-000000000003', 4,
 'Gespräch in der Zelle',
 'Huss: „Gelobt sei Jesus Christus.“

Hains: „In Ewigkeit. Amen.“

Huss: „Lieber Karl, ich komme, um Ihn zu besuchen und mich Ihm zu zeigen. Ich bin derjenige, der an Ihm das Todesurteil vollstrecken muss. Hat Er sich denn auch auf sein Ende gehörig vorbereitet?“

Hains: „Ja. Dies ist das Bild meines Erlösers, der mir, wie ich hoffe, barmherzig sein wird. Ich erkenne meine großen Sünden an und bereue besonders mein Verbrechen, das ich an dem unschuldigen Gerstner begangen habe.“',
 'Hör dir das Gespräch des Henkers mit dem Verurteilten an. Was zeigte Hains dem Henker, als dieser fragte, ob er bereit sei?',
 'TEXT',
 'Ja, das Bild des Erlösers. Dieses Gespräch ist nicht erfunden: Huss schrieb es in den vierten Band seiner Chronik der Stadt Eger, und wir können es bis heute lesen. Buchstabe I.',
 'Geh weiter in die Folterkammer. Huss will dir dort etwas über Henker erklären.',
 '/audio/demo-de/st04-pribeh.mp3', '/audio/demo-de/st04-odmena.mp3',
 '/images/demo/st04.webp', 'Henker und Verurteilter in einer gewölbten Zelle bei Kerzenlicht', TRUE, 'I'),
('00000000-0000-0002-0001-000000000005', '00000000-0000-0002-0000-000000000003', 5,
 'Die Folterkammer',
 'Die Leute erzählen sich über Henker Schauergeschichten. Auch Hains fürchtete sich in der Zelle, dass ich ihn quälen würde. Ich antwortete ihm so, wie ich es meinte.',
 'Was versprach Huss dem Verurteilten?',
 'CHOICE',
 '„Grausamkeit ist mir fremd. Ich vollstrecke nur, was mir das Gericht aufträgt. Nicht mehr und nicht weniger.“ Die Folter bei Verhören hatte Maria Theresia schon 1776 abgeschafft, Huss kannte die Folterkammer also nur aus Erzählungen. Buchstabe C.',
 'Kehr in den Burgpalast zurück. Dort verbrachte Huss die letzten Tage mit dem Verurteilten.',
 '/audio/demo-de/st05-pribeh.mp3', '/audio/demo-de/st05-odmena.mp3',
 '/images/demo/st05.webp', 'Verlassene Folterkammer mit abgedeckten Werkzeugen', FALSE, 'C'),
('00000000-0000-0002-0001-000000000006', '00000000-0000-0002-0000-000000000003', 6,
 'Die letzte Rolle',
 'In den letzten Tagen vor der Hinrichtung hatte Hains nicht viel Zeit für finstere Gedanken. Huss gab ihm ein Blatt Papier mit den Worten, die er auf seinem letzten Weg sprechen sollte. Mehrmals übte er den Text mit ihm, verbesserte ihn und erklärte ihn, wie ein Regisseur mit einem Schauspieler.',
 'Warum tat Huss das?',
 'CHOICE',
 'Ja. Der Historiker Miloš Říha nennt das eine psychologisch sehr kluge Art, den Verurteilten zu beschäftigen. Huss war ein tief gläubiger Mensch. Er tötete den Verurteilten nicht einfach, er bereitete ihn vielmehr auf den letzten Weg vor. Buchstabe H.',
 'Geh zur romanischen Rotunde, dem ältesten Bauwerk der Burg. Dort wartete ein Priester auf Hains.',
 '/audio/demo-de/st06-pribeh.mp3', '/audio/demo-de/st06-odmena.mp3',
 '/images/demo/st06.webp', 'Henker bringt dem Verurteilten einen Text von einem Blatt Papier bei', FALSE, 'H'),
('00000000-0000-0002-0001-000000000007', '00000000-0000-0002-0000-000000000003', 7,
 'Der letzte Weg',
 'Am Morgen des 19. August 1822 bereitete Hochwürden Seifert Hains auf den letzten Weg vor. Der Justizrat übergab den Verurteilten dann dem Henker. Und Hains sprach:

„Ich bitte, meinen letzten Weg zu Fuß gehen zu dürfen, da ich mich stark genug fühle. Auch der Erlöser Jesus Christus musste zu Fuß zur Richtstätte gehen und trug dabei das schwere Kreuz. Und ich, ein großer Verbrecher, sollte fahren?“',
 'Hör dir die Bitte von Hains an. Warum wollte er zu Fuß gehen?',
 'CHOICE',
 'Ja. Begleitet von Soldaten und vielen Leuten aus der Umgebung zog der Zug zu Fuß auf einen nahen Hügel, der als Richtstätte bestimmt war. Buchstabe T.',
 'Steig auf den Burgturm. Von dort siehst du die Landschaft, durch die der Zug zog.',
 '/audio/demo-de/st07-pribeh.mp3', '/audio/demo-de/st07-odmena.mp3',
 '/images/demo/st07.webp', 'Priester am Eingang der romanischen Rotunde im Morgenlicht', TRUE, 'T'),
('00000000-0000-0002-0001-000000000008', '00000000-0000-0002-0000-000000000003', 8,
 'Der Burgturm',
 'Vom Turm siehst du die Hügel um Elbogen. An jenem Tag war der ganze Fall für mich erledigt. Doch das Amt schloss ihn viel später ab: Der Brief aus der Buchhaltung, den ich jetzt in der Hand halte, kam im Jahr 1835.',
 'Wie viele Jahre vergingen von der Hinrichtung in Elbogen bis zum Brief aus der Buchhaltung?',
 'NUMBER',
 'Dreizehn Jahre! Huss hatte inzwischen das Richtschwert abgelegt und kümmerte sich ab 1827 um die Sammlungen des Schlossmuseums in Königswart. Dorthin gelangte auch seine Chronik. Buchstabe E.',
 'Geh hinunter zur Burgkasse, dorthin, wo du heute die Eintrittskarten kaufst. Es ist Zeit, Geld zu zählen.',
 '/audio/demo-de/st08-pribeh.mp3', '/audio/demo-de/st08-odmena.mp3',
 '/images/demo/st08.webp', 'Aussicht vom Burgturm auf die Hügel um Elbogen', FALSE, 'E'),
('00000000-0000-0002-0001-000000000009', '00000000-0000-0002-0000-000000000003', 9,
 'Die Rechnung von 1835',
 'Für meine Mühe bekam ich damals 47 Gulden Conventionsmünze. Und jetzt schreiben sie mir, ich soll sechs Gulden und sechsundzwanzig Kreuzer zurückgeben! Ein Gulden hat sechzig Kreuzer. Rechne es mir aus, Schreiber, damit ich weiß, wie viel Kleingeld ich abzählen muss.',
 'Wie viele Kreuzer muss Huss zurückgeben?',
 'NUMBER',
 '386 Kreuzer, richtig. Das Amt hatte Huss nach dreizehn Jahren den Lohn, das Reisegeld und die Tagegelder neu berechnet. Der Historiker Říha merkt dazu an: „Beneidenswerte Ordnung herrschte im alten Österreich!“ Buchstabe N.',
 'Kehr in den Burghof zurück und löse das Rätselwort.',
 '/audio/demo-de/st09-pribeh.mp3', '/audio/demo-de/st09-odmena.mp3',
 '/images/demo/st09.webp', 'Amtlicher Brief aus dem Jahr 1835 mit Siegel und ein Häufchen Münzen', FALSE, 'N'),
('00000000-0000-0002-0001-000000000010', '00000000-0000-0002-0000-000000000003', 10,
 'Das Rätselwort',
 'Du bist den ganzen Weg von Meister Huss gegangen. Im Rätselwort hast du neun Buchstaben, aber sie sind durcheinander. Ordne sie und du findest das Wort, nach dem Huss lebte.',
 'Welches Wort sagte Huss zum Justizrat, als er den Verurteilten übernahm?',
 'TEXT',
 'Pflichten. „Ich werde meine Pflichten auf das Gewissenhafteste erfüllen, nach der mir verliehenen Macht und dem Recht“, sagte Huss damals.',
 NULL,
 '/audio/demo-de/st10-pribeh.mp3', '/audio/demo-de/st10-odmena.mp3',
 '/images/demo/st10.webp', 'Aufgeschlagene Chronik mit Federkiel und versiegeltem Brief', FALSE, NULL);

INSERT INTO hint (id, station_id, order_no, text, penalty_seconds) VALUES
('00000000-0000-0002-0002-000000010001', '00000000-0000-0002-0001-000000000001', 1, 'Schau, was der Fluss um den Felsen macht.', 60),
('00000000-0000-0002-0002-000000020001', '00000000-0000-0002-0001-000000000002', 1, 'Wer wartete hinter dem Tor und was hielt er in der Hand?', 60),
('00000000-0000-0002-0002-000000030001', '00000000-0000-0002-0001-000000000003', 1, 'Fang mit April an, hör mit August auf.', 60),
('00000000-0000-0002-0002-000000030002', '00000000-0000-0002-0001-000000000003', 2, 'Die Reihenfolge beginnt mit B, A …', 120),
('00000000-0000-0002-0002-000000040001', '00000000-0000-0002-0001-000000000004', 1, 'Es war ein Bild. Wen zeigte es?', 60),
('00000000-0000-0002-0002-000000050001', '00000000-0000-0002-0001-000000000005', 1, 'Der Henker war nur Vollstrecker. Wer entschied über die Strafe?', 60),
('00000000-0000-0002-0002-000000060001', '00000000-0000-0002-0001-000000000006', 1, 'Woran hätte Hains gedacht, wenn er nichts zu tun gehabt hätte?', 60),
('00000000-0000-0002-0002-000000070001', '00000000-0000-0002-0001-000000000007', 1, 'Wen erwähnte Hains in seiner Bitte?', 60),
('00000000-0000-0002-0002-000000080001', '00000000-0000-0002-0001-000000000008', 1, 'Die Hinrichtung war im Jahr 1822.', 60),
('00000000-0000-0002-0002-000000090001', '00000000-0000-0002-0001-000000000009', 1, 'Rechne zuerst die Gulden um: 6 × 60.', 60),
('00000000-0000-0002-0002-000000090002', '00000000-0000-0002-0001-000000000009', 2, '360 Kreuzer und noch 26.', 120),
('00000000-0000-0002-0002-000000100001', '00000000-0000-0002-0001-000000000010', 1, 'Die Buchstaben waren P, F, L, I, C, H, T, E, N.', 60);

INSERT INTO accepted_answer (id, station_id, value, normalized_value) VALUES
('00000000-0000-0002-0003-000000010001', '00000000-0000-0002-0001-000000000001', 'Die Flussbiegung wie ein angewinkelter Arm', 'die flussbiegung wie ein angewinkelter arm'),
('00000000-0000-0002-0003-000000020001', '00000000-0000-0002-0001-000000000002', 'Gefängnis', 'gefangnis'),
('00000000-0000-0002-0003-000000030001', '00000000-0000-0002-0001-000000000003', 'BADC', 'badc'),
('00000000-0000-0002-0003-000000030002', '00000000-0000-0002-0001-000000000003', 'B A D C', 'b a d c'),
('00000000-0000-0002-0003-000000040001', '00000000-0000-0002-0001-000000000004', 'Bild des Erlösers', 'bild des erlosers'),
('00000000-0000-0002-0003-000000040002', '00000000-0000-0002-0001-000000000004', 'Bild des Erloesers', 'bild des erloesers'),
('00000000-0000-0002-0003-000000040003', '00000000-0000-0002-0001-000000000004', 'Bild', 'bild'),
('00000000-0000-0002-0003-000000040004', '00000000-0000-0002-0001-000000000004', 'Bildchen', 'bildchen'),
('00000000-0000-0002-0003-000000040005', '00000000-0000-0002-0001-000000000004', 'Heiligenbild', 'heiligenbild'),
('00000000-0000-0002-0003-000000040006', '00000000-0000-0002-0001-000000000004', 'Erlöser', 'erloser'),
('00000000-0000-0002-0003-000000040007', '00000000-0000-0002-0001-000000000004', 'Erlösers', 'erlosers'),
('00000000-0000-0002-0003-000000040008', '00000000-0000-0002-0001-000000000004', 'Erloeser', 'erloeser'),
('00000000-0000-0002-0003-000000040009', '00000000-0000-0002-0001-000000000004', 'Erloesers', 'erloesers'),
('00000000-0000-0002-0003-000000040010', '00000000-0000-0002-0001-000000000004', 'Heiland', 'heiland'),
('00000000-0000-0002-0003-000000040011', '00000000-0000-0002-0001-000000000004', 'Bild des Heilands', 'bild des heilands'),
('00000000-0000-0002-0003-000000040012', '00000000-0000-0002-0001-000000000004', 'Christus', 'christus'),
('00000000-0000-0002-0003-000000040013', '00000000-0000-0002-0001-000000000004', 'Bild Christi', 'bild christi'),
('00000000-0000-0002-0003-000000040014', '00000000-0000-0002-0001-000000000004', 'Jesus', 'jesus'),
('00000000-0000-0002-0003-000000040015', '00000000-0000-0002-0001-000000000004', 'Bild von Jesus', 'bild von jesus'),
('00000000-0000-0002-0003-000000050001', '00000000-0000-0002-0001-000000000005', 'Keine Folter, nur was das Gericht befahl', 'keine folter nur was das gericht befahl'),
('00000000-0000-0002-0003-000000060001', '00000000-0000-0002-0001-000000000006', 'Um den Verurteilten zu beschäftigen und zu beruhigen', 'um den verurteilten zu beschaftigen und zu beruhigen'),
('00000000-0000-0002-0003-000000070001', '00000000-0000-0002-0001-000000000007', 'Weil auch Christus zu Fuß ging', 'weil auch christus zu fuß ging'),
('00000000-0000-0002-0003-000000080001', '00000000-0000-0002-0001-000000000008', '13', '13'),
('00000000-0000-0002-0003-000000090001', '00000000-0000-0002-0001-000000000009', '386', '386'),
('00000000-0000-0002-0003-000000100001', '00000000-0000-0002-0001-000000000010', 'Pflichten', 'pflichten'),
('00000000-0000-0002-0003-000000100002', '00000000-0000-0002-0001-000000000010', 'Pflicht', 'pflicht');

INSERT INTO station_choice (id, station_id, order_no, text) VALUES
('00000000-0000-0002-0004-000000010001', '00000000-0000-0002-0001-000000000001', 1, 'Die Flussbiegung wie ein angewinkelter Arm'),
('00000000-0000-0002-0004-000000010002', '00000000-0000-0002-0001-000000000001', 2, 'Der Name des ersten Besitzers'),
('00000000-0000-0002-0004-000000010003', '00000000-0000-0002-0001-000000000001', 3, 'Ein altes Längenmaß'),
('00000000-0000-0002-0004-000000010004', '00000000-0000-0002-0001-000000000001', 4, 'Die Form des Burgturms'),
('00000000-0000-0002-0004-000000020001', '00000000-0000-0002-0001-000000000002', 1, 'Gefängnis'),
('00000000-0000-0002-0004-000000020002', '00000000-0000-0002-0001-000000000002', 2, 'Brauerei'),
('00000000-0000-0002-0004-000000020003', '00000000-0000-0002-0001-000000000002', 3, 'Kaserne'),
('00000000-0000-0002-0004-000000020004', '00000000-0000-0002-0001-000000000002', 4, 'Schule'),
('00000000-0000-0002-0004-000000050001', '00000000-0000-0002-0001-000000000005', 1, 'Keine Folter, nur was das Gericht befahl'),
('00000000-0000-0002-0004-000000050002', '00000000-0000-0002-0001-000000000005', 2, 'Dass er ihm zur Flucht verhilft'),
('00000000-0000-0002-0004-000000050003', '00000000-0000-0002-0001-000000000005', 3, 'Dass er um Gnade bittet'),
('00000000-0000-0002-0004-000000050004', '00000000-0000-0002-0001-000000000005', 4, 'Dass er ihn heimlich hinrichtet'),
('00000000-0000-0002-0004-000000060001', '00000000-0000-0002-0001-000000000006', 1, 'Um den Verurteilten zu beschäftigen und zu beruhigen'),
('00000000-0000-0002-0004-000000060002', '00000000-0000-0002-0001-000000000006', 2, 'Damit Hains lesen lernt'),
('00000000-0000-0002-0004-000000060003', '00000000-0000-0002-0001-000000000006', 3, 'Um Theater für Gäste zu spielen'),
('00000000-0000-0002-0004-000000060004', '00000000-0000-0002-0001-000000000006', 4, 'Um Hains zu verhören'),
('00000000-0000-0002-0004-000000070001', '00000000-0000-0002-0001-000000000007', 1, 'Weil auch Christus zu Fuß ging'),
('00000000-0000-0002-0004-000000070002', '00000000-0000-0002-0001-000000000007', 2, 'Weil er Angst vor Pferden hatte'),
('00000000-0000-0002-0004-000000070003', '00000000-0000-0002-0001-000000000007', 3, 'Um fliehen zu können'),
('00000000-0000-0002-0004-000000070004', '00000000-0000-0002-0001-000000000007', 4, 'Weil der Wagen kaputt war');

-- ---------------------------------------------------------
-- Vývojový přístupový kód DEMO-KATA: nevyprší, každé zadání založí novou hru, dávka je rovnou „předaná“
-- (kód se ukládá bez pomlčky, viz AccessCodeFormat)
-- ---------------------------------------------------------
INSERT INTO access_code_batch (id, partner_id, game_id, quantity, validity_hours, multi_session, note, handed_over_at)
VALUES ('00000000-0000-0000-0005-000000000001', '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000003', 1, NULL, TRUE, 'Vývojový kód (bez expirace, každé zadání = nová hra)',
        now());

INSERT INTO access_code (id, batch_id, code)
VALUES ('00000000-0000-0000-0006-000000000001', '00000000-0000-0000-0005-000000000001', 'DEMOKATA');

-- ---------------------------------------------------------
-- Vývojový účet partnera pro administraci hradu: hrad / hrad-loket (jen lokální vývoj)
-- ---------------------------------------------------------
INSERT INTO partner_user (id, partner_id, username, password_hash, display_name)
VALUES ('00000000-0000-0000-0007-000000000001', '00000000-0000-0000-0000-000000000001', 'hrad',
        '{bcrypt}$2a$10$xgzBVRPfBX5BRJLbpvvpbO77bHzUG9KWgs.O/bI7GcStYQPUUaT/m', 'Pokladna Demo hradu')
ON CONFLICT (id) DO UPDATE SET username = EXCLUDED.username, password_hash = EXCLUDED.password_hash,
                               display_name = EXCLUDED.display_name;
