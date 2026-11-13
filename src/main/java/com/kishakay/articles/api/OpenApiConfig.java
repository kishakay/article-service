package com.kishakay.articles.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI articleServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Article Management API")
                        .description(
                                "Interview code sample for creating, editing, publishing, "
                                        + "listing, and previewing stub summaries of articles. "
                                        + "POST /articles requires Idempotency-Key; PUT /articles/{id} "
                                        + "requires expectedVersion. No authentication — drafts are readable by id."
                        )
                        .version("0.0.1"));
    }
}
