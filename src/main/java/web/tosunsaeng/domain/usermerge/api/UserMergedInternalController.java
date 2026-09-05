package web.tosunsaeng.domain.usermerge.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import web.tosunsaeng.domain.usermerge.application.UserMergedConsumerService;

@RestController
@ConditionalOnProperty(prefix = "app.user-merged", name = "consumer-enabled", havingValue = "true")
public class UserMergedInternalController {

    private final UserMergedConsumerService consumerService;

    public UserMergedInternalController(UserMergedConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    @PostMapping("/internal/v1/events/user-merged")
    public ResponseEntity<Void> consume(@RequestBody UserMergedEventRequest request) {
        consumerService.consume(request);
        return ResponseEntity.noContent().build();
    }
}
