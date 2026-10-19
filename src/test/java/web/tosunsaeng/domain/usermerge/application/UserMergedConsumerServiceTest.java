package web.tosunsaeng.domain.usermerge.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import web.tosunsaeng.domain.usermerge.api.UserMergedEventRequest;
import web.tosunsaeng.domain.usermerge.domain.UserMergedInboxEvent;
import web.tosunsaeng.domain.usermerge.domain.UserMergedInboxStatus;
import web.tosunsaeng.domain.usermerge.repository.UserMergedInboxRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserMergedConsumerServiceTest {

    private static final String EVENT_ID = "9a88bc80-d73a-4a3d-8f68-492641d27208";
    private static final String SOURCE = "73a18ed4-1d56-4c4f-afd6-b39175b82a86";
    private static final String TARGET = "45c05c3f-ae7f-4ca7-af88-3ab8aa8f428e";
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-05T00:59:00Z");
    private static final Instant NOW = Instant.parse("2026-09-05T01:00:00Z");

    @Mock
    private UserMergedTransactionService transactionService;
    @Mock
    private UserMergedInboxRepository inboxRepository;
    @Mock
    private UserMergedEventMetrics metrics;

    private UserMergedConsumerService service;
    private UserMergedEventRequest request;
    private NormalizedUserMergedEvent event;

    @BeforeEach
    void setUp() {
        request = new UserMergedEventRequest(
                EVENT_ID,
                1,
                SOURCE,
                TARGET,
                OCCURRED_AT.toString()
        );
        event = UserMergedEventNormalizer.normalize(request, NOW);
        service = new UserMergedConsumerService(
                transactionService,
                inboxRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                metrics
        );
    }

    @Test
    void unknownCommitConvergesToDuplicateWhenCommittedInboxIsVisible() {
        when(transactionService.consume(any())).thenThrow(unknownCommit());
        when(inboxRepository.findById(EVENT_ID)).thenReturn(Optional.of(inbox(event.payloadDigest())));

        assertThat(service.consume(request)).isEqualTo(UserMergedConsumeResult.DUPLICATE);

        verify(transactionService, times(1)).consume(any());
        verify(inboxRepository, times(1)).findById(EVENT_ID);
    }

    @Test
    void unknownCommitConvergesToConflictWhenAnotherPayloadWon() {
        when(transactionService.consume(any())).thenThrow(unknownCommit());
        when(inboxRepository.findById(EVENT_ID)).thenReturn(Optional.of(inbox("different-digest")));

        assertThatThrownBy(() -> service.consume(request))
                .isInstanceOfSatisfying(UserMergedEventException.class, failure ->
                        assertThat(failure.getReason())
                                .isEqualTo(UserMergedEventException.Reason.PAYLOAD_CONFLICT));

        verify(transactionService, times(1)).consume(any());
    }

    @Test
    void unknownCommitWithoutVisibleInboxReturnsProcessingUnavailable() {
        when(transactionService.consume(any())).thenThrow(unknownCommit());
        when(inboxRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consume(request))
                .isInstanceOfSatisfying(UserMergedEventException.class, failure ->
                        assertThat(failure.getReason())
                                .isEqualTo(UserMergedEventException.Reason.PROCESSING_UNAVAILABLE));

        verify(transactionService, times(1)).consume(any());
    }

    private UserMergedInboxEvent inbox(String digest) {
        return new UserMergedInboxEvent(
                EVENT_ID,
                1,
                digest,
                SOURCE,
                TARGET,
                OCCURRED_AT,
                NOW,
                NOW,
                UserMergedInboxStatus.PROCESSED
        );
    }

    private static UserOwnedCommitOutcomeUnknownException unknownCommit() {
        return new UserOwnedCommitOutcomeUnknownException(
                new IllegalStateException("commit response unavailable")
        );
    }
}
