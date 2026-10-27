package web.tosunsaeng.domain.promotion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import web.tosunsaeng.global.auth.CurrentUserProvider;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.SuccessStatus;

@RestController
@RequestMapping("/api/v1/promotions")
@ConditionalOnProperty(prefix="app.promotion",name="enabled",havingValue="true")
public class PromotionController {
    private final CurrentUserProvider users;
    private final PromotionService service;
    public PromotionController(CurrentUserProvider users, PromotionService service) { this.users=users; this.service=service; }
    @io.swagger.v3.oas.annotations.Operation(summary="현재 접수 중인 프로모션 신청",description="gifticon(희망 기프티콘, 최대 100자), email(수령 이메일, 최대 254자), report 모두 필수. 성적표 PDF/JPEG/PNG/HEIC 1개, 최대 9 MiB. 은행·계좌·예금주 및 사용자·프로모션 ID는 보내지 않습니다.")
    @PostMapping(value="/applications",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<PromotionService.Receipt> apply(@RequestParam String gifticon,
            @RequestParam String email, @RequestPart MultipartFile report,
            org.springframework.web.multipart.MultipartHttpServletRequest request) {
        if (request.getMultiFileMap().size() != 1 || request.getFiles("report").size() != 1)
            throw PromotionFailure.invalid();
        if (!request.getParameterMap().keySet().equals(java.util.Set.of("gifticon","email"))
                || request.getParameterMap().values().stream().anyMatch(values -> values.length != 1))
            throw PromotionFailure.invalid();
        return BaseResponse.onSuccess(SuccessStatus.OK,service.apply(users.getCurrentUserId(),gifticon,email,report));
    }
}
