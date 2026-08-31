package web.tosunsaeng.domain.exams.billing;

import web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind;

import java.time.Instant;

public interface BillingReservationClient {

    ReservationSnapshot reserve(
            String operationId,
            String userId,
            String sessionId,
            String mockExamId
    );

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
            Instant expiresAt,
            Instant terminalAt
    ) {
    }

    enum AttemptGroupStatus {
        OPEN,
        GRADING,
        COMPLETED,
        RETAKE_AVAILABLE
    }
}
