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
import web.tosunsaeng.domain.exams.billing.reconciliation.ReservationRecovery;

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
    private ReservationRecovery recovery;
    private PaidExamSelection paidSelection;
    private String paidRequestDigest;
    private PaidReservationEvidence paidReservation;
    // Paid schema only. Immutable original acceptance, not a lease/login/replay timestamp.
    private Instant firstAcceptedAt;
    // Earliest review time, deliberately NOT the TTL field purgeAt.
    private Instant detailPurgeEligibleAt;
    private PaidDeletedSource paidDeletedSource;
    private web.tosunsaeng.domain.exams.rollout.LegacyExamManifest.Summary paidLegacySource;
    private String paidLocalSourceOrigin;

    public void fixPaidLocalSourceOrigin(String origin) {
        requireState(ExamCreationState.PREPARED);
        if (!ExamSession.TRANSITION_FREE_V1.equals(origin) || paidLocalSourceOrigin != null
                || paidLegacySource != null || paidDeletedSource != null || paidSelection == null
                || paidSelection.startReason() == null || replacementSourceSessionId == null
                || expectedAttemptGroupId != null || isPhoneContinuation())
            throw new IllegalStateException("Invalid local free source binding");
        paidLocalSourceOrigin = origin;
    }

    public void fixPaidLegacySource(web.tosunsaeng.domain.exams.rollout.LegacyExamManifest.Summary source) {
        requireState(ExamCreationState.PREPARED);
        if (paidLocalSourceOrigin != null || paidLegacySource != null || paidDeletedSource != null || paidSelection == null
                || paidSelection.startReason() == null || replacementSourceSessionId == null
                || expectedAttemptGroupId != null || isPhoneContinuation())
            throw new IllegalStateException("Invalid legacy source binding");
        paidLegacySource = java.util.Objects.requireNonNull(source);
    }

    public record PaidDeletedSource(String deletionId, String eventId) {
        public PaidDeletedSource {
            web.tosunsaeng.domain.learningrecorddeletion.domain.DeletionIdentifiers.uuidV4(deletionId);
            web.tosunsaeng.domain.learningrecorddeletion.domain.DeletionIdentifiers.uuidV4(eventId);
        }
    }
    public void fixPaidDeletedSource(PaidDeletedSource source) {
        requireState(ExamCreationState.PREPARED);
        if (paidLocalSourceOrigin != null || paidDeletedSource != null || paidLegacySource != null || paidSelection == null || isPhoneContinuation() || replacementSourceSessionId == null)
            throw new IllegalStateException("Invalid deleted source binding");
        paidDeletedSource = java.util.Objects.requireNonNull(source);
    }

    public static final java.time.Duration PAID_RETENTION = java.time.Duration.ofDays(120);

    public Instant getGuaranteeUntil() {
        return firstAcceptedAt == null ? null : firstAcceptedAt.plus(PAID_RETENTION);
    }

    public boolean withinPaidGuarantee(Instant now) {
        return paidSelection != null && firstAcceptedAt != null && now != null
                && !now.isBefore(firstAcceptedAt) && now.isBefore(getGuaranteeUntil());
    }

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
                null, null, null, true, now, now, null, null, ReservationRecovery.prepared(now), null, null, null, null, null, null, null, null, null
        );
    }

    public boolean isPhoneContinuation() {
        return continuationReason == BillingContinuationReason.PHONE_REJOIN;
    }

    public void fixPaidSelection(PaidExamSelection selection) {
        requireState(ExamCreationState.PREPARED);
        if (paidSelection != null) throw new IllegalStateException("Selection is already fixed");
        paidSelection = java.util.Objects.requireNonNull(selection);
        firstAcceptedAt = createdAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        createdAt = firstAcceptedAt;
        updatedAt = firstAcceptedAt;
        // P2/P3 must explicitly adopt this format; legacy status/cancel workers must not claim it.
        recovery.setSchemaVersion(2);
    }

    public void fixPaidRequestDigest(String digest) {
        web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.require(paidSelection != null
                && digest != null && digest.matches("[0-9a-f]{64}")
                && (paidRequestDigest == null || paidRequestDigest.equals(digest)));
        paidRequestDigest = digest;
    }

    public void observePaidReservation(web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationSnapshot snapshot) {
        web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.require(paidRequestDigest != null);
        web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.matches(this, snapshot);
        paidReservation = paidReservation == null ? new PaidReservationEvidence(snapshot) : paidReservation.observe(snapshot);
    }

    /** Preserve the durable lease after the transaction fence increments @Version. */
    public void adoptExecutionMetadata(ExamCreationOperation fenced) {
        this.version = fenced.version;
        this.recovery = fenced.recovery;
    }

    public void initializeLegacyRecovery(Instant now) {
        if (recovery == null) {
            recovery = ReservationRecovery.prepared(now);
            // A legacy record never proves that reserve was not sent.
            recovery.setSchemaVersion(0);
            recovery.setDispatch(ReservationRecovery.Dispatch.MAY_HAVE_BEEN_SENT);
        }
    }

    public void observeReservation(String id, BillingReservationKind kind, String group, Instant expiresAt) {
        if (state != ExamCreationState.PREPARED) {
            throw new IllegalStateException("Only PREPARED can adopt a reservation snapshot");
        }
        reservationId = id;
        reservationKind = kind;
        attemptGroupId = group;
        reservationExpiresAt = expiresAt;
    }

    public void completeRecovery(Instant resolvedAt) {
        if (!isTerminal() || recovery == null) return;
        recovery.setStatus(ReservationRecovery.Status.DONE);
        recovery.setResolvedAt(resolvedAt);
        recovery.setLeaseToken(null);
        recovery.setLeaseOwner(null);
        recovery.setLeaseUntil(null);
        if (paidSelection != null) {
            // Holds/unknown outcomes cannot be enforced by Mongo's TTL monitor.
            // P6.1 never arms automatic deletion for paid records.
            purgeAt = null;
            return;
        }
        Instant minimum = resolvedAt.plus(java.time.Duration.ofDays(7));
        if (purgeAt == null || purgeAt.isBefore(minimum)) purgeAt = minimum;
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
        if (recovery != null) recovery.setDispatch(ReservationRecovery.Dispatch.OBSERVED);
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

    public void markPaidSucceeded(Instant confirmedAt, Instant resolvedAt) {
        requireState(ExamCreationState.SESSION_COMMITTED);
        if (paidSelection == null) throw new IllegalStateException("Paid operation required");
        this.confirmedAt = confirmedAt;
        this.state = ExamCreationState.SUCCEEDED;
        markTerminal(resolvedAt, resolvedAt.plus(java.time.Duration.ofDays(120)));
    }

    public void markCancelPending(Instant now) {
        if (state != ExamCreationState.PREPARED && state != ExamCreationState.RESERVED
                && state != ExamCreationState.SESSION_COMMITTED
                && state != ExamCreationState.CANCEL_PENDING) {
            throw new IllegalStateException("Exam creation operation cannot enter CANCEL_PENDING");
        }
        this.state = ExamCreationState.CANCEL_PENDING;
        this.updatedAt = now;
    }

    public void markCanceled(Instant terminalAt, Instant purgeAt) {
        if (state != ExamCreationState.PREPARED && state != ExamCreationState.RESERVED
                && state != ExamCreationState.SESSION_COMMITTED
                && state != ExamCreationState.CANCEL_PENDING) {
            throw new IllegalStateException("Exam creation operation cannot enter CANCELED");
        }
        this.state = ExamCreationState.CANCELED;
        markTerminal(terminalAt, purgeAt);
    }

    public void markExpired(Instant terminalAt, Instant purgeAt) {
        if (state != ExamCreationState.PREPARED && state != ExamCreationState.RESERVED
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
        this.terminalAt = paidSelection == null ? terminalAt : terminalAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        this.purgeAt = paidSelection == null ? purgeAt : null;
        if (paidSelection != null && firstAcceptedAt != null && !this.terminalAt.isBefore(firstAcceptedAt)) {
            Instant terminalLimit = this.terminalAt.plus(PAID_RETENTION);
            detailPurgeEligibleAt = terminalLimit.isAfter(getGuaranteeUntil()) ? terminalLimit : getGuaranteeUntil();
        }
        this.updatedAt = this.terminalAt;
    }

    private void requireState(ExamCreationState expected) {
        if (state != expected) {
            throw new IllegalStateException("Unexpected exam creation operation state");
        }
    }
}
