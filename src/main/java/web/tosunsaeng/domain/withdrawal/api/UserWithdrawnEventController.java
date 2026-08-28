package web.tosunsaeng.domain.withdrawal.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import web.tosunsaeng.domain.withdrawal.application.UserWithdrawnEventConsumerService;

@RestController
@ConditionalOnProperty(prefix = "app.user-withdrawn", name = "consumer-enabled", havingValue = "true")
public class UserWithdrawnEventController {

    private final UserWithdrawnEventConsumerService consumerService;

    public UserWithdrawnEventController(UserWithdrawnEventConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    @PostMapping("/internal/v1/events/withdrawn")
    public ResponseEntity<Void> consume(@RequestBody UserWithdrawnEventRequest request) {
        consumerService.consume(request);
        return ResponseEntity.noContent().build();
    }
}
