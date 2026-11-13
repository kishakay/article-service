# Interview walkthrough

**Canonical** runnable demo of the Article Management API after Stages 1–8 (+ CI). Other interview prep docs should link here rather than copy a second full script.

## Prerequisites

1. **Compose Postgres** for the running app (dev credentials in `docker-compose.yml`):

   ```bash
   docker compose up -d
   docker compose ps   # wait until healthy
   ```

2. App on `http://localhost:8080`:

   ```bash
   ./mvnw spring-boot:run
   ```

   Flyway applies `V1`–`V7` (including `version`, idempotency records, and nullable `summary`).

3. `curl` + `jq`.

**Tests / CI** use a **separate** Postgres via Testcontainers (`postgres:16-alpine`) — not the Compose database. Locally: `./mvnw test` or `./mvnw --batch-mode --no-transfer-progress verify` (same command as GitHub Actions job `build`).

Article IDs and versions are captured from responses — nothing is hard-coded except a fixed unknown UUID for the 404 check.

```bash
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
IDEMPOTENCY_KEY="demo-create-$(date +%s)"

# 1. Create a draft (Idempotency-Key required)
CREATE=$(curl -s -i -X POST "$BASE/articles" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{"title":"Demo draft","body":"<p>Original body</p>"}')
echo "$CREATE"
ARTICLE_ID=$(echo "$CREATE" | sed -n '/^{/,$p' | jq -r '.id')
VERSION=$(echo "$CREATE" | sed -n '/^{/,$p' | jq -r '.version')
echo "ARTICLE_ID=$ARTICLE_ID VERSION=$VERSION"

# 1b. Replay create with the same key + payload → same 201 body / Location
REPLAY=$(curl -s -i -X POST "$BASE/articles" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{"title":"Demo draft","body":"<p>Original body</p>"}')
echo "$REPLAY" | sed -n '/^{/,$p' | jq '{id,title,version,summary,createdAt}'

# 2. Edit the draft (expectedVersion required)
EDIT=$(curl -s -X PUT "$BASE/articles/$ARTICLE_ID" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"Demo revised\",\"body\":\"<p>Revised body</p>\",\"expectedVersion\":$VERSION}")
echo "$EDIT" | jq '{id,title,body,status,summary,createdAt,updatedAt,publishedAt,version}'
VERSION=$(echo "$EDIT" | jq -r '.version')

# 3. Stub summary preview (draft only — labelled sample, not real summarisation; not saved)
PREVIEW=$(curl -s -X POST "$BASE/articles/$ARTICLE_ID/summary-preview")
echo "$PREVIEW" | jq .

# 4. Review the preview text, then save an author-approved summary
SAVE=$(curl -s -X PUT "$BASE/articles/$ARTICLE_ID/summary" \
  -H 'Content-Type: application/json' \
  -d "{\"summary\":\"Reviewed summary text\",\"expectedVersion\":$VERSION}")
echo "$SAVE" | jq '{id,summary,status,updatedAt,version}'
VERSION=$(echo "$SAVE" | jq -r '.version')

# 5. Publish
PUB1=$(curl -s -X POST "$BASE/articles/$ARTICLE_ID/publish")
echo "$PUB1" | jq '{id,status,publishedAt,summary,version}'
PUBLISHED_AT=$(echo "$PUB1" | jq -r '.publishedAt')
VERSION=$(echo "$PUB1" | jq -r '.version')

# 6. Publish again — same publishedAt (and same version; no dirty flush)
PUB2=$(curl -s -X POST "$BASE/articles/$ARTICLE_ID/publish")
echo "$PUB2" | jq '{id,status,publishedAt,version}'
test "$(echo "$PUB2" | jq -r '.publishedAt')" = "$PUBLISHED_AT"

# 7. List published articles (drafts excluded; body omitted)
curl -s "$BASE/articles?page=0&size=10" | jq .

# 8. Edit after publication → 409 immutability
# Use the version returned by publish so expectedVersion matches — otherwise the
# service rejects with "Article version conflict" before the published check.
curl -s -i -X PUT "$BASE/articles/$ARTICLE_ID" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"Should fail\",\"body\":\"<p>nope</p>\",\"expectedVersion\":$VERSION}"

# 9. Unknown article → 404
curl -s -i "$BASE/articles/00000000-0000-4000-8000-000000000000"
```

## Expected outcomes

| Step | Result |
|------|--------|
| Create | `201`, `status: "DRAFT"`, `publishedAt: null`, `summary: null`, `version: 0`, `Location` header |
| Replay create | `201`, **same** body timestamps/version/summary and `Location` (one article row) |
| Edit | `200`, new title/body, still `DRAFT`, `createdAt` unchanged, `updatedAt` refreshed, `version` → `1` |
| Summary preview | `200`, `provider: "stub"`, summary starts with `[STUB SUMMARY]`; article unchanged (no version bump; saved `summary` still null) |
| Save summary | `200`, `summary: "Reviewed summary text"`, `updatedAt` refreshed, `version` → `2` |
| Publish | `200`, `status: "PUBLISHED"`, `publishedAt` set, approved `summary` retained, `version` → `3` |
| Republish | `200`, **same** `publishedAt` and `version` (domain no-op → no dirty flush) |
| List | `200`, page of published items (no HTML `body`); backed by partial index `idx_articles_published_list` |
| Edit published | `409`, message `Published articles cannot be edited` (when `expectedVersion` matches the published row) |
| Unknown id | `404`, message `Article not found` |

Typical version path in this script: create `0` → edit `1` → save summary `2` → publish `3` → republish `3`.

## Design decisions and trade-offs

| Decision | Why | Trade-off |
|----------|-----|-----------|
| Flyway owns schema; Hibernate `ddl-auto: validate` | Migrations are authoritative; startup fails on drift | No Hibernate auto-evolve; every schema change needs a migration |
| Domain methods for `publish()` / `edit()` / `saveApprovedSummary()` | Rules stay on the aggregate; controllers stay thin | Callers must go through the service/domain — no raw field mutation |
| Create always DRAFT | Status is not client-writable | No “create already published” shortcut |
| Idempotent publish; 409 on edit/summary-after-publish | Republish is safe; mutating published content is a client mistake in this sample | No post-publish revisions |
| `@Version` + client `expectedVersion` on edit and summary save | Hibernate catches load→commit races; explicit compare rejects stale clients after a fresh reload | Clients must track `version` from reads |
| Publish protected by `@Version` only | Concurrent publish/edit writers still conflict on flush | No client `expectedVersion` on publish in this sample |
| `INSERT … ON CONFLICT DO NOTHING` for idempotency claims | PostgreSQL serialises same-key creates without catching unique violations in a failed TX | Keys retained forever here; production needs retention + auth scoping |
| Same TX: claim + article + completed response | Rollback removes article and key together | Responses returned only after commit; missing `summary` in older snapshots deserialises as null |
| Payload fingerprint (SHA-256 of deterministic JSON) | Same key + different body → 409; same key + same body → replay | Fingerprint covers title/body only |
| `GET /articles` published-only; `GET /articles/{id}` includes drafts | Collection ≈ public feed; by-id ≈ editorial | Drafts are readable without auth (documented limitation) |
| Offset pagination + `idx_articles_published_list` | Simple totals for demos; DB-side filter/sort/limit | Offsets shift under inserts; keyset deferred |
| Slim list DTO (no body) | Keeps list payloads small | Clients need `GET /{id}` for full HTML |
| `SummaryProvider` port + stub bean | Swap a real provider later without changing the API | Stub does not summarise content |
| Preview outside a service `@Transactional` | Repository read finishes in its own short TX; provider never holds a service-level TX (`open-in-view: false`) | Preview cannot rely on an open persistence context |
| Preview vs save | Preview suggests; `PUT …/summary` persists author approval | Two endpoints keep suggestion separate from approval |
| Clear summary only on body change | Title-only / identical-body edits keep approved text; one version bump with the edit | Authors must re-approve after body rewrites |
| Typed `503` only for `SummaryProviderUnavailableException` | Avoid masking bugs as “provider down” | Unexpected exceptions still surface as 500 |
| Shared `ApiError` envelope | Predictable client / demo handling | All mapped errors share one shape |
| Compose for app; Testcontainers for tests/CI | Dev DB stays stable; tests get an isolated real Postgres | Docker required for `./mvnw test` |

## Known limitations

- **No authentication or authorisation** — every endpoint is open; idempotency keys are not scoped to a client identity.
- **Individual retrieval exposes drafts** — `GET /articles/{id}` returns DRAFT articles; only the collection endpoint filters to PUBLISHED.
- **Idempotency keys retained indefinitely** — fine for the sample; production needs TTL/retention and authenticated-client key namespaces.
- **Stub summary provider only** — fixed labelled sample; no AI SDK, credentials, retries, caching, or async jobs. Preview is not persisted; save is a separate author-approved write.
- **No real media handling or cloud deployment** — no uploads, object storage, or production hosting. Compose credentials are local-dev defaults only.

Also out of scope: frontend, search/categories, post-publish revisions, cursor/keyset pagination.
