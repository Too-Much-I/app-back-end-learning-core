package web.tosunsaeng.domain.promotion;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import web.tosunsaeng.global.auth.JwtCurrentUserProvider;
import web.tosunsaeng.global.config.SecurityConfig;
import web.tosunsaeng.global.config.security.SecurityErrorResponseHandler;
import web.tosunsaeng.global.sentry.UnexpectedExceptionReporter;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@WebMvcTest(controllers=PromotionController.class,properties={"app.auth.mode=jwt","app.auth.identity.issuer=https://identity.example.test",
        "app.auth.identity.jwk-set-uri=https://identity.example.test/jwks","app.auth.identity.audience=test","app.promotion.enabled=true"})
@Import({SecurityConfig.class,SecurityErrorResponseHandler.class,PromotionExceptionAdvice.class,JwtCurrentUserProvider.class})
@ContextConfiguration(classes=PromotionControllerTest.Slice.class)
class PromotionControllerTest {
    @Configuration @Import(PromotionController.class) static class Slice {}
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean PromotionService service;
    @MockitoBean UnexpectedExceptionReporter reporter;
    @BeforeEach void setup() {
        when(decoder.decode("fixture-token")).thenReturn(Jwt.withTokenValue("fixture-token").header("alg","RS256").subject(PromotionServiceTest.OWNER).build());
    }
    org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder request() {
        return multipart("/api/v1/promotions/applications")
                .file(new MockMultipartFile("report","fixture.pdf","application/pdf","%PDF-fixture".getBytes()));
    }
    @Test void authIsRequiredAndReceiptDoesNotExposePrivateFields() throws Exception {
        mvc.perform(request()).andExpect(status().isUnauthorized());
        when(service.apply(eq(PromotionServiceTest.OWNER),eq("국민은행"),eq("001234"),eq("Fixture"),any()))
                .thenReturn(new PromotionService.Receipt("receipt"));
        mvc.perform(request().header("Authorization","Bearer fixture-token").param("bankName","국민은행")
                .param("accountNumber","001234").param("accountHolder","Fixture"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.result.applicationId").value("receipt"))
                .andExpect(jsonPath("$.result.userId").doesNotExist()).andExpect(jsonPath("$.result.accountNumber").doesNotExist());
        verify(service).apply(eq(PromotionServiceTest.OWNER),eq("국민은행"),eq("001234"),eq("Fixture"),any());
    }
    @Test void missingFieldsSpoofedIdsAndMultipleReportsAreRejected() throws Exception {
        mvc.perform(request().header("Authorization","Bearer fixture-token")).andExpect(status().isBadRequest());
        mvc.perform(request().header("Authorization","Bearer fixture-token").param("bankName","국민은행")
                .param("accountNumber","001234").param("accountHolder","Fixture").param("userId","spoof"))
                .andExpect(status().isBadRequest());
        mvc.perform(request().file(new MockMultipartFile("report",new byte[]{1})).header("Authorization","Bearer fixture-token")
                .param("bankName","국민은행").param("accountNumber","001234").param("accountHolder","Fixture"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void errorsNeverEchoProviderOrFormData() throws Exception {
        when(service.apply(anyString(),anyString(),anyString(),anyString(),any())).thenThrow(new RuntimeException("private provider details"));
        mvc.perform(request().header("Authorization","Bearer fixture-token").param("bankName","국민은행")
                .param("accountNumber","001234").param("accountHolder","Fixture"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PROMOTION_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))));
    }
}
