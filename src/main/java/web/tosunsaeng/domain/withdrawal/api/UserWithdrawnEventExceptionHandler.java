package web.tosunsaeng.domain.withdrawal.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import web.tosunsaeng.domain.withdrawal.application.UserWithdrawnEventException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = UserWithdrawnEventController.class)
public class UserWithdrawnEventExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Void> malformed() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(UserWithdrawnEventException.class)
    public ResponseEntity<Void> eventFailure(UserWithdrawnEventException exception) {
        return switch (exception.getReason()) {
            case INVALID_PAYLOAD -> ResponseEntity.badRequest().build();
            case PAYLOAD_CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT).build();
            case PROCESSING_UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        };
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Void> storeFailure() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
}
