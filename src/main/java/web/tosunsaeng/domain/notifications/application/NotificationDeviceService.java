package web.tosunsaeng.domain.notifications.application;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationDevice;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationDevicePlatform;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationDeviceRepository;
import web.tosunsaeng.domain.notifications.dto.NotificationDeviceRequest;
import web.tosunsaeng.domain.notifications.dto.NotificationDeviceResponse;
import web.tosunsaeng.domain.notifications.exception.NotificationException;
import web.tosunsaeng.global.auth.CurrentUserProvider;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationDeviceService {

    private static final int UPSERT_ATTEMPTS = 2;

    private final NotificationDeviceRepository deviceRepository;
    private final CurrentUserProvider currentUserProvider;
    private final NotificationIdentityCodec identityCodec;
    private final ExpoPushTokenValidator tokenValidator;
    private final Clock clock;

    public NotificationDeviceResponse.Registered register(NotificationDeviceRequest.Register request) {
        if (request == null) {
            throw invalidRequest();
        }

        String userId = currentUserProvider.getCurrentUserId();
        String installationIdHash = identityCodec.installationIdHash(request.installationId());
        NotificationDevicePlatform platform = parsePlatform(request.platform());
        tokenValidator.validate(request.expoPushToken());
        String tokenHash = identityCodec.expoPushTokenHash(request.expoPushToken());
        String deviceId = identityCodec.deviceId(userId, installationIdHash);

        try {
            return registerWithConvergence(
                    userId,
                    installationIdHash,
                    platform,
                    request.expoPushToken(),
                    tokenHash,
                    deviceId
            );
        } catch (DuplicateKeyException duplicate) {
            throw new NotificationException(ErrorStatus._NOTIFICATION_DEVICE_CONFLICT);
        } catch (DataAccessException databaseFailure) {
            throw new NotificationException(ErrorStatus._NOTIFICATION_DEVICE_STORAGE_ERROR);
        }
    }

    private NotificationDeviceResponse.Registered registerWithConvergence(
            String userId,
            String installationIdHash,
            NotificationDevicePlatform platform,
            String expoPushToken,
            String tokenHash,
            String deviceId) {
        for (int attempt = 0; attempt < UPSERT_ATTEMPTS; attempt++) {
            Instant now = clock.instant();
            Optional<NotificationDevice> existing =
                    deviceRepository.findByUserIdAndInstallationIdHash(userId, installationIdHash);
            try {
                if (existing.isPresent()) {
                    NotificationDevice device = existing.get();
                    device.register(platform, expoPushToken, tokenHash, now);
                    deviceRepository.save(device);
                } else {
                    deviceRepository.insert(NotificationDevice.registered(
                            deviceId,
                            userId,
                            installationIdHash,
                            platform,
                            expoPushToken,
                            tokenHash,
                            now
                    ));
                }
                return new NotificationDeviceResponse.Registered(true);
            } catch (DuplicateKeyException duplicate) {
                boolean sameDeviceNowExists = deviceRepository
                        .findByUserIdAndInstallationIdHash(userId, installationIdHash)
                        .isPresent();
                if (!sameDeviceNowExists || attempt + 1 >= UPSERT_ATTEMPTS) {
                    throw duplicate;
                }
            }
        }
        throw new DuplicateKeyException("Notification device upsert did not converge");
    }

    public NotificationDeviceResponse.Disabled disable(String installationId) {
        String userId = currentUserProvider.getCurrentUserId();
        String installationIdHash = identityCodec.installationIdHash(installationId);
        try {
            deviceRepository.disableOwnedDevice(userId, installationIdHash, clock.instant());
        } catch (DataAccessException databaseFailure) {
            throw new NotificationException(ErrorStatus._NOTIFICATION_DEVICE_STORAGE_ERROR);
        }
        return new NotificationDeviceResponse.Disabled(true);
    }

    private NotificationDevicePlatform parsePlatform(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw invalidRequest();
        }
        try {
            return NotificationDevicePlatform.valueOf(value);
        } catch (IllegalArgumentException invalidPlatform) {
            throw invalidRequest();
        }
    }

    private NotificationException invalidRequest() {
        return new NotificationException(ErrorStatus._NOTIFICATION_DEVICE_INVALID_REQUEST);
    }
}
