package web.tosunsaeng.domain.notifications.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationDevice;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationDevicePlatform;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationDeviceRepository;
import web.tosunsaeng.domain.notifications.dto.NotificationDeviceRequest;
import web.tosunsaeng.domain.notifications.exception.NotificationException;
import web.tosunsaeng.global.auth.CurrentUserProvider;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeviceServiceTest {

    private static final String USER_ID = "00000000-0000-4000-8000-000000000042";
    private static final String INSTALLATION_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TOKEN = "ExponentPushToken[placeholder-value]";
    private static final Instant NOW = Instant.parse("2026-08-04T06:00:00Z");

    @Mock
    private NotificationDeviceRepository deviceRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private NotificationIdentityCodec codec;
    private NotificationDeviceService service;

    @BeforeEach
    void setUp() {
        codec = new NotificationIdentityCodec();
        service = new NotificationDeviceService(
                deviceRepository,
                currentUserProvider,
                codec,
                new ExpoPushTokenValidator(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
    }

    @Test
    void firstRegistrationStoresOnlyInstallationHashAndCurrentJwtUser() {
        String installationHash = codec.installationIdHash(INSTALLATION_ID);
        when(deviceRepository.findByUserIdAndInstallationIdHash(USER_ID, installationHash))
                .thenReturn(Optional.empty());

        assertTrue(service.register(request(TOKEN)).registered());

        ArgumentCaptor<NotificationDevice> captor = ArgumentCaptor.forClass(NotificationDevice.class);
        verify(deviceRepository).insert(captor.capture());
        NotificationDevice stored = captor.getValue();
        assertAll(
                () -> assertEquals(USER_ID, stored.getUserId()),
                () -> assertEquals(installationHash, stored.getInstallationIdHash()),
                () -> assertEquals(NotificationDevicePlatform.IOS, stored.getPlatform()),
                () -> assertEquals(TOKEN, stored.getExpoPushToken()),
                () -> assertEquals(codec.expoPushTokenHash(TOKEN), stored.getExpoPushTokenHash()),
                () -> assertTrue(stored.isEnabled()),
                () -> assertEquals(NOW, stored.getCreatedAt()),
                () -> assertNull(stored.getDisabledAt())
        );
    }

    @Test
    void reRegistrationUpdatesTokenPlatformAndReenablesDevice() {
        String installationHash = codec.installationIdHash(INSTALLATION_ID);
        NotificationDevice existing = NotificationDevice.builder()
                .id(codec.deviceId(USER_ID, installationHash))
                .userId(USER_ID)
                .installationIdHash(installationHash)
                .platform(NotificationDevicePlatform.IOS)
                .expoPushToken(TOKEN)
                .expoPushTokenHash(codec.expoPushTokenHash(TOKEN))
                .enabled(false)
                .createdAt(NOW.minusSeconds(60))
                .disabledAt(NOW.minusSeconds(30))
                .build();
        String changedToken = "ExpoPushToken[changed-placeholder]";
        when(deviceRepository.findByUserIdAndInstallationIdHash(USER_ID, installationHash))
                .thenReturn(Optional.of(existing));

        service.register(new NotificationDeviceRequest.Register(
                INSTALLATION_ID,
                "ANDROID",
                changedToken
        ));

        verify(deviceRepository).save(existing);
        assertAll(
                () -> assertEquals(NotificationDevicePlatform.ANDROID, existing.getPlatform()),
                () -> assertEquals(changedToken, existing.getExpoPushToken()),
                () -> assertEquals(codec.expoPushTokenHash(changedToken), existing.getExpoPushTokenHash()),
                () -> assertTrue(existing.isEnabled()),
                () -> assertNull(existing.getDisabledAt()),
                () -> assertEquals(NOW, existing.getLastRegisteredAt())
        );
        verify(deviceRepository, never()).insert(any(NotificationDevice.class));
    }

    @Test
    void disableUsesJwtUserAndInstallationHashAndIsIdempotentWhenMissing() {
        String hash = codec.installationIdHash(INSTALLATION_ID);
        when(deviceRepository.disableOwnedDevice(USER_ID, hash, NOW)).thenReturn(0L);

        assertTrue(service.disable(INSTALLATION_ID).disabled());

        verify(deviceRepository).disableOwnedDevice(USER_ID, hash, NOW);
    }

    @Test
    void duplicateTokenOwnedElsewhereReturnsSafeConflictWithoutTokenInMessage() {
        String hash = codec.installationIdHash(INSTALLATION_ID);
        when(deviceRepository.findByUserIdAndInstallationIdHash(USER_ID, hash))
                .thenReturn(Optional.empty());
        when(deviceRepository.insert(any(NotificationDevice.class)))
                .thenThrow(new DuplicateKeyException("synthetic duplicate"));

        NotificationException exception = assertThrows(
                NotificationException.class,
                () -> service.register(request(TOKEN))
        );

        assertSame(ErrorStatus._NOTIFICATION_DEVICE_CONFLICT, exception.getCode());
        assertEquals(false, String.valueOf(exception.getMessage()).contains(TOKEN));
        verify(deviceRepository, times(2))
                .findByUserIdAndInstallationIdHash(USER_ID, hash);
    }

    @Test
    void concurrentFirstRegistrationConvergesToTheDeviceCreatedByAnotherRequest() {
        String hash = codec.installationIdHash(INSTALLATION_ID);
        NotificationDevice concurrentlyCreated = NotificationDevice.registered(
                codec.deviceId(USER_ID, hash),
                USER_ID,
                hash,
                NotificationDevicePlatform.IOS,
                TOKEN,
                codec.expoPushTokenHash(TOKEN),
                NOW.minusSeconds(1)
        );
        when(deviceRepository.findByUserIdAndInstallationIdHash(USER_ID, hash))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(concurrentlyCreated),
                        Optional.of(concurrentlyCreated)
                );
        when(deviceRepository.insert(any(NotificationDevice.class)))
                .thenThrow(new DuplicateKeyException("synthetic concurrent insert"));

        assertTrue(service.register(request(TOKEN)).registered());

        verify(deviceRepository).insert(any(NotificationDevice.class));
        verify(deviceRepository).save(concurrentlyCreated);
        verify(deviceRepository, times(3))
                .findByUserIdAndInstallationIdHash(USER_ID, hash);
    }

    @Test
    void invalidUuidPlatformAndTokenAreRejectedBeforeAnyWrite() {
        assertThrows(NotificationException.class, () -> service.register(
                new NotificationDeviceRequest.Register("not-a-uuid", "IOS", TOKEN)
        ));
        assertThrows(NotificationException.class, () -> service.register(
                new NotificationDeviceRequest.Register(INSTALLATION_ID, "WEB", TOKEN)
        ));
        assertThrows(NotificationException.class, () -> service.register(
                new NotificationDeviceRequest.Register(INSTALLATION_ID, "IOS", "pushToken[value]")
        ));

        verify(deviceRepository, never()).insert(any(NotificationDevice.class));
        verify(deviceRepository, never()).save(any(NotificationDevice.class));
    }

    private NotificationDeviceRequest.Register request(String token) {
        return new NotificationDeviceRequest.Register(INSTALLATION_ID, "IOS", token);
    }
}
