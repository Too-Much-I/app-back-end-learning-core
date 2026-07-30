"use strict";

const assert = require("node:assert/strict");
const path = require("node:path");
const {spawnSync} = require("node:child_process");
const test = require("node:test");

const migrationPath = path.join(__dirname, "tmi-31-migrate-exam-assignment.js");
const {
    FINAL_RECHECK_EVIDENCE_REASON,
    INDEX_SPECS,
    JAVA_INTEGER_MAX,
    applyLegacyActivationWithFinalRecheck,
    applyLegacyCompletionBackfills,
    buildMigrationPlan,
    inspectCatalog,
    inspectFinalMigrationState,
    inspectIndexes,
    resolveTargetDatabaseName,
    validateApplyPreconditions,
    validateDatabaseName
} = require(migrationPath);

function validMockExam(overrides = {}) {
    return {
        _id: "paper-1",
        mock_exam_id: "mock_exam_001",
        sequence: 1,
        active: true,
        questions: [{question_number: 1}],
        ...overrides
    };
}

function objectIdEvidence(timestamp, hex = "65b9f4000000000000000031") {
    return {
        toHexString() {
            return hex;
        },
        getTimestamp() {
            return new Date(timestamp);
        }
    };
}

function fakeEvidenceCollection(documents) {
    return {
        find(query) {
            const examId = query.examId ?? query.$or?.find(candidate => candidate.examId)?.examId;
            const deterministicId = query.$or?.find(candidate => candidate._id)?._id;
            return {
                toArray() {
                    return documents.filter(document =>
                        document.examId === examId || document._id === deterministicId);
                }
            };
        }
    };
}

function fakeSessionCollection(initialSession) {
    let session = initialSession ? {...initialSession} : null;
    return {
        findOne(query) {
            return session && session._id === query._id ? {...session} : null;
        },
        updateOne(filter, update) {
            const legacyActive = session && (session.active === null || session.active === undefined);
            const legacyCompletedAt = session
                && (session.completedAt === null || session.completedAt === undefined);
            if (!session || session._id !== filter._id || !legacyActive || !legacyCompletedAt) {
                return {modifiedCount: 0};
            }
            session = {...session, ...update.$set};
            return {modifiedCount: 1};
        },
        current() {
            return session ? {...session} : null;
        }
    };
}

function exactIndexesByCollection() {
    return Object.fromEntries(["mock_exams", "exam_sessions"].map(collection => [
        collection,
        INDEX_SPECS.filter(spec => spec.collection === collection).map(spec => ({
            name: spec.name,
            key: spec.key,
            unique: spec.unique,
            partialFilterExpression: spec.partialFilterExpression
        }))
    ]));
}

test("MONGODB_DATABASE is mandatory", () => {
    assert.throws(
        () => resolveTargetDatabaseName("mongodb://cluster.example.test", undefined),
        /MONGODB_DATABASE is required/
    );
});

test("cluster-only URI selects the explicit environment database", () => {
    assert.equal(
        resolveTargetDatabaseName("mongodb://cluster.example.test", "learning-core"),
        "learning-core"
    );
});

test("environment database wins over a different URI database", () => {
    assert.equal(
        resolveTargetDatabaseName("mongodb://cluster.example.test/wrong-db", "learning-core"),
        "learning-core"
    );
});

test("MongoDB system databases are rejected", () => {
    for (const databaseName of ["admin", "local", "config", "ADMIN"]) {
        assert.throws(() => validateDatabaseName(databaseName), /system database/);
    }
});

test("apply requires an explicit legacy-writer-stopped acknowledgement", () => {
    assert.throws(
        () => validateApplyPreconditions(true, undefined),
        /TMI31_LEGACY_WRITER_STOPPED=true is required/
    );
});

test("apply rejects a false legacy-writer-stopped acknowledgement", () => {
    assert.throws(
        () => validateApplyPreconditions(true, "false"),
        /TMI31_LEGACY_WRITER_STOPPED=true is required/
    );
});

test("dry-run does not require a legacy-writer-stopped acknowledgement", () => {
    assert.doesNotThrow(() => validateApplyPreconditions(false, undefined));
});

test("apply accepts the exact legacy-writer-stopped acknowledgement", () => {
    assert.doesNotThrow(() => validateApplyPreconditions(true, "true"));
});

test("node entrypoint fails before mongosh when apply lacks writer-stop acknowledgement", () => {
    const result = spawnSync(process.execPath, [migrationPath], {
        encoding: "utf8",
        env: {
            ...process.env,
            MONGODB_URI: "mongodb://cluster.example.test",
            MONGODB_DATABASE: "learning-core",
            TMI31_APPLY: "true",
            TMI31_LEGACY_WRITER_STOPPED: "",
            TMI31_MONGOSH_PAYLOAD: ""
        }
    });

    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /TMI31_LEGACY_WRITER_STOPPED=true is required/);
});

test("node entrypoint fails before mongosh when apply writer-stop acknowledgement is false", () => {
    const result = spawnSync(process.execPath, [migrationPath], {
        encoding: "utf8",
        env: {
            ...process.env,
            MONGODB_URI: "mongodb://cluster.example.test",
            MONGODB_DATABASE: "learning-core",
            TMI31_APPLY: "true",
            TMI31_LEGACY_WRITER_STOPPED: "false",
            TMI31_MONGOSH_PAYLOAD: ""
        }
    });

    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /TMI31_LEGACY_WRITER_STOPPED=true is required/);
});

test("dry-run planning detects summarized legacy Session and timestamp source", () => {
    const plan = buildMigrationPlan({
        mockExams: [validMockExam()],
        examSessions: [{
            _id: "ex_legacy_001",
            userId: "00000000-0000-0000-0000-000000000031",
            mockExamId: "mock_exam_001"
        }],
        examSummaries: [{
            _id: objectIdEvidence("2024-01-31T06:30:00.000Z"),
            examId: "ex_legacy_001"
        }],
        legacySummaryResults: [],
        indexesByCollection: {}
    });

    assert.equal(plan.legacySessions.legacyCompletedSessionCount, 1);
    assert.equal(plan.legacySessions.backfills.length, 1);
    assert.equal(plan.legacySessions.backfills[0].method, "exam_summaries ObjectId timestamp");
    assert.equal(plan.legacySessions.activateIncompleteLegacy.length, 0);
});

test("dry-run planning detects legacy totalScore completion evidence", () => {
    const plan = buildMigrationPlan({
        mockExams: [validMockExam()],
        examSessions: [{
            _id: "ex_legacy_total_score",
            userId: "user-1",
            mockExamId: "mock_exam_001"
        }],
        examSummaries: [],
        legacySummaryResults: [{
            _id: "legacy-summary-string-id",
            examId: "ex_legacy_total_score",
            totalScore: 120,
            createdAt: new Date("2025-01-01T09:00:00.000Z")
        }],
        indexesByCollection: {}
    });

    assert.equal(plan.legacySessions.legacyResultEvidenceSessionCount, 1);
    assert.equal(plan.legacySessions.summaryEvidenceSessionCount, 0);
    assert.equal(plan.legacySessions.backfills.length, 1);
    assert.equal(plan.legacySessions.backfills[0].method, "exam_results.createdAt");
    assert.equal(plan.legacySessions.activateIncompleteLegacy.length, 0);
});

test("evidence in both collections completes one Session only once", () => {
    const plan = buildMigrationPlan({
        mockExams: [validMockExam()],
        examSessions: [{
            _id: "ex_both_evidence",
            userId: "user-1",
            createdAt: new Date("2024-01-01T00:00:00.000Z")
        }],
        examSummaries: [{
            _id: "summary:ex_both_evidence:v1",
            examId: "ex_both_evidence",
            createdAt: new Date("2025-01-02T00:00:00.000Z")
        }],
        legacySummaryResults: [{
            _id: "legacy-result",
            examId: "ex_both_evidence",
            totalScore: 100,
            completedAt: new Date("2025-01-01T00:00:00.000Z")
        }],
        indexesByCollection: {}
    });

    assert.equal(plan.legacySessions.summaryEvidenceSessionCount, 1);
    assert.equal(plan.legacySessions.legacyResultEvidenceSessionCount, 1);
    assert.equal(plan.legacySessions.bothEvidenceSessionCount, 1);
    assert.equal(plan.legacySessions.backfills.length, 1);
    assert.equal(plan.legacySessions.backfills[0].method, "exam_results.completedAt");
});

test("totalScore null question result is not completion evidence", () => {
    const plan = buildMigrationPlan({
        mockExams: [validMockExam()],
        examSessions: [{_id: "ex_question_only", userId: "user-1"}],
        examSummaries: [],
        legacySummaryResults: [{
            _id: "question-result",
            examId: "ex_question_only",
            totalScore: null,
            questionNumber: 1
        }],
        indexesByCollection: {}
    });

    assert.equal(plan.legacySessions.legacyCompletedSessionCount, 0);
    assert.equal(plan.legacySessions.backfills.length, 0);
    assert.equal(plan.legacySessions.activateIncompleteLegacy.length, 1);
});

test("string evidence id is not interpreted as ObjectId and falls back to Session createdAt", () => {
    const createdAt = new Date("2024-02-01T12:00:00.000Z");
    const plan = buildMigrationPlan({
        mockExams: [validMockExam()],
        examSessions: [{_id: "ex_string_id", userId: "user-1", createdAt}],
        examSummaries: [{
            _id: "65b9f4000000000000000031",
            examId: "ex_string_id"
        }],
        legacySummaryResults: [],
        indexesByCollection: {}
    });

    assert.equal(plan.legacySessions.backfills[0].method, "exam_sessions.createdAt (approximate)");
    assert.equal(plan.legacySessions.backfills[0].completedAt.getTime(), createdAt.getTime());
});

test("completion evidence without any trustworthy timestamp is not activated", () => {
    const plan = buildMigrationPlan({
        mockExams: [validMockExam()],
        examSessions: [{_id: "ex_unresolved", userId: "user-1"}],
        examSummaries: [],
        legacySummaryResults: [{
            _id: "deterministic-result-id",
            examId: "ex_unresolved",
            totalScore: 100
        }],
        indexesByCollection: {}
    });

    assert.equal(plan.legacySessions.unresolvedCompletions.length, 1);
    assert.equal(plan.legacySessions.backfills.length, 0);
    assert.equal(plan.legacySessions.activateIncompleteLegacy.length, 0);
});

test("apply backfill conditionally writes active false and completedAt", () => {
    const completedAt = new Date("2026-07-29T06:30:00.000Z");
    const calls = [];
    const fakeCollection = {
        updateOne(filter, update) {
            calls.push({filter, update});
        }
    };

    applyLegacyCompletionBackfills(fakeCollection, [{
        sessionId: "ex_legacy_001",
        completedAt
    }]);

    assert.equal(calls.length, 1);
    assert.equal(calls[0].filter._id, "ex_legacy_001");
    assert.equal(calls[0].filter.$and.length, 2);
    assert.deepEqual(calls[0].update.$set, {active: false, completedAt});
});

test("final recheck backfills legacy totalScore evidence created after the initial snapshot", () => {
    const session = {
        _id: "ex_late_legacy_result",
        userId: "user-1",
        createdAt: new Date("2025-01-01T00:00:00.000Z")
    };
    const initialPlan = buildMigrationPlan({
        mockExams: [validMockExam()],
        examSessions: [session],
        examSummaries: [],
        legacySummaryResults: [],
        indexesByCollection: exactIndexesByCollection()
    });
    assert.equal(initialPlan.legacySessions.activateIncompleteLegacy.length, 1);

    const sessions = fakeSessionCollection(session);
    const result = applyLegacyActivationWithFinalRecheck(
        sessions,
        fakeEvidenceCollection([]),
        fakeEvidenceCollection([{
            _id: "late-result",
            examId: session._id,
            totalScore: 120,
            completedAt: new Date("2025-02-01T00:00:00.000Z")
        }]),
        initialPlan.legacySessions.activateIncompleteLegacy[0]
    );

    assert.equal(result.outcome, "BACKFILLED_COMPLETION");
    assert.equal(result.reason, FINAL_RECHECK_EVIDENCE_REASON);
    assert.equal(sessions.current().active, false);
    assert.equal(sessions.current().completedAt.toISOString(), "2025-02-01T00:00:00.000Z");
    const finalVerification = inspectFinalMigrationState(
        [sessions.current()],
        [],
        [{examId: session._id, totalScore: 120}],
        exactIndexesByCollection()
    );
    assert.deepEqual(finalVerification.errors, []);
});

test("final recheck backfills ExamSummary evidence created after the initial snapshot", () => {
    const session = {
        _id: "ex_late_summary",
        userId: "user-1",
        createdAt: new Date("2025-01-01T00:00:00.000Z")
    };
    const sessions = fakeSessionCollection(session);
    const result = applyLegacyActivationWithFinalRecheck(
        sessions,
        fakeEvidenceCollection([{
            _id: `summary:${session._id}:v1`,
            createdAt: new Date("2025-03-01T00:00:00.000Z")
        }]),
        fakeEvidenceCollection([]),
        {sessionId: session._id}
    );

    assert.equal(result.outcome, "BACKFILLED_COMPLETION");
    assert.equal(result.reason, FINAL_RECHECK_EVIDENCE_REASON);
    assert.equal(sessions.current().active, false);
    assert.equal(sessions.current().completedAt.toISOString(), "2025-03-01T00:00:00.000Z");
});

test("final recheck activates only a still-legacy Session without completion evidence", () => {
    const sessions = fakeSessionCollection({
        _id: "ex_still_in_progress",
        userId: "user-1"
    });

    const result = applyLegacyActivationWithFinalRecheck(
        sessions,
        fakeEvidenceCollection([]),
        fakeEvidenceCollection([]),
        {sessionId: "ex_still_in_progress"}
    );

    assert.equal(result.outcome, "ACTIVATED");
    assert.equal(sessions.current().active, true);
    assert.equal(sessions.current().completedAt, undefined);
});

test("final recheck does not overwrite a Session whose active value is already set", () => {
    const sessions = fakeSessionCollection({
        _id: "ex_already_inactive",
        userId: "user-1",
        active: false
    });

    const result = applyLegacyActivationWithFinalRecheck(
        sessions,
        fakeEvidenceCollection([]),
        fakeEvidenceCollection([]),
        {sessionId: "ex_already_inactive"}
    );

    assert.equal(result.outcome, "SKIPPED");
    assert.equal(result.reason, "active-already-set");
    assert.equal(sessions.current().active, false);
});

test("final verification rejects active Sessions that have completion evidence", () => {
    const result = inspectFinalMigrationState(
        [{_id: "ex_unsafe", userId: "user-1", active: true}],
        [{_id: "summary:ex_unsafe:v1", examId: "ex_unsafe"}],
        [],
        exactIndexesByCollection()
    );

    assert.equal(result.activeWithEvidence, 1);
    assert.ok(result.errors.some(error => error.includes("active ExamSessions have completion evidence")));
});

test("final verification accepts active Sessions only when completion evidence and completedAt are absent", () => {
    const result = inspectFinalMigrationState(
        [{_id: "ex_safe", userId: "user-1", active: true}],
        [],
        [],
        exactIndexesByCollection()
    );

    assert.deepEqual(result.errors, []);
});

test("final verification rejects multiple active Sessions for one user", () => {
    const result = inspectFinalMigrationState(
        [
            {_id: "ex_active_1", userId: "user-1", active: true},
            {_id: "ex_active_2", userId: "user-1", active: true}
        ],
        [],
        [],
        exactIndexesByCollection()
    );

    assert.equal(result.usersWithMultipleActiveSessions, 1);
    assert.ok(result.errors.some(error => error.includes("multiple active ExamSessions")));
});

test("final verification rejects active Sessions with completedAt", () => {
    const result = inspectFinalMigrationState(
        [{
            _id: "ex_active_completed",
            userId: "user-1",
            active: true,
            completedAt: new Date("2025-01-01T00:00:00.000Z")
        }],
        [],
        [],
        exactIndexesByCollection()
    );

    assert.equal(result.activeWithCompletedAt, 1);
    assert.ok(result.errors.some(error => error.includes("active ExamSessions have completedAt")));
});

test("final verification rejects completion evidence with a null active field", () => {
    const result = inspectFinalMigrationState(
        [{_id: "ex_null_active", userId: "user-1", active: null}],
        [{examId: "ex_null_active"}],
        [],
        exactIndexesByCollection()
    );

    assert.equal(result.completionEvidenceWithUnsafeActive, 1);
    assert.ok(result.errors.some(error => error.includes("active null or true")));
});

test("duplicate Summary evidence is reported and earliest trustworthy time is selected", () => {
    const plan = buildMigrationPlan({
        mockExams: [validMockExam()],
        examSessions: [{
            _id: "ex_duplicate_summary",
            userId: "user-1",
            createdAt: new Date("2024-01-01T00:00:00.000Z")
        }],
        examSummaries: [
            {
                _id: "summary-1",
                examId: "ex_duplicate_summary",
                createdAt: new Date("2025-02-01T00:00:00.000Z")
            },
            {
                _id: "summary-2",
                examId: "ex_duplicate_summary",
                completedAt: new Date("2025-01-01T00:00:00.000Z")
            }
        ],
        legacySummaryResults: [],
        indexesByCollection: {}
    });

    assert.equal(plan.legacySessions.duplicateSummaryExamCount, 1);
    assert.equal(plan.legacySessions.backfills.length, 1);
    assert.equal(plan.legacySessions.backfills[0].method, "exam_summaries.completedAt");
    assert.equal(plan.errors.some(error => error.includes("multiple ExamSummary")), false);
});

test("duplicate legacy totalScore evidence is reported and earliest trustworthy time is selected", () => {
    const plan = buildMigrationPlan({
        mockExams: [validMockExam()],
        examSessions: [{_id: "ex_duplicate_legacy_result", userId: "user-1"}],
        examSummaries: [],
        legacySummaryResults: [
            {
                _id: "legacy-1",
                examId: "ex_duplicate_legacy_result",
                totalScore: 100,
                completedAt: new Date("2025-02-01T00:00:00.000Z")
            },
            {
                _id: "legacy-2",
                examId: "ex_duplicate_legacy_result",
                totalScore: 110,
                createdAt: new Date("2025-01-01T00:00:00.000Z")
            }
        ],
        indexesByCollection: {}
    });

    assert.equal(plan.legacySessions.duplicateLegacyResultExamCount, 1);
    assert.equal(plan.legacySessions.backfills.length, 1);
    assert.equal(plan.legacySessions.backfills[0].method, "exam_results.createdAt");
});

test("deterministic Summary id is completion evidence when examId field is missing", () => {
    const plan = buildMigrationPlan({
        mockExams: [validMockExam()],
        examSessions: [{
            _id: "ex_deterministic_summary",
            userId: "user-1",
            createdAt: new Date("2024-01-01T00:00:00.000Z")
        }],
        examSummaries: [{_id: "summary:ex_deterministic_summary:v1"}],
        legacySummaryResults: [],
        indexesByCollection: {}
    });

    assert.equal(plan.legacySessions.summaryEvidenceSessionCount, 1);
    assert.equal(plan.legacySessions.summaryWithoutExamIdCount, 0);
    assert.equal(plan.legacySessions.backfills.length, 1);
});

test("inactive paper with an unparseable id is excluded without blocking migration", () => {
    const catalog = inspectCatalog([
        validMockExam(),
        validMockExam({
            _id: "retired-paper",
            mock_exam_id: "retired-paper",
            sequence: null,
            active: false
        })
    ]);

    assert.deepEqual(catalog.errors, []);
    assert.equal(catalog.updates.length, 1);
    assert.deepEqual(catalog.excluded[0].reasons, ["INACTIVE"]);
    assert.equal(catalog.excluded[0].sequenceInterpretable, false);
});

test("empty paper with an unparseable id is excluded without blocking migration", () => {
    const catalog = inspectCatalog([
        validMockExam(),
        validMockExam({
            _id: "empty-paper",
            mock_exam_id: "empty-paper",
            sequence: null,
            questions: []
        })
    ]);

    assert.deepEqual(catalog.errors, []);
    assert.equal(catalog.updates.length, 1);
    assert.deepEqual(catalog.excluded[0].reasons, ["EMPTY_QUESTIONS"]);
    assert.equal(catalog.excluded[0].sequenceInterpretable, false);
});

test("assignable paper with an unparseable id still fails sequence validation", () => {
    const catalog = inspectCatalog([
        validMockExam({mock_exam_id: "current-paper", sequence: null})
    ]);

    assert.ok(catalog.errors.some(error => error.includes("sequence cannot be derived")));
});

test("explicit sequence at Java Integer maximum is accepted", () => {
    const catalog = inspectCatalog([
        validMockExam({sequence: JAVA_INTEGER_MAX})
    ]);

    assert.deepEqual(catalog.errors, []);
    assert.equal(catalog.updates[0].sequence, JAVA_INTEGER_MAX);
});

test("explicit sequence above Java Integer maximum is rejected", () => {
    const catalog = inspectCatalog([
        validMockExam({sequence: JAVA_INTEGER_MAX + 1})
    ]);

    assert.ok(catalog.errors.some(error => error.startsWith("JAVA_INTEGER_OVERFLOW:")));
    assert.equal(catalog.updates.length, 0);
});

test("derived sequence at Java Integer maximum is accepted", () => {
    const catalog = inspectCatalog([
        validMockExam({mock_exam_id: `mock_exam_${JAVA_INTEGER_MAX}`, sequence: null})
    ]);

    assert.deepEqual(catalog.errors, []);
    assert.equal(catalog.updates[0].sequence, JAVA_INTEGER_MAX);
});

test("derived sequence above Java Integer maximum is rejected", () => {
    const catalog = inspectCatalog([
        validMockExam({mock_exam_id: "mock_exam_2147483648", sequence: null})
    ]);

    assert.ok(catalog.errors.some(error => error.startsWith("JAVA_INTEGER_OVERFLOW:")));
});

test("safe JavaScript integers above Java Integer maximum are rejected", () => {
    const catalog = inspectCatalog([
        validMockExam({sequence: Number.MAX_SAFE_INTEGER})
    ]);

    assert.ok(catalog.errors.some(error => error.startsWith("JAVA_INTEGER_OVERFLOW:")));
});

test("fractional and numeric-string explicit sequences are rejected as non-integers", () => {
    for (const sequence of [1.5, "2"]) {
        const catalog = inspectCatalog([validMockExam({sequence})]);
        assert.ok(catalog.errors.some(error => error.startsWith("NON_INTEGER_SEQUENCE:")));
    }
});

test("zero and negative explicit sequences are rejected as non-positive", () => {
    for (const sequence of [0, -1]) {
        const catalog = inspectCatalog([validMockExam({sequence})]);
        assert.ok(catalog.errors.some(error => error.startsWith("NON_POSITIVE_SEQUENCE:")));
    }
});

test("excluded papers report overflow as a diagnostic without blocking migration", () => {
    const catalog = inspectCatalog([
        validMockExam(),
        validMockExam({
            _id: "retired-overflow",
            mock_exam_id: "mock_exam_2147483648",
            sequence: null,
            active: false
        })
    ]);

    assert.deepEqual(catalog.errors, []);
    assert.equal(catalog.excluded[0].sequenceDiagnostic, "JAVA_INTEGER_OVERFLOW");
});

test("sequence overflow errors do not expose the full catalog document", () => {
    const catalog = inspectCatalog([
        validMockExam({
            sequence: JAVA_INTEGER_MAX + 1,
            internalMarker: "must-not-be-logged"
        })
    ]);

    assert.equal(catalog.errors.join(" ").includes("must-not-be-logged"), false);
});

test("inactive sequence does not conflict with an assignable sequence", () => {
    const catalog = inspectCatalog([
        validMockExam(),
        validMockExam({
            _id: "retired-paper",
            mock_exam_id: "mock_exam_retired",
            sequence: 1,
            active: false
        })
    ]);

    assert.equal(catalog.errors.some(error => error.includes("sequence 1 is duplicated")), false);
    assert.equal(catalog.updates.length, 1);
});

test("two assignable papers with the same sequence fail migration", () => {
    const catalog = inspectCatalog([
        validMockExam(),
        validMockExam({_id: "paper-2", mock_exam_id: "mock_exam_002", sequence: 1})
    ]);

    assert.ok(catalog.errors.some(error => error.includes("sequence 1 is duplicated")));
});

test("duplicate mock_exam_id remains invalid even when both papers are inactive", () => {
    const catalog = inspectCatalog([
        validMockExam(),
        validMockExam({
            _id: "retired-1",
            mock_exam_id: "retired-paper",
            sequence: null,
            active: false
        }),
        validMockExam({
            _id: "retired-2",
            mock_exam_id: "retired-paper",
            sequence: null,
            active: false
        })
    ]);

    assert.ok(catalog.errors.some(error => error.includes("Duplicate mock_exam_id")));
});

test("excluded papers do not block required index planning or receive updates", () => {
    const plan = buildMigrationPlan({
        mockExams: [
            validMockExam(),
            validMockExam({
                _id: "retired-paper",
                mock_exam_id: "retired-paper",
                sequence: null,
                active: false
            })
        ],
        examSessions: [],
        examSummaries: [],
        legacySummaryResults: [],
        indexesByCollection: {}
    });

    assert.deepEqual(plan.errors, []);
    assert.equal(plan.catalog.updates.length, 1);
    assert.ok(plan.indexes.indexesToCreate.some(index => index.name === "uniq_exam_sessions_active_user"));
});

test("duplicate mock_exam_id blocks migration regardless of sequence", () => {
    const catalog = inspectCatalog([
        validMockExam(),
        validMockExam({_id: "paper-2", sequence: 2})
    ]);

    assert.equal(catalog.duplicateMockExamIds.length, 1);
    assert.ok(catalog.errors.some(error => error.includes("Duplicate mock_exam_id")));
});

test("mock exam unique index uses the mapped Mongo field", () => {
    const mockExamIndex = INDEX_SPECS.find(spec => spec.name === "uniq_mock_exams_mock_exam_id");

    assert.deepEqual(mockExamIndex.key, {mock_exam_id: 1});
    assert.equal(mockExamIndex.unique, true);
});

test("incompatible same-name unique index is rejected", () => {
    const result = inspectIndexes({
        mock_exams: [{
            name: "uniq_mock_exams_mock_exam_id",
            key: {mock_exam_id: 1},
            unique: false
        }],
        exam_sessions: []
    });

    assert.ok(result.errors.some(error => error.includes("uniq_mock_exams_mock_exam_id")));
});

test("validation output never echoes the MongoDB URI", () => {
    const secretUri = "private-uri-marker-that-must-not-appear";
    const result = spawnSync(process.execPath, [migrationPath], {
        encoding: "utf8",
        env: {
            ...process.env,
            MONGODB_URI: secretUri,
            MONGODB_DATABASE: "",
            TMI31_MONGOSH_PAYLOAD: ""
        }
    });
    const output = `${result.stdout}${result.stderr}`;

    assert.notEqual(result.status, 0);
    assert.equal(output.includes(secretUri), false);
});
