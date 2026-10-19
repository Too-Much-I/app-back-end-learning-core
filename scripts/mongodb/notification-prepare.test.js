const {test} = require('node:test');
const assert = require('node:assert/strict');
const {INDEXES, databaseName, compatible, prepare} = require('./notification-prepare');
test('explicit database, no system or blank database', () => {
    for (const name of [undefined, '', ' prod', 'admin', 'local', 'config']) assert.throws(() => databaseName(name));
    assert.equal(databaseName('isolated-notification'), 'isolated-notification');
});
test('full index options including zero TTL and sparse uniqueness', () => {
    for (const s of INDEXES) {
        const actual = {key: s.key, unique: s.unique, sparse: s.sparse, expireAfterSeconds: s.ttl};
        assert.equal(compatible(actual, s), true);
        for (const extra of [{hidden: true}, {partialFilterExpression: {x: 1}}, {collation: {locale: 'en'}}])
            assert.equal(compatible({...actual, ...extra}, s), false);
        assert.equal(compatible({...actual, unique: !s.unique}, s), false);
    }
    const ttl = INDEXES.find(s => s.ttl === 0);
    assert.equal(compatible({key: ttl.key}, ttl), false);
});
function fake(duplicate = false) {
    const writes = [], indexes = new Map();
    const db = {getName: () => 'isolated-notification', getCollectionInfos: async () => [{name: 'exam_sessions'}, {name: 'notification_devices'}],
        createCollection: async name => writes.push(name), getCollection: name => ({
            getIndexes: async () => indexes.get(name) || [],
            aggregate: () => ({toArray: async () => duplicate ? [{count: 2}] : []}),
            createIndex: async (key, options) => { writes.push(options.name); indexes.set(name, [...(indexes.get(name) || []), {key, ...options}]); }
        })};
    return {db, writes};
}
test('submission TTL uses explicit completion-relative expiresAt, not initial acceptedAt', () => {
    const ttl = INDEXES.filter(s => s.collection === 'exam_submission_receipts' && s.ttl !== undefined);
    assert.equal(ttl.length, 1);
    assert.deepEqual(ttl[0].key, {expiresAt: 1});
    assert.equal(ttl[0].ttl, 0);
    assert.equal(INDEXES.some(s => s.collection === 'exam_sessions' && s.ttl !== undefined), false);
});
test('dry run does not write, apply verifies indexes, duplicates abort before writes', async () => {
    const a = fake(); await prepare(a.db, false); assert.equal(a.writes.length, 0);
    await prepare(a.db, true); assert.ok(a.writes.length > 0);
    const b = fake(true); await assert.rejects(prepare(b.db, true)); assert.equal(b.writes.length, 0);
});
