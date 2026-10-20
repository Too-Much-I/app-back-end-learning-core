package web.tosunsaeng.domain.exams.attemptgroup.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
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
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;

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

    @Autowired(required = false)
    private UserOwnedTransactionExecutor userOwnedTransactionExecutor;

    @Autowired(required = false)
    private web.tosunsaeng.domain.exams.application.ExamSubmissionEvidence submissionEvidence;

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
        AttemptGroupTransactionRetry.execute(() -> {
            transactions.executeWithoutResult(status -> reconcileWithinTransaction(examId));
            return null;
        });
    }

    void reconcileWithinTransaction(String examId) {
        if (!properties.writerEnabled()) {
            return;
        }
        reconcileInTransaction(examId);
    }

    /** Only called after submitQuestion has returned successfully; never retries submission or dispatch. */
    public void reconcileAfterSubmission(String examId, int question, int retry, Runnable verifyPublicAccess) {
        try {
            reconcile(examId);
        } catch (RuntimeException failure) {
            var detail = AttemptGroupTransactionRetry.classify(failure);
            if (!detail.retryable() || !detail.transientError() || detail.unknownCommit()
                    || org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                    || !properties.writerEnabled() || submissionEvidence == null
                    || userOwnedTransactionExecutor == null || !userOwnedTransactionExecutor.enabled()) {
                throw failure;
            }
            // Read after the aborted transaction. These existing documents survive process restart
            // and are rediscovered by the writer's reconciler; no best-effort failure marker is needed.
            ExamSession session = sessionRepository.findById(examId).orElse(null);
            boolean recoveryCandidate = eligible(session) && session.getTerminalEventId() == null;
            // Explicit server provenance only: these submissions have no Billing projection to repair.
            boolean noBillingProjection = session != null && session.isTransitionFree();
            if ((!recoveryCandidate && !noBillingProjection)
                    || !submissionEvidence.acceptedQuestion(session, question, retry)) {
                throw failure;
            }
            // Do not turn a concurrent ownership/deletion denial into an accepted response.
            userOwnedTransactionExecutor.requireDeferredExamCoordinationAllowed(session.getUserId(), examId);
            verifyPublicAccess.run();
            log.debug("AttemptGroup transaction event=attempt_group.transaction outcome=deferred_after_submission");
        }
    }

    String touchCurrentOwnerWithinTransaction(String examId) {
        ExamSession session = sessionRepository.findById(examId).orElse(null);
        if (session != null && userOwnedTransactionExecutor != null) {
            userOwnedTransactionExecutor.touchExamWithinExistingTransaction(session.getUserId(), examId);
        }
        return session == null ? null : session.getUserId();
    }

    public boolean manages(String examId) {
        if (!properties.writerEnabled()) {
            return false;
        }
        return sessionRepository.findById(examId).map(this::managed).orElse(false);
    }

    private void reconcileInTransaction(String examId) {
        ExamSession session = sessionRepository.findById(examId).orElse(null);
        if (session != null && userOwnedTransactionExecutor != null) {
            userOwnedTransactionExecutor.touchExamWithinExistingTransaction(session.getUserId(), examId);
        }
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
            if(session.isAccessRevoked()) session.markRevokedAcceptedWorkCompleted(durableEventId,now,clock.getZone());
            else session.markAttemptGroupCompleted(durableEventId, now, clock.getZone());
        } else {
            if(session.isAccessRevoked()) session.markRevokedAcceptedWorkFailed(durableEventId,failureCode);
            else session.markAttemptGroupRetakeAvailable(durableEventId, failureCode);
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
        return managed(session) && session.isInProgress()
                && (!session.isAccessRevoked()
                    || web.tosunsaeng.domain.exams.application.ExamAcceptedWorkPolicy.summaryAllowed(session));
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
