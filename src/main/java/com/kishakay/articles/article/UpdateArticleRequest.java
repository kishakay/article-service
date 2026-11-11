package com.kishakay.articles.article;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /articles/{id}}.
 * Only title and body are accepted; status, ids, and timestamps cannot be changed by the client.
 */
public record UpdateArticleRequest(
        @NotBlank
        @Size(max = ArticleConstraints.TITLE_MAX_LENGTH)
        String title,

        @NotBlank
        @Size(max = ArticleConstraints.BODY_MAX_LENGTH)
        String body
) {
}
