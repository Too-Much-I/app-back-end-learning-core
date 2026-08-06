package web.tosunsaeng.domain.exams;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.mapping.Field;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionTableImageMappingTest {

    private static final String TABLE_IMAGE_URL =
            "https://cdn.example.com/mock-exam/001/part4/q8.png";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mongoTableImageUrlUsesSnakeCaseFieldName() throws NoSuchFieldException {
        java.lang.reflect.Field javaField = Question.class.getDeclaredField("tableImageUrl");
        Field mongoField = javaField.getAnnotation(Field.class);

        assertAll(
                () -> assertNotNull(mongoField),
                () -> assertEquals("table_image_url", mongoField.value())
        );
    }

    @Test
    void existingStructuredTableContextMappingRemainsAvailableInternally() throws NoSuchFieldException {
        java.lang.reflect.Field javaField = Question.class.getDeclaredField("tableContext");
        Field mongoField = javaField.getAnnotation(Field.class);

        assertAll(
                () -> assertNotNull(mongoField),
                () -> assertEquals("table_context", mongoField.value())
        );
    }

    @Test
    void responseSerializesTableImageUrlAsCamelCaseWithoutRewritingValue() {
        ExamResponseDTO.QuestionDTO questionInfo = ExamResponseDTO.QuestionDTO.builder()
                .part(4)
                .questionNumber(8)
                .tableImageUrl(TABLE_IMAGE_URL)
                .build();

        JsonNode json = objectMapper.valueToTree(questionInfo);

        assertAll(
                () -> assertTrue(json.has("tableImageUrl")),
                () -> assertFalse(json.has("table_image_url")),
                () -> assertEquals(TABLE_IMAGE_URL, json.get("tableImageUrl").asText())
        );
    }
}
