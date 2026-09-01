# 1차 업데이트 진행 체크리스트

- 기준일: 2026-08-31
- 기준 범위: SNS 로그인, 검증된 전화번호당 무료 모의고사 1회, 10초 챌린지
- 판정 근거: Identity·Billing·Learning Core 현재 코드, 저장소 CURRENT_STATE/WORKLOG, 병합·테스트 기록
- Jira 주의: 이번 점검은 세 저장소의 현재 `develop`, 코드와 최근 Jira 재조회 기록을 기준으로 한다. Challenge `TMI-102`·`TMI-106`은 별도 실시간 재조회 없이 저장소의 최근 상태 기록을 사용한다.

표시 기준:

- ✅ 완료: 코드 병합·테스트 완료 또는 구현 기준 계약 승인 완료
- 🟡 진행/부분 완료: 일부 코드가 있지만 production 연결·활성화·검증이 남음
- ⬜ 미착수: 현재 대상 저장소에 구현 코드가 없음
- 🚫 출시 차단: 해소 전 1차 production release를 열면 안 되는 항목

## 0. 전체 판정

현재 1차 업데이트는 **Identity와 무료시험 핵심 서버 코드는 대부분 구현됐지만, 실제 workload 경로와 상태 event·모바일 종단 흐름이 아직 완성되지 않은 상태**다. production 출시 가능 단계는 아니다.

| 영역 | 현재 판정 | 핵심 잔여 작업 |
| --- | --- | --- |
| Identity·SNS | ✅ 서버 lifecycle 완료 / 🟡 통합 전 | 모바일·SigV4 workload·replica set staging E2E, feature flag rollout |
| 무료 모의고사 1회 | ✅ 원장·Reservation·시험 생성 saga / 🟡 상태 연동 전 | Learning Core AttemptGroup publisher, owner rebind, 실제 Lattice·E2E |
| Learning Core 기존 시험 | ✅ 시험·Billing saga 구현 / 🟡 feature off | migration·Lattice·failure-injection E2E, UserMerged 연동 |
| 10초 챌린지 | 🟡 v1 계약·콘텐츠 준비 | Learning Core backend와 양방향 AI 구현 |
| 배포·통합 | 🚫 미완료 | 환경 격리, response-loss·multi-instance·rollback E2E, canary |

## 1. Identity / SNS 로그인

- ✅ Firebase 인증 broker 기반 — `TMI-90`~`TMI-91`
- ✅ PhoneIdentity와 fingerprint 기반 — `TMI-92`
- ✅ Firebase MEMBER signup — `TMI-94`
- ✅ Phone eligibility 계약·publisher — `TMI-95`~`TMI-96`
- ✅ Guest → MEMBER 승격·merge — `TMI-97`~`TMI-98`
- ✅ 탈퇴 lifecycle·외부 cleanup·identity release — `TMI-103`, `TMI-104`, `TMI-107`
- ✅ 탈퇴 Session 전용 오류 — `TMI-108`
- ✅ Learning Core Access Token 차단 consumer — `TMI-109`
  - PR #23이 `develop`에 병합됨
  - consumer/gate 분리 flag, marker race 409 수렴, startup Transaction probe 포함
  - 전체 402개 테스트 성공 기록
  - Jira 상태·Resolution 완료
- ✅ Identity UserWithdrawn outbox·publisher·bounded backfill — `TMI-111`
  - PR #35가 `develop`에 병합됨
  - withdrawal Transaction과 outbox 원자 저장, lease·retry·dead-letter·replay 포함
  - workload RS256 JWT와 JWKS rotation, dry-run 우선 backfill 포함
  - 109개 suite·591개 테스트 성공 기록
  - Jira 상태·Resolution 완료
- ✅ Jira dependency는 `TMI-109 blocks TMI-111`로 유지
- ✅ 가입 중단 Firebase User cleanup lifecycle — `TMI-114`
  - PR #36이 Identity `develop`에 병합됨
  - enrollment 상태·lease/CAS·owner preflight·Firebase disable/revoke/delete·bounded capture 구현
  - 전체 113 suite·600개 테스트 성공 기록
  - Jira 상태·Resolution 완료
  - worker는 production 기본 비활성으로 staging 검증 전 활성화 금지
- ⬜ Learning Core `UserMerged` consumer와 source actor deny 처리
- 🚫 실제 모바일 Google·Apple·Phone 로그인/link 및 staging E2E
- 🚫 Kakao를 1차에 포함한다면 Identity Platform/OIDC·deep-link staging PoC
- 🚫 실제 Mongo replica set·multi-instance worker·workload JWT key overlap 검증
- 🚫 withdrawal publisher/backfill과 Guest merge production feature flag 활성화 검증

판정: Identity 서버의 SNS·탈퇴·가입 중단 cleanup lifecycle 코드는 구현됐다. 현재 병목은 신규 domain 코드보다 **실제 Firebase/mobile, Identity→Billing SigV4 transport, replica set staging E2E와 안전한 feature flag rollout**이다.

## 2. 검증된 전화번호당 무료 모의고사 1회

- ✅ 무료 혜택 소유 서비스는 최소 Billing/Entitlement로 확정
- ✅ Identity eligibility publisher 구현 — `TMI-95`~`TMI-96`
- ✅ Billing eligibility consumer — `TMI-110`
  - event inbox·revision high-water·current binding Mongo Transaction
  - 전체 33개 테스트 성공 기록
- ✅ `TrialClaim` unique 원장과 `FREE_EXAM_ONCE` grant/ledger — `TMI-112`
  - 첫 reserve Transaction에서 eligibility·candidate dedupe·claim·grant·ledger·allocation·Reservation을 함께 반영
  - 같은 operation replay와 동시 reserve 수렴 구현
  - 구현 당시 전체 58개 테스트 성공 기록
- ✅ Billing Reservation reserve — `TMI-112`
- ✅ Billing confirm/cancel/status와 만료 lifecycle — `TMI-113`
  - INITIAL/REPLACEMENT confirm·cancel과 expiry CAS/Transaction
  - response-loss 확인용 read-only status endpoint
  - package 개편 후 전체 82개 테스트 성공 기록
- ✅ Billing BenefitDefinition foundation — `TMI-115`
  - PR #3이 Billing `develop`에 병합됨
  - `FREE_EXAM_ONCE` policy catalog와 schema v3 startup/index 기준 구현
- ✅ Learning Core 시험 생성 Billing Reservation saga — `TMI-116`
  - PR #24가 Learning Core `develop`에 병합됨
  - Billing reserve → Mongo Session durable commit → confirm과 same-operation replay 구현
  - feature flag on에서 lowercase UUID v4 `Idempotency-Key` 필수 검증
  - confirm/status/cancel 응답 유실, Mongo unknown outcome과 P1/P2 보완 포함
  - 전체 432개 테스트 성공 기록
  - Jira는 최근 기록 기준 상태 전환 전이며, 실제 Lattice·staging gate가 남음
- ✅ Billing AttemptGroup 상태 event consumer — `TMI-117`
  - PR #4가 Billing `develop`에 병합됨
  - active Session fencing, 단방향 상태 전이, inbox·Transaction·CAS와 204/409/422/503 계약 구현
  - 전체 137개 테스트 성공 기록
  - Jira 상태·Resolution 완료
- 🟡 Reservation expiry worker
  - 구현은 완료됐지만 기본 비활성
  - production schedule·metric·alert·운영값 검증 필요
- 🟡 AttemptGroup과 R3 무료 replacement
  - Billing consumer와 AttemptGroup·AttemptSession 상태 전이는 구현됨
  - Learning Core의 durable outbox/publisher가 없어 실제 `GRADING → COMPLETED/RETAKE_AVAILABLE` event는 아직 전달되지 않음
- ⬜ Billing 탈퇴·재가입 owner rebind
- ⬜ Billing 자동 repair/reconciliation과 운영 route
- 🟡 실제 VPC Lattice·SigV4·IAM/SG 연결
  - Learning Core SigV4 client 코드는 구현됐지만 실제 route·role·policy·SG 검증은 미완료
- 🟡 `POST /api/v1/exams` lowercase UUID v4 `Idempotency-Key`
  - `TMI-116` feature flag on에서 필수 처리 구현
  - flag off에서는 기존 무헤더 흐름을 유지하며 프론트 header 선배포가 필요
- 🟡 `reserve → ExamSession durable commit → confirm` saga
  - 코드와 회귀 테스트는 완료
  - feature flag 기본 off, 실제 migration·replica set·Lattice·INITIAL/REPLACEMENT E2E 미완료
- 🟡 reserve/confirm/cancel 응답 유실 복구
  - same-key replay와 status/cancel 수렴은 구현
  - 장기 background reconciliation과 실제 failure injection은 후속
- 🚫 같은 전화번호·다른 계정, 동시 시험 시작, Session commit 실패, confirm 응답 유실 cross-service E2E

판정: Billing의 **TrialClaim·무료 grant·Reservation·AttemptGroup consumer와 Learning Core 시험 생성 saga까지 핵심 코드가 구현됐다.** 다만 기능은 기본 off이고 실제 Lattice·migration·staging E2E와 Learning Core 상태 publisher가 없어 production 종단 흐름은 아직 닫히지 않았다.

## 3. Learning Core 기존 시험 기반

- ✅ Identity JWT 사용자 식별과 시험 소유권 검증
- ✅ 시험 Session과 시험지 배정
- ✅ S3 음성 업로드 URL과 제출 흐름
- ✅ 비동기 AI 문항·요약 채점
- ✅ Callback·Job 멱등성
- ✅ 결과·Polling·이력·재답변·시험 단위 채점 복구
- ✅ 탈퇴 사용자 local deny marker consumer/gate — `TMI-109`
- ⬜ `UserMerged` 학습 데이터 consumer
- ✅ Billing Reservation client와 시험 생성 saga — `TMI-116`
- ✅ feature flag on의 필수 `Idempotency-Key`와 동일 operation replay — `TMI-116`
- ⬜ AttemptGroup 상태 durable outbox/publisher
- 🟡 R3 replacement 연결
  - Billing 수신 상태 전이는 구현됐지만 Learning Core event 발행이 없어 종단 미완성
- ⬜ Billing 장애 reconciliation
- ⬜ Challenge domain·API·AI Job

판정: 기존 모의고사와 Billing Reservation 시험 생성 경계는 코드상 연결됐다. 이제 **feature flag 활성화 전 Mongo migration·실제 Lattice와 failure-injection E2E**, 그리고 채점 결과를 Billing AttemptGroup으로 보내는 publisher가 필요하다.

## 4. 10초 챌린지

- ✅ 제품·프론트 v1 계약 승인
  - KST 기준 전 사용자 공통 일 3문제
  - 한국어 prompt → 영어 발화
  - 1→2→3 순차 진행, 문제당 1회
  - 앱 최대 10초 녹음
  - attempt 생성 후 1시간 submission deadline
  - 날짜 rollover 보호와 비순환 dayNumber
  - MEMBER 전용
- ✅ 오디오·AI v1 계약 승인
  - M4A/AAC-LC, `audio/mp4`
  - 16/44.1/48 kHz, mono/stereo, 최대 2 MiB
  - Learning Core → AI multipart와 전용 Callback
  - `attemptId + jobId + gradingAttempt` fencing
  - transcript·verdict·correctedAnswer·meaning/grammar/pronunciation feedback
  - no-speech·timeout·최대 3 generation·최종 실패 projection
- ✅ 문제 콘텐츠와 생성 Jira — `TMI-105`
  - `challenge_10s_questions`에 dayNumber별 세 문제 구조 존재
- 🟡 UI — `TMI-102`
  - 저장소의 최근 확인 기록 기준 진행 중
- 🟡 채점 agent — `TMI-106`
  - 저장소의 최근 확인 기록 기준 진행 중
- ⬜ Learning Core Challenge backend Jira
  - 저장소 AGENTS 범위 제한은 해소돼 구현 자체는 허용됨
- ⬜ `ChallengeCatalogState`, content resolver/validator
- ⬜ `ChallengeAttempt`, grading job, inbox/outbox domain
- ⬜ 오늘 진행도·문제·attempt·upload-url·answer·result·history API 7개
- ⬜ Challenge 전용 AI request dispatch·Callback consumer 구현
- ⬜ 중복 submit·응답 유실·timeout·stale/late Callback 멱등성 구현
- ⬜ production index·catalog initializer·content exhaustion 운영 검증
- 🚫 모바일 countdown·background·자정·앱 재실행·60초 polling E2E

판정: 이전에 미확정이던 sample rate·channel·최대 크기·AI 결과 DTO·retry·rollover는 이제 v1 계약으로 확정됐다. 현재 핵심 공백은 **Learning Core Challenge backend 전체와 실제 AI 양방향 구현**이다.

## 5. 배포·서비스 간 통합

- 🟡 Identity → Learning Core withdrawal event
  - producer와 consumer 코드는 양쪽 모두 구현
  - 실제 workload credential·HTTPS route·key rotation·backfill staging E2E는 미완료
- 🟡 Identity → Billing eligibility event
  - publisher와 consumer 코드는 구현
  - Identity publisher의 실제 SigV4/Lattice transport 보정과 route·role 검증은 미완료
- 🟡 Learning Core → Billing Reservation 호출 — `TMI-116`
  - SigV4 client와 saga 코드는 구현·병합
  - 실제 Lattice/IAM/SG·Mongo migration·staging E2E와 feature flag 활성화는 미완료
- 🟡 Learning Core → Billing AttemptGroup 상태 event — `TMI-117` 후속
  - Billing consumer는 구현·병합·Jira 완료
  - Learning Core outbox/publisher PLAN·Jira·구현은 없음
- ⬜ 무료시험 전체 흐름 E2E
- ⬜ Challenge 전체 흐름 E2E
- ⬜ staging 환경별 Mongo·Redis·S3·Firebase·credential 분리 확인
- ⬜ 실제 Mongo replica set Transaction·multi-instance lease·unique race 검증
- ⬜ response loss·timeout·retry·dead-letter·manual replay·rollback 검증
- ⬜ production canary와 단계별 feature flag 활성화
- 🚫 1차 업데이트 production release

## 6. 출시 차단 조건

다음 조건이 모두 해소되기 전에는 production release를 열지 않는다.

- 🚫 모바일 SNS 로그인·phone link와 Identity staging E2E
- 🚫 Learning Core Billing saga의 실제 Lattice·migration·무료시험 cross-service E2E
- 🚫 Learning Core AttemptGroup outbox/publisher와 Billing owner rebind 구현
- 🚫 Challenge backend·AI 양방향 구현과 모바일 E2E
- 🚫 workload 인증, 환경 격리, 실제 replica set·multi-instance 검증
- 🚫 response loss·rollback·dead-letter/replay runbook과 canary 검증

## 7. 가장 우선적인 다음 순서

1. Learning Core AttemptGroup durable outbox/publisher의 PLAN·신규 Jira·명시적 범위를 확정하고 구현한다.
2. Billing 탈퇴·재가입 retained subject owner rebind를 계획·구현한다.
3. Identity→Billing eligibility publisher를 SigV4/Lattice transport로 정렬하고 `TMI-110` staging E2E를 수행한다.
4. `TMI-116` Mongo migration, Lattice/IAM/SG와 reserve/commit/confirm failure-injection E2E를 통과한다.
5. Learning Core `UserMerged` consumer를 구현한다.
6. Challenge backend Jira를 만들고 catalog/attempt/API/AI Job을 구현한다.
7. 모바일 SNS·무료시험·Challenge 종단 E2E와 multi-instance·response-loss·rollback을 통과한다.
8. canary 후 consumer → publisher → 사용자 feature 순서로 production flag를 활성화한다.

## 한 줄 요약

현재는 **Identity lifecycle, Billing 무료권·Reservation·AttemptGroup consumer, Learning Core 시험 생성 saga까지 핵심 코드가 구현됐고, Learning Core 상태 publisher·owner rebind·Challenge backend·실제 Lattice/mobile/staging 종단 검증이 남은 상태**다.

프론트 API 상세는 [`docs/contracts/FRONTEND_API_HANDOFF.md`](../contracts/FRONTEND_API_HANDOFF.md), Challenge API v1은 [`docs/contracts/ten-second-challenge-frontend-api.md`](../contracts/ten-second-challenge-frontend-api.md)를 따른다.
