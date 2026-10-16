package web.tosunsaeng.domain.exams.rollout;

import org.springframework.stereotype.Component;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.dao.DataAccessException;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

/** Read-only rollback defense, independent of feature flags. Not a distributed drain/sealing lock. */
@Component
public final class LegacyExamWriterGuard {
    private final MongoTemplate mongo;

    public LegacyExamWriterGuard(MongoTemplate mongo) { this.mongo = mongo; }

    public void requireAllowed() {
        try {
            // Any installed cutover state is a stop signal, including malformed/incomplete state.
            // Never delete/repair it or infer that OFF means the old writer is safe to restart.
            if (!mongo.exists(Query.query(Criteria.where("_id").is(PaidExamCutoverInspector.STATE_ID)),
                    PaidExamCutoverInspector.STATE)) return;
        } catch (DataAccessException unavailable) {
            // An unavailable check must not become an unbilled fallback.
        }
        throw new ExamsException(ErrorStatus._EXAM_CREATION_PROCESSING, 1);
    }
}
