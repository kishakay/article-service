-- Stage 2: articles table for draft creation.
-- Status is constrained in the database so invalid values cannot land via SQL either.

CREATE TABLE articles (
    id          UUID PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    body        TEXT NOT NULL,
    status      VARCHAR(32) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT articles_status_check CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT articles_title_not_blank CHECK (char_length(btrim(title)) > 0),
    CONSTRAINT articles_body_not_blank CHECK (char_length(btrim(body)) > 0)
);

CREATE INDEX idx_articles_status ON articles (status);
