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
        return session!=null && !session.isAbandoned() && !session.isRetakeAvailable() && !session.isEntitlementConfirming();
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
        if (job!=null && job.isActivityUserSubmission()) return true;
        return evidence.acceptedQuestion(session,question,canonical);
    }

    public static boolean summaryAllowed(ExamSession session) {
        if (!processable(session)) return false;
        if (!requiresEvidence(session)) return true;
        if (!Integer.valueOf(1).equals(session.getSubmissionEvidenceVersion()) || session.getSubmissionCompletedAt()==null
                || session.getRequiredQuestionNumbers()==null || session.getRequiredQuestionNumbers().isEmpty()) return false;
        if (session.getRequiredQuestionNumbers().stream().anyMatch(number -> number==null || number<1)) return false;
        return session.getSubmissionsClosedAt()==null || !session.getSubmissionCompletedAt().isAfter(session.getSubmissionsClosedAt());
    }
}
