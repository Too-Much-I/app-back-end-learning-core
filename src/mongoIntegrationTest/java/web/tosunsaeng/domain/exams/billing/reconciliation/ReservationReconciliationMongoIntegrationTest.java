package web.tosunsaeng.domain.exams.billing.reconciliation;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClients;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import web.tosunsaeng.domain.exams.application.BillingExamCreationTransactionService;
import web.tosunsaeng.domain.exams.application.ExamSessionManager;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventProperties;
import web.tosunsaeng.domain.exams.billing.BillingClientException;
import web.tosunsaeng.domain.exams.billing.BillingReservationClient;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind;
import web.tosunsaeng.domain.exams.domain.enums.ExamCreationState;
import web.tosunsaeng.domain.exams.domain.enums.ExamEntitlementState;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.repository.ExamCreationOperationRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationStatus.*;

@Testcontainers
class ReservationReconciliationMongoIntegrationTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");
    private MongoTemplate mongo;
    private com.mongodb.client.MongoClient mongoClient;
    private ExamCreationOperationRepository operations;
    private ExamSessionRepository sessions;
    private BillingReservationClient billing;
    private ReservationReconciliationProperties properties;
    private ReservationOperationExecution execution;
    private ReservationRecoveryStore store;
    private ReservationAuthCircuit circuit;
    private BillingExamCreationTransactionService transactions;
    private TransactionOperations mongoTransactions;
    private BillingReservationReconciliationService service;
    private final MutableClock clock = new MutableClock();

    @BeforeEach void setup() {
        mongoClient = MongoClients.create(MONGO.getReplicaSetUrl());
        var factory = new SimpleMongoClientDatabaseFactory(mongoClient, "reservation-recovery-it");
        mongo = new MongoTemplate(factory);
        mongo.getDb().drop();
        for (String collection : List.of("exam_creation_operations", "exam_sessions", "user_ownership_guards",
                "withdrawn_user_access_denies", ReservationAuthCircuit.COLLECTION)) mongo.createCollection(collection);
        clock.now = Instant.parse("2026-09-08T03:00:00Z");
        var repositories = new MongoRepositoryFactory(mongo);
        operations = repositories.getRepository(ExamCreationOperationRepository.class);
        sessions = repositories.getRepository(ExamSessionRepository.class);
        mongoTransactions = new TransactionTemplate(new MongoTransactionManager(factory));
        properties = new ReservationReconciliationProperties(); properties.setEnabled(true);
        properties.setAlertMaxAge(Duration.ofHours(24)); properties.setAlertMaxAttempts(200);
        execution = new ReservationOperationExecution(mongo, properties, clock);
        store = new ReservationRecoveryStore(mongo, execution, properties);
        circuit = new ReservationAuthCircuit(mongo, execution, properties);
        billing = mock(BillingReservationClient.class);
        var sessionManager = mock(ExamSessionManager.class);
        when(sessionManager.findInProgressSessions(anyString())).thenReturn(List.of());
        var beans = new StaticListableBeanFactory(); beans.addBean("tx", mongoTransactions);
        transactions = new BillingExamCreationTransactionService(operations, sessions, sessionManager,
                beans.getBeanProvider(TransactionOperations.class), mock(AttemptGroupEventProperties.class));
        ReflectionTestUtils.setField(transactions, "execution", execution);
        service = new BillingReservationReconciliationService(execution, store, circuit, properties, transactions,
                billing, mongo, new SimpleMeterRegistry(), beans.getBeanProvider(Tracer.class));
    }

    @org.junit.jupiter.api.AfterEach void closeClient() { if (mongoClient != null) mongoClient.close(); }

    @Test void neverDispatchedPreparedEndsWithoutAnyBillingCall() {
        var op = prepared(); operations.insert(op);
        service.run(op.getCommandId());
        var result = reload(op);
        assertThat(result.getState()).isEqualTo(ExamCreationState.FAILED_TERMINAL);
        assertThat(result.getActiveGuard()).isFalse();
        assertThat(result.getPurgeAt()).isEqualTo(clock.instant().plus(Duration.ofDays(7)));
        verifyNoInteractions(billing);
    }

    @Test void unknown404KeepsCleanupIntentAndGuardAndNeverReserves() {
        var op = prepared(); op.getRecovery().setDispatch(ReservationRecovery.Dispatch.MAY_HAVE_BEEN_SENT); operations.insert(op);
        when(billing.status(anyString(), anyString())).thenThrow(new BillingClientException(BillingClientException.Category.OPERATION_NOT_FOUND, null));
        service.run(op.getCommandId());
        var result = reload(op);
        assertThat(result.getState()).isEqualTo(ExamCreationState.PREPARED);
        assertThat(result.getRecovery().getIntent()).isEqualTo(ReservationRecovery.Intent.CLEANUP_PRECOMMIT);
        assertThat(result.getActiveGuard()).isTrue(); assertThat(result.getPurgeAt()).isNull();
        assertThat(result.getRecovery().getNextAt()).isAfter(clock.instant());
        verify(billing).status(op.getUserId(), op.getOperationId()); verifyNoMoreInteractions(billing);
    }

    @Test void lateReserveIsDiscoveredAndCanceledWithSameIdentity() {
        var op = prepared(); op.getRecovery().setDispatch(ReservationRecovery.Dispatch.MAY_HAVE_BEEN_SENT); operations.insert(op);
        String reservation = UUID.randomUUID().toString(), group = UUID.randomUUID().toString();
        when(billing.status(anyString(), anyString())).thenReturn(new BillingReservationClient.ReservationSnapshot(
                op.getOperationId(), reservation, BillingReservationKind.INITIAL, RESERVED, group,
                BillingReservationClient.AttemptGroupStatus.OPEN, op.getSessionId(), op.getMockExamId(), clock.instant().minusSeconds(10), null));
        when(billing.cancel(op.getOperationId(), reservation, op.getUserId())).thenReturn(new BillingReservationClient.ReservationSnapshot(
                op.getOperationId(), reservation, null, CANCELED, null, null, null, null, null, clock.instant()));
        service.run(op.getCommandId());
        assertThat(reload(op).getState()).isEqualTo(ExamCreationState.CANCELED);
        assertThat(sessions.count()).isZero();
        verify(billing, never()).reserve(anyString(), anyString(), anyString(), anyString());
    }

    @Test void committedReservationConfirmsUsingOriginalCommitTimestamp() {
        var op = committed();
        when(billing.status(anyString(), anyString())).thenReturn(snapshot(op, RESERVED));
        when(billing.confirm(op.getOperationId(), op.getReservationId(), op.getUserId(), op.getSessionId(), op.getSessionCommittedAt()))
                .thenReturn(snapshot(op, CONFIRMED));
        service.run(op.getCommandId());
        assertThat(reload(op).getState()).isEqualTo(ExamCreationState.SUCCEEDED);
        assertThat(sessions.findById(op.getSessionId()).orElseThrow().getStatus()).isEqualTo(ExamSessionStatus.IN_PROGRESS);
        verify(billing).confirm(op.getOperationId(), op.getReservationId(), op.getUserId(), op.getSessionId(), op.getSessionCommittedAt());
        verify(billing, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test void lostConfirmResponseConvergesOnNextStatusWithoutSecondConfirm() {
        var op = committed();
        when(billing.status(anyString(), anyString())).thenReturn(snapshot(op, RESERVED), snapshot(op, CONFIRMED));
        when(billing.confirm(anyString(), anyString(), anyString(), anyString(), any())).thenThrow(
                new BillingClientException(BillingClientException.Category.TEMPORARILY_UNAVAILABLE, 60));
        service.run(op.getCommandId());
        assertThat(reload(op).getRecovery().getNextAt()).isAfterOrEqualTo(clock.instant().plusSeconds(60));
        clock.now = clock.now.plusSeconds(61); service.run(op.getCommandId());
        assertThat(reload(op).getState()).isEqualTo(ExamCreationState.SUCCEEDED);
        verify(billing, times(1)).confirm(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test void remoteExpiryAtomicallyAbandonsOnlyConfirmingSessionAndRetainsLocalEvidence() {
        var op = committed();
        var status = snapshot(op, EXPIRED);
        status = new BillingReservationClient.ReservationSnapshot(status.operationId(), status.reservationId(), status.reservationKind(),
                EXPIRED, status.attemptGroupId(), status.attemptGroupStatus(), status.sessionId(), status.mockExamId(),
                status.expiresAt(), clock.instant().minus(Duration.ofDays(20)));
        when(billing.status(anyString(), anyString())).thenReturn(status);
        service.run(op.getCommandId());
        var result = reload(op);
        assertThat(result.getState()).isEqualTo(ExamCreationState.EXPIRED);
        assertThat(result.getTerminalAt()).isEqualTo(status.terminalAt());
        assertThat(result.getPurgeAt()).isEqualTo(clock.instant().plus(Duration.ofDays(7)));
        assertThat(sessions.findById(op.getSessionId()).orElseThrow().getStatus()).isEqualTo(ExamSessionStatus.ABANDONED);
        verify(billing, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test void contradictoryCurrentGroupAndMissingSessionAreQuarantined() {
        var op = committed(); var good = snapshot(op, CONFIRMED);
        when(billing.status(anyString(), anyString())).thenReturn(new BillingReservationClient.ReservationSnapshot(good.operationId(),
                good.reservationId(), good.reservationKind(), CONFIRMED, good.attemptGroupId(), BillingReservationClient.AttemptGroupStatus.COMPLETED,
                good.sessionId(), good.mockExamId(), good.expiresAt(), good.terminalAt()));
        service.run(op.getCommandId());
        assertThat(reload(op).getRecovery().getStatus()).isEqualTo(ReservationRecovery.Status.NEEDS_REVIEW);
        assertThat(reload(op).getRecovery().getAlertStatus()).isEqualTo("PENDING");
        assertThat(reload(op).getActiveGuard()).isTrue();
        reset(billing);
        var missing = committed(); sessions.deleteById(missing.getSessionId()); service.run(missing.getCommandId());
        assertThat(reload(missing).getRecovery().getStatus()).isEqualTo(ReservationRecovery.Status.NEEDS_REVIEW);
        verifyNoInteractions(billing);
    }

    @Test void staleLeaseCannotWriteAfterAnotherInstanceClaims() {
        var op = reserved();
        var old = execution.claim(op.getCommandId(), false);
        assertThat(execution.claim(op.getCommandId(), true)).isNull();
        clock.now = clock.now.plusSeconds(31);
        var other = new ReservationOperationExecution(mongo, properties, clock);
        var current = other.claim(op.getCommandId(), true); assertThat(current).isNotNull();
        assertThatThrownBy(() -> execution.within(old, () -> transactions.beginCleanup(op.getCommandId())))
                .isInstanceOf(ReservationRecoveryException.class);
        execution.release(old);
        assertThat(reload(op).getRecovery().getStatus()).isEqualTo(ReservationRecovery.Status.IN_FLIGHT);
        other.release(current);
    }

    @Test void cleanupFenceAndSessionCommitCannotBothWin() {
        var op = reserved();
        execution.http(op.getCommandId(), () -> transactions.beginCleanup(op.getCommandId()));
        assertThatThrownBy(() -> execution.http(op.getCommandId(), () -> transactions.commitReservedSession(op.getCommandId(), clock.instant(), ZoneOffset.UTC)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(sessions.count()).isZero();
        var other = reserved();
        execution.http(other.getCommandId(), () -> transactions.commitReservedSession(other.getCommandId(), clock.instant(), ZoneOffset.UTC));
        assertThatThrownBy(() -> execution.http(other.getCommandId(), () -> transactions.beginCleanup(other.getCommandId())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reload(other).getState()).isEqualTo(ExamCreationState.SESSION_COMMITTED);
    }

    @Test void transactionFailureRollsBackInsertedSessionAndOperationTogether() {
        var op = reserved();
        var failing = mock(ExamCreationOperationRepository.class, withSettings().defaultAnswer(org.mockito.AdditionalAnswers.delegatesTo(operations)));
        doThrow(new IllegalStateException("injected after Session insert")).when(failing).save(any(ExamCreationOperation.class));
        ReflectionTestUtils.setField(transactions, "operationRepository", failing);
        assertThatThrownBy(() -> execution.http(op.getCommandId(), () -> transactions.commitReservedSession(op.getCommandId(), clock.instant(), ZoneOffset.UTC)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(sessions.count()).isZero(); assertThat(reload(op).getState()).isEqualTo(ExamCreationState.RESERVED);
    }

    @Test void lostCleanupCommitAckNeverCancelsBeforeFreshPass() {
        var op = reserved(); injectUnknownCommitOnce();
        when(billing.status(anyString(), anyString())).thenReturn(snapshot(op, RESERVED));
        when(billing.cancel(anyString(), anyString(), anyString())).thenReturn(snapshot(op, CANCELED));
        service.run(op.getCommandId()); verifyNoInteractions(billing);
        assertThat(reload(op).getRecovery().getIntent()).isEqualTo(ReservationRecovery.Intent.CLEANUP_PRECOMMIT);
        clock.now = clock.now.plusSeconds(20); service.run(op.getCommandId());
        assertThat(reload(op).getState()).isEqualTo(ExamCreationState.CANCELED);
    }

    @Test void lostFinalCommitAckUsesMajorityEvidenceAndDoesNotCancel() {
        var op = committed(); injectUnknownCommitOnce();
        when(billing.status(anyString(), anyString())).thenReturn(snapshot(op, CONFIRMED));
        service.run(op.getCommandId());
        assertThat(reload(op).getState()).isEqualTo(ExamCreationState.SUCCEEDED);
        assertThat(sessions.findById(op.getSessionId()).orElseThrow().getEntitlementState()).isEqualTo(ExamEntitlementState.CONFIRMED);
        verify(billing, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test void authCircuitIsSharedSingleProbeAndDoesNotEmitPerOperationAlerts() {
        var op = committed();
        when(billing.status(anyString(), anyString())).thenThrow(new BillingClientException(BillingClientException.Category.AUTH_FAILURE, null));
        service.run(op.getCommandId());
        assertThat(circuit.blocked()).isTrue();
        assertThat(reload(op).getRecovery().getStatus()).isEqualTo(ReservationRecovery.Status.BLOCKED_AUTH);
        assertThat(reload(op).getRecovery().getAlertIncidentId()).isNull();
        assertThat(circuit.claimProbe()).isNull();
        clock.now = clock.now.plus(Duration.ofMinutes(15));
        String probe = circuit.claimProbe(); assertThat(probe).isNotNull(); assertThat(circuit.claimProbe()).isNull();
        circuit.recovered("stale"); assertThat(circuit.blocked()).isTrue();
        circuit.recovered(probe); store.wakeAuthBlocked(); assertThat(circuit.blocked()).isFalse();
        assertThat(reload(op).getRecovery().getStatus()).isEqualTo(ReservationRecovery.Status.READY);
    }

    @Test void budgetAndOwnerDenyPreserveGuardWithoutBillingCalls() {
        var op = reserved();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(op.getCommandId())), new Update().set("recovery.attempts", 200), ExamCreationOperation.class);
        service.run(op.getCommandId());
        assertThat(reload(op).getRecovery().getFailureCode()).isEqualTo("retry_exhausted");
        assertThat(reload(op).getActiveGuard()).isTrue();
        var denied = reserved();
        mongo.getCollection("user_ownership_guards").insertOne(new Document("_id", denied.getUserId()).append("state", "MERGED"));
        service.run(denied.getCommandId());
        assertThat(reload(denied).getRecovery().getStatus()).isEqualTo(ReservationRecovery.Status.BLOCKED_OWNER);
        verifyNoInteractions(billing);
    }

    @Test void recoveryAgeLimitQuarantinesWithoutClearingBusinessGuard() {
        var op = reserved();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(op.getCommandId())),
                new Update().set("recovery.firstAt", clock.instant().minus(Duration.ofHours(24))), ExamCreationOperation.class);
        service.run(op.getCommandId());
        assertThat(reload(op).getRecovery().getFailureCode()).isEqualTo("retry_exhausted");
        assertThat(reload(op).getActiveGuard()).isTrue(); verifyNoInteractions(billing);
    }

    @Test void disabledWorkerDoesNotLatchHttpAuthCircuitWithoutAProbeScheduler() {
        properties.setEnabled(false); circuit.blockFromHttp();
        assertThat(circuit.blocked()).isFalse();
        assertThat(mongo.getCollection(ReservationAuthCircuit.COLLECTION).countDocuments()).isZero();
    }

    @Test void offAndLegacyHaveNoAutomaticExecution() {
        var op = reserved(); properties.setEnabled(false); service.run(op.getCommandId());
        assertThat(reload(op).getRecovery().getAttempts()).isZero();
        properties.setEnabled(true);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(op.getCommandId())), new Update().unset("recovery"), ExamCreationOperation.class);
        service.run(op.getCommandId()); assertThat(store.due()).isEmpty(); verifyNoInteractions(billing);
        execution.http(op.getCommandId(), () -> true);
        assertThat(reload(op).getRecovery().getSchemaVersion()).isZero();
        assertThat(reload(op).getRecovery().getDispatch()).isEqualTo(ReservationRecovery.Dispatch.MAY_HAVE_BEEN_SENT);
    }

    @Test void simultaneousHttpAndWorkerClaimHaveOnlyOneWinner() throws Exception {
        var op = reserved(); var other = new ReservationOperationExecution(mongo, properties, clock);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return execution.claim(op.getCommandId(), false); });
            var second = pool.submit(() -> { start.await(); return other.claim(op.getCommandId(), true); });
            start.countDown();
            var a = first.get(5, TimeUnit.SECONDS); var b = second.get(5, TimeUnit.SECONDS);
            assertThat((a == null ? 0 : 1) + (b == null ? 0 : 1)).isEqualTo(1);
        }
    }

    @Test void actualMongoWriteConflictSerializesCleanupAndSessionCommit() throws Exception {
        var op = reserved(); var lease = execution.claim(op.getCommandId(), false);
        var fenced = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var commit = pool.submit(() -> execution.within(lease, () -> mongoTransactions.execute(status -> {
                execution.fence(op.getCommandId(), null); fenced.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test barrier timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("test interrupted"); }
                return transactions.commitReservedSession(op.getCommandId(), clock.instant(), ZoneOffset.UTC);
            })));
            assertThat(fenced.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                assertThatThrownBy(() -> execution.within(lease, () -> transactions.beginCleanup(op.getCommandId())))
                        .isInstanceOf(RuntimeException.class);
            } finally { release.countDown(); }
            commit.get(5, TimeUnit.SECONDS);
        }
        assertThat(reload(op).getState()).isEqualTo(ExamCreationState.SESSION_COMMITTED);
        assertThat(reload(op).getRecovery().getIntent()).isEqualTo(ReservationRecovery.Intent.CONTINUE);
        assertThat(sessions.count()).isEqualTo(1);
    }

    @Test void pendingAlertSurvivesSdkFailureAndIsNotReportedEveryPoll() {
        var op = reserved();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(op.getCommandId())), new Update().set("recovery.attempts", 200), ExamCreationOperation.class);
        service.run(op.getCommandId());
        var hub = mock(io.sentry.IHub.class); when(hub.isEnabled()).thenReturn(true);
        when(hub.captureEvent(any(io.sentry.SentryEvent.class), any(io.sentry.Hint.class), any(io.sentry.ScopeCallback.class)))
                .thenReturn(io.sentry.protocol.SentryId.EMPTY_ID, new io.sentry.protocol.SentryId());
        var reporter = new ReservationReconciliationAlertReporter(mongo, execution, properties, hub,
                new SimpleMeterRegistry(), new web.tosunsaeng.global.sentry.SentryEventSanitizer());
        String incident = reload(op).getRecovery().getAlertIncidentId();
        reporter.poll(); reporter.poll();
        assertThat(reload(op).getRecovery().getAlertStatus()).isEqualTo("PENDING");
        assertThat(reload(op).getRecovery().getAlertAttempts()).isEqualTo(1);
        clock.now = clock.now.plusSeconds(20); reporter.poll(); reporter.poll();
        assertThat(reload(op).getRecovery().getAlertStatus()).isEqualTo("SDK_ACCEPTED");
        assertThat(reload(op).getRecovery().getAlertIncidentId()).isEqualTo(incident);
        assertThat(reload(op).getActiveGuard()).isTrue();
        verify(hub, times(2)).captureEvent(any(io.sentry.SentryEvent.class), any(io.sentry.Hint.class), any(io.sentry.ScopeCallback.class));
    }

    @Test void pendingAlertExhaustionRetainsUnsubmittedEvidence() {
        var op = reserved();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(op.getCommandId())), new Update().set("recovery.attempts", 200), ExamCreationOperation.class);
        service.run(op.getCommandId()); clock.now = clock.now.plus(Duration.ofDays(2));
        var hub = mock(io.sentry.IHub.class);
        new ReservationReconciliationAlertReporter(mongo, execution, properties, hub, new SimpleMeterRegistry(),
                new web.tosunsaeng.global.sentry.SentryEventSanitizer()).poll();
        assertThat(reload(op).getRecovery().getAlertStatus()).isEqualTo("UNSUBMITTED");
        assertThat(reload(op).getRecovery().getAlertIncidentId()).isNotNull();
        assertThat(reload(op).getPurgeAt()).isNull(); verifyNoInteractions(hub);
    }

    @Test void earlyWarningStartsAtFiveMinutesOnceAndDoesNotChangeRecoverySchedule() {
        var op = reserved(); // created three minutes ago
        Instant nextAt = reload(op).getRecovery().getNextAt();
        var hub = acceptingHub(); var reporter = alertReporter(hub);
        clock.now = clock.now.plusSeconds(119); reporter.poll();
        assertThat(reload(op).getRecovery().getEarlyAlert()).isNull();
        verifyNoInteractions(hub);
        clock.now = clock.now.plusSeconds(1); reporter.poll();
        var result = reload(op);
        assertThat(result.getRecovery().getEarlyAlert().getAlertStatus()).isEqualTo("SDK_ACCEPTED");
        assertThat(result.getRecovery().getEarlyAlert().getAlertReason()).isEqualTo("creation_blocked_5m");
        assertThat(result.getActiveGuard()).isTrue(); assertThat(result.getState()).isEqualTo(ExamCreationState.RESERVED);
        assertThat(result.getRecovery().getAttempts()).isZero(); assertThat(result.getRecovery().getNextAt()).isEqualTo(nextAt);
        String incident = result.getRecovery().getEarlyAlert().getAlertIncidentId();
        reporter.poll(); alertReporter(hub).poll();
        assertThat(reload(op).getRecovery().getEarlyAlert().getAlertIncidentId()).isEqualTo(incident);
        verify(hub, times(1)).captureEvent(any(io.sentry.SentryEvent.class), any(io.sentry.Hint.class), any(io.sentry.ScopeCallback.class));
        verifyNoInteractions(billing);
    }

    @Test void concurrentEarlyWarningPollersCreateOneIncident() throws Exception {
        var op = reserved(); clock.now = clock.now.plusSeconds(120);
        var hub = acceptingHub(); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); alertReporter(hub).enqueueEarlyWarnings(); return true; });
            var second = pool.submit(() -> { start.await(); alertReporter(hub).enqueueEarlyWarnings(); return true; });
            start.countDown(); first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS);
        }
        String incident = reload(op).getRecovery().getEarlyAlert().getAlertIncidentId();
        assertThat(incident).isNotBlank();
        alertReporter(hub).poll(); alertReporter(hub).poll();
        assertThat(reload(op).getRecovery().getEarlyAlert().getAlertIncidentId()).isEqualTo(incident);
        verify(hub, times(1)).captureEvent(any(io.sentry.SentryEvent.class), any(io.sentry.Hint.class), any(io.sentry.ScopeCallback.class));
    }

    @Test void earlyWarningPendingSurvivesRestartAndLaterQuarantineWithoutOverwriting() {
        var op = reserved(); clock.now = clock.now.plusSeconds(120);
        var hub = acceptingHub(); var reporter = alertReporter(hub);
        reporter.enqueueEarlyWarnings(); // simulate crash before SDK capture
        String earlyIncident = reload(op).getRecovery().getEarlyAlert().getAlertIncidentId();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(op.getCommandId())), new Update().set("recovery.attempts", 200), ExamCreationOperation.class);
        service.run(op.getCommandId());
        var quarantined = reload(op).getRecovery();
        assertThat(quarantined.getStatus()).isEqualTo(ReservationRecovery.Status.NEEDS_REVIEW);
        assertThat(quarantined.getAlertIncidentId()).isNotEqualTo(earlyIncident);
        assertThat(quarantined.getEarlyAlert().getAlertIncidentId()).isEqualTo(earlyIncident);
        alertReporter(hub).poll();
        assertThat(reload(op).getRecovery().getAlertStatus()).isEqualTo("SDK_ACCEPTED");
        assertThat(reload(op).getRecovery().getEarlyAlert().getAlertStatus()).isEqualTo("SDK_ACCEPTED");
        verify(hub, times(2)).captureEvent(any(io.sentry.SentryEvent.class), any(io.sentry.Hint.class), any(io.sentry.ScopeCallback.class));
    }

    @Test void earlyWarningSdkFailureRetriesSameIncidentAndExhaustionRetainsEvidence() {
        var op = reserved(); clock.now = clock.now.plusSeconds(120);
        var hub = acceptingHub();
        when(hub.captureEvent(any(io.sentry.SentryEvent.class), any(io.sentry.Hint.class), any(io.sentry.ScopeCallback.class)))
                .thenReturn(io.sentry.protocol.SentryId.EMPTY_ID);
        var reporter = alertReporter(hub); reporter.poll();
        String incident = reload(op).getRecovery().getEarlyAlert().getAlertIncidentId();
        reporter.poll();
        assertThat(reload(op).getRecovery().getEarlyAlert().getAlertAttempts()).isEqualTo(1);
        clock.now = clock.now.plusSeconds(10); alertReporter(hub).poll();
        assertThat(reload(op).getRecovery().getEarlyAlert().getAlertAttempts()).isEqualTo(2);
        assertThat(reload(op).getRecovery().getEarlyAlert().getAlertIncidentId()).isEqualTo(incident);
        clock.now = clock.now.plus(Duration.ofDays(2)); reporter.poll();
        assertThat(reload(op).getRecovery().getEarlyAlert().getAlertStatus()).isEqualTo("UNSUBMITTED");
        assertThat(reload(op).getActiveGuard()).isTrue(); assertThat(reload(op).getPurgeAt()).isNull();
    }

    @Test void earlyWarningSkipsResolvedLegacyOffAndExistingQuarantine() {
        var completed = committed();
        execution.http(completed.getCommandId(), () -> transactions.finalizeConfirmed(completed.getCommandId(), clock.instant()));
        var legacy = reserved(); mongo.updateFirst(Query.query(Criteria.where("_id").is(legacy.getCommandId())),
                new Update().set("recovery.schemaVersion", 0), ExamCreationOperation.class);
        var quarantined = reserved();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(quarantined.getCommandId())),
                new Update().set("recovery.attempts", 200), ExamCreationOperation.class);
        service.run(quarantined.getCommandId());
        var pending = reserved(); clock.now = clock.now.plusSeconds(120);
        var hub = acceptingHub(); var reporter = alertReporter(hub);
        properties.setEnabled(false); reporter.poll();
        assertThat(reload(pending).getRecovery().getEarlyAlert()).isNull();
        properties.setEnabled(true); reporter.poll();
        assertThat(reload(completed).getRecovery().getEarlyAlert()).isNull();
        assertThat(reload(legacy).getRecovery().getEarlyAlert()).isNull();
        assertThat(reload(quarantined).getRecovery().getEarlyAlert()).isNull();
        assertThat(reload(quarantined).getRecovery().getAlertStatus()).isEqualTo("SDK_ACCEPTED");
        assertThat(reload(pending).getRecovery().getEarlyAlert().getAlertStatus()).isEqualTo("SDK_ACCEPTED");
        verify(hub, times(2)).captureEvent(any(io.sentry.SentryEvent.class), any(io.sentry.Hint.class), any(io.sentry.ScopeCallback.class));
    }

    @Test void globalAuthAlertDoesNotFanOutIntoEarlyWarnings() {
        var op = reserved(); clock.now = clock.now.plusSeconds(120); circuit.block();
        var hub = acceptingHub(); alertReporter(hub).poll();
        assertThat(reload(op).getRecovery().getEarlyAlert()).isNull();
        verify(hub, times(1)).captureEvent(any(io.sentry.SentryEvent.class), any(io.sentry.Hint.class), any(io.sentry.ScopeCallback.class));
    }

    @Test void earlyWarningEnqueueIsBoundedAndPreservesClaimedOperation() {
        properties.setBatchSize(2);
        var first = reserved(); var second = reserved(); var third = reserved(); var fourth = reserved();
        clock.now = clock.now.plusSeconds(120);
        var lease = execution.claim(first.getCommandId(), false);
        alertReporter(acceptingHub()).enqueueEarlyWarnings();
        assertThat(reload(first).getRecovery().getEarlyAlert()).isNull();
        assertThat(java.util.stream.Stream.of(second, third, fourth)
                .filter(op -> reload(op).getRecovery().getEarlyAlert() != null).count()).isEqualTo(2);
        execution.release(lease); alertReporter(acceptingHub()).enqueueEarlyWarnings();
        assertThat(reload(first).getRecovery().getEarlyAlert()).isNotNull();
        assertThat(reload(second).getRecovery().getEarlyAlert()).isNotNull();
        assertThat(reload(third).getRecovery().getEarlyAlert()).isNotNull();
        assertThat(reload(fourth).getRecovery().getEarlyAlert()).isNotNull();
    }

    private io.sentry.IHub acceptingHub() {
        var hub = mock(io.sentry.IHub.class); when(hub.isEnabled()).thenReturn(true);
        when(hub.captureEvent(any(io.sentry.SentryEvent.class), any(io.sentry.Hint.class), any(io.sentry.ScopeCallback.class)))
                .thenReturn(new io.sentry.protocol.SentryId());
        return hub;
    }

    private ReservationReconciliationAlertReporter alertReporter(io.sentry.IHub hub) {
        return new ReservationReconciliationAlertReporter(mongo, execution, properties, hub, new SimpleMeterRegistry(),
                new web.tosunsaeng.global.sentry.SentryEventSanitizer());
    }

    @Test void actualHttpSagaUsesSharedFenceAndSameKeyReplay() {
        var op = prepared(); operations.insert(op);
        String reservation = UUID.randomUUID().toString(), group = UUID.randomUUID().toString();
        when(billing.reserve(op.getOperationId(), op.getUserId(), op.getSessionId(), op.getMockExamId()))
                .thenReturn(new BillingReservationClient.ReservationSnapshot(op.getOperationId(), reservation, BillingReservationKind.INITIAL,
                        RESERVED, group, BillingReservationClient.AttemptGroupStatus.OPEN, op.getSessionId(), op.getMockExamId(), clock.instant().plusSeconds(300), null));
        when(billing.confirm(eq(op.getOperationId()), eq(reservation), eq(op.getUserId()), eq(op.getSessionId()), any()))
                .thenReturn(new BillingReservationClient.ReservationSnapshot(op.getOperationId(), reservation, null,
                        CONFIRMED, group, BillingReservationClient.AttemptGroupStatus.OPEN, op.getSessionId(), null, null, clock.instant()));
        var saga = httpSaga();
        assertThat(saga.start(op.getUserId(), op.getOperationId()).session().getExamId()).isEqualTo(op.getSessionId());
        assertThat(saga.start(op.getUserId(), op.getOperationId()).session().getExamId()).isEqualTo(op.getSessionId());
        assertThat(sessions.count()).isEqualTo(1);
        assertThat(reload(op).getRecovery().getStatus()).isEqualTo(ReservationRecovery.Status.DONE);
        verify(billing, times(1)).reserve(anyString(), anyString(), anyString(), anyString());
        verify(billing, times(1)).confirm(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test void httpReplayAfterCleanupIntentCannotReserveOrCommit() {
        var op = prepared(); operations.insert(op);
        execution.http(op.getCommandId(), () -> transactions.beginCleanup(op.getCommandId()));
        assertThatThrownBy(() -> httpSaga().start(op.getUserId(), op.getOperationId()))
                .isInstanceOf(web.tosunsaeng.domain.exams.exception.ExamsException.class);
        verifyNoInteractions(billing); assertThat(sessions.count()).isZero();
    }

    @Test void workerPassCannotStartThirdRemoteCall() {
        var op = reserved(); var lease = execution.claim(op.getCommandId(), true);
        execution.within(lease, () -> {
            execution.beforeRemote(op.getCommandId()); execution.beforeRemote(op.getCommandId());
            assertThatThrownBy(() -> execution.beforeRemote(op.getCommandId())).isInstanceOf(ReservationRecoveryException.class)
                    .extracting(e -> ((ReservationRecoveryException) e).reason()).isEqualTo(ReservationRecoveryException.Reason.BUDGET_EXHAUSTED);
            return true;
        });
    }

    private web.tosunsaeng.domain.exams.application.BillingExamCreationSaga httpSaga() {
        var config = new web.tosunsaeng.domain.exams.billing.BillingSagaProperties(); config.setCreationSagaEnabled(true);
        var saga = new web.tosunsaeng.domain.exams.application.BillingExamCreationSaga(config, operations, sessions,
                mock(ExamSessionManager.class), mock(web.tosunsaeng.domain.exams.application.MockExamCatalogService.class), billing, transactions, clock);
        ReflectionTestUtils.setField(saga, "execution", execution); ReflectionTestUtils.setField(saga, "authCircuit", circuit);
        return saga;
    }

    private ExamCreationOperation prepared() {
        return ExamCreationOperation.prepared(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "ex_" + UUID.randomUUID(),
                "mock_exam_001", 1, clock.instant().minusSeconds(180));
    }
    private ExamCreationOperation reserved() {
        var op = prepared(); op.markReserved(UUID.randomUUID().toString(), BillingReservationKind.INITIAL, UUID.randomUUID().toString(),
                clock.instant().plusSeconds(120), clock.instant().minusSeconds(180)); return operations.insert(op);
    }
    private ExamCreationOperation committed() {
        var op = reserved();
        execution.http(op.getCommandId(), () -> transactions.commitReservedSession(op.getCommandId(), clock.instant().minusSeconds(150), ZoneOffset.UTC));
        return reload(op);
    }
    private ExamCreationOperation reload(ExamCreationOperation op) { return operations.findById(op.getCommandId()).orElseThrow(); }
    private BillingReservationClient.ReservationSnapshot snapshot(ExamCreationOperation op, BillingReservationClient.ReservationStatus state) {
        return new BillingReservationClient.ReservationSnapshot(op.getOperationId(), op.getReservationId(), op.getReservationKind(), state,
                op.getAttemptGroupId(), BillingReservationClient.AttemptGroupStatus.OPEN, op.getSessionId(), op.getMockExamId(),
                op.getReservationExpiresAt(), state == RESERVED ? null : clock.instant());
    }
    private void injectUnknownCommitOnce() {
        AtomicBoolean once = new AtomicBoolean();
        var wrapped = new TransactionOperations() {
            public <T> T execute(TransactionCallback<T> action) {
                T result = mongoTransactions.execute(action);
                if (once.compareAndSet(false, true)) {
                    MongoException lostAck = new MongoException(91, "injected lost commit acknowledgement");
                    lostAck.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
                    throw new TransactionSystemException("injected", lostAck);
                }
                return result;
            }
        };
        var beans = new StaticListableBeanFactory(); beans.addBean("tx", wrapped);
        ReflectionTestUtils.setField(transactions, "transactionOperationsProvider", beans.getBeanProvider(TransactionOperations.class));
    }
    static class MutableClock extends Clock {
        Instant now;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
