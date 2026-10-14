package web.tosunsaeng.domain.exams.domain.entity;

import web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationSnapshot;
import web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationStatus;
import web.tosunsaeng.domain.exams.billing.PaidReservationProtocol;
import java.util.Objects;

/** Embedded recovery evidence; no independent collection or TTL. */
public record PaidReservationEvidence(ReservationSnapshot snapshot) {
    public PaidReservationEvidence { PaidReservationProtocol.snapshot(snapshot); }

    public PaidReservationEvidence observe(ReservationSnapshot next) {
        PaidReservationProtocol.snapshot(next);
        var old = snapshot;
        PaidReservationProtocol.require(Objects.equals(old.operationId(), next.operationId())
                && Objects.equals(old.reservationId(), next.reservationId())
                && old.reservationKind() == next.reservationKind()
                && Objects.equals(old.attemptGroupId(), next.attemptGroupId())
                && Objects.equals(old.sessionId(), next.sessionId()) && Objects.equals(old.mockExamId(), next.mockExamId())
                && old.entitlementSource() == next.entitlementSource()
                && Objects.equals(old.transition(), next.transition())
                && Objects.equals(old.continuationReason(), next.continuationReason())
                && Objects.equals(old.continuationId(), next.continuationId())
                && Objects.equals(old.expiresAt(), next.expiresAt()));
        if (old.reservationStatus() != ReservationStatus.RESERVED) {
            PaidReservationProtocol.require(old.reservationStatus() == next.reservationStatus()
                    && Objects.equals(old.terminalAt(), next.terminalAt())
                    && Objects.equals(old.confirmRejection(), next.confirmRejection()));
        }
        return new PaidReservationEvidence(next);
    }
}
