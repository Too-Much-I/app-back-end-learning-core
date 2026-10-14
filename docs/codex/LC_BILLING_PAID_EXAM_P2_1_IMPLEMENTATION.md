# LC 유료 시험 P2.1 — 오류·phone 검증 준비

- 날짜: 2026-10-14 / 브랜치: `feat/billing-paid-exam-p2`
- 관련: TMI-116/TMI-128/TMI-122, 선행 P1 TMI-218. P2.1 전용 Jira는 생성하지 않았다.
- 범위: LC client/validator/영속 기반·오류 mapper/방어적 worker 분류. 공개 paid saga 연결, MEMBER 실제 재개 및 J3 terminal 처리, DB 설치/배포/활성화는 아니다.

## 변경 동작·파일

- `PaidBillingErrorDecoder`: 정확한 오류 네 필드·strict JSON/UTF-8·HTTP/endpoint/retryable/Retry-After 및 MEMBER/J3/owner 고정 문구 검증. message/correlation/body를 저장하거나 예외 cause에 붙이지 않는다. MEMBER 대기, 소유권 차단, 예약 전 거절 증거를 별도 category로 분리했다.
- `BillingClientException`, `BillingReservationClient`/`JwtBillingReservationClient`: paid phone reserve/discovery와 strict 오류를 사용하는 confirm/cancel 메서드 추가. 기존 일반 메서드의 code-only 오류 호환은 유지한다. HTTP 오류 metric은 고정된 waiting_member/blocked_owner/pre_reserve_rejected/business_rejected 등으로 분류한다. 성공 HTTP 이후 업무 snapshot 검증까지 성공했다는 metric은 아니다.
- `PaidReservationProtocol`: typed phone 6필드 요청과 exact echo. INITIAL/SUBSCRIPTION은 신규 group, REPLACEMENT/EXAM_PASS는 expected group이며 둘 다 transition 금지. 일반 restart 요청과 혼합하지 않는다.
- `PaidReservationEvidenceStore`: owner Transaction에서 phone 요청 digest 고정 및 기존 embedded evidence 저장. 원 expectedGroup은 그대로 두고 실제 group을 snapshot에 저장한다. Session 생성·owner 이전·기존 답안 복사는 하지 않는다.
- `BillingExamCreationSaga`/`ErrorStatus`: MEMBER는 기존503/Retry-After1, revoked/transition은 승인된 두409 mapper를 준비했다. owner/계약오류/J3 증거는 자동 retry header를 부여하지 않는다. 신규 client는 공개 saga에 아직 연결하지 않았다.
- `BillingReservationReconciliationService`: owner 오류는 BLOCKED_OWNER이지 auth circuit이 아니다. 구 worker가 다른 신규 paid category를 받는 방어적 경로는 NEEDS_REVIEW로 격리하며 guard 해제/terminal 처리/자동 재개하지 않는다. 정상 paid schema2는 기존대로 구 worker 대상에서 제외된다. 이는 P3 MEMBER 대기 정책 구현을 대체하지 않는다.
- AGENTS/구현계획/ADR/CURRENT_STATE/WORKLOG 및 이 보고서에 승인·구현 경계를 기록했다.

## 테스트 범위와 공동 fixture 불일치

- `PaidReservationClientTest`: 실제 loopback HTTP로 원문 오류 fixture22개, phone selected-field fixture11개를 검증한다. phone fixture는 완전한 DTO가 아니므로 테스트용 고정 operation/reservation/status/expiry/user envelope를 추가했음을 테스트명에 명시했다. reserve/status 연결 및 불변 evidence 비교를 실제 구현으로 수행한다.
- **E06-owner 1개는 공동 기대값과 불일치**한다. 원문에는 예약 전 거절 문구가 있지만 frozen contract는 `The operation owner does not match the current owner.`를 요구한다. 원본은 수정하지 않고 CONTRACT_ERROR 거절을 회귀 검사한다. 나머지21개 기대값은 일치하며, 올바른 owner 고정 문구는 별도 테스트에서 OWNER_BLOCKED로 확인한다. 22개 공동 기대값 전부 통과로 보고하지 않는다. 기존 test-only oracle 통과와도 구분한다.
- discovery200/204/GRADING COMMAND_PROCESSING, 오류 뒤 reserve fallback 미발생, abbreviated confirm/cancel과 full evidence의 분리, exact Session 불일치를 검사한다.
- `PaidBillingErrorDecoderTest`: 잘못된 Retry-After/UTF-8, auth 우선순위, endpoint별 업무오류와 parser cause 비노출을 검사한다.
- `BillingExamCreationSagaTest`/`PaidErrorRecoveryBoundaryTest`: mapper 및 worker 분류를 직접 호출해 circuit/자동 retry/guard 해제 없는 경계를 검사한다. 실제 P3 복구 시나리오 테스트라고 주장하지 않는다.
- `PaidExamP1MongoTest`: 격리 replica-set에서 phone paid/free 저장·same-request digest·원group 보존·Session 미생성/guard 유지·owner 불일치 rollback을 추가 확인한다.

## 유지 계약·남은 위험·다음 작업

공개 URL/Method/요청/성공 DTO/BaseResponse, AI `user_id=examId`, retryCount, S3/Redis key와 workload A 계약을 유지한다. 두 신규409는 승인된 오류 mapper 준비이며 공개 paid 기능은 차단 상태다. 새 collection/index/TTL/환경 설정은 추가하지 않았다.

P3 전에는 E06-owner 공동 문구를 Billing과 정정 revision으로 합의해야 한다. 기존 fixture bytes/hash를 덮어쓰지 않는다. MEMBER 재개 원인 증거·owner/lease/version·J3 PREPARED/예약/commit 부재 확인, confirm/status와 원자적 Session 마무리를 다음 단계에서 연결해야 한다. 실제 상태28개/E2E·역방향 revoke·legacy/purge·운영 rollout gate 완료가 아니며 배포 전에 별도 검증이 필요하다.

기존 미커밋 P2와 머지/인계 검토 기록, 사용자 DS_Store 변경은 보존했다. 이번 작업의 예상 밖 제품 변경 없음. Billing/Identity 저장소·실제 DB·Jira·외부 메시지·commit/push·배포는 변경하지 않았다.

## 검증 결과

`JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew clean test mongoIntegrationTest bootJar` 성공 후 원문 bytes/추가 HTTP 테스트를 포함해 `test mongoIntegrationTest bootJar` 재검증도 성공했다. 최종 JUnit XML 합계는 단위1,141개 실패/오류0·기존 LearningImportedContentTest 조건부1 skip, Mongo177개 실패/오류/skip0이다. bootJar 성공. 후속 실행의 Mongo/bootJar는 코드 불변으로 UP-TO-DATE이며 앞선 clean 실행 성공을 유지한다.

P2 기준 대비 단위39개·Mongo1개가 추가됐다. A3/R2 13/simplified9 원본 checksum25개 모두 일치, `git diff --check` 통과. 실제 Billing/Identity/앱/운영 DB와 E2E는 실행하지 않았다.
