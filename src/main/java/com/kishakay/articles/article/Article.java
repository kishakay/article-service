package com.kishakay.articles.article;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "articles")
public class Article {

    @Id
    private UUID id;

    @Column(nullable = false, length = ArticleConstraints.TITLE_MAX_LENGTH)
    private String title;

    /**
     * HTML body. Stored as Postgres TEXT; mapped explicitly so Hibernate validate
     * does not assume the default VARCHAR(255).
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ArticleStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Set once on first successful publish. Remains null while the article is a draft.
     */
    @Column(name = "published_at")
    private Instant publishedAt;

    protected Article() {
        // JPA
    }

    /**
     * Factory for new drafts — creation always starts as {@link ArticleStatus#DRAFT}.
     */
    public static Article createDraft(String title, String body) {
        Article article = new Article();
        article.id = UUID.randomUUID();
        article.title = title;
        article.body = body;
        article.status = ArticleStatus.DRAFT;
        return article;
    }

    /**
     * Publication rules live on the aggregate:
     * <ul>
     *   <li>DRAFT → PUBLISHED stamps {@code publishedAt} once</li>
     *   <li>Already PUBLISHED is a no-op (idempotent; preserves original {@code publishedAt})</li>
     * </ul>
     */
    public void publish() {
        if (status == ArticleStatus.PUBLISHED) {
            return;
        }
        status = ArticleStatus.PUBLISHED;
        if (publishedAt == null) {
            // Truncate to micros so the in-memory stamp matches Postgres timestamptz.
            publishedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    /**
     * Replaces editable content fields. Only {@link ArticleStatus#DRAFT} articles may be edited;
     * published articles are immutable in this sample (revision workflows deferred).
     *
     * @throws ArticleNotEditableException if the article is not a draft
     */
    public void edit(String newTitle, String newBody) {
        if (status != ArticleStatus.DRAFT) {
            throw new ArticleNotEditableException(id, status);
        }
        title = newTitle;
        body = newBody;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public ArticleStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
