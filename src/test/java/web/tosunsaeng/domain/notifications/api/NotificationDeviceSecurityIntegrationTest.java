package web.tosunsaeng.domain.notifications.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import web.tosunsaeng.domain.notifications.application.ExpoPushTokenValidator;
import web.tosunsaeng.domain.notifications.application.NotificationDeviceService;
import web.tosunsaeng.domain.notifications.application.NotificationIdentityCodec;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationDevice;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationDeliveryRepository;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationDeviceRepository;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationOutboxRepository;
import web.tosunsaeng.domain.notifications.dto.NotificationDeviceRequest;
import web.tosunsaeng.global.auth.JwtCurrentUserProvider;
import web.tosunsaeng.global.config.SecurityConfig;
import web.tosunsaeng.global.config.security.SecurityErrorResponseHandler;
import web.tosunsaeng.global.exception.GlobalExceptionAdvice;
import web.tosunsaeng.domain.exams.domain.repository.AzureResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;
import web.tosunsaeng.domain.exams.domain.repository.MockExamRepository;
import web.tosunsaeng.domain.exams.domain.repository.QuestionGradingJobRepository;
import web.tosunsaeng.domain.exams.domain.repository.QuestionRepository;
import web.tosunsaeng.domain.exams.domain.repository.SpeechAceResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.SummaryGradingJobRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(NotificationDeviceController.class)
@Import({
        SecurityConfig.class,
        SecurityErrorResponseHandler.class,
        JwtCurrentUserProvider.class,
        NotificationDeviceService.class,
        NotificationIdentityCodec.class,
        ExpoPushTokenValidator.class,
        GlobalExceptionAdvice.class,
        NotificationDeviceSecurityIntegrationTest.FixedClockConfig.class
})
class NotificationDeviceSecurityIntegrationTest {

    private static final String OWNER_ID = "00000000-0000-4000-8000-000000000042";
    private static final String GUEST_ID = "00000000-0000-4000-8000-000000000099";
    private static final String INSTALLATION_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TOKEN = "ExponentPushToken[placeholder-value]";
    private static final Instant NOW = Instant.parse("2026-08-04T06:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationIdentityCodec codec;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private NotificationDeviceRepository deviceRepository;

    @MockitoBean
    private NotificationOutboxRepository outboxRepository;

    @MockitoBean
    private NotificationDeliveryRepository deliveryRepository;

    @MockitoBean
    private AzureResultRepository azureResultRepository;

    @MockitoBean
    private ExamResultRepository examResultRepository;

    @MockitoBean
    private ExamSessionRepository examSessionRepository;

    @MockitoBean
    private ExamSummaryRepository examSummaryRepository;

    @MockitoBean
    private MockExamRepository mockExamRepository;

    @MockitoBean
    private QuestionGradingJobRepository questionGradingJobRepository;

    @MockitoBean
    private QuestionRepository questionRepository;

    @MockitoBean
    private SpeechAceResultRepository speechAceResultRepository;

    @MockitoBean
    private SummaryGradingJobRepository summaryGradingJobRepository;

    @DynamicPropertySource
    static void authProperties(DynamicPropertyRegistry registry) {
        registry.add("app.auth.mode", () -> "jwt");
        registry.add("app.auth.identity.issuer", () -> "https://identity.example.test");
        registry.add("app.auth.identity.jwk-set-uri", () -> "https://identity.example.test/jwks");
        registry.add("app.auth.identity.audience", () -> "tosunsaeng-learning-core");
    }

    @BeforeEach
    void setUp() {
        reset(deviceRepository);
        when(jwtDecoder.decode("owner-access-token")).thenReturn(jwt(OWNER_ID));
        when(jwtDecoder.decode("guest-access-token")).thenReturn(jwt(GUEST_ID));
        when(deviceRepository.findByUserIdAndInstallationIdHash(anyString(), anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void putWithoutBearerTokenReturnsBaseResponse401() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/devices")
                        .contentType("application/json")
                        .content(requestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON401"));

        verify(deviceRepository, never()).insert(any(NotificationDevice.class));
    }

    @Test
    void jwtSubjectRegistersDeviceWithoutSensitiveResponseFields() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/devices")
                        .header("Authorization", "Bearer owner-access-token")
                        .contentType("application/json")
                        .content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.message").value("알림 기기가 등록되었습니다."))
                .andExpect(jsonPath("$.result.registered").value(true))
                .andExpect(jsonPath("$..expoPushToken").doesNotExist())
                .andExpect(jsonPath("$..expoPushTokenHash").doesNotExist())
                .andExpect(jsonPath("$..installationIdHash").doesNotExist())
                .andExpect(jsonPath("$..userId").doesNotExist());

        verify(deviceRepository).insert(org.mockito.ArgumentMatchers.<NotificationDevice>argThat(device ->
                OWNER_ID.equals(device.getUserId())
                        && codec.installationIdHash(INSTALLATION_ID)
                        .equals(device.getInstallationIdHash())
                        && TOKEN.equals(device.getExpoPushToken())
        ));
    }

    @Test
    void guestAccessTokenRegistersLikeAnyOtherJwtSubject() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/devices")
                        .header("Authorization", "Bearer guest-access-token")
                        .contentType("application/json")
                        .content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.registered").value(true));

        verify(deviceRepository).insert(org.mockito.ArgumentMatchers.<NotificationDevice>argThat(device ->
                GUEST_ID.equals(device.getUserId())));
    }

    @Test
    void deleteUsesOnlyJwtOwnerAndRemainsIdempotent() throws Exception {
        String hash = codec.installationIdHash(INSTALLATION_ID);

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete("/api/v1/notifications/devices/{installationId}", INSTALLATION_ID)
                            .header("Authorization", "Bearer owner-access-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("알림 기기가 비활성화되었습니다."))
                    .andExpect(jsonPath("$.result.disabled").value(true));
        }

        verify(deviceRepository, times(2)).disableOwnedDevice(OWNER_ID, hash, NOW);
        verify(deviceRepository, never()).deleteById(anyString());
    }

    @Test
    void anotherJwtUserCannotModifyOwnersDeviceBecauseUpdateIncludesSubject() throws Exception {
        String hash = codec.installationIdHash(INSTALLATION_ID);

        mockMvc.perform(delete("/api/v1/notifications/devices/{installationId}", INSTALLATION_ID)
                        .header("Authorization", "Bearer guest-access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.disabled").value(true));

        verify(deviceRepository).disableOwnedDevice(GUEST_ID, hash, NOW);
        verify(deviceRepository, never()).disableOwnedDevice(OWNER_ID, hash, NOW);
    }

    @Test
    void requestContractHasNoUserIdField() {
        assertEquals(3, NotificationDeviceRequest.Register.class.getRecordComponents().length);
        for (var component : NotificationDeviceRequest.Register.class.getRecordComponents()) {
            assertFalse(component.getName().equals("userId"));
        }
    }

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(NOW.minusSeconds(60))
                .expiresAt(NOW.plusSeconds(3600))
                .build();
    }

    private static String requestJson() {
        return """
                {
                  "installationId": "550e8400-e29b-41d4-a716-446655440000",
                  "platform": "IOS",
                  "expoPushToken": "ExponentPushToken[placeholder-value]"
                }
                """;
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock notificationTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
