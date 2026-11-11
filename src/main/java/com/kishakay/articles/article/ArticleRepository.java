package com.kishakay.articles.article;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleRepository extends JpaRepository<Article, UUID> {

    /**
     * Published listing query — status filter and sort/limit come from {@link Pageable}.
     */
    Page<Article> findByStatus(ArticleStatus status, Pageable pageable);
}
