"use strict";
const test = require("node:test"), assert = require("node:assert/strict");
const {INDEXES, databaseName, compatible, validateOperation, legacyMetadata, prepare} = require("./billing-reservation-reconciliation-prepare");
const NOW = new Date("2026-09-08T03:00:00Z");
const id = n => `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`;
function operation() { return {_id: id(1), userId: id(2), operationId: id(3), sessionId: "ex_1", mockExamId: "mock_exam_001",
    createdAt: new Date(NOW - 180000), updatedAt: new Date(NOW - 180000), state: "PREPARED", activeGuard: true}; }
function committed() { return {...operation(), state: "SESSION_COMMITTED", reservationId: id(4), attemptGroupId: id(5),
    reservationKind: "INITIAL", reservationExpiresAt: new Date(NOW.getTime() + 120000), sessionCommittedAt: new Date(NOW - 100000)}; }
function session(op) { return {_id: op.sessionId, userId: op.userId, creationOperationId: op.operationId, mockExamId: op.mockExamId,
    billingReservationId: op.reservationId, attemptGroupId: op.attemptGroupId, billingReservationKind: op.reservationKind,
    status: "ENTITLEMENT_CONFIRMING", entitlementState: "CONFIRMING"}; }
function database(ops = [], sessions = []) {
    const data = {exam_creation_operations: ops, exam_sessions: sessions}, writes = [];
    const matches = (d, q = {}) => Object.entries(q).every(([k, v]) => k.startsWith("$") || d[k] === v);
    return {writes, getName: () => "learning-core-test", getCollectionInfos: async () => Object.keys(data).map(name => ({name})),
        createCollection: async name => { writes.push("create"); data[name] = []; },
        getCollection: name => ({find: q => (data[name] || []).filter(d => matches(d, q)),
            findOne: async q => (data[name] || []).find(d => matches(d, q)) || null,
            getIndexes: async () => [], createIndex: async () => writes.push("index"),
            updateOne: async (q, u) => { writes.push("update"); const d = (data[name] || []).find(d => d._id === q._id);
                Object.assign(d, u.$set); d.version = Number(d.version || 0) + 1; return {modifiedCount: 1}; }})};
}
test("explicit database and exact non-TTL indexes", () => {
    for (const bad of [undefined, "", "admin", "local", "config", " abc "]) assert.throws(() => databaseName(bad));
    for (const spec of INDEXES) {
        assert.equal(compatible({key: spec.key}, spec), true);
        for (const invalid of [{unique: true}, {expireAfterSeconds: 0}, {partialFilterExpression: {}}, {hidden: true}, {collation: {locale: "en"}}])
            assert.equal(compatible({key: spec.key, ...invalid}, spec), false);
    }
});
test("valid prepared and committed snapshots", () => {
    assert.deepEqual(validateOperation(operation(), null, NOW), []);
    const op = committed(); assert.deepEqual(validateOperation(op, session(op), NOW), []);
});
test("early warning eligibility and pending indexes are separate non-TTL operation scans", () => {
    const early = INDEXES.filter(spec => spec.name.startsWith("reservation_recovery_early_"));
    assert.deepEqual(early, [
        {collection: "exam_creation_operations", name: "reservation_recovery_early_due",
            key: {"recovery.schemaVersion": 1, activeGuard: 1, "recovery.earlyAlert.alertIncidentId": 1, createdAt: 1, _id: 1}},
        {collection: "exam_creation_operations", name: "reservation_recovery_early_pending",
            key: {"recovery.earlyAlert.alertStatus": 1, "recovery.earlyAlert.alertNextAt": 1, _id: 1}}
    ]);
    for (const spec of early) assert.equal(compatible({key: spec.key, expireAfterSeconds: 300}, spec), false);
});
for (const [name, change, code] of [
    ["invalid group UUID", op => op.attemptGroupId = "group", "invalid_reservation_snapshot"],
    ["unsafe TTL", op => op.purgeAt = NOW, "unsafe_active_retention"],
    ["false active guard", op => op.activeGuard = false, "unsafe_active_retention"],
    ["invalid phone", op => op.continuationReason = "PHONE_REJOIN", "invalid_phone_snapshot"],
    ["missing reservation", op => delete op.reservationId, "missing_reservation_snapshot"],
    ["live lease", op => op.recovery = {schemaVersion: 1, leaseUntil: new Date(NOW.getTime() + 1000)}, "live_or_invalid_lease"]
]) test(name, () => { const op = committed(); change(op); assert.ok(validateOperation(op, session(op), NOW).includes(code)); });
test("missing or mismatched Session cannot be enrolled", () => {
    const op = committed(); assert.ok(validateOperation(op, null, NOW).includes("missing_confirming_session"));
    assert.ok(validateOperation(op, {...session(op), userId: id(9)}, NOW).includes("session_snapshot_mismatch"));
    assert.ok(validateOperation(operation(), session(op), NOW).includes("precommit_session_present"));
});
test("legacy dispatch is always unknown and existing budget remains", () => {
    const op = operation(); assert.equal(legacyMetadata(op, NOW).dispatch, "MAY_HAVE_BEEN_SENT");
    op.recovery = {attempts: 77, firstAt: new Date(NOW - 1000), intent: "CLEANUP_PRECOMMIT"};
    const metadata = legacyMetadata(op, NOW); assert.equal(metadata.attempts, 77);
    assert.equal(metadata.firstAt, op.recovery.firstAt); assert.equal(metadata.intent, "CLEANUP_PRECOMMIT");
});
test("dry-run never writes and reports missing allowlist", async () => {
    const db = database([operation()]);
    const report = await prepare(db, {allowlist: [id(8)], now: NOW});
    assert.equal(report.legacy, 1); assert.equal(report.blockers.allowlist_missing, 1); assert.deepEqual(db.writes, []);
});
test("apply requires drain and rejects blockers before writing", async () => {
    const db = database([{...operation(), purgeAt: NOW}]);
    await assert.rejects(prepare(db, {apply: true}));
    await assert.rejects(prepare(db, {apply: true, drained: true, allowlist: [id(1)], now: NOW}));
    assert.deepEqual(db.writes, []);
});
test("only allowlisted legacy is enrolled; repeat does not reset", async () => {
    const a = operation(), b = {...operation(), _id: id(6), operationId: id(7), userId: id(8)};
    const db = database([a, b]);
    await prepare(db, {apply: true, drained: true, allowlist: [a._id], now: NOW});
    assert.equal(a.recovery.schemaVersion, 1); assert.equal(b.recovery, undefined);
    a.recovery.attempts = 15;
    await prepare(db, {apply: true, drained: true, allowlist: [a._id], now: NOW});
    assert.equal(a.recovery.attempts, 15); assert.equal(db.writes.filter(x => x === "update").length, 1);
});
test("duplicate active owners and orphan confirming Sessions block", async () => {
    const a = operation(), b = {...operation(), _id: id(6), operationId: id(7)};
    const db = database([a, b], [session(committed())]);
    const report = await prepare(db, {now: NOW});
    assert.equal(report.blockers.duplicate_active_owner, 1); assert.equal(report.blockers.orphan_confirming_session, 1);
});
