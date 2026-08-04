package web.tosunsaeng.domain.exams;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelAnswerResponseContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void modelAnswerDtoAndJsonContainOnlyAudioUrlAndSpokenWordSequence() {
        Set<String> declaredFields = Arrays.stream(
                        ExamResponseDTO.ModelAnswerResponse.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toSet());
        ExamResponseDTO.ModelAnswerResponse modelAnswer =
                ExamResponseDTO.ModelAnswerResponse.builder()
                        .audioUrl("https://example.invalid/model-answer-audio")
                        .spokenWordSequence(List.of(spokenWord("welcome")))
                        .build();

        JsonNode json = objectMapper.valueToTree(modelAnswer);

        assertAll(
                () -> assertEquals(Set.of("audioUrl", "spokenWordSequence"), declaredFields),
                () -> assertEquals(2, json.size()),
                () -> assertTrue(json.has("audioUrl")),
                () -> assertTrue(json.path("spokenWordSequence").isArray()),
                () -> assertFalse(json.has("text")),
                () -> assertFalse(json.has("modelAnswerText")),
                () -> assertFalse(json.has("referenceText")),
                () -> assertFalse(json.has("script")),
                () -> assertFalse(json.has("transcript")),
                () -> assertFalse(json.has("answer"))
        );
    }

    @Test
    void absentModelAnswerIsOmittedInsteadOfSerializedAsNull() {
        ExamResponseDTO.PartResultDTO question = ExamResponseDTO.PartResultDTO.builder()
                .partNumber(2)
                .questionNumber(3)
                .build();

        JsonNode json = objectMapper.valueToTree(question);

        assertFalse(json.has("modelAnswer"));
    }

    @Test
    void openApiDocumentsOnlyModelAnswerAudioAndSpokenWords() throws NoSuchFieldException {
        Schema modelAnswerSchema = ExamResponseDTO.PartResultDTO.class
                .getDeclaredField("modelAnswer")
                .getAnnotation(Schema.class);
        Schema audioUrlSchema = ExamResponseDTO.ModelAnswerResponse.class
                .getDeclaredField("audioUrl")
                .getAnnotation(Schema.class);
        Schema spokenWordsSchema = ExamResponseDTO.ModelAnswerResponse.class
                .getDeclaredField("spokenWordSequence")
                .getAnnotation(Schema.class);

        assertAll(
                () -> assertEquals(
                        "Part 1의 Question 1·2에서만 제공되는 모범답안 음성 정보",
                        modelAnswerSchema.description()
                ),
                () -> assertEquals(
                        "모범답안 음성의 임시 Presigned GET URL",
                        audioUrlSchema.description()
                ),
                () -> assertEquals(
                        "모범답안 음성의 단어별 타이밍 및 발음 점수",
                        spokenWordsSchema.description()
                )
        );
    }

    private ExamResponseDTO.SpokenWordDTO spokenWord(String word) {
        return ExamResponseDTO.SpokenWordDTO.builder()
                .index(0)
                .segmentIndex(0)
                .wordIndex(0)
                .word(word)
                .offset(400000L)
                .duration(7500000L)
                .accuracyScore(94.0)
                .pronunciationScore(94.0)
                .errorType("None")
                .build();
    }
}
