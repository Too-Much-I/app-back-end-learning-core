package web.tosunsaeng.domain.challenge;

import java.time.Clock;
import java.time.Instant;
import static web.tosunsaeng.domain.challenge.ChallengeModels.*;
import static web.tosunsaeng.domain.challenge.ChallengeMetrics.*;

public class ChallengeCallbackService {
    private final ChallengeStore store;
    private final ChallengeTransactions tx;
    private final Clock clock;
    private final ChallengeMetrics metrics;
    public ChallengeCallbackService(ChallengeStore store, ChallengeTransactions tx, Clock clock, ChallengeMetrics metrics) {
        this.store = store; this.tx = tx; this.clock = clock; this.metrics = metrics;
    }
    public void accept(ChallengeCallback c) {
        long start = System.nanoTime();
        Attempt observed = store.attempt(c.attemptId());
        if (observed == null) throw new ChallengeFailure(404, "ATTEMPT_NOT_FOUND");
        try {
            Outcome outcome = tx.run(observed.userId, () -> apply(c));
            metrics.record(Stage.callback, outcome, start);
        } catch (RuntimeException failure) {
            if (failure instanceof ChallengeFailure) { metrics.record(Stage.callback, Outcome.conflict, start); throw failure; }
            CallbackReceipt receipt = store.callback(c.callbackId());
            if (receipt != null) {
                same(receipt, c);
                if (store.attempt(c.attemptId()) != null && store.job(c.jobId()) != null) {
                    metrics.record(Stage.callback, Outcome.duplicate, start); return;
                }
            }
            metrics.record(Stage.callback, Outcome.temporary_failure, start);
            throw ChallengeFailure.internal();
        }
    }
    private Outcome apply(ChallengeCallback c) {
        Attempt a = store.attempt(c.attemptId());
        if (a == null) throw new ChallengeFailure(404, "ATTEMPT_NOT_FOUND");
        Job j = store.job(c.jobId());
        if (!Job.id(a.id, c.generation()).equals(c.jobId()) || j == null || !a.id.equals(j.attemptId)
                || j.generation != c.generation() || c.generation() > a.generation || a.state != State.SUBMITTED)
            throw new ChallengeFailure(409, "GRADING_GENERATION_CONFLICT");
        CallbackReceipt existing = store.callback(c.callbackId());
        if (existing != null) { same(existing, c); return Outcome.duplicate; }
        Outcome outcome;
        if (c.generation() < a.generation) outcome = Outcome.stale;
        else if (j.callbackDigest != null) {
            if (!j.callbackDigest.equals(c.digest())) throw conflict(); outcome = Outcome.duplicate;
        } else if (a.gradingTerminal()) outcome = Outcome.stale;
        else {
            j.callbackDigest = c.digest(); j.leaseToken = null; j.leaseUntil = null;
            if ("failed".equals(c.outcome())) {
                j.state = JobState.FAILED;
                j.failureCode = "AI_FAILED"; // No untrusted provider text or free-form error metric tags.
                retryOrFail(a, j, c.retryable(), clock.instant());
                outcome = c.retryable() && a.generation > j.generation ? Outcome.generation_retry : Outcome.failed;
            } else {
                a.result = c.result(); a.gradedAt = clock.instant(); a.gradingStatus = "completed";
                j.state = JobState.COMPLETED;
                outcome = "no_speech".equals(c.outcome()) ? Outcome.no_speech : Outcome.completed;
            }
            store.save(a); store.save(j);
        }
        CallbackReceipt receipt = new CallbackReceipt(); receipt.id = c.callbackId(); receipt.attemptId = c.attemptId();
        receipt.jobId = c.jobId(); receipt.generation = c.generation(); receipt.digest = c.digest(); store.insert(receipt);
        return outcome;
    }
    void retryOrFail(Attempt a, Job j, boolean retry, Instant now) {
        if (retry && a.generation < 3) {
            a.generation++; a.gradingStatus = "pending"; store.insert(Job.pending(a, now));
        } else a.gradingStatus = "failed";
    }
    private static void same(CallbackReceipt r, ChallengeCallback c) {
        if (!r.attemptId.equals(c.attemptId()) || !r.jobId.equals(c.jobId()) || r.generation != c.generation() || !r.digest.equals(c.digest())) throw conflict();
    }
    private static ChallengeFailure conflict() { return new ChallengeFailure(409, "CALLBACK_PAYLOAD_CONFLICT"); }
}
