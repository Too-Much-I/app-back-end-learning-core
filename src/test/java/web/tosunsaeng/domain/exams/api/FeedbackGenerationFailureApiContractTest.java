package web.tosunsaeng.domain.exams.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import web.tosunsaeng.domain.exams.application.ExamReadService;
import web.tosunsaeng.domain.exams.application.ExamService;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;
import web.tosunsaeng.global.exception.GlobalExceptionAdvice;
import web.tosunsaeng.global.sentry.UnexpectedExceptionReporter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeedbackGenerationFailureApiContractTest {

    private static final String EXAM_ID = "ex_feedback_failure_001";

    private ExamService examService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        examService = mock(ExamService.class);
        ExamReadService examReadService = mock(ExamReadService.class);
        UnexpectedExceptionReporter reporter = mock(UnexpectedExceptionReporter.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ExamRestController(examService, examReadService)
                )
                .setControllerAdvice(new GlobalExceptionAdvice(reporter))
                .build();
    }

    @Test
    void statusPollingReturnsExactFeedbackGenerationFailureContract() throws Exception {
        when(examService.getExamStatus(EXAM_ID)).thenThrow(feedbackGenerationFailure());

        mockMvc.perform(get("/api/v1/exams/{examId}/status", EXAM_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("FEEDBACK_GENERATION_FAILED"))
                .andExpect(jsonPath("$.message").value("피드백 생성에 실패했습니다."))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(examService).getExamStatus(EXAM_ID);
    }

    @Test
    void summaryReadReturnsSameFeedbackGenerationFailureContract() throws Exception {
        when(examService.getExamSummary(EXAM_ID)).thenThrow(feedbackGenerationFailure());

        mockMvc.perform(get("/api/v1/exams/{examId}/summary", EXAM_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("FEEDBACK_GENERATION_FAILED"))
                .andExpect(jsonPath("$.message").value("피드백 생성에 실패했습니다."))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(examService).getExamSummary(EXAM_ID);
    }

    private static ExamsException feedbackGenerationFailure() {
        return new ExamsException(ErrorStatus._FEEDBACK_GENERATION_FAILED);
    }
}
