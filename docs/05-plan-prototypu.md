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
- [ ] První nasazení: `.env` na serveru, nahrání souborů, spuštění
- [ ] Ověření: `/actuator/health` = UP (za basic auth), demo hra přes `DEMO-KATA` na telefonu přes mobilní data, přihlášení do `/admin` silným heslem, CSV s odkazy `https://test.storyhold.cz/?kod=…`, MP3 na iPhonu (Range přes Caddy)

### Později

- [ ] Zálohy: denní `pg_dump` → `age`/`gpg` → WEDOS Disk přes rsync, retence 14 denních + měsíční (až budou ostrá data)
- [ ] Produkce `storyhold.cz` na stejném serveru: vlastní stack bez seedu a bez basic auth, DNS `@` a `*` na VPS, absolutní `og:image`
- [ ] Rate limiting odpovědí (Q11) před ostrým provozem
- [ ] DMARC `p=quarantine` po ověření doručování
- [ ] Skutečné IP klientů přes IPv6: Docker bez IPv6 předává Caddy spojení z IPv6 s adresou brány, limity pokusů pak sdílí všichni hráči na IPv6 (zapnout IPv6 v Dockeru, nebo Caddy v síti hostitele)
- [ ] GitHub Container Registry a CI místo `docker save` / `scp`

## Další fáze

1. ~~Přístupové kódy~~ — hotovo (F1) včetně přístupu pro hrady
2. Skutečný obsah pro hrad Loket (průzkum na místě, příběh kata)
3. Administrace obsahu a statistiky obtížnosti
4. Ostrý provoz — testovací server hotov (F2), dál produkce, zálohy, rate limiting
5. Volitelně PWA/offline, GPS/QR, jazykové mutace
