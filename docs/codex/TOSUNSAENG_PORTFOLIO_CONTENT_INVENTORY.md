# 토선생 프로젝트 포트폴리오 소재 전체 목록

- 작성일: 2026-09-06
- 2026-09-07 보강: [트러블슈팅 사례집](TOSUNSAENG_TROUBLESHOOTING_CASEBOOK.md)에 채팅·WORKLOG·수정 diff를 연결한 상세 사례 16개와 추가 진단 후보 6개를 정리했다. 아래 기술 목록과 함께 읽되 사례별 발생·리뷰·진단 상태를 따른다.
- 2026-09-07 팀원 회고 보강: [회고 3편 백엔드 대조](TOSUNSAENG_TEAM_RETROSPECTIVE_BACKEND_SUPPLEMENT.md)에 운영 장애·협업·비용·콘텐츠 등 9개 소재를 추가했다. 사례집은 팀 보고 장애 TS-23·24를 포함해 총 24개 후보이며, 모두 본인이 해결한 독립 장애라는 뜻은 아니다.
- 2026-09-07 변경 이유 보강: [구현 후 수정·설계 진화 사례집](TOSUNSAENG_DESIGN_EVOLUTION_CASEBOOK.md)에 이전 방식→변경 계기→이유→수정→대가·검증으로 18개 소재를 정리했다. 구현 변경·확장 15개, 구현 전 계약 보정 후 구현 2개, 상품 계획 변경 1개를 구분했다.
- 시점 보정: 아래 저장소 기준은 최초 조사 snapshot이다. 후속 Learning Core `develop@cb5f6ee`에는 TMI-126 Challenge 구현이 포함되며, 09-07 검증 기록은 일반 Java 529개·Mongo 통합 40개·Node 90개 성공이다. 이번 보강에서 테스트를 재실행한 것은 아니다. 근거: [구현·검증 안내](TEN_SECOND_CHALLENGE_ROLLOUT.md).
- 기준 저장소: 앱용 Learning Core `develop@88b46c6`, Identity `develop@fa9843e`, Billing `develop@7138810`
- 범위: 앱·Identity·Learning Core·Billing·AI 연동과 AWS 운영 구조
- 제외: 기존 웹 POC의 상세 구현을 현재 앱 서버가 직접 구현한 것처럼 주장하는 내용

## 1. 5줄 결론

1. **[구현 사실]** 가장 강한 주제는 웹 POC를 앱용 서비스로 확장하면서 Identity·Learning Core·Billing·AI의 책임을 분리하고, 기존 외부 계약을 지킨 채 운영 안정성을 보강한 과정이다. 근거: [`APP_SERVER_SYSTEM_OVERVIEW.md`](../architecture/APP_SERVER_SYSTEM_OVERVIEW.md), [`ExamRestController.java`](../../src/main/java/web/tosunsaeng/domain/exams/api/ExamRestController.java)
2. **[경험 + 구현 사실]** “동기 채점 때문에 요청 Thread가 고갈됐다”는 장애 경험은 비동기 Job·Callback·Polling·재처리 구조로 전환한 대표 문제 해결 사례가 될 수 있다. 단, 현재 코드의 AI 접수 HTTP 호출 자체는 짧은 동기 호출이고 장시간 채점 완료를 비동기로 분리한 구조라고 정확히 표현해야 한다. 근거: [`ExamGradingService.java`](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamGradingService.java), [`GradingDispatchService.java`](../../src/main/java/web/tosunsaeng/domain/exams/application/GradingDispatchService.java)
3. **[구현 사실 + 분석]** MongoDB 선택은 단순히 “긴 문자열을 빨리 쓴다”보다 AI 결과의 중첩·가변 document, 문항/회차 단위 저장, 시험 결과 aggregate 조회와 빠른 schema 진화에 적합했다는 설명이 설득력 있다. 근거: [`ExamResult.java`](../../src/main/java/web/tosunsaeng/domain/exams/domain/entity/ExamResult.java), [`ExamRequestDTO.java`](../../src/main/java/web/tosunsaeng/domain/exams/dto/ExamRequestDTO.java)
4. **[구현 사실]** 차별화할 소재는 Billing Reservation Saga·Idempotency-Key·Transactional Outbox·lease/CAS·SigV4·UserMerged ownership migration처럼 분산 시스템의 실패와 정합성을 실제 코드로 다뤘다는 점이다. 근거: [`BillingExamCreationSaga.java`](../../src/main/java/web/tosunsaeng/domain/exams/application/BillingExamCreationSaga.java), [`AttemptGroupOutboxPublisher.java`](../../src/main/java/web/tosunsaeng/domain/exams/attemptgroup/application/AttemptGroupOutboxPublisher.java), [`UserMergedTransactionService.java`](../../src/main/java/web/tosunsaeng/domain/usermerge/application/UserMergedTransactionService.java)
5. **[주의]** 포트폴리오는 기능 나열보다 `문제 → 원인 → 선택지 → 결정 → 구현 → 검증 → 한계` 구조로 쓰고, 처리량·응답시간·장애 감소율은 측정 자료가 생기기 전에는 숫자를 만들어 쓰지 않는다.

## 2. 내가 반드시 읽어야 하는 내용

트러블슈팅부터 고르려면 [사례집](TOSUNSAENG_TROUBLESHOOTING_CASEBOOK.md)의 TS-01 빈 종합 피드백 복구, TS-08 Mongo 트랜잭션 hotfix, TS-05 CI 동시성 테스트 실패를 먼저 읽는다. TS-04 점수 집계 오류와 TS-06 환경변수 충돌은 원인을 짧고 명확하게 설명하기 좋은 후보다.

### 2.1 프로젝트를 한 문장으로 정의하는 후보

> 토선생은 실제 토익스피킹과 유사한 모의고사 응시 경험을 제공하고, 사용자의 음성 답변을 AI로 채점해 문항별 피드백과 전체 학습 방향을 제공하는 모바일 학습 서비스입니다.

백엔드 중심으로는 다음 문장이 좋다.

> 웹 POC의 도메인 지식을 재사용하면서 앱용 Identity·Learning Core·Billing·AI 서비스로 책임을 분리하고, 음성 업로드부터 비동기 채점·결과 복구·사용권 차감까지 운영 실패에 수렴하도록 발전시킨 프로젝트입니다.

### 2.2 포트폴리오의 중심 서사로 가장 추천하는 세 가지

#### A. 동기 채점 장애를 비동기 workflow로 바꾼 이야기

- **문제 경험:** 시험 Session마다 AI 채점 완료를 동기적으로 기다리자 요청 Thread가 오래 점유됐다.
- **관찰:** 동시 응시가 늘면 Servlet Thread가 고갈되고, AI 지연이나 외부 Provider 장애가 Learning Core 장애로 전파됐다.
- **결정:** 음성 제출과 채점 완료를 분리하고 `QuestionGradingJob`·`SummaryGradingJob`을 영속화했다.
- **구현:** 제출 시 결정적 Job을 만들고 AI에는 멱등 키로 요청한다. 결과는 Callback으로 받고 앱은 status API를 Polling한다.
- **복구:** duplicate·stale Callback을 구분하고 실패/timeout Job만 시험 단위로 다시 전송한다.
- **정확한 표현:** 현재 Question AI 접수 요청은 `RestTemplate.postForEntity`로 짧게 동기 호출한다. 비동기화한 대상은 “전체 채점 완료 대기”이며 완전한 non-blocking I/O라고 주장하면 안 된다.
- **근거:** [`ExamRestController.java`](../../src/main/java/web/tosunsaeng/domain/exams/api/ExamRestController.java), [`QuestionGradingJob.java`](../../src/main/java/web/tosunsaeng/domain/exams/domain/entity/QuestionGradingJob.java), [`SummaryDispatchScheduler.java`](../../src/main/java/web/tosunsaeng/domain/exams/application/SummaryDispatchScheduler.java)

#### B. 웹 POC를 활용하되 MSA 경계를 다시 만든 이야기

- **출발점:** 기존 웹 POC에서 검증한 시험·채점 흐름과 외부 AI 계약을 재사용했다.
- **문제:** 인증, 시험, AI, 과금의 변경 주기와 장애 특성이 다르며 한 서버에 결합하면 기능 확대 시 영향 범위가 커진다.
- **결정:** Identity는 계정/JWT, Learning Core는 시험/결과, Billing은 사용권/원장, AI는 채점으로 소유권을 분리했다.
- **얻은 것:** 독립 배포, 팀/기능별 변경 격리, AI 장애의 blast radius 축소, Billing 데이터의 시험 도메인 오염 방지.
- **치른 비용:** 네트워크 실패, 중복 요청, 부분 성공, 서비스 간 인증, 관측성과 데이터 정합성을 직접 설계해야 했다.
- **좋은 표현:** “서버를 나눠 안정성을 확보했다”보다 “장애 범위와 변경 책임을 격리하고, 새로 생긴 분산 실패는 Saga·Outbox·멱등성으로 다뤘다”가 정확하다.
- **근거:** [`APP_SERVER_SYSTEM_OVERVIEW.md`](../architecture/APP_SERVER_SYSTEM_OVERVIEW.md), [`BILLING_RESERVATION_SAGA_IMPLEMENTATION_PLAN.md`](BILLING_RESERVATION_SAGA_IMPLEMENTATION_PLAN.md)

#### C. AI 결과에 맞는 저장 모델을 선택한 이야기

- **데이터 특성:** transcript, strengths, weaknesses, recommendedPractice, correctionItems, spokenWordSequence, Provider 원본 Map 등 중첩 구조가 많다.
- **결정:** 시험·문항·회차 단위 결과를 MongoDB document로 저장했다.
- **장점:** 객체 구조와 저장 구조의 간극이 작고, AI Provider별 선택 필드와 schema 진화에 대응하기 쉽다.
- **조회 모델:** `examId + questionNumber + retryCount`로 한 응시 결과를 찾고, Summary는 별도 결정적 document로 관리한다.
- **주의:** “AI 응답이 길어서 MongoDB가 무조건 더 빠르다”는 근거가 부족하다. 긴 payload는 RDB도 저장할 수 있으며 실제 쓰기 성능 우위는 benchmark가 필요하다.
- **더 나은 표현:** “가변적이고 중첩된 AI 응답을 빠르게 제품화하고 문항 단위 aggregate로 읽는 모델에 맞춰 MongoDB를 선택했다. 대신 unique index, Transaction, schema validation을 별도로 강화했다.”
- **근거:** [`ExamResult.java`](../../src/main/java/web/tosunsaeng/domain/exams/domain/entity/ExamResult.java), [`ExamSummary.java`](../../src/main/java/web/tosunsaeng/domain/exams/domain/entity/ExamSummary.java), [`ExamResultRepository.java`](../../src/main/java/web/tosunsaeng/domain/exams/domain/repository/ExamResultRepository.java)

## 3. 내가 결정해야 하는 사항

| 결정 항목 | 선택지 | 권장 |
|---|---|---|
| 지원 직무 | Backend / Platform / AI Product Backend | Java Backend를 중심으로 AI·분산 시스템 경험을 보조축으로 사용 |
| 본인 기여 범위 | 직접 설계·구현 / 공동 구현 / 리뷰·의사결정 | PR·commit·Jira와 실제 역할에 맞게 항목마다 구분 |
| 대표 사례 수 | 3개 깊게 / 8개 얕게 | 본문은 3개, 나머지는 부록 |
| 장애 수치 | 기억에 의존 / 로그·부하테스트로 재측정 | 재측정 전에는 Thread 고갈 현상과 구조 변화만 기술 |
| MSA 표현 | 안정성을 위해 분리 / 책임·장애 범위 분리와 trade-off | 후자 |
| MongoDB 표현 | 긴 응답의 빠른 쓰기 / 유연한 document와 aggregate 적합성 | 후자, 성능은 측정 후 추가 |
| 구현 상태 공개 | 모두 완성처럼 표현 / 구현·조건부·계획 구분 | 반드시 상태 구분 |
| AI 활용 공개 | 숨김 / 설계·리뷰·검증 과정 설명 | AI가 작성자가 아니라 가속 도구였음을 보여주는 방식 권장 |
| 민감 정보 | 실계정·URL·ARN 포함 / 일반화 | 계정 ID, Secret, 실제 내부 URL, 사용자 데이터는 제외 |

포트폴리오를 확정하기 전에 다음 질문에 답해야 한다.

1. 이 프로젝트에서 내가 직접 소유한 범위는 앱, Learning Core, Identity, Billing, AI 중 어디까지인가?
2. 동기 방식 장애 당시 동시 사용자 수, Thread pool 크기, 평균/최대 AI 응답시간, 오류율 자료가 남아 있는가?
3. 비동기 전환 전후 응답시간·성공률·서버 CPU/Memory/Thread 지표를 재현할 수 있는가?
4. MongoDB를 선택할 당시 RDB·JSONB·Object Storage와 비교한 기록이 있는가?
5. AWS 구성은 실제 Production인지, staging 리소스를 Production 목표로 설명하는 것인지?
6. 1차 업데이트에서 실제 출시된 기능과 feature flag가 꺼진 기능은 무엇인가?

## 4. 주요 위험과 미확인 사항

### 4.1 사실로 단정하면 안 되는 내용

- “MSA를 적용해 장애가 사라졌다”: 서비스 분리는 장애 범위를 줄일 수 있지만 네트워크·정합성 장애를 새로 만든다.
- “MongoDB가 RDB보다 쓰기가 빠르다”: 이 프로젝트의 동일 workload benchmark가 없다.
- “완전 비동기·non-blocking이다”: 현재 AI 접수와 S3 download에는 blocking `RestTemplate`·AWS SDK 호출이 있다.
- “Exactly-once를 구현했다”: Outbox와 consumer 멱등성은 실질적으로 at-least-once 전달에 수렴한다.
- “Production 검증 완료”: 여러 기능은 default-off이며 실제 Lattice/Mongo replica-set/staging E2E gate가 남았던 기록이 있다.
- “AI 채점 품질이 보장된다”: 품질 지표, golden dataset, human agreement rate가 있어야 한다.

### 4.2 추가로 수집하면 포트폴리오가 강해지는 자료

- 동기/비동기 전후 API p50·p95·p99와 Thread active/queue/rejection 수
- 동시 시험 Session 1·10·50·100개 부하에서 성공률과 처리시간
- AI Callback 평균·최대 지연과 timeout/retry 성공률
- 중복 submit·중복 Callback·응답 유실을 주입한 멱등성 결과
- MongoDB document 평균/p95 크기와 주요 query explain/index hit
- S3 직접 업로드로 서버를 경유할 때 대비 줄어든 network byte·heap 사용량
- Outbox pending age, retry 횟수, dead-letter와 lease reclaim 지표
- 배포 전후 오류율, Sentry issue 수, MTTR
- AI 평가의 사람 검수 일치율, 점수 편차, no-speech·오류 문항 비율

## 5. 현재 작업과 직접 관련된 설명

### 5.1 프로젝트 소개에 넣을 수 있는 내용

#### 서비스 목적

- 실제 TOEIC Speaking Q1~Q11에 가까운 모의고사 흐름 제공
- 모바일 마이크 녹음과 S3 직접 업로드
- STT·발음 평가·LLM 기반 문항별 피드백
- 시험 전체 점수·강점·약점·추천 학습 제공
- 재답변 회차 비교와 실패한 채점 복구
- 무료 시험/이용권을 Billing과 연동
- Guest→Member 병합, 탈퇴·재가입에도 시험 소유권과 권리 정합성 유지

#### 사용자 문제

- 실제 말하기 시험을 반복 연습하기 어렵다.
- 녹음은 가능해도 구체적인 발음·문법·내용 피드백을 즉시 얻기 어렵다.
- 긴 시험 중 네트워크·앱 중단·AI 장애가 발생하면 답변과 사용권을 잃을 수 있다.
- 무료 시험과 유료 권리가 중복 지급되거나 장애 때문에 잘못 차감되면 신뢰가 무너진다.

### 5.2 기술 스택에 넣을 수 있는 내용

| 영역 | 기술 | 사용 목적 |
|---|---|---|
| Backend | Java 21, Spring Boot 3.4 | 앱용 시험·채점 orchestration |
| API/Security | Spring MVC, Spring Security, OAuth2 Resource Server | 공개 API와 workload API 분리, JWT 검증 |
| Database | MongoDB Atlas | 시험·문항·AI 결과·Job·Outbox document 저장 |
| Cache/Projection | Redis/Valkey | 시험 상태 Polling projection과 Lock |
| Object Storage | AWS S3, Presigned URL | 음성 파일의 앱 직접 업로드와 제한 시간 접근 |
| Service Runtime | Amazon ECS Fargate | Identity·Learning Core·Billing·AI 컨테이너 실행 |
| Ingress/Network | ALB, ECS Service Connect, VPC Lattice | 공개 요청과 서비스 간 비공개 통신 분리 |
| AWS Auth | IAM Task Role, SigV4 | static key 없는 AWS 접근과 내부 서비스 인증 |
| AI Integration | Python AI, STT, 발음 평가, LLM/VLM | 음성 분석·문항 피드백·전체 Summary |
| Observability | structured log, correlation ID, Sentry, metrics/tracing | 분산 요청 추적과 개인정보 정제 |
| Quality | Gradle, JUnit, MockMvc, Testcontainers, Node migration tests | 단위·계약·보안·Mongo Transaction 검증 |
| Delivery | Docker, GitHub Actions, ECR/ECS | 이미지 기반 배포와 환경별 검증 |

### 5.3 아키텍처 선택 후보 전체

#### 1) MSA와 서비스 책임 분리 — 대표 주제

**문제**

- 웹 POC의 자산은 활용해야 했지만 앱 출시 과정에서 인증, 시험, AI, 과금 요구가 동시에 커졌다.
- 한 서버에서 모두 변경하면 인증 변경이 시험에, AI 장애가 사용자 API에, 과금 변경이 학습 데이터에 영향을 줄 수 있었다.

**선택**

- Identity: 계정·인증·JWT·사용자 lifecycle
- Learning Core: 시험지·Session·음성 제출·AI 채점 orchestration·결과
- Billing: 무료/유료 권리·원장·Reservation·AttemptGroup
- AI: 음성 분석·채점·피드백 생성

**trade-off와 대응**

- 부분 성공 → Saga와 보상
- 중복 전달 → idempotency key와 inbox/outbox
- 서비스 인증 → RS256 workload JWT와 SigV4
- 데이터 소유권 → 서비스별 DB/collection과 opaque ID
- 추적 난이도 → correlation ID와 W3C trace context

**상태:** 서비스 코드와 계약은 구현됐고, 일부 운영 flag와 실제 환경 E2E는 별도 gate로 관리한다.

#### 2) 장시간 AI 채점의 비동기화 — 대표 주제

**초기 문제**

- AI가 모든 채점을 끝낼 때까지 HTTP 요청을 유지하면 시험 Session 수에 비례해 서버 Thread가 장시간 묶였다.
- 외부 AI 지연이 앱 API timeout과 서버 Thread pool 고갈로 전파됐다.

**현재 workflow**

```text
앱이 S3에 음성 업로드
→ submit
→ 결정적 QuestionGradingJob 생성·claim
→ AI가 요청을 접수
→ 앱에는 PROCESSING 반환
→ AI 결과 Callback
→ Job과 결과를 멱등 완료
→ 앱은 status Polling
→ 모든 필수 문항 완료 후 Summary Job
```

**추가 안정화**

- Job ID를 `examId + questionNumber + retryCount` 의미로 결정적으로 생성
- AI 요청에 `Idempotency-Key`
- Callback generation과 stale/duplicate 검증
- PENDING·PROCESSING·COMPLETED·FAILED 상태
- timeout과 최대 dispatch 횟수
- 실패 문항과 Summary만 재채점하는 시험 단위 복구 API
- Summary 전용 bounded ThreadPool: 기본 2 threads, queue 100

**정직하게 밝힐 한계**

- Question dispatch의 S3 download와 AI 접수 POST는 현재 blocking 호출이다.
- 장시간 “채점 계산 완료”를 분리했지만, 접수 API 자체의 timeout·bulkhead·worker 분리는 추가 발전 지점이다.

#### 3) MongoDB 기반 AI 결과 document 모델 — 대표 주제

**적합했던 이유**

- 문항·파트·Provider마다 결과 구조가 달라 nullable/선택적 field가 많다.
- transcript와 다단계 feedback, 배열, 중첩 correction item을 한 결과 document로 다룬다.
- Azure/SpeechAce 원본 응답을 `Map<String,Object>` 형태로 보존해야 했다.
- POC 이후 field 변화가 잦아 schema 진화 속도가 중요했다.

**보완한 부분**

- `(examId, questionNumber, retryCount)` 식별 의미 유지
- 별도 `ExamSession`, `QuestionGradingJob`, `SummaryGradingJob`, `ExamSummary`
- unique/partial index와 startup validator
- Mongo Transaction과 optimistic version/CAS
- migration dry-run·apply와 legacy 데이터 backfill

**trade-off**

- 자유 schema는 잘못된 문서가 들어오기 쉬우므로 validator와 contract test가 필요하다.
- 여러 aggregate의 원자 변경은 replica-set Transaction이 필요하다.
- 장문 payload가 커지면 16 MiB document 제한과 read amplification을 관찰해야 한다.

#### 4) S3 Presigned URL로 음성 업로드 경로 분리

- 앱이 Learning Core를 거치지 않고 S3에 raw audio를 PUT한다.
- Learning Core는 소유권 검증 후 5분짜리 제한 URL과 server-generated key를 발급한다.
- 장점: 대용량 음성 byte가 애플리케이션 서버 bandwidth와 heap을 점유하지 않는다.
- 보안: 앱은 AWS credential을 받지 않고 제한된 object/key/time 권한만 갖는다.
- trade-off: 업로드 성공과 submit 사이의 분리, orphan object, 이미 발급된 URL 취소 한계를 다뤄야 한다.
- 근거: [`ExamServiceImpl.java`](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamServiceImpl.java), [`S3Config.java`](../../src/main/java/web/tosunsaeng/global/config/S3Config.java)

#### 5) Redis를 진실의 원장이 아닌 상태 projection으로 사용

- 영속 Job과 결과는 MongoDB가 소유한다.
- Redis/Valkey는 앱 Polling용 전체 상태를 빠르게 제공하는 projection이다.
- Redis가 비어도 Mongo Job/result에서 상태를 다시 계산할 수 있는 구조다.
- 이것은 cache와 source of truth를 구분한 사례로 설명할 수 있다.

#### 6) 시험 생성 command의 멱등성

- 모바일 network에서 응답을 못 받은 앱이 시험 생성 요청을 재전송할 수 있다.
- optional `Idempotency-Key`를 Billing Saga flag가 켜진 환경에서 lowercase UUID v4로 검증한다.
- 같은 사용자·같은 key는 같은 `ExamCreationOperation`과 결과에 수렴한다.
- key는 user, reservation, attempt group과 다른 “한 번의 command” 식별자다.
- 근거: [`ExamCreationIdempotencyKey.java`](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamCreationIdempotencyKey.java), [`ExamCreationOperation.java`](../../src/main/java/web/tosunsaeng/domain/exams/domain/entity/ExamCreationOperation.java)

#### 7) Billing Reservation Saga로 권리 차감 정합성 유지

**문제**

- Billing에서 권리를 먼저 완전히 차감한 뒤 Learning Core Session 저장이 실패하면 사용자는 시험 없이 권리를 잃는다.
- Session을 먼저 만들고 Billing이 거절하면 권리 없는 시험이 생긴다.

**흐름**

```text
Billing reserve(hold)
→ Learning Core Session + operation Mongo Transaction commit
→ Billing confirm(consume)
→ Session IN_PROGRESS
```

**실패 처리**

- Session commit 전 실패 → cancel
- confirm 응답 불명 → Billing status 조회 후 수렴
- 응답 유실 → 같은 idempotency key replay
- 공개 API 성공 DTO는 기존 계약 유지

**근거:** [`BillingExamCreationSaga.java`](../../src/main/java/web/tosunsaeng/domain/exams/application/BillingExamCreationSaga.java), [`SigV4BillingReservationClient.java`](../../src/main/java/web/tosunsaeng/domain/exams/billing/SigV4BillingReservationClient.java)

#### 8) Transactional Outbox로 시험 상태를 Billing에 전달

- 시험이 GRADING·COMPLETED·RETAKE_AVAILABLE로 바뀔 때 Session 상태와 event를 같은 Mongo Transaction에 저장한다.
- Publisher가 Mongo에서 lease claim하고 Billing에 전송한다.
- 같은 `eventId`와 canonical payload/digest를 재사용해 retry가 새 사건처럼 보이지 않게 한다.
- 2xx는 DELIVERED, 408/425/429/5xx는 retry, 4xx 계약 오류는 DEAD_LETTER로 분류한다.
- 401/403은 `BLOCKED_AUTH`로 보존하고 global circuit과 half-open probe를 사용한다.
- lease token CAS로 여러 Fargate task의 중복 claim과 stale worker update를 방지한다.
- W3C trace context를 저장하고 publish attempt마다 새 span을 만든 뒤 SigV4를 마지막에 적용한다.
- 정확한 전달 의미는 at-least-once + idempotent consumer다.
- 근거: [`AttemptGroupEventOutbox.java`](../../src/main/java/web/tosunsaeng/domain/exams/attemptgroup/domain/AttemptGroupEventOutbox.java), [`AttemptGroupOutboxPublisher.java`](../../src/main/java/web/tosunsaeng/domain/exams/attemptgroup/application/AttemptGroupOutboxPublisher.java)

#### 9) 시험 완료를 단일 flag가 아닌 evidence로 판정

- 모든 필수 `retryCount=0` submit과 durable Job이 있어야 GRADING이다.
- 필수 feedback·유효 score·결정적 Summary가 모두 조회 가능해야 COMPLETED다.
- 재시도 소진·Summary 불가·deadline·정합성 위반은 RETAKE_AVAILABLE로 수렴한다.
- 사용자가 새로 녹음한 `retryCount>0`은 원 시험 완료 evidence를 바꾸지 않는다.
- 장점: Callback 순서와 부분 성공에 관계없이 같은 상태로 수렴한다.
- 근거: [`AttemptGroupEvidenceEvaluator.java`](../../src/main/java/web/tosunsaeng/domain/exams/attemptgroup/application/AttemptGroupEvidenceEvaluator.java), [`AttemptGroupStateCoordinator.java`](../../src/main/java/web/tosunsaeng/domain/exams/attemptgroup/application/AttemptGroupStateCoordinator.java)

#### 10) 재채점과 장애 복구를 제품 기능으로 만든 사례

- 실패하거나 timeout된 최초 응시 Job만 복구한다.
- 이미 완료·처리 중·제출 누락 문항을 구분한다.
- 새 녹음을 요구하지 않고 기존 S3 object와 Job을 활용한다.
- Summary generation attempt를 증가시키고 이전 Callback이 현재 결과를 덮지 못하게 한다.
- 근거: [`ExamRestController.java`](../../src/main/java/web/tosunsaeng/domain/exams/api/ExamRestController.java), [`ExamGradingService.java`](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamGradingService.java)

#### 11) 사용자 소유권과 JWT 경계

- 앱은 userId를 body/path/query로 보내지 않는다.
- Identity가 발급한 RS256 Access Token의 UUID `sub`를 실제 userId로 사용한다.
- 모든 사용자용 examId API에서 `ExamSession.userId`와 현재 사용자를 비교한다.
- Python AI의 기존 `user_id`는 실제 개인정보 userId가 아니라 examId를 유지한다.
- 내부 event API는 사용자 JWT와 다른 workload credential/audience를 사용한다.
- 근거: [`SecurityConfig.java`](../../src/main/java/web/tosunsaeng/global/config/SecurityConfig.java), [`CurrentUserProvider.java`](../../src/main/java/web/tosunsaeng/global/auth/CurrentUserProvider.java)

#### 12) Guest→Member 병합의 분산 데이터 소유권 이전

**문제**

- Guest 계정이 Member로 합쳐진 뒤 기존 Guest token이 살아 있거나, 시험 일부만 새 userId로 이동하면 권한 누수와 데이터 분열이 생긴다.

**구현**

- exact internal event endpoint와 전용 workload JWT
- event inbox의 영구 멱등성
- source·target guard를 결정적 순서로 획득
- Session·Result·Summary owner를 하나의 Mongo Transaction에서 이전
- source를 MERGED로 바꾸고 이후 source token을 deny
- non-terminal 시험 생성 operation이나 withdrawal 충돌은 fail-closed
- Callback과 writer도 current owner guard Transaction을 통과하도록 연결

**trade-off**

- merge 전에 이미 발급한 S3 Presigned URL은 만료 전까지 취소하기 어렵다.
- Mongo replica-set Transaction과 production 유사 성능 검증이 중요하다.
- 근거: [`UserMergedTransactionService.java`](../../src/main/java/web/tosunsaeng/domain/usermerge/application/UserMergedTransactionService.java), [`UserOwnershipGuardService.java`](../../src/main/java/web/tosunsaeng/domain/usermerge/application/UserOwnershipGuardService.java)

#### 13) 회원 탈퇴 event와 fail-closed 접근 차단

- Identity의 탈퇴를 Learning Core inbox가 수신한다.
- deny marker를 Mongo Transaction으로 기록한다.
- marker가 있으면 사용자 API를 차단한다.
- marker 조회 장애 때 허용해 버리지 않고 fail-closed 503으로 처리한다.
- Inbox TTL과 Access Token 잔여 수명을 분리한다.
- 근거: [`UserWithdrawnEventConsumerService.java`](../../src/main/java/web/tosunsaeng/domain/withdrawal/application/UserWithdrawnEventConsumerService.java), [`UserWithdrawnAccessGateFilter.java`](../../src/main/java/web/tosunsaeng/domain/withdrawal/security/UserWithdrawnAccessGateFilter.java)

#### 14) 시험지 순환 배정과 legacy 데이터 migration

- 활성 시험지를 sequence 순으로 배정한다.
- 사용자별 완료 횟수를 집계해 다음 시험지를 선택하고 전체 소진 후 순환한다.
- 중복 `mockExamId`·sequence, 빈 문항, 범위 초과를 fail-fast한다.
- legacy Session의 완료 evidence를 backfill하고 partial unique index로 active Session 중복을 막는다.
- 단순 랜덤 배정이 아니라 재현 가능한 catalog 정책과 migration 안전성을 다룬 사례다.
- 근거: [`MockExamCatalogService.java`](../../src/main/java/web/tosunsaeng/domain/exams/application/MockExamCatalogService.java), [`ExamSessionManager.java`](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamSessionManager.java), [`tmi-31-migrate-exam-assignment.js`](../../scripts/mongodb/tmi-31-migrate-exam-assignment.js)

#### 15) 관측성과 개인정보 보호

- 요청별 correlation ID와 MDC 전파
- 비동기 executor에도 MDC context 전달
- stage별 S3 download·AI POST duration log
- Sentry로 예상 밖 예외를 수집하되 event sanitizer로 header·query·body·사용자 데이터를 제거
- Outbox 상태·age·auth failure·dead-letter metric
- W3C trace context와 publish attempt span
- “로그를 많이 남겼다”보다 “장애 재현에 필요한 문맥과 개인정보 최소화를 동시에 설계했다”로 설명한다.
- 근거: [`RequestCorrelationFilter.java`](../../src/main/java/web/tosunsaeng/global/logging/RequestCorrelationFilter.java), [`MdcTaskDecorator.java`](../../src/main/java/web/tosunsaeng/global/logging/MdcTaskDecorator.java), [`SentryEventSanitizer.java`](../../src/main/java/web/tosunsaeng/global/sentry/SentryEventSanitizer.java)

#### 16) AWS credential과 최소 권한

- 코드에 static Access Key를 넣지 않고 AWS Default Credentials Provider Chain을 사용한다.
- local은 SSO/Profile, ECS는 Task Role의 임시 credential을 사용한다.
- Task Execution Role과 애플리케이션 Task Role을 구분한다.
- S3 client와 presigner가 같은 provider를 사용한다.
- 근거: [`S3Config.java`](../../src/main/java/web/tosunsaeng/global/config/S3Config.java), [`README.md`](../../README.md)

#### 17) 안전한 rollout을 위한 feature flag와 startup validation

- Billing Saga, AttemptGroup writer/publisher, withdrawal, UserMerged 같은 위험 기능은 기본 OFF다.
- 필요한 Mongo index·Transaction capability·issuer/JWKS·endpoint 설정을 기동 시 검증한다.
- publisher를 먼저 idle 활성화한 뒤 writer를 켜는 식으로 배포 순서를 문서화했다.
- legacy data는 전체 자동 backfill보다 inventory/dry-run/allowlist를 택했다.
- 포트폴리오에서는 “기능 구현”과 “운영 활성화”를 분리한 사례로 쓸 수 있다.

#### 18) 구조화 로그로 분산 채점 흐름 추적 — 독립 문제 해결 사례

**문제**

- 한 번의 시험은 HTTP 요청, S3 음성 다운로드, AI 접수, 비동기 Callback, Summary 생성까지 여러 실행 경계를 지난다.
- 일반 문장형 로그만으로는 한 사용자의 채점이 어느 단계에서 지연·실패했는지 연결해서 찾기 어려웠다.

**원인**

- 비동기 executor로 넘어갈 때 요청 문맥이 자동으로 유지되지 않는다.
- 로그마다 필드명과 표현이 다르면 검색은 가능해도 단계별 실패 건수와 소요시간을 일관되게 집계하기 어렵다.

**구현**

- 모든 HTTP 요청에 UUID `requestId`를 만들고 MDC에 저장한 뒤 응답이 끝나면 기존 MDC를 복원한다.
- `MdcTaskDecorator`로 Summary 비동기 executor에도 MDC 문맥을 복사하고 실행 후 원래 worker 문맥을 복원한다.
- 주요 로그를 `event`, `outcome`, `reason`, `stage`, `durationMs`와 `jobId`, `examId`, `questionNumber`, `retryCount`, `generationAttempt`, `dispatchAttempt` 같은 `key=value` 규격으로 통일했다.
- HTTP 완료 로그에 method·path·status·duration을 남기고, S3 다운로드와 AI POST도 단계별 소요시간과 성공·실패 원인을 구분한다.
- Sentry는 예상 밖 예외를 보조적으로 수집하되 기본 PII, request body와 민감 header가 전송되지 않도록 제한했다.

**효과**

- 동일 요청과 비동기 작업의 로그를 `requestId`로 연결하고, `event/outcome/reason` 조합으로 실패 단계와 유형을 필터링할 수 있게 됐다.
- 장애 재현이 어려워도 S3, AI dispatch, Callback, Summary 중 어디에서 멈췄는지 좁힐 수 있는 관측 기반을 만들었다.
- 정형 필드 덕분에 향후 실패율·단계별 latency·retry 성공률을 metric이나 dashboard로 집계할 수 있다.

**검증**

- `RequestCorrelationFilterTest`가 요청 안의 requestId 생성, 종료 후 제거와 외부 MDC 복원을 검증한다.
- `MdcTaskDecoratorTest`가 호출자 MDC 전달과 기존 worker MDC 복원을 검증한다.
- Sentry 단위·통합 테스트가 requestId 전달과 민감정보 정제 경계를 검증한다.
- 근거: [`RequestCorrelationFilter.java`](../../src/main/java/web/tosunsaeng/global/logging/RequestCorrelationFilter.java), [`MdcTaskDecorator.java`](../../src/main/java/web/tosunsaeng/global/logging/MdcTaskDecorator.java), [`application.yml`](../../src/main/resources/application.yml), [`RequestCorrelationFilterTest.java`](../../src/test/java/web/tosunsaeng/global/logging/RequestCorrelationFilterTest.java), [`MdcTaskDecoratorTest.java`](../../src/test/java/web/tosunsaeng/global/logging/MdcTaskDecoratorTest.java)

**한계와 정확한 표현**

- 현재 형식은 JSON encoder를 사용한 JSON log가 아니라 `key=value` 구조화 로그다.
- `requestId`는 한 애플리케이션 안의 요청·비동기 문맥 연결에 유용하지만, 모든 서비스 호출을 관통하는 완전한 distributed trace와 같지는 않다. 서비스 간 흐름은 W3C trace context와 함께 설명한다.
- MTTR 감소율이나 탐지시간 개선 수치는 아직 운영 측정 근거가 없으므로 “추적 가능성을 높였다”까지 표현한다.

#### 19) AI 오류·지연의 선택적 재시도와 stale Callback 방어 — 독립 문제 해결 사례

**문제**

- AI 연결 실패, 처리 지연 또는 Callback 유실이 생기면 일부 문항만 `PENDING`·`PROCESSING`에 남아 전체 시험 결과가 끝나지 않을 수 있었다.
- 시험 전체를 무조건 다시 보내면 이미 완료된 문항이 중복 채점되고, 늦게 도착한 과거 Summary Callback이 최신 재시도 결과를 덮을 위험이 있었다.

**원인**

- 외부 AI의 접수 성공과 실제 채점 완료는 서로 다른 시점이며 중간 응답 유실 여부를 HTTP 결과만으로 확정하기 어렵다.
- 문항별 진행 상태, dispatch 횟수와 Summary 세대를 영속적으로 구분하지 않으면 안전한 부분 재처리가 불가능하다.

**구현**

- `QuestionGradingJob`과 `SummaryGradingJob`에 상태·전송 횟수·시간 정보를 저장하고, 기본값으로 PENDING 1분·PROCESSING 3분 timeout과 최대 dispatch 3회를 둔다.
- `POST /api/v1/exams/{examId}/grading/retry`가 예상 문항 전체를 판정하되 완료 문항은 건너뛰고, 실패·timeout 문항만 기존 S3 음성으로 재전송한다.
- Job이 없을 때는 S3 object 존재 여부로 “제출됐지만 Job이 유실된 경우”와 “사용자가 제출하지 않은 경우”를 나눈다. 처리 중 문항은 재전송하지 않고 waiting으로 반환한다.
- 문항 결과가 모두 준비되면 Summary만 복구한다. `generationAttempt`는 `FAILED/FEEDBACK_GENERATION_FAILED`에 대한 사용자 retry에서만 증가하며, 같은 세대의 transport retry는 증가시키지 않는다. 과거 세대 Callback·실패 통지는 `stale_ignored`로 수렴시킨다. 근거: [복구 계약](FEEDBACK_GENERATION_RECOVERY_PLAN.md).
- 결정적 Job ID와 AI `Idempotency-Key`, `dispatchAttempt`를 사용해 중복 요청과 응답 유실에도 같은 작업으로 수렴시킨다.
- 사용자가 새로 녹음한 `retryCount>0` 문항은 최초 시험 전체 복구 대상에서 제외해 원 시험 결과와 새 응시를 섞지 않는다.

**효과**

- 사용자가 11개 문항을 전부 다시 응시하지 않고 실패한 문항과 Summary만 복구할 수 있다.
- 완료·처리 중·제출 누락·재전송 대상을 구분해 불필요한 AI 호출과 중복 결과 저장을 줄이는 구조가 됐다.
- 늦은 Callback이 최신 Summary를 덮지 못해 재시도가 오히려 결과 정합성을 깨뜨리는 문제를 방지한다.

**검증**

- `ExamGradingServiceTest`가 완료 문항 skip, timeout·failed 재전송, 처리 중 waiting, S3 object 유무, 동시 retry, 최대 시도와 Summary generation 전이를 검증한다.
- Callback 관련 테스트가 동일 generation 중복과 이전 generation의 no-op 수렴을 검증한다.
- 근거: [`ExamRestController.java`](../../src/main/java/web/tosunsaeng/domain/exams/api/ExamRestController.java), [`ExamGradingService.java`](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamGradingService.java), [`QuestionGradingJob.java`](../../src/main/java/web/tosunsaeng/domain/exams/domain/entity/QuestionGradingJob.java), [`SummaryGradingJob.java`](../../src/main/java/web/tosunsaeng/domain/exams/domain/entity/SummaryGradingJob.java), [`ExamGradingServiceTest.java`](../../src/test/java/web/tosunsaeng/domain/exams/application/ExamGradingServiceTest.java), [`application.yml`](../../src/main/resources/application.yml)

**한계와 정확한 표현**

- 문항 재채점은 현재 사용자 또는 운영 흐름이 시험 단위 복구 API를 호출해 시작하는 선택적 복구다. 모든 실패를 background worker가 자동 재전송한다고 표현하면 안 된다.
- Summary는 bounded executor에서 예약·재시도되지만, 문항 submit의 S3 download와 AI 접수 HTTP 호출 자체는 여전히 blocking이다.
- timeout 1분·3분과 최대 3회는 현재 설정 기본값이며 실제 운영 latency 분포를 수집해 조정해야 한다.

### 5.4 제품 기능 후보 전체

#### 모의고사

- 시험 Session 생성과 시험지 순환 배정
- 실제 Part별 text·audio·image·Part 4 tableContext 제공
- 문항별 준비 시간과 응답 시간 metadata
- 완료 시험 이력
- 문항별 최초/재답변 결과 비교
- 전체 Summary 조회
- 모범답안 음성 제공

#### 음성과 채점

- Presigned URL 기반 S3 직접 업로드
- 문항·회차별 object key
- STT·발음·내용·문법·유창성 피드백 결합
- 비동기 Callback과 Polling
- 문항 Job과 Summary Job
- 시험 단위 재채점 복구
- duplicate/stale Callback 방어

#### 인증과 사용자 lifecycle

- 이메일·Guest·Firebase/SNS 로그인
- Access/Refresh Token과 JWKS
- Guest→Member 승격·병합
- 탈퇴 후 downstream 접근 차단
- 전화 재가입 시 기존 무료 시험 AttemptGroup continuation

#### 사용권과 과금

- 검증 전화번호 기준 무료 시험 1회
- Claim·Grant·append-only ledger
- reserve·confirm·cancel·status lifecycle
- 시험 중단·채점 실패 때 같은 consumption 안의 replacement
- AttemptGroup 상태 동기화
- 기간제 유료 이용권은 2026-09-06 기준 계약/계획 소재이며 실제 구현과 구분해야 한다.

#### 콘텐츠/향후 기능

- LLM 문제 생성 pipeline, 모범답안, Part 2 image, Part 3~5 audio 관리
- 자동 유효성 검사 + 수동 검수
- 학습 로드맵과 무료/유료 챗봇
- 데일리 학습 콘텐츠
- 10초 챌린지는 09-07 TMI-126에서 구현·격리 통합 검증됐다. 기본 OFF이며 실제 프론트·AI·운영 연동은 별도다. 근거: [구현·검증 안내](TEN_SECOND_CHALLENGE_ROLLOUT.md).

### 5.5 문제 해결 사례 후보

아래는 초기 아키텍처·문제 해결 후보 표다. 실제 트러블슈팅 서사는 [별도 사례집](TOSUNSAENG_TROUBLESHOOTING_CASEBOOK.md)에 증상·원인 분석·수정·검증·한계와 함께 추가했다. Saga·Outbox 같은 예방 설계 자체를 운영 장애 발생 경험으로 표현하지 않는다.

| 우선 읽을 사례 | 확인된 출발점 | 설명할 핵심 |
|---|---|---|
| TS-01 빈 종합 피드백 | 채팅의 빈 partFeedback 처리 요구·코드 공백 | 실제 결과 검증, Summary 단독 재생성, generation |
| TS-08 Summary 트랜잭션 | 병합 후 리뷰의 DuplicateKey 처리 결함 | 취소된 트랜잭션을 종료한 뒤 전체 작업 재시도 |
| TS-05 CI flaky test | TooFewActualInvocations 실패 | 스케줄에 따라 달라지는 호출 횟수와 최종 불변식 분리 |
| TS-04 파트 점수 누적 | 사용자의 재답변 점수 합산 문제 제기 | retryCount=0 집계와 회귀 fixture |
| TS-06 CI 환경변수 충돌 | 테스트 step 실패 | Spring 설정 우선순위와 step별 환경 격리 |
| TS-07·16 Mongo 스크립트 | NamespaceNotFound·실제 mongosh 조기 종료 | 비동기 예외와 실행 수명주기 검증 |

| 우선순위 | 사례 | 문제 | 핵심 해결 | 포트폴리오 가치 |
|---:|---|---|---|---|
| 1 | 동기 AI 채점 Thread 고갈 | 장시간 외부 연산이 request Thread 점유 | Job·Callback·Polling·retry | 장애 원인 분석과 구조 개선 |
| 2 | Billing 시험 생성 부분 성공 | 권리 차감과 Session 생성이 다른 DB | reserve/commit/confirm Saga + idempotency | 분산 트랜잭션 설계 |
| 3 | AttemptGroup event 유실 | DB 상태 변경과 HTTP publish 사이 crash | Transactional Outbox + lease/CAS | 운영 신뢰성과 재처리 |
| 4 | Summary Transaction abort | duplicate를 catch해도 Mongo Transaction은 이미 abort | 전체 unit을 새 Transaction으로 retry | DB Transaction 의미 이해 |
| 5 | Guest merge ownership race | source token·Callback·writer와 owner migration 경합 | ownership guard + 단일 Transaction | 보안·동시성·데이터 정합성 |
| 6 | retry 점수 중복 합산 | 새 응시 회차가 원 시험 총점에 섞임 | retryCount 0 evidence 고정 | 도메인 규칙과 회귀 테스트 |
| 7 | Part 4 질문 누락 | 전용 converter가 공통 text를 빼먹음 | 최소 mapping 수정 + 3경로 contract test | API 호환성 중심 디버깅 |
| 8 | S3 static credential 위험 | 로컬 key 설정과 ECS 배포 경계 혼재 | Default Chain + SSO/Profile/Task Role | Cloud 보안 실무 |
| 9 | 탈퇴 event의 부분 반영 | 계정은 탈퇴했지만 학습 서버 접근 가능 | durable event + deny marker + fail-closed | 분산 사용자 lifecycle |
| 10 | 시험지 legacy migration | 완료/활성 상태와 sequence가 혼재 | evidence backfill·dry-run·partial unique index | 운영 데이터 이관 |
| 11 | 분산 채점 로그 단절 | 요청·S3·AI·Callback·Summary 흐름을 연결하기 어려움 | MDC requestId 전파 + `key=value` event 규격 + Sentry 정제 | 장애 분석과 관측성 설계 |
| 12 | AI 오류 뒤 일부 문항 정체 | 실패·timeout·Callback 유실로 시험 완료 불가 | 영속 Job + 선택적 retry + generation 기반 stale 방어 | 외부 시스템 장애 복구와 멱등성 |

### 5.6 협업과 개발 방식으로 쓸 수 있는 내용

- 프론트·AI·Identity·Billing 간 API 계약을 문서로 먼저 고정했다.
- 공개 API와 내부 workload API를 분리했다.
- Jira 단위로 계획→구현→review→hotfix→merge→상태 동기화를 관리했다.
- 구현 사실·계획·추론을 문서에서 구분했다.
- feature flag 기본 OFF와 rollout gate로 큰 기능을 단계적으로 활성화했다.
- 모든 변경에서 유지해야 할 URL·Method·DTO·BaseResponse·AI `user_id=examId` 계약을 fitness rule로 관리했다.
- AI 도구에는 조사·계획·구현·리뷰를 맡기되, 본인은 계획서·diff·외부 계약·테스트·위험을 검토하는 방식으로 개발했다.
- 여러 저장소를 동시에 바꾸는 대신 서비스별 책임과 후속 issue를 분리했다.
- 실제 Secret·Token·사용자 음성·전체 transcript를 문서/로그에 남기지 않는 규칙을 운영했다.

### 5.7 품질과 테스트로 쓸 수 있는 내용

- 최초 조사인 09-06에는 Java 496개 성공 기록을 확인했다. 후속 09-07 기록은 Java 529개·Mongo 통합 40개·Node 90개 성공이다. 근거: [후속 검증 기록](TEN_SECOND_CHALLENGE_ROLLOUT.md).
- API 계약: URL·Method·field·BaseResponse 회귀
- Security integration: JWT 401/403/200, issuer/audience/sub, workload chain 격리
- 멱등성: duplicate submit·Callback·event
- 동시성: duplicate key, optimistic lock, CAS, stale claim
- Transaction: rollback, unknown commit, owner migration
- Migration: Node syntax/test, dry-run/apply, index 검증
- 외부 의존성은 Mock/fake로 격리
- 09-05 `mongoIntegrationTest`는 Docker 부재로 실패했지만, 09-07에는 Docker API 호환 설정 후 격리 Mongo suite 성공 기록이 있다. 테스트 당시 상태와 운영 배포 검증을 구분한다.

### 5.8 추천 포트폴리오 목차

1. 표지: “AI 말하기 시험을 운영 가능한 분산 시스템으로 발전시키기”
2. 프로젝트 한 줄·기간·팀·본인 역할
3. 사용자 문제와 핵심 기능
4. 1차 시스템 구성도
5. 기술 선택 요약: MSA·비동기·MongoDB·S3
6. 대표 문제 해결 1: 동기 채점 Thread 고갈
7. 대표 문제 해결 2: Billing Reservation Saga
8. 대표 문제 해결 3: Outbox 또는 UserMerged ownership migration
9. API·데이터 모델·상태 머신
10. 테스트·관측·보안
11. 실패한 시도와 trade-off
12. 성과 지표
13. 남은 한계와 다음 개선

### 5.9 바로 사용할 수 있는 경력기술서 bullet 후보

아래 문장은 본인 기여가 맞는 항목만 선택한다.

- 장시간 AI 채점 완료를 HTTP 요청에서 분리하고 영속 Job·Callback·Polling 기반 workflow를 설계해, 외부 AI 지연이 애플리케이션 요청 Thread를 장시간 점유하던 구조를 개선했습니다.
- `examId + questionNumber + retryCount` 기반 결정적 Job과 idempotency key를 도입하고 duplicate/stale Callback을 수렴시켜 중복 제출과 응답 유실에도 결과 일관성을 유지했습니다.
- S3 Presigned URL을 적용해 모바일 음성 파일이 백엔드 서버를 경유하지 않고 직접 업로드되도록 구성하고, 서버의 network·memory 부담과 AWS credential 노출을 줄였습니다.
- Identity·Learning Core·Billing·AI의 도메인 책임을 분리하고, 서비스 분리로 생긴 부분 성공 문제를 Billing Reservation Saga와 Transactional Outbox로 해결했습니다.
- 시험 사용권 reserve→Session Transaction commit→confirm 흐름과 command idempotency를 구현해 장애 시 시험 없이 권리가 소모되거나 권리 없는 Session이 생성되는 문제를 방지했습니다.
- MongoDB lease/CAS 기반 Outbox publisher와 상태별 retry/dead-letter/auth circuit을 구현해 다중 인스턴스 환경의 event 전달을 at-least-once 방식으로 수렴시켰습니다.
- Guest→Member 병합 시 source/target ownership guard와 Mongo Transaction으로 Session·Result·Summary 소유권을 원자 이전하고 기존 source token을 차단했습니다.
- RS256 JWT·issuer·audience·UUID subject 검증과 workload 전용 SecurityFilterChain을 구성해 사용자 API와 내부 서비스 API의 인증 경계를 분리했습니다.
- correlation ID, structured log, W3C trace와 Sentry sanitizer를 적용해 분산 장애 추적 가능성을 높이면서 Token·사용자 음성·전체 transcript의 관측 데이터 노출을 제한했습니다.
- 요청별 correlation ID를 MDC에 저장해 비동기 executor까지 전파하고, 채점 로그를 `event/outcome/reason/stage/durationMs` 규격으로 통일해 S3 다운로드부터 AI Callback·Summary까지 실패 지점을 추적할 수 있게 했습니다.
- AI 실패·timeout 문항만 기존 S3 음성으로 다시 전송하는 시험 단위 복구 API와 Summary `generationAttempt` 검증을 구현해, 완료 문항의 중복 채점과 늦은 Callback의 최신 결과 덮어쓰기를 방지했습니다.
- 병합 후 리뷰에서 발견한 Mongo DuplicateKey 이후 트랜잭션 재사용 결함을 수정하고, Summary·Job·Session·Outbox 전체 단위를 새 트랜잭션에서 재시도하도록 보강했습니다. 근거: [사례집 TS-08](TOSUNSAENG_TROUBLESHOOTING_CASEBOOK.md).
- CI 동시성 테스트의 간헐적 실패를 스레드 스케줄별로 분석하고, 최종 불변식 검증과 결정적 충돌 테스트를 분리해 10회 반복 검증했습니다. 근거: [사례집 TS-05](TOSUNSAENG_TROUBLESHOOTING_CASEBOOK.md).
- CI 환경변수가 Spring 테스트 설정을 덮어쓰는 원인을 확인하고 테스트 step에 설정을 격리했습니다. 근거: [사례집 TS-06](TOSUNSAENG_TROUBLESHOOTING_CASEBOOK.md).
- unit·API contract·security integration·migration·Mongo transaction test를 구축했습니다. 09-07 작업 기록에는 Java 529개·Mongo 통합 40개·Node 90개 성공이 남아 있습니다. 근거: [검증 안내](TEN_SECOND_CHALLENGE_ROLLOUT.md).

### 5.10 면접에서 나올 질문과 답변 핵심

#### “왜 처음부터 MSA였나요?”

- 웹 POC의 시험/AI 자산은 활용하되 앱에서는 인증·시험·과금의 책임과 변경 주기가 달라졌다.
- AI 장애와 과금 변경의 영향 범위를 분리할 필요가 있었다.
- 단, 초기 비용과 복잡성을 인정하고 서비스 수를 무한정 늘리지 않고 4개 책임으로 제한했다.

#### “MSA로 안정성이 정말 좋아졌나요?”

- 자동으로 좋아지는 것은 아니다.
- 장점은 blast radius와 독립 배포다.
- 대신 부분 성공과 event 유실이 생겨 Saga·Outbox·멱등성·서비스 인증을 추가했다.
- 안정성 개선 수치는 부하/장애 주입 측정 결과로만 제시한다.

#### “왜 Kafka/SQS를 쓰지 않았나요?”

- 1차 규모와 운영 역량에서 broker를 추가하면 비용·관측·재처리 복잡도가 커진다.
- 채점 결과는 AI Callback+Mongo Job, 서비스 event는 Mongo Outbox+HTTP로 필요한 내구성과 멱등성을 먼저 확보했다.
- 처리량과 fan-out 요구가 임계점을 넘으면 managed queue를 재평가한다.

#### “비동기라면서 submit 안에서 AI를 호출하는데요?”

- 맞다. 현재는 AI가 요청을 접수할 때까지 짧은 blocking call이 남아 있다.
- 분리한 것은 수 초~수십 초 걸리는 전체 채점 완료 대기다.
- 다음 개선은 durable dispatcher/queue, timeout·bulkhead와 완전한 request thread 분리다.

#### “MongoDB를 왜 선택했나요?”

- transcript, 배열, 중첩 피드백과 Provider별 raw Map처럼 결과 schema가 가변적이다.
- 문항/회차 결과를 document aggregate로 읽는 패턴과 제품 초기 schema 변화에 적합했다.
- 대신 index, validator, Transaction, migration discipline을 보완했다.
- RDB보다 무조건 빠르다고 주장하지 않는다.

#### “Outbox가 중복 전달하면 어떻게 하나요?”

- eventId와 canonical payload/digest를 retry마다 유지한다.
- Publisher는 lease token CAS로 stale worker 갱신을 막는다.
- Consumer는 eventId inbox와 digest로 duplicate는 no-op, 같은 ID의 다른 payload는 conflict 처리한다.

#### “결제/권리 차감 도중 서버가 죽으면요?”

- Billing이 먼저 reserve만 하고 Session commit 후 confirm한다.
- commit 전 실패는 cancel하고 confirm 응답이 불명확하면 status 조회로 수렴한다.
- 같은 command 재전송은 idempotency key로 기존 operation을 재사용한다.

#### “Callback이 두 번 오거나 늦게 오면요?”

- 결정적 Job ID, retryCount, generation attempt를 검증한다.
- 동일 generation duplicate는 멱등 완료하고 과거 generation은 최신 결과를 덮지 않는 no-op이다.

#### “왜 JSON 로그가 아니라 key=value 구조화 로그인가요?”

- 기존 SLF4J와 CloudWatch 검색 흐름을 크게 바꾸지 않으면서 필드명을 먼저 표준화할 수 있었다.
- `event/outcome/reason/stage/durationMs`를 고정해 사람이 읽기 쉽고 검색·집계도 가능한 중간 단계를 택했다.
- 처리량과 중앙 분석 요구가 커지면 JSON encoder와 log schema validation을 다음 단계로 검토한다.

#### “비동기 작업에서도 correlation ID를 어떻게 유지했나요?”

- 요청 진입 시 MDC에 UUID requestId를 저장한다.
- Task 제출 시 `MdcTaskDecorator`가 호출자 MDC를 복사하고, 실행이 끝나면 worker에 원래 있던 MDC를 복원한다.
- Thread pool 재사용 시 이전 요청의 문맥이 다음 작업에 새지 않도록 정리까지 테스트했다.

#### “자동 재시도와 시험 단위 복구 API의 경계는 무엇인가요?”

- 현재 문항 실패 복구는 시험 단위 API가 상태를 판정해 failed·timeout 문항만 재전송하는 방식이다.
- 이미 완료된 문항은 skip하고, 아직 정상 처리 중이면 waiting, S3 object도 없으면 missing submission으로 분리한다.
- 따라서 완전 자동 retry worker라고 표현하지 않고, 외부 AI의 불확실한 상태에서 사용자가 통제할 수 있는 선택적 복구라고 설명한다.

#### “재시도 뒤 과거 Callback이 오면 어떻게 하나요?”

- 문항은 결정적 Job과 retryCount, Summary는 generationAttempt로 현재 작업을 식별한다.
- 현재 generation과 맞지 않는 Callback·실패 통지는 `stale_ignored`로 처리해 최신 결과와 Job 상태를 변경하지 않는다.

#### “가장 아쉬운 점은?”

- 장시간 채점 완료는 분리했지만 Question 접수 I/O는 blocking이다.
- 운영 Production 유사 환경의 부하·failure injection 수치가 아직 부족하다.
- AI 품질 평가용 golden dataset과 사람 평가 일치율을 체계화할 필요가 있다.

### 5.11 팀원 회고에서 추가한 운영·협업 소재

원문 3편과 코드의 자세한 대조는 [회고 보강 문서](TOSUNSAENG_TEAM_RETROSPECTIVE_BACKEND_SUPPLEMENT.md)에 있다. 아래는 선택 안내다. **팀원이 보고한 사건, 현재 백엔드 구현, 본인 기여를 구분**하며 당시 장애 이후 현재 구현이 직접 도입됐다는 인과는 별도 확인한다.

| 소재 | 백엔드 포트폴리오 연결 | 사용 조건 |
|---|---|---|
| 약 10분 피드백 대기 후 이탈 | 상태·timeout·제한된 재시도·선택적 복구 | TS-24; 스레드 고갈 사건과 동일시하지 않음 |
| 신규 사용자 Q5 음성 로드 실패 | 시험지 배정·S3 참조 무결성·조건별 재현 | TS-23; 직접 복구 역할·성공 확인 필요 |
| AI 필드 repair와 서버 작업 복구 분리 | 책임 경계·중복 호출·재시도 증폭 방지 | AI 내부 구현은 팀원 성과 |
| 무음 STT 환각 | 무응답과 기술 오류의 구분·null 계약 | 후속 Challenge no_speech 구현과 연결, 모의고사 계약과 분리 |
| 모바일 피드백 축약 | AI 출력→저장→API→앱 표시의 계약 협의 | 최대 5개 강제 등 본인 구현을 추정하지 않음 |
| 100세트 콘텐츠·Part 4 표 품질 | catalog 배정·표/질문 전달·모범답안 노출 조건 | 콘텐츠 생성과 backend 전달 책임 구분 |
| AI 비용·모델 교체 실험 | 완료 시험당 비용·지연·실패율·멱등 처리 | 실측 없는 절감률·운영 모델 전환 주장 금지 |
| 반복 문항 이탈과 로그 추적 | 구조화 로그의 활용·사용자 흐름 관측 | 자동 funnel 경보는 개선 제안 |
| 웹 검증 후 iOS 출시 | POC 재사용·앱 분리·세 파트 협업·실사용 | 출시/사용량은 팀 제품 성과 |

프로젝트 소개 수치 후보는 **2026-08-31 21시 기준 웹+앱 전체 모의고사 누적 완료 100회**, 같은 회고의 **앱 17명·완료 30회**, **8월 제작 콘텐츠 100세트**다. 서로 다른 집계이며 팀원 보고값이다. 앱 사용자 100명이나 현재 운영 지표로 바꾸면 안 된다. [8월 회고](https://velog.io/@jinjinjara1022/일단-되게에서-제대로-되게로-토선생-개발-8월-회고).

**역할 확인 후 사용할 문장:** “AI 담당자와 출력·실패 상태 계약을 조율하고, 백엔드에서는 완료 결과를 보존한 채 실패 문항과 종합 피드백을 선택적으로 복구하는 경로를 구현·검증했습니다.” 백엔드 근거는 [복구 서비스](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamGradingService.java), [테스트](../../src/test/java/web/tosunsaeng/domain/exams/application/ExamGradingServiceTest.java)다. 직접 계약 조율에 참여했는지는 본인 확인이 필요하다.

### 5.12 “처음에는 이렇게 만들었지만, 이런 이유로 바꿨다” 소재

상세 원인·전후 코드·대가·검증은 [설계 진화 사례집](TOSUNSAENG_DESIGN_EVOLUTION_CASEBOOK.md)을 읽는다. 기존 TS는 결함 해결 중심, EV는 설계·요구사항 변화 중심이며 서로 독립 사건 수로 합산하지 않는다.

| 추천 흐름 | 변경 이유 | 주의할 표현 |
|---|---|---|
| 세션 재사용 → 새 응시·폐기 → Billing command replay | 새 시작과 이어풀기, 다시 전송한 같은 요청을 구별 | 모든 요청이 항상 새 Session이라는 과거 정책을 현재 flag on에도 적용하지 않음 |
| 문항·종합 혼합 → ExamSummary 별도 저장 | 종합 결과 책임과 최신 조회를 분리 | DB 성능 장애·속도 개선을 실측 없이 주장하지 않음 |
| Part 4 이미지/고정 DTO → 원본 Map·세 API 통일 | API별 표현 불일치, 비정형 내부 키·값 보존 | 승인된 응답 삭제·대체가 있어 완전 무변경 계약이라고 하지 않음 |
| Q11 Callback → 필수 최초 결과 전체 확인 | 도착 순서와 실제 완료는 다름 | 마지막 문항 번호를 전체 완료 evidence로 혼동하지 않음 |
| 같은 Summary 재전송 → generation 분리 | 빈 결과 재사용·늦은 Callback 구별 | 사용자 새 생성과 transport retry가 다름 |
| 로그 추가 → 중복 정리·상태 이벤트·Sentry 분리 | 진단 가능성과 민감정보 최소화 | 로그만으로 운영 자동 경보가 완성됐다고 하지 않음 |

그 밖에 AWS Default Provider, JWT 운영 fail-closed, AI 주소 환경변수화, outbox, phone continuation, History 응답 보강을 포함했다. Challenge 집계·총 retry 예산은 **구현 전에 계약을 보정한 후 구현**한 사례이며, credit→기간제 pass는 **상품 계획 변경**이다. 본인이 이미 개발한 코드를 수정한 사례와 구분해서 고른다.

## 6. 부록: 상세 조사 근거와 전체 표

### 6.1 구현 상태별 포트폴리오 사용 가능성

| 소재 | 현재 근거 | 포트폴리오 사용 | 상태 표기 |
|---|---|---|---|
| 시험 생성·문제·결과 API | Controller/Service/Test | 가능 | 구현 |
| S3 Presigned upload | Service/S3 config/Test | 가능 | 구현 |
| AI Job·Callback·Polling | Job/Service/Contract test | 가능 | 구현 |
| 시험 단위 재채점 | API/Service/Test | 가능 | 구현 |
| 구조화 로그·correlation ID | Filter/MDC decorator/Sentry/Test | 가능 | 구현, 운영 개선 수치 미측정 |
| AI 선택적 retry·stale Callback 방어 | Job/Service/API/Test | 가능 | 구현, 문항 자동 worker retry는 아님 |
| Mongo AI 결과 모델 | Entity/Repository/DTO | 가능 | 구현 |
| JWT 사용자 소유권 | Security/Provider/Test | 가능 | 구현 |
| Billing Reservation Saga | Saga/client/Test/merge history | 가능 | 구현, 운영 flag/gate 별도 |
| AttemptGroup Outbox | Domain/publisher/Test/merge history | 가능 | 구현, rollout gate 별도 |
| UserWithdrawn deny | Consumer/filter/Test | 가능 | 구현, 환경 flag 별도 |
| UserMerged ownership migration | Consumer/Transaction/Test/PR #29 | 가능 | 구현, production gate 별도 |
| Identity SNS·Guest merge | Identity code/history | 본인 기여 확인 후 가능 | 구현 |
| Billing 무료권·원장 | Billing code/history | 본인 기여 확인 후 가능 | 구현 |
| 기간제 유료 결제 | Billing 계약 문서 | 본문 완성 기능으로 금지 | 계획/계약 |
| 10초 챌린지 | TMI-126 코드·격리 Mongo 검증·rollout 문서 | 구현 경험으로 가능 | 09-07 구현·검증, 기본 OFF·운영 gate 별도 |
| 학습 로드맵·챗봇 | 제품 설명 | 설계/기획으로 가능 | 구현 상태 확인 필요 |
| AI 콘텐츠 생성 pipeline | 사용자 설명 | 기획/AI 저장소 확인 후 가능 | 현재 저장소만으로 미확인 |

### 6.2 핵심 코드 근거

| 주제 | 파일 |
|---|---|
| 공개 시험 API | [`ExamRestController.java`](../../src/main/java/web/tosunsaeng/domain/exams/api/ExamRestController.java) |
| 시험 orchestration | [`ExamServiceImpl.java`](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamServiceImpl.java) |
| 채점 Job·복구 | [`ExamGradingService.java`](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamGradingService.java) |
| AI 요청 | [`GradingDispatchService.java`](../../src/main/java/web/tosunsaeng/domain/exams/application/GradingDispatchService.java) |
| Summary executor | [`GradingConfig.java`](../../src/main/java/web/tosunsaeng/global/config/GradingConfig.java) |
| 시험 Session | [`ExamSession.java`](../../src/main/java/web/tosunsaeng/domain/exams/domain/entity/ExamSession.java) |
| AI 결과 document | [`ExamResult.java`](../../src/main/java/web/tosunsaeng/domain/exams/domain/entity/ExamResult.java) |
| Billing Saga | [`BillingExamCreationSaga.java`](../../src/main/java/web/tosunsaeng/domain/exams/application/BillingExamCreationSaga.java) |
| SigV4 client | [`SigV4BillingReservationClient.java`](../../src/main/java/web/tosunsaeng/domain/exams/billing/SigV4BillingReservationClient.java) |
| AttemptGroup evidence | [`AttemptGroupEvidenceEvaluator.java`](../../src/main/java/web/tosunsaeng/domain/exams/attemptgroup/application/AttemptGroupEvidenceEvaluator.java) |
| Outbox publisher | [`AttemptGroupOutboxPublisher.java`](../../src/main/java/web/tosunsaeng/domain/exams/attemptgroup/application/AttemptGroupOutboxPublisher.java) |
| User merge | [`UserMergedTransactionService.java`](../../src/main/java/web/tosunsaeng/domain/usermerge/application/UserMergedTransactionService.java) |
| Workload JWT | [`UserMergedWorkloadJwtValidator.java`](../../src/main/java/web/tosunsaeng/domain/usermerge/security/UserMergedWorkloadJwtValidator.java) |
| 탈퇴 차단 | [`UserWithdrawnAccessGateFilter.java`](../../src/main/java/web/tosunsaeng/domain/withdrawal/security/UserWithdrawnAccessGateFilter.java) |
| 관측/개인정보 | [`SentryEventSanitizer.java`](../../src/main/java/web/tosunsaeng/global/sentry/SentryEventSanitizer.java) |
| 요청 correlation | [`RequestCorrelationFilter.java`](../../src/main/java/web/tosunsaeng/global/logging/RequestCorrelationFilter.java) |
| 비동기 MDC 전파 | [`MdcTaskDecorator.java`](../../src/main/java/web/tosunsaeng/global/logging/MdcTaskDecorator.java) |
| 시험 단위 AI 복구 | [`ExamGradingService.java`](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamGradingService.java) |
| 시스템 전체 구조 | [`APP_SERVER_SYSTEM_OVERVIEW.md`](../architecture/APP_SERVER_SYSTEM_OVERVIEW.md) |

### 6.3 성과 수치를 채우는 표

| 지표 | 변경 전 | 변경 후 | 측정 방법 |
|---|---:|---:|---|
| submit API p95 | 미측정 | 미측정 | 부하 테스트 + APM |
| active request threads | 미측정 | 미측정 | Tomcat metric |
| AI 완료 평균 시간 | 미측정 | 미측정 | Job processingStartedAt→completedAt |
| 중복 submit 결과 수 | 미측정 | 기대 1 | 동일 key 동시 요청 test |
| Callback retry 성공률 | 미측정 | 미측정 | failure injection |
| S3 업로드 시 서버 ingress byte | 서버 경유 기준 필요 | direct upload | ALB/Container network metric |
| Outbox p95 delivery age | 해당 없음 | 미측정 | outbox age metric |
| 채점 복구 성공률 | 해당 없음 | 미측정 | retry API 운영 log |
| 전체 기본 Java test | 09-06 기록 496 | 09-07 기록 529 pass | 날짜별 WORKLOG·검증 안내, 이번 재실행 아님 |
| AI-human 평가 일치율 | 미측정 | 미측정 | golden dataset |

### 6.4 작성 원칙

- 내가 직접 구현하지 않은 기능은 “팀에서 구현” 또는 “공동 설계”로 쓴다.
- 계획 문서만 있는 기능은 “설계했다”라고 쓰고 “구현했다”라고 쓰지 않는다.
- staging 검증 전 feature는 “production 운영”으로 표현하지 않는다.
- 오류·지연·Thread 고갈은 재현 자료가 없으면 현상과 원인 분석까지만 쓴다.
- 기술 선택에는 얻은 장점뿐 아니라 복잡성·비용·남은 한계를 함께 쓴다.
- 포트폴리오에는 실제 AWS 계정, ARN, Secret, Token, 내부 endpoint, 사용자 음성·전체 transcript를 넣지 않는다.
