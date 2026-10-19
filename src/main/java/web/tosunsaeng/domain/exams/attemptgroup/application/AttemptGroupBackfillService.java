package web.tosunsaeng.domain.exams.attemptgroup.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupFailureCode;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventProperties;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;

import java.time.Clock;
import java.util.List;
import java.util.Set;

@Service
public class AttemptGroupBackfillService {
    private final AttemptGroupEventProperties properties;
    private final ExamSessionRepository sessionRepository;
    private final AttemptGroupEvidenceEvaluator evaluator;
    private final AttemptGroupStateCoordinator coordinator;
    private final ObjectProvider<TransactionOperations> transactionProvider;
    private final Clock clock;

    public AttemptGroupBackfillService(
            AttemptGroupEventProperties properties,
            ExamSessionRepository sessionRepository,
            AttemptGroupEvidenceEvaluator evaluator,
            AttemptGroupStateCoordinator coordinator,
            @Qualifier("attemptGroupTransactionOperations")
            ObjectProvider<TransactionOperations> transactionProvider,
            @Qualifier("gradingClock") Clock clock
    ) {
        this.properties = properties;
        this.sessionRepository = sessionRepository;
        this.evaluator = evaluator;
        this.coordinator = coordinator;
        this.transactionProvider = transactionProvider;
        this.clock = clock;
    }

    public List<DryRunCandidate> dryRun(Set<String> allowlistedSessionIds) {
        Set<String> allowlist = Set.copyOf(allowlistedSessionIds);
        return sessionRepository.findAttemptGroupBackfillCandidates().stream()
                .filter(session -> allowlist.contains(session.getExamId()))
                .map(session -> {
                    AttemptGroupEvidenceEvaluator.Evaluation result =
                            evaluator.evaluate(session, clock.instant());
                    return new DryRunCandidate(
                            session.getExamId(), result.gradingReady(), result.completed(),
                            result.failureCode());
                })
                .toList();
    }

    public int apply(Set<String> allowlistedSessionIds) {
        if (!properties.writerEnabled()) {
            throw new IllegalStateException("AttemptGroup writer must be enabled for backfill");
        }
        TransactionOperations transactions = transactionProvider.getIfAvailable();
        if (transactions == null) {
            throw new IllegalStateException("AttemptGroup backfill requires Mongo transactions");
        }
        int applied = 0;
        for (String examId : Set.copyOf(allowlistedSessionIds)) {
            Boolean changed = transactions.execute(status -> {
                ExamSession session = sessionRepository.findById(examId).orElse(null);
                if (session == null || session.getAttemptGroupProjectionStatus() != null) {
                    return false;
                }
                coordinator.touchCurrentOwnerWithinTransaction(examId);
                session.enableAttemptGroupProjectionForBackfill();
                sessionRepository.save(session);
                return true;
            });
            if (Boolean.TRUE.equals(changed)) {
                coordinator.reconcile(examId);
                applied++;
            }
        }
        return applied;
    }

    public record DryRunCandidate(
            String sessionId,
            boolean gradingReady,
            boolean completed,
            AttemptGroupFailureCode failureCode
    ) {
    }
}
