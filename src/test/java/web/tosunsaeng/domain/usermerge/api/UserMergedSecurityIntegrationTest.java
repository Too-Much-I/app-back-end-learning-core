package web.tosunsaeng.domain.usermerge.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import web.tosunsaeng.domain.usermerge.application.UserMergedConsumerService;
import web.tosunsaeng.domain.usermerge.config.UserMergedProperties;
import web.tosunsaeng.domain.usermerge.config.UserMergedSecurityConfig;
import web.tosunsaeng.domain.usermerge.domain.OwnershipGuardState;
import web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;
import web.tosunsaeng.domain.usermerge.security.MergedUserAccessGateFilter;
import web.tosunsaeng.global.config.SecurityConfig;
import web.tosunsaeng.global.config.security.SecurityErrorResponseHandler;
import web.tosunsaeng.global.sentry.UnexpectedExceptionReporter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(controllers = {
        UserMergedInternalController.class,
        UserMergedSecurityIntegrationTest.ProbeController.class
})
@ContextConfiguration(classes = {
        SecurityConfig.class,
        UserMergedSecurityConfig.class,
        SecurityErrorResponseHandler.class,
        UserMergedInternalController.class,
        UserMergedInternalExceptionAdvice.class,
        UserMergedSecurityIntegrationTest.ProbeController.class,
        UserMergedSecurityIntegrationTest.FilterConfiguration.class
})
@Import({
        SecurityConfig.class,
        UserMergedSecurityConfig.class,
        SecurityErrorResponseHandler.class,
        UserMergedInternalExceptionAdvice.class,
        UserMergedSecurityIntegrationTest.FilterConfiguration.class
})
@TestPropertySource(properties = {
        "app.auth.mode=jwt",
        "app.auth.identity.issuer=http://identity.test",
        "app.auth.identity.jwk-set-uri=http://identity.test/.well-known/jwks.json",
        "app.auth.identity.audience=tosunsaeng-learning-core",
        "app.auth.identity.clock-skew=PT60S",
        "app.user-merged.consumer-enabled=true"
})
class UserMergedSecurityIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-04T01:00:00Z");
    private static final String USER_ID = "00000000-0000-4000-8000-000000000042";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserMergedConsumerService consumerService;

    @MockitoBean
    private UserOwnershipGuardRepository guardRepository;

    @MockitoBean(name = "jwtDecoder")
    private JwtDecoder userJwtDecoder;

    @MockitoBean(name = "userMergedWorkloadJwtDecoder")
    private JwtDecoder workloadJwtDecoder;

    @MockitoBean
    private UnexpectedExceptionReporter unexpectedExceptionReporter;

    @BeforeEach
    void setUp() {
        reset(userJwtDecoder, workloadJwtDecoder, guardRepository, consumerService);
        when(userJwtDecoder.decode("user-token")).thenReturn(userJwt());
        when(userJwtDecoder.decode("workload-token"))
                .thenThrow(new BadJwtException("wrong credential profile"));
        when(workloadJwtDecoder.decode("workload-token"))
                .thenReturn(workloadJwt("identity-service"));
        when(workloadJwtDecoder.decode("wrong-principal-token"))
                .thenReturn(workloadJwt("other-service"));
        when(workloadJwtDecoder.decode("user-token"))
                .thenThrow(new BadJwtException("wrong credential profile"));
        when(guardRepository.findById(USER_ID)).thenReturn(Optional.empty());
    }

    @Test
    void workloadCredentialConsumesThroughTheDedicatedChain() throws Exception {
        mockMvc.perform(post("/internal/v1/events/user-merged")
                        .header("Authorization", "Bearer workload-token")
                        .contentType("application/json")
                        .content(validPayload()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(consumerService).consume(any(UserMergedEventRequest.class));
        verify(guardRepository, never()).findById(any());
    }

    @Test
    void transactionWrapperIsMappedToRetryableEmptyServiceUnavailable() throws Exception {
        when(consumerService.consume(any())).thenThrow(
                new TransactionSystemException("commit outcome unavailable")
        );

        mockMvc.perform(post("/internal/v1/events/user-merged")
                        .header("Authorization", "Bearer workload-token")
                        .contentType("application/json")
                        .content(validPayload()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                .andExpect(content().string(""));
    }

    @Test
    void userCredentialCannotCallWorkloadEndpoint() throws Exception {
        mockMvc.perform(post("/internal/v1/events/user-merged")
                        .header("Authorization", "Bearer user-token")
                        .contentType("application/json")
                        .content(validPayload()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401"));

        verify(consumerService, never()).consume(any());
    }

    @Test
    void validWorkloadCredentialWithWrongPrincipalIsForbidden() throws Exception {
        mockMvc.perform(post("/internal/v1/events/user-merged")
                        .header("Authorization", "Bearer wrong-principal-token")
                        .contentType("application/json")
                        .content(validPayload()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON403"));

        verify(consumerService, never()).consume(any());
    }

    @Test
    void authenticationAndPrincipalAuthorizationRunBeforeBodyBuffering() throws Exception {
        String oversized = "x".repeat(UserMergedProperties.MAX_BODY_BYTES + 1);

        mockMvc.perform(post("/internal/v1/events/user-merged")
                        .contentType("application/json")
                        .content(oversized))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/internal/v1/events/user-merged")
                        .header("Authorization", "Bearer wrong-principal-token")
                        .contentType("application/json")
                        .content(oversized))
                .andExpect(status().isForbidden());
    }

    @Test
    void workloadCredentialCannotCallUserEndpoint() throws Exception {
        mockMvc.perform(get("/protected-probe")
                        .header("Authorization", "Bearer workload-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401"));
    }

    @Test
    void mergedSourceTokenIsRejectedBeforeUserController() throws Exception {
        when(guardRepository.findById(USER_ID)).thenReturn(Optional.of(mergedGuard()));

        mockMvc.perform(get("/protected-probe")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_MERGED_TOKEN_REJECTED"));
    }

    @Test
    void guardStoreFailureIsFailClosed() throws Exception {
        when(guardRepository.findById(USER_ID))
                .thenThrow(new IllegalStateException("unavailable"));

        mockMvc.perform(get("/protected-probe")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("USER_MERGED_DENY_GATE_UNAVAILABLE"));
    }

    @Test
    void payloadLimitAccepts4096BytesAndRejects4097Bytes() throws Exception {
        mockMvc.perform(post("/internal/v1/events/user-merged")
                        .header("Authorization", "Bearer workload-token")
                        .contentType("application/json")
                        .content(paddedPayload(UserMergedProperties.MAX_BODY_BYTES)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/internal/v1/events/user-merged")
                        .header("Authorization", "Bearer workload-token")
                        .contentType("application/json")
                        .content(paddedPayload(UserMergedProperties.MAX_BODY_BYTES + 1)))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void nonJsonPayloadIsRejectedAfterAuthentication() throws Exception {
        mockMvc.perform(post("/internal/v1/events/user-merged")
                        .header("Authorization", "Bearer workload-token")
                        .contentType("text/plain")
                        .content(validPayload()))
                .andExpect(status().isUnsupportedMediaType());
    }

    private static String validPayload() {
        return """
                {
                  "eventId": "00000000-0000-4000-8000-000000000109",
                  "schemaVersion": 1,
                  "sourceUserId": "00000000-0000-4000-8000-000000000042",
                  "targetUserId": "00000000-0000-4000-8000-000000000043",
                  "occurredAt": "2026-09-04T00:59:00Z"
                }
                """;
    }

    private static String paddedPayload(int bytes) {
        String payload = validPayload().stripTrailing();
        return payload + " ".repeat(bytes - payload.length());
    }

    private static Jwt userJwt() {
        return Jwt.withTokenValue("user-token")
                .header("alg", "RS256")
                .issuer("http://identity.test")
                .subject(USER_ID)
                .audience(List.of("tosunsaeng-learning-core"))
                .issuedAt(NOW.minusSeconds(30))
                .expiresAt(NOW.plusSeconds(300))
                .build();
    }

    private static Jwt workloadJwt(String subject) {
        return Jwt.withTokenValue("workload-token")
                .header("alg", "RS256")
                .header("typ", "JWT")
                .header("kid", "test-key")
                .issuer("http://workload.test")
                .subject(subject)
                .audience(List.of(UserMergedProperties.AUDIENCE))
                .issuedAt(NOW.minusSeconds(30))
                .notBefore(NOW.minusSeconds(30))
                .expiresAt(NOW.plusSeconds(30))
                .claim("jti", "00000000-0000-4000-8000-000000000110")
                .build();
    }

    private static UserOwnershipGuard mergedGuard() {
        return new UserOwnershipGuard(
                USER_ID,
                OwnershipGuardState.MERGED,
                2L,
                "00000000-0000-4000-8000-000000000043",
                NOW.minusSeconds(60),
                "00000000-0000-4000-8000-000000000109",
                NOW.minusSeconds(120),
                NOW.minusSeconds(60)
        );
    }

    @RestController
    static class ProbeController {
        @GetMapping("/protected-probe")
        String protectedProbe() {
            return "ok";
        }
    }

    @TestConfiguration
    static class FilterConfiguration {
        @Bean
        MergedUserAccessGateFilter mergedUserAccessGateFilter(
                UserOwnershipGuardRepository repository,
                SecurityErrorResponseHandler responseHandler
        ) {
            return new MergedUserAccessGateFilter(repository, responseHandler);
        }
    }
}
