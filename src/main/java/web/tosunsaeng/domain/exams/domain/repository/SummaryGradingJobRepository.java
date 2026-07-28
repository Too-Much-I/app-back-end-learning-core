package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.exams.domain.entity.SummaryGradingJob;

public interface SummaryGradingJobRepository extends MongoRepository<SummaryGradingJob, String> {
}
