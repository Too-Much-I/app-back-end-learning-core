package web.tosunsaeng.domain.exams.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupFailureCode;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupProjectionStatus;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind;
import web.tosunsaeng.domain.exams.domain.enums.ExamEntitlementState;

import java.time.Instant;
import java.time.LocalDateTime;

@Document(collection = "exam_sessions")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSession {

    @Id
    private String examId;

    private String userId;

    public static final String TRANSITION_FREE_V1 = "TRANSITION_FREE_V1";
    private String creationOrigin;

    /** Server-written provenance; never infer free admission from absent Billing metadata. */
    public boolean isTransitionFree() {
        if (creationOrigin == null) return false;
        if (!TRANSITION_FREE_V1.equals(creationOrigin) || paidReservation != null || creationOperationId != null
                || billingReservationId != null || attemptGroupId != null || billingReservationKind != null
                || entitlementState != null || entitlementConfirmedAt != null || entitlementConfirmedUserId != null
                || legacyClosureManifest != null || !Integer.valueOf(1).equals(submissionEvidenceVersion)
                || requiredQuestionNumbers == null || requiredQuestionNumbers.isEmpty()
                || requiredQuestionNumbers.stream().anyMatch(n -> n == null || n < 1))
            throw new IllegalStateException("Invalid transition free provenance");
        return true;
    }

    private LocalDateTime createdAt;

    private String mockExamId;

    private Integer cycleNumber;

    private Boolean active;

    // Durable deny projection. OFF/restart must not restore access to a revoked Session.
    private Boolean accessRevoked;
    private String accessRevocationEventId;
    private String accessRevocationUserId;
    private AcceptedWorkClosure acceptedWorkClosure;

    public boolean permitsRevokedAcceptedWork() {
        return isAccessRevoked() && isInProgress() && terminalEventId==null && completedAt==null
                && accessRevocationEventId!=null && !accessRevocationEventId.isBlank()
                && java.util.Objects.equals(accessRevocationUserId,userId)
                && acceptedWorkClosure!=null && acceptedWorkClosure.matches(this);
    }

    public boolean isAccessRevoked() { return Boolean.TRUE.equals(accessRevoked); }

    private ExamSessionStatus status;

    private LocalDateTime completedAt;

    // Actual last required retry-0 submission, independent of grading completion.
    private Instant submissionCompletedAt;

    // Admission closure is separate from grading/summary lifecycle completion.
    private Instant submissionsClosedAt;
    private String submissionsClosedByOperationId;
    private web.tosunsaeng.domain.exams.rollout.LegacyExamManifest.Summary legacyClosureManifest;

    public void closeLegacySubmissions(web.tosunsaeng.domain.exams.rollout.LegacyExamManifest.Summary manifest,
            String operationId, Instant now) {
        if (paidReservation != null || creationOperationId != null || billingReservationId != null || attemptGroupId != null
                || manifest == null || legacyClosureManifest != null && !legacyClosureManifest.equals(manifest))
            throw new IllegalStateException("Invalid legacy closure");
        closeSubmissions(operationId, now);
        legacyClosureManifest = manifest;
    }

    public boolean hasLegacyClosure() {
        return legacyClosureManifest != null && submissionsClosedAt != null && submissionsClosedByOperationId != null
                && paidReservation == null && creationOperationId == null && billingReservationId == null && attemptGroupId == null;
    }

    public void closeSubmissions(String operationId, Instant now) {
        if (submissionsClosedAt != null) {
            if (!java.util.Objects.equals(submissionsClosedByOperationId, operationId))
                throw new IllegalStateException("Session already replaced by another operation");
            return;
        }
        acceptedWorkClosure = AcceptedWorkClosure.capture(this,operationId,now);
        submissionsClosedAt = java.util.Objects.requireNonNull(now);
        submissionsClosedByOperationId = java.util.Objects.requireNonNull(operationId);
        active = false;
    }

    public void activatePaid(Instant confirmedAt) {
        if (isAccessRevoked() || !isEntitlementConfirming() || entitlementState != ExamEntitlementState.CONFIRMING
                || submissionsClosedAt != null || completedAt != null)
            throw new IllegalStateException("Session cannot be activated");
        entitlementState = ExamEntitlementState.CONFIRMED;
        entitlementConfirmedAt = confirmedAt;
        entitlementConfirmedUserId = userId;
        status = ExamSessionStatus.IN_PROGRESS;
        active = true;
    }

    public void cancelPaidPending() {
        if (!isEntitlementConfirming() || entitlementState != ExamEntitlementState.CONFIRMING)
            throw new IllegalStateException("Only a pending Session can be compensated");
        status = ExamSessionStatus.ABANDONED;
        active = false;
    }

    private Integer submissionEvidenceVersion;
    private java.util.List<Integer> requiredQuestionNumbers;

    private String creationOperationId;

    private String billingReservationId;

    private PaidReservationEvidence paidReservation;

    public void observePaidReservation(PaidReservationEvidence evidence) {
        var snapshot = evidence.snapshot();
        web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.require(
                java.util.Objects.equals(examId, snapshot.sessionId())
                && java.util.Objects.equals(mockExamId, snapshot.mockExamId())
                && java.util.Objects.equals(creationOperationId, snapshot.operationId())
                && java.util.Objects.equals(billingReservationId, snapshot.reservationId())
                && java.util.Objects.equals(attemptGroupId, snapshot.attemptGroupId())
                && billingReservationKind == snapshot.reservationKind());
        paidReservation = paidReservation == null ? evidence : paidReservation.observe(snapshot);
    }

    private BillingReservationKind billingReservationKind;

    private String attemptGroupId;

    private ExamEntitlementState entitlementState;

    private Instant entitlementConfirmedAt;
    private String entitlementConfirmedUserId;

    private AttemptGroupProjectionStatus attemptGroupProjectionStatus;

    private Long attemptGroupProjectionVersion;

    private Instant gradingStartedAt;

    private String gradingEventId;

    private String terminalEventId;

    private AttemptGroupFailureCode terminalFailureCode;

    @Version
    private Long version;

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    public ExamSessionStatus effectiveStatus() {
        if (status != null) {
            return status;
        }
        if (completedAt != null) {
            return ExamSessionStatus.COMPLETED;
        }
        if (Boolean.FALSE.equals(active)) {
            return ExamSessionStatus.ABANDONED;
        }
        return ExamSessionStatus.IN_PROGRESS;
    }

    public boolean isInProgress() {
        return effectiveStatus() == ExamSessionStatus.IN_PROGRESS;
    }

    public boolean isEntitlementConfirming() {
        return effectiveStatus() == ExamSessionStatus.ENTITLEMENT_CONFIRMING;
    }

    public boolean isCompleted() {
        return effectiveStatus() == ExamSessionStatus.COMPLETED;
    }

    public boolean isRetakeAvailable() {
        return effectiveStatus() == ExamSessionStatus.RETAKE_AVAILABLE;
    }

    public boolean isAbandoned() {
        return effectiveStatus() == ExamSessionStatus.ABANDONED;
    }

    public AttemptGroupProjectionStatus effectiveAttemptGroupProjectionStatus() {
        return attemptGroupProjectionStatus == null
                ? AttemptGroupProjectionStatus.OPEN
                : attemptGroupProjectionStatus;
    }

    public void markAttemptGroupGrading(String eventId, Instant startedAt) {
        if (effectiveAttemptGroupProjectionStatus() != AttemptGroupProjectionStatus.OPEN) {
            return;
        }
        attemptGroupProjectionStatus = AttemptGroupProjectionStatus.GRADING;
        attemptGroupProjectionVersion = effectiveProjectionVersion() + 1;
        gradingStartedAt = startedAt;
        gradingEventId = eventId;
    }

    public void enableAttemptGroupProjectionForBackfill() {
        if (attemptGroupProjectionStatus != null
                || entitlementState != ExamEntitlementState.CONFIRMED
                || attemptGroupId == null
                || attemptGroupId.isBlank()) {
            throw new IllegalStateException("ExamSession is not eligible for AttemptGroup backfill");
        }
        attemptGroupProjectionStatus = AttemptGroupProjectionStatus.OPEN;
        attemptGroupProjectionVersion = 0L;
    }

    public void markAttemptGroupCompleted(String eventId, Instant now, java.time.ZoneId zone) {
        requireTerminalAvailable();
        completeAttemptGroup(eventId,now,zone);
    }

    /** Called only after full result/summary evaluation inside the existing owner transaction. */
    public void markRevokedAcceptedWorkCompleted(String eventId, Instant now, java.time.ZoneId zone) {
        requireRevokedAcceptedTerminal();
        completeAttemptGroup(eventId,now,zone);
    }

    private void completeAttemptGroup(String eventId, Instant now, java.time.ZoneId zone) {
        attemptGroupProjectionStatus = AttemptGroupProjectionStatus.COMPLETED;
        attemptGroupProjectionVersion = effectiveProjectionVersion() + 1;
        terminalEventId = eventId;
        terminalFailureCode = null;
        status = ExamSessionStatus.COMPLETED;
        active = false;
        completedAt = LocalDateTime.ofInstant(now, zone);
    }

    public void markAttemptGroupRetakeAvailable(String eventId, AttemptGroupFailureCode code) {
        requireTerminalAvailable();
        failAttemptGroup(eventId,code);
    }

    public void markRevokedAcceptedWorkFailed(String eventId, AttemptGroupFailureCode code) {
        requireRevokedAcceptedTerminal();
        failAttemptGroup(eventId,code);
    }

    private void requireRevokedAcceptedTerminal() {
        if (!permitsRevokedAcceptedWork() || !Integer.valueOf(1).equals(submissionEvidenceVersion)
                || submissionCompletedAt==null || submissionCompletedAt.isAfter(submissionsClosedAt)
                || requiredQuestionNumbers==null || requiredQuestionNumbers.isEmpty()
                || requiredQuestionNumbers.stream().anyMatch(n->n==null || n<1))
            throw new IllegalStateException("Accepted terminal evidence is not available");
    }

    private void failAttemptGroup(String eventId, AttemptGroupFailureCode code) {
        attemptGroupProjectionStatus = AttemptGroupProjectionStatus.RETAKE_AVAILABLE;
        attemptGroupProjectionVersion = effectiveProjectionVersion() + 1;
        terminalEventId = eventId;
        terminalFailureCode = code;
        status = ExamSessionStatus.RETAKE_AVAILABLE;
        active = false;
        completedAt = null;
    }

    private long effectiveProjectionVersion() {
        return attemptGroupProjectionVersion == null ? 0L : attemptGroupProjectionVersion;
    }

    private void requireTerminalAvailable() {
        if (isAccessRevoked() || terminalEventId != null || isCompleted() || isRetakeAvailable() || isAbandoned()) {
            throw new IllegalStateException("AttemptGroup terminal state is already decided");
        }
    }
}
