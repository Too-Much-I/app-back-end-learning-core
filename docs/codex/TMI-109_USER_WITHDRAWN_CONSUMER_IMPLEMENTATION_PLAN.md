# TMI-109 Learning Core `UserWithdrawn` consumer 구현 계획

## 1. 문서 목적

이 문서는 Jira `TMI-109`의 Learning Core 범위를 구현·검증·배포하기 위한 실행 계획이다.

기준 문서는 Identity 저장소의
`docs/contracts/user-withdrawn-downstream-deny-marker-stage-5-plan.md`이며, 그중 Learning Core가 소유하는 다음 책임만 구체화한다.

- Identity가 at-least-once로 전달하는 `UserWithdrawn` v1 event 소비
- eventId inbox와 userId deny marker의 원자적 저장
- 탈퇴 전에 발급된 사용자 Access Token의 잔여 유효기간 차단
- workload endpoint와 사용자 JWT endpoint의 인증 경계 분리
- 중복·충돌·Mongo 장애·TTL 지연에 대한 fail-closed 처리

현재 작업 트리에는 TMI-109 초안 구현과 단위·MVC 테스트가 이미 존재한다. 따라서 이 계획은 greenfield 설계가 아니라, 현재 구현을 계약에 대조하고 운영 가능한 완료 상태까지 남은 차이를 닫는 계획으로 사용한다.

이 문서는 Jira 구현 상태를 대신하지 않는다. Learning Core는 `TMI-109`, Identity producer/outbox/backfill은 후속 `TMI-111`에서 각각 추적하며 Git commit과 push는 사용자가 수행한다.

## 2. 선행 조건과 범위

### 2.1 선행 조건

- Jira 선행 작업: `TMI-108`
- Identity producer 후속 작업: `TMI-111` (`TMI-109 blocks TMI-111`)
- Identity withdrawal commit 이후 `UserWithdrawn` outbox가 생성되어야 한다.
- Identity publisher는 at-least-once 전달과 동일 eventId 재시도를 보장해야 한다.
- Learning Core consumer는 Identity publisher보다 먼저 배포되어야 한다.
- MongoDB는 multi-document Transaction을 지원하는 replica set 또는 동등한 구성이어야 한다.

### 2.2 TMI-109 포함 범위

- `POST /internal/v1/events/withdrawn`
- v1 request validation과 semantic digest
- `user_withdrawn_event_inbox`
- `withdrawn_user_access_denies`
- 두 collection을 묶는 Mongo Transaction
- duplicate 204와 payload conflict 격리
- 사용자 JWT 인증 뒤 deny marker gate
- `401 ACCOUNT_WITHDRAWN`
- marker store 장애의 `503 WITHDRAWAL_DENY_GATE_UNAVAILABLE`
- TTL index, startup validation, metrics와 안전한 로그
- contract, security, concurrency, Transaction, TTL, staging E2E 테스트

### 2.3 제외 범위

- Identity outbox, publisher, dead-letter, backfill 구현
- Billing consumer
- 사용자 데이터 삭제·익명화
- Access Token blacklist 또는 Token hash 저장
- request마다 Identity introspection 호출
- Refresh Token·Firebase·SNS 탈퇴 단계 변경
- 기존 공개 시험 API, DTO, `BaseResponse`, `retryCount`, S3, Redis, AI/Callback 계약 변경
- production workload credential 값을 임의로 결정하거나 저장소에 기록하는 작업

## 3. 확정 wire 계약

### 3.1 Endpoint

```text
POST /internal/v1/events/withdrawn
Authorization: <승인된 workload credential>
Content-Type: application/json
```

성공과 동일 payload 중복은 모두 body 없는 `204 No Content`다.

### 3.2 Request v1

```json
{
  "eventId": "9a88bc80-d73a-4a3d-8f68-492641d27208",
  "schemaVersion": 1,
  "userId": "73a18ed4-1d56-4c4f-afd6-b39175b82a86",
  "withdrawnAt": "2026-08-27T02:00:00Z"
}
```

| 필드 | 계약 |
| --- | --- |
| `eventId` | 필수 lowercase canonical UUID, 영구 event 멱등 key |
| `schemaVersion` | 필수 integer, v1은 정확히 `1` |
| `userId` | 필수 lowercase canonical UUID, 탈퇴한 Identity User 식별자 |
| `withdrawnAt` | 필수 UTC ISO-8601 instant, withdrawal Transaction 시각 |

v1에는 `eventType`, email, phone, Firebase UID, provider subject, Token, credential을 추가하지 않는다. JSON field 순서에는 의존하지 않고 알 수 없는 schema version을 추측 처리하지 않는다.

### 3.3 Semantic digest

raw JSON이 아니라 정규화된 의미 필드로 digest를 만든다.

```text
normalizedUserId      = canonical lowercase UUID
normalizedWithdrawnAt = Instant.parse 후 Instant.toString()

digestInput = UTF-8(
    "tosunsaeng:user-withdrawn"
    + NUL + "1"
    + NUL + normalizedUserId
    + NUL + normalizedWithdrawnAt
)

payloadDigest = lowercase hex(SHA-256(digestInput))
```

`eventId`는 unique lookup key이므로 digest 입력에서 제외한다. Identity와 Learning Core는 같은 golden vector를 사용해 digest 결과를 고정해야 한다.

### 3.4 HTTP 결과

| 조건 | HTTP | Body |
| --- | --- | --- |
| 신규 event commit | 204 | 없음 |
| 같은 eventId + 같은 digest | 204 | 없음 |
| 같은 eventId + 다른 digest | 409 | 없음 |
| 같은 userId + 다른 withdrawal 관계 충돌 | 409 | 없음 |
| malformed/invalid v1 payload | 400 | 없음 |
| winner 미확정 또는 처리 기반 장애 | 503 | 없음 |
| workload 인증 실패 | 기존 공통 401 | 내부 정보 비노출 |

오류 응답에는 document id, digest, DB 상세, stack trace를 포함하지 않는다.

## 4. 데이터 모델과 TTL

### 4.1 Inbox

Collection: `user_withdrawn_event_inbox`

```text
eventId          @Id, unique
schemaVersion
payloadDigest
userId
withdrawnAt
receivedAt
processedAt
status           PROCESSED
cleanupAt        TTL
```

`cleanupAt = receivedAt + inboxRetention`으로 계산한다. `inboxRetention`은 Identity outbox retention과 dead-letter 수동 replay 최장 기간보다 길어야 한다. Identity 계획의 후보는 120일이지만 운영 승인 전에는 production 값을 확정한 것으로 보지 않는다.

### 4.2 Deny marker

Collection: `withdrawn_user_access_denies`

```text
userId           @Id, unique
sourceEventId
withdrawnAt
blockedUntil
expireAt         TTL
createdAt
```

```text
blockedUntil = withdrawnAt
             + maxAcceptedAccessTokenLifetime
             + allowedVerifierClockSkew

expireAt = blockedUntil
```

한 userId에는 marker 하나만 존재한다. 재가입 계정은 새 UUID를 사용하므로 기존 marker를 재사용하거나 해제하지 않는다.

event 수신 시 이미 `receivedAt >= blockedUntil`이면 만료된 marker를 만들지 않고 inbox만 저장한다.

### 4.3 TTL 판정 원칙

Mongo TTL monitor는 즉시 삭제를 보장하지 않는다. authorization 결과는 document 존재 여부가 아니라 다음 직접 비교로 결정한다.

```text
now < blockedUntil   → 차단
now >= blockedUntil  → 허용
```

staging/prod에서는 이름, 단일 key, 방향, `expireAfter=0`이 정확한 두 TTL index가 없으면 활성화하지 않는다.

## 5. 처리 알고리즘과 Transaction

### 5.1 Request 전처리

Transaction 진입 전에 다음을 수행한다.

1. body와 필수 필드 확인
2. `schemaVersion == 1` 확인
3. eventId와 userId의 lowercase canonical UUID 확인
4. withdrawnAt parse와 정규화
5. `withdrawnAt <= now + maximumFutureEventSkew` 확인
6. semantic digest 계산
7. blockedUntil과 inbox cleanupAt 계산

### 5.2 Transaction body

하나의 명시적 `MongoTransactionManager`로 다음 순서를 원자적으로 실행한다.

1. eventId inbox 조회
2. inbox가 있고 digest가 같으면 mutation 없이 `DUPLICATE`
3. inbox가 있고 digest가 다르면 `PAYLOAD_CONFLICT`
4. 같은 userId marker가 이미 다른 event 관계로 존재하면 자동 병합하지 않고 conflict
5. marker 유효기간이 남았으면 userId marker insert
6. inbox insert
7. commit 뒤 `PROCESSED`

marker만 commit되거나 inbox만 commit되는 상태는 허용하지 않는다. marker save, inbox save, commit 단계 중 어느 하나가 실패하면 전체 Transaction이 rollback되어야 한다.

### 5.3 동시 unique 충돌 수렴

동시 요청의 `DuplicateKeyException`은 eventId inbox 충돌뿐 아니라 userId marker 충돌에서도 발생할 수 있다. 두 unique 경계를 구분하지 않고 자기 eventId inbox만 확인하면, 같은 userId·다른 eventId의 계약상 conflict가 503으로 잘못 분류된다.

Transaction loser는 rollback 뒤 다음 순서로 bounded 재조회한다.

1. 자기 eventId inbox를 조회한다.
2. inbox가 있고 digest가 같으면 duplicate 204를 반환한다.
3. inbox가 있고 digest가 다르면 payload conflict 409를 반환한다.
4. inbox가 없으면 자기 userId deny marker를 조회한다.
5. marker가 있고 `sourceEventId != eventId`이면 같은 userId·다른 event 관계가 확정된 것이므로 409를 반환한다.
6. marker가 있고 `sourceEventId == eventId`인데 inbox가 없으면 Transaction 불변식 위반 또는 winner commit 미가시성이므로 성공을 추정하지 않고 계속 bounded 재조회한다.
7. inbox와 marker가 모두 제한 시간 안에 보이지 않을 때만 503 `PROCESSING_UNAVAILABLE`을 반환한다.

bounded 재조회는 최대 횟수만 고정한 busy spin이 아니라 총 시간 한도와 짧은 backoff를 사용한다. 동일 요청 retry는 Identity publisher의 at-least-once 정책에 따라 다시 수렴할 수 있어야 한다.

실제 replica set race 테스트에서 다음 결과를 고정한다.

- 같은 eventId·같은 digest: winner 1건, 모든 loser 204
- 같은 eventId·다른 digest: winner 불변, loser 409
- 같은 userId·다른 eventId: marker winner 불변, loser 409
- winner가 아직 commit되지 않았거나 저장소가 불확정: 제한 시간 뒤에만 503

## 6. Security 설계

### 6.1 Workload chain

internal endpoint는 exact path와 POST method에만 매칭되는 우선순위 높은 별도 `SecurityFilterChain`을 사용한다.

사용자 Access JWT와 workload credential은 서로 대체할 수 없다.

```text
사용자 JWT:
  aud = Learning Core user API
  sub = userId

workload credential:
  principal = Identity service
  resource/audience = UserWithdrawn internal endpoint
```

현재 초안은 workload JWT의 issuer, JWKS, audience, principal claim/value, 최대 lifetime과 skew를 분리해 두었다. 하지만 Identity 기준 계획은 SigV4와 workload OIDC 중 production 방식을 아직 확정하지 않았다. 따라서 현재 JWT 구현을 production 계약으로 간주하지 않고, Phase 0에서 승인된 방식에 맞춰 유지 또는 교체한다.

### 6.2 User Access Token deny gate

gate는 `BearerTokenAuthenticationFilter` 뒤, authorization과 controller/application 진입 전에 실행한다.

| 상태 | 동작 |
| --- | --- |
| JWT 없음 또는 invalid | 기존 인증 결과 유지, marker 조회 0회 |
| valid user JWT + marker 없음 | 기존 요청 진행 |
| valid user JWT + active marker | SecurityContext clear, `401 ACCOUNT_WITHDRAWN` |
| valid user JWT + expired marker | 기존 요청 진행 |
| valid user JWT + marker store 장애 | application 진입 없이 `503 WITHDRAWAL_DENY_GATE_UNAVAILABLE` |

gate는 JWT `sub`만 userId로 사용한다. body, path, query에서 userId를 받지 않는다.

### 6.3 Gate 제외 경로

- public Python AI Callback
- `POST /internal/v1/events/withdrawn` workload chain
- JWT 인증이 적용되지 않는 local/test Legacy 요청

public callback을 인증 endpoint로 바꾸거나 AI Callback JSON을 변경하지 않는다.

### 6.4 Consumer와 deny gate 활성화 분리

현재 단일 `app.user-withdrawn.enabled`는 endpoint, workload chain, repository와 deny gate를 함께 제거하므로 안전한 endpoint-only rollback을 지원하지 못한다. 구현 시 단일 flag를 다음 두 flag로 교체한다.

```text
app.user-withdrawn.consumer-enabled
app.user-withdrawn.deny-gate-enabled
```

| consumer | deny gate | 허용 여부와 의미 |
| --- | --- | --- |
| false | false | local/test 또는 최초 production 활성화 전 상태만 허용; marker 생성 이후 rollback에는 금지 |
| false | true | publisher 중지 또는 consumer rollback 중 기존 marker 차단 유지 |
| true | true | 정상 소비와 사용자 Access Token 차단 |
| true | false | 금지, startup fail-fast |

구성 경계는 다음과 같이 나눈다.

- 두 flag 중 하나라도 true이면 marker repository, 공용 clock·metric과 deny marker TTL 검증을 등록한다.
- `consumer-enabled=true`일 때만 request/controller, workload chain·decoder, inbox repository, Transaction manager, consumer service, inbox TTL 검증과 Transaction capability probe를 등록한다.
- `deny-gate-enabled=true`일 때만 사용자 JWT chain에 deny gate를 추가한다.
- consumer가 event를 받을 수 있는데 gate가 꺼진 상태는 설정 검증에서 거절한다.
- active marker가 하나라도 남은 운영 환경에서는 publisher 중지 여부와 관계없이 deny gate를 끄지 않는다.

### 6.5 Mongo Transaction capability startup probe

`MongoTransactionManager` bean 생성만으로 standalone Mongo를 식별할 수 없으므로, staging/prod에서 `consumer-enabled=true`이면 readiness 전에 실제 Transaction capability를 검증한다.

1. 배포 DDL 단계에서 전용 `user_withdrawn_transaction_probe` collection과 최소 권한을 준비한다.
2. startup validator가 `userWithdrawnMongoTransactionManager`를 사용하는 `TransactionTemplate`로 Transaction을 시작한다.
3. instance마다 충돌하지 않는 임시 canary document를 insert한다.
4. Transaction을 명시적으로 abort/rollback한다.
5. Transaction 밖에서 canary document가 0건임을 확인한다.
6. 시작, write, abort 또는 잔존 확인 중 하나라도 실패하면 예외를 던져 pod를 Ready 상태로 만들지 않는다.

probe document에는 임시 probe 식별자와 생성 시각 외에 userId, eventId, Token, credential을 넣지 않는다. 식별자를 로그나 metric tag로 남기지 않는다.

성공 증거는 배포 버전과 함께 낮은 cardinality 구조화 event `transaction_capability outcome=verified`와 readiness 성공으로 남긴다. 실패 시 배포 파이프라인은 rollout을 중단하고 Identity publisher를 비활성 상태로 유지하며, 기존 `deny-gate-enabled=true` release를 보존한다. 실제 commit·rollback 정확성과 동시성은 별도 replica set integration test로 검증한다.

## 7. 현재 구현 inventory와 gap

### 7.1 이미 존재하는 초안 구현

- `domain/withdrawal/api`: request, controller, 전용 exception handler
- `domain/withdrawal/application`: normalizer, digest, consumer, Transaction service, metrics
- `domain/withdrawal/domain`: inbox, deny marker, status
- `domain/withdrawal/repository`: 두 Mongo repository
- `domain/withdrawal/security`: deny gate와 workload JWT validator
- `domain/withdrawal/config`: properties, decoder, Transaction manager, TTL index validator
- `global/config/SecurityConfig`: workload chain과 user JWT chain의 gate 순서
- `ErrorStatus`: `ACCOUNT_WITHDRAWN`, `WITHDRAWAL_DENY_GATE_UNAVAILABLE`
- consumer, Transaction, filter, MVC security, properties, index validator 단위 테스트

### 7.2 Jira 대비 gap matrix

| 항목 | 현재 상태 | 완료 전 필요한 작업 |
| --- | --- | --- |
| v1 endpoint와 body | 초안 구현 | Identity golden fixture로 양쪽 계약 고정 |
| semantic digest | 알고리즘 구현 | known digest golden vector 테스트 추가 |
| inbox + marker Transaction | annotation과 manager 존재 | 실제 replica set commit/rollback 검증 |
| duplicate 204/conflict 409 | 단위 테스트 존재 | 진짜 unique race와 winner visibility 검증 |
| late event inbox-only | 단위 테스트 존재 | replica set integration 확인 |
| user JWT deny gate | filter/MVC 테스트 존재 | 실제 filter chain 순서와 다중 instance staging 확인 |
| marker store fail-closed | 구현·단위 테스트 존재 | 실제 Mongo 장애 주입 E2E |
| TTL index | local ensure, staging/prod validate | 운영 DDL/runbook 및 실제 index 확인 |
| workload 인증 분리 | JWT 초안 존재 | SigV4/OIDC 최종 결정과 staging credential 검증 |
| 설정 fail-fast | 단일 flag와 값 검증 초안 | consumer/gate flag 분리와 금지 조합 테스트 완성 |
| Transaction capability | manager bean만 존재 | 실제 startup canary Transaction abort probe 추가 |
| 관측 | 기본 counter 존재 | delivery lag, alert, dashboard/runbook 결정 |
| privacy | 식별자 없는 로그 초안 | 로그·Sentry·오류 응답 자동 검사 |
| rollout | 코드 기본 비활성 | consumer 선배포 후 Identity publisher 활성화 |

현재 단위 테스트와 기존 전체 테스트 성공 기록은 유효한 회귀 근거지만, replica set·workload auth·다중 instance·publisher 연동 완료 조건을 대신하지 않는다.

## 8. 단계별 구현 계획

### Phase 0. 계약과 운영값 동결

다음을 Identity 담당자·인프라 담당자와 확정한다.

- workload 인증: SigV4 또는 workload OIDC
- exact ingress와 endpoint resource
- exact Identity service principal allowlist
- audience/resource와 algorithm
- credential/token 최대 lifetime, clock skew, rotation
- 사용자 Access Token의 실제 최대 lifetime
- user JWT verifier clock skew와 marker 계산값 일치
- inbox retention과 dead-letter/manual replay 최장 기간
- maximum future event skew
- staging/prod Mongo replica set과 Transaction 지원
- 두 TTL index의 DDL·소유 주체·배포 순서

미확정 값을 임의 기본값으로 넣지 않는다. consumer는 기본 비활성을 유지하고 필수 값이 없으면 활성 profile 기동을 실패시킨다.

### Phase 1. Wire 계약과 digest 고정

- request 필드와 validation을 Identity v1 문서에 맞춘다.
- Identity와 공유할 golden JSON fixture 또는 동일한 test vector를 만든다.
- canonical UUID, unknown schema, malformed instant, future skew를 검증한다.
- 의미가 같은 timestamp 표기가 같은 normalized digest를 만드는지 확인한다.
- digest 입력에 eventId와 raw JSON field order가 들어가지 않음을 테스트한다.
- duplicate 204와 conflict 409 body 없음 계약을 MVC 테스트로 고정한다.

### Phase 2. Mongo Transaction과 concurrency 완성

- 이름이 명확한 Mongo Transaction manager와 repository scan을 유지한다.
- 실제 replica set integration test 환경을 구성한다.
- marker save 뒤 inbox save 실패 시 두 document 모두 없는지 검증한다.
- commit error와 transient transaction error 정책을 검증한다.
- 같은 event 동시 요청 N개에서 inbox 1개, marker 1개, 모든 same-digest 요청 204를 검증한다.
- same eventId/different digest race에서 winner는 보존되고 loser가 conflict인지 확인한다.
- winner commit 가시성에 맞춰 bounded recheck/backoff를 조정한다.
- 같은 userId/different eventId loser가 inbox 이후 userId marker를 재조회해 409가 되는지 검증한다.
- 같은 `sourceEventId` marker만 보이고 inbox가 보이지 않는 불변식 위반은 성공으로 추정하지 않는지 검증한다.

### Phase 3. Deny gate와 security chain 완성

- workload chain을 exact path/method로 user chain보다 먼저 매칭한다.
- 승인된 workload credential만 internal endpoint를 호출하게 한다.
- user JWT를 workload endpoint에, workload credential을 user endpoint에 사용할 수 없음을 검증한다.
- invalid user JWT에서 marker 조회가 발생하지 않음을 검증한다.
- active, exact boundary, expired TTL 잔존 marker를 각각 검증한다.
- active marker에서 SecurityContext가 지워지고 application mutation이 0건인지 검증한다.
- Mongo timeout/exception에서 503 fail-closed와 application 진입 0건을 검증한다.
- public AI Callback과 workload endpoint에 user deny gate가 적용되지 않음을 고정한다.

### Phase 4. 설정·index·Transaction capability·관측 운영화

- 기존 단일 `enabled`를 `consumer-enabled`와 `deny-gate-enabled`로 분리하고 둘 다 false를 기본값으로 둔다.
- `consumer=true, deny-gate=false` 조합은 startup에서 거절한다.
- consumer 비활성·gate 활성 조합에서도 repository와 marker TTL 검증이 유지되는지 확인한다.
- consumer 활성 상태에서 필수 duration, verifier skew 일치, workload 설정을 fail-fast 검증한다.
- staging/prod에서는 local issuer/JWKS와 placeholder를 거부한다.
- TTL index migration/runbook을 먼저 적용하고 startup validator로 정확성을 확인한다.
- staging/prod consumer startup에서 전용 canary Transaction write·abort·잔존 0건 probe를 실행한다.
- capability probe 실패 시 readiness를 열지 않고 rollout을 중단하며 Identity publisher가 비활성인지 확인한다.
- consumer outcome과 deny gate outcome을 낮은 cardinality metric으로 기록한다.
- delivery lag를 측정하되 userId/eventId를 tag로 쓰지 않는다.
- conflict, consumer 5xx, deny store unavailable, delivery lag에 alert를 정의한다.
- Token, Authorization, Cookie, raw body, userId, eventId, digest가 로그·metric·Sentry에 노출되지 않음을 확인한다.

### Phase 5. 회귀와 staging E2E

- 집중 테스트 후 `./gradlew clean test`를 실행한다.
- 탈퇴 직전 발급한 Access Token이 event 전에는 성공하고 event commit 후에는 거절되는지 확인한다.
- 다른 기기의 기존 Access Token도 동일하게 거절되는지 확인한다.
- duplicate delivery와 publisher response loss가 204로 수렴하는지 확인한다.
- 실제 Mongo rollback, write conflict, 다중 Learning Core instance 가시성을 확인한다.
- 실제 workload credential 발급·검증·rotation overlap을 확인한다.
- marker/inbox TTL index 생성과 지연 삭제를 확인한다.
- delivery lag가 Access Token 차단 목표보다 짧은지 측정한다.

### Phase 6. 배포와 활성화

1. wire, 오류, TTL, workload auth 설정 승인
2. Transaction probe collection과 marker/inbox TTL index 선적용
3. staging에서 `consumer-enabled=true`, `deny-gate-enabled=true`로 기동해 capability probe와 workload 인증을 검증
4. production에서 같은 두 flag로 empty-store consumer와 gate를 선배포하고 readiness 증거 확인
5. 그 뒤 후속 Jira `TMI-111`의 Identity capture/outbox를 publisher 비활성 상태로 배포
6. Identity backfill dry-run과 staging replay
7. 마지막으로 Identity publisher production 활성화
8. backlog, delivery lag, conflict, dead-letter, gate 장애를 관찰

Identity publisher를 Learning Core consumer보다 먼저 활성화하지 않는다.

## 9. 예상 변경 파일

실제 package는 현재 `web.tosunsaeng.domain.withdrawal` 구조를 유지한다.

### Production

- `src/main/java/web/tosunsaeng/domain/withdrawal/api/**`
- `src/main/java/web/tosunsaeng/domain/withdrawal/application/**`
- `src/main/java/web/tosunsaeng/domain/withdrawal/domain/**`
- `src/main/java/web/tosunsaeng/domain/withdrawal/repository/**`
- `src/main/java/web/tosunsaeng/domain/withdrawal/security/**`
- `src/main/java/web/tosunsaeng/domain/withdrawal/config/**`
- `src/main/java/web/tosunsaeng/global/config/SecurityConfig.java`
- `src/main/java/web/tosunsaeng/global/config/security/SecurityErrorResponseHandler.java`
- `src/main/java/web/tosunsaeng/global/error/code/status/ErrorStatus.java`
- `src/main/resources/application.yml`
- profile별 설정과 `.env.example`

### Tests and runbook

- `src/test/java/web/tosunsaeng/domain/withdrawal/**`
- Identity/Learning Core 공유 contract fixture 또는 golden vector
- replica set Transaction/concurrency integration test
- workload auth와 filter-chain integration test
- staging/prod startup configuration test
- TTL index migration 또는 운영 runbook

관련 없는 시험 domain, 공개 DTO, AI, S3, Redis 코드는 수정하지 않는다.

## 10. 테스트 acceptance matrix

### 10.1 Contract

- 신규 event 204, body 없음
- 동일 eventId·동일 digest 204, mutation 없음
- 동일 eventId·다른 digest 409, 기존 marker/inbox 불변
- lowercase canonical UUID만 허용
- schemaVersion 1만 허용
- future skew 초과 거절
- Identity와 Learning Core digest golden vector 일치

### 10.2 Transaction과 concurrency

- marker와 inbox 단일 commit
- marker/inbox 각 save 실패 전체 rollback
- 실제 unique race winner 1개
- loser bounded recheck 수렴
- 같은 userId·다른 eventId marker race loser 409
- late event inbox-only
- startup canary Transaction이 abort되고 잔존 document 0건
- standalone, Transaction 권한 누락 또는 probe collection 누락 시 readiness 실패

### 10.3 Security

- workload credential 성공·실패
- user JWT의 workload endpoint 접근 거절
- workload credential의 user endpoint 접근 거절
- invalid JWT 기존 401, marker lookup 0
- active marker 401 `ACCOUNT_WITHDRAWN`
- exact blockedUntil과 expired marker 허용
- marker store 장애 503 `WITHDRAWAL_DENY_GATE_UNAVAILABLE`
- controller/service/repository mutation 진입 0
- public AI Callback과 workload endpoint deny gate 미적용
- consumer=false·gate=true에서 endpoint/workload chain은 비활성이고 기존 marker gate는 유지
- consumer=true·gate=false 설정은 startup 실패

### 10.4 TTL·privacy·regression

- TTL index 이름·key·expireAfter 검증
- TTL 지연 document가 authorization을 바꾸지 않음
- Token·credential·userId·eventId·digest 비노출
- 기존 공개 API URL·Method·parameter·DTO·`BaseResponse` 불변
- `user_id=examId`, Callback JSON, S3, Redis, Polling, retryCount 불변
- `./gradlew clean test` 성공

## 11. 관측과 운영 기준

권장 metric:

```text
learning_core.user_withdrawn.consumer{schemaVersion,outcome}
learning_core.user_withdrawn.deny_gate{outcome}
learning_core.user_withdrawn.delivery_lag
```

권장 consumer outcome:

- `PROCESSED`
- `DUPLICATE`
- `PAYLOAD_CONFLICT`
- `VALIDATION_REJECTED`
- `TRANSACTION_FAILED`

권장 gate outcome:

- `DENIED`
- `STORE_UNAVAILABLE`
- 필요한 경우 sampled/timer 기반 `ALLOWED_NO_MARKER`, `ALLOWED_EXPIRED`

다음 상황을 alert 대상으로 둔다.

- payload conflict 1건 이상
- consumer 5xx 또는 gate store unavailable 지속
- delivery lag가 승인된 SLO 초과
- Identity dead-letter 또는 pending oldest age 증가
- 필수 TTL index 누락·불일치

## 12. Rollback

1. Identity publisher를 먼저 비활성화한다.
2. workload ingress/IAM route를 차단하고 pending outbox와 dead-letter를 보존한다.
3. Learning Core가 정상이라면 두 flag를 모두 true로 유지해 endpoint 복구와 deny gate를 준비한다.
4. consumer endpoint 또는 workload 구성 자체가 장애 원인이면 `consumer-enabled=false`, `deny-gate-enabled=true`로 배포한다.
5. 이 상태에서 request/controller, workload chain, Transaction consumer는 내려가지만 marker repository와 사용자 JWT deny gate는 유지되는지 smoke test한다.
6. `app.user-withdrawn.enabled=false` 또는 두 flag 동시 false로 rollback하지 않는다.
7. 수정 배포 뒤 `consumer-enabled=true`, `deny-gate-enabled=true`로 복구하고 동일 eventId로 pending/dead-letter를 replay한다.

이미 처리된 marker를 삭제하거나 gate를 꺼 old userId를 다시 활성화하지 않는다. marker는 blockedUntil 이후 자연 만료시킨다. inbox를 삭제해 delivery 성공을 추정하지 않는다.

deny gate를 비활성화할 수 있는 가장 이른 시점은 Identity publisher·pending·dead-letter가 모두 0이고, 저장된 marker의 최대 blockedUntil이 현재 시각 이하이며, 보안·운영 승인을 받은 뒤다. 일반 장애 rollback에서는 이 조건을 충족한 것으로 추정하지 않는다.

## 13. Production 활성화 전 미확정 값

다음 값은 문서 작성 시점에 확정되지 않았다.

- workload 인증 방식과 ingress
- exact Identity workload principal
- audience/resource, algorithm, rotation
- workload credential 최대 lifetime과 clock skew
- 사용자 Access Token 최대 lifetime
- user JWT verifier와 공유할 allowed clock skew
- inbox retention과 manual replay 최장 기간
- maximum future event skew
- production TTL index 적용 주체와 시점
- delivery lag SLO와 alert threshold

이 값이 승인되고 staging 증거가 남기 전에는 `USER_WITHDRAWN_CONSUMER_ENABLED=true`와 `USER_WITHDRAWN_DENY_GATE_ENABLED=true`로 production 활성화하지 않는다.

## 14. 완료 정의

- Identity와 Learning Core의 v1 schema·digest·HTTP 계약이 동일하다.
- duplicate는 mutation 없이 204, conflict는 기존 상태를 바꾸지 않고 격리된다.
- 같은 userId·다른 eventId의 동시 marker 충돌이 409로 수렴한다.
- marker와 inbox가 실제 replica set Transaction으로 함께 commit 또는 rollback된다.
- consumer 활성 startup canary Transaction이 성공적으로 abort되고 잔존 document가 없으며, 미지원 환경은 readiness 전에 실패한다.
- 유효한 old Access Token이 event 처리 뒤 application 진입 전에 거절된다.
- invalid JWT로 탈퇴 여부를 탐색할 수 없다.
- marker store 장애가 fail-closed 503으로 처리된다.
- TTL 지연이 authorization 결과를 바꾸지 않는다.
- Token, credential, userId, eventId가 로그·metric·오류 응답에 노출되지 않는다.
- 전체 회귀, replica set concurrency, workload auth, multi-instance staging E2E가 통과한다.
- Learning Core consumer 선배포 뒤 Identity publisher가 활성화된다.
- `TMI-109 blocks TMI-111` Jira 관계가 유지되고 producer rollout은 `TMI-111`에서 추적된다.
- production 운영값과 rollback runbook이 승인된다.
