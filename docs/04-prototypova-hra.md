# 04 — Prototypová hra „Katova povinnost“ (demo, verze 2)

> **Stav: schváleno a nahráno v seedu `R__demo_game.sql`** (nahradilo původní demo „Tajemství katova klíče“). Nahrávky (`docs/nahravky.md`) a obrázky (`docs/grafika.md`) se dodávají postupně; dokud soubor chybí, hra ukáže jen text. Fakta mimo článek jsou pro demo ponechána tak, jak jsou; ostrá hra se opře o podklady od hradu.

> **Historický základ.** Příběh stojí na skutečné události: 19. srpna 1822 vykonal poslední chebský kat Karl Huss na hradě Loket rozsudek nad Karlem Hainsem. Zdroj: PhDr. Miloš Říha, *Poprava na hradu Loket* (2004), viz `docs/Kat_dejiny.txt`, a Hussova rukopisná kronika města Chebu, ze které článek cituje. Co je doložené a co je herní fikce, shrnuje tabulka na konci.

## Koncept

- **Rámec:** Rok 1835, zámek Kynžvart. Starý Karl Huss, dnes kustod zámeckého muzea, dostal od centrální účetní kanceláře dopis: před lety mu prý špatně spočítali odměnu za popravu na Lokti a má vrátit přeplatek. Huss si vše zapisoval do kroniky a chce si to ověřit. Hráč je jeho **písař**: projde s ním na Lokti cestu z léta 1822 místo po místě.
- **Hlasy:** **vypravěč** vede hráče a po každém splněném úkolu řekne, kam má jít dál. **Karl Huss** vzpomíná a vypráví. V jednom kroku zazní i **odsouzený Karl Hains** a **žalářník**.
- **Smyčka kroku:** příběh na místě (hlas) → úkol → odměna: historická zajímavost + písmeno do tajenky + **kam dál** (vypravěč).
- **Tajenka:** písmena z kroků 1–9 (`station.puzzle_letter`) tvoří slovo **POVINNOST**. Hra je sbírá za hráče a ukazuje je v řádku „Tajenka“ zpřeházená, v posledním kroku je hráč přeskládá. Slovo POVINNOST použil Huss, když si odsouzeného přebíral: „Vykonám svoji povinnost co nejbedlivěji.“
- **Demo jde hrát kdekoli.** Odpověď je vždy v textu, poslechu nebo obrázku na obrazovce. Ve skutečné hře na hradě se část úkolů přesune na místo (spočítej, najdi, přečti nápis).
- **Citlivé téma:** poprava se nepopisuje, hra mluví o lidech, právu a době. Vhodné od cca 10 let.
- **Místa** odpovídají skutečným částem hradu Loket, přesné umístění kroků je potřeba doladit s hradem.

## Metadata hry

| Pole | Hodnota |
|---|---|
| `slug` | `demo-katova-povinnost` |
| `title` | Katova povinnost |
| `subtitle` | Demo hra — skutečný příběh posledního chebského kata |
| `estimated_minutes` | 30 |
| `session_validity_hours` | 24 |
| `status` | `PUBLISHED` |
| Partner | Demo partner |
| Lokalita | Hrad Loket |

### Úvodní příběh (`intro_text`)

**Vypravěč:** Zámek Kynžvart, roku 1835. Starý pán Karl Huss, kustod zámeckého muzea, drží v ruce úřední dopis. Centrální účetní kancelář mu píše, že mu kdysi špatně spočítali odměnu za popravu na hradě Loket — a že má vrátit přeplatek.

**Huss:** Kdysi jsem byl posledním katem města Chebu. Všechno jsem si zapisoval do kroniky — každý den, každý krejcar. Pojď, písaři. Projdeme spolu ještě jednou cestu z léta 1822. Na každém místě ti povím, co se tam stalo, a ty mi pomůžeš ověřit, že jsem nic nezapomněl.

**Vypravěč:** Na každém místě získáš jedno písmeno. Dohromady dají slovo, podle kterého mistr Huss žil.

### Závěrečný příběh (`outro_text`)

**Huss:** Povinnost. Lidé si o katech vyprávějí hrůzy, ale já jsem nikdy nedělal víc, než mi soud uložil. Psal jsem kroniku, aby si i jiní mohli přečíst, jak to bylo. Přeplatek tedy vrátím. Pořádek musí být. A tobě, písaři, děkuji. Prošel jsi se mnou celou cestu, nic jsi nevynechal a na každou hádanku jsi přišel. Kdybych tě měl tenkrát v Chebu, byla by moje kronika hotová o rok dřív.

**Vypravěč:** Karl Huss byl posledním chebským katem. Jeho kronika se dostala do sbírek zámku Kynžvart, kde od roku 1827 působil jako kustod muzea. Díky ní dnes známe i rozhovor, který vedl v loketském žaláři s Karlem Hainsem. A dopis z účtárny? Ten přišel opravdu — třináct let po popravě.

Gratulujeme, dohrál jsi Katovu povinnost! Teď víš o posledním chebském katovi víc než většina návštěvníků hradu. Až půjdeš kolem loketské věže, vzpomeň si na mistra Husse.

---

## Kroky

Nápovědy mají penalizaci 60 s (první) a 120 s (druhá), pokud není uvedeno jinak.

### 1. Vyhlídka nad Ohří — `CHOICE`

**Příběh (Huss):** Když jsem v srpnu 1822 přijížděl od Chebu, uviděl jsem hrad na skále a řeku Ohři, jak ho obtáčí skoro ze všech stran. Jako by ho držela v ohnuté paži.

**Úkol:** Podle čeho dostal hrad i město jméno Loket?

**Možnosti:** Ohyb řeky jako ohnutá paže · Jméno prvního majitele · Stará délková míra · Tvar hradní věže

**Přijímané odpovědi:** `Ohyb řeky jako ohnutá paže`

**Nápovědy:**
1. Podívej se, co dělá řeka kolem skály.

**Odměna:** Správně. Řeka Ohře tu opisuje ohyb jako loket ohnuté paže — odtud jméno hradu i města. Do tajenky získáváš písmeno **P**.
*Kam dál (vypravěč):* Vystoupej k hradní bráně. Tudy Huss před dvěma sty lety vjížděl na hrad.

### 2. Hradní brána — `CHOICE`

**Příběh (Huss):** Ve své kronice jsem Loket nazval šlikovským hradem — kdysi tu vládli mocní Šlikové. Ale v roce, kdy jsem přijel, tu už nesídlili páni. Za branou na mě čekal žalářník se svazkem klíčů.

**Úkol:** K čemu sloužil hrad Loket od roku 1822?

**Možnosti:** Věznice · Pivovar · Kasárna · Škola

**Přijímané odpovědi:** `Věznice`

**Nápovědy:**
1. Kdo čekal za branou a co měl v ruce?

**Odměna:** Ano. Od roku 1822 sloužil hrad jako věznice, a to až do poloviny 20. století. Právě tady čekal Karl Hains na svůj rozsudek. Písmeno **O**.
*Kam dál:* Projdi na nádvoří. Tam se dozvíš, proč Huss na Loket vůbec přijel.

### 3. Nádvoří — rozsypaná kronika — `TEXT`

**Příběh (vypravěč):** Na jaře 1822 zavraždil Karl Hains svého souseda, právního úředníka Matyáše Gerstnera, kterému dlužil peníze. Huss všechno zapsal do kroniky, ale listy se ti rozsypaly:

- **A** — Soud vynesl rozsudek smrti (24. května)
- **B** — Hains zavraždil Matyáše Gerstnera (16. dubna)
- **C** — Rozsudek byl vykonán (19. srpna)
- **D** — Kat navštívil odsouzeného v cele (18. srpna)

**Úkol:** Seřaď listy podle data a napiš jejich písmena za sebou (např. ABCD).

**Přijímané odpovědi:** `BADC`, `B A D C` (normalizace sjednotí čárky a mezery, ale mezery mezi písmeny neodstraní)

**Nápovědy:**
1. Začni dubnem, skonči srpnem.
2. Pořadí začíná B, A… — 120 s

**Odměna:** Přesně tak. Od vraždy k rozsudku uběhlo jen 38 dní, k popravě 125 dní. Tehdejší soudy byly rychlé. Písmeno **V**.
*Kam dál:* Sestup do žaláře. Tam stanul Huss poprvé tváří v tvář odsouzenému.

> Návrh na grafiku: čtyři „listy kroniky“ jako obrázek.

### 4. Žalář — rozhovor v cele — `TEXT` (poslechová úloha)

**Příběh (Huss a Hains, zkráceně podle kroniky):**
— Buď pochválen Pán Ježíš Kristus.
— Na věky věkův amen.
— Milý Karle, přicházím, abych ho navštívil a se mu ukázal. Jsem ten, který musí na něm rozsudek smrti vykonati. Jestlipak se také on k svému konci náležitě připravil?
— Ano. Toto je obraz mého Spasitele, který mi, jak doufám, bude milosrdný. Uznávám své velké hříchy a zvláště lituji svého zločinu, který jsem spáchal na nevinném Gerstnerovi.

**Úkol:** Co ukázal Hains katovi, když se ho ptal, jestli je připravený?

**Přijímané odpovědi:** `obraz Spasitele`, `obraz`, `Spasitele`, `Spasitel`, `Krista`, `Ježíše`, `obraz Krista`

**Nápovědy:**
1. Byl to obrázek. Koho na něm měl?

**Odměna:** Ano, obraz Spasitele. Tenhle rozhovor není vymyšlený — Huss ho zapsal do čtvrtého dílu své kroniky města Chebu a dodnes si ho můžeme přečíst. Písmeno **I**.
*Kam dál:* Projdi do mučírny. Huss ti tam chce něco vysvětlit o katech.

> Návrh: úloha jen na poslech — text rozhovoru se zobrazí až po stisknutí „Zobrazit přepis“ (vyžaduje úpravu aplikace).

### 5. Mučírna — `CHOICE`

**Příběh (Huss):** Lidé si o katech vyprávějí hrůzy. I Hains se v cele bál, že ho budu trýznit. Odpověděl jsem mu tak, jak jsem to myslel.

**Úkol:** Co Huss odsouzenému slíbil?

**Možnosti:** Žádné mučení, jen co nařídil soud · Že mu pomůže utéct · Že požádá o milost · Že ho popraví tajně

**Přijímané odpovědi:** `Žádné mučení, jen co nařídil soud`

**Nápovědy:**
1. Kat byl jen vykonavatel. Kdo rozhodoval o trestu?

**Odměna:** „Krutost je mi cizí. Vykonám jen to, co mi soudní úřad ukládá. Nic víc a nic méně.“ Mučení při vyšetřování zrušila Marie Terezie už v roce 1776, Huss tedy mučírnu znal jen z vyprávění. Písmeno **N**.
*Kam dál:* Vrať se do hradního paláce. Tam Huss trávil s odsouzeným poslední dny.

### 6. Palác — poslední role — `CHOICE`

**Příběh (vypravěč):** Posledních dní před popravou neměl Hains mnoho času na černé myšlenky. Huss mu dal do ruky papír se slovy, která měl na své poslední cestě pronést. Několikrát s ním text zkoušel, opravoval ho a vysvětloval — jako režisér s hercem.

**Úkol:** Proč to Huss dělal?

**Možnosti:** Aby odsouzeného zaměstnal a uklidnil · Aby se Hains naučil číst · Aby mohl hrát divadlo pro hosty · Aby Hainse vyslechl

**Přijímané odpovědi:** `Aby odsouzeného zaměstnal a uklidnil`

**Nápovědy:**
1. Na co by Hains myslel, kdyby neměl co dělat?

**Odměna:** Ano. Historik Miloš Říha to nazývá psychologicky velmi chytrým způsobem, jak odsouzeného zaměstnat. Huss byl hluboce věřící člověk a na poslední cestu odsouzeného spíš „vypravoval“. Písmeno **N**.
*Kam dál:* Zamiř k románské rotundě, nejstarší stavbě hradu. Tam čekal na Hainse kněz.

### 7. Rotunda — poslední cesta — `CHOICE`

**Příběh (vypravěč a Hains):** Ráno 19. srpna 1822 připravil Hainse k poslední cestě velebný pán Seifert. Justiční rada pak odsouzeného předal katovi. A Hains promluvil:
— Prosím, abych směl svoji poslední cestu vykonati pěšky, jelikož se cítím dostatečně silným. Vykupitel Ježíš Kristus musel také až na popraviště, nesa těžký kříž, pěšky. A já, velký zločinec, bych měl jeti?

**Úkol:** Proč chtěl Hains jít pěšky?

**Možnosti:** Protože i Kristus šel pěšky · Protože se bál koní · Aby mohl utéct · Protože byl vůz rozbitý

**Přijímané odpovědi:** `Protože i Kristus šel pěšky`

**Nápovědy:**
1. Poslechni si znovu, koho Hains zmínil.

**Odměna:** Ano. Průvod se v doprovodu vojska a mnoha lidí z okolí vydal pěšky na nedaleký vrch, vybraný jako popravní místo. Písmeno **O**.
*Kam dál:* Vystoupej na hradní věž. Odtud je vidět krajina, kudy průvod šel.

### 8. Hradní věž — `NUMBER`

**Příběh (Huss):** Z věže vidíš kopce kolem Lokte. Tím dnem pro mě případ skončil. Jenže úřad ho uzavřel mnohem později: dopis z účtárny, který teď držím v ruce, přišel roku 1835.

**Úkol:** Kolik let uplynulo od popravy na Lokti do dopisu z účtárny?

**Přijímané odpovědi:** `13`

**Nápovědy:**
1. Poprava byla v roce 1822.

**Odměna:** Třináct let! Huss mezitím odložil katovský meč a od roku 1827 se staral o sbírky zámeckého muzea v Kynžvartu. Tam se dostala i jeho kronika. Písmeno **S**.
*Kam dál:* Sestup k hradní pokladně — tam, kde dnes kupuješ vstupenky. Je čas spočítat peníze.

### 9. Pokladna — účet z roku 1835 — `NUMBER`

**Příběh (Huss):** Za svou námahu jsem tehdy dostal 47 zlatých konvenční měny. A teď mi píšou, že mám vrátit šest zlatých a dvacet šest krejcarů! Jeden zlatý má šedesát krejcarů. Spočítej mi to, písaři, ať vím, kolik drobných mám odpočítat.

**Úkol:** Kolik krejcarů musí Huss vrátit?

**Přijímané odpovědi:** `386`

**Nápovědy:**
1. Nejdřív převeď zlaté: 6 × 60.
2. 360 krejcarů a ještě 26. — 120 s

**Odměna:** 386 krejcarů, správně. Úřad Hussovi po třinácti letech přepočítal odměnu, cestovné i diety. Historik Říha to glosuje: „Záviděníhodný pořádek vládl ve starém Rakousku!“ Písmeno **T**.
*Kam dál:* Vrať se na nádvoří a slož tajenku.

> Návrh na grafiku: stylizovaný úřední dopis z roku 1835 s částkou „6 fl. 26 kr.“ — hráč by částku vyčetl z obrázku.

### 10. Nádvoří — tajenka — `TEXT`

**Příběh (vypravěč):** Prošel jsi celou cestu mistra Husse. V tajence máš devět písmen, jenže jsou zpřeházená. Přeskládej je a najdeš slovo, podle kterého Huss žil.

**Úkol:** Jaké slovo řekl Huss justičnímu radovi, když si přebíral odsouzeného?

**Přijímané odpovědi:** `povinnost`

**Nápovědy:**
1. Písmena byla P, O, V, I, N, N, O, S, T.

**Odměna:** Povinnost. „Vykonám svoji povinnost co nejbedlivěji, podle moci a práva mi propůjčeného,“ řekl tehdy Huss. *(následuje závěrečný příběh)*

---

## Co je pravda a co fikce

| Doloženo (zdroj) | Herní fikce |
|---|---|
| Karl Huss, poslední chebský kat, vykonal 19. 8. 1822 na Lokti rozsudek nad Karlem Hainsem (Říha) | Písař, který chodí s Hussem po hradě |
| Hains zavraždil 16. 4. 1822 právního úředníka Matyáše Gerstnera, kterému dlužil; rozsudek 24. 5. (Říha) | Přiřazení jednotlivých kroků k místům na hradě |
| Rozhovor v cele, obraz Spasitele, prosba jít pěšky, kněz Seifert, slova o povinnosti — citace z kroniky (Říha) | Hussovy vzpomínkové monology (stylizace podle kroniky) |
| Huss zkoušel s odsouzeným text jeho „poslední role“ (Říha) | Žalářník se svazkem klíčů u brány |
| Odměna 47 zlatých, v roce 1835 výzva k vrácení 6 zl 26 kr (Říha) | Huss drží dopis v ruce na Kynžvartu |
| Kronika ve sbírkách zámku Kynžvart, Huss kustodem muzea (Říha: „o pět let později“, tj. 1827) | |

**Ověřit před ostrým provozem** (nepochází z článku): hrad jako věznice od 1822 do poloviny 20. století, zrušení mučení roku 1776, rotunda jako nejstarší stavba hradu, odvození jména Loket od ohybu Ohře, 1 zlatý konvenční měny = 60 krejcarů, existence mučírny v expozici.

## Poznámky k testování

- Typy: `CHOICE` (kroky 1, 2, 5, 6, 7), `TEXT` (3, 4, 10), `NUMBER` (8, 9).
- Krok 3 testuje odpověď z písmen: `badc`, `B, A, D, C` i ` b a d c ` musí být správně.
- Krok 4 má více přijímaných odpovědí. Kroky 4 a 7 jsou poslechové úlohy (`listen_only`).
- U `CHOICE` posílá klient text vybrané možnosti; přijímaná odpověď musí po normalizaci odpovídat textu ve `station_choice`.

## Možná rozšíření aplikace pro tuto hru

| Rozšíření | Krok | Náročnost |
|---|---|---|
| ~~Obrázek u kroku~~ — hotovo (V8) | všechny | |
| ~~Poslechová úloha se skrytým přepisem~~ — hotovo (V8) | 4, 7 | |
| ~~Samostatný text „Kam dál“~~ — hotovo (V8); hlasově zůstává součástí nahrávky odměny | všechny | |
| Seřazovací úloha přetažením karet místo psaní písmen | 3 | střední (nový typ úlohy) |
