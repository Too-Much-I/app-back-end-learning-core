package web.tosunsaeng.domain.exams.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class ExamRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class SubmitAudioReq {
        private String fileKey; // S3에 업로드된 파일의 경로 (예: temp/ex_123/q_001.wav)
    }

    @Getter
    @NoArgsConstructor
    public static class AiResultReq {
        private String examId;
        private int totalScore;
        private String feedback;

        // Metrics 정보 (상세 점수)
        private MetricsDTO metrics;

        // Part별 결과 리스트
        private List<PartResultDTO> partResults;
    }

    @Getter
    @NoArgsConstructor
    public static class MetricsDTO {
        private String pronunciation;
        private String fluency;
        private String grammar;
        private String vocabulary;
        private String topicRelevance;
    }

    @Getter
    @NoArgsConstructor
    public static class PartResultDTO {
        private String part;
        private String sttText;
        private String deductionReason;
        private String etsRubric;
        private String feedback;
    }
}