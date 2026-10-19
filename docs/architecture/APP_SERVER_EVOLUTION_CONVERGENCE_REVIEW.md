# 토선생 앱 서버 진화수렴·사실상 표준 비교

## 1. 평가 관점

여기서 진화수렴은 최신 유행 기술을 많이 쓰는 상태가 아니다. 제품이 커질수록 다음 성질을 안정적으로 얻는 방향을 뜻한다.

- 변경 범위가 서비스·capability 경계 안에 머문다.
- 응답 유실, 중복 요청, 재시작과 순서 역전에도 같은 결과로 수렴한다.
- 외부 계약과 내부 모델이 분리된다.
- 민감 정보와 권한 경계가 명시적이다.
- 운영 환경의 index, transaction, credential, network 조건을 기동·테스트에서 검증한다.
- 새 기능을 추가해도 기존 계약을 깨지 않는 fitness function이 있다.

비교 상태는 다음과 같다.

- 수렴: 현재 방향이 대표적인 실무 패턴과 잘 맞는다.
- 부분 수렴: 기반은 있으나 실행 경로나 운영 연결이 덜 닫혔다.
- 의도된 차이: 현재 규모·호환성 때문에 표준을 단순화한 합리적 선택이다.
- 간극: 다음 성장 단계 전에 보완할 필요가 있다.

## 2. 현재 기술과 컨셉의 MECE 분류

| 분류 | 현재 기술·컨셉 |
| --- | --- |
| Runtime | Java 21, Spring Boot 3.4.2, Gradle Groovy |
| API | Spring MVC, JSON REST, OpenAPI, 앱 `BaseResponse`, internal raw DTO/204 |
| 인증 | Spring Security, OAuth2 Resource Server, RS256 JWT, JWKS, Firebase Admin |
| 서비스 인증 | workload JWT 일부, AWS SigV4·VPC Lattice 목표/일부 구현 |
| 데이터 | 서비스별 MongoDB, Spring Data/MongoTemplate, Transaction, unique/TTL/partial index |
| Cache·projection | Learning Core Redis 상태 projection |
| Object storage | AWS S3 Presigned URL, server-side audio download |
| 외부 AI | Python AI multipart/JSON 요청, 비동기 Callback, polling |
| 분산 정합성 | Saga, durable Operation/Command/Job, idempotency key, outbox/inbox, CAS |
| 비동기 실행 | Spring Scheduler/Worker/Publisher, Mongo durable queue 패턴 |
| 관측성 | structured logging, Micrometer, Actuator, Sentry sanitizer |
| 배포·운영 | ECS, task role, feature flag, startup validation, migration script, staging E2E gate |
| 테스트 | JUnit, Mockito, Spring Security test, contract test, 일부 replica-set Testcontainers |

## 3. 영역별 사실상 표준과 현재 차이

### 3.1 서비스 경계와 모듈 구조

| 항목 | 대표적인 수렴 방향 | 현재 상태 | 판단 |
| --- | --- | --- | --- |
| 서비스 분리 | business capability와 data ownership 기준 | Identity·Learning·Billing 경계가 명확 | 수렴 |
| 서비스 내부 | modular monolith 또는 vertical slice | Identity/Billing은 capability가 비교적 분명, Learning 시험 계층은 큰 facade에 집중 | 부분 수렴 |
| 공유 모델 | wire contract만 공유하고 aggregate 복제 금지 | 각 서비스가 필요한 ID/projection만 저장 | 수렴 |
| 서비스 수 | 독립 배포 가치가 있을 때만 분리 | 현재 세 서비스는 보안·데이터·변경 이유가 다름 | 적정 |

왜 차이가 있는가:

- Learning Core는 기존 POC 계약을 보존하면서 채점 복구와 Billing saga를 점진 추가해 application class에 책임이 누적됐다.
- Identity와 Billing은 비교적 최근에 vertical slice·outbox·strict boundary를 기준으로 설계됐다.

권고:

- 새로운 microservice를 더 만들기보다 Learning Core 내부 모듈화를 먼저 한다.
- 10초 챌린지는 같은 배포 단위 안의 별도 domain package로 시작한다.

### 3.2 인증·인가와 Zero Trust

| 항목 | 대표적인 수렴 방향 | 현재 상태 | 판단 |
| --- | --- | --- | --- |
| 사용자 인증 | OIDC/OAuth2, asymmetric JWT, short-lived token | RS256, JWKS, issuer/audience/sub 검증 | 수렴 |
| token 발급자 | Identity 단일 소유 | Identity만 Private Key·발급 책임 보유 | 수렴 |
| resource server | local signature 검증, introspection 최소화 | Learning Core가 JWKS로 로컬 검증 | 수렴 |
| 서비스 인증 | workload identity, mTLS 또는 cloud IAM | Learning→Billing SigV4 구현, Identity→Billing은 JWT adapter 잔존 | 부분 수렴 |
| Callback 인증 | sender authentication+replay/idempotency | idempotency는 강하지만 AI callback 경로는 permitAll | 간극 |

대표 트렌드는 사용자 JWT를 서비스 간 credential로 재사용하지 않고 cloud workload identity나 mTLS로 분리하는 것이다. 현재 AWS ECS·Lattice 환경에서는 SigV4가 합리적이다.

### 3.3 데이터 소유권과 저장 모델

| 항목 | 대표적인 수렴 방향 | 현재 상태 | 판단 |
| --- | --- | --- | --- |
| database per service | 서비스별 write ownership | 세 서비스가 Mongo aggregate를 분리 | 수렴 |
| aggregate projection | 필요한 최소 정보만 복제 | Billing AttemptSession, Learning deny marker | 수렴 |
| schema 강제 | validation+index+migration+startup check | Billing과 신규 Learning 기능에서 강함 | 수렴 |
| 시간 | UTC `Instant` 저장, 표현 시 timezone 변환 | Identity/Billing/신규 Job은 Instant, ExamSession은 LocalDateTime | 부분 수렴 |
| 비정형 document | versioned schema 또는 bounded raw payload | Learning의 Azure/table Map이 넓음 | 간극 |

MongoDB 선택은 현재 aggregate·document·개발 속도에 맞는다. 관계형 DB로 전환해야 진화한다는 근거는 없다. 중요한 것은 unique/index/transaction/migration discipline이며 현재 방향이 좋다.

### 3.4 분산 Transaction과 멱등성

| 항목 | 대표적인 수렴 방향 | 현재 상태 | 판단 |
| --- | --- | --- | --- |
| cross-service transaction | Saga+durable state+compensation | ExamCreationOperation과 Billing Reservation | 수렴 |
| duplicate command | idempotency key+payload hash+snapshot replay | 양 서비스에 구현 | 수렴 |
| unknown commit | reread/reconciliation, 섣부른 보상 금지 | TMI-116 보완으로 반영 | 수렴 |
| local consistency | Transaction+unique index+CAS | 세 서비스 핵심 흐름에 적용 | 수렴 |
| background recovery | scheduler/retry/dead-letter/reconciliation | 일부 구현, cross-service background reconciliation은 후속 | 부분 수렴 |

Kafka나 workflow engine이 없어도 현재 규모에서는 Mongo durable operation으로 충분하다. 상태 수가 더 늘고 장시간 orchestration이 여러 서비스로 확장될 때 Temporal 같은 durable workflow 도입을 비교할 수 있지만 지금 선행 도입할 이유는 약하다.

### 3.5 이벤트와 비동기 처리

| 항목 | 대표적인 수렴 방향 | 현재 상태 | 판단 |
| --- | --- | --- | --- |
| producer | transactional outbox | Identity에 적용 | 수렴 |
| consumer | inbox+idempotency+state fencing | Billing eligibility, Learning withdrawal에 적용 | 수렴 |
| delivery | at-least-once+retry+DLQ | Identity publisher에 적용 | 수렴 |
| event schema | version+strict decode+size bound | 신규 Billing 계약은 강함 | 수렴 |
| 전체 lifecycle | 양 끝 producer/consumer가 함께 운영 | AttemptGroup과 UserMerged가 미완성 | 간극 |
| broker | 규모·fanout·throughput이 요구할 때 도입 | HTTP push+Mongo outbox | 의도된 차이 |

지금 Kafka/SQS를 넣는 것보다 미완성 event chain을 닫고 event contract test·replay 운영을 만드는 것이 우선이다.

### 3.6 API와 계약 관리

| 항목 | 대표적인 수렴 방향 | 현재 상태 | 판단 |
| --- | --- | --- | --- |
| API version | 명시적 `/api/v1` | 공개·내부 모두 적용 | 수렴 |
| compatibility | additive change, consumer contract test | AGENTS와 contract test로 강하게 보호 | 수렴 |
| public/internal 분리 | auth·wrapper·DTO 정책 분리 | Billing은 명확, Learning user/callback Controller는 혼재 | 부분 수렴 |
| error code | 안정 code+HTTP status | 서비스별 안정 code 존재 | 수렴 |
| 공통 응답 | client convenience와 서비스 독립성 균형 | 모양은 같고 code/null 의미가 다름 | 의도된 차이·문서 필요 |
| 문서 freshness | generated spec+handbook 상태 자동 검증 | OpenAPI는 있으나 handoff 상단 drift 존재 | 간극 |

공통 DTO library를 만들어 세 서비스를 compile-time 결합하는 방식은 권장하지 않는다. OpenAPI/event schema와 consumer-driven test로 계약을 공유하는 편이 낫다.

### 3.7 Resilience와 운영 안정성

| 항목 | 대표적인 수렴 방향 | 현재 상태 | 판단 |
| --- | --- | --- | --- |
| timeout | connect/read timeout 명시 | 외부 clients에 적용 | 수렴 |
| retry | same-key, backoff, retryable 분류 | publisher/Job/saga에 적용 | 수렴 |
| circuit breaker | 장애 전파가 큰 sync dependency에 선택 적용 | 별도 library 없음 | 현재는 관찰 필요 |
| feature flag | 기본 off+activation/retirement gate | Billing saga·consumer에 적용 | 수렴 |
| startup validation | config/index/transaction fail-fast | 적극 사용 | 수렴 |
| reconciliation | 정기 비교와 repair | 일부 계획, 전체 자동화는 미완성 | 부분 수렴 |

Resilience4j를 넣는 것보다 먼저 현재 timeout/retry semantics와 same-key replay를 운영 metric으로 관찰해야 한다. 호출량과 장애 전파가 실제로 확인될 때 circuit breaker를 도입한다.

### 3.8 관측성과 개인정보

| 항목 | 대표적인 수렴 방향 | 현재 상태 | 판단 |
| --- | --- | --- | --- |
| structured log | stable event/outcome/error code | Identity·Learning에 적용 | 수렴 |
| metrics | low-cardinality domain outcome | Identity/Billing에 적극 적용 | 수렴 |
| tracing | W3C trace context, cross-service span | requestId는 있으나 전체 trace 기반은 미완성 | 간극 |
| error reporting | scrubbed Sentry, no payload | sanitizer·whitelist 경계 구현 | 수렴 |
| privacy | data minimization, retention, redaction | phone candidate/Token/audio 규칙이 상세 | 수렴 |

다음 대표 방향은 OpenTelemetry 기반 tracing이다. 단, provider payload나 userId를 span attribute로 무분별하게 넣지 않는 allowlist가 먼저다.

### 3.9 테스트와 delivery

| 항목 | 대표적인 수렴 방향 | 현재 상태 | 판단 |
| --- | --- | --- | --- |
| unit/contract | 빠른 단위+API contract | 세 저장소에 존재 | 수렴 |
| data integration | 실제 semantics가 필요한 곳만 Testcontainers | Billing/Identity 일부에 적용 | 수렴 |
| cross-service | ephemeral/staging consumer contract | 수동 gate·문서 중심 | 간극 |
| infra | IaC, environment parity, policy test | 실제 AWS 리소스는 수동·workflow는 app revision 중심 | 간극 |
| deployment | backward compatible, consumer-first | event 흐름에 consumer-first 원칙 | 수렴 방향 |

실제 출시 전에는 세 서비스 code test보다 `Identity eligibility→Billing`, `앱→Learning saga→Billing`, `Learning grading→AttemptGroup` E2E가 더 중요하다.

## 4. 저장소별 수렴도

### 4.1 Learning Core

이미 수렴한 부분:

- 앱 계약 호환성, 소유권, AI 식별자 분리
- durable grading Job과 Callback 멱등성
- Billing saga와 unknown outcome 복구
- S3/AI adapter 분리, startup validation

차이가 큰 부분:

- 큰 application facade와 DTO/converter 집중
- 공개 AI Callback trust boundary
- 기존 `LocalDateTime`과 raw Map
- 사용자 API·internal Callback package 혼재

진화 방향:

`하나의 Exam service`에서 `동일 배포 단위 안의 명확한 exam-session / grading / result-read / integration module`로 수렴한다.

### 4.2 Identity

이미 수렴한 부분:

- asymmetric JWT/JWKS, Refresh Token hash/rotation
- capability별 use case, port/adapter
- transactional outbox, worker, retry, dead-letter
- credential·payload redaction과 민감정보 최소화

차이가 큰 부분:

- auth subtree의 탐색 비용
- Firebase/phone/merge lifecycle의 많은 class와 configuration
- Billing 목표 transport와 기존 workload JWT adapter의 차이

진화 방향:

새 bounded context를 늘리기보다 auth 내부를 capability vertical slice로 재배치하고 outbound transport를 AWS workload identity로 통일한다.

### 4.3 Billing

이미 수렴한 부분:

- strict internal API decode
- BenefitDefinition→Claim/Grant→Reservation/AttemptGroup 모델
- append-only ledger, allocation, Transaction, unique index
- command idempotency, expiry와 lifecycle 분리
- public API 없이 서버 간 최소 surface 유지

차이가 큰 부분:

- AttemptGroup 상태 consumer 미구현
- 실제 Lattice route·staging failure injection 미검증
- 결제·구독은 아직 제품/계약 후속 단계

진화 방향:

현재 free-trial vertical slice를 운영에서 닫은 뒤, 결제 provider보다 먼저 owner lifecycle과 AttemptGroup reconciliation을 완성한다.

## 5. 권장 목표 구조

### 5.1 단기: 운영 연결을 닫는 단계

1. Identity→Billing SigV4 adapter와 route별 IAM을 완성한다.
2. Billing AttemptGroup consumer를 먼저 배포한다.
3. Learning Core outbox/publisher를 local state transition과 같은 Transaction에 연결한다.
4. Mongo migration, unknown outcome failure injection, Lattice staging E2E를 통과한다.
5. AI Callback trust boundary 개선안을 Python AI와 동결한다.

### 5.2 중기: 변경 비용을 낮추는 단계

1. Learning Core 내부 capability 모듈을 분리하되 공개 Controller/DTO는 유지한다.
2. 10초 챌린지를 별도 aggregate와 AI endpoint로 구현한다.
3. 세 서비스 네이밍·시간·format 규칙을 신규 코드에 적용한다.
4. OpenAPI와 handoff 상태를 비교하는 문서 fitness check를 추가한다.
5. W3C trace context를 서비스 간 호출에 전달한다.

### 5.3 장기: 제품 확장에 맞춰 선택할 단계

1. Billing public product/subscription API가 필요해질 때 audience와 Store lifecycle을 별도 ADR로 설계한다.
2. event fanout·throughput·운영 replay가 HTTP outbox 한계를 넘을 때 managed queue/broker를 비교한다.
3. 장시간 saga 수와 복구 단계가 크게 늘 때 durable workflow engine을 비교한다.
4. 인프라 변경 빈도와 환경 drift가 커지기 전에 Terraform/CDK 등 IaC source of truth를 확정한다.

## 6. 지금 도입하지 않는 것이 좋은 것

| 기술·패턴 | 지금 보류할 이유 |
| --- | --- |
| Kafka | 현재 핵심 문제는 throughput이 아니라 미완성 producer/consumer와 운영 gate다. |
| 서비스 추가 분리 | 현재 세 서비스 경계는 충분하며 Learning Core 내부 모듈화가 먼저다. |
| 전체 Event Sourcing | Billing ledger 외 aggregate까지 event sourcing할 비용 대비 제품 요구가 없다. |
| 공통 Entity/DTO library | 서비스 독립 배포와 계약 evolution을 compile-time 결합한다. |
| 전면 reactive stack | 현재 병목 근거가 없고 Transaction·Security·운영 복잡도만 커질 수 있다. |
| 전면 REST 재설계 | 앱·Python AI 기존 계약과 출시 안정성을 깨뜨린다. |
| 조기 circuit breaker library | 현재 retry·same-key·timeout metric을 먼저 관찰해야 한다. |

## 7. 구조를 지키는 Fitness Function 제안

| 규칙 | 자동 검증 예 |
| --- | --- |
| 실제 userId 외부 비노출 | 공개 DTO field scan과 JSON contract test |
| AI `user_id=examId` | multipart/Callback contract test |
| 사용자용 examId 소유권 | Controller endpoint별 403 test matrix |
| internal/public 경계 | Security matcher와 BaseResponse 사용 금지 test |
| idempotency | duplicate/same-key/different-payload concurrency test |
| Mongo 운영 전제 | startup index·transaction capability test |
| event 멱등성 | duplicate/stale/conflict/out-of-order contract test |
| 민감정보 로그 금지 | log capture와 redaction pattern test |
| 문서 freshness | Controller endpoint 목록과 handoff status diff check |
| dependency direction | ArchUnit로 Controller→application→domain/infrastructure 경계 검증 |
| 신규 timestamp | durable entity의 `LocalDateTime` 신규 사용 금지 rule |
| Callback payload | size/strict decode/authentication contract test |

## 8. 최종 판단

현재 앱 서버는 유행에 뒤처진 구조라기보다, 최근 추가된 기능은 이미 멱등성·outbox·saga·strict contract 방향으로 상당히 잘 수렴하고 있다. 가장 큰 차이는 기술 선택보다 완성도 분포다.

- Identity와 Billing 신규 영역은 구조적 안전장치가 강하다.
- Learning Core는 출시 계약을 지키는 과정에서 내부 책임 집중이 커졌다.
- 서비스 사이에는 코드가 존재하지만 아직 운영 경로가 닫히지 않은 연결이 있다.

따라서 최우선 전략은 새로운 기반 기술 도입이 아니라 `미완성 연결 닫기 → Learning Core 내부 모듈화 → 공통 fitness function` 순서다.

