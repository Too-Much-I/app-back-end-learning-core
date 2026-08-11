package web.tosunsaeng.global.sentry;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(OutputCaptureExtension.class)
class UnhandledExceptionCaptureFilterTest {

    @Test
    void capturesRuntimeExceptionOnceAndRethrowsOriginal(CapturedOutput output) {
        UnexpectedExceptionReporter reporter = mock(UnexpectedExceptionReporter.class);
        UnhandledExceptionCaptureFilter filter = new UnhandledExceptionCaptureFilter(reporter);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/v1/exams/ex_failure/status"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        IllegalStateException failure = new IllegalStateException("SENSITIVE_FILTER_FAILURE");

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
                    throw failure;
                })
        );

        assertAll(
                () -> assertSame(failure, thrown),
                () -> verify(reporter).reportUnhandled(same(failure)),
                () -> assertEquals(
                        1,
                        countOccurrences(output.getOut(), "event=http.request outcome=failed")
                ),
                () -> assertTrue(output.getOut().contains(
                        "처리되지 않은 HTTP 요청 실패 "
                                + "event=http.request outcome=failed status=500 "
                                + "method=GET path=/api/v1/exams/ex_failure/status "
                                + "errorType=java.lang.IllegalStateException"
                )),
                () -> assertFalse(output.getOut().contains("SENSITIVE_FILTER_FAILURE"))
        );
    }

    @Test
    void doesNotCaptureAgainWhenResolverAlreadyCaptured() {
        UnexpectedExceptionReporter reporter = mock(UnexpectedExceptionReporter.class);
        UnhandledExceptionCaptureFilter filter = new UnhandledExceptionCaptureFilter(reporter);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/_test/unhandled");
        request.setAttribute(UnhandledExceptionCaptureFilter.CAPTURED_ATTRIBUTE, Boolean.TRUE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServletException failure = new ServletException("sensitive failure");

        ServletException thrown = assertThrows(
                ServletException.class,
                () -> filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
                    throw failure;
                })
        );

        assertSame(failure, thrown);
        verifyNoInteractions(reporter);
    }

    private static int countOccurrences(String text, String target) {
        return (text.length() - text.replace(target, "").length()) / target.length();
    }
}
