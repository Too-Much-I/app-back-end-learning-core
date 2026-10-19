package web.tosunsaeng.domain.exams.attemptgroup.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionOperations;
import web.tosunsaeng.domain.exams.application.ExamGradingService;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;

import java.util.Objects;

@Service
public class AttemptGroupSummaryCompletionService {
    private final ExamSummaryRepository summaryRepository;
    private final ExamGradingService gradingService;
    private final AttemptGroupStateCoordinator coordinator;
    private final ObjectProvider<TransactionOperations> transactionProvider;

    public AttemptGroupSummaryCompletionService(
            ExamSummaryRepository summaryRepository,
            ExamGradingService gradingService,
            AttemptGroupStateCoordinator coordinator,
            @Qualifier("attemptGroupTransactionOperations")
            ObjectProvider<TransactionOperations> transactionProvider
    ) {
        this.summaryRepository = summaryRepository;
        this.gradingService = gradingService;
        this.coordinator = coordinator;
        this.transactionProvider = transactionProvider;
    }

    public boolean supports(String examId) {
        return coordinator.manages(examId) && transactionProvider.getIfAvailable() != null;
    }

    public boolean persistAndComplete(
            ExamSummary summary,
            String examId,
            int generationAttempt
    ) {
        TransactionOperations transactions = transactionProvider.getIfAvailable();
        if (transactions == null || !coordinator.manages(examId)) {
            throw new IllegalStateException("AttemptGroup summary transaction is unavailable");
        }
        return AttemptGroupTransactionRetry.execute(() -> {
            Boolean completed = transactions.execute(status ->
                    persistAndCompleteInTransaction(summary, examId, generationAttempt, status));
            return Boolean.TRUE.equals(completed);
        });
    }

    private boolean persistAndCompleteInTransaction(
            ExamSummary summary,
            String examId,
            int generationAttempt,
            TransactionStatus status
    ) {
        String currentOwner = coordinator.touchCurrentOwnerWithinTransaction(examId);
        persistSummaryIfAbsent(withCurrentOwner(summary, currentOwner));
        if (!gradingService.completeSummary(examId, generationAttempt)) {
            status.setRollbackOnly();
            return false;
        }
        coordinator.reconcileWithinTransaction(examId);
        return true;
    }

    private void persistSummaryIfAbsent(ExamSummary summary) {
        ExamSummary existing = summaryRepository.findById(summary.getId()).orElse(null);
        if (existing == null) {
            summaryRepository.insert(summary);
            return;
        }
        if (!Objects.equals(existing.getId(), summary.getId())
                || !Objects.equals(existing.getExamId(), summary.getExamId())
                || !Objects.equals(existing.getUserId(), summary.getUserId())
                || !Objects.equals(existing.getMockExamId(), summary.getMockExamId())) {
            throw new IllegalStateException("Deterministic summary identity conflict");
        }
    }

    private ExamSummary withCurrentOwner(ExamSummary summary, String currentOwner) {
        if (currentOwner == null || Objects.equals(currentOwner, summary.getUserId())) {
            return summary;
        }
        return ExamSummary.builder()
                .id(summary.getId())
                .examId(summary.getExamId())
                .userId(currentOwner)
                .mockExamId(summary.getMockExamId())
                .totalScore(summary.getTotalScore())
                .levelEstimate(summary.getLevelEstimate())
                .summary(summary.getSummary())
                .overallFeedback(summary.getOverallFeedback())
                .partFeedback(summary.getPartFeedback())
                .strengths(summary.getStrengths())
                .weaknesses(summary.getWeaknesses())
                .recommendedPractice(summary.getRecommendedPractice())
                .build();
    }
}
