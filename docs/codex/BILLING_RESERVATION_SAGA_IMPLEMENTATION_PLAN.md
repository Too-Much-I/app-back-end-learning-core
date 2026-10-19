# Learning Core Billing Reservation·시험 생성 Saga 구현 계획

- 상태: 애플리케이션 구현·로컬 회귀 검증 완료, 운영 활성화 gate 대기
- 작성일: 2026-08-28
- 대상 저장소: `Too-Much-I/app-back-end-learning-core`
- Jira: `TMI-116`
- 선행 Billing 구현: `TMI-110`, `TMI-112`, `TMI-113` 완료
- 관련 계약: `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`, `docs/codex/PHONE_REJOIN_CONTINUATION_IMPLEMENTATION_PLAN.md`, Billing `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, Billing ADR-001·ADR-002·ADR-003

## 1. 정정된 작업 목표

이번 작업의 수정 대상은 Billing consumer가 아니라 Learning Core다.

앱의 한 번의 시험 시작 동작을 필수 `Idempotency-Key`로 식별하고 다음 흐름으로 수렴시킨다.

```text
앱 POST /api/v1/exams + Idempotency-Key
  → Learning Core operation 준비
  → Billing reserve
  → Learning Core ExamSession durable commit
  → Billing confirm
  → 같은 ExamSession 성공 응답
```

목표는 다음 네 가지다.

1. 네트워크 재시도로 시험 Session이나 무료권 소비가 중복되지 않게 한다.
2. Session 저장 실패 시 Billing hold를 cancel/expiry로 복구한다.
3. confirm 응답 유실 시 새 시험을 만들지 않고 status 조회와 같은 operation으로 수렴한다.
4. Billing `attemptGroupId`를 `ExamSession`에 저장해 후속 AttemptGroup 상태 outbox/publisher의 기반을 만든다.

## 2. 왜 AttemptGroup publisher보다 이 작업이 먼저인가

현재 `ExamSession`에는 다음 Billing 연결 정보가 없다.

- `operationId`
- `reservationId`
- `reservationKind`
- `attemptGroupId`
- Billing confirm 상태

따라서 현재 코드만으로는 채점 상태 event가 어느 Billing AttemptGroup에 속하는지 결정할 수 없다. AttemptGroup outbox를 먼저 만들면 event의 aggregate ID와 active Session fencing 근거가 없어 임의 연결이 된다.

올바른 순서는 다음과 같다.

```text
1. Learning Core Idempotency-Key + Reservation saga
2. ExamSession ↔ Billing AttemptGroup durable mapping
3. Learning Core AttemptGroup 상태 판정·outbox/publisher
4. delivery reconciliation과 staging E2E
```

## 3. 현재 코드와 변경점

현재 시험 생성은 다음과 같다.

```text
ExamRestController.createSession()
→ ExamService.createExamSession()
→ ExamSessionManager.startNew(userId)
→ 기존 IN_PROGRESS Session 즉시 ABANDONED
→ 새 ExamSession 즉시 insert
→ Redis PENDING
→ 문제 DTO 조립
→ 200 반환
```

현재 문제점은 다음과 같다.

- 공개 요청에 operation ID가 없다.
- Billing reserve/confirm/cancel/status 호출이 없다.
- Billing 실패 전에 기존 Session을 먼저 abandon한다.
- 응답 유실 재시도가 새 Session을 만들 수 있다.
- confirm 결과 불명 상태를 저장할 곳이 없다.
- Billing group과 Session의 관계가 남지 않는다.

변경 후에는 Billing reserve 성공 전 기존 Session을 abandon하지 않는다. reserve와 로컬 준비가 끝난 뒤 Mongo Transaction에서 기존 Session abandon과 새 Session insert를 함께 처리한다.

## 4. 범위

### 4.1 포함

- `POST /api/v1/exams`의 `Idempotency-Key` header 수신
- Billing 활성 환경에서 lowercase UUID v4 header 필수 검증
- 동일 user/operation replay
- `ExamCreationOperation` command document
- Billing reserve/confirm/cancel/status client port와 adapter
- operation별 고정 `sessionId`, `mockExamId`
- ExamSession에 Billing reservation/group mapping 저장
- `reserve → Session commit → confirm` saga
- confirm 응답 유실의 동기 status 재확인과 same-key replay 복구
- INITIAL과 REPLACEMENT 처리
- 기존 Session abandon과 신규 Session insert의 Mongo Transaction
- Billing 오류의 안정적인 공개 오류 mapping
- feature flag 기본 off와 rollout compatibility
- SigV4/Lattice adapter 경계와 local/test fake adapter
- Mongo index/migration·startup validation
- 단위·MVC·Mongo transaction·HTTP contract test

### 4.2 제외

- Billing AttemptGroup status event consumer 구현
- Learning Core `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE` outbox/publisher
- background reconciliation scheduler와 운영 repair command
- `UserMerged` consumer
- Billing owner rebind
- 결제, paid credit, subscription, coupon
- 기존 시험 submit·S3·Redis·AI request/Callback 계약 변경
- 기존 공개 성공 Response DTO 필드 추가·삭제
- 실제 AWS Lattice/IAM/SG/ECS 리소스 생성·배포
- Git commit·push와 Jira 생성·상태 변경

## 5. 공개 API 계약

### 5.1 목표 계약

```http
POST /api/v1/exams
Authorization: Bearer <access token>
Idempotency-Key: 018f6f36-2f42-4bf5-8c17-0be35de4872c
```

- Request Body 없음 유지
- 성공 HTTP status와 `BaseResponse<CreateSessionResult>` 유지
- `CreateSessionResult`의 `examId`, `title`, `questions` 유지
- 실제 `userId`, `reservationId`, `attemptGroupId`를 응답에 추가하지 않음
- header는 lowercase UUID v4만 허용
- 한 번의 의도적 시작/restart마다 새 key
- 같은 동작의 timeout·connection retry에는 같은 key

### 5.2 rollout compatibility

feature flag는 기본 off다.

```yaml
app:
  billing:
    creation-saga-enabled: false
```

- flag off: 기존 시험 생성 흐름과 header optional을 유지한다.
- flag on: `Idempotency-Key`를 필수로 검증하고 Billing saga만 사용한다.
- local/test Legacy 인증 모드는 flag off에서 기존 무헤더 흐름을 유지한다.
- staging에서 새 앱 header 전송을 확인한 뒤에만 flag를 켠다.
- production 구버전 앱 drain/최소 지원 버전 적용 전에 flag를 켜지 않는다.

최종 목표 계약은 flag on이며 무헤더 요청은 `400 IDEMPOTENCY_KEY_INVALID`다. flag를 켠 뒤 무헤더 fallback으로 Billing을 우회하지 않는다.

### 5.3 공개 오류

기존 `BaseResponse` 구조를 유지하고 Billing 내부 response body를 그대로 노출하지 않는다.

| HTTP | 공개 code | 의미 | 앱 동작 |
| ---: | --- | --- | --- |
| 400 | `IDEMPOTENCY_KEY_INVALID` | header 누락·형식 오류 | 올바른 새 key 생성 |
| 402 | `ENTITLEMENT_INSUFFICIENT` | 무료권/사용권 부족 | 구매·자격 안내, 자동 retry 금지 |
| 409 | `EXAM_CREATION_PROCESSING` | 같은 user의 생성 operation 처리 중 | `Retry-After` 후 같은 key |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` | key 의미 충돌 | 자동 우회 금지, client bug 처리 |
| 429 | `BILLING_RATE_LIMITED` | Billing rate limit | `Retry-After` 후 같은 key |
| 503 | `BILLING_TEMPORARILY_UNAVAILABLE` | timeout·5xx·confirm 불명·내부 불일치 | 같은 key로 retry |

Billing의 correlation ID, reservation 상태, candidate, grant/ledger와 내부 exception을 public `result`나 message에 넣지 않는다.

## 6. 식별자 규칙

| 값 | 생성자 | 저장 위치 | 의미 |
| --- | --- | --- | --- |
| `userId` | Identity JWT `sub` | operation, ExamSession | 실제 owner |
| `operationId` | 앱 `Idempotency-Key` | operation, ExamSession | 한 번의 시험 생성 command |
| `sessionId` | Learning Core | operation, ExamSession, Billing request | 기존 `examId` |
| `mockExamId` | Learning Core | operation, ExamSession | group 동안 고정 문제지 |
| `reservationId` | Billing | operation, ExamSession | Billing hold aggregate |
| `attemptGroupId` | Billing | operation, ExamSession | consumption/replacement group |

- `sessionId = examId` 계약을 유지한다.
- AI `user_id = examId` 계약은 변경하지 않는다.
- Billing에 보내는 `userId`는 인증된 실제 UUID다.
- operation/session/group ID를 서로 바꾸어 저장하지 않는다.

## 7. 신규 `ExamCreationOperation`

collection 권장명은 `exam_creation_operations`다.

```text
commandId
userId
operationId
sessionId
mockExamId
state
reservationId?
reservationKind?
attemptGroupId?
reservationExpiresAt?
sessionCommittedAt?
confirmedAt?
failureCategory?
activeGuard?
createdAt
updatedAt
terminalAt?
purgeAt?
version
```

### 7.1 상태

```text
PREPARED
  → RESERVED
  → SESSION_COMMITTED
  → SUCCEEDED

RESERVED
  → CANCEL_PENDING
  → CANCELED
  → EXPIRED

어느 단계든 복구 불가능한 계약 충돌
  → FAILED_TERMINAL
```

- `SESSION_COMMITTED`에는 confirm 요청 중/응답 불명 상태도 포함한다.
- 외부 HTTP 호출 중이라는 메모리 상태를 별도 영속 enum으로 만들지 않는다.
- terminal command는 7일 뒤 purge할 수 있다.
- `ExamSession.creationOperationId`는 command purge 뒤에도 남겨 장기 replay 충돌을 막는다.

### 7.2 command 멱등성

- 같은 user + same operation: 저장된 `sessionId`, `mockExamId` 재사용
- 같은 user + 다른 operation이 active: `409 EXAM_CREATION_PROCESSING`
- 같은 operation에 새 `sessionId`나 `mockExamId` 생성 금지
- concurrent first request는 unique index 한 건만 승리하고 loser는 winner command 재조회
- `SUCCEEDED` replay는 새 Session/Billing 호출 없이 기존 ExamSession으로 성공 DTO 재조립

## 8. `ExamSession` 변경

내부 필드를 추가한다.

```text
creationOperationId?
billingReservationId?
billingReservationKind?
attemptGroupId?
entitlementState?
entitlementConfirmedAt?
```

`entitlementState` 권장값:

- `LEGACY`: Billing 적용 전 Session
- `CONFIRMING`: local commit 완료, Billing confirm 미확정
- `CONFIRMED`: Billing confirm 확인

`ExamSessionStatus`에는 내부 생성 상태 `ENTITLEMENT_CONFIRMING`을 추가한다.

- local Session commit 시 `status=ENTITLEMENT_CONFIRMING`, `active=true`
- Billing confirm 확인 뒤 `status=IN_PROGRESS`
- cancel/expiry terminal이면 `status=ABANDONED`, `active=false`
- `ENTITLEMENT_CONFIRMING` Session은 submit/upload/result 사용자 흐름에서 진행 가능한 Session으로 취급하지 않음
- 기존 field가 없는 Session은 migration 또는 runtime compatibility에서 `LEGACY`로 해석

외부 DTO에는 위 필드를 노출하지 않는다.

## 9. 문제지와 Session 사전 준비

Billing reserve 전에 다음을 고정한다.

1. `sessionId` 생성
2. `mockExamId` 선택
3. 문제 catalog와 Part 4 table context validation
4. 생성 응답 조립 가능성 확인
5. `ExamCreationOperation.PREPARED` 저장

### 9.1 INITIAL 후보

Billing-linked non-terminal Session이 없으면 기존 completion count·sequence 기반 catalog 선택을 사용한다.

### 9.2 REPLACEMENT 후보

현재 사용자에게 Billing-linked 미완료 Session이 있으면 그 Session의 `mockExamId`를 재사용한다. Billing reserve 응답의 `reservationKind=REPLACEMENT`, 기존 `attemptGroupId`, 같은 `mockExamId`를 exact 검증한다.

Billing이 local 예상과 다른 group/mockExam을 반환하면 임의 보정하지 않고 계약 불일치로 격리한다. 기존 Session은 새 local commit 전까지 abandon하지 않는다.

## 10. 정상 saga

### 10.1 PREPARE

로컬 짧은 Transaction에서 다음을 수행한다.

- same operation 조회·replay 판정
- active different operation 차단
- server-generated `sessionId`, fixed `mockExamId` 준비
- `PREPARED` command insert

외부 Billing 호출을 Mongo Transaction 안에서 실행하지 않는다.

### 10.2 RESERVE

```http
POST /internal/v1/reservations
Idempotency-Key: <operationId>

{
  "userId": "<authenticated user UUID>",
  "sessionId": "<examId>",
  "mockExamId": "<fixed mock exam>"
}
```

응답의 다음 값을 요청/command와 exact 비교한다.

- `operationId`
- `sessionId`
- `mockExamId`
- `reservationStatus=RESERVED`
- non-blank `reservationId`, `attemptGroupId`
- `reservationKind=INITIAL|REPLACEMENT`
- 미래 `expiresAt`

검증 후 command를 `RESERVED`로 CAS 전이한다.

### 10.3 LOCAL SESSION COMMIT

하나의 Mongo Transaction에서 다음을 처리한다.

1. command가 `RESERVED`이고 version 일치하는지 확인
2. 기존 local `IN_PROGRESS` Session을 조건부 `ABANDONED`
3. 새 ExamSession을 `ENTITLEMENT_CONFIRMING`, `active=true`로 insert
4. operation/reservation/group metadata 저장
5. command `SESSION_COMMITTED`와 `sessionCommittedAt` 기록

기존 `uniq_exam_sessions_active_user` partial unique index를 마지막 동시성 방어선으로 유지한다. Transaction 실패 시 새 Session과 abandon이 모두 rollback된다.

### 10.4 CONFIRM

```http
POST /internal/v1/reservations/{reservationId}/confirm
Idempotency-Key: <operationId>

{
  "userId": "<authenticated user UUID>",
  "sessionId": "<examId>",
  "sessionCommittedAt": "<UTC commit timestamp>"
}
```

성공 응답의 operation/reservation/group/session과 `reservationStatus=CONFIRMED`를 exact 검증한다.

### 10.5 LOCAL FINALIZE

짧은 Mongo Transaction에서 다음을 CAS 처리한다.

- ExamSession `ENTITLEMENT_CONFIRMING → IN_PROGRESS`
- `entitlementState=CONFIRMED`, `entitlementConfirmedAt`
- command `SUCCEEDED`, active guard 해제, terminal/purge 시각 기록

commit 뒤 Redis `exam:status:<examId>=PENDING`을 설정하고 기존 성공 DTO를 반환한다.

Redis 실패로 Billing consumption과 Session을 되돌리지 않는다. 같은 operation replay에서 Redis를 재구성할 수 있게 하고 metric/경보를 남긴다.

## 11. 실패와 복구

### 11.1 reserve 실패

- local Session을 만들거나 기존 Session을 abandon하지 않음
- 402는 terminal insufficient outcome
- processing/429/503은 command를 유지하고 같은 key replay 허용
- request payload를 바꾸거나 새 sessionId를 만들지 않음

### 11.2 Session commit 실패

Billing cancel을 호출한다.

```http
POST /internal/v1/reservations/{reservationId}/cancel
Idempotency-Key: <operationId>

{
  "userId": "<authenticated user UUID>",
  "reason": "SESSION_COMMIT_FAILED"
}
```

- cancel 성공: command `CANCELED`
- cancel timeout: `CANCEL_PENDING`, 같은 operation status 재확인
- cancel 호출 자체가 불가능해도 Billing 5분 expiry가 hold를 해제
- 기존 active Session은 local Transaction rollback으로 유지

### 11.3 confirm 응답 유실

confirm timeout/connection reset을 실패로 단정하지 않는다.

```http
POST /internal/v1/reservations/status

{
  "userId": "<authenticated user UUID>",
  "operationId": "<same operation>"
}
```

| Billing status | Learning Core 처리 |
| --- | --- |
| `CONFIRMED` | local finalize 후 기존 Session 성공 반환 |
| `RESERVED` | 같은 key로 confirm 재시도 |
| `CANCELED`/`EXPIRED` | confirming Session을 ABANDONED, terminal failure |
| operation missing | 자동 새 reserve 금지, 503과 reconciliation |
| status timeout | `SESSION_COMMITTED` 유지, 503과 same-key retry |

confirm 불명 상태에서 새 operation/session을 자동 생성하지 않는다.

### 11.4 process crash replay

| local state | replay 처리 |
| --- | --- |
| command 없음 | PREPARED 생성 |
| `PREPARED` | same payload reserve |
| `RESERVED`, Session 없음 | local Session commit |
| `SESSION_COMMITTED` | Billing status/confirm 수렴 |
| `SUCCEEDED` | 기존 Session 성공 DTO 재조립 |
| `CANCEL_PENDING` | Billing status 확인 |
| `CANCELED`/`EXPIRED`/`FAILED_TERMINAL` | 기존 terminal outcome 재현, 새 Session 금지 |
| command purge, Session same operation 존재 | Session metadata로 replay/conflict 판단 |

## 12. Billing client와 SigV4

도메인은 다음 port에만 의존한다.

```text
BillingReservationClient
  reserve(...)
  confirm(...)
  cancel(...)
  status(...)
```

adapter 규칙:

- HTTPS exact configured base URL만 사용
- redirect disabled
- connect timeout 1초, 전체 request timeout 3초 초기값
- HTTP client의 숨은 POST retry disabled
- 실제 전송 JSON byte를 한 번 만든 뒤 SigV4 서명
- signing service 상수 `vpc-lattice-svcs`
- region `ap-northeast-2`
- `DefaultCredentialsProvider`로 ECS application task role 사용
- static AWS key/secret 환경변수 추가 금지
- 서명 뒤 URI/body/signed header 변경 금지
- local/test는 fake client 또는 local stub을 사용하고 실제 AWS 호출 금지

현재 AWS SDK v2 BOM을 유지하고 필요한 signer/auth 모듈만 명시적으로 추가한다. 구현 전 실제 사용 API와 transitive dependency를 확인하며 S3/STS와 무관한 broad SDK를 추가하지 않는다.

## 13. configuration과 startup gate

권장 설정:

```yaml
app:
  billing:
    creation-saga-enabled: false
    base-url: ${BILLING_BASE_URL:}
    connect-timeout: ${BILLING_CONNECT_TIMEOUT:PT1S}
    request-timeout: ${BILLING_REQUEST_TIMEOUT:PT3S}
    region: ${BILLING_AWS_REGION:ap-northeast-2}
```

staging/prod에서 flag가 true면 다음을 startup fail-fast한다.

- base URL이 HTTPS absolute URI
- user-info, query, fragment 없음
- 승인된 환경 host allowlist와 일치
- Mongo Transaction 사용 가능
- 필수 index 존재·정의 일치
- auth mode가 Legacy가 아님

flag false이면 Billing bean이 외부 호출하지 않고 기존 시험 생성 경로를 유지한다.

## 14. Mongo index와 migration

### 14.1 `exam_creation_operations`

| key | option | 목적 |
| --- | --- | --- |
| `{userId:1, operationId:1}` | unique | same operation replay |
| `{userId:1}` | unique partial `{activeGuard:true}` | 사용자별 생성 command 하나 |
| `{purgeAt:1}` | TTL 0 | terminal command 7일 cleanup |
| `{state:1, updatedAt:1}` | non-unique | 후속 reconciliation scan |

### 14.2 `exam_sessions`

- 기존 `uniq_exam_sessions_active_user` 유지
- `{userId:1, creationOperationId:1}` unique partial: operation 장기 dedupe
- `{billingReservationId:1}` unique partial: reservation-session 1:1
- `{attemptGroupId:1, createdAt:-1}` non-unique: group Session 조회/후속 outbox

index는 migration script 기본 dry-run, explicit apply와 startup validator로 관리한다. 자동 index 생성이나 실행 중 drop/recreate를 사용하지 않는다.

기존 Session은 destructive rewrite하지 않는다. missing Billing field를 `LEGACY`로 해석하거나 명시적 backfill하고, active/completed/abandoned 상태와 기존 index를 보존한다.

## 15. transaction 구조

외부 HTTP와 Mongo Transaction을 한 경계로 묶지 않는다.

```text
Tx A: command prepare
HTTP: reserve
Tx B: old Session abandon + new Session insert + command SESSION_COMMITTED
HTTP: confirm/status
Tx C: Session CONFIRMED + command SUCCEEDED
```

- Tx B가 핵심 local atomic boundary다.
- 각 Transaction은 expected state/version CAS를 사용한다.
- `TransientTransactionError`는 bounded retry한다.
- unknown commit result는 command/session을 재조회해 수렴한다.
- duplicate key를 500으로 노출하지 않고 winner operation/session을 재조회한다.

## 16. 기존 SessionManager 리팩터링 경계

현재 `startNew(userId)`가 선택·abandon·insert를 한 번에 수행하므로 다음 책임으로 분리한다.

```text
ExamSessionPreparationService
  - sessionId 생성
  - INITIAL/REPLACEMENT mockExam 후보 선택
  - catalog validation

ExamCreationSagaService
  - operation replay
  - Billing reserve/confirm/cancel/status orchestration

ExamSessionTransactionService
  - 기존 Session 조건부 abandon
  - confirming Session insert
  - confirm/terminal local state CAS
```

기존 시험 조회·채점 서비스에 Billing HTTP 코드를 직접 넣지 않는다. 관련 없는 grading/S3/AI 로직을 리팩터링하지 않는다.

## 17. observability와 개인정보

허용 metric 예시:

- `learning_core.billing.exam_creation` outcome
- Billing action: reserve/confirm/cancel/status
- reservation kind: INITIAL/REPLACEMENT
- latency와 retry/reconciliation category

금지 log/metric tag:

- userId, operationId, reservationId, attemptGroupId, sessionId
- Billing request/response body
- SigV4 Authorization, session token, credential
- candidate, grant, ledger와 balance
- 문제·답안·사용자 음성·transcript

로그에는 action, low-cardinality outcome, HTTP category, latency와 서버 생성 correlation ID만 남긴다. public error에는 Billing correlation ID를 노출하지 않는다.

## 18. 테스트 계획

### 18.1 header/API contract

- flag off 무헤더 기존 성공
- flag on lowercase UUID v4 성공
- missing, uppercase, non-v4, 공백 key 400
- Request Body 없음과 기존 성공 DTO exact 유지
- Response에 userId/reservationId/attemptGroupId 미노출
- public error `BaseResponse` 유지

### 18.2 operation unit test

- first PREPARED와 same-key replay
- same user different active key processing conflict
- concurrent same key winner 재조회
- operation마다 sessionId/mockExamId 고정
- SUCCEEDED replay가 새 Session/Billing 호출 없음
- terminal operation replay가 새 Session을 만들지 않음

### 18.3 saga unit test

- INITIAL reserve→commit→confirm→success
- REPLACEMENT가 같은 mockExamId/group mapping 저장
- reserve 실패 시 기존 Session 유지
- local commit 실패 시 cancel
- cancel timeout 시 CANCEL_PENDING
- confirm timeout→status CONFIRMED→finalize
- status RESERVED→same-key confirm
- status CANCELED/EXPIRED→local abandon
- status missing/timeout→503, mapping 유지
- Billing response ID/enum mismatch fail-closed
- Redis failure와 same-key 재구성

### 18.4 Mongo replica-set integration

- old abandon + new confirming Session + command atomic commit
- Transaction rollback에서 기존 active Session 유지
- same key concurrency에서 command/session 하나
- different key concurrency에서 active command/session 하나
- unique operation/reservation index
- transient transaction retry·unknown commit 재확인
- confirming→confirmed와 cancel terminal CAS race
- command TTL 전후 Session operation 장기 dedupe
- 기존 legacy Session/index 회귀

### 18.5 HTTP adapter contract

- 네 Billing request path/method/header/body exact fixture
- `Idempotency-Key` 동일 전달
- response strict decode와 unknown/missing field 거절
- Retry-After parse
- redirect disabled
- timeout/connection reset/4xx/5xx mapping
- SigV4 service/region/final body hash
- credential·Authorization 로그 미노출
- test가 실제 Billing/AWS를 호출하지 않음

### 18.6 전체 회귀

```bash
./gradlew clean test
```

기존 공개 API, 시험 소유권, Session 순환 배정, active unique index, S3/submit/polling, AI Callback와 `user_id=examId` 테스트를 모두 유지한다.

## 19. 구현 단계

### Phase 0. 저장소 규칙·계약 정렬

- Learning Core `AGENTS.md`에 이번 Billing saga의 명시적 허용 범위 추가
- 공개 header rollout과 error mapping을 프론트 인계서에 반영
- Billing request/response fixture를 소비자 contract로 고정
- Jira 생성은 별도 사용자 승인 후 수행

### Step 1. operation·header 기반

- Idempotency-Key parser
- `ExamCreationOperation`·repository·index 계획
- controller/service signature
- flag off/on API contract test

### Step 2. Session preparation·transaction

- 기존 SessionManager 책임 분리
- fixed session/mockExam preparation
- ExamSession Billing metadata와 confirming state
- old abandon + new insert Transaction

### Step 3. Billing client port·fake adapter

- internal DTO와 strict response decode
- reserve/confirm/cancel/status port
- fake/local contract tests

### Step 4. saga orchestration

- 정상 INITIAL/REPLACEMENT
- same-key replay
- commit failure cancel
- confirm unknown status 수렴
- public error mapping

### Step 5. SigV4 adapter·configuration

- AWS SDK signer/auth 최소 dependency
- HTTPS/Lattice adapter
- timeout·Retry-After·no-hidden-retry
- startup fail-fast와 privacy-safe metrics

### Step 6. migration·transaction·회귀 검증

- dry-run/apply index migration
- replica-set concurrency/failure injection
- 전체 Gradle test
- CURRENT_STATE/WORKLOG와 운영 gate 갱신

## 20. 완료 조건

- [x] Billing 활성 시 `Idempotency-Key` 필수 lowercase UUID v4
- [x] Request Body·성공 DTO·BaseResponse 유지
- [x] same-key retry가 같은 sessionId/mockExamId로 수렴
- [x] different active key가 중복 Session을 만들지 않음
- [x] reserve 전에 기존 Session을 abandon하지 않음
- [x] reserve→Session commit→confirm 순서 보장
- [x] local commit 실패 시 cancel/expiry 복구
- [x] confirm 응답 불명 시 status와 same-key retry
- [x] ExamSession에 operation/reservation/attemptGroup mapping 저장
- [x] confirm 전 Session이 사용자 진행 가능 상태가 아님
- [x] INITIAL/REPLACEMENT와 fixed mockExamId 검증
- [x] Billing 내부 오류의 안정적인 공개 mapping
- [x] 실제 userId/Billing ID/credential 비노출
- [x] Mongo Transaction·unique index·CAS 코드와 Mock 기반 동시성 검증
- [x] SigV4 service `vpc-lattice-svcs`, region/HTTPS/redirect 계약 검증
- [x] feature flag 기본 off
- [x] 기존 시험 API·S3·Redis·AI Callback 회귀 없음
- [x] `./gradlew clean test` 전체 성공
- [x] CURRENT_STATE·WORKLOG 갱신
- [ ] 실제 replica-set index migration·Transaction failure injection staging 검증
- [ ] Lattice/IAM/SG 연결과 INITIAL·REPLACEMENT staging E2E

## 21. 위험과 대응

| 위험 | 대응 |
| --- | --- |
| 응답 유실 retry가 E2/E3를 연속 생성 | user+operation command와 Session long-lived operation index |
| reserve 실패가 기존 시험을 폐기 | reserve 전 abandon 금지, Tx B에서만 교체 |
| Session만 저장되고 Billing 미confirm | confirming state + status/confirm replay |
| confirm 성공했는데 timeout으로 cancel | confirm 불명 시 cancel 금지, status 우선 |
| Billing response ID가 다른 Session에 연결 | operation/session/mock/group exact validation |
| REPLACEMENT가 다른 문제지를 사용 | local 이전 Session snapshot + Billing mockExamId exact check |
| 외부 HTTP를 Transaction 안에서 호출 | 세 local Tx와 HTTP 단계 분리 |
| command TTL 뒤 key 재사용 | ExamSession의 creationOperationId 장기 unique index |
| flag rollout 중 구버전 앱 장애 | default off, frontend header 선배포, staging 검증 뒤 활성화 |
| SigV4 credential 노출 | DefaultCredentialsProvider, request/body/auth logging 금지 |
| saga만 완료하고 production 활성화 | Lattice/IAM/SG·reconciliation·outbox E2E까지 gate 유지 |

## 22. production 활성화 gate

코드 완료만으로 Billing production caller를 활성화하지 않는다.

1. 프론트가 매 시작 동작에 UUID v4 key를 만들고 transport retry에 재사용
2. staging/prod Mongo replica-set과 index migration 검증
3. Billing expiry worker 활성·monitoring 확인
4. Learning Core application task role과 Lattice route 최소 권한
5. reserve 실패, commit rollback, confirm timeout/status 복구 staging E2E
6. INITIAL과 R3 REPLACEMENT E2E
7. wrong role, unsigned, wrong route, direct Billing bypass negative test
8. metric·alert·rollback과 feature flag disable runbook

## 23. 다음 작업

이 saga가 완료되면 `ExamSession.attemptGroupId`를 기준으로 Learning Core AttemptGroup 상태 outbox/publisher를 구현한다.

```text
모든 필수 retryCount=0 submit + durable jobs
  → GRADING outbox

필수 feedback + valid score + summary queryable
  → COMPLETED outbox

복구 policy 최종 소진
  → RETAKE_AVAILABLE outbox
```

그다음 owner rebind/UserMerged 정합성, background reconciliation과 실제 Lattice staging E2E를 진행한다.
