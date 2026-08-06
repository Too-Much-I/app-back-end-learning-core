package web.tosunsaeng.domain.exams.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;

import java.time.LocalDateTime;

@Document(collection = "exam_sessions")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSession {

    @Id
    private String examId;

    private String userId;

    private LocalDateTime createdAt;

    private String mockExamId;

    private Integer cycleNumber;

    private Boolean active;

    private ExamSessionStatus status;

    private LocalDateTime completedAt;

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    public ExamSessionStatus effectiveStatus() {
        if (status != null) {
            return status;
        }
        if (completedAt != null) {
            return ExamSessionStatus.COMPLETED;
        }
        if (Boolean.FALSE.equals(active)) {
            return ExamSessionStatus.ABANDONED;
        }
        return ExamSessionStatus.IN_PROGRESS;
    }

    public boolean isInProgress() {
        return effectiveStatus() == ExamSessionStatus.IN_PROGRESS;
    }

    public boolean isCompleted() {
        return effectiveStatus() == ExamSessionStatus.COMPLETED;
    }

    public boolean isAbandoned() {
        return effectiveStatus() == ExamSessionStatus.ABANDONED;
    }
}
