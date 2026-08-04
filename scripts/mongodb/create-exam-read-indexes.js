"use strict";

const SYSTEM_DATABASES = new Set(["admin", "local", "config"]);
const APPLY_ENV = "EXAM_READ_INDEXES_APPLY";
const PAYLOAD_ENV = "EXAM_READ_INDEXES_MONGOSH_PAYLOAD";
const INDEX_SPECS = [
    {
        collection: "exam_sessions",
        name: "idx_exam_sessions_user_completed_desc",
        key: {userId: 1, completedAt: -1, _id: -1}
    },
    {
        collection: "exam_summaries",
        name: "idx_exam_summaries_exam_id_latest",
        key: {examId: 1, _id: -1}
    },
    {
        collection: "question_grading_jobs",
        name: "idx_question_grading_jobs_exam_question_retry",
        key: {examId: 1, questionNumber: 1, retryCount: 1}
    },
    {
        collection: "exam_results",
        name: "idx_exam_results_exam_question_retry",
        key: {examId: 1, questionNumber: 1, retryCount: 1}
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
        throw new Error("MONGODB_DATABASE must not contain leading or trailing whitespace");
    }
    if (SYSTEM_DATABASES.has(value.toLowerCase())) {
        throw new Error("MONGODB_DATABASE must not select a MongoDB system database");
    }
    return value;
}

function resolveTargetDatabaseName(mongodbUri, databaseName) {
    if (typeof mongodbUri !== "string" || mongodbUri.trim() === "") {
        throw new Error("MONGODB_URI is required");
    }
    return validateDatabaseName(databaseName);
}

function orderedKeyEquals(left, right) {
    if (!left || !right) {
        return false;
    }
    const leftEntries = Object.entries(left);
    const rightEntries = Object.entries(right);
    return leftEntries.length === rightEntries.length
        && leftEntries.every(([field, direction], index) =>
            field === rightEntries[index][0] && direction === rightEntries[index][1]);
}

function orderedKeyHasPrefix(candidate, requiredPrefix) {
    if (!candidate || !requiredPrefix) {
        return false;
    }
    const candidateEntries = Object.entries(candidate);
    const requiredEntries = Object.entries(requiredPrefix);
    return candidateEntries.length >= requiredEntries.length
        && requiredEntries.every(([field, direction], index) =>
            field === candidateEntries[index][0] && direction === candidateEntries[index][1]);
}

function hasIncompatibleOptions(index) {
    return index.unique === true
        || index.sparse === true
        || index.partialFilterExpression !== undefined
        || index.collation !== undefined
        || index.hidden === true;
}

function hiddenIndexConflictMessage(spec, index) {
    const expectedKey = JSON.stringify(spec.key ?? null);
    const actualKey = JSON.stringify(index.key ?? null);
    return `${spec.collection}.${spec.name} conflicts with a hidden index: `
        + `expectedKey=${expectedKey}, actualKey=${actualKey}, hidden=true; `
        + "no automatic drop or unhide was performed";
}

function inspectIndexes(indexesByCollection) {
    const errors = [];
    const indexesToCreate = [];
    const compatibleIndexes = [];

    for (const spec of INDEX_SPECS) {
        const existingIndexes = indexesByCollection[spec.collection] ?? [];
        const sameName = existingIndexes.find(index => index.name === spec.name);
        const keyCandidates = existingIndexes.filter(index =>
            orderedKeyEquals(index.key, spec.key)
            || (spec.collection === "exam_summaries"
                && orderedKeyHasPrefix(index.key, spec.key)));
        const compatibleKey = keyCandidates.find(index => !hasIncompatibleOptions(index));
        const incompatibleVisibleKey = keyCandidates.find(index =>
            index.hidden !== true && hasIncompatibleOptions(index));

        if (sameName && sameName.hidden === true) {
            errors.push(hiddenIndexConflictMessage(spec, sameName));
            continue;
        }
        if (sameName && (!orderedKeyEquals(sameName.key, spec.key)
                || hasIncompatibleOptions(sameName))) {
            errors.push(`${spec.collection}.${spec.name} exists with an incompatible definition`);
            continue;
        }
        if (compatibleKey) {
            if (hasIncompatibleOptions(compatibleKey)) {
                errors.push(`${spec.collection} has the required key with incompatible options`);
                continue;
            }
            compatibleIndexes.push({
                collection: spec.collection,
                requiredName: spec.name,
                existingName: compatibleKey.name
            });
            continue;
        }
        if (incompatibleVisibleKey) {
            errors.push(`${spec.collection} has the required key with incompatible options`);
            continue;
        }
        indexesToCreate.push(spec);
    }

    return {errors, indexesToCreate, compatibleIndexes};
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

function output(line) {
    if (typeof print === "function") {
        print(line);
    } else {
        console.log(line);
    }
}

function printPlan(databaseName, applyChanges, inspection) {
    output(`Exam read index mode: ${applyChanges ? "APPLY" : "DRY-RUN"}`);
    output(`Target database: ${databaseName}`);
    output(`Target collections: ${INDEX_SPECS.map(spec => spec.collection).join(", ")}`);
    output(`Compatible existing indexes: ${inspection.compatibleIndexes.length}`);
    output(`Indexes to create: ${inspection.indexesToCreate.length}`);
    for (const spec of inspection.indexesToCreate) {
        output(`CREATE: ${spec.collection}.${spec.name}`);
    }
    for (const error of inspection.errors) {
        output(`ERROR: ${error}`);
    }
}

function applyIndexPlan(database, inspection, applyChanges) {
    if (!applyChanges || inspection.errors.length > 0) {
        return [];
    }
    return inspection.indexesToCreate.map(spec => {
        database.getCollection(spec.collection).createIndex(spec.key, {name: spec.name});
        return spec.name;
    });
}

async function runMongoMigration() {
    const mongodbUri = environmentValue("MONGODB_URI");
    const databaseName = resolveTargetDatabaseName(
        mongodbUri,
        environmentValue("MONGODB_DATABASE")
    );
    const applyChanges = environmentValue(APPLY_ENV) === "true";
    const targetDatabase = connect(mongodbUri).getSiblingDB(databaseName);
    const inspection = inspectIndexes(await readIndexes(targetDatabase));
    printPlan(databaseName, applyChanges, inspection);

    if (inspection.errors.length > 0) {
        output("No indexes were created because validation failed.");
        quit(2);
    }
    if (!applyChanges) {
        output(`Dry-run complete. Set ${APPLY_ENV}=true explicitly to create missing indexes.`);
        quit(0);
    }

    applyIndexPlan(targetDatabase, inspection, true);

    const finalInspection = inspectIndexes(await readIndexes(targetDatabase));
    if (finalInspection.errors.length > 0 || finalInspection.indexesToCreate.length > 0) {
        output("Exam read index final verification failed.");
        quit(3);
    }
    output("Exam read indexes applied and verified successfully.");
    quit(0);
}

function runNodeCli() {
    try {
        resolveTargetDatabaseName(
            environmentValue("MONGODB_URI"),
            environmentValue("MONGODB_DATABASE")
        );
    } catch (validationError) {
        console.error(`Exam read index validation failed: ${validationError.message}`);
        process.exitCode = 2;
        return;
    }

    const {spawnSync} = require("node:child_process");
    const child = spawnSync(
        "mongosh",
        ["--nodb", "--quiet", "--file", __filename],
        {
            env: {...process.env, [PAYLOAD_ENV]: "true"},
            stdio: "inherit"
        }
    );
    if (child.error) {
        console.error("Exam read index migration could not start mongosh");
        process.exitCode = 2;
        return;
    }
    process.exitCode = child.status ?? 2;
}

const mongoshPayload = environmentValue(PAYLOAD_ENV) === "true";
if (mongoshPayload) {
    runMongoMigration().catch(error => {
        throw error;
    });
} else if (typeof module !== "undefined") {
    module.exports = {
        INDEX_SPECS,
        applyIndexPlan,
        inspectIndexes,
        listIndexesOrEmpty,
        orderedKeyEquals,
        orderedKeyHasPrefix,
        readIndexes,
        resolveTargetDatabaseName,
        validateDatabaseName
    };
    if (require.main === module) {
        runNodeCli();
    }
}
