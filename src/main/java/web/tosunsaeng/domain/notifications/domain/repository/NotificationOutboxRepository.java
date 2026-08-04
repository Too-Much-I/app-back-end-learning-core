package web.tosunsaeng.domain.notifications.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationOutbox;

import java.util.Optional;

public interface NotificationOutboxRepository extends MongoRepository<NotificationOutbox, String> {

    boolean existsByEventKey(String eventKey);

    Optional<NotificationOutbox> findByEventKey(String eventKey);
}
