# 앱 프론트엔드 API 인계서

- 기준일: 2026-08-28
- 대상: 앱 프론트엔드
- 범위: Identity, Learning Core, 1차 업데이트 예정 기능
- 기준: 현재 서버 컨트롤러·DTO·Security 설정

> 이 문서는 프론트가 실제 호출할 API를 한곳에서 찾기 위한 인계 문서다. `✅ 구현됨`만 현재 서버에 연결할 수 있다. `🟡 연동 전`은 계약이 승인됐더라도 앱용 endpoint 구현·배포가 끝나지 않은 상태이므로 production 코드에서 호출하지 않는다. `⛔ 내부`는 서버 간 또는 AI 전용이며 앱에서 호출하지 않는다.

## 1. 한눈에 보기

| 영역 | 상태 | 프론트 호출 수 | 비고 |
| --- | --- | ---: | --- |
| Identity LOCAL·Guest 인증 | ✅ 구현됨 | 7 | 로그인 전 공개 API와 인증 API 혼재 |
| Identity Firebase·SNS 인증 | ✅ 구현됨 | 6 | Google·Kakao·Apple은 Firebase ID Token을 Identity Token으로 교환 |
| 사용자 프로필·동의·탈퇴 | ✅ 구현됨 | 4 | 모두 Bearer 인증 필요 |
| 모의고사·채점 | ✅ 구현됨 | 11 | staging/prod는 Bearer 인증 필요 |
| 무료 모의고사 1회·결제 권한 | 🟡 연동 전 | 0 | Billing 내부 reserve/lifecycle 구현, Learning Core saga 미구현 |
| 10초 영작 챌린지 | 🟡 v1 계약 승인 | 7 | 콘텐츠 collection 존재, 전체 API 미구현 |
| AI callback·서버 간 event | ⛔ 내부 | 9 | 프론트 호출 금지 |

## 2. 공통 규칙

### 2.1 서비스 Base URL

환경별 host는 배포 설정에서 주입한다.

```text
IDENTITY_BASE_URL=https://<identity-host>
LEARNING_CORE_BASE_URL=https://<learning-core-host>
```

Identity와 Learning Core는 서로 다른 서비스다. 상대 서비스의 path를 다른 host에 호출하지 않는다.

### 2.2 인증

인증 필수 API:

```http
Authorization: Bearer <IDENTITY_ACCESS_TOKEN>
```

- Access Token은 Identity가 발급한 RS256 JWT다.
- Refresh Token은 API Body로만 전달하고 Bearer Token으로 사용하지 않는다.
- 프론트는 `userId`를 Path, Query, Request Body에 추가하지 않는다. 서버가 Access Token의 `sub`를 사용한다.
- Learning Core의 local/test `legacy` 모드는 개발 호환을 위해 인증 없이 동작할 수 있지만 staging/prod 연동 기준은 JWT다.
- S3 Presigned URL에 파일을 PUT할 때는 Identity Authorization 헤더를 보내지 않는다.

### 2.3 공통 응답

두 서비스 모두 다음 최상위 구조를 사용한다.

```json
{
  "isSuccess": true,
  "code": "SUCCESS",
  "message": "요청에 성공했습니다.",
  "result": {}
}
```

서비스별 성공 code는 다르다.

| 서비스 | 일반 성공 code | 일반 성공 message |
| --- | --- | --- |
| Identity | `SUCCESS` | `요청에 성공했습니다.` |
| Learning Core | `COMMON_200` | `성공입니다.` |

실패 처리 규칙:

- HTTP status와 `code`를 함께 확인한다.
- 화면 분기는 변경 가능한 `message`가 아니라 안정적인 `code`를 사용한다.
- `401`은 Access Token 재발급 시도 후 원 요청을 한 번만 재시도한다.
- 재발급도 실패하면 저장한 Access/Refresh Token을 지우고 로그인 화면으로 이동한다.
- Identity는 실패 시 `result: null`이 포함될 수 있다. Learning Core는 null `result`가 JSON에서 생략될 수 있다.

### 2.4 시간·enum·nullable

- 토큰의 `*ExpiresIn`은 밀리초다.
- API 시각은 ISO 8601 문자열이다.
- enum 문자열은 대소문자를 그대로 사용한다.
- `null` 가능 필드는 임의의 빈 문자열이나 0으로 치환하지 않는다.

## 3. 전체 구현 API 목록

### 3.1 Identity

| 상태 | 인증 | Method | Path | 용도 |
| --- | --- | --- | --- | --- |
| ✅ | 공개 | `POST` | `/api/v1/auth/check-email` | LOCAL 이메일 중복 확인 |
| ✅ | 공개 | `POST` | `/api/v1/auth/signup` | LOCAL 회원가입 |
| ✅ | 공개 | `POST` | `/api/v1/auth/guest` | Guest 생성 및 토큰 발급 |
| ✅ | 공개 | `POST` | `/api/v1/auth/login` | LOCAL 로그인 |
| ✅ | 공개 | `POST` | `/api/v1/auth/reissue` | Access/Refresh Token rotation |
| ✅ | 공개 | `POST` | `/api/v1/auth/logout` | RefreshSession 단건 폐기 |
| ✅ | Bearer | `POST` | `/api/v1/auth/logout-all` | 현재 사용자의 전체 RefreshSession 폐기 |
| ✅ | 공개 | `POST` | `/api/v1/auth/firebase/exchange` | Firebase ID Token 교환 |
| ✅ | 공개 | `POST` | `/api/v1/auth/firebase/signup` | Firebase 신규 MEMBER 가입 완료 |
| ✅ | Bearer Guest | `POST` | `/api/v1/auth/firebase/guest/prepare` | Guest 승격/병합 분기 확인 |
| ✅ | Bearer Guest | `POST` | `/api/v1/auth/firebase/guest/upgrade` | Guest UUID를 유지해 MEMBER 승격 |
| ✅ | Bearer Guest | `POST` | `/api/v1/auth/firebase/guest/merge` | Guest를 기존 MEMBER로 통합 |
| ✅ | Bearer MEMBER | `POST` | `/api/v1/auth/firebase/auth-methods/sync` | 연결된 SNS 인증수단 동기화 |
| ✅ | Bearer | `GET` | `/api/v1/users/me` | 내 프로필 조회 |
| ✅ | Bearer | `GET` | `/api/v1/users/me/consents` | 현재 정책 버전과 동의 상태 조회 |
| ✅ | Bearer | `PUT` | `/api/v1/users/me/consents` | 정책 동의 갱신/철회 |
| ✅ | Bearer | `POST` | `/api/v1/users/withdraw` | 회원 탈퇴 |

### 3.2 Learning Core

| 상태 | 인증 | Method | Path | 용도 |
| --- | --- | --- | --- | --- |
| ✅ | Bearer | `POST` | `/api/v1/exams` | 모의고사 세션 생성 |
| ✅ | Bearer | `GET` | `/api/v1/exams/history` | 완료 시험 이력 조회 |
| ✅ | Bearer | `GET` | `/api/v1/exams/{examId}/retries` | 재답변 문항과 회차 조회 |
| ✅ | Bearer | `GET` | `/api/v1/exams/{examId}/questions/{questionNumber}/prompt` | 문항 문제 조회 |
| ✅ | Bearer | `GET` | `/api/v1/exams/{examId}/questions/{questionNumber}/upload-url` | S3 업로드 URL 발급 |
| ✅ | Bearer | `POST` | `/api/v1/exams/{examId}/questions/{questionNumber}/submit` | 업로드 완료 및 채점 시작 |
| ✅ | Bearer | `POST` | `/api/v1/exams/{examId}/grading/retry` | 실패·지연된 최초 응시 채점 복구 |
| ✅ | Bearer | `GET` | `/api/v1/exams/{examId}/status` | 시험 전체 채점 상태 polling |
| ✅ | Bearer | `GET` | `/api/v1/exams/{examId}/summary` | 종합 점수·피드백 조회 |
| ✅ | Bearer | `GET` | `/api/v1/exams/{examId}/questions` | 문항·회차별 상세 결과 조회 |
| ✅ | Bearer | `GET` | `/api/v1/exams/{examId}/questions/status` | 문항·회차별 채점 상태 polling |

모든 `{examId}` API는 로그인 사용자의 시험 소유권을 검증한다.

## 4. Identity 상세

### 4.1 LOCAL 회원가입·로그인

#### 이메일 중복 확인

```http
POST /api/v1/auth/check-email
Content-Type: application/json

{"email":"user@example.com"}
```

```json
{
  "isSuccess": true,
  "code": "SUCCESS",
  "message": "요청에 성공했습니다.",
  "result": {
    "isAvailable": true,
    "message": "사용 가능한 이메일입니다."
  }
}
```

#### LOCAL 회원가입

```http
POST /api/v1/auth/signup
Content-Type: application/json
```

```json
{
  "email": "user@example.com",
  "password": "example-password",
  "nickname": "토스마스터",
  "isPrivacyConsented": true,
  "privacyConsentVersion": "privacy-v1",
  "isTermConsented": true,
  "termConsentVersion": "term-v1"
}
```

- 비밀번호는 8~64자, 닉네임은 2~20자다.
- 성공 `result`는 `userId`, `email`, `nickname`, privacy/term 동의 상태·버전·시각과 `createdAt`을 반환한다.
- 이 API는 Token을 반환하지 않는다. 성공 후 로그인 API를 호출한다.

#### LOCAL 로그인

```http
POST /api/v1/auth/login
Content-Type: application/json

{"email":"user@example.com","password":"example-password"}
```

```json
{
  "isSuccess": true,
  "code": "SUCCESS",
  "message": "요청에 성공했습니다.",
  "result": {
    "accessToken": "<ACCESS_TOKEN>",
    "refreshToken": "<REFRESH_TOKEN>",
    "grantType": "Bearer",
    "accessTokenExpiresIn": 1800000
  }
}
```

### 4.2 Guest

```http
POST /api/v1/auth/guest
Content-Type: application/json
```

```json
{
  "installationId": "550e8400-e29b-41d4-a716-446655440000",
  "isPrivacyConsented": true,
  "privacyConsentVersion": "privacy-v1",
  "isTermConsented": true,
  "termConsentVersion": "term-v1",
  "isQualityReviewConsented": false,
  "qualityReviewConsentVersion": null
}
```

- `installationId`는 앱이 생성한 UUID v4다. 인증 자격증명이 아니라 Guest 중복 생성 방지용이다.
- 같은 설치 ID로 이미 Guest가 생성됐으면 `409 GUEST_ALREADY_EXISTS`다.
- 설치 ID만으로 기존 Guest Token을 복구할 수 없다.
- 성공 result는 `accessToken`, `refreshToken`, `grantType`, `accessTokenExpiresIn`, `refreshTokenExpiresIn`이다.

### 4.3 Firebase·SNS 로그인

지원 Social Provider enum은 `GOOGLE`, `KAKAO`, `APPLE`이다. 앱이 각 Provider 로그인 후 Firebase ID Token을 얻고 Identity에 전달한다.

#### 기존 회원 로그인 또는 신규 가입 분기

```http
POST /api/v1/auth/firebase/exchange
Content-Type: application/json

{"firebaseIdToken":"<FIREBASE_ID_TOKEN>"}
```

기존 MEMBER:

```json
{
  "isSuccess": true,
  "code": "SUCCESS",
  "message": "요청에 성공했습니다.",
  "result": {
    "type": "AUTHENTICATED",
    "accessToken": "<ACCESS_TOKEN>",
    "refreshToken": "<REFRESH_TOKEN>",
    "grantType": "Bearer",
    "accessTokenExpiresIn": 1800000,
    "refreshTokenExpiresIn": 1209600000
  }
}
```

신규 가입 필요:

```json
{
  "isSuccess": true,
  "code": "SUCCESS",
  "message": "요청에 성공했습니다.",
  "result": {
    "type": "ENROLLMENT_REQUIRED",
    "enrollmentId": "550e8400-e29b-41d4-a716-446655440000",
    "missingRequirements": ["PHONE_VERIFICATION", "PROFILE", "CONSENTS"],
    "expiresIn": 600000
  }
}
```

`missingRequirements` 후보는 `EMAIL_VERIFICATION`, `PHONE_VERIFICATION`, `PROFILE`, `CONSENTS`다.

#### 신규 Firebase MEMBER 가입 완료

```http
POST /api/v1/auth/firebase/signup
Content-Type: application/json
```

```json
{
  "enrollmentId": "550e8400-e29b-41d4-a716-446655440000",
  "firebaseIdToken": "<FRESH_FIREBASE_ID_TOKEN>",
  "nickname": "토스마스터",
  "isPrivacyConsented": true,
  "privacyConsentVersion": "privacy-v1",
  "isTermConsented": true,
  "termConsentVersion": "term-v1"
}
```

- phone link 등 요구사항을 완료한 뒤 강제 갱신한 fresh Firebase ID Token을 보낸다.
- 성공 result는 Access/Refresh Token과 각 만료 시간이다.

#### Guest 승격/병합

1. Guest Access Token으로 준비 요청을 한다.

```http
POST /api/v1/auth/firebase/guest/prepare
Authorization: Bearer <GUEST_ACCESS_TOKEN>
Content-Type: application/json

{"firebaseIdToken":"<FRESH_FIREBASE_ID_TOKEN>"}
```

응답 `type` 분기:

| type | 프론트 처리 |
| --- | --- |
| `ENROLLMENT_REQUIRED` | `enrollmentId`, `expiresIn`을 저장하고 `/guest/upgrade` 진행 |
| `ALREADY_LINKED` | 이미 같은 계정에 연결됨. 프로필 재조회 |
| `MERGE_REQUIRED` | 기존 MEMBER 소유 인증수단임. 사용자 확인 후 `/guest/merge` 진행 |

2. 신규 MEMBER로 승격할 때는 `/api/v1/auth/firebase/guest/upgrade`에 Firebase signup과 같은 필드를 보낸다.
3. 기존 MEMBER로 병합할 때는 `/api/v1/auth/firebase/guest/merge`에 `{"firebaseIdToken":"..."}`를 보낸다.
4. 두 성공 응답 모두 target MEMBER의 새 Access/Refresh Token을 반환한다. 기존 Guest Token을 즉시 교체한다.

#### 인증수단 동기화

```http
POST /api/v1/auth/firebase/auth-methods/sync
Authorization: Bearer <MEMBER_ACCESS_TOKEN>
Content-Type: application/json

{"firebaseIdToken":"<FRESH_FIREBASE_ID_TOKEN>"}
```

성공 result: `{"linkedProviders":["GOOGLE","APPLE"]}`

### 4.4 Token 재발급·로그아웃

#### 재발급

```http
POST /api/v1/auth/reissue
Content-Type: application/json

{"refreshToken":"<REFRESH_TOKEN>"}
```

- Refresh Token rotation이므로 성공 시 Access Token과 Refresh Token을 모두 원자적으로 교체한다.
- 성공 result에는 `grantType`, `accessTokenExpiresIn`, `refreshTokenExpiresIn`도 포함된다.
- `INVALID_REFRESH_TOKEN`, `REFRESH_TOKEN_EXPIRED`, `REFRESH_TOKEN_REUSE_DETECTED`, `ACCOUNT_WITHDRAWN`이면 재로그인한다.

#### 단일 로그아웃

```http
POST /api/v1/auth/logout
Content-Type: application/json

{"refreshToken":"<REFRESH_TOKEN>"}
```

성공 result는 `null`이다. 요청 성공 여부와 관계없이 사용자 로그아웃 의도가 확정됐다면 로컬 Token을 삭제한다.

#### 전체 로그아웃

```http
POST /api/v1/auth/logout-all
Authorization: Bearer <ACCESS_TOKEN>
```

Request Body는 없고 성공 result는 `null`이다.

### 4.5 사용자 프로필·동의·탈퇴

#### 내 프로필

```http
GET /api/v1/users/me
Authorization: Bearer <ACCESS_TOKEN>
```

주요 result:

```json
{
  "userId": "00000000-0000-4000-8000-000000000001",
  "email": "user@example.com",
  "nickname": "토스마스터",
  "accountType": "MEMBER",
  "provider": "FEDERATED",
  "privacyConsented": true,
  "privacyConsentVersion": "privacy-v1",
  "privacyConsentedAt": "2026-08-28T01:00:00Z",
  "termConsented": true,
  "termConsentVersion": "term-v1",
  "termConsentedAt": "2026-08-28T01:00:00Z",
  "createdAt": "2026-08-28T01:00:00Z"
}
```

- `accountType`은 `GUEST | MEMBER`다.
- `provider`는 하위 호환용 deprecated 필드다. 화면 분기는 `accountType`을 우선 사용한다.
- Guest의 `email`은 null일 수 있다.

#### 동의 상태 조회

```http
GET /api/v1/users/me/consents
Authorization: Bearer <ACCESS_TOKEN>
```

result는 `privacy`, `terms`, `qualityReview` 세 객체이며 각 객체는 다음 필드를 가진다.

```json
{
  "currentVersion": "privacy-v2",
  "consented": false,
  "consentedVersion": "privacy-v1",
  "consentedAt": "2026-08-20T01:00:00Z",
  "requiresConsent": true
}
```

필수 재동의 화면은 privacy/terms의 `requiresConsent`로 판단한다.

#### 동의 갱신

```http
PUT /api/v1/users/me/consents
Authorization: Bearer <ACCESS_TOKEN>
Content-Type: application/json
```

```json
{
  "isPrivacyConsented": true,
  "privacyConsentVersion": "privacy-v2",
  "isTermConsented": true,
  "termConsentVersion": "term-v2",
  "isQualityReviewConsented": false,
  "qualityReviewConsentVersion": null
}
```

성공 result는 세 정책의 저장된 동의 여부·버전·동의 시각을 반환한다.

#### 회원 탈퇴

```http
POST /api/v1/users/withdraw
Authorization: Bearer <ACCESS_TOKEN>
Content-Type: application/json
```

계정 유형별 Body:

```json
{
  "refreshToken": "<REFRESH_TOKEN>",
  "password": "<LOCAL_CURRENT_PASSWORD>",
  "firebaseIdToken": null
}
```

```json
{
  "refreshToken": "<REFRESH_TOKEN>",
  "password": null,
  "firebaseIdToken": "<FRESH_FIREBASE_ID_TOKEN>"
}
```

- `refreshToken`은 항상 필수다.
- LOCAL은 현재 `password`, Firebase/SNS 회원은 recent-auth를 위한 `firebaseIdToken`, Guest는 둘 다 생략한다.
- 성공 result는 `status=WITHDRAWN`, `withdrawnAt`, `cleanupStatus`다.
- 성공 즉시 앱이 가진 모든 Identity Access/Refresh Token과 사용자 캐시를 삭제한다.

## 5. Learning Core 모의고사 상세

### 5.1 시험 생성과 문제 조회

#### 시험 생성

```http
POST /api/v1/exams
Authorization: Bearer <ACCESS_TOKEN>
Idempotency-Key: <lowercase UUID v4>
```

Request Body는 없다.

- 한 번의 의도적 시험 시작/restart마다 새 UUID v4를 생성한다.
- 응답 유실·timeout 등 동일 HTTP 동작의 transport retry에는 반드시 같은 key를 재사용한다.
- key는 결제·AttemptGroup ID가 아니라 한 번의 시험 Session 생성 command ID다.
- Learning Core의 Billing saga feature flag는 기본 off다. flag off 환경에서는 기존 앱 호환을 위해 header가 없어도 동작하지만, staging/prod 활성화 전에 프론트가 header를 먼저 배포해야 한다.
- flag on 환경에서 누락·대문자·non-v4 key는 `400 IDEMPOTENCY_KEY_INVALID`다.

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "성공입니다.",
  "result": {
    "examId": "exam-example-id",
    "title": "TOEIC Speaking Mock Exam",
    "questions": []
  }
}
```

`questions[]` 필드:

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `part` | integer | Part 번호 |
| `questionNumber` | integer | 문항 번호 |
| `text` | string/null | 문제 문구 |
| `referenceText` | string/null | 지문 |
| `partIntroText` | string/null | Part 안내 |
| `audioUrl` | string/null | 문제 음원 Presigned GET URL |
| `guideAudioUrl` | string/null | 안내 음원 URL |
| `imageUrl` | string/null | 이미지 URL |
| `tableContext` | object/null | Part 4 비정형 표 정보 |
| `prepTimeSec` | integer/null | 준비 시간 |
| `speakTimeSec` | integer/null | 답변 시간 |

#### 특정 문항 문제 조회

```http
GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt
Authorization: Bearer <ACCESS_TOKEN>
```

result는 위 `questions[]`의 단일 객체다.

### 5.2 음성 업로드·제출

호출 순서:

```text
upload-url 발급 → S3 PUT → submit → 문항 status polling
```

#### 업로드 URL 발급

```http
GET /api/v1/exams/{examId}/questions/{questionNumber}/upload-url?retryCount=0
Authorization: Bearer <ACCESS_TOKEN>
```

| Query | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `retryCount` | 아니요 | `0` | 최초 답변은 0, 재답변은 1 이상 |

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "성공입니다.",
  "result": {
    "uploadUrl": "https://<presigned-s3-url>",
    "fileKey": "temp/exam-example-id/q_1_r0.wav",
    "expiresIn": 60
  }
}
```

S3 업로드:

```http
PUT <result.uploadUrl>

<raw audio binary>
```

- `multipart/form-data`가 아니라 파일 raw binary를 보낸다.
- S3 요청에는 Identity Authorization 헤더를 보내지 않는다.
- `fileKey`를 submit Body에 다시 보내지 않는다. 서버가 examId·문항·retryCount로 같은 key를 계산한다.
- 현재 구현은 `.wav` Object Key를 만들지만 Presigned PUT에 업로드 `Content-Type`을 고정하거나 응답으로 알려주지 않는다. 프론트 녹음 포맷과 요청 `Content-Type`은 별도 합의 전 임의 변경하지 않는다.
- 현재 구현은 Presigned URL signature를 5분으로 생성하면서 응답 `expiresIn`은 60을 반환한다. 프론트는 URL 만료 오류가 나면 같은 API로 재발급하고, 이 값의 단위/일치를 backend가 정리하기 전까지 장시간 캐시하지 않는다.

#### 제출·채점 시작

```http
POST /api/v1/exams/{examId}/questions/{questionNumber}/submit?retryCount=0
Authorization: Bearer <ACCESS_TOKEN>
```

Request Body는 없다. 성공 result는 `{"status":"PENDING"}`처럼 `PENDING | PROCESSING | COMPLETED | FAILED` 중 하나다.

#### 문항 상태 polling

```http
GET /api/v1/exams/{examId}/questions/status?questionNumber=1&retryCount=0
Authorization: Bearer <ACCESS_TOKEN>
```

```json
{
  "examId": "exam-example-id",
  "questionNumber": 1,
  "retryCount": 0,
  "status": "PROCESSING"
}
```

`PENDING | PROCESSING`이면 polling하고 `COMPLETED | FAILED`이면 멈춘다.

### 5.3 시험 상태·결과

#### 전체 상태 polling

```http
GET /api/v1/exams/{examId}/status
Authorization: Bearer <ACCESS_TOKEN>
```

result: `examId`, `overallStatus`, `progressPercent`.

#### 종합 결과

```http
GET /api/v1/exams/{examId}/summary
Authorization: Bearer <ACCESS_TOKEN>
```

result:

| 필드 | 타입 |
| --- | --- |
| `examId` | string |
| `totalScore` | integer/null |
| `levelEstimate` | string/null |
| `totalSolvedQuestions` | integer |
| `summary`, `overallFeedback` | string/null |
| `partFeedback` | object/null |
| `strengths`, `weaknesses`, `recommendedPractice` | string array/null |
| `partScores` | object/null |

#### 문항 상세 결과

```http
GET /api/v1/exams/{examId}/questions?questionNumber=1&retryCount=0
Authorization: Bearer <ACCESS_TOKEN>
```

result는 `examId`와 `question`을 반환한다. `question` 주요 필드:

- 식별/회차: `partNumber`, `questionNumber`, `retryCount`, `totalRetryCount`
- 회차 비교: `retryScores[]`, `retryFeedbackScores[]`
- 제출 결과: `audioUrl`, `score`, `maxScore`, `transcript`
- 피드백: `feedback`, `azureFeedback`, `spokenWordSequence`
- Part 1 Q1·Q2 전용: `modelAnswer`
- 공통 문제 정보: `questionInfo`

`feedback`은 요약·level·발음/내용 점수·강점/약점·교정 항목·추천 답안·다음 전략 등을 담는다. `azureFeedback` 내부 필드에는 snake_case가 적용될 수 있으므로 실제 JSON key를 그대로 사용한다.

### 5.4 이력·재답변·채점 복구

#### 완료 시험 이력

```http
GET /api/v1/exams/history
Authorization: Bearer <ACCESS_TOKEN>
```

result는 `totalCount`, `histories[]`다. 각 이력은 `examId`, `title`, `status`, `cycleNumber`, `startedAt`, `completedAt`, `totalScore`, `maxScore`, `levelEstimate`, `summaryAvailable`, `retriedQuestionCount`를 가진다. 이력이 없으면 `histories: []`다.

시험 상태 enum은 `IN_PROGRESS | COMPLETED | ABANDONED`다.

#### 재답변 문항·회차

```http
GET /api/v1/exams/{examId}/retries
Authorization: Bearer <ACCESS_TOKEN>
```

result는 `examId`, `questions[]`다. 각 문항은 `partNumber`, `questionNumber`, `totalAttemptCount`, `latestRetryCount`, `attempts[]`를 반환한다. `attempts[]`는 `retryCount`, `status`, `score`, `completedAt`을 가진다.

#### 시험 단위 채점 복구

```http
POST /api/v1/exams/{examId}/grading/retry
Authorization: Bearer <ACCESS_TOKEN>
```

Request Body는 없다. result:

```json
{
  "examId": "exam-example-id",
  "overallStatus": "PROCESSING",
  "retriedQuestionNumbers": [2],
  "waitingQuestionNumbers": [3],
  "missingSubmissionQuestionNumbers": [],
  "summaryAction": "WAITING"
}
```

`summaryAction`은 `NOT_READY | WAITING | RETRIED | ALREADY_COMPLETED`다. 이 API는 새 사용자 녹음을 만드는 API가 아니라 기존 최초 응시 채점 작업 복구용이다.

## 6. 1차 업데이트 예정 API

### 6.1 무료 모의고사 1회·결제 권한 — Billing 내부 기반 구현, 프론트 연동 금지

현재 확정된 정책은 검증된 휴대전화 번호당 무료 모의고사 1회다. Billing에는 다음 서버 간 기반이 구현돼 있다.

- `TMI-110`: Identity trial eligibility event consumer
- `TMI-112`: 첫 reserve에서 `TrialClaim`, `FREE_EXAM_ONCE` grant/ledger와 Reservation 생성
- `TMI-113`: Reservation confirm/cancel/status와 만료 lifecycle

`TMI-116`에서 Learning Core의 다음 코드 기반을 구현했다.

- Learning Core의 필수 `Idempotency-Key` 수신과 Billing client
- `reserve → ExamSession durable commit → confirm` 시험 생성 saga와 불명확 응답 reconciliation

아직 남은 production 연결 범위:

- AttemptGroup 상태 event consumer, 탈퇴·재가입 owner rebind
- 실제 Lattice/IAM/SG 설정과 staging E2E·rollout
- 결제 상품·구매·복원용 공개 API

따라서 현재 프론트가 호출할 Billing 공개 API는 0개다. `userId`, phone, 무료 여부, credit 수량, 결제 성공 여부를 `POST /api/v1/exams`에 임의로 보내지 않는다.

`POST /api/v1/exams`의 header와 Billing saga 코드는 구현됐지만 feature flag 기본값은 off다. Mongo index·replica-set, Billing expiry worker, 실제 Lattice/IAM/SG와 INITIAL/REPLACEMENT·장애 복구 staging E2E가 끝나기 전에는 flag를 켜거나 production 무료권/결제 UX를 연결하지 않는다.

### 6.2 10초 영작 챌린지 — v1 계약 승인, API 미구현

콘텐츠는 Learning Core가 사용하는 MongoDB cluster `to-teacher-app`의 `challenge_10s_questions` collection에 `dayNumber`별 세 문제로 저장돼 있다. 다음 7개 Learning Core API는 계획돼 있지만 현재 서버에는 없다.

| 상태 | Method | Path | 용도 |
| --- | --- | --- | --- |
| 🟡 | `GET` | `/api/v1/challenges/today` | 오늘 진행도 |
| 🟡 | `GET` | `/api/v1/challenges/today/questions/{questionNumber}` | 개별 문제 |
| 🟡 | `POST` | `/api/v1/challenges/today/questions/{questionNumber}/attempt` | 녹음 시작 attempt |
| 🟡 | `POST` | `/api/v1/challenges/attempts/{attemptId}/upload-url` | 녹음 후 S3 URL 발급 |
| 🟡 | `POST` | `/api/v1/challenges/today/questions/{questionNumber}/answer` | 업로드 완료·답안 제출 |
| 🟡 | `GET` | `/api/v1/challenges/history?yearMonth=YYYY-MM` | 월별 참여 이력 |
| 🟡 | `GET` | `/api/v1/challenges/{challengeDate}/results?questionNumber={optional}` | 날짜·문항별 결과 |

전체 요청·응답·상태·오류의 승인된 v1 계약은 [10초 영작 챌린지 프론트엔드 API 명세](./ten-second-challenge-frontend-api.md)를 따른다.

콘텐츠 필드 매핑:

| Mongo 내부 필드 | 프론트 필드/처리 |
| --- | --- |
| `questions[].questionNumber` | `questionNumber` |
| `questions[].korean` | `promptKo` |
| `questions[].referenceAnswer` | 제출·만료 terminal 이후에만 `referenceAnswer` |
| `questions[].difficulty` | 문제 조회·terminal 결과의 `difficulty` integer 그대로 전달 |
| `_id`, `dayNumber`, `questionId` | 내부 전용, Request/Response에 사용하지 않음 |

- 프론트는 `dayNumber`를 계산하거나 collection을 직접 조회하지 않는다.
- 현재 날짜의 문제 선택은 서버가 `challengeDate`를 기준으로 수행한다.
- `referenceAnswer`는 DB에 저장돼 있어도 문제 조회·attempt·upload-url 응답에는 포함되지 않는다.
- AI 완료 시 `aiResult.referenceAnswer`에도 attempt snapshot의 같은 사전 정의 답안을 포함한다. `no_speech`에서도 `aiResult`는 non-null이고 이 답안은 유지되며 나머지 AI 생성 field만 null이다.
- `difficulty`는 프론트가 scale을 해석하지 않고 표시 metadata로만 사용하며 Request에 다시 보내지 않는다. AI 요청에는 포함되지 않는다.
- 서버는 challenge feature를 처음 활성화해 성공 기동한 KST 날짜를 영구 저장하고 그날을 dayNumber 1로 사용한다. 재배포해도 초기화하지 않고 콘텐츠는 순환하지 않는다.
- 선택된 콘텐츠가 없거나 하루 세 문제 구성이 잘못됐으면 `404 CHALLENGE_CONTENT_NOT_FOUND` 화면을 사용한다.

v1 확정 사항:

- MEMBER 전용이며 Guest는 `403`으로 거절
- M4A/AAC-LC, 16/44.1/48 kHz, mono/stereo, 최대 2 MiB
- `aiResult.referenceAnswer`, transcript, verdict, correctedAnswer와 meaning·grammar·pronunciation feedback
- no-speech에서도 사전 정의 답안을 가진 non-null `aiResult` 유지
- foreground polling 최대 60초, 서버 Callback deadline 120초·최대 3 generation
- MVP 사용자 audio 재생과 `audioUrl` 제외
- 기존 날짜 rollover 보호 계약 승인

## 7. 프론트에서 호출하면 안 되는 API

| 구분 | Method | Path | 호출 주체 |
| --- | --- | --- | --- |
| ⛔ Learning Core AI callback | `POST` | `/api/v1/exams/callback/feedback` | Python AI 서버 |
| ⛔ Learning Core AI callback | `POST` | `/api/v1/exams/callback/speechace` | Python AI 서버 |
| ⛔ Learning Core AI callback | `POST` | `/api/v1/exams/callback/azure` | Python AI 서버 |
| ⛔ Learning Core event | `POST` | `/internal/v1/events/withdrawn` | Identity workload |
| ⛔ Billing event | `POST` | `/internal/v1/eligibility/trial/events` | Identity workload |
| ⛔ Billing reservation | `POST` | `/internal/v1/reservations` | Learning Core workload |
| ⛔ Billing reservation | `POST` | `/internal/v1/reservations/{reservationId}/confirm` | Learning Core workload |
| ⛔ Billing reservation | `POST` | `/internal/v1/reservations/{reservationId}/cancel` | Learning Core workload |
| ⛔ Billing reservation | `POST` | `/internal/v1/reservations/status` | Learning Core workload |
| ⛔ Identity infrastructure | `GET` | `/.well-known/jwks.json` | JWT 검증 서비스 |

내부 API에 사용자 Access Token을 전달하거나 모바일 앱에서 직접 호출하지 않는다.

## 8. 프론트 구현 체크리스트

- [ ] Identity와 Learning Core Base URL을 별도 환경변수로 둔다.
- [ ] 인증 API interceptor의 공개/Bearer 경로를 구분한다.
- [ ] `401`에서 refresh rotation 동시 요청을 한 번으로 묶는다.
- [ ] rotation 성공 시 Access/Refresh Token을 함께 교체한다.
- [ ] 사용자 분기는 HTTP status와 `code`로 처리한다.
- [ ] Request에 실제 `userId`를 추가하지 않는다.
- [ ] S3 PUT에 Authorization을 추가하지 않는다.
- [ ] S3 PUT 성공 후에만 exam submit을 호출한다.
- [ ] 시험/문항 status의 terminal 상태에서 polling을 중단한다.
- [ ] 회원 탈퇴·Guest merge 성공 시 이전 Token과 캐시를 폐기한다.
- [ ] Billing 내부 API는 앱에서 직접 호출하지 않고 Learning Core 연동 완료 전 무료권 UX를 production에서 열지 않는다.
- [ ] 시험 시작마다 새 lowercase UUID v4 `Idempotency-Key`를 만들고 동일 transport retry에는 같은 key를 재사용한다.
- [ ] Challenge 🟡 API는 feature flag 뒤에 두고 endpoint 구현·배포 확인 전 호출하지 않는다.
- [ ] Challenge는 승인된 v1 계약으로 프론트 타입을 생성한다.

## 9. 현재 알려진 연동 주의점

1. Learning Core upload URL의 실제 signature는 5분인데 `expiresIn` 응답은 현재 60이다. backend 정리 전까지 URL 만료 시 재발급하는 방식으로 처리한다.
2. Billing의 무료권 reserve/lifecycle은 `TMI-112`·`TMI-113`, Learning Core 시험 생성 saga 코드는 `TMI-116`으로 구현됐다. 다만 feature flag는 기본 off이며 실제 Lattice/IAM/SG·Mongo migration과 staging 장애 복구 E2E 전에는 production에서 활성화하지 않는다.
3. Challenge 프론트·AI 계약은 v1으로 승인됐지만 현재 endpoint는 구현되지 않았다.
4. Identity LOCAL signup은 Token을 반환하지 않으므로 별도 login이 필요하다. Firebase signup과 Guest upgrade/merge는 Token을 반환한다.
5. Identity `provider`는 deprecated다. 신규 화면 로직은 `accountType`과 Firebase 인증수단 정보를 기준으로 한다.
6. 기존 모의고사 음성 업로드는 `.wav` key를 사용하지만 업로드 `Content-Type`과 실제 codec을 서버가 고정 검증하지 않는다. 포맷 계약을 확정하기 전 프론트 녹음 설정을 임의 변경하지 않는다.
