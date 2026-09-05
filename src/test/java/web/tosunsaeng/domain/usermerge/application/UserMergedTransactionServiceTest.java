package web.tosunsaeng.domain.usermerge.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.repository.ExamCreationOperationRepository;
import web.tosunsaeng.domain.usermerge.domain.UserMergedInboxEvent;
import web.tosunsaeng.domain.usermerge.domain.UserMergedInboxStatus;
import web.tosunsaeng.domain.usermerge.repository.UserMergedInboxRepository;
import web.tosunsaeng.domain.withdrawal.repository.WithdrawnUserAccessDenyRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserMergedTransactionServiceTest {

    private static final String EVENT_ID = "9a88bc80-d73a-4a3d-8f68-492641d27208";
    private static final String SOURCE = "73a18ed4-1d56-4c4f-afd6-b39175b82a86";
    private static final String TARGET = "45c05c3f-ae7f-4ca7-af88-3ab8aa8f428e";
    private static final Instant NOW = Instant.parse("2026-08-20T02:00:01Z");

    @Mock
    private UserMergedInboxRepository inboxRepository;
    @Mock
    private WithdrawnUserAccessDenyRepository withdrawalRepository;
    @Mock
    private ExamCreationOperationRepository operationRepository;
    @Mock
    private UserOwnershipGuardService guardService;
    @Mock
    private UserOwnedTransactionExecutor transactionExecutor;
    @Mock
    private MongoTemplate mongoTemplate;

    private UserMergedTransactionService service;
    private NormalizedUserMergedEvent event;

    @BeforeEach
    void setUp() {
        service = new UserMergedTransactionService(
                inboxRepository,
                withdrawalRepository,
                operationRepository,
                guardService,
                transactionExecutor,
                mongoTemplate
        );
        event = new NormalizedUserMergedEvent(
                EVENT_ID,
                1,
                "digest",
                SOURCE,
                TARGET,
                Instant.parse("2026-08-20T02:00:00Z"),
                NOW
        );
        when(transactionExecutor.executeWithoutGuard(any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(0)).get());
        org.mockito.Mockito.lenient().when(inboxRepository.findById(EVENT_ID)).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(withdrawalRepository.findById(any())).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(operationRepository.findByUserIdAndActiveGuardTrue(any()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(mongoTemplate.find(
                        any(), eq(web.tosunsaeng.domain.exams.domain.entity.ExamSession.class)))
                .thenReturn(List.of());
    }

    @Test
    void migratesOwnersMarksSourceMergedAndStoresProcessedInbox() {
        assertThat(service.consume(event)).isEqualTo(UserMergedConsumeResult.PROCESSED);

        verify(guardService).touchActive(TARGET, NOW);
        verify(guardService).touchActive(SOURCE, NOW);
        verify(guardService).markMerged(
                SOURCE,
                TARGET,
                EVENT_ID,
                Instant.parse("2026-08-20T02:00:00Z"),
                NOW
        );
        verify(inboxRepository).insert(any(UserMergedInboxEvent.class));
        verify(mongoTemplate, org.mockito.Mockito.times(3)).updateMulti(any(), any(), any(Class.class));
    }

    @Test
    void sameDigestProcessedEventIsDuplicateWithoutMutation() {
        when(inboxRepository.findById(EVENT_ID)).thenReturn(Optional.of(new UserMergedInboxEvent(
                EVENT_ID,
                1,
                "digest",
                SOURCE,
                TARGET,
                event.occurredAt(),
                NOW,
                NOW,
                UserMergedInboxStatus.PROCESSED
        )));

        assertThat(service.consume(event)).isEqualTo(UserMergedConsumeResult.DUPLICATE);
        verify(guardService, never()).touchActive(any(), any());
        verify(mongoTemplate, never()).updateMulti(any(), any(), any(Class.class));
    }

    @Test
    void differentDigestIsAConflictWithoutMutation() {
        when(inboxRepository.findById(EVENT_ID)).thenReturn(Optional.of(new UserMergedInboxEvent(
                EVENT_ID,
                1,
                "different",
                SOURCE,
                TARGET,
                event.occurredAt(),
                NOW,
                NOW,
                UserMergedInboxStatus.PROCESSED
        )));

        assertReason(UserMergedEventException.Reason.PAYLOAD_CONFLICT);
        verify(guardService, never()).touchActive(any(), any());
    }

    @Test
    void nonTerminalCreationOperationReturnsRetryablePreconditionBeforeGuardTouch() {
        ExamCreationOperation operation = ExamCreationOperation.prepared(
                SOURCE,
                "bb88bc80-d73a-4a3d-8f68-492641d27208",
                "exam",
                "mock",
                1,
                NOW
        );
        when(operationRepository.findByUserIdAndActiveGuardTrue(SOURCE))
                .thenReturn(Optional.of(operation));

        assertReason(UserMergedEventException.Reason.RETRYABLE_PRECONDITION);
        verify(guardService, never()).touchActive(any(), any());
    }

    private void assertReason(UserMergedEventException.Reason reason) {
        assertThatThrownBy(() -> service.consume(event))
                .isInstanceOfSatisfying(UserMergedEventException.class, failure ->
                        assertThat(failure.getReason()).isEqualTo(reason));
    }
}
