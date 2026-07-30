package web.tosunsaeng.global.config;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.mock.env.MockEnvironment;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExamAssignmentIndexValidatorTest {

    private MongoTemplate mongoTemplate;
    private IndexOperations sessionIndexOperations;
    private IndexOperations mockExamIndexOperations;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        sessionIndexOperations = mock(IndexOperations.class);
        mockExamIndexOperations = mock(IndexOperations.class);
        when(mongoTemplate.indexOps(ExamSession.class)).thenReturn(sessionIndexOperations);
        when(mongoTemplate.indexOps(MockExam.class)).thenReturn(mockExamIndexOperations);
        when(mockExamIndexOperations.getIndexInfo()).thenReturn(List.of(validMockExamIdIndex()));
    }

    @Test
    void exactRequiredIndexesAllowProductionStartup() {
        when(sessionIndexOperations.getIndexInfo()).thenReturn(List.of(
                validActiveSessionIndex(),
                validCompletionLookupIndex()
        ));

        assertDoesNotThrow(() -> productionValidator().run(null));
    }

    @Test
    void missingActiveIndexFailsProductionStartup() {
        when(sessionIndexOperations.getIndexInfo()).thenReturn(List.of(validCompletionLookupIndex()));

        assertThrows(IllegalStateException.class, () -> productionValidator().run(null));
    }

    @Test
    void nonUniqueActiveIndexFailsProductionStartup() {
        when(sessionIndexOperations.getIndexInfo()).thenReturn(List.of(activeSessionIndex(false, true, "userId")));

        assertThrows(IllegalStateException.class, () -> productionValidator().run(null));
    }

    @Test
    void missingPartialFilterFailsProductionStartup() {
        when(sessionIndexOperations.getIndexInfo()).thenReturn(List.of(activeSessionIndex(true, false, "userId")));

        assertThrows(IllegalStateException.class, () -> productionValidator().run(null));
    }

    @Test
    void wrongActiveIndexKeyFailsProductionStartup() {
        when(sessionIndexOperations.getIndexInfo()).thenReturn(List.of(activeSessionIndex(true, true, "examId")));

        assertThrows(IllegalStateException.class, () -> productionValidator().run(null));
    }

    @Test
    void missingMockExamIdUniqueIndexFailsProductionStartup() {
        when(sessionIndexOperations.getIndexInfo()).thenReturn(List.of(validActiveSessionIndex()));
        when(mockExamIndexOperations.getIndexInfo()).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> productionValidator().run(null));
    }

    @Test
    void nonUniqueMockExamIdIndexFailsProductionStartup() {
        when(sessionIndexOperations.getIndexInfo()).thenReturn(List.of(validActiveSessionIndex()));
        when(mockExamIndexOperations.getIndexInfo()).thenReturn(List.of(IndexInfo.indexInfoOf(
                new Document("name", ExamAssignmentIndexValidator.MOCK_EXAM_ID_INDEX)
                        .append("key", new Document("mock_exam_id", 1))
                        .append("unique", false)
        )));

        assertThrows(IllegalStateException.class, () -> productionValidator().run(null));
    }

    @Test
    void missingCompletionLookupIndexDoesNotFailProductionCorrectnessCheck() {
        when(sessionIndexOperations.getIndexInfo()).thenReturn(List.of(validActiveSessionIndex()));

        assertDoesNotThrow(() -> productionValidator().run(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"staging", "prod"})
    void restrictedProfilesFailClosed(String profile) {
        when(sessionIndexOperations.getIndexInfo()).thenReturn(List.of());
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        ExamAssignmentIndexValidator validator = new ExamAssignmentIndexValidator(mongoTemplate, environment);

        assertThrows(IllegalStateException.class, () -> validator.run(null));
    }

    @Test
    void localProfileWarnsInsteadOfFailingWhenRequiredIndexIsMissing() {
        when(sessionIndexOperations.getIndexInfo()).thenReturn(List.of());
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        ExamAssignmentIndexValidator validator = new ExamAssignmentIndexValidator(mongoTemplate, environment);

        assertDoesNotThrow(() -> validator.run(null));
    }

    private ExamAssignmentIndexValidator productionValidator() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return new ExamAssignmentIndexValidator(mongoTemplate, environment);
    }

    private static IndexInfo validActiveSessionIndex() {
        return activeSessionIndex(true, true, "userId");
    }

    private static IndexInfo activeSessionIndex(boolean unique, boolean withPartial, String key) {
        Document source = new Document("name", ExamAssignmentIndexValidator.ACTIVE_SESSION_INDEX)
                .append("key", new Document(key, 1))
                .append("unique", unique);
        if (withPartial) {
            source.append("partialFilterExpression", new Document("active", true));
        }
        return IndexInfo.indexInfoOf(source);
    }

    private static IndexInfo validCompletionLookupIndex() {
        return IndexInfo.indexInfoOf(new Document("name", ExamAssignmentIndexValidator.COMPLETION_LOOKUP_INDEX)
                .append("key", new Document("userId", 1)
                        .append("completedAt", 1)
                        .append("mockExamId", 1)));
    }

    private static IndexInfo validMockExamIdIndex() {
        return IndexInfo.indexInfoOf(new Document("name", ExamAssignmentIndexValidator.MOCK_EXAM_ID_INDEX)
                .append("key", new Document("mock_exam_id", 1))
                .append("unique", true));
    }
}
