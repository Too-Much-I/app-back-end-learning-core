package web.tosunsaeng.domain.exams.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import web.tosunsaeng.domain.exams.application.ExamReadService;
import web.tosunsaeng.domain.exams.application.ExamService;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.enums.GradingJobStatus;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExamReadApiContractTest {

    private ExamService examService;
    private ExamReadService examReadService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        examService = mock(ExamService.class);
        examReadService = mock(ExamReadService.class);
        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ExamRestController(examService, examReadService)
        )
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void historyStaticPathDispatchesToHistoryInsteadOfAnExamIdRoute() throws Exception {
        when(examReadService.getExamHistory()).thenReturn(
                ExamResponseDTO.ExamHistoryResult.builder()
                        .totalCount(1)
                        .histories(List.of(ExamResponseDTO.ExamHistoryItem.builder()
                                .examId("ex_history")
                                .title("모의고사 4")
                                .status(ExamSessionStatus.COMPLETED)
                                .cycleNumber(2)
                                .startedAt(LocalDateTime.of(2026, 8, 4, 9, 0))
                                .completedAt(LocalDateTime.of(2026, 8, 4, 9, 30))
                                .totalScore(145)
                                .maxScore(200)
                                .levelEstimate("Advanced High")
                                .summaryAvailable(true)
                                .retriedQuestionCount(2)
                                .build()))
                        .build()
        );

        mockMvc.perform(get("/api/v1/exams/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.result.totalCount").value(1))
                .andExpect(jsonPath("$.result.histories[0].examId").value("ex_history"))
                .andExpect(jsonPath("$.result.histories[0].title").value("모의고사 4"))
                .andExpect(jsonPath("$.result.histories[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.histories[0].maxScore").value(200))
                .andExpect(jsonPath("$.result.histories[0].startedAt").value("2026-08-04T09:00:00"))
                .andExpect(jsonPath("$.result.histories[0].summaryAvailable").value(true))
                .andExpect(jsonPath("$.result.histories[0].retriedQuestionCount").value(2))
                .andExpect(jsonPath("$..userId").doesNotExist())
                .andExpect(jsonPath("$..mockExamId").doesNotExist());

        verify(examReadService).getExamHistory();
        verifyNoInteractions(examService);
    }

    @Test
    void retriesPathReturnsOnlyAttemptMetadata() throws Exception {
        when(examReadService.getExamRetries("ex_retries")).thenReturn(
                ExamResponseDTO.ExamRetriesResult.builder()
                        .examId("ex_retries")
                        .questions(List.of(ExamResponseDTO.RetriedQuestionItem.builder()
                                .partNumber(1)
                                .questionNumber(1)
                                .totalAttemptCount(2)
                                .latestRetryCount(1)
                                .attempts(List.of(
                                        ExamResponseDTO.RetryAttemptItem.builder()
                                                .retryCount(0)
                                                .status(GradingJobStatus.COMPLETED)
                                                .score(2.1)
                                                .completedAt(Instant.parse("2026-08-01T12:10:00Z"))
                                                .build(),
                                        ExamResponseDTO.RetryAttemptItem.builder()
                                                .retryCount(1)
                                                .status(GradingJobStatus.PROCESSING)
                                                .score(2.6)
                                                .build()
                                ))
                                .build()))
                        .build()
        );

        mockMvc.perform(get("/api/v1/exams/{examId}/retries", "ex_retries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.examId").value("ex_retries"))
                .andExpect(jsonPath("$.result.questions[0].partNumber").value(1))
                .andExpect(jsonPath("$.result.questions[0].attempts[0].retryCount").value(0))
                .andExpect(jsonPath("$.result.questions[0].attempts[0].score").value(2.1))
                .andExpect(jsonPath("$.result.questions[0].attempts[0].completedAt")
                        .value("2026-08-01T12:10:00Z"))
                .andExpect(jsonPath("$.result.questions[0].attempts[1].status").value("PROCESSING"))
                .andExpect(jsonPath("$..feedback").doesNotExist())
                .andExpect(jsonPath("$..audioUrl").doesNotExist())
                .andExpect(jsonPath("$..transcript").doesNotExist())
                .andExpect(jsonPath("$..failureReason").doesNotExist());

        verify(examReadService).getExamRetries("ex_retries");
        verifyNoInteractions(examService);
    }

    @Test
    void createSessionResponseExposesStoredOpaquePartFourTableContext() throws Exception {
        Map<String, Object> storedTableContext = partFourTableContext();
        when(examService.createExamSession(null)).thenReturn(
                ExamResponseDTO.CreateSessionResult.builder()
                        .examId("ex_part4")
                        .title("Part 4 mock exam")
                        .questions(List.of(ExamResponseDTO.QuestionDTO.builder()
                                .part(4)
                                .questionNumber(8)
                                .text("Part 4 question")
                                .audioUrl("https://example.com/question-audio")
                                .tableContext(storedTableContext)
                                .build()))
                        .build()
        );

        mockMvc.perform(post("/api/v1/exams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.questions[0].part").value(4))
                .andExpect(jsonPath("$.result.questions[0].questionNumber").value(8))
                .andExpect(jsonPath("$.result.questions[0].text").value("Part 4 question"))
                .andExpect(jsonPath("$.result.questions[0].audioUrl")
                        .value("https://example.com/question-audio"))
                .andExpect(jsonPath("$.result.questions[0].tableContext.resume_owner")
                        .value("Maya Bennett"))
                .andExpect(jsonPath(
                        "$.result.questions[0].tableContext.education_history[0].graduation_year"
                ).value(2022))
                .andExpect(jsonPath("$.result.questions[0].tableContext.title").doesNotExist())
                .andExpect(jsonPath("$.result.questions[0].tableImageUrl").doesNotExist());

        verify(examService).createExamSession(null);
        verifyNoInteractions(examReadService);
    }

    @Test
    void createSessionForwardsIdempotencyKeyWithoutChangingResponseShape() throws Exception {
        String operationId = "018f6f36-2f42-4bf5-8c17-0be35de4872c";
        when(examService.createExamSession(operationId)).thenReturn(
                ExamResponseDTO.CreateSessionResult.builder()
                        .examId("ex_idempotent")
                        .title("Idempotent exam")
                        .questions(List.of())
                        .build()
        );

        mockMvc.perform(post("/api/v1/exams")
                        .header("Idempotency-Key", operationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.examId").value("ex_idempotent"))
                .andExpect(jsonPath("$.result.title").value("Idempotent exam"))
                .andExpect(jsonPath("$.result.questions").isArray())
                .andExpect(jsonPath("$.result.userId").doesNotExist())
                .andExpect(jsonPath("$.result.reservationId").doesNotExist())
                .andExpect(jsonPath("$.result.attemptGroupId").doesNotExist());

        verify(examService).createExamSession(operationId);
    }

    @Test
    void promptResponseExposesStoredOpaquePartFourTableContext() throws Exception {
        Map<String, Object> storedTableContext = partFourTableContext();
        when(examService.getQuestionPrompt("ex_part4", 8)).thenReturn(
                ExamResponseDTO.QuestionDTO.builder()
                        .part(4)
                        .questionNumber(8)
                        .text("Part 4 question")
                        .tableContext(storedTableContext)
                        .build()
        );

        mockMvc.perform(get(
                        "/api/v1/exams/{examId}/questions/{questionNumber}/prompt",
                        "ex_part4",
                        8
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.part").value(4))
                .andExpect(jsonPath("$.result.questionNumber").value(8))
                .andExpect(jsonPath("$.result.text").value("Part 4 question"))
                .andExpect(jsonPath("$.result.tableContext.resume_owner")
                        .value("Maya Bennett"))
                .andExpect(jsonPath(
                        "$.result.tableContext.education_history[0].university_name"
                ).value("Example University"))
                .andExpect(jsonPath("$.result.tableImageUrl").doesNotExist());

        verify(examService).getQuestionPrompt("ex_part4", 8);
        verifyNoInteractions(examReadService);
    }

    @Test
    void questionResultResponseExposesStoredOpaquePartFourTableContext() throws Exception {
        Map<String, Object> storedTableContext = partFourTableContext();
        when(examService.getExamQuestion("ex_part4", 8, 0)).thenReturn(
                ExamResponseDTO.QuestionResult.builder()
                        .examId("ex_part4")
                        .question(ExamResponseDTO.PartResultDTO.builder()
                                .partNumber(4)
                                .questionNumber(8)
                                .retryCount(0)
                                .questionInfo(ExamResponseDTO.QuestionDTO.builder()
                                        .part(4)
                                        .questionNumber(8)
                                        .text("When did Dr. Kim graduate from university?")
                                        .tableContext(storedTableContext)
                                        .build())
                                .build())
                        .build()
        );

        mockMvc.perform(get("/api/v1/exams/{examId}/questions", "ex_part4")
                        .param("questionNumber", "8")
                        .param("retryCount", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.question.questionInfo.part").value(4))
                .andExpect(jsonPath("$.result.question.questionInfo.questionNumber").value(8))
                .andExpect(jsonPath("$.result.question.questionInfo.text")
                        .value("When did Dr. Kim graduate from university?"))
                .andExpect(jsonPath(
                        "$.result.question.questionInfo.tableContext.education_history[0].graduation_year"
                ).value(2022))
                .andExpect(jsonPath(
                        "$.result.question.questionInfo.tableContext.education_history[0].university_name"
                ).value("Example University"))
                .andExpect(jsonPath(
                        "$.result.question.questionInfo.tableImageUrl"
                ).doesNotExist());

        verify(examService).getExamQuestion("ex_part4", 8, 0);
        verifyNoInteractions(examReadService);
    }

    @Test
    void newAndExistingControllerMappingsRemainDistinct() throws Exception {
        Set<String> getMappings = Arrays.stream(ExamRestController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(GetMapping.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .collect(Collectors.toSet());
        Set<String> postMappings = Arrays.stream(ExamRestController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .collect(Collectors.toSet());
        Method history = ExamRestController.class.getDeclaredMethod("getExamHistory");
        Method retries = ExamRestController.class.getDeclaredMethod("getExamRetries", String.class);
        Operation historyOperation = history.getAnnotation(Operation.class);
        Operation retriesOperation = retries.getAnnotation(Operation.class);

        assertAll(
                () -> assertEquals(Set.of(
                        "/history",
                        "/{examId}/retries",
                        "/{examId}/questions/{questionNumber}/prompt",
                        "/{examId}/questions/{questionNumber}/upload-url",
                        "/{examId}/status",
                        "/{examId}/summary",
                        "/{examId}/questions",
                        "/{examId}/questions/status"
                ), getMappings),
                () -> assertEquals(Set.of(
                        "",
                        "/{examId}/questions/{questionNumber}/submit",
                        "/{examId}/grading/retry",
                        "/callback/feedback",
                        "/callback/speechace",
                        "/callback/azure"
                ), postMappings),
                () -> assertTrue(historyOperation.description().contains("Bearer")),
                () -> assertTrue(historyOperation.description().contains("빈 histories")),
                () -> assertTrue(retriesOperation.description().contains("retryCount 1 이상")),
                () -> assertTrue(retriesOperation.description().contains("상세 피드백")),
                () -> assertTrue(retriesOperation.description().contains("빈 questions"))
        );
    }

    private static Map<String, Object> partFourTableContext() {
        return Map.of(
                "resume_owner", "Maya Bennett",
                "education_history", List.of(Map.of(
                        "graduation_year", 2022,
                        "university_name", "Example University"
                ))
        );
    }
}
