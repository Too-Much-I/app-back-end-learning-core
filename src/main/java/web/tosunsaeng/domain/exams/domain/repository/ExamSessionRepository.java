package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;

public interface ExamSessionRepository extends MongoRepository<ExamSession, String> {
}
