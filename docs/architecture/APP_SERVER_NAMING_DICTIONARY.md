# 토선생 앱 서버 네이밍 사전

## 1. 목적과 사용법

이 문서는 세 앱 서버에서 같은 책임을 같은 이름으로 이해하기 위한 공통 사전이다. 기존 공개 API·Mongo field·이벤트 wire 이름을 즉시 바꾸자는 목록이 아니다. 외부 계약 이름은 호환성을 우선하고, 신규 코드와 내부 리팩터링에서 이 사전을 기본값으로 사용한다.

판정은 다음과 같다.

- 일관: 의미와 사용 위치가 대체로 동일하다.
- 문맥 구분: 같은 단어지만 qualifier가 있어 구별 가능하다.
- 혼재: 같은 책임의 이름이 다르거나 같은 이름의 책임이 넓다.
- 레거시: 호환성 때문에 유지하지만 신규 코드에서는 확대하지 않는다.

## 2. 핵심 도메인 명사

### 2.1 사용자와 인증

| 표준 이름 | 정의 | 실제 이름·위치 | 사용 규칙 | 판정 |
| --- | --- | --- | --- | --- |
| 사용자 ID | Identity가 발급한 실제 사용자의 UUID 문자열 | 세 서비스의 `userId`, JWT `sub` | 클라이언트 입력을 신뢰하지 않고 검증된 JWT 또는 workload 계약에서 얻는다. | 일관 |
| 사용자 | 계정과 lifecycle의 원본 aggregate | Identity `User` | 시험·혜택 필드를 넣지 않는다. | 일관 |
| 계정 유형 | Guest와 정회원 구분 | `UserAccountType(GUEST, MEMBER)` | 로그인 수단과 구분한다. | 일관 |
| 가입 Provider | 기존 가입 형태 호환 정보 | Identity `UserProvider` | 신규 SNS 연결의 source of truth로 확대하지 않는다. | 레거시 |
| 소셜 Provider | 연결된 SNS 인증수단 종류 | Identity `SocialProvider` | `GOOGLE`, `APPLE`, `KAKAO` 등 외부 인증수단에만 사용한다. | 일관 |
| 외부 Identity | 외부 provider subject와 내부 User 연결 | `FirebaseIdentity`, `SocialIdentity`, `PhoneIdentity` | provider별 opaque subject와 소유권을 가진다. | 일관 |
| 현재 사용자 공급자 | 인증 문맥에서 실제 userId 제공 | 각 서비스 `CurrentUserProvider` | Controller Request에서 userId를 직접 받지 않는다. | 일관 |
| Refresh Session | Refresh Token 회전·폐기 단위 | Identity `RefreshSession` | 토큰 원문을 저장하지 않는다. | 일관 |
| Workload credential | 서비스 간 호출 주체 증명 | Identity `WorkloadIdentityCredential`, AWS SigV4 | 사용자 Access Token과 분리한다. | 혼재·전환 중 |

### 2.2 시험과 채점

| 표준 이름 | 정의 | 실제 이름·위치 | 사용 규칙 | 판정 |
| --- | --- | --- | --- | --- |
| 시험지 | 배정 가능한 문제 묶음 정의 | Learning Core `MockExam` | 사용자 실행 상태를 넣지 않는다. 한국어 문서에서는 시험지로 부른다. | 문맥 구분 |
| 시험 세션 | 사용자가 시작한 한 번의 시험 실행 | `ExamSession`, ID=`examId` | `examId→userId`, 배정 시험지, lifecycle을 소유한다. | 일관 |
| 문항 | 시험지에 속한 문제 정의 | `Question` | 응시 결과와 분리한다. | 일관 |
| 문항 응시 회차 | 사용자의 새 녹음 제출 번호 | `retryCount` | AI transport 재시도나 Job dispatch 횟수에 사용하지 않는다. | 중요 계약 |
| 문항 채점 Job | 문항·회차의 durable 채점 작업 | `QuestionGradingJob` | 상태·dispatch attempt·복구 generation을 소유한다. | 일관 |
| 종합 채점 Job | 시험 전체 요약 생성 작업 | `SummaryGradingJob` | 문항 Job과 식별·generation을 분리한다. | 일관 |
| 문항 결과 | 한 문항·회차의 피드백 결과 | `ExamResult` | 이름은 Exam이지만 실제 주 역할은 Question attempt result다. | 혼재 |
| 종합 결과 | 시험 전체 점수·피드백 | `ExamSummary` | 신규 종합 결과는 별도 collection을 사용한다. | 일관 |
| 외부 채점 결과 | provider별 원본/정규화 결과 | `AzureResult`, `SpeechAceResult` | 최종 앱 DTO가 아니라 provider integration record다. | 일관 |
| 채점 상태 | Job 또는 외부 공개 상태 | `GradingJobStatus`, `ExamStatus` | 같은 값이 있어도 aggregate와 API projection을 구분한다. | 문맥 구분 |
| 모델 답안 Catalog | 시험지별 모범 음성 metadata | `ModelAnswerCatalogService` | Question catalog와 다른 source를 읽는다. | 일관 |

`ExamResult`는 과거 종합 필드와 현재 문항 필드를 함께 가진다. 신규 코드에서는 문서·변수에서 `questionResult`라고 의미를 명시하되, Mongo collection과 외부 계약은 별도 migration 없이 바꾸지 않는다.

### 2.3 혜택·사용권·Billing

| 표준 이름 | 정의 | 실제 이름·위치 | 사용 규칙 | 판정 |
| --- | --- | --- | --- | --- |
| 혜택 정의 | 공통 혜택 종류와 정책 catalog | `BenefitDefinition` | 사용자 소유 상태를 저장하지 않는다. | 일관 |
| 전화 자격 projection | 현재 verified/revoked 상태 | `TrialEligibility` | Claim·Grant·사용 이력이 아니다. | 일관 |
| 무료 Claim | 검증 전화 기준 중복 수급 방지 기록 | `TrialClaim` | 일반적인 request claim이나 권리 자체로 부르지 않는다. | 일관 |
| 사용권 Grant | 사용자 subject에게 발급된 실제 권리 | `EntitlementGrant` | available/held/consumed 수량을 가진다. | 일관 |
| 사용권 Ledger | 지급·hold·release·consume append-only 이력 | `EntitlementLedgerEntry` | mutable balance를 대체하지 않고 감사 근거가 된다. | 일관 |
| Reservation | Session commit 전 임시 hold | `Reservation` | 결제 예약이나 좌석 예약과 섞지 않고 Billing entitlement 문맥에서 쓴다. | 일관 |
| Allocation | Reservation과 Grant unit 연결 | `ReservationAllocation` | hold/consume/release 출처를 추적한다. | 일관 |
| 응시 그룹 | 최초 시험과 무차감 replacement를 묶는 소비 단위 | `AttemptGroup` | 시험 문제·점수 aggregate가 아니다. | 일관 |
| 응시 세션 projection | active/stale fencing용 최소 projection | `AttemptSession` | Learning Core `ExamSession`의 복제본이 아니다. | 문맥 구분 |
| Billing 주체 참조 | 삭제 가능한 사용자 mapping과 audit core 분리 | `subjectRefId` | 실제 `userId`와 동일하다고 가정하지 않는다. | 일관 |
| 전화 후보값 | scope/key별 pseudonymous dedupe 값 | `candidate` | 인증 credential이나 raw phone이 아니다. | 일관 |

### 2.4 Lifecycle·비동기 처리 명사

| 이름 | 권장 정의 | 실제 사용 | 판정·규칙 |
| --- | --- | --- | --- |
| `Operation` | 사용자 의도 하나를 장기간 수렴시키는 durable process | Learning Core `ExamCreationOperation` | 적절하다. 공개 `Idempotency-Key`와 1:1로 연결한다. |
| `Command` | 한 endpoint에서 수행할 reserve/confirm/cancel 같은 명령 | Billing `IdempotencyCommand`, `ReserveCommand` | 적절하다. Operation 전체와 구분한다. |
| `Job` | 외부 처리가 끝날 때까지 상태를 가진 비동기 작업 | Learning Core Grading Job | 적절하다. 사용자 응시 회차와 분리한다. |
| `Claim` | 특정 worker가 Job을 처리할 수 있도록 얻은 immutable snapshot | Learning Core `QuestionDispatchClaim`, `SummaryDispatchClaim` | Billing `TrialClaim`과 뜻이 완전히 다르다. 항상 qualifier를 붙인다. |
| `Result` | 완료된 도메인 결과 또는 use case 반환값 | 세 저장소 전반 | 가장 과부하된 suffix다. 아래 세부 규칙을 적용한다. |
| `Outcome` | 성공/중복/stale 등 처리 분류 | Identity·Billing publisher/consumer | 신규 event 처리 분류에 권장한다. |
| `Snapshot` | 특정 시점의 불변 응답·상태 복사 | Identity/Billing | replay용 저장값에만 사용한다. |
| `State` | durable aggregate 내부 상태 | Operation, Session, Claim | 외부 API projection에는 가능하면 `Status`를 사용한다. |
| `Status` | 외부에 노출하거나 조회하는 현재 상태 | Session, Job, API response | State와 완벽히 통일되지는 않지만 의미 구분은 가능하다. |

## 3. 역할 suffix 사전

| suffix | 같은 책임의 정의 | 실제 예 | 신규 코드 규칙 |
| --- | --- | --- | --- |
| `Controller` | HTTP를 application use case로 변환 | 세 서비스 Controller | Repository와 정책을 직접 호출하지 않는다. |
| `Service` | application use case 또는 명확한 domain capability | `ExamReadService`, `LoginService`, `ReserveService` | 이름만으로 부족하면 동사·대상을 구체화한다. 범용 `SomethingService`에 여러 lifecycle을 모으지 않는다. |
| `TransactionService` | 하나의 local Transaction 안에서 여러 document 불변식 보장 | 세 서비스에 존재 | orchestration·HTTP 호출을 넣지 않는다. |
| `Manager` | 여러 entity에 걸친 내부 lifecycle 조정 | `ExamSessionManager` | 사용 범위가 모호하므로 신규 도입은 `Service`/`Coordinator`보다 먼저 책임을 설명한다. |
| `Saga` | 서비스 간 Transaction 불가를 durable state와 보상으로 수렴 | `BillingExamCreationSaga` | local Transaction Service와 외부 client를 조정하되 wire 세부를 직접 구현하지 않는다. |
| `Catalog` | 정적·versioned 정의 조회와 검증 | `MockExamCatalogService`, `BenefitCatalog` | 사용자별 상태를 넣지 않는다. `Catalog`와 `CatalogService` 중 한 저장소 안에서는 통일한다. |
| `UseCase` | 교체 가능한 application port | Identity Firebase `*UseCase` | 실제 disabled/enabled 구현 교체가 있을 때 적절하다. 단일 구현에 습관적으로 만들지 않는다. |
| `Port` | infrastructure가 구현하는 application 경계 | Identity `*DeliveryPort`, cleanup port | 외부 시스템·운송 경계에 사용한다. |
| `Adapter` | Port의 구체 기술 구현 | Identity `Jdk*DeliveryAdapter` | 기술명을 앞에 붙여 교체 가능성을 드러낸다. |
| `Client` | 외부 HTTP/API 계약 호출자 | Learning Core `BillingReservationClient` | transport와 strict wire mapping까지만 담당한다. |
| `Publisher` | outbox event claim·전송·재시도 결정 | Identity publisher | Scheduler와 분리한다. |
| `Consumer` | inbound event 검증 후 local 처리 | Learning Core `UserWithdrawnEventConsumerService` | `EventService`와 혼용하지 않도록 신규 코드는 `Consumer` 또는 `EventHandler` 중 하나를 선택한다. |
| `Worker` | 한 번 또는 batch의 background 처리 | Identity cleanup worker, Billing expiry worker | 스케줄 주기는 소유하지 않는다. |
| `Scheduler` | 시간 기반으로 worker/publisher를 호출 | Identity publisher scheduler, Learning summary scheduler | 업무 상태 전이보다 scheduling을 담당한다. Learning의 `SummaryDispatchScheduler`는 scheduling+claim 경계를 함께 검토한다. |
| `Repository` | aggregate 저장·조회 abstraction | 세 서비스 | 현재 구현 방식은 다르다. Spring Data interface인지 MongoTemplate concrete인지 문서에서 명확히 한다. |
| `Query` | read model·aggregation 전용 조회 | `ExamSessionCompletionQuery` | write Repository와 분리할 때 사용한다. |
| `Converter` | 내부 모델 간 구조 변환 | `ExamConverter`, `ReservationConverter` | validation·I/O·상태 전이를 넣지 않는다. |
| `Mapper` | wire event나 작은 값 mapping | Identity `*EventMapper` | Converter와의 차이를 저장소 안에서 유지한다. |
| `Decoder` | 신뢰하지 않는 raw payload를 strict DTO로 변환 | Billing `*Decoder` | size/content-type/unknown/coercion 정책을 명시한다. |
| `Parser` | 단일 header/path scalar를 canonical 값으로 변환 | Billing `IdempotencyKeyParser` | payload 전체에는 Decoder를 사용한다. |
| `Hasher` | canonical input을 digest로 변환 | Billing payload hasher, Identity token hasher | 입력 normalization 경계를 함께 문서화한다. |
| `Validator` | 이미 binding된 값/설정의 규칙 확인 | JWT validator, startup validator | 외부 JSON decode와 구분한다. |
| `Verifier` | 외부 증명 또는 인프라 capability 확인 | Firebase verifier, Mongo capability verifier | 단순 bean validation에는 Validator를 사용한다. |
| `Initializer` | 승인된 collection/index/catalog 초기 상태 보장 | Billing initializer | 운영 중 임의 migration과 구분한다. |
| `Probe` | 기동 시 capability를 실제로 시험 | Learning transaction capability probe | schema validation과 구분한다. |
| `Properties` | 외부 설정 binding | 세 서비스 `*Properties` | Secret 값을 `toString`이나 로그에 노출하지 않는다. |
| `Configuration`·`Config` | Spring bean/configuration 구성 | 세 서비스 혼재 | 신규 공통 규칙은 Spring `@Configuration`에 `Configuration`, 단순 기술 bean 묶음에 기존 `Config` 호환을 허용한다. |

## 4. DTO와 반환값 이름 규칙

| 이름 | 권장 용도 | 현재 편차 |
| --- | --- | --- |
| `*Request` | HTTP/wire request body | Identity·Billing은 독립 record, Learning은 `ExamRequestDTO` 중첩 class를 주로 사용한다. |
| `*Response` | HTTP/wire response body | Identity·Billing은 독립 type, Learning은 `ExamResponseDTO` 중첩 class를 주로 사용한다. |
| `*DTO` | 여러 boundary에서 재사용되는 transport 구조 | Learning에 집중되어 있다. 신규 코드는 Request/Response 역할이 분명하면 해당 suffix를 우선한다. |
| `*Result` | application use case의 내부 반환값 | Billing `ReserveResult`, `LifecycleResult`; Learning은 외부 DTO에도 사용한다. 외부 wire에는 `Response`, 내부 use case에는 `Result`를 권장한다. |
| `*Outcome` | 분기 가능한 처리 결과 enum | Identity·Billing event/worker에서 잘 사용한다. |
| `*Command` | application service 입력 | Billing에서 일관적이다. |
| `*Req` | 축약 wire request | Learning AI Callback DTO | 기존 Python 계약은 유지하되 신규 사용자 API에서는 `Request`를 사용한다. |

## 5. 같은 단어가 다른 뜻인 경우

### 5.1 Session

| 이름 | 뜻 |
| --- | --- |
| `RefreshSession` | Identity Refresh Token lifecycle |
| `ExamSession` | Learning Core 시험 실행 |
| `AttemptSession` | Billing active/stale fencing projection |

`Session`을 단독 변수명이나 문서 제목으로 쓰지 않고 qualifier를 유지한다.

### 5.2 retry

| 이름 | 뜻 |
| --- | --- |
| `retryCount` | 사용자가 새 음성을 제출한 응시 회차 |
| `dispatchAttempt` | 같은 Job의 외부 전송 시도 횟수 |
| `generationAttempt` | Summary 결과 generation 세대 |
| `recoveryCycle` | 완료 표시와 결과 불일치 복구 세대 |
| retry policy attempt | event/worker transport 재시도 횟수 |

로그·메트릭에서 단순 `retry` tag를 쓰지 않고 위 이름을 사용한다.

### 5.3 Provider

| 이름 | 뜻 |
| --- | --- |
| `UserProvider` | 가입 형태 호환 정보 |
| `SocialProvider` | Google·Apple·Kakao 로그인 수단 |
| AI provider | Azure·SpeechAce 등 채점 공급자 |
| Spring `Provider` | dependency lookup 기술 타입 |

신규 도메인 이름에는 대상 qualifier를 붙인다.

### 5.4 Claim

| 이름 | 뜻 |
| --- | --- |
| JWT claim | 토큰 속성 (`sub`, `aud` 등) |
| `TrialClaim` | 무료 혜택 anti-abuse aggregate |
| `DispatchClaim` | worker가 얻은 immutable Job snapshot |

## 6. 저장소별 네이밍 특성

| 관점 | Learning Core | Identity | Billing |
| --- | --- | --- | --- |
| package root | `web.tosunsaeng` | `web.tosunsaeng.identity` | `web.tosunsaeng.billing` |
| application naming | `Service`, `Manager`, `Saga`, `Scheduler` | `Service`, `TransactionService`, `UseCase`, `Worker`, `Publisher`, `Port` | `Service`, `Command`, `Result`, `Worker`, `Catalog` |
| DTO | 큰 외부 DTO container의 중첩 class | 독립 Request/Response record | 독립 Request/Response record와 Command/Result |
| Repository | 주로 Spring Data interface | Spring Data+Custom interface/Impl | MongoTemplate concrete class |
| configuration | `Config`와 `Configuration` 혼재 | 주로 `Configuration` | 주로 `Config`/`Properties`, initializer 분리 |
| event | inbound consumer 중심 | Outbox·WireEvent·Publisher·DeliveryPort | Inbox·EventDecoder·EventService |

## 7. 컨벤션 준수 평가

| 항목 | 판정 | 근거 |
| --- | --- | --- |
| 도메인 aggregate 이름 | 양호 | User/ExamSession/Reservation/Grant 등 핵심 명사가 책임을 잘 드러낸다. |
| 식별자 qualifier | 양호 | examId, operationId, reservationId, attemptGroupId가 대체로 분명하다. |
| `retryCount` 의미 보존 | 양호·주의 필요 | 외부 계약은 유지되지만 내부 retry 종류가 많아 qualifier가 필수다. |
| Service 역할 명명 | 부분 혼재 | Learning의 `ExamServiceImpl`과 큰 `ExamGradingService`, Identity의 세분화된 UseCase/TransactionService가 다른 철학을 따른다. |
| Repository 의미 | 혼재 | interface abstraction과 concrete Mongo adapter가 같은 suffix를 쓴다. |
| DTO 이름 | 혼재 | `DTO`, `Req`, `Request`, `Response`, `Result`가 서비스별로 다르다. |
| 시간 이름 | 이름은 양호, 타입 혼재 | `createdAt`, `completedAt`은 일관되지만 Learning Session만 `LocalDateTime`이 남아 있다. |
| 이벤트 이름 | 양호 | Outbox, WireEvent, Publisher, Inbox가 대체로 명확하다. |
| Provider | 주의 | Identity 내부에서도 legacy UserProvider와 SocialProvider가 공존한다. |

## 8. 신규 코드 기본 규칙

1. 공개/wire 타입은 `Request`·`Response`, application 내부 입력·출력은 `Command`·`Result`를 사용한다.
2. durable 비동기 작업만 `Job`, worker가 얻은 처리 snapshot만 `*DispatchClaim`으로 부른다.
3. 사용자 재응시는 `retryCount`, transport 재전송은 `dispatchAttempt` 또는 `deliveryAttempt`로 부른다.
4. `Session`, `Provider`, `Claim`, `Result`, `Status`는 qualifier 없이 단독 도메인 이름으로 만들지 않는다.
5. 외부 시스템 경계는 `Port`+`Adapter` 또는 `Client` 중 저장소의 기존 패턴을 따른다.
6. 단일 구현체를 위한 `Service`/`ServiceImpl` 쌍은 새로 만들지 않는다. 실제 boundary 교체나 contract가 있을 때만 interface를 둔다.
7. Repository 구현 방식은 당장 통일하지 않되, package와 문서에서 domain port인지 Mongo adapter인지 드러낸다.
8. 외부 계약 이름 변경은 별도 migration·소비자 합의 없이 수행하지 않는다.

