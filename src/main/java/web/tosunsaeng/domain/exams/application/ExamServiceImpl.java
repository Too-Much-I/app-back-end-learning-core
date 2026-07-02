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

    // AI 서버 주소 (필요 시 수정)
    private final String AI_SERVER_URL = "http://172.16.202.101:8000/evaluation";

    private final QuestionRepository questionRepository;
    private final ExamResultRepository examResultRepository;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    // [추가] 모의고사 출제 시 문제 음성 파일(TTS 등) 읽어오기용 URL 발급
    private String getQuestionAudioUrl(String examPaperId, String questionId) {
        String fileKey = String.format("questions/%s/%s.wav", examPaperId, questionId);

        software.amazon.awssdk.services.s3.model.GetObjectRequest getObjectRequest =
                software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileKey)
                        .build();

        software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest presignRequest =
                software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(60)) // 시험 치는 시간 고려 (60분)
                        .getObjectRequest(getObjectRequest)
                        .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    // [추가] 채점 결과 확인 시 사용자 녹음본 읽어오기용 URL 발급
    private String getDownloadUrl(String examId, String questionId) {
        String fileKey = String.format("temp/%s/%s.wav", examId, questionId);

        software.amazon.awssdk.services.s3.model.GetObjectRequest getObjectRequest =
                software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileKey)
                        .build();

        software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest presignRequest =
                software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(5)) // 결과 확인은 5분
                        .getObjectRequest(getObjectRequest)
                        .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    @Override
    public ExamResponseDTO.CreateSessionResult createExamSession() {
        String examId = "ex_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        String redisKey = "exam:status:" + examId;
        redisTemplate.opsForValue().set(redisKey, "PENDING", 1, TimeUnit.HOURS);
        log.info("새로운 모의고사 세션 생성 완료: {}", examId);

        String targetPaperId = "paper_001";
        List<Question> questions = questionRepository.findByExamPaperId(targetPaperId);

        if (questions.isEmpty()) {
            log.warn("MongoDB에 '{}'에 해당하는 문제가 없습니다. 데이터를 추가해 주세요.", targetPaperId);
        }

        // Entity -> DTO 변환 시 S3 오디오 URL(문제 음성) 함께 발급
        List<ExamResponseDTO.QuestionDTO> questionDTOs = questions.stream()
                .map(question -> {
                    ExamResponseDTO.QuestionDTO dto = ExamConverter.toQuestionDTO(question);
                    String audioUrl = getQuestionAudioUrl(targetPaperId, dto.getQuestionId());
                    dto.setAudioUrl(audioUrl);
                    return dto;
                })
                .collect(Collectors.toList());

        return ExamConverter.toCreateSessionResult(examId, questionDTOs);
    }

    @Override
    public ExamResponseDTO.UploadUrlResult getPresignedUrl(String examId, String questionId) {
        String fileKey = String.format("temp/%s/%s.wav", examId, questionId);

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

        return ExamConverter.toUploadUrlResult(url, fileKey, 300);
    }

    @Override
    public ExamResponseDTO.SubmitResult submitAudio(String examId, String questionId, ExamRequestDTO.SubmitAudioReq req) {
        String redisKey = "exam:status:" + examId;
        redisTemplate.opsForValue().set(redisKey, "PROCESSING", 1, TimeUnit.HOURS);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("user_id", "test");
        body.add("mock_exam_id", examId);
        body.add("part_number", "1");
        body.add("question_number", questionId);
        body.add("file_key", req.getFileKey());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(AI_SERVER_URL, entity, String.class);
            log.info("AI 에이전트 채점 요청 성공: {}", response.getBody());
        } catch (Exception e) {
            log.error("AI 에이전트 연동 실패: {}", e.getMessage());
            redisTemplate.opsForValue().set(redisKey, "FAILED", 1, TimeUnit.HOURS);
            throw new ExamsException(ErrorStatus._AI_SERVER_CONNECTION_ERROR);
        }

        return ExamConverter.toSubmitResult("PROCESSING");
    }

    @Override
    public ExamResponseDTO.StatusResult getExamStatus(String examId) {
        String redisKey = "exam:status:" + examId;
        String status = (String) redisTemplate.opsForValue().get(redisKey);

        if (status == null) status = "FAILED";

        return ExamConverter.toStatusResult(examId, status, 60);
    }

    @Override
    public ExamResponseDTO.ScoreResult getExamResults(String examId) {
        // 결과 조회
        ExamResult result = examResultRepository.findByExamId(examId)
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_NOT_FOUND));

        ExamResponseDTO.ScoreResult scoreResult = ExamConverter.toScoreResult(result);

        // 파트별로 S3 오디오 URL(사용자 녹음본) 함께 발급
        if (scoreResult.getPartResults() != null) {
            scoreResult.getPartResults().forEach(partDto -> {
                if (partDto.getQuestionId() != null) {
                    String audioUrl = getDownloadUrl(examId, partDto.getQuestionId());
                    partDto.setAudioUrl(audioUrl);
                }
            });
        }

        return scoreResult;
    }

    @Override
    public void updateExamResult(ExamRequestDTO.AiResultReq req) {
        String redisKey = "exam:status:" + req.getExamId();
        redisTemplate.opsForValue().set(redisKey, "COMPLETED", 1, TimeUnit.HOURS);

        ExamResult result = ExamConverter.toExamResult(req);
        examResultRepository.save(result);

        log.info("채점 완료 및 결과 저장 성공: {}", req.getExamId());
    }
}