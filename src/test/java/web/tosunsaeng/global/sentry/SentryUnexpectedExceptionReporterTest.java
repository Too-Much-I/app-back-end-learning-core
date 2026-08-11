package web.tosunsaeng.global.sentry;

import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.IScope;
import io.sentry.PropagationContext;
import io.sentry.ScopeCallback;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.SentryId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import web.tosunsaeng.global.logging.RequestCorrelationFilter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SentryUnexpectedExceptionReporterTest {

    private static final String REQUEST_ID = "123e4567-e89b-42d3-a456-426614174000";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void capturesExceptionWithIsolatedSafeMetadata() {
        IHub hub = mock(IHub.class);
        IScope scope = mock(IScope.class);
        when(scope.getContexts()).thenReturn(new Contexts());
        SentryUnexpectedExceptionReporter reporter = new SentryUnexpectedExceptionReporter(hub);
        IllegalArgumentException rootCause = new IllegalArgumentException("sensitive root cause");
        IllegalStateException failure = new IllegalStateException("sensitive failure", rootCause);
        MDC.put(RequestCorrelationFilter.REQUEST_ID_MDC_KEY, REQUEST_ID);

        reporter.report(failure);

        ArgumentCaptor<ScopeCallback> callbackCaptor = ArgumentCaptor.forClass(ScopeCallback.class);
        verify(hub).captureException(same(failure), any(Hint.class), callbackCaptor.capture());
        callbackCaptor.getValue().run(scope);

        verify(scope).clear();
        verify(scope).clearSession();
        verify(scope).setPropagationContext(any(PropagationContext.class));
        verify(scope).setReplayId(SentryId.EMPTY_ID);
        verify(scope).setTag(
                SentryEventSanitizer.ERROR_TYPE_TAG,
                IllegalStateException.class.getName()
        );
        verify(scope).setTag(
                SentryEventSanitizer.ROOT_CAUSE_TYPE_TAG,
                IllegalArgumentException.class.getName()
        );
        verify(scope).setTag(SentryEventSanitizer.HTTP_STATUS_CODE_TAG, "500");
        verify(scope).setTag(
                SentryEventSanitizer.CAPTURE_SOURCE_TAG,
                SentryUnexpectedExceptionReporter.CAPTURE_SOURCE
        );
        verify(scope).setTag(
                SentryEventSanitizer.SERVICE_TAG,
                SentryUnexpectedExceptionReporter.SERVICE_NAME
        );
        verify(scope).setContexts(SentryEventSanitizer.CORRELATION_CONTEXT_KEY, REQUEST_ID);
    }

    @Test
    void doesNotAttachInvalidRequestId() {
        IHub hub = mock(IHub.class);
        IScope scope = mock(IScope.class);
        when(scope.getContexts()).thenReturn(new Contexts());
        SentryUnexpectedExceptionReporter reporter = new SentryUnexpectedExceptionReporter(hub);
        IllegalStateException failure = new IllegalStateException("sensitive failure");
        MDC.put(RequestCorrelationFilter.REQUEST_ID_MDC_KEY, "SENSITIVE_INVALID_REQUEST_ID");

        reporter.report(failure);

        ArgumentCaptor<ScopeCallback> callbackCaptor = ArgumentCaptor.forClass(ScopeCallback.class);
        verify(hub).captureException(same(failure), any(Hint.class), callbackCaptor.capture());
        callbackCaptor.getValue().run(scope);

        verify(scope, never()).setContexts(
                same(SentryEventSanitizer.CORRELATION_CONTEXT_KEY),
                any(String.class)
        );
    }

    @Test
    void marksUnhandledCaptureWithResolverSource() {
        IHub hub = mock(IHub.class);
        IScope scope = mock(IScope.class);
        when(scope.getContexts()).thenReturn(new Contexts());
        SentryUnexpectedExceptionReporter reporter = new SentryUnexpectedExceptionReporter(hub);
        IllegalStateException failure = new IllegalStateException("sensitive failure");

        reporter.reportUnhandled(failure);

        ArgumentCaptor<ScopeCallback> callbackCaptor = ArgumentCaptor.forClass(ScopeCallback.class);
        verify(hub).captureException(same(failure), any(Hint.class), callbackCaptor.capture());
        callbackCaptor.getValue().run(scope);

        verify(scope).setTag(
                SentryEventSanitizer.CAPTURE_SOURCE_TAG,
                SentryUnexpectedExceptionReporter.UNHANDLED_CAPTURE_SOURCE
        );
    }
}
