# MongoDB maintenance scripts

## TMI-125 UserMerged guard preparation

`user-merged-prepare.js`는 UserMerged consumer를 켜기 전에 기존 Session·Result·Summary
소유자를 읽기 전용으로 점검하고 ACTIVE ownership guard와 owner migration index를 준비한다.
기본은 dry-run이며 URI, credential과 실제 userId를 출력하지 않는다. Apply는 모든 구버전
writer가 drain됐음을 명시적으로 확인한 경우에만 허용한다.

```bash
MONGODB_URI=<secret> MONGODB_DATABASE=<learning-core-db> \
node scripts/mongodb/user-merged-prepare.js

MONGODB_URI=<secret> MONGODB_DATABASE=<learning-core-db> \
USER_MERGED_LEGACY_WRITERS_DRAINED=true \
USER_MERGED_PREPARE_APPLY=true \
node scripts/mongodb/user-merged-prepare.js
```

Apply 전에는 Learning Core 구버전 instance drain, Identity UserMerged publisher OFF, DB backup과
dry-run blocker 0을 확인한다. `exam_results.userId`, `exam_summaries.userId` index와 모든 기존
canonical owner의 ACTIVE guard를 idempotent하게 생성하며 기존 MERGED guard나 충돌 데이터는
자동 변경하지 않는다.

Dry-run은 다음 관계 오류도 건수만 출력하고 한 건이라도 있으면 apply를 차단한다.

- Session이 없는 orphan `ExamResult`와 `ExamSummary`
- Result/Summary의 `userId`와 참조 `ExamSession.userId` 불일치

실제 userId, examId와 문서 ID는 출력하지 않는다. 관계 오류는 올바른 소유자를 자동으로
판단할 수 없으므로 script가 삭제하거나 owner를 덮어쓰지 않는다. read-only inventory와 운영
승인을 거친 별도 보정 계획으로 해결한 뒤 dry-run을 다시 실행한다.

```bash
node --test scripts/mongodb/user-merged-prepare.test.js
```

## TMI-116 Billing 시험 생성 saga indexes

`tmi-116-migrate-billing-exam-saga.js`는 Billing saga feature flag를 켜기 전에
`exam_creation_operations`와 `exam_sessions`에 필요한 unique, lookup, TTL index를 생성한다.
대상 database를 명시적으로 선택한 `mongosh` 세션에서 실행하고 staging/prod에서는
백업과 기존 중복 데이터 점검 후 적용한다. 애플리케이션은 staging/prod에서 flag가 켜졌을 때
필수 index가 없거나 호환되지 않으면 시작을 중단한다.

필수 환경변수는 `MONGODB_URI`, `MONGODB_DATABASE`다. 기본 실행은 dry-run이며 정확히
`TMI116_BILLING_SAGA_INDEXES_APPLY=true`일 때만 index를 생성한다. URI와 credential은
출력하지 않는다.

```bash
mongosh "$MONGODB_URI" --file scripts/mongodb/tmi-116-migrate-billing-exam-saga.js

TMI116_BILLING_SAGA_INDEXES_APPLY=true \
mongosh "$MONGODB_URI" --file scripts/mongodb/tmi-116-migrate-billing-exam-saga.js
```

## Completed-history and retry-attempt read indexes

`create-exam-read-indexes.js` validates and optionally creates the compound indexes used by the
completed exam history and retry-attempt APIs. The application does not create these indexes at
startup. The script is idempotent: an existing compatible index with the same ordered key is
accepted when its name differs. For `exam_summaries`, a differently named longer index is also
accepted when its leading ordered key is `{ examId: 1, _id: -1 }`. The required name with a
different definition and indexes with incompatible options fail without writing.

Hidden indexes are not compatible because the Query Planner does not use them for normal queries.
If the required name already belongs to a hidden index, validation stops before apply. The script
does not automatically drop the index or use `collMod` to unhide it; an administrator must inspect
and resolve that index state explicitly.

Required environment variables:

- `MONGODB_URI`: secret connection URI. The script validates it but never prints it.
- `MONGODB_DATABASE`: exact Learning Core database selected with `getSiblingDB`. Blank values and
  MongoDB system databases are rejected.
- `EXAM_READ_INDEXES_APPLY`: optional. Only the exact value `true` enables index creation; every
  other value runs a dry-run.

Run the default dry-run and review the selected database, collections, compatible indexes,
missing indexes, and conflicts:

```bash
MONGODB_URI=<secret> \
MONGODB_DATABASE=<learning-core-db> \
node scripts/mongodb/create-exam-read-indexes.js
```

After a backup and a clean dry-run, apply explicitly:

```bash
MONGODB_URI=<secret> \
MONGODB_DATABASE=<learning-core-db> \
EXAM_READ_INDEXES_APPLY=true \
node scripts/mongodb/create-exam-read-indexes.js
```

The script creates and verifies these indexes:

- `exam_sessions`: `{ userId: 1, completedAt: -1, _id: -1 }`
- `exam_summaries`: `{ examId: 1, _id: -1 }`
  - History API가 여러 `examId`의 Summary를 batch 조회하고 각 시험의 최신 `_id` 순으로
    정렬할 때 사용한다.
- `question_grading_jobs`: `{ examId: 1, questionNumber: 1, retryCount: 1 }`
- `exam_results`: `{ examId: 1, questionNumber: 1, retryCount: 1 }`

Staging 또는 운영에서 명시적으로 apply한 뒤에는 실제 데이터와 대표 `examId`로 다음
`explain("executionStats")`를 실행한다. 이 저장소의 로컬 테스트는 실제 apply나 explain을
대체하지 않는다.

```javascript
db.exam_summaries
  .find({
    examId: {
      $in: [
        "exam-id-1",
        "exam-id-2"
      ]
    }
  })
  .sort({
    examId: 1,
    _id: -1
  })
  .explain("executionStats")
```

확인 항목:

- `IXSCAN`을 사용하고 `idx_exam_summaries_exam_id_latest`가 선택되는지
- `COLLSCAN`이 없는지
- blocking `SORT` stage가 없거나 Query Planner가 인덱스 정렬을 사용하는지
- `totalDocsExamined`가 불필요하게 전체 컬렉션 크기로 증가하지 않는지

Test the migration logic without connecting to MongoDB:

```bash
node --test scripts/mongodb/create-exam-read-indexes.test.js
```

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
