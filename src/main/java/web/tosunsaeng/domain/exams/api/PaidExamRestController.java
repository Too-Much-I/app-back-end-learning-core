package web.tosunsaeng.domain.exams.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import web.tosunsaeng.domain.exams.application.ExamService;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.SuccessStatus;

/** Creation only; existing v1 learning APIs and DTOs remain shared. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/exams")
public class PaidExamRestController {
    private final ExamService examService;

    @PostMapping("")
    public BaseResponse<ExamResponseDTO.CreateSessionResult> createSession(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        // The existing saga validates missing/malformed keys with the established public error.
        return BaseResponse.onSuccess(SuccessStatus.OK, examService.createPaidExamSession(idempotencyKey));
    }
}
