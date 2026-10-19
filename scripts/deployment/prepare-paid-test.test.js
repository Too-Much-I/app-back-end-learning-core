const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const { lcIndexMatches, lcStateMatches, LC_TEST_SPECS } = require('./prepare-paid-test-mongo.js');
test('recovery six and outbox three exact specs; only one TTL', () => {
  assert.equal(LC_TEST_SPECS.length, 9);
  assert.equal(LC_TEST_SPECS.filter(x => x[3]?.expireAfterSeconds !== undefined).length, 1);
});
for (const bad of [{ unique: true }, { sparse: true }, { hidden: true }, { collation: {} },
  { partialFilterExpression: {} }, { expireAfterSeconds: 0 }, { key: { _id: -1 } }]) {
  test('index conflict fails closed ' + JSON.stringify(bad), () => {
    assert.equal(lcIndexMatches({ key: { _id: 1 }, ...bad }, { _id: 1 }), false);
  });
}
test('incomplete seal is invalid', () => assert.equal(lcStateMatches({ _id: 'legacy-v1' }), false));
const source = fs.readFileSync(require('node:path').join(__dirname, 'prepare-paid-test-mongo.js'), 'utf8');
async function exercise({ nonempty = false, conflict = false, approved = true, existingSeal = false } = {}) {
  const actions = []; let output; const data = new Map();
  const indexes = new Map();
  for (const name of ['exam_sessions', 'exam_creation_operations']) { data.set(name, []); indexes.set(name, [{ name: '_id_', key: { _id: 1 } }]); }
  if (nonempty) data.get('exam_sessions').push({ _id: 'must-not-leak' });
  if (conflict) indexes.get('exam_creation_operations').push({ name: 'reservation_recovery_due', key: { wrong: 1 } });
  if (existingSeal) {
    data.set('exam_paid_rollout_state', [{ _id: 'legacy-v1', status: 'SEALED', environment: 'test',
      database: 'to-teacher-learning-core-test', revision: 'lc-test-legacy-20261017-r1',
      sha256: '66508198c1deae88e5b39b6d9ea88d01c70b5250a074d8a671d821933b274070', count: 0,
      writersDrained: true, readerCoverageVerified: true, sealedAt: new Date() }]);
    indexes.set('exam_paid_rollout_state', [{ name: '_id_', key: { _id: 1 } }]);
  }
  const collection = name => ({ getIndexes: async () => indexes.get(name) || [],
    countDocuments: async () => (data.get(name) || []).length,
    find: () => { const cursor = { limit: () => cursor, maxTimeMS: () => cursor, toArray: async () => data.get(name) || [] }; return cursor; },
    createIndex: async (key, options) => { actions.push('index:' + options.name); const all = indexes.get(name);
      if (!all.some(x => x.name === options.name)) all.push({ key, ...options }); },
    insertOne: async state => { actions.push('seal'); data.get(name).push(state); }
  });
  const db = { getCollection: collection, getCollectionInfos: async () => [...data.keys()].map(name => ({ name, type: 'collection', options: {} })),
    createCollection: async name => { actions.push('collection:' + name); data.set(name, []); indexes.set(name, [{ name: '_id_', key: { _id: 1 } }]); }
  };
  const session = { getDatabase: () => db, startTransaction: async () => actions.push('start'),
    commitTransaction: async () => actions.push('commit'), abortTransaction: async () => actions.push('abort'), endSession: async () => actions.push('end') };
  const context = { require, URL, Buffer, Date, NumberInt: x => x, module: { exports: {} },
    process: { env: { LC_TEST_INVENTORY_URI: 'mongodb+srv://fake:fake@cluster0.lkpybbj.mongodb.net/to-teacher-learning-core-test',
      LC_TEST_APPLY_APPROVED: String(approved), LC_TEST_OUTBOX_TTL_APPROVED: 'true',
      LC_TEST_WRITER_DRAIN_ATTESTED: 'true', LC_TEST_READER_COVERAGE_ATTESTED: 'true' } },
    print: x => { output = JSON.parse(x); }, quit: () => { actions.push('quit'); } };
  vm.createContext(context);
  // Define helpers without automatic execution, then supply only the isolated fake Mongo.
  vm.runInContext(source, context);
  context.Mongo = function () { return { getDB: () => db, startSession: () => session }; };
  await vm.runInContext('preparePaidTest()', context);
  return { actions, output };
}
test('empty test installs only expected structures and one seal; no business write', async () => {
  const { actions, output } = await exercise();
  assert.equal(output.result, 'PREPARED_AND_SEALED');
  assert.equal(actions.filter(x => x === 'seal').length, 1);
  assert.equal(actions.filter(x => x.startsWith('index:')).length, 9);
  assert.equal(actions.filter(x => x.startsWith('collection:')).length, 5);
  assert.equal(output.businessDocumentsModified, false);
});
for (const setup of [{ nonempty: true }, { conflict: true }, { approved: false }]) {
  test('preflight refuses writes ' + JSON.stringify(setup), async () => {
    const { actions, output } = await exercise(setup);
    assert.equal(output.result, 'STOPPED_REQUIRES_REVIEW');
    assert.ok(!actions.some(x => x.startsWith('index:') || x.startsWith('collection:') || x === 'seal'));
    assert.ok(!JSON.stringify(output).includes('must-not-leak'));
  });
}
test('same sealed artifact replays without replacing first seal', async () => {
  const { actions, output } = await exercise({ existingSeal: true });
  assert.equal(output.result, 'PREPARED_AND_SEALED'); assert.equal(output.replay, true);
  assert.ok(!actions.includes('seal'));
});
