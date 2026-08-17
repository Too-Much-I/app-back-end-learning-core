package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import web.tosunsaeng.domain.exams.domain.entity.QuestionGradingJob;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface QuestionGradingJobRepository extends MongoRepository<QuestionGradingJob, String> {
    List<QuestionGradingJob> findByExamIdAndRetryCount(String examId, Integer retryCount);

    @Query(
            value = "{ 'examId': ?0 }",
            fields = "{ '_id': 1, 'examId': 1, 'questionNumber': 1, 'retryCount': 1, "
                    + "'status': 1, 'completedAt': 1 }",
            sort = "{ 'questionNumber': 1, 'retryCount': 1, '_id': -1 }"
    )
    List<QuestionGradingJob> findAttemptsByExamId(String examId);

    @Query(
            value = "{ 'examId': { '$in': ?0 }, 'questionNumber': { '$exists': true, '$ne': null }, "
                    + "'retryCount': { '$gte': 1 } }",
            fields = "{ '_id': 0, 'examId': 1, 'questionNumber': 1, 'retryCount': 1 }"
    )
    List<QuestionGradingJob> findRetriedQuestionCandidatesByExamIdIn(Collection<String> examIds);

    @Query("{ '_id': ?0, 'status': 'PROCESSING', 'dispatchAttempt': ?1, "
            + "'$expr': { '$eq': [ { '$ifNull': [ '$recoveryCycle', 0 ] }, ?2 ] } }")
    @Update("{ '$set': { 'status': 'FAILED', 'failedAt': ?3, 'failureReason': ?4 }, "
            + "'$inc': { 'version': 1 } }")
    long failClaimedAttempt(
            String jobId,
            int dispatchAttempt,
            int recoveryCycle,
            Instant failedAt,
            String failureReason);

    @Query("{ '_id': ?0, 'status': 'COMPLETED', "
            + "'$expr': { '$eq': [ { '$ifNull': [ '$recoveryCycle', 0 ] }, ?1 ] } }")
    @Update("{ '$set': { 'status': 'PENDING', 'dispatchAttempt': 0, 'pendingAt': ?2, "
            + "'processingStartedAt': null, 'lastDispatchedAt': null, 'completedAt': null, "
            + "'failedAt': null, 'failureReason': null }, "
            + "'$inc': { 'recoveryCycle': 1, 'version': 1 } }")
    long reopenCompletedMissingResult(String jobId, int expectedRecoveryCycle, Instant pendingAt);
}
