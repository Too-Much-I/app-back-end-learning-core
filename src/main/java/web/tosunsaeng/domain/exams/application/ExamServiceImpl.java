package web.tosunsaeng.domain.exams.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.tosunsaeng.domain.exams.converter.ExamConverter;
import web.tosunsaeng.domain.exams.domain.entity.*;
import web.tosunsaeng.domain.exams.domain.enums.ExamStatus;
import web.tosunsaeng.domain.exams.domain.repository.AzureResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;
import web.tosunsaeng.domain.exams.domain.repository.MockExamRepository;
import web.tosunsaeng.domain.exams.domain.repository.SpeechAceResultRepository;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.auth.CurrentUserProvider;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final software.amazon.awssdk.services.s3.presigner.S3Presigner s3Presigner;
    private final ExamGradingService gradingService;

    private final ExamResultRepository examResultRepository;
    private final ExamSummaryRepository examSummaryRepository;
    private final ExamSessionRepository examSessionRepository;
    private final MockExamRepository mockExamRepository;
    private final SpeechAceResultRepository speechAceResultRepository;
    private final AzureResultRepository azureResultRepository;
    private final CurrentUserProvider currentUserProvider;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    // --- 1. 유틸리티 메서드: S3 URL 생성 및 변환 ---

    // S3 객체 다운로드용 임시 Presigned URL을 발행합니다.
    private String generatePresignedGetUrl(String fileKey, int expirationMinutes) {
        software.amazon.awssdk.services.s3.model.GetObjectRequest getObjectRequest =
                software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileKey)
                        .build();

        software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest presignRequest =
                software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(expirationMinutes))
                        .getObjectRequest(getObjectRequest)
                        .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    // 문제 음성 파일의 S3 다운로드 주소를 획득합니다.
    private String getQuestionAudioUrl(String examPaperId, Integer questionNumber) {
        String fileKey = String.format("questions/%s/q_%d.wav", examPaperId, questionNumber);
        return generatePresignedGetUrl(fileKey, 60);
    }

    // 파트 3용 인트로 안내 음성 주소를 획득합니다.
    private String getQuestionGuideAudioUrl(String examPaperId) {
        String fileKey = String.format("questions/%s/part3_intro.wav", examPaperId);
        return generatePresignedGetUrl(fileKey, 60);
    }

    // 사용자가 제출한 오디오 파일 복원을 위한 임시 S3 Presigned URL을 획득합니다.
    private String getDownloadUrl(String examId, Integer questionNumber, Integer retryCount) {
        String fileKey = String.format("temp/%s/q_%d_r%d.wav", examId, questionNumber, retryCount);
        return generatePresignedGetUrl(fileKey, 5);
    }

    private ExamSession resolveSession(String examId) {
        return examSessionRepository.findById(examId)
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_NOT_FOUND));
    }

    private ExamSession requireOwnedSession(String examId) {
        ExamSession examSession = resolveSession(examId);
        String currentUserId = currentUserProvider.getCurrentUserId();

        if (!Objects.equals(examSession.getUserId(), currentUserId)) {
            throw new ExamsException(ErrorStatus._FORBIDDEN);
        }

        return examSession;
    }

    // --- 2. 유틸리티 메서드: 토익스피킹 파트 판별 ---

    // 문항 번호를 토대로 토익스피킹 파트(Part) 번호를 계산합니다.
    private Integer getPartNumber(Integer questionNumber) {
        if (questionNumber == null) return 1;
        if (questionNumber == 0) return 0;
        if (questionNumber >= 1 && questionNumber <= 2) return 1;
        if (questionNumber >= 3 && questionNumber <= 4) return 2;
        if (questionNumber >= 5 && questionNumber <= 7) return 3;
        if (questionNumber >= 8 && questionNumber <= 10) return 4;
        return 5;
    }

    // --- 3. 핵심 비즈니스 로직 구현체 ---

    // 새로운 정규 모의고사 세션을 생성하고 초기 시험 지문 및 S3 오디오 스트리밍 주소를 조립합니다.
    @Override
    public ExamResponseDTO.CreateSessionResult createExamSession() {
        String uuidPart = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String timePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd_HHmm"));
        String examId = "ex_" + uuidPart + "_" + timePart;
        String redisKey = "exam:status:" + examId;

        // Redis에 진행 상태를 대기(PENDING)로 등록하고 1시간 만료 시간을 부여합니다.
        redisTemplate.opsForValue().set(redisKey, ExamStatus.PENDING.name(), 1, TimeUnit.HOURS);
        log.info("정규 모의고사 세션 생성 완료: {}", examId);

        // 지정된 족보 데이터인 mock_exam_003 셋을 MongoDB에서 로드합니다.
        MockExam mockExam = mockExamRepository.findByMockExamId("mock_exam_003")
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_PAPER_NOT_FOUND));

        // 전체 문항 배열을 순회하며 문항별 다운로드용 오디오 URL 및 가이드 URL을 결합합니다.
        List<ExamResponseDTO.QuestionDTO> questionDTOs = mockExam.getQuestions().stream()
                .map(q -> {
                    ExamResponseDTO.QuestionDTO dto = ExamConverter.toQuestionDTO(q);
                    dto.setAudioUrl(getQuestionAudioUrl(mockExam.getMockExamId(), q.getQuestionNumber()));
                    if (q.getPartNumber() == 3) {
                        dto.setGuideAudioUrl(getQuestionGuideAudioUrl(mockExam.getMockExamId()));
                    }
                    return dto;
                })
                .collect(Collectors.toList());

        ExamResponseDTO.CreateSessionResult result =
                ExamConverter.toCreateSessionResult(examId, mockExam.getTitle(), questionDTOs);

        String userId = currentUserProvider.getCurrentUserId();
        ExamSession examSession = ExamSession.builder()
                .examId(examId)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .build();
        examSessionRepository.save(examSession);

        return result;
    }

    // 사용자가 가상으로 녹음 오디오 파일을 업로드할 수 있는 임시 S3 PutObject용 Presigned URL을 발급합니다.
    @Override
    public ExamResponseDTO.UploadUrlResult getPresignedUrl(String examId, Integer questionNumber, Integer retryCount) {
        requireOwnedSession(examId);

        String fileKey = String.format("temp/%s/q_%d_r%d.wav", examId, questionNumber, retryCount);

        software.amazon.awssdk.services.s3.model.PutObjectRequest objectRequest =
                software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileKey)
                        .build();

        software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest presignRequest =
                software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(5))
                        .putObjectRequest(objectRequest)
                        .build();

        software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest presignedRequest =
                s3Presigner.presignPutObject(presignRequest);

        String url = presignedRequest.url().toString();

        return ExamConverter.toUploadUrlResult(url, fileKey, 60);
    }

    // 기존 submit 계약은 유지하고 결정적 Job을 생성한 최초 요청만 AI 채점을 시작합니다.
    @Override
    public ExamResponseDTO.SubmitResult submitAudio(String examId, Integer questionNumber, Integer retryCount) {
        requireOwnedSession(examId);
        ExamStatus status = gradingService.submitQuestion(examId, questionNumber, retryCount);
        return ExamConverter.toSubmitResult(status);
    }

    @Override
    public ExamResponseDTO.GradingRetryResult retryGrading(String examId) {
        requireOwnedSession(examId);
        return gradingService.retryExam(examId);
    }

    // Job과 저장 결과를 기준으로 전체 상태를 산정하고 기존 Redis Key/TTL에 projection합니다.
    @Override
    public ExamResponseDTO.StatusResult getExamStatus(String examId) {
        requireOwnedSession(examId);
        ExamStatus currentStatus = gradingService.calculateAndCacheOverallStatus(examId);
        return ExamConverter.toStatusResult(examId, currentStatus, 60);
    }

    // AI 서버 연산 완료 후 백엔드 웹훅 콜백을 통해 인입된 분석 스코어와 텍스트 피드백 데이터를 처리합니다.
    @Override
    public void updateExamResult(ExamRequestDTO.AiResultReq req) {
        String examId = req.getExamId();
        ExamSession examSession = resolveSession(examId);

        // 종합 결과도 결정적 ID와 legacy 논리 결과 확인으로 멱등 저장합니다.
        if (req.getTotalScore() != null) {
            String resultId = GradingKeys.summaryJobId(examId);
            boolean alreadyStored = examSummaryRepository.existsById(resultId)
                    || examSummaryRepository.existsByExamId(examId)
                    || examResultRepository.findFirstByExamIdAndTotalScoreIsNotNullOrderByIdDesc(examId).isPresent();
            if (!alreadyStored) {
                try {
                    ExamSummary summary = ExamConverter.toExamSummary(req, examSession.getUserId(), resultId);
                    examSummaryRepository.insert(summary);
                    log.info("AI 종합 피드백 영구 데이터 적재 완료: examId={}", examId);
                } catch (DuplicateKeyException duplicateCallback) {
                    log.info("중복 AI 종합 피드백 Callback 멱등 처리: examId={}", examId);
                }
            }
            gradingService.completeSummary(examId);
            gradingService.calculateAndCacheOverallStatus(examId);
            return;
        }

        int retryCount = GradingKeys.canonicalRetryCount(req.getRetryCount());
        boolean alreadyStored = examResultRepository.existsByExamIdAndQuestionNumberAndRetryCountIn(
                examId,
                req.getQuestionNumber(),
                compatibleRetryCounts(retryCount)
        );
        if (!alreadyStored) {
            try {
                ExamResult result = ExamConverter.toExamResult(
                        req,
                        examSession.getUserId(),
                        GradingKeys.feedbackResultId(examId, req.getQuestionNumber(), retryCount)
                );
                examResultRepository.insert(result);
                log.info("AI 피드백 영구 데이터 적재 완료: examId={}, qNum={}, retryCount={}",
                        examId, req.getQuestionNumber(), retryCount);
            } catch (DuplicateKeyException duplicateCallback) {
                log.info("중복 AI Feedback Callback 멱등 처리: examId={}, qNum={}, retryCount={}",
                        examId, req.getQuestionNumber(), retryCount);
            }
        }

        gradingService.completeQuestion(examId, req.getQuestionNumber(), retryCount);
        gradingService.tryDispatchOverallSummary(examId);
    }

    // 특정 시험 세션의 AI 총합 진단 레코드와 파트별 획득 점수의 누적 가산 합산 값을 연산하여 성적표 리포트를 반환합니다.
    @Override
    public ExamResponseDTO.SummaryResult getExamSummary(String examId) {
        requireOwnedSession(examId);

        List<ExamResult> results = examResultRepository.findByExamId(examId);

        // 파트별 세부 획득 점수의 누적 총합 연산
        java.util.Map<String, Double> partScores = results.stream()
                .filter(r -> r.getQuestionNumber() != null && r.getScore() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        r -> {
                            int partNum = r.getPartNumber() != null ? r.getPartNumber() : getPartNumber(r.getQuestionNumber());
                            return "part" + partNum;
                        },
                        java.util.stream.Collectors.summingDouble(ExamResult::getScore)
                ));

        // 소수점 유실 방지 및 가독성을 위한 첫째 자리 반올림 정규화를 수행합니다.
        partScores.replaceAll((part, sum) -> Math.round(sum * 10.0) / 10.0);

        // 유저가 실제 풀이한 순수 문항 개수 산출 (retryCount == 0 이거나 null 체크, 종합요약 문서 제외)
        long totalSolvedQuestions = results.stream()
                .filter(r -> r.getQuestionNumber() != null && r.getQuestionNumber() > 0)
                .filter(r -> r.getRetryCount() != null && r.getRetryCount() == 0)
                .count();

        // 신규 종합 피드백 컬렉션의 최신 문서를 우선 사용합니다.
        // 분리 배포 전에 exam_results에 저장된 기존 종합 문서는 최신순으로 fallback합니다.
        return examSummaryRepository.findFirstByExamIdOrderByIdDesc(examId)
                .map(summary -> ExamConverter.toSummaryResult(summary, partScores, (int) totalSolvedQuestions))
                .orElseGet(() -> {
                    ExamResult legacySummary = examResultRepository
                            .findFirstByExamIdAndTotalScoreIsNotNullOrderByIdDesc(examId)
                            .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_NOT_FOUND));
                    return ExamConverter.toSummaryResult(legacySummary, partScores, (int) totalSolvedQuestions);
                });
    }

    // 유저가 채점 결과를 문항 단위로 핀포인트 조회할 때, 문제 원본(MongoDB)과 AI 결과 조각, Azure 발음 분석 세션을 결합합니다.
    @Override
    public ExamResponseDTO.QuestionResult getExamQuestion(String examId, Integer questionNumber, Integer retryCount) {
        requireOwnedSession(examId);

        List<ExamResult> examResults = examResultRepository.findByExamId(examId);

        // Azure 연산 결과 레포지토리에서 문항 식별 및 특정 회차 타겟 레코드를 로드합니다.
        AzureResult matchingAzure = azureResultRepository
                .findFirstByExamIdAndQuestionNumberAndRetryCountOrderByIdDesc(examId, questionNumber, retryCount)
                .orElse(null);

        // 해당 문항에 대해 유저가 누적하여 도전한 총 횟수를 연산합니다.
        Integer totalRetryCount = examResults.stream()
                .filter(r -> r.getQuestionNumber() != null && r.getQuestionNumber().equals(questionNumber))
                .map(r -> r.getRetryCount() != null ? r.getRetryCount() : 0)
                .max(Integer::compare)
                .map(max -> max + 1)
                .orElse(1);

        // 현재 클라이언트가 요청한 회차 안에서 가장 최근에 저장된 AI 채점 도큐먼트를 조회합니다.
        // 기존 null retryCount 문서는 0회차로 해석하던 호환성을 유지합니다.
        List<Integer> compatibleRetryCounts = retryCount == 0
                ? Arrays.asList(0, null)
                : List.of(retryCount);
        ExamResult targetDoc = examResultRepository
                .findFirstByExamIdAndQuestionNumberAndRetryCountInOrderByIdDesc(
                        examId,
                        questionNumber,
                        compatibleRetryCounts
                )
                .orElse(null);

        MockExam mockExam = mockExamRepository.findByMockExamId("mock_exam_003")
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_PAPER_NOT_FOUND));

        // 모의고사 원본 데이터셋에서 현재 문항에 일치하는 기준 문제 엔티티를 검출합니다.
        Question rawQuestion = mockExam.getQuestions().stream()
                .filter(q -> q.getQuestionNumber() != null && q.getQuestionNumber().equals(questionNumber))
                .findFirst()
                .orElseThrow(() -> new ExamsException(ErrorStatus._QUESTION_NOT_FOUND));

        // 종합 응답 데이터 구조 결합 및 조립 처리를 전용 Converter 컴포넌트에 위임
        return ExamConverter.toQuestionResult(
                examId,
                questionNumber,
                retryCount,
                totalRetryCount,
                rawQuestion,
                targetDoc,
                matchingAzure,
                getDownloadUrl(examId, questionNumber, retryCount),
                getPartNumber(questionNumber)
        );
    }

    // 별도의 3rd 파티 발음 평가 데이터인 SpeechAce 분석의 원본 수록 JSON을 전용 가공 컬렉션에 영구 보존합니다.
    @Override
    public void saveSpeechAceResult(ExamRequestDTO.SpeechAceReq req) {
        int retryCount = GradingKeys.canonicalRetryCount(req.getRetryCount());
        if (speechAceResultRepository.existsByExamIdAndQuestionNumberAndRetryCountIn(
                req.getExamId(),
                req.getQuestionNumber(),
                compatibleRetryCounts(retryCount))) {
            return;
        }

        SpeechAceResult result = SpeechAceResult.builder()
                .id(GradingKeys.speechAceResultId(req.getExamId(), req.getQuestionNumber(), retryCount))
                .examId(req.getExamId())
                .questionNumber(req.getQuestionNumber())
                .retryCount(retryCount)
                .speechAceData(req.getSpeechAceData())
                .build();

        try {
            speechAceResultRepository.insert(result);
            log.info("SpeechAce 가공 분석 원본 데이터 수복 완료: examId={}, questionNum={}",
                    req.getExamId(), req.getQuestionNumber());
        } catch (DuplicateKeyException duplicateCallback) {
            log.info("중복 SpeechAce Callback 멱등 처리: examId={}, qNum={}, retryCount={}",
                    req.getExamId(), req.getQuestionNumber(), retryCount);
        }
    }

    // 외부 Azure 전용 음성 분석 모델로부터 수신한 대용량 JSON 페이로드를 매핑 변환 없이 물리 구조 그대로 누적 보존합니다.
    @Override
    @Transactional
    public void processAzureCallback(Map<String, Object> rawPayload) {
        Map<String, Object> metadata = (Map<String, Object>) rawPayload.get("metadata");
        String examId = (String) metadata.get("user_id");
        Integer questionNumber = (Integer) metadata.get("question_number");
        Integer retryCount = metadata.get("retry_count") != null ? (Integer) metadata.get("retry_count") : 0;

        log.info("Azure 음성인식 분석 원본 수신 및 저장 시작: examId={}, questionNumber={}, retryCount={}", examId, questionNumber, retryCount);

        if (azureResultRepository.existsByExamIdAndQuestionNumberAndRetryCountIn(
                examId,
                questionNumber,
                compatibleRetryCounts(retryCount))) {
            return;
        }

        AzureResult entity = AzureResult.builder()
                .id(GradingKeys.azureResultId(examId, questionNumber, retryCount))
                .examId(examId)
                .questionNumber(questionNumber)
                .retryCount(retryCount)
                .rawData(rawPayload)
                .build();

        try {
            azureResultRepository.insert(entity);
        } catch (DuplicateKeyException duplicateCallback) {
            log.info("중복 Azure Callback 멱등 처리: examId={}, qNum={}, retryCount={}",
                    examId, questionNumber, retryCount);
        }
    }

    // 프론트엔드가 개별 문항 녹음본을 제출한 후, 해당 단건 채점 분석 결과가 MongoDB에 도착했는지 추적하기 위한 폴링 엔드포인트용 조회 메서드입니다.
    @Override
    @Transactional(readOnly = true)
    public ExamResponseDTO.QuestionPollResult getQuestionProcessingStatus(String examId, Integer questionNumber, Integer retryCount) {
        requireOwnedSession(examId);

        ExamStatus questionStatus = gradingService.getQuestionStatus(examId, questionNumber, retryCount);

        return ExamResponseDTO.QuestionPollResult.builder()
                .examId(examId)
                .questionNumber(questionNumber)
                .retryCount(retryCount)
                .status(questionStatus)
                .build();
    }

    private static List<Integer> compatibleRetryCounts(int retryCount) {
        return retryCount == 0 ? Arrays.asList(0, null) : List.of(retryCount);
    }
}
