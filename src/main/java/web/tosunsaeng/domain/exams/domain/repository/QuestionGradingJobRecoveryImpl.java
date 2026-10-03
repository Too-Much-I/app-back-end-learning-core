package web.tosunsaeng.domain.exams.domain.repository;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Update;
import web.tosunsaeng.domain.exams.domain.entity.QuestionGradingJob;

import java.time.Instant;
import java.util.List;

public class QuestionGradingJobRecoveryImpl implements QuestionGradingJobRecovery {
    private final MongoTemplate mongo;

    public QuestionGradingJobRecoveryImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public long reopenCompletedMissingResult(String jobId, int expectedRecoveryCycle, Instant pendingAt) {
        var query = new BasicQuery(new Document("_id", jobId).append("status", "COMPLETED")
                .append("$expr", new Document("$eq", List.of(
                        new Document("$ifNull", List.of("$recoveryCycle", 0)), expectedRecoveryCycle))));
        // Use Update's field tracking: BasicUpdate from @Update loses $inc fields when @Version is added.
        // Set the CAS-validated successor so explicit null legacy cycles also recover as cycle zero.
        var update = new Update().set("status", "PENDING").set("dispatchAttempt", 0)
                .set("pendingAt", pendingAt).set("processingStartedAt", null)
                .set("lastDispatchedAt", null).set("completedAt", null)
                .set("failedAt", null).set("failureReason", null)
                .set("recoveryCycle", Math.addExact(expectedRecoveryCycle, 1)).inc("version", 1);
        return mongo.updateFirst(query, update, QuestionGradingJob.class).getModifiedCount();
    }
}
