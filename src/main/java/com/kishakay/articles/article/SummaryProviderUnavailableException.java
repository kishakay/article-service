package com.kishakay.articles.article;

/**
 * Expected failure when a {@link SummaryProvider} cannot produce a preview.
 * Mapped to HTTP 503 by {@code ApiExceptionHandler} — not a catch-all for unexpected errors.
 */
public class SummaryProviderUnavailableException extends RuntimeException {

    public SummaryProviderUnavailableException(String message) {
        super(message);
    }

    public SummaryProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
