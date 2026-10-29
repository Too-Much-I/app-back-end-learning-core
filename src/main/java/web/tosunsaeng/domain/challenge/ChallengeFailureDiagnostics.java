package web.tosunsaeng.domain.challenge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import web.tosunsaeng.global.exception.MongoFailureDiagnostics;

/** Never pass a Throwable to the logger: messages may contain credentials or BSON documents. */
final class ChallengeFailureDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(ChallengeFailureDiagnostics.class);
    enum Stage { START_RECOVERY, HTTP_FAILURE }

    private ChallengeFailureDiagnostics() {}

    static void record(Throwable failure, Stage stage) {
        StringBuilder causes = new StringBuilder();
        StringBuilder frames = new StringBuilder();
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
            if (!causes.isEmpty()) causes.append(',');
            causes.append(current.getClass().getName());
            int count = 0;
            for (StackTraceElement frame : current.getStackTrace()) {
                if (frame.getClassName().startsWith("web.tosunsaeng.") && count++ < 3) {
                    if (!frames.isEmpty()) frames.append(',');
                    frames.append(frame.getClassName()).append('#').append(frame.getMethodName())
                            .append(':').append(frame.getLineNumber());
                }
            }
        }
        log.error("event=challenge.failure stage={} errorTypes={} appFrames={}", stage, causes, frames);
        MongoFailureDiagnostics.record(failure, MongoFailureDiagnostics.Boundary.CHALLENGE, 0,
                MongoFailureDiagnostics.Outcome.PROPAGATED);
    }
}
