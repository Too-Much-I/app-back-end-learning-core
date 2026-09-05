package web.tosunsaeng.domain.usermerge.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard;

public interface UserOwnershipGuardRepository extends MongoRepository<UserOwnershipGuard, String> {
}
