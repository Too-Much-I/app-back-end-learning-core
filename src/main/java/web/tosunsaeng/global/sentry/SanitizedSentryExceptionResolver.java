package web.tosunsaeng.global.sentry;

import io.sentry.IHub;
import io.sentry.spring.boot.jakarta.SentryProperties;
import io.sentry.spring.jakarta.SentryExceptionResolver;
import io.sentry.spring.jakarta.tracing.TransactionNameProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

@Slf4j
@Component
public class SanitizedSentryExceptionResolver extends SentryExceptionResolver {

    private final UnexpectedExceptionReporter unexpectedExceptionReporter;

    public SanitizedSentryExceptionResolver(
            IHub hub,
            TransactionNameProvider transactionNameProvider,
            SentryProperties sentryProperties,
            UnexpectedExceptionReporter unexpectedExceptionReporter) {
        super(hub, transactionNameProvider, sentryProperties.getExceptionResolverOrder());
        this.unexpectedExceptionReporter = unexpectedExceptionReporter;
    }

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception) {
        request.setAttribute(UnhandledExceptionCaptureFilter.CAPTURED_ATTRIBUTE, Boolean.TRUE);
        try {
            unexpectedExceptionReporter.reportUnhandled(exception);
        } catch (RuntimeException reportingFailure) {
            log.warn(
                    "처리되지 않은 예외의 Sentry 보고 실패 "
                            + "event=sentry.exception.report outcome=failed source={} errorType={}",
                    SentryUnexpectedExceptionReporter.UNHANDLED_CAPTURE_SOURCE,
                    reportingFailure.getClass().getName()
            );
        }
        return null;
    }
}
