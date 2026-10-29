package web.tosunsaeng.domain.challenge;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mongodb.MongoException;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import web.tosunsaeng.global.exception.MongoFailureDiagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChallengeFailureDiagnosticsTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(ChallengeFailureDiagnostics.class);
    private final Logger mongoLogger = (Logger) LoggerFactory.getLogger(MongoFailureDiagnostics.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    @BeforeEach void attach() { appender.start(); logger.addAppender(appender); mongoLogger.addAppender(appender); }
    @AfterEach void detach() { logger.detachAppender(appender); mongoLogger.detachAppender(appender); appender.stop(); MDC.clear(); }

    @Test void recordsOnlySafeMetadataAndKeepsRequestCorrelation() {
        MDC.put("requestId", "fixture-request-id");
        var cause = new MongoException(112, "private-uri token transcript document");
        cause.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        cause.addLabel("private-label");
        ChallengeFailureDiagnostics.record(new IllegalStateException("private-user", cause),
                ChallengeFailureDiagnostics.Stage.START_RECOVERY);
        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.getFirst().getFormattedMessage())
                .contains("stage=START_RECOVERY", "java.lang.IllegalStateException", "com.mongodb.MongoException", "appFrames=");
        assertThat(appender.list.getLast().getFormattedMessage()).contains("mongoCode=112", "transientTransaction=true");
        for (var event : appender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain("private-", "transcript", "document");
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "fixture-request-id");
        }
    }

    @Test void boundedCauseTraversalHandlesCycles() {
        var a = new RuntimeException("private-a"); var b = new RuntimeException("private-b", a); a.initCause(b);
        ChallengeFailureDiagnostics.record(a, ChallengeFailureDiagnostics.Stage.HTTP_FAILURE);
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getFormattedMessage().split("java.lang.RuntimeException", -1)).hasSize(9);
    }

    @Test void adviceLogsInternalErrorsButNotExpectedClientFailures() {
        var advice = new ChallengeExceptionAdvice();
        var request = new MockHttpServletRequest("POST", "/api/v1/challenges/today/questions/2/attempt");
        var response = advice.error(new IllegalStateException("private-body"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getFormattedMessage()).contains("stage=HTTP_FAILURE").doesNotContain("private-body");
        appender.list.clear();
        assertThat(advice.error(ChallengeFailure.badRequest(), request).getStatusCode().value()).isEqualTo(400);
        assertThat(appender.list).isEmpty();
        assertThat(advice.error(ChallengeFailure.internal(), request).getStatusCode().value()).isEqualTo(500);
        assertThat(appender.list).hasSize(1);
    }

    @Test void startLogsOriginalFailureBeforePublicConversionAndKeepsWinnerRecovery() {
        var store = mock(ChallengeStore.class);
        var tx = mock(ChallengeTransactions.class);
        var catalog = mock(ChallengeCatalog.class);
        var clock = java.time.Clock.fixed(ChallengeContractTest.NOW, java.time.ZoneOffset.UTC);
        var service = new ChallengeService(store, tx, catalog, mock(ChallengeAudioStorage.class), clock);
        when(tx.run(eq(ChallengeContractTest.OWNER), any())).thenThrow(new IllegalStateException("private-db-document"));
        assertThatThrownBy(() -> service.start(ChallengeContractTest.OWNER, 1, "2026-09-07"))
                .isInstanceOfSatisfying(ChallengeFailure.class, failure -> {
                    assertThat(failure.status).isEqualTo(500);
                    assertThat(failure.code).isEqualTo("COMMON500");
                    assertThat(failure.getCause()).isNull();
                });
        assertThat(appender.list.getFirst().getFormattedMessage())
                .contains("START_RECOVERY", "IllegalStateException").doesNotContain("private-db-document");
        when(store.question(ChallengeContractTest.OWNER, "2026-09-07", 1))
                .thenReturn(null, ChallengeContractTest.attempt());
        assertThat(service.start(ChallengeContractTest.OWNER, 1, "2026-09-07"))
                .isEqualTo(ChallengeViews.start(ChallengeContractTest.attempt()));
    }
}
