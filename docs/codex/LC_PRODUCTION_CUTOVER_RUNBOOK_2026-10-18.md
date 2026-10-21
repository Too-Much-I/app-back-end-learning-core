# LC–Billing 운영 전환 실행안

## 1. 5줄 결론

1. TMI-116/TMI-217/TMI-220: 사용자 요청으로 구앱 보호 전환 절차를 작성했다. 실행 승인·봉인·배포 완료 문서가 아니다.
2. 양측 코드·설정·네트워크 변경안과 데이터 검증을 먼저 준비한다. LC 봉인 때문에 Billing의 사전 준비까지 기다릴 필요는 없다.
3. 전환 중 신규 시험 생성만 잠시 제한하는 안을 우선 검토한다. 제한 방식·응답·시간대는 실행 전에 승인받는다. 무중단을 보장하지 않는다.
4. 기존 생성 요청을 배출하고 exact 목록을 봉인한 뒤, 구 writer가 다시 생성하지 못하는 상태에서 새 코드를 배포한다.
5. 기존 태스크 완전 배출·인증/TLS·구앱/신앱 검증 후 개방한다. 새 데이터 생성 이후에는 구 이미지로 무조건 되돌리지 않는다.

## 2. 반드시 읽어야 하는 내용

### 후속 로컬 후보 준비

최신 보완: 실제 후보 재시작·늦은 Callback 및 production 프로필/JWT/TLS 격리 검사는 각각 통과했다. 아래 초기 기록의 미완료 항목 중 이 두 범위는 해소됐다. [배출·복구/공동 연결 확인표](LC_CUTOVER_DRAIN_AND_CONNECTION_CHECKLIST_2026-10-18.md)에 실제 in-flight 검증의 한계와 종료30초 대비 worker 기본 순차 대기20+30초를 기록했다. 운영 연결·봉인·배포·전체 작업 자동복구 통과를 뜻하지 않는다.

[후보 패키지](LC_PRODUCTION_CANDIDATE_PACKAGE_2026-10-18.md)에 dirty 포함 소스 지문·linux/amd64 로컬 이미지와 설정 변경 명세를 기록했다. unit1399(기존skip1), Mongo337, Node88 및 이미지 smoke3개 검증. prod9 시작 명령이 TLS entrypoint를 우회함을 확인해 기존 전처리를 유지하는 순수 변환 도구/검사8개를 준비했다. 실제 태스크 변경·원격 이미지 전달은 없고, 앱 ON 기동/종료·Callback 리허설과 최종 커밋/원격 digest는 남아 있다.

### 2026-10-18 12:33 KST 운영 읽기 재확인

- AWS ECS/ECR/ELB를 LC 전용 CloudShell에서 조회했다. LC는 여전히 prod:8, running1/pending0/PRIMARY COMPLETED,8080만 연결이다. 실행 digest는 `sha256:0b566cc1cd7b73ac87a0be133aa834f84333bb16b1a44db12d36b09bc9958fd0`, ECR tag는 `b2b37ff1857c807817235a9891073812538589c2`(10월11일 전달)다.
- 준비 prod:9도 같은 구 이미지·8080이며 Billing 신규 기능/production 승인은 false다. 완성된 전환 태스크가 아니다.
- 실제 container stopTimeout=30초, 현재8080 TG deregistration delay=300초다. 서로 다른 단계의 상한이며 300초가 앱 처리/Callback 완료를 보장하지 않는다. 환경변수와 SPRING_APPLICATION_JSON에서 shutdown/phase timeout override는 확인되지 않았고 command/entryPoint에도 해당 옵션 문자열이 없다. 이미지 내부 Spring 기본값 및 실제 종료 동작은 별도 검증해야 한다.
- 로컬 workflow는 현재 서비스의 taskDefinition을 읽어 이미지·profile·deployment environment만 교체한다. 따라서 main 머지만으로 준비 prod:9의 trust/Secret/TLS 준비가 승계되지 않는다. 운영 전환용 설정을 명시 조립·대조해야 한다.
- 로컬 HEAD `9fb8f71`에는 production readiness가 있으나 Azure 보완은 현재 작업트리에서도 미커밋이다. 최종 단일 소스 revision/image는 아직 확정하지 않았다. 로컬 origin/develop은 오래된 참조이므로 현재 원격 상태의 증거로 사용하지 않는다(GitHub API 연결 실패).

**다음 실행 단위:** 서버 중단이 아니라 readiness+공존+Azure 보완을 포함한 LC 후보 소스/이미지 확정과 별도 전환 task 설정안 작성이다. 기존 command/entryPoint override가 후보 TLS entrypoint를 가리지 않는지도 대조한다. 이후 격리 기동/종료·Callback 복구 리허설과 운영 DB 최신 읽기 점검을 마친 뒤 시간대·계정·전환 승인을 받는다. 이번에는 이미지 전달·commit/push·태스크 등록·DB 조회/변경·봉인·배포를 하지 않았다.

현재 관측/회신 기준 LC prod8과 Billing prod3이 실행본, LC prod9와 Billing prod4는 미배포 설정 준비본이다. 이번에 AWS를 재조회하지 않았다. 준비본의 이미지·port mapping·시작 명령을 그대로 배포해도 된다는 뜻이 아니다.

봉인 목록은 전환 전 시험의 exact 서버 ID 집합이다. source 누락으로 추론하거나 EXAM_PASS로 backfill하지 않는다. 목록 포함은 owner/삭제/terminal 검사를 면제하지 않는다. 이미 준비한 키·index와 최종 봉인은 별개다.

`AccessEventRolloutGate`는 receiver ON에서 공통 paid readiness를 요구한다. `PaidExamTestActivation`은 production 승인, paid/saga/signer/receiver/owner ON, production 환경 일치, verified legacy runtime 및 index 검사를 요구한다. `TransitionFreeExamService.start`도 legacy-create/owner/runtime이 없으면409이다. 따라서 새 코드+paid OFF 상태로 구앱 생성 유지를 기대하지 않는다.

## 3. 실행 전 승인·확정할 사항

- 신규 생성 제한 시간대와 최대 허용 시간, 제한 수단 및 공개 응답. 기존 ALB가 BaseResponse·Retry-After를 정확히 재현할 수 있다고 가정하지 않는다. 앱에서 안전한 재시도 안내가 되는지 사전 검증한다.
- 제한 대상은 실제 생성 writer에 도달하는 모든 경로다. `/api/v1/exams`, `/api/v2/exams`의 POST 및 trailing slash/정규화·내부 직접 접근/다른 인스턴스를 확인한다. prefix 전체 차단으로 submit/callback까지 막지 않는다.
- 최종 봉인 DB 쓰기, 양측 배포·feature 조합, exact ALB/SG/DNS/TG 변경과 인증된 검증 요청 범위를 별도 승인받는다.
- 기존 시험 조회/접수/채점/Callback 보존을 기본으로 한다. 구/신 코드 동시 실행에서 lifecycle/owner 검사가 달라질 수 있으므로 기존 태스크·worker 완전 배출 방식을 정한다. 생성 외 경로의 일시 제한이 필요하면 추가 영향을 설명하고 승인받는다.
- 실사용자의 시험 생성·소비·환불·삭제를 임의 테스트하지 않는다. 운영 검증 계정 및 금전/학습데이터 영향은 별도 지정한다.

## 4. 양측 병행 준비 — 생성 제한 전에 완료

| 담당 | 준비 및 증거 | 중단 조건 |
| --- | --- | --- |
| LC | 운영 readiness/공존/삭제 보완을 포함하는 exact commit·image digest, CI/격리 테스트 및 기존 실행본과 차이 | 검증하지 않은 이미지 또는 기능 누락 |
| Billing | 운영 환불 지원 이미지, TLS base64→PKCS12 시작 명령, production host/DB/cert 검사, exact 기능 조합 | 준비 task4의 기존 이미지/시작 명령을 완성본으로 오인 |
| 공동 | LC 환불 HTTPS origin 및 Billing workload origin 확정, production scope/JWK 지문, Secret 참조·역할 대조 | 예정 주소만 있고 실제 경로 불명 |
| 각 담당 | 8443/8444 port/TG/ALB·DNS와 최소 발신 대역의 exact 변경안, rollback 설정 | 기존 prod/test/다른 서비스 영향 불명 |
| LC | 최신 inventory dry-run, owner/index·삭제 연동 준비 검사, 기존19건 부모 미연결/개발 owner 영향의 경로별 판정 | 실패한 필수 검사 우회 또는 임의 자료 삭제 필요 |
| 공동 | 아래 전환 순서의 격리/test 리허설, 구앱 생성·기존 제출/채점·신앱 same-key 검증 | 공동 runtime 결과가 없거나 실행 중 요청 배출 근거 없음 |

삭제 index/데이터 조건은 해당 기능 범위별로 판단한다. 모든19건을 삭제해야 전환 가능하다고 단정하지 않으며, 검사 실패를 무시하는 것도 허용하지 않는다. feature OFF로 미룰 수 있는 범위는 실제 의존성 확인 후 명시한다.

## 5. 전환 순서와 단계별 통과 조건

1. **기준점 확보:** 실행 revision/image, feature·Secret 참조, ALB/TG 및 DB 검사 결과를 안전한 기록으로 고정한다. 비밀값·개별 시험 ID는 보고서에 쓰지 않는다. 운영 담당·중단 기준을 확인한다.
2. **신규 생성 유입 제한:** 승인된 exact 수단을 적용하고 v1/v2 모두 우회 없이 제한되는지 확인한다. 신규 사용자 시험을 만들 수 있는 다른 writer도 통제한다. 이미 들어온 요청의 처리 완료를 관측한다. 단순히 일정 시간 기다리거나 건수가 두 번 같은 것만으로 배출 완료를 주장하지 않는다.
3. **기존 writer 배출·목록 확정:** 생성 in-flight 없음과 구 writer가 더 생성할 수 없다는 근거를 확보한 후 최종 inventory→manifest 설치/봉인→readback hash/revision/count 대조를 수행한다. 운영의 비어 있지 않은 목록을 test 빈 목록으로 대체하지 않는다. 대상 수는 과거 관측값으로 고정하지 않는다.
4. **양측 배포:** 제한 유지 상태에서 Billing의 검증된 코드/TLS 시작 설정·LC의 검증된 코드/봉인 설정을 명시 적용한다. 공통 gate에 필요한 LC 설정은 함께 충족시킨다. 연결 선후관계는 사전 리허설에서 확정하고 Billing 업무 publisher는 수신측 준비 전 발신하지 않는다. 설정 기본OFF 준비본을 그대로 배포하지 않는다.
5. **구 코드 완전 배출 확인:** ECS COMPLETED뿐 아니라 이전 task/ALB draining 종료, 기존 request/worker 종료를 확인한다. 봉인 이후 새로 생긴 미분류 Session이 없는지 검사한다. 문제가 있으면 개방하지 않고 원인을 해결한다. 봉인 hash를 조용히 덮어쓰지 않는다.
6. **연결·인증 검증:** exact host/port/TLS·JWT 검증, 잘못된 token/route/공개8080 우회 차단, production scope 대조. TG health HTTP8080 성공은 TLS8444 성공 증거가 아니다. 양측 receiver 준비 후 승인된 업무 발신만 순차 개방한다.
7. **제한된 업무 검증:** 지정 계정으로 v1 무료 생성 및 기존 시험 조회/제출/채점/완료, v2 권리 검사·same-key 재현·새 source 저장·free fallback 부재를 검증한다. 환불/삭제/재시도 검증은 승인된 fixture/계정 범위에서 양측 결과를 기록한다. 실제 검증이 불가능하면 미완료로 남기고 일반 개방하지 않는다.
8. **일반 생성 개방:** 신규 v1 무료 생성 ON을 포함한 승인된 설정과 앱 호환을 확인하고 제한을 제거한다. 심사 기간 구앱은 무료, 신앱은 Billing 필수다. 기존 paid 진행/미완료 operation의 v1 차단은 유지한다. 오류율·pending/격리·outbox·Callback 실패를 확인한다.

동일한 production DB에서 새 runtime을 시작하는 것 자체가 worker 등 업무 변화를 만들 수 있다. 사전 후보 기동을 읽기 전용으로 취급하지 않는다. consumer/publisher/reconciler 설정을 역할별로 검토한다.

## 6. 실패·복구 기준

- **봉인 전:** 준비 실패 시 생성 제한을 해제하기 전에 기존 실행본/설정 및 in-flight 안전성을 확인한다. DB backfill·권리 재지급은 하지 않는다.
- **봉인 후, 새 코드의 쓰기 전:** 구 writer 재개는 봉인 대상의 미래 누락을 만든다. 전환 취소로 처리하고 해당 봉인을 이후 활성화에 재사용하지 않는다. 폐기/새 revision 절차는 별도 검토한다. 원본 hash 재작성으로 해결하지 않는다.
- **새 Session/operation/접수/lifecycle 변경 발생 후:** 자동 구 이미지 rollback 금지. 생성 제한을 유지하고 compatible 버전으로 forward-fix 또는 검증된 복구안을 선택한다. 이미 CONFIRMED인 Billing 소비를 cancel/삭제하거나 새 key로 재지급하지 않는다. 결과 불명은 status-first로 수렴한다.
- **연동 불명/장애:** v2를 무료 경로로 우회하지 않는다. v1도 공통 fence/owner/runtime을 우회하지 않는다. 서비스가 살아 있다는 이유만으로 신규 생성을 개방하지 않는다.
- 구/신 공존이나 배출 과정에서 기존 submit/Callback 보존을 보장할 수 없으면 실행을 중단하고 해당 제한·재시도 보장까지 재설계한다. 실제 접수 증거 없는 요청을 접수된 것으로 보상하지 않는다.

## 7. 완료 증거와 현재 미완료

완료 보고에는 양측 image/revision, 봉인 revision/hash/count, index/owner 검증, task 및 old-target 배출, 인증/TLS 결과, 구앱/신앱·환불/삭제 실행 결과와 미실행 항목을 구분한다. 로그에는 키/토큰/시험 원문을 기록하지 않는다.

이번 완료는 실행안 작성과 코드·인계 문서 대조뿐이다. 생성 제한 수단/응답·일정, 구 태스크 배출 리허설, 검증 계정, 실제 네트워크 변경안과 전환 승인은 미확정이다. 서버·DB·봉인·배포·Jira·외부 전송·commit/push 변경 없음.

## 8. 근거

### 생성 제한만으로 전환 가능한지 정적 점검 (2026-10-18)

결론: 전체 서버 중단이 반드시 필요하다고 입증된 것은 아니지만, 현재 자료로 생성 API 차단만으로 즉시 봉인·안전한 교체가 가능하다고 승인할 수도 없다. 아래는 로컬 후보 코드의 사실이며 실행 prod8 이미지의 동일 코드 포함 여부 및 ECS 종료 설정은 이번에 재조회하지 않았다.

1. 공개 생성은 ExamRestController의 POST /api/v1/exams와 PaidExamRestController의 POST /api/v2/exams로 분리된다. 제출/재채점/Callback은 별도 경로여서 경로별 제한은 설계상 가능하다. 그러나 Session insert는 ExamSessionManager/TransitionFreeExamService/BillingExamCreationTransactionService/PaidExamLifecycleService 여러 경로에 있다. HTTP 차단만으로 기존 요청·복구 writer 정지를 증명하지 못한다.
2. PaidExamCutoverInspector.inspectCandidate의 writersDrained 인자는 외부 확인 선언이며 자동 drain/fence가 아니다. 검사기는 미완료 operation을 거절하고 snapshot을 읽지만, 그 자체로 신규 writer를 잠그지 않는다. LegacyExamWriterGuard도 주석 및 구현상 분산 봉인 lock이 아니며 검사 후 insert 사이 경합이 남는다. old request 배출 근거 또는 검증된 writer fence가 필요하다.
3. 현재 저장소의 명시 봉인 설치 스크립트 prepare-paid-test-mongo.js는 test DB·빈 Session/operation·고정0건 hash 전용이다. 운영 비어 있지 않은 exact 목록을 설치/봉인하는 검증된 실행 도구를 이 점검에서 찾지 못했다. 이름/환경만 바꿔 실행하지 않는다. 운영 전용 dry-run/승인 summary/중단 후 재실행/unknown commit·readback 검증 절차가 필요하다.
4. GradingConfig는 summary executor에 waitForTasksToCompleteOnShutdown(false)를 설정한다. durable Job와 timeout/retry 경로는 있지만 종료 시 queued 작업이 모두 처리된다는 보장은 아니다. 새 서버에서 미완료 Job 재개를 리허설해야 하며 데이터 손실 확정으로 해석하지 않는다. Docker entrypoint의 exec로 SIGTERM 전달은 준비돼 있으나 그것만으로 HTTP/비동기 종료 완료가 입증되지 않는다. 명시 server.shutdown/phase timeout은 로컬 설정 검색에서 없었으며 Boot 기본값·실제 배포 override는 별도 대조 대상이다.

따라서 선행 작업은 **운영 목록 봉인 도구와 생성 요청/복구 writer 배출·Job 복구 리허설 준비**다. 이 준비가 끝난 후 짧은 생성 제한 방식으로 갈지, 추가 제한이 필요한지 결정한다. 현재 태스크를 먼저 내리고 작업을 시작하는 것을 권장하지 않는다. 이번은 진단만이며 코드 구현·테스트 실행·실환경 변경 없음.

### 2026-10-18 Billing 커밋·원격 이미지·경로 후속 확정 회신

- 소스 commit `57d66398ba3bf63a4fc2750ec8fd8cc4fdbdf11c`, 전달 기록 commit `3ca7d52`(사용자 보고). 아래의 dirty/원격 digest 미확정 기록은 이전 단계 이력이다.
- Billing 보고 배포 후보: `889384901776.dkr.ecr.ap-northeast-2.amazonaws.com/tosunsaeng-billing-test@sha256:e5666d537391eebc7cffe91896a756f93b93d8acef2d480030e1b56e9e7597f9`. ECR 이름의 test 문자열과 배포 환경을 혼동하지 않는다. 이미지 전달은 서비스 배포가 아니다.
- LC 환불 endpoint: `POST https://api-prod.to-teacher.com/internal/v1/entitlements/access/events`. Billing origin에는 경로 없이 `https://api-prod.to-teacher.com`을 사용한다.
- 양방향 발신 CIDR `13.124.57.130/32`: Billing 문서상 두 운영 private subnet의 default route→available NAT 및 public IP DISABLED를 확인했다. 공유 NAT이므로 IP 제한과 전용 JWT를 함께 유지한다. 적용 직전 현재 subnet/route/NAT를 재확인한다.
- 공개443 DNS/인증서 검증은 Billing 측 증거다. 전용8444 연결·수신ON·정상서명 공동 검증은 미완료다. 이번 LC 작업은 문서 대조이며 ECR/AWS 독립 재검증이 아니다.
- LC 후속 변경안은 exact host+POST+path+source CIDR→기존8444 TG 및 같은 내부 경로의8080 fallback 차단이다. rule70 evaluation/rule80 앱 경로는 보존하며 priority는 최신 listener 재조회 후 결정한다. 신규 생성 제한·봉인·배포의 미승인 조건은 그대로 유지한다.

### 2026-10-18 Billing 후보 후속 인계

`billing-production-cutover-package-2026-10-18.md` 회신상 Billing 실행본은 prod5로 갱신됐고 앱 설정 OFF 배포만 완료됐다. 앞선 prod3/prod4는 당시 관측/준비 이력이며 후속 적용 기준은 현재 서비스 재조회로 결정한다. 이번 LC 작업은 AWS 독립 조회가 아니다.

보고상 전체1838개 통과, 별도 workload 후보 빌드 및 스크립트3개 검사 통과다. 후보에는 미커밋 변경이 포함돼 commit 기반 재현 이미지·원격 digest가 아직 없다. 스크립트 검사는 Java launch를 stub하므로 실제 ON 기동/TLS/공동 E2E와 구분한다.

다음 LC 확인 범위는 운영 환불 HTTPS origin/route와 실제 Billing→LC 발신 출구대역, LC→Billing 출구대역이다. 이전 test NAT 관측을 production 확정값으로 복사하지 않는다. 양측 subnet/route/NAT 및 ALB 인증서/host를 읽기 대조한 뒤 exact 변경안을 확정한다. 네트워크 적용·봉인·배포 승인은 별도다.

- [LC 운영 연결 인계](LC_PROD_BILLING_CONNECTION_HANDOFF_2026-10-17.md) §8–9.
- [LC 운영 readiness](LC_PRODUCTION_ACTIVATION_PREPARATION.md).
- Billing 회신: `/Users/msde76/.codex/worktrees/plan012-refund/billing/docs/runbooks/billing-lc-production-connection-preparation-2026-10-18.md`.
- 코드: AccessEventRolloutGate, PaidExamTestActivation, LegacyExamRuntimeConfiguration, LegacyExamManifest, TransitionFreeExamService, ExamRestController/PaidExamRestController.
