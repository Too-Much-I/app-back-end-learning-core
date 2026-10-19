# 10초 영작 챌린지 API·상태 계약 결정서

프론트 전달용 요청·응답 명세는 `docs/contracts/ten-second-challenge-frontend-api.md`, Learning Core–AI 서버 간 승인 계약은 `docs/contracts/ten-second-challenge-ai-api.md`를 따른다. 두 계약 모두 v1로 승인됐고 TMI-126 Learning Core 로컬 구현·테스트를 완료했다. 상대 서비스 적용·운영 E2E·배포는 남아 있다.

- 작성일: 2026-08-25
- 최종 갱신일: 2026-09-07
- Jira: [TMI-126](https://to-teacher.atlassian.net/browse/TMI-126) — Learning Core 10초 챌린지 API 및 비동기 AI 채점 구현
- 상태: 콘텐츠·자동 baseDate·비순환 dayNumber·difficulty·M4A/AAC·녹음·순차 진행·attempt/upload-url·1시간 deadline·rollover·MEMBER·프론트 결과/polling과 AI 계약 v1 승인 및 Learning Core 로컬 구현·테스트 완료. feature OFF·미배포이며 운영 활성화는 별도다.

## 1. 확정된 제품 요구

- 하루에 최대 3문제를 제공한다.
- 세 문제를 모두 푸는 것은 필수가 아니며 일부 문제만 참여한 날짜도 history에 노출한다.
- 같은 KST 날짜에는 모든 사용자에게 동일한 3문제를 제공한다.
- 사용자는 문제마다 최대 한 번만 응시한다.
- 문제는 1→2→3 순서로 진행하며 앞 문제가 terminal이어야 다음 문제를 시작할 수 있다.
- 한국어 문장을 문제로 제공한다.
- 사용자는 영어 문장을 만들고 최대 10초 길이로 직접 발음한다.
- 녹음 길이는 프론트에서 측정하고 녹음한 영어 발화 audio를 S3에 직접 업로드한다.
- 녹음 파일은 `.m4a` 확장자의 M4A 컨테이너와 AAC 코덱을 사용하고 S3 PUT `Content-Type`은 `audio/mp4`로 고정한다.
- 제출 답안은 AI 서버가 간단한 피드백을 생성한다.
- 프론트에는 오늘 진행도, 개별 문제, 답안 제출, 월별 날짜 참여·풀이 수와 특정 날짜 풀이 결과가 필요하다.

문제 DTO는 한국어 prompt를 반환하고 정답/참고 영어 문장은 제출 결과 전까지 공개하지 않는다.

## 2. 프론트 API 검토

### 2.1 오늘 진행도 조회 — 필요

권장 정보:

- `challengeDate`: `Asia/Seoul` 날짜
- `challengeDateExpiresAt`: 현재 server KST 날짜가 끝나는 ISO 8601 UTC 시각
- `expiresInSeconds`: 응답 생성 시점 기준 남은 초
- `status`: `not_started | in_progress | completed`
- `totalQuestionCount`: 항상 3
- `nextQuestionNumber`: 완료 시 null
- `completedQuestionNumbers`
- 문제별 `attemptStatus`와 `gradingStatus`

`nextQuestionNumber`와 완료 목록은 둘 중 하나가 아니라 둘 다 반환하는 편이 프론트 복구와 화면 표시가 단순하다.

앱은 기기 시계가 아니라 server가 계산한 `expiresInSeconds`로 timer를 시작하고 만료·foreground 복귀 시 오늘 진행도를 재조회한다. timer만으로는 요청 도중 자정이 바뀌는 경합을 막을 수 없으므로 question·attempt 요청에 직전 `challengeDate`를 `X-Challenge-Date` header로 보내고 server가 현재 KST 날짜와 최종 비교한다. 불일치하면 `409 CHALLENGE_DATE_CHANGED`와 최신 날짜·만료 정보를 반환한다.

### 2.2 녹음 시작 attempt 생성 — 필요

녹음 시작 직전에 attempt를 생성해 server clock 기준 `createdAt`, `challengeDate`와 `submissionDeadlineAt`을 고정한다. 이 attempt가 문제당 1회와 자정 rollover의 authoritative start다.

권장 계약:

```text
POST /api/v1/challenges/today/questions/{questionNumber}/attempt
X-Challenge-Date: YYYY-MM-DD
```

응답:

- `attemptId`
- `challengeDate`
- `questionNumber`
- `submissionDeadlineAt`
- 공개 `attemptStatus=not_started`

내부 S3 object key는 attempt 생성 시 attemptId를 기반으로 서버가 결정·고정하고 외부에 노출하지 않는다. 같은 문제의 재호출은 새 attempt를 만들지 않고 같은 `attemptId`와 deadline을 반환한다.

사용자 승인 경로는 `temp/challenges/{attemptId}/q_{questionNumber}.m4a`다. 같은 attempt 재녹음·URL 재발급은 이 key에 덮어쓰며 retryCount를 붙이지 않는다. 실제 userId·날짜를 추가하지 않고 기존 시험 key는 유지한다. 프론트 Request/Response에 별도 key field를 추가하지 않으며 받은 URL만 사용한다. 기존 버킷의 `temp/` lifecycle·권한 검증은 배포 전 확인사항이다.

`X-Challenge-Date`가 현재 server KST 날짜와 다르면 새 attempt를 만들지 않고 `CHALLENGE_DATE_CHANGED`로 오늘 진행도 재조회를 유도한다.

선택적으로 다음 read API를 둘 수 있다.

```text
GET /api/v1/challenges/today/questions/{questionNumber}
```

- 문제와 prompt를 조회한다. 이 GET 자체는 server 상태를 변경하지 않는다.
- 기존 attempt가 있으면 attempt 상태만 함께 반환할 수 있다.
- 제출·만료 후에는 현재 상태와 result link 가능 여부를 반환한다.

### 2.3 attempt S3 업로드 URL 발급 — 필요

녹음 완료 후 기존 attempt에 고정된 S3 object key의 Presigned PUT URL을 발급한다.

권장 계약:

```text
POST /api/v1/challenges/attempts/{attemptId}/upload-url
```

응답:

- `attemptId`
- `submissionDeadlineAt`
- Presigned PUT URL과 URL 만료 시각
- `contentType=audio/mp4`
- 최대 업로드 크기

규칙:

- JWT 사용자의 attempt 소유권을 확인한다.
- 현재 날짜가 아니라 attempt에 저장된 `challengeDate`와 deadline을 사용한다.
- URL 만료·응답 유실 시 동일 attemptId로 같은 S3 object key의 URL을 재발급한다.
- URL 재발급은 사용자 재응시로 계산하지 않는다.
- URL 만료 시각은 `submissionDeadlineAt`을 넘지 않는다.
- SUBMITTED 또는 EXPIRED attempt에는 새 URL을 발급하지 않는다.
- 후속 사용자 확인·승인: 프론트 재녹음은 answer 전에만 허용한다. 최종 PUT 완료 뒤 한 번 제출하며 최초 attempt의 deadline을 연장하지 않는다. 제출 후 예외적으로 바뀐 음성은 같은 Job에 보내지 않고 복구 불가 시 채점 실패로 끝낸다. 최초 AI 전송 bytes의 durable digest와 비교하며 version pin·별도 음성 보관본은 만들지 않는다. 기존 AI 409 규칙과 제출 접수·풀이 수·참고 답안은 유지한다.
- 2026-09-07 사용자 결정: 기존 유효 PUT URL의 같은 key 덮어쓰기를 허용하고 현재 객체는 마지막 성공 업로드로 유지한다. version pin·별도 보관본을 만들지 않는다. AI에 이미 접수된 음성/결과의 자동 교체는 포함하지 않는다. 변경 음성 재전송 방어는 위 후속 승인 기준을 따르고 기존 AI 409 계약을 유지한다.

### 2.4 문제 답변 제출 — 필요, timeout UX 재검토

권장 계약:

```text
POST /api/v1/challenges/today/questions/{questionNumber}/answer
Idempotency-Key: UUID
```

Request:

- `attemptId`

규칙:

- userId와 client 시각은 받지 않는다.
- server가 attempt에 미리 고정한 S3 key만 사용하고 client가 임의 URL/key를 지정하지 않는다.
- 제출 전에 exact object의 존재, content type, 크기와 필요 metadata를 검증한다.
- 10초 녹음 종료는 timeout 실패가 아니라 녹음된 audio의 정상 제출이다.
- 같은 key·같은 attempt 재전송은 기존 제출 결과를 반환한다.
- 이미 제출한 문제의 다른 attempt나 다른 object는 conflict다.
- 사용자 답안은 한 번만 저장하고 AI 재시도는 새 사용자 응시로 계산하지 않는다.

업로드 artifact는 `.m4a` M4A·AAC-LC, `audio/mp4`, 16/44.1/48 kHz, mono/stereo, 최대 2 MiB로 확정됐다. Learning Core는 제출 시 S3 존재·metadata MIME·크기를 검증하고 AI는 실제 binary profile과 decode를 검증한다. MIME 불일치는 submit 415, 접수 후 binary 오류는 비동기 grading failed로 처리한다.

### 2.4.1 10초 종료와 피드백 UX 재검토

기존 `10초 종료 → timed_out → AI 미요청 → 다음 문제` 모델은 사용자가 실제로 발화했는데도 답안과 피드백을 받지 못할 수 있어 채택하지 않는 것을 권장한다.

권장 모델:

1. 앱은 10초에 녹음을 자동 종료한다.
2. 녹음된 audio는 길이·발화 유무와 관계없이 S3에 업로드한다.
3. 제출 접수 즉시 참고 영어 문장을 반환하고 다음 문제를 연다.
4. AI는 비동기로 transcript·교정·발음 피드백을 생성한다.
5. 무음이면 `transcript=null`과 참고 답안·재시도 학습 안내를 결과로 저장하며 별도의 공개 `feedbackType` enum은 추가하지 않는다.
6. AI가 실패해도 참고 답안과 사용자 제출 상태는 유지하고 자동 복구한다.
7. 1시간 제출 유효시간까지 S3 제출 자체를 못 한 경우만 `expired` terminal로 처리하고 참고 답안만 제공한다.

따라서 client의 `outcome=timed_out`은 제거하고 정상 submit과 server-controlled expiry를 분리하는 것이 권장안이다. 공개 `attemptStatus`는 두 경우 모두 terminal인 `submitted`로 projection해 다음 문제와 결과 화면을 열며 AI 완료까지 기다리지 않는다.

### 2.5 월별 참여 이력 조회 — 필요

권장 계약:

```text
GET /api/v1/challenges/history?yearMonth=YYYY-MM
```

요청한 월의 각 날짜에 다음을 반환한다.

- `challengeDate`
- `participated`: 실제 audio 제출이 접수된 문제가 한 건 이상인지
- `solvedQuestionCount`: 내부 `SUBMITTED`인 실제 제출 접수 문제 수 0~3; `EXPIRED` 제외

2026-09-07 사용자 결정으로 실제 audio 제출이 접수된 문제만 풀이 수에 포함한다. AI 처리 중·정상 완료·무음·최종 채점 실패는 모두 포함하며 미제출 만료·시작만 한 attempt·S3 업로드만 한 문제는 제외한다. 동일 제출 replay는 중복 계산하지 않는다. 요청 월과 `[contentBaseDate, KST 오늘]`의 교집합 날짜를 반환하며 기준일 이전 월은 `dates=[]`, 미래 월은 400이다. 콘텐츠 소진으로 기준일 이후 날짜를 삭제하지 않고 history는 attempt snapshot으로 계산한다.

만료의 공개 `attemptStatus=submitted`와 다음 문제·참고 답안 접근은 유지한다. `dailyStatus`·`completedQuestionNumbers`는 진행 종료 기준이고 실제 풀이 수와 분리한다. 프론트는 이 목록이나 공개 상태를 세지 않고 서버의 `solvedQuestionCount`를 사용한다. 만료만 있는 날짜는 `participated=false`다.

월 단위 최대 31건이므로 cursor, size와 pagination은 사용하지 않는다.

### 2.6 특정 날짜 풀이 결과 조회 — 필요

권장 계약:

```text
GET /api/v1/challenges/{challengeDate}/results?questionNumber={optional}
```

규칙:

- `questionNumber`가 없으면 `challengeDate`와 `solvedQuestionCount`만 반환한다.
- `questionNumber`가 있으면 날짜 전체 풀이 수와 해당 문제 상세 단일 `question` 객체를 반환한다.
- 지정 문제의 attempt가 없거나 미제출·미만료이면 `question=null`이고 날짜 전체 풀이 수는 유지한다. 만료는 풀이 수에서 제외하되 참고 답안이 있는 `question`을 반환한다. 풀이 수 0과 non-null 만료 상세는 함께 나올 수 있다.
- `solvedQuestionCount`는 query 유무와 무관하게 날짜 전체 풀이 수다.
- 2026-09-07 사용자 승인: EXPIRED의 `submittedAt=null`, `gradedAt=null`, `gradingStatus=not_requested`, `aiResult=null`이다. snapshot 참고 답안은 유지하며 실제 제출이 없는 것을 만료 시각으로 대신 기록하지 않는다.

문제별 응답 후보:

- 공개 `attemptStatus`: not_started 또는 submitted
- `gradingStatus`: pending | processing | completed | failed
- prompt
- user audio URL 또는 재생 가능 여부
- AI가 인식한 영어 발화 transcript
- reference answer: 제출 뒤에만 공개
- `verdict`: correct | needs_improvement — 선택
- corrected answer
- 의미·문법에 대한 짧은 feedback
- 발음 feedback — 제공 범위 선택
- submittedAt, gradedAt

숫자 점수는 현재 요구에 없으므로 MVP에서 제외하는 편을 권장한다. 랭킹이나 보상이 필요해질 때 별도 계약으로 추가한다.

## 3. 추가로 필요한 비프론트 API

### AI 채점 Callback 또는 결과 수신

audio 인식과 피드백을 사용자 HTTP 요청과 외부 AI 성공으로 하나로 묶지 않는 것을 권장한다.

```text
answer 저장 + grading job 생성
→ submit 응답
→ AI 비동기 요청
→ challenge 전용 callback
→ 상세 결과에서 polling
```

- `submitted` 성공 응답 전에는 사용자 attempt와 결정적 grading job이 내구성 있게 저장돼야 한다.
- `attemptStatus=submitted`인 문제의 결과 조회는 AI Callback에 의존하지 않는다. Callback 전에는 HTTP 200, `gradingStatus=pending|processing`, `aiResult=null`과 참고 답안을 반환한다.
- 프론트 polling 중단이나 앱 종료는 grading job을 취소하지 않는다. 재진입 시 같은 결과 API로 최신 상태를 조회한다.
- 자동 재시도 후 최종 실패하면 `gradingStatus=failed`로 바꾸되 attempt, 참고 답안과 조회 가능성은 유지한다. submitted 문제 조회가 404가 되는 것은 허용하지 않는다.
- 최종 실패 조회도 HTTP 200과 기존 `BaseResponse` 성공 구조를 사용한다. `attemptStatus=submitted`, `gradingStatus=failed`, `gradedAt=null`, `aiResult=null`을 반환하고 prompt·submittedAt·reference answer는 유지한다.
- 내부 예외·AI 원문·재시도 횟수·failure reason은 클라이언트 DTO에 노출하지 않는다. 프론트는 `failed`에서 foreground polling을 중단하고 고정 안내 문구를 표시한다.
- 시험 Feedback Callback을 재사용하지 않는다.
- AI에는 실제 userId와 S3 위치를 보내지 않는다. 전용 v1 multipart의 식별자·prompt·reference answer와 서버가 S3에서 다운로드한 `audio_file`을 전달한다.
- 결정적 Job ID와 callback idempotency를 사용한다.
- AI timeout은 서버가 자동 재시도한다.
- 최종 AI 실패여도 사용자 answer attempt는 유지하고 재답변은 허용하지 않는다.
- 사용자용 grading retry API는 MVP에 두지 않고 자동 복구·운영 retry로 시작한다.

### 콘텐츠 등록

프론트 API는 아니지만 실제 콘텐츠 저장 구조와 게시 검증이 필요하다.

현재 확인된 저장 위치:

- MongoDB cluster: Learning Core가 이미 사용하는 `to-teacher-app`
- Collection: `challenge_10s_questions`
- 별도 Atlas cluster나 신규 Mongo connection은 만들지 않는다.
- 실제 URI·credential·logical database 이름은 환경 설정을 사용하고 문서·응답·로그에 노출하지 않는다.

현재 document 구조:

```javascript
{
  dayNumber: NumberInt(1),
  questions: [
    {
      questionNumber: NumberInt(1),
      questionId: "D001Q01",
      korean: "저는 어제 운동하지 않았어요.",
      referenceAnswer: "I didn't exercise yesterday.",
      difficulty: NumberInt(2)
    },
    {
      questionNumber: NumberInt(2),
      questionId: "D001Q02",
      korean: "저는 보통 버스를 타고 출근해요.",
      referenceAnswer: "I usually take the bus to work.",
      difficulty: NumberInt(2)
    },
    {
      questionNumber: NumberInt(3),
      questionId: "D001Q03",
      korean: "저는 작년보다 올해 책을 더 많이 읽어요.",
      referenceAnswer: "I read more books this year than last year.",
      difficulty: NumberInt(2)
    }
  ]
}
```

저장·조회 규칙:

- document 하나는 `dayNumber` 한 개와 정확히 세 문제를 가진다.
- 문제 번호는 정수 `1`, `2`, `3`을 각각 한 번씩 포함한다.
- `questionId`는 서버·AI·운영 추적용 stable opaque ID다. `D001Q01` 문자열 형식을 파싱해 날짜나 번호를 추론하지 않는다.
- `korean`은 공개 `promptKo`로 변환한다.
- `referenceAnswer`는 문제 조회, attempt 생성과 upload-url 응답에서 숨기고 SUBMITTED 또는 EXPIRED terminal 이후에만 공개한다.
- `difficulty`는 의미·scale을 해석하지 않고 Mongo integer 값을 공개 문제 DTO와 terminal 결과 DTO에 그대로 전달한다. 프론트는 이 값을 표시 metadata로만 사용하고 Request에 다시 보내지 않는다.
- `difficulty`는 AI 요청·grading job payload에 포함하지 않는다. 과거 결과 재현을 위해 attempt snapshot에는 저장한다.
- Mongo `_id`, `dayNumber`, `questionId`는 프론트 Request/Response 계약에 추가하지 않는다.
- published day document는 과거 결과 재현성을 위해 임의 수정하지 않고 append-only seed/migration으로 관리한다.

필수 index와 startup/catalog 검증:

- `dayNumber` unique index
- `questions.questionId` unique multikey index 또는 동일 수준의 migration 사전 중복 검사
- 양의 정수 dayNumber, non-empty questions, 정확한 문제 수 3, questionNumber 집합 `{1,2,3}`
- document 내부·전체 catalog의 questionId 중복, blank `korean`·`referenceAnswer`, BSON integer가 아닌 difficulty를 배포 전 거절한다. difficulty 값의 범위·의미는 검증하지 않는다.
- production index는 startup auto-create에 의존하지 않고 migration으로 생성한 뒤 서버가 정의를 검증한다.

날짜 선택 정책은 다음으로 확정한다.

- metadata collection `challenge_10s_catalog_state`에 singleton `_id="active:v1"`, KST `YYYY-MM-DD` 문자열 `contentBaseDate`, `zoneId="Asia/Seoul"`, UTC `initializedAt`을 저장한다.
- `app.challenge.enabled=true`로 처음 성공 기동하는 instance가 server `Clock`과 `Asia/Seoul`로 계산한 그날 날짜를 `contentBaseDate`로 원자 `setOnInsert`한다. 먼저 dayNumber 1 catalog가 유효한지 확인한다.
- 여러 instance가 동시에 기동하면 Mongo `_id` unique와 atomic upsert로 한 날짜만 승자가 되고 모든 instance가 저장된 값을 다시 읽는다.
- feature가 꺼진 배포는 기준일을 만들지 않는다. 재시작·재배포·scale-out은 기존 metadata를 절대 갱신하거나 초기화하지 않는다.
- `dayNumber = daysBetween(contentBaseDate, challengeDate) + 1`로 계산한다. `contentBaseDate` 당일이 dayNumber 1이다.
- dayNumber는 순환하지 않는다. 해당 document가 없으면 modulo·random·이전 문제 fallback 없이 `404 CHALLENGE_CONTENT_NOT_FOUND`와 운영 alert로 fail-closed한다.
- 운영자가 기준일을 바꾸려면 자동 재초기화가 아니라 별도 승인된 migration을 사용한다.

ChallengeAttempt 생성 시 최소 `dayNumber`, `questionId`, `questionNumber`, `korean`, `referenceAnswer`, `difficulty` snapshot을 저장한다. 이후 catalog가 변경돼도 제출·과거 결과는 attempt snapshot을 사용한다. AI 요청에는 snapshot 중 difficulty를 제외한다.

## 4. 상태 모델

### 일일 상태

```text
not_started: 세 문제 모두 시작 전
in_progress: 하나 이상 시작했고 아직 terminal이 아닌 문제가 존재
completed: 세 문제가 submitted 또는 expired로 terminal
```

AI 피드백 완료는 일일 진행 완료와 분리한다. 사용자는 세 답안 행위를 끝냈지만 피드백은 잠시 processing일 수 있다.

### 문제 응시 상태

```text
공개: not_started → submitted
내부: NOT_CREATED → CREATED → UPLOAD_READY/UPLOADING → SUBMITTED 또는 EXPIRED
```

프론트는 공개 상태만으로 챌린지 화면과 결과 화면을 선택한다. 내부 생성·업로드 중은 공개 `not_started`, 정상 제출·무음·1시간 만료 terminal은 공개 `submitted`로 projection한다. 내부 상태는 Presigned URL 재발급, 멱등 submit과 deadline 처리를 위해 유지한다.

### AI 채점 상태

```text
not_requested → pending → processing → completed
                                ↘ failed
```

expired 문제는 `not_requested`를 유지한다.

### 하루가 바뀔 때

- 기준 시간대는 `Asia/Seoul`이다.
- 오늘 진행도는 `challengeDateExpiresAt`과 server 계산 `expiresInSeconds`를 반환한다.
- client는 만료 timer와 foreground 복귀 시 진행도를 재조회한다.
- question·attempt 요청은 `X-Challenge-Date`를 받고 server가 처리 직전 현재 KST 날짜와 비교한다.
- 날짜가 바뀌었으면 mutation을 수행하지 않고 `CHALLENGE_DATE_CHANGED`와 최신 날짜 정보를 반환한다.
- 과거 날짜의 시작하지 않은 문제는 새로 응시할 수 없다.
- 자정 직전 생성된 attempt는 생성 당시 challengeDate에 고정한다. `submissionDeadlineAt=attemptCreatedAt+1시간`이며 자정을 지나도 이 시각까지 이전 날짜 제출을 허용하고, 이후에는 `CHALLENGE_ATTEMPT_EXPIRED`로 거절한다. 자정 이후 이전 날짜의 새 attempt 생성은 허용하지 않는다.

## 5. Client timer와 한 번 응시를 지키는 규칙

- 녹음 길이 최대 10초와 자동 종료는 client UX 계약이며 server는 audio duration을 검증하지 않는다.
- server는 변조된 client가 10초를 지켰는지 증명할 수 없다. 경제적 reward·랭킹·경쟁이 없는 MVP에서 이 위험을 수용한다.
- 프론트가 녹음 시작 전에 attempt를 생성하고, 최대 10초 녹음이 끝난 뒤 동일 attemptId로 upload URL을 발급받아 S3 upload와 completion을 수행한다.
- 앱 재실행·URL 재발급은 같은 attempt를 사용한다.
- 10초에 녹음이 끝난 audio는 정상 제출한다. 발화가 없으면 AI 결과에서 `no_speech`로 구분한다.
- 1시간 안에 제출하지 못한 attempt만 server가 `expired` terminal로 처리한다.
- 문제는 1→2→3 순서로만 시작한다. 앞 문제의 공개 상태가 submitted여야 다음 문제를 시작할 수 있다.
- Mongo unique key는 최소 `(userId, challengeDate, questionNumber)`다.
- userId는 JWT `sub`에서 가져오고 Request/Response에 임의 추가하지 않는다.

## 6. 추가 확정 완료 사항

1. `aiResult`에는 DB `referenceAnswer` snapshot과 AI transcript·verdict·correctedAnswer·feedback을 포함한다.
2. no-speech는 `gradingStatus=completed`, non-null `aiResult`, non-blank `referenceAnswer`와 나머지 null AI field로 표현한다.
3. MEMBER 전용이며 Guest preview는 제공하지 않는다.
4. 기존 `challengeDateExpiresAt`, `expiresInSeconds`, `X-Challenge-Date`, `CHALLENGE_DATE_CHANGED` rollover 보호안을 승인한다.
5. foreground polling은 최대 60초이며 이후 Job을 취소하지 않고 준비 중 UX로 전환한다.
6. MVP에서는 사용자 audio 재생과 `audioUrl`을 제공하지 않는다.
7. AI transcript·corrected answer는 각 1000자, feedback 각 500자, Callback JSON 전체는 16 KiB로 제한한다.

## 7. 권장 MVP 승인 패키지

- 한국어 문장 → 사용자가 영어 문장을 만들어 발음한 audio
- MEMBER 전용, KST 기준 전 사용자 공통 3문제
- 순차 진행 1→2→3
- client가 녹음 길이를 최대 10초로 제한하고 server는 audio duration을 판정하지 않음
- 녹음·업로드 형식은 `.m4a` M4A 컨테이너·AAC 코덱·`Content-Type: audio/mp4`
- attempt는 생성 시점부터 1시간 동안 제출할 수 있고 생성 당시 challengeDate에 귀속
- attempt 생성 API는 녹음 시작과 challengeDate·deadline을 고정하고 server-generated S3 key를 내부에 연결
- 녹음 완료 후 별도 upload-url API에서 동일 attempt의 S3 Presigned PUT URL 발급·재발급
- 10초 녹음 종료는 정상 제출하고 제출 접수 즉시 참고 답안을 반환
- 무음과 1시간 미제출도 별도 공개 상태나 `feedbackType`을 늘리지 않고 `gradingStatus`, nullable AI 결과와 안내 문구로 표현
- 하루 completed는 세 문제의 공개 attemptStatus가 submitted일 때
- AI feedback은 비동기, 숫자 점수 없이 transcript·verdict·corrected answer·의미/문법/발음의 짧은 feedback
- `yearMonth` 기준 날짜별 참여 여부·풀이 수를 pagination 없이 조회
- 특정 날짜 결과는 optional `questionNumber`가 없으면 풀이 수만, 있으면 날짜 전체 풀이 수와 해당 문제 단일 상세를 조회
- `to-teacher-app`의 `challenge_10s_questions`를 append-only seed/migration으로 게시하고 dayNumber catalog를 검증
- 첫 challenge feature 활성화 KST 날짜를 `challenge_10s_catalog_state`에 한 번만 저장하고 비순환 dayNumber를 계산
- difficulty는 공개 문제·결과 DTO에 정수 그대로 전달하되 AI 요청에서는 제외

## 8. 2026-09-07 구현 전 보완 승인

후속 사용자 승인으로 남은 AI 종료 경계도 확정했다. 각 Job의 최초 dispatch부터 접수 전 전송 총 시간은 5분(대기/backoff 포함)이며 재시작·재전송으로 연장하지 않는다. 예산 소진 시 최종 채점 실패로 종료하고 새 generation으로 우회하지 않는다. 마지막 generation timeout을 포함해 최종 실패 후의 유효 late Callback은 204 no-op으로 처리하고 실패 상태를 유지한다. 기존 3초 연결·15초 접수·120초 결과 대기·최대 3 generation 및 AI→LC Callback 전달 최대 10회는 유지한다. 세부 backoff·검증 우선순위·경합 처리는 구현 계획 C.2와 AI v1 7~8절을 따른다. 이는 사용자 정책 확정이며 AI 팀 적용·운영 배포 완료를 뜻하지 않는다.

사용자가 권장안을 승인했다. 아래는 Learning Core의 구현 기준이며 Identity 변경 구현과 프론트·AI의 보완 계약 적용 확인은 남아 있다. 상세 순서와 검증은 [구현 계획](TEN_SECOND_CHALLENGE_IMPLEMENTATION_PLAN.md)을 따른다.

1. Identity가 모든 사용자 Access Token 발급·재발급에서 현재 계정 상태에 따른 `account_type=MEMBER|GUEST`를 발급한다. Learning Core는 Challenge에서 MEMBER만 허용하고 누락·GUEST·알 수 없는 값은 403이다. 프로필 `accountType`과 이름을 혼동하지 않는다. 기존 시험 인증 계약은 유지한다.
2. 요청 경계의 lazy expiration과 bounded scheduler를 함께 사용한다. `now >= submissionDeadlineAt`인 미제출 attempt만 CAS로 EXPIRED 전이하며 submit과의 경합은 terminal 상태 하나로 수렴한다. 이미 성공한 제출 replay를 만료로 덮어쓰지 않는다. 읽기와 다음 문제 진행은 scheduler 실행 지연에 의존하지 않는다.
3. Challenge Callback은 방향별 opaque Bearer 계약을 유지한다. exact path 전용 chain을 항상 등록하고 기존 catch-all보다 먼저 검사한다. 기능 OFF이면 deny-all이며 local/test Legacy에서도 인증을 우회하지 않는다. 사용자 endpoint도 Legacy permit-all로 MEMBER 검증을 우회할 수 없다.
4. Learning Core는 S3 metadata와 상한을 검사하고 AI가 실제 media profile을 검증한다. submit 415와 비동기 failed의 차이를 프론트·AI 계약에 명시한다.
5. ID는 구조화 로그에만 허용하고 metric tag는 고정 enum만 사용한다. 2 MiB 임시값 문구를 제거하고 `CHALLENGE_DATE_CLOSED`를 삭제한다. history는 baseDate 이전 날짜를 제외하며 프론트가 없는 날짜를 결석으로 채우지 않는다.
6. Identity 발급 변경 선배포와 모든 구버전 발급 instance drain 뒤 마지막 구형 Token의 최대 TTL+허용 skew가 지나야 Challenge를 활성화한다. 새 Token에는 claim이 있고 Guest upgrade·refresh·merge 이후에도 현재 계정 유형이 반영되는지 검증한다. 프론트는 기존 토큰 교체 흐름을 사용한다.
