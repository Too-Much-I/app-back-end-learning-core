package web.tosunsaeng.domain.exams.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Document(collection = "exam_results")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamResult {
    @Id
    private String id;
    private String examId;
    private String mockExamId;
    private Integer retryCount;

    // 요약 데이터
    private Integer totalScore;
    private String levelEstimate;
    private String summary;
    private String overallFeedback;
    private Map<String, String> partFeedback;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> recommendedPractice;

    // 문항 데이터
    private Integer partNumber;
    private Integer questionNumber;
    private Double score;
    private Double maxScore;
    private String transcript;
    private ItemFeedback feedback;

    // 새로 추가된 시퀀스 데이터
    private List<SpokenWord> spokenWordSequence;

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ItemFeedback {
        private String summary;
        private String level;
        private Double pronunciationFluencyScore;
        private Double contentRelevanceScore;
        private Double fluencyScore;
        private Double completenessScore;
        private Double prosodyScore;
        private Double accuracyScore;
        private List<String> strengths;
        private List<String> weaknesses;
        private String pronunciation;
        private String fluency;
        private String content;
        private String grammarVocabulary;
        private List<String> actionItems;

        // 🌟 새로 추가된 피드백 필드들
        private List<CorrectionItem> correctionItems;
        private List<String> offTopicItems;
        private String correctedAnswer;
        private String recommendedAnswer;
        private String nextStrategy;
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CorrectionItem {
        private String type;
        private String original;
        private String issue;
        private String explanation;
        private String suggested;
        private String severity;
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SpokenWord {
        private Integer index;
        private Integer segmentIndex;
        private Integer wordIndex;
        private String word;
        private Long offset;
        private Long duration;
        private Double accuracyScore;
        private Double pronunciationScore;
        private String errorType;
    }
}