package web.tosunsaeng.domain.exams.application;

import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.exams.converter.ExamConverter;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final S3Template s3Template; // Spring Cloud AWS 제공

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    @Override
    public ExamResponseDTO.CreateSessionResult createExamSession() {
        // 1. 고유한 시험 세션 ID 생성
        String examId = "ex_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        // 2. Redis에 세션 상태 저장 (키: "exam:status:{examId}", 값: "PENDING", 만료시간: 1시간)
        String redisKey = "exam:status:" + examId;
        redisTemplate.opsForValue().set(redisKey, "PENDING", 1, TimeUnit.HOURS);
        log.info("새로운 모의고사 세션 생성 완료: {}", examId);

        // 3. PoC용 하드코딩 문제 데이터 변환 (추후 MongoDB 조회 후 ExamConverter로 변환하도록 고도화)
        List<ExamResponseDTO.QuestionDTO> mockQuestions = List.of(
                ExamConverter.toQuestionDTO(1, "q_001", "Read a text aloud. I'd like to welcome you to the Tosunsaeng English experience...", 45, 45),
                ExamConverter.toQuestionDTO(3, "q_003", "Describe a picture. Describe what you see in this picture in as much detail as possible.", 45, 30)
        );

        // 4. 컨버터를 통한 최종 결과 조립 및 반환
        return ExamConverter.toCreateSessionResult(examId, mockQuestions);
    }

    @Override
    public ExamResponseDTO.UploadUrlResult getPresignedUrl(String examId, String questionId) {
        // 1. S3에 저장될 파일 경로 (Key) 생성
        String fileKey = String.format("temp/%s/%s.wav", examId, questionId);

        // 2. 5분간 유효한 S3 PUT Presigned URL 발급
        URL presignedUrl = s3Template.createSignedPutURL(bucketName, fileKey, Duration.ofMinutes(5));

        return ExamConverter.toUploadUrlResult(presignedUrl.toString(), fileKey, 300);
    }

    @Override
    public ExamResponseDTO.SubmitResult submitAudio(String examId, String questionId, ExamRequestDTO.SubmitAudioReq req) {
        // 프론트엔드가 업로드를 마쳤다고 알림 -> 상태를 PROCESSING으로 변경
        String redisKey = "exam:status:" + examId;
        redisTemplate.opsForValue().set(redisKey, "PROCESSING", 1, TimeUnit.HOURS);

        // TODO: (고도화 시) 여기서 SQS 큐에 메시지를 보내 AI 에이전트(Lambda 또는 파이썬 서버)를 비동기로 호출합니다.
        log.info("채점 요청 접수 완료 - FileKey: {}", req.getFileKey());

        return ExamConverter.toSubmitResult("PROCESSING");
    }

    @Override
    public ExamResponseDTO.StatusResult getExamStatus(String examId) {
        String redisKey = "exam:status:" + examId;
        String status = (String) redisTemplate.opsForValue().get(redisKey);

        if (status == null) status = "FAILED"; // 세션 만료 등

        // PoC 테스트를 위해 임시로 진행도 60% 하드코딩
        // (실제로는 에이전트가 상태를 업데이트할 때마다 이 값을 읽어옴)
        return ExamConverter.toStatusResult(examId, status, 60);
    }

    @Override
    public ExamResponseDTO.ScoreResult getExamResults(String examId) {
        // PoC를 위한 목업(Mock) 결과 데이터 반환 (추후 MongoDB 조회 로직으로 교체)
        ExamResponseDTO.MetricsDTO metrics = ExamResponseDTO.MetricsDTO.builder()
                .pronunciation("Good").fluency("Fair").grammar("Fair").vocabulary("Good").topicRelevance("Good").build();

        ExamResponseDTO.PartResultDTO part1 = ExamResponseDTO.PartResultDTO.builder()
                .part(1)
                .sttText("I'd like to welcome you to the wor... early...")
                .deductionReason("world, early 단어 발음 부정확")
                .etsRubric("Some words are mispronounced but generally understandable.")
                .feedback("world 발음 시 r과 l 사운드의 구분이 필요합니다.")
                .build();

        return ExamConverter.toScoreResult(examId, "120~130", metrics, List.of(part1));
    }
}