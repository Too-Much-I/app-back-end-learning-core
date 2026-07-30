# MongoDB maintenance scripts

## TMI-31 exam assignment migration

`tmi-31-migrate-exam-assignment.js` validates and optionally backfills the data and indexes used by
sequential exam assignment. It never creates or modifies application data at application startup.
Node.js runs the entrypoint, which invokes `mongosh`; both executables must be available on `PATH`.

The script always requires the MongoDB connection and database variables:

- `MONGODB_URI`: secret MongoDB connection URI. The script never prints it.
- `MONGODB_DATABASE`: exact Learning Core database name. This value is always selected with
  `getSiblingDB`, even if the URI contains a different database. Blank values and the `admin`,
  `local`, and `config` system databases are rejected.
- `TMI31_APPLY`: optional. Only the exact value `true` enables writes; otherwise execution is a
  dry-run.
- `TMI31_LEGACY_WRITER_STOPPED`: required only when `TMI31_APPLY=true`. Its value must be exactly
  `true`, confirming that every legacy Learning Core and Callback writer is stopped and that the
  new version will not receive traffic until migration verification finishes. Apply fails closed
  when this acknowledgement is absent or has any other value.

Run the default dry-run:

```bash
MONGODB_URI=<secret> \
MONGODB_DATABASE=<learning-core-db> \
node scripts/mongodb/tmi-31-migrate-exam-assignment.js
```

The dry-run prints the selected database and target collection names, planned document changes,
legacy completed Session detection from both `exam_summaries` and legacy
`exam_results.totalScore != null`, overlap and duplicate counts, completion timestamp sources,
orphan evidence, excluded catalog papers, catalog ID/sequence problems, and index conflicts. It
does not print the URI or credentials.

Timestamp selection considers every Summary and legacy total-score result for the Session and uses
the earliest trustworthy value at the first available priority:

1. Evidence `completedAt`, `createdAt`, or `updatedAt`
2. actual BSON ObjectId creation timestamp
3. `ExamSession.createdAt` as an explicitly reported approximate fallback

Deterministic string IDs are not interpreted as ObjectIds. Multiple evidence documents are
reported and resolved by the earliest trustworthy timestamp rather than by an arbitrary latest
document. If even the Session creation time is unavailable, the dry-run reports the unresolved
Session and apply stops. It does not invent the current time or mark that completed Session active.

Use this maintenance-window order for production or staging apply:

1. Block Learning Core and legacy Callback-writer traffic.
2. Stop every running legacy Learning Core instance.
3. Back up the selected application database.
4. Run the default migration dry-run.
5. Review the report and reconcile every conflict.
6. Apply with both `TMI31_LEGACY_WRITER_STOPPED=true` and `TMI31_APPLY=true`.
7. Confirm the migration's final cross-collection and index verification succeeds.
8. Start the new Learning Core version.
9. Resume traffic.

After reviewing a clean dry-run and completing steps 1-5, apply explicitly:

```bash
MONGODB_URI=<secret> \
MONGODB_DATABASE=<learning-core-db> \
TMI31_LEGACY_WRITER_STOPPED=true \
TMI31_APPLY=true \
node scripts/mongodb/tmi-31-migrate-exam-assignment.js
```

Apply backfills legacy Sessions completed by either evidence source with `active=false` and the
resolved `completedAt`, marks only Sessions with no completion evidence active, fills legacy
`mockExamId` with `mock_exam_003`, and creates these indexes after all validation succeeds:

- `uniq_exam_sessions_active_user`: `{ userId: 1 }`, unique, partial `{ active: true }`
- `idx_exam_sessions_user_completed_mock_exam`:
  `{ userId: 1, completedAt: 1, mockExamId: 1 }`
- `uniq_mock_exams_mock_exam_id`: `{ mock_exam_id: 1 }`, unique

Immediately before activating each incomplete legacy Session, apply re-reads the Session plus
`exam_summaries` and `exam_results.totalScore != null`. A deleted Session, or one whose `active` or
`completedAt` was already set, is not activated. Newly discovered completion evidence is recorded
as `completion-evidence-detected-during-final-recheck` and is backfilled with `active=false` and the
same historical timestamp policy used by the dry-run. The Session update remains conditional on
both fields still being null or missing.

MongoDB cannot atomically predicate an `exam_sessions` update on documents in the two evidence
collections. `TMI31_LEGACY_WRITER_STOPPED=true` and an actual maintenance window are therefore
required even though the script performs the final re-read. After all updates and index creation,
the script re-reads current data and fails unless every active Session has no completion evidence
and no `completedAt`, each user has at most one active Session, every completed-evidence Session is
inactive, and all migration indexes have their exact expected definitions.

Duplicate `mock_exam_id` documents are reported with sequence/active metadata but are never
rewritten or deleted automatically. Sequence uniqueness is enforced only among assignable papers;
inactive or empty papers are listed as excluded and an unparseable fallback sequence on those
papers does not block index installation. Only assignable papers receive sequence/active updates.
Null, blank, whitespace, or duplicate IDs remain catalog errors because they make lookup
ambiguous. Reconcile invalid IDs, unresolved completion timestamps, multiple reusable Sessions,
and incompatible existing indexes manually, then rerun the dry-run.

Assignable sequences must be Java `Integer` values in the inclusive range `1..2147483647`, whether
stored explicitly or derived from the trailing digits of `mock_exam_id`. Dry-run diagnostics
distinguish `NON_INTEGER_SEQUENCE`, `NON_POSITIVE_SEQUENCE`, `JAVA_INTEGER_OVERFLOW`, and
`UNPARSABLE_SEQUENCE_SUFFIX`. Sequence diagnostics for inactive or empty papers are informational
and do not block apply.

Run this against a backup or staging copy first. The partial unique active-session index is
mandatory for multi-instance concurrency. Staging/prod validate the required unique indexes at
startup and fail closed if they are absent or incompatible, so deploying before a successful
migration apply can prevent application startup. The completion-count index is a performance
requirement and is warned about when missing or incompatible.
