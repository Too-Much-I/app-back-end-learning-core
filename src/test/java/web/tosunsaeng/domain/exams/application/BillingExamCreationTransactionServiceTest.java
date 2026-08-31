package web.tosunsaeng.domain.exams.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind;
import web.tosunsaeng.domain.exams.domain.enums.ExamEntitlementState;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.repository.ExamCreationOperationRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingExamCreationTransactionServiceTest {

    @Mock
    private ExamCreationOperationRepository operationRepository;
    @Mock
    private ExamSessionRepository sessionRepository;
    @Mock
    private ExamSessionManager sessionManager;
    @Mock
    private ObjectProvider<TransactionOperations> transactionProvider;

    private BillingExamCreationTransactionService service;

    @BeforeEach
    void setUp() {
        TransactionOperations directTransactions = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
        when(transactionProvider.getIfAvailable()).thenReturn(directTransactions);
        service = new BillingExamCreationTransactionService(
                operationRepository,
                sessionRepository,
                sessionManager,
                transactionProvider
        );
    }

    @Test
    void commitAtomicallyAbandonsOldSessionAndInsertsConfirmingSessionWithBillingMapping() {
        Instant preparedAt = Instant.parse("2026-08-28T03:00:00Z");
        Instant committedAt = Instant.parse("2026-08-28T03:00:01Z");
        ExamCreationOperation operation = ExamCreationOperation.prepared(
                "00000000-0000-4000-8000-000000000001",
                "018f6f36-2f42-4bf5-8c17-0be35de4872c",
                "ex_new",
                "mock_exam_003",
                2,
                preparedAt
        );
        operation.markReserved(
                "018f6f36-2f42-4bf5-8c17-0be35de4872d",
                BillingReservationKind.REPLACEMENT,
                "018f6f36-2f42-4bf5-8c17-0be35de4872e",
                preparedAt.plusSeconds(300),
                preparedAt
        );
        ExamSession oldSession = ExamSession.builder()
                .examId("ex_old")
                .userId(operation.getUserId())
                .active(true)
                .status(ExamSessionStatus.IN_PROGRESS)
                .build();
        when(operationRepository.findById(operation.getCommandId()))
                .thenReturn(Optional.of(operation));
        when(sessionManager.findInProgressSessions(operation.getUserId()))
                .thenReturn(List.of(oldSession));
        when(sessionRepository.insert(any(ExamSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(operationRepository.save(operation)).thenReturn(operation);

        service.commitReservedSession(operation.getCommandId(), committedAt, ZoneOffset.UTC);

        verify(sessionRepository).abandonIfInProgress("ex_old");
        ArgumentCaptor<ExamSession> inserted = ArgumentCaptor.forClass(ExamSession.class);
        verify(sessionRepository).insert(inserted.capture());
        ExamSession session = inserted.getValue();
        assertEquals("ex_new", session.getExamId());
        assertEquals(ExamSessionStatus.ENTITLEMENT_CONFIRMING, session.getStatus());
        assertEquals(ExamEntitlementState.CONFIRMING, session.getEntitlementState());
        assertEquals(operation.getOperationId(), session.getCreationOperationId());
        assertEquals(operation.getReservationId(), session.getBillingReservationId());
        assertEquals(operation.getAttemptGroupId(), session.getAttemptGroupId());
        assertNull(session.getEntitlementConfirmedAt());
        assertEquals(committedAt, operation.getSessionCommittedAt());
    }
}
