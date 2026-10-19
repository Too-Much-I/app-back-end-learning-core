const {test} = require("node:test");
const assert = require("node:assert/strict");
const {COLLECTIONS, INDEXES, databaseName, validateCatalog, compatible, prepare} = require("./challenge-10s-prepare");
const day = n => ({dayNumber: n, questions: [1, 2, 3].map(q => ({questionNumber: q, questionId: `D${n}Q${q}`, korean: "테스트 문장", referenceAnswer: "Fixture answer", difficulty: -2}))});
test("catalog validates exact three, positive day and uninterpreted integer difficulty", () => assert.deepEqual(validateCatalog([day(1), day(2)]), {days: 2, questions: 6}));
test("mongosh BSON Int32 values accepted without accepting strings or Long", () => {
    const d = day(1); d.dayNumber = {_bsontype: "Int32", valueOf: () => 1};
    d.questions[0].difficulty = {_bsontype: "Int32", valueOf: () => 2};
    assert.equal(validateCatalog([d]).days, 1);
    d.questions[0].difficulty = {_bsontype: "Long", valueOf: () => 2}; assert.throws(() => validateCatalog([d]));
});
test("reject missing day1, repeated day and repeated embedded IDs", () => {
    assert.throws(() => validateCatalog([day(2)])); assert.throws(() => validateCatalog([day(1), day(1)]));
    const d = day(1); d.questions[1].questionId = d.questions[0].questionId; assert.throws(() => validateCatalog([d]));
});
test("reject cross-document IDs, noninteger difficulty and blank content", () => {
    const a = day(1), b = day(2); b.questions[0].questionId = a.questions[0].questionId; assert.throws(() => validateCatalog([a, b]));
    a.questions[0].difficulty = "2"; assert.throws(() => validateCatalog([a]));
    a.questions[0].difficulty = 2; a.questions[0].referenceAnswer = " "; assert.throws(() => validateCatalog([a]));
});
test("migration scoped to Challenge, no TTL", () => {
    assert.equal(COLLECTIONS.length, 6); assert.ok(INDEXES.every(i => i.collection.startsWith("challenge_10s_") && !i.expireAfterSeconds));
});
test("explicit non-system database required", () => {
    for (const value of [undefined, "", "admin", "local", "config", " app "]) assert.throws(() => databaseName(value));
    assert.equal(databaseName("isolated-challenge-fixture"), "isolated-challenge-fixture");
});
test("unique/index-order/TTL/partial/collation incompatibilities fail", () => {
    const e = INDEXES[2]; assert.equal(compatible({...e}, e), true);
    for (const extra of [{unique: false}, {expireAfterSeconds: 1}, {partialFilterExpression: {}}, {collation: {}}, {hidden: true}, {sparse: true}]) assert.equal(compatible({...e, ...extra}, e), false);
    assert.equal(compatible({...e, key: {challengeDate: 1, userId: 1, questionNumber: 1}}, e), false);
});
function migrationFixture() {
    const changes = [];
    const legacy = {name: "challenge_owner_date_question_unique", unique: true, key: {userId: 1, challengeDate: 1, questionNumber: 1}};
    const collections = new Map(COLLECTIONS.map(name => [name, {
        indexes: name.endsWith("attempts") ? [legacy] : [],
        find: () => ({toArray: async () => name.endsWith("questions") ? [day(1)] : [], async *[Symbol.asyncIterator]() {}}),
        aggregate: () => ({toArray: async () => []}),
        async getIndexes() { return this.indexes; },
        async createIndex(key, options) { this.indexes.push({key, ...options}); changes.push(`create:${options.name}`); },
        async dropIndex(index) { this.indexes = this.indexes.filter(i => i.name !== index); changes.push(`drop:${index}`); }
    }]));
    return {changes, database: {getName: () => "isolated-challenge-fixture", getCollection: name => collections.get(name),
        getCollectionInfos: async () => COLLECTIONS.map(name => ({name}))}};
}
test("retry index includes sequence; dry run does not drop old index", async () => {
    assert.deepEqual(INDEXES[2].key, {userId: 1, challengeDate: 1, questionNumber: 1, attemptSequence: 1});
    const f = migrationFixture(); const result = await prepare(f.database, false);
    assert.equal(result.retryIndexMigrationRequired, true); assert.deepEqual(f.changes, []);
});
test("legacy constraint replacement requires explicit approval before writes", async () => {
    const f = migrationFixture(); await assert.rejects(prepare(f.database, true), /CHALLENGE_RETRY_INDEX_MIGRATION/);
    assert.deepEqual(f.changes, []);
});
test("approved replacement creates and verifies new indexes before dropping only legacy constraint", async () => {
    const f = migrationFixture(); await prepare(f.database, true, true);
    assert.equal(f.changes.at(-1), "drop:challenge_owner_date_question_unique");
    assert.ok(f.changes.includes("create:challenge_owner_date_question_sequence_unique"));
    assert.equal(f.changes.filter(x => x.startsWith("drop:")).length, 1);
    const report = await prepare(f.database, false); assert.equal(report.retryIndexMigrationRequired, false);
});
