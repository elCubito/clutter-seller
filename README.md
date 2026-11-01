# clutter-seller

A private, single-user app for quickly listing used items for sale.

1. Take photos of an item with your phone in the web app.
2. AI (Claude Code in headless mode) drafts the title, description, condition, category hints and package size.
3. AI searches the web for similar offers; the app computes price statistics and suggests a price.
4. Review and approve the draft on a PC, choosing the target services (OLX, Vinted).
5. A custom Chrome extension fills in the "add listing" form on the open OLX/Vinted page. A human always clicks "Publish".

> **Status:** Stage 0 – project skeleton. See [docs/PLAN.md](docs/PLAN.md) for the roadmap.

## Tech stack

- **Backend:** Java 25, Spring Boot 4, Spring Data JPA, Flyway, PostgreSQL
- **Frontend:** Angular (planned, Stage 2)
- **Browser extension:** Chrome Manifest V3, TypeScript (planned, Stage 6)
- **Tests:** JUnit 5, AssertJ, Spring Boot Test, Testcontainers
- **Ops:** Docker Compose, GitHub Actions

## Repository layout

| Path | Contents |
|---|---|
| `pom.xml`, `src/` | Spring Boot backend (modular monolith, packages per feature) |
| `compose.yaml` | PostgreSQL for local development |
| `docs/` | Roadmap and design notes |
| `frontend/` | Angular app (Stage 2) |
| `extension/` | Chrome extension (Stage 6) |

## Prerequisites

- JDK 25+
- Docker (for the dev database and for Testcontainers in tests)

## Running locally

```sh
cp .env.example .env   # optional; compose.yaml has dev defaults
./mvnw spring-boot:run
```

Spring Boot's Docker Compose support starts PostgreSQL from `compose.yaml` automatically.
Check the app at <http://localhost:8080/actuator/health>.

## Tests

```sh
./mvnw verify
```

Integration tests start a throwaway PostgreSQL container via Testcontainers, so Docker must be running.

## License

[MIT](LICENSE)
