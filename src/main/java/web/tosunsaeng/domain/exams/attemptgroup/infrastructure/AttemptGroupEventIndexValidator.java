package web.tosunsaeng.domain.exams.attemptgroup.infrastructure;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupEventOutbox;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${app.attempt-group-events.writer-enabled:false} or ${app.attempt-group-events.publisher-enabled:false}")
public class AttemptGroupEventIndexValidator {
    private final MongoTemplate mongoTemplate;

    @PostConstruct
    void ensureRequiredIndexes() {
        try {
            IndexOperations indexes = mongoTemplate.indexOps(AttemptGroupEventOutbox.class);
            indexes.ensureIndex(new Index()
                    .on("sessionId", Sort.Direction.ASC)
                    .on("eventSlot", Sort.Direction.ASC)
                    .unique()
                    .named("uq_attempt_group_session_slot"));
            indexes.ensureIndex(new Index()
                    .on("status", Sort.Direction.ASC)
                    .on("nextAttemptAt", Sort.Direction.ASC)
                    .on("leaseUntil", Sort.Direction.ASC)
                    .on("_id", Sort.Direction.ASC)
                    .named("ix_attempt_group_claim"));
            indexes.ensureIndex(new Index()
                    .on("expiresAt", Sort.Direction.ASC)
                    .expire(0)
                    .named("ttl_attempt_group_outbox"));
            var actual = indexes.getIndexInfo();
            verify(actual, "_id_", java.util.List.of("_id"), true, false);
            verify(actual, "uq_attempt_group_session_slot", java.util.List.of("sessionId", "eventSlot"), true, false);
            verify(actual, "ix_attempt_group_claim", java.util.List.of("status", "nextAttemptAt", "leaseUntil", "_id"), false, false);
            verify(actual, "ttl_attempt_group_outbox", java.util.List.of("expiresAt"), false, true);
            // Provision an empty collection through the existing index API, never a business/state document.
            IndexOperations state = mongoTemplate.indexOps(web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupPublisherState.class);
            state.ensureIndex(new Index().on("_id", Sort.Direction.ASC).named("_id_"));
            verify(state.getIndexInfo(), "_id_", java.util.List.of("_id"), true, false);
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("AttemptGroup required index validation failed");
        }
    }

    private static void verify(java.util.List<org.springframework.data.mongodb.core.index.IndexInfo> indexes,
                               String name, java.util.List<String> fields, boolean unique, boolean ttl) {
        var index = indexes.stream().filter(i -> name.equals(i.getName())).findFirst().orElseThrow();
        // Mongo reports the intrinsic _id uniqueness without always emitting unique:true.
        if ((!"_id_".equals(name) && index.isUnique() != unique) || index.isSparse() || index.isHidden()
                || index.getCollation().filter(c -> !"simple".equals(c.getString("locale"))).isPresent()
                || index.getPartialFilterExpression() != null
                || !index.getIndexFields().stream().map(f -> f.getKey()).toList().equals(fields)
                || index.getIndexFields().stream().anyMatch(f -> f.getDirection() != Sort.Direction.ASC)
                || (ttl ? !java.time.Duration.ZERO.equals(index.getExpireAfter().orElse(null)) : index.getExpireAfter().isPresent()))
            throw new IllegalStateException("Required index mismatch");
    }
}
