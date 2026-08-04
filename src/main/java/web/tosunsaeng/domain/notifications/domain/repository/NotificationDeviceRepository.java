package web.tosunsaeng.domain.notifications.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationDevice;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationDeviceRepository extends MongoRepository<NotificationDevice, String> {

    Optional<NotificationDevice> findByUserIdAndInstallationIdHash(String userId, String installationIdHash);

    List<NotificationDevice> findByUserIdAndEnabledTrue(String userId);

    @Query("{ 'userId': ?0, 'installationIdHash': ?1, 'enabled': true }")
    @Update("{ '$set': { 'enabled': false, 'disabledAt': ?2, 'updatedAt': ?2 } }")
    long disableOwnedDevice(String userId, String installationIdHash, Instant disabledAt);

    @Query("{ '_id': ?0, 'expoPushTokenHash': ?1, 'enabled': true }")
    @Update("{ '$set': { 'enabled': false, 'disabledAt': ?2, 'updatedAt': ?2 } }")
    long disableEnabledDeviceIfTokenMatches(String deviceId, String expoPushTokenHash, Instant disabledAt);
}
