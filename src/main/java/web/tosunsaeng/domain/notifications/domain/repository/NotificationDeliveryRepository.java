package web.tosunsaeng.domain.notifications.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationDelivery;

import java.util.List;

public interface NotificationDeliveryRepository extends MongoRepository<NotificationDelivery, String> {

    boolean existsByNotificationIdAndDeviceId(String notificationId, String deviceId);

    List<NotificationDelivery> findByNotificationId(String notificationId);
}
