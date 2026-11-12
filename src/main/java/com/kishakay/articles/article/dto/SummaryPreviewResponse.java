package com.kishakay.articles.article.dto;

import java.util.UUID;

/**
 * Preview-only summary payload. Not persisted; author review / save is deferred.
 */
public record SummaryPreviewResponse(
        UUID articleId,
        String summary,
        String provider
) {
}
