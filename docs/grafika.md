# Grafika — demo „Katova povinnost“

Prompty pro AI generátor obrázků (Midjourney, DALL·E, Gemini, Leonardo…). Obsah hry viz `docs/04-prototypova-hra.md`.

## Jak na to

- **Formát:** na šířku **4:3** (např. 1600 × 1200 px), JPG nebo PNG. Zmenšení a kompresi pro mobil udělám sám.
- **Pojmenování:** přesně podle tabulky (`cover.jpg`, `st01.jpg` … `st10.jpg`). Hotové obrázky uložte do složky `grafika/` v projektu, přesunu je do hry.
- **Žádný text v obrázku.** AI nápisy zkomolí. Vše, co hráč potřebuje k řešení úkolu, zůstává v textu hry, obrázek jen dokresluje atmosféru.
- **Jednotný styl:** ke každému promptu připojte stejný **stylový dodatek** níže. Když generátor umí referenční obrázek („style reference“), použijte první povedený obrázek jako vzor pro ostatní.
- **Postavy:** Karl Huss není „temný kat v kápi“. V roce 1822 je to starší, upravený muž v tmavém civilním oděvu té doby (dlouhý kabát, vesta, šátek kolem krku), klidná vlídná tvář. V roce 1835 je to starý pán, kustod muzea.
- **Citlivé téma:** žádná poprava, krev ani mučení na obrázku. Napětí stačí naznačit světlem a prostředím.
- Pokud má generátor možnost nahrát fotku, pomůže skutečná fotka hradu Loket jako předloha (krok 1, 2, 8).

### Stylový dodatek (připojit ke každému promptu)

```
painterly historical illustration, early 19th century Bohemia, muted earthy palette with warm candlelight accents, soft atmospheric light, detailed but calm, storybook realism, no text, no letters, no watermark, 4:3 landscape
```

---

## Obrázky

| Soubor | Kde ve hře | Co má ukázat |
|---|---|---|
| `cover.jpg` | úvod hry | Starý Huss s úředním dopisem v muzeu na zámku Kynžvart (1835) |
| `st01.jpg` | 1 Vyhlídka nad Ohří | Hrad Loket na skále v ohybu řeky |
| `st02.jpg` | 2 Hradní brána | Žalářník se svazkem klíčů v bráně |
| `st03.jpg` | 3 Rozsypaná kronika | Rozsypané listy kroniky na nádvoří |
| `st04.jpg` | 4 Rozhovor v cele | Kat a odsouzený v cele při svíčce |
| `st05.jpg` | 5 Mučírna | Prázdná, zaprášená mučírna |
| `st06.jpg` | 6 Poslední role | Kat zkouší s odsouzeným text z papíru |
| `st07.jpg` | 7 Poslední cesta | Kněz u románské rotundy za úsvitu |
| `st08.jpg` | 8 Hradní věž | Výhled z věže na kopce kolem Lokte |
| `st09.jpg` | 9 Účet z roku 1835 | Úřední dopis s pečetí a mince na stole |
| `st10.jpg` | 10 Tajenka | Otevřená kronika s brkem, závěrečná atmosféra |

### cover.jpg — Huss s dopisem z účtárny

```
An elderly gentleman in his seventies, Karl Huss, former executioner and now custodian of a castle museum, standing in a 19th century cabinet of curiosities full of mineral collections, coins and old books, holding an official letter with a red wax seal, slightly indignant but dignified expression, dark Biedermeier frock coat and cravat, window light, year 1835
```

### st01.jpg — Hrad Loket nad Ohří

```
A medieval Gothic stone castle with a tall square tower standing on a high granite rock, the river Ohře wrapping around the rock in a tight meander like a bent arm, small town roofs below the castle, late summer afternoon, a horse carriage approaching on the road in the foreground, view from a distance, 1822
```

### st02.jpg — Žalářník v bráně

```
A gruff castle jailer in simple early 19th century clothes standing in an arched stone castle gateway, holding a heavy ring of iron keys, half in shadow, the castle courtyard visible behind him, morning light, sense of a castle turned into a prison, 1822
```

### st03.jpg — Rozsypaná kronika

```
Four loose handwritten manuscript pages scattered on the cobblestones of a castle courtyard, a leather-bound chronicle lying open nearby, a quill and an ink pot, wind slightly lifting one page, warm late light, close-up from above, pages show only illegible scribbles
```

### st04.jpg — Rozhovor v cele

```
A vaulted stone prison cell lit by a single candle, an elderly calm man in a dark frock coat standing and speaking kindly to a young remorseful prisoner sitting on a wooden bench, the prisoner holding a small devotional picture of Christ, quiet emotional moment, deep shadows, 1822
```

### st05.jpg — Mučírna

```
An abandoned dusty medieval torture chamber in a castle cellar, old wooden instruments covered with cobwebs and cloth, clearly unused for decades, a beam of light from a small barred window, eerie but not gory, no people
```

### st06.jpg — Poslední role

```
An elderly calm man in a dark frock coat sitting at a wooden table in a plain castle room, patiently rehearsing a text with a younger prisoner who reads from a sheet of paper, like a theatre director with an actor, candle and window light, quiet focused mood, 1822
```

### st07.jpg — Poslední cesta

```
A small round Romanesque stone rotunda chapel inside a castle at dawn, a priest in a black cassock waiting at its door holding a prayer book, soft mist and first golden sunlight, solemn quiet mood, 1822
```

### st08.jpg — Výhled z věže

```
View from the top of a medieval castle tower over the green wooded hills and the river valley around the town of Loket, a small procession of people and soldiers on a distant road, seen from far away, summer morning haze, wide landscape, 1822
```

### st09.jpg — Účet z roku 1835

```
An official Austrian government letter from 1835 with a red wax seal lying on a wooden desk, next to a small leather purse and a neat stack of old silver and copper coins, an old man's hand counting the coins, warm lamplight, close-up, the letter shows only illegible handwriting
```

### st10.jpg — Tajenka

```
An old leather-bound chronicle lying open on a desk by a window, a quill resting in an ink pot, a sealed letter beside it, the castle visible through the window in warm evening light, peaceful reflective mood, end of a story
```
