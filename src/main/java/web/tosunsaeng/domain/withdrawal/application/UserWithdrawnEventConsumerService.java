package web.tosunsaeng.domain.withdrawal.application;

import org.springframework.dao.DuplicateKeyException;
import web.tosunsaeng.domain.withdrawal.api.UserWithdrawnEventRequest;
import web.tosunsaeng.domain.withdrawal.config.UserWithdrawnConsumerProperties;
import web.tosunsaeng.domain.withdrawal.repository.UserWithdrawnEventInboxRepository;
import web.tosunsaeng.domain.withdrawal.repository.WithdrawnUserAccessDenyRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;

public class UserWithdrawnEventConsumerService {

    private static final int MAX_CONCURRENCY_RECHECKS = 25;
    private static final Duration CONCURRENCY_RECHECK_TIMEOUT = Duration.ofMillis(250);
    private static final Duration CONCURRENCY_RECHECK_BACKOFF = Duration.ofMillis(10);

    private final UserWithdrawnEventTransactionService transactionService;
    private final UserWithdrawnEventInboxRepository inboxRepository;
    private final WithdrawnUserAccessDenyRepository denyRepository;
    private final UserWithdrawnConsumerProperties properties;
    private final Clock clock;
    private final UserWithdrawnMetrics metrics;
    private final LongSupplier nanoTime;
    private final Runnable concurrencyBackoff;

    public UserWithdrawnEventConsumerService(
            UserWithdrawnEventTransactionService transactionService,
            UserWithdrawnEventInboxRepository inboxRepository,
            WithdrawnUserAccessDenyRepository denyRepository,
            UserWithdrawnConsumerProperties properties,
            Clock clock,
            UserWithdrawnMetrics metrics) {
        this(
                transactionService,
                inboxRepository,
                denyRepository,
                properties,
                clock,
                metrics,
                System::nanoTime,
                () -> LockSupport.parkNanos(CONCURRENCY_RECHECK_BACKOFF.toNanos())
        );
    }

    UserWithdrawnEventConsumerService(
            UserWithdrawnEventTransactionService transactionService,
            UserWithdrawnEventInboxRepository inboxRepository,
            WithdrawnUserAccessDenyRepository denyRepository,
            UserWithdrawnConsumerProperties properties,
            Clock clock,
            UserWithdrawnMetrics metrics,
            LongSupplier nanoTime,
            Runnable concurrencyBackoff) {
        this.transactionService = transactionService;
        this.inboxRepository = inboxRepository;
        this.denyRepository = denyRepository;
        this.properties = properties;
        this.clock = clock;
        this.metrics = metrics;
        this.nanoTime = nanoTime;
        this.concurrencyBackoff = concurrencyBackoff;
    }

    public UserWithdrawnConsumeResult consume(UserWithdrawnEventRequest request) {
        try {
            NormalizedUserWithdrawnEvent event = UserWithdrawnEventNormalizer.normalize(
                    request,
                    clock.instant(),
                    properties
            );
            try {
                UserWithdrawnConsumeResult result = transactionService.consume(event);
                metrics.recordConsumer(result.name());
                if (result == UserWithdrawnConsumeResult.PROCESSED) {
                    metrics.recordDeliveryLag(event.withdrawnAt(), event.receivedAt());
                }
                return result;
            } catch (DuplicateKeyException duplicate) {
                return resolveConcurrentWinner(event, duplicate);
            }
        } catch (UserWithdrawnEventException exception) {
            metrics.recordConsumer(exception.getReason().name());
            throw exception;
        } catch (RuntimeException exception) {
            metrics.recordConsumer("TRANSACTION_FAILED");
            throw exception;
        }
    }

    private UserWithdrawnConsumeResult resolveConcurrentWinner(
            NormalizedUserWithdrawnEvent event,
            DuplicateKeyException duplicate) {
        long deadline = nanoTime.getAsLong() + CONCURRENCY_RECHECK_TIMEOUT.toNanos();
        for (int attempt = 0; attempt < MAX_CONCURRENCY_RECHECKS; attempt++) {
            var winnerInbox = inboxRepository.findById(event.eventId());
            if (winnerInbox.isPresent()) {
                if (winnerInbox.orElseThrow().getPayloadDigest().equals(event.payloadDigest())) {
                    metrics.recordConsumer(UserWithdrawnConsumeResult.DUPLICATE.name());
                    return UserWithdrawnConsumeResult.DUPLICATE;
                }
                throw conflict();
            }

            var winnerMarker = denyRepository.findById(event.userId());
            if (winnerMarker.isPresent()
                    && !event.eventId().equals(winnerMarker.orElseThrow().getSourceEventId())) {
                throw conflict();
            }

            if (attempt + 1 >= MAX_CONCURRENCY_RECHECKS || nanoTime.getAsLong() >= deadline) {
                break;
            }
            concurrencyBackoff.run();
        }
        throw new UserWithdrawnEventException(
                UserWithdrawnEventException.Reason.PROCESSING_UNAVAILABLE,
                duplicate
        );
    }

    private static UserWithdrawnEventException conflict() {
        return new UserWithdrawnEventException(
                UserWithdrawnEventException.Reason.PAYLOAD_CONFLICT
        );
    }
}
