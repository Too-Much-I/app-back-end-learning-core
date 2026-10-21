/* global Mongo, NumberInt, print */
'use strict';
// Run ONLY on a newly created disposable local replica set. Never accepts remote credentials.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
async function rehearsal() {
const uri = process.env.LC_SEAL_REHEARSAL_URI || '';
assert.match(uri, /^mongodb:\/\/127\.0\.0\.1:[0-9]+\/\?directConnection=true$/);
const connection = new Mongo(uri);
const hello = await connection.getDB('admin').runCommand({ hello: 1 });
assert.equal(hello.setName, 'lcseal');
assert.equal(hello.isWritablePrimary, true);
const context = { require, Buffer, URL, Date, module: { exports: {} } };
const source = fs.readFileSync(path.join(process.cwd(), 'scripts/deployment/seal-production-legacy.js'), 'utf8');
vm.runInNewContext(source, context);
const tool = context.module.exports;
const db = connection.getDB(tool.DATABASE);
assert.equal((await db.getCollectionNames()).length, 0, 'must be fresh disposable database');
for (const name of ['exam_sessions', 'exam_creation_operations', tool.ENTRIES, tool.STATE]) await db.createCollection(name);
await db.exam_sessions.insertMany([{ _id: 'fake-exam-a', active: true }, { _id: 'fake-exam-한글', active: false }]);
const revision = 'lc-local-rehearsal-r1';
const original = EJSON.stringify(await db.exam_sessions.find().sort({ _id: 1 }).toArray());
let passed = 0;
const ok = label => { passed++; print(JSON.stringify({ test: label, passed: true })); };
const inspect = { mode: 'inspect', revision };
const candidate = await tool.run(connection, inspect, NumberInt);
assert.equal(candidate.result, 'INSPECTED_NOT_APPLIED');
assert.equal(candidate.count, 2);
assert.equal(await db.getCollection(tool.STATE).countDocuments({}), 0);
ok('real snapshot inspect is read only');
const apply = { mode: 'apply', revision, expectedHash: candidate.sha256, expectedCount: 2 };
await db.exam_creation_operations.insertOne({ _id: 'fake-active', activeGuard: true, state: 'PREPARED' });
await assert.rejects(tool.run(connection, apply, NumberInt));
assert.equal(await db.getCollection(tool.ENTRIES).countDocuments({}), 0);
await db.exam_creation_operations.deleteOne({ _id: 'fake-active' });
ok('active operation refuses sealing');
await assert.rejects(tool.run(connection, { ...apply, expectedHash: '0'.repeat(64) }, NumberInt));
assert.equal(await db.getCollection(tool.STATE).countDocuments({}), 0);
ok('wrong approved hash makes no writes');

// Real Mongo abort after entries insertion: deliberately fail state write within the session.
const failingConnection = {
  getDB: n => connection.getDB(n),
  startSession: () => {
    const session = connection.startSession();
    return { startTransaction: o => session.startTransaction(o), abortTransaction: () => session.abortTransaction(),
      commitTransaction: () => session.commitTransaction(), endSession: () => session.endSession(),
      getDatabase: n => ({ getCollection: name => {
        if (name !== tool.STATE) return session.getDatabase(n).getCollection(name);
        const c = session.getDatabase(n).getCollection(name);
        return { find: (...a) => c.find(...a), insertOne: async () => { throw Error('injected'); } };
      } }) };
  }
};
await assert.rejects(tool.run(failingConnection, apply, NumberInt));
assert.equal(await db.getCollection(tool.ENTRIES).countDocuments({}), 0);
assert.equal(await db.getCollection(tool.STATE).countDocuments({}), 0);
ok('real transaction abort leaves no partial entries');
let commitCalls = 0;
const lostReplyConnection = { getDB: n => connection.getDB(n), startSession: () => {
  const s = connection.startSession();
  return { getDatabase: n => s.getDatabase(n), startTransaction: o => s.startTransaction(o),
    abortTransaction: () => s.abortTransaction(), endSession: () => s.endSession(),
    commitTransaction: async () => { commitCalls++; await s.commitTransaction(); throw Error('simulated lost response'); } };
} };
await assert.rejects(tool.run(lostReplyConnection, apply, NumberInt), /COMMIT_OUTCOME_UNKNOWN/);
assert.equal(commitCalls, 1);
assert.equal((await tool.run(connection, inspect, NumberInt)).sealed, true);
ok('lost commit response keeps durable seal; no automatic retry');
const result = await tool.run(connection, apply, NumberInt);
assert.equal(result.result, 'SEALED_VERIFIED');
const state = await db.getCollection(tool.STATE).findOne({ _id: 'legacy-v1' });
const countType = await db.getCollection(tool.STATE).aggregate([{ $project: { t: { $type: '$count' } } }]).toArray();
assert.equal(countType[0].t, 'int');
assert.equal(await db.getCollection(tool.ENTRIES).countDocuments({}), 2);
assert.equal(EJSON.stringify(await db.exam_sessions.find().sort({ _id: 1 }).toArray()), original);
ok('nonempty manifest commits atomically with BSON Int32; exams unchanged');
assert.equal((await tool.run(connection, apply, NumberInt)).replay, true);
assert.equal((await db.getCollection(tool.STATE).findOne({ _id: 'legacy-v1' })).sealedAt.getTime(), state.sealedAt.getTime());
ok('same artifact replay preserves first seal');
await db.exam_sessions.insertOne({ _id: 'fake-late-writer' });
await assert.rejects(tool.run(connection, apply, NumberInt));
assert.equal(await db.getCollection(tool.ENTRIES).countDocuments({}), 2);
ok('late writer fails closed without resealing');
await db.exam_sessions.deleteOne({ _id: 'fake-late-writer' });
await db.getCollection(tool.ENTRIES).updateOne({ _id: 'fake-exam-a' }, { $set: { revision: 'wrong' } });
await assert.rejects(tool.run(connection, inspect, NumberInt));
ok('tampered manifest fails readback');
print(JSON.stringify({ result: 'LOCAL_REPLICA_SET_TESTS_PASSED', tests: passed,
  productionConnected: false, candidateHash: candidate.sha256, revision }));
}
rehearsal().catch(error => { print(String(error)); quit(1); });
