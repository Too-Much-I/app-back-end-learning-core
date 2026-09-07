package web.tosunsaeng.domain.challenge;

import java.time.*;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import static web.tosunsaeng.domain.challenge.ChallengeModels.*;
import static web.tosunsaeng.domain.challenge.ChallengeMetrics.*;

public class ChallengeWorker {
    private enum Selection { SELECTED, AUDIO_CHANGED, LEASE_LOST }
    private final ChallengeStore store;
    private final ChallengeTransactions tx;
    private final ChallengeService service;
    private final ChallengeCallbackService callbacks;
    private final ChallengeAudioStorage audio;
    private final ChallengeAiClient ai;
    private final Clock clock;
    private final ChallengeMetrics metrics;
    public ChallengeWorker(ChallengeStore store, ChallengeTransactions tx, ChallengeService service,
                           ChallengeCallbackService callbacks, ChallengeAudioStorage audio, ChallengeAiClient ai,
                           Clock clock, ChallengeMetrics metrics) {
        this.store = store; this.tx = tx; this.service = service; this.callbacks = callbacks;
        this.audio = audio; this.ai = ai; this.clock = clock; this.metrics = metrics;
    }
    public void tick() {
        long start = System.nanoTime();
        try {
            for (Job observed : store.due(clock.instant(), 20)) {
                try { dispatch(observed); }
                catch (RuntimeException failure) { metrics.record(Stage.dispatch, Outcome.temporary_failure, start); }
            }
        } catch (RuntimeException failure) { metrics.record(Stage.dispatch, Outcome.temporary_failure, start); }
    }
    public void expire() {
        long start = System.nanoTime();
        try {
            for (Attempt a : store.expired(clock.instant(), 100)) {
                try { service.expire(a); metrics.record(Stage.expiry, Outcome.expired, start); }
                catch (RuntimeException failure) { metrics.record(Stage.expiry, Outcome.temporary_failure, start); }
            }
        } catch (RuntimeException failure) { metrics.record(Stage.expiry, Outcome.temporary_failure, start); }
    }
    public void dispatch(Job observed) {
        long started = System.nanoTime();
        Attempt observedAttempt = store.attempt(observed.attemptId);
        if (observedAttempt == null) return;
        Job claim = tx.run(observedAttempt.userId, () -> claim(observed.id));
        if (claim == null) return;
        if (claim.state != JobState.DISPATCHING) {
            metrics.record(Stage.dispatch, claim.state == JobState.TIMED_OUT && claim.generation < 3 ? Outcome.generation_retry : Outcome.failed, started);
            return;
        }
        Attempt a = store.attempt(claim.attemptId);
        byte[] bytes;
        try { bytes = audio.read(a.uploadKey); }
        catch (ChallengeFailure failure) {
            finish(a, claim, new ChallengeAiClient.Reply(false, failure.status == 500, null), "AUDIO_UNAVAILABLE", started); return;
        }
        String digest = ChallengeCallback.sha256(bytes);
        Selection selected = tx.run(a.userId, () -> {
            Attempt current = store.attempt(a.id); Job job = store.job(claim.id);
            if (!owned(current, job, claim)) return Selection.LEASE_LOST;
            if (current.audioDigest != null && !current.audioDigest.equals(digest)) {
                fail(current, job, "AUDIO_CHANGED"); return Selection.AUDIO_CHANGED;
            }
            if (current.audioDigest == null) current.audioDigest = digest;
            store.save(current); store.save(job); return Selection.SELECTED;
        });
        if (selected != Selection.SELECTED) {
            metrics.record(Stage.dispatch, selected == Selection.AUDIO_CHANGED ? Outcome.failed : Outcome.lease_lost, started); return;
        }
        Instant now = clock.instant();
        Duration remaining = Duration.between(now, claim.dispatchDeadlineAt.isBefore(claim.leaseUntil) ? claim.dispatchDeadlineAt : claim.leaseUntil);
        ChallengeAiClient.Reply reply = remaining.isNegative() || remaining.isZero()
                ? new ChallengeAiClient.Reply(false, true, null) : ai.send(a, claim, bytes, remaining);
        finish(a, claim, reply, "AI_TRANSPORT_FAILED", started);
    }
    private Job claim(String id) {
        Job j = store.job(id); if (j == null) return null;
        Attempt a = store.attempt(j.attemptId); Instant now = clock.instant();
        if (a == null || a.generation != j.generation || a.gradingTerminal()) return null;
        if (j.state == JobState.WAITING_CALLBACK) {
            if (now.isBefore(j.callbackDeadlineAt)) return null;
            j.state = JobState.TIMED_OUT; j.failureCode = "CALLBACK_TIMEOUT";
            callbacks.retryOrFail(a, j, true, now); store.save(a); return store.save(j);
        }
        boolean due = ((j.state == JobState.PENDING || j.state == JobState.RETRY_WAIT) && !now.isBefore(j.nextAttemptAt))
                || (j.state == JobState.DISPATCHING && !now.isBefore(j.leaseUntil));
        if (!due) return null;
        if (j.dispatchDeadlineAt != null && !now.isBefore(j.dispatchDeadlineAt)) { fail(a, j, "DISPATCH_BUDGET_EXHAUSTED"); return j; }
        if (j.firstDispatchAt == null) { j.firstDispatchAt = now; j.dispatchDeadlineAt = now.plusSeconds(300); }
        j.state = JobState.DISPATCHING; j.leaseToken = UUID.randomUUID().toString(); j.leaseUntil = now.plusSeconds(30); j.dispatchCount++;
        return store.save(j);
    }
    private boolean owned(Attempt a, Job j, Job claim) {
        return a != null && j != null && !a.gradingTerminal() && a.generation == claim.generation
                && j.state == JobState.DISPATCHING && claim.leaseToken.equals(j.leaseToken) && clock.instant().isBefore(j.leaseUntil);
    }
    private void finish(Attempt observed, Job claim, ChallengeAiClient.Reply reply, String failureCode, long start) {
        Outcome outcome = tx.run(observed.userId, () -> {
            Attempt a = store.attempt(observed.id); Job j = store.job(claim.id);
            if (!owned(a, j, claim)) return Outcome.lease_lost;
            Instant now = clock.instant();
            if (reply.accepted()) {
                j.state = JobState.WAITING_CALLBACK;
                if (j.acceptedAt == null) { j.acceptedAt = now; j.callbackDeadlineAt = now.plusSeconds(120); }
                a.gradingStatus = "processing"; j.leaseToken = null; j.leaseUntil = null;
                store.save(a); store.save(j); return Outcome.accepted;
            }
            if (!reply.retryable() || !now.isBefore(j.dispatchDeadlineAt)) { fail(a, j, failureCode); return Outcome.failed; }
            long cap = Math.min(30_000, 1000L << Math.min(5, Math.max(0, j.dispatchCount - 1)));
            Instant next = now.plusMillis(ThreadLocalRandom.current().nextLong(Math.max(1, cap / 2), cap + 1));
            if (reply.retryAfter() != null && reply.retryAfter().isAfter(next)) next = reply.retryAfter();
            // Retry-After beyond budget schedules terminal reconciliation, never an earlier HTTP retry.
            j.nextAttemptAt = next.isBefore(j.dispatchDeadlineAt) ? next : j.dispatchDeadlineAt;
            j.state = JobState.RETRY_WAIT; j.leaseToken = null; j.leaseUntil = null; store.save(j); return Outcome.retry;
        });
        metrics.record(Stage.dispatch, outcome, start);
    }
    private void fail(Attempt a, Job j, String code) {
        a.gradingStatus = "failed"; j.state = JobState.FAILED; j.failureCode = code; j.leaseToken = null; j.leaseUntil = null;
        store.save(a); store.save(j);
    }
}
