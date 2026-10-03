"use strict";
const {test} = require("node:test");
const assert = require("node:assert/strict");
const {INDEXES, COLLECTIONS, databaseName, compatible, run} = require("./learning-record-deletion-prepare");

function fixture(duplicateActiveOwners = 0) {
    const indexes = {}, writes = [];
    return {indexes, writes, getName: () => "deletion-fixture", getCollection: name => ({
        getIndexes: async () => indexes[name] ?? [],
        aggregate: () => ({toArray: async () => duplicateActiveOwners ? [{count: duplicateActiveOwners}] : []}),
        createIndex: async (key, options) => {
            writes.push(name);
            (indexes[name] ??= []).push({key, ...options});
        }
    })};
}

test("explicit database, no implicit system database", () => {
    for (const name of [undefined, "", " admin ", "admin", "CONFIG", "local"]) assert.throws(() => databaseName(name));
});
test("TTL only targets completed deletion evidence, never learning content or aggregates", () => {
    const ttl = INDEXES.filter(i => i.expireAfterSeconds !== undefined);
    assert.equal(ttl.length, 3);
    assert.ok(ttl.every(i => i.collection.startsWith("learning_record_deletion_") && i.expireAfterSeconds === 0));
    assert.ok(INDEXES.some(i => i.collection === "learning_activity_daily_aggregates" && i.unique));
});
test("reject weakened or broadened index definitions", () => {
    const expected = INDEXES[0];
    assert.ok(compatible(expected, expected));
    for (const patch of [{unique: false}, {partialFilterExpression: {}}, {partialFilterExpression: undefined},
        {hidden: true}, {sparse: true}, {expireAfterSeconds: 0}, {collation: {}}, {key: {other: 1}}]) {
        assert.equal(compatible({...expected, ...patch}, expected), false);
    }
});
test("dry run never writes", async () => {
    const db = fixture();
    const result = await run(db, {databaseName: "deletion-fixture"});
    assert.equal(result.mode, "dry-run");
    assert.equal(result.missing.length, INDEXES.length);
    assert.equal(db.writes.length, 0);
});
test("apply requires exact database and drained writers", async () => {
    const db = fixture();
    await assert.rejects(() => run(db, {databaseName: "wrong", apply: true, writersDrained: true}));
    await assert.rejects(() => run(db, {databaseName: "deletion-fixture", apply: true}));
    assert.equal(db.writes.length, 0);
});
test("apply is idempotent and verifies definitions", async () => {
    const db = fixture();
    const options = {databaseName: "deletion-fixture", apply: true, writersDrained: true};
    await run(db, options);
    await run(db, options);
    assert.equal(db.writes.length, INDEXES.length);
});
test("duplicates and existing incompatible indexes fail before writes", async () => {
    const duplicate = fixture(1), wrong = fixture();
    wrong.indexes[INDEXES[0].collection] = [{...INDEXES[0], unique: false}];
    for (const db of [duplicate, wrong]) {
        await assert.rejects(() => run(db, {databaseName: "deletion-fixture", apply: true, writersDrained: true}));
        assert.equal(db.writes.length, 0);
    }
});
test("orphan inventory and unexpected analytics TTL prevent all DDL", async () => {
    const orphan = fixture(), ttl = fixture();
    const collection = orphan.getCollection;
    orphan.getCollection = name => ({...collection(name), aggregate: () => ({toArray: async () => name === "azure_results" ? [{count: 1}] : []})});
    ttl.indexes.learning_activity_daily_aggregates = [{key: {createdAt: 1}, expireAfterSeconds: 3600, name: "unsafe_ttl"}];
    for (const db of [orphan, ttl]) {
        await assert.rejects(() => run(db, {databaseName: "deletion-fixture", apply: true, writersDrained: true}));
        assert.equal(db.writes.length, 0);
    }
});
test("BSON numeric TTL zero is accepted but missing/string TTL is not", () => {
    const expected = INDEXES.find(i => i.expireAfterSeconds === 0);
    assert.ok(compatible({...expected, expireAfterSeconds: {valueOf: () => 0}}, expected));
    assert.equal(compatible({...expected, expireAfterSeconds: "0"}, expected), false);
    assert.equal(compatible({...expected, expireAfterSeconds: undefined}, expected), false);
});
