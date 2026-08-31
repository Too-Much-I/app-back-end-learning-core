package web.tosunsaeng.global.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import web.tosunsaeng.domain.exams.api.ExamRestController;
import web.tosunsaeng.domain.exams.application.ExamReadService;
import web.tosunsaeng.domain.exams.application.ExamServiceImpl;
import web.tosunsaeng.domain.exams.application.ExamGradingService;
import web.tosunsaeng.domain.exams.application.ExamSessionManager;
import web.tosunsaeng.domain.exams.application.BillingExamCreationSaga;
import web.tosunsaeng.domain.exams.billing.BillingSagaProperties;
import web.tosunsaeng.domain.exams.application.ModelAnswerCatalogService;
import web.tosunsaeng.domain.exams.application.MockExamCatalogService;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.domain.entity.QuestionGradingJob;
import web.tosunsaeng.domain.exams.domain.enums.ExamStatus;
import web.tosunsaeng.domain.exams.domain.enums.GradingJobStatus;
import web.tosunsaeng.domain.exams.domain.repository.AzureResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamCreationOperationRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;
import web.tosunsaeng.domain.exams.domain.repository.MockExamRepository;
import web.tosunsaeng.domain.exams.domain.repository.QuestionGradingJobRepository;
import web.tosunsaeng.domain.exams.domain.repository.QuestionRepository;
import web.tosunsaeng.domain.exams.domain.repository.SpeechAceResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.SummaryGradingJobRepository;
import web.tosunsaeng.global.auth.CurrentUserProvider;
import web.tosunsaeng.global.auth.JwtCurrentUserProvider;
import web.tosunsaeng.global.auth.LegacyCurrentUserProvider;
import web.tosunsaeng.global.config.security.SecurityErrorResponseHandler;
import web.tosunsaeng.global.exception.GlobalExceptionAdvice;
import web.tosunsaeng.global.sentry.UnexpectedExceptionReporter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(ExamRestController.class)
@Import({
        SecurityConfig.class,
        SecurityErrorResponseHandler.class,
        JwtCurrentUserProvider.class,
        ExamServiceImpl.class,
        ExamReadService.class,
        GlobalExceptionAdvice.class
})
class JwtSecurityIntegrationTest {

    private static final String ISSUER = "http://identity.test";
    private static final String AUDIENCE = "tosunsaeng-learning-core";
    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000042";
    private static final String OTHER_USER_ID = "00000000-0000-0000-0000-000000000043";
    private static final String CALLBACK_EXAM_ID = "ex_callback_security_test";

    private static final RSAKey VALID_SIGNING_KEY = generateRsaKey("identity-key");
    private static final RSAKey INVALID_SIGNING_KEY = generateRsaKey("untrusted-key");
    private static final HttpServer JWKS_SERVER = startJwksServer();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockitoBean
    private S3Presigner s3Presigner;

    @MockitoBean
    private RestTemplate restTemplate;

    @MockitoBean
    private ExamGradingService gradingService;

    @MockitoBean
    private ExamSessionManager examSessionManager;

    @MockitoBean
    private BillingExamCreationSaga billingExamCreationSaga;

    @MockitoBean
    private BillingSagaProperties billingSagaProperties;

    @MockitoBean
    private ExamResultRepository examResultRepository;

    @MockitoBean
    private ExamSummaryRepository examSummaryRepository;

    @MockitoBean
    private ExamSessionRepository examSessionRepository;

    @MockitoBean
    private ExamCreationOperationRepository examCreationOperationRepository;

    @MockitoBean
    private MockExamCatalogService mockExamCatalogService;

    @MockitoBean
    private ModelAnswerCatalogService modelAnswerCatalogService;

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
    private UnexpectedExceptionReporter unexpectedExceptionReporter;

    private final Map<String, ExamSession> sessions = new ConcurrentHashMap<>();
    private ValueOperations<String, Object> valueOperations;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("app.auth.mode", () -> "jwt");
        registry.add("app.auth.identity.issuer", () -> ISSUER);
        registry.add("app.auth.identity.audience", () -> AUDIENCE);
        registry.add(
                "app.auth.identity.jwk-set-uri",
                () -> "http://localhost:" + JWKS_SERVER.getAddress().getPort() + "/.well-known/jwks.json"
        );
    }

    @AfterAll
    static void stopJwksServer() {
        JWKS_SERVER.stop(0);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        sessions.clear();
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(gradingService.calculateAndCacheOverallStatus(anyString())).thenReturn(ExamStatus.PENDING);

        when(examSessionRepository.save(any(ExamSession.class))).thenAnswer(invocation -> {
            ExamSession session = invocation.getArgument(0);
            sessions.put(session.getExamId(), session);
            return session;
        });
        when(examSessionRepository.findById(anyString()))
                .thenAnswer(invocation -> java.util.Optional.ofNullable(sessions.get(invocation.getArgument(0))));

        Question question = Question.builder()
                .partNumber(1)
                .questionNumber(1)
                .question("JWT integration test question")
                .build();
        MockExam mockExam = MockExam.builder()
                .mockExamId("mock_exam_003")
                .title("JWT integration test exam")
                .questions(List.of(question))
                .build();
        when(examSessionManager.startNew(anyString())).thenAnswer(invocation -> {
            String userId = invocation.getArgument(0);
            String examId = "ex_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)
                    + "_0729_0600";
            ExamSession session = ExamSession.builder()
                    .examId(examId)
                    .userId(userId)
                    .createdAt(LocalDateTime.of(2026, 7, 29, 6, 0))
                    .mockExamId("mock_exam_003")
                    .cycleNumber(1)
                    .active(true)
                    .build();
            sessions.put(examId, session);
            return new ExamSessionManager.Assignment(session, mockExam, true);
        });
        when(mockExamCatalogService.getRequiredExam("mock_exam_003"))
                .thenReturn(mockExam);

        PresignedGetObjectRequest presignedGetObjectRequest = mock(PresignedGetObjectRequest.class);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGetObjectRequest);
        when(presignedGetObjectRequest.url())
                .thenReturn(URI.create("https://example.com/questions/mock_exam_003/q_1.wav").toURL());
    }

    @Test
    void jwtModeRegistersOnlyJwtCurrentUserProvider() {
        assertInstanceOf(JwtCurrentUserProvider.class, currentUserProvider);
        assertEquals(1, applicationContext.getBeansOfType(CurrentUserProvider.class).size());
        assertTrue(applicationContext.getBeansOfType(LegacyCurrentUserProvider.class).isEmpty());
        assertEquals(Set.of("jwtDecoder"), applicationContext.getBeansOfType(JwtDecoder.class).keySet());
        assertEquals(
                Set.of("jwtSecurityFilterChain"),
                applicationContext.getBeansOfType(SecurityFilterChain.class).keySet()
        );
    }

    @Test
    void userApiWithoutTokenReturnsBaseResponse401() throws Exception {
        expectUnauthorized(mockMvc.perform(post("/api/v1/exams")));
    }

    @Test
    void questionPromptWithoutTokenReturnsBaseResponse401() throws Exception {
        expectUnauthorized(mockMvc.perform(get(
                "/api/v1/exams/{examId}/questions/{questionNumber}/prompt",
                "ex_prompt_security_test",
                1
        )));
    }

    @Test
    void examHistoryWithoutTokenReturnsBaseResponse401() throws Exception {
        expectUnauthorized(mockMvc.perform(get("/api/v1/exams/history")));
    }

    @Test
    void examRetriesWithoutTokenReturnsBaseResponse401() throws Exception {
        expectUnauthorized(mockMvc.perform(get(
                "/api/v1/exams/{examId}/retries",
                "ex_retries_security_test"
        )));
    }

    @Test
    void validTokenHistoryUsesJwtSubjectAndDoesNotExposeInternalIds() throws Exception {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 4, 9, 0);
        LocalDateTime completedAt = LocalDateTime.of(2026, 8, 4, 9, 30);
        when(examSessionRepository.findCompletedByUserId(OWNER_USER_ID)).thenReturn(List.of(
                ExamSession.builder()
                        .examId("ex_history_security_test")
                        .userId(OWNER_USER_ID)
                        .createdAt(startedAt)
                        .mockExamId("mock_exam_004")
                        .cycleNumber(2)
                        .active(null)
                        .completedAt(completedAt)
                        .build()
        ));
        when(mockExamRepository.findTitlesByMockExamIdIn(anyCollection())).thenReturn(List.of(
                MockExam.builder()
                        .mockExamId("mock_exam_004")
                        .title("JWT history test exam")
                        .build()
        ));
        when(examSummaryRepository.findHistoryCandidatesByExamIdIn(anyCollection())).thenReturn(List.of(
                ExamSummary.builder()
                        .id("summary:ex_history_security_test:v1")
                        .examId("ex_history_security_test")
                        .totalScore(145)
                        .levelEstimate("Advanced High")
                        .build()
        ));
        when(examResultRepository.findLegacySummaryCandidatesByExamIdIn(anyCollection()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/exams/history")
                        .header("Authorization", bearer(validToken(OWNER_USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalCount").value(1))
                .andExpect(jsonPath("$.result.histories[0].examId").value("ex_history_security_test"))
                .andExpect(jsonPath("$.result.histories[0].title").value("JWT history test exam"))
                .andExpect(jsonPath("$.result.histories[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.histories[0].totalScore").value(145))
                .andExpect(jsonPath("$.result.histories[0].maxScore").value(200))
                .andExpect(jsonPath("$.result.histories[0].startedAt").value("2026-08-04T09:00:00"))
                .andExpect(jsonPath("$..userId").doesNotExist())
                .andExpect(jsonPath("$..user_id").doesNotExist())
                .andExpect(jsonPath("$..mockExamId").doesNotExist());

        verify(examSessionRepository).findCompletedByUserId(OWNER_USER_ID);
    }

    @Test
    void ownerCanReadRetriesWithJwtSubject() throws Exception {
        String examId = "ex_retries_security_test";
        sessions.put(examId, ExamSession.builder()
                .examId(examId)
                .userId(OWNER_USER_ID)
                .build());
        when(examResultRepository.findQuestionAttemptsByExamId(examId)).thenReturn(List.of(
                ExamResult.builder()
                        .id("feedback:ex_retries_security_test:1:0")
                        .examId(examId)
                        .questionNumber(1)
                        .retryCount(0)
                        .score(2.1)
                        .build(),
                ExamResult.builder()
                        .id("feedback:ex_retries_security_test:1:1")
                        .examId(examId)
                        .questionNumber(1)
                        .retryCount(1)
                        .score(2.6)
                        .build()
        ));
        when(questionGradingJobRepository.findAttemptsByExamId(examId)).thenReturn(List.of(
                QuestionGradingJob.builder()
                        .jobId("question:ex_retries_security_test:1:1")
                        .examId(examId)
                        .questionNumber(1)
                        .retryCount(1)
                        .dispatchAttempt(34)
                        .status(GradingJobStatus.COMPLETED)
                        .completedAt(Instant.parse("2026-08-01T12:20:00Z"))
                        .build()
        ));

        mockMvc.perform(get("/api/v1/exams/{examId}/retries", examId)
                        .header("Authorization", bearer(validToken(OWNER_USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.examId").value(examId))
                .andExpect(jsonPath("$.result.questions[0].attempts[0].retryCount").value(0))
                .andExpect(jsonPath("$.result.questions[0].attempts[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.questions[0].attempts[0].score").value(2.1))
                .andExpect(jsonPath("$.result.questions[0].attempts[1].retryCount").value(1))
                .andExpect(jsonPath("$.result.questions[0].attempts[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.questions[0].attempts[1].score").value(2.6))
                .andExpect(jsonPath("$.result.questions[0].attempts[1].completedAt")
                        .value("2026-08-01T12:20:00Z"))
                .andExpect(jsonPath("$..dispatchAttempt").doesNotExist());
    }

    @Test
    void anotherUserCannotReadRetries() throws Exception {
        String examId = "ex_other_retries_security_test";
        sessions.put(examId, ExamSession.builder()
                .examId(examId)
                .userId(OWNER_USER_ID)
                .build());

        mockMvc.perform(get("/api/v1/exams/{examId}/retries", examId)
                        .header("Authorization", bearer(validToken(OTHER_USER_ID))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON403"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void missingExamRetriesKeepsExistingNotFoundResponse() throws Exception {
        mockMvc.perform(get("/api/v1/exams/{examId}/retries", "ex_missing_retries")
                        .header("Authorization", bearer(validToken(OWNER_USER_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("EXAM_4004"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void validRs256TokenCreatesExamStoresSubjectAndDoesNotExposeUserId() throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", bearer(validToken(OWNER_USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.result.examId").isString())
                .andExpect(jsonPath("$..userId").doesNotExist())
                .andExpect(jsonPath("$..user_id").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode responseJson = objectMapper.readTree(responseBody);
        String examId = responseJson.path("result").path("examId").asText();
        ExamSession savedSession = sessions.get(examId);

        assertNotNull(savedSession);
        assertEquals(OWNER_USER_ID, savedSession.getUserId());
        assertEquals(examId, savedSession.getExamId());
        assertNotEquals(savedSession.getExamId(), savedSession.getUserId());
    }

    @Test
    void sameUserCanAccessOwnedExam() throws Exception {
        String examId = createExam(validToken(OWNER_USER_ID));

        mockMvc.perform(get("/api/v1/exams/{examId}/status", examId)
                        .header("Authorization", bearer(validToken(OWNER_USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.examId").value(examId))
                .andExpect(jsonPath("$.result.overallStatus").value("PENDING"));
    }

    @Test
    void jwtSubjectOwnerCanReadAssignedQuestionPromptWithoutUserIdExposure() throws Exception {
        String examId = createExam(validToken(OWNER_USER_ID));

        mockMvc.perform(get(
                        "/api/v1/exams/{examId}/questions/{questionNumber}/prompt",
                        examId,
                        1
                ).header("Authorization", bearer(validToken(OWNER_USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.part").value(1))
                .andExpect(jsonPath("$.result.questionNumber").value(1))
                .andExpect(jsonPath("$.result.text").value("JWT integration test question"))
                .andExpect(jsonPath("$.result.audioUrl").isString())
                .andExpect(jsonPath("$..userId").doesNotExist())
                .andExpect(jsonPath("$..user_id").doesNotExist())
                .andExpect(jsonPath("$..mockExamId").doesNotExist());

        verify(mockExamCatalogService).getRequiredExam("mock_exam_003");
    }

    @Test
    void anotherUserCannotAccessOwnedExamAndGetsBaseResponse403() throws Exception {
        String examId = createExam(validToken(OWNER_USER_ID));

        mockMvc.perform(get("/api/v1/exams/{examId}/status", examId)
                        .header("Authorization", bearer(validToken(OTHER_USER_ID))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON403"))
                .andExpect(jsonPath("$.message").value("금지된 요청입니다."))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void anotherUserCannotReadQuestionPrompt() throws Exception {
        String examId = createExam(validToken(OWNER_USER_ID));

        mockMvc.perform(get(
                        "/api/v1/exams/{examId}/questions/{questionNumber}/prompt",
                        examId,
                        1
                ).header("Authorization", bearer(validToken(OTHER_USER_ID))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON403"))
                .andExpect(jsonPath("$.message").value("금지된 요청입니다."))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    void invalidTokensAreRejectedWithBaseResponse401(String token) throws Exception {
        expectUnauthorized(mockMvc.perform(post("/api/v1/exams")
                .header("Authorization", bearer(token))));
    }

    @Test
    void aiCallbackRemainsPublicAndKeepsExternalUserIdAsExamId() throws Exception {
        sessions.put(CALLBACK_EXAM_ID, ExamSession.builder()
                .examId(CALLBACK_EXAM_ID)
                .userId(OWNER_USER_ID)
                .createdAt(LocalDateTime.now())
                .build());

        mockMvc.perform(post("/api/v1/exams/callback/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "user_id": "ex_callback_security_test",
                                  "mock_exam_id": "mock_exam_003",
                                  "part_number": 1,
                                  "question_number": 1,
                                  "retry_count": 0,
                                  "score": 5.0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));

        verify(examSessionRepository).findById(CALLBACK_EXAM_ID);
        verify(examResultRepository).insert(any(ExamResult.class));
    }

    @Test
    void openApiDocsPathBypassesAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }

    private String createExam(String token) throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(responseBody).path("result").path("examId").asText();
    }

    private void expectUnauthorized(org.springframework.test.web.servlet.ResultActions resultActions)
            throws Exception {
        resultActions
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON401"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    private static Stream<Arguments> invalidTokens() {
        Instant now = Instant.now();
        return Stream.of(
                Arguments.of(Named.of(
                        "잘못된 서명",
                        token(
                                INVALID_SIGNING_KEY,
                                VALID_SIGNING_KEY.getKeyID(),
                                OWNER_USER_ID,
                                ISSUER,
                                AUDIENCE,
                                now.minusSeconds(10),
                                now.minusSeconds(10),
                                now.plusSeconds(300)
                        )
                )),
                Arguments.of(Named.of(
                        "만료 토큰",
                        token(
                                VALID_SIGNING_KEY,
                                VALID_SIGNING_KEY.getKeyID(),
                                OWNER_USER_ID,
                                ISSUER,
                                AUDIENCE,
                                now.minusSeconds(600),
                                now.minusSeconds(600),
                                now.minusSeconds(300)
                        )
                )),
                Arguments.of(Named.of(
                        "미래 nbf",
                        token(
                                VALID_SIGNING_KEY,
                                VALID_SIGNING_KEY.getKeyID(),
                                OWNER_USER_ID,
                                ISSUER,
                                AUDIENCE,
                                now,
                                now.plusSeconds(300),
                                now.plusSeconds(600)
                        )
                )),
                Arguments.of(Named.of(
                        "잘못된 issuer",
                        token(
                                VALID_SIGNING_KEY,
                                VALID_SIGNING_KEY.getKeyID(),
                                OWNER_USER_ID,
                                "http://wrong-issuer.test",
                                AUDIENCE,
                                now.minusSeconds(10),
                                now.minusSeconds(10),
                                now.plusSeconds(300)
                        )
                )),
                Arguments.of(Named.of(
                        "잘못된 audience",
                        token(
                                VALID_SIGNING_KEY,
                                VALID_SIGNING_KEY.getKeyID(),
                                OWNER_USER_ID,
                                ISSUER,
                                "wrong-audience",
                                now.minusSeconds(10),
                                now.minusSeconds(10),
                                now.plusSeconds(300)
                        )
                )),
                Arguments.of(Named.of(
                        "UUID가 아닌 sub",
                        token(
                                VALID_SIGNING_KEY,
                                VALID_SIGNING_KEY.getKeyID(),
                                "not-a-uuid",
                                ISSUER,
                                AUDIENCE,
                                now.minusSeconds(10),
                                now.minusSeconds(10),
                                now.plusSeconds(300)
                        )
                ))
        );
    }

    private static String validToken(String subject) {
        Instant now = Instant.now();
        return token(
                VALID_SIGNING_KEY,
                VALID_SIGNING_KEY.getKeyID(),
                subject,
                ISSUER,
                AUDIENCE,
                now.minusSeconds(10),
                now.minusSeconds(10),
                now.plusSeconds(300)
        );
    }

    private static String token(
            RSAKey signingKey,
            String keyId,
            String subject,
            String issuer,
            String audience,
            Instant issuedAt,
            Instant notBefore,
            Instant expiresAt) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(subject)
                    .audience(audience)
                    .issueTime(Date.from(issuedAt))
                    .notBeforeTime(Date.from(notBefore))
                    .expirationTime(Date.from(expiresAt))
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(keyId)
                            .type(JOSEObjectType.JWT)
                            .build(),
                    claims
            );
            jwt.sign(new RSASSASigner(signingKey.toPrivateKey()));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create test JWT", exception);
        }
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static RSAKey generateRsaKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create test RSA key", exception);
        }
    }

    private static HttpServer startJwksServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            byte[] jwks = new JWKSet(VALID_SIGNING_KEY.toPublicJWK())
                    .toString()
                    .getBytes(StandardCharsets.UTF_8);
            server.createContext("/.well-known/jwks.json", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
                exchange.sendResponseHeaders(200, jwks.length);
                try (var responseBody = exchange.getResponseBody()) {
                    responseBody.write(jwks);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start test JWKS server", exception);
        }
    }
}
