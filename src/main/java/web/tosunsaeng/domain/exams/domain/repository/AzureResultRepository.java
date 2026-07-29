package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import web.tosunsaeng.domain.exams.domain.entity.AzureResult;

import java.util.Collection;
import java.util.Optional;

public interface AzureResultRepository extends MongoRepository<AzureResult, String> {
    Optional<AzureResult> findFirstByExamIdAndQuestionNumberAndRetryCountOrderByIdDesc(String examId, Integer questionNumber, Integer retryCount);

    @Query("{ 'exam_id': ?0, 'question_number': ?1, 'retry_count': { '$type': 10 } }")
    Optional<AzureResult> findFirstLegacyNullRetryCount(
            String examId,
            Integer questionNumber
    );

    @Query("{ 'exam_id': ?0, 'question_number': ?1, 'retry_count': { '$exists': false } }")
    Optional<AzureResult> findFirstLegacyMissingRetryCount(
            String examId,
            Integer questionNumber
    );

    boolean existsByExamIdAndQuestionNumberAndRetryCountIn(
            String examId,
            Integer questionNumber,
            Collection<Integer> retryCounts
    );
}
