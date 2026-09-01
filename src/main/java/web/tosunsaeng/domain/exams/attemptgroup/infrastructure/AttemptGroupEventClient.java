package web.tosunsaeng.domain.exams.attemptgroup.infrastructure;

public interface AttemptGroupEventClient {
    Response send(String canonicalPayload, String traceparent);

    record Response(int statusCode, Integer retryAfterSeconds) {
    }

    class TransportException extends RuntimeException {
        public TransportException(Throwable cause) {
            super(cause);
        }
    }
}
