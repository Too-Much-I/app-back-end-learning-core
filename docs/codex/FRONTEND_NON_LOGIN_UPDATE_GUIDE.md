# 프론트엔드 1차 업데이트 연동 가이드 — 로그인 제외

## 1. 5줄 결론

1. 가장 큰 변경은 `POST /api/v1/exams`에 시험 시작 command별 lowercase UUID v4 `Idempotency-Key`를 보내야 한다는 점이다.
2. 최초 시작에서 응시 권리를 한 번 확정하고, 시험 완료 전 재시작은 새 key·새 examId를 사용하되 추가 entitlement를 차감하지 않는다.
3. 모든 필수 최초 제출 전에는 AttemptGroup이 `OPEN`, 제출 후 채점 중에는 `GRADING`, 정상 결과·Summary가 모두 있으면 `COMPLETED`다.
4. 채점·Summary 복구가 최종 실패하면 `RETAKE_AVAILABLE`이 되어 추가 차감 없이 처음부터 재시작할 수 있다.
5. 앱은 Billing 내부 API를 직접 호출하거나 userId·무료시험·기간제 이용권 정보를 시험 생성 body에 보내지 않는다.

## 2. 프론트가 반드시 반영할 변경

### 2.1 시험 시작 버튼의 command 관리

시험 생성 API는 그대로다.

```http
POST /api/v1/exams
Authorization: Bearer <Identity Access Token>
Idempotency-Key: <lowercase UUID v4>
```

- Request Body는 없다.
- 사용자가 시험 시작 또는 “처음부터 다시 시작”을 한 번 누를 때마다 새 UUID v4를 만든다.
- 그 요청의 성공·실패가 확정될 때까지 key를 로컬에 보관한다.
- timeout, 연결 종료, 응답 유실, `EXAM_CREATION_PROCESSING`, Billing 일시 장애의 재전송에는 **같은 key**를 쓴다.
- 사용자가 별개의 새 시작을 명시했을 때만 새 key를 만든다.
- key는 결제권·시험 그룹 식별자가 아니라 한 번의 Session 생성 command 식별자다.

권장 프론트 상태:

```text
IDLE
→ STARTING(key 저장, 시작 버튼 잠금)
→ SUCCESS(examId 저장, key 완료 처리)
또는 RETRYABLE_ERROR(key 유지)
또는 TERMINAL_ERROR(key 폐기)
```

중복 탭·연타·화면 재마운트로 동시에 여러 생성 요청을 보내지 않는다.

### 2.2 생성 성공 응답

성공 Response DTO는 바뀌지 않았다.

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

- Billing의 reservationId, AttemptGroup ID, consumption ID와 선택된 권리 정보는 응답에 노출되지 않는다.
- 같은 key를 replay해 성공하면 같은 생성 operation의 결과로 수렴하므로 새 시험으로 취급하지 않는다.
- 성공한 `examId`를 현재 Session으로 저장하고 해당 시험의 문제 화면으로 이동한다.

### 2.3 생성 오류별 처리

| HTTP/code | 의미 | key | 프론트 처리 |
|---|---|---|---|
| `400 IDEMPOTENCY_KEY_INVALID` | key 누락·대문자·non-v4 등 | 폐기 | 올바른 lowercase UUID v4를 새로 만들어 사용자 동작부터 재시작 |
| `402 ENTITLEMENT_INSUFFICIENT` | 보호 중인 replacement·활성 기간제 이용권·무료시험이 모두 없음 | 폐기 | 기간제 이용권 구매 화면 안내. 현재 결제 공개 API 배포 전에는 준비 중 UX |
| `409 EXAM_CREATION_PROCESSING` | 같은 operation 처리 중 | 유지 | `Retry-After` 우선, 없으면 backoff 후 같은 key로 재시도 |
| `409 IDEMPOTENCY_KEY_CONFLICT` | key가 다른 command와 충돌 | 폐기 | 자동 반복 금지; 새 사용자 시작 command로 새 key 발급 |
| `429 BILLING_RATE_LIMITED` | Billing rate limit | 유지 | `Retry-After`와 jitter를 적용해 같은 key로 재시도 |
| `503 BILLING_TEMPORARILY_UNAVAILABLE` | Billing timeout·장애·상태 확인 중 | 유지 | 권리 부족으로 단정하지 말고 같은 key로 재시도 |

`409/429/503`의 무한 재시도는 금지한다. 화면에는 “시험 시작 상태를 확인하고 있습니다”를 보여주고 제한 횟수 이후 사용자가 다시 확인할 수 있게 하되, **같은 시작을 확인하는 동안에는 기존 key를 유지**한다.

## 3. 시험 중단과 처음부터 재시작

이어풀기는 제공하지 않는다. 다만 새로운 무료 응시권을 계속 지급하는 것도 아니다.

> 최초 응시에서 권리를 1회만 확정하고, 그 시험이 완료될 때까지 동일 consumption 안에서 추가 차감 없이 처음부터 재시작한다.

| 서버 상태 | 의미 | 다시 시작 요청 |
|---|---|---|
| `OPEN` | 필수 `retryCount=0` 제출이 아직 모두 접수되지 않음 | 새 key·새 examId로 처음부터 시작, 추가 차감 없음 |
| `GRADING` | 모든 필수 최초 제출이 접수되어 기존 채점·Summary 복구 중 | 새 Session 생성을 잠시 막고 기존 결과 polling·복구 우선 |
| `RETAKE_AVAILABLE` | 채점·Summary 복구가 최종 실패했거나 결과 정합성 오류 | 새 key·새 examId로 처음부터 시작, 추가 차감 없음 |
| `COMPLETED` | 필수 feedback·유효 점수·조회 가능한 Summary 존재 | 다음 시험은 새로운 entitlement 필요 |

재시작 시:

- 기존 Session은 `ABANDONED_RESTARTED`로 종료된다.
- 답안, 녹음, 결과, 채점 Job과 Summary는 새 Session에 복사하지 않는다.
- 같은 모의고사 문제지와 consumption/AttemptGroup 연결은 서버가 관리한다.
- 앱은 기존 examId를 생성 API body에 보내지 않는다.
- 앱 종료만으로 서버 Session이 즉시 바뀌는 것은 아니며, 사용자가 다음 시작을 명시했을 때 교체된다.

## 4. 문항 제출과 채점 화면

기존 순서는 유지한다.

```text
문항 표시
→ upload-url 발급
→ Presigned URL에 raw audio PUT
→ submit
→ 문항 status polling
→ 시험 status polling
→ Summary·문항 결과 조회
```

### 4.1 업로드

```http
GET /api/v1/exams/{examId}/questions/{questionNumber}/upload-url?retryCount=0
```

- S3 PUT에는 Identity Authorization header를 붙이지 않는다.
- `multipart/form-data`가 아니라 raw audio binary를 PUT한다.
- `fileKey`를 submit body에 다시 보내지 않는다.
- URL 만료 시 같은 upload-url API로 재발급한다.

### 4.2 제출

```http
POST /api/v1/exams/{examId}/questions/{questionNumber}/submit?retryCount=0
```

- Body는 없다.
- 최초 답변은 `retryCount=0`, 사용자가 같은 문항을 새로 녹음한 재답변만 1 이상이다.
- 시험 전체 복구는 사용자의 새 녹음이 아니라 기존 `retryCount=0` 채점 Job을 대상으로 한다.

### 4.3 polling

- 문항 상태 `PENDING | PROCESSING`은 계속 polling한다.
- `COMPLETED | FAILED`면 해당 문항 polling을 중단한다.
- 앱이 종료돼도 서버의 AI Job은 취소되지 않는다.
- 재진입 시 현재 examId의 상태·결과를 다시 조회한다.
- 모든 문항 제출 직후 Summary가 즉시 있다고 가정하지 않는다.

## 5. 채점·Summary 복구

사용자가 모든 필수 문제를 제출했는데 결과 또는 Summary가 끝내 준비되지 않을 때 사용하는 기존 API다.

```http
POST /api/v1/exams/{examId}/grading/retry
Authorization: Bearer <Identity Access Token>
```

- Request Body는 없다.
- 새 시험이나 새 녹음을 만드는 API가 아니다.
- `retryCount=0`의 누락·실패한 기존 채점만 복구한다.
- `summaryAction`은 `NOT_READY | WAITING | RETRIED | ALREADY_COMPLETED`다.
- `missingSubmissionQuestionNumbers`가 있다면 서버 채점 복구 대상이 아니라 실제 제출이 없다는 뜻이다.
- 복구 중에는 동일 examId의 status/Summary를 polling한다.
- 최종 복구 실패로 서버가 `RETAKE_AVAILABLE`을 확정한 뒤 사용자가 다시 시작하면 생성 API에 새 key를 사용한다.

`FEEDBACK_GENERATION_FAILED`를 받으면 일반 500 화면보다 채점 복구 UX를 우선한다. 복구 API도 반복 실패하고 서버가 재시작 가능 상태로 수렴하면 처음부터 재시작을 안내한다.

## 6. 결과·이력 화면

- 시험 완료는 단순히 모든 submit 호출이 끝난 시점이 아니다.
- 필수 feedback, 유효 점수와 조회 가능한 결정적 Summary가 모두 있어야 한다.
- `GET /api/v1/exams/{examId}/summary`의 결과가 준비되기 전에는 완료 화면을 확정하지 않는다.
- `GET /api/v1/exams/history`의 `summaryAvailable`을 결과 화면 진입 가능 여부에 사용한다.
- 기존 외부 이력 status는 `IN_PROGRESS | COMPLETED | ABANDONED`다. 내부 `OPEN/GRADING/RETAKE_AVAILABLE`을 프론트 enum으로 임의 추가하지 않는다.
- 폐기된 examId 요청에서 `EXAM_4007`을 받으면 이어풀기를 시도하지 말고 현재 새 Session으로 이동한다.

## 7. Part 4 표 렌더링

문제 응답의 `tableContext`는 `object | null`이며 서버가 고정 DTO, HTML이나 Markdown으로 바꾸지 않고 Mongo의 비정형 JSON을 전달한다.

- Part 4에서는 `tableContext`를 안전한 구조화 UI로 렌더링한다.
- 알 수 없는 key가 있어도 앱이 crash하지 않도록 tolerant parser를 사용한다.
- HTML로 직접 삽입하지 않는다.
- 빈 object는 정상적으로 허용한다.
- Part 4에서 null이면 catalog 설정 오류일 수 있으므로 일반 빈 표로 조용히 처리하기보다 오류 수집과 fallback UI를 적용한다.
- `table_image_url`은 외부 응답 계약이 아니므로 의존하지 않는다.

## 8. phone 재가입 사용자

검증된 같은 휴대전화 번호로 탈퇴 후 재가입한 사용자는, 서버가 과거의 미완료 consumption continuation을 발견하면 다음 시험 생성 때 기존 AttemptGroup·mockExam에 새 examId를 연결할 수 있다.

- 프론트는 별도 continuation API를 호출하지 않는다.
- 생성 Request/Response는 일반 시험 시작과 동일하다.
- 과거 답안·결과·녹음은 복사되지 않는다.
- phone 번호, 과거 userId 또는 continuationId를 생성 body에 보내지 않는다.

## 9. Billing·결제 경계

- 모바일 앱은 Billing의 `/internal/**` API를 호출하지 않는다.
- 기존 AttemptGroup replacement, 활성 기간제 이용권, 검증 phone당 무료시험의 선택은 Learning Core→Billing 서버 간 처리다.
- 기간제 이용권은 활성 기간 동안 횟수 차감 없이 사용하며, 앱이 시험 생성 body에 상품이나 권리 식별자를 보내지 않는다.
- 인앱결제 상품 구매·복원용 공개 API는 별도 계약·배포 확인 후 연결한다.
- `ENTITLEMENT_INSUFFICIENT`에서 아직 구매 API가 활성화되지 않았다면 결제 완료를 가장하거나 시험 생성을 우회하지 않는다.

## 10. 현재 구현·출시 상태

| 항목 | 코드 상태 | 프론트 적용 |
|---|---|---|
| 시험 생성 `Idempotency-Key` | `TMI-116` 구현·develop 반영 | 선배포 필요 |
| reserve → Session commit → confirm | `TMI-116` 구현, flag 기본 off | 프론트는 생성 API만 호출 |
| AttemptGroup GRADING/COMPLETED/RETAKE_AVAILABLE | `TMI-118` 구현, writer/publisher flag 기본 off | 외부 기존 status 계약 유지 |
| phone 재가입 continuation | `TMI-120`·`TMI-122` 구현, flag 기본 off | 별도 UI/API 없음 |
| Guest UserMerged 학습 이력 이전 | 후속 통합·검증 필요 | 완료 전 production merge UX 금지 |
| 인앱결제 공개 API | 후속 범위 | 배포 전 호출 금지 |
| 10초 챌린지 API | 계약 승인, runtime 미구현 | feature flag 뒤에서 비노출 |

Mongo replica-set/index migration, Billing·Learning Core 배포, Lattice IAM/SG, INITIAL/OPEN replacement/GRADING/RETAKE/response-loss staging E2E가 끝나기 전에 production 관련 flag를 켜면 안 된다.

## 11. 프론트 체크리스트

- [ ] 시험 시작마다 lowercase UUID v4 key를 생성한다.
- [ ] 동일 시작 요청의 timeout·응답 유실 재시도에는 같은 key를 사용한다.
- [ ] 별개 재시작에 이전 key를 재사용하지 않는다.
- [ ] 시작 버튼 연타와 생성 요청 동시 실행을 막는다.
- [ ] `EXAM_CREATION_PROCESSING`, 429, 503에서 `Retry-After`와 backoff를 적용한다.
- [ ] `ENTITLEMENT_INSUFFICIENT`만 기간제 이용권 구매 필요 상태로 처리한다.
- [ ] 미완료 재시작은 이어풀이가 아니라 처음부터 시작함을 안내한다.
- [ ] S3 PUT에 Bearer Token을 붙이지 않고 성공 후 submit한다.
- [ ] terminal 문항 상태에서 polling을 멈춘다.
- [ ] Summary가 조회 가능해야 완료 화면으로 이동한다.
- [ ] `FEEDBACK_GENERATION_FAILED`를 grading retry UX에 연결한다.
- [ ] Part 4 `tableContext`를 tolerant하게 렌더링한다.
- [ ] Billing 내부 API와 내부 ID를 앱에서 사용하지 않는다.
- [ ] 미구현 결제·Challenge UI는 feature flag로 숨긴다.

## 부록 A. 기준 계약

- 전체 프론트 API: `docs/contracts/FRONTEND_API_HANDOFF.md`
- Billing 권리·시험 완료 정책: `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`
- 시험 생성 saga: `docs/codex/BILLING_RESERVATION_SAGA_IMPLEMENTATION_PLAN.md`
- AttemptGroup 상태: `docs/codex/ATTEMPT_GROUP_OUTBOX_PUBLISHER_IMPLEMENTATION_PLAN.md`
- phone 재가입: `docs/codex/PHONE_REJOIN_CONTINUATION_IMPLEMENTATION_PLAN.md`
- 전체 기능 흐름: `docs/codex/APP_FEATURE_LOGIC_OVERVIEW.md`
