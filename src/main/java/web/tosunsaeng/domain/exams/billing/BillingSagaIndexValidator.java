package web.tosunsaeng.domain.exams.billing;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;

import java.util.ArrayList;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "app.billing",
        name = "creation-saga-enabled",
        havingValue = "true"
)
public class BillingSagaIndexValidator implements ApplicationRunner {

    public static final String OPERATION_KEY_INDEX = "uniq_exam_creation_user_operation";
    public static final String ACTIVE_OPERATION_INDEX = "uniq_exam_creation_active_user";
    public static final String OPERATION_STATE_INDEX = "idx_exam_creation_state_updated";
    public static final String OPERATION_PURGE_INDEX = "ttl_exam_creation_purge";
    public static final String SESSION_OPERATION_INDEX = "uniq_exam_sessions_creation_operation";
    public static final String SESSION_RESERVATION_INDEX = "uniq_exam_sessions_billing_reservation";
    public static final String SESSION_GROUP_INDEX = "idx_exam_sessions_attempt_group_created";

    private static final Profiles STAGING_OR_PROD = Profiles.of("staging", "prod");

    private final MongoTemplate mongoTemplate;
    private final Environment environment;

    public BillingSagaIndexValidator(MongoTemplate mongoTemplate, Environment environment) {
        this.mongoTemplate = mongoTemplate;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean failClosed = environment.acceptsProfiles(STAGING_OR_PROD);
        try {
            validate(failClosed);
        } catch (RuntimeException failure) {
            if (failClosed) {
                throw new IllegalStateException("Billing saga MongoDB index validation failed", failure);
            }
            log.warn("현재 환경에서 Billing saga MongoDB index를 확인할 수 없습니다");
        }
    }

    void validate(boolean failClosed) {
        List<IndexInfo> operationIndexes = mongoTemplate
                .indexOps(ExamCreationOperation.class).getIndexInfo();
        List<IndexInfo> sessionIndexes = mongoTemplate
                .indexOps(ExamSession.class).getIndexInfo();
        List<String> problems = new ArrayList<>();

        require(operationIndexes, OPERATION_KEY_INDEX,
                List.of(key("userId"), key("operationId")), true, null, problems);
        require(operationIndexes, ACTIVE_OPERATION_INDEX,
                List.of(key("userId"), key("activeGuard")), true,
                new Document("activeGuard", true), problems);
        require(operationIndexes, OPERATION_STATE_INDEX,
                List.of(key("state"), key("updatedAt")), false, null, problems);
        require(operationIndexes, OPERATION_PURGE_INDEX,
                List.of(key("purgeAt")), false,
                new Document("purgeAt", new Document("$type", "date")), problems);
        operationIndexes.stream()
                .filter(index -> OPERATION_PURGE_INDEX.equals(index.getName()))
                .findFirst()
                .filter(index -> !Duration.ZERO.equals(index.getExpireAfter().orElse(null)))
                .ifPresent(index -> problems.add(OPERATION_PURGE_INDEX + " expireAfter"));
        require(sessionIndexes, SESSION_OPERATION_INDEX,
                List.of(key("userId"), key("creationOperationId")), true,
                new Document("creationOperationId", new Document("$type", "string")), problems);
        require(sessionIndexes, SESSION_RESERVATION_INDEX,
                List.of(key("billingReservationId")), true,
                new Document("billingReservationId", new Document("$type", "string")), problems);
        require(sessionIndexes, SESSION_GROUP_INDEX,
                List.of(key("attemptGroupId"), key("createdAt")), false, null, problems);

        if (!problems.isEmpty()) {
            String message = "Billing saga MongoDB index validation failed: "
                    + String.join("; ", problems);
            if (failClosed) {
                throw new IllegalStateException(message);
            }
            log.warn(message);
        }
    }

    private static ExpectedKey key(String field) {
        return new ExpectedKey(field, Sort.Direction.ASC);
    }

    private static void require(
            List<IndexInfo> indexes,
            String name,
            List<ExpectedKey> keys,
            boolean unique,
            Document partial,
            List<String> problems
    ) {
        IndexInfo index = indexes.stream()
                .filter(candidate -> name.equals(candidate.getName()))
                .findFirst()
                .orElse(null);
        if (index == null || !matches(index, keys, unique, partial)) {
            problems.add(name);
        }
    }

    private static boolean matches(
            IndexInfo index,
            List<ExpectedKey> expected,
            boolean unique,
            Document partial
    ) {
        List<IndexField> actual = index.getIndexFields();
        if (actual.size() != expected.size()
                || index.isUnique() != unique
                || index.isSparse()
                || index.isHidden()) {
            return false;
        }
        for (int position = 0; position < expected.size(); position++) {
            if (!expected.get(position).field().equals(actual.get(position).getKey())
                    || expected.get(position).direction()
                    != actual.get(position).getDirection()) {
                return false;
            }
        }
        return Objects.equals(partial, parsePartial(index.getPartialFilterExpression()));
    }

    private static Document parsePartial(String expression) {
        if (expression == null) {
            return null;
        }
        try {
            return Document.parse(expression);
        } catch (RuntimeException invalid) {
            return new Document("$invalid", true);
        }
    }

    private record ExpectedKey(String field, Sort.Direction direction) {
    }
}
