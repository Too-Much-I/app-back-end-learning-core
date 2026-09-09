# 1차 업데이트 진행 체크리스트

- 기준일: 2026-09-08
- 기준 범위: SNS 로그인, 검증된 전화번호당 무료 모의고사 1회, 1·3·7·14·28일 비자동갱신 무제한 이용권, 10초 챌린지
- 판정 근거: Identity·Billing·Learning Core 현재 코드, 저장소 CURRENT_STATE/WORKLOG, 병합·테스트 기록
- Jira 주의: 이번 점검은 세 저장소의 현재 `develop`, 작업 트리와 최근 Jira 재조회 기록을 기준으로 한다. Challenge `TMI-102`·`TMI-106`은 별도 실시간 재조회 없이 저장소의 최근 상태 기록을 사용한다.

표시 기준:

- ✅ 완료: 코드 병합·테스트 완료 또는 구현 기준 계약 승인 완료
- 🟡 진행/부분 완료: 일부 코드가 있지만 production 연결·활성화·검증이 남음
- ⬜ 미착수: 현재 대상 저장소에 구현 코드가 없음
- 🚫 출시 차단: 해소 전 1차 production release를 열면 안 되는 항목

## 0. 전체 판정

현재 1차 업데이트는 **Identity lifecycle·`account_type`, 무료시험 전체 서버 기반·공개 조회 API, 기존 시험과 10초 챌린지 Learning Core backend까지 구현됐지만, 기간제 결제 runtime과 실제 workload·AI·모바일 종단 검증이 남은 상태**다. production 출시 가능 단계는 아니다.

| 영역 | 현재 판정 | 핵심 잔여 작업 |
| --- | --- | --- |
| Identity·SNS | ✅ lifecycle·fan-out·`account_type` 구현 / 🟡 rollout 전 | Billing reader용 audience/scope, 모바일·workload staging E2E |
| 무료 모의고사 1회 | ✅ 생성·상태·continuation·owner migration·공개 조회 / 🟡 통합 전 | Identity reader token, 실제 Lattice·ALB·cross-service E2E |
| 기간제 무제한 이용권 | ✅ 제품·RevenueCat 계약 / ⬜ runtime 미구현 | Billing 결제·권리 원장/API, Learning Core evaluator, 모바일 구매·복원 |
| Learning Core 기존 시험 | ✅ Billing saga·AttemptGroup·continuation·UserMerged·안전성 보완 / 🟡 feature off | migration·Lattice·failure-injection E2E |
| 10초 챌린지 | ✅ Learning Core backend·격리 통합 테스트 / 🟡 rollout 전 | 실제 AI·S3/IAM·catalog·모바일 E2E |
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
  - PR #28과 production safety 후속 PR #29가 `develop`에 병합됨
  - source/target ownership migration, source deny, workload JWT와 Mongo Transaction 구현
  - UUID strict decode, orphan/owner mismatch preflight, unknown commit 수렴과 CI gate 보완 완료
  - 최신 전체 검증에서 일반 Java 529개·Mongo replica-set 40개·Node migration 90개 성공
  - Jira 상태·Resolution 완료
- ✅ 사용자 Access Token `account_type=MEMBER|GUEST`
  - Identity PR #39 merge commit `fe9c7f6`이 `develop`에 병합됨
  - 로그인·교환·refresh·upgrade·merge 등 사용자 발급 경로에 현재 계정 유형 반영
  - Identity 전체 640개 테스트 성공 기록
- ⬜ Billing public reader용 사용자 JWT audience·`billing:read` scope 확장
  - 계획서는 작성됐지만 Jira·runtime 구현은 아직 없음
  - 기존 `account_type` 병합만으로 Billing reader 인증이 완료된 것은 아님
- 🚫 실제 모바일 Google·Apple·Phone 로그인/link 및 staging E2E
- 🚫 Kakao를 1차에 포함한다면 Identity Platform/OIDC·deep-link staging PoC
- 🚫 실제 Mongo replica set·multi-instance worker·workload JWT key overlap 검증
- 🚫 withdrawal publisher/backfill과 Guest merge production feature flag 활성화 검증

판정: Identity 서버의 SNS·탈퇴·가입 중단 cleanup, Stage 7 owner event fan-out과 Challenge용 `account_type` 발급까지 구현됐다. 신규 기능성 공백은 **Billing 무료 사용권 조회용 audience·scope 발급**이며, 이후 실제 Firebase/mobile·workload staging E2E와 안전한 rollout이 필요하다.

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
- ✅ Billing retained trial owner rebind·phone continuation — `TMI-120`
  - owner rebind와 continuation 보완 PR #6·#7·#8이 Billing `develop`에 병합됨
  - OPEN·RETAKE_AVAILABLE owner CAS, GRADING pending, COMPLETED NOOP와 phone continuation discovery 구현
  - Billing 최신 전체 236개 테스트에서 replica-set Testcontainers 포함 성공
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
  - replica-set 회귀는 통과했고 cross-service staging 검증이 남음
- ✅ Billing 무료 사용권·진행·재응시 공개 조회 — `PLAN-007`
  - PR #9 merge commit `eb0ae14`가 Billing `develop`에 병합됨
  - `GET /api/v1/entitlements`, JWT·scope 경계, read-only snapshot과 owner epoch 귀속 구현
  - 전체 236개 테스트 성공, feature flag와 public connector는 기본 off
- ⬜ Identity Billing audience·`billing:read` 발급과 프론트 조회 연동
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

판정: Billing의 **TrialClaim·무료 grant·Reservation·AttemptGroup·owner rebind·공개 조회와 Learning Core 시험 생성·상태 publisher·phone continuation·UserMerged까지 핵심 코드와 replica-set 회귀 검증이 완료됐다.** 다만 Identity reader token, public ingress/Lattice·migration과 cross-service staging E2E가 남아 production 종단 흐름은 아직 닫히지 않았다.

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
- ✅ `TMI-125` production 안전성 후속 코드 3건
  - PR/staging workflow `mongoIntegrationTest` required gate
  - UserMerged migration orphan Result/Summary·Session-owner 불일치 preflight
  - phone continuation `attemptGroupId` lowercase UUID v4 strict decode
- ✅ Challenge domain·API·AI Job — `TMI-126`

판정: 기존 모의고사, Billing Reservation 시험 생성, 채점 상태 publisher, phone continuation, UserMerged와 후속 안전성 보완까지 코드상 연결됐고 replica-set 검증도 통과했다. 기존 시험 계열의 남은 신규 코드는 **Billing Reservation background reconciliation**이며, feature flag 활성화 전 실제 migration·Lattice와 cross-service failure-injection E2E가 필요하다.

## 4. 5종 기간제 무제한 이용권

- ✅ 제품 범위와 핵심 정책 확정
  - `PREMIUM_1D`, `PREMIUM_3D`, `PREMIUM_7D`, `PREMIUM_14D`, `PREMIUM_28D`
  - Apple·Google 재구매 가능한 consumable one-time 상품, 자동 갱신 없음
  - Billing 검증 시점부터 24·72·168·336·672시간
  - 정상 만료 전 승인된 현재 Session은 기한 내 완료 허용, 만료된 권리로 새 INITIAL/replacement 금지
  - credit과 첫 구매 2배는 제품에서 완전히 제거
- ✅ RevenueCat 기반 기술 계약 초안 — Billing `ADR-004`
  - RevenueCat은 SDK·Offering·검증 데이터/webhook source, Billing은 권리 source of truth
  - client sync·Authorization+HMAC webhook·API reconciliation을 같은 transaction 멱등 처리로 수렴
  - 환불 후 신규/미제출 진행/replacement 차단, 이미 제출된 GRADING 완료와 COMPLETED history 보존
- ⬜ 기간제 결제 전용 구현 PLAN·Jira
- ⬜ Billing 5종 상품 catalog와 환경별 Store·RevenueCat product/package 매핑
- ⬜ 앱 공개 상품 조회·구매 제출·구매 상태·현재 이용권·복원 API
- ⬜ RevenueCat webhook·REST API 거래 검증 adapter
- ⬜ order·payment·store transaction 원장과 command/transaction 멱등성
- ⬜ `FixedTermEntitlement` 활성·만료·회수와 usage audit
- ⬜ RevenueCat Authorization+HMAC webhook durable inbox
- ⬜ refund·revoke·chargeback 처리
- ⬜ 결제·entitlement reconciliation worker와 운영 runbook
- ⬜ phone 재가입 시 유료권 미이전·새 purchase account binding 처리
- ⬜ Learning Core Reservation entitlement evaluator의 기간제 이용권 지원
- ⬜ 모바일 RevenueCat SDK 구매·동기화·pending/환불 UX
- 🚫 RevenueCat+Store sandbox 결제·환불·webhook 역순/중복·응답 유실 staging E2E

판정: 현재 가장 큰 미구현 기능이다. 제품 정책과 RevenueCat 기반 계약 초안은 상세화됐지만 **별도 결제 PLAN·Jira·runtime은 아직 없다.** Billing 거래·권리 원장과 공개 API, Learning Core evaluator, 모바일 RevenueCat 구매·동기화를 새 vertical slice로 구현해야 한다.

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
- ✅ Learning Core Challenge backend — `TMI-126`
  - PR #30 merge commit `cb5f6ee`가 `develop`에 병합됨
  - 별도 `domain/challenge`, 7개 공개 API, MEMBER 인가와 소유권 검증 구현
  - catalog baseDate singleton·비순환 day, attempt snapshot·1시간 deadline·실제 제출 집계 구현
  - 고정 S3 key, 원자 submit receipt/Job, AI multipart·lease/retry와 strict Callback 구현
  - 결과·history, no-speech·timeout·stale/duplicate/late Callback 수렴 구현
- ✅ Challenge 격리 통합 검증
  - 일반 Java 529개 중 Challenge 33개 성공
  - Mongo replica-set 40개 중 Challenge 29개 성공
  - Node migration 90개 중 Challenge 7개 성공
  - 기존 Exam API·AI `user_id=examId`·S3/Redis 계약 유지
- 🟡 UI — `TMI-102`
  - 저장소 기록 기준 진행 중이며 실제 앱 staging 적용은 미확인
- 🟡 채점 agent — `TMI-106`
  - 저장소 기록 기준 진행 중이며 실제 AI endpoint·credential·TLS·E2E는 미확인
- 🟡 `TMI-126` Jira 상태 정리
  - 구현·PR 병합은 확인했으나 마지막 기록상 Jira 상태 전환은 수행하지 않음
- ⬜ production index·catalog initializer·content exhaustion 운영 적용
- 🚫 모바일 countdown·background·자정·앱 재실행·60초 polling E2E

판정: **Learning Core Challenge backend와 비동기 AI 연동 코드는 구현·병합되고 격리 replica-set 검증까지 통과했다.** 남은 것은 Identity claim 운영 선배포, 실제 AI/Callback·S3/IAM·catalog/index와 모바일 staging E2E, feature flag rollout이다.

## 6. 배포·서비스 간 통합

- 🟡 Identity → Learning Core withdrawal event
  - producer와 consumer 코드는 양쪽 모두 구현
  - 실제 workload credential·HTTPS route·key rotation·backfill staging E2E는 미완료
- 🟡 Identity → Billing eligibility event
  - publisher·SigV4 transport와 consumer 코드는 구현·병합
  - 실제 route·role·negative E2E는 미완료
- 🟡 Identity → Learning Core UserMerged event — `TMI-123`, `TMI-125`
  - producer durable fan-out과 consumer·ownership migration 코드는 모두 구현·병합
  - 격리 replica-set 회귀는 통과했고 실제 workload issuer/JWKS·retry·성능 E2E는 미완료
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
  - Billing replica-set 회귀는 통과했고 Lattice exact route·stale epoch·응답 유실 staging E2E가 남음
- 🟡 앱 → Billing 무료 사용권 조회 — `PLAN-007`
  - Billing reader와 보안·snapshot 코드는 구현·병합
  - Identity Billing audience/`billing:read`, public ALB/SG/JWKS와 프론트 표시 연동은 미완료
- ⬜ 무료시험 전체 흐름 E2E
- ⬜ 기간제 이용권 구매→검증→활성화→시험→복원/환불 전체 흐름 구현·E2E
- ⬜ Challenge 전체 흐름 E2E
- ⬜ staging 환경별 Mongo·Redis·S3·Firebase·credential 분리 확인
- ✅ 격리 Testcontainers Mongo replica-set Transaction·rollback·unique race 검증
- ⬜ production 유사 multi-instance lease·부하·network failure 검증
- ⬜ response loss·timeout·retry·dead-letter·manual replay·rollback 검증
- ⬜ production canary와 단계별 feature flag 활성화
- 🚫 1차 업데이트 production release

## 7. 출시 차단 조건

다음 조건이 모두 해소되기 전에는 production release를 열지 않는다.

- 🚫 모바일 SNS 로그인·phone link와 Identity staging E2E
- 🚫 Identity Billing reader audience·`billing:read` 구현·배포와 무료 조회 staging E2E
- 🚫 Learning Core Billing saga의 실제 Lattice·migration·무료시험 cross-service E2E
- 🚫 기간제 이용권의 Billing·Learning Core·모바일 runtime 구현과 RevenueCat+Store sandbox E2E
- 🚫 Learning Core UserMerged migration·workload·성능 staging E2E
- 🚫 Billing owner rebind·phone continuation cross-service E2E
- 🚫 Challenge 실제 AI/Callback·S3/IAM·catalog/index와 모바일 E2E
- 🚫 workload 인증, 환경 격리, production 유사 multi-instance 검증
- 🚫 response loss·rollback·dead-letter/replay runbook과 canary 검증

## 8. 가장 우선적인 다음 순서

1. Identity의 Billing reader audience·`billing:read` 계획을 Jira로 확정하고 구현해 무료 사용권 조회를 연결한다.
2. `TMI-126` Challenge의 실제 Identity claim·AI/Callback·S3/IAM·catalog/index와 모바일 staging E2E를 통과시킨다.
3. 5종 기간제 이용권의 승인된 RevenueCat 계약으로 별도 구현 PLAN·Jira를 만들고 Billing payment/entitlement vertical slice를 구현한다.
4. Learning Core 유료 entitlement evaluator와 모바일 RevenueCat 구매·동기화를 연결한다.
5. Billing Reservation background reconciliation을 별도 범위로 구현한다.
6. 무료시험·UserMerged·phone continuation의 migration, Lattice/IAM/SG와 cross-service failure-injection staging E2E를 통과한다.
7. 모바일 SNS·무료시험·기간제 이용권·Challenge 종단 E2E 후 canary와 단계별 feature flag rollout을 수행한다.

## 한 줄 요약

현재는 **SNS·무료시험·기존 시험·10초 챌린지의 핵심 서버 코드가 대부분 구현**됐다. 큰 신규 기능 공백은 RevenueCat 기반 5종 기간제 결제이며, Identity Billing reader token과 Billing Reservation background reconciliation도 남아 있다. 구현 완료 영역도 실제 Lattice·AI·S3/IAM·mobile/staging rollout 전에는 production 사용 가능으로 보지 않는다.

프론트 API 상세는 [`docs/contracts/FRONTEND_API_HANDOFF.md`](../contracts/FRONTEND_API_HANDOFF.md), Challenge API v1은 [`docs/contracts/ten-second-challenge-frontend-api.md`](../contracts/ten-second-challenge-frontend-api.md)를 따른다.
