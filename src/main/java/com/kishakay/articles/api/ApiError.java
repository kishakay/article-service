package com.kishakay.articles.api;

import java.time.Instant;
import java.util.List;

/**
 * Stable error envelope so clients (and interview demos) get consistent validation failures.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> violations
) {

    public record FieldViolation(String field, String message) {
    }
}
