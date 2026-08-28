# Learning Core `UserMerged` consumer 최종 구현 계획

- 작성일: 2026-08-20
- 대상 저장소: `Too-Much-I/app-back-end-learning-core`
- 기준 브랜치: `develop`
- 기준 계약: Identity `UserMerged` schema version 1
- 결정 근거: `docs/codex/USER_MERGED_CONTRACT_DECISIONS.md`
- 사전 검토: `docs/codex/USER_MERGED_CONSUMER_REVIEW.md`
- 상태: 구현 기준 확정, 운영값·Mongo 성능 gate 이행 필요
- Jira: 별도 이슈 키 미제공

## 1. 목적과 완료 상태의 정의

Identity가 ACTIVE GUEST를 최종 ACTIVE MEMBER로 merge한 뒤 at-least-once로 전달하는 `UserMerged` event를 Learning Core가 안전하게 소비한다.

한 번의 Learning Core local Mongo Transaction에서 다음 결과를 확정한다.

1. source가 소유한 `exam_sessions`, `exam_results`, `exam_summaries`의 직접 `userId`를 target으로 이전한다.
2. 확정된 활성 시험 충돌 정책을 적용한다.
3. source ownership guard를 `MERGED` deny 상태로 바꾼다.
4. inbox event를 `PROCESSED`로 저장한다.

구현 완료는 endpoint가 응답하는 것만을 뜻하지 않는다. 모든 기존 writer와 Callback의 guard 전환, migration 준비, 실제 Mongo transaction 통합 테스트, Identity staging E2E, direct Transaction 성능 gate와 배포 runbook까지 통과해야 한다.

## 2. 확정 계약

### 2.1 선택 결과

| ID | 확정 내용 |
| --- | --- |
| C1-A | 사용자 Access JWT와 분리된 비대칭 서명 workload JWT와 JWKS를 사용한다. |
| C2-A | direct Transaction 후 `204`를 우선 구현하되 staging 성능 gate 실패 시 C2-B durable inbox + worker로 계약을 개정한다. |
| C3-A | merge는 source/target 양쪽 guard를 결정적 순서로 touch한다. |
| C4-A | target 활성 시험 우선, source만 활성일 때는 같은 `examId`로 target에 이전한다. |
| C5-A | 완료·폐기 history는 삭제하지 않고 target 이력에 합친다. |
| C6-A | merge 전 발급된 S3 PUT URL의 최대 5분 잔여 capability를 수용한다. |
| C7-A | Callback은 Session 조회, current owner guard, 결과와 Job 전이를 같은 Transaction에서 처리한다. |
| C8-A | Identity가 최종 target 불변식을 보장하고 Learning Core는 상충 event를 fail-closed한다. |
| C9 | 정상·동일 duplicate는 `204`, 일시적 경합은 `503`으로 고정한다. |
| C10-A | 인프라가 HTTPS와 network 접근을 제한하고 애플리케이션이 workload credential을 검증한다. |
| C11-A | publisher OFF 상태에서 guard writer 전환, 구버전 drain과 backfill 후 consumer를 활성화한다. |

### 2.2 유지할 기존 외부 계약

- 기존 공개 API URL, HTTP Method, Path/Query Parameter, Request Body와 Response DTO
- 공개 API의 `BaseResponse`
- 클라이언트가 실제 `userId`를 보내거나 받지 않는 계약
- `retryCount` 의미
- Python AI request와 Callback의 `user_id = examId`
- 기존 AI Callback JSON의 나머지 필드
- Redis `exam:status:{examId}` Key/TTL
- S3 `temp/{examId}/q_{questionNumber}_r{retryCount}.wav` Object Key
- Presigned URL 발급, 음성 제출과 Polling 흐름

신규 internal endpoint의 `204` 빈 body는 공개 API `BaseResponse` 변경이 아니다.

### 2.3 명시적 비범위

- Identity 사용자 merge 구현 변경
- 기존 공개 API나 DTO 재설계
- source JWT를 target actor로 치환하는 alias
- Redis Key 또는 S3 Object Key 재설계
- 이미 발급된 S3 URL을 즉시 취소하는 기능
- Kafka, SQS 또는 새 메시지 큐
- 스트릭, 챌린지, 단어장처럼 현재 저장소에 없는 aggregate
- processed merge의 자동 역병합

## 3. 현재 코드 기준 ownership과 writer inventory

### 3.1 직접 migration 대상

| 컬렉션 | 직접 owner | 처리 |
| --- | --- | --- |
| `exam_sessions` | `userId` | 모든 source Session을 target으로 변경하고 활성 충돌 정책 적용 |
| `exam_results` | `userId`, `examId` | `userId`만 target으로 변경, `examId` 유지 |
| `exam_summaries` | `userId`, `examId` | `userId`만 target으로 변경, `examId` 유지 |

### 3.2 간접 ownership으로 rewrite하지 않을 대상

| 대상 | ownership | 처리 |
| --- | --- | --- |
| `question_grading_jobs` | `examId` | rewrite하지 않고 계속 처리 |
| `summary_grading_jobs` | `examId` | rewrite하지 않고 계속 처리 |
| `azure_results` | `examId` | rewrite하지 않음 |
| `speechace_results` | `examId` | rewrite하지 않음 |
| Redis 시험 상태 | `examId` | key/TTL 유지, invalidation 불필요 |
| S3 제출 객체 | `examId` | key와 객체를 이동하지 않음 |
| `mock_exams`, `questions` | 비사용자 catalog | migration 금지 |

### 3.3 반드시 guard 경계로 전환할 쓰기

| 경로 | 현재 대표 코드 | 목표 DB 경계 | commit 후 작업 |
| --- | --- | --- | --- |
| 시험 생성·기존 시험 abandon·legacy 상태 보정 | `ExamSessionManager.startNew()` | current user guard touch + Session 상태 변경/insert | Redis 초기 상태와 문제지 GET URL 조립 |
| PUT URL 발급 | `ExamServiceImpl.getPresignedUrl()` | owner 확인 + current user guard touch | 없음. 로컬 presign은 같은 command 안에서 생성 가능 |
| 음성 제출 | `submitAudio()` → `submitQuestion()` | guard touch + deterministic Question Job 생성/상태 전이 | S3 확인·AI dispatch |
| 시험 단위 재채점 | `retryGrading()` → `retryExam()` | guard touch + recovery 대상 Job 전이 | S3 확인·AI dispatch·Summary schedule |
| Feedback Callback | `updateExamResult()` | Session 재조회 + owner guard touch + Result insert + Question Job 전이 | Redis projection·Summary schedule |
| Summary Callback | `updateExamResult()` | Session 재조회 + owner guard touch + Summary insert + Summary Job/Session 전이 | Redis projection |
| SpeechAce Callback | `saveSpeechAceResult()` | Session 재조회 + owner guard touch + 결과 insert | 없음 |
| Azure Callback | `processAzureCallback()` | Session 재조회 + owner guard touch + 결과 insert | 없음 |
| Session 완료·abandon·legacy 보정 | `ExamSessionManager` repository update | 해당 Session current owner guard와 같은 Transaction | 로그·projection |
| background Question/Summary Job 전이 | `ExamGradingService`, `SummaryDispatchScheduler` | Session current owner 확인과 guard touch 후 Job claim/전이 | AI 호출 |

순수 조회는 request 시작 시 merged marker를 확인하되 revision을 증가시키지 않는다. guard 확인이 merge commit보다 먼저 선형화된 in-flight read는 완료될 수 있고, merge commit 뒤 시작한 guard 확인은 항상 source를 거절한다. 모든 쓰기는 guard document를 실제로 update해 merge와 Mongo write conflict를 만든다.

## 4. 목표 구조

### 4.1 신규 package와 component

기존 패키지 스타일에 맞춰 `web.tosunsaeng.domain.usermerge`를 추가한다.

```text
domain/usermerge/
  api/
    UserMergedInternalController
    UserMergedInternalExceptionAdvice
  application/
    UserMergedConsumerService
    UserMergeMigrationService
    UserOwnershipGuardService
    UserOwnedTransactionExecutor
  domain/entity/
    UserOwnershipGuard
    UserMergedInboxEvent
  domain/enums/
    OwnershipGuardState
    UserMergedInboxStatus
  domain/repository/
    UserOwnershipGuardRepository
    UserMergedInboxRepository
  dto/
    UserMergedEventRequest
  support/
    UserMergedEventNormalizer
    UserMergedPayloadDigest
    UserMergedPayloadLimitFilter
```

global 구성에는 다음 역할을 분리한다.

```text
global/config/
  MongoTransactionConfig
  UserMergedProperties
  UserMergedStartupValidator
  UserMergedSecurityConfig

global/config/security/
  WorkloadPrincipalAuthorizationManager
  MergedUserAuthorizationFilter
  MergedUserAccessDeniedHandler
```

실제 클래스명은 구현 중 기존 naming과 겹치면 조정할 수 있지만 책임과 경계는 합치지 않는다.

### 4.2 `UserOwnershipGuard`

collection: `user_ownership_guards`

| 필드 | 타입/제약 | 의미 |
| --- | --- | --- |
| `_id` / `userId` | canonical UUID, unique | 사용자별 동시성 key |
| `state` | `ACTIVE` 또는 `MERGED` | actor 허용 상태 |
| `revision` | non-negative long | 모든 user-owned write와 merge의 touch 경계 |
| `targetUserId` | MERGED일 때 canonical UUID | migration/audit용이며 authorization alias가 아님 |
| `mergedAt` | MERGED일 때 `Instant` | Identity event 발생 시각 |
| `eventId` | MERGED일 때 canonical UUID | 상태를 만든 event |
| `createdAt`, `updatedAt` | `Instant` | 운영 추적 |

`_id` unique를 최종 경계로 사용한다. 별도 userId 중복 필드를 둘 경우 값 일치 검증을 추가하고, 가능하면 `_id` 하나를 단일 진실 공급원으로 사용한다.

guard가 없는 사용자는 read에서 아직 merge되지 않은 것으로 취급한다. 첫 user-owned write는 같은 Transaction에서 ACTIVE guard를 insert/touch한다. merge가 없는 source에 MERGED guard를 insert하는 것과 첫 ACTIVE insert가 경쟁하면 `_id` unique constraint와 transaction retry 후 최종 state를 다시 읽어 판정한다.

### 4.3 `UserMergedInboxEvent`

collection: `user_merged_inbox_events`

| 필드 | 타입/제약 | 의미 |
| --- | --- | --- |
| `_id` / `eventId` | canonical UUID, unique | 영구 idempotency key |
| `schemaVersion` | integer, v1은 1 | wire version |
| `payloadDigest` | lowercase SHA-256 hex | semantic payload 불변성 |
| `sourceUserId`, `targetUserId` | canonical UUID | migration 관계 |
| `occurredAt` | `Instant` | Identity 발생 시각 |
| `receivedAt`, `processedAt` | `Instant` | consumer 시간 |
| `status` | direct v1은 `PROCESSED` | commit 완료 상태 |

direct v1에서는 `PENDING` inbox를 먼저 commit하지 않는다. migration, source deny와 최종 `PROCESSED`가 한 Transaction에서 commit되거나 전부 rollback된다. retention/TTL은 감사 보존 정책이 별도로 확정되기 전에는 추가하지 않는다.

### 4.4 Mongo Transaction 기반

`MongoTransactionManager`를 명시적으로 구성하고, user-owned command는 이름이 명확한 `TransactionTemplate` 기반 executor를 사용한다.

프로그램 방식으로 두는 이유는 다음과 같다.

- 기존 `@Transactional`이 transaction manager 추가와 함께 의도치 않게 실제 Mongo Transaction으로 바뀌는 것을 방지한다.
- self-invocation으로 annotation이 무시되는 경계를 피한다.
- DB commit 전 단계와 Redis/S3/AI 같은 commit 후 단계를 명확히 분리한다.
- transient transaction과 unknown commit result의 수렴 로직을 command별 business key로 검증할 수 있다.

구현 시 기존 `ExamReadService`와 `ExamServiceImpl`의 `@Transactional(readOnly=true)`를 감사한다. 순수 Mongo read에 불필요한 transaction이 생기지 않도록 제거하거나 transaction manager를 명시한다. Azure Callback의 기존 `@Transactional`은 새 command 경계로 이동한다.

retry 정책:

1. `TransientTransactionError`: bounded backoff 후 전체 DB command 재시도.
2. `UnknownTransactionCommitResult`: 무조건 같은 mutation을 반복하지 않고 business key/inbox/guard/Job을 재조회해 commit 여부에 수렴.
3. duplicate key: active Transaction 안에서 catch하고 계속하지 않는다. Transaction을 abort한 뒤 외부 retry coordinator가 결정적 ID나 guard/inbox를 재조회한다.
4. 최대 시도 초과: mutation 성공을 추측하지 않고 `503` 또는 기존 API 오류 경로로 반환한다.

수렴 key:

- merge: `eventId`
- Question/Summary Job: 기존 deterministic job ID
- Callback 결과: 기존 deterministic result ID
- 시험 생성: user active partial unique index와 생성 후 active Session 재조회

### 4.5 DB 단계와 외부 단계 분리

Mongo Transaction 안에서 S3 network 요청, AI HTTP 호출, Redis write를 실행하지 않는다.

```text
사용자/Callback 요청
→ guard + Mongo state command commit
→ immutable outcome/claim 반환
→ commit 후 S3/AI/Redis 실행
```

예외는 `S3Presigner`의 PUT URL 로컬 서명이다. 이는 S3 network 호출이 아니고 매우 짧으므로 다음 경계로 처리한다.

```text
Session ownership 확인
→ current user guard touch
→ 같은 command에서 URL 로컬 서명
→ Transaction commit
→ 응답 반환
```

merge가 먼저 commit하면 재시도에서 `MERGED`를 보고 거절한다. presign command가 먼저 commit하면 URL은 merge 전에 승인된 최대 5분 capability로 C6-A 범위에 들어간다.

외부 단계가 실패하면 기존 Question/Summary Job의 FAILED/retry 상태 전이를 사용한다. 새 Redis Key, queue 또는 outbox를 이 범위에서 추가하지 않는다.

## 5. endpoint와 security 설계

### 5.1 internal endpoint

```text
POST /internal/v1/events/user-merged
Content-Type: application/json
Maximum raw body: 4096 bytes
Success: 204 No Content
```

Controller는 `BaseResponse`를 사용하지 않는다. 이 endpoint는 사용자 API OpenAPI 그룹에서 제외하거나 internal 전용 문서로 분리한다.

### 5.2 별도 `SecurityFilterChain`

`@Order(1)`의 internal 전용 chain이 `/internal/v1/events/user-merged`만 match한다.

- workload 전용 `JwtDecoder`
- decoder에서 issuer, timestamp, audience와 algorithm 검증
- 인증 성공 뒤 별도 AuthorizationManager에서 principal claim/value allowlist 검증
- clock skew와 JWKS `kid` rotation/overlap
- stateless, CSRF/form/basic/logout 비활성
- CORS 비사용
- 사용자 Access JWT decoder/authority와 bean qualifier 분리
- local/test legacy chain보다 먼저 match해 internal endpoint permit-all 방지

기존 사용자 chain은 다음 순서로 유지한다.

```text
사용자 JWT signature/issuer/audience/expiry/sub 검증
→ MergedUserAuthorizationFilter
→ source guard MERGED이면 403 ACCOUNT_MERGED_TOKEN_REJECTED
→ 기존 Controller/ownership 검증
```

public AI Callback은 기존처럼 사용자 JWT 대상이 아니며 merged-user filter를 적용하지 않는다.

### 5.3 설정과 startup 검증

예상 설정 namespace:

```yaml
app:
  user-merged:
    consumer-enabled: ${USER_MERGED_CONSUMER_ENABLED:false}
    workload:
      issuer: ${USER_MERGED_WORKLOAD_ISSUER:}
      jwk-set-uri: ${USER_MERGED_WORKLOAD_JWK_SET_URI:}
      audience: ${USER_MERGED_WORKLOAD_AUDIENCE:}
      principal-claim: ${USER_MERGED_WORKLOAD_PRINCIPAL_CLAIM:}
      principal: ${USER_MERGED_WORKLOAD_PRINCIPAL:}
      algorithm: ${USER_MERGED_WORKLOAD_ALGORITHM:}
      max-token-lifetime: ${USER_MERGED_WORKLOAD_MAX_TOKEN_LIFETIME:}
      clock-skew: ${USER_MERGED_WORKLOAD_CLOCK_SKEW:}
```

실제 값과 algorithm allowlist는 Identity·인프라 합의 후 채운다. Secret이나 token을 설정 예시, 테스트 로그, 문서에 넣지 않는다.

startup validator는 consumer가 enabled인 staging/prod에서 다음을 fail-closed한다.

- blank/placeholder/non-HTTPS issuer·JWKS
- blank audience/principal claim/value
- 허용되지 않은 algorithm
- 비양수 max token lifetime, 음수 clock skew 또는 합의 범위 밖 값
- workload decoder/chain 미등록
- Mongo transaction 미지원 또는 필수 guard/inbox/migration index 누락

local에서는 기본 disabled로 두고, internal contract test는 가짜 RSA key와 test JWKS를 사용해 명시적으로 enabled한다. signature/issuer/audience/expiry/algorithm 오류는 인증 실패 `401`, 그 검증을 통과했지만 principal claim/value가 불일치하면 authorization 실패 `403`으로 분리한다.

### 5.4 body와 schema 검증

`Content-Length`만 믿지 않는다. workload 인증·authorization 뒤에 실행되는 endpoint 전용 filter가 chunked body도 4097번째 byte에서 중단하고 `413`을 반환하며, 최대 4096 byte만 wrapper에 보관해 MVC로 전달한다. 인증되지 않은 요청이 body buffering 비용을 먼저 유발하지 않도록 filter order를 contract test로 고정한다.

- `application/json` compatible media type만 허용, 아니면 `415`
- malformed JSON은 `400`
- unknown JSON property는 v1 DTO에서 명시적으로 무시
- 필수 필드 null/blank, non-canonical UUID, source=target, unknown schema는 `422`
- UUID는 parse 결과의 lowercase canonical `UUID.toString()`과 입력이 정확히 같아야 함
- `occurredAt`은 `Instant`로 parse하고 UTC `Instant.toString()`으로 normalize

payload digest는 확정된 NUL-delimited semantic input과 SHA-256 lowercase hex를 그대로 구현한다. digest test에는 고정 입력/기대 hex vector를 둬 언어별 구현 차이를 방지한다.

### 5.5 status mapping

| 상황 | status | mutation |
| --- | --- | --- |
| 신규 event commit | `204` | commit 완료 |
| 동일 eventId + 동일 digest | `204` | no-op |
| malformed JSON | `400` | 없음 |
| body 4 KiB 초과 | `413` | 없음 |
| Content-Type 불일치 | `415` | 없음 |
| field/schema 의미 오류 | `422` | 없음 |
| eventId/digest 또는 guard 상충 | `409` | 없음 |
| workload token 누락·유효성 실패 | `401` | 없음 |
| principal 불허 | `403` | 없음 |
| rate limit | `429` | 없음, `Retry-After` 가능 |
| 처리 winner 미확정·일시 DB 장애 | `503` | 성공 추측 금지, `Retry-After` 가능 |

internal 전용 `@RestControllerAdvice`와 security handler가 위 status를 우선 적용한다. 오류 body는 없어도 되며 필요하면 stable internal code와 correlation ID만 포함한다. source/target UUID, raw payload와 token은 응답이나 로그에 넣지 않는다.

## 6. direct consumer Transaction 상세 순서

### 6.1 request 전처리

1. workload 인증
2. content type과 raw size 검사
3. JSON parse와 schema v1 검증
4. UUID/Instant normalization
5. semantic digest 계산
6. `receivedAt` 기록

### 6.2 Transaction body

```text
1. inbox eventId 조회
   ├─ 같은 digest + PROCESSED → duplicate outcome
   ├─ 다른 digest → conflict
   └─ 없음 → 신규 처리

2. source/target UUID를 canonical 문자열 순서로 정렬
3. 두 guard document를 그 순서로 insert/touch
   - source는 ACTIVE이거나 absent여야 함
   - target은 ACTIVE이거나 absent여야 함
   - target MERGED면 conflict

4. source/target의 effective active Session 조회
   ├─ target active + source active → source를 ABANDONED/active=false
   ├─ target active only → target 유지
   ├─ source active only → 상태 유지 후 target owner로 이전
   └─ 둘 다 없음 → history만 이전

5. source exam_results.userId → target
6. source exam_summaries.userId → target
7. source exam_sessions.userId → target
8. source guard ACTIVE → MERGED
   - targetUserId, occurredAt, eventId 저장
9. inbox PROCESSED insert
10. commit
```

활성 source를 abandon하는 update를 Session owner rewrite보다 먼저 수행해 `{userId:1, active:true}` partial unique index 충돌을 피한다. 모든 변경은 같은 Transaction이므로 외부에서는 중간 순서를 관찰할 수 없다.

### 6.3 commit 후 응답과 duplicate 경합

- 신규 commit: `204`
- 같은 event 동시 요청의 loser가 unique/write conflict를 만나면 bounded wait/backoff 후 inbox 재조회
- winner의 같은 digest `PROCESSED` 확인: `204`
- winner rollback 또는 제한 시간 안에 결과 미확정: `503`
- 다른 digest나 guard 상충: `409`와 bounded 경보

same source/target이라도 다른 `eventId`는 producer 계약 위반이므로 성공으로 숨기지 않는다.

### 6.4 Callback과 merge의 수렴

Callback Transaction은 다음 순서를 사용한다.

```text
examId로 Session 재조회
→ Session의 현재 userId guard touch
→ abandoned 여부와 Job generation/recovery fencing 확인
→ 결과 insert와 Job/Session 상태 전이
→ commit
→ Redis projection 또는 다음 dispatch
```

- Callback commit이 먼저면 merge Transaction이 guard conflict 후 재시도해 새 결과까지 target으로 이전한다.
- merge commit이 먼저면 Callback 재시도가 Session의 target owner를 다시 읽고 결과의 `userId`를 target으로 저장한다.
- merge 중 source 시험이 abandon되면 늦은 Callback은 기존 abandoned 정책으로 no-op한다.
- Callback에는 `CurrentUserProvider` 기반 사용자 소유권 검증을 적용하지 않는다.

## 7. 기존 writer 전환 계획

### 7.1 공통 command 규칙

모든 user-owned Mongo command는 다음 template을 사용한다.

```text
현재 Session/actor 확인
→ guard absent면 ACTIVE insert 또는 ACTIVE state 조건부 revision increment
→ business mutation
→ commit
→ 외부 작업
```

guard update count가 0이면 state를 다시 읽는다.

- `MERGED`: 사용자 요청은 `ACCOUNT_MERGED_TOKEN_REJECTED`, background/Callback은 현재 Session을 재조회해 수렴
- revision 경합: bounded transaction retry
- document 없음: ACTIVE/MERGED 동시 insert 결과를 다시 읽어 판정

### 7.2 서비스별 변경

#### `ExamSessionManager`

- `startNew(userId)`의 abandon, completion count 조회, 새 Session insert를 한 DB command로 묶는다.
- 시작 시 user guard를 touch한다.
- active unique 충돌/unknown commit이면 active Session을 재조회해 기존 reuse semantics에 수렴한다.
- legacy Session 상태 보정도 guard와 같은 Transaction에서만 수행한다.
- Redis 초기화와 질문 GET URL 조립은 commit 뒤 유지한다.

#### `ExamServiceImpl`

- 순수 orchestration과 public DTO 변환을 유지한다.
- ownership이 있는 DB write와 Callback mutation은 별도 command component로 이동한다.
- `getPresignedUrl`은 Session ownership + guard touch + 로컬 presign command로 변경한다.
- 순수 read의 불필요한 `@Transactional(readOnly=true)`를 제거한다.
- 공개 Request/Response method signature는 변경하지 않는다.

#### `ExamGradingService`

- Question/Summary Job 생성·claim·완료·실패·recovery 전이를 transaction-aware command로 분리한다.
- duplicate key를 active Transaction 안에서 catch해 계속하지 않는다.
- DB phase는 `QuestionDispatchClaim`, `SummaryDispatchClaim` 또는 no-op outcome만 반환한다.
- S3 HEAD/download, AI HTTP, Redis projection과 scheduler submit은 commit 후 실행한다.
- 기존 deterministic Job ID, generationAttempt, recoveryCycle과 max dispatch semantics를 유지한다.

#### `SummaryDispatchScheduler`

- dispatch 직전 Session과 current owner guard를 다시 확인한다.
- Job claim은 guard와 같은 Transaction에서 수행한다.
- merge로 Session이 abandoned면 AI 전송 없이 기존 실패/no-op 상태에 수렴한다.
- executor queue와 기존 재시도 제한은 유지한다.

#### Feedback/Summary/SpeechAce/Azure Callback

- 각 callback payload parse와 metadata 검증은 Transaction 밖에서 수행한다.
- Session/guard/result/Job mutation을 하나의 callback command로 묶는다.
- raw Azure/SpeechAce/Feedback payload와 transcript를 새 로그에 남기지 않는다.
- 기존 멱등 ID와 stale generation/recovery fencing을 유지한다.

### 7.3 merged source 요청 차단

JWT mode의 모든 사용자 API는 controller 진입 전 source marker를 검사한다.

- guard `MERGED`: HTTP 403, 기존 `BaseResponse` 형태, code `ACCOUNT_MERGED_TOKEN_REJECTED`
- `targetUserId`로 SecurityContext나 `CurrentUserProvider` 결과를 바꾸지 않음
- source token으로 target Session query를 수행하지 않음
- marker 조회 장애는 staging/prod에서 fail-closed 5xx 처리

local/test Legacy mode는 기존 고정 UUID 흐름을 유지한다. internal endpoint는 Legacy permit-all chain에 포함되지 않는다.

## 8. Mongo migration과 index 계획

### 8.1 신규 maintenance script

다음 파일을 추가한다.

```text
scripts/mongodb/user-merged-prepare.js
scripts/mongodb/user-merged-prepare.test.js
```

기존 script 규칙을 따른다.

- `MONGODB_URI`, 정확한 `MONGODB_DATABASE` 필수
- URI, credential, UUID 원문과 payload를 출력하지 않음
- 기본 dry-run
- 정확한 apply flag가 있을 때만 write
- system database 거절
- 모든 구버전 writer가 drain됐다는 명시적 acknowledgement 없으면 apply fail
- 충돌을 자동 삭제/overwrite하지 않고 보고 후 중단

### 8.2 dry-run 검사

- `exam_sessions`, `exam_results`, `exam_summaries`의 owner 수와 문서 수 분포
- direct owner document와 Session owner 불일치
- orphan Result/Summary
- user별 `active=true` 중복과 legacy effective-active 이상
- canonical UUID가 아닌 owner
- 필요한 index 없음/이름 충돌/정의 불일치/hidden index
- guard 기존 MERGED/상충 데이터
- transaction 한 건에서 이동할 예상 P50/P95/P99/max 문서 수와 BSON 크기 근사치

실제 userId는 출력하지 않고 bounded count와 비가역 hash를 사용해야 할 경우에도 운영 승인된 방식만 사용한다.

### 8.3 apply

1. guard-aware 새 writer를 배포한다.
2. 구버전 instance를 전부 drain한다.
3. publisher와 Identity merge가 OFF인지 재확인한다.
4. DB backup을 확보한다.
5. dry-run conflict 0을 확인한다.
6. 기존 owner UUID에 ACTIVE guard를 idempotent `$setOnInsert`로 backfill한다.
7. migration 성능 index를 생성한다.
8. 최종 cross-collection owner 일치와 index를 재검증한다.

신규 사용자는 첫 write에서 guard가 생성되므로 backfill 이후 새 user 유입을 막을 필요는 없다.

### 8.4 index

최소 검증 대상:

- `user_ownership_guards`: `_id` unique
- `user_merged_inbox_events`: `_id` unique
- `exam_sessions`: 기존 `uniq_exam_sessions_active_user`
- `exam_sessions`: 기존 userId 선두 completion index
- `exam_results`: `{userId:1}` migration index
- `exam_summaries`: `{userId:1}` migration index

Result/Summary userId index는 staging `explain("executionStats")`와 document 분포를 보고 최종 required 여부를 확정한다. direct migration P99에 필요하면 운영 필수 index와 startup validator에 포함한다. 애플리케이션 startup에서 index를 자동 생성하지 않는다.

## 9. 구현 단계와 산출물

### Phase 0. 외부 계약값과 인프라 확인

담당: Identity, Learning Core, 인프라/보안, DBA

- workload issuer/JWKS/algorithm/audience/principal claim/value/TTL/skew/rotation 확정
- Identity 인계서에 C8 producer 불변식과 C9 status/retry 반영
- TLS 종료, trusted forwarded header, ingress/security group/WAF 4 KiB 책임 확정
- staging/prod Mongo가 replica set 또는 transaction 지원 sharded cluster인지 확인
- Identity 5초 read timeout과 retry 설정 재확인

완료 gate: credential 표에 TBD가 없고 Secret 없이 검증 가능한 claim 명세가 승인됨.

### Phase 1. Mongo Transaction과 guard foundation

- `MongoTransactionConfig`, `UserOwnedTransactionExecutor`
- guard entity/repository/service/state
- bounded retry와 unknown commit convergence
- `ACCOUNT_MERGED_TOKEN_REJECTED`
- merged-user authorization filter/handler
- 기존 `@Transactional` 영향 감사

완료 gate: replica-set integration test에서 source write/merge, target write/merge와 absent guard insert 경합이 단일 결과로 수렴.

### Phase 2. 기존 writer와 Callback 전환

- Exam Session command
- PUT URL capability command
- Question/Summary Job DB phase와 external phase 분리
- 네 Callback transaction 전환
- scheduler/background claim 전환
- Redis/S3/AI 기존 계약 회귀

완료 gate: 모든 repository mutation call site가 guard 적용 대상인지 inventory test/수동 checklist로 확인되고 구버전 writer와 섞이지 않는 배포 artifact 준비.

### Phase 3. preparation migration과 index

- dry-run/apply script와 Node unit test
- ACTIVE guard backfill
- owner mismatch/orphan/active anomaly 검사
- migration index와 startup validator
- backup/staging rehearsal

완료 gate: staging copy dry-run conflict 0, apply 후 재검증 성공.

### Phase 4. internal security와 endpoint contract

- workload properties/startup validator
- 별도 SecurityFilterChain/decoder/principal validator
- body limit/content type filter
- DTO/normalizer/digest
- internal advice/status mapping
- `204` endpoint

완료 gate: 사용자 JWT로 internal endpoint 접근 불가, workload positive/negative contract test와 raw/chunked 4 KiB 경계 통과.

### Phase 5. inbox와 ownership migration

- inbox entity/repository
- direct Transaction service
- 활성 시험 4-case matrix
- owner rewrite와 source guard transition
- concurrent duplicate/response-loss/conflict handling
- metrics/logs

완료 gate: rollback injection, duplicate, Callback/source/target concurrent writer test에서 partial commit과 stale source owner 0건.

### Phase 6. staging E2E와 performance gate

- 실제 HTTPS/workload identity
- publisher timeout/retry/duplicate/response-loss
- process termination과 재시작
- source token deny와 target history 확인
- C6 5분 잔여 PUT 시나리오
- production 유사 P50/P95/P99/max 데이터량과 latency

완료 gate: 합의 latency 기준 통과. 실패하면 production 활성화 없이 C2-B 문서 개정.

### Phase 7. production rollout

- dashboard/alert와 on-call runbook 확인
- consumer 배포, ingress 검증
- Identity publisher를 제한적으로 활성화
- synthetic/fake account canary
- merge feature 활성화
- backlog/error/latency 집중 관찰

완료 gate: processed/duplicate/failure, transaction latency, source denial과 publisher backlog가 합의 범위.

## 10. 예상 변경 파일

### 신규 production code

- `src/main/java/web/tosunsaeng/domain/usermerge/**`
- `src/main/java/web/tosunsaeng/global/config/MongoTransactionConfig.java`
- `src/main/java/web/tosunsaeng/global/config/UserMergedProperties.java`
- `src/main/java/web/tosunsaeng/global/config/UserMergedStartupValidator.java`
- `src/main/java/web/tosunsaeng/global/config/UserMergedSecurityConfig.java`
- `src/main/java/web/tosunsaeng/global/config/security/WorkloadPrincipalAuthorizationManager.java`
- `src/main/java/web/tosunsaeng/global/config/security/MergedUserAuthorizationFilter.java`
- `src/main/java/web/tosunsaeng/global/config/security/MergedUserAccessDeniedHandler.java`

### 기존 production code 수정

- `global/config/SecurityConfig.java`
- `global/error/code/status/ErrorStatus.java`
- `domain/exams/application/ExamServiceImpl.java`
- `domain/exams/application/ExamSessionManager.java`
- `domain/exams/application/ExamGradingService.java`
- `domain/exams/application/SummaryDispatchScheduler.java`
- 필요한 Exam repository query/update
- `src/main/resources/application.yml`

### migration/runbook

- `scripts/mongodb/user-merged-prepare.js`
- `scripts/mongodb/user-merged-prepare.test.js`
- `scripts/mongodb/README.md`
- UserMerged deployment/runbook 문서

### test

- internal API/auth/schema/digest/payload limit 단위·MVC 테스트
- guard/consumer/callback command 단위 테스트
- public API contract regression 테스트
- replica-set Mongo transaction integration test source set
- migration Node test

정확한 파일 수와 세부 class 분리는 구현 중 책임 응집도를 기준으로 조정하되, 관련 없는 파일 리팩터링은 하지 않는다.

## 11. 테스트 계획

### 11.1 단위 테스트

- canonical UUID와 source=target
- Instant normalization
- digest NUL delimiter와 고정 SHA-256 vector
- unknown optional field 무시
- unknown schema 거절
- guard ACTIVE/MERGED/absent 상태
- source/target 정렬 순서
- 활성 시험 4-case matrix
- 같은/different event digest
- transaction retry 분류
- 민감정보 없는 log argument

### 11.2 Security/MVC 계약 테스트

- 정상 workload token → `204`
- missing/expired/과도한 lifetime/wrong signature/issuer/audience/algorithm → `401`
- 허용되지 않은 principal → `403`
- 사용자 Access JWT로 internal endpoint → 거절
- local/test Legacy permit-all로 internal endpoint 우회 불가
- malformed `400`, over-size `413`, media type `415`, semantic `422`, conflict `409`, transient `503`
- 정확히 4096 byte와 chunked 4097 byte
- `204` body 없음
- source merged 사용자 API → 기존 `BaseResponse` + `ACCOUNT_MERGED_TOKEN_REJECTED`
- AI Callback public 범위와 JSON unchanged

### 11.3 Mongo replica-set integration test

mock repository만으로 Transaction 완료를 판정하지 않는다. Testcontainers 또는 CI 전용 replica-set Mongo에서 다음을 검증한다.

- migration 세 collection + source deny + inbox의 all-or-nothing
- 각 mutation 단계 failure injection rollback
- 같은 event 순차/동시 duplicate
- response loss 뒤 duplicate `204`
- 같은 eventId/different digest no mutation
- source/target guard 획득 순서가 반대인 event의 교착/재시도
- source write vs merge
- target write vs merge
- absent ACTIVE insert vs MERGED insert
- Feedback/Summary/Azure/SpeechAce Callback vs merge
- target active/source active partial unique 충돌 정책
- unknown commit result convergence
- process 종료 후 retry

통합 테스트를 별도 Gradle source set으로 둘 경우 CI 필수 task로 연결하고, 기본 `./gradlew clean test`와 함께 실행 방법을 문서화한다.

### 11.4 기존 계약 회귀

- 공개 Controller method/URL/parameter snapshot
- Request/Response DTO와 `BaseResponse`
- `user_id=examId`
- `retryCount`
- Redis Key/TTL
- S3 Object Key와 expiresIn
- abandoned Callback no-op
- Job idempotency/generation/recovery
- 시험 history 합집합 후 cycle/sequence 배정

### 11.5 migration script

```text
node --test scripts/mongodb/user-merged-prepare.test.js
```

- dry-run 기본
- apply acknowledgement
- exact database 선택과 system DB 거절
- URI/UUID 비노출
- orphan/mismatch/duplicate active 검출
- compatible/conflicting index
- idempotent backfill
- final verification 실패 시 non-zero exit

### 11.6 최종 로컬 명령

```text
./gradlew clean test
./gradlew <mongo-transaction-integration-task>
node --test scripts/mongodb/user-merged-prepare.test.js
git diff --check
```

실제 AWS, 운영 Mongo/Redis, Python AI와 Sentry를 단위 테스트에서 호출하지 않는다.

## 12. 관측과 개인정보

권장 metric:

- `user_merged_event_total{outcome=processed|duplicate|conflict|failed}`
- `user_merged_transaction_duration`
- delivery/processing/total lag
- transaction retry/timeout
- aggregate migrated document count distribution
- merged source token rejection
- publisher backlog/terminal failure는 Identity 측 metric과 연결

metric tag에 eventId, source/target userId, examId를 넣지 않는다.

로그는 `event`, `outcome`, bounded `reason`, `schemaVersion`, eventId와 처리시간 중심으로 남긴다. source/target UUID, Authorization, raw payload, email/phone, S3 URL/Key, transcript와 Callback 원문은 기록하지 않는다. 예상 가능한 duplicate/conflict는 Sentry event로 만들지 않고 metric/CloudWatch alert로 운영한다.

## 13. 성능 gate

Identity publisher read timeout은 5초이므로 5초 전체를 DB Transaction 예산으로 사용하지 않는다.

staging에서 다음을 측정한다.

- source 직접 owner 문서 수 P50/P95/P99/max
- transaction latency P50/P95/P99/max
- index별 `docsExamined`, `keysExamined`, blocking sort
- transaction retry율과 lock/write-conflict율
- 동시 target write/Callback이 있을 때 latency
- HTTP 전체 latency와 publisher timeout/duplicate율

초기 제안 기준은 P99 2초 이하, 최대 HTTP latency 5초 미만이지만 최종 숫자는 Identity·DBA와 측정 결과로 승인한다. 기준 실패 시 다음을 금지한다.

- publisher timeout만 임의로 증가
- 일부 aggregate만 direct, 나머지는 무기록 background 처리
- `202`를 반환하면서 direct 완료로 간주

실패하면 production 활성화를 중단하고 C2-B durable inbox + worker의 ack 의미, source deny 시점, migration 중 target 가시성, SLA/DLQ를 새 계약으로 확정한다.

## 14. 배포와 rollback

### 14.1 배포 순서

1. Phase 0 외부 계약과 Mongo 지원 확인
2. transaction/guard-aware writer 배포, publisher/merge OFF
3. 모든 구버전 instance drain 확인
4. migration dry-run, backup, ACTIVE guard backfill/index apply
5. internal consumer 배포, ingress는 아직 제한
6. workload auth와 staging E2E
7. direct 성능 gate
8. dashboard/alert/runbook 확인
9. publisher 제한 활성화
10. canary 성공 후 merge feature 활성화

### 14.2 rollback 원칙

publisher 활성화 전에는 guard-aware 버전을 안전하게 재배포하거나 수정할 수 있다.

첫 merge가 `PROCESSED`된 뒤에는 다음 순서를 따른다.

1. Identity merge feature와 publisher를 먼저 OFF.
2. guard/inbox를 이해하고 source deny를 유지하는 Learning Core 버전은 계속 운영.
3. 처리된 ownership, MERGED marker와 inbox를 되돌리지 않음.
4. 구버전 guard-unaware Learning Core로 rollback 금지.
5. 장애 수정은 roll-forward하고 staging 재검증 후 publisher를 재개.

이미 처리된 merge를 단순 reverse update하면 그 사이 target이 만든 데이터와 source deny 의미를 분리할 수 없으므로 자동 rollback 대상이 아니다.

## 15. 완료 acceptance criteria

- [ ] C1 workload profile에서 TBD가 제거되고 실제 값은 Secret 없이 승인 문서에 기록됐다.
- [ ] C8 producer 불변식과 C9 status/retry가 Identity 인계서에 반영됐다.
- [ ] staging/prod Mongo transaction 지원과 retry가 검증됐다.
- [ ] 모든 user-owned writer와 네 Callback이 guard Transaction 경계를 사용한다.
- [ ] source/target guard, migration, source deny와 inbox가 같은 Transaction이다.
- [ ] 활성 시험 C4-A와 history C5-A가 테스트로 고정됐다.
- [ ] C6-A 5분 잔여 capability 문구와 E2E가 승인됐다.
- [ ] 동일 duplicate는 `204`, 상충 event는 `409`, 미확정 일시 장애는 `503`이다.
- [ ] 사용자 JWT가 internal endpoint에 접근할 수 없다.
- [ ] source JWT는 target alias가 되지 않고 사용자 API에서 안정적인 403 code를 받는다.
- [ ] 공개 API/DTO/`BaseResponse`, AI/Callback, Redis/S3와 retryCount 계약이 유지된다.
- [ ] migration dry-run/apply/final verification과 index explain이 성공했다.
- [ ] rollback injection과 모든 동시성 integration test가 성공했다.
- [ ] `./gradlew clean test`, Mongo integration task, Node migration test가 성공했다.
- [ ] staging workload auth, duplicate, response-loss, process-kill과 P99 gate가 성공했다.
- [ ] 구버전 writer drain, dashboard/alert와 no-rollback runbook이 확인됐다.
- [ ] 위 조건 전에는 production publisher와 merge feature가 OFF다.

## 16. 구현 착수 순서 요약

가장 먼저 endpoint/controller를 만들지 않는다.

```text
외부 credential·Mongo 지원 확인
→ Transaction/guard foundation
→ 기존 writer·Callback 전환
→ migration/backfill/index
→ workload security와 internal endpoint
→ inbox/ownership migration
→ replica-set 동시성 테스트
→ staging Identity E2E/P99
→ 제한 rollout
```

이 순서를 지켜야 consumer가 활성화되는 순간부터 source/target concurrent write와 늦은 Callback까지 같은 안전 경계에 들어간다.
