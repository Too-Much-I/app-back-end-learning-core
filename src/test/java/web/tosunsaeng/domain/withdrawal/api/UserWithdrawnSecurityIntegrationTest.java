package web.tosunsaeng.domain.withdrawal.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import web.tosunsaeng.domain.withdrawal.application.UserWithdrawnEventConsumerService;
import web.tosunsaeng.domain.withdrawal.application.UserWithdrawnEventException;
import web.tosunsaeng.domain.withdrawal.application.UserWithdrawnMetrics;
import web.tosunsaeng.domain.withdrawal.domain.WithdrawnUserAccessDeny;
import web.tosunsaeng.domain.withdrawal.repository.WithdrawnUserAccessDenyRepository;
import web.tosunsaeng.domain.withdrawal.repository.UserWithdrawnEventInboxRepository;
import web.tosunsaeng.domain.withdrawal.security.UserWithdrawnAccessGateFilter;
import web.tosunsaeng.global.config.SecurityConfig;
import web.tosunsaeng.global.config.security.SecurityErrorResponseHandler;
import web.tosunsaeng.global.sentry.UnexpectedExceptionReporter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(controllers = {
        UserWithdrawnEventController.class,
        UserWithdrawnSecurityIntegrationTest.ProbeController.class
})
@ContextConfiguration(classes = {
        SecurityConfig.class,
        SecurityErrorResponseHandler.class,
        UserWithdrawnEventController.class,
        UserWithdrawnEventExceptionHandler.class,
        UserWithdrawnSecurityIntegrationTest.ProbeController.class,
        UserWithdrawnSecurityIntegrationTest.FilterTestConfiguration.class
})
@Import({
        SecurityConfig.class,
        SecurityErrorResponseHandler.class,
        UserWithdrawnEventExceptionHandler.class,
        UserWithdrawnSecurityIntegrationTest.FilterTestConfiguration.class
})
@TestPropertySource(properties = {
        "app.auth.mode=jwt",
        "app.auth.identity.issuer=http://identity.test",
        "app.auth.identity.jwk-set-uri=http://identity.test/.well-known/jwks.json",
        "app.auth.identity.audience=tosunsaeng-learning-core",
        "app.auth.identity.clock-skew=PT60S",
        "app.user-withdrawn.consumer-enabled=true",
        "app.user-withdrawn.deny-gate-enabled=true"
})
class UserWithdrawnSecurityIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-27T10:00:00Z");
    private static final String USER_ID = "00000000-0000-0000-0000-000000000042";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserWithdrawnEventConsumerService consumerService;

    @MockitoBean
    private WithdrawnUserAccessDenyRepository denyRepository;

    @MockitoBean
    private UserWithdrawnEventInboxRepository inboxRepository;

    @MockitoBean(name = "jwtDecoder")
    private JwtDecoder userJwtDecoder;

    @MockitoBean(name = "userWithdrawnWorkloadJwtDecoder")
    private JwtDecoder workloadJwtDecoder;

    @MockitoBean
    private UnexpectedExceptionReporter unexpectedExceptionReporter;

    @BeforeEach
    void setUp() {
        reset(userJwtDecoder, workloadJwtDecoder, denyRepository, consumerService);
        when(userJwtDecoder.decode("user-token")).thenReturn(userJwt());
        when(userJwtDecoder.decode("workload-token")).thenThrow(new BadJwtException("wrong profile"));
        when(userJwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid"));
        when(workloadJwtDecoder.decode("workload-token")).thenReturn(workloadJwt());
        when(workloadJwtDecoder.decode("user-token")).thenThrow(new BadJwtException("wrong profile"));
        when(workloadJwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid"));
        when(denyRepository.findById(USER_ID)).thenReturn(Optional.empty());
    }

    @Test
    void workloadCredentialCanConsumeWithoutUserDenyLookup() throws Exception {
        mockMvc.perform(post("/internal/v1/events/withdrawn")
                        .header("Authorization", "Bearer workload-token")
                        .contentType("application/json")
                        .content(validPayload()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(consumerService).consume(any(UserWithdrawnEventRequest.class));
        verify(denyRepository, never()).findById(any());
    }

    @Test
    void userCredentialCannotCallWorkloadEndpoint() throws Exception {
        expectCommon401(post("/internal/v1/events/withdrawn")
                .header("Authorization", "Bearer user-token")
                .contentType("application/json")
                .content(validPayload()));

        verify(consumerService, never()).consume(any());
        verify(denyRepository, never()).findById(any());
    }

    @Test
    void workloadCredentialCannotCallUserEndpoint() throws Exception {
        expectCommon401(get("/protected-probe")
                .header("Authorization", "Bearer workload-token"));
        verify(denyRepository, never()).findById(any());
    }

    @Test
    void activeMarkerRejectsValidUserJwtBeforeController() throws Exception {
        when(denyRepository.findById(USER_ID)).thenReturn(Optional.of(marker(NOW.plusSeconds(30))));

        mockMvc.perform(get("/protected-probe")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("ACCOUNT_WITHDRAWN"))
                .andExpect(jsonPath("$.message").value("탈퇴 처리된 계정입니다."))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void expiredMarkerAllowsValidUserJwt() throws Exception {
        when(denyRepository.findById(USER_ID)).thenReturn(Optional.of(marker(NOW)));

        mockMvc.perform(get("/protected-probe")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void markerStoreFailureIsFailClosed() throws Exception {
        when(denyRepository.findById(USER_ID)).thenThrow(new IllegalStateException("unavailable"));

        mockMvc.perform(get("/protected-probe")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_DENY_GATE_UNAVAILABLE"));
    }

    @Test
    void invalidUserJwtDoesNotReadMarker() throws Exception {
        expectCommon401(get("/protected-probe")
                .header("Authorization", "Bearer invalid-token"));
        verify(denyRepository, never()).findById(any());
    }

    @Test
    void publicCallbackRemainsPublicAndSkipsDenyGate() throws Exception {
        mockMvc.perform(get("/api/v1/exams/callback/probe"))
                .andExpect(status().isOk())
                .andExpect(content().string("callback"));
        verify(denyRepository, never()).findById(any());
    }

    @Test
    void consumerMapsPayloadErrorsWithoutInternalDetails() throws Exception {
        when(consumerService.consume(any())).thenThrow(new UserWithdrawnEventException(
                UserWithdrawnEventException.Reason.PAYLOAD_CONFLICT
        ));

        mockMvc.perform(post("/internal/v1/events/withdrawn")
                        .header("Authorization", "Bearer workload-token")
                        .contentType("application/json")
                        .content(validPayload()))
                .andExpect(status().isConflict())
                .andExpect(content().string(""));
    }

    private void expectCommon401(org.springframework.test.web.servlet.RequestBuilder request)
            throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON401"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    private static String validPayload() {
        return """
                {
                  "eventId": "00000000-0000-0000-0000-000000000109",
                  "schemaVersion": 1,
                  "userId": "00000000-0000-0000-0000-000000000042",
                  "withdrawnAt": "2026-08-27T09:59:00Z"
                }
                """;
    }

    private static Jwt userJwt() {
        return Jwt.withTokenValue("user-token")
                .header("alg", "RS256")
                .issuer("http://identity.test")
                .subject(USER_ID)
                .audience(java.util.List.of("tosunsaeng-learning-core"))
                .issuedAt(NOW.minusSeconds(30))
                .expiresAt(NOW.plusSeconds(300))
                .build();
    }

    private static Jwt workloadJwt() {
        return Jwt.withTokenValue("workload-token")
                .header("alg", "RS256")
                .issuer("http://workload.test")
                .subject("identity-service")
                .claim("service", "identity")
                .audience(java.util.List.of("learning-core-user-withdrawn"))
                .issuedAt(NOW.minusSeconds(30))
                .expiresAt(NOW.plusSeconds(300))
                .build();
    }

    private static WithdrawnUserAccessDeny marker(Instant blockedUntil) {
        return new WithdrawnUserAccessDeny(
                USER_ID,
                "00000000-0000-0000-0000-000000000109",
                NOW.minusSeconds(60),
                blockedUntil,
                blockedUntil,
                NOW.minusSeconds(60)
        );
    }

    @RestController
    static class ProbeController {
        @GetMapping("/protected-probe")
        String protectedProbe() {
            return "ok";
        }

        @GetMapping("/api/v1/exams/callback/probe")
        String callbackProbe() {
            return "callback";
        }
    }

    @TestConfiguration
    static class FilterTestConfiguration {
        @Bean
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        UserWithdrawnAccessGateFilter userWithdrawnAccessGateFilter(
                WithdrawnUserAccessDenyRepository repository,
                Clock testClock,
                SecurityErrorResponseHandler responseHandler,
                UserWithdrawnMetrics metrics) {
            return new UserWithdrawnAccessGateFilter(repository, testClock, responseHandler, metrics);
        }

        @Bean
        UserWithdrawnMetrics userWithdrawnMetrics() {
            return new UserWithdrawnMetrics(new SimpleMeterRegistry());
        }
    }
}
