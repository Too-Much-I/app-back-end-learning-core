package web.tosunsaeng.domain.withdrawal.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.withdrawal.domain.WithdrawnUserAccessDeny;

public interface WithdrawnUserAccessDenyRepository extends MongoRepository<WithdrawnUserAccessDeny, String> {
}
