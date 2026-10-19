package web.tosunsaeng.domain.notification;

import java.time.Instant;

public interface PushGateway {
    enum Status { ACCEPTED, FAILED, UNKNOWN, SKIPPED }
    record Result(Status status, boolean invalidToken, boolean authFailure, long retryAfterSeconds) {
        public static Result unknown() { return new Result(Status.UNKNOWN, false, false, 0); }
        public static Result skipped() { return new Result(Status.SKIPPED, false, false, 0); }
    }
    final class PreparationFailure extends RuntimeException {
        public final Result result;
        public PreparationFailure(Result result) { super("FCM_PREPARATION_FAILED"); this.result = result; }
    }
    /** OAuth preparation occurs before a durable send attempt is reserved. No push is sent here. */
    void prepare();
    Result send(String token, String notificationId, Instant expiresAt);
}
