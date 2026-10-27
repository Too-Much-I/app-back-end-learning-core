package web.tosunsaeng.domain.answerlearning;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import web.tosunsaeng.domain.exams.reanswer.ReanswerEligibilityClient;
import web.tosunsaeng.domain.exams.exception.ExamsException;

/** Request-local read-only observation; never persisted or shared between requests. */
public final class LearningPaidAccess {
    private final ReanswerEligibilityClient client;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final Consumer<Throwable> reporter;
    public LearningPaidAccess(ReanswerEligibilityClient client, Clock clock, Consumer<Throwable> reporter) {
        this(client, clock, System::nanoTime, reporter);
    }
    LearningPaidAccess(ReanswerEligibilityClient client, Clock clock, LongSupplier nanoTime, Consumer<Throwable> reporter) {
        this.client=client; this.clock=clock; this.nanoTime=nanoTime; this.reporter=reporter;
    }
    public Runnable prepare(String owner) {
        long started=nanoTime.getAsLong();
        try {
            var proof=client.check(owner);
            Instant received=clock.instant();
            if (proof==null || !proof.valid() || !owner.equals(proof.userId())
                    || Instant.parse(proof.checkedAt()).isAfter(received)) throw contract();
            Runnable check=() -> {
                Instant now=clock.instant();
                long elapsed=nanoTime.getAsLong()-started;
                if (elapsed<0 || now.isBefore(received)) throw contract();
                if (elapsed>=5_000_000_000L || !now.isBefore(Instant.parse(proof.validUntil())))
                    throw new LearningFailure(503,"BILLING_TEMPORARILY_UNAVAILABLE");
            };
            check.run();
            return check;
        } catch (LearningFailure known) { throw known; }
        catch (ExamsException known) {
            var reason=known.getErrorReasonHttpStatus();
            throw new LearningFailure(reason.getHttpStatus().value(),reason.getCode());
        } catch (RuntimeException invalid) { throw contract(); }
    }
    private LearningFailure contract() {
        // Fixed diagnostic only: never pass remote bodies, user IDs or tokens to telemetry.
        try { reporter.accept(new IllegalStateException("Part learning Billing contract failure")); }
        catch (RuntimeException ignored) { /* Reporting cannot change the public classification. */ }
        return new LearningFailure(500,"COMMON500");
    }
}
