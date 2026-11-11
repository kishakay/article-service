package com.kishakay.articles.article;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/articles")
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @PostMapping
    public ResponseEntity<ArticleResponse> create(@Valid @RequestBody CreateArticleRequest request) {
        ArticleResponse created = articleService.create(request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Retrieves any article by id, including drafts.
     * Auth is deferred — treat as editorial workflow only, not production-secure.
     */
    @GetMapping("/{id}")
    public ArticleResponse getById(@PathVariable UUID id) {
        return articleService.getById(id);
    }

    /**
     * Publishes a draft. Already-published articles return 200 with the existing
     * article (original {@code publishedAt} preserved).
     */
    @PostMapping("/{id}/publish")
    public ArticleResponse publish(@PathVariable UUID id) {
        return articleService.publish(id);
    }
}
