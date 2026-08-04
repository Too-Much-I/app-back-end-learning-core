package web.tosunsaeng.domain.exams.application;

public final class GradingKeys {

    public static final String LEGACY_MOCK_EXAM_ID = "mock_exam_003";

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

    public static String effectiveMockExamId(String mockExamId) {
        return mockExamId == null || mockExamId.isBlank() ? LEGACY_MOCK_EXAM_ID : mockExamId;
    }

    public static int partNumberForQuestion(Integer questionNumber) {
        if (questionNumber == null) return 1;
        if (questionNumber == 0) return 0;
        if (questionNumber >= 1 && questionNumber <= 2) return 1;
        if (questionNumber >= 3 && questionNumber <= 4) return 2;
        if (questionNumber >= 5 && questionNumber <= 7) return 3;
        if (questionNumber >= 8 && questionNumber <= 10) return 4;
        return 5;
    }
}
