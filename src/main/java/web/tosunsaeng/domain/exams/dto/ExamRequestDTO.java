package web.tosunsaeng.domain.exams.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

public class ExamRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class SubmitAudioReq {
        private String fileKey; // S3에 업로드된 파일의 경로 (예: temp/ex_123/q_001.wav)
    }
}