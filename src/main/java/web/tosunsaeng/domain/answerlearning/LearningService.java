package web.tosunsaeng.domain.answerlearning;

import org.bson.Document;
import java.time.*;
import java.util.*;
import static web.tosunsaeng.domain.answerlearning.LearningModels.*;

public class LearningService {
    private final LearningCatalog catalog;
    private final LearningStore store;
    private final LearningStorage storage;
    private final LearningProperties config;
    private final Clock clock;
    public LearningService(LearningCatalog catalog, LearningStore store, LearningStorage storage, LearningProperties config, Clock clock) {
        this.catalog=catalog; this.store=store; this.storage=storage; this.config=config; this.clock=clock;
    }
    static String uuid(String id) {
        try { if (id == null || !UUID.fromString(id).toString().equals(id)) throw LearningFailure.bad(); return id; }
        catch (IllegalArgumentException e) { throw LearningFailure.bad(); }
    }
    private void gradingReady() { if (!config.isGradingEnabled()) throw new LearningFailure(503, "GRADING_NOT_READY"); }
    public Document capabilities(String lessonId) {
        Document lesson = catalog.lesson(lessonId);
        boolean has = lesson.getInteger("part") != 1 && catalog.checklist(lesson) != null;
        return new Document("ready", config.isGradingEnabled()).append("lessonIds", has ? List.of(lessonId) : List.of());
    }
    private void same(Submission s, Create request) {
        if (!s.lessonId.equals(request.lessonId()) || !s.level.equals(request.level()) || !s.contentVersion.equals(request.contentVersion()))
            throw new LearningFailure(409, "IDEMPOTENCY_CONFLICT");
    }
    public Job create(String owner, Create request) {
        String id = uuid(request.submissionId());
        Submission existing = store.find(id);
        if (existing != null) {
            existing = store.owned(owner, id); same(existing, request); return existing.view();
        }
        gradingReady(); catalog.version(request.contentVersion());
        Document lesson = catalog.lesson(request.lessonId());
        if (lesson.getInteger("part") == 1) throw LearningFailure.bad();
        String level = LearningCatalog.level(lesson.getInteger("part"), request.level());
        Document checklist = catalog.checklist(lesson);
        if (checklist == null) throw new LearningFailure(409, "CHECKLIST_NOT_READY");
        Instant now = clock.instant();
        Submission s = new Submission(); s.id=id; s.userId=owner; s.lessonId=request.lessonId();
        s.contentVersion=request.contentVersion(); s.level=level; s.checklist=checklist; s.createdAt=now; s.updatedAt=now;
        Submission saved = store.create(s, config.getDailySubmissions()); same(saved, request); return saved.view();
    }
    public Job get(String owner, String id) { return store.owned(owner, uuid(id)).view(); }
    public UploadUrl upload(String owner, String id, Upload request) {
        Submission s = store.owned(owner, uuid(id));
        if (!"awaiting_audio".equals(s.status)) throw new LearningFailure(409, "UPLOAD_CLOSED");
        if (!"audio/mp4".equals(request.contentType()) || request.sizeBytes() < 1 || request.sizeBytes() > LearningStorage.MAX_BYTES) throw LearningFailure.bad();
        if (s.createdAt.plus(Duration.ofDays(1)).isBefore(clock.instant())) throw new LearningFailure(410, "SUBMISSION_EXPIRED");
        s = store.uploadSize(s, request.sizeBytes(), clock.instant());
        if (s == null) throw new LearningFailure(409, "UPLOAD_CLOSED");
        return storage.upload(s);
    }
    public Job submit(String owner, String id) {
        Submission s = store.owned(owner, uuid(id));
        if (!"awaiting_audio".equals(s.status)) return s.view();
        gradingReady();
        if (s.createdAt.plus(Duration.ofDays(1)).isBefore(clock.instant())) throw new LearningFailure(410, "SUBMISSION_EXPIRED");
        s = store.beginFreeze(s, clock.instant());
        if (s == null) return store.owned(owner, id).view();
        Audio fixed = storage.freeze(s);
        Submission queued = store.queue(s, fixed, clock.instant());
        if (queued == null) {
            Submission current = store.owned(owner, s.id);
            if ("awaiting_audio".equals(current.status)) throw new LearningFailure(409, "UPLOAD_CHANGED");
            return current.view();
        }
        return queued.view();
    }
    public Job retry(String owner, String id) {
        Submission s = store.owned(owner, uuid(id));
        if (!"failed".equals(s.status)) return s.view();
        if (s.error == null || !s.error.retryable() || s.attempt >= 3) throw new LearningFailure(409, "RETRY_NOT_ALLOWED");
        // Lambda receipts expire after 7 days. Never resurrect them from older Mongo rows.
        if (s.queuedAt == null || s.queuedAt.plus(Duration.ofDays(1)).isBefore(clock.instant())) throw new LearningFailure(410, "SUBMISSION_EXPIRED");
        gradingReady();
        Submission next = store.retry(s, clock.instant(), config.getDailySubmissions());
        return (next == null ? store.owned(owner, s.id) : next).view();
    }
    public Document complete(String owner, String lessonId, Completion request) {
        catalog.version(request.contentVersion());
        Document lesson = catalog.lesson(lessonId);
        String level = LearningCatalog.level(lesson.getInteger("part"), request.level());
        if (lesson.getInteger("part") != 1) {
            Submission s = store.owned(owner, uuid(request.submissionId()));
            if (!s.lessonId.equals(lessonId) || !s.level.equals(level) || !s.contentVersion.equals(request.contentVersion())
                    || !"completed".equals(s.status) || s.result == null) throw new LearningFailure(409, "COMPLETION_NOT_READY");
        } else if (request.submissionId() != null || !"common".equals(request.level())) throw LearningFailure.bad();
        store.complete(owner, lessonId, level, request.contentVersion(), request.submissionId(), clock.instant());
        return new Document("lessonId", lessonId).append("level", level).append("contentVersion", request.contentVersion()).append("completed", true);
    }
}
