package web.tosunsaeng.domain.learningrecorddeletion.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import web.tosunsaeng.domain.learningrecorddeletion.application.*;
import web.tosunsaeng.domain.learningrecorddeletion.domain.DeletionOperation;
import web.tosunsaeng.global.auth.CurrentUserProvider;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.SuccessStatus;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/learning-records")
@ConditionalOnProperty(prefix = "app.learning-record-deletion", name = "read-fence-enabled", havingValue = "true")
public class DeletionController {
    private final DeletionCommandService service;
    private final CurrentUserProvider users;
    @org.springframework.beans.factory.annotation.Value("${app.learning-record-deletion.command-enabled:false}")
    private boolean commandEnabled;
    public DeletionController(DeletionCommandService service, CurrentUserProvider users) { this.service = service; this.users = users; }
    @DeleteMapping
    public ResponseEntity<BaseResponse<DeletionView>> delete(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                            HttpServletRequest request) throws IOException {
        noInput(request);
        if (!commandEnabled) throw DeletionFailure.temporary();
        var operation = service.request(users.getCurrentUserId(), key);
        return ResponseEntity.status(operation.getStatus() == DeletionOperation.Status.COMPLETED ? 200 : 202)
                .body(BaseResponse.onSuccess(SuccessStatus.OK, DeletionView.from(operation)));
    }
    @GetMapping("/deletion")
    public BaseResponse<DeletionView> status(HttpServletRequest request) throws IOException {
        noInput(request);
        return BaseResponse.onSuccess(SuccessStatus.OK, DeletionView.from(service.latest(users.getCurrentUserId())));
    }
    private void noInput(HttpServletRequest request) throws IOException {
        if (request.getQueryString() != null || request.getInputStream().read() != -1)
            throw new DeletionFailure(400, "LEARNING_RECORD_DELETION_INVALID_REQUEST");
    }
}
