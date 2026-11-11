package com.kishakay.articles.article;

/**
 * Shared field limits for request validation and the JPA column mapping.
 * Kept in one place so README examples and tests stay aligned with the API contract.
 */
public final class ArticleConstraints {

    public static final int TITLE_MAX_LENGTH = 200;
    public static final int BODY_MAX_LENGTH = 100_000;

    /** Zero-based page index default for {@code GET /articles}. */
    public static final int PAGE_DEFAULT = 0;

    /** Default page size for {@code GET /articles}. */
    public static final int SIZE_DEFAULT = 20;

    /** Minimum accepted page size (inclusive). */
    public static final int SIZE_MIN = 1;

    /** Maximum accepted page size (inclusive). */
    public static final int SIZE_MAX = 100;

    private ArticleConstraints() {
    }
}
