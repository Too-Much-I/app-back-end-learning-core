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
@org.springframework.boot.autoconfigure.ImportAutoConfiguration({
        org.springdoc.core.configuration.SpringDocConfiguration.class,
        org.springdoc.core.properties.SpringDocConfigProperties.class,
        org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration.class})
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
        when(service.apply(eq(PromotionServiceTest.OWNER),eq("커피"),eq("fixture@example.com"),any()))
                .thenReturn(new PromotionService.Receipt("receipt"));
        mvc.perform(request().header("Authorization","Bearer fixture-token").param("gifticon","커피")
                .param("email","fixture@example.com"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.result.applicationId").value("receipt"))
                .andExpect(jsonPath("$.result.userId").doesNotExist()).andExpect(jsonPath("$.result.email").doesNotExist())
                .andExpect(jsonPath("$.result.gifticon").doesNotExist()).andExpect(jsonPath("$.result.accountNumber").doesNotExist());
        verify(service).apply(eq(PromotionServiceTest.OWNER),eq("커피"),eq("fixture@example.com"),any());
    }
    @Test void missingFieldsSpoofedIdsAndMultipleReportsAreRejected() throws Exception {
        mvc.perform(request().header("Authorization","Bearer fixture-token")).andExpect(status().isBadRequest());
        mvc.perform(request().header("Authorization","Bearer fixture-token").param("gifticon","커피")
                .param("email","fixture@example.com").param("userId","spoof"))
                .andExpect(status().isBadRequest());
        mvc.perform(request().file(new MockMultipartFile("report",new byte[]{1})).header("Authorization","Bearer fixture-token")
                .param("gifticon","커피").param("email","fixture@example.com"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void errorsNeverEchoProviderOrFormData() throws Exception {
        when(service.apply(anyString(),anyString(),anyString(),any())).thenThrow(new RuntimeException("private provider details"));
        mvc.perform(request().header("Authorization","Bearer fixture-token").param("gifticon","커피")
                .param("email","fixture@example.com"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PROMOTION_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))));
    }
    @Test void removedBankFieldsAndDuplicateNewFieldsAreRejected() throws Exception {
        for (String extra:new String[]{"bankName","bankCode","accountNumber","accountHolder","promotionId"})
            mvc.perform(request().header("Authorization","Bearer fixture-token").param("gifticon","커피")
                    .param("email","fixture@example.com").param(extra,"unwanted"))
                    .andExpect(status().isBadRequest());
        for (String field:new String[]{"gifticon","email"}) {
            mvc.perform(request().header("Authorization","Bearer fixture-token").param(field,"only-one-field"))
                    .andExpect(status().isBadRequest());
            mvc.perform(request().header("Authorization","Bearer fixture-token").param("gifticon","커피")
                    .param("email","fixture@example.com").param(field,"duplicate"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(request().header("Authorization","Bearer fixture-token").param("bankName","old")
                .param("accountNumber","1234").param("accountHolder","old"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void heicMultipartIsForwardedWithoutRequiringBankFields() throws Exception {
        when(service.apply(anyString(),anyString(),anyString(),any())).thenReturn(new PromotionService.Receipt("receipt"));
        mvc.perform(multipart("/api/v1/promotions/applications")
                .file(new MockMultipartFile("report","fixture.heic","image/heic",PromotionServiceTest.ftyp("heic")))
                .header("Authorization","Bearer fixture-token").param("gifticon","커피").param("email","fixture@example.com"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.applicationId").value("receipt"));
    }
    @Test void openApiDocumentsAllFieldsInMultipartBodyAndNoQueryParameters() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/promotions/applications'].post.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/promotions/applications'].post.requestBody.required").value(true))
                .andExpect(jsonPath("$.paths['/api/v1/promotions/applications'].post.requestBody.content['multipart/form-data'].schema['$ref']")
                        .value("#/components/schemas/PromotionApplicationForm"))
                .andExpect(jsonPath("$.components.schemas.PromotionApplicationForm.required",
                        org.hamcrest.Matchers.containsInAnyOrder("gifticon","email","report")))
                .andExpect(jsonPath("$.components.schemas.PromotionApplicationForm.properties",org.hamcrest.Matchers.aMapWithSize(3)))
                .andExpect(jsonPath("$.components.schemas.PromotionApplicationForm.properties.gifticon.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.PromotionApplicationForm.properties.email.format").value("email"))
                .andExpect(jsonPath("$.components.schemas.PromotionApplicationForm.properties.report.format").value("binary"));
    }
}
