# Learning Core 테스트 서비스 구성 현황

## 결론

- 2026-09-30 TMI-187 로컬 구현·검증 완료: Mongo Repository 자동 등록 통일, 실제 configuration의 동시ON 회귀 검증을 추가했다. 단위549/Mongo83/Node105 통과, API·업무 flags 불변. 원격 서비스·Task Definition 변경은 없으며 아래 test:6/OFF 복구 이력 이후 새 이미지 배포를 해야 한다. 배포 시 구 writer 완전 종료·최신 guard inventory, Challenge 설정/문제/index, workload 및 방향별 인증정보를 확인하고 ECS/ALB/HTTPS를 검증한다. Identity 발행/merge E2E는 별도다.
- 2026-09-30 최종 복구 확인: test:6/OFF 단일 deployment COMPLETED, desired/running/pending1/1/0, ALB healthy, HTTPS200/UP. test:7 활성화 성공을 의미하지 않으며 다음 작업은 UserMerged 조건부 repository 등록 코드 수정이다.
- 2026-09-30 UserMerged 후속(TMI-125/TMI-136/TMI-178): 테스트 배포 예외를 AGENTS.md에 반영하고 DB 최종 재검증 성공. 기존 이미지로 세 UserMerged flag와 issuer/JWKS만 설정한 test:7은 `UserOwnershipGuardRepository` bean 미등록으로 exit1이다. 정상 test:6/OFF로 복구 요청 후 HTTPS200/UP 확인, ALB/ECS 최종 수렴 대기 중. UserMerged 수신/E2E는 미완료이며 조건부 Mongo repository 등록 수정이 다음 차단점이다. 자세한 증거는 USER_MERGED_TEST_DB_PREPARATION_STATUS.md에 기록했다.
- 2026-09-28 Challenge 후속: 문제 100일/300개를 테스트 DB에 복사하고 원본과 전체 JSON 일치 확인. migration dry-run/apply가 6컬렉션/9필수 인덱스로 성공했다. LC test:4 활성화는 `UserOwnedTransactionExecutor` bean 누락으로 실패하여 정상 test:3/OFF로 복구했다. 아래 초기 bootstrap 이력과 구분하며 독립 활성화 코드 수정 후 재배포가 필요하다.
- 최신 결과: 사용자 credential 수정 후 DB 권한 오류는 해결됐다. 빈 LC 테스트 DB에 필수 인덱스 2개를 준비한 뒤 test:3이 1대 실행되고 HTTPS health 200/UP, 미인증 API401을 확인했다. 최종 ALB는 healthy 대상1개와 이전 대상 draining1개, ECS running1/pending0이다.
- 후속 진단 확정: Atlas의 LC 전용 사용자 권한은 정상이다. LC Secret URI가 Identity 전용 사용자로 접속하고 있어 LC DB 권한이 거절됐다. 사용자가 LC 전용 credential로 Secret을 수정한 뒤 재기동해야 한다. 권한 확대는 필요하지 않다.
- 후속 수정: 사용자 확인에 따라 JSON Secret의 `MONGODB_URI` 키 선택을 적용한 test:3을 배포했다. URI 형식 오류는 해결됐으나 Atlas가 `to-teacher-learning-core-test.exam_sessions`의 `listIndexes`를 error8000으로 거절했다. DB 사용자/권한 확인 전 desired0 복귀 요청 상태다.
- 2026-09-28 사용자 승인으로 테스트 ECS 서비스와 네트워크 리소스를 생성했다(TMI-126).
- 이미지 배포 후 1대로 기동했으나 MongoDB URI 형식 오류로 종료되어 `desired/running/pending=0/0/0`으로 복귀 확인했다. 정상 기동 완료가 아니다. 기존 운영은 `tosunsaeng-learning-core:20`, `1/1/0`을 유지한다.
- 기존 ALB·NAT·Redis를 재사용하고 기존 운영 서비스·Task Definition은 변경하지 않았다.
- 사용자가 DNS를 등록했고 인증서 ISSUED 및 ALB 추가 인증서 연결 완료, TLS 연결 정상화 확인.
- develop 배포 run `36366557693` attempt 2는 테스트·OIDC·이미지 배포까지 통과했지만 최종 health 단계에서 실패했다.

## 생성한 리소스

| 대상 | 값 |
| --- | --- |
| ECS cluster | `tosunsaeng-staging-cluster` (기존) |
| ECS service | `tosunsaeng-learning-core-test-service` |
| 최초 Task Definition | `tosunsaeng-learning-core-test:1` |
| 사양 | Fargate Linux x86_64, CPU 512 / memory 1024 |
| 테스트 SG | `sg-0245da61307f3e190` |
| Target Group | `tosunsaeng-lc-test-tg/7ea0893635d9cbfd` |
| ALB HTTPS 규칙 | priority 40, exact host `api-test.to-teacher.com` |
| 규칙 ID | `58f3ef09f5b962e3` |
| ACM certificate ID | `9b19c96b-d290-4602-a5c6-8f8f5c5b5e2e` |
| 로그 그룹 | `/ecs/tosunsaeng-learning-core-test`, 보존 7일 |

최초 task image는 `test-b2cd2b684eaeab0f8d01edc27295329bfcf43b74` 예정 태그다. 이미지가 존재하기 전에는 task를 실행하지 않는다. Workflow가 이미지 push 후 digest 기반 새 revision으로 변경한 것을 확인하고 desiredCount를 1로 올린다. 현재 workflow는 desiredCount를 변경하지 않는다.

실제 배포 revision은 `tosunsaeng-learning-core-test:2`, ECR과 일치하는 digest는 `sha256:88444cb679d0caf41e1d6e816441a9c2278bb35c762cb0a5eda218a9cd79b41b`다. 최초 1대가 RUNNING으로 등록됐지만 exit code 1 / EssentialContainerExited로 종료됐다. 정제한 최하위 예외는 MongoDB connection string이 `mongodb://` 또는 `mongodb+srv://`로 시작하지 않는다는 IllegalArgumentException이다. Secret 원문은 조회하지 않아 JSON 저장/불필요한 접두문자 등 정확한 저장 형태는 미확정이다. Secret 저장 형태 확인 또는 수정 전에는 재기동하지 않는다.

## 네트워크와 격리

- VPC `vpc-04068339482839ff1`, 기존 private subnets `subnet-0fa638ca8effafa81`, `subnet-0dc3e0343bb98e3e3`, public IP disabled.
- 두 subnet의 기본 경로는 기존 NAT `nat-0a11238673df5ef5d`; 실제 public IP `13.124.57.130`을 확인했다.
- 테스트 SG inbound는 ALB SG `sg-0e5462fa65a22ded8`의 TCP 8080만 허용한다.
- 테스트 SG 기본 all-egress를 제거하고 TCP 443/27017 목적지 0.0.0.0/0 및 Redis SG `sg-0f8528393d749b3a9` TCP 6379만 허용했다. Atlas 주소 변경을 수용하기 위한 27017 outbound이며 인터넷 inbound 공개가 아니다.
- Redis SG에는 테스트 SG에서 오는 TCP 6379 규칙 `sgr-0362698e12ea1e949`만 추가했다. 기존 규칙은 유지한다.
- Redis DB2는 논리 분리이며 보안·장애·메모리 격리는 아니다.
- 테스트 MongoDB secret 참조, 테스트 S3, Identity-test JWT 설정은 기존 template과 같다. Secret 원문은 읽거나 기록하지 않았다.
- Challenge·Billing·AttemptGroup·UserMerged·UserWithdrawn flag는 OFF다. AI 주소는 fail-fast loopback이며 시험 API 자체를 비활성화한 것은 아니므로 채점 가능 상태로 안내하지 않는다.

## DNS 등록 요청

AWS Route53 hosted zone 목록은 비어 있어 외부 DNS 관리자의 등록이 필요하다. `to-teacher.com` 영역에 아래 CNAME을 DNS 전용으로 등록한다.

| 이름 | 값 |
| --- | --- |
| `_45305af5fc4fe431478946bc2226bebb.api-test` | `_45d997eda7fa3cb5cbc470d1e0feb0a4.wzccmgtwzk.acm-validations.aws.` |
| `api-test` | `tosunsaeng-staging-alb-447454057.ap-northeast-2.elb.amazonaws.com` |

사용자 등록 후 ACM ISSUED와 `getent hosts`의 ALB DNS 연결을 확인했고 기존 443 listener에 추가 인증서로 연결했다. 기존 기본 인증서와 운영 host rule은 교체하지 않았다. 추가 직후 일시적인 hostname mismatch가 있었지만 이후 인증서 검증을 우회하지 않은 curl이 HTTP 503에 도달했다. TLS 연결은 완료됐으나 애플리케이션 health 200은 미완료다. CloudShell에는 dig가 없어 해당 조회는 실패했고 getent/ACM 결과를 근거로 사용했다.

## 완료 전 검증

- GitHub routing/unit/migration/Mongo integration tests, OIDC AssumeRole, ECR push 및 digest 배포 단계 통과 확인. 전체 workflow 성공은 아니며 health 단계 실패다.
- 실제 task의 Mongo/Redis 접근과 ALB health. 인증서/DNS 정상화 이후 외부 HTTPS health.
- JWT 미제공 API 차단 및 실제 MEMBER token 인증(사용자 토큰을 문서·로그에 기록하지 않음).
- S3 권한과 챌린지 문제/AI 연동은 별도 검증이며 이번 bootstrap만으로 완료를 주장하지 않는다.

## 이번 변경 및 인계

- 로컬 변경은 이 문서와 CURRENT_STATE/WORKLOG뿐이다. runtime 코드·공개 API·AI 계약은 변경하지 않았다.
- 로컬 Gradle은 코드 변경이 없어 재실행하지 않았고 배포 workflow에서 clean test, migration Node test와 mongoIntegrationTest를 실행했다. 문서 whitespace 검사도 수행한다.
- 기존 `.DS_Store` 및 BRANCH_DEPLOYMENT 문서 변경은 사용자/이전 작업 상태 그대로 유지했다. commit/push 없음.
- 다음 작업은 MongoDB Secret의 저장 형태 확인이다. JSON이면 정확한 key를 ECS secret ARN에 선택하도록 수정하거나 사용자가 plaintext URI로 저장해야 한다. credential 입력/변경은 사용자가 수행하며 실제 값은 채팅·문서에 공유하지 않는다.

## JSON 키 선택 수정 후 결과

- 후속 read-only 대조에서 LC 전용 사용자의 LC DB 전체 readWrite 및 Cluster0 허용을 확인했다. AWS LC Secret을 메모리 내 파싱해 사용자명·호스트만 출력했고, 실제 접속 사용자가 Identity 전용 사용자임을 확인했다. URI 전체/비밀번호는 출력하거나 저장하지 않았다. 사용자에게 Secret credential 수정 작업을 인계했으며 직접 변경하지 않았다.
- 사용자 확인: Secret은 JSON이며 키 이름은 `MONGODB_URI`다. `valueFrom` 끝에 `:MONGODB_URI::`를 추가했다. Secret 원문 조회·변경 및 IAM 확대는 하지 않았다.
- test:2를 기반으로 Secret 선택자만 바꾼 test:3 등록 후 1대로 기동했다. 이미지 digest와 나머지 설정은 유지했다. 로컬 task-definition template에도 동일하게 반영했다.
- Tomcat8080 및 Application started 신호 뒤 필수 시험 배정 인덱스 검증에서 종료됐다. 최하위 오류: AtlasError8000, `user is not allowed to do action [listIndexes] on [to-teacher-learning-core-test.exam_sessions]`.
- URI 형식과 네트워크 연결 단계는 통과했지만 이 사실이 올바른 LC DB 사용자/권한을 증명하지 않는다. URI가 LC 전용 사용자를 사용하는지 및 해당 DB의 readWrite 역할을 확인해야 한다. 권한을 임의로 확대하거나 startup 검증을 끄지 않는다.
- 반복 실행 중지를 위해 desired0 복귀 요청. health200·실제 MEMBER·DB/Redis/S3 앱 통합 검증 미완료. 코드/API/AI 계약 불변, 이전 CI 결과 유지하며 이번 설정 수정은 template JSON 파싱과 whitespace 및 AWS readback/정제 로그로 검증했다. GitHub workflow 재실행은 하지 않았다.

## 사용자 credential 수정 후 DB bootstrap

- 사용자의 저장 완료 알림 후 Secret 원문 조회 없이 test:3을 재기동했다. listIndexes 권한 거절은 해소됐고 필수 인덱스 누락으로 종료돼 준비 동안 다시 desired0으로 내렸다.
- Atlas tosunsaeng-test/Cluster0에 LC DB 자체가 없는 것을 확인했다. 빈 `to-teacher-learning-core-test` DB와 `exam_sessions`, `mock_exams` 컬렉션을 생성했으며 두 컬렉션의 Documents0 확인 후 아래 인덱스를 생성했다. 기존 문서 backfill·수정·삭제는 없었다.
- `exam_sessions`: 이름 `uniq_exam_sessions_active_user`, key `{userId:1}`, unique=true, partialFilterExpression=`{active:true}`, sparse/TTL/custom collation 없음.
- `mock_exams`: 이름 `uniq_mock_exams_mock_exam_id`, key `{mock_exam_id:1}`, unique=true, partial/sparse/TTL/custom collation 없음.
- 정의는 ExamAssignmentIndexValidator 및 tmi-31 migration INDEX_SPECS와 대조했고 Atlas Ready 확인. 기존 데이터가 없는 신규 DB라 legacy migration/backfill은 실행하지 않았다. 완료 이력 조회용 비필수 `idx_exam_sessions_user_completed_mock_exam` 인덱스 및 시험/챌린지 문제 seed는 아직 없다.
- 재기동 후 test:3 desired/running/pending=1/1/0, rollout COMPLETED, 인증서 검증을 유지한 HTTPS health200/UP 및 미인증 `/api/v1/exams` GET401 확인. 실제 MEMBER token 검증·S3 업로드·음성/AI E2E는 아직 미완료다.
- main LC:20은1대 실행 유지. GitHub의 이전 health 실패 run은 재실행하지 않았으며 이번 복구는 ECS 설정과 DB bootstrap으로 진행했다. 원격 신규 코드 push/이미지 변경은 없다.

## Challenge 활성화 시도 및 코드 차단점 (2026-09-28)

- 원본 `to-teacher-app.challenge_10s_questions`는 read-only로 조회하고 테스트 `to-teacher-learning-core-test.challenge_10s_questions`에만 복사했다. 100문서/300문제와 전체 JSON 일치, BSON int 검증 완료. 사용자 데이터·녹음 복사 및 원본 변경 없음.
- 기존 `scripts/mongodb/challenge-10s-prepare.js`를 mongosh7.0 일회성 Fargate task로 실행했다. dry-run/apply 모두 exit0, 6컬렉션/9인덱스 검증 성공. task ce7ea42dbd014719be488e43743cddfc 및 761aa4756a6b4283bfc3c5392e8ebae2는 STOPPED다.
- 승인된 LC test execution role에 AI Secret exact ARN GetSecretValue를 추가했다. IAM은 Secret 단위 접근이며 필드 단위 제한은 아니다. ECS에는 요청/콜백 키 두 개만 참조하도록 test:4를 구성했다. 원문 미출력, 운영 역할 불변.
- test:4는 기존 이미지와 나머지 flags를 유지하고 Challenge ON, AI HTTPS endpoint 및 양방향 Secret 참조만 추가했다. 기동 실패 원인은 `ChallengeConfiguration.challengeTransactions`의 필수 `UserOwnedTransactionExecutor` 주입이다. 이를 만드는 `UserMergedConfiguration`은 세 UserMerged flag가 전부 OFF면 등록되지 않는다.
- 다른 feature를 임의 활성화하지 않고 test:3/OFF로 복구했다. template도 정상 OFF 기준 유지. 시작일은 아직 생성되지 않았으며 사용자는 테스트에 한해 실제 활성화 성공일을 day1로 승인했다.
- 다음 단계: UserMerged OFF에서도 Challenge 자체 Mongo Transaction을 유지하고, writer ON에서는 ownership guard를 반드시 적용하는 구성 수정 및 회귀 테스트, 사용자 commit/push, 새 이미지 배포 후 Challenge 재활성화. 실제 MEMBER today/음성 제출/provider/Callback E2E는 아직 미완료다.
- 복구 중 같은 test:3의 교체 task bc75146c65554a5a8cbcab90aa0f017b는 시작 로그 후 ELB health 실패로 종료됐다. grace120초이며 정확한 health 실패 원인은 미확정이다. 진행 중 교체 deployment를 ECS StopServiceDeployment/ROLLBACK으로 중단하여 마지막 성공 deployment로 복귀했다. 당시 test:3 desired/running/pending=1/1/0·COMPLETED, ALB healthy 대상 유지(실패 대상 draining). 후속 결과는 아래 TMI-178 검증을 참고한다.

### TMI-178 후속 배포 검증 (2026-09-28)

- 현재 테스트 서비스: `tosunsaeng-learning-core-test:6`, 단일 deployment `COMPLETED`, desired/running/pending=1/1/0. HTTPS health200/UP.
- 테스트 ECS health-check grace를 사용자 승인으로 **300초**로 변경했다. ALB interval30초/healthy threshold5/unhealthy2/timeout5초는 유지한다. 서버 기동약51초와 초기 unhealthy 복귀 시간을 고려해 신규 서비스 재구성 시에도 grace300초를 유지한다.
- 동일test:6 이미지에서 기존120초는 실패했고 300초 적용 후 신규 태스크가 시작 약174초 시점에 healthy로 관측됐다. 코드 수정 없이 배포 정상화. 최초 health 요청의 개별 실패 원인은 직접 관측하지 못했다.
- 진단용 테스트 ECS Exec 활성 및 `LearningCoreTestExecDiagnostics` 역할 정책 유지. 내부 health는200/UP·16~90ms였다. 진단 접근 정리는 별도 후속으로 관리한다.
- UserMerged/Challenge 및 Billing 기능flags는 기존OFF 유지. `_id_` 수정 이미지 배포 성공과 UserMerged 실제 활성화/E2E 검증은 구분한다. 운영 변경 없음.
