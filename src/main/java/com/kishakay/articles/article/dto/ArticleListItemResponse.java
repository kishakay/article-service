package com.kishakay.articles.article.dto;

import com.kishakay.articles.article.domain.Article;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Slim list projection for published articles — excludes HTML body.
 */
@Schema(name = "ArticleListItemResponse", description = "Slim published-article list item (no body)")
public record ArticleListItemResponse(
        @Schema(description = "Article id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,

        @Schema(description = "Article title", example = "Hello draft")
        String title,

        @Schema(description = "First publish timestamp (UTC)", example = "2026-01-15T12:00:00Z")
        Instant publishedAt,

        @Schema(description = "Last update timestamp (UTC)", example = "2026-01-15T12:00:00Z")
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
