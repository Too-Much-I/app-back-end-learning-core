package web.tosunsaeng.domain.usermerge.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import web.tosunsaeng.domain.usermerge.application.UserMergedEventException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = UserMergedInternalController.class)
public class UserMergedInternalExceptionAdvice {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Void> malformed() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(UserMergedEventException.class)
    public ResponseEntity<Void> eventFailure(UserMergedEventException failure) {
        return switch (failure.getReason()) {
            case INVALID_PAYLOAD -> ResponseEntity.unprocessableEntity().build();
            case PAYLOAD_CONFLICT, LIFECYCLE_CONFLICT ->
                    ResponseEntity.status(HttpStatus.CONFLICT).build();
            case RETRYABLE_PRECONDITION -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.RETRY_AFTER, "5")
                    .build();
            case PROCESSING_UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.RETRY_AFTER, "1")
                    .build();
        };
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Void> databaseFailure() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .build();
    }
}
