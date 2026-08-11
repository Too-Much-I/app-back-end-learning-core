package web.tosunsaeng.global.sentry;

import io.sentry.IHub;
import io.sentry.spring.boot.jakarta.SentryProperties;
import io.sentry.spring.jakarta.tracing.TransactionNameProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

@ExtendWith(OutputCaptureExtension.class)
class SanitizedSentryExceptionResolverTest {

    @Test
    void reportingFailureDoesNotReplaceUnhandledExceptionFlow(CapturedOutput output) {
        UnexpectedExceptionReporter reporter = mock(UnexpectedExceptionReporter.class);
        SentryProperties properties = new SentryProperties();
        properties.setExceptionResolverOrder(1);
        SanitizedSentryExceptionResolver resolver = new SanitizedSentryExceptionResolver(
                mock(IHub.class),
                mock(TransactionNameProvider.class),
                properties,
                reporter
        );
        IllegalStateException failure = new IllegalStateException("original sensitive failure");
        doThrow(new IllegalStateException("SENSITIVE_REPORTER_FAILURE"))
                .when(reporter)
                .reportUnhandled(failure);

        Object result = resolver.resolveException(
                mock(HttpServletRequest.class),
                mock(HttpServletResponse.class),
                null,
                failure
        );

        assertAll(
                () -> assertNull(result),
                () -> assertEquals(1, resolver.getOrder()),
                () -> verify(reporter).reportUnhandled(same(failure)),
                () -> assertTrue(output.getOut().contains(
                        "처리되지 않은 예외의 Sentry 보고 실패 "
                                + "event=sentry.exception.report outcome=failed "
                                + "source=unhandled_http_exception "
                                + "errorType=java.lang.IllegalStateException"
                )),
                () -> assertFalse(output.getOut().contains("SENSITIVE_REPORTER_FAILURE")),
                () -> assertFalse(output.getOut().contains("original sensitive failure"))
        );
    }
}
