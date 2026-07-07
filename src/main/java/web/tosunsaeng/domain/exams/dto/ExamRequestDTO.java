package web.tosunsaeng.domain.exams.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

public class ExamRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class SubmitAudioReq {
        private String fileKey;
    }

    @Getter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AiResultReq {
        @JsonProperty("user_id") private String examId;
        @JsonProperty("mock_exam_id") private String mockExamId;

        // 요약 데이터
        @JsonProperty("suggested_total_score") private Integer totalScore;
        @JsonProperty("level_estimate") private String levelEstimate;
        private String summary;
        @JsonProperty("overall_feedback") private String overallFeedback;
        @JsonProperty("part_feedback") private Map<String, String> partFeedback;
        private List<String> strengths;
        private List<String> weaknesses;
        @JsonProperty("recommended_practice") private List<String> recommendedPractice;

        // 문항 데이터
        @JsonProperty("part_number") private Integer partNumber;
        @JsonProperty("question_number") private Integer questionNumber;
        private Double score;
        @JsonProperty("max_score") private Double maxScore;
        private String transcript;
        private ItemFeedbackDTO feedback;
    }

    // 💡 1. SpeechAce 결과를 통째로 받기 위한 전용 DTO 추가
    @Getter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpeechAceReq {
        @JsonProperty("user_id") private String examId;
        @JsonProperty("question_number") private Integer questionNumber;
        // SpeechAce API의 복잡한 JSON 결과를 파싱 에러 없이 그대로 Map으로 수신합니다.
        @JsonProperty("speechace_result") private Map<String, Object> speechAceData;
    }

    @Getter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ItemFeedbackDTO {
        private String summary;
        private String level;
        @JsonProperty("pronunciation_fluency_score") private Double pronunciationFluencyScore;
        @JsonProperty("content_relevance_score") private Double contentRelevanceScore;
        private List<String> strengths;
        private List<String> weaknesses;
        private String pronunciation;
        private String fluency;
        private String content;
        @JsonProperty("grammar_vocabulary") private String grammarVocabulary;
        @JsonProperty("action_items") private List<String> actionItems;
    }
}