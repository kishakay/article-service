# article-service

Small Java / Spring Boot **Article Management API** interview code sample.

## Current stage

**Stage 3 — retrieve and publish**

- Spring Boot 3.4 / Java 21 / Maven
- Docker Compose PostgreSQL + Flyway (`V1` baseline, `V2` articles table, `V3` `published_at`)
- Hibernate `ddl-auto: validate` (schema owned by Flyway)
- `POST /articles` creates a **DRAFT** article (`201` + `Location`)
- `GET /articles/{id}` returns the article (including drafts — see note below)
- `POST /articles/{id}/publish` transitions **DRAFT → PUBLISHED** and stamps `publishedAt` once

Editing, listing, authentication, optimistic locking, and summary generation are **not** implemented yet.

### Auth deferred — drafts are readable

`GET /articles/{id}` may return **DRAFT** articles. There is no authentication yet; treat this as an **editorial workflow sample**, not a production-secure public API. A later stage would gate draft visibility.

## Out of scope (documented)

- Authentication / authorization (none; do not treat this API as secured)
- Frontend / UI
- Media uploads
- Cloud deployment
- Real AI summarization (a stub provider comes later)
- Microservices split
- Listing / editing articles (later stages)

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

On successful startup you should see Flyway apply `V1`–`V3`, then `Started ArticleServiceApplication`.

### 3. Create a draft article

```bash
curl -i -X POST http://localhost:8080/articles \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "Hello draft",
    "body": "<p>First paragraph</p>"
  }'
```

Expected:

- HTTP `201 Created`
- `Location: http://localhost:8080/articles/<uuid>`
- JSON body with `id`, `title`, `body`, `status: "DRAFT"`, `createdAt`, `updatedAt`, and `publishedAt: null` (omitted/null)

Validation rules:

| Field | Rules |
|-------|-------|
| `title` | required, non-blank, max **200** characters |
| `body`  | required, non-blank, max **100000** characters (HTML) |

Invalid input returns `400 Bad Request` with a consistent error envelope (`message`, `violations[]`).

### 4. Retrieve an article by id

```bash
# Replace <uuid> with the id from the create response (or Location header)
curl -i http://localhost:8080/articles/<uuid>
```

Expected: HTTP `200` with `ArticleResponse` (drafts included — auth deferred).

Unknown id → `404` with the same error envelope (`message: "Article not found"`).  
Malformed UUID → `400` (`message: "Malformed UUID"`).

### 5. Publish an article

```bash
curl -i -X POST http://localhost:8080/articles/<uuid>/publish
```

Expected:

- HTTP `200`
- `status: "PUBLISHED"`
- `publishedAt` set to the first publish time

Publishing again returns `200` with the **same** `publishedAt` (idempotent; original stamp preserved).

Unknown id → `404`. Malformed UUID → `400`.

### 6. Tests

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

Schema is owned by **Flyway**. Hibernate `ddl-auto` is `validate` so the app fails fast if migrations and entities drift.

## Project layout (so far)

```
├── docker-compose.yml
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/kishakay/articles/
    │   ├── ArticleServiceApplication.java
    │   ├── api/          # shared error envelope (400 / 404)
    │   └── article/      # feature package (entity, DTOs, service, controller)
    └── resources/
        ├── application.yml
        └── db/migration/
            ├── V1__baseline.sql
            ├── V2__create_articles.sql
            └── V3__add_published_at.sql
```

## Design notes

- **Flyway owns schema; Hibernate validates** — keeps migrations authoritative and catches mapping drift at startup.
- **DTO boundary** — controllers expose `ArticleResponse`, never the JPA entity.
- **Creation always DRAFT** — status is set in the domain factory, not accepted from the client.
- **Publication rules on the domain** — `Article.publish()` is idempotent and stamps `publishedAt` once; the service owns the `@Transactional` boundary.
- **Consistent errors** — `@RestControllerAdvice` maps validation, malformed UUIDs (`400`), and missing articles (`404`) to one envelope.
- **GET returns drafts** — intentional for this editorial sample; auth comes later.
- Defaults in Compose are for **local development only**, not production secrets.
