package web.tosunsaeng.domain.exams.attemptgroup;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionOperations;
import web.tosunsaeng.domain.exams.attemptgroup.application.AttemptGroupEventMetrics;
import web.tosunsaeng.domain.exams.attemptgroup.application.AttemptGroupEvidenceEvaluator;
import web.tosunsaeng.domain.exams.attemptgroup.application.AttemptGroupStateCoordinator;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupEventOutbox;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupProjectionStatus;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventCanonicalizer;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupTraceContext;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupOutboxStore;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.enums.ExamEntitlementState;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptGroupStateCoordinatorTest {
    @Mock ExamSessionRepository sessionRepository;
    @Mock AttemptGroupOutboxStore outboxStore;
    @Mock AttemptGroupEvidenceEvaluator evaluator;
    @Mock AttemptGroupTraceContext traceContext;
    @Mock AttemptGroupEventMetrics metrics;
    @Mock ObjectProvider<TransactionOperations> transactionProvider;
    @Mock TransactionOperations transactions;

    @Test
    void gradingTransitionAndOutboxInsertShareTransactionBoundary() {
        Instant now = Instant.parse("2026-09-01T03:00:00Z");
        ExamSession session = ExamSession.builder()
                .examId("ex_group").userId("00000000-0000-4000-8000-000000000001")
                .mockExamId("mock_exam_001").status(ExamSessionStatus.IN_PROGRESS).active(true)
                .entitlementState(ExamEntitlementState.CONFIRMED)
                .attemptGroupId("018f6f36-2f42-4bf5-8c17-0be35de4872e")
                .attemptGroupProjectionStatus(AttemptGroupProjectionStatus.OPEN)
                .build();
        when(transactionProvider.getIfAvailable()).thenReturn(transactions);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
        when(sessionRepository.findById("ex_group")).thenReturn(Optional.of(session));
        when(evaluator.evaluate(session, now)).thenReturn(
                new AttemptGroupEvidenceEvaluator.Evaluation(true, false, null, null, true));
        when(outboxStore.findBySessionAndSlot(any(), any())).thenReturn(Optional.empty());
        when(traceContext.captureOrCreate()).thenReturn(
                new AttemptGroupTraceContext.StoredContext(
                        "0123456789abcdef0123456789abcdef", "0123456789abcdef", "01", false));

        AttemptGroupStateCoordinator coordinator = new AttemptGroupStateCoordinator(
                AttemptGroupEvidenceEvaluatorTest.properties(), sessionRepository, outboxStore,
                evaluator, new AttemptGroupEventCanonicalizer(new ObjectMapper().findAndRegisterModules()),
                traceContext, metrics, transactionProvider, Clock.fixed(now, ZoneOffset.UTC));

        coordinator.reconcile("ex_group");

        ArgumentCaptor<AttemptGroupEventOutbox> event = ArgumentCaptor.forClass(AttemptGroupEventOutbox.class);
        verify(outboxStore).insert(event.capture());
        verify(sessionRepository).save(session);
        assertEquals(AttemptGroupProjectionStatus.GRADING,
                session.getAttemptGroupProjectionStatus());
        assertNotNull(session.getGradingEventId());
        assertEquals(session.getGradingEventId(), event.getValue().getEventId());
    }
}
