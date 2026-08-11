package web.tosunsaeng.global.sentry;

import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.JsonSerializer;
import io.sentry.SentryEvent;
import io.sentry.SentryLockReason;
import io.sentry.SentryOptions;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.protocol.SentryThread;
import io.sentry.protocol.User;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SentryEventSanitizerTest {

    private static final String REQUEST_ID = "123e4567-e89b-42d3-a456-426614174000";
    private static final String SENSITIVE_MARKER_A = "SENSITIVE_MARKER_A";
    private static final String SENSITIVE_MARKER_B = "SENSITIVE_MARKER_B";
    private static final String SENSITIVE_MARKER_C = "SENSITIVE_MARKER_C";
    private static final String SENSITIVE_MARKER_D = "SENSITIVE_MARKER_D";

    private final SentryEventSanitizer sanitizer = new SentryEventSanitizer();

    @Test
    void removesSensitiveFieldsAndKeepsOnlyDiagnosticStackData() throws Exception {
        SentryEvent event = sensitiveEvent();

        SentryEvent sanitized = sanitizer.execute(event, new Hint());
        String serialized = serialize(sanitized);
        SentryException exception = sanitized.getExceptions().getFirst();
        SentryStackFrame frame = exception.getStacktrace().getFrames().getFirst();

        assertAll(
                () -> assertSame(event, sanitized),
                () -> assertNull(sanitized.getMessage()),
                () -> assertNull(sanitized.getRequest()),
                () -> assertNull(sanitized.getUser()),
                () -> assertNull(sanitized.getBreadcrumbs()),
                () -> assertNull(sanitized.getThrowable()),
                () -> assertTrue(sanitized.getThreads().isEmpty()),
                () -> assertEquals("IllegalStateException", exception.getType()),
                () -> assertNull(exception.getValue()),
                () -> assertEquals("sanitizeTarget", frame.getFunction()),
                () -> assertEquals("SentryEventSanitizerTest.java", frame.getFilename()),
                () -> assertEquals(123, frame.getLineno()),
                () -> assertNull(frame.getVars()),
                () -> assertNull(frame.getAbsPath()),
                () -> assertEquals(
                        Map.of("value", REQUEST_ID),
                        sanitized.getContexts().get(SentryEventSanitizer.CORRELATION_CONTEXT_KEY)
                ),
                () -> assertEquals("test", sanitized.getEnvironment()),
                () -> assertEquals("app-back-end-learning-core@test", sanitized.getRelease()),
                () -> assertEquals("java", sanitized.getPlatform()),
                () -> assertEquals(
                        "java.lang.IllegalStateException",
                        sanitized.getTag(SentryEventSanitizer.ERROR_TYPE_TAG)
                ),
                () -> assertFalse(serialized.contains("SENSITIVE_MARKER")),
                () -> assertFalse(serialized.contains("https://example.invalid")),
                () -> assertFalse(serialized.contains("Authorization")),
                () -> assertTrue(serialized.contains("SentryEventSanitizerTest.java")),
                () -> assertTrue(serialized.contains("app-back-end-learning-core@test"))
        );
    }

    @Test
    void removesInvalidCorrelationIdAndAllowedTagWithUnsafeValue() {
        SentryEvent event = new SentryEvent();
        event.getContexts().put(
                SentryEventSanitizer.CORRELATION_CONTEXT_KEY,
                Map.of("value", "SENSITIVE_INVALID_REQUEST_ID")
        );
        event.setTag(SentryEventSanitizer.ERROR_TYPE_TAG, "invalid type SENSITIVE_MARKER_A");
        event.setTag(SentryEventSanitizer.CAPTURE_SOURCE_TAG, "SENSITIVE_MARKER_B");

        SentryEvent sanitized = sanitizer.execute(event, new Hint());

        assertAll(
                () -> assertTrue(sanitized.getContexts().isEmpty()),
                () -> assertNull(sanitized.getTag(SentryEventSanitizer.ERROR_TYPE_TAG)),
                () -> assertNull(sanitized.getTag(SentryEventSanitizer.CAPTURE_SOURCE_TAG))
        );
    }

    @Test
    void dropsEventWhenSanitizingFails() {
        assertNull(sanitizer.execute(null, new Hint()));
    }

    private static SentryEvent sensitiveEvent() {
        SentryEvent event = new SentryEvent(new IllegalStateException(SENSITIVE_MARKER_A));
        event.setEnvironment("test");
        event.setRelease("app-back-end-learning-core@test");
        event.setLogger(SENSITIVE_MARKER_A);
        event.setTransaction("/api/v1/exams/" + SENSITIVE_MARKER_B);
        event.setFingerprints(List.of(SENSITIVE_MARKER_C));
        event.setDist(SENSITIVE_MARKER_D);
        event.setPlatform(SENSITIVE_MARKER_D);
        event.setModule("unsafe-module", SENSITIVE_MARKER_A);
        event.setUnknown(Map.of("event_unknown", SENSITIVE_MARKER_D));

        Message message = new Message();
        message.setFormatted(SENSITIVE_MARKER_A);
        message.setMessage(SENSITIVE_MARKER_B);
        message.setParams(List.of(SENSITIVE_MARKER_C));
        event.setMessage(message);

        Request request = new Request();
        request.setUrl("https://example.invalid/" + SENSITIVE_MARKER_A);
        request.setQueryString("signature=" + SENSITIVE_MARKER_B);
        request.setHeaders(Map.of("Authorization", SENSITIVE_MARKER_C));
        request.setData(Map.of("payload", SENSITIVE_MARKER_D));
        event.setRequest(request);

        User user = new User();
        user.setId(SENSITIVE_MARKER_A);
        user.setEmail(SENSITIVE_MARKER_B);
        event.setUser(user);

        Breadcrumb breadcrumb = new Breadcrumb(SENSITIVE_MARKER_C);
        breadcrumb.setData("payload", SENSITIVE_MARKER_D);
        event.setBreadcrumbs(List.of(breadcrumb));
        event.setExtra("payload", SENSITIVE_MARKER_A);

        event.setTag(SentryEventSanitizer.ERROR_TYPE_TAG, "java.lang.IllegalStateException");
        event.setTag(SentryEventSanitizer.ROOT_CAUSE_TYPE_TAG, "java.lang.IllegalArgumentException");
        event.setTag(SentryEventSanitizer.HTTP_METHOD_TAG, "POST");
        event.setTag(SentryEventSanitizer.HTTP_STATUS_CODE_TAG, "500");
        event.setTag(
                SentryEventSanitizer.CAPTURE_SOURCE_TAG,
                SentryUnexpectedExceptionReporter.CAPTURE_SOURCE
        );
        event.setTag(SentryEventSanitizer.SERVICE_TAG, SentryUnexpectedExceptionReporter.SERVICE_NAME);
        event.setTag("unsafe.tag", SENSITIVE_MARKER_B);

        event.getContexts().put(
                SentryEventSanitizer.CORRELATION_CONTEXT_KEY,
                Map.of("value", REQUEST_ID)
        );
        event.getContexts().put("unsafe", Map.of("value", SENSITIVE_MARKER_C));
        event.setExceptions(List.of(sensitiveException()));

        SentryThread thread = new SentryThread();
        thread.setName(SENSITIVE_MARKER_D);
        event.setThreads(List.of(thread));
        return event;
    }

    private static SentryException sensitiveException() {
        SentryStackFrame frame = new SentryStackFrame();
        frame.setModule("web.tosunsaeng.global.sentry.SentryEventSanitizerTest");
        frame.setFunction("sanitizeTarget");
        frame.setFilename("SentryEventSanitizerTest.java");
        frame.setLineno(123);
        frame.setVars(Map.of("password", SENSITIVE_MARKER_A));
        frame.setAbsPath("/private/" + SENSITIVE_MARKER_B);
        frame.setPreContext(List.of(SENSITIVE_MARKER_C));
        frame.setContextLine(SENSITIVE_MARKER_D);
        frame.setPostContext(List.of(SENSITIVE_MARKER_A));
        frame.setPackage(SENSITIVE_MARKER_B);
        frame.setPlatform(SENSITIVE_MARKER_D);
        frame.setRawFunction(SENSITIVE_MARKER_C);
        frame.setUnknown(Map.of("frame_unknown", SENSITIVE_MARKER_D));

        SentryLockReason lock = new SentryLockReason();
        lock.setAddress(SENSITIVE_MARKER_A);
        frame.setLock(lock);

        SentryStackTrace stackTrace = new SentryStackTrace(List.of(frame));
        stackTrace.setRegisters(Map.of("register", SENSITIVE_MARKER_B));
        stackTrace.setUnknown(Map.of("stack_unknown", SENSITIVE_MARKER_C));

        Mechanism mechanism = new Mechanism();
        mechanism.setType("generic");
        mechanism.setHandled(true);
        mechanism.setDescription(SENSITIVE_MARKER_A);
        mechanism.setHelpLink(SENSITIVE_MARKER_B);
        mechanism.setMeta(Map.of("meta", SENSITIVE_MARKER_C));
        mechanism.setData(Map.of("data", SENSITIVE_MARKER_D));
        mechanism.setUnknown(Map.of("mechanism_unknown", SENSITIVE_MARKER_A));

        SentryException exception = new SentryException();
        exception.setType("IllegalStateException");
        exception.setModule("java.lang");
        exception.setValue(SENSITIVE_MARKER_D);
        exception.setStacktrace(stackTrace);
        exception.setMechanism(mechanism);
        exception.setUnknown(Map.of("exception_unknown", SENSITIVE_MARKER_A));
        return exception;
    }

    private static String serialize(SentryEvent event) throws Exception {
        StringWriter writer = new StringWriter();
        new JsonSerializer(new SentryOptions()).serialize(event, writer);
        return writer.toString();
    }
}
