package com.kishakay.articles.article;

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
}
