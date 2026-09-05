# 1차 업데이트 진행 체크리스트

- 기준일: 2026-09-05
- 기준 범위: SNS 로그인, 검증된 전화번호당 무료 모의고사 1회, 1·3·7·14·30일 비자동갱신 무제한 이용권, 10초 챌린지
- 판정 근거: Identity·Billing·Learning Core 현재 코드, 저장소 CURRENT_STATE/WORKLOG, 병합·테스트 기록
- Jira 주의: 이번 점검은 세 저장소의 현재 `develop`, 작업 트리와 최근 Jira 재조회 기록을 기준으로 한다. Challenge `TMI-102`·`TMI-106`은 별도 실시간 재조회 없이 저장소의 최근 상태 기록을 사용한다.

표시 기준:

- ✅ 완료: 코드 병합·테스트 완료 또는 구현 기준 계약 승인 완료
- 🟡 진행/부분 완료: 일부 코드가 있지만 production 연결·활성화·검증이 남음
- ⬜ 미착수: 현재 대상 저장소에 구현 코드가 없음
- 🚫 출시 차단: 해소 전 1차 production release를 열면 안 되는 항목

## 0. 전체 판정

현재 1차 업데이트는 **Identity lifecycle, 무료시험 생성·채점 상태·phone 재가입 continuation과 UserMerged ownership migration까지 핵심 서버 코드가 구현됐지만, 새로 확정한 기간제 이용권은 아직 전용 runtime 구현이 없고 실제 workload·모바일 종단 검증도 남은 상태**다. production 출시 가능 단계는 아니다.

| 영역 | 현재 판정 | 핵심 잔여 작업 |
| --- | --- | --- |
| Identity·SNS | ✅ lifecycle·Stage 7 fan-out 구현 / 🟡 rollout 전 | 모바일·replica set·workload staging E2E |
| 무료 모의고사 1회 | ✅ 생성·상태·phone continuation·owner migration 코드 / 🟡 통합 전 | 실제 Lattice·replica-set·cross-service E2E |
| 기간제 무제한 이용권 | ⬜ 전용 runtime 미구현 | Billing 결제·검증·권리 원장과 공개 API, Learning Core evaluator, 모바일 구매·복원 |
| Learning Core 기존 시험 | ✅ Billing saga·AttemptGroup publisher·continuation·UserMerged / 🟡 feature off | 코드 보완 3건, migration·Lattice·failure-injection E2E |
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
- ✅ Billing SigV4와 owner event durable fan-out — `TMI-123`
  - Identity PR #38 merge commit `fa9843e`와 후속 구현 commit `1110b8a`가 `develop`에 병합됨
  - phone eligibility SigV4 전송, `UserMerged` consumer별 독립 delivery와 `TrialOwnerRebindApproved` Billing-only delivery 구현
  - 전체 630개 테스트 성공 기록, Jira 상태·Resolution 완료
  - capture·publisher flag는 production 기본 비활성 유지
- ✅ Learning Core `UserMerged` consumer와 source actor deny — `TMI-125`
  - PR #28 merge commit `8c8208b`가 `develop`에 병합됨
  - source/target ownership migration, source deny, workload JWT와 Mongo Transaction 구현
  - Java 483개·Node migration 6개 테스트 성공 기록, Jira 상태·Resolution 완료
  - Docker daemon 부재로 replica-set `mongoIntegrationTest` 4개 실행 증거는 아직 없음
- 🚫 실제 모바일 Google·Apple·Phone 로그인/link 및 staging E2E
- 🚫 Kakao를 1차에 포함한다면 Identity Platform/OIDC·deep-link staging PoC
- 🚫 실제 Mongo replica set·multi-instance worker·workload JWT key overlap 검증
- 🚫 withdrawal publisher/backfill과 Guest merge production feature flag 활성화 검증

판정: Identity 서버의 SNS·탈퇴·가입 중단 cleanup과 Stage 7 owner event fan-out, Learning Core UserMerged consumer까지 구현됐다. 이 영역의 남은 일은 새 대형 도메인 기능보다 **실제 Firebase/mobile·replica set·workload staging E2E와 안전한 feature flag rollout**이다.

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
- ✅ Learning Core AttemptGroup durable outbox/publisher — `TMI-118`
  - PR #25와 Summary Transaction hotfix PR #26이 Learning Core `develop`에 병합됨
  - GRADING·COMPLETED·RETAKE_AVAILABLE 판정, Session+outbox Transaction, lease/retry/dead-letter/auth circuit 구현
  - Summary Transaction 전체 단위 재시도 보완 후 전체 444개 테스트 성공 기록
  - Jira 상태 완료
- 🟡 Billing retained trial owner rebind·phone continuation — `TMI-120`
  - owner rebind와 continuation 보완 PR #6·#7·#8이 Billing `develop`에 병합됨
  - OPEN·RETAKE_AVAILABLE owner CAS, GRADING pending, COMPLETED NOOP와 phone continuation discovery 구현
  - 비-Docker 테스트와 집중 테스트는 통과했지만 Docker daemon 미가동으로 replica-set Testcontainers 4개는 아직 실행 증거가 없음
  - Jira는 최근 기록 기준 `해야 할 일`, production flag 기본 off
- ✅ Learning Core phone 재가입 시험 continuation — `TMI-122`
  - PR #27이 Learning Core `develop`에 병합됨
  - target Session 0건일 때 Billing discovery 204/200, PHONE_REJOIN REPLACEMENT와 same group/mock 연결 구현
  - source Session·답안·결과는 이전하지 않고 target의 새 examId로 처음부터 재시작
  - 전체 457개 테스트 성공 기록, feature flag 기본 off
- 🟡 Reservation expiry worker
  - 구현은 완료됐지만 기본 비활성
  - production schedule·metric·alert·운영값 검증 필요
- 🟡 AttemptGroup과 R3 무료 replacement
  - Learning Core publisher와 Billing consumer 양쪽 코드가 구현됨
  - feature flag·실제 Lattice·multi-instance·failure-injection staging E2E는 미완료
- 🟡 Billing 탈퇴·재가입 owner rebind
  - Billing `TMI-120`과 Identity `TMI-123` 양쪽 코드는 병합됨
  - Billing replica-set Testcontainers와 cross-service staging 검증이 남음
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

판정: Billing의 **TrialClaim·무료 grant·Reservation·AttemptGroup consumer·owner rebind와 Learning Core 시험 생성·상태 publisher·phone continuation·UserMerged까지 핵심 코드가 구현됐다.** 다만 관련 flag가 기본 off이고 실제 Lattice·migration·replica-set 및 staging E2E가 남아 production 종단 흐름은 아직 닫히지 않았다.

## 3. Learning Core 기존 시험 기반

- ✅ Identity JWT 사용자 식별과 시험 소유권 검증
- ✅ 시험 Session과 시험지 배정
- ✅ S3 음성 업로드 URL과 제출 흐름
- ✅ 비동기 AI 문항·요약 채점
- ✅ Callback·Job 멱등성
- ✅ 결과·Polling·이력·재답변·시험 단위 채점 복구
- ✅ 탈퇴 사용자 local deny marker consumer/gate — `TMI-109`
- ✅ `UserMerged` 학습 데이터 consumer와 source deny — `TMI-125`
- ✅ Billing Reservation client와 시험 생성 saga — `TMI-116`
- ✅ feature flag on의 필수 `Idempotency-Key`와 동일 operation replay — `TMI-116`
- ✅ AttemptGroup 상태 durable outbox/publisher — `TMI-118`
- ✅ R3 replacement 코드 연결 — `TMI-118`
  - 최초 응시에서 1회만 차감하고 완료할 때까지 같은 consumption·group·mock으로 새 Session을 처음부터 시작
  - `GRADING`은 기존 복구를 우선하고 최종 실패의 `RETAKE_AVAILABLE`에서 replacement 허용
- ✅ phone 재가입 continuation — `TMI-122`
  - Billing `TMI-120` discovery와 exact context를 사용해 target 새 Session을 기존 미완료 group에 연결
- ⬜ Billing 장애 reconciliation
- 🟡 production 안전성 후속 코드 3건
  - staging deploy workflow에 `mongoIntegrationTest` required gate 추가
  - UserMerged migration preflight에 orphan Result/Summary와 Session-owner 불일치 검증 추가
  - phone continuation discovery의 `attemptGroupId`를 lowercase UUID v4로 strict 검증
- ⬜ Challenge domain·API·AI Job

판정: 기존 모의고사, Billing Reservation 시험 생성, 채점 상태 publisher, phone continuation과 UserMerged ownership migration까지 코드상 연결됐다. 남은 기능성 코드는 **production 안전성 후속 3건과 Billing 장애 reconciliation**이며, feature flag 활성화 전 Mongo migration·실제 Lattice·replica-set과 failure-injection E2E가 필요하다.

## 4. 5종 기간제 무제한 이용권

- ✅ 제품 범위와 핵심 정책 확정
  - `UNLIMITED_1D`, `UNLIMITED_3D`, `UNLIMITED_7D`, `UNLIMITED_14D`, `UNLIMITED_30D`
  - 자동 갱신 없음, Billing 검증 `CAPTURED` 시점부터 24·72·168·336·720시간
  - 활성 기간 중 새 AttemptGroup 무제한, 만료 전에 연 AttemptGroup은 완료 전 replacement 보장
  - credit과 첫 구매 2배는 제품에서 완전히 제거
- ⬜ 기간제 이용권 전용 Jira와 서비스 간 최종 wire/API 계약
- ⬜ Billing 5종 상품 catalog와 환경별 Apple·Google product ID 매핑
- ⬜ 앱 공개 상품 조회·구매 제출·구매 상태·현재 이용권·복원 API
- ⬜ Apple App Store·Google Play server-side 거래 검증 adapter
- ⬜ order·payment·store transaction 원장과 command/transaction 멱등성
- ⬜ `FixedTermEntitlement` 활성·만료·회수와 usage audit
- ⬜ Apple Server Notifications·Google RTDN durable inbox
- ⬜ refund·revoke·chargeback 처리
- ⬜ 결제·entitlement reconciliation worker와 운영 runbook
- ⬜ Billing `UserMerged` owner rebind의 결제 원장/기간제 권리 확장
- ⬜ Learning Core Reservation entitlement evaluator의 기간제 이용권 지원
- ⬜ 모바일 StoreKit 2·Google Play Billing 구매·복원·pending/환불 UX
- 🚫 sandbox 결제·복원·환불·알림 역순/중복·응답 유실 staging E2E

판정: 10초 챌린지를 제외해도 가장 큰 신규 기능 개발이다. 무료시험 Billing 기반을 재사용할 수는 있지만 **스토어 결제 검증부터 기간 권리 원장, 공개 API, 모바일 구매/복원까지 별도 vertical slice를 새로 구현**해야 한다. 현재 거친 추정은 backend 1명+mobile 1명 병렬 기준 약 4~6주이며 Store 심사 대기는 별도다.

## 5. 10초 챌린지

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

## 6. 배포·서비스 간 통합

- 🟡 Identity → Learning Core withdrawal event
  - producer와 consumer 코드는 양쪽 모두 구현
  - 실제 workload credential·HTTPS route·key rotation·backfill staging E2E는 미완료
- 🟡 Identity → Billing eligibility event
  - publisher·SigV4 transport와 consumer 코드는 구현·병합
  - 실제 route·role·negative E2E는 미완료
- 🟡 Identity → Learning Core UserMerged event — `TMI-123`, `TMI-125`
  - producer durable fan-out과 consumer·ownership migration 코드는 모두 구현·병합
  - 실제 workload issuer/JWKS·retry·replica-set·성능 E2E는 미완료
- 🟡 Learning Core → Billing Reservation 호출 — `TMI-116`
  - SigV4 client와 saga 코드는 구현·병합
  - 실제 Lattice/IAM/SG·Mongo migration·staging E2E와 feature flag 활성화는 미완료
- 🟡 Learning Core → Billing AttemptGroup 상태 event — `TMI-117`, `TMI-118`
  - Billing consumer와 Learning Core outbox/publisher 모두 구현·병합
  - 실제 Lattice, consumer/publisher flag 순차 활성화와 상태·오류 E2E는 미완료
- 🟡 Identity → Billing owner rebind event — `TMI-120`, `TMI-123`
  - Billing consumer와 Identity durable fan-out 모두 구현·병합
  - 실제 workload route·replica-set·stale/duplicate E2E는 미완료
- 🟡 Billing ↔ Learning Core phone continuation — `TMI-120`, `TMI-122`
  - 양쪽 코드가 `develop`에 병합됨
  - Billing replica-set Testcontainers 4개와 Lattice exact route·stale epoch·응답 유실 staging E2E가 남음
- ⬜ 무료시험 전체 흐름 E2E
- ⬜ 기간제 이용권 구매→검증→활성화→시험→복원/환불 전체 흐름 구현·E2E
- ⬜ Challenge 전체 흐름 E2E
- ⬜ staging 환경별 Mongo·Redis·S3·Firebase·credential 분리 확인
- ⬜ 실제 Mongo replica set Transaction·multi-instance lease·unique race 검증
- ⬜ response loss·timeout·retry·dead-letter·manual replay·rollback 검증
- ⬜ production canary와 단계별 feature flag 활성화
- 🚫 1차 업데이트 production release

## 7. 출시 차단 조건

다음 조건이 모두 해소되기 전에는 production release를 열지 않는다.

- 🚫 모바일 SNS 로그인·phone link와 Identity staging E2E
- 🚫 Learning Core Billing saga의 실제 Lattice·migration·무료시험 cross-service E2E
- 🚫 기간제 이용권의 Billing·Learning Core·모바일 runtime 구현과 Store sandbox E2E
- 🚫 Learning Core UserMerged replica-set 테스트·migration·workload·성능 E2E
- 🚫 Billing `TMI-120` replica-set 테스트와 owner rebind·phone continuation cross-service E2E
- 🚫 Challenge backend·AI 양방향 구현과 모바일 E2E
- 🚫 workload 인증, 환경 격리, 실제 replica set·multi-instance 검증
- 🚫 response loss·rollback·dead-letter/replay runbook과 canary 검증

## 8. 가장 우선적인 다음 순서

1. 5종 기간제 이용권의 Billing·Learning Core·모바일 계약을 고정하고 전용 Jira를 서비스별 vertical slice로 만든다.
2. Billing 상품·거래 검증·payment/entitlement 원장·공개 API·notification/reconciliation을 구현한다.
3. Learning Core entitlement evaluator와 모바일 StoreKit 2·Google Play 구매/복원을 연결한다.
4. Learning Core production 안전성 후속 코드 3건을 보완한다.
5. Billing `TMI-120`과 Learning Core `TMI-125`의 replica-set 테스트를 Docker 환경에서 통과시키고 Jira·배포 gate를 정리한다.
6. `TMI-116`·`TMI-118`·`TMI-120`·`TMI-122`·`TMI-125`의 Mongo migration, Lattice/IAM/SG와 failure-injection staging E2E를 통과한다.
7. Challenge를 같은 release에 유지한다면 별도 backend Jira와 catalog/attempt/API/AI Job을 구현한다.
8. 모바일 SNS·무료시험·기간제 이용권과 선택한 Challenge 종단 E2E를 통과한 뒤 canary와 단계별 feature flag rollout을 수행한다.

## 한 줄 요약

10초 챌린지를 제외해도 **5종 기간제 무제한 이용권은 대규모 신규 기능 개발이 필요**하다. SNS·무료시험·owner lifecycle의 큰 서버 기능은 구현됐지만 production 안전성 후속 코드 3건과 실제 Lattice·replica-set·mobile/staging 종단 검증도 남아 있다.

프론트 API 상세는 [`docs/contracts/FRONTEND_API_HANDOFF.md`](../contracts/FRONTEND_API_HANDOFF.md), Challenge API v1은 [`docs/contracts/ten-second-challenge-frontend-api.md`](../contracts/ten-second-challenge-frontend-api.md)를 따른다.
