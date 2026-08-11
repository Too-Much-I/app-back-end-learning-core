package web.tosunsaeng.global.sentry;

import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.PropagationContext;
import io.sentry.protocol.SentryId;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import web.tosunsaeng.global.logging.RequestCorrelationFilter;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

@Component
public class SentryUnexpectedExceptionReporter implements UnexpectedExceptionReporter {

    static final String CAPTURE_SOURCE = "global_exception_advice";
    static final String UNHANDLED_CAPTURE_SOURCE = "unhandled_http_exception";
    static final String SERVICE_NAME = "learning-core";

    private final IHub hub;

    public SentryUnexpectedExceptionReporter(IHub hub) {
        this.hub = hub;
    }

    @Override
    public void report(Throwable exception) {
        report(exception, CAPTURE_SOURCE);
    }

    @Override
    public void reportUnhandled(Throwable exception) {
        report(exception, UNHANDLED_CAPTURE_SOURCE);
    }

    private void report(Throwable exception, String captureSource) {
        Objects.requireNonNull(exception, "exception must not be null");
        String requestId = SentryEventSanitizer.normalizeRequestId(
                MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)
        );
        String errorType = exception.getClass().getName();
        String rootCauseType = rootCauseType(exception);

        hub.captureException(exception, new Hint(), scope -> {
            scope.clear();
            scope.getContexts().clear();
            scope.clearSession();
            scope.setPropagationContext(new PropagationContext());
            scope.setReplayId(SentryId.EMPTY_ID);
            scope.setTag(SentryEventSanitizer.ERROR_TYPE_TAG, errorType);
            scope.setTag(SentryEventSanitizer.ROOT_CAUSE_TYPE_TAG, rootCauseType);
            scope.setTag(SentryEventSanitizer.HTTP_STATUS_CODE_TAG, "500");
            scope.setTag(SentryEventSanitizer.CAPTURE_SOURCE_TAG, captureSource);
            scope.setTag(SentryEventSanitizer.SERVICE_TAG, SERVICE_NAME);
            if (requestId != null) {
                scope.setContexts(SentryEventSanitizer.CORRELATION_CONTEXT_KEY, requestId);
            }
        });
    }

    private static String rootCauseType(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable rootCause = failure;
        visited.add(rootCause);

        Throwable cause = rootCause.getCause();
        while (cause != null && visited.add(cause)) {
            rootCause = cause;
            cause = rootCause.getCause();
        }
        return rootCause.getClass().getName();
    }
}
