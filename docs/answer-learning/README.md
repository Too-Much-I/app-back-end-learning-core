# 답변 익히기 백엔드 인수인계

`origin/develop`의 `b2b37ff`에서 만든 `feat/answer-learning` 브랜치다. 2026-10-13 최신 develop `3d50195`와의 합류 검사는 별도 임시 복사본에서 통과했다. 프론트 `feat/answer-learning`의 `/api/v1/learning` 계약을 구현한다. 기존 시험·챌린지 API, 공개 DTO, Redis key, AI Callback은 변경하지 않았다. 기본값은 비활성이라 이 브랜치를 빌드하는 것만으로 기존 서비스에서 학습 API나 Worker가 실행되지 않는다.

## 연결된 구성

```text
Expo 앱 → Learning Core (기존 Bearer JWT / CurrentUserProvider)
            ├─ Mongo: 목록·문항·수준별 답변·완료 상태 조회
            ├─ S3: 사진/모범 음성 GET 서명 URL
            └─ 제출 생성 → 임시 녹음 PUT URL → 업로드 확인
                 └─ 채점 전용 S3 key에 최초 1회 고정
                    → Mongo에 queued 작업 저장
                    → 백그라운드 Worker가 IAM 서명으로 Lambda 접수
                    → 결과 조회 → Mongo 결과·완료 기록 저장 → 앱 조회
```

따라 말하기의 실시간 STT는 계속 기기 내에서 처리한다. 서버 채점은 Part 2~5의 **마지막 실전 녹음 제출**에서만 진행한다. Part 1은 서버에 녹음/채점 작업을 만들지 않으며, 앱의 본인 녹음·모범 음성 비교 이후 완료 기록만 저장한다. 현재 구현은 재생 위치나 따라 말하기 중간 진행을 저장하지 않는다.

## 구현 파일

`src/main/java/web/tosunsaeng/domain/answerlearning/` 안에 Controller/Service/Catalog/Store/Storage/LambdaClient/Worker/설정/응답 타입을 모았다.

- `LearningCatalog`: 현재 콘텐츠 버전만 조회하고 프론트 DTO로 투영. Part 1 common, Part 2~5 IM3/IH/AL. Part 3·4는 저장된 문항 순서 유지. 표 전체·발췌·해석·단어 타이밍 보존.
- `LearningStorage`: 콘텐츠 GET 1시간, 녹음 PUT 5분. 오디오 업로드 요청의 크기·MIME 확인. 고정 파일의 ETag/SHA-256을 Lambda에 전달. 실제 AAC 형식/길이 검사는 배포된 Lambda가 STT 전에 실행.
- `LearningStore`: Mongo 조건부 갱신으로 생성·제출·재시도·Worker lease 보호. 저장된 작업이 outbox이므로 별도 서버 SQS 자격 증명은 불필요.
- `LearningWorker`: 앱이 종료되어도 접수/조회/결과 저장. 한 tick에 최대 10개를 조회하고 `(nextRunAt, _id)` 커서로 다음 묶음을 이어 확인해 삭제 중인 사용자의 작업이 다른 사용자 처리를 막지 않는다. 한 순회의 조회 시각을 고정하고 끝에서 커서를 초기화하므로 새 작업의 유입 중에도 차단됐던 작업을 다시 확인한다. 네트워크 오류는 **같은 submissionId:attempt**로만 재전달. 모델 실패는 사용자 retry 호출이 있어야 다음 attempt를 생성한다.
- `LearningLambdaClient`: 기존 AWS SDK 자격 증명 체인으로 SigV4 `lambda/ap-northeast-2` 서명. 프론트 JWT나 OpenAI 키를 Lambda 요청에 전달하지 않는다.

기존 앱 계약을 유지하면서 UserMerged 및 학습 기록 삭제 흐름에 신규 데이터만 연결했다. Java 운영 의존성 추가는 없다. 전용 Lambda의 소스/고정 의존성/빌드 도구/합성 테스트는 `lambda/answer-learning/`에 함께 전달한다.

## 설정과 실행

기존 Spring/MongoDB/Redis/JWT/S3 설정은 그대로 필요하다. 아래 값은 비밀이 아니며 현재 준비한 staging 콘텐츠와 Lambda다. AWS 키·MongoDB URI는 저장소에 넣지 않는다. ECS에서는 task role을 사용하고, 로컬에서는 AWS CLI 프로필/SSO 등을 사용한다.

```dotenv
ANSWER_LEARNING_ENABLED=true
ANSWER_LEARNING_GRADING_ENABLED=true
ANSWER_LEARNING_DATASET_ID=ts-level-bank-40-per-part-20261007-v1
ANSWER_LEARNING_CONTENT_VERSION=20261012-cda52eb5001a
ANSWER_LEARNING_S3_BUCKET=tosunsaeng-app-audio
ANSWER_LEARNING_LAMBDA_URL=https://ywga5eassoft7jlpymoxttrdfa0vfruw.lambda-url.ap-northeast-2.on.aws/
ANSWER_LEARNING_AWS_REGION=ap-northeast-2
ANSWER_LEARNING_DAILY_SUBMISSIONS=60
ANSWER_LEARNING_POLL_MS=1500
AWS_REGION=ap-northeast-2
```

Spring Boot는 `.env`를 자동으로 읽지 않는다. IDE/컨테이너 환경변수 또는 쉘에 실제 설정을 주입한다. Java 21로 `./gradlew bootRun`을 실행한다. local/test에서만 기존 legacy 인증 모드가 가능하다. staging/prod는 기존 JWT 검증 정책을 유지한다. 학습 API에 userId를 보내지 않는다.

위 예시는 현재 테스트 활성화 값이다. **2026-10-13 최신 Lambda와 삭제 계약을 검증한 뒤 테스트 ECS `tosunsaeng-learning-core-test:26`에 `ANSWER_LEARNING_GRADING_ENABLED=true`를 적용했다.** 기존 실행 이미지는 유지했으므로 새 학습 API가 포함된 백엔드 이미지 배포는 아직 필요하다. [현재 적용 상태](test-release.md)를 따른다.

콘텐츠만 먼저 연결할 때는 `ANSWER_LEARNING_GRADING_ENABLED=false`로 둔다. 목록·상세·미디어·Part 1 완료는 사용할 수 있고 채점 지원은 ready=false다. 기능 활성 시 지정 dataset/version의 `learning_datasets.status=ready`를 확인하며, 없으면 시작을 거절한다. 신규 사용자 컬렉션 인덱스는 기능 활성 시 생성한다. 활성화에는 기존 `UserOwnedTransactionExecutor`의 writer fence가 켜져 있어야 하며, 없으면 시작을 거절한다. Mongo replica set과 기존 UserMerged/삭제 설정을 유지한다. 기존 콘텐츠 컬렉션은 읽기만 한다.

프론트의 샘플 모드를 해제하고 기존 API base URL을 이 서버로 지정한다. [API 계약](api.md)의 URI/응답을 그대로 사용하므로 새로운 프론트 전용 경로는 필요 없다. 앱 샘플의 고정 Decisions 결과와 이 서버의 실제 채점 결과를 혼용하지 않는다.

## AWS 역할 권한

2026-10-13 사용자 요청으로 테스트 백엔드 **task role** `tosunsaeng-learning-core-test-task-role`에 다음 기존 관리형 정책을 연결했다. execution role 또는 프론트 사용자에 연결하지 않는다.

`arn:aws:iam::889384901776:policy/tosunsaeng-answer-learning-staging-BackendInvokePolicy-Te87n2HOPfmQ`

추가 S3 권한 예시는 [backend-s3-policy.json](backend-s3-policy.json)이다. 읽기 prefix는 현재 콘텐츠 버전으로 고정했고 쓰기·목록 조회·모든 버전 삭제는 학습 녹음 prefix에 한정했다. 출판 콘텐츠 삭제 권한은 없다. 현재 SSE-S3(AES256)를 사용한다. SSE-KMS로 변경하면 별도 KMS 정책이 필요하다. 백엔드는 DynamoDB/SQS/Secrets Manager/OpenAI 직접 호출 권한이 필요 없다.

Lambda URL은 브라우저에서 열면 403이 정상이다. IAM 서명한 백엔드만 호출한다. 테스트 task role에 S3 인라인 정책 `LearningCoreTestAnswerLearningS3`도 추가했다. 기존 정책 3개와 버킷 정책은 그대로 보존했다. IAM 권한 추가, Lambda 코드 배포, 기존 서버 이미지를 유지한 ECS 설정 적용은 각각 검증했다. 결과와 되돌리기 절차는 [테스트 환경 적용 문서](test-release.md)에 있다.

## DB와 준비된 데이터

읽기: `learning_datasets`, `learning_units`, `learning_lessons`, `learning_answers`, `learning_media`, `learning_checklists`.

현재 업로드: 200카드 / 360문항 / 1,000답안 / 2,756미디어. **2026-10-12 전체 Part 2–5 체크리스트 320문항·721항목 작성 및 로컬 휴대폰 테스트 DB 반영 완료.** Part 2/3/4/5 각각 40/120/120/40문항이며 Part 1은 제외한다. 기준 버전은 `question-checklists-20261012-v1`, 콘텐츠 버전은 그대로다. 최초 운영 Atlas 업로드는 9개였으며, 2026-10-13 Atlas 테스트 DB `to-teacher-learning-core-test`에는 전체 320개 반영과 재조회 검증을 완료했다. 운영 DB의 9개는 이번에 변경하지 않았다. 미준비 문항을 임의 채점하지 않는다. 현재 버전에서 같은 문항에 prepared 기준이 2개면 임의 선택하지 않고 오류 처리한다.

전체 기준·Mongo 문서·검토 HTML은 인접 AI 저장소 `app-ai-learning/data/practice_checklists/full-bank/`에 있으며 전달본은 `mongodb/learning_checklists.json`이다. 세부 기준/재현은 해당 저장소 `docs/local-practice-grading.md`의 “전체 문항 기준” 절을 따른다. 이 데이터는 서버용이며 프론트에 전체 정답 근거를 미리 전달하지 않는다. 사진·전체 표·질문을 작성자가 직접 대조했고 외부 생성/검수 API는 호출하지 않았다. 독립 검수·실제 모델 정확도 평가와는 구분한다.

로컬에서는 구 9개를 `superseded`로 보존하고 새 320개만 `prepared`로 활성화했다. 기존 제출·완료 문서는 변경하지 않았다. 기존 9개가 있는 운영 Atlas 확장 배포 시에는 이전 prepared 버전을 먼저 비활성화하고 문항당 활성 기준 하나를 유지해야 한다. 기존 insert-only importer로 320개를 단순 추가하면 안 된다. 런타임/API/DTO/Lambda 코드를 바꾸지 않아도 새 제출부터 DB 기준을 스냅샷으로 사용한다.

쓰기:

| 컬렉션 | 내용 | 고유 기준 |
| --- | --- | --- |
| learning_submissions | 사용자·문항·수준·버전·체크리스트 스냅샷·파일 해시·attempt·작업/결과 | UUID `_id` |
| learning_completions | 완료 문항/수준/버전만 저장 | userId + lessonId + level + contentVersion |
| learning_submission_limits | UTC 일별 생성/재시도 한도, 2일 TTL | 사용자·날짜의 SHA-256 `_id` |

제출 UUID는 Lambda에서도 전역 키이므로 전역 유일하게 사용한다. 다른 소유자의 같은 UUID는 404다. 사용자별 하루 60회 생성/명시적 재시도가 기본이다. 새 제출 insert 또는 재시도 attempt의 조건부 갱신과 한도 차감은 기존 owner Mongo 트랜잭션 하나에서 함께 확정한다. 동시 중복 요청은 실제 생성/상태 전환에 성공한 한 요청만 차감하며, 한도 초과 시 제출 생성/attempt 변경도 함께 롤백한다. 이미 생성된 동일 요청의 정상 재전송은 한도를 다시 차감하지 않는다.

완료 결과 저장 시 `completionPending`을 함께 남긴다. Worker는 완료 upsert 뒤 이 표시를 해제하므로 그 사이 서버가 종료돼도 재시작 후 복구된다. 클라이언트의 반복 완료 PUT도 같은 문서를 upsert한다. 체크리스트 일부 미충족이어도 채점이 정상 완료되면 학습 완료로 인정한다.

## 제출·재시도·복구 규칙

1. 앱이 새 녹음마다 UUID를 만들고 `/submissions`로 생성한다. 같은 ID에서 문항·등급·버전 변경은 409.
2. `/upload-url`은 `answer-learning-uploads/{id}/recording.m4a`만 쓰도록 서명한다. binary PUT, 정확한 `Content-Type: audio/mp4` 및 실제 파일 크기가 필요하다.
3. `/submit`은 파일을 최대 8 MiB까지 읽어 SHA-256을 계산하고 `answer-learning-submissions/{id}/recording.m4a`에 `If-None-Match: *`로 한 번만 쓴다. 이후 살아 있는 임시 PUT URL이 채점 파일을 바꿀 수 없다. 같은 ID로 새 녹음을 대체하지 않는다.
4. 접수는 Mongo에 queued를 저장한 후 응답한다. Worker만 Lambda를 호출한다. 스케줄러/서버가 중단돼도 Mongo 작업은 남는다. 소유자·attempt·lease 토큰 조건으로 오래된 Worker의 결과 덮어쓰기를 막는다.
5. 429·일시 연결 실패·응답 유실은 최대 60초 간격으로 같은 작업을 복구한다. 불확실한 상황에서 attempt를 증가시키지 않는다. Lambda가 중복 모델 호출을 차단한다.
6. 모델 실패의 retryable=true일 때만 사용자 `/retry`로 최대 3회 시도한다. Lambda의 저장된 전사를 재사용한다. 상태가 처리 중/완료면 현재 작업을 돌려준다.
7. 접수 후 24시간이 지나면 자동 dispatch와 재시도를 중단하고 새 녹음을 안내한다. 7일 후 만료되는 Lambda receipt를 과거 Mongo 작업으로 재생성하지 않는다. 업로드 대기 작업도 24시간 후 닫힌다.
8. 결과에서 작업 ID·문항·등급·콘텐츠/체크리스트 버전·항목 집합·확률·판정·개수를 검증한다. 잘못된 응답을 점수로 만들지 않는다. 앱에는 전사·체크리스트 피드백만 보내고 공급자 내부 응답은 내리지 않는다.

## 검증 및 운영 확인

```sh
./gradlew clean test
./gradlew mongoIntegrationTest --tests 'web.tosunsaeng.domain.answerlearning.*'
```

Docker 29에서 기존 Testcontainers가 API 버전 오류로 시작하지 않으면 테스트 명령에만 `JAVA_TOOL_OPTIONS=-Dapi.version=1.44`를 지정한다. Docker 전역 설정이나 저장소 의존성을 바꾸지 않는다.

단위/MVC 테스트는 AWS·실제 모델·Atlas를 호출하지 않는다. Mongo 통합 테스트는 Docker의 격리된 `mongo:7.0.14` replica set을 사용한다. 로컬 정규화 콘텐츠가 있을 때만 `LEARNING_IMPORT_FIXTURES=/path/to/import`로 전체 데이터 DTO 검증을 추가할 수 있다. 원본 문제·음성·전사·키는 저장소에 포함하지 않는다. 테스트의 발화는 합성된 고정 문자열이다.

배포 전 확인:

- 백엔드 task role에 Lambda/S3 정책 연결, 활성 dataset/version 설정.
- 실제 JWT로 목록·사진·음성 → 녹음 PUT → 제출 → 결과 조회 → 앱 재시작 후 완료 유지 확인.
- `answer_learning.worker.errors`, `answer_learning.transport.errors` 지표와 오래된 queued 작업을 관측. 단순 장애 확인으로 새 제출/모델 호출을 만들지 않는다.
- 임시/고정 녹음은 서로 다른 prefix이므로 일반 보존 기간용 S3 수명주기는 별도로 설정한다. 기존 학습 기록 삭제에 Part 1 완료·Part 2~5 제출/완료·녹음·Lambda 결과/전사 삭제를 연결했다. Lambda 새 DELETE 계약을 먼저 배포하고, 신규 학습 OFF 시에도 정리용 URL/버킷/권한을 남긴다. 공개 접근은 삭제 준비 구간에서 차단하며 안전한 재시작 지점부터 새 학습을 허용한다.
- 웹 업로드를 제공한다면 별도 S3 CORS에서 PUT/GET/HEAD, Content-Type, Range, 필요한 origin을 설정한다. 네이티브 앱 검증과 별개다.

현재 완료 범위는 **브랜치의 백엔드 구현/자동 검증과 테스트용 IAM·Lambda·ECS 설정 배포**다. 새 백엔드 이미지 배포와 실제 사용자 JWT를 사용한 앱 종단 검증은 남아 있다. 이전 Lambda의 단건 STT/Decisions 확인과 이번 최신 패키지의 삭제·인증 계약 검증은 구분한다. 이번 배포에서는 실제 모델을 호출하지 않았다.

## 이번 검증 결과

2026-10-13: Java 전체 689개(선택 fixture 1개 별도 실행), Mongo 전체 152개, 프론트 학습 68개 통과. Java 응답 13개 목록·520개 상세·1,000답안의 프론트 스키마 검증도 통과했다. Lambda/설정 생성기 검증 및 세부 결과는 [release-handoff.md](release-handoff.md)에 있다.

테스트 AWS 적용과 최종 상태는 [테스트 환경 적용 절차](test-release.md), 패키지·실환경 계약 검증은 [Lambda 빌드·배포 기록](../../lambda/answer-learning/README.md)에 있다. main/develop의 새 코드 배포나 운영 환경 적용을 완료했다는 뜻은 아니다.
