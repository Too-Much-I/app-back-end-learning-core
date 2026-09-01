package web.tosunsaeng.domain.exams.attemptgroup.infrastructure;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupEventOutbox;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.attempt-group-events",
        name = "writer-enabled",
        havingValue = "true"
)
public class AttemptGroupEventIndexValidator {
    private final MongoTemplate mongoTemplate;

    @PostConstruct
    void ensureRequiredIndexes() {
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
    }
}
