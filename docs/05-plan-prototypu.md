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

- [ ] Produkční build: frontend se zabalí do JAR (`static/`), SPA fallback pro klientské routy
- [ ] `Dockerfile` (multi-stage) pro celou aplikaci
- [ ] Ruční průchod demo hrou na skutečném telefonu v lokální síti
- [ ] Aktualizovat dokumentaci podle skutečného stavu (API, datový model, otevřené otázky)

## Definice hotového prototypu

1. `docker compose up -d` + spuštění backendu a frontendu podle README funguje na čistém počítači.
2. Demo hru lze na telefonu projít od začátku do konce včetně nápověd a špatných odpovědí.
3. Po zavření a znovuotevření prohlížeče hra pokračuje.
4. `./mvnw verify`, `npm run build` a `npm test` procházejí.
5. V DB existují tabulky pro partnery, dávky a přístupové kódy, připravené pro další fázi.

## Další fáze (po prototypu)

1. Přístupové kódy — generování dávek, zadání kódu místo demo výběru, platnost
2. Skutečný obsah pro hrad Loket (průzkum na místě, příběh kata)
3. Administrace obsahu a statistiky obtížnosti
4. Ostrý provoz — hosting, HTTPS, zálohy, rate limiting
5. Volitelně PWA/offline, GPS/QR, jazykové mutace
