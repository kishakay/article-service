package com.kishakay.articles.article.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Offset-paginated response for {@code GET /articles}.
 */
@Schema(name = "ArticlePageResponse", description = "Offset-paginated published article list")
public record ArticlePageResponse(
        @Schema(description = "Page items (published only)")
        List<ArticleListItemResponse> items,

        @Schema(description = "Zero-based page index", example = "0")
        int page,

        @Schema(description = "Page size", example = "20")
        int size,

        @Schema(description = "Total matching articles", example = "1")
        long totalElements,

        @Schema(description = "Total pages (0 when empty)", example = "1")
        int totalPages
) {
}
