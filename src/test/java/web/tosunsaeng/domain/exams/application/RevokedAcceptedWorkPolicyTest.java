package web.tosunsaeng.domain.exams.application;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import web.tosunsaeng.domain.exams.billing.BillingReservationClient.*;
import web.tosunsaeng.domain.exams.billing.PaidReservationProtocol;
import web.tosunsaeng.domain.exams.domain.entity.*;
import web.tosunsaeng.domain.exams.domain.enums.*;
import web.tosunsaeng.domain.exams.domain.repository.QuestionGradingJobRepository;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RevokedAcceptedWorkPolicyTest {
    static final Instant NOW=Instant.parse("2026-10-14T00:00:00Z");
    static final String ID="30000000-0000-4000-8000-000000000001";
    final QuestionGradingJobRepository jobs=mock(QuestionGradingJobRepository.class);
    final ExamSubmissionEvidence evidence=mock(ExamSubmissionEvidence.class);
    final ExamAcceptedWorkPolicy policy=new ExamAcceptedWorkPolicy(jobs,evidence);

    ExamSession confirmed() {
        return confirmed("old", ID, "mock");
    }
    static ExamSession confirmed(String exam,String owner,String mock) {
        var snapshot=new ReservationSnapshot(ID,ID,BillingReservationKind.INITIAL,ReservationStatus.CONFIRMED,
                ID,AttemptGroupStatus.OPEN,exam,mock,null,null,NOW.plusSeconds(300),NOW.minusSeconds(60),
                PaidReservationProtocol.Source.SUBSCRIPTION,null,null);
        return ExamSession.builder().examId(exam).userId(owner).mockExamId(mock).creationOperationId(ID)
                .billingReservationId(ID).billingReservationKind(BillingReservationKind.INITIAL).attemptGroupId(ID)
                .entitlementState(ExamEntitlementState.CONFIRMED).entitlementConfirmedAt(snapshot.terminalAt()).entitlementConfirmedUserId(owner)
                .paidReservation(new PaidReservationEvidence(snapshot)).active(true).status(ExamSessionStatus.IN_PROGRESS)
                .submissionEvidenceVersion(1).requiredQuestionNumbers(List.of(1,2)).build();
    }
    ExamSession revoked() {
        var s=confirmed();s.closeSubmissions("closing-operation",NOW);
        ReflectionTestUtils.setField(s,"accessRevoked",true);
        ReflectionTestUtils.setField(s,"accessRevocationEventId",ID);
        ReflectionTestUtils.setField(s,"accessRevocationUserId",ID);return s;
    }
    QuestionGradingJob job(int retry) {
        return QuestionGradingJob.pending(GradingKeys.questionJobId("old",1,retry),"old",1,retry,"key","mock",NOW.minusSeconds(1));
    }
    @Test void c01ExactOldWorkSurvivesRevokeAndSystemRetryWithoutReopeningAdmission() {
        var s=revoked();var j=job(1).markUserSubmission(NOW.minusSeconds(1));
        when(jobs.findById(j.getJobId())).thenReturn(Optional.of(j));
        assertThat(policy.questionAllowed(s,1,1)).isTrue();
        j.complete(NOW);j.reopenMissingResult(NOW.plusSeconds(100));
        assertThat(j.getPendingAt()).isEqualTo(NOW.plusSeconds(100));
        assertThat(policy.questionAllowed(s,1,1)).isTrue();
        assertThat(s.isAccessRevoked()).isTrue();assertThat(s.isActive()).isFalse();
    }
    @Test void c02NewInputAndUnprovenJobAreDenied() {
        var s=revoked();var j=job(1).markUserSubmission();
        when(jobs.findById(j.getJobId())).thenReturn(Optional.of(j));
        assertThat(policy.questionAllowed(s,1,1)).isFalse();
        j.markUserSubmission(NOW.minusSeconds(1)); // Cannot backfill a legacy boolean into new evidence.
        assertThat(j.getFirstUserSubmittedAt()).isNull();
        when(jobs.findById(j.getJobId())).thenReturn(Optional.of(job(1).markUserSubmission(NOW.plusMillis(1))));
        assertThat(policy.questionAllowed(s,1,1)).isFalse();
        assertThat(policy.questionAllowed(s,2,1)).isFalse();
    }
    @Test void c03PartialReceiptDoesNotAuthorizeSummaryOrTerminal() {
        var s=revoked();when(evidence.acceptedQuestion(s,1,0)).thenReturn(true);
        assertThat(policy.questionAllowed(s,1,0)).isTrue();assertThat(ExamAcceptedWorkPolicy.summaryAllowed(s)).isFalse();
        assertThatThrownBy(()->s.markRevokedAcceptedWorkCompleted(ID,NOW,ZoneOffset.UTC)).isInstanceOf(IllegalStateException.class);
    }
    @Test void fullMarkerAllowsDerivedSummaryButOnlyDedicatedTerminalPathOnce() {
        var s=revoked();ReflectionTestUtils.setField(s,"submissionCompletedAt",NOW);
        assertThat(ExamAcceptedWorkPolicy.summaryAllowed(s)).isTrue();
        assertThatThrownBy(()->s.markAttemptGroupCompleted(ID,NOW,ZoneOffset.UTC)).isInstanceOf(IllegalStateException.class);
        s.markRevokedAcceptedWorkCompleted(ID,NOW,ZoneOffset.UTC);
        assertThat(s.isCompleted()).isTrue();assertThat(ExamAcceptedWorkPolicy.processable(s)).isFalse();
        assertThatThrownBy(()->s.markRevokedAcceptedWorkCompleted(ID,NOW,ZoneOffset.UTC)).isInstanceOf(IllegalStateException.class);
    }
    @Test void c04TerminalAndOwnerChangesNeverInheritException() {
        for(var status:List.of(ExamSessionStatus.COMPLETED,ExamSessionStatus.ABANDONED,ExamSessionStatus.RETAKE_AVAILABLE)) {
            var s=revoked();ReflectionTestUtils.setField(s,"status",status);
            assertThat(ExamAcceptedWorkPolicy.processable(s)).isFalse();
        }
        var s=revoked();ReflectionTestUtils.setField(s,"userId","other");
        assertThat(ExamAcceptedWorkPolicy.processable(s)).isFalse();
    }
    @Test void c06MissingOrMismatchedConfirmOwnerSourceClosureAndEventFailClosed() {
        for(String field:List.of("paidReservation","entitlementConfirmedAt","entitlementConfirmedUserId","acceptedWorkClosure","accessRevocationEventId","accessRevocationUserId")) {
            var s=revoked();ReflectionTestUtils.setField(s,field,null);
            assertThat(ExamAcceptedWorkPolicy.processable(s)).isFalse();
        }
        for(String field:List.of("mockExamId","billingReservationId","attemptGroupId","creationOperationId","submissionsClosedByOperationId")) {
            var s=revoked();ReflectionTestUtils.setField(s,field,"different");
            assertThat(ExamAcceptedWorkPolicy.processable(s)).isFalse();
        }
    }
    @Test void wrongJobIdentityCannotFallbackToValidReceipt() {
        var s=revoked();var j=job(0).markUserSubmission(NOW.minusSeconds(1));
        when(jobs.findById(j.getJobId())).thenReturn(Optional.of(j));
        ReflectionTestUtils.setField(j,"examId","different");
        assertThat(policy.questionAllowed(s,1,0)).isFalse();verifyNoInteractions(evidence);
    }
    @Test void closureReplayDoesNotMoveBoundaryOrEraseProofAndRevokeFirstCannotCreateProof() {
        var s=revoked();var proof=s.getAcceptedWorkClosure();s.closeSubmissions("closing-operation",NOW.plusSeconds(1));
        assertThat(s.getAcceptedWorkClosure()).isEqualTo(proof);assertThat(s.getSubmissionsClosedAt()).isEqualTo(NOW);
        var other=confirmed();ReflectionTestUtils.setField(other,"accessRevoked",true);other.closeSubmissions("closing",NOW);
        assertThat(other.getAcceptedWorkClosure()).isNull();
    }
}
