package web.tosunsaeng.domain.promotion;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import web.tosunsaeng.domain.learningrecorddeletion.application.DeletionFailure;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardException;
import web.tosunsaeng.global.common.response.BaseResponse;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=PromotionController.class)
public class PromotionExceptionAdvice {
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> error(Exception error) {
        int status=503; String code="PROMOTION_UNAVAILABLE";
        if (error instanceof PromotionFailure f) { status=f.status; code=f.code; }
        else if (error instanceof DeletionFailure f) { status=f.status(); code=f.code(); }
        else if (error instanceof UserOwnershipGuardException) { status=403; code="PROMOTION_FORBIDDEN"; }
        else if (error instanceof MaxUploadSizeExceededException) { status=413; code="PROMOTION_REPORT_TOO_LARGE"; }
        else if (error instanceof ServletRequestBindingException || error instanceof MissingServletRequestPartException) {
            status=400; code="PROMOTION_INVALID_REQUEST";
        }
        // Never return/log exception messages, form fields, original filenames or storage paths.
        return ResponseEntity.status(status).body(new BaseResponse<>(false,code,"프로모션 신청 상태를 확인해 주세요.",null));
    }
}
