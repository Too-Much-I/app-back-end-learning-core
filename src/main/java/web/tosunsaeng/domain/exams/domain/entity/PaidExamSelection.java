package web.tosunsaeng.domain.exams.domain.entity;

import java.util.List;

/** P1 internal snapshot; not a Billing wire DTO. Null reason means there was no previous exam. */
public record PaidExamSelection(StartReason startReason, List<Integer> requiredQuestionNumbers) {
    public enum StartReason { RESTART_INCOMPLETE, NEW_AFTER_SUBMISSION, NEW_AFTER_COMPLETION }
    public PaidExamSelection {
        requiredQuestionNumbers = List.copyOf(requiredQuestionNumbers);
        if (requiredQuestionNumbers.isEmpty()) throw new IllegalArgumentException("Empty required questions");
    }
}
