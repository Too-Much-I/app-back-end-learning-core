package web.tosunsaeng.domain.notification;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

public class NotificationStore {
    public static final String DEVICES = "notification_devices";
    public static final String DELIVERIES = "daily_exam_reminder_deliveries";
    public static final String SUPPRESSIONS = "daily_reminder_suppressions";
    public static final String CONTROL = "notification_control";
    final MongoTemplate mongo;
    private final UserOwnershipGuardService guards;
    private final Clock clock;
    public NotificationStore(MongoTemplate mongo, UserOwnershipGuardService guards, Clock clock) {
        this.mongo = mongo; this.guards = guards; this.clock = clock;
    }
    public void touchOwner(String owner) {
        guards.touchActive(owner, clock.instant());
        if (blocked(owner)) throw new NotificationFailure(403);
    }
    boolean blocked(String owner) {
        Document guard = mongo.findById(owner, Document.class, "user_ownership_guards");
        return (guard != null && !"ACTIVE".equals(guard.getString("state")))
                || mongo.exists(Query.query(Criteria.where("_id").is(owner).and("blockedUntil").gt(Date.from(clock.instant()))), "withdrawn_user_access_denies");
    }
    public void register(String owner, String accountType, String id, String secret, String platform, String token,
                         String permission, Instant observed) {
        validate(id, secret, platform, token, permission, observed, clock.instant());
        touchOwner(owner);
        rateLimit(owner);
        Document old = mongo.findById(id, Document.class, DEVICES);
        if (old != null && !hash(secret).equals(old.getString("installationProof"))) throw new NotificationFailure(409);
        if (old != null && old.getDate("permissionObservedAt") != null
                && observed.isBefore(old.getDate("permissionObservedAt").toInstant())) throw new NotificationFailure(409);
        if ((old == null || !Boolean.TRUE.equals(old.getBoolean("active")) || !owner.equals(old.getString("userId")))
                && mongo.count(Query.query(Criteria.where("userId").is(owner).and("active").is(true)), DEVICES) >= 20)
            throw new NotificationFailure(429);
        String tokenHash = hash(token);
        if (mongo.exists(Query.query(Criteria.where("tokenHash").is(tokenHash).and("_id").ne(id)), DEVICES)) throw new NotificationFailure(409);
        // A preserved per-installation secret permits an authenticated account switch, not an arbitrary UUID claim.
        mongo.upsert(Query.query(Criteria.where("_id").is(id)), new Update()
                .setOnInsert("installationProof", hash(secret)).set("userId", owner).set("accountType", accountType)
                .set("platform", platform).set("pushToken", token).set("tokenHash", tokenHash)
                .set("permission", permission).set("permissionObservedAt", Date.from(observed))
                .set("active", true).set("lastSeenAt", Date.from(clock.instant())).inc("version", 1L), DEVICES);
    }
    public void unregister(String owner, String id, String secret) {
        uuid(id); proof(secret); touchOwner(owner);
        Document device = mongo.findById(id, Document.class, DEVICES);
        if (device == null) return;
        if (!hash(secret).equals(device.getString("installationProof"))) throw new NotificationFailure(403);
        if (!Boolean.TRUE.equals(device.getBoolean("active"))) return;
        if (!owner.equals(device.getString("userId"))) throw new NotificationFailure(403);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), revoke(), DEVICES);
    }
    private void rateLimit(String owner) {
        long minute = clock.instant().getEpochSecond() / 60;
        String id = "registration:" + owner + ":" + minute;
        Document bucket = mongo.findById(id, Document.class, CONTROL);
        if (bucket != null && bucket.getInteger("count", 0) >= 60) throw new NotificationFailure(429);
        mongo.upsert(Query.query(Criteria.where("_id").is(id)), new Update().inc("count", 1)
                .setOnInsert("expiresAt", Date.from(clock.instant().plusSeconds(120))), CONTROL);
    }
    public void suppress(String owner, Instant now, String reason) {
        LocalDate day = ReminderPolicy.day(now);
        mongo.upsert(Query.query(Criteria.where("_id").is(owner + ":" + day)), new Update()
                .setOnInsert("userId", owner).setOnInsert("dateKst", day.toString()).setOnInsert("reason", reason)
                .setOnInsert("expiresAt", Date.from(ReminderPolicy.start(day.plusDays(2)))), SUPPRESSIONS);
    }
    public void withdrawn(String owner, Instant now) {
        // Conflicts with registration and the send marker in the same ownership document.
        // Withdrawal must also work for a source already marked MERGED; never reopen its guard.
        mongo.upsert(Query.query(Criteria.where("_id").is(owner)), new Update().inc("revision", 1L)
                .set("updatedAt", Date.from(now)).setOnInsert("state", "ACTIVE"), "user_ownership_guards");
        mongo.updateMulti(Query.query(Criteria.where("userId").is(owner)), revoke(), DEVICES);
        suppress(owner, now, "WITHDRAWN");
        // Retain only the 30-day dedup evidence, never provider tokens in history.
    }
    public void merged(String source, String target, Instant now) {
        LocalDate day = ReminderPolicy.day(now);
        if (completed(source, day) || mongo.exists(Query.query(Criteria.where("userId").is(source)
                .and("dateKst").is(day.toString())), DELIVERIES)
                || mongo.exists(Query.query(Criteria.where("_id").is(source + ":" + day)), SUPPRESSIONS))
            suppress(target, now, "MERGED");
        mongo.updateMulti(Query.query(Criteria.where("userId").is(source)), revoke(), DEVICES);
    }
    private static Update revoke() {
        return new Update().set("active", false).unset("pushToken").unset("tokenHash").unset("userId")
                .unset("accountType").unset("permissionObservedAt").set("permission", "UNKNOWN").inc("version", 1L);
    }
    List<Document> candidates(String after, int limit) {
        Criteria c = Criteria.where("active").is(true).and("accountType").is("MEMBER").and("permission").is("AUTHORIZED");
        if (after != null) c.and("_id").gt(after);
        return mongo.find(Query.query(c).with(Sort.by("_id")).limit(limit), Document.class, DEVICES);
    }
    boolean completed(String owner, LocalDate day) {
        return mongo.exists(Query.query(Criteria.where("userId").is(owner).and("submissionCompletedAt")
                .gte(Date.from(ReminderPolicy.start(day))).lt(Date.from(ReminderPolicy.start(day.plusDays(1))))), "exam_sessions");
    }
    boolean eligible(Document device, Instant now, Instant readyAt) {
        if (device == null || !Boolean.TRUE.equals(device.getBoolean("active")) || !"MEMBER".equals(device.getString("accountType"))
                || !"AUTHORIZED".equals(device.getString("permission")) || device.getString("pushToken") == null
                || device.getDate("permissionObservedAt") == null
                || device.getDate("permissionObservedAt").toInstant().isBefore(now.minus(Duration.ofDays(30)))) return false;
        String owner = device.getString("userId"); LocalDate day = ReminderPolicy.day(now);
        if (owner == null || blocked(owner) || completed(owner, day)
                || mongo.exists(Query.query(Criteria.where("_id").is(owner + ":" + day)), SUPPRESSIONS)
                || mongo.exists(Query.query(Criteria.where("userId").is(owner).and("activeGuard").is(true)), "learning_record_deletion_operations")) return false;
        // Missing historical completion evidence alone does not suppress reminders.
        // Explicit daily suppressions and lifecycle guards remain enforced above.
        return true;
    }
    Document device(String id) { return mongo.findById(id, Document.class, DEVICES); }
    static String deliveryId(String owner, LocalDate day, String installation) { return owner + ":" + day + ":" + ReminderPolicy.TYPE + ":" + installation; }
    Claim claim(String installation, Instant now, Instant readyAt) {
        if (!ReminderPolicy.inWindow(now) || paused(now)) return null;
        Document d = device(installation);
        if (d == null || d.getString("userId") == null) return null;
        touchOwner(d.getString("userId"));
        if (!eligible(d, now, readyAt)) return null;
        String id = deliveryId(d.getString("userId"), ReminderPolicy.day(now), installation);
        if (mongo.exists(Query.query(Criteria.where("_id").is(id)), DELIVERIES)) return null;
        mongo.updateFirst(Query.query(Criteria.where("_id").is(installation)), new Update().inc("version", 1L), DEVICES);
        mongo.insert(new Document("_id", id).append("userId", d.getString("userId"))
                .append("dateKst", ReminderPolicy.day(now).toString()).append("type", ReminderPolicy.TYPE)
                .append("installationId", installation).append("status", "SENDING").append("attemptedAt", Date.from(now))
                .append("expiresAt", Date.from(ReminderPolicy.expiry(ReminderPolicy.day(now)))), DELIVERIES);
        return new Claim(id, installation, d.getString("userId"), d.getString("pushToken"), d.getString("tokenHash"),
                ((Number) d.get("version")).longValue() + 1);
    }
    boolean stillEligible(Claim c, Instant now, Instant readyAt) {
        Document d = device(c.installation);
        return ReminderPolicy.inWindow(now) && !paused(now) && d != null && c.owner.equals(d.getString("userId"))
                && ((Number) d.get("version")).longValue() == c.version && eligible(d, now, readyAt);
    }
    void finish(Claim c, PushGateway.Result result, Instant now) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(c.id).and("status").is("SENDING")),
                new Update().set("status", result.status().name()).set("finishedAt", Date.from(now)), DELIVERIES);
        if (result.invalidToken()) mongo.updateFirst(Query.query(Criteria.where("_id").is(c.installation)
                .and("version").is(c.version).and("tokenHash").is(c.tokenHash)), revoke(), DEVICES);
    }
    void pause(PushGateway.Result result, Instant now) {
        if (!result.authFailure() && result.retryAfterSeconds() <= 0) return;
        Update update = new Update();
        if (result.authFailure()) update.set("authBlocked", true);
        if (result.retryAfterSeconds() > 0) update.max("pausedUntil", Date.from(now.plusSeconds(result.retryAfterSeconds())));
        mongo.upsert(Query.query(Criteria.where("_id").is("fcm-circuit")), update, CONTROL);
    }
    boolean paused(Instant now) {
        Document circuit = mongo.findById("fcm-circuit", Document.class, CONTROL);
        return circuit != null && (Boolean.TRUE.equals(circuit.getBoolean("authBlocked"))
                || (circuit.getDate("pausedUntil") != null && circuit.getDate("pausedUntil").toInstant().isAfter(now)));
    }
    void maintain(Instant now, int limit) {
        for (Document d : mongo.find(Query.query(Criteria.where("status").is("SENDING")
                .and("attemptedAt").lt(Date.from(now.minusSeconds(60)))).limit(limit), Document.class, DELIVERIES))
            mongo.updateFirst(Query.query(Criteria.where("_id").is(d.getString("_id")).and("status").is("SENDING")),
                    new Update().set("status", "UNKNOWN").set("finishedAt", Date.from(now)), DELIVERIES);
        for (Document d : mongo.find(Query.query(Criteria.where("active").is(true).and("lastSeenAt")
                .lt(Date.from(now.minus(Duration.ofDays(90))))).limit(limit), Document.class, DEVICES))
            mongo.updateFirst(Query.query(Criteria.where("_id").is(d.getString("_id")).and("version").is(d.get("version"))), revoke(), DEVICES);
    }
    static void validate(String id, String secret, String platform, String token, String permission, Instant observed, Instant now) {
        uuid(id); proof(secret);
        if (!Set.of("IOS", "ANDROID").contains(platform == null ? "" : platform)
                || token == null || !token.matches("[A-Za-z0-9_:.-]{20,4096}")
                || !Set.of("AUTHORIZED", "DENIED", "UNKNOWN").contains(permission == null ? "" : permission)
                || observed == null || observed.isAfter(now.plusSeconds(60)) || observed.isBefore(now.minus(Duration.ofDays(30))))
            throw new NotificationFailure(400);
    }
    static void uuid(String id) { if (id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) throw new NotificationFailure(400); }
    static void proof(String value) { if (value == null || !value.matches("[A-Za-z0-9_-]{43}") || Base64.getUrlDecoder().decode(value).length != 32) throw new NotificationFailure(400); }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA256 unavailable"); }
    }
    static final class Claim {
        final String id, installation, owner, token, tokenHash;
        final long version;
        Claim(String id, String installation, String owner, String token, String tokenHash, long version) {
            this.id = id; this.installation = installation; this.owner = owner; this.token = token; this.tokenHash = tokenHash; this.version = version;
        }
        @Override public String toString() { return "NotificationClaim[redacted]"; }
    }
}
