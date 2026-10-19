package web.tosunsaeng.domain.exams.attemptgroup.application;

import com.mongodb.MongoException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionOperations;
import web.tosunsaeng.domain.exams.application.ExamGradingService;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;

import java.util.Objects;

@Service
public class AttemptGroupSummaryCompletionService {
    private static final int MAX_TRANSACTION_ATTEMPTS = 3;
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
        for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
            try {
                Boolean completed = transactions.execute(status ->
                        persistAndCompleteInTransaction(summary, examId, generationAttempt, status));
                return Boolean.TRUE.equals(completed);
            } catch (RuntimeException failure) {
                if (attempt == MAX_TRANSACTION_ATTEMPTS || !retryableTransactionFailure(failure)) {
                    throw failure;
                }
            }
        }
        throw new IllegalStateException("AttemptGroup summary transaction retry exhausted");
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

    private boolean retryableTransactionFailure(RuntimeException failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof DuplicateKeyException
                    || current instanceof OptimisticLockingFailureException) {
                return true;
            }
            if (current instanceof MongoException mongoException
                    && (mongoException.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                    || mongoException.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL))) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return false;
    }
}
