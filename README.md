# Flat Meal Manager

Replaces the monthly meal/expense spreadsheet for our flat.
React frontend · Spring Boot REST API · MySQL.

## Project layout

```
flat-meal-manager/
├── docker-compose.yml          MySQL 8 for local development
├── backend/                    Spring Boot (Java 21, Maven)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/flatmeals/
│       │   ├── FlatMealsApplication.java
│       │   └── common/HealthController.java
│       └── resources/
│           ├── application.yml
│           └── db/migration/V1__schema.sql
├── frontend/                   React (Vite)
└── docs/                       Design notes
```

## Prerequisites

- Java 21 (JDK), e.g. Eclipse Temurin 21
- Maven 3.9+
- Node.js 20+
- Docker Desktop

## Running locally

```bash
# 1. Database
docker compose up -d

# 2. Backend  (new terminal)
cd backend
mvn spring-boot:run
#    -> http://localhost:8080/api/health

# 3. Frontend (new terminal)
cd frontend
npm install
npm run dev
#    -> http://localhost:5173
```

## Conventions

- Money is `DECIMAL(12,2)` in MySQL and `BigDecimal` in Java. Never float/double.
- Balance sign: **positive = member owes the flat**, **negative = member has credit**.
- Financial records are voided with a reason, never deleted.
- Schema changes go in a new Flyway file (`V2__...sql`). Never edit a migration that has already run.
