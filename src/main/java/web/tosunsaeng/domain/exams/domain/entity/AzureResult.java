package web.tosunsaeng.domain.exams.domain.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Document(collection = "azure_results")
public class AzureResult {

    @Id
    private String id;

    @Field("exam_id")
    private String examId;

    @Field("question_number")
    private Integer questionNumber;

    @Field("spoken_word_sequence")
    private List<SpokenWord> spokenWordSequence;

    @Field("repeated_word_events")
    private List<RepeatedWordEvent> repeatedWordEvents;

    @Field("error_counts")
    private ErrorCounts errorCounts;

    @Field("legend")
    private Legend legend;

    // --- 하위 문서(Sub-documents) 정의 ---

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SpokenWord {
        private Integer index;
        private String word;
        @Field("normalized_word")
        private String normalizedWord;
        @Field("error_type")
        private String errorType;
        @Field("accuracy_score")
        private Double accuracyScore;
        @Field("start_seconds")
        private Double startSeconds;
        @Field("duration_seconds")
        private Double durationSeconds;
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RepeatedWordEvent {
        private String word;
        @Field("normalized_word")
        private String normalizedWord;
        @Field("first_index")
        private Integer firstIndex;
        @Field("second_index")
        private Integer secondIndex;
        @Field("intervening_words")
        private List<String> interveningWords;
        @Field("first_accuracy_score")
        private Double firstAccuracyScore;
        @Field("second_accuracy_score")
        private Double secondAccuracyScore;
        @Field("first_error_type")
        private String firstErrorType;
        @Field("second_error_type")
        private String secondErrorType;
        @Field("start_seconds")
        private Double startSeconds;
        @Field("second_start_seconds")
        private Double secondStartSeconds;
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ErrorCounts {
        private Integer mispronunciation;
        private Integer omission;
        private Integer insertion;
        @Field("unnecessary_pause")
        private Integer unnecessaryPause;
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Legend {
        private String correct;
        private String mispronunciation;
        private String omission;
        private String insertion;
        @Field("unnecessary_pause")
        private String unnecessaryPause;
    }
}