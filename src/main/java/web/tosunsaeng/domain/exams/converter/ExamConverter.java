package web.tosunsaeng.domain.exams.converter;

import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;

import java.util.List;

public class ExamConverter {

    // 1. 최종 세션 생성 응답 DTO 변환
    public static ExamResponseDTO.CreateSessionResult toCreateSessionResult(String examId, List<ExamResponseDTO.QuestionDTO> questions) {
        return ExamResponseDTO.CreateSessionResult.builder()
                .examId(examId)
                .questions(questions)
                .build();
    }

    // 2. 문제 정보 DTO 변환 (현재는 PoC용 데이터를 받지만, 추후 매개변수를 Question Entity로 변경)
    public static ExamResponseDTO.QuestionDTO toQuestionDTO(Integer part, String questionId, String text, Integer prepTimeSec, Integer speakTimeSec) {
        return ExamResponseDTO.QuestionDTO.builder()
                .part(part)
                .questionId(questionId)
                .text(text)
                .prepTimeSec(prepTimeSec)
                .speakTimeSec(speakTimeSec)
                .build();
    }

    public static ExamResponseDTO.UploadUrlResult toUploadUrlResult(String uploadUrl, String fileKey, Integer expiresIn) {
        return ExamResponseDTO.UploadUrlResult.builder()
                .uploadUrl(uploadUrl)
                .fileKey(fileKey)
                .expiresIn(expiresIn)
                .build();
    }

    public static ExamResponseDTO.SubmitResult toSubmitResult(String status) {
        return ExamResponseDTO.SubmitResult.builder()
                .status(status)
                .build();
    }

    public static ExamResponseDTO.StatusResult toStatusResult(String examId, String status, Integer progress) {
        return ExamResponseDTO.StatusResult.builder()
                .examId(examId)
                .overallStatus(status)
                .progressPercent(progress)
                .build();
    }

    public static ExamResponseDTO.ScoreResult toScoreResult(String examId, String estimatedScore,
                                                            ExamResponseDTO.MetricsDTO metrics,
                                                            List<ExamResponseDTO.PartResultDTO> partResults) {
        return ExamResponseDTO.ScoreResult.builder()
                .examId(examId)
                .estimatedScore(estimatedScore)
                .metrics(metrics)
                .partResults(partResults)
                .build();
    }
}