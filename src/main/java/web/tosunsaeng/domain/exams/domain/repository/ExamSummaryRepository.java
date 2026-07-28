package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;

import java.util.Optional;

public interface ExamSummaryRepository extends MongoRepository<ExamSummary, String> {
    Optional<ExamSummary> findFirstByExamIdOrderByIdDesc(String examId);

    boolean existsByExamId(String examId);
}
