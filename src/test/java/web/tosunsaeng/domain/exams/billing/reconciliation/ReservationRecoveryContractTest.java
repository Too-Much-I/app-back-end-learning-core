package web.tosunsaeng.domain.exams.billing.reconciliation;

import io.micrometer.core.instrument.MeterRegistry;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.JsonSerializer;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionOperations;
import web.tosunsaeng.domain.exams.billing.BillingSagaProperties;
import web.tosunsaeng.domain.exams.billing.BillingReservationClient;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.enums.BillingContinuationReason;
import web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind;
import web.tosunsaeng.global.sentry.SentryEventSanitizer;

import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationRecoveryContractTest {
    @Test void defaultsRemainOffAndOperatingAlertLimitsAreNotInferred() {
        var p = new ReservationReconciliationProperties();
        assertThat(p.isEnabled()).isFalse(); assertThat(p.getConcurrency()).isEqualTo(2);
        assertThat(p.getPoll()).isEqualTo(Duration.ofSeconds(10)); assertThat(p.getBatchSize()).isEqualTo(20);
        assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
        p.setAlertMaxAge(Duration.ofHours(24)); p.setAlertMaxAttempts(200); p.validate();
        p.setLease(Duration.ofSeconds(21)); assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test void disabledStartupAndPollDoNotTouchAnyService() {
        var p = new ReservationReconciliationProperties();
        var mongo = mock(MongoTemplate.class); var billing = mock(BillingSagaProperties.class);
        ObjectProvider<TransactionOperations> tx = mock(ObjectProvider.class); var hub = mock(IHub.class);
        new ReservationReconciliationStartupValidator(p, billing, mongo, tx, hub, mock(ObjectProvider.class)).run(null);
        var store = mock(ReservationRecoveryStore.class); var circuit = mock(ReservationAuthCircuit.class);
        var service = mock(BillingReservationReconciliationService.class); var alerts = mock(ReservationReconciliationAlertReporter.class);
        var metrics = mock(MeterRegistry.class);
        var scheduler = new BillingReservationReconciliationScheduler(p, store, circuit, service, alerts, metrics);
        scheduler.start(); scheduler.poll(); scheduler.stop();
        verifyNoInteractions(mongo, billing, tx, hub, store, circuit, service, alerts, metrics);
    }

    @Test void saturatedExecutorHasNoQueueAndStartsAtMostConfiguredWorkers() throws Exception {
        var p = new ReservationReconciliationProperties(); p.setEnabled(true); p.setPoll(Duration.ofHours(1));
        var store = mock(ReservationRecoveryStore.class); var circuit = mock(ReservationAuthCircuit.class);
        var service = mock(BillingReservationReconciliationService.class); var alerts = mock(ReservationReconciliationAlertReporter.class);
        var entered = new java.util.concurrent.CountDownLatch(2); var release = new java.util.concurrent.CountDownLatch(1);
        when(store.due()).thenReturn(java.util.stream.IntStream.range(0, 20).mapToObj(i -> prepared()).toList());
        doAnswer(call -> { entered.countDown(); release.await(5, java.util.concurrent.TimeUnit.SECONDS); return null; })
                .when(service).run(anyString());
        var scheduler = new BillingReservationReconciliationScheduler(p, store, circuit, service, alerts,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        try {
            scheduler.start(); assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            scheduler.poll();
            verify(service, times(2)).run(anyString());
            var executor = (java.util.concurrent.ThreadPoolExecutor) org.springframework.test.util.ReflectionTestUtils.getField(scheduler, "workers");
            assertThat(executor.getQueue()).isEmpty();
        } finally { release.countDown(); scheduler.stop(); }
    }

    @Test void backoffIsBoundedWithPositiveJitterAndHonorsRetryAfter() {
        for (int i = 0; i < 100; i++) {
            assertThat(ReservationRecoveryStore.retryDelay(1, null)).isBetween(6L, 6L);
            assertThat(ReservationRecoveryStore.retryDelay(2, null)).isBetween(16L, 16L);
            assertThat(ReservationRecoveryStore.retryDelay(3, null)).isBetween(61L, 66L);
            assertThat(ReservationRecoveryStore.retryDelay(4, null)).isBetween(301L, 330L);
            assertThat(ReservationRecoveryStore.retryDelay(200, null)).isBetween(901L, 990L);
            assertThat(ReservationRecoveryStore.retryDelay(1, 1200)).isEqualTo(1200);
        }
    }

    @Test void strictUuidAndStatusSnapshotsRejectChangedIdentity() {
        assertThat(ReservationSnapshotValidator.uuid4("group")).isFalse();
        assertThat(ReservationSnapshotValidator.uuid4("00000000-0000-1000-8000-000000000001")).isFalse();
        assertThat(ReservationSnapshotValidator.uuid4("A0000000-0000-4000-8000-000000000001")).isFalse();
        var op = prepared(); var status = status(op, "different", null, null);
        assertThatThrownBy(() -> ReservationSnapshotValidator.status(op, status)).isInstanceOf(ReservationRecoveryException.class);
    }

    @Test void pastExpiryIsAllowedButConfirmedMustHaveOpenGroupAndTimestamp() {
        var op = prepared(); var snapshot = status(op, op.getSessionId(), null, null);
        ReservationSnapshotValidator.status(op, snapshot);
        op.markReserved(snapshot.reservationId(), snapshot.reservationKind(), snapshot.attemptGroupId(), snapshot.expiresAt(), Instant.now());
        assertThatThrownBy(() -> ReservationSnapshotValidator.confirmed(op, snapshot)).isInstanceOf(ReservationRecoveryException.class);
        var confirmed = new BillingReservationClient.ReservationSnapshot(op.getOperationId(), op.getReservationId(), null,
                BillingReservationClient.ReservationStatus.CONFIRMED, op.getAttemptGroupId(), BillingReservationClient.AttemptGroupStatus.OPEN,
                op.getSessionId(), null, null, Instant.now());
        ReservationSnapshotValidator.confirmed(op, confirmed);
    }

    @Test void phoneSnapshotMustMatchPersistedContinuationAndExpectedGroup() {
        var op = ExamCreationOperation.prepared(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "ex_phone", "mock", 1,
                null, UUID.randomUUID().toString(), "mock", BillingContinuationReason.PHONE_REJOIN, UUID.randomUUID().toString(), Instant.now());
        var good = status(op, op.getSessionId(), BillingContinuationReason.PHONE_REJOIN, op.getContinuationId());
        ReservationSnapshotValidator.status(op, good);
        var bad = status(op, op.getSessionId(), BillingContinuationReason.PHONE_REJOIN, UUID.randomUUID().toString());
        assertThatThrownBy(() -> ReservationSnapshotValidator.status(op, bad)).isInstanceOf(ReservationRecoveryException.class);
    }

    @Test void workerSentryKeepsOnlyIncidentAndFixedReasonWithoutHttpOrPayload() throws Exception {
        String incident = UUID.randomUUID().toString();
        var event = new SentryEvent(); var message = new Message(); message.setMessage("PRIVATE_BILLING_BODY"); event.setMessage(message);
        event.setTag("capture.source", ReservationReconciliationAlertReporter.SOURCE);
        event.setTag("reconciliation.reason", "state_inconsistent");
        event.setTag("http.status_code", "500"); event.setTag("http.method", "POST");
        event.setTag("userId", "PRIVATE_USER"); event.setExtra("payload", "PRIVATE_PAYLOAD");
        event.getContexts().put("baggage", Map.of("value", "PRIVATE_BAGGAGE"));
        event.getContexts().put(ReservationReconciliationAlertReporter.INCIDENT_CONTEXT, Map.of("value", incident));
        event.setFingerprints(List.of("PRIVATE_ID"));
        var sanitizer = new SentryEventSanitizer(); var safe = sanitizer.execute(event, new Hint());
        assertThat(safe.getFingerprints()).containsExactly("learning-core", ReservationReconciliationAlertReporter.SOURCE, "state_inconsistent");
        assertThat(safe.getContexts()).containsOnlyKeys(ReservationReconciliationAlertReporter.INCIDENT_CONTEXT);
        assertThat(safe.getTag("http.status_code")).isNull(); assertThat(safe.getTag("http.method")).isNull();
        StringWriter json = new StringWriter(); new JsonSerializer(new SentryOptions()).serialize(safe, json);
        assertThat(json.toString()).doesNotContain("PRIVATE_").contains(incident);
        sanitizer.execute(safe, new Hint()); // SDK invokes beforeSend after the reporter's pre-sanitization.
        assertThat(safe.getFingerprints()).hasSize(3);
    }

    @Test void earlyWarningHasFixedGroupingAndNoUserIdentifiers() throws Exception {
        var event = new SentryEvent();
        event.setTag("capture.source", ReservationReconciliationAlertReporter.SOURCE);
        event.setTag("reconciliation.reason", ReservationReconciliationAlertReporter.EARLY_REASON);
        event.setTag("operationId", "PRIVATE_OPERATION"); event.setExtra("userId", "PRIVATE_USER");
        event.setTag("http.status_code", "500");
        String incident = UUID.randomUUID().toString();
        event.getContexts().put(ReservationReconciliationAlertReporter.INCIDENT_CONTEXT, Map.of("value", incident));
        new SentryEventSanitizer().execute(event, new Hint());
        assertThat(event.getFingerprints()).containsExactly("learning-core", "billing_reservation_reconcile", "creation_blocked_5m");
        StringWriter json = new StringWriter(); new JsonSerializer(new SentryOptions()).serialize(event, json);
        assertThat(json.toString()).doesNotContain("PRIVATE_").contains(incident);
        assertThat(event.getTag("http.status_code")).isNull();
    }

    @Test void httpEventsDoNotGainWorkerContextOrArbitraryReasonAllowlist() {
        var event = new SentryEvent(); event.setTag("capture.source", "global_exception_advice");
        event.setTag("reconciliation.reason", "state_inconsistent");
        event.getContexts().put(ReservationReconciliationAlertReporter.INCIDENT_CONTEXT, UUID.randomUUID().toString());
        new SentryEventSanitizer().execute(event, new Hint());
        assertThat(event.getTag("reconciliation.reason")).isNull(); assertThat(event.getContexts()).isEmpty();
    }

    private ExamCreationOperation prepared() { return ExamCreationOperation.prepared(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "ex_1", "mock", 1, Instant.now()); }
    private BillingReservationClient.ReservationSnapshot status(ExamCreationOperation op, String session,
            BillingContinuationReason reason, String continuation) {
        return new BillingReservationClient.ReservationSnapshot(op.getOperationId(), UUID.randomUUID().toString(),
                op.getExpectedAttemptGroupId() == null ? BillingReservationKind.INITIAL : BillingReservationKind.REPLACEMENT,
                BillingReservationClient.ReservationStatus.RESERVED,
                op.getExpectedAttemptGroupId() == null ? UUID.randomUUID().toString() : op.getExpectedAttemptGroupId(),
                BillingReservationClient.AttemptGroupStatus.OPEN, session, op.getMockExamId(), reason, continuation, Instant.EPOCH, null);
    }
}
