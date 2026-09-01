package web.tosunsaeng.domain.exams.attemptgroup.domain;

public record AttemptGroupCompletionEvidence(
        boolean requiredFeedbackQueryable,
        boolean validScoreQueryable,
        boolean summaryQueryable,
        int evidenceVersion
) {
    public static AttemptGroupCompletionEvidence complete() {
        return new AttemptGroupCompletionEvidence(true, true, true, 1);
    }
}
