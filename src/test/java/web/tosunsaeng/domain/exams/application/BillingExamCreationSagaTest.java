package web.tosunsaeng.domain.exams.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import web.tosunsaeng.domain.exams.billing.BillingClientException;
import web.tosunsaeng.domain.exams.billing.BillingReservationClient;
import web.tosunsaeng.domain.exams.billing.BillingSagaProperties;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind;
import web.tosunsaeng.domain.exams.domain.enums.ExamEntitlementState;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.repository.ExamCreationOperationRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingExamCreationSagaTest {

    private static final String USER_ID = "00000000-0000-4000-8000-000000000001";
    private static final String OPERATION_ID = "018f6f36-2f42-4bf5-8c17-0be35de4872c";
    private static final String SESSION_ID = "ex_saga_0828_1200";
    private static final String MOCK_EXAM_ID = "mock_exam_003";
    private static final String RESERVATION_ID = "018f6f36-2f42-4bf5-8c17-0be35de4872d";
    private static final String GROUP_ID = "018f6f36-2f42-4bf5-8c17-0be35de4872e";
    private static final Instant NOW = Instant.parse("2026-08-28T03:00:00Z");
    private static final Instant COMMITTED = Instant.parse("2026-08-28T03:00:01Z");
    private static final Instant CONFIRMED = Instant.parse("2026-08-28T03:00:02Z");

    @Mock
    private ExamCreationOperationRepository operationRepository;
    @Mock
    private ExamSessionRepository sessionRepository;
    @Mock
    private ExamSessionManager sessionManager;
    @Mock
    private MockExamCatalogService mockExamCatalogService;
    @Mock
    private BillingReservationClient billingClient;
    @Mock
    private BillingExamCreationTransactionService transactionService;

    private BillingExamCreationSaga saga;
    private MockExam mockExam;

    @BeforeEach
    void setUp() {
        BillingSagaProperties properties = new BillingSagaProperties();
        properties.setCreationSagaEnabled(true);
        mockExam = MockExam.builder()
                .mockExamId(MOCK_EXAM_ID)
                .title("Saga exam")
                .questions(List.of())
                .build();
        saga = new BillingExamCreationSaga(
                properties,
                operationRepository,
                sessionRepository,
                sessionManager,
                mockExamCatalogService,
                billingClient,
                transactionService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsSessionThroughReserveCommitAndConfirm() {
        ExamCreationOperation operation = stubPreparedOperation();
        when(billingClient.reserve(OPERATION_ID, USER_ID, SESSION_ID, MOCK_EXAM_ID))
                .thenReturn(reserved());
        when(sessionManager.findInProgressSessions(USER_ID)).thenReturn(List.of());
        when(operationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionService.commitReservedSession(operation.getCommandId(), NOW, ZoneOffset.UTC))
                .thenAnswer(invocation -> {
                    operation.markSessionCommitted(COMMITTED);
                    return operation;
                });
        when(billingClient.confirm(
                OPERATION_ID, RESERVATION_ID, USER_ID, SESSION_ID, COMMITTED))
                .thenReturn(confirmed());
        when(transactionService.finalizeConfirmed(operation.getCommandId(), CONFIRMED))
                .thenAnswer(invocation -> {
                    operation.markSucceeded(CONFIRMED, CONFIRMED.plusSeconds(604800));
                    return operation;
                });
        when(sessionRepository.findByUserIdAndCreationOperationId(USER_ID, OPERATION_ID))
                .thenReturn(Optional.empty(), Optional.of(confirmedSession()));
        when(mockExamCatalogService.getRequiredExam(MOCK_EXAM_ID)).thenReturn(mockExam);

        ExamSessionManager.Assignment result = saga.start(USER_ID, OPERATION_ID);

        assertEquals(SESSION_ID, result.session().getExamId());
        assertEquals(MOCK_EXAM_ID, result.mockExam().getMockExamId());
        verify(billingClient).reserve(OPERATION_ID, USER_ID, SESSION_ID, MOCK_EXAM_ID);
        verify(billingClient).confirm(
                OPERATION_ID, RESERVATION_ID, USER_ID, SESSION_ID, COMMITTED);
    }

    @Test
    void successfulSameKeyReplayReturnsExistingSessionWithoutBillingCall() {
        when(sessionRepository.findByUserIdAndCreationOperationId(USER_ID, OPERATION_ID))
                .thenReturn(Optional.of(confirmedSession()));
        when(mockExamCatalogService.getRequiredExam(MOCK_EXAM_ID)).thenReturn(mockExam);

        ExamSessionManager.Assignment result = saga.start(USER_ID, OPERATION_ID);

        assertEquals(SESSION_ID, result.session().getExamId());
        verifyNoInteractions(billingClient);
        verify(sessionManager, never()).prepareForBilling(USER_ID);
    }

    @Test
    void confirmTimeoutUsesStatusAndFinalizesConfirmedReservation() {
        ExamCreationOperation operation = committedOperation();
        when(operationRepository.findByUserIdAndOperationId(USER_ID, OPERATION_ID))
                .thenReturn(Optional.of(operation));
        when(operationRepository.findById(operation.getCommandId()))
                .thenReturn(Optional.of(operation));
        when(billingClient.confirm(
                OPERATION_ID, RESERVATION_ID, USER_ID, SESSION_ID, COMMITTED))
                .thenThrow(new BillingClientException(
                        BillingClientException.Category.TEMPORARILY_UNAVAILABLE, 2));
        when(billingClient.status(USER_ID, OPERATION_ID)).thenReturn(confirmedStatus());
        when(transactionService.finalizeConfirmed(operation.getCommandId(), CONFIRMED))
                .thenAnswer(invocation -> {
                    operation.markSucceeded(CONFIRMED, CONFIRMED.plusSeconds(604800));
                    return operation;
                });
        when(sessionRepository.findByUserIdAndCreationOperationId(USER_ID, OPERATION_ID))
                .thenReturn(Optional.empty(), Optional.of(confirmedSession()));
        when(mockExamCatalogService.getRequiredExam(MOCK_EXAM_ID)).thenReturn(mockExam);

        ExamSessionManager.Assignment result = saga.start(USER_ID, OPERATION_ID);

        assertEquals(SESSION_ID, result.session().getExamId());
        verify(billingClient).status(USER_ID, OPERATION_ID);
    }

    @Test
    void confirmTimeoutWithCanceledStatusAbandonsCommittedOperation() {
        ExamCreationOperation operation = committedOperation();
        when(operationRepository.findByUserIdAndOperationId(USER_ID, OPERATION_ID))
                .thenReturn(Optional.of(operation));
        when(operationRepository.findById(operation.getCommandId()))
                .thenReturn(Optional.of(operation));
        when(billingClient.confirm(
                OPERATION_ID, RESERVATION_ID, USER_ID, SESSION_ID, COMMITTED))
                .thenThrow(new BillingClientException(
                        BillingClientException.Category.TEMPORARILY_UNAVAILABLE, 2));
        when(billingClient.status(USER_ID, OPERATION_ID)).thenReturn(canceledStatus());
        when(transactionService.markCanceled(operation.getCommandId(), CONFIRMED))
                .thenAnswer(invocation -> {
                    operation.markCanceled(CONFIRMED, CONFIRMED.plusSeconds(604800));
                    return operation;
                });
        when(sessionRepository.findByUserIdAndCreationOperationId(USER_ID, OPERATION_ID))
                .thenReturn(Optional.empty());

        ExamsException failure = assertThrows(
                ExamsException.class,
                () -> saga.start(USER_ID, OPERATION_ID)
        );

        assertEquals(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE, failure.getCode());
        verify(transactionService).markCanceled(operation.getCommandId(), CONFIRMED);
    }

    @Test
    void localCommitFailureCancelsReservationAndReturnsRetryableFailure() {
        ExamCreationOperation operation = reservedOperation();
        when(operationRepository.findByUserIdAndOperationId(USER_ID, OPERATION_ID))
                .thenReturn(Optional.of(operation));
        when(operationRepository.findById(operation.getCommandId()))
                .thenReturn(Optional.of(operation));
        when(transactionService.commitReservedSession(operation.getCommandId(), NOW, ZoneOffset.UTC))
                .thenThrow(new IllegalStateException("transaction failed"));
        when(billingClient.cancel(OPERATION_ID, RESERVATION_ID, USER_ID))
                .thenReturn(canceled());

        ExamsException failure = assertThrows(
                ExamsException.class,
                () -> saga.start(USER_ID, OPERATION_ID)
        );

        assertEquals(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE, failure.getCode());
        verify(transactionService).markCanceled(operation.getCommandId(), CONFIRMED);
        verify(billingClient, never()).confirm(any(), any(), any(), any(), any());
    }

    @Test
    void concurrentSameKeyCommitDoesNotCancelSharedReservation() {
        ExamCreationOperation operation = reservedOperation();
        when(operationRepository.findByUserIdAndOperationId(USER_ID, OPERATION_ID))
                .thenReturn(Optional.of(operation));
        when(operationRepository.findById(operation.getCommandId()))
                .thenReturn(Optional.of(operation));
        when(transactionService.commitReservedSession(operation.getCommandId(), NOW, ZoneOffset.UTC))
                .thenThrow(new OptimisticLockingFailureException("concurrent commit"));
        when(sessionRepository.findByUserIdAndCreationOperationId(USER_ID, OPERATION_ID))
                .thenReturn(Optional.empty());

        ExamsException failure = assertThrows(
                ExamsException.class,
                () -> saga.start(USER_ID, OPERATION_ID)
        );

        assertEquals(ErrorStatus._EXAM_CREATION_PROCESSING, failure.getCode());
        verify(billingClient, never()).cancel(any(), any(), any());
        verify(billingClient, never()).confirm(any(), any(), any(), any(), any());
    }

    @Test
    void insufficientEntitlementBecomesStablePublicError() {
        ExamCreationOperation operation = stubPreparedOperation();
        when(billingClient.reserve(OPERATION_ID, USER_ID, SESSION_ID, MOCK_EXAM_ID))
                .thenThrow(new BillingClientException(
                        BillingClientException.Category.ENTITLEMENT_INSUFFICIENT, null));

        ExamsException failure = assertThrows(
                ExamsException.class,
                () -> saga.start(USER_ID, OPERATION_ID)
        );

        assertEquals(ErrorStatus._ENTITLEMENT_INSUFFICIENT, failure.getCode());
        verify(transactionService).markFailedTerminal(
                operation.getCommandId(), "ENTITLEMENT_INSUFFICIENT", NOW);
    }

    @Test
    void malformedBillingReservationIdentifiersFailClosedBeforeSessionCommit() {
        ExamCreationOperation operation = stubPreparedOperation();
        when(billingClient.reserve(OPERATION_ID, USER_ID, SESSION_ID, MOCK_EXAM_ID))
                .thenReturn(new BillingReservationClient.ReservationSnapshot(
                        OPERATION_ID,
                        "../unexpected-path",
                        BillingReservationKind.INITIAL,
                        BillingReservationClient.ReservationStatus.RESERVED,
                        GROUP_ID,
                        null,
                        SESSION_ID,
                        MOCK_EXAM_ID,
                        NOW.plusSeconds(300),
                        null
                ));

        ExamsException failure = assertThrows(
                ExamsException.class,
                () -> saga.start(USER_ID, OPERATION_ID)
        );

        assertEquals(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE, failure.getCode());
        verify(transactionService).markFailedTerminal(
                operation.getCommandId(), "RESERVE_CONTRACT_MISMATCH", NOW);
        verify(transactionService, never()).commitReservedSession(
                any(String.class), any(Instant.class), any(ZoneId.class));
    }

    private ExamCreationOperation stubPreparedOperation() {
        ExamCreationOperation operation = preparedOperation();
        when(operationRepository.findByUserIdAndOperationId(USER_ID, OPERATION_ID))
                .thenReturn(Optional.empty());
        when(operationRepository.findByUserIdAndActiveGuardTrue(USER_ID))
                .thenReturn(Optional.empty());
        when(sessionManager.prepareForBilling(USER_ID)).thenReturn(
                new ExamSessionManager.PreparedAssignment(
                        SESSION_ID,
                        mockExam,
                        1,
                        LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
                )
        );
        when(operationRepository.insert(any(ExamCreationOperation.class))).thenReturn(operation);
        when(operationRepository.findById(operation.getCommandId())).thenReturn(Optional.of(operation));
        return operation;
    }

    private static ExamCreationOperation preparedOperation() {
        return ExamCreationOperation.prepared(
                USER_ID, OPERATION_ID, SESSION_ID, MOCK_EXAM_ID, 1, NOW);
    }

    private static ExamCreationOperation reservedOperation() {
        ExamCreationOperation operation = preparedOperation();
        operation.markReserved(
                RESERVATION_ID, BillingReservationKind.INITIAL, GROUP_ID,
                NOW.plusSeconds(300), NOW);
        return operation;
    }

    private static ExamCreationOperation committedOperation() {
        ExamCreationOperation operation = reservedOperation();
        operation.markSessionCommitted(COMMITTED);
        return operation;
    }

    private static ExamCreationOperation succeededOperation() {
        ExamCreationOperation operation = committedOperation();
        operation.markSucceeded(CONFIRMED, CONFIRMED.plusSeconds(604800));
        return operation;
    }

    private static BillingReservationClient.ReservationSnapshot reserved() {
        return new BillingReservationClient.ReservationSnapshot(
                OPERATION_ID, RESERVATION_ID, BillingReservationKind.INITIAL,
                BillingReservationClient.ReservationStatus.RESERVED,
                GROUP_ID, null, SESSION_ID, MOCK_EXAM_ID,
                NOW.plusSeconds(300), null
        );
    }

    private static BillingReservationClient.ReservationSnapshot confirmed() {
        return new BillingReservationClient.ReservationSnapshot(
                OPERATION_ID, RESERVATION_ID, null,
                BillingReservationClient.ReservationStatus.CONFIRMED,
                GROUP_ID, BillingReservationClient.AttemptGroupStatus.OPEN, SESSION_ID, null,
                null, CONFIRMED
        );
    }

    private static BillingReservationClient.ReservationSnapshot confirmedStatus() {
        return new BillingReservationClient.ReservationSnapshot(
                OPERATION_ID, RESERVATION_ID, BillingReservationKind.INITIAL,
                BillingReservationClient.ReservationStatus.CONFIRMED,
                GROUP_ID, BillingReservationClient.AttemptGroupStatus.OPEN, SESSION_ID, MOCK_EXAM_ID,
                null, CONFIRMED
        );
    }

    private static BillingReservationClient.ReservationSnapshot canceled() {
        return new BillingReservationClient.ReservationSnapshot(
                OPERATION_ID, RESERVATION_ID, null,
                BillingReservationClient.ReservationStatus.CANCELED,
                null, null, null, null, null, CONFIRMED
        );
    }

    private static BillingReservationClient.ReservationSnapshot canceledStatus() {
        return new BillingReservationClient.ReservationSnapshot(
                OPERATION_ID, RESERVATION_ID, BillingReservationKind.INITIAL,
                BillingReservationClient.ReservationStatus.CANCELED,
                GROUP_ID, null, SESSION_ID, MOCK_EXAM_ID,
                null, CONFIRMED
        );
    }

    private static ExamSession confirmedSession() {
        return ExamSession.builder()
                .examId(SESSION_ID)
                .userId(USER_ID)
                .createdAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC))
                .mockExamId(MOCK_EXAM_ID)
                .cycleNumber(1)
                .active(true)
                .status(ExamSessionStatus.IN_PROGRESS)
                .creationOperationId(OPERATION_ID)
                .billingReservationId(RESERVATION_ID)
                .billingReservationKind(BillingReservationKind.INITIAL)
                .attemptGroupId(GROUP_ID)
                .entitlementState(ExamEntitlementState.CONFIRMED)
                .entitlementConfirmedAt(CONFIRMED)
                .build();
    }
}
