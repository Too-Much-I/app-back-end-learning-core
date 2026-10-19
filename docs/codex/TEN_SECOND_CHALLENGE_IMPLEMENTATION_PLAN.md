# 10초 챌린지 Learning Core 구현 계획

- 작성일: 2026-09-07
- 보완: TMI-126 등록 후 실행 가능한 저장 구조·Transaction·상태 전이·검증 계획으로 상세화
- 기준: 로컬 `develop@88b46c6`, 승인된 Challenge v1 및 2026-09-07 보완 결정
- Jira: [TMI-126](https://to-teacher.atlassian.net/browse/TMI-126) — [Learning Core] 10초 챌린지 API 및 비동기 AI 채점 구현. 관련 이력은 TMI-102·TMI-105·TMI-106이다.
- 상태: 2026-09-07 TMI-126 Learning Core 로컬 구현 및 테스트 완료, 미배포·기능 기본 OFF. 일반 Java 529개(Challenge 33개), replica-set Mongo 40개(Challenge 29개), Node migration 90개(Challenge 7개)가 통과했다. Identity 운영 배포·프론트/AI 적용·운영 E2E 확인은 남아 있다. Jira 상태 전환은 이번 구현에서 수행하지 않았다.

구현 파일·설정·migration·활성화/중단 절차는 [rollout 안내](TEN_SECOND_CHALLENGE_ROLLOUT.md)를 따른다. 아래 계획의 제품·계약 결정은 유지하며, 구현 예정 이름은 실제 코드 책임으로 대응한다. worker는 기존 시험 scheduler를 대체하지 않는 전용 lifecycle executor를 사용한다. Mongo 테스트는 Testcontainers 격리 DB에서 실제 mongosh dry-run/apply까지 실행했다.

## 1. 5줄 결론

1. 기존 시험과 격리된 Challenge domain을 만들고 [프론트 v1](../contracts/ten-second-challenge-frontend-api.md)의 일곱 서버 API를 구현한다.
2. MEMBER 판정은 Identity의 새 `account_type` claim에 의존하며 기존 JWT의 공통 scope로 판정하지 않는다.
3. attempt를 먼저 만들고 1시간 deadline·콘텐츠 snapshot·고정 upload key를 저장한다. 요청 시 만료 CAS와 bounded scheduler를 병행한다.
4. 제출·Job을 내구성 있게 저장한 뒤 [AI v1](../contracts/ten-second-challenge-ai-api.md) multipart 요청과 전용 인증 Callback으로 비동기 채점한다.
5. feature OFF로 개발하고 Identity·catalog/index·격리된 Mongo 통합 검증·모바일 audio·AI E2E를 마친 뒤 활성화한다.

## 2. 반드시 읽을 내용

### 서비스별 영향

| 담당 | 필요한 작업 | 유지되는 계약 |
| --- | --- | --- |
| Identity | claim 구현·로컬 병합/테스트 확인 완료. 실제 선배포·구버전 issuer drain 확인 필요 | 기존 로그인 Request/Response, sub·issuer·audience·scope |
| Learning Core | MEMBER 인가, catalog/date/attempt, 만료, S3 검증, Job/AI/Callback, 결과/history 구현 | 기존 시험 API·AI user_id=examId·S3/Redis key |
| 프론트 | history 누락 날짜 비활성 표시, DATE_CLOSED 분기 제거, 415와 grading failed UX 대조 | 모든 Challenge URL·Method·Request/Response field, token 전달·녹음·upload 순서 |
| AI | binary profile 검사 및 영구 실패 처리 대조, metric 식별자 tag 제거 여부 확인 | endpoint·multipart·Callback field·방향별 Bearer·120초/3 generation |

프론트·AI 실제 코드는 이번 작업에서 검토하지 않았다. 이미 이 동작을 구현했다면 추가 수정이 없을 수 있지만, 수정이 전혀 없다고 보장할 수 없다. 사용자 승인은 보완 문서 적용의 근거이며 상대 팀의 구현·배포 완료를 의미하지 않는다.

### 승인한 선택

- JWT `account_type` claim. MEMBER만 허용하며 missing/GUEST/unknown은 인증된 사용자라도 `403 COMMON403`이다. JWT 자체의 서명·만료 실패는 기존 401이다.
- lazy expiration을 사용자 관측 결과의 기준으로 두고 bounded scheduler는 방치 문서 보정에 사용한다.
- Callback opaque Bearer exact chain을 항상 등록한다. 제안 순위는 `@Order(-1)`이며 기존 0/1/2를 유지한다. exact 경로의 허용 method는 POST이고 그 외 method가 다른 인증 경로로 우회하지 않게 막는다. flag OFF는 deny-all이다.
- Learning Core submit은 S3 객체 존재·metadata MIME·2 MiB, AI는 실제 M4A/AAC-LC·sample rate·channel·decode를 검사한다.
- history는 요청 월과 `[contentBaseDate, KST today]`의 교집합이다. 출시 이전 월은 빈 dates다. 콘텐츠 소진은 과거 attempt 결과를 숨기지 않는다.
- `CHALLENGE_DATE_CLOSED` 제거, metric tag에 개별 식별자 금지, 스트릭 제외.
- 2026-09-07 후속 사용자 결정: history/results의 `solvedQuestionCount`와 `participated`는 실제 audio 제출 접수만 기준으로 한다. 미제출 만료는 제외한다. 진행 종료·다음 문제·참고 답안 접근은 그대로 유지한다.

## 3. 사용자 결정 사항

위 선택은 2026-09-07 승인됐다. 같은 사항을 다시 선택할 필요는 없다. 프론트·AI 적용 확인과 Identity claim 운영 배포를 의존 작업으로 추적한다. TMI-126은 등록됐으며 runtime 구현·production 활성화는 Jira 등록과 구분한다.

클래스·collection·index는 Learning Core 내부 구현안이다. 아래 제품/예외 정책은 사용자 승인으로 확정됐으며 남은 것은 구현·상대 팀 적용 및 운영 검증이다.

1. 음성 저장·재녹음 경계 확정: 프론트 재녹음은 answer 제출 전에만 허용한다고 사용자가 확인했다. 같은 attempt/key에 최종 업로드 후 한 번 제출한다. version pin·보관본 없이 마지막 PUT을 유지하며 제출 후 바뀐 audio는 같은 Job으로 재전송하지 않고 복구 불가 시 채점 실패로 종료한다(부록 B).
2. AI 경계 확정: 각 Job의 최초 dispatch부터 202 접수 전 재전송 총 예산은 5분이다. 예산 소진 또는 마지막 generation timeout으로 최종 실패가 확정되면 늦은 유효 Callback은 결과를 변경하지 않고 204로 종료한다(부록 C).
3. 만료 결과 timestamp는 사용자 승인으로 확정: EXPIRED의 `submittedAt=null`이다. 프론트 적용·fixture 검증은 남아 있으나 정책 재선택은 필요 없다.

제품·재녹음·AI 종료 경계의 구현 기준은 확정됐다. 프론트·AI 계약 반영, 회귀/통합 테스트와 운영 gate는 별도로 검증하며 이 승인을 배포·production 활성화 승인으로 취급하지 않는다.

## 4. 주요 위험과 미확인 사항

- Identity 발급기는 필수 UserAccountType을 받아 account_type=MEMBER|GUEST를 넣도록 구현됐다. 로컬 develop 및 origin/develop 참조의 PR #39 merge fe9c7f6과 구현 6b34f44, 7개 발급 경로 및 전체 640개 테스트 통과를 확인했다. 원격 최신 상태·운영 배포는 미확인이다. 신뢰 가능한 claim 없이 MEMBER gate를 열지 않으며, 발급 서버 전체 전환 뒤 마지막 구형 Token의 최대 TTL+검증 skew를 기다린다. 기본 TTL은 30분이나 rollout은 실제 운영 설정을 기준으로 한다.
- 사용자가 가진 PUT URL은 submit 뒤에도 만료 전까지 재사용할 수 있다. 사용자 요청으로 이를 허용하며 version pin·보관본을 제외한다. AI가 이미 읽은 음성과 마지막 S3 객체는 달라질 수 있다. 덮어쓰기만으로 자동 재채점하지 않으며, 같은 job/key에 다른 audio를 다시 보내면 기존 AI 계약상 409다.
- 접수 전 총 5분과 접수 후 Callback 120초는 별도다. Job 최초 dispatch 시각/접수 시각을 내구성 있게 저장하고 재시작·lease 회수·중복 202가 deadline을 초기화하지 않도록 검증한다. 최종 실패 뒤 유효 Callback은 204 no-op이며 완료 결과를 되살리지 않는다.
- submit·expire·duplicate 생성·Callback·timeout 경쟁은 mock만으로 충분히 증명되지 않는다. 실제 운영 데이터 대신 격리된 replica-set을 사용한다. 현재 로컬 Docker 가용성은 이 문서 작업에서 재검증하지 않았다.
- 기준일 초기화 전에 설정·index·Day 1 catalog 검증을 완료하고 readiness를 연다. 실패 기동·자정 scale-out에서도 이미 저장된 기준일을 자동으로 되돌리지 않는다.
- AI 인증 값·TLS·mobile 녹음 profile·catalog 전체 품질은 배포 전 실제 환경 검증 사항이다.

## 5. 구현 순서와 완료 기준

### 단계 1 — 계약 fixture와 Identity 의존성

- 프론트 성공·오류·processing·no-speech·failed·history 경계 JSON fixture를 고정한다.
- AI completed/no_speech/failed, duplicate·stale·conflict·payload 상한 fixture를 고정한다.
- Identity 선행 증거: 필수 enum·신뢰된 User 유형·기존 claim 유지·upgrade/refresh/merge는 PR #39 로컬 병합 참조와 640개 테스트로 확인했다. 이 이슈에서 Identity를 다시 구현하지 않고 MEMBER/GUEST/claim 누락 fixture와 운영 선배포 체크리스트를 만든다.
- Learning Core의 Challenge 전용 member provider/authorization에서 claim을 읽고 사용자 입력으로 권한을 보충하지 않는다. Legacy에서도 Challenge를 무인증 MEMBER로 취급하지 않는다. 기존 withdrawal/merged deny gate 적용을 유지한다.

### 단계 2 — catalog·날짜·attempt

- 기존 Mongo 연결의 `challenge_10s_questions`를 사용한다. 양의 dayNumber, 정확한 1/2/3, 전체 및 문서 내부 questionId 중복, non-blank 문장/답안, integer difficulty를 검사한다.
- `challenge_10s_catalog_state` singleton의 atomic setOnInsert로 KST Day 1을 고정한다. feature OFF는 초기화하지 않으며 비순환 resolver와 콘텐츠 없음 404를 구현한다.
- ChallengeAttempt에 userId·challengeDate·deadline·콘텐츠 snapshot·upload key를 저장한다. `(userId, challengeDate, questionNumber)` unique로 한 번만 생성하며 중복 키 실패는 실패한 Transaction 밖에서 재조회한다.
- 사용자 승인 S3 object key: `temp/challenges/{attemptId}/q_{questionNumber}.m4a`. 생성 시 서버가 고정하고 URL 재발급·제출 전 재녹음은 같은 key를 사용한다. 시험 retryCount·실제 userId·날짜를 key에 추가하지 않는다. 기존 시험 `temp/{examId}/q_{questionNumber}_r{retryCount}.wav`는 변경하지 않는다.
- 순서 검사·날짜 header 비교·기존 attempt replay·소유권을 구현한다. referenceAnswer는 terminal 전 응답에 포함하지 않는다.

### 단계 3 — 만료·S3·submit

- `now >= deadline`인 미제출 상태만 EXPIRED로 CAS 전환한다. 순수 존재하지 않는 attempt를 만료로 생성하지 않는다.
- today/question/attempt/upload/answer/results/history에서 관련 만료 상태를 반영하고 앞 문제 만료가 다음 문제를 막지 않게 한다. GET은 새 attempt를 만들지 않지만 기존 만료 상태 보정은 수행할 수 있다.
- bounded scheduler는 deadline index를 이용해 일정 수량만 보정한다. 같은 문서를 여러 instance가 조회해도 per-document CAS로 안전하게 수렴시킨다. 이 CAS-only 작업에 전역 lease가 반드시 필요한 것은 아니다.
- submit과 expire가 경쟁할 때 submit은 미제출·deadline 전 조건을 최종 저장 시 검사한다. 이미 SUBMITTED인 동일 요청의 성공 replay는 만료 검사보다 먼저 복구한다. EXPIRED에는 AI Job을 만들지 않는다.
- Presigned PUT은 고정 .m4a key·audio/mp4를 사용하며 만료 시각은 attempt deadline을 넘지 않는다. S3 검증·다운로드·AI HTTP는 Mongo Transaction 밖에서 수행한다.
- submit 성공 전에 attempt SUBMITTED와 결정적 Job을 원자적으로 저장한다. 동일 key/동일 요청 replay 및 다른 Body conflict, unknown commit 재조회 수렴을 구현한다.

### 단계 4 — Job dispatch·Callback

- 기존 Exam Job 엔티티를 재사용하지 않고 별도 durable Job과 batch scheduler를 만든다. claim/lease token·stale update fencing으로 다중 instance 전송을 제어한다.
- 전송은 [AI 계약의 모든 field](../contracts/ten-second-challenge-ai-api.md)를 사용한다. contract_version/Header 일치와 202 응답의 job/generation/version을 검증한다. 실제 userId·difficulty·S3 위치를 보내지 않는다.
- transport retry는 같은 job/generation/idempotency key와 bytes, generation retry는 새 결정적 job을 사용한다. 3초 connect·15초 accept·120초 Callback deadline·최대 3 generation을 유지한다.
- Callback exact chain에서 credential 검증 후 16 KiB body 상한과 text/enum 조합을 검사한다. 신뢰하지 않는 raw header/body/credential을 로그에 넣지 않는다.
- Callback ID·semantic payload digest·Job generation을 검증한다. 결과와 Job terminal 변경을 원자적으로 저장하고 duplicate/stale은 204, conflict는 계약의 409다. 202 응답보다 빠른 Callback이 결과를 저장해도 dispatch 응답이 이를 덮어쓰지 못하게 한다.
- 정상 완료와 no-speech의 referenceAnswer는 같은 attempt snapshot에서 조립한다. AI 실패는 제출과 참고 답안을 보존한다.

### 단계 5 — 조회·운영 검증

- 결과는 catalog 재조회 없이 attempt snapshot으로 조립한다. query가 없으면 question 필드를 생략하고, 지정 문제의 attempt가 없거나 미제출·미만료이면 question=null이다. EXPIRED는 풀이 수에서 제외하되 참고 답안 상세를 반환한다.
- history/results는 내부 SUBMITTED만 풀이 수에 포함하고 EXPIRED는 제외한다. AI 처리 중·무음·실패는 제출 접수 사실을 바꾸지 않는다. participated는 실제 제출 수>0이며 공개 attemptStatus로 집계하지 않는다. calendar fixture에서 empty month·부분 월·자정 넘어 제출을 검증한다.
- ID는 구조화 로그에만 넣고 metrics에는 fixed stage/outcome/error 분류와 timer 값을 기록한다.
- migration dry-run/apply, index 정의 확인, feature startup validator, rollout/rollback runbook을 추가한다. feature 비활성화 시 진행 중 Job/Callback 처리 방침을 runbook에 포함하고 데이터를 삭제하지 않는다.

## 6. 테스트·배포와 부록

필수 검증은 unit/HTTP contract fixture, Node migration 검사, 격리 Mongo replica-set의 commit/rollback·duplicate·submit/expiry·Callback/timeout·lease 경쟁·unknown commit 수렴이다. 문서의 code/field/date 범위와 실제 serialization도 검사한다. `./gradlew clean test`, `./gradlew mongoIntegrationTest`, migration Node test와 `git diff --check`를 구현 후 수행한다.

배포는 Identity 신규 claim 발급 및 구버전 발급 instance drain → 기존 Token 최대 TTL+skew 경과 → Mongo migration/catalog·AI 서비스 인증/TLS 준비 → Learning Core/AI OFF 배포 → staging contract/E2E → 환경별 Challenge 활성화 순서다. 다른 환경의 최초 활성화가 운영 Day 1을 먼저 소비하지 않도록 database/metadata 격리를 확인한다.

| 예상 변경 위치 | 책임 |
| --- | --- |
| `domain/challenge/{api,application,domain,repository,config,security,infrastructure}` | 신규 aggregate·API·Job·Callback·catalog·인가 |
| `global/config/SecurityConfig.java` 및 Challenge 전용 config | 일반 API MEMBER gate·기존 deny gate 유지, Callback exact chain |
| `src/main/resources/application*.yml` | default-off flag와 외부 주입 설정 |
| `scripts/mongodb` | catalog/attempt/Job/index 사전검사와 migration |
| `src/test`, `src/mongoIntegrationTest` | fixture·회귀·격리 Transaction/경합 검증 |

근거: [상세 결정서](TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md), [프론트 계약](../contracts/ten-second-challenge-frontend-api.md), [AI 계약](../contracts/ten-second-challenge-ai-api.md), [현재 보안 chain](../../src/main/java/web/tosunsaeng/global/config/SecurityConfig.java), [현재 사용자 식별](../../src/main/java/web/tosunsaeng/global/auth/JwtCurrentUserProvider.java), [격리 Mongo task](../../build.gradle).

## 7. 부록 A — 코드 책임과 저장 구조

다음 이름은 구현 예정이며 현재 존재하는 클래스라고 보장하지 않는다. 패키지·collection 이름은 구현 시 일관되게 확정하되 공개 계약과 분리한다.

| 구현 단위 | 책임 | 경계 |
| --- | --- | --- |
| ChallengeController / ChallengeQueryService | 승인된 사용자 API 7개, 진행도·결과·history DTO 조립 | 실제 userId·Job·내부 enum 노출 금지 |
| ChallengeMemberAccess / ChallengeSecurityConfig | verified JWT MEMBER 인가, Callback 전용 chain·body limit | 기존 시험·UserMerged·UserWithdrawn decoder 변경 금지 |
| ChallengeCatalogService / ChallengeDateResolver | 콘텐츠 검증, atomic baseDate, KST 날짜 | Clock 주입, system default timezone 의존 금지 |
| ChallengeAttemptService / ChallengeExpiryService | 생성 replay·순서·1시간 deadline·lazy CAS·bounded expiry | AI 완료를 다음 문제 진행 조건으로 삼지 않음 |
| ChallengeAudioStorage | URL 발급, 객체 metadata 검증, 같은 key 최신 객체 읽기(version pin·보관본 제외) | 기존 S3Client/S3Presigner bean 재사용, 시험 key 생성기 미변경 |
| ChallengeSubmitService / ChallengeTransactionExecutor | 제출 receipt·attempt·Job 원자 저장, unknown commit 재조회 | S3·HTTP·sleep을 Transaction에 넣지 않음 |
| ChallengeDispatchWorker / ChallengeAiClient | Job claim·lease·multipart·202·transport retry | 사용자 token/S3 위치 전송 금지 |
| ChallengeCallbackService / ChallengeTimeoutWorker | semantic digest·generation fencing·결과 저장·자동 복구 | 현재 결과를 stale 응답으로 덮어쓰지 않음 |
| ChallengeStartupValidator / migration script | 설정·Mongo Transaction·index·catalog 사전검사 | default OFF, 운영 DDL 자동 실행하지 않음 |

### A.1 저장 모델 및 index 초안

| Collection | 저장 내용 | 필수 조회·유일성 |
| --- | --- | --- |
| challenge_10s_questions (기존) | dayNumber와 3개 embedded question | dayNumber unique; embedded questionNumber/questionId 중복은 validator로 별도 검사 |
| challenge_10s_catalog_state | `_id=active:v1`, contentBaseDate, 초기화 UTC 시각 | singleton `_id` unique |
| challenge_10s_attempts | UUID `_id`, userId, challengeDate, questionNumber, 전체 콘텐츠 snapshot, createdAt/deadline, 고정 upload key, 상태·결과 projection | `(userId,challengeDate,questionNumber)` unique; `(state,submissionDeadlineAt,_id)` 만료 조회 |
| challenge_10s_submit_receipts | userId, Idempotency-Key, 정규화 command digest, attemptId, 최초 성공 응답 snapshot | `(userId,idempotencyKey)` unique. key가 다른 attempt에 재사용돼도 conflict를 찾음 |
| challenge_10s_grading_jobs | 결정적 jobId, attemptId, generation, state, upload key 참조, leaseToken/until, dispatch count/nextAt, acceptedAt/deadline, terminal evidence | `_id=jobId`; `(attemptId,generation)` unique; 상태+nextAt, 상태+leaseUntil, 상태+callbackDeadlineAt 조회 index |
| challenge_10s_callback_receipts | callbackId, attemptId/jobId/generation, semantic digest와 저장 outcome | callbackId unique. 원문 payload/credential을 보관하지 않음 |

Attempt에 현재 generation과 최종 AI 결과를 저장하고 Job에는 전송·재시도·Callback 증거를 둔다. 조회는 Attempt snapshot/projection만으로 완결하며 Job과 결과가 모순되지 않도록 같은 Transaction에서 변경한다. 별도 결과 collection을 불필요하게 추가하지 않는다.

- unique multikey index만으로 한 catalog 문서 안의 questionId 중복이 방지된다고 가정하지 않는다. 전체 catalog 사전검사와 읽기 시 검증을 모두 둔다.
- TTL index를 deadline에 걸지 않는다. EXPIRED도 사용자 결과·이력·응시 제한의 근거다.
- receipt/Job을 먼저 지워 재요청이 신규 제출로 해석되지 않게 한다. audio·결과·receipt 보존 및 개인정보 삭제 기간은 별도 운영 정책 확인 대상이며 임의 보존일수·삭제 작업을 추가하지 않는다.
- dayNumber/date/string UUID·enum·필수 snapshot·중복·참조 일관성을 dry-run에서 검사한다. apply는 명시적으로 선택한 DB에서만 수행하며 기존 시험 collection을 고치지 않는다.
- 사용자 월 이력은 owner/date 범위의 최대 93개 attempt를 읽어 최대 31개 날짜로 집계한다. 날짜별 조회 N+1 또는 전체 catalog scan을 하지 않는다.

### A.2 사용자 상태와 Transaction 기반

현재 `UserOwnedTransactionExecutor.execute()`는 writer flag OFF이면 Transaction 없이 command를 실행한다. 따라서 Challenge submit의 원자성을 이 메서드 하나에 의존하지 않는다. Challenge flag ON에서 사용할 명시적인 Mongo Transaction 경계를 구성하고, 기존 writer guard가 활성화돼 있으면 그 body 안에서 `touchWithinExistingTransaction()`으로 참여시킨다. 다른 manager의 TransactionTemplate을 겹쳐 호출하지 않는다.

사용자용 API는 기존 withdrawn/merged deny gate와 MEMBER 검사를 모두 거친다. callback/worker는 사용자 JWT를 받지 않으며 attempt에 저장된 owner 및 기존 guard 정책을 사용한다. MEMBER 전용이라는 사실만으로 HTTP gate를 생략하지 않는다. Challenge의 Guest 데이터 migration, phone 재가입 승계, 새 withdrawal 삭제 정책은 이 이슈에 추가하지 않는다. background 처리와 withdrawal 경합 시 적용할 기존 정책의 재사용 가능 여부는 통합 테스트 전 점검한다.

관련 실제 기반:

- `src/main/java/web/tosunsaeng/domain/usermerge/application/UserOwnedTransactionExecutor.java`
- `src/main/java/web/tosunsaeng/domain/usermerge/application/UserOwnershipGuardService.java`
- `src/main/java/web/tosunsaeng/domain/usermerge/security/MergedUserAccessGateFilter.java`
- `src/main/java/web/tosunsaeng/global/config/S3Config.java`
- `build.gradle`의 `mongoIntegrationTest`와 Testcontainers MongoDB 의존성

## 8. 부록 B — 사용자 command와 음성 덮어쓰기

### B.1 Attempt와 공개 projection

| 내부 상태 / evidence | 공개 attemptStatus | gradingStatus | 결과·다음 문제 |
| --- | --- | --- | --- |
| attempt 없음 | not_started | not_requested | 결과 없음 |
| CREATED, deadline 전 | not_started | not_requested | attempt 재호출로 복구, 다음 문제 잠금 |
| EXPIRED | submitted | not_requested | snapshot 참고 답안, AI 결과 null, 다음 문제 허용 |
| SUBMITTED, Job 대기 | submitted | pending | 참고 답안, 다음 문제 허용 |
| SUBMITTED, AI 접수 확인 | submitted | processing | 참고 답안, 다음 문제 허용 |
| SUBMITTED, completed/no_speech 저장 | submitted | completed | snapshot 답안을 포함한 aiResult |
| SUBMITTED, 최종 실패 | submitted | failed | 참고 답안, aiResult null, 재응시 금지 |

`dailyStatus`는 attempt가 전혀 없으면 not_started, 하나라도 생성돼 있고 terminal이 3개 미만이면 in_progress, terminal 3개면 completed다. 생성만 했을 때 `dailyStatus=in_progress`와 `attemptStatus=not_started`가 동시에 나올 수 있다. `completedQuestionNumbers`도 만료를 포함한 진행 종료 기준을 유지한다. 반면 풀이 수는 실제 audio 제출 접수인 내부 SUBMITTED만 세며 EXPIRED는 제외한다. 세 문제 모두 만료면 dailyStatus=completed·solvedQuestionCount=0·participated=false다. 실제 제출 1개+만료 2개면 풀이 수는 1이다.

EXPIRED의 `submittedAt=null`은 사용자 승인으로 확정됐다. `gradedAt=null`, `gradingStatus=not_requested`, `aiResult=null`과 snapshot 참고 답안을 반환한다. 내부 expiredAt은 deadline으로 기록하며 제출 시각으로 꾸미지 않는다. 새 field 없이 프론트 null 처리와 fixture를 검증한다.

### B.2 생성·조회·upload

1. 인가 후 현재 `Clock`에서 KST 날짜를 결정하고 형식·문항 번호·X-Challenge-Date를 검증한다.
2. 같은 owner/date의 기존 attempt를 읽고 필요한 lazy expiry를 CAS 처리한다. 이전 문제가 제출 또는 만료됐는지 확인한다.
3. 유효한 기존 attempt가 있으면 같은 ID/deadline을 반환한다. terminal은 ALREADY_ATTEMPTED다.
4. 신규 생성 직전 날짜를 재확인하고 catalog snapshot·server UUID·고정 key·createdAt+1시간을 저장한다. unique 충돌은 abort된 Transaction 밖에서 winner를 읽는다.
5. 날짜 판정은 성공 생성 연산의 기준 시점으로 고정한다. 응답 전송 시각이 자정을 넘었다고 이미 저장된 attempt의 날짜를 바꾸지 않는다.
6. URL 발급은 현재 날짜가 아닌 attempt owner/state/deadline을 확인하며, 기본 5분을 내부 권장값으로 두되 실제 만료는 남은 제출 시간 이하로 제한한다. field·format은 프론트 v1을 따른다.

조회가 유발한 lazy expiry는 실제 쓰기다. writer guard가 켜져 있으면 기존 guard와 일관된 Transaction/CAS 경계를 사용한다. 순수 읽기 경로에 불필요한 revision 증가를 추가하지 않는다.

### B.3 제출 원자성 및 replay

1. MEMBER/deny/owner와 path questionNumber=attempt.questionNumber를 확인한다. URL에 today가 있어도 answer는 attempt.challengeDate를 사용한다.
2. `(owner,Idempotency-Key)` receipt를 먼저 읽는다. 같은 command digest면 최초 성공 응답을 반환하고 다른 command면 IDEMPOTENCY_CONFLICT다. replay 응답을 현재 AI 상태로 재조립하지 않는다.
3. 미제출/deadline 전 상태를 확인한 뒤 Transaction 밖에서 현재 upload key 객체의 metadata를 검증한다. 파일 version은 고정하지 않는다. digest는 caller의 원문 JSON이 아니라 operation/path questionNumber/attemptId를 정규화한 의미를 기준으로 만든다.
4. Mongo Transaction 안에서 guard 참여 → receipt 재확인 → deadline/state CAS → SUBMITTED + acceptedAt + upload key 참조 + generation 1 → Job 1 → receipt 성공 응답을 함께 저장한다.
5. commit 뒤에만 HTTP 성공을 반환한다. worker는 commit된 Job만 읽는다. 다른 key로 이미 제출된 attempt에 요청하면 ALREADY_ATTEMPTED이며 새 Job을 만들지 않는다.
6. duplicate/write conflict는 전체 Transaction이 종료된 뒤 재조회한다. transient retry는 제한된 횟수만 새 Transaction으로 수행한다.
7. unknown commit은 receipt와 attempt/Job 증거를 새 읽기로 확인한다. commit 성공이 확인되면 replay하고, 단순한 첫 미조회만으로 실패 확정·artifact 삭제·새 generation 생성을 하지 않는다. 확인 불가 시 기존 COMMON500으로 동일 key 재시도를 유도한다. 공개 503 등 새 오류 계약은 임의로 만들지 않는다.

S3 검사 중 deadline이 지나면 최종 DB CAS에서 제출을 거절하고 만료로 수렴한다. acceptedAt은 최종 제출 상태 전이의 서버 시각이며 audio 검증 시작 시각으로 deadline을 우회하지 않는다.

### B.4 마지막 업로드 유지 — 사용자 확정

정확한 내부 경로는 `temp/challenges/{attemptId}/q_{questionNumber}.m4a`로 확정한다. 예를 들어 2번 문제는 `temp/challenges/{attemptId}/q_2.m4a`다. 기존 설정의 버킷을 사용하며 공개 DTO에 object key field를 추가하지 않는다. 프론트는 반환받은 Presigned URL을 그대로 사용하고 AI에는 파일 binary만 전달한다. 경로에 다른 prefix를 임의로 덧붙이지 않는다.

배포 전 기존 `temp/` 대상 lifecycle과 task role의 해당 prefix 접근 권한을 확인한다. AI 전송·재시도 중 객체가 삭제되지 않아야 하며 기존 시험을 위한 lifecycle/IAM을 이 작업에서 변경하지 않는다. 실제 설정 확인은 아직 수행하지 않았다.

사용자 확인: 다시 녹음은 answer 제출 전에만 가능하다. 같은 attempt/key/최초 1시간 deadline을 유지하며 새 AI Job을 만들지 않는다. 이전 PUT과 최종 PUT/answer를 경쟁시키지 않고 최종 업로드 완료 뒤 answer를 보낸다. answer 전송 후 응답이 유실되면 재녹음 대신 같은 Idempotency-Key replay로 접수 여부를 복구한다.

- 같은 attempt의 같은 upload key에 PUT 덮어쓰기를 허용한다. 현재 객체는 마지막으로 성공한 PUT 기준이며 동시 업로드에서 앱의 녹음 순서까지 보장하는 것은 아니다.
- 이미 발급된 URL은 유효기간 동안 제출 후에도 사용할 수 있다. 기존 계약대로 terminal attempt에는 새 URL을 발급하지 않는다.
- versionId pin, 별도 immutable object, 제출 시 파일 복제는 구현하지 않는다. 기존 bucket versioning을 끄거나 과거 version을 삭제하는 운영 변경도 수행하지 않는다.
- submit은 당시 metadata를 검사하고 최초 AI 전송은 읽는 시점의 현재 객체를 사용한다. 이후 덮어쓰기될 수 있으므로 다운로드 시 MIME·최대 2 MiB를 재검사하고 bounded read를 사용한다. binary 검증 책임은 AI에 유지한다.
- AI가 이미 접수한 audio와 나중에 S3에 남은 audio가 다를 수 있다. 덮어쓰기는 새 사용자 응시·새 generation·완료 결과 수정의 트리거가 아니다.
- **승인된 방어 처리:** 기존 AI의 같은 job/key·다른 audio=409 계약을 유지한다. 최초 AI 전송 bytes의 SHA-256을 durable metadata로 저장하고 재전송은 해당 digest와 같은 bytes만 사용한다. hash는 파일 보관본이 아니며 ETag/versionId로 대체하지 않는다. 같은 로드 bytes를 검증·hash·전송하고 검증 후 S3를 다시 읽어 보내지 않는다.
- 최초 digest 등록은 현재 lease/currentGeneration/미완료 조건의 CAS로 HTTP 전에 확정한다. 프로세스 재시작·lease 회수·새 generation에도 attempt에 선택된 digest를 유지한다. 나중에 읽은 음성이 다르면 digest를 바꾸거나 새 generation으로 우회하지 않는다. 일치하는 bytes를 복구할 수 없으면 전송 중단·채점 실패로 수렴하되 이미 완료된 결과를 덮어쓰지 않는다. 사용자 제출·풀이 수·참고 답안은 유지한다.
- 이 방어는 최초 AI 전송 시 선택한 음성의 동일성 검증이다. 제출 bytes를 보관하지 않으므로 submit과 최초 AI 읽기 사이의 덮어쓰기를 원천 차단한다고 주장하지 않는다. 정상 프론트는 제출 뒤 재녹음하지 않으며 이전 PUT이 남지 않게 한다.
- 로컬 구현은 완료했지만 운영 S3 object·권한·versioning·lifecycle은 변경하지 않았다.

## 9. 부록 C — Job 상태 전이와 Callback 경계

아래 상태명은 내부 초안이다. 새 공개 enum이 아니다. 전송 claim은 leaseToken·leaseUntil로 제어하고 완료 변경은 Job revision 및 attempt.currentGeneration까지 CAS한다.

| 사건 | 원자적으로 저장할 내용 | 외부 동작 |
| --- | --- | --- |
| 최초 submit commit | Attempt SUBMITTED/currentGeneration=1 + Job PENDING + receipt | 앱 200; AI는 commit 후 호출 |
| 전송 claim | PENDING → DISPATCHING, leaseToken/until, 전송 횟수 | HTTP는 Transaction 밖 |
| 유효한 202 수신 | 현재 lease·미완료 확인 후 WAITING_CALLBACK, 최초 acceptedAt +120초 | 공개 processing |
| 202 전 transport 일시 실패 | 같은 Job RETRY_WAIT, nextAttemptAt, lease 해제 | 같은 semantic payload/audio/key 재전송 |
| lease 만료 | CAS로 회수, 새 token 발급 | 중복 HTTP 가능하므로 AI idempotency 필수 |
| 202보다 먼저 Callback 수신 | 유효 Job이면 결과/receipt/terminal 저장 | 204; 뒤따르는 202는 완료를 덮어쓰지 않음 |
| completed/no_speech | Attempt 결과·gradedAt + Job COMPLETED + receipt | completed projection, 204 |
| retryable failed, generation<3 | 해당 Callback receipt + 이전 Job FAILED + 다음 Job PENDING/currentGeneration 증가 | 같은 attempt 새 generation, 204 |
| non-retryable failed 또는 generation=3 failed | receipt + Job/Attempt 최종 실패 | failed projection, 204 |
| WAITING_CALLBACK deadline 경과, generation<3 | 이전 Job TIMED_OUT + 다음 Job PENDING/currentGeneration 증가 | 새 job/key, 이전 Callback stale 204 |
| 마지막 generation timeout | Job TIMED_OUT + Attempt 최종 failed | 유효 late Callback은 204 no-op, 실패 유지 |

### C.1 확정 계약에서 바로 구현할 불변식

- 중복 202가 callback deadline을 계속 연장하지 않는다. 최초 acceptedAt을 저장하고 terminal/Callback evidence를 우선한다.
- 같은 Job은 재전송에 따라 새 generation이 되지 않는다. 새 generation 생성과 currentGeneration 변경은 한 Transaction이다.
- lease는 worker 중복 실행을 줄이는 장치이지 exactly-once HTTP 보장이 아니다. stale lease token은 전송 후 DB 상태 변경도 할 수 없다.
- Callback은 worker lease token을 가지지 않는다. Job/generation/result CAS로 판단하므로 lease 보유자가 없어도 결과를 저장할 수 있다.
- timeout과 Callback이 경쟁하면 동일 Job/Attempt의 write conflict·CAS로 한쪽만 확정한다. 다음 generation이 먼저 commit되면 이전 결과는 현재 답안을 덮어쓰지 않는다.
- receipt의 semantic digest는 알려진 계약 field와 null 규칙을 정규화한다. JSON key 순서·공백·무시 가능한 추가 field를 결과 차이로 해석하지 않는다. callbackId 식별자와 semantic 결과 비교를 분리한다.
- callbackId 재사용의 동일성, 현재 generation의 서로 다른 결과 409, 과거 generation stale 204의 검사 우선순위를 AI v1 7.2와 fixture로 고정한다. 기존 receipt를 다른 attempt에 재사용해 결과를 적용하지 않는다.
- unknown commit 뒤에는 callback receipt와 Attempt/Job 증거를 재조회한다. 적용 여부를 확신하지 못하면 5xx로 같은 Callback 재전송을 요청하며 성공 204를 먼저 보내지 않는다.
- multipart boundary 같은 transport 인코딩이 달라도 semantic field·audio bytes는 동일하다. 서명/인증 값과 실제 음성은 fixture에 넣지 않는다.

### C.2 AI 재시도 종료 — 사용자 승인 확정

기존 AI v1의 연결 3초·접수 응답 15초·202 이후 Callback 120초·최대 채점 generation 3회는 유지한다. AI→Learning Core Callback 전달도 5초 backoff 시작·최대 간격 10분·최대 시도 10회로 유지한다. 아래는 사용자 승인으로 추가 확정한 Learning Core 종료 정책이며 AI 팀의 실제 적용·fixture 검증은 별도다.

| 경계 | 확정 처리 |
| --- | --- |
| 202 접수 전 일시 실패 | 같은 job/key/generation/audio로 재전송, 해당 Job의 최초 dispatch부터 총 5분 |
| 접수 전 backoff | 1초 시작·30초 상한 exponential backoff+jitter, 429 Retry-After를 앞당기지 않음 |
| 5분 소진·접수 불명 | 추가 전송 중단·Job/Attempt 최종 failed·경보, 예산 회피용 새 generation 생성 금지 |
| 마지막 generation Callback timeout | 최종 failed, 이후 유효 late Callback은 204 no-op |
| 인증·payload 영구 HTTP 오류 | 기존 분류대로 자동 transport retry 중단·격리/경보·공개 failed, 자동 재개 추가 없음 |

- 최초 dispatch에서 firstDispatchAt과 dispatchDeadlineAt=firstDispatchAt+5분을 HTTP 전에 저장한다. 재시작·lease 회수·전송 재시도가 시각을 초기화하지 않는다. 정상적인 채점 재시도로 생성된 새 Job은 자체 최초 dispatch 예산을 갖는다.
- 총 5분에는 연결·응답 대기·backoff가 모두 포함된다. 각 HTTP 호출은 기존 timeout과 남은 예산 중 짧은 범위로 제한한다. deadline 이후 새 전송하지 않는다. Retry-After가 남은 예산보다 길면 앞당겨 전송하지 않고 예산 만료로 수렴한다.
- 유효한 202가 먼저 처리되면 최초 acceptedAt+120초 Callback 대기로 전환한다. 더 이상 접수 전 5분을 결과 대기 제한으로 사용하지 않으며 중복 202가 Callback deadline을 연장하지 않는다.
- 접수 응답보다 먼저 도착한 유효 Callback은 기존 early Callback 규칙으로 처리한다. 실패 확정과 Callback 완료가 경쟁하면 CAS/Transaction으로 하나만 확정하고 먼저 확정된 terminal을 뒤집지 않는다. 단순히 로컬 시계만 보고 이미 완료된 결과를 failed로 덮어쓰지 않는다.
- 최종 실패 뒤 known attempt/job/generation의 유효 late Callback은 결과를 반영하지 않고 204로 전달 재시도를 멈춘다. 이전에 저장된 Callback의 duplicate/conflict 규칙은 유지하며 잘못된 인증·body·미래 generation·식별자·payload conflict를 무조건 204로 삼키지 않는다.
- 사용자 제출·풀이 수·참고 답안은 최종 채점 실패와 관계없이 유지한다. 자동 완료 복원·새 사용자 attempt·임의 새 generation은 만들지 않는다.
- 5분 경계·재시작/lease 회수·Retry-After 예산 초과·early Callback 경쟁·마지막 timeout 뒤 정상/비정상 Callback을 공유 fixture와 replica-set 테스트로 검증한다.

## 10. 부록 D — 테스트와 단계별 인수 기준

재녹음 회귀: answer 전 같은 attempt 재녹음은 Job 0개, 최종 PUT 후 answer 접수는 Job 1개, 처음 정한 deadline 불변, 최초 audio digest의 durable CAS 경쟁, 재시작 후 동일 bytes 전송, 다른 bytes 차단·복구 불가 시 실패, 완료 결과/제출/참고 답안 보존을 검증한다.

| 단계 | 필수 증거 | 완료 조건 |
| --- | --- | --- |
| 1 계약·인가 | MEMBER/GUEST/missing/unknown, 잘못된 JWT, Legacy, flag OFF, 잘못된 Callback credential/method/version, 기존 0/1/2 chain 회귀 | 새 공개 URL/field 없이 fixture와 security 테스트 통과 |
| 2 catalog·attempt | invalid catalog·중복 questionId, Day 1 setOnInsert race, OFF 미초기화, 날짜 변경·정확히 deadline·다중 instance 생성 | 한 owner/date/question에 한 attempt와 고정 deadline/snapshot |
| 3 submit·audio | 같은 key 다른 attempt conflict, 응답 유실 replay, 검사 중 deadline 경과, 다른 owner, metadata 오류/상한, PUT overwrite 후 최신 객체·재검증 | Job/receipt/Attempt 부분 저장 없음; AI 동일 Job audio 충돌 경계는 별도 fixture 검증 |
| 4 dispatch·Callback | 응답 유실·early Callback·stale lease·duplicate payload·미래 generation·Callback/timeout 경쟁·generation 상한 | 현재 결과 보호, 유한 종료, AI fixture 일치 |
| 5 결과·운영 | no-speech 답안 유지, expired fixture, omission/null 구분, 부분 월·콘텐츠 소진·자정 뒤 이전 날짜 제출, 로그/metric privacy | 프론트 wire 일치와 rollout 증빙 |

집계 회귀 fixture에는 전부 만료=0/미참여, 실제 제출 1+만료 2=1/참여, S3 업로드만=0, 제출 후 pending/no-speech/failed도 각 1, 동일 submit replay 증가 없음, 만료 상세 non-null과 풀이 수 0의 공존을 포함한다. 진행 종료와 실제 제출 수가 서로 다른 의도된 계약임을 프론트에도 전달한다.

Mongo replica-set 검증은 mock 테스트와 별도로 수행한다. 각 DB 변경 지점에 오류를 주입해 전체 rollback을 확인하고, 실제 commit 후 응답/ack 유실을 모사한 경우와 commit 전 실패를 구분한다. 예외 객체만 던진 mock을 unknown commit 성공 증거로 삼지 않는다. 동시성 테스트는 barrier/latch로 경쟁을 재현하고 Thread.sleep 의존을 최소화한다.

실행 명령:

```bash
./gradlew clean test
./gradlew mongoIntegrationTest
node --test scripts/mongodb/challenge-10s-prepare.test.js
git diff --check
```

Node script를 구현했고 순수 Node 7개와 격리 mongosh dry-run/apply 검증을 완료했다. Docker API 버전 차이는 테스트 JVM의 api.version=1.44 설정으로 조정했다. 테스트에서 실제 Atlas·S3·Redis·AI provider·사용자 credential을 사용하지 않았다. 이는 실제 환경 staging E2E나 production 활성화 gate 통과를 대신하지 않는다.

### D.1 작업 분할

TMI-126 안에서 다음 review 단위로 진행하며 commit/push는 사용자가 수행한다.

1. 계약 fixture·MEMBER 인가·Callback deny-only chain·config OFF 기반
2. catalog/date/attempt·expiry·migration/index
3. 같은 key 음성 덮어쓰기·submit receipt·Transaction 및 replica-set 경합 검증
4. AI dispatch/Callback/timeout·lease·contract fixtures
5. query/history·관측성·전체 회귀·운영 runbook

부분 구현을 production 활성화 가능한 완료 상태로 표시하지 않는다. 새로운 queue/Kafka/SQS·Redis 체계·시험 refactor를 추가하지 않는다.

## 11. 부록 E — 활성화·중단 체크리스트

- [ ] Identity account_type 발급 전환·구형 issuer drain·운영 최대 TTL와 검증 skew 증빙
- [ ] 프론트 EXPIRED null timestamp fixture, 기존 history/415/no-speech 적용 확인
- [ ] AI transport budget·최종 late Callback 계약/fixture 확인
- [ ] 제출 전 재녹음·최종 PUT 완료 후 answer·동일 digest 재전송/변경 음성 차단·읽기 권한/lifecycle·개인정보 보존/삭제 운영 정책 검증
- [ ] 동일 기존 Mongo 연결에서 catalog·index dry-run, replica-set Transaction 검증. 환경별 DB/metadata 격리
- [ ] 기동 전 설정·index·Day 1 콘텐츠 검증, atomic baseDate 초기화, 오류 시 readiness 차단
- [ ] LC↔AI 방향별 credential·내부 경로·TLS·redirect 차단·rotation 확인
- [ ] 격리 통합 테스트, 실제 iOS/Android audio staging E2E, callback 인증/중복/유실/지연 주입
- [ ] 진행 중 attempt/Job/Callback drain 또는 긴급 중단 후 복구 절차와 경보 수신 확인

flag OFF는 Callback도 deny-all이므로 정상 중단 시 먼저 신규 사용자 유입을 운영적으로 차단하고 진행 중 작업을 drain한 뒤 OFF로 전환한다. 긴급 OFF에서는 AI Callback 재시도가 인증 오류로 격리될 수 있음을 경보하고 Job/receipt/audio를 보존한다. 새 admin API나 이미 합의되지 않은 자동 재시작 기능은 추가하지 않는다. 구버전으로 되돌려도 catalog baseDate·attempt·receipt를 삭제하거나 초기화하지 않는다.

이 체크리스트는 운영 작업을 대신 실행하는 명령이 아니다. 로컬 코드·테스트와 격리 DB migration 검증은 완료했지만 배포·운영 DB migration·Jira 상태 전환은 수행하지 않았다.
