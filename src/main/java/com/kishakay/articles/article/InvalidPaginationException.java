package com.kishakay.articles.article;

import java.util.List;

/**
 * Thrown when {@code page}/{@code size} query params are out of the accepted range.
 * Mapped to HTTP 400 by {@code ApiExceptionHandler}.
 */
public class InvalidPaginationException extends RuntimeException {

    private final List<FieldViolation> violations;

    public InvalidPaginationException(List<FieldViolation> violations) {
        super("Invalid pagination parameters");
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> getViolations() {
        return violations;
    }

    public record FieldViolation(String field, String message) {
    }
}
