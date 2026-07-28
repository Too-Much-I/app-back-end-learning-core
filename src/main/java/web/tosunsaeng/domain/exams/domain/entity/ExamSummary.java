package web.tosunsaeng.domain.exams.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Document(collection = "exam_summaries")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSummary {
    @Id
    private String id;
    private String examId;
    private String userId;
    private String mockExamId;
    private Integer totalScore;
    private String levelEstimate;
    private String summary;
    private String overallFeedback;
    private Map<String, String> partFeedback;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> recommendedPractice;
}
