-- Author-approved summary (nullable). Cleared when draft body changes.
-- Preview endpoint does not write this column.

ALTER TABLE articles
    ADD COLUMN summary TEXT;

COMMENT ON COLUMN articles.summary IS
    'Author-approved summary text; null until saved; cleared when draft body changes.';
