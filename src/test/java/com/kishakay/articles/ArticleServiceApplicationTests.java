package com.kishakay.articles;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: context loads and connects when PostgreSQL is reachable
 * (e.g. via {@code docker compose up -d}).
 */
@SpringBootTest
class ArticleServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
