# 유료 시험 단순화 exact R1 — LC 검토

## 1. 5줄 결론

1. 2026-10-14 `lc-billing-paid-exam-simplified-exact-r1-draft-1`은 승인된 단순화 정책을 유지한다.
2. 공개 오류/120일 경계/phone INITIAL snapshot·target 복원 조합의 이번 구체화에 추가 정책 이견은 없다.
3. MEMBER pending의 same-key 재개는 현 worker cleanup intent와 충돌하므로 대기/재개/격리 계약을 보완해야 한다.
4. 삭제 전달 인증·과거 owner·자료별 폐기는 별도 잔여 범위이며 이 문서로 완료되지 않는다.
5. 기존 A/R2/fixtures-2를 유지하고 보완 수락 후 새 fixture를 제작한다. 이번은 최종 공동수락/구현 승인이 아니다.

## 2. 반드시 읽어야 하는 내용

관련 PLAN-011/012/013, TMI-116/TMI-128/TMI-122/TMI-125, 삭제 TMI-193/TMI-136. 사용자 첨부와 LC 오류/응답·saga/worker를 정적 대조했다.

| 항목 | 검토 결과 |
| --- | --- |
| 공개503/생성409/EXAM_4004 | 현 ErrorStatus의 code/message와 일치. 생성중삭제만 endpoint별 새 message 및 Retry-After1 구현 필요 |
| result 생략 | BaseResponse의 NON_NULL과 일치. 내부 retryable/correlationId 공개 추가 없음 |
|120일 | UTC10,368,000초·정각 제외·firstAcceptedAt 불변/terminalAt 분리·남은증거 replay·unknown 비폐기 명확 |
| 기간후 과거key 위험 | 승인된 범위 그대로. 영구 최초key 식별 요구를 다시 추가하지 않음 |
| phone INITIAL | 새group/PHONE_REJOIN echo·transition없음·원 expectedGroup 보존 예외가 명시됨 |
| phone 경합/복원 | discovery pending 우회금지·reserve에서선택·source고정·target수혜/consumption unique가 명시됨 |
| 생성중삭제 | 같은 fence·미접수·삭제선행시생성차단 방향 일치. 실제 runtime 검증은 후속 |

## 3. 보완 요청 — MEMBER 대기와 기존 cleanup의 분리

### 확인된 현재 구현

`BillingReservationReconciliationService.attempt()`는 PREPARED/RESERVED가 precommit 정체 기준을 넘으면 status 전에 `beginCleanup()`을 호출한다. `BillingExamCreationTransactionService.beginCleanup()`은 intent를 CLEANUP_PRECOMMIT으로 영속 변경한다. 이후 `BillingExamCreationSaga.runOperation()`은 이 intent의 PREPARED/RESERVED HTTP 재시도를 EXAM_CREATION_PROCESSING으로 막는다.

따라서 §3.1의 'PREPARED 보존'만 구현하면 다음 경로에서 §3.3/E02의 같은 key 재개가 되지 않는다.

`MEMBER_EVIDENCE_PENDING 수신 → 앱 보강 지연 → worker cleanup intent 확정 → 앱 purchase-account 보강 → 같은 key 재시도 → 생성409 계속 반환`

새 key로 우회하거나 worker가 reserve를 발행하는 해결은 승인 정책과 다르다. 구현 차이가 있다는 이유로 정책을 다시 선택할 필요는 없고, 다음 계약을 명시할 것을 요청한다.

1. 정상 strict MEMBER pending을 관측한 **그 operation**의 대기 사유/복구 처분을 durable 저장해 일반 precommit cleanup과 구분한다. 구체적인 enum/field 명칭은 LC 내부 설계로 남길 수 있다.
2. 대기 중 worker는 새 reserve/증거 수집/guard 해제를 하지 않는다. 필요하면 bounded status 관측과 경보를 하되 OPERATION_NOT_FOUND로 terminal 확정하거나 자동 cleanup으로 옮기지 않는다.
3. 앱의 same-key 재시도는 owner/불변payload/lease/version을 재검증하고 동일 operation을 재개한다. 보강 후에도 원 sessionId/mock/startReason·firstAcceptedAt을 바꾸지 않는다. 로컬 MEMBER 대기 해제만으로 권리를 허용하지 않고 Billing이 증거/권리를 다시 검사한다.
4. pending 응답 저장 전 worker 경합/응답 유실은 단순404로 MEMBER 대기를 추정하지 않는다. 이미 cleanup/격리가 commit된 operation의 안전 재개 조건 또는 운영 검토 필요 여부를 명시한다. HTTP가 intent/격리 상태를 무조건 CONTINUE로 되돌리지 않는다.
5.24시간/200회 또는120일 제한에 이미 도달한 상태와 정상 MEMBER 대기를 구분한다. 앱 복귀가 기존 격리를 자동 해제하는지 임의로 구현하지 말고, 승인된 검토/재개 조건을 정의한다. 무한 고속 retry나 새key 지시는 금지한다.

추가 E02 회귀 권고: precommit 정체 기준 전/후의 앱 보강, pending 저장과 worker 양순서, pending 응답 유실, 앱/worker 동시 lease 경쟁, 같은key 다른payload, 격리/120일 경계의 앱 복귀. worker reserve0·Session/consumption 중복0·guard 유지/첫시각 불변을 실제 worker/Transaction 테스트로 검증한다. static JSON만으로 E02 통과라고 보고하지 않는다.

## 4. 주요 위험과 미확인 사항

- DeletionExceptionAdvice에는 아직 새 삭제409 message/Retry-After 처리가 없다. 일반 생성409 message는 바꾸지 않고 endpoint별 응답을 구현·테스트해야 한다.
-120일 보장에 필요한 receipt coverage는 기존 terminal7일보다 길다. cutover/백업·서비스clock/연결된 미완료 작업 purge 제한을 별도 설계한다. 미완료/금융/consumption/권한 증거를 멱등기간과 함께 지우지 않는다.
- Identity claim/fence 확인은 첨부의 전달 보고로 접수한다. 이번 턴에 Identity 원문/운영 issuer/JWKS를 직접 확인한 것은 아니다.
- 새 삭제 route/A allowlist/owner/자료별 최종 폐기는 명시된 별도 검토 범위다. 이 exact 문서에 다시 전부 포함시키라는 요청은 아니다.

## 5. 직접 관련된 다음 단계

위 MEMBER 대기/cleanup/재개 보완을 반영한 뒤 이번 exact 규격의 공동 수락 범위를 기록하고 새 `lc-billing-paid-exam-simplified-exact-r1-fixtures-1` 제작·bytes/hash 대조·독립검증으로 진행할 수 있다. 기존 승인 정책이나 J3 strict terminal 조건을 다시 선택받지 않는다. LC 계획/ADR/AGENTS 동기화 및 runtime 구현은 해당 승인 범위에 따라 별도로 수행한다.

## 6. 부록 — 근거와 작업 경계

- `src/main/java/web/tosunsaeng/global/error/code/status/ErrorStatus.java`: EXAM_CREATION_PROCESSING/EXAM_4004/BILLING_TEMPORARILY_UNAVAILABLE.
- `src/main/java/web/tosunsaeng/global/common/response/BaseResponse.java`: NON_NULL result.
- `src/main/java/web/tosunsaeng/domain/learningrecorddeletion/api/DeletionExceptionAdvice.java`, `application/DeletionFailure.java`: 현재 삭제 오류 처리.
- `src/main/java/web/tosunsaeng/domain/exams/application/BillingExamCreationSaga.java`: runOperation cleanup intent gate.
- `src/main/java/web/tosunsaeng/domain/exams/application/BillingExamCreationTransactionService.java`: beginCleanup.
- `src/main/java/web/tosunsaeng/domain/exams/billing/reconciliation/BillingReservationReconciliationService.java`: attempt의 precommit cleanup.

이번 변경은 본 검토서/CURRENT_STATE/WORKLOG만이다. 기존 dirty 보존, 코드/계획/ADR/AGENTS/계약/fixtures/타저장소/DB/배포/Jira/외부전송/commit/push 변경과 Secret/Token 기록 없음. 정적검토로 테스트 재실행 생략, git diff --check 수행.

## 후속 — draft-2 MEMBER 대기 보완 검토 (2026-10-14)

`lc-billing-paid-exam-simplified-exact-r1-draft-2`를 직전 첨부와 diff 대조했다. 이전 핵심 의견인 MEMBER 대기와 cleanup 분리·응답유실·같은key 재개 조건이 §3.4/E02-a~h에 반영됐다. Billing 비종료 receipt/status pending, LC durable waiting·worker status-first/무reserve, HTTP lease/version/fence 재개·대기이력 보존 및 cancel불명/성공증거 충돌 시 격리 방향에 추가 이견 없다. 기존 공개오류/120일/phone 검토 결과도 유지한다.

**최종 규격에는 격리 표의 우선순위 한 가지를 명확히 해야 한다.** 현재 LC `ReservationRecoveryStore.retry()`는 retry budget 소진을 `NEEDS_REVIEW`와 reason=`retry_exhausted`로 저장한다. 따라서 표의 'timeout/retry 소진 격리: 동일 MEMBER 대기 원인 증명 시 제한적 재개'와 다음 행의 'NEEDS_REVIEW: 앱 보강만으로 해제 금지'는 현재 저장 상태에서 겹친다.

권고 문구: NEEDS_REVIEW의 일반/불명 사유는 계속 해제 금지이며, **durable 원인 근거로 MEMBER 대기로 인한 소진만 증명된 경우에 한해** 앞 행의 모든 안전 조건과120일 상한을 만족하면 명시된 CAS 재개를 허용한다. 현재 `retry_exhausted` 문자열만으로 MEMBER 원인을 증명하지 않는다. 과거 원인 불명 격리는 운영 확인으로 남긴다. 또는 제한적 재개도 운영 승인 후만 허용할 의도라면 해당 행을 그에 맞춰 명시한다. 새로운 제품 정책을 요구하는 것이 아니라 두 행의 적용 우선순위를 동결하는 요청이다.

E02-f/h에 동일 NEEDS_REVIEW 상태의 MEMBER 원인 증명/불명/일반 장애 소진을 구별하는 사례를 추가한다. waitingSince는 불변 관측근거이고120일 상한의 기준은 기존 firstAcceptedAt임도 구현에서 유지한다. 응답 유실/재개로 새120일 창을 만들지 않는다.

신규 receipt/status 의미와 E02 확대는 아직 공동최종수락/fixture제작/runtime 검증 완료가 아니다. 별도 삭제route/owner/자료별 purge gate와 기존 A/R2/fixtures-2 bytes 보존을 유지한다. 관련 PLAN-011/012/013, TMI-116/TMI-128/TMI-122/TMI-125, TMI-193/TMI-136. 이번 작업은 검토서/상태/기록만 변경했으며 테스트 재실행·코드/DB/배포 변경 없음.

## 후속 — draft-3 격리 우선순위 확인 (2026-10-14)

Billing 원문 `docs/contracts/LC-BILLING-PAID-EXAM-SIMPLIFIED-EXACT-R1.md`의 `lc-billing-paid-exam-simplified-exact-r1-draft-3`을 이전 첨부와 diff 대조했다. 앞선 격리 표의 겹침은 해소됐고 이번 수정 범위에 추가 이견 없다.

- NEEDS_REVIEW 기본 운영 확인과 MEMBER 원인 증명 시의 좁은 예외 우선순위가 명시됐다.
- 같은 operation/key/digest의 영속 대기 관측과 대기→소진 인과 기록, 현재 Billing pending 및 모든 로컬 안전 조건을 함께 요구한다.
- retry_exhausted·현재 pending·앱 보강 성공만으로 추정하지 않으며 이력 누락/원인 혼재는 운영 확인을 유지한다.
- auth/owner/삭제/탈퇴 deny는 예외보다 우선하고, 재개 시 이전 원인/이력도 보존한다. E02-f에 양/음성 사례가 반영됐다.

이로써 해당 exact 규격의 LC 검토 보완 의견은 해소됐다. 다음은 양측 수락 범위를 기록하고 별도 승인 후 새 fixture를 제작·hash 대조·독립검증하는 단계다. 별도 삭제 route/owner/자료별 보존 gate는 그대로 남으며 전체 공동 계약 최종 동결·runtime 구현/배포 완료를 선언하지 않는다. 이번은 검토서/기록만 변경했고 테스트 재실행이나 Billing 파일 수정은 없다.
