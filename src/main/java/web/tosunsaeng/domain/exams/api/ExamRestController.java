package web.tosunsaeng.domain.exams.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import web.tosunsaeng.domain.exams.application.ExamReadService;
import web.tosunsaeng.domain.exams.application.ExamService;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.SuccessStatus;

import java.util.Map;

@Tag(name = "Exam API", description = "모의고사 세션 및 채점 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/exams")
public class ExamRestController {

    private final ExamService examService;
    private final ExamReadService examReadService;

    @Operation(summary = "모의고사 세션 생성 API", description = "체험 시작 시 새로운 세션을 발급하고 문제를 반환합니다.")
    @PostMapping("")
    public BaseResponse<ExamResponseDTO.CreateSessionResult> createSession() {
        return BaseResponse.onSuccess(SuccessStatus.OK, examService.createExamSession());
    }

    @Operation(
            summary = "현재 사용자의 완료 모의고사 이력 조회 API",
            description = "Bearer 인증 사용자의 completedAt이 기록된 완료 시험만 최신순으로 반환합니다. "
                    + "시작·완료 시각, 상태, 만점과 시험별 재답변 문항 수를 함께 제공하고, "
                    + "종합 결과가 없는 완료 시험도 포함하며, "
                    + "이력이 없으면 200과 빈 histories 배열을 반환합니다."
    )
    @GetMapping("/history")
    public BaseResponse<ExamResponseDTO.ExamHistoryResult> getExamHistory() {
        return BaseResponse.onSuccess(SuccessStatus.OK, examReadService.getExamHistory());
    }

    @Operation(
            summary = "시험의 재답변 문항 및 회차 조회 API",
            description = "Bearer 인증 사용자 소유 시험에서 retryCount 1 이상이 실제로 존재하는 문항만 반환하고, "
                    + "저장된 최초 회차도 비교를 위해 함께 제공합니다. 회차별 상태·점수·완료 시각을 제공하고, "
                    + "상세 피드백은 기존 문항 단건 API로 조회하며, "
                    + "재답변 문항이 없으면 200과 빈 questions 배열을 반환합니다."
    )
    @GetMapping("/{examId}/retries")
    public BaseResponse<ExamResponseDTO.ExamRetriesResult> getExamRetries(
            @PathVariable("examId") String examId) {
        return BaseResponse.onSuccess(SuccessStatus.OK, examReadService.getExamRetries(examId));
    }

    @Operation(
            summary = "시험 문항 문제 조회 API",
            description = "JWT 사용자 소유의 시험 세션에 배정된 문제지에서 특정 문항의 Part별 문제 정보를 조회합니다."
    )
    @GetMapping("/{examId}/questions/{questionNumber}/prompt")
    public BaseResponse<ExamResponseDTO.QuestionDTO> getQuestionPrompt(
            @PathVariable("examId") String examId,
            @PathVariable("questionNumber") Integer questionNumber
    ) {
        return BaseResponse.onSuccess(
                SuccessStatus.OK,
                examService.getQuestionPrompt(examId, questionNumber)
        );
    }

    @Operation(summary = "S3 Presigned URL 발급 API", description = "녹음된 오디오를 S3에 직접 업로드하기 위한 회차별(retryCount) 고유 주소를 발급합니다.")
    @GetMapping("/{examId}/questions/{questionNumber}/upload-url")
    public BaseResponse<ExamResponseDTO.UploadUrlResult> getUploadUrl(
            @PathVariable("examId") String examId,
            @PathVariable("questionNumber") Integer questionNumber,
            @RequestParam(value = "retryCount", defaultValue = "0") Integer retryCount // 🌟 [수정] 회차 식별자 파라미터 추가
    ) {
        return BaseResponse.onSuccess(SuccessStatus.OK, examService.getPresignedUrl(examId, questionNumber, retryCount));
    }

    @Operation(summary = "업로드 완료 알림 및 채점 요청 API", description = "S3 우회용으로 실제 음성 파일을 전송하여 AI 채점을 시작합니다. 몇 번째 재시도인지(retryCount)를 함께 전달합니다.")
    @PostMapping(value = "/{examId}/questions/{questionNumber}/submit")
    public BaseResponse<ExamResponseDTO.SubmitResult> submitAudio(
            @PathVariable("examId") String examId,
            @PathVariable("questionNumber") Integer questionNumber,
            @RequestParam(value = "retryCount", defaultValue = "0") Integer retryCount // 🌟 [수정] 회차 식별자 파라미터 추가
    ) {
        return BaseResponse.onSuccess(SuccessStatus.OK, examService.submitAudio(examId, questionNumber, retryCount));
    }

    @Operation(summary = "시험 단위 재채점 API", description = "최초 응시 문항과 전체 요약 중 실패하거나 제한 시간을 초과한 채점 작업만 복구합니다.")
    @PostMapping("/{examId}/grading/retry")
    public BaseResponse<ExamResponseDTO.GradingRetryResult> retryGrading(
            @PathVariable("examId") String examId) {
        return BaseResponse.onSuccess(SuccessStatus.OK, examService.retryGrading(examId));
    }

    @Operation(summary = "채점 진행 상태 조회 API", description = "비동기 채점이 완료되었는지 진행 상태를 폴링(Polling)합니다.")
    @GetMapping("/{examId}/status")
    public BaseResponse<ExamResponseDTO.StatusResult> getExamStatus(
            @PathVariable("examId") String examId) {
        return BaseResponse.onSuccess(SuccessStatus.OK, examService.getExamStatus(examId));
    }

    @Operation(summary = "[프론트엔드] 전체 요약 피드백 조회 API", description = "모의고사의 총점 및 요약 피드백만 빠르게 가져옵니다.")
    @GetMapping("/{examId}/summary")
    public BaseResponse<ExamResponseDTO.SummaryResult> getExamSummary(@PathVariable("examId") String examId) {
        return BaseResponse.onSuccess(SuccessStatus.OK, examService.getExamSummary(examId));
    }

    @Operation(
            summary = "문항별 채점 피드백 정밀 단건 조회 API",
            description = "특정 문항의 회차별(retryCount) 피드백과 공통 문제 정보를 조회하며, "
                    + "Part 1의 Question 1·2에는 모범답안 음성 정보를 제공합니다."
    )
    @GetMapping("/{examId}/questions")
    public BaseResponse<ExamResponseDTO.QuestionResult> getExamQuestion(
            @PathVariable String examId,
            @RequestParam Integer questionNumber,
            @RequestParam(defaultValue = "0") Integer retryCount
    ) {
        ExamResponseDTO.QuestionResult result = examService.getExamQuestion(examId, questionNumber, retryCount);
        return BaseResponse.onSuccess(SuccessStatus.OK, result);
    }

    @Operation(summary = "[AI 서버용] 채점 피드백 콜백 API", description = "AI가 분석한 결과를 부분적으로 저장합니다.")
    @PostMapping("/callback/feedback")
    public BaseResponse<Void> receiveAiResult(@RequestBody ExamRequestDTO.AiResultReq req) {
        examService.updateExamResult(req);
        return BaseResponse.onSuccess(SuccessStatus.OK, null);
    }

    @Operation(summary = "[AI 서버용] SpeechAce 결과 콜백 API", description = "AI가 호출한 스피치에이스 JSON을 저장합니다.")
    @PostMapping("/callback/speechace")
    public BaseResponse<Void> receiveSpeechAceResult(@RequestBody ExamRequestDTO.SpeechAceReq req) {
        examService.saveSpeechAceResult(req);
        return BaseResponse.onSuccess(SuccessStatus.OK, null);
    }

    @Operation(summary = "[AI 서버용] azure 결과 콜백 API", description = "AI가 호출한 azure 원본 JSON을 통째로 저장합니다.")
    @PostMapping("/callback/azure")
    public BaseResponse<String> azureCallback(@RequestBody Map<String, Object> rawPayload) {
        examService.processAzureCallback(rawPayload);
        return BaseResponse.onSuccess(SuccessStatus.OK, "Azure 콜백 데이터 원본이 성공적으로 저장되었습니다.");
    }

    @Operation(summary = "문항별 재시도 채점 진행 상태 조회 (폴링) API", description = "특정 문항의 콕 집은 회차(retryCount) 채점이 완료되었는지 폴링합니다.")
    @GetMapping("/{examId}/questions/status")
    public BaseResponse<ExamResponseDTO.QuestionPollResult> getQuestionStatus(
            @PathVariable("examId") String examId,
            @RequestParam("questionNumber") Integer questionNumber,
            @RequestParam(value = "retryCount", defaultValue = "0") Integer retryCount
    ) {
        ExamResponseDTO.QuestionPollResult result = examService.getQuestionProcessingStatus(examId, questionNumber, retryCount);
        return BaseResponse.onSuccess(SuccessStatus.OK, result);
    }
}
