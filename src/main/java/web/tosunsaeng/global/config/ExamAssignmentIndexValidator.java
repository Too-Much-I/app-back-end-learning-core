package web.tosunsaeng.global.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class ExamAssignmentIndexValidator implements ApplicationRunner {

    public static final String ACTIVE_SESSION_INDEX = "uniq_exam_sessions_active_user";
    public static final String COMPLETION_LOOKUP_INDEX = "idx_exam_sessions_user_completed_mock_exam";
    public static final String MOCK_EXAM_ID_INDEX = "uniq_mock_exams_mock_exam_id";

    private static final Profiles STAGING_OR_PROD = Profiles.of("staging", "prod");

    private final MongoTemplate mongoTemplate;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        boolean failClosed = environment.acceptsProfiles(STAGING_OR_PROD);
        try {
            validateIndexes(failClosed);
        } catch (RuntimeException inspectionFailure) {
            if (failClosed) {
                throw new IllegalStateException(
                        "Required MongoDB exam-assignment indexes could not be verified", inspectionFailure);
            }
            log.warn("MongoDB exam-assignment indexes could not be verified in this environment");
        }
    }

    void validateIndexes(boolean failClosed) {
        List<IndexInfo> sessionIndexes = mongoTemplate.indexOps(ExamSession.class).getIndexInfo();
        List<IndexInfo> mockExamIndexes = mongoTemplate.indexOps(MockExam.class).getIndexInfo();
        List<String> requiredProblems = new ArrayList<>();

        validateRequired(
                sessionIndexes,
                ACTIVE_SESSION_INDEX,
                List.of(new ExpectedKey("userId", Sort.Direction.ASC)),
                true,
                new Document("active", true),
                requiredProblems
        );
        validateRequired(
                mockExamIndexes,
                MOCK_EXAM_ID_INDEX,
                List.of(new ExpectedKey("mock_exam_id", Sort.Direction.ASC)),
                true,
                null,
                requiredProblems
        );

        if (!requiredProblems.isEmpty()) {
            String message = "Required MongoDB exam-assignment index validation failed: "
                    + String.join("; ", requiredProblems);
            if (failClosed) {
                throw new IllegalStateException(message);
            }
            log.warn(message);
        }

        boolean completionIndexValid = findByName(sessionIndexes, COMPLETION_LOOKUP_INDEX)
                .map(index -> matches(
                        index,
                        List.of(
                                new ExpectedKey("userId", Sort.Direction.ASC),
                                new ExpectedKey("completedAt", Sort.Direction.ASC),
                                new ExpectedKey("mockExamId", Sort.Direction.ASC)
                        ),
                        false,
                        null
                ))
                .orElse(false);
        if (!completionIndexValid) {
            log.warn("MongoDB completion-count lookup index is missing or incompatible: {}",
                    COMPLETION_LOOKUP_INDEX);
        }
    }

    private static void validateRequired(
            List<IndexInfo> indexes,
            String name,
            List<ExpectedKey> keys,
            boolean unique,
            Document partialFilter,
            List<String> problems) {
        IndexInfo index = findByName(indexes, name).orElse(null);
        if (index == null) {
            problems.add(name + " is missing");
            return;
        }
        if (!matches(index, keys, unique, partialFilter)) {
            problems.add(name + " has an incompatible definition");
        }
    }

    private static java.util.Optional<IndexInfo> findByName(List<IndexInfo> indexes, String name) {
        if (indexes == null) {
            return java.util.Optional.empty();
        }
        return indexes.stream().filter(index -> name.equals(index.getName())).findFirst();
    }

    private static boolean matches(
            IndexInfo index,
            List<ExpectedKey> expectedKeys,
            boolean unique,
            Document partialFilter) {
        List<IndexField> actualKeys = index.getIndexFields();
        if (actualKeys.size() != expectedKeys.size()) {
            return false;
        }
        for (int position = 0; position < expectedKeys.size(); position++) {
            ExpectedKey expected = expectedKeys.get(position);
            IndexField actual = actualKeys.get(position);
            if (!expected.field().equals(actual.getKey())
                    || expected.direction() != actual.getDirection()) {
                return false;
            }
        }
        if (index.isUnique() != unique || index.isSparse()) {
            return false;
        }

        Document actualPartial = parsePartialFilter(index.getPartialFilterExpression());
        return Objects.equals(partialFilter, actualPartial);
    }

    private static Document parsePartialFilter(String partialFilter) {
        if (partialFilter == null) {
            return null;
        }
        try {
            return Document.parse(partialFilter);
        } catch (RuntimeException invalidFilter) {
            return new Document("$invalid", true);
        }
    }

    private record ExpectedKey(String field, Sort.Direction direction) {
    }
}
