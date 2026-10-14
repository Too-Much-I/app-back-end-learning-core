package web.tosunsaeng.domain.answerlearning;

import io.micrometer.core.instrument.MeterRegistry;
import org.bson.Document;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.mongodb.core.query.Update;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static web.tosunsaeng.domain.answerlearning.LearningModels.*;

/** Mongo is the durable outbox. Leases fence stale workers; Lambda deduplicates transport re-delivery. */
public class LearningWorker implements SmartLifecycle {
    private final LearningStore store;
    private final LearningLambdaClient lambda;
    private final LearningProperties config;
    private final Clock clock;
    private final MeterRegistry metrics;
    private ScheduledExecutorService executor;
    private volatile boolean running;
    private LearningStore.DueCursor cursor;
    private Instant scanThrough;
    public LearningWorker(LearningStore store, LearningLambdaClient lambda, LearningProperties config, Clock clock, MeterRegistry metrics) {
        this.store=store; this.lambda=lambda; this.config=config; this.clock=clock; this.metrics=metrics;
    }
    public void tick() {
        try {
            // A fixed horizon and a cursor keep a blocked first page from starving other owners.
            // No blocked user's data is changed; each tick still examines at most ten candidates.
            if (scanThrough == null) scanThrough = clock.instant();
            List<Submission> candidates = store.due(scanThrough, cursor);
            for (Submission candidate : candidates) {
                cursor = new LearningStore.DueCursor(candidate.nextRunAt, candidate.id);
                try {
                    Submission claimed = store.claim(candidate, clock.instant());
                    if (claimed != null) process(claimed);
                } catch (Exception ignored) { metrics.counter("answer_learning.worker.errors").increment(); }
            }
            if (candidates.size() < LearningStore.DUE_BATCH_SIZE) {
                cursor = null;
                scanThrough = null;
            }
        } catch (Exception ignored) { metrics.counter("answer_learning.worker.errors").increment(); }
    }
    void process(Submission s) {
        Instant now = clock.instant();
        try {
            if (s.completionPending && "completed".equals(s.status)) {
                store.completeClaim(s, now); return;
            }
            if (s.queuedAt == null || s.queuedAt.plus(Duration.ofDays(1)).isBefore(now)) {
                fail(s, "SUBMISSION_EXPIRED", now); return;
            }
            Document remote = s.dispatched ? lambda.get(s) : lambda.submit(s);
            Update update = validate(s, remote).set("dispatched", true).set("transportFailures", 0);
            store.finish(s, update, clock.instant(), 2);
        } catch (LearningFailure e) {
            if (Set.of("INVALID_GRADING_RESPONSE", "GRADING_REQUEST_REJECTED").contains(e.code)) fail(s, e.code, clock.instant());
            else backoff(s);
        } catch (Exception ignored) { backoff(s); }
    }
    private void backoff(Submission s) {
        metrics.counter("answer_learning.transport.errors").increment();
        // Same job/attempt only. A lost response never increments the model attempt.
        store.finish(s, new Update().inc("transportFailures", 1), clock.instant(), Math.min(60, 2L << Math.min(s.transportFailures, 5)));
    }
    private void fail(Submission s, String code, Instant now) {
        store.finish(s, new Update().set("status", "failed").set("result", null)
                .set("error", new LearningModels.Error(code, "답변 처리를 확인하지 못했어요. 새로 녹음해 주세요.", false)), now, 0);
    }
    static Update validate(Submission s, Document r) {
        try {
            if (!s.id.equals(r.getString("id")) || !(s.id+":"+s.attempt).equals(r.getString("jobId"))
                    || !Objects.equals(s.attempt, r.getInteger("attempt")) || !s.lessonId.equals(r.getString("lessonId"))
                    || !s.level.equals(r.getString("level")) || !s.contentVersion.equals(r.getString("contentVersion"))) throw invalid();
            String state = r.getString("status");
            if (!Set.of("queued", "transcribing", "grading", "completed", "failed", "no_speech").contains(state)) throw invalid();
            Update update = new Update().set("status", state).set("error", null).set("result", null);
            if ("completed".equals(state)) {
                Document result = r.get("result", Document.class);
                if (result == null || !s.checklist.getString("version").equals(result.getString("checklistVersion"))) throw invalid();
                String transcript = result.getString("transcript");
                if (transcript == null || transcript.isBlank() || transcript.length() > 12000) throw invalid();
                List<Document> criteria = s.checklist.getList("items", Document.class);
                List<Document> items = result.getList("items", Document.class);
                if (items == null || items.size() != criteria.size() || !Objects.equals(result.getInteger("totalCount"), items.size())) throw invalid();
                Set<String> ids = new HashSet<>(); criteria.forEach(i -> ids.add(i.getString("id")));
                int met = 0;
                List<Document> clean = new ArrayList<>();
                for (Document item : items) {
                    if (!ids.remove(item.getString("id")) || !(item.get("probability") instanceof Number probability)) throw invalid();
                    double p = probability.doubleValue();
                    if (!Double.isFinite(p) || p < 0 || p > 1) throw invalid();
                    String verdict = item.getString("verdict");
                    double yes = ((Number)s.checklist.get("met_threshold")).doubleValue();
                    double no = ((Number)s.checklist.get("not_met_threshold")).doubleValue();
                    if (!(p >= yes ? "met" : p <= no ? "not_met" : "uncertain").equals(verdict)) throw invalid();
                    if ("met".equals(verdict)) met++;
                    String label = item.getString("label"), feedback = item.getString("feedback");
                    if (label == null || feedback == null || label.length()>200 || feedback.length()>1000) throw invalid();
                    clean.add(new Document("id", item.getString("id")).append("label", label).append("verdict", verdict)
                            .append("probability", p).append("feedback", feedback));
                }
                if (!ids.isEmpty() || !Objects.equals(result.getInteger("metCount"), met)) throw invalid();
                update.set("result", new Document("checklistVersion", result.getString("checklistVersion")).append("transcript", transcript)
                        .append("metCount", met).append("totalCount", clean.size()).append("items", clean)).set("completionPending", true);
            } else if ("failed".equals(state)) {
                Document error = r.get("error", Document.class);
                if (error == null || !(error.get("retryable") instanceof Boolean) || error.getString("code") == null
                        || !error.getString("code").matches("[A-Z_]{1,80}")) throw invalid();
                update.set("error", new LearningModels.Error(error.getString("code"), "채점하지 못했어요. 다시 시도하거나 새로 녹음해 주세요.",
                        s.attempt < 3 && error.getBoolean("retryable")));
            }
            return update;
        } catch (LearningFailure e) { throw e; }
        catch (RuntimeException e) { throw invalid(); }
    }
    private static LearningFailure invalid() { return new LearningFailure(502, "INVALID_GRADING_RESPONSE"); }
    @Override public synchronized void start() {
        if (running || !config.isGradingEnabled()) return;
        executor=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("answer-learning-worker").factory());
        executor.scheduleWithFixedDelay(this::tick, 0, config.getPollMs(), TimeUnit.MILLISECONDS); running=true;
    }
    @Override public synchronized void stop() {
        running=false;
        if (executor != null) executor.shutdownNow();
    }
    @Override public boolean isRunning() { return running; }
    @Override public int getPhase() { return Integer.MAX_VALUE; }
}
