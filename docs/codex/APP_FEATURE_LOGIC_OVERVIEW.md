# 토선생 앱 기능별 로직 현황

- 기준일: 2026-09-03
- 기준 브랜치: Learning Core `develop`
- 범위: 앱 사용자 흐름과 Identity·Learning Core·Billing·AI의 서비스 로직

## 1. 5줄 결론

1. 인증은 Identity가 소유하며 앱은 Identity Access Token으로 Learning Core를 호출한다.
2. 모의고사의 문제·음성·채점·결과는 Learning Core가, 무료시험 권리와 시험 소비 상태는 Billing이 소유한다.
3. 시험 생성은 `reserve → ExamSession durable commit → confirm` saga로 설계됐고 동일 command 재시도는 `Idempotency-Key`로 한 결과에 수렴한다.
4. 문항은 S3 직접 업로드와 비동기 AI Callback으로 처리하며 시험 완료·복구 가능 상태는 durable outbox로 Billing에 전달한다.
5. 핵심 코드는 구현됐지만 관련 flag는 기본 off다. 스토어 결제, Guest UserMerged 전체 이전과 10초 챌린지는 아직 production 사용자 기능이 아니다.

## 2. 반드시 읽어야 하는 현재 상태

| 기능 영역 | 현재 상태 | 사용자 관점 |
|---|---|---|
| LOCAL·Guest·Firebase SNS 인증 | 구현됨, provider별 운영 준비 별도 | Identity Token 발급 가능 |
| 기본 모의고사·채점·결과 | 구현됨 | 현재 앱의 핵심 사용 흐름 |
| 전화번호당 무료시험 원장 | Billing 구현 기반 존재 | production 통합 flag·E2E 필요 |
| Billing 시험 생성 saga | Learning Core 구현·병합, 기본 off | 활성화 전 기존 생성 흐름 사용 |
| AttemptGroup 상태 전파 | 구현·병합, writer/publisher 기본 off | 활성화 전 Billing 상태 동기화 안 됨 |
| phone 재가입 continuation | 구현·병합, 기본 off | staging 검증 뒤 제한 활성화 |
| 회원탈퇴 차단 consumer | 구현 기반, 기본 off | rollout 전 기존 Token 차단 E2E 필요 |
| Guest UserMerged owner 이전 | 후속 작업 | 아직 전체 권리·학습 owner 이전 미완성 |
| 인앱결제·구매 복원 | 후속 릴리스 | 현재 공개 Billing 앱 API 없음 |
| 10초 챌린지 | v1 계약·콘텐츠 준비, API 미구현 | 앱에서 아직 호출 금지 |

코드가 병합된 것과 production에서 flag가 활성화된 것은 다르다. 현재 Learning Core 기본값은 Billing creation saga, phone continuation, AttemptGroup writer/publisher와 UserWithdrawn consumer가 모두 `false`다.

## 3. 사용자가 결정해야 하는 사항

1. 1차 production에서 실제로 활성화할 SNS provider를 Google·Apple로 한정할지 결정한다.
2. Billing·AttemptGroup·phone continuation의 staging E2E가 끝난 뒤 각 flag의 canary 순서와 rollback 기준을 승인한다.
3. 인앱결제 상품·구매 복원·환불 구현 시점을 무료시험 출시와 분리할지 결정한다.
4. Guest UserMerged에서 Billing 권리뿐 아니라 Learning Core 학습 이력도 이전할 최종 proof와 개인정보 정책을 확정한다.
5. 10초 챌린지를 무료시험과 같은 릴리스에 포함할지 별도 릴리스로 분리한다.

## 4. 주요 위험과 미확인 사항

- Identity의 실제 모바일 Google·Apple·phone link와 Firebase staging E2E가 필요하다.
- Billing 연동에는 Mongo replica-set Transaction·index, VPC Lattice exact route·IAM/SG와 장애 주입 E2E가 필요하다.
- `Idempotency-Key`는 앱이 시험 시작마다 새 UUID v4를 만들고 transport retry에서만 재사용해야 한다.
- 이어풀기는 없다. 앱 종료만으로 즉시 Session을 폐기하지는 않지만, 사용자가 다시 시험 시작을 요청했을 때 기존 AttemptGroup이 `OPEN`이면 기존 Session을 `ABANDONED_RESTARTED` 처리하고 추가 소비 없이 처음부터 새 시험을 시작한다.
- phone proof만으로 과거 학습 기록을 새 계정에 자동 이전하면 번호 재할당 시 개인정보 위험이 있으므로 현재 phone continuation은 과거 답안·결과·audio를 복사하지 않는다.
- 기존 프론트 인계 문서 일부의 과거 상태 문장은 최신 병합 상태보다 뒤처질 수 있으므로 runtime 코드와 CURRENT_STATE를 우선한다.

## 5. 기능별 동작 로직

### 5.1 LOCAL 회원가입·로그인

**사용자 동작**

- 이메일 중복 확인 후 약관에 동의하고 회원가입한다.
- 이메일과 비밀번호로 로그인한다.

**서버 로직**

1. Identity가 이메일 unique, 비밀번호 정책과 동의 버전을 검증한다.
2. 회원가입은 canonical UUID user를 생성하지만 Token을 즉시 반환하지 않는다.
3. 로그인 성공 시 Access Token과 rotation 대상 Refresh Token을 발급한다.
4. 앱은 Access Token을 Bearer로 사용하고 Refresh Token은 재발급 API body에만 사용한다.

**실패·보안 처리**

- Access Token 만료 시 한 번 재발급하고 원 요청을 한 번만 재시도한다.
- 재발급도 실패하면 Token을 삭제하고 로그인 화면으로 이동한다.
- 실제 userId를 Learning Core 요청 body/path/query에 보내지 않는다.

### 5.2 Guest 사용

**사용자 동작**

- 로그인 전 Guest로 앱을 시작한다.

**서버 로직**

1. 앱이 설치 단위 UUID v4 `installationId`를 생성한다.
2. Identity가 같은 installationId의 중복 Guest 생성을 막고 Guest Token을 발급한다.
3. installationId는 인증 credential이 아니며 Token 복구 수단으로 사용하지 않는다.
4. 이후 SNS 가입 시 Guest UUID 유지 승격 또는 기존 MEMBER 병합을 선택한다.

**현재 제한**

- canonical `UserMerged`를 Billing과 Learning Core에 완전히 fan-out하여 owner를 이전하는 전체 흐름은 후속 작업이다.

### 5.3 Firebase SNS 가입·로그인

**사용자 동작**

- 앱 Firebase SDK에서 Google·Apple 등 primary credential로 인증한 뒤, 신규 MEMBER 가입 또는 Guest→MEMBER 승격을 완료하기 전에 검증된 phone credential을 같은 Firebase User에 반드시 연결한다.

**서버 로직**

1. 앱이 Firebase ID Token을 강제 갱신한다.
2. Identity가 ID Token을 검증해 기존 identity면 exchange, 신규면 signup 흐름으로 분기한다.
3. 신규 가입·Guest 승격 목적에서는 같은 Firebase UID에 연결된 verified phone이 없으면 `FIREBASE_PHONE_VERIFICATION_REQUIRED`로 거절한다.
4. 가입 완료 Transaction에서 PhoneIdentity와 무료시험 eligibility candidate를 함께 확정한다.
5. provider subject와 canonical userId를 분리해 관리하며 email·phone을 자동 merge key로 사용하지 않는다.
6. Identity가 자체 RS256 Access/Refresh Token을 발급한다.

**현재 제한**

- provider flag와 실제 Firebase project/mobile deep-link 설정은 운영 환경별 검증이 필요하다.
- phone-only 로그인을 기본 가입 흐름으로 사용하지 않는다.
- 이미 가입 시 전화번호 인증을 완료한 기존 MEMBER는 일반 로그인마다 SMS 인증을 반복하지 않는다. 재인증은 전화번호 변경·재가입 또는 별도 고위험 행위처럼 phone proof를 새로 확정해야 하는 흐름에 한정한다.

### 5.4 프로필·동의·로그아웃·회원탈퇴

**서버 로직**

- 프로필과 현재 정책 버전별 동의를 조회·갱신한다.
- 단건 로그아웃은 해당 RefreshSession, 전체 로그아웃은 모든 RefreshSession을 폐기한다.
- 회원탈퇴는 내부 사용자를 개인정보가 제거된 tombstone으로 남기고 RefreshSession 폐기와 Firebase disable/revoke/delete, identity release를 멱등 lifecycle로 처리한다.
- Learning Core UserWithdrawn consumer는 event inbox와 deny gate로 탈퇴 후 남은 Access Token 사용을 막도록 설계됐다.

**현재 제한**

- consumer와 deny gate는 기본 off이며 Token 최대 수명·clock skew·event 보존 설정과 staging E2E 후 활성화한다.

### 5.5 검증 전화번호당 무료 모의고사 1회

**서비스 책임**

| 서비스 | 책임 |
|---|---|
| Identity | verified phone eligibility candidate 생성·전달 |
| Billing | phone당 TrialClaim unique, FREE_EXAM_ONCE grant와 ledger |
| Learning Core | 시험 Session·답안·채점·결과 |

**서버 로직**

1. Identity가 검증 전화번호 eligibility event를 멱등 발행한다.
2. Billing은 eligibility projection만 먼저 저장하고 이 시점에는 사용 완료로 처리하지 않는다.
3. 사용자가 첫 시험을 시작할 때 Billing Transaction이 TrialClaim과 1-unit 무료 grant를 lazy 생성하고 Reservation을 만든다.
4. Learning Core Session 저장이 성공하면 confirm하여 최초 AttemptGroup 소비를 확정한다.
5. Session 저장 전 실패하면 cancel 또는 TTL expiry로 hold를 되돌린다.

**불변 규칙**

- 탈퇴·재가입·merge로 이미 사용한 phone 무료 기회를 다시 열지 않는다.
- raw phone과 fingerprint 원문을 Learning Core에 전달하지 않는다.

### 5.6 시험 생성과 Idempotency

**앱 로직**

- 의도적인 시험 시작 또는 새 restart마다 새 lowercase UUID v4를 만든다.
- timeout·응답 유실로 같은 HTTP operation을 다시 보낼 때만 동일 key를 재사용한다.

**Billing saga**

```text
PREPARED → reserve → RESERVED
→ ExamSession + operation Mongo Transaction commit
→ SESSION_COMMITTED / ENTITLEMENT_CONFIRMING
→ confirm 또는 status reconciliation
→ SUCCEEDED / IN_PROGRESS
```

**실패 처리**

- reserve 실패: 권리를 소비하지 않고 안정적인 공개 오류로 변환한다.
- Session commit 실패: authoritative 상태를 확인한 뒤 안전하게 cancel하거나 재시도한다.
- confirm 응답 유실: 새 Session을 만들지 않고 같은 operation으로 confirm/status를 재조회한다.
- 같은 user의 동시에 진행 중인 생성 command는 active guard로 막는다.

### 5.7 이어풀기 없는 새 시험

**설계**

- 새 시험을 시작하면 기존 `IN_PROGRESS` Session은 `ABANDONED`로 전환한다.
- 기존 답안과 채점 결과를 새 Session으로 이어붙이지 않는다.
- 새 시험은 새 examId와 새 Idempotency-Key를 가진다.

**무료 재시험 경계**

- 시험 완료는 필수 feedback·유효 점수와 사용자가 조회할 수 있는 결정적 Summary가 모두 존재할 때뿐이다. Summary가 없으면 `COMPLETED`로 보지 않는다.
- 앱 종료 자체가 즉시 Session 상태를 바꾸지는 않는다. 이후 사용자가 새 시험 시작을 명시적으로 요청하면 AttemptGroup 상태에 따라 다음처럼 처리한다.

| AttemptGroup 상태 | 새 시험 시작 요청 처리 |
|---|---|
| `OPEN` | 최초 응시에서 차감한 같은 consumption·attemptGroupId·mockExamId를 유지하고, 추가 차감 없이 처음부터 시작하는 새 Session을 만든다. 완료할 때까지 재시작 횟수와 기간을 제한하지 않는다. |
| `GRADING` | 새 Session 생성을 잠시 막고 기존 제출의 채점·Summary 복구를 우선한다. |
| `RETAKE_AVAILABLE` | 최초 응시의 같은 consumption을 유지하고, 추가 차감 없이 처음부터 시작하는 새 Session을 만든다. 완료할 때까지 재시작 횟수와 기간을 제한하지 않는다. |
| `COMPLETED` | 기존 시험 소비가 완료됐으므로 새 entitlement가 필요하다. |

- 각 재시작은 새 examId와 새 Idempotency-Key를 사용하고 처음부터 푼다. 이전 답안·결과·업로드·채점 Job·Summary는 복사하지 않는다.
- 필수 제출을 마치기 전에 종료했다면 `OPEN`이므로 추가 차감 없이 처음부터 재시작한다. 필수 제출을 모두 마쳤지만 Summary가 아직 없다면 `GRADING`에서 기존 채점을 복구하고, 최종 복구가 불가능하면 `RETAKE_AVAILABLE`로 전환해 추가 차감 없는 처음부터 재시작을 허용한다.

### 5.8 문제 제공

**응답 내용**

- part, questionNumber, 문제 text·referenceText·안내문
- 문제·가이드 audio URL
- 일반 imageUrl
- 준비·발화 시간
- Part 4 `tableContext`

**Part 4 표 처리**

- Mongo `table_context`를 `Map<String,Object>`로 읽어 JSON `tableContext`로 가공 없이 전달한다.
- 서버는 HTML·Markdown 표 또는 title/rows 고정 schema로 변환하지 않는다.
- 앱이 중첩 object·array를 해석해 렌더링한다.
- 내부 `table_image_url`은 공개 DTO에 포함하지 않는다.
- Part 4 tableContext가 null이면 catalog configuration error이고 빈 object는 허용된다.

### 5.9 음성 녹음·S3 업로드·제출

**앱 호출 순서**

```text
문제 조회
→ upload-url 발급
→ S3 Presigned URL에 raw audio PUT
→ body 없는 submit
→ 문항 상태 polling
```

**서버 로직**

1. Learning Core가 examId·questionNumber·retryCount로 S3 key를 결정한다.
2. 앱은 S3 PUT에 Identity Authorization을 붙이지 않는다.
3. 업로드 성공 후 submit을 호출하면 QuestionGradingJob을 멱등 생성·dispatch한다.
4. 최초 답변은 retryCount 0, 사용자의 새 녹음은 1 이상으로 분리한다.

### 5.10 AI 문항 채점

**계약**

- Python AI의 `user_id`는 실제 userId가 아니라 examId다.
- 기존 multipart 요청·Callback URL·JSON 계약을 유지한다.

**서버 로직**

1. submit이 결정적 Job을 만들고 AI에 전송한다.
2. STT·Azure·LLM 등의 결과가 Callback으로 도착한다.
3. examId, questionNumber, retryCount와 Job generation을 검증한다.
4. duplicate·stale Callback은 최신 결과를 덮어쓰지 않고 멱등 수렴한다.
5. 앱은 PENDING·PROCESSING 동안 polling하고 COMPLETED·FAILED에서 중단한다.

### 5.11 시험 Summary와 결과

**서버 로직**

1. 모든 필수 문항의 최초 응시 retryCount 0 결과가 준비되면 SummaryGradingJob을 시작한다.
2. Summary Callback은 generation을 검증하고 Summary·Job·Session terminal·outbox를 Transaction으로 저장한다.
3. 앱은 전체 상태, 종합 점수·피드백, 문항별 결과와 완료 이력을 조회한다.
4. retryCount 1 이상 답안은 비교 이력으로 제공하지만 최초 시험 완료 evidence를 대체하지 않는다.

### 5.12 채점 복구

**사용자 동작**

- 제출한 녹음이 있는데 채점이 실패·지연된 시험에 복구 요청을 보낸다.

**서버 로직**

- 새 녹음을 요구하지 않고 기존 retryCount 0 제출의 Job만 재구성·재전송한다.
- 이미 처리 중인 문항, 재전송한 문항, 제출 자체가 없는 문항을 구분한다.
- Summary가 이미 완료됐으면 중복 실행하지 않는다.

### 5.13 AttemptGroup 상태와 Billing 동기화

**상태 판정**

| 상태 | 조건 |
|---|---|
| OPEN | 아직 필수 retry 0 제출이 모두 접수되지 않음. 중단 후 다시 시작하면 추가 차감 없이 새 Session으로 교체 |
| GRADING | 필수 retry 0 submit과 durable Question Job 존재 |
| COMPLETED | 필수 feedback·점수와 결정적 Summary 모두 존재 |
| RETAKE_AVAILABLE | GRADING 진입 후 채점 retry 소진, Summary 복구 불가 또는 deadline 초과. 제출·결과 정합성 위반은 발견 시점과 무관하게 안전한 재시작 대상으로 종료 |

**미제출 중단과 채점 실패의 차이**

- 필수 문제를 모두 제출하지 않은 Session은 `GRADING`으로 가지 않고 AttemptGroup이 `OPEN`에 남는다.
- 이 상태에서 사용자가 다시 시작하면 `OPEN → OPEN` 교체가 일어난다. 기존 Session은 `ABANDONED_RESTARTED`, 새 Session은 새 examId로 생성되며 추가 entitlement는 차감하지 않는다.
- 모든 필수 retry 0 제출과 durable Question Job이 확인된 뒤에만 `OPEN → GRADING`으로 전환한다.
- `RETAKE_AVAILABLE`은 원칙적으로 `GRADING`에 들어간 시험의 채점·Summary를 끝내 복구하지 못했을 때 사용한다. 데이터 중복 등 자동 복구 불가능한 결과 정합성 위반은 잘못된 결과를 완료 처리하지 않기 위해 예외적으로 즉시 `RETAKE_AVAILABLE`로 종료할 수 있다.

**전달 로직**

1. Session local projection과 outbox event를 같은 Mongo Transaction/CAS로 저장한다.
2. 한 Session에는 terminal event 하나만 허용한다.
3. publisher가 lease로 event를 claim하고 새 CLIENT span을 만든다.
4. traceparent를 넣은 뒤 SigV4를 마지막 단계에서 적용해 Billing으로 전송한다.
5. 401/403은 BLOCKED_AUTH circuit으로 보존하며 event를 잃지 않는다.

### 5.14 시스템 실패 무료 재시험

**서버 로직**

- AttemptGroup이 `RETAKE_AVAILABLE`이면 다음 시험 생성에서 기존 attemptGroupId와 mockExamId를 snapshot한다.
- Billing이 exact REPLACEMENT를 승인해야 새 Session을 만든다.
- 새 examId로 처음부터 다시 풀며 과거 답안·결과를 복사하지 않는다.
- 정상 완료 evidence가 뒤늦게 발견되면 COMPLETED가 실패 상태보다 우선한다.

### 5.15 phone 탈퇴·재가입 continuation

**발동 조건**

- phone continuation flag가 켜져 있다.
- 새 target user 소유 ExamSession이 하나도 없다.
- Billing이 동일 retained phone claim의 안전한 continuation을 승인한다.

**서버 로직**

1. Learning Core가 Billing discovery를 호출한다.
2. 204면 일반 INITIAL 시험 생성으로 진행한다.
3. strict 200이면 PHONE_REJOIN REPLACEMENT context를 operation에 먼저 snapshot한다.
4. Billing의 기존 AttemptGroup·mockExamId에 target의 새 examId를 연결한다.
5. source Session owner, 답안, 결과, Summary, Job과 audio는 조회·복사·수정하지 않는다.

### 5.16 인앱결제와 상품

**확정된 제품 방향**

- Apple·Google 인앱결제만 사용한다.
- 서버 고정비와 시험당 AI 변동비를 분리해 BEP를 계산한다.

**현재 상태**

- 상품 조회, 영수증 검증, 구매 복원, 환불, credit/pass/coupon 공개 앱 API는 아직 완성되지 않았다.
- 현재 Billing 공개 앱 API는 0개이며 앱이 Billing internal Reservation API를 직접 호출하면 안 된다.
- 시험 시작은 항상 Learning Core가 Billing internal API를 호출하는 구조를 유지한다.

### 5.17 10초 영작 챌린지

**승인된 목표 로직**

- MEMBER 전용, KST 날짜별 공통 3문제, 1→2→3 순차 진행이다.
- 한국어 prompt를 보고 영어로 최대 10초 녹음한다.
- 문제당 별도 ChallengeAttempt를 만들고 S3 업로드 후 Challenge 전용 AI 계약으로 채점한다.
- referenceAnswer는 제출 또는 만료 terminal 전까지 숨기고 attempt snapshot에서 결과를 조립한다.
- 기존 ExamSession·retryCount·시험 AI Callback을 재사용하지 않는다.

**현재 상태**

- 프론트·AI v1 계약과 Mongo 콘텐츠는 준비됐다.
- Learning Core Challenge domain, 공개 API, Job·Callback runtime은 아직 구현되지 않았다.

## 6. 부록 — 서비스 책임 경계

| 서비스 | 단일 책임 |
|---|---|
| 앱 | 로그인 UI, Token 보관·재발급, 녹음, S3 PUT, polling과 결과 화면 |
| Identity | canonical user, 인증수단, Token, phone eligibility, 동의·탈퇴·merge event |
| Learning Core | 시험 Session, 문제, S3 key, 채점 Job, 결과·Summary, AttemptGroup evidence |
| Billing | TrialClaim, grant/credit/pass, Reservation, consumption, AttemptGroup 권리 상태 |
| AI | 음성 인식·문항 채점·Summary 생성 후 Callback |

## 7. 부록 — production 활성화 순서

1. Billing consumer·Mongo Transaction/index·Lattice IAM을 비활성 상태로 선배포한다.
2. AttemptGroup publisher를 writer보다 먼저 idle 활성화해 인증·연결을 검증한다.
3. Billing creation saga를 staging에서 INITIAL·timeout·응답 유실·cancel로 검증한다.
4. AttemptGroup writer를 canary 활성화하고 GRADING·COMPLETED·RETAKE_AVAILABLE을 확인한다.
5. phone continuation을 204·200·stale·응답 유실 시나리오로 검증한 뒤 별도 활성화한다.
6. Identity eligibility와 owner event producer는 consumer 준비 후 마지막에 활성화한다.
7. 결제와 Challenge는 각각 별도 공개 API·모바일 E2E를 마친 뒤 독립 출시한다.
