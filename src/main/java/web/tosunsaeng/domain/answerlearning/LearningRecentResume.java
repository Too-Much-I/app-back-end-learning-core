package web.tosunsaeng.domain.answerlearning;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import java.time.Instant;
import java.util.*;
import static web.tosunsaeng.domain.answerlearning.LearningModels.Submission;

/** Read-only CTA projection ordered by persisted grading results. */
final class LearningRecentResume {
    private final MongoTemplate mongo;
    private final LearningProperties config;
    LearningRecentResume(MongoTemplate mongo, LearningProperties config) { this.mongo=mongo; this.config=config; }
    private Criteria current() {
        return Criteria.where("datasetId").is(config.getDatasetId()).and("contentVersion").is(config.getContentVersion());
    }
    private Criteria owned(String owner, String level) {
        return Criteria.where("userId").is(owner).and("level").is(level).and("contentVersion").is(config.getContentVersion());
    }
    private record Position(int part, String unitId, int unitNumber, String lessonId, int questionNumber, int lessonNumber) {}
    private record Practice(String lessonId, Instant at) {}

    Document resume(String owner, String level) {
        if (!Set.of("IM3","IH","AL").contains(level == null ? "" : level)) throw LearningFailure.bad();
        Query unitQuery = Query.query(current().and("publicationStatus").is("ready").and("part").in(1,2,3,4,5))
                .with(Sort.by("part","sortOrder","_id")).limit(501);
        unitQuery.fields().include("_id", "part", "lessonIds");
        List<Document> units = mongo.find(unitQuery, Document.class, "learning_units");
        if (units.isEmpty()) throw LearningFailure.missing();
        if (units.size() > 500) throw LearningFailure.unavailable();
        List<String> ids = new ArrayList<>();
        Set<String> unitIds = new HashSet<>();
        for (Document unit : units) {
            List<String> lessons = unit.getList("lessonIds",String.class);
            Integer part = unit.getInteger("part");
            if (part == null || part < 1 || part > 5 || unit.getString("_id") == null
                    || !unitIds.add(unit.getString("_id")) || lessons == null || lessons.isEmpty()
                    || lessons.stream().anyMatch(Objects::isNull)) throw LearningFailure.unavailable();
            ids.addAll(lessons);
        }
        if (new HashSet<>(ids).size() != ids.size()) throw LearningFailure.unavailable();
        Map<String,Document> lessons = new HashMap<>();
        Query lessonQuery = Query.query(current().and("_id").in(ids));
        lessonQuery.fields().include("_id", "unitId", "part", "questionNumber");
        for (Document lesson : mongo.find(lessonQuery,Document.class,"learning_lessons")) {
            if (lessons.put(lesson.getString("_id"),lesson) != null) throw LearningFailure.unavailable();
        }
        List<Position> positions = new ArrayList<>();
        Map<Integer,Integer> unitNumbers = new HashMap<>();
        for (Document unit : units) {
            int part = unit.getInteger("part"), unitNumber = unitNumbers.merge(part,1,Integer::sum), lessonNumber = 0;
            if (unitNumber > 100) throw LearningFailure.unavailable();
            for (String id : unit.getList("lessonIds",String.class)) {
                Document lesson = lessons.get(id);
                if (lesson == null || !unit.getString("_id").equals(lesson.getString("unitId"))
                        || !Objects.equals(part,lesson.getInteger("part")) || lesson.getInteger("questionNumber") == null)
                    throw LearningFailure.unavailable();
                positions.add(new Position(part,unit.getString("_id"),unitNumber,id,lesson.getInteger("questionNumber"),++lessonNumber));
            }
        }
        List<String> spoken = positions.stream().filter(p -> p.part()!=1).map(Position::lessonId).toList();
        Query latestQuery = Query.query(owned(owner,level)
                .and("lessonId").in(spoken).and("status").is("completed").and("result").ne(null).and("updatedAt").ne(null))
                .with(Sort.by(Sort.Order.desc("updatedAt"),Sort.Order.desc("_id")));
        latestQuery.fields().include("lessonId", "updatedAt").exclude("_id");
        Submission latest = spoken.isEmpty() ? null : mongo.findOne(latestQuery, Submission.class);
        Practice practice = latest == null ? null : new Practice(latest.lessonId,latest.updatedAt);
        List<String> common = positions.stream().filter(p -> p.part()==1).map(Position::lessonId).toList();
        if (!common.isEmpty()) {
            Query completionQuery = Query.query(owned(owner,"common").and("lessonId").in(common));
            completionQuery.fields().include("lessonId", "lastPracticedAt", "completedAt").exclude("_id");
            for (Document row : mongo.find(completionQuery,Document.class,"learning_completions")) {
                Date date = row.getDate("lastPracticedAt");
                if (date == null) date = row.getDate("completedAt"); // Existing Part 1 first-completion evidence.
                if (date == null) throw LearningFailure.unavailable();
                Practice candidate = new Practice(row.getString("lessonId"),date.toInstant());
                if (practice == null || candidate.at().isAfter(practice.at())
                        || (candidate.at().equals(practice.at()) && candidate.lessonId().compareTo(practice.lessonId()) > 0)) practice=candidate;
            }
        }
        if (practice == null) return response("NO_HISTORY",level,positions.getFirst());
        for (int i=0;i<positions.size();i++) {
            Position previous=positions.get(i);
            if (!previous.lessonId().equals(practice.lessonId())) continue;
            Position next=i+1<positions.size()?positions.get(i+1):null;
            return response("READY",level,next!=null ? next : positions.getFirst());
        }
        throw LearningFailure.unavailable();
    }
    private Document response(String status, String level, Position p) {
        return new Document("status",status).append("level",level).append("part",p==null?null:p.part())
                .append("unitId",p==null?null:p.unitId()).append("unitNumber",p==null?null:p.unitNumber())
                .append("lessonId",p==null?null:p.lessonId()).append("lessonNumber",p==null?null:p.lessonNumber())
                .append("questionNumber",p==null?null:p.questionNumber());
    }
}
