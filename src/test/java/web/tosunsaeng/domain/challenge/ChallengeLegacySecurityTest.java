package web.tosunsaeng.domain.challenge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import web.tosunsaeng.global.config.SecurityConfig;
import web.tosunsaeng.global.sentry.UnexpectedExceptionReporter;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.verifyNoInteractions;

@ActiveProfiles("test")
@WebMvcTest(controllers = {ChallengeController.class, ChallengeCallbackController.class}, properties = {"app.auth.mode=legacy", "app.challenge.enabled=false"})
@ContextConfiguration(classes = ChallengeLegacySecurityTest.Slice.class)
@Import({SecurityConfig.class, ChallengeSecurityConfiguration.class, ChallengeExceptionAdvice.class})
class ChallengeLegacySecurityTest {
    @Configuration @Import({ChallengeController.class, ChallengeCallbackController.class}) static class Slice {}
    @Autowired MockMvc mvc;
    @MockitoBean ChallengeService service;
    @MockitoBean ChallengeCallbackService callbacks;
    @MockitoBean UnexpectedExceptionReporter reporter;
    @Test void legacyNeverBecomesAnonymousMemberAndDisabledCallbackCannotFallThrough() throws Exception {
        mvc.perform(get("/api/v1/challenges/today")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COMMON403"));
        mvc.perform(post(ChallengeCallbackController.PATH).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get(ChallengeCallbackController.PATH)).andExpect(status().isForbidden());
        verifyNoInteractions(service, callbacks);
    }
}
