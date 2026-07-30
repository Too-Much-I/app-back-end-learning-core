package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;

import java.time.LocalDateTime;
import java.util.List;

public interface ExamSessionRepository extends MongoRepository<ExamSession, String> {

    // Runtime compatibility decides whether a null/missing active candidate is truly in progress.
    @Query(
            value = "{ 'userId': ?0, '$or': ["
                    + "{ 'active': true }, { 'active': null }, { 'active': { '$exists': false } }] }",
            sort = "{ 'createdAt': -1, '_id': -1 }"
    )
    List<ExamSession> findActiveOrLegacyCandidatesByUserId(String userId);

    @Query("{ '_id': ?0, '$and': ["
            + "{ '$or': [ { 'active': null }, { 'active': { '$exists': false } } ] },"
            + "{ '$or': [ { 'completedAt': null }, { 'completedAt': { '$exists': false } } ] } ] }")
    @Update("{ '$set': { 'completedAt': ?1, 'active': false } }")
    long backfillLegacyCompletionIfUnchanged(String examId, LocalDateTime completedAt);

    @Query("{ '_id': ?0, 'active': true, '$and': ["
            + "{ '$or': [ { 'cycleNumber': null }, { 'cycleNumber': { '$exists': false } } ] },"
            + "{ '$or': [ { 'completedAt': null }, { 'completedAt': { '$exists': false } } ] } ] }")
    @Update("{ '$set': { 'completedAt': ?1, 'active': false } }")
    long backfillLegacyActiveCompletionIfUnchanged(String examId, LocalDateTime completedAt);

    @Query("{ '_id': ?0, 'completedAt': { '$exists': true, '$ne': null },"
            + "'$or': [ { 'active': null }, { 'active': { '$exists': false } } ] }")
    @Update("{ '$set': { 'active': false } }")
    long deactivateLegacyCompletedSessionIfUnchanged(String examId);

    @Query("{ '_id': ?0, 'active': true, 'completedAt': { '$exists': true, '$ne': null },"
            + "'$or': [ { 'cycleNumber': null }, { 'cycleNumber': { '$exists': false } } ] }")
    @Update("{ '$set': { 'active': false } }")
    long deactivateLegacyActiveCompletedSessionIfUnchanged(String examId);

    @Query("{ '_id': ?0, 'completedAt': null }")
    @Update("{ '$set': { 'completedAt': ?1, 'active': false } }")
    long completeIfIncomplete(String examId, LocalDateTime completedAt);
}
