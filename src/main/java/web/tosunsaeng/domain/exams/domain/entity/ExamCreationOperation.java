package web.tosunsaeng.domain.exams.domain.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind;
import web.tosunsaeng.domain.exams.domain.enums.ExamCreationState;
import web.tosunsaeng.domain.exams.domain.enums.BillingContinuationReason;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "exam_creation_operations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExamCreationOperation {

    @Id
    private String commandId;
    private String userId;
    private String operationId;
    private String sessionId;
    private String mockExamId;
    private Integer cycleNumber;
    private ExamCreationState state;
    private String reservationId;
    private BillingReservationKind reservationKind;
    private String attemptGroupId;
    private String replacementSourceSessionId;
    private String expectedAttemptGroupId;
    private String expectedMockExamId;
    private BillingContinuationReason continuationReason;
    private String continuationId;
    private Instant reservationExpiresAt;
    private Instant sessionCommittedAt;
    private Instant confirmedAt;
    private String failureCategory;
    private Boolean activeGuard;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant terminalAt;
    private Instant purgeAt;

    @Version
    private Long version;

    public static ExamCreationOperation prepared(
            String userId,
            String operationId,
            String sessionId,
            String mockExamId,
            Integer cycleNumber,
            Instant now
    ) {
        return prepared(userId, operationId, sessionId, mockExamId, cycleNumber,
                null, null, null, null, null, now);
    }

    public static ExamCreationOperation prepared(
            String userId,
            String operationId,
            String sessionId,
            String mockExamId,
            Integer cycleNumber,
            String replacementSourceSessionId,
            String expectedAttemptGroupId,
            String expectedMockExamId,
            Instant now
    ) {
        return prepared(userId, operationId, sessionId, mockExamId, cycleNumber,
                replacementSourceSessionId, expectedAttemptGroupId, expectedMockExamId,
                null, null, now);
    }

    public static ExamCreationOperation prepared(
            String userId,
            String operationId,
            String sessionId,
            String mockExamId,
            Integer cycleNumber,
            String replacementSourceSessionId,
            String expectedAttemptGroupId,
            String expectedMockExamId,
            BillingContinuationReason continuationReason,
            String continuationId,
            Instant now
    ) {
        return new ExamCreationOperation(
                UUID.randomUUID().toString(), userId, operationId, sessionId, mockExamId,
                cycleNumber, ExamCreationState.PREPARED, null, null, null,
                replacementSourceSessionId, expectedAttemptGroupId, expectedMockExamId,
                continuationReason, continuationId, null,
                null, null, null, true, now, now, null, null, null
        );
    }

    public boolean isPhoneContinuation() {
        return continuationReason == BillingContinuationReason.PHONE_REJOIN;
    }

    public boolean isTerminal() {
        return state == ExamCreationState.SUCCEEDED
                || state == ExamCreationState.CANCELED
                || state == ExamCreationState.EXPIRED
                || state == ExamCreationState.FAILED_TERMINAL;
    }

    public void markReserved(
            String reservationId,
            BillingReservationKind reservationKind,
            String attemptGroupId,
            Instant reservationExpiresAt,
            Instant now
    ) {
        requireState(ExamCreationState.PREPARED);
        this.reservationId = reservationId;
        this.reservationKind = reservationKind;
        this.attemptGroupId = attemptGroupId;
        this.reservationExpiresAt = reservationExpiresAt;
        this.state = ExamCreationState.RESERVED;
        this.updatedAt = now;
    }

    public void markCancelPendingFromPrepared(
            String reservationId,
            BillingReservationKind reservationKind,
            String attemptGroupId,
            Instant reservationExpiresAt,
            Instant now
    ) {
        requireState(ExamCreationState.PREPARED);
        this.reservationId = reservationId;
        this.reservationKind = reservationKind;
        this.attemptGroupId = attemptGroupId;
        this.reservationExpiresAt = reservationExpiresAt;
        this.state = ExamCreationState.CANCEL_PENDING;
        this.updatedAt = now;
    }

    public void markSessionCommitted(Instant committedAt) {
        requireState(ExamCreationState.RESERVED);
        this.sessionCommittedAt = committedAt;
        this.state = ExamCreationState.SESSION_COMMITTED;
        this.updatedAt = committedAt;
    }

    public void markSucceeded(Instant confirmedAt, Instant purgeAt) {
        requireState(ExamCreationState.SESSION_COMMITTED);
        this.confirmedAt = confirmedAt;
        this.state = ExamCreationState.SUCCEEDED;
        markTerminal(confirmedAt, purgeAt);
    }

    public void markCancelPending(Instant now) {
        if (state != ExamCreationState.RESERVED
                && state != ExamCreationState.SESSION_COMMITTED
                && state != ExamCreationState.CANCEL_PENDING) {
            throw new IllegalStateException("Exam creation operation cannot enter CANCEL_PENDING");
        }
        this.state = ExamCreationState.CANCEL_PENDING;
        this.updatedAt = now;
    }

    public void markCanceled(Instant terminalAt, Instant purgeAt) {
        if (state != ExamCreationState.RESERVED
                && state != ExamCreationState.SESSION_COMMITTED
                && state != ExamCreationState.CANCEL_PENDING) {
            throw new IllegalStateException("Exam creation operation cannot enter CANCELED");
        }
        this.state = ExamCreationState.CANCELED;
        markTerminal(terminalAt, purgeAt);
    }

    public void markExpired(Instant terminalAt, Instant purgeAt) {
        if (state != ExamCreationState.RESERVED
                && state != ExamCreationState.SESSION_COMMITTED
                && state != ExamCreationState.CANCEL_PENDING) {
            throw new IllegalStateException("Exam creation operation cannot enter EXPIRED");
        }
        this.state = ExamCreationState.EXPIRED;
        markTerminal(terminalAt, purgeAt);
    }

    public void markFailedTerminal(String failureCategory, Instant terminalAt, Instant purgeAt) {
        if (isTerminal()) {
            throw new IllegalStateException("Exam creation operation is already terminal");
        }
        this.failureCategory = failureCategory;
        this.state = ExamCreationState.FAILED_TERMINAL;
        markTerminal(terminalAt, purgeAt);
    }

    private void markTerminal(Instant terminalAt, Instant purgeAt) {
        this.activeGuard = false;
        this.terminalAt = terminalAt;
        this.purgeAt = purgeAt;
        this.updatedAt = terminalAt;
    }

    private void requireState(ExamCreationState expected) {
        if (state != expected) {
            throw new IllegalStateException("Unexpected exam creation operation state");
        }
    }
}
