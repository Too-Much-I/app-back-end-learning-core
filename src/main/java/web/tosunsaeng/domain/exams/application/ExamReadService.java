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
@Transactional(readOnly = true)
public class ExamReadService {

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

        List<ExamResponseDTO.ExamHistoryItem> histories = completedSessions.stream()
                .map(session -> toHistoryItem(
                        session,
                        titlesByMockExamId,
                        summariesByExamId,
                        legacySummariesByExamId
                ))
                .toList();

        return ExamResponseDTO.ExamHistoryResult.builder()
                .totalCount(histories.size())
                .histories(histories)
                .build();
    }

    public ExamResponseDTO.ExamRetriesResult getExamRetries(String examId) {
        requireOwnedSession(examId);

        Map<AttemptKey, GradingJobStatus> attemptsByKey = new HashMap<>();
        safeList(examResultRepository.findQuestionAttemptsByExamId(examId)).stream()
                .filter(Objects::nonNull)
                .filter(result -> result.getQuestionNumber() != null)
                .forEach(result -> attemptsByKey.putIfAbsent(
                        new AttemptKey(
                                result.getQuestionNumber(),
                                GradingKeys.canonicalRetryCount(result.getRetryCount())
                        ),
                        GradingJobStatus.COMPLETED
                ));

        Map<AttemptKey, GradingJobStatus> jobAttemptsByKey = new HashMap<>();
        safeList(questionGradingJobRepository.findAttemptsByExamId(examId)).stream()
                .filter(Objects::nonNull)
                .filter(job -> job.getQuestionNumber() != null)
                .forEach(job -> jobAttemptsByKey.putIfAbsent(
                        new AttemptKey(
                                job.getQuestionNumber(),
                                GradingKeys.canonicalRetryCount(job.getRetryCount())
                        ),
                        job.getStatus()
                ));
        attemptsByKey.putAll(jobAttemptsByKey);

        Map<Integer, List<Map.Entry<AttemptKey, GradingJobStatus>>> attemptsByQuestion =
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
            throw new ExamsException(ErrorStatus._FORBIDDEN);
        }
        return session;
    }

    private ExamResponseDTO.ExamHistoryItem toHistoryItem(
            ExamSession session,
            Map<String, String> titlesByMockExamId,
            Map<String, ExamSummary> summariesByExamId,
            Map<String, ExamResult> legacySummariesByExamId) {
        String examId = session.getExamId();
        String title = titlesByMockExamId.get(
                GradingKeys.effectiveMockExamId(session.getMockExamId())
        );
        ExamSummary summary = summariesByExamId.get(examId);
        ExamResult legacySummary = summary == null ? legacySummariesByExamId.get(examId) : null;
        boolean summaryAvailable = summary != null || legacySummary != null;

        if (title == null) {
            log.warn("완료 시험 이력 문제지 제목 없음: examId={}", examId);
        }
        if (!summaryAvailable) {
            log.warn("완료 시험 이력 종합 결과 없음: examId={}", examId);
        }

        return ExamResponseDTO.ExamHistoryItem.builder()
                .examId(examId)
                .title(title)
                .cycleNumber(session.getCycleNumber())
                .completedAt(session.getCompletedAt())
                .totalScore(summary != null ? summary.getTotalScore()
                        : legacySummary != null ? legacySummary.getTotalScore() : null)
                .levelEstimate(summary != null ? summary.getLevelEstimate()
                        : legacySummary != null ? legacySummary.getLevelEstimate() : null)
                .summaryAvailable(summaryAvailable)
                .build();
    }

    private ExamResponseDTO.RetriedQuestionItem toRetriedQuestion(
            Integer questionNumber,
            Collection<Map.Entry<AttemptKey, GradingJobStatus>> storedAttempts) {
        List<ExamResponseDTO.RetryAttemptItem> attempts = storedAttempts.stream()
                .sorted(Comparator.comparingInt(entry -> entry.getKey().retryCount()))
                .map(entry -> ExamResponseDTO.RetryAttemptItem.builder()
                        .retryCount(entry.getKey().retryCount())
                        .status(entry.getValue())
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
}
