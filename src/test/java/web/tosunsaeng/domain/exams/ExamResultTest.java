package web.tosunsaeng.domain.exams;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.enums.ExamStatus;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamResultTest {

    private static final String EXAM_ID = "ex_1234567890_0723_1500";
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Test
    void builderKeepsExamIdAndUserIdDistinctAlongsideExistingFields() {
        ExamResult result = ExamResult.builder()
                .examId(EXAM_ID)
                .userId(USER_ID)
                .mockExamId("mock_exam_003")
                .retryCount(2)
                .partNumber(1)
                .questionNumber(3)
                .score(8.5)
                .maxScore(10.0)
                .build();

        assertAll(
                () -> assertEquals(EXAM_ID, result.getExamId()),
                () -> assertEquals(USER_ID, result.getUserId()),
                () -> assertEquals("mock_exam_003", result.getMockExamId()),
                () -> assertEquals(2, result.getRetryCount()),
                () -> assertEquals(1, result.getPartNumber()),
                () -> assertEquals(3, result.getQuestionNumber()),
                () -> assertEquals(8.5, result.getScore()),
                () -> assertEquals(10.0, result.getMaxScore())
        );
    }

    @Test
    void userIdRemainsNullableWhenItIsNotSet() {
        ExamResult result = ExamResult.builder()
                .examId(EXAM_ID)
                .build();

        assertEquals(EXAM_ID, result.getExamId());
        assertNull(result.getUserId());
    }

    @Test
    void examResponseSerializationDoesNotExposeInternalUserIdentifiers() {
        List<Object> responsePayloads = List.of(
                ExamResponseDTO.CreateSessionResult.builder()
                        .examId(EXAM_ID)
                        .title("Test mock exam")
                        .questions(List.of())
                        .build(),
                ExamResponseDTO.UploadUrlResult.builder()
                        .uploadUrl("https://example.com/upload")
                        .fileKey("test-file-key")
                        .expiresIn(300)
                        .build(),
                ExamResponseDTO.SubmitResult.builder()
                        .status(ExamStatus.PROCESSING)
                        .build(),
                ExamResponseDTO.StatusResult.builder()
                        .examId(EXAM_ID)
                        .overallStatus(ExamStatus.COMPLETED)
                        .progressPercent(100)
                        .build(),
                ExamResponseDTO.SummaryResult.builder()
                        .examId(EXAM_ID)
                        .totalScore(90)
                        .levelEstimate("Advanced")
                        .totalSolvedQuestions(11)
                        .build(),
                ExamResponseDTO.QuestionResult.builder()
                        .examId(EXAM_ID)
                        .question(ExamResponseDTO.PartResultDTO.builder()
                                .questionNumber(3)
                                .retryCount(2)
                                .score(8.5)
                                .maxScore(10.0)
                                .build())
                        .build(),
                ExamResponseDTO.QuestionPollResult.builder()
                        .examId(EXAM_ID)
                        .questionNumber(3)
                        .retryCount(2)
                        .status(ExamStatus.COMPLETED)
                        .build()
        );

        JsonNode responseJson = new ObjectMapper().valueToTree(responsePayloads);

        assertAll(
                () -> assertTrue(responseJson.findValues("userId").isEmpty()),
                () -> assertTrue(responseJson.findValues("user_id").isEmpty())
        );
    }

    @Test
    void questionResultSerializesRetryScoresAsAnArray() {
        ExamResponseDTO.QuestionResult response = ExamResponseDTO.QuestionResult.builder()
                .examId(EXAM_ID)
                .question(ExamResponseDTO.PartResultDTO.builder()
                        .questionNumber(1)
                        .retryCount(3)
                        .retryScores(List.of(
                                ExamResponseDTO.RetryScoreDTO.builder()
                                        .retryCount(0)
                                        .score(2.0)
                                        .build(),
                                ExamResponseDTO.RetryScoreDTO.builder()
                                        .retryCount(3)
                                        .score(3.0)
                                        .build()
                        ))
                        .retryFeedbackScores(List.of(
                                ExamResponseDTO.RetryFeedbackScoreDTO.builder()
                                        .retryCount(0)
                                        .pronunciationFluencyScore(8.1)
                                        .contentRelevanceScore(8.2)
                                        .detailedScores(List.of(
                                                Map.of("accuracy_score", 8.6),
                                                Map.of("fluency_score", 8.3)
                                        ))
                                        .build()
                        ))
                        .build())
                .build();

        JsonNode json = new ObjectMapper().valueToTree(response);

        assertAll(
                () -> assertTrue(json.path("question").path("retryScores").isArray()),
                () -> assertEquals(0, json.path("question").path("retryScores").get(0)
                        .path("retryCount").asInt()),
                () -> assertEquals(2.0, json.path("question").path("retryScores").get(0)
                        .path("score").asDouble()),
                () -> assertEquals(1, json.path("question").path("retryFeedbackScores").size()),
                () -> assertEquals(0, json.path("question").path("retryFeedbackScores").get(0)
                        .path("retryCount").asInt()),
                () -> assertEquals(8.1, json.path("question").path("retryFeedbackScores").get(0)
                        .path("pronunciationFluencyScore").asDouble()),
                () -> assertEquals(8.6, json.path("question").path("retryFeedbackScores").get(0)
                        .path("detailedScores").get(0).path("accuracy_score").asDouble())
        );
    }
}
