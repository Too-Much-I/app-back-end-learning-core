"use strict";

// TMI-128: offline inventory/index preparation, never a billing/owner repair command.
const OPERATIONS = "exam_creation_operations", SESSIONS = "exam_sessions", CONTROL = "reservation_reconciliation_control";
const ACTIVE = new Set(["PREPARED", "RESERVED", "SESSION_COMMITTED", "CANCEL_PENDING"]);
const TERMINAL = new Set(["SUCCEEDED", "CANCELED", "EXPIRED", "FAILED_TERMINAL"]);
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const USER_UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const INDEXES = [
    {collection: OPERATIONS, name: "reservation_recovery_early_due", key: {"recovery.schemaVersion": 1, activeGuard: 1, "recovery.earlyAlert.alertIncidentId": 1, createdAt: 1, _id: 1}},
    {collection: OPERATIONS, name: "reservation_recovery_early_pending", key: {"recovery.earlyAlert.alertStatus": 1, "recovery.earlyAlert.alertNextAt": 1, _id: 1}},
    {collection: OPERATIONS, name: "reservation_recovery_due", key: {"recovery.schemaVersion": 1, activeGuard: 1, "recovery.status": 1, "recovery.nextAt": 1, _id: 1}},
    {collection: OPERATIONS, name: "reservation_recovery_lease", key: {"recovery.schemaVersion": 1, activeGuard: 1, "recovery.status": 1, "recovery.leaseUntil": 1, _id: 1}},
    ...[OPERATIONS, CONTROL].map(collection => ({collection, name: "reservation_recovery_alert", key: {"recovery.alertStatus": 1, "recovery.alertNextAt": 1, _id: 1}}))
];
const date = value => value instanceof Date && Number.isFinite(value.getTime());
const text = value => typeof value === "string" && value.trim().length > 0;
function databaseName(name) {
    if (!text(name) || name.trim() !== name || ["admin", "config", "local"].includes(name.toLowerCase())) throw Error("Explicit non-system database required");
    return name;
}
function compatible(index, spec) {
    return JSON.stringify(index.key) === JSON.stringify(spec.key) && !index.unique && !index.sparse && !index.hidden
        && !index.partialFilterExpression && !index.collation && index.expireAfterSeconds === undefined;
}
function validateOperation(op, session, now) {
    const errors = [];
    if (!UUID.test(op._id) || !USER_UUID.test(op.userId) || !UUID.test(op.operationId) || !text(op.sessionId)
        || !text(op.mockExamId) || !date(op.createdAt) || !date(op.updatedAt) || ![...ACTIVE, ...TERMINAL].includes(op.state)) errors.push("invalid_identity_or_state");
    if (ACTIVE.has(op.state) && (op.activeGuard !== true || op.purgeAt != null)) errors.push("unsafe_active_retention");
    if (TERMINAL.has(op.state) && (op.activeGuard !== false || !date(op.terminalAt) || !date(op.purgeAt))) errors.push("invalid_terminal_evidence");
    if (op.recovery?.resolvedAt && (!date(op.recovery.resolvedAt) || !date(op.purgeAt)
        || op.purgeAt - op.recovery.resolvedAt < 7 * 86400000)) errors.push("short_local_retention");
    if (op.reservationId != null && (!UUID.test(op.reservationId) || !UUID.test(op.attemptGroupId)
        || !["INITIAL", "REPLACEMENT"].includes(op.reservationKind) || !date(op.reservationExpiresAt))) errors.push("invalid_reservation_snapshot");
    if (["RESERVED", "SESSION_COMMITTED", "CANCEL_PENDING", "SUCCEEDED"].includes(op.state) && !op.reservationId) errors.push("missing_reservation_snapshot");
    const replacement = op.expectedAttemptGroupId != null;
    if (replacement && (!UUID.test(op.expectedAttemptGroupId) || op.expectedMockExamId !== op.mockExamId
        || (op.attemptGroupId != null && op.attemptGroupId !== op.expectedAttemptGroupId))) errors.push("invalid_replacement_snapshot");
    if (op.reservationKind != null && (op.reservationKind === "REPLACEMENT") !== replacement) errors.push("reservation_kind_mismatch");
    if (op.continuationReason === "PHONE_REJOIN") {
        if (!UUID.test(op.continuationId) || !replacement || op.replacementSourceSessionId != null) errors.push("invalid_phone_snapshot");
    } else if (op.continuationReason != null || op.continuationId != null || (op.replacementSourceSessionId != null) !== replacement) errors.push("invalid_continuation_context");
    if (session && (session._id !== op.sessionId || (ACTIVE.has(op.state) && session.userId !== op.userId) || session.creationOperationId !== op.operationId
        || session.billingReservationId !== op.reservationId || session.attemptGroupId !== op.attemptGroupId
        || session.billingReservationKind !== op.reservationKind || session.mockExamId !== op.mockExamId)) errors.push("session_snapshot_mismatch");
    if (["PREPARED", "RESERVED", "CANCEL_PENDING"].includes(op.state) && session) errors.push("precommit_session_present");
    if (op.state === "SESSION_COMMITTED" && (!date(op.sessionCommittedAt) || !session
        || session.status !== "ENTITLEMENT_CONFIRMING" || session.entitlementState !== "CONFIRMING")) errors.push("missing_confirming_session");
    if (op.recovery && ![0, 1].includes(Number(op.recovery.schemaVersion))) errors.push("unknown_recovery_schema");
    if (op.recovery && (!Number.isInteger(Number(op.recovery.attempts)) || Number(op.recovery.attempts) < 0
        || !["CONTINUE", "CLEANUP_PRECOMMIT"].includes(op.recovery.intent)
        || !["READY", "IN_FLIGHT", "BLOCKED_AUTH", "BLOCKED_OWNER", "NEEDS_REVIEW", "DONE"].includes(op.recovery.status)
        || !["NOT_DISPATCHED", "MAY_HAVE_BEEN_SENT", "OBSERVED"].includes(op.recovery.dispatch)
        || (op.recovery.firstAt != null && !date(op.recovery.firstAt))
        || !date(op.recovery.lastProgressAt) || !date(op.recovery.nextAt))) errors.push("invalid_recovery_schedule");
    if (op.recovery?.leaseUntil && (!date(op.recovery.leaseUntil) || op.recovery.leaseUntil > now)) errors.push("live_or_invalid_lease");
    return [...new Set(errors)];
}
function legacyMetadata(op, now) {
    // Never infer NOT_DISPATCHED for legacy records, even if PREPARED and status returns 404.
    return {schemaVersion: 1, status: "READY", intent: op.recovery?.intent ?? (op.state === "CANCEL_PENDING" ? "CLEANUP_PRECOMMIT" : "CONTINUE"),
        dispatch: "MAY_HAVE_BEEN_SENT", firstAt: op.recovery?.firstAt ?? now, attempts: Number(op.recovery?.attempts ?? 0),
        nextAt: now, lastProgressAt: op.updatedAt};
}
async function prepare(database, {apply = false, drained = false, allowlist = [], now = new Date()} = {}) {
    databaseName(database.getName());
    if (!Array.isArray(allowlist) || allowlist.some(id => !UUID.test(id)) || new Set(allowlist).size !== allowlist.length) throw Error("Explicit unique command UUID allowlist required");
    if (apply && !drained) throw Error("All HTTP and worker writers must be drained before apply");
    const report = {operations: 0, legacy: 0, approved: 0, staleTerminalEvidence: 0, blockers: {}};
    const issue = reason => { report.blockers[reason] = (report.blockers[reason] ?? 0) + 1; };
    const approved = new Set(allowlist), found = new Set(), activeOwners = new Set(), keys = new Set(), enrolled = [];
    const collection = database.getCollection(OPERATIONS), sessions = database.getCollection(SESSIONS);
    for await (const op of collection.find()) {
        report.operations++;
        const session = await sessions.findOne({_id: op.sessionId});
        validateOperation(op, session, now).forEach(issue);
        const key = `${op.userId}:${op.operationId}`;
        if (keys.has(key)) issue("duplicate_operation_key"); keys.add(key);
        if (ACTIVE.has(op.state)) { if (activeOwners.has(op.userId)) issue("duplicate_active_owner"); activeOwners.add(op.userId); }
        if (TERMINAL.has(op.state) && date(op.purgeAt) && op.purgeAt <= now) report.staleTerminalEvidence++;
        if (ACTIVE.has(op.state) && (!op.recovery || Number(op.recovery.schemaVersion) === 0)) report.legacy++;
        if (approved.has(op._id)) {
            found.add(op._id);
            if (!ACTIVE.has(op.state)) issue("allowlist_terminal");
            else if (Number(op.recovery?.schemaVersion) === 1) { /* already enrolled: do not reset budget */ }
            else {
                const guard = await database.getCollection("user_ownership_guards").findOne({_id: op.userId});
                const deny = await database.getCollection("withdrawn_user_access_denies").findOne({_id: op.userId});
                if (guard?.state === "MERGED" || (date(deny?.blockedUntil) && deny.blockedUntil > now)) issue("allowlist_owner_blocked");
                else enrolled.push(op);
            }
        }
    }
    for (const id of approved) if (!found.has(id)) issue("allowlist_missing");
    for await (const session of sessions.find({entitlementState: "CONFIRMING"})) {
        const op = await collection.findOne({userId: session.userId, operationId: session.creationOperationId});
        if (!op || op.sessionId !== session._id || op.state !== "SESSION_COMMITTED") issue("orphan_confirming_session");
    }
    const names = new Set((await database.getCollectionInfos()).map(info => info.name));
    for (const spec of INDEXES) {
        if (!names.has(spec.collection)) continue;
        const indexes = await database.getCollection(spec.collection).getIndexes();
        const named = indexes.find(i => i.name === spec.name);
        if (named && !compatible(named, spec)) issue("incompatible_recovery_index");
        if (spec.collection === CONTROL && indexes.some(i => i.expireAfterSeconds !== undefined)) issue("unsafe_circuit_ttl");
        if (spec.collection === OPERATIONS && indexes.some(i => i.expireAfterSeconds !== undefined
            && i.name !== "ttl_exam_creation_purge")) issue("unsafe_operation_ttl");
    }
    report.approved = enrolled.length;
    if (!apply) return {...report, apply: false};
    if (Object.keys(report.blockers).length) throw Error("Inventory blockers must be resolved through an approved separate procedure");
    if (!names.has(CONTROL)) await database.createCollection(CONTROL);
    for (const spec of INDEXES) await database.getCollection(spec.collection).createIndex(spec.key, {name: spec.name});
    for (const op of enrolled) {
        const result = await collection.updateOne({_id: op._id, version: op.version ?? null, state: op.state,
            $or: [{recovery: null}, {"recovery.schemaVersion": 0}]}, {$set: {recovery: legacyMetadata(op, now)}, $inc: {version: 1}});
        if (result.modifiedCount !== 1) throw Error("Enrollment CAS lost; stop and repeat dry-run without resetting prior records");
    }
    return {...report, apply: true};
}
function environment(name) { return typeof process !== "undefined" ? process.env[name] : _getEnv(name); }
async function runMongosh() {
    const name = databaseName(environment("MONGODB_DATABASE"));
    if (!environment("MONGODB_URI")) throw Error("Explicit connection required");
    const connection = new Mongo(environment("MONGODB_URI"));
    print(JSON.stringify(await prepare(connection.getDB(name), {apply: environment("RESERVATION_RECOVERY_APPLY") === "true",
        drained: environment("RESERVATION_WRITERS_DRAINED") === "true", allowlist: JSON.parse(environment("RESERVATION_RECOVERY_ALLOWLIST") || "[]")})));
    quit(0);
}
if (typeof module !== "undefined") module.exports = {INDEXES, databaseName, compatible, validateOperation, legacyMetadata, prepare};
if (environment("RESERVATION_RECOVERY_MONGOSH") === "true") {
    runMongosh().catch(() => { print("Reservation inventory/apply failed; no data or credentials printed"); quit(2); });
} else if (typeof require !== "undefined" && require.main === module) {
    try {
        databaseName(process.env.MONGODB_DATABASE);
        if (!process.env.MONGODB_URI) throw Error("Explicit connection required");
        const child = require("node:child_process").spawnSync("mongosh", ["--nodb", "--quiet", "--file", __filename],
            {env: {...process.env, RESERVATION_RECOVERY_MONGOSH: "true"}, stdio: "inherit"});
        process.exitCode = child.status ?? 2;
    } catch (_) { console.error("Reservation preparation requires explicit connection and database"); process.exitCode = 2; }
}
