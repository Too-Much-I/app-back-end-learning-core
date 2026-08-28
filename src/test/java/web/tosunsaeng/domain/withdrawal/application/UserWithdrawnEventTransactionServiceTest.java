package web.tosunsaeng.domain.withdrawal.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;
import web.tosunsaeng.domain.withdrawal.domain.UserWithdrawnEventInbox;
import web.tosunsaeng.domain.withdrawal.domain.WithdrawnUserAccessDeny;
import web.tosunsaeng.domain.withdrawal.repository.UserWithdrawnEventInboxRepository;
import web.tosunsaeng.domain.withdrawal.repository.WithdrawnUserAccessDenyRepository;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserWithdrawnEventTransactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T10:00:00Z");
    private UserWithdrawnEventInboxRepository inboxRepository;
    private WithdrawnUserAccessDenyRepository denyRepository;
    private UserWithdrawnEventTransactionService service;

    @BeforeEach
    void setUp() {
        inboxRepository = mock(UserWithdrawnEventInboxRepository.class);
        denyRepository = mock(WithdrawnUserAccessDenyRepository.class);
        service = new UserWithdrawnEventTransactionService(inboxRepository, denyRepository);
    }

    @Test
    void activeEventWritesMarkerThenInbox() {
        NormalizedUserWithdrawnEvent event = event(NOW.plusSeconds(60));

        assertEquals(UserWithdrawnConsumeResult.PROCESSED, service.consume(event));

        InOrder order = inOrder(denyRepository, inboxRepository);
        order.verify(denyRepository).save(any(WithdrawnUserAccessDeny.class));
        order.verify(inboxRepository).save(any(UserWithdrawnEventInbox.class));
        ArgumentCaptor<WithdrawnUserAccessDeny> marker =
                ArgumentCaptor.forClass(WithdrawnUserAccessDeny.class);
        verify(denyRepository).save(marker.capture());
        assertEquals(event.userId(), marker.getValue().getUserId());
        assertEquals(event.blockedUntil(), marker.getValue().getExpireAt());
    }

    @Test
    void eventPastBlockedUntilStoresInboxOnly() {
        NormalizedUserWithdrawnEvent event = event(NOW);

        assertEquals(UserWithdrawnConsumeResult.PROCESSED, service.consume(event));

        verify(denyRepository, never()).save(any());
        verify(inboxRepository).save(any(UserWithdrawnEventInbox.class));
    }

    @Test
    void sameEventAndDigestIsIdempotent() {
        NormalizedUserWithdrawnEvent event = event(NOW.plusSeconds(60));
        when(inboxRepository.findById(event.eventId())).thenReturn(Optional.of(inbox(event.payloadDigest())));

        assertEquals(UserWithdrawnConsumeResult.DUPLICATE, service.consume(event));

        verify(denyRepository, never()).save(any());
        verify(inboxRepository, never()).save(any());
    }

    @Test
    void sameEventWithDifferentDigestAndExistingUserMarkerAreConflicts() {
        NormalizedUserWithdrawnEvent event = event(NOW.plusSeconds(60));
        when(inboxRepository.findById(event.eventId())).thenReturn(Optional.of(inbox("different")));
        assertConflict(() -> service.consume(event));

        when(inboxRepository.findById(event.eventId())).thenReturn(Optional.empty());
        when(denyRepository.existsById(event.userId())).thenReturn(true);
        assertConflict(() -> service.consume(event));
    }

    @Test
    void transactionManagerNameIsExplicit() throws Exception {
        Method method = UserWithdrawnEventTransactionService.class.getMethod(
                "consume",
                NormalizedUserWithdrawnEvent.class
        );
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertEquals("userWithdrawnMongoTransactionManager", transactional.transactionManager());
    }

    private static void assertConflict(Runnable action) {
        UserWithdrawnEventException exception = assertThrows(UserWithdrawnEventException.class, action::run);
        assertEquals(UserWithdrawnEventException.Reason.PAYLOAD_CONFLICT, exception.getReason());
    }

    private static NormalizedUserWithdrawnEvent event(Instant blockedUntil) {
        return new NormalizedUserWithdrawnEvent(
                "00000000-0000-0000-0000-000000000109",
                1,
                "digest",
                "00000000-0000-0000-0000-000000000042",
                NOW.minusSeconds(60),
                NOW,
                blockedUntil,
                NOW.plus(Duration.ofDays(120))
        );
    }

    private static UserWithdrawnEventInbox inbox(String digest) {
        NormalizedUserWithdrawnEvent event = event(NOW.plusSeconds(60));
        return new UserWithdrawnEventInbox(
                event.eventId(), 1, digest, event.userId(), event.withdrawnAt(), NOW, NOW,
                web.tosunsaeng.domain.withdrawal.domain.UserWithdrawnInboxStatus.PROCESSED,
                event.inboxCleanupAt()
        );
    }
}
