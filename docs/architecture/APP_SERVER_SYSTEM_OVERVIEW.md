# 토선생 앱 서버 시스템 구조 조사

- 조사일: 2026-08-31
- 범위: 앱용 Learning Core, Identity, Billing
- 제외: 기존 웹 POC 백엔드와 웹 프론트엔드
- 목적: 출시 이후 신규 기능을 추가하면서도 서비스 경계와 코드 구조를 유지하기 위한 공통 지도

## 1. 조사 기준 시점

| 서비스 | 저장소 | checkout | 조사 대상 |
| --- | --- | --- | --- |
| Learning Core | `app-back-end-learning-core` | `develop@d95d18b` | 시험, 채점, S3, AI 연동, 사용자 소유권, Billing 시험 생성 saga, 탈퇴 사용자 차단 |
| Identity | `identity` | `feat/TMI-116-billing-reservation-exam-saga@8c4f3ca` | 계정, LOCAL·Guest·Firebase 인증, JWT/JWKS, 세션, 프로필·동의·탈퇴, downstream outbox |
| Billing | `billing` | `develop@39e424d` | 전화 검증 자격 projection, 무료 혜택·Claim·Grant·Ledger, Reservation, AttemptGroup projection |

기존 미커밋 변경은 조사 입력으로만 읽고 수정하거나 되돌리지 않았다. 이 문서는 세 checkout의 순간 스냅샷이며 배포 상태를 자동으로 보증하지 않는다.

## 2. 먼저 보는 결론

현재 서비스 분리는 전반적으로 합리적이다.

- Identity가 사용자와 인증 자격증명을 소유한다.
- Learning Core가 시험·문항·음성·채점 결과를 소유한다.
- Billing이 혜택·사용권·원장·Reservation을 소유한다.
- 앱은 Identity와 Learning Core만 호출하고 Billing 내부 API를 직접 호출하지 않는다.
- 실제 `userId`와 Python AI의 기존 `user_id=examId` 의미가 분리되어 있다.

즉, 지금 필요한 것은 서비스를 다시 합치거나 더 쪼개는 작업보다 다음 세 가지다.

1. 구현된 경계를 실제 운영 연결까지 닫는다.
2. Learning Core 내부에 누적된 시험 orchestration을 책임별 모듈로 정리한다.
3. 세 저장소의 용어·응답·시간·패키지·이벤트 관례를 공통 사전과 fitness test로 고정한다.

## 3. 상태 범례

| 상태 | 의미 |
| --- | --- |
| 구현 | 현재 checkout에 실행 코드와 테스트가 존재 |
| 조건부 | 실행 코드는 있으나 feature flag가 기본 off이거나 운영 gate가 남음 |
| 계약 | 승인된 계약·계획은 있으나 실행 코드가 없음 |
| 후속 | 방향만 정했거나 별도 작업이 필요한 상태 |

draw.io에서도 구현은 초록, 조건부는 파랑, 계약은 주황 점선, 후속은 회색으로 표시한다.

## 4. 통합 컨셉맵

### 4.1 사용자와 인증

| 개념 | 소유 서비스 | 설명 |
| --- | --- | --- |
| `User` | Identity | 실제 사용자 계정. `userId`는 UUID 문자열이다. |
| `UserAccountType` | Identity | `GUEST`와 `MEMBER`를 구분한다. |
| `FirebaseIdentity`·`SocialIdentity`·`PhoneIdentity` | Identity | 외부 인증수단과 내부 User의 연결이다. |
| `RefreshSession` | Identity | 원문이 아닌 해시 Refresh Token의 회전·폐기 단위다. |
| Access Token | Identity 발급, 각 resource server 검증 | RS256 JWT이며 `sub=userId`, `aud`와 issuer를 검증한다. |
| JWKS | Identity | Public Key만 제공하며 Learning Core가 매 요청 로컬 검증에 사용한다. |

### 4.2 시험과 채점

| 개념 | 소유 서비스 | 설명 |
| --- | --- | --- |
| `MockExam` | Learning Core | 사용자에게 배정할 시험지 정의다. |
| `ExamSession` | Learning Core | 사용자에게 배정된 한 번의 시험 실행이며 `examId→userId`를 소유한다. |
| `Question` | Learning Core | 시험지의 문항 정의다. |
| `QuestionGradingJob` | Learning Core | `examId+questionNumber+retryCount` 단위 채점 작업이다. |
| `SummaryGradingJob` | Learning Core | 시험 전체 요약 생성 작업이다. |
| `ExamResult` | Learning Core | 문항·회차별 AI 피드백 결과다. |
| `ExamSummary` | Learning Core | 시험 전체 결과다. |
| 사용자 `retryCount` | Learning Core | 새 녹음을 제출한 응시 회차다. 내부 전송 재시도 횟수와 다르다. |

### 4.3 혜택과 사용권

| 개념 | 소유 서비스 | 설명 |
| --- | --- | --- |
| `TrialEligibility` | Billing | Identity의 verified phone event를 반영한 현재 자격 projection이다. 무료권 자체가 아니다. |
| `BenefitDefinition` | Billing | `FREE_EXAM_ONCE` 같은 혜택 종류와 정책을 정의하는 catalog다. |
| `TrialClaim` | Billing | 검증 전화 기준 무료 혜택 중복 수급을 막는 anti-abuse 기록이다. |
| `EntitlementGrant` | Billing | 사용자 subject에게 실제로 발급된 1-unit 권리다. |
| `EntitlementLedgerEntry` | Billing | 지급·hold·release·consume 이력이다. |
| `Reservation` | Billing | 시험 Session commit 전에 사용권을 잠시 hold하는 5분 수명 command 결과다. |
| `ReservationAllocation` | Billing | 어느 Grant unit을 어떤 Reservation이 hold/consume했는지 연결한다. |
| `AttemptGroup` | Billing | 최초 시험과 무차감 replacement를 같은 소비 단위로 묶는다. |
| `AttemptSession` | Billing | 시험·채점 데이터가 아니라 active/stale fencing을 위한 최소 Session projection이다. |

### 4.4 서비스 사이의 식별자

| 이름 | 생성·소유 | 다른 서비스에서의 의미 |
| --- | --- | --- |
| `userId` | Identity | Learning Core 사용자 소유권 키, Billing workload command의 인증된 사용자 참조 |
| `examId` | Learning Core | Learning Core `ExamSession` ID, Billing 계약의 opaque `sessionId`, Python AI `user_id` |
| `mockExamId` | Learning Core catalog | AttemptGroup에서 유지할 시험지 식별자 |
| `operationId` | 앱의 `Idempotency-Key` | Learning Core와 Billing command가 같은 시험 시작 동작으로 수렴하는 키 |
| `reservationId` | Billing | hold lifecycle 식별자. 앱에 노출하지 않는다. |
| `attemptGroupId` | Billing | 최초 시험과 replacement의 소비 그룹. Learning Core Session에 내부 저장한다. |
| `jobId` | Learning Core | 채점 요청과 Callback generation을 식별하는 결정적 내부 키 |
| `candidate` | Identity 생성, Billing 저장 | raw phone 대신 무료 Claim 중복 비교에 사용하는 scope별 불투명 값 |
| `subjectRefId` | Billing | erasable user mapping과 원장 core를 분리하기 위한 Billing 내부 주체 참조 |

## 5. 현재 시스템 아키텍처

### 5.1 사용자 요청 경로

1. 앱이 Identity 공개 인증 API에서 Access/Refresh Token을 발급받는다.
2. 앱은 Identity 보호 API와 Learning Core 보호 API에 Access Token을 보낸다.
3. Identity는 자체 보호 API에서 JWT를 검증한다.
4. Learning Core는 Identity JWKS로 RS256 서명, issuer, audience와 UUID `sub`를 검증한다.
5. Learning Core는 `sub`를 실제 `userId`로 사용하고 `examId` API마다 `ExamSession.userId` 소유권을 확인한다.

### 5.2 시험 생성과 Billing

Billing saga flag가 off이면 Learning Core가 기존 방식으로 Session을 생성한다.

flag가 on인 승인 흐름은 다음과 같다.

1. 앱이 `POST /api/v1/exams`와 lowercase UUID v4 `Idempotency-Key`를 보낸다.
2. Learning Core가 `ExamCreationOperation`과 proposed `examId/mockExamId`를 준비한다.
3. Learning Core가 SigV4로 Billing reserve를 호출한다.
4. Billing이 TrialEligibility·Claim·Grant를 확인하고 unit을 hold한다.
5. Learning Core가 `ExamSession(ENTITLEMENT_CONFIRMING)`과 operation `SESSION_COMMITTED`를 같은 Mongo Transaction에 저장한다.
6. Learning Core가 Billing confirm을 호출한다.
7. Billing이 unit을 consume하고 AttemptGroup/AttemptSession을 연다.
8. Learning Core가 Session을 `IN_PROGRESS`, operation을 `SUCCEEDED`로 확정한 뒤 기존 성공 DTO를 반환한다.

코드는 구현됐지만 flag는 기본 off다. Mongo migration·replica-set failure injection, 실제 Lattice/IAM/SG와 INITIAL/REPLACEMENT staging E2E가 활성화 gate로 남는다.

### 5.3 음성 제출과 채점

1. 앱이 Learning Core에서 S3 Presigned PUT URL을 받는다.
2. 앱이 S3에 직접 음성을 업로드한다.
3. 앱이 submit API로 업로드 완료를 알린다.
4. Learning Core가 결정적 `QuestionGradingJob`을 생성·claim한다.
5. Learning Core가 S3 음성을 내려받아 Python AI에 multipart로 전송한다.
6. Python AI와 외부 채점 결과가 Learning Core Callback으로 돌아온다.
7. Learning Core가 Callback을 멱등 저장하고 문항 Job을 완료한다.
8. 모든 필수 최초 문항 결과가 준비되면 `SummaryGradingJob`을 시작한다.
9. 앱은 상태 polling 후 문항 결과와 Summary를 조회한다.

### 5.4 이벤트와 사용자 lifecycle

- Identity는 전화 eligibility, UserMerged, UserWithdrawn을 durable outbox로 발행하는 구조를 가진다.
- Learning Core에는 UserWithdrawn inbox와 접근 deny marker consumer가 구현되어 있다.
- Billing에는 phone eligibility inbox·revision high-water·projection consumer가 구현되어 있다.
- Identity의 phone eligibility outbound adapter는 현재 Bearer workload JWT를 사용하지만 Billing의 운영 목표 ingress는 VPC Lattice AWS_IAM이다. 실제 연동 전에 SigV4 transport 정렬이 필요하다.
- Learning Core의 AttemptGroup 상태 outbox/publisher와 Billing consumer는 아직 없다. 따라서 `OPEN→GRADING→COMPLETED/RETAKE_AVAILABLE` 수렴은 후속 작업이다.

## 6. Feature Map

### 6.1 Identity

| Capability | 상태 | 외부 사용자 |
| --- | --- | --- |
| 이메일 중복 확인·회원가입·로그인 | 구현 | 앱 |
| Guest 생성·인증 | 구현 | 앱 |
| Firebase exchange·가입 | 구현 | 앱 |
| Guest prepare·upgrade·merge | 구현 | 앱 |
| SNS 인증수단 동기화 | 구현 | 앱 |
| Access Token·Refresh Token rotation | 구현 | 앱 |
| 단일·전체 로그아웃 | 구현 | 앱 |
| 내 프로필·동의 조회/수정 | 구현 | 앱 |
| 회원 탈퇴 | 구현 | 앱 |
| JWKS | 구현 | Learning Core 등 resource server |
| Phone eligibility outbox | 구현·연동 gate | Billing |
| UserMerged outbox | 구현·downstream 미완성 | Learning Core |
| UserWithdrawn outbox | 구현·환경별 활성화 | Learning Core |
| 사용자 프로필 수정 | 후속 | 앱 |
| 다중 active/retiring JWT key rotation | 후속 | 운영 |

### 6.2 Learning Core

| Capability | 상태 | 외부 사용자 |
| --- | --- | --- |
| 시험 생성·시험지 순환 배정 | 구현 | 앱 |
| 완료 시험 이력 | 구현 | 앱 |
| 문제 조회 | 구현 | 앱 |
| S3 업로드 URL·음성 제출 | 구현 | 앱·S3 |
| 문항/전체 채점 상태 polling | 구현 | 앱 |
| 문항 결과·재응시 비교·모범 음성 | 구현 | 앱 |
| 종합 결과 | 구현 | 앱 |
| 시험 단위 채점 복구 | 구현 | 앱 |
| AI Question/Summary 요청·Callback | 구현 | Python AI |
| Billing Reservation 시험 생성 saga | 조건부 | Billing |
| 탈퇴 사용자 deny consumer | 조건부 | Identity |
| AttemptGroup 상태 outbox/publisher | 계약·미구현 | Billing |
| UserMerged consumer와 소유권 이전 | 계약·미구현 | Identity |
| 10초 챌린지 7개 API | 승인 계약·미구현 | 앱·Python AI |

### 6.3 Billing

| Capability | 상태 | 외부 사용자 |
| --- | --- | --- |
| Phone eligibility event 수신 | 구현 | Identity workload |
| BenefitDefinition catalog | 구현 | 내부 |
| FREE_EXAM_ONCE lazy Claim·Grant | 구현 | reserve 내부 |
| append-only entitlement ledger | 구현 | 내부 |
| reserve·confirm·cancel·status | 구현 | Learning Core workload |
| Reservation expiry worker | 구현 | 내부 scheduler |
| AttemptGroup·AttemptSession projection 생성 | 구현 | confirm 내부 |
| AttemptGroup 상태 event consumer | 계약·미구현 | Learning Core workload |
| 앱용 Billing 공개 API | 범위 밖 | 앱은 직접 호출하지 않음 |
| Apple/Google 결제·구독·환불 | 후속 | 별도 제품 단계 |
| coupon·출석·추천·유료 credit | 후속 | 별도 제품 단계 |

## 7. 앱 관점 IA

```text
앱
├─ 시작·인증 [Identity]
│  ├─ 이메일 가입·로그인
│  ├─ Guest 시작
│  ├─ Firebase/SNS 로그인
│  ├─ Guest 승격·병합
│  └─ 토큰 재발급·로그아웃
├─ 내 계정 [Identity]
│  ├─ 프로필 조회
│  ├─ 동의 조회·변경
│  └─ 회원 탈퇴
├─ 모의고사 [Learning Core]
│  ├─ 시험 시작
│  ├─ 현재 문제
│  │  ├─ 문제 조회
│  │  ├─ 녹음 업로드
│  │  ├─ 제출
│  │  └─ 채점 상태
│  ├─ 문항 결과
│  │  ├─ 현재 회차
│  │  ├─ 최초 응시 비교
│  │  └─ 재응시 이력
│  ├─ 종합 결과
│  ├─ 완료 시험 이력
│  └─ 실패·지연 채점 복구
├─ 10초 챌린지 [Learning Core, 계약]
│  ├─ 오늘 진행도·문제
│  ├─ attempt·업로드·제출
│  └─ 날짜별 결과·이력
└─ 사용권 [Billing]
   └─ 앱에는 직접 메뉴/API 없음
      시험 시작 과정에서 Learning Core가 내부 처리
```

IA에서 서버 내부 식별자인 `reservationId`, `attemptGroupId`, `jobId`, S3 object key와 실제 `userId`는 화면 정보 구조에 노출하지 않는다.

## 8. 서비스 경계 평가

| 질문 | 현재 판단 |
| --- | --- |
| Identity가 시험·혜택을 소유하는가? | 아니오. 경계가 유지된다. |
| Learning Core가 결제 원장을 소유하는가? | 아니오. Billing saga는 orchestration과 internal mapping만 가진다. |
| Billing이 문제·답안·점수를 저장하는가? | 아니오. AttemptSession은 fencing projection이다. |
| 앱이 Billing을 직접 호출하는가? | 아니오. 현재 공개 Billing API가 없다. |
| Python AI에 실제 userId가 전달되는가? | 아니오. 기존 `user_id=examId` 계약을 유지한다. |
| 탈퇴·merge가 서비스 간 자동 수렴하는가? | 탈퇴 일부만 구현됐다. merge와 owner rebind는 미완성이다. |
| 시험 결과 lifecycle이 Billing AttemptGroup과 수렴하는가? | 아직 아니다. 상태 event 파이프라인이 미구현이다. |

## 9. draw.io 페이지 안내

`app-server-mentoring.drawio`는 다음 페이지로 구성한다.

1. 통합 컨셉맵
2. 시스템 아키텍처
3. Feature Map
4. 앱 IA
5. Learning Core 상세
6. Identity 상세
7. Billing 상세
8. 서비스 간 흐름과 미완성 연결

## 10. 주요 근거 위치

| 주제 | 저장소와 파일 |
| --- | --- |
| Learning Core 공개/Callback API | Learning Core `src/main/java/web/tosunsaeng/domain/exams/api/ExamRestController.java` |
| Learning Core 인증·공개 Callback | Learning Core `src/main/java/web/tosunsaeng/global/config/SecurityConfig.java` |
| 시험 생성 Billing saga | Learning Core `domain/exams/application/BillingExamCreationSaga.java` |
| 채점 orchestration | Learning Core `domain/exams/application/ExamGradingService.java` |
| Identity 공개 API | Identity `domain/auth/common/api/AuthController.java`, `domain/auth/federation/api/FirebaseExchangeController.java`, `domain/user/api/UserController.java` |
| JWT/JWKS | Identity `global/security/jwt`, Learning Core `global/config/SecurityConfig.java` |
| Identity outbox | Identity `PhoneEligibilityBindingOutbox`, `UserMergedOutbox`, `UserWithdrawnOutbox` |
| Billing 내부 API | Billing `domain/reservation/api/ReservationController.java`, `domain/eligibility/trial/api/TrialEligibilityEventController.java` |
| Billing 사용권 모델 | Billing `domain/benefit`, `domain/entitlement`, `domain/reservation`, `domain/attempt` |
| 앱 API 상태 | Learning Core `docs/contracts/FRONTEND_API_HANDOFF.md` |
| Challenge 승인 계약 | Learning Core `docs/contracts/ten-second-challenge-frontend-api.md`, `ten-second-challenge-ai-api.md` |

