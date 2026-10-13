# Billing 인계서 — Lattice/SigV4에서 workload JWT로 전환

- 작성일: 2026-10-13
- 상태: **양측 검토용 제안서 — 인증 계약 확정·구현·배포 승인 아님**
- 관련 이력: TMI-116(Reservation saga), TMI-128(자동 복구), TMI-122(phone continuation), AttemptGroup publisher
- 수신: Billing 담당자 / 발신 관점: Learning Core(LC)

## 1. 5줄 결론

1. 사용자 제약으로 LC → Billing 통신 설계에서 VPC Lattice를 제외한다.
2. 대안은 HTTPS + 서비스 전용 workload JWT이며, LC 전용 RS256 키 직접 서명을 권장한다. 발급 방식은 양측 확정이 필요하다.
3. 실제 네트워크 구축 없이 인증 계약 합의와 양측 구현·격리 테스트를 먼저 진행할 수 있다.
4. 기존 Billing URL path·HTTP method·업무 JSON·멱등성·상태 머신은 유지하고 인증 계층을 교체한다.
5. 실제 주소·TLS·키 배포·접근 통제·E2E 검증을 마친 뒤 기능을 활성화하며, 이 문서로 AWS 설정이나 배포를 수행하지 않는다.

## 2. 반드시 읽어야 할 내용

### 요청하는 변경과 책임

| 담당 | 요청 사항 |
| --- | --- |
| Billing | 아래 6개 endpoint에 서비스 JWT 검증·작업별 인가 적용, 기존 업무 계약 보존, 인증 오류 계약 및 테스트 fixture 회신 |
| LC | Reservation/phone/status/reconciliation 및 AttemptGroup 발신의 SigV4를 JWT로 교체, 기존 복구·trace·멱등성 유지 |
| 양측 | issuer/audience/subject/TTL/키 신뢰·회전/오류 계약 합의, 교차 contract test 및 단계적 활성화 |
| 배포 담당 | 합의 이후 HTTPS 주소·인증서·네트워크·키 주입 준비. 내부 경로 권장이나 공개 HTTPS도 전용 인증·접근 통제 검토 후 가능 |

Billing 저장소는 이번 작성 과정에서 조회하거나 수정하지 않았다. 아래 현행 사실은 LC 코드 기준이다. Billing 담당자는 수신 구현과 최신 정본 계약의 일치 여부를 확인해야 한다.

### 유지할 통신 범위

모든 요청은 현재 `POST`, JSON body를 사용한다. 성공 응답 필드와 업무 오류는 최신 Billing 정본 계약을 그대로 유지한다.

| Path | 용도 / 유지할 요청 | 기존 Idempotency-Key |
| --- | --- | --- |
| `/internal/v1/reservations` | 일반: `userId, sessionId, mockExamId`. Phone 재가입만 `continuationReason, continuationId, expectedAttemptGroupId` 추가 | operationId |
| `/internal/v1/reservations/{reservationId}/confirm` | `userId, sessionId, sessionCommittedAt` | operationId |
| `/internal/v1/reservations/{reservationId}/cancel` | `userId, reason` — 현재 reason은 `SESSION_COMMIT_FAILED` | operationId |
| `/internal/v1/reservations/status` | `userId, operationId` | 없음: body로 기존 operation 조회 |
| `/internal/v1/reservations/continuations/phone` | `userId`. 성공은 strict 200 JSON 또는 body 없는 204 | 없음 |
| `/internal/v1/attempt-group-events` | 기존 eventId와 canonical event payload 그대로 | 헤더 추가 없음: 기존 eventId 멱등성 유지 |

실제 userId는 기존 **서버 간 업무 body**에만 유지한다. JWT `sub`는 사용자 UUID가 아닌 호출 서비스다. 앱 공개 API에 userId나 Billing 내부 식별자를 추가하지 않는다.

인증 전환으로 Reservation 상태, 무료 기회·이용권 정책, cancel/confirm 조건, owner guard, outbox payload/digest를 변경하지 않는다. 학습 기록 삭제 등 추가 Billing 연동은 별도 inventory/계약 확인 없이 임의로 endpoint나 권한을 추가하지 않는다.

## 3. 양측이 결정해야 할 사항 — 권장 초안

아래 값은 **제안이며 현행 Billing 계약으로 간주하면 안 된다.** Billing 회신 후 승인본으로 고정한다.

| 항목 | 권장안 | 확정 필요 내용 |
| --- | --- | --- |
| 발급 주체 | LC 전용 비대칭 키로 직접 서명 | 기존 중앙 M2M 발급 서비스가 있으면 대안 비교. Identity 자체 JWT 발급을 범용 발급 기능으로 가정하지 않음 |
| 전송 | `Authorization: Bearer <workload JWT>` | 기존 SigV4 경로 교체 일정. 인증 실패 시 SigV4/무인증 fallback 금지 |
| 알고리즘/header | RS256 고정, `typ=JWT`, `kid` 필수 | RSA 2048-bit 이상, 키 형식·등록 방법 |
| issuer | 환경별 LC workload issuer를 고정 allowlist로 검증 | 정확한 문자열; 아직 실제 URL 없음 |
| audience | `billing-internal` 제안 | 정확한 값과 단일 audience 정책 |
| subject | `learning-core-service` 제안 | Billing의 서비스 principal/권한 매핑 |
| 시간 | `iat=nbf`, `exp-iat` 최대 120초, clock skew 최대 30초 제안 | 시간 검증 경계와 NTP 운영. 미래 iat·nbf 및 만료·비정상 시간 순서 거절 |
| jti | 전송 시도마다 새 UUID | 토큰 식별자일 뿐 업무 operationId/eventId 대체 금지 |
| 권한 | 고정 issuer/sub에 대해 위 method/path만 서버 측 allowlist | scope claim 필요 여부·이름. 임의 scope 문자열을 신뢰하지 않음 |
| 공개키 신뢰 | 환경별 고정 JWKS 위치, 제한된 cache/refresh | 호스팅 담당, 주소, cache TTL/회전 overlap/긴급 회수 SLA. bootstrap은 명시적 공개키 배포도 합의 가능 |
| 오류 | 인증 실패 401, 유효 서비스의 권한 부족 403 | 아래 제안 code와 기존 오류 envelope의 호환성 |

JWT header의 임의 `jku`/`x5u`나 미검증 issuer에서 키 주소를 동적으로 가져오지 않는다. 허용 algorithm/issuer/audience/subject/kid를 검증한다. 사용자 로그인 JWT와 Identity의 서명 private key는 재사용하지 않는다.

정확한 도메인이 없어도 위 계약과 설정 인터페이스는 합의 가능하다. Billing 주소는 기존 `BILLING_BASE_URL`로 분리한다. issuer/JWKS 관련 새 설정 이름은 구현 시 양측 명세에 별도로 추가한다. 예시 주소를 실제 인프라가 존재하는 주소처럼 등록하지 않는다.

### Billing 회신 요청

- 직접 서명 방식 및 위 claim/TTL/인가 제안의 수용 여부와 수정안.
- JWT 검증을 적용할 endpoint 목록 및 현재 SigV4/Lattice 의존 제거 범위.
- 최신 `BILLING_SERVICE_INTEGRATION_CONTRACT.md` 기준 성공·오류 fixture와 인증 오류 envelope.
- 공개키 등록/JWKS 담당, 키 교체·회수·장애 정책.
- 코드 계약 합의 시점과 테스트 배포 가능 시점. 실제 도메인·ALB 선택은 별도 배포 단계에서 결정 가능.

## 4. 주요 위험과 미확인 사항

- **네트워크와 인증은 별개:** JWT가 DNS/TLS/라우팅을 제공하지 않는다. 내부 ALB가 필수는 아니지만 실제 활성화 전 도달 가능한 안전한 HTTPS 경로는 필요하다.
- **Bearer 재사용 위험:** JWT 자체는 body·method에 대한 SigV4 요청 서명을 대체하지 않는다. 탈취 시 만료 전 재사용 가능하므로 TLS·단기 TTL·최소 권한·로그 비노출이 필수다. 엄격한 replay 차단이나 body binding은 별도 계약 없이 추가하지 않는다.
- **인증과 멱등성 구분:** 재시도 JWT/jti는 달라져도 operationId/eventId와 body는 유지한다. token replay 차단 정책이 업무 재시도를 잘못 차단하지 않아야 한다.
- **키 장애:** unknown kid는 제한된 refresh 후 fail-closed. JWKS 장애 때 만료된 cache를 무기한 신뢰하거나 인증을 끄지 않는다. 정상 cache 유효기간 내 기존 키 검증 정책과 키 회수 SLA를 합의해야 한다.
- **분산 변경:** LC만 JWT로 바꾸면 Billing이 거절한다. Billing verifier와 LC sender를 양측 준비 후 활성화한다. 자동 무인증 fallback 금지.
- **AWS 확인 범위:** 2026-10-13 현재 계정 서울 리전 ECS/ELB 조회에서 Billing 명명 서비스·활성 task family·target group·ALB routing이 없었다. 다른 계정/리전/비ECS 존재 여부와 Billing 실제 수신 코드는 미확인이다.

## 5. 구현·오류·검증 인계

### Billing 구현 요구

1. 서비스 전용 security 경계를 위 exact method/path에 적용하고 사용자 JWT와 구분한다. 다른 Billing 공개 API나 Identity 연동 인증은 임의로 바꾸지 않는다.
2. 서명·claim·시간 검증 및 endpoint 인가를 **업무 처리/DB mutation 전에** 완료한다. 기능 비활성 또는 필수 설정 누락 시 fail-closed한다.
3. private key는 LC만 보유하고 Billing에는 공개키만 제공한다. test/prod 키·issuer 신뢰를 분리한다. token/private key/Authorization/body 원문을 로그·trace에 남기지 않는다.
4. 요청 body·응답·상태 코드·멱등성 저장·ledger 상태 머신을 기존 계약대로 유지한다. `traceparent`는 인증 주체 판정에 사용하지 않는다.

### 인증 오류 제안과 기존 LC 해석

| 상황 | 제안 HTTP / code | LC 관점 |
| --- | --- | --- |
| 누락·만료·잘못된 서명/issuer/aud/sub/algorithm 등 신뢰 불가 token | 401 / `WORKLOAD_UNAUTHENTICATED` | 기존 401·403은 body code와 관계없이 AUTH_FAILURE |
| 신뢰된 서비스지만 해당 작업 권한 없음 | 403 / `WORKLOAD_FORBIDDEN` | 동일 AUTH_FAILURE; 업무 처리 금지 |
| 키 검증 인프라 장애로 판정 불가, 유효한 cache도 없음 | 503 / 기존 `BILLING_TEMPORARILY_UNAVAILABLE` 사용 제안 | fail-closed 임시 실패; 인증 허용으로 우회하지 않음 |
| rate limit/일시 장애 | 기존 429/5xx 계약 보존 | 기존 retry/backoff 유지, `Retry-After`는 정수 초 호환 |

새 인증 code는 제안이며 최종 오류 envelope는 Billing 정본과 합의한다. 검증 실패 세부 사유/키 정보/token을 응답에 노출하지 않는다. 기존 LC 업무 code `INVALID_REQUEST`, `INVALID_IDEMPOTENCY_KEY`, `ENTITLEMENT_INSUFFICIENT`, `COMMAND_PROCESSING`, `IDEMPOTENCY_KEY_CONFLICT`, `RESERVATION_STATE_CONFLICT`, `OPERATION_NOT_FOUND`, `BILLING_TEMPORARILY_UNAVAILABLE`는 재정의하지 않는다.

Reservation worker와 AttemptGroup publisher는 기존 인증 실패 circuit·15분 probe 정책을 유지한다. 인증 방식 변경을 이유로 무한 즉시 재시도하거나 operation/event 증거를 삭제하지 않는다.

### LC 변경 시 보존할 것

- 두 SigV4 client의 인증 부분을 교체하되 공용 AWS credential provider는 제거하지 않는다(S3가 사용).
- W3C CLIENT span/traceparent 및 AttemptGroup attempt별 새 span을 유지하고 baggage를 전파하지 않는다.
- reserve 응답 유실/status-first 복구, 동일 key confirm/cancel, durable lease/CAS/owner guard, eventId/canonical payload를 유지한다.
- HTTP redirect 미추종, 제한된 timeout/응답 크기, 비밀 비노출 유지. 새 대상에 token을 전달하는 redirect 허용 금지.
- 기존 앱 공개 API·BaseResponse·Idempotency-Key 의미·retryCount·AI `user_id=examId`·S3/Redis 계약 유지.

### 테스트·활성화 순서

1. **계약 합의:** 이 문서의 제안을 승인된 양측 계약에 반영. Lattice 전제 runbook/startup validator/테스트도 변경 대상으로 명시한다.
2. **격리 테스트:** 실제 AWS·DB·실키 없이 테스트 키/fixture와 mock HTTP로 sender/verifier 교차 검증. 정상, 만료, 미래 시간, 잘못된 alg/kid/iss/aud/sub, 사용자 token, 환경 간 token, 권한 부족을 검증한다.
3. **업무 회귀:** 같은 operation/event의 새 JWT 재시도, 성공 응답 유실 후 status 복구, duplicate event, 인증 실패 무변경, 키 회전/cache 장애, 로그 비노출을 검증한다. LC `./gradlew clean test` 및 관련 Mongo 격리 통합 테스트를 수행한다.
4. **배포 준비:** 실제 HTTPS 주소·DNS·인증서·접근 통제·키 배포 및 Billing verifier 준비. 기존 flag는 기본 OFF 유지.
5. **테스트 환경 E2E:** LC 발신→Billing 검증→업무 처리/중복 처리→응답 유실 복구와 인증 circuit 동작 확인.
6. **승인 후 활성화:** saga/phone/reconciliation/AttemptGroup flag는 각 기존 gate 충족 후 단계적으로 켠다. AttemptGroup은 publisher idle 검증 후 writer 활성화한다.

실패 시 신규 활성화를 중단하고 기존 복구 증거를 보존한다. 진행 중 Reservation/미전달 outbox가 있으면 worker까지 일괄 OFF하는 식의 rollback을 하지 말고 처리 중 상태와 양측 호환 버전을 확인한다. 폐기한 Lattice나 무인증 경로로 자동 복귀하지 않는다.

## 6. 부록 — 근거와 정본

### LC에서 이번에 확인한 코드

- [Reservation client](../../src/main/java/web/tosunsaeng/domain/exams/billing/SigV4BillingReservationClient.java): 5개 endpoint, 일반/phone reserve body, Idempotency-Key, SigV4 `vpc-lattice-svcs`, 인증 오류 분류.
- [AttemptGroup client](../../src/main/java/web/tosunsaeng/domain/exams/attemptgroup/infrastructure/SigV4AttemptGroupEventClient.java): event POST, canonical payload, traceparent, SigV4와 redirect 금지.
- [환경 설정](../../src/main/resources/application.yml): `BILLING_BASE_URL`, saga/phone/reconciliation/AttemptGroup default-off, connect 2초/read 5초 기본값.
- [Reservation 복구 계획](BILLING_RESERVATION_RECONCILIATION_IMPLEMENTATION_PLAN.md), [복구 runbook](BILLING_RESERVATION_RECONCILIATION_RUNBOOK.md).
- [Phone continuation 계획](PHONE_REJOIN_CONTINUATION_IMPLEMENTATION_PLAN.md), [AttemptGroup 계획](ATTEMPT_GROUP_OUTBOX_PUBLISHER_IMPLEMENTATION_PLAN.md).

Billing 정본은 Billing 저장소 `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`이며 trace는 `docs/contracts/LEARNING_CORE_ATTEMPT_GROUP_TRACE_HANDOFF.md`를 대조한다. 기존 phone 구현 기준은 Billing PR #8 merge `7138810`이지만 최신 정본은 Billing 담당자가 확인해야 한다. 이 인계서는 업무 정본을 대체하지 않으며, 이번 작업은 LC 문서 작성뿐으로 Billing/Identity 코드나 AWS 설정을 변경하지 않았다.
