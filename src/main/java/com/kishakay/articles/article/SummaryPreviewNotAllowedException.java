package com.kishakay.articles.article;

import java.util.UUID;

/**
 * Thrown when summary preview is requested for a non-DRAFT article.
 * Mapped to HTTP 409 by {@code ApiExceptionHandler}.
 */
public class SummaryPreviewNotAllowedException extends RuntimeException {

    private final UUID articleId;
    private final ArticleStatus status;

    public SummaryPreviewNotAllowedException(UUID articleId, ArticleStatus status) {
        super("Summary preview not allowed for article: " + articleId + " (status=" + status + ")");
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
