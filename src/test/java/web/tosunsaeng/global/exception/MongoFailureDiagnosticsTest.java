package web.tosunsaeng.global.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mongodb.MongoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class MongoFailureDiagnosticsTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(MongoFailureDiagnostics.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach void attach() { appender.start(); logger.addAppender(appender); }
    @AfterEach void detach() { logger.detachAppender(appender); appender.stop(); }

    @Test void extractsWrappedCodeAndKnownLabelsWithoutSensitiveContent() {
        var mongo = new MongoException(112, "secret-uri audio transcript document");
        mongo.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        mongo.addLabel("private-custom-label");
        MongoFailureDiagnostics.record(new DataIntegrityViolationException("private-user", mongo),
                MongoFailureDiagnostics.Boundary.USER_OWNED, 3, MongoFailureDiagnostics.Outcome.EXHAUSTED);
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getFormattedMessage()).isEqualTo(
                "event=mongo.failure boundary=USER_OWNED outcome=EXHAUSTED attempt=3 mongoCode=112 transientTransaction=true unknownCommit=false");
        assertThat(appender.list.getFirst().getThrowableProxy()).isNull();
    }

    @Test void recordsUnknownCommitIndependentlyOfTransientLabel() {
        var mongo = new MongoException(91, "private");
        mongo.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        MongoFailureDiagnostics.record(mongo, MongoFailureDiagnostics.Boundary.NOTIFICATION, 1,
                MongoFailureDiagnostics.Outcome.UNKNOWN_COMMIT);
        assertThat(appender.list.getFirst().getFormattedMessage())
                .contains("outcome=UNKNOWN_COMMIT", "attempt=1", "unknownCommit=true");
    }

    @Test void ignoresNonMongoAndBoundsCyclicCauseTraversal() {
        MongoFailureDiagnostics.record(new RuntimeException("private"), MongoFailureDiagnostics.Boundary.HTTP,
                0, MongoFailureDiagnostics.Outcome.PROPAGATED);
        var a = new RuntimeException(); var b = new RuntimeException(a); a.initCause(b);
        MongoFailureDiagnostics.record(a, MongoFailureDiagnostics.Boundary.HTTP, 0,
                MongoFailureDiagnostics.Outcome.PROPAGATED);
        assertThat(appender.list).isEmpty();
    }
}
