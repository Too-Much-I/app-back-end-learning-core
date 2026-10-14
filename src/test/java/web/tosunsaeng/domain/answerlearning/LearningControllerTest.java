package web.tosunsaeng.domain.answerlearning;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import web.tosunsaeng.global.config.SecurityConfig;
import web.tosunsaeng.global.config.security.SecurityErrorResponseHandler;
import web.tosunsaeng.global.auth.JwtCurrentUserProvider;
import web.tosunsaeng.global.sentry.UnexpectedExceptionReporter;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static web.tosunsaeng.domain.answerlearning.LearningFixtures.*;

@ActiveProfiles("test")
@WebMvcTest(controllers=LearningController.class,properties={"app.auth.mode=jwt","app.auth.identity.issuer=https://identity.example.test",
        "app.auth.identity.jwk-set-uri=https://identity.example.test/jwks","app.auth.identity.audience=test","app.answer-learning.enabled=true"})
@Import({SecurityConfig.class,SecurityErrorResponseHandler.class,LearningExceptionAdvice.class,JwtCurrentUserProvider.class})
@ContextConfiguration(classes=LearningControllerTest.Slice.class)
class LearningControllerTest {
    @Configuration @Import(LearningController.class) static class Slice {}
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean LearningCatalog catalog;
    @MockitoBean LearningService service;
    @MockitoBean UnexpectedExceptionReporter reporter;
    @BeforeEach void setup() {
        when(decoder.decode("fixture-token")).thenReturn(Jwt.withTokenValue("fixture-token").header("alg","RS256").subject(OWNER).build());
        when(decoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid"));
    }
    @Test void jwtIsRequiredAndSubjectIsTheOnlyOwnerSource() throws Exception {
        mvc.perform(get("/api/v1/learning/units").param("part","2").param("level","IH")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/learning/units").header("Authorization","Bearer invalid-token").param("part","2").param("level","IH")).andExpect(status().isUnauthorized());
        when(catalog.list(OWNER,2,"IH")).thenReturn(new Document("part",2));
        mvc.perform(get("/api/v1/learning/units").header("Authorization","Bearer fixture-token").param("part","2").param("level","IH"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isSuccess").value(true)).andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.result.part").value(2)).andExpect(jsonPath("$.result.userId").doesNotExist());
        verify(catalog).list(OWNER,2,"IH");
    }
    @Test void postedUserIdCannotOverrideAuthenticatedOwnerAndWireShapeStaysStable() throws Exception {
        when(service.create(eq(OWNER),any())).thenReturn(submission().view());
        mvc.perform(post("/api/v1/learning/submissions").header("Authorization","Bearer fixture-token").contentType("application/json")
                .content("{\"submissionId\":\""+ID+"\",\"lessonId\":\"lesson\",\"level\":\"IH\",\"contentVersion\":\"test-v1\",\"userId\":\"spoof\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("awaiting_audio"))
                .andExpect(jsonPath("$.result.id").value(ID)).andExpect(jsonPath("$.result.userId").doesNotExist())
                .andExpect(jsonPath("$.result.audio").doesNotExist()).andExpect(jsonPath("$.result.checklist").doesNotExist());
        verify(service).create(eq(OWNER),any());
    }
    @Test void foreignSubmissionReturns404WithoutLeakingErrors() throws Exception {
        when(service.get(OWNER,ID)).thenThrow(LearningFailure.missing());
        mvc.perform(get("/api/v1/learning/submissions/"+ID).header("Authorization","Bearer fixture-token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LEARNING_NOT_FOUND"));
    }
    @Test void invalidUploadsAndNonemptyConfirmAreRejected() throws Exception {
        mvc.perform(post("/api/v1/learning/submissions/"+ID+"/upload-url").header("Authorization","Bearer fixture-token")
                .contentType("application/json").content("{\"contentType\":\"audio/mp4\",\"sizeBytes\":8388609}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/learning/submissions/"+ID+"/submit").header("Authorization","Bearer fixture-token")
                .contentType("application/json").content("{\"checklist\":{}}"))
                .andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void operationalExceptionsDoNotLeakProviderBodies() throws Exception {
        when(service.get(OWNER,ID)).thenThrow(new RuntimeException("private transcript and secret"));
        mvc.perform(get("/api/v1/learning/submissions/"+ID).header("Authorization","Bearer fixture-token"))
                .andExpect(status().isServiceUnavailable()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))));
    }
}
