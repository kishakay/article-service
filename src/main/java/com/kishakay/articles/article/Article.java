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
}
