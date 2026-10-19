package web.tosunsaeng.domain.exams.attemptgroup.application;

import com.mongodb.MongoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import web.tosunsaeng.domain.exams.application.ExamGradingService;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptGroupSummaryCompletionServiceTest {
    private static final String EXAM_ID = "ex_summary_transaction";
    private static final int GENERATION_ATTEMPT = 2;

    @Mock ExamSummaryRepository summaryRepository;
    @Mock ExamGradingService gradingService;
    @Mock AttemptGroupStateCoordinator coordinator;
    @Mock ObjectProvider<TransactionOperations> transactionProvider;
    @Mock TransactionOperations transactions;
    @Mock TransactionStatus transactionStatus;
    private AttemptGroupSummaryCompletionService service;
    private ExamSummary summary;

    @BeforeEach
    void setUp() {
        service = new AttemptGroupSummaryCompletionService(
                summaryRepository, gradingService, coordinator, transactionProvider);
        summary = summary("00000000-0000-4000-8000-000000000001");
        when(transactionProvider.getIfAvailable()).thenReturn(transactions);
        when(coordinator.manages(EXAM_ID)).thenReturn(true);
        executeCallbacksInCurrentTestTransaction();
    }

    @Test
    void duplicateInsertRollsBackBeforeRetryingWholeUnitInNewTransaction() {
        when(summaryRepository.findById(summary.getId()))
                .thenReturn(Optional.empty(), Optional.of(summary));
        when(summaryRepository.insert(summary)).thenThrow(new DuplicateKeyException("duplicate"));
        when(gradingService.completeSummary(EXAM_ID, GENERATION_ATTEMPT)).thenReturn(true);

        assertTrue(service.persistAndComplete(summary, EXAM_ID, GENERATION_ATTEMPT));

        verify(transactions, times(2)).execute(any());
        verify(summaryRepository).insert(summary);
        verify(gradingService).completeSummary(EXAM_ID, GENERATION_ATTEMPT);
        verify(coordinator).reconcileWithinTransaction(EXAM_ID);
        verify(coordinator, never()).reconcile(EXAM_ID);
    }

    @Test
    void incompleteSummaryJobRollsBackSummaryAndSkipsCoordinator() {
        when(summaryRepository.findById(summary.getId())).thenReturn(Optional.empty());
        when(summaryRepository.insert(summary)).thenReturn(summary);
        when(gradingService.completeSummary(EXAM_ID, GENERATION_ATTEMPT)).thenReturn(false);

        assertFalse(service.persistAndComplete(summary, EXAM_ID, GENERATION_ATTEMPT));

        verify(transactionStatus).setRollbackOnly();
        verify(coordinator, never()).reconcileWithinTransaction(any());
    }

    @Test
    void unknownCommitResultRetriesTheWholeUnit() {
        MongoException mongoFailure = new MongoException("unknown commit result");
        mongoFailure.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        TransactionSystemException unknownCommit =
                new TransactionSystemException("commit result unknown", mongoFailure);
        doThrow(unknownCommit)
                .doAnswer(invocation -> callback(invocation).doInTransaction(transactionStatus))
                .when(transactions).execute(any());
        when(summaryRepository.findById(summary.getId())).thenReturn(Optional.of(summary));
        when(gradingService.completeSummary(EXAM_ID, GENERATION_ATTEMPT)).thenReturn(true);

        assertTrue(service.persistAndComplete(summary, EXAM_ID, GENERATION_ATTEMPT));

        verify(transactions, times(2)).execute(any());
        verify(summaryRepository, never()).insert(any(ExamSummary.class));
        verify(coordinator).reconcileWithinTransaction(EXAM_ID);
    }

    @Test
    void existingSummaryWithDifferentOwnerFailsClosed() {
        when(summaryRepository.findById(summary.getId()))
                .thenReturn(Optional.of(summary("00000000-0000-4000-8000-000000000002")));

        assertThrows(IllegalStateException.class,
                () -> service.persistAndComplete(summary, EXAM_ID, GENERATION_ATTEMPT));

        verify(gradingService, never()).completeSummary(any(), any(Integer.class));
        verify(coordinator, never()).reconcileWithinTransaction(any());
    }

    private void executeCallbacksInCurrentTestTransaction() {
        lenient().doAnswer(invocation -> callback(invocation).doInTransaction(transactionStatus))
                .when(transactions).execute(any());
    }

    @SuppressWarnings("unchecked")
    private static TransactionCallback<Boolean> callback(org.mockito.invocation.InvocationOnMock invocation) {
        return invocation.getArgument(0, TransactionCallback.class);
    }

    private static ExamSummary summary(String userId) {
        return ExamSummary.builder()
                .id("summary:" + EXAM_ID + ":v1")
                .examId(EXAM_ID)
                .userId(userId)
                .mockExamId("mock_exam_001")
                .totalScore(160)
                .partFeedback(Map.of("part1", "feedback"))
                .build();
    }
}
