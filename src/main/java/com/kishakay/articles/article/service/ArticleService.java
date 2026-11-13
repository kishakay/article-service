package com.kishakay.articles.article.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kishakay.articles.article.domain.Article;
import com.kishakay.articles.article.domain.ArticleConstraints;
import com.kishakay.articles.article.domain.ArticleStatus;
import com.kishakay.articles.article.dto.ArticleListItemResponse;
import com.kishakay.articles.article.dto.ArticlePageResponse;
import com.kishakay.articles.article.dto.ArticleResponse;
import com.kishakay.articles.article.dto.CreateArticleRequest;
import com.kishakay.articles.article.dto.CreateArticleResult;
import com.kishakay.articles.article.dto.SaveSummaryRequest;
import com.kishakay.articles.article.dto.SummaryPreviewResponse;
import com.kishakay.articles.article.dto.UpdateArticleRequest;
import com.kishakay.articles.article.exception.ArticleNotFoundException;
import com.kishakay.articles.article.exception.ArticleVersionConflictException;
import com.kishakay.articles.article.exception.IdempotencyKeyConflictException;
import com.kishakay.articles.article.exception.InvalidIdempotencyKeyException;
import com.kishakay.articles.article.exception.InvalidPaginationException;
import com.kishakay.articles.article.exception.SummaryPreviewNotAllowedException;
import com.kishakay.articles.article.idempotency.IdempotencyRecord;
import com.kishakay.articles.article.idempotency.IdempotencyRecordRepository;
import com.kishakay.articles.article.idempotency.RequestFingerprint;
import com.kishakay.articles.article.repository.ArticleRepository;
import com.kishakay.articles.article.summary.SummaryProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final SummaryProvider summaryProvider;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    public ArticleService(
            ArticleRepository articleRepository,
            SummaryProvider summaryProvider,
            IdempotencyRecordRepository idempotencyRecordRepository,
            ObjectMapper objectMapper
    ) {
        this.articleRepository = articleRepository;
        this.summaryProvider = summaryProvider;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists a new article as {@link ArticleStatus#DRAFT}, coordinated by {@code Idempotency-Key}.
     * Same key + same payload replays the original 201 body/Location; same key + different payload → 409.
     * Bean Validation on the request DTO runs before this method is invoked.
     */
    @Transactional
    public CreateArticleResult create(String idempotencyKey, CreateArticleRequest request) {
        String key = requireIdempotencyKey(idempotencyKey);
        String fingerprint = RequestFingerprint.forCreateArticle(request, objectMapper);
        String operation = ArticleConstraints.CREATE_ARTICLE_OPERATION;

        boolean claimed = idempotencyRecordRepository.tryClaim(operation, key, fingerprint);
        if (!claimed) {
            return replayOrConflict(operation, key, fingerprint);
        }

        Article article = Article.createDraft(request.title(), request.body());
        Article saved = articleRepository.saveAndFlush(article);
        ArticleResponse body = ArticleResponse.from(saved);
        String locationPath = "/articles/" + saved.getId();

        try {
            idempotencyRecordRepository.complete(
                    operation,
                    key,
                    HttpStatus.CREATED.value(),
                    objectMapper.writeValueAsString(body),
                    locationPath
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialise create response for idempotency store", ex);
        }

        return new CreateArticleResult(body, locationPath, false);
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
     * Protected by JPA {@code @Version}: concurrent writers lose with 409.
     */
    @Transactional
    public ArticleResponse publish(UUID id) {
        Article article = requireArticle(id);
        try {
            article.publish();
            // Flush so @PreUpdate timestamps / version are present in the response body.
            Article saved = articleRepository.saveAndFlush(article);
            return ArticleResponse.from(saved);
        } catch (OptimisticLockingFailureException ex) {
            throw new ArticleVersionConflictException(id, ex);
        }
    }

    /**
     * Transaction boundary for draft edits. Domain rules (DRAFT-only; published is immutable)
     * live on {@link Article#edit(String, String)}. Bean Validation runs before this method.
     * <p>
     * Compares {@code expectedVersion} to the loaded entity <em>before</em> mutating — {@code @Version}
     * alone would not reject a client that submits a stale version after the server reloads fresh data.
     * Hibernate's version check still detects races between load and commit.
     */
    @Transactional
    public ArticleResponse update(UUID id, UpdateArticleRequest request) {
        Article article = requireArticle(id);
        if (!Objects.equals(article.getVersion(), request.expectedVersion())) {
            throw new ArticleVersionConflictException(id);
        }
        try {
            article.edit(request.title(), request.body());
            // Flush so @PreUpdate updatedAt / version are present in the response body.
            Article saved = articleRepository.saveAndFlush(article);
            return ArticleResponse.from(saved);
        } catch (OptimisticLockingFailureException ex) {
            throw new ArticleVersionConflictException(id, ex);
        }
    }

    /**
     * Saves an author-approved summary on a DRAFT article. Optimistic locking matches
     * {@link #update}: clients must send the version from their last read. Changing the
     * summary updates {@code updatedAt} and increments {@code version}. Preview remains
     * {@link #previewSummary(UUID)} and does not write this field.
     * <p>
     * Domain rules (DRAFT-only) live on {@link Article#saveApprovedSummary(String)}.
     */
    @Transactional
    public ArticleResponse saveSummary(UUID id, SaveSummaryRequest request) {
        Article article = requireArticle(id);
        if (!Objects.equals(article.getVersion(), request.expectedVersion())) {
            throw new ArticleVersionConflictException(id);
        }
        try {
            article.saveApprovedSummary(request.summary());
            Article saved = articleRepository.saveAndFlush(article);
            return ArticleResponse.from(saved);
        } catch (OptimisticLockingFailureException ex) {
            throw new ArticleVersionConflictException(id, ex);
        }
    }

    /**
     * Preview a summary for a {@link ArticleStatus#DRAFT} article only.
     * Does <strong>not</strong> persist the summary or change article status/timestamps.
     * <p>
     * Intentionally <em>not</em> {@code @Transactional}: the repository read completes in its
     * own short transaction before {@link SummaryProvider#summarize(String)} runs, so a slow
     * or failing provider never holds a database transaction open.
     */
    public SummaryPreviewResponse previewSummary(UUID id) {
        Article article = requireArticle(id);
        if (article.getStatus() != ArticleStatus.DRAFT) {
            throw new SummaryPreviewNotAllowedException(id, article.getStatus());
        }

        UUID articleId = article.getId();
        String articleText = article.getTitle() + "\n\n" + article.getBody();

        String summary = summaryProvider.summarize(articleText);
        return new SummaryPreviewResponse(articleId, summary, summaryProvider.providerId());
    }

    private CreateArticleResult replayOrConflict(String operation, String key, String fingerprint) {
        IdempotencyRecord existing = idempotencyRecordRepository
                .findByOperationAndKey(operation, key)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency key claim lost without a persisted row: " + key
                ));

        if (!fingerprint.equals(existing.requestFingerprint())) {
            throw new IdempotencyKeyConflictException(key);
        }
        if (!existing.isComplete()) {
            throw new IllegalStateException(
                    "Idempotency record for key=" + key + " is incomplete after competing claim"
            );
        }

        try {
            ArticleResponse body = objectMapper.readValue(existing.responseBody(), ArticleResponse.class);
            return new CreateArticleResult(body, existing.responseLocation(), true);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialise stored create response", ex);
        }
    }

    private static String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidIdempotencyKeyException("must not be blank");
        }
        if (idempotencyKey.length() > ArticleConstraints.IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new InvalidIdempotencyKeyException(
                    "must be at most " + ArticleConstraints.IDEMPOTENCY_KEY_MAX_LENGTH + " characters"
            );
        }
        return idempotencyKey;
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
