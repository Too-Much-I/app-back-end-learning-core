package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExamResultRepository extends MongoRepository<ExamResult, String> {
    List<ExamResult> findByExamId(String examId);

    Optional<ExamResult> findFirstByExamIdAndQuestionNumberAndRetryCountInOrderByIdDesc(
            String examId,
            Integer questionNumber,
            Collection<Integer> retryCounts
    );

    Optional<ExamResult> findFirstByExamIdAndTotalScoreIsNotNullOrderByIdDesc(String examId);

    boolean existsByExamIdAndQuestionNumberAndRetryCount(String examId, Integer questionNumber, Integer retryCount);

    boolean existsByExamIdAndQuestionNumberAndRetryCountIn(
            String examId,
            Integer questionNumber,
            Collection<Integer> retryCounts
    );

    @Query(
            value = "{ 'examId': { '$in': ?0 }, 'totalScore': { '$exists': true, '$ne': null } }",
            fields = "{ '_id': 1, 'examId': 1, 'totalScore': 1, 'levelEstimate': 1 }",
            sort = "{ 'examId': 1, '_id': -1 }"
    )
    List<ExamResult> findLegacySummaryCandidatesByExamIdIn(Collection<String> examIds);

    @Query(
            value = "{ 'examId': ?0, 'questionNumber': { '$exists': true, '$ne': null } }",
            fields = "{ '_id': 1, 'questionNumber': 1, 'retryCount': 1, 'score': 1 }",
            sort = "{ 'questionNumber': 1, 'retryCount': 1, '_id': -1 }"
    )
    List<ExamResult> findQuestionAttemptsByExamId(String examId);

    @Query(
            value = "{ 'examId': { '$in': ?0 }, 'questionNumber': { '$exists': true, '$ne': null }, "
                    + "'retryCount': { '$gte': 1 } }",
            fields = "{ '_id': 0, 'examId': 1, 'questionNumber': 1, 'retryCount': 1 }"
    )
    List<ExamResult> findRetriedQuestionCandidatesByExamIdIn(Collection<String> examIds);
}
