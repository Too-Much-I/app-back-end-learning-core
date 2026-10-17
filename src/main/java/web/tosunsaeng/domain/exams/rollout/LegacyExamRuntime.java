package web.tosunsaeng.domain.exams.rollout;

import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.billing.PaidReservationProtocol;
import web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationStatus;
import web.tosunsaeng.domain.exams.domain.enums.ExamEntitlementState;
import java.util.Objects;

/** Immutable, independently pinned server inventory. Membership is not permission to use a Session. */
public final class LegacyExamRuntime {
    private final LegacyExamManifest manifest;

    public LegacyExamRuntime(LegacyExamManifest manifest) {
        this.manifest = java.util.Objects.requireNonNull(manifest);
    }

    public LegacyExamManifest.Summary requireLegacy(ExamSession session) {
        if (!manifest.containsLegacy(session)) throw LegacyExamManifest.invalid();
        return manifest.summary();
    }

    public void requireBound(ExamCreationOperation operation, ExamSession session) {
        if (!requireLegacy(session).equals(operation.getPaidLegacySource())) throw LegacyExamManifest.invalid();
    }

    /** Classifies stored evidence only; owner, deletion and admission checks remain with the caller.
     * Does not load the creation operation, which may legitimately expire after terminal retention.
     */
    public void requireKnown(ExamSession session) {
        if (session != null && session.isTransitionFree()) return;
        if (manifest.containsLegacy(session)) {
            if (session.getEntitlementState() != null && session.getEntitlementState() != ExamEntitlementState.LEGACY
                    || session.getBillingReservationKind() != null || session.getEntitlementConfirmedAt() != null
                    || session.getEntitlementConfirmedUserId() != null
                    || session.getLegacyClosureManifest() != null && !manifest.summary().equals(session.getLegacyClosureManifest()))
                throw LegacyExamManifest.invalid();
            return;
        }
        if (session == null || session.getPaidReservation() == null) throw LegacyExamManifest.invalid();
        var snapshot = session.getPaidReservation().snapshot();
        PaidReservationProtocol.snapshot(snapshot);
        if (!Objects.equals(session.getExamId(), snapshot.sessionId())
                || !Objects.equals(session.getMockExamId(), snapshot.mockExamId())
                || !Objects.equals(session.getCreationOperationId(), snapshot.operationId())
                || !Objects.equals(session.getBillingReservationId(), snapshot.reservationId())
                || !Objects.equals(session.getAttemptGroupId(), snapshot.attemptGroupId())
                || session.getBillingReservationKind() != snapshot.reservationKind()) throw LegacyExamManifest.invalid();
        if (session.getEntitlementState() == ExamEntitlementState.CONFIRMED) {
            if (snapshot.reservationStatus() != ReservationStatus.CONFIRMED
                    || !Objects.equals(session.getEntitlementConfirmedAt(), snapshot.terminalAt())
                    || session.getEntitlementConfirmedUserId() == null
                    || session.getEntitlementConfirmedUserId().isBlank()) throw LegacyExamManifest.invalid();
            // A legitimate ownership migration can change current userId, not the historical confirmer.
        } else if (session.getEntitlementState() != ExamEntitlementState.CONFIRMING
                || snapshot.reservationStatus() == ReservationStatus.CONFIRMED
                || session.isActive() || !(session.isEntitlementConfirming() || session.isAbandoned())) {
            throw LegacyExamManifest.invalid();
        }
    }
}
