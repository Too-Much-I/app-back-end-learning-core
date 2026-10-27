package web.tosunsaeng.domain.answerlearning;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import java.time.Instant;

/** Called inside UserMerged's transaction after both owner guards have been acquired. */
public final class LearningOwnership {
    private LearningOwnership() {}
    public static void merge(MongoTemplate mongo, String source, String target, Instant now) {
        // Completed submissions retain their result time; unfinished uploads retain the old refresh behavior.
        mongo.updateMulti(Query.query(Criteria.where("userId").is(source).and("status").ne("completed")),
                new Update().set("updatedAt", now), "learning_submissions");
        mongo.updateMulti(Query.query(Criteria.where("userId").is(source)),
                new Update().set("userId", target).unset("leaseToken").set("leaseUntil", Instant.EPOCH), "learning_submissions");
        // Completion IDs are opaque. Keep the earliest proof when both accounts finished the same lesson.
        for (Document row : mongo.find(Query.query(Criteria.where("userId").is(source)), Document.class, "learning_completions")) {
            Query key = LearningStore.completionKey(target, row.getString("lessonId"), row.getString("level"), row.getString("contentVersion"));
            Document existing = mongo.findOne(key, Document.class, "learning_completions");
            if (existing == null) {
                mongo.updateFirst(Query.query(Criteria.where("_id").is(row.get("_id")).and("userId").is(source)),
                        Update.update("userId", target), "learning_completions");
            } else {
                java.util.Date practiced = row.getDate("lastPracticedAt");
                if (practiced == null && "common".equals(row.getString("level"))) practiced = row.getDate("completedAt");
                java.util.Date targetPractice = existing.getDate("lastPracticedAt");
                if (targetPractice == null && "common".equals(existing.getString("level"))) targetPractice = existing.getDate("completedAt");
                if (targetPractice != null && (practiced == null || targetPractice.after(practiced))) practiced = targetPractice;
                if (practiced != null) mongo.updateFirst(key, new Update().max("lastPracticedAt", practiced), "learning_completions");
                if (row.getDate("completedAt").before(existing.getDate("completedAt"))) {
                    mongo.updateFirst(key, new Update().set("completedAt", row.getDate("completedAt"))
                            .set("submissionId", row.get("submissionId")), "learning_completions");
                }
                mongo.remove(Query.query(Criteria.where("_id").is(row.get("_id")).and("userId").is(source)), "learning_completions");
            }
        }
    }
}
