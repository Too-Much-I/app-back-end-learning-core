package web.tosunsaeng.domain.exams.attemptgroup.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupPublisherState;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AttemptGroupPublisherStateStore {
    private final MongoTemplate mongoTemplate;

    public Gate acquireGate(Instant now, Duration probeInterval, Duration leaseDuration) {
        AttemptGroupPublisherState state = mongoTemplate.findById(
                AttemptGroupPublisherState.SINGLETON_ID, AttemptGroupPublisherState.class);
        if (state == null) {
            try {
                mongoTemplate.insert(AttemptGroupPublisherState.open(now));
                return Gate.OPEN;
            } catch (DuplicateKeyException race) {
                state = mongoTemplate.findById(
                        AttemptGroupPublisherState.SINGLETON_ID, AttemptGroupPublisherState.class);
            }
        }
        if (state == null || !state.isAuthBlocked()) {
            return Gate.OPEN;
        }
        if (state.getNextAuthProbeAt() != null && now.isBefore(state.getNextAuthProbeAt())) {
            return Gate.BLOCKED;
        }

        String token = UUID.randomUUID().toString();
        Criteria probeDue = new Criteria().orOperator(
                Criteria.where("nextAuthProbeAt").lte(now),
                Criteria.where("nextAuthProbeAt").exists(false));
        Criteria leaseFree = new Criteria().orOperator(
                Criteria.where("probeLeaseUntil").lte(now),
                Criteria.where("probeLeaseUntil").exists(false));
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(AttemptGroupPublisherState.SINGLETON_ID),
                Criteria.where("authBlocked").is(true),
                probeDue,
                leaseFree));
        Update update = new Update()
                .set("probeLeaseToken", token)
                .set("probeLeaseUntil", now.plus(leaseDuration))
                .set("nextAuthProbeAt", now.plus(probeInterval))
                .set("updatedAt", now);
        AttemptGroupPublisherState claimed = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true),
                AttemptGroupPublisherState.class);
        return claimed == null ? Gate.BLOCKED : Gate.PROBE;
    }

    public void block(Instant now, Duration probeInterval) {
        Query query = Query.query(Criteria.where("_id").is(AttemptGroupPublisherState.SINGLETON_ID));
        Update update = new Update()
                .set("authBlocked", true)
                .set("nextAuthProbeAt", now.plus(probeInterval))
                .set("updatedAt", now)
                .unset("probeLeaseToken")
                .unset("probeLeaseUntil");
        mongoTemplate.upsert(query, update, AttemptGroupPublisherState.class);
    }

    public void open(Instant now) {
        Query query = Query.query(Criteria.where("_id").is(AttemptGroupPublisherState.SINGLETON_ID));
        Update update = new Update()
                .set("authBlocked", false)
                .set("updatedAt", now)
                .unset("nextAuthProbeAt")
                .unset("probeLeaseToken")
                .unset("probeLeaseUntil");
        mongoTemplate.upsert(query, update, AttemptGroupPublisherState.class);
    }

    public enum Gate { OPEN, BLOCKED, PROBE }
}
