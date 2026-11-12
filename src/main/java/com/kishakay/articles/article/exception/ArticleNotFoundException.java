package com.kishakay.articles.article.exception;

import java.util.UUID;

/**
 * Thrown when an article id does not exist. Mapped to HTTP 404 by {@code ApiExceptionHandler}.
 */
public class ArticleNotFoundException extends RuntimeException {

    private final UUID articleId;

    public ArticleNotFoundException(UUID articleId) {
        super("Article not found: " + articleId);
        this.articleId = articleId;
    }

    public UUID getArticleId() {
        return articleId;
    }
}
