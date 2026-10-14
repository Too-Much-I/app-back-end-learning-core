# ADR — LC 유료 시험의 접수 기준 전환과 복구 경계

- P5.1 후속: [ON 구성·전용 TLS 연결](../codex/LC_BILLING_PAID_EXAM_P5_1_IMPLEMENTATION.md)을 구현했다. OFF에서는 키/DB/TLS를 구성하지 않고 ON 구성은 owner writer 및 index 점검을 요구한다. 별도 rollout gate는 P6 legacy/lifecycle/V07 검증 전 실제 ON을 계속 거절한다. 격리 HTTPS 테스트는 배포 완료가 아니다.

- P5 후속: [역방향 revoke 구성요소](../codex/LC_BILLING_PAID_EXAM_P5_IMPLEMENTATION.md). 단일 group fact/event unique와 Session deny projection을 같은 Transaction으로 저장하고 실제 signed fixture·HTTP/격리Mongo로 검증한다. Spring은 OFF404만 등록, ON wiring/TLS connector·legacy/lifecycle/V07 검증은 미완료이므로 startup gate를 유지한다. 과거 owner alias/Session upsert/원장 변경/자동TTL 없음.

- P4.1 후속: [생성 중 삭제 미접수·오류](../codex/LC_BILLING_PAID_EXAM_P4_1_IMPLEMENTATION.md). 동일 owner fence에서 생성guard를 확인하고 삭제row/알림 suppression 전에409로 거절한다. 삭제된 성공replay는 owner/withdrawal/merge 검증 뒤404이며 writer/finalize 우회가 아니다. 신규 삭제 ACK와 원문정리 분리는 후속 설계, 기존 worker 교체·활성화는 미실행이다.

- P4 후속: [기접수 작업 분리](../codex/LC_BILLING_PAID_EXAM_P4_IMPLEMENTATION.md). 신규 Job 접수와 원 durable Job/receipt 기반 후처리를 공통 policy로 구분한다. coverage 전체접수 marker 없이 Summary/COMPLETED를 만들지 않으며 기존 owner/삭제 fence를 우회하지 않는다. 공개 계약/fixture/paid 기동차단은 유지하고 삭제 연결·legacy 전환은 별도 gate다.

- 2026-10-14 정정 수락: simplified fixtures-2의 E06-owner 문구를 LC 독립 재검증했다. [검증 기록](../codex/LC_BILLING_SIMPLIFIED_FIXTURES_2_VALIDATION.md). 아래 과거 E06-owner 미해결 gate는 이 정정 건에 한해 해소됐으며 나머지 공동 검증/활성화 gate는 유지한다. fixtures-1 및 제품 auth 정책 불변.

- P3.1 후속: [MEMBER 영속 대기·HTTP same-key 재개](../codex/LC_BILLING_PAID_EXAM_P3_1_IMPLEMENTATION.md)를 내부 서비스에 구현했다. worker는 status-only, 순수 대기는 장애 budget에서 제외한다. NEEDS_REVIEW는 검증된 과거 순수 MEMBER 소진 증거와 현재 안전 조건을 모두 충족할 때만1회 재개한다. 원인 불명/auth/owner 차단·120일 경계·paid 기동 차단은 유지한다. 공개/자동 scheduler 및 실환경 공동 검증은 후속이다.

- P3 후속: [paid lifecycle/status-first 구현](../codex/LC_BILLING_PAID_EXAM_P3_IMPLEMENTATION.md)을 추가했다. legacy saga는 보존하고 paid 전용 내부 서비스는 공유 lease/owner Tx를 사용한다. 원 시험의 접수차단은 grading 상태와 분리한다. 신규 공개/자동 worker 연결·P3.1/P4~P6 및 paid 기동은 미완료/차단 유지. E06-owner 정정은 사용자 승인으로 병행 추적하되 공동 최종 검증·활성화 전 해결한다.

- P2.1 후속: strict 오류 분류·phone paid/free exact 검증과 owner Tx evidence 저장 준비 완료. [P2.1 보고](../codex/LC_BILLING_PAID_EXAM_P2_1_IMPLEMENTATION.md). E06-owner 원본 문구 불일치는 공동 정정 필요. MEMBER 실제 재개/J3 terminal·P3 saga 연결·기능 활성화는 미실행이며 기존 차단을 유지한다.

- 날짜: 2026-10-14
- P2 후속: 사용자 요청으로 일반 reserve/status wire·source/transition/거절의 embedded evidence 저장을 구현했다. [P2 보고](../codex/LC_BILLING_PAID_EXAM_P2_IMPLEMENTATION.md). P2.1/phone·P3 활성화/복구 연결은 후속이며 기존 default-off/기동 차단을 유지한다.
- P1 후속: TMI-218 기반 구현·단위/격리 Mongo 검증 완료. [구현 보고](../codex/TMI_218_P1_IMPLEMENTATION.md). 공통 접수 writer와 준비 snapshot은 구현됐으나 공개 유료 생성 연결/활성화 및 P2~P6는 완료하지 않았다.
- 상태: 사용자 구현계획 승인 후 P0 설계 기록. 제품 구현/공동 잔여 계약/실환경 검증 완료 아님.
- 2026-10-14 후속 승인: 세 내부 저장 단순화만 반영했다. 별도 생성 결과 receipt·revoke inbox·Session 접수 문항 배열을 추가하지 않는다. MEMBER 관측/격리 자동재개 및 나머지 계약은 유지한다.
- 관련: TMI-116/TMI-128/TMI-122/TMI-125, TMI-193/TMI-136; PLAN-011/012/013.

## 1. 5줄 결론

1. 필수 retry0 전체 접수 여부를 알림과 독립적으로 보존하고 단일 mock과 시작 사유를 고정한다.
2. 대기 Session은 active=false로 기존 시험과 공존하고 confirm 후 exact 이전 제출 차단과 신규 활성화를 원자적으로 처리하는 설계를 채택한다.
3. 채점 lifecycle과 사용자 제출 허용을 분리해 기접수 Job/파생 Summary를 보존한다.
4. 기존 operation/lease/version/owner guard를 재사용하고 operation 자체를120일 결과 증거로 보존한다. revoke는 단일 그룹 문서, 접수는 기존 receipt/완료 marker를 사용한다.
5. 새 flag/index 이름은 LC 내부 설계안이며 계약 원본과 실환경 설정을 바꾸지 않는다. 잔여 삭제/보존 gate는 분리한다.

## 2. 반드시 읽어야 하는 내용

정책 기준은 [구현계획](../codex/LC_BILLING_PAID_EXAM_IMPLEMENTATION_PLAN.md), exact 기준·검증 범위는 [LC 독립 검증 기록](../codex/LC_BILLING_SIMPLIFIED_FIXTURES_1_VALIDATION.md)이다. [P0 상세 설계](../codex/LC_BILLING_PAID_EXAM_P0_DESIGN.md)에 field/index/flag/테스트와 보류 범위를 연결했다.

기존 [workload JWT ADR](ADR-LC-BILLING-WORKLOAD-JWT.md)의 A 인증·sender·publisher drain 계약은 대체하지 않는다. 두 후보/transition.type/accessVersion/rejectedAt을 되살리지 않는다. 공개 시험 생성 body 없음·성공 DTO/BaseResponse·AI/S3/Redis 계약은 유지한다.

## 3. 결정 범위

P0의 로컬 설계안 작성과 ADR/AGENTS 동기화까지 수행한다. 신규 삭제 전달 인증/과거 owner 처리 및 자료별 폐기 계약은 LC/Billing 담당이 확인한다. 기존 승인 제품 정책을 다시 선택받는 일이 아니며, 확인 전 해당 조합을 출시 가능으로 표시하지 않는다.

## 4. 위험과 대안

- 현재 confirm 전 일괄 abandon은 원 시험 선종료 위험이 있다. exact 원 대상과 confirm 후 마무리로 교체할 계획이다.
- active=false만으로는 충분하지 않다. 기존 repository는 status로도 대기 시험을 조회하고 confirm 쿼리는 active=true를 요구한다. 모든 reader/validator 전환과 격리 Transaction 검증을 함께 수행해야 한다.
- ABANDONED 예외를 전역 허용하지 않는다. submissionClosedAt과 durable 접수 증거로 좁게 허용하고 기존 terminal/삭제/탈퇴 우선순위를 유지한다.
- 별도 결과 receipt 없이 operation을 max(guaranteeUntil, 최초terminalAt+120일)까지 보존한다. 각 terminal/purge writer를 함께 검증하며 unresolved/hold는 무TTL이다. 불필요 terminal metadata 정리와 용량/개인정보 조건은 별도 확인하고 단순 상수변경으로 완료 처리하지 않는다.
- revoke 한 문서에서 group/event unique와 최초 payload digest/보호 Session을 보존한다. 답안 접수는 기존 receipt를 사용하고 전체 접수 marker를 남긴 뒤에만 receipt3일 TTL을 적용한다. 알림 OFF는 접수 기록을 끄지 않는다. T04 등 저장방식 종속 공동 설명은 보완하되 원본 fixture를 수정하지 않는다.
- source 누락을 legacy로 추론하지 않는다. 서버 exact manifest가 읽히지 않으면 새 권리를 임의 허용하지 않는다.

## 5. 결과와 검증

후속 P1~P6는 P0 상세 표의 test gate를 따른다. 기존 오프라인81/전체1,029 통과는 이전 실행 기록이며 실제 상태28개는 미실행이다. 문서 정리로 runtime 테스트 통과/DB 설치/공동 최종 동결을 주장하지 않는다.
