package web.tosunsaeng.domain.exams;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.mapping.Field;
import web.tosunsaeng.domain.exams.converter.ExamConverter;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void createSessionPartFourMappingUsesImageAndOmitsStructuredTable() {
        Question.TableContext internalTableContext = Question.TableContext.builder()
                .title("Internal table")
                .build();
        Question question = Question.builder()
                .partNumber(4)
                .questionNumber(8)
                .question("Part 4 question")
                .tableImageUrl(TABLE_IMAGE_URL)
                .tableContext(internalTableContext)
                .build();

        ExamResponseDTO.QuestionDTO dto = ExamConverter.toCreateSessionQuestionDTO(question);
        JsonNode json = objectMapper.valueToTree(dto);

        assertAll(
                () -> assertEquals("Part 4 question", dto.getText()),
                () -> assertEquals(TABLE_IMAGE_URL, dto.getTableImageUrl()),
                () -> assertNull(dto.getTableContext()),
                () -> assertEquals(TABLE_IMAGE_URL, json.get("tableImageUrl").asText()),
                () -> assertFalse(json.has("tableContext"))
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 6, 7})
    void createSessionNonPartFourMappingKeepsExistingStructuredTable(int partNumber) {
        Question.TableContext existingTableContext = Question.TableContext.builder()
                .title("Existing table")
                .build();
        Question question = Question.builder()
                .partNumber(partNumber)
                .questionNumber(partNumber)
                .tableImageUrl(TABLE_IMAGE_URL)
                .tableContext(existingTableContext)
                .build();

        ExamResponseDTO.QuestionDTO dto = ExamConverter.toCreateSessionQuestionDTO(question);

        assertAll(
                () -> assertSame(existingTableContext, dto.getTableContext()),
                () -> assertNull(dto.getTableImageUrl())
        );
    }
}
