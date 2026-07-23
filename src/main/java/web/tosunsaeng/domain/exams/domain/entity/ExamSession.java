package web.tosunsaeng.domain.exams.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
}
