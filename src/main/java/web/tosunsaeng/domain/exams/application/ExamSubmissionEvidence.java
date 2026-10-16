package web.tosunsaeng.domain.exams.application;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/** Durable admission evidence shared by exams and reminders. No notification flag or remote call. */
@Service
public class ExamSubmissionEvidence {
    public static final String RECEIPTS = "exam_submission_receipts";
    private final MongoTemplate mongo;

    public ExamSubmissionEvidence(MongoTemplate mongo) { this.mongo = mongo; }

    public boolean covered(ExamSession session) {
        if (session != null && session.getSubmissionEvidenceVersion() != null
                && !Integer.valueOf(1).equals(session.getSubmissionEvidenceVersion()))
            throw new IllegalStateException("Unsupported submission evidence version");
        return session != null && Integer.valueOf(1).equals(session.getSubmissionEvidenceVersion());
    }

    public boolean covered(String examId) {
        return covered(mongo.findById(examId, ExamSession.class));
    }

    public static List<Integer> required(List<Integer> numbers) {
        if (numbers == null || numbers.isEmpty() || numbers.stream().anyMatch(n -> n == null || n < 1)) {
            throw new IllegalStateException("Required submission snapshot is missing or invalid");
        }
        return numbers.stream().distinct().sorted().toList();
    }

    /** Called only after inserting a new, actual user-submitted Job, inside the same transaction. */
    public void accepted(String examId, int question, int retry, List<Integer> legacyRequired, Instant now) {
        if (retry != 0) return;
        requireTransaction();
        ExamSession session = mongo.findById(examId, ExamSession.class);
        if (session == null) throw new IllegalStateException("Submission Session is missing");
        if (session.isAccessRevoked() || session.getSubmissionsClosedAt()!=null || session.isEntitlementConfirming())
            throw new IllegalStateException("Session admission is closed");
        List<Integer> expected = required(covered(session) ? session.getRequiredQuestionNumbers() : legacyRequired);
        if (!expected.contains(question)) throw new IllegalStateException("Question is outside the submission snapshot");
        if (session.getSubmissionCompletedAt() != null) return;
        touch(session);
        mongo.upsert(Query.query(Criteria.where("_id").is(examId + ":" + question)), new Update()
                .setOnInsert("examId", examId).setOnInsert("questionNumber", question)
                .setOnInsert("acceptedAt", Date.from(now)), RECEIPTS);
        var receipts = mongo.find(Query.query(Criteria.where("examId").is(examId)
                .and("questionNumber").in(expected)), Document.class, RECEIPTS);
        if (receipts.stream().map(d -> d.getInteger("questionNumber")).distinct().count() != expected.size()) return;
        Instant last = receipts.stream().map(d -> d.getDate("acceptedAt").toInstant())
                .max(Instant::compareTo).orElseThrow();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(examId).and("submissionCompletedAt").is(null)),
                new Update().set("submissionCompletedAt", last).inc("version", 1L), ExamSession.class);
        mongo.updateMulti(Query.query(Criteria.where("examId").is(examId)),
                Update.update("expiresAt", Date.from(last.plus(Duration.ofDays(3)))), RECEIPTS);
    }

    /** Read-only evidence lookup; a result or an S3 object alone is never proof of admission. */
    public boolean acceptedQuestion(ExamSession session,int question,int retry) {
        if (retry!=0 || !covered(session) || !required(session.getRequiredQuestionNumbers()).contains(question)) return false;
        if (session.getSubmissionCompletedAt()!=null)
            return session.getSubmissionsClosedAt()==null || !session.getSubmissionCompletedAt().isAfter(session.getSubmissionsClosedAt());
        Document receipt=mongo.findById(session.getExamId()+":"+question,Document.class,RECEIPTS);
        if (receipt==null || !session.getExamId().equals(receipt.getString("examId"))
                || !Integer.valueOf(question).equals(receipt.getInteger("questionNumber")) || receipt.getDate("acceptedAt")==null) return false;
        return session.getSubmissionsClosedAt()==null || !receipt.getDate("acceptedAt").toInstant().isAfter(session.getSubmissionsClosedAt());
    }

    /** Exact existing receipt only; legacy same-mock fallback never manufactures admission evidence. */
    public boolean legacyAcceptedQuestion(ExamSession session,int question,int retry) {
        if (session==null || !session.hasLegacyClosure() || retry!=0) return false;
        Document receipt=mongo.findById(session.getExamId()+":"+question,Document.class,RECEIPTS);
        return receipt!=null && session.getExamId().equals(receipt.getString("examId"))
                && Integer.valueOf(question).equals(receipt.getInteger("questionNumber")) && receipt.getDate("acceptedAt")!=null
                && !receipt.getDate("acceptedAt").toInstant().isAfter(session.getSubmissionsClosedAt());
    }

    /** Same Session write fence as admission; caller must also hold the current owner guard. */
    public boolean completeAndFence(ExamSession session) {
        requireTransaction();
        if (!covered(session)) throw new IllegalStateException("Submission coverage is not proven");
        required(session.getRequiredQuestionNumbers());
        touch(session);
        return session.getSubmissionCompletedAt() != null;
    }

    /** Caller must first verify exact legacy membership. Missing evidence means same mock, not completion. */
    public boolean legacyCompleteAndFence(ExamSession session) {
        requireTransaction();
        if (covered(session)) required(session.getRequiredQuestionNumbers());
        touch(session);
        return session.getSubmissionCompletedAt() != null;
    }

    private void touch(ExamSession session) {
        if (mongo.updateFirst(Query.query(Criteria.where("_id").is(session.getExamId())
                        .and("userId").is(session.getUserId()).and("version").is(session.getVersion())),
                new Update().inc("version", 1L), ExamSession.class).getModifiedCount() != 1) {
            throw new org.springframework.dao.OptimisticLockingFailureException("Submission fence changed");
        }
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Submission evidence requires a Mongo transaction");
    }
}
