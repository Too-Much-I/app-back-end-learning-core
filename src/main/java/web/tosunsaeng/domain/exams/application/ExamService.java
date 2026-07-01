package web.tosunsaeng.domain.exams.application;

import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;

public interface ExamService {
    ExamResponseDTO.CreateSessionResult createExamSession();

    ExamResponseDTO.UploadUrlResult getPresignedUrl(String examId, String questionId);

    ExamResponseDTO.SubmitResult submitAudio(String examId, String questionId, ExamRequestDTO.SubmitAudioReq req);

    ExamResponseDTO.StatusResult getExamStatus(String examId);

    ExamResponseDTO.ScoreResult getExamResults(String examId);
}