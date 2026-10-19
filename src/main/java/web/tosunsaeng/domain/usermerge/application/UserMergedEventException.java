package web.tosunsaeng.domain.usermerge.application;

import lombok.Getter;

@Getter
public class UserMergedEventException extends RuntimeException {

    private final Reason reason;

    public UserMergedEventException(Reason reason) {
        this(reason, null);
    }

    public UserMergedEventException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public enum Reason {
        INVALID_PAYLOAD,
        PAYLOAD_CONFLICT,
        LIFECYCLE_CONFLICT,
        RETRYABLE_PRECONDITION,
        PROCESSING_UNAVAILABLE
    }
}
