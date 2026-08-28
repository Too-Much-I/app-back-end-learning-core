package web.tosunsaeng.domain.withdrawal.application;

import org.springframework.transaction.annotation.Transactional;
import web.tosunsaeng.domain.withdrawal.domain.UserWithdrawnEventInbox;
import web.tosunsaeng.domain.withdrawal.domain.UserWithdrawnInboxStatus;
import web.tosunsaeng.domain.withdrawal.domain.WithdrawnUserAccessDeny;
import web.tosunsaeng.domain.withdrawal.repository.UserWithdrawnEventInboxRepository;
import web.tosunsaeng.domain.withdrawal.repository.WithdrawnUserAccessDenyRepository;

public class UserWithdrawnEventTransactionService {

    private final UserWithdrawnEventInboxRepository inboxRepository;
    private final WithdrawnUserAccessDenyRepository denyRepository;

    public UserWithdrawnEventTransactionService(
            UserWithdrawnEventInboxRepository inboxRepository,
            WithdrawnUserAccessDenyRepository denyRepository) {
        this.inboxRepository = inboxRepository;
        this.denyRepository = denyRepository;
    }

    @Transactional(transactionManager = "userWithdrawnMongoTransactionManager")
    public UserWithdrawnConsumeResult consume(NormalizedUserWithdrawnEvent event) {
        var existingInbox = inboxRepository.findById(event.eventId());
        if (existingInbox.isPresent()) {
            if (existingInbox.orElseThrow().getPayloadDigest().equals(event.payloadDigest())) {
                return UserWithdrawnConsumeResult.DUPLICATE;
            }
            throw conflict();
        }

        if (denyRepository.existsById(event.userId())) {
            throw conflict();
        }

        if (event.blockedUntil().isAfter(event.receivedAt())) {
            denyRepository.save(new WithdrawnUserAccessDeny(
                    event.userId(),
                    event.eventId(),
                    event.withdrawnAt(),
                    event.blockedUntil(),
                    event.blockedUntil(),
                    event.receivedAt()
            ));
        }

        inboxRepository.save(new UserWithdrawnEventInbox(
                event.eventId(),
                event.schemaVersion(),
                event.payloadDigest(),
                event.userId(),
                event.withdrawnAt(),
                event.receivedAt(),
                event.receivedAt(),
                UserWithdrawnInboxStatus.PROCESSED,
                event.inboxCleanupAt()
        ));
        return UserWithdrawnConsumeResult.PROCESSED;
    }

    private static UserWithdrawnEventException conflict() {
        return new UserWithdrawnEventException(
                UserWithdrawnEventException.Reason.PAYLOAD_CONFLICT
        );
    }
}
