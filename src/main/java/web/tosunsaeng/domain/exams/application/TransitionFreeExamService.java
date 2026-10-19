package web.tosunsaeng.domain.exams.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import web.tosunsaeng.domain.exams.domain.entity.*;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.repository.*;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.domain.exams.rollout.LegacyExamRuntime;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import web.tosunsaeng.global.error.code.status.ErrorStatus;
import java.time.*;
import java.util.*;

/** Temporary free admission only. Shares owner/deletion/admission fences; never calls Billing. */
@Service
@RequiredArgsConstructor
public class TransitionFreeExamService {
    private final MongoTemplate mongo;
    private final ExamCreationOperationRepository operations;
    private final ExamSessionRepository sessions;
    private final ExamSessionCompletionQuery counts;
    private final MockExamCatalogService catalog;
    private final ExamSubmissionEvidence evidence;
    private final ObjectProvider<UserOwnedTransactionExecutor> owners;
    private final ObjectProvider<LegacyExamRuntime> runtimes;
    private final Clock clock;
    @Value("${app.billing.paid-exam.coexistence.legacy-create-enabled:false}")
    private boolean enabled;

    public ExamSessionManager.Assignment start(String userId) {
        var owner = owners.getIfAvailable();
        var runtime = runtimes.getIfAvailable();
        if (!enabled || owner == null || !owner.enabled() || runtime == null) throw blocked();
        return owner.execute(userId, () -> {
            owner.requirePublicWrite(userId);
            if (!TransactionSynchronizationManager.isActualTransactionActive()) throw blocked();
            if (operations.findByUserIdAndActiveGuardTrue(userId).isPresent()
                    || mongo.exists(Query.query(Criteria.where("_id").is(userId)
                        .and("blockedUntil").gt(Date.from(clock.instant()))), "withdrawn_user_access_denies")) throw blocked();
            var all = mongo.find(Query.query(Criteria.where("userId").is(userId))
                    .with(Sort.by(Sort.Direction.DESC, "createdAt", "active", "_id")), ExamSession.class);
            // Validate before making any closure. Old paid work must never be converted to free work.
            for (var session : all) {
                runtime.requireKnown(session);
                if (session.getPaidReservation() != null && (!session.isCompleted() && !session.isAbandoned()
                        || session.isActive() || session.isEntitlementConfirming())) throw blocked();
            }
            var previous = all.isEmpty() ? null : all.getFirst();
            boolean complete = false;
            if (previous != null && previous.getPaidReservation() == null) {
                if (previous.getSubmissionCompletedAt() != null && previous.getSubmissionCompletedAt().isAfter(clock.instant()))
                    throw blocked();
                complete = previous.isTransitionFree() ? evidence.completeAndFence(previous)
                        : evidence.legacyCompleteAndFence(previous);
                if (previous.isTransitionFree() && previous.isCompleted() && !complete) throw blocked();
                // Evidence touch increments the version; closure uses a fresh document.
                previous = mongo.findById(previous.getExamId(), ExamSession.class);
            }
            Map<String, Long> completed = new HashMap<>();
            for (var c : counts.countCompletedByMockExamId(userId))
                completed.merge(GradingKeys.effectiveMockExamId(c.mockExamId()), c.completionCount(), Long::sum);
            MockExam mock;
            if (previous != null && previous.getPaidReservation() == null && !previous.isCompleted() && !complete) {
                mock = catalog.getRequiredExam(previous.getMockExamId());
            } else {
                var candidates = catalog.findAssignableExams();
                if (previous != null && complete && !previous.isCompleted()) {
                    String priorMock = previous.getMockExamId();
                    var others = candidates.stream().filter(c -> !priorMock.equals(c.mockExam().getMockExamId())).toList();
                    if (!others.isEmpty()) candidates = others;
                }
                mock = candidates.stream().min(Comparator
                        .comparingLong((MockExamCatalogService.CatalogExam c) -> completed.getOrDefault(c.mockExam().getMockExamId(), 0L))
                        .thenComparingInt(MockExamCatalogService.CatalogExam::sequence)).orElseThrow().mockExam();
            }
            var required = ExamSubmissionEvidence.required(mock.getQuestions().stream().filter(Objects::nonNull)
                    .map(Question::getQuestionNumber).filter(n -> n != null && n > 0).toList());
            String examId = "ex_" + UUID.randomUUID().toString().replace("-", "");
            for (var observed : all) {
                var old = mongo.findById(observed.getExamId(), ExamSession.class);
                if (old.getPaidReservation() == null && old.isInProgress() && old.getSubmissionsClosedAt() == null) {
                    if (old.isTransitionFree()) old.closeSubmissions(examId, clock.instant());
                    else old.closeLegacySubmissions(runtime.requireLegacy(old), examId, clock.instant());
                    mongo.save(old);
                }
            }
            var fresh = ExamSession.builder().examId(examId).userId(userId)
                    .createdAt(LocalDateTime.ofInstant(clock.instant(), clock.getZone()))
                    .creationOrigin(ExamSession.TRANSITION_FREE_V1).mockExamId(mock.getMockExamId())
                    .cycleNumber(Math.toIntExact(completed.getOrDefault(mock.getMockExamId(), 0L) + 1))
                    .active(true).status(ExamSessionStatus.IN_PROGRESS)
                    .submissionEvidenceVersion(1).requiredQuestionNumbers(required).build();
            return new ExamSessionManager.Assignment(sessions.insert(fresh), mock, true);
        });
    }

    private static ExamsException blocked() { return new ExamsException(ErrorStatus._EXAM_CREATION_PROCESSING, 1); }
}
