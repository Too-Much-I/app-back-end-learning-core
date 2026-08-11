package web.tosunsaeng.global.sentry;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class UnhandledExceptionCaptureFilter extends OncePerRequestFilter {

    static final String CAPTURED_ATTRIBUTE =
            UnhandledExceptionCaptureFilter.class.getName() + ".captured";

    private final UnexpectedExceptionReporter unexpectedExceptionReporter;

    public UnhandledExceptionCaptureFilter(
            UnexpectedExceptionReporter unexpectedExceptionReporter) {
        this.unexpectedExceptionReporter = unexpectedExceptionReporter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | RuntimeException failure) {
            reportOnce(request, failure);
            log.error(
                    "처리되지 않은 HTTP 요청 실패 "
                            + "event=http.request outcome=failed status=500 "
                            + "method={} path={} errorType={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    failure.getClass().getName()
            );
            throw failure;
        }
    }

    private void reportOnce(HttpServletRequest request, Exception failure) {
        if (Boolean.TRUE.equals(request.getAttribute(CAPTURED_ATTRIBUTE))) {
            return;
        }

        request.setAttribute(CAPTURED_ATTRIBUTE, Boolean.TRUE);
        try {
            unexpectedExceptionReporter.reportUnhandled(failure);
        } catch (RuntimeException reportingFailure) {
            log.warn(
                    "처리되지 않은 예외의 Sentry 보고 실패 "
                            + "event=sentry.exception.report outcome=failed source={} errorType={}",
                    SentryUnexpectedExceptionReporter.UNHANDLED_CAPTURE_SOURCE,
                    reportingFailure.getClass().getName()
            );
        }
    }
}
