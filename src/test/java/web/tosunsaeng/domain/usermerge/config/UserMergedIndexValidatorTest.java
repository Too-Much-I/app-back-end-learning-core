package web.tosunsaeng.domain.usermerge.config;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;
import web.tosunsaeng.domain.usermerge.domain.UserMergedInboxEvent;
import web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserMergedIndexValidatorTest {

    private MongoTemplate mongo;
    private UserMergedIndexValidator validator;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        validator = new UserMergedIndexValidator(mongo);
        indexes(UserOwnershipGuard.class, idIndex());
        indexes(UserMergedInboxEvent.class, idIndex());
        indexes(ExamResult.class, ownerIndex(UserMergedIndexValidator.RESULT_OWNER_INDEX));
        indexes(ExamSummary.class, ownerIndex(UserMergedIndexValidator.SUMMARY_OWNER_INDEX));
    }

    @Test
    void acceptsImplicitlyUniqueBuiltInIndexesFromRealisticMetadata() {
        assertThat(IndexInfo.indexInfoOf(idIndex()).isUnique()).isFalse();
        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
    }

    @Test
    void alsoAcceptsExplicitUniqueMetadata() {
        indexes(UserOwnershipGuard.class, idIndex().append("unique", true));
        indexes(UserMergedInboxEvent.class, idIndex().append("unique", true));
        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingBuiltInIndexOnEitherCollection() {
        for (Class<?> type : List.of(UserOwnershipGuard.class, UserMergedInboxEvent.class)) {
            indexes(type);
            assertThatThrownBy(() -> validator.run(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Required UserMerged _id index is missing");
            indexes(type, idIndex());
        }
    }

    @Test
    void rejectsWrongNameKeyCompoundOrDescendingBuiltInIndexes() {
        for (Class<?> type : List.of(UserOwnershipGuard.class, UserMergedInboxEvent.class)) {
            for (Document invalid : List.of(
                    idIndex().append("name", "other"),
                    idIndex().append("key", new Document("other", 1)),
                    idIndex().append("key", new Document("_id", 1).append("other", 1)),
                    idIndex().append("key", new Document("_id", -1)))) {
                indexes(type, invalid.append("unique", true));
                assertThatThrownBy(() -> validator.run(null))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("Required UserMerged _id index is missing");
            }
            indexes(type, idIndex());
        }
    }

    @Test
    void retainsStrictOwnerIndexValidationForBothCollections() {
        for (Class<?> type : List.of(ExamResult.class, ExamSummary.class)) {
            String name = type == ExamResult.class ? UserMergedIndexValidator.RESULT_OWNER_INDEX
                    : UserMergedIndexValidator.SUMMARY_OWNER_INDEX;
            indexes(type);
            assertOwnerRejected(name);
            for (Document invalid : List.of(
                    ownerIndex(name).append("name", "other"),
                    ownerIndex(name).append("unique", true),
                    ownerIndex(name).append("sparse", true),
                    ownerIndex(name).append("hidden", true),
                    ownerIndex(name).append("key", new Document("other", 1)),
                    ownerIndex(name).append("key", new Document("userId", -1)),
                    ownerIndex(name).append("key", new Document("userId", 1).append("other", 1)))) {
                indexes(type, invalid);
                assertOwnerRejected(name);
            }
            indexes(type, ownerIndex(name));
        }
    }

    private void assertOwnerRejected(String name) {
        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Required UserMerged owner index is missing: " + name);
    }

    private void indexes(Class<?> type, Document... metadata) {
        IndexOperations operations = mock(IndexOperations.class);
        when(mongo.indexOps(type)).thenReturn(operations);
        when(operations.getIndexInfo()).thenReturn(
                List.of(metadata).stream().map(IndexInfo::indexInfoOf).toList());
    }

    private static Document idIndex() {
        return new Document("v", 2).append("key", new Document("_id", 1)).append("name", "_id_");
    }

    private static Document ownerIndex(String name) {
        return new Document("v", 2).append("key", new Document("userId", 1)).append("name", name);
    }
}
