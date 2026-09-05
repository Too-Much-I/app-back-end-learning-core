"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {
    INDEX_SPECS,
    blockers,
    canonicalUuid,
    inspectIndexes,
    validateDatabaseName,
    validateEnvironment
} = require("./user-merged-prepare.js");

test("canonical UUID validation is lowercase and strict", () => {
    assert.equal(canonicalUuid("73a18ed4-1d56-4c4f-afd6-b39175b82a86"), true);
    assert.equal(canonicalUuid("73A18ED4-1D56-4C4F-AFD6-B39175B82A86"), false);
    assert.equal(canonicalUuid("not-a-uuid"), false);
});

test("system and blank databases are rejected", () => {
    assert.throws(() => validateDatabaseName(""));
    assert.throws(() => validateDatabaseName("admin"));
    assert.equal(validateDatabaseName("to-teacher-app"), "to-teacher-app");
});

test("apply requires explicit legacy writer drain acknowledgement", () => {
    assert.throws(() => validateEnvironment("mongodb://example", "app", true, false));
    assert.equal(validateEnvironment("mongodb://example", "app", true, true), "app");
});

test("missing owner indexes are planned without conflict", () => {
    const inspection = inspectIndexes({});
    assert.deepEqual(inspection.errors, []);
    assert.deepEqual(inspection.missing, INDEX_SPECS);
});

test("same-name incompatible index blocks apply", () => {
    const inspection = inspectIndexes({
        exam_results: [{name: "idx_exam_results_user", key: {userId: -1}}]
    });
    assert.equal(inspection.errors.length, 1);
});

test("inventory blockers never depend on raw owner identifiers", () => {
    assert.deepEqual(blockers({
        indexes: {errors: []},
        invalidOwnerCount: 1,
        activeDuplicateGroups: 1,
        existingMergedGuards: 1
    }), [
        "non-canonical owner UUIDs exist",
        "duplicate active Session owner groups exist",
        "pre-existing MERGED guards require manual review"
    ]);
});
