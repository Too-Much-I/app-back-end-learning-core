# TMI-126 — 10초 챌린지 구현 및 활성화 안내

## 1. 5줄 결론

1. Learning Core 전용 Challenge API·Mongo aggregate·S3 업로드·AI worker/Callback을 구현했다. 기본값은 OFF다.
2. 기존 시험 API·AI user_id=examId·S3/Redis key와 BaseResponse는 변경하지 않았다.
3. 최초 enabled 기동이 Day 1을 결정한다. 배포·재기동으로 기준일을 재설정하지 않는다.
4. 실제 프론트·AI·모바일 파일·Identity 운영 배포·AWS 권한 검증은 로컬 테스트와 별개다.
5. 운영 DB migration·배포·flag 활성화는 이번 구현에서 실행하지 않았다.

## 2. 반드시 읽을 내용

구현 코드는 `src/main/java/web/tosunsaeng/domain/challenge`에 모았다.

| 파일 | 책임 |
| --- | --- |
| ChallengeController / ChallengeViews | 승인된 7개 사용자 API와 공개 projection |
| ChallengeService / ChallengeCatalog | KST 날짜·콘텐츠·snapshot·1시간 attempt·제출·이력·lazy expiry |
| ChallengeModels / ChallengeStore / ChallengeTransactions | 별도 collection, version CAS, 명시적 Mongo transaction, majority receipt 복구 |
| ChallengeAudioStorage | 고정 key PUT URL, metadata 검사, bounded download와 rejected stream abort |
| ChallengeAiClient / ChallengeWorker | multipart, 30초 lease, 5분 전송 예산, 120초 Callback 대기, 최대 3 generation |
| ChallengeCallback / ChallengeCallbackService | strict JSON·semantic digest·receipt·stale/duplicate/late fencing |
| ChallengeSecurityConfiguration / ChallengeExceptionAdvice | 전용 opaque Bearer chain(-1), OFF 차단, 공개/내부 오류 격리 |
| ChallengeConfiguration / ChallengeProperties / ChallengeStartupValidator | 설정·index·transaction·catalog 검증과 전용 scheduler |
| ChallengeMetrics | 고정 stage/outcome counter와 monotonic operation duration histogram |

공개 날짜/결과 API는 MEMBER JWT만 사용한다. GUEST·missing/unknown account_type·Legacy 익명은 거절한다. 기존 withdrawal(401 ACCOUNT_WITHDRAWN)·merged(403 ACCOUNT_MERGED_TOKEN_REJECTED) gate는 기존 응답 그대로 적용된다. 기존 gate 저장소 장애 응답도 이 작업에서 재정의하지 않았다.

만료는 다음 문제·참고 답안 접근만 열고 실제 풀이 수에는 포함하지 않는다. 생성·업로드만 한 attempt도 풀이 수 0이며 실제 제출 후 AI 실패는 풀이 수 1을 유지한다. 제출 receipt는 최초 응답을 보존하므로 완료 후 replay도 최초 pending 응답을 반환하고 현재 채점 상태는 결과 API로 조회한다.

S3 key는 정확히 `temp/challenges/{attemptId}/q_{questionNumber}.m4a`다. 제출 전 재녹음은 같은 key에 마지막 PUT을 남긴다. version pin·보관본·사용자 재생 URL은 없다. 최초 AI bytes의 SHA-256을 HTTP 전에 저장하며 이후 변경 음성은 같은 Job이나 새 generation으로 보내지 않고 failed로 종료한다. submit과 최초 AI download 사이의 덮어쓰기까지 방지하는 불변 파일 저장은 아니다.

## 3. 설정과 운영자가 확인할 사항

| 환경변수 | 의미 / 기본값 |
| --- | --- |
| CHALLENGE_ENABLED | false |
| CHALLENGE_AI_ENDPOINT | 정확한 `/v1/challenges/evaluations` URL. staging/prod는 HTTPS |
| CHALLENGE_AI_OUTBOUND_CREDENTIAL | LC→AI 전용 credential, secret store에서 주입 |
| CHALLENGE_AI_CALLBACK_CREDENTIAL | AI→LC 전용 credential, 반대 방향과 다른 값 |
| CHALLENGE_POLL_MS | 1000; 한 번에 최대 20 Job, 별도 scheduler |
| CHALLENGE_EXPIRY_POLL_MS | 10000; 한 번에 최대 100 attempt |

기존 `MONGODB_URI`, `MONGODB_DATABASE`, `AWS_REGION`, `AWS_S3_BUCKET_NAME`을 재사용한다. 신규 cluster·bucket·IAM role을 생성하지 않는다. 실제 credential은 명령행·문서·로그에 직접 기록하지 않는다.

다음은 새 제품 정책 결정이 아니라 운영 활성화 확인 사항이다.

- Identity account_type 발급 배포, 구형 issuer drain, 실제 구형 Token 최대 TTL+검증 skew 경과.
- 프론트가 기존 PUT 완료를 기다린 뒤 answer를 호출하고, answer 전송 이후 재녹음하지 않는지 확인.
- AI가 동일 job/audio 멱등 202, early/duplicate/stale Callback, 120초/3 generation과 최종 late 204 계약을 지키는지 확인.
- iOS/Android의 실제 M4A/AAC-LC, sample rate/channel/2 MiB 검증. LC는 metadata만, binary decode는 AI 책임.
- `temp/challenges/` PUT/HEAD/GET task 권한, lifecycle이 AI 재시도 전에 객체를 삭제하지 않는지 확인.
- 개인정보 보존·삭제 정책 및 장애 알림 수신/대시보드. 임의 TTL이나 자동 삭제를 추가하지 않았다.

## 4. Mongo 사전검사와 활성화 순서

먼저 선택한 환경의 DB backup과 Challenge writer/catalog 변경 중단을 확인한다. 다른 환경과 같은 baseDate metadata를 공유하지 않도록 DB 격리를 확인한다. 기존 운영 DB 연결 값은 승인된 비밀정보 주입 경로로 준비한다.

기본 dry-run:

```bash
node scripts/mongodb/challenge-10s-prepare.js
```

blocker가 없고 운영 적용을 승인받은 뒤에만:

```bash
CHALLENGE_WRITERS_DRAINED=true CHALLENGE_PREPARE_APPLY=true node scripts/mongodb/challenge-10s-prepare.js
```

script는 explicit non-system DB를 요구하고 catalog BSON int/중복/내용, attempt·Job·receipt 참조, index 호환성을 검사한다. apply는 Challenge collection과 index만 준비하며 콘텐츠·기준일·기존 시험 collection을 수정하지 않는다. 데이터나 credential 대신 고정 오류 또는 건수만 출력한다. `mongosh`가 필요하다.

기동은 필수 6개 collection·9개 index·비-TTL·transaction rollback·전체 catalog/Day 1을 확인한 다음 singleton `_id=active:v1`을 setOnInsert한다. 기존 기준일은 갱신하지 않는다. 뒤 단계에서 다른 기동 오류가 발생하더라도 이미 만들어진 기준일을 자동 되돌리지 않는다.

순서: Identity 준비 → catalog/migration → LC/AI OFF 배포 → 방향별 인증·TLS·프론트/AI staging E2E → 출시할 KST 날짜에 환경별 feature 활성화. 이번 사용자 승인은 구현 승인이지 production 활성화 승인이 아니다.

## 5. 중단·복구와 남은 위험

- 정상 중단은 신규 사용자 유입을 먼저 운영적으로 차단하고 진행 중 Job/Callback을 drain한 뒤 OFF로 전환한다.
- 긴급 OFF는 Callback도 403으로 거절한다. AI 전달 재시도/격리를 확인하고 attempt·Job·receipt·audio를 보존한다.
- process crash는 lease 만료 후 다른 worker가 같은 Job으로 회수한다. 최초 dispatch/accepted deadline과 audio digest는 초기화하지 않는다.
- 최종 failed는 늦은 Callback으로 자동 복원하지 않는다. 제출 기록·풀이 수·참고 답안은 유지한다. 새 수동 retry/admin API는 추가하지 않았다.
- 자동 expiry를 위해 attempt deadline에 TTL을 설정하면 안 된다. 만료 결과·재응시 방지 증거가 사라진다.
- local 테스트는 운영 부하·ECS 네트워크·모바일 실물·AI 서비스 배포를 증명하지 않는다. 활성화 전에 별도 E2E가 필요하다.

## 6. 테스트와 계약 근거

```bash
./gradlew clean test
./gradlew mongoIntegrationTest
node --test scripts/mongodb/challenge-10s-prepare.test.js
git diff --check
```

로컬 Docker daemon이 Testcontainers 기본 API 1.32를 거절할 때만 테스트 프로세스에 `JAVA_TOOL_OPTIONS=-Dapi.version=1.44`를 주입해 실행했다. 운영 dependency나 daemon 설정은 바꾸지 않았다. Mongo 테스트는 Testcontainers `mongo:7.0.14` 격리 DB만 사용하며 실제 mongosh dry-run/apply도 그 안에서 검증한다.

실제 replica-set 검증 범위: 시작/제출 중복, 각 submit write rollback, 실제 commit 후 ack 유실 receipt 복구, 만료/submit·Callback/timeout 경합, early Callback, generation 상한, worker lease 회수/동시 claim, audio 변경 차단, Retry-After/예산 유지, owner guard와 동일 rollback, snapshot/집계/history.

공유 synthetic fixture: `docs/contracts/fixtures/ten-second-challenge-v1.json`. completed/no_speech/failed는 각각 독립 시나리오다. 같은 Job에 세 결과를 차례로 적용하는 시나리오가 아니다.

정확한 외부 계약은 [프론트 v1](../contracts/ten-second-challenge-frontend-api.md), [AI v1](../contracts/ten-second-challenge-ai-api.md), [구현 계획](TEN_SECOND_CHALLENGE_IMPLEMENTATION_PLAN.md)을 따른다.
