package web.tosunsaeng.global.sentry;

import io.sentry.Attachment;
import io.sentry.Baggage;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.Hub;
import io.sentry.PropagationContext;
import io.sentry.SentryEnvelope;
import io.sentry.SentryEnvelopeItem;
import io.sentry.SentryEvent;
import io.sentry.SentryItemType;
import io.sentry.SentryOptions;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.User;
import io.sentry.transport.ITransport;
import io.sentry.transport.RateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import web.tosunsaeng.global.logging.RequestCorrelationFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SentryPipelineIntegrationTest {

    private static final String FIRST_REQUEST_ID = "123e4567-e89b-42d3-a456-426614174000";
    private static final String SECOND_REQUEST_ID = "223e4567-e89b-42d3-b456-426614174001";
    private static final String SENSITIVE_MARKER = "SENSITIVE_PIPELINE_MARKER";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void capturesEachExceptionOnceWithoutSensitiveDataOrScopeLeakage() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        SentryOptions options = sentryOptions(transport);
        Hub hub = new Hub(options);
        configureUnsafeParentScope(hub);
        hub.startSession();
        transport.clear();
        SentryUnexpectedExceptionReporter reporter = new SentryUnexpectedExceptionReporter(hub);

        MDC.put(RequestCorrelationFilter.REQUEST_ID_MDC_KEY, FIRST_REQUEST_ID);
        reporter.report(new IllegalStateException(SENSITIVE_MARKER + "_FIRST"));
        MDC.put(RequestCorrelationFilter.REQUEST_ID_MDC_KEY, SECOND_REQUEST_ID);
        reporter.report(new IllegalArgumentException(SENSITIVE_MARKER + "_SECOND"));

        List<SentryEnvelope> envelopes = transport.envelopes();
        assertEquals(2, envelopes.size());

        SentryEvent firstEvent = eventFrom(envelopes.get(0), options);
        SentryEvent secondEvent = eventFrom(envelopes.get(1), options);
        String firstJson = serialize(firstEvent, options);
        String secondJson = serialize(secondEvent, options);
        String firstEnvelope = serializeEnvelope(envelopes.get(0), options);
        String secondEnvelope = serializeEnvelope(envelopes.get(1), options);

        assertAll(
                () -> assertEnvelopeContainsOnlyEvent(envelopes.get(0)),
                () -> assertEnvelopeContainsOnlyEvent(envelopes.get(1)),
                () -> assertEquals(
                        Map.of("value", FIRST_REQUEST_ID),
                        firstEvent.getContexts().get(SentryEventSanitizer.CORRELATION_CONTEXT_KEY)
                ),
                () -> assertEquals(
                        Map.of("value", SECOND_REQUEST_ID),
                        secondEvent.getContexts().get(SentryEventSanitizer.CORRELATION_CONTEXT_KEY)
                ),
                () -> assertFalse(firstJson.contains(SECOND_REQUEST_ID)),
                () -> assertFalse(secondJson.contains(FIRST_REQUEST_ID)),
                () -> assertFalse(firstJson.contains(SENSITIVE_MARKER)),
                () -> assertFalse(secondJson.contains(SENSITIVE_MARKER)),
                () -> assertFalse(firstEnvelope.contains(SENSITIVE_MARKER)),
                () -> assertFalse(secondEnvelope.contains(SENSITIVE_MARKER)),
                () -> assertFalse(firstJson.contains("unsafe-parent")),
                () -> assertFalse(secondJson.contains("unsafe-parent")),
                () -> assertEquals(
                        "java.lang.IllegalStateException",
                        firstEvent.getTag(SentryEventSanitizer.ERROR_TYPE_TAG)
                ),
                () -> assertEquals(
                        "java.lang.IllegalArgumentException",
                        secondEvent.getTag(SentryEventSanitizer.ERROR_TYPE_TAG)
                ),
                () -> assertEquals(
                        SentryUnexpectedExceptionReporter.CAPTURE_SOURCE,
                        firstEvent.getTag(SentryEventSanitizer.CAPTURE_SOURCE_TAG)
                ),
                () -> assertTrue(hasApplicationFrame(firstEvent)),
                () -> assertTrue(hasApplicationFrame(secondEvent)),
                () -> assertTrue(allExceptionValuesRemoved(firstEvent)),
                () -> assertTrue(allExceptionValuesRemoved(secondEvent)),
                () -> assertEquals("test", firstEvent.getEnvironment()),
                () -> assertEquals("app-back-end-learning-core@test", firstEvent.getRelease()),
                () -> assertNull(envelopes.get(0).getHeader().getTraceContext().getUserId()),
                () -> assertNull(envelopes.get(1).getHeader().getTraceContext().getUserId())
        );
    }

    @Test
    void dropsEventWhenBeforeSendSanitizerFails() {
        RecordingTransport transport = new RecordingTransport();
        SentryOptions options = sentryOptions(transport);
        options.setBeforeSend(new SentryEventSanitizer() {
            @Override
            SentryEvent sanitize(SentryEvent event) {
                throw new IllegalStateException("SENSITIVE_SANITIZER_FAILURE");
            }
        });
        Hub hub = new Hub(options);

        hub.captureException(new IllegalStateException(SENSITIVE_MARKER));

        assertTrue(transport.envelopes().isEmpty());
    }

    private static SentryOptions sentryOptions(RecordingTransport transport) {
        SentryOptions options = new SentryOptions();
        options.setDsn("https://public@example.invalid/1");
        options.setEnvironment("test");
        options.setRelease("app-back-end-learning-core@test");
        options.setSendDefaultPii(false);
        options.setTracesSampleRate(0.0);
        options.setServerName(SENSITIVE_MARKER);
        options.setBeforeSend(new SentryEventSanitizer());
        options.setTransportFactory((ignoredOptions, ignoredRequestDetails) -> transport);
        return options;
    }

    private static void configureUnsafeParentScope(Hub hub) {
        hub.configureScope(scope -> {
            scope.setTag("unsafe-parent", SENSITIVE_MARKER);
            scope.setExtra("unsafe-parent", SENSITIVE_MARKER);
            scope.setContexts("unsafe-parent", SENSITIVE_MARKER);
            scope.addBreadcrumb(new Breadcrumb(SENSITIVE_MARKER));
            scope.addAttachment(new Attachment(
                    SENSITIVE_MARKER.getBytes(StandardCharsets.UTF_8),
                    "sensitive.txt"
            ));

            User user = new User();
            user.setId(SENSITIVE_MARKER);
            user.setIpAddress(SENSITIVE_MARKER);
            scope.setUser(user);

            Request request = new Request();
            request.setUrl("https://example.invalid/" + SENSITIVE_MARKER);
            scope.setRequest(request);

            Baggage baggage = new Baggage(hub.getOptions().getLogger());
            PropagationContext propagationContext = new PropagationContext();
            baggage.setTraceId(propagationContext.getTraceId().toString());
            baggage.setPublicKey("public");
            baggage.setUserId(SENSITIVE_MARKER);
            propagationContext.setBaggage(baggage);
            scope.setPropagationContext(propagationContext);
        });
    }

    private static SentryEvent eventFrom(
            SentryEnvelope envelope,
            SentryOptions options) throws Exception {
        for (SentryEnvelopeItem item : envelope.getItems()) {
            if (item.getHeader().getType() == SentryItemType.Event) {
                SentryEvent event = item.getEvent(options.getSerializer());
                assertNotNull(event);
                return event;
            }
        }
        throw new AssertionError("event item not found");
    }

    private static void assertEnvelopeContainsOnlyEvent(SentryEnvelope envelope) {
        List<SentryItemType> itemTypes = StreamSupport.stream(
                        envelope.getItems().spliterator(),
                        false
                )
                .map(item -> item.getHeader().getType())
                .toList();
        assertEquals(List.of(SentryItemType.Event), itemTypes);
    }

    private static boolean hasApplicationFrame(SentryEvent event) {
        List<SentryException> exceptions = event.getExceptions();
        if (exceptions == null) {
            return false;
        }
        return exceptions.stream()
                .filter(exception -> exception.getStacktrace() != null)
                .flatMap(exception -> exception.getStacktrace().getFrames().stream())
                .anyMatch(frame -> frame.getModule() != null
                        && frame.getModule().startsWith("web.tosunsaeng."));
    }

    private static boolean allExceptionValuesRemoved(SentryEvent event) {
        List<SentryException> exceptions = event.getExceptions();
        return exceptions != null
                && !exceptions.isEmpty()
                && exceptions.stream().allMatch(exception -> exception.getValue() == null);
    }

    private static String serialize(SentryEvent event, SentryOptions options) throws Exception {
        StringWriter writer = new StringWriter();
        options.getSerializer().serialize(event, writer);
        return writer.toString();
    }

    private static String serializeEnvelope(
            SentryEnvelope envelope,
            SentryOptions options) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        options.getSerializer().serialize(envelope, output);
        return output.toString(StandardCharsets.UTF_8);
    }

    private static final class RecordingTransport implements ITransport {

        private final List<SentryEnvelope> envelopes = new ArrayList<>();

        @Override
        public synchronized void send(SentryEnvelope envelope, Hint hint) {
            envelopes.add(envelope);
        }

        synchronized List<SentryEnvelope> envelopes() {
            return List.copyOf(envelopes);
        }

        synchronized void clear() {
            envelopes.clear();
        }

        @Override
        public void flush(long timeoutMillis) {
        }

        @Override
        public RateLimiter getRateLimiter() {
            return null;
        }

        @Override
        public void close(boolean isRestarting) {
        }

        @Override
        public void close() throws IOException {
        }
    }
}
