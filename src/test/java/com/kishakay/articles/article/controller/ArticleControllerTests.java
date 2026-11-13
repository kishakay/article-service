package com.kishakay.articles.article.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kishakay.articles.article.domain.Article;
import com.kishakay.articles.article.domain.ArticleConstraints;
import com.kishakay.articles.article.domain.ArticleStatus;
import com.kishakay.articles.article.dto.CreateArticleRequest;
import com.kishakay.articles.article.exception.SummaryProviderUnavailableException;
import com.kishakay.articles.article.repository.ArticleRepository;
import com.kishakay.articles.article.summary.StubSummaryProvider;
import com.kishakay.articles.support.AbstractPostgresIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for article create / retrieve / publish / edit / list /
 * summary-preview / author-approved summary save.
 * Uses Testcontainers PostgreSQL via {@link AbstractPostgresIntegrationTest}.
 */
class ArticleControllerTests extends AbstractPostgresIntegrationTest {

    private static final AtomicInteger KEY_SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Spy over the real stub so happy-path previews stay deterministic while
     * missing/published/503 cases can assert invocation / force unavailable failures.
     */
    @MockitoSpyBean
    private StubSummaryProvider summaryProvider;

    @BeforeEach
    void cleanArticles() {
        articleRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM idempotency_records");
        reset(summaryProvider);
    }

    @Test
    void createArticle_returns201WithLocationAndDraftBody() throws Exception {
        String payload = """
                {
                  "title": "Hello draft",
                  "body": "<p>First paragraph</p>"
                }
                """;

        MvcResult result = mockMvc.perform(post("/articles")
                        .header("Idempotency-Key", nextKey("create-hello"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(".*/articles/[0-9a-fA-F-]{36}")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Hello draft"))
                .andExpect(jsonPath("$.body").value("<p>First paragraph</p>"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.publishedAt").value(nullValue()))
                .andExpect(jsonPath("$.summary").value(nullValue()))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(response.get("id").asText());

        Article persisted = articleRepository.findById(id).orElseThrow();
        assertThat(persisted.getTitle()).isEqualTo("Hello draft");
        assertThat(persisted.getBody()).isEqualTo("<p>First paragraph</p>");
        assertThat(persisted.getStatus()).isEqualTo(ArticleStatus.DRAFT);
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
        assertThat(persisted.getPublishedAt()).isNull();
        assertThat(persisted.getSummary()).isNull();
        assertThat(persisted.getVersion()).isEqualTo(0L);
    }

    @Test
    void createArticle_rejectsMissingIdempotencyKey() throws Exception {
        String payload = """
                {
                  "title": "No key",
                  "body": "<p>ok</p>"
                }
                """;

        mockMvc.perform(post("/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid Idempotency-Key"))
                .andExpect(jsonPath("$.violations[0].field").value("Idempotency-Key"));

        assertThat(articleRepository.count()).isZero();
    }

    @Test
    void createArticle_rejectsBlankTitle() throws Exception {
        String payload = """
                {
                  "title": "   ",
                  "body": "<p>ok</p>"
                }
                """;

        mockMvc.perform(post("/articles")
                        .header("Idempotency-Key", nextKey("blank-title"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.violations[0].field").value("title"));

        assertThat(articleRepository.count()).isZero();
    }

    @Test
    void createArticle_rejectsBlankBody() throws Exception {
        String payload = """
                {
                  "title": "A title",
                  "body": ""
                }
                """;

        mockMvc.perform(post("/articles")
                        .header("Idempotency-Key", nextKey("blank-body"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("body"));

        assertThat(articleRepository.count()).isZero();
    }

    @Test
    void createArticle_rejectsTitleLongerThanMax() throws Exception {
        String tooLongTitle = "t".repeat(ArticleConstraints.TITLE_MAX_LENGTH + 1);
        String payload = """
                {
                  "title": "%s",
                  "body": "<p>ok</p>"
                }
                """.formatted(tooLongTitle);

        mockMvc.perform(post("/articles")
                        .header("Idempotency-Key", nextKey("long-title"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("title"));

        assertThat(articleRepository.count()).isZero();
    }

    @Test
    void createArticle_rejectsBodyLongerThanMax() throws Exception {
        String tooLongBody = "b".repeat(ArticleConstraints.BODY_MAX_LENGTH + 1);
        String payload = objectMapper.writeValueAsString(
                new CreateArticleRequest("Within limit", tooLongBody)
        );

        mockMvc.perform(post("/articles")
                        .header("Idempotency-Key", nextKey("long-body"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("body"));

        assertThat(articleRepository.count()).isZero();
    }

    @Test
    void getArticle_returnsDraftIncludingNullPublishedAt() throws Exception {
        UUID id = createDraft("Retrieve me", "<p>body</p>");

        mockMvc.perform(get("/articles/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("Retrieve me"))
                .andExpect(jsonPath("$.body").value("<p>body</p>"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.publishedAt").value(nullValue()))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void getArticle_returns404ForUnknownId() throws Exception {
        UUID missing = UUID.randomUUID();

        mockMvc.perform(get("/articles/{id}", missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Article not found"))
                .andExpect(jsonPath("$.path").value("/articles/" + missing));
    }

    @Test
    void getArticle_returns400ForMalformedUuid() throws Exception {
        mockMvc.perform(get("/articles/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed UUID"))
                .andExpect(jsonPath("$.violations[0].field").value("id"));
    }

    @Test
    void publishArticle_transitionsDraftToPublishedAndSetsPublishedAt() throws Exception {
        UUID id = createDraft("Ready to ship", "<p>content</p>");

        MvcResult result = mockMvc.perform(post("/articles/{id}/publish", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        Instant publishedAt = Instant.parse(response.get("publishedAt").asText());

        Article persisted = articleRepository.findById(id).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(persisted.getPublishedAt()).isEqualTo(publishedAt);

        mockMvc.perform(get("/articles/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").value(publishedAt.toString()));
    }

    @Test
    void publishArticle_whenAlreadyPublished_isIdempotentAndPreservesPublishedAt() throws Exception {
        UUID id = createDraft("Ship once", "<p>once</p>");

        MvcResult first = mockMvc.perform(post("/articles/{id}/publish", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andReturn();

        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        String originalPublishedAt = firstBody.get("publishedAt").asText();
        long versionAfterFirstPublish = firstBody.get("version").asLong();

        // Ensure clock would differ if we stamped again
        Thread.sleep(15);

        mockMvc.perform(post("/articles/{id}/publish", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").value(originalPublishedAt))
                .andExpect(jsonPath("$.version").value(versionAfterFirstPublish));

        Article persisted = articleRepository.findById(id).orElseThrow();
        assertThat(persisted.getPublishedAt()).isEqualTo(Instant.parse(originalPublishedAt));
        assertThat(persisted.getVersion()).isEqualTo(versionAfterFirstPublish);
    }

    @Test
    void publishArticle_returns404ForUnknownId() throws Exception {
        UUID missing = UUID.randomUUID();

        mockMvc.perform(post("/articles/{id}/publish", missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Article not found"));
    }

    @Test
    void publishArticle_returns400ForMalformedUuid() throws Exception {
        mockMvc.perform(post("/articles/{id}/publish", "bad-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed UUID"));
    }

    @Test
    void editArticle_updatesDraftAndPersistsChanges() throws Exception {
        UUID id = createDraft("Original title", "<p>original</p>");

        Article before = articleRepository.findById(id).orElseThrow();
        Instant originalCreatedAt = before.getCreatedAt();
        Instant originalUpdatedAt = before.getUpdatedAt();
        long originalVersion = before.getVersion();

        // Ensure updatedAt will differ after edit
        Thread.sleep(15);

        String payload = """
                {
                  "title": "Revised title",
                  "body": "<p>revised</p>",
                  "expectedVersion": %d
                }
                """.formatted(originalVersion);

        mockMvc.perform(put("/articles/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("Revised title"))
                .andExpect(jsonPath("$.body").value("<p>revised</p>"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.publishedAt").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").value(originalCreatedAt.toString()))
                .andExpect(jsonPath("$.version").value(originalVersion + 1));

        mockMvc.perform(get("/articles/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Revised title"))
                .andExpect(jsonPath("$.body").value("<p>revised</p>"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdAt").value(originalCreatedAt.toString()));

        Article persisted = articleRepository.findById(id).orElseThrow();
        assertThat(persisted.getTitle()).isEqualTo("Revised title");
        assertThat(persisted.getBody()).isEqualTo("<p>revised</p>");
        assertThat(persisted.getStatus()).isEqualTo(ArticleStatus.DRAFT);
        assertThat(persisted.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(persisted.getUpdatedAt()).isAfter(originalUpdatedAt);
        assertThat(persisted.getPublishedAt()).isNull();
        assertThat(persisted.getVersion()).isEqualTo(originalVersion + 1);
    }

    @Test
    void editArticle_rejectsInvalidInputAndLeavesArticleUnchanged() throws Exception {
        UUID id = createDraft("Keep me", "<p>unchanged</p>");

        Article before = articleRepository.findById(id).orElseThrow();
        Instant originalUpdatedAt = before.getUpdatedAt();
        long originalVersion = before.getVersion();

        String payload = """
                {
                  "title": "   ",
                  "body": "<p>should not save</p>",
                  "expectedVersion": %d
                }
                """.formatted(originalVersion);

        mockMvc.perform(put("/articles/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.violations[0].field").value("title"));

        Article persisted = articleRepository.findById(id).orElseThrow();
        assertThat(persisted.getTitle()).isEqualTo("Keep me");
        assertThat(persisted.getBody()).isEqualTo("<p>unchanged</p>");
        assertThat(persisted.getStatus()).isEqualTo(ArticleStatus.DRAFT);
        assertThat(persisted.getUpdatedAt()).isEqualTo(originalUpdatedAt);
        assertThat(persisted.getVersion()).isEqualTo(originalVersion);

        mockMvc.perform(get("/articles/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Keep me"))
                .andExpect(jsonPath("$.body").value("<p>unchanged</p>"));
    }

    @Test
    void editArticle_rejectsStaleExpectedVersionWithoutMutation() throws Exception {
        UUID id = createDraft("Versioned", "<p>v0</p>");

        mockMvc.perform(put("/articles/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "First edit",
                                  "body": "<p>v1</p>",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        Article before = articleRepository.findById(id).orElseThrow();
        Instant originalUpdatedAt = before.getUpdatedAt();

        mockMvc.perform(put("/articles/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Stale rewrite",
                                  "body": "<p>should not apply</p>",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Article version conflict"))
                .andExpect(jsonPath("$.path").value("/articles/" + id));

        Article persisted = articleRepository.findById(id).orElseThrow();
        assertThat(persisted.getTitle()).isEqualTo("First edit");
        assertThat(persisted.getBody()).isEqualTo("<p>v1</p>");
        assertThat(persisted.getVersion()).isEqualTo(1L);
        assertThat(persisted.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void editArticle_rejectsPublishedWithoutChangingIt() throws Exception {
        UUID id = createDraft("Ship then lock", "<p>final</p>");

        mockMvc.perform(post("/articles/{id}/publish", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        Article before = articleRepository.findById(id).orElseThrow();
        Instant originalUpdatedAt = before.getUpdatedAt();
        Instant originalPublishedAt = before.getPublishedAt();
        long originalVersion = before.getVersion();

        String payload = """
                {
                  "title": "Attempted rewrite",
                  "body": "<p>should not apply</p>",
                  "expectedVersion": %d
                }
                """.formatted(originalVersion);

        mockMvc.perform(put("/articles/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Published articles cannot be edited"))
                .andExpect(jsonPath("$.path").value("/articles/" + id));

        Article persisted = articleRepository.findById(id).orElseThrow();
        assertThat(persisted.getTitle()).isEqualTo("Ship then lock");
        assertThat(persisted.getBody()).isEqualTo("<p>final</p>");
        assertThat(persisted.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(persisted.getUpdatedAt()).isEqualTo(originalUpdatedAt);
        assertThat(persisted.getPublishedAt()).isEqualTo(originalPublishedAt);
        assertThat(persisted.getVersion()).isEqualTo(originalVersion);

        mockMvc.perform(get("/articles/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Ship then lock"))
                .andExpect(jsonPath("$.body").value("<p>final</p>"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void editArticle_returns404ForUnknownId() throws Exception {
        UUID missing = UUID.randomUUID();
        String payload = """
                {
                  "title": "Does not matter",
                  "body": "<p>missing</p>",
                  "expectedVersion": 0
                }
                """;

        mockMvc.perform(put("/articles/{id}", missing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Article not found"))
                .andExpect(jsonPath("$.path").value("/articles/" + missing));
    }

    @Test
    void listArticles_excludesDraftsAndOmitsBody() throws Exception {
        UUID draftId = createDraft("Still drafting", "<p>secret draft body</p>");
        UUID publishedId = createAndPublish("Live article", "<p>published body</p>");

        mockMvc.perform(get("/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(publishedId.toString()))
                .andExpect(jsonPath("$.items[0].title").value("Live article"))
                .andExpect(jsonPath("$.items[0].publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].body").doesNotExist())
                .andExpect(jsonPath("$.items[0].status").doesNotExist());

        // Draft remains retrievable by id (GET /articles/{id} unchanged)
        mockMvc.perform(get("/articles/{id}", draftId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void listArticles_paginatesWithCorrectItemsAndTotals() throws Exception {
        List<UUID> newestFirst = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            newestFirst.add(0, createAndPublish("Article " + i, "<p>" + i + "</p>"));
            Thread.sleep(15);
        }

        mockMvc.perform(get("/articles").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(newestFirst.get(0).toString()))
                .andExpect(jsonPath("$.items[1].id").value(newestFirst.get(1).toString()));

        mockMvc.perform(get("/articles").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(newestFirst.get(2).toString()))
                .andExpect(jsonPath("$.items[1].id").value(newestFirst.get(3).toString()));

        mockMvc.perform(get("/articles").param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(newestFirst.get(4).toString()));
    }

    @Test
    void listArticles_ordersByPublishedAtDescWithStableIdTieBreaker() throws Exception {
        UUID first = createAndPublish("Alpha", "<p>a</p>");
        UUID second = createAndPublish("Beta", "<p>b</p>");

        Instant sharedPublishedAt = Instant.parse("2024-06-15T12:00:00Z");
        jdbcTemplate.update(
                "UPDATE articles SET published_at = ? WHERE id IN (?, ?)",
                Timestamp.from(sharedPublishedAt),
                first,
                second
        );

        List<UUID> expectedOrder = jdbcTemplate.query(
                """
                        SELECT id FROM articles
                        WHERE status = 'PUBLISHED'
                        ORDER BY published_at DESC, id ASC
                        """,
                (rs, rowNum) -> rs.getObject("id", UUID.class)
        );
        assertThat(expectedOrder).containsExactlyInAnyOrder(first, second);
        assertThat(expectedOrder).hasSize(2);

        MvcResult firstCall = mockMvc.perform(get("/articles").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].id").value(expectedOrder.get(0).toString()))
                .andExpect(jsonPath("$.items[1].id").value(expectedOrder.get(1).toString()))
                .andReturn();

        MvcResult secondCall = mockMvc.perform(get("/articles").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(expectedOrder.get(0).toString()))
                .andExpect(jsonPath("$.items[1].id").value(expectedOrder.get(1).toString()))
                .andReturn();

        assertThat(firstCall.getResponse().getContentAsString())
                .isEqualTo(secondCall.getResponse().getContentAsString());
    }

    @Test
    void listArticles_returnsEmptyItemsWhenNoPublishedArticles() throws Exception {
        createDraft("Draft only", "<p>not listed</p>");

        mockMvc.perform(get("/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void listArticles_rejectsNegativePage() throws Exception {
        mockMvc.perform(get("/articles").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid pagination parameters"))
                .andExpect(jsonPath("$.path").value("/articles"))
                .andExpect(jsonPath("$.violations[0].field").value("page"));
    }

    @Test
    void listArticles_rejectsSizeOutsideAllowedRange() throws Exception {
        mockMvc.perform(get("/articles").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid pagination parameters"))
                .andExpect(jsonPath("$.violations[0].field").value("size"));

        mockMvc.perform(get("/articles").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid pagination parameters"))
                .andExpect(jsonPath("$.violations[0].field").value("size"));
    }

    @Test
    void summaryPreview_returnsStubPreviewForDraft() throws Exception {
        UUID id = createDraft("Preview me", "<p>draft body for preview</p>");

        mockMvc.perform(post("/articles/{id}/summary-preview", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleId").value(id.toString()))
                .andExpect(jsonPath("$.summary").value(StubSummaryProvider.SAMPLE_SUMMARY))
                .andExpect(jsonPath("$.provider").value(StubSummaryProvider.PROVIDER_ID));

        verify(summaryProvider).summarize(anyString());
    }

    @Test
    void summaryPreview_doesNotChangePersistedArticle() throws Exception {
        UUID id = createDraft("Leave me alone", "<p>unchanged body</p>");

        Article before = articleRepository.findById(id).orElseThrow();
        Instant originalCreatedAt = before.getCreatedAt();
        Instant originalUpdatedAt = before.getUpdatedAt();
        String originalTitle = before.getTitle();
        String originalBody = before.getBody();
        long originalVersion = before.getVersion();

        mockMvc.perform(post("/articles/{id}/summary-preview", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("stub"));

        Article after = articleRepository.findById(id).orElseThrow();
        assertThat(after.getTitle()).isEqualTo(originalTitle);
        assertThat(after.getBody()).isEqualTo(originalBody);
        assertThat(after.getStatus()).isEqualTo(ArticleStatus.DRAFT);
        assertThat(after.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(after.getUpdatedAt()).isEqualTo(originalUpdatedAt);
        assertThat(after.getPublishedAt()).isNull();
        assertThat(after.getSummary()).isNull();
        assertThat(after.getVersion()).isEqualTo(originalVersion);

        mockMvc.perform(get("/articles/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(originalTitle))
                .andExpect(jsonPath("$.body").value(originalBody))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.summary").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").value(originalCreatedAt.toString()))
                .andExpect(jsonPath("$.updatedAt").value(originalUpdatedAt.toString()));
    }

    @Test
    void summaryPreview_doesNotClearSavedSummary() throws Exception {
        UUID id = createDraft("Keep summary", "<p>body</p>");
        saveApprovedSummary(id, 0L, "Approved text");

        Article before = articleRepository.findById(id).orElseThrow();
        Instant originalUpdatedAt = before.getUpdatedAt();
        long originalVersion = before.getVersion();

        mockMvc.perform(post("/articles/{id}/summary-preview", id))
                .andExpect(status().isOk());

        Article after = articleRepository.findById(id).orElseThrow();
        assertThat(after.getSummary()).isEqualTo("Approved text");
        assertThat(after.getUpdatedAt()).isEqualTo(originalUpdatedAt);
        assertThat(after.getVersion()).isEqualTo(originalVersion);
    }

    @Test
    void saveSummary_persistsAndIsReturnedOnGet() throws Exception {
        UUID id = createDraft("Summarise me", "<p>body</p>");

        Article before = articleRepository.findById(id).orElseThrow();
        Instant originalCreatedAt = before.getCreatedAt();
        Instant originalUpdatedAt = before.getUpdatedAt();
        long originalVersion = before.getVersion();

        Thread.sleep(15);

        mockMvc.perform(put("/articles/{id}/summary", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summary": "Reviewed summary text",
                                  "expectedVersion": %d
                                }
                                """.formatted(originalVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.summary").value("Reviewed summary text"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(originalVersion + 1))
                .andExpect(jsonPath("$.createdAt").value(originalCreatedAt.toString()));

        mockMvc.perform(get("/articles/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Reviewed summary text"))
                .andExpect(jsonPath("$.version").value(originalVersion + 1));

        Article persisted = articleRepository.findById(id).orElseThrow();
        assertThat(persisted.getSummary()).isEqualTo("Reviewed summary text");
        assertThat(persisted.getUpdatedAt()).isAfter(originalUpdatedAt);
        assertThat(persisted.getVersion()).isEqualTo(originalVersion + 1);
    }

    @Test
    void saveSummary_rejectsBlankAndOverlongWithoutMutation() throws Exception {
        UUID id = createDraft("Validate summary", "<p>body</p>");
        Article before = articleRepository.findById(id).orElseThrow();
        Instant originalUpdatedAt = before.getUpdatedAt();
        long originalVersion = before.getVersion();

        mockMvc.perform(put("/articles/{id}/summary", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summary": "   ",
                                  "expectedVersion": %d
                                }
                                """.formatted(originalVersion)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.violations[0].field").value("summary"));

        String tooLong = "x".repeat(ArticleConstraints.SUMMARY_MAX_LENGTH + 1);
        mockMvc.perform(put("/articles/{id}/summary", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summary": "%s",
                                  "expectedVersion": %d
                                }
                                """.formatted(tooLong, originalVersion)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.violations[0].field").value("summary"));

        Article persisted = articleRepository.findById(id).orElseThrow();
        assertThat(persisted.getSummary()).isNull();
        assertThat(persisted.getUpdatedAt()).isEqualTo(originalUpdatedAt);
        assertThat(persisted.getVersion()).isEqualTo(originalVersion);
    }

    @Test
    void saveSummary_rejectsMissingExpectedVersion() throws Exception {
        UUID id = createDraft("Need version", "<p>body</p>");

        mockMvc.perform(put("/articles/{id}/summary", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summary": "No version field"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.violations[0].field").value("expectedVersion"));
    }

    @Test
    void saveSummary_rejectsStaleExpectedVersionWithoutMutation() throws Exception {
        UUID id = createDraft("Stale summary", "<p>body</p>");
        saveApprovedSummary(id, 0L, "First approved");

        Article before = articleRepository.findById(id).orElseThrow();
        Instant originalUpdatedAt = before.getUpdatedAt();

        mockMvc.perform(put("/articles/{id}/summary", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summary": "Should not apply",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Article version conflict"))
                .andExpect(jsonPath("$.path").value("/articles/" + id + "/summary"));

        Article persisted = articleRepository.findById(id).orElseThrow();
        assertThat(persisted.getSummary()).isEqualTo("First approved");
        assertThat(persisted.getVersion()).isEqualTo(1L);
        assertThat(persisted.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void saveSummary_rejectsPublishedWithoutChangingIt() throws Exception {
        UUID id = createAndPublish("Published summary lock", "<p>final</p>");
        Article before = articleRepository.findById(id).orElseThrow();
        Instant originalUpdatedAt = before.getUpdatedAt();
        long originalVersion = before.getVersion();

        mockMvc.perform(put("/articles/{id}/summary", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summary": "Should not save",
                                  "expectedVersion": %d
                                }
                                """.formatted(originalVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Published articles cannot be edited"))
                .andExpect(jsonPath("$.path").value("/articles/" + id + "/summary"));

        Article persisted = articleRepository.findById(id).orElseThrow();
        assertThat(persisted.getSummary()).isNull();
        assertThat(persisted.getUpdatedAt()).isEqualTo(originalUpdatedAt);
        assertThat(persisted.getVersion()).isEqualTo(originalVersion);
    }

    @Test
    void saveSummary_returns404ForUnknownId() throws Exception {
        UUID missing = UUID.randomUUID();

        mockMvc.perform(put("/articles/{id}/summary", missing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summary": "Does not matter",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Article not found"))
                .andExpect(jsonPath("$.path").value("/articles/" + missing + "/summary"));
    }

    @Test
    void editArticle_clearsSummaryWhenBodyChangesWithSingleVersionIncrement() throws Exception {
        UUID id = createDraft("Clear on body", "<p>original</p>");
        saveApprovedSummary(id, 0L, "Will be cleared");

        Article before = articleRepository.findById(id).orElseThrow();
        assertThat(before.getSummary()).isEqualTo("Will be cleared");
        assertThat(before.getVersion()).isEqualTo(1L);
        Instant originalUpdatedAt = before.getUpdatedAt();

        Thread.sleep(15);

        mockMvc.perform(put("/articles/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Clear on body",
                                  "body": "<p>changed body</p>",
                                  "expectedVersion": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value(nullValue()))
                .andExpect(jsonPath("$.body").value("<p>changed body</p>"))
                .andExpect(jsonPath("$.version").value(2));

        Article persisted = articleRepository.findById(id).orElseThrow();
        assertThat(persisted.getSummary()).isNull();
        assertThat(persisted.getBody()).isEqualTo("<p>changed body</p>");
        assertThat(persisted.getVersion()).isEqualTo(2L);
        assertThat(persisted.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    void editArticle_retainsSummaryOnTitleOnlyEdit() throws Exception {
        UUID id = createDraft("Old title", "<p>same body</p>");
        saveApprovedSummary(id, 0L, "Keep me");

        mockMvc.perform(put("/articles/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "New title",
                                  "body": "<p>same body</p>",
                                  "expectedVersion": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.summary").value("Keep me"))
                .andExpect(jsonPath("$.version").value(2));

        Article persisted = articleRepository.findById(id).orElseThrow();
        assertThat(persisted.getSummary()).isEqualTo("Keep me");
        assertThat(persisted.getTitle()).isEqualTo("New title");
        assertThat(persisted.getVersion()).isEqualTo(2L);
    }

    @Test
    void editArticle_retainsSummaryWhenBodyUnchanged() throws Exception {
        UUID id = createDraft("Same title", "<p>unchanged body</p>");
        saveApprovedSummary(id, 0L, "Still valid");

        mockMvc.perform(put("/articles/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Same title",
                                  "body": "<p>unchanged body</p>",
                                  "expectedVersion": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Still valid"));

        Article persisted = articleRepository.findById(id).orElseThrow();
        assertThat(persisted.getSummary()).isEqualTo("Still valid");
    }

    @Test
    void summaryPreview_returns404ForMissingAndDoesNotInvokeProvider() throws Exception {
        UUID missing = UUID.randomUUID();

        mockMvc.perform(post("/articles/{id}/summary-preview", missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Article not found"))
                .andExpect(jsonPath("$.path").value("/articles/" + missing + "/summary-preview"));

        verify(summaryProvider, never()).summarize(anyString());
    }

    @Test
    void summaryPreview_returns409ForPublishedAndDoesNotInvokeProvider() throws Exception {
        UUID id = createAndPublish("Already live", "<p>published</p>");

        mockMvc.perform(post("/articles/{id}/summary-preview", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Summary preview is only available for draft articles"))
                .andExpect(jsonPath("$.path").value("/articles/" + id + "/summary-preview"));

        verify(summaryProvider, never()).summarize(anyString());
    }

    @Test
    void summaryPreview_returns503WhenProviderUnavailable() throws Exception {
        UUID id = createDraft("Provider down", "<p>still a draft</p>");

        doThrow(new SummaryProviderUnavailableException("simulated outage"))
                .when(summaryProvider).summarize(anyString());

        mockMvc.perform(post("/articles/{id}/summary-preview", id))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Summary provider unavailable"))
                .andExpect(jsonPath("$.path").value("/articles/" + id + "/summary-preview"));

        Article persisted = articleRepository.findById(id).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(ArticleStatus.DRAFT);
        assertThat(persisted.getTitle()).isEqualTo("Provider down");
    }

    private UUID createDraft(String title, String body) throws Exception {
        String payload = objectMapper.writeValueAsString(new CreateArticleRequest(title, body));
        MvcResult result = mockMvc.perform(post("/articles")
                        .header("Idempotency-Key", nextKey("draft"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(response.get("id").asText());
    }

    private UUID createAndPublish(String title, String body) throws Exception {
        UUID id = createDraft(title, body);
        mockMvc.perform(post("/articles/{id}/publish", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        return id;
    }

    private void saveApprovedSummary(UUID id, long expectedVersion, String summary) throws Exception {
        mockMvc.perform(put("/articles/{id}/summary", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summary": %s,
                                  "expectedVersion": %d
                                }
                                """.formatted(objectMapper.writeValueAsString(summary), expectedVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value(summary));
    }

    private static String nextKey(String prefix) {
        return prefix + "-" + KEY_SEQ.incrementAndGet();
    }
}
