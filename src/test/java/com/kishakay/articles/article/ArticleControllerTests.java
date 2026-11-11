package com.kishakay.articles.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for {@code POST /articles}.
 * Requires a reachable PostgreSQL instance (e.g. {@code docker compose up -d}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ArticleControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ArticleRepository articleRepository;

    @BeforeEach
    void cleanArticles() {
        articleRepository.deleteAll();
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
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(response.get("id").asText());

        Article persisted = articleRepository.findById(id).orElseThrow();
        assertThat(persisted.getTitle()).isEqualTo("Hello draft");
        assertThat(persisted.getBody()).isEqualTo("<p>First paragraph</p>");
        assertThat(persisted.getStatus()).isEqualTo(ArticleStatus.DRAFT);
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("body"));

        assertThat(articleRepository.count()).isZero();
    }
}
