package web.tosunsaeng.domain.exams.attemptgroup.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import web.tosunsaeng.domain.exams.application.ExamGradingService;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;

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
        Boolean completed = transactions.execute(status -> {
            try {
                summaryRepository.insert(summary);
            } catch (DuplicateKeyException duplicate) {
                // Deterministic summary ID makes a committed replay safe.
            }
            if (!gradingService.completeSummary(examId, generationAttempt)) {
                return false;
            }
            coordinator.reconcile(examId);
            return true;
        });
        return Boolean.TRUE.equals(completed);
    }
}
