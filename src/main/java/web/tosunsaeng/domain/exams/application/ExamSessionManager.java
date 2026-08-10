package web.tosunsaeng.domain.exams.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
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

    public Assignment startNew(String userId) {
        return startNew(userId, 1);
    }

    private Assignment startNew(String userId, int attempt) {
        List<String> abandonedExamIds = abandonInProgressSessions(userId);

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
                .status(ExamSessionStatus.IN_PROGRESS)
                .completedAt(null)
                .build();

        try {
            ExamSession inserted = examSessionRepository.insert(newSession);
            log.info(
                    "시험 세션 생성 완료 event=exam.session.created outcome=success "
                            + "examId={} mockExamId={} "
                            + "cycleNumber={} abandonedCount={} createAttempt={}",
                    inserted.getExamId(),
                    inserted.getMockExamId(),
                    inserted.getCycleNumber(),
                    abandonedExamIds.size(),
                    attempt
            );
            return new Assignment(inserted, selected.mockExam(), true);
        } catch (DuplicateKeyException concurrentCreation) {
            log.warn(
                    "활성 시험 세션 중복 감지 event=exam.session.create "
                            + "outcome={} reason=duplicate_active_session "
                            + "createAttempt={} nextAttempt={}",
                    attempt < CREATE_ATTEMPTS ? "retry" : "failed",
                    attempt,
                    attempt < CREATE_ATTEMPTS ? attempt + 1 : null
            );
            if (attempt < CREATE_ATTEMPTS) {
                return startNew(userId, attempt + 1);
            }
            throw concurrentCreation;
        }
    }

    public boolean completeIfIncomplete(String examId) {
        LocalDateTime completedAt = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        boolean completed = examSessionRepository.completeIfIncomplete(examId, completedAt) == 1;
        if (completed) {
            log.info(
                    "시험 세션 완료 전환 event=exam.session.completed "
                            + "outcome=transitioned examId={} "
                            + "fromStatus=IN_PROGRESS toStatus=COMPLETED",
                    examId
            );
        } else {
            log.debug("시험 세션 완료 전환 생략 event=exam.session.completed outcome=noop examId={}", examId);
        }
        return completed;
    }

    private List<String> abandonInProgressSessions(String userId) {
        List<String> abandonedExamIds = new ArrayList<>();
        for (ExamSession candidate : findInProgressSessions(userId)) {
            if (examSessionRepository.abandonIfInProgress(candidate.getExamId()) == 1) {
                abandonedExamIds.add(candidate.getExamId());
                log.info(
                        "이전 시험 세션 폐기 완료 event=exam.session.abandoned "
                                + "outcome=transitioned examId={} "
                                + "fromStatus=IN_PROGRESS toStatus=ABANDONED reason=new_session_started",
                        candidate.getExamId()
                );
            }
        }
        return List.copyOf(abandonedExamIds);
    }

    private List<ExamSession> findInProgressSessions(String userId) {
        List<ExamSession> candidates = examSessionRepository.findActiveOrLegacyCandidatesByUserId(userId);
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<ExamSession> reusable = new ArrayList<>();
        for (ExamSession candidate : candidates) {
            if (candidate.getStatus() != null) {
                if (candidate.isInProgress() && candidate.getCompletedAt() == null) {
                    reusable.add(candidate);
                }
                continue;
            }
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
                log.warn("레거시 시험 세션에 완료 근거가 있지만 신뢰할 수 있는 완료 시각이 없어 "
                        + "재사용하거나 보정하지 않습니다: examId={}", candidate.getExamId());
                continue;
            }

            long updated = isLegacyExplicitActive(candidate)
                    ? examSessionRepository.backfillLegacyActiveCompletionIfUnchanged(
                            candidate.getExamId(), evidence.completedAt())
                    : examSessionRepository.backfillLegacyCompletionIfUnchanged(
                            candidate.getExamId(), evidence.completedAt());
            if (updated == 0) {
                log.debug("레거시 시험 세션 완료 시각 보정이 이미 적용됐거나 세션 상태가 변경되었습니다: examId={}",
                        candidate.getExamId());
            } else if (evidence.approximateTimestamp()) {
                log.warn("레거시 시험 세션 완료 시각에 createdAt을 근사값으로 사용했습니다: examId={}",
                        candidate.getExamId());
            }
        }

        if (reusable.size() > 1) {
            log.warn("한 사용자에게 진행 중인 시험 세션이 여러 개 있어 모두 폐기합니다");
        }
        return List.copyOf(reusable);
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
