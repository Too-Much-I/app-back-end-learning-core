package web.tosunsaeng.domain.learningrecorddeletion.infrastructure;

import org.bson.Document;
import org.springframework.data.mongodb.MongoDatabaseUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveCallback;
import web.tosunsaeng.domain.learningrecorddeletion.application.*;
import web.tosunsaeng.domain.learningrecorddeletion.domain.DeletionTarget;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardService;
import java.time.Clock;
import java.util.Set;

/** Last-line persistence fence, in addition to public read/write and callback handling. */
public final class DeletionPersistenceFence implements BeforeSaveCallback<Object> {
    private static final Set<String> EXAMS = Set.of("exam_sessions", "exam_results", "exam_summaries",
            "question_grading_jobs", "summary_grading_jobs", "azure_results", "speechace_results");
    private static final Set<String> CHALLENGES = Set.of("challenge_10s_attempts", "challenge_10s_grading_jobs",
            "challenge_10s_submit_receipts", "challenge_10s_callback_receipts");
    private final java.util.function.Supplier<MongoTemplate> mongoProvider;
    private final java.util.function.Supplier<DeletionAccess> accessProvider;
    private final java.util.function.Supplier<UserOwnershipGuardService> guardsProvider;
    private final Clock clock;
    public DeletionPersistenceFence(MongoTemplate mongo, DeletionAccess access, UserOwnershipGuardService guards, Clock clock) {
        this(() -> mongo, () -> access, () -> guards, clock);
    }
    public DeletionPersistenceFence(java.util.function.Supplier<MongoTemplate> mongo, java.util.function.Supplier<DeletionAccess> access,
            java.util.function.Supplier<UserOwnershipGuardService> guards, Clock clock) {
        this.mongoProvider = mongo; this.accessProvider = access; this.guardsProvider = guards; this.clock = clock;
    }
    @Override public Object onBeforeSave(Object entity, Document document, String collection) {
        check(collection, document); return entity;
    }
    public void check(String collection, Document document) {
        check(collection, document, false);
    }
    public void checkReservationFinalization(Document document) {
        check("exam_sessions", document, true);
    }
    private void check(String collection, Document document, boolean reservationFinalization) {
        if (document == null || (!EXAMS.contains(collection) && !CHALLENGES.contains(collection))) return;
        MongoTemplate mongo = mongoProvider.get();
        DeletionAccess access = accessProvider.get();
        UserOwnershipGuardService guards = guardsProvider.get();
        if (!MongoDatabaseUtils.isTransactionActive(mongo.getMongoDatabaseFactory()))
            throw new IllegalStateException("Learning writer requires an owner transaction");
        boolean exam = EXAMS.contains(collection);
        String rootCollection = exam ? "exam_sessions" : "challenge_10s_attempts";
        boolean rootWrite = rootCollection.equals(collection);
        String id = document.getString(rootWrite ? "_id" : exam ? "examId" : "attemptId");
        if (id == null) throw new IllegalStateException("Learning aggregate reference is missing");
        if (access.sealed(exam ? DeletionTarget.Type.EXAM : DeletionTarget.Type.CHALLENGE, id)) throw DeletionFailure.blocked();
        Document root = mongo.findById(id, Document.class, rootCollection);
        if (root == null && !rootWrite) throw new IllegalStateException("Learning aggregate is missing");
        String owner = root == null ? document.getString("userId") : root.getString("userId");
        if (owner == null || (document.get("userId") != null && !owner.equals(document.getString("userId"))))
            throw new IllegalStateException("Learning owner evidence is inconsistent");
        guards.touchActive(owner, clock.instant());
        if (reservationFinalization && root != null && "ENTITLEMENT_CONFIRMING".equals(root.getString("status"))) return;
        if (root == null) access.requirePublicWrite(owner);
        else if (exam) access.examCoordination(id, () -> { access.requireWriter(owner); return null; });
        else access.requireWriter(owner);
    }
}
