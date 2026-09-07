"use strict";

// Offline preparation only. No baseDate initialization, object deletion or exam collection writes.
const COLLECTIONS = ["questions", "catalog_state", "attempts", "submit_receipts", "grading_jobs", "callback_receipts"].map(x => `challenge_10s_${x}`);
const spec = (suffix, name, unique, ...keys) => ({collection: `challenge_10s_${suffix}`, name, unique, key: Object.fromEntries(keys.map(k => [k, 1]))});
const INDEXES = [
    spec("questions", "challenge_day_unique", true, "dayNumber"),
    spec("questions", "challenge_question_id_unique", true, "questions.questionId"),
    spec("attempts", "challenge_owner_date_question_unique", true, "userId", "challengeDate", "questionNumber"),
    spec("attempts", "challenge_expiry", false, "state", "submissionDeadlineAt", "_id"),
    spec("submit_receipts", "challenge_submit_key_unique", true, "userId", "idempotencyKey"),
    spec("grading_jobs", "challenge_generation_unique", true, "attemptId", "generation"),
    spec("grading_jobs", "challenge_dispatch_due", false, "state", "nextAttemptAt", "_id"),
    spec("grading_jobs", "challenge_lease_due", false, "state", "leaseUntil"),
    spec("grading_jobs", "challenge_callback_due", false, "state", "callbackDeadlineAt")
];
function environment(name) {
    if (typeof process !== "undefined" && process.env) return process.env[name];
    if (typeof _getEnv === "function") return _getEnv(name);
    return undefined;
}
function databaseName(name) {
    if (typeof name !== "string" || !name.trim() || name !== name.trim() || ["admin", "local", "config"].includes(name.toLowerCase())) throw Error("Explicit non-system MONGODB_DATABASE required");
    return name;
}
function integer(value) {
    const number = value && value._bsontype === "Int32" ? value.valueOf() : value;
    return typeof number === "number" && Number.isInteger(number) ? number : NaN;
}
function validateCatalog(documents) {
    const days = new Set(), ids = new Set();
    const text = s => typeof s === "string" && s.trim().length > 0;
    for (const d of documents) {
        const dayNumber = integer(d.dayNumber);
        if (!Number.isInteger(dayNumber) || dayNumber < 1 || days.has(dayNumber) || !Array.isArray(d.questions) || d.questions.length !== 3) throw Error("Invalid catalog day");
        days.add(dayNumber); const numbers = new Set();
        for (const q of d.questions) {
            const questionNumber = integer(q.questionNumber);
            if (![1, 2, 3].includes(questionNumber) || numbers.has(questionNumber) || !Number.isInteger(integer(q.difficulty))
                || !text(q.korean) || !text(q.referenceAnswer) || !text(q.questionId) || ids.has(q.questionId)) throw Error("Invalid catalog question");
            numbers.add(questionNumber); ids.add(q.questionId);
        }
    }
    if (!days.has(1)) throw Error("Day 1 required");
    return {days: days.size, questions: ids.size};
}
function compatible(index, expected) {
    return JSON.stringify(index.key) === JSON.stringify(expected.key) && (index.unique === true) === expected.unique
        && !index.sparse && !index.hidden && !index.partialFilterExpression && !index.collation && index.expireAfterSeconds === undefined;
}
async function prepare(database, apply) {
    databaseName(database.getName());
    const catalog = await database.getCollection(COLLECTIONS[0]).find().toArray();
    const report = validateCatalog(catalog);
    // JavaScript integer values alone cannot distinguish BSON double(2) from int(2).
    const wrongTypes = await database.getCollection(COLLECTIONS[0]).aggregate([
        {$unwind: "$questions"}, {$match: {$expr: {$or: [
            {$ne: [{$type: "$dayNumber"}, "int"]}, {$ne: [{$type: "$questions.questionNumber"}, "int"]},
            {$ne: [{$type: "$questions.difficulty"}, "int"]}
        ]}}}, {$count: "count"}
    ]).toArray();
    if (wrongTypes.length) throw Error("Catalog requires BSON int fields");
    const names = new Set((await database.getCollectionInfos()).map(c => c.name));
    for (const collection of COLLECTIONS) {
        if (!names.has(collection)) continue;
        const indexes = await database.getCollection(collection).getIndexes();
        if (indexes.some(i => i.expireAfterSeconds !== undefined)) throw Error("Unexpected Challenge TTL");
        for (const expected of INDEXES.filter(i => i.collection === collection)) {
            const named = indexes.find(i => i.name === expected.name);
            if (named && !compatible(named, expected)) throw Error("Incompatible existing index");
            if (expected.unique) {
                const group = Object.fromEntries(Object.keys(expected.key).map(k => [k.replaceAll(".", "_"), `$${k}`]));
                if (collection !== COLLECTIONS[0] && (await database.getCollection(collection).aggregate([
                    {$group: {_id: group, count: {$sum: 1}}}, {$match: {count: {$gt: 1}}}, {$limit: 1}
                ]).toArray()).length) throw Error("Duplicate Challenge unique key");
            }
        }
    }
    const attempts = database.getCollection("challenge_10s_attempts");
    const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
    for await (const a of attempts.find()) {
        const questionNumber = integer(a.questionNumber), generation = integer(a.generation);
        if (!uuid.test(a._id) || !uuid.test(a.userId) || !/^\d{4}-\d{2}-\d{2}$/.test(a.challengeDate)
            || ![1, 2, 3].includes(questionNumber) || !["CREATED", "SUBMITTED", "EXPIRED"].includes(a.state)
            || !(a.createdAt instanceof Date) || !(a.submissionDeadlineAt instanceof Date)
            || a.submissionDeadlineAt - a.createdAt !== 3600000
            || a.uploadKey !== `temp/challenges/${a._id}/q_${questionNumber}.m4a`
            || !a.question || integer(a.question.questionNumber) !== questionNumber || typeof a.question.referenceAnswer !== "string" || !a.question.referenceAnswer.trim())
            throw Error("Invalid persisted Challenge attempt");
        if (a.state === "SUBMITTED" && (!Number.isInteger(generation) || generation < 1 || generation > 3
            || !await database.getCollection("challenge_10s_grading_jobs").findOne({_id: `challenge:${a._id}:grading:${generation}`}))) throw Error("Submitted attempt missing current Job");
        if (a.state === "SUBMITTED" && !await database.getCollection("challenge_10s_submit_receipts").findOne({attemptId: a._id})) throw Error("Submitted attempt missing success receipt");
    }
    for (const suffix of ["grading_jobs", "submit_receipts", "callback_receipts"]) {
        for await (const record of database.getCollection(`challenge_10s_${suffix}`).find()) {
            const a = await attempts.findOne({_id: record.attemptId});
            if (!a || a.state !== "SUBMITTED") throw Error("Orphan Challenge Job or receipt");
            if (suffix === "submit_receipts" && (record.userId !== a.userId || !record.response || integer(record.questionNumber) !== integer(a.questionNumber))) throw Error("Submit receipt owner or response mismatch");
            if (suffix === "grading_jobs" && (record._id !== `challenge:${a._id}:grading:${integer(record.generation)}` || integer(record.generation) > integer(a.generation))) throw Error("Invalid Job identity");
        }
    }
    if (apply) {
        for (const name of COLLECTIONS) if (!names.has(name)) await database.createCollection(name);
        for (const index of INDEXES) await database.getCollection(index.collection).createIndex(index.key, {name: index.name, unique: index.unique});
        for (const index of INDEXES) if (!(await database.getCollection(index.collection).getIndexes()).some(i => i.name === index.name && compatible(i, index))) throw Error("Index verification failed");
    }
    return {...report, apply, collections: COLLECTIONS.length, indexes: INDEXES.length};
}
async function runMongosh() {
    const name = databaseName(environment("MONGODB_DATABASE"));
    const apply = environment("CHALLENGE_PREPARE_APPLY") === "true";
    if (apply && environment("CHALLENGE_WRITERS_DRAINED") !== "true") throw Error("Drain Challenge writers before apply");
    if (!environment("MONGODB_URI")) throw Error("MONGODB_URI required");
    const connection = new Mongo(environment("MONGODB_URI"));
    print(JSON.stringify(await prepare(connection.getDB(name), apply)));
    quit(0);
}
if (typeof module !== "undefined") module.exports = {COLLECTIONS, INDEXES, databaseName, validateCatalog, compatible, prepare};
// Keep the async mongosh invocation as the final completion value so --file awaits it.
if (environment("CHALLENGE_MONGOSH_PAYLOAD") === "true") {
    runMongosh().catch(() => { print("Challenge preparation failed; no credentials or data printed"); quit(2); });
} else if (typeof require !== "undefined" && require.main === module) {
    try {
        databaseName(process.env.MONGODB_DATABASE);
        if (!process.env.MONGODB_URI) throw Error("MONGODB_URI required");
        const child = require("node:child_process").spawnSync("mongosh", ["--nodb", "--quiet", "--file", __filename], {
            env: {...process.env, CHALLENGE_MONGOSH_PAYLOAD: "true"}, stdio: "inherit"
        });
        process.exitCode = child.status ?? 2;
    } catch (_) { console.error("Challenge preparation requires explicit database and connection configuration"); process.exitCode = 2; }
}
