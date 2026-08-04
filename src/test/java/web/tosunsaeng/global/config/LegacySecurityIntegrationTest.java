package web.tosunsaeng.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import web.tosunsaeng.domain.exams.api.ExamRestController;
import web.tosunsaeng.domain.exams.application.ExamReadService;
import web.tosunsaeng.domain.exams.application.ExamService;
import web.tosunsaeng.domain.exams.domain.repository.AzureResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;
import web.tosunsaeng.domain.exams.domain.repository.MockExamRepository;
import web.tosunsaeng.domain.exams.domain.repository.QuestionRepository;
import web.tosunsaeng.domain.exams.domain.repository.QuestionGradingJobRepository;
import web.tosunsaeng.domain.exams.domain.repository.SpeechAceResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.SummaryGradingJobRepository;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import web.tosunsaeng.domain.exams.domain.enums.ExamStatus;
import web.tosunsaeng.domain.exams.domain.enums.SummaryAction;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationDeliveryRepository;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationDeviceRepository;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationOutboxRepository;
import web.tosunsaeng.global.auth.CurrentUserProvider;
import web.tosunsaeng.global.auth.JwtCurrentUserProvider;
import web.tosunsaeng.global.auth.LegacyCurrentUserProvider;
import web.tosunsaeng.global.config.security.SecurityErrorResponseHandler;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(ExamRestController.class)
@Import({
        SecurityConfig.class,
        SecurityErrorResponseHandler.class,
        LegacyCurrentUserProvider.class
})
class LegacySecurityIntegrationTest {

    private static final String LEGACY_USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @MockitoBean
    private ExamService examService;

    @MockitoBean
    private ExamReadService examReadService;

    @MockitoBean
    private ExamResultRepository examResultRepository;

    @MockitoBean
    private ExamSummaryRepository examSummaryRepository;

    @MockitoBean
    private ExamSessionRepository examSessionRepository;

    @MockitoBean
    private MockExamRepository mockExamRepository;

    @MockitoBean
    private QuestionRepository questionRepository;

    @MockitoBean
    private SpeechAceResultRepository speechAceResultRepository;

    @MockitoBean
    private AzureResultRepository azureResultRepository;

    @MockitoBean
    private QuestionGradingJobRepository questionGradingJobRepository;

    @MockitoBean
    private SummaryGradingJobRepository summaryGradingJobRepository;

    @MockitoBean
    private NotificationDeviceRepository notificationDeviceRepository;

    @MockitoBean
    private NotificationOutboxRepository notificationOutboxRepository;

    @MockitoBean
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @BeforeEach
    void setUp() {
        when(examService.createExamSession()).thenReturn(
                ExamResponseDTO.CreateSessionResult.builder()
                        .examId("ex_legacy_test")
                        .title("Legacy test exam")
                        .questions(List.of())
                        .build()
        );
    }

    @Test
    void defaultModeRegistersOnlyLegacyCurrentUserProviderAndNoJwtDecoder() {
        assertEquals("legacy", environment.getProperty("app.auth.mode"));
        assertInstanceOf(LegacyCurrentUserProvider.class, currentUserProvider);
        assertEquals(LEGACY_USER_ID, currentUserProvider.getCurrentUserId());
        assertEquals(1, applicationContext.getBeansOfType(CurrentUserProvider.class).size());
        assertTrue(applicationContext.getBeansOfType(JwtCurrentUserProvider.class).isEmpty());
        assertTrue(applicationContext.getBeansOfType(JwtDecoder.class).isEmpty());
        assertEquals(
                Set.of("legacySecurityFilterChain"),
                applicationContext.getBeansOfType(SecurityFilterChain.class).keySet()
        );
    }

    @Test
    void examApiRemainsAccessibleWithoutAuthorizationAndDoesNotExposeUserId() throws Exception {
        mockMvc.perform(post("/api/v1/exams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.result.examId").value("ex_legacy_test"))
                .andExpect(jsonPath("$..userId").doesNotExist())
                .andExpect(jsonPath("$..user_id").doesNotExist());
    }

    @Test
    void gradingRetryEndpointRequiresNoRequestBodyAndKeepsBaseResponse() throws Exception {
        when(examService.retryGrading("ex_legacy_test")).thenReturn(
                ExamResponseDTO.GradingRetryResult.builder()
                        .examId("ex_legacy_test")
                        .overallStatus(ExamStatus.PROCESSING)
                        .retriedQuestionNumbers(List.of(2))
                        .waitingQuestionNumbers(List.of(3))
                        .missingSubmissionQuestionNumbers(List.of(4))
                        .summaryAction(SummaryAction.NOT_READY)
                        .build()
        );

        mockMvc.perform(post("/api/v1/exams/{examId}/grading/retry", "ex_legacy_test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.examId").value("ex_legacy_test"))
                .andExpect(jsonPath("$.result.overallStatus").value("PROCESSING"))
                .andExpect(jsonPath("$.result.retriedQuestionNumbers[0]").value(2))
                .andExpect(jsonPath("$.result.waitingQuestionNumbers[0]").value(3))
                .andExpect(jsonPath("$.result.missingSubmissionQuestionNumbers[0]").value(4))
                .andExpect(jsonPath("$.result.summaryAction").value("NOT_READY"));

        verify(examService).retryGrading("ex_legacy_test");
    }
}
