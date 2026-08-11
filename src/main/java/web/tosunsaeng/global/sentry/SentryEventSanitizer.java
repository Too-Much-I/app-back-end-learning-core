package web.tosunsaeng.global.sentry;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SentryEventSanitizer implements SentryOptions.BeforeSendCallback {

    public static final String CORRELATION_CONTEXT_KEY = "correlation.request_id";

    static final String ERROR_TYPE_TAG = "error.type";
    static final String ROOT_CAUSE_TYPE_TAG = "error.root_cause_type";
    static final String HTTP_METHOD_TAG = "http.method";
    static final String HTTP_STATUS_CODE_TAG = "http.status_code";
    static final String CAPTURE_SOURCE_TAG = "capture.source";
    static final String SERVICE_TAG = "service";

    private static final Pattern JAVA_TYPE_PATTERN = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*"
    );
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-"
                    + "[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"
    );
    private static final Set<String> HTTP_METHODS = Set.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"
    );
    private static final Set<String> CAPTURE_SOURCES = Set.of(
            SentryUnexpectedExceptionReporter.CAPTURE_SOURCE,
            SentryUnexpectedExceptionReporter.UNHANDLED_CAPTURE_SOURCE
    );

    @Override
    public SentryEvent execute(SentryEvent event, Hint hint) {
        try {
            return sanitize(event);
        } catch (RuntimeException sanitizationFailure) {
            log.warn(
                    "Sentry 이벤트 정제 실패 event=sentry.event.sanitize outcome=dropped "
                            + "errorType={}",
                    sanitizationFailure.getClass().getName()
            );
            return null;
        }
    }

    SentryEvent sanitize(SentryEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }

        Map<String, String> safeTags = sanitizeTags(event.getTags());
        String requestId = extractRequestId(event.getContexts().get(CORRELATION_CONTEXT_KEY));

        event.setMessage(null);
        event.setLogger(null);
        event.setThreads(null);
        event.setTransaction(null);
        event.setFingerprints(null);
        event.setRequest(null);
        event.setUser(null);
        event.setServerName(null);
        event.setDist(null);
        event.setPlatform("java");
        event.setBreadcrumbs(null);
        event.setDebugMeta(null);
        event.setExtras(null);
        event.setModules(null);
        event.setUnknown(null);

        event.setTags(null);
        safeTags.forEach(event::setTag);

        event.getContexts().clear();
        if (requestId != null) {
            event.getContexts().put(
                    CORRELATION_CONTEXT_KEY,
                    Map.of("value", requestId)
            );
        }

        sanitizeExceptions(event.getExceptions());
        event.setThrowable(null);
        return event;
    }

    static String normalizeRequestId(String requestId) {
        if (requestId == null || !UUID_PATTERN.matcher(requestId).matches()) {
            return null;
        }
        try {
            return UUID.fromString(requestId).toString().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException invalidUuid) {
            return null;
        }
    }

    private static Map<String, String> sanitizeTags(Map<String, String> tags) {
        Map<String, String> safeTags = new HashMap<>();
        if (tags == null) {
            return safeTags;
        }

        putIfValidType(safeTags, ERROR_TYPE_TAG, tags.get(ERROR_TYPE_TAG));
        putIfValidType(safeTags, ROOT_CAUSE_TYPE_TAG, tags.get(ROOT_CAUSE_TYPE_TAG));
        putIfAllowed(safeTags, HTTP_METHOD_TAG, tags.get(HTTP_METHOD_TAG), HTTP_METHODS);

        String statusCode = tags.get(HTTP_STATUS_CODE_TAG);
        if (statusCode != null && statusCode.matches("[1-5][0-9]{2}")) {
            safeTags.put(HTTP_STATUS_CODE_TAG, statusCode);
        }

        putIfAllowed(
                safeTags,
                CAPTURE_SOURCE_TAG,
                tags.get(CAPTURE_SOURCE_TAG),
                CAPTURE_SOURCES
        );

        String service = tags.get(SERVICE_TAG);
        if (SentryUnexpectedExceptionReporter.SERVICE_NAME.equals(service)) {
            safeTags.put(SERVICE_TAG, service);
        }
        return safeTags;
    }

    private static void putIfValidType(
            Map<String, String> safeTags,
            String key,
            String value) {
        if (value != null
                && value.length() <= 255
                && JAVA_TYPE_PATTERN.matcher(value).matches()) {
            safeTags.put(key, value);
        }
    }

    private static void putIfAllowed(
            Map<String, String> safeTags,
            String key,
            String value,
            Set<String> allowedValues) {
        if (value != null && allowedValues.contains(value)) {
            safeTags.put(key, value);
        }
    }

    private static String extractRequestId(Object correlationContext) {
        if (correlationContext instanceof String requestId) {
            return normalizeRequestId(requestId);
        }
        if (correlationContext instanceof Map<?, ?> correlationValues) {
            Object requestId = correlationValues.get("value");
            if (requestId instanceof String requestIdValue) {
                return normalizeRequestId(requestIdValue);
            }
        }
        return null;
    }

    private static void sanitizeExceptions(List<SentryException> exceptions) {
        if (exceptions == null) {
            return;
        }
        for (SentryException exception : exceptions) {
            if (exception == null) {
                continue;
            }
            exception.setValue(null);
            exception.setUnknown(null);
            sanitizeMechanism(exception.getMechanism());
            sanitizeStackTrace(exception.getStacktrace());
        }
    }

    private static void sanitizeMechanism(Mechanism mechanism) {
        if (mechanism == null) {
            return;
        }
        mechanism.setDescription(null);
        mechanism.setHelpLink(null);
        mechanism.setMeta(null);
        mechanism.setData(null);
        mechanism.setUnknown(null);
    }

    private static void sanitizeStackTrace(SentryStackTrace stackTrace) {
        if (stackTrace == null) {
            return;
        }
        stackTrace.setRegisters(null);
        stackTrace.setUnknown(null);
        List<SentryStackFrame> frames = stackTrace.getFrames();
        if (frames == null) {
            return;
        }
        for (SentryStackFrame frame : frames) {
            sanitizeStackFrame(frame);
        }
    }

    private static void sanitizeStackFrame(SentryStackFrame frame) {
        if (frame == null) {
            return;
        }
        frame.setPreContext(null);
        frame.setPostContext(null);
        frame.setVars(null);
        frame.setAbsPath(null);
        frame.setContextLine(null);
        frame.setPackage(null);
        frame.setPlatform(null);
        frame.setImageAddr(null);
        frame.setSymbolAddr(null);
        frame.setInstructionAddr(null);
        frame.setRawFunction(null);
        frame.setSymbol(null);
        frame.setLock(null);
        frame.setUnknown(null);
    }
}
