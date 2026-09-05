# TMI-125 UserMerged production safety 후속 수정 계획

- 작성일: 2026-09-05
- 대상 저장소: `Too-Much-I/app-back-end-learning-core`
- 대상 Jira: `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`
- 기준 브랜치: `feat/TMI-125-user-merged-ownership-migration`
- 상태: 로컬 구현 완료, Docker replica-set CI 실행 대기
- 선행 구현: PR #28, merge commit `8c8208b`

## 구현 결과 (2026-09-05)

- Billing client의 PhoneContinuation/Reserve/Confirm/Status 응답과 Saga의 discovery·PREPARED operation 경계에 `attemptGroupId` lowercase UUID v4 검증을 적용했다.
- Mongo 준비 script에 orphan Result/Summary와 Session owner mismatch `$lookup` count-only blocker를 추가했다.
- unknown commit을 전용 예외로 분류하고 inbox bounded recheck로 duplicate/conflict/unavailable에 수렴시키며 Spring Transaction wrapper를 빈 `503`으로 매핑했다.
- replica-set 테스트를 4개에서 11개로 확장하고 PR verify 및 staging deploy workflow에 Node·Mongo integration gate를 추가했다.
- `./gradlew clean test --no-daemon` 496개와 Node test 7개는 성공했다. `mongoIntegrationTest` 소스 컴파일은 성공했지만 로컬 Docker daemon이 없어 실제 Testcontainers 실행은 initialization error로 중단됐으므로 CI Docker 환경의 성공 전에는 전체 검증 완료로 보지 않는다.

## 1. 5줄 결론

1. phone continuation discovery의 `attemptGroupId`를 Billing 응답 decode 시점과 `ExamCreationOperation` 저장 직전에 lowercase UUID v4로 이중 검증한다.
2. UserMerged Mongo 준비 script는 orphan Result/Summary와 참조 Session owner 불일치를 count-only로 검사하고 한 건이라도 있으면 apply를 중단한다.
3. `UnknownTransactionCommitResult`는 migration을 재실행하지 않고 `eventId + payloadDigest` inbox 재조회로 `204`, `409`, `503` 중 하나에 수렴시킨다.
4. 실제 replica-set에서 후반 rollback, 진행 중 operation, concurrent duplicate, source/target writer·Callback 경합과 unknown commit을 검증하고 CI 필수 gate로 만든다.
5. 공개 시험 API·DTO·`BaseResponse`, Billing wire schema, AI `user_id=examId`, S3·Redis key와 feature flag 기본 OFF는 변경하지 않는다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 2.1 첫 번째 결함의 정확한 범위

일반 reserve 응답은 이미 [`BillingExamCreationSaga.validateReserved()`](../../src/main/java/web/tosunsaeng/domain/exams/application/BillingExamCreationSaga.java)에서 `attemptGroupId`를 lowercase UUID v4로 검사한 뒤 `markReserved()`를 실행한다.

빠진 경로는 phone continuation discovery다.

```text
Billing continuation discovery 200
→ PhoneContinuationResponse가 attemptGroupId를 일반 문자열로 허용
→ BillingExamCreationSaga.validatePhoneContinuation()도 일반 문자열로 허용
→ ExamCreationOperation.expectedAttemptGroupId에 저장
→ 다음 reserve 단계에서야 잘못된 값이 발견될 수 있음
```

따라서 일반 reserve 검증을 새로 만드는 작업이 아니라, discovery 응답이 durable operation에 들어가기 전에 막고 기존 방어를 모든 Billing 성공 응답에 일관되게 적용하는 작업이다.

### 2.2 unknown commit에서 하지 않을 일

`UnknownTransactionCommitResult`는 commit이 실패했다는 뜻이 아니라, client가 성공 여부를 확정하지 못했다는 뜻이다. 같은 migration을 즉시 다시 실행하면 이미 commit된 owner 이전을 중복 수행할 수 있으므로 mutation을 blind retry하지 않는다.

수렴 규칙은 다음으로 고정한다.

```text
UnknownTransactionCommitResult
→ 같은 eventId inbox를 제한 시간 동안 read-only 재조회
   ├─ PROCESSED + 같은 digest → 성공 확인, 빈 204
   ├─ 같은 eventId + 다른 digest → conflict, 빈 409
   └─ 없음 또는 winner 미확정 → 빈 503 + Retry-After: 1
```

Identity가 `503`을 받으면 같은 `eventId`와 같은 payload로 재시도한다. 다음 요청에서는 inbox가 보이면 duplicate `204`, 계속 보이지 않으면 migration을 새 Transaction에서 다시 시도할 수 있다.

### 2.3 기존 데이터는 자동 보정하지 않는다

orphan 또는 owner mismatch는 단순히 어떤 userId로 덮어써야 하는지 자동 판단할 수 없는 데이터다. 준비 script는 다음만 수행한다.

- 문제 건수 계산
- 실제 userId, examId와 payload를 출력하지 않음
- dry-run과 apply 모두 blocker로 종료
- Mongo 문서 삭제·owner 변경·Session 생성 같은 자동 복구는 하지 않음

문제가 발견되면 별도 read-only inventory와 운영 승인으로 원인을 분류한 뒤 수동 보정 또는 격리 계획을 세운다.

### 2.4 현재 테스트 네 개만으로는 완료가 아니다

현재 replica-set 테스트는 정상 migration과 일부 사전 충돌을 확인하지만, 데이터 일부가 이미 수정된 뒤 실패했을 때 rollback되는지와 실제 동시 write 경합을 증명하지 않는다. 이 후속 작업은 테스트 개수만 늘리는 작업이 아니라 TMI-125가 약속한 원자성·동시성·응답 계약을 실제 Mongo Transaction에서 증명하는 작업이다.

### 2.5 Jira 완료와 production 활성화는 분리한다

TMI-125는 네 수정과 전체 자동 검증이 끝나면 다시 완료할 수 있다. 다음 운영 gate는 Jira 완료 뒤에도 남는다.

- 실제 환경 Mongo topology와 Transaction 지원 확인
- migration dry-run 결과 승인과 apply
- 구버전 writer drain 및 ACTIVE guard backfill
- workload issuer/JWKS와 Identity 재시도 E2E
- direct Transaction P99 1초 이하, 전체 HTTP 2초 미만

위 조건 전 production `writer-enabled`, `consumer-enabled`, `source-deny-enabled`는 OFF다.

## 3. 사용자가 결정해야 하는 사항

### 3.1 구현 전에 추가로 결정할 제품·wire 계약

없다. 다음 값을 기존 계약 그대로 사용한다.

| 항목 | 확정값 |
| --- | --- |
| `attemptGroupId` | lowercase canonical UUID v4 |
| 동일 event commit 확인 | 빈 `204` |
| 동일 eventId·다른 digest | 빈 `409` |
| commit winner 미확정 | 빈 `503`, `Retry-After: 1` |
| non-terminal operation | 빈 `503`, `Retry-After: 5` |
| migration 오염 데이터 | 자동 보정 금지, apply 차단 |
| feature flag | 기본 OFF 유지 |

### 3.2 실행 단계에서만 필요한 운영 결정

dry-run에서 orphan 또는 owner mismatch가 1건 이상 발견될 때만 실제 데이터별 보정 정책을 별도로 승인해야 한다. 이 결정은 현재 코드 구현을 막지 않는다.

## 4. 주요 위험과 미확인 사항

### 4.1 Mongo failpoint 지원

실제 unknown commit driver 경로 테스트에는 `commitTransaction` failpoint가 필요하다. Testcontainers Mongo 7 replica-set에서 `enableTestCommands=1`과 `configureFailPoint` 사용 가능 여부를 먼저 확인한다. 사용할 수 없으면 테스트를 삭제하거나 mock으로 대체하지 않고, 아래 두 계층으로 분리한다.

- 실제 replica-set: commit failure 뒤 전체 mutation 0건과 HTTP `503` 경계 검증
- committed-response-loss simulation: 실제 Transaction을 commit한 뒤 wrapped unknown exception을 던지는 `TransactionOperations` decorator로 inbox `204` 수렴 검증

### 4.2 테스트용 failure injection이 production 코드에 침투할 위험

production Service에 `if (test)` hook이나 단계별 flag를 추가하지 않는다. 후반 rollback은 test fixture에서 repository decorator 또는 proxy가 inbox insert 직전에 예외를 던지게 하여 재현한다. 실제 Mongo Transaction은 그대로 사용한다.

### 4.3 대량 `$lookup` 비용

Result/Summary 전체 `$lookup`은 데이터량에 따라 오래 걸릴 수 있다. 준비 script는 서비스 시작 시 자동 실행하지 않고 운영자가 dry-run으로 실행한다. `examId` index가 없는 collection이 확인되면 쿼리 계획과 실행 시간을 먼저 측정하고, 필요한 index는 별도 migration 항목으로 명시한다.

### 4.4 이미 저장된 잘못된 PREPARED operation

strict 검증을 추가하면 기존에 잘못 저장된 `expectedAttemptGroupId`가 있는 operation은 재시도 시 fail-closed된다. 자동으로 UUID를 생성하거나 group을 교체하지 않는다. 실제 존재 여부는 identifier를 로그에 노출하지 않는 count-only 운영 조회로 확인한다.

### 4.5 CI 자원과 실행 시간

`mongoIntegrationTest`는 Docker image pull과 replica-set 초기화 시간이 필요하다. 일반 unit test와 별도 task는 유지하되 PR required check와 staging deploy 전 gate에서는 반드시 실행한다. timeout 때문에 자동 skip하거나 `continue-on-error`를 사용하지 않는다.

## 5. 현재 작업과 직접 관련된 구현 계획

### Phase 0. 작업 기준 고정

권장 브랜치:

```text
codex/fix-tmi-125-production-safety
```

구현 시작 전 다음을 확인한다.

1. `develop`이 PR #28 merge commit `8c8208b` 이상인지 확인한다.
2. 사용자 소유 기존 문서 변경을 보존한다.
3. TMI-125 Jira가 `진행 중`인지 확인한다.
4. production UserMerged flag가 활성화되지 않았음을 확인한다.

### Phase 1. `attemptGroupId` strict 검증

#### 5.1.1 Billing client 응답 경계

대상:

- `SigV4BillingReservationClient.PhoneContinuationResponse`
- `SigV4BillingReservationClient.ReserveResponse`
- `SigV4BillingReservationClient.ConfirmResponse`
- `SigV4BillingReservationClient.StatusResponse`

변경:

1. `attemptGroupId`에 `requireOpaqueText()`가 아니라 `requireCanonicalUuidV4()`를 적용한다.
2. `mockExamId`는 기존 opaque text 계약을 유지한다.
3. 잘못된 Billing 2xx 응답은 기존 `CONTRACT_ERROR`로 변환한다.
4. request DTO와 JSON field 이름은 변경하지 않는다.

모든 성공 응답 record에 같은 규칙을 적용하는 이유는 discovery만 고쳐도 reserve/status/confirm decode가 약한 상태로 남기 때문이다. Saga의 후속 비교 검증은 방어선으로 그대로 유지한다.

#### 5.1.2 Saga 저장 직전 방어

대상:

- `BillingExamCreationSaga.validatePhoneContinuation()`
- `BillingExamCreationSaga.validatePreparedOperation()`

변경:

1. discovery `attemptGroupId`를 `isLowercaseUuidV4()`로 검사한다.
2. phone continuation과 일반 replacement의 `expectedAttemptGroupId`가 존재하면 UUID v4인지 검사한다.
3. invalid discovery에서는 `sessionManager.preparePhoneReplacement()`와 `operationRepository.insert()`가 호출되지 않아야 한다.
4. 이미 저장된 invalid PREPARED operation replay는 Billing reserve를 호출하지 않고 기존 안정 오류 `BILLING_TEMPORARILY_UNAVAILABLE`로 fail-closed한다.

#### 5.1.3 회귀 테스트

추가·수정:

- `SigV4BillingReservationClientTest`
  - valid lowercase UUID v4 수락
  - 일반 문자열 거절
  - uppercase UUID 거절
  - UUID v1/v5 거절
  - reserve/confirm/status의 invalid group도 `CONTRACT_ERROR`
- `BillingExamCreationSagaTest`
  - invalid discovery에서 operation insert 0회
  - invalid persisted PREPARED operation에서 Billing reserve 0회
  - valid phone continuation snapshot은 기존 흐름 유지

기존 `group-existing` fixture는 계약에 맞는 lowercase UUID v4로 교체한다.

### Phase 2. Mongo owner 관계 사전검사

대상:

- `scripts/mongodb/user-merged-prepare.js`
- `scripts/mongodb/user-merged-prepare.test.js`
- `scripts/mongodb/README.md`

#### 5.2.1 새 inventory 값

다음 count를 추가한다.

```text
orphanResultCount
orphanSummaryCount
resultOwnerMismatchCount
summaryOwnerMismatchCount
```

각 child collection에서 `examId`와 `exam_sessions._id`를 `$lookup`한다.

```text
exam_results.examId  ─┐
                      ├─> exam_sessions._id
exam_summaries.examId ┘
```

판정:

- lookup 결과 0건: orphan
- lookup 결과 1건이고 child.userId != session.userId: owner mismatch
- 일치: 정상

Session `_id=examId`는 현재 `ExamSession.@Id` 계약을 사용한다.

#### 5.2.2 blocker와 출력

`blockers(report)`에 네 count를 연결한다.

```text
orphan ExamResult documents exist
orphan ExamSummary documents exist
ExamResult owner does not match Session owner
ExamSummary owner does not match Session owner
```

`printReport()`는 count만 출력하고 실제 userId, examId, Mongo `_id`를 출력하지 않는다. 하나라도 1 이상이면 dry-run과 apply 모두 exit code 2로 종료하며 collection 생성, guard backfill과 index 생성이 실행되지 않아야 한다.

#### 5.2.3 검증

- pure Node test에서 각 count가 blocker로 변환되는지 검증
- blocker 메시지에 raw identifier가 포함되지 않는지 검증
- 정상 report에서는 기존 missing index 계획이 유지되는지 검증
- staging dry-run에서 네 count와 쿼리 수행 시간을 기록
- 오염 데이터가 있으면 현재 script로 자동 수정하지 않음

### Phase 3. Unknown commit 결과 수렴

대상:

- `UserOwnedTransactionExecutor`
- 신규 내부 예외 `UserOwnedCommitOutcomeUnknownException`
- `UserMergedConsumerService`
- `UserMergedInternalExceptionAdvice`

#### 5.3.1 Transaction 결과 분류

`UserOwnedTransactionExecutor`의 cause-chain 검사를 다음 세 결과로 명시화한다.

```text
TRANSIENT_RETRYABLE
UNKNOWN_COMMIT
NON_RETRYABLE
```

처리 규칙:

1. `UnknownTransactionCommitResult` label이 cause chain 어디에 있든 `UNKNOWN_COMMIT`을 우선한다.
2. unknown commit은 command를 다시 실행하지 않고 원인을 보존한 전용 내부 예외로 전달한다.
3. `TransientTransactionError`, DuplicateKey와 OptimisticLocking은 기존 최대 3회 bounded retry를 유지한다.
4. 일반 RuntimeException은 그대로 전달한다.
5. unknown과 transient label이 함께 있어도 unknown commit 우선으로 blind replay를 막는다.

#### 5.3.2 Consumer inbox 확인

`UserMergedConsumerService`의 DuplicateKey 전용 `resolveConcurrentWinner()`를 공통 read-only `resolveInboxOutcome()`로 변경한다.

호출 대상:

- DuplicateKey 경합
- `UserOwnedCommitOutcomeUnknownException`

기존 250ms bounded recheck와 10ms backoff를 재사용한다. 이 시간 안에 결과가 보이지 않으면 장시간 HTTP 요청을 유지하지 않고 `PROCESSING_UNAVAILABLE`로 전환한다.

수렴 결과:

| inbox 상태 | 결과 | HTTP |
| --- | --- | --- |
| `PROCESSED`, 같은 digest | `DUPLICATE` | `204` |
| 같은 eventId, 다른 digest | `PAYLOAD_CONFLICT` | `409` |
| 없음 | `PROCESSING_UNAVAILABLE` | `503`, Retry-After 1 |
| inbox read DB 오류 | database failure | `503`, Retry-After 1 |

source/target userId로 수렴 여부를 판단하지 않고 eventId와 canonical digest만 사용한다.

#### 5.3.3 HTTP fallback

`UserMergedInternalExceptionAdvice`는 다음을 보장한다.

- custom `PROCESSING_UNAVAILABLE`: 빈 `503`, `Retry-After: 1`
- `DataAccessException`: 빈 `503`, `Retry-After: 1`
- Spring `TransactionException` 계열: 빈 `503`, `Retry-After: 1`

Advice는 UserMerged controller에만 적용되므로 기존 사용자 API 오류 구조를 변경하지 않는다. 프로그래밍 오류 전체를 잡는 `RuntimeException` handler는 추가하지 않는다.

#### 5.3.4 단위·WebMvc 테스트

- wrapped Mongo unknown label을 전용 예외로 분류
- unknown에서 command 호출 횟수 1회
- unknown 뒤 같은 inbox가 보이면 duplicate success
- 다른 digest면 conflict
- 제한 시간 동안 없으면 processing unavailable
- inbox read 실패면 503
- `TransactionSystemException`과 일반 `TransactionException`이 빈 503으로 매핑
- 기존 malformed 400, semantic 422, conflict 409, duplicate/new 204 유지

### Phase 4. Replica-set Transaction 통합 테스트 확장

대상:

- `UserMergedMongoTransactionIntegrationTest`
- 필요 시 user writer/Callback별 별도 `src/mongoIntegrationTest` test class
- test-only repository/transaction decorator

#### 5.4.1 후반 단계 rollback

실제 MongoTransactionManager를 사용하되 inbox repository decorator가 마지막 `insert()` 직전에 예외를 던지게 한다.

실패 후 다음을 모두 확인한다.

- source Session owner 원복
- Result owner 원복
- Summary owner 원복
- source/target guard revision·state 원복
- source Session abandon 변경 원복
- inbox 0건

이 테스트는 production 코드에 test hook을 추가하지 않는다.

#### 5.4.2 non-terminal operation

실제 `exam_creation_operations`에 `activeGuard=true` non-terminal 문서를 저장하고 merge를 호출한다.

- 예외 reason `RETRYABLE_PRECONDITION`
- owner/guard/inbox mutation 0건
- WebMvc 계약에서 `503`, `Retry-After: 5`
- operation을 terminal 처리한 뒤 동일 event 재시도 성공

#### 5.4.3 concurrent duplicate

두 thread를 latch/barrier로 동시에 시작한다.

- 같은 eventId와 digest
- 결과는 `PROCESSED` 1건과 `DUPLICATE` 1건 또는 둘 다 HTTP 의미상 `204`
- inbox는 1건
- owner migration은 최종 한 번의 동일 상태
- 500과 partial mutation 없음

같은 eventId와 다른 digest의 동시 요청은 한 요청만 commit되고 다른 요청은 `409`여야 한다.

#### 5.4.4 source/target writer 경합

실제 `UserOwnedTransactionExecutor`와 Mongo Transaction을 사용하고 guard touch 뒤 command를 latch로 제어한다.

검증할 두 순서:

1. source write commit 우선: merge가 재시도하여 새 문서까지 target owner로 이전
2. merge commit 우선: source write가 재시도 후 `ALREADY_MERGED`로 거절되고 stale source owner 문서 0건

target write 경합은 merge 이후에도 target owner로 보존되고 source 이력 이전과 충돌하지 않아야 한다.

#### 5.4.5 Callback 경합

Feedback, Summary, Azure, SpeechAce Callback command에서 최소 다음을 각각 검증한다.

- Callback commit 우선: merge 재시도 후 새 결과가 target owner
- merge commit 우선: Callback이 Session을 다시 읽어 target owner로 저장
- source Session이 target active 충돌로 abandon된 경우 늦은 Callback은 기존 정책대로 no-op

외부 Redis projection, AI HTTP와 S3는 mock으로 두고 실제 Mongo 결과·Job·guard Transaction만 검증한다.

#### 5.4.6 unknown commit

두 케이스를 분리한다.

1. commit command failpoint로 inbox가 없는 unknown 결과를 만들고 `503`, mutation 0건 또는 미확정 뒤 재조회 가능 상태를 검증
2. 실제 Transaction을 commit한 뒤 unknown wrapper를 던지는 test decorator로 응답 유실을 재현하고 inbox 재조회가 `204`로 수렴하는지 검증

같은 event를 다시 호출했을 때 최종 owner, guard와 inbox가 한 번만 존재해야 한다.

### Phase 5. CI 필수 gate

현재 staging workflow는 `./gradlew clean test`만 실행하므로 `mongoIntegrationTest`가 자동 실행되지 않는다. 다음을 추가한다.

PR verification:

```text
./gradlew clean test --no-daemon
node --test scripts/mongodb/user-merged-prepare.test.js
./gradlew mongoIntegrationTest --no-daemon
git diff --check
```

권장 구성:

1. `.github/workflows/verify.yml`을 pull request용으로 두어 `develop`/`main` 병합 전에 실행한다.
2. `.github/workflows/deploy-staging.yml`에도 Node migration test와 `mongoIntegrationTest`를 이미지 build 전 추가한다.
3. integration test 실패, Docker unavailable과 timeout은 배포 실패로 처리한다.
4. `continue-on-error`, 자동 skip과 실제 staging Mongo 사용을 금지한다.
5. GitHub branch protection에서 verification job을 required check로 지정한다.

실제 Mongo URI, AWS credential과 운영 secret은 CI test에 사용하지 않는다.

### Phase 6. 문서와 Jira 완료 재판정

수정할 문서:

- `docs/codex/USER_MERGED_CONSUMER_IMPLEMENTATION_PLAN.md`
- `docs/codex/USER_MERGED_CONTRACT_DECISIONS.md`
- `scripts/mongodb/README.md`
- `docs/codex/CURRENT_STATE.md`
- `docs/codex/WORKLOG.md`

기존 계획의 완료 체크박스를 실제 검증 결과와 일치시키고, Docker 미실행 상태에서 완료로 표기하지 않는다.

TMI-125 재완료 조건:

1. 네 finding 코드와 테스트 구현
2. `./gradlew clean test` 성공
3. Node migration test 성공
4. Docker `mongoIntegrationTest` 성공
5. PR required verification 성공
6. 기존 공개 API와 외부 계약 diff 없음
7. feature flag 기본 OFF 확인
8. Jira 댓글에 실제 테스트 결과와 남은 운영 gate 기록

## 6. 유지할 외부 계약

다음은 수정하지 않는다.

- 기존 공개 시험 API URL, HTTP method, parameter와 DTO
- 기존 `BaseResponse`
- 실제 userId 비노출
- 시험 `retryCount`
- S3 Object Key, Presigned URL, submit과 Polling
- Redis key와 TTL
- Python AI request/Callback `user_id=examId`
- Billing endpoint와 request/response field 이름
- UserMerged schema v1 payload
- UserMerged 정상·동일 duplicate 빈 `204`
- UserMerged workload JWT 계약

`attemptGroupId` strict 검증은 wire schema 변경이 아니라 이미 승인된 UUID v4 계약을 consumer가 정확히 집행하는 변경이다.

## 7. 테스트 매트릭스

| 영역 | 테스트 | 기대 결과 |
| --- | --- | --- |
| Billing decode | non-UUID group | `CONTRACT_ERROR`, operation 미저장 |
| Billing decode | uppercase/version != 4 | `CONTRACT_ERROR` |
| Saga pre-persist | invalid discovery | Session 준비·operation insert·reserve 0회 |
| Saga replay | invalid PREPARED group | fail-closed, Billing 호출 0회 |
| migration | orphan Result | dry-run/apply exit 2, mutation 0건 |
| migration | orphan Summary | dry-run/apply exit 2, mutation 0건 |
| migration | Result owner mismatch | exit 2, 식별자 비출력 |
| migration | Summary owner mismatch | exit 2, 식별자 비출력 |
| unknown commit | commit 확인됨 | inbox same digest, `204` |
| unknown commit | 다른 payload winner | `409` |
| unknown commit | 미확정 | `503`, Retry-After 1 |
| unknown wrapper | `TransactionSystemException` | 500이 아닌 `503` |
| rollback | inbox insert 직전 실패 | owner·guard·inbox 전부 원복 |
| operation | non-terminal | mutation 0건, `503`, Retry-After 5 |
| duplicate | concurrent same payload | inbox 1건, 모두 204 의미 |
| conflict | concurrent different payload | winner 1건, loser `409` |
| source writer | writer first | 새 데이터까지 target 이전 |
| source writer | merge first | source write 거절 |
| target writer | merge 경합 | target 데이터 보존 |
| Callback 4종 | Callback first/merge first | stale source owner 0건 |
| 계약 회귀 | 공개 API·AI·S3·Redis | 기존 계약 불변 |

## 8. 배포 순서와 rollback

1. 수정 PR의 unit·Node·replica-set CI를 통과한다.
2. `develop` 병합 후 staging image를 배포하되 UserMerged 세 flag는 OFF로 유지한다.
3. migration script를 dry-run하고 네 relational integrity count가 모두 0인지 확인한다.
4. 문제가 있으면 apply하지 않고 데이터 보정 작업을 별도로 승인한다.
5. 정상이라면 구버전 writer drain 이후 migration apply와 guard/index 검증을 수행한다.
6. writer guard를 먼저 활성화하고 기존 API·Callback 회귀를 확인한다.
7. consumer와 source deny를 승인된 순서로 활성화한다.
8. Identity same-event retry, unknown response와 conflict E2E를 수행한다.

이미 처리된 owner migration을 reverse update하는 자동 rollback은 하지 않는다. 문제가 생기면 publisher/consumer를 중지하고 새 event 유입을 막은 뒤 incident별 복구 계획을 세운다.

## 9. 부록 A — 파일별 변경 지도

| 파일 | 예정 변경 |
| --- | --- |
| `SigV4BillingReservationClient.java` | 모든 Billing 성공 response의 group UUID v4 strict decode |
| `BillingExamCreationSaga.java` | discovery와 PREPARED operation 저장 전 group 이중 검증 |
| `SigV4BillingReservationClientTest.java` | invalid UUID contract test |
| `BillingExamCreationSagaTest.java` | invalid snapshot durable 저장 방지 test |
| `user-merged-prepare.js` | orphan/mismatch `$lookup`, report와 blocker |
| `user-merged-prepare.test.js` | blocker와 privacy test |
| `scripts/mongodb/README.md` | dry-run count와 수동 remediation 경계 |
| `UserOwnedTransactionExecutor.java` | transaction result 분류와 unknown 전용 예외 |
| `UserOwnedCommitOutcomeUnknownException.java` | mutation replay 없이 상위 수렴에 전달 |
| `UserMergedConsumerService.java` | duplicate/unknown 공통 inbox bounded recheck |
| `UserMergedInternalExceptionAdvice.java` | transaction wrapper 503 fallback |
| UserMerged unit/WebMvc tests | label·inbox·HTTP 수렴 test |
| `src/mongoIntegrationTest/...` | rollback·operation·duplicate·writer·Callback·unknown commit |
| `.github/workflows/verify.yml` | PR Docker replica-set required verification |
| `.github/workflows/deploy-staging.yml` | deploy 전 Node·Mongo integration gate |

## 10. 부록 B — 구현 금지 사항

- invalid `attemptGroupId`를 새 UUID로 교체하지 않는다.
- orphan Result/Summary를 삭제하거나 임의 Session에 연결하지 않는다.
- unknown commit에서 owner migration command를 같은 요청 안에서 무조건 재실행하지 않는다.
- 테스트를 위해 production 코드에 단계별 failure flag를 추가하지 않는다.
- Docker unavailable을 test success 또는 skip으로 취급하지 않는다.
- 실제 userId, examId, event payload와 Token을 test log·metric·문서에 기록하지 않는다.
- Billing, Identity 저장소와 실제 AWS·Mongo 운영 리소스를 이 작업에서 수정하지 않는다.
