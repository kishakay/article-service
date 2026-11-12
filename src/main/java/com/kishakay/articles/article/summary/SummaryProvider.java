package com.kishakay.articles.article.summary;

import com.kishakay.articles.article.exception.SummaryProviderUnavailableException;

/**
 * Pluggable summarisation port. Stage 6 ships a deterministic stub;
 * a real implementation can replace the Spring bean without changing the API.
 */
public interface SummaryProvider {

    /**
     * Builds a summary preview from article text (title + body).
     *
     * @throws SummaryProviderUnavailableException when the provider cannot serve the request
     */
    String summarize(String articleText);

    /**
     * Stable provider identifier returned in preview responses (e.g. {@code "stub"}).
     */
    String providerId();
}
