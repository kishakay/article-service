package com.kishakay.articles.article;

/**
 * Shared field limits for request validation and the JPA column mapping.
 * Kept in one place so README examples and tests stay aligned with the API contract.
 */
public final class ArticleConstraints {

    public static final int TITLE_MAX_LENGTH = 200;
    public static final int BODY_MAX_LENGTH = 100_000;

    private ArticleConstraints() {
    }
}
