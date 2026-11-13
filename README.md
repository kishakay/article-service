# article-service

Small Java / Spring Boot **Article Management API** interview code sample.

## Current stage

**Stage 7 — optimistic locking + idempotent create**

- Spring Boot 3.4 / Java 21 / Maven
- Docker Compose PostgreSQL + Flyway (`V1`–`V6`: articles, `published_at`, list index, `version`, idempotency records)
- Hibernate `ddl-auto: validate` (schema owned by Flyway)
- `POST /articles` creates a **DRAFT** article (`201` + `Location`) and **requires** `Idempotency-Key`
- `GET /articles` returns **PUBLISHED** articles only (offset pagination)
- `GET /articles/{id}` returns the article (including drafts — see note below)
- `POST /articles/{id}/publish` transitions **DRAFT → PUBLISHED** and stamps `publishedAt` once
- `PUT /articles/{id}` replaces title and body on **DRAFT** articles only and **requires** `expectedVersion`
- `POST /articles/{id}/summary-preview` returns a **preview-only** stub summary for **DRAFT** articles
- springdoc OpenAPI (`/v3/api-docs`) + Swagger UI (`/swagger-ui/index.html`)

Authentication, search/categories, author review / saving of summaries, and real AI summarization are **not** implemented yet.

**Interview demo:** see [`docs/interview-walkthrough.md`](docs/interview-walkthrough.md) for a reproducible `curl` + `jq` script (idempotent create → edit with version → stub summary → publish → republish → list → 409 → 404), design trade-offs, and known limitations.

### Auth deferred — drafts are readable by id

`GET /articles/{id}` may return **DRAFT** articles. The collection endpoint `GET /articles` never includes drafts. There is no authentication yet; treat this as an **editorial workflow sample**, not a production-secure public API. A later stage would gate draft visibility.

### Published articles are immutable (this sample)

Once published, an article cannot be edited via `PUT /articles/{id}` — the API returns `409 Conflict`. Revision workflows (new draft versions after publish) are deferred. Concurrent writers are protected with optimistic locking (see below).

### Optimistic locking

Every article carries a JPA `@Version` field (`version` in `ArticleResponse`, starting at `0`).

- **Edit (`PUT /articles/{id}`)** requires `expectedVersion` in the body. The service compares it to the loaded entity **before** mutating. This matters because `@Version` alone does not reject a client that submits an old edit after the server reloads fresh data. Hibernate’s version check still detects races between load and commit.
- **Publish** is also covered by the entity `@Version` on flush (concurrent conflicting writers → `409`). Repeat publish of an already-published article remains a no-op and **preserves** the original `publishedAt` (no dirty update → version unchanged).
- Stale `expectedVersion` or Hibernate optimistic-lock failures return the shared `409` envelope with `message: "Article version conflict"`.

### Idempotent `POST /articles`

`Idempotency-Key` is **required** (non-blank, max **128** characters). Keys are scoped to the create-article operation only.

| Same key + … | Result |
|--------------|--------|
| Same title/body | Replay original `201` body + `Location` (original timestamps and `version`) |
| Different title/body | `409` (`Idempotency key reused with a different payload`) |
| Different keys, identical content | Two articles (allowed) |

**Coordination:** the service claims the key with PostgreSQL `INSERT … ON CONFLICT DO NOTHING`, then saves the article and completed response **in the same transaction**. Concurrent same-key requests block on the unique `(operation, idempotency_key)` index; losers replay or conflict after the winner commits. Responses are returned only after commit. This sample **retains keys indefinitely**; production would need retention rules and authenticated-client key scoping (auth remains out of scope).

Payload fingerprinting uses deterministic JSON serialisation of validated `title`/`body` and SHA-256.

### Offset pagination choice and limitations

`GET /articles` uses **offset pagination** (`page` + `size`):

| Param  | Default | Constraints                          |
|--------|---------|--------------------------------------|
| `page` | `0`     | must be ≥ 0                          |
| `size` | `20`    | must be between **1** and **100**    |

Results are ordered by `publishedAt` descending, with `id` ascending as a deterministic tie-breaker. Filtering, sorting, and `LIMIT`/`OFFSET` run in PostgreSQL (backed by partial index `idx_articles_published_list`).

**Why offset for this sample:** it is simple for clients, easy to reason about totals (`totalElements` / `totalPages`), and enough for an interview demo with modest catalogs.

**Limitations:** when new articles are published between requests, offsets shift. Page `1` after a publish can repeat or skip items that appeared on page `0` earlier. Cursor / keyset pagination (`publishedAt` + `id` seek) would be more stable under inserts; deferred here to keep listing focused.

### Stub summary preview (not real summarisation)

`POST /articles/{id}/summary-preview` is **DRAFT-only** and **does not save** anything. The wired `StubSummaryProvider` returns a fixed, clearly labelled sample string — it does **not** summarise the article content.

A real provider can replace the stub by implementing `SummaryProvider` and registering that Spring bean instead (constructor-injected into `ArticleService`). Author review and persisting summaries are deferred to a later stage.

## Known limitations

- **No authentication or authorisation** — do not treat this API as secured; idempotency keys are not client-scoped.
- **Individual retrieval exposes drafts** — `GET /articles/{id}` returns DRAFT articles; only `GET /articles` is published-only.
- **Idempotency keys retained forever** — sample behaviour only; production needs TTL/retention and auth-scoped namespaces.
- **Stub summary provider only** — labelled sample string; no real AI, persistence, or author-review save flow.
- **No real media handling or cloud deployment** — no uploads/object storage; Compose credentials are local-dev defaults.

Also out of scope: frontend / UI, microservices split, search / categories, post-publish revisions, cursor / keyset pagination.

## Prerequisites

- Java 21+
- Maven 3.9+ (3.8+ works), or use the committed Maven Wrapper (`./mvnw`)
- Docker + Docker Compose (app runtime + Testcontainers for tests)
- `curl` + `jq` (for the interview walkthrough script)

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

On successful startup you should see Flyway apply `V1`–`V6`, then `Started ArticleServiceApplication`.

### 3. OpenAPI / Swagger UI

Once the app is running:

| Resource | URL |
|----------|-----|
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |

Documented with **springdoc-openapi** `2.8.8` (`springdoc-openapi-starter-webmvc-ui`). Create examples include the `Idempotency-Key` header; edit examples include `expectedVersion`.

### 4. Create a draft and capture its id

Inspect headers + body:

```bash
curl -i -X POST http://localhost:8080/articles \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: create-hello-draft-001' \
  -d '{
    "title": "Hello draft",
    "body": "<p>First paragraph</p>"
  }'
```

Capture the id (and version) for later steps:

```bash
CREATE=$(curl -s -X POST http://localhost:8080/articles \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: create-hello-draft-001' \
  -d '{"title":"Hello draft","body":"<p>First paragraph</p>"}')
ARTICLE_ID=$(echo "$CREATE" | jq -r '.id')
VERSION=$(echo "$CREATE" | jq -r '.version')
echo "ARTICLE_ID=$ARTICLE_ID VERSION=$VERSION"
```

Retrying with the **same** key and payload returns the **same** `201` body and `Location` (one article). Changing the payload with that key returns `409`.

Expected:

- HTTP `201 Created`
- `Location: http://localhost:8080/articles/<uuid>`
- JSON body with `id`, `title`, `body`, `status: "DRAFT"`, `createdAt`, `updatedAt`, `publishedAt: null`, and `version: 0`

Validation rules:

| Field / header | Rules |
|----------------|-------|
| `title` | required, non-blank, max **200** characters |
| `body`  | required, non-blank, max **100000** characters (HTML) |
| `Idempotency-Key` | required, non-blank, max **128** characters |

Invalid input or key → `400 Bad Request` with the consistent error envelope (`message`, `violations[]`).

### 5. Retrieve an article by id

```bash
curl -i "http://localhost:8080/articles/$ARTICLE_ID"
```

Expected: HTTP `200` with `ArticleResponse` (drafts included — auth deferred), including `version`.

Unknown id → `404` with the same error envelope (`message: "Article not found"`).  
Malformed UUID → `400` (`message: "Malformed UUID"`).

### 6. Edit a draft article

```bash
curl -i -X PUT "http://localhost:8080/articles/$ARTICLE_ID" \
  -H 'Content-Type: application/json' \
  -d "{
    \"title\": \"Revised title\",
    \"body\": \"<p>Updated paragraph</p>\",
    \"expectedVersion\": $VERSION
  }"
```

Expected:

- HTTP `200`
- Updated `title` / `body`, still `status: "DRAFT"`
- `createdAt` unchanged; `updatedAt` refreshed; `version` incremented
- Clients cannot change `status`, ids, or timestamps through this request

Same validation rules as create for title/body; `expectedVersion` is required. Invalid input → `400` and the article is left unchanged.

Stale `expectedVersion` → `409` (`message: "Article version conflict"`); no mutation.  
Editing a **PUBLISHED** article → `409` (`message: "Published articles cannot be edited"`).  
Unknown id → `404`. Malformed UUID → `400`.

### 7. Preview a summary (draft only — stub)

```bash
curl -i -X POST "http://localhost:8080/articles/$ARTICLE_ID/summary-preview"
```

Expected (DRAFT):

- HTTP `200`
- Body shape:

```json
{
  "articleId": "<captured-uuid>",
  "summary": "[STUB SUMMARY] This is a sample preview. It does not summarise the article content.",
  "provider": "stub"
}
```

Important:

- The stub **does not summarise** the article — the `summary` string is a fixed labelled sample
- Nothing is persisted; article `status`, `updatedAt`, `version`, and content are unchanged
- Author review and saving summaries are **deferred**
- To swap in a real provider later: implement `SummaryProvider`, expose it as the Spring bean (instead of `StubSummaryProvider`), and keep the same endpoint/DTO

**PUBLISHED** article → `409 Conflict` (`message: "Summary preview is only available for draft articles"`).  
Unknown id → `404` (`message: "Article not found"`).  
Provider unavailable (`SummaryProviderUnavailableException`) → `503` (`message: "Summary provider unavailable"`). Unexpected failures are **not** mapped to 503.

### 8. Publish an article

```bash
curl -i -X POST "http://localhost:8080/articles/$ARTICLE_ID/publish"
```

Expected:

- HTTP `200`
- `status: "PUBLISHED"`
- `publishedAt` set to the first publish time
- `version` increments when the draft actually transitions

Publishing again returns `200` with the **same** `publishedAt` (idempotent; original stamp preserved). Compare with `jq`:

```bash
PUBLISHED_AT=$(curl -s -X POST "http://localhost:8080/articles/$ARTICLE_ID/publish" | jq -r '.publishedAt')
curl -s -X POST "http://localhost:8080/articles/$ARTICLE_ID/publish" | jq -r --arg p "$PUBLISHED_AT" 'select(.publishedAt == $p) | .publishedAt'
```

Unknown id → `404`. Malformed UUID → `400`. Concurrent optimistic-lock conflicts → `409`.

### 9. List published articles

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
- List items omit HTML `body` (and `status` / `createdAt` / `version`)
- No published rows → `200` with `"items": []`, `totalElements: 0`, `totalPages: 0`
- `page < 0` or `size` outside 1–100 → `400` (`message: "Invalid pagination parameters"`, `violations[]`)

### 10. After publish: 409 on edit, 404 on unknown id

```bash
# 409 — published articles are immutable in this sample
curl -i -X PUT "http://localhost:8080/articles/$ARTICLE_ID" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Should fail","body":"<p>nope</p>","expectedVersion":0}'

# 404 — fixed unknown UUID (no need to invent one at random)
curl -i http://localhost:8080/articles/00000000-0000-4000-8000-000000000000
```

For the full scripted path in one block, see [`docs/interview-walkthrough.md`](docs/interview-walkthrough.md).

### 11. Tests

Integration tests use **Testcontainers** PostgreSQL (`postgres:16-alpine`). Docker must be available (no external Compose DB required for tests):

```bash
./mvnw test
# or the same checks CI runs:
./mvnw --batch-mode --no-transfer-progress verify
```

Concurrency cases coordinate with barriers/latches (not sleeps) and cover idempotent replay, fingerprint conflicts, simultaneous same-key creates, rollback of article + key, stale `expectedVersion`, and competing edits.

## Continuous Integration

GitHub Actions (`.github/workflows/ci.yml`) runs on **pull requests** and **pushes to `main`**:

- Ubuntu runner, Eclipse Temurin **Java 21**, Maven Wrapper with dependency caching
- `./mvnw --batch-mode --no-transfer-progress verify` (compile, tests, package)
- Integration tests start PostgreSQL via **Testcontainers** (`1.21.4+`) using the runner’s Docker (no CI Postgres service, no developer secrets)
- Superseded runs on the same PR/branch are cancelled; Surefire/Failsafe reports upload on failure

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
├── .github/workflows/ci.yml
├── docker-compose.yml
├── mvnw / mvnw.cmd
├── pom.xml
├── README.md
├── docs/
│   └── interview-walkthrough.md   # runnable demo + design trade-offs
└── src/main/
    ├── java/com/kishakay/articles/
    │   ├── ArticleServiceApplication.java
    │   ├── api/          # shared error envelope + OpenAPI config
    │   └── article/      # feature package (entity, DTOs, service, controller,
    │                     # idempotency, SummaryProvider)
    └── resources/
        ├── application.yml
        └── db/migration/
            ├── V1__baseline.sql
            ├── V2__create_articles.sql
            ├── V3__add_published_at.sql
            ├── V4__index_published_articles_list.sql
            ├── V5__add_article_version.sql
            └── V6__create_idempotency_records.sql
```

## Design notes

Short list below; trade-offs are expanded in [`docs/interview-walkthrough.md`](docs/interview-walkthrough.md).

- **Flyway owns schema; Hibernate validates** — migrations authoritative; startup fails on mapping drift (no Hibernate auto-evolve).
- **DTO boundary** — controllers expose response DTOs, never the JPA entity. Listing uses a slim item DTO without HTML body.
- **Creation always DRAFT** — status is set in the domain factory, not accepted from the client.
- **Idempotent create** — claim key with `ON CONFLICT DO NOTHING`, fingerprint payload, store completed `201` in the same TX; replay or `409` for conflicts.
- **Optimistic locking** — `@Version` on `Article`; edit requires `expectedVersion`; publish participates via Hibernate version checks; stale/conflict → shared `409`.
- **Publication rules on the domain** — `Article.publish()` is idempotent and stamps `publishedAt` once; the service owns the `@Transactional` boundary.
- **Edit rules on the domain** — `Article.edit(...)` allows DRAFT only; published → `409` (not an idempotent no-op). Service owns the write transaction; `createdAt` preserved, `updatedAt` via `@PreUpdate`.
- **Published listing in the database** — `findByStatus(PUBLISHED, pageable)` applies filter + `ORDER BY published_at DESC, id ASC` + limit/offset in SQL; partial index `idx_articles_published_list` supports the access path. Offset is simple for demos; keyset would be stabler under inserts.
- **Summary preview via replaceable port** — `SummaryProvider` is constructor-injected; `StubSummaryProvider` is the default bean. Preview loads the draft first, then calls the provider **outside** a service-level transaction (`open-in-view` is already `false`) so DB work finishes before summarisation. Typed `SummaryProviderUnavailableException` → `503` only; no catch-all.
- **Preview is not a write** — no summary column, no status/timestamp/version mutation; author review / save deferred.
- **Consistent errors** — `@RestControllerAdvice` maps validation / bad keys / malformed UUIDs / bad pagination (`400`), missing articles (`404`), non-editable / version / idempotency conflicts (`409`), and expected provider unavailability (`503`) to one envelope.
- **GET by id returns drafts** — intentional for this editorial sample; auth comes later. Collection listing stays published-only.
- Defaults in Compose are for **local development only**, not production secrets.
