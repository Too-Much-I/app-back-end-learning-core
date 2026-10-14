# TMI-218 — 알림 독립 접수 증거 및 문제 선정 구현

- 날짜: 2026-10-14
- 브랜치: `feat/TMI-218-submission-evidence-exam-selection` (local develop `3d50195`에서 분기)
- 상태: P1 기반 코드·로컬 검증 완료. 공개 유료 생성 경로 연결/활성화는 P2/P3 이후다. Jira 상태는 변경하지 않았다.

## 1. 5줄 결론

1. 기존 receipt와 전체 접수 marker를 공통 `ExamSubmissionEvidence`로 분리했다.
2. coverage version 1 Session은 알림 tracking 없이도 신규 필수 retry0 Job 접수와 증거를 같은 owner Transaction에서 저장한다.
3. 부분 접수는 동일 mock, 전체 접수·채점 대기는 다른 적격 mock 우선이며 첫 operation의 판정을 고정한다.
4. 새 준비 operation은 기존 Billing 발신/worker와 격리했다. 신규 Session 생성·confirm 후 전환은 구현하지 않았다.
5. 단위 1,036개·격리 Mongo 158개가 통과했고, 외부 API/AI/S3/Redis 및 기존 fixture bytes를 유지했다.

## 2. 반드시 읽어야 하는 내용

P1은 전체 유료 연동 완성이 아니다. `PaidExamPreparationService`는 공개 controller/saga에 연결하지 않았다. 현 Session 생성 writer는 `submissionEvidenceVersion=1`을 쓰지 않으며 P2/P3에서 선정 snapshot을 새 Session에 설치해야 한다. 기존 Session의 coverage/source를 추론하거나 backfill하지 않는다.

`app.billing.paid-exam.enabled` 기본값은 false다. true이면 `PaidExamActivationGate`가 기동을 거절한다. 실환경 설정은 변경하지 않았다. P1 준비 operation의 `paidSelection`/recovery schema 2는 기존 HTTP saga 발신과 schema 1 worker 후보 조회를 차단한다. 향후 worker 지원 없이 이 준비 메서드를 운영 경로에서 호출해서는 안 된다.

## 3. 결정 및 후속 확인

- 기존 [구현계획](LC_BILLING_PAID_EXAM_IMPLEMENTATION_PLAN.md)의 P2/P3에서 wire digest·phone/삭제 continuation·source 검증·새 대기 Session·confirm 후 로컬 마무리를 연결한다.
- legacy/알 수 없는 coverage, 지원하지 않는 이전 lifecycle과 삭제 continuation은 준비 단계에서 fail-closed다. 실제 legacy exact manifest 지원은 후속 범위다.
- P1 서비스의 내부 실패를 최종 공개 오류로 매핑하고 owner/lease/unknown commit 복구를 전체 생성 경로에서 검증해야 한다.
- 신규 flag 전체 조합/index 점검·구 writer drain·staging E2E·실제 배포/활성화는 별도다. 기존 A/R2/simplified 계약과 공동 보류 사항을 바꾸지 않았다.

## 4. 위험과 미확인 사항

- 기존 owner executor는 Transaction 충돌을 최대 3회 즉시 재시도한다. 동시 요청에서 소진될 수 있다. 테스트는 원인이 확인된 `TransientTransactionError`만 동일 요청으로 재전송하여 수렴을 확인하며 unknown commit을 같은 것으로 취급하지 않는다. 공유 재시도 정책은 이번에 변경하지 않았다.
- 미완료 접수 receipt에는 TTL이 없으므로 용량은 별도 모니터링이 필요하다. 전체 접수 시에만 marker와 receipt의 완료+3일 expiry를 원자 저장한다. marker는 receipt 삭제 후에도 유지된다.
- 전체 simplified 상태 시나리오 28개를 실행 완료한 것은 아니다. 이번 P1 테스트와 기존 오프라인 fixture 독립 검증은 별도 증거다.
- 실 Billing/AI/S3/Redis/운영 Mongo를 호출하지 않았다. 실제 TTL 모니터 대기는 하지 않고 receipt 삭제 후 marker 판정 생존을 검증했다.

## 5. 구현 및 변경 파일

| 파일/영역 | 변경 |
| --- | --- |
| `ExamSubmissionEvidence` (신규) | 필수집합 validation, receipt 중복 제거, Session version fence, 완료 marker/3일 expiry |
| `ExamGradingService` | schema 1 Session의 알림 독립 접수 기록, frozen 필수집합 reader, owner guard 필수 |
| `NotificationSubmissionTracker` | 기존 flag/owner 처리 유지, 공통 증거 writer 위임 |
| `ExamSession` | 내부 submissionEvidenceVersion/requiredQuestionNumbers 추가; accepted 배열 없음 |
| `PaidExamSelection` / `ExamCreationOperation` | 시작 사유·단일 문제 필수집합 불변 snapshot, 기존 원 Session/group/mock 필드 재사용 |
| `PaidExamPreparationService` (신규) | 같은 owner Tx에서 마지막 접수와 선정 직렬화, 같은 key 재조회 우선, catalog count/sequence 재사용 |
| `BillingExamCreationSaga` / `PaidExamActivationGate` (신규) | 미완성 paid snapshot의 구 Billing 경로 발신 및 설정 활성화 차단 |
| `PaidExamP1Test`, `PaidExamP1MongoTest` (신규), 기존 grading/saga 테스트 | 아래 회귀 검증 |
| AGENTS/구현계획/ADR/CURRENT_STATE/WORKLOG/이 보고서 | 승인·구현 상태 및 후속 경계 기록 |

신규 준비는 실제 새 Session을 저장하거나 기존 Session을 종료하지 않는다. 접수·준비의 Session version touch 외 원 Session 상태는 유지한다. wire용 canonical payload/digest 확장은 P2에서 선정 snapshot으로 구성해야 한다. 공개 Request/Response/BaseResponse, `retryCount`, AI `user_id=examId`, Callback JSON, S3/Redis key는 불변이다.

## 6. 검증 근거

| 실행 | 결과 |
| --- | --- |
| `./gradlew clean test` | 1,036개, 실패/오류/skip 0 |
| `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew mongoIntegrationTest` | 158개, 실패/오류/skip 0 |
| P1 신규 Mongo 테스트 | 12개: 부분/전체 접수, 3일 expiry/삭제 이후 marker, restart replay, 후보 fallback/실패 rollback, legacy 미추론, 완료/신규 선정, 잘못된 문항 rollback, 중복 expiry 불변, 탈퇴 replay 차단, 구 worker 격리, 동시 접수/준비, 동시 same-key |
| 신규 단위/기존 서비스 추가 테스트 | 7개: 설정 차단, 불변 snapshot/schema, 필수집합/coverage, guard 없는 접수 거절, 알림 없는 실제 grading 진입, 구 Billing client 미호출 |
| fixture checksum | A 3 + paid R2 13 + simplified 9개 전부 일치 |
| `git diff --check` | 통과 |

로컬 Docker 29.6.1과 Testcontainers 1.20.4의 기본 API 버전 조합은 HTTP 400으로 초기화 실패했다. 테스트 실행 프로세스에만 API 1.44를 지정해 해결했다. Docker 전역 설정/의존성/배포 파일은 변경하지 않았다. 초기 import 누락 및 경합 시 무조건 첫 호출 성공을 요구하던 테스트를 보완한 뒤 위 전체 테스트를 재실행했다.

기존 dirty 문서/fixture test와 `.DS_Store`는 보존했다. 이번 작업에서 예상 밖 범위 변경은 없으며 Billing/Identity 저장소·실DB/index·배포·Jira·commit/push는 변경하지 않았다.
