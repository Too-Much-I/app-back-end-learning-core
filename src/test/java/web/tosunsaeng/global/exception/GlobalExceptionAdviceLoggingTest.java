package web.tosunsaeng.global.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;
import web.tosunsaeng.global.sentry.UnexpectedExceptionReporter;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionAdviceLoggingTest {

    private UnexpectedExceptionReporter reporter;
    private GlobalExceptionAdvice advice;

    @BeforeEach
    void setUp() {
        reporter = mock(UnexpectedExceptionReporter.class);
        advice = new GlobalExceptionAdvice(reporter);
    }

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
                        "비즈니스 요청 거절 "
                                + "event=http.business outcome=rejected status=403 errorCode=COMMON403 "
                                + "method=GET path=/api/v1/exams/ex_forbidden/status"
                )),
                () -> assertFalse(output.getOut().contains(
                        "00000000-0000-0000-0000-000000000001"
                )),
                () -> verifyNoInteractions(reporter)
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
                        "HTTP 요청 처리 실패 "
                                + "event=http.request outcome=failed status=500 errorCode=COMMON500 "
                                + "method=POST path=/api/v1/exams/ex_failure/questions/1/submit "
                                + "errorType=java.lang.IllegalStateException"
                )),
                () -> assertFalse(output.getOut().contains("https://example.com")),
                () -> assertFalse(output.getOut().contains("X-Amz-Signature")),
                () -> assertFalse(output.getOut().contains("should-not-be-logged")),
                () -> assertEquals(
                        1,
                        countOccurrences(output.getOut(), "event=http.request outcome=failed")
                ),
                () -> verify(reporter).report(same(failure))
        );
    }

    @Test
    void malformedJsonDoesNotReportToSentry(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/v1/exams"
        );
        HttpMessageNotReadableException failure = mock(HttpMessageNotReadableException.class);

        ResponseEntity<?> response = advice.exception(
                failure,
                new ServletWebRequest(request)
        );

        assertAll(
                () -> assertEquals(400, response.getStatusCode().value()),
                () -> assertTrue(output.getOut().contains(
                        "event=http.request.parse outcome=rejected status=400"
                )),
                () -> verifyNoInteractions(reporter)
        );
    }

    @Test
    void reporterFailureDoesNotChangeResponseOrLeakFailureMessage(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/v1/exams/ex_failure/status"
        );
        IllegalStateException failure = new IllegalStateException("original failure");
        doThrow(new IllegalStateException("SENSITIVE_REPORTER_FAILURE"))
                .when(reporter)
                .report(failure);

        ResponseEntity<?> response = advice.exception(
                failure,
                new ServletWebRequest(request)
        );

        assertAll(
                () -> assertEquals(500, response.getStatusCode().value()),
                () -> assertTrue(output.getOut().contains(
                        "event=sentry.exception.report outcome=failed "
                                + "errorType=java.lang.IllegalStateException"
                )),
                () -> assertEquals(
                        1,
                        countOccurrences(output.getOut(), "event=http.request outcome=failed")
                ),
                () -> assertFalse(output.getOut().contains("SENSITIVE_REPORTER_FAILURE"))
        );
    }

    private static int countOccurrences(String text, String target) {
        return (text.length() - text.replace(target, "").length()) / target.length();
    }
}
