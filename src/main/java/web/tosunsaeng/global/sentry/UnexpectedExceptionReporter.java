package web.tosunsaeng.global.sentry;

public interface UnexpectedExceptionReporter {

    void report(Throwable exception);

    default void reportUnhandled(Throwable exception) {
        report(exception);
    }
}
