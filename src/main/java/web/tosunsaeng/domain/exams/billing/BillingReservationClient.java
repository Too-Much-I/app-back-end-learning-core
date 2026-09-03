package web.tosunsaeng.domain.exams.billing;

import web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind;
import web.tosunsaeng.domain.exams.domain.enums.BillingContinuationReason;

import java.time.Instant;
import java.util.Optional;

public interface BillingReservationClient {

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
            Instant terminalAt
    ) {
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
