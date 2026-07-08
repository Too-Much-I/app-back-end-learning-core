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

    @Getter @NoArgsConstructor
    public static class AzureCallbackDTO {
        private MetadataDTO metadata;

        @JsonProperty("azure_speech_result")
        private AzureSpeechResultDTO azureSpeechResult;
    }

    @Getter @NoArgsConstructor
    public static class MetadataDTO {
        @JsonProperty("user_id")
        private String userId; // examId 역할

        @JsonProperty("mock_exam_id")
        private String mockExamId;

        @JsonProperty("part_number")
        private Integer partNumber;

        @JsonProperty("question_number")
        private Integer questionNumber;
    }

    @Getter @NoArgsConstructor
    public static class AzureSpeechResultDTO {
        @JsonProperty("spoken_word_sequence")
        private List<SpokenWordDTO> spokenWordSequence;

        @JsonProperty("repeated_word_events")
        private List<RepeatedWordEventDTO> repeatedWordEvents;

        @JsonProperty("error_counts")
        private ErrorCountsDTO errorCounts;

        private LegendDTO legend;
    }

    @Getter
    @NoArgsConstructor
    public static class SpokenWordDTO {
        private Integer index;
        private String word;

        @JsonProperty("normalized_word")
        private String normalizedWord;

        @JsonProperty("error_type")
        private String errorType;

        @JsonProperty("accuracy_score")
        private Double accuracyScore;

        @JsonProperty("start_seconds")
        private Double startSeconds;

        @JsonProperty("duration_seconds")
        private Double durationSeconds;
    }

    @Getter
    @NoArgsConstructor
    public static class RepeatedWordEventDTO {
        private String word;

        @JsonProperty("normalized_word")
        private String normalizedWord;

        @JsonProperty("first_index")
        private Integer firstIndex;

        @JsonProperty("second_index")
        private Integer secondIndex;

        @JsonProperty("intervening_words")
        private List<String> interveningWords;

        @JsonProperty("first_accuracy_score")
        private Double firstAccuracyScore;

        @JsonProperty("second_accuracy_score")
        private Double secondAccuracyScore;

        @JsonProperty("first_error_type")
        private String firstErrorType;

        @JsonProperty("second_error_type")
        private String secondErrorType;

        @JsonProperty("start_seconds")
        private Double startSeconds;

        @JsonProperty("second_start_seconds")
        private Double secondStartSeconds;
    }

    @Getter
    @NoArgsConstructor
    public static class ErrorCountsDTO {
        private Integer mispronunciation;
        private Integer omission;
        private Integer insertion;

        @JsonProperty("unnecessary_pause")
        private Integer unnecessaryPause;
    }

    @Getter
    @NoArgsConstructor
    public static class LegendDTO {
        private String correct;
        private String mispronunciation;
        private String omission;
        private String insertion;

        @JsonProperty("unnecessary_pause")
        private String unnecessaryPause;
    }
}