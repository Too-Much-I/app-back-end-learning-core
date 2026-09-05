package web.tosunsaeng.domain.usermerge.application;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.repository.ExamCreationOperationRepository;
import web.tosunsaeng.domain.usermerge.domain.UserMergedInboxEvent;
import web.tosunsaeng.domain.usermerge.domain.UserMergedInboxStatus;
import web.tosunsaeng.domain.usermerge.repository.UserMergedInboxRepository;
import web.tosunsaeng.domain.withdrawal.repository.WithdrawnUserAccessDenyRepository;

import java.util.Comparator;
import java.util.List;

public class UserMergedTransactionService {

    private final UserMergedInboxRepository inboxRepository;
    private final WithdrawnUserAccessDenyRepository withdrawalRepository;
    private final ExamCreationOperationRepository operationRepository;
    private final UserOwnershipGuardService guardService;
    private final UserOwnedTransactionExecutor transactionExecutor;
    private final MongoTemplate mongoTemplate;

    public UserMergedTransactionService(
            UserMergedInboxRepository inboxRepository,
            WithdrawnUserAccessDenyRepository withdrawalRepository,
            ExamCreationOperationRepository operationRepository,
            UserOwnershipGuardService guardService,
            UserOwnedTransactionExecutor transactionExecutor,
            MongoTemplate mongoTemplate
    ) {
        this.inboxRepository = inboxRepository;
        this.withdrawalRepository = withdrawalRepository;
        this.operationRepository = operationRepository;
        this.guardService = guardService;
        this.transactionExecutor = transactionExecutor;
        this.mongoTemplate = mongoTemplate;
    }

    public UserMergedConsumeResult consume(NormalizedUserMergedEvent event) {
        try {
            return transactionExecutor.executeWithoutGuard(() -> consumeInTransaction(event));
        } catch (UserOwnershipGuardException conflict) {
            throw new UserMergedEventException(
                    UserMergedEventException.Reason.LIFECYCLE_CONFLICT,
                    conflict
            );
        }
    }

    private UserMergedConsumeResult consumeInTransaction(NormalizedUserMergedEvent event) {
        UserMergedInboxEvent existing = inboxRepository.findById(event.eventId()).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == UserMergedInboxStatus.PROCESSED
                    && existing.getPayloadDigest().equals(event.payloadDigest())) {
                return UserMergedConsumeResult.DUPLICATE;
            }
            throw new UserMergedEventException(
                    UserMergedEventException.Reason.PAYLOAD_CONFLICT
            );
        }

        if (activeWithdrawal(event.sourceUserId(), event)
                || activeWithdrawal(event.targetUserId(), event)) {
            throw new UserMergedEventException(
                    UserMergedEventException.Reason.LIFECYCLE_CONFLICT
            );
        }

        if (hasNonTerminalOperation(event.sourceUserId())
                || hasNonTerminalOperation(event.targetUserId())) {
            throw new UserMergedEventException(
                    UserMergedEventException.Reason.RETRYABLE_PRECONDITION
            );
        }

        List<String> guardOrder = List.of(event.sourceUserId(), event.targetUserId()).stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        for (String userId : guardOrder) {
            guardService.touchActive(userId, event.receivedAt());
        }

        List<ExamSession> sourceActive = findEffectiveActiveSessions(event.sourceUserId());
        List<ExamSession> targetActive = findEffectiveActiveSessions(event.targetUserId());
        if (!sourceActive.isEmpty() && !targetActive.isEmpty()) {
            List<String> sourceActiveIds = sourceActive.stream()
                    .map(ExamSession::getExamId)
                    .toList();
            mongoTemplate.updateMulti(
                    Query.query(Criteria.where("_id").in(sourceActiveIds)
                            .and("userId").is(event.sourceUserId())),
                    new Update()
                            .set("status", ExamSessionStatus.ABANDONED)
                            .set("active", false),
                    ExamSession.class
            );
        }

        migrateOwner(ExamResult.class, event.sourceUserId(), event.targetUserId());
        migrateOwner(ExamSummary.class, event.sourceUserId(), event.targetUserId());
        migrateOwner(ExamSession.class, event.sourceUserId(), event.targetUserId());

        guardService.markMerged(
                event.sourceUserId(),
                event.targetUserId(),
                event.eventId(),
                event.occurredAt(),
                event.receivedAt()
        );

        inboxRepository.insert(new UserMergedInboxEvent(
                event.eventId(),
                event.schemaVersion(),
                event.payloadDigest(),
                event.sourceUserId(),
                event.targetUserId(),
                event.occurredAt(),
                event.receivedAt(),
                event.receivedAt(),
                UserMergedInboxStatus.PROCESSED
        ));
        return UserMergedConsumeResult.PROCESSED;
    }

    private boolean activeWithdrawal(String userId, NormalizedUserMergedEvent event) {
        return withdrawalRepository.findById(userId)
                .filter(marker -> marker.isActiveAt(event.receivedAt()))
                .isPresent();
    }

    private boolean hasNonTerminalOperation(String userId) {
        ExamCreationOperation operation = operationRepository
                .findByUserIdAndActiveGuardTrue(userId)
                .orElse(null);
        return operation != null && !operation.isTerminal();
    }

    private List<ExamSession> findEffectiveActiveSessions(String userId) {
        Criteria modern = Criteria.where("status").in(
                ExamSessionStatus.IN_PROGRESS,
                ExamSessionStatus.ENTITLEMENT_CONFIRMING
        );
        Criteria legacy = new Criteria().andOperator(
                new Criteria().orOperator(
                        Criteria.where("status").is(null),
                        Criteria.where("status").exists(false)
                ),
                new Criteria().orOperator(
                        Criteria.where("active").is(true),
                        Criteria.where("active").is(null),
                        Criteria.where("active").exists(false)
                ),
                new Criteria().orOperator(
                        Criteria.where("completedAt").is(null),
                        Criteria.where("completedAt").exists(false)
                )
        );
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("userId").is(userId),
                new Criteria().orOperator(modern, legacy)
        ));
        return mongoTemplate.find(query, ExamSession.class);
    }

    private void migrateOwner(Class<?> entityType, String sourceUserId, String targetUserId) {
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("userId").is(sourceUserId)),
                Update.update("userId", targetUserId),
                entityType
        );
    }
}
