package com.kishakay.articles.article.dto;

/**
 * Outcome of {@code POST /articles}: either a freshly created article or an idempotent replay.
 * Controllers always return HTTP 201 with the same body and Location shape.
 */
public record CreateArticleResult(
        ArticleResponse body,
        String locationPath,
        boolean replay
) {
}
