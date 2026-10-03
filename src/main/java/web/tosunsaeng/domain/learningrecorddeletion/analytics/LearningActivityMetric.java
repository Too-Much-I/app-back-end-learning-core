package web.tosunsaeng.domain.learningrecorddeletion.analytics;

import java.util.Set;

/** Closed cardinality: exactly eighteen allowed day/metric/type/outcome combinations. */
public enum LearningActivityMetric {
    exam_started_total("MOCK_EXAM", "INITIAL", "REPLACEMENT"),
    exam_completed_total("MOCK_EXAM", "ALL"),
    exam_retake_available_total("MOCK_EXAM", "ALL"),
    question_submitted_total("MOCK_EXAM", "INITIAL", "USER_RETRY"),
    question_grading_completed_total("MOCK_EXAM", "ALL"),
    question_grading_failed_total("MOCK_EXAM", "ALL"),
    question_grading_retry_total("MOCK_EXAM", "ALL"),
    summary_grading_completed_total("MOCK_EXAM", "ALL"),
    summary_grading_failed_total("MOCK_EXAM", "ALL"),
    challenge_started_total("CHALLENGE_10S", "ALL"),
    challenge_submitted_total("CHALLENGE_10S", "ALL"),
    challenge_completed_total("CHALLENGE_10S", "SCORED", "NO_SPEECH"),
    challenge_expired_total("CHALLENGE_10S", "ALL"),
    challenge_grading_failed_total("CHALLENGE_10S", "ALL"),
    challenge_grading_retry_total("CHALLENGE_10S", "ALL");

    public final String examType;
    public final Set<String> outcomes;
    LearningActivityMetric(String examType, String... outcomes) { this.examType = examType; this.outcomes = Set.of(outcomes); }
}
