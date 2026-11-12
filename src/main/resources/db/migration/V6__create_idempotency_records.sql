-- Stage 7: idempotency records for POST /articles.
-- Uniqueness on (operation, idempotency_key) coordinates concurrent claims in PostgreSQL.
-- Keys are retained indefinitely in this sample; production would add retention and
-- authenticated-client scoping (auth is out of scope here).

CREATE TABLE idempotency_records (
    id                   UUID PRIMARY KEY,
    operation            VARCHAR(64) NOT NULL,
    idempotency_key      VARCHAR(128) NOT NULL,
    request_fingerprint  CHAR(64) NOT NULL,
    response_status      INTEGER,
    response_body        TEXT,
    response_location    TEXT,
    created_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT idempotency_records_operation_key_unique
        UNIQUE (operation, idempotency_key)
);

COMMENT ON TABLE idempotency_records IS
    'Completed (and in-flight) idempotent operation responses; scoped by operation + key.';

COMMENT ON COLUMN idempotency_records.request_fingerprint IS
    'SHA-256 hex of the deterministic create payload (title + body).';

COMMENT ON COLUMN idempotency_records.response_body IS
    'JSON ArticleResponse stored after successful create; null only before complete in-TX.';
