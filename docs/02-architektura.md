# 02 — Architektura

## 1. Přehled

Klasická třívrstvá webová aplikace: React SPA (mobilní prohlížeč) → REST API ve Spring Bootu → PostgreSQL. Stejný stack v prototypu i v ostrém provozu.

```mermaid
%%{init: {'theme':'base','themeVariables':{'primaryColor':'#ffffff','primaryTextColor':'#000000','primaryBorderColor':'#000000','lineColor':'#000000','textColor':'#000000','edgeLabelBackground':'#ffffff'}}}%%
flowchart LR
    P[Mobilní prohlížeč<br/>React SPA] -->|HTTPS / JSON<br/>/api/v1| B[Spring Boot<br/>REST API]
    B -->|JPA / JDBC| D[(PostgreSQL)]
    B -.->|Flyway migrace<br/>při startu| D
```

### Klíčová rozhodnutí

| Rozhodnutí | Volba | Důvod |
|---|---|---|
| Typ klienta | SPA (React), později PWA | Bez instalace, jeden kód pro všechny telefony |
| Komunikace | REST + JSON, verze `/api/v1` | Jednoduché, dobře testovatelné |
| Stav hry | Na serveru (tabulka `game_session` + `session_progress`) | Odpovědi se ověřují na serveru, hra přežije zavření prohlížeče, data pro analytiku |
| Identita hráče | Anonymní token relace (UUID v4) v `localStorage` + hlavička `X-Session-Token` | Žádné účty, žádné osobní údaje |
| Obsah her | V DB, v prototypu nahraný Flyway seed migrací | Připraveno na budoucí administraci |
| Schéma DB | Flyway, `ddl-auto=validate` | Kontrolované změny schématu |
| Nasazení | Jeden Spring Boot JAR, který servíruje i build frontendu (`static/`) | Jedna jednotka nasazení, žádné CORS v produkci |

## 2. Backend

### Technologie

- Java 21 LTS (případně 25 LTS), aktuální stabilní Spring Boot 3.x/4.x, Maven Wrapper
- Starters: `web`, `data-jpa`, `validation`, `actuator`
- `flyway-core` + `flyway-database-postgresql`, `postgresql` driver
- Testy: `spring-boot-starter-test`, Testcontainers (`postgresql`, `junit-jupiter`)
- Lombok **nepoužívat** — DTO jsou `record`, entity s ručními gettery/settery.

### Balíčky (package-by-feature)

```
cz.mibcon.zazitky            # (název balíčku lze upravit)
├── ZazitkyApplication.java
├── common/                  # ProblemDetail handler, normalizace textu, konfigurace, Clock bean
├── game/                    # Partner, Location, Game, Station, Hint, AcceptedAnswer — čtení obsahu
│   ├── domain/              # JPA entity
│   ├── GameRepository.java ...
│   ├── GameService.java
│   └── GameController.java  # /api/v1/games
├── session/                 # GameSession, SessionProgress, AnswerAttempt — herní logika
│   ├── domain/
│   ├── SessionService.java
│   ├── AnswerEvaluator.java # normalizace + porovnání odpovědí
│   └── SessionController.java # /api/v1/sessions
└── accesscode/              # AccessCodeBatch, AccessCode — v prototypu jen entity + repo
```

### Vrstvy uvnitř feature

`Controller` (HTTP, validace vstupu, mapování na DTO) → `Service` (transakce, herní pravidla) → `Repository` (Spring Data JPA). Controllery neobsahují logiku, entity neopouštějí service vrstvu.

### Herní logika (`SessionService`)

1. **start(gameId, teamName)** — ověří, že hra je `PUBLISHED`; vytvoří `game_session` se stavem `IN_PROGRESS`, `token = UUID.randomUUID()`, `expires_at = now + session_validity_hours`; vytvoří `session_progress` pro první stanoviště se stavem `ACTIVE`.
2. **getState(token)** — vrátí aktuální stav; pokud `expires_at < now`, nastaví `EXPIRED`.
3. **submitAnswer(token, stationId, answer)** — v transakci:
   - stanoviště musí být aktuálně `ACTIVE` v dané relaci (jinak 409);
   - zapíše `answer_attempt` (vždy, i při chybě);
   - `AnswerEvaluator` normalizuje vstup a porovná s `accepted_answer.normalized_value`;
   - při úspěchu: progress → `SOLVED`, `solved_at`; další stanoviště → `ACTIVE`; pokud žádné není, relace → `COMPLETED`, `finished_at`.
4. **revealHint(token, stationId)** — zvýší `hints_used` (max. počet nápověd stanoviště) a připočte penalizaci do `penalty_seconds` relace; vrátí odkryté nápovědy.

Souběh: dvojité odeslání z jednoho telefonu ošetřit optimistickým zamykáním (`@Version` na `game_session`).

### Normalizace odpovědí (`TextNormalizer` v `common`)

```
trim → toLowerCase(Locale.ROOT) → Normalizer.Form.NFD + odstranit \p{M} (diakritika)
     → odstranit interpunkci [^\p{L}\p{N} ] → sloučit mezery
NUMBER: navíc odstranit vše kromě číslic
```

Normalizovaná hodnota se ukládá do `accepted_answer.normalized_value`. **Rozhodnuto v M2:** v seedu se vyplňuje ručně a integrační test `DemoGameSeedTest` hlídá, že odpovídá výstupu `TextNormalizer`. Budoucí administrace ji bude dopočítávat při zápisu přes service. Pro typ `NUMBER` se používá `normalizeNumber`, pro `TEXT` a `CHOICE` `normalize`.

### Chyby

`@RestControllerAdvice` vrací `ProblemDetail` (RFC 9457):

| Situace | HTTP | `type` (suffix) |
|---|---|---|
| Hra/relace/stanoviště nenalezeno | 404 | `not-found` |
| Neplatný vstup (validace) | 400 | `validation` |
| Odpověď na neaktivní stanoviště, hra není rozehraná | 409 | `invalid-state` |
| Relace vypršela | 410 | `session-expired` |
| Chybí/nesedí token relace | 401 | `invalid-token` |

`type` je relativní URI `/problems/<suffix>` (např. `/problems/not-found`), `title` a `detail` jsou česky. Validační chyby těla požadavku obsahují navíc pole `errors: [{ "field", "message" }]`. Neznámá hra nebo nepublikovaná hra vrací 404.

### Konfigurace a profily

- `application.yml` — společné; `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`
- `application-dev.yml` — lokální DB z docker-compose, Flyway `locations: classpath:db/migration,classpath:db/seed`
- `application-prod.yml` — jen `db/migration`, připojení z proměnných prostředí (`DB_URL`, `DB_USER`, `DB_PASSWORD`)
- Hesla a tajemství nikdy v repozitáři.

## 3. REST API (v1)

Base path: `/api/v1`. Všechny odpovědi JSON. Relace se identifikuje hlavičkou `X-Session-Token: <uuid>`.

### Hry

| Metoda | Cesta | Popis |
|---|---|---|
| GET | `/games` | Seznam publikovaných her (demo výběr) |
| GET | `/games/{gameId}` | Detail hry pro úvodní obrazovku (bez stanovišť) |

```json
// GET /games
[ { "id": "4b0c…", "title": "Prokletí loketského kata", "subtitle": "Demo hra", "estimatedMinutes": 45 } ]
```

```json
// GET /games/{gameId}
{
  "id": "4b0c…",
  "title": "Prokletí loketského kata",
  "subtitle": "Demo hra",
  "introText": "Píše se rok 1625…",
  "estimatedMinutes": 45,
  "stationCount": 5
}
```

### Relace

| Metoda | Cesta | Popis |
|---|---|---|
| POST | `/sessions` | Zahájit hru. Body: `{ "gameId": "…", "teamName": "Kuchtovi" }` → `201` + stav relace včetně `token` |
| GET | `/sessions/current` | Aktuální stav relace (pro obnovu i po každém kroku) |
| POST | `/sessions/current/stations/{stationId}/answers` | Odeslat odpověď. Body: `{ "answer": "1625" }` |
| POST | `/sessions/current/stations/{stationId}/hints` | Odkrýt další nápovědu → odkryté nápovědy a nová penalizace |
| POST | `/sessions/current/abandon` | Opustit hru → `204` (opakované volání je bez efektu) |

```json
// GET /sessions/current
{
  "token": "e3f1…",            // jen v odpovědi na POST /sessions
  "status": "IN_PROGRESS",
  "game": { "id": "4b0c…", "title": "Prokletí loketského kata" },
  "teamName": "Kuchtovi",
  "startedAt": "2026-10-28T10:15:00Z",
  "penaltySeconds": 60,
  "progress": { "solved": 1, "total": 5 },
  "currentStation": {
    "id": "a1…",
    "orderNo": 2,
    "title": "Katova studna",
    "storyText": "…",
    "taskText": "Kolik kroků vede…?",
    "taskType": "NUMBER",
    "choices": null,
    "hintsAvailable": 1,       // počet ještě neodkrytých nápověd
    "nextHintPenaltySeconds": 120, // penalizace za další nápovědu, null když už žádná není
    "revealedHints": [ { "orderNo": 1, "text": "…", "penaltySeconds": 60 } ]
  },
  "solvedStations": [ { "id": "…", "orderNo": 1, "title": "…", "rewardText": "…" } ],
  "finale": null              // po dokončení: { "outroText": "…", "totalSeconds": 2710, "hintsUsed": 3 }
}
```

```json
// POST …/answers  → 200
{ "correct": false, "message": "To není ono. Zkus to znovu." }   // u NUMBER bez číslic: "Odpověď musí obsahovat číslo."
{ "correct": true, "rewardText": "…", "nextStationId": "b2…", "gameCompleted": false }
// poslední stanoviště: nextStationId chybí, gameCompleted = true
```

```json
// POST …/hints  → 200
{ "revealedHints": [ { "orderNo": 1, "text": "…", "penaltySeconds": 60 } ],
  "hintsAvailable": 1, "nextHintPenaltySeconds": 120, "penaltySeconds": 60 }
```

**Pravidla a stavové kódy relací** (kontroly v tomto pořadí):

1. Chybí, je poškozený nebo neznámý `X-Session-Token` → `401 invalid-token`.
2. Relace `IN_PROGRESS` s `expires_at <= now` se při jakémkoli přístupu přepne na `EXPIRED` (změna se uloží) → `410 session-expired`; to platí i pro `GET /sessions/current`.
3. Odpověď / nápověda, když relace není `IN_PROGRESS` (`COMPLETED`, `ABANDONED`) → `409 invalid-state`. `GET /sessions/current` vrací u `COMPLETED` a `ABANDONED` normálně stav (`200`).
4. Stanoviště, které do hry nepatří → `404 not-found`; stanoviště, které ještě není odemčené nebo už je vyřešené → `409 invalid-state`.
5. Další nápověda, když už žádná nezbývá → `409 invalid-state`.
6. Odmítnutý požadavek (body 1–5) se nezapisuje do `answer_attempt`; každý vyhodnocený pokus ano.
7. Každá zápisová operace zvyšuje `game_session.version` (`OPTIMISTIC_FORCE_INCREMENT`), takže ze dvou souběžných požadavků na stejnou relaci uspěje jen jeden, druhý dostane `409 invalid-state`.

`choices` u typu `CHOICE` je seznam textů k výběru (které z nich jsou správné, klient nezjistí).

## 4. Frontend

### Technologie

React + TypeScript (strict), Vite, React Router, TanStack Query (stav serveru), Tailwind CSS. Žádný Redux — stav hry žije na serveru, lokálně jen token.

### Struktura

```
frontend/src/
├── api/            # fetch klient (+ X-Session-Token), typy DTO, query hooks
├── pages/          # HomePage, GameIntroPage, StationPage, RewardPage, FinalePage
├── components/     # ProgressBar, AnswerInput (TEXT/NUMBER/CHOICE), HintList, StoryText, ErrorBanner
├── session/        # useSessionToken (localStorage s try/catch)
├── App.tsx         # routy
└── main.tsx
```

### Routy

| Cesta | Stránka |
|---|---|
| `/` | Úvod — seznam her, „Pokračovat ve hře“ pokud je token |
| `/games/:gameId` | Úvod hry |
| `/play` | Aktuální stanoviště (podle `GET /sessions/current`) |
| `/play/reward` | Odměna po vyřešení |
| `/play/finale` | Finále |

Navigace ve hře je řízená stavem ze serveru — klient nikdy sám nerozhoduje, které stanoviště je odemčené.

### Chování klienta (M4)

- Token relace je v `localStorage` pod klíčem `zazitky.sessionToken`. Když úložiště není dostupné (anonymní okno), hra funguje do obnovení stránky.
- Stránky `/play*` načítají `GET /sessions/current`. Bez tokenu nebo při `401`/`410` klient token smaže a vrátí hráče na úvod se zprávou ze serveru.
- Po správné odpovědi klient přejde na `/play/reward` s textem odměny; stav relace se obnoví až tam (jinak by stránka stanoviště přesměrovala dřív, než se odměna zobrazí). Po reloadu odměnové stránky se zobrazí odměna posledního vyřešeného stanoviště.
- `NUMBER` používá textové pole s `inputMode="numeric"` (numerická klávesnice, ale odpověď typu „56 schodů“ projde). `CHOICE` jsou velká tlačítka, klepnutí rovnou odešle odpověď.
- Nápověda stojí čas, proto se odkrývá až po potvrzení, které ukazuje výši penalizace (`nextHintPenaltySeconds`).
- Zahájení nové hry při existujícím tokenu nejdřív zavolá `abandon` na starou relaci.

### Vývoj

Vite dev server na `:5173` s proxy `/api → http://localhost:8080`. V produkci se `npm run build` výstup kopíruje do `backend/src/main/resources/static` (Maven `frontend-maven-plugin` nebo krok v CI — rozhodnout v M5) a SPA fallback (`/play/**` → `index.html`) zajistí jednoduchý controller nebo `WebMvcConfigurer`.

## 5. Bezpečnost

- Správné odpovědi a nápovědy, které hráč ještě neodkryl, nikdy neopouštějí server.
- Token relace = UUID v4 (122 bitů náhody), jen v hlavičce, ne v URL.
- Rate limiting odpovědí (ochrana proti hrubé síle u `NUMBER`) — v prototypu jen záznam pokusů, v ostrém provozu limit např. 10 pokusů/min na relaci.
- Přístupové kódy (budoucí): generovat `SecureRandom`, abeceda bez zaměnitelných znaků (bez 0/O, 1/I/L), ukládat v plném znění kvůli tisku, ověřovat s rate limitem na IP.
- Admin rozhraní (budoucí): Spring Security, oddělené `/api/v1/admin/**`.
- HTTPS v ostrém provozu povinné.

## 6. Nasazení (výhled)

- Build: `./mvnw verify` → spustitelný JAR s vloženým frontendem; případně Docker image (`Dockerfile` multi-stage).
- Provoz: Docker Compose (app + PostgreSQL) na VPS za reverzní proxy (Caddy/Nginx s Let's Encrypt), nebo spravovaná PostgreSQL v cloudu. Varianta SAP BTP Cloud Foundry zůstává otevřená (viz Q1 v analýze).
- Monitoring: Actuator `/actuator/health` pro proxy/orchestraci, logy na stdout.
- Zálohy DB: denní `pg_dump`.

## 7. Testování

| Úroveň | Co | Nástroj |
|---|---|---|
| Unit | `TextNormalizer`, `AnswerEvaluator`, výpočet času a penalizací | JUnit 5 |
| Integrace | Celý průchod demo hrou přes REST (start → odpovědi → nápovědy → finále), chybové stavy | `@SpringBootTest` + MockMvc + Testcontainers PostgreSQL |
| Migrace | Flyway proběhne na čisté DB, `ddl-auto=validate` projde | Testcontainers |
| Frontend | `AnswerInput`, `HintList`, routování podle stavu | Vitest + Testing Library |
