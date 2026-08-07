package web.tosunsaeng.domain.exams.application;

import java.util.concurrent.TimeUnit;

final class GradingDispatchException extends RuntimeException {

    private final Stage stage;
    private final long stageDurationMs;

    private GradingDispatchException(Stage stage, long stageDurationMs, RuntimeException cause) {
        super("Grading dispatch failed at stage " + stage.code, cause);
        this.stage = stage;
        this.stageDurationMs = stageDurationMs;
    }

    static GradingDispatchException at(Stage stage, long startedAt, RuntimeException cause) {
        return new GradingDispatchException(
                stage,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                cause
        );
    }

    static String stageCode(Throwable failure) {
        GradingDispatchException dispatchFailure = find(failure);
        return dispatchFailure == null ? "unknown" : dispatchFailure.stage.code;
    }

    static long stageDurationMs(Throwable failure) {
        GradingDispatchException dispatchFailure = find(failure);
        return dispatchFailure == null ? -1L : dispatchFailure.stageDurationMs;
    }

    private static GradingDispatchException find(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof GradingDispatchException dispatchFailure) {
                return dispatchFailure;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }

    enum Stage {
        S3_DOWNLOAD("s3_download"),
        AI_POST("ai_post");

        private final String code;

        Stage(String code) {
            this.code = code;
        }
    }
}
