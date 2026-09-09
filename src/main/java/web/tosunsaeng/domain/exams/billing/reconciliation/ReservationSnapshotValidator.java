package web.tosunsaeng.domain.exams.billing.reconciliation;

import web.tosunsaeng.domain.exams.billing.BillingReservationClient;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.enums.BillingContinuationReason;
import web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind;

import java.util.Objects;
import java.util.UUID;

import static web.tosunsaeng.domain.exams.billing.reconciliation.ReservationRecoveryException.Reason.STATE_INCONSISTENT;

public final class ReservationSnapshotValidator {
    private ReservationSnapshotValidator() { }

    public static void status(ExamCreationOperation op, BillingReservationClient.ReservationSnapshot s) {
        require(s != null && s.reservationStatus() != null && s.reservationKind() != null
                && uuid4(s.reservationId()) && uuid4(s.attemptGroupId())
                && Objects.equals(op.getOperationId(), s.operationId())
                && Objects.equals(op.getSessionId(), s.sessionId())
                && Objects.equals(op.getMockExamId(), s.mockExamId()));
        if (op.getReservationId() != null) require(Objects.equals(op.getReservationId(), s.reservationId()));
        if (op.getReservationKind() != null) require(op.getReservationKind() == s.reservationKind());
        if (op.getAttemptGroupId() != null) require(Objects.equals(op.getAttemptGroupId(), s.attemptGroupId()));
        boolean replacement = op.getExpectedAttemptGroupId() != null;
        require((s.reservationKind() == BillingReservationKind.REPLACEMENT) == replacement);
        if (replacement) require(uuid4(op.getExpectedAttemptGroupId())
                && Objects.equals(op.getExpectedAttemptGroupId(), s.attemptGroupId())
                && Objects.equals(op.getExpectedMockExamId(), s.mockExamId()));
        if (op.isPhoneContinuation()) {
            require(op.getReplacementSourceSessionId() == null && uuid4(op.getContinuationId())
                    && s.continuationReason() == BillingContinuationReason.PHONE_REJOIN
                    && Objects.equals(op.getContinuationId(), s.continuationId()));
        } else {
            require(s.continuationId() == null && s.continuationReason() == null
                    && op.getContinuationId() == null && op.getContinuationReason() == null);
            require((op.getReplacementSourceSessionId() != null) == replacement);
        }
        require(s.expiresAt() != null);
        if (s.reservationStatus() != BillingReservationClient.ReservationStatus.RESERVED) require(s.terminalAt() != null);
        // Past expiry is valid evidence; only Billing decides whether RESERVED has become EXPIRED.
    }

    public static void confirmed(ExamCreationOperation op, BillingReservationClient.ReservationSnapshot s) {
        require(s != null && s.reservationStatus() == BillingReservationClient.ReservationStatus.CONFIRMED
                && Objects.equals(op.getOperationId(), s.operationId())
                && Objects.equals(op.getReservationId(), s.reservationId())
                && Objects.equals(op.getSessionId(), s.sessionId())
                && Objects.equals(op.getAttemptGroupId(), s.attemptGroupId())
                && s.attemptGroupStatus() == BillingReservationClient.AttemptGroupStatus.OPEN
                && s.terminalAt() != null);
    }

    public static void canceled(ExamCreationOperation op, BillingReservationClient.ReservationSnapshot s) {
        require(s != null && s.reservationStatus() == BillingReservationClient.ReservationStatus.CANCELED
                && Objects.equals(op.getOperationId(), s.operationId())
                && Objects.equals(op.getReservationId(), s.reservationId()) && s.terminalAt() != null);
    }

    public static void session(ExamCreationOperation op, ExamSession session) {
        require(session != null && Objects.equals(op.getSessionId(), session.getExamId())
                && Objects.equals(op.getUserId(), session.getUserId())
                && Objects.equals(op.getMockExamId(), session.getMockExamId())
                && Objects.equals(op.getOperationId(), session.getCreationOperationId())
                && Objects.equals(op.getReservationId(), session.getBillingReservationId())
                && op.getReservationKind() == session.getBillingReservationKind()
                && Objects.equals(op.getAttemptGroupId(), session.getAttemptGroupId()));
    }

    public static boolean uuid4(String value) {
        if (value == null) return false;
        try {
            UUID id = UUID.fromString(value);
            return id.version() == 4 && id.variant() == 2 && id.toString().equals(value);
        } catch (IllegalArgumentException e) { return false; }
    }

    public static void require(boolean condition) {
        if (!condition) throw new ReservationRecoveryException(STATE_INCONSISTENT);
    }
}
