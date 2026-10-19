# LC 유료 시험 — test 한정 기동 준비

> 2026-10-16 전달 완료: 아래 구현 당시 미커밋/미배포 기록 이후2e24f10·PR #45를 develop f41745a로 머지했고 test 자동배포38895660977이 성공했다. 기능 설정은 변경하지 않았으며 실제 활성화·Billing 공동E2E는 미완료다. 최신 상태는 CURRENT_STATE를 따른다.

- 날짜: 2026-10-16 / 브랜치: `feat/billing-paid-exam-test-activation`
- 관련 TMI-116/TMI-128/TMI-217/TMI-125/TMI-220/TMI-193/TMI-136.

## 1. 5줄 결론

1. 선행 legacy 변경은 72fd00a로 commit/push하고 PR #44를 CI 성공 후 develop ef6d151에 머지했다.
2. 후속 브랜치에서 paid/revoke 기동차단의 **명시적 test 예외**를 구현했다. 기본OFF이며 staging/production 예외는 없다.
3. test 프로필·승인설정·환경 일치·검증된 legacy 목록·owner executor·필수 인덱스를 모두 요구한다.
4. test에서 기존에 생략/경고 처리하던 핵심 인덱스 검사도 이 예외에서는 읽기 전용으로 엄격하게 수행한다.
5. 실제 서버 환경변수/키/DB/목록 설치/활성화는 하지 않았다. 이번 새 브랜치 변경은 미커밋이다.

## 2. 반드시 읽어야 하는 내용

`PaidExamTestActivation`은 두 기동차단에서 공유하는 준비 검사다. 승인값 하나로 조건을 우회하지 않는다. 아래 **기동 허용 조건**은 실환경/공동 E2E 통과 증거가 아니다.

| 조건 | 규칙 |
| --- | --- |
| Spring active profile | 정확히 test 하나. 기본profile·local·staging·prod·production·혼합profile 거절 |
| 승인 | `app.billing.paid-exam.test-activation-approved=true` 명시 |
| 테스트 구성 | paid-exam·creation-saga·workload sender·access receiver·user-merged writer 모두 true |
| 환경 | workload JWT·legacy·access-events environment 모두 exact test |
| legacy | 기존 `LegacyExamRuntimeConfiguration`이 별도 승인 summary/DB와 sealed manifest를 검증해 만든 runtime 필수 |
| owner | 실제 `UserOwnedTransactionExecutor` bean이 있고 enabled여야 함 |
| 필수 index | saga operation/session, active Session/mock, owner guard/inbox/result/summary 기존 validator 통과 |

이번 test 예외는 paid와 revoke receiver를 함께 검증하는 구성만 허용한다. receiver 단독ON을 새로 허용하지 않는다. 기존 발신 W OFF/P ON drain 계약 자체를 변경하지 않으며 phone/reconciliation/outbox/삭제sender는 각 기능의 기존 설정/검증과 테스트 범위를 별도로 확인한다.

키 파싱·HTTPS origin·TLS connector/port·revoke indexes·reconciliation 설정 등의 기존 검증은 그대로다. 이번 클래스가 그 검증을 대체하거나 실제 Billing URL/인증 성공을 증명하지 않는다. primary 사용자 인증/Legacy 개발 모드·외부 API·AI user_id=examId·Billing JWT/wire는 변경하지 않았다.

## 3. 설정과 사용자 결정 사항

다음 환경변수 매핑만 application.yml에 추가했다. 실제 값을 설정하지 않았으며 secret을 기본값으로 넣지 않았다.

| 환경변수 | 내부 property | 기본 |
| --- | --- | --- |
| BILLING_PAID_EXAM_ENABLED | app.billing.paid-exam.enabled | false |
| BILLING_PAID_EXAM_TEST_ACTIVATION_APPROVED | app.billing.paid-exam.test-activation-approved | false |
| BILLING_PAID_EXAM_LEGACY_ENVIRONMENT | app.billing.paid-exam.legacy.environment | 빈 값 |
| BILLING_PAID_EXAM_LEGACY_DATABASE | app.billing.paid-exam.legacy.database | 빈 값 |
| BILLING_PAID_EXAM_LEGACY_REVISION | app.billing.paid-exam.legacy.revision | 빈 값 |
| BILLING_PAID_EXAM_LEGACY_SHA256 | app.billing.paid-exam.legacy.sha256 | 빈 값 |
| BILLING_PAID_EXAM_LEGACY_COUNT | app.billing.paid-exam.legacy.count | -1 |

이미 test 서버를 수동 요청으로만 사용한다는 운영 전제는 [간소화 runbook](LC_BILLING_PAID_EXAM_CUTOVER_RUNBOOK.md)을 따른다. 실제 대상/DB·현재 active profile·목록/키/TLS/index 준비·Billing 수신 버전 확인과 활성화 요청이 있어야 서버에 적용한다. 승인 property를 staging 설정에 복사해도 기동을 허용하지 않는다. profile/environment 문자열만으로 실제 AWS 계정을 식별하지는 않으므로 운영 대상 확인을 생략하지 않는다.

## 4. 위험·배포 전 잔여

- 자동 manifest export/install CLI는 이번에 만들지 않았다. 실제 목록/봉인·설치 수단과 별도 승인 summary가 여전히 필요하다. 임의DB 생성·과거 시험 수정·index 자동복구 없음.
- test 예외 조건 통과는 전체 Spring 웹 애플리케이션·Redis/S3/AI/Billing 실제 연결 성공을 뜻하지 않는다. real key/TLS/ingress·동시 finalize/delete/revoke·재시작/유실 공동E2E는 미실행이다.
- guard/목록을 삭제하거나 flag만OFF해 옛 생성 경로로 복귀하지 않는다. 원자료·미완료/권한 증거를 유지한다. 최종purge는 별도다.
- 자동 배포는 선행 PR #44까지만 포함한다. 현재 새 브랜치의 test 예외는 아직 배포되지 않았다. 배포 결과는 CURRENT_STATE 최신 관측을 따른다.

## 5. 구현 파일과 검증

- `PaidExamTestActivation`, `PaidExamTestIndexCheck`: test 조건과 read-only strict index 검사. OFF에서 준비 검사/DB조회/키 파싱 없음.
- `PaidExamActivationGate`, `AccessEventRolloutGate`: 준비 검사에 성공한 test만 예외 허용, 그 외 차단 유지. 기존 직접생성 테스트/의존성 없는 ON은 계속 실패한다.
- `ExamAssignmentIndexValidator`: 기존 검증의 공개 strict 호출 메서드만 추가. 일반 실행 동작은 불변.
- `application.yml`: 위 환경변수 매핑, 기본OFF.
- `PaidExamTestActivationTest`: 22개. OFF·정상 test·기본/혼합/비test profile·부분 flag·환경불일치·목록/owner 누락·index 실패.
- `PaidExamTestActivationMongoTest`: 3개. 실제 격리Mongo에서 sealed manifest/hash/index 검증 및 성공시 Session 무수정, missing owner index·wrong hash·missing saga index 거절. owner executor는 mock이며 HTTP/TLS·공동 E2E가 아니다.

전체 `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew clean test mongoIntegrationTest bootJar` 통과. 최종 XML 단위1,353 실패/오류0·기존1skip, 격리Mongo286 실패/오류/skip0. Node migration/deployment137개·기존fixture34 hash·diff 검사 통과. 테스트 과정의 DB/index 설치는 Testcontainers 격리DB에만 수행했다.

## 6. 전달·범위

- 선행 PR: https://github.com/Too-Much-I/app-back-end-learning-core/pull/44
- CI38892797996 성공, merge ef6d151bf041588d30233dc29ab9a97ea8d3530a.
- 실제 자동배포38893127747은 첫시도 Mongo의 기존 LearningQuotaTransactionTest 동시성1건 실패로 AWS 전 중단했고 동일commit 실패job만1회 재실행했다. 테스트를 제외하거나 해당 기능을 수정하지 않았다.
- 사용자 DS_Store 보존·커밋 제외. 이번 후속 코드에 예상 밖 범위 변경 없음. Billing/Identity/Jira·실DB/설정 변경·후속 commit/push 없음.
