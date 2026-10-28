# 04 — Prototypová hra „Tajemství katova klíče“ (demo)

> **Vymyšlený obsah pro prototyp.** Neodpovídá skutečnosti na hradě Loket. Úkoly jsou řešitelné jen z textu na obrazovce, takže demo jde hrát kdekoli — u stolu, v kanceláři, při testování. Skutečná hra se stanovišti na hradě a ve městě vznikne později.

## Metadata hry

| Pole | Hodnota |
|---|---|
| `slug` | `demo-katuv-klic` |
| `title` | Tajemství katova klíče |
| `subtitle` | Demo hra — vyzkoušej si, jak to funguje |
| `estimated_minutes` | 20 |
| `session_validity_hours` | 24 |
| `status` | `PUBLISHED` |
| Partner | Demo partner (`revenue_share_pct` 50.00) |
| Lokalita | Demo hrad, město Loket |

### Úvodní příběh (`intro_text`)

Píše se rok 1621. Městský kat mistr Jakub zmizel beze stopy a s ním i klíč od hradního žaláře. Purkrabí tvrdí, že kat utekl. Jenže v katově světnici se našel lístek: „Kdo chce klíč najít, musí jít po mých stopách. Každá stopa skrývá jedno písmeno.“

Projdi pět míst, kudy kat naposledy šel, a slož z písmen, kde klíč ukryl.

### Závěrečný příběh (`outro_text`)

Klíč ležel celou dobu tam, kde by ho nikdo nehledal — pod prahem katovy vlastní světnice. Mistr Jakub neutekl. Ukryl klíč, aby žalář nemohl otevřít nikdo, kdo by chtěl osvobodit vězně dřív, než soud rozhodne o jejich vině. Díky tobě se klíč vrací na hrad a příběh mistra Jakuba může být konečně vyprávěn celý.

---

## Stanoviště

Tajenka: písmena z odměn stanovišť 1–4 dávají slovo **KLÍČ**, které je odpovědí na stanoviště 5.

### 1. Katova světnice — `TEXT`

**Příběh:** Ve světnici je cítit kouř z vyhaslého krbu. Na stole leží lístek psaný úhledným písmem.

**Úkol:** Na lístku stojí hádanka: „Čím víc z ní bereš, tím je větší. Co je to?“

**Přijímané odpovědi:** `jáma`, `díra`

**Nápovědy:**
1. Myslí se něco, co vzniká kopáním. — 60 s
2. Začíná na „J“ a hrobník ji kope každý den. — 120 s

**Odměna:** Správně! Pod lístkem je do stolu vyryto písmeno **K**. Kat si zjevně dal záležet, aby po sobě nechal stopu. Další stopa vede do hradní věže.

### 2. Hradní věž — `NUMBER`

**Příběh:** Strážný u věže vzpomíná: „Kat tudy šel včera v noci a schody počítal nahlas, jako by si je chtěl zapamatovat.“

**Úkol:** Věž má 5 pater. Do každého patra vede 12 schodů, jen do posledního jsou o 4 schody méně. Kolik schodů kat vystoupal až nahoru?

**Přijímané odpovědi:** `56`

**Nápovědy:**
1. Spočítej nejdřív, kolik by to bylo, kdyby všechna patra měla 12 schodů. — 60 s
2. 5 × 12 = 60. A teď odečti rozdíl u posledního patra. — 120 s

**Odměna:** Přesně tolik! Na 56. schodu je do kamene vyryto písmeno **L**. Z okna věže je vidět sklepení pod nádvořím.

### 3. Sklepení — `CHOICE`

**Příběh:** Ve sklepení je tma. Žalářník vypráví: „Kat sem přišel s mečem, provazem a lucernou. Odcházel ale potmě — meč měl u pasu a provaz přes rameno.“

**Úkol:** Co kat ve sklepení zanechal?

**Možnosti (`station_choice`):** 1. Meč · 2. Provaz · 3. Lucernu · 4. Klíč

**Přijímané odpovědi:** `Lucernu` (případně i `lucerna`)

**Nápovědy:**
1. Když odcházel potmě, něco mu chybělo. — 60 s

**Odměna:** Ano! Na lucerně je sazemi napsané písmeno **Í**. Pod lucernou leží útržek papíru se zvláštním nápisem.

### 4. Šifra na útržku — `TEXT`

**Příběh:** Na útržku je napsáno slovo, které na první pohled nedává smysl: **CSBOB**. Pod ním drobným písmem: „O jedno zpět.“

**Úkol:** Rozlušti, kam kat šel dál. Každé písmeno je v abecedě posunuté o jedno dál.

**Přijímané odpovědi:** `brána`

**Nápovědy:**
1. Místo C napiš písmeno, které je v abecedě těsně před ním. — 60 s
2. C → B, S → R… dokonči zbytek. — 120 s

**Odměna:** Brána! Na pantu hradní brány je vyryto písmeno **Č**. Teď máš všechna písmena.

### 5. Tajenka — `TEXT`

**Příběh:** Stojíš u brány a v hlavě skládáš písmena, která jsi cestou našel.

**Úkol:** Slož písmena ze všech stop v pořadí, v jakém jsi je našel. Co kat ukryl?

**Přijímané odpovědi:** `klíč`

**Nápovědy:**
1. Písmena byla K, L, Í, Č. — 60 s

**Odměna:** Klíč! A teď už víš, kde ho hledat… *(následuje závěrečný příběh)*

---

## Poznámky k testování

- Odpovědi ověřují normalizaci: `JÁMA`, ` jama `, `Díra!` musí být správně; `56 schodů` u typu `NUMBER` → `56` → správně.
- Stanoviště 3 ověřuje typ `CHOICE` a stanoviště 3 má jen jednu nápovědu (UI musí zvládnout různý počet).
- Ideální průchod bez nápověd: 5 správných odpovědí; nejhorší: 8 nápověd = 660 s penalizace.
- U `CHOICE` posílá klient text vybrané možnosti; přijímaná odpověď proto musí po normalizaci odpovídat textu v `station_choice`.
