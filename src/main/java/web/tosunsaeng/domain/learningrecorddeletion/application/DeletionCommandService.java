package web.tosunsaeng.domain.learningrecorddeletion.application;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import web.tosunsaeng.domain.learningrecorddeletion.domain.*;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardService;
import java.time.Clock;
import java.util.UUID;

public class DeletionCommandService {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private web.tosunsaeng.domain.notification.NotificationStore notificationStore;
    private final MongoTemplate mongo;
    private final DeletionTransactions transactions;
    private final UserOwnershipGuardService guards;
    private final Clock clock;
    public DeletionCommandService(MongoTemplate mongo, DeletionTransactions transactions, UserOwnershipGuardService guards, Clock clock) {
        this.mongo = mongo; this.transactions = transactions; this.guards = guards; this.clock = clock;
    }
    public DeletionOperation request(String owner, String key) {
        final String commandId;
        try { commandId = DeletionIdentifiers.commandId(owner, key); }
        catch (IllegalArgumentException invalid) { throw new DeletionFailure(400, "LEARNING_RECORD_DELETION_INVALID_REQUEST"); }
        try {
            return transactions.run(() -> {
                requireOwner(owner);
                // Replay precedes active check, including a completed key from a previous operation.
                DeletionCommand replay = mongo.findById(commandId, DeletionCommand.class);
                if (replay != null) return operationFor(replay);
                if (mongo.exists(Query.query(Criteria.where("userId").is(owner).and("activeGuard").is(true)), DeletionOperation.class))
                    throw new DeletionFailure(409, "LEARNING_RECORD_DELETION_ALREADY_ACTIVE");
                var now = clock.instant();
                if (notificationStore != null) notificationStore.suppress(owner, now, "LEARNING_RECORD_DELETION");
                var operation = DeletionOperation.requested(UUID.randomUUID().toString(), owner, now);
                mongo.insert(operation);
                mongo.insert(DeletionCommand.create(owner, key, operation.getDeletionId(), now));
                return operation;
            });
        } catch (DeletionFailure e) { throw e; }
        catch (web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardException denied) {
            throw new DeletionFailure(403, "COMMON403");
        }
        catch (RuntimeException failure) {
            // Read outside the aborted/unknown transaction, using primary majority evidence.
            try {
                DeletionCommand committed = majorityById(commandId, DeletionCommand.class);
                if (committed != null) {
                    DeletionOperation operation = majorityById(committed.deletionId(), DeletionOperation.class);
                    if (operation != null && owner.equals(operation.getUserId()) && DeletionCommand.SEMANTIC_DIGEST.equals(committed.semanticDigest())) return operation;
                }
            } catch (RuntimeException unavailable) { /* Unknown is not absence or success. */ }
            throw DeletionFailure.temporary();
        }
    }
    public DeletionOperation latest(String owner) {
        DeletionIdentifiers.userId(owner);
        Document found = mongo.getCollection("learning_record_deletion_operations").withReadConcern(ReadConcern.MAJORITY)
                .withReadPreference(ReadPreference.primary()).find(new Document("userId", owner))
                .sort(new Document("requestedAt", -1).append("_id", -1)).first();
        return found == null ? null : mongo.getConverter().read(DeletionOperation.class, found);
    }
    private void requireOwner(String owner) {
        guards.touchActive(owner, clock.instant());
        if (mongo.exists(Query.query(Criteria.where("_id").is(owner).and("blockedUntil").gt(clock.instant())), "withdrawn_user_access_denies"))
            throw new DeletionFailure(403, "COMMON403");
    }
    private DeletionOperation operationFor(DeletionCommand command) {
        if (!DeletionCommand.SEMANTIC_DIGEST.equals(command.semanticDigest())) throw new DeletionFailure(409, "LEARNING_RECORD_DELETION_IDEMPOTENCY_CONFLICT");
        DeletionOperation operation = mongo.findById(command.deletionId(), DeletionOperation.class);
        if (operation == null) throw DeletionFailure.temporary();
        return operation;
    }
    private <T> T majorityById(String id, Class<T> type) {
        Document found = mongo.getCollection(mongo.getCollectionName(type)).withReadConcern(ReadConcern.MAJORITY)
                .withReadPreference(ReadPreference.primary()).find(new Document("_id", id)).first();
        return found == null ? null : mongo.getConverter().read(type, found);
    }
}
