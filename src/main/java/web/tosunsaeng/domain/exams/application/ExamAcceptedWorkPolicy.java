package web.tosunsaeng.domain.exams.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.repository.QuestionGradingJobRepository;

/** Admission evidence is not an owner/deletion bypass. Call inside the existing owner transaction. */
@Service
@RequiredArgsConstructor
public class ExamAcceptedWorkPolicy {
    private final QuestionGradingJobRepository jobs;
    private final ExamSubmissionEvidence evidence;

    public static boolean processable(ExamSession session) {
        return session!=null && (!session.isAccessRevoked() || session.permitsRevokedAcceptedWork())
                && !session.isAbandoned() && !session.isRetakeAvailable() && !session.isEntitlementConfirming();
    }

    public static boolean requiresEvidence(ExamSession session) {
        return session!=null && (session.getSubmissionsClosedAt()!=null || session.getSubmissionEvidenceVersion()!=null);
    }

    public boolean questionAllowed(ExamSession session,Integer question,Integer retry) {
        if (!processable(session) || question==null) return false;
        if (!requiresEvidence(session)) return true; // Preserve the existing legacy callback/recovery contract.
        if (session.getSubmissionEvidenceVersion()!=null && !Integer.valueOf(1).equals(session.getSubmissionEvidenceVersion())) return false;
        int canonical=GradingKeys.canonicalRetryCount(retry);
        var job=jobs.findById(GradingKeys.questionJobId(session.getExamId(),question,canonical)).orElse(null);
        if (session.hasLegacyClosure() && !session.isAccessRevoked()) {
            if (job!=null && java.util.Objects.equals(job.getJobId(),GradingKeys.questionJobId(session.getExamId(),question,canonical))
                    && java.util.Objects.equals(GradingKeys.effectiveMockExamId(job.getMockExamId()),GradingKeys.effectiveMockExamId(session.getMockExamId()))
                    && java.util.Objects.equals(job.getExamId(),session.getExamId())
                    && java.util.Objects.equals(job.getQuestionNumber(),question)
                    && java.util.Objects.equals(job.getRetryCount(),canonical)
                    && job.isActivityUserSubmission() && job.getFirstUserSubmittedAt()!=null
                    && !job.getFirstUserSubmittedAt().isAfter(session.getSubmissionsClosedAt())) return true;
            return evidence.legacyAcceptedQuestion(session,question,canonical);
        }
        if (session.isAccessRevoked()) {
            if (!Integer.valueOf(1).equals(session.getSubmissionEvidenceVersion())
                    || session.getRequiredQuestionNumbers()==null || !session.getRequiredQuestionNumbers().contains(question)
                    || session.getRequiredQuestionNumbers().stream().anyMatch(n->n==null || n<1)) return false;
            if (job!=null) {
                if (!java.util.Objects.equals(job.getJobId(),GradingKeys.questionJobId(session.getExamId(),question,canonical))
                        || !java.util.Objects.equals(job.getExamId(),session.getExamId())
                        || !java.util.Objects.equals(job.getMockExamId(),session.getMockExamId())
                        || !java.util.Objects.equals(job.getQuestionNumber(),question)
                        || !java.util.Objects.equals(job.getRetryCount(),canonical)) return false;
                if (job.isActivityUserSubmission() && job.getFirstUserSubmittedAt()!=null)
                    return !job.getFirstUserSubmittedAt().isAfter(session.getSubmissionsClosedAt());
            }
            return evidence.acceptedQuestion(session,question,canonical);
        }
        if (job!=null && job.isActivityUserSubmission()) return true;
        return evidence.acceptedQuestion(session,question,canonical);
    }

    public static boolean summaryAllowed(ExamSession session) {
        if (!processable(session)) return false;
        if (!requiresEvidence(session)) return true;
        if (session.hasLegacyClosure() && !session.isAccessRevoked())
            return session.getSubmissionCompletedAt()!=null && !session.getSubmissionCompletedAt().isAfter(session.getSubmissionsClosedAt());
        if (!Integer.valueOf(1).equals(session.getSubmissionEvidenceVersion()) || session.getSubmissionCompletedAt()==null
                || session.getRequiredQuestionNumbers()==null || session.getRequiredQuestionNumbers().isEmpty()) return false;
        if (session.getRequiredQuestionNumbers().stream().anyMatch(number -> number==null || number<1)) return false;
        return session.getSubmissionsClosedAt()==null || !session.getSubmissionCompletedAt().isAfter(session.getSubmissionsClosedAt());
    }
}
