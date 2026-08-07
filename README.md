# App Learning Core

기존 토선생 웹 POC와 외부 API 계약을 유지하는 앱용 Learning Core 서비스다. Java 21과 Spring Boot를 사용하며 MongoDB, Redis, AWS S3, Python AI 채점 서버와 연동한다.

## 인증 모드

`APP_AUTH_MODE`는 소문자 `legacy` 또는 `jwt`만 허용한다. 빈 값, 대문자 표기, 오타와 그 밖의 값은 애플리케이션 시작 실패로 처리하며 Legacy로 fallback하지 않는다.

| Profile | 허용 모드 | 동작 |
| --- | --- | --- |
| `local` | `legacy`, `jwt` | 개발자가 명시한 모드로 실행한다. |
| `test` | `legacy`, `jwt` | 테스트 설정과 Mock 또는 테스트용 JWT 검증 구성을 사용한다. |
| `staging` | `jwt` | Legacy 기동과 로컬 Identity 설정을 거부한다. |
| `prod` | `jwt` | Legacy 기동과 로컬 Identity 설정을 거부한다. |

Legacy 모드는 로컬·테스트에서 기존 웹 호환 흐름을 확인하기 위한 용도이며 고정 개발 UUID를 사용한다. 앱용 Learning Core의 staging·prod에서는 사용할 수 없다. 기존 웹 POC 서버는 별도 저장소이므로 이 설정의 영향을 받지 않는다.

### 로컬 Legacy 모드

```bash
APP_AUTH_MODE=legacy \
SPRING_PROFILES_ACTIVE=local \
./gradlew bootRun
```

- Identity 서버나 JWKS 연결 없이 실행할 수 있다.
- 사용자용 API의 기존 무인증 호환 흐름을 유지한다.
- 운영 환경에서 사용하면 안 된다.

### 로컬 JWT 모드

```bash
APP_AUTH_MODE=jwt \
SPRING_PROFILES_ACTIVE=local \
IDENTITY_ISSUER=http://localhost:8081 \
IDENTITY_JWK_SET_URI=http://localhost:8081/.well-known/jwks.json \
IDENTITY_AUDIENCE=tosunsaeng-learning-core \
./gradlew bootRun
```

JWT 모드는 Identity JWKS Public Key를 사용하는 Spring Security OAuth2 Resource Server가 RS256 서명, issuer, audience, `exp`, `nbf`, UUID `sub`를 검증한다. Identity에 요청마다 별도 Token 검증 요청을 보내지 않는다.

### 스테이징·운영

```bash
APP_AUTH_MODE=jwt \
SPRING_PROFILES_ACTIVE=staging \
IDENTITY_ISSUER=<환경별 Identity URL> \
IDENTITY_JWK_SET_URI=<환경별 JWKS URL> \
IDENTITY_AUDIENCE=tosunsaeng-learning-core \
./gradlew bootRun
```

운영에서는 `SPRING_PROFILES_ACTIVE=prod`를 사용한다. staging·prod는 다음 조건을 시작 시 검증한다.

- 인증 모드가 `jwt`인지 확인한다.
- issuer와 JWKS URL이 비어 있지 않은 HTTP(S) URI인지 확인한다.
- audience가 비어 있거나 placeholder가 아닌지 확인한다.
- localhost 또는 loopback Identity URL을 사용하지 않는지 확인한다.
- Legacy Provider와 Legacy SecurityFilterChain이 등록되지 않았는지 확인한다.

Startup 검증은 설정 형식만 확인하며 JWKS endpoint로 네트워크 요청을 보내지 않는다. 실제 배포 도메인은 코드에 하드코딩하지 않는다.

## 보안 경계

기존 JWT 모드의 보호·공개 경로는 변경하지 않는다.

공개 경로:

- `/api/v1/exams/callback/**`
- `/swagger-ui/**`
- `/swagger-ui.html`
- `/v3/api-docs`, `/v3/api-docs/**`
- `/actuator/health`, `/actuator/health/**` (Actuator가 실제로 제공되는 경우)

그 밖의 기존 사용자용 API는 JWT 모드에서 `authenticated()`다. Legacy 모드에서는 기존 웹 호환을 위해 `permitAll` 흐름을 유지한다.

과거 HMAC 기반 `JwtAuthenticationFilter`와 `JwtTokenProvider`는 사용되지 않아 제거했다. JJWT 의존성과 `jwt.secret` 설정도 제거했으며 `JWT_SECRET_KEY`는 더 이상 필요하지 않다. Learning Core에는 Identity RSA Private Key나 공유 JWT Secret을 저장하지 않는다.

## 유지하는 외부 계약

- 기존 API URL, HTTP Method, Parameter, Request/Response DTO와 `BaseResponse`
- `retryCount`, Redis Key·TTL, S3 Presigned URL·Object Key, 음성 제출·Polling 흐름
- Python AI 요청과 Callback의 `user_id = examId`
- Callback의 `examId -> ExamSession -> 실제 userId` 매핑과 시험 소유권 검증
- 클라이언트 요청·응답에 실제 `userId`를 노출하지 않는 규칙

앱 Learning Core가 Python AI로 보내는 문항 채점 multipart에는 요청 출처를 나타내는 `client_source=app`을 추가한다. 앱과 웹 백엔드가 분리되어 있으므로 클라이언트가 이 값을 전달하지 않으며, 공개 submit API와 시험 단위 retry Job 계약은 변경하지 않는다. 전체 요약 요청과 AI Callback JSON에는 이 필드를 추가하지 않는다.

## 완료 시험 이력과 재답변 회차 조회

JWT 모드에서 다음 사용자용 GET API는 Bearer Access Token이 필요하며, 사용자 ID는 Token의 UUID
`sub`에서만 가져온다. 요청이나 응답에 `userId`를 추가하지 않는다. 로컬·테스트 Legacy 모드의 기존
무인증 호환 정책은 그대로 유지한다.

- `GET /api/v1/exams/history`: 현재 사용자의 `completedAt`이 존재하는 `ExamSession`만
  `completedAt` 내림차순, `examId` 내림차순으로 반환한다. `active=false`만으로 완료를 판정하지
  않는다. 신규 `exam_summaries`의 최신 문서를 우선하고, 없으면 `exam_results.totalScore`가 있는
  Legacy 종합 문서를 사용한다. 종합 결과가 없는 완료 시험은 점수·레벨을 `null`,
  `summaryAvailable=false`로 반환한다. `retriedQuestionCount`는 Job과 Legacy 문항 결과를 batch
  결합하여 `retryCount >= 1`이 존재하는 서로 다른 문항 수로 반환한다. `status`는
  ExamSession의 유효 상태, `startedAt`은 ExamSession `createdAt`, `maxScore`는 모의고사 고정
  만점 200을 반환한다.
- `GET /api/v1/exams/{examId}/retries`: 사용자 소유 시험의 `question_grading_jobs`와 문항별
  Legacy `exam_results` 회차를 합쳐, `retryCount >= 1`이 실제로 존재하는 문항만 반환한다. 해당
  문항의 저장된 최초 회차는 비교를 위해 포함하지만 존재하지 않는 0회차는 만들지 않는다. Job이
  없는 Legacy 결과 회차는 `COMPLETED`이고, Job과 결과가 겹치면 Job 상태가 우선한다.

History가 없으면 `histories: []`, 재답변 문항이 없으면 `questions: []`인 200 응답이다. Retries는
점수, 피드백, Transcript, 음성 URL을 반환하지 않는다. 선택한 회차의 상세 피드백은 기존
`GET /api/v1/exams/{examId}/questions?questionNumber={questionNumber}&retryCount={retryCount}`로
조회한다. `dispatchAttempt`는 AI 재전송 횟수이며 사용자 답변 회차인 `retryCount`로 사용하지 않는다.

## AWS S3 인증

Learning Core는 AWS SDK v2의 `DefaultCredentialsProvider`를 사용한다. 프로젝트 전용 `AWS_ACCESS_KEY`, `AWS_SECRET_KEY` 설정은 사용하지 않으며, Region과 Bucket의 기존 설정 계약인 `AWS_REGION`, `AWS_S3_BUCKET_NAME`은 유지한다. `S3Client`와 `S3Presigner`는 같은 Default Credentials Provider Chain을 사용한다.

AWS SDK 모듈은 `2.29.52` BOM 하나로 버전을 관리한다. S3 접근에는 `s3`, 일반·IAM Identity Center Profile에는 `sso`와 `ssooidc`, Assume Role Profile과 Web Identity에는 `sts` 모듈을 사용한다.

SDK는 대략 다음 순서로 자격 증명 소스를 확인한다.

1. Java System Property
2. AWS 표준 환경변수인 `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, 선택적 `AWS_SESSION_TOKEN`
3. Web Identity Token
4. AWS shared profile, Assume Role profile 또는 IAM Identity Center SSO profile(`sso_session` 포함)
5. ECS Container Credentials
6. EC2 Instance Profile

Provider와 S3 Bean을 생성할 때는 자격 증명을 조회하지 않는다. 실제 S3 요청 또는 Presigned URL 생성 시점에 자격 증명을 조회하므로, 프로젝트 전용 Access Key placeholder가 없다는 이유로 ApplicationContext가 실패하지 않는다. Region이나 Bucket처럼 기존에 필수였던 비밀이 아닌 설정의 정책은 바꾸지 않는다.

### 로컬 JVM

AWS CLI profile은 `aws configure`로 만들 수 있다. AWS SSO를 사용하는 경우 먼저 해당 환경의 profile로 로그인한 뒤 `AWS_PROFILE`을 지정한다. `~/.aws/config`의 현대식 `sso_session`, Assume Role Profile과 Web Identity도 SDK 모듈에 포함되며, Profile 이름은 개발자 환경마다 다를 수 있다.

```bash
AWS_PROFILE=<profile> \
AWS_REGION=ap-northeast-2 \
AWS_S3_BUCKET_NAME=<bucket-name> \
./gradlew bootRun
```

Default Chain이 지원하는 AWS 표준 환경변수도 사용할 수 있지만, 장기 Access Key를 로컬 env 파일에 저장하는 방식은 권장하지 않는다. 로컬 Profile은 다음처럼 구분한다.

- 일반 Shared Credentials Profile: 로컬 Docker에서는 `~/.aws/credentials`를 read-only mount로 읽으며 해당 Credential 자체의 만료 정책을 따른다.
- IAM Identity Center SSO Profile: host AWS CLI로 `aws sso login`을 먼저 수행해 유효한 cache를 만든다. 만료되면 host에서 다시 로그인하고 로컬 Docker 컨테이너를 재시작한다.

### 로컬 Docker — Docker Desktop for macOS

`.env.example`을 참고해 Git에서 제외된 `.env.docker.local`을 준비한다. 이 파일에는 `AWS_REGION`, `AWS_S3_BUCKET_NAME`, MongoDB, Redis, Identity, AI 서버 등 실행 설정만 두고 AWS Access Key나 Secret Key는 넣지 않는다.
`AI_SERVER_URL`에는 AI 서버의 base URL만 지정하며 Learning Core가 기존 `/evaluations` 경로를 붙인다.

Dockerfile의 non-root `app` 사용자 홈은 `/app`이므로 host의 AWS profile 디렉터리를 read-only로 mount한다. IAM Identity Center SSO Profile을 사용할 때는 각 개발 세션을 시작하기 전에 host에서 로그인하고 Credential을 검증한다.

```bash
AWS_PROFILE=tosunsaeng

aws sso login --profile "$AWS_PROFILE"

aws sts get-caller-identity \
  --profile "$AWS_PROFILE" \
  >/dev/null
```

`tosunsaeng`은 문서용 Profile 이름이다. 개발자의 실제 Profile 이름으로 변경하며, 검증 결과는 stdout에 출력하지 않는다.

```bash
docker run --rm \
  --platform linux/amd64 \
  --env-file .env.docker.local \
  -e AWS_PROFILE="$AWS_PROFILE" \
  -v "$HOME/.aws:/app/.aws:ro" \
  -p 18080:8080 \
  --name tosunsaeng-learning-core \
  tosunsaeng-learning-core:local
```

전체 `.aws` mount에는 `credentials`, `config`와 IAM Identity Center의 `sso/cache`가 포함된다. read-only mount이므로 컨테이너는 host의 Profile과 SSO cache를 읽을 수 있지만 수정할 수 없다. 로컬 Docker에서는 컨테이너 내부 SSO token cache 갱신을 보장하지 않는다.

### 로컬 Docker — native Linux

native Linux의 bind mount는 host UID와 `0600` 같은 파일 권한을 그대로 보존한다. host UID와 image의 `app` UID가 다르면 기본 `app` 사용자가 Profile을 읽지 못할 수 있으므로, 로컬 실행 프로세스를 host UID/GID로 실행한다. 현재 linux/amd64 image의 app group GID는 `999`이고 `/app`은 이 group만 진입할 수 있어 supplementary group도 함께 지정한다.

IAM Identity Center SSO Profile을 사용할 때는 macOS와 마찬가지로 각 개발 세션 전에 host에서 로그인하고 Credential을 검증한다.

```bash
AWS_PROFILE=tosunsaeng

aws sso login --profile "$AWS_PROFILE"

aws sts get-caller-identity \
  --profile "$AWS_PROFILE" \
  >/dev/null
```

`tosunsaeng`은 예시이므로 개발자의 실제 Profile 이름으로 변경한다.

```bash
docker run --rm \
  --platform linux/amd64 \
  --user "$(id -u):$(id -g)" \
  --group-add 999 \
  -e HOME=/app \
  --env-file .env.docker.local \
  -e AWS_PROFILE="$AWS_PROFILE" \
  -v "$HOME/.aws:/app/.aws:ro" \
  -p 18080:8080 \
  --name tosunsaeng-learning-core \
  tosunsaeng-learning-core:local
```

이 방식은 root로 실행하지 않는다. host UID로 owner-only Profile을 읽고, group `999`는 `/app/app.jar` 읽기에만 필요하며 `/tmp` 쓰기는 유지된다. Profile 디렉터리는 계속 read-only이고 image에 복사되지 않는다. Dockerfile이나 base image에서 app group ID가 변경되면 `--group-add` 값도 해당 image의 app group과 맞춰야 한다. Credential을 world-readable로 변경하지 않는다. Profile 파일 내용, Token, Secret과 Presigned URL은 로그나 저장소에 기록하지 않는다.

### 로컬 Docker — SSO 만료 및 Credential 오류 복구

read-only mount를 사용하므로 실행 중인 컨테이너가 host SSO cache를 수정하거나 장시간 자동 갱신한다고 가정하지 않는다. 다음 증상이 발생하면 SSO session 또는 Credential 만료를 먼저 확인한다.

- `SSO session expired`
- `Unable to load credentials`
- `Token has expired`
- Presigned URL 생성 실패
- S3 `HeadObject`, `GetObject`, `PutObject` Credential 오류

host에서 같은 Profile로 다시 로그인하고 Credential을 검증한다.

```bash
aws sso login --profile "$AWS_PROFILE"

aws sts get-caller-identity \
  --profile "$AWS_PROFILE" \
  >/dev/null
```

재로그인 후 실행 중 컨테이너가 새 token을 즉시 다시 읽는다고 보장하지 않는다. 기존 컨테이너를 종료한 뒤 운영체제에 맞는 위 `docker run` 명령을 다시 실행한다.

```bash
docker stop tosunsaeng-learning-core
```

두 실행 예시 모두 `--rm`을 사용하므로 컨테이너는 종료 후 자동 삭제된다.

### AWS ECS

ECS는 위 로컬 Profile·SSO 절차와 무관하다. AWS Profile을 mount하거나 `aws sso login`을 실행하거나 컨테이너에 AWS Access Key 환경변수를 주입하지 않는다. Task Definition의 `taskRoleArn`으로 연결한 Task Role에 애플리케이션의 S3 권한을 부여하면 AWS SDK가 ECS Container Credentials의 임시 자격 증명을 자동으로 사용한다.

- Task Execution Role: ECR 이미지 Pull, CloudWatch Logs 전송, Secrets Manager 값 주입처럼 ECS agent가 배포·실행에 필요한 권한
- Task Role: 실행 중인 Learning Core 애플리케이션이 S3 등 AWS API를 호출할 때 사용하는 권한

현재 코드가 사용하는 최소 S3 권한은 다음과 같다. 실제 Bucket ARN이나 계정 정보는 코드와 문서에 넣지 않는다.

- `s3:GetObject`: Presigned GET 및 `HeadObject` 확인에 필요한 동등 권한
- `s3:PutObject`: Presigned PUT 업로드에 필요한 권한

### Health Check

프로젝트는 Spring Cloud AWS 자동 구성이나 S3 Health Indicator를 사용하지 않는다. 따라서 `/actuator/health`는 S3 자격 증명을 조회하거나 S3 API를 호출하지 않으며, 기존 MongoDB·Redis Health 정책은 변경하지 않는다. Health 상세 정보는 외부에 노출하지 않는다.

## 문항 단건 결과의 모범답안 음성

기존 `GET /api/v1/exams/{examId}/questions?questionNumber={questionNumber}&retryCount={retryCount}` 계약을 유지한다. `questionNumber`는 필수이고 `retryCount`는 선택이며 기본값은 `0`이다. 요청한 canonical retryCount의 채점 완료 결과가 존재하는 Part 1의 Question 1·2에만 `question.modelAnswer`로 모범답안 음성 정보를 제공한다. 제출 전·처리 중·실패 또는 존재하지 않는 회차와 다른 문항에서는 이 필드 자체를 생략하고 모범답안 Presigned URL도 생성하지 않는다.

`modelAnswer.audioUrl`은 `ExamSession`에 저장된 실제 `mockExamId`로 결정한 모범답안 음성의 임시 Presigned GET URL이고, `modelAnswer.spokenWordSequence`는 해당 음성의 단어별 타이밍과 발음 점수다. 사용자 녹음인 `question.audioUrl`·`question.spokenWordSequence`, 문제 출제용 음성인 `question.questionInfo.audioUrl`과는 서로 다른 데이터다.

아래 URL 값은 실제 URL이 아닌 문서용 placeholder다.

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "성공입니다.",
  "result": {
    "examId": "ex_example",
    "question": {
      "partNumber": 1,
      "questionNumber": 1,
      "retryCount": 0,
      "totalRetryCount": 1,
      "audioUrl": "temporary-user-audio-url",
      "score": 2.8,
      "maxScore": 3.0,
      "transcript": "사용자 답변",
      "feedback": {},
      "azureFeedback": {},
      "spokenWordSequence": [],
      "modelAnswer": {
        "audioUrl": "temporary-model-answer-audio-url",
        "spokenWordSequence": [
          {
            "index": 0,
            "segmentIndex": 0,
            "wordIndex": 0,
            "word": "welcome",
            "offset": 400000,
            "duration": 7500000,
            "accuracyScore": 94.0,
            "pronunciationScore": 94.0,
            "errorType": "None"
          }
        ]
      },
      "questionInfo": {}
    }
  }
}
```

## 테스트

테스트는 실제 Identity, MongoDB Atlas, Redis, AWS S3 또는 Python AI 서버를 호출하지 않도록 구성한다.

```bash
./gradlew clean test
```
