package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import web.tosunsaeng.domain.exams.domain.entity.SummaryGradingJob;

import java.time.Instant;

public interface SummaryGradingJobRepository extends MongoRepository<SummaryGradingJob, String> {

    @Query("{ '_id': ?0, 'status': 'PROCESSING', 'dispatchAttempt': ?1 }")
    @Update("{ '$set': { 'status': 'FAILED', 'failedAt': ?2, 'failureReason': ?3 }, '$inc': { 'version': 1 } }")
    long failClaimedAttempt(String jobId, int dispatchAttempt, Instant failedAt, String failureReason);
}
