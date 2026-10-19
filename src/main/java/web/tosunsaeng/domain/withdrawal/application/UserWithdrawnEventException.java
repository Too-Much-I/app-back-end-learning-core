package web.tosunsaeng.domain.withdrawal.application;

public class UserWithdrawnEventException extends RuntimeException {

    public enum Reason {
        INVALID_PAYLOAD,
        PAYLOAD_CONFLICT,
        PROCESSING_UNAVAILABLE
    }

    private final Reason reason;

    public UserWithdrawnEventException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public UserWithdrawnEventException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
