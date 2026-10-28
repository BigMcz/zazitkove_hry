# Zážitkový Loket

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

Na Windows lze místo `./mvnw` použít `mvnw.cmd`. Dev server Vite poslouchá i v lokální síti (`host: true`), takže jde otevřít z telefonu přes IP počítače.

## Build a testy

```bash
cd backend && ./mvnw verify                 # build + testy (vyžaduje běžící Docker kvůli Testcontainers)
cd frontend && npm run build && npm test
```

## Profily backendu

| Profil | Použití | Flyway | Připojení k DB |
|---|---|---|---|
| `dev` | lokální vývoj | `db/migration` + `db/seed` | `localhost:5432/zazitky` z `docker-compose.yml` |
| `prod` | ostrý provoz | jen `db/migration` | proměnné prostředí `DB_URL`, `DB_USER`, `DB_PASSWORD` |

## Struktura

```
├── docs/                 # analýza, architektura, datový model, demo hra, plán
├── docker-compose.yml    # PostgreSQL pro lokální vývoj
├── backend/              # Spring Boot 4 (Java 21, Maven)
└── frontend/             # React + TypeScript + Vite, Tailwind, React Router, TanStack Query
```
