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
        when(repository.findAttemptGroupReconciliationCandidatesAfter(eq(""), any())).thenReturn(List.of(
                ExamSession.builder().examId("failed").build(), ExamSession.builder().examId("next").build()));
        doThrow(new IllegalStateException("private details")).when(coordinator).reconcile("failed");
        new AttemptGroupStateReconciler(repository, coordinator, properties).reconcile();
        verify(coordinator).reconcile("failed");
        verify(coordinator).reconcile("next");
        verify(repository).findAttemptGroupReconciliationCandidatesAfter(eq(""), any());
        verifyNoMoreInteractions(repository);
    }

    @Test void advancesPastPersistentFailuresAndWrapsIncludingAfterRestart() {
        var repository = mock(ExamSessionRepository.class);
        var coordinator = mock(AttemptGroupStateCoordinator.class);
        var properties = mock(AttemptGroupEventProperties.class);
        when(properties.batchSize()).thenReturn(1);
        when(repository.findAttemptGroupReconciliationCandidatesAfter(eq(""), any()))
                .thenReturn(List.of(ExamSession.builder().examId("a").build()));
        when(repository.findAttemptGroupReconciliationCandidatesAfter(eq("a"), any()))
                .thenReturn(List.of(ExamSession.builder().examId("b").build()));
        when(repository.findAttemptGroupReconciliationCandidatesAfter(eq("b"), any())).thenReturn(List.of());
        doThrow(new IllegalStateException()).when(coordinator).reconcile("a");
        var worker = new AttemptGroupStateReconciler(repository, coordinator, properties);
        worker.reconcile(); worker.reconcile(); worker.reconcile(); worker.reconcile();
        verify(coordinator, times(2)).reconcile("a");
        verify(coordinator).reconcile("b");
        new AttemptGroupStateReconciler(repository, coordinator, properties).reconcile();
        verify(coordinator, times(3)).reconcile("a");
        verify(repository, times(3)).findAttemptGroupReconciliationCandidatesAfter("",
                org.springframework.data.domain.PageRequest.of(0, 1));
    }
}
