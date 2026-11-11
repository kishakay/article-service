-- Stage 3: publication timestamp.
-- Nullable because drafts have not been published yet.
-- Application sets this once on first publish and never overwrites it.

ALTER TABLE articles
    ADD COLUMN published_at TIMESTAMPTZ;

COMMENT ON COLUMN articles.published_at IS
    'Set once when status first becomes PUBLISHED; null for drafts.';
