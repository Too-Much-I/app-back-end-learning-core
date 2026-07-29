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

@Document(collection = "summary_grading_jobs")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryGradingJob {

    @Id
    private String jobId;
    private String examId;
    private int summaryVersion;
    private GradingJobStatus status;
    private int dispatchAttempt;
    private Instant pendingAt;
    private Instant processingStartedAt;
    private Instant lastDispatchedAt;
    private Instant completedAt;
    private Instant failedAt;
    private String failureReason;

    @Version
    private Long version;

    public static SummaryGradingJob pending(String jobId, String examId, Instant now) {
        return SummaryGradingJob.builder()
                .jobId(jobId)
                .examId(examId)
                .summaryVersion(1)
                .status(GradingJobStatus.PENDING)
                .dispatchAttempt(0)
                .pendingAt(now)
                .build();
    }

    public static SummaryGradingJob completed(String jobId, String examId, Instant now) {
        return SummaryGradingJob.builder()
                .jobId(jobId)
                .examId(examId)
                .summaryVersion(1)
                .status(GradingJobStatus.COMPLETED)
                .dispatchAttempt(0)
                .pendingAt(now)
                .completedAt(now)
                .build();
    }

    public void startProcessing(Instant now) {
        status = GradingJobStatus.PROCESSING;
        dispatchAttempt += 1;
        processingStartedAt = now;
        lastDispatchedAt = now;
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
