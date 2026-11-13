package com.kishakay.articles.article.dto;

import com.kishakay.articles.article.domain.ArticleConstraints;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /articles/{id}/summary}.
 * Persists an author-approved summary on a DRAFT; preview remains non-persisting.
 */
@Schema(name = "SaveSummaryRequest", description = "Save an author-approved summary on a DRAFT article with optimistic locking")
public record SaveSummaryRequest(
        @NotBlank(message = "Summary must not be blank")
        @Size(max = ArticleConstraints.SUMMARY_MAX_LENGTH, message = "Summary must be at most {max} characters")
        @Schema(
                description = "Author-approved summary text",
                example = "Reviewed summary text",
                maxLength = ArticleConstraints.SUMMARY_MAX_LENGTH
        )
        String summary,

        @NotNull(message = "expectedVersion is required")
        @Schema(
                description = "Expected optimistic lock version from the last read",
                example = "0",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Long expectedVersion
) {
}
