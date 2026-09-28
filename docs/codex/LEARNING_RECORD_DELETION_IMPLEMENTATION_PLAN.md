# Learning Core 학습 기록 독립 삭제 구현 계획

- 작성일: 2026-09-21
- 대상 저장소: `Too-Much-I/app-back-end-learning-core`
- 기준 브랜치/리비전: `develop@16eb5de`
- 제품 결정: `docs/codex/LEARNING_RECORD_DELETION_DECISION_OPTIONS.md`
- 상태: 2026-09-22 추가 제품 정책 승인·리뷰 보완 반영, runtime 미구현
- Jira: 없음

## 0. 결론

1. 계정·로그인·Token은 유지한 채 현재 사용자의 모의고사와 10초 Challenge 학습 기록 전체를 삭제한다.
2. 앱은 두 번 확인한 뒤 한 UUID `Idempotency-Key`를 operation이 끝날 때까지 재사용한다. Learning Core는 Request의 userId를 받지 않고 검증된 Access JWT의 `sub`를 사용한다.
3. 삭제 요청과 동시에 이전 학습 기록을 숨기고 새 학습 write를 일시 차단한다. cutoff·target inventory, Callback fence와 Billing coordination이 안전하게 확정되면 물리 정리 중에도 새 학습을 허용하고, durable worker가 24시간 안에 MongoDB·S3·Redis를 정리한다.
4. Billing 소비, 무료 기회 사용, AttemptGroup 전달, UserMerged/UserWithdrawn와 삭제 멱등성에 필요한 최소 증거는 삭제하지 않는다. 개인에게 재연결할 수 없는 서비스 집계 지표도 유지하되 사용자별 counter는 삭제한다. 삭제로 credit·무료 기회·응시권을 생성하거나 복원하지 않는다.
5. 단순 물리 정리 지연은 `CLEANUP_DELAYED`로 두고 새 학습을 허용한다. target·ownership·Billing·commit outcome이 불확실한 경우만 `NEEDS_REVIEW`로 차단하며, 두 경우 모두 같은 operation을 복구해 `COMPLETED`로 수렴시킨다.

이 문서는 구현 순서와 계약을 확정하는 계획서다. endpoint, collection, worker, mobile app 또는 운영 환경은 아직 변경되지 않았다.

### 0.1 반드시 읽을 추가 확정 사항 — 2026-09-22

- 삭제 안전 checkpoint 후에는 오늘의 Challenge도 다시 풀 수 있다. 삭제를 반복하면 당일 재참여도 가능하며, 이를 막는 별도 개인별 참여 제한 증거는 만들지 않는다. Billing credit·무료 시험 기회 복원 금지는 그대로다.
- 기존 삭제가 `COMPLETED`가 되기 전 다른 key의 추가 삭제는 명시적으로 거절한다. 같은 key 재전송은 기존 작업을 반환한다. 새 학습은 `canStartLearning`에 따라 계속 허용하지만, 정리가 지연되면 추가 삭제도 기다려야 한다. 추가 요청을 이전 cutoff에 합치거나 후속 작업으로 예약하지 않는다.
- 통계 v1은 발생일별 건수·주월 합계와 명확한 terminal 결과 구성비만 제공한다. 동일 시작 집단의 완료율은 제외한다. 수집 시작일부터 보장하고 과거는 근거가 있는 항목만 검증 후 반영하며, 미수집·복원 불가 구간을 0으로 표시하지 않는다. 하루 최대 18개 조합은 유지한다.
- 리뷰에서 지적한 guard 조건, Challenge 당일 slot 해제, GRADING drain/Callback 차단 분리, Billing 일회 승계, 집계 중복 방지와 앱 cache 보호는 아래 기술 명세·테스트에 반영했다. 이는 구현·검증 완료를 뜻하지 않는다.

추가 제품 선택은 확정됐다. 사용자가 다음에 확인할 사항은 §18의 계약·운영 검증이며, 본 문서 승인은 runtime 구현·Jira 생성·production 활성화 요청을 대신하지 않는다. 주요 잔여 위험은 Billing 실제 fixture 미검증, S3 권한/보존 설정 미검증, 집계 hot document 부하와 모바일 상태·캐시 계약 미검증이다.

## 1. 목표와 완료 정의

### 1.1 사용자 관점

- 설정의 독립 메뉴에서 “모든 학습 기록 삭제”를 실행한다.
- 모의고사와 Challenge 이력·결과·피드백·제출 음성이 요청 직후 화면에서 사라진다.
- 삭제 초기에는 새 시험 생성, upload URL 발급, 제출, 재채점, Challenge 시작·업로드·답변을 잠시 차단한다.
- cutoff·old target inventory, late Callback fence와 Billing OPEN tombstone이 안전하게 확정되면 물리 삭제가 끝나기 전에도 같은 계정으로 새 학습을 시작할 수 있다.
- 계정, 로그인 상태, Access/Refresh Token과 프로필은 그대로 유지된다.
- 사용한 credit·무료 응시 기회·Billing entitlement는 삭제 때문에 돌아오지 않는다.
- 삭제는 되돌릴 수 없다.

### 1.2 서버 완료 조건

`COMPLETED`는 다음 조건을 모두 만족할 때만 기록한다.

1. 요청 시점 `cutoffAt` 이하의 사용자 소유 모의고사·Challenge aggregate가 사용자 조회에서 보이지 않는다.
2. 해당 aggregate의 사용자 콘텐츠·결과·채점 산출물·Job·receipt를 MongoDB에서 제거했다.
3. 시험 Redis projection과 제출 S3 음성을 제거하고 final sweep으로 잔존 객체가 없음을 확인했다.
4. 기존 Presigned PUT의 잔여 capability가 끝난 뒤 S3 final sweep을 수행했다.
5. 늦은 AI Callback·worker가 삭제 데이터를 다시 만들지 못하는 tombstone/fence가 남아 있다.
6. Billing reservation/consumption, TrialClaim, AttemptGroup outbox와 보안·멱등성 증거가 승인된 보존 정책대로 유지된다.
7. 사용자별 누적 counter와 식별 가능한 analytics staging data가 남지 않고, 허용된 서비스 집계에는 사용자·시험 식별자가 없음을 검증했다.
8. operation 검증 count가 0이고 완료 Transaction이 commit되었다.

물리 삭제 목표는 요청 후 24시간이다. 24시간 초과 자체로 증거를 강제 삭제하거나 `COMPLETED`로 꾸미지 않는다. 재시도 한도 소진 시 안전 checkpoint가 확정된 정리 지연은 `CLEANUP_DELAYED`, 신규 데이터 손상·중복 차감 가능성이 있는 미확정 상태는 `NEEDS_REVIEW`로 전환한다.

## 2. 확정 범위와 비범위

### 2.1 포함

- 모의고사와 10초 Challenge의 전체 학습 기록 삭제
- 신규 삭제 command와 상태 조회 API
- 요청 즉시 read hide와 초기 write fence, 안전 checkpoint 후 신규 학습 재개
- durable Mongo operation, lease, retry, worker와 수동 복구 runbook
- MongoDB·S3·Redis 정리
- 진행 중 Exam/Challenge Job, 늦은 Callback과 Presigned URL 경합 방어
- Billing/AttemptGroup 최소 coordination evidence 보존
- 모바일 이중 확인, idempotency key 보존, 상태 표시와 로컬 cache 제거
- migration/index, default-off 설정, metric·alert·privacy-safe logging
- 서비스 전체·기간별 비식별 응시·채점 품질 집계와 사용자별 counter 삭제

### 2.2 제외

- 로그아웃, 회원 탈퇴, RefreshSession 폐기와 Token 재발급
- Identity recent-auth claim, 비밀번호·OTP·MFA 재인증
- 개별 시험, 개별 Challenge 또는 기간별 선택 삭제
- 삭제 취소·복원·유예기간
- credit, 무료 기회, paid entitlement, TrialClaim의 환불·복원·재발급
- Billing ledger, owner rebind, AttemptGroup 상태 머신 변경
- 기존 API URL·Method·Parameter·Response DTO, `BaseResponse`, `retryCount` 변경
- Python AI `user_id=examId`, Callback JSON, S3/Redis key 형식 변경
- 새 메시지 큐, 관리자 repair API, 실제 AWS/IAM/배포 변경
- catalog인 `mock_exams`, `questions`, `challenge_10s_questions`, `challenge_10s_catalog_state` 삭제
- 사용자별 lifetime 응시 횟수·평균 점수·최근 응시일 유지와 exact lifetime unique user 지표

## 3. 공개 API 계약 초안

신규 API만 추가하고 기존 공개 계약은 변경하지 않는다. 최종 구현 전에 모바일 담당자와 field casing 및 error code fixture를 고정한다.

### 3.1 삭제 command

```http
DELETE /api/v1/learning-records
Authorization: Bearer <access-token>
Idempotency-Key: <lowercase UUID v4>
```

- Request Body 없음
- userId Path/Query/Body 금지
- 정상 actor는 검증된 JWT `sub`
- `Idempotency-Key` 필수, canonical lowercase UUID v4만 허용
- 신규 또는 진행 중 요청은 HTTP `202 Accepted`
- 같은 key의 완료 replay는 HTTP `200 OK`
- 응답은 기존 `BaseResponse` envelope를 사용
- 진행 중인 operation에 다른 key가 오면 HTTP `409 LEARNING_RECORD_DELETION_ALREADY_ACTIVE`로 거절한다(신규 삭제 API 전용 오류 초안). 새 operation/command alias를 만들거나 기존 cutoff/target을 확장하지 않는다.
- 같은 key의 mapping 조회를 active operation 검사보다 먼저 수행한다. 과거 완료 key는 다른 작업이 진행 중이어도 그 과거 결과만 replay한다. 추가 전체 삭제는 기존 작업 완료 뒤 새 key로 다시 확인·요청해야 한다.
- response 유실 뒤 같은 key 재전송은 최초 operation을 반환

예상 `result`:

```json
{
  "deletionId": "6bf07092-08f6-4f27-82ef-fca4c56667e6",
  "status": "processing",
  "canStartLearning": false,
  "requestedAt": "2026-09-21T10:00:00Z",
  "physicalDeletionTargetAt": "2026-09-22T10:00:00Z",
  "completedAt": null
}
```

operation이 존재할 때 공개 status는 `processing | cleanup_delayed | completed | needs_review`를 사용한다. `canStartLearning`은 삭제 완료 여부가 아니라 현재 새 학습 안전성만 나타낸다. 내부 stage, collection명, S3 key, examId, attemptId, Billing 식별자와 failure 원문은 노출하지 않는다.

### 3.2 현재/최근 삭제 상태

```http
GET /api/v1/learning-records/deletion
Authorization: Bearer <access-token>
```

- Request Body와 userId 없음
- 최신 operation이 없으면 HTTP `200`과 조회 전용 `status=not_requested`
- 있으면 command와 동일한 공개 projection 반환
- 진행 중이면 `Retry-After`를 제공할 수 있으나 모바일은 장시간 foreground polling을 전제로 하지 않는다.
- 안전 checkpoint 전 `processing`은 `canStartLearning=false`, checkpoint 뒤 물리 정리 중 `processing`은 true다.
- `cleanup_delayed`는 true, blocking `needs_review`는 false, `completed`는 true다.
- `NEEDS_REVIEW`는 사용자용 고정 문구와 고객지원 안내만 반환하고 내부 실패 이유는 숨긴다.

### 3.3 삭제 중 기존 API 동작

| API 성격 | 동작 |
| --- | --- |
| 시험/Challenge history·calendar | 삭제 cutoff 이전 항목을 제외하여 빈 이력처럼 반환 |
| examId/attemptId 상세·결과 | 기존 not-found 계열 응답으로 숨김 |
| 시험 생성·upload URL·submit·retry | `canStartLearning=false` 동안 `409 LEARNING_RECORD_DELETION_IN_PROGRESS`; true면 신규 record만 정상 허용 |
| Challenge start·upload·answer | 같은 availability 규칙 적용 |
| 삭제 command/status | 삭제 fence 중에도 접근 허용 |
| 내부 AI Callback | Exam의 승인된 GRADING drain은 §9.1에 따라 계속 반영하고, 최종 `SEALED` target은 기존 성공 형태의 no-op 응답 |

`CLEANUP_DELAYED`에서는 old record hide를 유지하면서 신규 학습을 허용한다. blocking `NEEDS_REVIEW`에서만 write block을 유지한다. safe checkpoint 뒤 생성된 신규 기록은 operation 완료 전에도 정상 노출한다.

## 4. 모바일 앱 계약

1. `설정 → 모든 학습 기록 삭제` 진입점을 제공한다.
2. 1차 확인은 삭제 범위와 계정·로그인 유지 사실을 안내한다.
3. 2차 확인은 되돌릴 수 없음과 credit·무료 기회 미복원을 다시 안내한다.
4. 최종 확인 시 UUID v4 key를 한 번 생성하여 deletion이 terminal일 때까지 로컬에 저장한다.
5. timeout, 중복 탭, 앱 재실행과 네트워크 재시도에서 같은 key를 재사용한다.
6. 진행 중에는 같은 key로 복구하고 추가 전체 삭제 버튼은 비활성화한다. 다른 key의 요청은 `409 LEARNING_RECORD_DELETION_ALREADY_ACTIVE`로 처리하며 완료 후 새 확인·새 key가 필요하다. `cleanup_delayed`에서도 추가 삭제는 대기하지만 새 학습은 허용한다.
7. `canStartLearning=true`가 되면 물리 정리가 진행 중이어도 신규 학습 진입을 허용한다.
8. `cleanup_delayed`에서는 “기존 기록 정리가 진행 중이지만 새 학습은 가능”하다고 안내한다.
9. 삭제 접수가 확인되면 이전 시험/Challenge cache를 즉시 무효화하고, 진행 중이던 목록·결과 요청의 응답도 폐기한다. 앱의 로컬 요청/cache 세대와 deletionId를 사용해 접수 전 데이터와 새 학습 데이터를 구분한다. `completed`에서는 해당 삭제 세대의 잔여 cache만 정리하며 checkpoint 후 새 학습 cache를 일괄 삭제하지 않는다. 로그인 Token과 계정 정보는 유지한다.
10. blocking `needs_review`에서는 기록을 다시 표시하지 않고 새 학습 일시 제한과 고객지원 안내를 보여준다.

고정 사용자 문구와 오류 처리는 다음을 기본으로 한다.

| 상황 | 앱 문구·동작 |
| --- | --- |
| `processing`, `canStartLearning=false` | “학습 기록 삭제를 준비하고 있어요. 잠시 후 새 학습을 시작할 수 있어요.” 이전 기록과 학습 진입을 숨긴다. |
| `processing`, `canStartLearning=true` | “기존 학습 기록을 정리 중이에요. 새 학습은 시작할 수 있어요.” |
| `cleanup_delayed` | “일부 이전 기록 정리가 지연되고 있지만 새 학습은 정상적으로 이용할 수 있어요.” |
| `completed` | “학습 기록 삭제가 완료됐어요.” 이전 삭제 세대의 cache만 정리하고 새 학습 cache와 로그인 상태는 유지한다. |
| `needs_review` | “안전한 처리를 위해 확인이 필요해 새 학습을 잠시 시작할 수 없어요. 기존 기록은 계속 숨김 처리돼요.” 고객지원 진입점을 제공한다. |
| 네트워크 timeout/5xx | 성공·실패를 단정하지 않고 “삭제 상태를 확인하고 있어요.”를 표시한다. 저장한 같은 key로 상태 조회 후 command를 재전송한다. |
| `401` | 삭제 operation 실패로 표시하지 않고 로그인 만료 안내 후 기존 재로그인 흐름으로 보낸다. |
| `409 LEARNING_RECORD_DELETION_IN_PROGRESS` | 일반 오류 팝업 대신 최신 deletion status를 다시 조회하고 `canStartLearning`에 맞춰 화면을 갱신한다. |
| `409 LEARNING_RECORD_DELETION_ALREADY_ACTIVE` | “이전 기록을 아직 정리 중이에요. 삭제가 완료되면 다시 요청해 주세요.” 추가 삭제가 접수됐다고 표시하지 않으며 새 학습 가능 여부는 status의 `canStartLearning`을 따른다. |

접수 응답 유실 시에도 이전 cache를 다시 보여주지 않고 확인 대기 화면을 유지한다. 앱 재시작·다른 기기에서의 삭제를 고려해 온라인 복귀 시 deletion status를 먼저 동기화한 뒤 이력을 표시한다. 서버에 연결되지 않은 다른 기기의 로컬 cache를 즉시 원격 제거한다고 보장하지 않는다. 앱은 영속 삭제 상태/세대와 늦은 응답 차단을 구현해야 하며, 기존 API DTO에 generation 필드를 추가하지 않는다.

`confirmed=true` 같은 Body는 추가하지 않는다. 두 번의 확인은 오조작 방지 UX이지 서버가 신뢰할 재인증 증거가 아니다.

## 5. 저장 모델

### 5.1 `learning_record_deletion_operations`

사용자별 한 개의 active deletion을 나타내는 durable aggregate다.

| 필드 | 의미 |
| --- | --- |
| `_id/deletionId` | server UUID v4 |
| `userId` | JWT `sub`의 canonical UUID, 외부 비노출 |
| `scopeVersion` | v1은 `ALL_EXAMS_AND_CHALLENGES` |
| `cutoffAt` | 이 시점 이하 데이터가 삭제 범위 |
| `status` | 내부 상태 enum |
| `activeGuard` | `COMPLETED` 전 true, partial unique 대상 |
| `writeBlocked` | 초기 fence·blocking review는 true, safe checkpoint 뒤 false |
| `safeToStartLearningAt` | sealed target·Callback/Billing fence 확정 시각 |
| `requestedAt`, `targetCompletionAt` | 요청 시각과 24시간 목표 |
| `presignedCapabilityExpiresAt` | 기존 PUT URL 최대 TTL+clock skew 이후 final sweep 기준 |
| `nextAttemptAt`, `attemptCount` | durable retry |
| `leaseOwner`, `leaseToken`, `leaseUntil` | 다중 instance claim |
| `lastFailureCategory` | 고정 low-cardinality enum만 저장 |
| `counts` | inventory/deleted/remaining의 숫자만 저장 |
| `completedAt`, `cleanupDelayedAt`, `needsReviewAt` | 완료·지연·운영 격리 전이 시각 |
| `expiresAt` | 승인된 완료 evidence 보존기간 뒤 TTL, active 상태에는 없음 |
| `createdAt`, `updatedAt`, `version` | 감사·CAS |

내부 상태:

```text
REQUESTED
  → FENCED
  → INVENTORYING
  → WAITING_COORDINATION
  → PREPARING_RESTART
  → SAFE_TO_START_LEARNING
  → DELETING_S3
  → DELETING_MONGO
  → CLEARING_CACHE
  → VERIFYING
  → COMPLETED

재시도 소진 또는 24시간 초과 후 운영 개입 필요
  → CLEANUP_DELAYED     (safe checkpoint 확정, writeBlocked=false)
  → NEEDS_REVIEW        (안전성 미확정, writeBlocked=true)
```

- `FAILED`를 즉시 terminal로 쓰지 않는다. 재시도 가능한 실패는 현재 stage와 `nextAttemptAt`을 유지한다.
- `CLEANUP_DELAYED`와 `NEEDS_REVIEW` 모두 `activeGuard=true`라서 두 번째 deletion operation은 만들지 않는다.
- `PREPARING_RESTART`는 coordination 종료, target별 최종 Callback 차단, Billing source 이전과 Challenge 당일 slot 해제를 bounded batch로 마무리한다. S3 삭제 전에 일부 원본 DB 문서를 제거할 수 있으며 재시작에 필요한 참조는 이미 sealed inventory에 있어야 한다.
- `SAFE_TO_START_LEARNING` Transaction은 sealed target count/digest, 모든 target의 `callbackFence=SEALED`, Billing 승계 evidence와 Challenge slot 해제 완료 증거를 검증한 뒤 `writeBlocked=false`와 시각을 함께 기록한다. 일괄 삭제를 이 한 Transaction에 넣지 않는다.
- `COMPLETED` Transaction에서 `activeGuard=false`로 전환하지만 새 학습 허용은 그보다 앞선 안전 checkpoint에서 가능하다.
- 재처리는 status를 뒤로 이동시키지 않고 각 stage의 durable evidence를 확인해 멱등 실행한다.

### 5.2 `learning_record_deletion_commands`

- `_id = SHA-256(canonical userId + ':' + canonical Idempotency-Key)`
- `deletionId`, semantic command digest, `createdAt`, `expiresAt`
- 같은 key/같은 command는 replay, digest가 다르면 conflict
- active operation 중 다른 key는 409로 거절하고 mapping을 만들지 않음. 같은 key replay만 기존 deletionId를 반환
- 완료 뒤 30일 동안 유지하여 늦은 재전송이 새 삭제가 되지 않게 함
- 원문 idempotency key는 저장하거나 로그하지 않음

### 5.3 `learning_record_deletion_targets`

한 operation 문서에 무제한 examId/attemptId 배열을 넣지 않는다. aggregate별 작은 target 문서를 만든다.

- 결정적 `_id = deletionId + ':' + targetType + ':' + aggregateId`
- `deletionId`, `userId`, `targetType=EXAM|CHALLENGE`, `aggregateId`
- `s3Prefix` 또는 정확한 key, Redis key, coordination 분류
- stage별 완료 bit와 고정 failure category
- `callbackFence=DRAINING|SEALED`, `callbackSealedAt`, version/CAS와 참조 inventory 완료 증거. inventory seal은 대상 ID 고정이며 Callback seal과 다른 개념
- 원문 답안·피드백·transcript·오디오·provider payload는 저장 금지
- 늦은 Callback no-op 판단에 필요한 기간 동안 보존 후 TTL
- `NEEDS_REVIEW` target에는 TTL을 두지 않음

target은 S3/Callback 재시도용 최소 tombstone이다. 존재한다는 사실만으로 Exam GRADING Callback을 버리지 않는다. `DRAINING`에서는 승인된 coordination 경로만 허용하고 `SEALED`에서 재생성을 막는다. 완료 후 30일 동안 남기고 이후 제거한다.

### 5.4 Billing coordination tombstone

Billing-linked `ExamSession`의 사용자 콘텐츠를 제거해도 다음 최소 정보는 별도 tombstone으로 보존할 수 있다.

- userId, source examId, mockExamId
- reservation kind/status 식별에 필요한 참조
- attemptGroupId와 local projection/terminal event 참조
- creation operation 참조와 created/terminal 시각
- deletionId 및 retention metadata

문제·답안·점수·피드백·음성 key와 AI 원문은 포함하지 않는다. 이 tombstone은 삭제로 credit나 retake를 만들기 위한 것이 아니라 기존 소비를 중복 청구하거나 잃지 않게 하는 증거다. 실제 구현 전 Billing replacement reserve가 tombstone의 source examId/attemptGroupId를 받아 기존 OPEN group을 재사용할 수 있는지 contract fixture로 확인한다.

원본 `exam_sessions` 문서를 `active=false`, 별도 status 또는 hidden flag만 붙여 계속 보존하는 방식은 금지한다. 삭제 operation이 진행 중인 동안 coordination drain을 위해 Session을 일시 보유할 수는 있지만 사용자 read fence로 숨기고, 다음 조건을 만족한 뒤 같은 Mongo Transaction에서 tombstone insert/CAS와 원본 Session delete를 확정한다.

1. non-terminal ExamCreationOperation의 commit outcome이 확정됐다.
2. OPEN group은 tombstone이 같은 attemptGroup/consumption replacement source로 사용 가능함을 검증했다.
3. GRADING group은 기존 정책으로 terminal이 확정되고 필요한 outbox evidence가 보존됐다. 격리했다는 사실만으로 terminal로 간주하거나 원본 evidence를 삭제하지 않는다. 안전성 미확정 시 `NEEDS_REVIEW`로 유지한다.
4. tombstone은 allowlist된 최소 필드만 포함하고 deletionId·retention을 가진다.

`COMPLETED` 시점에는 deletion 대상 원본 ExamSession이 0건이어야 한다. tombstone을 ExamSession으로 다시 복원하거나 일반 시험 history/result query의 source로 사용하지 않는다. tombstone도 식별 가능한 최소 거래·조정 증거이므로 일반 분석·개인화에 사용하지 않고 OPEN/non-terminal 동안만 무TTL, terminal 이후 30일 뒤 제거한다.

여기서 ExamSession은 Learning Core의 시험 aggregate다. Identity의 로그인 Session, Access/Refresh Token과 계정 상태는 이 기능으로 삭제하거나 폐기하지 않는다.

#### 일회 승계와 시험 생성 순서

- tombstone에는 내부 `transferState=AVAILABLE|CLAIMED|TRANSFERRED|RETIRED`, `claimedByCreationOperationId`, `replacementExamId`, version과 상태 전이 시각을 추가한다. 원본 Session 삭제와 tombstone 생성은 같은 Transaction이며 source 중복 등록을 unique로 막는다. OPEN 또는 기존 정책상 replacement 가능한 RETAKE_AVAILABLE만 `AVAILABLE`로 시작하고 COMPLETED는 승계 대상이 아니다.
- 시험 생성은 먼저 같은 공개 key의 기존 operation을 replay/reconcile한다. 다른 non-terminal 생성 operation/claim이 있으면 기존 경합 정책으로 재시도시키며 INITIAL로 우회하지 않는다. 그다음 현재 유효한 새 Session을 기존 규칙대로 우선 사용하고, Session이 없을 때만 유효한 미승계 tombstone을 선택한다. current Session과 tombstone의 group 관계가 모순되거나 source가 여러 개라면 추측하지 않고 안전 오류로 중지한다.
- 기존 owner guard와 동일 Transaction에서 tombstone을 `AVAILABLE → CLAIMED` CAS하고, 새 creation operation에 source examId·expectedAttemptGroupId·mockExamId snapshot과 참조를 고정한다. reserve HTTP는 Transaction 밖에서 기존 REPLACEMENT 계약으로 호출하며, 참조 누락이나 불일치를 INITIAL로 바꿔 추가 차감하지 않는다.
- 새 Session commit과 operation 갱신, tombstone의 `CLAIMED → TRANSFERRED` 및 replacementExamId 기록은 같은 Transaction이다. 이후 기존 confirm/status 복구를 따른다. 전달한 source를 다시 선택하지 않으며 후속 시험 생성은 새 Session/operation의 정상 정책을 따른다.
- timeout·lease 만료·unknown commit만으로 claim을 해제하지 않는다. fresh majority의 operation/Session/tombstone evidence와 Billing status로 같은 operation을 복구한다. 새 Session 미commit과 reservation CANCELED/EXPIRED가 확정된 경우에만 같은 owner Transaction에서 claim을 재사용 가능하게 정리한다. CONFIRMED 또는 commit 미확정은 새 reserve·claim 재할당을 금지한다.
- 승계가 끝났거나 group이 terminal인 tombstone의 lifecycle과 삭제 TTL은 구분한다. TRANSFERRED여도 group non-terminal이면 거래 연결 증거를 보존하고, terminal 확정 후 30일 retention을 적용한다. COMPLETED source는 RETIRED로 두며 무료 기회를 만들지 않는다.
- 실제 Billing fixture에서 OPEN source replacement, 현재 Session 우선순위, 중복 claim과 후속 두 번째 시험 생성까지 검증하기 전 이 경로를 활성화하지 않는다. wire 계약 변경이 필요하면 별도 승인을 받고 Billing 저장소를 이 작업에서 수정하지 않는다.

## 6. 동시성 fence

### 6.1 기존 ownership guard와 분리

`user_ownership_guards`는 `ACTIVE|MERGED` 의미를 유지한다. 삭제 상태를 새 enum으로 추가하지 않는다. 삭제 여부는 별도 operation에서 관리한다.

삭제 request Transaction은 다음을 함께 수행한다.

1. 현재 user의 ownership guard가 ACTIVE인지 검증하고 revision을 touch한다.
2. active deletion을 조회한다.
3. command mapping과 operation을 insert 또는 replay한다.

기존 모든 user-owned writer도 같은 Transaction에서 ownership guard를 touch한 뒤 active deletion을 확인한다. 따라서 삭제 시작과 in-flight writer가 같은 guard 문서에서 write conflict를 일으켜 한쪽만 먼저 commit한다. 단순히 “operation이 없음을 읽는 것”만으로 insert race를 막았다고 가정하지 않는다.

### 6.2 `UserOwnedTransactionExecutor` 통합

현재 executor는 UserMerged writer flag가 꺼지면 Transaction 없이 command를 실행한다. 삭제 fence를 켠 환경에서는 이를 그대로 사용할 수 없다.

- UserMerged writer 또는 deletion writer fence 중 하나라도 켜지면 Transaction을 연다.
- 둘 중 하나라도 켜지면 모든 참여 writer와 deletion command가 동일 ACTIVE ownership guard를 같은 Transaction에서 touch한다. deletion ON/UserMerged OFF도 생략하지 않는다.
- deletion fence가 켜지면 active deletion을 확인한다.
- 기존 Transaction 안에서는 별도 `TransactionTemplate`을 중첩하지 않고 같은 body에 참여한다.
- Billing, AttemptGroup, Challenge의 기존 transaction manager 경계를 유지한다.
- deletion worker와 승인된 drain 경로만 `MutationIntent.DELETION_CLEANUP|COORDINATION_DRAIN`을 사용한다.
- 일반 HTTP, Callback, reconciler가 임의로 bypass intent를 사용할 수 없게 package/API 경계를 제한한다.

feature 활성화 전 ACTIVE ownership guard backfill과 구버전 instance drain이 필수다.

### 6.3 read fence

read는 revision을 증가시키지 않지만 요청 시작 시 active deletion과 cutoff를 확인한다.

- history/range query에는 `createdAt > cutoffAt` 조건을 적용해 cutoff 이전의 old record를 항상 숨긴다.
- direct id query는 문서 owner와 createdAt을 확인하고 cutoff 이전이거나 sealed target이면 not found로 취급한다.
- 안전 checkpoint 전에는 신규 write 자체가 차단된다. checkpoint 뒤 `processing` 또는 `CLEANUP_DELAYED`에서는 cutoff 이후 신규 record를 정상 조회한다.
- `NEEDS_REVIEW`에서는 old record hide와 write block을 유지한다. 안전성이 복구돼 checkpoint를 확정한 뒤에만 신규 학습을 허용한다.
- `COMPLETED` 뒤에도 cutoff 이후 신규 기록은 정상 조회한다.
- 캐시가 DB보다 오래된 기록을 다시 노출하지 않도록 Redis와 애플리케이션 cache key를 invalidate한다.

## 7. 삭제 inventory와 보존 행렬

### 7.1 모의고사

| 대상 | 처리 |
| --- | --- |
| `exam_sessions` | 진행 중 coordination 동안 read fence로 숨긴 뒤 완료 전 원본 문서 삭제. hidden Session 영구 보존 금지 |
| `exam_results` | examId별 전체 삭제 |
| `exam_summaries` | examId별 전체 삭제 |
| `question_grading_jobs` | coordination 종료 뒤 삭제 |
| `summary_grading_jobs` | coordination 종료 뒤 삭제 |
| `azure_results` | examId별 삭제 |
| `speechace_results` | examId별 삭제 |
| Redis `exam:status:{examId}` | 삭제 후 부재 검증 |
| S3 `temp/{examId}/` | prefix 전체 삭제 및 final sweep |
| `exam_creation_operations` | 삭제 금지. 기존 retention/purge 정책 유지 |
| `attempt_group_event_outbox` | 삭제·rewrite 금지. canonical payload/digest/eventId 유지 |
| `attempt_group_publisher_state` | global 운영 상태, 삭제 금지 |
| `mock_exams`, `questions` | catalog, 삭제 금지 |

### 7.2 Challenge

| 대상 | 처리 |
| --- | --- |
| `challenge_10s_attempts` | 사용자 attempt/snapshot/result 삭제 |
| `challenge_10s_grading_jobs` | attemptId/generation별 삭제 |
| `challenge_10s_submit_receipts` | userId와 target attempt 기준 삭제 |
| `challenge_10s_callback_receipts` | target job/attempt 기준 삭제 |
| S3 `temp/challenges/{attemptId}/` | prefix 삭제 및 final sweep |
| question/catalog state | 삭제 금지 |

Callback receipt에는 userId가 없으므로 attempt를 먼저 지워 참조를 잃지 않는다. inventory target을 만든 뒤 target attempt/job로 receipt를 삭제한다.

### 7.3 공통 보존

- Billing reservation/consumption, entitlement와 TrialClaim
- non-terminal 및 terminal ExamCreationOperation의 durable evidence
- 미전달 AttemptGroup outbox와 전달/DEAD_LETTER retention evidence
- UserMerged/UserWithdrawn inbox, withdrawn deny와 ownership guard
- deletion operation/command/target의 최소 멱등성·Callback tombstone
- 일반 애플리케이션 로그와 암호화 backup은 30일 뒤 제거
- 보안 감사 데이터는 90일 뒤 제거
- 개인에게 재연결할 수 없는 `learning_activity_daily_aggregates`의 일별 저카디널리티 응시·품질 집계

완료 operation/command와 Callback target tombstone은 30일 보존한다. Billing coordination tombstone과 그 밖의 미해결 coordination evidence는 OPEN/non-terminal 동안 TTL을 두지 않고 terminal 확정 뒤 30일 보존한다. `NEEDS_REVIEW`에도 TTL을 두지 않는다. Billing ledger의 법정·거래 보존기간은 이 계획으로 단축하지 않으며 Learning Core의 일반 분석 데이터로 재사용하지 않는다. 위 기간과 live delete·backup 만료 차이는 production 활성화 전에 개인정보 안내문에 반영한다.

### 7.4 비식별 일별 집계 지표

사용자 삭제 후에도 유지할 수 있는 것은 사용자별 record가 아니라 다시 개인에게 연결할 수 없는 서비스 집계다.

별도 MongoDB collection 이름은 `learning_activity_daily_aggregates`로 확정한다. v1은 `Asia/Seoul` 기준 일별 문서만 저장하고 주·월 통계는 조회 시 일별 count를 합산한다. 동일 수치를 일·주·월 문서로 중복 저장하지 않는다.

문서 schema:

| 필드 | 의미 |
| --- | --- |
| `_id` | `bucketDate:metric:examType:outcome`의 결정적 key |
| `bucketDate` | `YYYY-MM-DD` 형식의 KST 일자 |
| `metric` | 승인된 고정 metric enum |
| `examType` | `MOCK_EXAM|CHALLENGE_10S|ALL` 고정 enum |
| `outcome` | 승인된 고정 outcome, 해당 없으면 `ALL` |
| `count` | 0 이상 64-bit 누적값 |
| `createdAt`, `updatedAt`, `version` | 생성·수정 시각과 optimistic concurrency |

unique key는 `(bucketDate, metric, examType, outcome)`이며 timezone과 dimension 누락을 null로 표현하지 않는다. final aggregate에는 userId, examId, attemptId와 개별 event ID를 저장하지 않는다.
이 collection은 사용자별 삭제 대상과 TTL 대상에 포함하지 않는다. 별도 통계 retention 정책을 승인하기 전 v1에는 TTL을 설정하지 않는다.

v1 metric allowlist:

| 영역 | metric | 허용 outcome | 증가 시점 | 일별 최대 문서 |
| --- | --- | --- | --- | ---: |
| 시험 | `exam_started_total` | `INITIAL|REPLACEMENT` | 신규 ExamSession commit | 2 |
| 시험 | `exam_completed_total` | `ALL` | Summary를 포함한 시험 `COMPLETED` terminal commit | 1 |
| 시험 | `exam_retake_available_total` | `ALL` | 시험 `RETAKE_AVAILABLE` terminal commit | 1 |
| 문항 | `question_submitted_total` | `INITIAL|USER_RETRY` | 최초 submit 또는 `retryCount>0` 사용자 재답변 commit | 2 |
| 문항 | `question_grading_completed_total` | `ALL` | Question Job `COMPLETED` 최초 전이 | 1 |
| 문항 | `question_grading_failed_total` | `ALL` | Question Job 자동 복구 불가 최종 실패 전이 | 1 |
| 문항 | `question_grading_retry_total` | `ALL` | 초기 dispatch가 아닌 durable 시스템 retry 확정 | 1 |
| Summary | `summary_grading_completed_total` | `ALL` | Summary Job `COMPLETED` 최초 전이 | 1 |
| Summary | `summary_grading_failed_total` | `ALL` | Summary Job 자동 복구 불가 최종 실패 전이 | 1 |
| Challenge | `challenge_started_total` | `ALL` | Challenge Attempt 생성 commit | 1 |
| Challenge | `challenge_submitted_total` | `ALL` | Challenge `SUBMITTED` 최초 전이 | 1 |
| Challenge | `challenge_completed_total` | `SCORED|NO_SPEECH` | Challenge Job `COMPLETED` 최초 전이 | 2 |
| Challenge | `challenge_expired_total` | `ALL` | Challenge `EXPIRED` 최초 전이 | 1 |
| Challenge | `challenge_grading_failed_total` | `ALL` | Challenge Job 최종 `FAILED` 전이 | 1 |
| Challenge | `challenge_grading_retry_total` | `ALL` | Challenge Job durable retry/generation retry 확정 | 1 |
| 합계 |  |  | 발생한 조합만 sparse upsert | **18** |

실패·시스템 재시도의 provider/failureCode별 상세 원인은 aggregate outcome으로 만들지 않는다. 원인별 관측은 기존 low-cardinality Micrometer metric을 사용하고 원문 오류는 어느 쪽에도 저장하지 않는다. 삭제 자체와 HTTP/Callback replay, duplicate, stale, lease lost와 temporary failure는 위 durable 제품 count를 증가시키지 않는다.

저장 예시는 `bucketDate=2026-09-21`, `metric=exam_completed_total`, `examType=MOCK_EXAM`, `outcome=ALL`, `count=120`이다. userId, examId, attemptId, 정확한 event 시각, 원점수·자유문장·오디오 특성 및 희소 dimension 조합을 저장하지 않는다. 작은 cohort는 대시보드에서 여러 날짜 또는 유형을 합치거나 숨기며 최소 기준은 production 활성화 전 개인정보·분석 담당자가 확정한다.

집계 증가는 실제 durable 상태 전이에서 정확히 한 번 수행해야 한다. domain 상태의 insert 또는 CAS가 실제로 성공한 같은 Mongo Transaction 안에서 해당 일별 문서를 `$inc`하고, replay나 CAS loser는 증가시키지 않는다. Transaction 경계상 직접 증가가 불가능해 임시 contribution/outbox를 사용한다면 결정적 event key로 중복을 막고 그 staging record는 식별 가능한 개인정보로 분류하여 짧은 TTL·삭제 fence·deletion worker 정리 대상에 포함한다. 최종 일별 aggregate만 비식별 보존 대상이다.

상태 CAS만으로 최초 집계를 보장하지 않는다. QuestionGradingJob의 `reopenMissingResult`처럼 COMPLETED를 PENDING으로 되돌리는 복구가 있으므로 다음 규칙을 적용한다.

- Job/Session/Attempt에 지표별 최초 집계 marker와 집계 bucket을 내부 metadata로 보존하고 상태 전이·marker CAS·aggregate `$inc`를 같은 Transaction으로 commit한다. reopen, dispatchAttempt 초기화, recoveryCycle/generation 증가로 최초 marker를 초기화하지 않는다. COMPLETED 재전이도 같은 논리 Job의 완료는 한 번만 센다.
- 완료와 최종 실패는 서로 다른 최초 event다. 같은 논리 Job이 최종 실패 후 승인된 사용자 복구로 완료되면 각각 최초 발생일에 한 번 집계할 수 있으므로 두 지표를 상호 배타적인 모집단으로 해석하지 않는다. 일시적 dispatch 실패는 최종 실패 count가 아니다.
- 시스템 retry는 실제 네트워크 호출 횟수가 아니라 최초 dispatch를 제외한 새로운 durable 재시도 결정 횟수다. 동일 retry의 HTTP 재전송·lease 회수는 증가시키지 않는다. Job 내부의 초기화되지 않는 retry sequence와 마지막 집계 sequence를 사용하고, generation/recoveryCycle이 바뀌어도 같은 결정의 replay는 중복 집계하지 않는다. 사용자 새 녹음은 별도 제출 지표이며 그 Job의 첫 dispatch를 시스템 retry로 세지 않는다.
- marker는 식별 가능한 원본 domain metadata이며 해당 원본과 함께 삭제한다. aggregate에 식별자를 옮기지 않는다. 삭제 후 late Callback은 SEALED fence로 차단하여 marker 없는 원본을 재생성·재집계하지 못하게 한다. 삭제 차단·cleanup 자체로 완료/실패/만료 event를 만들지 않는다.

v1은 실제 event 발생일별 건수와 주·월 합계를 제공한다. 같은 기간의 시험 `COMPLETED / (COMPLETED + RETAKE_AVAILABLE)`는 **그 기간 terminal event의 결과 구성비**로만 표시하며 전체 시작 대비 완료율이 아니다. 분모가 0이면 계산 불가로 표시한다. 완료/시작, 제출/시작 같은 서로 다른 날짜·집단이 섞이는 전환율과 Job 완료/실패의 단순 성공률은 제공하지 않는다. 동일 시작일 cohort 완료율은 별도 설계 대상으로 제외한다. 비율과 주·월 값을 별도 문서로 저장하지 않는다.

18개 조합 기준 연간 최대 문서 수는 6,570개이며 0건 조합은 만들지 않는다. 정상 11문항 시험 한 건은 시험 시작 1, 문항 제출 11, 문항 완료 11, Summary 완료 1, 시험 완료 1로 약 25회의 aggregate update를 추가한다. v1은 direct transactional `$inc`를 사용하고, 부하 테스트에서 동일 일자 hot document 경합이 확인될 때만 시간 bucket/shard 또는 durable batching을 별도 설계한다.

특정 사용자의 누적 응시 횟수·평균 점수·최근 응시일과 user-scoped analytics counter는 삭제한다. 삭제된 사용자의 과거 기여분은 개인과 연결할 수 없는 최종 aggregate에서 차감하지 않는다. Billing consumption·TrialClaim은 거래 정합성 목적으로만 보존하고 이 aggregate의 원천 또는 개인화 counter로 재사용하지 않는다.

`unique_users_total`, lifetime unique user와 같은 지표는 장기 식별 없이는 exact 계산·중복 제거가 어렵다. 이 계획에는 추가하지 않으며 필요하면 회전 식별자, cohort threshold, retention과 삭제 가능성을 포함한 별도 privacy 설계를 승인받는다.

정확한 live 집계는 수집 활성화 시각부터 보장한다. 운영 manifest에 지표별 수집 시작 시각·최초 부분 일자·과거 검증 coverage를 기록하되 사용자 식별자는 포함하지 않고 18개 aggregate dimension도 늘리지 않는다. coverage 밖의 문서 부재는 미수집/복원 불가이며 0이 아니다. 수집이 완전한 날짜·지표에 한해서만 sparse 문서 부재를 0으로 해석한다.

과거 backfill은 선택 사항이며 원본에 실제 발생일·중복 제거 증거가 남아 있는 지표만 shadow 계산·검증 후 반영한다. 현재 상태나 덮어쓴 completedAt에서 과거 완료·일별 retry 횟수를 추정하지 않는다. 기존 Job에는 일별 retry 이력이 없을 수 있으므로 전체 18개 지표의 historical exactness를 보장하지 않는다. live 시작 경계와 겹치지 않는 bucket만 deterministic exact-value upsert하고, 과거 값을 live `$inc` 문서에 덮어쓰지 않는다. 같은 날 cutover가 필요하면 부분 일자로 표시하고 자동 backfill하지 않는다.

기존 domain의 집계 marker 초기화와 historical inclusion manifest도 전환 계획에 포함한다. 과거에 이미 집계한 event가 reopen되어 live에서 다시 세어지지 않게 하며, 기존 COMPLETED의 최초 event를 증명할 수 없으면 다시 완료됐다는 이유로 새 완료로 집계하지 않는다. 증거 없는 legacy Job의 최초 지표는 coverage 제한으로 명시한다. 삭제 worker는 `learning_activity_daily_aggregates`를 삭제 predicate에 포함하지 않는다.

## 8. worker 처리 순서

### 8.1 claim과 retry

- 기본 poll 10초, batch 20, instance 동시 실행 2, lease 30초를 초기값으로 둔다.
- 외부 S3 호출은 Mongo Transaction 밖에서 수행한다.
- Mongo stage는 최대 100개 문서의 bounded batch로 처리하고 각 batch 결과를 operation/target에 기록한다.
- backoff는 5초 → 15초 → 1분 → 5분 → 15분 상한과 양의 jitter를 사용한다.
- 같은 stage 재실행은 존재하지 않는 객체/문서를 성공으로 취급한다.
- 24시간을 넘기거나 승인된 attempt 한도를 소진했을 때 안전 checkpoint가 이미 확정됐고 남은 작업이 old target 물리 정리뿐이면 `CLEANUP_DELAYED`로 전환해 `writeBlocked=false`를 유지한다.
- 같은 조건에서도 target·ownership·Billing·commit outcome이 불명확해 신규 데이터 손상이나 중복 차감 가능성이 있으면 `NEEDS_REVIEW`로 격리하고 `writeBlocked=true`를 유지한다.

### 8.2 FENCED와 inventory

1. operation과 write/read fence가 majority-visible한지 확인한다.
2. `cutoffAt` 이하 `exam_sessions`와 Challenge attempt를 결정적 target으로 snapshot한다.
3. orphan Result/Summary/Job처럼 Session 없이 남은 userId 또는 참조 문서를 별도 count한다.
4. target count와 저장 count가 일치해야 다음 stage로 간다.
5. fence 이후 신규 일반 write가 0인지 검증한다.
6. target ID·cutoff·count·digest를 seal하고 이후 inventory 추가·교체를 금지한다.

inventory seal은 삭제 대상 ID를 고정하는 단계이지 GRADING 결과를 버리는 시점이 아니다. non-terminal creation operation의 Session commit 여부와 source inventory를 먼저 확정하고 seal한다. seal 뒤 뒤늦게 기존 Session commit 증거가 발견되면 target을 임의 확장하지 않고 `NEEDS_REVIEW`로 복구한다.

안전 checkpoint는 sealed inventory, old record read hide, target별 최종 `callbackFence=SEALED`, Billing OPEN 승계 tombstone 및 Challenge 당일 slot 해제 증거를 모두 검증한 뒤 같은 Transaction에서 `safeToStartLearningAt`과 `writeBlocked=false`를 기록하는 시점이다. 이후 worker는 sealed된 examId/attemptId와 그 자식만 삭제한다. cutoff 이후 생성된 신규 record를 다시 inventory에 넣거나 `deleteMany(userId)` 같은 사용자 전체 조건으로 삭제하는 것은 금지한다.

### 8.3 coordination drain

#### ExamCreationOperation

- source user의 non-terminal creation operation이 있으면 Session/content를 먼저 삭제하지 않는다.
- reserve/status/confirm/cancel은 기존 status-first reconciliation을 사용한다.
- 삭제 fence 뒤 Session commit을 새로 허용하지 않는다. 아직 remote reserve만 있는 operation은 기존 같은 key로 cancel/terminal 수렴한다.
- 이미 Session commit이 확정된 operation은 그 Session을 inventory에 편입하고 confirm/status 증거를 끝낸다.
- unknown commit은 fresh majority evidence로 판정하고 추측으로 cancel하지 않는다.

#### AttemptGroup

- 삭제 요청 자체를 grading failure로 사용하거나 `RETAKE_AVAILABLE` event를 만들지 않는다.
- OPEN group은 기존 소비를 유지한다. visible Session은 제거하되 새 학습 시작 시 같은 group을 replacement로 재사용할 최소 tombstone을 남긴다.
- GRADING group은 기존 Question/Summary recovery와 최대 `PT30M` deadline 판정을 계속 수행한다. target은 `DRAINING`으로 두며 사용자 조회·submit은 차단하지만 해당 examId의 승인된 Callback·coordination writer는 계속 처리한다. 최초 deadline을 삭제 요청으로 초기화하지 않는다.
- 기존 정책으로 실제 완료하면 `COMPLETED`, 실제 복구 실패면 기존 failureCode의 `RETAKE_AVAILABLE`로 수렴한다. 삭제 때문에 failureCode를 위조하지 않는다.
- terminal·outbox의 로컬 commit 증거를 확정한 뒤 같은 owner guard/target CAS 경계에서 Callback fence를 `SEALED`로 전환한다. Callback과 seal 중 먼저 commit한 쪽의 증거를 재조회하여 수렴하며, seal 뒤 결과를 다시 저장하지 않는다. 통신 장애로 terminal 전달만 남으면 canonical outbox 증거를 보존하며 publisher가 계속 처리한다.
- 기존 outbox가 PENDING/IN_FLIGHT/BLOCKED_AUTH면 payload를 수정하지 않고 publisher의 정상 전달·격리 정책을 따른다.
- OPEN group·Session↔tombstone·commit outcome이 불명확해 신규 시험의 추가 차감 가능성이 있으면 critical evidence를 지우지 않고 deletion을 `NEEDS_REVIEW`로 둔다.
- 위 coordination과 safe checkpoint가 확정된 뒤 old target 물리 정리만 지연되면 `CLEANUP_DELAYED`로 두고 신규 학습은 허용한다. outbox는 삭제 대상이 아니며 미전달 증거와 정상 publisher 정책을 유지한다.

#### Challenge

- Challenge는 Billing coordination이 없으므로 fence 이후 새 generation을 만들지 않는다.
- 미전송 Job은 취소용 신규 외부 상태를 만들지 않고 worker claim에서 deletion target을 확인해 no-op한다.
- Challenge target은 참조 inventory 확정 뒤 Callback fence를 `SEALED`로 전환한다. 이미 전송된 Job의 Callback은 이 상태를 확인하여 성공 no-op으로 종료한다. 실제 채점 실패/만료로 위조하거나 통계를 증가시키지 않는다.
- attempt/job/receipt 삭제와 Callback이 경쟁하면 같은 user fence/target evidence로 재생성을 막는다.
- `(userId, challengeDate, questionNumber)` unique index는 유지한다. checkpoint 전에 모든 삭제 대상 Attempt를 제거하여 당일 slot을 비운다. Attempt를 지우기 전에 자식 Job/receipt ID와 S3 prefix를 sealed inventory로 확보하고, Job dispatch/expiry·Callback의 재생성을 차단한다. 자식 cleanup은 target ID로 계속 가능해야 한다.
- bounded Transaction에서 target의 slot-release progress와 정확한 old attemptId 삭제를 함께 commit한다. 모든 대상의 해제가 증명되기 전 `canStartLearning=true`를 반환하지 않는다. S3 정리를 기다리느라 slot 해제를 늦추지 않는다.
- checkpoint 후 같은 날 새 attemptId로 재참여할 수 있다. 삭제 반복에 따른 반복 재참여도 허용하며 별도 당일 이용 제한 record는 남기지 않는다. 이후 cleanup은 새 attempt가 아닌 sealed old attemptId만 사용한다.

### 8.4 S3 삭제

- 시험: `temp/{examId}/` prefix
- Challenge: `temp/challenges/{attemptId}/` prefix
- pagination된 ListObjectsV2와 최대 1,000개 DeleteObjects batch를 사용하고 partial error를 개별 target에 기록한다.
- scoped `s3:ListBucket` prefix 조건과 `s3:DeleteObject` 권한이 배포 선행 조건이다.
- bucket versioning이 활성화됐으면 같은 `temp/*` 범위의 `s3:ListBucketVersions`와 `s3:DeleteObjectVersion`을 추가하거나, 삭제 marker와 과거 version을 승인된 기간 안에 제거하는 lifecycle를 검증한다.
- bucket 전체 resource에 대한 `s3:*` 권한은 금지한다.
- API timeout을 bounded하게 설정하고 SDK 무한 retry에 의존하지 않는다.
- 요청 전 발급된 PUT URL은 즉시 취소할 수 없으므로 `presignedCapabilityExpiresAt = requestedAt + 최대 PUT TTL 5분 + clock skew` 뒤 final prefix sweep을 반드시 수행한다.
- final sweep 전에 객체 0건이라고 `COMPLETED`로 전환하지 않는다.
- 기존 Object Key 형식이나 bucket은 바꾸지 않는다.

### 8.5 Mongo 삭제

- 일반 target은 자식 Result/Job/receipt를 먼저 삭제한다. 단, Billing source Session은 §5.4의 원자적 tombstone 이전 뒤, Challenge Attempt는 §8.3의 참조 inventory·fence 확정 뒤 checkpoint 전에 먼저 삭제할 수 있다. 모든 경우 자식 참조를 잃지 않으며 남은 cleanup은 sealed ID로만 수행한다.
- delete count가 예상보다 많거나 sealed target 밖의 신규 데이터가 포함될 가능성이 있으면 즉시 `NEEDS_REVIEW`로 전환한다.
- delete count가 예상보다 적으면 재조회하여 이미 삭제된 멱등 성공인지 old orphan/일시 장애인지 판정한다. 신규 데이터 오삭제 가능성 없이 old target 정리만 남았다면 retry 후 `CLEANUP_DELAYED`로 전환할 수 있다.
- 한 사용자 전체를 단일 대형 Transaction으로 삭제하지 않는다.
- 각 bounded batch와 progress marker만 Transaction으로 commit한다.
- 안전 checkpoint 뒤에는 사용자 기준 `deleteMany(userId)`를 사용하지 않고 sealed target ID 기반 predicate만 사용한다.
- deletion operation, command, target와 보존 데이터는 같은 bulk delete predicate에 포함하지 않는다.

### 8.6 Redis 정리와 검증

- inventory의 각 examId에 대해 정확한 `exam:status:{examId}`만 삭제한다.
- pattern 전체 삭제나 `FLUSHDB`는 금지한다.
- Redis 장애는 Mongo/S3 완료를 되돌리지 않고 retry한다.
- 최종 검증은 Mongo remaining count, S3 final listing, Redis key absence와 unresolved coordination count를 확인한다.
- 완료 Transaction에서 operation을 `COMPLETED`, `activeGuard=false`로 바꾸고 counts/completedAt을 고정한다.

## 9. Callback·worker·Presigned URL 경합

### 9.1 Exam Callback

Callback의 `user_id`는 계속 examId다.

1. 기존 인증·payload 검증을 유지하고 Session/current owner, deletion operation 및 target 상태를 같은 Transaction에서 확인한다. 일반 Callback이 임의로 deletion bypass intent를 선택할 수 없고 내부 조정 서비스가 대상과 허용 단계를 검증한다.
2. active deletion의 GRADING Session은 inventory 중이거나 target이 `DRAINING`이면 승인된 coordination 경로로 기존 유효 결과·Job·Summary·terminal/outbox를 저장한다. 사용자 read hide는 유지한다. inventory marker 존재만으로 성공 no-op하지 않는다.
3. target이 `SEALED`이면 결과·Job·Summary를 생성하지 않고 기존 Callback 성공 envelope를 반환한다. Session이 이미 없어도 sealed target을 검증하여 처리한다. Session과 target이 모두 없으면 기존 malformed/not-found 정책을 유지한다.
4. Callback·drain worker·seal·cleanup은 같은 ownership guard와 target version 경계에 참여한다. target 생성 전 경합도 owner guard가 직렬화한다. seal의 unknown commit은 fresh evidence로 판정하며 미확정 상태를 성공 저장 또는 영구 no-op으로 추측하지 않는다.

### 9.2 Challenge Callback

- Attempt가 삭제되어 owner를 찾을 수 없어도 attemptId target의 `callbackFence=SEALED`가 확인되면 `204` no-op한다. slot 해제는 항상 이 차단 증거를 먼저 확정한다.
- 같은 callbackId의 payload conflict를 이미 receipt로 확정한 경우 기존 `409`를 유지한다.
- 삭제가 먼저 확정된 target에는 새 receipt를 만들지 않는다.
- tombstone 보존기간은 AI의 최대 retry window보다 길어야 한다.

### 9.3 background writer inventory

삭제 fence 검사가 필요한 경로:

- 시험 생성과 Billing saga/reconciler
- upload URL, submit, retry와 상태 보정
- Question/Summary dispatch, timeout, Callback과 result 저장
- AttemptGroup coordinator, summary completion, reconciler와 backfill
- Challenge attempt 생성, lazy expiry, submit, dispatch, timeout과 Callback
- UserMerged migration의 source/target preflight

UserMerged가 source 또는 target의 active deletion과 만나는 경우 부분 migration하지 않고 retryable `503`으로 재시도한다. deletion이 완료된 뒤 merge하면 삭제 cutoff 이전 데이터가 다시 target history로 나타나지 않는지 E2E로 검증한다. active withdrawal deny와 만나면 기존 fail-closed 정책을 유지한다.

## 10. 멱등성·unknown commit

- command의 진실 공급원은 `(userId, keyDigest) → deletionId` mapping과 user별 partial unique active operation이다.
- duplicate key/write conflict는 실패한 Transaction 밖에서 majority read로 winner를 재조회한다.
- `UnknownTransactionCommitResult`에서 command를 blind replay하지 않는다. command mapping, active operation과 ownership guard revision을 bounded recheck한다.
- 같은 key mapping이 있으면 그 operation을 반환한다.
- 다른 key의 active operation이 있으면 409로 거절한다. alias mapping·추가 target·후속 삭제 예약을 만들지 않는다. 동시 서로 다른 key는 하나만 생성에 성공하고 나머지는 거절된다. unknown commit의 자기 mapping 존재 여부가 미확정이면 409로 단정하지 않고 bounded 재조회/재시도한다.
- 완료된 old key는 old operation을 계속 반환한다. 사용자가 나중에 다시 삭제할 때 앱은 새 key를 생성한다.
- worker lease 만료만으로 stage 성공이나 terminal을 확정하지 않는다.
- S3 404/NoSuchKey와 Mongo 문서 부재는 해당 target의 멱등 성공일 수 있지만, 전체 검증 없이 operation 완료로 확대하지 않는다.

## 11. 보안과 개인정보

- production/staging에서는 기존 검증된 JWT principal만 허용한다.
- local/test Legacy 고정 UUID는 명시된 profile에서만 fixture 데이터 삭제에 사용한다.
- userId, deletionId, examId, attemptId, idempotency key/digest, S3 key, provider payload를 일반 로그·metric tag·span attribute에 기록하지 않는다.
- 보존 집계 document에도 사용자·시험 식별자, 정확한 event 시각과 희소 dimension을 저장하지 않는다.
- 로그는 stage, fixed outcome/failure category, batch size, duration과 environment만 기록한다.
- Sentry에도 원문 식별자·Token·Authorization·Callback body를 보내지 않는다.
- 운영 조회가 필요하면 random incident correlation id만 사용하고 실제 대상 조회는 접근 통제된 DB 절차로 수행한다.
- backup, 감사 로그, Billing/security evidence의 보존 목적·기간과 live delete와의 차이를 개인정보 안내문에 명시한다.

## 12. 설정과 startup validation

제안 설정:

```yaml
app:
  learning-record-deletion:
    command-enabled: false
    read-fence-enabled: false
    writer-fence-enabled: false
    worker-enabled: false
    poll-interval: PT10S
    batch-size: 20
    lease-duration: PT30S
    target-duration: PT24H
    presigned-clock-skew: PT1M
    terminal-retention: P30D
    aggregate-zone-id: Asia/Seoul
```

검증 규칙:

- 모든 flag 기본값 OFF
- `command-enabled=true`이면 read/write fence와 worker가 모두 true여야 함
- writer fence 또는 worker가 true이면 Mongo replica-set Transaction manager가 있어야 함
- command 활성화 전 required index와 migration revision이 일치해야 함
- target duration, lease, batch, retry, retention은 양수·상호 일관성을 검사
- aggregate zone은 `Asia/Seoul`만 허용하고 runtime 기본 timezone에 의존하지 않음
- staging/prod에서 Legacy auth 또는 자동 index 생성에 의존하면 startup 실패
- S3 bucket/prefix 삭제 권한은 배포 전 read-only dry-run과 canary 객체로 검증하되 실제 사용자 객체를 사용하지 않음

## 13. index와 migration

필수 index 초안:

| Collection | Index |
| --- | --- |
| operations | `(userId, activeGuard)` partial unique where `activeGuard=true` |
| operations | `(status, nextAttemptAt, leaseUntil, _id)` worker 조회 |
| operations | `expiresAt` TTL, terminal 문서만 값 존재 |
| commands | `_id` unique, `(deletionId)`, `expiresAt` TTL |
| targets | `_id` unique, `(deletionId, targetType, stage)`, `(targetType, aggregateId)` |
| targets | `expiresAt` TTL, active/review에는 값 없음 |
| Billing coordination tombstone | source examId unique, `(userId, transferState)`, claim operation 참조와 version/CAS, group terminal 후에만 TTL |
| `learning_activity_daily_aggregates` | `(bucketDate, metric, examType, outcome)` unique, `(metric, bucketDate)` range query |
| existing data | userId/examId/attemptId 기반 bounded inventory/delete query index 확인 |

migration은 다음 순서로 만든다.

1. schema/index dry-run inventory와 duplicate/invalid 데이터 count
2. ACTIVE ownership guard 누락 사용자 count와 승인된 backfill
3. orphan Session/Result/Summary/Job/Challenge receipt count
4. 지표별 과거 복원 가능성·coverage 확인, 근거가 있는 범위만 선택적 shadow backfill·원본 대조
5. aggregate index와 나머지 index apply·definition verification
6. live 수집 시작 시각·legacy counted marker 초기화·coverage manifest 확정, backfill과 live bucket 분리 및 경합 검증
7. feature OFF 상태 startup validation

운영 DDL을 애플리케이션 startup에서 자동 실행하지 않는다. dry-run 결과가 0 또는 승인된 allowlist와 일치할 때만 apply한다.

## 14. 관측과 운영 대응

### 14.1 metric

- command `created|replayed|rejected_active|rejected`
- operation count by internal status
- stage duration, total completion duration, 24h SLA breach
- target count/deleted/remaining by `EXAM|CHALLENGE`
- retry/failure by fixed category `MONGO|S3|REDIS|COORDINATION|UNKNOWN_COMMIT|AUTH_CONFIG`
- late callback no-op count
- writer blocked/read hidden count
- lease lost, `cleanup_delayed`, `review_required`와 worker backlog age
- `canStartLearning=true|false` 상태별 operation 수와 safe checkpoint 소요시간

식별자를 label로 사용하지 않는다.

### 14.2 alert

- `NEEDS_REVIEW` 신규 발생은 즉시 운영 호출 대상으로 분리한다.
- `CLEANUP_DELAYED` 신규 발생·24시간 초과·backlog 증가는 cleanup SLA 경보로 분리하고 신규 학습을 차단하는 장애로 취급하지 않는다.
- oldest pre-checkpoint operation 24시간 초과
- worker가 켜졌는데 poll/claim heartbeat 없음
- S3 final sweep 반복 실패
- sealed old target remaining count가 삭제 후 증가하거나 sealed target 밖 삭제가 감지됨
- 삭제 fence 뒤 user-owned write 성공 감지
- auth/config circuit failure

### 14.3 수동 복구

- 신규 관리자 HTTP API를 만들지 않는다.
- runbook은 operation/target을 read-only inspect하고 원인을 제거한 뒤 승인된 내부 worker resume 절차를 사용한다.
- `CLEANUP_DELAYED`는 `canStartLearning=true`를 유지하고 old target cleanup만 재개한다. 운영 편의를 위해 write block을 다시 걸거나 신규 데이터를 target에 추가하지 않는다.
- `NEEDS_REVIEW`는 target seal·ownership·Billing·commit evidence를 먼저 복구하고 safe checkpoint 조건을 재검증한다. 조건이 충족되면 같은 operation을 resume하여 신규 학습 허용 상태로 전환한다.
- terminal 증거를 직접 수정하거나 target을 임의 삭제하지 않는다.
- 강제 `COMPLETED`, DB 직접 fence 해제, Billing evidence 삭제와 우회용 새 deletion operation 생성은 금지한다.

## 15. 테스트 계획

### 15.1 unit/contract

- lowercase UUID v4 key 검증, Body/userId 거절
- 같은 key replay, 다른 key active 요청 409/alias 미생성, terminal old key replay
- 공개 status projection과 내부 field 비노출
- 모바일 fixture: 202/200, timeout replay, not_requested, cleanup_delayed, needs_review와 `canStartLearning`
- safe checkpoint 전 read hide/write block과 checkpoint 뒤 old hide/new write·read 허용
- credit/entitlement/Token 변경 호출이 없음을 검증
- 사용자별 counter 삭제와 비식별 aggregate 유지, replay 중복 증가 방지
- aggregate schema에 금지 식별자·희소 dimension·원점수가 직렬화되지 않음을 검증
- KST 자정 경계, `ALL` dimension canonicalization과 결정적 `_id` 검증
- v1 allowlist 외 metric/outcome 거절, 하루 최대 18개 조합과 0건 sparse 미생성 검증
- failureCode/provider 원문이 aggregate outcome으로 확장되지 않고 Micrometer와 분리됨을 검증
- KST 전날 시작/다음 날 완료 fixture에서 발생일별 count만 표시하고 시작 대비 완료율을 만들지 않음. terminal 구성비 분모 0은 계산 불가
- coverage 밖 문서 부재는 미수집, 완전 수집 범위의 sparse 부재는 0; 최초 부분 일자와 복원 불가 과거 retry history 표시

### 15.2 Mongo replica-set integration

- 삭제 request와 Exam/Challenge writer 경쟁에서 한쪽만 선형화
- UserMerged/deletion flag OFF/OFF, ON/OFF, OFF/ON, ON/ON 조합 검증. 특히 OFF/ON에서 같은 guard touch 없이 writer가 deletion insert와 함께 성공하는 경로가 없음을 확인
- UserMerged source/target migration과 deletion 경합
- transaction rollback/transient retry/unknown commit majority recovery
- active partial unique, 다른 key 동시 요청의 단일 winner/409와 같은 key replay, lease 경쟁
- bounded inventory, orphan 탐지, stage 재실행과 count 검증
- 식별 가능한 analytics contribution은 삭제되지만 최종 aggregate는 유지됨
- 같은 domain transition replay·CAS 경쟁에서 일별 count가 정확히 한 번만 증가
- 증명 가능한 과거 bucket의 backfill 재실행은 멱등이고 live count를 덮어쓰지 않음. legacy marker 초기화 후 reopen해도 과거 최초 event를 중복 집계하지 않음
- COMPLETED → reopenMissingResult → COMPLETED, failed 후 사용자 복구, recoveryCycle/generation 증가와 HTTP 재전송 각각에서 최초 marker와 durable retry sequence 기준 expected count 검증
- 11문항 정상 완료의 25회 expected increment와 retry/duplicate/stale의 비증가 검증
- 동일 일자 aggregate hot document 동시 `$inc`에서 count 유실 없음
- Callback/worker와 Mongo batch delete 경합에서 재생성 없음
- safe checkpoint 전 `canStartLearning=false`, checkpoint commit 뒤 completion 전에도 신규 학습 허용
- `CLEANUP_DELAYED`에서 old hide와 `canStartLearning=true`, `NEEDS_REVIEW`에서 hide/block과 false 유지
- checkpoint 뒤 새 학습과 cleanup이 경합해도 sealed old target만 삭제되고 cutoff 이후 신규 record는 남음
- checkpoint 뒤 사용자 전체 `deleteMany(userId)` 경로가 사용되지 않음을 검증
- 원본 ExamSession이 남아 있으면 `COMPLETED` 전환 거절, tombstone에서 history 재노출·Session 복원 금지
- Challenge 당일 old Attempt/자식 참조 확보 → Callback seal → old slot 삭제 → checkpoint → 새 attemptId 생성; unique 충돌·old Attempt 반환 없이 새 학습 가능
- Challenge slot 삭제의 unknown commit/중간 재시작과 late Callback에서도 참조를 잃지 않으며 새 Attempt/Job/S3 객체는 old cleanup에 포함되지 않음
- 삭제 A 진행 → checkpoint → 새 학습 B → 다른 key 삭제 409 → A 완료 → 새 key 삭제 C가 B를 대상으로 처리. 거절된 요청이 예약되거나 A cutoff에 합쳐지지 않음

### 15.3 S3/Redis

- paginated prefix listing, partial DeleteObjects failure와 retry
- 기존 Presigned PUT로 첫 삭제 뒤 재업로드한 객체를 final sweep이 제거
- Challenge exact prefix와 Exam retry 여러 개 제거
- 다른 사용자·catalog prefix는 삭제하지 않음
- versioning 활성 fixture에서 current object, delete marker와 과거 version 제거 또는 승인된 lifecycle 증거 확인
- Redis는 inventory의 정확한 exam key만 삭제
- S3/Redis 장애 중 Mongo stage와 operation progress가 보존됨

### 15.4 Billing/AttemptGroup

- non-terminal creation operation status-first drain
- reserve 성공/Session 미commit은 cancel, commit winner는 inventory 편입
- OPEN group tombstone이 다음 Session을 같은 group replacement로 연결하고 새 credit를 소비하지 않음
- 동일/다른 creation key 동시 claim, reserve 응답 유실, Session commit unknown, confirm 유실에서 tombstone·operation·새 Session 연결이 하나로 수렴하고 INITIAL로 우회하지 않음
- CLAIMED 재시작과 CANCELED/EXPIRED 증명 뒤 해제, TRANSFERRED 재선택 금지, 새 Session 우선순위와 다음 시험 생성까지 기존 소비 정책 유지
- GRADING은 기존 completion/failure evidence로만 terminal 처리
- deletion 자체로 RETAKE_AVAILABLE/failureCode를 생성하지 않음
- 기존 outbox eventId/userId/canonical payload/digest 불변
- PENDING/IN_FLIGHT/BLOCKED_AUTH/DEAD_LETTER 각각에서 삭제 evidence 보존

### 15.5 Callback/E2E

- Exam GRADING의 DRAINING Callback은 실제 결과·terminal을 반영하고 SEALED 뒤 Feedback/Summary/Azure/SpeechAce late Callback만 no-op
- 정상 Callback과 final seal 경합·unknown commit에서 삭제 요청 때문에 인위적인 deadline 실패/RETAKE_AVAILABLE가 생성되지 않음
- Challenge fast Callback, timeout Callback와 duplicate/conflict
- 삭제 요청 전 시작한 AI dispatch가 결과를 다시 만들지 않음
- 앱 삭제 → 즉시 이력 숨김·초기 차단 → safe checkpoint → physical cleanup 중 신규 학습 → completed
- 로그아웃/Token 삭제 없이 같은 로그인 session 유지
- safe checkpoint 뒤 24시간 초과 cleanup failure는 `CLEANUP_DELAYED`와 `canStartLearning=true`
- target·ownership·Billing·unknown commit 불확실성 failure는 `NEEDS_REVIEW`와 `canStartLearning=false`
- 앱 접수/응답 유실/재시작 시 old cache 숨김, 늦은 old 목록·결과 응답 폐기, completed 시 checkpoint 후 새 cache 보존
- cleanup_delayed의 추가 삭제 409 문구와 새 학습 허용이 독립적으로 표시됨. 오프라인 다른 기기의 즉시 원격 삭제를 보장하지 않고 온라인 복귀 시 status 동기화

구현 완료 필수 명령:

```text
./gradlew clean test
./gradlew mongoIntegrationTest
관련 migration Node test
git diff --check
```

## 16. 구현 단계

### 단계 0 — 계약·허용 경계

- 확정된 신규 API/DTO, 공개 오류와 모바일 fixture를 contract 문서·테스트 fixture로 고정
- `AGENTS.md`에 학습 기록 삭제 전용 허용 범위 추가
- 완료/Callback tombstone·backup/log 30일, 보안 감사 90일과 coordination terminal+30일 설정·고지 반영
- Billing OPEN replacement tombstone contract 검증
- S3 `temp/*` prefix list/delete와 versioning 처리 IAM 선행 조건 확인

### 단계 1 — 모델·migration·fence

- operation/command/target 모델과 repository
- index migration, dry-run과 startup validator
- ownership guard와 deletion fence를 user-owned Transaction 경계에 통합
- read visibility service와 전체 endpoint inventory 적용
- durable 상태 전이 기반의 비식별 aggregate와 replay 중복 방지 구현
- 최초 counted marker·durable retry sequence, legacy coverage manifest와 선택적 검증 backfill
- command/status API를 flag OFF로 구현

### 단계 2 — coordination

- ExamCreationOperation deletion-aware drain
- OPEN/GRADING AttemptGroup 분기와 Billing tombstone
- tombstone 일회 claim/transfer와 status-first unknown commit 복구
- outbox 불변 검증
- Challenge worker/Callback deletion no-op
- Exam Callback과 scheduler deletion no-op/drain
- GRADING drain 완료 후 Callback seal, Challenge 참조 inventory 확보·당일 slot 선해제와 PREPARING_RESTART

### 단계 3 — physical cleanup

- S3 scoped delete/list와 capability-expiry final sweep
- Mongo bounded deleter와 orphan 처리
- Redis exact key cleanup
- safe checkpoint와 신규 학습 허용 전환
- final verification, completion과 retention TTL

### 단계 4 — 관측·통합 검증

- metric, Sentry-safe alert와 runbook
- unit/contract/replica-set/failure injection/E2E
- 공개 API·AI·S3·Redis 기존 계약 회귀 확인

### 단계 5 — rollout

1. migration dry-run/apply와 index 검증
2. 새 코드·worker OFF 배포
3. writer/read fence 활성화, 구버전 instance drain과 write 경합 검증
4. worker idle 활성화와 S3/Redis 권한 canary
5. command endpoint staging 활성화
6. 모바일 fixture와 `processing|cleanup_delayed|completed|needs_review`, `canStartLearning` staging E2E
7. safe checkpoint 전후 write 경합, 24시간 `CLEANUP_DELAYED`와 blocking `NEEDS_REVIEW` failure injection
8. production worker idle → command 활성화

rollback은 신규 command를 먼저 OFF로 하여 새 요청을 막되, 이미 active인 operation의 read hide와 worker는 끄지 않는다. safe checkpoint 전 operation과 `NEEDS_REVIEW`는 write block을 유지하고, checkpoint 뒤 operation과 `CLEANUP_DELAYED`는 sealed old target만 복구하면서 신규 학습 허용을 유지한다.

## 17. 예상 코드 변경 위치

```text
src/main/java/web/tosunsaeng/domain/learningrecorddeletion/
  api/
  application/
  domain/
  repository/
  infrastructure/
  config/

src/main/java/web/tosunsaeng/domain/exams/
  기존 user-owned writer, Callback, Billing/AttemptGroup coordination 연결

src/main/java/web/tosunsaeng/domain/challenge/
  기존 service/worker/Callback/S3 storage 삭제 fence 및 cleanup 연결

src/main/resources/application*.yml
scripts/mongodb/
src/test/
src/mongoIntegrationTest/
docs/contracts/
docs/codex/
```

실제 클래스명은 기존 패키지 naming에 맞춰 조정할 수 있지만 command, fence, worker, target tombstone과 storage adapter 책임을 한 서비스에 합치지 않는다.

## 18. 구현 전 남은 승인·확인

다음은 제품 방향을 다시 고르는 항목이 아니라 production에 필요한 계약·운영 값 확인이다.

1. Billing OPEN group을 coordination tombstone source로 replacement하는 실제 contract fixture
2. production bucket의 versioning/lifecycle 상태와 `temp/*` 한정 IAM 검증 결과
3. 확정된 30일/90일 보존기간, live delete와 backup 만료 차이를 반영한 개인정보 고지 문구
4. 상위·하위 Jira key, 담당자와 production rollout 승인자
5. 모바일 추가 삭제 409 fixture·로컬 cache 세대 처리·오프라인 안내 합의
6. 집계 coverage manifest·legacy 최초 marker 전략과 희소 집계 표시 기준의 개인정보/분석 검토

위 항목이 확인되기 전에도 feature OFF 구현과 테스트는 가능하다. production command 활성화는 모두 확인한 뒤 진행한다.

## 19. 완료 체크리스트

- [ ] 전용 구현 허용 범위와 Jira 확정
- [ ] 공개 API/mobile fixture 승인
- [ ] operation/command/target migration dry-run·apply 검증
- [ ] read hide/write fence 전체 inventory 적용
- [ ] Mongo/S3/Redis 멱등 cleanup 구현
- [ ] Exam/Challenge late Callback과 worker 재생성 방지
- [ ] Billing/AttemptGroup 최소 증거와 무복원 정책 검증
- [ ] safe checkpoint 전후 `canStartLearning`과 sealed target 불변 검증
- [ ] 당일 Challenge slot 선해제·재참여, 다른 key 추가 삭제 409와 완료 후 재삭제 검증
- [ ] DRAINING/SEALED Callback 경계, deletion-only guard와 Billing 일회 승계 경합 검증
- [ ] 24시간 target, CLEANUP_DELAYED와 NEEDS_REVIEW 분리 alert/runbook 검증
- [ ] `temp/*` IAM과 versioning/lifecycle 삭제 검증
- [ ] `./gradlew clean test`
- [ ] `./gradlew mongoIntegrationTest`
- [ ] migration Node test
- [ ] `git diff --check`
- [ ] staging mobile/AI/Billing failure-injection E2E
- [ ] 사용자별 analytics counter 삭제·비식별 aggregate 유지와 중복 집계 테스트
- [ ] Job reopen counted marker·retry sequence·통계 coverage와 terminal 구성비 명칭 검증
- [ ] 앱 old cache 즉시 무효화·late 응답 폐기·새 학습 cache 보존 검증
- [ ] 30일/90일 retention과 개인정보 고지 승인
- [ ] production flag 활성화 별도 승인

## 부록 A. 리뷰 보완의 현 코드 근거

아래는 검토한 기존 구현이며, 본 문서의 신규 설계가 이미 구현됐다는 의미는 아니다.

- `src/main/java/web/tosunsaeng/domain/usermerge/application/UserOwnedTransactionExecutor.java`: 현재 UserMerged writer flag 중심 Transaction/guard 참여 조건. §6.2는 deletion-only 조합까지 확장하는 계획이다.
- `src/main/java/web/tosunsaeng/domain/challenge/ChallengeService.java`, `ChallengeStore.java`, `scripts/mongodb/challenge-10s-prepare.js`: 같은 사용자·날짜·문항의 기존 Attempt 조회와 unique slot. §8.3은 이 제약을 삭제 후 당일 재참여 정책과 일치시키는 계획이다.
- `src/main/java/web/tosunsaeng/domain/exams/domain/entity/QuestionGradingJob.java`: `reopenMissingResult`의 상태/시각 초기화. §7.4의 최초 marker는 상태 CAS와 별도로 필요한 계획이다.
- `src/main/java/web/tosunsaeng/domain/exams/application/ExamSessionManager.java`, `BillingExamCreationSaga.java`, `BillingExamCreationTransactionService.java`: 현재 Session 기반 source 선택, replacement snapshot 검증과 Session commit. §5.4는 최소 tombstone을 이 흐름에 연결하는 계획이며 Billing 실제 fixture 확인은 여전히 필요하다.
- §3/§4/§8/§9의 추가 삭제·cache·drain 충돌은 기존 삭제 계획의 내부 명세 모순이었다. 이번에 문구와 경합 테스트 기준을 보완했으며 모바일 구현이나 runtime 테스트 결과를 주장하지 않는다.
