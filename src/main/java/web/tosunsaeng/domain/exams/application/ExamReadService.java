package web.tosunsaeng.domain.exams.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;
import web.tosunsaeng.domain.exams.domain.enums.GradingJobStatus;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;
import web.tosunsaeng.domain.exams.domain.repository.MockExamRepository;
import web.tosunsaeng.domain.exams.domain.repository.QuestionGradingJobRepository;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.auth.CurrentUserProvider;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamReadService {

    private static final int EXAM_MAX_SCORE = 200;
    private static final Comparator<ExamSession> HISTORY_ORDER = Comparator
            .comparing(
                    ExamSession::getCompletedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())
            )
            .thenComparing(
                    ExamSession::getExamId,
                    Comparator.nullsLast(Comparator.reverseOrder())
            );

    private final ExamSessionRepository examSessionRepository;
    private final MockExamRepository mockExamRepository;
    private final ExamSummaryRepository examSummaryRepository;
    private final ExamResultRepository examResultRepository;
    private final QuestionGradingJobRepository questionGradingJobRepository;
    private final CurrentUserProvider currentUserProvider;

    public ExamResponseDTO.ExamHistoryResult getExamHistory() {
        String currentUserId = currentUserProvider.getCurrentUserId();
        List<ExamSession> completedSessions = safeList(
                examSessionRepository.findCompletedByUserId(currentUserId)
        ).stream()
                .filter(session -> session != null
                        && Objects.equals(session.getUserId(), currentUserId)
                        && session.getCompletedAt() != null)
                .sorted(HISTORY_ORDER)
                .toList();

        if (completedSessions.isEmpty()) {
            return ExamResponseDTO.ExamHistoryResult.builder()
                    .totalCount(0)
                    .histories(List.of())
                    .build();
        }

        Set<String> examIds = completedSessions.stream()
                .map(ExamSession::getExamId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> mockExamIds = completedSessions.stream()
                .map(ExamSession::getMockExamId)
                .map(GradingKeys::effectiveMockExamId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, String> titlesByMockExamId = new HashMap<>();
        safeList(mockExamRepository.findTitlesByMockExamIdIn(mockExamIds)).stream()
                .filter(Objects::nonNull)
                .filter(mockExam -> mockExam.getMockExamId() != null)
                .forEach(mockExam -> titlesByMockExamId.putIfAbsent(
                        mockExam.getMockExamId(),
                        mockExam.getTitle()
                ));
        Map<String, ExamSummary> summariesByExamId = latestByExamId(
                safeList(examSummaryRepository.findHistoryCandidatesByExamIdIn(examIds)),
                ExamSummary::getExamId
        );
        Map<String, ExamResult> legacySummariesByExamId = latestByExamId(
                safeList(examResultRepository.findLegacySummaryCandidatesByExamIdIn(examIds)),
                ExamResult::getExamId
        );
        Map<String, Integer> retriedQuestionCountsByExamId = retriedQuestionCounts(examIds);

        List<ExamResponseDTO.ExamHistoryItem> histories = completedSessions.stream()
                .map(session -> toHistoryItem(
                        session,
                        titlesByMockExamId,
                        summariesByExamId,
                        legacySummariesByExamId,
                        retriedQuestionCountsByExamId
                ))
                .toList();

        return ExamResponseDTO.ExamHistoryResult.builder()
                .totalCount(histories.size())
                .histories(histories)
                .build();
    }

    public ExamResponseDTO.ExamRetriesResult getExamRetries(String examId) {
        requireOwnedSession(examId);

        Map<AttemptKey, RetryAttemptMetadata> attemptsByKey = new HashMap<>();
        safeList(examResultRepository.findQuestionAttemptsByExamId(examId)).stream()
                .filter(Objects::nonNull)
                .filter(result -> result.getQuestionNumber() != null)
                .forEach(result -> attemptsByKey.putIfAbsent(
                        new AttemptKey(
                                result.getQuestionNumber(),
                                GradingKeys.canonicalRetryCount(result.getRetryCount())
                        ),
                        new RetryAttemptMetadata(
                                GradingJobStatus.COMPLETED,
                                result.getScore(),
                                null
                        )
                ));

        Map<AttemptKey, RetryAttemptMetadata> jobAttemptsByKey = new HashMap<>();
        safeList(questionGradingJobRepository.findAttemptsByExamId(examId)).stream()
                .filter(Objects::nonNull)
                .filter(job -> job.getQuestionNumber() != null)
                .forEach(job -> jobAttemptsByKey.putIfAbsent(
                        new AttemptKey(
                                job.getQuestionNumber(),
                                GradingKeys.canonicalRetryCount(job.getRetryCount())
                        ),
                        new RetryAttemptMetadata(
                                job.getStatus(),
                                null,
                                job.getCompletedAt()
                        )
                ));
        jobAttemptsByKey.forEach((attemptKey, jobAttempt) -> attemptsByKey.merge(
                attemptKey,
                jobAttempt,
                (resultAttempt, currentJobAttempt) -> new RetryAttemptMetadata(
                        currentJobAttempt.status(),
                        resultAttempt.score(),
                        currentJobAttempt.completedAt()
                )
        ));

        Map<Integer, List<Map.Entry<AttemptKey, RetryAttemptMetadata>>> attemptsByQuestion =
                attemptsByKey.entrySet().stream()
                        .collect(Collectors.groupingBy(entry -> entry.getKey().questionNumber()));
        List<ExamResponseDTO.RetriedQuestionItem> questions = attemptsByQuestion.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .anyMatch(attempt -> attempt.getKey().retryCount() >= 1))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> toRetriedQuestion(entry.getKey(), entry.getValue()))
                .toList();

        return ExamResponseDTO.ExamRetriesResult.builder()
                .examId(examId)
                .questions(questions)
                .build();
    }

    private ExamSession requireOwnedSession(String examId) {
        ExamSession session = examSessionRepository.findById(examId)
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_NOT_FOUND));
        if (!Objects.equals(session.getUserId(), currentUserProvider.getCurrentUserId())) {
            log.warn(
                    "시험 소유권 검증 실패 event=exam.access outcome=denied "
                            + "reason=ownership_mismatch examId={}",
                    examId
            );
            throw new ExamsException(ErrorStatus._FORBIDDEN);
        }
        if (session.isEntitlementConfirming()) {
            throw new ExamsException(ErrorStatus._EXAM_CREATION_PROCESSING, 1);
        }
        return session;
    }

    private ExamResponseDTO.ExamHistoryItem toHistoryItem(
            ExamSession session,
            Map<String, String> titlesByMockExamId,
            Map<String, ExamSummary> summariesByExamId,
            Map<String, ExamResult> legacySummariesByExamId,
            Map<String, Integer> retriedQuestionCountsByExamId) {
        String examId = session.getExamId();
        String title = titlesByMockExamId.get(
                GradingKeys.effectiveMockExamId(session.getMockExamId())
        );
        ExamSummary summary = summariesByExamId.get(examId);
        ExamResult legacySummary = summary == null ? legacySummariesByExamId.get(examId) : null;
        boolean summaryAvailable = summary != null || legacySummary != null;

        if (title == null) {
            log.warn(
                    "시험 이력 제목 누락 event=exam.history.data outcome=incomplete "
                            + "reason=missing_mock_exam_title examId={}",
                    examId
            );
        }
        if (!summaryAvailable) {
            log.warn(
                    "시험 이력 요약 결과 누락 event=exam.history.data outcome=incomplete "
                            + "reason=missing_summary examId={}",
                    examId
            );
        }

        return ExamResponseDTO.ExamHistoryItem.builder()
                .examId(examId)
                .title(title)
                .status(session.effectiveStatus())
                .cycleNumber(session.getCycleNumber())
                .startedAt(session.getCreatedAt())
                .completedAt(session.getCompletedAt())
                .totalScore(summary != null ? summary.getTotalScore()
                        : legacySummary != null ? legacySummary.getTotalScore() : null)
                .maxScore(EXAM_MAX_SCORE)
                .levelEstimate(summary != null ? summary.getLevelEstimate()
                        : legacySummary != null ? legacySummary.getLevelEstimate() : null)
                .summaryAvailable(summaryAvailable)
                .retriedQuestionCount(retriedQuestionCountsByExamId.getOrDefault(examId, 0))
                .build();
    }

    private Map<String, Integer> retriedQuestionCounts(Set<String> examIds) {
        Map<String, Set<Integer>> questionNumbersByExamId = new HashMap<>();
        safeList(examResultRepository.findRetriedQuestionCandidatesByExamIdIn(examIds))
                .stream()
                .filter(Objects::nonNull)
                .forEach(result -> addRetriedQuestion(
                        questionNumbersByExamId,
                        examIds,
                        result.getExamId(),
                        result.getQuestionNumber(),
                        result.getRetryCount()
                ));
        safeList(questionGradingJobRepository.findRetriedQuestionCandidatesByExamIdIn(examIds))
                .stream()
                .filter(Objects::nonNull)
                .forEach(job -> addRetriedQuestion(
                        questionNumbersByExamId,
                        examIds,
                        job.getExamId(),
                        job.getQuestionNumber(),
                        job.getRetryCount()
                ));

        Map<String, Integer> countsByExamId = new HashMap<>();
        questionNumbersByExamId.forEach((examId, questionNumbers) ->
                countsByExamId.put(examId, questionNumbers.size()));
        return countsByExamId;
    }

    private static void addRetriedQuestion(
            Map<String, Set<Integer>> questionNumbersByExamId,
            Set<String> requestedExamIds,
            String examId,
            Integer questionNumber,
            Integer retryCount) {
        if (!requestedExamIds.contains(examId)
                || questionNumber == null
                || GradingKeys.canonicalRetryCount(retryCount) < 1) {
            return;
        }
        questionNumbersByExamId
                .computeIfAbsent(examId, ignored -> new LinkedHashSet<>())
                .add(questionNumber);
    }

    private ExamResponseDTO.RetriedQuestionItem toRetriedQuestion(
            Integer questionNumber,
            Collection<Map.Entry<AttemptKey, RetryAttemptMetadata>> storedAttempts) {
        List<ExamResponseDTO.RetryAttemptItem> attempts = storedAttempts.stream()
                .sorted(Comparator.comparingInt(entry -> entry.getKey().retryCount()))
                .map(entry -> ExamResponseDTO.RetryAttemptItem.builder()
                        .retryCount(entry.getKey().retryCount())
                        .status(entry.getValue().status())
                        .score(entry.getValue().score())
                        .completedAt(entry.getValue().completedAt())
                        .build())
                .toList();

        return ExamResponseDTO.RetriedQuestionItem.builder()
                .partNumber(getPartNumber(questionNumber))
                .questionNumber(questionNumber)
                .totalAttemptCount(attempts.size())
                .latestRetryCount(attempts.getLast().getRetryCount())
                .attempts(attempts)
                .build();
    }

    private static Integer getPartNumber(Integer questionNumber) {
        return GradingKeys.partNumberForQuestion(questionNumber);
    }

    private static <T> Map<String, T> latestByExamId(
            List<T> candidates,
            Function<T, String> examIdExtractor) {
        Map<String, T> latest = new HashMap<>();
        candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> examIdExtractor.apply(candidate) != null)
                .forEach(candidate -> latest.putIfAbsent(
                        examIdExtractor.apply(candidate),
                        candidate
                ));
        return latest;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record AttemptKey(Integer questionNumber, int retryCount) {
    }

    private record RetryAttemptMetadata(
            GradingJobStatus status,
            Double score,
            Instant completedAt) {
    }
}
