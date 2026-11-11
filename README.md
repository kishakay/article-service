# article-service

Small Java / Spring Boot **Article Management API** interview code sample.

## Current stage

**Stage 1 — infrastructure only**

- Spring Boot 3.4 / Java 21 / Maven
- Docker Compose PostgreSQL
- Datasource configured via environment variables
- Flyway migrations (PostgreSQL module)
- Application starts and connects to the database

Article endpoints, domain schema, and SummaryProvider are **not** implemented yet.

## Out of scope (documented)

- Authentication / authorization (none; do not treat this API as secured)
- Frontend / UI
- Media uploads
- Cloud deployment
- Real AI summarization (a stub provider comes later)
- Microservices split

## Prerequisites

- Java 21+
- Maven 3.9+ (3.8+ works)
- Docker + Docker Compose

## Quick start

### 1. Start PostgreSQL

```bash
docker compose up -d
```

Wait until the container is healthy:

```bash
docker compose ps
```

### 2. Run the application

Defaults match `docker-compose.yml` (`articles` / `articles` / DB `articles` on `localhost:5432`):

```bash
mvn spring-boot:run
```

Or override connection settings:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/articles
export DB_USERNAME=articles
export DB_PASSWORD=articles
mvn spring-boot:run
```

On successful startup you should see:

- HikariCP acquiring a connection
- Flyway applying `V1__baseline`
- `Started ArticleServiceApplication`

There are no HTTP controllers yet, so `GET http://localhost:8080/` returns **404** — that is expected.

### 3. Tests

Requires a reachable Postgres (same Compose stack):

```bash
mvn test
```

## Configuration

| Variable       | Default                                      | Purpose              |
|----------------|----------------------------------------------|----------------------|
| `DB_URL`       | `jdbc:postgresql://localhost:5432/articles`  | JDBC URL             |
| `DB_USERNAME`  | `articles`                                   | DB user              |
| `DB_PASSWORD`  | `articles`                                   | DB password          |
| `SERVER_PORT`  | `8080`                                       | HTTP port            |

Schema is owned by **Flyway**. Hibernate `ddl-auto` is `validate` so the app fails fast if migrations and entities drift (entities arrive in a later stage).

## Project layout (so far)

```
├── docker-compose.yml
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/kishakay/articles/
    │   └── ArticleServiceApplication.java
    └── resources/
        ├── application.yml
        └── db/migration/
            └── V1__baseline.sql
```

Packages are organized **by feature** as the domain grows; Stage 1 keeps a single application root with no extra layers.

## Design notes (Stage 1)

- **Env-based datasource** with Compose-matching defaults keeps local setup friction low while remaining deployable.
- **`flyway-database-postgresql`** is required explicitly on Flyway 10+ (pulled in by Boot 3.4).
- **Baseline-only migration** proves the pipeline without locking in article tables early.
- Defaults in Compose are for **local development only**, not production secrets.
