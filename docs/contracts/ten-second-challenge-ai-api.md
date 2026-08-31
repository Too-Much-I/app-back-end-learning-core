# 10초 영작 챌린지 Learning Core–AI API 명세

- 버전: v1
- 작성일: 2026-08-28
- 대상: Learning Core 백엔드, Python AI 서버
- 상태: Learning Core·AI 팀 승인, 구현 전
- 관련 Jira: `TMI-102`, `TMI-105`, `TMI-106` — Learning Core Challenge backend·AI 연동 전용 Jira는 아직 없음

> 이 문서의 API는 Learning Core·AI 팀이 승인한 v1 계약이지만 아직 구현·배포되지 않았다. 기존 모의고사 `/evaluations`와 시험 Feedback Callback 계약을 변경하거나 재사용하지 않고, 10초 챌린지 전용 versioned 계약으로 구현한다. 양쪽 구현은 이 문서와 공유 contract fixture를 기준으로 검증한다.

## 1. 목표와 처리 흐름

사용자는 한국어 문제 문장을 보고 영어 문장을 만들어 최대 10초 동안 발화한다. Learning Core는 제출을 먼저 내구성 있게 저장하고 AI 채점은 비동기로 수행한다.

```text
앱이 S3 audio 업로드
→ Learning Core가 ChallengeAttempt SUBMITTED와 결정적 GradingJob 저장
→ 앱 submit 요청 성공 응답
→ Learning Core가 AI 평가 요청
→ AI가 202 Accepted
→ AI가 비동기 분석
→ AI가 Learning Core 전용 Callback 호출
→ Learning Core가 결과를 멱등 저장
→ 앱이 기존 결과 API로 polling
```

- 사용자 제출 완료는 AI 성공에 의존하지 않는다.
- AI 요청·Callback 재시도는 새 사용자 응시를 만들지 않는다.
- 프론트 앱은 이 문서의 endpoint를 직접 호출하지 않는다.
- 기존 시험 채점의 `user_id=examId` 계약에는 영향을 주지 않는다.

## 2. 용어와 식별자

| 이름 | 형식 | 생성 주체 | 의미 |
| --- | --- | --- | --- |
| `attempt_id` | UUID 문자열 | Learning Core | 사용자의 문제별 단일 ChallengeAttempt |
| `job_id` | 문자열 | Learning Core | 특정 grading generation의 결정적 작업 ID |
| `grading_attempt` | 1 이상의 정수 | Learning Core | timeout·실패 후 AI 재채점 generation |
| `callback_id` | UUID 문자열 | AI | 하나의 논리적 Callback event ID |
| `question_id` | opaque 문자열 | 콘텐츠 | `D001Q01` 같은 stable ID이며 형식을 파싱하지 않음 |
| `question_number` | 정수 enum | 콘텐츠 | `1`, `2`, `3` 중 하나 |

권장 결정적 `job_id` 형식:

```text
challenge:{attemptId}:grading:{gradingAttempt}
```

- `job_id` 문자열 형식은 Learning Core 내부 규칙이며 AI는 파싱하지 않는다.
- AI는 요청에서 받은 `attempt_id`, `job_id`, `grading_attempt`를 변경하지 않고 Callback에 그대로 반환한다.
- `grading_attempt`는 사용자 재응시 횟수가 아니다.

## 3. 공통 전송·보안 계약

### 3.1 네트워크

- staging과 production은 private ECS Service Connect 또는 동등한 private service discovery 경로를 사용한다.
- 같은 ECS cluster에 있다는 사실만으로 인증을 생략하지 않는다.
- 전송 구간은 TLS를 사용한다. Service Connect TLS를 사용하지 못하는 초기 환경은 private VPC security group으로 양방향 target을 제한한 뒤 TLS 적용을 production 활성화 gate로 둔다.
- redirect를 따라가지 않는다.
- AI endpoint와 Callback URL은 배포 설정으로 고정한다. 요청 Body에서 임의 Callback URL을 받지 않는다.

### 3.2 서비스 인증

MVP에서는 방향별로 분리된 전용 Bearer credential을 사용한다.

```http
Authorization: Bearer <SERVICE_CREDENTIAL>
```

- Learning Core → AI credential과 AI → Learning Core credential은 서로 다른 값이다.
- 실제 값은 AWS Secrets Manager 또는 동등한 secret store에서 ECS task로 주입한다.
- 실제 credential을 코드, 문서, 테스트 fixture와 로그에 기록하지 않는다.
- 사용자 Access Token을 서버 간 인증에 재사용하지 않는다.
- 누락·불일치 credential은 `401` 또는 `403`으로 거절하고 운영 경보를 발생시킨다.
- credential rotation 기간에는 현재 값과 직전 값 두 개를 짧은 기간 함께 허용할 수 있다.

향후 workload JWT 또는 AWS_IAM으로 교체하더라도 request/callback payload 계약은 유지한다.

### 3.3 공통 Header

```http
X-Challenge-Contract-Version: v1
```

- Body의 `contract_version`과 Header 값은 반드시 일치해야 한다.
- 지원하지 않는 version은 `400 UNSUPPORTED_CONTRACT_VERSION`으로 거절한다.
- JSON field는 `snake_case`를 사용한다.
- 시각이 필요한 경우 ISO 8601 UTC 문자열을 사용한다.

## 4. Audio 계약

| 항목 | 허용값 |
| --- | --- |
| 파일 확장자 | `.m4a` |
| 컨테이너 | M4A |
| 코덱 | AAC-LC |
| MIME type | `audio/mp4` |
| sample rate | 16,000 Hz, 44,100 Hz 또는 48,000 Hz |
| channel | mono 또는 stereo |
| 최대 파일 크기 | 2,097,152 bytes(2 MiB) |
| 제품 녹음 길이 | 앱 기준 최대 10초 |

- 모바일 OS가 생성하는 정상적인 AAC encoder delay와 metadata를 허용한다.
- AI는 입력을 decode한 뒤 내부 평가용 16 kHz mono PCM으로 정규화한다.
- sample rate나 channel 차이를 평가 점수 또는 verdict에 반영하지 않는다.
- `Content-Type`, container 또는 codec이 지원 범위 밖이면 영구 오류로 처리한다.
- 파일이 2 MiB를 초과하면 AI 호출 전에 Learning Core가 거절하는 것이 원칙이며, AI도 같은 상한을 방어적으로 검증한다.
- 사용자 audio와 transcript 전체를 애플리케이션 로그에 기록하지 않는다.

## 5. AI 평가 요청

### 5.1 Endpoint

```http
POST {AI_INTERNAL_BASE_URL}/v1/challenges/evaluations
Authorization: Bearer <LEARNING_CORE_TO_AI_CREDENTIAL>
X-Challenge-Contract-Version: v1
Idempotency-Key: challenge:{attemptId}:grading:{gradingAttempt}
Content-Type: multipart/form-data
```

기존 시험 `/evaluations` endpoint와 분리한다.

### 5.2 multipart field

| field | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `contract_version` | 문자열 | O | 고정값 `v1` |
| `attempt_id` | UUID 문자열 | O | ChallengeAttempt 식별자 |
| `job_id` | 문자열 | O | 현재 generation의 결정적 Job ID |
| `grading_attempt` | 정수 | O | 1 이상의 현재 generation |
| `question_id` | 문자열 | O | 콘텐츠의 stable opaque ID |
| `question_number` | 정수 | O | `1`, `2`, `3` 중 하나 |
| `prompt_ko` | 문자열 | O | 사용자가 본 한국어 문제 문장 |
| `reference_answer` | 문자열 | O | 콘텐츠의 참고 영어 답안 |
| `audio_file` | binary | O | 위 Audio 계약을 만족하는 M4A/AAC 파일 |

`prompt_ko`는 Mongo `challenge_10s_questions.questions[].korean`, 프론트 공개 필드 `promptKo`와 같은 값이다.

다음 값은 AI 요청에 보내지 않는다.

- 실제 사용자 `userId`
- 사용자 Access Token
- `difficulty`
- Mongo `_id`
- `dayNumber`
- `challengeDate`
- S3 bucket, object key와 credential

Learning Core가 S3 object를 내려받아 `audio_file`로 전달한다. 이 방식은 AI task에 S3 읽기 권한이나 만료되는 Presigned GET URL을 제공하지 않는다.

### 5.3 평가 의미

- `reference_answer`는 유일한 정답 문자열이 아니라 의미·문법 평가를 돕는 참고 답안이다.
- transcript가 reference answer와 다르더라도 한국어 prompt의 의미를 자연스럽고 정확하게 전달하면 정답으로 판단할 수 있다.
- 평가는 의미 전달, 영어 문법·자연스러움, 발음 이해 가능성을 구분한다.
- `difficulty`나 사용자 과거 기록을 이용해 판정 기준을 바꾸지 않는다.
- 숫자 점수, 랭킹 점수와 경제적 reward는 v1 범위에 포함하지 않는다.

### 5.4 접수 성공 응답

AI가 요청을 내구성 있게 접수한 뒤 다음 응답을 반환한다.

```http
HTTP/1.1 202 Accepted
Content-Type: application/json
```

```json
{
  "contract_version": "v1",
  "job_id": "challenge:2af4c181-9549-45ee-81cb-8274886423f4:grading:1",
  "grading_attempt": 1,
  "status": "accepted"
}
```

- `202`는 채점 완료가 아니라 요청 접수만 의미한다.
- 동일한 `Idempotency-Key`와 동일한 payload 재전송은 새 AI 작업을 만들지 않고 같은 `202` 응답을 반환한다.
- 동일한 key에 식별자 또는 audio가 다른 요청은 `409 IDEMPOTENCY_CONFLICT`다.
- AI가 내구성 있게 접수하지 못했다면 `202`를 반환하지 않는다.

## 6. AI 결과 Callback

### 6.1 Endpoint

```http
POST {LEARNING_CORE_INTERNAL_BASE_URL}/internal/v1/challenges/grading/callback
Authorization: Bearer <AI_TO_LEARNING_CORE_CREDENTIAL>
X-Challenge-Contract-Version: v1
Content-Type: application/json
```

이 endpoint에는 사용자 Access Token과 `BaseResponse`를 사용하지 않는다.

### 6.2 공통 Callback field

| field | 형식 | 필수 | 설명 |
| --- | --- | --- | --- |
| `contract_version` | 문자열 | O | 고정값 `v1` |
| `callback_id` | UUID 문자열 | O | AI가 생성한 Callback 멱등성 ID |
| `attempt_id` | UUID 문자열 | O | 요청 값을 그대로 echo |
| `job_id` | 문자열 | O | 요청 값을 그대로 echo |
| `grading_attempt` | 정수 | O | 요청 값을 그대로 echo |
| `outcome` | enum | O | `completed`, `no_speech`, `failed` |
| `transcript` | 문자열 또는 null | 조건부 | AI가 인식한 영어 발화 |
| `verdict` | enum 또는 null | 조건부 | `correct`, `needs_improvement` |
| `corrected_answer` | 문자열 또는 null | 조건부 | 교정된 영어 답안 |
| `feedback` | 객체 또는 null | 조건부 | 의미·문법·발음 피드백 |
| `error` | 객체 또는 null | 조건부 | `failed`일 때만 사용 |

Learning Core는 알 수 없는 추가 field는 무시할 수 있지만 필수 field 누락, 잘못된 enum과 field 조합은 거절한다.

Callback 크기 제한:

| 항목 | 최대 길이 |
| --- | --- |
| `transcript` | 1,000자 |
| `corrected_answer` | 1,000자 |
| `feedback.meaning` | 500자 |
| `feedback.grammar` | 500자 |
| `feedback.pronunciation` | 500자 |
| Callback JSON 전체 | UTF-8 기준 16 KiB |

- 길이 상한을 넘는 Callback은 `413 CALLBACK_PAYLOAD_TOO_LARGE`로 거절한다.
- AI는 잘라낸 문장임을 나타내는 별도 suffix를 추가하지 않고, 의미가 완결된 범위 안에서 상한 이하로 생성한다.

### 6.3 `completed`

```json
{
  "contract_version": "v1",
  "callback_id": "6d424480-f26a-4837-9c14-fbaee760bb4f",
  "attempt_id": "2af4c181-9549-45ee-81cb-8274886423f4",
  "job_id": "challenge:2af4c181-9549-45ee-81cb-8274886423f4:grading:1",
  "grading_attempt": 1,
  "outcome": "completed",
  "transcript": "I normally take a bus to work.",
  "verdict": "correct",
  "corrected_answer": null,
  "feedback": {
    "meaning": "문장의 핵심 의미를 정확하게 전달했어요.",
    "grammar": "문법적으로 자연스러운 문장이에요.",
    "pronunciation": "normally의 첫 음절을 조금 더 분명하게 발음해 보세요."
  },
  "error": null
}
```

규칙:

- `transcript`, `verdict`, `feedback.meaning`, `feedback.grammar`, `feedback.pronunciation`은 non-blank 필수다.
- `verdict=correct`이면 `corrected_answer`는 null일 수 있다.
- `verdict=needs_improvement`이면 `corrected_answer`는 non-blank 필수다.
- feedback은 사용자에게 직접 노출 가능한 짧은 한국어 존댓말 문장으로 반환한다.
- 내부 prompt, chain-of-thought, 모델명, provider 원문과 디버그 metadata를 반환하지 않는다.

### 6.4 `no_speech`

발화가 감지되지 않은 것은 시스템 실패가 아니다.

```json
{
  "contract_version": "v1",
  "callback_id": "9f4d1f66-ea3a-4a7e-b0d3-f84dbb3c0ce5",
  "attempt_id": "2af4c181-9549-45ee-81cb-8274886423f4",
  "job_id": "challenge:2af4c181-9549-45ee-81cb-8274886423f4:grading:1",
  "grading_attempt": 1,
  "outcome": "no_speech",
  "transcript": null,
  "verdict": null,
  "corrected_answer": null,
  "feedback": null,
  "error": null
}
```

- Learning Core는 Job을 `COMPLETED`로 저장한다.
- 공개 `gradingStatus=completed`로 projection한다.
- 프론트 공개 `feedbackType` enum은 추가하지 않는다.
- 프론트 `aiResult`는 null이 아니며 Learning Core의 attempt snapshot에서 가져온 `referenceAnswer`를 포함한다. `transcript`, `verdict`, `correctedAnswer`, `feedback`은 null이다.
- 사전 정의 답안은 AI가 Callback으로 echo하지 않는다. Learning Core가 DB 콘텐츠 snapshot에서 조립하므로 no-speech에서도 그대로 유지된다.
- 프론트에는 transcript가 null인 경우의 고정 발화 없음 안내와 참고 답안을 제공한다.

### 6.5 `failed`

```json
{
  "contract_version": "v1",
  "callback_id": "be0fcfbf-ce8e-43d5-aab8-f8a384910d62",
  "attempt_id": "2af4c181-9549-45ee-81cb-8274886423f4",
  "job_id": "challenge:2af4c181-9549-45ee-81cb-8274886423f4:grading:1",
  "grading_attempt": 1,
  "outcome": "failed",
  "transcript": null,
  "verdict": null,
  "corrected_answer": null,
  "feedback": null,
  "error": {
    "code": "MODEL_UNAVAILABLE",
    "retryable": true
  }
}
```

- `failed`에서는 `error.code`와 `error.retryable`이 필수다.
- error message, stack trace, provider payload와 사용자 audio/transcript는 포함하지 않는다.
- `retryable=true`이면 Learning Core가 새 `grading_attempt`로 재시도할 수 있다.
- `retryable=false` 또는 최대 generation 소진이면 공개 `gradingStatus=failed`로 전환한다.

권장 error code:

| code | retryable | 의미 |
| --- | --- | --- |
| `UNSUPPORTED_AUDIO` | false | container·codec이 계약 범위 밖 |
| `AUDIO_TOO_LARGE` | false | 2 MiB 초과 |
| `AUDIO_DECODE_FAILED` | false | 손상되었거나 decode 불가능한 파일 |
| `MODEL_TIMEOUT` | true | 모델 처리 timeout |
| `MODEL_UNAVAILABLE` | true | 일시적인 모델·provider 장애 |
| `INTERNAL_ERROR` | true | AI 내부 일시 오류 |

## 7. Callback 응답과 멱등성

### 7.1 성공·중복·stale

Learning Core는 다음 세 경우 모두 `204 No Content`를 반환한다.

- 현재 generation의 최초 유효 Callback을 저장함
- 같은 `callback_id`와 같은 payload가 재전송됨
- 현재보다 작은 `grading_attempt`의 늦은 Callback을 상태 변경 없이 무시함

AI는 `204`를 받으면 해당 Callback 재시도를 중단한다. Learning Core는 저장, duplicate와 stale을 구조화 로그와 metric으로 구분한다.

### 7.2 fencing 규칙

1. `attempt_id`로 ChallengeAttempt와 GradingJob을 찾는다.
2. `job_id`가 해당 `grading_attempt`의 결정적 Job ID인지 확인한다.
3. Callback `grading_attempt`이 현재 값보다 작으면 stale 성공 no-op 처리한다.
4. 현재 값보다 크거나 존재하지 않는 Job이면 `409 GRADING_GENERATION_CONFLICT`로 격리한다.
5. 현재 generation의 결과가 이미 저장됐다면 같은 payload는 duplicate 성공, 다른 payload는 `409 CALLBACK_PAYLOAD_CONFLICT`다.
6. 결과 저장과 Job terminal 전환은 하나의 원자적 상태 전이로 처리한다.

### 7.3 Callback 오류

| HTTP | code | AI 처리 |
| --- | --- | --- |
| `204` | 없음 | 성공, 재시도 중단 |
| `400` | `INVALID_CALLBACK` | 영구 실패, DLQ·경보 |
| `401/403` | 인증 오류 | 재시도 폭주 중단, 즉시 경보 |
| `404` | `ATTEMPT_NOT_FOUND` | 영구 실패, DLQ·경보 |
| `409` | generation/payload conflict | 영구 격리·경보 |
| `413` | `CALLBACK_PAYLOAD_TOO_LARGE` | 영구 실패, DLQ·경보 |
| `429` | 없음 또는 `TOO_MANY_REQUESTS` | `Retry-After`를 존중해 재시도 |
| `5xx` | 없음 | 동일 `callback_id`와 payload로 재시도 |

## 8. Timeout과 재시도 소유권

### 8.1 Learning Core → AI

- connection timeout: 3초
- AI 접수 응답 timeout: 15초
- `202`를 받기 전 connection failure, timeout, `408`, `425`, `429`, `5xx`는 동일 `job_id`, `grading_attempt`, `Idempotency-Key`로 재전송한다.
- `400`, `401`, `403`, `404`, `405`, `409`, `413`, `415`, `422`는 자동 transport retry하지 않고 설정·payload 오류로 격리·경보한다.
- `429`에 `Retry-After`가 있으면 우선 적용한다.

### 8.2 AI 처리 deadline

- `202` 접수 후 Callback deadline은 120초다.
- deadline까지 Callback이 없으면 Learning Core는 기존 generation을 timeout 처리하고 `grading_attempt`를 1 증가시켜 새 `job_id`로 재요청한다.
- 사용자 attempt 하나의 최대 AI generation은 3이다.
- 세 generation을 모두 소진하면 Job과 공개 `gradingStatus`를 `failed`로 전환한다.
- 이전 generation Callback이 나중에 도착하면 fencing 규칙에 따라 `204` stale no-op으로 처리한다.

### 8.3 AI → Learning Core

- AI는 Callback timeout, connection failure, `429`, `5xx`에 동일 `callback_id`와 payload로 지수 backoff 재시도한다.
- backoff는 5초부터 시작해 최대 10분으로 제한한다.
- 최대 시도 횟수는 10회이며 모두 실패하면 DLQ 또는 내구성 있는 실패 저장소에 보관하고 경보한다.
- AI의 Callback delivery retry는 새 분석 작업이나 새 `grading_attempt`를 만들지 않는다.

## 9. 관측성과 개인정보

양쪽 구조화 로그와 metric에는 다음 식별값만 사용한다.

- `attempt_id`
- `job_id`
- `grading_attempt`
- `callback_id`
- stage, outcome, error code, duration

다음 값은 로그에 기록하지 않는다.

- 사용자 audio 또는 audio base64
- transcript 전체
- 실제 userId와 Access Token
- 서비스 credential
- prompt와 reference answer 전체
- AI provider 원문과 stack trace

필수 metric:

- 평가 요청 접수 성공·실패 수
- AI 처리 latency p50/p95/p99
- outcome별 `completed/no_speech/failed` 수
- transport retry, generation retry 수
- duplicate·stale·payload conflict Callback 수
- 최종 `gradingStatus=failed` 수

## 10. Contract test fixture

양쪽 저장소가 같은 fixture를 사용해 최소 다음을 검증한다.

1. 정상 `completed` 요청·Callback
2. reference answer와 다른 올바른 영어 문장
3. `needs_improvement`와 필수 corrected answer
4. `no_speech`
5. retryable·non-retryable `failed`
6. 동일 Idempotency-Key 요청 재전송
7. 같은 key·다른 audio conflict
8. 동일 Callback 재전송
9. 이전 `grading_attempt` stale Callback
10. 미래 generation과 payload conflict
11. 지원하지 않는 version·enum·field 조합
12. M4A/AAC의 허용 sample rate·channel 조합
13. 손상 audio, 지원하지 않는 codec, 2 MiB 초과
14. 인증 누락·잘못된 credential

실제 AWS, S3, MongoDB와 외부 AI provider를 contract test에서 호출하지 않는다. 작은 가짜 audio fixture와 Mock transport를 사용한다.

## 11. 구현·운영 활성화 체크리스트

- [x] Learning Core와 AI 팀이 endpoint와 snake_case field를 승인
- [ ] M4A/AAC 허용 profile과 2 MiB 상한을 실제 iOS·Android 녹음으로 검증
- [ ] 방향별 service credential 생성·주입·rotation 절차 준비
- [ ] Security group과 TLS 경로 확인
- [ ] request idempotency와 Callback fencing 양쪽 contract test 통과
- [ ] 120초 deadline·최대 3 generation을 staging latency로 검증
- [ ] Callback 204·4xx·429·5xx 재시도 E2E 통과
- [ ] transcript·audio·credential 로그 비노출 확인
- [x] 프론트 `aiResult` DTO를 이 계약의 projection으로 최종 동결
- [ ] Challenge backend·AI 구현 Jira 생성
