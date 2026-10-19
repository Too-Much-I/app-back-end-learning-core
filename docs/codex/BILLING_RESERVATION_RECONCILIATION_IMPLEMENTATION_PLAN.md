# Learning Core Reservation 장애 자동 복구 구현 계획

- 작성일: 2026-09-08
- 보완일: 2026-09-08 — operation/polling 설명, 기존 Sentry 경보 재사용, 무료권 해제와 그룹 재시작 구분 반영
- 상태: 2026-09-08 사용자 구현 요청에 따라 TMI-128 runtime 구현·로컬 검증 진행. 기본 OFF·production 활성화 미승인. 구현 결과와 운영 경계는 아래 §7 및 RUNBOOK 참조.
- Jira: [TMI-128](https://to-teacher.atlassian.net/browse/TMI-128), 해야 할 일. 기반 이력: TMI-116, TMI-118, TMI-122, TMI-125. Challenge TMI-126은 변경 대상이 아니다.
- 대상: 앱 Learning Core의 `exam_creation_operations`와 해당 ExamSession. Billing 저장소는 계약 확인만 수행한다.

## 1. 5줄 결론

1. 사용자가 같은 요청을 다시 보내지 않아도 멈춘 시험 생성 operation을 찾아 Billing 상태와 로컬 상태를 맞춘다.
2. 승인안은 **Session 저장 후에는 확정 복구, 저장 전에는 미완료 예약 정리**다. worker가 새 시험이나 새 Reservation을 만들지 않는다.
3. HTTP 요청과 worker가 같은 operation의 durable 실행권과 CAS를 사용해야 정상 예약을 잘못 취소하지 않는다.
4. 불명확한 404·Mongo commit·계약 불일치·인증 오류는 성공/취소로 추정하지 않고 재조회 또는 격리한다.
5. 기존 공개 API·Billing wire·AI 계약을 유지하며 default-off로 구현하고 replica-set 경합 테스트와 staging 검증 후 활성화한다.

구현 이후의 파일·설정·활성화 순서는 [TMI-128 RUNBOOK](BILLING_RESERVATION_RECONCILIATION_RUNBOOK.md)을 기준으로 한다. 아래 기존 코드 공백/미착수 설명은 계획 수립 시점의 조사 이력이며 §7에서 구현 상태를 갱신한다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 2.1 현재 코드와 이번 추가의 차이

**확인된 구현:** [BillingExamCreationSaga](../../src/main/java/web/tosunsaeng/domain/exams/application/BillingExamCreationSaga.java)는 같은 `Idempotency-Key`의 사용자 요청이 다시 오면 저장된 operation부터 읽어 reserve/commit/confirm/cancel을 이어간다. `SESSION_COMMITTED`에서 confirm과 status를 재시도하는 동기 복구도 있다.

**현재 공백:** 사용자가 앱을 종료하거나 재요청하지 않으면 이 복구를 실행할 주체가 없다. repository에는 user/operation과 active operation 단건 조회만 있고 Reservation 자동 복구 scheduler·lease·retry metadata가 없다.

이번 worker는 그 공백을 메운다. 이미 구현된 TMI-118의 AttemptGroup outbox 재전송·채점 상태 복구와 다른 기능이다.

`operation`은 매번 쌓는 문자열 로그가 아니라 요청 하나의 현재 진행표다. 이미 존재하는 `exam_creation_operations` 문서를 갱신해 중복 생성 방지와 재시작 후 복구에 사용한다. 이번에는 복구 일정·실행권·사유를 추가하며 매 poll마다 새 operation을 만들지 않는다.

```text
시험 시작 요청 → PREPARED → RESERVED → Session 저장 + SESSION_COMMITTED → Billing confirm
                                  │                                 │
                    저장 전 중단: 안전한 예약 정리       저장 후 중단: 같은 Session 확정 복구
                                  └──────── worker가 durable 상태를 확인 ────────┘
```

### 2.2 사용자에게 보이는 결과

- confirm은 성공했지만 응답이 유실된 경우: 같은 Session을 확정하고 기존 동일 key 재요청으로 같은 결과를 반환한다.
- Session 저장 없이 중단된 경우: 예약 정리를 완료한 뒤 새 시작 요청이 가능해진다. 이전 key를 새 시험 생성에 재사용하지 않는다.
- Billing에서 이미 CANCELED/EXPIRED인 경우: 아직 CONFIRMING인 해당 Session만 기존 정책대로 ABANDONED/비활성 처리한다. 추가 무료 권리나 환불을 만들지 않는다.
- 성공 여부가 불명확하면 계속 진행 중/일시 장애로 응답한다. 자동 복구 때문에 공개 성공 응답을 먼저 주지 않는다.
- 격리된 operation은 안전한 판정 전까지 새 생성과 UserMerged를 차단할 수 있다. 이 제한을 경보와 운영 runbook에 명시한다.

#### 무료권 예약 해제와 같은 그룹 재시작은 다르다

다음은 신규 보상 정책이 아니라 기존 Billing 계약·코드의 동작이다. Learning Core가 무료권 수량을 직접 수정하지 않는다.

| 상황 | Billing 무료권 처리 | Learning Core 처리 |
| --- | --- | --- |
| INITIAL reserve | 기존 1회를 available에서 held로 이동 | 생성 operation 진행 |
| INITIAL 예약 cancel/expire | 같은 Transaction에서 held를 기존 grant의 available로 복원하고 release ledger 기록 | 원격 terminal을 검증한 뒤 해당 CONFIRMING Session/operation 정리 |
| INITIAL confirm | held를 consumed로 이동해 사용 확정 | 같은 Session 확정. 응답 유실이면 status/same-key 복구 |
| confirm 후 허용된 REPLACEMENT | 기존 consumption 재사용, 추가 차감 없음 | 같은 group/mock, 새 operation/examId로 처음부터 응시 |
| REPLACEMENT 예약 cancel/expire | 기존 consumption 유지, available에 1회를 추가하지 않음 | 실패한 새 생성 요청만 정리하고 이전 데이터 보존 |

- 예약 취소는 새로운 무료권 지급이 아니다. `TrialClaim`과 최초 지급 이력은 보존해 전화번호당 중복 지급을 막는다.
- 정상적인 INITIAL 해제 후 현재 자격 조건이 유효하고 로컬 미완료 guard 정리도 끝나면 새 key로 시험을 다시 시작할 수 있다. 취소된 동일 key는 실패 replay이며 신규 시험으로 재사용하지 않는다.
- Session이 DB에 저장돼도 `ENTITLEMENT_CONFIRMING`일 수 있다. “Session 존재”와 “Billing 사용 확정 완료”를 같다고 보지 않는다.
- 이미 CONFIRMED인 예약은 일반 cancel/expiry 대상이 아니다. 404/timeout만 보고 available을 되돌리지 않는다.
- 같은 그룹 재시작은 사용자 새 시작 요청의 기존 기능이다. worker가 이 요청을 대신 만들지 않는다. OPEN/RETAKE_AVAILABLE의 기존 정책을 따르고 GRADING은 기존 채점 복구 우선, COMPLETED를 새 무료 재응시 근거로 사용하지 않는다.
- 원격 cancel/expiry 이후 로컬 정리가 늦어져도 Billing 수량 해제가 다시 실행되는 것은 아니다. 멱등성으로 같은 release를 중복 지급하지 않는지 검증한다.

### 2.3 범위와 제외

포함: 기존 INITIAL, 일반 REPLACEMENT, PHONE_REJOIN의 **이미 저장된 operation** 복구, 공유 실행권, status-first 판정, 안전한 confirm/cancel, 로컬 원자 정리, migration·관측·테스트.

제외: Billing 권리 원장/expiry worker 구현, 새 reserve/continuation discovery의 worker 실행, 새 Session 생성, 유료 이용권·refund·repair-confirm, owner rewrite, 관리자 공개 API, Kafka/SQS, Challenge, AWS/IAM/배포 변경.

2026-09-08 사용자 승인에 따라 AGENTS에 Reservation 자동 복구 전용 허용 절을 추가했다. 기존 TMI-116/AttemptGroup의 범위 제외는 해당 과제의 제한으로 유지하고 새 전용 허용을 취소하지 않는다. 이번 요청에서는 계획·허용 범위·Jira만 반영하며 runtime을 구현하지 않는다.

## 3. 사용자 승인으로 확정한 사항

2026-09-08 사용자가 아래 권장안을 승인했다. 표의 대안은 비교 이력이며 채택하지 않는다. 구현 기준 승인과 production 활성화 승인은 별개다. §4.4의 Billing 계약 증빙과 §5.6의 미제안 Sentry 운영값은 여전히 확인 대상이다.

| 결정 | 승인안 | 미채택 대안과 장단점 |
| --- | --- | --- |
| Session 저장 전 중단 | 정체 2분 이후 status 확인과 미완료 예약 정리. worker는 Session 생성·reserve 금지 | 원래 요청을 끝까지 자동 완성하면 성공률은 높지만 앱 종료 후 시험 교체·권리 소비가 발생할 수 있음 |
| 복구 대상 | 신규 metadata가 있는 operation 자동 처리. 기존 데이터는 dry-run 후 승인 allowlist만 편입 | 기존 전체 자동 편입은 빠르지만 오래된 데이터·구버전 실행 경합 위험 |
| 일시 장애 재시도 | 최대 24시간/200회 중 먼저 도달하면 NEEDS_REVIEW. 증거·activeGuard 보존 | 무기한 재시도는 자동 회복 기회가 많지만 장기 오류·새 시험/merge 차단을 숨길 수 있음 |
| 인증 장애 | durable 전역 auth circuit, 15분마다 한 status-only probe | 계속 전송하면 복구 즉시 반영되지만 잘못된 role 설정에 요청·로그가 집중됨 |
| 404/계약 모순/owner deny | 상태별 재조회 후 운영 격리, 자동 해제·repair 금지 | 시간만 보고 실패 확정하면 가용성은 높지만 늦은 원격 성공을 버릴 위험 |
| terminal 보존 | Billing terminal 시각은 보존하고 로컬 복구 확정 후 최소 7일 증거 보존 | 기존 원격 terminal 시각+7일만 쓰면 오래된 복구 결과가 TTL로 즉시 사라질 수 있음 |

확정 기본값은 poll 10초, 한 poll 최대 20건, instance 동시 처리 2건, 한 번에 처리할 건만 claim, lease 30초, 단일 attempt 총 실행 예산 20초, 최대 Billing HTTP 2회다. 미처리 batch 전체를 미리 lease하지 않는다.

확정 backoff: 5초 → 15초 → 1분 → 5분 → 15분, 이후 15분 상한과 양의 jitter. 유효한 Retry-After보다 일찍 재시도하지 않는다. 실행 횟수와 최초 복구 시각은 재시작·lease 회수로 초기화하지 않는다. lease/auth 차단 대기는 원격 처리 시도 횟수에 포함하지 않되 전체 24시간 age 경보 대상이다.

기존 Billing connect 2초/read 5초, Reservation 기본 expiry 5분은 재정의하지 않는다. lease 길이만으로 원격 요청 완료를 보장하지 않는다는 점이 중요하다.

### 3.1 polling 부하와 의미

- 10초는 **후보 확인 주기**다. 전체 operation을 모두 읽거나 같은 예약을 10초마다 원격 재처리하는 주기가 아니다. non-terminal·due·lease 조건과 index로 조회한다.
- instance당 분당 약 6회 poll에 더해 claim/상태 갱신/lease·circuit 관리 DB 작업이 발생한다. 여러 instance에서는 전체 조회·동시 처리 상한이 instance 수에 비례하므로 위 값은 전역 한도가 아니다.
- 다음 실행이 5분 뒤인 operation은 그 전에 Billing으로 보내지 않는다. 실제 retry 시작은 poll·부하 때문에 nextReconcileAt보다 늦을 수 있다.
- bounded executor의 남은 슬롯만 claim하고 poll 중첩과 무제한 작업 큐를 금지한다. 포화 시 다음 poll로 넘긴다.
- staging에서 explain의 index 사용·examined/returned, DB latency/CPU/연결 수, 기존 API 지연, oldest due age를 관측한다. 부담이 크면 poll을 30초로 늘리거나 동시 처리 수를 낮출 수 있으나 복구 지연과 5분 예약 expiry 영향을 함께 평가한다.
- 24시간 복구 예산은 Reservation 수명 연장이 아니다. 이미 EXPIRED로 확정된 예약을 살리거나 무료권 유효기간을 늘리지 않는다.

## 4. 주요 위험과 미확인 사항

### 4.1 lease만으로 원격 호출을 취소할 수 없음

lease가 끝나도 이미 Billing에 도착한 reserve/confirm은 늦게 성공할 수 있다. 오래된 실행자의 **로컬 쓰기 차단**과 **원격 멱등성·status 재조회**를 함께 사용한다. timeout·lease 만료·404 한 번으로 예약이 없다고 단정하지 않는다.

### 4.2 Session commit과 cancel 경쟁

Session이 안 보인다는 단순 조회만으로 cancel하지 않는다. 같은 operation 문서를 실제로 갱신하는 Transaction으로 cleanup intent를 commit하고, Session commit Transaction도 같은 문서의 intent/lease/version을 검사·갱신해야 한다. 둘은 Mongo write conflict/CAS로 한쪽만 이긴다. cleanup intent commit이 unknown이면 원격 cancel하지 않는다.

### 4.3 계약상 복구 불가능한 상태

Billing ADR-001 §5.2~5.4는 CANCELED/EXPIRED의 일반 confirm 복원을 금지한다. 이미 CONFIRMED인 예약도 cancel하지 않는다. worker는 현재 상태를 정확히 반영할 뿐 Billing 권리를 복원하거나 과거 Session을 새로 만들어 증거를 맞추지 않는다.

### 4.4 운영 확인이 필요한 경계

- 404의 “현재 기록 없음”은 늦은 reserve 성공 가능성이나 원격 기록 보존 기간까지 증명하지 않는다. authoritative absence를 계약으로 보장받지 못한 경우 자동 terminal 처리하지 않는다.
- 오래된 CONFIRMED status의 `attemptGroupStatus`가 원래 confirm snapshot인지 현재 group projection인지, 최신 Billing 배포와 fixture로 확인한다. 현재 LC strict `OPEN` 검증을 조용히 완화하지 않는다.
- `sessionCommittedAt`은 현재 commit body에 넘긴 고정 시각이다. 복구 실행 시각으로 다시 만들거나 실제 서버 commit timestamp라고 과장하지 않는다.
- withdrawal marker와 background 확정/정리의 경합은 기존 guard 동작만으로 모두 보호된다고 가정하지 않는다. v1은 active withdrawal/MERGED 발견 시 업무 변경을 멈추고 격리한다. 운영상 해소에는 별도 승인 절차가 필요하다.
- live Billing, 실제 Mongo/Lattice/IAM과 배포 상태는 이번 계획 작성에서 확인하지 않았다. 로컬 Billing ADR와 LC 코드가 근거다.

## 5. 구현 설계와 진행 순서

### 5.1 operation metadata와 scan

별도 업무 상태 머신을 복제하지 않고 기존 `ExamCreationState`를 유지한다.

- 비종료: `PREPARED`, `RESERVED`, `SESSION_COMMITTED`, `CANCEL_PENDING`
- 종료: `SUCCEEDED`, `CANCELED`, `EXPIRED`, `FAILED_TERMINAL`

`ExamCreationOperation`에 다음 **내부 필드 초안**을 추가한다.

| 필드 | 의미 |
| --- | --- |
| `reconciliationSchemaVersion` | 신규/검증 편입 데이터 구분 |
| `reconciliationStatus` | `READY`, `IN_FLIGHT`, `BLOCKED_AUTH`, `BLOCKED_OWNER`, `NEEDS_REVIEW`, `DONE` |
| `recoveryIntent` | `CONTINUE`, `CLEANUP_PRECOMMIT`. cleanup에서 continue로 자동 복귀 금지 |
| `nextReconcileAt`, `firstReconcileAt`, `reconcileAttempts` | durable scheduling과 예산 |
| `leaseToken`, `leaseOwner`, `leaseUntil` | HTTP와 worker 공유 실행권. token은 매 claim 신규 생성 |
| `lastProgressAt` | 실제 업무 전이 시각. heartbeat/실패 재조회로 정체 시계를 초기화하지 않음 |
| `reserveDispatchState` | `NOT_DISPATCHED`, `MAY_HAVE_BEEN_SENT`, `OBSERVED`. 전송 전 durable 기록 |
| `lastFailureCode`, `lastReconciledAt`, `resolvedAt` | 고정 사유 enum과 운영 확인 시각 |

`activeGuard`는 사용자별 진행 중 생성의 unique 제약이고 lease는 단일 실행권이다. 서로 대체하지 않는다. 격리되어도 업무가 미확정이면 activeGuard=true, purgeAt=null을 유지한다.

주 scan은 상태+nextReconcileAt+commandId로 정렬하고 batch 제한을 둔다. expired lease 회수는 별도 index 경로를 둔다. 제안 index는 `(reconciliationStatus,nextReconcileAt,commandId)`와 `(reconciliationStatus,leaseUntil,commandId)`이며 explain으로 검증한다. 기존 user/operation·active-user unique와 Session unique를 유지한다.

전역 auth circuit은 전용 singleton 문서의 CAS/lease로 공유한다. 기존 AttemptGroup publisher circuit을 같이 차단하지 않는다. 해당 circuit과 non-terminal operation에는 TTL을 두지 않는다.

### 5.2 HTTP와 worker의 공통 실행 경계

1. HTTP 신규 요청은 기존 인증/deny/동일 key 조회와 prepare를 수행한다. worker는 `start(userId,key)`를 호출하지 않고 존재하는 commandId만 처리한다.
2. 두 경로가 같은 operation 문서에서 실행권을 원자 claim한다. HTTP가 얻지 못하면 기존 PROCESSING/Retry-After 응답을 사용한다.
3. HTTP용 실행과 worker용 복구는 공통 snapshot validator·transition service를 사용하되 허용 action은 다르다. worker action은 status/confirm/cancel/로컬 수렴뿐이다.
4. 외부 HTTP는 Mongo Transaction 밖에서 수행한다. 실제 변경 Transaction 안에서는 lease token·유효기간·version·업무 상태·recoveryIntent를 다시 검사하고 같은 문서를 write한다.
5. 정상 소유권 guard touch는 기존 Mongo manager의 같은 body에 참여한다. 기능별 TransactionTemplate을 중첩하지 않는다.
6. lease를 잃은 실행자는 결과를 직접 반영하지 않는다. 다른 실행자가 status와 로컬 증거로 수렴한다. 새로운 원격 호출도 시작하지 않는다.
7. 각 pass 종료 시 token 조건으로 release/retry/격리를 저장한다. graceful shutdown은 신규 claim 중단·in-flight drain, crash는 lease 회수로 처리한다.

worker flag가 OFF여도 새 코드의 HTTP 경로는 공유 fencing 규칙을 사용해야 한다. 구버전 HTTP instance를 모두 drain하기 전 worker를 켜지 않는다. worker ON 상태에서 구버전 HTTP로 롤백하지 않는다.

### 5.3 상태별 복구 표

모든 행은 owner gate, exact identity/continuation 검증과 authoritative 로컬 증거 판정이 선행한다.

| 로컬 상태 / 증거 | Billing 상태 | 승인된 처리 |
| --- | --- | --- |
| PREPARED, 전송 전 marker가 확실히 NOT_DISPATCHED, Session 없음 | 원격 전송 불가능 증명 | cleanup intent/실행권을 원자 확보한 뒤 FAILED_TERMINAL로 종료 가능. 타 실행자가 전송 전 marker를 쓰지 못함을 테스트 |
| PREPARED, 전송 가능/unknown, Session 없음 | 404 | 새 reserve 금지. cleanup intent 아래 재조회·age 경보, 예산 소진 시 NEEDS_REVIEW |
| PREPARED/RESERVED, Session 없음 | RESERVED | 예약 identity를 검증·보존하고 cleanup intent를 확정한 뒤 CANCEL_PENDING → cancel/status |
| PREPARED/RESERVED, Session 없음 | CANCELED/EXPIRED | exact snapshot을 보존한 뒤 해당 terminal로 종료. PREPARED에서의 제한적 terminal 전이 보완 필요 |
| PREPARED/RESERVED, Session 없음 | CONFIRMED | Session을 만들지 않고 NEEDS_REVIEW |
| SESSION_COMMITTED, matching CONFIRMING Session | RESERVED | 저장된 sessionCommittedAt과 같은 key로 confirm. 응답 유실이면 다음 pass status |
| SESSION_COMMITTED, matching Session | CONFIRMED | strict confirm/status evidence로 기존 로컬 finalize Transaction 수행 |
| SESSION_COMMITTED, matching CONFIRMING Session | CANCELED/EXPIRED | 해당 Session만 abandon하고 operation terminal·guard 해제를 원자 반영. 자동 repair-confirm 금지 |
| SESSION_COMMITTED | 404 또는 Session 없음/불일치 | 유실·정합성 오류로 재조회/격리. cancel·새 reserve 금지 |
| CANCEL_PENDING, cleanup intent와 Session 부재가 확정 | RESERVED | 같은 key로 cancel. 409는 status로 다시 판정 |
| CANCEL_PENDING | CANCELED/EXPIRED | 증거와 상태를 확인해 로컬 terminal 수렴 |
| CANCEL_PENDING | CONFIRMED | 취소하지 않음. matching durable Session이 있어도 현재 모델은 바로 SUCCEEDED 전이 불가하므로 v1은 NEEDS_REVIEW |
| 모든 상태 | 식별/owner/continuation 모순 | 업무 변경 없이 NEEDS_REVIEW 또는 BLOCKED_OWNER |
| SUCCEEDED/CANCELED/EXPIRED/FAILED_TERMINAL | 무관 | scan 제외. 사용자 terminal replay와 기존 결과 보존 |

추가 불변식:

- PREPARED인데 matching Session이 존재하거나 RESERVED인데 이미 Session이 보이는 모순은 단순히 cancel하지 않는다. Transaction 밖 fresh majority 재조회로 정상 전이 관측을 우선하고 모순이 지속되면 격리한다.
- 이미 CONFIRMED/진행/완료/폐기된 Session을 IN_PROGRESS로 되돌리지 않는다. 정상 전이의 완료 evidence가 있으면 no-op, 서로 모순되면 격리한다.
- `reservationExpiresAt <= now`는 Billing EXPIRED의 증거가 아니다. RESERVED면 Billing confirm/cancel/expiry CAS 결과를 따른다.
- PREPARED terminal snapshot 수용 validator는 과거 expiresAt을 허용해야 하지만 identity/UUID/kind/continuation 검증은 생략하지 않는다. 현재 `validateReserved`의 “미래 expiresAt” 검증을 terminal 조회에 재사용하지 않는다.
- PHONE_REJOIN은 저장된 continuationId·expected group/mock을 그대로 사용한다. discovery 재실행·target alias·owner 변경은 금지한다.

### 5.4 cleanup과 늦은 reserve 처리

정체 2분은 cleanup 후보 판정 기준이지 “실행 종료 증명”이 아니다.

1. HTTP reserve 전 반드시 MAY_HAVE_BEEN_SENT를 durable 기록한다. 이 기록 commit이 unknown이면 전송하지 않고 재조회한다.
2. worker는 공유 실행권과 Session 부재·현재 상태를 같은 Transaction에서 검증하고 CLEANUP_PRECOMMIT을 기록한다.
3. 오래된 HTTP 실행자가 Billing reserve를 늦게 완료해도 로컬 RESERVED 저장/Session commit은 fencing으로 거절된다.
4. cleanup worker가 status로 늦게 생성된 예약을 찾아 cancel한다. 404만 받았다면 미확정 상태를 유지한다.
5. NOT_DISPATCHED라는 durable 증거가 없는 legacy 데이터는 “한 번도 보내지 않음”으로 추정하지 않는다.

현재 `cancelAfterCommitFailure`의 직접 원격 cancel 경로도 공통 cleanup-intent 검증을 거치도록 바꿔야 한다. worker만 lock을 추가하고 기존 HTTP cancel을 그대로 남기는 구현은 불완전하다.

### 5.5 Mongo unknown commit와 실패 분류

- duplicate key·transient conflict는 실패한 Transaction 밖에서 전체 단위를 제한 재시도한다. abort된 Transaction 안에서 예외를 잡고 계속 쓰지 않는다.
- UnknownTransactionCommitResult와 이를 감싼 TransactionSystemException은 원인 체인을 분류한다. fresh transaction 밖 majority read로 operation과 Session의 일치하는 완료 evidence를 확인한다.
- cleanup intent의 확정 여부가 unknown이면 cancel을 보내지 않는다. Session commit이 unknown이면 Session 없음 한 번으로 cancel하지 않는다.
- 재조회도 실패하거나 evidence가 불일치하면 bounded retry/NEEDS_REVIEW다. 실패했다고 가정해 상태를 되돌리지 않는다.
- business state, Session state, activeGuard·terminal/retention 변경은 하나의 Transaction으로 처리한다.

| 오류 | worker 처리 |
| --- | --- |
| network/timeout/408/425/429/5xx/PROCESSING | backoff와 Retry-After, 예산 소진 격리 |
| 401/403/AUTH_FAILURE | BLOCKED_AUTH, 전역 circuit과 경보. probe 성공 후 점진 재개 |
| RESERVATION_STATE_CONFLICT | status 재확인 후 상태표 적용. 일반 409를 전부 같은 방식으로 처리하지 않음 |
| IDEMPOTENCY_KEY_CONFLICT/invalid payload/strict decode 실패 | NEEDS_REVIEW, payload 변경·다른 key 우회 금지 |
| OPERATION_NOT_FOUND | 상태표의 absence evidence 기준 적용 |
| Mongo conflict/lease lost | fresh reload 또는 release. remote success를 로컬 실패로 바꾸지 않음 |
| owner deny | BLOCKED_OWNER. marker 삭제·source→target alias·권리 해제 금지 |

격리는 전달 event의 DEAD_LETTER가 아니라 **미해결 생성 operation의 운영 상태**다. 예산 소진만으로 FAILED_TERMINAL/activeGuard=false로 바꾸지 않는다.

### 5.6 인증·trace·로그

- 기존 `BillingReservationClient`와 `SigV4BillingReservationClient`, ECS role credential chain 및 `vpc-lattice-svcs` 서명을 사용한다. 새 사용자 Token·정적 AWS key는 필요 없다.
- 복구 pass마다 `billing_reservation_reconcile` span, 각 HTTP는 기존 CLIENT child span을 사용한다. worker v1은 독립 trace를 시작하며 원래 사용자 요청과 동일 trace라고 주장하지 않는다.
- traceparent를 inject하고 SigV4를 최종 논리 단계에서 수행한다. 서명 뒤 payload/header를 변경하지 않으며 baggage/raw tracing header는 저장·전파하지 않는다.
- 구조화 로그: `service=learning-core`, `operation=billing_reservation_reconcile`, 고정 outcome, traceId, non-negative durationMs. duration은 System.nanoTime 차이로 측정한다.
- 승인 outcome: `confirmed`, `canceled`, `expired`, `retry_scheduled`, `blocked_auth`, `blocked_owner`, `needs_review`, `lease_lost`, `noop`.
- 일반 로그/span에 userId, sessionId, commandId/operationId, reservationId, attemptGroupId, continuationId, payload/digest, credential·응답 원문을 넣지 않는다. exact record 조사는 접근 통제된 DB inventory를 사용한다.
- metric은 고정 state/outcome enum, latency/age timer, backlog/oldest age와 auth/review counter다. traceId나 식별자를 label로 쓰지 않는다.

#### 기존 Sentry 경보 재사용

사용자 제안을 반영해 기존 Learning Core Sentry SDK/프로젝트를 경보 수단으로 재사용하는 설계를 추가한다. 새 모니터링 서비스를 도입하거나 이번 문서 작업에서 Sentry 운영 설정을 변경하지 않는다.

**현재 코드의 주의점:** `SentryUnexpectedExceptionReporter`는 HTTP 예외 capture source와 HTTP 500 태그를 사용한다. `SentryEventSanitizer`는 message·임의 tag/context·fingerprint를 제거한다. worker에서 임의 메시지나 태그를 붙이는 것만으로 복구 경보가 충분해진다고 가정하지 않는다.

구현 계획:

1. worker 전용 `ReservationReconciliationAlertReporter`를 추가해 현재 `IHub`를 재사용한다. HTTP 500이나 사용자 HTTP 요청으로 위장하지 않는다.
2. 고정 `capture.source=billing_reservation_reconcile`, 고정 reason(`retry_exhausted`, `state_inconsistent`, `auth_failure`, `owner_blocked`, `creation_blocked_5m`)을 최소 allowlist로 추가한다. 기존 Sentry 개인정보 제거·stack 정제 규칙을 완화하지 않는다. 자유 문자열 원격 오류를 경보 message로 보내지 않는다.
3. `NEEDS_REVIEW`/`BLOCKED_OWNER` 최초 전이와 전역 auth circuit 최초 차단을 보고한다. 추가로 2026-09-08 사용자 승인에 따라 생성 차단이 5분 이상 지속된 operation에 조기 경보를 한 번 등록한다. 정상적인 retry·매 poll·동일 상태 유지마다 새 incident를 만들지 않는다. auth 차단은 operation별 수백 개가 아니라 circuit 단위로 묶는다.
4. crash로 경보가 누락되지 않도록 상태 전이와 함께 pending alert metadata를 해당 operation/circuit에 저장한다. 경보 전송은 업무 Transaction 밖에서 수행하고, 별도의 bounded pending-alert 조회로 회수한다. 새 업무 outbox collection/메시지 큐는 추가하지 않는다.
5. pending metadata에는 무작위 `alertIncidentId`, 고정 reason, 전달 상태·재시도 시각을 둔다. Sentry context에는 검증된 opaque incident UUID만 허용하고 DB에서 해당 incident로 조사 대상을 찾는다. userId/operationId/sessionId/reservationId/groupId/continuationId는 보내지 않는다. incident는 metric label이나 issue fingerprint로 사용하지 않는다.
6. reporter·sanitizer가 함께 보장하는 고정 source/reason 기반 grouping과 제한된 중복 전송을 사용한다. DB와 Sentry의 원자 commit은 불가능하므로 crash 경계의 중복은 허용한다. SDK capture 반환은 최종 전달·사람의 알림 수신 보장이 아니며 delivery 상태를 실제 수신 완료라고 표시하지 않는다.
7. Sentry 장애가 업무 복구나 응답을 실패시키지 않도록 분리한다. pending age·전송 실패 counter를 별도로 남기고 무한 전송/무한 큐 적재를 막는다. Sentry가 꺼져 있으면 조용히 성공 처리하지 않고 배포 점검과 health/metric으로 식별한다.

운영 활성화 전 기존 프로젝트의 환경별 Alert Rule·수신자·실제 이메일/Slack 등 사용 중 채널을 확인한다. staging synthetic 사건으로 “상태 전이 → 정제 이벤트 → issue 분류 → 담당자 수신 → incident를 통한 안전한 DB 조사”까지 검증한다. 실제 채널/권한은 아직 확인하지 않았고 수신자 설정을 임의로 변경하지 않는다.

Sentry 재사용·전용 reporter·pending 회수·privacy·중복 제한과 **5분 조기 경보**는 승인됐다. 실제 담당자, 알림 심각도·반복 제한/재알림 간격, pending 경보 보존·재시도 한도는 이번 5분 승인으로 추정하지 않는다. 기존 팀 운영값을 확인해 재사용하고, 없으면 명시적으로 제안·확정한다. 실제 수신 검증은 활성화 gate다.

#### 2026-09-08 확정: 5분 생성 차단 조기 경보

- 기준 시각은 최초 시험 생성 operation의 `createdAt`이다. `activeGuard=true`가 유지된 채 5분 이상이고, 검증 편입된 `recovery.schemaVersion=1`, non-terminal 업무 상태, `READY`/`IN_FLIGHT` 복구 상태인 operation을 조회한다. retry 시작·lease 회수·재배포로 시간을 초기화하지 않는다. legacy는 승인 편입 후에도 원래 createdAt을 사용하므로 이미 5분을 넘었다면 즉시 후보가 된다.
- 업무 실행권이 비어 있거나 만료된 다음 poll에서 최대 batch-size개를 원자 등록한다. **5분은 경보 대상이 되는 기준**이지 5분 정각의 담당자 수신 보장이 아니다. poll·업무 lease·대기열·SDK 지연이 추가될 수 있다.
- `recovery.earlyAlert.*`에 별도 incident·PENDING·시각·시도 횟수·alert lease를 보존한다. 이후 NEEDS_REVIEW 격리 경보는 기존 `recovery.alert*`에 별도로 남겨 아직 전달하지 못한 조기 경보를 덮어쓰지 않는다.
- 동일 operation의 조기 incident는 한 번만 만들고, SDK 접수 실패·프로세스 재시작에서는 같은 incident/eventId로 제한 재시도한다. 이미 만들어진 pending 조기 경보는 이후 operation이 해결되거나 격리돼도 회수한다. SDK_ACCEPTED는 실제 알림 수신과 다르며 crash 경계의 중복 capture 가능성은 남는다.
- 이미 격리 경보가 있는 operation과 전역 인증 circuit 차단 중에는 새 조기 incident를 만들지 않는다. 기존 pending 사건은 보존한다. 전역 인증 장애는 기존 global incident로 대응한다.
- 경보만 추가하며 업무 상태·activeGuard·복구 시각/횟수·24시간/200회 격리 정책은 변경하지 않는다. 자동 차단 해제·새 reserve·운영자 repair command는 추가하지 않는다.
- poll당 circuit 경보 1건, operation 격리 경보 1건, 조기 경보 1건으로 SDK capture 최대 3건이다. 신규 후보 scan용 `reservation_recovery_early_due`와 pending용 `reservation_recovery_early_pending` non-TTL index를 startup에서 검증하며 활성화 전에 migration으로 준비한다.

### 5.7 파일과 구현 단계

이름은 계획상의 책임 분리 초안이다. 파일 수 자체를 목표로 삼지 않는다.

1. **계약·허용 범위 확정:** §3 승인·독립 AGENTS 허용 절·TMI-128 작성 완료. Billing fixture 대조는 구현 전 확인한다.
2. **공유 실행권 기반:** ExamCreationOperation metadata, Mongo lease store, cleanup intent, 전송 전 marker, state별 validator. 기존 HTTP saga/Transaction service 경로까지 fencing 전환.
3. **복구 서비스:** `BillingReservationReconciliationService`와 typed decision/result, status-first 표 구현. 공개 ErrorStatus/DTO 변환은 HTTP adapter에 남긴다.
4. **worker/설정:** `BillingReservationReconciliationScheduler`, properties/startup validator, 전용 bounded lifecycle executor, auth circuit·관측·Sentry 전용 reporter와 sanitizer 최소 allowlist/경보 pending metadata 추가. 기존 scheduler를 대체하지 않는다.
5. **migration/운영:** `scripts/mongodb/billing-reservation-reconciliation-prepare.js`와 Node test, runbook, dry-run inventory와 allowlist 편입. drop/자동 데이터 수선 금지.
6. **테스트와 rollout:** 아래 acceptance matrix 통과 뒤 feature OFF 배포 → drain → 검증 편입 → staging canary.

새 설정은 `app.billing.reconciliation.*` 아래 default-off로 둔다. ON에는 creation saga ON, 실제 Mongo Transaction/index, bounded duration/lease, 대상 스키마 및 기존 Billing URL/region/timeout 검증을 요구한다. 기존 phone operation 복구에 필요한 phone 계약 배포도 확인한다. flag OFF일 때 신규 scan/외부 호출은 0이어야 한다.

### 5.8 migration·운영 활성화

dry-run 항목: operation/Session 참조·owner/operation/reservation/group/mock 일치, non-terminal activeGuard/purgeAt, 중복 active operation, 잘못된 state/UUID/continuation, 이미 만료된 terminal purgeAt, orphan CONFIRMING Session, 기존 index 호환성.

- Session만 있고 operation이 없는 경우 worker가 operation을 재생성하지 않는다. 별도 inventory blocker로 보고한다.
- 신규 metadata는 reader-first로 배포하고 worker OFF를 유지한다. 구버전 HTTP drain 뒤 index와 검증된 legacy allowlist를 적용한다.
- legacy는 전송 여부를 UNKNOWN 취급하고 최초 복구 예산을 편입 시 명시적으로 설정한다. 도입 때마다 reset하지 않는다.
- 기존 operation 원격 terminal 시각을 보존한다. 승인 보존안에 따라 `purgeAt >= resolvedAt+7일`을 보장하고 미해결/차단 record에 purgeAt가 남지 않게 검증한다. TTL 정책 변경은 문서/테스트로 명시한다.
- 운영 repair HTTP/CLI는 v1에 추가하지 않는다. inventory→원격 status/로컬 증거 확인→별도 승인된 지원 절차→감사 기록이 기본이다. 직접 state/owner/activeGuard 변경을 일반 복구 절차로 안내하지 않는다.
- 정상 중단은 신규 claim 중단과 drain, 긴급 worker OFF는 사용자 인증/API와 증거를 보존한다. OFF만으로 원격 in-flight 요청이 취소된다고 주장하지 않는다.

### 5.9 테스트와 완료 기준

unit/contract와 **실제 격리 replica-set** 테스트를 함께 추가한다.

| 필수 시나리오 | 검증할 결과 |
| --- | --- |
| confirm 응답 유실 + 사용자 재요청 없음 | status CONFIRMED로 같은 Session·operation 완료 |
| Session commit 후 crash | 저장된 시각/ID로 confirm, 중복 소비 없음 |
| reserve 응답 유실 + PREPARED | 늦은 RESERVED 관측 후 cleanup, 새 reserve/Session 없음 |
| 확실한 NOT_DISPATCHED/unknown 404 | 전자는 안전 종료, 후자는 재조회/격리 |
| HTTP replay와 worker 동시 claim | 한 실행권, loser는 PROCESSING/no-op |
| Session commit vs cleanup intent/cancel | 같은 operation conflict로 승자 하나, commit winner 예약 취소 금지 |
| lease 만료 후 늦은 HTTP/worker 성공 | stale token 로컬 쓰기 차단, status로 수렴 |
| 다중 instance crash/lease 회수 | 중복 Session/권리 소비·장기 영구 lease 없음 |
| 모든 로컬 변경 단계 오류 주입 | Session/operation/guard 전체 rollback |
| 실제 commit 성공 뒤 ack 유실 | fresh majority evidence로 성공 관측, 보상 금지 |
| CANCEL_PENDING+CONFIRMED, operation 없는 Session | v1 격리, 불법 상태 전이·데이터 재생성 없음 |
| INITIAL/REPLACEMENT/PHONE_REJOIN | 고정 snapshot 유지, discovery/owner rewrite 없음 |
| merge/withdrawal 경합 | same guard boundary, source 접근 우회·marker 해제 없음 |
| 401/403 동시 실패·probe | 전체 instance circuit·단일 half-open, 업무 실패로 오판 금지 |
| timeout/429/Retry-After/재시도 소진 | durable 일정/예산 유지, no busy-loop, activeGuard 보존 |
| 오래된 원격 terminal/retention | 합의한 로컬 최소 보존, 미해결 TTL 없음 |
| strict 응답·SigV4·trace/privacy | 잘못된 성공 거절, 최종 서명 순서, baggage/PII 미포함 |
| INITIAL 취소/만료 및 중복 status/cancel | 기존 available 복원이라는 Billing fixture와 일치, LC에서 grant 생성/수량 갱신 없음 |
| REPLACEMENT 취소/만료 및 응답 유실 | 기존 consumption 불변, 새 무료 1회 추가/이중 생성 없음 |
| Sentry worker 보고·sanitizer | 고정 source/reason만 보존, HTTP500 오표기 없음, PII/원격 메시지 제거와 기존 HTTP 회귀 유지 |
| 격리 commit 직후 crash·Sentry 장애 | pending 경보 재시도, 업무 복구 영향 없음, 중복 제한과 고정 grouping |
| poll 포화/다중 instance/긴 backoff | 무제한 queue·overlapping poll 없음, due 전 외부 호출 없음, 처리량과 DB 부하 관측 가능 |
| legacy dry-run/apply·flag OFF | blocker 탐지/allowlist 제한, OFF scan·호출 0 |
| 공개 API/기존 시험·Challenge 회귀 | URL/DTO/BaseResponse/AI/S3/Redis 계약 불변 |

구현 완료 시 실행:

```bash
./gradlew clean test
./gradlew mongoIntegrationTest
node --test scripts/mongodb/*.test.js
git diff --check
```

staging에서는 실제 Lattice SigV4 role/route, Billing expiry와 confirm 경합, multi-instance·response-loss, auth circuit, 운영 경보 수신과 rollback을 검증한다. 로컬 테스트를 production 활성화 근거로 대신하지 않는다.

완료 정의: 사용자 재요청 없이 복구 가능한 모든 상태가 terminal로 수렴하고, 불명확한 상태는 증거/guard를 보존해 명확히 격리되며, 동시성·원격 성공 유실로 정상 예약을 취소하지 않는다는 검증이 있어야 한다. 전체 유료 1차 기능 완료를 의미하지 않는다.

## 6. 상세 근거와 부록

### A. 확인한 로컬 구현

| 파일 | 확인한 사실/계획 영향 |
| --- | --- |
| [BillingExamCreationSaga](../../src/main/java/web/tosunsaeng/domain/exams/application/BillingExamCreationSaga.java) | operation 우선 replay, 최대 8 state step, private 복구 메서드, 직접 cancel 경로. worker에서 start 재사용 금지 |
| [ExamCreationOperation](../../src/main/java/web/tosunsaeng/domain/exams/domain/entity/ExamCreationOperation.java) | @Version, activeGuard·purgeAt 존재; lease/dispatch/retry 없음; markSucceeded는 SESSION_COMMITTED만 허용 |
| [ExamCreationOperationRepository](../../src/main/java/web/tosunsaeng/domain/exams/domain/repository/ExamCreationOperationRepository.java) | 단건 조회만 존재, bounded scan/claim 추가 필요 |
| [BillingExamCreationTransactionService](../../src/main/java/web/tosunsaeng/domain/exams/application/BillingExamCreationTransactionService.java) | old Session abandon+new Session insert+operation commit 원자 처리, terminal 보존 7일, owner touch 참여 |
| [BillingReservationClient](../../src/main/java/web/tosunsaeng/domain/exams/billing/BillingReservationClient.java) | 기존 reserve/confirm/cancel/status 및 phone snapshot port |
| [SigV4BillingReservationClient](../../src/main/java/web/tosunsaeng/domain/exams/billing/SigV4BillingReservationClient.java) | cancel reason SESSION_COMMIT_FAILED, POST status, strict decode·CLIENT trace·최종 서명 재사용 |
| [BillingSagaProperties](../../src/main/java/web/tosunsaeng/domain/exams/billing/BillingSagaProperties.java) | creation/phone flag, connect 2초/read 5초, reconciliation 설정 없음 |
| [UserMergedTransactionService](../../src/main/java/web/tosunsaeng/domain/usermerge/application/UserMergedTransactionService.java) | activeGuard=true 비종료 operation은 merge precondition을 막음 |
| [UserOwnedTransactionExecutor](../../src/main/java/web/tosunsaeng/domain/usermerge/application/UserOwnedTransactionExecutor.java) | 기존 Transaction 안 guard touch 참여 지점 |
| [TMI-116 migration](../../scripts/mongodb/tmi-116-migrate-billing-exam-saga.js) | active-user/operation unique, state+updatedAt scan, purgeAt TTL 기존 존재 |
| [기존 saga 테스트](../../src/test/java/web/tosunsaeng/domain/exams/application/BillingExamCreationSagaTest.java) | same-key 동기 복구 회귀 유지; background/lease 증명은 신규 테스트 필요 |
| [기존 Sentry reporter](../../src/main/java/web/tosunsaeng/global/sentry/SentryUnexpectedExceptionReporter.java) | IHub 재사용 가능, HTTP capture source/500과 worker 보고는 구분 필요 |
| [기존 Sentry sanitizer](../../src/main/java/web/tosunsaeng/global/sentry/SentryEventSanitizer.java) | message/임의 tag/context/fingerprint 제거. 명시적 최소 allowlist와 개인정보 회귀 필요 |

### B. 계약 근거와 상호 서비스 영향

- LC [TMI-116 계획](BILLING_RESERVATION_SAGA_IMPLEMENTATION_PLAN.md): 동기 saga, background 별도 범위, operation missing 시 새 reserve 금지.
- LC [TMI-122 계획](PHONE_REJOIN_CONTINUATION_IMPLEMENTATION_PLAN.md): phone snapshot과 기존 group/mock 불변.
- LC [TMI-125 계획](USER_MERGED_CONSUMER_IMPLEMENTATION_PLAN.md): non-terminal operation 차단과 같은 owner guard Transaction 경계.
- Billing 로컬 [서비스 계약](/Users/msde76/billing/docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md) §6.5~6.8: 5분 expiry·원격 terminal 불가역·status 복구.
- Billing 로컬 [ADR-001](/Users/msde76/billing/docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md) §5.2~5.4: exact confirm/cancel/status JSON, cancel reason SESSION_COMMIT_FAILED/CALLER_ABORTED, 404 의미.
- Billing 로컬 [ReservationLifecycleService](/Users/msde76/billing/src/main/java/web/tosunsaeng/billing/domain/reservation/application/ReservationLifecycleService.java): cancelOnce/expireOnce가 releaseInitial을 호출하고 REPLACEMENT는 수량 변경 없이 반환한다.
- Billing 로컬 [EntitlementGrantRepository](/Users/msde76/billing/src/main/java/web/tosunsaeng/billing/domain/entitlement/repository/EntitlementGrantRepository.java): releaseHeldOne이 held를 availableUnits로 돌린다. 이는 계약/로컬 코드 근거이며 운영 배포 완료 증거는 아니다.

**예상 영향:** 프론트·AI wire 변경 없음. Billing도 기존 endpoint로 구현하는 계획이므로 신규 runtime 변경을 전제하지 않는다. 다만 오래된 status snapshot 의미·보존/404 경계는 Billing 측 fixture/배포와 확인한다. 다른 응답/repair API가 필요해지면 구현으로 우회하지 않고 계약을 먼저 개정한다.

### C. 승인·등록 당시 상태 — 구현 전 이력

- 2026-09-08 사용자 권장안 승인과 TMI-128 생성(해야 할 일)을 반영했다. 계획·AGENTS·CURRENT_STATE/WORKLOG를 갱신하고 기존 사용자 변경은 보존했다.
- runtime·Billing 코드·운영 Sentry·DB/AWS·배포는 변경하지 않았다. 신규 과제 등록은 구현을 시작한 의미가 아니다.
- 코드 변경이 없어 Gradle을 실행하지 않았다. Jira 재조회, 문서 diff와 파일 링크·marker를 검증한다.
- 다음 단계는 Billing fixture/미확인 계약 대조 후 별도 구현 요청에 따라 TMI-128 구현이다. Sentry 미제안 운영값 확인 및 실제 종단 검증은 남는다.

## 7. TMI-128 구현 반영 — 2026-09-08

- 사용자 후속 승인으로 §5.6의 5분 조기 경보를 구현했다. 별도 earlyAlert journal·bounded 원자 등록/전송·인덱스를 추가하고, 4분59초/5분 경계·다중 인스턴스·재시작·SDK 실패·후속 격리·OFF/인증 차단/legacy 제외·업무 lease 보존·privacy를 테스트한다. 기본 OFF와 운영 수신 검증 경계는 유지한다.
- 사용자 후속 구현 요청으로 기존 Saga/TransactionService와 operation metadata, 공유 HTTP/worker lease fence, dispatch marker·cleanup intent, status-first worker, retry/owner/review/auth 격리, bounded scheduler·startup/index 검증, Sentry pending 회수와 sanitizer, migration·테스트·RUNBOOK을 추가했다.
- 내부 필드 초안은 `recovery` embedded document로 구현했다. 기존 business state와 Request/Response는 유지한다. 기존 HTTP도 공유 fencing을 적용하며 새 worker flag는 OFF다.
- Billing 로컬 `ReservationLifecycleService.status`는 원래 confirm snapshot이 아니라 현재 AttemptGroup 상태를 조회한다. 원격 command/reservation 없음은 OPERATION_NOT_FOUND이며 과거 전송/늦은 성공의 불가능성 증명이 아니다. 그래서 strict OPEN은 유지하고 모순은 review, unknown 404는 재조회·예산 소진 격리로 처리했다. 실제 배포와 원장 E2E는 검증하지 않았다.
- unknown commit은 fresh majority operation/Session 재조회와 다음 pass로 수렴한다. Mongo transient/duplicate 실패는 abort된 Transaction 안에서 계속하거나 이미 변이된 entity를 그대로 재실행하지 않고 command/pass 경계에서 reload·bounded retry한다.
- 격리된 Docker replica-set의 실제 claim 경쟁, Session commit/cleanup write conflict, rollback, commit ack 유실, 원격 confirm 유실, terminal retention, auth single probe, pending alert 장애와 same-key HTTP replay를 검증한다. 전체 단위·Mongo·Node 테스트 수와 최종 결과는 CURRENT_STATE/WORKLOG에 기록한다.
- Sentry pending 재시도 한도는 아직 운영값 확인/승인 대상이므로 `alert-max-age`, `alert-max-attempts` 기본값을 만들지 않았다. worker ON 시 명시 설정을 요구한다. 실제 수신자·severity/Alert Rule·수신 검증은 별도 gate다.
- [RUNBOOK](BILLING_RESERVATION_RECONCILIATION_RUNBOOK.md)에 정확한 설정·인덱스/allowlist 편입·privacy·관측·OFF/drain/ON 순서와 한계를 정리했다. 구버전 HTTP drain·실제 Mongo/Lattice/Billing/Sentry staging gate 전에는 활성화하지 않는다.
- 기존 API/AI/S3/Redis/Challenge·Billing 저장소·운영 데이터/AWS/Sentry 설정은 변경하지 않았다. commit/push/배포와 Jira 종료는 수행하지 않았다.
