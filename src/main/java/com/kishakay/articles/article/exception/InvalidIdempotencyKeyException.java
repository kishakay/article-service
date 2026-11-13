package com.kishakay.articles.article.exception;

/**
 * Thrown when the Idempotency-Key header is missing, blank, or longer than the documented max.
 * Mapped to HTTP 400 by {@code ApiExceptionHandler}.
 */
public class InvalidIdempotencyKeyException extends RuntimeException {

    private final String detail;

    public InvalidIdempotencyKeyException(String detail) {
        super(detail);
        this.detail = detail;
    }

    public String getDetail() {
        return detail;
    }
}
