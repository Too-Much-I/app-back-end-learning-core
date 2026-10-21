package web.tosunsaeng.domain.exams.attemptgroup.application;

import com.mongodb.MongoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import web.tosunsaeng.domain.exams.application.ExamSubmissionEvidence;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupProjectionStatus;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventProperties;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.enums.ExamEntitlementState;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import java.time.Clock;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttemptGroupSubmissionRecoveryTest {
    final ExamSessionRepository repository = mock(ExamSessionRepository.class);
    final ExamSubmissionEvidence evidence = mock(ExamSubmissionEvidence.class);
    final TransactionOperations transactions = mock(TransactionOperations.class);
    final Runnable access = mock(Runnable.class);
    final web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor owner =
            mock(web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor.class);
    AttemptGroupStateCoordinator coordinator;
    ExamSession session;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        var properties = mock(AttemptGroupEventProperties.class);
        when(properties.writerEnabled()).thenReturn(true);
        ObjectProvider<TransactionOperations> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(transactions);
        coordinator = new AttemptGroupStateCoordinator(properties, repository, null, null, null, null, null,
                provider, Clock.systemUTC());
        ReflectionTestUtils.setField(coordinator, "submissionEvidence", evidence);
        ReflectionTestUtils.setField(coordinator, "userOwnedTransactionExecutor", owner);
        when(owner.enabled()).thenReturn(true);
        session = ExamSession.builder().examId("ex").userId("owner").status(ExamSessionStatus.IN_PROGRESS)
                .active(true).entitlementState(ExamEntitlementState.CONFIRMED).attemptGroupId("group")
                .attemptGroupProjectionStatus(AttemptGroupProjectionStatus.GRADING).build();
    }

    MongoException fail(boolean unknown) {
        var error = new MongoException(112, "private message");
        error.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        if (unknown) error.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        doThrow(error).when(transactions).executeWithoutResult(any());
        return error;
    }
    void accepted() {
        when(repository.findById("ex")).thenReturn(Optional.of(session));
        when(evidence.acceptedQuestion(session, 3, 0)).thenReturn(true);
    }
    @Test void exhaustedTransientAfterCommittedAdmissionDefersToExistingWorker() {
        fail(false); accepted();
        assertDoesNotThrow(() -> coordinator.reconcileAfterSubmission("ex", 3, 0, access));
        verify(transactions, times(3)).executeWithoutResult(any());
        verify(access).run();
        verify(repository, never()).save(any());
    }
    @Test void successDoesNotPerformAdditionalAdmissionReads() {
        coordinator.reconcileAfterSubmission("ex", 3, 0, access);
        verifyNoInteractions(repository, evidence, access);
    }
    @Test void unknownCommitIsNotSuppressedOrRetried() {
        var error = fail(true);
        assertSame(error, assertThrows(MongoException.class,
                () -> coordinator.reconcileAfterSubmission("ex", 3, 0, access)));
        verify(transactions).executeWithoutResult(any());
        verifyNoInteractions(repository, evidence, access);
    }
    @Test void missingReceiptIsNotAccepted() {
        var error = fail(false);
        when(repository.findById("ex")).thenReturn(Optional.of(session));
        assertSame(error, assertThrows(MongoException.class,
                () -> coordinator.reconcileAfterSubmission("ex", 3, 0, access)));
        verifyNoInteractions(access);
    }
    @Test void unprovenLegacySessionDoesNotPromiseBillingRecovery() {
        var error = fail(false);
        when(repository.findById("ex")).thenReturn(Optional.of(ExamSession.builder().examId("ex").build()));
        assertSame(error, assertThrows(MongoException.class,
                () -> coordinator.reconcileAfterSubmission("ex", 3, 0, access)));
        verifyNoInteractions(evidence, access);
    }
    @Test void missingSessionIsNotAccepted() {
        var error = fail(false);
        assertSame(error, assertThrows(MongoException.class,
                () -> coordinator.reconcileAfterSubmission("ex", 3, 0, access)));
        verifyNoInteractions(evidence, access);
    }
    @Test void explicitTransitionFreeAdmissionNeedsNoBillingProjectionRecovery() {
        fail(false);
        session = ExamSession.builder().examId("ex").userId("owner")
                .creationOrigin(ExamSession.TRANSITION_FREE_V1).submissionEvidenceVersion(1)
                .requiredQuestionNumbers(java.util.List.of(3)).build();
        accepted();
        assertDoesNotThrow(() -> coordinator.reconcileAfterSubmission("ex", 3, 0, access));
        verify(access).run();
    }
    @Test void concurrentAccessDenialIsNotSuppressed() {
        fail(false); accepted();
        var denial = new IllegalStateException("denied");
        doThrow(denial).when(access).run();
        assertSame(denial, assertThrows(IllegalStateException.class,
                () -> coordinator.reconcileAfterSubmission("ex", 3, 0, access)));
    }
    @Test void mergedOwnerIsNotSuppressed() {
        fail(false); accepted();
        var denial = new web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardException(
                web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardException.Reason.ALREADY_MERGED);
        doThrow(denial).when(owner).requireDeferredExamCoordinationAllowed("owner", "ex");
        assertSame(denial, assertThrows(RuntimeException.class,
                () -> coordinator.reconcileAfterSubmission("ex", 3, 0, access)));
        verifyNoInteractions(access);
    }
    @Test void evidenceReadFailureIsNotSuppressed() {
        fail(false);
        var unavailable = new IllegalStateException("unavailable");
        when(repository.findById("ex")).thenThrow(unavailable);
        assertSame(unavailable, assertThrows(IllegalStateException.class,
                () -> coordinator.reconcileAfterSubmission("ex", 3, 0, access)));
    }
    @Test void permanentOrDuplicateErrorIsNotAccepted() {
        for (RuntimeException error : new RuntimeException[] {new IllegalStateException(),
                new org.springframework.dao.DuplicateKeyException("duplicate")}) {
            doThrow(error).when(transactions).executeWithoutResult(any());
            assertSame(error, assertThrows(RuntimeException.class,
                    () -> coordinator.reconcileAfterSubmission("ex", 3, 0, access)));
        }
        verifyNoInteractions(repository, evidence, access);
    }
    @Test void participatingTransactionCannotReturnSuccessAfterAbort() {
        var error = fail(false);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(error, assertThrows(MongoException.class,
                    () -> coordinator.reconcileAfterSubmission("ex", 3, 0, access)));
            verify(transactions).executeWithoutResult(any());
            verifyNoInteractions(repository, evidence, access);
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }
}
