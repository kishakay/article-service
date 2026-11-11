package com.kishakay.articles.article;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /articles}.
 * Only title and body are accepted; status is always DRAFT on create.
 */
public record CreateArticleRequest(
        @NotBlank
        @Size(max = ArticleConstraints.TITLE_MAX_LENGTH)
        String title,

        @NotBlank
        @Size(max = ArticleConstraints.BODY_MAX_LENGTH)
        String body
) {
}
