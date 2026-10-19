const { test } = require('node:test');
const assert = require('node:assert/strict');
const { config, summary, run, DATABASE, HOST, ENTRIES, STATE } = require('./seal-production-legacy');
const revision = 'lc-prod-rehearsal-r1';
const env = () => ({ LC_SEAL_URI: `mongodb+srv://fake:fake@${HOST}/${DATABASE}`, LC_SEAL_REVISION: revision });
const approve = () => ({ ...env(), LC_SEAL_MODE: 'apply', LC_SEAL_APPLY_APPROVED: 'true',
  LC_SEAL_WRITERS_DRAINED: 'true', LC_SEAL_READER_COVERAGE: 'true', LC_SEAL_DRAIN_EVIDENCE_REF: 'rehearsal-1',
  LC_SEAL_EXPECTED_COUNT: '2', LC_SEAL_EXPECTED_SHA256: summary(['exam-a', 'exam-b'], revision).sha256 });
function fake(options = {}) {
  let data = { exam_sessions: [{ _id: 'exam-b' }, { _id: 'exam-a' }], [ENTRIES]: [], [STATE]: [], ...options.data };
  let tx;
  const actions = [];
  const db = transactional => ({
    getCollectionInfos: async () => ['exam_sessions', 'exam_creation_operations', ENTRIES, STATE]
      .map(name => ({ name, type: 'collection', options: {} })),
    getCollection(name) {
      const current = () => transactional ? tx : data;
      return {
        getIndexes: async () => [{ name: '_id_', key: { _id: 1 } }, ...(options.ttl && name === ENTRIES
          ? [{ name: 'bad', key: { expiresAt: 1 }, expireAfterSeconds: 0 }] : [])],
        countDocuments: async query => { assert.equal(name, 'exam_creation_operations');
          assert.equal(query.$or[0].activeGuard, true); return options.pending ? 1 : 0; },
        find: () => { const cursor = { limit: () => options.asyncCursor ? Promise.resolve(cursor) : cursor,
          maxTimeMS: () => options.asyncCursor ? Promise.resolve(cursor) : cursor,
          toArray: async () => structuredClone(current()[name] || []) };
          return options.asyncCursor ? Promise.resolve(cursor) : cursor; },
        insertMany: async docs => { assert.equal(name, ENTRIES); actions.push('entries'); current()[name].push(...docs); },
        insertOne: async doc => { assert.equal(name, STATE); actions.push('state');
          if (options.failWrite) throw Error('private-db-error'); current()[name].push(doc); }
      };
    }
  });
  let starts = 0;
  const connection = { getDB: () => db(false), startSession: () => ({
    getDatabase: () => db(true),
    startTransaction: async opts => { assert.equal(opts.readConcern.level, 'snapshot');
      assert.equal(opts.writeConcern.w, 'majority'); starts++;
      if (options.lateWriter && starts === 2) data.exam_sessions.push({ _id: 'late-exam' });
      tx = structuredClone(data); actions.push('begin'); },
    abortTransaction: async () => { tx = undefined; actions.push('abort'); },
    commitTransaction: async () => { data = tx; actions.push('commit');
      if (options.unknown) throw Error('private-commit-error'); },
    endSession: async () => actions.push('end')
  }) };
  return { connection, actions, data: () => data };
}
test('inspect default never writes, emits no exam identifiers', async () => {
  const f = fake(); const result = await run(f.connection, config(env()));
  assert.equal(result.result, 'INSPECTED_NOT_APPLIED'); assert.equal(result.count, 2);
  assert.equal(result.writerDrainVerified, false);
  assert.ok(!f.actions.includes('entries')); assert.ok(!JSON.stringify(result).includes('exam-a'));
});
test('nonempty seal is atomic; same artifact replay does not rewrite', async () => {
  const f = fake(); const cfg = config(approve()); const before = structuredClone(f.data().exam_sessions);
  assert.equal((await run(f.connection, cfg)).result, 'SEALED_VERIFIED');
  const firstTime = f.data()[STATE][0].sealedAt;
  assert.equal((await run(f.connection, cfg)).replay, true);
  assert.equal(f.actions.filter(x => x === 'state').length, 1);
  assert.deepEqual(f.data().exam_sessions, before); assert.deepEqual(f.data()[STATE][0].sealedAt, firstTime);
});
test('mongosh async find/limit/maxTimeMS cursors work', async () => {
  const f = fake({ asyncCursor: true });
  assert.equal((await run(f.connection, config(approve()))).result, 'SEALED_VERIFIED');
});
for (const setup of [{ pending: true }, { ttl: true }, { data: { [ENTRIES]: [{ _id: 'partial', revision }] } },
  { data: { exam_sessions: [{ _id: 'linked', entitlementSource: 'EXAM_PASS' }] } }]) {
  test('refuse precondition before write ' + JSON.stringify(setup), async () => {
    const f = fake(setup); await assert.rejects(run(f.connection, config(approve())));
    assert.ok(!f.actions.includes('entries'));
  });
}
test('changed expected set stops before write', async () => {
  const f = fake(); const cfg = config({ ...approve(), LC_SEAL_EXPECTED_SHA256: '0'.repeat(64) });
  await assert.rejects(run(f.connection, cfg)); assert.ok(!f.actions.includes('entries'));
});
test('write failure rolls back all entries', async () => {
  const f = fake({ failWrite: true }); await assert.rejects(run(f.connection, config(approve())), /STOPPED_REQUIRES_REVIEW/);
  assert.equal(f.data()[ENTRIES].length, 0); assert.equal(f.data()[STATE].length, 0);
});
test('unknown commit never retries or aborts; next invocation can inspect evidence', async () => {
  const f = fake({ unknown: true }); await assert.rejects(run(f.connection, config(approve())), /COMMIT_OUTCOME_UNKNOWN/);
  assert.equal(f.actions.filter(x => x === 'commit').length, 1); assert.ok(!f.actions.includes('abort'));
  assert.equal((await run(f.connection, config(env()))).sealed, true);
});
test('late writer detected on readback; no silent reseal', async () => {
  const f = fake({ lateWriter: true }); await assert.rejects(run(f.connection, config(approve())));
  assert.equal(f.actions.filter(x => x === 'state').length, 1); assert.equal(f.data()[ENTRIES].length, 2);
});
for (const key of ['LC_SEAL_APPLY_APPROVED','LC_SEAL_WRITERS_DRAINED','LC_SEAL_READER_COVERAGE',
  'LC_SEAL_DRAIN_EVIDENCE_REF','LC_SEAL_EXPECTED_COUNT','LC_SEAL_EXPECTED_SHA256']) {
  test('apply requires '+key, () => { const e = approve(); delete e[key]; assert.throws(() => config(e)); });
}
for (const uri of [`mongodb+srv://fake@other.example/${DATABASE}`, env().LC_SEAL_URI+'?tls=false',
  env().LC_SEAL_URI+'?tlsAllowInvalidCertificates=true', env().LC_SEAL_URI+'\n',
  env().LC_SEAL_URI.replace(DATABASE, 'test')]) {
  test('reject wrong/unsafe URI '+uri.split('?').pop(), () => assert.throws(() => config({ ...env(), LC_SEAL_URI: uri })));
}
test('hash sorts UTF16 and length frames; malformed/duplicate IDs rejected', () => {
  assert.deepEqual(summary(['a','b'], revision), summary(['b','a'], revision));
  assert.notEqual(summary(['a','bc'], revision).sha256, summary(['ab','c'], revision).sha256);
  for (const ids of [['a','a'], ['\ud800'], ['\u0085'], [' '], [3], ['a'.repeat(129)]])
    assert.throws(() => summary(ids, revision));
});
