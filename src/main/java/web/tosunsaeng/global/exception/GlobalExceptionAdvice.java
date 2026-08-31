package web.tosunsaeng.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.BaseErrorCode;
import web.tosunsaeng.global.error.code.status.ErrorReasonDTO;
import web.tosunsaeng.global.error.code.status.ErrorStatus;
import web.tosunsaeng.global.sentry.UnexpectedExceptionReporter;
import web.tosunsaeng.domain.exams.exception.ExamsException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestControllerAdvice(annotations = {RestController.class})
public class GlobalExceptionAdvice extends ResponseEntityExceptionHandler {

    private final UnexpectedExceptionReporter unexpectedExceptionReporter;

    public GlobalExceptionAdvice(UnexpectedExceptionReporter unexpectedExceptionReporter) {
        this.unexpectedExceptionReporter = unexpectedExceptionReporter;
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String errorMessage = e.getPropertyName() + ": 올바른 값이 아닙니다.";

        logRequestRejected(e, ErrorStatus._BAD_REQUEST, request);
        return handleExceptionInternalMessage(e, headers, request, errorMessage);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String errorMessage = e.getParameterName() + ": 올바른 값이 아닙니다.";

        logRequestRejected(e, ErrorStatus._BAD_REQUEST, request);
        return handleExceptionInternalMessage(e, headers, request, errorMessage);
    }

    @ExceptionHandler
    public ResponseEntity<Object> validation(ConstraintViolationException e, WebRequest request) {
        String errorMessage =
                e.getConstraintViolations().stream()
                        .map(constraintViolation -> constraintViolation.getMessage())
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new RuntimeException(
                                                "ConstraintViolationException 추출 도중 에러 발생"));

        logRequestRejected(e, ErrorStatus.valueOf(errorMessage), request);
        return handleExceptionInternalConstraint(
                e, ErrorStatus.valueOf(errorMessage), HttpHeaders.EMPTY, request);
    }

    @Override
    public ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();

// 필드 에러 처리
        e.getBindingResult().getFieldErrors().forEach(fieldError -> {
            String fieldName = fieldError.getField();
            String errorMessage;
            try {
                errorMessage = Optional.ofNullable(ErrorStatus.valueOf(fieldError.getDefaultMessage()).getMessage()).orElse("");
            } catch (IllegalArgumentException ex) {
                errorMessage = Optional.ofNullable(fieldError.getDefaultMessage()).orElse("");
            }
            errors.merge(fieldName, errorMessage,
                    (existingErrorMessage, newErrorMessage) -> existingErrorMessage + ", " + newErrorMessage);
        });

// 클래스 레벨 에러 처리 (ObjectError)
        e.getBindingResult().getGlobalErrors().forEach(objectError -> {
            String objectName = objectError.getObjectName();
            String errorMessage;
            try {
                errorMessage = Optional.ofNullable(ErrorStatus.valueOf(objectError.getDefaultMessage()).getMessage()).orElse("");
            } catch (IllegalArgumentException ex) {
                errorMessage = Optional.ofNullable(objectError.getDefaultMessage()).orElse("");
            }
            errors.merge("message :", errorMessage,
                    (existingErrorMessage, newErrorMessage) -> existingErrorMessage + ", " + newErrorMessage);
        });

        logRequestRejected(e, ErrorStatus._BAD_REQUEST, request);
        return handleExceptionInternalArgs(
                e, HttpHeaders.EMPTY, ErrorStatus.valueOf("_BAD_REQUEST"), request, errors);
    }

    @ExceptionHandler
    public ResponseEntity<Object> exception(Exception e, WebRequest request) {
        if (e instanceof HttpMessageNotReadableException) {
            log.warn(
                    "요청 본문 파싱 거절 event=http.request.parse outcome=rejected status={} errorCode={} "
                            + "method={} path={} errorType={}",
                    ErrorStatus._BAD_REQUEST.getHttpStatus().value(),
                    ErrorStatus._BAD_REQUEST.getCode(),
                    requestMethod(request),
                    requestPath(request),
                    e.getClass().getName()
            );

            String errorMessage = "요청 본문(JSON) 파싱 실패: " + e.getMessage();
            return handleExceptionInternalMessage(e, HttpHeaders.EMPTY, request, errorMessage);
        }

        reportUnexpectedException(e);
        log.error(
                "HTTP 요청 처리 실패 event=http.request outcome=failed status={} errorCode={} "
                        + "method={} path={} errorType={}",
                ErrorStatus._INTERNAL_SERVER_ERROR.getHttpStatus().value(),
                ErrorStatus._INTERNAL_SERVER_ERROR.getCode(),
                requestMethod(request),
                requestPath(request),
                e.getClass().getName()
        );

        return handleExceptionInternalFalse(
                e,
                ErrorStatus._INTERNAL_SERVER_ERROR,
                HttpHeaders.EMPTY,
                ErrorStatus._INTERNAL_SERVER_ERROR.getHttpStatus(),
                request,
                   e.getMessage());
    }

    @ExceptionHandler(value = GeneralException.class)
    public ResponseEntity onThrowException(
            GeneralException generalException, HttpServletRequest request) {
        ErrorReasonDTO reason = generalException.getCode().getReasonHttpStatus();
        log.warn(
                "비즈니스 요청 거절 event=http.business outcome=rejected status={} errorCode={} "
                        + "method={} path={} errorType={}",
                reason.getHttpStatus().value(),
                reason.getCode(),
                request.getMethod(),
                request.getRequestURI(),
                generalException.getClass().getName()
        );
        HttpHeaders headers = new HttpHeaders();
        if (generalException instanceof ExamsException examsException
                && examsException.getRetryAfterSeconds() != null) {
            headers.set(HttpHeaders.RETRY_AFTER, examsException.getRetryAfterSeconds().toString());
        }
        return handleExceptionInternal(generalException, generalException.getCode(), headers, request);
    }

    private void logRequestRejected(Exception exception, BaseErrorCode code, WebRequest request) {
        ErrorReasonDTO reason = code.getReasonHttpStatus();
        log.warn(
                "HTTP 요청 거절 event=http.request outcome=rejected status={} errorCode={} "
                        + "method={} path={} errorType={}",
                reason.getHttpStatus().value(),
                reason.getCode(),
                requestMethod(request),
                requestPath(request),
                exception.getClass().getName()
        );
    }

    private static String requestMethod(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest().getMethod();
        }
        return "unknown";
    }

    private static String requestPath(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest().getRequestURI();
        }
        return "unknown";
    }

    private void reportUnexpectedException(Exception exception) {
        try {
            unexpectedExceptionReporter.report(exception);
        } catch (RuntimeException reportingFailure) {
            log.warn(
                    "Sentry 예외 보고 실패 event=sentry.exception.report outcome=failed "
                            + "errorType={}",
                    reportingFailure.getClass().getName()
            );
        }
    }

    private ResponseEntity<Object> handleExceptionInternal(
            Exception e, BaseErrorCode code, HttpHeaders headers, HttpServletRequest request) {

        BaseResponse<Object> body =
                BaseResponse.onFailure(code, null);

        WebRequest webRequest = new ServletWebRequest(request);
        return super.handleExceptionInternal(e, body, headers, code.getReasonHttpStatus().getHttpStatus(), webRequest);
    }

    private ResponseEntity<Object> handleExceptionInternalFalse(
            Exception e,
            ErrorStatus errorCommonStatus,
            HttpHeaders headers,
            HttpStatus status,
            WebRequest request,
            String errorPoint) {
        BaseResponse<Object> body =
                BaseResponse.onFailure(errorCommonStatus, errorPoint);
        return super.handleExceptionInternal(e, body, headers, status, request);
    }

    private ResponseEntity<Object> handleExceptionInternalArgs(
            Exception e,
            HttpHeaders headers,
            ErrorStatus errorCommonStatus,
            WebRequest request,
            Map<String, String> errorArgs) {
        BaseResponse<Object> body =
                BaseResponse.onFailure(errorCommonStatus, errorArgs);
        return super.handleExceptionInternal(
                e, body, headers, errorCommonStatus.getHttpStatus(), request);
    }

    private ResponseEntity<Object> handleExceptionInternalConstraint(
            Exception e, ErrorStatus errorCommonStatus, HttpHeaders headers, WebRequest request) {
        BaseResponse<Object> body =
                BaseResponse.onFailure(errorCommonStatus, null);
        return super.handleExceptionInternal(
                e, body, headers, errorCommonStatus.getHttpStatus(), request);
    }

    private ResponseEntity<Object> handleExceptionInternalMessage(
            Exception e, HttpHeaders headers, WebRequest request, String errorMessage) {
        ErrorStatus errorStatus = ErrorStatus._BAD_REQUEST;
        BaseResponse<String> body =
                BaseResponse.onFailure(errorStatus, errorMessage);

        return super.handleExceptionInternal(
                e, body, headers, errorStatus.getHttpStatus(), request);
    }
}
