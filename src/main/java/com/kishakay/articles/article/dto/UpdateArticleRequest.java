package com.kishakay.articles.article.dto;

import com.kishakay.articles.article.domain.ArticleConstraints;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /articles/{id}}.
 * Only title and body are accepted; status, ids, and timestamps cannot be changed by the client.
 */
@Schema(name = "UpdateArticleRequest", description = "Replace title and body on a DRAFT article")
public record UpdateArticleRequest(
        @NotBlank
        @Size(max = ArticleConstraints.TITLE_MAX_LENGTH)
        @Schema(description = "Article title", example = "Revised title", maxLength = ArticleConstraints.TITLE_MAX_LENGTH)
        String title,

        @NotBlank
        @Size(max = ArticleConstraints.BODY_MAX_LENGTH)
        @Schema(description = "Article body (HTML)", example = "<p>Updated paragraph</p>", maxLength = ArticleConstraints.BODY_MAX_LENGTH)
        String body
) {
}
