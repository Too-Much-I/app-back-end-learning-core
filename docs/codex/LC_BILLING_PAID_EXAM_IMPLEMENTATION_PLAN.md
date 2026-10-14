# LC–Billing 유료 시험 연동 구현 계획

- 작성일: 2026-10-14
- 세 단순화 승인 반영(2026-10-14): 기존 operation120일 결과 보관·그룹당 단일 revoke 문서·기존 접수 receipt/완료 marker 재사용을 적용한다. 별도 생성 결과 receipt/inbox/Session 접수 문항 배열은 추가하지 않는다. MEMBER 관측 주기/NEEDS_REVIEW 예외 등 나머지 정책은 변경하지 않는다. 이번은 문서 수정이며 제품 코드/DB 변경은 아니다.
- 후속 승인: 2026-10-14 사용자 「좋아 지금 계획안 승인할게 다음은 뭐야」로 본 계획안을 승인했다. 아래 승인 전/초안 표기는 작성 당시 이력이다. 다음은 P0의 ADR/AGENTS·schema/index·flag 조합 정리 및 잔여 계약 경계 확인이며, 이번 턴은 승인 기록과 다음 단계 안내만 수행한다. 계획 승인으로 공동 계약 잔여 항목의 확정이나 실제 DB 적용·배포·활성화 완료를 주장하지 않는다.
- 상태: 사용자 요청으로 작성한 **구현 승인 전 계획 초안**. 공동 최종 동결/ADR·AGENTS 변경/제품 구현/DB 변경/배포·활성화를 수행하거나 승인받았다는 의미가 아니다.
- 개정: 2026-10-14 최신 exact/독립 검증 반영본. 이번 승인은 계획서 최신화이며 제품 구현 승인은 아니다.
- 기준: 기반 `lc-billing-paid-exam-contract-r2-draft-2`/`lc-billing-paid-exam-r2-fixtures-2` + 보완 `lc-billing-paid-exam-simplified-exact-r1-draft-3`/`lc-billing-paid-exam-simplified-exact-r1-fixtures-1`. 보완 범위는 최신 exact를 우선하며 기존 원본 bytes는 유지한다.
- 관련: Billing PLAN-011/012/013, LC TMI-116(생성 saga)/TMI-128(복구)/TMI-122(phone)/TMI-125(owner lifecycle). 기존 이슈는 연관 근거이며 유료 기능 전체에 대한 기존 구현 승인을 뜻하지 않는다.

> 2026-10-14 후속 사용자 승인 반영: §3.1 정책과 §5의 최신 exact 세부안을 적용한다. 신규 fixture 인수/독립81개 및 전체1,029개 검증은 완료했지만 상태28개는 실제 미실행이다. 정책·문서 승인을 제품 구현 승인으로 확대하지 않는다. 삭제 관련 TMI-193/TMI-136도 연관된다.

## 1. 5줄 결론

1. 접수 증거를 알림 기능에서 분리하고, 시작 사유와 단일 문제·원 대상을 operation 준비 시 고정한다.
2. Billing 응답의 source/transition을 검증·보존하고 confirm 성공 후에만 이전 시험의 신규 제출 차단과 새 시험 활성화를 원자적으로 마무리한다.
3. 이미 접수된 채점·파생 Summary는 허용하되 신규 제출과 lifecycle 종료/삭제를 구분하며 기존 operation으로 끝까지 복구한다.
4. 역방향 revoke는 그룹별 최초 불변 사실로 저장하고, 기존 시험은 서버 exact manifest로 보존한다.
5. S15/V07의 제품 방향은 §3.1 승인대로 반영하고 exact wire/보존 조합은 잔여 의존성으로 남긴다. 별도 구현 승인 후 기본 OFF로 구현하며 배포/활성화는 별도다.

## 2. 반드시 읽어야 하는 내용

### 범위와 불변 계약

- LC 제품 변경 후보: 접수 evidence/문제 선정, reserve/status client와 snapshot, 생성 saga/로컬 마무리/복구, Session/Job 접근 판단, revoke receiver, legacy 경계, 관련 테스트·운영 절차.
- Billing 담당: 권리 선택·소비·최초 trial consumption당 최대1회 복원·원 group 재응시 차단의 원자성, 영속 성공/거절 결과, revoke outbox/signer. LC에서 무료권/구독/금융 원장을 구현하지 않는다. Billing 저장소도 수정하지 않는다.
- 공개 `POST /api/v1/exams`의 body 없음·기존 Idempotency-Key 규칙·성공 DTO/BaseResponse 유지. 앱은 source/startReason/group/userId를 지정하지 않는다. 신규 공개409만 계약·앱 reader 확인 후 추가한다.
- submit/Polling/retryCount·S3/Redis key·Presigned URL·AI `user_id=examId` 및 Callback JSON 유지. 새 시험에 이전 답안/점수/Job/Summary를 복사하지 않는다.
- workload JWT A 계약과 기존 A fixtures는 불변이다. 신규 역방향 revoke trust는 별도로 구성하고 test-only oracle을 제품 보안 구현으로 간주하지 않는다.
- 기존 feature OFF 동작을 유지한다. 유료 기능의 exact flag 명칭/의존성 표는 P0에서 제안·수락하며 이 문서로 실제 환경변수를 추가하거나 활성화하지 않는다.

### 핵심 처리 순서

`owner/fence 확인 → 같은 key replay → 원 대상·접수판정·mock 고정 → reserve → 확인대기 Session commit → confirm/status 성공 확인 → 로컬 마무리 Transaction → 공개 성공`

같은 key의 다른 canonical payload는 conflict다. 동일 요청 replay는 현재 Session stale 검사보다 먼저지만 인증/owner fence를 우회하지 않는다. 원 Session 종료 전까지의 유효 사용자 접수는 기존 규칙을 따르며, 준비 시 고정한 사유는 뒤늦은 접수로 바꾸지 않는다. 새 시험은 로컬 마무리 전 사용자 제출에 사용할 수 없다.

## 3. 사용자가 결정해야 하는 사항과 착수 조건

지금 새로 선택할 필요 없는 정책: 단일 mock, 세 startReason, 모든 전환의 기접수 마무리, Billing 전담 복원, 불변 revoke, terminalAt 재사용, legacy 보존. 이 정책들을 다시 선택받지 않는다.

| 항목 | 계획상 처리 | 완료 책임/조건 |
| --- | --- | --- |
| R2/fixtures-2 공동 수락·기준 동결 | LC 독립 검증 결과와 exact revision/hash를 인용한 별도 수락 기록 | 양측; 검증 성공과 계약 최종 수락을 구분 |
| 구현 범위 승인 | 본 계획 검토 후 명시적 구현 승인 | 사용자; 기존 JWT B 승인을 유료 기능으로 확대하지 않음 |
| ADR/AGENTS 동기화 | 승인된 범위·공개409·전환/수신 예외를 기록 | LC; 이번 계획 작성에서 변경하지 않음 |
| S15 phone/삭제 | 유료 우선·조건부 target복원/명시삭제 재응시포기 정책 확정. echo/snapshot/삭제전달·오류는 잔여 | LC/Billing 공동 wire·경합 검증, 명시삭제 무료 continuation claim/transfer 복구 제외 |
| V07 보존/purge | 동일key120일 보장·기간후재입력 위험수용 확정. 자료별 폐기 조건/개인정보 상세는 잔여 | 양측; 상세/최소금융·소비·권한/미완료 증거 분리 |

S15/V07을 계획에 보류로 둔 채 독립적인 부분의 설계는 가능하다. 그러나 해당 의존성을 가진 schema·삭제·phone 흐름을 최종 구현 완료로 처리하거나 전부 무기한 보존/임의 TTL로 대신하지 않는다. 좁은 구현 단계만 먼저 진행하려면 지원/비지원 조합과 기존 기능 회귀 차단 방식을 P0에서 별도 수락한다.

### 3.1 후속 승인 — 재가입 유료 우선 유지와 단순화

이 절은 정책 승인 경위를 보존한다. 당시 '공동 동결'로 남겼던 오류/시간/phone 세부안은 현재 §5의 exact draft-3 구현 대상으로 구체화됐다. 신규 삭제 전달/과거owner/자료별최종purge만 별도 미확정 범위로 구분한다.

- **phone:** target의 유효 paid 우선, 없으면 정상 이전된 기존 무료 continuation. paid 불명은 무료 fallback 금지. 최초 미완료 trial이고 미복원·명시삭제 아님 등 승인 자격을 충족한 paid confirm에서 target에게 복원권1개를 지급한다. Billing이 원 consumption ID unique로 반복 재가입에도1회를 보장한다. source의 paid/기지급 복원권 자동이전 없음. GRADING pending/COMPLETED NOOP·owner epoch 규칙 유지. phone INITIAL echo/귀속·경합은 새 공동 계약/fixture로 확인한다.
- **생성 중 삭제:** reserve/confirm뿐 아니라 LC 로컬 마무리까지 미완료면 삭제 요청을 접수·queue 저장하지 않고 재시도 안내로 거절한다. 문구는 “시험 준비 중입니다. 잠시 후 다시 시도해 주세요.”이며 exact public HTTP/code는 기존 삭제 오류와 대조 후 동결한다. 생성 준비와 삭제 접수를 같은 사용자 fence/Transaction으로 순서화한다. 생성 선행은 삭제 미접수, 삭제 선행은 기존 삭제절차/관련 생성 차단. 자동 삭제 재실행이나 삭제 deny 우회 finalize 특례를 추가하지 않는다. 정상 응시 Session의 삭제를 금지하는 정책이 아니다.
- **삭제 접수 이후:** Billing ACK와 개인정보 원문 정리를 별도 checkpoint로 두어 원격 장애로 실제 삭제를 무한 연기하지 않는다. 원 consumption 무료 재사용/삭제 이유 복원은 금지하며 다른 권리와 기지급 복원권은 보존한다. 삭제 후 과거 성공 replay로 학습자료를 재생성하지 않는다.
- **MEMBER:** 앱이 기존 purchase-account를 유효 Identity JWT로 호출해 정규화 증거를 보강한다. 계정/ref 유지·WITHDRAWN 우선·원문 JWT 미보관. worker는 원 operation 증거를 보존하고 pending/관측 처리만 하며 새 증거 수집/사용자 token 재사용/새 reserve를 하지 않는다. 앱 복귀·같은 key 재시도로 진행한다. 무증거는 GUEST/권리부족과 구분한다. 매시험 실시간조회/전체회원 projection은 제외.
- **120일:** 동일key 결과 멱등 보장은120일이며 이후 근거가 없으면 과거 UUID가 새 요청/새 시험·추가 무료권 소비로 처리될 위험을 사용자가 수용했다. 이를 막기 위한 영구 최초key 저장/새 서버 발급 시작권은 범위 밖이다. 따라서 이 위험만을 이유로 tombstone purge를 차단하지 않는다. 요청 재실행 상한은 최초 operation 생성시각, 종료상세 보존은 최초 terminalAt부터120일을 기준안으로 두고 정확 정각/clock·앱 공개문구를 공동 동결한다. 남은 상세 결과로 replay 가능하나 무기한 보장하지 않는다.
- **보존 예외:** 미완료/unknown/격리 evidence는120일만으로 삭제하지 않는다. 기존24시간/200회 격리가 우선이고120일까지 자동 retry하지 않는다. 금융원장·consumption/복원1회 unique·삭제/revoke/보호Session 증거는 별도 보존한다. 오래된 key 위험수용은 환불권리 재사용/재복원 허용이 아니다. V07 전체기간·개인정보 purge까지 확정된 것은 아니다.

후속 회귀: 생성/삭제 fence 양순서·삭제요청 미생성, MEMBER 앱보강/same-key·worker reserve0, phone target수혜/consumption unique,120일 내 유실복구·이후 보장제외/남은증거 replay·unknown 비폐기·금융/권한/복원 unique 유지. 기존89개 fixture 검증에 포함됐다고 표시하지 않는다.

## 4. 주요 위험과 미확인 사항

- 기존 생성 Transaction은 confirm 전에 진행 중 Session들을 abandon한다. 단순히 종료 시각만 뒤로 옮기면 활성 Session unique/index와 충돌할 수 있어 대기 Session의 상태·active 의미·query/index를 함께 점검한다.
- 신규 제출 차단과 기접수 Job 완료 허용을 `ABANDONED` 하나로 표현하면 기존 Callback 거절 로직과 충돌한다. 제출 차단 사실과 채점 가능 evidence를 분리해야 한다. exact 내부 필드는 schema 검토에서 결정한다.
- 현재 `NotificationSubmissionTracker`는 tracking OFF이면 기록하지 않는다. marker 없음만으로 INCOMPLETE를 추정하면 권리/문제 선정이 틀릴 수 있다.
- 제출 receipt 완료 후3일 TTL은 유지하되 완료 marker와 Job 기접수 증거의 용도를 구분한다. receipt를 Job 생존/보존의 유일 증거로 사용하지 않는다. 개인정보의 exact 보존은 V07과 연결된다.
- confirm 성공 후 owner 변경·삭제·탈퇴가 선행하면 성공 Session을 재생성하거나 권한을 되살릴 수 없다. Billing 성공은 보존하고 LC 마무리/격리 결과를 공동 검증한다. 미확정 terminal 상세를 임의로 새 enum/공개오류로 확정하지 않는다.
- 기반 R2 독립89개와 최신 simplified 독립81개는 서로 다른 검증 묶음이다. 최신 전체 clean test1,029개에 포함돼 있으며 합산 보고하지 않는다. 신규 상태28개·실HTTP/Transaction/실환경은 미실행이다.

## 5. 단계별 구현 계획

각 단계는 아래 선행조건을 만족하고 별도 구현 승인 후 수행한다. 모든 외부 HTTP는 Mongo Transaction 밖에서 호출한다.

### P0 — 계약·schema·범위 설계 확정

2026-10-14 P0 문서 준비: [저장 구조·index·flag 조합 설계](LC_BILLING_PAID_EXAM_P0_DESIGN.md)와 [유료 시험 ADR](../adr/ADR-LC-BILLING-PAID-EXAM.md), AGENTS의 한정 준비 범위를 추가했다. 로컬 산출물 작성 완료이며 공동 잔여 계약/제품 구현/실DB 적용 완료는 아니다. 내부 명칭과 지원 조합은 설계안으로 구분한다.

- 기반 R2와 simplified exact draft-3의 한정 수락/원본 hash/독립검증을 인용하고 제외 범위를 명시한다. 새 검증을 다시 미완료로 취급하지 않는다. 이전 draft의 initialMockExamId/transition.type/accessVersion/high-water/semantic history/rejectedAt을 되살리지 않는다.
- 기존 saga/worker/phone/삭제/UserMerged/withdrawal/AttemptGroup writer와 새 기능의 조합표를 작성한다. flag 기본 OFF, ON 시 키/환경·필수 index/Transaction 조건 검증, OFF 미파싱을 포함한다.
- exact 내부 필드/collection/index, 대기 Session active 의미, 신규 생성·제출 fence를 설계한다. 신규 operation부터 source/evidence 필수로 하고 기존 문서 전체 required/backfill은 금지한다.
- 상태 전이는 기존 operation/lease/version/guard에 수용하는 것을 우선한다. 별도 finalization job 시스템을 만들지 않는다. 추가 enum/metadata가 필요하면 기존 reader와 양측 영향 검토 후 정한다.
- S15/V07에 영향받는 스키마와 비영향 부분을 분리하고 미확정 항목별 담당·해결 전 금지 작업을 기록한다.
- 완료 기준: 공동 wire 수락 및 구현 범위 승인, ADR/AGENTS 동기화, schema/index·flag 조합표 검토 완료. 실제 DB 적용은 제외.

착수 분할: P0 완료 후 P1·P2/P2.1 reader/schema 준비 → P3/P3.1 복구·P4/P4.1 lifecycle → P5 및 P6/P6.1 보존/전환 검증 순서다. reader가 없는 환경에서 새 writer를 켜지 않는다. P5의 보안·codec 설계는 독립 준비 가능하지만 삭제/revoke 증거 최종 폐기·새 삭제 route 활성화는 잔여 계약 확정 전 완료 처리하지 않는다. 단계별 작은 변경으로 unit/contract/격리 Mongo 검증을 나눠 수행한다.

### P1 — 알림 독립 접수 증거와 단일 문제 선정

- 구현 추적: [TMI-218](https://to-teacher.atlassian.net/browse/TMI-218). 2026-10-14 사용자 구현 승인으로 P1 기반 코드/검증 완료, [구현 보고](TMI_218_P1_IMPLEMENTATION.md). Jira 상태는 별도로 변경하지 않았다. 공개 생성 경로 연결·Session coverage 설치·wire digest는 P2/P3 후속이며 배포/활성화 완료가 아니다.

- `ExamGradingService`의 신규 사용자 Job 접수 Transaction에서 필수 retry0 접수 evidence를 저장한다. 알림 전송/OS 권한/notification tracking과 무관하게 신규 유료 경로에 보장한다. 알림은 같은 evidence를 소비하도록 책임을 분리한다.
- 최초 불변 필수 문항 집합, retry0 중복 제거, 최초 접수 시각·전체 접수 marker를 유지한다. 시스템 재시도/replay/retryCount>0은 필수 최초 접수를 늘리지 않는다.
- 기존 exam_submission_receipts와 Session.submissionCompletedAt을 공통 접수 기능으로 재사용한다. Session에는 필수집합/coverage만 추가하고 accepted 문항 배열을 중복 저장하지 않는다. 미완료 receipt 무TTL, 전체접수 marker 저장과 완료+3일 TTL 설정을 같은 Tx로 유지한다. marker 부재는 coverage 확인 없이 미접수로 추론하지 않는다.
- 원 Session 마지막 접수와 operation 준비를 동일 owner/Session version fence로 직렬화한다. abort 후 새 Transaction으로 재판정하되 이미 commit된 operation의 판정은 바꾸지 않는다.
- `RESTART_INCOMPLETE`: 일부/0개 접수가 증명되면 같은 mock. `NEW_AFTER_SUBMISSION`: 전체 접수·결과 미완료면 직전 mock 제외 후 기존 count/sequence 선정, 다른 적격 후보 실제0개일 때만 동일 mock. `NEW_AFTER_COMPLETION`: 권위 완료 evidence 이후 기존 선정 정책. catalog 장애는0개로 취급하지 않는다.
- 신규 Session/원 Session·group/사유/mock/필수집합 관련 snapshot과 canonical 요청 동일성을 operation에 고정한다. 새 문제 선정이 재응시 권리를 부여하지 않는다.
- 완료 기준: 준비/마지막 접수 양순서, tracking OFF, receipt TTL 이후, catalog 변화/장애, same-key replay/다른 payload, 부분접수/동일mock fallback 테스트 통과.

### P2 — Billing client와 source/transition 영속

- 2026-10-14 구현: 일반 reserve/status typed wire·exact validator·embedded evidence 저장과 로컬 테스트를 추가했다. [P2 보고](LC_BILLING_PAID_EXAM_P2_IMPLEMENTATION.md). P2.1/phone·공개 saga/worker 연결·P3 lifecycle/활성화는 포함하지 않는다. source/transition/confirmRejection은 `paidReservation.snapshot`으로 매핑하며 실제 상태 전이는 후속이다.

- reserve의 기존3필드에 optional restartContext 세 필드를 확장한다. 부분 객체·명시 null·unknown/duplicate/trailing/coercion을 거절한다. phone 필드와 혼합하지 않는다. operationId는 기존 command header, status는 body userId/operationId/no Idempotency-Key를 유지한다.
- reserve 성공/status snapshot에 EXAM_PASS/SUBSCRIPTION을 필수 검증한다. PREPARED에는 source 미정 허용. confirm/cancel DTO의 field 생략으로 저장된 값을 null로 덮어쓰지 않는다.
- 요청 Session/mock exact 일치, REPLACEMENT 원group/mock·transition 없음, restart INITIAL 새group/exact transition, NEW_AFTER_SUBMISSION/COMPLETION의 INITIAL만 허용을 검증한다.
- source/원·신규 연결을 client snapshot→operation→Session/복구 evidence에 보존한다. 단순 enum 일치로 동일 구매/권리 재응시를 추정하지 않는다.
- `confirmRejection={code:ENTITLEMENT_REVOKED}`는 CANCELED+terminalAt에서만 허용한다. 성공/거절 증거 공존·불변 snapshot 불일치는 계약 오류로 격리한다.
- 완료 기준: fixtures wire/status 예시를 실제 client decoder/validator 테스트에 연결하고 누락/금지조합·응답 유실·잘못된 원대상·구reader 회귀 검증. 성공 예시9개만으로 전체 decoder 검증을 대체하지 않는다.

### P2.1 — 최신 내부 오류·phone reader 준비 (P3 선행)

- 2026-10-14 구현 결과: [오류·phone 기반 보고](LC_BILLING_PAID_EXAM_P2_1_IMPLEMENTATION.md). strict client/검증/저장·mapper/worker 방어 분류와 로컬 테스트 완료. E06-owner fixture 문구 불일치는 별도 기록했으며 원본을 변경하지 않았다. 아래 실제 MEMBER 재개/J3 terminal 조건 및 공개 saga·worker 연결은 P3/P3.1에서 구현하며 현재 활성화 차단을 유지한다.

- 내부 오류는 code/message/retryable/correlationId 네 필드의 필수·타입·unknown/duplicate/trailing/coercion 및 HTTP/호출별 조합을 검증한다. code-only reader 확장만으로 완료하지 않는다. category에 MEMBER 대기/owner-blocked/J3 terminal을 분리하고 기존 switch/metric/HTTP mapper/worker 분기를 함께 점검한다.
- reserve/status의503 MEMBER_EVIDENCE_PENDING/true/Retry-After1은 비종료 대기다. 고정 message는 snapshot §3.1을 따른다. 현재 owner deny와 권위 성공/terminal이 대기보다 우선한다. status는 조회만 하며 LC가 원장/예약 생성을 기대하지 않는다.
- status 전용409 RESERVE_REJECTED_BEFORE_CREATION/false는 PREPARED/예약·신규Session·commit marker 없음 및 owner/lease/version 조건에서만 terminal 처리한다. reserve에 같은 code가 오거나 조합이 틀리면 계약 오류다. owner mismatch는 업무차단이지 전역 서비스 auth circuit 사유가 아니다.
- phone 응답 예외: INITIAL/SUBSCRIPTION은 새group!=expectedGroup, REPLACEMENT/EXAM_PASS는 원group이다. 둘 다 요청 신규Session/discovery mock/PHONE_REJOIN reason/id를 echo하고 transition이 없다. 일반 restart INITIAL의 transition 필수 규칙은 유지한다. 원 expectedGroup을 실제 새group로 덮어쓰지 않는다.
- discovery GRADING pending/불명/오류는 paid로 우회하지 않는다.204는 일반 INITIAL, 같은key replay는 owner fence 뒤 가변 context 검사보다 먼저다. reserve 뒤 source는 재선택하지 않으며 변경된 owner/group/권리 결과는 공동 exact 분류를 따른다.
- Billing의 target 복원1회/원consumption unique는 LC 재구현 대상이 아니다. LC는 요청/응답 귀속·same-key·원학습자료 비이전과 새group guard 보호를 검증한다.

### P3 — confirm 후 원자적 마무리와 기존 operation 복구

- 2026-10-14: 별도 paid 전용 lifecycle/recovery 서비스로 기존 legacy Transaction 동작을 보존하면서 구현했다. [P3 구현 범위](LC_BILLING_PAID_EXAM_P3_IMPLEMENTATION.md). 공유 lease·status-first·exact 접수차단/활성 원자성·120일 terminal 기반이며 공개 route/자동 schema2 scheduler 연결·MEMBER(P3.1)·P4~P6/활성화는 후속이다. 원 Session은 ABANDONED 대신 접수차단 metadata로 채점 lifecycle과 구분한다. 아래 기존 메서드명은 설계 책임을 뜻하며 실제 paid 서비스 진입점은 보고서를 따른다.

- `commitReservedSession`에서 이전 진행 시험 일괄 abandon을 제거하고 권리 확인대기 새 Session만 저장한다. 새 Session은 사용자 제출 불가이며 기존 시험을 권리 부족/통신 실패 때문에 선종료하지 않는다.
- `finalizeConfirmed`는 확정된 operation/reservation/session/source 귀속을 검증한 뒤 같은 Transaction으로 exact 이전 Session 신규제출 차단·새 Session 사용 가능·operation SUCCEEDED를 기록한다. 이미 terminal/삭제된 원대상은 부활시키지 않으며 다른 최신 Session을 대신 종료하지 않는다.
- 기존 worker와 HTTP가 공통 lease/version/intent를 사용한다. Billing CONFIRMED 후 로컬 마무리 실패는 같은 operation의 status-first 복구로 수렴한다. 성공 confirm cancel/새 reserve/새 operation 재지급은 금지한다.
- 미완료 guard와 evidence에 TTL을 적용하지 않는다. 404/RESERVED/timeout/lease 만료/retry 소진으로 보상하지 않는다. 권위 CANCELED/EXPIRED/영속 거절 및 귀속 확인 후만 안전 보상한다. unknown commit은 fresh majority evidence로 확인한다.
- 공개 처리: 기존409 EXAM_CREATION_PROCESSING 및503 BILLING_TEMPORARILY_UNAVAILABLE·Retry-After:1 유지, 신규409 ENTITLEMENT_REVOKED/EXAM_TRANSITION_CONFLICT는 정확 mapper와 앱 reader 테스트를 추가한다. CONTRACT_ERROR/auth circuit을 일시 retry로 합치지 않는다.
- LC operation 자체를120일 결과 증거로 보존해 성공/거절 replay를 제공한다. 별도 결과 receipt는 만들지 않으며 Billing 단기 command TTL과는 독립이다. 기간 이후 무기한 replay를 약속하지 않는다. 미완료/unknown evidence는 나이로 폐기하지 않는다.
- 완료 기준: confirm 응답 유실/환불 양순서, 로컬 commit 불명/재시작/동시 replay, guard 유지, 새 key 차단, TTL 이후 replay, owner/lifecycle 경합의 격리 Mongo 테스트.

### P3.1 — MEMBER 영속 대기와 안전한 HTTP 재개

- 2026-10-14 fixture 정정: [simplified fixtures-2 독립 재검증](LC_BILLING_SIMPLIFIED_FIXTURES_2_VALIDATION.md)으로 E06-owner 문구 불일치를 해소했다. 기존 fixtures-1 보존, 계약/제품 정책 불변. 다른 공동 상태/실환경/활성화 gate는 유지한다.

- 2026-10-14 내부 구현: [P3.1 보고](LC_BILLING_PAID_EXAM_P3_1_IMPLEMENTATION.md). 영속 관측/HTTP same-key 재개·worker status-only·CAS·순수 대기 예산 제외·제한적 과거 소진 증거 수용을 추가했다. 공개/자동 scheduler 연결·실제 양측 E02 전체 검증은 미완료이며 활성화 차단 유지. 과거 소진 증거 자동 생성/수입 도구는 없다.

- 기존 PREPARED/recovery에 waitingReason/waitingSince/lastObservedAt·원인 이력 결속을 저장하는 schema를 마련한다(명칭은 후보). 최초 대기시각·firstAcceptedAt·요청 digest/session/mock/context/owner를 고정하며 JWT 원문은 저장하지 않는다. source 미결정을 성공 snapshot으로 꾸미지 않는다.
- strict pending 관측 및 PREPARED/예약·신규Session·commit marker 부재 확인 후 동일 Transaction에 대기 기록을 저장한다. 기존 이전 Session 존재는 부재 검사 대상이 아니다. 실제 Billing 비종료 receipt는 Billing 담당이다.
- worker는 대기를 일반 precommit cleanup보다 먼저 분기한다. dispatch된 PREPARED의 최초 응답유실도 cleanup 의도 확정 전 status 관측으로 복구한다.404/인증·계약 오류는 대기 증거가 아니다. worker reserve/독자 회원증거 수집 금지.
- 앱 purchase-account 선호출/보강 후 HTTP 같은key 재개만 허용한다. 공통 lease/version/fence와 actor/삭제/탈퇴 deny, 동일payload, 자기guard, 신규Session/예약/commit/cancel전송·불명 부재를 검증한다. 앱의 보강 성공 주장을 신뢰해 권리를 부여하지 않는다.
- 원격 호출 전 대기 이력을 지우지 않고 재개중 처분을 영속해 다른 worker cleanup을 차단한다. pending이면 같은 대기, 응답유실이면 status-first, 예약/terminal이면 권위 결과로 진전한다. 오래된 version 결과는 재조회하며 PREPARED로 되돌리지 않는다.
- CLEANUP_PRECOMMIT+PREPARED는 현재 Billing의 durable pending 및 모든 안전 조건이 있을 때만 CAS로 대기 복귀 가능. cancel전송/unknown·예약/Session 근거 있으면 금지한다.
- NEEDS_REVIEW는 기본 운영 확인이다. 같은 operation/key/digest의 MEMBER 관측과 대기→소진 영속 인과 기록, 현재 pending 및 안전 조건이 모두 있는 경우만 좁은 재개 예외다. retry_exhausted·앱보강·현재pending만으로 해제하지 않는다. 혼합원인/이력부재·BLOCKED_AUTH/OWNER 및 lifecycle deny는 예외보다 우선한다. 이전 격리 이력은 보존한다.
- MEMBER 순수대기 시간을 장애 retry budget으로 계속 소모하지 않으며 bounded 관측/경보로 처리한다. 실제 장애의24시간/200회격리는 유지한다. firstAcceptedAt+120일 정각부터 자동재개 금지/운영확인, 증거/guard 유지. 관측 poll/대기index/분리 metric exact값은 P0에서 기존 예산과 함께 정한다.
- 완료 기준: E02-a~h를 실제 saga/worker/replica-set 경합으로 실행하고 worker reserve0·중복Session0·stale쓰기0 확인. 오류값만 반환하는 test-only 모형으로 대체하지 않는다.

### P4 — 제출 차단과 기접수 채점/요약의 분리

- 사용자 신규 제출/새 녹음 Job과 기존 durable 접수의 Callback/재시도/파생 Summary를 구분하는 공통 판단을 적용한다. 공개 submit·retry·upload/read와 내부 Job·Callback 경계별 권한 표를 작성한다.
- 원 Session 종료 후 신규 사용자 접수는 차단한다. 종료 전에 접수한 Job의 동일 요청 replay는 새 접수로 만들지 않는다. 기존 Presigned URL의 잔여 capability를 임의 취소하는 새 key/nonce 계약은 추가하지 않는다.
- 기접수 문항 채점·결과 저장·나중의 Summary 최초 생성/시스템 재시도는 원 접수 evidence와 기존 멱등/lifecycle 검증을 통과할 때만 허용한다. 단순 Session abandoned 예외를 모든 Job에 적용하지 않는다.
- 일부 접수는 전체 COMPLETED를 만들지 않는다. 늦은완료는 원 Session/group에만 남고 새 group/생성 operation guard를 해제하지 않는다. Billing 복원 grant를 LC가 취소하거나 무료권을 추가하지 않는다.
- 삭제/탈퇴/owner deny가 우선한다. UserMerged/삭제와 Transaction guard 참여·deadlock 순서를 점검한다.
- 완료 기준: 종료와 접수 양순서, 일부/전체 접수, 늦은 Callback/파생 Summary/시스템 retry·신규 녹음, duplicate/stale callback, 삭제/merge 경합 테스트.

### P4.1 — 생성 중 삭제 미접수와 공개 응답

- 삭제 command 준비와 생성 operation 준비가 같은 사용자 fence를 조건부 쓰는 Transaction에 참여하도록 바꾼다. 생성 guard가 살아있으면 실행 가능한 삭제 row/inbox/queue를 만들지 않는다. 생성 Session ACTIVE 여부만으로 guard를 대신하지 않는다.
- 삭제409 EXAM_CREATION_PROCESSING은 “시험 준비 중입니다. 잠시 후 다시 시도해 주세요.”/Retry-After1, 일반 생성409는 기존 같은요청재시도 문구를 유지한다. BaseResponse result=null은 생략하며 내부 retryable/correlationId를 추가하지 않는다.
- MEMBER pending 공개503은 기존 BILLING_TEMPORARILY_UNAVAILABLE/기존문구/Retry-After1. 앱은503만으로 원인을 추정하지 않고 기존 purchase-account의 준비/보강 흐름을 사용한다. 삭제선행의 기존 LEARNING_RECORD_DELETION_IN_PROGRESS는 그대로 유지한다.
- 삭제된 Session의 과거 성공 key replay는404 EXAM_4004/기존문구/Retry-After없음/result생략. 금융성공·소비를 취소하거나 새Session으로 재생성하지 않는다.
- 삭제접수 후 원문정리와 Billing전달 ACK를 분리하는 checkpoint를 설계한다. 기존 무료 continuation claim/transfer를 명시삭제 경로에서 재사용하지 않는다. 새삭제 route/auth·과거owner·ACK 이후 신규학습 허용 상세가 미정이므로 해당 연결은 구현/활성화 완료로 간주하지 않는다.
- 완료 기준: E04/E05/C01에 실제 deletion row0 및 fence 양순서 테스트, 정상 응시중 시험은 생성완료 후 삭제가능 회귀. 삭제대기queue/deny우회 finalize 특례 없음.

### P5 — 역방향 revoke 수신

- exact `POST /internal/v1/entitlements/access/events`, 독립 Billing issuer/sub/aud/keySet/환경·설정으로 기본 OFF 수신기를 구현한다. A parser/time 규칙을 따르되 기존 Identity/user/LC 발신 trust와 union/fallback하지 않는다.
- 보안 우선순위: 살아있는 OFF/잘못된 port404 → 활성 query/Bearer/JWT 실패401 → 검증된 wrong sub/aud/method/path403 → body검증. 빈 body/no-store·다른 chain 비침범·인증 실패 업무/DB 무호출을 실제 filter/HTTP 테스트로 검증한다. 포트 미기동은 transport 실패다.
- raw16,384bytes, strict JSON/UTF-8/scalar·고정 canonical 필드순서/bytes를 적용한다. 크기413 → 문법/unknown/필수/형식400 → 지원되지 않는 의미422 순서를 지킨다.
- 그룹별 단일 revoke 문서에 최초 eventId/digest/수신시각/deny·보호 Session을 저장하고 별도 inbox는 만들지 않는다. eventId 및 logical group(환경/인증producer/scope/group) unique를 모두 유지한다. 같은ID/digest204, 같은ID 다른내용 EVENT_ID_CONFLICT409, 같은group 다른ID GROUP_REVOKE_CONFLICT409를 event 충돌 우선으로 검사한다. 동시 insert duplicate-key는 fresh read로 분류하고 필요한 Session 변경과 원자 반영한다. 보존이 필요한 차단 증거를120일 수신 이력 TTL로 지우지 않는다.
- 최초 reason/occurredAt/보호 Session(null 포함)을 불변으로 둔다. higher version/semantic history/권한 복원/upsert로 Session 부활 없음. 보호 Session은 lifecycle보다 우선하지 않는다.
- 완료 기준: shared canonical/token을 제품 verifier/serializer에 적용, 실제 HTTP/port/chain·동시 first/중복/충돌 rollback, 보호예외와 confirm/revoke 양순서 테스트. retention/index 최종 적용은 V07 의존성 해결 후.

### P6 — legacy·index·운영 전환 준비

- 전환 직전 서버 exact 시험ID manifest를 사용한다. source 없음/클라이언트 시각으로 legacy를 허용하지 않는다. 원 Session source backfill, 기존 소비/복원 증거 생성, 일괄 종료/삭제를 하지 않는다.
- manifest 원본은 접근 제한하고 보고서에는 revision/hash/건수만 남긴다. 생성/검증/조회 실패·manifest 밖 신규writer 처리의 fail-closed 기준을 테스트한다. 이전1493/active146 조회를 미래 고정 목록으로 사용하지 않는다.
- 구writer drain → 새 inventory/manifest 확정 → index/schema 확인 → reader/source writer coverage 확인 → 별도 승인 전환 순서를 runbook으로 설계한다. manifest 수집과 writer 재개 사이 빈틈이 없도록 서버 경계를 검증한다.
- 신규/기존 collection key/options·unique/partial/TTL와 실제 query를 점검한다. dry-run과 충돌 보고를 우선하고 임의 drop/recreate/데이터 정리는 하지 않는다.
- 앱 신규409 처리, Billing 배포 호환, 역방향 키/origin/port·회수 SLA, 경보/운영복구·롤백 절차를 분리한다. flag OFF만으로 이미 생성된 nonterminal operation/revoke evidence를 버리는 롤백을 금지한다.
- 완료 기준: 문서와 격리 DB 검사 통과. 실제 prod inventory/DB/index 적용/배포는 별도 승인 실행이다.

### P6.1 —120일 operation 결과 보관과 안전한 정리 준비

- UTC millisecond 정규화 서버시각을 첫 유효 요청의 PREPARED/영속terminal 최초 commit에 firstAcceptedAt으로 고정한다. D=10,368,000초, now<firstAcceptedAt+D만 보장 창 안이다. waitingSince/lease/login/재전송으로 창을 갱신하지 않는다. JWT 나노초 규칙은 변경하지 않는다.
- 기존 exam_creation_operations를 유일한 LC 요청 결과 증거로 유지한다. 별도 receipt collection/이중 reader/복제 Transaction은 추가하지 않는다. payload digest·owner/access fence·최소 성공/실패 및 삭제표식을 기존 operation reader로 복구 가능하게 보존한다. 원문 답안/AI/JWT/raw 응답을 장기 복제하지 않는다.
- LC 상세는 max(guaranteeUntil, 최초terminalAt+D) 전 삭제하지 않는다. Billing 최초receipt+D 및 미완료 보호와 양측 coverage를 대조한다. TTL index만으로 운영 hold/분쟁/미완료 여부를 판정하지 않으며 안전 eligibility 확인 후에만 정리 대상화한다.
- 창밖에도 남은 증거는 owner/삭제검사 후 replay하고 살아있는 결과를 덮어쓰지 않는다. 모든 key근거가 정당하게 폐기된 뒤 신규요청으로 처리될 위험은 승인범위다. 이를 막는 영구key체계는 만들지 않는다.
- 미완료/unknown/격리는 operation을 그대로 유지하고 purgeAt을 주지 않는다. 금융/Claim/consumption/복원unique·삭제revoke/보호Session 증거의 목적별 보존은 결과120일과 구분한다. terminal 불필요 lease/재시도 상세 정리는 allowlist·남은 경보/증거 확인 뒤 수행한다. 최종 개인정보 purge gate 미확정으로 실제 자동폐기는 별도다. 큰clock이상/backup재유입 때 purge중지·원시각/deny 유지 절차를 설계한다.
- 기존자료는 cutover 표에서 증거보유/부족/미완료/legacy를 분리한다. 이미7일 TTL로 사라진 옛 요청을 새 보장 대상으로 소급 backfill하지 않는다. 새 유료 operation의 모든 terminal/recovery purge writer가120일 보장을 지키는지 검증하며 Billing 단기cache 정책은 변경하지 않는다. 기존 LC7일 삭제를 새 유료 operation에 유지하지 않는다.
- T04의 기존 'operation7일 삭제→별도 receipt 복구'는 저장방식 종속이므로 공동 보완 대상이다. 새 구현 테스트는 operation120일 보관·재시작/단기캐시 무관 replay를 검증한다. 원 fixture bytes/기대는 수정하지 않고 원 T04 실행 통과로 표시하지 않는다.
- 완료 기준: T01~T06 실제 저장/query/모의시계·격리Mongo 복구검증. purge 실행·prod inventory/설정변경은 제외한다.

## 6. 검증·완료 판정

| 검증 층 | 실행 대상 | 완료 증거 |
| --- | --- | --- |
| 불변 기준 | 기존 A/paid fixtures-2/simplified fixtures-1 | 원본 byte/hash 그대로, 새 회귀는 별도 파일/revision |
| unit/contract | 접수판정·선정·strict reader·serializer·mapper·flags | ./gradlew clean test, fixture기대/실제품 테스트 구분 |
| HTTP/security | 신규 exact chain/port/query/auth/body 및 타chain | 실제 HTTP 컴포넌트 테스트, DB 무호출 검증 |
| Transaction | 생성/접수/finalize/revoke/merge/delete 경합 | ./gradlew mongoIntegrationTest, 격리 replica-set만 사용 |
| schema/tools | index 충돌/기존데이터 경계/manifest·dry-run | 해당 Node migration 테스트, git diff --check |
| 공동 E2E | Billing와 원격 응답유실/TLS/재시작/권리 정책 | 별도 승인 환경의 양측 증거; 오프라인 결과로 대체 금지 |

fixtures-2의 state26개는 각 요구를 실제 구현 테스트에 매핑하는 추적표를 만든다. 신규 테스트 결과는 원본의 미실행 표시를 수정하지 않고 별도 보고한다. S15/V07은 통과/skip으로 위장하지 않고 미확정·미실행으로 유지한다. 정상 지원 범위 전체 완료는 해당 보류가 해소되기 전 선언하지 않는다.

### 최신 상태28개 실행 매핑 — 모두 계획/현재 미실행

| ID | LC 단계·검증 | 별도 담당/경계 |
| --- | --- | --- |
| E01 | P2.1 HTTP decoder/mapper·신규소비요청0 | 실제 소비0은 Billing Tx |
| E02 | P3.1 하위 a~h 전체추적, worker reserve0 | 상위집계, 독립 실행 증거로 중복합산 금지 |
| E02-a | P3.1 같은key·Session·영속대기/restart | Billing purchase-account stable ref |
| E02-b | P3.1 응답유실/status·404비대기 | Billing durable waiting receipt |
| E02-c | P2.1/P3.1 status부작용0/관측 | Billing status 조회만 수행 |
| E02-d | P3.1 replica-set HTTP/worker CAS 양순서 | lease만료·stale쓰기 금지 |
| E02-e | P3.1 cleanup→waiting 안전복귀 | pendingSession/예약/cancelunknown 부정 |
| E02-f | P3.1 NEEDS_REVIEW 인과증거·deny우선 | 원인혼재·이력부재 운영확인 |
| E02-g | P3.1/P3 성공유실/unknowncommit | 양측 권위snapshot 대조 |
| E02-h | P3.1/P6.1 대기창·예산·무TTL | 실제장애격리 회귀 |
| E03 | P3.1 owner/withdrawal deny회귀·토큰비저장 | Billing 늦은증거/Identity fence |
| E04 | P4.1 삭제HTTP/row0·응시중정상삭제 | mock응답이 아닌 저장검사 |
| E05 | P4.1 과거성공404·비재생성 | 금융성공보존 Billing |
| E06 | P2.1/P3 J3 strict조건·owner블록 | malformed·wrongendpoint 부정 |
| T01 | P6.1 −1ms/정각/+1ms | float 없이 Instant/Duration |
| T02 | P6.1 replay/lease/login 첫시각불변 | clock역행 격리자동해제 없음 |
| T03 | P6.1 terminal+D/hold·unknown | 최종purge별도, 안전eligibility검증 |
| T04 | P6.1 operation120일 유지·재시작 replay 보완 테스트 | 원 fixture의7일operation삭제/별도receipt 경로는 공동 설명 보완 전 미실행, source/digest/ownercoverage 유지 |
| T05 | P6.1 폐기후보장제외·남은증거replay | Billingconsumption/복원unique유지 |
| T06 | P6.1 격리DB restore/deny·조기격리 | 실제백업/운영복원은 별도승인 |
| P01 | P2.1 paidphone INITIAL/echo/status | target paid선택 Billing |
| P02 | P2.1 free REPLACEMENT/불명fallback0 | Billing source판정 |
| P03 | P2.1 discovery/reserve변화 조합 | 실제권리경합 공동E2E |
| P04 | P2.1/P3 귀속비이전·응답검증 | C당target복원1회 Billing실Tx |
| P05 | P2.1 원group/echo/mock/source변조거절 | transition예외 분리 |
| C01 | P4.1 공통fence 양순서 | 삭제queue0 |
| C02 | P3/P4 unknown·원완료/삭제·guard | Billingconfirm/refund/복원 Tx, 삭제계약의존 |
| C03 | P3/P4 owner경합·새guard보호 | 원consumptionunique·원claim Billing |

LC mock/component 테스트는 Billing 금융 원자성을 증명하지 않는다. Billing 실제 replica-set 결과와 공동E2E를 별도 증거로 연결한다. baseline81개(80벡터+범위1)/전체1,029는 재현 기준이지 위28개의 통과증거가 아니다. 후속 보고에는 각 ID의 테스트클래스/명령/결과/미실행사유를 기록한다.

### 기준 hash와 보류 목록

- exact snapshot SHA256: `79bcfe3d79225c21d712791266bea10d4aed9eed3dc0529ddf86f7b29673a482`.
- simplified SHA256SUMS SHA256: `35f7d437fb562e1427efe385ffaf1721a60f4780fcaacf495c2a0c7e787c2436`.
- 검증 기록: `LC_BILLING_SIMPLIFIED_FIXTURES_1_VALIDATION.md` 및 기반 `LC_BILLING_PAID_FIXTURES_2_VALIDATION.md`.
- 보류: deletion-route-auth, historical-owner-deletion, final-record-retention-purge와 실환경키/port/TLS·운영이관. 정상phone/120일/삭제미접수 정책 전체를 다시 미정으로 돌리지 않는다.
- 다음 승인 단위: 이 최신 계획의 지원범위 검토 → 필요한 한정수락/ADR·AGENTS 동기화 → 별도제품구현승인. 이번에 ADR/AGENTS를 바꾸거나 실제환경에 적용하지 않는다.

각 단계 종료 보고에는 변경파일/외부계약 유지/실행 명령·건수/실패·미실행/예상 밖 diff/잔여 gate를 기록한다. 실제 credential·JWT·음성·원문 payload를 로그/metric/문서에 추가하지 않는다. 기존 저카디널리티 metric·sanitized 경보를 재사용한다.

## 7. 부록 — 현재 코드와 변경 지점

아래는 2026-10-14 정적 확인이며 승인된 목표 동작과 현행 구현을 구분한다. 경로는 저장소 기준이다.

| 파일/영역 | 현재 확인 사실 | 계획 변경 |
| --- | --- | --- |
| src/main/java/web/tosunsaeng/domain/notification/NotificationSubmissionTracker.java | tracking OFF 시 접수 기록 안 함, 완료 marker/완료 후3일 receipt TTL | P1 알림 독립 evidence, marker와 작업 증거 분리 |
| domain/exams/application/ExamSessionManager.java | prepareForBilling replacement 및 완료count/sequence 선정 | P1 단일mock/startReason 고정·전체접수시 직전 제외 |
| domain/exams/billing/BillingReservationClient.java | reserve3필드/phone6필드, snapshot에 source/transition/거절 없음 | P2 확장·strict validation |
| domain/exams/billing/JwtBillingReservationClient.java 및 reconciliation/ReservationSnapshotValidator.java | 기존 client/복구 검증 경계 | P2 신규snapshot·P3 불명복구 검증 확장 |
| domain/exams/application/BillingExamCreationTransactionService.java | commitReservedSession에서 기존 진행시험 일괄abandon, finalizeConfirmed에서 성공 기록 | P3 원대상 종료를 confirm 뒤 exact atomic 단계로 이동 |
| domain/exams/application/BillingExamCreationSaga.java | 기존 operation replay/reserve/confirm 흐름 | P1/P2/P3 고정snapshot·오류·마무리까지복구 |
| domain/exams/billing/reconciliation/BillingReservationReconciliationService.java | status-first 및 finalizeConfirmed 호출 | P3 공통마무리·guard/TTL/terminal증거 |
| domain/exams/application/ExamGradingService.java | 접수 tracker 호출과 abandoned Job 거절 경계 | P4 사용자신규와기접수/시스템Job 구분 |
| domain/exams/attemptgroup/application/* | evidence/coordinator/summary/reconciler | P4 원group만 완료·신규guard보호, outbox불변 |
| ExamSession/ExamCreationOperation/Repository·삭제/merge/보안 설정 | 공통 영속/owner/index 경계 | P0 schema 검토·P3/P5/P6 연계 |

첫 행 이후 `domain/...` 경로 prefix는 `src/main/java/web/tosunsaeng/`다. 신규 receiver·schema의 exact class/collection 이름은 P0에서 충돌 검토 후 정하며 존재하는 구현인 것처럼 기재하지 않는다.

참조 문서:

- `docs/codex/LC_BILLING_PAID_FIXTURES_2_VALIDATION.md` —89/948 검증·미실행 범위와 원본 hash.
- `docs/codex/LC_BILLING_PAID_SIMPLIFICATION_REVIEW.md` §9/10 —통합R2 검토·담당/미확정.
- `docs/codex/BILLING_RESERVATION_RECONCILIATION_IMPLEMENTATION_PLAN.md`, `PHONE_REJOIN_CONTINUATION_IMPLEMENTATION_PLAN.md`, `LEARNING_RECORD_DELETION_IMPLEMENTATION_PLAN.md`, `USER_MERGED_CONSUMER_IMPLEMENTATION_PLAN.md` —기존 lifecycle/복구 경계, 새정책과 충돌 시 조용히 덮어쓰지 않고 동기화.
- Billing 원문: `/Users/msde76/billing/docs/contracts/LC-BILLING-PAID-EXAM-CONTRACT-R2.md` —exact wire/오류·보안·보존 gate. 기존 A 인증 계약은 변경하지 않음.

이번 작업은 본 계획 및 WORKLOG/CURRENT_STATE만 변경했다. 테스트/제품/fixture/ADR/AGENTS/타저장소/DB/배포 변경이나 외부 전송은 없다. 테스트 재실행은 하지 않았고 정적 대조·git diff --check로 문서를 확인한다.
