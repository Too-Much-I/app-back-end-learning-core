package web.tosunsaeng.domain.challenge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import web.tosunsaeng.global.config.SecurityConfig;
import web.tosunsaeng.global.config.security.SecurityErrorResponseHandler;
import web.tosunsaeng.global.sentry.UnexpectedExceptionReporter;
import web.tosunsaeng.domain.withdrawal.security.UserWithdrawnAccessGateFilter;
import web.tosunsaeng.domain.withdrawal.repository.WithdrawnUserAccessDenyRepository;
import web.tosunsaeng.domain.usermerge.security.MergedUserAccessGateFilter;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@WebMvcTest(controllers = {ChallengeController.class, ChallengeCallbackController.class}, properties = {
        "app.auth.mode=jwt", "app.auth.identity.issuer=https://identity.example.test",
        "app.auth.identity.jwk-set-uri=https://identity.example.test/jwks", "app.auth.identity.audience=fixture",
        "app.challenge.enabled=true", "app.challenge.callback-credential=synthetic-callback-fixture"})
@Import({SecurityConfig.class, SecurityErrorResponseHandler.class, ChallengeSecurityConfiguration.class, ChallengeExceptionAdvice.class})
@ContextConfiguration(classes = ChallengeSecurityTest.Slice.class)
class ChallengeSecurityTest {
    @Configuration
    @Import({ChallengeController.class, ChallengeCallbackController.class})
    static class Slice {
        @Bean UserWithdrawnAccessGateFilter withdrawalGate(WithdrawnUserAccessDenyRepository repository, SecurityErrorResponseHandler errors) {
            return new UserWithdrawnAccessGateFilter(repository, java.time.Clock.fixed(ChallengeContractTest.NOW, java.time.ZoneOffset.UTC), errors,
                    new web.tosunsaeng.domain.withdrawal.application.UserWithdrawnMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
        }
        @Bean MergedUserAccessGateFilter mergeGate(UserOwnershipGuardRepository repository, SecurityErrorResponseHandler errors) {
            return new MergedUserAccessGateFilter(repository, errors);
        }
        @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<UserWithdrawnAccessGateFilter> withdrawalRegistration(UserWithdrawnAccessGateFilter filter) {
            var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter); registration.setEnabled(false); return registration;
        }
        @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<MergedUserAccessGateFilter> mergedRegistration(MergedUserAccessGateFilter filter) {
            var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter); registration.setEnabled(false); return registration;
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ChallengeProperties properties;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ChallengeService service;
    @MockitoBean ChallengeCallbackService callbacks;
    @MockitoBean UnexpectedExceptionReporter reporter;
    @MockitoBean WithdrawnUserAccessDenyRepository withdrawalRepository;
    @MockitoBean UserOwnershipGuardRepository guardRepository;
    @BeforeEach void setup() {
        properties.setEnabled(true);
        when(jwtDecoder.decode("member-fixture")).thenReturn(Jwt.withTokenValue("member-fixture").header("alg", "RS256")
                .subject(ChallengeContractTest.OWNER).claim("account_type", "MEMBER").build());
        when(jwtDecoder.decode("guest-fixture")).thenReturn(Jwt.withTokenValue("guest-fixture").header("alg", "RS256")
                .subject(ChallengeContractTest.OWNER).claim("account_type", "GUEST").build());
        when(jwtDecoder.decode("invalid-fixture")).thenThrow(new BadJwtException("invalid"));
    }
    @Test void memberAllowedGuestForbiddenInvalidAndMissingJwtUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/challenges/today").header("Authorization", "Bearer member-fixture")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("COMMON_200"));
        verify(service).today(ChallengeContractTest.OWNER);
        mvc.perform(get("/api/v1/challenges/today").header("Authorization", "Bearer guest-fixture")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COMMON403"));
        mvc.perform(get("/api/v1/challenges/today").header("Authorization", "Bearer invalid-fixture")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/challenges/today")).andExpect(status().isUnauthorized());
    }
    @Test void callbackUsesDedicatedCredentialNotUserDecoder() throws Exception {
        byte[] body = ChallengeCallback.JSON.writeValueAsBytes(ChallengeContractTest.callback("no_speech"));
        mvc.perform(post(ChallengeCallbackController.PATH).header("Authorization", "Bearer synthetic-callback-fixture")
                .header("X-Challenge-Contract-Version", "v1").contentType("application/json").content(body)).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(callbacks).accept(any()); verifyNoInteractions(jwtDecoder);
        mvc.perform(post(ChallengeCallbackController.PATH).header("Authorization", "Bearer member-fixture").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(ChallengeCallbackController.PATH).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(get(ChallengeCallbackController.PATH).header("Authorization", "Bearer synthetic-callback-fixture")).andExpect(status().isForbidden());
    }
    @Test void offDeniesBothSurfacesAndOversizeIsNotBaseResponse() throws Exception {
        mvc.perform(post(ChallengeCallbackController.PATH).header("Authorization", "Bearer synthetic-callback-fixture")
                .header("X-Challenge-Contract-Version", "v1").contentType("application/json").content(new byte[16385]))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("CALLBACK_PAYLOAD_TOO_LARGE")).andExpect(jsonPath("$.isSuccess").doesNotExist());
        properties.setEnabled(false);
        mvc.perform(post(ChallengeCallbackController.PATH).header("Authorization", "Bearer synthetic-callback-fixture")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/challenges/today").header("Authorization", "Bearer member-fixture")).andExpect(status().isForbidden());
        verifyNoInteractions(service, callbacks);
    }
    @Test void rejectsUnknownBodyFieldsAndReturnsDateChangedPayload() throws Exception {
        mvc.perform(post("/api/v1/challenges/today/questions/1/answer").header("Authorization", "Bearer member-fixture")
                .header("Idempotency-Key", ChallengeContractTest.ID).contentType("application/json")
                .content("{\"attemptId\":\"" + ChallengeContractTest.ID + "\",\"userId\":\"spoof\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON400"));
        when(service.question(anyString(), anyInt(), anyString())).thenThrow(new ChallengeFailure(409, "CHALLENGE_DATE_CHANGED",
                new ChallengeViews.DateInfo("2026-09-08", java.time.Instant.parse("2026-09-08T15:00:00Z"), 86400)));
        mvc.perform(get("/api/v1/challenges/today/questions/1").header("Authorization", "Bearer member-fixture").header("X-Challenge-Date", "2026-09-07"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.result.challengeDate").value("2026-09-08"));
    }
    @Test void memberStillPassesExistingWithdrawalAndMergedDenies() throws Exception {
        var now = ChallengeContractTest.NOW;
        when(withdrawalRepository.findById(ChallengeContractTest.OWNER)).thenReturn(java.util.Optional.of(
                new web.tosunsaeng.domain.withdrawal.domain.WithdrawnUserAccessDeny(ChallengeContractTest.OWNER, "fixture-event", now, now.plusSeconds(100), now.plusSeconds(100), now)));
        mvc.perform(get("/api/v1/challenges/today").header("Authorization", "Bearer member-fixture")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCOUNT_WITHDRAWN"));
        when(withdrawalRepository.findById(ChallengeContractTest.OWNER)).thenReturn(java.util.Optional.empty());
        when(guardRepository.findById(ChallengeContractTest.OWNER)).thenReturn(java.util.Optional.of(
                new web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard(ChallengeContractTest.OWNER,
                        web.tosunsaeng.domain.usermerge.domain.OwnershipGuardState.MERGED, 1, "fixture-target", now, "fixture-event", now, now)));
        mvc.perform(get("/api/v1/challenges/today").header("Authorization", "Bearer member-fixture")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_MERGED_TOKEN_REJECTED"));
        verifyNoInteractions(service);
    }
}
