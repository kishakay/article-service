package com.kishakay.articles.article.dto;

import com.kishakay.articles.article.domain.ArticleConstraints;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /articles}.
 * Only title and body are accepted; status is always DRAFT on create.
 */
@Schema(name = "CreateArticleRequest", description = "Create a draft article (title and body only)")
public record CreateArticleRequest(
        @NotBlank
        @Size(max = ArticleConstraints.TITLE_MAX_LENGTH)
        @Schema(description = "Article title", example = "Hello draft", maxLength = ArticleConstraints.TITLE_MAX_LENGTH)
        String title,

        @NotBlank
        @Size(max = ArticleConstraints.BODY_MAX_LENGTH)
        @Schema(description = "Article body (HTML)", example = "<p>First paragraph</p>", maxLength = ArticleConstraints.BODY_MAX_LENGTH)
        String body
) {
}
