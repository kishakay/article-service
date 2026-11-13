package com.kishakay.articles.article.controller;

import com.kishakay.articles.api.ApiError;
import com.kishakay.articles.article.domain.ArticleConstraints;
import com.kishakay.articles.article.dto.ArticlePageResponse;
import com.kishakay.articles.article.dto.ArticleResponse;
import com.kishakay.articles.article.dto.CreateArticleRequest;
import com.kishakay.articles.article.dto.CreateArticleResult;
import com.kishakay.articles.article.dto.SummaryPreviewResponse;
import com.kishakay.articles.article.dto.UpdateArticleRequest;
import com.kishakay.articles.article.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/articles")
@Tag(name = "Articles", description = "Create, edit, publish, list, and preview summaries for articles")
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @PostMapping
    @Operation(
            summary = "Create a draft article",
            description = "Always creates status DRAFT. Requires a non-blank Idempotency-Key "
                    + "(max " + ArticleConstraints.IDEMPOTENCY_KEY_MAX_LENGTH + " characters). "
                    + "Same key + same payload replays the original 201; same key + different payload → 409."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Draft created or idempotent replay of the original create",
                    headers = @Header(name = "Location", description = "URI of the created article", schema = @Schema(type = "string", format = "uri")),
                    content = @Content(schema = @Schema(implementation = ArticleResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed or invalid Idempotency-Key",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Idempotency-Key reused with a different payload",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public ResponseEntity<ArticleResponse> create(
            @Parameter(
                    name = "Idempotency-Key",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "Client-supplied create key (max "
                            + ArticleConstraints.IDEMPOTENCY_KEY_MAX_LENGTH
                            + " chars). Scoped to create-article only.",
                    example = "create-hello-draft-001"
            )
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateArticleRequest request
    ) {
        CreateArticleResult created = articleService.create(idempotencyKey, request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path(created.locationPath())
                .build()
                .toUri();
        return ResponseEntity.created(location).body(created.body());
    }

    /**
     * Lists published articles only (drafts excluded). Offset pagination:
     * {@code page} defaults to 0, {@code size} defaults to 20 (max 100).
     */
    @GetMapping
    @Operation(
            summary = "List published articles",
            description = "Returns PUBLISHED articles only (drafts excluded). Ordered by publishedAt descending, id ascending."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Published article page",
                    content = @Content(schema = @Schema(implementation = ArticlePageResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination parameters",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public ArticlePageResponse listPublished(
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "" + ArticleConstraints.PAGE_DEFAULT) int page,
            @Parameter(description = "Page size (1–100)")
            @RequestParam(defaultValue = "" + ArticleConstraints.SIZE_DEFAULT) int size
    ) {
        return articleService.listPublished(page, size);
    }

    /**
     * Retrieves any article by id, including drafts.
     * Auth is deferred — treat as editorial workflow only, not production-secure.
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get article by id",
            description = "Returns any article including drafts. Auth is deferred — editorial workflow sample only."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Article found",
                    content = @Content(schema = @Schema(implementation = ArticleResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Malformed UUID",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Article not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public ArticleResponse getById(
            @Parameter(description = "Article id", required = true) @PathVariable UUID id
    ) {
        return articleService.getById(id);
    }

    /**
     * Publishes a draft. Already-published articles return 200 with the existing
     * article (original {@code publishedAt} preserved). Concurrent writers may receive 409
     * from optimistic locking.
     */
    @PostMapping("/{id}/publish")
    @Operation(
            summary = "Publish an article",
            description = "Transitions DRAFT → PUBLISHED and stamps publishedAt once. Already-published articles return 200 with the original publishedAt preserved. Concurrent conflicting updates return 409."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Article published (or already published)",
                    content = @Content(schema = @Schema(implementation = ArticleResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Malformed UUID",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Article not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Optimistic lock conflict",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public ArticleResponse publish(
            @Parameter(description = "Article id", required = true) @PathVariable UUID id
    ) {
        return articleService.publish(id);
    }

    /**
     * Replaces title and body on a DRAFT article.
     * Published articles return {@code 409 Conflict} (immutable in this sample).
     * Stale {@code expectedVersion} also returns {@code 409}.
     */
    @PutMapping("/{id}")
    @Operation(
            summary = "Update a draft article",
            description = "Replaces title and body on a DRAFT article. Requires expectedVersion from the last read. "
                    + "Published articles or stale/conflicting versions return 409 Conflict."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Draft updated",
                    content = @Content(schema = @Schema(implementation = ArticleResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed or malformed UUID",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Article not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Published articles cannot be edited, or article version conflict",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public ArticleResponse update(
            @Parameter(description = "Article id", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateArticleRequest request
    ) {
        return articleService.update(id, request);
    }

    /**
     * Returns a summary <em>preview</em> for a DRAFT article. Does not save the summary.
     * Published articles → {@code 409}; missing → {@code 404}; provider unavailable → {@code 503}.
     */
    @PostMapping("/{id}/summary-preview")
    @Operation(
            summary = "Preview a stub summary",
            description = "DRAFT-only preview. Does not persist. Stub provider returns a fixed labelled sample string."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Summary preview",
                    content = @Content(schema = @Schema(implementation = SummaryPreviewResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Malformed UUID",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Article not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Summary preview is only available for draft articles",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Summary provider unavailable",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public SummaryPreviewResponse previewSummary(
            @Parameter(description = "Article id", required = true) @PathVariable UUID id
    ) {
        return articleService.previewSummary(id);
    }
}
