package web.tosunsaeng.global.config.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class SecurityErrorResponseHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SecurityErrorResponseHandler handler =
            new SecurityErrorResponseHandler(objectMapper);

    @Test
    void unauthorizedLogOmitsAuthorizationHeaderAndExceptionMessage(CapturedOutput output)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/exams");
        request.addHeader("Authorization", "Bearer should-not-be-logged");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(
                request,
                response,
                new BadCredentialsException("token=should-not-be-logged")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertAll(
                () -> assertEquals(401, response.getStatus()),
                () -> assertEquals("COMMON401", body.path("code").asText()),
                () -> assertTrue(output.getOut().contains(
                        "인증되지 않은 요청 거절 "
                                + "event=http.security outcome=rejected reason=unauthorized "
                                + "method=POST path=/api/v1/exams errorCode=COMMON401"
                )),
                () -> assertFalse(output.getOut().contains("Authorization")),
                () -> assertFalse(output.getOut().contains("should-not-be-logged"))
        );
    }

    @Test
    void forbiddenLogKeepsBaseResponseAndSafeClassification(CapturedOutput output)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/v1/exams/ex_forbidden/status"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(
                request,
                response,
                new AccessDeniedException("user identifier should-not-be-logged")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertAll(
                () -> assertEquals(403, response.getStatus()),
                () -> assertEquals("COMMON403", body.path("code").asText()),
                () -> assertTrue(output.getOut().contains(
                        "접근 권한이 없는 요청 거절 "
                                + "event=http.security outcome=rejected reason=forbidden "
                                + "method=GET path=/api/v1/exams/ex_forbidden/status "
                                + "errorCode=COMMON403"
                )),
                () -> assertFalse(output.getOut().contains("should-not-be-logged"))
        );
    }
}
