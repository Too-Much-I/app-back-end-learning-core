package web.tosunsaeng.global.error.code.status;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseErrorCode {

    // 기본 에러
    _INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 에러, 관리자에게 문의 바랍니다."),
    _BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
    _UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
    _FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "금지된 요청입니다."),
    _ACCOUNT_WITHDRAWN(HttpStatus.UNAUTHORIZED, "ACCOUNT_WITHDRAWN", "탈퇴 처리된 계정입니다."),
    _WITHDRAWAL_DENY_GATE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "WITHDRAWAL_DENY_GATE_UNAVAILABLE",
            "탈퇴 계정 확인을 일시적으로 수행할 수 없습니다."
    ),
    _ACCOUNT_MERGED_TOKEN_REJECTED(
            HttpStatus.FORBIDDEN,
            "ACCOUNT_MERGED_TOKEN_REJECTED",
            "다른 계정으로 병합된 계정입니다."
    ),
    _USER_MERGED_DENY_GATE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "USER_MERGED_DENY_GATE_UNAVAILABLE",
            "병합 계정 확인을 일시적으로 수행할 수 없습니다."
    ),

    // Member
    _MEMBER_NOT_FOUND(HttpStatus.FORBIDDEN, "MEMBER_4000", "없는 유저 입니다."),

    // Exams
    _EXAM_NOT_FOUND(HttpStatus.NOT_FOUND, "EXAM_4004", "해당 모의고사 세션을 찾을 수 없습니다."),
    _EXAM_PAPER_NOT_FOUND(HttpStatus.NOT_FOUND, "EXAM_4005", "해당 문제지를 찾을 수 없습니다."),
    _EXAM_ABANDONED(HttpStatus.CONFLICT, "EXAM_4007", "새 시험 시작으로 종료된 시험입니다."),
    _EXAM_ALREADY_COMPLETED(HttpStatus.CONFLICT, "EXAM_4008", "이미 완료된 시험입니다."),
    _EXAM_CATALOG_CONFIGURATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "EXAM_5001", "모의고사 카탈로그 설정이 올바르지 않습니다."),
    _EXAM_SESSION_CONFIGURATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "EXAM_5002", "모의고사 세션 데이터 설정이 올바르지 않습니다."),
    _IDEMPOTENCY_KEY_INVALID(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID", "Idempotency-Key가 올바르지 않습니다."),
    _ENTITLEMENT_INSUFFICIENT(HttpStatus.PAYMENT_REQUIRED, "ENTITLEMENT_INSUFFICIENT", "사용 가능한 시험 응시권이 없습니다."),
    _EXAM_CREATION_PROCESSING(HttpStatus.CONFLICT, "EXAM_CREATION_PROCESSING", "시험 생성이 처리 중입니다. 같은 요청으로 다시 시도해 주세요."),
    _IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", "Idempotency-Key가 기존 요청과 충돌합니다."),
    _BILLING_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "BILLING_RATE_LIMITED", "시험 사용권 확인 요청이 많습니다. 잠시 후 다시 시도해 주세요."),
    _BILLING_TEMPORARILY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "BILLING_TEMPORARILY_UNAVAILABLE", "시험 사용권을 일시적으로 확인할 수 없습니다."),
    _FEEDBACK_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FEEDBACK_GENERATION_FAILED", "피드백 생성에 실패했습니다."),
    _AI_SERVER_CONNECTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "EXAM_4001", "AI 채점 서버와 통신할 수 없습니다. 잠시 후 다시 시도해주세요."),
    _QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "EXAM_4002", "해당 문제를 찾을 수 없습니다."),
    _AI_SERVER_PROCESSING_NOW(HttpStatus.BAD_REQUEST, "EXAM_4006", "현재 채점이 진행 중입니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public ErrorReasonDTO getReasonHttpStatus() {
        return ErrorReasonDTO.builder()
                .message(message)
                .code(code)
                .isSuccess(false)
                .httpStatus(httpStatus)
                .build();
    }
}
