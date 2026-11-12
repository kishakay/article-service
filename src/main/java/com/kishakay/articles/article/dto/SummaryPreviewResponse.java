package com.kishakay.articles.article.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Preview-only summary payload. Not persisted; author review / save is deferred.
 */
@Schema(name = "SummaryPreviewResponse", description = "Preview-only summary; not persisted")
public record SummaryPreviewResponse(
        @Schema(description = "Article id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID articleId,

        @Schema(
                description = "Preview summary text (stub returns a fixed labelled sample)",
                example = "[STUB SUMMARY] This is a sample preview. It does not summarise the article content."
        )
        String summary,

        @Schema(description = "Provider id", example = "stub")
        String provider
) {
}
