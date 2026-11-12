package com.kishakay.articles.article.exception;

/**
 * Expected failure when a {@link com.kishakay.articles.article.summary.SummaryProvider}
 * cannot produce a preview.
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
