package com.kishakay.articles.article.dto;

import com.kishakay.articles.article.domain.Article;
import java.time.Instant;
import java.util.UUID;

/**
 * Slim list projection for published articles — excludes HTML body.
 */
public record ArticleListItemResponse(
        UUID id,
        String title,
        Instant publishedAt,
        Instant updatedAt
) {

    public static ArticleListItemResponse from(Article article) {
        return new ArticleListItemResponse(
                article.getId(),
                article.getTitle(),
                article.getPublishedAt(),
                article.getUpdatedAt()
        );
    }
}
