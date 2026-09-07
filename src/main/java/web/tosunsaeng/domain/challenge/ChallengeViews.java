package web.tosunsaeng.domain.challenge;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import static web.tosunsaeng.domain.challenge.ChallengeModels.*;

public final class ChallengeViews {
    private ChallengeViews() {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DateInfo(String challengeDate, Instant challengeDateExpiresAt, long expiresInSeconds) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Progress(String challengeDate, Instant challengeDateExpiresAt, long expiresInSeconds,
                           String dailyStatus, int totalQuestionCount, Integer nextQuestionNumber,
                           List<Integer> completedQuestionNumbers, List<QuestionStatus> questions) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record QuestionStatus(int questionNumber, String attemptStatus, String gradingStatus, boolean resultAvailable) {}
    public record QuestionView(String challengeDate, int questionNumber, int totalQuestionCount, String promptKo,
                               int difficulty, String attemptStatus, String gradingStatus) {}
    public record Start(String attemptId, String challengeDate, int questionNumber, String attemptStatus, Instant submissionDeadlineAt) {}
    public record Upload(String method, String url, Instant expiresAt, String contentType, long maxBytes) {}
    public record UploadResponse(String attemptId, Instant submissionDeadlineAt, Upload upload) {}
    public record Submission(String attemptId, String challengeDate, int questionNumber, int difficulty,
                             String attemptStatus, String gradingStatus, Instant acceptedAt,
                             String referenceAnswer, boolean feedbackAvailable) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AiResult(String referenceAnswer, String transcript, String verdict, String correctedAnswer, Feedback feedback) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Detail(int questionNumber, String promptKo, int difficulty, String attemptStatus,
                         String gradingStatus, Instant submittedAt, Instant gradedAt, String referenceAnswer, AiResult aiResult) {}
    public record Count(String challengeDate, int solvedQuestionCount) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Results(String challengeDate, int solvedQuestionCount, Detail question) {}
    public record Day(String challengeDate, boolean participated, int solvedQuestionCount) {}
    public record History(String yearMonth, List<Day> dates) {}
    public static Start start(Attempt a) { return new Start(a.id, a.challengeDate, a.questionNumber, "not_started", a.submissionDeadlineAt); }
    public static Submission submitted(Attempt a) {
        return new Submission(a.id, a.challengeDate, a.questionNumber, a.question.difficulty(), "submitted",
                "pending", a.submittedAt, a.question.referenceAnswer(), false);
    }
    public static Detail detail(Attempt a) {
        if (a == null || !a.terminal()) return null;
        Result r = a.result;
        AiResult ai = r == null ? null : new AiResult(a.question.referenceAnswer(), r.transcript(), r.verdict(), r.correctedAnswer(), r.feedback());
        return new Detail(a.questionNumber, a.question.korean(), a.question.difficulty(), "submitted",
                a.gradingStatus, a.submittedAt, a.gradedAt, a.question.referenceAnswer(), ai);
    }
}
