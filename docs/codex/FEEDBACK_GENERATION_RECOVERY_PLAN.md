# 종합 피드백 빈 결과 감지 및 선택적 재생성 계획

## 1. 목적

Python AI의 종합 피드백 Callback이 도착했더라도 `part_feedback`이 빈 객체이면 유효한 종합 피드백으로 저장하거나 시험을 완료하지 않는다.

프론트에는 기존 조회 API를 통해 다음 오류를 전달한다.

```text
code=FEEDBACK_GENERATION_FAILED
message=피드백 생성에 실패했습니다.
```

프론트가 기존 `POST /api/v1/exams/{examId}/grading/retry`를 호출하면 Learning Core가 최초 응시 문항 결과를 다시 확인한다.

- 필수 문항 결과가 모두 있으면 문항별 AI 채점을 다시 보내지 않고 종합 피드백만 재생성한다.
- 결과가 누락된 문항이 있으면 종합 피드백을 보내지 않고 해당 문항부터 복구한다.
- 누락 문항 복구가 끝나면 같은 사용자 재시도 의도를 이어받아 종합 피드백을 한 번만 생성한다.
- 종합 피드백 재생성도 실패하면 `FAILED`로 돌아가며 프론트가 같은 API로 다시 시도할 수 있다.

## 2. 현재 구현 확인 결과

### 2.1 빈 `partFeedback`도 성공 처리된다

`ExamServiceImpl.updateExamResult()`는 `suggested_total_score`가 있으면 Summary Callback으로 분류한다. 현재는 `partFeedback`의 null/empty 여부를 검사하지 않고 `ExamSummary`를 저장한 다음 다음 작업을 모두 수행한다.

1. `ExamSessionManager.completeIfIncomplete(examId)`
2. `ExamGradingService.completeSummary(examId)`
3. 전체 상태 재계산

따라서 `part_feedback: {}`도 `ExamSummary` 저장과 시험 `COMPLETED`로 이어질 수 있다.

### 2.2 재시도 API의 큰 분기는 이미 목표와 유사하다

`ExamGradingService.retryExam()`은 배정된 문제지의 필수 문항을 순회한다.

- 최초 응시 결과가 있으면 문항 재전송을 생략한다.
- Job이 없으면 기존 S3 Object 존재 여부를 확인한 뒤 복구한다.
- FAILED 또는 timeout Job이면 문항 채점을 다시 보낸다.
- 문항 작업이 하나라도 남으면 Summary 전송을 보류한다.
- 모든 문항이 끝났을 때만 Summary Job을 재시도한다.

이 구조는 유지하고 완료 근거와 Summary 재무장 방식만 보강한다.

### 2.3 실제 결과가 없어도 COMPLETED Job으로 Summary가 시작될 수 있다

현재 `QuestionCompletionSnapshot.isComplete()`는 다음 둘 중 하나를 완료로 인정한다.

- 최초 응시 `ExamResult`가 존재함
- `QuestionGradingJob.status == COMPLETED`

이번 요구사항에서 Summary 생성의 근거는 실제 문항 결과 저장이다. 따라서 Job만 COMPLETED이고 `ExamResult`가 없으면 완료로 인정하면 안 된다.

### 2.4 반복 재생성과 stale 작업 차단에 공백이 있다

- Summary Job의 `dispatchAttempt`가 `maxDispatchAttempts`에 도달하면 현재 재시도는 더 이상 전송하지 않는다.
- Summary 재시도도 동일한 `Idempotency-Key=summary:<examId>:v1`을 사용한다. Python AI가 이 값을 결과 캐시 키로 사용하면 빈 결과가 재사용되어 실제 재생성이 일어나지 않을 수 있다.
- 현재 Summary Request/Callback에는 generation 식별자가 없어 이전 재생성 회차의 늦은 Callback을 현재 회차와 구분할 수 없다.

반복 가능한 종합 피드백 재생성과 stale 작업 차단을 보장하려면 사용자 재생성 회차와 전송 재시도 횟수를 구분하고, Scheduler와 Callback 양쪽에 generation fencing을 적용해야 한다.

## 3. 범위와 호환성 원칙

### 3.1 유지할 계약

- 신규 API를 추가하지 않는다.
- `POST /api/v1/exams/{examId}/grading/retry`는 Request Body 없음 계약을 유지한다.
- 기존 `GradingRetryResult` 필드를 추가·삭제·변경하지 않는다.
- 상태 조회 URL, HTTP Method와 성공 Response DTO 필드를 유지한다.
- `BaseResponse` 구조를 변경하지 않는다.
- AI `user_id=examId`를 유지한다.
- `retryCount` 의미를 변경하지 않고 시험 복구 대상은 최초 응시 `retryCount=0`으로 제한한다.
- Redis Key/TTL, S3 Object Key와 Presigned URL 흐름을 변경하지 않는다.
- 사용자용 API의 기존 소유권 검증을 유지한다.
- AI Callback에는 `CurrentUserProvider` 기반 소유권 검증을 추가하지 않는다.
- Question AI 요청 및 Callback 계약은 변경하지 않는다.

단, Summary 재생성 회차 식별을 위해 Summary AI 요청과 Summary Callback에 다음 wire field를 추가한다.

```json
{
  "generation_attempt": 2
}
```

- `generationAttempt`의 생성과 증가는 Learning Core만 담당한다.
- Python AI는 전달받은 값을 수정하거나 자체 증가시키지 않는다.
- Python AI는 Summary Callback에 전달받은 값을 그대로 반환한다.
- Learning Core는 Callback generation과 현재 `SummaryGradingJob.generationAttempt`가 일치할 때만 상태와 Summary 저장 결과를 변경한다.
- 기존 Summary 필드는 변경하거나 삭제하지 않는다.

### 3.2 명시적으로 달라지는 외부 동작

빈 종합 피드백이 감지된 신규 실패 상태에 한해 프론트 조회가 성공 응답 대신 다음 기존 `BaseResponse` 오류 형태를 받는다.

```json
{
  "isSuccess": false,
  "code": "FEEDBACK_GENERATION_FAILED",
  "message": "피드백 생성에 실패했습니다."
}
```

프론트 공개 DTO에는 새 필드를 추가하지 않는다. Summary AI Request/Callback의 `generation_attempt`와 위 오류 응답은 사용자가 명시적으로 요청한 외부 동작 변경 범위다.

## 4. 유효성 기준

### 4.1 종합 피드백

다음이면 종합 피드백 생성 실패로 본다.

```java
partFeedback == null || partFeedback.isEmpty()
```

`null`도 JSON 필드 누락에 따른 동일한 실패이므로 빈 객체와 함께 거절한다.

이번 범위에서는 비어 있지 않은 Map의 part 개수, key 이름, value 공백 여부까지 새로 검증하지 않는다. 기존 AI 응답 호환성을 유지하기 위해 부분 Map은 기존과 동일하게 허용한다.

### 4.2 필수 문항 결과

필수 문항 번호의 기준은 프론트 입력이나 별도 `1..11` 상수가 아니라 시험 세션에 배정된 `MockExam.questions`다.

현재 TOEIC Speaking 문제지는 계약 테스트로 번호가 정확히 1번부터 11번까지인지 고정한다. 다른 문제지가 추가되어도 서버 카탈로그가 단일 진실 공급원이 된다.

각 필수 문항은 다음 실제 저장 근거가 있어야 완료다.

```text
ExamResult.examId == 대상 examId
ExamResult.questionNumber == 필수 문항 번호
ExamResult.retryCount == 0 또는 legacy null
```

Part별 결과 필드 차이가 있으므로 score나 feedback의 특정 하위 필드를 새 필수 조건으로 만들지 않는다. `QuestionGradingJob=COMPLETED`만 있고 결과 문서가 없으면 완료가 아니다.

## 5. 목표 상태 전이

| 시점 | Summary Job | ExamSession | 전체 상태 | Summary 저장 |
|---|---|---|---|---|
| 정상 Summary 처리 중 | `PROCESSING` | `IN_PROGRESS` | `PROCESSING` | 없음 |
| generation N의 `partFeedback` null/empty | generation N `FAILED`, reason=`FEEDBACK_GENERATION_FAILED` | `IN_PROGRESS` 유지 | `FAILED` | 저장하지 않음 |
| 프론트 retry, 누락 문항 있음 | generation N+1 재생성 의도로 `PENDING`, `dispatchAttempt=0` | `IN_PROGRESS` | 문항 상태에 따라 `PENDING/PROCESSING` | 없음 |
| 누락 문항 복구 완료 | `PENDING → PROCESSING` | `IN_PROGRESS` | `PROCESSING` | 없음 |
| 모든 문항 결과가 이미 있음 | 문항 전송 없이 `PENDING → PROCESSING` | `IN_PROGRESS` | `PROCESSING` | 없음 |
| 현재 generation의 유효한 Summary Callback | `COMPLETED` | `COMPLETED` | `COMPLETED` | 1건 |
| 이전 generation Callback | 현재 generation 변경 없음 | 변경 없음 | 변경 없음 | 저장하지 않음 |
| 재생성 Callback도 null/empty | 현재 generation `FAILED` | `IN_PROGRESS` 유지 | `FAILED` | 저장하지 않음 |

`FAILED`는 `ExamSessionStatus`에 새 값을 추가한다는 뜻이 아니다. `SummaryGradingJob`과 기존 `ExamStatus`의 `FAILED`를 사용하고, 세션은 재시도를 허용하기 위해 `IN_PROGRESS`로 유지한다.

## 6. 상세 구현 계획

### 6.1 ErrorCode 추가

`ErrorStatus`에 다음 항목을 추가한다.

```java
_FEEDBACK_GENERATION_FAILED(
    HttpStatus.INTERNAL_SERVER_ERROR,
    "FEEDBACK_GENERATION_FAILED",
    "피드백 생성에 실패했습니다."
)
```

예상 가능한 AI 결과 품질 실패이므로 `ExamsException` 비즈니스 오류 경로로 처리한다. 현재 Sentry 정책상 예상하지 못한 Exception으로 수집하지 않고 안전한 CloudWatch 로그만 남긴다.

### 6.2 Summary Callback 검증 순서 변경

`ExamServiceImpl.updateExamResult()`의 Summary 분기에서는 저장 및 상태 변경 전에 generation과 `partFeedback`을 검증한다.

```text
Summary Callback 식별
        ↓
현재 SummaryGradingJob 조회
        ↓
callback generationAttempt와 현재 generationAttempt 비교
        ├─ 불일치 또는 값 누락 → stale Callback no-op
        └─ 일치
             ↓
        이미 유효한 Summary가 저장됐는지 확인
             ├─ 있음 → 중복 Callback no-op, COMPLETED 상태 수렴
             └─ 없음
                  ↓
             partFeedback 유효성 검사
                  ├─ null/empty → 현재 generation의 Summary Job FAILED
                  │               reason=FEEDBACK_GENERATION_FAILED
                  │               ExamSession 완료 금지
                  │               ExamSummary 저장 금지
                  │               전체 상태 FAILED 반영
                  └─ non-empty → 기존 멱등 저장 및 완료 처리
```

현재 Job이 없으면 비교할 generation이 없으므로 Callback에서 Job을 새로 만들지 않고 no-op한다. Callback generation이 현재 Job generation과 다르면 성공/실패 여부와 관계없이 현재 generation의 상태나 Summary 저장 결과를 변경하지 않는다.

```text
현재 generationAttempt = 2

generation 1 empty Callback → stale no-op
generation 1 valid Callback → stale no-op
generation 2 Callback       → 정상 처리
```

동일 generation 안에서는 기존 단조 상태 전이를 유지한다.

- 유효한 Summary 저장 결과가 존재하면 `COMPLETED`가 우선한다.
- 이미 `COMPLETED`인 Job은 뒤늦은 empty Callback으로 `FAILED`가 되지 않는다.
- valid/empty Callback 경합은 optimistic locking과 재조회로 최종 유효 결과에 수렴한다.

### 6.3 Summary 실패 기록

`ExamGradingService`에 다음 의미의 메서드를 둔다.

```text
failSummaryGeneration(
    examId,
    generationAttempt,
    FEEDBACK_GENERATION_FAILED
)
```

실패 상태 변경에는 다음 generation fencing 조건을 포함한다.

```text
jobId == summary:<examId>:v1
AND generationAttempt == callbackGenerationAttempt
AND status IN (PENDING, PROCESSING, FAILED)
```

- Callback generation이 현재 generation과 다르면 no-op한다.
- 유효한 Summary가 이미 있으면 no-op한다.
- 현재 generation의 Job이 `COMPLETED`면 `FAILED`로 되돌리지 않는다.
- 현재 generation의 Job이 `PROCESSING/PENDING/FAILED`이면 optimistic locking 또는 generation 조건부 update로 `FAILED`와 실패 사유를 저장한다.
- 상태 저장 뒤 기존 Redis Key/TTL에 전체 `FAILED` 상태를 projection한다.

빈 `partFeedback` 원문이나 전체 Callback payload는 로그에 남기지 않는다.

### 6.4 AI Callback delivery 응답

stale Callback no-op 또는 빈 Summary 실패 상태가 DB에 안전하게 반영된 뒤에는 기존 Callback delivery 응답 계약을 유지한다. 프론트 재시도 신호는 Callback 응답이 아니라 6.11의 기존 사용자 조회 API에서 제공한다.

### 6.5 retry 시작 시 Summary 재생성 의도 저장

사용자가 기존 retry API를 호출했을 때 `generationAttempt` 증가는 다음 조건을 모두 만족하는 명시적 재생성에서만 발생한다.

```text
SummaryGradingJob.status == FAILED
AND failureReason == FEEDBACK_GENERATION_FAILED
AND 사용자 grading/retry 요청
```

위 조건을 만족할 때만 다음 상태로 원자적으로 재무장한다.

```text
generationAttempt = generationAttempt + 1
dispatchAttempt = 0
status = PENDING
failureReason = null
```

이 PENDING 전환은 사용자 재생성 의도를 durable하게 남긴다. 같은 retry 호출에서 누락 문항이 발견되어도 Summary를 즉시 보내지 않고, 마지막 실제 문항 결과 Callback의 `ensureSummaryStartedIfReady()`가 PENDING Summary를 한 번 예약한다.

다음 경우에는 generation을 증가시키지 않는다.

- transport timeout
- AI 요청 전송 실패
- 동일 generation 안의 dispatch 재시도
- `PENDING/PROCESSING` 상태에 대한 동시 retry
- Question 복구를 위한 재전송

```text
generationAttempt = AI 결과를 새로 생성하는 논리적 회차
dispatchAttempt   = 동일 generation 안에서 Summary 요청을 전달하는 시도 횟수
```

동시 retry에서는 optimistic locking 또는 조건부 update로 한 요청만 `FAILED(generation=N) → PENDING(generation=N+1)`에 성공한다. 나머지는 이미 `PENDING/PROCESSING`인 같은 generation을 확인하고 `WAITING`으로 수렴한다.

### 6.6 문항 결과 누락 복구 정책

Summary 생성 가능 여부는 Question Job 완료 상태가 아니라 실제 최초 응시 `ExamResult` 존재 여부를 기준으로 한다.

```text
QuestionGradingJob.status == COMPLETED
ExamResult 없음
```

위 상태는 `QUESTION_RESULT_MISSING` 데이터 불일치로 분류하고 완료로 인정하지 않는다. Question Job을 원자적으로 다시 연다.

```text
COMPLETED → PENDING
dispatchAttempt = 0
```

기존 dispatch attempt는 완료된 AI 요청의 transport 시도 횟수이므로, 실제 결과가 저장되지 않은 일치성 복구는 별도 복구 사이클로 보고 기존 `maxDispatchAttempts` 정책을 0부터 다시 적용한다.

동시 retry에서는 이전 status와 document version을 조건으로 한 요청만 `COMPLETED → PENDING` 전환에 성공한다. 나머지는 PENDING/PROCESSING 상태를 보고 waiting 처리한다. `dispatchAttempt` 초기화에 따른 stale 상태 갱신을 막기 위해 re-open 이후 Question claim의 document version 또는 별도 내부 recovery cycle도 상태 변경 조건에 포함한다. Question AI Request/Callback wire 계약은 변경하지 않는다.

| 실제 결과 | Question Job | 처리 |
|---|---|---|
| 있음 | 어떤 상태든 | AI 재전송 없음, Job을 COMPLETED로 수렴 |
| 없음 | Job 없음, S3 있음 | Job 생성 후 해당 문항만 전송 |
| 없음 | Job 없음, S3 없음 | `missingSubmissionQuestionNumbers` |
| 없음 | FAILED 또는 timeout | 기존 attempt 정책에 따라 재전송 |
| 없음 | fresh PENDING/PROCESSING | `waitingQuestionNumbers` |
| 없음 | COMPLETED | PENDING으로 원자적 re-open, `dispatchAttempt=0`, 해당 문항 복구 |

`retryCount>0`의 사용자 새 녹음은 시험 전체 복구 대상에 포함하지 않는다. 문항 하나라도 `retried`, `waiting`, `missingSubmission`이면 해당 retry 호출에서 Summary를 즉시 보내지 않는다. 마지막 누락 문항의 실제 `ExamResult` 저장 Callback 이후 PENDING Summary를 시작한다.

### 6.7 Summary generation과 dispatch attempt 분리

`SummaryGradingJob`에 내부 필드 `generationAttempt`를 추가한다.

- 최초 Summary 생성은 generation 1이다.
- `FEEDBACK_GENERATION_FAILED` 후 사용자가 명시적으로 retry한 경우에만 generation을 1 증가시킨다.
- 새 generation에서는 `dispatchAttempt=0`으로 초기화한다.
- 동일 generation의 transport retry는 generation을 변경하지 않고 `dispatchAttempt`만 증가시킨다.
- generation별 기존 `maxDispatchAttempts` 정책을 독립적으로 적용한다.
- 재생성된 Summary도 empty/null이면 현재 generation이 `FAILED/FEEDBACK_GENERATION_FAILED`가 된다.
- 이후 사용자가 다시 retry하면 다음 generation을 연다.
- legacy Mongo 문서에 `generationAttempt`가 없으면 generation 1로 해석한다.

legacy null을 안전하게 처리하기 위해 모든 비교·claim·직렬화는 `effectiveGenerationAttempt()`와 같은 단일 정규화 경계를 사용한다.

```text
generation 1 / dispatch 1 / dispatch 2 → valid Callback
```

은 같은 논리 생성 회차의 transport retry다.

```text
generation 1 → empty Callback → FAILED
사용자 retry
generation 2 / dispatch 1
```

은 새로운 AI 결과 생성이다.

Summary Mongo Job ID와 Summary 결과 ID는 기존 `summary:<examId>:v1`을 유지하고 generation은 Job 내부 필드로 관리한다.

### 6.8 AI Idempotency-Key 및 generation 계약

Summary AI 요청에는 Learning Core가 생성한 `generation_attempt`를 포함한다.

```json
{
  "user_id": "<examId>",
  "generation_attempt": 2
}
```

Python AI는 값을 계산하거나 증가시키지 않고 Summary Callback에 그대로 반환한다.

```json
{
  "user_id": "<examId>",
  "generation_attempt": 2,
  "part_feedback": {
    "part1": "..."
  }
}
```

Summary `Idempotency-Key`도 generation과 연결한다.

```text
generation 1: summary:<examId>:v1
generation 2: summary:<examId>:v1:generation:2
generation 3: summary:<examId>:v1:generation:3
```

동일 generation 안의 transport retry는 같은 Idempotency-Key를 사용하고, 새 generation만 새 키를 사용한다. Question AI 요청과 idempotency key는 변경하지 않는다.

Java Callback DTO의 내부 필드는 `generationAttempt`로 두고 `@JsonProperty("generation_attempt")`로 wire naming을 고정한다. Question Callback에서는 이 필드를 사용하지 않는다.

### 6.9 Scheduler / Dispatch generation fencing

`SummaryDispatchScheduler`, `SummaryDispatchClaim`, `GradingDispatchService`에서도 generation을 fencing token으로 사용한다.

`SummaryDispatchClaim`은 최소 다음 정보를 가진다.

```text
jobId
generationAttempt
```

필요하면 조건부 갱신을 위한 document version도 함께 가진다. Scheduler가 `PENDING → PROCESSING`을 claim할 때 다음 조건을 원자적으로 확인한다.

```text
jobId == claim.jobId
AND generationAttempt == claim.generationAttempt
AND status == PENDING
```

동일 generation의 timeout/FAILED transport retry claim은 허용된 기존 status와 timeout 조건을 사용하되 generation 일치를 반드시 포함한다. dispatch attempt 증가, timeout 처리, 재시도, 실패 처리 등 이후 상태 변경도 claim generation과 현재 Job generation이 같은 경우에만 반영한다.

```text
claim.generationAttempt != currentJob.generationAttempt
→ stale claim
→ no-op
```

실제 AI 요청 전송 직전에도 현재 generation을 다시 확인한다.

```text
Scheduler A가 generation 1 claim 획득
→ 처리 지연
→ generation 1 FAILED
→ 사용자 retry로 generation 2 PENDING
→ Scheduler A 재개
→ stale generation 1 claim no-op
```

이미 외부 요청이 전송된 뒤 generation이 바뀌면 요청은 취소할 수 없다. 이 경우 Python AI가 generation을 Callback에 그대로 반환하므로 Callback fencing이 현재 generation을 보호한다.

```text
1. Dispatch fencing: stale worker의 상태 변경과 미전송 요청 차단
2. Callback fencing: 이미 전송된 이전 generation 응답 차단
```

### 6.10 성공 완료 순서

유효한 Summary Callback은 다음 순서로 멱등 수렴한다.

1. `ExamSession`에서 실제 userId와 선택된 mockExamId 조회
2. Callback `generationAttempt`와 현재 Summary Job generation 비교
3. generation 불일치 또는 누락이면 stale Callback no-op
4. 동일 generation이면 `partFeedback` non-empty 확인
5. 결정적 ID로 유효 Summary 저장 또는 기존 유효 Summary 확인
6. 동일 generation 조건으로 Summary Job `COMPLETED` 전환
7. ExamSession `COMPLETED` 전환
8. 기존 Redis Key/TTL에 전체 `COMPLETED` projection

중간 실패 후 동일 generation의 valid Callback이 다시 들어오면 저장된 유효 Summary를 근거로 나머지 상태 전이를 완료한다.

1회의 선행 generation 조회만으로는 검증 직후 동시 retry가 새 generation을 여는 경합을 막을 수 없다. 따라서 5번 Summary 저장에 진입하기 전에 `jobId + generationAttempt + status + version` 조건의 completion claim을 원자적으로 획득하거나, 같은 조건부 Job 갱신과 Summary 저장을 Mongo transaction으로 묶는다. claim 또는 transaction을 획득하지 못한 Callback은 저장 없이 stale no-op한다.

transaction을 사용하지 않는 경우에도 동일 generation의 반복 valid Callback이 저장 실패 이후 재수렴할 수 있는 completion recovery 경로를 둔다. 전체 COMPLETED 판정은 Job 상태만이 아니라 유효한 Summary 저장 결과까지 확인해, Job만 COMPLETED이고 Summary가 없는 중간 상태를 외부 완료로 노출하지 않는다.

### 6.11 프론트 오류 노출 계약

빈 Summary 생성 실패는 기존 polling API와 방어적 Summary 조회에서 같은 오류로 전달한다.

```text
GET /api/v1/exams/{examId}/status
GET /api/v1/exams/{examId}/summary
```

HTTP status는 `500 Internal Server Error`를 사용하고 body는 기존 `BaseResponse` 구조를 유지한다.

```json
{
  "isSuccess": false,
  "code": "FEEDBACK_GENERATION_FAILED",
  "message": "피드백 생성에 실패했습니다."
}
```

프론트는 일반적인 HTTP 500 여부만으로 판단하지 않고 `response.code == FEEDBACK_GENERATION_FAILED`를 확인해 기존 `POST /api/v1/exams/{examId}/grading/retry`로 연결한다. 프론트 공통 5xx 처리보다 exact code 분기가 먼저 적용되도록 contract/integration test로 고정한다.

Question Job 실패 등 `FEEDBACK_GENERATION_FAILED` 이외의 기존 5xx/FAILED 처리 방식은 변경하지 않는다.

## 7. 로그 및 모니터링 계획

추가 이벤트 예시는 다음과 같다.

```text
종합 피드백 유효성 검사 실패
event=grading.summary.validation
outcome=failed
reason=empty_part_feedback

종합 피드백 작업 실패 전환
event=grading.summary.job
outcome=failed
reason=FEEDBACK_GENERATION_FAILED

종합 피드백 재생성 준비
event=grading.summary.regeneration
outcome=rearmed
generationAttempt=<number>

종합 피드백 재생성 예약
event=grading.summary.regeneration
outcome=scheduled
generationAttempt=<number>

이전 generation Callback 무시
event=grading.summary.callback
outcome=stale_ignored
callbackGenerationAttempt=<number>
currentGenerationAttempt=<number>

이전 generation dispatch claim 무시
event=grading.summary.dispatch
outcome=stale_claim_ignored
claimGenerationAttempt=<number>
currentGenerationAttempt=<number>
```

로그에는 `examId`, `jobId`, 저카디널리티 reason, generation/dispatch attempt만 사용한다. `partFeedback`, 전체 Callback, Transcript, 실제 userId, Token, URL, S3 Key와 예외 메시지는 기록하지 않는다. generation 값이 없는 구버전 Callback도 payload 없이 `missing_generation` reason으로만 기록한다.

이 실패는 예상 가능한 비즈니스 상태이므로 초기 Sentry Issue 대상이 아니다. CloudWatch에서는 `reason=FEEDBACK_GENERATION_FAILED` 횟수와 재생성 성공률을 집계한다.

## 8. 예상 변경 파일

### 운영 코드

- `src/main/java/web/tosunsaeng/global/error/code/status/ErrorStatus.java`
- `src/main/java/web/tosunsaeng/domain/exams/application/ExamServiceImpl.java`
- `src/main/java/web/tosunsaeng/domain/exams/application/ExamGradingService.java`
- `src/main/java/web/tosunsaeng/domain/exams/application/SummaryDispatchScheduler.java`
- `src/main/java/web/tosunsaeng/domain/exams/application/SummaryDispatchClaim.java`
- `src/main/java/web/tosunsaeng/domain/exams/application/QuestionDispatchClaim.java` 또는 동일한 내부 Question claim fencing 지점
- `src/main/java/web/tosunsaeng/domain/exams/application/GradingDispatchService.java`
- `src/main/java/web/tosunsaeng/domain/exams/dto/ExamRequestDTO.java`
- `src/main/java/web/tosunsaeng/domain/exams/domain/entity/SummaryGradingJob.java`
- `src/main/java/web/tosunsaeng/domain/exams/domain/entity/QuestionGradingJob.java`
- Question/Summary Job Repository의 generation/version 조건부 claim·완료·실패·re-open 메서드

### 테스트

- `FeedbackCallbackServiceTest`
- `ExamGradingServiceTest`
- `SummaryDispatchSchedulerTest`
- `GradingDispatchServiceTest`
- 상태/Summary API의 기존 contract 또는 security integration test
- ErrorStatus·BaseResponse 직렬화 검증이 필요한 경우 관련 집중 테스트

프론트 공개 Request/Response DTO에는 필드를 추가하지 않는다. AI Callback DTO에는 Summary 전용 `generationAttempt` wire mapping만 추가한다.

## 9. 테스트 계획

### 9.1 Callback 유효성

- `part_feedback: {}`이면 ExamSummary를 저장하지 않는다.
- `part_feedback` 누락/null도 동일하게 처리한다.
- 현재 generation과 일치하는 Summary Callback만 유효성 검사와 저장으로 진입한다.
- generation이 누락되거나 현재 Job과 다르면 empty/valid 여부와 관계없이 stale no-op한다.
- Summary Job은 `FAILED/FEEDBACK_GENERATION_FAILED`가 된다.
- ExamSession은 IN_PROGRESS를 유지한다.
- `completeSummary()`와 Session 완료가 호출되지 않는다.
- non-empty Map은 기존과 동일하게 저장·완료된다.
- 같은 empty Callback 반복은 중복 상태 전이와 중복 ERROR 로그를 만들지 않는다.
- 동일 generation의 empty 이후 valid Callback은 Summary 저장과 COMPLETED로 수렴한다.
- 동일 generation의 valid 이후 늦은 empty Callback은 COMPLETED를 되돌리지 않는다.
- generation 검증 직후 동시 retry가 발생하는 경합에서도 completion claim/transaction fence를 잃은 stale Callback은 Summary를 저장하지 않는다.

### 9.2 프론트 오류 계약

- 상태 조회에서 HTTP status, `isSuccess=false`, exact code와 exact message를 고정한다.
- Summary 조회도 실패 상태에서는 같은 오류를 반환한다.
- Question 실패 등 기존 FAILED 경로는 기존 응답을 유지한다.
- 소유권 검증이 오류 사유 확인보다 먼저 수행되어 다른 사용자가 실패 상태를 추론할 수 없게 한다.

### 9.3 모든 문항 결과가 존재하는 경우

- 현재 문제지의 1~11번 최초 결과가 모두 있으면 Question dispatch는 0회다.
- Summary만 1회 예약한다.
- retry 응답의 기존 필드와 `summaryAction` 의미를 유지한다.
- 사용자 재답변 `retryCount>0` 결과는 completeness 판단에 영향을 주지 않는다.

### 9.4 문항 결과가 누락된 경우

- 실제 결과가 없는 문항만 복구한다.
- 다른 성공 문항은 재전송하지 않는다.
- COMPLETED Job만 있고 결과가 없는 문항은 Summary 완료 근거가 되지 않는다.
- COMPLETED Job을 PENDING으로 원자적 re-open하고 `dispatchAttempt=0`으로 초기화한다.
- 동시 re-open은 한 요청만 성공하고 stale pre-reopen 상태 변경은 version/recovery-cycle fence로 차단한다.
- 첫 retry 호출 안에서는 Summary를 보내지 않는다.
- 마지막 누락 문항의 valid Callback 뒤 PENDING Summary가 정확히 1회 예약된다.
- S3 Object가 없으면 기존 missingSubmission 목록을 유지하고 Summary를 보내지 않는다.

### 9.5 반복 재생성과 동시성

#### generation 기본 동작

- 첫 empty 결과는 generation 1을 FAILED로 만든다.
- retry는 generation 2와 `dispatchAttempt=0`을 만든다.
- 두 번째 empty 결과는 generation 2를 FAILED로 만든다.
- 다시 retry하면 generation 3이 된다.
- generation은 `FEEDBACK_GENERATION_FAILED`에 대한 명시적 retry마다 정확히 한 번 증가한다.
- transport retry에서는 generation이 증가하지 않고 `dispatchAttempt`만 증가한다.
- generation별 기존 max dispatch 정책이 독립적으로 적용된다.

#### stale Callback fencing

```text
gen1 empty
→ retry
→ gen2 PROCESSING
→ gen1 delayed empty Callback
```

gen2 상태와 `dispatchAttempt`가 변경되지 않아야 한다. gen2 시작 뒤 늦게 도착한 gen1 valid Callback도 Summary 저장이나 gen2 COMPLETED를 만들지 않고 stale no-op해야 한다.

동일 generation 안의 valid/empty Callback 경합에서는 유효 Summary 저장 결과가 최종 승리해야 한다.

#### Scheduler stale claim fencing

```text
gen1 scheduler claim 획득
→ gen1 FAILED
→ retry로 gen2 생성
→ stale gen1 claim 실행
```

다음을 모두 검증한다.

- gen2 status 변경 없음
- gen2 `dispatchAttempt` 변경 없음
- gen1 기준 새 AI dispatch 없음

#### 동시 retry

```text
gen1 FAILED
→ retry 두 건 동시 호출
```

- generation은 정확히 2가 된다.
- generation 3으로 중복 증가하지 않는다.
- Summary dispatch는 generation 2에 대해 정확히 한 건만 예약된다.
- 나머지 호출은 PENDING/PROCESSING을 보고 WAITING으로 수렴한다.

#### Question 결과 누락 복구

```text
Question Job COMPLETED
dispatchAttempt == max
ExamResult 없음
```

- Job을 원자적으로 PENDING으로 re-open한다.
- `dispatchAttempt=0`으로 초기화한다.
- 기존 max attempt 정책을 새 복구 사이클에 다시 적용해 실제 Question dispatch가 가능해야 한다.
- 동일 문항 동시 복구는 한 re-open과 한 dispatch만 시작한다.
- stale pre-reopen worker는 새 복구 사이클의 상태를 변경하지 않는다.

#### legacy 데이터

- `generationAttempt` 필드가 없는 기존 `SummaryGradingJob`은 generation 1로 읽힌다.
- 첫 `FEEDBACK_GENERATION_FAILED` retry 이후 정확히 generation 2가 된다.
- generation이 없는 구버전 Summary Callback은 현재 Job을 변경하지 않고 stale/missing generation no-op한다.
- generation 2 이상의 AI Idempotency-Key는 generation 1과 다르고, 같은 generation의 transport retry에서는 동일해야 한다.
- 기존 Question AI 요청과 idempotency key는 변경되지 않는다.

### 9.6 전체 회귀

```text
./gradlew clean test
git diff --check
```

실제 MongoDB, Redis, AWS S3, Python AI와 Sentry는 테스트에서 호출하지 않는다.

## 10. 배포 및 기존 데이터

### 10.1 순서

1. Python AI에 Summary `generation_attempt` echo 계약을 먼저 적용하고, 전환 기간에는 필드가 없는 구버전 Summary 요청을 generation 1로 처리해 `generation_attempt=1`로 echo하도록 한다.
2. Python AI가 generation별 Idempotency-Key를 독립된 요청으로 처리하는지 확인
3. Learning Core 배포
4. staging에서 generation 1 empty → FAILED 시나리오 수행
5. retry 후 generation 2가 생성되는지 확인
6. generation 1의 늦은 empty/valid Callback을 보내 generation 2가 영향받지 않는지 확인
7. stale Scheduler claim이 generation 2를 변경하지 않는지 확인
8. generation 2 valid Callback으로 Summary와 Session이 `COMPLETED` 되는지 확인
9. status API가 HTTP 500과 exact `FEEDBACK_GENERATION_FAILED`를 반환하는지 확인
10. 프론트가 해당 code를 기준으로 기존 retry 흐름으로 연결하는지 확인
11. Question 결과가 모두 있는 경우 Question dispatch 0회, Summary dispatch 1회 확인
12. CloudWatch 로그에서 민감 payload 부재와 generation/dispatch attempt를 확인

### 10.2 이미 저장된 빈 Summary

현재 버전은 빈 `partFeedback` Summary를 저장하고 Session까지 COMPLETED로 만들 수 있다. 새 Callback 검증만으로 기존 데이터가 자동 복구되지는 않는다.

배포 전에 운영 데이터에서 다음 조건을 읽기 전용으로 집계한다.

```text
exam_summaries.partFeedback가 null 또는 빈 object
```

해당 데이터가 존재하면 별도 승인된 복구 runbook이 필요하다. 완료 Session을 자동으로 IN_PROGRESS로 되돌리거나 Summary 문서를 삭제하는 작업은 이 구현에 묶어 임의 수행하지 않는다.

## 11. 완료 조건

- empty/null `partFeedback`은 Summary 저장, Summary COMPLETED, Session COMPLETED를 만들지 않는다.
- Summary 요청에 Learning Core가 관리하는 `generationAttempt`가 포함된다.
- Python AI는 전달받은 generation을 Summary Callback에 그대로 반환한다.
- Callback generation이 현재 Summary Job generation과 다르거나 누락되면 stale Callback으로 no-op한다.
- 이전 generation의 empty/valid Callback은 현재 generation 상태나 Summary 저장 결과를 변경하지 않는다.
- Scheduler/dispatch claim은 generation fencing을 적용한다.
- stale claim은 새 generation의 status나 `dispatchAttempt`를 변경하지 않고, 전송 전 감지되면 외부 AI 요청도 보내지 않는다.
- `generationAttempt`는 `FEEDBACK_GENERATION_FAILED`에 대한 명시적 사용자 retry에서만 증가한다.
- 동일 generation의 transport retry는 generation을 유지하고 `dispatchAttempt`만 증가시킨다.
- 새 generation이 시작되면 Summary `dispatchAttempt`는 0부터 다시 시작한다.
- legacy Summary Job의 누락된 generation은 1로 해석한다.
- 기존 retry API 한 번으로 누락 문항 복구 의도가 PENDING Summary에 durable하게 남는다.
- `QuestionGradingJob=COMPLETED`이더라도 실제 최초 `ExamResult`가 없으면 Summary 완료 근거로 인정하지 않는다.
- `COMPLETED + missing result` Question Job 복구 시 `dispatchAttempt=0`으로 reset하고 기존 attempt 정책을 새 복구 사이클에 적용한다.
- 프론트는 HTTP 500 응답의 exact `FEEDBACK_GENERATION_FAILED` code를 식별해 기존 grading retry 흐름으로 연결한다.
- 다른 기존 5xx/FAILED 동작은 변경하지 않는다.
- 실제 최초 결과가 모두 있으면 Question AI 요청은 0건이고 Summary만 재생성된다.
- 결과 누락 문항만 복구되고 마지막 결과 저장 후 Summary가 1회 생성된다.
- 재생성 실패 후 같은 API로 다시 재생성할 수 있다.
- 공개 API URL·Method·기존 DTO·BaseResponse, `user_id=examId`, retryCount, Redis/S3 계약을 유지한다.
- Summary AI Request/Callback에 한해 `generation_attempt` 필드를 추가하고 Question AI 계약은 유지한다.
- 전체 테스트와 정적 검사가 통과한다.

## 12. 구현 전 확인 사항

다음 항목은 결정 완료로 본다.

1. Summary generation의 소유자는 Learning Core다.
2. Python AI는 Summary 요청의 `generation_attempt` 값을 Callback에 그대로 반환한다.
3. Callback generation이 현재 Job generation과 다르면 stale Callback으로 처리한다.
4. Scheduler/dispatch에도 동일한 generation fencing을 적용한다.
5. `generationAttempt`는 `FEEDBACK_GENERATION_FAILED`에 대한 사용자 retry에서만 증가한다.
6. `COMPLETED Question Job + missing ExamResult` 복구 시 `dispatchAttempt=0`으로 초기화한다.
7. `FEEDBACK_GENERATION_FAILED`는 HTTP 500과 기존 `BaseResponse`로 반환하고 프론트는 exact code를 retry signal로 사용한다.

배포 전에 확인할 항목은 다음만 남긴다.

1. Python AI가 generation별 다른 `Idempotency-Key`를 별개의 Summary 생성 요청으로 처리하는지 staging에서 확인한다.
2. Learning Core와 Python AI 배포 순서에서 generation이 없는 구버전 Callback이 유입될 수 있는지 확인한다.
3. 이미 저장된 empty/null `partFeedback` Summary가 있는지 읽기 전용으로 집계하고, 존재한다면 별도 데이터 복구 범위를 승인받는다.
