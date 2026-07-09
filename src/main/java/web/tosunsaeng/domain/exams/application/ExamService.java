package web.tosunsaeng.domain.exams.application;

import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;

import java.util.Map;

public interface ExamService {
    ExamResponseDTO.CreateSessionResult createExamSession();

    ExamResponseDTO.UploadUrlResult getPresignedUrl(String examId, Integer questionNumber);

    ExamResponseDTO.SubmitResult submitAudio(String examId, Integer questionNumber);

    ExamResponseDTO.StatusResult getExamStatus(String examId);

    void updateExamResult(ExamRequestDTO.AiResultReq req);

    ExamResponseDTO.QuestionResult getExamQuestion(String examId, Integer questionNumber);

    void saveSpeechAceResult(ExamRequestDTO.SpeechAceReq req);

    ExamResponseDTO.SummaryResult getExamSummary(String examId);

    void processAzureCallback(Map<String, Object> rawPayload);
}