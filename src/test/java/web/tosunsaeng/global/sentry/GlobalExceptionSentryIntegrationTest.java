package web.tosunsaeng.global.sentry;

import io.sentry.Hint;
import io.sentry.Hub;
import io.sentry.IHub;
import io.sentry.ScopeCallback;
import io.sentry.SentryEnvelope;
import io.sentry.SentryEnvelopeItem;
import io.sentry.SentryEvent;
import io.sentry.SentryItemType;
import io.sentry.SentryOptions;
import io.sentry.spring.boot.jakarta.SentryProperties;
import io.sentry.spring.jakarta.tracing.SpringMvcTransactionNameProvider;
import io.sentry.transport.ITransport;
import io.sentry.transport.RateLimiter;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import web.tosunsaeng.global.exception.GlobalExceptionAdvice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@WebAppConfiguration
@ContextConfiguration(classes = GlobalExceptionSentryIntegrationTest.MvcTestConfiguration.class)
@ExtendWith({SpringExtension.class, OutputCaptureExtension.class})
class GlobalExceptionSentryIntegrationTest {

    private static final String SENSITIVE_FAILURE_MESSAGE = "SENSITIVE_CONTROLLER_FAILURE";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private IHub sentryHub;

    private MockMvc mockMvc;
    private RecordingTransport transport;

    @BeforeEach
    void setUp() {
        reset(sentryHub);
        mockMvc = webAppContextSetup(applicationContext)
                .addFilters(new UnhandledExceptionCaptureFilter(
                        applicationContext.getBean(UnexpectedExceptionReporter.class)
                ))
                .build();
        transport = new RecordingTransport();
        Hub delegate = new Hub(sentryOptions(transport));
        when(sentryHub.captureException(
                any(Throwable.class),
                any(Hint.class),
                any(ScopeCallback.class)
        )).thenAnswer(invocation -> delegate.captureException(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2)
        ));
        when(sentryHub.captureEvent(
                any(SentryEvent.class),
                any(Hint.class)
        )).thenAnswer(invocation -> delegate.captureEvent(
                invocation.getArgument(0, SentryEvent.class),
                invocation.getArgument(1, Hint.class)
        ));
    }

    @Test
    void handledFiveHundredCreatesOneReporterEventAndOneSafeErrorLog(
            CapturedOutput output) throws Exception {
        mockMvc.perform(get("/_test/sentry/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON500"));

        verify(sentryHub, times(1)).captureException(
                any(Throwable.class),
                any(Hint.class),
                any(ScopeCallback.class)
        );
        verify(sentryHub, never()).captureEvent(any(SentryEvent.class), any(Hint.class));

        List<SentryEnvelope> envelopes = transport.envelopes();
        assertEquals(1, envelopes.size());
        SentryEvent event = eventFrom(envelopes.getFirst());

        assertAll(
                () -> assertEquals(
                        1,
                        countOccurrences(output.getOut(), "event=http.request outcome=failed")
                ),
                () -> assertFalse(output.getOut().contains(SENSITIVE_FAILURE_MESSAGE)),
                () -> assertEquals(
                        "java.lang.IllegalStateException",
                        event.getTag(SentryEventSanitizer.ERROR_TYPE_TAG)
                ),
                () -> assertTrue(hasApplicationFrame(event)),
                () -> assertTrue(
                        event.getExceptions().stream()
                                .allMatch(exception -> exception.getValue() == null)
                ),
                () -> assertEnvelopeContainsOnlyEvent(envelopes.getFirst())
        );
    }

    @Test
    void unhandledControllerExceptionUsesSanitizedResolverExactlyOnce(
            CapturedOutput output) throws Exception {
        assertThrows(
                ServletException.class,
                () -> mockMvc.perform(get("/_test/sentry/unhandled"))
        );

        verify(sentryHub, times(1)).captureException(
                any(Throwable.class),
                any(Hint.class),
                any(ScopeCallback.class)
        );
        verify(sentryHub, never()).captureEvent(any(SentryEvent.class), any(Hint.class));

        List<SentryEnvelope> envelopes = transport.envelopes();
        assertEquals(1, envelopes.size());
        SentryEvent event = eventFrom(envelopes.getFirst());

        assertAll(
                () -> assertTrue(hasApplicationFrame(event)),
                () -> assertTrue(
                        event.getExceptions().stream()
                                .allMatch(exception -> exception.getValue() == null)
                ),
                () -> assertEquals(
                        SentryUnexpectedExceptionReporter.UNHANDLED_CAPTURE_SOURCE,
                        event.getTag(SentryEventSanitizer.CAPTURE_SOURCE_TAG)
                ),
                () -> assertEquals(
                        1,
                        countOccurrences(output.getOut(),
                                "처리되지 않은 HTTP 요청 실패 "
                                        + "event=http.request outcome=failed")
                ),
                () -> assertNull(event.getTransaction()),
                () -> assertEnvelopeContainsOnlyEvent(envelopes.getFirst())
        );
    }

    private static SentryOptions sentryOptions(RecordingTransport transport) {
        SentryOptions options = new SentryOptions();
        options.setDsn("https://public@example.invalid/1");
        options.setEnvironment("test");
        options.setRelease("app-back-end-learning-core@test");
        options.setBeforeSend(new SentryEventSanitizer());
        options.setTransportFactory((ignoredOptions, ignoredRequestDetails) -> transport);
        return options;
    }

    private static SentryEvent eventFrom(SentryEnvelope envelope) throws Exception {
        SentryOptions serializerOptions = new SentryOptions();
        for (SentryEnvelopeItem item : envelope.getItems()) {
            if (item.getHeader().getType() == SentryItemType.Event) {
                SentryEvent event = item.getEvent(serializerOptions.getSerializer());
                assertNotNull(event);
                return event;
            }
        }
        throw new AssertionError("event item not found");
    }

    private static boolean hasApplicationFrame(SentryEvent event) {
        return event.getExceptions().stream()
                .filter(exception -> exception.getStacktrace() != null)
                .flatMap(exception -> exception.getStacktrace().getFrames().stream())
                .anyMatch(frame -> frame.getModule() != null
                        && frame.getModule().startsWith("web.tosunsaeng."));
    }

    private static void assertEnvelopeContainsOnlyEvent(SentryEnvelope envelope) {
        List<SentryItemType> itemTypes = new ArrayList<>();
        envelope.getItems().forEach(item -> itemTypes.add(item.getHeader().getType()));
        assertEquals(List.of(SentryItemType.Event), itemTypes);
    }

    private static int countOccurrences(String text, String target) {
        return (text.length() - text.replace(target, "").length()) / target.length();
    }

    @RestController
    static class FailureController {

        @GetMapping("/_test/sentry/unexpected")
        void fail() {
            throw new IllegalStateException(SENSITIVE_FAILURE_MESSAGE);
        }
    }

    @Controller
    static class UnhandledFailureController {

        @GetMapping("/_test/sentry/unhandled")
        @ResponseBody
        void fail() {
            throw new IllegalStateException(SENSITIVE_FAILURE_MESSAGE);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class MvcTestConfiguration {

        @Bean
        FailureController failureController() {
            return new FailureController();
        }

        @Bean
        UnhandledFailureController unhandledFailureController() {
            return new UnhandledFailureController();
        }

        @Bean
        IHub sentryHub() {
            return mock(IHub.class);
        }

        @Bean
        SentryUnexpectedExceptionReporter unexpectedExceptionReporter(IHub hub) {
            return new SentryUnexpectedExceptionReporter(hub);
        }

        @Bean
        GlobalExceptionAdvice globalExceptionAdvice(
                UnexpectedExceptionReporter unexpectedExceptionReporter) {
            return new GlobalExceptionAdvice(unexpectedExceptionReporter);
        }

        @Bean
        SanitizedSentryExceptionResolver sentryExceptionResolver(
                IHub hub,
                UnexpectedExceptionReporter unexpectedExceptionReporter) {
            SentryProperties properties = new SentryProperties();
            properties.setExceptionResolverOrder(1);
            return new SanitizedSentryExceptionResolver(
                    hub,
                    new SpringMvcTransactionNameProvider(),
                    properties,
                    unexpectedExceptionReporter
            );
        }
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
