package web.tosunsaeng.domain.exams.attemptgroup.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionOperations;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupCompletionEvidence;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupProjectionStatus;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventCanonicalizer;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventProperties;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupOutboxStore;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupTraceContext;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.enums.ExamEntitlementState;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptGroupStateCoordinatorTransactionTest {
    private static final Instant NOW = Instant.parse("2026-09-01T03:00:00Z");

    @Mock ExamSessionRepository sessionRepository;
    @Mock AttemptGroupOutboxStore outboxStore;
    @Mock AttemptGroupEvidenceEvaluator evaluator;
    @Mock AttemptGroupTraceContext traceContext;
    @Mock AttemptGroupEventMetrics metrics;
    @Mock ObjectProvider<TransactionOperations> transactionProvider;

    @Test
    void participatingTransactionPropagatesDuplicateForOuterRetry() {
        ExamSession session = ExamSession.builder()
                .examId("ex_terminal_race")
                .userId("00000000-0000-4000-8000-000000000001")
                .mockExamId("mock_exam_001")
                .status(ExamSessionStatus.IN_PROGRESS)
                .active(true)
                .entitlementState(ExamEntitlementState.CONFIRMED)
                .attemptGroupId("018f6f36-2f42-4bf5-8c17-0be35de4872e")
                .attemptGroupProjectionStatus(AttemptGroupProjectionStatus.GRADING)
                .build();
        when(sessionRepository.findById(session.getExamId())).thenReturn(Optional.of(session));
        when(evaluator.evaluate(session, NOW)).thenReturn(
                new AttemptGroupEvidenceEvaluator.Evaluation(
                        true, true, AttemptGroupCompletionEvidence.complete(), null, false));
        when(outboxStore.findBySessionAndSlot(any(), any())).thenReturn(Optional.empty());
        when(traceContext.captureOrCreate()).thenReturn(new AttemptGroupTraceContext.StoredContext(
                "0123456789abcdef0123456789abcdef", "0123456789abcdef", "01", false));
        when(outboxStore.insert(any())).thenThrow(new DuplicateKeyException("terminal slot race"));
        AttemptGroupStateCoordinator coordinator = new AttemptGroupStateCoordinator(
                properties(), sessionRepository, outboxStore, evaluator,
                new AttemptGroupEventCanonicalizer(new ObjectMapper().findAndRegisterModules()),
                traceContext, metrics, transactionProvider, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(DuplicateKeyException.class,
                () -> coordinator.reconcileWithinTransaction(session.getExamId()));

        verify(sessionRepository, never()).save(any());
    }

    private static AttemptGroupEventProperties properties() {
        return new AttemptGroupEventProperties(true, false, "", "ap-northeast-2",
                Duration.ofMinutes(30), Duration.ofSeconds(1), 20, Duration.ofSeconds(30),
                Duration.ofMinutes(15), Duration.ofSeconds(2), Duration.ofSeconds(5),
                Duration.ofDays(30), Duration.ofDays(90));
    }
}
