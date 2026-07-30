package web.tosunsaeng.domain.exams.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionCompletionQuery;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamSessionManager {

    private static final DateTimeFormatter EXAM_ID_TIME_FORMAT = DateTimeFormatter.ofPattern("MMdd_HHmm");
    private static final int CREATE_ATTEMPTS = 3;

    private final ExamSessionRepository examSessionRepository;
    private final ExamSessionCompletionQuery examSessionCompletionQuery;
    private final ExamCompletionEvidenceService completionEvidenceService;
    private final MockExamCatalogService mockExamCatalogService;
    private final Clock clock;

    public Assignment findOrCreate(String userId) {
        return findOrCreate(userId, 1);
    }

    private Assignment findOrCreate(String userId, int attempt) {
        Optional<ExamSession> reusable = findReusableSession(userId);
        if (reusable.isPresent()) {
            return existingAssignment(reusable.get());
        }

        List<MockExamCatalogService.CatalogExam> catalog = mockExamCatalogService.findAssignableExams();
        Map<String, Long> completionCounts = completionCounts(userId);
        MockExamCatalogService.CatalogExam selected = catalog.stream()
                .min(Comparator
                        .comparingLong((MockExamCatalogService.CatalogExam candidate) ->
                                completionCounts.getOrDefault(candidate.mockExam().getMockExamId(), 0L))
                        .thenComparingInt(MockExamCatalogService.CatalogExam::sequence))
                .orElseThrow();

        long completionCount = completionCounts.getOrDefault(selected.mockExam().getMockExamId(), 0L);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        ExamSession newSession = ExamSession.builder()
                .examId(newExamId(now))
                .userId(userId)
                .createdAt(now)
                .mockExamId(selected.mockExam().getMockExamId())
                .cycleNumber(Math.toIntExact(completionCount + 1))
                .active(true)
                .completedAt(null)
                .build();

        try {
            ExamSession inserted = examSessionRepository.insert(newSession);
            return new Assignment(inserted, selected.mockExam(), true);
        } catch (DuplicateKeyException concurrentCreation) {
            Optional<ExamSession> concurrentSession = findReusableSession(userId);
            if (concurrentSession.isPresent()) {
                return existingAssignment(concurrentSession.get());
            }
            if (attempt < CREATE_ATTEMPTS) {
                return findOrCreate(userId, attempt + 1);
            }
            throw concurrentCreation;
        }
    }

    public boolean completeIfIncomplete(String examId) {
        LocalDateTime completedAt = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        return examSessionRepository.completeIfIncomplete(examId, completedAt) == 1;
    }

    private Assignment existingAssignment(ExamSession session) {
        String mockExamId = GradingKeys.effectiveMockExamId(session.getMockExamId());
        MockExam mockExam = mockExamCatalogService.getRequiredExam(mockExamId);
        return new Assignment(session, mockExam, false);
    }

    private Optional<ExamSession> findReusableSession(String userId) {
        List<ExamSession> candidates = examSessionRepository.findActiveOrLegacyCandidatesByUserId(userId);
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        List<ExamSession> reusable = new ArrayList<>();
        for (ExamSession candidate : candidates) {
            if (Boolean.FALSE.equals(candidate.getActive())) {
                continue;
            }
            if (candidate.getCompletedAt() != null) {
                if (candidate.getActive() == null) {
                    examSessionRepository.deactivateLegacyCompletedSessionIfUnchanged(candidate.getExamId());
                } else if (isLegacyExplicitActive(candidate)) {
                    examSessionRepository.deactivateLegacyActiveCompletedSessionIfUnchanged(candidate.getExamId());
                }
                continue;
            }
            if (Boolean.TRUE.equals(candidate.getActive()) && !isLegacyExplicitActive(candidate)) {
                reusable.add(candidate);
                continue;
            }

            ExamCompletionEvidenceService.CompletionEvidence evidence =
                    completionEvidenceService.findCompletionEvidence(
                            candidate.getExamId(),
                            candidate.getCreatedAt()
                    );
            if (!evidence.completed()) {
                reusable.add(candidate);
                continue;
            }

            if (evidence.completedAt() == null) {
                log.warn("Legacy ExamSession has completion evidence but no trustworthy completion timestamp; "
                        + "the Session will not be reused or backfilled: examId={}", candidate.getExamId());
                continue;
            }

            long updated = isLegacyExplicitActive(candidate)
                    ? examSessionRepository.backfillLegacyActiveCompletionIfUnchanged(
                            candidate.getExamId(), evidence.completedAt())
                    : examSessionRepository.backfillLegacyCompletionIfUnchanged(
                            candidate.getExamId(), evidence.completedAt());
            if (updated == 0) {
                log.debug("Legacy ExamSession completion backfill was already applied or the Session changed: examId={}",
                        candidate.getExamId());
            } else if (evidence.approximateTimestamp()) {
                log.warn("Legacy ExamSession completion used createdAt as an approximate timestamp: examId={}",
                        candidate.getExamId());
            }
        }

        if (reusable.isEmpty()) {
            return Optional.empty();
        }
        if (reusable.size() > 1) {
            log.warn("Multiple reusable legacy ExamSessions exist for one user; reusing the newest session");
        }
        return Optional.of(reusable.getFirst());
    }

    private static boolean isLegacyExplicitActive(ExamSession session) {
        return Boolean.TRUE.equals(session.getActive()) && session.getCycleNumber() == null;
    }

    private Map<String, Long> completionCounts(String userId) {
        List<ExamSessionCompletionQuery.CompletionCount> completed =
                examSessionCompletionQuery.countCompletedByMockExamId(userId);
        Map<String, Long> counts = new LinkedHashMap<>();
        if (completed == null) {
            return counts;
        }
        for (ExamSessionCompletionQuery.CompletionCount count : completed) {
            String mockExamId = GradingKeys.effectiveMockExamId(count.mockExamId());
            counts.merge(mockExamId, count.completionCount(), Long::sum);
        }
        return counts;
    }

    private static String newExamId(LocalDateTime now) {
        String uuidPart = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return "ex_" + uuidPart + "_" + now.format(EXAM_ID_TIME_FORMAT);
    }

    public record Assignment(ExamSession session, MockExam mockExam, boolean created) {
    }
}
