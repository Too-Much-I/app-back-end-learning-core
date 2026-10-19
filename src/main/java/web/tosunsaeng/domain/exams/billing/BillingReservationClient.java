package web.tosunsaeng.domain.exams.billing;

import web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind;
import web.tosunsaeng.domain.exams.domain.enums.BillingContinuationReason;

import java.time.Instant;
import java.util.Optional;

public interface BillingReservationClient {

    default ReservationSnapshot reservePaid(String operationId, PaidReservationProtocol.Request request) {
        throw new UnsupportedOperationException("Paid reservation client is not configured");
    }

    default ReservationSnapshot statusPaid(String userId, String operationId) {
        throw new UnsupportedOperationException("Paid reservation client is not configured");
    }

    default ReservationSnapshot reservePaidPhone(String operationId, PaidReservationProtocol.PhoneRequest request) {
        throw new UnsupportedOperationException("Paid phone client is not configured");
    }

    default Optional<PhoneContinuationSnapshot> findPaidPhoneContinuation(String userId) {
        throw new UnsupportedOperationException("Paid phone client is not configured");
    }

    default ReservationSnapshot confirmPaid(String operationId, String reservationId, String userId,
            String sessionId, Instant committedAt) {
        throw new UnsupportedOperationException("Paid confirm client is not configured");
    }

    default ReservationSnapshot cancelPaid(String operationId, String reservationId, String userId) {
        throw new UnsupportedOperationException("Paid cancel client is not configured");
    }

    ReservationSnapshot reserve(
            String operationId,
            String userId,
            String sessionId,
            String mockExamId
    );

    ReservationSnapshot reservePhoneContinuation(
            String operationId,
            String userId,
            String sessionId,
            String mockExamId,
            BillingContinuationReason continuationReason,
            String continuationId,
            String expectedAttemptGroupId
    );

    Optional<PhoneContinuationSnapshot> findPhoneContinuation(String userId);

    ReservationSnapshot confirm(
            String operationId,
            String reservationId,
            String userId,
            String sessionId,
            Instant sessionCommittedAt
    );

    ReservationSnapshot cancel(
            String operationId,
            String reservationId,
            String userId
    );

    ReservationSnapshot status(String userId, String operationId);

    enum ReservationStatus {
        RESERVED,
        CONFIRMED,
        CANCELED,
        EXPIRED
    }

    record ReservationSnapshot(
            String operationId,
            String reservationId,
            BillingReservationKind reservationKind,
            ReservationStatus reservationStatus,
            String attemptGroupId,
            AttemptGroupStatus attemptGroupStatus,
            String sessionId,
            String mockExamId,
            BillingContinuationReason continuationReason,
            String continuationId,
            Instant expiresAt,
            Instant terminalAt,
            PaidReservationProtocol.Source entitlementSource,
            PaidReservationProtocol.Transition transition,
            PaidReservationProtocol.ConfirmRejection confirmRejection
    ) {
        public ReservationSnapshot(String operationId, String reservationId, BillingReservationKind reservationKind,
                ReservationStatus reservationStatus, String attemptGroupId, AttemptGroupStatus attemptGroupStatus,
                String sessionId, String mockExamId, BillingContinuationReason continuationReason,
                String continuationId, Instant expiresAt, Instant terminalAt) {
            this(operationId, reservationId, reservationKind, reservationStatus, attemptGroupId, attemptGroupStatus,
                    sessionId, mockExamId, continuationReason, continuationId, expiresAt, terminalAt, null, null, null);
        }
        public ReservationSnapshot(
                String operationId,
                String reservationId,
                BillingReservationKind reservationKind,
                ReservationStatus reservationStatus,
                String attemptGroupId,
                AttemptGroupStatus attemptGroupStatus,
                String sessionId,
                String mockExamId,
                Instant expiresAt,
                Instant terminalAt
        ) {
            this(operationId, reservationId, reservationKind, reservationStatus,
                    attemptGroupId, attemptGroupStatus, sessionId, mockExamId,
                    null, null, expiresAt, terminalAt);
        }
    }

    record PhoneContinuationSnapshot(
            BillingContinuationReason continuationReason,
            String continuationId,
            String attemptGroupId,
            String mockExamId
    ) {
    }

    enum AttemptGroupStatus {
        OPEN,
        GRADING,
        COMPLETED,
        RETAKE_AVAILABLE
    }
}
