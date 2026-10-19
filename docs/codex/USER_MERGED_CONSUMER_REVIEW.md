# Learning Core `UserMerged` consumer 구현 사전 검토

- 검토일: 2026-08-20
- 상태 갱신: 2026-09-04
- 검토 대상: Identity Service 발행 `UserMerged` schema version 1 인계서
- 저장소 기준: `main` / `98730c9`
- 결론: 방향은 타당하지만, 현재 인계서만으로 endpoint 구현을 시작하면 안 된다. 아래 차단 사항과 Phase 0 정책을 먼저 확정해야 한다.

> 이 문서는 2026-08-20 당시의 사전 검토 근거를 보존하는 역사적 snapshot이다. 아래의 `TBD`, 5초 timeout, 당시 코드 inventory는 현재 구현 기준이 아니다. 2026-09-04 사용자 승인과 최신 Identity·Billing·Learning Core 코드를 반영한 실제 구현 기준은 `USER_MERGED_CONSUMER_IMPLEMENTATION_PLAN.md`와 `USER_MERGED_CONTRACT_DECISIONS.md`를 따른다. 현재 workload 계약은 RS256, `aud=learning-core-user-merged`, `sub=identity-service`, TTL `PT2M`이며 Identity read timeout은 `PT3S`다. 신규 Billing creation operation·AttemptGroup outbox·UserWithdrawn 경합 정책도 두 최신 문서의 C12~C18에 확정돼 있다.

## 1. 최우선 결론

`UserMerged`는 단순 webhook 추가가 아니다. 현재 Learning Core에는 다중 document Mongo Transaction 기반과 ownership guard가 없고, 사용자 소유 쓰기가 시험 생성·채점 Job·Callback에 분산돼 있다. 다음 항목이 해결되기 전에는 production-ready 구현으로 볼 수 없다.

1. workload credential 계약의 모든 `TBD` 확정
2. source와 target 양쪽 ownership guard를 획득하는 동시성 계약 확정
3. source와 target 모두 활성 시험이 있을 때의 충돌 정책 확정
4. Callback이 merge와 경쟁할 때 stale source `userId`를 저장하지 않는 Transaction 경계 확정
5. 기존 Presigned PUT URL의 merge 후 잔여 권한을 어떻게 정의할지 확정
6. MongoDB Transaction 지원 환경과 5초 timeout 안의 실제 P99 검증 계획 확정

## 2. 현재 ownership inventory

현재 저장소에는 시험 도메인만 있으며 스트릭, 10초 챌린지, 단어장 구현은 없다.

| 컬렉션/외부 상태 | ownership | migration 판단 | 충돌/주의점 |
| --- | --- | --- | --- |
| `exam_sessions` | 직접 `userId` | source → target 갱신 필요 | `active=true` 사용자별 unique index 때문에 양쪽에 활성 시험이 있으면 충돌 |
| `exam_results` | 직접 `userId`, `examId` | `userId` 갱신 필요 | Callback이 Session을 먼저 읽고 merge 후 insert하면 stale source 저장 가능 |
| `exam_summaries` | 직접 `userId`, `examId` | `userId` 갱신 필요 | Summary Callback도 같은 stale owner 경합 가능 |
| `question_grading_jobs` | `examId`로 간접 귀속 | rewrite 불필요 | Job은 merge 후에도 target 소유 시험의 처리로 계속 진행돼야 함 |
| `summary_grading_jobs` | `examId`로 간접 귀속 | rewrite 불필요 | 위와 동일 |
| `azure_results` | `examId`로 간접 귀속 | rewrite 불필요 | Callback Transaction이 현재 canonical Session 소유권과 일관돼야 함 |
| `speechace_results` | `examId`로 간접 귀속 | rewrite 불필요 | 위와 동일 |
| Redis `exam:status:{examId}` | `examId`로 간접 귀속 | key rewrite 불필요 | 기존 Redis Key/TTL 계약 유지 |
| S3 `temp/{examId}/...` | `examId`로 간접 귀속 | object rewrite 불필요 | 이미 발급된 PUT URL은 marker만으로 즉시 폐기 불가 |
| `mock_exams`, `questions` | 사용자 비소유 catalog | 대상 아님 | migration 금지 |

직접 `userId`를 가진 document는 현재 `ExamSession`, `ExamResult`, `ExamSummary` 세 종류다. 나머지 채점 데이터는 `examId`를 통해 Session에 귀속되므로 불필요한 rewrite를 하지 않는 것이 기존 AI·S3·Redis 계약에도 맞다.

## 3. 구현 전 반드시 수정하거나 합의할 사항

### 3.1 source guard만으로는 target 동시 쓰기를 직렬화할 수 없음

인계서는 source guard CAS를 강조하지만 동시에 “target의 정상 write와 migration이 경쟁해도 target 데이터를 overwrite하지 않는다”고 요구한다. migration이 target guard를 획득하지 않으면 이 보장은 일반적으로 성립하지 않는다.

권장 계약:

- merge Transaction은 source와 target guard를 모두 획득/touch한다.
- 두 guard는 UUID 문자열 정렬 등 결정적 순서로 획득해 교착 가능성을 줄인다.
- source는 `ACTIVE -> MERGED` CAS, target은 `ACTIVE` 확인과 revision CAS/touch를 수행한다.
- target이 이미 `MERGED`이면 처리하지 않는다.
- 모든 user-owned DB write는 현재 owner guard를 같은 Mongo Transaction에서 touch한다.

### 3.2 활성 시험 충돌 정책이 없음

`exam_sessions`에는 `{userId: 1}` + `{active: true}` partial unique index가 있다. source와 target 모두 진행 중 시험을 가지고 있으면 단순 ownership update는 실패한다.

제품 결정이 필요하다. 현재 구조에 가장 자연스러운 후보는 “canonical MEMBER인 target의 활성 시험을 유지하고 source의 활성 시험은 `ABANDONED`로 전환한 뒤 target 이력으로 이전”이지만, 이는 제품 정책이므로 구현자가 임의 결정하면 안 된다. 다음도 함께 정해야 한다.

- source만 활성 시험을 가졌으면 target의 활성 시험으로 계속 제공할지
- source 시험을 abandon할 때 진행 중 Job과 늦은 Callback을 기존 정책대로 무시할지
- 완료·폐기 이력은 모두 target history에 노출할지
- cycle/completion count가 합쳐진 뒤 다음 시험 배정 순서가 기대한 값인지

### 3.3 Callback과 merge의 stale owner 경합

현재 Feedback/Summary Callback은 `ExamSession`을 먼저 읽고 그 `userId`로 결과를 생성한다. 이 사이 merge가 commit하면 Callback이 source `userId`를 가진 새 결과를 뒤늦게 insert할 수 있다.

Callback은 사용자 JWT 요청이 아니므로 MERGED source를 단순 403 처리해서는 안 된다. 다음 경계가 필요하다.

- Session 조회, 현재 owner guard touch, 결과 insert/Job 전이를 같은 Transaction에 둔다.
- merge와 충돌해 Callback Transaction이 재시도되면 Session을 다시 읽는다.
- migration 후에는 동일 `examId`의 target owner로 결과를 저장한다.
- background grading 자체는 `examId` 기반으로 계속 처리하며 source authorization alias를 만들지 않는다.

### 3.4 이미 발급된 Presigned PUT URL은 즉시 차단할 수 없음

Learning Core가 merge 후 source JWT를 거절해도 merge 전에 발급된 S3 Presigned PUT URL은 만료 전까지 재사용할 수 있다. 현재 URL 유효시간은 5분이고 Object Key는 고정돼 있다.

따라서 인계서의 “migration commit 이후 source JWT write는 항상 거절”은 Learning Core HTTP 요청에는 적용할 수 있지만, 이미 위임된 S3 capability까지 즉시 회수한다는 의미로는 충족할 수 없다. 다음 중 하나를 합의해야 한다.

- 잔여 위험을 최대 5분의 bounded capability로 명시하고 submit/API 접근은 즉시 차단
- S3 측에서 회수 가능한 별도 설계를 도입
- Object Key/version 설계를 변경

뒤의 두 선택은 기존 S3 계약 변경 가능성이 있으므로 별도 명시적 승인 없이는 적용할 수 없다.

### 3.5 workload authentication은 현재 사용자 JWT 구성과 분리해야 함

현재 `SecurityConfig`는 JWT 모드에서 한 개의 사용자용 RS256 decoder를 사용하고, public endpoint 외 모든 요청을 사용자 JWT로 인증한다. 내부 endpoint를 그대로 추가하면 workload token을 검증하지 못하거나 사용자 Access JWT가 endpoint에 접근할 수 있다.

권장 구조:

- `/internal/v1/events/user-merged` 같은 전용 경로와 우선순위가 높은 별도 `SecurityFilterChain`
- 사용자 decoder와 별개의 workload decoder/validator
- issuer, audience, algorithm, principal allowlist, expiry/clock skew 고정
- local/test legacy 모드에서도 내부 endpoint는 permit-all 하지 않음
- 204 응답은 기존 공개 `BaseResponse`를 사용하지 않는 빈 body

실제 credential type, issuer, JWKS, algorithm, audience, principal claim/value, TTL, skew, rotation이 모두 `TBD`이므로 인증 구현의 완료 조건은 아직 충족되지 않았다.

### 3.6 HTTPS 및 network 제한은 애플리케이션 코드만으로 확정할 수 없음

TLS가 ALB/reverse proxy에서 종료되면 애플리케이션이 보는 scheme과 원본 HTTPS를 신뢰성 있게 연결할 proxy 설정이 필요하다. network policy도 이 저장소 밖의 배포 설정이다. 다음을 확정해야 한다.

- TLS 종료 위치와 trusted forwarded-header 정책
- Identity workload에서만 내부 endpoint로 접근 가능한 ingress/security group 정책
- 애플리케이션 자체 HTTPS 검사와 인프라 강제 중 책임 경계

### 3.7 다중 document Mongo Transaction 기반이 아직 없음

저장소에는 명시적인 `MongoTransactionManager` 또는 `TransactionTemplate` 구성이 없다. 일부 서비스에 `@Transactional`은 있으나, 이번 요구의 다중 collection 원자성과 transient transaction retry를 증명하는 기반은 구현돼 있지 않다.

구현 전에 다음을 확인해야 한다.

- staging/prod MongoDB가 transaction 가능한 replica set 또는 sharded cluster인지
- transaction manager와 transaction retry 정책
- `TransientTransactionError`, `UnknownTransactionCommitResult`, duplicate key 경합 처리
- direct transaction 내 document 수/크기 제한과 5초 publisher timeout 대비 P99
- 실제 Mongo integration test 환경

unbounded 사용자 이력을 한 transaction에서 rewrite하므로 mock 기반 단위 테스트만으로 완료 처리할 수 없다.

### 3.8 content type/size 오류 응답 계약 보완

인계서에는 content type과 4 KiB 제한 검사가 필수지만 응답 표에 status가 없다. 다음을 계약에 추가하는 편이 명확하다.

- unsupported `Content-Type`: `415`
- payload 초과: `413`
- malformed JSON/field validation: `400` 또는 `422` 중 프로젝트 정책으로 고정

Identity가 모든 해당 4xx를 영구 실패로 격리하는지도 함께 확인한다. `Content-Length`만 믿지 말고 chunked request도 실제 읽기 상한을 적용해야 한다.

### 3.9 guard 상태 전이의 추가 불변식 필요

아래 경우의 응답과 mutation 정책이 빠져 있다.

- source guard가 이미 같은 target으로 `MERGED`인데 다른 `eventId`가 도착한 경우
- source guard가 다른 target으로 이미 `MERGED`인 경우
- target guard가 `MERGED`인 경우
- 동일 source에 서로 다른 target event가 경쟁하는 경우

Identity가 “target은 항상 최종 ACTIVE MEMBER이고 다시 merge source가 되지 않는다”를 보장한다면 wire/producer 불변식에 명시해야 한다. 그렇지 않으면 consumer가 chain과 순서를 임의 추측하게 된다.

## 4. 권장 구현 순서

1. 계약 보완: workload profile, 오류 status, source/target guard 불변식, 활성 시험 충돌, Presigned URL 잔여 권한 확정
2. Phase 0 inventory 문서 확정 및 staging 문서 수/처리시간 측정
3. Mongo Transaction 기반과 source/target ownership guard entity/index/retry 구현
4. 모든 사용자 DB command와 Callback에 guard Transaction 경계 적용
5. 기존 사용자 guard backfill 및 rolling deployment/구버전 writer 차단 절차 마련
6. inbox/digest/내부 endpoint 구현
7. `exam_sessions`, `exam_results`, `exam_summaries` migration 구현
8. Redis/S3/AI 간접 ownership 회귀 테스트
9. staging workload auth, duplicate, response-loss, process-kill, 동시 write, timeout E2E

guard를 먼저 배포할 때 구버전 인스턴스가 guard를 touch하지 않은 채 계속 write하면 동시성 보장이 깨진다. 모든 writer 전환, backfill, 구버전 drain의 정확한 순서를 배포 계획에 포함해야 한다.

## 5. 외부 계약 보존 기준

이 작업은 additive internal endpoint로 구현할 수 있으며 다음 기존 계약은 변경할 이유가 없다.

- 기존 공개 API URL/Method/Parameter/Request/Response와 `BaseResponse`
- 클라이언트에 실제 `userId`를 노출하지 않는 규칙
- AI request/Callback의 `user_id = examId`
- 기존 Callback JSON
- `retryCount`
- Redis `exam:status:{examId}` Key/TTL
- S3 `temp/{examId}/q_{questionNumber}_r{retryCount}.wav` Object Key

특히 migration 뒤에도 `examId`는 유지해야 하며 Python AI에 target 실제 사용자 UUID를 보내면 안 된다.

## 6. 완료 판정

2026-08-20 사용자 승인으로 활성 시험은 target 우선·source-only 활성 이전, history 합집합 보존, 기존 Presigned PUT URL의 최대 5분 잔여 capability 수용, source/target 양쪽 guard, Callback 단일 Transaction과 상충 event fail-closed 방향이 확정됐다. 전체 선택과 승인 범위는 `docs/codex/USER_MERGED_CONTRACT_DECISIONS.md`를 따른다.

현재 상태는 “구현 방향 확정, 운영값·측정 gate 이행 전”이다. workload credential의 실제 issuer/JWKS/audience/principal/TTL/rotation, Identity producer 불변식과 status 표의 공동 반영, Mongo Transaction 지원, direct 처리 P99, TLS/network 책임과 staging E2E가 남아 있다. 이 값을 임의로 채우거나 검증 없이 production publisher를 활성화하면 안 된다.
