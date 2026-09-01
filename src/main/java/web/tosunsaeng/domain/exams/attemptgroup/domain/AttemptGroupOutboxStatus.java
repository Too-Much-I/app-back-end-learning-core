package web.tosunsaeng.domain.exams.attemptgroup.domain;

public enum AttemptGroupOutboxStatus {
    PENDING,
    IN_FLIGHT,
    DELIVERED,
    DEAD_LETTER,
    BLOCKED_AUTH
}
