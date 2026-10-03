"use strict";

// DDL only. Does not delete content, backfill guards, approve rollout, or enable runtime flags.
const INDEXES = [
    {collection: "learning_record_deletion_operations", name: "uq_deletion_active_owner", key: {userId: 1},
        unique: true, partialFilterExpression: {activeGuard: true}},
    {collection: "learning_record_deletion_operations", name: "idx_deletion_owner_requested", key: {userId: 1, requestedAt: -1}},
    {collection: "learning_record_deletion_commands", name: "idx_deletion_command_operation", key: {deletionId: 1}},
    {collection: "learning_record_deletion_targets", name: "idx_deletion_target_operation", key: {deletionId: 1, targetType: 1, callbackFence: 1}},
    {collection: "learning_record_deletion_targets", name: "idx_deletion_target_aggregate", key: {targetType: 1, aggregateId: 1}},
    {collection: "learning_record_billing_continuations", name: "idx_deleted_continuation_owner", key: {userId: 1, transferState: 1}},
    {collection: "learning_record_billing_continuations", name: "idx_deleted_continuation_command", key: {claimedByCreationCommandId: 1}},
    {collection: "learning_record_deletion_operations", name: "idx_deletion_due", key: {activeGuard: 1, nextAttemptAt: 1, leaseUntil: 1}},
    {collection: "learning_record_deletion_operations", name: "idx_deletion_retention", key: {status: 1, retentionFinalized: 1}},
    ...["operations", "commands", "targets"].map(suffix => ({collection: `learning_record_deletion_${suffix}`,
        name: "ttl_deletion_completed", key: {expiresAt: 1}, expireAfterSeconds: 0})),
    {collection: "learning_activity_daily_aggregates", name: "uq_learning_activity_day", key: {bucketDate: 1, metric: 1, examType: 1, outcome: 1}, unique: true},
    ...["exam_sessions", "challenge_10s_attempts"].map(collection => ({collection, name: "idx_deletion_owner_inventory", key: {userId: 1, _id: 1}})),
    ...["exam_results", "exam_summaries", "question_grading_jobs", "summary_grading_jobs", "azure_results", "speechace_results"]
        .map(collection => ({collection, name: "idx_deletion_exam_cleanup", key: {examId: 1, _id: 1}})),
    ...["challenge_10s_grading_jobs", "challenge_10s_submit_receipts", "challenge_10s_callback_receipts"]
        .map(collection => ({collection, name: "idx_deletion_attempt_cleanup", key: {attemptId: 1, _id: 1}}))
];
// No TTL on unresolved Billing continuations or anonymous daily counts.
const COLLECTIONS = [...new Set(INDEXES.map(i => i.collection))];

function databaseName(value) {
    if (typeof value !== "string" || value.trim() !== value || !value
            || ["admin", "config", "local"].includes(value.toLowerCase())) {
        throw new Error("An explicit non-system database is required");
    }
    return value;
}

function compatible(actual, expected) {
    return JSON.stringify(actual.key) === JSON.stringify(expected.key)
        && Boolean(actual.unique) === Boolean(expected.unique)
        && JSON.stringify(actual.partialFilterExpression) === JSON.stringify(expected.partialFilterExpression)
        && !actual.sparse && !actual.hidden && actual.collation === undefined
        && (expected.expireAfterSeconds === undefined ? actual.expireAfterSeconds === undefined
            : actual.expireAfterSeconds !== undefined && actual.expireAfterSeconds !== null
                && typeof actual.expireAfterSeconds !== "string" && Number(actual.expireAfterSeconds) === expected.expireAfterSeconds);
}

async function inspect(database) {
    const missing = [], conflicts = [];
    for (const collection of COLLECTIONS) {
        let indexes;
        try { indexes = await database.getCollection(collection).getIndexes(); }
        catch (error) { if (error.code !== 26) throw error; indexes = []; }
        if (collection.startsWith("learning_")) {
            for (const actual of indexes.filter(i => i.expireAfterSeconds !== undefined)) {
                if (!INDEXES.some(i => i.collection === collection && i.expireAfterSeconds !== undefined && compatible(actual, i)))
                    conflicts.push(`${collection}.unexpected_ttl`);
            }
        }
        for (const spec of INDEXES.filter(i => i.collection === collection)) {
            const named = indexes.find(i => i.name === spec.name);
            if (named && !compatible(named, spec)) conflicts.push(`${collection}.${spec.name}`);
            else if (!indexes.some(i => compatible(i, spec))) missing.push(spec);
        }
    }
    const duplicates = await database.getCollection("learning_record_deletion_operations").aggregate([
        {$match: {activeGuard: true}}, {$group: {_id: "$userId", count: {$sum: 1}}},
        {$match: {count: {$gt: 1}}}, {$count: "count"}
    ]).toArray();
    let orphanChildren = 0, ownerMismatches = 0;
    for (const spec of INDEXES.filter(i => i.name === "idx_deletion_exam_cleanup" || i.name === "idx_deletion_attempt_cleanup")) {
        const field = spec.name === "idx_deletion_exam_cleanup" ? "examId" : "attemptId";
        const lookup = {$lookup: {from: field === "examId" ? "exam_sessions" : "challenge_10s_attempts",
            localField: field, foreignField: "_id", as: "parent"}};
        const orphan = await database.getCollection(spec.collection).aggregate([lookup,
            {$match: {"parent.0": {$exists: false}}}, {$count: "count"}]).toArray();
        orphanChildren += orphan[0]?.count ?? 0;
        const mismatch = await database.getCollection(spec.collection).aggregate([lookup,
            {$match: {userId: {$exists: true}, "parent.0": {$exists: true},
                $expr: {$ne: ["$userId", {$arrayElemAt: ["$parent.userId", 0]}]}}}, {$count: "count"}]).toArray();
        ownerMismatches += mismatch[0]?.count ?? 0;
    }
    return {missing, conflicts, duplicateActiveOwners: duplicates[0]?.count ?? 0, orphanChildren, ownerMismatches};
}

async function run(database, options) {
    const selected = databaseName(options.databaseName);
    if (database.getName() !== selected) throw new Error("Selected database does not match explicit database name");
    if (options.apply === true && options.writersDrained !== true) throw new Error("Apply requires drained writers");
    const before = await inspect(database);
    if (before.conflicts.length || before.duplicateActiveOwners || before.orphanChildren || before.ownerMismatches)
        throw new Error("Deletion index/inventory preflight failed; no DDL applied");
    if (options.apply !== true) return {mode: "dry-run", ...before};
    for (const {collection, key, ...definition} of before.missing) {
        await database.getCollection(collection).createIndex(key, definition);
    }
    const after = await inspect(database);
    if (after.missing.length || after.conflicts.length || after.duplicateActiveOwners || after.orphanChildren || after.ownerMismatches)
        throw new Error("Deletion index/inventory verification failed");
    return {mode: "apply", ...after};
}

if (typeof module !== "undefined") module.exports = {INDEXES, COLLECTIONS, databaseName, compatible, inspect, run};
if (typeof db !== "undefined" && typeof process !== "undefined") {
    run(db, {databaseName: process.env.MONGODB_DATABASE,
        apply: process.env.LEARNING_RECORD_DELETION_PREPARE_APPLY === "true",
        writersDrained: process.env.LEARNING_RECORD_DELETION_WRITERS_DRAINED === "true"})
        .then(result => print(JSON.stringify(result)))
        .catch(() => { print("Learning record deletion preparation failed; inspect approved schema metadata"); quit(1); });
}
