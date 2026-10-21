package web.tosunsaeng.domain.exams.reanswer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.exams.application.*;
import web.tosunsaeng.domain.exams.domain.entity.*;
import web.tosunsaeng.domain.exams.domain.repository.QuestionGradingJobRepository;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;
import java.time.*;
import java.util.Objects;
import java.util.function.LongSupplier;

@Service
public class ReanswerAdmission {
    private final boolean enabled;
    private final ObjectProvider<ReanswerEligibilityClient> clients;
    private final QuestionGradingJobRepository jobs;
    private final MockExamCatalogService catalog;
    private final Clock clock;
    private final LongSupplier nanoTime;

    @org.springframework.beans.factory.annotation.Autowired
    public ReanswerAdmission(@Value("${app.billing.reanswer.enabled:false}") boolean enabled,
            ObjectProvider<ReanswerEligibilityClient> clients, QuestionGradingJobRepository jobs,
            MockExamCatalogService catalog, Clock clock) {
        this(enabled,clients,jobs,catalog,clock,System::nanoTime);
    }
    ReanswerAdmission(boolean enabled,ObjectProvider<ReanswerEligibilityClient> clients,
            QuestionGradingJobRepository jobs,MockExamCatalogService catalog,Clock clock,LongSupplier nanoTime) {
        this.enabled=enabled; this.clients=clients; this.jobs=jobs; this.catalog=catalog; this.clock=clock; this.nanoTime=nanoTime;
    }
    public boolean applies(ExamSession session,Integer retry) {
        return enabled && session.isCompleted() && GradingKeys.canonicalRetryCount(retry)>0;
    }
    /** Call outside Mongo transactions, after public ownership/deletion checks. */
    public Observation prepare(ExamSession session,Integer question,Integer retry,boolean replayAllowed) {
        if (!applies(session,retry)) return null;
        requireSession(session);
        if (question==null || catalog.getRequiredExam(GradingKeys.effectiveMockExamId(session.getMockExamId()))
                .getQuestions().stream().noneMatch(q -> q!=null && question.equals(q.getQuestionNumber())))
            throw new ExamsException(ErrorStatus._QUESTION_NOT_FOUND);
        String jobId=GradingKeys.questionJobId(session.getExamId(),question,retry);
        if (replayAllowed && jobs.findById(jobId).isPresent())
            return new Observation(session.getUserId(),jobId,null,0,null);
        long started=nanoTime.getAsLong();
        var proof=clients.getObject().check(session.getUserId());
        var received=clock.instant();
        var observation=new Observation(session.getUserId(),jobId,proof,started,received);
        checkFresh(observation,received);
        return observation;
    }
    /** Existing evidence never authorizes another user or a revoked/deleted Session. */
    public ReanswerApproval authorize(ExamSession session,Integer question,Integer retry,Observation observation,
            QuestionGradingJob existing) {
        if (observation!=null && !applies(session,retry))
            throw new ExamsException(ErrorStatus._EXAM_ABANDONED);
        if (!applies(session,retry)) return null;
        requireSession(session);
        if (observation==null || !session.getUserId().equals(observation.userId())
                || !GradingKeys.questionJobId(session.getExamId(),question,retry).equals(observation.jobId()))
            throw ReanswerFailures.unavailable();
        if (existing!=null) {
            if (existing.getReanswerApproval()!=null && !accepted(session,existing)) throw ReanswerFailures.contract();
            // Legacy evidence is not upgraded; the caller still enforces its existing closure rule.
            return null;
        }
        checkFresh(observation,clock.instant());
        return observation.proof();
    }
    private void checkFresh(Observation observation,Instant now) {
        var proof=observation.proof();
        if (proof==null || !proof.valid() || !proof.userId().equals(observation.userId())) throw ReanswerFailures.contract();
        long elapsed=nanoTime.getAsLong()-observation.started();
        if (elapsed<0 || Instant.parse(proof.checkedAt()).isAfter(observation.received())
                || now.isBefore(observation.received())) throw ReanswerFailures.contract("clock",0);
        if (elapsed>=5_000_000_000L || !now.isBefore(Instant.parse(proof.validUntil())))
            throw ReanswerFailures.unavailable();
    }
    private static void requireSession(ExamSession session) {
        if (session.isAccessRevoked()) throw new ExamsException(ErrorStatus._ENTITLEMENT_REVOKED);
        if (!session.isCompleted()) throw new ExamsException(ErrorStatus._EXAM_ABANDONED);
    }
    public static boolean accepted(ExamSession session,QuestionGradingJob job) {
        if (session==null || job==null || !session.isCompleted() || session.isAccessRevoked()
                || job.getReanswerApproval()==null || !job.getReanswerApproval().valid()
                || !job.isActivityUserSubmission() || job.getFirstUserSubmittedAt()==null
                || job.getQuestionNumber()==null || job.getQuestionNumber()<1
                || GradingKeys.canonicalRetryCount(job.getRetryCount())<=0) return false;
        return Objects.equals(job.getExamId(),session.getExamId())
                && Objects.equals(job.getJobId(),GradingKeys.questionJobId(session.getExamId(),job.getQuestionNumber(),job.getRetryCount()))
                && Objects.equals(GradingKeys.effectiveMockExamId(job.getMockExamId()),GradingKeys.effectiveMockExamId(session.getMockExamId()));
        // Snapshot userId stays immutable after a legitimate owner migration. Current owner guards remain mandatory.
    }
    public record Observation(String userId,String jobId,ReanswerApproval proof,long started,Instant received) {}
}
