package com.kishakay.articles.article.dto;

import com.kishakay.articles.article.domain.Article;
import com.kishakay.articles.article.domain.ArticleStatus;
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
        Instant updatedAt,
        Instant publishedAt
) {

    public static ArticleResponse from(Article article) {
        return new ArticleResponse(
                article.getId(),
                article.getTitle(),
                article.getBody(),
                article.getStatus(),
                article.getCreatedAt(),
                article.getUpdatedAt(),
                article.getPublishedAt()
        );
    }
}
