package web.tosunsaeng.domain.answerlearning;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mongodb.MongoClientSettings;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandSucceededEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LearningTimingTest {
    @AfterEach void cleanup() { LearningRequestTiming.clear(); }

    @Test void sumsSuccessfulAndFailedCommandsWithoutReadingPayload() {
        var timing = LearningRequestTiming.begin();
        var listener = new LearningTimingMongoConfiguration.TimingListener();
        var success = mock(CommandSucceededEvent.class);
        var failure = mock(CommandFailedEvent.class);
        when(success.getElapsedTime(TimeUnit.NANOSECONDS)).thenReturn(2_000_000L);
        when(failure.getElapsedTime(TimeUnit.NANOSECONDS)).thenReturn(3_000_000L);
        listener.commandSucceeded(success);
        listener.commandFailed(failure);
        assertThat(timing.mongoCommandMs()).isEqualTo(5);
        assertThat(timing.mongoCommands()).isEqualTo(2);
        verify(success).getElapsedTime(TimeUnit.NANOSECONDS);
        verify(failure).getElapsedTime(TimeUnit.NANOSECONDS);
        verifyNoMoreInteractions(success, failure);
    }

    @Test void preservesBillingSuccessAndExceptionAndClearsRequestScope() {
        var timing = LearningRequestTiming.begin();
        assertThat(LearningRequestTiming.billing(() -> "ok")).isEqualTo("ok");
        var failure = new IllegalStateException("private exception");
        assertThatThrownBy(() -> LearningRequestTiming.billing(() -> { throw failure; })).isSameAs(failure);
        assertThat(timing.billingCalls()).isEqualTo(2);
        assertThat(timing.billingMs()).isGreaterThanOrEqualTo(0);
        LearningRequestTiming.clear();
        LearningRequestTiming.billing(() -> "outside");
        LearningRequestTiming.mongo(9_000_000L);
        assertThat(timing.billingCalls()).isEqualTo(2);
        assertThat(timing.mongoCommands()).isZero();
    }

    @Test void doesNotMixConcurrentThreads() throws Exception {
        var parent = LearningRequestTiming.begin();
        Thread child = new Thread(() -> {
            LearningRequestTiming.mongo(3_000_000L);
            LearningRequestTiming.billing(() -> "outside");
        });
        child.start(); child.join();
        assertThat(parent.mongoCommands()).isZero();
        assertThat(parent.billingCalls()).isZero();
    }

    @Test void customizerAddsListenerWithoutReplacingExistingListeners() {
        var existing = new LearningTimingMongoConfiguration.TimingListener();
        var builder = MongoClientSettings.builder().addCommandListener(existing);
        new LearningTimingMongoConfiguration().learningTimingMongoCustomizer().customize(builder);
        assertThat(builder.build().getCommandListeners()).hasSize(2).contains(existing);
    }

    @Test void logsOneSanitizedRecordAndPreservesResponse() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(LearningTimingFilter.class);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            var request = new MockHttpServletRequest("GET", "/api/v1/learning/units/private-id");
            request.setQueryString("token=secret-query");
            request.addHeader("Authorization", "Bearer secret-header");
            var response = new MockHttpServletResponse();
            new LearningTimingFilter().doFilter(request, response, (req, res) -> {
                LearningRequestTiming.mongo(7_000_000L);
                LearningRequestTiming.billing(() -> "ok");
                res.getWriter().write("original-body");
            });
            assertThat(response.getContentAsString()).isEqualTo("original-body");
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage())
                    .contains("route=unit_detail", "status=200", "mongoCommandMs=7", "mongoCommands=1", "billingCalls=1")
                    .doesNotContain("private-id", "secret-query", "secret-header", "original-body");
            assertThat(appender.list.getFirst().getThrowableProxy()).isNull();
        } finally { logger.detachAppender(appender); }
    }

    @Test void exceptionIsRethrownAndThreadLocalDoesNotLeak() throws Exception {
        var filter = new LearningTimingFilter();
        var failure = new IllegalStateException("private");
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/learning/resume"),
                new MockHttpServletResponse(), (req, res) -> { throw failure; })).isSameAs(failure);
        var next = LearningRequestTiming.begin();
        assertThat(next.mongoCommands()).isZero();
        assertThat(next.billingCalls()).isZero();
    }

    @Test void unrelatedRoutesAreExcludedAndUnknownRoutesAreConstant() throws Exception {
        var filter = new LearningTimingFilter();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/learning-extra"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/exams"))).isTrue();
        assertThat(LearningTimingFilter.route(new MockHttpServletRequest("GET", "/api/v1/learning/secret/unknown"))).isEqualTo("other");
    }
}
