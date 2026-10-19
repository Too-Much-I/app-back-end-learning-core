package web.tosunsaeng.domain.usermerge.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import web.tosunsaeng.domain.usermerge.application.UserMergedConsumerService;
import web.tosunsaeng.domain.usermerge.config.UserMergedProperties;
import web.tosunsaeng.domain.usermerge.config.UserMergedSecurityConfig;
import web.tosunsaeng.global.config.SecurityConfig;
import web.tosunsaeng.global.config.security.SecurityErrorResponseHandler;
import web.tosunsaeng.global.sentry.UnexpectedExceptionReporter;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(controllers = UserMergedInternalController.class)
@ContextConfiguration(classes = {
        SecurityConfig.class,
        UserMergedSecurityConfig.class,
        SecurityErrorResponseHandler.class,
        UserMergedInternalController.class,
        UserMergedInternalExceptionAdvice.class
})
@Import({
        SecurityConfig.class,
        UserMergedSecurityConfig.class,
        SecurityErrorResponseHandler.class,
        UserMergedInternalExceptionAdvice.class
})
@TestPropertySource(properties = {
        "app.auth.mode=legacy",
        "app.user-merged.consumer-enabled=true"
})
class UserMergedLegacyIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserMergedConsumerService consumerService;

    @MockitoBean(name = "userMergedWorkloadJwtDecoder")
    private JwtDecoder workloadJwtDecoder;

    @MockitoBean
    private UnexpectedExceptionReporter unexpectedExceptionReporter;

    @BeforeEach
    void setUp() {
        when(workloadJwtDecoder.decode("workload-token")).thenReturn(workloadJwt());
    }

    @Test
    void legacyPermitAllChainCannotOpenInternalEndpoint() throws Exception {
        mockMvc.perform(post("/internal/v1/events/user-merged")
                        .contentType("application/json")
                        .content(validPayload()))
                .andExpect(status().isUnauthorized());

        verify(consumerService, never()).consume(any());
    }

    @Test
    void workloadTokenStillUsesTheHigherPriorityInternalChain() throws Exception {
        mockMvc.perform(post("/internal/v1/events/user-merged")
                        .header("Authorization", "Bearer workload-token")
                        .contentType("application/json")
                        .content(validPayload()))
                .andExpect(status().isNoContent());
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

    private static Jwt workloadJwt() {
        Instant now = Instant.parse("2026-09-04T01:00:00Z");
        return Jwt.withTokenValue("workload-token")
                .header("alg", "RS256")
                .header("typ", "JWT")
                .header("kid", "test-key")
                .issuer("http://workload.test")
                .subject("identity-service")
                .audience(List.of(UserMergedProperties.AUDIENCE))
                .issuedAt(now.minusSeconds(30))
                .notBefore(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(30))
                .claim("jti", "00000000-0000-4000-8000-000000000110")
                .build();
    }
}
