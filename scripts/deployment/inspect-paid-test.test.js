const { test } = require('node:test');
const assert = require('node:assert/strict');
const { validateUri, run, HOST, DATABASE, ACCOUNT } = require('./inspect-paid-test.cjs');
const uri = `mongodb+srv://test-user:test-only-password@${HOST}/${DATABASE}?retryWrites=true`;
test('accept exact test URI', () => assert.equal(validateUri(uri), uri));
for (const bad of [undefined, 'bad', uri.replace(HOST, 'other.example'), uri.replace(DATABASE, 'production'),
  `${uri}&tls=false`, `${uri}&tlsAllowInvalidCertificates=true`, `${uri}\n`, uri.replace('mongodb+srv:', 'mongodb:')]) {
  test(`reject unsafe target ${String(bad).includes('test-only') ? 'URI' : typeof bad}`, () => assert.throws(() => validateUri(bad)));
}
test('wrong account stops before secret retrieval', () => {
  let calls = 0;
  assert.throws(() => run(() => { calls++; return { status: 0, stdout: '{"Account":"other"}' }; }, {}), /WRONG_AWS_ACCOUNT/);
  assert.equal(calls, 1);
});
test('AWS raw error and secret never become thrown message', () => {
  assert.throws(() => run(() => ({ status: 1, stderr: uri }), {}), /^Error: AWS_AUTH_UNAVAILABLE$/);
});
test('credential passed only to child environment; safe report returned', () => {
  let calls = 0;
  const report = { database: DATABASE, readOnly: true, collections: [], snapshotCounts: { sessions: 0 } };
  const actual = run((command, args, options) => {
    calls++;
    assert.ok(!JSON.stringify(args).includes('test-only-password'));
    if (calls === 1) return { status: 0, stdout: JSON.stringify({ Account: ACCOUNT }) };
    if (calls === 2) return { status: 0, stdout: JSON.stringify({ SecretString: JSON.stringify({ MONGODB_URI: uri }) }) };
    assert.equal(command, 'mongosh'); assert.equal(options.env.LC_TEST_INVENTORY_URI, uri);
    assert.ok(args.includes('--nodb')); return { status: 0, stdout: JSON.stringify(report) };
  }, {});
  assert.deepEqual(actual, report); assert.equal(calls, 3);
});

test('mongosh inventory awaits reads and aborts read-only snapshot; no identifiers output', async () => {
  const fs = require('node:fs');
  const vm = require('node:vm');
  const source = fs.readFileSync(require('node:path').join(__dirname, 'inspect-paid-test-mongo.js'), 'utf8');
  const actions = []; let output;
  const cursor = { limit: () => cursor, maxTimeMS: () => cursor, toArray: async () => [] };
  const collection = { getIndexes: async () => [{ name: '_id_', key: { _id: 1 } }],
    find: () => cursor,
    countDocuments: async () => { actions.push('count'); return 0; } };
  const db = { getCollectionNames: async () => ['exam_sessions'], getCollection: () => collection };
  const session = { startTransaction: async options => { assert.equal(options.readConcern.level, 'snapshot'); actions.push('start'); },
    getDatabase: name => { assert.equal(name, DATABASE); return db; },
    abortTransaction: async () => actions.push('abort'), endSession: async () => actions.push('end') };
  const env = { LC_TEST_INVENTORY_URI: uri };
  await vm.runInNewContext(source, { URL, Buffer, require, process: { env }, Mongo: function () {
    return { getDB: name => { assert.equal(name, DATABASE); return db; }, startSession: () => session };
  }, print: value => { output = value; }, quit: () => { throw Error('unexpected failure'); } });
  const report = JSON.parse(output);
  assert.equal(report.readOnly, true); assert.equal(report.snapshotCounts.sessions, 0);
  assert.ok(Object.values(report.snapshotCounts).every(Number.isFinite));
  assert.deepEqual(actions, ['start', ...Array(7).fill('count'), 'abort', 'end']);
  assert.equal(env.LC_TEST_INVENTORY_URI, undefined);
  assert.equal(report.manifestCandidate.count, 0);
  assert.equal(report.manifestCandidate.sealed, false);
  assert.equal(report.manifestCandidate.writerDrainAttested, false);
  const hash = require('node:crypto').createHash('sha256');
  for (const value of ['lc-legacy-manifest-v1', 'test', DATABASE, 'lc-test-legacy-20261017-r1']) {
    const bytes = Buffer.from(value); const length = Buffer.alloc(4); length.writeInt32BE(bytes.length);
    hash.update(length); hash.update(bytes);
  }
  hash.update(Buffer.alloc(4));
  assert.equal(report.manifestCandidate.sha256, hash.digest('hex'));
  assert.ok(!output.includes('test-only-password'));
});
