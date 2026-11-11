package com.kishakay.articles.article;

/**
 * Lifecycle status for an article.
 * Stage 2 only creates {@link #DRAFT}; publishing comes later.
 */
public enum ArticleStatus {
    DRAFT,
    PUBLISHED
}
