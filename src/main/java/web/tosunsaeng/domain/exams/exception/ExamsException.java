package web.tosunsaeng.domain.exams.exception;

import web.tosunsaeng.global.error.code.status.BaseErrorCode;
import web.tosunsaeng.global.exception.GeneralException;

public class ExamsException extends GeneralException {

    private final Integer retryAfterSeconds;

    public ExamsException(BaseErrorCode code) {
        this(code, null);
    }

    public ExamsException(BaseErrorCode code, Integer retryAfterSeconds) {
        super(code);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
