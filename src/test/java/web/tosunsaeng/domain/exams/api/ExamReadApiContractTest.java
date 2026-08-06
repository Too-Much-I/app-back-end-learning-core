package web.tosunsaeng.domain.exams.api;

import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import web.tosunsaeng.domain.exams.application.ExamReadService;
import web.tosunsaeng.domain.exams.application.ExamService;
import web.tosunsaeng.domain.exams.domain.enums.GradingJobStatus;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
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
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ExamRestController(examService, examReadService)
        ).build();
    }

    @Test
    void historyStaticPathDispatchesToHistoryInsteadOfAnExamIdRoute() throws Exception {
        when(examReadService.getExamHistory()).thenReturn(
                ExamResponseDTO.ExamHistoryResult.builder()
                        .totalCount(1)
                        .histories(List.of(ExamResponseDTO.ExamHistoryItem.builder()
                                .examId("ex_history")
                                .title("모의고사 4")
                                .cycleNumber(2)
                                .completedAt(LocalDateTime.of(2026, 8, 4, 9, 30))
                                .totalScore(145)
                                .levelEstimate("Advanced High")
                                .summaryAvailable(true)
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
                .andExpect(jsonPath("$.result.histories[0].summaryAvailable").value(true))
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
                                                .build(),
                                        ExamResponseDTO.RetryAttemptItem.builder()
                                                .retryCount(1)
                                                .status(GradingJobStatus.PROCESSING)
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
                .andExpect(jsonPath("$.result.questions[0].attempts[1].status").value("PROCESSING"))
                .andExpect(jsonPath("$..feedback").doesNotExist())
                .andExpect(jsonPath("$..audioUrl").doesNotExist())
                .andExpect(jsonPath("$..transcript").doesNotExist())
                .andExpect(jsonPath("$..failureReason").doesNotExist());

        verify(examReadService).getExamRetries("ex_retries");
        verifyNoInteractions(examService);
    }

    @Test
    void createSessionResponseExposesPartFourTableImageWithoutTableContext() throws Exception {
        String storedTableImageUrl = "https://cdn.example.com/mock-exam/001/part4/q8.png";
        when(examService.createExamSession()).thenReturn(
                ExamResponseDTO.CreateSessionResult.builder()
                        .examId("ex_part4")
                        .title("Part 4 mock exam")
                        .questions(List.of(ExamResponseDTO.QuestionDTO.builder()
                                .part(4)
                                .questionNumber(8)
                                .text("Part 4 question")
                                .audioUrl("https://example.com/question-audio")
                                .tableImageUrl(storedTableImageUrl)
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
                .andExpect(jsonPath("$.result.questions[0].tableImageUrl")
                        .value(storedTableImageUrl))
                .andExpect(jsonPath("$.result.questions[0].tableContext").doesNotExist());

        verify(examService).createExamSession();
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
}
