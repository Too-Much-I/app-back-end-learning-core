package web.tosunsaeng.domain.notification;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Clock;

public class DailyReminderWorker {
    private final NotificationStore store;
    private final NotificationTransactions tx;
    private final NotificationProperties properties;
    private final PushGateway gateway;
    private final Clock clock;
    private final MeterRegistry meters;
    private String cursor;
    public DailyReminderWorker(NotificationStore store, NotificationTransactions tx, NotificationProperties properties,
                               PushGateway gateway, Clock clock, MeterRegistry meters) {
        this.store = store; this.tx = tx; this.properties = properties; this.gateway = gateway; this.clock = clock; this.meters = meters;
    }
    @Scheduled(fixedDelayString = "${app.notifications.poll-ms:1000}")
    public synchronized void tick() {
        if (!properties.isSendingEnabled() && !properties.isApiEnabled()) return;
        try {
            var now = clock.instant();
            store.maintain(now, properties.getBatchSize());
            if (!properties.isSendingEnabled()) return;
            if (!ReminderPolicy.inWindow(now) || properties.getTrackingReadyAt() == null
                    || properties.getTrackingReadyAt().isAfter(ReminderPolicy.start(ReminderPolicy.day(now))) || store.paused(now)) { cursor = null; return; }
            var devices = store.candidates(cursor, properties.getBatchSize());
            if (devices.isEmpty()) { cursor = null; return; }
            for (var device : devices) {
                cursor = device.getString("_id");
                if (!ReminderPolicy.inWindow(clock.instant()) || store.paused(clock.instant())) break;
                try { process(cursor); }
                catch (RuntimeException failure) { count("LOCAL_FAILURE"); } // Do not log provider/request/DB exception payloads.
            }
        } catch (RuntimeException failure) { count("SWEEP_FAILURE"); }
    }
    void process(String installation) {
        if (!store.eligible(store.device(installation), clock.instant(), properties.getTrackingReadyAt())) return;
        if (properties.isDryRun()) { count("DRY_RUN_ELIGIBLE"); return; }
        try { gateway.prepare(); }
        catch (PushGateway.PreparationFailure failure) {
            store.pause(failure.result, clock.instant()); count(failure.result.authFailure() ? "AUTH_BLOCKED" : "PREPARATION_FAILED"); return;
        }
        // Unknown commit throws: do not send, nor interpret it as a missing marker.
        var claim = tx.run(() -> store.claim(installation, clock.instant(), properties.getTrackingReadyAt()));
        if (claim == null) return;
        PushGateway.Result result;
        try {
            result = store.stillEligible(claim, clock.instant(), properties.getTrackingReadyAt())
                    ? gateway.send(claim.token, NotificationStore.hash(claim.id), ReminderPolicy.end(ReminderPolicy.day(clock.instant())))
                    : PushGateway.Result.skipped();
        } catch (RuntimeException failure) { result = PushGateway.Result.unknown(); }
        // Once claimed, no exception path can make another HTTP attempt for this device/day.
        store.pause(result, clock.instant());
        store.finish(claim, result, clock.instant());
        count(result.authFailure() ? "AUTH_BLOCKED" : result.status().name());
    }
    private void count(String outcome) { meters.counter("notification.daily_reminder", "outcome", outcome).increment(); }
}
