package web.tosunsaeng.domain.exams.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.exams.converter.ExamConverter;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.QuestionRepository;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final software.amazon.awssdk.services.s3.presigner.S3Presigner s3Presigner;
    private final RestTemplate restTemplate;
    private final String AI_SERVER_URL = "http://172.16.202.101:8000/evaluation";

    // MongoDB Repository 주입
    private final QuestionRepository questionRepository;
    private final ExamResultRepository examResultRepository;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    @Override
    public ExamResponseDTO.CreateSessionResult createExamSession() {
        // 1. 고유한 시험 세션 ID 생성
        String examId = "ex_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        // 2. Redis에 세션 상태 저장 (키: "exam:status:{examId}", 값: "PENDING", 만료시간: 1시간)
        String redisKey = "exam:status:" + examId;
        redisTemplate.opsForValue().set(redisKey, "PENDING", 1, TimeUnit.HOURS);
        log.info("새로운 모의고사 세션 생성 완료: {}", examId);

        // 3. [DB 연동] 지정된 모의고사 세트의 문제 목록 조회
        // TODO: 향후 프론트엔드에서 파라미터로 넘겨받도록 고도화 가능
        String targetPaperId = "paper_001";
        List<Question> questions = questionRepository.findByExamPaperId(targetPaperId);

        if (questions.isEmpty()) {
            log.warn("MongoDB에 '{}'에 해당하는 문제가 없습니다. 데이터를 추가해 주세요.", targetPaperId);
        }

        // 4. Entity -> DTO 변환 (ExamConverter 사용)
        List<ExamResponseDTO.QuestionDTO> questionDTOs = questions.stream()
                .map(ExamConverter::toQuestionDTO)
                .collect(Collectors.toList());

        // 5. 최종 결과 조립 및 반환
        return ExamConverter.toCreateSessionResult(examId, questionDTOs);
    }

    @Override
    public ExamResponseDTO.UploadUrlResult getPresignedUrl(String examId, String questionId) {
        String fileKey = String.format("temp/%s/%s.wav", examId, questionId);

        // 1. S3에 PUT(업로드)할 객체 정보 생성
        software.amazon.awssdk.services.s3.model.PutObjectRequest objectRequest =
                software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileKey)
                        .build();

        // 2. 5분 동안 유효한 Presign 요청 생성
        software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest presignRequest =
                software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(5))
                        .putObjectRequest(objectRequest)
                        .build();

        // 3. 최종 URL 발급
        software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest presignedRequest =
                s3Presigner.presignPutObject(presignRequest);

        String url = presignedRequest.url().toString();

        return ExamConverter.toUploadUrlResult(url, fileKey, 300);
    }

    @Override
    public ExamResponseDTO.SubmitResult submitAudio(String examId, String questionId, ExamRequestDTO.SubmitAudioReq req) {
        // 1. 상태 변경
        String redisKey = "exam:status:" + examId;
        redisTemplate.opsForValue().set(redisKey, "PROCESSING", 1, TimeUnit.HOURS);

        // 2. AI 에이전트로 전송할 데이터 구성
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("user_id", "test");
        body.add("mock_exam_id", examId);
        body.add("part_number", "1");
        body.add("question_number", questionId);
        body.add("file_key", req.getFileKey());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        // 3. 동기 호출 시도
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(AI_SERVER_URL, entity, String.class);
            log.info("AI 에이전트 채점 요청 성공: {}", response.getBody());
        } catch (Exception e) {
            log.error("AI 에이전트 연동 실패: {}", e.getMessage());

            // 상태를 FAILED로 즉시 변경
            redisTemplate.opsForValue().set(redisKey, "FAILED", 1, TimeUnit.HOURS);

            // [중요] 여기서 예외를 던져야 클라이언트가 실패를 알 수 있음
            throw new ExamsException(ErrorStatus._AI_SERVER_CONNECTION_ERROR);
        }

        return ExamConverter.toSubmitResult("PROCESSING");
    }

    @Override
    public ExamResponseDTO.StatusResult getExamStatus(String examId) {
        String redisKey = "exam:status:" + examId;
        String status = (String) redisTemplate.opsForValue().get(redisKey);

        if (status == null) status = "FAILED"; // 세션 만료 등 예외 처리

        // PoC 테스트를 위해 임시로 진행도 60% 고정
        // (실제로는 AI 에이전트가 상태를 업데이트할 때마다 이 값을 읽어옴)
        return ExamConverter.toStatusResult(examId, status, 60);
    }

    @Override
    public ExamResponseDTO.ScoreResult getExamResults(String examId) {
        // [DB 연동] MongoDB에서 examId로 채점 결과를 조회합니다.
        ExamResult result = examResultRepository.findByExamId(examId)
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_NOT_FOUND));

        return ExamConverter.toScoreResult(result);
    }

    @Override
    public void updateExamResult(ExamRequestDTO.AiResultReq req) {
        // 1. Redis 상태 변경
        String redisKey = "exam:status:" + req.getExamId();
        redisTemplate.opsForValue().set(redisKey, "COMPLETED", 1, TimeUnit.HOURS);

        // 2. Converter를 사용하여 DTO를 Entity로 변환 (필드 매핑 포함)
        ExamResult result = ExamConverter.toExamResult(req);

        // 3. MongoDB 저장
        examResultRepository.save(result);

        log.info("채점 완료 및 결과 저장 성공: {}", req.getExamId());
    }
}