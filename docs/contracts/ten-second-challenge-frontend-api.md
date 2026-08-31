# 10초 영작 챌린지 프론트엔드 API 명세

- 버전: v1
- 작성일: 2026-08-28
- 대상: 앱 프론트엔드
- 상태: 제품·프론트·백엔드·AI 계약 승인, 구현 전

> 이 문서의 API는 승인된 v1 계약이지만 아직 구현·배포되지 않았다. 실제 `challenge_10s_questions` 콘텐츠 구조, 첫 feature 활성화 KST 날짜의 자동 Day 1 고정, 비순환 dayNumber, difficulty 프론트 전달/AI 제외, 녹음 시작 attempt와 녹음 후 S3 upload-url 분리, attempt 제출 유효시간 1시간, 날짜 rollover, MEMBER 전용, AI 결과와 polling 계약을 반영한다.

## 1. 콘텐츠와 제출물

- KST(`Asia/Seoul`) 기준으로 하루에 3문제를 제공한다.
- 같은 KST 날짜에는 모든 사용자가 동일한 3문제를 푼다.
- 각 문제는 한국어 문장 `promptKo`를 제공한다.
- 사용자는 한국어 문장을 영어로 영작해 직접 발음한다.
- 실제 제출물은 텍스트가 아니라 영어 발화 녹음 audio 파일이다.
- 문제는 1→2→3 순서로만 진행한다. 앞 문제의 공개 상태가 `submitted`여야 다음 문제를 열 수 있다.
- 앱이 녹음 길이를 최대 10초로 제한한다. 10초 도달은 timeout 실패가 아니라 녹음 자동 종료와 정상 제출 시작이다. 서버는 audio가 10초인지 검증하지 않는다.
- 문제마다 최대 한 번만 응시할 수 있다. 정상 제출된 녹음은 한 번의 응시를 소비한다.
- 세 문제를 반드시 모두 풀 필요는 없다. 일부 문제만 참여한 날짜도 history에 보존하고 노출한다.
- 앱은 audio를 서버가 발급한 S3 Presigned PUT URL로 직접 업로드한다.
- 서버는 S3 audio를 확인한 뒤 비동기로 AI 피드백을 생성한다.
- 참고 영어 문장은 문제 조회 시 노출하지 않고 제출 접수 직후부터 노출한다. AI 개인화 피드백은 비동기로 나중에 채운다.

### 1.1 현재 콘텐츠 저장 구조와 프론트 매핑

Learning Core는 기존 MongoDB 클러스터 `to-teacher-app`의 `challenge_10s_questions` collection에서 콘텐츠를 읽는다. Mongo 연결 정보와 내부 문서 식별자는 프론트 계약이 아니다.

현재 document는 하나의 `dayNumber`와 세 개의 embedded question을 가진다.

| Mongo 필드 | 프론트 API 매핑 | 공개 시점 |
| --- | --- | --- |
| `_id` | 없음 | 비공개 |
| `dayNumber` | 없음. 서버가 `challengeDate`에 대응하는 콘텐츠를 선택 | 비공개 |
| `questions[].questionNumber` | `questionNumber` | 문제 조회부터 |
| `questions[].questionId` | 없음. 서버·AI·운영 추적용 stable ID | 비공개 |
| `questions[].korean` | `promptKo` | 문제 조회부터 |
| `questions[].referenceAnswer` | `referenceAnswer` | 제출 접수 또는 만료 terminal 이후 |
| `questions[].difficulty` | `difficulty` integer | 문제 조회와 terminal 결과 |

- 앱은 `_id`, `dayNumber`, `questionId`를 Request에 보내거나 날짜·문제 선택에 사용하지 않는다.
- `difficulty`는 scale을 해석하지 않은 Mongo 정수 원문이다. 앱은 표시 metadata로 사용할 수 있지만 Request에 다시 보내지 않는다.
- `difficulty`는 AI 요청에 포함되지 않는다.
- 앱은 서버가 반환한 `challengeDate`, `nextQuestionNumber`, `questionNumber`만 사용한다.
- `referenceAnswer`는 Mongo에 존재하더라도 문제 조회와 attempt·upload-url 응답에는 포함하지 않는다.
- 선택된 `dayNumber` 문서가 없거나 세 문제 구성이 유효하지 않으면 서버는 임의의 다른 날 문제를 섞지 않고 `404 CHALLENGE_CONTENT_NOT_FOUND`를 반환한다.
- `challengeDate → dayNumber` 선택은 서버 내부 정책이다. challenge feature를 처음 활성화해 성공 기동한 KST 날짜를 서버가 Mongo metadata에 한 번만 저장하고 그날을 dayNumber 1로 사용한다. 재배포해도 초기화하지 않고 dayNumber는 순환하지 않는다. 프론트 계산 대상이 아니다.

## 2. 공통 계약

### 2.1 Base URL과 인증

```text
{LEARNING_CORE_BASE_URL}/api/v1/challenges
```

모든 앱 API에는 Identity가 발급한 Access Token을 전달한다.

```http
Authorization: Bearer <ACCESS_TOKEN>
```

- `userId`는 Request Body, Path, Query에 보내지 않는다.
- 10초 챌린지는 `accountType=MEMBER`인 사용자만 이용한다. Guest Access Token은 `403 COMMON403`으로 거절한다.
- S3 Presigned URL로 업로드할 때는 `Authorization` 헤더를 보내지 않는다.
- 날짜 문자열은 KST 기준 `YYYY-MM-DD` 형식이다.
- 시각 문자열은 ISO 8601 UTC 형식이다. 예: `2026-08-24T03:20:10Z`.

### 2.2 공통 성공 응답

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "성공입니다.",
  "result": {}
}
```

### 2.3 공통 실패 응답

```json
{
  "isSuccess": false,
  "code": "CHALLENGE_ALREADY_ATTEMPTED",
  "message": "이미 응시가 완료된 문제입니다."
}
```

HTTP status와 `code`를 함께 확인한다. 사용자 분기에는 `message` 문자열 대신 `code`를 사용한다.

## 3. 상태값

### 3.1 하루 진행 상태 `dailyStatus`

| 값 | 의미 |
| --- | --- |
| `not_started` | 세 문제 모두 시작 전 |
| `in_progress` | 하나 이상 시작했지만 세 문제가 모두 terminal은 아님 |
| `completed` | 세 문제의 공개 `attemptStatus`가 모두 `submitted` |

`completed`는 세 문제를 모두 마친 상태를 표현할 뿐 필수 참여 조건이 아니다. 사용자는 1개 또는 2개 문제만 풀고 그날 참여를 끝낼 수 있다.

### 3.2 문제 응시 상태 `attemptStatus`

| 값 | 의미 |
| --- | --- |
| `not_started` | 결과 화면으로 이동할 수 없음 |
| `submitted` | 결과 화면으로 이동할 수 있음 |

- 프론트는 이 값만으로 챌린지 화면과 결과 화면 중 어디로 이동할지 결정한다.
- server 내부의 attempt 생성·업로드 중 상태는 공개 DTO에서 `not_started`로 projection한다.
- 정상 audio 제출, 무음 결과와 1시간 제출 만료는 공개 DTO에서 모두 `submitted`로 projection한다.
- `in_progress`, `expired` 같은 내부 상태를 공개 enum에 추가하지 않는다.

### 3.3 AI 처리 상태 `gradingStatus`

| 값 | 의미 |
| --- | --- |
| `not_requested` | 아직 제출하지 않았거나 만료된 문제 |
| `pending` | AI 작업 생성 완료 |
| `processing` | AI 처리 중 |
| `completed` | 결과 저장 완료 |
| `failed` | 자동 재시도 후에도 처리 실패 |

`dailyStatus=completed`와 AI 피드백 완료는 별개다. 세 문제 제출이 끝나도 일부 문제는 `gradingStatus=processing`일 수 있다.

## 4. 전체 호출 순서

```text
오늘 진행도 조회
→ 다음 문제 조회
→ 녹음 시작 attempt 생성
→ 앱에서 최대 10초 동안 영어 발화 녹음
→ attemptId로 Presigned PUT URL 발급
→ S3 Presigned URL에 audio PUT
→ 서버에 submitted 완료 통지
→ 결과 API polling
```

10초 녹음이 끝나면 timeout으로 넘기지 않고 다음 순서로 처리한다.

```text
녹음 자동 종료
→ 녹음된 audio를 S3에 PUT
→ 서버가 제출 접수와 함께 참고 답안 반환
→ 다음 문제 진행 가능
→ AI 피드백은 결과 API에서 비동기 갱신
```

## 5. API 목록

| 기능 | Method | Path |
| --- | --- | --- |
| 오늘 진행도 조회 | `GET` | `/api/v1/challenges/today` |
| 오늘의 개별 문제 조회 | `GET` | `/api/v1/challenges/today/questions/{questionNumber}` |
| 녹음 시작 attempt 생성 | `POST` | `/api/v1/challenges/today/questions/{questionNumber}/attempt` |
| attempt 업로드 URL 발급 | `POST` | `/api/v1/challenges/attempts/{attemptId}/upload-url` |
| audio 업로드 | `PUT` | 응답으로 받은 S3 Presigned URL |
| audio 제출 완료 통지 | `POST` | `/api/v1/challenges/today/questions/{questionNumber}/answer` |
| 월별 참여 이력 조회 | `GET` | `/api/v1/challenges/history?yearMonth={YYYY-MM}` |
| 특정 날짜 풀이 결과 조회 | `GET` | `/api/v1/challenges/{challengeDate}/results?questionNumber={optional}` |

## 6. API 상세

### 6.1 오늘 진행도 조회

```http
GET /api/v1/challenges/today
Authorization: Bearer <ACCESS_TOKEN>
```

응답 예시:

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "성공입니다.",
  "result": {
    "challengeDate": "2026-08-24",
    "challengeDateExpiresAt": "2026-08-24T15:00:00Z",
    "expiresInSeconds": 420,
    "dailyStatus": "in_progress",
    "totalQuestionCount": 3,
    "nextQuestionNumber": 2,
    "completedQuestionNumbers": [1],
    "questions": [
      {
        "questionNumber": 1,
        "attemptStatus": "submitted",
        "gradingStatus": "completed",
        "resultAvailable": true
      },
      {
        "questionNumber": 2,
        "attemptStatus": "not_started",
        "gradingStatus": "not_requested",
        "resultAvailable": false
      },
      {
        "questionNumber": 3,
        "attemptStatus": "not_started",
        "gradingStatus": "not_requested",
        "resultAvailable": false
      }
    ]
  }
}
```

- `completedQuestionNumbers`에는 공개 `attemptStatus=submitted`인 문제가 포함된다.
- `nextQuestionNumber`는 서버가 다음 진행 대상으로 판단한 번호다. 모두 완료되면 `null`이다.
- 앱은 문제 순서를 자체 계산하지 말고 `nextQuestionNumber`를 우선 사용한다.
- `challengeDateExpiresAt`은 현재 server KST 날짜가 끝나는 절대 시각이다.
- `expiresInSeconds`는 응답 생성 시점부터 `challengeDateExpiresAt`까지 남은 초를 server가 계산한 값이다.
- 앱은 기기 시계와 `challengeDateExpiresAt`을 직접 비교하지 않고 `expiresInSeconds`로 monotonic timer를 시작한다.
- timer가 끝나거나 앱이 background에서 foreground로 돌아오면 캐시를 버리고 이 API를 다시 조회한다.

### 6.2 오늘의 개별 문제 조회

```http
GET /api/v1/challenges/today/questions/{questionNumber}
Authorization: Bearer <ACCESS_TOKEN>
X-Challenge-Date: 2026-08-24
```

Path Parameter:

| 이름 | 타입 | 제약 |
| --- | --- | --- |
| `questionNumber` | integer | `1`~`3` |

응답 예시:

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "성공입니다.",
  "result": {
    "challengeDate": "2026-08-24",
    "questionNumber": 2,
    "totalQuestionCount": 3,
    "promptKo": "저는 보통 버스를 타고 출근해요.",
    "difficulty": 2,
    "attemptStatus": "not_started",
    "gradingStatus": "not_requested"
  }
}
```

- 조회 자체는 attempt를 만들거나 응시 횟수를 소비하지 않는다.
- 참고 영어 문장과 AI 피드백은 반환하지 않는다.
- 아직 순서가 오지 않은 문제를 조회하면 `409 CHALLENGE_PREVIOUS_QUESTION_INCOMPLETE`를 반환한다.
- `X-Challenge-Date`가 server의 현재 KST 날짜와 다르면 `409 CHALLENGE_DATE_CHANGED`를 반환한다.

### 6.3 녹음 시작 attempt 생성

```http
POST /api/v1/challenges/today/questions/{questionNumber}/attempt
Authorization: Bearer <ACCESS_TOKEN>
X-Challenge-Date: 2026-08-24
```

Request Body는 없다.

응답 예시:

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "성공입니다.",
  "result": {
    "attemptId": "f4b66a4e-67f8-4d9f-b68b-47bd6278a0d8",
    "challengeDate": "2026-08-24",
    "questionNumber": 2,
    "attemptStatus": "not_started",
    "submissionDeadlineAt": "2026-08-24T04:20:10Z"
  }
}
```

- 앱은 이 요청이 성공한 뒤에만 녹음을 시작한다.
- 내부 S3 object key는 attempt 생성 시 `attemptId`를 기반으로 서버가 결정·고정한다. 앱에 key를 노출하지 않는다.
- 같은 문제에서 네트워크 재시도로 다시 호출하면 새 attempt를 만들지 않는다.
- server 내부에 제출 전 attempt가 있다면 같은 `attemptId`와 동일한 `submissionDeadlineAt`을 반환한다. 공개 `attemptStatus`는 여전히 `not_started`다.
- 공개 `attemptStatus=submitted`이면 `409 CHALLENGE_ALREADY_ATTEMPTED`를 반환한다.
- 앞 문제가 terminal이 아니면 `409 CHALLENGE_PREVIOUS_QUESTION_INCOMPLETE`를 반환한다.
- `X-Challenge-Date`는 직전 오늘 진행도 응답의 `challengeDate`다. server 현재 날짜와 다르면 attempt를 만들지 않고 `409 CHALLENGE_DATE_CHANGED`를 반환한다.
- `submissionDeadlineAt`은 attempt 생성 시각부터 1시간이다. 자정을 지나더라도 이 시각까지는 attempt가 속한 원래 `challengeDate`의 제출로 처리한다.

### 6.4 attempt 업로드 URL 발급

녹음이 끝난 뒤 동일한 attempt에 연결된 S3 Presigned PUT URL을 발급한다.

```http
POST /api/v1/challenges/attempts/{attemptId}/upload-url
Authorization: Bearer <ACCESS_TOKEN>
```

Request Body는 없다.

응답 예시:

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "성공입니다.",
  "result": {
    "attemptId": "f4b66a4e-67f8-4d9f-b68b-47bd6278a0d8",
    "submissionDeadlineAt": "2026-08-24T04:20:10Z",
    "upload": {
      "method": "PUT",
      "url": "https://s3-presigned-url.example",
      "expiresAt": "2026-08-24T03:25:10Z",
      "contentType": "audio/mp4",
      "maxBytes": 2097152
    }
  }
}
```

- JWT 사용자가 attempt 소유자인지 확인한다. 다른 사용자의 attempt에는 `403`을 반환한다.
- attempt의 `challengeDate`와 question은 생성 시 값으로 고정하며 현재 KST 날짜로 다시 계산하지 않는다.
- `submissionDeadlineAt`이 지났거나 attempt가 terminal이면 새 URL을 발급하지 않는다.
- 같은 attempt의 URL 재발급은 새 응시로 계산하지 않고, 항상 attempt에 고정된 동일 S3 object key에 대해서만 발급한다.
- 녹음 파일은 `.m4a` 확장자의 M4A 컨테이너와 AAC 코덱을 사용하며 업로드 `Content-Type`은 `audio/mp4`로 고정한다.
- 서버가 생성하는 S3 object key도 `.m4a` 확장자를 사용한다.
- `maxBytes`는 서버 응답을 따르며 예시의 2 MiB는 최대 파일 크기 확정 전까지 임시값이다.
- Presigned URL은 짧게 발급하고 만료 시 같은 attemptId로 재발급한다. 예시의 URL은 5분 유효하지만 attempt 제출 유효시간은 1시간이다.
- URL 자체의 `expiresAt`은 `submissionDeadlineAt`을 넘을 수 없다.

### 6.5 S3에 audio 업로드

이 요청은 Learning Core가 아니라 응답으로 받은 S3 Presigned URL로 보낸다.

```http
PUT <upload.url>
Content-Type: <upload.contentType>

<raw audio binary>
```

- `multipart/form-data`가 아니라 audio 파일의 raw binary를 Body로 전송한다.
- `Authorization: Bearer ...`를 추가하지 않는다.
- `Content-Type`은 upload-url 응답의 `upload.contentType`과 정확히 일치시킨다.
- 성공 status는 S3 설정에 따라 `200` 또는 `204`일 수 있다.
- 업로드 실패 시 URL 만료 전 같은 URL로 재시도할 수 있다.
- URL이 만료됐으면 upload-url API를 다시 호출해 같은 attempt와 S3 object key의 새 URL을 받는다.
- S3 응답 성공 후에만 audio 제출 완료 API를 호출한다.

### 6.6 audio 제출 완료 통지

```http
POST /api/v1/challenges/today/questions/{questionNumber}/answer
Authorization: Bearer <ACCESS_TOKEN>
Idempotency-Key: <UUID>
Content-Type: application/json
```

audio 제출 Request:

```json
{
  "attemptId": "f4b66a4e-67f8-4d9f-b68b-47bd6278a0d8"
}
```

응답 예시:

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "성공입니다.",
  "result": {
    "attemptId": "f4b66a4e-67f8-4d9f-b68b-47bd6278a0d8",
    "challengeDate": "2026-08-24",
    "questionNumber": 2,
    "difficulty": 2,
    "attemptStatus": "submitted",
    "gradingStatus": "pending",
    "acceptedAt": "2026-08-24T03:21:02Z",
    "referenceAnswer": "I usually take the bus to work.",
    "feedbackAvailable": false
  }
}
```

- 앱이 timer duration이나 client 시각을 보내지 않는다.
- 서버가 attempt에 연결된 정확한 S3 object의 존재·형식·크기를 확인한 뒤 `submitted`로 전환한다.
- 녹음이 10초에 도달해 자동 종료된 경우에도 이 정상 제출 API를 사용한다.
- 제출 접수 응답에서 `referenceAnswer`를 즉시 반환하므로 AI 처리 완료를 기다리지 않고 학습 결과와 다음 문제를 볼 수 있다.
- 녹음에 발화가 없더라도 audio를 제출한다. 발화가 감지되지 않으면 `transcript=null`과 발화 없음 안내 문구를 제공하며 별도의 공개 `feedbackType` enum은 추가하지 않는다.
- 같은 요청을 재전송할 때는 같은 `Idempotency-Key`를 사용한다.
- 같은 key와 같은 Body는 기존 성공 결과를 반환한다.
- 같은 key로 다른 Body를 보내면 `409 CHALLENGE_IDEMPOTENCY_CONFLICT`다.
- AI 실패나 polling timeout 때문에 새 사용자 attempt를 만들지 않는다.

### 6.7 월별 참여 이력 조회

```http
GET /api/v1/challenges/history?yearMonth=2026-08
Authorization: Bearer <ACCESS_TOKEN>
```

Query Parameter:

| 이름 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `yearMonth` | 아니요 | 현재 KST 월 | `YYYY-MM` |

응답 예시:

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "성공입니다.",
  "result": {
    "yearMonth": "2026-08",
    "dates": [
      {
        "challengeDate": "2026-08-21",
        "participated": false,
        "solvedQuestionCount": 0
      },
      {
        "challengeDate": "2026-08-22",
        "participated": true,
        "solvedQuestionCount": 1
      },
      {
        "challengeDate": "2026-08-23",
        "participated": true,
        "solvedQuestionCount": 3
      }
    ]
  }
}
```

- 요청한 월의 날짜별 참여 여부와 실제로 푼 문제 수만 반환한다.
- `participated=true`는 해당 날짜에 공개 `attemptStatus=submitted`인 문제가 하나 이상 있다는 뜻이다.
- `solvedQuestionCount`는 공개 `attemptStatus=submitted`인 문제 수이며 `0`~`3`이다.
- 정상 audio 제출, 무음과 1시간 만료 terminal을 프론트 상태에서 구분하지 않으므로 모두 풀이 수에 포함한다.
- attempt만 만들고 아직 terminal이 아닌 공개 `not_started` 문제는 풀이 수에 포함하지 않는다.
- 과거 월은 월 전체 날짜를 반환한다. 현재 월은 KST 오늘까지 반환하고 미래 날짜는 포함하지 않는다.
- 미래 `yearMonth` 요청은 `400 COMMON400`으로 거절한다.
- 월 단위 최대 31건이므로 cursor와 pagination은 사용하지 않는다.

### 6.8 특정 날짜 풀이 결과 조회

```http
GET /api/v1/challenges/{challengeDate}/results
GET /api/v1/challenges/{challengeDate}/results?questionNumber=2
Authorization: Bearer <ACCESS_TOKEN>
```

Parameter:

| 위치 | 이름 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| Path | `challengeDate` | 예 | KST `YYYY-MM-DD` | 조회 날짜 |
| Query | `questionNumber` | 아니요 | `1`~`3` | 없으면 풀이 수만, 있으면 해당 문제 상세 |

`questionNumber`가 없는 응답은 풀이 개수만 반환한다.

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "성공입니다.",
  "result": {
    "challengeDate": "2026-08-24",
    "solvedQuestionCount": 2
  }
}
```

`questionNumber=2`가 있는 응답은 날짜 전체 풀이 수와 해당 문제 상세만 반환한다.

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "성공입니다.",
  "result": {
    "challengeDate": "2026-08-24",
    "solvedQuestionCount": 2,
    "question": {
      "questionNumber": 2,
      "promptKo": "저는 보통 버스를 타고 출근해요.",
      "difficulty": 2,
      "attemptStatus": "submitted",
      "gradingStatus": "processing",
      "submittedAt": "2026-08-24T03:21:02Z",
      "gradedAt": null,
      "referenceAnswer": "I usually take the bus to work.",
      "aiResult": null
    }
  }
}
```

AI 분석이 정상 완료된 응답:

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "성공입니다.",
  "result": {
    "challengeDate": "2026-08-24",
    "solvedQuestionCount": 2,
    "question": {
      "questionNumber": 2,
      "promptKo": "저는 보통 버스를 타고 출근해요.",
      "difficulty": 2,
      "attemptStatus": "submitted",
      "gradingStatus": "completed",
      "submittedAt": "2026-08-24T03:21:02Z",
      "gradedAt": "2026-08-24T03:21:25Z",
      "referenceAnswer": "I usually take the bus to work.",
      "aiResult": {
        "referenceAnswer": "I usually take the bus to work.",
        "transcript": "I normally take a bus to work.",
        "verdict": "correct",
        "correctedAnswer": null,
        "feedback": {
          "meaning": "문장의 핵심 의미를 정확하게 전달했어요.",
          "grammar": "문법적으로 자연스러운 문장이에요.",
          "pronunciation": "normally의 첫 음절을 조금 더 분명하게 발음해 보세요."
        }
      }
    }
  }
}
```

발화가 감지되지 않은 `no_speech` 응답:

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "성공입니다.",
  "result": {
    "challengeDate": "2026-08-24",
    "solvedQuestionCount": 2,
    "question": {
      "questionNumber": 2,
      "promptKo": "저는 보통 버스를 타고 출근해요.",
      "difficulty": 2,
      "attemptStatus": "submitted",
      "gradingStatus": "completed",
      "submittedAt": "2026-08-24T03:21:02Z",
      "gradedAt": "2026-08-24T03:21:25Z",
      "referenceAnswer": "I usually take the bus to work.",
      "aiResult": {
        "referenceAnswer": "I usually take the bus to work.",
        "transcript": null,
        "verdict": null,
        "correctedAnswer": null,
        "feedback": null
      }
    }
  }
}
```

- `aiResult.referenceAnswer`는 AI Callback 값이 아니다. Learning Core가 attempt 생성 시 Mongo `questions[].referenceAnswer`에서 snapshot한 사전 정의 답안을 결과 DTO에 조립한다.
- 정상 완료와 `no_speech` 모두 `aiResult.referenceAnswer`를 non-blank로 반환한다.
- `no_speech`는 `gradingStatus=completed`이며 `aiResult` 자체는 null이 아니다. `referenceAnswer`만 유지하고 AI 생성 하위 필드는 null이다.
- `question.referenceAnswer`는 제출 접수 직후부터 사용할 수 있는 기존 필드이고, `aiResult.referenceAnswer`는 완료 결과 component를 독립적으로 렌더링할 수 있도록 같은 snapshot 값을 포함한다.
- 두 `referenceAnswer` 값이 다르면 서버 데이터 정합성 오류이며 attempt snapshot 값을 authoritative 값으로 사용한다.
- `verdict`는 `correct | needs_improvement`다. `needs_improvement`에서는 `correctedAnswer`가 non-blank이며 `correct`에서는 null일 수 있다.

`aiResult` 필드:

| 필드 | 형식 | null 규칙 | 출처 |
| --- | --- | --- | --- |
| `referenceAnswer` | string | 항상 non-blank | Learning Core attempt의 DB 콘텐츠 snapshot |
| `transcript` | string/null | `no_speech`에서 null | AI Callback |
| `verdict` | `correct`, `needs_improvement` 또는 null | `no_speech`에서 null | AI Callback |
| `correctedAnswer` | string/null | correct·no-speech에서 null 가능 | AI Callback |
| `feedback` | object/null | `no_speech`에서 null | AI Callback |
| `feedback.meaning` | string | completed feedback에서 non-blank | AI Callback |
| `feedback.grammar` | string | completed feedback에서 non-blank | AI Callback |
| `feedback.pronunciation` | string | completed feedback에서 non-blank | AI Callback |

서버는 transcript·correctedAnswer를 각각 최대 1,000자, feedback 각 문장을 최대 500자로 보장한다.

자동 재시도까지 모두 실패한 경우에도 같은 결과 조회 API가 HTTP 200을 반환한다.

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "성공입니다.",
  "result": {
    "challengeDate": "2026-08-24",
    "solvedQuestionCount": 2,
    "question": {
      "questionNumber": 2,
      "promptKo": "저는 보통 버스를 타고 출근해요.",
      "difficulty": 2,
      "attemptStatus": "submitted",
      "gradingStatus": "failed",
      "submittedAt": "2026-08-24T03:21:02Z",
      "gradedAt": null,
      "referenceAnswer": "I usually take the bus to work.",
      "aiResult": null
    }
  }
}
```

- `questionNumber`가 없으면 `question` 또는 `questions` 필드를 반환하지 않는다.
- `questionNumber`가 있으면 해당 문제 상세를 단일 `question` 객체로 반환한다.
- 해당 날짜에 풀이가 없으면 `solvedQuestionCount=0`이다.
- 날짜에는 풀이가 있지만 지정한 문제를 풀지 않았다면 날짜 전체 `solvedQuestionCount`는 유지하고 `question=null`을 반환한다.
- AI 처리 중에도 HTTP 200과 `gradingStatus=pending|processing`, `aiResult=null`을 반환한다.
- 공개 `attemptStatus=submitted`인 문제는 AI Callback 도착 여부와 관계없이 항상 결과 조회가 가능해야 한다. 이 경우 `404 CHALLENGE_ATTEMPT_NOT_FOUND`가 발생하면 정상 대기 상태가 아니라 서버 데이터 정합성 오류다.
- AI 결과가 아직 없을 때도 prompt, 제출 시각과 참고 답안은 유지하고 AI에 의존하는 필드만 `null`로 반환한다.
- `gradingStatus=failed`는 결과 조회 요청 자체의 실패가 아니므로 root `isSuccess=true`, `code=COMMON_200`을 유지한다. 프론트는 polling을 멈추고 참고 답안과 `피드백을 생성하지 못했어요` 안내를 표시한다.
- 내부 예외명, AI 응답 원문, 재시도 횟수와 `failureReason`은 공개 DTO에 넣지 않는다. 운영 진단용 Job과 로그에서만 관리한다.
- 발화가 감지되지 않으면 `gradingStatus=completed`, `aiResult.referenceAnswer`는 non-blank, 나머지 AI 생성 field는 null이다. 프론트는 이 조합에 고정 발화 없음 안내를 표시하고 별도 `feedbackType` enum은 사용하지 않는다.
- 1시간 만료처럼 개인화 피드백이 없는 terminal도 공개 상태상 푼 문제로 계산하며 문제 번호를 지정하면 참고 답안과 nullable AI 결과를 반환한다.

## 7. 오류 계약

| HTTP | code | 프론트 처리 |
| --- | --- | --- |
| `400` | `COMMON400` | 요청 형식·날짜·문항 번호 확인 |
| `401` | `COMMON401` | Access Token 갱신 또는 로그인 |
| `403` | `COMMON403` | MEMBER 권한 또는 사용자 상태 확인 |
| `404` | `CHALLENGE_CONTENT_NOT_FOUND` | 해당 날짜 콘텐츠 준비 중 화면 |
| `404` | `CHALLENGE_ATTEMPT_NOT_FOUND` | 오늘 진행도 재조회 |
| `409` | `CHALLENGE_ALREADY_ATTEMPTED` | 현재 상태·결과 화면으로 이동 |
| `409` | `CHALLENGE_PREVIOUS_QUESTION_INCOMPLETE` | `nextQuestionNumber` 문제로 이동 |
| `409` | `CHALLENGE_IDEMPOTENCY_CONFLICT` | 새 요청을 만들지 말고 상태 재조회 |
| `409` | `CHALLENGE_AUDIO_NOT_UPLOADED` | S3 업로드 성공 여부 확인 후 같은 key로 재시도 |
| `409` | `CHALLENGE_DATE_CLOSED` | 오늘 진행도를 다시 조회 |
| `409` | `CHALLENGE_DATE_CHANGED` | 캐시된 날짜·문항 상태를 버리고 오늘 진행도 재조회 |
| `410` | `CHALLENGE_ATTEMPT_EXPIRED` | 이전 날짜 제출을 중단하고 오늘 진행도 조회 |
| `413` | `CHALLENGE_AUDIO_TOO_LARGE` | 녹음 파일 크기 오류 안내 |
| `415` | `CHALLENGE_AUDIO_FORMAT_UNSUPPORTED` | 서버가 지정한 audio 형식으로 다시 인코딩 |
| `500` | `COMMON500` | 재시도 안내 및 오류 화면 |

아래 Challenge 전용 code 이름과 HTTP mapping은 v1 계약이다.

날짜 rollover 오류는 최신 server 날짜 정보를 함께 반환한다.

```json
{
  "isSuccess": false,
  "code": "CHALLENGE_DATE_CHANGED",
  "message": "챌린지 날짜가 변경되었습니다.",
  "result": {
    "challengeDate": "2026-08-25",
    "challengeDateExpiresAt": "2026-08-25T15:00:00Z",
    "expiresInSeconds": 86400
  }
}
```

## 8. 프론트 재시도와 로컬 보관 규칙

- attempt 생성 응답의 `attemptId`, `challengeDate`, `questionNumber`, `submissionDeadlineAt`을 제출 완료까지 로컬에 보관한다.
- answer 요청의 `Idempotency-Key`도 성공 응답을 받을 때까지 같은 값으로 보관한다.
- 앱 재실행 후에는 오늘 진행도부터 다시 조회한다.
- 오늘 진행도 응답의 `expiresInSeconds` timer가 끝나거나 앱이 foreground로 돌아오면 오늘 진행도를 다시 조회한다.
- question·attempt 요청에는 직전 응답의 `challengeDate`를 `X-Challenge-Date`로 보내며, `CHALLENGE_DATE_CHANGED`이면 사용자에게 오류를 노출하지 않고 새 날짜 진행도를 재조회한다.
- client timer는 UX 최적화일 뿐 최종 안전장치가 아니다. server는 question·attempt 처리 직전에 현재 KST 날짜와 `X-Challenge-Date`를 반드시 비교한다.
- 공개 `attemptStatus=not_started`여도 server 내부에 제출 전 attempt가 있을 수 있다. attempt API를 재호출하면 새 attempt를 만들지 않고 기존 attempt를 복구한다.
- 녹음 완료 후 upload-url API를 호출하고, URL 만료 시 같은 attemptId로 새 URL을 받는다.
- S3 PUT 성공 여부가 불확실하면 같은 URL로 재시도하고, 만료됐으면 새 Presigned URL을 받는다.
- `gradingStatus=pending|processing`이면 상세 결과 API를 foreground에서 최대 60초 polling한다. 처음 20초는 2초 간격, 이후에는 5초 간격으로 조회한다.
- 프론트 polling이 끝나거나 앱을 닫아도 제출과 서버 채점 Job은 취소되지 않는다. 무한 로딩으로 화면을 막지 말고 `피드백 준비 중` 상태에서 다음 문제·목록 이동을 허용하며, 결과 화면 재진입 또는 foreground 복귀 시 다시 조회한다.
- `gradingStatus=failed`는 사용자 재응시 사유가 아니다. 서버 자동 복구 또는 운영 복구 대상으로 표시한다.
- AI가 실패해도 제출 접수 시 받은 참고 답안은 유지하고 UI에는 `피드백 준비 중` 또는 `피드백 생성 실패`를 표시한다. 사용자 답안을 사라진 것처럼 처리하지 않는다.
- 60초가 지나도 `pending|processing`이면 foreground polling을 중단하고 `피드백 준비 중`을 표시한다. 서버 Job은 취소하지 않으며 결과 화면 재진입·foreground 복귀 시 다시 조회한다.
- 서버의 Callback deadline은 120초이고 최대 AI generation은 3회다.
- MVP에서는 사용자 녹음 재생과 `audioUrl`을 제공하지 않는다. S3 audio는 AI 처리용 내부 artifact이며 프론트 결과 DTO에 URL을 노출하지 않는다.

### 자정 직전 attempt 처리

- attempt는 생성 당시의 `challengeDate`에 고정한다.
- attempt 생성 시 서버가 `submissionDeadlineAt=attemptCreatedAt+1시간`을 반환한다.
- 23:59:50 KST에 생성한 attempt는 00:59:50 KST까지 이전 날짜 문제로 제출할 수 있다.
- 22:50 KST에 생성한 attempt는 23:50 KST에 만료되므로 자정까지 무기한 유지되지 않는다.
- 자정 이후에는 이전 날짜의 새 attempt를 생성할 수 없다.
- answer 처리 시 서버는 현재 날짜가 아니라 `attemptId`에 저장된 `challengeDate`를 사용한다.
- deadline이 지나면 `410 CHALLENGE_ATTEMPT_EXPIRED`를 반환하고 내부 attempt를 만료 terminal로 처리한다. 이후 공개 `attemptStatus=submitted`로 projection해 다음 문제와 결과 화면을 열고 참고 답안만 제공한다.
- 이 1시간은 10초 녹음 검증 시간이 아니라 S3 업로드·네트워크 재시도·앱 종료·응답 유실을 수습하는 제출 유효시간이다.

## 9. v1 확정 사항

녹음 파일은 `.m4a` M4A 컨테이너·AAC-LC 코덱·`Content-Type: audio/mp4`, 16/44.1/48 kHz, mono/stereo, 최대 2 MiB로 확정됐다. 녹음 길이 최대 10초, MEMBER 전용, 전 사용자 공통 3문제, 1→2→3 순차 진행, 일부 참여 날짜를 포함한 history와 attempt 생성 시점부터 1시간의 제출 유효시간도 확정됐다.

`timed_out`은 공개 계약에서 제거하고 10초 녹음 종료를 정상 `submitted`로 처리한다. 공개 `attemptStatus`는 `not_started|submitted`만 사용한다. 처리 중·최종 실패는 `aiResult=null`, no-speech 완료는 사전 정의 답안을 가진 non-null `aiResult`로 표현한다. foreground polling 상한은 60초이며 사용자 audio 재생은 MVP에서 제공하지 않는다.
