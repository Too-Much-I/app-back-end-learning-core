package web.tosunsaeng.domain.exams.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

public class ExamResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateSessionResult {
        private String examId;
        private List<QuestionDTO> questions;
    }

    @Builder
    @Getter
    @Setter // Service에서 audioUrl 할당을 위해 추가
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionDTO {
        private String part;
        private String questionId;
        private String text;
        private Integer prepTimeSec;
        private Integer speakTimeSec;
        private String audioUrl; // 프론트엔드 문제 음성(TTS) 재생용 URL
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
        private String status;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusResult {
        private String examId;
        private String overallStatus; // PENDING, PROCESSING, COMPLETED
        private Integer progressPercent;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreResult {
        private String examId;
        private Integer totalScore; // 명칭 통일
        private MetricsDTO metrics;
        private List<PartResultDTO> partResults;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricsDTO {
        private String pronunciation;
        private String fluency;
        private String grammar;
        private String vocabulary;
        private String topicRelevance;
    }

    @Builder
    @Getter
    @Setter // Service에서 audioUrl 할당을 위해 추가
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartResultDTO {
        private String part;
        private String questionId;
        private String audioUrl; // 프론트엔드 사용자 녹음 파일 재생용 URL
        private String sttText;
        private String deductionReason;
        private String etsRubric;
        private String feedback;
    }
}