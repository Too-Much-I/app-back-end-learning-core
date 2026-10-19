# TMI-128 Reservation 자동 복구 — 구현 및 활성화 안내

## 1. 5줄 결론

1. 기존 operation만 복구한다. worker는 reserve·phone discovery·새 Session 생성을 하지 않는다.
2. 저장 전에는 정체 2분 이후 cleanup intent를 확보해 예약을 정리하고, 저장 후에는 저장된 ID·시각으로 같은 Session을 확정한다.
3. HTTP와 worker가 같은 Mongo lease/version fence를 사용한다. cleanup과 Session commit은 같은 문서를 쓰는 Transaction으로 경쟁한다.
4. 404·응답 유실·unknown commit은 실패 확정 근거가 아니다. status 재조회, fresh majority 증거와 제한 재시도를 사용한다.
5. 기본 OFF다. 로컬 검증은 운영 배포·Sentry 수신·Billing 권리 원장 E2E 검증을 대체하지 않는다.

## 2. 사용자가 알아야 할 동작

- `SESSION_COMMITTED`와 matching CONFIRMING Session: status가 RESERVED면 같은 `sessionCommittedAt`으로 confirm, CONFIRMED면 strict OPEN 증거를 확인해 로컬 finalize한다.
- PREPARED/RESERVED/CANCEL_PENDING: cleanup intent·Session 부재를 같은 Transaction에서 확정한 뒤 status→cancel로 처리한다. 확실한 `NOT_DISPATCHED`만 원격 호출 없이 종료한다.
- 기존 HTTP replay도 cleanup intent 이후 reserve/Session commit으로 돌아가지 않는다. 기존 PROCESSING 응답으로 유지한다.
- Billing CANCELED/EXPIRED는 해당 CONFIRMING Session만 abandon한다. INITIAL 무료권 held 해제와 REPLACEMENT consumption 유지의 실제 처리는 Billing 소관이다. Learning Core는 grant를 추가하거나 수량을 조작하지 않는다.
- 모순은 NEEDS_REVIEW, active withdrawal/MERGED 발견은 BLOCKED_OWNER다. activeGuard와 증거를 유지하므로 새 시험 생성·UserMerged가 계속 차단될 수 있다. 자동 owner alias·marker 삭제·권리 해제는 없다.
- 최초 operation 생성부터 미해결 차단이 **5분 이상**이면 조기 Sentry 경보 대상이다. 경보 후에도 기존 자동 복구를 계속하며, 시간 경과만으로 새 시험 차단을 해제하지 않는다. 5분은 후보 기준이고 실제 수신 시점은 poll·lease·대기열·Sentry 전달 지연의 영향을 받는다.
- 원격 terminalAt을 보존하면서 `purgeAt >= recovery.resolvedAt + 7일`을 보장한다. 미해결 operation과 circuit에는 TTL을 추가하지 않는다.

## 3. 설정과 승인 경계

`app.billing.reconciliation.*`의 실행 기본값:

| 설정 | 기본값 |
| --- | --- |
| enabled | false |
| poll / batch-size / concurrency | PT10S / 20 / 2 |
| lease / attempt-timeout | PT30S / PT20S |
| precommit-stale | PT2M |
| max-age / max-attempts | PT24H / 200 |
| auth-probe-interval | PT15M |
| 생성 차단 조기 경보 | 최초 operation.createdAt + PT5M — 승인된 고정값, 별도 설정 없음 |
| alert-max-age / alert-max-attempts | 기본값 없음 — 활성화 전 명시 설정 필수 |

- YAML 환경 변수는 `BILLING_RECONCILIATION_ENABLED`, `BILLING_RECONCILIATION_POLL`, `BILLING_RECONCILIATION_BATCH_SIZE`, `BILLING_RECONCILIATION_CONCURRENCY`, `BILLING_RECONCILIATION_LEASE`, `BILLING_RECONCILIATION_ATTEMPT_TIMEOUT`다.
- 경보 한도는 `BILLING_RECONCILIATION_ALERT_MAX_AGE`, `BILLING_RECONCILIATION_ALERT_MAX_ATTEMPTS`로 설정한다. 24시간/200회는 제안했지만 별도 승인·운영값 확인 전 기본값으로 채택하지 않았다. 미설정 상태에서 worker ON이면 기동을 거절한다.
- 추가 설정은 Spring의 `APP_BILLING_RECONCILIATION_*` 바인딩 또는 환경별 YAML을 사용한다. 비밀 값은 문서·명령 기록에 넣지 않는다.
- ON은 creation saga ON, 기존 unique/TTL index와 신규 index 일치, Mongo replica-set/sharded Transaction, Sentry SDK enabled를 요구한다. Billing connect≤2초/read≤5초, pass≥14초, lease≥pass+5초를 검증한다.
- pass에는 원격 호출을 최대 2회 시작한다. 남은 실행 예산이 7초 미만이면 새 호출을 시작하지 않는다. 로컬 DB 드라이버의 연결/서버 선택 timeout까지 강제 취소하는 실시간 deadline은 아니며, stale lease의 로컬 반영은 차단한다.
- 기존 HTTP 경로는 worker OFF일 때도 shared fencing을 쓴다. OFF는 이미 전송한 요청 취소 또는 남아 있는 auth circuit의 자동 해제를 뜻하지 않는다.

## 4. 배포 전 순서

1. Billing 배포의 Reservation status/confirm/cancel·phone 계약, Lattice exact route/IAM, 실제 Mongo 연결·Transaction을 검증한다. Billing 저장소나 AWS 리소스는 이번 작업에서 수정하지 않았다.
2. worker OFF로 신버전을 배포하고 구버전 HTTP instance를 모두 drain한다. worker ON 상태에서 구버전 HTTP로 롤백하지 않는다.
3. `scripts/mongodb/billing-reservation-reconciliation-prepare.js`를 먼저 dry-run한다. `MONGODB_URI`, `MONGODB_DATABASE`는 승인된 보안 환경으로 주입한다. 명령 예: `node scripts/mongodb/billing-reservation-reconciliation-prepare.js`.
4. dry-run은 operation/Session 연결·UUID/phone snapshot·activeGuard/retention·duplicate active owner·orphan CONFIRMING Session·index 충돌을 검사한다. 출력은 개수/고정 blocker 코드뿐이며 ID·payload·URI를 출력하지 않는다.
5. index apply에는 `RESERVATION_RECOVERY_APPLY=true`, `RESERVATION_WRITERS_DRAINED=true`가 필요하다. 이 확인은 모든 해당 HTTP/worker writer를 실제 중단했다는 운영자 확인이며 스크립트가 ECS를 drain해 주지 않는다.
6. legacy 편입은 `RESERVATION_RECOVERY_ALLOWLIST`에 승인된 command UUID JSON 배열만 전달한다. 기본 `[]`는 신규 index만 준비하고 기존 operation은 자동 편입하지 않는다. legacy dispatch는 항상 MAY_HAVE_BEEN_SENT, 예산 시작은 편입 시각으로 저장한다. 재실행으로 이미 편입한 예산을 초기화하지 않는다.
7. Sentry 경보 운영값·환경별 Alert Rule·담당자/채널을 확인하고 synthetic 사건으로 실제 수신을 검증한다. 이후 staging canary에서만 worker를 활성화한다.
8. ready/expired-lease query의 explain과 examined/returned, Mongo 지연·CPU/connection, 기존 API P99, backlog·샘플 due age를 관측한다. 이 저장소의 격리 테스트 결과를 운영 부하 검증으로 간주하지 않는다.

기존 TMI-116의 user/operation unique, active user partial unique, Session operation/reservation unique와 purge TTL은 별도로 유지한다. 신규 scan index는 `reservation_recovery_due`, `reservation_recovery_lease`, `reservation_recovery_alert`, `reservation_recovery_early_due`, `reservation_recovery_early_pending`이다. 마지막 두 index는 5분 조기 경보용이며 모두 non-TTL이다. circuit collection은 `reservation_reconciliation_control`, singleton은 `auth:v1`이며 TTL이 없다.

legacy 전체 inventory는 운영 중 자동 실행하지 않는다. dry-run blocker가 있으면 apply는 데이터 자동 수선 없이 거절한다. terminal operation의 userId는 UserMerged 후에도 원래 snapshot으로 남으므로 terminal Session owner 차이만으로 owner rewrite를 요구하지 않는다.

## 5. 운영 시 관측·격리

- 구조화 로그는 service, operation, 고정 outcome, traceId, monotonic durationMs만 남긴다. userId/sessionId/commandId/operationId/reservationId/groupId/continuationId·payload·원격 응답·인증 헤더를 넣지 않는다.
- `learning_core.billing.reconciliation.attempt`와 `.duration`: 고정 outcome별 counter/timer. `.due`는 ready+expired lease 후보 개수의 근사 시점 조회, `.sample_oldest_due_seconds`는 해당 poll의 제한된 후보 샘플 최댓값이다. 후자를 전역 oldest age로 해석하지 않는다.
- `.alert`, `.alert_age`, `.scheduler_failure`는 SDK 접수·실패·예산 소진과 scheduler 오류를 관측한다. ID/traceId/incident/age 값은 metric label이 아니다.
- auth 401/403은 공유 circuit을 차단하고 15분당 한 status-only probe만 허용한다. probe 후 한 poll당 batch 이하로 BLOCKED_AUTH를 재개한다. AttemptGroup publisher circuit과 분리돼 있다.
- NEEDS_REVIEW/BLOCKED_OWNER의 최초 전이와 circuit 최초 차단에서 같은 문서에 pending alert를 저장한다. eventId는 무작위 incident UUID이며 source/reason fingerprint로 그룹화한다. DB에서 incident로 찾아보는 작업은 접근 통제된 운영 절차에서 수행한다.
- 조기 경보 reason은 `creation_blocked_5m`이다. schemaVersion=1·activeGuard=true·non-terminal·READY/IN_FLIGHT이고 createdAt이 5분 이상 지난 후보를 업무 lease가 비어 있는 다음 poll에서 최대 batch-size개 등록한다. 이미 격리 경보가 있거나 전역 auth circuit이 차단된 동안에는 새 조기 incident를 만들지 않는다. legacy는 승인 편입 후 원래 createdAt으로 판정한다.
- 조기 경보의 `recovery.earlyAlert.*`와 후속 격리의 `recovery.alert*`는 독립 journal이다. incident UUID 하나를 operation당 한 번만 생성해 재시작·SDK 접수 실패에도 유지한다. 조기 pending이 남은 상태로 업무가 해결/격리돼도 회수하며 업무 retry 예산·상태·activeGuard는 바꾸지 않는다. 경보 메시지만 보고 현재도 차단됐다고 단정하지 말고 DB의 현재 증거를 재확인한다.
- SDK 전송은 poll당 circuit 1건 + operation 격리 1건 + 조기 경보 1건(최대 3건)으로 제한한다. 두 operation 경보는 동일한 승인된 alert retry 한도를 각각 적용한다. 조기 경보가 후속 격리 경보를 덮어쓰지 않는다. Alert Rule은 신규 issue뿐 아니라 이미 그룹화된 issue의 새 사건도 담당자에게 전달하는지 synthetic 수신 검증으로 확인한다.
- `SDK_ACCEPTED`는 SDK가 capture를 받았다는 뜻이지 Sentry 도착·사람의 수신 확인이 아니다. 전송과 DB 갱신 사이 crash로 중복 접수가 가능하다. 전송 예산 소진은 `UNSUBMITTED`로 남기고 자동 삭제하지 않는다.
- 미접수 auth incident가 남은 채 circuit이 재차 닫히면 기존 사건을 보존한다. DB 원자 commit과 Sentry 전달을 하나의 원자 작업이라고 주장하지 않는다.
- 해결은 inventory→Billing status/로컬 증거 확인→별도 승인된 지원 절차→감사 기록이다. 직접 상태·owner·activeGuard를 바꾸는 관리자 repair API나 CLI는 추가하지 않았다.

## 6. 구현 파일과 검증 범위

| 파일/영역 | 역할 |
| --- | --- |
| `ExamCreationOperation`, `ReservationRecovery` | 기존 진행표에 스케줄/실행권/cleanup/경보 metadata, 로컬 terminal 보존 |
| `ReservationOperationExecution`, 기존 Saga/TransactionService | HTTP/worker 공유 fence, dispatch marker, 동일 Transaction owner gate·Session 변경, fresh majority 재조회 |
| `ReservationSnapshotValidator`, `BillingReservationReconciliationService` | status-first 상태표, 동일 key confirm/cancel, owner/contract/budget 격리 |
| `ReservationRecoveryStore`, `ReservationAuthCircuit`, Scheduler/Properties/StartupValidator | index 후보 조회, bounded executor, backoff, 전역 인증 차단, 안전한 ON 조건 |
| `ReservationReconciliationAlertReporter`, `SentryEventSanitizer` | pending 회수와 개인정보 정제, 고정 reason/incident·grouping |
| migration JS/Node test, `ReservationRecoveryContractTest`, `ReservationReconciliationMongoIntegrationTest` | 사전검사·허용목록·호환성·실제 Transaction/경합·경보 실패 검증 |

- Mongo transient/duplicate 충돌은 abort된 Transaction 안에서 계속하지 않고 command/pass 밖 fresh reload와 제한된 재시도로 처리한다. 이미 변경된 Java entity를 동일 Transaction callback으로 무작정 replay하지 않는다.
- unknown commit cause chain은 별도 outcome-unknown 예외로 분류한다. fresh majority로 실제 terminal/Session 증거를 확인하거나 다음 pass로 넘긴다. cleanup commit ack가 불명확한 pass에서는 cancel을 전송하지 않는다.
- 실제 격리 replica-set에서 commit/rollback, commit ack 유실 후 수렴, HTTP/worker claim 경합, stale token, cleanup vs Session write conflict, 원격 confirm 유실, terminal retention, auth circuit, Sentry pending 실패/중복 억제를 검증한다.
- 5분 경보는 4분59초에는 미발생·5분부터 발생, 동시 poll의 단일 incident, 재시작 회수, SDK 실패 재시도/소진, 후속 격리와의 독립 보존, OFF/완료/미편입 legacy 제외, auth fan-out 억제와 batch/업무 lease 경계를 검증한다.
- 기존 SigV4 client 회귀 테스트의 최종 서명·CLIENT trace header, strict decode 검증을 유지한다. Billing 실서버·무료권 원장·실제 Sentry/모바일 E2E는 별도 staging gate다.
- withdrawal marker와 모든 background 변경이 강하게 직렬화된다고 주장하지 않는다. 기존 guard Transaction 참여와 전송/쓰기 직전 deny 확인을 사용하며, 관측된 deny는 격리한다.
- public URL/Method/DTO/BaseResponse/Idempotency-Key 의미, AI user_id=examId·Callback, S3/Redis, Challenge 계약은 변경하지 않는다. commit/push/배포는 사용자가 수행한다.
