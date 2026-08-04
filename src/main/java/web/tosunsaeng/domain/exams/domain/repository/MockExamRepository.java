package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;

import java.util.Collection;
import java.util.List;

public interface MockExamRepository extends MongoRepository<MockExam, String> {
    List<MockExam> findAllByMockExamId(String mockExamId);

    @Query(
            value = "{ 'mock_exam_id': { '$in': ?0 } }",
            fields = "{ '_id': 0, 'mock_exam_id': 1, 'title': 1 }"
    )
    List<MockExam> findTitlesByMockExamIdIn(Collection<String> mockExamIds);
}
