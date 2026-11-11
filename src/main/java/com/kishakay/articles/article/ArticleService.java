package com.kishakay.articles.article;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
     * Lists published articles with offset pagination applied in the database.
     * Sorted by {@code publishedAt} descending, then {@code id} ascending for stable ties.
     */
    @Transactional(readOnly = true)
    public ArticlePageResponse listPublished(int page, int size) {
        validatePagination(page, size);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("publishedAt"),
                        Sort.Order.asc("id")
                )
        );
        Page<Article> result = articleRepository.findByStatus(ArticleStatus.PUBLISHED, pageable);

        List<ArticleListItemResponse> items = result.getContent().stream()
                .map(ArticleListItemResponse::from)
                .toList();

        return new ArticlePageResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
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

    private static void validatePagination(int page, int size) {
        List<InvalidPaginationException.FieldViolation> violations = new ArrayList<>();
        if (page < 0) {
            violations.add(new InvalidPaginationException.FieldViolation(
                    "page",
                    "must be greater than or equal to 0"
            ));
        }
        if (size < ArticleConstraints.SIZE_MIN || size > ArticleConstraints.SIZE_MAX) {
            violations.add(new InvalidPaginationException.FieldViolation(
                    "size",
                    "must be between " + ArticleConstraints.SIZE_MIN
                            + " and " + ArticleConstraints.SIZE_MAX
            ));
        }
        if (!violations.isEmpty()) {
            throw new InvalidPaginationException(violations);
        }
    }
}
