package com.kishakay.articles.article.exception;

import java.util.UUID;

/**
 * Thrown when a client's expectedVersion does not match the loaded article version,
 * or when Hibernate optimistic locking fails on flush/commit.
 * Mapped to HTTP 409 by {@code ApiExceptionHandler}.
 */
public class ArticleVersionConflictException extends RuntimeException {

    private final UUID articleId;

    public ArticleVersionConflictException(UUID articleId) {
        super("Article version conflict: " + articleId);
        this.articleId = articleId;
    }

    public ArticleVersionConflictException(UUID articleId, Throwable cause) {
        super("Article version conflict: " + articleId, cause);
        this.articleId = articleId;
    }

    public UUID getArticleId() {
        return articleId;
    }
}
