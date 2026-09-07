package web.tosunsaeng.domain.challenge;

import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Internal persistence models; never serialize these as public responses. */
public final class ChallengeModels {
    private ChallengeModels() {}
    public enum State { CREATED, SUBMITTED, EXPIRED }
    public enum JobState { PENDING, DISPATCHING, RETRY_WAIT, WAITING_CALLBACK, COMPLETED, FAILED, TIMED_OUT }
    public record Question(int dayNumber, int questionNumber, String questionId, String korean,
                           String referenceAnswer, int difficulty) {}
    public record Feedback(String meaning, String grammar, String pronunciation) {}
    public record Result(String transcript, String verdict, String correctedAnswer, Feedback feedback) {}

    @Document("challenge_10s_attempts") @NoArgsConstructor
    public static class Attempt {
        @Id public String id;
        @Version public Long revision;
        public String userId;
        public String challengeDate;
        public int questionNumber;
        public Question question;
        public Instant createdAt;
        public Instant submissionDeadlineAt;
        public String uploadKey;
        public State state;
        public String gradingStatus = "not_requested";
        public Instant submittedAt;
        public Instant expiredAt;
        public Instant gradedAt;
        public int generation;
        public String audioDigest;
        public Result result;
        public boolean terminal() { return state != State.CREATED; }
        public boolean gradingTerminal() { return "completed".equals(gradingStatus) || "failed".equals(gradingStatus); }
        public static Attempt create(String id, String owner, String date, Question question, Instant now) {
            Attempt a = new Attempt();
            a.id = id; a.userId = owner; a.challengeDate = date; a.questionNumber = question.questionNumber();
            a.question = question; a.createdAt = now; a.submissionDeadlineAt = now.plusSeconds(3600);
            a.uploadKey = "temp/challenges/" + id + "/q_" + a.questionNumber + ".m4a";
            a.state = State.CREATED;
            return a;
        }
    }

    @Document("challenge_10s_grading_jobs") @NoArgsConstructor
    public static class Job {
        @Id public String id;
        @Version public Long revision;
        public String attemptId;
        public int generation;
        public JobState state;
        public Instant nextAttemptAt;
        public String leaseToken;
        public Instant leaseUntil;
        public int dispatchCount;
        public Instant firstDispatchAt;
        public Instant dispatchDeadlineAt;
        public Instant acceptedAt;
        public Instant callbackDeadlineAt;
        public String callbackDigest;
        public String failureCode;
        public static String id(String attemptId, int generation) { return "challenge:" + attemptId + ":grading:" + generation; }
        public static Job pending(Attempt a, Instant now) {
            Job j = new Job(); j.id = id(a.id, a.generation); j.attemptId = a.id;
            j.generation = a.generation; j.state = JobState.PENDING; j.nextAttemptAt = now;
            return j;
        }
    }

    @Document("challenge_10s_submit_receipts") @NoArgsConstructor
    public static class SubmitReceipt {
        @Id public String id;
        public String userId;
        public String idempotencyKey;
        public String attemptId;
        public int questionNumber;
        public ChallengeViews.Submission response;
    }

    @Document("challenge_10s_callback_receipts") @NoArgsConstructor
    public static class CallbackReceipt {
        @Id public String id;
        public String attemptId;
        public String jobId;
        public int generation;
        public String digest;
    }
}
