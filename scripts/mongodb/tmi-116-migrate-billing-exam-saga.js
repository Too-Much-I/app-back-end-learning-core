"use strict";

const SYSTEM_DATABASES = new Set(["admin", "local", "config"]);
const APPLY_ENV = "TMI116_BILLING_SAGA_INDEXES_APPLY";
const INDEX_SPECS = [
    {
        collection: "exam_creation_operations",
        name: "uniq_exam_creation_user_operation",
        key: {userId: 1, operationId: 1},
        options: {unique: true}
    },
    {
        collection: "exam_creation_operations",
        name: "uniq_exam_creation_active_user",
        key: {userId: 1, activeGuard: 1},
        options: {unique: true, partialFilterExpression: {activeGuard: true}}
    },
    {
        collection: "exam_creation_operations",
        name: "idx_exam_creation_state_updated",
        key: {state: 1, updatedAt: 1},
        options: {}
    },
    {
        collection: "exam_creation_operations",
        name: "ttl_exam_creation_purge",
        key: {purgeAt: 1},
        options: {
            expireAfterSeconds: 0,
            partialFilterExpression: {purgeAt: {$type: "date"}}
        }
    },
    {
        collection: "exam_sessions",
        name: "uniq_exam_sessions_creation_operation",
        key: {userId: 1, creationOperationId: 1},
        options: {
            unique: true,
            partialFilterExpression: {creationOperationId: {$type: "string"}}
        }
    },
    {
        collection: "exam_sessions",
        name: "uniq_exam_sessions_billing_reservation",
        key: {billingReservationId: 1},
        options: {
            unique: true,
            partialFilterExpression: {billingReservationId: {$type: "string"}}
        }
    },
    {
        collection: "exam_sessions",
        name: "idx_exam_sessions_attempt_group_created",
        key: {attemptGroupId: 1, createdAt: 1},
        options: {}
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

function requiredDatabaseName(value) {
    if (typeof value !== "string" || value.trim() === "" || value !== value.trim()) {
        throw new Error("MONGODB_DATABASE is required and must not contain surrounding whitespace");
    }
    if (SYSTEM_DATABASES.has(value.toLowerCase())) {
        throw new Error("MONGODB_DATABASE must not select a MongoDB system database");
    }
    return value;
}

function orderedJson(value) {
    if (value === undefined) {
        return undefined;
    }
    if (value === null || typeof value !== "object" || Array.isArray(value)) {
        return value;
    }
    return Object.fromEntries(Object.entries(value).map(([key, nested]) => [key, orderedJson(nested)]));
}

function sameDocument(left, right) {
    return JSON.stringify(orderedJson(left ?? null)) === JSON.stringify(orderedJson(right ?? null));
}

function compatible(existing, spec) {
    return sameDocument(existing.key, spec.key)
        && Boolean(existing.unique) === Boolean(spec.options.unique)
        && Boolean(existing.sparse) === false
        && existing.hidden !== true
        && sameDocument(existing.partialFilterExpression, spec.options.partialFilterExpression)
        && (spec.options.expireAfterSeconds === undefined
            || Number(existing.expireAfterSeconds) === spec.options.expireAfterSeconds);
}

async function indexesOrEmpty(collection) {
    try {
        return await collection.getIndexes();
    } catch (error) {
        if (error && (error.code === 26 || error.codeName === "NamespaceNotFound")) {
            return [];
        }
        throw error;
    }
}

function output(message) {
    if (typeof print === "function") {
        print(message);
    } else {
        console.log(message);
    }
}

async function main() {
    if (!environmentValue("MONGODB_URI")) {
        throw new Error("MONGODB_URI is required");
    }
    const databaseName = requiredDatabaseName(environmentValue("MONGODB_DATABASE"));
    const apply = environmentValue(APPLY_ENV) === "true";
    const targetDb = db.getSiblingDB(databaseName);
    const toCreate = [];

    for (const spec of INDEX_SPECS) {
        const indexes = await indexesOrEmpty(targetDb.getCollection(spec.collection));
        const sameName = indexes.find(index => index.name === spec.name);
        if (sameName && !compatible(sameName, spec)) {
            throw new Error(`${spec.collection}.${spec.name} has an incompatible definition`);
        }
        if (!sameName) {
            toCreate.push(spec);
        }
    }

    output(`TMI-116 Billing saga index mode: ${apply ? "APPLY" : "DRY-RUN"}`);
    output(`Target database: ${databaseName}`);
    output(`Indexes to create: ${toCreate.length}`);
    if (!apply) {
        return;
    }
    for (const spec of toCreate) {
        await targetDb.getCollection(spec.collection).createIndex(
            spec.key,
            {...spec.options, name: spec.name}
        );
    }
    output("TMI-116 Billing saga index migration completed");
}

main().catch(error => {
    output(`TMI-116 Billing saga index migration failed: ${error.message}`);
    if (typeof process !== "undefined") {
        process.exitCode = 1;
    }
    throw error;
});
