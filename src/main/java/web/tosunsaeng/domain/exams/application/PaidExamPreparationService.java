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

/** Owner-fenced selection for the default-off paid saga. No network calls inside transactions. */
@Service
public class PaidExamPreparationService {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private web.tosunsaeng.domain.learningrecorddeletion.infrastructure.DeletedExamContinuationStore deletedSources;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private web.tosunsaeng.domain.exams.rollout.LegacyExamRuntime legacyRuntime;
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
        return prepare(userId, operationId, null);
    }

    /** Authorize before phone discovery; preparation repeats the fence after the remote call. */
    public void requireDiscoveryAllowed(String userId) {
        var owner = owners.getIfAvailable();
        if (owner == null || !owner.enabled()) throw new IllegalStateException("Paid preparation requires owner fencing");
        owner.execute(userId, () -> { owner.requirePublicWrite(userId); return null; });
    }

    public ExamCreationOperation prepare(String userId, String operationId,
            web.tosunsaeng.domain.exams.billing.BillingReservationClient.PhoneContinuationSnapshot phone) {
        if (operationId == null || !operationId.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("A lowercase UUID v4 operation key is required");
        UserOwnedTransactionExecutor owner = owners.getIfAvailable();
        if (owner == null || !owner.enabled()) throw new IllegalStateException("Paid preparation requires owner fencing");
        owner.requirePublicWrite(userId);
        return owner.execute(userId, () -> prepareInTransaction(userId, operationId, owner, phone));
    }

    private ExamCreationOperation prepareInTransaction(String userId, String operationId, UserOwnedTransactionExecutor owner,
            web.tosunsaeng.domain.exams.billing.BillingReservationClient.PhoneContinuationSnapshot phone) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Paid preparation requires a Mongo transaction");
        owner.requirePublicWrite(userId);
        if (mongo.exists(Query.query(Criteria.where("_id").is(userId).and("blockedUntil").gt(Date.from(clock.instant()))),
                "withdrawn_user_access_denies")) throw new IllegalStateException("Owner is withdrawn");
        ExamCreationOperation existing = operations.findByUserIdAndOperationId(userId, operationId).orElse(null);
        if (existing != null) return existing;
        if (operations.findByUserIdAndActiveGuardTrue(userId).isPresent())
            throw new ExamsException(ErrorStatus._EXAM_CREATION_PROCESSING, 1);
        ExamSession previous = mongo.findOne(Query.query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "createdAt", "_id")), ExamSession.class);
        web.tosunsaeng.domain.learningrecorddeletion.domain.DeletedExamContinuation deleted = null;
        if (previous == null && mongo.exists(Query.query(Criteria.where("userId").is(userId)),"learning_record_billing_continuations")) {
            if (deletedSources == null) throw new IllegalStateException("Deletion evidence reader unavailable");
            deleted = deletedSources.findPaidSource(userId).orElse(null);
        }
        if (phone != null && (previous != null || deleted != null)) throw new IllegalStateException("Phone source changed during discovery");
        PaidExamSelection.StartReason reason = null;
        web.tosunsaeng.domain.exams.rollout.LegacyExamManifest.Summary legacySource = null;
        if (previous != null) {
            if (previous.getAttemptGroupId() == null) {
                if (legacyRuntime == null) throw new IllegalStateException("Verified legacy inventory unavailable");
                legacySource = legacyRuntime.requireLegacy(previous);
            }
            if (previous.isEntitlementConfirming() || (legacySource == null
                    && !previous.isInProgress() && !previous.isCompleted() && !previous.isRetakeAvailable()))
                throw new IllegalStateException("Previous Session lifecycle cannot start a paid exam");
            if (previous.getSubmissionCompletedAt()!=null && previous.getSubmissionCompletedAt().isAfter(clock.instant()))
                throw new IllegalStateException("Submission evidence is in the future");
            boolean allAccepted = legacySource != null ? evidence.legacyCompleteAndFence(previous) : evidence.completeAndFence(previous);
            if (previous.isCompleted()) {
                if (!allAccepted && legacySource == null) throw new IllegalStateException("Completion and admission evidence disagree");
                reason = NEW_AFTER_COMPLETION;
            } else reason = allAccepted ? NEW_AFTER_SUBMISSION : RESTART_INCOMPLETE;
        }
        if (deleted != null) reason = deleted.getPaidStartReason();
        Map<String, Long> counts = new HashMap<>();
        for (var count : completionCounts.countCompletedByMockExamId(userId))
            counts.merge(GradingKeys.effectiveMockExamId(count.mockExamId()), count.completionCount(), Long::sum);
        MockExam selected;
        if (phone != null) {
            selected = catalog.getRequiredExam(phone.mockExamId());
        } else if (reason == RESTART_INCOMPLETE) {
            selected = catalog.getRequiredExam(previous != null ? previous.getMockExamId() : deleted.getMockExamId());
        } else {
            List<MockExamCatalogService.CatalogExam> candidates = catalog.findAssignableExams();
            if (reason == NEW_AFTER_SUBMISSION) {
                String previousMock = previous != null ? previous.getMockExamId() : deleted.getMockExamId();
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
        Integer priorCycle = previous != null ? previous.getCycleNumber() : deleted != null ? deleted.getCycleNumber() : null;
        int cycle = reason == RESTART_INCOMPLETE && priorCycle != null ? priorCycle
                : Math.toIntExact(counts.getOrDefault(selected.getMockExamId(), 0L) + 1);
        ExamCreationOperation operation = ExamCreationOperation.prepared(userId, operationId,
                "ex_" + UUID.randomUUID().toString().replace("-", ""), selected.getMockExamId(), cycle,
                previous != null ? previous.getExamId() : deleted != null ? deleted.getSourceExamId() : null,
                previous != null ? previous.getAttemptGroupId() : deleted != null ? deleted.getAttemptGroupId() : null,
                previous != null ? previous.getMockExamId() : deleted != null ? deleted.getMockExamId() : null, clock.instant());
        operation.fixPaidSelection(new PaidExamSelection(reason, required));
        if (legacySource != null) operation.fixPaidLegacySource(legacySource);
        if (phone != null) {
            operation = ExamCreationOperation.prepared(userId,operationId,operation.getSessionId(),selected.getMockExamId(),cycle,
                    null,phone.attemptGroupId(),phone.mockExamId(),phone.continuationReason(),phone.continuationId(),clock.instant());
            operation.fixPaidSelection(new PaidExamSelection(null,required));
            web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.phoneRequest(operation);
        }
        if (deleted != null) {
            operation.fixPaidDeletedSource(deletedSources.bindPaid(operation,deleted));
            deletedSources.claimPaid(operation);
        }
        return operations.insert(operation);
    }
}
