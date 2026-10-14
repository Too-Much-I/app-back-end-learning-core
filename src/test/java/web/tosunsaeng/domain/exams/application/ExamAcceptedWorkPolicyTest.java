package web.tosunsaeng.domain.exams.application;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.exams.domain.entity.*;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.repository.QuestionGradingJobRepository;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExamAcceptedWorkPolicyTest {
    static final Instant NOW=Instant.parse("2026-10-14T00:00:00Z");
    final QuestionGradingJobRepository jobs=mock(QuestionGradingJobRepository.class);
    final ExamSubmissionEvidence receipts=mock(ExamSubmissionEvidence.class);
    final ExamAcceptedWorkPolicy policy=new ExamAcceptedWorkPolicy(jobs,receipts);
    ExamSession closed() { return ExamSession.builder().examId("old").status(ExamSessionStatus.IN_PROGRESS)
            .submissionEvidenceVersion(1).requiredQuestionNumbers(List.of(1,2)).submissionsClosedAt(NOW).build(); }

    @Test void acceptedUserJobSurvivesClosureButSyntheticJobDoesNot() {
        var job=QuestionGradingJob.pending(GradingKeys.questionJobId("old",1,1),"old",1,1,"key",NOW);
        when(jobs.findById(job.getJobId())).thenReturn(Optional.of(job));
        assertThat(policy.questionAllowed(closed(),1,1)).isFalse();
        job.markUserSubmission();
        assertThat(policy.questionAllowed(closed(),1,1)).isTrue();
    }
    @Test void receiptCanRecoverLostJobWithoutAcceptingNewQuestion() {
        when(receipts.acceptedQuestion(any(),eq(1),eq(0))).thenReturn(true);
        assertThat(policy.questionAllowed(closed(),1,0)).isTrue();
        assertThat(policy.questionAllowed(closed(),2,0)).isFalse();
        assertThat(policy.questionAllowed(closed(),1,1)).isFalse();
    }
    @Test void summaryNeedsFullReceiptMarkerNotPartialResultsOrExistingSummary() {
        assertThat(ExamAcceptedWorkPolicy.summaryAllowed(closed())).isFalse();
        var full=ExamSession.builder().examId("old").status(ExamSessionStatus.IN_PROGRESS).submissionEvidenceVersion(1)
                .requiredQuestionNumbers(List.of(1,2)).submissionCompletedAt(NOW).submissionsClosedAt(NOW).build();
        assertThat(ExamAcceptedWorkPolicy.summaryAllowed(full)).isTrue();
        full=ExamSession.builder().examId("old").status(ExamSessionStatus.IN_PROGRESS).submissionEvidenceVersion(1)
                .requiredQuestionNumbers(List.of(1,2)).submissionCompletedAt(NOW.plusSeconds(1)).submissionsClosedAt(NOW).build();
        assertThat(ExamAcceptedWorkPolicy.summaryAllowed(full)).isFalse();
    }
    @Test void pendingAbandonedAndRetakeStatesNeverUseAcceptedWorkException() {
        for(var status:List.of(ExamSessionStatus.ENTITLEMENT_CONFIRMING,ExamSessionStatus.ABANDONED,ExamSessionStatus.RETAKE_AVAILABLE)) {
            var session=ExamSession.builder().examId("old").status(status).submissionCompletedAt(NOW).build();
            assertThat(policy.questionAllowed(session,1,0)).isFalse();
            assertThat(ExamAcceptedWorkPolicy.summaryAllowed(session)).isFalse();
        }
        verifyNoInteractions(jobs,receipts);
    }
    @Test void revokeDenyCannotBeOverriddenByAcceptedJobsOrFullSubmission() {
        var session=ExamSession.builder().examId("old").status(ExamSessionStatus.IN_PROGRESS).accessRevoked(true)
                .submissionEvidenceVersion(1).requiredQuestionNumbers(List.of(1,2)).submissionCompletedAt(NOW).build();
        assertThat(policy.questionAllowed(session,1,0)).isFalse();
        assertThat(ExamAcceptedWorkPolicy.summaryAllowed(session)).isFalse();
        verifyNoInteractions(jobs,receipts);
    }
}
