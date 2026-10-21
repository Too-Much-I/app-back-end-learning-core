package web.tosunsaeng.global.exception;

import com.mongodb.MongoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Allowlisted metadata only: never log exception messages, commands, labels or documents. */
public final class MongoFailureDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(MongoFailureDiagnostics.class);

    public enum Boundary { HTTP, USER_OWNED, NOTIFICATION }
    public enum Outcome { PROPAGATED, RETRYING, EXHAUSTED, NON_RETRYABLE, UNKNOWN_COMMIT }

    private MongoFailureDiagnostics() {}

    public static void record(Throwable failure, Boundary boundary, int attempt, Outcome outcome) {
        Integer code = null;
        boolean transientTransaction = false;
        boolean unknownCommit = false;
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 20; depth++) {
            if (current instanceof MongoException mongo) {
                if (code == null) code = mongo.getCode();
                transientTransaction |= mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
                unknownCommit |= mongo.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
            }
            if (current == current.getCause()) break;
            current = current.getCause();
        }
        if (code == null) return;
        log.warn("event=mongo.failure boundary={} outcome={} attempt={} mongoCode={} transientTransaction={} unknownCommit={}",
                boundary, outcome, attempt, code, transientTransaction, unknownCommit);
    }
}
