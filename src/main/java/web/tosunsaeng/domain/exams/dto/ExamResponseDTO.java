package web.tosunsaeng.domain.exams.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.domain.enums.ExamStatus;

import java.util.List;
import java.util.Map;

public class ExamResponseDTO {

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateSessionResult {
        private String examId;
        private String title;
        private List<QuestionDTO> questions;
    }

    @Builder
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuestionDTO {
        private Integer part;
        private Integer questionNumber;

        // 텍스트 기반 문제 (Part 1, 3, 5 등)
        private String text;
        private String referenceText;

        // 오디오 파일 (Part 1 등에서 문제 읽어줄 때 사용)
        private String audioUrl;

        // 이미지 문제 (Part 2)
        private String imageUrl;

        // 표 문제 (Part 4)
        private Question.TableContext tableContext;

        // 시간 정보
        private Integer prepTimeSec;
        private Integer speakTimeSec;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadUrlResult {
        private String uploadUrl;
        private String fileKey;
        private Integer expiresIn;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmitResult {
        private ExamStatus status; // (수정) String -> ExamStatus 통일
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusResult {
        private String examId;
        private ExamStatus overallStatus; // (수정) String -> ExamStatus 통일
        private Integer progressPercent;
    }

    @Builder @Getter @NoArgsConstructor @AllArgsConstructor
    public static class SummaryResult {
        private String examId;
        private Integer totalScore;
        private String levelEstimate;
        private String summary;
        private String overallFeedback;
        private Map<String, String> partFeedback;
        private List<String> strengths;
        private List<String> weaknesses;
        private List<String> recommendedPractice;
        private Map<String, Double> partScores;
    }

    // 💡 2-2. 개별 문항 리스트 전용 응답 DTO
    @Builder @Getter @NoArgsConstructor @AllArgsConstructor
    public static class QuestionResult {
        private String examId;
        private PartResultDTO question;
    }

    @Builder @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PartResultDTO {
        private Integer partNumber;
        private Integer questionNumber;
        private String audioUrl;
        private Double score;
        private Double maxScore;
        private String transcript;
        private ItemFeedbackDTO feedback;
        private AzureFeedbackDTO azureFeedback;
    }

    @Builder @Getter @NoArgsConstructor @AllArgsConstructor
    public static class ItemFeedbackDTO {
        private String summary;
        private String level;
        private Double pronunciationFluencyScore;
        private Double contentRelevanceScore;
        private List<String> strengths;
        private List<String> weaknesses;
        private String pronunciation;
        private String fluency;
        private String content;
        private String grammarVocabulary;
        private List<String> actionItems;
    }

    @Getter @Builder
    public static class AzureFeedbackDTO {
        @JsonProperty("spoken_word_sequence")
        private List<AzureSpokenWordDTO> spokenWordSequence;

        @JsonProperty("repeated_word_events")
        private List<AzureRepeatedWordEventDTO> repeatedWordEvents;

        @JsonProperty("error_counts")
        private AzureErrorCountsDTO errorCounts;

        private AzureLegendDTO legend;
    }

    @Getter @Builder
    public static class AzureSpokenWordDTO {
        private Integer index;
        private String word;
        @JsonProperty("normalized_word") private String normalizedWord;
        @JsonProperty("error_type") private String errorType;
        @JsonProperty("accuracy_score") private Double accuracyScore;
        @JsonProperty("start_seconds") private Double startSeconds;
        @JsonProperty("duration_seconds") private Double durationSeconds;
    }

    @Getter @Builder
    public static class AzureRepeatedWordEventDTO {
        private String word;
        @JsonProperty("normalized_word") private String normalizedWord;
        @JsonProperty("first_index") private Integer firstIndex;
        @JsonProperty("second_index") private Integer secondIndex;
        @JsonProperty("intervening_words") private List<String> interveningWords;
        @JsonProperty("first_accuracy_score") private Double firstAccuracyScore;
        @JsonProperty("second_accuracy_score") private Double secondAccuracyScore;
        @JsonProperty("first_error_type") private String firstErrorType;
        @JsonProperty("second_error_type") private String secondErrorType;
        @JsonProperty("start_seconds") private Double startSeconds;
        @JsonProperty("second_start_seconds") private Double secondStartSeconds;
    }

    @Getter @Builder
    public static class AzureErrorCountsDTO {
        private Integer mispronunciation;
        private Integer omission;
        private Integer insertion;
        @JsonProperty("unnecessary_pause") private Integer unnecessaryPause;
    }

    @Getter @Builder
    public static class AzureLegendDTO {
        private String correct;
        private String mispronunciation;
        private String omission;
        private String insertion;
        @JsonProperty("unnecessary_pause") private String unnecessaryPause;
    }
}