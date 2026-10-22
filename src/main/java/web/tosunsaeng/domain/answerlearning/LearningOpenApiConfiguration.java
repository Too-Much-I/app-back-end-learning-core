package web.tosunsaeng.domain.answerlearning;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

/** Documentation only: preserve controller serialization and feature-off endpoint visibility. */
@Configuration
public class LearningOpenApiConfiguration {
    @Bean
    OpenApiCustomizer learningOpenApiCustomizer() throws IOException {
        final OpenAPI fragment;
        try (var stream = new ClassPathResource("openapi/answer-learning.json").getInputStream()) {
            fragment = Json.mapper().readValue(stream, OpenAPI.class);
        }
        return api -> {
            if (api.getPaths() == null || !api.getPaths().containsKey("/api/v1/learning/units")) return;
            if (api.getComponents() == null) api.setComponents(new Components());
            fragment.getComponents().getSchemas().forEach(api.getComponents()::addSchemas);
            fragment.getTags().forEach(api::addTagsItem);
            fragment.getPaths().forEach((path, docs) -> {
                var existing = api.getPaths().get(path);
                if (existing == null) return;
                docs.readOperationsMap().forEach((method, documented) -> {
                    var operation = existing.readOperationsMap().get(method);
                    if (operation == null) return;
                    operation.setTags(documented.getTags());
                    operation.setSummary(documented.getSummary());
                    operation.setDescription(documented.getDescription());
                    operation.setParameters(documented.getParameters());
                    operation.setResponses(documented.getResponses());
                    operation.setRequestBody(documented.getRequestBody());
                });
            });
        };
    }
}
