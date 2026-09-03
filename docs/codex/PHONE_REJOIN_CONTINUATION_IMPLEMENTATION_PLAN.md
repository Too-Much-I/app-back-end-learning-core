# Learning Core phone 재가입 시험 continuation 구현 계획

- 작성일: 2026-09-03
- 대상 저장소: `Too-Much-I/app-back-end-learning-core`
- 기준 브랜치: `develop`
- Learning Core 기준 commit: `4f9e74c`
- 상류 Billing 기준: PR #8 merge commit `7138810`, 구현 commit `b61ebb9`
- 상류 Jira: `TMI-120`
- Learning Core Jira: `TMI-122`
- 상태: Learning Core 구현·로컬 회귀 검증 완료, staging rollout gate 대기

## 1. 5줄 결론

1. phone 재가입은 Guest 계정 통합인 `UserMerged`와 다른 lifecycle이며, Learning Core가 과거 시험 데이터를 이전하지 않는다.
2. 새 target에 `ExamSession`이 하나도 없을 때만 Billing continuation을 조회하고, 200이면 기존 AttemptGroup·mockExamId로 새 replacement Session을 만든다.
3. continuation 결과는 reserve 전에 `ExamCreationOperation`에 불변 snapshot으로 저장해 같은 idempotency key의 모든 재시도가 같은 payload로 수렴하게 한다.
4. reserve·status의 continuation context를 strict 검증하고, 응답 유실은 status로 복구하며 계약 불일치 보상은 status-first 원칙으로 처리한다.
5. 공개 시험 생성 API와 AI·S3·Redis 계약은 바꾸지 않고 별도 default-off flag, reader-first 배포와 staging E2E를 통과한 뒤 활성화한다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 2.1 Billing 병합 확인

2026-09-03 확인 결과 Billing 저장소는 다음 상태다.

- 현재 브랜치: `develop`
- `HEAD`: `7138810`
- `origin/develop`: `7138810`
- merge commit: `7138810 Merge pull request #8 from Too-Much-I/fix/TMI-120-phone-continuation`
- 구현 commit: `b61ebb9 fix(TMI-120): add phone rejoin continuation flow`
- 작업 트리: clean

병합된 Billing 구현과 계약에는 다음 항목이 존재한다.

- `POST /internal/v1/reservations/continuations/phone`
- `PHONE_REJOIN` continuation discovery
- reserve 요청의 `continuationReason`, `continuationId`, `expectedAttemptGroupId`
- reserve·status 응답의 optional `continuationReason`, `continuationId`
- 같은 operation payload hash에 continuation context 포함
- Billing security route와 관련 contract/integration test

기준 문서는 다음과 같다.

- Billing `docs/adr/ADR-003-retained-trial-owner-rebind-contract.md`
- Billing `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`

### 2.2 이 작업은 `UserMerged` consumer를 대체하지 않는다

phone 재가입과 Guest merge는 증명하는 소유권 범위가 다르다.

| 구분 | phone 재가입 | Guest → Member merge |
| --- | --- | --- |
| Identity lifecycle | `TrialOwnerRebindApproved` | `UserMerged` |
| Learning Core event consumer | 추가하지 않음 | 별도 구현 대상 |
| 과거 ExamSession owner 변경 | 금지 | 별도 UserMerged 계약에 따름 |
| 과거 답안·결과·Summary 이전 | 금지 | 별도 UserMerged 계약에 따름 |
| Billing 권리 | retained 무료시험 owner rebind | retained subject owner merge |
| 새 시험 | 새 target `examId` | merge 계약에 따른 기존 이력 처리 |

따라서 이 계획은 기존 `USER_MERGED_CONSUMER_IMPLEMENTATION_PLAN.md`를 삭제하거나 수정해 대체하지 않는다. phone 재가입 target은 Billing이 승인한 같은 consumption과 AttemptGroup을 사용하지만, Learning Core에서는 새 `examId`로 처음부터 응시한다.

### 2.3 최종 사용자 흐름

```text
POST /api/v1/exams
→ JWT sub에서 target userId 확정
→ 기존 ExamCreationOperation replay 확인
→ 기존 durable Session replay 확인
→ 같은 사용자의 active operation 확인
→ target userId의 ExamSession 존재 여부 확인
```

target Session이 하나라도 있거나 phone flag가 꺼져 있으면 기존 생성 saga를 그대로 사용한다.

target Session이 전혀 없고 phone flag가 켜져 있으면 다음을 수행한다.

```text
Billing phone continuation discovery
├─ 204: 기존 INITIAL 준비 및 기존 3-field reserve
└─ 200: Billing mockExamId로 새 examId 준비
        → PHONE_REJOIN context를 operation에 저장
        → 6-field reserve
        → REPLACEMENT strict 검증
        → 새 target Session commit
        → confirm
```

Billing discovery의 204는 무료시험 자격 성공이 아니다. 이후 일반 reserve에서 `ENTITLEMENT_INSUFFICIENT`가 반환될 수 있다.

### 2.4 외부 공개 계약은 변경하지 않는다

다음은 변경하지 않는다.

- `POST /api/v1/exams` URL과 HTTP Method
- Request Body 없음 계약
- optional `Idempotency-Key` header와 Billing saga 활성 환경의 기존 검증 규칙
- 성공 Response DTO와 `BaseResponse`
- 실제 `userId` 비노출
- 시험 `retryCount`
- S3 Object Key·Presigned URL·음성 submit
- Redis Key와 Polling
- Python AI `user_id=examId`
- AI 요청·Callback URL과 JSON

이번 변경은 Learning Core와 Billing 사이의 내부 시험 생성 command만 확장한다.

## 3. 사용자가 결정해야 하는 사항

제품 동작과 wire 계약은 Billing `TMI-120`에서 이미 확정됐으므로 추가 제품 결정은 없다. 구현 전에 관리상 다음 두 항목만 확정하면 된다.

1. Learning Core 전용 Jira는 `TMI-122`로 생성했고 상류 `TMI-120`과 Blocks 관계로 연결했다.
2. `TMI-122`에 한해 phone continuation client·operation metadata·시험 생성 saga 확장을 허용하도록 `AGENTS.md`에 제한적 예외 또는 영구 허용 범위를 추가한다.

구현 기본값은 다음으로 고정한다.

```yaml
app:
  billing:
    creation-saga-enabled: false
    phone-continuation-enabled: false
```

- `phone-continuation-enabled=true`는 `creation-saga-enabled=true`일 때만 허용한다.
- local/test 기본값은 false다.
- staging/prod도 명시적으로 활성화하기 전까지 false다.
- flag를 꺼도 reader-first response decoder는 optional continuation field를 읽을 수 있어야 한다.

## 4. 주요 위험과 미확인 사항

### 4.1 같은 idempotency key에서 discovery 결과가 바뀌는 위험

Billing discovery는 읽기 전용이지만 시간에 따라 204에서 200으로 바뀔 수 있다. 같은 operation이 재시도될 때 discovery를 다시 실행해 payload를 바꾸면 Billing의 payload hash와 충돌하거나 서로 다른 시험이 될 수 있다.

대응:

- 기존 operation과 durable Session replay를 discovery보다 먼저 확인한다.
- discovery가 끝나면 reserve 전에 선택 결과를 `ExamCreationOperation`에 저장한다.
- operation insert 경쟁에서는 unique index winner의 snapshot만 사용한다.
- loser는 자신의 discovery 결과를 버리고 저장된 winner operation을 reload한다.
- PREPARED 이후에는 같은 key로 discovery·mock 선택·sessionId 생성을 다시 하지 않는다.

외부 side effect가 시작되기 전 process가 종료돼 operation insert 자체가 없으면 다음 요청은 새 discovery를 할 수 있다. 이 시점에는 Billing reserve도 Learning Core Session도 생성되지 않았으므로 허용한다.

### 4.2 target Session 존재 판정과 동시 요청

continuation 조회 조건은 `현재 활성 Session 없음`이 아니라 `target userId 소유 ExamSession이 전혀 없음`이다. 완료·폐기 Session이 하나라도 있으면 phone 신규 계정 경로로 추측하지 않는다.

대응:

- `ExamSessionRepository.existsByUserId(userId)` 또는 동등한 최소 projection query를 추가한다.
- active operation partial unique index를 최종 동시성 경계로 유지한다.
- 같은 key와 다른 key의 동시 요청을 각각 테스트한다.
- 기존 local REPLACEMENT가 있으면 항상 기존 흐름이 우선한다.

### 4.3 204 empty body 처리

현재 `SigV4BillingReservationClient`의 공통 success decoder는 빈 body를 계약 오류로 처리한다. 이를 전체 2xx에 느슨하게 허용하면 기존 reserve·confirm·cancel·status 계약이 약해진다.

대응:

- continuation 전용 HTTP 처리에서 정확히 `204 + empty body`만 `Optional.empty()`로 허용한다.
- `200`은 non-empty strict JSON만 허용한다.
- `204`에 body가 있거나 다른 2xx, `200` empty body는 계약 오류로 거절한다.
- 기존 lifecycle endpoint의 non-empty 성공 응답 규칙은 유지한다.

### 4.4 오래된 continuation context

discovery 200 뒤 reserve 전에 owner transition이나 AttemptGroup 상태가 바뀔 수 있다. Billing은 같은 Transaction에서 이를 재검증하고 `409 RESERVATION_STATE_CONFLICT`로 거절한다.

대응:

- 수신한 409를 non-retryable phone operation 실패로 저장한다.
- 같은 idempotency key는 같은 terminal 결과를 재현한다.
- 새로운 사용자 시험 생성 동작은 새 idempotency key에서 discovery부터 다시 시작한다.
- 이미 성공 가능성이 불명확한 transport failure와 명시적 409를 같은 방식으로 처리하지 않는다.

### 4.5 reserve 응답 유실과 계약 불일치 보상

reserve 요청이 Billing에 commit됐지만 응답이 유실되면 Learning Core operation은 아직 PREPARED일 수 있다. 반대로 2xx body가 local snapshot과 다르면 응답의 reservationId를 신뢰할 수 없다.

대응:

- ambiguous transport failure·timeout·일시 오류 뒤 `status(userId, operationId)`로 commit 여부를 조회한다.
- status가 strict snapshot과 일치하는 `RESERVED`이면 local operation을 RESERVED로 전진시킨다.
- `OPERATION_NOT_FOUND` 또는 `COMMAND_PROCESSING`이면 local PREPARED를 유지하고 Retry-After로 재시도한다.
- 계약 불일치 응답의 `reservationId`를 직접 cancel하지 않는다.
- status에서 operationId·sessionId·mockExamId·kind·group·continuation context가 모두 일치한 authoritative RESERVED를 확인한 경우에만 local에 저장하고 기존 cancel 흐름을 실행한다.
- status도 불일치하거나 확인할 수 없으면 mutation을 추측하지 않고 격리·경보하며 Billing hold의 5분 expiry를 안전망으로 둔다.

### 4.6 이전 mockExam catalog 가용성

Billing이 반환한 `mockExamId`는 새 시험지를 선택하라는 힌트가 아니라 기존 AttemptGroup의 authoritative 값이다.

대응:

- 다른 활성 mockExam으로 fallback하거나 순환 선택하지 않는다.
- `MockExamCatalogService.getRequiredExam(mockExamId)`로 exact 시험지와 문항 존재를 확인한다.
- catalog의 `active=false`만으로 기존 continuation을 거절하지 않되, 문서가 없거나 문항이 없으면 fail-closed한다.
- phone target은 과거 Learning Core history를 이전하지 않으므로 `cycleNumber=1`로 시작한다.

### 4.7 trace와 SigV4 서명 순서

현재 시험 생성용 Billing client에는 W3C trace context 주입이 없다. `traceparent 전파 유지`가 아니라 신규 적용 대상이다.

대응:

- 각 Billing HTTP 호출마다 현재 server span을 parent로 별도 CLIENT span을 만든다.
- 새 CLIENT span의 `traceparent`만 unsigned request에 주입한다.
- raw inbound `traceparent`, `tracestate`, baggage를 복사하지 않는다.
- baggage는 전파하지 않는다.
- trace header·Content-Type·Idempotency-Key·URI·body를 확정한 뒤 SigV4를 마지막 논리적 변경 단계에서 수행한다.
- 서명 뒤 request header·URI·body를 변경하지 않는다.
- traceId와 식별자를 metric tag로 넣지 않는다.

### 4.8 배포 순서 불일치

reader가 optional response field를 모르는 상태에서 Billing을 먼저 배포하면 strict decoder가 정상 응답을 거절할 수 있다. Lattice exact route가 없으면 discovery는 403이 된다.

대응은 10장의 rollout 순서를 따른다.

## 5. 현재 작업과 직접 관련된 구현 설계

### 5.1 데이터 모델

`ExamCreationOperation`에 다음 nullable metadata를 추가한다.

```text
continuationReason     null | PHONE_REJOIN
continuationId         null | lowercase UUID v4
expectedAttemptGroupId null | Billing discovery group
expectedMockExamId     null | Billing discovery mock
```

기존 `expectedAttemptGroupId`, `expectedMockExamId`는 일반 local replacement에서도 사용하므로 다음 조합을 명시적으로 구분한다.

| operation 유형 | replacementSourceSessionId | continuationReason | continuationId | expected group/mock |
| --- | --- | --- | --- | --- |
| INITIAL | null | null | null | null |
| 일반 REPLACEMENT | non-null | null | null | non-null |
| PHONE_REJOIN REPLACEMENT | null | `PHONE_REJOIN` | non-null | non-null |

부분 조합이나 일반 replacement와 phone context가 섞인 hybrid 상태는 저장·전송하지 않는다. 기존 document에는 새 필드가 없으므로 모두 null인 기존 operation으로 호환한다. 새 index는 필요하지 않다.

권장 내부 enum:

```text
BillingContinuationReason.PHONE_REJOIN
```

문자열 자유 입력으로 분기하지 않는다.

### 5.2 Billing client 인터페이스

`BillingReservationClient`에 read-only discovery를 추가한다.

```java
Optional<PhoneContinuationSnapshot> findPhoneContinuation(String userId);
```

권장 snapshot:

```text
PhoneContinuationSnapshot(
  continuationReason,
  continuationId,
  attemptGroupId,
  mockExamId
)
```

`reserve`는 기존 인자 네 개를 계속 지원하면서 phone context를 선택적으로 전달할 수 있도록 request command 객체 또는 overload로 확장한다. positional parameter가 계속 늘어나지 않도록 내부 record를 권장한다.

```text
ReserveCommand(
  operationId,
  userId,
  sessionId,
  mockExamId,
  continuationReason?,
  continuationId?,
  expectedAttemptGroupId?
)
```

`ReservationSnapshot`에는 reserve·status에서 읽은 optional `continuationReason`, `continuationId`를 추가한다. confirm·cancel 응답에는 이 두 값을 요구하지 않는다.

### 5.3 strict discovery decoder

요청:

```http
POST /internal/v1/reservations/continuations/phone
Content-Type: application/json

{"userId":"<JWT sub canonical UUID>"}
```

- `Idempotency-Key`를 보내지 않는다.
- userId는 Controller body가 아니라 인증된 `CurrentUserProvider` 결과가 saga로 전달된 값이다.
- redirect를 따라가지 않는다.
- response 상한은 기존 16 KiB를 유지한다.

200 exact field:

```json
{
  "continuationReason": "PHONE_REJOIN",
  "continuationId": "<lowercase UUID v4>",
  "attemptGroupId": "<lowercase UUID v4>",
  "mockExamId": "<nonblank existing mock exam>"
}
```

검증:

- unknown·duplicate·trailing field 거절
- scalar coercion 거절
- null·blank·앞뒤 공백·control character 거절
- `continuationReason` exact enum 확인
- `continuationId` lowercase UUID v4 확인
- `attemptGroupId`는 현행 양 서비스 계약에 맞춰 lowercase UUID v4 확인
- `mockExamId` 최대 길이와 exact text 확인

오류 mapping:

| Billing 결과 | 내부 분류 | 공개 처리 |
| --- | --- | --- |
| 204 empty | no continuation | 기존 INITIAL 계속 |
| 200 valid | phone continuation | snapshot 저장 후 reserve |
| 409 `COMMAND_PROCESSING` | PROCESSING | 기존 시험 생성 processing + Retry-After |
| 503 | TEMPORARILY_UNAVAILABLE | 기존 Billing 일시 오류 + Retry-After |
| 400 | INVALID_REQUEST/CONTRACT | fail-closed, 운영 경보 |
| 401/403 | AUTH_FAILURE | fail-closed, 고정 counter·운영 경보 |
| timeout/connection | TEMPORARILY_UNAVAILABLE | 재시도 가능 |
| malformed success | CONTRACT_ERROR | fail-closed, 운영 경보 |

401/403은 사용자 인증 실패가 아니라 Learning Core task role/Lattice 정책 오류다. credential·Authorization·signed header를 로그에 남기지 않는다.

### 5.4 시험 준비 로직

`BillingExamCreationSaga.findOrPrepare`의 책임을 다음 순서로 정리한다.

1. 같은 `(userId, operationId)` operation 재조회
2. 같은 operation으로 이미 생성된 durable Session 재조회
3. 같은 user의 active operation guard 확인
4. `existsByUserId(userId)` 확인
5. Session이 있거나 phone flag가 off면 기존 `prepareForBilling(userId)` 실행
6. Session이 없고 flag가 on이면 discovery 실행
7. 204면 기존 `prepareForBilling(userId)` 실행
8. 200이면 `preparePhoneReplacement(userId, snapshot)` 실행
9. 완성된 불변 operation을 insert
10. duplicate면 winner operation을 reload하고 자신의 준비 결과 폐기

`preparePhoneReplacement`는 다음을 수행한다.

- 새 server-generated `examId` 생성
- Billing `mockExamId` exact catalog 조회
- `cycleNumber=1`
- `replacementSourceSessionId=null`
- `expectedAttemptGroupId=Billing attemptGroupId`
- `expectedMockExamId=Billing mockExamId`
- `continuationReason=PHONE_REJOIN`
- `continuationId=Billing continuationId`

source userId, source Session과 source 데이터는 조회하지 않는다.

### 5.5 reserve request 조립

operation 유형에 따라 JSON field 집합을 정확히 둘 중 하나로 만든다.

일반 INITIAL/REPLACEMENT:

```json
{
  "userId": "<target userId>",
  "sessionId": "<new examId>",
  "mockExamId": "<local exact value>"
}
```

phone replacement:

```json
{
  "userId": "<target userId>",
  "sessionId": "<new examId>",
  "mockExamId": "<discovery exact value>",
  "continuationReason": "PHONE_REJOIN",
  "continuationId": "<discovery exact value>",
  "expectedAttemptGroupId": "<discovery exact value>"
}
```

- phone 세 field를 일부만 보내지 않는다.
- null field를 JSON에 포함하지 않는다.
- 같은 operation의 모든 transport retry는 byte-equivalent semantic payload를 사용한다.
- 실제 userId를 public Request/Response나 로그에 노출하지 않는다.

### 5.6 reserve 응답 검증 행렬

공통 검증:

- operationId exact match
- reservationId lowercase UUID v4
- reservationStatus `RESERVED`
- sessionId exact match
- mockExamId exact match
- attemptGroupId lowercase UUID v4
- expiresAt non-null이며 현재 시각 이후

유형별 검증:

| 예상 유형 | reservationKind | continuationReason/id | group/mock 검증 |
| --- | --- | --- | --- |
| INITIAL | `INITIAL` | 둘 다 없음 | local operation mock 일치 |
| 일반 REPLACEMENT | `REPLACEMENT` | 둘 다 없음 | local expected group/mock 일치 |
| PHONE_REJOIN | `REPLACEMENT` | `PHONE_REJOIN`과 exact id 필수 | discovery expected group/mock 일치 |

다음을 모두 거절한다.

- INITIAL 예상 중 REPLACEMENT
- 일반 replacement에 phone context 포함
- phone replacement에 continuation field 누락
- phone request에 INITIAL 또는 context 없는 REPLACEMENT
- continuationId/group/mock/session 불일치
- 일부 optional field만 존재

### 5.7 reserve 응답 유실 복구

PREPARED 상태에서 reserve가 일시 실패하면 바로 새 payload를 만들지 않는다.

```text
reserve ambiguous failure
→ status(userId, operationId)
├─ matching RESERVED: operation.markReserved
├─ PROCESSING: PREPARED 유지, Retry-After
├─ OPERATION_NOT_FOUND: PREPARED 유지, 같은 payload 재시도
├─ CANCELED/EXPIRED: local terminal 수렴
└─ mismatched/CONFIRMED: invariant failure, fail-closed
```

status 검증에는 reserve 검증과 같은 continuation 행렬을 적용한다. status가 PHONE_REJOIN operation에 context를 누락하거나 다른 continuationId를 반환하면 성공으로 수렴시키지 않는다.

### 5.8 Session commit과 confirm

strict reserve 성공 뒤 기존 Mongo Transaction을 재사용한다.

새 Session:

```text
examId                 새 target examId
userId                 새 target userId
mockExamId              Billing authoritative existing mock
cycleNumber             1
billingReservationKind  REPLACEMENT
attemptGroupId          Billing authoritative existing group
entitlementState        CONFIRMING → CONFIRMED
status                  ENTITLEMENT_CONFIRMING → IN_PROGRESS
```

- 기존 source Session을 수정하거나 owner를 변경하지 않는다.
- 기존 답안·ExamResult·ExamSummary·Question/Summary Job을 복사하지 않는다.
- 기존 AttemptGroup publisher 상태도 source document에서 새 Session으로 복사하지 않는다.
- 새 Session은 현재 writer flag 정책에 따라 새 local projection을 초기화한다.
- Session commit 이후에만 기존 confirm을 호출한다.
- confirm 응답의 attemptGroupId와 Session 연결을 기존 strict 검증으로 확인한다.

### 5.9 contract mismatch 보상

reserve 2xx body가 예상과 다를 때 처리 순서:

1. mismatched body의 reservationId를 사용하지 않는다.
2. `status(userId, operationId)`를 조회한다.
3. status가 strict exact `RESERVED`면 authoritative reservationId를 operation에 저장한다.
4. operation을 `CANCEL_PENDING`으로 전환하고 기존 cancel/status reconciliation을 사용한다.
5. status가 CANCELED/EXPIRED면 local terminal로 수렴한다.
6. status가 CONFIRMED인데 local Session이 없으면 자동으로 새 Session을 추측 생성하지 않고 invariant alert와 수동 reconciliation 대상으로 둔다.
7. status 자체가 불일치하면 mutation 없이 fail-closed하고 hold expiry를 기다린다.

보상 중에도 source Session이나 Billing AttemptGroup을 직접 변경하지 않는다.

### 5.10 trace·로그·metric

Billing 호출별 CLIENT span 이름 권장안:

```text
billing_phone_continuation
billing_reservation_reserve
billing_reservation_status
billing_reservation_confirm
billing_reservation_cancel
```

허용 구조화 필드:

```text
service=learning-core
operation=<고정 enum>
outcome=<고정 lowercase enum>
traceId=<현재 client span trace id>
durationMs=<monotonic non-negative integer>
```

금지 항목:

- userId, sessionId, examId, attemptGroupId, continuationId
- request/response payload
- Authorization과 SigV4 header·credential
- raw traceparent·tracestate·baggage
- phone·candidate·subjectRefId

metric tag에는 `service`, `operation`, 고정 outcome만 사용한다. traceId, 사용자·operation·group ID와 오류 message를 tag로 사용하지 않는다.

## 6. 예상 변경 파일

실제 구현 시 최소 범위는 다음과 같다.

### 애플리케이션

- `src/main/java/web/tosunsaeng/domain/exams/billing/BillingReservationClient.java`
- `src/main/java/web/tosunsaeng/domain/exams/billing/SigV4BillingReservationClient.java`
- `src/main/java/web/tosunsaeng/domain/exams/billing/BillingSagaProperties.java`
- `src/main/java/web/tosunsaeng/domain/exams/billing/BillingSagaConfigurationValidator.java`
- `src/main/java/web/tosunsaeng/domain/exams/application/BillingExamCreationSaga.java`
- `src/main/java/web/tosunsaeng/domain/exams/application/ExamSessionManager.java`
- `src/main/java/web/tosunsaeng/domain/exams/domain/entity/ExamCreationOperation.java`
- `src/main/java/web/tosunsaeng/domain/exams/domain/repository/ExamSessionRepository.java`
- 필요 시 phone continuation 내부 enum·snapshot·command 파일
- 필요 시 Billing synchronous client trace helper

### 설정

- `src/main/resources/application.yml`
- `src/test/resources/application-test.yml`

### 테스트

- `src/test/java/web/tosunsaeng/domain/exams/billing/SigV4BillingReservationClientTest.java`
- `src/test/java/web/tosunsaeng/domain/exams/billing/BillingSagaConfigurationValidatorTest.java`
- `src/test/java/web/tosunsaeng/domain/exams/application/BillingExamCreationSagaTest.java`
- `src/test/java/web/tosunsaeng/domain/exams/application/BillingExamCreationTransactionServiceTest.java`
- `src/test/java/web/tosunsaeng/domain/exams/application/ExamSessionManagerTest.java`
- 필요 시 operation state와 HTTP component test 신규 파일

### 문서

- `AGENTS.md`: 신규 Jira의 제한된 허용 범위
- `docs/codex/BILLING_RESERVATION_SAGA_IMPLEMENTATION_PLAN.md`: phone continuation 후속 확장 링크
- `docs/codex/CURRENT_STATE.md`
- `docs/codex/WORKLOG.md`

현재 계획만 작성하는 단계에서는 위 애플리케이션·설정·테스트 파일을 수정하지 않는다.

## 7. 구현 순서

### Phase 0. 범위 승인

1. [x] Learning Core 전용 Jira `TMI-122` 생성
2. [x] Jira에 Billing PR #8을 기록하고 `TMI-120` Blocks 관계 연결
3. [x] `AGENTS.md`에 phone continuation capability 영구 허용 범위 추가
4. [x] 공개 계약 불변과 UserMerged 별도 범위 명시

### Phase 1. reader-first 모델

1. continuation enum/snapshot 추가
2. reserve/status response optional field decoder 추가
3. 기존 일반 응답에서 optional field가 없을 때의 회귀 테스트
4. 기존 operation document null 호환 테스트

### Phase 2. discovery client

1. phone continuation request 추가
2. 200/204 전용 decoder 추가
3. error·Retry-After mapping 추가
4. response size·redirect·strict JSON 테스트
5. CLIENT span과 traceparent-before-SigV4 적용

### Phase 3. operation 준비

1. target Session 존재 query 추가
2. local 기존 흐름 우선 분기
3. 204 INITIAL 준비
4. 200 phone replacement 준비
5. continuation context를 operation에 insert
6. duplicate insert winner reload

### Phase 4. reserve·reconciliation

1. 3-field/6-field request 조립 분리
2. 유형별 strict reserve 검증
3. ambiguous reserve failure status 복구
4. status continuation 검증
5. contract mismatch status-first cancel
6. nonretryable stale context terminal 수렴

### Phase 5. Session commit

1. 새 target Session 필드 검증
2. 기존 AttemptGroup 연결
3. source 데이터 불변 검증
4. confirm과 durable replay 검증

### Phase 6. 설정·관측

1. default-off flag 추가
2. flag 조합 startup validation
3. 개인정보 없는 구조화 로그·metric 추가
4. SigV4 최종 서명 component test

### Phase 7. 전체 회귀

1. `./gradlew clean test`
2. 공개 Controller·DTO diff 확인
3. AI·S3·Redis 계약 불변 scan
4. Secret·Token·식별자 로그 scan
5. `git diff --check`

## 8. 필수 테스트 목록

### discovery와 선택

- flag off → discovery 미호출, 기존 흐름
- target Session 존재 → discovery 미호출
- target Session 0 + 204 → 기존 INITIAL
- target Session 0 + 200 → phone REPLACEMENT
- local active/RETAKE Session → 기존 general REPLACEMENT 우선
- 기존 operation replay → discovery 미호출
- durable Session replay → discovery 미호출
- same key 동시 준비 → winner snapshot만 사용
- different key 동시 준비 → active operation unique guard로 한 건만 진행

### HTTP contract

- discovery request exact `{"userId":...}`
- discovery에 Idempotency-Key 없음
- 204 empty 성공
- 204 body 존재 거절
- 200 valid strict decode
- 200 empty/malformed/unknown/duplicate/trailing/coercion 거절
- 409 COMMAND_PROCESSING과 Retry-After
- 503 retry mapping
- 401/403 auth failure 분류
- 16 KiB 초과 거절
- redirect 미추종

### reserve와 status

- normal reserve는 기존 3 field 유지
- phone reserve는 exact 6 field
- continuation 세 field 일부 조합 생성 차단
- phone reserve response exact match
- phone response context 누락·불일치 거절
- INITIAL 예상 중 예상 밖 REPLACEMENT 거절
- general replacement에 예상 밖 phone context 거절
- reserve 응답 유실 후 status RESERVED 복구
- status continuation context 누락·불일치 거절
- 명시적 stale continuation 409 terminal 수렴
- contract mismatch에서 response reservationId 직접 cancel하지 않음
- authoritative status 확인 뒤에만 cancel

### Session과 데이터 격리

- 새 Session userId는 target
- 새 examId와 Billing group/mock 연결
- cycleNumber 1
- source Session owner 불변
- source ExamResult·Summary·Job 불변
- source history가 target 조회에 노출되지 않음
- source audio/object를 새 Session에 복사하지 않음
- no continuation이면 기존 assignment 순환 회귀 없음

### trace·privacy

- origin server span과 Billing client span traceId 동일
- spanId는 서로 다름
- client span traceparent가 서명 전 request에 포함
- SigV4 이후 request 불변
- baggage 미전파
- 로그/span/metric에 userId·sessionId·groupId·continuationId·payload·credential 없음
- metric tag 저카디널리티 유지

## 9. 완료 조건

다음 조건을 모두 만족해야 구현 완료로 본다.

1. Billing PR #8 wire와 Learning Core request/decoder가 exact match한다.
2. phone target은 새 examId로 기존 AttemptGroup·mockExamId에 연결된다.
3. 과거 source Session·답안·결과·Summary·Job은 변경되지 않는다.
4. 같은 idempotency key의 재시도가 동일 operation snapshot으로 수렴한다.
5. 204, 응답 유실, processing, stale context와 contract mismatch가 정의된 상태로 수렴한다.
6. 공개 API·BaseResponse·AI·S3·Redis·retryCount 계약이 바뀌지 않는다.
7. flag 기본값이 off이고 잘못된 flag 조합은 startup에서 거절한다.
8. traceparent가 새 client span으로 전파되고 SigV4가 마지막 변경이다.
9. 개인정보·payload·credential이 로그/span/metric에 없다.
10. `./gradlew clean test`와 `git diff --check`가 성공한다.

## 10. 배포 및 활성화 순서

1. Learning Core optional response/status decoder를 reader-first로 배포한다.
2. discovery client와 phone saga 코드를 `phone-continuation-enabled=false`로 배포한다.
3. Billing `7138810` 포함 버전이 해당 환경에 실제 배포됐는지 확인한다.
4. Lattice auth policy에 Learning Core task role의 exact `POST /internal/v1/reservations/continuations/phone` 권한을 추가한다.
5. unsigned 요청, Identity role과 잘못된 환경 role이 거절되는지 확인한다.
6. staging에서 204 INITIAL, 200 PHONE_REJOIN, 응답 유실, 중복, stale context와 contract mismatch E2E를 실행한다.
7. Learning Core phone caller flag를 canary로 활성화한다.
8. 관측 결과가 정상인 뒤 Identity의 phone owner rebind producer를 단계적으로 활성화한다.

rollback 시 reader support는 유지하고 caller flag만 끈다. 이미 생성·confirm된 target Session을 자동 삭제하거나 source owner로 되돌리지 않는다.

## 11. 명시적 제외 범위

- `TrialOwnerRebindApproved` Learning Core consumer
- source ExamSession owner 변경
- source 답안·결과·Summary·Job migration
- Guest `UserMerged` consumer 구현 또는 계약 변경
- Billing owner rebind·Reservation·AttemptGroup 상태 머신 수정
- 프론트 Request/Response 변경
- 새 공개 API 추가
- 과거 source 시험 조회 허용
- 새로운 무료 Grant·Claim·consumption 생성 로직
- 실제 AWS Lattice/IAM/SG/ECS 리소스 변경·배포
- static AWS credential 추가
- Challenge 기능

## 부록 A. 현재 코드와의 차이

| 현재 구현 | 필요한 변경 |
| --- | --- |
| `findOrPrepare`가 곧바로 `prepareForBilling` 호출 | target Session 0일 때 discovery 선행 |
| operation에 phone reason/id 없음 | continuation 불변 snapshot 추가 |
| Billing client에 reserve/confirm/cancel/status만 있음 | read-only phone discovery 추가 |
| reserve request는 항상 3 field | phone일 때 exact 6 field |
| reserve/status snapshot에 continuation 없음 | optional reason/id reader-first 추가 |
| 공통 2xx decoder가 empty body 거절 | continuation 204 전용 처리 |
| PREPARED reserve transport 실패 뒤 status 복구 없음 | status 기반 commit 관찰 추가 |
| response contract mismatch 즉시 terminal | status-first authoritative cancel/reconciliation |
| 시험 생성 Billing client trace 주입 없음 | 새 CLIENT span traceparent 후 SigV4 |
| creation saga flag 하나 | phone caller default-off flag 추가 |

## 부록 B. 계약 근거

### Billing discovery

```http
POST /internal/v1/reservations/continuations/phone
Content-Type: application/json

{"userId":"<target UUID>"}
```

- 200: `PHONE_REJOIN`, continuationId, attemptGroupId, mockExamId
- 204: 적용 가능한 continuation 없음
- 409 `COMMAND_PROCESSING`: group 처리 중
- 503 `BILLING_TEMPORARILY_UNAVAILABLE`: projection/DB 일시 오류

### Billing reserve

- 일반: userId, sessionId, mockExamId
- phone: 일반 세 field + continuationReason, continuationId, expectedAttemptGroupId
- 같은 operation·같은 canonical payload는 replay
- 같은 operation·다른 payload는 `IDEMPOTENCY_KEY_CONFLICT`
- phone 성공 reserve/status에는 continuationReason/id 포함
- Billing existing AttemptGroup의 group/mock이 authoritative

### Learning Core 보안 경계

- target userId는 사용자 JWT `sub`
- Learning Core → Billing은 VPC Lattice `AWS_IAM` + SigV4
- 실제 userId는 공개 DTO와 AI 요청에 포함하지 않음
- route·method·task role 최소 권한
- request/response payload와 식별자 로그 금지
