package web.tosunsaeng.domain.answerlearning;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class LearningOpenApiTest {
    private OpenAPI controllerPaths() {
        var api = new OpenAPI().paths(new Paths());
        for (var method : LearningController.class.getDeclaredMethods()) {
            String path = null;
            PathItem.HttpMethod verb = null;
            if (method.isAnnotationPresent(GetMapping.class)) {
                path = method.getAnnotation(GetMapping.class).value()[0]; verb = PathItem.HttpMethod.GET;
            } else if (method.isAnnotationPresent(PostMapping.class)) {
                path = method.getAnnotation(PostMapping.class).value()[0]; verb = PathItem.HttpMethod.POST;
            } else if (method.isAnnotationPresent(PutMapping.class)) {
                path = method.getAnnotation(PutMapping.class).value()[0]; verb = PathItem.HttpMethod.PUT;
            }
            if (path != null) {
                PathItem item = api.getPaths().computeIfAbsent("/api/v1/learning" + path, ignored -> new PathItem());
                item.operation(verb, new Operation().operationId(method.getName()));
            }
        }
        return api;
    }

    @Test void allTenControllerOperationsHaveTypedDocumentationWithoutChangingRoutes() throws Exception {
        var api = controllerPaths();
        var before = new HashSet<>(api.getPaths().keySet());
        new LearningOpenApiConfiguration().learningOpenApiCustomizer().customise(api);
        assertThat(api.getPaths().keySet()).containsExactlyInAnyOrderElementsOf(before).hasSize(10);
        api.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            assertThat(operation.getOperationId()).isNotBlank();
            assertThat(operation.getSummary()).isNotBlank();
            assertThat(operation.getDescription()).isNotBlank();
            assertThat(operation.getTags()).containsExactly("답변 학습");
            var schema = operation.getResponses().get("200").getContent().get("application/json").getSchema();
            assertThat(schema.getProperties().keySet()).containsExactlyInAnyOrder("isSuccess", "code", "message", "result");
            assertThat(((Schema<?>) schema.getProperties().get("result")).get$ref()).startsWith("#/components/schemas/Learning");
            assertThat(operation.getResponses()).containsKeys("400", "401", "403", "404", "503");
        }));
        String json = Json.mapper().writeValueAsString(api);
        assertThat(json).doesNotContain("userId", "bucket", "judgeResponse", "BaseResponseObject");
        var matcher = java.util.regex.Pattern.compile("#/components/schemas/([A-Za-z0-9]+)").matcher(json);
        while (matcher.find()) assertThat(api.getComponents().getSchemas()).containsKey(matcher.group(1));
    }

    @Test void disabledFeatureDoesNotAdvertiseEndpointsAndUnrelatedPathsStayUntouched() throws Exception {
        var api = new OpenAPI().paths(new Paths().addPathItem("/api/v1/exams", new PathItem().get(new Operation().summary("original"))));
        var customizer = new LearningOpenApiConfiguration().learningOpenApiCustomizer();
        customizer.customise(api);
        assertThat(api.getPaths()).hasSize(1);
        assertThat(api.getComponents()).isNull();
        assertThat(api.getPaths().get("/api/v1/exams").getGet().getSummary()).isEqualTo("original");
    }

    @Test void wordTimingJobStatesAndEmptyBodiesMatchCurrentWireContract() throws Exception {
        var api = controllerPaths();
        new LearningOpenApiConfiguration().learningOpenApiCustomizer().customise(api);
        var schemas = api.getComponents().getSchemas();
        assertThat(((Schema<?>) schemas.get("LearningSentence").getProperties().get("text")).getDescription()).contains("공백 하나", "정확히 일치");
        assertThat(schemas.get("LearningWord").getProperties().keySet()).containsExactlyInAnyOrder("text", "start", "end");
        assertThat(((Schema<String>) schemas.get("LearningJob").getProperties().get("status")).getEnum())
                .containsExactly("awaiting_audio", "queued", "transcribing", "grading", "completed", "failed", "no_speech");
        assertThat(schemas.get("LearningResult").getProperties().keySet())
                .containsExactlyInAnyOrder("checklistVersion", "transcript", "metCount", "totalCount", "items");
        for (String action : List.of("submit", "retry")) {
            var body = api.getPaths().get("/api/v1/learning/submissions/{id}/" + action).getPost().getRequestBody();
            assertThat(body.getRequired()).isFalse();
            assertThat(body.getContent().get("application/json").getSchema().get$ref()).endsWith("LearningEmpty");
        }
        assertThat(schemas.get("LearningEmpty").getAdditionalProperties()).isEqualTo(false);
        assertThat(schemas.get("LearningErrorResponse").getProperties()).doesNotContainKey("result");
    }
}
