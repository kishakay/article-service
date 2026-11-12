package com.kishakay.articles.article.idempotency;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted idempotency claim / completed response for a scoped operation key.
 */
public record IdempotencyRecord(
        UUID id,
        String operation,
        String idempotencyKey,
        String requestFingerprint,
        Integer responseStatus,
        String responseBody,
        String responseLocation,
        Instant createdAt
) {

    public boolean isComplete() {
        return responseStatus != null && responseBody != null && responseLocation != null;
    }
}
