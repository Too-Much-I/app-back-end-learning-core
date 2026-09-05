package web.tosunsaeng.domain.usermerge.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import web.tosunsaeng.domain.usermerge.api.UserMergedEventRequest;
import web.tosunsaeng.domain.usermerge.repository.UserMergedInboxRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@Slf4j
public class UserMergedConsumerService {

    private static final int MAX_RECHECKS = 25;
    private static final Duration RECHECK_TIMEOUT = Duration.ofMillis(250);
    private static final Duration RECHECK_BACKOFF = Duration.ofMillis(10);

    private final UserMergedTransactionService transactionService;
    private final UserMergedInboxRepository inboxRepository;
    private final Clock clock;
    private final UserMergedEventMetrics metrics;

    public UserMergedConsumerService(
            UserMergedTransactionService transactionService,
            UserMergedInboxRepository inboxRepository,
            Clock clock,
            UserMergedEventMetrics metrics
    ) {
        this.transactionService = transactionService;
        this.inboxRepository = inboxRepository;
        this.clock = clock;
        this.metrics = metrics;
    }

    public UserMergedConsumeResult consume(UserMergedEventRequest request) {
        long startedAt = System.nanoTime();
        String outcome = "failed";
        String eventId = null;
        int schemaVersion = -1;
        try {
            NormalizedUserMergedEvent event = UserMergedEventNormalizer.normalize(
                    request,
                    clock.instant()
            );
            eventId = event.eventId();
            schemaVersion = event.schemaVersion();
            UserMergedConsumeResult result;
            try {
                result = transactionService.consume(event);
            } catch (DuplicateKeyException race) {
                result = resolveConcurrentWinner(event, race);
            }
            outcome = result.name();
            return result;
        } catch (UserMergedEventException failure) {
            outcome = failure.getReason().name();
            throw failure;
        } finally {
            long durationNanos = Math.max(0L, System.nanoTime() - startedAt);
            String normalizedOutcome = outcome.toLowerCase(Locale.ROOT);
            metrics.record(normalizedOutcome, durationNanos);
            log.info(
                    "UserMerged event 처리 event=user_merged.consume outcome={} "
                            + "schemaVersion={} eventId={} durationMs={}",
                    normalizedOutcome,
                    schemaVersion,
                    eventId,
                    TimeUnit.NANOSECONDS.toMillis(durationNanos)
            );
        }
    }

    private UserMergedConsumeResult resolveConcurrentWinner(
            NormalizedUserMergedEvent event,
            RuntimeException original
    ) {
        long deadline = System.nanoTime() + RECHECK_TIMEOUT.toNanos();
        for (int attempt = 0; attempt < MAX_RECHECKS; attempt++) {
            var winner = inboxRepository.findById(event.eventId());
            if (winner.isPresent()) {
                if (winner.orElseThrow().getPayloadDigest().equals(event.payloadDigest())) {
                    return UserMergedConsumeResult.DUPLICATE;
                }
                throw new UserMergedEventException(
                        UserMergedEventException.Reason.PAYLOAD_CONFLICT
                );
            }
            if (attempt + 1 >= MAX_RECHECKS || System.nanoTime() >= deadline) {
                break;
            }
            LockSupport.parkNanos(RECHECK_BACKOFF.toNanos());
        }
        throw new UserMergedEventException(
                UserMergedEventException.Reason.PROCESSING_UNAVAILABLE,
                original
        );
    }
}
