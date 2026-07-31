# Learning Core Current State

## Last updated

- 2026-07-31

## Current branch

- `feat/TMI-31-sequential-exam-assignment`
- HEAD는 `b71b54b`다.
- TMI-31 애플리케이션·테스트·MongoDB migration 스크립트와 Codex 문서 변경이 작업 트리에 있다.
- Codex는 commit과 push를 수행하지 않았고 Jira 댓글·필드·상태도 변경하지 않았다.
- 기존 순차·순환 선택, 진행 중 세션 재사용, Summary 저장 후 완료와 전 과정 `mockExamId` 전파 구조는 유지했다.
- merge base `b71b54bb4ff871a8e082cd6d94a34007c84b062c` 기준 최종 리뷰의 HIGH 1건과 MEDIUM 4건을 수정했다. summarized legacy Session 판정/backfill, 운영 partial unique index 시작 검증, 명시적 migration DB 선택, 완료 횟수 aggregation/index, 중복 `mockExamId` 차단이 현재 작업 트리에 반영됐다.
- 같은 merge base 기준 재리뷰에서 확인한 HIGH 1건과 MEDIUM 1건을 최소 범위로 수정했다. 분리 이전 `exam_results.totalScore != null` 종합 결과도 legacy 완료 증거로 인정하며, migration은 inactive/empty 등 배정 제외 MockExam을 먼저 분류한 뒤 assignable 시험에만 sequence를 강제한다.
- 필수 partial unique index fail-closed, 명시적 migration DB 선택, 완료 횟수 Mongo aggregation/index, 중복 `mockExamId` runtime/migration 차단과 외부 API·AI·Redis·S3 계약은 그대로 유지했다.
- 2026-07-30 사용자 지정 최종 명령 `git diff --check`, migration `node --check`, `./gradlew clean test`를 다시 실행해 모두 성공했다. 애플리케이션·테스트·migration 코드는 이 재검증에서 변경하지 않았다.
- 같은 merge base 기준 독립 최종 리뷰에서 P1 live migration stale activation, P2 Java `Integer` 범위를 넘는 sequence 허용, P3 기존 WORKLOG 항목 수정 3건을 확인했다. 리뷰 대상 코드는 수정하지 않았다.
- AGENTS.md와 Jira TMI-31을 다시 대조한 사용자 지정 11개 항목 최종 리뷰에서도 위 HIGH 1건, MEDIUM 1건, LOW 1건을 재확인했다. 정상 runtime의 legacy 증거 판정·단일 Session 집계·조건부 backfill·순환 선택·활성 재사용·운영 인덱스 fail-closed·DB 선택·제외 catalog·ID 고유성·`mockExamId` 전파와 외부 계약에는 별도 finding이 없었다.
- 후속 수정에서 HIGH를 해소했다. apply는 `TMI31_LEGACY_WRITER_STOPPED=true`를 필수로 요구하고, legacy 활성화 직전에 최신 Session과 `exam_summaries`, `exam_results.totalScore != null`을 재조회한다. 새 완료 증거는 `active=false`/`completedAt`으로 조건부 보정하며 apply 후 active/완료 증거/사용자 중복/필수 인덱스를 현재 DB 상태로 교차검증해 불일치 시 실패한다.
- Runtime은 `active=true`이면서 `cycleNumber`가 없는 legacy 의심 Session에만 완료 증거 방어 조회를 추가했다. 신규 `cycleNumber`가 있는 active Session은 빠른 재사용 경로를 유지한다.
- 후속 수정에서 MEDIUM을 해소했다. migration의 명시 sequence와 ID suffix는 공통 Java `Integer` 범위 `1..2147483647`만 허용하고 오류 유형을 구분한다. Runtime catalog는 suffix overflow와 repository mapping overflow를 민감한 BSON 내용 없는 설정 오류로 처리한다.
- LOW의 과거 WORKLOG branch/HEAD 한 줄은 main 원문 `feat/TMI-25-grading-retry-idempotency` / `fb354b6`로 복원했고 정정 경위는 새 append 항목에만 기록했다.
- 최종 검증은 `git diff --check`, 두 migration 파일 `node --check`, Node 49개, `./gradlew clean test` Java 205개 모두 성공했고 failures/errors/skipped는 0개다. 공개 Controller/DTO diff는 없고 실제 AWS Access Key·자격증명 포함 Mongo URI·private key 패턴도 발견되지 않았다.
- 이번 merge base 최종 재리뷰는 tracked diff와 신규 미추적 application·migration·테스트 파일을 다시 독립 검토했으며, 수정 가치가 확실한 신규 finding을 확인하지 않았다. 공개 API·DTO·`BaseResponse`, 소유권, Redis/S3 Key, `retryCount`, AI/Callback `user_id=examId` 계약도 그대로다.
- 이번 환경에서 migration 두 파일 `node --check`, Node 49개와 whitespace/Secret 정적 검사는 성공했다. 정확한 `./gradlew clean test`와 writable offline Gradle home 재시도는 각각 sandbox의 사용자 Gradle lock 쓰기 제한과 file-lock UDP socket 제한으로 task 실행 전에 중단됐고, 현재 소스보다 최신인 기존 XML은 Java 205개와 failures/errors/skipped 0개를 기록한다.
- 추가 HIGH/MEDIUM/LOW 해소 여부 최종 리뷰에서도 신규 severity finding은 확인하지 않았다. 초기 snapshot 뒤 완료 증거는 legacy 활성화 직전 실DB 재조회에서 차단되고, 성공 종료 전 active/완료 증거/사용자 중복/필수 인덱스 교차검증이 남은 불일치를 실패 처리한다. apply는 실제 writer 종료를 전제로 `TMI31_LEGACY_WRITER_STOPPED=true`를 필수 요구하며 이 승인값을 거짓으로 설정하는 운영 위반은 자동 프로세스 탐지 대상이 아니다.
- assignable sequence와 ID suffix는 `1..2147483647` 범위로 제한되고 Runtime mapping/suffix overflow도 안전한 catalog 오류로 실패한다. WORKLOG는 main 대비 기존 행 삭제·수정 없이 append-only 상태이며 공개 API·DTO·`BaseResponse`, `retryCount`, Redis/S3 Key, Callback JSON과 AI `user_id=examId` 계약도 유지된다.
- 이번 targeted review에서 `git diff --check`, migration `node --check`, Node 49개가 성공했고 현재 source의 기존 Java XML은 205개·failures/errors/skipped 0개다. 애플리케이션·migration·테스트 파일은 수정하지 않았다.
- 문항별 피드백 응답 흐름을 코드 기준으로 재확인했다. AI Callback은 결과를 Mongo에 멱등 저장하고 `BaseResponse<Void>`만 반환하며, 프론트는 문항 상태를 폴링한 뒤 `GET /api/v1/exams/{examId}/questions`에서 `QuestionResult`를 받는다. 상세 응답은 요청 회차의 최신 AI 결과, Azure 결과, 5분 제출 음성 URL과 Session `mockExamId`의 문제 정보를 결합한다. 채점 전 상세 조회도 가능해 빈 feedback/누락된 nullable 결과 필드가 반환될 수 있으므로 UI는 `COMPLETED` 뒤 조회하는 흐름이 안전하다.
- 사용자 요청으로 문항 상세의 `question` 객체에 additive `retryScores` 배열을 추가했다. 각 원소는 `retryCount`와 `score`를 가지며 동일 examId·questionNumber의 점수 있는 최신 결과를 retry 오름차순으로 반환한다. legacy null retry는 0으로 병합하고 동일 retry 중복은 `_id` 최신 문서만 사용하며, 최신 score가 null이면 과거 점수로 fallback하지 않고 해당 retry를 제외한다.
- 사용자 확정에 따라 문항 상세 `question.retryFeedbackScores`를 구현했다. 기존 `feedback`은 요청한 현재 retry를 유지하고, 새 배열은 동일 examId·questionNumber의 최초 응시 `retryCount=0` 세부 점수만 한 건 반환한다. legacy null retry도 0으로 병합하고 중복 0회차는 `_id` 최신 문서 하나만 사용하며, 최초 피드백이 없으면 빈 배열을 반환한다.
- 프론트 전달용 문항 상세 응답 계약을 현재 Controller·DTO 기준으로 재확인했다. HTTP 200 `BaseResponse.result.question`에 현재 retry `feedback`, 총점 이력 `retryScores`, 최초 응시 비교값 `retryFeedbackScores`와 음성·Azure·문제 정보가 들어간다. null인 question 필드는 생략될 수 있고 최초 피드백이 없으면 `retryFeedbackScores=[]`이다. 애플리케이션·테스트 코드는 이 정리 작업에서 수정하지 않았다.
- 변경 후 집중 테스트와 `./gradlew clean test`가 성공했다. XML 기준 Java 207개, failures/errors/skipped 0개이며 `git diff --check`도 통과했다. 문항 상세 기존 필드와 URL·Method·Query, `BaseResponse`, 소유권, AI user_id, retryCount, Redis·S3·grading 계약은 유지했다.
- TMI-31은 사용자 요청으로 Jira `완료`(ID `10003`, resolution `완료` ID `10000`)로 전환했고 재조회로 확인했다. 실제 Atlas backup/dry-run/apply·index build·aggregation explain 및 Redis·S3·Python AI staging E2E는 수행하지 않았다.

## Current Jira issue

- [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31) — [Learning Core] 사용자별 모의고사 순차 배정 및 순환 제공
- 프로젝트: `TMI` (ID `10000`)
- 이슈 유형: `작업` (ID `10003`)
- Jira 상태: `완료` (상태 ID `10003`, resolution `완료` ID `10000`)
- 우선순위: `High` (ID `2`)
- 담당자: 미지정
- 라벨: 없음
- 사용자별 활성 MockExam 완료 횟수와 sequence 기반 순차·순환 선택, 진행 중 활성 ExamSession 재사용, 사용자당 활성 세션 하나, 선택된 `mockExamId`의 S3·문항 조회·AI grading retry·summary 전 과정 전파, legacy null의 `mock_exam_003` fallback과 Summary Callback 기반 완료 처리를 다룬다.
- 생성 Payload에는 프로젝트, 이슈 유형, 승인된 제목·Markdown 설명과 우선순위만 포함했다. 담당자·라벨·스프린트·에픽·상위 항목·상태 전환은 설정하지 않았다.
- 생성 후 상세 재조회로 승인된 제목·설명, `작업`, `High`, 기본 상태 `해야 할 일`, 담당자 미지정과 빈 라벨을 확인했다.

## Latest independent TMI-31 review

- merge base `b71b54bb4ff871a8e082cd6d94a34007c84b062c` 기준 tracked 변경과 신규 미추적 production·migration·test 파일을 함께 검토했다.
- 순차·순환 선택, 활성 Session 재사용·동시 insert 충돌 복구, legacy 완료 증거와 조건부 backfill, Summary 성공 후 완료, 선택된 `mockExamId`의 S3·Job·AI·retry·조회 전파, staging/prod 필수 인덱스 검증과 migration fail-closed 경로에서 신규 actionable finding은 확인하지 않았다.
- Controller·Request/Response DTO·`BaseResponse`에는 diff가 없고 사용자 소유권, 실제 userId 비노출, Redis/S3 Key, `retryCount`, Callback JSON과 Python AI `user_id=examId` 계약이 유지된다.
- Node syntax와 migration 49개 테스트, tracked/untracked whitespace 및 Secret 패턴 검사는 성공했다. fresh Gradle은 sandbox 제약으로 task 시작 전에 실패했으며, 2026-07-30 16:02에 현재 소스로 생성된 XML은 Java 205개, failures/errors/skipped 0개다.
- 실제 Atlas migration/index, 다중 인스턴스 동시성, Redis·S3·Python AI staging E2E는 이번 리뷰 범위에서 실행하지 않았다. 애플리케이션·migration·테스트 코드는 수정하지 않았고 Jira와 Git commit/push도 변경하지 않았다.

## Previous independent TMI-31 review

- merge base `b71b54bb4ff871a8e082cd6d94a34007c84b062c`의 tracked diff와 신규 미추적 파일을 함께 재검토했다.
- P1: `TMI31_APPLY=true`가 기존 main Callback과 겹치면 plan snapshot 뒤 Summary가 저장된 Session도 stale `activateIncompleteLegacy` 목록에서 `active=true`가 된다. 기존 Callback은 Session 완료 필드를 쓰지 않고 신규 Manager는 true-active 후보의 evidence를 확인하지 않으므로 구버전 writer quiescence 또는 activation/index 전 재검증이 필요하다.
- P2: migration은 explicit sequence와 ID suffix를 JavaScript integer 범위로만 검사해 Java Entity의 `Integer.MAX_VALUE`를 넘는 값을 저장할 수 있다. APPLY 전에 두 경로 모두 signed 32-bit 상한을 검증해야 한다.
- P3: 기존 WORKLOG의 `019fac7a-...` branch 기록 한 줄을 append가 아니라 수정했다. append-only 규칙에 따라 원문을 복원하고 정정은 새 항목으로 남겨야 한다.
- 외부 공개 API·DTO·`BaseResponse`, 소유권, Redis/S3 Key, `retryCount`, AI/Callback `user_id=examId` 계약은 리뷰 중 변경하지 않았다.
- tracked/untracked whitespace 검사, migration 두 파일 `node --check`와 Node 테스트 25개는 성공했다. fresh Gradle은 sandbox lock/socket 제한으로 task 시작 전에 실패했으며, 현재 소스보다 최신인 기존 XML은 Java 200개와 failures/errors/skipped 0개를 기록한다.
- 후속 사용자 지정 최종 리뷰는 AGENTS.md와 Atlassian MCP의 TMI-31 설명을 다시 읽고 요청된 11개 경로를 추적했으며, 위 세 finding 외 추가 HIGH/MEDIUM/LOW finding을 확인하지 않았다. Jira 쓰기와 application·migration·테스트 코드 수정은 수행하지 않았다.

## Previous TMI-31 code review and resolution

- merge base `b71b54bb4ff871a8e082cd6d94a34007c84b062c`의 tracked diff와 신규 미추적 파일을 함께 검토했다.
- HIGH 수정: `ExamCompletionEvidenceService`가 `exam_summaries`와 `exam_results.totalScore != null`을 동일한 완료 증거로 조회한다. legacy null active/null completedAt Session에 증거가 있으면 재사용하지 않고, 가장 이른 명시 시각·실제 BSON ObjectId 시각·Session createdAt 순으로 완료 시각을 산정해 조건부 원자 backfill한다. 시각을 얻지 못해도 활성으로 재사용하지 않는다.
- MEDIUM 수정: migration catalog 검사는 `INACTIVE`, `EMPTY_QUESTIONS`, `MISSING_ID`, `INVALID_ACTIVE`를 먼저 분류하고 assignable 문서에만 `deriveSequence`와 sequence 중복 검증을 적용한다. `mockExamId` 중복 검증은 전체 catalog 범위를 유지한다.
- migration도 Summary와 legacy totalScore 증거를 합쳐 Session당 한 번만 완료 처리하고, 가장 이른 신뢰 가능한 완료 시각과 evidence overlap·duplicate·orphan·unresolved 통계를 dry-run에 출력한다. 배정 제외 문서는 별도 목록에 표시하며 sequence/active 보정 대상에서 제외한다.
- 공개 API·DTO·`BaseResponse`, 소유권, Redis/S3 Key, `retryCount`, AI/Callback `user_id=examId` 계약은 변경하지 않았다.
- `git diff --check main --`, migration `node --check`, Node 테스트 25개와 `./gradlew clean test`가 성공했다. XML 기준 Java 테스트 200개, 실패·오류·건너뜀 0개다.

## TMI-31 implementation state

- `MockExam`에 `sequence`, `active`를 추가했다. `active=null`은 활성, `sequence=null`은 `mockExamId` 끝 숫자를 임시 sequence로 해석하며 활성·비어 있지 않은 시험지를 숫자 sequence 오름차순으로 반환한다. 유효하지 않은 sequence, 활성 sequence 중복, 전체 catalog의 null/blank/whitespace/중복 `mockExamId`는 `EXAM_5001` 설정 오류로 안전하게 실패하고 빈 시험지는 배정에서 제외한다. 단건 조회도 `List` 결과가 2개 이상이면 임의 선택하지 않는다.
- `ExamSession`에 `mockExamId`, `cycleNumber`, `active`, `completedAt`을 추가했다. 신규 세션은 사용자별 완료 횟수가 최소인 활성 시험 중 sequence가 가장 작은 시험을 선택하고 `cycleNumber=completionCount+1`, `active=true`, `completedAt=null`로 Mongo `insert`한다.
- `POST /api/v1/exams`는 현재 사용자의 재사용 가능 세션을 먼저 조회한다. `active=true`는 재사용하고 `active=false` 또는 완료 시각이 있는 세션은 제외한다. null active+null completedAt 후보는 결정적/legacy `ExamSummary` 또는 분리 이전 `exam_results.totalScore != null` 증거를 확인해 완료면 조건부 원자 backfill하고, 증거가 없는 실제 진행 중 legacy Session만 같은 `examId`로 재사용한다. 문제·가이드 Presigned GET URL은 매 호출 새로 발급하고 Redis 누락은 기존 Key/TTL로 복구한다.
- 완료 횟수는 전체 `ExamSession` Entity 목록을 읽지 않고 Mongo aggregation으로 현재 `userId`, `completedAt != null`만 `mockExamId`별 집계한다. null `mockExamId`는 `mock_exam_003`으로 그룹화한다.
- 동시 신규 생성은 `active=true` 문서에 대한 사용자별 Mongo partial unique index `uniq_exam_sessions_active_user`를 전제로 한다. 두 요청이 동시에 insert하면 한 요청만 성공하고 loser는 `DuplicateKeyException`을 500으로 노출하지 않고 승자 활성 세션을 재조회한다. staging/prod는 시작 시 이름·키·unique·partial 정의를 검증해 누락/불일치 시 fail-closed하고 local은 경고한다.
- Summary Callback은 `ExamSummary`가 신규 또는 멱등 성공으로 확인된 뒤에만 `completedAt is null` 조건 원자 update로 세션 완료 시각을 기존 UTC `Clock`에서 설정하고 `active=false`로 바꾼다. 저장 예외 전에는 완료하지 않고 중복 Callback은 최초 전이 이후 no-op이다. 문항 결과/Job 완료만으로는 세션을 완료하지 않는다.
- 신규 `QuestionGradingJob`과 `SummaryGradingJob`은 최소 필드 `mockExamId`를 저장한다. 세션의 선택값이 문제 조회, `questions/{mockExamId}/q_N.wav`, `part3_intro.wav`, 문항 AI multipart, 시험 retry 예상 문항, Summary AI JSON과 문항 상세 조회까지 전파된다. 기존 Job의 값이 없으면 세션을 조회하고 세션도 없거나 값이 null/blank이면 legacy `mock_exam_003`만 fallback한다.
- AI outbound `user_id`와 Callback `user_id`는 계속 `examId`다. Callback 저장 시 외부 `mock_exam_id`보다 세션의 canonical `mockExamId`를 사용하며 실제 사용자 UUID를 AI 서버로 보내거나 외부 응답에 추가하지 않는다.
- `scripts/mongodb/tmi-31-migrate-exam-assignment.js`와 실행 문서를 추가했다. Node entrypoint가 `MONGODB_DATABASE`를 필수 검증하고 URI의 DB와 무관하게 `getSiblingDB`로 명시 선택한다. 기본은 dry-run이며 `TMI31_APPLY=true`일 때만 Summary/legacy totalScore 증거에 따른 완료 Session backfill, assignable MockExam/Session 보정과 active unique·완료 집계·`mock_exam_id` unique 세 인덱스를 생성한다. 완료 증거·시각 충돌, 중복 ID, 여러 활성 후보와 인덱스 충돌은 쓰기 전에 중단한다.
- `POST /api/v1/exams`의 URL·Method·Request Body 없음, `CreateSessionResult` 세 필드와 `BaseResponse`를 포함한 기존 공개 API/DTO, `retryCount`, Redis Key/TTL, 제출 S3 Key, Callback JSON 계약은 변경하지 않았다.

## Previous TMI-31 finding resolution

- HIGH 수정: null/missing `active`와 null `completedAt`인 legacy 후보는 Summary 증거를 조회한다. Summary가 있으면 재사용하지 않고 ObjectId 또는 Summary Job 완료 시각을 사용해 `active=false`, `completedAt`을 기존 값이 여전히 없는 경우에만 원자 보정한다. 중복 Summary는 임의 선택하지 않고 안전하게 실패한다.
- MEDIUM 수정: `ExamAssignmentIndexValidator`가 `uniq_exam_sessions_active_user`와 `uniq_mock_exams_mock_exam_id`를 정확한 이름·순서 있는 키·unique·partial 기준으로 검증한다. staging/prod는 실패 시 기동하지 않으며 test profile은 실제 Mongo를 검사하지 않는다. 완료 집계 인덱스는 정확성 필수와 분리해 경고한다.
- MEDIUM 수정: migration은 `MONGODB_DATABASE` 누락·공백·시스템 DB를 거부하고 URI와 별개로 해당 DB를 선택하며 DB/collection/예정 변경 수를 dry-run과 apply 직전에 표시한다. URI와 자격증명은 출력하지 않는다.
- MEDIUM 수정: 완료 횟수 집계를 Mongo aggregation으로 옮기고 `idx_exam_sessions_user_completed_mock_exam`을 migration apply 대상으로 추가했다. 현재 사용자·완료 Session만 집계하며 legacy null 시험 ID fallback을 유지한다.
- MEDIUM 수정: runtime 전체 catalog와 단건 조회에서 중복/null/blank/공백 `mockExamId`를 거부하고, migration은 실제 저장 필드 `mock_exam_id`의 중복 metadata와 인덱스 충돌을 보고한 뒤 문제가 없을 때만 `uniq_mock_exams_mock_exam_id`를 생성한다.
- 추가 HIGH 수정: `ExamCompletionEvidenceService`가 Summary와 `exam_results.totalScore != null` projection을 합쳐 가장 이른 완료 시각을 결정한다. Manager는 완료 증거가 있는 legacy Session을 재사용하지 않고 조건부 원자 backfill한 뒤 기존 `completedAt` aggregation으로 한 번만 집계한다. `totalScore=null` 문항 결과는 증거에서 제외한다.
- 추가 MEDIUM 수정: migration은 assignable 여부를 sequence 해석 전에 판정한다. sequence uniqueness는 assignable 시험에만 적용하고, 전체 catalog `mockExamId` uniqueness는 그대로 유지하며 제외 문서를 임의 활성화·수정하지 않는다.
- Jira TMI-31 설명과 완료 조건은 Atlassian MCP로 읽기 전용 재조회했고 Jira 쓰기 API는 호출하지 않았다.
- migration Node 테스트 25개와 현재 소스의 `./gradlew clean test`가 성공했다. XML 기준 Java 테스트 200개, 실패·오류·건너뜀 0개이며 기존 `ExamServiceImpl` unchecked 경고만 남았다.

## TMI-31 application package map

- `ExamService`는 Controller가 의존하는 시험 유스케이스 계약이고 `ExamServiceImpl`은 사용자 소유권, S3 URL, 세션 생성·재사용, 제출·상태·결과 조회와 세 종류 Callback 저장을 조율하는 API 파사드다.
- `ExamSessionManager`는 활성 세션 재사용, 사용자별 완료 횟수·sequence 기반 신규 배정, 동시 insert 충돌 복구와 Summary 성공 뒤 세션 완료를 담당한다. `MockExamCatalogService`는 활성·비어 있지 않은 문제지와 유효한 숫자 sequence로 배정 catalog를 만든다.
- `ExamGradingService`는 Question/Summary Job 생성·완료·retry, S3 제출 존재 확인, optimistic claim, Mongo 결과 기반 전체/문항 상태 계산과 Redis projection을 담당하는 채점 상태 오케스트레이터다.
- `SummaryDispatchScheduler`는 bounded executor에서 Summary Job을 원자 claim하고 비동기 AI 전송·실패 전이를 수행하며, `GradingDispatchService`는 S3 음성 로드와 Python AI multipart/JSON HTTP 계약을 실제로 실행한다.
- `QuestionDispatchClaim`과 `SummaryDispatchClaim`은 claim 시점의 attempt·시간·`examId`·`mockExamId`를 고정하는 immutable 전송 스냅샷이고, `GradingKeys`는 결정적 Job/결과 ID, 기존 S3 제출 Key, retry 0 정규화와 `mock_exam_003` legacy fallback을 중앙화한다.
- 2026-07-29 역할 분석에서는 application 패키지 10개 파일과 Controller 호출 관계를 읽기 전용으로 확인했다. 애플리케이션·테스트 코드는 수정하지 않았고 기존 TMI-31 외부 계약과 직전 169개 전체 테스트 성공 상태를 유지한다.

## Previous completed Jira issue — TMI-25

- [`TMI-25`](https://to-teacher.atlassian.net/browse/TMI-25) — [Learning Core] 시험 단위 재채점 및 AI 채점·Callback 멱등성 보장
- 프로젝트: `TMI` (ID `10000`)
- 이슈 유형: `작업` (ID `10003`)
- Jira 상태: `완료` (상태 ID `10003`, resolution `완료` ID `10000`)
- 우선순위: `High` (ID `2`)
- 담당자: 미지정
- 라벨: 없음
- 사용자가 승인한 제목과 Markdown 설명을 그대로 사용해 생성했다. 설명에는 기존 문항 submit·전체 상태 API 유지, 신규 시험 단위 retry API, retryCount 0 복구 규칙, 문항·요약 Job, Callback 멱등성, Redis 전체 상태, AI `Idempotency-Key`, 완료 조건과 범위 제외가 포함된다.
- 생성 Payload에는 프로젝트, 이슈 유형, 제목, 설명, 우선순위만 포함했다. 담당자·라벨·상위 항목·스프린트·에픽·상태 전환은 설정하지 않았다.
- 생성 후 상세 재조회로 승인된 제목·설명, `작업`, `High`, 기본 상태 `해야 할 일`, 담당자 미지정과 빈 라벨을 확인했다.
- 2026-07-28 전환 전 읽기 전용 조회에서 상태 `해야 할 일`과 사용 가능한 전환 `해야 할 일`(ID `11`)·`검토 중`(`31`)·`진행 중`(`21`)·`완료`(`41`)를 확인했다.
- 사용자 요청에 따라 전환 직전 상태와 `진행 중` 전환 ID `21`의 가용성을 다시 확인한 뒤 해당 전환만 적용했다. 전환 Payload에는 다른 필드·댓글·업데이트를 포함하지 않았고 다른 Jira 이슈를 호출하지 않았다.
- 전환 후 상세 재조회에서 현재 상태 `진행 중`(상태 ID `10001`)을 확인했다.
- 구현 전 정적 분석에서 동일 submit·네 종류 Callback·11번 요약 Trigger의 중복 가능성, Redis 단일 상태와 고정 progress, Job·Clock·S3Client Bean·Mongo `@Version`·원자 claim 부재, legacy Unique Index 충돌 위험을 확인했다. 애플리케이션 구현과 Jira 변경은 수행하지 않았다.
- 사용자가 TMI-25에 한해 API 변경 금지 규칙의 제한 예외를 승인했고 `AGENTS.md`에 전용 예외를 기록했다. 신규 시험 단위 retry API·전용 DTO·Question/Summary Job·submit/Callback 멱등성·Job 기반 status 내부 처리·전체 필수 retry 0 문항 완료 요약 Trigger만 허용되며 다른 작업에는 자동 적용되지 않는다.
- 승인된 범위의 구현과 리뷰 finding 회귀 수정을 완료했다. 사용자의 종료 요청에 따라 Atlassian MCP로 전환 직전 `진행 중`과 `완료` 전환 ID `41`을 재확인한 뒤 해당 전환만 적용했다.
- 후속 상세 조회에서 상태 `완료`와 resolution `완료`를 확인했다. 댓글·다른 필드·다른 Jira 이슈는 변경하지 않았다.
- `./gradlew clean test`는 142개 테스트 모두 성공했고 `git diff --check`, 외부 API·AI/Redis/S3 계약 검색과 Secret 패턴 검색도 통과했다.

## Latest Jira creation

- 프로젝트 `TMI`에 `[Learning Core] 사용자별 모의고사 순차 배정 및 순환 제공` 제목의 `작업` 이슈를 `TMI-31`로 생성했다.
- Atlassian 메타데이터에서 프로젝트 ID `10000`, 이슈 유형 ID `10003`, 설명 필드와 우선순위 `High`(ID `2`) 지원을 확인했고 동일 제목 검색 결과는 없었다.
- 설명은 사용자별 활성 MockExam 완료 횟수와 sequence 기반 순차·순환 선택, 진행 중 활성 ExamSession 재사용, 사용자당 활성 세션 하나, 선택된 `mockExamId`의 S3·문항 조회·AI grading retry·summary 전 과정 전파, legacy null의 `mock_exam_003` fallback과 Summary Callback 기반 완료 처리를 포함한다.
- 프로젝트, 이슈 유형, 승인된 제목·Markdown 설명과 `High`만 전송했다. 담당자·라벨·스프린트·에픽·상위 항목·상태 전환은 설정하지 않았고 기본 상태 `해야 할 일`을 유지했다.

## Latest TMI-25 regression fixes

- Question/Summary dispatch는 immutable claim에 `jobId`, `dispatchAttempt`, `claimedAt`을 고정한다. HTTP 실패는 Mongo `_id + status=PROCESSING + dispatchAttempt=claimedAttempt` 조건 update만 사용하며 0건이면 이전 attempt의 늦은 실패로 무시한다.
- Feedback Callback은 결과 저장과 Question Job 완료·복구 후 모든 필수 retry 0 완료를 확인하고 Summary PENDING만 확보한다. bounded 전용 executor에 task를 넘기고 실제 Summary HTTP는 worker가 `@Version` claim에 성공한 경우에만 실행한다.
- Callback gate `ensureSummaryStartedIfReady`는 기존 FAILED 또는 stale PROCESSING Summary를 재시도하지 않는다. `retrySummaryIfEligible` 경로만 FAILED·stale PENDING/PROCESSING과 max attempts를 판정해 recovery task를 제출한다.
- AI HTTP connect/read timeout 기본값은 각각 `PT3S`/`PT30S`, Summary worker/queue 기본값은 `2`/`100`이며 모두 `app.grading` 타입 안전 설정이다. queue rejection은 Job을 변경하지 않아 PENDING 복구가 가능하다.
- submit은 Job insert 전에 retry 0의 `0/null/missing` compatible Feedback 결과를 확인하고 COMPLETED Job을 지연 복구한다. 기존 non-COMPLETED Job보다 결과를 우선해 COMPLETED로 보정하며 AI를 재호출하지 않는다.
- Azure retry 0 조회는 결정적 ID, 정확한 0, 명시적 BSON null, 필드 누락 순서다. retryCount>0은 정확한 회차만 조회하며 ObjectId와 문자열 ID를 한 정렬에서 시간순으로 비교하지 않는다.
- 실제 attempt 1 HTTP를 timeout 경계 너머까지 대기시켜 attempt 2를 claim한 뒤 attempt 1 실패를 도착시키는 Question/Summary 동시성 테스트, 중복 scheduler task 단일 dispatch, queue rejection, Callback/retry gate 분리, legacy submit 복구와 Azure null/missing 조회 테스트가 통과했다. 자체 재리뷰에서 남은 HIGH/MEDIUM finding은 확인하지 않았다.

## Previous TMI-25 code review state

- 2026-07-29에 사용자 요청으로 merge base `bc15c504b4130e011cbb476d71a37e98e1d8a862` 기준 전체 diff와 미커밋 회귀 수정까지 다시 재검증했다. 리뷰 대상 애플리케이션·테스트 코드는 수정하지 않았고 Git/Jira 쓰기 작업도 수행하지 않았다.
- P1: 시험 retry가 여러 Question의 S3 GET과 AI POST를 요청 스레드에서 직렬 실행하므로 downstream timeout 시 단일 요청이 수분간 지속되고 Tomcat 스레드 풀이 고갈될 수 있다.
- P2: 세션이 생성될 때 제공한 문항 집합을 고정하지 않고 매 status/retry/Callback gate에서 현재 `mock_exam_003`을 다시 읽어, 시험지 변경 시 진행 중 세션의 완료 기준이 바뀐다.
- P2: retry 0 Azure의 legacy BSON null·필드 누락 fallback 쿼리에 최신순 정렬이 없어 pre-idempotency 중복 문서 중 임의 결과를 반환할 수 있다.
- P2: staging/prod localhost 차단이 축약형 IPv6만 열거해 `[0:0:0:0:0:0:0:1]`, IPv4-mapped IPv6 같은 loopback 표기를 허용한다.
- P2: E2E의 단일 logout은 Refresh 재사용 탐지가 이미 폐기한 Token을 사용해 logout이 no-op이어도 통과한다.
- P2: E2E의 `logout-all`은 활성 Session을 하나만 만들어 단일 logout 구현도 통과할 수 있다.
- 정적 검증인 `git diff --check bc15c504b4130e011cbb476d71a37e98e1d8a862`와 E2E `bash -n`은 성공했다. `./gradlew clean test --no-daemon`은 사용자 Gradle home lock 쓰기 제한으로, cache를 `/tmp`에 복제한 offline 재시도는 sandbox의 file-lock contention socket 제한으로 시작되지 않았다. 기존 XML 결과는 현재 소스로 컴파일된 142개 테스트와 실패·오류·건너뜀 0개를 기록한다.

## Previous completed Jira issue

- [`TMI-14`](https://to-teacher.atlassian.net/browse/TMI-14) — [Learning Core] 운영 JWT 모드 강제 및 Legacy/HMAC 인증 정리
- 프로젝트: `TMI` (ID `10000`)
- 이슈 유형: `작업` (ID `10003`)
- Jira 상태: `완료` (상태 ID `10003`, resolution `완료` ID `10000`)
- 우선순위: `High` (ID `2`)
- 담당자: 설정됨 (개인 식별 정보는 기록하지 않으며 이번 작업에서는 변경하지 않음)
- 타입 안전 `AuthMode`, staging/prod Startup Validator, Legacy profile 격리와 미사용 HMAC/JJWT/`JWT_SECRET_KEY` 제거를 구현했고, 사용자가 PR 병합과 테스트 성공을 확인했다.
- 전환 직전 상태는 `진행 중`(상태 ID `10001`)이었고 `완료` 전환 ID `41`이 사용 가능했다.
- 사용자 요청에 따라 TMI-14에 전환 ID `41`만 전송했다. 전환 Payload에 다른 필드·업데이트·댓글을 포함하지 않았고 다른 Jira 이슈를 수정하지 않았다.
- 후속 상세 조회에서 상태 `완료`와 resolution `완료`를 확인했다. resolution은 완료 전환 워크플로가 자동으로 설정했으며 별도 필드 수정으로 지정하지 않았다.
- Jira 완료 댓글은 등록하지 않았다.

## Earlier completed Jira issue

- [`TMI-11`](https://to-teacher.atlassian.net/browse/TMI-11) — [Integration] Identity·Learning Core E2E 인증 테스트 및 JWT 계약 확정
- 이슈 유형: `작업` (ID `10003`)
- Jira 상태: `완료` (상태 ID `10003`, resolution `완료` ID `10000`)
- 우선순위: `High` (ID `2`)
- 담당자: 미지정
- 생성 시각: `2026-07-28T12:30:27.701+0900`
- Identity 회원가입·로그인부터 Learning Core 시험 소유권, 실패 Token, Refresh Token Rotation·로그아웃, 공개 AI Callback, Python AI `user_id = examId` 계약까지 실제 두 서버 E2E로 검증하는 작업이다.
- 완료 댓글 ID `10002`에 구현 파일, 자동화 범위, JWT 계약, 정적·Gradle 테스트 결과, 실제 서버 E2E 미실행과 수동 DB 확인 잔여 항목을 기록했다.
- 사용자 요청에 따라 완료 전환 ID `41`을 실행하고 상태와 resolution을 재조회해 확인했다.
- 로컬 구현과 정적 검증, 두 저장소 전체 테스트는 완료했다. 실제 Identity 8081과 Learning Core 8080이 실행 중이지 않아 두 서버 E2E 실행과 직접 `ExamSession.userId` 확인은 후속 운영 검증으로 남아 있다.

## Related completed Jira issue

- `TMI-10` — [Learning Core] Identity JWKS 기반 JWT 인증 연동
- Jira 상태: `완료` (상태 ID `10003`, resolution `완료` ID `10000`)
- 2026-07-28 테스트 결과와 PR #8을 Jira 댓글 ID `10001`로 기록했다.
- 완료 전환 ID `41` 실행 후 상태와 resolution을 재조회해 확인했다.

## Completed

- 기존 웹 POC 백엔드에서 앱용 Learning Core 분리
- trial API와 terminate API 제거
- `ExamSession`에 `examId`, 실제 `userId`, `createdAt` 저장
- `CurrentUserProvider` 추상화와 `LegacyCurrentUserProvider` 유지
- `ExamResult.userId` 저장과 시험 소유권 검증 유지
- Feedback Callback의 `examId -> ExamSession -> 실제 userId` 매핑 유지
- Spring Security OAuth2 Resource Server 의존성 추가
- `APP_AUTH_MODE` 기반 Legacy/JWT 조건부 보안 구성 추가
- 기본값 `legacy`에서 기존 전체 `permitAll` 웹 흐름 유지
- `jwt` 모드에서 Identity JWKS를 명시적으로 사용하는 RS256 검증 구성
- issuer, audience, exp, nbf, UUID subject 검증 구성
- `JwtCurrentUserProvider`에서 JWT `sub`를 실제 사용자 UUID로 변환
- JWT 모드의 사용자용 API 인증 강제와 Callback·Swagger·OpenAPI·health 공개 경로 구성
- Security 계층의 401/403을 기존 `BaseResponse` JSON 구조로 반환
- 테스트용 JWKS endpoint와 합성 RSA 키를 이용한 JWT Resource Server 통합 테스트 추가
- PR [#8](https://github.com/Too-Much-I/app-back-end-learning-core/pull/8) merge 완료 및 CodeRabbit 체크 성공 확인
- Jira `TMI-10` 테스트·PR 댓글 등록과 완료 처리
- AI 문항 피드백을 요청한 `examId + questionNumber + retryCount` 범위의 최신 `_id` 문서로 조회하도록 보완
- 종합 피드백을 문항별 `exam_results`와 분리된 `exam_summaries` 컬렉션에 저장하도록 보완
- 종합 피드백 조회 시 `exam_summaries`의 최신 `_id` 문서를 우선하고, 분리 전 `exam_results`의 최신 종합 문서를 fallback하도록 보완
- PR #9로 최신 피드백 조회·종합 피드백 저장소 분리 변경을 `main`에 merge
- Identity·Learning Core E2E 인증 통합 테스트 후속 Jira Payload 초안과 지원 필드 검증 완료
- Learning Core 운영 JWT 모드 강제 및 Legacy/HMAC 인증 정리 후속 Jira Payload 초안과 지원 필드 검증 완료
- Jira `TMI-14` 생성과 승인된 제목·설명·유형·우선순위·기본 상태 재조회 검증 완료
- Jira `TMI-14` 생성 turn의 전용 Stop Hook marker 기록 완료
- Jira `TMI-14` 현재 상태와 가능한 전환의 읽기 전용 조회 완료
- Jira `TMI-14`를 다른 필드 변경 없이 `진행 중`으로 전환하고 재조회 검증 완료
- Jira `TMI-11` 생성과 제목·설명·유형·상태·우선순위 재조회 검증 완료
- Jira `TMI-11` 작업 결과 댓글 ID `10002` 등록과 완료 처리
- `scripts/e2e/auth-integration-test.sh`에 실제 두 서버용 JWT 인증 E2E 자동화 추가
- `scripts/e2e/README.md`에 실행 전제, 환경변수, 정리 정책, 수동 DB 검증과 운영 실행 금지 안내 추가
- `docs/contracts/identity-learning-jwt.md`에 RS256·`kid`·Claim·JWKS·사용자 식별·AI·로그아웃 계약 확정
- Jira TMI-14 제한 예외를 `AGENTS.md`에 명시하고 다른 작업·경로·외부 계약으로 확대되지 않음을 재검토
- `AuthMode.LEGACY`·`AuthMode.JWT`와 `AuthProperties`를 추가하고 소문자 `legacy`·`jwt`만 허용하도록 설정 바인딩 검증
- staging/prod에서 JWT 모드와 비로컬 issuer·JWKS URL·audience를 강제하고 설정 형식만 검사하는 `AuthStartupValidator` 추가
- Legacy Provider와 Legacy SecurityFilterChain을 `local`·`test` profile로 제한하고 staging/prod 강제 등록 탐지 추가
- 미사용 `JwtAuthenticationFilter`, `JwtTokenProvider`, JJWT 의존성, `jwt.secret`, `JWT_SECRET_KEY` 설정 제거
- 기존 OAuth2 Resource Server, RS256·issuer·audience·timestamp·UUID subject 검증과 보호·공개 경로 유지
- README와 JWT 계약·E2E 실행 문서에 local/test Legacy와 staging/prod JWT 환경 규칙 반영
- 사용자가 TMI-14 PR 병합과 테스트 성공을 확인한 뒤 Jira `TMI-14`를 다른 필드·댓글 변경 없이 `완료`로 전환하고 상태와 자동 resolution을 재조회해 검증 완료
- Learning Core 시험 단위 재채점과 AI 채점·Callback 멱등성 보장 Jira Payload 초안 작성 및 TMI 생성 필드·`High` 지원 여부 검증 완료. Jira 이슈는 생성하지 않음
- 승인된 채점 복구·멱등성 Payload로 Jira `TMI-25`를 `작업`, `High`, 기본 상태 `해야 할 일`로 생성하고 제목·설명·미지정 담당자·빈 라벨을 재조회해 검증 완료
- Jira `TMI-25` 구현 전 제출·Callback·요약·상태·소유권·MockExam·S3·Mongo·테스트 구조 정적 분석과 Question/Summary Job·retry·Callback 멱등성·legacy 호환 설계 완료
- Jira `TMI-25`에만 적용되는 신규 retry API와 채점·Callback 멱등성 구현의 제한적 호환성 예외를 `AGENTS.md`에 명시
- Jira `TMI-25`의 시험 단위 retry API, Question/Summary Job, submit·Callback·요약 dispatch 멱등성, Job 기반 전체 상태 산정과 legacy 결과 지연 복구 구현 완료
- Question/Summary AI 요청에 안정적인 `Idempotency-Key`를 추가하고 S3 `HeadObject` 기반 누락/복구 분기, 양수 timeout·최소 attempt 설정 검증과 UTC `Clock` Bean 추가
- TMI-25 집중·회귀 테스트와 전체 `./gradlew clean test` 126개 성공, 외부 인프라 호출 없음

## TMI-25 implementation state

- 신규 `POST /api/v1/exams/{examId}/grading/retry`는 Request Body 없이 기존 `BaseResponse`로 `examId`, `overallStatus`, retried/waiting/missing 문항 번호와 `summaryAction`을 반환하며 기존 소유권 검증을 먼저 수행한다.
- `QuestionGradingJob`의 결정적 `_id`는 `question:{examId}:{questionNumber}:{retryCount}`, `SummaryGradingJob`은 `summary:{examId}:v1`이다. 두 문서 모두 상태·dispatch 횟수·필수 시각·실패 정보와 Mongo `@Version`을 가진다.
- submit은 결정적 Job `insert`에 성공한 최초 요청만 `PROCESSING`으로 optimistic claim하고 AI를 호출한다. PENDING/PROCESSING/COMPLETED 및 기존 FAILED Job의 동일 submit은 새 요청을 만들지 않는다.
- 시험 retry는 `mock_exam_003`의 `MockExam.questions`에서 예상 문항을 읽고 retryCount 0만 처리한다. fresh PENDING/PROCESSING은 대기하고 stale PENDING/PROCESSING 또는 시도 한도 미만 FAILED만 optimistic claim 후 재전송한다.
- Job이 없으면 기존 S3 Key에 `HeadObject`를 수행한다. 404만 미제출로 분류하고 객체가 있으면 PENDING Job을 복구해 dispatch하며 403·timeout·인프라 오류는 미제출로 오인하지 않는다.
- Feedback·SpeechAce·Azure·전체 요약 결과는 논리 키의 legacy 존재 여부를 먼저 확인하고 결정적 `_id`로 `insert`한다. 동시 중복의 `DuplicateKeyException`은 멱등 성공으로 처리하며 기존 결과에 Unique Index나 자동 마이그레이션을 적용하지 않는다.
- Feedback Callback은 Question Job을 COMPLETED로 전이하거나 legacy 시험의 누락 Job을 복구한 뒤 매번 요약 gate를 확인한다. 11번 특별 Trigger는 제거했고 모든 필수 retryCount 0 결과 또는 COMPLETED Job이 있어야 Summary Job을 한 번 claim한다.
- 시험 retry는 문항 작업이 남지 않았을 때만 Summary Job을 처리한다. 요약 fresh PROCESSING은 `WAITING`, stale PROCESSING/FAILED는 재전송, 완료는 `ALREADY_COMPLETED`, Job 없음은 생성·dispatch한다.
- 전체 상태는 retryCount 0 결과와 Question/Summary Job을 일괄 조회해 산정하고 기존 `exam:status:{examId}`와 1시간 TTL에 캐시한다. 기존 status DTO와 `progressPercent=60`은 프론트 계약을 위해 유지한다.
- AI multipart/JSON Body와 `user_id = examId`는 유지하고 Header만 `question:{examId}:{questionNumber}:{retryCount}` 또는 `summary:{examId}:v1`로 추가했다.
- 설정 기본값은 pending `PT1M`, processing `PT3M`, max dispatch attempts `3`이며 Duration 양수와 attempt 1 이상을 검증한다. 신규 시간 로직은 UTC `Clock` Bean만 사용한다.

### Approved limited exception

- TMI-25에 한해 `POST /api/v1/exams/{examId}/grading/retry`, 해당 API 전용 DTO, Question/Summary Job, 기존 submit·Callback의 멱등 내부 처리와 모든 필수 retry 0 문항 완료 기반 요약 Trigger를 구현할 수 있다.
- 기존 status API는 URL·Method·기존 필드를 유지하면서 Job 기반으로 내부 상태 산정을 변경할 수 있다.
- 기존 API URL·Method·Request Parameter·Response 필드, retryCount 의미, AI `user_id = examId`, Redis/S3 Key, 소유권 검증은 변경할 수 없다.
- retryCount>0 사용자 새 녹음의 시험 전체 복구, 프론트 문항 목록 전달, 별도 외부 summary retry API는 허용되지 않는다.
- 이 예외는 TMI-25 전용이며 다른 Jira나 후속 작업에 승계되지 않는다.

## Authentication modes

### Legacy

- `APP_AUTH_MODE=legacy` 또는 모드 설정 누락은 `AuthMode.LEGACY`로 해석되지만 `local`·`test` profile에서만 활성화된다.
- active profile이 없거나 staging/prod에서 Legacy 모드를 선택하면 시작에 실패한다.
- `LegacyCurrentUserProvider`만 `CurrentUserProvider`로 등록한다.
- JWT Resource Server와 `JwtDecoder`는 등록하지 않는다.
- Identity 서버와 Authorization 헤더 없이 기존 API를 호출할 수 있다.

### JWT

- `APP_AUTH_MODE=jwt`일 때만 활성화된다.
- `JwtCurrentUserProvider`만 `CurrentUserProvider`로 등록한다.
- `IDENTITY_JWK_SET_URI`를 직접 사용하므로 OIDC discovery를 전제하지 않는다.
- RS256 서명, issuer, audience, exp, nbf, UUID subject를 검증한다.
- JWT `sub`를 정규화된 UUID 문자열로 반환해 `ExamSession.userId`와 소유권 검증에 사용한다.

## TMI-14 Startup validation

- `APP_AUTH_MODE`는 소문자 `legacy`와 `jwt`만 허용한다. 빈 값, 대문자 표기, 오타와 지원하지 않는 값은 안전한 오류로 시작 실패하며 Legacy로 fallback하지 않는다.
- JWT 모드는 모든 profile에서 issuer·JWKS URL·audience의 존재와 HTTP(S) URI 형식을 검증한다.
- 공통 설정에는 Identity 기본값을 두지 않고 `application-local.yml`에서만 localhost issuer·JWKS URL과 개발 audience 기본값을 제공하므로 staging/prod 누락이 local 기본값으로 숨지 않는다.
- staging/prod는 JWT 모드만 허용하고 localhost·loopback Identity URL과 placeholder audience를 거부한다.
- 검증 단계에서 Identity 또는 JWKS endpoint로 네트워크 요청을 보내지 않는다.
- staging/prod에서 `LegacyCurrentUserProvider` 또는 `legacySecurityFilterChain`이 강제로 등록되면 시작 실패한다.
- local/test에서는 명시적으로 Legacy를 사용할 수 있고 Identity 연결 없이 기존 웹 호환 흐름이 동작한다.

## Public paths in JWT mode

- `/api/v1/exams/callback/**`
- `/swagger-ui/**`
- `/swagger-ui.html`
- `/v3/api-docs`와 `/v3/api-docs/**`
- `/actuator/health`와 하위 경로. 현재 Actuator 의존성은 없어 실제 health endpoint는 생성되지 않는다.

## Protected paths in JWT mode

- 위 공개 경로를 제외한 모든 요청은 authenticated다.
- 시험 생성, 상태·종합·문항 결과 조회, 업로드 URL, 음성 제출, 문항 Polling 등 기존 사용자용 시험 API가 포함된다.
- `examId`를 받는 사용자용 API의 기존 `ExamSession.userId == CurrentUserProvider.getCurrentUserId()` 소유권 검증을 유지한다.

## TMI-11 E2E automation

- `IDENTITY_BASE_URL` 기본값은 `http://localhost:8081`, `LEARNING_CORE_BASE_URL` 기본값은 `http://localhost:8080`이다.
- 스크립트는 Identity health·JWKS, 두 사용자 회원가입/로그인, JWT Claim, Learning Core 401/403·시험 소유권, 잘못된 Token, Refresh Rotation·재사용 탐지, 단일·전체 로그아웃, 공개 Feedback Callback을 단계별 검증한다.
- Access/Refresh Token과 Token 또는 URL을 포함할 수 있는 전체 응답은 출력하지 않고, 실패 시 단계·HTTP 상태·최상위 안전 필드만 출력한다.
- 임시 파일은 제한된 권한의 임시 디렉터리에 두고 `trap`으로 삭제한다.
- 만료·잘못된 issuer·잘못된 audience Token은 공개 API로 안전하게 생성하지 않고 기존 `JwtSecurityIntegrationTest` 검증을 사용한다.
- 사용자·시험 삭제 API가 없으므로 계정과 시험 문서는 자동 삭제하지 않는다. 기본 모드에서는 남은 Refresh Session만 로그아웃하며 직접 DB 검증은 수동 항목이다.

## Latest feedback lookup assessment

- Azure retry 0 문항 피드백은 신규 결정적 ID, 정확한 `retryCount=0`, legacy BSON null, legacy 필드 누락 순서로 조회한다. retryCount>0은 결정적 ID와 정확한 회차만 사용한다.
- Azure의 null과 missing은 별도 Mongo 쿼리로 구분하며 ObjectId와 결정적 문자열 `_id`를 한 정렬에서 시간순으로 간주하지 않는다.
- AI 문항 피드백인 `ExamResult`도 `examId + questionNumber + retryCount` 조건에 `OrderByIdDesc`를 적용한 Repository 단건 조회로 최신 문서를 선택한다.
- 0회차 조회는 기존 `retryCount=null` 문서를 0으로 해석하던 호환성을 유지하기 위해 `retryCount in [0, null]` 조건을 사용한다.
- 문항 피드백 API는 클라이언트가 전달한 `retryCount` 회차를 조회하며 가장 큰 retryCount를 자동 선택하지 않는다.
- 신규 종합 피드백은 같은 MongoDB 연결의 별도 `exam_summaries` 컬렉션에 저장하고 `examId + OrderByIdDesc`로 최신 문서를 조회한다.
- `exam_summaries`가 비어 있으면 분리 전 `exam_results`에서 `totalScore != null`인 최신 `_id` 문서를 조회해 기존 데이터를 계속 제공한다.

## Important contracts

- JWT `sub`는 실제 `userId`다.
- Access Token은 RS256이며 Header의 `kid`가 Identity JWKS Public Key를 선택한다.
- JWT issuer는 환경별 Identity 설정값이고 `scope`는 공백 구분 문자열이다.
- JWT audience는 `tosunsaeng-learning-core`다.
- Python AI 요청의 `user_id`는 계속 `examId`다.
- AI Callback의 `user_id`도 `examId`로 해석한다.
- 실제 `userId`를 Python AI 서버로 보내지 않는다.
- 클라이언트 Request Body, Path, Query, Response DTO에 `userId`를 추가하지 않는다.
- 기존 API URL·Method·Parameter·DTO·`BaseResponse`·`retryCount` 계약을 유지한다.
- TMI-25에서 허용된 신규 API는 `POST /api/v1/exams/{examId}/grading/retry` 하나뿐이며 Request Body와 별도 summary retry API가 없다.
- 기존 status 응답 필드와 `progressPercent=60`, Redis `exam:status:{examId}`·1시간 TTL을 유지한다.
- 기존 Redis Key·TTL, S3 Presigned URL·Object Key, 음성 제출·Polling 흐름을 유지한다.
- AI Body 계약은 그대로 두고 `Idempotency-Key` Header만 Question/Summary 논리 키로 추가한다.
- 종합 피드백 저장소 분리는 MongoDB 연결·database 설정을 추가하지 않고 컬렉션만 `exam_summaries`로 분리했다.
- 운영 앱에서는 Legacy 모드를 금지한다.
- `logout`과 `logout-all`은 Refresh Session을 폐기하지만 기존 Access Token의 즉시 무효화를 보장하지 않는다.

## Test status

- 사용자 지정 최종 재검증에서 `git diff --check`와 `node --check scripts/mongodb/tmi-31-migrate-exam-assignment.js`가 종료 코드 0, `./gradlew clean test`가 `BUILD SUCCESSFUL`로 완료됐다.
- TMI-31 최신 finding 수정 후 정확한 `./gradlew clean test`가 성공했다: Java 200개 테스트, 실패·오류·건너뜀 0개.
- migration은 `node --check`와 `node --test scripts/mongodb/tmi-31-migrate-exam-assignment.test.js` 25개가 성공했다. DB 필수 선택, 환경 DB 우선, 시스템 DB 거부, Summary와 legacy totalScore 완료 증거/backfill, assignable 선판정, 제외 catalog, 중복 Summary/ID, 인덱스 필드와 URI 비출력을 검증했다.
- TMI-31 집중 테스트에서 완료 이력별 sequence 선택과 cycle 증가, 비활성·빈 시험 제외, legacy sequence fallback, 중복·해석 불가 sequence 실패와 다른 사용자 이력 격리를 확인했다.
- 진행 중 세션 재사용 시 같은 `examId`와 새 Presigned URL, Redis 누락 복구, 동시 insert unique 충돌 시 승자 세션 재조회와 활성 세션 1개 유지를 검증했다.
- Summary 저장 성공 뒤 원자적 세션 완료, 중복 Callback no-op, 저장 실패·문항 완료만으로는 미완료임을 검증했다.
- 선택된 `mockExamId`가 문제 조회·S3 문제 음성·Question/Summary Job·문항/요약 AI 요청·시험 retry 예상 문항·상세 결과 조회까지 전파되고 legacy Session/Job은 세션 또는 `mock_exam_003`으로 fallback함을 확인했다.
- `POST /api/v1/exams` Request Body 없음, 기존 `CreateSessionResult`·`BaseResponse`, AI `user_id=examId` 계약이 유지되는 회귀 테스트가 성공했다.
- migration 스크립트는 기본 dry-run이고 명시적 `TMI31_APPLY=true`에서만 write 함수를 실행하도록 검증했다. 이 환경에는 `mongosh`와 실제 DB가 없어 실제 staging dry-run/apply는 수행하지 않았다.
- TMI-25 finding 집중 테스트가 성공했다. 실제 Atlas·S3·Redis·Python AI 서버는 호출하지 않고 Repository, S3Client, RestTemplate과 executor 경계를 Mockito/단위 테스트로 검증했다.
- Question/Summary attempt 1 HTTP를 timeout까지 대기시킨 뒤 attempt 2를 claim하고 attempt 1 실패를 늦게 도착시켜 최신 PROCESSING/attempt 2, null `failedAt`·`failureReason`을 확인했다.
- Callback Summary gate의 FAILED/stale PROCESSING 비재시도, grading retry의 recovery scheduling, 중복 task 단일 HTTP, queue rejection PENDING 유지, HTTP timeout의 claimedAttempt 조건 실패 전이를 확인했다.
- legacy Feedback `retryCount=null/0`과 Job 부재·기존 FAILED Job submit 복구, Azure null/missing retry 0 조회와 retry 1 격리, executor 크기·queue와 connect/read timeout 설정을 확인했다.
- TMI-25 집중 테스트에서 최초·반복·동시 submit, 상태/timeout/attempt별 시험 retry, S3 HeadObject 404·403, retryCount>0 제외와 concurrent claim을 검증했다.
- 네 Callback의 결정적 ID·중복 1개 저장, legacy null retry 결과와 누락 Job 복구, 11번 단독 요약 금지, 전체 필수 문항 완료 후 요약 1회와 요약 timeout/FAILED retry를 검증했다.
- AI `user_id = examId`, 기존 multipart/summary Body, 안정적인 두 `Idempotency-Key`, 신규 API의 Request Body 없음·기존 BaseResponse, status `progressPercent=60`, 소유권 검증을 확인했다.
- AuthMode의 legacy/JWT 변환, 누락 시 local Legacy 기본값, 빈 값·대문자·오타 실패 검증 성공
- local/test Legacy 성공, profile 없는 Legacy와 staging/prod Legacy 실패, staging/prod 정상 JWT 설정 성공 검증
- staging/prod issuer·JWKS URL·audience 누락, URI 형식 오류, localhost·loopback, placeholder audience 실패 검증
- local/test Legacy Provider 등록과 staging/prod 미등록, 강제 Legacy Provider·FilterChain 등록 실패 검증
- Legacy/JWT 모드별 `CurrentUserProvider`, `SecurityFilterChain`, `JwtDecoder` 단일 등록 검증
- HMAC 두 클래스 부재, JJWT 의존성과 `JWT_SECRET_KEY`·`jwt.secret` 활성 설정 부재 검증
- 기본 Legacy 모드, 무인증 Legacy API 접근, Legacy/JWT 빈 상호 배타 등록 검증 성공
- 테스트용 JWKS HTTP endpoint를 통한 유효 RS256 Token 시험 생성과 `ExamSession.userId` 저장 검증 성공
- 동일 사용자 접근 성공, 다른 사용자 접근 BaseResponse 403 검증 성공
- Token 없음, 잘못된 서명, 만료, 미래 nbf, 잘못된 issuer·audience, UUID가 아닌 sub의 BaseResponse 401 검증 성공
- AI Callback 무인증 접근과 Callback `user_id = examId` 흐름 검증 성공
- 기존 테스트에서 AI multipart/summary 요청의 `user_id = examId`, 외부 userId 미노출, Callback 실제 userId 매핑 검증 성공
- `git diff --check` 성공
- 실제 Identity 프로세스·Atlas·AWS·Redis·Python AI 서버는 테스트에서 호출하지 않았다.
- Jira 완료 처리 작업에서는 애플리케이션 코드를 변경하지 않아 테스트를 다시 실행하지 않았고, 직전 구현 작업의 53개 전체 성공 결과를 댓글에 기록했다.
- 최신 문항 피드백 조회 여부 분석 작업에서는 코드를 변경하지 않아 테스트를 실행하지 않았다.
- 같은 문항·retryCount의 구·신규 `ExamResult`가 함께 있을 때 최신 결과를 응답하는 테스트와 0회차 null 호환 조회 검증 성공
- 종합 Callback이 `ExamSummary`만 저장하고 `ExamResult`에는 저장하지 않는지, `ExamSession.userId` 매핑과 Redis 완료 상태가 유지되는지 검증 성공
- 최신 `exam_summaries` 조회와 새 컬렉션이 비어 있을 때 최신 legacy `exam_results` 종합 문서 fallback 검증 성공
- Atlassian MCP에서 TMI 프로젝트, `작업` 유형, 설명 필드와 `High` 우선순위 지원 여부를 읽기 전용으로 확인했다. 애플리케이션 코드를 변경하지 않아 이번 초안 작업에서는 Gradle 테스트를 다시 실행하지 않았다.
- Atlassian MCP에서 운영 JWT 보안 정리용 TMI 생성 권한과 `작업`·설명·`High` 필드를 재검증하고 동일 제목 중복이 없음을 확인한 뒤 `TMI-14`를 생성했다. 생성 후 승인된 제목·설명, `작업`, `High`, 기본 상태 `해야 할 일`, 담당자 미지정과 빈 라벨을 재조회했다. 초안 및 생성 turn의 필수 marker가 각각 정확히 한 번 존재하고 `git diff --check`가 성공했다. 애플리케이션 코드는 변경하지 않아 Gradle 테스트를 실행하지 않았다.
- Atlassian MCP로 `TMI-14`의 현재 상태 `해야 할 일`과 사용 가능한 전환 `해야 할 일(11)`·`진행 중(21)`·`검토 중(31)`·`완료(41)`를 직접 조회했다. Jira 변경 API와 애플리케이션 코드는 호출·수정하지 않아 Gradle 테스트를 실행하지 않았다.
- Atlassian MCP로 `TMI-14`의 `진행 중` 전환 ID `21`을 실행하고 상태 ID `10001`을 후속 재조회했다. 전환 Payload에는 다른 필드·댓글·업데이트가 없었고 다른 Jira 이슈를 수정하지 않았다. 애플리케이션 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.
- 사용자 확인 기준 TMI-14 PR 병합과 테스트가 성공했다. Atlassian MCP로 `진행 중` 상태와 사용 가능한 `완료` 전환 ID `41`을 확인한 뒤 TMI-14에 해당 전환만 실행했고, 후속 조회에서 상태 `완료`(ID `10003`)와 워크플로가 자동 설정한 resolution `완료`(ID `10000`)를 확인했다. 애플리케이션 코드 변경이 없어 Gradle 테스트는 다시 실행하지 않았다.
- TMI-14 구현 전 `AGENTS.md`, CURRENT_STATE와 Jira 설명·완료 조건을 대조했다. “JWT 인증 강제” 금지와 staging/prod JWT 모드 강제 요구의 충돌로 구현을 시작하지 않았고, 코드 변경이 없어 인증 모드 테스트와 Gradle 테스트를 실행하지 않았다.
- Atlassian MCP로 `TMI-11`을 생성한 뒤 제목·설명·프로젝트·이슈 유형·상태·우선순위를 재조회해 승인된 Payload 반영을 확인했다. 애플리케이션 코드는 변경하지 않았다.
- Stop Hook 보완 기록의 필수 marker 단일 존재와 `git diff --check`를 검증했다.
- TMI-11 정적 검증: `bash -n scripts/e2e/auth-integration-test.sh`, JWKS/Claim jq filter 샘플, 비대화형 비밀번호 누락 오류, `git diff --check` 성공
- ShellCheck는 로컬에 설치돼 있지 않아 자동 설치하거나 실행하지 않았다.
- Learning Core `./gradlew clean test` 성공: 56개, 실패·오류·건너뜀 0개. 기존 `ExamServiceImpl` unchecked 경고만 남았다.
- Identity 저장소 `./gradlew clean test` 성공: 138개, 실패·오류·건너뜀 0개. Identity 소스와 추적 파일은 변경하지 않았다.
- 기본 8081/8080 포트 모두 연결되지 않아 실제 E2E 스크립트는 실행하지 않았다.
- 이번 Jira Payload 초안 작업에서는 Atlassian MCP로 TMI 생성 권한, `작업` 유형, 설명과 `High` 우선순위 지원 및 동일 제목 후보 부재를 읽기 전용으로 확인했다. 애플리케이션 코드 변경이 없어 `./gradlew clean test`는 실행하지 않았다.
- Atlassian MCP로 `TMI-25`를 생성한 뒤 제목·설명·프로젝트·유형·우선순위·기본 상태·담당자·라벨을 재조회했다. 애플리케이션 코드 변경이 없어 `./gradlew clean test`는 실행하지 않았다.
- Atlassian MCP로 `TMI-25`의 현재 상태와 사용 가능한 전환을 읽기 전용으로 조회했다. 현재 상태는 `해야 할 일`이고 전환 `11`·`31`·`21`·`41`이 모두 사용 가능했다. Jira 변경 API와 애플리케이션 코드를 호출·수정하지 않아 `./gradlew clean test`는 실행하지 않았다.
- Atlassian MCP로 TMI-25의 전환 직전 상태와 `진행 중` 전환 ID `21`을 재확인한 뒤 전환 ID만 적용하고, 후속 조회에서 상태 ID `10001`을 확인했다. 애플리케이션 코드 변경이 없어 `./gradlew clean test`는 실행하지 않았다.
- TMI-25 구현 전 분석에서는 관련 소스·설정·테스트와 Jira 설명·완료 조건을 정적으로 확인하고 `git diff --check`를 실행했다. 애플리케이션 구현 변경이 없어 `./gradlew clean test`는 실행하지 않았다.

## HMAC cleanup

- 저장소 전수 검색에서 `JwtAuthenticationFilter`와 `JwtTokenProvider`는 Bean·FilterChain·비즈니스 코드에서 사용되지 않고 서로만 참조함을 확인했다.
- 두 HMAC 클래스와 전용 JJWT API·runtime 의존성을 삭제했다.
- `application.yml`과 테스트 설정에서 `jwt.secret`·`JWT_SECRET_KEY`를 제거했으며 공유 HMAC Secret은 더 이상 필요하지 않다.
- 활성 런타임 소스·설정·빌드에서 HMAC 클래스, `addFilterBefore`, JJWT와 공유 Secret 잔여 사용처가 없음을 확인했다.
- JWT 인증 책임은 기존 Identity JWKS 기반 OAuth2 Resource Server에만 남아 있다.

## Known risks

- 운영 배포 전에 `scripts/mongodb/tmi-31-migrate-exam-assignment.js`를 먼저 dry-run으로 실행해 중복 sequence, 여러 legacy 활성 세션과 호환되지 않는 기존 인덱스를 해소한 뒤 `TMI31_APPLY=true`로 사용자당 활성 세션 partial unique index를 설치해야 한다. 인덱스가 없으면 다중 인스턴스 동시 요청의 단일 활성 세션 보장이 완성되지 않는다.
- legacy `active` 누락/null이면서 `completedAt`도 없는 세션이 사용자당 여러 개면 런타임은 최신 세션을 선택하고 경고하지만 migration apply는 운영자 조정 전 중단한다. 자동 데이터 migration은 의도적으로 없다.
- 활성 세션 재사용 시 해당 `MockExam`이 삭제됐거나 문제가 비어 있으면 안전하게 설정 오류로 실패한다. 진행 중 시험의 문항 구성을 운영 중 변경하지 않는 정책이 필요하다.
- TMI-31 테스트는 실제 Atlas·Redis·S3·Python AI 서버를 호출하지 않았다. partial unique index 충돌, Presigned URL과 AI multipart/JSON의 실제 인프라 연동은 staging smoke test가 필요하다.
- Summary 문서 insert와 ExamSession 완료 update는 서로 다른 Mongo 연산이므로 둘 사이 프로세스 중단 window가 남는다. Summary가 저장된 Callback은 멱등 재전달되면 세션 완료가 복구되므로 Python AI/인프라의 Callback 재시도 정책을 staging에서 확인해야 한다.
- Jira `TMI-10`은 사용자 요청에 따라 완료 처리됐지만 실제 Identity와 Learning Core 로컬 E2E는 수행하지 않았다. 테스트용 JWKS 경계 통합 검증으로 대체한 상태다.
- 배포 환경에서 JWT 모드를 활성화하기 전에 실제 Identity issuer·JWKS 주소와 audience 설정을 확인해야 한다.
- Nimbus 기본 clock skew가 적용되므로 exp와 nbf 경계에는 표준 허용 오차가 있다.
- JWKS key rotation과 Identity 장애 시 캐시 동작은 실제 환경에서 별도 점검이 필요하다.
- AI Callback은 의도대로 공개 상태이며 서비스 간 인증은 범위 밖이다.
- `APP_AUTH_MODE`는 소문자 `legacy` 또는 `jwt`만 허용되며 잘못된 값은 시작 실패한다.
- 최신 저장 순서는 MongoDB가 자동 생성하는 `_id` 내림차순을 기준으로 판단한다.
- 기존 `exam_results`의 종합 문서는 삭제·이관하지 않고 읽기 fallback으로 유지한다.
- 물리적으로 별도 MongoDB database나 클러스터를 요구한다면 별도 연결 설정과 운영 값이 추가로 필요하다. 현재 구현은 같은 database 내 컬렉션 분리다.
- 데이터가 커지면 `exam_summaries`의 `examId + _id` 조회용 복합 인덱스를 운영 환경에서 검토해야 한다.
- TMI-11 스크립트 구현은 완료했지만 실제 Identity 8081과 JWT 모드 Learning Core 8080이 기동되지 않아 실서버 E2E 결과는 아직 없다.
- Jira 완료 조건의 `ExamSession.userId == JWT sub` 직접 DB 비교는 MongoDB 자격증명을 스크립트에 넣지 않기 위해 수동 검증으로 남겼다. 소유권 200/403 시나리오는 API 경계에서 간접 검증한다.
- AI Callback은 사용자 JWT 없이 공개 상태이며 서비스 간 인증은 아직 없다.
- 사용자·시험 삭제 API가 없어 로컬 E2E 계정과 시험 문서는 테스트 DB에 남으며 운영자 정리가 필요하다.
- Startup Validator는 설정 형식과 로컬 URL 사용 여부만 확인하며 실제 staging/prod Identity·JWKS 네트워크 도달성은 배포 전 별도 확인이 필요하다.
- staging/prod 전체 애플리케이션을 실제 운영 인프라 설정으로 기동하는 smoke test는 수행하지 않았고 외부 호출 없는 ApplicationContext 검증으로 대체했다.
- Learning Core가 안정적인 `Idempotency-Key`를 보내더라도 Python AI가 그 키를 실제 처리하기 전까지는 AI 서버 내부 중복 실행까지 단독으로 보장할 수 없다.
- DB Job claim과 외부 AI HTTP 요청은 단일 트랜잭션이 아니므로 Python AI가 멱등 키를 처리하기 전까지 crash window의 정확히 한 번 실행은 보장할 수 없다.
- S3 `HeadObject`는 404만 미제출로 분류한다. 운영 IAM에 대상 버킷 객체 조회 권한이 없으면 403이 API 오류로 전파되므로 배포 전 권한을 확인해야 한다.
- 기존 결과의 ObjectId와 신규 결정적 문자열 `_id`가 혼재하면 `_id DESC`가 생성 시간순이 아닐 수 있으며, legacy 중복은 현재 파트 점수와 풀이 문항 수를 부풀릴 수 있다.
- 기존 결과의 중복은 삭제하지 않고 논리 존재 확인으로 신규 중복만 막는다. 운영 중복 정리가 필요하면 별도 검토·백업 후 명시적 일회성 스크립트로 수행해야 한다.
- AI `RestTemplate`은 connect/read timeout 기본값 `PT3S`/`PT30S`를 갖지만, 문항 음성을 계속 전체 `byte[]`로 읽으므로 시험 단위 다문항 복구의 메모리 사용과 timeout 적정값을 운영 부하에서 확인해야 한다.

## Next

- 운영 데이터 백업 후 TMI-31 migration을 먼저 dry-run하고 보고된 sequence·legacy 활성 세션 문제를 조정한 뒤 명시적 apply로 필드 보정과 `uniq_exam_sessions_active_user` 인덱스를 설치한다.
- staging에서 같은 사용자 동시 `POST /api/v1/exams`, 활성 세션 재사용, 순차·순환 배정, Summary Callback 완료 전이와 선택된 `mockExamId`의 S3·Python AI 전파를 실제 MongoDB·Redis·S3·AI 연동으로 smoke test한다.
- Jira `TMI-31`은 사용자 요청에 따라 `완료`로 전환했다. 완료 댓글은 등록하지 않았고 다른 Jira 필드는 변경하지 않았다.
- Identity를 8081, Learning Core를 JWT 모드 8080으로 기동한 뒤 `scripts/e2e/auth-integration-test.sh`를 실행한다.
- 실제 E2E 성공 후 출력된 수동 확인 식별자로 `exam_sessions.userId`와 JWT `sub`를 폐기 가능한 로컬 DB에서 비교한다.
- 배포 환경에서 `APP_AUTH_MODE=jwt` 전환 전 issuer·JWKS·audience와 네트워크 접근성을 확인한다.
- 실제 배포 전에 staging/prod에 `APP_AUTH_MODE=jwt`, 환경별 issuer·JWKS URL·audience와 나머지 인프라 설정을 주입해 smoke test한다.
- Jira `TMI-14`는 완료됐으며 완료 댓글은 등록하지 않았다. 다시 열기나 댓글 등록은 사용자가 명시적으로 요청하는 경우에만 수행한다.
- Jira `TMI-10`은 완료됐으므로 후속 위험은 별도 Jira 이슈로 추적한다.
- 물리적으로 다른 MongoDB database가 필요한지 확인하고, 필요하면 별도 MongoTemplate·자격증명·배포 환경변수 범위를 정의한다.
- 운영 데이터 규모에 따라 `exam_summaries` 조회 인덱스와 legacy 종합 문서 이관·보존 정책을 결정한다.
- Jira `TMI-11`은 완료 처리됐으며 실제 서버 E2E나 수동 DB 검증에서 문제가 발견되면 이슈를 다시 열거나 별도 후속 이슈로 추적한다.
- Jira `TMI-25`는 `완료` 상태와 resolution `완료`로 닫혔으며 완료 댓글은 등록하지 않았다. 다시 열기나 댓글 등록은 사용자가 명시적으로 요청하는 경우에만 수행한다.
- Python AI가 두 `Idempotency-Key`를 실제 저장·중복 반환하도록 하는 후속 작업을 별도 이슈로 분리한다.
- 배포 전 staging에서 S3 HeadObject 권한, Mongo 신규 컬렉션 생성 권한과 AI Header 전달을 smoke test한다.
- 사용자가 변경분을 검토한 뒤 commit과 push를 수행한다.
