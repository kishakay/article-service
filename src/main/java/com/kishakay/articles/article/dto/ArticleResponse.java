package com.kishakay.articles.article.dto;

import com.kishakay.articles.article.domain.Article;
import com.kishakay.articles.article.domain.ArticleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * API representation of an article. Controllers return this DTO — never the JPA entity.
 *
 * <p>{@code summary} is null until the author saves an approved summary and is cleared when a
 * draft body edit changes the body. Missing {@code summary} in stored idempotency snapshots
 * deserialises as null so pre-field response JSON still replays.
 */
@Schema(name = "ArticleResponse", description = "Full article representation")
public record ArticleResponse(
        @Schema(description = "Article id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,

        @Schema(description = "Article title", example = "Hello draft")
        String title,

        @Schema(description = "Article body (HTML)", example = "<p>First paragraph</p>")
        String body,

        @Schema(description = "Lifecycle status", example = "DRAFT")
        ArticleStatus status,

        @Schema(description = "Creation timestamp (UTC)", example = "2026-01-15T10:00:00Z")
        Instant createdAt,

        @Schema(description = "Last update timestamp (UTC)", example = "2026-01-15T10:05:00Z")
        Instant updatedAt,

        @Schema(description = "First publish timestamp (UTC); null while DRAFT", example = "2026-01-15T12:00:00Z", nullable = true)
        Instant publishedAt,

        @Schema(
                description = "Author-approved summary; null until saved; cleared when draft body changes",
                example = "Reviewed summary text",
                nullable = true
        )
        String summary,

        @Schema(description = "Optimistic lock version; send as expectedVersion on edit or summary save", example = "0")
        Long version
) {

    public static ArticleResponse from(Article article) {
        return new ArticleResponse(
                article.getId(),
                article.getTitle(),
                article.getBody(),
                article.getStatus(),
                article.getCreatedAt(),
                article.getUpdatedAt(),
                article.getPublishedAt(),
                article.getSummary(),
                article.getVersion()
        );
    }
}
