"use strict";

const LEGACY_MOCK_EXAM_ID = "mock_exam_003";
const JAVA_INTEGER_MAX = 2147483647;
const FINAL_RECHECK_EVIDENCE_REASON = "completion-evidence-detected-during-final-recheck";
const SYSTEM_DATABASES = new Set(["admin", "local", "config"]);
const COLLECTION_NAMES = [
    "mock_exams",
    "exam_sessions",
    "exam_summaries",
    "exam_results"
];
const INDEX_SPECS = [
    {
        collection: "exam_sessions",
        name: "uniq_exam_sessions_active_user",
        key: {userId: 1},
        unique: true,
        partialFilterExpression: {active: true}
    },
    {
        collection: "exam_sessions",
        name: "idx_exam_sessions_user_completed_mock_exam",
        key: {userId: 1, completedAt: 1, mockExamId: 1},
        unique: false
    },
    {
        collection: "mock_exams",
        name: "uniq_mock_exams_mock_exam_id",
        key: {mock_exam_id: 1},
        unique: true
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
    // The environment database always wins, even when the URI contains another database.
    return validateDatabaseName(databaseName);
}

function validateApplyPreconditions(applyChanges, legacyWriterStoppedValue) {
    if (applyChanges && legacyWriterStoppedValue !== "true") {
        throw new Error(
            "TMI31_LEGACY_WRITER_STOPPED=true is required when TMI31_APPLY=true"
        );
    }
}

function hasValue(value) {
    return value !== null && value !== undefined;
}

function isLegacyUnset(value) {
    return value === null || value === undefined;
}

function documentId(document) {
    return hasValue(document._id) ? document._id : document.examId;
}

function idText(value) {
    if (typeof value === "string") {
        return value;
    }
    if (value && typeof value.toHexString === "function") {
        return value.toHexString();
    }
    return String(value);
}

function summaryExamId(summary) {
    if (typeof summary.examId === "string" && summary.examId.trim() !== "") {
        return summary.examId;
    }
    if (typeof summary._id !== "string") {
        return null;
    }
    const deterministicId = summary._id.match(/^summary:(.+):v1$/);
    return deterministicId ? deterministicId[1] : null;
}

function asDate(value) {
    if (!hasValue(value)) {
        return null;
    }
    const converted = value instanceof Date ? new Date(value.getTime()) : new Date(value);
    return Number.isNaN(converted.getTime()) ? null : converted;
}

function objectIdTimestamp(value) {
    if (!value || typeof value !== "object" || typeof value.toHexString !== "function") {
        return null;
    }
    if (typeof value.getTimestamp === "function") {
        return asDate(value.getTimestamp());
    }
    const hex = value.toHexString();
    if (typeof hex !== "string" || !/^[0-9a-fA-F]{24}$/.test(hex)) {
        return null;
    }
    const seconds = Number.parseInt(hex.slice(0, 8), 16);
    return Number.isSafeInteger(seconds) ? new Date(seconds * 1000) : null;
}

function activeValue(mockExam) {
    return isLegacyUnset(mockExam.active) ? true : mockExam.active;
}

function hasAssignableQuestion(mockExam) {
    return Array.isArray(mockExam.questions)
        && mockExam.questions.some(question => {
            const number = Number.isInteger(question?.question_number)
                ? question.question_number
                : question?.questionNumber;
            return Number.isInteger(number) && number > 0;
        });
}

function inspectSequence(mockExam) {
    if (hasValue(mockExam.sequence)) {
        if (!Number.isInteger(mockExam.sequence)) {
            return {
                explicit: true,
                interpretable: false,
                sequence: null,
                errorCode: "NON_INTEGER_SEQUENCE",
                error: "MockExam sequence must be an integer"
            };
        }
        if (mockExam.sequence < 1) {
            return {
                explicit: true,
                interpretable: false,
                sequence: null,
                errorCode: "NON_POSITIVE_SEQUENCE",
                error: "MockExam sequence must be at least 1"
            };
        }
        if (mockExam.sequence > JAVA_INTEGER_MAX) {
            return {
                explicit: true,
                interpretable: false,
                sequence: null,
                errorCode: "JAVA_INTEGER_OVERFLOW",
                error: "MockExam sequence exceeds the Java Integer range"
            };
        }
        return {
            explicit: true,
            interpretable: true,
            sequence: mockExam.sequence,
            errorCode: null,
            error: null
        };
    }

    const mockExamId = mockExam.mock_exam_id;
    const match = typeof mockExamId === "string" ? mockExamId.match(/(\d+)$/) : null;
    if (!match) {
        return {
            explicit: false,
            interpretable: false,
            sequence: null,
            errorCode: "UNPARSABLE_SEQUENCE_SUFFIX",
            error: "MockExam sequence cannot be derived from mock_exam_id"
        };
    }
    const derived = Number.parseInt(match[1], 10);
    if (!Number.isSafeInteger(derived)) {
        return {
            explicit: false,
            interpretable: false,
            sequence: null,
            errorCode: "JAVA_INTEGER_OVERFLOW",
            error: "Derived MockExam sequence exceeds the Java Integer range"
        };
    }
    if (derived < 1) {
        return {
            explicit: false,
            interpretable: false,
            sequence: null,
            errorCode: "NON_POSITIVE_SEQUENCE",
            error: "Derived MockExam sequence must be at least 1"
        };
    }
    if (derived > JAVA_INTEGER_MAX) {
        return {
            explicit: false,
            interpretable: false,
            sequence: null,
            errorCode: "JAVA_INTEGER_OVERFLOW",
            error: "Derived MockExam sequence exceeds the Java Integer range"
        };
    }
    return {
        explicit: false,
        interpretable: true,
        sequence: derived,
        errorCode: null,
        error: null
    };
}

function deriveSequence(mockExam, errors) {
    const inspection = inspectSequence(mockExam);
    if (!inspection.interpretable) {
        errors.push(`${inspection.errorCode}: ${inspection.error}`);
        return null;
    }
    return inspection.sequence;
}

function inspectCatalog(mockExams) {
    const errors = [];
    const warnings = [];
    const updates = [];
    const byMockExamId = new Map();
    const activeSequences = new Map();
    const excluded = [];
    let invalidMockExamIdCount = 0;

    for (const mockExam of mockExams) {
        const mockExamId = mockExam?.mock_exam_id;
        const validMockExamId = typeof mockExamId === "string"
            && mockExamId.trim() !== ""
            && mockExamId === mockExamId.trim();
        const active = activeValue(mockExam);
        const hasQuestions = hasAssignableQuestion(mockExam);
        const sequenceInspection = inspectSequence(mockExam);
        const exclusionReasons = [];

        if (!validMockExamId) {
            exclusionReasons.push("MISSING_ID");
        }
        if (typeof active !== "boolean") {
            exclusionReasons.push("INVALID_ACTIVE");
        } else if (!active) {
            exclusionReasons.push("INACTIVE");
        }
        if (!hasQuestions) {
            exclusionReasons.push("EMPTY_QUESTIONS");
        }

        if (!validMockExamId) {
            if (typeof mockExamId === "string"
                && mockExamId.trim() !== ""
                && mockExamId !== mockExamId.trim()) {
                errors.push("MockExam mock_exam_id must be trimmed");
            } else {
                errors.push("MockExam mock_exam_id must be non-blank");
            }
            invalidMockExamIdCount += 1;
        } else {
            const duplicateMetadata = byMockExamId.get(mockExamId) ?? [];
            duplicateMetadata.push({
                sequence: hasValue(mockExam.sequence) ? mockExam.sequence : null,
                active
            });
            byMockExamId.set(mockExamId, duplicateMetadata);
        }

        if (typeof active !== "boolean") {
            errors.push("MockExam active must be boolean");
        }

        if (exclusionReasons.length > 0) {
            excluded.push({
                mockExamId: validMockExamId ? mockExamId : "<missing>",
                reasons: exclusionReasons,
                sequenceExplicit: sequenceInspection.explicit,
                sequenceInterpretable: sequenceInspection.interpretable,
                sequenceDiagnostic: sequenceInspection.errorCode ?? "VALID"
            });
            if (active === true && !hasQuestions && validMockExamId) {
                warnings.push(`MockExam ${mockExamId} is active but empty; runtime assignment excludes it`);
            }
            continue;
        }

        const sequence = deriveSequence(mockExam, errors);
        if (sequence === null) {
            continue;
        }

        if (activeSequences.has(sequence)) {
            errors.push(`Active MockExam sequence ${sequence} is duplicated`);
        } else {
            activeSequences.set(sequence, mockExamId);
        }
        updates.push({documentId: documentId(mockExam), sequence, active: true});
    }

    const duplicateMockExamIds = [];
    for (const [mockExamId, documents] of byMockExamId.entries()) {
        if (documents.length > 1) {
            duplicateMockExamIds.push({mockExamId, documents});
            errors.push(`Duplicate mock_exam_id detected: ${mockExamId}`);
        }
    }
    if (activeSequences.size === 0) {
        errors.push("No active non-empty MockExam is assignable");
    }

    return {
        errors,
        warnings,
        updates,
        excluded,
        duplicateMockExamIds,
        invalidMockExamIdCount,
        assignableCount: activeSequences.size
    };
}

function earliestTimestamp(candidates) {
    if (candidates.length === 0) {
        return null;
    }
    return candidates.slice().sort((left, right) => {
        const timeComparison = left.completedAt.getTime() - right.completedAt.getTime();
        return timeComparison !== 0 ? timeComparison : left.method.localeCompare(right.method);
    })[0];
}

function completionTimestamp(session, evidenceDocuments) {
    const explicitCandidates = [];
    for (const evidence of evidenceDocuments) {
        for (const field of ["completedAt", "createdAt", "updatedAt"]) {
            const completedAt = asDate(evidence.document[field]);
            if (completedAt) {
                explicitCandidates.push({
                    completedAt,
                    method: `${evidence.collection}.${field}`
                });
            }
        }
    }
    const explicit = earliestTimestamp(explicitCandidates);
    if (explicit) {
        return explicit;
    }

    const objectIdCandidates = evidenceDocuments
        .map(evidence => ({
            completedAt: objectIdTimestamp(evidence.document._id),
            method: `${evidence.collection} ObjectId timestamp`
        }))
        .filter(candidate => candidate.completedAt);
    const objectId = earliestTimestamp(objectIdCandidates);
    if (objectId) {
        return objectId;
    }

    const sessionCreatedAt = asDate(session.createdAt);
    if (sessionCreatedAt) {
        return {
            completedAt: sessionCreatedAt,
            method: "exam_sessions.createdAt (approximate)"
        };
    }

    return {completedAt: null, method: "unresolved; no trustworthy historical timestamp"};
}

function evidenceProjection() {
    return {
        _id: 1,
        examId: 1,
        totalScore: 1,
        completedAt: 1,
        createdAt: 1,
        updatedAt: 1
    };
}

function loadLiveCompletionEvidence(examSummaries, examResults, examId) {
    const summaries = examSummaries.find(
        {$or: [{examId}, {_id: `summary:${examId}:v1`}]},
        evidenceProjection()
    ).toArray();
    const legacyResults = examResults.find(
        {examId, totalScore: {$exists: true, $ne: null}},
        evidenceProjection()
    ).toArray();
    return {summaries, legacyResults};
}

function applyLegacyActivationWithFinalRecheck(
    examSessions,
    examSummaries,
    examResults,
    activationCandidate
) {
    const latestSession = examSessions.findOne({_id: activationCandidate.sessionId});
    if (!latestSession) {
        return {outcome: "SKIPPED", reason: "session-deleted-before-final-recheck"};
    }
    if (!isLegacyUnset(latestSession.completedAt)) {
        return {outcome: "SKIPPED", reason: "completedAt-already-set"};
    }
    if (!isLegacyUnset(latestSession.active)) {
        return {outcome: "SKIPPED", reason: "active-already-set"};
    }

    const examId = idText(documentId(latestSession));
    const liveEvidence = loadLiveCompletionEvidence(examSummaries, examResults, examId);
    const evidenceDocuments = [
        ...liveEvidence.summaries.map(document => ({collection: "exam_summaries", document})),
        ...liveEvidence.legacyResults.map(document => ({collection: "exam_results", document}))
    ];

    if (evidenceDocuments.length > 0) {
        const timestamp = completionTimestamp(latestSession, evidenceDocuments);
        if (!timestamp.completedAt) {
            return {
                outcome: "ERROR",
                reason: FINAL_RECHECK_EVIDENCE_REASON,
                examId,
                error: "Completion evidence was detected during final recheck but completedAt is unresolved"
            };
        }
        const result = examSessions.updateOne(
            {
                _id: activationCandidate.sessionId,
                $and: [legacyMissingFilter("active"), legacyMissingFilter("completedAt")]
            },
            {$set: {active: false, completedAt: timestamp.completedAt}}
        );
        return {
            outcome: result?.modifiedCount === 1 ? "BACKFILLED_COMPLETION" : "SKIPPED",
            reason: result?.modifiedCount === 1
                ? FINAL_RECHECK_EVIDENCE_REASON
                : "session-changed-before-completion-backfill",
            examId,
            completedAt: timestamp.completedAt,
            timestampMethod: timestamp.method
        };
    }

    // Completion evidence lives in different collections, so it cannot be included in this
    // update's atomic predicate. TMI31_LEGACY_WRITER_STOPPED=true is therefore mandatory.
    const result = examSessions.updateOne(
        {
            _id: activationCandidate.sessionId,
            $and: [legacyMissingFilter("active"), legacyMissingFilter("completedAt")]
        },
        {$set: {active: true}}
    );
    return {
        outcome: result?.modifiedCount === 1 ? "ACTIVATED" : "SKIPPED",
        reason: result?.modifiedCount === 1
            ? "no-completion-evidence-during-final-recheck"
            : "session-changed-before-activation",
        examId
    };
}

function inspectFinalMigrationState(
    examSessions,
    examSummaries,
    legacySummaryResults,
    indexesByCollection
) {
    const errors = [];
    const evidenceExamIds = new Set();
    for (const summary of examSummaries) {
        const examId = summaryExamId(summary);
        if (examId) {
            evidenceExamIds.add(examId);
        }
    }
    for (const result of legacySummaryResults) {
        if (hasValue(result.totalScore)
            && typeof result.examId === "string"
            && result.examId.trim() !== "") {
            evidenceExamIds.add(result.examId);
        }
    }

    let activeWithEvidence = 0;
    let activeWithCompletedAt = 0;
    let completionEvidenceWithUnsafeActive = 0;
    const activeByUser = new Map();
    for (const session of examSessions) {
        const examId = idText(documentId(session));
        const hasEvidence = evidenceExamIds.has(examId);
        if (session.active === true) {
            if (hasEvidence) {
                activeWithEvidence += 1;
            }
            if (!isLegacyUnset(session.completedAt)) {
                activeWithCompletedAt += 1;
            }
            const userKey = typeof session.userId === "string" ? session.userId : "<missing>";
            activeByUser.set(userKey, (activeByUser.get(userKey) ?? 0) + 1);
        }
        if (hasEvidence && (session.active === true || isLegacyUnset(session.active))) {
            completionEvidenceWithUnsafeActive += 1;
        }
    }
    const usersWithMultipleActiveSessions = [...activeByUser.values()]
        .filter(count => count > 1)
        .length;

    if (activeWithEvidence > 0) {
        errors.push(`${activeWithEvidence} active ExamSessions have completion evidence`);
    }
    if (activeWithCompletedAt > 0) {
        errors.push(`${activeWithCompletedAt} active ExamSessions have completedAt`);
    }
    if (usersWithMultipleActiveSessions > 0) {
        errors.push(`${usersWithMultipleActiveSessions} users have multiple active ExamSessions`);
    }
    if (completionEvidenceWithUnsafeActive > 0) {
        errors.push(`${completionEvidenceWithUnsafeActive} completed ExamSessions have active null or true`);
    }

    const indexInspection = inspectIndexes(indexesByCollection ?? {});
    errors.push(...indexInspection.errors);
    if (indexInspection.indexesToCreate.length > 0) {
        errors.push(`Required migration indexes are still missing: ${indexInspection.indexesToCreate
            .map(index => index.name)
            .join(", ")}`);
    }

    return {
        errors,
        activeWithEvidence,
        activeWithCompletedAt,
        completionEvidenceWithUnsafeActive,
        usersWithMultipleActiveSessions
    };
}

function inspectLegacySessions(examSessions, examSummaries, legacySummaryResults) {
    const errors = [];
    const warnings = [];
    const sessionsByExamId = new Map();
    const summariesByExamId = new Map();
    const legacyResultsByExamId = new Map();
    const backfills = [];
    const deactivateWithKnownCompletion = [];
    const activateIncompleteLegacy = [];
    const unresolvedCompletions = [];
    const timestampMethodCounts = new Map();
    let summaryWithoutExamIdCount = 0;
    let legacyResultWithoutExamIdCount = 0;

    for (const session of examSessions) {
        sessionsByExamId.set(idText(documentId(session)), session);
    }
    for (const summary of examSummaries) {
        const examId = summaryExamId(summary);
        if (!examId) {
            summaryWithoutExamIdCount += 1;
            warnings.push("ExamSummary without examId cannot be matched to a Session");
            continue;
        }
        const group = summariesByExamId.get(examId) ?? [];
        group.push(summary);
        summariesByExamId.set(examId, group);
    }
    for (const result of legacySummaryResults) {
        if (!hasValue(result.totalScore)) {
            continue;
        }
        if (typeof result.examId !== "string" || result.examId.trim() === "") {
            legacyResultWithoutExamIdCount += 1;
            warnings.push("Legacy totalScore ExamResult without examId cannot be matched to a Session");
            continue;
        }
        const group = legacyResultsByExamId.get(result.examId) ?? [];
        group.push(result);
        legacyResultsByExamId.set(result.examId, group);
    }

    const duplicateSummaryEvidence = [...summariesByExamId.entries()]
        .filter(([, group]) => group.length > 1)
        .map(([examId, group]) => ({examId, count: group.length}));
    const duplicateLegacyResultEvidence = [...legacyResultsByExamId.entries()]
        .filter(([, group]) => group.length > 1)
        .map(([examId, group]) => ({examId, count: group.length}));
    if (duplicateSummaryEvidence.length > 0) {
        warnings.push(`${duplicateSummaryEvidence.length} ExamSession keys have multiple ExamSummary documents`);
    }
    if (duplicateLegacyResultEvidence.length > 0) {
        warnings.push(`${duplicateLegacyResultEvidence.length} ExamSession keys have multiple legacy totalScore results`);
    }

    const orphanSummaryExamIds = [...summariesByExamId.keys()]
        .filter(examId => !sessionsByExamId.has(examId))
        .sort();
    const orphanLegacyResultExamIds = [...legacyResultsByExamId.keys()]
        .filter(examId => !sessionsByExamId.has(examId))
        .sort();
    const orphanEvidenceExamIds = [...new Set([
        ...orphanSummaryExamIds,
        ...orphanLegacyResultExamIds
    ])].sort();

    let legacyCompletedSessionCount = 0;
    let summaryEvidenceSessionCount = 0;
    let legacyResultEvidenceSessionCount = 0;
    let bothEvidenceSessionCount = 0;
    for (const session of examSessions) {
        const examId = idText(documentId(session));
        const legacyActive = isLegacyUnset(session.active);
        const completionMissing = isLegacyUnset(session.completedAt);
        const summaries = summariesByExamId.get(examId) ?? [];
        const legacyResults = legacyResultsByExamId.get(examId) ?? [];
        const hasSummaryEvidence = summaries.length > 0;
        const hasLegacyResultEvidence = legacyResults.length > 0;

        if (legacyActive && !completionMissing) {
            deactivateWithKnownCompletion.push({sessionId: documentId(session)});
            continue;
        }
        if (!legacyActive || !completionMissing) {
            continue;
        }
        if (!hasSummaryEvidence && !hasLegacyResultEvidence) {
            activateIncompleteLegacy.push({sessionId: documentId(session)});
            continue;
        }

        legacyCompletedSessionCount += 1;
        if (hasSummaryEvidence) {
            summaryEvidenceSessionCount += 1;
        }
        if (hasLegacyResultEvidence) {
            legacyResultEvidenceSessionCount += 1;
        }
        if (hasSummaryEvidence && hasLegacyResultEvidence) {
            bothEvidenceSessionCount += 1;
        }
        const evidenceDocuments = [
            ...summaries.map(document => ({collection: "exam_summaries", document})),
            ...legacyResults.map(document => ({collection: "exam_results", document}))
        ];
        const timestamp = completionTimestamp(session, evidenceDocuments);
        timestampMethodCounts.set(timestamp.method, (timestampMethodCounts.get(timestamp.method) ?? 0) + 1);
        if (!timestamp.completedAt) {
            unresolvedCompletions.push({examId, method: timestamp.method});
            errors.push(`Legacy completed ExamSession timestamp is unresolved: ${examId}`);
            continue;
        }
        backfills.push({
            sessionId: documentId(session),
            examId,
            completedAt: timestamp.completedAt,
            method: timestamp.method
        });
    }

    const inconsistentCompletedActive = examSessions.filter(
        session => session.active === true && !isLegacyUnset(session.completedAt)
    ).length;
    if (inconsistentCompletedActive > 0) {
        errors.push(`${inconsistentCompletedActive} completed ExamSessions still have active=true`);
    }

    const reusableByUser = new Map();
    for (const session of examSessions) {
        const examId = idText(documentId(session));
        const completed = !isLegacyUnset(session.completedAt)
            || (summariesByExamId.get(examId) ?? []).length > 0
            || (legacyResultsByExamId.get(examId) ?? []).length > 0;
        const reusable = (session.active === true && isLegacyUnset(session.completedAt))
            || (isLegacyUnset(session.active) && !completed);
        if (!reusable) {
            continue;
        }
        const userKey = typeof session.userId === "string" ? session.userId : "<missing>";
        reusableByUser.set(userKey, (reusableByUser.get(userKey) ?? 0) + 1);
    }
    const usersWithMultipleReusableSessions = [...reusableByUser.values()]
        .filter(count => count > 1)
        .length;
    if (usersWithMultipleReusableSessions > 0) {
        errors.push(`${usersWithMultipleReusableSessions} users have multiple reusable ExamSessions`);
    }

    return {
        errors,
        warnings,
        backfills,
        deactivateWithKnownCompletion,
        activateIncompleteLegacy,
        unresolvedCompletions,
        timestampMethodCounts: Object.fromEntries(timestampMethodCounts),
        legacyCompletedSessionCount,
        summaryEvidenceSessionCount,
        legacyResultEvidenceSessionCount,
        bothEvidenceSessionCount,
        duplicateSummaryExamCount: duplicateSummaryEvidence.length,
        duplicateLegacyResultExamCount: duplicateLegacyResultEvidence.length,
        duplicateSummaryEvidence,
        duplicateLegacyResultEvidence,
        orphanSummaryExamCount: orphanSummaryExamIds.length,
        orphanLegacyResultExamCount: orphanLegacyResultExamIds.length,
        orphanEvidenceExamCount: orphanEvidenceExamIds.length,
        orphanSummaryExamIds,
        orphanLegacyResultExamIds,
        orphanEvidenceExamIds,
        summaryWithoutExamIdCount,
        legacyResultWithoutExamIdCount,
        usersWithMultipleReusableSessions
    };
}

function sameOrderedKey(actual, expected) {
    if (!actual || typeof actual !== "object") {
        return false;
    }
    const actualEntries = Object.entries(actual);
    const expectedEntries = Object.entries(expected);
    return actualEntries.length === expectedEntries.length
        && actualEntries.every(([key, value], index) =>
            key === expectedEntries[index][0] && value === expectedEntries[index][1]);
}

function sameDocument(actual, expected) {
    if (!hasValue(actual) && !hasValue(expected)) {
        return true;
    }
    if (!hasValue(actual) || !hasValue(expected)) {
        return false;
    }
    return JSON.stringify(actual) === JSON.stringify(expected);
}

function inspectIndexes(indexesByCollection) {
    const errors = [];
    const indexesToCreate = [];

    for (const spec of INDEX_SPECS) {
        const indexes = indexesByCollection[spec.collection] ?? [];
        const named = indexes.find(index => index.name === spec.name);
        const exact = named
            && sameOrderedKey(named.key, spec.key)
            && (named.unique === true) === spec.unique
            && named.sparse !== true
            && sameDocument(named.partialFilterExpression, spec.partialFilterExpression);
        if (named && !exact) {
            errors.push(`Index ${spec.name} exists with an incompatible definition`);
            continue;
        }
        if (!named) {
            const sameKeyDifferentName = indexes.find(index =>
                index.name !== spec.name && sameOrderedKey(index.key, spec.key));
            if (sameKeyDifferentName) {
                errors.push(`Index key for ${spec.name} already exists under a different name or definition`);
                continue;
            }
            indexesToCreate.push(spec);
        }
    }
    return {errors, indexesToCreate};
}

function buildMigrationPlan(input) {
    const catalog = inspectCatalog(input.mockExams ?? []);
    const legacySessions = inspectLegacySessions(
        input.examSessions ?? [],
        input.examSummaries ?? [],
        input.legacySummaryResults ?? []
    );
    const indexes = inspectIndexes(input.indexesByCollection ?? {});
    return {
        catalog,
        legacySessions,
        indexes,
        errors: [...catalog.errors, ...legacySessions.errors, ...indexes.errors],
        warnings: [...catalog.warnings, ...legacySessions.warnings]
    };
}

function legacyMissingFilter(field) {
    return {$or: [{[field]: null}, {[field]: {$exists: false}}]};
}

function applyLegacyCompletionBackfills(examSessionsCollection, backfills) {
    for (const backfill of backfills) {
        examSessionsCollection.updateOne(
            {
                _id: backfill.sessionId,
                $and: [legacyMissingFilter("active"), legacyMissingFilter("completedAt")]
            },
            {$set: {active: false, completedAt: backfill.completedAt}}
        );
    }
}

function printLine(message) {
    if (typeof print === "function") {
        print(message);
    } else {
        console.log(message);
    }
}

function printPlan(databaseName, applyChanges, plan) {
    const plannedSessionChanges = plan.legacySessions.backfills.length
        + plan.legacySessions.deactivateWithKnownCompletion.length
        + plan.legacySessions.activateIncompleteLegacy.length;
    printLine(`TMI-31 migration mode: ${applyChanges ? "APPLY" : "DRY-RUN"}`);
    printLine(`Selected MongoDB database: ${databaseName}`);
    printLine(`Target collections: ${COLLECTION_NAMES.join(", ")}`);
    printLine(`MockExam documents prepared: ${plan.catalog.updates.length}`);
    printLine(`MockExam documents excluded from assignment: ${plan.catalog.excluded.length}`);
    for (const excluded of plan.catalog.excluded) {
        printLine(`EXCLUDED MockExam: ${excluded.mockExamId} `
            + `reasons=${excluded.reasons.join(",")} `
            + `sequenceExplicit=${excluded.sequenceExplicit} `
            + `sequenceInterpretable=${excluded.sequenceInterpretable} `
            + `sequenceDiagnostic=${excluded.sequenceDiagnostic}`);
    }
    printLine(`Invalid null/blank/whitespace mock_exam_id documents: ${plan.catalog.invalidMockExamIdCount}`);
    printLine(`Duplicate mock_exam_id groups: ${plan.catalog.duplicateMockExamIds.length}`);
    for (const duplicate of plan.catalog.duplicateMockExamIds) {
        printLine(`DUPLICATE mock_exam_id: ${duplicate.mockExamId} ${JSON.stringify(duplicate.documents)}`);
    }
    printLine(`Legacy completed Sessions detected from any evidence: ${plan.legacySessions.legacyCompletedSessionCount}`);
    printLine(`Legacy Sessions with exam_summaries evidence: ${plan.legacySessions.summaryEvidenceSessionCount}`);
    printLine(`Legacy Sessions with exam_results.totalScore evidence: ${plan.legacySessions.legacyResultEvidenceSessionCount}`);
    printLine(`Legacy Sessions with evidence in both collections: ${plan.legacySessions.bothEvidenceSessionCount}`);
    printLine(`Legacy completion backfills planned: ${plan.legacySessions.backfills.length}`);
    for (const backfill of plan.legacySessions.backfills) {
        printLine(`BACKFILL ExamSession: examId=${backfill.examId} `
            + `completedAt=${backfill.completedAt.toISOString()} source=${backfill.method}`);
    }
    printLine(`Legacy completion timestamp methods: ${JSON.stringify(plan.legacySessions.timestampMethodCounts)}`);
    printLine(`Legacy completion evidence without a timestamp: ${plan.legacySessions.unresolvedCompletions.length}`);
    for (const unresolved of plan.legacySessions.unresolvedCompletions) {
        printLine(`UNRESOLVED completion timestamp: examId=${unresolved.examId}`);
    }
    printLine(`ExamSummary duplicate conflicts: ${plan.legacySessions.duplicateSummaryExamCount}`);
    for (const duplicate of plan.legacySessions.duplicateSummaryEvidence) {
        printLine(`DUPLICATE ExamSummary evidence: examId=${duplicate.examId} count=${duplicate.count}`);
    }
    printLine(`Legacy totalScore duplicate conflicts: ${plan.legacySessions.duplicateLegacyResultExamCount}`);
    for (const duplicate of plan.legacySessions.duplicateLegacyResultEvidence) {
        printLine(`DUPLICATE legacy totalScore evidence: examId=${duplicate.examId} count=${duplicate.count}`);
    }
    printLine(`Completion evidence examIds without a matching Session: ${plan.legacySessions.orphanEvidenceExamCount}`);
    for (const examId of plan.legacySessions.orphanEvidenceExamIds) {
        printLine(`ORPHAN completion evidence: examId=${examId}`);
    }
    printLine(`ExamSummary records without a matching Session: ${plan.legacySessions.orphanSummaryExamCount}`);
    printLine(`Legacy totalScore examIds without a matching Session: ${plan.legacySessions.orphanLegacyResultExamCount}`);
    printLine(`ExamSummary records without examId: ${plan.legacySessions.summaryWithoutExamIdCount}`);
    printLine(`Legacy totalScore records without examId: ${plan.legacySessions.legacyResultWithoutExamIdCount}`);
    printLine(`Planned ExamSession compatibility changes: ${plannedSessionChanges}`);
    printLine(`Indexes planned for creation: ${plan.indexes.indexesToCreate.map(index => index.name).join(", ") || "none"}`);
    if (!applyChanges) {
        printLine(`${FINAL_RECHECK_EVIDENCE_REASON}: not-run-in-dry-run`);
    }
    for (const warning of plan.warnings) {
        printLine(`WARNING: ${warning}`);
    }
    for (const error of plan.errors) {
        printLine(`ERROR: ${error}`);
    }
}

function createIndex(collection, spec) {
    const options = {name: spec.name};
    if (spec.unique) {
        options.unique = true;
    }
    if (spec.partialFilterExpression) {
        options.partialFilterExpression = spec.partialFilterExpression;
    }
    collection.createIndex(spec.key, options);
}

function runMongoMigration() {
    const uri = environmentValue("MONGODB_URI");
    const databaseName = resolveTargetDatabaseName(uri, environmentValue("MONGODB_DATABASE"));
    const applyChanges = environmentValue("TMI31_APPLY") === "true";
    validateApplyPreconditions(
        applyChanges,
        environmentValue("TMI31_LEGACY_WRITER_STOPPED")
    );
    const connectedDatabase = connect(uri);
    const targetDatabase = connectedDatabase.getSiblingDB(databaseName);
    const mockExams = targetDatabase.getCollection("mock_exams");
    const examSessions = targetDatabase.getCollection("exam_sessions");
    const examSummaries = targetDatabase.getCollection("exam_summaries");
    const examResults = targetDatabase.getCollection("exam_results");

    const plan = buildMigrationPlan({
        mockExams: mockExams.find({}).toArray(),
        examSessions: examSessions.find({}).toArray(),
        examSummaries: examSummaries.find({}, {
            _id: 1,
            examId: 1,
            completedAt: 1,
            createdAt: 1,
            updatedAt: 1
        }).toArray(),
        legacySummaryResults: examResults.find(
            {totalScore: {$exists: true, $ne: null}},
            {
                _id: 1,
                examId: 1,
                totalScore: 1,
                completedAt: 1,
                createdAt: 1,
                updatedAt: 1
            }
        ).toArray(),
        indexesByCollection: {
            mock_exams: mockExams.getIndexes(),
            exam_sessions: examSessions.getIndexes()
        }
    });
    printPlan(databaseName, applyChanges, plan);

    if (plan.errors.length > 0) {
        printLine("No changes were applied because validation failed.");
        quit(2);
    }
    if (!applyChanges) {
        printLine("Dry-run complete. Set TMI31_APPLY=true explicitly to apply the planned changes.");
        quit(0);
    }

    const plannedSessionChanges = plan.legacySessions.backfills.length
        + plan.legacySessions.deactivateWithKnownCompletion.length
        + plan.legacySessions.activateIncompleteLegacy.length;
    printLine(`APPLY confirmation - database: ${databaseName}`);
    printLine(`APPLY confirmation - collections: ${COLLECTION_NAMES.join(", ")}`);
    printLine(`APPLY confirmation - MockExam updates: ${plan.catalog.updates.length}`);
    printLine(`APPLY confirmation - ExamSession compatibility changes: ${plannedSessionChanges}`);
    printLine(`APPLY confirmation - indexes: ${plan.indexes.indexesToCreate.length}`);

    if (plan.catalog.updates.length > 0) {
        mockExams.bulkWrite(plan.catalog.updates.map(update => ({
            updateOne: {
                filter: {_id: update.documentId},
                update: {$set: {sequence: update.sequence, active: update.active}}
            }
        })), {ordered: true});
    }

    examSessions.updateMany(
        {$or: [{mockExamId: null}, {mockExamId: {$exists: false}}]},
        {$set: {mockExamId: LEGACY_MOCK_EXAM_ID}}
    );
    applyLegacyCompletionBackfills(examSessions, plan.legacySessions.backfills);
    for (const update of plan.legacySessions.deactivateWithKnownCompletion) {
        examSessions.updateOne(
            {
                _id: update.sessionId,
                completedAt: {$exists: true, $ne: null},
                ...legacyMissingFilter("active")
            },
            {$set: {active: false}}
        );
    }
    const finalRecheckResults = [];
    for (const update of plan.legacySessions.activateIncompleteLegacy) {
        const result = applyLegacyActivationWithFinalRecheck(
            examSessions,
            examSummaries,
            examResults,
            update
        );
        finalRecheckResults.push(result);
        if (result.outcome === "ERROR") {
            printLine(`ERROR: ${result.error}`);
            printLine("Migration stopped because final completion-evidence recheck could not be resolved.");
            quit(3);
        }
    }
    const completionEvidenceDetectedDuringFinalRecheck = finalRecheckResults
        .filter(result => result.reason === FINAL_RECHECK_EVIDENCE_REASON);
    printLine(`${FINAL_RECHECK_EVIDENCE_REASON}: `
        + completionEvidenceDetectedDuringFinalRecheck.length);
    for (const result of completionEvidenceDetectedDuringFinalRecheck) {
        printLine(`FINAL RECHECK BACKFILL: examId=${result.examId} `
            + `completedAt=${result.completedAt.toISOString()} source=${result.timestampMethod}`);
    }
    for (const spec of plan.indexes.indexesToCreate) {
        createIndex(targetDatabase.getCollection(spec.collection), spec);
    }

    const finalVerification = inspectFinalMigrationState(
        examSessions.find({}).toArray(),
        examSummaries.find({}, evidenceProjection()).toArray(),
        examResults.find(
            {totalScore: {$exists: true, $ne: null}},
            evidenceProjection()
        ).toArray(),
        {
            mock_exams: mockExams.getIndexes(),
            exam_sessions: examSessions.getIndexes()
        }
    );
    printLine(`Final verification active-with-completion-evidence: `
        + finalVerification.activeWithEvidence);
    printLine(`Final verification active-with-completedAt: `
        + finalVerification.activeWithCompletedAt);
    printLine(`Final verification users-with-multiple-active-sessions: `
        + finalVerification.usersWithMultipleActiveSessions);
    printLine(`Final verification completed-with-active-null-or-true: `
        + finalVerification.completionEvidenceWithUnsafeActive);
    if (finalVerification.errors.length > 0) {
        for (const error of finalVerification.errors) {
            printLine(`ERROR: ${error}`);
        }
        printLine("TMI-31 migration final verification failed.");
        quit(3);
    }

    printLine("TMI-31 migration applied successfully.");
    quit(0);
}

function runNodeCli() {
    try {
        const applyChanges = environmentValue("TMI31_APPLY") === "true";
        resolveTargetDatabaseName(
            environmentValue("MONGODB_URI"),
            environmentValue("MONGODB_DATABASE")
        );
        validateApplyPreconditions(
            applyChanges,
            environmentValue("TMI31_LEGACY_WRITER_STOPPED")
        );
    } catch (validationError) {
        console.error(`TMI-31 migration validation failed: ${validationError.message}`);
        process.exitCode = 2;
        return;
    }

    const {spawnSync} = require("node:child_process");
    const child = spawnSync(
        "mongosh",
        ["--nodb", "--quiet", "--file", __filename],
        {
            env: {...process.env, TMI31_MONGOSH_PAYLOAD: "true"},
            stdio: "inherit"
        }
    );
    if (child.error) {
        console.error("TMI-31 migration could not start mongosh");
        process.exitCode = 2;
        return;
    }
    process.exitCode = child.status ?? 2;
}

const mongoshPayload = environmentValue("TMI31_MONGOSH_PAYLOAD") === "true";
if (mongoshPayload) {
    runMongoMigration();
} else if (typeof module !== "undefined") {
    module.exports = {
        INDEX_SPECS,
        JAVA_INTEGER_MAX,
        LEGACY_MOCK_EXAM_ID,
        FINAL_RECHECK_EVIDENCE_REASON,
        applyLegacyActivationWithFinalRecheck,
        applyLegacyCompletionBackfills,
        buildMigrationPlan,
        inspectCatalog,
        inspectFinalMigrationState,
        inspectIndexes,
        inspectLegacySessions,
        resolveTargetDatabaseName,
        validateApplyPreconditions,
        validateDatabaseName
    };
    if (require.main === module) {
        runNodeCli();
    }
}
