# 토선생 앱 서버 컨벤션과 Code Smell 조사

## 1. 조사 방법

세 저장소의 운영 코드, 테스트, `AGENTS.md`, API Controller, Security 설정, Mongo entity/repository, 외부 client, 계약·ADR·CURRENT_STATE를 비교했다.

Code smell은 곧바로 버그라는 뜻이 아니다. 다음 기준으로 우선순위를 정했다.

- 높음: production 활성화나 보안·정합성 전에 닫아야 하는 경계
- 중간: 기능 추가 비용과 회귀 위험을 계속 키우는 구조
- 낮음: 읽기·검색·일관성을 저해하지만 즉시 장애 가능성은 낮음

## 2. 규모 스냅샷

| 항목 | Learning Core | Identity | Billing |
| --- | ---: | ---: | ---: |
| main Java 파일 | 121 | 306 | 82 |
| main Java LOC | 약 10,952 | 약 21,661 | 약 6,083 |
| test Java 파일 | 61 | 115 | 16 |
| Mongo `@Document` type | 11 | 14 | 13 |
| public/top-level record 사용 파일 | 10 | 59 | 18 |
| `@RequiredArgsConstructor` 사용 파일 | 11 | 19 | 0 |
| Lombok model annotation 사용 파일 | 24 | 3 | 0 |
| 명시적 `toString()` redaction 파일 | 0 | 58 | 18 |
| `Map<String,Object>` 사용 파일 | 9 | 3 | 0 |

숫자는 코드 품질 점수가 아니라 저장소별 스타일과 성장 단계의 차이를 보여주는 관찰값이다.

## 3. 코드에서 확인되는 공통 컨벤션

### 3.1 도메인 경계

- Identity는 계정·인증·사용자 lifecycle만 소유한다.
- Learning Core는 시험·문항·음성·채점 결과만 소유한다.
- Billing은 혜택·사용권·원장·Reservation만 소유한다.
- 다른 서비스 aggregate를 그대로 복제하지 않고 필요한 식별자나 projection만 저장한다.
- 실제 사용자 ID는 UUID 문자열이고 클라이언트가 body/path/query로 보내지 않는다.
- Python AI의 `user_id`는 실제 userId가 아니라 기존 `examId`다.

평가: 가장 중요한 bounded context 규칙은 잘 지켜지고 있다.

### 3.2 Controller와 application 경계

- Controller는 HTTP 입력을 application service에 전달한다.
- Identity와 Billing은 Request/Response record를 비교적 작게 유지한다.
- 내부 workload API는 앱용 `BaseResponse`를 사용하지 않고 204 또는 내부 DTO를 사용한다.
- 앱 공개 API는 각 서비스의 기존 `BaseResponse` 구조를 유지한다.
- Billing은 raw HTTP body를 크기 제한 후 strict decoder로 넘긴다.

평가: Identity·Billing은 경계가 선명하다. Learning Core는 사용자 API와 AI Callback이 한 Controller에 함께 있어 경계가 흐리다.

### 3.3 인증과 소유권

- Identity만 사용자 Access Token을 발급한다.
- JWT는 RS256, `kid`, JWKS, issuer, audience, timestamp, UUID `sub`를 검증한다.
- Learning Core는 매 요청 Identity introspection 없이 JWT를 로컬 검증한다.
- 사용자용 `examId` API는 `ExamSession.userId`와 현재 userId를 비교한다.
- local/test Legacy 모드와 staging/prod JWT 모드를 분리한다.
- workload credential은 사용자 JWT와 분리하려고 한다.

평가: 사용자 인증·소유권 원칙은 좋다. 서비스 간 인증 방식은 현재 전환 중이라 실제 연결 기준을 하나로 닫아야 한다.

### 3.4 멱등성과 분산 정합성

- 사용자 동작은 `Idempotency-Key`/operation ID로 식별한다.
- Billing command는 canonical payload hash와 unique index로 동일 key의 다른 payload를 거절한다.
- Learning Core 시험 생성은 durable `ExamCreationOperation` 상태 머신으로 reserve→Session commit→confirm을 수렴시킨다.
- 채점 Job ID와 결과 ID를 결정적으로 생성한다.
- Callback은 insert/unique key와 generation 검증으로 duplicate·stale 처리를 한다.
- Identity publisher는 outbox claim·lease·retry·dead-letter 구조를 사용한다.
- inbound event는 inbox·digest·revision 또는 상태 fencing을 사용한다.

평가: 세 서비스가 가장 잘 수렴한 영역이다. 메시지 브로커가 없어도 현재 규모에 맞는 durable pattern을 사용한다.

### 3.5 MongoDB

- 여러 document 정합성이 필요한 경로는 Mongo Transaction을 사용한다.
- unique index와 duplicate-key 수렴을 사전 조회보다 최종 경계로 사용한다.
- staging/prod에서 필수 index·transaction capability를 startup에 검증한다.
- TTL cleanup과 business expiry를 구분한다.
- 운영 중 index를 임의 drop/recreate하지 않고 migration·initializer를 구분한다.

평가: 강한 편이다. Learning Core의 여러 독립 index validator/probe는 기능 확장 시 공통 framework가 필요한지 관찰할 시점이다.

### 3.6 외부 payload와 보안

- Secret, Token, raw phone, 음성, transcript 전체와 provider 원문을 로그에 남기지 않는다.
- Identity와 Billing의 민감 record는 `toString()`을 redaction한다.
- Billing internal decoder는 size, content type, duplicate field, unknown field, trailing token, scalar coercion을 엄격히 검증한다.
- Learning Core의 Billing client도 strict ObjectMapper와 16 KiB 응답 상한을 사용한다.

평가: 신규 경계는 강하지만 Learning Core의 기존 AI Callback 경계는 이 수준에 미치지 못한다.

### 3.7 테스트

- 외부 MongoDB, S3, Redis, AI, Firebase, AWS, Store provider를 기본 단위 테스트에서 직접 호출하지 않는다.
- API URL·Method·DTO와 보안 401/403 contract test를 둔다.
- Mongo Transaction·index·동시성은 필요한 경우 replica-set Testcontainers 계획/테스트를 둔다.
- 실제 운영 연결은 staging E2E gate로 분리한다.

평가: 회귀 테스트 문화는 강하다. 세 저장소를 함께 검증하는 consumer-driven contract/E2E는 아직 약하다.

### 3.8 Feature flag와 startup fail-closed

- 큰 연동은 기본 off로 두고 설정·index·transaction capability를 함께 검증한다.
- local/test fake 또는 Legacy 경로와 staging/prod 경로를 분리한다.
- 지원하지 않는 설정 조합은 startup을 실패시킨다.

평가: 출시 안전성에 적합하다. 다만 flag가 오래 남으면 두 실행 경로의 유지 비용이 커지므로 제거 기준이 필요하다.

## 4. 저장소별 사실상 컨벤션

| 관점 | Learning Core | Identity | Billing |
| --- | --- | --- | --- |
| 들여쓰기 | 4 spaces 중심 | tab 중심 | 4 spaces 중심 |
| model style | Lombok class·중첩 DTO | record+명시적 domain class, Lombok 최소 | record+명시적 domain class, Lombok 없음 |
| service 구성 | 큰 facade/orchestrator 중심 | 작은 use case·port·worker 중심 | vertical slice command/service 중심 |
| repository | Spring Data interface 중심 | Spring Data+custom interface/Impl | concrete MongoTemplate repository |
| transaction | `@Transactional`과 TransactionOperations 혼용 | `@Transactional` 및 Transaction Service | `MongoTransactionExecutor` 명시 사용 |
| 외부 HTTP | RestTemplate, JDK HttpClient, AWS signer | JDK HttpClient adapter | inbound only |
| event | consumer 일부 | outbox/publisher 다수 | inbox/consumer와 projection |
| 응답 | `BaseResponse`, `COMMON_200` 계열 | `BaseResponse`, `SUCCESS` | 내부 API는 wrapper 없음 |

이 차이 자체는 오류가 아니다. 문제는 팀이 공통 규칙이라고 착각하거나 코드를 서비스 사이에 복사할 때 발생한다.

## 5. Code Smell과 구조 위험

### C-01. Learning Core AI Callback 신뢰 경계가 공개 경로에 머물러 있음

- 우선순위: 높음
- 근거:
  - `SecurityConfig.PUBLIC_ENDPOINTS`가 `/api/v1/exams/callback/**`를 `permitAll` 처리한다.
  - Feedback, SpeechAce, Azure Callback이 사용자 API와 같은 `ExamRestController`에 있다.
  - Azure Callback은 `Map<String,Object>`를 받아 cast하고 원본 payload를 Mongo에 저장한다.
- 영향:
  - 네트워크에서 endpoint에 도달 가능한 주체가 Callback을 위조하거나 큰/비정형 payload를 보낼 수 있다.
  - Billing의 strict internal boundary와 보안 성숙도가 다르다.
- 권고:
  - 기존 JSON·URL을 바꾸지 않는 범위에서 AI service credential, payload size 상한, strict decoder와 callback 전용 security chain을 설계한다.
  - 사용자 API Controller와 Callback Controller를 내부 코드에서 분리한다.
  - 실제 계약 변경이 필요하면 Python AI와 배포 순서를 먼저 합의한다.

### C-02. Identity→Billing workload transport가 목표 계약과 다름

- 우선순위: 높음, Billing production 연동 gate
- 근거:
  - Identity `JdkPhoneEligibilityBindingDeliveryAdapter`는 Bearer workload JWT를 보낸다.
  - Billing 운영 ingress의 승인 모드는 VPC Lattice `AWS_IAM`이며 Lattice에서 SigV4를 검증한다.
  - Learning Core→Billing은 이미 SigV4 client를 사용한다.
- 영향:
  - 코드가 각각 구현되어 있어도 phone eligibility event E2E가 운영 경로에서 연결되지 않는다.
- 권고:
  - Identity delivery port 뒤에 AWS SDK v2 SigV4 adapter를 추가하고 기존 JWT transport의 폐기/로컬 용도를 결정한다.
  - route별 task role 권한, timeout, retry와 staging E2E를 contract test와 함께 닫는다.

### C-03. AttemptGroup 상태 수렴 파이프라인이 없음

- 우선순위: 높음, Billing saga 활성화 gate
- 근거:
  - Learning Core에 AttemptGroup 상태 outbox/publisher가 없다.
  - Billing에 `/internal/v1/attempt-group-events` consumer가 없다.
  - Billing은 confirm에서 AttemptGroup을 `OPEN`으로 만들지만 시험의 `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE`을 아직 받지 못한다.
- 영향:
  - production flag를 켜면 완료/재응시 정책이 Billing projection에 끝까지 수렴하지 않는다.
- 권고:
  - 승인된 순서대로 Billing consumer 선배포 → Learning Core local Transaction outbox → SigV4 publisher → staging E2E로 진행한다.

### C-04. Learning Core 시험 application 계층의 책임 집중

- 우선순위: 중간
- 근거:
  - `ExamGradingService` 약 1,130 LOC
  - `ExamServiceImpl` 약 835 LOC
  - `BillingExamCreationSaga` 약 573 LOC
  - `ExamConverter` 약 388 LOC
  - `ExamResponseDTO` 약 307 LOC
- 영향:
  - 채점 submission, Callback, 결과 조회, Summary, 복구와 상태 projection 변경이 서로 넓은 회귀 범위를 만든다.
  - 10초 챌린지를 기존 Exam 계층에 얹을 유혹이 커진다.
- 권고:
  - 외부 API 계약을 유지하면서 내부를 `exam-session`, `question-grading`, `summary-grading`, `exam-read`, `exam-creation-saga` capability로 분리한다.
  - 먼저 public method와 상태 소유권을 표로 고정하고 facade는 얇게 유지한다.
  - 클래스 길이 자체보다 변경 이유가 둘 이상인지와 transaction boundary를 기준으로 분리한다.

### C-05. Learning Core 시간 타입 혼재

- 우선순위: 중간
- 근거:
  - `ExamSession.createdAt/completedAt`은 `LocalDateTime`이다.
  - 신규 Job, Operation, Billing, Identity aggregate는 `Instant`를 사용한다.
  - saga commit에서 `Instant→LocalDateTime` 변환이 필요하다.
- 영향:
  - timezone이 값에 내장되지 않아 환경 설정과 migration에서 해석 오류가 생길 수 있다.
- 권고:
  - 신규 durable timestamp는 `Instant`를 기본값으로 한다.
  - 기존 Session field는 API/DB migration과 historical timezone을 확인한 뒤 expand-migrate-contract로 전환한다.
  - 단순 일괄 타입 변경은 하지 않는다.

### C-06. 서비스 간 코드 스타일과 type 철학의 편차

- 우선순위: 중간
- 근거:
  - Identity는 tabs, Learning/Billing은 spaces를 사용한다.
  - Learning은 Lombok과 중첩 DTO가 많고 Billing은 Lombok을 사용하지 않는다.
  - Repository, Config/Configuration, Result/Response 의미가 다르다.
- 영향:
  - 팀원이 저장소를 오갈 때 리뷰 기준이 바뀌고 공통 코드 복사 시 혼합 스타일이 생긴다.
- 권고:
  - 전체 코드를 포맷하지 말고 각 저장소 `.editorconfig`와 신규 파일 규칙을 고정한다.
  - 공통 네이밍 사전과 boundary 규칙만 통일하고 구현 스타일은 저장소 단위로 유지한다.

### C-07. 계약 문서 내부의 상태 drift

- 우선순위: 중간
- 근거:
  - `FRONTEND_API_HANDOFF.md` 상단 표는 Learning Core Billing saga를 미구현으로 표시한다.
  - 같은 문서 후반은 TMI-116 구현 완료와 feature flag 기본 off를 정확히 설명한다.
  - CURRENT_STATE에는 과거 계획과 최신 상태가 함께 길게 누적되어 첫 진입점이 불명확한 경우가 있다.
- 영향:
  - 프론트·멘토·신규 개발자가 같은 문서에서 서로 다른 결론을 얻는다.
- 권고:
  - 문서 첫 화면에 `구현/활성화/배포`를 별도 열로 둔다.
  - append-only WORKLOG와 현재 사실의 CURRENT_STATE 역할을 구분하고, CURRENT_STATE 상단 요약을 source of truth로 유지한다.

### C-08. Learning Core의 비정형 Map payload

- 우선순위: 중간
- 근거:
  - Azure raw Callback, SpeechAce data, Part 4 table context 등에 `Map<String,Object>`가 사용된다.
- 영향:
  - cast 실패가 runtime에 나타나고 schema drift가 compile/contract test에서 드러나지 않는다.
- 권고:
  - 외부 계약이 실제로 비정형인 Azure raw body는 최소 envelope DTO+bounded raw subtree로 제한한다.
  - catalog의 `tableContext`는 승인된 schema가 생길 때 versioned value object로 전환한다.
  - provider 원문 보존 필요성과 개인정보·보존기간을 별도로 검토한다.

### C-09. Identity `domain.auth`의 탐색 비용

- 우선순위: 중간
- 근거:
  - local, session, Firebase federation, phone identity, registration, merge가 큰 auth subtree에 공존한다.
  - Identity CURRENT_STATE도 horizontal layer와 capability 탐색 비용을 이미 인식하고 있다.
- 영향:
  - Firebase/phone/merge 변경이 어떤 package에 속하는지 판단 비용이 커진다.
- 권고:
  - 최상위 bounded context를 늘리기보다 `auth/local`, `auth/session`, `auth/firebase`, `auth/phoneidentity` capability 내부에 application/domain/infrastructure를 모으는 vertical slice를 점진 적용한다.

### C-10. 공통 BaseResponse 모양은 같지만 의미가 다름

- 우선순위: 낮음~중간
- 근거:
  - Identity 일반 성공 code는 `SUCCESS`, Learning Core는 `COMMON_200`이다.
  - null `result` 직렬화도 서비스별 차이가 있다.
  - Billing internal API는 의도적으로 wrapper를 쓰지 않는다.
- 영향:
  - 앱 공통 client가 HTTP status·code·null handling을 서비스별로 알아야 한다.
- 권고:
  - 기존 code를 일괄 변경하지 않는다.
  - 앱 SDK/프론트 공통 layer에 서비스별 contract test와 normalization을 둔다.
  - 신규 서비스의 성공 code 정책만 별도 ADR로 정한다.

### C-11. 주석과 이름에 과거 수정 흔적이 남음

- 우선순위: 낮음
- 근거:
  - Learning Core Controller/Entity에 `🌟 [수정]`, `새로 추가된` 같은 시간 의존 주석이 남아 있다.
- 영향:
  - 현재 불변식보다 변경 이력을 설명해 코드 독해를 방해한다.
- 권고:
  - 해당 코드를 다음에 수정할 때 현재 의미를 설명하는 주석으로 바꾸고 관련 없는 일괄 정리는 하지 않는다.

### C-12. Service interface 기준이 저장소마다 다름

- 우선순위: 낮음
- 근거:
  - Learning Core는 단일 `ExamServiceImpl implements ExamService` facade를 사용한다.
  - Identity는 disabled/enabled 구현 교체가 있는 Firebase UseCase에 interface를 쓰고 단일 Service에는 만들지 않는다.
  - Billing은 concrete application service를 사용한다.
- 권고:
  - 팀 공통 기준을 Identity 방식에 가깝게 둔다. 실제 대체 구현, 외부 port 또는 테스트 경계가 있을 때만 interface를 만든다.
  - 기존 `ExamService`는 대규모 rename보다 facade 축소와 capability 분리를 우선한다.

## 6. 좋은 구조로 유지해야 할 패턴

다음은 리팩터링 과정에서 잃으면 안 된다.

- `examId→userId`와 모든 사용자용 시험 API의 소유권 검증
- AI `user_id=examId`, 실제 userId 비전송
- Billing reserve→Session local commit→confirm 순서
- durable operation/command/job과 같은-key replay
- Mongo unique index, Transaction, optimistic/CAS와 duplicate 수렴
- Identity outbox와 Billing/Learning inbox의 at-least-once 멱등 처리
- TrialEligibility와 Claim·Grant 발급의 분리
- append-only ledger와 allocation 추적
- raw phone·Token·음성·transcript 전체 로그 금지
- feature flag 기본 off와 startup fail-closed
- internal API와 앱 `BaseResponse` 경계 분리

## 7. 우선순위별 개선 제안

### 출시/활성화 전

1. Identity→Billing SigV4 transport와 route 권한을 정렬한다.
2. AttemptGroup consumer/outbox/publisher를 consumer-first로 구현한다.
3. Billing saga Mongo migration·Lattice·failure injection·staging E2E를 완료한다.
4. AI Callback 인증·size/strict decode 개선의 계약과 배포 순서를 합의한다.
5. FRONTEND_API_HANDOFF 상단 상태 drift를 정리한다.

### 다음 기능 개발 전

1. Learning Core 내부 capability 경계와 public facade를 문서화한다.
2. 10초 챌린지는 기존 Exam aggregate·retryCount·Callback을 재사용하지 않는 별도 package로 시작한다.
3. 세 저장소에 신규 파일용 formatter/editorconfig와 네이밍 사전을 적용한다.
4. 서비스 간 consumer-driven contract test를 추가한다.

### 점진 개선

1. Learning Core timestamp를 `Instant`로 수렴하는 migration을 설계한다.
2. raw Map envelope를 typed boundary로 축소한다.
3. Identity auth capability package를 vertical slice로 재배치한다.
4. tracing과 문서 freshness를 자동 검증하는 fitness function을 둔다.

