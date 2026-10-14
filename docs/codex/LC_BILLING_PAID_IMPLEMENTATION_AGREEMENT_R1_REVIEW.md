# 공동 구현 합의안 R1 — LC 검토 회신

## 5줄 결론

1. 2026-10-14 전달된 `lc-billing-paid-exam-r2-implementation-agreement-r1`은 LC 구현계획과 방향이 일치한다.
2. J1/J2는 LC 구현 요구로 이견 없으나 현재 모든 경로가 구현·검증됐다는 수락은 아니다.
3. J3는 예약 생성 전 실패의 status 관측과 worker 종료 조건을 exact 계약/fixture로 추가 확인해야 한다.
4. J4는 owner fence 우선 원칙에 이견 없지만 T2의 source별 owner 모델·삭제 조합표를 받아야 상세 수락 가능하다.
5. S15/V07은 보류하고 원본 R2/fixtures-2를 유지한다. 본 검토는 최종 공동 동결·제품 구현 승인이 아니다.

## 반드시 읽어야 하는 내용

관련 PLAN-011/012/013, TMI-116/TMI-128/TMI-122 및 TMI-125. 근거는 `LC_BILLING_PAID_EXAM_IMPLEMENTATION_PLAN.md`, `LC_BILLING_PAID_FIXTURES_2_VALIDATION.md`와 이번 첨부다. 첨부의 계약/SHA256SUMS hash와89/948 검증 수치는 LC 기록과 일치한다. 이번에는 테스트를 재실행하지 않았다.

| 항목 | LC 검토 결과 | 다음 증거 |
| --- | --- | --- |
| J1 | 요구 수용 가능. 현재 saga findOrPrepare에 activeGuard 확인은 있으나 모든 생성/phone/삭제 경로와 confirm 후 마무리까지 보장됐다고 보고하지 않음 | P0/P3 경로·flag 조합표, 원자적 guard 획득/해제, 새 key 경합 테스트 |
| J2 | 추가 ack/route 없이 같은 operation/status로 LC 마무리 복구하는 방향에 이견 없음 | confirm 성공 유실·로컬 마무리 실패/unknown commit 실제 테스트 |
| J3 | 미확정 유지. reserve의 같은 실패 replay만으로 status-first worker의 관측/terminal 수렴이 정의되지 않음 | 아래 exact 오류/귀속/보존 계약과 신규 fixture |
| J4 | owner fence 우선·paid/MEMBER 보상권을 phone subject로 위장하지 않는 원칙에 이견 없음 | T2 정의/설계 revision, source별 owner snapshot·현재 actor·삭제/merge/withdrawal 조합표 |
| S15/V07 | 보류 유지 | 정상 지원 조합/오류·복구 및 보존·purge/재유입 gate 공동 확정 |

## 결정·확인해야 하는 사항

### J3: 예약 생성 전 terminal 실패

Billing이 제시할 exact 관측 계약에서 다음을 확인한다. 아래는 추가 API/DTO를 지금 확정하는 제안이 아니라 누락 방지 체크리스트다.

- reserve 실패 HTTP/status/code/retryable와 동일 key의 최초 실패 재현 조건, 다른 payload conflict 우선순위.
- 기존 status body userId/operationId 조회에서 예약이 없는 terminal receipt를 어떻게 구분하는지: HTTP/error body/업무 code 및 owner/caller/operation 귀속 증거.
- LC worker가 그 응답을 영속 실패로 판정할 근거와 로컬 terminal/guard 해제 조건. transient404/ingress404/OPERATION_NOT_FOUND와 혼동하지 않는다.
- 실패 응답 유실, command7일TTL 뒤 replay/status, owner 변경·탈퇴·삭제 이후 접근/최소 증거 처리.
- 현 worker의 새 reserve 금지 경계를 유지한다. status로 증명할 수 없다는 이유로 worker에서 reserve를 재발행하는 변경은 별도 계약 없이는 추가하지 않는다.
- fixture에 없으면 별도 revision으로 추가한다. fixtures-2의 SHA256SUMS/bytes를 덮어쓰지 않는다.

### J4: source별 owner와 lifecycle

전달 합의안만으로는 T2의 exact owner snapshot 필드/의미를 확인할 수 없다. EXAM_PASS/SUBSCRIPTION enum만으로 owner 정책을 추정하지 않는다. Billing의 snapshot caller/user/owner fence와 LC의 현재 actor·삭제 deny가 언제 비교되는지, 성공 replay/신규 요청/confirm 전후를 나눠 확인한다. J4의 일반 owner 검증과 S15의 phone 정상경로를 섞어 수락하지 않는다.

## 주요 위험과 미확인 사항

합의안 §2.2의 기접수 작업 완료는 무조건 완료 보장이 아니라 원 durable 접수·멱등성과 lifecycle deny 우선 조건 아래 처리 허용으로 읽는다(§5와 LC P4 기준). 삭제·탈퇴 후 학습자료를 재생성하거나 새 제출을 허용하지 않는다.

확인된 J1 코드 일부만으로 전체경로 안전성을 증명할 수 없다. 테스트89/948은 offline/현행회귀이며 새 유료 runtime/Transaction/상태26개·E2E 증거가 아니다. J1/J2 요구 확인과 실제 통과 증거를 별도 열로 관리해야 한다.

## 현재 작업과 직접 관련된 설명

LC P0에 J1~J4 확인 결과를 반영하는 후속 동기화가 가능하다. 다음 권장 순서는 J3 exact 관측안/J4 owner 표 접수 → 확정 범위와 보류 목록을 명시한 공동 수락 → 계획/ADR/AGENTS 동기화 및 구현 승인이다. 이번 첨부만으로 그 절차가 완료됐다고 표시하지 않는다. Billing 내부 ClientSession adapter나 schema/index를 LC가 구현하지 않는다.

## 부록: 조사 근거와 변경 범위

- `src/main/java/web/tosunsaeng/domain/exams/application/BillingExamCreationSaga.java`: start의 기존 operation/Session replay 및 findOrPrepare의 activeGuard 확인, 기존 상태별 실행 경계.
- `src/main/java/web/tosunsaeng/domain/exams/billing/JwtBillingReservationClient.java`: OPERATION_NOT_FOUND 별도 category 분류.
- LC 자체 검증 기록의 계약 hash `3210c7eef1fb2f9fdde101c2384c86a5bbde1f9be346da0651c971e357444c52`, SHA256SUMS hash `bb4458f3e7b6ed801795fbd652fc41e2d5e6c6c2d752b9ef693d6f43e4893659`와 첨부 값 일치.
- 이번 변경: 본 검토서/CURRENT_STATE/WORKLOG만. 기존 변경 보존. 코드/계획/ADR/AGENTS/계약/fixture/Billing/DB/배포/활성화/Jira/commit/push 변경 및 외부 메시지 전송 없음. Secret/Token 기록 없음. 정적 검토로 테스트 재실행 생략, git diff --check 수행.
