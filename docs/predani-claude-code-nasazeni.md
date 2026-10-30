# Předání: Storyhold — testovací server a nasazení (F2)

Tento dokument shrnuje, co se mezitím udělalo mimo repozitář (značka, doména, e-mail, server), a zadává další práci: dostat aplikaci na testovací server `test.storyhold.cz`. Před prací si přečti `CLAUDE.md` a `docs/02-architektura.md`.

## 1. Co je hotové mimo repozitář

### Značka a doména
- **Značka: Storyhold** (story + stronghold). Rešerše konkurence a TMview (EU/ČR) bez kolize. „Hradní tajemství“ / „Zážitkový Loket“ zůstávají pracovními názvy, Loket je první lokalita. **Přejmenování v UI ani v kódu teď nedělej**, pokud o to výslovně nepožádám.
- Doména **`storyhold.cz`** registrovaná u WEDOS (VEDOS), DNS spravuje WEDOS.
- E-mail **`info@storyhold.cz`** běží zdarma na Seznam Email Profi.

### DNS (`storyhold.cz`)
| Název | Typ | Hodnota |
|---|---|---|
| `test` | A / AAAA | `31.31.75.96` / `2a02:2b88:2:1::7dd8:1` |
| `@` a `*` | A / AAAA | zatím parkovací stránka WEDOS (při spuštění produkce přesměrovat na VPS) |
| `@` | MX | Seznam Email Profi (`…mx2.emailprofi.seznam.cz` prio 10, `…mx1…` prio 20) |
| `@` | TXT | `v=spf1 include:spf.seznam.cz ~all` |
| `szn1._domainkey`, `szn2…`, `szn3…` | CNAME | `szn1._domainkey.seznam.cz` atd. (DKIM) |
| `_dmarc` | TXT | `v=DMARC1; p=none` |

### Server (VPS)
- WEDOS VPS SSD **2 moduly: 4 GB RAM, 1 vlákno CPU, 30 GB SSD**, roční platba, **Ubuntu 24.04 LTS**.
- IP `31.31.75.96`, IPv6 `2a02:2b88:2:1::7dd8:1`.
- Jeden server pro **staging** (`test.storyhold.cz`, teď) i budoucí **produkci** (`storyhold.cz`, později).
- Přístup: uživatel **`michal`** se `sudo`, **jen SSH klíčem**. Přihlášení rootem i heslem je zakázané (`/etc/ssh/sshd_config.d/00-storyhold.conf`). Nouzový přístup přes KVM/VNC z administrace WEDOS.
- Na mém počítači (Windows, PowerShell 5.1) je v `~/.ssh/config` alias **`ssh storyhold`** (klíč `~/.ssh/storyhold_ed25519`). Firemní klíč `id_ed25519` na GitHub **neměnit**.
- Firewall `ufw`: povoleno jen 22, 80, 443.
- Swap 2 GB, automatické bezpečnostní aktualizace, **Docker Engine + Compose plugin** (oficiální repozitář, Compose v5), rotace logů Dockeru v `/etc/docker/daemon.json` (10 MB × 3).
- Na serveru existuje složka **`~/storyhold`** s `compose.yaml` (jen služba `caddy`, porty 80/443, volumes `caddy_data`, `caddy_config`) a `Caddyfile`:
  - `test.storyhold.cz` s automatickým HTTPS (Let's Encrypt funguje),
  - `basic_auth` s uživatelem `tester` (hash hesla je jen na serveru),
  - hlavička `X-Robots-Tag: noindex, nofollow`,
  - zatím jen zástupná stránka `respond "Storyhold – testovací server běží."`.

### Rozpočet (pro kontext)
VPS ~3 195 Kč/rok (se slevou), doména 194 Kč/rok, e-mail zdarma. Volitelně později WEDOS Disk 30 GB na zálohy (~726 Kč/rok).

## 2. Pravidla pro nasazení

1. **Image se nebuildují na serveru** (1 vlákno CPU). Build na mém počítači (Docker Desktop) nebo později v CI.
2. **Porty zveřejňuje jen Caddy.** Docker obchází `ufw`, takže `app` a `db` nesmí mít `ports:`, jen interní síť Compose.
3. **Tajemství nikdy do repozitáře**: `DB_PASSWORD`, `ADMIN_PASSWORD`, hash pro basic auth patří do `.env` na serveru (práva `600`). Do repozitáře jen `.env.example` s prázdnými hodnotami. Hesla nevypisuj do výstupu.
4. **V Linuxu jsem začátečník.** Každý krok na serveru mi dej jako konkrétní příkazy ke zkopírování a krátce vysvětli, co dělají. Příkazy pro můj počítač piš pro **Windows PowerShell 5.1**. Pozor: binární data nepipuj přes PowerShell (`docker save | ssh …` by soubor poškodil), použij soubor a `scp`.
5. Změny v repozitáři commitni po logických celcích (commit messages anglicky, dokumentace česky dle `CLAUDE.md`).

## 3. Úkoly (fáze F2)

### 3.1 Dokumentace
Aktualizované verze `docs/01-analyza.md`, `docs/02-architektura.md` a `docs/05-plan-prototypu.md` mám v Claude projektu. Pokud ti je předám, nahraď jimi soubory v repozitáři. Jinak do nich zapiš:
- **Q1 (hosting)**: rozhodnuto, viz kap. 1 tohoto dokumentu.
- **Q15 (značka)**: Storyhold, rozhodnuto (Q8 je obsazená normalizací odpovědí).
- **`02-architektura.md`, kap. 6**: skutečný provoz (prostředí dev / staging / prod, server, DNS, e-mail, zálohy, pravidlo „porty jen Caddy“), diagram Mermaid s černým textem.
- **`05-plan-prototypu.md`**: nová fáze **F2 — Testovací server a nasazení**. Hotové body jsou doména, e-mail, VPS, zabezpečení, Docker, DNS a Caddy se zástupnou stránkou. Otevřené body jsou úkoly níže.

### 3.2 Konfigurace pro staging
- Staging = profil **`prod`** + seed demo hry přes `SPRING_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed` (stejně jako `docker compose --profile app` lokálně). Pokud je čistší samostatný profil `staging`, navrhni ho.
- Proměnné: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `ADMIN_USERNAME`, `ADMIN_PASSWORD` (silné), `PUBLIC_URL=https://test.storyhold.cz`, `FORWARD_HEADERS_STRATEGY=native` (aplikace je za Caddy, kvůli limitu pokusů podle skutečné IP), `SESSION_COOKIE_SECURE=true`.
- Upozorni mě, co dalšího seed na stagingu vytvoří (vývojový kód `DEMO-KATA`, vývojový účet hradu `hrad` / `hrad-loket`). Za basic auth je to přijatelné, ale chci o tom vědět. Případně navrhni, jak na stagingu nastavit jiné heslo.
- Paměť: JVM může mít víc než v původním plánu (4 GB RAM), ale nastav rozumný strop (např. `-XX:MaxRAMPercentage=50`), ať zbyde na PostgreSQL a případnou produkci vedle.

### 3.3 Soubory pro server v repozitáři
Navrhuji složku `deploy/staging/`:
- `compose.yaml`: služby `caddy` (jediná s `ports: 80, 443`), `app` (image aplikace, `env_file: .env`, healthcheck `/actuator/health`, `depends_on` db), `db` (`postgres:16`, pojmenovaný volume, bez portů), `restart: unless-stopped`.
- `Caddyfile`: `test.storyhold.cz` → `reverse_proxy app:8080`, `basic_auth` (uživatel a hash z proměnných prostředí, např. `{$BASIC_AUTH_USER} {$BASIC_AUTH_HASH}`), `X-Robots-Tag: noindex, nofollow`. Pozor na znaky `$` v bcrypt hashi při interpolaci v Compose (`$$` nebo vhodné uvozovky). Ověř, že to funguje.
- `.env.example`: všechny proměnné bez hodnot, s komentáři.
- Na serveru nahradí stávající `~/storyhold/compose.yaml` a `Caddyfile`. **Zachovej volume `caddy_data`** (certifikáty), ať se zbytečně nežádá Let's Encrypt znovu.

### 3.4 Dostání image na server
Na začátek jednoduše, bez registru:
1. Na mém PC: `docker build -t storyhold-app:staging .` → `docker save -o storyhold-app.tar storyhold-app:staging`
2. `scp storyhold-app.tar storyhold:~/storyhold/`
3. Na serveru: `docker load -i ~/storyhold/storyhold-app.tar` → `docker compose up -d` → smazat tar.

Připrav na to skript **`deploy/deploy-staging.ps1`** (build, save, scp, vzdálené `docker load` a `docker compose up -d` přes `ssh storyhold "…"`, úklid, kontrola health), plus kopírování `compose.yaml` a `Caddyfile` na server. Později lze přejít na GitHub Container Registry a CI. Pokud usoudíš, že je lepší rovnou GHCR, zdůvodni to.

### 3.5 První nasazení a ověření
- Proveď mě prvním nasazením krok za krokem (vytvoření `.env` na serveru, nahrání souborů, spuštění).
- Ověřit: `https://test.storyhold.cz/actuator/health` = UP (za basic auth), demo hra přes `DEMO-KATA` na telefonu přes mobilní data, přihlášení do `/admin` silným heslem, CSV odkazy s `https://test.storyhold.cz/?kod=…`, přehrávání MP3 na iPhonu (Range požadavky přes Caddy).

### 3.6 README
Doplň do `README.md` sekci **„Nasazení na testovací server“**: předpoklady (SSH alias, Docker Desktop), první nasazení, aktualizace verze, logy (`docker compose logs -f app`), restart, kde je `.env`.

### 3.7 Později (zatím jen zapsat do plánu)
- Zálohy: denní `pg_dump` → šifrování (`age`/`gpg`) → WEDOS Disk přes rsync, retence 14 denních + měsíční. Až budou na serveru ostrá data.
- Produkce `storyhold.cz` na stejném serveru (vlastní compose/stack bez seedu a bez basic auth), přesměrování `@` a `*` v DNS.
- Rate limiting odpovědí (Q11) před ostrým provozem.
- `p=quarantine` v DMARC po ověření, že e-maily procházejí.
