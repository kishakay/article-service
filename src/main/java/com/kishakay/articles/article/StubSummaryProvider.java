package com.kishakay.articles.article;

import org.springframework.stereotype.Component;

/**
 * Deterministic stand-in for a real summariser. Does <strong>not</strong> inspect
 * or summarise {@code articleText} — it always returns the same labelled sample.
 */
@Component
public class StubSummaryProvider implements SummaryProvider {

    static final String SAMPLE_SUMMARY =
            "[STUB SUMMARY] This is a sample preview. It does not summarise the article content.";

    static final String PROVIDER_ID = "stub";

    @Override
    public String summarize(String articleText) {
        return SAMPLE_SUMMARY;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }
}
