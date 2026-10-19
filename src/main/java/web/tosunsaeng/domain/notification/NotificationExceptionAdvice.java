package web.tosunsaeng.domain.notification;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.ServletRequestBindingException;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardException;
import web.tosunsaeng.global.common.response.BaseResponse;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = NotificationController.class)
public class NotificationExceptionAdvice {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> error(Exception e) {
        int status = e instanceof NotificationFailure n ? n.status
                : e instanceof UserOwnershipGuardException ? 403
                : e instanceof ServletRequestBindingException || e instanceof org.springframework.web.HttpMediaTypeNotSupportedException ? 400 : 503;
        return ResponseEntity.status(status).body(new BaseResponse<>(false, "NOTIFICATION_" + status, "알림 기기 등록 상태를 확인해 주세요.", null));
    }
}
