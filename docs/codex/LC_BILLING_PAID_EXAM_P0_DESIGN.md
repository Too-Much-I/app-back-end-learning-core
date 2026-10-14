# LC 유료 시험 P0 — 저장 구조·설정 조합·착수 경계

- 날짜: 2026-10-14 / revision: lc-paid-exam-p0-design-r2
- R2 승인 반영: 기존 operation120일 보관·그룹별 단일 revoke 문서·기존 접수 receipt/완료 marker 재사용 세 항목만 채택한다. MEMBER 관측 축소·NEEDS_REVIEW 예외 제거·추가 flag 단순화는 승인 대상이 아니므로 기존 설계를 유지한다. 문서 변경이며 제품 구현/DB 적용은 아니다.
- 상태: P0 문서 준비 완료. 아래 신규 내부 이름/조합은 LC 설계안이며 제품 구현·공동 잔여 항목 동결·실제 index 적용은 미실행.
- 관련: PLAN-011/012/013, TMI-116/TMI-128/TMI-122/TMI-125, TMI-193/TMI-136.

## 1. 5줄 결론

1. 기존 접수 receipt와 Session.submissionCompletedAt을 재사용하고 기록을 알림 OFF와 분리한다. Session 접수 문항 배열은 추가하지 않는다.
2. 대기 Session은 ENTITLEMENT_CONFIRMING/active=false, 기존 Session은 confirm 성공 전 유지한다.
3. 기존 operation 한 곳에서 120일 결과 보장을 제공하며 별도 생성 결과 receipt collection은 만들지 않는다.
4. 기존 J/S/H/R/W/P 조합을 유지하면서 새 writer·revoke 수신을 default-off로 분리한다.
5. 신규 삭제 route/과거 owner/최종 purge와 실환경은 별도 gate다. 지원 조합이 검증되기 전 새 writer를 켜지 않는다.

## 2. 반드시 읽어야 하는 내용

[구현계획](LC_BILLING_PAID_EXAM_IMPLEMENTATION_PLAN.md)과 [ADR](../adr/ADR-LC-BILLING-PAID-EXAM.md)을 함께 따른다. 기준은 paid R2 draft-2 및 simplified exact draft-3/fixtures-1이다. [검증 보고](LC_BILLING_SIMPLIFIED_FIXTURES_1_VALIDATION.md)의 계약 hash `79bcfe3d79225c21d712791266bea10d4aed9eed3dc0529ddf86f7b29673a482`, SHA256SUMS hash `35f7d437fb562e1427efe385ffaf1721a60f4780fcaacf495c2a0c7e787c2436`를 유지한다. A와 R2 fixture 원본도 수정하지 않는다.

공개 생성 성공 body/DTO·Idempotency-Key·BaseResponse와 submit/Polling/retryCount/AI/S3/Redis는 유지한다. 새 내부 source/group/증거를 공개 DTO에 노출하지 않는다. 내부 source 미정 PREPARED와 reserve 성공 뒤 필수를 구분한다. 무료권 소비·복원·금융 원장은 Billing 책임이다.

## 3. 결정 사항과 남은 공동 확인

| 항목 | P0 결정/후속 책임 | 착수·활성화 경계 |
| --- | --- | --- |
| 선정 기준 | 필수 retry0 전체 durable 접수, 최초 operation 판정 고정 | 채점/요약 시작 조건으로 바꾸지 않음 |
| 저장/flag/index | 아래 로컬 설계안 | 구현 시 validator/실쿼리/격리Mongo 검증으로 확정; 운영 적용 아님 |
| phone paid/free snapshot | latest exact 조합 유지, Billing은 consumption unique/owner epoch 책임 | LC reader와 양측 경합 테스트 전 출시 불가 |
| 신규 삭제 전달 route/auth·과거 owner | LC/Billing 공동 담당 | 기존 continuation을 임의 재사용하지 않음; 해당 조합 OFF |
| 자료별 purge/비식별 연결/backup | LC 삭제 담당과 Billing 보존 담당 | 자동 purge/영구 보존으로 임의 확정하지 않음 |
| origin/port/키 회수·프론트 오류 처리 | 배포 담당/앱/Billing | 실환경 별도 승인·E2E 필요 |

로컬 P0 산출물 준비와 공동 P0 gate 완료는 다르다. 아래 스키마/설정안에 대한 검토 및 미지원 조합 분리가 후속 코드 착수 기준이다. 이번 「어 해줘」는 직전 안내의 P0 수행으로 기록하며 제품 코드 착수로 확대하지 않는다.

## 4. 주요 위험과 현행 사실

- `BillingExamCreationTransactionService.commitReservedSession`은 원 시험들을 먼저 abandon하고 ENTITLEMENT_CONFIRMING/active=true를 insert한다. `ExamSessionRepository.confirmEntitlementIfConfirming`도 active=true를 조건으로 쓴다. 이 둘을 함께 변경해야 한다.
- `findActiveOrLegacyCandidatesByUserId`는 active뿐 아니라 status를 조회하므로 flag 하나만 바꾸면 새 대기/제출차단 원 시험을 잘못 선택한다. public read/submit/upload/retry·history·phone discovery·merge 후보를 모두 분류한다.
- `ExamSession.requireTerminalAvailable`은 ABANDONED를 terminal로 본다. 기접수 완료 허용을 위해 ABANDONED 전역 예외를 만들지 않는다.
- `NotificationSubmissionTracker` OFF는 증거 누락을 뜻할 수 있다. marker 부재=부분접수로 단정하지 않고 신규 schema의 coverage와 집합을 확인한다.
- `ExamCreationOperation.completeRecovery`의 최소7일 및 각 terminal 경로의 purgeAt 설정을 함께 검토해야 한다. 새 유료 operation은 120일 결과 증거로 유지하며 기존 TTL index는 안전한 purgeAt writer와 결합한다. 단일 상수만 바꾸거나 옛 요청을 자동 소급 보장하지 않는다.

## 5. 저장·처리 설계

아래 필드/collection명은 외부 wire가 아닌 신규 LC 내부 설계명이다. 기존 데이터는 일괄 backfill하지 않는다. 모든 외부 HTTP는 Transaction 밖에서 호출한다.

### 5.1 기존 aggregate 확장

| 저장 위치 | 신규/재사용 필드 | 불변식·reader |
| --- | --- | --- |
| exam_sessions | 신규 paidSchemaVersion=1, entitlementSource | 신규 예약 성공부터 EXAM_PASS/SUBSCRIPTION 필수; legacy 판정용 누락 fallback 금지 |
| exam_sessions + 기존 exam_submission_receipts | 신규 submissionEvidenceVersion=1, requiredQuestionNumbers; 기존 submissionCompletedAt 및 receipt의 examId/questionNumber/acceptedAt | 서버 확정 필수집합 불변, retry0 Job 접수 Tx에서 기존 receipt를 멱등 기록. 전체 충족 시 최초 완료 marker와 receipt 완료+3일 TTL을 함께 저장. 미완료 receipt에는 TTL 없음. acceptedInitialQuestionNumbers 배열은 추가하지 않음 |
| exam_sessions | 신규 submissionClosedAt, submissionClosedByOperationId | 이전 시험 신규 제출만 차단. 활성 해제와 함께 기록; 읽기/기접수 처리 허용은 owner/lifecycle와 별도 검사 |
| exam_sessions | 기존 status/active/entitlementState | 대기는 ENTITLEMENT_CONFIRMING/false/CONFIRMING. 성공 Tx에서 IN_PROGRESS/true/CONFIRMED. source·예약·새 Session·원대상 exact 확인 |
| exam_creation_operations | 신규 paidSchemaVersion=1, startReason, requestDigest, firstAcceptedAt, guaranteeUntil | 동일 key에 단일 mock·원대상·사유 고정. firstAcceptedAt UTC ms 최초 commit 불변. public body 없음과 무관하게 내부 고정 요청 digest 저장 |
| exam_creation_operations | 기존 replacementSourceSessionId/expectedAttemptGroupId/expectedMockExamId/phone snapshot; 신규 entitlementSource/transition/confirmRejection | 실제 원대상 재사용. phone INITIAL은 expected 원group를 actual 새group로 덮어쓰지 않음. source/transition omission을 null 덮어쓰기하지 않음 |
| recovery embedded | 신규 waitingReason/waitingSince/lastObservedAt, memberWaitEvidence, resumeDispatch; 기존 lease/version/status/intent/nextAt | 원인증거는 같은 command/key/digest에 결속. 최초 대기 및 격리 인과를 보존. reserve 보내기 전 durable marker; worker 새 reserve 금지 |

`memberWaitEvidence`는 최초 관측·마지막 권위 관측·격리 전이 원인/시각·혼합원인 여부·이전 격리 이유를 구조화한다. 문자열 retry_exhausted만 저장하지 않는다. 무한 이력 배열/원문 JWT/응답 body는 저장하지 않는다. resumeDispatch는 NOT_DISPATCHED/MAY_HAVE_BEEN_SENT/OBSERVED의 기존 dispatch 의미를 재사용하는 방향이며 별도 업무 상태 머신을 만들지 않는다. unknown 전송을 NOT_DISPATCHED로 되돌리지 않는다.

원 시험은 신규 제출 차단 후 active=false로 두되 기존 IN_PROGRESS/AttemptGroup 채점 상태를 유지할 수 있다. 따라서 새 제출 허용 predicate는 active/status/차단/confirmed/owner/lifecycle 모두 검사한다. 채점 predicate는 원 durable Job·필수집합·현재 lifecycle로 판단하며 부분접수는 COMPLETED를 만들지 않는다. 명시적 terminal/삭제/탈퇴/merge 규칙을 제출차단 marker로 무력화하지 않는다.

### 5.2 Transaction과 조회 순서

1. 현재 actor/owner·삭제/탈퇴 fence 확인 → 기존 operation same-key 조회. 결과가 남아 있으면 기간 경과만으로 덮어쓰지 않는다. 별도 결과 receipt reader는 만들지 않는다.
2. 새 요청 준비 Tx: 같은 사용자 guard와 exact 원 Session version을 touch, 필수집합 접수 snapshot·startReason·mock 고정. 동일 마지막 접수와 준비가 경합하면 abort 후 새 Tx로 재판정한다.
3. reserve 성공 snapshot 검증/저장 뒤 대기 Session insert Tx. 원 시험은 수정하지 않는다. 실패·불명은 status-first.
4. confirm/status CONFIRMED 이후 finalize Tx: lease/version·권위 귀속·현재 owner/lifecycle를 다시 확인한다. exact 원 Session 제출차단/active 해제 → 새 Session 활성 → operation SUCCEEDED/guard 해제/불변 결과 저장을 같은 Tx에서 commit한다. 성공 증거는 이 operation에 보존한다.
5. 원 시험이 이미 완료되었으면 완료 상태를 보존하고 다른 최신 시험을 대신 종료하지 않는다. 원대상/owner가 달라졌거나 삭제되면 임의 finalize/부활하지 않고 해당 계약 분류·격리로 수렴한다.
6. unknown commit은 fresh majority 증거 조회. CONFIRMED cancel/새 operation 재지급 금지. 기접수 Summary 완료는 원 group만 갱신하고 새 operation guard를 해제하지 않는다.

삭제 준비도 동일 사용자 guard 쓰기 Tx에 참여한다. 생성 선행이면 실행 가능한 삭제 row 0, 삭제 선행이면 새 생성 차단. 네트워크 호출 동안 Mongo Tx를 열지 않고 HTTP 복귀 시 fence/version을 재검증한다. source/target 동시 처리 lock 순서는 기존 UserMerged의 결정적 순서를 유지한다.

### 5.3 단일 operation·revoke·legacy

| collection 설계안 | 최소 자료/목적 | 삭제 경계 |
| --- | --- | --- |
| exam_creation_operations (기존 확장) | 기존 userId/operationId unique에 requestDigest, schemaVersion, firstAcceptedAt/guaranteeUntil/terminalAt, terminal 결과·session/reservation/group/source/transition·거절 증거, access/deletion fence 참조 | 성공/권위 실패 terminal Tx에 저장. 별도 exam_creation_receipts 없음. 답안/점수/audio/JWT/raw 응답 복제 없음. unresolved는 동일 operation 유지 |
| billing_access_revocations | environment/producer/consumerScopeId/attemptGroupId, 최초 eventId/canonicalDigest/reason/occurredAt/confirmedSessionId(null 포함), firstObservedAt | 이 단일 문서가 dedupe와 deny 증거를 함께 담당. group 및 event unique 모두 유지. 별도 billing_access_event_inbox 없음. 최초 사실·보호예외 불변; 실제 폐기 gate 미확정 |
| exam_legacy_manifest_entries / exam_paid_rollout_state | exact examId unique, manifest revision; 상태 singleton의 hash/count/봉인/reader coverage | 원문 접근 제한. 보고서는 hash/revision/count만. source backfill/신규 writer 편입 없음 |

operation 결과 replay에도 현재 owner/삭제 deny를 적용한다. 삭제된 시험은 EXAM_4004로 응답하고 금융 성공은 남기며 Session을 재생성하지 않는다. UserMerged에서 operation 과거 owner를 임의 rewrite/alias하지 않는다. 그 이후 삭제 연결 상세는 공동 gate다.

새 유료 terminal operation은 max(guaranteeUntil, 최초terminalAt+120일) 전 삭제하지 않는다. 모든 terminal/복구 경로에서 동일 규칙을 적용하며 운영 hold/분쟁/미해결/연결 증거는 eligibility 확인 전 purgeAt을 주지 않는다. terminal 뒤 불필요 lease/재시도 상세는 allowlist 검토 후 정리할 수 있으나 결과·원대상·owner/삭제·미전달 경보/복구 증거는 보존한다. 기존7일보다 용량이 늘 수 있어 대상 BSON/index 크기는 별도 확인한다. TTL/실DB 변경은 이번에 수행하지 않는다. 기간 뒤 모든 key 근거가 합법적으로 정리되면 과거 UUID가 신규 소비가 될 위험은 승인 범위다.

revoke는 event 충돌 먼저, group 충돌 다음 순서와 최초 reason/보호 Session 불변을 유지한다. 동시 insert duplicate-key는 fresh read로 분류한다. 관련 Session 상태를 갱신할 경우 같은 Transaction으로 처리한다. 단일 문서라고 권한 반영/중복 충돌 검증을 생략하지 않는다. 상세 수신 이력은 별도 축적하지 않으며 열린 보호 Session의 deny/예외 증거를120일로 임의 폐기하지 않는다.

legacy manifest는 cutover 직전 구writer drain 이후 봉인하며 reader coverage 검증 뒤 신규 writer를 허용한다. 이미 조회했던1493/146건을 미래 manifest로 고정하지 않는다. 신규 source 필수와 기존 보존은 schemaVersion+서버 manifest로 구분하며 증거 불명은 신규 권리 허용으로 처리하지 않는다.

### 5.4 MEMBER 관측 예산 설계안

- 기존 scheduler poll10초/batch20/동시2/lease30초/attempt20초/HTTP최대2회 예산 안에서 함께 처리한다. 별도 worker/새 reserve는 만들지 않는다.
- 순수 MEMBER 대기의 status-only nextAt은 관측 후60초+0~5초 양의 jitter, Retry-After가 더 길면 그 이후다. HTTP 같은-key 재개는 공통 lease를 얻은 경우만 처리한다. 이는 운영값 적용이 아닌 구현 기본안이다.
- 순수 대기는 장애 attempts를 증가시키지 않는다. 실제 통신/계약/auth 장애는 기존 분류·24시간/200회 및 auth15분 probe를 유지한다. firstAcceptedAt+120일에는 자동재개를 멈추고 증거/guard를 보존한다.
- metric 후보: paid_exam_member_wait_total, paid_exam_member_resume_total, paid_exam_finalize_total; tag는 고정 outcome/reason만. 원격 상세/식별자/토큰은 기록하지 않는다. 경보 재전송 한도는 기존 명시 운영 설정을 재사용하고 새 임의 한도를 만들지 않는다.

## 6. 부록 — 인덱스·flag·검증 전체 표

### 6.1 Index manifest 설계

모든 표의 방향은 +1이며 명시 없는 index는 non-sparse/non-hidden/simple collation/no TTL이다. 기존 이름/options는 그대로 검증하고 충돌 시 fail-closed한다. drop/recreate/실DB DDL은 이번 범위가 아니다.

| collection/index | key/options | 조회/검증 |
| --- | --- | --- |
| exam_sessions / uniq_exam_sessions_active_user (기존) | userId unique, partial active=true | 이전 active 해제→신규 active 설정 동일Tx. Mongo unique slot 교체 회귀 필수; 실제 동작 검증 전 완료 선언 금지 |
| operations / uniq_exam_creation_user_operation (기존) | userId,operationId unique | same-key |
| operations / uniq_exam_creation_active_user (기존) | userId,activeGuard unique, partial activeGuard=true | 사용자 생성 직렬화 |
| operations / ttl_exam_creation_purge (기존) | purgeAt, expireAfterSeconds=0, partial purgeAt type date | 안전 종료·120일 보장/상세 보존·hold 조건 충족 전 purgeAt 없음 |
| sessions / 기존 operation·reservation·group indexes | BillingSagaIndexValidator의 exact key/options 유지 | 대기/완료도 reservation unique 유지 |
| operations / 기존 recovery claim·lease·alert indexes | ReservationReconciliationStartupValidator 유지 | MEMBER도 기존 recovery.nextAt scheduler 사용; 불필요한 별도 wait index 없음 |
| billing_access_revocations / uq_paid_revoke_group (신규) | environment,producer,consumerScopeId,attemptGroupId unique | retained group 충돌·deny |
| billing_access_revocations / uq_paid_revoke_event (신규) | environment,producer,consumerScopeId,eventId unique | 같은 eventId 다른 group 차단. group unique와 함께 단일 문서 dedupe 보장 |
| exam_legacy_manifest_entries (신규) | _id=examId 기본 unique | revision 필드로 봉인 manifest 결속 |
| exam_paid_rollout_state (신규) | _id 기본 unique | 활성 revision/hash/count/coverage singleton |

existing state/update/group index도 계속 필요하며 위 표가 기존 validator의 목록을 축소하지 않는다. revoke/manifest에 purge TTL을 임의 설치하지 않는다. V07 확정 뒤 eligibility/index/query를 추가 검토한다. 기존 접수 receipt index와 완료 후3일 TTL은 유지한다. 결과 조회는 operation user/key index를 사용하며 owner/삭제 fence도 적용한다. 삭제용 sessionId 조회 index 추가는 실제 삭제 연결 계약/쿼리 확인 뒤 결정한다. 별도 결과 receipt/inbox index는 계획에서 제외한다.

### 6.2 설정 명칭과 조합

기존 J=`app.billing.workload-jwt.sender-enabled`, S=`app.billing.creation-saga-enabled`, H=`app.billing.phone-continuation-enabled`, R=`app.billing.reconciliation.enabled`, W/P=`app.attempt-group-events.writer-enabled`/`publisher-enabled`를 재사용한다.

신규 LC 제안: N=`app.billing.paid-exam.enabled` / `BILLING_PAID_EXAM_ENABLED` 기본false. V=`app.billing.access-events.receiver-enabled` / `BILLING_ACCESS_EVENTS_RECEIVER_ENABLED` 기본false. V environment/public key set은 R2 역방향 trust 전용이며 기존 user/Identity/LC sender 키와 union하지 않는다. runtime application.yml에는 아직 추가하지 않는다. 알림 API·tracking·OS 권한은 N의 의존성이 아니다.

| 조합/기능 | 설계상 판정과 준비 조건 |
| --- | --- |
| N=0,V=0 | 기존 J/S/H/R/W/P 22허용/42거절 표 그대로. 새 기능 미동작; 저장된 paid 작업이 없는 초기 상태의 기존 OFF 동작 유지 |
| J=0 AND (S=1 OR P=1) | 기존처럼 거절; H→S,R→S 유지 |
| W=0,P=1,N=0 | 기존 outbox drain 허용; W OR P에서 index/state _id 확인. P→W 강제 금지 |
| N=1 | S=1,J=1,R=1,W=1 필요로 제안. source/120일 operation/owner·생성·제출 fence/Tx/manifest 및 index coverage 모두 점검. 단독 새writer 우회 금지 |
| N=1,P=0 또는 V=0 | 격리 테스트의 부분 구성은 가능. 실제 새 유료 생성 활성화는 금지: 완료 전달/환불차단 준비 필요 |
| V=1,N=0 | reader-first 수신 준비 가능. 전용 키/환경/TLS port/chain·revoke indexes·기존 writer 접근 gate coverage 필수. 수신만 켜고 기존 submit이 deny 무시하는 구성 금지 |
| N=1,H=1 | exact phone paid/free echo/원group와 actual group 분리 및 pending/owner 회귀 통과 필요. discovery 불명 무료 fallback 금지 |
| N=1 + UserMerged/withdrawal | 각 기존 flag validator 유지, 같은 guard Tx·deny 우선 검증. consumer를 강제로 새로 켜지 않음 |
| N=1 + 삭제 command/worker | 생성중 삭제 미접수 fence 가능. 신규 Billing 삭제 전달/과거owner 계약 미확정 동안 전체 조합 활성화 금지; 기존 continuation로 우회 금지 |
| notification tracking=0 | N 접수 증거 정상 동작 필수; 알림3일 TTL로 유료 evidence 소실 금지 |
| N를1→0으로 변경 | 단순 rollback 수단 아님. 기존 paid operation/deny reader·복구 유지 또는 traffic drain. OFF로 과거 legacy 신규생성 우회 금지 |

ON 키/환경 필수검증·OFF 미파싱을 유지한다. 새 설정명·조합은 구현 전 충돌 검사/parameterized startup test로 검증한다. 현행 J/S/H/R/W/P 규칙 위의 추가 N/V 조건이며 기존22/42라는 수치를 새 전체 조합 수로 재사용하지 않는다. 실환경 JWT auth 모드·origin/port·키 회수 SLA는 실제 배포 inventory에서 확정한다.

### 6.3 단계별 완료 증거와 미실행 항목

| 단계 | 후속 제품 테스트 | 현재 |
| --- | --- | --- |
| P1 | 알림OFF/3일TTL/마지막접수·준비 양순서/같은key mock 고정 | 미실행 |
| P2 | 제품 strict decoder, source/transition, phone P01~P05, 오류 E01/E06 | 미실행 |
| P3 | 실제 lease/CAS/unknown commit, MEMBER E02-a~h, operation120일 T01~T06 의미 검증 | 미실행; T04 저장방식 종속 부분은 공동 보완 필요 |
| P4 | 실제 생성/삭제 fence E04/E05/C01, 늦은Summary/submit 차단 | 미실행 |
| P5 | 실제 HTTP/port/trust, group/event 충돌과 rollback/보호예외 | 미실행 |
| P6 | 실제쿼리/index/manifest 실패/구writer drain 모의, 삭제/보존 공동 gate | 미실행 |

최신28개 상태 시나리오의 전체 담당 매핑은 구현계획을 따른다. Billing consumption 복원1회 원자성은 LC 모형으로 검증 완료 처리하지 않는다. 제품 변경 후 clean test·격리 mongoIntegrationTest·해당 migration Node test를 수행한다. P0는 문서 link/diff와 fixture SHA256 대조만 수행하며 과거1,029개 결과를 이번 재실행으로 표시하지 않는다.

T04의 기존 원문 'operation7일 삭제 후 별도 receipt replay'는 이번 승인안과 물리 저장 방식이 다르다. 기존 fixture는 그대로 보존하고 새 공동 보완에서 '120일 operation 유지·재시작/단기 캐시 무관 replay'로 검증 경로를 설명한다. 원 T04를 그대로 실행/통과한 것으로 보고하지 않는다. Billing wire와 자체 receipt/원장 보존은 변경하지 않는다.

### 6.4 코드 근거

- `src/main/java/web/tosunsaeng/domain/exams/domain/entity/ExamCreationOperation.java`, `ExamSession.java`
- `src/main/java/web/tosunsaeng/domain/exams/domain/repository/ExamSessionRepository.java`
- `src/main/java/web/tosunsaeng/domain/exams/application/BillingExamCreationTransactionService.java`, `ExamGradingService.java`
- `src/main/java/web/tosunsaeng/domain/exams/billing/BillingSagaIndexValidator.java`, `BillingSagaProperties.java`
- `src/main/java/web/tosunsaeng/domain/exams/billing/reconciliation/ReservationRecovery.java`, `ReservationReconciliationProperties.java`, `ReservationReconciliationStartupValidator.java`
- `src/main/java/web/tosunsaeng/domain/learningrecorddeletion/config/DeletionStartupValidator.java`
- `scripts/mongodb/tmi-31-migrate-exam-assignment.js`, `src/main/resources/application.yml`
