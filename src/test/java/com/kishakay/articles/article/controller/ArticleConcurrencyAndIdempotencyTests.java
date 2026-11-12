package com.kishakay.articles.article.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kishakay.articles.article.domain.Article;
import com.kishakay.articles.article.domain.ArticleConstraints;
import com.kishakay.articles.article.dto.CreateArticleRequest;
import com.kishakay.articles.article.repository.ArticleRepository;
import com.kishakay.articles.support.AbstractPostgresIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * PostgreSQL-backed concurrency and idempotency proofs (Testcontainers).
 * Concurrent cases use barriers/latches — not sleeps — to coordinate threads.
 */
class ArticleConcurrencyAndIdempotencyTests extends AbstractPostgresIntegrationTest {

    private static final AtomicInteger KEY_SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private ArticleRepository articleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        reset(articleRepository);
        articleRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM idempotency_records");
    }

    @Test
    void create_sameKeyAndPayload_createsOnceAndReplaysOriginalResponse() throws Exception {
        String key = nextKey("replay");
        String payload = """
                {
                  "title": "Idempotent draft",
                  "body": "<p>same payload</p>"
                }
                """;

        MvcResult first = mockMvc.perform(post("/articles")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(".*/articles/[0-9a-fA-F-]{36}")))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        String firstBody = first.getResponse().getContentAsString();
        String firstLocation = first.getResponse().getHeader("Location");
        JsonNode firstJson = objectMapper.readTree(firstBody);
        UUID id = UUID.fromString(firstJson.get("id").asText());

        MvcResult second = mockMvc.perform(post("/articles")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", firstLocation))
                .andReturn();

        assertThat(second.getResponse().getContentAsString()).isEqualTo(firstBody);
        assertThat(articleRepository.count()).isEqualTo(1);
        assertThat(articleRepository.findById(id)).isPresent();
        assertThat(countIdempotencyRows()).isEqualTo(1);
    }

    @Test
    void create_sameKeyDifferentPayload_returns409() throws Exception {
        String key = nextKey("conflict");

        mockMvc.perform(post("/articles")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"First","body":"<p>one</p>"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/articles")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Second","body":"<p>two</p>"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Idempotency key reused with a different payload"));

        assertThat(articleRepository.count()).isEqualTo(1);
    }

    @Test
    void create_differentKeys_mayCreateIdenticalContent() throws Exception {
        String payload = """
                {"title":"Twins","body":"<p>identical</p>"}
                """;

        mockMvc.perform(post("/articles")
                        .header("Idempotency-Key", nextKey("twin-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/articles")
                        .header("Idempotency-Key", nextKey("twin-b"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        assertThat(articleRepository.count()).isEqualTo(2);
    }

    @Test
    void create_rejectsBlankAndOversizedIdempotencyKey() throws Exception {
        String payload = """
                {"title":"Key rules","body":"<p>ok</p>"}
                """;

        mockMvc.perform(post("/articles")
                        .header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid Idempotency-Key"))
                .andExpect(jsonPath("$.violations[0].field").value("Idempotency-Key"));

        String tooLong = "k".repeat(ArticleConstraints.IDEMPOTENCY_KEY_MAX_LENGTH + 1);
        mockMvc.perform(post("/articles")
                        .header("Idempotency-Key", tooLong)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid Idempotency-Key"));

        assertThat(articleRepository.count()).isZero();
        assertThat(countIdempotencyRows()).isZero();
    }

    @Test
    void create_simultaneousSameKey_createsExactlyOneArticle() throws Exception {
        int threads = 8;
        String key = nextKey("concurrent-create");
        String payload = """
                {"title":"Race create","body":"<p>one wins</p>"}
                """;

        CyclicBarrier ready = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<MvcResult>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.await(10, TimeUnit.SECONDS);
                    try {
                        return mockMvc.perform(post("/articles")
                                        .header("Idempotency-Key", key)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(payload))
                                .andReturn();
                    } finally {
                        done.countDown();
                    }
                }));
            }

            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

            List<String> bodies = new ArrayList<>();
            List<String> locations = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get(5, TimeUnit.SECONDS);
                assertThat(result.getResponse().getStatus()).isEqualTo(201);
                bodies.add(result.getResponse().getContentAsString());
                locations.add(result.getResponse().getHeader("Location"));
            }

            assertThat(bodies.stream().distinct()).hasSize(1);
            assertThat(locations.stream().distinct()).hasSize(1);
            assertThat(articleRepository.count()).isEqualTo(1);
            assertThat(countIdempotencyRows()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void create_transactionFailure_rollsBackArticleAndIdempotencyRecord() {
        doThrow(new DataAccessResourceFailureException("simulated write failure"))
                .when(articleRepository)
                .saveAndFlush(any(Article.class));

        try {
            mockMvc.perform(post("/articles")
                            .header("Idempotency-Key", nextKey("rollback"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title":"Should roll back","body":"<p>no persist</p>"}
                                    """))
                    .andReturn();
            throw new AssertionError("Expected create to fail when article persistence throws");
        } catch (Exception ex) {
            assertThat(ex).hasRootCauseInstanceOf(DataAccessResourceFailureException.class);
        } finally {
            reset(articleRepository);
        }

        assertThat(articleRepository.count()).isZero();
        assertThat(countIdempotencyRows()).isZero();
    }

    @Test
    void update_competingEdits_onlyOneSucceedsForSameVersion() throws Exception {
        UUID id = createDraft("Contend", "<p>start</p>");
        long expectedVersion = articleRepository.findById(id).orElseThrow().getVersion();

        int threads = 2;
        CyclicBarrier ready = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    ready.await(10, TimeUnit.SECONDS);
                    try {
                        MvcResult result = mockMvc.perform(put("/articles/{id}", id)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {
                                                  "title": "Writer %d",
                                                  "body": "<p>writer %d</p>",
                                                  "expectedVersion": %d
                                                }
                                                """.formatted(idx, idx, expectedVersion)))
                                .andReturn();
                        int status = result.getResponse().getStatus();
                        if (status == 200) {
                            successes.incrementAndGet();
                        } else if (status == 409) {
                            conflicts.incrementAndGet();
                            assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                                    .get("message").asText())
                                    .isEqualTo("Article version conflict");
                        } else {
                            throw new IllegalStateException("Unexpected status " + status);
                        }
                    } finally {
                        done.countDown();
                    }
                    return null;
                }));
            }

            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }

            assertThat(successes.get()).isEqualTo(1);
            assertThat(conflicts.get()).isEqualTo(1);

            Article persisted = articleRepository.findById(id).orElseThrow();
            assertThat(persisted.getVersion()).isEqualTo(expectedVersion + 1);
            assertThat(persisted.getTitle()).startsWith("Writer ");
        } finally {
            pool.shutdownNow();
        }
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

    private long countIdempotencyRows() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotency_records", Long.class);
        return count == null ? 0L : count;
    }

    private static String nextKey(String prefix) {
        return prefix + "-" + KEY_SEQ.incrementAndGet();
    }
}
