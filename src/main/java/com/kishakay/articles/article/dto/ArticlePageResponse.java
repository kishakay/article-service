package com.kishakay.articles.article.dto;

import java.util.List;

/**
 * Offset-paginated response for {@code GET /articles}.
 */
public record ArticlePageResponse(
        List<ArticleListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
