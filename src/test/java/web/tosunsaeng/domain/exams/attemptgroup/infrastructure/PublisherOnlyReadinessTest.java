package web.tosunsaeng.domain.exams.attemptgroup.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.domain.Sort;
import web.tosunsaeng.domain.exams.attemptgroup.domain.*;
import web.tosunsaeng.domain.exams.attemptgroup.application.*;
import java.util.List;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PublisherOnlyReadinessTest {
    static IndexInfo info(String name, boolean unique, Duration ttl, String... fields) {
        var i=mock(IndexInfo.class);when(i.getName()).thenReturn(name);when(i.isUnique()).thenReturn(unique);
        when(i.getExpireAfter()).thenReturn(java.util.Optional.ofNullable(ttl));
        when(i.getIndexFields()).thenReturn(java.util.Arrays.stream(fields).map(f->IndexField.create(f,Sort.Direction.ASC)).toList());
        return i;
    }
    @Test
    void publisherOnlyHasIndexDependencyAndNoWriterTransaction() {
        var mongo=mock(MongoTemplate.class); var out=mock(IndexOperations.class);var state=mock(IndexOperations.class);
        when(mongo.indexOps(AttemptGroupEventOutbox.class)).thenReturn(out);
        when(mongo.indexOps(AttemptGroupPublisherState.class)).thenReturn(state);
        doReturn(List.of(info("_id_",true,null,"_id"),
                info("uq_attempt_group_session_slot",true,null,"sessionId","eventSlot"),
                info("ix_attempt_group_claim",false,null,"status","nextAttemptAt","leaseUntil","_id"),
                info("ttl_attempt_group_outbox",false,Duration.ZERO,"expiresAt"))).when(out).getIndexInfo();
        doReturn(List.of(info("_id_",true,null,"_id"))).when(state).getIndexInfo();
        runner(mongo).run(c->{
            assertThat(c).hasNotFailed().hasSingleBean(AttemptGroupOutboxPublisher.class)
                    .hasSingleBean(AttemptGroupEventIndexValidator.class);
            assertThat(c.containsBean("attemptGroupTransactionOperations")).isFalse();
            verify(out,times(3)).ensureIndex(any());verify(state).ensureIndex(any());
            verify(c.getBean(AttemptGroupOutboxStore.class),never()).claimNext(any(),any(),any());
        });
    }
    @Test
    void indexFailurePreventsPublisherConstructionAndAnySend() {
        var mongo=mock(MongoTemplate.class);when(mongo.indexOps(AttemptGroupEventOutbox.class)).thenThrow(new IllegalStateException("unavailable"));
        runner(mongo).run(c->assertThat(c).hasFailed());
    }
    @Test
    void missingOrWrongIndexesFailClosed() {
        var mongo=mock(MongoTemplate.class);var out=mock(IndexOperations.class);
        when(mongo.indexOps(AttemptGroupEventOutbox.class)).thenReturn(out);
        when(out.getIndexInfo()).thenReturn(List.of());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                ()->new AttemptGroupEventIndexValidator(mongo).ensureRequiredIndexes());
        doReturn(List.of(info("_id_",true,null,"_id"),
                info("uq_attempt_group_session_slot",false,null,"sessionId","eventSlot"))).when(out).getIndexInfo();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                ()->new AttemptGroupEventIndexValidator(mongo).ensureRequiredIndexes());
    }
    private ApplicationContextRunner runner(MongoTemplate mongo) {
        return new ApplicationContextRunner().withPropertyValues("app.attempt-group-events.writer-enabled=false",
                "app.attempt-group-events.publisher-enabled=true")
                .withUserConfiguration(AttemptGroupEventIndexValidator.class,AttemptGroupOutboxPublisher.class,
                        JwtAttemptGroupEventClient.class, AttemptGroupPublisherStateStore.class)
                .withBean(MongoTemplate.class,()->mongo)
                .withBean(AttemptGroupEventProperties.class,()->JwtAttemptGroupEventClientTest.properties("https://billing.test"))
                .withBean(AttemptGroupOutboxStore.class,()->mock(AttemptGroupOutboxStore.class))
                .withBean(web.tosunsaeng.domain.exams.billing.BillingWorkloadTokenIssuer.class,
                        web.tosunsaeng.domain.exams.billing.WorkloadTestKeys::issuer)
                .withBean(AttemptGroupTraceContext.class,()->mock(AttemptGroupTraceContext.class))
                .withBean(AttemptGroupEventMetrics.class,()->mock(AttemptGroupEventMetrics.class))
                .withBean("gradingClock",java.time.Clock.class,java.time.Clock::systemUTC);
    }
}
