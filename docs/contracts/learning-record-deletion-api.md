# 학습 기록 독립 삭제 API v1

## 결론

- 계정·로그인·Token·이용권을 삭제하거나 복원하지 않는다.
- `DELETE /api/v1/learning-records`: body/query 없음, `Idempotency-Key` 필수 lowercase UUID v4.
- `GET /api/v1/learning-records/deletion`: body/query 없음, 현재 사용자의 최신 삭제 상태.
- 기존 BaseResponse를 사용하며 userId/시험/AttemptGroup/내부 stage는 노출하지 않는다.
- 기본 OFF. 이 문서는 서버 구현 계약이며 실제 앱 적용·테스트 서버 활성화 완료를 뜻하지 않는다.

## 요청·응답

인증은 기존 Access Token을 사용한다. 사용자 ID를 전송하지 않는다. local/test Legacy 예외는 기존 규칙을 따르고 staging/prod에서는 JWT 모드가 필요하다.

DELETE 신규/진행 중 같은 key replay는 202, 완료된 같은 key replay는 200이다. 진행 중 다른 key는 409이며 새 삭제를 예약하거나 범위를 확대하지 않는다. 완료된 과거 key는 이후 새 작업이 있어도 과거 결과만 반환한다. 멱등 mapping 보존은 완료 후 30일이다.

성공 `result`:

```json
{
  "deletionId": "139f345b-9be8-43a3-a63d-9108df972741",
  "status": "processing",
  "canStartLearning": false,
  "requestedAt": "2026-10-03T00:00:00Z",
  "physicalDeletionTargetAt": "2026-10-04T00:00:00Z",
  "completedAt": null
}
```

날짜는 UTC ISO-8601 문자열이다. 목표 시각은 24시간 목표이며 삭제 완료를 보증하는 시각이 아니다. GET은 항상 성공 시 200이며 작업이 없으면 deletionId/날짜는 null, status는 `not_requested`, canStartLearning은 true다.

| status | canStartLearning | 앱 처리 |
| --- | --- | --- |
| not_requested | true | 정상 이용 |
| processing | false | 이전 기록 숨김, 새 학습 잠시 대기 |
| processing | true | 이전 기록 정리 중, 새 학습 허용 |
| cleanup_delayed | true | 물리 정리 지연 안내, 새 학습 허용 |
| needs_review | false | 이전 기록 숨김 유지, 고객지원 안내 |
| completed | true | 해당 삭제 세대 cache만 정리 |

## 오류와 앱 필수 처리

| HTTP/code | 처리 |
| --- | --- |
| 400 LEARNING_RECORD_DELETION_INVALID_REQUEST | body/query 제거, key 형식 확인 |
| 409 LEARNING_RECORD_DELETION_ALREADY_ACTIVE | 이전 작업 완료까지 추가 삭제 금지 |
| 409 LEARNING_RECORD_DELETION_IDEMPOTENCY_CONFLICT | 기존 key의 요청 의미가 충돌; 새 key로 자동 우회하지 않음 |
| 409 LEARNING_RECORD_DELETION_IN_PROGRESS | 기존 학습 쓰기 차단; 최신 상태 조회 |
| 503 LEARNING_RECORD_DELETION_TEMPORARILY_UNAVAILABLE | 결과 불명 가능; 같은 key 유지, 상태 조회/재전송 |
| 기존 401/403 | 기존 인증·권한 처리 유지 |

- UI 이중 확인 뒤 key를 한 번 만들고 응답 유실·앱 재시작에도 같은 key를 사용한다. 확인용 body는 보내지 않는다.
- 접수/결과 불명 시 이전 cache를 숨기고 늦게 도착한 삭제 전 조회 응답을 폐기한다. 로그인 Token은 유지한다.
- 온라인 복귀 때 status를 동기화한다. 오프라인인 다른 기기의 cache를 즉시 원격 제거하는 계약은 아니다.
- 삭제 완료 시 checkpoint 뒤 새로 만든 학습 cache를 함께 지우지 않는다.
- command flag만 OFF이면 DELETE는 503이지만 status와 기존 fence는 유지된다.
- 기존 상세/결과는 기존 not-found 응답으로 숨긴다. 기존 API DTO/AI callback JSON/retryCount/S3·Redis key는 변경하지 않았다.

서버 계약 테스트: `DeletionApiTest`. 전체 정책과 앱 고정 안내 문구는 `../codex/LEARNING_RECORD_DELETION_IMPLEMENTATION_PLAN.md` §3–4를 따른다. 실제 모바일 담당자 fixture 합의·앱 테스트는 별도 필요하다.
