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

    @Scheduled(fixedDelayString = "${app.attempt-group-events.poll-interval:PT1S}")
    public void reconcile() {
        sessionRepository.findAttemptGroupReconciliationCandidates().stream()
                .limit(properties.batchSize())
                .map(ExamSession::getExamId)
                .forEach(coordinator::reconcile);
    }
}
