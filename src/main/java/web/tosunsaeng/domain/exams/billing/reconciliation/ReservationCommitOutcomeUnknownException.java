package web.tosunsaeng.domain.exams.billing.reconciliation;

/** A commit acknowledgement is missing, not proof that the write failed. Never log its raw cause. */
public class ReservationCommitOutcomeUnknownException extends RuntimeException {
    public ReservationCommitOutcomeUnknownException(Throwable cause) {
        super("Reservation commit outcome requires fresh evidence", cause);
    }
}
