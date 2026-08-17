package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import web.tosunsaeng.domain.exams.domain.entity.SummaryGradingJob;

import java.time.Instant;

public interface SummaryGradingJobRepository extends MongoRepository<SummaryGradingJob, String> {

    @Query("{ '_id': ?0, 'status': 'PROCESSING', 'dispatchAttempt': ?2, "
            + "'$expr': { '$eq': [ { '$ifNull': [ '$generationAttempt', 1 ] }, ?1 ] }, "
            + "'completionClaimedGeneration': { '$ne': ?1 } }")
    @Update("{ '$set': { 'status': 'FAILED', 'failedAt': ?3, 'failureReason': ?4 }, "
            + "'$inc': { 'version': 1 } }")
    long failClaimedAttempt(
            String jobId,
            int generationAttempt,
            int dispatchAttempt,
            Instant failedAt,
            String failureReason);

    @Query("{ '_id': ?0, 'status': { '$in': [ 'PENDING', 'PROCESSING', 'FAILED' ] }, "
            + "'$expr': { '$eq': [ { '$ifNull': [ '$generationAttempt', 1 ] }, ?1 ] }, "
            + "'completionClaimedGeneration': { '$ne': ?1 } }")
    @Update("{ '$set': { 'generationAttempt': ?1, 'status': 'FAILED', "
            + "'completedAt': null, 'failedAt': ?2, 'failureReason': ?3 }, "
            + "'$inc': { 'version': 1 } }")
    long failGeneration(
            String jobId,
            int generationAttempt,
            Instant failedAt,
            String failureReason);

    @Query("{ '_id': ?0, 'status': 'FAILED', 'failureReason': ?3, "
            + "'$expr': { '$eq': [ { '$ifNull': [ '$generationAttempt', 1 ] }, ?1 ] }, "
            + "'completionClaimedGeneration': { '$ne': ?1 } }")
    @Update("{ '$set': { 'generationAttempt': ?2, 'status': 'PENDING', "
            + "'dispatchAttempt': 0, 'pendingAt': ?4, 'processingStartedAt': null, "
            + "'lastDispatchedAt': null, 'completedAt': null, 'failedAt': null, "
            + "'failureReason': null, 'completionClaimedGeneration': null, "
            + "'completionClaimedAt': null }, '$inc': { 'version': 1 } }")
    long rearmFeedbackGeneration(
            String jobId,
            int expectedGenerationAttempt,
            int nextGenerationAttempt,
            String expectedFailureReason,
            Instant pendingAt);

    @Query("{ '_id': ?0, 'status': { '$in': [ 'PENDING', 'PROCESSING', 'FAILED', 'COMPLETED' ] }, "
            + "'$expr': { '$eq': [ { '$ifNull': [ '$generationAttempt', 1 ] }, ?1 ] } }")
    @Update("{ '$set': { 'generationAttempt': ?1, 'status': 'PROCESSING', "
            + "'processingStartedAt': ?2, 'failedAt': null, 'failureReason': null, "
            + "'completionClaimedGeneration': ?1, 'completionClaimedAt': ?2 }, "
            + "'$inc': { 'version': 1 } }")
    long claimCompletion(String jobId, int generationAttempt, Instant claimedAt);

    @Query("{ '_id': ?0, 'status': { '$in': [ 'PENDING', 'PROCESSING', 'FAILED' ] }, "
            + "'$expr': { '$eq': [ { '$ifNull': [ '$generationAttempt', 1 ] }, ?1 ] }, "
            + "'completionClaimedGeneration': ?1 }")
    @Update("{ '$set': { 'generationAttempt': ?1, 'status': 'COMPLETED', "
            + "'completedAt': ?2, 'failedAt': null, 'failureReason': null }, "
            + "'$inc': { 'version': 1 } }")
    long completeClaimedGeneration(String jobId, int generationAttempt, Instant completedAt);
}
