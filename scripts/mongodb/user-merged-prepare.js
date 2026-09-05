"use strict";

const SYSTEM_DATABASES = new Set(["admin", "local", "config"]);
const APPLY_ENV = "USER_MERGED_PREPARE_APPLY";
const DRAINED_ENV = "USER_MERGED_LEGACY_WRITERS_DRAINED";
const PAYLOAD_ENV = "USER_MERGED_PREPARE_MONGOSH_PAYLOAD";
const OWNER_COLLECTIONS = ["exam_sessions", "exam_results", "exam_summaries"];
const USER_MERGED_COLLECTIONS = ["user_ownership_guards", "user_merged_inbox_events"];
const INDEX_SPECS = [
    {
        collection: "exam_results",
        name: "idx_exam_results_user",
        key: {userId: 1}
    },
    {
        collection: "exam_summaries",
        name: "idx_exam_summaries_user",
        key: {userId: 1}
    }
];

function environmentValue(name) {
    if (typeof process !== "undefined" && process.env) {
        return process.env[name];
    }
    if (typeof _getEnv === "function") {
        return _getEnv(name);
    }
    return undefined;
}

function validateDatabaseName(value) {
    if (typeof value !== "string" || value.trim() === "") {
        throw new Error("MONGODB_DATABASE is required and must be non-blank");
    }
    if (value !== value.trim()) {
        throw new Error("MONGODB_DATABASE must not contain surrounding whitespace");
    }
    if (SYSTEM_DATABASES.has(value.toLowerCase())) {
        throw new Error("MONGODB_DATABASE must not select a MongoDB system database");
    }
    return value;
}

function validateEnvironment(mongodbUri, databaseName, applyChanges, writersDrained) {
    if (typeof mongodbUri !== "string" || mongodbUri.trim() === "") {
        throw new Error("MONGODB_URI is required");
    }
    const selected = validateDatabaseName(databaseName);
    if (applyChanges && writersDrained !== true) {
        throw new Error(`${DRAINED_ENV}=true is required for apply`);
    }
    return selected;
}

function canonicalUuid(value) {
    return typeof value === "string"
        && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(value);
}

function orderedKeyEquals(left, right) {
    const a = Object.entries(left ?? {});
    const b = Object.entries(right ?? {});
    return a.length === b.length
        && a.every(([field, direction], index) =>
            field === b[index][0] && direction === b[index][1]);
}

function inspectIndexes(indexesByCollection) {
    const errors = [];
    const missing = [];
    for (const spec of INDEX_SPECS) {
        const indexes = indexesByCollection[spec.collection] ?? [];
        const sameName = indexes.find(index => index.name === spec.name);
        const compatible = indexes.find(index => orderedKeyEquals(index.key, spec.key)
            && index.unique !== true
            && index.sparse !== true
            && index.hidden !== true
            && index.partialFilterExpression === undefined
            && index.collation === undefined);
        if (sameName && sameName !== compatible) {
            errors.push(`${spec.collection}.${spec.name} has an incompatible definition`);
        } else if (!compatible) {
            missing.push(spec);
        }
    }
    return {errors, missing};
}

function output(message) {
    if (typeof print === "function") {
        print(message);
    } else {
        console.log(message);
    }
}

async function listIndexesOrEmpty(collection) {
    try {
        return await collection.getIndexes();
    } catch (error) {
        if (error && (error.code === 26 || error.codeName === "NamespaceNotFound")) {
            return [];
        }
        throw error;
    }
}

async function readIndexes(database) {
    const entries = [];
    for (const spec of INDEX_SPECS) {
        entries.push([
            spec.collection,
            await listIndexesOrEmpty(database.getCollection(spec.collection))
        ]);
    }
    return Object.fromEntries(entries);
}

async function distinctOwners(database) {
    const owners = new Set();
    let invalidOwnerCount = 0;
    for (const collectionName of OWNER_COLLECTIONS) {
        const values = await database.getCollection(collectionName).distinct("userId");
        for (const value of values) {
            if (!canonicalUuid(value)) {
                invalidOwnerCount++;
            } else {
                owners.add(value);
            }
        }
    }
    return {owners: [...owners].sort(), invalidOwnerCount};
}

async function activeDuplicateCount(database) {
    const rows = await database.getCollection("exam_sessions").aggregate([
        {$match: {$or: [
            {status: {$in: ["IN_PROGRESS", "ENTITLEMENT_CONFIRMING"]}},
            {$and: [
                {$or: [{status: null}, {status: {$exists: false}}]},
                {$or: [{active: true}, {active: null}, {active: {$exists: false}}]},
                {$or: [{completedAt: null}, {completedAt: {$exists: false}}]}
            ]}
        ]}},
        {$group: {_id: "$userId", count: {$sum: 1}}},
        {$match: {count: {$gt: 1}}},
        {$count: "groups"}
    ]).toArray();
    return rows[0]?.groups ?? 0;
}

async function inventory(database) {
    const ownerInventory = await distinctOwners(database);
    const indexes = inspectIndexes(await readIndexes(database));
    return {
        ...ownerInventory,
        activeDuplicateGroups: await activeDuplicateCount(database),
        existingMergedGuards: await database.getCollection("user_ownership_guards")
            .countDocuments({state: "MERGED"}),
        activeWithdrawalMarkers: await database.getCollection("withdrawn_user_access_denies")
            .countDocuments({blockedUntil: {$gt: new Date()}}),
        nonTerminalOperations: await database.getCollection("exam_creation_operations")
            .countDocuments({activeGuard: true}),
        indexes
    };
}

function blockers(report) {
    const values = [...report.indexes.errors];
    if (report.invalidOwnerCount > 0) {
        values.push("non-canonical owner UUIDs exist");
    }
    if (report.activeDuplicateGroups > 0) {
        values.push("duplicate active Session owner groups exist");
    }
    if (report.existingMergedGuards > 0) {
        values.push("pre-existing MERGED guards require manual review");
    }
    return values;
}

function printReport(databaseName, applyChanges, report) {
    output(`UserMerged prepare mode: ${applyChanges ? "APPLY" : "DRY-RUN"}`);
    output(`Target database: ${databaseName}`);
    output(`Canonical owner count: ${report.owners.length}`);
    output(`Invalid owner count: ${report.invalidOwnerCount}`);
    output(`Duplicate active owner groups: ${report.activeDuplicateGroups}`);
    output(`Existing MERGED guard count: ${report.existingMergedGuards}`);
    output(`Active withdrawal marker count: ${report.activeWithdrawalMarkers}`);
    output(`Non-terminal creation operation count: ${report.nonTerminalOperations}`);
    output(`Missing migration index count: ${report.indexes.missing.length}`);
    for (const problem of blockers(report)) {
        output(`ERROR: ${problem}`);
    }
}

async function applyPreparation(database, report) {
    const now = new Date();
    const existingCollections = new Set(
        (await database.listCollections({}, {nameOnly: true}).toArray())
            .map(collection => collection.name)
    );
    for (const collectionName of USER_MERGED_COLLECTIONS) {
        if (!existingCollections.has(collectionName)) {
            await database.createCollection(collectionName);
        }
    }
    const guards = database.getCollection("user_ownership_guards");
    for (let start = 0; start < report.owners.length; start += 500) {
        const batch = report.owners.slice(start, start + 500);
        if (batch.length > 0) {
            await guards.bulkWrite(batch.map(userId => ({
                updateOne: {
                    filter: {_id: userId},
                    update: {$setOnInsert: {
                        state: "ACTIVE",
                        revision: NumberLong(0),
                        createdAt: now,
                        updatedAt: now
                    }},
                    upsert: true
                }
            })), {ordered: true});
        }
    }
    for (const spec of report.indexes.missing) {
        await database.getCollection(spec.collection).createIndex(spec.key, {name: spec.name});
    }
}

async function verifyPreparation(database, expectedOwners) {
    const collectionNames = new Set(
        (await database.listCollections({}, {nameOnly: true}).toArray())
            .map(collection => collection.name)
    );
    const activeGuardCount = await database.getCollection("user_ownership_guards")
        .countDocuments({_id: {$in: expectedOwners}, state: "ACTIVE"});
    const finalIndexes = inspectIndexes(await readIndexes(database));
    if (USER_MERGED_COLLECTIONS.some(name => !collectionNames.has(name))
            || activeGuardCount !== expectedOwners.length
            || finalIndexes.errors.length > 0 || finalIndexes.missing.length > 0) {
        throw new Error("UserMerged preparation final verification failed");
    }
}

async function runMongoMigration() {
    const mongodbUri = environmentValue("MONGODB_URI");
    const applyChanges = environmentValue(APPLY_ENV) === "true";
    const writersDrained = environmentValue(DRAINED_ENV) === "true";
    const databaseName = validateEnvironment(
        mongodbUri,
        environmentValue("MONGODB_DATABASE"),
        applyChanges,
        writersDrained
    );
    const database = connect(mongodbUri).getSiblingDB(databaseName);
    const report = await inventory(database);
    printReport(databaseName, applyChanges, report);
    if (blockers(report).length > 0) {
        output("No changes were made because dry-run blockers exist.");
        quit(2);
    }
    if (!applyChanges) {
        output(`Dry-run complete. Set ${DRAINED_ENV}=true and ${APPLY_ENV}=true to apply.`);
        quit(0);
    }
    await applyPreparation(database, report);
    await verifyPreparation(database, report.owners);
    output("UserMerged preparation applied and verified successfully.");
    quit(0);
}

function runNodeCli() {
    try {
        validateEnvironment(
            environmentValue("MONGODB_URI"),
            environmentValue("MONGODB_DATABASE"),
            environmentValue(APPLY_ENV) === "true",
            environmentValue(DRAINED_ENV) === "true"
        );
    } catch (error) {
        console.error(`UserMerged preparation validation failed: ${error.message}`);
        process.exitCode = 2;
        return;
    }
    const {spawnSync} = require("node:child_process");
    const child = spawnSync("mongosh", ["--nodb", "--quiet", "--file", __filename], {
        env: {...process.env, [PAYLOAD_ENV]: "true"},
        stdio: "inherit"
    });
    if (child.error) {
        console.error("UserMerged preparation could not start mongosh");
        process.exitCode = 2;
        return;
    }
    process.exitCode = child.status ?? 2;
}

const mongoshPayload = environmentValue(PAYLOAD_ENV) === "true";
if (mongoshPayload) {
    runMongoMigration().catch(error => {
        output(`UserMerged preparation failed: ${error.message}`);
        quit(2);
    });
} else if (typeof require !== "undefined" && require.main === module) {
    runNodeCli();
}

if (typeof module !== "undefined") {
    module.exports = {
        INDEX_SPECS,
        blockers,
        canonicalUuid,
        inspectIndexes,
        orderedKeyEquals,
        validateDatabaseName,
        validateEnvironment
    };
}
