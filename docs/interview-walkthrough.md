# Interview walkthrough

Runnable demo of the Article Management API after Stages 1–7. Assumes Postgres is up (`docker compose up -d`) and the app is listening on `http://localhost:8080` (`mvn spring-boot:run`).

Requires `curl` and `jq`. Article IDs and versions are captured from responses — nothing is hard-coded except a fixed unknown UUID for the 404 check.

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
echo "$REPLAY" | sed -n '/^{/,$p' | jq '{id,title,version,createdAt}'

# 2. Edit the draft (expectedVersion required)
EDIT=$(curl -s -X PUT "$BASE/articles/$ARTICLE_ID" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"Demo revised\",\"body\":\"<p>Revised body</p>\",\"expectedVersion\":$VERSION}")
echo "$EDIT" | jq '{id,title,body,status,createdAt,updatedAt,publishedAt,version}'
VERSION=$(echo "$EDIT" | jq -r '.version')

# 3. Stub summary preview (draft only — labelled sample, not real summarisation)
PREVIEW=$(curl -s -X POST "$BASE/articles/$ARTICLE_ID/summary-preview")
echo "$PREVIEW" | jq .

# 4. Publish
PUB1=$(curl -s -X POST "$BASE/articles/$ARTICLE_ID/publish")
echo "$PUB1" | jq '{id,status,publishedAt,version}'
PUBLISHED_AT=$(echo "$PUB1" | jq -r '.publishedAt')

# 5. Publish again — same publishedAt
PUB2=$(curl -s -X POST "$BASE/articles/$ARTICLE_ID/publish")
echo "$PUB2" | jq '{id,status,publishedAt,version}'
test "$(echo "$PUB2" | jq -r '.publishedAt')" = "$PUBLISHED_AT"

# 6. List published articles (drafts excluded; body omitted)
curl -s "$BASE/articles?page=0&size=10" | jq .

# 7. Edit after publication → 409
curl -s -i -X PUT "$BASE/articles/$ARTICLE_ID" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"Should fail\",\"body\":\"<p>nope</p>\",\"expectedVersion\":0}"

# 8. Unknown article → 404
curl -s -i "$BASE/articles/00000000-0000-4000-8000-000000000000"
```

## Expected outcomes

| Step | Result |
|------|--------|
| Create | `201`, `status: "DRAFT"`, `publishedAt: null`, `version: 0`, `Location` header |
| Replay create | `201`, **same** body timestamps/version and `Location` (one article row) |
| Edit | `200`, new title/body, still `DRAFT`, `createdAt` unchanged, `updatedAt` refreshed, `version` incremented |
| Summary preview | `200`, `provider: "stub"`, summary starts with `[STUB SUMMARY]`; article unchanged |
| Publish | `200`, `status: "PUBLISHED"`, `publishedAt` set, version bumped when status changes |
| Republish | `200`, **same** `publishedAt` (no dirty flush → version unchanged) |
| List | `200`, page of published items (no HTML `body`) |
| Edit published | `409`, message `Published articles cannot be edited` |
| Unknown id | `404`, message `Article not found` |

## Design decisions and trade-offs

| Decision | Why | Trade-off |
|----------|-----|-----------|
| Flyway owns schema; Hibernate `ddl-auto: validate` | Migrations are authoritative; startup fails on drift | No Hibernate auto-evolve; every schema change needs a migration |
| Domain methods for `publish()` / `edit()` | Rules stay on the aggregate; controllers stay thin | Callers must go through the service/domain — no raw field mutation |
| Create always DRAFT | Status is not client-writable | No “create already published” shortcut |
| Idempotent publish; 409 on edit-after-publish | Republish is safe; editing published content is a client mistake in this sample | No post-publish revisions / versioning |
| `@Version` + client `expectedVersion` on edit | Hibernate catches load→commit races; explicit compare rejects stale clients after a fresh reload | Clients must track `version` from reads |
| Publish protected by `@Version` only | Concurrent publish/edit writers still conflict on flush | No client `expectedVersion` on publish in this sample |
| `INSERT … ON CONFLICT DO NOTHING` for idempotency claims | PostgreSQL serialises same-key creates without catching unique violations in a failed TX | Keys retained forever here; production needs retention + auth scoping |
| Payload fingerprint (SHA-256 of deterministic JSON) | Same key + different body → 409; same key + same body → replay | Fingerprint covers title/body only |
| `GET /articles` published-only; `GET /articles/{id}` includes drafts | Collection ≈ public feed; by-id ≈ editorial | Drafts are readable without auth (documented limitation) |
| Offset pagination + partial index | Simple totals for demos; DB-side filter/sort/limit | Offsets shift under inserts; keyset deferred |
| Slim list DTO (no body) | Keeps list payloads small | Clients need `GET /{id}` for full HTML |
| `SummaryProvider` port + stub bean | Swap a real provider later without changing the API | Stub does not summarise content |
| Preview outside a service `@Transactional` | DB read finishes before provider work | Preview cannot rely on an open persistence context |
| Typed `503` only for `SummaryProviderUnavailableException` | Avoid masking bugs as “provider down” | Unexpected exceptions still surface as 500 |
| Shared `ApiError` envelope | Predictable client / demo handling | All mapped errors share one shape |

## Known limitations

- **No authentication or authorisation** — every endpoint is open; idempotency keys are not scoped to a client identity.
- **Individual retrieval exposes drafts** — `GET /articles/{id}` returns DRAFT articles; only the collection endpoint filters to PUBLISHED.
- **Idempotency keys retained indefinitely** — fine for the sample; production needs TTL/retention and authenticated-client key namespaces.
- **Stub summary provider only** — fixed labelled sample; no AI SDK, credentials, retries, caching, or async jobs. Preview is not persisted.
- **No real media handling or cloud deployment** — no uploads, object storage, or production hosting. Compose credentials are local-dev defaults only.

Also out of scope: frontend, search/categories, post-publish revisions, cursor/keyset pagination, author review / saving of summaries.
