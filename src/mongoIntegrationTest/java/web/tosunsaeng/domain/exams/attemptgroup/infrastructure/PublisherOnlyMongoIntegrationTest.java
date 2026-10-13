package web.tosunsaeng.domain.exams.attemptgroup.infrastructure;

import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Query;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import web.tosunsaeng.domain.exams.attemptgroup.domain.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Isolated replica set only. No deployed database or writer transaction is used. */
@Testcontainers
class PublisherOnlyMongoIntegrationTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");
    private com.mongodb.client.MongoClient connection;
    private MongoTemplate mongo;
    private AttemptGroupOutboxStore store;
    private final Instant now = Instant.now();

    @BeforeEach void setup() {
        connection = MongoClients.create(MONGO.getReplicaSetUrl());
        mongo = new MongoTemplate(connection, "publisher-only-b-it");
        mongo.getDb().drop();
        store = new AttemptGroupOutboxStore(mongo);
    }
    @AfterEach void close() { if (connection != null) connection.close(); }

    @Test void initializesEmptyCollectionsAndRejectsConflictingIndexWithoutRepair() {
        var validator = new AttemptGroupEventIndexValidator(mongo);
        validator.ensureRequiredIndexes();
        validator.ensureRequiredIndexes();
        assertThat(mongo.indexOps(AttemptGroupEventOutbox.class).getIndexInfo()).hasSize(4);
        assertThat(mongo.indexOps(AttemptGroupPublisherState.class).getIndexInfo()).hasSize(1);
        assertThat(mongo.count(new Query(), AttemptGroupPublisherState.class)).isZero();
        // This destructive operation is limited to this freshly created container test database.
        mongo.indexOps(AttemptGroupEventOutbox.class).dropIndex("uq_attempt_group_session_slot");
        mongo.indexOps(AttemptGroupEventOutbox.class).ensureIndex(new Index().on("sessionId", Sort.Direction.ASC)
                .on("eventSlot", Sort.Direction.ASC).named("uq_attempt_group_session_slot"));
        assertThatThrownBy(validator::ensureRequiredIndexes).isInstanceOf(IllegalStateException.class);
        assertThat(mongo.indexOps(AttemptGroupEventOutbox.class).getIndexInfo().stream()
                .filter(i -> i.getName().equals("uq_attempt_group_session_slot")).findFirst().orElseThrow().isUnique()).isFalse();
    }

    @Test void concurrentLeaseAndLostResponsePreserveEventAndDoNotClaimDeadLetter() throws Exception {
        new AttemptGroupEventIndexValidator(mongo).ensureRequiredIndexes();
        var original = event("pending");
        store.insert(original);
        var dead = event("dead"); dead.deadLetter("http_404", now, Duration.ofDays(90)); store.insert(dead);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Optional<AttemptGroupEventOutbox>> claim = () -> {
                start.await(); return store.claimNext(UUID.randomUUID().toString(), now, now.plusSeconds(30));
            };
            var first = pool.submit(claim); var second = pool.submit(claim); start.countDown();
            var results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(results.stream().filter(Optional::isPresent).count()).isEqualTo(1);
            var winner = results.stream().flatMap(Optional::stream).findFirst().orElseThrow();
            var reclaimed = store.claimNext("after-lost-response", now.plusSeconds(31), now.plusSeconds(61)).orElseThrow();
            assertThat(store.markDelivered(winner.getEventId(), winner.getLeaseToken(), now, now.plusSeconds(500))).isFalse();
            assertThat(reclaimed.getEventId()).isEqualTo(original.getEventId());
            assertThat(reclaimed.getCanonicalPayload()).isEqualTo(original.getCanonicalPayload());
            assertThat(reclaimed.getPayloadDigest()).isEqualTo(original.getPayloadDigest());
            assertThat(store.markDelivered(reclaimed.getEventId(), reclaimed.getLeaseToken(), now, now.plus(Duration.ofDays(30)))).isTrue();
        }
        assertThat(store.claimNext("empty", now.plusSeconds(62), now.plusSeconds(92))).isEmpty();
        assertThat(mongo.count(new Query(), AttemptGroupEventOutbox.class)).isEqualTo(2);
        assertThat(mongo.findById("dead", AttemptGroupEventOutbox.class).getStatus()).isEqualTo(AttemptGroupOutboxStatus.DEAD_LETTER);
    }

    @Test void publisherDrainsExistingEventWithoutWriterTransaction() {
        new AttemptGroupEventIndexValidator(mongo).ensureRequiredIndexes();
        var original = event("drain"); store.insert(original);
        var traces = new org.springframework.beans.factory.support.StaticListableBeanFactory()
                .getBeanProvider(io.micrometer.tracing.Tracer.class);
        var sent = new ArrayList<String>();
        AttemptGroupEventClient client = (body, trace) -> { sent.add(body); return new AttemptGroupEventClient.Response(204, null); };
        var properties = new AttemptGroupEventProperties(false, true, "https://billing.test", null,
                Duration.ofMinutes(30), Duration.ofSeconds(1), 20, Duration.ofSeconds(30), Duration.ofMinutes(15),
                Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofDays(30), Duration.ofDays(90));
        var publisher = new web.tosunsaeng.domain.exams.attemptgroup.application.AttemptGroupOutboxPublisher(
                properties, store, new AttemptGroupPublisherStateStore(mongo), client, new AttemptGroupTraceContext(traces),
                org.mockito.Mockito.mock(web.tosunsaeng.domain.exams.attemptgroup.application.AttemptGroupEventMetrics.class),
                traces, Clock.fixed(now, ZoneOffset.UTC));
        publisher.publishAvailable();
        assertThat(sent).containsExactly(original.getCanonicalPayload());
        assertThat(mongo.findById("drain", AttemptGroupEventOutbox.class).getStatus()).isEqualTo(AttemptGroupOutboxStatus.DELIVERED);
        assertThat(mongo.count(new Query(), AttemptGroupEventOutbox.class)).isEqualTo(1);
    }

    private AttemptGroupEventOutbox event(String id) {
        var payload = new AttemptGroupEventPayload(id, "AttemptGroupStatusChanged", 1, "learning-core", now,
                "test-user", "test-group", "session-" + id, AttemptGroupEventTarget.GRADING, null, null);
        return AttemptGroupEventOutbox.pending(payload, AttemptGroupEventSlot.GRADING, "{\"eventId\":\""+id+"\"}",
                "fixed-test-digest", "0123456789abcdef0123456789abcdef", "0123456789abcdef", "01", now);
    }
}
