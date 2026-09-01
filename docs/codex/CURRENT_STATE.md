# Learning Core Current State

## Last updated

- 2026-09-01

## Current branch

- `develop`
- 2026-09-01 Jira `TMI-118`의 backfill 의미를 설명했다. 이번 backfill은 AttemptGroup outbox 기능이 생기기 전에 생성된 기존 CONFIRMED Billing-linked ExamSession 중 projection 상태가 없는 데이터만 대상으로, 먼저 dry-run으로 현재 시험 증거를 판정하고 운영자가 승인한 Session에 `attemptGroupProjectionStatus=OPEN`, projection version 0을 초기화한 뒤 coordinator가 GRADING·COMPLETED·RETAKE_AVAILABLE 상태와 outbox event를 생성하도록 연결하는 일회성 데이터 이관이다. 신규 Session은 정상 writer 흐름이 자동 처리하므로 대상이 아니며, 현재 local profile Atlas에는 대상이 0개여서 실행할 backfill이 없다. 설명과 문서만 갱신했고 애플리케이션 코드·DB·외부 계약은 변경하지 않았다.
- 2026-09-01 Jira `TMI-118` 추가 작업 여부 최종 확인: local profile Atlas의 backfill 후보와 CONFIRMED Billing-linked Session이 모두 0개이므로 현재 연결 DB를 위한 추가 제품 코드, 세션별 allowlist와 backfill apply는 필요 없다. staging/production이 별도 DB라면 각 환경 inventory를 별도로 확인해야 하며, Billing TMI-117 consumer 배포, Mongo Transaction·index, Lattice IAM, publisher idle 선활성, writer canary와 상태·오류 E2E는 배포·운영 단계의 필수 잔여 작업이다. 이번 확인은 문서만 동기화했고 애플리케이션 코드·DB·AWS·Jira·Git 상태와 외부 계약을 변경하지 않았다. 직전 `./gradlew clean test` 439개 성공 상태를 유지하며 문서 diff는 `git diff --check`로 검증한다.
- 2026-09-01 Jira `TMI-118`의 추가 작업 필요 여부를 정리했다. 현재 local profile Atlas에는 CONFIRMED Billing-linked Session이 0개이므로 이 DB를 위한 backfill runner·allowlist·apply 추가 작업은 필요 없다. 다만 이 결과만으로 staging/production rollout이 끝난 것은 아니며, 별도 DB를 사용하면 각 환경 inventory count, Billing TMI-117 consumer 배포·활성, Mongo transaction/index, Lattice IAM, publisher 선활성 후 writer canary와 상태·오류 E2E가 남아 있다. 따라서 추가 애플리케이션 기능 개발은 현재 필수가 아니지만 배포·운영 검증은 필수다. 애플리케이션 코드는 변경하지 않았고 공개 계약과 직전 439개 테스트 성공 상태는 유지된다.
- 2026-09-01 종료 훅 동기화: Jira `TMI-118` backfill inventory를 `.env.docker.local` local profile의 Atlas 연결에서 읽기 전용으로 실행한 결과 후보 0개, CONFIRMED Billing-linked Session 전체 0개였다. 현재 연결 대상에는 backfill apply가 필요 없으며 DB 문서는 변경하지 않았다. staging/production이 별도 연결이면 writer 활성화 전 각 환경을 별도로 조회해야 한다. URI·credential·userId·Session ID와 Secret/Token은 출력·기록하지 않았고 애플리케이션 코드는 변경하지 않았다.
- 2026-09-01 사용자 승인으로 `.env.docker.local`의 local profile이 가리키는 Atlas DB에 TMI-118 backfill inventory를 읽기 전용 실행했다. Secret·URI·사용자·Session ID는 출력하지 않고 집계만 조회했으며 `CONFIRMED + nonblank attemptGroupId + projection null/missing` 후보는 0개였다. 추가 분해 조회에서도 CONFIRMED Billing-linked Session 전체가 0개여서 현재 이 연결 대상에는 backfill이 필요하지 않다. 이 결과는 local profile 연결에만 해당하며 staging/production이 별도 URI를 사용한다면 각 환경은 writer 활성화 전에 별도로 count 0을 확인해야 한다. DB 변경, backfill apply와 애플리케이션 코드는 수행·변경하지 않았고 공개 계약과 직전 439개 테스트 성공 상태는 유지된다.
- 2026-09-01 Jira `TMI-118` rollout 전 기존 backfill 후보 조회 시점을 설명했다. 지금 필요한 작업은 apply가 아니라 환경별 read-only inventory count다. `BILLING_CREATION_SAGA_ENABLED`가 한 번이라도 true였던 staging/prod에서는 writer 활성화 전에 `exam_sessions`의 `entitlementState=CONFIRMED`, nonblank attemptGroupId, projection null/missing 조건을 조회해야 한다. count 0이면 backfill 없이 신규 writer cutover만 진행하고, count가 있으면 writer 활성화 전 dry-run report·분류·canary batch 계획을 확정한다. Billing saga가 해당 환경에서 한 번도 활성화되지 않았다면 후보가 생길 수 없지만 count 0을 확인해 기록하는 것을 권장한다. 조회는 DB 상태를 변경하지 않으며 애플리케이션 코드는 변경하지 않았다. 공개 계약과 직전 439개 테스트 성공 상태는 유지된다.
- 2026-09-01 Jira `TMI-118` 기존 Session backfill은 운영자가 Mongo 문서를 하나씩 수동 확인하는 방식이 아님을 설명했다. 먼저 repository가 `CONFIRMED + attemptGroupId 존재 + projection null/missing` 후보 전체를 inventory하고, 후보 ID 집합을 batch dry-run해 결과를 `completed`, `gradingReady`, failureCode와 추가 조사 대상으로 분류한 뒤 승인된 그룹을 한 번에 allowlist apply하는 방식이 권장된다. 데이터가 적으면 개별 확인하고, 많으면 완전한 strict evidence 대상은 batch 승인하며 failure/integrity 후보만 개별 조사한다. 현재 내부 service는 후보 조회·명시적 ID 집합 dry-run/apply까지만 있고 보고서·CLI/admin runner는 없으므로 실제 기존 후보가 존재하면 TMI-118 staging rollout 전에 일회성 운영 runner 또는 command를 추가해야 한다. 기존 후보가 없으면 backfill 자체를 실행하지 않는다. 애플리케이션 코드는 변경하지 않았고 공개 계약과 직전 439개 테스트 성공 상태는 유지된다.
- 2026-09-01 Jira `TMI-118`의 기존 Billing-linked Session allowlist backfill을 사용자에게 설명했다. writer 활성화 전에 만들어져 `attemptGroupProjectionStatus`가 없는 CONFIRMED Session은 reconciler가 자동 처리하지 않는다. 운영자가 명시한 Session ID 집합만 `AttemptGroupBackfillService.dryRun`으로 현재 evidence 기준 GRADING 준비·COMPLETED·failureCode 후보를 읽기 전용 확인하고, 승인 후 `apply`가 Transaction에서 projection을 OPEN/version 0으로 초기화한 뒤 coordinator를 실행해 상태+outbox를 생성한다. 이는 legacy Summary·feedback 불완전성으로 잘못 COMPLETED/RETAKE 처리하거나 대량 event를 한꺼번에 전송하는 위험을 줄인다. 현재 서비스는 공개·admin API나 자동 runner가 없는 내부 실행 경계이므로 실제 staging backfill 실행 방법과 allowlist는 별도 운영 절차로 확정해야 한다. 애플리케이션 코드는 변경하지 않았고 공개 계약과 직전 전체 439개 테스트 성공 상태는 그대로다.
- 2026-09-01 Jira `TMI-118`의 `AttemptGroupOutboxStore`가 Mongo `findAndModify`와 CAS로 수행하는 작업을 사용자에게 설명했다. `findAndModify`는 PENDING 또는 lease 만료 IN_FLIGHT event 하나를 찾는 것과 새 leaseToken·owner·until·attemptCount를 기록하는 것을 단일 원자 연산으로 묶어 여러 ECS Task의 동시 claim을 막는다. 이후 성공, retry, DEAD_LETTER와 BLOCKED_AUTH 갱신은 `_id + status=IN_FLIGHT + leaseToken` 조건의 update로 처리해 현재 처리권을 보유한 worker만 상태를 바꾸도록 한다. 만료 lease는 다른 worker가 회수하며, 잘못된 trace context도 동일 leaseToken CAS를 통과한 worker가 한 번만 fallback context로 교체한다. 애플리케이션 코드는 변경하지 않았고 공개 계약과 직전 439개 전체 테스트 성공 상태는 유지된다.
- 2026-09-01 Jira `TMI-118` publisher의 lease 의미를 사용자에게 설명했다. lease는 여러 Learning Core ECS Task 중 한 Task가 outbox event를 일정 시간 독점 처리하도록 Mongo에 `leaseOwner`, random `leaseToken`, `leaseUntil`을 기록하는 시간제 처리권이다. 정상 완료 시 동일 token 보유자만 DELIVERED/retry/dead-letter/auth 상태를 갱신하고, Task가 중간 종료되면 30초 기본 lease 만료 뒤 다른 Task가 event를 회수한다. 따라서 영구 lock 없이 동시 중복 처리를 줄이고 장애 복구가 가능하며, 아주 드문 HTTP 성공 후 local 갱신 실패에 따른 재전송은 Billing의 eventId/digest 멱등성이 최종 방어한다. 애플리케이션 코드는 변경하지 않았고 공개 계약과 직전 전체 439개 테스트 성공 상태는 그대로다.
- 2026-09-01 Jira `TMI-118` 구현 파일을 역할별로 재검토하고 사용자 설명용 구조를 정리했다. 핵심 흐름은 `ExamServiceImpl` submit/Callback trigger → `AttemptGroupEvidenceEvaluator` evidence 판정 → `AttemptGroupStateCoordinator`의 Session+outbox Transaction → `AttemptGroupOutboxPublisher` lease claim → W3C publish span/traceparent → `SigV4AttemptGroupEventClient`의 Billing 전송이다. 상태·payload·outbox domain, coordinator/reconciler/Summary transaction/backfill application, Mongo lease/auth circuit/index/config/trace/SigV4 infrastructure, RETAKE replacement를 위한 기존 Exam/Billing 수정 파일과 테스트 책임을 대조했다. 애플리케이션 코드는 변경하지 않았고 공개 API·AI·S3·Redis 계약과 이전 `./gradlew clean test` 439개 성공 상태는 그대로다. 이번 설명 작업에서는 문서만 갱신했으며 신규 테스트는 실행하지 않았다.
- 2026-09-01 Jira `TMI-118` Learning Core AttemptGroup durable outbox/publisher를 로컬 구현했다. Billing-linked·CONFIRMED Session만 writer가 관리하며 필수 retry 0 제출 시 GRADING, strict `exam_summaries` evidence 시 COMPLETED, retry 소진·정합성 위반·PT30M deadline 시 고정 failureCode의 RETAKE_AVAILABLE로 전이한다. Session projection+outbox는 Mongo Transaction/optimistic CAS와 Session별 GRADING/TERMINAL unique slot으로 묶었다. Summary Callback의 Summary insert·Job 완료·terminal+outbox도 writer 대상에서 같은 Transaction으로 수렴한다. lease publisher는 같은 eventId/payload를 유지하고 재시도별 새 W3C CLIENT span·traceparent 주입 뒤 SigV4 `vpc-lattice-svcs` 서명, HTTP 분류, DELIVERED 30일·DEAD_LETTER 90일, 401/403 BLOCKED_AUTH 전역 circuit·15분 단일 probe를 구현했다. RETAKE_AVAILABLE Session은 다음 시험 생성 operation에 source Session/group/mockExam을 snapshot해 exact Billing REPLACEMENT만 허용한다. writer/publisher 기본값은 off이며 기존 linked Session은 자동 backfill하지 않고 명시적 allowlist dry-run/apply만 제공한다. 공개 API·BaseResponse·AI·S3·Redis 계약은 변경하지 않았고 `./gradlew clean test` 439개가 성공했다. staging에서는 Billing consumer image/flag, Mongo replica-set·index, Lattice IAM과 INITIAL/REPLACEMENT·401/403·timeout failure-injection E2E가 남아 있으므로 아직 production 활성화 상태가 아니다.
- 2026-08-31 세 앱 서버 현재 `develop`과 최근 Jira·테스트 기록을 기준으로 `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`를 최신화했다. Identity `TMI-114` 가입 중단 Firebase cleanup은 PR #36·600 tests·Jira 완료, Billing `TMI-115` BenefitDefinition과 `TMI-117` AttemptGroup consumer는 각각 PR #3·#4에 병합됐고 `TMI-117`은 137 tests·Jira 완료다. Learning Core `TMI-116` Billing Reservation 시험 생성 saga는 PR #24로 병합되고 P1/P2 보완 후 전체 432 tests가 성공했지만 feature flag 기본 off이며 Jira 상태 전환, 실제 Mongo migration·Lattice/IAM/SG·staging E2E가 남아 있다. 무료시험의 가장 큰 코드 공백은 Learning Core AttemptGroup durable outbox/publisher와 Billing owner rebind이고 Challenge backend도 미구현이므로 production release는 계속 차단한다. 애플리케이션·Jira·외부 계약은 이번 점검에서 변경하지 않았다.
- 2026-08-31 앱 문제 응답의 Part 4 표 처리 현황을 분석했다. MongoDB `table_context`는 `Map<String,Object>`로 읽어 시험 생성·문항 prompt·문항 결과 응답의 `tableContext` JSON에 가공 없이 전달하며 서버가 고정 schema, HTML 또는 Markdown으로 렌더링하지 않는다. `table_image_url`은 내부 entity에만 남고 공개 응답에서 제외된다. Part 4 tableContext가 null이면 catalog configuration error이며 빈 object는 허용한다. AI 채점 요청에는 table_context와 table_image_url을 보내지 않는다. 신규 Jira 키는 없다.
- 2026-08-28 종료 훅 동기화: 현재 저장소 기준 1차 업데이트 체크리스트 작성·검증 결과를 현재 turn 기록으로 WORKLOG 끝에 추가했다. Identity `TMI-109`·`TMI-111`과 Billing `TMI-110`·`TMI-112`·`TMI-113` 완료, Learning Core Billing saga·Challenge backend·모바일/workload/staging E2E 잔여 판정은 동일하다. 애플리케이션·Jira·외부 계약은 변경하지 않았다.
- 2026-08-28 현재 저장소 기준 1차 업데이트 진행 상태를 `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`로 다시 정리했다. Identity `TMI-109`·`TMI-111`과 Billing `TMI-110`·`TMI-112`·`TMI-113`은 구현·병합 기록에 따라 완료로 반영했다. Billing에는 TrialClaim, `FREE_EXAM_ONCE` grant/ledger와 Reservation reserve/confirm/cancel/status/expiry 기반이 있으나 Learning Core Billing client·필수 `Idempotency-Key`·reserve/commit/confirm saga는 아직 없다. Challenge는 프론트·AI v1 계약과 콘텐츠가 준비됐지만 Learning Core backend·AI 양방향 구현은 미착수다. 따라서 실제 모바일 SNS, workload/Lattice, replica set·multi-instance, 무료시험·Challenge staging E2E와 canary가 끝나기 전 production 출시는 차단한다. 신규 Jira와 애플리케이션 코드는 변경하지 않았다.
- 2026-08-28 전체 프론트 API 인계서를 Identity·Learning Core·Billing의 모든 `@RestController`와 Security 설정에 다시 대조했다. Identity 앱 API 17개와 Learning Core 앱 API 11개는 누락 없이 유지된다. Billing은 공개 앱 API가 0개지만 `TMI-110` eligibility consumer, `TMI-112` TrialClaim·FREE_EXAM_ONCE initial reserve, `TMI-113` confirm/cancel/status·expiry lifecycle이 구현된 상태여서 기존 “Reservation 미구현” 설명을 정정했다. Learning Core Billing saga·필수 `Idempotency-Key`, AttemptGroup event·owner rebind·Lattice staging E2E는 여전히 남아 있다. Billing 내부 Reservation endpoint 4개를 프론트 호출 금지 표에 추가하고 Challenge는 `TMI-102`·`TMI-105`·`TMI-106` 관련 승인된 v1 계약·API 미구현 상태로 통일했다. 기존 시험 upload URL의 5분 signature/`expiresIn=60` 불일치와 `.wav` key 대비 Content-Type·codec 미고정 위험을 명시했다. 애플리케이션·Jira는 변경하지 않았다.
- 2026-08-28 월 서버 고정비 300,000원과 사용자가 제공한 AI 실측 합계 275.28원/모의고사로 무제한 이용권 BEP를 재계산했다. 단기권 집중 사용 4·8·14·21·28회, VAT 10%와 IAP 15% 기준 상품 단독 BEP는 24시간 52건, 3일 25건, 7일 17건, 2주 10건, 4주 7건이다. IAP 30% 민감도는 65·31·21·12·9건이며 무료 시험·추천·쿠폰·환불·광고비는 제외한다. 신규 Jira 키는 없다.
- 2026-08-28 종료 훅 동기화: 고정비 300,000원·AI 실측 275.28원 기준 BEP 결과와 검증 상태를 WORKLOG 끝에 기록했다. 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았으며 신규 Jira 키는 없다.
- 2026-08-28 서버 비용 최종 관리 기준을 월 300,000원으로 표로 고정했다. Production AWS 전체 비용 242,200원, staging 테스트 24,200원, 환율·청구 지연·Task 중복·로그·전송량 변동 대응 33,600원이며 완료 모의고사당 AI API 250원은 별도 변동비다. 신규 Jira 키는 없다.
- 2026-08-28 종료 훅 동기화: 월 300,000원 서버 예산표와 검증 상태를 WORKLOG 끝에 기록했다. 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았으며 신규 Jira 키는 없다.
- 2026-08-28 사용자가 다른 AWS 비용을 모두 포함한 실제 전체 비용을 `$1.26/day`가 아니라 `$5.49/day`로 재정정했다. 조정 사양의 compute 감소율 6.46%와 고정비 유지 조건에서 production 전체 비용은 `$5.135~5.490/day`, `$154.06~164.70/30일`, 환율 1,400원·VAT 포함 약 237,300~253,600원이다. compute 비중 70% 기준은 약 242,200원이며 staging 10% 24,200원과 변동 완충액 33,600원을 합쳐 월 운영 예산을 300,000원으로 수정했다. 과거 월 7만원 예산은 폐기하며 신규 Jira 키는 없다.
- 2026-08-28 조정 사양의 월 7만원 운영 예산을 표로 분해했다. Production 기준 예상액 약 55,600원, staging 테스트 여유 약 5,600원, 환율·청구 지연·사용량 변동 완충액 약 8,800원으로 합계 70,000원이다. 완충액은 확정 서비스 비용이 아니라 변동 흡수용 예산이며 신규 Jira 키는 없다.
- 2026-08-28 사용자가 `$1.26/day`가 Fargate뿐 아니라 다른 AWS 항목을 모두 포함한 전체 비용이라고 확정했다. 따라서 ALB·NAT·로그 등을 별도 가산한 월 19만~29만원 시나리오는 폐기한다. 조정 사양의 compute 정상 단가 감소율은 6.46%지만 고정 인프라는 줄지 않으므로 전체 비용은 이보다 적게 감소한다. compute 비중 0~100% 경계에서 조정 후 전체 비용은 `$1.179~1.260/day`, `$35.36~37.80/30일`, 환율 1,400원·VAT 포함 약 54,500~58,200원이다. 운영 예산은 청구 지연·환율·staging 테스트 여유를 포함해 월 7만원으로 유지한다. 신규 Jira 키는 없다.
- 2026-08-28 조정 사양 기준 서버비를 관측 `$1.26/day`의 범위에 따라 재계산했다. `$1.26`이 AWS 전체 비용이면 production 약 `$35.36/월`, staging 10% 여유 포함 VAT 기준 약 6만원이며 운영 예산은 월 7만원이다. `$1.26`이 Fargate compute만이면 Mongo 무료·예비비 0·Valkey 최소 가정에서 staging 네트워크 시간제 생성 시 약 19만원, staging ALB·NAT 상시 유지 시 약 29만원이다. Cost Explorer에서 필터 없는 Service별 합계로 어느 시나리오인지 확인해야 하며 신규 Jira 키는 없다.
- 2026-08-28 종료 훅 동기화: 조정 사양 전체 서버비 재계산 결과와 검증 상태를 WORKLOG 끝에 기록했다. 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았으며 신규 Jira 키는 없다.
- 2026-08-28 사용자가 조직 계정에서 실제 비용이 `$1.26/day`로 정확히 표시된다고 확인했다. 향후 Identity `1 vCPU/2GB`, Learning Core `1 vCPU/2GB`, Billing `1 vCPU/1GB`, AI `1 vCPU/2GB`로 조정하면 서울 정상 단가 자원비가 현재 구성의 93.54%가 된다. 동일한 조직 정산 효과 유지 가정에서 약 `$1.179/day`, `$35.36/30일`, 환율 1,400원 기준 VAT 포함 약 54,500원이다. 혜택이 사라진 정상 단가 compute는 `$162.07/month`, 환율·VAT 적용 약 249,600원이며 네트워크·로그는 별도다. AWS Organizations 자체는 자동 할인 근거가 아니므로 credit·Savings Plans·private pricing·cost type을 확인해야 한다. 신규 Jira 키는 없다.
- 2026-08-28 종료 훅 동기화: 조직 계정 실제 단가 기반 4서비스 축소 비용 추정과 검증 상태를 WORKLOG 끝에 기록했다. 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았으며 신규 Jira 키는 없다.
- 2026-08-28 무제한 이용권 BEP의 사용량 가정을 단기권 집중 사용 패턴으로 보정했다. 기준 평균 응시는 24시간 4회, 3일 8회, 7일 14회, 2주 21회, 4주 28회다. VAT 10%와 IAP 15%, 월 고정비 380,000원, 완료 시험당 AI 250원 기준 BEP는 각각 64건·30건·21건·12건·9건이다. 24시간권 3~5회 시 62~67건, 3일권 6~10회 시 29~32건이며 출시 후 실제 cohort의 completed exam 평균·p95로 교체해야 한다. 신규 Jira 키는 없다.
- 2026-08-28 종료 훅 동기화: 단기권 집중 사용 BEP 보정 결과와 검증 상태를 WORKLOG 끝에 기록했다. 애플리케이션·AWS·Jira·외부 계약은 변경하지 않았으며 신규 Jira 키는 없다.
- 2026-08-28 사용자가 production 관측 비용을 `$12.6/day`가 아니라 `$1.26`으로 재정정했다. `$1.26×30=$37.80`, 환율 1,400원 기준 VAT 전 약 52,920원이지만, Identity `1 vCPU/3GB`, Learning Core `1 vCPU/3GB`, AI `2 vCPU/4GB` Task가 각 1개씩 24시간 실행되면 Fargate compute만 `$5.696/day`, `$173.26/month`이므로 `$1.26`은 완전한 하루 총비용과 양립하지 않는다. 당일 부분 누적·필터·간헐 실행·credit/net cost 여부를 확인하기 전에는 월 고정비로 사용하지 않는다. 신규 Jira 키는 없다.
- 2026-08-28 사용자가 제공한 토스트 앱 화면에서 무제한 멤버십 가격을 24시간 9,000원, 3일 19,000원, 7일 29,000원, 2주 49,000원, 4주 69,000원으로 확인했다. 월 고정비 380,000원, 모의고사 완료 1회당 AI 변동비 250원, 구매자당 하루 평균 1회 응시, 한국 표시가격 VAT 10%와 IAP 15% 차감 가정에서 상품 단독 판매 월 BEP는 각각 57건·28건·19건·12건·9건이다. IAP 30%이면 70건·34건·23건·14건·11건이다. 사용자가 말한 한 달은 첨부 화면상 4주(28일)로 계산했으며 실제 혼합 판매 BEP는 상품별 판매수×공헌이익 합계가 38만원 이상인 지점이다. 무료시험·추천·쿠폰·환불·광고비는 제외했고 실제 구매자당 시험 수와 Apple/Google 15% 적용 자격을 확인해야 한다. 상세 근거는 `docs/codex/SUBSCRIPTION_BEP_ESTIMATE.md`에 기록했으며 신규 Jira 키는 없다.
- 2026-08-28 사용자가 비용 산정에서 AI Task를 `2 vCPU/4GB`에서 `1 vCPU/2GB`로 낮추고 MongoDB 비용과 기타 예비비를 `$0`으로 정했다. 서울 Fargate 단가 기준 AI production 24시간 비용은 `$41.45/month`, Identity·Learning Core·Billing을 포함한 production Fargate는 `$93.26`, 동일 크기 staging 월 40시간은 `$5.11`이다. staging ALB·NAT도 테스트 때만 IaC로 생성·제거하면 Valkey 두 환경 최소 `$12`를 포함한 전체가 약 `$184.14`, 환율 1,400원과 VAT 10% 가정 약 28.4만원으로 운영 예산은 월 29만~30만원이다. staging ECS만 끄고 ALB·NAT를 유지하면 약 `$249.83`, 약 38.5만원으로 월 39만~40만원이다. 수정안 예상 범위는 월 29만~40만원이다. 현재 실제 AI Task Definition은 `2 vCPU/4GB`이고 API+worker 4개가 함께 있으므로 `1 vCPU/2GB` 적용 전 staging CPU throttling·peak RSS/OOM·queue backlog·p95 부하 검증이 필요하다. 외부 AI provider 호출료는 제외하며 신규 Jira 키는 없다.
- 2026-08-28 종료 훅 동기화: production 24시간·staging 테스트 시 운영 비용 재산정 결과는 동일하다. staging 월 40시간 기준 ALB·NAT까지 필요시에만 IaC로 생성하면 약 `$317.80/month`, 환율 1,400원과 VAT 10% 가정 약 49만원이고, staging ECS만 끈 채 ALB·NAT를 유지하면 약 `$383.49/month`, 약 59만원이다. 현실적인 안전 예산은 월 50만~60만원이며 신규 Jira 키는 없다. 애플리케이션·인프라와 외부 계약은 변경하지 않았다.
- 2026-08-28 사용자가 production만 24시간 운영하고 staging은 테스트할 때만 사용한다고 확정해 월 비용을 재산정했다. staging 월 40시간을 가정하면 네 서비스의 staging Fargate는 약 `$7.38`이고 production Fargate는 `$134.71`이다. staging ALB·NAT까지 테스트 때 IaC로 생성·제거하면 전체 약 `$317.80/month`, 환율 1,400원과 VAT 10% 가정 약 49만원으로 안전 예산은 월 50만~55만원이다. staging Task만 0으로 내리고 ALB·NAT·Atlas Flex·Valkey를 유지하면 약 `$383.49/month`, 약 59만원이므로 현실적인 예산 범위는 월 50만~60만원이다. ALB와 NAT는 ECS Task를 꺼도 삭제하지 않으면 계속 과금되며, staging 네트워크의 자동 생성·삭제는 IaC와 데이터 초기화가 전제다. 상세 계산은 `docs/codex/MONTHLY_INFRA_COST_ESTIMATE.md`에 반영했고 신규 Jira 키는 없다.
- 2026-08-28 staging+production 월 인프라 비용을 서울 리전 공식 단가로 추정했다. 확인 단가는 Fargate Linux/x86 `vCPU $0.04656/hour`, memory `$0.00511/GB-hour`, ALB `$0.0225/hour + $0.008/LCU-hour`, public IPv4 `$0.005/address-hour`, NAT Gateway `$0.059/hour + $0.059/GB`, Atlas M10 시작 `$56.94/month`, Atlas Flex 최저 `$8/month`, ElastiCache Serverless for Valkey 시작 `$6/month`이다. 확인된 AI Task `2 vCPU/4GB`와 Identity/Learning Core `0.5 vCPU/1GB`, Billing `0.25 vCPU/0.5GB` 가정으로 환경별 Task 한 개를 24시간 운영하면 기준 합계는 약 `$515.82/month`, 환율 1,400원과 VAT 10% 가정 약 79만원이며 안전 예산은 월 80만~90만원이다. staging 시간제 운영·NAT 대체 시 약 59만원, production 2 Task/AZ·Atlas M30의 보수적 HA안은 약 164만원이다. 외부 AI/provider 호출료, IAP 수수료, Atlas backup/egress와 대량 S3/로그는 제외했다. 상세 근거는 `docs/codex/MONTHLY_INFRA_COST_ESTIMATE.md`에 기록했다. 신규 Jira 키는 없다.
- 2026-08-28 사용자가 제공한 실제 Mongo document에 맞춰 10초 챌린지 계획·계약·프론트 인계 문서를 갱신했다. 콘텐츠는 Learning Core가 이미 사용하는 `to-teacher-app` cluster의 `challenge_10s_questions` collection에 `dayNumber`별 정확히 세 문제로 저장된다. `questions[].korean → promptKo`, `referenceAnswer → 제출 또는 만료 terminal 이후 공개`로 매핑하고 `_id`, `dayNumber`, `questionId`, `difficulty`는 프론트 비노출로 고정했다. 프론트 명세는 Draft v0.8로 올리고 실제 day 1 문항 예시를 반영했다. 계획에는 기존 cluster/connection 재사용, dayNumber unique와 questionId 중복 검증, catalog fail-closed, published content append-only, attempt 문제 snapshot을 추가했다. 남은 필수 결정은 `contentBaseDate`와 dayNumber=1 대응 KST 날짜, 콘텐츠 소진 후 순환 여부, difficulty scale이다. 관련 기존 Jira는 TMI-102·TMI-105·TMI-106이며 Learning Core Challenge backend 구현 Jira는 아직 없다. 애플리케이션과 Jira는 변경하지 않았다.
- 2026-08-28 Jira `TMI-109`·`TMI-111`의 UserWithdrawn workload JWT 계약안을 현재 Learning Core와 Identity 코드에 대조 검토했다. RS256, 별도 workload issuer, 전용 audience `learning-core-user-withdrawn`, Identity 기존 RSA/JWKS 재사용, PT2M TTL·PT30S skew, 내부 로컬 발급·요청별 새 token·HTTPS/no-redirect 방향은 타당하다. Learning Core는 현재 RS256, issuer, audience 포함 여부, 설정된 단일 principal claim/value, timestamp, `exp-iat` 최대 수명을 검증한다. 따라서 제안의 `service=identity`만 실제 principal allowlist이고 `sub=identity-service`, `jti`, `kid 필수`는 현재 별도 validator로 강제되지 않음을 계약에 명시해야 한다. 권장안은 principal을 표준 `sub=identity-service` 하나로 통일해 `principal-claim=sub`으로 설정하거나, `service`를 유지한다면 `sub`까지 두 validator로 모두 강제하는 것이다. 미래 `iat`를 막기 위해 `nbf=iat`을 필수 claim으로 추가하거나 별도 future-iat validator가 필요하다. Identity는 기존 RS256 JwtEncoder와 단일 RSAKey JWKS를 제공하지만 workload credential provider 구현체는 아직 없고 JWKS 다중 키 rotation도 미지원이므로 TMI-111과 production activation 전에 구현·E2E가 필요하다. 코드·Jira는 변경하지 않았다.
- 2026-08-28 프론트가 Identity·Learning Core와 1차 업데이트 예정 API를 한곳에서 확인할 수 있도록 `docs/contracts/FRONTEND_API_HANDOFF.md`를 추가했다. 현재 구현된 Identity 17개와 Learning Core 11개 앱 API를 공개/Bearer 인증으로 구분하고 요청·응답·상태·S3 upload/polling 흐름을 정리했다. 무료 모의고사 1회·결제 권한과 10초 챌린지는 구현 API와 섞지 않고 계획/Draft로 표시했으며 AI callback, withdrawal·eligibility workload endpoint와 JWKS는 프론트 호출 금지로 분리했다. TMI-102·TMI-105·TMI-106·TMI-109·TMI-110·TMI-111 관련 현재 경계를 반영했으며 애플리케이션과 Jira는 변경하지 않았다. 현재 Learning Core upload URL의 실제 5분 signature와 응답 `expiresIn=60` 불일치가 프론트 연동 주의점으로 남아 있다.
- 2026-08-28 종료 훅 요구에 따라 10초 챌린지 attempt 제출 유효시간 1시간과 Draft v0.7 갱신 결과를 현재 turn marker로 재동기화했다. attempt deadline은 생성 시각+1시간이고 Presigned URL은 짧게 발급해 deadline 전 같은 key로 재발급한다. 계약 문서만 변경했으며 애플리케이션·Jira는 변경하지 않았고 신규 Jira 키는 없다.
- 2026-08-28 사용자 확정에 따라 10초 챌린지 attempt 제출 유효시간을 생성 시점부터 5분에서 1시간으로 변경하고 프론트 명세를 Draft v0.7로 갱신했다. `submissionDeadlineAt=attemptCreatedAt+1시간`이며 23:59:50 KST에 생성한 attempt는 00:59:50까지 원래 challengeDate로 upload-url 발급·S3 업로드·answer 제출이 가능하다. Presigned URL 자체는 예시 기준 5분처럼 짧게 유지하고 attempt deadline 전 같은 object key로 재발급한다. 1시간 만료의 공개 `submitted` projection·history 풀이 수·참고 답안 정책은 기존 만료 규칙을 그대로 유지한다. 프론트 계약, 상태 결정서와 출시 계획을 동기화했고 애플리케이션·Jira는 변경하지 않았다. 신규 Jira 키는 없다.
- 2026-08-28 사용자 요청에 따라 현재 10초 챌린지 프론트 계약 문서 `docs/contracts/ten-second-challenge-frontend-api.md`의 위치와 버전을 확인했다. 문서는 Draft v0.6이며 녹음 시작 attempt 생성과 녹음 후 `POST /api/v1/challenges/attempts/{attemptId}/upload-url` 발급 분리, 자정 rollover 보호를 반영한 구현 전 합의용 명세다. 문서 내용·애플리케이션·Jira는 변경하지 않았고 신규 Jira 키는 없다.
- 2026-08-28 종료 훅 요구에 따라 10초 챌린지 Draft v0.6의 attempt·S3 upload-url 분리 계약 확정 기록을 현재 turn marker로 재동기화했다. `POST /today/questions/{questionNumber}/attempt`는 녹음 시작 시 날짜·deadline·내부 object key를 고정하고, 녹음 후 `POST /attempts/{attemptId}/upload-url`이 동일 key의 Presigned URL을 발급·재발급한다. 자정 후에도 기존 attempt의 저장된 날짜와 deadline을 사용한다. 애플리케이션 구현·Jira 변경은 수행하지 않았고 신규 Jira 키는 없다.
- 2026-08-28 사용자의 승인에 따라 10초 챌린지 attempt 시작과 S3 Presigned URL 발급 분리안을 구현 기준 Draft 계약으로 반영했다. 프론트 명세를 Draft v0.6으로 올리고 호출 순서를 `문제 조회 → 녹음 시작 attempt 생성 → 최대 10초 녹음 → POST /api/v1/challenges/attempts/{attemptId}/upload-url → S3 PUT → answer 제출`로 고정했다. attempt 응답에서는 upload 객체를 제거하고 `attemptId`, `challengeDate`, `questionNumber`, `submissionDeadlineAt`만 반환한다. S3 object key는 attempt 생성 시 attemptId 기반으로 내부 고정하며, upload-url은 소유권·상태·deadline을 검증하고 동일 key에 대해서만 재발급한다. 자정 이후에도 기존 attempt의 저장된 challengeDate와 deadline을 사용한다. 결정서와 1차 출시 계획도 같은 내용으로 동기화했다. Challenge API는 아직 구현·배포되지 않아 애플리케이션 코드는 변경하지 않았고 신규 Jira 키는 없다.
- 2026-08-28 10초 챌린지의 attempt 생성과 S3 Presigned URL 발급을 분리하는 계약 대안을 검토했다. 권장 흐름은 `문제 조회 → 녹음 시작 직전 attempt 생성 → 최대 10초 녹음 → attemptId로 upload-url 발급 → S3 PUT → answer 제출`이다. attempt의 server-side `createdAt`, `challengeDate`, `submissionDeadlineAt`이 자정 경계의 authoritative start가 되므로 별도 임시 session이 필요 없고, 자정 전 생성된 attempt는 deadline까지 자정 이후에도 같은 날짜 문제로 URL 발급·제출할 수 있다. object key는 attemptId 기반으로 attempt 생성 시 결정해 저장하거나 결정적으로 계산하고, upload-url 재발급은 동일 key에 대해 멱등 처리한다. 내부 상태는 CREATED → UPLOAD_READY/UPLOADING → SUBMITTED 또는 EXPIRED로 관리하되 공개 `attemptStatus`는 기존 Draft처럼 제출 전 `not_started`, terminal 후 `submitted` projection을 유지할 수 있다. 다만 attempt 생성 즉시 문제당 1회를 점유하므로 사용자가 녹음을 취소하거나 앱을 종료했을 때 deadline 후 EXPIRED 처리와 참고 답안·풀이 수 정책을 제품적으로 확정해야 한다. 이 대안은 프론트 호출 순서와 draft challenge API를 변경하지만 아직 배포된 API가 아니며, 이번 작업에서는 코드·계약 문서·Jira를 변경하지 않았다. 신규 Jira 키는 없다.
- 2026-08-28 10초 챌린지의 backend-only rollover를 임시 recording session 기반으로 구체화했다. 서버가 문제 조회 또는 기존 녹음 직전 요청에서 `ChallengeRecordingSession(userId, challengeDate, questionNumber, startedAt, expiresAt)`을 server clock으로 생성하고, 자정 후 attempt 생성 시 session의 KST `startedAt` 날짜가 요청 `challengeDate`와 같고 session이 유효한 경우에만 이전 날짜 attempt를 허용하는 방식이 더 안전하다. client가 보낸 시작 시각은 조작 가능하므로 근거로 사용하지 않는다. attempt와 session consume은 Mongo 단일 Transaction으로 처리하고 `(userId, challengeDate, questionNumber)` attempt unique를 유지하며 TTL 삭제 지연과 무관하게 `expiresAt`을 직접 비교해야 한다. 제출 deadline은 늦은 attempt 생성 시각이 아니라 `session.startedAt + 허용시간`을 기준으로 해야 자정 후 유효시간이 부당하게 연장되지 않는다. 프론트 변경 없이 문제 GET에서 session을 만들면 실제 녹음 시작이 아니라 문제 조회 시각이라는 한계가 있고, 정확한 녹음 시작이 필요하면 녹음 직전 start 호출이라는 최소 프론트 변경이 필요하다. 코드·계약·Jira는 변경하지 않았으며 신규 Jira 키는 없다.
- 2026-08-28 종료 훅 요구에 따라 10초 챌린지 backend-only 자정 rollover 검토 기록을 현재 turn marker로 재동기화했다. 결론은 동일하다. 프론트 attempt 요청이 기존 `X-Challenge-Date`를 보내면 서버는 자정 후 제한된 creation grace와 server-side question view/recording lease를 사용해 이전 날짜 attempt 생성을 허용할 수 있다. 요청 날짜로 ChallengeDefinition·unique key를 고정하고 creation grace와 제출 deadline을 분리해야 한다. 날짜 식별자가 전혀 없으면 정확한 backend-only 해결은 불가능하다. 코드·공개 계약·Jira는 변경하지 않았고 신규 Jira 키는 없다.
- 2026-08-28 10초 챌린지에서 프론트의 `녹음 → attempt 생성 → 업로드/제출` 순서를 유지하는 backend-only rollover 대안을 검토했다. attempt 요청이 기존 계약대로 캐시된 `X-Challenge-Date`를 보내면, 서버는 현재 KST 날짜와 무조건 같아야 한다는 규칙 대신 요청 날짜가 오늘이거나 직전 날짜이고 자정 후 제한된 creation grace 안인 경우를 허용할 수 있다. 늦게 생성한 attempt도 요청 날짜의 ChallengeDefinition과 `(userId, challengeDate, questionNumber)` unique key에 귀속하고 기존 순차 진행·1회 제한을 검증한다. 권장 안전장치는 question 조회 시 사용자·날짜·문항별 짧은 server-side view/recording lease를 남기고, 직전 날짜 attempt는 자정 전에 발급된 lease가 있을 때만 허용하는 방식이다. 그러면 프론트 payload 변경 없이 자정 전 문제를 실제 조회한 사용자만 이전 날짜 attempt를 만들 수 있다. `X-Challenge-Date`나 동등한 기존 날짜 식별자가 전혀 없다면 백엔드는 녹음이 어느 날짜 문제인지 판별할 수 없어 정확한 backend-only 해결은 불가능하다. creation grace와 submission deadline의 정확한 duration은 구현 전 확정해야 하며 이번 작업에서는 분석·기록만 수행했다. 신규 Jira 키는 없다.
- 2026-08-28 10초 챌린지의 자정 경계와 프론트 attempt 생성 순서를 재검토했다. 확정된 Draft 계약의 호출 순서는 `오늘 진행도 → 문제 → attempt 생성/Presigned URL 발급 → 최대 10초 녹음 → S3 PUT → answer 제출`이다. 프론트가 녹음을 먼저 끝내고 제출 직전에 attempt를 생성하면, 녹음 중 KST 날짜가 바뀔 때 이전 `X-Challenge-Date`가 현재 server 날짜와 달라 `409 CHALLENGE_DATE_CHANGED`로 새 attempt 생성이 거절되고 해당 녹음을 기존 날짜 문제에 연결할 수 없다. 따라서 녹음 버튼 처리에서 recorder 시작 전에 attempt를 생성·로컬 보관해야 한다. 자정 전에 생성된 attempt는 생성 당시 challengeDate에 고정되고 `submissionDeadlineAt=attemptCreatedAt+5분`까지 자정 이후에도 제출을 허용하며, answer 처리는 현재 날짜가 아닌 attempt의 저장된 날짜를 사용해야 한다. 이번 작업에서는 계약 분석과 기록만 수행했고 코드·Jira는 변경하지 않았다. 관련 신규 Jira 키는 없다.
- 2026-08-28 TMI-109 PR [#23](https://github.com/Too-Much-I/app-back-end-learning-core/pull/23)이 base `develop`에 merge commit `4baa4f20b7b179290dd743325ef7b251a408da47`로 병합된 것을 원격 fetch와 GitHub 재조회로 확인했다. PR 상태는 `MERGED`, CodeRabbit check는 `SUCCESS`이며 병합 커밋에 withdrawal 운영 코드·테스트·설정·runbook이 포함돼 있고 diff check가 통과한다. 로컬 `develop`도 원격과 같은 커밋으로 fast-forward했다. 구현 시 실행한 전체 402개 테스트는 failures/errors/skipped 0개였다. Jira `TMI-109`를 transition 41로 `완료` 처리하고 재조회에서 status와 resolution이 모두 `완료`임을 확인했다. `TMI-109 blocks TMI-111` 관계는 유지되며 `TMI-111`은 `해야 할 일`이다. 운영 feature flag 활성화 전 replica set·TTL index·workload 인증값과 staging E2E 검증은 계속 필요하다.
- 2026-08-28 Jira `TMI-109`의 production 보완 구현을 완료했다. `consumer-enabled`와 `deny-gate-enabled`를 분리하고 consumer만 켠 위험 조합은 startup에서 차단한다. gate-only rollback에서는 deny marker repository와 TTL 검증을 유지하며 inbox consumer는 내릴 수 있다. 동일 userId의 동시 event 충돌은 250ms/10ms bounded recheck로 다른 `sourceEventId`를 확인하면 409, 동일 source이나 승자를 확정하지 못하면 503으로 수렴한다. staging/prod에는 실제 Mongo Transaction write·rollback·잔존 0건을 검증하는 startup probe를 추가했고 공유 semantic digest golden vector와 식별자 없는 delivery-lag metric, TTL/index 설정 runbook을 보강했다. `./gradlew clean test --no-daemon` 전체 402개 테스트가 failures/errors/skipped 0개로 통과했고 `git diff --check`도 통과했다. 운영 활성화 전 workload 인증값·Access Token/retention/skew 값 승인, replica set과 정확한 TTL index 준비, 실제 rollback·동시성·다중 instance·staging E2E, 후속 Identity `TMI-111` publisher 연동이 필요하다. Jira 상태·댓글과 Git commit·push는 변경하지 않았다.
- 2026-08-27 TMI-109 구현 내용 설명 turn의 종료 훅 기록을 동기화했다. Identity 탈퇴 event 수신부터 validation·digest, inbox/marker Mongo Transaction, duplicate/conflict 수렴, JWT deny gate, 분리 flag, TTL·startup probe·관측·staging E2E와 Learning Core 선배포 순서를 설명했다. Jira `TMI-109 blocks TMI-111` 관계는 유지되며 이번 turn에서는 애플리케이션·Jira를 변경하지 않았다.
- 2026-08-27 사용자에게 TMI-109 구현 범위를 설명했다. 구현은 Identity의 `UserWithdrawn` v1 event를 workload 전용 endpoint에서 검증·멱등 소비해 eventId inbox와 userId deny marker를 단일 Mongo Transaction으로 저장하는 consumer 축과, 정상 사용자 JWT 인증 뒤 active marker를 확인해 old Access Token을 application 진입 전에 차단하는 deny gate 축으로 구성된다. 남은 production 보완은 consumer/gate flag 분리, marker unique race의 204·409·503 수렴, startup Transaction capability probe, 공유 digest golden vector, TTL·관측, replica set·workload auth·multi-instance staging E2E다. 이번 설명에서는 코드·Jira를 변경하지 않았다.
- 2026-08-27 TMI-109 dependency 교정 완료 turn의 종료 훅 기록을 동기화했다. Jira 관계는 최종적으로 `TMI-109 blocks TMI-111`이며 API와 TMI-109 화면에서 재검증됐다. 잘못된 기존 link만 제거하고 올바른 link를 재생성했으며 이슈 본문·상태·댓글과 애플리케이션 구현은 변경하지 않았다.
- 2026-08-27 사용자 확인 후 Jira dependency를 교정했다. 기존 반대 방향 `TMI-111 blocks TMI-109` link 한 건을 해제하고 `TMI-109 blocks TMI-111` link 한 건을 생성했다. TMI-109 API에는 outward issue TMI-111, TMI-111 API에는 inward issue TMI-109가 각각 한 건만 존재하며 TMI-109 화면도 `차단: TMI-111`로 표시됨을 재검증했다. 두 issue의 본문·상태·댓글은 변경하지 않았고 애플리케이션 구현도 시작하지 않았다.
- 2026-08-27 현재 확정된 1차 업데이트 범위인 SNS 로그인·검증 전화번호당 무료 모의고사 1회·10초 챌린지의 진행 상태를 Identity·Billing·Learning Core 코드와 Jira에 대조했다. Identity의 Firebase broker·PhoneIdentity·signup·eligibility publisher·Guest merge와 탈퇴 lifecycle `TMI-90`~`TMI-98`, `TMI-103`, `TMI-104`, `TMI-107`, `TMI-108`은 Jira 완료다. Billing eligibility consumer `TMI-110`도 완료돼 replica-set 테스트 33개 기록이 있지만 TrialClaim·FREE_EXAM_ONCE ledger·Reservation/reconciliation·UserMerged consumer와 실제 Lattice/SigV4 연동은 없다. Learning Core `TMI-109`는 초안 코드와 과거 전체 389개 테스트 기록이 있으나 현재 feature branch의 미커밋·미추적 상태이고 계획에서 추가한 분리 flag·marker race 409·startup Transaction probe는 미구현이며 Jira도 해야 할 일이다. Identity producer `TMI-111`도 해야 할 일이며 Jira dependency는 현재 올바른 `TMI-109 blocks TMI-111`로 교정돼 있다. 기존 시험·채점 기반은 준비됐지만 Billing reserve/confirm saga, UserMerged consumer, AttemptGroup/R3, Challenge domain/API/S3·AI job은 미구현이다. Challenge 관련 Jira는 문제 생성 `TMI-105` 완료, UI `TMI-102`와 채점 agent `TMI-106` 진행 중이나 Learning Core backend Jira는 없다. 따라서 Phase 1 서버 기반은 대부분 완료됐지만 무료시험 vertical slice, Challenge backend, 모바일·workload·staging production E2E와 rollout은 production blocker로 남아 있다. 이번 상태 점검에서는 Jira와 애플리케이션 코드를 변경하지 않았다.
- 2026-08-27 TMI-109 Jira dependency 교정 준비와 구현 계획 설명 turn의 종료 기록을 동기화했다. 현재 실제 관계는 `TMI-111 blocks TMI-109`이며, 목표는 기존 link 한 건을 해제하고 `TMI-109 blocks TMI-111`로 재생성하는 것이다. cloud link 삭제·생성의 실행 직전 확인을 요청한 상태라 Jira는 아직 변경하지 않았고 애플리케이션 구현도 시작하지 않았다. 구현 계획은 운영 계약 확정 → wire/digest golden vector → Mongo Transaction·race → security/gate → 분리 flag·TTL·capability probe·관측 → 전체/staging E2E → Learning Core 선활성화 후 TMI-111 publisher 활성화 순서다.
- 2026-08-27 사용자가 Jira dependency 방향 수정을 요청해 TMI-109 화면을 확인했다. 연결된 업무 항목이 실제로 `다음에 의해 차단됨: TMI-111`로 표시되어, 현재는 `TMI-111 blocks TMI-109`인 것이 확정됐다. 목표는 기존 link만 해제하고 `TMI-109 blocks TMI-111`로 다시 연결하는 것이며 두 issue의 내용·상태는 변경하지 않는다. 브라우저에서 cloud link 삭제와 새 link 생성은 실행 직전 확인이 필요한 외부 변경이라 사용자 확인을 기다리고 있다. 애플리케이션 구현은 시작하지 않았다.
- 2026-08-27 수정된 Jira `TMI-109` 계획서를 Jira `TMI-109`·후속 `TMI-111`, Identity Stage 5 계약과 현재 Learning Core 초안 코드에 다시 대조했다. 이전 검토의 단일 flag rollback 문제는 `consumer-enabled`/`deny-gate-enabled` 분리와 금지 조합·smoke test로, 같은 userId·다른 eventId race 오분류는 inbox 이후 marker/sourceEventId bounded 재조회와 409 acceptance로, replica set startup 검증 누락은 canary Transaction abort·잔존 0건 readiness probe로 해소됐다. 계획서는 구현 진행 가능한 상태다. 다만 실제 Jira link는 문서의 `TMI-109 blocks TMI-111`과 반대로 현재 `TMI-111 blocks TMI-109` 방향이므로 rollout 전 링크 방향을 교정해야 한다. 애플리케이션 코드·계획서·Jira는 이번 재검토에서 변경하지 않았다.
- 2026-08-27 Jira `TMI-109` 계획서를 조건부 승인 검토에 맞춰 갱신했다. 단일 flag를 목표 계약상 `consumer-enabled`와 `deny-gate-enabled`로 분리하고 consumer→gate 의존, consumer-only rollback과 gate 유지 조건을 고정했다. 동시 같은 userId·다른 eventId marker unique loser는 eventId inbox 다음 userId marker를 bounded 재조회해 다른 `sourceEventId`가 확정되면 409, winner 미가시성만 503으로 처리하도록 명시했다. staging/prod consumer startup은 전용 canary를 실제 Mongo Transaction으로 write·abort한 뒤 잔존 0건을 확인하고 실패 시 readiness 전에 중단하도록 구체화했다. 후속 Identity producer/outbox/backfill Jira `TMI-111`을 High 작업으로 생성했고 `TMI-109 blocks TMI-111` 링크를 재조회로 확인했다. 애플리케이션 코드는 변경하지 않았고 Jira 상태 전환·댓글과 Git commit·push는 수행하지 않았다.
- 2026-08-27 Jira `TMI-109` 계획서 외부 검토 4건을 코드와 Jira에 독립 재대조했고 모두 유효하다고 확인했다. 권장 보완은 `consumer-enabled`와 `deny-gate-enabled`를 분리하되 consumer 활성은 gate 활성에 종속시키고, rollback 시 consumer/workload endpoint만 내려도 기존 marker gate와 repository는 유지하는 것이다. 동시 같은 userId·다른 eventId의 marker unique loser는 inbox 확인 뒤 userId marker를 bounded 재조회해 다른 `sourceEventId`가 보이면 409, winner가 아직 보이지 않을 때만 503으로 분류해야 한다. replica set fail-fast는 consumer 활성 startup에서 실제 canary Transaction write 후 abort와 잔존 0건을 확인하는 방식으로 구체화하는 것을 권장한다. Jira read-only 재조회 결과 TMI-109 link는 0건이고 별도 UserWithdrawn producer/outbox/publisher 이슈도 검색되지 않았다. 계획서·애플리케이션·Jira는 변경하지 않았다.
- 2026-08-27 Jira `TMI-109` 계획서를 Jira 본문, Identity Stage 5 기준 문서, 현재 Learning Core 초안 구현에 대조 검토했다. 전체 범위와 wire 계약은 대체로 일치하지만 production 진행 전 보완할 핵심 항목이 있다. 단일 `app.user-withdrawn.enabled`가 consumer endpoint와 deny gate를 함께 제거하므로 계획서의 "endpoint만 비활성화하고 기존 gate 유지" rollback을 실행할 수 없고, 같은 userId·다른 eventId의 동시 insert가 marker unique 충돌을 내면 현재 loser는 계약상 409가 아니라 inbox 재조회 실패 후 503으로 끝날 수 있다. 또한 replica set 미지원 환경의 startup fail-fast를 완료 조건으로 두었지만 이를 구현하는 단계가 불명확하며, Jira의 후속 Identity producer 이슈 blocks 링크도 현재 없다. 공유 digest golden vector, 실제 replica set·multi-instance·workload auth E2E 등 기존 production gate는 계속 유효하다. 애플리케이션 코드와 계획서, Jira 필드·댓글·상태는 변경하지 않았다.
- 2026-08-27 Jira `TMI-109`의 Learning Core 구현 계획을 Identity Stage 5 계획과 현재 초안 코드에 대조해 `docs/codex/TMI-109_USER_WITHDRAWN_CONSUMER_IMPLEMENTATION_PLAN.md`로 작성했다. v1 wire·digest, inbox/marker Transaction, user JWT deny gate, workload chain, TTL·관측·rollback을 확정 범위로 정리했다. 현재 초안에는 핵심 코드와 단위/MVC 테스트가 있지만 Identity 공유 digest golden vector, 실제 replica set rollback·동시성, 다중 instance 가시성, production workload 인증 방식과 TTL 운영값, staging E2E가 남아 있으므로 이를 production 완료 gate로 분리했다. 애플리케이션 코드와 Jira 상태·댓글, Git commit·push는 변경하지 않았다.
- 2026-08-27 Jira `TMI-109`의 Learning Core consumer를 구현했다. 기능은 기본 비활성이며, 활성화하면 workload JWT 전용 `POST /internal/v1/events/withdrawn`이 v1 event를 검증하고 eventId inbox와 userId deny marker를 단일 Mongo Transaction으로 저장한다. 기존 사용자 JWT 검증 뒤 active marker가 있으면 `401 ACCOUNT_WITHDRAWN`, marker 조회 장애면 fail-closed `503 WITHDRAWAL_DENY_GATE_UNAVAILABLE`을 반환한다. marker는 Access Token 최대 수명과 verifier clock skew까지만 유지하고 inbox는 별도 TTL로 보존한다. consumer·보안·설정 테스트와 전체 389개 테스트가 성공했다. 실제 workload profile·TTL 값 승인, replica set Transaction과 staging E2E 전에는 production에서 활성화하지 않는다. Jira 댓글·상태와 Git commit·push는 변경하지 않았다.
- 2026-08-26 사용자가 Billing workload 인증 C3-D를 최종 승인했다. Learning Core의 기존 사용자 inbound Load Balancer와 Identity 사용자 JWT 검증은 유지하고, Billing outbound만 Learning Core ECS task role credential로 VPC Lattice 요청을 SigV4 서명한다. Identity workload token client는 만들지 않으며 reserve/confirm/cancel/status client와 same-key retry를 후속 구현해야 한다. 코드·외부 API·현재 Git/Jira 상태는 변경하지 않았다.
- 2026-08-26 Billing workload 인증을 기존 서비스와 맞추기 위해 인증·outbound 구현을 읽기 전용 대조했다. Learning Core의 실제 인증은 Identity RS256 사용자 JWT를 issuer·JWKS·audience·시간·UUID sub로 로컬 검증하는 방식이다. Python AI outbound는 Authorization 없이 `Idempotency-Key`만 전송하고 Identity workload event consumer는 아직 없으므로 재사용 가능한 server-to-server 인증은 없다. Billing에는 사용자 token을 전달하지 않고 Identity-issued workload 전용 JWT를 발급받아 캐시하는 client가 새로 필요하다. 코드·외부 API·현재 Git/Jira 상태는 변경하지 않았다.
- 2026-08-25 Part 4 PR 준비 상태 확인 turn의 marker를 종료 hook 요구값으로 보완했다. `origin/develop`은 `514fb49`이고 `origin/main`보다 1커밋 앞서며 PR에는 converter와 테스트 2개만 포함된다. 남은 절차는 develop→main PR, CI, review와 merge다. 별도 Jira 키는 제공되지 않았다.
- 2026-08-25 Part 4 `text` 수정의 PR 준비 상태를 확인했다. `develop`과 `origin/develop`은 동일 커밋 `514fb49`이며 `origin/main`보다 정확히 1커밋 앞서 있어 commit·push가 완료됐다. main 대비 diff는 `ExamConverter.java`와 회귀 테스트 2개만 포함하며 3 files, 10 insertions, 3 deletions이고 `git diff --check origin/main...develop`이 성공했다. 따라서 남은 Git 작업은 `develop → main` PR 생성·검토·병합뿐이다. working tree의 AGENTS/README/작업 문서 변경은 커밋에 포함되지 않았으며 별도 Jira 키는 제공되지 않았다.
- 2026-08-25 Part 4 선택적 stage turn의 기록 marker를 종료 hook 요구값으로 보완했다. staged 대상은 converter 1개와 테스트 2개뿐이며 다른 문서는 stage되지 않았다. 직전 전체 352개 테스트와 staged/unstaged diff check는 성공했고 commit·push·main merge는 사용자가 수행해야 한다. 별도 Jira 키는 제공되지 않았다.
- 2026-08-25 사용자의 요청에 따라 Part 4 결과 상세 `text` 수정 커밋 대상만 stage했다. staged 파일은 `ExamConverter.java`, `ExamOwnershipServiceTest.java`, `ExamReadApiContractTest.java` 세 개이며 staged diff는 10 insertions/3 deletions다. AGENTS, README, WORKLOG/CURRENT_STATE와 다른 미추적 문서는 stage하지 않아 커밋에 섞이지 않는다. 직전 전체 352개 테스트와 staged `git diff --check`가 성공했다. 저장소 규칙에 따라 Codex는 commit·push·main merge를 수행하지 않으며 사용자가 커밋·push 후 develop→main PR을 병합해야 한다. 별도 Jira 키는 제공되지 않았다.
- 2026-08-25 Part 4 `text` 수정의 main 반영 전 Git 상태 확인 turn marker를 종료 hook 요구값으로 보완했다. 현재 수정은 `develop` checkout의 미커밋 working tree에 있으며 develop/main/origin-main은 모두 `98730c9`다. 관련 파일만 선택적으로 stage/commit한 뒤 main PR로 병합해야 하고 다른 미커밋 문서를 함께 반영하면 안 된다. 별도 Jira 키는 제공되지 않았다.
- 2026-08-25 Part 4 결과 상세 `text` 수정의 Git 상태를 확인했다. 현재 checkout은 `develop`이고 `develop`, 로컬 `main`, `origin/main`은 모두 `98730c9`를 가리키지만 수정 파일은 아직 미커밋 working tree 상태라 어느 브랜치 이력에도 포함되지 않았다. 즉 즉시 main 반영에는 관련 운영 코드 1개와 테스트 2개만 선택적으로 stage/commit한 뒤 PR로 main에 merge해야 한다. 작업 트리에 AGENTS/README와 다수 문서 변경이 함께 있으므로 `git add .` 또는 전체 commit은 피해야 한다. Codex는 규칙상 commit·push를 수행하지 않는다. 별도 Jira 키는 제공되지 않았다.
- 2026-08-25 세 문항 제공 경로 대조 turn의 작업 기록 marker를 종료 hook 요구값으로 보완했다. 결론은 동일하다. 시험 생성과 prompt는 기존부터 Part 4 `text`를 제공했고 결과 상세의 Part 4 전용 converter만 누락돼 운영 코드 한 곳 수정이 충분하며, 세 경로의 계약 테스트와 전체 352개 테스트가 성공했다. 별도 Jira 키는 제공되지 않았다.
- 2026-08-25 문항 원문을 제공하는 세 경로를 대조했다. `POST /api/v1/exams`는 `result.questions[].text`, `GET /{examId}/questions/{questionNumber}/prompt`는 `result.text`, 결과 상세 `GET /{examId}/questions?questionNumber=&retryCount=`는 `result.question.questionInfo.text`를 사용한다. 시험 생성과 prompt는 이미 공통 `ExamConverter.toQuestionDTO()`가 Part 4 원본 `Question.question`을 `text`로 매핑하고 있었고, 결과 상세만 Part 4 전용 `toQuestionInfoDTO()`가 누락해 이번 운영 코드 한 곳 수정이 충분하다. 생성·prompt·결과 상세의 Part 4 text 계약 테스트를 모두 명시적으로 확인·보강했다. 핵심 테스트와 전체 352개 테스트가 모두 성공했으며 실패·오류·건너뜀은 0개다. 별도 Jira 키는 제공되지 않았다.
- 2026-08-25 Part 4 문항 상세 결과의 `result.question.questionInfo`가 표 `tableContext`만 제공하고 질문 문장을 누락하던 원인을 수정했다. `ExamConverter.toQuestionInfoDTO`의 Part 4 전용 최소 변환이 원본 `Question.question`을 제외하고 있었으며, 기존 공통 응답 필드 `text`에 이를 매핑했다. 앱 필드 경로는 `result.question.questionInfo.text`다. Part 4의 `tableContext`와 기존 필드, URL·Method·query parameter·`BaseResponse`는 유지하고 `tableImageUrl` 등 기존 비노출 필드는 추가하지 않았다. 핵심 테스트와 `./gradlew clean test --no-daemon`이 성공했으며 전체 352개 테스트의 실패·오류·건너뜀은 0개다. 별도 Jira 키는 제공되지 않았다.
- 2026-08-25 Challenge 녹음·업로드 canonical 형식은 `.m4a` 확장자의 M4A 컨테이너와 AAC 코덱으로 확정했다. S3 PUT과 object metadata의 `Content-Type`은 `audio/mp4`, server-generated S3 key 확장자는 `.m4a`를 사용한다. sample rate·channel·최대 파일 크기와 AI 서버의 직접 처리 또는 내부 변환 방식은 아직 미확정이며 Jira 키는 없다.
- 2026-08-25 Challenge AI 자동 재시도 소진 시 결과 조회 응답을 확정했다. 조회 요청 자체는 성공이므로 HTTP 200과 기존 `BaseResponse(isSuccess=true, code=COMMON_200)`를 유지하고, 문제에는 `attemptStatus=submitted`, `gradingStatus=failed`, `gradedAt=null`, `aiResult=null`을 반환한다. prompt·submittedAt·referenceAnswer는 유지하며 내부 예외명·AI 원문·재시도 횟수·failureReason은 공개하지 않는다. 프론트는 polling을 중단하고 피드백 생성 실패 안내를 표시한다. Jira 키는 없다.
- 2026-08-25 Challenge의 `attemptStatus=submitted`는 사용자 audio 접수 완료를 뜻하며 AI 채점 완료를 뜻하지 않는다. submitted 문제는 Callback 전이나 최종 AI 실패 후에도 결과 API에서 항상 HTTP 200으로 조회 가능해야 한다. Callback 전에는 참고 답안·제출 정보와 `gradingStatus=pending|processing`, `aiResult=null`을 반환하고, 최종 실패 시에도 `gradingStatus=failed`와 참고 답안을 유지한다. 프론트 polling 중단·앱 종료는 서버 Job을 취소하지 않으며 재진입 시 재조회한다. submitted 문제 조회가 404라면 정상 대기 상태가 아니라 서버 정합성 오류다. 프론트 polling 상한과 서버 timeout·최대 retry·최종 failed 전환 시간은 미확정이며 Jira 키는 없다.
- 2026-08-25 혼동을 일으킨 “`timed_out` 제거 후 결과 내용을 `feedbackType`과 `gradingStatus`로 표현” 문구를 바로잡았다. 공개 `attemptStatus`는 화면 이동 기준인 `not_started|submitted`만 사용하고, 10초 녹음 종료는 정상 제출이다. 별도의 공개 `feedbackType` enum은 두지 않으며 AI 준비 상태는 `gradingStatus`, 실제 결과는 nullable `aiResult`·`transcript`와 안내 문구로 표현한다. Jira 키는 없다.
- 2026-08-25 자정 직전 프론트 캐시 경합을 방지하기 위해 오늘 진행도 응답에 server 기준 `challengeDateExpiresAt`과 `expiresInSeconds`를 주는 안을 권장했다. 앱은 server TTL로 timer를 시작하고 만료·foreground 복귀 시 재조회하며, question·attempt 요청에 `X-Challenge-Date`를 보내 server가 현재 KST 날짜와 최종 비교한다. 불일치하면 mutation 없이 `409 CHALLENGE_DATE_CHANGED`와 최신 날짜 정보를 반환한다. client timer만으로는 요청 중 rollover 경합을 막을 수 없어 server 검증이 필수다. 이 계약은 사용자 최종 승인 전이며 Jira 키는 없다.
- 2026-08-25 Challenge history는 cursor pagination을 제거하고 `yearMonth=YYYY-MM` 월별 조회로 변경했다. 응답은 KST 날짜마다 `participated`와 공개 `attemptStatus=submitted` 문제 수 `solvedQuestionCount`만 제공한다. 정상 audio 제출·무음·5분 만료 terminal은 프론트에서 구분하지 않고 풀이 수에 포함하며 아직 terminal이 아닌 공개 `not_started`만 제외한다. 특정 날짜 결과는 `GET /api/v1/challenges/{challengeDate}/results?questionNumber={optional}`로 통합하고 번호가 없으면 풀이 수만, 번호가 있으면 날짜 전체 풀이 수와 해당 문제 단일 상세만 반환한다. Jira 키는 없다.
- 2026-08-25 프론트 공개 문제 상태는 화면 이동에 필요한 `attemptStatus=not_started|submitted` 두 값만 두는 것으로 단순화했다. 서버 내부의 CREATED/UPLOADING은 공개 `not_started`, 정상 제출·무음·5분 만료 terminal은 공개 `submitted`로 projection한다. 내부 상태는 Presigned URL 재발급·멱등 submit·deadline 처리에 필요하므로 제거하지 않는다. history에서도 timeout/expired count를 별도 노출하지 않는다. Jira 키는 없다.
- 2026-08-25 history의 cursor는 페이지 경계를 위한 토큰이지만 최신 결정에서는 월별 최대 31건만 반환하므로 제거했다. Jira 키는 없다.
- 2026-08-25 `10초 종료=timed_out=피드백 없음`은 사용자가 실제 발화했는데도 답안·피드백을 잃는 UX이므로 채택하지 않는다. 앱은 10초에 녹음을 자동 종료하고 녹음된 audio를 정상 `submitted`로 올리며, 서버는 제출 접수 즉시 참고 영어 문장을 반환하고 다음 문제를 연다. AI feedback은 비동기로 갱신하고 무음과 5분 미제출도 별도의 공개 결과 타입 없이 `gradingStatus`, nullable AI 결과와 안내 문구로 표현한다. AI 실패에도 참고 답안과 제출 상태는 유지한다. Jira 키는 없다.
- 2026-08-24 자정 직전 ChallengeAttempt는 생성 당시 challengeDate에 귀속하고 `submissionDeadlineAt=attemptCreatedAt+5분`까지 제출을 허용하는 것으로 확정됐다. 23:59:50 생성은 00:04:50까지 이전 날짜 제출로 처리하며, 자정 이후 이전 날짜의 새 attempt 생성은 금지하고 deadline 이후 제출은 `CHALLENGE_ATTEMPT_EXPIRED`로 거절한다. 이 5분은 10초 녹음 검증이 아니라 S3·네트워크 복구 시간이다. Jira 키는 없다.
- 2026-08-24 10초 챌린지는 세 문제를 모두 푸는 것이 필수가 아니며 월별 history에서 실제 audio 제출이 한 문제 이상인 날짜를 참여로 표시하고 풀이 문제 수를 함께 노출한다. Jira 키는 없다.
- 2026-08-24 10초 챌린지는 녹음 길이를 최대 10초로 제한하고, 1→2→3 순차 진행하며, 같은 KST 날짜에 모든 사용자가 동일한 3문제를 푸는 것으로 확정됐다. 세 문제 완료 여부와 무관하게 일부 참여 날짜도 history에 포함한다. Jira 키는 없다.
- 2026-08-25 프론트 전달용 10초 챌린지 API를 Draft v0.5로 갱신했다. 공개 `attemptStatus`는 `not_started|submitted`만 제공하고, 월별 참여·풀이 수, 날짜 count/detail 분리와 server 기준 날짜 rollover 보호를 반영했다. timeout UX와 M4A/AAC·`audio/mp4` 형식은 확정됐고 API는 아직 구현·배포되지 않았다. rollover 보호 최종 승인, sample rate·channel·최대 파일 크기와 AI 결과 필드는 미확정이다. 기존 시험 API·AI·S3 계약은 변경하지 않았고 Jira 키는 없다.
- 2026-08-24 10초 챌린지 콘텐츠가 한국어 문장을 보고 영어 문장을 만들어 최대 10초 길이로 직접 발음한 audio를 S3에 올리는 방식으로 확정됐다. 문제 DTO는 한국어 prompt를 제공하고 참고 영어 문장은 결과 전까지 숨긴다. AI는 S3 audio를 인식해 transcript와 의미·문법·발음의 간단한 feedback을 생성하는 challenge 전용 비동기 계약을 사용한다. server는 audio duration을 검증하지 않고 attempt·presigned upload·object 검증·terminal·AI Job을 관리한다. audio canonical format과 최종 feedback 필드는 미확정이며 Jira 키는 없다.
- 2026-08-24 사용자가 녹음 시간은 프론트가 측정하고 영어 발화 녹음 audio를 S3에 직접 업로드한다고 확인했다. 최신 UX 권장안에서는 server가 문제당 단일 attempt, presigned upload URL, exact server-generated object key, submitted/expired terminal과 AI Job을 관리한다. 녹음 길이는 최대 10초이며 `.m4a` M4A/AAC와 `audio/mp4`는 확정됐고 sample rate·channel·최대 크기는 추가 확정이 필요하다. Jira 키는 없다.
- 2026-08-24 프론트가 요청한 10초 챌린지 API를 검토했다. 오늘 진행도, 개별 문제, 답안 제출, 날짜별 이력, 특정 날짜·문제 결과는 필요하며 하루 3문제·문제당 최대 1회·한국어 prompt 기반 영어 발화 audio·AI 간단 피드백 요구를 반영했다. 녹음 길이는 client가 최대 10초로 제한하고 server는 audio duration을 검증하지 않으며, server는 attempt·S3 upload·terminal 상태를 관리한다. 일일 상태와 문제 attempt 상태, AI grading 상태를 분리하고 answer submit은 UUID idempotency, AI 피드백은 challenge 전용 비동기 Job/Callback으로 처리한다. 순차 진행·공통 문제·M4A/AAC와 `audio/mp4`는 확정됐고 sample rate·channel·최대 크기와 feedback 필드는 미확정이며 상세 내용은 `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`에 기록했다. Jira 키는 없다.
- 2026-08-24 결제·credit·pass 구현을 후속으로 미루고 1차 우선 범위를 SNS 로그인, 검증된 전화번호당 무료 모의고사 1회, 10초 챌린지로 변경하는 계획을 정리했다. Identity에는 Firebase exchange/signup·Guest flow, SocialIdentity·PhoneIdentity·eligibility publisher 기반이 이미 있으나 production flag는 꺼져 있고 탈퇴 lifecycle 1~3, 실제 모바일 Google/Apple/Phone과 staging E2E가 남아 있다. 무료 1회는 결제 없이도 TrialClaim unique와 reserve/confirm 원장이 필요하며 기존 결정대로 최소 Billing/Entitlement가 소유한다. 10초 챌린지는 Learning Core 신규 domain이며 MEMBER·KST 일 3문제·한국어 prompt 기반 영어 발화 audio·문제당 별도 attempt·경제적 reward 없음의 MVP를 권장한다. 상세 계획은 `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`에 기록했고 Jira 키는 없다.
- 2026-08-24 Learning Core와 Identity의 Sentry 수집·메일 알림 경계를 정적 확인했다. Learning Core는 DSN이 주입된 환경에서 ControllerAdvice의 예상 밖 500과 servlet/filter까지 빠져나간 unhandled Runtime/Servlet 예외만 명시적으로 수집하고, validation·JSON parse·인증·비즈니스 4xx와 단순 ERROR 로그·grading/AI/Callback 운영 실패 로그는 자동 Sentry event로 보내지 않는다. Identity는 `SENTRY_ENABLED=true`와 DSN이 함께 주입돼야 하며 GlobalExceptionHandler의 예상 밖 500만 명시 capture하고 expected 4xx와 ERROR 로그는 제외한다. 두 서비스 모두 tracing/log integration은 꺼져 있다. Sentry event 수신과 이메일 발송은 별개이며 저장소에는 실제 Alert Rule·수신자·임계값이 없으므로 현재 이메일 조건은 Sentry 프로젝트의 Alerts/Notifications에서 확인해야 한다. Jira 키는 없다.
- 2026-08-24 Billing 계약의 단일 기준과 이후 작업기록 위치를 `/Users/msde76/billing/docs/codex`로 이전했다. Billing의 `CONTRACT_DECISIONS.md`에 기존 확정사항과 C1~C13 선택지·장단점·권장 승인 순서를 기록했으며, 1차 구현 전 C1~C8 승인이 필요하다. Learning Core 종료 훅 호환성을 위해 이 상태 요약만 남기며 앞으로 Billing 계약 본문은 Learning Core 문서에 추가하지 않는다. Learning Core 애플리케이션·외부 API·DTO·AI·Redis·S3 계약은 변경하지 않았고 Jira 키는 없다.
- 2026-08-24 신규 `/Users/msde76/billing` 프로젝트의 기본 설정을 완료했다. 로컬 Git 저장소를 `develop` 브랜치로 초기화하고 빈 GitHub 저장소 `Too-Much-I/app-back-end-billing`을 `origin`으로 연결했다. Spring Boot 3.4.2·Java 21과 Web, Validation, MongoDB, Security Resource Server, Actuator, Lombok, Testcontainers 기반으로 맞췄고 애플리케이션 이름 `app-back-end-billing`, 기본 포트 8082, 환경변수 Mongo 설정을 적용했다. 인증 계약 구현 전에는 health 외 요청을 fail-closed로 차단한다. Billing에 `AGENTS.md`, `.codex/hooks`, `docs/codex/CURRENT_STATE.md`, `WORKLOG.md`를 추가했으며 `./gradlew clean test`가 성공했다. 결제 API·도메인·스토어 검증과 Learning Core workload 연동은 구현하지 않았고 Jira 키는 없다. Learning Core 애플리케이션·외부 계약은 변경하지 않았다.
- 2026-08-24 `/Users/msde76/billing`에 생성된 Billing skeleton을 사용자 요청대로 읽기 전용 점검했다. Java 21, Gradle Groovy, group/package `web.tosunsaeng`/`web.tosunsaeng.billing`, 기본 Application·context test와 wrapper는 정상 생성됐지만 Spring Boot는 기존 두 서비스의 3.4.2가 아닌 4.1.1, Gradle wrapper는 9.5.1이다. `build.gradle`에는 `spring-boot-starter`와 test starter만 있어 Web, Validation, MongoDB, Security, OAuth2 Resource Server, Actuator, Lombok 및 transaction 통합 테스트 의존성이 없다. root project와 application name은 `billing`이고 아직 Git repository가 아니다. Billing 파일은 수정하지 않았고 빌드 산출물 생성을 피하기 위해 Gradle 테스트도 실행하지 않았다. Billing/Learning Core 후속 Jira 키는 아직 없다.
- 2026-08-24 신규 앱 Billing/Entitlement 서버의 Spring Initializr 권장 구성을 확정했다. 기존 Identity·Learning Core와 맞춰 Gradle Groovy, Java 21, Spring Boot 3.4.2 계열, group `web.tosunsaeng`, artifact/name `app-back-end-billing`, package `web.tosunsaeng.billing`, Jar를 사용한다. 초기 의존성은 Spring Web, Validation, Spring Data MongoDB, Spring Security, OAuth2 Resource Server, Actuator, Lombok이며 Testcontainers는 Mongo transaction/unique concurrency 검증을 위해 수동 추가한다. Redis, JPA/SQL, Kafka/SQS, AWS SDK, OAuth2 Client, Apple/Google adapter와 Sentry/OpenAPI는 실제 계약·운영 필요에 따라 후속 추가하고, 결제·entitlement·reservation 원장은 MongoDB를 단일 진실 공급원으로 둔다. Billing/Learning Core 후속 Jira 키는 아직 없다.
- 2026-08-21 종료 훅 요구에 따라 Summary generation wire field 확인 작업의 WORKLOG marker를 보완했다. 외부 JSON은 root-level `generation_attempt`, Java 내부는 `generationAttempt`라는 결론과 관련 기존 Jira `TMI-25` 상태는 동일하다.
- 2026-08-21 Summary generation 외부 wire 이름은 camelCase가 아니라 root-level snake_case `generation_attempt`로 확정돼 있다. Java DTO 내부만 `generationAttempt`이며 `@JsonProperty("generation_attempt")`로 역직렬화한다. AI 요청과 Callback 모두 숫자 값을 최상위 `generation_attempt`로 보내야 하고 `generationAttempt`나 nested metadata는 계약이 아니다. 관련 기존 Jira는 `TMI-25`이며 신규 Jira 키는 없다.
- 2026-08-21 사용자가 AI 문항 Callback은 동일한 `FEEDBACK_CALLBACK_URL`로 현재 앱 Backend에 정상 저장된다고 확인했다. 따라서 Callback 목적지 문제는 우선순위에서 제외하고 Summary JSON 계약 차이를 확인한다. 문항과 Summary는 같은 `/api/v1/exams/callback/feedback` endpoint를 사용하지만 Summary는 root-level snake_case `generation_attempt`가 현재 Job generation과 같아야 하고, `suggested_total_score`로 Summary로 분류되며, non-empty `part_feedback`가 필요하다. 현재 두 Job에 completion claim과 failureReason이 모두 없으므로 가장 유력한 원인은 실제 worker Callback에서 `generation_attempt`가 누락·다른 이름·중첩·불일치한 경우다. 관련 기존 Jira는 `TMI-25`이며 신규 Jira 키는 없다.
- 2026-08-21 종료 훅 요구에 따라 두 Summary Job `PROCESSING/dispatchAttempt=2` 추가 진단의 WORKLOG turn marker를 보완했다. 진단 결론과 관련 기존 Jira `TMI-25`, 신규 Jira 없음 상태는 변경되지 않았다.
- 2026-08-21 두 Summary Job(`ex_e855ed97a6_0821_0429`, `ex_0ff1b425ab_0821_0412`) 모두 generation 1·`PROCESSING`·`dispatchAttempt=2`이고 `completionClaimedGeneration`, `failureReason`, `completedAt`이 없다. 유효한 current-generation Callback이 현재 앱 Backend의 저장 경로에 진입했다면 completion claim이 먼저 기록되므로, 두 시험의 동일 패턴은 우연한 경합이나 empty `part_feedback`보다 AI Callback URL이 다른 Backend를 가리키거나 payload의 `generation_attempt`가 누락/불일치하는 시스템 문제를 우선 지시한다. 첫 시험은 04:34:22 Callback 200 이후에도 완료되지 않아 04:41:08에 재전송됐다. 다음 확인은 `exam_sessions` abandoned 여부, `exam_summaries` 부재, AI worker의 실제 Callback target host와 안전한 metadata `user_id/generation_attempt/part_feedback key count`, ECS worker image digest가 echo 수정 commit을 포함하는지다. 관련 기존 Jira는 `TMI-25`이며 신규 Jira 키는 없다.
- 2026-08-21 `ex_e855ed97a6_0821_0429` Summary 요청은 04:34:14.642 UTC에 generation 1로 AI에 전송됐고 AI가 04:34:22.293 UTC에 Backend Callback HTTP 200을 기록했으므로 네트워크 미도착보다 Backend 수신 후 no-op/실패 분기가 유력하다. Callback controller는 stale/missing generation, duplicate, abandoned Session, completion claim 상실과 empty `part_feedback` 처리 뒤에도 200을 반환한다. 정상 저장이면 같은 examId의 `요약 채점 콜백 저장 완료` INFO와 Summary Job 완료 INFO가 있어야 한다. 오늘 10:22 KST에 web-ai generation echo 수정이 이미 배포됐으므로 누락을 단정하지 않고 `summary_grading_jobs` generation/status/failureReason, `exam_summaries` 존재, `exam_sessions` 상태와 Backend INFO/DEBUG/WARN을 함께 확인해야 한다. 관련 기존 Jira는 `TMI-25`이며 신규 Jira 키는 없다. 제공된 AWS Task 링크는 브라우저 세션이 로그아웃 상태라 직접 조회하지 못했다.
- 2026-08-21 `GET /`의 `COMMON401`·`InsufficientAuthenticationException` 로그를 진단했다. JWT 모드에서 공개 경로는 Callback, Swagger, `/actuator/health`이고 루트 `/`는 `authenticated()` 대상이므로 Bearer 인증 없이 접근하면 Security가 Controller/404 처리 전에 정상적으로 401을 반환한다. 단발이면 브라우저·외부 probe일 가능성이 높고 30~60초 주기 반복이면 ALB Target Group health check path가 `/`로 설정됐는지 확인해 `/actuator/health`로 수정해야 한다. 배포 workflow의 검증 URL은 이미 `/actuator/health`다. `/`를 단순히 공개하는 코드는 추가하지 않았으며 관련 신규 Jira 키는 없다.
- 2026-08-21 사용자가 현재 저장소는 앱 전용이며 웹과 앱 서버가 분리되어 있으므로 앞으로 웹 저장소·웹 동작을 함께 고려하지 말고 앱만 대상으로 하라고 확정했다. 저장소 검색 결과 실제 웹 백엔드·웹 프론트 코드는 없고, 웹 관련 항목은 복제 출처를 설명하는 문서, 과거 작업 기록, 레거시 Java package namespace `web.tosunsaeng`, 별도 공유 Python AI 저장소명 `web-ai`뿐이다. `AGENTS.md`와 `README.md`를 앱 전용 범위로 명확히 했으며, 웹 호환성을 신규 설계 제약에서 제외하되 현재 앱 공개 API와 공유 Python AI `user_id=examId`·Callback 계약 보호는 유지한다. 관련 기존 Jira는 `TMI-14`, `TMI-25`, `TMI-31`, Identity `TMI-90`, `TMI-95`, `TMI-98`이며 신규 Jira 키는 없다.
- 2026-08-21 reservation 5분 TTL은 시험 시간 제한이나 confirmed consumption 만료가 아니라 `RESERVED` 상태에만 적용된다고 명확히 했다. 정상 시작은 Session durable commit 직후, client에 시험을 노출하기 전에 `RESERVED → CONFIRMED/CONSUMED`로 전이하므로 시험 중 5분이 지나도 credits가 `AVAILABLE`로 돌아가지 않는다. confirm되지 않은 Session은 사용할 수 있게 반환하지 않으며, TTL 만료는 Session commit 전에 프로세스가 중단되는 orphan hold 복구에만 쓰인다. 관련 기존 Jira는 Identity `TMI-95`, `TMI-96`, `TMI-98`이며 Billing/Learning Core 후속 Jira 키는 아직 없다.
- 2026-08-21 시험 시작의 `5분 reserve → Session 저장 → confirm` 의미를 명확히 했다. 5분은 사용자 대기 시간이 아니라 Billing reservation TTL이며 정상 요청은 세 단계를 수초 안에 처리한다. reserve는 10 credits/free entitlement/pass 사용 권리를 동시 요청이 재사용하지 못하게 임시 hold할 뿐 영구 소비하지 않고, Learning Core가 `ExamSession`과 operation/reservation 관계를 MongoDB에 durable commit한 뒤 confirm에서 최초 AttemptGroup 소비를 확정한다. Session 저장 실패는 cancel/TTL 만료로 hold를 반환하고, confirm 응답 유실은 같은 operation의 상태 조회·재시도로 수렴해 새 Session·이중 차감을 만들지 않는다. 관련 기존 Jira는 Identity `TMI-95`, `TMI-96`, `TMI-98`이며 Billing/Learning Core 후속 Jira 키는 아직 없다.
- 2026-08-21 `web-ai` deploy role의 최종 IAM 정책 전체본을 확정했다. 기존 ECR authorization/repository push, exact ECS Service describe/update, Task Definition register/tag와 execution role PassRole 제한을 유지하고, AWS authorization 특성에 따라 `ecs:DescribeTaskDefinition`, `ecs:ListTasks`, `ecs:DescribeTasks`는 resource `*`의 read-only statement로 허용한다. 이를 통해 현재 Task Definition 다운로드와 배포 후 running Task image/digest 검증의 두 AccessDenied를 해결한다. 정책 저장 후 GitHub Actions failed jobs를 rerun하며 Role과 `AWS_ROLE_ARN`은 변경하지 않는다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. 실제 IAM·workflow는 변경하지 않았다.
- 2026-08-21 재실행한 `web-ai` workflow가 새 Task Definition `tosunsaeng-ai:6`과 새 ECR digest까지 생성한 뒤 배포 후 Task 검증 단계의 `ecs:ListTasks` AccessDenied로 실패했다. 배포·register 단계는 통과했고 실패는 running Task image/digest 확인을 위한 read-only 검증이다. deploy role 정책에 `ecs:ListTasks`와 후속 검증에서 필요한 `ecs:DescribeTasks`를 resource `*`로 추가한다. 기존 ECR push, exact Service update, execution role PassRole 제한은 유지한다. 정책 저장 후 failed jobs만 rerun하며 이미 같은 digest/revision이 배포된 상태일 수 있으므로 ECS Service events와 revision 6 상태를 함께 확인한다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. 실제 IAM·workflow는 변경하지 않았다.
- 2026-08-21 Phase 0 계약 확정을 위한 선택 영역을 정리했다. 이미 확정된 것은 Billing/Entitlement 단일 서비스, Apple/Google 결제, 검증 phone당 무료 1회, 10 credits/시험, 5분 reserve→Session commit→confirm, 이어풀기 제외와 R3 무료 replacement다. 추가 결정이 필요한 핵심은 (1) 공개 `Idempotency-Key` 의무 수준과 replay 응답, (2) entitlement 자동 선택 여부, (3) confirm 불명 시 외부 응답과 Session 노출, (4) Billing 오류 mapping, (5) AttemptGroup·consumption의 서비스별 소유권과 완료 증거, (6) replacement 동시성·authorization, (7) PhoneEligibility/UserMerged 전달 방식, (8) workload 인증, (9) store 거래 검증·notification 멱등 원천, (10) reconciliation과 rollout gate다. 권장 기본 조합은 기존 공개 body/response를 유지하며 신규 앱만 UUID v4 key 필수, 완료 replay는 동일 200·처리 중 409, 서버 자동 entitlement 선택, confirm 전 Session 비노출·503, 안정적 오류 mapping, Learning Core가 AttemptGroup 학습 상태를 소유하고 Billing이 consumption을 소유, consumer별 direct HTTPS delivery와 workload JWT, server-side store 검증+notification inbox, 양쪽 operation 조회 기반 reconciliation이다. 관련 기존 Jira는 `TMI-90`, `TMI-95`, `TMI-98`, `TMI-14`, `TMI-25`, `TMI-31`이며 후속 Jira 키는 아직 없다.
- 2026-08-21 전체 앱 계획의 최우선 다음 단계는 코드 구현보다 Phase 0 계약·Jira 동결이다. 가장 먼저 Learning Core→Billing의 `reserve/confirm/cancel/status/reconcile`, 5분 reservation, Session commit 불명 복구, `Idempotency-Key`, 공개 오류 mapping과 R3 `AttemptGroup` 상태 계약을 확정하고, Identity `PhoneEligibilityBinding`·`UserMerged` multi-consumer fan-out, Billing 서비스, Learning Core Billing 연동, Client IAP, staging E2E를 별도 Jira로 분리해야 한다. 이후 feature flag OFF로 Identity fan-out, Billing 원장/TrialClaim/Reservation, Learning Core contract client·Session metadata를 병렬 구현한다. 관련 기존 Jira는 Identity `TMI-90`, `TMI-95`, `TMI-98`, Learning Core 기반 `TMI-14`, `TMI-25`, `TMI-31`이며 Billing/Learning Core 후속 Jira 키는 아직 제공되지 않았다.
- 2026-08-21 사용자가 현재 `tosunsaeng-web-ai-github-deploy-policy` 전체 JSON을 제공하고 AccessDenied 수정본 전체를 요청했다. `ReadAiTaskDefinitions` statement의 `ecs:DescribeTaskDefinition` resource만 Task Definition ARN에서 `*`로 변경한 완전한 정책을 제공하며, ECR repository `tosunsaeng-ai`, exact Service `tosunsaeng-staging-cluster/tosunsaeng-ai-service`, Task Definition tag ARN과 기존 execution role PassRole 제한은 유지한다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. 실제 IAM·workflow는 변경하지 않았다.
- 2026-08-21 첫 `web-ai` ECS GitHub Actions run에서 OIDC assume은 성공했지만 현재 Task Definition 다운로드 중 `ecs:DescribeTaskDefinition` AccessDenied가 발생했다. 오류가 action resource를 `*`로 평가하므로 앞서 ARN으로 제한해 안내한 `DescribeTaskDefinition` statement가 매칭되지 않은 것이 원인이다. 기존 `tosunsaeng-web-ai-github-deploy-role`이나 GitHub variable은 다시 만들지 않고 연결 정책에서 `ecs:DescribeTaskDefinition`의 `Resource`만 `*`로 수정한다. ECR repository, exact ECS Service update와 execution role PassRole 범위는 그대로 유지한다. 정책 저장 후 실패한 workflow를 rerun하면 되며 후속 denied action이 있으면 해당 API의 AWS authorization model에 맞춰 최소 범위로 보완한다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. 실제 IAM·workflow는 변경하지 않았다.
- 2026-08-21 사용자가 `web-ai`에 deploy 코드가 없다고 확인했다. 별도 `deploy.sh`나 애플리케이션 배포 모듈은 필요 없지만 GitHub Actions 자동 배포를 위해 `.github/workflows/deploy-ecs.yml` 같은 workflow 파일은 반드시 새로 생성해야 한다. 이 YAML 자체가 checkout/test, OIDC assume, ECR build/push, 현재 Task Definition 조회, 다섯 container image render, `tosunsaeng-ai-service` deploy/stability 검증을 수행하는 배포 코드다. Dockerfile은 image build에 필요하고 기존 offline test/Compose 검증 명령은 새 workflow에 옮긴다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. 실제 `web-ai` 파일·AWS·GitHub를 변경하지 않았다.
- 2026-08-21 사용자가 `tosunsaeng-web-ai-github-deploy-role` 생성과 `Too-Much-I/web-ai` GitHub variable `AWS_ROLE_ARN` 등록을 완료했다고 확인했다. 남은 작업은 AI 팀원이 `web-ai/.github/workflows/deploy.yml`을 수정하는 것이다. 기존 offline test·Compose validation은 유지하고 Docker Hub/EC2 SSH 단계를 제거하며 OIDC permission, ECR `tosunsaeng-ai` 단일 build/push, 현재 `tosunsaeng-ai-service` Task Definition 조회, `ai-api`와 worker 4개에 동일 digest 순차 render, Service 단일 deploy와 stability 검증을 추가한다. public `HEALTH_URL` curl은 제외하고 기존 `/ready` ECS health check를 사용하며 Task Definition의 command/environment/secrets/mount/resources는 변경하지 않는다. trust가 `web-ai/main` branch subject이므로 실제 배포 검증은 main merge/push 또는 main 대상 workflow_dispatch에서 이루어진다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. 실제 workflow·AWS·GitHub를 추가 변경하지 않았다.
- 2026-08-21 `tosunsaeng-web-ai-github-deploy-role` 생성 절차를 현재 AI ECS 실값으로 확정했다. 기존 GitHub OIDC provider `token.actions.githubusercontent.com`과 audience `sts.amazonaws.com`을 재사용하고 trust subject를 `repo:Too-Much-I/web-ai:ref:refs/heads/main`으로 제한한다. Role 권한은 ECR authorization, repository `tosunsaeng-ai` push, ECS describe/register/tag, 정확한 service `tosunsaeng-staging-cluster/tosunsaeng-ai-service` update, 기존 execution role `tosunsaeng-ecs-execution-role`에 대한 `iam:PassRole`만 허용한다. AI `taskRoleArn`은 null이므로 PassRole 대상에 추가하지 않는다. 생성된 role ARN은 GitHub repository variable `AWS_ROLE_ARN`으로 등록하고 static AWS key는 만들지 않는다. workflow에 GitHub Environment를 추가하면 OIDC subject가 달라지므로 현재 branch trust와 함께 사용하지 않는다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. 실제 IAM·GitHub·workflow는 변경하지 않았다.
- 2026-08-21 사용자가 제공한 `tosunsaeng-ai:5` Task Definition JSON을 민감값 없이 구조적으로 확인했다. `taskRoleArn`은 null이므로 AI Task Role은 생성하지 않는다. `executionRoleArn`은 기존 `arn:aws:iam::889384901776:role/tosunsaeng-ecs-execution-role`이며 현재 ECR image pull, CloudWatch logging과 세 가지 API credential의 ECS secret injection이 정상 작동하므로 새 Execution Role도 생성하지 않고 재사용한다. GitHub deploy role의 `iam:PassRole` resource에는 이 execution role ARN 하나만 넣는다. Fargate/awsvpc Task는 family `tosunsaeng-ai`, revision 5, CPU 2048, memory 4096이며 다섯 essential container가 동일 image digest를 사용한다. `ai-api`에는 localhost port 8000 `/ready` health check가 interval 30s, timeout 5s, retries 3, startPeriod 60s로 실제 등록되어 있고 worker에는 health check가 없다. public `HEALTH_URL`은 불필요하다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. AWS·GitHub·코드는 변경하지 않았고 Secret 값은 조회·기록하지 않았다.
- 2026-08-21 사용자가 AI Task Role과 Task Execution Role을 새로 만들려 했으나, 이미 정상 실행 중인 `tosunsaeng-ai-service`의 현재 Task Definition에서 `taskRoleArn`과 `executionRoleArn`을 먼저 확인·재사용해야 한다고 정리했다. Execution Role은 ECR pull·CloudWatch Logs·ECS secret 주입을 담당하고 기존 실행 Task에 거의 확실히 존재한다. Task Role은 AI 애플리케이션이 AWS API를 직접 호출할 때만 필요하며 null이면 새로 만들 필요가 없다. 새 역할이 실제로 필요한 경우 둘 다 `ecs-tasks.amazonaws.com` trust를 사용하고, execution role에는 `AmazonECSTaskExecutionRolePolicy`와 현재 Task Definition이 참조하는 Secret/KMS에만 최소 권한을, task role에는 애플리케이션이 실제 호출하는 AWS resource 권한만 부여한다. 새 역할 적용은 Task Definition revision과 Service 배포 검증이 필요한 별도 변경이며 GitHub deploy role의 `iam:PassRole`은 최종 사용 role ARN에만 제한한다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. AWS·GitHub·코드는 변경하지 않았다.
- 2026-08-21 지금까지 확인한 실제 AI ECS 값을 반영해 `web-ai` GitHub Actions 전환 절차를 통합 정리했다. 확정값은 account/region 기반 ECR repository `tosunsaeng-ai`, cluster `tosunsaeng-staging-cluster`, service `tosunsaeng-ai-service`, containers `ai-api`, `ai-worker-1`~`ai-worker-4`다. 기존 offline test·Compose validation은 유지하고 Docker Hub·EC2 SSH 단계를 GitHub OIDC→ECR SHA push→현재 Task Definition 조회→다섯 container 순차 render→Service 단일 deploy/stability 검증으로 교체한다. public AI DNS가 없으므로 `HEALTH_URL` curl은 제외한다. AWS OIDC provider는 기존 backend 설정을 재사용할 수 있지만 `web-ai` repo/main subject trust, AI ECR/ECS 권한과 현재 Task Definition의 execution/task role에 제한된 `iam:PassRole`을 가진 deploy role이 필요하다. 실제 role ARN 두 개와 GitHub `AWS_ROLE_ARN` 값은 AWS에서 확인해야 하며 Secret·Token으로 문서화하지 않는다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. 실제 workflow·AWS·GitHub는 변경하지 않았다.
- 2026-08-21 사용자가 `ai-api`와 `ai-worker-1`~`ai-worker-4`가 모두 동일한 `tosunsaeng-ai` ECR repository와 동일 current image digest를 사용한다고 확인했다. 따라서 AI GitHub Actions image 전략은 하나의 Docker build, `tosunsaeng-ai:${GITHUB_SHA}` ECR push, 동일 image URI를 다섯 container에 순차 render, 최종 Task Definition 하나를 `tosunsaeng-ai-service`에 한 번 deploy하는 것으로 확정됐다. 기존 Task Definition의 API/worker별 command, environment, secret, mount, resource 설정은 render action이 보존한다. public health URL은 사용하지 않고 `ai-api` container readiness와 ECS service stability를 사용한다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. AWS·GitHub·코드는 변경하지 않았다.
- 2026-08-21 사용자가 현재 AI container image URI를 제공해 ECR repository를 확정했다. registry 뒤 `/`와 digest `@sha256:` 사이 값에 따라 `ECR_REPOSITORY=tosunsaeng-ai`다. 제공된 digest는 현재 배포 image의 immutable 식별자이며 workflow repository 값에는 포함하지 않는다. `ai-worker-1`~`ai-worker-4`도 같은 repository/digest인지 Task Definition에서 대조한 뒤 다섯 container를 동일 새 SHA image URI로 render한다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. AWS·GitHub·코드는 변경하지 않았고 image digest 외 Secret·Token은 기록하지 않았다.
- 2026-08-21 사용자가 AI workflow의 `ECR_REPOSITORY` 확인 위치를 질문했다. ECS `tosunsaeng-ai-service`가 사용하는 Task Definition revision에서 `ai-api` container의 `Image URI`를 확인하고, registry hostname 다음 `/`부터 tag `:` 또는 digest `@sha256:` 전까지를 repository name으로 사용한다. 예를 들어 `<account>.dkr.ecr.ap-northeast-2.amazonaws.com/tosunsaeng-web-ai:<tag>`이면 `ECR_REPOSITORY=tosunsaeng-web-ai`다. worker 4개의 Image URI도 같은 repository인지 함께 확인해야 하며, 다르면 하나의 image로 일괄 render하지 않는다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. AWS·GitHub·코드는 변경하지 않았다.
- 2026-08-21 사용자가 `tosunsaeng-ai-service`의 단일 Task에 `ai-api`, `ai-worker-1`~`ai-worker-4` 다섯 컨테이너가 있고 `ai-api` 로그에서 localhost `/ready` 200 응답을 확인했다고 제공했다. 자동 배포는 service를 한 번만 갱신하되, 동일 ECR image를 사용하는 것이 확인되면 `amazon-ecs-render-task-definition`을 `ai-api`→worker 1~4 순서로 체인해 다섯 container image를 모두 같은 immutable SHA URI로 교체하고 마지막 rendered Task Definition만 deploy해야 한다. `CONTAINER_NAME=ai-api` 한 개만 render하면 worker가 구버전 image로 남을 수 있다. localhost readiness가 이미 동작하므로 public DNS가 없는 현재 구성에서는 `HEALTH_URL` curl을 제거하고 ECS container health와 `wait-for-service-stability`를 사용한다. Task Definition에서 다섯 container의 current image repository 동일성과 `healthCheck` 등록 상태는 최종 확인이 필요하다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. AWS·GitHub·코드는 변경하지 않았다.
- 2026-08-21 사용자가 공통 cluster `tosunsaeng-staging-cluster`와 AI ECS Service `tosunsaeng-ai-service`를 확정하고 workflow의 `CONTAINER_NAME`, `HEALTH_URL` 확인 위치를 질문했다. `CONTAINER_NAME`은 Service가 참조하는 Task Definition revision의 `Container definitions` 또는 실행 Task의 `Containers`에서 `Name`을 확인한다. `HEALTH_URL`은 Task Definition 값이 아니라 public ALB listener/target group health path와 Route 53 record가 있을 때만 조합한다. 공개 조회에서 `ai.to-teacher.com`은 DNS 해석되지 않아 현재 health URL로 사용할 수 없다. Learning Core 기본 `AI_SERVER_URL=http://tosunsaeng-ai:8000`을 고려하면 Service Connect/Cloud Map 내부 연결일 가능성이 높으며, 이 경우 workflow는 public curl 대신 container `healthCheck`와 ECS service stability/healthy task 확인을 사용해야 한다. AWS Console은 여전히 로그아웃 상태라 실제 container name과 service networking은 확정하지 못했다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. AWS·GitHub·코드는 변경하지 않았다.
- 2026-08-21 `web-ai`가 Learning Core와 같은 `tosunsaeng-staging-cluster`에 배포됐다는 사용자 확인을 바탕으로 자동 배포에 필요한 나머지 실값 조회를 시도했다. 로컬 Learning Core workflow에서 region `ap-northeast-2`, cluster, GitHub OIDC와 현재 task definition을 조회해 ECS Service를 갱신하는 패턴은 확인했다. 그러나 로컬 AWS CLI에는 credential이 없고 GitHub CLI 인증은 만료됐으며, in-app AWS Console과 비공개 GitHub 저장소도 로그아웃 상태라 AI ECS Service/Task Definition/container/ECR/deploy role 권한의 실제 값은 확인하지 못했다. AWS 로그인 탭을 사용자 handoff로 열어두었으며 로그인 뒤 읽기 전용으로 AI Service→Task Definition→container/image/ECR→deployment config→IAM role→GitHub workflow/variables 순으로 대조할 수 있다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. AWS·GitHub·코드는 변경하지 않았다.
- 2026-08-21 사용자가 `web-ai`도 이미 Learning Core와 유사하게 ECS에 정상 배포되어 있다고 정정했다. 따라서 Redis/EFS/ALB/ECS Service를 신규 설계·생성하는 작업은 현재 범위가 아니다. 필요한 변경은 기존 `web-ai` workflow의 Docker Hub·EC2 SSH 재기동 단계를 제거하고, GitHub OIDC로 AWS deploy role을 assume한 뒤 ECR에 immutable SHA image를 push하고 현재 AI ECS Service의 Task Definition image를 render·register·deploy하여 service stability와 health를 확인하는 자동화다. AI가 API/worker 단일 service면 한 번, 별도 service면 동일 image로 각 service를 순차 갱신한다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. 실제 AWS·GitHub Actions·`web-ai` 코드는 변경하지 않았다.
- 2026-08-21 `web-ai`의 기존 EC2 Docker Compose·Docker Hub·SSH 배포를 ECS/ECR·GitHub OIDC 방식으로 전환하는 설계를 정리했다. ECS에서는 cluster 자체가 아니라 API·worker 별 Task Definition revision과 Service를 갱신한다. 현재 AI 작업이 Redis에 로컬 업로드 경로를 전달하므로 workflow만 바꾸면 분리된 Fargate task가 파일을 공유하지 못한다. 1차 전환은 ElastiCache Redis와 EFS access point를 공용으로 사용하고 `/app/data` 전체를 덮지 않는 별도 mount path를 평가 관련 환경변수에 연결한다. 이후 API 1개와 worker 4개를 별도 ECS Service로 구성하고 동일 immutable ECR SHA image를 GitHub OIDC deploy role로 배포한다. execution role, application task role, GitHub deploy role을 분리하고 staging(`develop`)·production(`main`)의 cluster/service/secret/role을 격리한다. 관련 Summary 멱등·generation 범위는 기존 `TMI-25`이며 신규 인프라 Jira 키는 제공되지 않았다. 실제 `web-ai`, AWS, GitHub Actions와 애플리케이션 외부 계약은 변경하지 않았다.
- 2026-08-21 전체 앱 흐름을 현재 구현과 확정 계획으로 나눠 재정리했다. 현재 Learning Core는 JWT/Legacy 사용자 식별, 순환 시험지 배정, S3 직접 업로드, `QuestionGradingJob`/`SummaryGradingJob` 기반 비동기 AI 채점·멱등 Callback, Polling·결과·이력·재답변 조회와 시험 단위 채점 복구를 구현한 상태다. 1차 앱의 목표 흐름은 Identity 로그인·verified phone(`TMI-90`, `TMI-95`, `TMI-98`) → Billing/Entitlement의 무료 1회·인앱결제·사용권 reserve/confirm → Learning Core Session/AttemptGroup → 채점 완료 또는 무료 replacement → 결과 조회이며, Learning Core의 기존 인증·채점 기반 작업에는 `TMI-14`, `TMI-25`, 시험 배정에는 `TMI-31`이 반영돼 있다. Billing 연동, `AttemptGroup`/R3, Learning Core·Billing의 `UserMerged` consumer, Identity multi-consumer fan-out과 staging/prod 배포 자동화는 아직 계획·후속 구현 범위이고 Billing/Learning Core 후속 Jira 키는 제공되지 않았다. 기존 공개 API·DTO·`BaseResponse`, `retryCount`, Redis/S3 Key, AI/Callback `user_id=examId` 계약은 유지한다.
- 사용자가 Billing/Entitlement를 새로운 하나의 배포 서비스로 시작하고, 10 credits=시험 1회, 5천원=5 credits, 1만원=10 credits, 3만원=3일 무제한+3일 출석 시 하루 연장, 5만원=100 credits, 첫 구매 2배, 주간 연속 로그인 `0,1,1,1,2,2,3`, 추천 code 10 credits, coupon별 credits와 검증된 휴대전화 번호당 무료 1회를 제품 기본안으로 정했다. 관련 Identity Jira는 `TMI-95`, `TMI-98`이며 Billing/Learning Core 후속 키는 미제공이다.
- `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`에 immutable grant ledger, unlimited/free entitlement, 5분 reservation, Session commit-confirm saga, PG webhook 멱등 상태, Billing `UserMerged` consumer와 TrialClaim 보존 권장안을 기록했다.
- 결제 채널은 Apple In-App Purchase와 Google Play Billing만 사용하는 것으로 확정했다. 웹 checkout·웹 PG는 현재 범위에서 제외하고 향후 별도 제품 결정으로만 추가한다. Billing은 두 store provider adapter와 단일 주문·entitlement 원장을 사용하며, 배포 국가별 store 정책·product ID·상품 유형·실제 가격 구간은 출시 전에 확정해야 한다.
- 첫 구매 2배는 verified phone 기준 첫 credit 상품에 적용하고 `CREDIT_100`은 총 200 credits를 지급한다. unlimited pass를 먼저 구매해도 자격을 소진하지 않으며 merge·탈퇴·환불로 자격을 다시 열지 않는다.
- 3만원 pass는 구매 후 30일 안의 첫 reserve부터 72시간, 서로 다른 KST 3일의 Billing check-in 완료 시 24시간 한 번 연장으로 확정했다. 재구매 pass는 별도 보존하고 활성 pass 종료 뒤 활성화하며 미활성·미사용 pass만 환불한다.
- 추천 보상은 입력자와 추천인에게 각각 10 credits를 지급하되 입력자의 verified phone과 첫 유료 인앱결제 `CAPTURED` 후 한 번만 지급한다. phone당 code 입력 1회, self-referral 금지와 abuse 보류, 첫 결제 환불 시 revoke/debt 규칙을 적용한다.
- 아직 확정할 product 세부사항은 7일 streak 반복/reset과 paid/promotional 만료, 부분 사용 후 환불·chargeback, optional `Idempotency-Key`, coupon stacking/한도/만료, TrialClaim 법무 보존과 번호 재할당 정책이다.
- 확정 계약의 코드 영향 검토 결과, Learning Core `UserMerged` 전용 계획은 그대로 유지하되 시험 생성의 Billing reserve→Session commit→confirm/cancel/reconcile를 다루는 별도 구현 계획·Jira가 필요하다. 현재 `startNew()`은 기존 활성 Session을 abandon하고 새 Session을 즉시 insert하므로 operation 멱등성과 reservation metadata·durable reconciliation을 함께 설계해야 한다.
- Identity의 PhoneEligibilityBinding publisher는 Billing consumer를 이미 전제하므로 상품·인앱결제·추천을 위한 payload 확장은 필요 없다. 다만 현재 `UserMerged` publisher는 단일 endpoint/audience와 event당 delivery status 하나만 지원하므로 Learning Core와 Billing의 독립 delivery를 위해 consumer별 delivery state fan-out 또는 승인된 durable broker가 필요하며 direct HTTPS consumer별 delivery를 권장한다.
- 권장 실행 순서는 Identity→Billing→Learning Core의 완전 직렬 개발이 아니다. Phase 0에서 phone binding, multi-consumer `UserMerged`, reserve/confirm/cancel, store transaction과 idempotency/error 계약 및 Jira를 동결하고, Identity fan-out·Billing foundation·Learning Core 계획/consumer를 feature OFF로 병렬 구현한다. staging에서는 consumer endpoint와 보안을 먼저 배포한 뒤 phone eligibility, 무료시험 E2E, `UserMerged` fan-out, store sandbox, Billing enforcement 순으로 검증하고 signup/Guest merge production flag를 마지막에 연다.
- `Idempotency-Key`와 Billing 오류 계약의 선택지를 `BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`에 추가했다. 권장 패키지는 공개 API header optional·신규 앱 UUID v4 필수, user/operation scope, Session 수명 동안 mapping과 terminal command 7일, 완료 결과 재사용·processing 409다. 오류는 pass-through하지 않고 사용권 부족 402, eligibility/payment/exam processing 409, rate limit 429, Billing 장애·confirm 불명 503과 `Retry-After`로 mapping하며 같은 key 재시도와 reconciliation을 사용한다. 아직 사용자 승인은 받지 않았다.
- 사용자는 이어풀기를 제외하고 완료 전 무제한 무료 replacement(R3)를 확정했다. 최초 시작에서 entitlement를 한 번 소비해 OPEN AttemptGroup을 만들고, restart마다 기존 Session을 `ABANDONED_RESTARTED`로 종료한 뒤 새 key·새 examId를 같은 group/consumption과 동일 mockExamId에 연결한다. 서로 다른 restart는 다른 key를 쓰되 같은 restart의 response loss 재전송은 같은 key로 같은 Session에 수렴하므로 R3에서도 idempotency는 필요하다.
- 차감은 Billing 5분 reserve→Learning Core Session commit→Billing confirm에서 최초 한 번 확정한다. submit 미완료 장애는 OPEN group 무료 replacement, 모든 필수 최초 submit 접수 뒤에는 GRADING에서 기존 채점 복구, 필수 피드백·유효 점수·Summary가 조회 가능할 때만 COMPLETED다. grading retry/reconciliation이 최종 실패하면 RETAKE_AVAILABLE로 전환해 같은 consumption으로 무료 새 Session을 열며, retake조차 제공할 수 없는 장기 장애에는 원 credits/free entitlement를 멱등 복원한다. 재화 자동 순서는 unlimited→free once→promotional→paid를 권장하며 아직 사용자 승인은 받지 않았다.
- `Idempotency-Key`는 결제·AttemptGroup 식별자가 아니라 한 번의 Session create/restart command 식별자다. 같은 사용자 동작의 timeout·response loss 재전송은 같은 key로 같은 Session을 반환하고, 사용자가 의도한 다음 restart는 새 key·새 Session을 만든다. R3 무료 replacement에서도 key가 없으면 한 번의 restart 재전송이 여러 Session과 Billing authorization·Redis/S3/Job을 만들 수 있으므로 유지한다.
- 배포 환경은 현재 `tosunsaeng-staging-cluster`를 업데이트 전 통합 검증용으로 유지하고 신규 `tosunsaeng-prod-cluster`를 실제 사용자용으로 추가하는 방향으로 정했다. 초기에는 같은 AWS account·VPC를 사용할 수 있지만 ECS service/task definition/target group/log, 도메인과 workload credential, task role/secret, MongoDB·Redis·S3 mutable data boundary, Apple/Google sandbox·production 설정을 환경별로 격리한다. staging에서 검증한 동일 immutable image digest만 production으로 승격하며 production signup/merge/Billing flag는 마지막에 연다. 제공된 AWS Console 링크는 로그인 화면으로 전환되어 기존 cluster의 실제 service·capacity provider·network inventory는 확인하지 못했다.
- 브랜치별 자동 배포는 `develop` merge/push→`tosunsaeng-staging-cluster`, protected `main` PR merge→`tosunsaeng-prod-cluster`로 구성한다. 현재 `deploy-staging.yml`은 `main`에서 staging을 배포하므로 trigger 변경이 필요하고, production은 별도 workflow·GitHub Environment·OIDC role·cluster/service/health URL과 환경별 secret/variable을 사용해야 한다. 기본 자동화는 각 환경 test/build/deploy이고, 더 강한 배포 동일성이 필요하면 staging 검증 ECR image digest를 production에 그대로 승격한다. Billing·인프라 후속 Jira 키는 아직 없다.
- 1차 업데이트의 SNS 로그인·결제·검증된 휴대전화 번호당 무료 모의고사 1회 범위를 Identity의 기존 `TMI-90`, `TMI-95`, `TMI-98` 계약과 대조했다. Billing/Learning Core 후속 Jira 키는 제공되지 않았다.
- 결제와 무료 1회를 1차 production 범위에 포함한다면 Billing 서버는 릴리스 이후 작업이 아니라 선행·병렬 의존성이다. 초기에는 결제와 Entitlement를 별도 서비스 둘로 나누지 않고 Billing/Entitlement 하나가 payment, TrialClaim, Entitlement, reserve/confirm/cancel/reconcile와 merge 이전을 소유하는 방향을 권장한다.
- 무료 정책은 userId당 1회가 아니라 검증된 휴대전화 번호당 1회다. Identity는 consumer-scoped eligibility binding만 생산하고 Billing/Entitlement가 TrialClaim unique와 사용권 원장을 소유하며, Learning Core는 시험 생성 전 reserve하고 성공 후 confirm한다.
- 검증 번호당 1회는 실제 자연인당 1회를 완전히 보장하지 않는다. 여러 번호와 번호 재할당까지 막아야 한다면 KYC·abuse·보존 정책이 추가되므로 1차 제품 요구사항에서 의미를 명시해야 한다.
- 현재 `UserMerged` 최종 계획의 애플리케이션 코드 변경 주 대상은 Learning Core다. 그러나 production 연동에는 Identity의 불변식/status 계약·workload/publisher 설정 또는 보완, 인프라 TLS/network/Mongo, staging E2E가 필요하며 Billing의 entitlement merge consumer는 별도 범위다.
- 상세 책임 경계와 1차 업데이트 병렬 track을 `docs/codex/FIRST_RELEASE_BILLING_BOUNDARY_REVIEW.md`에 기록했다.
- 확정된 `UserMerged` C1~C11 계약을 실제 구현 단위로 내린 최종 계획을 `docs/codex/USER_MERGED_CONSUMER_IMPLEMENTATION_PLAN.md`에 추가했다. 별도 Jira 이슈 키는 제공되지 않았다.
- 구현 순서는 외부 credential·Mongo 지원 확인, Transaction/guard foundation, 기존 writer·Callback 전환, migration/backfill/index, workload security/internal endpoint, inbox/ownership migration, replica-set 동시성 테스트, staging E2E/P99, 제한 rollout이다. endpoint부터 먼저 구현하지 않는다.
- 모든 user-owned Mongo command는 guard와 business mutation을 같은 Transaction에서 commit하고 S3 network·AI·Redis는 commit 후 실행한다. PUT Presigned URL의 로컬 서명만 guard와 직렬화해 merge 뒤 신규 capability 발급 경합을 막는다.
- direct consumer는 source/target guard, 활성 시험 정책, `exam_sessions`/`exam_results`/`exam_summaries` owner rewrite, source MERGED deny와 inbox PROCESSED를 하나의 Transaction으로 처리한다. Callback은 Session current owner를 다시 읽고 guard/result/Job을 같은 Transaction에 둔다.
- production 유사 staging의 direct Transaction 성능 gate 실패 시 timeout 연장이나 hybrid 처리 없이 C2-B durable inbox + worker 계약으로 개정한다. 첫 processed merge 뒤에는 guard-unaware 구버전으로 rollback하지 않는 runbook을 필수로 한다.
- 사용자가 `UserMerged` 계약 결정 가이드의 C1~C11 권장 기본 패키지를 승인해 구현 방향과 C4-A+C6-A 위험 수용 정책을 확정했다. 별도 Jira 이슈 키는 제공되지 않았다.
- target 활성 시험 우선·source-only 활성 이전, 모든 history 합집합 보존, merge 전 발급된 S3 PUT URL의 최대 5분 잔여 capability 수용이 제품 정책으로 확정됐다. Learning Core API의 source actor 권한은 merge commit 즉시 폐기하되 S3 capability 자체는 즉시 취소되지 않는 범위를 명시한다.
- source/target 양쪽 guard, Callback 전체 Transaction, 상충 event fail-closed, 권장 HTTP status 표, 인프라 TLS/network 제한과 publisher OFF 상태의 단계적 writer 전환을 구현 기준으로 확정했다.
- direct Transaction은 조건부 확정이다. Mongo 지원과 production 유사 staging 성능 기준을 통과하면 유지하고, 실패하면 timeout 연장이나 hybrid 대신 durable inbox + worker로 계약을 개정한다.
- 아직 운영 활성화 승인은 아니다. C1 실제 issuer/JWKS/audience/principal/TTL/rotation, Identity 인계서 반영, Mongo/P99, TLS/network와 staging E2E가 남아 있다. 상세 확정 상태는 `docs/codex/USER_MERGED_CONTRACT_DECISIONS.md`에 기록했다.
- `UserMerged` 선행 계약을 실제로 확정하기 위한 선택지·장단점·권장 조합·승인 절차를 `docs/codex/USER_MERGED_CONTRACT_DECISIONS.md`에 정리했다. 별도 Jira 이슈 키는 제공되지 않았다.
- 필수 기술 불변식은 source/target 양쪽 guard, Callback의 Session/guard/result/Job 단일 Transaction, 상충 event fail-closed다. 제품·보안이 직접 선택할 핵심은 활성 시험 처리와 merge 전 발급된 S3 PUT URL의 최대 5분 잔여 capability 수용 여부다.
- 권장 기본안은 target 활성 시험 우선·source-only 활성 이전·모든 history 합집합 보존·5분 잔여 위험 명시적 수용이다. source 유래 S3 write 가능성도 0이어야 한다면 source 활성을 항상 abandon하거나 revocable/nonce 업로드를 별도 설계해야 한다.
- direct Transaction은 아직 무조건 확정하지 않고 production 유사 staging에서 예시 P99 2초 이하 등 공동 승인 기준을 통과할 때만 채택한다. 실패하면 timeout 연장이나 hybrid가 아니라 durable inbox + worker 계약으로 개정한다.
- 현재 확인 시점 HEAD는 `98730c9`이며 작업 시작 시 worktree는 clean이었다.
- Identity Service의 `UserMerged` schema version 1 인계서를 현재 Learning Core 코드와 대조 검토했다. 애플리케이션·설정·테스트 코드는 변경하지 않았으며 별도 Jira 이슈 키는 제공되지 않았다.
- 검토 결론은 “구현 가능, 선행 결정 필요”다. workload credential의 모든 TBD, source/target 양쪽 guard 획득, 활성 시험 충돌 정책, Callback stale-owner 경합, 기존 Presigned PUT URL의 최대 5분 잔여 권한, Mongo Transaction/P99 검증이 endpoint 구현 전 차단 사항이다.
- 현재 직접 userId를 가진 컬렉션은 `exam_sessions`, `exam_results`, `exam_summaries`다. Question/Summary Job, Azure/SpeechAce 결과, Redis와 S3는 `examId` 간접 귀속이므로 rewrite하지 않고 Session ownership을 유지하는 방향이 맞다.
- 기존 공개 API·DTO·`BaseResponse`, `retryCount`, Redis/S3 Key, AI·Callback `user_id=examId` 계약은 변경할 필요가 없으며 internal endpoint는 사용자 JWT와 분리된 workload 전용 SecurityFilterChain을 사용해야 한다.
- 상세 inventory, 차단 사항과 권장 구현 순서는 `docs/codex/USER_MERGED_CONSUMER_REVIEW.md`에 기록했다. 이번 검토에서는 테스트를 실행하지 않았고 Git/Jira 쓰기 작업도 수행하지 않았다.
- 확인 시점 HEAD는 `52634a9`이며 이번 분석에서는 애플리케이션·설정·테스트 코드를 변경하지 않았다.
- 기존 사용자 작업인 Actuator 의존성·Health 설정을 보존한 상태에서 AWS S3 자격 증명 구성을 Default Credentials Provider Chain으로 전환한 미커밋 변경이 작업 트리에 있다.
- `S3Config`의 프로젝트 전용 Access Key/Secret Key 주입과 static credential 생성을 제거했다. `S3Client`와 `S3Presigner`는 공유 `DefaultCredentialsProvider`를 사용하며 기존 Region·Bucket property, Object Key와 Presigned URL 동작은 유지한다.
- `application.yml`과 test profile에서 static credentials 설정을 제거했고 credential 없는 `.env.example`, 로컬 profile/Docker/ECS Task Role 문서를 추가했다. AWS SDK는 BOM `2.29.52`로 `s3`, `sso`, `ssooidc`, `sts`를 함께 관리해 일반 Profile, 현대식 `sso_session`, Assume Role와 Web Identity 경로를 지원한다.
- Spring Cloud AWS 자동 구성과 S3 Health Indicator는 없다. Credential 환경변수와 Profile mount 없이 새 linux/amd64 이미지를 실행해 `/actuator/health` HTTP 200·`UP`을 확인했고 검증 컨테이너는 SIGTERM으로 종료했다.
- S3 credential 관련 테스트는 classpath 5개, S3 Bean 7개, 설정 계약 4개, Health 1개로 총 17개다. 최신 `./gradlew clean test`는 XML 기준 전체 Java 248개, failures/errors/skipped 0개다.
- 운영 코드·설정에서 프로젝트 전용 AWS key 이름, `StaticCredentialsProvider`, `AwsBasicCredentials`, credentials property 잔여가 없고 실제 AWS Key 패턴도 발견되지 않았다. 설명과 금지 계약 테스트의 문자열만 의도적으로 남아 있다.
- Gradle `runtimeClasspath`의 AWS SDK 모듈은 transitive `auth`·`profiles`를 포함해 모두 `2.29.52`로 해석되며 혼합 버전이 없다. 필요한 SSO OIDC와 STS 클래스도 classpath 테스트로 확인했다.
- native Linux 문서는 host UID/GID 실행, image app group `999` 추가, `HOME=/app`과 read-only Profile mount를 사용한다. 현재 Docker Desktop에서 owner-only 빈 모의 Profile·SSO cache 접근, Java `user.home=/app`, non-root, JAR 읽기와 `/tmp` 쓰기를 확인했지만 실제 native Linux host 검증은 남아 있다.
- 최종 MEDIUM finding 후 README의 로컬 Docker SSO 정책을 보완했다. macOS와 native Linux 모두 각 개발 세션 전에 host에서 `aws sso login`과 stdout을 숨긴 credential 검증을 수행하고, 컨테이너는 host `.aws`를 read-only로 읽기만 한다. 컨테이너 내부 token cache 갱신은 보장하지 않으며 만료 시 host 재로그인 후 컨테이너를 재시작한다. 일반 Shared Credentials Profile의 만료 정책과 SSO Profile 절차를 구분했고 ECS Task Role 흐름에는 영향이 없다.
- `.dockerignore`는 root·중첩 `.aws`를 제외하며 새 이미지에도 `/app/.aws`가 없다. 실제 AWS Profile/SSO를 사용한 S3 Smoke Test와 ECS Task Role 실환경 검증은 수행하지 않았다.
- 이번 credential 후속 작업에 별도 Jira 이슈 키는 없으며 Codex는 commit·push·PR 생성과 Jira 댓글·필드·상태 변경을 수행하지 않았다.
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

## Current question prompt API

- 별도 Jira 이슈 키 없이 `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`를 additive로 추가했다. JWT 모드에서는 기존 SecurityFilterChain이 Bearer 인증을 요구하며 `JwtCurrentUserProvider`가 검증된 JWT `sub` UUID를 실제 사용자 ID로 사용한다. local/test legacy 인증 정책은 변경하지 않았다.
- 서비스는 `ExamSession`을 조회하고 현재 사용자와 `ExamSession.userId`를 비교한 뒤에만 `ExamSession.mockExamId`의 MockExam을 조회한다. legacy null/blank `mockExamId`는 기존 `mock_exam_003` fallback을 유지하고 다른 사용자의 시험은 `COMMON403`으로 차단한다.
- 응답은 세션 생성에서 사용하던 `QuestionDTO`를 그대로 재사용한다. Part, 문항 번호, text/reference/intro, image/table, 준비·답변 시간과 문제 음성 URL을 반환하며 Part 3은 기존 안내 음성 URL도 제공한다. 내부 examId·userId·mockExamId와 문제지 내부 Mongo ID는 응답에 추가하지 않았다.
- 문제 음성 Key와 60분 만료 정책은 기존 `questions/{mockExamId}/q_{questionNumber}.wav`를 유지하고 Part 3 안내 음성도 기존 Key를 유지한다. retryCount, 사용자 제출 S3 Key, AI·Callback, Redis와 채점 계약은 변경하지 않았다.
- URL 계약·Part별 매핑·선택된 시험지·문항 없음·소유권 선검증·JWT 401/403/200 테스트를 추가했다. 집중 테스트 55개와 `./gradlew clean test` 전체 Java 245개가 failures/errors/skipped 0개로 성공했다.
- Git commit·push·PR 생성과 Jira 댓글·필드·상태 변경을 수행하지 않았고 Secret, Token, 실제 URI와 Presigned URL을 기록하지 않았다.

## Current question result model-answer audio change

- 이번 작업에 별도 Jira 이슈 키는 제공되지 않았으며 Jira 댓글·필드·상태를 변경하지 않았다. Git commit·push·PR 생성도 수행하지 않았다.
- 기존 문항 단건 `GET /api/v1/exams/{examId}/questions`와 필수 `questionNumber`, 선택·기본값 0인 `retryCount`, 별도 `/summary` 계약을 유지하면서 `result.question.modelAnswer`를 additive 선택 필드로 추가했다.
- 최종 `ModelAnswerResponse`는 `audioUrl`과 `spokenWordSequence` 두 필드만 가진다. 응답 DTO·Builder·JSON·OpenAPI·README 예시에 모범답안 문장 필드는 없고 null placeholder도 직렬화하지 않는다.
- `modelAnswer`는 소유권 확인 후 요청한 canonical retryCount의 `ExamResult`가 존재하고, 원본 문제의 `partNumber=1`이면서 `questionNumber=1` 또는 `2`이며 해당 시험지 메타데이터가 있을 때만 조립한다. 기존 상태 정책은 matching 결과 문서를 채점 완료 증거로 본다. 제출 전·처리 중·실패·존재하지 않는 회차와 다른 문항에서는 `PartResultDTO`의 `NON_NULL` 정책으로 필드 자체를 생략한다.
- MongoDB `model_answer` 컬렉션과 데이터는 조회·수정·삭제하지 않았다. 문항 단건 응답 경로는 해당 컬렉션이나 모범답안 텍스트에 의존하지 않는다.
- `mock_exam_004`의 q1 55개·q2 53개 모범답안 단어 시퀀스를 classpath 내부 메타데이터로 추가했다. 내부 record에서 기존 응답용 `SpokenWordDTO`로 명시 매핑해 index·segmentIndex·wordIndex, Long offset·duration, Double 발음 점수와 errorType을 그대로 유지하며 사용자 녹음 시퀀스와 분리한다.
- 완료 결과가 확인된 뒤에만 `ExamSession.mockExamId`를 사용해 `{mockExamId}/part1_a{questionNumber}.wav`를 결정하고 기존 `S3Presigner`로 60분 Presigned GET URL을 생성한다. 결과가 없으면 model-answer catalog 조회, 사용자·모범답안 S3 Key 조립과 Presign을 모두 생략한다. HeadObject, 새 credential provider, S3 Key DB 저장은 추가하지 않았다.
- `mock_exam_004` 외 시험지는 다른 시험의 시퀀스를 재사용하지 않는다. 해당 시험지의 q1/q2 모범답안 제공이 필요하면 올바른 시퀀스 메타데이터 파일을 먼저 추가해야 한다.
- 제출 전, 존재하지 않는 retry와 처리 중 결과 없음의 조기 차단 테스트 3개를 추가했다. 완료 Q1·Q2, 다른 Part, 타 사용자 403, feedback·retryScores·retryFeedbackScores 회귀를 포함한 집중 테스트 44개와 전체 Java 248개가 failures/errors/skipped 0개로 성공했고 `git diff --check`도 성공했다. 실제 AWS, MongoDB, Redis, Python AI와 Sentry는 호출하지 않았다.
- 기존 feedback·azureFeedback·완료 결과의 사용자 audioUrl·사용자 spokenWordSequence·questionInfo/referenceText/audioUrl, JWT·Guest JWT·소유권, Redis·채점 멱등성, AI/Callback `user_id=examId`, Default Credentials와 Health 계약은 변경하지 않았다. 결과가 없는 회차는 사용자 음성 및 모범답안 Presign을 모두 생략한다. 기존 미커밋 AWS/Actuator 작업도 보존했으며 S3 인증·Docker 파일은 수정하지 않았다.

## Current grading client-source implementation

- 이번 구현에 별도 Jira 이슈 키는 없다. Git commit·push·PR 생성과 Jira 댓글·필드·상태 변경도 수행하지 않았다.
- 앱 Learning Core와 기존 웹 POC 백엔드가 별도라는 확정에 따라 공개 submit API나 `submitQuestion` 시그니처에는 source를 추가하지 않았다. 앱 전용 `GradingDispatchService.dispatchQuestion`이 Python AI multipart에 `client_source=app`을 고정 추가한다.
- 최초 submit, 시험 단위 retry와 stale recovery가 모두 같은 `dispatchQuestion` 경로를 사용하므로 `QuestionGradingJob`·`QuestionDispatchClaim`에 중복 source 필드를 저장하지 않아도 매 AI 문항 요청에 동일하게 전달된다.
- 기존 AI `user_id=examId`, mockExam/part/question/retry/audio, 결정적 Job ID와 `Idempotency-Key`를 유지한다. 전체 요약 AI Body와 Callback JSON에는 `client_source`를 추가하지 않았고, source를 JWT 인증·시험 소유권 판단에 사용하지 않는다.
- `GradingDispatchServiceTest`가 문항 multipart의 정확한 `client_source=app`과 Summary Body의 source 미포함을 함께 검증한다. 집중 테스트와 `./gradlew clean test`가 성공했고 XML 기준 Java 229개, failures/errors/skipped 0개이며 실제 AWS·Python AI는 호출하지 않았다.
- Python AI는 신규 multipart 필드 `client_source`를 읽고 값 `app`을 처리해야 한다. 기존 웹 POC 요청의 필드 누락을 `web`으로 해석하는 정책은 Python 측에서 별도로 반영·검증해야 한다.

## Current grading service responsibility note

- `ExamGradingService`는 채점 자체를 계산하는 서비스가 아니라 Question/Summary Job 상태를 관리하는 orchestration 계층이다. 기존 결과 확인, 결정적 Job 생성, optimistic claim, retry 가능 여부·시도 한도 판단, 실패/완료 전이, 전체 상태 계산과 Redis projection, Summary 시작 조건을 담당한다.
- `GradingDispatchService`는 이미 claim된 immutable 요청을 외부로 운반하는 integration 계층이다. S3 GET Presigned URL 생성과 음성 다운로드, Python AI용 Question multipart·Summary JSON·`Idempotency-Key` 조립, HTTP POST만 담당하고 Mongo Job 상태나 retry 정책을 결정하지 않는다.
- 호출 방향은 Controller → `ExamServiceImpl`의 소유권 확인 → `ExamGradingService`의 상태·정책 결정 → `GradingDispatchService`의 외부 I/O → Python AI다. 전송 실패가 발생하면 Dispatch 서비스는 예외를 올리고 Grading 서비스가 claim attempt와 Job 상태를 안전하게 실패 전이한다.
- `client_source=app`은 앱 전용 백엔드의 AI wire metadata이므로 `GradingDispatchService`에 위치한다. 이 값이 retry 정책·Job identity를 바꾸지 않으므로 `ExamGradingService`나 Job Entity에 저장하지 않는다.
- 이번 설명 작업에 별도 Jira 이슈 키는 없으며 애플리케이션·테스트 코드는 수정하지 않았다. Git commit·push·PR 생성 및 Jira 댓글·필드·상태 변경도 수행하지 않았다.

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
- `/actuator/health`와 하위 경로. 현재 Actuator 의존성과 Health 설정이 있어 endpoint가 생성되며 상세 정보는 노출하지 않는다.

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

## Current review against main (2026-07-31)

- 브랜치 `chore/add-actuator-health`의 HEAD·main과 사용자 지정 merge base는 모두 `b70d03f38afc239849086fef6549bc3af47c89f6`다. 해당 기준 tracked diff와 신규 미추적 운영·리소스·테스트 파일을 함께 리뷰했으며, 수정 가치가 확실한 correctness finding은 확인하지 않았다.
- Actuator Health, AWS Default Credentials, 선택적 모범답안 음성 응답, AI `client_source=app`, 신규 문항 prompt API를 점검했다. 기존 공개 API·`BaseResponse`, retryCount, Redis/S3 기존 계약, Callback JSON, AI `user_id=examId`와 사용자 소유권/비노출 규칙은 유지된다.
- `git diff --check`와 catalog JSON·민감 패턴 정적 검증은 성공했다. 정확한 `./gradlew clean test`는 sandbox의 Gradle lock 쓰기 제한, writable offline 재시도는 file-lock UDP socket 제한으로 task 시작 전에 중단됐다. 현재 source 이후 생성된 기존 XML은 Java 245개, failures/errors/skipped 0개다.
- 리뷰 대상 애플리케이션·설정·테스트 코드는 수정하지 않았고 Codex 기록 파일만 갱신했다. 별도 Jira 이슈 키는 없으며 commit·push·PR 생성과 Jira 변경을 수행하지 않았다.
- 실제 AWS Profile/SSO·native Linux Docker·ECS Task Role/Bucket IAM 및 Python AI `client_source` 수신은 배포 전 별도 smoke test가 필요하다.

## Latest AWS credentials final review against main (2026-07-31)

- HEAD·main `b70d03f38afc239849086fef6549bc3af47c89f6` 기준 tracked 변경과 신규 미추적 파일을 함께 재검토했다. 이번 리뷰에 별도 Jira 이슈 키는 없고 Git·Jira 쓰기 작업을 수행하지 않았다.
- 이전 finding의 직접 수정은 확인됐다. AWS SDK BOM `2.29.52` 아래 `s3`·`sso`·`ssooidc`·`sts`와 transitive `auth`·`profiles`가 모두 같은 버전이고, SSO OIDC·STS factory 및 ECS Container Credentials provider가 runtime에 있다. S3Client와 S3Presigner는 공유 Default Provider를 사용하며 Bean/Health 생성 시 credential을 조회하지 않는다.
- native Linux host UID/GID, supplementary app group `999`, `HOME=/app` 방식은 현재 amd64 image의 `/app`·JAR·`/tmp` 권한과 일치한다. Dockerfile의 non-root `app`, `.aws`·`.env`·key build-context 제외와 image 내 `.aws` 부재도 유지된다.
- 남은 MEDIUM finding은 read-only SSO token cache다. AWS SDK SSO OIDC provider가 만료 임박 token을 갱신한 뒤 cache에 저장하므로, README의 전체 `.aws:ro` 방식은 host가 먼저 cache를 갱신하지 않은 장시간 local Docker 실행에서 S3 credential 해석이 실패할 수 있다. host-side SSO 재로그인 운영 절차를 명시하거나 host 원본을 read-only로 유지하는 안전한 container-owned 임시 cache 방식을 검토해야 한다.
- fresh `./gradlew clean test --no-daemon`은 Java 245개, failures/errors/skipped 0개로 성공했다. runtime dependency report·insight, `git diff --check main --`, 민감 패턴 검증도 성공했다. 실제 AWS Profile/SSO, native Linux host와 ECS Task Role smoke test는 수행하지 않았다.
- 기존 S3 Region·Bucket·Object Key·Presigned URL, 시험 API·DTO·`BaseResponse`, JWT·Guest JWT, retryCount, Redis, grading retry·멱등성, Callback JSON과 AI `user_id=examId`에는 별도 회귀를 확인하지 않았다.

## Latest frontend question-feedback contract analysis (2026-08-04)

- 현재 미커밋 작업 트리 기준 프론트 문항별 상세 조회는 `GET /api/v1/exams/{examId}/questions?questionNumber={questionNumber}&retryCount={retryCount}`이며 HTTP 200 `BaseResponse.result.question`에 요청 회차의 최신 `feedback`, 회차별 `retryScores`, 최초 응시 retry 0의 `retryFeedbackScores`, 사용자 음성·Azure·문제 정보를 결합한다. 이번 분석에 별도 Jira 이슈 키는 없다.
- 텍스트 기준 답안은 `question.feedback.correctedAnswer`로 보내며 AI Callback의 `corrected_answer`가 아니라 Session 시험지 원본 `Question.corrected_answer`를 매 조회 시 사용한다. AI가 생성한 회차별 추천 답안은 별도 `question.feedback.recommendedAnswer`이며 Callback의 `recommended_answer`가 camelCase 응답으로 변환된다.
- 음성 모범답안 `question.modelAnswer`는 텍스트를 포함하지 않고 `audioUrl`, `spokenWordSequence`만 가진다. Part 1 Question 1·2이면서 해당 Session 시험지 metadata가 있을 때만 제공하고, 그 외에는 필드 자체를 생략한다. 현재 metadata는 `mock_exam_004` q1·q2만 있다.
- `modelAnswer.audioUrl`은 `{mockExamId}/part1_a{questionNumber}.wav`의 60분 Presigned GET URL이다. 사용자 녹음인 `question.audioUrl`·`question.spokenWordSequence` 및 출제 음성인 `question.questionInfo.audioUrl`과 분리되고 retryCount에 따라 바뀌지 않는다.
- 응답의 일반 필드는 camelCase이고 `azureFeedback` 내부만 snake_case다. PartResult의 null 선택 필드는 생략될 수 있으므로 프론트는 `modelAnswer`, 점수, transcript, Azure와 사용자 단어 시퀀스의 존재 여부를 확인해야 한다.
- 애플리케이션·테스트 코드는 수정하지 않았다. 관련 집중 테스트 3개 클래스, 총 12개가 failures/errors/skipped 0개로 성공했으며 전체 `./gradlew clean test`는 분석 작업이라 다시 실행하지 않았다. 실제 배포 버전과 `mock_exam_004` 외 시험지의 음성 metadata는 별도 확인 사항이다.

## Latest code review against main (2026-08-04)

- 브랜치 `chore/add-actuator-health`의 HEAD·main과 사용자 지정 merge base는 `b70d03f38afc239849086fef6549bc3af47c89f6`다. tracked diff와 신규 미추적 파일을 함께 재리뷰했으며 별도 Jira 이슈 키는 없다.
- 이전 HIGH finding을 해결했다. 요청한 canonical retryCount의 `ExamResult`가 없으면 `getDownloadUrl`과 `buildModelAnswer`를 호출하지 않으며 model-answer catalog와 `S3Presigner`도 사용하지 않는다. matching 결과가 있는 완료 회차에만 Part 1 문항 1·2의 모범답안을 조립한다.
- 이전 로컬 Docker SSO MEDIUM은 해소됐다. macOS와 native Linux 모두 host 사전 SSO 로그인과 stdout을 숨긴 credential 검증, `.aws` read-only mount, 만료 시 host 재로그인 후 컨테이너 재시작을 안내한다. 컨테이너 내부 token cache 자동 갱신을 보장하지 않고 ECS는 Profile 없이 Task Role을 사용한다.
- 기존 공개 API·`BaseResponse`, retryCount, 사용자 소유권·userId 비노출, 완료 결과의 사용자 음성 URL, Redis/S3 기존 계약, JWT·Guest, grading, Callback JSON과 AI `user_id=examId`에 별도 회귀를 확인하지 않았다.
- fresh `./gradlew clean test`는 Java 248개, failures/errors/skipped 0개로 성공했다. 신규 집중 테스트 3개를 포함한 관련 서비스·소유권 테스트 44개도 성공했고 `git diff --check`가 통과했다.
- 실제 AWS Profile/SSO·native Linux Docker·ECS Task Role/Bucket IAM 및 Python AI `client_source=app` 수신은 배포 전 별도 smoke test가 필요하다.

## Latest modelAnswer HIGH finding narrow review (2026-08-04)

- `ExamServiceImpl.getExamQuestion`, 직접 관련된 modelAnswer 테스트와 Presigned URL 생성 경로만 재검토했으며 HIGH·MEDIUM finding은 없다. 별도 Jira 이슈 키는 없다.
- 요청 문항·canonical retry 결과가 없으면 `buildModelAnswer`, model-answer catalog 조회와 Presigned GET URL 생성을 모두 생략한다. 제출 전·처리 중·존재하지 않는 retry에는 `modelAnswer`가 없고 완료된 Part 1 문항 1·2에는 기존대로 제공된다.
- 다른 사용자 시험은 소유권 검사에서 403으로 선차단되어 모범답안 조회와 Presigner가 실행되지 않는다.
- 집중 테스트 `ExamQuestionModelAnswerTest` 8개와 `ExamOwnershipServiceTest` 36개, 총 44개가 failures/errors/skipped 0개로 성공했다. 애플리케이션·테스트 파일과 Git·Jira 상태는 변경하지 않았다.

## Latest completed-history and retry-attempt APIs (2026-08-04)

- 관련 Jira 이슈 [`TMI-61`](https://to-teacher.atlassian.net/browse/TMI-61)을 `TMI` 프로젝트의 `작업` 타입으로 생성했다. 설명에는 JWT `sub` 식별, `completedAt` 완료 기준, 신규 Summary 우선·Legacy fallback, Job 우선 Retries, `dispatchAttempt`·상세 피드백 비노출, 소유권·호환성 및 테스트 완료 조건을 기록했다.
- `GET /api/v1/exams/history`를 추가했다. JWT 모드에서는 기존 Resource Server가 Bearer 인증을 요구하고 `JwtCurrentUserProvider`가 검증된 JWT `sub` UUID를 실제 사용자 ID로 사용한다. 요청·응답에 `userId`나 `mockExamId`를 추가하지 않았고 local/test Legacy Guest 정책은 유지했다.
- History 완료 판정은 `ExamSession.userId = current user`와 `completedAt` 존재 여부만 사용한다. `active=false`만으로 완료를 판정하지 않으며 active가 null인 Legacy 완료 Session도 포함한다. 결과는 `completedAt DESC`, 동일 시각에는 `examId DESC`다.
- History는 `totalCount`와 `histories`를 반환한다. 각 항목은 `examId`, MockExam `title`, `cycleNumber`, `completedAt`, `totalScore`, `levelEstimate`, `summaryAvailable`만 포함한다. 완료 이력이 없으면 200과 `histories=[]`다.
- Session 목록 뒤 MockExam 제목, 신규 ExamSummary 후보, Legacy `exam_results.totalScore != null` 후보를 각각 batch 조회한다. Mongo `_id DESC`의 첫 문서를 최신으로 사용하고 신규 Summary를 우선한다. Summary가 전혀 없는 완료 시험은 점수·레벨 null과 `summaryAvailable=false`이며 해당 examId만 로그에 남긴다.
- `GET /api/v1/exams/{examId}/retries`를 추가했다. Session 존재와 JWT `sub` 소유권을 먼저 확인해 기존 `EXAM_4004`/`COMMON403`을 유지한다. `question_grading_jobs`의 `questionNumber`, 사용자 `retryCount`, 기존 Job status가 1차 기준이고 `dispatchAttempt`는 읽거나 응답 회차로 사용하지 않는다.
- 문항별 Legacy `exam_results.retryCount`를 합치고 null retryCount는 기존 canonical 정책대로 0으로 해석한다. 동일 question/retry Key는 Job 상태가 우선하며 결과만 있는 회차는 `COMPLETED`다. 실제 retryCount 1 이상이 있는 문항만 반환하고, 저장된 0회차는 함께 제공하되 없는 0회차는 생성하지 않는다. 문항과 회차는 각각 오름차순이며 상세 score·feedback·Transcript·URL·failureReason을 반환하지 않는다. 재답변 문항이 없으면 200과 `questions=[]`다.
- 운영 자동 인덱스 생성에 의존하지 않도록 별도 기본 dry-run/idempotent 스크립트를 추가했다. 대상은 `exam_sessions {userId:1, completedAt:-1, _id:-1}`, `question_grading_jobs {examId:1, questionNumber:1, retryCount:1}`, `exam_results {examId:1, questionNumber:1, retryCount:1}`이며 기존 호환 인덱스는 중복 생성하지 않고 충돌 정의는 쓰기 전에 차단한다. 실제 MongoDB에는 적용하지 않았다.
- 새 Java 테스트 18개와 Node migration 테스트 7개, 총 25개를 추가했다. 신규 집중 Java 테스트 37개가 성공했고 `./gradlew clean test` 전체 Java 266개가 failures/errors/skipped 0으로 성공했다. MongoDB 스크립트 전체 Node 56개도 성공했으며 `git diff --check`가 통과했다.
- 기존 시험 생성·문항 단건·Summary·status·submit·grading retry와 Controller mapping, `BaseResponse`, retryCount/dispatchAttempt 의미, JWT·Guest, Redis, S3, AI/Callback `user_id=examId`, modelAnswer의 `audioUrl`·`spokenWordSequence`, Health 계약을 변경하지 않았다. Secret, Token, 실제 URI·Credential·Presigned URL을 코드·로그·문서에 기록하지 않았다.
- 남은 운영 확인은 실제 데이터 규모의 query explain, 별도 인덱스 스크립트 dry-run/apply, 혼합 BSON `_id` 타입을 가진 중복 Summary의 최신 정렬 결과와 staging Bearer smoke test다. Git commit·push·PR 생성은 수행하지 않았다.

## Latest Jira issue creation (2026-08-04)

- [`TMI-61`](https://to-teacher.atlassian.net/browse/TMI-61) — `[Learning Core] 완료 시험 이력 및 재답변 회차 조회 API`를 생성했다.
- 프로젝트는 `TMI`(ID `10000`), 이슈 유형은 `작업`(ID `10003`)이다. 생성 후 재조회에서 기본 상태 `해야 할 일`(ID `10000`), 기본 우선순위 `Medium`(ID `3`), 담당자 미지정, 빈 라벨을 확인했다.
- 설명에는 JWT `sub` 기반 사용자 식별, `ExamSession.completedAt` 완료 기준, 신규/Legacy Summary batch 결합과 신규 우선 fallback, `question_grading_jobs` 우선 및 `exam_results` Legacy fallback, 사용자 `retryCount`·Job 상태 제공과 `dispatchAttempt`·상세 피드백 비노출을 기록했다.
- 보안·호환성 및 Java·MongoDB 스크립트 테스트 완료 조건도 기록했다. 제공된 Jira/PR 완료 댓글 초안은 이번 이슈 생성 요청 범위에서 등록하지 않았고 상태 전환·담당자·라벨·댓글은 변경하지 않았다.
- 애플리케이션·테스트·migration 구현은 수정하지 않았다. 문서 기록만 갱신했으며 이번 turn에서는 Gradle·Node 테스트를 다시 실행하지 않았다. 직전 구현 검증 결과인 Java 266개와 MongoDB 스크립트 56개 성공 상태를 인용했을 뿐 재실행 결과로 기록하지 않는다.

## Latest TMI-61 History/Retries scoped review (2026-08-04)

- Jira `TMI-61`의 `GET /api/v1/exams/history`, `GET /api/v1/exams/{examId}/retries`와 Controller, `ExamReadService`, 신규 DTO, 관련 Repository, MongoDB read-index 스크립트 및 관련 테스트만 검토했다.
- 리뷰 결과는 HIGH 없음, MEDIUM 1건이다. `ExamSummaryRepository.findHistoryCandidatesByExamIdIn`은 `exam_summaries`를 `examId IN (...)`으로 조회하고 `{examId:1, _id:-1}` 정렬하지만 `create-exam-read-indexes.js`에는 `exam_summaries` 인덱스가 없다. 데이터가 증가하면 사용자 History 요청마다 전역 collection scan과 blocking sort가 발생할 수 있으므로 해당 query shape를 지원하는 인덱스를 스크립트·테스트·문서에 추가하고 실제 `explain`으로 검증해야 한다.
- 확인 항목 1~10의 기능 동작은 모두 충족한다. completedAt/current user 필터, `completedAt DESC`·`examId DESC`, 고정 개수 batch 조회, Summary 없음 허용, 타 사용자 Retries 403, dispatchAttempt 비사용, Job/Legacy 회차 dedupe, retry 1 이상 없는 문항 제외, 200 빈 배열, 기존 문항 단건·Summary mapping/DTO 계약 유지가 확인됐다.
- 관련 Java 테스트 6개 클래스 40개와 `create-exam-read-indexes.test.js` Node 7개가 모두 failures/errors/skipped 0개로 성공했고 `git diff --check`도 통과했다. 첫 Gradle 시도는 sandbox의 사용자 Gradle cache lock 권한으로 task 시작 전에 중단됐고 승인된 동일 명령 재실행은 성공했다.
- 실제 MongoDB query `explain`과 인덱스 dry-run/apply는 수행하지 않았다. 애플리케이션·테스트·인덱스 스크립트는 수정하지 않았고 필수 Codex 작업 기록 문서만 갱신했다.

## Latest Stop Hook record reconciliation (2026-08-04)

- Stop Hook이 요구한 현재 turn 기록을 추가했다. Jira `TMI-61` History/Retries 지정 범위 리뷰 결과는 HIGH 없음, MEDIUM 1건으로 동일하며, MEDIUM은 `exam_summaries` History batch query용 인덱스가 read-index 스크립트에서 누락된 문제다.
- 기능 확인 1~10, 관련 Java 40개·Node 7개 성공, `git diff --check` 성공과 실제 MongoDB `explain`·dry-run/apply 미실행 상태는 변경되지 않았다.
- 애플리케이션·테스트·인덱스 스크립트와 Jira는 변경하지 않았고 Stop Hook 기록을 위한 Codex 문서만 갱신했다. Secret과 Token은 기록하지 않았다.

## Latest TMI-61 Summary batch index MEDIUM fix (2026-08-04)

- targeted review의 MEDIUM finding을 최소 범위로 수정했다. `create-exam-read-indexes.js`의 선언형 계획에 `exam_summaries`용 `idx_exam_summaries_exam_id_latest`, Key `{examId:1, _id:-1}`를 추가해 `ExamSummaryRepository.findHistoryCandidatesByExamIdIn`의 `examId IN (...)`과 `{examId:1, _id:-1}` 정렬을 지원한다.
- 기본 dry-run, `EXAM_READ_INDEXES_APPLY=true` 명시 apply, apply 전 전체 충돌 검사, apply 후 재검증과 운영 자동 적용 금지 정책을 유지했다. 인덱스만 계획·생성하며 `exam_summaries` 문서는 조회·수정하지 않는다.
- 같은 이름·정확히 같은 Key와 다른 이름·같은 Key는 idempotent하게 재생성하지 않는다. 다른 이름의 더 긴 `{examId:1, _id:-1, ...}` 인덱스는 필수 ordered prefix와 옵션이 호환되면 재사용하고, 확정 이름의 다른 정의·역방향·필드 순서 불일치·짧은 Key와 unique/sparse/partial/collation 옵션은 호환으로 보지 않는다.
- Node 테스트는 Summary 계획·확정 이름·정확한 Key, dry-run 무쓰기, apply 생성, 동일/다른 이름 idempotency, 긴 prefix, 동일 이름 충돌, 역방향·재정렬·짧은 Key와 기존 세 인덱스 회귀를 실제 MongoDB 없이 검증한다. 전체 MongoDB 스크립트 테스트 63개가 성공했다.
- 요청한 Java 집중 테스트 `*ExamRead*`, `*JwtSecurityIntegrationTest*`, `*LegacySecurityIntegrationTest*` 총 37개와 `git diff --check`가 성공했다. Java 운영 코드, Controller, Repository, DTO와 공개 API·인증·소유권·retryCount·dispatchAttempt·modelAnswer 계약은 변경하지 않았다.
- 실제 Staging/운영 DB apply와 `explain("executionStats")`는 수행하지 않았다. README에 apply 후 IXSCAN, 선택 인덱스, COLLSCAN·blocking SORT 부재와 `totalDocsExamined`를 확인하는 쿼리를 기록했다. 전체 `clean test`는 Java 운영 코드가 바뀌지 않아 PR 직전 통합 검증으로 남겼다.
- Git commit·push·PR 생성 및 Jira `TMI-61` 댓글·필드·상태 변경은 수행하지 않았다. Secret과 Token은 기록하지 않았다.

## Latest TMI-61 Summary index narrow review (2026-08-04)

- `ExamSummaryRepository`, `create-exam-read-indexes.js`, `create-exam-read-indexes.test.js`만 재검토했다. 결과는 HIGH 없음, MEDIUM 1건이다.
- MEDIUM: `hasIncompatibleOptions`가 `hidden:true`를 검사하지 않아 정확한 `{examId:1, _id:-1}` 또는 호환 prefix 인덱스가 hidden이어도 compatible로 처리한다. apply와 최종 검증은 새 usable 인덱스를 만들지 않고 성공할 수 있지만 MongoDB Query Planner는 hidden 인덱스를 사용하지 않으므로 History query가 COLLSCAN/blocking SORT로 남을 수 있다.
- Key `{examId:1, _id:-1}`와 이름 `idx_exam_summaries_exam_id_latest`는 Repository의 `examId IN (...)`, `{examId:1, _id:-1}` 정렬에 맞게 존재한다. 기본 dry-run, 명시 apply, exact/different-name idempotency, 확정 이름 충돌 무쓰기와 기존 세 인덱스 계획도 유지된다.
- Node 테스트 14개와 `git diff --check`가 성공했다. 별도 Node probe에서 다른 이름의 `{examId:1, _id:-1, hidden:true}`가 오류 없이 compatible로 분류되고 Summary 인덱스 생성 계획에서 제외되는 것을 재현했다. 실제 MongoDB 연결·apply·explain은 수행하지 않았다.
- 리뷰 대상 코드는 수정하지 않았고 필수 Codex 기록만 갱신했다. Jira `TMI-61`, Git commit·push·PR, Secret과 Token에는 변경이 없다.

## Latest TMI-61 hidden index MEDIUM fix (2026-08-04)

- targeted review의 hidden 인덱스 MEDIUM finding 하나만 수정했다. `hasIncompatibleOptions`가 `hidden:true`를 비호환으로 판정하며 `hidden:false` 또는 hidden 필드가 없는 visible 인덱스는 기존대로 호환 가능하다.
- 다른 이름의 exact `{examId:1, _id:-1}` 또는 compatible prefix가 hidden이면 재사용하지 않고 visible 목표 인덱스를 생성 계획에 남긴다. 동일 이름의 hidden 인덱스는 컬렉션·이름·예상 Key·실제 Key·`hidden=true`와 자동 drop/unhide 미수행 사실을 포함한 명시적 충돌로 apply 전에 전체 쓰기를 차단한다.
- 스크립트는 `dropIndex`나 `collMod`를 호출하지 않고 기존 인덱스를 수정하지 않는다. 기본 dry-run, 명시 apply, visible 인덱스 idempotency, unique/sparse/partial/collation 충돌과 기존 네 컬렉션 인덱스 계획은 유지된다.
- read-index Node 테스트는 19개로 늘어 exact/prefix hidden 배제, same-name hidden 무쓰기 충돌, create/drop/collMod 0회, hidden false/필드 누락 visible 호환, 다른 이름 visible 중복 방지와 기존 옵션·계획 회귀를 실제 MongoDB 없이 검증한다. 전체 MongoDB 스크립트 테스트 68개와 `git diff --check`가 성공했다.
- Java·Repository·Controller·DTO와 공개 API 계약은 변경하지 않아 Java 테스트는 다시 실행하지 않았다. 실제 DB apply와 `explain("executionStats")`, Git commit·push·PR 및 Jira `TMI-61` 댓글·필드·상태 변경은 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## Latest TMI-61 hidden index targeted review (2026-08-04)

- `create-exam-read-indexes.js`의 hidden 호환 판정, 동일 이름 hidden 충돌 무쓰기, visible 인덱스 idempotency를 재검토했으며 HIGH·MEDIUM finding은 없다.
- `hidden:true` exact/prefix 인덱스는 호환에서 제외된다. 동일 이름 hidden은 apply 전에 충돌하고 `createIndex`, `dropIndex`, `collMod`를 호출하지 않으며, `hidden:false` 또는 hidden 필드가 없는 visible exact/prefix 인덱스는 기존대로 중복 생성하지 않는다.
- 직접 관련된 Node 테스트 19개가 failures/errors/skipped 0으로 성공했다. 실제 MongoDB 연결·apply·explain은 수행하지 않았고 리뷰 대상 코드·테스트, Git 및 Jira 상태는 변경하지 않았다.

## Latest TMI-61 missing-namespace dry-run fix (2026-08-04)

- 아직 없는 `exam_summaries`의 비동기 인덱스 조회가 `NamespaceNotFound`로 dry-run을 중단하던 문제를 수정했다. 조회 helper와 호출부는 `async/await`를 사용하며 `code === 26` 또는 `codeName === "NamespaceNotFound"`만 빈 인덱스 목록으로 정규화한다.
- 누락 컬렉션의 인덱스는 dry-run 생성 예정에 포함되지만 `createCollection`, `createIndex`, `dropIndex`, `collMod` 쓰기는 발생하지 않는다. apply에서는 기존 `createIndex` 흐름을 유지하며 별도 문서 insert/delete를 사용하지 않는다.
- 인증·네트워크·권한·명령·알 수 없는 MongoDB 오류는 숨기지 않고 전파한다. visible idempotency, hidden 비호환, 동일 이름 충돌 선차단과 자동 drop/unhide 금지 정책도 유지된다.
- Node 테스트 8개를 추가했고 전체 MongoDB 스크립트 테스트 76개와 `git diff --check`가 성공했다. 실제 MongoDB 연결·apply·explain, Git commit·push·PR과 Jira `TMI-61` 쓰기 작업은 수행하지 않았다.

## Latest AWS Secrets Manager configuration inventory (2026-08-04)

- 현재 tracked Learning Core 설정 기준으로 `MONGODB_URI`는 자격증명을 포함하므로 AWS Secrets Manager 필수 대상이고, `SENTRY_DSN`은 보호 저장 권장 대상이다. 실제 값이나 실행 환경 Secret은 조회하지 않았다.
- 현재 checkout에는 Expo Access Token과 Redis password 설정이 없다. 해당 인증 기능이 배포될 때만 Provider Access Token 또는 Redis AUTH 값을 Secrets Manager 대상으로 추가해야 하며, 먼저 애플리케이션 설정 바인딩을 확인해야 한다.
- MongoDB database 이름, Redis host/port, AWS Region·S3 Bucket, Identity issuer·JWKS URL·audience, profile/auth mode와 각종 prefix·timeout·thread·port·sampling 값은 비밀값이 아닌 일반 구성이다.
- AWS 장기 Access Key/Secret Key는 Secrets Manager 주입 대상이 아니라 ECS Task Role로 대체한다. Learning Core에는 Identity RSA Private Key나 공유 JWT Secret을 저장하지 않는다.
- 별도 Jira 이슈 키는 없고 코드·테스트 변경이나 AWS/Git/Jira 쓰기 작업은 수행하지 않았다.

## Latest AI endpoint configuration check (2026-08-05)

- 현재 `main`의 AI 채점 endpoint는 `GradingDispatchService` 정적 상수로 고정되어 있고 환경변수 또는 Spring property로 처리되지 않는다. 문항과 Summary 전송이 같은 고정 주소를 사용한다.
- 환경변수로 조정 가능한 AI 관련 값은 연결 timeout과 읽기 timeout뿐이며 `.env.example`에는 AI 주소 항목이 없다.
- 현황 확인만 수행해 코드·설정·테스트를 변경하거나 테스트를 재실행하지 않았다. 별도 Jira 이슈 키와 Git/Jira 쓰기 작업은 없고 실제 Secret·Token·실행 환경값은 조회하지 않았다.

## Latest AI server URL environment configuration (2026-08-05)

- AI 서버 주소는 더 이상 `GradingDispatchService` 상수로 고정되지 않는다. `app.grading.ai-server-url`이 `AI_SERVER_URL`을 읽고 기본값과 `.env.example` 예시는 `http://tosunsaeng-ai:8000`이다.
- 환경변수는 base URL 계약이며 서비스가 기존 `/evaluations`를 한 번만 붙인다. `GradingProperties`는 URI와 HTTP(S) base URL 조건을 기동 시 검증한다.
- 문항·Summary의 AI 요청 body·header, `user_id=examId`, `mock_exam_id`, `client_source`, `Idempotency-Key`와 endpoint path는 유지했다.
- 관련 집중 테스트와 전체 `./gradlew clean test` Java 267개가 failures/errors/skipped 0으로 성공했고 `git diff --check`도 성공했다. 실제 AI 호출, Git commit·push·PR 및 Jira 쓰기는 수행하지 않았다.
- Stop Hook 요구에 따라 현재 turn marker를 포함한 append-only WORKLOG 보완 기록을 추가했으며 구현·검증 결과에는 변경이 없다.

## Latest exam-session audio URL inspection (2026-08-06)

- `POST /api/v1/exams`가 반환하는 각 문제의 `audioUrl`은 `questions/{mockExamId}/q_{questionNumber}.wav`를 대상으로 생성한 60분 S3 Presigned GET URL이다.
- Part 3 문항에는 `questions/{mockExamId}/part3_intro.wav`의 60분 `guideAudioUrl`도 포함된다. 실제 URL 문자열은 실행 환경의 Bucket·Region과 서명에 따라 달라진다.
- 사용자 녹음용 Presigned PUT URL은 세션 생성과 분리되어 기존 upload-url API에서 `temp/{examId}/q_{questionNumber}_r{retryCount}.wav` Key로 발급된다.
- 별도 Jira 이슈 키와 애플리케이션·테스트 변경은 없다. 실제 S3·Secret·Credential·Token·Presigned URL 접근 또는 발급, Git/Jira 쓰기 작업은 수행하지 않았다.

## Latest exam-session issuance logging inspection (2026-08-06)

- `POST /api/v1/exams`에서 `ExamSessionManager`가 새 문서를 insert해 `assignment.created() == true`인 경우에만 `ExamServiceImpl`이 세션 생성 완료 INFO 로그를 출력한다.
- 진행 중 활성 세션 재사용 또는 동시 생성 충돌 후 기존 세션 선택은 `created=false`이므로 현재 세션 발행 로그가 없다. 별도 HTTP access log 설정도 확인되지 않았다.
- 기본 Spring 로깅에서는 신규 생성 INFO 로그가 보이지만 배포 환경이 INFO를 차단하도록 별도 override하면 수집되지 않을 수 있다.
- 별도 Jira 이슈 키 및 애플리케이션·테스트 변경은 없다. Secret·Token·Credential 접근과 Git/Jira 쓰기 작업도 수행하지 않았다.

## Latest ECS logging diagnosis (2026-08-06)

- Learning Core 이미지는 커스텀 파일 appender 없이 `java -jar /app/app.jar`로 실행되므로 Spring 기본 기동·INFO 로그는 컨테이너 stdout/stderr에 기록된다.
- CloudWatch에 기동 로그까지 전혀 없다면 ECS Container Definition의 `awslogs` `logConfiguration`, 정확한 Region·Log Group·최신 stream, Task Execution Role의 CloudWatch Logs 권한과 Task Definition revision을 우선 확인해야 한다. 저장소에는 실제 Task Definition/IaC가 없다.
- 현재 로컬 AWS CLI에는 자격 증명이 없어 ECS와 CloudWatch의 실제 설정을 읽기 전용으로도 확인하지 못했다. AWS 쓰기는 수행하지 않았다.
- 세션 생성 로그만 없는 경우에는 진행 중 세션 재사용 분기와 미활성 HTTP access log가 원인이 될 수 있다. Task Definition의 `LOGGING_LEVEL_ROOT=OFF` 또는 외부 `LOGGING_CONFIG` override도 실제 배포 설정에서 확인이 필요하다.
- 별도 Jira 이슈 키와 코드·테스트 변경은 없다. 실제 Secret·Token·Credential 값은 조회하거나 기록하지 않았다.

## Latest grading-after-upload diagnosis (2026-08-06)

- Presigned PUT은 앱에서 S3로 직접 수행되며 Learning Core에 업로드 완료 이벤트가 전달되지 않는다. 채점은 동일 식별자와 회차의 별도 `POST /api/v1/exams/{examId}/questions/{questionNumber}/submit` 호출로만 시작된다.
- submit은 결정적 Question Job을 PROCESSING으로 claim한 뒤 S3 객체를 Presigned GET으로 다운로드하고 `AI_SERVER_URL`의 `/evaluations`에 multipart POST한다. Put 성공만으로 S3 Get 권한·Key 일치와 AI 연결은 검증되지 않는다.
- ECS에서 `AI_SERVER_URL` 미주입 시 기본값 `http://tosunsaeng-ai:8000`을 사용한다. 해당 이름이 ECS Service Connect/Cloud Map 또는 실제 Task 통신 경로로 해석되지 않으면 dispatch가 실패한다. Task Role의 `s3:GetObject` 누락도 같은 증상을 만든다.
- dispatch 예외는 Job의 `failureReason=QUESTION_DISPATCH_FAILED`와 API `EXAM_4001`로 정규화되지만 원래 예외를 로그로 남기지 않는다. submit·S3 fetch·AI outbound에도 정상 흐름 로그가 없어 현재 관측성만으로 S3와 AI 실패를 구분할 수 없다.
- submit 응답이 없으면 앱 호출 누락, 401/403이면 인증·소유권, 500 `EXAM_4001`이면 S3 GET/AI 연결, 200 `PROCESSING` 뒤 정체면 AI 처리·Callback을 우선 확인한다. 실패 원인 수정 후 동일 submit은 기존 Job을 자동 재전송하지 않으므로 grading retry 경로를 사용해야 한다.
- 별도 Jira 이슈 키와 코드·테스트 변경은 없다. 실제 AWS·MongoDB·AI·Secret·Token·Credential 접근 및 Git/Jira 쓰기 작업은 수행하지 않았다.

## Latest AI grading outbound logging inspection (2026-08-06)

- 문항 submit, S3 음성 재다운로드, AI `/evaluations` 요청 직전과 성공 응답에는 현재 INFO 로그가 없다. `GradingDispatchService`에는 logger가 선언되어 있지 않다.
- outbound RuntimeException은 Question Job을 `FAILED`와 `QUESTION_DISPATCH_FAILED`로 기록하고 `EXAM_4001`로 변환되지만 원래 예외와 S3/AI 실패 단계는 로그에 남지 않는다.
- AI Feedback Callback이 실제 도달했을 때만 Controller가 exam·문항·회차 식별 정보를 INFO로 기록한다. outbound와 inbound 관측성은 비대칭이다.
- 별도 Jira 이슈 키와 코드·테스트 변경은 없다. 실제 외부 시스템·Secret·Token·Credential 접근 및 Git/Jira 쓰기 작업은 수행하지 않았다.

## Latest FAILED Question Job resubmission behavior (2026-08-06)

- 동일 `examId + questionNumber + retryCount` submit은 결정적 Job ID를 재사용한다. 기존 Job insert가 Duplicate Key이면 기존 상태를 반환하고 dispatch하지 않으므로 FAILED Job의 같은 submit 재호출은 HTTP 200과 `result.status=FAILED`가 된다.
- 최초 dispatch 실패가 발생한 원 요청은 Job을 `FAILED/QUESTION_DISPATCH_FAILED`로 전이하고 500 `EXAM_4001`을 반환한다. 이후 같은 submit부터는 중복 Job 상태 조회 경로다.
- 시험 단위 `POST /api/v1/exams/{examId}/grading/retry`는 최초 응시 `retryCount=0` FAILED Job만 즉시 재시도하며 dispatchAttempt가 설정된 최대 횟수 미만일 때 AI에 다시 보낸다. 기본 최대 횟수는 3회다.
- `retryCount>0` 사용자 재답변 Job은 이 시험 단위 복구 API 대상이 아니다. 새로운 retryCount submit은 새로운 답변 Job이며 기존 FAILED Job 재전송과는 다르다.
- 별도 Jira 이슈 키와 코드·테스트 변경은 없다. 실제 외부 시스템·Secret·Token·Credential 접근 및 Git/Jira 쓰기 작업은 수행하지 않았다.

## Latest question submit payload inspection (2026-08-06)

- 앱 submit은 Body 없이 `POST /api/v1/exams/{examId}/questions/{questionNumber}/submit?retryCount={retryCount}`를 호출한다. JWT 모드에서는 Bearer 인증이 필요하며 retryCount 기본값은 0이다.
- Learning Core는 `temp/{examId}/q_{questionNumber}_r{retryCount}.wav`의 음성을 읽고 AI multipart에 `user_id=examId`, Session `mock_exam_id`, 파생 `part_number`, `question_number`, canonical `retry_count`, `client_source=app`, `audio_file`을 보낸다. 실제 사용자 ID는 보내지 않는다.
- AI endpoint는 `${AI_SERVER_URL}/evaluations`, multipart 파일명은 `q_{questionNumber}_r{retryCount}.webm`, `Idempotency-Key`는 `question:{examId}:{questionNumber}:{retryCount}`다.
- 별도 Jira 이슈 키와 코드·테스트 변경은 없다. 실제 외부 시스템·Secret·Token·Credential 접근 및 Git/Jira 쓰기 작업은 수행하지 않았다.

## Latest AI request recognition diagnosis (2026-08-06)

- `AI_SERVER_URL`은 base URL이어야 하며 Learning Core가 `/evaluations`를 붙인다. 배포 값에 이미 해당 path가 있으면 `/evaluations/evaluations`로 전송될 수 있고 현재 URI 검증은 이 오설정을 차단하지 않는다.
- 기존 웹 POC 대비 앱 문항 요청의 주요 wire 차이는 `client_source=app`, Session의 실제 `mock_exam_id`, `Idempotency-Key`와 환경변수 endpoint다. 웹 요청만 정상이라면 AI의 이 값 처리 여부를 우선 대조해야 한다.
- S3 Key `.wav`의 bytes를 multipart 파일명 `.webm`으로 보내는 기존 동작도 AI가 확장자·Content-Type을 엄격히 검증하는 경우 확인 대상이다.
- submit이 200 `PROCESSING`이면 AI HTTP endpoint가 2xx를 반환했으므로 AI 내부 분기·Callback 문제이고, 500 `EXAM_4001` 또는 FAILED이면 path, multipart validation, AI 4xx/5xx 또는 전송 문제다. Learning Core는 현재 원본 AI 응답 오류를 로그에 남기지 않는다.
- Python 채점 서버 소스와 실제 ECS/AI 요청·응답은 확인하지 못했다. 별도 Jira 이슈 키와 코드·테스트 변경은 없으며 외부 시스템·Secret·Token·Credential 접근 및 Git/Jira 쓰기 작업도 수행하지 않았다.

## Latest question grading diagnostic logging (2026-08-06)

- 별도 Jira 이슈 키 없이 Question submit과 AI outbound 진단 로그를 추가했다. submit job/exam/question/retry, 신규·기존 Job과 status/dispatchAttempt, AI 호출 전 job/fileKey/attempt와 성공·실패를 기록한다.
- `GradingDispatchService`는 실제 `${AI_SERVER_URL}/evaluations` URI, jobId, fileKey, audio byte size와 반환 HTTP status를 INFO로 기록하며 최초 submit과 grading retry outbound 모두 추적 가능하다.
- dispatch 실패는 jobId·예외 타입·안전한 메시지를 ERROR로 기록한다. Presigned URL·서명·Token 가능성이 있는 URI 및 민감 값은 치환하고 메시지를 단일 행 500자로 제한하며 원본 Throwable stacktrace는 로그에 출력하지 않는다.
- 기존 Job idempotency, FAILED/retry 상태 전이, S3·AI payload와 `Idempotency-Key`, 공개 API·DTO·응답은 변경하지 않았다.
- 로그 내용과 Presigned URL 비노출 집중 테스트 및 전체 `./gradlew clean test`가 성공했다. Java 268개, failures/errors/skipped 0개이며 `git diff --check`도 성공했다. 실제 AI·AWS·MongoDB·ECS 및 Git/Jira 쓰기 작업은 수행하지 않았다.

## Latest question prompt API inspection (2026-08-06)

- 특정 문제만 조회하는 API는 `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`다. JWT sub 사용자와 ExamSession 소유권을 확인한 뒤 Session의 실제 시험지에서 해당 문항을 반환한다.
- `QuestionDTO`는 part, questionNumber, 문제·참조·Part 안내 문구, image/table, 준비·답변 시간과 60분 문제 audioUrl을 포함하며 Part 3은 guideAudioUrl도 제공한다.
- 채점 상태·점수·피드백·retryCount·사용자 녹음은 포함하지 않아 기존 결과 단건 `GET /api/v1/exams/{examId}/questions`와 구분된다. 세션 생성 API는 전체 문제 목록을 반환한다.
- 별도 Jira 이슈 키와 코드·테스트 변경은 없다. 실제 외부 시스템·Secret·Token·Credential 접근 및 Git/Jira 쓰기 작업은 수행하지 않았다.

## Latest always-new exam session lifecycle (2026-08-06)

- `POST /api/v1/exams`는 더 이상 진행 중 Session을 재사용하지 않는다. 같은 사용자의 기존 `IN_PROGRESS` Session을 조건부로 모두 `ABANDONED`, `active=false`로 전이한 뒤 매번 새 examId와 `IN_PROGRESS` status의 Session을 insert하고 새 Redis 상태를 PENDING으로 초기화한다.
- 내부 Session 상태는 `IN_PROGRESS`, `COMPLETED`, `ABANDONED`이며 status 없는 기존 문서는 completedAt/active와 완료 증거를 이용한 legacy 호환 처리를 유지한다. 완료 처리는 completedAt, active=false, status=COMPLETED를 함께 기록하고 ABANDONED Session을 완료로 되돌리지 않는다.
- 다중 ECS 동시 시작은 기존 필수 `uniq_exam_sessions_active_user` partial unique 인덱스와 조건부 ABANDON, Duplicate Key 재시도로 직렬화한다. 동시 테스트에서 서로 다른 신규 ID가 생성되고 최종 활성 Session이 한 개임을 확인했다.
- ABANDONED 시험의 Feedback/Summary·SpeechAce·Azure Callback은 저장 및 Job/Session 완료 없이 성공 no-op이며, Question/Summary dispatch도 Session 상태를 재검사해 AI 재전송을 막는다. 시험 단위 grading retry는 IN_PROGRESS만 허용하고 ABANDONED/COMPLETED를 각각 `EXAM_4007`/`EXAM_4008`로 차단한다.
- 기존 사용자 재답변은 완료 시험에서도 같은 examId와 증가한 retryCount로 유지된다. 새 시험의 최초 submit은 retryCount=0이고 과거 Job·결과·오디오를 상속하지 않는다. 공개 API·DTO·BaseResponse, AI `user_id=examId`, Callback JSON, S3 Key와 기존 submit 멱등성은 변경하지 않았다.
- `git diff --check`와 `./gradlew clean test`가 성공했고 XML 기준 전체 Java 272개, failures/errors/skipped 0개다. 실제 MongoDB·Redis·S3·AI 호출, 운영 변경, Git commit·push·PR 및 Jira 쓰기는 수행하지 않았다.

## Latest Question submit state-transition inspection (2026-08-06)

- 최초 Question Job은 실제로 PENDING으로 insert되지만 같은 submit 요청이 즉시 optimistic-lock claim하여 PROCESSING과 dispatchAttempt=1로 저장한 뒤 AI HTTP 호출을 수행한다.
- 현재 의미에서 PENDING은 아직 claim되지 않은 대기 상태이고 PROCESSING은 outbound 호출 시작부터 AI Callback 완료 전까지의 상태다. 정상 submit 응답은 AI endpoint의 2xx 이후 PROCESSING이므로 일반 클라이언트는 짧은 PENDING 구간을 관찰하지 못한다.
- 클라이언트에 PENDING을 먼저 반환하려면 응답만 바꾸는 것이 아니라 초기 AI 전송을 별도 원자적 claim Worker로 분리해야 한다. 코드·테스트 변경과 실제 외부 호출은 수행하지 않았다.

## Latest grading-status semantics clarification (2026-08-06)

- 현재 PENDING은 Learning Core 내부에서 아직 dispatch claim되지 않은 상태이고, PROCESSING은 AI 전송 claim부터 최종 Callback까지의 전체 상태다.
- 따라서 PROCESSING은 AI가 실제 모델 계산을 시작했다는 뜻이 아니라 요청 전송 중, AI 내부 대기 또는 결과 Callback 대기를 모두 포함할 수 있다.
- AI 실제 대기와 계산 중을 정확히 구분하려면 AI가 accepted/started 상태를 제공하는 추가 계약이 필요하다. 이번 확인에서는 코드·테스트·외부 시스템을 변경하지 않았다.

## Latest grading-retry eligibility clarification (2026-08-06)

- 기존 grading retry는 FAILED Job을 즉시, PENDING은 `GRADING_PENDING_TIMEOUT` 이후, PROCESSING은 `GRADING_PROCESSING_TIMEOUT` 이후 재시도 대상으로 삼는다. 기본값은 각각 1분과 3분이며 최대 dispatch 시도 기본값은 3이다.
- 프론트의 복구 조건에는 장기 PROCESSING도 포함하는 것이 현재 상태 의미와 일치한다. 동일 submit 재호출은 기존 Job 상태만 반환하므로 실제 재전송에는 시험 단위 grading retry API를 사용하고, 최종 eligibility는 백엔드 응답을 기준으로 해야 한다.
- 이번 확인에서는 코드·테스트·외부 시스템을 변경하지 않았다.

## Latest frontend retry guidance for long PROCESSING (2026-08-06)

- 프론트 복구 UI는 FAILED뿐 아니라 backend timeout을 넘긴 PENDING과 PROCESSING도 대상으로 삼아야 한다. 현재 기본 timeout은 PENDING 1분, PROCESSING 3분이고 최대 dispatch 시도는 3회다.
- 재전송은 동일 submit 호출이 아니라 시험 단위 `POST /api/v1/exams/{examId}/grading/retry`를 사용한다. 최종 eligibility와 동시 claim은 백엔드가 판정하며, 장기 PROCESSING 재전송에 대비해 AI의 동일 Idempotency-Key 처리 보장이 필요하다.
- 코드·테스트·외부 시스템 변경은 수행하지 않았다.

## Latest Part 4 question table image response (2026-08-06)

- 문항 단건 `GET /api/v1/exams/{examId}/questions?questionNumber={number}&retryCount={optional}`의 Part 4 `questionInfo`는 이제 `part`, `questionNumber`, `tableImageUrl`만 반환한다. MongoDB `table_image_url`은 `Question.tableImageUrl`로 명시 매핑되며 저장된 URL을 가공 없이 전달한다.
- Part 4 단건 응답에서는 기존 text, referenceText, partIntroText, audioUrl, guideAudioUrl, imageUrl, tableContext, prepTimeSec, speakTimeSec를 노출하지 않는다. DB·내부 `tableContext`, 세션·prompt 변환, Part 1·2·3·5·6·7과 Summary API는 유지한다.
- Part 4 URL이 null·빈 문자열·공백이면 기존 카탈로그 설정 오류 `EXAM_5001`로 처리하며 임의 URL이나 Presigned URL을 생성하지 않는다.
- Part 4 AI submit multipart와 `Idempotency-Key`는 변경하지 않았다. 집중 테스트와 전체 `./gradlew clean test`가 성공했고 Java 286개, failures/errors/skipped 0개이며 `git diff --check`도 성공했다. 실제 MongoDB·S3·AI, Git 및 Jira 쓰기 작업은 수행하지 않았다.

## Latest Part 4 delivery-path clarification (2026-08-06)

- URL-only Part 4 변경은 채점 결과 단건 `GET /api/v1/exams/{examId}/questions?questionNumber={number}&retryCount={optional}`의 `questionInfo`에 적용된다. 이 응답은 part, questionNumber, tableImageUrl만 포함한다.
- 초기 문제를 전달하는 `POST /api/v1/exams`와 `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`는 아직 기존 공통 변환을 사용해 tableContext를 전달하며 tableImageUrl은 채우지 않는다. 프론트가 어느 API로 문제를 렌더링하는지에 따라 후속 범위 확인이 필요하다.
- 현재 문항 번호 규칙에서 Part 4는 Question 8~10이다. 이번 확인은 읽기 전용이며 코드·테스트·외부 시스템과 Git·Jira를 변경하지 않았다.

## Latest deployed Part 4 response diagnosis (2026-08-06)

- 배포 후 관찰된 Part 4의 text·audioUrl·tableContext 배열은 `POST /api/v1/exams` 세션 생성 응답이다. 이 경로는 `createExamSession` → `toQuestionPrompt` → 기존 `toQuestionDTO`를 사용하므로 현재 동작과 일치한다.
- tableImageUrl-only 변경은 채점 결과 단건 `GET /api/v1/exams/{examId}/questions?questionNumber={number}&retryCount={optional}`의 `questionInfo` 변환에만 적용되어 있다. 세션 생성과 문제 prompt API에는 아직 적용되지 않았다.
- 실제 시험 문제 표시 경로도 이미지 URL 방식으로 바꾸려면 세션 생성 및 prompt의 Part 4 변환까지 후속 변경해야 한다. 사용자 제공 Presigned URL과 임시 자격·서명 값은 문서에 기록하지 않았으며 이번 진단에서는 코드·테스트·외부 시스템을 변경하지 않았다.

## Latest POST exam Part 4 table image response (2026-08-06)

- `POST /api/v1/exams`의 `result.questions`에서 Part 4는 기존 text·audioUrl을 유지하면서 DB `table_image_url`의 원본 값을 camelCase `tableImageUrl`로 반환하고 tableContext는 JSON에 노출하지 않는다.
- 세션 생성 전용 변환만 추가했으므로 Part 1·2·3·5·6·7, 기존 채점 결과 단건 `GET /api/v1/exams/{examId}/questions`, Summary·AI 계약은 유지된다. 내부 `Question.tableContext`와 Mongo 매핑도 보존한다.
- 별도 prompt `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`는 이번 명시 범위 밖이라 기존 tableContext 변환을 유지한다.
- 집중 테스트와 전체 `./gradlew clean test`가 성공했다. Java 295개, failures/errors/skipped 0개이며 `git diff --check`도 성공했다. 실제 외부 시스템과 Git·Jira 쓰기 작업은 수행하지 않았다.

## Latest Codex record synchronization (2026-08-06)

- 현재 turn hook 요구에 따라 Part 4 시험 시작 응답 구현 상태를 WORKLOG 끝에 append하고 CURRENT_STATE를 동기화했다. 구현 및 검증 결과는 직전 상태와 동일하며 애플리케이션·테스트 코드는 추가 변경하지 않았다.
- 별도 Jira 이슈 키가 없고 Secret·Token·Credential 및 사용자 제공 임시 URL 값은 기록하지 않았다. Git·Jira 쓰기 작업과 외부 시스템 호출도 수행하지 않았다.

## Latest GitHub Actions concurrency-test diagnosis (2026-08-07)

- `ExamSessionManagerTest.concurrentStartsLeaveExactlyOneActiveSessionAndNeverReuseExamId`의 CI 실패는 최종 세션 불변식이 아니라 `insert()` 정확히 3회라는 Mockito 검증에서 발생한다.
- latch 해제 후 snapshot을 읽는 현재 테스트에서는 스케줄에 따라 두 initial lookup이 모두 빈 목록을 보아 Duplicate Key와 3회 insert가 발생하거나, 두 번째 lookup이 첫 insert를 보아 정상 abandon 후 총 2회 insert로 완료될 수 있다. 둘 다 최종 active Session 1개와 서로 다른 신규 examId를 만족한다.
- 권장 방향은 snapshot을 latch 대기 전에 고정해 collision을 결정적으로 만들거나, 최종 동시성 불변식 테스트와 Duplicate Key 재시도 테스트를 분리하는 것이다. 이번 진단에서는 코드·테스트·외부 시스템 및 Git·Jira를 변경하지 않았다.

## Latest GitHub Actions concurrency-test resolution (2026-08-07)

- `ExamSessionManagerTest`의 동시 시작 테스트에서 스케줄링 의존적인 `insert()` 정확히 3회 검증을 제거하고, 기존 최종 불변식 검증은 유지했다.
- Duplicate Key retry는 별도 `duplicateKeyDuringSessionCreationRetries` 테스트로 분리했다. 첫 insert 예외, recursive retry, concurrent Session abandon, 두 번째 insert 성공과 정상 Assignment 반환을 Mockito로 결정적으로 검증한다.
- flaky 대상 테스트는 `--rerun-tasks`로 10회 반복해 10/10 성공했다. 전체 `./gradlew test`도 성공했고 Java 296개, failures/errors/skipped 0개다. `git diff --check`가 성공했으며 production 코드는 변경하지 않았다.
- 실제 외부 시스템과 Git·Jira 쓰기 작업은 수행하지 않았고 Secret·Token·Credential을 기록하지 않았다.

## Latest History response inspection (2026-08-07)

- 현재 `GET /api/v1/exams/history`는 JWT sub 사용자의 completedAt이 있는 Session 전체를 completedAt DESC, examId DESC로 반환한다. 결과는 totalCount와 histories이며 항목 필드는 examId, title, cycleNumber, completedAt, totalScore, levelEstimate, summaryAvailable이다.
- Controller가 page·size 또는 Pageable을 받지 않으므로 `?page=0&size=20`은 무시되며 실제 pagination metadata도 없다.
- 현재 main 소스·테스트·Git 이력에 retriedQuestionCount 필드는 없고 History 경로는 Question Job/문항 Result를 조회하지 않는다. 별도 retries API의 questions 크기는 retryCount 1 이상이 존재하는 서로 다른 문항 수지만 History에는 결합되지 않는다.
- 배포 응답에 retriedQuestionCount가 있다면 실행 이미지/커밋 또는 클라이언트·중간 계층 확인이 필요하다. 이번 확인에서는 코드·테스트·외부 시스템과 Git·Jira를 변경하지 않았다.

## Latest TMI-61 History retriedQuestionCount implementation (2026-08-07)

- `GET /api/v1/exams/history`의 각 history 항목에 primitive int `retriedQuestionCount`를 추가했다. 값은 해당 examId에서 `retryCount >= 1`이 존재하는 서로 다른 questionNumber 수이며 없으면 0이다.
- 완료 History examId 전체를 기준으로 QuestionGradingJob과 Legacy ExamResult 후보를 각각 batch 조회하고 `(examId, questionNumber)` Set으로 합친다. 여러 retry 회차와 Job/Legacy 중복은 한 문항으로 계산하며 dispatchAttempt는 사용하지 않는다. 기존 read 인덱스를 재사용하고 N+1 조회를 만들지 않았다.
- JWT sub, completedAt 완료 기준, History 정렬, 신규 Summary 우선·Legacy fallback, 기존 Retries·문항 단건·Summary API와 page·size 미지원 상태는 유지된다.
- 집중 테스트를 이번 turn에 재실행해 성공했고, 전체 `./gradlew clean test`도 Java 297개, failures/errors/skipped 0개로 성공했다. `git diff --check`가 성공했으며 실제 DB apply·explain, Git·Jira 쓰기 작업은 수행하지 않았다.

## Latest TMI-61 History response contract clarification (2026-08-07)

- 현재 `GET /api/v1/exams/history`의 `result`는 `totalCount` 및 `histories`로 구성된다. `exams` 배열과 page·size·totalElements·totalPages·hasNext 메타데이터는 없다.
- History 항목의 현재 필드는 `examId`, `title`, `cycleNumber`, `completedAt`, `totalScore`, `levelEstimate`, `summaryAvailable`, `retriedQuestionCount`다. `status`, `maxScore`, `startedAt`은 반환하지 않는다.
- `page`/`size` query parameter는 Controller에 바인딩되지 않아 무시되며 완료 이력 전체가 반환된다. `completedAt`은 `LocalDateTime`이므로 `Z`가 붙지 않는다.
- `retriedQuestionCount`는 `retryCount >= 1`이 존재하는 고유 `questionNumber` 수이며 Job/Legacy 중복과 다중 회차는 한 문항으로 계산한다. 이번 turn에서 애플리케이션·테스트 코드, Git·Jira는 변경하지 않았다.

## Latest TMI-61 History status/maxScore/startedAt implementation (2026-08-07)

- `GET /api/v1/exams/history`의 `histories` 항목에 `status`, `maxScore`, `startedAt`이 additive로 추가됐다. 기존 응답 필드와 `totalCount`/`histories` 구조는 그대로다.
- `status`는 `ExamSession.effectiveStatus()`를 사용해 Legacy 완료 세션도 `COMPLETED`로 보정하고, `startedAt`은 `ExamSession.createdAt`을 가공 없이 반환한다. Legacy 문서에 createdAt이 없으면 `startedAt` 또한 null이다.
- `maxScore`는 현재 모의고사 채점 계약의 고정 만점 200이다. 이 추가로 Repository·MongoDB 문서·인덱스는 변경하지 않았다.
- JWT sub, completedAt 완료 판정, completedAt DESC/examId DESC 정렬, Summary fallback, `retriedQuestionCount`, page·size 미지원은 유지된다.
- 집중 테스트와 `./gradlew clean test`가 성공했고 tests/failures/errors/skipped는 297/0/0/0이다. turn 종료 기록까지 반영했으며 `git diff --check`도 성공했고 Git·Jira 쓰기 작업은 수행하지 않았다.

## Latest TMI-61 History response shape (2026-08-07)

- 현재 성공 응답은 BaseResponse의 `isSuccess`, `code`, `message`, `result`를 사용하고, `result`는 `totalCount`와 `histories`로 구성된다.
- `histories` 항목은 `examId`, `title`, `status`, `cycleNumber`, `startedAt`, `completedAt`, `totalScore`, `maxScore`, `levelEstimate`, `summaryAvailable`, `retriedQuestionCount`를 반환한다.
- `status`는 `ExamSession.effectiveStatus()`, `startedAt`은 `ExamSession.createdAt`, `maxScore`는 200이다. Legacy createdAt 누락 세션은 `startedAt: null`이고 Summary가 없으면 `totalScore`/`levelEstimate`는 null, `summaryAvailable`는 false다.
- page·size는 현재 바인딩하지 않으며 pagination metadata도 없다. 응답 구조 안내 turn 종료 기록까지 반영했고, 코드·테스트·Git·Jira를 변경하지 않았다.

## Latest Part 4 tableImageUrl response-path audit (2026-08-07)

- `Question.tableImageUrl`은 MongoDB `table_image_url`에 명시적으로 매핑된다. URL은 재작성·presign·기본값 생성 없이 저장값을 사용한다.
- `POST /api/v1/exams` 시험 시작 응답은 Part 4 `questions[]` 항목에 `tableImageUrl`을 반환하고 `tableContext`는 제외한다.
- `GET /api/v1/exams/{examId}/questions?questionNumber=...&retryCount=...` 채점 결과 문항 단건의 Part 4 `questionInfo`는 `part`, `questionNumber`, `tableImageUrl`만 반환한다.
- 다만 `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`는 아직 `toQuestionDTO()` 경로라 `tableContext`를 매핑하고 `tableImageUrl`을 매핑하지 않는다. 이번 turn은 읽기 전용 확인으로 코드·테스트·Git·Jira를 변경하지 않았다.

## Latest TMI-61 Retries response-shape audit (2026-08-07)

- `GET /api/v1/exams/{examId}/retries`의 `result`는 `examId`, `questions`고, 문항 항목은 `partNumber`, `questionNumber`, `totalAttemptCount`, `latestRetryCount`, `attempts`를 반환한다.
- 각 `attempts[]`는 `retryCount`, `status` 두 필드만 반환한다. `score`, `completedAt`, 피드백·음성·Transcript는 노출하지 않는다. 상태는 `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`다.
- Job과 Legacy Result를 `(questionNumber,retryCount)`로 합치고 Job status를 우선하며, Legacy-only 회차는 `COMPLETED`다. 실제 저장된 회차만 retryCount 오름차순으로 반환한다.
- `retryCount >= 1`이 하나도 없는 문항은 제외하고 저장된 0회차는 포함하지만 없는 0회차를 생성하지 않는다. 응답 비교 turn 종료 기록까지 반영했고 코드·테스트·Git·Jira를 변경하지 않았다.

## Latest TMI-61 Retries score/completedAt implementation (2026-08-07)

- `GET /api/v1/exams/{examId}/retries`의 각 `attempts[]`는 `retryCount`, `status`, `score`, `completedAt`을 반환한다. `score`는 Double, `completedAt`은 UTC `Instant`이므로 JSON에서 `Z` suffix ISO-8601 문자열이다.
- `score`는 `ExamResult.score`, `completedAt`은 `QuestionGradingJob.completedAt`에서 가져온다. Legacy Result-only 회차는 `completedAt=null`, Job-only 회차는 `score=null`이다.
- Job/Result가 겹치면 Job status·completedAt과 Result score를 함께 보존한다. 기존 dedupe, Job 상태 우선, Legacy-only `COMPLETED`, 정렬, 소유권, 빈 결과 계약은 유지된다.
- Repository는 Result `score`와 Job `completedAt`만 추가 projection하고 피드백·Transcript·음성 URL·`dispatchAttempt`·내부 userId는 노출하지 않는다. MongoDB 문서·인덱스 변경은 없다.
- 집중 테스트와 `./gradlew clean test`가 성공했고 tests/failures/errors/skipped는 298/0/0/0이다. `git diff --check`도 성공했으며 Git·Jira 쓰기 작업은 수행하지 않았다.

## Latest TMI-77 Part 4 table_context implementation (2026-08-07)

- Jira `TMI-77` `[Learning Core] Part 4 table_context 원본 응답 통일`을 생성하고 구현한 뒤 사용자 요청에 따라 상태와 resolution을 `완료`로 전환했다. Jira 댓글·기타 필드는 변경하지 않았고 Git commit·push·PR은 생성하지 않았다.
- `Question.tableContext`와 응답 `QuestionDTO.tableContext`는 `Map<String, Object>`다. Mongo 최상위 `table_context`만 API `tableContext`로 연결하며 내부 임의 키, 중첩 객체·배열, null과 snake_case는 이름 변경이나 고정 구조 생성 없이 보존한다.
- 시험 시작 `POST /api/v1/exams`, 채점 결과 문항 단건 `GET /api/v1/exams/{examId}/questions`, 문제 prompt `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`의 Part 4가 동일한 원본 Map을 반환한다. 응답 DTO에는 `tableImageUrl`이 없으며 Mongo 내부 `table_image_url` 필드와 기존 데이터는 유지한다.
- Part 4 `table_context=null`은 기존 catalog configuration 오류, 빈 Map은 정상 빈 객체 응답이다. Part 1·2·3·5·6·7, BaseResponse와 URL·파라미터, AI 요청·Callback, Summary, JWT 소유권은 유지된다.
- 실제 `MappingMongoConverter`, 세 API JSON, null/empty, 다른 Part, AI dispatch, 문항 경로, JWT 집중 테스트와 전체 `./gradlew clean test`가 성공했다. 전체 tests/failures/errors/skipped는 `303/0/0/0`, `git diff --check`도 성공했다. 실제 MongoDB와 외부 인프라는 호출하거나 수정하지 않았다.
- 배포 전 프론트가 기존 Part 4 `tableImageUrl` 대신 비정형 `tableContext`와 DB 내부 키 이름을 그대로 처리하는지 확인해야 한다. 모든 운영 Part 4 문서에 `table_context`가 존재하는지도 별도 읽기 전용 점검이 필요하다.
- Jira `TMI-77` 완료 처리 turn의 WORKLOG 기록까지 반영됐다. 종료 처리 이후 애플리케이션·테스트 코드는 추가 변경하지 않았다.
- 운영 로그 후속 검토 결과, 세 API의 Part 4 성공/누락 로그는 아직 구현되지 않았다. 구현한다면 Service 경계에서 operation, examId, questionNumber, fieldCount만 INFO/WARN으로 남기고 tableContext 원문·키·값과 URL·Token은 금지하는 방향이다.

## Latest operations logging analysis (2026-08-07)

- 운영 로그 추가는 권장하지만, 현재 가장 큰 공백은 Summary dispatch, 시험 단위 retry 결과, Job 상태 전환과 submit-to-callback 지연 시간이다. 폴링 요청별 INFO 로그는 대량 중복을 만들므로 상태 전환만 기록해야 한다.
- 신규 로그는 안정적인 `event`/`outcome`과 `jobId`, `examId`, question/retry/attempt, `durationMs`, 제한된 reason만 사용한다. 실제 `userId`, 음성·Transcript·Callback/Table Context 원문, Presigned URL, Token·Secret은 기록하지 않는다.
- 기존 `ExamSessionManager`의 `userId`·abandoned ID 목록, `GradingDispatchService`의 `fileKey`, `GlobalExceptionAdvice`의 `printStackTrace()`·예외 원문은 새 로그 추가 전에 정리할 후보다. 현재 저장소에는 Sentry error와 Actuator health 외의 로그 집계·알림 구성이 확인되지 않아 배포 환경의 수집기·대시보드·알림 연결이 별도로 필요하다.
- 이번 검토에서는 애플리케이션·테스트 코드와 외부 API·AI/Callback·Redis/S3 계약을 변경하지 않았다. Jira `TMI-77`은 완료 상태로 유지하며 신규 Jira 작업이나 Jira/Git 쓰기는 수행하지 않았다.

## Latest AI communication logging cleanup (2026-08-07)

- 최초 Question AI 전송의 기본 로그는 기존 약 6줄에서 `event=grading.question.dispatch` 성공 한 줄로 줄었다. 실패는 Mongo Job 실패 전이가 실제 반영된 경우에만 ERROR이며, 오래된 attempt 실패는 DEBUG no-op이다. 로그에는 `jobId`, `examId`, question/retry/attempt와 `durationMs`만 남기고 URI, S3 key, 오디오 크기와 예외 메시지는 제외한다.
- Feedback Controller 수신 로그와 adapter 내부 POST 단계 로그는 제거했다. 핵심 Feedback/Summary 저장은 INFO 한 줄, 중복 및 SpeechAce·Azure 보조 Callback은 DEBUG다. Summary dispatch 성공/실패, executor rejection과 시험 단위 retry 집계는 별도 단일 이벤트로 추적한다.
- 시험 생성 로그는 실제 `userId`와 abandoned ID 목록 없이 생성 결과 한 줄만 남긴다. 전역 JSON 파싱 오류도 `printStackTrace()`와 원문 로그 대신 예외 타입만 포함한 WARN 한 줄로 정리했다.
- 로그 전용·동시성 집중 테스트와 전체 `./gradlew clean test`가 성공했다. 전체 tests/failures/errors/skipped는 `303/0/0/0`이고 `git diff --check`도 성공했다. 외부 API·DTO·BaseResponse, AI `user_id=examId`와 Callback JSON, retryCount, Redis/S3 계약은 그대로다.
- 관련 Jira `TMI-25`와 `TMI-77`은 완료 상태를 유지하며 Jira 쓰기 작업은 없었다. 로그 집계·대시보드·알림 연결과 request/trace correlation은 아직 별도 운영 과제로 남는다.

## Latest monitoring logging plan (2026-08-07)

- 이번 계획 작업에는 별도 Jira 이슈 키가 없다. 현재 워킹 트리의 기존 구조화 로그와 미커밋 로그 정리 변경을 보존했으며 애플리케이션·테스트 코드는 수정하지 않았다.
- 현재 정상 흐름은 세션 생성, Question/Summary dispatch, 시험 retry 집계와 핵심 Callback 저장 로그로 일부 연결된다. 다음 구현 우선순위는 세션 폐기·완료, Question/Summary Job 완료와 최대 attempt, Summary Trigger 결정, Callback 거절, 안전한 인증/비즈니스/5xx 오류 경계다.
- Polling 성공을 요청마다 INFO로 기록하지 않고 상태 전이와 장기 PROCESSING만 관측한다. 내부 requestId/MDC는 동기 요청을 연결하고, submit과 별도 Callback 요청은 외부 계약 변경 없이 `examId`·`jobId`로 연결한다.
- 외부 I/O 장애는 Presigned URL·S3 Key·예외 메시지를 남기지 않은 채 S3 다운로드와 AI POST 단계, 결과, attempt, 소요 시간만 구분한다. INFO/WARN/ERROR 기준과 안정적인 key-value 이벤트명을 먼저 고정하고 Sentry의 중복 오류 이벤트도 방지한다.
- 실제 사용자 ID, Token·Secret, 음성·Transcript, Callback/Feedback/Azure/SpeechAce/Table Context 원문과 내부 키·값은 로그 금지 대상이다. 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, retryCount, Redis와 S3 계약은 그대로 유지한다.
- 구현 시 로그 캡처 단위 테스트, 중복·동시성 회귀, 민감값 부재 검사를 추가한 뒤 `./gradlew clean test`와 `git diff --check`를 실행한다. CloudWatch 등 실제 수집 대상과 retention이 저장소에 없으므로 배포 전 확정하고 장기 PROCESSING, dispatch 실패, queue rejection, max attempts, completion race와 5xx 알림을 연결해야 한다.

## Latest monitoring logging implementation (2026-08-07)

- 별도 Jira 이슈 키 없이 운영 모니터링 로그 계획을 구현했다. 요청 필터가 내부 UUID `requestId`를 MDC에 설정·정리하고 Summary executor의 TaskDecorator가 MDC를 작업 스레드에 복사·복원한다. requestId를 외부 응답 헤더나 DTO에는 추가하지 않았다.
- 시험 세션 폐기·충돌 재시도·완료, Question/Summary Job 완료·최대 attempt, Summary Trigger 판단·예약, Callback 분류 거절, 소유권 거절과 조회 데이터 누락을 구조화 이벤트로 관측한다. 일반 요청과 정상 Polling은 DEBUG이며, 중요한 상태 전이는 정확히 한 번 기록되도록 멱등·동시성 경로를 보존했다.
- 외부 dispatch는 `s3_download`와 `ai_post` stage 및 `durationMs`로 실패 위치를 구분한다. 로그와 Sentry에는 실제 userId, Authorization/JWT, Secret·Token, URL·S3 Key, 음성·Transcript, Callback/채점/tableContext payload, 예외 메시지를 넣지 않는다. 잘못된 JSON은 Sentry 대상에서 제외하고 예상하지 못한 5xx만 예외 타입 tag를 포함한 안전한 단일 메시지 이벤트로 전송한다.
- 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, retryCount, Redis Key/TTL과 S3 Object Key는 유지했다. 실제 외부 인프라는 호출하지 않았다.
- 집중 테스트와 `./gradlew clean test --no-daemon`이 성공했다. 전체 tests/failures/errors/skipped는 `316/0/0/0`이며 `git diff --check`도 성공했다.
- CloudWatch 로그 그룹·retention·metric filter·dashboard·alarm은 저장소 외부 운영 설정으로 남아 있다. Callback이 아예 도착하지 않는 경우와 장기 `PROCESSING` Job은 로그만으로 직접 검출할 수 없어 별도 metric/watchdog 결정이 필요하다.

## Latest monitoring log language recommendation (2026-08-08)

- 운영자가 빠르게 읽을 수 있도록 로그의 고정 설명 문장은 한글화하는 방향을 권장한다. 다만 자동 검색·집계·알림 계약인 `event`, `outcome`, `reason`, `stage`, 상태값과 필드명은 영문으로 유지한다.
- 권장 형식은 `문항 채점 작업 완료 event=grading.question.job.completed outcome=success ...`와 같은 혼합형이다. 기존 event code를 유지하면 향후 CloudWatch metric filter와 dashboard를 언어에 의존하지 않고 구성할 수 있다.
- 한글 설명에는 예외 메시지나 payload를 삽입하지 않는다. 실제 userId, Token, URL·S3 Key, 음성·Transcript, Callback/채점/tableContext 원문을 남기지 않는 기존 로그 보안 원칙도 유지한다.
- 이번 검토에서는 운영 코드와 테스트를 변경하거나 실행하지 않았다. 한글화 구현 여부와 적용 범위는 사용자 확인 후 결정한다.

## Latest monitoring log Korean descriptions (2026-08-08)

- 운영 로그의 사람이 읽는 고정 설명을 한글로 변경했다. 적용 범위는 HTTP 요청·인증·전역 예외, 시험 세션·소유권·이력, Question/Summary 채점 Job·Trigger·dispatch, Callback, S3/AI 단계, MongoDB 인덱스와 MockExam 카탈로그다.
- 자동 검색·집계·알림 계약인 `event`, `outcome`, `reason`, `stage`, 상태값과 구조화 필드명은 영문으로 유지했다. HEAD 대비 현재 `event` 코드 목록의 정적 비교 결과가 동일하다.
- 실제 userId, Authorization/JWT, Secret·Token, URL·S3 Key, 음성·Transcript, Callback/채점/tableContext payload와 예외 메시지를 로그에 넣지 않는 보안 원칙은 유지한다. 외부 API·AI/Callback·Redis/S3 계약도 변경하지 않았다.
- 한글 설명과 기존 event code의 동시 출력을 대표 로그 캡처 테스트로 검증했다. 집중 테스트와 전체 `./gradlew clean test --no-daemon`이 성공했으며 tests/failures/errors/skipped는 `316/0/0/0`, `git diff --check`도 성공했다.
- CloudWatch 등 수집 환경은 UTF-8 출력을 기준으로 확인해야 한다. metric filter·dashboard·alarm은 한글 문장보다 유지된 `event` 코드와 영문 구조화 필드를 기준으로 구성한다.

## Latest Sentry DSN readiness audit (2026-08-10)

- Sentry Spring Boot starter가 이미 포함되어 있고 `application.yml`은 실제 값을 저장하지 않은 채 `SENTRY_DSN` 환경변수를 참조한다. DSN 자체를 코드 변경이나 채팅으로 전달할 필요가 없다.
- 기본 설정은 `send-default-pii=false`, active profile 기반 environment, trace sampling 0이며 테스트는 비운영 DSN과 sampling 0을 사용한다. 전역 예외 처리의 예상하지 못한 5xx는 안전한 고정 메시지와 예외 타입 tag로 명시적으로 수집한다.
- 적용 단계는 배포 환경 Secret에 `SENTRY_DSN`을 등록하고 애플리케이션을 재시작한 뒤 의도적으로 발생시킨 비민감 테스트 오류가 해당 environment로 수집되는지 확인하는 것이다. tracing이 필요할 때만 `SENTRY_TRACES_SAMPLE_RATE`를 별도로 결정한다.
- 이번 확인에서는 운영 코드·테스트·외부 시스템을 변경하지 않았고 실제 DSN이나 Secret을 조회·기록하지 않았다.

## Latest Sentry production-readiness assessment (2026-08-10)

- 현재 설정은 DSN 환경변수, `send-default-pii=false`, ERROR 자동 수집, trace sampling 0과 4xx WARN 제외를 사용하므로 초기 오류 수집과 노이즈 제어 관점에서는 적절하다.
- 운영 분석의 최우선 공백은 예상하지 못한 5xx가 안전한 `captureMessage`로만 수집되어 예외 타입 tag는 있지만 원본 스택트레이스가 없다는 점이다. 예외 메시지·payload를 제거하면서 스택 프레임을 보존하는 안전한 Sentry event sanitizing 설계와 테스트가 필요하다.
- 배포 추적을 위해 `SENTRY_RELEASE`, 환경 오분류 방지를 위해 명시적인 `SENTRY_ENVIRONMENT`를 배포 Secret/환경변수로 주입하는 것을 권장한다. tracing은 성능 관측 요구가 생길 때 별도 sampling 정책을 정한다.
- DSN 주입 후 staging의 비민감 5xx 테스트로 수집, environment/release, 중복 이벤트, stack trace, PII 부재를 확인하고 Sentry 프로젝트의 Alert Rule도 별도로 구성해야 한다.
- 이번 검토에서는 코드·테스트·외부 시스템을 변경하지 않았고 실제 자격정보를 조회·기록하지 않았다.

## Latest Sentry production hardening plan (2026-08-10)

- 상세 계획서는 `docs/codex/SENTRY_PRODUCTION_HARDENING_PLAN.md`에 있다. 별도 Jira 이슈 키는 없다.
- P0 순서는 `SENTRY_ENVIRONMENT`·`SENTRY_RELEASE` 명시, `BeforeSendCallback` sanitizer, `IHub` 기반 reporter와 안전한 `captureException`, 인메모리 transport 중복·PII 테스트, staging smoke 검증이다.
- sanitizer는 exception value와 자유 형식 message, request data/query/cookie/header, user와 비허용 breadcrumb/extra를 제거하고 예외 type·module·mechanism·stack frame 및 제한된 분류 tag만 보존한다.
- 기본 order 1의 자동 `SentryExceptionResolver`, 명시적 reporter와 Logback ERROR 경로가 겹칠 수 있으므로 5xx 1건, 4xx 0건, grading ERROR 의도 건수를 통합 테스트로 고정한다.
- SDK 8.x 업데이트와 Sentry Alert Rule은 capture 동작을 현재 7.14.0에서 먼저 고정한 후 별도 P1 단계로 진행한다. tracing은 초기 0을 유지하고 실제 DSN은 배포 Secret으로만 주입한다.
- 계획 작성만 완료했으며 운영·테스트 코드와 외부 시스템은 변경하지 않았다. 구현 전 배포 환경변수 주입 방식, release 규칙, grading ERROR 수집 정책, Alert 임계값과 SDK 업데이트 순서를 확정해야 한다.

## Latest Sentry production hardening plan revision (2026-08-11)

- 상세 계획서는 `docs/codex/SENTRY_PRODUCTION_HARDENING_PLAN.md`에 있다. 별도 Jira 이슈 키는 없고 아직 애플리케이션 구현 전이다.
- 초기 운영 역할은 `Sentry=조사가 필요한 예외`, `CloudWatch=구조화 운영 로그`로 확정했다. `sentry.logging.enabled=false`로 Sentry LogbackAppender를 끄고, 예상하지 못한 Controller 5xx만 명시적 `captureException` 1건과 안전한 CloudWatch ERROR 1건으로 남긴다. grading·AI dispatch·Callback ERROR는 초기 Sentry event 0건이다.
- `BeforeSendCallback`은 fail-closed로 동작한다. event·exception message뿐 아니라 request·transaction·user·breadcrumb·비허용 tag/extra/context, stack local·절대 경로·source context·register·lock, mechanism 자유 형식 map과 SDK unknown field를 제거하고 실패하면 원본 대신 event를 drop한다.
- `BeforeSendCallback`이 attachment와 tracing transaction까지 정제하지는 않으므로 초기 reporter의 attachment를 금지하고 envelope item 0건을 검사한다. tracing은 0을 유지하며 활성화 전 별도 transaction sanitizer 검토가 필요하다.
- Sentry에는 저카디널리티 오류 분류와 형식 검증된 `correlation.request_id` context만 허용한다. examId·jobId·questionNumber·retryCount는 CloudWatch에서 조회하며 release는 CI source context와 ECS runtime 모두 `app-back-end-learning-core@<git-sha>`로 맞춘다.
- SDK 7.14.0에서 sanitizer·호출별 local scope reporter·자동 resolver·recording transport의 정확히 1회 동작을 먼저 고정한다. 모든 위치의 가짜 민감 marker가 최종 직렬화 JSON에 없는지, 일반 ERROR의 Sentry event가 0건인지, 연속 capture 간 metadata가 누출되지 않는지와 reporter 실패에도 기존 응답 계약이 유지되는지를 테스트하고 8.x 업그레이드는 별도 이슈로 분리한다.
- Spring MVC 6.2.2의 기본 exception resolver composite order 0과 Sentry resolver order 1을 확인했다. Advice가 처리한 예외는 명시적 reporter만, 앞선 resolver가 처리하지 못한 예외는 자동 resolver만 타는 현재 전제를 통합 테스트의 최종 event 건수로 고정한다.
- 실제 DSN·Secret·Token은 기록하지 않고 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, `retryCount`, Redis Key/TTL과 S3 Object Key를 유지한다. 이번 보정은 문서만 변경했으며 배포 workflow·Sentry 프로젝트·애플리케이션·테스트 코드는 변경하지 않았다.
- 추적 문서 정적 검증으로 tracked 변경과 신규 계획서 모두 whitespace 오류가 없음을 확인했다. 문서 전용 작업이라 Gradle 테스트는 실행하지 않았다.

## Latest Sentry production hardening implementation (2026-08-11)

- 별도 Jira 이슈 키 없이 `docs/codex/SENTRY_PRODUCTION_HARDENING_PLAN.md`의 P0 애플리케이션 구현과 자동 검증을 완료했다. 실제 배포 환경과 외부 Sentry 프로젝트는 변경하지 않았다.
- 설정은 `SENTRY_ENVIRONMENT`·`SENTRY_RELEASE` 환경변수를 지원하고 request body 비수집, resolver order 1, `sentry.logging.enabled=false`, trace sampling 기본 0을 명시한다. 따라서 일반 grading·AI dispatch·Callback ERROR는 CloudWatch에 남고 Logback을 통해 Sentry Issue로 자동 승격되지 않는다.
- 예상하지 못한 ControllerAdvice 5xx는 `UnexpectedExceptionReporter`를 통해 `captureException` 정확히 1건과 원문 없는 한글 CloudWatch ERROR 정확히 1건을 남긴다. 4xx와 JSON 파싱·비즈니스 오류는 reporter를 호출하지 않으며 reporter 실패도 기존 응답을 바꾸지 않는다.
- `SentryEventSanitizer`는 fail-closed `BeforeSendCallback`으로 event/exception message, request/user/breadcrumb, transaction/fingerprint, 비허용 tag·context·extra, module/dist, stack local·절대 경로·source context·register·lock·주소, mechanism 자유 형식 map과 unknown field를 제거한다. 예외 type과 애플리케이션 stack class/method/file/line, environment/release, 안전한 분류와 UUID requestId context만 남긴다.
- reporter의 local scope는 parent request/user/breadcrumb/attachment뿐 아니라 session·propagation baggage·replay ID까지 초기화한다. 기본 Sentry resolver도 `SanitizedSentryExceptionResolver`로 교체해 같은 격리 reporter를 사용하고, `UnhandledExceptionCaptureFilter`는 하위 필터의 `ServletException`·`RuntimeException`을 1회 보고하며 request attribute로 resolver 중복을 막는다. `IOException`은 연결 종료 노이즈 가능성 때문에 자동 Issue로 보내지 않는다.
- 인메모리 recording transport에서 연속 두 capture의 scope 누출 없음, event·envelope 전체의 가짜 민감 marker 부재, attachment/session item 부재, stack 보존, handled/unhandled 정확히 1건, sanitizer 실패 시 0건, Logback initializer 부재를 검증했다. 실제 외부 Sentry는 호출하지 않았다.
- 최종 `./gradlew clean test --no-daemon`은 tests/failures/errors/skipped `332/0/0/0`, `git diff --check`와 민감정보 패턴 검사는 성공했다. 기존 `ExamServiceImpl` unchecked 경고만 남았으며 이번 범위와 무관하다.
- 공개 API URL·Method·Request/Response DTO·`BaseResponse`, 실제 userId 비노출, AI/Callback `user_id=examId`, `retryCount`, Redis Key/TTL과 S3 Object Key는 변경하지 않았다. 기존 500 응답 body가 내부 예외 메시지를 담을 수 있는 위험도 호환성 때문에 이번 작업에서는 변경하지 않았다.
- 운영 전 남은 작업은 실제 배포 환경의 DSN·environment·`app-back-end-learning-core@<git-sha>` release 주입, CI/ECS 값 일치 확인, staging smoke, Sentry IP 저장 방지와 Alert Rule 설정이다. SDK 7.14.0에서 8.x 업그레이드와 tracing 활성화는 별도 작업으로 유지한다.

## Latest Sentry deployment environment variables (2026-08-11)

- 별도 Jira 이슈 키 없이 현재 `application.yml`, Dockerfile과 배포 파일 존재 여부를 기준으로 Sentry 환경변수를 정리했다. 저장소에는 ECS Task Definition이나 GitHub Actions 배포 Workflow가 없으므로 실제 환경변수 주입은 아직 저장소 밖 배포 설정에서 수행해야 한다.
- staging/prod 런타임 필수 항목은 보호 저장소에서 주입할 `SENTRY_DSN`, 명시적인 `SENTRY_ENVIRONMENT=staging|prod`, 배포 산출물의 전체 Git SHA를 사용한 `SENTRY_RELEASE=app-back-end-learning-core@<git-sha>`다. 같은 배포의 CI release 값과 ECS runtime 값을 반드시 일치시킨다.
- `SENTRY_TRACES_SAMPLE_RATE`는 선택 항목이며 현재는 미설정 또는 `0.0`으로 유지한다. tracing을 활성화하기 전에는 transaction 데이터 정제와 sampling 비용 정책을 별도로 검토해야 한다.
- 기존 `SPRING_PROFILES_ACTIVE`는 Sentry 전용 신규 값은 아니지만 staging/prod에서 각각 명시해야 한다. `SENTRY_ENVIRONMENT`가 없으면 Spring profile로 fallback하지만 운영 오분류 방지를 위해 두 값을 모두 명시한다.
- `SENTRY_AUTH_TOKEN`, `SENTRY_ORG`, `SENTRY_PROJECT`는 현재 런타임 이벤트 전송에 필요하지 않으며 추가하지 않는다. 향후 CI에서 Sentry release 생성이나 source context 업로드를 도입할 때만 별도 최소 권한 CI Secret으로 검토한다.
- 실제 DSN·Secret·Token은 조회하거나 기록하지 않았다. 애플리케이션·테스트·배포 파일과 외부 API·AI/Callback·Redis/S3 계약은 변경하지 않았고, 문서 갱신 외 코드 변경이 없어 Gradle 테스트는 다시 실행하지 않았다.

## Latest empty Summary feedback recovery plan (2026-08-11)

- 별도 신규 Jira 이슈 키는 제공되지 않았으며, 기존 `TMI-25`에서 구현한 시험 단위 `POST /api/v1/exams/{examId}/grading/retry`와 Question/Summary Job 흐름을 활용하는 후속 계획이다. 상세 내용은 `docs/codex/FEEDBACK_GENERATION_RECOVERY_PLAN.md`에 있다.
- 현재 Summary Callback은 `partFeedback` null/empty를 검사하지 않아 빈 객체도 `ExamSummary` 저장, Summary Job 완료와 ExamSession 완료로 이어질 수 있다. 계획은 빈/null Map이면 Summary를 저장하지 않고 Job을 `FAILED`, reason=`FEEDBACK_GENERATION_FAILED`로 만들며 Session을 `IN_PROGRESS`로 유지한다.
- 프론트의 주 오류 수신 경계는 기존 `GET /api/v1/exams/{examId}/status`로 권장했다. 기존 `BaseResponse` 구조에서 exact code `FEEDBACK_GENERATION_FAILED`, message `피드백 생성에 실패했습니다.`를 non-2xx로 반환하고, Summary 조회도 같은 상태에서 동일 오류를 반환한다. AI Callback은 실패 상태를 영속화한 뒤 200 delivery acknowledgement를 반환하는 안을 권장한다.
- Summary 시작 근거는 배정된 MockExam의 모든 필수 문항에 대한 실제 최초 `ExamResult` 존재로 강화한다. 현재 문제지는 카탈로그 계약 테스트로 1~11번을 고정하되 별도 하드코딩 목록이나 프론트 입력을 추가하지 않는다. `QuestionGradingJob=COMPLETED`만 있고 결과가 없는 문항은 `QUESTION_RESULT_MISSING` 복구 대상으로 취급한다.
- 프론트 retry가 들어오면 실패한 Summary Job을 다음 generation의 `PENDING`으로 먼저 재무장한다. 모든 결과가 이미 있으면 Question dispatch 없이 Summary만 예약하고, 누락 문항이 있으면 해당 문항만 복구한 뒤 마지막 valid Callback에서 PENDING Summary를 한 번 예약한다.
- 반복 재생성을 위해 내부 `generationAttempt`와 기존 `dispatchAttempt` 분리를 권장했다. Summary document/job ID와 JSON 계약은 유지하되 generation 2 이상 멱등 키를 구분해야 실제 AI 재생성이 보장되므로 Python AI의 `Idempotency-Key` 캐시 정책 확인이 구현 전 필수다. Callback JSON에 generation이 없어 stale Callback 완전 구분은 불가능하므로 valid Summary와 COMPLETED가 항상 우선하도록 단조성을 보장한다.
- 기존에 이미 빈 Summary가 저장되고 Session이 COMPLETED인 데이터는 새 Callback 검증만으로 복구되지 않는다. 배포 전 읽기 전용 집계 후 별도 승인된 복구 runbook 범위를 정하며, 계획 작업에서 운영 데이터나 완료 Session을 변경하지 않았다.
- 이번 turn은 계획·상태·작업 기록 문서만 변경했다. 애플리케이션·테스트·외부 시스템은 변경하지 않았고 Gradle 테스트도 실행하지 않았다. 실제 Secret·Token과 Callback payload를 조회하거나 기록하지 않았다.

## Latest Summary generation fencing plan revision (2026-08-11)

- 별도 신규 Jira 이슈 키는 제공되지 않았고, 기존 `TMI-25`의 시험 단위 grading retry를 활용하는 `docs/codex/FEEDBACK_GENERATION_RECOVERY_PLAN.md`에 사용자가 확정한 `generationAttempt` 계약과 동시성 보강안을 반영했다.
- Question AI Request/Callback은 그대로 유지한다. Summary에만 `generation_attempt`를 추가하며 Learning Core가 generation을 생성·증가시키고 Python AI는 요청 값을 Callback에 그대로 echo한다. Callback 값이 누락되거나 현재 `SummaryGradingJob.generationAttempt`와 다르면 empty/valid 여부와 관계없이 stale no-op한다.
- `generationAttempt`는 `FAILED/FEEDBACK_GENERATION_FAILED` 상태에서 사용자가 기존 grading retry API를 호출할 때만 1 증가한다. 새 generation은 `PENDING`, `dispatchAttempt=0`으로 시작하고 동일 generation의 transport timeout·전송 실패·retry는 generation을 유지한 채 dispatch attempt만 증가한다. legacy Mongo Job의 누락 필드는 generation 1로 해석한다.
- Summary Scheduler, claim, dispatch 전 검증과 완료·실패 갱신에 generation fencing을 적용한다. 이전 generation worker는 새 generation의 status·dispatch attempt를 바꾸지 않고, 전송 직전 stale이면 외부 요청도 보내지 않는다. 이미 전송된 이전 요청의 Callback은 Callback generation fence로 차단한다.
- Callback의 최초 generation 조회와 실제 Summary 저장 사이 경합도 막기 위해 저장 직전 `jobId + generation + status + version` completion claim 또는 Mongo transaction을 요구한다. 전체 COMPLETED 판정은 유효 Summary 저장 결과까지 확인해 Job만 완료된 중간 상태를 외부 완료로 노출하지 않는다.
- 실제 최초 `ExamResult`가 없고 Question Job만 COMPLETED이면 `QUESTION_RESULT_MISSING`으로 분류한다. Job을 version/recovery-cycle 조건으로 PENDING re-open하고 `dispatchAttempt=0`으로 초기화해 새 복구 사이클의 max attempt 정책을 적용하며, Question wire 계약은 변경하지 않는다.
- Summary Idempotency-Key는 generation 1에서 기존 Job ID, generation 2 이상에서 `:generation:<n>` suffix를 사용한다. 같은 generation transport retry는 같은 키를 쓴다. 프론트는 기존 status/Summary 조회의 HTTP 500 `FEEDBACK_GENERATION_FAILED` exact code로 기존 grading retry를 호출하고 다른 5xx/FAILED 동작은 유지한다.
- 배포는 Python AI의 generation echo를 먼저 적용하되 전환 기간 동안 필드 없는 구버전 Summary 요청을 generation 1로 처리해 echo한 뒤 Learning Core를 배포한다. staging에서 generation별 키 독립 처리, 구버전/in-flight Callback, stale worker를 확인하고 기존 empty Summary는 별도 읽기 전용 집계와 승인된 복구 runbook으로 분리한다.
- 이번 turn은 계획·상태·작업 기록 문서만 변경했다. 애플리케이션·테스트·외부 시스템을 변경하지 않았고 Gradle 테스트를 실행하지 않았다. 실제 Secret·Token·Callback payload를 조회하거나 기록하지 않았으며 공개 프론트 API·DTO·Redis/S3 계약도 변경하지 않았다.

## Latest Summary generation recovery implementation (2026-08-11)

- Jira `TMI-25` 후속 범위로 `docs/codex/FEEDBACK_GENERATION_RECOVERY_PLAN.md`의 Summary 재생성과 Question 결과 누락 복구를 구현했다. 신규 공개 API는 추가하지 않았고 기존 `POST /api/v1/exams/{examId}/grading/retry`의 body 없음 및 `GradingRetryResult` 계약을 유지한다.
- `SummaryGradingJob`은 legacy 누락 값을 1로 해석하는 `generationAttempt`와 completion claim을 가진다. `FAILED/FEEDBACK_GENERATION_FAILED`에서 사용자가 retry할 때만 Mongo 조건부 update로 다음 generation을 열고 `dispatchAttempt=0`, `PENDING`으로 재무장한다. transport retry는 같은 generation에서 dispatch attempt만 증가한다.
- Summary Request와 Callback에만 `generation_attempt`를 추가했다. generation 1은 기존 `summary:<examId>:v1`, generation 2 이상은 `summary:<examId>:v1:generation:<n>` Idempotency-Key를 사용한다. Scheduler task와 dispatch claim은 generation을 캡처하고 claim 전·AI 전송 직전·실패 갱신에서 현재 generation을 확인한다.
- Summary Callback은 generation 누락·불일치를 empty/valid 모두 stale no-op한다. 현재 generation의 null/empty `partFeedback`은 Summary를 저장하거나 Session을 완료하지 않고 Job을 `FAILED/FEEDBACK_GENERATION_FAILED`로 만든다. valid Callback은 generation 조건의 completion claim을 먼저 획득한 뒤 결정적 Summary 저장, Job COMPLETED, Session COMPLETED, 기존 Redis status projection 순으로 수렴한다.
- 실제 최초 `ExamResult`만 Summary 준비 근거로 사용한다. 결과 없이 `QuestionGradingJob=COMPLETED`이면 `QUESTION_RESULT_MISSING`으로 보고 원자적으로 PENDING re-open하며 `dispatchAttempt=0`과 새 내부 recovery cycle로 해당 문항만 다시 보낸다. stale 이전 cycle의 전송 실패는 새 cycle 상태를 변경하지 않는다.
- 기존 status와 Summary 조회는 유효 Summary가 없는 현재 Summary Job의 `FAILED/FEEDBACK_GENERATION_FAILED`를 HTTP 500, code `FEEDBACK_GENERATION_FAILED`, message `피드백 생성에 실패했습니다.`의 기존 `BaseResponse`로 반환한다. 다른 5xx/FAILED 처리와 공개 프론트 DTO는 변경하지 않았다.
- 공개 API URL·Method·기존 Request/Response DTO·`BaseResponse`, `user_id=examId`, `retryCount`, Question AI Request/Callback, Redis Key/TTL, S3 Object Key와 소유권 검증을 유지했다. 실제 외부 Python AI, MongoDB, Redis, S3, Sentry와 배포 환경은 호출하거나 변경하지 않았다.
- 최종 `./gradlew clean test`는 tests/failures/errors/skipped `351/0/0/0`으로 성공했고 `git diff --check`도 성공했다. 기존 `ExamServiceImpl` unchecked 경고는 이번 범위와 무관하게 유지했다.
- 배포 전 Python AI의 generation echo와 generation별 Idempotency-Key 독립 처리를 먼저 적용해야 한다. generation 없는 in-flight 구버전 Callback 가능성과 기존 empty/null Summary 데이터는 staging 확인 및 별도 승인된 읽기 전용 집계·복구 runbook 대상으로 남아 있다.

## Latest daily work summary for 2026-08-11 through 2026-08-13 (2026-08-14)

- 기존 작업 기록의 연속된 주제와 진행 순서를 날짜별로 재분류했다. 원래 WORKLOG 항목은 변경하거나 삭제하지 않았으며, 상세 정정 요약은 WORKLOG의 2026-08-14 항목에 있다.
- **2026-08-11:** 별도 Jira 이슈 없이 Sentry 운영 보완 정책 확정, fail-closed event sanitizing, 예상하지 못한 5xx 단일 수집, scope 격리와 recording transport 검증을 구현했다. 전체 테스트 결과는 `332/0/0/0`이다.
- **2026-08-12:** Jira `TMI-25` 후속 범위로 빈 Summary feedback 실패 처리, 사용자 retry 기반 `generationAttempt`, generation fencing과 누락 Question 결과 복구 계획을 확정했다. 계획·상태 문서만 변경한 단계다.
- **2026-08-13:** Jira `TMI-25` 후속 Summary 재생성과 Question 결과 누락 복구를 구현하고 stale worker/Callback 및 동시 retry 회귀 테스트를 보강했다. 전체 테스트 결과는 `351/0/0/0`이다.
- 공개 API·DTO·`BaseResponse`, `retryCount`, AI/Callback `user_id=examId`, Redis Key/TTL, S3 Object Key와 시험 소유권 검증 계약은 유지됐다.
- 남은 배포 확인 사항은 Python AI의 `generation_attempt` echo와 generation별 Idempotency-Key 독립 처리, in-flight legacy Callback, 기존 empty/null Summary 데이터의 읽기 전용 집계 및 승인된 복구 runbook이다.
- 이번 날짜별 정리는 문서만 변경했으며 Gradle 테스트는 다시 실행하지 않았다. 외부 시스템·Jira 상태·Git commit·push는 변경하지 않았다.

## Latest in-chat blog draft delivery (2026-08-14)

- 사용자의 정정에 따라 2026-08-11부터 2026-08-13까지의 작업 내용을 저장소 문서가 아닌 대화창에서 사용할 블로그 글 초안으로 제공한다.
- 구성은 8월 11일 Sentry 운영 보완, 8월 12일 Jira `TMI-25` 후속 Summary 복구 설계, 8월 13일 Summary generation 및 누락 Question 결과 복구 구현·검증 순서다.
- 애플리케이션 동작과 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, `retryCount`, Redis Key/TTL, S3 Object Key 및 소유권 검증 계약은 변경하지 않았다.
- 이번 turn은 응답 작성과 작업 기록 갱신만 수행했으며 Gradle 테스트는 실행하지 않았다. 외부 시스템·Jira 상태·Git commit·push도 변경하지 않았다.

## Latest part score retry inclusion analysis (2026-08-17)

- 별도 Jira 이슈 키 없이 `GET /api/v1/exams/{examId}/summary`의 `partScores` 산정 경로를 확인했다.
- 현재 `ExamServiceImpl.getExamSummary()`는 시험의 모든 `ExamResult` 중 `questionNumber`와 `score`가 있는 모든 문서를 파트별로 합산하며, `retryCount`를 필터링하지 않는다. 따라서 최초 응시와 재시도 점수가 모두 `partScores`에 더해지는 현상이 맞다.
- `totalSolvedQuestions`는 `retryCount == 0`만 카운트하고, `totalScore`는 종합 문서에 저장된 값을 사용하므로 위 영향은 `partScores`에 한정된다.
- 코드를 수정하지 않았고 공개 API·DTO·`BaseResponse`, AI/Callback, `retryCount`, Redis/S3 계약을 변경하지 않았다. 정적 분석과 문서 기록만 수행해 Gradle 테스트는 실행하지 않았다.
- 후속 수정 전에 최초 응시만 사용할지, 문항별 최신 재시도나 최고 점수를 사용할지 산정 정책을 확정해야 한다.

## Latest initial-attempt-only part score implementation (2026-08-17)

- 별도 Jira 이슈 키 없이 `GET /api/v1/exams/{examId}/summary`의 `partScores`를 `retryCount == 0`인 최초 응시 결과만 파트별로 합산하도록 변경했다.
- `retryCount>0` 재시도 결과와 `retryCount=null` legacy 결과는 `partScores`에서 제외된다. `totalScore`와 `totalSolvedQuestions` 로직은 변경하지 않았다.
- 회귀 테스트로 최초 응시 점수만 합산되고 재시도와 null 회차가 제외되는 것을 검증했다. 최종 `./gradlew clean test`는 tests/failures/errors/skipped `352/0/0/0`, `git diff --check`는 성공했다.
- 공개 API URL·Method·DTO 필드·`BaseResponse`, AI/Callback `user_id=examId`, `retryCount` 의미, Redis/S3와 소유권 검증 계약은 유지했다.
- 배포 전 실제 데이터에 `retryCount=null`인 최초 응시 문서가 존재하는지 확인할 수 있다. 명시적 요청은 `retryCount=0`만 포함하는 것이므로 현재 구현은 null을 최초 응시로 간주하지 않는다.

### Completion record sync (2026-08-17)

- 종료 hook에서 요구한 WORKLOG 보완 항목을 추가했다. 현재 구현·테스트 결과·외부 계약·legacy `retryCount=null` 위험은 위 최신 상태와 동일하며, 이 보완으로 코드나 외부 시스템은 추가 변경되지 않았다.

## Latest initial-attempt part score implementation plan (2026-08-17)

- 별도 Jira 이슈 키 없이 이미 반영된 `partScores` 최초 응시 집계의 구현 계획을 정리했다.
- 외부 API·DTO·`BaseResponse`는 유지하고 `getExamSummary()`의 점수 합산 스트림에서 `retryCount == 0`만 통과시키는 최소 변경이다.
- 최초 응시·재시도·null 회차를 함께 사용하는 회귀 테스트와 전체 Gradle 테스트로 검증하며, legacy null 문서 존재 여부를 배포 전 확인 사항으로 유지한다.
- 이번 계획 정리 turn에서는 작업 기록 문서 외의 코드나 외부 시스템을 추가 변경하지 않았다.

## Latest initial-attempt part score implementation confirmation (2026-08-17)

- 별도 Jira 이슈 키 없이 사용자의 구현 요청에 따라 현재 작업 트리의 구현·회귀 테스트·전체 테스트 상태를 확정했다.
- `partScores`는 `retryCount == 0`인 최초 응시만 합산하고 재시도와 null 회차는 제외한다. 공개 API 구조와 기타 외부 계약은 유지된다.
- `./gradlew clean test`는 tests/failures/errors/skipped `352/0/0/0`, `git diff --check`는 성공했다. 기존 unchecked 경고 외에 추가 문제는 없다.
- 이미 요청한 애플리케이션·테스트 변경이 작업 트리에 존재했으므로 이번 확인에서는 코드를 추가 변경하지 않았다. legacy `retryCount=null` 최초 응시 문서는 집계에서 제외되는 상태다.

## Latest staging GitHub Actions test failure fix (2026-08-17)

- 별도 Jira 이슈 키 없이 GitHub Actions run `32034974696`의 job·step·실패 로그를 읽기 전용으로 확인했다. `checkout@v4`·`setup-java@v4` 경고는 실패 원인이 아니었고, job 전역 `SENTRY_RELEASE` 오버라이드로 `TosunsaengApplicationTests` 1건이 실패한 것이 직접 원인이었다.
- `Run tests` step의 `SENTRY_RELEASE`를 `app-back-end-learning-core@test`로 격리했고, checkout·setup-java를 Node.js 24 기반 v5 action으로 올렸다. 배포 후속 step의 commit SHA release와 AWS·ECR·ECS·health check 흐름은 유지된다.
- CI 동일 테스트 release 환경의 `./gradlew clean test --no-daemon`은 tests/failures/errors/skipped `352/0/0/0`으로 성공했다. YAML parse와 `git diff --check`도 성공했고 `actionlint`는 미설치로 실행하지 못했다.
- 사용자가 commit·push한 뒤 실제 GitHub Actions에서 v5 action 초기화부터 ECS health check까지 전체 배포를 재검증해야 한다. Codex는 workflow run을 재실행하거나 Git commit·push·배포 변경을 수행하지 않았다.

## Latest deployed-main Callback persistence diagnosis (2026-08-20)

- 로컬 `main`과 `develop`은 동일한 `98730c9`이며, 별도 신규 Jira 키 없이 기존 `TMI-25` Summary generation fencing과 운영 Callback 미저장 현상을 정적으로 대조했다.
- 가장 유력한 원인은 Python AI Summary Callback의 `generation_attempt` 누락 또는 현재 `summary_grading_jobs.generationAttempt`와의 불일치다. 2026-08-17 `a2c4fb6` 이후 이 Callback은 stale no-op 처리되어 `exam_summaries` insert 없이 HTTP 성공 응답을 반환한다.
- 운영 확인 순서는 Callback의 `user_id`, `generation_attempt`, `part_feedback` 존재 여부, 같은 examId의 `summary_grading_jobs` generation/status, ExamSession abandoned 여부, `exam_summaries`와 legacy `exam_results` 중복 결과 존재 여부다. 실제 payload 본문이나 민감정보는 로그에 남기지 않는다.
- `missing_generation`, `generation_mismatch`, abandoned, duplicate와 completion-claim-lost 경로는 DEBUG라 기본 INFO 운영 로그에 보이지 않을 수 있다. 성공 저장이면 `요약 채점 콜백 저장 완료 event=grading.callback outcome=stored callbackType=summary` INFO가 있어야 한다.
- 코드·외부 API·AI `user_id=examId`, Callback의 나머지 JSON, Redis/S3와 소유권 계약은 변경하지 않았다. 실제 운영 DB·로그·외부 시스템은 조회하지 않았고 Gradle 테스트도 실행하지 않았다.

## Latest Callback log versus Mongo result-count diagnosis (2026-08-20)

- 제공된 운영 로그에는 `ex_da814c87a9_0820_1425`와 `ex_3871c98953_0820_1412` 두 시험이 섞여 있다. 별도 신규 Jira 키는 없으며 기존 채점 멱등 관련 키는 `TMI-25`다.
- 최신 `ex_da...1425`에는 문항 1~5 저장 로그만 있고, 문항 9~11 및 Summary 예약 로그는 이전 `ex_387...1412`에 속한다. 최신 examId Mongo 조회에서 1~5만 보이는 현상은 이 로그와 모순되지 않는다.
- 이전 시험의 Summary 예약은 `completedQuestionCount=11 expectedQuestionCount=11`이며, 이는 해당 examId의 `exam_results`에서 최초 응시 결과 11개를 확인한 뒤에만 기록된다. 저장 INFO도 Mongo insert 정상 반환 뒤에만 출력된다.
- 운영 확인 대상은 두 examId별 `exam_results`, 결정적 feedback `_id`, 연결된 `exam_sessions`, 그리고 배포 앱의 `MONGODB_DATABASE`와 Atlas에서 조회 중인 database/cluster 일치 여부다. 실제 운영 DB와 payload는 이번 분석에서 조회하지 않았다.
- 애플리케이션·설정·테스트 코드는 변경하지 않았고 Gradle 테스트를 실행하지 않았다. 공개 API·AI Callback·Redis/S3 계약과 외부 시스템·Jira·Git 상태도 변경하지 않았다.

## Latest Summary-only persistence confirmation (2026-08-20)

- 문항 결과 부족처럼 보인 현상은 서로 다른 examId 혼동으로 해소됐고, 남은 Summary 미저장은 기존 `TMI-25` generation fencing과 가장 잘 일치한다. 별도 신규 Jira 키는 없다.
- 제공 로그의 Summary 요청은 generation 1이지만 저장 완료 로그가 없다. Python Callback에 `generation_attempt=1`이 없으면 main은 `missing_generation` stale no-op으로 HTTP 성공만 반환한다.
- 최종 확정에는 Callback 필드와 `summary_grading_jobs` status/generation/failureReason, `exam_summaries` 존재 여부를 함께 확인해야 한다. 실제 운영 payload·DB는 이번 turn에서 조회하지 않았다.
- 코드·설정·테스트와 공개 API·AI/Callback·Redis/S3 계약은 변경하지 않았고 Gradle 테스트도 실행하지 않았다.

## Latest web-ai deployment and generation propagation diagnosis (2026-08-20)

- `Too-Much-I/web-ai` 최신 main `ef060d2`의 GitHub Actions 배포 run `32333161432`는 성공했으므로 단순 서버 업데이트 누락으로 보이지 않는다. 별도 신규 Jira 키는 없으며 Learning Core 관련 기존 키는 `TMI-25`다.
- web-ai 최신 코드가 Summary 요청의 `generation_attempt`를 JSON parsing, Redis payload, sync/worker 처리, Summary response와 backend Callback까지 보존하지 않는다. 최신 이미지를 재배포하는 것만으로는 해결되지 않는다.
- AI 저장소에서 `generation_attempt` end-to-end echo와 sync/Redis callback 계약 테스트를 구현한 뒤 main에 병합하면 기존 workflow가 테스트, Docker image push와 EC2 `ai-server`·`ai-worker` 교체를 자동 수행한다.
- 외부 저장소·Actions는 읽기 전용으로만 확인했고 commit·push·PR·배포 재실행과 Learning Core 애플리케이션 변경은 수행하지 않았다.

### Completion record sync (2026-08-20)

- 종료 훅에 맞춰 `web-ai` 배포 안내 turn의 WORKLOG marker를 보완했다. 최신 main은 이미 배포됐고, 해결에는 AI 코드의 `generation_attempt` end-to-end echo 구현과 테스트 후 main 병합이 필요하다는 상태는 동일하다.
- 별도 신규 Jira 키는 없고 관련 기존 범위는 `TMI-25`다. 외부 저장소·Actions·EC2와 Learning Core 코드는 추가 변경하지 않았으며 Secret·Token도 다루지 않았다.

## Latest legacy Summary timing confirmation (2026-08-20)

- `ex_d9b6268627_0817_1308`의 suffix는 UTC 기준 2026-08-17 13:08, 한국 시간 22:08의 시험 생성 시각이다. 별도 신규 Jira 키는 없고 관련 기존 범위는 `TMI-25`다.
- generation fencing이 포함된 첫 workflow는 13:24 UTC에 실패했고, 실제 성공 배포 `98730c9`는 13:51 UTC, 한국 시간 22:51에 완료됐다. 해당 시험은 성공 배포 약 43분 전에 생성되어 구버전 Callback 저장 로직을 사용할 수 있었다.
- Summary 문서에는 저장 시각이 없으므로 정확한 Callback 저장 시각은 `exam_sessions.completedAt` 또는 CloudWatch 저장 완료 로그로 확인해야 한다. 코드·외부 시스템·Git/Jira 상태는 변경하지 않았다.

### Legacy Summary timing completion record sync (2026-08-20)

- 종료 훅에 맞춰 legacy Summary 시각 대조 turn의 WORKLOG marker를 보완했다. `0817_1308` 시험 생성은 generation fencing 성공 배포보다 약 43분 빠르다는 결론은 동일하다.
- 정확한 Summary 저장 시각은 `exam_sessions.completedAt` 또는 CloudWatch 로그 확인이 필요하며, 별도 신규 Jira 키는 없고 관련 기존 범위는 `TMI-25`다. 외부 시스템과 코드는 추가 변경하지 않았다.

## Latest web-ai AWS deployment confirmation (2026-08-21)

- 별도 신규 Jira 키 없이 기존 `TMI-25` Summary generation 연동과 관련된 `web-ai` 배포 상태를 확인했다.
- 최신 main `883c45c`의 `Echo generation attempt in feedback callbacks` GitHub Actions run `32435961886`이 한국 시간 2026-08-21 10:22:59에 성공했다.
- offline test·Compose 검증·Docker image push·EC2 SSH deploy가 모두 성공했으며 `ai-server`와 `ai-worker` 교체까지 완료됐다. 추가 수동 AWS 업로드는 필요 없다.
- 새 시험으로 Summary 저장과 Job COMPLETED를 확인하고, 기존 누락 시험은 필요하면 기존 grading retry API로 복구한다. 외부 저장소·Actions는 읽기 전용으로만 확인했고 코드·배포·Git/Jira 상태는 변경하지 않았다.

## Billing 구현 시작 분석 (2026-08-25)

- 별도 Billing 저장소는 Spring Boot·MongoDB·Security health-only 골격만 있고 도메인 entity, API, transaction과 reconciliation은 아직 없다.
- Identity의 phone eligibility schema v1 producer와 publisher는 구현됐지만 Billing의 event inbox·revision high-water·current binding consumer와 staging E2E가 없다.
- Learning Core 시험 생성은 현재 Billing reserve 없이 기존 진행 Session을 abandon하고 새 `ExamSession`을 즉시 insert한다. Billing 연동 시 기존 공개 시험 생성 body/성공 DTO를 유지하면서 내부 경계를 `reserve → Session durable commit → confirm`과 동일-operation 복구로 변경해야 한다.
- Billing의 우선 구현 범위는 phone binding consumer, 무료 1회 TrialClaim·entitlement ledger, 멱등 Reservation API, 5분 만료·reconciliation과 workload 인증이다. Apple/Google 결제, paid credit, pass, coupon과 환불은 후속이다.
- 구현 전 Billing 사용자 JWT audience, Learning Core workload credential, internal endpoint/DTO, idempotency와 오류 mapping, confirm 불명 복구, AttemptGroup 완료 증거와 TrialClaim 보존 기간을 확정해야 한다.
- 별도 신규 Jira 키는 없다. Learning Core 코드는 이번 분석에서 변경하지 않았으며 Billing API 계약이 동결되기 전 임의 연동을 시작하지 않는다.

## Billing AGENTS.md 최신화 동기화 (2026-08-27)

- 별도 Billing 저장소의 `AGENTS.md`에 현재 무료 최소 Entitlement 범위, TrialClaim 3년 보존, eligibility event API·멱등성·Mongo Transaction, VPC Lattice AWS_IAM·ECS task role·SigV4와 코드 리뷰 우선순위를 반영했다.
- Billing은 명시적 요청 없이 Identity와 Learning Core를 읽기 전용 계약 확인 대상으로만 사용하며 다른 저장소 코드를 함께 변경하지 않는다.
- Learning Core 애플리케이션과 공개 API·DTO·BaseResponse·AI/Callback·S3·Redis 계약은 이번 작업에서 변경하지 않았다.
- 별도 신규 Jira 키는 없다. Billing Reservation API가 확정되기 전에는 Learning Core outbound client와 시험 생성 saga를 임의 구현하지 않는다.

## Billing AGENTS.md 전용 계약 정교화 동기화 (2026-08-27)

- Billing `AGENTS.md`는 무료 MVP 전체와 현재 PLAN-001 event consumer 범위를 분리하고, internal DTO·204/error mapping, 인증된 service userId body 예외와 explicit Mongo index·보존 규칙을 반영했다.
- PLAN-001에는 eligibility inbox와 current projection만 포함하며 TrialClaim·grant·Reservation은 후속 vertical slice다.
- Learning Core 애플리케이션과 외부 API 계약은 변경하지 않았다. 별도 신규 Jira 키는 없다.
- Billing Reservation API가 동결되기 전 Learning Core outbound 연동을 임의 구현하지 않는다.

## Billing 서비스 간 통합 계약서 동기화 (2026-08-27)

- Billing 저장소에 `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`가 추가됐다.
- 문서는 Identity eligibility event와 Learning Core reserve·confirm·cancel·status·AttemptGroup event의 인증, 멱등성, 재시도·장애 수렴과 배포 체크리스트를 한 흐름으로 설명한다.
- 세부 wire·Mongo는 Billing ADR-001, Lattice·SigV4는 ADR-002가 최종 기준이다.
- Learning Core 애플리케이션과 외부 계약은 변경하지 않았고 별도 신규 Jira 키는 없다.

## Billing 통합 계약 외부 검토 확인 (2026-08-27)

- 첨부 검토 4건을 Billing 계약과 Identity·Learning Core 실제 코드에 대조했다.
- Billing ADR-001 inbox field/index·disposition 불일치, Identity Bearer publisher와 Billing SigV4 목표 차이, Billing 필수 Idempotency-Key와 Learning Core controller 미구현은 사실이다.
- Identity가 `Retry-After`를 읽지 않는 지적은 맞지만 Billing eligibility endpoint의 409는 구체 계약상 EVENT_ID_CONFLICT 전용이므로 COMMAND_PROCESSING과 error body code를 구분할 필요는 없다.
- Learning Core 과거 계획 문서는 시험 생성 header를 optional로 기록하지만 Billing 최신 승인 계약은 필수 UUID v4다. Reservation saga 구현 전에 앱과 Learning Core 공개 header를 함께 전환해야 한다.
- Jira 키는 없다. 계약·애플리케이션 코드는 수정하지 않았다.

## Billing 필수 Idempotency-Key 계약 문서 정렬 (2026-08-27)

- Billing 최신 승인에 맞춰 Learning Core Billing 계약 검토 문서의 optional header를 필수 lowercase UUID v4 `Idempotency-Key` 목표로 보정했다.
- `POST /api/v1/exams`의 Request Body 없음과 기존 성공 Response DTO는 유지하며 header 없는 요청은 목표 구현에서 `INVALID_IDEMPOTENCY_KEY`로 거절한다.
- 같은 key는 응답 유실 transport retry에만 재사용하고 앱 종료 후 의도적 restart는 새 key·새 examId와 `ABANDONED_RESTARTED`를 사용한다.
- 실제 `ExamRestController`와 `ExamSession`·Billing client는 아직 이 계약을 구현하지 않았다. 앱 선배포·구버전 호환 gate와 Reservation API 준비 후 별도 구현한다.
- 관련 Identity transport 기존 Jira는 `TMI-95`이며 Jira는 변경하지 않았다. Learning Core 애플리케이션·외부 runtime 계약은 이번 문서 업데이트에서 변경하지 않았다.

## 10초 챌린지 자동 Day 1·difficulty 계약 확정 (2026-08-28)

- 관련 기존 Jira는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이며 Jira 자체는 조회하거나 변경하지 않았다. Learning Core Challenge backend 구현 Jira는 아직 없다.
- 프론트 상세 계약과 전체 API 인계서를 Draft v0.9로 갱신했다. Challenge API는 아직 구현·배포되지 않은 계획 상태다.
- `app.challenge.enabled=true`인 배포가 처음 성공 기동한 KST 날짜를 Mongo `challenge_10s_catalog_state`의 `_id="active:v1"` singleton에 원자 `setOnInsert`로 한 번만 저장하고 그날을 dayNumber 1로 사용한다. disabled 배포는 날짜를 만들지 않으며, 재시작·재배포·ECS scale-out은 기준일을 초기화하지 않는다.
- 이후 `dayNumber = daysBetween(contentBaseDate, challengeDate) + 1`로 계산하며 순환, modulo, random 또는 이전 날짜 fallback을 하지 않는다. 해당 dayNumber 콘텐츠가 없으면 `404 CHALLENGE_CONTENT_NOT_FOUND`로 fail-closed하고 운영 알림 대상으로 삼는다.
- Mongo의 `difficulty`는 정수인지 확인하되 scale이나 범위를 해석하지 않는다. 문제 조회, 제출 terminal 응답과 상세 결과 DTO에는 저장된 정수를 그대로 반환하고, attempt·upload-url 응답과 AI 요청·grading job payload에는 넣지 않는다. 과거 결과 안정성을 위해 attempt snapshot에는 저장한다.
- 변경 범위는 `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/contracts/FRONTEND_API_HANDOFF.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`와 Codex 기록 문서다. 애플리케이션·테스트 코드, Mongo 데이터, Jira와 배포는 변경하지 않았다.
- 기존 시험 API·DTO·`BaseResponse`, S3·Redis, Python AI/Callback의 `user_id=examId` 계약은 유지했다. 문서 작업이므로 Gradle 테스트는 실행하지 않았다.
- 구현 전에 Challenge backend Jira를 만들고 metadata initializer·비순환 resolver·catalog validation·attempt snapshot·공개 DTO·AI payload exclusion 테스트를 구현해야 한다. sample rate·channel·최대 파일 크기와 AI 결과 세부 계약은 여전히 확정이 필요하다.

## 10초 챌린지 Learning Core–AI 계약 미확정 항목 검토 (2026-08-28)

- 관련 기존 Jira는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이며 Jira를 조회하거나 변경하지 않았다. Learning Core Challenge backend와 AI 연동 전용 Jira는 아직 없다.
- 현재 문서는 시험 Callback을 재사용하지 않는 challenge 전용 versioned 비동기 계약, 실제 userId·difficulty 제외, attemptId·문제 식별값·한국어 prompt·audio 전달, 결정적 Job과 callback 멱등성까지만 방향이 정해져 있다. 실제 endpoint, 인증과 request/callback JSON 또는 multipart schema는 아직 동결되지 않았다.
- 구현 전 필수 확정 대상은 contract version, AI 요청 endpoint·전송 방식, audio 규격과 최대 크기, request 필수/nullable field, callback endpoint·인증, 결과 field와 enum/null 의미, no-speech·unsupported audio 처리, `attemptId + gradingAttempt` stale callback fencing, idempotency key, timeout·retry·최종 실패 전환, HTTP 오류 분류와 payload 제한이다.
- 권장 MVP는 숫자 점수 없이 transcript, `correct|needs_improvement` verdict, correctedAnswer, 짧은 meaning·grammar·pronunciation feedback만 제공한다. 내부 `no_speech`는 정상 terminal 결과로 저장하되 공개 `feedbackType`은 추가하지 않고, 시스템 실패와 구분한다.
- 기존 Summary의 `generation_attempt` 누락 문제를 반복하지 않도록 outbound와 callback 모두 `attemptId`, 결정적 `jobId`, 양의 정수 `gradingAttempt`를 필수로 두고 현재 attempt generation과 불일치하는 callback은 성공 no-op으로 처리하는 방향을 권장한다.
- 분석·기록만 수행했으며 애플리케이션·테스트 코드, 기존 프론트 Draft v0.9, Mongo 데이터, Jira와 배포는 변경하지 않았다. 기존 시험 AI/Callback `user_id=examId` 계약에도 영향을 주지 않는다.

## 10초 챌린지 promptKo 의미 확인 (2026-08-28)

- `promptKo`는 사용자가 보고 영어로 말해야 하는 한국어 문제 문장이며 Mongo `challenge_10s_questions.questions[].korean`을 공개 API 필드로 매핑한 값이다.
- `referenceAnswer`는 같은 문제의 참고 영어 답안으로 `promptKo`와 구분하며, 문제 조회 단계에는 숨기고 제출 또는 만료 terminal 이후에만 프론트에 공개한다.
- AI 계약에서 snake_case를 사용한다면 동일 값을 `prompt_ko`로 전달하는 제안이며, 실제 wire field명은 AI 계약 v1 동결 시 최종 확정한다.
- 관련 기존 Jira는 `TMI-102`, `TMI-105`, `TMI-106`이며 Jira와 애플리케이션·계약 문서는 변경하지 않았다.

## 10초 챌린지 Learning Core–AI 계약서 Draft v0.1 작성 (2026-08-28)

- 관련 기존 Jira는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이며 Jira를 조회하거나 변경하지 않았다. Learning Core Challenge backend·AI 연동 전용 Jira는 아직 없다.
- 신규 `docs/contracts/ten-second-challenge-ai-api.md` Draft v0.1을 작성했다. 기존 시험 `/evaluations`·Feedback Callback과 분리한 `POST /v1/challenges/evaluations`, `POST /internal/v1/challenges/grading/callback` 권장 계약이다.
- Learning Core가 S3 audio를 내려받아 multipart로 AI에 전달한다. 필수 field는 `attempt_id`, 결정적 `job_id`, `grading_attempt`, `question_id`, `question_number`, Mongo `korean`과 같은 `prompt_ko`, `reference_answer`, `audio_file`이며 실제 userId·difficulty·날짜·S3 위치는 제외한다.
- audio 허용 profile은 M4A/AAC-LC, `audio/mp4`, 16/44.1/48 kHz, mono/stereo, 최대 2 MiB이며 AI가 내부에서 16 kHz mono PCM으로 정규화하는 권장안이다.
- 결과는 숫자 점수 없이 `completed|no_speech|failed`, transcript, `correct|needs_improvement`, corrected answer와 짧은 meaning·grammar·pronunciation feedback을 사용한다. no-speech는 시스템 실패가 아닌 completed terminal로 projection한다.
- 방향별로 분리된 service Bearer credential, private service discovery·TLS, request `Idempotency-Key`, callback UUID와 `attemptId/jobId/gradingAttempt` fencing, 204 duplicate·stale no-op, 오류별 retry, 120초 deadline·최대 3 generation 권장값을 문서화했다.
- 결정서와 개정 출시 계획에 AI 계약 문서 링크·Draft 상태·담당 팀 승인 및 contract test gate를 반영했다. 기존 프론트 Draft v0.9와 애플리케이션·테스트 코드는 변경하지 않았다.
- 네 개의 JSON 예시는 Ruby JSON parser로 검증했고 `git diff --check`가 통과했다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.
- AI 팀과 실제 모바일 audio fixture로 profile을 검증하고, service credential/TLS 운영 경로, 120초 deadline·최대 3 generation, 프론트 `aiResult` projection을 승인한 뒤 v1로 동결해야 한다.

### AI 계약서 작업 종료 기록 동기화 (2026-08-28)

- 종료 훅의 현재 turn 기록 요구에 맞춰 WORKLOG 완료 항목을 추가했다. 신규 AI 계약서 Draft v0.1, 결정서·출시 계획 동기화, JSON 예시 및 `git diff --check` 검증 결과는 위 최신 상태와 동일하다.
- 관련 기존 Jira는 `TMI-102`, `TMI-105`, `TMI-106`이며 Jira, 애플리케이션 코드, Mongo 데이터와 배포는 추가로 변경하지 않았다.

## 10초 챌린지 AI 계약 v1 승인 반영·잔여 결정 검토 (2026-08-28)

- AI 팀이 `docs/contracts/ten-second-challenge-ai-api.md` 내용대로 구현하기로 합의해 문서 상태를 Draft v0.1에서 승인된 v1·미구현으로 변경했다. 관련 기존 Jira는 `TMI-102`, `TMI-105`, `TMI-106`이며 Jira 자체는 변경하지 않았다.
- 승인 범위는 challenge 전용 request/Callback endpoint, multipart audio, M4A/AAC profile·2 MiB, 방향별 service credential, `attemptId/jobId/gradingAttempt` fencing, 결과 schema, 120초 Callback deadline, 최대 3 generation과 retry/error 규칙이다.
- AI 계약서의 담당 팀 endpoint/field 승인 checklist를 완료 처리하고, 결정서·출시 계획의 Draft 표현을 v1 승인과 구현·contract test 잔여 상태로 동기화했다. 프론트 계약에는 서버 timeout·generation이 v1로 확정됐음을 반영했다.
- 추가 확정 대상은 프론트 `aiResult`와 no-speech null 표현, MEMBER/Guest 범위, 날짜 rollover 최종 승인, foreground polling 상한, audio 재생 제공 여부, AI text field와 Callback 전체 payload 상한이다.
- 권장 잔여안은 no-speech를 `gradingStatus=completed`와 null 하위 field를 가진 `aiResult` 객체로 표현, MEMBER 전용, 기존 rollover안 승인, foreground polling 60초, MVP audio 재생 제외, transcript/corrected answer 각 1000자·feedback 각 500자·Callback JSON 16 KiB 상한이다. 이 값들은 아직 사용자 승인 전이다.
- 구현·운영 준비로 실제 모바일 audio fixture, 양방향 credential 주입·rotation, private routing·TLS/security group, contract test·retry/DLQ와 staging latency 검증이 남아 있다. 이는 wire schema 재결정과 구분한다.
- 애플리케이션·테스트 코드, Mongo 데이터, 배포와 기존 시험 AI/Callback 계약은 변경하지 않았다. 문서 변경만 수행해 Gradle 테스트는 실행하지 않았고 `git diff --check`가 통과했다.

## 10초 챌린지 프론트 v1 잔여 계약 확정 (2026-08-28)

- 관련 기존 Jira는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이며 Jira를 조회하거나 변경하지 않았다. Learning Core Challenge backend·AI 연동 전용 Jira는 아직 없다.
- 프론트 계약을 v1 승인·미구현 상태로 올리고 `aiResult.referenceAnswer`를 추가했다. 이 값은 AI 생성값이나 Callback echo가 아니라 Mongo `challenge_10s_questions.questions[].referenceAnswer`를 attempt 생성 시 snapshot한 Learning Core 값이다.
- 정상 AI 완료와 no-speech 모두 non-null `aiResult`와 non-blank `aiResult.referenceAnswer`를 반환한다. no-speech는 `gradingStatus=completed`이고 transcript·verdict·correctedAnswer·feedback만 null이다. processing·최종 failed에서는 기존처럼 `aiResult=null`이고 top-level `question.referenceAnswer`는 유지한다.
- `question.referenceAnswer`는 제출 직후부터 이용하는 기존 field이고 `aiResult.referenceAnswer`는 완료 결과 component용 동일 snapshot이다. 두 값이 다르면 attempt snapshot을 authoritative 값으로 처리한다.
- MEMBER 전용·Guest `403`, 기존 KST rollover 보호, foreground polling 60초(처음 20초 2초 간격·이후 5초 간격), MVP 사용자 audio 재생과 `audioUrl` 제외를 확정했다.
- AI text 상한은 transcript·corrected answer 각 1000자, feedback 각 500자, Callback JSON 전체 UTF-8 16 KiB로 확정했다. 초과 Callback은 `413 CALLBACK_PAYLOAD_TOO_LARGE`다.
- `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/contracts/FRONTEND_API_HANDOFF.md`, AI v1 계약, 결정서와 출시 계획을 동기화했다. 애플리케이션·테스트 코드와 Mongo 데이터는 변경하지 않았다.
- 프론트·AI 계약의 모든 JSON code block parser 검증과 `git diff --check`가 통과했다. 문서 작업이라 Gradle 테스트는 실행하지 않았다.
- 실제 구현에서는 attempt snapshot 조립, no-speech projection, MEMBER authorization, 60초 polling 타입·UX, audioUrl 비노출, text/payload validation을 contract test로 검증해야 한다.

## 10초 챌린지 구현 착수 가능성 점검 (2026-08-28)

- 프론트·AI v1 계약, 콘텐츠 저장 구조, attempt/upload-url 분리, 날짜·상태·멱등성·retry 계약은 구현 가능한 수준으로 동결됐다. 관련 기존 Jira는 `TMI-102`, `TMI-105`, `TMI-106`이며 Jira를 조회하거나 변경하지 않았다.
- 현재 직접적인 착수 blocker는 repository `AGENTS.md`의 “현재 추가하지 않을 기능” 목록에 10초 챌린지가 포함돼 있고 `TMI-14`, `TMI-25` 외 별도 예외가 없다는 점이다. Learning Core Challenge backend 전용 Jira도 아직 없다.
- 구현 전 필수 순서는 Learning Core backend Jira 생성, 해당 키에 한정한 AGENTS 명시적 예외 추가, 현재 계약 문서 변경의 사용자 commit·push, 전용 branch 생성이다. Codex는 commit·push를 수행하지 않는다.
- 실제 secret 값 없이 방향별 service credential 환경변수·Secrets Manager 주입, private routing·TLS/security group과 feature flag 기본 off를 구현 범위에 포함해야 한다. 실제 credential 생성·운영 주입은 배포 준비 단계다.
- 구현은 catalog/state 기반 Day resolver, ChallengeAttempt·snapshot·upload/submit, MEMBER authorization, GradingJob·AI dispatch/callback fencing, 결과/history API, migration/index·contract/integration test 순서의 vertical slice로 진행할 수 있다.
- 구현 전 분석만 수행했으며 애플리케이션·테스트 코드, AGENTS.md, Jira, Git branch·commit·push와 배포는 변경하지 않았다. 출시 계획의 상태 문구만 계약 승인 상태에 맞춰 갱신했다.

## AGENTS.md 10초 챌린지 구현 범위 승인 반영 (2026-08-28)

- 사용자가 10초 챌린지를 Learning Core에 추가할 기능으로 명시 승인해 `AGENTS.md`의 “현재 추가하지 않을 기능” 목록에서 10초 챌린지를 제거했다.
- Jira 신규 키는 없으며 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이다. Jira를 조회하거나 변경하지 않았다.
- `AGENTS.md`에 프론트·AI v1 계약을 authoritative source로 지정하고 Challenge domain 격리, MEMBER·소유권, 콘텐츠/date resolver, attempt/S3, AI/Callback, secret·로그와 테스트 규칙을 추가했다.
- 기존 ExamSession·ExamResult·시험 Job/retryCount·시험 API/AI 계약을 Challenge가 재사용하거나 변경하지 않도록 경계를 고정했다.
- DB referenceAnswer attempt snapshot, 정상/no-speech `aiResult.referenceAnswer`, difficulty AI 제외, 비순환 Day 1, 1시간 attempt, M4A/AAC, AI generation fencing·payload 제한과 audioUrl 비노출을 구현 규칙으로 반영했다.
- 코드 리뷰 우선순위에 Challenge의 MEMBER/소유권, referenceAnswer 조기 노출·snapshot, no-speech, AI payload, stale Callback, baseDate와 audio 개인정보 검사를 추가했다.
- 이전 구현 착수 blocker였던 AGENTS 범위 제한은 해소됐다. Learning Core backend Jira 생성은 추적을 위해 권장하지만 저장소 규칙상 구현 허용의 선행 blocker는 아니다.
- 애플리케이션·테스트 코드와 배포는 변경하지 않았다. 문서 변경이라 Gradle 테스트는 실행하지 않았고 `git diff --check`로 형식을 검증한다.

### AGENTS 범위 승인 종료 기록 동기화 (2026-08-28)

- 종료 훅의 현재 turn 기록 요구에 맞춰 WORKLOG 완료 항목을 추가했다. 10초 챌린지 제외 해제, 전용 구현·테스트·리뷰 규칙 추가와 저장소 범위 blocker 해소 상태는 위 최신 기록과 동일하다.
- 관련 기존 Jira는 `TMI-102`, `TMI-105`, `TMI-106`이며 신규 Jira, 애플리케이션 코드, secret, 배포는 추가로 변경하지 않았다.

## Production 실제 비용 관측 재정정 (2026-08-28)

- 현재 보고된 비용은 `$12.6/day`가 아니라 `$1.26`이다.
- 서울 Fargate 기준 Task 각 1개 compute는 약 `$5.696/day`이므로 `$1.26`은 세 Task가 24시간 실행된 하루 전체 비용일 수 없다.
- 다음 확인 항목은 ECS 서비스별 desired/running/deployment Task 수와 Cost Explorer의 Fargate vCPU·GB hours, NAT Gateway, ALB/LCU, public IPv4, CloudWatch usage type이다.
- `$1.26`의 기간·필터·cost type·credit 여부를 확인하기 전에는 단순 월 환산 `$37.80`, 약 52,920원을 고정비로 사용하지 않는다. 애플리케이션과 AWS 리소스 및 외부 계약은 변경하지 않았다.

## AGENTS 10초 챌린지 범위 승인 최종 상태 (2026-08-28)

- 10초 챌린지는 더 이상 `AGENTS.md` 제외 기능이 아니며 승인된 프론트·AI v1 계약에 따라 Learning Core에서 구현할 수 있다.
- 관련 기존 Jira는 `TMI-102`, `TMI-105`, `TMI-106`이며 신규 Jira와 애플리케이션 코드는 아직 없다. Secret·Token과 배포 상태는 변경하지 않았다.

## Challenge 제외 Learning Core·Billing 잔여 구현 재확인 (2026-08-28)

- 사용자가 제시한 `UserMerged` Learning Core consumer, Billing Reservation client·시험 생성 saga, 공개 시험 생성 `Idempotency-Key`·same-operation replay, AttemptGroup 상태 outbox/publisher·R3 replacement 연결, Billing 장애 reconciliation은 Challenge를 제외한 Learning Core 기존 시험의 실제 잔여 구현 항목이 맞다.
- Learning Core `POST /api/v1/exams`는 현재 header 없이 `createExamSession()`을 호출해 Session을 즉시 생성하며 Billing client, reservationId·operationId metadata, UserMerged와 AttemptGroup/reconciliation 코드가 없다. 채점 dispatch에 사용되는 내부 `Idempotency-Key`는 공개 시험 생성 operation key와 별개다.
- Billing에는 `TMI-112`, `TMI-113` 범위의 TrialClaim·무료 grant/ledger와 reserve/confirm/cancel/status·expiry, AttemptGroup/AttemptSession 기반이 구현돼 있다. 따라서 “Billing Reservation 전체 미구현”이 아니라 Learning Core 호출·saga와 양방향 수렴이 미구현이다.
- AttemptGroup 종단 연결은 양쪽 작업이다. Learning Core의 상태 event outbox/publisher와 Billing의 event consumer가 모두 없으며, Billing의 REPLACEMENT 판정 기반만 존재해 현재는 GRADING/COMPLETED/RETAKE_AVAILABLE 실제 수렴이 불가능하다.
- 전체 1차 출시 기준에는 위 목록 외에 Billing owner rebind, Identity `TMI-114` 포함 여부와 구현, 실제 모바일 SNS·phone link, workload/Lattice/IAM/SG·replica-set/multi-instance·response-loss·rollback·canary E2E가 남아 있다.
- 관련 완료 Jira는 Learning Core `TMI-109`, Identity `TMI-111`, Billing `TMI-110`·`TMI-112`·`TMI-113`이며 후속 Identity 계획은 `TMI-114`다. Jira는 조회하거나 변경하지 않았다.
- 코드와 문서를 읽기 전용으로 대조했으며 애플리케이션·테스트 코드와 외부 시스템은 변경하지 않았다. 분석 작업이라 Gradle 테스트는 실행하지 않았다.

## 다음 작업 확정: Billing AttemptGroup 상태 event consumer (2026-08-28)

- 다음 vertical slice는 Billing `POST /internal/v1/attempt-group-events` consumer다. `AttemptGroup`·`AttemptSession`과 reserve/confirm 기반은 이미 있지만 Learning Core 상태 event를 받는 입구가 없어 group 상태가 실제 채점 진행·완료·최종 실패로 수렴하지 않는다.
- schema v1 strict validation, 16 KiB 상한, canonical digest inbox, active Session fencing, version CAS와 Mongo Transaction을 한 단위로 구현한다. 같은 eventId·같은 digest는 `204` no-op, 다른 digest는 `409`, stale Session은 inbox에 `STALE`로 기록하고 `204`로 종료한다.
- 권장 전이는 유효한 현재 Session의 terminal evidence가 도착하면 `OPEN`에서도 `COMPLETED` 또는 `RETAKE_AVAILABLE`로 전진 수렴시키고, `COMPLETED`는 terminal로 보호하는 방식이다. missing group/session은 retryable, owner mismatch는 계약 위반 격리, failureCode는 저 cardinality allowlist로 제한한다.
- 이 slice에는 Learning Core publisher/outbox, Billing owner rebind, 공개 시험 생성 Idempotency-Key·Reservation saga, reconciliation과 AWS/Lattice 배포를 포함하지 않는다. consumer가 완료된 뒤 owner rebind와 Learning Core outbound 쪽을 순서대로 연결한다.
- 이번 턴은 구현 전 계획 설명만 수행했으며 애플리케이션·외부 계약·Jira·배포를 변경하지 않았다.

## Billing PLAN-005 AttemptGroup 상태 event consumer 계획서 작성 (2026-08-28)

- Billing `docs/plans/PLAN-005-attempt-group-status-event-consumer.md`에 endpoint, schema v1 strict decode·16 KiB, digest/inbox, active Session fencing, 상태 전이, Transaction·CAS, security, 오류와 테스트·production gate를 구현 단계별로 작성했다.
- terminal evidence는 `GRADING` 누락 시 `OPEN`에서도 직접 전진한다. terminal 뒤 역행과 이전 Session event는 `STALE` 204, missing group/session은 `503 ATTEMPT_PROJECTION_NOT_READY`, 구조적 owner/session 충돌은 `409 EVENT_TARGET_CONFLICT`로 고정했다.
- Billing 내부 owner 검증은 `AttemptGroup.subjectRefId`를 userId로 직접 비교하지 않고 active·unexpired `BillingSubjectLink`를 통해 수행하도록 계획했다. retention 뒤 link는 복원하지 않고 stale 처리한다.
- RETAKE failureCode 초안 allowlist는 `REQUIRED_RESULTS_UNAVAILABLE`, `SUMMARY_UNAVAILABLE`, `GRADING_DEADLINE_EXCEEDED`, `RESULT_INTEGRITY_VIOLATION`이다. provider 원문·자유 형식 사유는 금지한다.
- 기존 Billing PLAN-004가 있어 번호는 PLAN-005를 사용했다. PLAN-004/TMI-115는 기술적 선행 조건이 아니며 상태를 변경하지 않았다. PLAN-005는 사용자 승인 대기·Jira 미생성 상태다.
- Learning Core publisher/outbox, UserMerged, Reservation saga·reconciliation, 실제 Lattice/AWS는 후속이며 이번 턴에는 애플리케이션·계약·Jira를 변경하지 않았다.

## Billing PLAN-005 계획서 종료 상태 (2026-08-28)

- PLAN-005 상세 계획서는 작성 완료·사용자 승인 대기 상태다. Jira는 미생성이며 애플리케이션 구현은 시작하지 않았다.
- 최종 계획은 `BillingSubjectLink` 기반 owner 검증, terminal 전진·역행 차단, duplicate/stale 204, missing projection retryable 503, target conflict non-retryable 409와 failureCode allowlist를 포함한다.
- 문서 외 런타임 계약은 아직 변경하지 않았다. 승인 후 Jira 생성과 Phase 0 ADR·서비스 통합 계약 보정이 다음 단계다.

## 범위 정정: Learning Core Billing Reservation·시험 생성 saga 계획 (2026-08-28)

- 사용자가 수정 대상은 Billing이 아니라 Learning Core라고 정정했다. 이전 Billing PLAN-005는 철회·삭제했고 Billing 애플리케이션이나 Jira를 변경하지 않았다.
- 신규 `docs/codex/BILLING_RESERVATION_SAGA_IMPLEMENTATION_PLAN.md`가 현재 활성 초안이다. 공개 시험 생성의 필수 `Idempotency-Key`, `ExamCreationOperation`, Billing reserve→ExamSession durable commit→confirm, cancel/status 복구와 same-operation replay를 Learning Core 구현 범위로 둔다.
- `ExamSession`에 `creationOperationId`, `billingReservationId`, `billingReservationKind`, `attemptGroupId`, entitlement confirmation 상태를 내부 저장하되 기존 성공 DTO에는 노출하지 않는다.
- 현재 `attemptGroupId` mapping이 없으므로 AttemptGroup 상태 outbox/publisher보다 이 saga가 먼저다. saga 완료 후 같은 mapping으로 `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE` event를 발행한다.
- Billing flag는 기본 off로 계획했다. flag off에서는 기존 무헤더 흐름을 유지하고, 프론트 header 선배포·staging 검증 뒤 flag on에서 lowercase UUID v4를 필수화한다.
- 이번 턴은 계획 문서만 변경했으며 Java/config/migration·runtime API·Jira·AWS는 변경하지 않았다. 계획은 사용자 승인 대기·Jira 미생성 상태다.
- 재확인 결과 수정 대상은 계속 Learning Core이며 Billing은 reserve/confirm/cancel/status 계약의 호출 대상일 뿐이다. Billing consumer나 Billing 런타임 구현은 현재 활성 작업이 아니다.
- Billing Reservation 기반은 `TMI-112`·`TMI-113`으로 이미 존재하므로 현재 saga를 위해 Billing에 같은 기능을 다시 만들 필요는 없다. 다만 전체 AttemptGroup 수렴에는 후속 Billing 상태 event consumer가 필요하며 owner rebind와 Billing 측 reconciliation도 별도 Billing 작업으로 남아 있다. 현재 순서는 Learning Core saga와 durable group mapping이 먼저다.

## Billing 변경 필요성 최종 결론 (2026-08-28)

- 현재 Learning Core 시험 생성 saga를 연결하는 데 필요한 Billing Reservation endpoint 기반은 이미 구현돼 있으므로 Billing을 먼저 수정하지 않는다.
- 우선순위는 Learning Core saga와 `ExamSession.attemptGroupId` mapping이다. 이후 Learning Core 상태 outbox/publisher에 대응하는 Billing consumer, owner rebind와 Billing reconciliation을 Billing 저장소의 별도 후속 작업으로 구현한다.
- 현재 턴에는 런타임 코드·외부 계약·Jira·AWS를 변경하지 않았다.

## Learning Core 시험 생성 saga 계획 설명 상태 (2026-08-28)

- 활성 계획서는 기존 Billing Reservation 기반을 Learning Core의 `POST /api/v1/exams`에 연결해 한 번의 사용자 시작 동작이 하나의 operation·Session·사용권 소비로 수렴하게 하는 구현 계획이다.
- 정상 순서는 operation 준비→Billing reserve→confirming Session local commit→Billing confirm→`IN_PROGRESS` finalize이며, commit 실패는 cancel/expiry, confirm 응답 불명은 Billing status와 same-key replay로 복구한다.
- 외부 성공 DTO와 `BaseResponse`, 실제 userId 비노출, retryCount, S3·Redis와 AI `user_id=examId` 계약은 유지한다. feature flag는 기본 off이고 AttemptGroup publisher/consumer와 owner rebind·background reconciliation은 후속이다.
- 이번 턴에는 계획을 설명하고 기록만 갱신했으며 애플리케이션·외부 계약·Jira·AWS를 변경하지 않았다.

## TMI-116 Learning Core 시험 생성 saga Jira (2026-08-28)

- Jira `TMI-116` `[Learning Core] Billing Reservation 시험 생성 saga 구현`을 생성했다. 재조회 상태는 `해야 할 일`, 담당자는 미지정이다.
- 이슈에는 Learning Core `POST /api/v1/exams`의 필수 `Idempotency-Key`, Billing reserve→Session commit→confirm, same-operation replay, 실패 복구, internal mapping, SigV4/Lattice, feature flag 기본 off와 회귀·production gate를 포함했다.
- 선행 완료 이슈는 Billing `TMI-112`·`TMI-113`이다. Billing Reservation 재구현과 AttemptGroup publisher/consumer, owner rebind·background reconciliation은 `TMI-116`에서 제외한 후속 범위다.
- 계획서는 Jira 생성·구현 대기 상태로 갱신했으며 애플리케이션·외부 계약·AWS는 변경하지 않았다.

## TMI-116 Billing Reservation 시험 생성 saga 구현 완료 (2026-08-29)

- 브랜치 `feat/TMI-116-billing-reservation-exam-saga`에서 Learning Core의 `POST /api/v1/exams`에 feature flag 기반 Billing Reservation saga를 구현했다. flag는 기본 off이며 on일 때만 `Idempotency-Key` lowercase UUID v4를 필수 검증한다.
- `ExamCreationOperation`의 `PREPARED → RESERVED → SESSION_COMMITTED → SUCCEEDED` 및 cancel·expiry·terminal 상태를 영속화하고, 같은 사용자·operation replay가 고정 `sessionId`와 `mockExamId`로 수렴하도록 했다. operation TTL 뒤에는 `(userId, creationOperationId)` Session mapping으로 장기 replay한다.
- Billing reserve·confirm·cancel·status SigV4 client를 추가했다. service는 `vpc-lattice-svcs`, region은 `ap-northeast-2`, redirect는 금지하며 strict JSON·16 KiB response 상한과 `Retry-After` mapping을 적용했다. confirm의 `sessionCommittedAt`은 Billing strict decoder에 맞춰 UTC 밀리초 3자리로 직렬화한다.
- 기존 Session abandon과 새 `ENTITLEMENT_CONFIRMING` Session insert를 Mongo Transaction으로 묶고 confirm 뒤에만 `IN_PROGRESS`로 전환한다. commit 실패 cancel, cancel 불명 `CANCEL_PENDING`, confirm 불명 status 조회, duplicate/stale local 전이를 복구하며 same-key 동시 commit은 공유 reservation을 취소하지 않는다.
- `ExamSession`에 operation·reservation·reservation kind·attemptGroup·entitlement metadata를 내부 저장하지만 기존 성공 DTO·`BaseResponse`, 실제 userId 비노출, 시험 retryCount·S3·Redis·AI request/Callback과 `user_id=examId` 계약은 유지했다.
- Mongo index dry-run/apply script, staging/prod index validator와 Transaction capability probe, 설정 startup validator, 프론트 인계 계약과 환경변수 예시를 추가했다. 실제 AWS/Lattice/IAM/SG와 운영 DB migration은 실행하지 않았다.
- 검증 결과 `./gradlew clean test`는 424 tests, failures 0, errors 0, skipped 0으로 성공했다. `node --check scripts/mongodb/tmi-116-migrate-billing-exam-saga.js`와 `git diff --check`도 성공했다.
- 애플리케이션 구현과 로컬 회귀 검증은 완료됐지만 Jira `TMI-116` 상태는 변경하지 않았다. 활성화 전 프론트 header 선배포, 실제 replica-set migration·failure injection, Lattice 권한·경로와 INITIAL/REPLACEMENT staging E2E가 남아 있다.

## TMI-116 코드 증가량 설명 (2026-08-29)

- 이번 변경은 단순 Billing HTTP 호출 하나가 아니라 분산 saga의 정상 흐름, 응답 유실·process crash·동시 요청 복구, Mongo Transaction·영속 operation, SigV4 전송, startup fail-closed와 migration 및 회귀 테스트까지 한 번에 포함해 파일 수와 코드량이 커졌다.
- 핵심 비즈니스 코드는 `BillingExamCreationSaga`, `BillingExamCreationTransactionService`, `ExamCreationOperation`과 기존 시험 생성 연결부다. 나머지 큰 비중은 SigV4/strict contract client, 설정·index·transaction startup 검증, migration과 테스트다.
- 작업 트리 전체 diff에는 이번 TMI-116과 무관하게 이전부터 존재하던 10초 챌린지·비용 관련 문서 변경도 함께 표시되므로 전체 변경량을 전부 이번 구현 코드로 보면 안 된다.
- 이번 설명 턴에는 애플리케이션 코드를 추가 수정하지 않았고 Jira·AWS·Git commit/push도 변경하지 않았다.
- 종료 훅 재검증에서도 결론은 동일하다. 신규 운영 코드의 대부분은 saga 복구와 SigV4·Mongo 운영 안전장치이며, 기존 10초 챌린지·비용 문서 변경은 TMI-116 구현량과 분리해서 본다.
- 상세 설명 기준으로 기존 직접 Session 생성과 달리 Billing·Mongo 두 시스템에는 단일 Transaction을 걸 수 없어, `ExamCreationOperation`을 복구 지점으로 사용하는 saga 상태 머신이 필요했다. 정상 흐름 외에도 reserve/commit/confirm 각 경계의 응답 유실·rollback·동시 요청을 같은 operation으로 수렴시키는 코드가 핵심 증가분이다.
- 종료 훅 기준 상세 설명 기록도 완료했으며, 애플리케이션 구현·Jira·AWS와 외부 계약 상태에는 추가 변경이 없다.

## TMI-116 Saga와 SigV4 client 역할 구분 (2026-08-31)

- `BillingExamCreationSaga`는 시험 생성 use case의 업무 순서와 복구 정책을 담당한다. 영속 operation 상태를 읽고 reserve, local Session commit, confirm, status/cancel reconciliation 중 다음 행동을 결정한다.
- `SigV4BillingReservationClient`는 saga가 요청한 reserve·confirm·cancel·status를 Billing HTTP 계약으로 변환하고 VPC Lattice SigV4 서명, timeout, strict response decode와 오류 mapping을 수행하는 infrastructure adapter다.
- Saga는 HTTP 서명 방법을 모르고 `BillingReservationClient` port만 사용하며, SigV4 client는 시험 Session 상태나 어떤 복구 단계를 선택할지 결정하지 않는다. 이번 설명에서 애플리케이션·계약·Jira `TMI-116` 상태는 변경하지 않았다.

## Billing VPC Lattice AWS_IAM 선택 근거 재확인 (2026-08-31)

- Billing은 모바일 앱이 직접 호출하지 않는 내부 workload API이고 Identity·Learning Core는 이미 ECS에서 실행되므로, ECS application task role의 자동 회전 임시 credential을 서비스 신원으로 사용하는 VPC Lattice `AWS_IAM`+SigV4를 선택했다.
- Lattice auth policy가 IAM principal뿐 아니라 HTTP Method·Path를 함께 제한해 Identity와 Learning Core 권한을 route별로 분리하고, production/staging role과 service network를 교차 차단할 수 있다. Billing은 public/internal ALB 없이 Lattice target으로만 두고 SG로 task 직접 우회 경로를 막는 계약이다.
- 이 선택은 별도 workload JWT issuer·JWKS·client-credentials·client secret rotation과 token cache를 새로 만드는 비용을 피하며, 사용자 Access Token이나 caller header를 서비스 인증으로 오용하지 않는다. 대가는 Lattice 비용·AWS 종속성·SigV4 client 및 IAM/SG 운영 복잡도다.
- 이는 승인된 설계 근거 재확인이며 Jira `TMI-116`, 애플리케이션 코드와 실제 AWS 배포 상태는 변경하지 않았다. 실제 Lattice/IAM/SG staging 연결과 negative/E2E 검증은 여전히 운영 gate다.

## TMI-116 이후 다음 작업 재확인 (2026-08-31)

- 다음 애플리케이션 vertical slice는 `ExamSession.attemptGroupId`를 사용한 AttemptGroup 상태 event 파이프라인이다. Learning Core가 `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE`을 durable outbox에 기록해 publisher가 Billing으로 전달하고, Billing consumer가 event inbox·active Session fencing·상태 전이를 처리해야 한다.
- 현재 코드 재확인 결과 Learning Core outbox/publisher와 Billing `POST /internal/v1/attempt-group-events` consumer가 모두 없다. 안전한 배포 순서는 event 계약 동결 → Billing consumer 선배포 → Learning Core outbox/publisher 선배포 → staging E2E → consumer 후 publisher 활성화다.
- 현재 저장소에서의 다음 구현 대상은 Learning Core outbox/publisher지만, Billing consumer가 준비되기 전 publisher를 활성화하지 않는다. TMI-116 자체의 프론트 key·Mongo migration·Lattice/IAM/SG·staging saga E2E는 별도 운영 활성화 gate로 먼저 또는 병행해야 한다.
- 이번 설명에서는 Jira `TMI-116`, 애플리케이션 코드, Billing 저장소와 AWS를 변경하지 않았다. 신규 Jira는 아직 만들지 않았다.
- 종료 훅 기준으로도 다음 개발 대상은 AttemptGroup 상태 consumer/outbox/publisher이며, consumer-first 활성화와 TMI-116 staging gate 병행 원칙을 유지한다.

## TMI-116 독립 리뷰 P1/P2 검증 (2026-08-31)

- 리뷰 1은 유효하다. `BillingExamCreationSaga.start()`가 operation보다 Session을 먼저 조회하고 `ENTITLEMENT_CONFIRMING`이면 `EXAM_CREATION_PROCESSING`을 즉시 반환해, 기존 operation이 `SESSION_COMMITTED`여도 confirm/status reconciliation에 다시 진입하지 못한다. operation을 먼저 drive하고 terminal command purge 뒤에만 Session durable replay를 fallback해야 한다.
- 리뷰 2도 유효하다. commit 단계는 `DuplicateKeyException`과 `OptimisticLockingFailureException`만 동시성으로 분류하며 그 밖의 Mongo transient/unknown Transaction 예외에서 한 번 re-read한 operation이 아직 `RESERVED`면 shared reservation을 cancel할 수 있다. transient/unknown 결과는 cancel하지 않고 operation과 `(userId, creationOperationId)` Session 재조회 및 same-key retry로 수렴해야 한다.
- 리뷰 3도 유효하다. 현재 ObjectMapper는 unknown·duplicate·trailing token은 막지만 scalar coercion과 enum ordinal, creator field missing/null을 완전히 차단하지 않는다. confirm 성공 검증도 `attemptGroupStatus=OPEN`과 non-null `confirmedAt`을 요구하지 않는다. global coercion 차단과 endpoint/status별 필수·조건부 semantic validation이 필요하다.
- PR 범위 지적도 유효하다. 작업 트리에 TMI-116과 무관한 10초 챌린지·비용 문서가 섞여 있고 `FRONTEND_API_HANDOFF.md`는 Idempotency-Key 부분은 관련되지만 전체 파일 포함 의도를 확인해야 한다. 사용자 변경을 되돌리지 말고 selective staging/별도 commit으로 분리해야 한다.
- 이번 턴은 Jira `TMI-116` 리뷰 진단만 수행했으며 아직 코드를 수정하지 않았다. 현재 상태에서는 PR 전 세 finding 수정과 회귀 테스트가 필요하다.

## ENTITLEMENT_CONFIRMING·SESSION_COMMITTED 관계 설명 (2026-08-31)

- `ENTITLEMENT_CONFIRMING`은 Session 생성 충돌이 아니라 Billing reserve 뒤 Learning Core의 local Session commit은 성공했지만 Billing confirm은 아직 확정되지 않은 정상 중간 상태다.
- `commitReservedSession()`의 단일 Mongo Transaction이 새 Session을 `ENTITLEMENT_CONFIRMING`·`entitlementState=CONFIRMING`으로 insert하고 같은 Transaction에서 operation을 `RESERVED → SESSION_COMMITTED`로 저장한다. 따라서 Transaction이 정상 동작하면 두 상태는 함께 나타나며 rollback이면 둘 다 나타나지 않는다.
- 이후 Billing confirm과 local finalize가 성공하면 두 번째 Transaction에서 Session은 `IN_PROGRESS`·`CONFIRMED`, operation은 `SUCCEEDED`가 된다. confirm/status 실패 시 정상적으로 `ENTITLEMENT_CONFIRMING + SESSION_COMMITTED` 쌍이 남고 same-key retry가 이를 복구해야 한다.
- 이 설명은 Jira `TMI-116` P1 finding의 근거를 명확히 한 것이며 Java·테스트·Jira 상태는 변경하지 않았다.

## TMI-116 P1/P2 리뷰 finding 구현 완료 (2026-08-31)

- `BillingExamCreationSaga.start()`는 동일 `(userId, operationId)` operation을 Session보다 먼저 복구한다. operation이 존재하면 `SESSION_COMMITTED` confirm/status reconciliation을 우선 실행하고, operation이 없을 때만 Session을 장기 durable replay fallback으로 사용한다.
- Session commit 예외는 관측 결과를 `ADVANCED`, `SESSION_VISIBLE`, `NOT_VISIBLE`로 나눈다. Mongo transient/unknown 결과에서는 reservation을 취소하지 않고, operation이 이미 `SESSION_COMMITTED` 또는 `SUCCEEDED`이면 다음 상태로 진행하며 그 밖에는 same-key `EXAM_CREATION_PROCESSING` 재시도를 반환한다. 명시적인 local `IllegalStateException`에서 operation·Session이 모두 보이지 않을 때만 기존 cancel 보상 흐름을 유지한다.
- Billing 성공 응답은 scalar·숫자 enum coercion을 차단하고 reserve/confirm/cancel/status별 필수 문자열·enum·timestamp를 검증한다. Saga도 confirm의 `attemptGroupStatus=OPEN`·`confirmedAt`, status의 reservation kind·attempt group·session·mock exam·terminal timestamp, cancel timestamp를 fail-closed로 검증한다.
- 공개 시험 생성 API URL·Method·Request·성공 Response·`BaseResponse`와 기존 AI·S3·Redis 계약은 변경하지 않았다. Billing 저장소와 AWS도 변경하지 않았다.
- 회귀 테스트를 추가했고 `./gradlew clean test` 전체 432개가 통과했다. 남은 운영 gate는 실제 replica set에서의 failure injection, Lattice/IAM/SG 연결과 staging E2E이며 Jira `TMI-116` 상태와 Git commit/push는 변경하지 않았다.

## TMI-116 로컬 develop 반영 확인 (2026-08-31)

- 현재 checkout은 로컬 `develop`이며 `HEAD`, `develop`, `origin/develop`이 모두 PR `#24` merge commit `d95d18b42a47383c2237fdb7eae536b7495136fb`를 가리킨다.
- merge commit에는 TMI-116 최신 feature commit `c3e3c8296316b1e49014413eb3dc32efaad76aba`가 포함돼 있으므로 원격뿐 아니라 현재 로컬 코드에도 TMI-116 P1/P2 보완이 반영됐다.
- 애플리케이션 코드·Jira `TMI-116` 상태·AWS·Git commit/push는 변경하지 않았고, 코드 변경이 없어 테스트를 재실행하지 않았다.

## 멘토링용 저장소 구조 조사 준비 상태 (2026-08-31)

- 별도 Jira 이슈 키 없이 앱 Learning Core의 구조·기능·정보 체계·네이밍·컨벤션·기술 진화 방향을 분석하는 멘토링 산출물의 사전 구성을 정리했다.
- 예정 산출물은 한국어 중심의 draw.io 4종(컨셉맵, 아키텍처, Feature Map, IA)과 Markdown 표·문서 3종(네이밍 사전, 컨벤션과 code smell, 진화수렴·디팩토 비교)이다.
- 분석 범위는 현재 저장소의 구현 코드, 테스트, 설정, 계약·운영 문서와 migration script다. 기존 웹 POC는 제외하고, 실행 중인 기능·feature flag 기능·문서상 계획을 시각적으로 구분한다.
- 현재 확인된 주요 축은 시험·채점, Billing Reservation 시험 생성 saga, 회원 탈퇴 이벤트, 인증·보안·관측성, MongoDB·Redis·S3·Python AI·Billing 외부 연동이다. 10초 챌린지는 승인 계약 문서는 있으나 실제 구현 여부를 전수 조사한 뒤 별도 상태로 표시해야 한다.
- 아직 실제 draw.io와 본 조사 문서는 생성하지 않았다. 애플리케이션 코드와 외부 계약은 변경하지 않았고 기록 문서만 갱신했으므로 테스트는 실행하지 않았다.

## 멘토링 구조 조사 범위 확장 결정 (2026-08-31)

- 별도 Jira 이슈 없이 구조 조사 범위를 웹을 제외한 앱 서버 전체인 Learning Core·Identity·Billing으로 확장하는 방향을 권고했다.
- 조사 자체는 세 저장소를 같은 시점의 하나의 시스템으로 수행한다. 산출물은 통합 시스템 관점과 서버별 내부 관점을 분리해, 서비스 경계가 사라지거나 한 장의 복잡한 도식으로 뭉개지지 않게 구성한다.
- 컨셉맵·시스템 아키텍처·Feature Map·IA는 공통 상위 관점을 제공하고 필요하면 서버별 상세 페이지를 둔다. 네이밍 사전과 컨벤션은 공통 기준, 저장소별 용례·편차, 서비스 간 계약 용어를 비교할 수 있게 작성한다.
- 기존 웹 POC와 웹 프론트·백엔드는 계속 조사·변경 범위에서 제외한다. 아직 실제 전수 조사와 draw.io·본 문서 작성은 시작하지 않았으며 애플리케이션 코드와 외부 계약은 변경하지 않았다.

## 웹 제외 앱 서버 통합 구조 조사 완료 (2026-08-31)

- 별도 신규 Jira 없이 Learning Core `develop@d95d18b`, Identity `feat/TMI-116-billing-reservation-exam-saga@8c4f3ca`, Billing `develop@39e424d`를 기준으로 세 앱 서버의 코드·테스트·설정·계약·ADR·현재 상태를 통합 조사했다. 관련 구현 문맥은 `TMI-115`·`TMI-116`이며 Jira 상태는 변경하지 않았다.
- `docs/architecture/app-server-mentoring.drawio`에 통합 컨셉맵, 시스템 아키텍처, Feature Map, 앱 IA, 세 서비스 상세와 서비스 간 Gap을 포함한 8개 페이지를 작성했다.
- `APP_SERVER_SYSTEM_OVERVIEW.md`, `APP_SERVER_NAMING_DICTIONARY.md`, `APP_SERVER_CONVENTIONS_AND_CODE_SMELLS.md`, `APP_SERVER_EVOLUTION_CONVERGENCE_REVIEW.md`에 기능 상태, 식별자·suffix 정의, 실제 컨벤션과 위반, 진화수렴 방향·fitness function을 기록했다.
- 서비스 분리와 data ownership은 대체로 적절하다. 현재 우선순위는 Identity→Billing SigV4 transport 정렬, Billing AttemptGroup consumer와 Learning Core outbox/publisher, saga 운영 gate, AI Callback 인증·strict boundary, Learning Core 내부 capability 분리다.
- Billing saga는 코드가 구현됐지만 기본 off와 staging gate가 남는다. 10초 챌린지와 AttemptGroup 상태 파이프라인은 계약·계획은 있으나 현재 실행 코드가 없음을 별도 상태로 표시했다.
- 공개 API·AI·S3·Redis·JWT·Billing wire 계약과 애플리케이션 코드는 변경하지 않았다. draw.io XML 8개 페이지와 whitespace를 검증했으며 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.

## 멘토링 draw.io 문서 사용 안내 (2026-08-31)

- 첨부된 `<mxfile>` 텍스트는 `docs/architecture/app-server-mentoring.drawio`와 동일한 완전한 8페이지 draw.io 문서다.
- diagrams.net에서 `.drawio` 파일을 직접 열면 하단 페이지 탭으로 통합 컨셉맵, 시스템 아키텍처, Feature Map, 앱 IA와 서비스별 상세 페이지를 이동하며 편집할 수 있다.
- 이번 안내에서는 애플리케이션 코드와 외부 계약을 변경하지 않았고 Gradle 테스트를 실행하지 않았다.

## 현재 앱 서버 개발 방식 정리 (2026-08-31)

- 별도 Jira 이슈 없이 현재 세 앱 서버의 개발 방식을 계약 우선의 점진적·진화적 개발로 설명했다.
- 서비스는 Identity·Learning Core·Billing의 business capability와 data ownership으로 나누고, 기능은 vertical slice로 구현하면서 멱등 command, durable state, saga, outbox/inbox, feature flag와 운영 gate를 함께 설계한다.
- 현재 방향은 무조건 최신 기술을 도입하기보다 기존 앱·AI 계약을 보호하고 장애·중복·재시작에도 같은 결과로 수렴하게 만드는 실용적 구조다.
- 다음 성숙 단계의 주요 과제는 Learning Core 내부 모듈화, 미완성 event pipeline 종결, cross-service E2E와 tracing·문서 freshness 강화다.
- 애플리케이션 코드와 외부 계약은 변경하지 않았고 Gradle 테스트는 실행하지 않았다.

## AI 활용 개발 방식 정리 (2026-08-31)

- 별도 Jira 이슈 없이 사용자의 AI 활용 방식을 repository-grounded·artifact-driven·human-in-the-loop 개발로 정리했다.
- AI가 코드 작성 전에 저장소 전체를 조사해 컨셉맵, 아키텍처, Feature Map, IA, 네이밍·컨벤션·기술 비교 문서로 외재화하고, 사용자가 이를 다시 읽고 직접 옮겨 그리며 이해와 판단을 형성하는 방식이다.
- AI는 조사자·지도 제작자·리뷰어·구현 보조자이고, 사용자는 범위·제품 의도·계약·우선순위와 최종 승인권을 유지한다.
- 주의점은 AI 산출물의 근거 확인, 문서와 코드의 동기화, AI에게 이해 자체를 외주화하지 않는 것이다. 애플리케이션 코드와 외부 계약은 변경하지 않았고 Gradle 테스트를 실행하지 않았다.

## AI 개발 루프의 스킬 도입 방향 (2026-08-31)

- 별도 Jira 이슈 없이 공식 OpenAI Codex 스킬 문서를 기준으로 현재 AI 활용 방식에 스킬을 적용하는 방향을 정리했다.
- 반복되는 저장소 조사·멘토링 산출물 생성, 외부 계약 검증, vertical slice 계획, 출시 준비 검토는 스킬화 가치가 높다.
- 상시 프로젝트 제약은 `AGENTS.md`, 정확한 계약은 `docs/contracts`, 조건부 절차는 Skill, 결정적 검사는 script로 분리한다. 모든 지식을 하나의 스킬에 복제하지 않는다.
- 첫 도입은 `repo-cartographer`와 `contract-guardian` 두 개를 작게 만들고 실제 사용 결과로 trigger·입출력·template을 보정한 뒤 나머지를 확장하는 것을 권장한다.
- 이번 작업에서는 스킬 생성이나 설치, 애플리케이션 코드와 외부 계약 변경을 수행하지 않았고 Gradle 테스트를 실행하지 않았다.

## 토선생 개발 병행 학습 방향 (2026-08-31)

- 별도 Jira 이슈 없이 AI를 활용하면서 직접 코딩 역량을 키우기 위한 프로젝트 내 학습 루프를 정리했다.
- 실제 기능마다 일부 작은 핵심 로직과 테스트를 사용자가 먼저 작성하고, AI는 질문·힌트·리뷰·실패 사례 생성에 우선 사용한다.
- 권장 루프는 코드 읽기와 결과 예측 → 30~60분 직접 구현 → AI 리뷰 → 수정 이유 설명 → 다음 날 무자료 재구현 → 주간 회고다.
- 학습 주제는 Java/Spring 일반론을 넓게 훑기보다 현재 변경과 맞닿은 테스트, 상태 전이, 멱등성, Spring 경계, Mongo transaction·index 등에서 주당 하나씩 선택한다.
- 애플리케이션 코드와 외부 계약은 변경하지 않았고 Gradle 테스트를 실행하지 않았다.

## AI 구현·별도 학습 이중 트랙 방향 (2026-08-31)

- 별도 Jira 이슈 없이 현재 출시 속도를 유지하기 위해 production 구현은 AI에게 적극 맡기고, 학습은 최근 구현 코드에서 작은 개념을 추출해 별도로 수행하는 방식으로 조정했다.
- 학습은 프로젝트와 무관한 병렬 강의가 아니라 이번 주 코드의 테스트·상태 전이·Spring·Mongo·멱등성 중 하나를 toy example, 무자료 재구현, 코드 설명으로 연습한다.
- 권장 최소 리듬은 구현 후 5분 후보 기록, 주 2~3회 20~40분 직접 연습, 주 1회 60분 코드 해부이며 production 일정과 학습 실패를 분리한다.
- 애플리케이션 코드와 외부 계약은 변경하지 않았고 Gradle 테스트를 실행하지 않았다.

## 코딩 외 최소 학습 범위 (2026-08-31)

- 별도 Jira 이슈 없이 직접 코딩 외에 추가로 유지해야 할 최소 학습 범위를 정리했다.
- 요구사항·계약·아키텍처·문서화는 현재 AI 협업 과정에서 이미 반복 학습되고 있으므로 별도 커리큘럼보다 현행 산출물 검토를 유지한다.
- 별도로 강화할 핵심은 디버깅과 운영 관찰이다. 기능별 정상·실패 요청 직접 실행, 주 1회 end-to-end 요청 추적, 월 1회 장애 시나리오 분석을 권장한다.
- 기반 이론은 전 범위를 미리 공부하기보다 실제 코드와 장애에서 등장한 HTTP, transaction, index, JWT, timeout 등을 just-in-time으로 학습한다.
- 애플리케이션 코드와 외부 계약은 변경하지 않았고 Gradle 테스트를 실행하지 않았다.

## 긴 개발 문서의 읽기 원칙 (2026-08-31)

- 별도 Jira 이슈 없이 긴 AI 산출물을 모두 정독하는 대신 정독·훑기·필요 시 조회로 계층화하는 방식을 정리했다.
- 현재 변경의 계약, 결정, 위험, 검증 방법은 정독하고 배경 설명과 대안 비교는 훑으며 네이밍 사전·전체 inventory·과거 WORKLOG는 검색 가능한 reference로 사용한다.
- 앞으로 AI 문서는 결론, 반드시 읽을 항목, 결정 필요 사항, 위험, 상세 근거의 순서로 작성하도록 요청하는 것이 적절하다.
- 사용자가 현재 변경의 목적, 보호 계약, 변경 범위, 실패 방식, 검증 방법을 설명할 수 있는지를 정독 완료 기준으로 삼는다.
- 애플리케이션 코드와 외부 계약은 변경하지 않았고 Gradle 테스트를 실행하지 않았다.

## 10초 챌린지 AI Callback 목적지 확인 (2026-08-31)

- 승인된 v1 계약상 AI는 `POST {LEARNING_CORE_INTERNAL_BASE_URL}/internal/v1/challenges/grading/callback`으로 결과를 전달한다.
- 필수 header는 AI→Learning Core 전용 `Authorization: Bearer <AI_TO_LEARNING_CORE_CREDENTIAL>`, `X-Challenge-Contract-Version: v1`, `Content-Type: application/json`이다. 사용자 Access Token과 기존 시험 Feedback Callback, `BaseResponse`는 사용하지 않는다.
- `LEARNING_CORE_INTERNAL_BASE_URL`의 실제 host는 같은 ECS cluster의 private Service Connect 또는 동등한 private discovery 주소를 배포 설정으로 주입해야 하며 요청 Body가 Callback URL을 지정하지 않는다.
- 현재 `develop`에는 Challenge Callback Controller가 아직 구현·배포되지 않았다. 따라서 경로 계약은 확정됐지만 실제 호출 가능한 base URL과 credential secret 주입은 Challenge backend 배포 전에 환경별로 확정해야 한다.
- 관련 계약 Jira는 `TMI-102`, `TMI-105`, `TMI-106`이며 이번 확인에서 Jira 상태·애플리케이션 코드·외부 계약은 변경하지 않았다.

## 10초 챌린지 사용자 Token과 서비스 인증 구분 (2026-08-31)

- 앱의 사용자 Access Token은 앱→Learning Core 공개 Challenge API에서만 사용하고 Learning Core가 AI 요청에 전달하지 않는다.
- Learning Core→AI 평가 요청은 별도 전용 Bearer credential, AI→Learning Core Callback은 그와 다른 방향별 전용 Bearer credential로 인증한다.
- AI→Learning Core credential은 AI ECS task 환경에 secret store로 주입하므로 AI가 사용자 Token을 알거나 Callback마다 전달받을 필요가 없다.
- AI 요청과 Callback에는 계약상 `attempt_id`, `job_id`, `grading_attempt` 등 Challenge 작업 식별자만 사용하며 실제 userId와 사용자 Access Token은 포함하지 않는다.
- 관련 계약 Jira는 `TMI-102`, `TMI-105`, `TMI-106`이며 이번 설명에서 코드·계약·Jira 상태는 변경하지 않았다.
- 종료 훅 기준으로도 사용자 Access Token 비전달, 방향별 service credential의 ECS secret 주입 원칙을 유지하며 실제 credential 값은 기록하지 않았다.

## Billing TMI-117 완료 확인과 Learning Core 후속 작업 (2026-08-31)

- Billing `develop@37a3e1d`에 PR `#4`의 TMI-117 feature commit `96a5727`이 merge됐고 Jira `TMI-117`은 완료 상태다. `POST /internal/v1/attempt-group-events`, strict schema v1 decoder, inbox 멱등성, active Session fencing, Mongo Transaction/CAS, 보안·관측성·replica-set 테스트가 구현돼 있다.
- Billing 기록상 `./gradlew clean test` 전체 137개가 성공했으며 merge diff의 `git diff --check`도 통과했다. 현재 Learning Core에는 AttemptGroup outbox/writer/publisher가 아직 없다.
- 다음 개발 대상은 Learning Core의 `AttemptGroupStatusChanged` durable outbox와 lease 기반 SigV4 publisher다. `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE` 판정과 outbox 생성을 local state transition과 같은 Mongo Transaction/CAS로 묶고 Session당 terminal event 하나만 허용해야 한다.
- Billing consumer 코드는 준비됐으므로 Learning Core 개발은 시작할 수 있다. 다만 Billing consumer가 staging에 먼저 배포·활성화되고 Lattice/IAM/SG route가 검증되기 전에는 Learning Core publisher를 활성화하지 않는다.
- 현재 TMI-116 예외 범위는 AttemptGroup outbox/publisher를 명시적으로 제외하므로 TMI-117을 재사용하지 않고 Learning Core 전용 PLAN·신규 Jira와 해당 작업의 명시적 허용 범위를 먼저 확정해야 한다.
- 이번 확인에서 Learning Core/Billing 애플리케이션 코드, Jira, AWS와 Git commit/push는 변경하지 않았다.

## AttemptGroup outbox DELIVERED·DEAD_LETTER 보존 근거 (2026-08-31)

- `DELIVERED` 30일은 Learning Core 발행과 Billing 수신을 eventId·traceId로 대조하고, 응답 유실·중복·상태 불일치 사고를 조사할 수 있는 짧은 운영 증거 기간이다. 정상 전달된 문서를 영구 보존하지 않아 저장 비용과 userId/sessionId 보유 기간을 제한한다.
- `DEAD_LETTER` 90일은 계약 오류·관계 충돌·인증 장애처럼 자동 재시도가 중단된 미해결 event를 조사·수정하고 같은 eventId/payload로 수동 replay할 시간을 더 길게 제공한다. 해결되지 않은 event를 정상 전달 문서보다 먼저 삭제하면 Billing projection 복구 근거를 잃는다.
- Billing inbox의 보존 기간이 120일이므로 30일·90일은 그보다 짧다. 이 기간 안의 replay는 Billing의 same eventId/digest 멱등성 창 안에서 안전하게 수렴한다.
- `PENDING`은 아직 전달되지 않은 업무 event이므로 TTL 삭제하지 않는다. 보존 기간은 authorization이나 업무 상태의 근거가 아니라 운영 기본값이며, on-call 대응 SLA·감사·개인정보 정책에 따라 계약 변경 절차로 조정할 수 있다.
- 관련 작업은 Billing `TMI-117`과 후속 Learning Core outbox/publisher이며 이번 설명에서 코드·Jira·계약값은 변경하지 않았다.

## 세 앱 서버 문서 계층·완료 보고 규칙 (2026-08-31)

- 별도 Jira 없이 Learning Core·Identity·Billing `AGENTS.md`에 공통 문서 가독성 및 구현 완료 보고 규칙을 반영했다.
- 계획·조사·분석·리뷰 문서는 5줄 결론 → 반드시 읽을 내용 → 사용자 결정 → 위험·미확인 → 현재 작업 설명 → 상세 근거 부록 순서로 작성하고 파일 근거와 구현 사실·계획·추론을 구분한다.
- 구현 완료 후에는 변경 파일·동작, 유지/변경 외부 계약, 테스트와 결과, 남은 위험, 배포 전 확인, 예상 밖 diff, 다음 확인 사항을 빠짐없이 보고한다.
- 애플리케이션 코드와 외부 계약은 변경하지 않았다. 규칙·기록 문서 변경만 있어 Gradle 테스트는 실행하지 않고 세 저장소 `git diff --check`로 검증한다.

## IntelliJ 학습 프로젝트 구성 방향 (2026-09-01)

- 별도 Jira 없이 토선생 학습용 프로젝트는 Java 21+Gradle+JUnit의 가벼운 `java-lab`으로 시작하는 방향을 권고했다.
- 상태 전이·검증·멱등성·테스트는 Spring 없이 연습하고, Spring MVC·DI·Validation·Transaction·Repository가 학습 대상일 때만 `spring-lab`을 별도로 추가한다.
- production 저장소에 연습 코드를 섞지 않고 MongoDB·Redis·AWS 같은 운영 의존성도 처음부터 추가하지 않는다.
- 실제 학습 프로젝트는 아직 생성하지 않았으며 Learning Core 애플리케이션과 외부 계약은 변경하지 않았다.

## AttemptGroup 분산 trace·구조화 관측 계약 검토 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core outbox/publisher 후속 Jira는 아직 없다. 제안한 W3C Trace Context, baggage 제외, event payload·digest와 trace metadata 분리, publish attempt별 새 span, trace 장애 시 업무 전달 계속 원칙은 타당하다.
- outbox에는 raw inbound header 대신 검증된 `traceId`, parent `spanId`, `traceFlags`만 transport metadata로 저장하는 것을 v1 기본안으로 권장한다. `tracestate`는 실제 backend 요구가 생기기 전에는 저장하지 않고, 필요해지면 W3C propagator 검증·크기 제한·로그 금지를 적용한다.
- 모든 재시도에서도 동일 traceId가 필수라면 각 publish attempt를 저장된 origin context의 자식인 sibling span으로 생성해야 한다. OpenTelemetry link만 사용하면 새 traceId가 될 수 있으므로 `parent 또는 link`라는 선택지는 계약에서 제거하거나 traceId 연속성 요구를 완화해야 한다.
- Billing은 현재 inbound W3C traceId를 이어받지만 production code에서 `attempt_group_event_consume`라는 별도 span을 만들지는 않는다. 정확한 span 이름이 계약이면 HTTP server span 아래 별도 internal span 또는 ObservationConvention을 추가해야 하며, 로그의 `operation` 값과 span name을 구분해야 한다.
- 401/403은 개별 event의 영구 payload 실패가 아니라 전역 인증 설정 장애이므로 즉시 DEAD_LETTER 처리하지 않고 `BLOCKED_AUTH` 또는 PENDING+긴 backoff와 publisher circuit/alert로 격리한 뒤 같은 eventId로 재개하는 안을 권장한다. `invalid_trace_context`는 delivery outcome이 아니라 missing/invalid 고정 counter로 기록한다.
- SigV4와 tracing의 header mutation 순서를 계획에 고정해야 한다. 최종 publish/client span context를 inject한 뒤 SigV4 서명하고 이후 `traceparent`를 변경하지 않거나, trace header를 서명 대상에서 명시적으로 제외해야 한다. 자동 HTTP client instrumentation이 context를 재주입해 서명을 깨거나 예상 span 계층을 바꾸지 않는 contract test가 필요하다.
- Learning Core에는 아직 tracing bridge와 AttemptGroup publisher가 없고 현재 TMI-116 예외는 이 범위를 명시적으로 제외한다. 구현 전 신규 Learning Core Jira·PLAN·AGENTS 예외를 만들고, 이번 검토 보정사항과 필수 테스트를 반영해야 한다.
- 이번 작업은 분석 및 기록 문서 갱신만 수행했다. 애플리케이션 코드·공개 API·event JSON·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았고 Gradle 테스트는 실행하지 않았다.

## AttemptGroup publish 재시도 span 관계 설명 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다. 하나의 `traceId`는 사건 전체의 폴더 번호, 각 `spanId`는 origin·publish 시도·Billing 처리 같은 개별 작업 번호로 설명했다.
- 최초 publish와 재시도를 모두 저장된 origin span의 자식으로 만들면 publish attempt들이 sibling이 되고 같은 traceId 안에서 서로 다른 spanId를 가진다. 재시도를 직전 실패 attempt의 자식으로 만들지 않아 독립된 재시도라는 의미도 유지한다.
- OpenTelemetry link는 다른 trace에서 원본 trace를 참조할 수 있게 하는 연결일 뿐 같은 traceId를 상속시키지 않는다. 따라서 “모든 재시도와 Billing 처리를 같은 traceId로 검색”하는 현재 목표에는 parent 관계가 필요하다.
- origin context가 없을 때도 이후 재시도까지 같은 traceId가 필요하다면 첫 전송 전에 fallback delivery root context를 한 번 생성·보존하고 각 attempt를 그 자식으로 만들어야 한다. 그렇지 않으면 missing-context 재시도마다 새 trace가 생길 수 있다.
- 설명과 기록 문서만 갱신했으며 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.

## AttemptGroup 서비스 경계 span 설명 종료 상태 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- 최종 설명은 서비스 경계마다 기존 spanId를 변경하는 것이 아니라 같은 traceId를 상속한 새 spanId를 생성한다는 것이다.
- Billing의 W3C trace 연결은 현재 동작하지만 `attempt_group_event_consume`은 로그 operation일 뿐 production span name으로 고정되지 않았다. 명확한 업무 단계가 필요하면 HTTP server span 아래 별도 internal span을 추가한다.
- 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았으며 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup 서비스 경계 span과 Billing span name 설명 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다. 새로운 서버나 처리 단계로 넘어갈 때 기존 spanId를 수정하는 것이 아니라 같은 traceId 아래 새로운 span과 새로운 spanId를 생성한다는 의미로 정리했다.
- 현재 Billing은 inbound `traceparent`를 통해 Learning Core와 같은 traceId를 이어받고 새로운 HTTP server spanId도 자동 생성하므로 분산 trace 연결 자체는 동작한다.
- 다만 Billing trace 화면의 실제 span name은 Spring HTTP server 관측 이름일 수 있고 로그의 `operation=attempt_group_event_consume` 값이 자동으로 span name이 되지는 않는다.
- 업무 처리 단계를 명확히 보이게 하려면 Billing HTTP server span 아래 `attempt_group_event_consume` internal span을 하나 더 생성하는 안을 권장한다. 그러면 HTTP 수신과 실제 event 처리 시간이 분리된다.
- 설명과 기록 문서만 갱신했으며 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.

## AttemptGroup outbox trace metadata 저장 방식 설명 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다. 동일한 사건은 하나의 `traceId`로 묶고 origin·publish attempt·Billing consume은 서로 다른 `spanId`로 구분한다는 이해를 재확인했다.
- W3C `traceparent`는 version, traceId, 현재 spanId와 flags를 한 문자열로 포장한 전송용 header다. outbox에는 외부에서 받은 문자열을 그대로 저장하지 않고 propagator가 검증·분해한 `traceId`, parent `spanId`, `traceFlags`만 transport metadata로 보존한다.
- publisher는 저장 metadata로 parent context를 복원한 뒤 새 publish spanId를 만들고, 같은 traceId와 새 spanId가 담긴 새로운 `traceparent`를 HTTP 요청에 inject한다. 원본 header를 replay하면 publish span이 trace에서 사라지는 문제가 생긴다.
- trace metadata는 event JSON·canonical digest·idempotency key와 분리하고 baggage는 저장하지 않는다. v1에서는 필요성이 확인되지 않은 `tracestate`도 생략하는 권장안을 유지한다.
- 설명과 기록 문서만 갱신했으며 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.

## AttemptGroup Billing 업무 span과 인증 장애 처리 설명 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- 3번은 로그 내용을 더 길게 만드는 작업이 아니라 Billing HTTP server span 아래 `attempt_group_event_consume` 업무 span을 추가해 trace timeline에서 HTTP 수신과 decode·DB 반영 시간 및 실패 위치를 구분하는 관측성 보완이다.
- 4번은 400·409·422처럼 event 자체가 잘못된 영구 오류와 401·403처럼 IAM·SigV4·route 등 publisher 전역 인증 설정이 잘못된 장애를 분리하는 정책이다.
- 401·403에서는 개별 event를 DEAD_LETTER로 보내지 않고 `auth_failure`를 기록·경보하며 publisher circuit을 열고 event를 `BLOCKED_AUTH` 또는 장기 backoff PENDING으로 보존한다. 인증 복구 후 같은 eventId로 재개한다.
- 설명과 기록 문서만 갱신했으며 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았다. 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup span 추가와 오류 분류 의미 재확인 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- 3번은 로그 line을 append하는 것이 아니라 기존 Billing HTTP server span 아래 `attempt_group_event_consume` internal span 하나를 추가하는 것이다. 구조화 로그 필드는 늘리지 않는다.
- 시간 분리는 Learning Core publish/client span, Billing HTTP server span과 Billing consume span을 함께 비교해 판단한다. consume span은 decode·멱등성 판단·Mongo 반영이라는 업무 처리 구간을 나타낸다.
- 4번은 오류 이름만 자세히 나누는 것이 아니라 HTTP category별 상태 전이와 후속 행동을 고정한다. network·408·425·429·5xx는 retry, 400·409·422는 DEAD_LETTER, 401·403은 전역 auth 차단·경보·복구 후 재개로 처리한다.
- 설명과 기록 문서만 갱신했으며 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았다. 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup span·오류 분류 설명 종료 상태 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- 최종 이해는 3번이 로그 추가가 아닌 Billing consume internal span 추가이고, 4번이 오류별 명칭뿐 아니라 retry·dead-letter·auth-block 후속 동작까지 구분하는 정책이라는 것이다.
- 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았으며 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup trace header와 SigV4 순서 설명 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- 5번은 publisher가 새 span을 만든 뒤 최종 `traceparent`를 요청에 붙이고, 그 완성된 요청을 SigV4로 서명한 다음 header를 변경하지 않고 전송하도록 순서를 고정하는 것이다.
- SigV4 서명 뒤 자동 tracing instrumentation이 `traceparent`를 새로 쓰면 Billing이 받은 요청과 서명 대상이 달라져 401·403 인증 실패가 날 수 있다. 따라서 이 client의 trace inject 소유자는 하나로 제한한다.
- 재시도마다 새 publish spanId와 시각이 생기므로 eventId·payload는 유지하되 `traceparent`와 SigV4 서명은 매번 새로 생성한다. 서명된 HTTP 요청 자체를 재사용하지 않는다.
- 설명과 기록 문서만 갱신했으며 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았다. 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup SigV4 순서 설명 종료 상태 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- 최종 원칙은 publish span과 `traceparent`를 먼저 확정하고 SigV4로 서명한 뒤 전송 전 header를 변경하지 않는 것이다. 재시도는 같은 eventId·payload를 유지하면서 새 span과 새 서명을 생성한다.
- 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았으며 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup SigV4 최종 단계 확인 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- SigV4는 URI·method·body·일반 header와 `traceparent`가 모두 확정된 뒤 전송 직전의 마지막 논리적 변경 단계에서 수행한다.
- 서명 후 SDK signed request를 실제 HTTP request로 변환하는 작업은 가능하지만 서명된 header·body·path를 변경하거나 자동 tracing이 `traceparent`를 다시 inject해서는 안 된다.
- 설명과 기록 문서만 갱신했으며 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았다. 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup SigV4 마지막 서명 원칙 종료 상태 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- 최종 확인은 URL·method·body·header와 `traceparent`를 먼저 확정하고 SigV4 서명을 마지막 논리적 변경 단계로 수행한 뒤 요청을 변경하지 않고 전송한다는 것이다.
- 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았으며 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup 분산 trace 계약 최종 확정 판단 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다. 지금까지 검토한 trace propagation·span·오류 분류·SigV4 원칙은 권장안 기준으로 계약을 동결할 수 있는 상태다.
- 동일 traceId와 단계별 새 spanId, origin 공통 parent의 retry sibling, 검증된 `traceId`·`parentSpanId`·`traceFlags` outbox metadata, baggage·raw header·v1 tracestate 제외를 확정안으로 둔다.
- Learning Core는 publish attempt별 `attempt_group_outbox_publish` span, Billing은 HTTP server span 아래 `attempt_group_event_consume` internal span을 사용한다. missing/invalid context는 최초 fallback trace anchor를 CAS로 한 번 보존하고 counter 후 delivery를 계속한다.
- publisher outcome은 `delivered`, `retry_scheduled`, `dead_letter`, `auth_failure`, `lease_lost`로 고정하고 `temporary_failure`는 제거한다. network·408·425·429·5xx는 retry, 400·409·422는 dead-letter, 401·403은 `BLOCKED_AUTH`·전역 circuit·alert·복구 후 재개다.
- 최종 요청에 trace header를 inject한 뒤 SigV4를 마지막 논리적 변경 단계에서 수행하며, 재시도마다 같은 eventId·payload와 새 span·새 서명을 사용한다. 구현 전 Learning Core 신규 Jira·PLAN·AGENTS 명시적 예외가 필요하다.
- 이번 판단은 분석과 기록 갱신만 수행했으며 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았다. 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup 분산 trace 계약 동결 종료 상태 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- trace context 저장·재시도 span·Billing consume span·오류별 상태와 행동·SigV4 최종 서명 순서를 포함한 관측 계약을 권장안으로 동결했다.
- 다음 단계는 Learning Core 신규 Jira·PLAN과 AGENTS 명시적 예외 작성이며 이번 작업에서는 애플리케이션 코드·외부 계약·AWS·Jira·Git commit/push를 변경하지 않았다.

## AttemptGroup trace Billing 전달 범위 확인 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Billing 보완 또는 Learning Core 후속 Jira는 아직 없다.
- Billing은 W3C-only propagator, inbound traceId 연속성, 고정 구조화 로그와 저카디널리티 metric이 이미 구현돼 있어 event JSON·endpoint·status 계약 변경은 필요 없다.
- Billing 필수 보완은 production HTTP server span 아래 `attempt_group_event_consume` internal span을 실제 decode·service 처리 범위에 생성하고, 현재 테스트의 수동 span이 아니라 실제 controller 요청에서 같은 traceId·서로 다른 spanId·정확한 span name을 검증하는 것이다.
- 401·403의 `BLOCKED_AUTH`·circuit·재개와 trace inject 후 SigV4 최종 서명은 Learning Core publisher 책임이다. Billing은 인증 실패 status를 안정적으로 반환하고 raw trace header·payload·식별자·credential을 log/span/metric에 남기지 않는 기존 경계를 유지한다.
- 선택 보완으로 missing·invalid를 함께 기록하는 metric 이름의 의미를 명확히 하고 baggage 미전파와 inner span 예외 종료·민감정보 비기록 테스트를 추가할 수 있다. exporter/backend·dashboard·alert는 별도 운영 범위다.
- 이번 확인은 읽기 전용 분석과 기록 갱신만 수행했으며 Billing·Learning Core 애플리케이션, 외부 계약, AWS, Jira와 Git commit/push를 변경하지 않았다. 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup trace Billing 전달 종료 상태 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Billing 보완과 Learning Core 후속 Jira는 아직 없다.
- Billing 전달 필수사항은 production `attempt_group_event_consume` internal span과 실제 Controller 기반 trace/span contract test이며 endpoint·event payload·status 계약 변경은 없다.
- Learning Core 전용 outbox·retry·auth-block·SigV4 책임과 Billing 보완 범위를 분리해 전달했으며 이번 작업에서는 애플리케이션 코드·AWS·Jira·Git commit/push를 변경하지 않았다.

## Billing AttemptGroup consume span 구현 확인과 Learning Core 다음 작업 (2026-09-01)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core outbox/publisher 후속 Jira는 아직 없다.
- Billing 로컬 `develop@37a3e1d`의 미커밋 변경에서 production Controller가 strict decode와 service 처리를 `attempt_group_event_consume` span으로 감싸고, Micrometer helper가 정상·RuntimeException·Error 경로에서 span을 종료·오류 기록하는 구현을 확인했다.
- embedded Tomcat 통합 테스트는 same inbound traceId, HTTP SERVER와 INTERNAL consume의 서로 다른 spanId·descendant 관계, 정확한 span name, decoder/service scope, baggage 미전파, 정상·409 예외 종료와 금지 attribute 부재를 검증한다.
- Billing 현재 작업 트리에서 `./gradlew clean test`를 재실행해 138개 성공, 실패·오류·skip 0을 확인했고 `git diff --check`도 통과했다. 다만 변경은 아직 commit·push·PR·merge되지 않아 배포 가능한 저장소 상태는 아니다.
- 다음 즉시 순서는 Billing trace 보완 commit·PR·merge·consumer 배포 후 Learning Core 신규 Jira·PLAN·AGENTS 예외를 확정하는 것이다. 그 다음 Exam 상태 전이와 같은 Mongo Transaction/CAS에서 `GRADING` 또는 terminal outbox를 저장하고 lease publisher가 W3C span·SigV4로 Billing에 전달하도록 구현한다.
- 구현 계획에서는 GRADING의 정확한 local trigger, COMPLETED evidence 세 boolean의 source of truth, RETAKE_AVAILABLE failureCode mapping과 Session당 terminal event 하나의 불변식을 코드 경계에 매핑해야 한다. publisher feature flag는 기본 off로 유지한다.
- 이번 작업은 Billing 읽기 전용 검토와 Learning Core 기록 갱신만 수행했다. Billing·Learning Core 애플리케이션, 공개 API·AI·S3·Redis·event wire, AWS, Jira와 Git commit/push는 변경하지 않았다.

## Billing consume span 검증 종료 상태 (2026-09-01)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- Billing 로컬 구현과 전체 138개 테스트 성공을 확인했으나 변경은 미커밋이므로 commit·PR·merge·배포가 선행돼야 한다.
- 이후 Learning Core 신규 Jira·PLAN·AGENTS 예외를 만들고 Exam 상태와 outbox 동시 저장 및 lease·W3C·SigV4 publisher를 구현하는 순서로 확정했다.

## Billing trace 보완 merge 확인과 Learning Core outbox 계획 (2026-09-01)

- Billing `develop`과 `origin/develop`은 PR #5 merge commit `a34766e`로 일치하고 작업 트리는 clean이다. `b1f6fbd`의 production `attempt_group_event_consume` span, 실제 HTTP trace/span·baggage·privacy·오류 테스트가 병합돼 Learning Core publisher의 consumer-first 선행 조건을 충족했다.
- `docs/codex/ATTEMPT_GROUP_OUTBOX_PUBLISHER_IMPLEMENTATION_PLAN.md`를 추가했다. 모든 필수 retry 0 submit의 GRADING, strict 결과·점수·Summary evidence의 COMPLETED, 최종 복구 실패의 RETAKE_AVAILABLE을 ExamSession 상태와 outbox의 동일 Mongo Transaction/CAS로 만들고 Session당 terminal event 하나를 보장하는 계획이다.
- publisher는 lease 기반 multi-instance claim, same eventId/canonical payload retry, DELIVERED 30일·DEAD_LETTER 90일·미전달/BLOCKED_AUTH 무TTL, W3C same trace/different span, trace inject 뒤 SigV4 최종 서명을 사용한다.
- 구현 전 신규 Learning Core Jira와 현재 TMI-116 제외 범위를 해소하는 `AGENTS.md` 명시적 예외가 필요하다. 사용자 확정이 필요한 핵심값은 권장 `GRADING` deadline `PT30M`이고, 신규 Billing-linked Session의 Summary source는 `exam_summaries` only를 권장한다.
- 이번 작업은 Billing 읽기 전용 merge 확인과 Learning Core 계획·상태·작업 기록 문서만 변경했다. 애플리케이션, 공개 API·AI·S3·Redis·Billing event wire, AWS, Jira와 Git commit/push는 변경하지 않았다.

## AttemptGroup 구현 전 선택지 정리 (2026-09-01)

- 관련 선행 이슈는 Billing `TMI-117`이며 Learning Core 신규 구현 Jira는 아직 없다.
- 구현 전 필수 선택을 GRADING deadline, 최종 실패 확정 방식, Summary 완료 source로 구분했다. 권장 조합은 `PT30M`, 완료 evidence 우선 뒤 retry 소진·정합성 오류를 즉시 terminal 처리하고 deadline을 정체 safety net으로 사용하는 단계적 확정, 신규 Billing-linked Session의 `exam_summaries` only다.
- 운영 활성화 전 선택은 401/403 인증 복구와 기존 linked Session backfill이다. 권장안은 `BLOCKED_AUTH`·전역 circuit 뒤 15분마다 한 event만 half-open probe하고, 기존 Session은 전체 자동 스캔 대신 inventory/dry-run 후 allowlist backfill하는 방식이다.
- poll 1초, batch 20, lease 30초와 writer/publisher 기본 off는 설정으로 조절 가능한 기술 기본값이므로 별도 제품 결정 없이 권장값으로 둘 수 있다.
- 선택지·장단점은 `docs/codex/ATTEMPT_GROUP_OUTBOX_PUBLISHER_IMPLEMENTATION_PLAN.md` 3절에 반영했다. 애플리케이션, 외부 계약, AWS, Jira와 Git commit/push는 변경하지 않았다.

## AttemptGroup 정책 확정과 AGENTS 영구 허용 (2026-09-01)

- 관련 선행 이슈는 Billing `TMI-117`이며 Learning Core 구현 Jira는 아직 생성되지 않았다.
- 사용자가 `1B·2C·3A·4A·5C`를 승인했다. GRADING deadline은 `PT30M`, 완료 evidence 우선·retry 소진과 정합성 오류 즉시 종료·deadline safety net의 단계적 실패 확정, 신규 Billing-linked `exam_summaries` only, 401/403의 15분 단일 half-open, inventory/dry-run 후 allowlist backfill이 확정값이다.
- 특정 Jira에만 묶인 예외를 반복하지 않고 `AGENTS.md`에 AttemptGroup 상태 판정·durable outbox·lease publisher·제한된 reconciliation·RETAKE replacement 연결을 영구 허용하는 규칙을 추가했다. 신규 Jira는 범위 허가가 아니라 작업 추적과 완료 관리 목적으로 생성하면 된다.
- 영구 허용은 Learning Core 내부 구현에만 적용한다. 공개 API·AI·S3·Redis 계약, Billing consumer/저장소, UserMerged·owner rebind·결제 보상, 실제 AWS 리소스 생성·배포는 범위 밖이다.
- 계획서의 정책 상태, Phase 0과 완료 조건을 영구 허용 기준으로 갱신했다. 애플리케이션·AWS·Jira·Git commit/push와 Secret/Token은 변경하지 않았다.

## TMI-118 AttemptGroup outbox/publisher Jira 생성 (2026-09-01)

- Learning Core 후속 구현 Jira `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`을 `작업` 유형, 상태 `해야 할 일`로 생성했다.
- 이슈에는 GRADING/COMPLETED/RETAKE_AVAILABLE 판정, `PT30M` 단계적 실패 확정, strict `exam_summaries` evidence, Session당 terminal 하나, Mongo Transaction/CAS, lease·retry·retention·BLOCKED_AUTH, W3C trace와 최종 SigV4, RETAKE replacement 연결과 전체 완료 조건을 기록했다.
- 선행 이슈 `TMI-116`과 `TMI-117`, 공개 API·AI·S3·Redis 계약 불변, Billing·결제·인프라 제외 범위와 production 활성화 gate를 명시했다.
- 계획서의 Jira 상태와 Phase 0·완료 체크리스트를 `TMI-118` 기준으로 갱신했다. 애플리케이션 구현, AWS 리소스와 Git commit/push는 수행하지 않았다.

## TMI-118 Jira 생성 종료 기록 동기화 (2026-09-01)

- `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`은 `작업` 유형과 `해야 할 일` 상태로 생성 완료됐다.
- 확정 정책, 구현·제외 범위, 완료 조건과 production 활성화 제한은 Jira와 `docs/codex/ATTEMPT_GROUP_OUTBOX_PUBLISHER_IMPLEMENTATION_PLAN.md`에 동기화돼 있다.
- 이번 종료 동기화는 CURRENT_STATE와 WORKLOG marker 보완만 수행했으며 애플리케이션·AWS·Jira 내용·Git commit/push와 Secret/Token은 변경하지 않았다.
