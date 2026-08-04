package web.tosunsaeng.domain.notifications.infrastructure;

import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationDelivery;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationOutbox;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationDeliveryStatus;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationOutboxStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationClaimStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-04T06:00:00Z");

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void outboxClaimIsOneAtomicFindAndModifyWithLeaseAndAttemptIncrement() {
        NotificationOutbox claimed = NotificationOutbox.builder()
                .notificationId("notification-id")
                .status(NotificationOutboxStatus.PROCESSING)
                .attemptCount(1)
                .leaseUntil(NOW.plusSeconds(120))
                .build();
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(NotificationOutbox.class)
        )).thenReturn(claimed);
        NotificationOutboxStore store = new NotificationOutboxStore(mongoTemplate);

        assertEquals(claimed, store.claimNext(NOW, Duration.ofSeconds(120)));

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(
                queryCaptor.capture(),
                updateCaptor.capture(),
                any(FindAndModifyOptions.class),
                eq(NotificationOutbox.class)
        );
        Document query = queryCaptor.getValue().getQueryObject();
        Document update = updateCaptor.getValue().getUpdateObject();
        assertTrue(query.toString().contains("PENDING"));
        assertTrue(query.toString().contains("FAILED"));
        assertTrue(query.toString().contains("leaseUntil"));
        assertEquals(1, ((Document) update.get("$inc")).get("attemptCount"));
        assertEquals(
                NotificationOutboxStatus.PROCESSING,
                ((Document) update.get("$set")).get("status")
        );
        verify(mongoTemplate, never()).save(any(NotificationOutbox.class));
    }

    @Test
    void twoTicketClaimersCannotReceiveTheSameAlreadyClaimedDelivery() {
        NotificationDelivery claimed = NotificationDelivery.builder()
                .deliveryId("delivery-id")
                .status(NotificationDeliveryStatus.PROCESSING)
                .attemptCount(1)
                .build();
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(NotificationDelivery.class)
        )).thenReturn(claimed, (NotificationDelivery) null);
        NotificationDeliveryStore store = new NotificationDeliveryStore(mongoTemplate);

        List<NotificationDelivery> firstWorker = store.claimTicketBatch(
                NOW, Duration.ofSeconds(120), 2
        );
        List<NotificationDelivery> secondWorker = store.claimTicketBatch(
                NOW, Duration.ofSeconds(120), 2
        );

        assertEquals(List.of(claimed), firstWorker);
        assertTrue(secondWorker.isEmpty());
        verify(mongoTemplate, times(3)).findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(NotificationDelivery.class)
        );
        verify(mongoTemplate, never()).save(any(NotificationDelivery.class));
    }

    @Test
    void deliveryResultUpdateRequiresProcessingStatusAndClaimAttempt() {
        when(mongoTemplate.updateFirst(
                any(Query.class), any(Update.class), eq(NotificationDelivery.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        NotificationDeliveryStore store = new NotificationDeliveryStore(mongoTemplate);
        NotificationDelivery claim = NotificationDelivery.builder()
                .deliveryId("delivery-id")
                .status(NotificationDeliveryStatus.PROCESSING)
                .attemptCount(3)
                .build();

        assertTrue(store.markTicketReceived(
                claim,
                "ticket-id",
                "token-hash",
                NOW.plusSeconds(900),
                NOW
        ));

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).updateFirst(
                queryCaptor.capture(), any(Update.class), eq(NotificationDelivery.class));
        Document query = queryCaptor.getValue().getQueryObject();
        assertNotNull(query.get("$and"));
        assertTrue(query.toString().contains("delivery-id"));
        assertTrue(query.toString().contains("PROCESSING"));
        assertTrue(query.toString().contains("attemptCount"));
        assertTrue(query.toString().contains("3"));
    }
}
