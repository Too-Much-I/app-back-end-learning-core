package web.tosunsaeng.domain.exams.access;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessRevocationReadRaceTest {
    private final AccessRevocationEvent event = new AccessRevocationEvent("event", "scope", "now", "owner",
            "group", "PURCHASE_REFUNDED", null, "digest");

    @SuppressWarnings("unchecked")
    private boolean classifyAfterConcurrentCommit(String eventId, String digest) {
        var mongo = mock(MongoTemplate.class);
        var factory = mock(MongoDatabaseFactory.class);
        var database = mock(MongoDatabase.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        FindIterable<Document> eventRead = mock(FindIterable.class), groupRead = mock(FindIterable.class);
        when(mongo.getMongoDatabaseFactory()).thenReturn(factory);
        when(factory.getMongoDatabase()).thenReturn(database);
        when(database.getCollection(AccessRevocationStore.COLLECTION)).thenReturn(collection);
        when(collection.withReadConcern(ReadConcern.MAJORITY)).thenReturn(collection);
        when(collection.withReadPreference(ReadPreference.primary())).thenReturn(collection);
        when(collection.find(any(Bson.class))).thenReturn(eventRead, groupRead);
        when(eventRead.first()).thenReturn(null);
        when(groupRead.first()).thenReturn(new Document("eventId", eventId).append("digest", digest));
        var store = new AccessRevocationStore(mongo, "test", "scope", Clock.systemUTC());
        return Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(store, "classify", event, true));
    }

    @Test void sameEventCommittedBetweenReadsIsADuplicate() {
        assertThat(classifyAfterConcurrentCommit("event", "digest")).isTrue();
    }
    @Test void sameEventDifferentContentIsStillAnEventConflict() {
        assertThatThrownBy(() -> classifyAfterConcurrentCommit("event", "different"))
                .isInstanceOfSatisfying(AccessEventFailure.class, e -> assertThat(e.code()).isEqualTo("EVENT_ID_CONFLICT"));
    }
    @Test void differentEventIsStillAGroupConflict() {
        assertThatThrownBy(() -> classifyAfterConcurrentCommit("other", "digest"))
                .isInstanceOfSatisfying(AccessEventFailure.class, e -> assertThat(e.code()).isEqualTo("GROUP_REVOKE_CONFLICT"));
    }
}
