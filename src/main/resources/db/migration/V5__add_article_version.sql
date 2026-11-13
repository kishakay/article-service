-- Stage 7: optimistic locking for articles.
-- Existing rows receive version 0 so Hibernate @Version starts from a valid value.

ALTER TABLE articles
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN articles.version IS
    'JPA @Version optimistic lock counter; starts at 0 for new and existing rows.';
