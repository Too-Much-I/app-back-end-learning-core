package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.exams.domain.entity.QuestionGradingJob;

import java.util.List;

public interface QuestionGradingJobRepository extends MongoRepository<QuestionGradingJob, String> {
    List<QuestionGradingJob> findByExamIdAndRetryCount(String examId, Integer retryCount);
}
