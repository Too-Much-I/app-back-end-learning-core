package web.tosunsaeng.domain.exams.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import web.tosunsaeng.domain.exams.domain.enums.GradingJobStatus;

import java.time.Instant;

@Document(collection = "question_grading_jobs")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionGradingJob {

    @Id
    private String jobId;
    private String examId;
    private Integer questionNumber;
    private Integer retryCount;
    private String fileKey;
    private String mockExamId;
    private GradingJobStatus status;
    private int dispatchAttempt;
    private Integer recoveryCycle;
    private Instant pendingAt;
    private Instant processingStartedAt;
    private Instant lastDispatchedAt;
    private Instant completedAt;
    private Instant failedAt;
    private String failureReason;

    @Version
    private Long version;

    public static QuestionGradingJob pending(
            String jobId,
            String examId,
            Integer questionNumber,
            Integer retryCount,
            String fileKey,
            Instant now) {
        return pending(jobId, examId, questionNumber, retryCount, fileKey, null, now);
    }

    public static QuestionGradingJob pending(
            String jobId,
            String examId,
            Integer questionNumber,
            Integer retryCount,
            String fileKey,
            String mockExamId,
            Instant now) {
        return QuestionGradingJob.builder()
                .jobId(jobId)
                .examId(examId)
                .questionNumber(questionNumber)
                .retryCount(retryCount)
                .fileKey(fileKey)
                .mockExamId(mockExamId)
                .status(GradingJobStatus.PENDING)
                .dispatchAttempt(0)
                .recoveryCycle(0)
                .pendingAt(now)
                .build();
    }

    public static QuestionGradingJob completed(
            String jobId,
            String examId,
            Integer questionNumber,
            Integer retryCount,
            String fileKey,
            Instant now) {
        return completed(jobId, examId, questionNumber, retryCount, fileKey, null, now);
    }

    public static QuestionGradingJob completed(
            String jobId,
            String examId,
            Integer questionNumber,
            Integer retryCount,
            String fileKey,
            String mockExamId,
            Instant now) {
        return QuestionGradingJob.builder()
                .jobId(jobId)
                .examId(examId)
                .questionNumber(questionNumber)
                .retryCount(retryCount)
                .fileKey(fileKey)
                .mockExamId(mockExamId)
                .status(GradingJobStatus.COMPLETED)
                .dispatchAttempt(0)
                .recoveryCycle(0)
                .pendingAt(now)
                .completedAt(now)
                .build();
    }

    public void startProcessing(Instant now) {
        recoveryCycle = effectiveRecoveryCycle();
        status = GradingJobStatus.PROCESSING;
        dispatchAttempt += 1;
        processingStartedAt = now;
        lastDispatchedAt = now;
        completedAt = null;
        failedAt = null;
        failureReason = null;
    }

    public int effectiveRecoveryCycle() {
        return recoveryCycle == null || recoveryCycle < 0 ? 0 : recoveryCycle;
    }

    public void reopenMissingResult(Instant now) {
        if (status != GradingJobStatus.COMPLETED) {
            return;
        }
        recoveryCycle = effectiveRecoveryCycle() + 1;
        status = GradingJobStatus.PENDING;
        dispatchAttempt = 0;
        pendingAt = now;
        processingStartedAt = null;
        lastDispatchedAt = null;
        completedAt = null;
        failedAt = null;
        failureReason = null;
    }

    public void complete(Instant now) {
        status = GradingJobStatus.COMPLETED;
        completedAt = now;
        failedAt = null;
        failureReason = null;
    }

    public void fail(Instant now, String reason) {
        if (status == GradingJobStatus.COMPLETED) {
            return;
        }
        status = GradingJobStatus.FAILED;
        failedAt = now;
        failureReason = reason;
    }
}
