-- Stage 5: index for published listing.
-- Supports WHERE status = 'PUBLISHED' ORDER BY published_at DESC, id ASC
-- with LIMIT/OFFSET applied in the database.

CREATE INDEX idx_articles_published_list
    ON articles (published_at DESC, id ASC)
    WHERE status = 'PUBLISHED';

COMMENT ON INDEX idx_articles_published_list IS
    'Partial index for GET /articles published listing (published_at DESC, id ASC).';
