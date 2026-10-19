# Learning Core AttemptGroup durable outbox/publisher 구현 계획

- 상태: 로컬 구현·전체 회귀 완료, staging 활성화 전
- 작성일: 2026-09-01
- 대상 저장소: `Too-Much-I/app-back-end-learning-core`
- 선행 구현: Billing `TMI-117`, PR #5 merge commit `a34766e`
- Learning Core Jira: `TMI-118`
- 관련 계약: Billing `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, Billing `docs/contracts/LEARNING_CORE_ATTEMPT_GROUP_TRACE_HANDOFF.md`, Learning Core `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`

## 1. 5줄 결론

1. Billing의 AttemptGroup consumer와 production `attempt_group_event_consume` span은 `develop@a34766e`에 병합됐으므로 Learning Core publisher 구현을 시작할 선행 조건은 충족됐다.
2. Learning Core는 모든 필수 `retryCount=0` 제출이 durable하게 접수되면 `GRADING`, 필수 결과·유효 점수·Summary가 조회 가능하면 `COMPLETED`, 복구 불가능한 최종 실패면 `RETAKE_AVAILABLE` event를 만든다.
3. ExamSession의 상태 판정과 outbox insert를 같은 Mongo Transaction/CAS로 처리하고, 같은 Session에는 terminal event를 `COMPLETED` 또는 `RETAKE_AVAILABLE` 중 하나만 허용한다.
4. 별도 lease publisher가 W3C publish span을 만든 뒤 `traceparent`를 주입하고 마지막 논리 단계에서 SigV4 서명해 Billing `POST /internal/v1/attempt-group-events`로 전송한다.
5. `GRADING` deadline `PT30M`과 단계적 실패 확정·strict Summary·auth half-open·allowlist backfill을 확정했고 Learning Core 구현 Jira는 `TMI-118`이다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 2.1 이번 작업은 API 추가가 아니라 내부 상태 연동이다

앱이 호출하는 공개 API는 바꾸지 않는다.

- 기존 URL, HTTP Method, Request/Response DTO와 `BaseResponse` 유지
- 기존 `retryCount`, Redis key, S3 key·Presigned URL과 submit·Polling 유지
- Python AI request/Callback의 `user_id=examId`와 JSON 구조 유지
- 실제 `userId`, `attemptGroupId`, Billing 상태를 앱 응답에 노출하지 않음

추가되는 것은 Learning Core 내부의 상태 판정, Mongo outbox와 Billing 내부 호출뿐이다.

### 2.2 Billing merge 확인 결과

2026-09-01 확인 기준 Billing 저장소는 다음 상태다.

```text
develop == origin/develop == a34766e
Merge pull request #5 from Too-Much-I/fix/TMI-117-attempt-group-tracing
included commit: b1f6fbd
working tree: clean
```

병합된 범위는 다음과 같다.

- `POST /internal/v1/attempt-group-events`
- strict request decode와 eventId/digest 멱등 처리
- active Session fencing과 `GRADING|COMPLETED|RETAKE_AVAILABLE` 전이
- production `attempt_group_event_consume` INTERNAL span
- 실제 HTTP 기반 same traceId/different spanId·baggage 차단·오류 종료·privacy 테스트

병합 직전 동일 변경에서 Billing `./gradlew clean test` 총 138개 성공을 확인했다. 이번 계획 작성에서는 Billing 코드를 다시 수정하거나 테스트하지 않았다.

### 2.3 특정 Jira 예외가 아닌 영구 허용 범위

사용자 승인에 따라 `AGENTS.md`에 AttemptGroup outbox/publisher 전용 영구 허용 규칙을 추가했다. 따라서 신규 Jira는 작업 추적과 완료 조건 관리에는 필요하지만, Jira별 명시적 예외를 반복해서 추가할 필요는 없다.

TMI-116의 제외 문구는 TMI-116 자체에 outbox/publisher 구현을 섞지 않기 위한 범위 제한이다. 이번 영구 허용은 다음 경계 안에서만 적용한다.

- Learning Core의 상태 판정·outbox·publisher·제한된 reconciliation과 RETAKE replacement 연결만 허용
- 공개 API·AI·S3·Redis 계약 변경 금지
- Billing consumer·Billing 저장소 수정 금지
- 실제 AWS 리소스 생성·배포 금지

### 2.4 event 발행 실패와 채점 실패는 서로 다른 상태다

- 채점이 성공했는데 Billing HTTP 전송만 실패한 경우: 시험은 성공 상태를 유지하고 outbox만 재시도한다.
- 채점이 최종 실패한 경우: `RETAKE_AVAILABLE` terminal event를 만든다.
- trace 생성이나 전파가 실패한 경우: 새 trace로 계속 보내며 업무 event를 폐기하지 않는다.
- Billing 인증이 실패한 경우: event를 `BLOCKED_AUTH`로 보존하고 전역 전송 circuit을 차단한다.

publisher 장애를 시험 실패로 오인해 무료 재응시를 열지 않는다.

## 3. 확정된 구현 정책과 남은 준비

### 3.1 GRADING 최종 deadline — 확정

기준 시각은 local `GRADING` 전이의 `gradingStartedAt`이다.

| 선택 | 값 | 장점 | 단점 |
| --- | --- | --- | --- |
| A. 빠른 종료 | `PT15M` | 명확한 장애에서 무료 재응시를 빨리 열 수 있음 | AI 지연이나 일시 부하를 최종 실패로 너무 빨리 판단할 위험이 큼 |
| B. 균형안 — 확정 | `PT30M` | 현재 문항 3분 timeout·최대 dispatch 3회와 Summary 복구에 여유가 있고 사용자 대기도 과도하지 않음 | 장애가 명확해도 최장 30분 동안 group이 GRADING에 머물 수 있음 |
| C. 보수안 | `PT60M` | provider 지연과 대규모 backlog에서 오판 가능성이 가장 낮음 | 사용자가 한 시간 동안 새 시험을 시작하지 못할 수 있음 |
| D. deadline 없음 | 없음 | 늦은 Callback을 가장 오래 기다릴 수 있음 | 누락 Callback·손상 Job이 영구 GRADING으로 남을 수 있어 운영상 부적합 |

확정값:

```text
app.attempt-group-events.grading-deadline=PT30M
```

deadline이 지나도 active dispatch나 Callback completion claim이 있으면 즉시 terminal 처리하지 않고 마지막 CAS 재확인을 거친다.

### 3.2 최종 실패 확정 방식 — 확정

| 선택 | 동작 | 장점 | 단점 |
| --- | --- | --- | --- |
| A. retry 소진 즉시 | Question/Summary 자동 retry가 소진되는 즉시 RETAKE_AVAILABLE | 명확한 실패에서 재응시가 가장 빠름 | 마지막 응답이 늦게 도착하는 경우 너무 빨리 포기할 수 있음 |
| B. deadline에서만 | retry가 소진돼도 deadline까지 기다림 | 늦은 Callback을 받아들일 가능성이 높음 | 이미 복구 불가능한 상태에서도 사용자를 불필요하게 기다리게 함 |
| C. 단계적 확정 — 확정 | 정합성 위반은 즉시, retry 소진+active work 없음은 즉시, 그 밖의 정체는 deadline을 safety net으로 사용 | 명확한 실패는 빠르게 처리하면서 누락·정체도 영구 방치하지 않음 | 상태 판정과 race 테스트가 A/B보다 복잡함 |

확정된 C의 정확한 순서는 다음과 같다.

1. 현재 완료 evidence가 있으면 항상 `COMPLETED`가 우선이다.
2. logical result 충돌·소유 관계 불일치 같은 자동 복구 불가 정합성 오류는 `RESULT_INTEGRITY_VIOLATION`으로 종료한다.
3. retry가 소진되고 active dispatch/completion claim이 없으면 결과 누락 또는 Summary 누락 code로 종료한다.
4. 그 밖의 애매한 정체만 deadline 뒤 `GRADING_DEADLINE_EXCEEDED`로 종료한다.

### 3.3 Summary 완료 source — 확정

| 선택 | 동작 | 장점 | 단점 |
| --- | --- | --- | --- |
| A. `exam_summaries` only — 확정 | 신규 Billing-linked Session은 결정적 Summary 문서만 완료 근거로 사용 | source of truth가 명확하고 totalScore만 있는 불완전 문서로 group을 닫지 않음 | rolling deploy 중 구버전 writer가 만든 legacy Summary는 자동 완료되지 않아 명시적 보정이 필요 |
| B. legacy fallback 포함 | `exam_results.totalScore != null`도 완료 근거로 허용 | 과거 데이터와 혼합 배포 호환이 쉬움 | feedback·Summary가 불완전해도 COMPLETED가 될 수 있고 오판 경계가 커짐 |
| C. cutover 혼합 | cutover 이전 Session만 fallback, 이후는 `exam_summaries` only | 과거 호환성과 신규 strict 정책을 함께 확보 | cutover 시각·timezone·재배포 설정과 테스트가 복잡해짐 |

확정안은 A다. 기존 Billing-linked Session이 실제로 남아 있다면 자동 fallback 대신 배포 전 inventory와 명시적 backfill로 처리한다.

### 3.4 401/403 인증 복구 방식 — 확정

| 선택 | 동작 | 장점 | 단점 |
| --- | --- | --- | --- |
| A. 15분 단일 half-open — 확정 | 전역 circuit 차단 후 15분마다 event 한 개만 시험 전송 | IAM 수정 뒤 자동 복구되고 요청 폭주를 막음 | 설정 오류가 지속되면 15분마다 실패 요청 한 건이 발생 |
| B. 수동 해제만 | 운영자가 IAM 수정 후 별도 repair/redeploy로 circuit 해제 | 인증 실패 중 불필요한 요청이 전혀 없음 | 해제 작업을 놓치면 event가 영구 정체될 수 있음 |
| C. 일반 retry 유지 | 다른 5xx와 동일하게 계속 재시도 | 구현이 단순함 | 권한 오류 폭주·로그 소음·비용이 커져 사용하지 않는 것이 좋음 |

확정안은 A이며 `BLOCKED_AUTH` row에는 TTL을 두지 않는다.

### 3.5 기존 Billing-linked Session 처리 — 확정

| 선택 | 동작 | 장점 | 단점 |
| --- | --- | --- | --- |
| A. cutover 이후만 | writer 활성화 이후 새 Session만 event 생성 | 가장 안전하고 rollout이 단순함 | 기존 OPEN/GRADING group이 Billing에서 계속 닫히지 않을 수 있음 |
| B. 전부 자동 reconciliation | 모든 기존 linked Session을 즉시 스캔·발행 | 누락 group을 한 번에 정리 | legacy evidence 오판과 대량 event burst 위험 |
| C. inventory 후 allowlist backfill — 확정 | dry-run으로 대상·evidence를 확인한 뒤 승인된 Session만 backfill | 기존 누락을 복구하면서 오판과 burst를 통제 | 배포 전에 운영 확인 작업이 추가됨 |

확정안은 C다. 구현 자체는 backfill을 자동 실행하지 않고 dry-run과 명시적 대상 입력 경계를 제공한다.

### 3.6 신규 Jira 키 — 추적을 위해 필요

구현 Jira는 `TMI-118`이다. `AGENTS.md`에는 기능 범위를 영구 허용했으므로 Jira별 예외를 추가하지 않는다. Billing `TMI-117`은 선행 consumer 이슈다.

### 3.7 별도 제품 결정 없이 사용할 기술 기본값

다음 값은 처리량 측정 후 설정으로 조절할 수 있어 별도 이견이 없으면 기본값으로 사용한다.

- publisher poll 1초
- batch 20
- lease 30초
- writer와 publisher feature flag 모두 기본 off

## 4. 주요 위험과 미확인 사항

| 우선순위 | 위험 | 계획상 대응 |
| --- | --- | --- |
| P1 | 같은 Session에 COMPLETED와 RETAKE_AVAILABLE이 모두 생성됨 | ExamSession terminal CAS와 outbox terminal slot unique index를 같은 Transaction으로 처리 |
| P1 | 결과 저장 뒤 process 종료로 outbox가 누락됨 | callback path의 Transaction 통합과 reconciliation scheduler를 함께 구현 |
| P1 | RETAKE_AVAILABLE Session을 기존 saga가 replacement source로 찾지 못함 | 별도 local terminal 상태와 replacement source query를 추가하고 operation에 expected group/source를 snapshot |
| P1 | 다른 ECS Task가 같은 outbox를 중복 전송 | 만료 가능한 lease token CAS; Billing eventId/digest 멱등성과 이중 방어 |
| P1 | SigV4 서명 뒤 trace header가 바뀌어 인증 실패 | publish span 생성·inject 후 SigV4 서명, 서명 이후 request 불변 |
| P1 | 401/403을 일반 retry해 요청 폭주 | durable `BLOCKED_AUTH`, 전역 circuit, 고정 counter·alert, 단일 half-open probe |
| P2 | `ExamResult` 존재만으로 완성된 feedback이라고 오판 | required question별 retry 0 결과·feedback·소유 관계를 evaluator에서 strict 검증 |
| P2 | legacy Summary가 신규 Session 완료로 잘못 사용됨 | 신규 Billing-linked Session은 `exam_summaries` deterministic result만 허용 |
| P2 | writer on/publisher off 상태가 오래 지속돼 Billing과 불일치 | publisher를 먼저 idle enable한 뒤 writer enable; 둘의 상태를 readiness/metric으로 관측 |
| P2 | 기존 Billing-linked Session을 자동 backfill해 대량 event 생성 | cutover inventory와 dry-run 후 명시적 backfill 대상만 처리 |
| P2 | 실제 trace exporter가 없어 UI에서 trace가 안 보임 | propagation·span·log 계약까지만 이번 범위; exporter/backend/dashboard는 운영 후속 |

## 5. 현재 코드와 직접 연결되는 설계

### 5.1 현재 준비된 기반

- `ExamSession.attemptGroupId`, `billingReservationId`, `entitlementState`가 이미 존재한다.
- Billing saga는 confirm 성공 뒤 `ExamSession`을 `IN_PROGRESS`·`CONFIRMED`로 만든다.
- `QuestionGradingJob`과 `SummaryGradingJob`은 결정적 ID와 optimistic version을 사용한다.
- `ExamResult`는 `examId`, 실제 `userId`, `questionNumber`, `retryCount`, score와 feedback을 저장한다.
- `ExamSummary`는 별도 `exam_summaries` collection에 결정적 summary ID로 저장된다.
- `ExamGradingService`는 필수 문제 목록과 retry 0 결과·Job 상태를 이미 계산한다.

### 5.2 현재 부족한 부분

- submit 접수 완료와 Billing `GRADING` 사이의 durable projection이 없다.
- Summary 저장, Summary Job 완료, ExamSession 완료가 하나의 Transaction이 아니다.
- 최종 실패를 Session terminal 상태와 Billing failureCode로 수렴시키는 coordinator가 없다.
- outbox collection, lease publisher, retry·retention과 auth circuit이 없다.
- Learning Core에는 W3C-only tracing 기반과 publish span 생성·inject 코드가 없다.
- RETAKE_AVAILABLE인 과거 Session을 다음 Billing REPLACEMENT command와 안전하게 연결하는 local snapshot이 없다.

## 6. 목표 상태 흐름

```text
Billing confirm 완료
→ ExamSession IN_PROGRESS / AttemptGroup local OPEN

모든 필수 retryCount=0 submit + QuestionGradingJob durable 존재
→ local GRADING + GRADING outbox (같은 Transaction/CAS)

모든 필수 retryCount=0 ExamResult + 유효 ExamSummary
→ ExamSession COMPLETED + local COMPLETED + COMPLETED outbox
  (Summary 저장·Job 완료·Session terminal·outbox를 같은 Transaction으로 수렴)

복구 정책 소진 또는 grading deadline 초과
→ ExamSession RETAKE_AVAILABLE + local RETAKE_AVAILABLE + terminal outbox

lease publisher
→ W3C publish span
→ traceparent inject
→ SigV4 최종 서명
→ Billing POST /internal/v1/attempt-group-events
```

`OPEN→COMPLETED|RETAKE_AVAILABLE` 직접 발행도 허용한다. GRADING event보다 terminal event가 먼저 전달될 수 있기 때문이다. Billing consumer도 이 순서 역전을 허용한다.

## 7. local 상태와 producer 불변식

### 7.1 ExamSession 내부 metadata

공개 DTO에 노출하지 않는 내부 필드를 추가한다.

```text
attemptGroupProjectionStatus = OPEN|GRADING|COMPLETED|RETAKE_AVAILABLE
attemptGroupProjectionVersion
gradingStartedAt
gradingEventId
terminalEventId
terminalFailureCode
```

`ExamSessionStatus`에는 내부 terminal `RETAKE_AVAILABLE`을 추가한다.

- `GRADING`: 기존 `ExamSessionStatus.IN_PROGRESS`, `active=true` 유지
- `COMPLETED`: 기존 `COMPLETED`, `active=false`, `completedAt` 기록
- `RETAKE_AVAILABLE`: 신규 terminal status, `active=false`, `completedAt`은 기록하지 않음

사용자용 처리 가능 여부는 `!ABANDONED`처럼 느슨하게 검사하지 않고 명시적으로 `IN_PROGRESS`만 허용하도록 관련 내부 guard를 보완한다. 공개 오류와 DTO는 기존 계약을 유지한다.

### 7.2 Session당 event 슬롯

한 Session에는 다음 두 슬롯만 존재한다.

```text
GRADING slot: 최대 1개
TERMINAL slot: COMPLETED 또는 RETAKE_AVAILABLE 중 최대 1개
```

terminal event 생성 조건은 다음 CAS를 동시에 만족해야 한다.

- Session이 Billing-linked이고 entitlement가 CONFIRMED
- `attemptGroupId`, userId, examId가 nonblank/유효
- 아직 `terminalEventId`가 없음
- 현재 Session이 terminal이 아님
- evidence 또는 failure 판정이 동일 Transaction 안에서 재검증됨

CAS 승자만 Session terminal 전이와 outbox insert를 commit한다. 패자는 최신 Session과 outbox를 다시 읽어 no-op으로 수렴한다.

## 8. 상태별 정확한 판정

### 8.1 GRADING

필수 문제 번호의 source of truth는 Session의 `mockExamId`로 조회한 현재 MockExam catalog다. 프론트가 문제 목록을 보내지 않는다.

각 필수 문제에 대해 다음 중 하나가 존재하면 제출이 durable하게 접수된 것으로 본다.

- 결정적 retry 0 `QuestionGradingJob`
- legacy 호환 retry null/0 `ExamResult`와 이를 보정한 completed Job

마지막 필수 `submit`에서 Job insert/reuse가 완료된 직후 writer를 호출한다. 요청 도중 process가 종료돼 writer 호출이 누락될 수 있으므로 reconciliation도 같은 조건을 주기적으로 검사한다.

### 8.2 COMPLETED evidence

`COMPLETED` event는 다음 세 값을 모두 true로 계산한 경우에만 생성한다.

```json
{
  "requiredFeedbackQueryable": true,
  "validScoreQueryable": true,
  "summaryQueryable": true,
  "evidenceVersion": 1
}
```

판정 기준:

- `requiredFeedbackQueryable`
  - 모든 catalog 필수 문제에 retry 0 또는 legacy null retry의 결정적 `ExamResult`가 정확히 하나의 논리 결과로 존재
  - 각 결과의 `examId`, `userId`, `mockExamId`, `questionNumber`가 Session과 일치
  - 현재 문항 상세 API가 feedback을 조립할 수 있도록 `feedback`이 non-null
- `validScoreQueryable`
  - 동일 Session의 결정적 `ExamSummary.totalScore`가 non-null
  - 점수는 공개 만점 계약에 맞춰 `0..200`
- `summaryQueryable`
  - `exam_summaries`의 결정적 ID `summary:{examId}:v1` 문서가 존재
  - `examId`, `userId`, `mockExamId`가 Session과 일치
  - 현재 Summary API가 요구하는 `partFeedback`이 non-null/non-empty

신규 Billing-linked Session의 자동 terminal 판정에는 legacy `exam_results.totalScore` fallback을 사용하지 않는다. rolling deploy 또는 과거 데이터 보정이 필요하면 별도 inventory와 명시적 migration으로 처리한다.

### 8.3 RETAKE_AVAILABLE

다음 fixed code만 event에 넣는다.

| failureCode | local 판정 |
| --- | --- |
| `REQUIRED_RESULTS_UNAVAILABLE` | 필수 retry 0 Question Job이 최대 dispatch/recovery 정책을 소진했고 결과가 없음 |
| `SUMMARY_UNAVAILABLE` | 필수 문항 결과는 모두 있으나 Summary Job의 자동 복구가 소진되고 유효 Summary가 없음 |
| `GRADING_DEADLINE_EXCEEDED` | GRADING deadline이 지났고 active dispatch/completion claim이 없으며 완료 근거가 없음 |
| `RESULT_INTEGRITY_VIOLATION` | 동일 logical result 충돌, Session 소유 관계 불일치, score 범위 오류 등 자동 복구하면 위험한 정합성 위반 |

AI/provider code, exception message, 문항 번호, 사용자 식별자와 원문은 failureCode나 event에 넣지 않는다.

현재 Job은 dispatch attempt 제한은 있지만 시험 전체 최종 실패와 deadline을 durable하게 확정하지 않는다. 따라서 별도 `AttemptGroupStateReconciler`가 Job 상태·result·deadline·completion claim을 함께 확인하고 terminal CAS를 수행해야 한다.

## 9. Summary Callback Transaction 재구성

현재 흐름은 Summary insert, Summary Job 완료, ExamSession 완료가 순차 호출이다. 다음과 같이 로컬 DB 단계만 Transaction으로 묶는다.

```text
Callback 계약·generation 검증
→ completion claim
→ Mongo Transaction
   1. current generation과 Session active 상태 재검증
   2. ExamSummary 결정적 insert/replay 확인
   3. SummaryGradingJob COMPLETED 전이
   4. COMPLETED evidence 재계산
   5. ExamSession COMPLETED terminal CAS
   6. COMPLETED outbox insert
→ commit
→ Redis status projection 갱신
```

AI 호출, S3 호출, Billing HTTP 호출과 로그 출력은 Mongo Transaction 안에 넣지 않는다. unknown commit은 Summary, Job, Session terminalEventId와 outbox를 다시 읽어 committed outcome으로 수렴한다.

## 10. RETAKE_AVAILABLE과 다음 시험 생성 연결

기존 `prepareForBilling()`과 reserve response 검증은 active Billing-linked Session만 replacement source로 본다. 신규 terminal Session도 안전하게 연결하도록 다음 metadata를 내부 command에 snapshot한다.

```text
replacementSourceSessionId
expectedAttemptGroupId
expectedMockExamId
```

- OPEN 재시작: 기존 active Billing-linked Session을 source로 사용
- RETAKE_AVAILABLE 재시작: 최신 local RETAKE_AVAILABLE Session을 source로 사용
- Billing reserve가 `REPLACEMENT`, 같은 `attemptGroupId`, 같은 `mockExamId`를 반환해야 진행
- Billing이 아직 outbox를 적용하지 않았다면 같은 operation으로 processing/retry하고 INITIAL로 우회하지 않음
- 새 Session commit 뒤 이전 RETAKE_AVAILABLE Session의 결과·Job·Summary를 승계하지 않음

이 변경도 실제 Billing ID를 공개 Request/Response에 추가하지 않는다.

## 11. outbox schema

collection 권장명:

```text
attempt_group_event_outbox
```

주요 필드:

```text
_id = eventId UUID v4
eventType = AttemptGroupStatusChanged
schemaVersion = 1
producer = learning-core
occurredAt
userId
attemptGroupId
sessionId
targetStatus
evidence?
failureCode?
eventSlot = GRADING|TERMINAL
canonicalPayload
payloadDigest
status = PENDING|IN_FLIGHT|DELIVERED|DEAD_LETTER|BLOCKED_AUTH
attemptCount
nextAttemptAt
leaseOwner?
leaseToken?
leaseUntil?
lastFailureCategory?
deliveredAt?
deadLetterAt?
expiresAt?
traceId
parentSpanId
traceFlags
createdAt
updatedAt
version
```

canonical payload는 event 생성 시 한 번 만들고 이후 수정하지 않는다. trace metadata는 payload, canonical digest, idempotency key와 domain aggregate 의미에 포함하지 않는다.

필수 index:

- `{sessionId:1,eventSlot:1}` unique
- `{status:1,nextAttemptAt:1,leaseUntil:1,_id:1}` claim scan
- `{expiresAt:1}` TTL; `expiresAt`이 있는 terminal delivery row에만 적용
- 필요 시 `{attemptGroupId:1,occurredAt:1}` 운영 조회용 non-unique

retention:

- `DELIVERED`: 전달 완료 시각부터 30일
- `DEAD_LETTER`: 격리 시각부터 90일
- `PENDING`, `IN_FLIGHT`, `BLOCKED_AUTH`: `expiresAt`을 두지 않아 TTL 삭제 금지

## 12. lease publisher

### 12.1 claim

여러 ECS Task가 동시에 실행되는 것을 전제로 한다.

1. `PENDING`이며 `nextAttemptAt<=now`, 또는 lease가 만료된 `IN_FLIGHT` row를 조회
2. `findAndModify`로 새 `leaseOwner`, random `leaseToken`, `leaseUntil`과 `IN_FLIGHT`를 CAS 기록
3. HTTP 전송 뒤 동일 leaseToken을 가진 worker만 결과 상태를 갱신
4. lease가 바뀌었으면 `lease_lost`로 기록하고 row를 수정하지 않음

기본 권장값:

```text
poll-interval=PT1S
batch-size=20
lease-duration=PT30S
connect-timeout=PT2S
read-timeout=PT5S
```

### 12.2 retry

같은 eventId와 canonical payload를 유지한다.

```text
5초 → 15초 → 1분 → 5분 → 15분
이후 최대 15분 + bounded jitter
```

- network, timeout, 408, 425, 429, 5xx: `PENDING`, `retry_scheduled`
- 400, 409, 422: `DEAD_LETTER`, `dead_letter`
- 401, 403: `BLOCKED_AUTH`, `auth_failure`, durable global circuit 차단
- 그 밖의 3xx/4xx: non-retryable contract/deployment 오류로 `DEAD_LETTER`와 alert
- 모든 2xx: `DELIVERED`

Billing의 `Retry-After`가 있으면 허용 범위 안에서 반영한다. redirect는 따라가지 않고 response body는 최대 16 KiB로 제한하며 일반 로그에 남기지 않는다.

### 12.3 auth circuit

singleton publisher state에 auth block을 durable하게 저장한다.

- 최초 401/403에서 현재 event를 `BLOCKED_AUTH`로 만들고 circuit 차단
- 다른 event의 신규 HTTP 전송 중단
- counter·운영 alert 기록
- 15분마다 한 event만 half-open probe
- 성공 시 circuit을 열고 `BLOCKED_AUTH` rows를 같은 eventId/payload로 재개
- 다시 401/403이면 circuit을 즉시 차단

auth 문제 때문에 event를 DEAD_LETTER 또는 TTL 삭제하지 않는다.

## 13. W3C trace 설계

### 13.1 저장 metadata

outbox에는 검증된 최소 context만 저장한다.

```text
traceId       # 32자리 lowercase, zero 금지
parentSpanId  # 16자리 lowercase, zero 금지
traceFlags    # W3C flags
```

저장하지 않는 값:

- raw `traceparent`
- raw `tracestate`
- baggage
- 사용자 Access Token과 SigV4 header/credential

현재 유효한 context가 없거나 잘못됐으면 새 fallback trace anchor를 한 번 만들어 event row에 CAS 저장하고 missing/invalid counter를 증가시킨다. trace 문제로 event 생성을 막지 않는다.

### 13.2 publish attempt

각 재시도는 저장된 origin context를 공통 parent로 사용하는 새 CLIENT span이다.

```text
origin/fallback anchor
├── attempt_group_outbox_publish span #1
├── attempt_group_outbox_publish span #2
└── attempt_group_outbox_publish span #3
```

- traceId는 동일
- 각 publish attempt의 spanId는 다름
- 저장된 raw header를 replay하지 않음
- publish span의 현재 W3C context를 HTTP header에 inject
- Billing HTTP server span과 `attempt_group_event_consume` span은 같은 trace의 서로 다른 descendant span

### 13.3 SigV4 순서

```text
method·URI·canonical body·일반 header 확정
→ publish span 생성
→ traceparent inject
→ SigV4 서명
→ 서명된 request를 변경하지 않고 전송
```

자동 HTTP instrumentation이 trace header를 다시 주입하지 않도록 injector 소유자를 하나로 제한한다. 재시도마다 새 span과 새 서명 시각으로 request를 다시 만든다.

## 14. 구조화 로그와 metric

publisher log 공통 필드:

```text
service=learning-core
operation=attempt_group_outbox_publish
outcome=delivered|retry_scheduled|dead_letter|auth_failure|lease_lost
traceId=<publish span trace id>
eventId=<event UUID>
durationMs=<monotonic non-negative integer>
```

`durationMs`는 `System.nanoTime()` 차이로 계산한다. 플랫폼 timestamp는 UTC를 사용한다.

금지 항목:

- userId, sessionId/examId, attemptGroupId, subjectRefId
- candidate와 전화번호 관련 값
- payload, canonical digest, prompt·feedback·Summary·provider 원문
- Authorization, SigV4 header·credential
- raw traceparent/tracestate와 baggage

metric 권장명:

```text
learning_core.attempt_group.outbox.events
learning_core.attempt_group.publish.duration
learning_core.attempt_group.outbox.age
learning_core.attempt_group.retry_exhausted
learning_core.attempt_group.auth_failure
learning_core.attempt_group.dead_letter
learning_core.attempt_group.lease_lost
learning_core.attempt_group.trace_context_missing
learning_core.attempt_group.trace_context_invalid
```

허용 tag는 `service`, `operation`, 고정 `outcome`, 고정 target/status뿐이다. traceId, eventId, 사용자/group/session ID, 자유 문자열 오류와 duration/age는 tag로 사용하지 않는다. duration과 age는 histogram/timer value다.

## 15. 설정과 feature flag

권장 설정 구조:

```yaml
app:
  attempt-group-events:
    writer-enabled: ${ATTEMPT_GROUP_EVENT_WRITER_ENABLED:false}
    publisher-enabled: ${ATTEMPT_GROUP_EVENT_PUBLISHER_ENABLED:false}
    billing-base-url: ${BILLING_BASE_URL:}
    aws-region: ${BILLING_AWS_REGION:ap-northeast-2}
    grading-deadline: ${ATTEMPT_GROUP_GRADING_DEADLINE:PT30M}
    poll-interval: ${ATTEMPT_GROUP_POLL_INTERVAL:PT1S}
    batch-size: ${ATTEMPT_GROUP_BATCH_SIZE:20}
    lease-duration: ${ATTEMPT_GROUP_LEASE_DURATION:PT30S}
    auth-probe-interval: ${ATTEMPT_GROUP_AUTH_PROBE_INTERVAL:PT15M}
```

- 둘 다 기본 off
- writer on이면 replica-set Transaction과 필수 index가 없을 때 startup 실패
- publisher on이면 HTTPS base URL, region, credential provider와 timeout 설정이 없을 때 startup 실패
- local/test는 fake client·fixed clock·in-memory tracer를 사용하고 실제 AWS/Billing을 호출하지 않음
- static AWS credential을 코드·설정·문서에 추가하지 않음

기존 Billing creation saga와 base URL/region을 공유하되, creation saga가 off이고 publisher만 on인 구성도 동작하도록 transport 설정과 Transaction bean 조건을 분리한다.

## 16. 패키지·파일 계획

권장 신규 구조:

```text
web.tosunsaeng.domain.exams.attemptgroup
├── application
│   ├── AttemptGroupStateCoordinator
│   ├── AttemptGroupEvidenceEvaluator
│   ├── AttemptGroupStateReconciler
│   ├── AttemptGroupOutboxPublisher
│   └── AttemptGroupEventMetrics
├── domain
│   ├── AttemptGroupEventOutbox
│   ├── AttemptGroupPublisherState
│   ├── AttemptGroupEventTarget
│   ├── AttemptGroupFailureCode
│   ├── AttemptGroupOutboxStatus
│   └── AttemptGroupEventSlot
├── repository
│   ├── AttemptGroupEventOutboxRepository
│   ├── AttemptGroupEventOutboxClaimRepository
│   └── AttemptGroupPublisherStateRepository
└── infrastructure
    ├── SigV4AttemptGroupEventClient
    ├── AttemptGroupEventCanonicalizer
    ├── AttemptGroupTraceContext
    ├── AttemptGroupEventProperties
    ├── AttemptGroupEventConfiguration
    └── AttemptGroupEventIndexValidator
```

수정 예상 파일:

- `ExamSession`, `ExamSessionStatus`, `ExamCreationOperation`
- `ExamSessionRepository`, `ExamSessionManager`
- `BillingExamCreationSaga`, `BillingExamCreationTransactionService`
- `ExamGradingService`, `ExamServiceImpl`
- `application.yml`, `application-test.yml`, `build.gradle`
- 관련 단위·HTTP·Mongo transaction·security regression 테스트

실제 구현 시 기존 패키지 스타일과 의존 방향을 우선하고, 파일 이름은 코드 대조 후 최소 범위로 조정한다.

## 17. 구현 순서

### Phase 0. 승인 경계

1. `TMI-118` 범위와 이 PLAN 대조
2. `AGENTS.md` 영구 허용 경계와 구현 diff 대조
3. Billing consumer merge·feature flag·배포 순서 재확인

### Phase 1. schema와 설정

1. enum, properties, startup validator 추가
2. ExamSession local projection·terminal metadata 추가
3. outbox/publisher state entity와 index validator 추가
4. writer/publisher default off 확인

### Phase 2. 상태 evaluator와 Transaction writer

1. GRADING submit completeness evaluator
2. COMPLETED strict evidence evaluator
3. failureCode mapper와 grading deadline
4. Session CAS + outbox insert Transaction
5. Summary callback DB 단계를 terminal Transaction으로 통합
6. duplicate/unknown commit reconciliation

### Phase 3. RETAKE replacement 연결

1. RETAKE_AVAILABLE local terminal Session query
2. operation에 replacement source/group snapshot
3. reserve response exact 검증
4. same group/mockExamId replacement와 stale callback fencing 테스트

### Phase 4. publisher

1. canonical event serializer와 immutable payload
2. lease claim·renew/reclaim과 retry schedule
3. W3C context 복원·publish span·inject
4. trace inject 후 SigV4 최종 서명
5. HTTP status 분류, retention과 auth circuit
6. 구조화 로그·metric·privacy guard

### Phase 5. reconciliation과 rollout

1. submit/callback 동기 trigger
2. 누락 복구 scheduler
3. 기존 Billing-linked Session inventory/dry-run
4. idle publisher enable
5. writer canary enable
6. GRADING→COMPLETED와 GRADING→RETAKE_AVAILABLE staging E2E

## 18. 필수 테스트

### 18.1 상태 판정

1. 마지막 필수 retry 0 Job이 durable해질 때 GRADING outbox 한 개 생성
2. retryCount>0은 completeness에 영향 없음
3. 필수 question result/feedback 누락 시 COMPLETED 금지
4. totalScore null, 음수, 200 초과 시 validScore false
5. Summary ownership/mockExam 불일치와 partFeedback empty 시 COMPLETED 금지
6. 네 failureCode의 정확한 mapping과 provider 원문 비포함
7. COMPLETED와 RETAKE_AVAILABLE race에서 terminal 한 개만 commit

### 18.2 Transaction·멱등성

1. Session transition과 outbox insert가 함께 commit/rollback
2. callback 중복과 reconciler 중복이 같은 eventId/payload로 수렴
3. unknown commit 재조회 후 새 terminal event를 만들지 않음
4. terminal slot unique index가 Session당 한 terminal event만 허용
5. 이전 Session 늦은 Callback이 새 replacement 상태를 덮어쓰지 않음

### 18.3 publisher

1. 두 publisher가 같은 row를 claim해도 한 lease token만 승리
2. expired lease reclaim과 stale worker `lease_lost`
3. retry마다 같은 eventId/payload, 다른 publish spanId와 새 SigV4 서명
4. network/408/425/429/5xx retry schedule
5. 400/409/422 dead-letter와 retention 90일
6. 401/403 BLOCKED_AUTH, global circuit과 half-open 복구
7. DELIVERED 30일 TTL, PENDING/BLOCKED_AUTH 무TTL
8. redirect 미추종과 response size 상한

### 18.4 trace·privacy

1. origin, publisher, Billing consumer가 같은 traceId와 서로 다른 spanId
2. 저장된 context를 parent로 새 publish span을 만들고 raw traceparent를 replay하지 않음
3. missing/invalid context의 one-time fallback anchor와 counter
4. baggage가 outbox와 HTTP에 저장·전파되지 않음
5. traceparent inject 후 SigV4 서명 순서
6. log/span/metric에 금지 식별자·payload·credential 없음
7. duration이 monotonic non-negative value이고 traceId/eventId가 metric tag에 없음

### 18.5 회귀

- 공개 API URL·Method·Parameter·Response DTO·BaseResponse 동일
- Python AI `user_id=examId`와 Callback JSON 동일
- S3/Redis/retryCount/소유권 검증 동일
- Billing creation saga INITIAL/OPEN replacement/RETAKE replacement 회귀
- `./gradlew clean test` 전체 성공

## 19. 배포 전 gate

1. Billing `a34766e` 포함 image가 staging에 배포되고 consumer flag가 활성인지 확인
2. Learning Core Mongo replica-set Transaction과 outbox index migration 확인
3. Learning Core task role의 Lattice `POST /internal/v1/attempt-group-events` 최소 권한
4. Billing Lattice auth policy의 Learning Core task role·method·path 제한
5. direct task bypass·wrong role·unsigned request negative test
6. publisher만 먼저 enable해 idle 상태와 auth 확인
7. writer canary enable 후 GRADING/COMPLETED eventId·traceId 종단 확인
8. 503 projection lag, 409 conflict, 401/403, timeout failure injection
9. RETAKE_AVAILABLE 후 같은 consumption·attemptGroupId·mockExamId replacement E2E
10. dead-letter/auth-block alert와 disable/repair runbook 확인

실제 AWS IAM/Lattice/SG/ECS 생성·변경, exporter/backend, dashboard와 alert 인프라 구축은 별도 운영 작업이다.

## 20. 완료 조건

- [x] `AGENTS.md` AttemptGroup 영구 허용 범위 확정
- [x] 신규 Learning Core Jira `TMI-118` 생성
- [x] GRADING deadline `PT30M` 확정
- [x] Billing-linked Session만 writer 대상
- [x] GRADING/terminal Session CAS와 outbox 동일 Transaction
- [x] Session당 terminal event 하나
- [x] strict COMPLETED evidence와 failureCode mapping
- [x] RETAKE_AVAILABLE local Session이 Billing REPLACEMENT로 연결
- [x] lease·retry·retention·BLOCKED_AUTH publisher
- [x] W3C same trace/different span과 baggage 차단
- [x] trace inject 후 SigV4 최종 서명
- [x] 고정 구조화 로그와 저카디널리티 metric
- [x] 전체 테스트 성공 — 439 tests, failures/errors/skipped 0
- [ ] staging failure-injection·INITIAL/REPLACEMENT E2E 성공
- [x] 공개 API·AI·S3·Redis 계약 불변 확인

### 20.1 로컬 구현 결과

- writer/publisher는 모두 기본 off이며, writer 활성화 이후 생성된 Billing-linked Session만 자동 추적한다.
- 기존 linked Session은 자동 backfill하지 않는다. `AttemptGroupBackfillService`가 명시적 Session allowlist의 dry-run과 apply 경계만 제공한다.
- Summary Callback의 결정적 Summary insert, Summary Job 완료, Session terminal 전이와 terminal outbox는 writer 대상 Session에서 같은 Mongo Transaction으로 수렴한다. Redis projection은 commit 이후 갱신한다.
- publisher는 lease token CAS, 동일 event payload, bounded retry, 30/90일 retention과 401/403 durable auth circuit·15분 단일 probe를 구현한다.
- `./gradlew clean test` 결과 439개가 성공했다. 실제 Billing/Lattice/Mongo replica-set staging failure-injection은 배포 전 gate로 남아 있다.

## 부록 A. Billing wire 계약

endpoint:

```http
POST /internal/v1/attempt-group-events
Content-Type: application/json
traceparent: <current publish span context>
```

공통 payload:

```json
{
  "eventId": "8d19e341-ec9c-4efd-b4c0-b1f3ad4c4442",
  "eventType": "AttemptGroupStatusChanged",
  "schemaVersion": 1,
  "producer": "learning-core",
  "occurredAt": "2026-09-01T03:00:00.000Z",
  "userId": "e8b37a41-bae6-47f1-a770-052e6c5786d4",
  "attemptGroupId": "be07ae1d-f877-4ae4-82df-c5f442e9bb8e",
  "sessionId": "ex_a1b2c3d4e5_0901_1200",
  "targetStatus": "GRADING"
}
```

field 규칙:

- body 16 KiB 이하
- UUID는 lowercase canonical UUID v4
- `sessionId`는 1~128자 opaque token
- `occurredAt`은 UTC millisecond canonical text
- unknown/duplicate/trailing field와 scalar/date/enum coercion 금지
- COMPLETED만 evidence 필수
- RETAKE_AVAILABLE만 failureCode 필수
- GRADING은 evidence/failureCode 금지

## 부록 B. 구현 사실·계획·추론 구분

### 확인된 구현 사실

- Billing consumer와 consume tracing은 `develop@a34766e`에 병합됨
- Learning Core `ExamSession.attemptGroupId`와 Billing reservation saga가 구현됨
- Learning Core Summary 저장·Job 완료·Session 완료는 현재 단일 Mongo Transaction이 아님
- Learning Core에는 AttemptGroup outbox/publisher와 W3C publish span이 없음

### 계약상 확정값

- target: GRADING, COMPLETED, RETAKE_AVAILABLE
- failureCode 네 개
- at-least-once, same eventId/payload retry
- DELIVERED 30일, DEAD_LETTER 90일, pending 무TTL
- same traceId/different spanId, baggage 금지
- trace inject 후 SigV4 최종 서명

### 이 계획의 확정 설계

- deadline PT30M
- 신규 Session Summary는 `exam_summaries` only
- poll 1초, batch 20, lease 30초, auth probe 15분
- `ExamSessionStatus.RETAKE_AVAILABLE`과 replacement source snapshot

사용자가 `1B·2C·3A·4A·5C` 조합을 승인했고 작업 추적 Jira `TMI-118`을 생성했다.
