package web.tosunsaeng.domain.learningrecorddeletion.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupProjectionStatus;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind;
import web.tosunsaeng.domain.exams.domain.enums.ExamCreationState;
import web.tosunsaeng.domain.exams.domain.enums.ExamEntitlementState;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Content-free coordination evidence. Never use this document to render learning history. */
@Document("learning_record_billing_continuations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeletedExamContinuation {
    public enum TransferState { AVAILABLE, CLAIMED, TRANSFERRED, RETIRED }

    @Id private String sourceExamId;
    @Version private Long version;
    private String userId;
    private String deletionId;
    private String mockExamId;
    private Integer cycleNumber;
    private String attemptGroupId;
    private String sourceReservationId;
    private String sourceCreationOperationId;
    private AttemptGroupProjectionStatus groupStatus;
    private String terminalEventId;
    private TransferState transferState;
    private String claimedByCreationCommandId;
    private String replacementExamId;
    private Instant createdAt;
    private Instant claimedAt;
    private Instant transferredAt;
    private Instant groupTerminalAt;
    private Instant expiresAt;

    public static DeletedExamContinuation capture(String deletionId, ExamSession source, Instant now) {
        Objects.requireNonNull(source);
        require(now != null && source.getExamId() != null && !source.getExamId().isBlank());
        require(source.getMockExamId() != null && !source.getMockExamId().isBlank());
        require(source.getCycleNumber() != null && source.getCycleNumber() > 0);
        require(source.getBillingReservationId() != null && !source.getBillingReservationId().isBlank());
        require(source.getCreationOperationId() != null && !source.getCreationOperationId().isBlank());
        require(source.getEntitlementState() == ExamEntitlementState.CONFIRMED);
        var status = source.getAttemptGroupProjectionStatus();
        require(status == AttemptGroupProjectionStatus.OPEN
                || status == AttemptGroupProjectionStatus.RETAKE_AVAILABLE
                || status == AttemptGroupProjectionStatus.COMPLETED);
        require(status != AttemptGroupProjectionStatus.OPEN || source.isInProgress());
        require(status != AttemptGroupProjectionStatus.COMPLETED || source.isCompleted());
        require(status != AttemptGroupProjectionStatus.RETAKE_AVAILABLE || source.isRetakeAvailable());
        if (status != AttemptGroupProjectionStatus.OPEN) {
            require(source.getTerminalEventId() != null && !source.getTerminalEventId().isBlank());
        }
        DeletedExamContinuation result = new DeletedExamContinuation();
        result.sourceExamId = source.getExamId();
        result.userId = DeletionIdentifiers.userId(source.getUserId());
        result.deletionId = DeletionIdentifiers.uuidV4(deletionId);
        result.mockExamId = source.getMockExamId();
        result.cycleNumber = source.getCycleNumber();
        result.attemptGroupId = DeletionIdentifiers.uuidV4(source.getAttemptGroupId());
        result.sourceReservationId = source.getBillingReservationId();
        result.sourceCreationOperationId = source.getCreationOperationId();
        result.groupStatus = status;
        result.terminalEventId = source.getTerminalEventId();
        result.transferState = status == AttemptGroupProjectionStatus.COMPLETED
                ? TransferState.RETIRED : TransferState.AVAILABLE;
        result.createdAt = now;
        // OPEN and reusable RETAKE evidence must not expire while a continuation remains possible.
        // Terminal retention needs a separately verified lifecycle event, not merely a local projection.
        return result;
    }

    public void claim(ExamCreationOperation operation, Instant now) {
        require(operation.getState() == ExamCreationState.PREPARED);
        requireMatches(operation);
        if (transferState == TransferState.CLAIMED) {
            require(Objects.equals(claimedByCreationCommandId, operation.getCommandId()));
            require(Objects.equals(replacementExamId, operation.getSessionId()));
            return;
        }
        require(transferState == TransferState.AVAILABLE && now != null && !now.isBefore(createdAt));
        transferState = TransferState.CLAIMED;
        claimedByCreationCommandId = operation.getCommandId();
        replacementExamId = operation.getSessionId();
        claimedAt = now;
    }

    public void transfer(ExamCreationOperation operation, Instant now) {
        requireMatches(operation);
        require(Objects.equals(claimedByCreationCommandId, operation.getCommandId()));
        require(Objects.equals(replacementExamId, operation.getSessionId()));
        require(operation.getReservationKind() == BillingReservationKind.REPLACEMENT);
        require(Objects.equals(attemptGroupId, operation.getAttemptGroupId()));
        require(operation.getState() == ExamCreationState.SESSION_COMMITTED
                || operation.getState() == ExamCreationState.SUCCEEDED);
        if (transferState == TransferState.TRANSFERRED) return;
        require(transferState == TransferState.CLAIMED && now != null && !now.isBefore(claimedAt));
        transferState = TransferState.TRANSFERRED;
        transferredAt = now;
    }

    /** Caller must additionally prove Session absence in the same Mongo transaction. */
    public void releaseCanceledClaim(ExamCreationOperation operation) {
        requireMatches(operation);
        require(Objects.equals(claimedByCreationCommandId, operation.getCommandId()));
        require(Objects.equals(replacementExamId, operation.getSessionId()));
        require(transferState == TransferState.CLAIMED);
        require(operation.getState() == ExamCreationState.CANCELED || operation.getState() == ExamCreationState.EXPIRED);
        require(operation.getSessionCommittedAt() == null);
        require(operation.getReservationKind() == BillingReservationKind.REPLACEMENT);
        require(Objects.equals(attemptGroupId, operation.getAttemptGroupId()));
        transferState = TransferState.AVAILABLE;
        claimedByCreationCommandId = null;
        replacementExamId = null;
        claimedAt = null;
    }

    /** Invoke only after the group lifecycle is independently proven terminal; never from lease expiry. */
    public void retire(Instant terminalAt) {
        require(transferState == TransferState.RETIRED || transferState == TransferState.TRANSFERRED);
        require(terminalAt != null && !terminalAt.isBefore(createdAt));
        if (groupTerminalAt != null) {
            require(groupTerminalAt.equals(terminalAt));
            return;
        }
        groupTerminalAt = terminalAt;
        expiresAt = terminalAt.plus(Duration.ofDays(30));
    }

    private void requireMatches(ExamCreationOperation operation) {
        require(Objects.equals(userId, operation.getUserId()));
        require(Objects.equals(sourceExamId, operation.getReplacementSourceSessionId()));
        require(Objects.equals(attemptGroupId, operation.getExpectedAttemptGroupId()));
        require(Objects.equals(mockExamId, operation.getExpectedMockExamId()));
        require(Objects.equals(mockExamId, operation.getMockExamId()));
        require(Objects.equals(cycleNumber, operation.getCycleNumber()));
        require(operation.getSessionId() != null && !sourceExamId.equals(operation.getSessionId()));
        require(!operation.isPhoneContinuation());
    }

    private static void require(boolean valid) {
        if (!valid) throw new IllegalStateException("Deleted exam continuation evidence is inconsistent");
    }
}
