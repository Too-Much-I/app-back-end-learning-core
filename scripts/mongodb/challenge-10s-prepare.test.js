const {test} = require("node:test");
const assert = require("node:assert/strict");
const {COLLECTIONS, INDEXES, databaseName, validateCatalog, compatible} = require("./challenge-10s-prepare");
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
