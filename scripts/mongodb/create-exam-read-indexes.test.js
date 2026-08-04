"use strict";

const assert = require("node:assert/strict");
const path = require("node:path");
const {spawnSync} = require("node:child_process");
const test = require("node:test");

const migrationPath = path.join(__dirname, "create-exam-read-indexes.js");
const {
    INDEX_SPECS,
    applyIndexPlan,
    inspectIndexes,
    orderedKeyEquals,
    orderedKeyHasPrefix,
    resolveTargetDatabaseName,
    validateDatabaseName
} = require(migrationPath);

const SUMMARY_INDEX = {
    collection: "exam_summaries",
    name: "idx_exam_summaries_exam_id_latest",
    key: {examId: 1, _id: -1}
};

const NOTIFICATION_INDEXES = [
    {
        collection: "notification_devices",
        name: "uniq_notification_devices_user_installation",
        key: {userId: 1, installationIdHash: 1},
        options: {unique: true}
    },
    {
        collection: "notification_devices",
        name: "uniq_notification_devices_enabled_expo_token",
        key: {expoPushTokenHash: 1},
        options: {unique: true, partialFilterExpression: {enabled: true}}
    },
    {
        collection: "notification_devices",
        name: "idx_notification_devices_user_enabled",
        key: {userId: 1, enabled: 1}
    },
    {
        collection: "notification_outbox",
        name: "uniq_notification_outbox_event_key",
        key: {eventKey: 1},
        options: {unique: true}
    },
    {
        collection: "notification_outbox",
        name: "idx_notification_outbox_claim",
        key: {status: 1, nextAttemptAt: 1, leaseUntil: 1}
    },
    {
        collection: "notification_deliveries",
        name: "uniq_notification_deliveries_notification_device",
        key: {notificationId: 1, deviceId: 1},
        options: {unique: true}
    },
    {
        collection: "notification_deliveries",
        name: "idx_notification_deliveries_claim",
        key: {status: 1, nextAttemptAt: 1, leaseUntil: 1}
    },
    {
        collection: "notification_deliveries",
        name: "idx_notification_deliveries_receipt",
        key: {status: 1, ticketReceivedAt: 1}
    }
];

function existingIndexes(specs = INDEX_SPECS, rename = false) {
    const result = {};
    for (const spec of specs) {
        result[spec.collection] ??= [];
        result[spec.collection].push({
            name: rename ? `existing_${spec.name}` : spec.name,
            key: spec.key,
            ...(spec.options ?? {})
        });
    }
    return result;
}

function recordingDatabase() {
    const calls = [];
    const dropCalls = [];
    const collModCalls = [];
    return {
        calls,
        dropCalls,
        collModCalls,
        database: {
            getCollection(collection) {
                return {
                    createIndex(key, options) {
                        calls.push({collection, key, options});
                    },
                    dropIndex(name) {
                        dropCalls.push({collection, name});
                    }
                };
            },
            runCommand(command) {
                collModCalls.push(command);
            }
        }
    };
}

test("existing four exam read index specs preserve required compound field order", () => {
    assert.deepEqual(INDEX_SPECS, [
        {
            collection: "exam_sessions",
            name: "idx_exam_sessions_user_completed_desc",
            key: {userId: 1, completedAt: -1, _id: -1}
        },
        SUMMARY_INDEX,
        {
            collection: "question_grading_jobs",
            name: "idx_question_grading_jobs_exam_question_retry",
            key: {examId: 1, questionNumber: 1, retryCount: 1}
        },
        {
            collection: "exam_results",
            name: "idx_exam_results_exam_question_retry",
            key: {examId: 1, questionNumber: 1, retryCount: 1}
        },
        ...NOTIFICATION_INDEXES
    ]);
});

test("notification device, outbox, and delivery index plans are exact", () => {
    assert.deepEqual(INDEX_SPECS.slice(4), NOTIFICATION_INDEXES);
    assert.deepEqual(
        INDEX_SPECS.find(spec => spec.name === "uniq_notification_devices_enabled_expo_token"),
        {
            collection: "notification_devices",
            name: "uniq_notification_devices_enabled_expo_token",
            key: {expoPushTokenHash: 1},
            options: {unique: true, partialFilterExpression: {enabled: true}}
        }
    );
});

test("dry-run plans all missing indexes without requiring apply", () => {
    const inspection = inspectIndexes({});

    assert.deepEqual(inspection.errors, []);
    assert.equal(inspection.indexesToCreate.length, 12);
    assert.deepEqual(inspection.indexesToCreate[1], SUMMARY_INDEX);
    assert.deepEqual(inspection.compatibleIndexes, []);
});

test("dry-run never calls createIndex", () => {
    const inspection = inspectIndexes({});
    const recorder = recordingDatabase();

    const created = applyIndexPlan(recorder.database, inspection, false);

    assert.deepEqual(created, []);
    assert.deepEqual(recorder.calls, []);
});

test("apply creates only the missing summary index when all other plans already exist", () => {
    const inspection = inspectIndexes(existingIndexes(
        INDEX_SPECS.filter(spec => spec.collection !== "exam_summaries")
    ));
    const recorder = recordingDatabase();

    const created = applyIndexPlan(recorder.database, inspection, true);

    assert.deepEqual(inspection.indexesToCreate, [SUMMARY_INDEX]);
    assert.equal(inspection.compatibleIndexes.length, 11);
    assert.deepEqual(created, [SUMMARY_INDEX.name]);
    assert.deepEqual(recorder.calls, [{
        collection: "exam_summaries",
        key: {examId: 1, _id: -1},
        options: {name: "idx_exam_summaries_exam_id_latest"}
    }]);
});

test("compatible indexes are idempotent even when an existing name differs", () => {
    const indexesByCollection = existingIndexes(INDEX_SPECS, true);

    const inspection = inspectIndexes(indexesByCollection);

    assert.deepEqual(inspection.errors, []);
    assert.deepEqual(inspection.indexesToCreate, []);
    assert.equal(inspection.compatibleIndexes.length, 12);
});

test("apply preserves required unique and partial options for notification indexes", () => {
    const inspection = inspectIndexes({});
    const recorder = recordingDatabase();

    applyIndexPlan(recorder.database, inspection, true);

    assert.deepEqual(
        recorder.calls.filter(call => call.collection.startsWith("notification_")),
        NOTIFICATION_INDEXES.map(spec => ({
            collection: spec.collection,
            key: spec.key,
            options: {name: spec.name, ...(spec.options ?? {})}
        }))
    );
});

test("notification indexes are idempotent only with matching required options", () => {
    const matching = inspectIndexes(existingIndexes(NOTIFICATION_INDEXES, true));
    assert.deepEqual(matching.errors, []);
    assert.equal(
        matching.indexesToCreate.some(spec => spec.collection.startsWith("notification_")),
        false
    );

    const incompatible = existingIndexes(NOTIFICATION_INDEXES);
    incompatible.notification_devices = incompatible.notification_devices.map(index =>
        index.name === "uniq_notification_devices_enabled_expo_token"
            ? {...index, partialFilterExpression: {enabled: false}}
            : index);
    const inspection = inspectIndexes(incompatible);
    const recorder = recordingDatabase();
    const created = applyIndexPlan(recorder.database, inspection, true);

    assert.equal(inspection.errors.length, 1);
    assert.match(inspection.errors[0], /notification_devices/);
    assert.deepEqual(created, []);
    assert.deepEqual(recorder.calls, []);
});

test("same-name hidden notification index blocks every apply write", () => {
    const target = NOTIFICATION_INDEXES.find(spec =>
        spec.name === "uniq_notification_outbox_event_key");
    const inspection = inspectIndexes({
        notification_outbox: [{
            name: target.name,
            key: target.key,
            unique: true,
            hidden: true
        }]
    });
    const recorder = recordingDatabase();

    const created = applyIndexPlan(recorder.database, inspection, true);

    assert.equal(inspection.errors.length, 1);
    assert.match(inspection.errors[0], /notification_outbox/);
    assert.match(inspection.errors[0], /hidden=true/);
    assert.deepEqual(created, []);
    assert.deepEqual(recorder.calls, []);
    assert.deepEqual(recorder.dropCalls, []);
    assert.deepEqual(recorder.collModCalls, []);
});

test("an existing exact summary index without a hidden field is idempotent", () => {
    const inspection = inspectIndexes({
        exam_summaries: [{name: SUMMARY_INDEX.name, key: SUMMARY_INDEX.key}]
    });

    assert.deepEqual(inspection.errors, []);
    assert.equal(
        inspection.indexesToCreate.some(spec => spec.collection === "exam_summaries"),
        false
    );
    assert.deepEqual(inspection.compatibleIndexes, [{
        collection: "exam_summaries",
        requiredName: SUMMARY_INDEX.name,
        existingName: SUMMARY_INDEX.name
    }]);
});

test("a differently named exact hidden summary index is not compatible and plans a visible index", () => {
    const inspection = inspectIndexes({
        exam_summaries: [{
            name: "existing_hidden_summary_index",
            key: SUMMARY_INDEX.key,
            hidden: true
        }]
    });

    assert.deepEqual(inspection.errors, []);
    assert.deepEqual(
        inspection.indexesToCreate.filter(spec => spec.collection === "exam_summaries"),
        [SUMMARY_INDEX]
    );
    assert.equal(
        inspection.compatibleIndexes.some(index => index.collection === "exam_summaries"),
        false
    );
});

test("a differently named hidden summary prefix is not compatible and plans a visible index", () => {
    const inspection = inspectIndexes({
        exam_summaries: [{
            name: "existing_hidden_summary_prefix",
            key: {examId: 1, _id: -1, createdAt: -1},
            hidden: true
        }]
    });

    assert.deepEqual(inspection.errors, []);
    assert.deepEqual(
        inspection.indexesToCreate.filter(spec => spec.collection === "exam_summaries"),
        [SUMMARY_INDEX]
    );
    assert.equal(
        inspection.compatibleIndexes.some(index => index.collection === "exam_summaries"),
        false
    );
});

test("a same-name hidden summary index fails before any create, drop, or unhide write", () => {
    const inspection = inspectIndexes({
        exam_summaries: [{
            name: SUMMARY_INDEX.name,
            key: SUMMARY_INDEX.key,
            hidden: true
        }]
    });
    const recorder = recordingDatabase();

    const created = applyIndexPlan(recorder.database, inspection, true);

    assert.equal(inspection.errors.length, 1);
    for (const expectedText of [
        "exam_summaries",
        SUMMARY_INDEX.name,
        'expectedKey={"examId":1,"_id":-1}',
        'actualKey={"examId":1,"_id":-1}',
        "hidden=true",
        "no automatic drop or unhide was performed"
    ]) {
        assert.match(inspection.errors[0], new RegExp(expectedText.replace(/[{}]/g, "\\$&")));
    }
    assert.deepEqual(created, []);
    assert.deepEqual(recorder.calls, []);
    assert.deepEqual(recorder.dropCalls, []);
    assert.deepEqual(recorder.collModCalls, []);
});

test("an exact summary index with hidden false remains compatible", () => {
    const inspection = inspectIndexes({
        exam_summaries: [{
            name: "existing_visible_summary_index",
            key: SUMMARY_INDEX.key,
            hidden: false
        }]
    });

    assert.deepEqual(inspection.errors, []);
    assert.equal(
        inspection.indexesToCreate.some(spec => spec.collection === "exam_summaries"),
        false
    );
    assert.equal(inspection.compatibleIndexes[0].existingName, "existing_visible_summary_index");
});

test("a differently named summary index with the same key avoids duplicate creation", () => {
    const inspection = inspectIndexes({
        exam_summaries: [{name: "existing_summary_index", key: SUMMARY_INDEX.key}]
    });

    assert.deepEqual(inspection.errors, []);
    assert.equal(
        inspection.indexesToCreate.some(spec => spec.collection === "exam_summaries"),
        false
    );
    assert.equal(inspection.compatibleIndexes[0].existingName, "existing_summary_index");
});

test("a longer differently named summary index with the required leading key is compatible", () => {
    const longerKey = {examId: 1, _id: -1, createdAt: -1};
    const inspection = inspectIndexes({
        exam_summaries: [{name: "existing_summary_prefix", key: longerKey}]
    });

    assert.equal(orderedKeyHasPrefix(longerKey, SUMMARY_INDEX.key), true);
    assert.deepEqual(inspection.errors, []);
    assert.equal(
        inspection.indexesToCreate.some(spec => spec.collection === "exam_summaries"),
        false
    );
    assert.equal(inspection.compatibleIndexes[0].existingName, "existing_summary_prefix");
});

test("unique, sparse, partial, and collation options remain incompatible", () => {
    const incompatibleOptions = [
        {unique: true},
        {sparse: true},
        {partialFilterExpression: {examId: {$exists: true}}},
        {collation: {locale: "en"}}
    ];

    for (const options of incompatibleOptions) {
        const inspection = inspectIndexes({
            exam_summaries: [{
                name: "existing_incompatible_summary_index",
                key: SUMMARY_INDEX.key,
                ...options
            }]
        });

        assert.deepEqual(inspection.errors, [
            "exam_summaries has the required key with incompatible options"
        ]);
        assert.equal(
            inspection.indexesToCreate.some(spec => spec.collection === "exam_summaries"),
            false
        );
    }
});

test("the required summary index name with another key fails before apply writes", () => {
    const inspection = inspectIndexes({
        exam_summaries: [{
            name: SUMMARY_INDEX.name,
            key: {examId: -1, _id: -1}
        }]
    });
    const recorder = recordingDatabase();

    const created = applyIndexPlan(recorder.database, inspection, true);

    assert.deepEqual(inspection.errors, [
        "exam_summaries.idx_exam_summaries_exam_id_latest exists with an incompatible definition"
    ]);
    assert.deepEqual(created, []);
    assert.deepEqual(recorder.calls, []);
});

test("reverse, reordered, and short summary keys are not compatible", () => {
    const incompatibleKeys = [
        {examId: -1, _id: -1},
        {_id: -1, examId: 1},
        {examId: 1}
    ];

    for (const key of incompatibleKeys) {
        const inspection = inspectIndexes({
            exam_summaries: [{name: "different_name", key}]
        });

        assert.equal(orderedKeyHasPrefix(key, SUMMARY_INDEX.key), false);
        assert.equal(
            inspection.indexesToCreate.some(spec => spec.collection === "exam_summaries"),
            true
        );
    }
});

test("same name, reordered key, and incompatible options fail closed", () => {
    const indexes = {
        ...existingIndexes(NOTIFICATION_INDEXES),
        exam_sessions: [{
            name: "idx_exam_sessions_user_completed_desc",
            key: {completedAt: -1, userId: 1, _id: -1}
        }],
        exam_summaries: [{
            name: "idx_exam_summaries_exam_id_latest",
            key: {examId: 1, createdAt: -1}
        }],
        question_grading_jobs: [{
            name: "another_name",
            key: {examId: 1, questionNumber: 1, retryCount: 1},
            partialFilterExpression: {status: "COMPLETED"}
        }],
        exam_results: [{
            name: "another_result_name",
            key: {examId: 1, questionNumber: 1, retryCount: 1},
            unique: true
        }]
    };
    const inspection = inspectIndexes(indexes);

    assert.equal(inspection.errors.length, 4);
    assert.deepEqual(inspection.indexesToCreate, []);
});

test("ordered index comparison rejects the same fields in another order", () => {
    assert.equal(
        orderedKeyEquals(
            {examId: 1, questionNumber: 1, retryCount: 1},
            {examId: 1, retryCount: 1, questionNumber: 1}
        ),
        false
    );
});

test("database selection requires an explicit non-system database and never trusts URI path", () => {
    assert.equal(
        resolveTargetDatabaseName("mongodb://cluster.example.test/wrong", "learning-core"),
        "learning-core"
    );
    assert.throws(() => resolveTargetDatabaseName(undefined, "learning-core"), /MONGODB_URI/);
    assert.throws(() => validateDatabaseName("admin"), /system database/);
    assert.throws(() => validateDatabaseName(" learning-core"), /whitespace/);
});

test("node entrypoint fails validation before invoking mongosh when URI is absent", () => {
    const environment = {...process.env};
    delete environment.MONGODB_URI;
    environment.MONGODB_DATABASE = "learning-core";
    environment.EXAM_READ_INDEXES_MONGOSH_PAYLOAD = "";

    const result = spawnSync(process.execPath, [migrationPath], {
        encoding: "utf8",
        env: environment
    });

    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /MONGODB_URI is required/);
});
