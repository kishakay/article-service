package com.kishakay.articles.article;

/**
 * Lifecycle status for an article.
 * Creation always starts as {@link #DRAFT}; {@link #PUBLISHED} is set via domain publish.
 */
public enum ArticleStatus {
    DRAFT,
    PUBLISHED
}
