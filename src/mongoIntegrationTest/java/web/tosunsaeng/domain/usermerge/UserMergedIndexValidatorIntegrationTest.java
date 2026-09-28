package web.tosunsaeng.domain.usermerge;

import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;
import web.tosunsaeng.domain.usermerge.config.UserMergedIndexValidator;
import web.tosunsaeng.domain.usermerge.domain.UserMergedInboxEvent;
import web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class UserMergedIndexValidatorIntegrationTest {

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");

    @Test
    void validatesRealBuiltInIndexesWithoutUniqueMetadataAndStillRequiresOwnerIndexes() {
        try (var client = MongoClients.create(MONGO.getReplicaSetUrl())) {
            MongoTemplate mongo = new MongoTemplate(client, "learning-core-user-merged-index-it");
            for (Class<?> type : List.of(UserOwnershipGuard.class, UserMergedInboxEvent.class,
                    ExamResult.class, ExamSummary.class)) {
                mongo.createCollection(type);
            }
            for (Class<?> type : List.of(UserOwnershipGuard.class, UserMergedInboxEvent.class)) {
                Document metadata = mongo.getCollection(mongo.getCollectionName(type))
                        .listIndexes().first();
                assertThat(metadata).isNotNull();
                assertThat(metadata.getString("name")).isEqualTo("_id_");
                assertThat(metadata.get("key")).isEqualTo(new Document("_id", 1));
                assertThat(metadata).doesNotContainKey("unique");
                assertThat(IndexInfo.indexInfoOf(metadata).isUnique()).isFalse();
            }

            UserMergedIndexValidator validator = new UserMergedIndexValidator(mongo);
            assertThatThrownBy(() -> validator.run(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(UserMergedIndexValidator.RESULT_OWNER_INDEX);
            mongo.indexOps(ExamResult.class).ensureIndex(new Index("userId", Sort.Direction.ASC)
                    .named(UserMergedIndexValidator.RESULT_OWNER_INDEX));
            assertThatThrownBy(() -> validator.run(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(UserMergedIndexValidator.SUMMARY_OWNER_INDEX);
            mongo.indexOps(ExamSummary.class).ensureIndex(new Index("userId", Sort.Direction.ASC)
                    .named(UserMergedIndexValidator.SUMMARY_OWNER_INDEX));

            assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
        }
    }
}
