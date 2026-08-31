# SNS 로그인·전화번호당 무료 모의고사·10초 챌린지 개정 계획

- 작성일: 2026-08-24
- Jira: 신규 키 없음
- 상태: 최소 Billing/Entitlement와 10초 챌린지 프론트·AI v1 계약·Learning Core 저장소 구현 범위 승인 완료, SNS provider·실제 구현·운영 검증 필요

## 1. 범위 변경

- Apple/Google 인앱결제, credit pack, unlimited pass, coupon과 환불 구현은 후속 릴리스로 연기한다.
- 현재 우선 범위는 SNS 로그인, 검증된 전화번호당 무료 모의고사 1회, 10초 챌린지다.
- 결제 계약과 Billing skeleton은 삭제하지 않고 동결한다. 결제 재개 시 Billing 저장소 계약을 기준으로 이어간다.
- 결제를 미뤄도 전화번호당 1회를 보장하는 `TrialClaim` 원장은 필요하다.

## 2. 현재 구현 상태

### Identity

- Firebase Authentication을 credential broker로 사용하는 기반과 Identity 자체 JWT/RefreshSession이 구현돼 있다.
- Firebase exchange/signup, Guest prepare/upgrade/merge와 auth-method sync API가 존재한다.
- FirebaseIdentity, SocialIdentity, PhoneIdentity, phone eligibility binding/publisher와 Guest merge outbox 기반이 있다.
- Google·Apple·Kakao·Phone feature flag는 기본 비활성이다.
- 실제 모바일 Google/Apple, 전화번호 link, 국내 SMS, Kakao Identity Platform/OIDC와 staging E2E가 남아 있다.
- production 활성화 전 탈퇴 재인증, Firebase disable/revoke/delete와 identity release lifecycle 1~3단계를 완료해야 한다.

### Learning Core

- Identity JWT 사용자 식별, 시험 Session, 시험지 배정, S3 음성 제출, AI 채점, Callback 멱등성, 결과·polling이 구현돼 있다.
- 전화번호당 무료 1회를 강제하는 `TrialClaim`/Entitlement 연동은 없다.
- 10초 챌린지 runtime domain, API, attempt와 scoring은 아직 없다. 콘텐츠는 Learning Core가 사용하는 MongoDB cluster `to-teacher-app`의 `challenge_10s_questions` collection에 `dayNumber`별 세 문제 embedded document로 저장돼 있다.

### Billing

- 서비스 skeleton과 계약 문서는 있으나 결제·entitlement domain은 구현되지 않았다.
- 결제 구현은 동결한다. 무료시험 원장은 Billing의 최소 Entitlement 범위로 먼저 구현하는 것으로 확정돼 있다.

## 3. 권장 서비스 경계

| 기능 | 소유 서비스 |
| --- | --- |
| Google·Apple·Kakao credential 인증, phone proof | Firebase |
| canonical userId, SNS/phone mapping, Access/Refresh Token | Identity |
| phone eligibility candidate 생성·전달 | Identity |
| 전화번호당 `TrialClaim`, `FREE_EXAM_ONCE`, reserve/confirm | 최소 Billing/Entitlement — 권장 |
| 시험 Session·문제·채점·결과 | Learning Core |
| 10초 챌린지 정의·attempt·학습 결과 | Learning Core |

Identity에 `freeTrialUsed`를 넣지 않는다. Learning Core에 raw phone이나 PhoneIdentity fingerprint를 전달하지 않는다.

### 무료 1회 원장 배치 결정

#### A. Billing을 결제 없이 최소 Entitlement 서버로 사용 — 확정

- `TrialClaim`, `FREE_EXAM_ONCE`, reserve/confirm/cancel과 멱등성만 구현한다.
- Store, credit, pass, coupon, 환불 코드는 구현하지 않는다.

장점: phone당 1회와 시험 상태를 Identity/Learning Core 밖에서 일관되게 소유하고 향후 결제 재개 시 migration이 작다.

단점: 무료 기능 하나를 위해 세 번째 서비스를 배포·운영해야 한다.

#### B. Learning Core에 임시 TrialClaim 구현 — 제외

장점: 배포 서비스가 늘지 않고 첫 출시가 빠르다.

단점: 학습 서비스가 benefit candidate와 claim 보존을 소유하게 되고 결제 재개 시 데이터·API migration이 필요하다.

#### C. Identity에 TrialClaim 구현 — 제외

장점: verified phone 데이터와 가깝다.

단점: 인증과 혜택 사용 원장을 결합하고 시험 reserve/confirm을 Identity가 알아야 하므로 권장하지 않는다.

## 4. 목표 사용자 흐름

### SNS 회원가입·로그인

```text
앱 Firebase SDK에서 Google/Apple 로그인
→ 같은 Firebase User에 phone credential link
→ 강제 갱신 Firebase ID Token
→ Identity signup 또는 exchange
→ Identity가 canonical UUID userId의 Access/Refresh Token 발급
→ 앱은 Identity JWT로 Learning Core와 Entitlement 호출
```

- provider email이나 phone을 userId 또는 자동 merge key로 사용하지 않는다.
- phone-only Firebase 로그인은 허용하지 않는다.
- Kakao는 Identity Platform/OIDC와 모바일 deep-link를 실제 staging에서 검증하기 전 활성화하지 않는다.

### 무료 모의고사 1회

```text
Identity가 verified-phone eligibility candidate를 Entitlement에 멱등 전달
→ 사용자가 첫 시험 시작
→ Entitlement가 candidate 기준 TrialClaim unique 확보 + FREE_EXAM_ONCE reserve
→ Learning Core가 ExamSession commit
→ Entitlement confirm
→ 시험 진행·채점·결과 조회
```

- binding event 수신만으로 사용 완료 처리하지 않는다.
- reserve 뒤 Session 저장 실패는 cancel/expire로 무료 기회를 복구한다.
- 탈퇴·재가입·merge로 TrialClaim을 삭제하거나 다시 열지 않는다.
- raw phone, token과 fingerprint 원문을 로그·응답에 넣지 않는다.

### 10초 챌린지

10초 챌린지는 시험과 별도 aggregate로 시작한다.

승인된 MVP:

- 로그인한 MEMBER 대상
- KST 날짜당 오늘의 challenge 1세트, 세 문제
- 각 한국어 문장을 보고 영어 문장을 만들어 직접 발음한 녹음 audio를 문제마다 S3에 제출
- client가 녹음 길이를 최대 10초로 제한하고 server는 attempt·S3 upload·terminal 상태만 관리
- 같은 KST 날짜에는 모든 사용자에게 동일한 3문제를 제공하고 1→2→3 순서로 진행
- 세 문제 완료는 필수가 아니며 월별 history에서 날짜별 실제 풀이 여부와 풀이 문제 수를 노출
- 기존 시험 `retryCount`, ExamSession, Summary Job을 재사용하지 않고 별도 `ChallengeAttempt`로 저장
- AI 피드백 계약도 시험 Callback과 섞지 않고 challenge 전용 versioned 계약을 사용한다. 승인된 wire 명세는 `docs/contracts/ten-second-challenge-ai-api.md` v1을 따른다.
- 첫 릴리스에는 credit·무료시험·경제적 reward를 연결하지 않고 문제별 완료 이력과 AI 피드백을 저장
- 별도 Mongo cluster를 만들지 않고 Learning Core의 기존 `to-teacher-app` 연결과 운영 credential boundary를 재사용
- `challenge_10s_questions.korean`을 공개 `promptKo`로 변환하고 `referenceAnswer`는 제출·만료 terminal 전까지 숨김
- attempt 생성 시 선택된 `dayNumber`, `questionId`, 문제 문장·참고 답안·difficulty를 snapshot해 catalog 변경과 무관하게 제출·과거 결과를 재현. difficulty는 프론트에 그대로 반환하지만 AI 요청에서는 제외

구현 전 검증할 항목:

1. AI 계약 v1의 M4A/AAC 허용 profile·최대 2 MiB·서비스 인증·timeout/retry를 실제 모바일 fixture와 staging contract test로 검증
2. 승인된 프론트 `aiResult`·no-speech·사전 정의 답안 projection을 구현하고 결과 노출 문구를 E2E 검증
3. MEMBER 전용 authorization을 구현하고 Guest `403`을 검증
4. streak를 즉시 제공할지 후속으로 미룰지

## 5. 우선 결정할 제품 계약

### 결정 1 — SNS provider 범위

- 권장: 1차 Google + Apple, Kakao는 staging PoC 뒤 2차 활성화
- 대안: Kakao까지 동시 출시. 국내 사용자 접근성은 좋지만 Identity Platform/OIDC, billing, stable subject와 deep-link 검증 범위가 커진다.

### 결정 2 — 무료 TrialClaim 소유 위치 — 확정

- Billing skeleton을 최소 Entitlement 서버로 사용하고 결제 모듈은 동결한다.
- Learning Core 임시 소유와 Identity 소유는 채택하지 않는다.

### 결정 3 — 10초 챌린지 MVP

- 확정: KST 일 3문제, 전 사용자 공통 문제, 1→2→3 순차 진행, 녹음 길이 최대 10초, 한국어→영어 발화 audio, 문제당 1 attempt
- 확정: attempt는 생성 당시 challengeDate에 귀속하고 생성 시점부터 1시간 동안 제출 허용
- 확정: 녹음 시작 직전에 attempt를 먼저 생성하고, 녹음 완료 후 같은 attemptId로 S3 Presigned PUT URL을 별도 발급·재발급한다. S3 object key는 attempt 생성 시 서버 내부에서 고정한다.
- 확정: 콘텐츠는 기존 `to-teacher-app` cluster의 `challenge_10s_questions` collection을 사용하고 별도 cluster를 만들지 않는다. Mongo 내부 식별자와 `referenceAnswer`는 문제 조회 응답에 노출하지 않는다.
- 확정: `app.challenge.enabled=true`로 처음 성공 기동한 KST 날짜를 Mongo `challenge_10s_catalog_state` singleton에 원자적으로 저장하고 그날을 dayNumber 1로 사용한다. 재배포 시 초기화하지 않고 콘텐츠는 순환하지 않는다.
- 확정: difficulty 정수값은 공개 문제·terminal 결과 DTO에 그대로 전달하고 AI 요청에는 포함하지 않는다.
- 확정: MEMBER 전용, Guest preview와 경제적 reward 없음
- audio와 AI 계약은 `docs/contracts/ten-second-challenge-ai-api.md` v1에 M4A/AAC-LC, 16/44.1/48 kHz, mono/stereo, 최대 2 MiB, AI 내부 16 kHz mono 정규화와 transcript·verdict·corrected answer·의미/문법/발음 feedback으로 승인했다. 구현과 staging contract test는 남아 있다.

## 6. 구현 순서

### Phase 0 — 범위와 계약 동결

1. SNS provider 범위와 10초 챌린지 MVP를 승인한다. 최소 Entitlement 소유권은 이미 확정됐다.
2. SNS, Free Trial, Challenge를 각각 별도 Jira와 branch로 분리한다.
3. payment 관련 Jira는 취소하지 않고 deferred/backlog로 이동한다.

### Phase 1 — SNS와 phone production readiness

1. Identity의 Firebase/SNS 탈퇴 lifecycle 1~3단계를 완료한다.
2. Firebase staging project에 Google·Apple·Phone을 구성한다.
3. 앱의 Firebase SDK 로그인·phone link·Token refresh를 구현한다.
4. Identity API와 실제 모바일 staging E2E를 수행한다.
5. Kakao는 별도 gate 통과 후 추가한다.

### Phase 2 — 전화번호당 무료 1회

1. Identity eligibility publisher의 consumer endpoint를 준비한다.
2. 선택한 서비스에 TrialClaim unique와 free entitlement를 구현한다.
3. reserve/Session commit/confirm과 실패 복구를 연결한다.
4. 같은 번호·다른 계정, 동시 시작, 응답 유실, 탈퇴·재가입·merge E2E를 수행한다.

### Phase 3 — 10초 챌린지 MVP

1. `challenge_10s_questions` repository, `dayNumber`/question catalog validator와 production index migration을 구현한다.
2. `challenge_10s_catalog_state` one-time initializer와 KST `challengeDate → dayNumber` 비순환 resolver, missing content fail-closed 정책을 구현한다.
3. ChallengeAttempt·grading job과 `(userId, challengeDate, questionNumber)` unique 정책, 문제 snapshot을 구현한다.
4. content 제공, attempt 시작, upload-url, S3 PUT 확인, submit, status/result API를 구현한다.
5. S3/AI 계약과 timeout·중복 submit·늦은 Callback을 멱등 처리한다.
6. 실제 모바일 countdown·background/재실행·자정 rollover E2E를 수행한다.

### Phase 4 — 통합 출시

- 신규 MEMBER → phone link → Identity JWT → 무료시험 1회 → 재요청 거절을 staging에서 검증한다.
- 같은 사용자로 오늘의 챌린지 시작·제출·결과 조회를 검증한다.
- 기능 flag는 consumer와 복구 흐름이 준비된 뒤 SNS, 무료시험, challenge 순으로 연다.

## 7. 지금 바로 할 일

1. 이미 작성된 Identity 탈퇴 lifecycle 1단계 계획의 Jira 초안을 검토·승인해 첫 구현 작업으로 만든다.
2. 1차 SNS provider를 Google+Apple로 고정하고 Kakao의 동시 출시 여부를 결정한다.
3. 승인된 `docs/contracts/ten-second-challenge-ai-api.md` v1을 기준으로 양쪽 구현·audio fixture·인증·feedback·retry contract test를 수행한다.
4. Billing 최소 Entitlement consumer와 Challenge MVP를 각각 별도 Jira로 분리한다.

서버 구현의 첫 작업은 신규 SNS endpoint 추가가 아니라 이미 작성된 Identity 탈퇴 lifecycle 1단계와 실제 Firebase staging/mobile 준비다.
