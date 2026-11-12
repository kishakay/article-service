package com.kishakay.articles.article.exception;

import com.kishakay.articles.article.domain.ArticleStatus;
import java.util.UUID;

/**
 * Thrown when a client attempts to edit a non-DRAFT article.
 * Mapped to HTTP 409 by {@code ApiExceptionHandler}.
 */
public class ArticleNotEditableException extends RuntimeException {

    private final UUID articleId;
    private final ArticleStatus status;

    public ArticleNotEditableException(UUID articleId, ArticleStatus status) {
        super("Article is not editable: " + articleId + " (status=" + status + ")");
        this.articleId = articleId;
        this.status = status;
    }

    public UUID getArticleId() {
        return articleId;
    }

    public ArticleStatus getStatus() {
        return status;
    }
}
