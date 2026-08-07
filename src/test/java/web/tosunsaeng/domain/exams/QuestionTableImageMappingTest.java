package web.tosunsaeng.domain.exams;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import web.tosunsaeng.domain.exams.converter.ExamConverter;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionTableImageMappingTest {

    private static final String TABLE_IMAGE_URL =
            "https://cdn.example.com/mock-exam/001/part4/q8.png";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mongoTableImageUrlFieldRemainsAvailableInternally() throws NoSuchFieldException {
        java.lang.reflect.Field javaField = Question.class.getDeclaredField("tableImageUrl");
        Field mongoField = javaField.getAnnotation(Field.class);

        assertAll(
                () -> assertNotNull(mongoField),
                () -> assertEquals("table_image_url", mongoField.value())
        );
    }

    @Test
    void mongoTableContextUsesSnakeCaseFieldNameAndOpaqueMapType() throws NoSuchFieldException {
        java.lang.reflect.Field javaField = Question.class.getDeclaredField("tableContext");
        Field mongoField = javaField.getAnnotation(Field.class);

        assertAll(
                () -> assertNotNull(mongoField),
                () -> assertEquals("table_context", mongoField.value()),
                () -> assertEquals(Map.class, javaField.getType())
        );
    }

    @Test
    void mongoConverterPreservesArbitraryNestedTableContext() throws Exception {
        Document storedTableContext = new Document("person_name", "Maya Bennett")
                .append("education", List.of(new Document("graduation_year", 2022)
                        .append("university_name", "Example University")))
                .append("custom_section", new Document("visible", true))
                .append("optional_value", null);
        Document storedQuestion = new Document("part_number", 4)
                .append("question_number", 8)
                .append("table_context", storedTableContext);

        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.setInitialEntitySet(Set.of(Question.class));
        mappingContext.afterPropertiesSet();
        MappingMongoConverter converter = new MappingMongoConverter(
                NoOpDbRefResolver.INSTANCE,
                mappingContext
        );
        converter.afterPropertiesSet();

        Question question = converter.read(Question.class, storedQuestion);

        assertAll(
                () -> assertEquals(
                        objectMapper.valueToTree(storedTableContext),
                        objectMapper.valueToTree(question.getTableContext())
                ),
                () -> assertTrue(question.getTableContext().containsKey("optional_value")),
                () -> assertFalse(question.getTableContext().containsKey("title")),
                () -> assertFalse(question.getTableContext().containsKey("items"))
        );
    }

    @Test
    void createSessionPartFourMappingReturnsOpaqueTableAndOmitsTableImage() {
        Map<String, Object> storedTableContext = arbitraryTableContext();
        Question question = Question.builder()
                .partNumber(4)
                .questionNumber(8)
                .question("Part 4 question")
                .tableImageUrl(TABLE_IMAGE_URL)
                .tableContext(storedTableContext)
                .build();

        ExamResponseDTO.QuestionDTO dto = ExamConverter.toCreateSessionQuestionDTO(question);
        JsonNode json = objectMapper.valueToTree(dto);

        assertAll(
                () -> assertEquals("Part 4 question", dto.getText()),
                () -> assertSame(storedTableContext, dto.getTableContext()),
                () -> assertEquals(
                        objectMapper.valueToTree(storedTableContext),
                        json.get("tableContext")
                ),
                () -> assertTrue(json.get("tableContext").has("optional_value")),
                () -> assertTrue(json.get("tableContext").get("optional_value").isNull()),
                () -> assertFalse(json.has("tableImageUrl")),
                () -> assertFalse(json.has("table_image_url"))
        );
    }

    @Test
    void promptMappingReturnsOpaqueTableAndOmitsTableImage() {
        Map<String, Object> storedTableContext = arbitraryTableContext();
        Question question = Question.builder()
                .partNumber(4)
                .questionNumber(8)
                .tableImageUrl(TABLE_IMAGE_URL)
                .tableContext(storedTableContext)
                .build();

        ExamResponseDTO.QuestionDTO dto = ExamConverter.toQuestionDTO(question);
        JsonNode json = objectMapper.valueToTree(dto);

        assertAll(
                () -> assertSame(storedTableContext, dto.getTableContext()),
                () -> assertEquals(
                        objectMapper.valueToTree(storedTableContext),
                        json.get("tableContext")
                ),
                () -> assertFalse(json.has("tableImageUrl"))
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 6, 7})
    void createSessionNonPartFourMappingKeepsExistingFields(int partNumber) {
        Map<String, Object> existingTableContext = arbitraryTableContext();
        Question question = Question.builder()
                .partNumber(partNumber)
                .questionNumber(partNumber)
                .tableImageUrl(TABLE_IMAGE_URL)
                .tableContext(existingTableContext)
                .build();

        ExamResponseDTO.QuestionDTO dto = ExamConverter.toCreateSessionQuestionDTO(question);

        assertSame(existingTableContext, dto.getTableContext());
    }

    private static Map<String, Object> arbitraryTableContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("person_name", "Maya Bennett");
        context.put("education", List.of(Map.of(
                "graduation_year", 2022,
                "university_name", "Example University"
        )));
        context.put("optional_value", null);
        return context;
    }
}
