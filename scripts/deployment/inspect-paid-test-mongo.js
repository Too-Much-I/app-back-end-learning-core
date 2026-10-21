/* global Mongo, print, quit */
'use strict';
// Invoked only with --nodb. No default DB, writes, raw identifiers or raw exception output.
(async function () {
  let session;
  try {
    const uri = process.env.LC_TEST_INVENTORY_URI;
    delete process.env.LC_TEST_INVENTORY_URI;
    const parsed = new URL(uri);
    const database = 'to-teacher-learning-core-test';
    if (parsed.protocol !== 'mongodb+srv:' || parsed.hostname !== 'cluster0.lkpybbj.mongodb.net'
        || !['', '/', `/${database}`].includes(parsed.pathname)) throw Error('target');
    const connection = new Mongo(uri);
    const target = connection.getDB(database);
    const names = ['mock_exams', 'reservation_reconciliation_control', 'exam_sessions', 'exam_creation_operations', 'exam_legacy_manifest_entries',
      'exam_paid_rollout_state', 'user_ownership_guards', 'user_merged_inbox_events',
      'exam_results', 'exam_summaries', 'question_grading_jobs', 'summary_grading_jobs',
      'attempt_group_event_outbox', 'attempt_group_publisher_state', 'billing_access_revocations',
      'learning_record_deletion_operations', 'learning_record_deletion_targets',
      'learning_record_billing_continuations'];
    const listed = await target.getCollectionNames();
    const collections = [];
    for (const name of names) collections.push({ name, exists: listed.includes(name),
      indexes: listed.includes(name) ? (await target.getCollection(name).getIndexes()).map(i => ({
        name: i.name, key: i.key, unique: i.unique === true, sparse: i.sparse === true,
        hidden: i.hidden === true, hasPartialFilter: i.partialFilterExpression !== undefined,
        partialFilterExpression: i.partialFilterExpression ?? null, collation: i.collation ?? null,
        ttl: i.expireAfterSeconds === undefined ? null : Number(i.expireAfterSeconds)
      })) : [] });
    session = connection.startSession();
    await session.startTransaction({ readConcern: { level: 'snapshot' }, writeConcern: { w: 'majority' } });
    const snapshot = session.getDatabase(database);
    const count = async (name, query) => Number(await snapshot.getCollection(name).countDocuments(query, { maxTimeMS: 10000 }));
    const snapshotCounts = {
      sessions: await count('exam_sessions', {}),
      activeSessions: await count('exam_sessions', { active: true }),
      billingLinkedSessions: await count('exam_sessions', { $or: ['paidReservation', 'creationOperationId',
        'billingReservationId', 'attemptGroupId', 'paidSchemaVersion', 'entitlementSource'].map(k => ({ [k]: { $ne: null } })) }),
      operations: await count('exam_creation_operations', {}),
      unresolvedOperations: await count('exam_creation_operations', { $or: [{ activeGuard: true },
        { state: { $nin: ['SUCCEEDED', 'CANCELED', 'EXPIRED', 'FAILED_TERMINAL'] } }] }),
      manifestEntries: await count('exam_legacy_manifest_entries', {}),
      rolloutStates: await count('exam_paid_rollout_state', {})
    };
    const ids = await snapshot.getCollection('exam_sessions').find({}, { _id: 1 }).limit(100001).maxTimeMS(10000).toArray();
    let manifestCandidate = null;
    if (snapshotCounts.unresolvedOperations === 0 && snapshotCounts.billingLinkedSessions === 0
        && ids.length === snapshotCounts.sessions && ids.length <= 100000) {
      const values = ids.map(x => x._id);
      if (values.some(x => typeof x !== 'string' || !x.trim() || x.length > 128
          || /[\u0000-\u001f\u007f-\u009f]/u.test(x)
          || Buffer.from(x, 'utf8').toString('utf8') !== x)
          || new Set(values).size !== values.length) throw Error('invalid_ids');
      const revision = 'lc-test-legacy-20261017-r1';
      const hash = require('node:crypto').createHash('sha256');
      const frame = value => { const b = Buffer.from(value, 'utf8'); const n = Buffer.alloc(4);
        n.writeInt32BE(b.length); hash.update(n); hash.update(b); };
      ['lc-legacy-manifest-v1', 'test', database, revision].forEach(frame);
      const countBytes = Buffer.alloc(4); countBytes.writeInt32BE(values.length); hash.update(countBytes);
      values.sort().forEach(frame);
      manifestCandidate = { environment: 'test', database, revision, count: values.length,
        sha256: hash.digest('hex'), sealed: false, writerDrainAttested: false };
    }
    await session.abortTransaction();
    print(JSON.stringify({ database, readOnly: true, observedAt: new Date().toISOString(),
      indexMetadataIsSeparateFromSnapshot: true, collections, snapshotCounts, manifestCandidate }));
  } catch (_) {
    try { if (session) await session.abortTransaction(); } catch (_) { /* no raw errors */ }
    quit(1);
  } finally {
    if (session) await session.endSession();
  }
}());
