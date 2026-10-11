# Flat Meal Manager

Replaces the flat's monthly meal and expense spreadsheet: daily meals, the meal rate, everyone's dues, payments and flat cash, with full history.

The stack is a React (Vite) frontend, a Spring Boot 3 REST API and MySQL 8.

## Project layout

```
flat-meal-manager/
├── backend/            Spring Boot (Java 21, Maven); also serves the built website
├── frontend/           React (Vite)
├── docs/
│   ├── DEPLOY.md       putting it online (Oracle / Render + Aiven / own PC), backups, restore
│   ├── FIRST-MONTH.md  moving from the spreadsheet to the app
│   ├── reset-local-db.sql
│   └── api-tests*.http manual API checks (VS Code REST Client)
├── deploy/             server files: systemd service, Caddyfile, backup scripts
├── .github/workflows/  nightly database backup (Option B)
├── build.ps1           builds everything into one jar
├── Dockerfile          used by Render
└── render.yaml         Render blueprint
```

## Running on your laptop (development)

You need Java 21+, Maven, Node.js 20+ and MySQL 8, with a database `flatmeals` and user `flatmeals` / `flatmeals`.

Use two PowerShell windows:

```powershell
# 1. Backend: http://localhost:8080/api/health
cd C:\flat-meal-manager\backend
mvn spring-boot:run

# 2. Frontend: http://localhost:5173  (forwards /api to the backend)
cd C:\flat-meal-manager\frontend
npm run dev
```

The first start creates the manager login `manager` / `changeme123`.

Run the tests with:

```powershell
cd C:\flat-meal-manager\backend
mvn test
```

## Building the one-file app

```powershell
cd C:\flat-meal-manager
powershell -ExecutionPolicy Bypass -File .\build.ps1
java -jar backend\target\flat-meal-manager.jar    # then open http://localhost:8080
```

## Going live

Read `docs/DEPLOY.md`, then `docs/FIRST-MONTH.md`.

- The live app runs with `SPRING_PROFILES_ACTIVE=prod`.
- It refuses to start without your own `JWT_SECRET` and `MANAGER_PASSWORD`.
- Backups run nightly. Where they go depends on the option you chose (see DEPLOY.md).

## Rules the code follows

- Money is `DECIMAL(12,2)` in MySQL and `BigDecimal` in Java, never float or double.
- Balance sign: **positive = the member owes the flat**, **negative = the member has credit**.
- Money records are cancelled (voided) with a reason, never deleted. Every change is in the audit log.
- Closed months are frozen. Only the latest closed month can be reopened, with a reason.
- Permissions are checked on the backend. Hiding a button is only for convenience.
- Schema changes go in a new Flyway file (`V2__...sql`). Never edit a migration that has already run.
