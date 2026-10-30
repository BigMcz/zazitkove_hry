# StoryHold

Webová aplikace pro příběhové venkovní hry na hradech a zámcích. Aktuálně ve fázi **prototypu** — viz `docs/`.

## Požadavky

- Java 21 (Maven není potřeba, projekt obsahuje Maven Wrapper)
- Node.js 22+ a npm
- Docker Desktop (PostgreSQL pro vývoj a Testcontainers pro testy)

## Spuštění pro vývoj

```bash
# 1) Databáze (PostgreSQL 16, port 5432, DB/uživatel/heslo: zazitky — jen pro lokální vývoj)
docker compose up -d

# 2) Backend na http://localhost:8080 (profil dev = lokální DB + demo data)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# kontrola: http://localhost:8080/actuator/health → {"status":"UP"}

# 3) Frontend na http://localhost:5173 (proxy /api → :8080)
cd frontend
npm install
npm run dev
```

Na Windows lze místo `./mvnw` použít `mvnw.cmd`. Demo hru otevřeš na http://localhost:5173 zadáním vývojového kódu **`DEMO-KATA`** (nevyprší, každé zadání založí novou hru). Administrace (generování kódů, CSV pro tisk, měsíční přehled aktivací) je na http://localhost:5173/admin — v profilu `dev` správce `admin` / `admin`, vývojový účet hradu `hrad` / `hrad-loket` (vidí jen přehled svého hradu).

## Build a testy

```bash
cd backend && ./mvnw verify                 # build + testy (vyžaduje běžící Docker kvůli Testcontainers)
cd frontend && npm run build && npm test
```

## Produkční build (jeden JAR s frontendem)

```bash
cd frontend && npm ci && npm run build      # → frontend/dist (úvodní stránka předgenerovaná do index.html)
cd backend && ./mvnw package                # pom.xml přibalí frontend/dist do static/
java -jar target/zazitky-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev   # aplikace i API na :8080
```

Bez `frontend/dist` vznikne JAR jen s backendem. V profilu `prod` se nastavuje přes proměnné prostředí: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `ADMIN_PASSWORD` (bez něj je administrace vypnutá), `PUBLIC_URL` (adresa do odkazů v CSV s kódy) a za reverzní proxy `FORWARD_HEADERS_STRATEGY=native` — popis v `docs/02-architektura.md`.

## Docker

```bash
docker build -t zazitky .                       # multi-stage: Node → Maven → JRE (testy se v image nespouští)
docker compose --profile app up -d --build      # aplikace na :8080 proti PostgreSQL z compose, včetně demo hry
docker compose --profile app stop app
```

## Nasazení na testovací server

Testovací server **https://test.storyhold.cz** (celý za basic auth) běží na VPS ve složce `~/storyhold`: Caddy (HTTPS), aplikace a PostgreSQL z `deploy/staging/compose.yaml`. Popis prostředí je v `docs/02-architektura.md`, kap. 6.

**Předpoklady:** Docker Desktop, SSH alias `storyhold` v `~/.ssh/config` a klíč načtený v `ssh-agent` (skript se připojuje víckrát a na heslo ke klíči se ptát nesmí). Jednorázově ve Windows:

```powershell
# PowerShell jako správce: zapne službu ssh-agent (i po restartu)
Get-Service ssh-agent | Set-Service -StartupType Automatic
Start-Service ssh-agent
# běžný PowerShell: načte klíč (heslo se zadá jednou, agent si klíč pamatuje i po restartu)
ssh-add $HOME\.ssh\storyhold_ed25519
```

### První nasazení

1. Na serveru (`ssh storyhold`) vytvoř `.env` s vygenerovanými hesly a s hashem basic auth z dosavadního `Caddyfile` (musí proběhnout před jeho přepsáním):
   ```bash
   cd ~/storyhold
   umask 077
   printf 'DB_PASSWORD=%s\nADMIN_USERNAME=admin\nADMIN_PASSWORD=%s\nBASIC_AUTH_USER=tester\n' "$(openssl rand -hex 24)" "$(openssl rand -hex 16)" > .env
   printf "BASIC_AUTH_HASH='%s'\n" "$(awk '$1=="tester" {print $2}' Caddyfile)" >> .env
   grep -c . .env                        # má vypsat 5 (řádků)
   grep BASIC_AUTH_HASH .env | cut -c1-24  # začátek hashe: BASIC_AUTH_HASH='$2a$…
   grep ADMIN_PASSWORD .env              # heslo do /admin, ulož si ho do správce hesel
   exit
   ```
   Vzor se všemi proměnnými je `deploy/staging/.env.example`. Pak ještě přidej účet pro prezentace (další bod).
2. Účet basic auth pro prezentace (`ukazka`, posílá se lidem mimo tým): na serveru v `~/storyhold`
   ```bash
   docker run --rm -it caddy:2 caddy hash-password   # zadej heslo 2×, vypíše hash $2a$14$…
   printf "PRESENTATION_AUTH_USER=ukazka\nPRESENTATION_AUTH_HASH='%s'\n" 'SEM_VLOŽ_HASH' >> .env
   ```
   Změna hesla později: v `.env` nahraď hash (`nano .env`) a spusť `docker compose up -d caddy`.
3. Na svém počítači v kořeni repozitáře (PowerShell):
   ```powershell
   .\deploy\deploy-staging.ps1
   ```
   Skript postaví image, nahraje ho s `compose.yaml` a `Caddyfile` na server, spustí `docker compose up -d`, načte konfiguraci Caddy a počká, až bude aplikace zdravá. Pokud PowerShell skript odmítne spustit, povol skripty pro svého uživatele: `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`.

### Aktualizace verze

Commitni změny a spusť znovu `.\deploy\deploy-staging.ps1`. Databáze (volume `pgdata`) zůstává, Flyway při startu doplní nové migrace. Pozor: změna `R__demo_game.sql` smaže rozehrané hry demo hry a dávky kódů k ní.

### Na serveru (`ssh storyhold`, pak `cd ~/storyhold`)

```bash
docker compose ps                    # stav služeb (app má být healthy)
docker compose logs -f app           # logy aplikace (Ctrl+C ukončí sledování)
docker compose logs -f caddy         # logy Caddy (certifikáty, přístupy)
docker compose restart app           # restart aplikace
nano .env && docker compose up -d    # změna tajemství (DB_PASSWORD platí jen při prvním založení DB)
```

## Hraní na telefonu v lokální síti

Telefon musí být ve stejné Wi-Fi jako počítač.

1. Spusť aplikaci jedním ze způsobů výše — buď produkční JAR / Docker (port **8080**), nebo vývojově backend + `npm run dev` (port **5173**, Vite poslouchá i v síti).
2. Zjisti IP počítače: Windows `ipconfig` (řádek *IPv4 Address* u Wi-Fi adaptéru), macOS/Linux `ipconfig getifaddr en0` / `hostname -I`.
3. Na telefonu otevři `http://<IP-počítače>:8080` (případně `:5173`).
4. Pokud se stránka nenačte, povol port ve firewallu. Windows (PowerShell jako správce):
   ```powershell
   New-NetFirewallRule -DisplayName "Zazitky dev" -Direction Inbound -Protocol TCP -LocalPort 8080,5173 -Action Allow -Profile Private
   ```
   Wi-Fi síť musí být v systému nastavená jako *soukromá*. Pravidlo jde později odebrat: `Remove-NetFirewallRule -DisplayName "Zazitky dev"`.

## Profily backendu

| Profil | Použití | Flyway | Připojení k DB |
|---|---|---|---|
| `dev` | lokální vývoj | `db/migration` + `db/seed` | `localhost:5432/zazitky` z `docker-compose.yml` |
| `prod` | ostrý provoz | jen `db/migration` | proměnné prostředí `DB_URL`, `DB_USER`, `DB_PASSWORD` |

## Struktura

```
├── docs/                 # analýza, architektura, datový model, demo hra, plán
├── docker-compose.yml    # PostgreSQL pro lokální vývoj (+ volitelně celá aplikace, profil app)
├── Dockerfile            # image celé aplikace
├── backend/              # Spring Boot 4 (Java 21, Maven)
└── frontend/             # React + TypeScript + Vite, Tailwind, React Router, TanStack Query
```
