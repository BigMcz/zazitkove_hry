# 05 — Plán prototypu

Postupuj po milnících v tomto pořadí. Každý milník končí zeleným buildem a testy. Dokončené body odškrtni (`[x]`).

## M0 — Kostra projektu

- [x] `docker-compose.yml` s PostgreSQL 16 (port 5432, DB `zazitky`, uživatel/heslo jen pro lokální vývoj, pojmenovaný volume)
- [x] `backend/` — Spring Boot projekt (Maven Wrapper, Java 21, závislosti dle `docs/02-architektura.md`)
- [x] `application.yml`, `application-dev.yml`, `application-prod.yml`
- [x] `frontend/` — Vite + React + TypeScript, Tailwind, React Router, TanStack Query, Vitest; proxy `/api` → `:8080`
- [x] `.gitignore`, `README.md` s návodem na spuštění
- [x] Ověřit: backend nastartuje proti DB, `/actuator/health` = UP; frontend zobrazí prázdnou úvodní stránku

## M1 — Databáze a entity

- [x] `V1__init_schema.sql` přesně podle `docs/03-datovy-model.md`
- [x] JPA entity a repository pro všechny tabulky (včetně `accesscode` — jen entity + repo)
- [x] `ddl-auto=validate` projde
- [x] Integrační test s Testcontainers: migrace proběhnou na čisté DB, kontext nastartuje

## M2 — Obsah demo hry a čtení her

- [x] `TextNormalizer` + unit testy (diakritika, velikost písmen, mezery, interpunkce, NUMBER)
- [x] `R__demo_game.sql` — demo hra podle `docs/04-prototypova-hra.md` (idempotentní, pevná UUID)
- [x] Test: každé `accepted_answer.normalized_value` v DB = `TextNormalizer.normalize(value)`
- [x] `GET /api/v1/games`, `GET /api/v1/games/{id}` + DTO + testy
- [x] `ProblemDetail` handler (404, 400)

## M3 — Herní logika a API relací

- [x] `SessionService`: start, getState, submitAnswer, revealHint, abandon, expirace
- [x] `AnswerEvaluator` + unit testy (TEXT, NUMBER, CHOICE, více variant)
- [x] Endpointy `/api/v1/sessions…` dle architektury, hlavička `X-Session-Token`
- [x] Každý pokus se zapíše do `answer_attempt`; `attempts_count` a `hints_used` se aktualizují
- [x] Chybové stavy: 401 (token), 409 (neaktivní stanoviště / hra není rozehraná), 410 (vypršelo)
- [x] Optimistický zámek (`@Version`) na `game_session`
- [x] Integrační test: celý průchod demo hrou (špatná odpověď → nápověda → správná odpověď … → `COMPLETED`, správný součet penalizací)
- [x] Test: odpověď na stanoviště, které ještě není odemčené → 409; v žádné odpovědi API nejsou správné odpovědi ani neodkryté nápovědy

## M4 — Frontend

- [x] API klient + typy DTO + hooky TanStack Query; token v `localStorage` (try/catch)
- [x] `HomePage` — seznam her, „Pokračovat ve hře“ při existujícím tokenu
- [x] `GameIntroPage` — intro, název týmu, „Začít“
- [x] `StationPage` — postup, příběh, úkol, `AnswerInput` (TEXT / NUMBER s numerickou klávesnicí / CHOICE jako velká tlačítka), nápovědy
- [x] `RewardPage`, `FinalePage` (souhrn: čas, nápovědy, penalizace)
- [x] Obnova hry po reloadu; při 401/410 smazat token a vrátit na úvod s hláškou
- [x] Mobile-first vzhled: velká tlačítka, vysoký kontrast, čitelné písmo; vyzkoušet v DevTools na šířce 360 px
- [x] Testy komponent (`AnswerInput`, `HintList`)

## M5 — Dotažení

- [x] Produkční build: frontend se zabalí do JAR (`static/`), SPA fallback pro klientské routy
- [x] `Dockerfile` (multi-stage) pro celou aplikaci
- [x] Ruční průchod demo hrou na skutečném telefonu v lokální síti (2026-10-28, Docker image na :8080)
- [x] Aktualizovat dokumentaci podle skutečného stavu (API, datový model, otevřené otázky)

## Definice hotového prototypu

1. `docker compose up -d` + spuštění backendu a frontendu podle README funguje na čistém počítači.
2. Demo hru lze na telefonu projít od začátku do konce včetně nápověd a špatných odpovědí.
3. Po zavření a znovuotevření prohlížeče hra pokračuje.
4. `./mvnw verify`, `npm run build` a `npm test` procházejí.
5. V DB existují tabulky pro partnery, dávky a přístupové kódy, připravené pro další fázi.

## F1 — Přístupové kódy

- [x] Rozhodnutí (Q2, Q3): 8 znaků, platnost 5 h od aktivace, prodej = aktivace, pokračování stejné hry, vývojový kód bez expirace
- [x] `V2__access_code_activation.sql` + ER diagram a DDL v `docs/03-datovy-model.md`
- [x] Generování kódů (`SecureRandom`, abeceda bez zaměnitelných znaků), normalizace zadání
- [x] `POST /api/v1/access-codes/check`, `POST /api/v1/sessions` s kódem: aktivace, platnost, pokračování, vypršení (410)
- [x] Limit pokusů o kód (10/min na IP) a o přihlášení do administrace (5/min na IP)
- [x] Administrace: Spring Security (jeden účet z konfigurace, CSRF), dávky, CSV pro tisk kartiček, měsíční přehled aktivací pro fakturaci
- [x] Frontend: zadání kódu na úvodu (i z QR `?kod=`), `/start`, `/admin`; zrušen výběr her
- [x] Vývojový kód `DEMO-KATA` v dev seedu
- [x] Testy (backend + frontend), ruční průchod v prohlížeči
- [x] Administrace pro partnera (Q12): `V3__partner_user.sql`, role `ADMIN`/`PARTNER`, správa účtů hradů, přehled hradu jen s vlastními daty, změna hesla

## F2 — Testovací server a nasazení

- [x] Značka StoryHold (Q15), doména `storyhold.cz` (WEDOS), e-mail `info@storyhold.cz` (Seznam Email Profi, SPF, DKIM, DMARC)
- [x] VPS (Q1): Ubuntu 24.04, přístup jen SSH klíčem, `ufw` 22/80/443, swap, automatické aktualizace
- [x] Docker Engine + Compose, rotace logů
- [x] DNS `test.storyhold.cz`, Caddy s HTTPS, basic auth a zástupnou stránkou
- [x] `deploy/staging/` (`compose.yaml`, `Caddyfile`, `.env.example`) a skript `deploy/deploy-staging.ps1`, popis v `02-architektura.md` kap. 6 a v README
- [x] První nasazení: `.env` na serveru, nahrání souborů, spuštění
- [x] Ověření: `/actuator/health` = UP (za basic auth), demo hra přes `DEMO-KATA` na telefonu přes mobilní data, přihlášení do `/admin` silným heslem, CSV s odkazy `https://test.storyhold.cz/?kod=…`, MP3 na iPhonu (Range přes Caddy)
- [x] Druhý účet basic auth `ukazka` pro prezentace

### Později

- [ ] Zálohy: denní `pg_dump` → `age`/`gpg` → WEDOS Disk přes rsync, retence 14 denních + měsíční (až budou ostrá data)
- [ ] Produkce `storyhold.cz` na stejném serveru: vlastní stack bez seedu a bez basic auth, DNS `@` a `*` na VPS
- [ ] Rate limiting odpovědí (Q11) před ostrým provozem
- [ ] DMARC `p=quarantine` po ověření doručování
- [ ] Skutečné IP klientů přes IPv6: Docker bez IPv6 předává Caddy spojení z IPv6 s adresou brány, limity pokusů pak sdílí všichni hráči na IPv6 (zapnout IPv6 v Dockeru, nebo Caddy v síti hostitele)
- [ ] GitHub Container Registry a CI místo `docker save` / `scp`

## F3 — Vyhledávače (SEO)

Rozhodnutí Q16, popis v `02-architektura.md` kap. 4 „Vyhledávače (SEO)“.

- [x] Hlavička `index.html`: titulek, kanonická adresa, absolutní `og:url` a `og:image`, JSON-LD `Organization` a `WebSite`
- [x] `robots.txt` (bez hry, administrace a API) a `sitemap.xml`
- [x] Backend: routy aplikace jako `app.html` s `X-Robots-Tag: noindex`, neznámé stránky skutečná 404 (`SpaPages`)
- [x] Předgenerování úvodní stránky při buildu (`prerender.mjs`, `src/prerender.tsx`), test
- [x] Hlavní obrázek s vysokou prioritou, obrázek níž na stránce líně
- [x] Ověřeno lokálně: `/` s obsahem v HTML, `/play` s `noindex`, `/neexistuje` 404 se stránkou „Stránka nenalezena“, `?kod=` se předvyplní, konzole bez chyb

### Později

- [ ] Po spuštění produkce: Google Search Console a Seznam Webmaster (ověření domény, odeslání sitemapy), kontrola náhledu odkazu (Facebook Sharing Debugger)
- [ ] Odkaz z webu hradu Loket na `storyhold.cz` (ideálně součást smlouvy), zápis na Firmy.cz, turistické portály (kudyznudy.cz, kraj)
- [ ] Stránky pro jednotlivé lokality (např. `/hry/loket`) s vlastním textem, až bude skutečný obsah hry

## F4 — Ochrana osobních údajů a identifikace provozovatele

Rozhodnutí Q17: bez lišty cookies, jen technicky nezbytné ukládání v prohlížeči.

- [x] Identifikace provozovatele (§ 435 občanského zákoníku) na stránce `/ochrana-udaju`, odkaz „Provozovatel a ochrana osobních údajů“ v patičce (adresa ne přímo na úvodu), společný `SiteLayout`
- [x] Stránka `/ochrana-udaju` (čl. 13 GDPR: správce, účely, právní základy, lhůty, příjemci, práva, místní úložiště), test
- [x] Doplněno podle odpovědí provozovatele: hra 12 měsíců, komunikace 3 roky, zpracovatelé WEDOS a Seznam, hrad jen souhrnné počty, účinnost 1. 11. 2026 (upravit při spuštění produkce)
- [x] Mazání názvů týmů 12 měsíců po vypršení kódu (`SessionRetention`, denně ve 3:30), test
- [ ] Smlouvy o zpracování (DPA) s WEDOS a Seznamem — obvykle součást obchodních podmínek, ověřit
- [ ] Kontrola textu právníkem před ostrým provozem

## F5 — Obsah webu pro hráče

- [x] Úvod: sekce „Proč StoryHold“ a „Pro památky“ (bez obchodních podmínek, výzva ozvat se)
- [x] Úvod: „Než vyrazíte“ (tipy) a „Časté otázky“ (cena na pokladně, věk, tým, délka, bez aplikace, pokračování, nápovědy, bezbariérovost podle památky)
- [x] Stránka `/pravidla` (kartička a platnost, průběh, bezpečnost, potíže a reklamace), odkaz v patičce
- [x] Pravidla potvrzena provozovatelem: reklamace kartičky i na pokladně památky, při technické chybě náhradní kód
- [ ] Reklamace na pokladně zapsat do smlouvy s hradem; postup pro náhradní kód (vygenerovat v administraci a poslat hráči)
- [x] Stránka 404 ve vzhledu webu
- [x] Obrazovka „Než začnete“ před úvodem hry (zvuk, bezpečnost, „Hrát bez zvuku“)
- [x] Odkaz z QR kódu vede rovnou do hry (nová hra na „Než začnete“, rozehraná pokračuje, neplatný kód s důvodem na úvodu)
- [ ] Měření návštěvnosti bez cookies (Umami na VPS nebo Plausible) — odloženo, rozhodnout s produkcí; pak doplnit do `/ochrana-udaju`
- [ ] Ikona pro plochu telefonu (`apple-touch-icon`, `manifest`)
- [ ] Podrobná stránka pro památky (průběh spolupráce, prezentace ke stažení)
- [ ] Po spuštění: hodnocení hráčů, sociální sítě, dárkové poukazy

## F6 — Offline režim (rozhodnutí Q4)

Návrh v `02-architektura.md` kap. 4 „Offline režim“. Každý krok se dá nasadit samostatně.

- [x] Krok 1: aplikace jako PWA, service worker s kostrou aplikace, offline navigace, mezipaměť pro obrázky a zvuky (Range); ověřeno lokálně s vypnutým serverem (úvod i `/play` se načtou, data hry zatím ne)
- [x] Krok 2: balíček relace (`GET /sessions/current/package`), šifrování obsahu po úkolech, `TextNormalizer` v TypeScriptu se společnými testovacími případy (`test-fixtures/`), stažení médií při startu s ukazatelem průběhu; ověřeno lokálně (demo: 10 stanovišť, balíček 20 kB, 33 souborů)
- [x] Krok 3: hra z balíčku v telefonu (odpovědi, nápovědy, odměny, tajenka, finále) bez signálu, fronta akcí odeslaná při signálu; ověřeno lokálně s vypnutým serverem (2 stanoviště offline, po zapnutí serveru 3 pokusy v `answer_attempt`)
- [x] Oprava: statické soubory se revalidují podle obsahu (`ETag`), ne podle data 1980 z JAR (prohlížeč jinak po nasazení držel starou verzi)
- [ ] Krok 4: synchronizace událostí na server (`POST /sessions/current/sync`), idempotence, časy z telefonu, vypršení kódu
- [ ] Krok 5: ověření na iPhonu a Androidu v letadlovém režimu, popis v README

## Další fáze

1. ~~Přístupové kódy~~ — hotovo (F1) včetně přístupu pro hrady
2. Skutečný obsah pro hrad Loket (průzkum na místě, příběh kata)
3. Administrace obsahu a statistiky obtížnosti; v administraci „vystavit náhradní kód“ (jeden kód ke stejné hře, rovnou platný, bez tisku a předání dávky) pro hráče s technickou chybou
4. Ostrý provoz — testovací server hotov (F2), SEO základy hotovy (F3), dál produkce, zálohy, rate limiting
5. Volitelně PWA/offline, GPS/QR, jazykové mutace
