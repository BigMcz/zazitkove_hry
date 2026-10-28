# Hradní tajemství — instrukce pro Claude Code

## O projektu

Webová aplikace pro příběhové venkovní hry (digitální gamebook / outdoorová únikovka) na hradech, zámcích a v jejich okolí. Návštěvník si na pokladně hradu koupí přístupový kód, na mobilu ho zadá a prochází stanoviště s příběhem a úkoly. První lokalitou bude hrad Loket, později další hrady a zámky.

**Aktuální fáze: prototyp.** Hra v prototypu je vymyšlená a neodpovídá realitě na hradě. Cílem je ověřit herní smyčku, architekturu a datový model, na kterých se pak postaví ostrý provoz.

## Dokumentace (čti před implementací)

| Soubor | Obsah |
|---|---|
| `docs/01-analyza.md` | Koncept, aktéři, případy užití, rozsah prototypu, herní flow |
| `docs/02-architektura.md` | Stack, komponenty, struktura repozitáře, REST API, bezpečnost, nasazení |
| `docs/03-datovy-model.md` | ER diagram, DDL, popis tabulek a stavů |
| `docs/04-prototypova-hra.md` | Obsah vymyšlené demo hry (příběh, stanoviště, odpovědi, nápovědy) |
| `docs/05-plan-prototypu.md` | Milníky a pořadí implementace, checklist |

## Rozsah prototypu

**Ano:** jedna demo hra (lineární sled stanovišť), spuštění hry bez kódu (demo režim), zobrazení příběhu a úkolu, ověření odpovědi na serveru, nápovědy s časovou penalizací, odměna po vyřešení, finále se souhrnem, obnovení rozehrané hry po zavření prohlížeče.

**Ne (zatím):** prodej a ověřování přístupových kódů (tabulky v DB ale existují), administrace obsahu (obsah se nahrává seed migrací), GPS/QR, offline režim, platby, vícejazyčnost, uživatelské účty.

## Technologie

- **Backend:** Java 21 LTS (nebo 25 LTS), aktuální stabilní Spring Boot, Maven, Spring Web, Spring Data JPA, Bean Validation, Flyway, Actuator, PostgreSQL driver
- **Databáze:** PostgreSQL (lokálně v Dockeru přes `docker-compose.yml`)
- **Frontend:** React + TypeScript + Vite, React Router, TanStack Query, Tailwind CSS, mobile-first
- **Testy:** JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL); frontend Vitest + Testing Library
- Stejný stack se použije v ostrém provozu — žádné prototypové zkratky typu H2 nebo in-memory úložiště.

## Struktura repozitáře

```
zazitkovy-loket/
├── CLAUDE.md
├── docs/
├── docker-compose.yml        # PostgreSQL pro lokální vývoj
├── backend/                  # Spring Boot (Maven)
│   └── src/main/resources/
│       ├── db/migration/     # schéma (Flyway, všechna prostředí)
│       └── db/seed/          # demo data (Flyway, jen profil dev)
└── frontend/                 # React + Vite
```

## Příkazy

```bash
docker compose up -d                       # spustit PostgreSQL
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd backend && ./mvnw verify                # build + testy
cd frontend && npm install && npm run dev  # Vite dev server, /api proxy na :8080
cd frontend && npm run build && npm test
docker compose --profile app up -d --build # celá aplikace z Dockerfile na :8080
```

## Konvence

- Kód, identifikátory, názvy tabulek a sloupců, commit messages: **anglicky**.
- Texty v UI, herní obsah a dokumentace: **česky**.
- Backend organizovat **package-by-feature** (`game`, `session`, `accesscode`, `common`), ne po vrstvách.
- Entity se nikdy neposílají ven — vždy DTO (Java `record`).
- Chyby vracet jako RFC 9457 `ProblemDetail` přes `@RestControllerAdvice`.
- Změny schématu **pouze novou Flyway migrací**; již aplikovanou migraci nikdy neupravovat.
- Enumy ukládat jako `VARCHAR` + `CHECK` constraint, v JPA `@Enumerated(EnumType.STRING)`.
- Časy ukládat jako `TIMESTAMPTZ`, v Javě `Instant`.
- **Správné odpovědi se nikdy neposílají na klienta.** Vyhodnocení odpovědi probíhá výhradně na serveru.
- Diagramy (Mermaid) vždy s **černým textem**. Po každé změně tabulek aktualizovat ER diagram i DDL v `docs/03-datovy-model.md`.

## Jak pracovat

1. Před větší částí práce si přečti příslušný dokument v `docs/`.
2. Postupuj podle milníků v `docs/05-plan-prototypu.md`; dokončené body odškrtni.
3. Před ohlášením hotové práce ověř build a testy (`./mvnw verify`, `npm run build`, `npm test`).
4. Když narazíš na rozpor mezi dokumenty nebo nejasné zadání, zeptej se — nedomýšlej si zásadní rozhodnutí.
5. Otevřené otázky a rozhodnutí zapisuj do sekce „Otevřené otázky“ v `docs/01-analyza.md`.
