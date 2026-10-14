# J3/J4 보완안 R1 — LC 검토 회신

## 5줄 결론

1. 2026-10-14 `lc-billing-paid-exam-j3-j4-r1-draft-1`의 J3 전용 status409·4-field 오류·worker 무reserve 방향은 구현 가능한 제안이다.
2. 현재 LC client/worker는 이를 지원하지 않으며 신규 category·strict 오류 reader·원자 terminal 전이를 추가해야 한다.
3. J3 종료 조건은 PREPARED·새 Session 미commit 증거까지 명시해야 한다. 단순 비활성 Session은 안전 종료 증거가 아니다.
4. J4 source별 owner와 일반 version/ownership epoch 구분에 이견 없으나 ACTIVE MEMBER의 권위·전달·최신성은 미확정이다.
5. 원 R2/fixtures-2 유지, 별도 J3/J4 fixture 검증과 S15/V07 보류를 지킨다. 최종 수락/구현 승인은 아니다.

## 반드시 읽어야 하는 내용

관련 PLAN-011/012/013, TMI-116/TMI-128/TMI-122 및 TMI-125. 앞선 검토서 `LC_BILLING_PAID_IMPLEMENTATION_AGREEMENT_R1_REVIEW.md`의 J3/J4 질문에 대한 후속이다.

| 요청 | LC 답변 | 현재 구현 여부 |
| --- | --- | --- |
| J3 전용 status 오류 분류 | 별도 RESERVE_REJECTED_BEFORE_CREATION category와 status 전용 응답 검증으로 가능 | 미구현 |
| J3 귀속 proof 미추가 | 인증된 exact Billing endpoint·불변 status 요청 결속·서버 key/Transaction 보장을 전제로 최소안 검토 가능 | 응답혼선·모순·실제 서버 검증 필요 |
| J4 source별 owner/fence 비교 위치 | 신규/replay/confirm/LC마무리 분리 및 일반 version 증가 허용에 이견 없음 | 새 owner 상세 미구현/미검증 |
| ACTIVE MEMBER 증거 | 현행 userId만으로는 불충분, 아래 연동 계약 필요 | 미확정 |

## 결정·보완할 사항

### 1. J3 로컬 terminal 조건을 정확히 제한

첨부 §3.3의 'reservationId/confirm 성공/활성 Session 증거 없음'에 더해 다음을 동결할 것을 권고한다.

- exact 로컬 operation이 PREPARED이며 Session commit marker가 없어야 한다. RESERVED/SESSION_COMMITTED/성공에서 역방향 terminal로 바꾸지 않는다.
- **해당 operation의 신규 Session**이 존재하면 active 여부와 무관하게 모순으로 격리한다. 원래 진행 중이던 이전 Session은 존재해도 되며 검사/종료 대상을 혼동하지 않는다.
- status 전후 lease/version·현재 owner/fence를 재검증하고 실패 기록과 자기 guard 해제를 같은 Transaction/CAS로 수행한다. 동시 HTTP가 진전한 경우 다시 읽고 판단한다.
- remote terminalAt은 오류 DTO에 없으므로 로컬 관측/실패기록 시각을 Billing 최초 terminalAt이라고 저장하지 않는다. Billing 최초 시각은 receipt 내부에 보존한다.

새 worker 분류는 HTTP409 + status 응답 + exact code + boolean false + 네 필드의 strict 타입/필수/unknown/duplicate/trailing 검증이 모두 맞을 때만 가능하다. message/correlationId는 식별·귀속 증거가 아니며 correlationId 변경을 멱등 conflict로 보지 않는다. 잘못된 HTTP/code/retryable 조합은 CONTRACT_ERROR/격리로 처리한다. reserve에서 같은 전용 status code가 오는 경우에도 무조건 terminal로 해석하지 않는다.

`OPERATION_OWNER_MISMATCH`는 별도 업무 owner-blocked 분류로 제안한다. 서비스 JWT 인증 실패와 달리 전역 auth circuit을 차단하지 않으며 그 응답만으로 Session 보상/guard 해제하지 않는다. 공개 오류는 여기서 임의 추가하지 않는다. 기존 status의 OPERATION_NOT_FOUND도 계속 부재일 뿐 미소비 증명이 아니다.

### 2. J4 ACTIVE MEMBER 권위와 전달 계약

현행 확인 사실:

- `CurrentUserProvider`는 userId만 제공한다. `JwtCurrentUserProvider`는 검증된 JWT sub를 UUID로 변환하며 MEMBER/ACTIVE 정보를 반환하지 않는다.
- `ChallengeController.member()`는 서명 검증된 JWT의 `account_type=MEMBER`를 검사한다. 이는 그 API의 회원 유형 증거이지 Billing에 전달되는 최신 ACTIVE 상태 증명이 아니다.
- 기존 `BillingReservationClient`의 reserve/status 요청과 LC 서비스 JWT는 현재 사용자 MEMBER/ACTIVE 증언을 전달하지 않는다. workload 인증은 호출 서비스 신원이지 요청 사용자의 현재 계정 상태가 아니다.

따라서 권장 방향은 Identity를 권위로 두고 Billing이 검증 가능한 계정 유형·lifecycle 증거를 갖도록 하는 것이다. 구체적인 동기 조회 또는 이벤트 projection/증언 방식은 기존 Identity–Billing 계약을 확인한 뒤 선택한다. 이번 검토에서 새 API/claim/동기화 기능을 만들거나 방식을 확정하지 않는다.

최소 공동 확인 항목: 권위 발급자·필드/대상 userId·환경/목적 결속, MEMBER와 ACTIVE 각각의 의미, version/epoch·관측시각/최신성 한계, 탈퇴 전파 지연 및 T2와 deny의 선후관계, 조회불가/증거없음의 처리·복구. 사용자 JWT가 없는 worker에서도 검증 가능해야 하며 원문 사용자 토큰을 operation에 저장·재사용하는 대안은 채택하지 않는다. 증거 없음만으로 GUEST라고 재분류하거나 최초 체험 경로로 우회하지 않는다.

J4의 일반 owner 모델은 기술 방향 검토 가능하지만, 이 연동이 미확정인 상태에서 MEMBER_GRANT 정상 경로까지 수락됐다고 표시하지 않는다. source별 epoch 초기화/변경 조건과 ownerMismatch의 hold 정리도 해당 설계/fixture로 검증한다. S15의 phone 정상정책 및 V07 폐기 후 조회는 별도다.

## 주요 위험·미확인 사항

- LC `JwtBillingReservationClient.errorCode()`는 현재 body에서 code만 추출한다. category switch에 이름만 추가하면 malformed/retryable=true·잘못된 status 응답까지 종료할 위험이 있으므로 reader-first 구현이 필요하다.
- `BillingReservationReconciliationService.handleBilling()`에는 신규 두 category가 없다. default retry로 흘리지 않도록 명시 분기 및 owner-blocked/needs-review/terminal을 구분한다.
- J3 최소 오류에는 request digest/owner proof가 없다. exact 요청/응답 결속과 Billing unique receipt 보장을 검증하는 범위이지, 오류 body만으로 이를 암호학적으로 증명한다는 뜻은 아니다.
- V07 미확정이므로 receipt가 영구 유지된다는 가정도, commandTTL 후 지워도 된다는 가정도 하지 않는다. S15/V07 및 ACTIVE MEMBER 미확정 정상 사례는 fixture 성공 기대값으로 만들지 않는다.

## 직접 관련된 다음 작업

Billing에 위 J3 PREPARED/신규Session·시각 구분과 J4 증거 계약 질문을 회신할 수 있다. J3/J4의 확정 가능한 범위와 미확정 부분을 나눠 수락한 뒤 별도 `lc-billing-paid-exam-j3-j4-fixtures-1`을 제작·hash 대조·독립검증한다. 기존 fixtures-2를 변경하지 않는다.

추가 fixture 권고: 전용 code의 잘못된 HTTP/route/retryable/누락·unknown 필드; PREPARED여도 신규 pending Session 존재; HTTP 진행과 worker CAS 양순서; 응답별 correlationId 변경; ownerMismatch 시 전역auth circuit/guard 불변. JSON 모형과 실제 worker/Transaction 검증은 계속 구분한다.

## 부록 — 근거 및 변경 범위

코드 근거(저장소 기준):

- `src/main/java/web/tosunsaeng/domain/exams/billing/BillingClientException.java`
- `src/main/java/web/tosunsaeng/domain/exams/billing/JwtBillingReservationClient.java`: mapError/errorCode.
- `src/main/java/web/tosunsaeng/domain/exams/billing/reconciliation/BillingReservationReconciliationService.java`: attempt/handleBilling.
- `src/main/java/web/tosunsaeng/global/auth/CurrentUserProvider.java`, `JwtCurrentUserProvider.java`.
- `src/main/java/web/tosunsaeng/domain/challenge/ChallengeController.java`: member.

이번 작업은 첨부 전체·관련 LC 코드 정적 검토 및 본 검토서/CURRENT_STATE/WORKLOG 기록만이다. 제품/API/계획/ADR/AGENTS/fixtures/Billing/Identity/DB/배포/Jira/commit/push 변경과 외부 전송 없음. Secret/Token 기록 없음. 기존 dirty 보존, 코드 미변경이라 테스트 재실행 생략, git diff --check 수행.

## 후속: draft-2 재검토 — 2026-10-14

`lc-billing-paid-exam-j3-j4-r1-draft-2`를 직전 첨부와 diff 대조했다. **J3에 앞서 요청한 보완이 반영됐고 이번 변경 범위에 추가 이견 없다.** 앞 절의 J3 보완 요청은 아래 반영으로 해소됐으며 같은 요구를 다시 미반영으로 취급하지 않는다.

- PREPARED/commit marker·reservation/confirm 근거 없음, exact 신규 Session은 pending/비활성이어도 존재 시 격리, 이전 Session과 구분.
- status 호출에만 HTTP409/exact code/boolean false 및4필드 strict 검증 적용, 잘못된 조합은 계약 오류. correlationId는 귀속/멱등 근거 아님.
- 응답 뒤 같은 Transaction에서 lease/version/owner/fence·Session 부재를 재검증하고 HTTP Session 생성도 동일 operation fence에 참여. stale status를 CAS 실패 후 재사용하지 않음.
- 로컬 실패와 자기 guard 해제 원자 저장, 관측시각과 Billing terminalAt 구분, ownerMismatch의 owner-blocked 처리·전역auth circuit/guard 불변.
- J3-08~12에 위 부정/경합 회귀 사례 추가. 아직 fixture 제작·실제 실행 결과가 아니다.

J4는 기술 방향과 **미확정 범위의 명시**가 보완됐다. Billing 로컬 lifecycle ACTIVE가 실시간 Identity ACTIVE 증명이 아니라는 설명, 구매 당시 MEMBER 증거를 현재 worker 증언으로 승격하지 않는 규칙, 권위/전달/결속/최신성/탈퇴전파/장애 질문이 LC 의견과 일치한다. 이 Billing 기존 계약 조사 결과는 이번 첨부의 보고로 접수했으며 Identity/Billing 원문이나 실제 환경을 이번 턴에 직접 확인한 것은 아니다.

다음 진행은 두 갈래로 분리할 수 있다. J3는 공동 수락 범위를 기록하고 별도 승인 후 독립 revision의 fixture를 구체화한다. J4는 Identity 담당과 증거 전달·최신성·탈퇴 선후관계 계약을 먼저 확정한다. J4/S15/V07을 해결됐다고 표시하거나 미확정 MEMBER_GRANT 정상 기대값을 만들지 않는다. 원 R2/fixtures-2 bytes/hash는 그대로 유지한다.

이번은 문서 diff 검토이며 J3의 최종 공동 동결·신규fixture 제작/검증·제품 구현 승인은 아니다. 검토서 후속절/CURRENT_STATE/WORKLOG만 갱신, 테스트 재실행·외부 메시지·코드/DB/배포 변경 없음. 관련 PLAN-011/012/013, TMI-116/TMI-128/TMI-122 및 TMI-125.
