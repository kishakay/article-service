package com.kishakay.articles.article;

import java.time.Instant;
import java.util.UUID;

/**
 * API representation of an article. Controllers return this DTO — never the JPA entity.
 */
public record ArticleResponse(
        UUID id,
        String title,
        String body,
        ArticleStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static ArticleResponse from(Article article) {
        return new ArticleResponse(
                article.getId(),
                article.getTitle(),
                article.getBody(),
                article.getStatus(),
                article.getCreatedAt(),
                article.getUpdatedAt()
        );
    }
}
