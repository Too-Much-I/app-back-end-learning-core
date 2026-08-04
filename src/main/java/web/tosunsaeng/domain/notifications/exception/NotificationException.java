package web.tosunsaeng.domain.notifications.exception;

import web.tosunsaeng.global.error.code.status.BaseErrorCode;
import web.tosunsaeng.global.exception.GeneralException;

public class NotificationException extends GeneralException {

    public NotificationException(BaseErrorCode code) {
        super(code);
    }
}
