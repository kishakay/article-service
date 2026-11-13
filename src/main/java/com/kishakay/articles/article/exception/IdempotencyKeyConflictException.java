package com.kishakay.articles.article.exception;

/**
 * Thrown when an Idempotency-Key is reused with a different request payload.
 * Mapped to HTTP 409 by {@code ApiExceptionHandler}.
 */
public class IdempotencyKeyConflictException extends RuntimeException {

    private final String idempotencyKey;

    public IdempotencyKeyConflictException(String idempotencyKey) {
        super("Idempotency key reused with a different payload: " + idempotencyKey);
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
