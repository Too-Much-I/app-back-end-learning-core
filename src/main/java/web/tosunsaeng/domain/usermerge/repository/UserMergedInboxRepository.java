package web.tosunsaeng.domain.usermerge.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.usermerge.domain.UserMergedInboxEvent;

public interface UserMergedInboxRepository extends MongoRepository<UserMergedInboxEvent, String> {
}
