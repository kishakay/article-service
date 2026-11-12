package com.kishakay.articles.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Stable error envelope so clients (and interview demos) get consistent validation failures.
 */
@Schema(name = "ApiError", description = "Consistent error envelope for 400 / 404 / 409 / 503 responses")
public record ApiError(
        @Schema(description = "Error timestamp (UTC)", example = "2026-01-15T10:00:00Z")
        Instant timestamp,

        @Schema(description = "HTTP status code", example = "400")
        int status,

        @Schema(description = "HTTP reason phrase", example = "Bad Request")
        String error,

        @Schema(description = "Human-readable message", example = "Validation failed")
        String message,

        @Schema(description = "Request path", example = "/articles")
        String path,

        @Schema(description = "Field-level violations (empty when not applicable)")
        List<FieldViolation> violations
) {

    @Schema(name = "FieldViolation", description = "Single field validation failure")
    public record FieldViolation(
            @Schema(description = "Field name", example = "title")
            String field,

            @Schema(description = "Violation message", example = "must not be blank")
            String message
    ) {
    }
}
