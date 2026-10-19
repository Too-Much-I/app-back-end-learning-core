package web.tosunsaeng.domain.withdrawal.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import web.tosunsaeng.domain.withdrawal.api.UserWithdrawnEventRequest;
import web.tosunsaeng.domain.withdrawal.config.UserWithdrawnConsumerProperties;
import web.tosunsaeng.domain.withdrawal.domain.UserWithdrawnEventInbox;
import web.tosunsaeng.domain.withdrawal.repository.UserWithdrawnEventInboxRepository;
import web.tosunsaeng.domain.withdrawal.repository.WithdrawnUserAccessDenyRepository;
import web.tosunsaeng.domain.withdrawal.domain.WithdrawnUserAccessDeny;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserWithdrawnEventConsumerServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T10:00:00Z");
    private static final String EVENT_ID = "00000000-0000-0000-0000-000000000109";
    private static final String USER_ID = "00000000-0000-0000-0000-000000000042";

    private UserWithdrawnEventTransactionService transactionService;
    private UserWithdrawnEventInboxRepository inboxRepository;
    private WithdrawnUserAccessDenyRepository denyRepository;
    private UserWithdrawnEventConsumerService service;

    @BeforeEach
    void setUp() {
        transactionService = mock(UserWithdrawnEventTransactionService.class);
        inboxRepository = mock(UserWithdrawnEventInboxRepository.class);
        denyRepository = mock(WithdrawnUserAccessDenyRepository.class);
        service = new UserWithdrawnEventConsumerService(
                transactionService,
                inboxRepository,
                denyRepository,
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new UserWithdrawnMetrics(new SimpleMeterRegistry()),
                () -> 0L,
                () -> { }
        );
    }

    @Test
    void validEventIsNormalizedBeforeTransaction() {
        when(transactionService.consume(any())).thenReturn(UserWithdrawnConsumeResult.PROCESSED);
        UserWithdrawnEventRequest request = request("2026-08-27T09:59:00Z");

        assertEquals(UserWithdrawnConsumeResult.PROCESSED, service.consume(request));

        ArgumentCaptor<NormalizedUserWithdrawnEvent> captor =
                ArgumentCaptor.forClass(NormalizedUserWithdrawnEvent.class);
        verify(transactionService).consume(captor.capture());
        NormalizedUserWithdrawnEvent event = captor.getValue();
        assertEquals(EVENT_ID, event.eventId());
        assertEquals(USER_ID, event.userId());
        assertEquals(1, event.schemaVersion());
        assertEquals(64, event.payloadDigest().length());
        assertEquals(Instant.parse("2026-08-27T10:30:00Z"), event.blockedUntil());
        assertEquals(NOW.plus(Duration.ofDays(120)), event.inboxCleanupAt());
    }

    @Test
    void semanticDigestMatchesSharedGoldenVector() {
        when(transactionService.consume(any())).thenReturn(UserWithdrawnConsumeResult.PROCESSED);
        UserWithdrawnEventRequest goldenRequest = new UserWithdrawnEventRequest(
                "9a88bc80-d73a-4a3d-8f68-492641d27208",
                1,
                "73a18ed4-1d56-4c4f-afd6-b39175b82a86",
                "2026-08-27T02:00:00Z"
        );

        service.consume(goldenRequest);

        ArgumentCaptor<NormalizedUserWithdrawnEvent> captor =
                ArgumentCaptor.forClass(NormalizedUserWithdrawnEvent.class);
        verify(transactionService).consume(captor.capture());
        assertEquals(
                "a956f71c53a448afbf657f9cc74a00a6ba1aed0571d43203d345bd44be489e27",
                captor.getValue().payloadDigest()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "00000000-0000-0000-0000-00000000010A",
            "not-a-uuid",
            "00000000-0000-0000-0000-00000000001"
    })
    void nonCanonicalEventIdIsRejected(String eventId) {
        UserWithdrawnEventRequest request = new UserWithdrawnEventRequest(
                eventId, 1, USER_ID, "2026-08-27T09:59:00Z"
        );

        UserWithdrawnEventException exception = assertThrows(
                UserWithdrawnEventException.class,
                () -> service.consume(request)
        );
        assertEquals(UserWithdrawnEventException.Reason.INVALID_PAYLOAD, exception.getReason());
    }

    @Test
    void unsupportedSchemaAndFutureEventAreRejected() {
        assertInvalid(new UserWithdrawnEventRequest(
                EVENT_ID, 2, USER_ID, "2026-08-27T09:59:00Z"
        ));
        assertInvalid(request("2026-08-27T10:01:01Z"));
    }

    @Test
    void duplicateKeyConvergesToCommittedWinnerWithSameDigest() {
        when(transactionService.consume(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(inboxRepository.findById(EVENT_ID)).thenAnswer(invocation -> {
            ArgumentCaptor<NormalizedUserWithdrawnEvent> captor =
                    ArgumentCaptor.forClass(NormalizedUserWithdrawnEvent.class);
            try {
                verify(transactionService).consume(captor.capture());
            } catch (AssertionError ignored) {
                return Optional.empty();
            }
            return Optional.of(inbox(captor.getValue().payloadDigest()));
        });

        assertEquals(UserWithdrawnConsumeResult.DUPLICATE, service.consume(request("2026-08-27T09:59:00Z")));
    }

    @Test
    void duplicateKeyWithDifferentPayloadIsConflict() {
        when(transactionService.consume(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(inboxRepository.findById(EVENT_ID)).thenReturn(Optional.of(inbox("different")));

        UserWithdrawnEventException exception = assertThrows(
                UserWithdrawnEventException.class,
                () -> service.consume(request("2026-08-27T09:59:00Z"))
        );
        assertEquals(UserWithdrawnEventException.Reason.PAYLOAD_CONFLICT, exception.getReason());
    }

    @Test
    void markerWinnerWithDifferentEventIdIsConflict() {
        when(transactionService.consume(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(inboxRepository.findById(EVENT_ID)).thenReturn(Optional.empty());
        when(denyRepository.findById(USER_ID)).thenReturn(Optional.of(marker("00000000-0000-0000-0000-000000000999")));

        UserWithdrawnEventException exception = assertThrows(
                UserWithdrawnEventException.class,
                () -> service.consume(request("2026-08-27T09:59:00Z"))
        );
        assertEquals(UserWithdrawnEventException.Reason.PAYLOAD_CONFLICT, exception.getReason());
    }

    @Test
    void markerWithSameEventButMissingInboxDoesNotAssumeSuccess() {
        when(transactionService.consume(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(inboxRepository.findById(EVENT_ID)).thenReturn(Optional.empty());
        when(denyRepository.findById(USER_ID)).thenReturn(Optional.of(marker(EVENT_ID)));

        UserWithdrawnEventException exception = assertThrows(
                UserWithdrawnEventException.class,
                () -> service.consume(request("2026-08-27T09:59:00Z"))
        );
        assertEquals(UserWithdrawnEventException.Reason.PROCESSING_UNAVAILABLE, exception.getReason());
    }

    @Test
    void unresolvedConcurrentWinnerFailsClosed() {
        when(transactionService.consume(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(inboxRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        UserWithdrawnEventException exception = assertThrows(
                UserWithdrawnEventException.class,
                () -> service.consume(request("2026-08-27T09:59:00Z"))
        );
        assertEquals(UserWithdrawnEventException.Reason.PROCESSING_UNAVAILABLE, exception.getReason());
    }

    private void assertInvalid(UserWithdrawnEventRequest request) {
        UserWithdrawnEventException exception = assertThrows(
                UserWithdrawnEventException.class,
                () -> service.consume(request)
        );
        assertEquals(UserWithdrawnEventException.Reason.INVALID_PAYLOAD, exception.getReason());
    }

    private static UserWithdrawnEventRequest request(String withdrawnAt) {
        return new UserWithdrawnEventRequest(EVENT_ID, 1, USER_ID, withdrawnAt);
    }

    private static UserWithdrawnEventInbox inbox(String digest) {
        return new UserWithdrawnEventInbox(
                EVENT_ID, 1, digest, USER_ID, NOW, NOW, NOW,
                web.tosunsaeng.domain.withdrawal.domain.UserWithdrawnInboxStatus.PROCESSED,
                NOW.plus(Duration.ofDays(120))
        );
    }

    private static UserWithdrawnConsumerProperties properties() {
        UserWithdrawnConsumerProperties properties = new UserWithdrawnConsumerProperties();
        properties.setConsumerEnabled(true);
        properties.setDenyGateEnabled(true);
        properties.setMaxAcceptedAccessTokenLifetime(Duration.ofMinutes(30));
        properties.setAllowedVerifierClockSkew(Duration.ofMinutes(1));
        properties.setInboxRetention(Duration.ofDays(120));
        properties.setMaximumFutureEventSkew(Duration.ofMinutes(1));
        return properties;
    }

    private static WithdrawnUserAccessDeny marker(String sourceEventId) {
        return new WithdrawnUserAccessDeny(
                USER_ID,
                sourceEventId,
                NOW.minusSeconds(60),
                NOW.plusSeconds(60),
                NOW.plusSeconds(60),
                NOW
        );
    }
}
