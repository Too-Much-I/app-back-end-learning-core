package web.tosunsaeng.domain.exams.application;

public final class GradingKeys {

    public static final String MOCK_EXAM_ID = "mock_exam_003";

    private GradingKeys() {
    }

    public static String questionJobId(String examId, Integer questionNumber, Integer retryCount) {
        return "question:%s:%d:%d".formatted(examId, questionNumber, canonicalRetryCount(retryCount));
    }

    public static String summaryJobId(String examId) {
        return "summary:%s:v1".formatted(examId);
    }

    public static String questionFileKey(String examId, Integer questionNumber, Integer retryCount) {
        return "temp/%s/q_%d_r%d.wav".formatted(examId, questionNumber, canonicalRetryCount(retryCount));
    }

    public static String feedbackResultId(String examId, Integer questionNumber, Integer retryCount) {
        return "feedback:%s:%d:%d".formatted(examId, questionNumber, canonicalRetryCount(retryCount));
    }

    public static String speechAceResultId(String examId, Integer questionNumber, Integer retryCount) {
        return "speechace:%s:%d:%d".formatted(examId, questionNumber, canonicalRetryCount(retryCount));
    }

    public static String azureResultId(String examId, Integer questionNumber, Integer retryCount) {
        return "azure:%s:%d:%d".formatted(examId, questionNumber, canonicalRetryCount(retryCount));
    }

    public static int canonicalRetryCount(Integer retryCount) {
        return retryCount == null ? 0 : retryCount;
    }
}
