package web.tosunsaeng.domain.challenge;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardException;
import web.tosunsaeng.global.common.response.BaseResponse;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {ChallengeController.class, ChallengeCallbackController.class})
public class ChallengeExceptionAdvice {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> error(Exception exception, HttpServletRequest request) {
        boolean internal = request.getRequestURI().equals(ChallengeCallbackController.PATH);
        ChallengeFailure error;
        if (exception instanceof ChallengeFailure failure) error = failure;
        else if (exception instanceof UserOwnershipGuardException) error = ChallengeFailure.forbidden();
        else if (exception instanceof ServletRequestBindingException || exception instanceof MethodArgumentTypeMismatchException
                || exception instanceof HttpMediaTypeNotSupportedException)
            error = internal ? ChallengeCallback.invalid() : ChallengeFailure.badRequest();
        else error = ChallengeFailure.internal();
        Object response = internal ? Map.of("code", error.code) : new BaseResponse<>(false, error.code, message(error.code), error.result);
        return ResponseEntity.status(error.status).body(response);
    }
    private static String message(String code) {
        return switch (code) {
            case "CHALLENGE_DATE_CHANGED" -> "챌린지 날짜가 변경되었습니다.";
            case "CHALLENGE_ALREADY_ATTEMPTED" -> "이미 응시가 완료된 문제입니다.";
            case "CHALLENGE_ATTEMPT_EXPIRED" -> "제출 유효시간이 만료되었습니다.";
            case "CHALLENGE_PREVIOUS_QUESTION_INCOMPLETE" -> "이전 문제를 먼저 완료해 주세요.";
            case "CHALLENGE_CONTENT_NOT_FOUND" -> "해당 날짜의 챌린지 콘텐츠를 찾을 수 없습니다.";
            case "CHALLENGE_ATTEMPT_NOT_FOUND" -> "챌린지 응시를 찾을 수 없습니다.";
            case "CHALLENGE_IDEMPOTENCY_CONFLICT" -> "동일한 요청 키가 다른 제출에 사용되었습니다.";
            case "CHALLENGE_AUDIO_NOT_UPLOADED" -> "음성 업로드를 확인해 주세요.";
            case "CHALLENGE_AUDIO_TOO_LARGE" -> "음성 파일 크기 제한을 초과했습니다.";
            case "CHALLENGE_AUDIO_FORMAT_UNSUPPORTED" -> "지원하지 않는 음성 형식입니다.";
            case "COMMON400" -> "잘못된 요청입니다.";
            case "COMMON403" -> "접근 권한이 없습니다.";
            default -> "서버 오류가 발생했습니다.";
        };
    }
}
