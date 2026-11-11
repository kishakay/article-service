package com.kishakay.articles.article;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;

    public ArticleService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    /**
     * Persists a new article as {@link ArticleStatus#DRAFT}.
     * Bean Validation on the request DTO runs before this method is invoked.
     */
    @Transactional
    public ArticleResponse create(CreateArticleRequest request) {
        Article article = Article.createDraft(request.title(), request.body());
        Article saved = articleRepository.save(article);
        return ArticleResponse.from(saved);
    }

    /**
     * Loads an article by id. Returns drafts and published articles alike —
     * authentication / editorial visibility filters are deferred (Stage 3 sample).
     */
    @Transactional(readOnly = true)
    public ArticleResponse getById(UUID id) {
        return ArticleResponse.from(requireArticle(id));
    }

    /**
     * Transaction boundary for publication. Domain rules (idempotent DRAFT→PUBLISHED
     * and one-time {@code publishedAt}) live on {@link Article#publish()}.
     */
    @Transactional
    public ArticleResponse publish(UUID id) {
        Article article = requireArticle(id);
        article.publish();
        // Flush so @PreUpdate timestamps are present in the response body.
        Article saved = articleRepository.saveAndFlush(article);
        return ArticleResponse.from(saved);
    }

    /**
     * Transaction boundary for draft edits. Domain rules (DRAFT-only; published is immutable)
     * live on {@link Article#edit(String, String)}. Bean Validation runs before this method.
     */
    @Transactional
    public ArticleResponse update(UUID id, UpdateArticleRequest request) {
        Article article = requireArticle(id);
        article.edit(request.title(), request.body());
        // Flush so @PreUpdate updatedAt is present in the response body.
        Article saved = articleRepository.saveAndFlush(article);
        return ArticleResponse.from(saved);
    }

    private Article requireArticle(UUID id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new ArticleNotFoundException(id));
    }
}
