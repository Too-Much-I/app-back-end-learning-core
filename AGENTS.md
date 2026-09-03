# 프로젝트 목적

이 저장소는 기존 토선생 웹 POC 백엔드를 복제하여 만든 앱용 Learning Core 서비스다.

현재 이 저장소의 제품·구현 범위는 앱 전용이다. 기존 웹 백엔드와 웹 프론트는 별도 저장소·별도 배포 서비스이며 이 저장소의 변경 대상으로 취급하지 않는다. 명시적인 요청이 없으면 웹 저장소를 조회하거나 웹 동작을 함께 검증하지 않는다.

기존 POC 저장소:
- Too-Much-I/web-back-end

현재 저장소:
- Too-Much-I/app-back-end-learning-core

기존 POC 저장소와 기존 POC 배포 환경은 절대 수정하지 않는다.
현재 저장소의 코드만 수정한다.

# 기술 환경

- Java 21
- Spring Boot
- Gradle Wrapper
- MongoDB
- Redis
- AWS S3
- Python AI 채점 서버 연동

기본 빌드 및 테스트 명령:
- ./gradlew clean test

# 최우선 호환성 규칙

앱 클라이언트와 Python AI 서버의 기존 계약 호환성을 최우선으로 유지한다.

과거 웹 POC와의 호환성은 이 저장소의 신규 설계 제약이 아니다. 다만 현재 앱이 이미 제공하는 공개 API와 공유 Python AI 계약은 명시적인 요청 없이 변경하지 않는다.

명시적인 요청이 없으면 다음 외부 계약을 변경하지 않는다.

- 기존 공개 API URL
- 기존 HTTP Method
- 기존 Path Parameter
- 기존 Query Parameter
- 기존 Request Body
- 기존 Response DTO
- 기존 BaseResponse 구조
- 기존 retryCount 방식
- 기존 S3 Presigned URL 발급 흐름
- 기존 음성 제출 흐름
- 기존 Polling 흐름
- 기존 AI 요청 구조
- 기존 AI Callback URL
- 기존 AI Callback JSON 구조

API를 더 RESTful하게 만든다는 이유로 기존 계약을 임의로 재설계하지 않는다.

다음과 같은 변경은 현재 작업 범위에 포함하지 않는다.

- upload-url GET API를 POST로 변경
- retryCount를 attemptId 방식으로 변경
- 별도의 시험 완료 API 추가
- 기존 성공 응답 코드를 201 또는 202로 일괄 변경
- 기존 응답 DTO에 새로운 필드 추가
- API 버전 변경

# 사용자 식별 규칙

클라이언트는 실제 userId를 직접 전달하지 않는다.

다음 위치에 userId를 추가하지 않는다.

- Request Body
- Path Parameter
- Query Parameter
- 기존 Response DTO

실제 사용자 ID는 UUID 형식 문자열로 관리한다.

현재 Identity 서버가 없으므로 CurrentUserProvider 추상화를 사용한다.

현재 단계에서는 LegacyCurrentUserProvider 또는 동일한 역할의 구현체가 고정 개발 UUID를 반환한다.

기본 개발 UUID:
- 00000000-0000-0000-0000-000000000001

local/test의 Legacy 모드는 Identity 없이 앱 개발 흐름을 확인할 수 있도록 Authorization 헤더 없이도 동작해야 한다.

Identity 서버 연동 이후에는 JWT의 sub 클레임을 실제 userId로 사용한다.

Identity 연동 이후에도 앱은 userId를 직접 보내지 않고 Authorization 헤더에 Access Token만 전달한다.

현재 작업에서는 다음을 수행하지 않는다.

- JWT 인증 강제
- 모든 API를 authenticated로 변경
- SecurityConfig의 전면 재설계
- Identity 서버 구현

# 시험 세션 규칙

시험 생성 시 다음 관계를 Learning Core의 MongoDB에 저장한다.

- examId -> 실제 userId

ExamSession은 최소한 다음 필드를 가진다.

- examId
- userId
- createdAt

status 또는 mockExamId는 기존 코드 흐름에서 명확히 얻을 수 있고 중복 상태를 만들지 않는 경우에만 추가한다.

시험 생성 API의 기존 Request와 Response는 변경하지 않는다.

외부 Response에 userId를 추가하지 않는다.

# AI 연동 규칙

Python AI 서버로 보내는 user_id에는 실제 사용자 UUID가 아니라 기존과 동일하게 examId를 전달한다.

반드시 다음 규칙을 유지한다.

- AI user_id = examId
- AI user_id != 실제 userId

Python AI 서버의 기존 필드명 user_id를 변경하지 않는다.

AI Callback에서 전달되는 user_id도 examId로 해석한다.

외부 JSON 필드명이 user_id라면 그대로 유지한다.

Java DTO 내부에서는 의미를 명확하게 하기 위해 examId라는 필드명을 사용할 수 있지만, 외부 JSON 계약은 @JsonProperty("user_id") 등으로 그대로 유지한다.

AI Callback 처리 순서는 다음과 같다.

1. Callback의 user_id를 examId로 해석한다.
2. ExamSessionRepository에서 examId로 ExamSession을 조회한다.
3. ExamSession.userId에서 실제 사용자 UUID를 가져온다.
4. ExamResult에 examId와 실제 userId를 함께 저장한다.
5. 기존 Azure, SpeechAce, LLM 채점 결과 저장 흐름을 유지한다.

실제 사용자 UUID를 Python AI 서버에 전송하지 않는다.

# ExamResult 규칙

ExamResult에 실제 userId 필드를 추가한다.

ExamResult에는 최소한 다음 식별 관계가 존재해야 한다.

- examId: 시험 세션 식별자
- userId: 실제 사용자 UUID
- questionNumber
- retryCount

examId와 userId를 서로 바꾸어 저장하지 않는다.

다음 외부 응답에는 userId를 추가하지 않는다.

- 시험 생성 응답
- 시험 상태 응답
- 시험 종합 결과 응답
- 문항별 결과 응답
- 업로드 URL 응답
- 음성 제출 응답
- 문항별 Polling 응답

userId는 내부 DB 저장과 사용자 소유권 검증 용도로만 사용한다.

# 시험 소유권 검증 규칙

사용자용 API에서 examId를 사용할 때 다음 조건을 확인한다.

- ExamSession.userId == CurrentUserProvider.getCurrentUserId()

examId를 입력받는 사용자용 API에는 소유권 검증을 적용한다.

최소 확인 대상은 다음과 같다.

- 시험 전체 상태 조회
- 시험 종합 결과 조회
- 문항별 결과 조회
- S3 업로드 URL 발급
- 음성 제출
- 개별 문항 상태 조회
- 그 밖에 examId를 입력받는 사용자용 공개 API

AI Callback은 사용자용 API가 아니므로 CurrentUserProvider 기반의 사용자 소유권 검증을 적용하지 않는다.

AI Callback에서는 examId로 ExamSession을 조회하여 실제 userId를 찾는다.

# 삭제할 기능

다음 API는 제거 대상이다.

- POST /api/v1/exams/trial
- POST /api/v1/exams/{examId}/terminate

위 두 API에만 사용되는 다음 코드는 제거할 수 있다.

- Controller 메서드
- Service 인터페이스 메서드
- Service 구현 메서드
- Request 또는 Response DTO
- Swagger 또는 OpenAPI 문서
- 테스트
- ErrorCode
- 미사용 import

다른 API에서도 사용하는 공유 코드는 삭제하지 않는다.

# 현재 추가하지 않을 기능

다음 기능은 현재 Learning Core 수정 범위에 포함하지 않는다.

- Identity 서버
- 이메일 회원가입
- 로그인
- 소셜 로그인
- Refresh Token
- JWT 인증 강제
- 스트릭
- 주간 학습 이력
- 취약 단어장
- attemptId 기반 재설계
- 메시지 큐
- Kafka
- SQS
- Redis 구조 전면 개편
- S3 Key 구조 전면 변경
- 관련 없는 대규모 리팩터링

## TMI-14 명시적 예외

- Jira TMI-14에 한하여 “JWT 인증 강제 제외” 규칙의 제한적 예외를 허용한다.
- 이 예외는 staging/prod Startup 검증, Legacy 차단, 인증 모드 설정 검증 및 미사용 HMAC 코드 정리에만 적용한다.
- 기존 JWT 보호 API와 공개 API 범위는 변경하지 않는다.
- local/test Legacy 모드는 유지한다.
- 외부 API와 AI 계약은 변경하지 않는다.
- TMI-14 완료 후에도 다른 작업에는 이 예외가 자동 적용되지 않는다.

## TMI-25 명시적 예외

- Jira TMI-25에 한하여 최우선 호환성 규칙과 “현재 추가하지 않을 기능” 규칙의 제한적 예외를 허용한다.
- 이 예외는 시험 단위 재채점과 AI 채점·Callback 멱등성 구현에만 적용한다.

허용 범위는 다음과 같다.

- 신규 API `POST /api/v1/exams/{examId}/grading/retry` 하나 추가
- 신규 API 전용 Request/Response DTO 추가
- `QuestionGradingJob`과 `SummaryGradingJob` 추가
- 기존 submit API의 외부 계약을 유지하면서 내부 처리를 멱등하게 변경
- 기존 status API의 URL, HTTP Method와 기존 Response 필드를 유지하면서 Job 기반 상태 산정으로 내부 처리 변경
- Feedback, SpeechAce, Azure와 전체 요약 Callback 저장을 멱등하게 변경
- 전체 요약 Trigger를 모든 필수 문항의 최초 응시 `retryCount=0` 완료 기준으로 변경

다음 변경은 이 예외에서도 허용하지 않는다.

- 기존 API URL 또는 HTTP Method 변경
- 기존 Request Parameter 변경
- 기존 Response 필드 삭제 또는 이름 변경
- 기존 `retryCount` 의미 변경
- Python AI 요청과 Callback의 `user_id = examId` 계약 변경
- Redis Key 형식 변경
- S3 Object Key 변경
- 사용자 소유권 검증 변경 또는 신규 시험 단위 retry API의 소유권 검증 누락
- 사용자 새 녹음인 `retryCount>0` 문항을 시험 전체 복구 대상에 포함
- 프론트가 필수 문항 번호 목록을 보내는 구조 추가
- 별도의 외부 전체 요약 retry API 추가

- Jira TMI-25에 명시된 신규 시험 단위 retry API는 Request Body 없음 계약을 유지한다.
- 기존 공개 API·DTO·`BaseResponse`, 음성 제출·Polling과 AI Callback JSON의 나머지 계약은 유지한다.
- 이 예외는 Jira TMI-25에만 적용되며 완료 후 또는 다른 작업에 자동 적용되지 않는다.

## TMI-116 명시적 예외

- Jira TMI-116에 한하여 최우선 호환성 규칙과 “현재 추가하지 않을 기능” 규칙의 제한적 예외를 허용한다.
- 이 예외는 Learning Core 시험 생성의 Billing Reservation saga와 공개 시험 생성 command 멱등성 구현에만 적용한다.

허용 범위는 다음과 같다.

- 기존 `POST /api/v1/exams`에 optional `Idempotency-Key` request header를 추가하고 Billing saga feature flag가 활성화된 환경에서만 필수 lowercase UUID v4로 검증
- 기존 Request Body 없음과 성공 Response DTO·`BaseResponse`를 유지한 same-operation replay
- `ExamCreationOperation`과 ExamSession의 operation·reservation·attemptGroup 내부 metadata 추가
- Billing reserve·confirm·cancel·status internal client와 SigV4 `vpc-lattice-svcs` 전송
- 기존 Session 교체와 새 Session 저장의 Mongo Transaction, confirm 불명 status 복구와 안정적인 공개 오류 mapping
- feature flag 기본값 off, local/test fake client, index migration·startup validation과 관련 테스트

다음 변경은 이 예외에서도 허용하지 않는다.

- 기존 시험 생성 URL, HTTP Method, Request Body와 성공 Response field 변경
- 실제 userId, reservationId, attemptGroupId를 공개 Request/Response에 추가
- 기존 시험 `retryCount`, S3 Object Key, Redis Key와 submit·Polling 흐름 변경
- Python AI request/Callback의 `user_id=examId` 또는 JSON 계약 변경
- Billing Reservation 기능 재구현, 결제·subscription·coupon 기능 추가
- Learning Core AttemptGroup 상태 outbox/publisher, Billing event consumer, UserMerged·owner rebind와 background reconciliation 구현
- 실제 AWS Lattice/IAM/SG/ECS 리소스 생성·배포 또는 static AWS credential 추가

- feature flag가 off이면 기존 무헤더 시험 생성 흐름을 유지한다.
- feature flag를 staging/prod에서 켜기 전에 프론트 header 선배포와 Mongo index·Lattice security·장애 복구 E2E를 완료한다.
- 이 예외는 Jira TMI-116에만 적용되며 완료 후 또는 다른 작업에 자동 적용되지 않는다.

## Phone 재가입 시험 continuation 구현 허용 규칙

- 사용자 결정에 따라 Learning Core의 phone 재가입 시험 continuation discovery와 기존 Billing 시험 생성 saga의 해당 확장은 신규 구현 범위에 포함한다.
- 이 허용은 특정 Jira 한 건에만 묶지 않으며, 동일한 계약 경계 안의 후속 버그 수정·테스트·운영 안정화에도 적용한다.
- Jira TMI-122는 최초 구현 이력이며 허용 범위의 유효기간을 제한하지 않는다.
- 기준 구현 계획은 `docs/codex/PHONE_REJOIN_CONTINUATION_IMPLEMENTATION_PLAN.md`이며 Billing wire 계약은 Billing PR #8 merge commit `7138810`의 `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`를 따른다.

허용 범위는 다음과 같다.

- target userId 소유 ExamSession이 전혀 없을 때 Billing `POST /internal/v1/reservations/continuations/phone`을 조회
- body 없는 204를 기존 INITIAL 흐름으로, strict 200을 `PHONE_REJOIN` REPLACEMENT로 처리
- `ExamCreationOperation`에 nullable continuation reason·ID와 expected AttemptGroup·mockExam snapshot 저장
- 일반 reserve 3-field 계약을 유지하면서 phone continuation에만 `continuationReason`, `continuationId`, `expectedAttemptGroupId`를 모두 포함한 6-field 요청 사용
- reserve·status optional continuation field의 reader-first decode와 operation snapshot strict 검증
- reserve 응답 유실의 status 복구, stale context terminal 수렴과 계약 불일치의 status-first cancel 보상
- 새 target examId·Session을 Billing의 기존 AttemptGroup·mockExamId에 연결하고 Session commit 뒤 기존 confirm 수행
- 별도 default-off phone continuation feature flag와 startup validation
- Billing 동기 호출의 W3C CLIENT span·traceparent 전파, baggage 미전파와 SigV4 최종 서명 순서 검증
- 관련 unit·contract·component·Transaction 회귀 테스트와 문서 갱신

다음 변경은 이 허용 범위에 포함하지 않는다.

- 기존 `POST /api/v1/exams` URL, HTTP Method, Request Body, 성공 Response DTO와 `BaseResponse` 변경
- 실제 userId, continuationId, reservationId와 attemptGroupId를 공개 Request/Response에 추가
- source ExamSession owner 변경 또는 기존 답안·결과·Summary·upload/grading Job·audio 복사
- `TrialOwnerRebindApproved` Learning Core consumer 추가
- Guest `UserMerged` consumer 구현·계약 변경 또는 기존 UserMerged 계획 폐기
- Billing owner rebind·Reservation·AttemptGroup 상태 머신과 Billing 저장소 코드 수정
- 새로운 Grant·Claim·consumption, 결제·subscription·coupon 기능 구현
- 기존 시험 `retryCount`, Redis Key, S3 Object Key, Presigned URL, submit·Polling과 AI `user_id=examId` 계약 변경
- 실제 AWS Lattice/IAM/SG/ECS 리소스 생성·배포 또는 static AWS credential 추가

- phone continuation flag는 기본 off이며 creation saga가 켜진 경우에만 활성화할 수 있다.
- Billing 배포·Lattice exact route·staging E2E 전에 production flag를 활성화하지 않는다.
- 새 continuation 유형, owner rebind lifecycle 또는 Billing 권리 정책을 추가할 때는 별도 계약과 명시적 승인이 필요하다.

# AttemptGroup 상태 outbox/publisher 구현 허용 규칙

사용자 결정에 따라 Learning Core의 AttemptGroup 상태 판정, durable outbox와 Billing publisher는 신규 구현 범위에 포함한다. 이 허용은 특정 Jira 한 건에만 묶지 않으며, 후속 버그 수정·테스트·운영 안정화에도 동일한 경계를 적용한다.

승인된 구현 계획과 외부 계약은 다음 문서를 따른다.

- 구현 계획: `docs/codex/ATTEMPT_GROUP_OUTBOX_PUBLISHER_IMPLEMENTATION_PLAN.md`
- 상태·과금 결정: `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`
- Billing event 계약: Billing 저장소 `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`
- trace 전달 계약: Billing 저장소 `docs/contracts/LEARNING_CORE_ATTEMPT_GROUP_TRACE_HANDOFF.md`

TMI-116에서 AttemptGroup outbox/publisher를 제외한 것은 해당 Jira의 구현 범위를 제한한 것이다. 이 절의 후속 영구 허용 범위를 취소하거나 금지하는 의미로 해석하지 않는다.

## AttemptGroup 허용 범위

- 모든 필수 `retryCount=0` submit과 durable Question Job을 기준으로 `GRADING` 상태를 판정한다.
- 필수 retry 0 feedback, 유효 점수와 Summary가 모두 조회 가능할 때만 `COMPLETED`를 판정한다.
- 자동 복구 불가능한 최종 실패를 승인된 failureCode와 `RETAKE_AVAILABLE`로 판정한다.
- ExamSession의 local AttemptGroup projection·terminal metadata와 outbox event를 같은 Mongo Transaction/CAS로 저장한다.
- 같은 Session에는 `COMPLETED` 또는 `RETAKE_AVAILABLE` terminal event 하나만 허용한다.
- Summary Callback의 외부 계약을 유지하면서 Summary·Job·Session terminal·outbox의 로컬 DB 단계를 Transaction으로 재구성할 수 있다.
- RETAKE_AVAILABLE Session을 같은 Billing consumption·attemptGroupId·mockExamId의 REPLACEMENT 생성 saga에 연결하기 위한 내부 metadata와 검증을 추가할 수 있다.
- outbox collection, index, lease claim, retry, retention, dead-letter, auth circuit과 제한된 reconciliation scheduler를 추가할 수 있다.
- Billing `POST /internal/v1/attempt-group-events` client와 SigV4 `vpc-lattice-svcs` 전송을 추가할 수 있다.
- W3C trace context 최소 metadata 저장, publish attempt별 새 span, 구조화 로그와 저카디널리티 metric을 추가할 수 있다.
- writer와 publisher feature flag, startup validator, local/test fake client와 관련 테스트를 추가할 수 있다.

이 절의 reconciliation은 submit·Callback과 local 상태 전이 뒤 누락된 AttemptGroup outbox를 복구하는 범위로 제한한다. Billing Reservation 생성 saga 전체의 background reconciliation, 결제 보상 command와 owner rebind까지 자동으로 허용하지 않는다.

## AttemptGroup 확정 정책

- GRADING deadline은 `PT30M`이다.
- 완료 evidence가 있으면 `COMPLETED`가 항상 우선한다.
- 자동 복구 불가 정합성 위반은 `RESULT_INTEGRITY_VIOLATION`으로 종료한다.
- retry가 소진되고 active dispatch·Callback completion claim이 없으면 `REQUIRED_RESULTS_UNAVAILABLE` 또는 `SUMMARY_UNAVAILABLE`로 종료한다.
- 그 밖의 정체는 deadline 뒤 `GRADING_DEADLINE_EXCEEDED`로 종료한다.
- 신규 Billing-linked Session의 Summary 완료 source는 결정적 `exam_summaries` 문서만 사용한다. `exam_results.totalScore` legacy fallback은 자동 terminal 판정에 사용하지 않는다.
- 401·403은 event를 `BLOCKED_AUTH`로 보존하고 전역 circuit을 차단한 뒤 15분마다 한 event만 half-open probe한다.
- 기존 Billing-linked Session은 전체 자동 backfill하지 않는다. inventory/dry-run 뒤 명시적으로 승인된 allowlist만 backfill한다.
- publisher 기본값은 poll 1초, batch 20, lease 30초이며 설정으로 조절할 수 있다.

## AttemptGroup event와 trace 규칙

- event target은 `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE`만 사용한다.
- RETAKE_AVAILABLE failureCode는 `REQUIRED_RESULTS_UNAVAILABLE`, `SUMMARY_UNAVAILABLE`, `GRADING_DEADLINE_EXCEEDED`, `RESULT_INTEGRITY_VIOLATION`만 사용한다.
- retry마다 같은 eventId와 canonical payload를 유지한다.
- `DELIVERED`는 30일, `DEAD_LETTER`는 90일 보존하고 PENDING·IN_FLIGHT·BLOCKED_AUTH에는 TTL을 두지 않는다.
- outbox에는 검증된 `traceId`, `parentSpanId`, `traceFlags`만 저장한다. raw `traceparent`, raw `tracestate`와 baggage를 저장하지 않는다.
- 각 publish attempt는 저장된 context를 parent로 새 span을 만들며 같은 traceId와 서로 다른 spanId를 사용한다.
- publish span의 `traceparent`를 inject한 뒤 SigV4를 마지막 논리적 변경 단계에서 수행하고 서명 뒤 request를 변경하지 않는다.
- trace context 문제는 새 fallback trace와 counter로 수렴시키며 업무 event 전달을 막거나 DEAD_LETTER로 보내지 않는다.

## AttemptGroup 금지 범위

- 기존 공개 API URL, HTTP Method, Parameter, Request/Response DTO와 `BaseResponse`를 변경하지 않는다.
- 실제 userId, reservationId, attemptGroupId와 내부 outbox 상태를 공개 Request/Response에 추가하지 않는다.
- 기존 시험 `retryCount`, Redis Key, S3 Object Key, Presigned URL, 음성 submit과 Polling 계약을 변경하지 않는다.
- Python AI request/Callback의 `user_id=examId`, URL과 JSON 계약을 변경하지 않는다.
- Billing consumer, Billing AttemptGroup 상태 머신과 Billing 저장소 코드를 Learning Core 작업 범위로 수정하지 않는다.
- UserMerged·owner rebind, 결제·subscription·coupon, entitlement 환불/보상과 Challenge 기능을 이 허용 범위에 포함하지 않는다.
- 실제 AWS Lattice/IAM/SG/ECS 리소스 생성·배포와 static AWS credential 추가를 수행하지 않는다.
- 사용자·Session·AttemptGroup 식별자, event payload·digest, 인증 header, provider/AI 원문을 일반 log·span attribute·metric tag에 기록하지 않는다.

## AttemptGroup rollout과 테스트

- writer와 publisher feature flag는 모두 기본 off다.
- publisher를 먼저 idle 활성화하고 인증·연결을 확인한 뒤 writer를 활성화한다.
- Mongo replica-set Transaction, index migration, Billing consumer 배포, Lattice security와 failure-injection E2E 전에는 production writer를 활성화하지 않는다.
- 상태 판정, terminal race, Transaction rollback·unknown commit, lease 경쟁, retry/status 분류, auth circuit, retention, W3C trace와 privacy 테스트를 추가한다.
- 실제 MongoDB, Redis, S3, Billing, AWS credential과 trace backend에 의존하지 않는 Mock·fixture 또는 격리된 테스트 구성을 사용한다.
- 구현 완료 후 `./gradlew clean test`를 실행하고 공개 API·AI·S3·Redis 계약 불변을 확인한다.

# 10초 챌린지 구현 허용 규칙

사용자 결정에 따라 10초 챌린지는 Learning Core의 신규 구현 범위에 포함한다.

승인된 계약은 다음 문서를 따른다.

- 프론트 API: `docs/contracts/ten-second-challenge-frontend-api.md` v1
- Learning Core–AI API: `docs/contracts/ten-second-challenge-ai-api.md` v1
- 상세 결정: `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`

위 계약과 다른 URL, HTTP Method, Request/Response field, enum, timeout, retry 또는 Callback 구조를 임의로 추가·변경하지 않는다. 계약 변경이 필요하면 구현보다 먼저 관련 문서와 프론트·AI 합의를 갱신한다.

## Challenge 범위와 격리

- 10초 챌린지는 기존 시험과 별도 domain·aggregate로 구현한다.
- 기존 `ExamSession`, `ExamResult`, `QuestionGradingJob`, `SummaryGradingJob`과 시험 `retryCount`를 Challenge에 재사용하지 않는다.
- 기존 시험 공개 API·DTO·`BaseResponse`, Redis key, S3 object key와 Python 시험 AI/Callback 계약을 변경하지 않는다.
- Challenge 신규 공개 응답도 기존 Learning Core `BaseResponse` 구조를 사용한다.
- feature flag 기본값은 off로 두고 catalog·index·AI·인증·staging E2E가 준비된 뒤 환경별로 활성화한다.
- 스트릭, 주간 학습 이력, 경제적 reward, 결제·credit와 랭킹은 Challenge 구현 범위에 포함하지 않는다.

## Challenge 사용자와 공개 API

- 10초 챌린지는 Identity의 `MEMBER`만 이용한다. Guest 요청은 `403`으로 거절한다.
- 실제 userId는 인증된 Identity에서 가져오며 Request Body, Path, Query와 Response에 추가하지 않는다.
- 모든 사용자용 attempt·날짜·결과 API는 인증 사용자 소유권을 검증한다.
- AI Callback에는 사용자용 CurrentUserProvider 소유권 검증을 적용하지 않고 service credential과 Job 식별자로 검증한다.
- 프론트 endpoint, field, 상태와 오류 code는 프론트 v1 계약을 그대로 구현한다.
- MVP에서는 사용자 audio 재생과 `audioUrl`을 제공하지 않는다.

## Challenge 콘텐츠와 날짜

- 기존 MongoDB 연결에서 `challenge_10s_questions` collection을 사용한다.
- `questions[].korean`을 공개 `promptKo`와 AI `prompt_ko`로 매핑한다.
- `questions[].difficulty`는 해석하지 않고 프론트에 정수 그대로 전달하며 AI 요청에는 포함하지 않는다.
- `questions[].referenceAnswer`는 문제 조회, attempt 생성과 upload-url 응답에서 노출하지 않는다.
- ChallengeAttempt 생성 시 `dayNumber`, `questionId`, `questionNumber`, `korean`, `referenceAnswer`, `difficulty`를 snapshot한다.
- 제출 또는 만료 terminal 이후의 참고 답안은 catalog 재조회가 아니라 attempt snapshot을 사용한다.
- `question.referenceAnswer`와 `aiResult.referenceAnswer`는 동일한 attempt snapshot 하나에서 조립한다.
- no-speech에서도 `gradingStatus=completed`, non-null `aiResult`, non-blank `aiResult.referenceAnswer`를 유지하고 transcript·verdict·correctedAnswer·feedback만 null로 반환한다.
- `app.challenge.enabled=true`로 처음 성공 기동한 KST 날짜를 `challenge_10s_catalog_state` singleton에 원자적으로 한 번만 저장하고 dayNumber 1로 사용한다.
- 재시작·재배포·scale-out으로 기준일을 초기화하지 않는다.
- dayNumber는 순환하지 않으며 콘텐츠가 없으면 다른 날 문제로 fallback하지 않고 `CHALLENGE_CONTENT_NOT_FOUND`로 fail-closed한다.

## Challenge attempt와 S3

- 녹음 시작 직전에 ChallengeAttempt를 먼저 생성하고 녹음 완료 후 같은 attemptId로 Presigned PUT URL을 별도 발급한다.
- attempt 제출 유효시간은 생성 시점부터 1시간이며 생성 당시 challengeDate에 귀속한다.
- 자정을 지나도 deadline 전에는 기존 attempt 제출을 허용하지만 이전 날짜의 새 attempt는 만들지 않는다.
- attemptId 기반 server-generated S3 object key를 생성하고 프론트가 key나 URL을 지정하게 하지 않는다.
- 같은 attempt의 upload URL 재발급은 동일 object key를 사용하고 새 응시로 계산하지 않는다.
- audio는 M4A/AAC-LC·`audio/mp4`, 16/44.1/48 kHz, mono/stereo, 최대 2 MiB 계약을 따른다.
- Challenge용 신규 S3 key를 설계할 수 있지만 기존 시험 S3 key 형식은 변경하지 않는다.

## Challenge AI와 Callback

- 기존 시험 `/evaluations`와 Feedback Callback을 재사용하지 않고 Challenge 전용 v1 endpoint를 사용한다.
- Learning Core가 S3 audio를 내려받아 multipart `audio_file`로 AI에 전달한다.
- AI 요청에는 `attempt_id`, 결정적 `job_id`, `grading_attempt`, `question_id`, `question_number`, `prompt_ko`, `reference_answer`, `audio_file`만 계약대로 전달한다.
- 실제 userId, 사용자 Access Token, difficulty, dayNumber, challengeDate, S3 위치와 credential을 AI에 전달하지 않는다.
- request와 Callback 모두 `attemptId`, `jobId`, `gradingAttempt`를 검증하고 이전 generation Callback은 상태를 덮어쓰지 않는 성공 no-op으로 처리한다.
- AI가 referenceAnswer를 Callback으로 echo하게 하지 않는다. 프론트 `aiResult.referenceAnswer`는 Learning Core attempt snapshot에서 조립한다.
- Callback deadline은 120초, 최대 AI generation은 3회다.
- transcript와 corrected answer는 각각 최대 1000자, feedback 각 항목은 최대 500자, Callback JSON은 UTF-8 기준 최대 16 KiB다.
- 방향별 service credential은 서로 분리해 환경변수·secret store로 주입하고 실제 값을 코드·문서·테스트·로그에 기록하지 않는다.
- 사용자 audio, transcript 전체, prompt·reference answer 전체와 AI provider 원문을 로그에 기록하지 않는다.

## Challenge 테스트

- catalog validator, one-time baseDate 초기화, 비순환 day resolver와 missing content fail-closed를 테스트한다.
- MEMBER 허용, Guest 거절과 사용자 attempt 소유권을 테스트한다.
- attempt 멱등 생성, 1시간 deadline, 자정 rollover, 동일 S3 key URL 재발급과 submit 멱등성을 테스트한다.
- referenceAnswer 조기 노출 금지, attempt snapshot 재현성과 정상/no-speech/processing/failed DTO를 테스트한다.
- AI request idempotency, duplicate·stale·conflict Callback, timeout·최대 generation과 payload 제한을 contract test로 검증한다.
- 실제 MongoDB, Redis, S3, AI provider와 secret에 의존하지 않고 Mock·fixture를 사용한다.
- 구현 완료 후 `./gradlew clean test`를 실행한다.

# Redis 및 S3 규칙

기존 Redis 상태 및 Lock 흐름을 임의로 변경하지 않는다.

application.yml에 app.redis.key-prefix 설정이 있더라도 명시적인 구현 요청이 있기 전에는 Redis 키 구조를 전면 변경하지 않는다.

기존 S3 Presigned URL 및 Object Key 구조를 임의로 변경하지 않는다.

별도의 S3 버킷을 사용하고 있으므로 app.s3.key-prefix를 적용하기 위해 기존 Object Key 계약을 자동으로 변경하지 않는다.

# 보안 및 설정 규칙

실제 Secret이나 자격증명을 저장소에 추가하지 않는다.

다음 값을 코드, 테스트, 문서, 로그에 작성하지 않는다.

- 실제 AWS Access Key
- 실제 AWS Secret Key
- 실제 MongoDB URI와 비밀번호
- 실제 JWT Secret
- 사용자 음성 데이터
- 사용자 발화 전체 Transcript
- Refresh Token

application.yml에서는 환경변수 참조 방식을 유지한다.

application-test.yml에는 가짜 테스트 값만 사용한다.

기존 POC용 GitHub Actions 배포 Workflow를 복원하지 않는다.

새로운 배포 Workflow는 명시적으로 요청받기 전에는 추가하지 않는다.

기존 POC EC2, 컨테이너 또는 배포 경로를 대상으로 설정하지 않는다.

# 테스트 규칙

변경한 비즈니스 로직에는 테스트를 추가한다.

테스트에서는 실제 운영 인프라를 호출하지 않는다.

다음 외부 의존성은 가능한 경우 Mock으로 처리한다.

- MongoDB Repository
- Redis
- AWS S3
- Python AI 서버
- Sentry

모든 구현 작업 후 다음 명령을 실행한다.

- ./gradlew clean test

기존 공개 API의 Request와 Response 구조가 바뀌지 않았는지 확인한다.

컴파일 경고를 현재 작업과 무관하게 일괄 수정하지 않는다.

# 코드 변경 규칙

기존 패키지 구조와 코드 스타일을 우선 따른다.

관련 없는 파일을 포맷하거나 리팩터링하지 않는다.

명시적인 요청 없이 새로운 운영 의존성을 추가하지 않는다.

작업 범위 밖의 버그를 발견하면 임의로 수정하지 말고 최종 보고에 기록한다.

기존 API 호환성에 영향을 줄 수 있는 변경은 구현 전에 명확히 보고한다.

# Git 규칙

Codex는 다음 명령을 직접 수행하지 않는다.

- git commit
- git push
- git push --force
- git reset --hard
- 기존 POC 저장소 수정
- 기존 POC 배포 실행

Codex는 파일 수정과 테스트까지만 수행한다.

커밋과 push는 사용자가 직접 수행한다.

# 작업 기록 규칙

- 모든 Codex 구현·분석·리뷰 작업은 종료 전에 `docs/codex/WORKLOG.md` 끝에 새 항목을 append한다.
- 모든 Codex 작업은 종료 전에 `docs/codex/CURRENT_STATE.md`를 최신 상태로 갱신한다.
- Jira 이슈 키가 있으면 WORKLOG와 CURRENT_STATE에 기록한다.
- WORKLOG의 과거 항목은 수정하거나 삭제하지 않는다.
- 코드 변경이 없는 분석 작업도 WORKLOG와 CURRENT_STATE에 기록한다.
- Secret과 Token은 WORKLOG와 CURRENT_STATE에 기록하지 않는다.
- Git commit과 push는 사용자가 직접 수행한다.

# 계획·조사 문서 작성 규칙

계획, 조사, 분석, 리뷰 문서는 상세 내용을 빠짐없이 확인하되 사용자가 중요한 내용을 먼저 읽을 수 있도록 다음 계층으로 작성한다.

1. 5줄 결론
2. 사용자가 반드시 읽어야 하는 내용
3. 사용자가 결정해야 하는 사항
4. 주요 위험과 미확인 사항
5. 현재 작업과 직접 관련된 설명
6. 상세 조사 근거와 전체 표를 담은 부록

- 각 결론에는 가능한 경우 관련 코드, 설정, 테스트 또는 계약 문서의 파일 근거를 연결한다.
- 확인된 구현 사실, 문서상 계획, Codex의 분석·추론을 명확히 구분한다.
- 상세 조사 결과를 삭제하거나 축약해서 잃지 말고 긴 목록, 비교표와 보조 근거는 부록으로 이동한다.
- 정확한 field, enum, timeout, retry, 보안·과금 조건과 금지 규칙은 요약만으로 대체하지 않고 관련 계약 원문을 연결한다.

# 작업 완료 보고 규칙

각 작업이 끝나면 다음 내용을 보고한다.

1. 변경한 파일
2. 변경한 동작
3. 유지한 외부 API 계약
4. 실행한 테스트
5. 테스트 결과
6. 남아 있는 위험 요소
7. 배포 전에 확인할 사항
8. 실제 diff에서 예상 밖으로 변경된 파일이나 범위가 있는지 여부
9. 다음 작업 전에 확인할 사항

- 구현 작업 완료 보고에서는 위 항목을 생략하지 않는다.
- 예상 밖의 변경이 없으면 없다고 명시하고, 있으면 사용자 변경과 이번 작업 변경을 구분해 설명한다.
- 테스트를 실행하지 않았다면 그 이유와 대신 수행한 검증을 기록한다.

# 코드 리뷰 우선순위

리뷰할 때 다음 문제를 우선 확인한다.

1. 기존 공개 API URL, Method, Parameter, Response가 변경되었는가
2. BaseResponse 구조가 변경되었는가
3. Python AI의 user_id가 examId가 아닌 실제 userId로 변경되었는가
4. Callback 외부 JSON 구조가 변경되었는가
5. 클라이언트 Request 또는 Response에 실제 userId가 추가되었는가
6. 시험 생성 시 ExamSession 저장이 누락되었는가
7. ExamResult.userId 저장이 누락되었는가
8. examId와 실제 userId가 뒤바뀌었는가
9. 사용자용 examId API의 소유권 검증이 누락되었는가
10. AI Callback에 사용자용 소유권 검증을 잘못 적용했는가
11. 테스트가 실제 AWS, Atlas, Redis 또는 Sentry에 의존하는가
12. Secret 또는 개인정보가 코드나 로그에 추가되었는가
13. trial과 terminate 외의 API가 삭제되거나 변경되었는가
14. 관련 없는 대규모 리팩터링이 포함되었는가
15. Challenge가 기존 Exam domain·retryCount·AI Callback을 재사용하거나 변경했는가
16. Challenge 사용자 API에서 MEMBER 검증 또는 attempt 소유권 검증이 누락됐는가
17. referenceAnswer가 제출 전 노출되거나 catalog 재조회로 snapshot과 달라질 수 있는가
18. no-speech 결과에서 `aiResult.referenceAnswer`가 사라지거나 `aiResult` 전체가 null이 되는가
19. difficulty·실제 userId·S3 위치가 Challenge AI 요청에 포함됐는가
20. stale·duplicate Callback이 현재 Challenge grading 결과를 덮어쓸 수 있는가
21. 재배포·scale-out으로 contentBaseDate가 초기화되거나 dayNumber가 순환하는가
22. Challenge 결과에 `audioUrl` 또는 사용자 음성·transcript 전체 로그가 노출되는가
