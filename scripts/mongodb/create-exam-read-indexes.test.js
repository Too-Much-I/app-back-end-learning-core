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
    listIndexesOrEmpty,
    orderedKeyEquals,
    orderedKeyHasPrefix,
    readIndexes,
    resolveTargetDatabaseName,
    validateDatabaseName
} = require(migrationPath);

const SUMMARY_INDEX = {
    collection: "exam_summaries",
    name: "idx_exam_summaries_exam_id_latest",
    key: {examId: 1, _id: -1}
};

function recordingDatabase() {
    const calls = [];
    const createCollectionCalls = [];
    const dropCalls = [];
    const collModCalls = [];
    return {
        calls,
        createCollectionCalls,
        dropCalls,
        collModCalls,
        database: {
            createCollection(collection) {
                createCollectionCalls.push(collection);
            },
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

function indexReadingDatabase(indexesByCollection, errorsByCollection = {}) {
    return {
        getCollection(collection) {
            return {
                async getIndexes() {
                    if (Object.prototype.hasOwnProperty.call(errorsByCollection, collection)) {
                        throw errorsByCollection[collection];
                    }
                    return indexesByCollection[collection] ?? [];
                }
            };
        }
    };
}

async function inspectionWithMissingSummary(error) {
    const existingIndexes = Object.fromEntries(
        INDEX_SPECS
            .filter(spec => spec.collection !== "exam_summaries")
            .map(spec => [spec.collection, [{name: spec.name, key: spec.key}]])
    );
    const database = indexReadingDatabase(existingIndexes, {exam_summaries: error});
    return inspectIndexes(await readIndexes(database));
}

test("exam read index specs preserve required compound field order", () => {
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
        }
    ]);
});

test("dry-run plans all missing indexes without requiring apply", () => {
    const inspection = inspectIndexes({});

    assert.deepEqual(inspection.errors, []);
    assert.equal(inspection.indexesToCreate.length, 4);
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

test("listIndexes code 26 is treated as an empty index list", async () => {
    const namespaceNotFound = Object.assign(new Error("synthetic missing namespace"), {code: 26});

    const indexes = await listIndexesOrEmpty({
        async getIndexes() {
            throw namespaceNotFound;
        }
    });

    assert.deepEqual(indexes, []);
});

test("listIndexes NamespaceNotFound codeName is treated as an empty index list", async () => {
    const namespaceNotFound = Object.assign(
        new Error("synthetic missing namespace"),
        {codeName: "NamespaceNotFound"}
    );

    const indexes = await listIndexesOrEmpty({
        async getIndexes() {
            throw namespaceNotFound;
        }
    });

    assert.deepEqual(indexes, []);
});

test("a missing exam_summaries collection remains in the dry-run creation plan", async () => {
    const inspection = await inspectionWithMissingSummary(
        Object.assign(new Error("synthetic missing namespace"), {code: 26})
    );

    assert.deepEqual(inspection.errors, []);
    assert.deepEqual(inspection.indexesToCreate, [SUMMARY_INDEX]);
    assert.equal(inspection.compatibleIndexes.length, 3);
});

test("dry-run with a missing collection performs no index or collection writes", async () => {
    const inspection = await inspectionWithMissingSummary(
        Object.assign(new Error("synthetic missing namespace"), {code: 26})
    );
    const recorder = recordingDatabase();

    const created = applyIndexPlan(recorder.database, inspection, false);

    assert.deepEqual(created, []);
    assert.deepEqual(recorder.createCollectionCalls, []);
    assert.deepEqual(recorder.calls, []);
    assert.deepEqual(recorder.dropCalls, []);
    assert.deepEqual(recorder.collModCalls, []);
});

test("apply creates the required index for a missing collection through createIndex", async () => {
    const inspection = await inspectionWithMissingSummary(
        Object.assign(new Error("synthetic missing namespace"), {code: 26})
    );
    const recorder = recordingDatabase();

    const created = applyIndexPlan(recorder.database, inspection, true);

    assert.deepEqual(created, [SUMMARY_INDEX.name]);
    assert.deepEqual(recorder.createCollectionCalls, []);
    assert.deepEqual(recorder.calls, [{
        collection: SUMMARY_INDEX.collection,
        key: SUMMARY_INDEX.key,
        options: {name: SUMMARY_INDEX.name}
    }]);
});

test("authentication errors from listIndexes are propagated unchanged", async () => {
    const authenticationFailure = Object.assign(
        new Error("synthetic authentication failure"),
        {code: 18, codeName: "AuthenticationFailed"}
    );

    await assert.rejects(
        () => listIndexesOrEmpty({
            async getIndexes() {
                throw authenticationFailure;
            }
        }),
        error => error === authenticationFailure
    );
});

test("network errors from listIndexes are propagated unchanged", async () => {
    const networkFailure = Object.assign(
        new Error("synthetic network failure"),
        {name: "MongoNetworkError"}
    );

    await assert.rejects(
        () => listIndexesOrEmpty({
            async getIndexes() {
                throw networkFailure;
            }
        }),
        error => error === networkFailure
    );
});

test("unknown MongoDB errors from listIndexes are propagated unchanged", async () => {
    const unknownFailure = Object.assign(
        new Error("synthetic unknown MongoDB failure"),
        {code: 99999, codeName: "SyntheticFailure"}
    );

    await assert.rejects(
        () => listIndexesOrEmpty({
            async getIndexes() {
                throw unknownFailure;
            }
        }),
        error => error === unknownFailure
    );
});

test("apply creates only the missing summary index when the other three already exist", () => {
    const existingThree = Object.fromEntries(
        INDEX_SPECS
            .filter(spec => spec.collection !== "exam_summaries")
            .map(spec => [spec.collection, [{name: spec.name, key: spec.key}]])
    );
    const inspection = inspectIndexes(existingThree);
    const recorder = recordingDatabase();

    const created = applyIndexPlan(recorder.database, inspection, true);

    assert.deepEqual(inspection.indexesToCreate, [SUMMARY_INDEX]);
    assert.equal(inspection.compatibleIndexes.length, 3);
    assert.deepEqual(created, [SUMMARY_INDEX.name]);
    assert.deepEqual(recorder.calls, [{
        collection: "exam_summaries",
        key: {examId: 1, _id: -1},
        options: {name: "idx_exam_summaries_exam_id_latest"}
    }]);
});

test("compatible indexes are idempotent even when an existing name differs", () => {
    const indexesByCollection = Object.fromEntries(INDEX_SPECS.map(spec => [
        spec.collection,
        [{name: `existing_${spec.name}`, key: spec.key}]
    ]));

    const inspection = inspectIndexes(indexesByCollection);

    assert.deepEqual(inspection.errors, []);
    assert.deepEqual(inspection.indexesToCreate, []);
    assert.equal(inspection.compatibleIndexes.length, 4);
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
    const inspection = inspectIndexes({
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
    });

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
