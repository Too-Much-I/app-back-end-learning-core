package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;

import java.util.Optional;

public interface ExamCreationOperationRepository
        extends MongoRepository<ExamCreationOperation, String> {

    Optional<ExamCreationOperation> findByUserIdAndOperationId(String userId, String operationId);

    Optional<ExamCreationOperation> findByUserIdAndActiveGuardTrue(String userId);
}
