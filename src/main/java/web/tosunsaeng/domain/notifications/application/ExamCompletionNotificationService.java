package web.tosunsaeng.domain.notifications.application;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import web.tosunsaeng.domain.exams.application.ExamGradingService;
import web.tosunsaeng.domain.exams.application.ExamSessionManager;
import web.tosunsaeng.domain.exams.application.GradingKeys;
import web.tosunsaeng.domain.exams.converter.ExamConverter;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;
import web.tosunsaeng.domain.exams.domain.enums.GradingJobStatus;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;
import web.tosunsaeng.domain.exams.domain.repository.SummaryGradingJobRepository;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationOutbox;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationType;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationOutboxRepository;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

@Service
public class ExamCompletionNotificationService {

    private static final int CONVERGENCE_ATTEMPTS = 3;
    private static final String EVENT_KEY_PREFIX = "EXAM_GRADING_COMPLETED:";

    private final TransactionOperations transactionOperations;
    private final ExamSummaryRepository examSummaryRepository;
    private final ExamResultRepository examResultRepository;
    private final ExamSessionRepository examSessionRepository;
    private final SummaryGradingJobRepository summaryJobRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final ExamSessionManager examSessionManager;
    private final ExamGradingService gradingService;
    private final NotificationIdentityCodec identityCodec;
    private final Clock clock;

    public ExamCompletionNotificationService(
            @Qualifier("notificationMongoTransactionOperations") TransactionOperations transactionOperations,
            ExamSummaryRepository examSummaryRepository,
            ExamResultRepository examResultRepository,
            ExamSessionRepository examSessionRepository,
            SummaryGradingJobRepository summaryJobRepository,
            NotificationOutboxRepository outboxRepository,
            ExamSessionManager examSessionManager,
            ExamGradingService gradingService,
            NotificationIdentityCodec identityCodec,
            Clock clock) {
        this.transactionOperations = transactionOperations;
        this.examSummaryRepository = examSummaryRepository;
        this.examResultRepository = examResultRepository;
        this.examSessionRepository = examSessionRepository;
        this.summaryJobRepository = summaryJobRepository;
        this.outboxRepository = outboxRepository;
        this.examSessionManager = examSessionManager;
        this.gradingService = gradingService;
        this.identityCodec = identityCodec;
        this.clock = clock;
    }

    public void completeSummaryCallback(
            ExamRequestDTO.AiResultReq request,
            ExamSession session,
            String mockExamId) {
        String examId = session.getExamId();
        String summaryId = GradingKeys.summaryJobId(examId);
        executeWithExpectedDuplicateConvergence(examId, summaryId, () ->
                transactionOperations.executeWithoutResult(ignored -> {
                    saveSummaryIfMissing(request, session, summaryId, mockExamId);
                    examSessionManager.completeIfIncomplete(examId);
                    gradingService.completeSummary(examId);
                    createOutboxIfEligible(examId);
                })
        );
    }

    public void reconcileAfterQuestionCompletion(String examId) {
        if (findEligibleCompletedSession(examId).isEmpty()) {
            return;
        }
        executeWithExpectedDuplicateConvergence(examId, null, () ->
                transactionOperations.executeWithoutResult(ignored -> createOutboxIfEligible(examId))
        );
    }

    private void saveSummaryIfMissing(
            ExamRequestDTO.AiResultReq request,
            ExamSession session,
            String summaryId,
            String mockExamId) {
        boolean alreadyStored = examSummaryRepository.existsById(summaryId)
                || examSummaryRepository.existsByExamId(session.getExamId())
                || examResultRepository
                .findFirstByExamIdAndTotalScoreIsNotNullOrderByIdDesc(session.getExamId())
                .isPresent();
        if (alreadyStored) {
            return;
        }

        ExamSummary summary = ExamConverter.toExamSummary(
                request,
                session.getUserId(),
                summaryId,
                mockExamId
        );
        try {
            examSummaryRepository.insert(summary);
        } catch (DuplicateKeyException duplicate) {
            throw new ExpectedDuplicate(ExpectedDuplicateKind.SUMMARY, duplicate);
        }
    }

    private void createOutboxIfEligible(String examId) {
        Optional<ExamSession> eligibleSession = findEligibleCompletedSession(examId);
        if (eligibleSession.isEmpty()) {
            return;
        }
        ExamSession completedSession = eligibleSession.get();

        String eventKey = eventKey(examId);
        if (outboxRepository.existsByEventKey(eventKey)) {
            return;
        }
        NotificationOutbox outbox = NotificationOutbox.pending(
                identityCodec.notificationId(eventKey),
                eventKey,
                completedSession.getUserId(),
                examId,
                clock.instant()
        );
        try {
            outboxRepository.insert(outbox);
        } catch (DuplicateKeyException duplicate) {
            throw new ExpectedDuplicate(ExpectedDuplicateKind.OUTBOX, duplicate);
        }
    }

    private Optional<ExamSession> findEligibleCompletedSession(String examId) {
        if (!gradingService.areAllRequiredQuestionsComplete(examId)
                || !examSummaryRepository.existsByExamId(examId)) {
            return Optional.empty();
        }
        Optional<ExamSession> completedSession = examSessionRepository.findById(examId)
                .filter(session -> session.getCompletedAt() != null);
        if (completedSession.isEmpty()) {
            return Optional.empty();
        }
        boolean summaryJobCompleted = summaryJobRepository.findById(GradingKeys.summaryJobId(examId))
                .map(job -> job.getStatus() == GradingJobStatus.COMPLETED)
                .orElse(false);
        return summaryJobCompleted ? completedSession : Optional.empty();
    }

    private void executeWithExpectedDuplicateConvergence(
            String examId,
            String summaryId,
            Runnable operation) {
        for (int attempt = 0; attempt < CONVERGENCE_ATTEMPTS; attempt++) {
            try {
                operation.run();
                return;
            } catch (ExpectedDuplicate candidate) {
                if (!isExpectedDuplicate(candidate.kind, examId, summaryId)) {
                    throw candidate.original;
                }
                if (attempt + 1 >= CONVERGENCE_ATTEMPTS) {
                    throw candidate.original;
                }
            }
        }
    }

    private boolean isExpectedDuplicate(
            ExpectedDuplicateKind kind,
            String examId,
            String summaryId) {
        if (kind == ExpectedDuplicateKind.SUMMARY) {
            return summaryId != null && examSummaryRepository.findById(summaryId)
                    .map(summary -> Objects.equals(examId, summary.getExamId()))
                    .orElse(false);
        }

        String eventKey = eventKey(examId);
        return outboxRepository.findByEventKey(eventKey)
                .filter(outbox -> Objects.equals(eventKey, outbox.getEventKey()))
                .filter(outbox -> outbox.getType() == NotificationType.EXAM_GRADING_COMPLETED)
                .filter(outbox -> Objects.equals(examId, outbox.getExamId()))
                .isPresent();
    }

    static String eventKey(String examId) {
        return EVENT_KEY_PREFIX + examId;
    }

    private enum ExpectedDuplicateKind {
        SUMMARY,
        OUTBOX
    }

    private static final class ExpectedDuplicate extends RuntimeException {
        private final ExpectedDuplicateKind kind;
        private final DuplicateKeyException original;

        private ExpectedDuplicate(ExpectedDuplicateKind kind, DuplicateKeyException original) {
            super(kind.name());
            this.kind = kind;
            this.original = original;
        }
    }
}
