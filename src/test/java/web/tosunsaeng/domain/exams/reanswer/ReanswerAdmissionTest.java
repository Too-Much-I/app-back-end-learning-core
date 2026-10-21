package web.tosunsaeng.domain.exams.reanswer;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import web.tosunsaeng.domain.exams.application.*;
import web.tosunsaeng.domain.exams.domain.entity.*;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.repository.QuestionGradingJobRepository;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReanswerAdmissionTest {
    static final String USER="11111111-1111-4111-8111-111111111111";
    static final Instant NOW=Instant.parse("2026-10-21T08:00:00Z");
    final QuestionGradingJobRepository jobs=mock(QuestionGradingJobRepository.class);
    final MockExamCatalogService catalog=mock(MockExamCatalogService.class);
    final ReanswerEligibilityClient client=mock(ReanswerEligibilityClient.class);
    final ObjectProvider<ReanswerEligibilityClient> provider=mock(ObjectProvider.class);
    final AtomicLong nanos=new AtomicLong();
    final ReanswerAdmission gate=new ReanswerAdmission(true,provider,jobs,catalog,Clock.fixed(NOW,ZoneOffset.UTC),nanos::get);
    final ReanswerApproval proof=new ReanswerApproval(ReanswerApproval.TYPE,ReanswerApproval.REVISION,USER,NOW.toString(),NOW.plusSeconds(5).toString());
    ExamSession session(boolean revoked) { return ExamSession.builder().examId("exam").userId(USER)
            .mockExamId("mock").status(ExamSessionStatus.COMPLETED).accessRevoked(revoked)
            .submissionsClosedAt(NOW.minusSeconds(5)).build(); }
    @BeforeEach void setup() {
        when(provider.getObject()).thenReturn(client);
        when(client.check(USER)).thenReturn(proof);
        when(catalog.getRequiredExam("mock")).thenReturn(MockExam.builder().questions(List.of(Question.builder().questionNumber(1).build())).build());
    }
    @Test void closedCompletedSessionGetsOneFreshObservation() {
        var observation=gate.prepare(session(false),1,1,true);
        assertEquals(proof,gate.authorize(session(false),1,1,observation,null));
        verify(client,times(1)).check(USER);
    }
    @Test void exactFiveSecondsAndWrongJobOrOwnerFail() {
        var observation=gate.prepare(session(false),1,1,true);
        assertThrows(ExamsException.class,()->gate.authorize(session(false),1,2,observation,null));
        nanos.set(5_000_000_000L);
        assertThrows(ExamsException.class,()->gate.authorize(session(false),1,1,observation,null));
    }
    @Test void futureCheckedAtAndExpiredWindowDoNotAuthorize() {
        when(client.check(USER)).thenReturn(new ReanswerApproval(ReanswerApproval.TYPE,ReanswerApproval.REVISION,USER,
                NOW.plusNanos(1).toString(),NOW.plusSeconds(5).toString()));
        assertThrows(IllegalStateException.class,()->gate.prepare(session(false),1,1,true));
    }
    @Test void acceptedJobReplaysWithoutBillingAndSurvivesExpiryButNotRevocation() {
        var job=QuestionGradingJob.pending("question:exam:1:1","exam",1,1,"key","mock",NOW).markUserSubmission(NOW);
        job.approveReanswer(proof);
        when(jobs.findById(job.getJobId())).thenReturn(Optional.of(job));
        var observation=gate.prepare(session(false),1,1,true);
        nanos.set(100_000_000_000L);
        assertNull(gate.authorize(session(false),1,1,observation,job));
        verifyNoInteractions(client);
        assertTrue(ReanswerAdmission.accepted(session(false),job));
        assertFalse(ReanswerAdmission.accepted(session(true),job));
        assertThrows(ExamsException.class,()->gate.authorize(session(true),1,1,observation,job));
        assertThrows(IllegalStateException.class,()->job.approveReanswer(proof));
        assertEquals(NOW,job.getFirstUserSubmittedAt());
    }
    @Test void uncompletedOrRetryZeroDoNotReceiveExceptionPermission() {
        assertFalse(gate.applies(session(false),0));
        assertFalse(gate.applies(ExamSession.builder().status(ExamSessionStatus.IN_PROGRESS).build(),1));
        assertThrows(ExamsException.class,()->gate.prepare(session(true),1,1,true));
        verifyNoInteractions(client);
    }
    @Test void uploadDoesNotReplayApprovalAndLegacyEvidenceIsNeverInvented() {
        var job=QuestionGradingJob.pending("question:exam:1:1","exam",1,1,"key","mock",NOW).markUserSubmission(NOW);
        when(jobs.findById(job.getJobId())).thenReturn(Optional.of(job));
        var replay=gate.prepare(session(false),1,1,true);
        assertNull(gate.authorize(session(false),1,1,replay,job));
        assertFalse(ReanswerAdmission.accepted(session(false),job));
        gate.prepare(session(false),1,1,false);
        verify(client,times(1)).check(USER);
    }
}
