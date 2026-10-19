package web.tosunsaeng.domain.exams.billing.reconciliation;

/** Fixed reasons only: never carry a Billing body or an identifier in an exception. */
public class ReservationRecoveryException extends RuntimeException {
    public enum Reason { LEASE_LOST, STATE_INCONSISTENT, OWNER_BLOCKED, AUTH_BLOCKED, BUDGET_EXHAUSTED }
    private final Reason reason;

    public ReservationRecoveryException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
