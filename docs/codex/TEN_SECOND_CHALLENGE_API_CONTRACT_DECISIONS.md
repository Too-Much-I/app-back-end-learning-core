# 10초 영작 챌린지 API·상태 계약 결정서

프론트 전달용 요청·응답 명세는 `docs/contracts/ten-second-challenge-frontend-api.md`를 따른다. 해당 문서는 아직 구현 전 Draft이며 이 결정서의 미확정 항목을 승인한 뒤 v1로 동결한다.

- 작성일: 2026-08-25
- Jira: 없음
- 상태: M4A/AAC·녹음 길이 최대 10초·순차 진행·전 사용자 공통 3문제·attempt 5분 제출 유효시간 확정, sample rate·channel·최대 크기·AI feedback 세부 계약 승인 필요

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

### 2.2 응시 생성·S3 업로드 URL 발급 — 추가 필요

서버가 10초를 판정하지 않으므로 deadline을 만드는 start API는 필요하지 않다. 하지만 S3 직접 업로드와 문제당 1회, server `in_progress` 상태를 연결할 attempt 생성 API는 필요하다.

권장 계약:

```text
POST /api/v1/challenges/today/questions/{questionNumber}/attempt
X-Challenge-Date: YYYY-MM-DD
```

응답:

- `attemptId`
- `challengeDate`
- `questionNumber`
- server가 결정한 S3 object key에 대한 presigned PUT URL
- upload URL 만료 시각
- 공개 `attemptStatus=not_started`

같은 문제의 재호출은 새 attempt를 만들지 않고 기존 attempt에 대한 새 presigned URL만 발급할 수 있다. URL 재발급은 사용자 재응시로 계산하지 않는다.

`X-Challenge-Date`가 현재 server KST 날짜와 다르면 새 attempt를 만들지 않고 `CHALLENGE_DATE_CHANGED`로 오늘 진행도 재조회를 유도한다.

선택적으로 다음 read API를 둘 수 있다.

```text
GET /api/v1/challenges/today/questions/{questionNumber}
```

- 문제와 prompt를 조회한다. 이 GET 자체는 server 상태를 변경하지 않는다.
- 기존 attempt가 있으면 attempt 상태만 함께 반환할 수 있다.
- 제출·만료 후에는 현재 상태와 result link 가능 여부를 반환한다.

### 2.3 문제 답변 제출 — 필요, timeout UX 재검토

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

업로드 artifact는 `.m4a` 확장자의 M4A 컨테이너와 AAC 코덱으로 확정됐다. S3 PUT과 object metadata의 canonical `Content-Type`은 `audio/mp4`이며 server-generated object key도 `.m4a`를 사용한다. 구현 전에 sample rate·channel과 최대 크기를 추가로 고정하고 AI 서버가 M4A/AAC를 직접 처리하거나 내부 변환하도록 계약해야 한다.

### 2.3.1 10초 종료와 피드백 UX 재검토

기존 `10초 종료 → timed_out → AI 미요청 → 다음 문제` 모델은 사용자가 실제로 발화했는데도 답안과 피드백을 받지 못할 수 있어 채택하지 않는 것을 권장한다.

권장 모델:

1. 앱은 10초에 녹음을 자동 종료한다.
2. 녹음된 audio는 길이·발화 유무와 관계없이 S3에 업로드한다.
3. 제출 접수 즉시 참고 영어 문장을 반환하고 다음 문제를 연다.
4. AI는 비동기로 transcript·교정·발음 피드백을 생성한다.
5. 무음이면 `transcript=null`과 참고 답안·재시도 학습 안내를 결과로 저장하며 별도의 공개 `feedbackType` enum은 추가하지 않는다.
6. AI가 실패해도 참고 답안과 사용자 제출 상태는 유지하고 자동 복구한다.
7. 5분 제출 유효시간까지 S3 제출 자체를 못 한 경우만 `expired` terminal로 처리하고 참고 답안만 제공한다.

따라서 client의 `outcome=timed_out`은 제거하고 정상 submit과 server-controlled expiry를 분리하는 것이 권장안이다. 공개 `attemptStatus`는 두 경우 모두 terminal인 `submitted`로 projection해 다음 문제와 결과 화면을 열며 AI 완료까지 기다리지 않는다.

### 2.4 월별 참여 이력 조회 — 필요

권장 계약:

```text
GET /api/v1/challenges/history?yearMonth=YYYY-MM
```

요청한 월의 각 날짜에 다음을 반환한다.

- `challengeDate`
- `participated`: 공개 `attemptStatus=submitted`가 한 건 이상인지
- `solvedQuestionCount`: 공개 `attemptStatus=submitted` 문제 수 0~3

정상 audio 제출, 무음과 5분 만료 terminal은 프론트에서 구분하지 않고 풀이 수에 포함한다. attempt 생성만 하고 아직 terminal이 아닌 공개 `not_started` 문제는 포함하지 않는다. 과거 월은 월 전체 날짜, 현재 월은 KST 오늘까지 반환하며 미래 월은 거절한다.

월 단위 최대 31건이므로 cursor, size와 pagination은 사용하지 않는다.

### 2.5 특정 날짜 풀이 결과 조회 — 필요

권장 계약:

```text
GET /api/v1/challenges/{challengeDate}/results?questionNumber={optional}
```

규칙:

- `questionNumber`가 없으면 `challengeDate`와 `solvedQuestionCount`만 반환한다.
- `questionNumber`가 있으면 날짜 전체 풀이 수와 해당 문제 상세 단일 `question` 객체를 반환한다.
- 지정 문제를 풀지 않았으면 `question=null`이고 날짜 전체 풀이 수는 유지한다.
- `solvedQuestionCount`는 query 유무와 무관하게 날짜 전체 풀이 수다.

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
- AI에는 실제 userId를 보내지 않고 `attemptId`, challenge/question 식별값, 한국어 prompt와 server가 생성한 S3 audio object reference만 전달한다.
- 결정적 Job ID와 callback idempotency를 사용한다.
- AI timeout은 서버가 자동 재시도한다.
- 최종 AI 실패여도 사용자 answer attempt는 유지하고 재답변은 허용하지 않는다.
- 사용자용 grading retry API는 MVP에 두지 않고 자동 복구·운영 retry로 시작한다.

### 콘텐츠 등록

프론트 API는 아니지만 하루 3문제를 누가 어떻게 등록·게시하는지 필요하다.

초기 선택지:

- 운영자가 Mongo seed/migration으로 날짜별 3문제 게시 — MVP 권장
- Admin API·관리 화면 구현 — 운영 편의는 좋지만 범위 증가
- 문제 bank에서 서버가 매일 자동 추첨 — 중복·난이도·배포 시각 정책 필요

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
내부: NOT_CREATED → CREATED/UPLOADING → SUBMITTED 또는 EXPIRED
```

프론트는 공개 상태만으로 챌린지 화면과 결과 화면을 선택한다. 내부 생성·업로드 중은 공개 `not_started`, 정상 제출·무음·5분 만료 terminal은 공개 `submitted`로 projection한다. 내부 상태는 Presigned URL 재발급, 멱등 submit과 deadline 처리를 위해 유지한다.

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
- 자정 직전 생성된 attempt는 생성 당시 challengeDate에 고정한다. `submissionDeadlineAt=attemptCreatedAt+5분`이며 자정을 지나도 이 시각까지 이전 날짜 제출을 허용하고, 이후에는 `CHALLENGE_ATTEMPT_EXPIRED`로 거절한다. 자정 이후 이전 날짜의 새 attempt 생성은 허용하지 않는다.

## 5. Client timer와 한 번 응시를 지키는 규칙

- 녹음 길이 최대 10초와 자동 종료는 client UX 계약이며 server는 audio duration을 검증하지 않는다.
- server는 변조된 client가 10초를 지켰는지 증명할 수 없다. 경제적 reward·랭킹·경쟁이 없는 MVP에서 이 위험을 수용한다.
- 프론트가 녹음 시작 전에 attempt와 upload URL을 준비하고, 최대 10초 동안 녹음한 뒤 S3 upload와 completion을 수행한다.
- 앱 재실행·URL 재발급은 같은 attempt를 사용한다.
- 10초에 녹음이 끝난 audio는 정상 제출한다. 발화가 없으면 AI 결과에서 `no_speech`로 구분한다.
- 5분 안에 제출하지 못한 attempt만 server가 `expired` terminal로 처리한다.
- 문제는 1→2→3 순서로만 시작한다. 앞 문제의 공개 상태가 submitted여야 다음 문제를 시작할 수 있다.
- Mongo unique key는 최소 `(userId, challengeDate, questionNumber)`다.
- userId는 JWT `sub`에서 가져오고 Request/Response에 임의 추가하지 않는다.

## 6. 아직 확정할 사항

1. sample rate, channel과 최대 파일 크기
2. AI 결과 필드: transcript·verdict·corrected answer·의미/문법/발음 feedback, 숫자 점수 필요 여부
3. AI 피드백 최종 실패 화면과 운영 retry 정책
4. 문제 게시 방식과 게시 누락 시 동작
5. MEMBER 전용인지 Guest preview를 허용할지
6. `challengeDateExpiresAt`, `expiresInSeconds`, `X-Challenge-Date`와 `CHALLENGE_DATE_CHANGED` rollover 보호 계약 최종 승인
7. 프론트 foreground polling 상한과 서버 grading job timeout·최대 자동 재시도·최종 `failed` 전환 시간

## 7. 권장 MVP 승인 패키지

- 한국어 문장 → 사용자가 영어 문장을 만들어 발음한 audio
- MEMBER 전용, KST 기준 전 사용자 공통 3문제
- 순차 진행 1→2→3
- client가 녹음 길이를 최대 10초로 제한하고 server는 audio duration을 판정하지 않음
- 녹음·업로드 형식은 `.m4a` M4A 컨테이너·AAC 코덱·`Content-Type: audio/mp4`
- attempt는 생성 시점부터 5분 동안 제출할 수 있고 생성 당시 challengeDate에 귀속
- attempt 생성 API에서 server-generated S3 key와 presigned URL 발급
- 10초 녹음 종료는 정상 제출하고 제출 접수 즉시 참고 답안을 반환
- 무음과 5분 미제출도 별도 공개 상태나 `feedbackType`을 늘리지 않고 `gradingStatus`, nullable AI 결과와 안내 문구로 표현
- 하루 completed는 세 문제의 공개 attemptStatus가 submitted일 때
- AI feedback은 비동기, 숫자 점수 없이 transcript·verdict·corrected answer·의미/문법/발음의 짧은 feedback
- `yearMonth` 기준 날짜별 참여 여부·풀이 수를 pagination 없이 조회
- 특정 날짜 결과는 optional `questionNumber`가 없으면 풀이 수만, 있으면 날짜 전체 풀이 수와 해당 문제 단일 상세를 조회
- 날짜별 콘텐츠는 초기 seed/migration으로 게시
