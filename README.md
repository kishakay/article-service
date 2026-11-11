# article-service

Small Java / Spring Boot **Article Management API** interview code sample.

## Current stage

**Stage 5 — paginated published listing**

- Spring Boot 3.4 / Java 21 / Maven
- Docker Compose PostgreSQL + Flyway (`V1` baseline, `V2` articles table, `V3` `published_at`, `V4` published-list index)
- Hibernate `ddl-auto: validate` (schema owned by Flyway)
- `POST /articles` creates a **DRAFT** article (`201` + `Location`)
- `GET /articles` returns **PUBLISHED** articles only (offset pagination)
- `GET /articles/{id}` returns the article (including drafts — see note below)
- `POST /articles/{id}/publish` transitions **DRAFT → PUBLISHED** and stamps `publishedAt` once
- `PUT /articles/{id}` replaces title and body on **DRAFT** articles only

Authentication, search/categories, optimistic locking, and summary generation are **not** implemented yet.

### Auth deferred — drafts are readable by id

`GET /articles/{id}` may return **DRAFT** articles. The collection endpoint `GET /articles` never includes drafts. There is no authentication yet; treat this as an **editorial workflow sample**, not a production-secure public API. A later stage would gate draft visibility.

### Published articles are immutable (this sample)

Once published, an article cannot be edited via `PUT /articles/{id}` — the API returns `409 Conflict`. Revision workflows (new draft versions after publish) and simultaneous-edit protection (optimistic locking / ETags) are deferred.

### Offset pagination choice and limitations

`GET /articles` uses **offset pagination** (`page` + `size`):

| Param  | Default | Constraints                          |
|--------|---------|--------------------------------------|
| `page` | `0`     | must be ≥ 0                          |
| `size` | `20`    | must be between **1** and **100**    |

Results are ordered by `publishedAt` descending, with `id` ascending as a deterministic tie-breaker. Filtering, sorting, and `LIMIT`/`OFFSET` run in PostgreSQL (backed by partial index `idx_articles_published_list`).

**Why offset for this sample:** it is simple for clients, easy to reason about totals (`totalElements` / `totalPages`), and enough for an interview demo with modest catalogs.

**Limitations:** when new articles are published between requests, offsets shift. Page `1` after a publish can repeat or skip items that appeared on page `0` earlier. Cursor / keyset pagination (`publishedAt` + `id` seek) would be more stable under inserts; deferred here to keep Stage 5 focused.

## Out of scope (documented)

- Authentication / authorization (none; do not treat this API as secured)
- Frontend / UI
- Media uploads
- Cloud deployment
- Real AI summarization (a stub provider comes later)
- Microservices split
- Search / categories / full-text filters
- Revision / re-draft after publish
- Optimistic locking / simultaneous-edit protection
- Cursor / keyset pagination

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

On successful startup you should see Flyway apply `V1`–`V4`, then `Started ArticleServiceApplication`.

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

### 5. Edit a draft article

```bash
curl -i -X PUT http://localhost:8080/articles/<uuid> \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "Revised title",
    "body": "<p>Updated paragraph</p>"
  }'
```

Expected:

- HTTP `200`
- Updated `title` / `body`, still `status: "DRAFT"`
- `createdAt` unchanged; `updatedAt` refreshed
- Clients cannot change `status`, ids, or timestamps through this request (only `title` and `body` are accepted)

Same validation rules as create. Invalid input → `400` and the article is left unchanged.

Editing a **PUBLISHED** article → `409 Conflict` (`message: "Published articles cannot be edited"`); the article is unchanged.

Unknown id → `404`. Malformed UUID → `400`.

### 6. Publish an article

```bash
curl -i -X POST http://localhost:8080/articles/<uuid>/publish
```

Expected:

- HTTP `200`
- `status: "PUBLISHED"`
- `publishedAt` set to the first publish time

Publishing again returns `200` with the **same** `publishedAt` (idempotent; original stamp preserved).

Unknown id → `404`. Malformed UUID → `400`.

### 7. List published articles

```bash
# Defaults: page=0, size=20
curl -i 'http://localhost:8080/articles'

# Explicit page / size
curl -i 'http://localhost:8080/articles?page=0&size=10'
```

Expected:

- HTTP `200`
- Body shape:

```json
{
  "items": [
    {
      "id": "...",
      "title": "...",
      "publishedAt": "...",
      "updatedAt": "..."
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

Notes:

- Only **PUBLISHED** articles appear (drafts are excluded)
- List items omit HTML `body` (and `status` / `createdAt`)
- No published rows → `200` with `"items": []`, `totalElements: 0`, `totalPages: 0`
- `page < 0` or `size` outside 1–100 → `400` (`message: "Invalid pagination parameters"`, `violations[]`)

### 8. Tests

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
    │   ├── api/          # shared error envelope (400 / 404 / 409)
    │   └── article/      # feature package (entity, DTOs, service, controller)
    └── resources/
        ├── application.yml
        └── db/migration/
            ├── V1__baseline.sql
            ├── V2__create_articles.sql
            ├── V3__add_published_at.sql
            └── V4__index_published_articles_list.sql
```

## Design notes

- **Flyway owns schema; Hibernate validates** — keeps migrations authoritative and catches mapping drift at startup.
- **DTO boundary** — controllers expose response DTOs, never the JPA entity. Listing uses a slim item DTO without HTML body.
- **Creation always DRAFT** — status is set in the domain factory, not accepted from the client.
- **Publication rules on the domain** — `Article.publish()` is idempotent and stamps `publishedAt` once; the service owns the `@Transactional` boundary.
- **Edit rules on the domain** — `Article.edit(...)` allows DRAFT only; published → conflict. Service owns the write transaction; `createdAt` is preserved and `updatedAt` is refreshed via `@PreUpdate`.
- **Published listing in the database** — `findByStatus(PUBLISHED, pageable)` applies filter + `ORDER BY published_at DESC, id ASC` + limit/offset in SQL; partial index `idx_articles_published_list` supports the access path.
- **Consistent errors** — `@RestControllerAdvice` maps validation / malformed UUIDs / bad pagination (`400`), missing articles (`404`), and non-editable published articles (`409`) to one envelope.
- **GET by id returns drafts** — intentional for this editorial sample; auth comes later. Collection listing stays published-only.
- Defaults in Compose are for **local development only**, not production secrets.
