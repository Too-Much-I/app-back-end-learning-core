/* global Mongo, NumberInt, print, quit */
'use strict';
// One-off, test-only preparation. Never drop/replace indexes or update/delete business documents.
const LC_TEST_DATABASE = 'to-teacher-learning-core-test';
const LC_TEST_REVISION = 'lc-test-legacy-20261017-r1';
const LC_TEST_HASH = '66508198c1deae88e5b39b6d9ea88d01c70b5250a074d8a671d821933b274070';
const LC_TEST_SPECS = [
  ['exam_creation_operations', 'reservation_recovery_early_due', { 'recovery.schemaVersion': 1, activeGuard: 1, 'recovery.earlyAlert.alertIncidentId': 1, createdAt: 1, _id: 1 }],
  ['exam_creation_operations', 'reservation_recovery_early_pending', { 'recovery.earlyAlert.alertStatus': 1, 'recovery.earlyAlert.alertNextAt': 1, _id: 1 }],
  ['exam_creation_operations', 'reservation_recovery_due', { 'recovery.schemaVersion': 1, activeGuard: 1, 'recovery.status': 1, 'recovery.nextAt': 1, _id: 1 }],
  ['exam_creation_operations', 'reservation_recovery_lease', { 'recovery.schemaVersion': 1, activeGuard: 1, 'recovery.status': 1, 'recovery.leaseUntil': 1, _id: 1 }],
  ['exam_creation_operations', 'reservation_recovery_alert', { 'recovery.alertStatus': 1, 'recovery.alertNextAt': 1, _id: 1 }],
  ['reservation_reconciliation_control', 'reservation_recovery_alert', { 'recovery.alertStatus': 1, 'recovery.alertNextAt': 1, _id: 1 }],
  ['attempt_group_event_outbox', 'uq_attempt_group_session_slot', { sessionId: 1, eventSlot: 1 }, { unique: true }],
  ['attempt_group_event_outbox', 'ix_attempt_group_claim', { status: 1, nextAttemptAt: 1, leaseUntil: 1, _id: 1 }],
  ['attempt_group_event_outbox', 'ttl_attempt_group_outbox', { expiresAt: 1 }, { expireAfterSeconds: 0 }]
];
const LC_TEST_AUX = ['reservation_reconciliation_control', 'attempt_group_event_outbox',
  'attempt_group_publisher_state', 'exam_legacy_manifest_entries', 'exam_paid_rollout_state'];
function lcIndexMatches(index, key, options = {}) {
  return !!index && JSON.stringify(index.key) === JSON.stringify(key)
    && Boolean(index.unique) === Boolean(options.unique) && !index.sparse && !index.hidden
    && index.partialFilterExpression === undefined && index.collation === undefined
    && (options.expireAfterSeconds === undefined ? index.expireAfterSeconds === undefined
      : Number(index.expireAfterSeconds) === options.expireAfterSeconds);
}
function lcStateMatches(state) {
  return !!state && state._id === 'legacy-v1' && state.status === 'SEALED'
    && state.environment === 'test' && state.database === LC_TEST_DATABASE
    && state.revision === LC_TEST_REVISION && state.sha256 === LC_TEST_HASH
    && Number(state.count) === 0 && state.writersDrained === true
    && state.readerCoverageVerified === true && state.sealedAt instanceof Date;
}
async function preparePaidTest() {
  let session; let stage = 'APPROVAL';
  try {
    // These assertions refer to separately recorded human/drain/reader evidence, not automatic proof.
    for (const name of ['LC_TEST_APPLY_APPROVED', 'LC_TEST_OUTBOX_TTL_APPROVED',
      'LC_TEST_WRITER_DRAIN_ATTESTED', 'LC_TEST_READER_COVERAGE_ATTESTED'])
      if (process.env[name] !== 'true') throw Error('approval');
    const uri = process.env.LC_TEST_INVENTORY_URI;
    delete process.env.LC_TEST_INVENTORY_URI;
    const parsed = new URL(uri);
    if (parsed.protocol !== 'mongodb+srv:' || parsed.hostname !== 'cluster0.lkpybbj.mongodb.net'
      || !['', '/', '/' + LC_TEST_DATABASE].includes(parsed.pathname)) throw Error('target');
    for (const [key, value] of parsed.searchParams)
      if ((['tls', 'ssl'].includes(key.toLowerCase()) && value.toLowerCase() !== 'true')
        || ['tlsinsecure', 'tlsallowinvalidcertificates', 'tlsallowinvalidhostnames'].includes(key.toLowerCase()))
        throw Error('tls');
    const hash = require('node:crypto').createHash('sha256');
    for (const value of ['lc-legacy-manifest-v1', 'test', LC_TEST_DATABASE, LC_TEST_REVISION]) {
      const bytes = Buffer.from(value); const length = Buffer.alloc(4); length.writeInt32BE(bytes.length);
      hash.update(length); hash.update(bytes);
    }
    hash.update(Buffer.alloc(4));
    if (hash.digest('hex') !== LC_TEST_HASH) throw Error('hash');
    stage = 'PREFLIGHT';
    const connection = new Mongo(uri);
    const database = connection.getDB(LC_TEST_DATABASE);
    const metadata = await database.getCollectionInfos();
    const names = metadata.map(x => x.name);
    if (!names.includes('exam_sessions') || !names.includes('exam_creation_operations')) throw Error('missing_base');
    for (const name of ['exam_creation_operations', ...LC_TEST_AUX]) {
      if (!names.includes(name)) continue;
      const meta = metadata.find(x => x.name === name);
      if (meta.type !== 'collection' || meta.options?.capped || meta.options?.timeseries
        || meta.options?.collation) throw Error('collection_options');
      const indexes = await database.getCollection(name).getIndexes();
      const id = indexes.find(x => x.name === '_id_');
      if (!id || JSON.stringify(id.key) !== '{"_id":1}' || id.sparse || id.hidden
        || id.partialFilterExpression || id.collation || id.expireAfterSeconds !== undefined) throw Error('id');
      if (LC_TEST_AUX.includes(name) && indexes.some(x => x.expireAfterSeconds !== undefined
        && !(name === 'attempt_group_event_outbox' && x.name === 'ttl_attempt_group_outbox'
          && lcIndexMatches(x, { expiresAt: 1 }, { expireAfterSeconds: 0 })))) throw Error('ttl');
      for (const [collection, indexName, key, options] of LC_TEST_SPECS.filter(x => x[0] === name)) {
        const existing = indexes.find(x => x.name === indexName);
        if (existing && !lcIndexMatches(existing, key, options)) throw Error('index_conflict');
        if (indexes.some(x => x.name !== indexName && JSON.stringify(x.key) === JSON.stringify(key))) throw Error('other_name');
      }
    }
    session = connection.startSession();
    const snapshot = session.getDatabase(LC_TEST_DATABASE);
    const transaction = () => session.startTransaction({ readConcern: { level: 'snapshot' }, writeConcern: { w: 'majority' } });
    async function requireEmptyAndState() {
      for (const name of ['exam_sessions', 'exam_creation_operations', ...LC_TEST_AUX.filter(x => x !== 'exam_paid_rollout_state')])
        if (Number(await snapshot.getCollection(name).countDocuments({}, { maxTimeMS: 10000 })) !== 0) throw Error('nonempty');
      const states = await snapshot.getCollection('exam_paid_rollout_state').find({}).limit(2).maxTimeMS(10000).toArray();
      if (states.length > 1 || (states.length === 1 && !lcStateMatches(states[0]))) throw Error('state_conflict');
      return states[0];
    }
    await transaction(); await requireEmptyAndState(); await session.abortTransaction();
    stage = 'INDEX_INSTALL';
    for (const name of LC_TEST_AUX) if (!names.includes(name)) await database.createCollection(name);
    for (const [name, indexName, key, options] of LC_TEST_SPECS)
      await database.getCollection(name).createIndex(key, { name: indexName, ...options });
    for (const [name, indexName, key, options] of LC_TEST_SPECS) {
      const indexes = await database.getCollection(name).getIndexes();
      if (!lcIndexMatches(indexes.find(x => x.name === indexName), key, options)) throw Error('index_verify');
    }
    stage = 'SEAL';
    await transaction();
    const existing = await requireEmptyAndState();
    if (!existing) await snapshot.getCollection('exam_paid_rollout_state').insertOne({
      _id: 'legacy-v1', status: 'SEALED', environment: 'test', database: LC_TEST_DATABASE,
      revision: LC_TEST_REVISION, sha256: LC_TEST_HASH, count: NumberInt(0),
      writersDrained: true, readerCoverageVerified: true, sealedAt: new Date()
    });
    await session.commitTransaction();
    stage = 'READBACK';
    await transaction();
    const sealed = await requireEmptyAndState();
    if (!lcStateMatches(sealed)) throw Error('readback');
    await session.abortTransaction();
    for (const name of ['exam_legacy_manifest_entries', 'exam_paid_rollout_state']) {
      const indexes = await database.getCollection(name).getIndexes();
      if (indexes.length !== 1 || indexes[0].name !== '_id_' || indexes[0].expireAfterSeconds !== undefined)
        throw Error('manifest_indexes');
    }
    print(JSON.stringify({ result: 'PREPARED_AND_SEALED', database: LC_TEST_DATABASE,
      revision: LC_TEST_REVISION, sha256: LC_TEST_HASH, count: 0, requiredIndexes: LC_TEST_SPECS.length,
      sealedAt: sealed.sealedAt.toISOString(), replay: !!existing, businessDocumentsModified: false }));
  } catch (_) {
    try { if (session) await session.abortTransaction(); } catch (_) { /* preserve uncertain commit */ }
    print(JSON.stringify({ result: 'STOPPED_REQUIRES_REVIEW', stage }));
    quit(1);
  } finally { if (session) await session.endSession(); }
}
if (typeof module !== 'undefined' && module.exports) module.exports = { lcIndexMatches, lcStateMatches, LC_TEST_SPECS };
if (typeof Mongo !== 'undefined') preparePaidTest();
