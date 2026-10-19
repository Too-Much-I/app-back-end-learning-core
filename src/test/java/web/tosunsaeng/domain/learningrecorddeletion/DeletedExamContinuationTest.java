package web.tosunsaeng.domain.learningrecorddeletion;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupProjectionStatus;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.enums.*;
import web.tosunsaeng.domain.learningrecorddeletion.domain.DeletedExamContinuation;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

class DeletedExamContinuationTest {
    private static final String OWNER = "00000000-0000-0000-0000-000000000001";
    private static final String ID = "139f345b-9be8-43a3-a63d-9108df972741";
    private static final String GROUP = "d5d1a1f4-bd12-4240-9b05-47a08ced480a";
    private static final Instant NOW = Instant.parse("2026-10-03T01:00:00Z");

    @Test void openSourceCanBeClaimedAndTransferredOnlyOnce() {
        var source = capture();
        var operation = operation();
        source.claim(operation, NOW);
        source.claim(operation, NOW.plusSeconds(1));
        assertThatThrownBy(() -> source.claim(operation(), NOW)).isInstanceOf(IllegalStateException.class);
        operation.markReserved("reservation-new", BillingReservationKind.REPLACEMENT, GROUP, NOW.plusSeconds(300), NOW);
        assertThatThrownBy(() -> source.transfer(operation, NOW)).isInstanceOf(IllegalStateException.class);
        operation.markSessionCommitted(NOW);
        source.transfer(operation, NOW);
        source.transfer(operation, NOW);
        assertThat(source.getTransferState()).isEqualTo(DeletedExamContinuation.TransferState.TRANSFERRED);
        assertThat(source.getExpiresAt()).isNull();
        assertThatThrownBy(() -> source.claim(operation(), NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test void timeoutOrFailedTerminalDoesNotReleaseClaim() {
        var source = capture();
        var operation = operation();
        source.claim(operation, NOW);
        assertThatThrownBy(() -> source.releaseCanceledClaim(operation)).isInstanceOf(IllegalStateException.class);
        operation.markFailedTerminal("TEMPORARY", NOW, NOW.plusSeconds(600));
        assertThatThrownBy(() -> source.releaseCanceledClaim(operation)).isInstanceOf(IllegalStateException.class);
        assertThat(source.getTransferState()).isEqualTo(DeletedExamContinuation.TransferState.CLAIMED);
    }

    @Test void canceledPrecommitReplacementCanReleaseButCommittedOneCannot() {
        var source = capture();
        var operation = operation();
        source.claim(operation, NOW);
        operation.markReserved("reservation-new", BillingReservationKind.REPLACEMENT, GROUP, NOW.plusSeconds(300), NOW);
        operation.markCanceled(NOW, NOW.plusSeconds(600));
        source.releaseCanceledClaim(operation);
        assertThat(source.getTransferState()).isEqualTo(DeletedExamContinuation.TransferState.AVAILABLE);
        var next = operation();
        source.claim(next, NOW);
        next.markReserved("reservation-new", BillingReservationKind.REPLACEMENT, GROUP, NOW.plusSeconds(300), NOW);
        next.markSessionCommitted(NOW);
        next.markCanceled(NOW, NOW.plusSeconds(600));
        assertThatThrownBy(() -> source.releaseCanceledClaim(next)).isInstanceOf(IllegalStateException.class);
    }

    @Test void initialReservationCannotConsumeExistingGroup() {
        var source = capture();
        var operation = operation();
        source.claim(operation, NOW);
        operation.markReserved("reservation-new", BillingReservationKind.INITIAL, GROUP, NOW.plusSeconds(300), NOW);
        operation.markSessionCommitted(NOW);
        assertThatThrownBy(() -> source.transfer(operation, NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test void mismatchedOwnerOrGroupCannotClaim() {
        var source = capture();
        var wrongOwner = ExamCreationOperation.prepared("00000000-0000-0000-0000-000000000002", ID,
                "new", "mock_exam_001", 2, "old", GROUP, "mock_exam_001", NOW);
        assertThatThrownBy(() -> source.claim(wrongOwner, NOW)).isInstanceOf(IllegalStateException.class);
        var wrongGroup = ExamCreationOperation.prepared(OWNER, ID,
                "new", "mock_exam_001", 2, "old", ID, "mock_exam_001", NOW);
        assertThatThrownBy(() -> source.claim(wrongGroup, NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test void unconfirmedOrGradingSourcesCannotBeCaptured() {
        var unconfirmed = session().entitlementState(ExamEntitlementState.CONFIRMING).build();
        var grading = session().attemptGroupProjectionStatus(AttemptGroupProjectionStatus.GRADING).build();
        assertThatThrownBy(() -> DeletedExamContinuation.capture(ID, unconfirmed, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DeletedExamContinuation.capture(ID, grading, NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test void completedSourceCannotGiveAnotherFreeAttempt() {
        var source = DeletedExamContinuation.capture(ID, session().status(ExamSessionStatus.COMPLETED)
                .attemptGroupProjectionStatus(AttemptGroupProjectionStatus.COMPLETED).terminalEventId("event").build(), NOW);
        assertThat(source.getTransferState()).isEqualTo(DeletedExamContinuation.TransferState.RETIRED);
        assertThatThrownBy(() -> source.claim(operation(), NOW)).isInstanceOf(IllegalStateException.class);
        source.retire(NOW);
        assertThat(source.getExpiresAt()).isEqualTo(NOW.plusSeconds(30 * 86400L));
    }

    @Test void contentIsNotCopiedAndUnresolvedEvidenceHasNoTtl() {
        assertThat(capture().getExpiresAt()).isNull();
        var fields = Arrays.stream(DeletedExamContinuation.class.getDeclaredFields()).map(java.lang.reflect.Field::getName).toList();
        assertThat(fields).doesNotContain("audio", "s3Key", "transcript", "result", "feedback", "score", "questions");
        assertThatThrownBy(() -> capture().retire(NOW)).isInstanceOf(IllegalStateException.class);
    }

    private static ExamSession.ExamSessionBuilder session() {
        return ExamSession.builder().examId("old").userId(OWNER).mockExamId("mock_exam_001").cycleNumber(2)
                .attemptGroupId(GROUP).billingReservationId("reservation-old").creationOperationId(ID)
                .entitlementState(ExamEntitlementState.CONFIRMED).status(ExamSessionStatus.IN_PROGRESS)
                .attemptGroupProjectionStatus(AttemptGroupProjectionStatus.OPEN);
    }
    private static DeletedExamContinuation capture() { return DeletedExamContinuation.capture(ID, session().build(), NOW); }
    private static ExamCreationOperation operation() {
        return ExamCreationOperation.prepared(OWNER, ID, "new", "mock_exam_001", 2, "old", GROUP, "mock_exam_001", NOW);
    }
}
