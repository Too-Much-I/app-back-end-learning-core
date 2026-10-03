package web.tosunsaeng.domain.learningrecorddeletion.api;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import web.tosunsaeng.domain.learningrecorddeletion.application.DeletionFailure;
import web.tosunsaeng.global.common.response.BaseResponse;

@Order(-100)
@RestControllerAdvice
public class DeletionExceptionAdvice {
    @ExceptionHandler(DeletionFailure.class)
    public ResponseEntity<?> handle(DeletionFailure failure) {
        String message = switch (failure.code()) {
            case "LEARNING_RECORD_DELETION_ALREADY_ACTIVE" -> "이전 기록 정리가 완료되면 다시 요청해 주세요.";
            case "LEARNING_RECORD_DELETION_IN_PROGRESS" -> "학습 기록 삭제를 준비하고 있어요. 잠시 후 다시 시도해 주세요.";
            default -> "학습 기록 삭제 상태를 확인해 주세요.";
        };
        return ResponseEntity.status(failure.status()).body(new BaseResponse<>(false, failure.code(), message, null));
    }
}
