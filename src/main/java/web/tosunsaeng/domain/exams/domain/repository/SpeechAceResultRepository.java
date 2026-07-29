package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.exams.domain.entity.SpeechAceResult;

import java.util.Collection;

public interface SpeechAceResultRepository extends MongoRepository<SpeechAceResult, String> {
    boolean existsByExamIdAndQuestionNumberAndRetryCountIn(
            String examId,
            Integer questionNumber,
            Collection<Integer> retryCounts
    );
}
