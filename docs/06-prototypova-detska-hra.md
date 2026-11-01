# 06 — Dětská hra „Poklad mistra Husse“ (od 6 let)

> **Stav: v dev seedu bez obrázků a nahrávek** (`R__demo_game_children.sql`, kód `DEMO-DETI`, příznak `for_children`, postup v `docs/05-plan-prototypu.md` F8). Dětská verze dema „Katova povinnost“ (`docs/04-prototypova-hra.md`). Obsahuje příběh, úkoly, texty k namluvení a prompty na obrázky.

## Koncept

Dětská verze dělá z Karla Husse vlídného dědečka a sběratele, poprava, vražda i mučírna z ní úplně mizí. Hráče provází krkavec Kryštof, který Hussovi sebral klíč od truhly a vrátí ho, jen když děti vyřeší jeho hádanky.

- **Rámec:** Rok 1835. Starý pan Huss, správce zámeckého muzea v Kynžvartu, přijel na Loket pro starou truhlu do své sbírky. Krkavec Kryštof z hradní věže miluje lesklé věci a sebral mu klíč. Děti jsou Hussovi „malí písaři“.
- **Téma kata:** zazní jednou a jemně, v úvodu: Huss byl kdysi katem, „trestal zločince, když tak rozhodl soud“. Dál hra stojí na hradu, kronice a sbírce. Víc může dítěti vysvětlit rodič podle sebe.
- **Co vypadlo:** Karl Hains, vražda, rozsudek, poprava, mučírna, rozhovor v cele, účtování odměny za popravu.
- **Co zůstalo z historie:** jméno Loket podle ohybu Ohře, hrad jako věznice od roku 1822, Huss jako kronikář a sběratel, psaní brkem.
- **Hlasy:** vypravěč, Huss (laskavý dědeček) a Kryštof (rozverný krkavec, krákorá a umí napodobit jiné zvuky jako skuteční krkavci).
- **Délka:** 6 kroků + tajenka, asi 30 minut. Dospělá verze má 10 kroků, na šestileté je to moc.
- **Tajenka:** slovo **POKLAD**. Písmena chodí rovnou ve správném pořadí (příznak hry `for_children`), takže finále je jen přečtení slova — ideální pro dítě, které se učí číst.
- **Úkoly pro šestileté:** počítání a sčítání do 10, pokračování řady, poslech zvuků, výběr ze tří možností. Převažuje `CHOICE` a `NUMBER`, jediný `TEXT` je tajenka.
- **Role rodiče:** čte zadání a možnosti a zadává odpověď. Dítě poslouchá, počítá a rozhoduje. Odpověď je vždy v nahrávce, textu nebo obrázku, takže demo jde hrát kdekoli.
- **Nápovědy bez penalizace** (0 s): u dětí nechceme stres z času. Nápovědy mluví k dítěti, rodič je přečte.

## Metadata hry

Hra běží vedle dospělé „Katovy povinnosti“ jako samostatná hra na stejné lokalitě.

| Pole | Hodnota |
|---|---|
| `slug` | `deti-poklad-mistra-husse` |
| `title` | Poklad mistra Husse |
| `subtitle` | Hra pro děti od 6 let — s krkavcem Kryštofem |
| `estimated_minutes` | 30 |
| `session_validity_hours` | 24 |
| `status` | `PUBLISHED` |
| Partner / lokalita | Demo partner / Hrad Loket |
| Obrázky / zvuky | `/images/deti/`, `/audio/deti/` |

### Úvodní příběh (`intro_text`)

**Vypravěč:** Hrad Loket, roku 1835. Na nádvoří stojí starý pán s laskavýma očima. Jmenuje se Karl Huss.

**Huss:** Dobrý den, malý písaři! Když jsem byl mladší, byl jsem katem města Chebu. Trestal jsem zločince, když tak rozhodl soud. Dnes sbírám zajímavé věci do muzea a všechno zapisuji do kroniky. Přijel jsem si pro starou truhlu. Jenže někdo mi sebral klíček!

**Kryštof:** Krá! To já! Jsem krkavec Kryštof a lesklé věci mám nejradši na světě. Klíček vrátím, ale jen tomu, kdo vyřeší všechny moje hádanky. Krá krá!

**Vypravěč:** Za každou hádanku ti Kryštof dá jedno písmenko. Na konci z nich složíš kouzelné slovo, které otevře truhlu.

### Závěrečný příběh (`outro_text`)

**Kryštof:** Poklad! Krá! Tak tady máš ten klíček… i když byl moc hezky lesklý.

**Huss:** Děkuji, písaři. Podívejme, co je v truhle… Staré mince a kamínky do mé sbírky. A tady dole čistá kniha. Do té začneme psát novou kroniku. A první zápis bude o tobě, můj nejšikovnější pomocníku.

**Vypravěč:** Karl Huss opravdu žil. Psal kroniku a staral se o muzeum na zámku Kynžvart. Krkavce Kryštofa jsme si vymysleli. Ale skuteční krkavci jsou moc chytří ptáci a umí napodobit spoustu zvuků.

---

## Kroky

Hra má šest kroků s hádankou a sedmý krok s tajenkou. Místa kopírují trasu dospělé verze, aby šly obě hry hrát po stejných stanovištích. Všechny nápovědy mají penalizaci 0 s.

| # | Místo | Hádanka | Typ | Odpověď | Písmeno |
|---|---|---|---|---|---|
| 1 | Vyhlídka nad Ohří | Proč se hrad jmenuje Loket | `CHOICE` | Řeka se ohýbá jako ruka | P |
| 2 | Hradní brána | Klíče žalářníka — pokračuj v řadě | `CHOICE` | Stříbrný | O |
| 3 | Nádvoří | Kolik zvířat Kryštof napodobil (poslech) | `NUMBER` | 4 | K |
| 4 | Vězení | Kolik tyčí má mříž (3 + 2) | `NUMBER` | 5 | L |
| 5 | Palác | Z kterého ptáka bylo Hussovo pero | `CHOICE` | Z husy | A |
| 6 | Hradní věž | Kolik knoflíků má Kryštof v hnízdě (poslech) | `NUMBER` | 6 | D |
| 7 | Nádvoří u truhly | Tajenka | `TEXT` | poklad | — |

### 1. Vyhlídka nad Ohří — `CHOICE`

**Příběh (Huss):** Podívej se dolů. Vidíš řeku? Jmenuje se Ohře. Teče kolem skály a ohýbá se, jako když ohneš ruku. Zkus to taky! Ohni ruku. Ta špička uprostřed je tvůj loket.

**Kryštof:** Krá! A teď moje hádanka!

**Úkol:** Proč se hrad jmenuje Loket?

**Možnosti:** Řeka se ohýbá jako ruka · Bydlel tu pan Loket · Hrad má tvar boty

**Přijímané odpovědi:** `Řeka se ohýbá jako ruka`

**Nápověda:** Ohni ruku a podívej se na řeku. Nejsou si podobné?

**Odměna (Kryštof):** Krá! Správně! Řeka dělá loket, a proto se hrad jmenuje Loket. Tady máš první písmenko: P. P jako písař!
*Kam dál (vypravěč):* Dojdi k hradní bráně. Kryštof už tam na tebe čeká.

### 2. Hradní brána — `CHOICE`

**Příběh (Huss):** Když jsem sem přijel poprvé, už tu nebydleli páni. Z hradu se stalo vězení. U brány stál žalářník, který hlídal vězně. Na kruhu měl spoustu klíčů a ty cinkaly: zlatý, stříbrný, zlatý, stříbrný, zlatý…

**Kryštof:** Krá! A jaký klíč přijde teď?

**Úkol:** Zlatý, stříbrný, zlatý, stříbrný, zlatý… Jaký klíč je na řadě?

**Možnosti:** Zlatý · Stříbrný · Dřevěný

**Přijímané odpovědi:** `Stříbrný`

**Nápověda:** Klíče se pořád střídají. Co přijde vždycky po zlatém?

**Odměna (Kryštof):** Krá! Stříbrný! Druhé písmenko je O.
*Kam dál:* Projdi na nádvoří. Kryštof si pro tebe připravil zvukovou hádanku.

### 3. Nádvoří — Kdo se ozval? — `NUMBER` (poslechová úloha)

**Příběh (Kryštof):** Krá! Umím napodobit každé zvíře, které kdy na hradě žilo. Poslouchej pozorně! *I-ha-há!* *Haf, haf!* *Kykyryký!* *Mňau!* Krá!

**Úkol:** Kolik různých zvířat Kryštof napodobil?

**Přijímané odpovědi:** `4`

**Nápověda:** Poslechni si to znovu. Na každé zvíře zvedni jeden prst.

**Odměna (Kryštof):** Krá! Čtyři! Kůň, pes, kohout a kočka. Koně tahali vozy a kočky chytaly myši. Písmenko K.
*Kam dál:* Sestup do sklepení k vězení. Neboj, dnes už je prázdné.

> Rodič zadává číslici. Slovo „čtyři“ aplikace jako číslo nevezme.

### 4. Vězení — Mříže — `NUMBER`

**Příběh (Huss):** Tady bývalo vězení. Zavírali sem lidi, kteří udělali něco hodně zlého. Aby neutekli, měla okna silné železné mříže.

**Kryštof:** Krá! Tři tyče jdou shora dolů a dvě tyče zleva doprava.

**Úkol:** Kolik železných tyčí má mříž dohromady?

**Přijímané odpovědi:** `5`

**Nápověda:** Zvedni tři prsty na jedné ruce a dva na druhé. Kolik je to dohromady?

**Odměna (Kryštof):** Krá! Pět! Přes takovou mříž neproletí ani krkavec. Písmenko L.
*Kam dál:* Vyjdi nahoru do hradního paláce. Tam Huss psal svou kroniku.

> Na hradě se úkol přesune na skutečné okno s mříží a odpověď se upraví podle něj.

### 5. Palác — Husí brk — `CHOICE`

**Příběh (Huss):** Všechno, co se stalo, jsem si zapisoval do kroniky. To je kniha, do které se píše, co se kde přihodilo. Tenkrát se nepsalo propiskou ani tužkou, ale ptačím pírkem. Říká se mu brk. Namočil jsem ho do inkoustu a psal.

**Kryštof:** Krá! A z kterého ptáka bylo Hussovo pero? Napovím: zní to skoro jako jeho jméno!

**Úkol:** Z kterého ptáka bylo pero, kterým Huss psal?

**Možnosti:** Z husy · Ze slepice · Z krkavce

**Přijímané odpovědi:** `Z husy`

**Nápověda:** Huss… Hu… Který pták zní podobně?

**Odměna (Kryštof):** Krá! Z husy! Husí brky se opravdu používaly nejčastěji. Huss a husa, to se pamatuje samo. Písmenko A.
*Kam dál:* Vystoupej na hradní věž. Tam má Kryštof své hnízdo.

### 6. Hradní věž — Kryštofovo hnízdo — `NUMBER` (poslechová úloha)

**Příběh (Kryštof):** Krá! Tady nahoře je moje hnízdo. Mám tu schované lesklé knoflíky. Poslouchej, jak je počítám: *cink, cink, cink, cink, cink, cink!* Krá!

**Úkol:** Kolik lesklých knoflíků má Kryštof v hnízdě?

**Přijímané odpovědi:** `6`

**Nápověda:** Poslechni si to znovu. Na každé cinknutí zvedni jeden prst — budeš potřebovat obě ruce.

**Odměna (Kryštof):** Krá! Šest! A ten nejlesklejší poklad je klíček od truhly. Dám ti ho, až řekneš tajné slovo. Poslední písmenko je D.
*Kam dál:* Vrať se na nádvoří k truhle. Huss tam čeká.

### 7. Nádvoří — Truhla — `TEXT`

**Příběh (vypravěč):** Huss stojí u staré truhly. Na víku je zámek a nad ním šest prázdných políček. Máš šest písmenek: P, O, K, L, A, D.

**Kryštof:** Krá! Přečti je za sebou a řekni tajné slovo!

**Úkol:** Jaké slovo otevře truhlu?

**Přijímané odpovědi:** `poklad`

**Nápověda:** P-O-K-L-A-D. Co se schovává v truhlách?

**Odměna:** Cvak! Zámek povolil a víko truhly se pomalu otevírá. *(následuje závěrečný příběh)*

---

## Texty k namluvení

Děti nečtou, proto každá nahrávka příběhu končí otázkou a u `CHOICE` i všemi možnostmi. Čísla jsou vypsaná slovy pro převod řeči.

**Hlasy:**

- **Vypravěč:** klidný a vlídný, jako při čtení pohádky na dobrou noc.
- **Huss:** starý dědeček, pomalý, laskavý, trochu pečlivý úředník. Žádný „temný kat“.
- **Kryštof:** rozverný a rychlejší, chraplavě krákavý, ale nikdy strašidelný. „Krá!“ je jeho citoslovce.

**Technicky:** MP3, 64 kbps mono, soubory do `audio/deti-poklad-mistra-husse/`. Namlouváme v ElevenLabs (Voice Design + Eleven v3 s audio tags); prompty hlasů a texty se značkami jsou v `audio/deti-poklad-mistra-husse/nahravky-elevenlabs.md`. Každá nahrávka příběhu nejvýš 40 s. Tempo pomalejší než v dospělé verzi, po otázce pauza asi 2 s.

### Úvod — `intro.mp3`

**Vypravěč:** Hrad Loket, roku osmnáct set třicet pět. Na nádvoří stojí starý pán s laskavýma očima. Jmenuje se Karl Huss.

**Huss:** Dobrý den, malý písaři! Když jsem byl mladší, byl jsem katem města Chebu. Trestal jsem zločince, když tak rozhodl soud. Dnes sbírám zajímavé věci do muzea a všechno zapisuji do kroniky. Přijel jsem si pro starou truhlu. Jenže… někdo mi sebral klíček!

**Kryštof:** Krá! To já! Jsem krkavec Kryštof a lesklé věci mám nejradši na světě. Klíček vrátím… ale jen tomu, kdo vyřeší všechny moje hádanky. Krá krá!

**Vypravěč:** Za každou hádanku ti Kryštof dá jedno písmenko. Na konci z nich složíš kouzelné slovo, které otevře truhlu.

### Krok 1 — Vyhlídka nad Ohří

`st01-pribeh.mp3` — **Huss, pak Kryštof**

**Huss:** Podívej se dolů. Vidíš řeku? Jmenuje se Ohře. Teče kolem skály a ohýbá se, jako když ohneš ruku. Zkus to taky! Ohni ruku. Ta špička uprostřed je tvůj loket.

**Kryštof:** Krá! A teď moje hádanka! Proč se hrad jmenuje Loket? Protože se řeka ohýbá jako ruka? Protože tu bydlel pan Loket? Nebo protože má hrad tvar boty?

`st01-odmena.mp3` — **Kryštof, pak vypravěč**

**Kryštof:** Krá! Správně! Řeka dělá loket, a proto se hrad jmenuje Loket. Tady máš první písmenko: P. P jako písař!

**Vypravěč:** Dojdi k hradní bráně. Kryštof už tam na tebe čeká.

### Krok 2 — Hradní brána

`st02-pribeh.mp3` — **Huss, pak Kryštof**

**Huss:** Když jsem sem přijel poprvé, už tu nebydleli páni. Z hradu se stalo vězení. U brány stál žalářník, který hlídal vězně. Na kruhu měl spoustu klíčů a ty cinkaly: zlatý… stříbrný… zlatý… stříbrný… zlatý…

**Kryštof:** Krá! A jaký klíč přijde teď? Zlatý, stříbrný, nebo dřevěný?

`st02-odmena.mp3` — **Kryštof, pak vypravěč**

**Kryštof:** Krá! Stříbrný! Druhé písmenko je O.

**Vypravěč:** Projdi na nádvoří. Kryštof si pro tebe připravil zvukovou hádanku.

### Krok 3 — Nádvoří, kdo se ozval

`st03-pribeh.mp3` — **Kryštof** *(zvířata napodobuje sám, mezi nimi zřetelná pauza asi 1 s)*

**Kryštof:** Krá! Umím napodobit každé zvíře, které kdy na hradě žilo. Poslouchej pozorně!
*I-ha-há!* … *Haf, haf!* … *Kykyryký!* … *Mňau!*
Krá! Kolik různých zvířat jsem napodobil?

`st03-odmena.mp3` — **Kryštof, pak vypravěč**

**Kryštof:** Krá! Čtyři! Kůň, pes, kohout a kočka. Koně tahali vozy a kočky chytaly myši. Písmenko K.

**Vypravěč:** Sestup do sklepení k vězení. Neboj, dnes už je prázdné.

### Krok 4 — Vězení, mříže

`st04-pribeh.mp3` — **Huss, pak Kryštof**

**Huss:** Tady bývalo vězení. Zavírali sem lidi, kteří udělali něco hodně zlého. Aby neutekli, měla okna silné železné mříže.

**Kryštof:** Krá! Tři tyče jdou shora dolů… a dvě tyče zleva doprava. Kolik železných tyčí má mříž dohromady?

`st04-odmena.mp3` — **Kryštof, pak vypravěč**

**Kryštof:** Krá! Pět! Přes takovou mříž neproletí ani krkavec. Písmenko L.

**Vypravěč:** Vyjdi nahoru do hradního paláce. Tam Huss psal svou kroniku.

### Krok 5 — Palác, husí brk

`st05-pribeh.mp3` — **Huss, pak Kryštof**

**Huss:** Všechno, co se stalo, jsem si zapisoval do kroniky. To je kniha, do které se píše, co se kde přihodilo. Tenkrát se nepsalo propiskou ani tužkou, ale ptačím pírkem. Říká se mu brk. Namočil jsem ho do inkoustu… a psal.

**Kryštof:** Krá! Z kterého ptáka bylo Hussovo pero? Z husy, ze slepice, nebo z krkavce? Napovím: zní to skoro jako jeho jméno!

`st05-odmena.mp3` — **Kryštof, pak vypravěč**

**Kryštof:** Krá! Z husy! Husí brky se opravdu používaly nejčastěji. Huss a husa, to se pamatuje samo. Písmenko A.

**Vypravěč:** Vystoupej na hradní věž. Tam má Kryštof své hnízdo.

### Krok 6 — Věž, Kryštofovo hnízdo

`st06-pribeh.mp3` — **Kryštof** *(místo „cink“ může být zvuk cinknutí mince, ale přesně šestkrát a každé zvlášť)*

**Kryštof:** Krá! Tady nahoře je moje hnízdo. Mám tu schované lesklé knoflíky. Poslouchej, jak je počítám:
*cink… cink… cink… cink… cink… cink!*
Krá! Kolik lesklých knoflíků mám v hnízdě?

`st06-odmena.mp3` — **Kryštof, pak vypravěč**

**Kryštof:** Krá! Šest! A ten nejlesklejší poklad je klíček od truhly. Dám ti ho, až řekneš tajné slovo. Poslední písmenko je D.

**Vypravěč:** Vrať se na nádvoří k truhle. Huss tam čeká.

### Krok 7 — Truhla

`st07-pribeh.mp3` — **vypravěč, pak Kryštof**

**Vypravěč:** Huss stojí u staré truhly. Na víku je zámek a nad ním šest prázdných políček. Máš šest písmenek: P… O… K… L… A… D.

**Kryštof:** Krá! Přečti je za sebou a řekni tajné slovo!

`st07-odmena.mp3` — **vypravěč** *(na začátku zvuk cvaknutí zámku)*

**Vypravěč:** Cvak! Zámek povolil a víko truhly se pomalu otevírá.

### Závěr — `zaver.mp3`

**Kryštof:** Poklad! Krá! Tak tady máš ten klíček… i když byl moc hezky lesklý.

**Huss:** Děkuji, písaři. Podívejme, co je v truhle… Staré mince a kamínky do mé sbírky. A tady dole čistá kniha. Do té začneme psát novou kroniku. A první zápis bude o tobě, můj nejšikovnější pomocníku.

**Vypravěč:** Karl Huss opravdu žil. Psal kroniku a staral se o muzeum na zámku Kynžvart. Krkavce Kryštofa jsme si vymysleli. Ale skuteční krkavci jsou moc chytří ptáci a umí napodobit spoustu zvuků.

---

## Obrázky

Osm obrázků (cover + 7 kroků) ve stylu dětské obrázkové knihy, formát 4:3, zdrojové soubory do `grafika/deti/`, do hry se převedou na `.webp` jako u dospělé verze. Pravidla z `docs/grafika.md` platí dál: žádný text v obrázku a ke každému promptu stejný stylový dodatek.

- **Nic, co se počítá, nesmí stavět na obrázku.** AI počty věcí nedodrží. Odpověď je vždy v nahrávce a textu, obrázek jen dokresluje. Výjimka je krok 4: mříž musí mít 3 svislé a 2 vodorovné tyče, jinak generovat znovu nebo mříž oříznout.
- **Nic strašidelného:** žádné zbraně, řetězy ani temné kobky. Vězení je prázdné a prosvícené.
- **Kryštof musí vypadat vždy stejně.** Popis níže vkládejte do každého promptu, kde je. První povedenou verzi použijte jako referenci postavy, pokud to generátor umí.

### Stylový dodatek

```
warm children's picture book illustration, soft watercolor and gouache textures, friendly rounded shapes, gentle cheerful colors with warm golden light, early 19th century Bohemian castle setting, cozy and safe mood, no text, no letters, no watermark, 4:3 landscape
```

### Postavy

```
Kryštof: a friendly cartoon raven with glossy blue-black feathers, big round shiny eyes, a slightly oversized curved beak and a small red neckerchief, playful mischievous expression
```

```
Huss: a kind elderly grandfatherly man with white hair and round spectacles, dark brown Biedermeier frock coat, waistcoat and cravat, gentle smile
```

### Přehled

| Soubor | Kde ve hře | Co ukazuje |
|---|---|---|
| `cover.jpg` | úvod | Huss u truhly, Kryštof s klíčkem v zobáku |
| `st01.jpg` | 1 Vyhlídka | Hrad na skále v ohybu řeky |
| `st02.jpg` | 2 Brána | Vlídný žalářník se zlatými a stříbrnými klíči |
| `st03.jpg` | 3 Nádvoří | Kryštof na studni „zpívá“ |
| `st04.jpg` | 4 Vězení | Okno s mříží 3 + 2, Kryštof za ní |
| `st05.jpg` | 5 Palác | Huss píše brkem kroniku |
| `st06.jpg` | 6 Věž | Kryštofovo hnízdo s lesklými knoflíky a klíčkem |
| `st07.jpg` | 7 Truhla | Huss a Kryštof u otevírající se truhly |

### cover.jpg

```
Huss standing in a sunny castle courtyard next to an old wooden treasure chest with a big iron lock, looking puzzled and patting his empty pockets, Kryštof sitting on top of the stone wall above him holding a shiny golden key in his beak, Gothic castle tower in the background
```

### st01.jpg

```
A Gothic stone castle with a tall square tower standing on a high rock, a blue river curving tightly around the rock like a bent arm, small red town roofs below, Kryštof flying happily in the foreground, bright late summer afternoon, wide view
```

### st02.jpg

```
A friendly round-faced castle jailer with a bushy moustache standing in an arched stone gateway, holding up a big iron ring with alternating golden and silver keys, Kryštof perched on his shoulder looking curiously at the keys, morning sunlight
```

### st03.jpg

```
Kryštof standing proudly on the edge of an old stone well in a castle courtyard, beak wide open as if singing loudly, colorful musical notes and swirls floating around him, no other animals, cheerful sunny day
```

### st04.jpg

```
An empty, clean vaulted stone cellar lit by warm sunlight coming through one small window, the window has an iron grille of exactly three vertical bars and two horizontal bars, Kryštof peeking in through the grille from outside with a cheeky smile, friendly not scary
```

### st05.jpg

```
Huss sitting at a wooden desk by a window, writing in a big leather-bound chronicle with a long white feather quill dipped in an ink pot, Kryštof watching from the top of a bookshelf, candle and soft window light, cozy room
```

### st06.jpg

```
A big twig nest on top of a castle tower battlement, the nest full of shiny buttons and one golden key, Kryštof sitting proudly in the nest, wide view over green wooded hills and a river valley behind, bright morning
```

### st07.jpg

```
Huss kneeling happily next to an old wooden treasure chest in a castle courtyard, the lid opening with warm golden light glowing from inside, Kryštof dropping a golden key into Huss's open hand, joyful celebratory mood
```

---

## Co je pravda a co fikce

| Doloženo nebo běžně známé | Herní fikce |
|---|---|
| Karl Huss, poslední chebský kat, kronikář, později kustod muzea v Kynžvartu (Říha) | Krkavec Kryštof, ukradený klíček, truhla |
| Hrad Loket jako věznice od roku 1822 (ověřit, jako v dospělé verzi) | Huss přijel roku 1835 na Loket pro truhlu |
| Jméno Loket podle ohybu Ohře (ověřit) | Žalářník se zlatými a stříbrnými klíči |
| Psaní husím brkem, krkavci umí napodobit zvuky | Kryštofovo hnízdo na věži, zvířata na hradě |

S hradem doladit: okno s mříží pro krok 4, přístup na věž pro krok 6 a kam postavit truhlu (třeba u pokladny).

## Poznámky k nahrání do seedu

Hra jde nahrát do seedu bez změny schématu. Hotovo v `R__demo_game_children.sql`, cesty k obrázkům a nahrávkám se doplní, až budou soubory.

- Druhá hra v dev seedu s vlastními pevnými UUID a vlastním vývojovým kódem, např. `DEMO-DETI` (bez expirace, `multi_session`). Ověřit, že kód projde normalizací stejně jako `DEMO-KATA`.
- Každý krok má jednu nápovědu s `penalty_seconds = 0`.
- `listen_only = true` u kroků 3 a 6; `next_text` = věta „Kam dál“.
- Krok 7 má `reward_text` „Cvak! Zámek povolil…“, protože sloupec je `NOT NULL`.
- `normalized_value`: `reka se ohyba jako ruka`, `stribrny`, `4`, `5`, `z husy`, `6`, `poklad`. Hlídá je `DemoGameSeedTest`, když se rozšíří na všechny hry.
- Cesty: `/images/deti/stNN.webp`, `/images/deti/cover.webp`, `/audio/deti/stNN-pribeh.mp3`, `/audio/deti/stNN-odmena.mp3`, `/audio/deti/intro.mp3`, `/audio/deti/zaver.mp3`.

## Možná rozšíření aplikace pro děti

| Rozšíření | Proč | Náročnost |
|---|---|---|
| ~~Vlastní hláška špatné odpovědi pro hru („Krá, to ne! Zkus to znovu.“)~~ hotovo (`game.wrong_answer_text`, V12); namluvená zatím ne | Výchozí zní úředně | malá |
| ~~Finále bez času a penalizací, místo nich diplom se jménem týmu~~ hotovo (`game.for_children`, V12) | Soutěž s časem šestileté nezajímá | malá |
| ~~Sbíraná písmena jako políčka na obrazovce stanoviště~~ hotovo (V9, u dětské hry v pořadí) | Dítě vidí, jak tajenka roste, a nemusí si písmena pamatovat | střední |
| Obrázek u každé možnosti `CHOICE` | Dítě, které nečte, vybere samo | střední (`station_choice.image_url`) |
| Odměna na pokladně po ukázání finále (samolepka, razítko) | Hmatatelný konec hry, argument pro hrad | jen dohoda s hradem |

Otevřená otázka pro `docs/01-analyza.md`: prodávat dětskou hru jako samostatný kód, nebo jako rodinnou kartičku s oběma hrami?
