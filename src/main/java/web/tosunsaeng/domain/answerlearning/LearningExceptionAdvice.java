package web.tosunsaeng.domain.answerlearning;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.domain.learningrecorddeletion.application.DeletionFailure;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=LearningController.class)
public class LearningExceptionAdvice {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> error(Exception e) {
        int status; String code;
        if (e instanceof LearningFailure f) { status=f.status; code=f.code; }
        else if (e instanceof DeletionFailure f) { status=f.status(); code=f.code(); }
        else if (e instanceof UserOwnershipGuardException) { status=403; code="LEARNING_FORBIDDEN"; }
        else if (e instanceof MethodArgumentNotValidException || e instanceof HttpMessageNotReadableException
                || e instanceof MethodArgumentTypeMismatchException || e instanceof ServletRequestBindingException) { status=400; code="INVALID_REQUEST"; }
        else { status=503; code="LEARNING_UNAVAILABLE"; }
        String message = switch (code) {
            case "ENTITLEMENT_INSUFFICIENT" -> "유료 이용권이 필요합니다";
            case "ACCOUNT_WITHDRAWN" -> "탈퇴 처리된 계정입니다.";
            case "BILLING_TEMPORARILY_UNAVAILABLE" -> "이용권을 일시적으로 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.";
            case "COMMON500" -> "서버에서 오류가 발생했습니다.";
            case "CONTENT_VERSION_CHANGED" -> "학습 내용이 변경됐어요. 문제를 다시 열어 주세요.";
            case "CHECKLIST_NOT_READY", "GRADING_NOT_READY" -> "이 문제의 채점을 준비하고 있어요.";
            case "AUDIO_NOT_UPLOADED" -> "녹음 업로드를 확인해 주세요.";
            case "LEARNING_RATE_LIMITED" -> "오늘의 제출 한도에 도달했어요. 내일 다시 이용해 주세요.";
            case "LEARNING_NOT_FOUND" -> "학습 정보를 찾을 수 없어요.";
            case "SUBMISSION_EXPIRED" -> "제출 시간이 만료됐어요. 새로 녹음해 주세요.";
            case "LEARNING_UNAVAILABLE" -> "학습 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.";
            default -> "학습 요청을 확인해 주세요.";
        };
        var response=ResponseEntity.status(status);
        if ("BILLING_TEMPORARILY_UNAVAILABLE".equals(code)) response.header("Retry-After","1");
        return response.body(new BaseResponse<>(false,code,message,null));
    }
}
