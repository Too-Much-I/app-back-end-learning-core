/* global Mongo, NumberInt, print, quit */
'use strict';
// Explicit offline-prepared operator tool. No DDL, business writes, automatic retry or drain claim.
const DATABASE = 'to-teacher-app';
const HOST = 'cluster0.nuuovhj.mongodb.net';
const ENTRIES = 'exam_legacy_manifest_entries';
const STATE = 'exam_paid_rollout_state';
const LIMIT = 100000;
const LINKS = ['paidReservation', 'creationOperationId', 'billingReservationId',
  'attemptGroupId', 'paidSchemaVersion', 'entitlementSource'];
function check(ok) { if (!ok) throw Error('PRECONDITION_FAILED'); }
function config(env) {
  const mode = env.LC_SEAL_MODE || 'inspect';
  check(['inspect', 'apply'].includes(mode));
  const uri = env.LC_SEAL_URI;
  check(typeof uri === 'string' && uri === uri.trim());
  const parsed = new URL(uri);
  check(parsed.protocol === 'mongodb+srv:' && parsed.hostname === HOST
    && ['', '/', '/' + DATABASE].includes(parsed.pathname) && !parsed.hash);
  for (const [key, value] of parsed.searchParams) {
    const k = key.toLowerCase();
    check(!(['tls', 'ssl'].includes(k) && value.toLowerCase() !== 'true'));
    check(!['tlsinsecure', 'tlsallowinvalidcertificates', 'tlsallowinvalidhostnames'].includes(k));
  }
  const revision = env.LC_SEAL_REVISION;
  check(typeof revision === 'string' && /^[a-z0-9][a-z0-9._-]{0,79}$/.test(revision));
  if (mode === 'apply') {
    for (const name of ['LC_SEAL_APPLY_APPROVED', 'LC_SEAL_WRITERS_DRAINED', 'LC_SEAL_READER_COVERAGE'])
      check(env[name] === 'true');
    check(/^[0-9a-f]{64}$/.test(env.LC_SEAL_EXPECTED_SHA256 || ''));
    check(/^(0|[1-9][0-9]*)$/.test(env.LC_SEAL_EXPECTED_COUNT || '')
      && Number(env.LC_SEAL_EXPECTED_COUNT) <= LIMIT);
    // Reference to separately reviewed evidence, NOT an automatically verified writer fence.
    check(/^[A-Za-z0-9._-]{1,128}$/.test(env.LC_SEAL_DRAIN_EVIDENCE_REF || ''));
  }
  return { mode, uri, revision, expectedHash: env.LC_SEAL_EXPECTED_SHA256,
    expectedCount: Number(env.LC_SEAL_EXPECTED_COUNT) };
}
function summary(ids, revision) {
  check(ids.length <= LIMIT && new Set(ids).size === ids.length);
  for (const id of ids) {
    check(typeof id === 'string' && id.trim().length > 0 && id.length <= 128
      && !/[\u0000-\u001f\u007f-\u009f]/.test(id));
    check(Buffer.from(id, 'utf8').toString('utf8') === id); // reject malformed UTF-16
  }
  const hash = require('node:crypto').createHash('sha256');
  const frame = value => { const b = Buffer.from(value, 'utf8'); const n = Buffer.alloc(4);
    n.writeInt32BE(b.length); hash.update(n); hash.update(b); };
  ['lc-legacy-manifest-v1', 'production', DATABASE, revision].forEach(frame);
  const n = Buffer.alloc(4); n.writeInt32BE(ids.length); hash.update(n);
  [...ids].sort().forEach(frame); // Java String/TreeSet UTF-16 ordering
  return { environment: 'production', database: DATABASE, revision, sha256: hash.digest('hex'), count: ids.length };
}
async function preflight(db) {
  const metadata = await db.getCollectionInfos();
  for (const name of ['exam_sessions', 'exam_creation_operations', ENTRIES, STATE]) {
    const m = metadata.find(x => x.name === name);
    check(m && m.type === 'collection' && !m.options?.capped && !m.options?.timeseries && !m.options?.collation);
    const indexes = await db.getCollection(name).getIndexes();
    const id = indexes.find(x => x.name === '_id_');
    check(id && JSON.stringify(id.key) === '{"_id":1}' && !id.sparse && !id.hidden
      && !id.partialFilterExpression && !id.collation && id.expireAfterSeconds === undefined);
    if ([ENTRIES, STATE].includes(name)) check(indexes.length === 1); // stricter than runtime
  }
}
async function read(db, revision) {
  check(await db.getCollection('exam_creation_operations').countDocuments({ $or: [
    { activeGuard: true }, { state: { $nin: ['SUCCEEDED', 'CANCELED', 'EXPIRED', 'FAILED_TERMINAL'] } }
  ] }, { maxTimeMS: 10000 }) === 0);
  const projection = Object.fromEntries(['_id', ...LINKS].map(k => [k, 1]));
  const sessions = await rows(db.getCollection('exam_sessions'), LIMIT + 1, projection);
  sessions.forEach(s => LINKS.forEach(k => check(s[k] == null)));
  const ids = sessions.map(s => s._id);
  const candidate = summary(ids, revision);
  const states = await rows(db.getCollection(STATE), 2);
  const entries = await rows(db.getCollection(ENTRIES), LIMIT + 1);
  check(states.length <= 1);
  if (!states.length) check(entries.length === 0);
  else {
    const s = states[0];
    check(s._id === 'legacy-v1' && s.status === 'SEALED' && s.writersDrained === true
      && s.readerCoverageVerified === true && s.sealedAt instanceof Date && Number.isFinite(s.sealedAt.getTime()));
    for (const key of Object.keys(candidate)) check(key === 'count' ? Number(s[key]) === candidate[key] : s[key] === candidate[key]);
    entries.forEach(e => check(Object.keys(e).sort().join(',') === '_id,revision' && e.revision === revision));
    check(summary(entries.map(e => e._id), revision).sha256 === candidate.sha256);
  }
  return { ids, candidate, sealed: states.length === 1 };
}
// Await each cursor stage: mongosh's async API is not a synchronous Node driver cursor.
async function rows(collection, limit, projection) {
  let cursor = await collection.find({}, projection);
  cursor = await cursor.limit(limit);
  cursor = await cursor.maxTimeMS(10000);
  return await cursor.toArray();
}
async function run(connection, cfg, int32 = x => x) {
  await preflight(connection.getDB(DATABASE));
  const session = connection.startSession();
  let committing = false;
  const begin = () => session.startTransaction({ readConcern: { level: 'snapshot' },
    writeConcern: { w: 'majority', wtimeout: 10000 }, readPreference: 'primary' });
  try {
    const db = session.getDatabase(DATABASE);
    await begin();
    const first = await read(db, cfg.revision);
    if (cfg.mode === 'inspect') {
      await session.abortTransaction();
      return { result: 'INSPECTED_NOT_APPLIED', ...first.candidate, sealed: first.sealed, writerDrainVerified: false };
    }
    check(first.candidate.sha256 === cfg.expectedHash && first.candidate.count === cfg.expectedCount);
    if (!first.sealed) {
      // Insert-only, one transaction: never repair/replace an existing or partial manifest.
      for (let offset = 0; offset < first.ids.length; offset += 500)
        await db.getCollection(ENTRIES).insertMany(first.ids.slice(offset, offset + 500)
          .map(_id => ({ _id, revision: cfg.revision })), { ordered: true });
      await db.getCollection(STATE).insertOne({ _id: 'legacy-v1', status: 'SEALED', ...first.candidate,
        count: int32(first.candidate.count), writersDrained: true, readerCoverageVerified: true, sealedAt: new Date() });
      committing = true;
      await session.commitTransaction(); // never retry a transaction after an ambiguous commit
      committing = false;
    } else await session.abortTransaction();
    await begin();
    const after = await read(db, cfg.revision);
    check(after.sealed && after.candidate.sha256 === cfg.expectedHash && after.candidate.count === cfg.expectedCount);
    await session.abortTransaction();
    await preflight(connection.getDB(DATABASE));
    return { result: 'SEALED_VERIFIED', ...after.candidate, replay: first.sealed, businessDocumentsModified: false };
  } catch (_) {
    if (!committing) { try { await session.abortTransaction(); } catch (_) { /* no raw diagnostics */ } }
    throw Error(committing ? 'COMMIT_OUTCOME_UNKNOWN' : 'STOPPED_REQUIRES_REVIEW');
  } finally { await session.endSession(); }
}
async function main() {
  try {
    const cfg = config(process.env); delete process.env.LC_SEAL_URI;
    print(JSON.stringify(await run(new Mongo(cfg.uri), cfg, NumberInt)));
  } catch (e) {
    delete process.env.LC_SEAL_URI;
    print(JSON.stringify({ result: e.message === 'COMMIT_OUTCOME_UNKNOWN' ? 'COMMIT_OUTCOME_UNKNOWN' : 'STOPPED_REQUIRES_REVIEW' }));
    quit(1);
  }
}
if (typeof module !== 'undefined') module.exports = { config, summary, preflight, read, run, DATABASE, HOST, ENTRIES, STATE };
if (typeof Mongo !== 'undefined') main();
