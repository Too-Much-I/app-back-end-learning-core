package web.tosunsaeng.domain.exams.attemptgroup.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupEventOutbox;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupEventPayload;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupEventSlot;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupEventTarget;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventCanonicalizer;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventProperties;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupTraceContext;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupOutboxStore;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.enums.ExamEntitlementState;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class AttemptGroupStateCoordinator {
    private final AttemptGroupEventProperties properties;
    private final ExamSessionRepository sessionRepository;
    private final AttemptGroupOutboxStore outboxStore;
    private final AttemptGroupEvidenceEvaluator evaluator;
    private final AttemptGroupEventCanonicalizer canonicalizer;
    private final AttemptGroupTraceContext traceContext;
    private final AttemptGroupEventMetrics metrics;
    private final ObjectProvider<TransactionOperations> transactionProvider;
    private final Clock clock;

    public AttemptGroupStateCoordinator(
            AttemptGroupEventProperties properties,
            ExamSessionRepository sessionRepository,
            AttemptGroupOutboxStore outboxStore,
            AttemptGroupEvidenceEvaluator evaluator,
            AttemptGroupEventCanonicalizer canonicalizer,
            AttemptGroupTraceContext traceContext,
            AttemptGroupEventMetrics metrics,
            @Qualifier("attemptGroupTransactionOperations")
            ObjectProvider<TransactionOperations> transactionProvider,
            @Qualifier("gradingClock") Clock clock
    ) {
        this.properties = properties;
        this.sessionRepository = sessionRepository;
        this.outboxStore = outboxStore;
        this.evaluator = evaluator;
        this.canonicalizer = canonicalizer;
        this.traceContext = traceContext;
        this.metrics = metrics;
        this.transactionProvider = transactionProvider;
        this.clock = clock;
    }

    public void reconcile(String examId) {
        if (!properties.writerEnabled()) {
            return;
        }
        TransactionOperations transactions = transactionProvider.getIfAvailable();
        if (transactions == null) {
            throw new IllegalStateException("AttemptGroup writer requires Mongo transactions");
        }
        try {
            transactions.executeWithoutResult(status -> reconcileInTransaction(examId));
        } catch (DuplicateKeyException | OptimisticLockingFailureException race) {
            // The unique event slot and ExamSession version make concurrent writers converge.
            log.debug("AttemptGroup 상태 동시 전환 감지 outcome=converged");
        }
    }

    public boolean manages(String examId) {
        if (!properties.writerEnabled()) {
            return false;
        }
        return sessionRepository.findById(examId).map(this::managed).orElse(false);
    }

    private void reconcileInTransaction(String examId) {
        ExamSession session = sessionRepository.findById(examId).orElse(null);
        if (!eligible(session) || session.getTerminalEventId() != null) {
            return;
        }
        Instant now = clock.instant();
        AttemptGroupEvidenceEvaluator.Evaluation evaluation = evaluator.evaluate(session, now);
        if (evaluation.completed()) {
            createTerminal(session, AttemptGroupEventTarget.COMPLETED,
                    evaluation.completionEvidence(), null, now);
            return;
        }
        if (evaluation.failureCode() != null) {
            createTerminal(session, AttemptGroupEventTarget.RETAKE_AVAILABLE,
                    null, evaluation.failureCode(), now);
            return;
        }
        if (evaluation.gradingReady() && session.getGradingEventId() == null) {
            createGrading(session, now);
        }
    }

    private void createGrading(ExamSession session, Instant now) {
        String eventId = UUID.randomUUID().toString();
        AttemptGroupEventPayload payload = basePayload(
                eventId, session, AttemptGroupEventTarget.GRADING, null, null, now);
        String durableEventId = persistEvent(session, payload, AttemptGroupEventSlot.GRADING, now);
        session.markAttemptGroupGrading(durableEventId, now);
        sessionRepository.save(session);
    }

    private void createTerminal(
            ExamSession session,
            AttemptGroupEventTarget target,
            web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupCompletionEvidence evidence,
            web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupFailureCode failureCode,
            Instant now
    ) {
        String eventId = UUID.randomUUID().toString();
        AttemptGroupEventPayload payload = basePayload(eventId, session, target, evidence, failureCode, now);
        String durableEventId = persistEvent(session, payload, AttemptGroupEventSlot.TERMINAL, now);
        if (target == AttemptGroupEventTarget.COMPLETED) {
            session.markAttemptGroupCompleted(durableEventId, now, clock.getZone());
        } else {
            session.markAttemptGroupRetakeAvailable(durableEventId, failureCode);
        }
        sessionRepository.save(session);
    }

    private String persistEvent(
            ExamSession session,
            AttemptGroupEventPayload payload,
            AttemptGroupEventSlot slot,
            Instant now
    ) {
        Optional<AttemptGroupEventOutbox> existing = outboxStore
                .findBySessionAndSlot(session.getExamId(), slot);
        if (existing.isPresent()) {
            return existing.get().getEventId();
        }
        AttemptGroupEventCanonicalizer.CanonicalEvent canonical = canonicalizer.canonicalize(payload);
        AttemptGroupTraceContext.StoredContext context = traceContext.captureOrCreate();
        if (context.fallback()) {
            metrics.counter("trace_context_missing");
        }
        outboxStore.insert(AttemptGroupEventOutbox.pending(
                payload, slot, canonical.payload(), canonical.digest(),
                context.traceId(), context.parentSpanId(), context.traceFlags(), now));
        return payload.eventId();
    }

    private AttemptGroupEventPayload basePayload(
            String eventId,
            ExamSession session,
            AttemptGroupEventTarget target,
            web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupCompletionEvidence evidence,
            web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupFailureCode failureCode,
            Instant now
    ) {
        return new AttemptGroupEventPayload(
                eventId,
                "AttemptGroupStatusChanged",
                1,
                "learning-core",
                now,
                session.getUserId(),
                session.getAttemptGroupId(),
                session.getExamId(),
                target,
                evidence,
                failureCode
        );
    }

    private boolean eligible(ExamSession session) {
        return managed(session) && session.isInProgress();
    }

    private boolean managed(ExamSession session) {
        return session != null
                && session.getEntitlementState() == ExamEntitlementState.CONFIRMED
                && session.getAttemptGroupId() != null
                && !session.getAttemptGroupId().isBlank()
                && session.getUserId() != null
                && !session.getUserId().isBlank()
                && session.getAttemptGroupProjectionStatus() != null;
    }
}
