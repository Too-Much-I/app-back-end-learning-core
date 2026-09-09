package web.tosunsaeng.domain.exams.billing.reconciliation;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Internal scheduling metadata, not a second Reservation business state. */
@Getter
@Setter
@NoArgsConstructor
public class ReservationRecovery {
    public enum Status { READY, IN_FLIGHT, BLOCKED_AUTH, BLOCKED_OWNER, NEEDS_REVIEW, DONE }
    public enum Intent { CONTINUE, CLEANUP_PRECOMMIT }
    public enum Dispatch { NOT_DISPATCHED, MAY_HAVE_BEEN_SENT, OBSERVED }

    private int schemaVersion = 1;
    private Status status = Status.READY;
    private Intent intent = Intent.CONTINUE;
    private Dispatch dispatch = Dispatch.NOT_DISPATCHED;
    private Instant nextAt;
    private Instant firstAt;
    private int attempts;
    private String leaseToken;
    private String leaseOwner;
    private Instant leaseUntil;
    private Instant lastProgressAt;
    private Instant resolvedAt;
    private String failureCode;
    private String alertIncidentId;
    private String alertReason;
    private String alertStatus;
    private Instant alertNextAt;
    private Instant alertCreatedAt;
    private int alertAttempts;
    private String alertLeaseToken;
    private Instant alertLeaseUntil;
    private EarlyAlert earlyAlert;

    /** Separate journal so a later quarantine alert cannot overwrite the early warning. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class EarlyAlert {
        private String alertIncidentId;
        private String alertReason;
        private String alertStatus;
        private Instant alertNextAt;
        private Instant alertCreatedAt;
        private int alertAttempts;
        private String alertLeaseToken;
        private Instant alertLeaseUntil;
    }

    public static ReservationRecovery prepared(Instant now) {
        ReservationRecovery recovery = new ReservationRecovery();
        recovery.lastProgressAt = now;
        recovery.nextAt = now.plusSeconds(120);
        return recovery;
    }
}
