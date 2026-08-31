package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;

import java.time.LocalDateTime;
import java.util.List;

public interface ExamSessionRepository extends MongoRepository<ExamSession, String> {

    @Query(
            value = "{ 'userId': ?0, 'completedAt': { '$exists': true, '$ne': null } }",
            sort = "{ 'completedAt': -1, '_id': -1 }"
    )
    List<ExamSession> findCompletedByUserId(String userId);

    // Runtime compatibility decides whether a null/missing legacy status candidate is truly in progress.
    @Query(
            value = "{ 'userId': ?0, '$or': ["
                    + "{ 'status': 'IN_PROGRESS' },"
                    + "{ 'status': 'ENTITLEMENT_CONFIRMING' },"
                    + "{ '$and': ["
                    + "{ '$or': [ { 'status': null }, { 'status': { '$exists': false } } ] },"
                    + "{ '$or': [ { 'active': true }, { 'active': null },"
                    + "{ 'active': { '$exists': false } } ] } ] } ] }",
            sort = "{ 'createdAt': -1, '_id': -1 }"
    )
    List<ExamSession> findActiveOrLegacyCandidatesByUserId(String userId);

    @Query("{ '_id': ?0, '$and': ["
            + "{ '$or': [ { 'active': null }, { 'active': { '$exists': false } } ] },"
            + "{ '$or': [ { 'completedAt': null }, { 'completedAt': { '$exists': false } } ] } ] }")
    @Update("{ '$set': { 'completedAt': ?1, 'active': false, 'status': 'COMPLETED' } }")
    long backfillLegacyCompletionIfUnchanged(String examId, LocalDateTime completedAt);

    @Query("{ '_id': ?0, 'active': true, '$and': ["
            + "{ '$or': [ { 'cycleNumber': null }, { 'cycleNumber': { '$exists': false } } ] },"
            + "{ '$or': [ { 'completedAt': null }, { 'completedAt': { '$exists': false } } ] } ] }")
    @Update("{ '$set': { 'completedAt': ?1, 'active': false, 'status': 'COMPLETED' } }")
    long backfillLegacyActiveCompletionIfUnchanged(String examId, LocalDateTime completedAt);

    @Query("{ '_id': ?0, 'completedAt': { '$exists': true, '$ne': null },"
            + "'$or': [ { 'active': null }, { 'active': { '$exists': false } } ] }")
    @Update("{ '$set': { 'active': false, 'status': 'COMPLETED' } }")
    long deactivateLegacyCompletedSessionIfUnchanged(String examId);

    @Query("{ '_id': ?0, 'active': true, 'completedAt': { '$exists': true, '$ne': null },"
            + "'$or': [ { 'cycleNumber': null }, { 'cycleNumber': { '$exists': false } } ] }")
    @Update("{ '$set': { 'active': false, 'status': 'COMPLETED' } }")
    long deactivateLegacyActiveCompletedSessionIfUnchanged(String examId);

    @Query("{ '_id': ?0, 'completedAt': null, '$or': ["
            + "{ 'status': 'IN_PROGRESS' }, { 'status': null }, { 'status': { '$exists': false } }] }")
    @Update("{ '$set': { 'completedAt': ?1, 'active': false, 'status': 'COMPLETED' } }")
    long completeIfIncomplete(String examId, LocalDateTime completedAt);

    @Query("{ '_id': ?0, '$and': ["
            + "{ '$or': [ { 'completedAt': null },"
            + "{ 'completedAt': { '$exists': false } } ] },"
            + "{ '$or': ["
            + "{ 'status': 'IN_PROGRESS' },"
            + "{ 'status': 'ENTITLEMENT_CONFIRMING' },"
            + "{ '$and': ["
            + "{ '$or': [ { 'status': null }, { 'status': { '$exists': false } } ] },"
            + "{ '$or': [ { 'active': true }, { 'active': null },"
            + "{ 'active': { '$exists': false } } ] } ] } ] } ] }")
    @Update("{ '$set': { 'status': 'ABANDONED', 'active': false } }")
    long abandonIfInProgress(String examId);

    @Query("{ '_id': ?0, 'status': 'ENTITLEMENT_CONFIRMING', 'active': true }")
    @Update("{ '$set': { 'status': 'IN_PROGRESS', 'entitlementState': 'CONFIRMED', "
            + "'entitlementConfirmedAt': ?1 } }")
    long confirmEntitlementIfConfirming(String examId, java.time.Instant confirmedAt);

    @Query("{ '_id': ?0, 'status': 'ENTITLEMENT_CONFIRMING' }")
    @Update("{ '$set': { 'status': 'ABANDONED', 'active': false } }")
    long abandonIfEntitlementConfirming(String examId);

    java.util.Optional<ExamSession> findByUserIdAndCreationOperationId(
            String userId,
            String creationOperationId
    );
}
