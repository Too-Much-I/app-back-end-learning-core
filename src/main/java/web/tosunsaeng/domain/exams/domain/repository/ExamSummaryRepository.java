package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExamSummaryRepository extends MongoRepository<ExamSummary, String> {
    Optional<ExamSummary> findFirstByExamIdOrderByIdDesc(String examId);

    List<ExamSummary> findAllByExamId(String examId);

    boolean existsByExamId(String examId);

    @Query(
            value = "{ 'examId': { '$in': ?0 } }",
            fields = "{ '_id': 1, 'examId': 1, 'totalScore': 1, 'levelEstimate': 1 }",
            sort = "{ 'examId': 1, '_id': -1 }"
    )
    List<ExamSummary> findHistoryCandidatesByExamIdIn(Collection<String> examIds);
}
