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
import web.tosunsaeng.domain.exams.application.ExamService;
import web.tosunsaeng.domain.exams.domain.repository.AzureResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;
import web.tosunsaeng.domain.exams.domain.repository.MockExamRepository;
import web.tosunsaeng.domain.exams.domain.repository.QuestionRepository;
import web.tosunsaeng.domain.exams.domain.repository.SpeechAceResultRepository;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import web.tosunsaeng.global.auth.CurrentUserProvider;
import web.tosunsaeng.global.auth.JwtCurrentUserProvider;
import web.tosunsaeng.global.auth.LegacyCurrentUserProvider;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(ExamRestController.class)
@Import({SecurityConfig.class, LegacyCurrentUserProvider.class})
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
}
