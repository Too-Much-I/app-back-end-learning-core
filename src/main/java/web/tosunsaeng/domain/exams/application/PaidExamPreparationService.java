package web.tosunsaeng.domain.exams.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import web.tosunsaeng.domain.exams.domain.entity.*;
import web.tosunsaeng.domain.exams.domain.repository.ExamCreationOperationRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionCompletionQuery;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.util.*;

import static web.tosunsaeng.domain.exams.domain.entity.PaidExamSelection.StartReason.*;

/** P1 preparation only. Deliberately not routed from the public saga until P2/P3 are ready. */
@Service
public class PaidExamPreparationService {
    private final MongoTemplate mongo;
    private final ExamCreationOperationRepository operations;
    private final MockExamCatalogService catalog;
    private final ExamSessionCompletionQuery completionCounts;
    private final ExamSubmissionEvidence evidence;
    private final ObjectProvider<UserOwnedTransactionExecutor> owners;
    private final Clock clock;

    public PaidExamPreparationService(MongoTemplate mongo, ExamCreationOperationRepository operations,
            MockExamCatalogService catalog, ExamSessionCompletionQuery completionCounts,
            ExamSubmissionEvidence evidence, ObjectProvider<UserOwnedTransactionExecutor> owners, Clock clock) {
        this.mongo = mongo; this.operations = operations; this.catalog = catalog;
        this.completionCounts = completionCounts; this.evidence = evidence; this.owners = owners; this.clock = clock;
    }

    public ExamCreationOperation prepare(String userId, String operationId) {
        if (operationId == null || !operationId.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("A lowercase UUID v4 operation key is required");
        UserOwnedTransactionExecutor owner = owners.getIfAvailable();
        if (owner == null || !owner.enabled()) throw new IllegalStateException("Paid preparation requires owner fencing");
        owner.requirePublicWrite(userId);
        return owner.execute(userId, () -> prepareInTransaction(userId, operationId, owner));
    }

    private ExamCreationOperation prepareInTransaction(String userId, String operationId, UserOwnedTransactionExecutor owner) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Paid preparation requires a Mongo transaction");
        owner.requirePublicWrite(userId);
        if (mongo.exists(Query.query(Criteria.where("_id").is(userId).and("blockedUntil").gt(Date.from(clock.instant()))),
                "withdrawn_user_access_denies")) throw new IllegalStateException("Owner is withdrawn");
        ExamCreationOperation existing = operations.findByUserIdAndOperationId(userId, operationId).orElse(null);
        if (existing != null) return existing;
        if (operations.findByUserIdAndActiveGuardTrue(userId).isPresent())
            throw new ExamsException(ErrorStatus._EXAM_CREATION_PROCESSING, 1);
        // Deleted and phone continuation selection belong to P2, not an inferred free INITIAL.
        if (mongo.exists(Query.query(Criteria.where("userId").is(userId)), "learning_record_billing_continuations"))
            throw new IllegalStateException("Deleted continuation requires the P2 contract");
        ExamSession previous = mongo.findOne(Query.query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "createdAt", "_id")), ExamSession.class);
        PaidExamSelection.StartReason reason = null;
        if (previous != null) {
            if (!previous.isInProgress() && !previous.isCompleted() && !previous.isRetakeAvailable())
                throw new IllegalStateException("Previous Session lifecycle cannot start a paid exam");
            boolean allAccepted = evidence.completeAndFence(previous);
            if (previous.isCompleted()) {
                if (!allAccepted) throw new IllegalStateException("Completion and admission evidence disagree");
                reason = NEW_AFTER_COMPLETION;
            } else reason = allAccepted ? NEW_AFTER_SUBMISSION : RESTART_INCOMPLETE;
        }
        Map<String, Long> counts = new HashMap<>();
        for (var count : completionCounts.countCompletedByMockExamId(userId))
            counts.merge(GradingKeys.effectiveMockExamId(count.mockExamId()), count.completionCount(), Long::sum);
        MockExam selected;
        if (reason == RESTART_INCOMPLETE) {
            selected = catalog.getRequiredExam(previous.getMockExamId());
        } else {
            List<MockExamCatalogService.CatalogExam> candidates = catalog.findAssignableExams();
            if (reason == NEW_AFTER_SUBMISSION) {
                String previousMock = previous.getMockExamId();
                var other = candidates.stream().filter(c -> !previousMock.equals(c.mockExam().getMockExamId())).toList();
                if (!other.isEmpty()) candidates = other;
            }
            selected = candidates.stream().min(Comparator
                    .comparingLong((MockExamCatalogService.CatalogExam c) -> counts.getOrDefault(c.mockExam().getMockExamId(), 0L))
                    .thenComparingInt(MockExamCatalogService.CatalogExam::sequence)).orElseThrow().mockExam();
        }
        List<Integer> required = ExamSubmissionEvidence.required(selected.getQuestions().stream()
                .filter(Objects::nonNull).map(Question::getQuestionNumber)
                .filter(n -> n != null && n > 0).toList());
        int cycle = reason == RESTART_INCOMPLETE && previous.getCycleNumber() != null ? previous.getCycleNumber()
                : Math.toIntExact(counts.getOrDefault(selected.getMockExamId(), 0L) + 1);
        ExamCreationOperation operation = ExamCreationOperation.prepared(userId, operationId,
                "ex_" + UUID.randomUUID().toString().replace("-", ""), selected.getMockExamId(), cycle,
                previous == null ? null : previous.getExamId(), previous == null ? null : previous.getAttemptGroupId(),
                previous == null ? null : previous.getMockExamId(), clock.instant());
        operation.fixPaidSelection(new PaidExamSelection(reason, required));
        return operations.insert(operation);
    }
}
