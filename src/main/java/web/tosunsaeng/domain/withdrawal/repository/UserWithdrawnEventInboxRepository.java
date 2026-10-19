package web.tosunsaeng.domain.withdrawal.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.withdrawal.domain.UserWithdrawnEventInbox;

public interface UserWithdrawnEventInboxRepository extends MongoRepository<UserWithdrawnEventInbox, String> {
}
