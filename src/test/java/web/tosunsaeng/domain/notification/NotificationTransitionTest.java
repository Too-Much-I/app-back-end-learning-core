package web.tosunsaeng.domain.notification;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardService;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationTransitionTest {
    final Instant now = Instant.parse("2026-10-08T12:00:00Z");
    final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @Test void readinessDuringTodayAllowsDryRunButFutureOrMissingReadinessDoesNot() {
        for (Instant ready : Arrays.asList(now.minusSeconds(60), now, now.plusSeconds(1), null)) {
            var store = mock(NotificationStore.class);
            var properties = new NotificationProperties();
            properties.setSendingEnabled(true); properties.setApiEnabled(true);
            properties.setTrackingEnabled(true); properties.setTrackingReadyAt(ready);
            var device = new Document("_id", "device");
            when(store.candidates(null, 100)).thenReturn(List.of(device));
            when(store.device("device")).thenReturn(device);
            when(store.eligible(device, now, ready)).thenReturn(true);
            var meters = new SimpleMeterRegistry();
            var gateway = mock(PushGateway.class);
            new DailyReminderWorker(store, mock(NotificationTransactions.class), properties, gateway, clock, meters).tick();
            if (ready != null && !ready.isAfter(now)) {
                assertThat(meters.get("notification.daily_reminder").tag("outcome", "DRY_RUN_ELIGIBLE").counter().count()).isEqualTo(1);
            } else verify(store, never()).candidates(any(), anyInt());
            verifyNoInteractions(gateway);
        }
    }

    @Test void missingHistoricalCompletionDoesNotTriggerAnUnboundedExamExclusion() {
        var mongo = mock(MongoTemplate.class);
        var store = new NotificationStore(mongo, mock(UserOwnershipGuardService.class), clock);
        // Only the bounded, known-completion query is allowed; an old unknown-exam query fails this test.
        when(mongo.exists(any(Query.class), eq("exam_sessions"))).thenAnswer(call -> {
            Document query = ((Query) call.getArgument(0)).getQueryObject();
            assertThat(query.get("submissionCompletedAt")).isInstanceOf(Document.class);
            assertThat((Document) query.get("submissionCompletedAt"))
                    .containsEntry("$gte", Date.from(ReminderPolicy.start(ReminderPolicy.day(now))));
            return false;
        });
        assertThat(store.eligible(device(), now, now)).isTrue();
        verify(mongo, times(1)).exists(any(Query.class), eq("exam_sessions"));
    }

    @Test void knownCompletionSuppressionWithdrawalAndDeletionStillExclude() {
        for (String collection : List.of("exam_sessions", NotificationStore.SUPPRESSIONS,
                "withdrawn_user_access_denies", "learning_record_deletion_operations")) {
            var mongo = mock(MongoTemplate.class);
            when(mongo.exists(any(Query.class), eq(collection))).thenReturn(true);
            var store = new NotificationStore(mongo, mock(UserOwnershipGuardService.class), clock);
            assertThat(store.eligible(device(), now, now)).as(collection).isFalse();
        }
    }

    @Test void inactiveGuestDeniedAndStaleDevicesStillExclude() {
        var store = new NotificationStore(mock(MongoTemplate.class), mock(UserOwnershipGuardService.class), clock);
        for (Document d : List.of(device().append("active", false), device().append("accountType", "GUEST"),
                device().append("permission", "DENIED"), device().append("permissionObservedAt", Date.from(now.minusSeconds(31 * 86400)))))
            assertThat(store.eligible(d, now, now)).isFalse();
    }

    @Test void existingDailyDeliveryStillPreventsClaim() {
        var mongo = mock(MongoTemplate.class);
        when(mongo.findById("device", Document.class, NotificationStore.DEVICES)).thenReturn(device().append("version", 1L));
        when(mongo.exists(any(Query.class), eq(NotificationStore.DELIVERIES))).thenReturn(true);
        var store = new NotificationStore(mongo, mock(UserOwnershipGuardService.class), clock);
        assertThat(store.claim("device", now, now)).isNull();
        verify(mongo, never()).insert(any(Document.class), eq(NotificationStore.DELIVERIES));
    }

    Document device() {
        return new Document("_id", "device").append("userId", "fixture-owner").append("active", true)
                .append("accountType", "MEMBER").append("permission", "AUTHORIZED")
                .append("pushToken", "fixture-only").append("permissionObservedAt", Date.from(now));
    }
}
