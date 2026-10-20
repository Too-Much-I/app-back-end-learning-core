package web.tosunsaeng.domain.exams.attemptgroup.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventProperties;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.attempt-group-events",
        name = "writer-enabled",
        havingValue = "true"
)
public class AttemptGroupStateReconciler {
    private final ExamSessionRepository sessionRepository;
    private final AttemptGroupStateCoordinator coordinator;
    private final AttemptGroupEventProperties properties;
    private String afterId = "";

    @Scheduled(fixedDelayString = "${app.attempt-group-events.poll-interval:PT1S}")
    public synchronized void reconcile() {
        var candidates = sessionRepository.findAttemptGroupReconciliationCandidatesAfter(afterId,
                org.springframework.data.domain.PageRequest.of(0, properties.batchSize()));
        if (candidates.isEmpty()) {
            afterId = "";
            return;
        }
        candidates.stream()
                .map(ExamSession::getExamId)
                .forEach(examId -> {
                    try {
                        coordinator.reconcile(examId);
                    } catch (RuntimeException failure) {
                        // Preserve the failed candidate; one owner must not starve the rest.
                        AttemptGroupTransactionRetry.reportFailure(AttemptGroupTransactionRetry.classify(failure), 0);
                    } finally {
                        // Advance even on failure/in-progress; wrap next cycle and retry from durable state.
                        afterId = examId;
                    }
                });
    }
}
