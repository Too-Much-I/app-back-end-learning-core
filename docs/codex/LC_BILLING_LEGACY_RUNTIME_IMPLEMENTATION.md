# LC legacy 생성 runtime 연결

- 후속: 선행 구현은 PR #44로 머지됐다. 별도 브랜치의 [test 한정 기동 준비](LC_BILLING_PAID_EXAM_TEST_ACTIVATION.md)는 승인된 조건부 예외를 추가한다. 아래 기동차단/미커밋 표기는 legacy 구현 당시 상태이며 실제 서버 활성화 완료를 뜻하지 않는다.

## 1. 5줄 결론

1. 2026-10-16 PR #43을 develop에 머지한 뒤 `feat/billing-paid-exam-legacy-runtime`에서 후속 구현했다.
2. 별도 승인 summary와 sealed exact manifest가 일치하는 기존 시험에만 legacy 예외를 적용한다.
3. 전체접수 근거가 불충분하면 동일 mock, 전체접수 marker가 있으면 다음 문제, 완료 상태는 기존 선정 규칙을 따른다.
4. 원 시험 정보는 LC 내부 operation에 고정하고 Billing에는 일반 INITIAL 요청을 보낸다. 원 시험에 source를 backfill하지 않는다.
5. 실제 manifest 설치/봉인·실DB/index·활성화·공동 E2E는 수행하지 않았으며 ON 기동차단은 유지한다.

## 2. 반드시 읽어야 하는 내용

`LegacyExamRuntimeConfiguration`은 paid ON일 때만 별도 서버 설정의 expected summary를 읽는다. workload JWT environment와 legacy environment가 같아야 하고, DB/name/revision/hash/count·봉인·index 확인은 기존 `verifySealed`를 그대로 사용한다. OFF에서는 legacy 설정을 해석하거나 Mongo inventory를 조회하지 않는다. 확인된 목록은 재시작까지 불변이며 클라이언트 입력이나 동일 DB의 state만으로 승인값을 자동 생성하지 않는다.

`PaidExamPreparationService`는 원 Session과 같은 owner/Session Transaction fence에서 접수판정을 고정한다. coverage가 없는 legacy도 동일 문제 선택이 가능하지만, 목록 밖 source 누락·Billing metadata 충돌·기동 검증 실패는 거절한다. 상태 없는 원 시험의 상태를 IN_PROGRESS로 변경하거나 terminal 시험을 부활시키지 않는다. 신규 시험을 준비하는 것과 기존 시험 이용 권한은 별개다.

operation의 `paidLegacySource`에는 manifest summary를 저장한다. 원 Session ID/mock와 startReason은 기존 내부 필드를 사용하며 요청 replay는 최초 판정을 재사용한다. Billing request에는 restartContext/transition을 만들어 소급 연결하지 않는다. 새 INITIAL/필수 source 응답만 허용한다.

confirm 성공 증거 뒤에만 exact 원 Session의 신규 제출 차단·새 Session 활성·operation 성공을 같은 Transaction으로 기록한다. 실패/cancel 시 원 시험은 닫지 않는다. finalize 전에 원 소유자/목록 결속을 다시 확인하고, 삭제·owner·revoke gate를 우회하지 않는다.

legacy 원 Session을 닫을 때 목록 summary만 closure metadata로 남긴다. 문항 목록·답안·Job을 복제하거나 과거 최초접수 시간을 생성하지 않는다. 기접수 Job의 실제 최초 사용자접수 시각 또는 원 receipt가 closure 이전임을 확인할 때만 기존 작업을 허용한다. 전체접수 marker가 없으면 동일 문제 fallback만으로 새 Summary를 허용하지 않는다. 기존 완료 기록은 유지한다.

## 3. 사용자 결정 사항

동일 문제 반복 비용은 앞선 사용자 승인 범위다. 추가 제품 정책을 선택하지 않았다. 이번 새 브랜치 변경은 아직 commit/push하지 않는다. 배포·활성화·목록 설치와 실제 운영 검증은 별도 승인 범위다.

후속 「이제 구현 다 했나? 아직 다 안 했으면 구현해줘」로 남은 LC 코드 경계를 점검하고 구writer 재개 차단을 추가했다. 이 요청으로 실환경 목록 설치/봉인·운영 purge나 공동 계약 보류를 임의 확정하지 않는다.

## 4. 위험 및 배포 전 확인

- 실제 prod inventory/해당 건수는 이번에 조회하지 않았다. 과거 1,493/146건을 새 목록으로 재사용하지 않는다.
- 원 mock를 식별할 수 없거나 여러 활성 시험이 충돌하면 자동 복구/추정하지 않는다. 신규 권리 확정이 기존 불명 상태의 권한을 만들어 주지 않는다.
- 오래된 작업의 Job/receipt/전체접수 marker도 없으면 그 작업을 승인된 접수로 추정하지 않는다. 동일문제 예외는 선택 비용 수용이지 채점/전체완료 증거의 면제가 아니다.
- 공개 조회/제출·Callback·채점 복구의 공통 reader 검사는 아래 후속으로 연결했다. 삭제/owner 연계의 전체 공동 시나리오, 실제 구writer drain/재개 방지, 실제 키/TLS/ALB/index 및 공동 경합 검증은 남아 있다. 이 연결만으로 전체 rollout 승인으로 보고하지 않는다.
- `PaidExamActivationGate`는 여전히 paid ON을 거절한다. 아래 설정만 배포해 켤 수 있는 상태가 아니다. 임의 flag OFF rollback으로 구writer를 재개해서도 안 된다.
- 직전 머지의 자동 테스트배포 첫 실행은 기존 LearningQuotaTransactionTest 동시성1건 실패로 AWS 적용 전 중단했다. 같은 커밋 실패 job 재실행 결과는 CURRENT_STATE 최신 항목을 따른다.

## 5. 파일·검증·계약

- 신규 `LegacyExamRuntime`, `LegacyExamRuntimeConfiguration`: 승인 목록의 불변 membership/operation 결속과 default-off 조립.
- `ExamCreationOperation`, `PaidReservationProtocol`, preparation/lifecycle: local-only legacy proof, 일반 INITIAL wire, confirm 후 exact closure.
- `ExamSession`, `ExamSubmissionEvidence`, `ExamAcceptedWorkPolicy`: 실제 접수 증거와 문제선정 fallback 분리.
- `PaidExamP1MongoTest`: legacy 무coverage/전체접수/완료/상태누락, same-key, 목록 밖·metadata 충돌.
- `PaidExamP3MongoTest`: confirm 전 원 시험 유지, 성공 원자 마무리, 취소 보존, 변조된 revision의 reserve 전 거절.
- `LegacyExamRuntimeConfigurationTest`, `ExamAcceptedWorkPolicyTest`: OFF 미파싱·환경불일치·기존작업/부분접수/revoke 우선.

공개 API/성공DTO/BaseResponse, AI user_id=examId/Callback, S3/Redis, Billing wire와 기존 fixture bytes는 불변이다. 사용자의 DS_Store는 보존·전달 제외했다. 예상 밖 변경은 없고 실DB/타저장소/기능설정은 변경하지 않았다.

최종 `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew clean test mongoIntegrationTest bootJar` 성공. 단위1,311개 실패/오류0·기존1skip, 격리Mongo281개 실패/오류/skip0, Node migration7개·diff check 통과. 기존fixture34개 hash 확인 및 새 브랜치의 fixture diff 없음. 개발 중 OFF 테스트의 Spring bean lifecycle 호출을 DB조회와 분리하고, 기존 Job의 생략 mock ID를 기존 기본값 규칙으로 비교하도록 수정한 뒤 전체 재검증했다.

## 6. 설정 및 근거

별도 승인값을 배포할 때 사용할 내부 property는 `app.billing.paid-exam.legacy.environment`, `.database`, `.revision`, `.sha256`, `.count`다. 실제 값은 이번에 설정하지 않았다. JWT environment는 기존 `app.billing.workload-jwt.environment`를 사용한다.

- [P6 준비·설치 전 절차](LC_BILLING_PAID_EXAM_P6_PREPARATION.md)
- [전체 구현 계획](LC_BILLING_PAID_EXAM_IMPLEMENTATION_PLAN.md)
- [삭제 후 INITIAL 연결](TMI_217_DELETED_INITIAL_PAID_ROUTING.md)
- PR #43: https://github.com/Too-Much-I/app-back-end-learning-core/pull/43
- 관련 TMI-116/128/217/193/136/125.

## 7. 2026-10-16 reader·채점 경계 후속

- `LegacyExamRuntime.requireKnown`은 exact 목록의 unlinked legacy 또는 Session 안에 저장된 strict paid snapshot만 인정한다. 신규 source 누락·ID/mock/kind/confirm 시각 불일치는 내부 검증 실패로 중단한다. 목록에 있는 시험에 paid metadata가 섞인 경우에도 허용하지 않는다.
- 신규 사용 허가와 분리된 **저장 근거 검사**다. 기존 owner/삭제/대기/환불/신규접수 차단과 기접수 policy는 유지한다. 정상 owner 이전 후 현재 userId와 최초 confirm userId가 다를 수 있으므로 같아야 한다는 새 제약은 추가하지 않는다.
- `ExamServiceImpl`의 소유 시험 조회 공통 경로 및 모든 Callback의 처리 판정, `ExamReadService`의 이력·문항 재시도 조회, `ExamGradingService`의 접수·기존 작업·Summary/owner Transaction 경계에 연결했다. 이력은 소유자/삭제 필터 이후 보이는 Session을 검증하며, 잘못된 자료를 조용히 누락해 정상 이력처럼 응답하지 않는다.
- 생성 operation repository를 추가 조회하지 않는다. 120일 보존 종료 뒤에도 Session의 immutable snapshot으로 검증한다. 격리 Mongo 테스트에서 operation 제거 후 실제 재조회된 기존/신규 Session 검증과 연결 변조 거절을 확인한다. 이 테스트의 제거는 production purge 구현/승인이 아니다.
- runtime bean은 기존 paid ON 조건에서만 존재한다. OFF 호환성 및 `PaidExamActivationGate`의 ON 거절은 유지한다. 소유권 이전/삭제 worker 자체의 동작이나 기존 Billing wire/외부 오류 코드를 바꾸지 않았다.
- 추가 검증: runtime 분류 7개, 공개/이력/제출/Callback/owner 우선 경계 6개, 영속 Mongo 1개. 전체 실행 결과는 CURRENT_STATE/WORKLOG 최신 항목을 따른다. 실제 TLS·공동 경합·실환경 검증과 활성화는 미실행이다.

## 8. 구writer 재개 차단 및 전체 완료 범위

`LegacyExamWriterGuard`는 feature flag와 무관하게 기존 `exam_paid_rollout_state`의 `legacy-v1` 존재를 읽는다. 상태가 설치됐으면 SEALED뿐 아니라 불완전/변조 상태도 기존 생성 중409/Retry-After1로 차단한다. 상태가 없으면 기존 생성 흐름을 유지하고 조회 실패는 허용으로 해석하지 않는다. 별도 컬렉션·TTL·state 수정/캐시는 없다. OFF에서도 구생성 요청에는 이 읽기 검사가 추가되며, legacy manifest 설정/키를 해석하지 않는다.

- `ExamSessionManager`: 이전 Session abandon·문제선정 이전에 차단, Billing/phone 구준비 경로도 방어.
- `BillingExamCreationSaga`: 새 operation/phone discovery 및 PREPARED reserve 전 차단. 이미 성공한 요청의 replay·기존 confirm/status/cancel 복구를 일괄 금지하지 않는다.
- `BillingExamCreationTransactionService`: PREPARED insert 및 새 Session commit의 Transaction 안에서 재확인. 이미 SESSION_COMMITTED/SUCCEEDED인 replay는 통과하며 기존 Session을 추가로 만들지 않는다.
- 새 paid preparation/lifecycle은 이 구writer guard를 사용하지 않는다. 기존 paid activation gate는 그대로 유지한다.

이는 **이번 코드를 배포한 인스턴스의 재개 방어**이며 전환 설치와 동시 실행을 원자적으로 순서화하는 lock이 아니다. guard 없는 구버전 바이너리·전환 state 수동삭제·backup rollback을 막았다고 주장하지 않는다. 설치 전에 모든 writer/진행 요청 drain, 인스턴스 버전 확인, 접근통제와 snapshot 재검증이 반드시 필요하다. 상태를 지워 구writer를 다시 여는 자동복구는 추가하지 않는다.

### 현재 완료와 별도 잔여

| 구분 | 현재 상태 |
| --- | --- |
| 승인한 LC 핵심 흐름 P1~P6.1 및 삭제sender/legacy reader | 코드·로컬/격리 회귀 구현. 공동 전체 완료와는 구분 |
| 구writer 재개 방어 | 이번 코드 구현·격리 검증; 실제 drain 증명 아님 |
| 실제 manifest 설치/봉인·배포 전환 도구/절차 | 설치/접근통제/drain 방식 확정 후 별도 작업; 자동 installer 없음 |
| 양측 TLS/권리/응답유실·owner/삭제/환불 공동 E2E | 별도 환경·양측 증거 필요. 로컬 테스트로 대체하지 않음 |
| 활성화 | 하드 기동차단 유지. 공동 gate 해소 후 해제 변경/배포 승인 필요 |
| 최종 자동 purge | 기존 제외 범위. 자료별 보존/삭제 gate 확정 전 구현·실행하지 않음 |

따라서 기능 코드의 후속 보완과 **전체 출시 준비 완료**는 동일하지 않다. 남은 배포 도구·차단 해제까지 이미 구현 완료라고 보고하지 않는다. 공개 계약/기존 fixture는 유지하며 변경 파일·최종 테스트 수치는 CURRENT_STATE와 WORKLOG의 최신 항목을 따른다.
