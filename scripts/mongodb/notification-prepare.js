"use strict";

const COLLECTIONS = ["notification_devices", "exam_submission_receipts", "daily_exam_reminder_deliveries",
    "daily_reminder_suppressions", "notification_control", "user_ownership_guards"];
const spec = (collection, name, keys, unique = false, sparse = false, ttl = undefined) =>
    ({collection, name, key: Object.fromEntries(keys.map(k => [k, 1])), unique, sparse, ttl});
const INDEXES = [
    spec("notification_devices", "notification_token_unique", ["tokenHash"], true, true),
    spec("notification_devices", "notification_candidates", ["active", "accountType", "permission", "_id"]),
    spec("notification_devices", "notification_owner", ["userId"]),
    spec("notification_devices", "notification_stale", ["active", "lastSeenAt"]),
    spec("exam_submission_receipts", "submission_question_unique", ["examId", "questionNumber"], true),
    spec("exam_submission_receipts", "submission_receipt_ttl", ["expiresAt"], false, false, 0),
    spec("exam_sessions", "notification_submission_date", ["userId", "submissionCompletedAt", "createdAt"]),
    spec("daily_exam_reminder_deliveries", "reminder_daily_device_unique", ["userId", "dateKst", "type", "installationId"], true),
    spec("daily_exam_reminder_deliveries", "reminder_stale_sending", ["status", "attemptedAt"]),
    spec("daily_exam_reminder_deliveries", "reminder_history_ttl", ["expiresAt"], false, false, 0),
    spec("daily_reminder_suppressions", "reminder_suppression_ttl", ["expiresAt"], false, false, 0),
    spec("notification_control", "notification_control_ttl", ["expiresAt"], false, false, 0)
];
function databaseName(name) {
    if (typeof name !== "string" || !name.trim() || name !== name.trim() || ["admin", "local", "config"].includes(name.toLowerCase()))
        throw Error("Explicit non-system database required");
    return name;
}
function compatible(actual, expected) {
    return JSON.stringify(actual.key) === JSON.stringify(expected.key) && (actual.unique === true) === expected.unique
        && (actual.sparse === true) === expected.sparse && !actual.hidden && !actual.partialFilterExpression && !actual.collation
        && (expected.ttl === undefined ? actual.expireAfterSeconds === undefined : Number(actual.expireAfterSeconds) === expected.ttl);
}
async function prepare(database, apply) {
    databaseName(database.getName());
    const names = new Set((await database.getCollectionInfos()).map(c => c.name));
    if (!names.has("exam_sessions")) throw Error("Learning Core exam_sessions required");
    for (const expected of INDEXES) {
        if (!names.has(expected.collection)) continue;
        const collection = database.getCollection(expected.collection);
        const indexes = await collection.getIndexes();
        const named = indexes.find(i => i.name === expected.name);
        if (named && !compatible(named, expected)) throw Error("Incompatible notification index");
        if (expected.unique) {
            const pipeline = expected.sparse ? [{$match: {tokenHash: {$exists: true}}}] : [];
            pipeline.push({$group: {_id: Object.fromEntries(Object.keys(expected.key).map(k => [k, `$${k}`])), count: {$sum: 1}}},
                {$match: {count: {$gt: 1}}}, {$limit: 1});
            if ((await collection.aggregate(pipeline).toArray()).length) throw Error("Duplicate notification key; no data repaired");
        }
    }
    if (apply) {
        for (const name of COLLECTIONS) if (!names.has(name)) await database.createCollection(name);
        for (const s of INDEXES) {
            const options = {name: s.name, unique: s.unique, sparse: s.sparse};
            if (s.ttl !== undefined) options.expireAfterSeconds = s.ttl;
            await database.getCollection(s.collection).createIndex(s.key, options);
        }
        for (const s of INDEXES) if (!(await database.getCollection(s.collection).getIndexes()).some(i => i.name === s.name && compatible(i, s)))
            throw Error("Notification index verification failed");
    }
    return {apply, collections: COLLECTIONS.length, indexes: INDEXES.length};
}
function environment(key) { return typeof process !== "undefined" ? process.env[key] : _getEnv(key); }
async function runMongosh() {
    const name = databaseName(environment("MONGODB_DATABASE"));
    const apply = environment("NOTIFICATION_PREPARE_APPLY") === "true";
    if (apply && environment("NOTIFICATION_WRITERS_DRAINED") !== "true") throw Error("Drain writers before apply");
    if (!environment("MONGODB_URI")) throw Error("Connection required");
    print(JSON.stringify(await prepare(new Mongo(environment("MONGODB_URI")).getDB(name), apply))); quit(0);
}
if (typeof module !== "undefined") module.exports = {COLLECTIONS, INDEXES, databaseName, compatible, prepare};
if (environment("NOTIFICATION_MONGOSH_PAYLOAD") === "true") {
    runMongosh().catch(() => { print("Notification preparation failed; no credentials or data printed"); quit(2); });
} else if (typeof require !== "undefined" && require.main === module) {
    try {
        databaseName(process.env.MONGODB_DATABASE);
        if (!process.env.MONGODB_URI) throw Error("Connection required");
        const child = require("node:child_process").spawnSync("mongosh", ["--nodb", "--quiet", "--file", __filename],
            {env: {...process.env, NOTIFICATION_MONGOSH_PAYLOAD: "true"}, stdio: "inherit"});
        process.exitCode = child.status ?? 2;
    } catch (_) { console.error("Notification preparation requires explicit database and connection configuration"); process.exitCode = 2; }
}
