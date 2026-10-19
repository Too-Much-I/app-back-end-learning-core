package web.tosunsaeng.domain.exams.attemptgroup.application;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventProperties;
import java.util.List;
import static org.mockito.Mockito.*;

class AttemptGroupStateReconcilerTest {
    @Test void failedCandidateDoesNotStarveNextCandidateOrDeleteAnything() {
        var repository = mock(ExamSessionRepository.class);
        var coordinator = mock(AttemptGroupStateCoordinator.class);
        var properties = mock(AttemptGroupEventProperties.class);
        when(properties.batchSize()).thenReturn(20);
        when(repository.findAttemptGroupReconciliationCandidates()).thenReturn(List.of(
                ExamSession.builder().examId("failed").build(), ExamSession.builder().examId("next").build()));
        doThrow(new IllegalStateException("private details")).when(coordinator).reconcile("failed");
        new AttemptGroupStateReconciler(repository, coordinator, properties).reconcile();
        verify(coordinator).reconcile("failed");
        verify(coordinator).reconcile("next");
        verify(repository).findAttemptGroupReconciliationCandidates();
        verifyNoMoreInteractions(repository);
    }
}
