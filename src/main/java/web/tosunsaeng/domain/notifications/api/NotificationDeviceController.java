package web.tosunsaeng.domain.notifications.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import web.tosunsaeng.domain.notifications.application.NotificationDeviceService;
import web.tosunsaeng.domain.notifications.dto.NotificationDeviceRequest;
import web.tosunsaeng.domain.notifications.dto.NotificationDeviceResponse;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.SuccessStatus;

@Tag(name = "Notification Device API", description = "Expo Push 알림 기기 등록 및 비활성화 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications/devices")
public class NotificationDeviceController {

    private final NotificationDeviceService deviceService;

    @Operation(
            summary = "Expo Push 기기 등록·갱신 API",
            description = "Bearer Access Token의 사용자에게 UUIDv4 installationId와 ExpoPushToken을 등록합니다."
    )
    @PutMapping
    public BaseResponse<NotificationDeviceResponse.Registered> register(
            @RequestBody NotificationDeviceRequest.Register request) {
        return BaseResponse.onSuccess(
                SuccessStatus.NOTIFICATION_DEVICE_REGISTERED,
                deviceService.register(request)
        );
    }

    @Operation(
            summary = "Expo Push 기기 비활성화 API",
            description = "Bearer Access Token 사용자가 소유한 installationId의 알림 기기를 멱등적으로 비활성화합니다."
    )
    @DeleteMapping("/{installationId}")
    public BaseResponse<NotificationDeviceResponse.Disabled> disable(
            @PathVariable("installationId") String installationId) {
        return BaseResponse.onSuccess(
                SuccessStatus.NOTIFICATION_DEVICE_DISABLED,
                deviceService.disable(installationId)
        );
    }
}
