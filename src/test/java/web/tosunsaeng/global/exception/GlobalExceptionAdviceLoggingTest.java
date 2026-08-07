package web.tosunsaeng.global.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionAdviceLoggingTest {

    private final GlobalExceptionAdvice advice = new GlobalExceptionAdvice();

    @Test
    void businessExceptionLogsCodeWithoutInternalUserIdentifiers(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/v1/exams/ex_forbidden/status"
        );

        ResponseEntity<?> response = advice.onThrowException(
                new ExamsException(ErrorStatus._FORBIDDEN),
                request
        );

        assertAll(
                () -> assertEquals(403, response.getStatusCode().value()),
                () -> assertTrue(output.getOut().contains(
                        "event=http.business outcome=rejected status=403 errorCode=COMMON403 "
                                + "method=GET path=/api/v1/exams/ex_forbidden/status"
                )),
                () -> assertFalse(output.getOut().contains(
                        "00000000-0000-0000-0000-000000000001"
                ))
        );
    }

    @Test
    void unexpectedExceptionLogOmitsMessageAndSignedUrl(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/v1/exams/ex_failure/questions/1/submit"
        );
        IllegalStateException failure = new IllegalStateException(
                "https://example.com/audio?X-Amz-Signature=should-not-be-logged"
        );

        ResponseEntity<?> response = advice.exception(failure, new ServletWebRequest(request));

        assertAll(
                () -> assertEquals(500, response.getStatusCode().value()),
                () -> assertTrue(output.getOut().contains(
                        "event=http.request outcome=failed status=500 errorCode=COMMON500 "
                                + "method=POST path=/api/v1/exams/ex_failure/questions/1/submit "
                                + "errorType=java.lang.IllegalStateException"
                )),
                () -> assertFalse(output.getOut().contains("https://example.com")),
                () -> assertFalse(output.getOut().contains("X-Amz-Signature")),
                () -> assertFalse(output.getOut().contains("should-not-be-logged"))
        );
    }
}
