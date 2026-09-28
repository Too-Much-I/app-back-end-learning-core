# Challenge 테스트 배포 인계

- 2026-09-28 배포 코드 변경: 사용자 명시적 요청으로 기존 workflow에 develop→테스트 분기를 추가했다. main 대상은 기존 그대로 유지한다. 아래 과거의 workflow 변경 미승인 상태는 이번 승인으로 대체되지만 실제 AWS 배포/리소스 생성 승인을 의미하지 않는다. 설정·최초 bootstrap·OIDC 권한 경계는 [브랜치별 배포 안내](deployment/BRANCH_DEPLOYMENT.md)를 따른다. 테스트 service가 없으면 기존 서비스로 fallback하지 않고 이미지 push 전에 중단한다.
- 갱신: 2026-09-23
- 임시 접근 정리 최신 결과: 사용자 최종 승인 후 lc-test-redis-check 환경 삭제(UI에서 해당 환경 제거 확인), 임시 그룹의 상호 규칙 제거 성공. 캐시 SG를 원래 sg-0f8528393d749b3a9 하나로 되돌렸고 cache available/SG active 확인. 캐시측 임시 sg-0f42223f236bb40ee 삭제 Return=true 및 최종 목록 부재 확인. CloudShell측 sg-0f83bb7d8c5b6b124는 관리 ENI eni-0aa843cd598fd3f0d 연결이 남아 DependencyViolation으로 삭제 불가, inbound/outbound는 모두 비어 있어 임시 Redis 접근 권한은 제거됐다. 관리 ENI는 강제 분리/삭제하지 않았다. AWS 측 연결 해제 후 이 SG 하나만 삭제하면 정리 완료다. 삭제한 환경은 복원 불가하며 기존 Redis 데이터/일반 CloudShell/기존 서비스는 삭제하지 않았다.
- 연결 점검 후속: VPC CloudShell에서 DB2를 명시한 PING=PONG 및 DBSIZE=0 확인. 이는 점검용 SG 경로 성공이며 아직 없는 `tosunsaeng-learning-core-test-service`(ECS MISSING)의 연결 검증을 대체하지 않는다. 임시 SG sg-0f83bb7d8c5b6b124는 CloudShell ENI eni-0aa843cd598fd3f0d, sg-0f42223f236bb40ee는 캐시 ENI eni-0818fdd91ebe4bb18에 연결되어 있다. 원래 캐시 SG sg-0f8528393d749b3a9도 유지 중이다. 임시 두 SG는 CloudShell→캐시 TCP6379 상호 참조 규칙만 갖는다. 정리는 캐시에서 임시 SG 분리(원래 SG 유지), 점검용 lc-test-redis-check 환경 제거, 미사용/참조 해제 검증 후 임시 SG 둘 삭제 순서이며 최종 승인 전 미실행이다. 기존 Redis 데이터/노드와 일반 CloudShell은 삭제 대상이 아니다.
- 사용자 Redis 재사용 승인 반영: LC-test의 배포 초안은 기존 `tosunsaeng-staging-redis:6379` endpoint와 `SPRING_DATA_REDIS_DATABASE=2`를 사용한다. 신규 캐시 생성이나 실제 ECS 등록/배포는 하지 않았다. DB2는 이 테스트 서비스용 계획상 배정이며 서버 측 별도 DB 생성 명령은 필요 없다. 기존 LC/AI 설정과 Redis key 계약은 변경하지 않는다.
- 추가 대조: 현재 `tosunsaeng-identity:23`, `tosunsaeng-identity-test:3` 모두 Redis 이름의 environment/secret 항목과 environmentFiles가 없다. 앞서 확인한 LC 기본 DB0 예상·AI API/worker DB1과 함께 확인한 ECS 설정에서 DB2 충돌은 발견되지 않았다. 숨은 이미지 설정/별도 클라이언트/예약 부재를 완전히 증명한 것은 아니다. 배포 직전 task override와 실제 RedisConnectionFactory DB=2 및 테스트 SG→6379 연결 검증이 필요하다. 공유 메모리/eviction/장애 위험과 전송 암호화 OFF 상태는 유지된다. FLUSHALL 등 전체 캐시 정리 금지, 부하 테스트는 별도 승인한다. 임시 진단 SG 두 개와 CloudShell VPC 환경의 정리는 아직 미실행이다.
- Redis 최신 실조회: 일반 CloudShell에서 기존 서비스 정의 LC `tosunsaeng-learning-core:20`, AI `tosunsaeng-ai:13`을 확인했다. LC Redis 환경값은 HOST/PORT만 있고 Redis secret/env file은 없으며 기본 DB0 예상이다(배포 이미지 내부 설정·runtime override 미검증). AI API와 worker1~4는 REDIS_URL 경로 `/1`이다. URL 인증부/secret 값은 출력하지 않았다. VPC 셸 ECS 조회는 connect timeout으로 끝났고 이후 메모리 요약은 성공하여 used 21.03MiB/max 384MiB를 확인했다. DB2를 테스트 후보로 권장하지만 전체 consumer 예약/런타임 검증은 남으므로 template은 미확정 유지한다. 신규 캐시/설정 변경 없이 점검 SG 정리는 후속이다. 아래 과거 미완료 관찰보다 이 기록이 최신이다.
- 관련 이력: TMI-126. 신규 인프라 전용 Jira는 미생성.
- 상태: 2026-09-23 사용자 확정으로 `api-test.to-teacher.com`의 LC 단독 기동 준비에 한정한다. AI 이미지 미준비·키 미수령으로 AI 이미지/서비스/양방향 연동은 보류한다. Challenge OFF 유지. AWS 생성·배포·secret 발급은 미실행.

## 0. 이번 준비의 5줄 결론

1. 사용자가 별도 테스트 서버 준비를 확정했다. 기존 운영/staging 서비스는 유지하고 학습 기록 삭제 기능은 계획으로만 남긴다.
2. Identity의 정확한 issuer/JWKS는 담당자 인계를 받았고 ECS 테스트 서비스 정상 상태를 콘솔에서 확인했다. 실제 MEMBER 토큰 연동 검증은 미완료다.
3. 로컬 `develop@16eb5de`에서 전체 단위 테스트 543개, Challenge Mongo 통합 테스트 29개, Challenge migration Node 테스트 7개가 통과했다.
4. LC 테스트 주소는 `api-test.to-teacher.com`으로 확정했다. 기존 `deploy-staging.yml`은 현재 LC 서비스 갱신용이므로 실행하지 않는다. 테스트 DB/S3/Redis와 배포 사양은 확인이 필요하다.
5. 2026-09-23 인앱 콘솔 접근과 ECS/ECR inventory를 확인했다. 테스트 도메인·데이터 격리·배포 이미지·사양/비용 확정 없이 이름만 추정하여 생성하지 않는다. 실제 migration apply·배포·flag ON은 아직 실행하지 않았다.

### 0.1 반드시 읽을 안전 경계

#### LC 준비 산출물과 적용 전 점검 (2026-09-23)

- 사용자 LC Secret 저장 완료 후 목록/상세 메타데이터로 `tosunsaeng/test/learning-core/mongodb` 존재와 AWS 관리형 암호화 키를 확인했다. ECS 초안 MONGODB_URI의 valueFrom에 확인된 ARN을 반영했다. 값은 열지 않았으므로 일반 텍스트 URI 형식·비밀번호 정확성·실제 연결은 미검증이다. DB 계정 생성은 앞서 Atlas 목록으로 확인했으며 물리 DB/collection 초기화 완료와 구분한다. ECS 실행 역할의 해당 Secret read 권한·허용 NAT 경로, Redis/S3·이미지 push·TLS 준비는 남는다.

- **Atlas 대상 정정:** 사용자 지정 대상은 `tosunsaeng-test / Cluster0`(project `6ab0c0b5a76fa14fc9e6d6af`)다. 앞서 `Project 0`에서 관찰한 전체 IP 허용·관리자 계정·84% 용량·DB 목록을 테스트 환경에 적용하지 않는다.
- 올바른 테스트 프로젝트에서 확인한 DB 계정은 `to-teacher-test`, 역할은 `readWrite @ to-teacher-identity-test`다. `All Resources` 표시는 DB 역할 제한을 없애지 않는다. 현재 보이는 계정은 Identity DB만 읽기/쓰기 가능하며 LC 테스트 DB 역할은 없다. AWS secret과 동일 계정인지 비밀값을 열어 대조하지 않았다.
- 테스트 IP Access List에는 `13.124.57.130/32` 한 항목이 Active이며 설명은 `AWS staging NAT - test services`다. 현재 사용자 IP는 미허용 경고가 있다. 이 IP가 LC task subnet의 실제 outbound NAT인지 AWS route 대조는 미완료다. LC가 같은 허용 NAT로 나가면 사용자 PC IP를 추가할 필요는 없다. `0.0.0.0/0` 확대나 현재 IP 추가는 수행하지 않았다.
- 테스트 overview 용량은 `0 B / 512 MB`로 표시됐다. 이것만으로 DB 미생성을 단정하지 않는다. 다음 권장안은 같은 테스트 cluster에 LC 전용 DB와 해당 DB의 readWrite 전용 사용자/별도 secret을 준비하고 Identity 계정·secret·권한은 유지하는 것이다. 사용자 생성/권한 부여는 실행 시 승인, 비밀번호 생성·입력/저장은 사용자 handoff가 필요하다.

- 로컬 `develop@16eb5deb69afa88c28f561275a820b00a5753aa1` 기준 `./gradlew clean test bootJar --no-daemon` 성공: 543 tests, failures/errors/skipped 0. 실행 JAR은 `build/libs/app-back-end-learning-core-0.0.1-SNAPSHOT.jar`다. 실제 테스트 인프라 기동 검증은 아니다.
- LC 로컬 Docker 이미지 `learning-core-test:16eb5de` 빌드 성공. `docker image inspect`로 `linux/amd64`, `user=app`, 로컬 ID `sha256:0a12ebebb37a216fd365f3f464d2604e340716b857aaa5286da8bb55f40322a8`을 확인했다. 이 ID는 ECR push 완료/registry digest 증빙이 아니다. 기존 Dockerfile을 사용했고 secret은 빌드에 주입하지 않았다. AWS task 실행·전체 컨테이너 기동·외부 연동은 미검증이다.
- ECS 등록용 초안: [learning-core-test.task-definition.template.json](deployment/learning-core-test.task-definition.template.json). `REQUIRED_*`는 미확정 참조이며 반드시 검증한 값으로 치환해야 한다. 현재 파일은 바로 등록할 수 있는 완성본이 아니다. 운영 task definition을 복사하거나 기존 service를 갱신하지 않는다.
- 초안 사양은 Fargate Linux x86_64, 0.5 vCPU/1 GiB, task 1개 권장이다. 아직 승인/생성하지 않았고 실제 기동 메모리·부하 검증과 서울 요금 확인이 필요하다. task 실행·로그·데이터 리소스 비용이 발생하며 신규 ALB/NAT는 이번 초안에 포함하지 않는다.
- service 목표: `tosunsaeng-learning-core-test-service`, family/container `tosunsaeng-learning-core-test`, public IP OFF, 기존 VPC 내 검증한 subnet과 테스트 전용 SG. 별도 IP target group:8080, health HTTP `/actuator/health`/200, grace 300초를 제안한다. HTTPS443의 정확한 host `api-test.to-teacher.com`만 신규 target group에 연결하고 기존 host/default rule은 변경하지 않는다. SG는 ALB SG→task8080으로 제한하며 outbound는 DB/Redis·JWKS·ECR/logging·S3에 필요한 경로를 검토한다. 실제 변경 직전 승인이 필요하다.
- IAM은 실행 역할(ECR pull·로그·테스트 Mongo secret read 및 필요 KMS decrypt)과 task 역할(테스트 S3 object 범위)을 분리한다. task 역할에 운영 DB/secret/bucket 접근을 추가하지 않는다. log group `/ecs/tosunsaeng-learning-core-test`는 사전 생성이 필요하고 7일 보존을 제안한다. 초안은 log group 자동 생성 권한을 요구하지 않는다.
- 기본 시험 AI URL이 `http://tosunsaeng-ai:8000`이므로 테스트 초안에서는 `AI_SERVER_URL=http://127.0.0.1:9`로 명시해 기존 AI로 잘못 보내지 않도록 했다. 이는 AI 미연결 상태의 fail-fast 목적이며 시험 API 자체를 차단하는 기능은 아니다. AI sidecar를 두지 않으며 이번 단계는 health/auth 점검만 하고 모의고사 submit·채점은 검증하지 않는다.
- 테스트 전용 신규 배포의 Billing/AttemptGroup/UserMerged/UserWithdrawn 관련 flag는 OFF다. 기존 배포의 flag를 끄지 않는다. 통제된 테스트 계정만 사용하며 탈퇴·병합·실사용자 테스트는 금지한다. 테스트 계정 수명주기 연동은 이후 별도 준비가 필요하다. Sentry는 미승인 외부 전송을 피하기 위해 초안 DSN을 비워 두었다.
- Mongo는 새 테스트 DB와 그 DB만 접근하는 계정/secret 참조가 필요하다. Redis는 운영과 분리된 인스턴스/인증 범위를 우선하며 prefix만으로 격리됐다고 간주하지 않는다. Redis TLS/인증을 요구하는 자원이면 Spring Data Redis 대응 설정을 추가 확인한 뒤 초안에 반영한다. S3는 private 테스트 전용 bucket과 task 권한을 준비한다. 연결값·비밀번호·키를 이 파일에 직접 넣지 않는다.
- ACM 읽기 전용 확인: Identity-test 인증서 `a405caa1-7cc1-4997-b82f-5c8e72d029df`는 domain 1개(identity-test)이고 추가 이름0이다. LC api-test 인증서로 재사용할 수 없다. 현재 LC 인증서 발급/DNS 검증/ALB 부착은 수행하지 않았다.
- ECR `tosunsaeng-learning-core` 목록37개 확인: `staging` tag는 `afa686c10209f703b3cc89e74df6d6628bb1e93e`, 2026-08-25 이미지다. 현재 develop revision과 다르므로 이를 신규 테스트 서버에 사용하지 않는다. 별도 test tag로 새 이미지를 push한 뒤 registry digest를 고정해야 하며 기존 `staging` tag를 덮어쓰지 않는다. CLI 인증 미연결 상태이므로 push/등록/서비스 생성은 미실행이다.
- 다음 적용 순서: 테스트 DB/Redis/S3·IAM 참조와 사양 승인 → 새 LC image push 및 digest 확인 → test task 등록 → 전용 service/target group 생성 → api-test 인증서/DNS·정확한 host routing → health/인증 검증. AI 비밀값은 이 단계의 선행조건이 아니다. CI/CD workflow 신설이나 기존 workflow 실행은 하지 않는다.
- 사용자 후속 확인: 테스트 DB는 준비했다고 답변했다. DB 이름, 테스트 전용 계정/권한, ECS에 주입할 Mongo secret 참조와 실제 연결은 아직 확인하지 않았다. Redis/S3 준비 여부는 답변에서 확인되지 않았다. DB를 새로 만들지 않고 기존 준비한 테스트 DB의 비밀 아닌 이름/참조를 인계받는다.
- 후속 Secrets Manager 목록 조회: 서울 계정의 9개 secret 중 이름에 mongodb가 있는 것은 `tosunsaeng/test/identity/mongodb`다(2026-09-21 생성, 설명 없음). LC 테스트용이라는 증거는 없으며 Identity 이름의 secret을 LC에 임의 연결하지 않았다. 같은 Mongo cluster를 의도하더라도 LC 전용 DB/계정 권한은 별도 확인해야 한다. secret 값/URI/비밀번호는 열람하지 않았고 task 초안의 Mongo 참조는 미확정으로 유지한다.
- Atlas 사용자 로그인 후 읽기 전용 확인: 현재 `Project 0 / Cluster0`의 IP Access List는 `0.0.0.0/0` Active이므로 IPv4 출발지 제한은 없다. DB Users 목록의 계정1개는 `atlasAdmin @ admin`/All Resources다. 이 계정이 AWS secret에 든 계정인지는 확인하지 않았다. TLS/비밀번호 인증이나 ECS outbound까지 검증한 것은 아니며 실제 LC 접속 성공으로 해석하지 않는다.
- 같은 cluster의 DB 이름 목록은 시스템 DB 외 `to-teacher-app`, `to-teacher-identity`, `tosunsaeng-db`다. 명시적인 LC 테스트 전용 DB는 목록에서 확인되지 않았다. 첫 write 전 DB는 목록에 없을 수 있고 다른 project/cluster의 준비 여부도 미확인이다. collection/사용자 문서는 열지 않았다. cluster 용량은 430.09/512 MB(84%)로 표시돼 테스트 데이터 증가 전 용량 계획이 필요하다. 설정/IP/계정 권한은 변경하지 않았다.

#### 최신 범위: LC 단독 기동 준비

- 사용자 결정: LC 주소는 `https://api-test.to-teacher.com`. DNS 추가 가능 여부에 긍정 답변을 받았으나 실제 record·인증서 발급/연결 완료는 미확인이다.
- 지금 준비: 별도 LC task/service 후보 `tosunsaeng-learning-core-test-service`, 컨테이너 8080, health path `/actuator/health`, 테스트 전용 데이터 연결, Identity JWT 설정, DNS/TLS/ALB 연결 계획. 실제 실행 사양·비용과 권한 변경은 실행 전에 확인한다.
- 이미지 빌드 자체에는 runtime AI API key가 필요 없다. 다만 사용자 요청에 따라 지금 AI 이미지 build/push와 API/worker 배포는 모두 보류한다. LC는 runtime secret 없이 로컬 Docker build를 완료했고 push/배포는 하지 않았다.
- `CHALLENGE_ENABLED=false`이면 `ChallengeProperties.validate()`는 AI endpoint/방향별 credential 검증을 건너뛴다. 미수령 키를 임의 값으로 채우거나 기존 운영 AI 키를 복사하지 않는다. 이는 LC 전체 기동 의존성 면제가 아니다. `MONGODB_URI` secret 참조, 명시적 테스트 `MONGODB_DATABASE`, 테스트 `REDIS_HOST`/`REDIS_PORT`, `AWS_S3_BUCKET_NAME` 및 task role/네트워크를 별도로 준비해야 한다.
- 준비할 비밀 아닌 환경값: `SPRING_PROFILES_ACTIVE=staging`, `APP_AUTH_MODE=jwt`, `IDENTITY_ISSUER=https://identity-test.to-teacher.com`, `IDENTITY_JWK_SET_URI=https://identity-test.to-teacher.com/.well-known/jwks.json`, `IDENTITY_AUDIENCE=tosunsaeng-learning-core`, `CHALLENGE_ENABLED=false`, `AWS_REGION=ap-northeast-2`, `SERVER_PORT=8080`.
- 지금 하지 않음: Challenge ON/Day1 초기화, AI 서비스/Redis queue/임시 S3 생성, provider key 및 방향별 credential 주입, 실제 채점/Callback E2E. LC용 Redis와 AI queue 준비는 구분한다. 이후 연결할 callback 목표 주소는 `https://api-test.to-teacher.com/internal/v1/challenges/grading/callback`이며 현재 사용 가능한 endpoint라는 의미는 아니다.
- 기동 준비 완료 판정은 승인된 LC 이미지·테스트 DB/Redis/S3 연결·TLS routing·JWT 설정을 갖추는 것이다. 실제 배포 후 health와 정상 MEMBER 인증을 확인해야 서버 오픈으로 보고하며 Challenge 사용 가능과 구분한다. 앞선 timeout 리뷰 2건은 Challenge 활성화 전 과제로 유지한다.

- 로컬 테스트 성공은 AWS/Identity/AI E2E 성공이 아니다. 실제 서버를 열었다고 보고하지 않는다.
- 별도 테스트 서버에도 `SPRING_PROFILES_ACTIVE=staging`, `APP_AUTH_MODE=jwt`를 사용한다. `test`/Legacy profile로 MEMBER 인증을 우회하지 않는다.
- `CHALLENGE_ENABLED=false`로 먼저 준비한다. 최초 ON 기동의 catalog 초기화가 KST Day 1을 영속 저장하며 뒤 기동 단계가 실패해도 자동으로 되돌리지 않는다. 활성 날짜와 테스트 콘텐츠 연속 일수부터 확인한다.
- `/actuator/health` 성공만으로 JWT·S3·AI 평가·Callback을 검증했다고 보지 않는다. OFF 상태에서는 Challenge 사용자 API/Callback이 차단되므로 실제 Challenge E2E는 격리 테스트 환경에서 승인된 ON 이후 수행한다.
- 현재 `.github/workflows/deploy-staging.yml`은 `main` push/수동 실행 시 기존 `tosunsaeng-learning-core-service`를 갱신한다. 테스트 서비스와 health URL이 고정된 별도 실행 경로를 승인받기 전 기존 workflow를 실행·변경하지 않는다.

### 0.2 준비값과 상태

| 영역 | 지금 준비할 내용 | 확인 상태 |
| --- | --- | --- |
| Identity | HTTPS 주소 접수, 정확한 issuer/JWKS URL·공개 kid·배포 commit·MEMBER 발급 결과 인계 | 사용자 배포 통보; 직접 연동 검증 전 |
| LC 인증 | `APP_AUTH_MODE=jwt`, `IDENTITY_AUDIENCE=tosunsaeng-learning-core`, 인계받은 `IDENTITY_ISSUER`/`IDENTITY_JWK_SET_URI` | 코드/설정명 확인, 환경 주입 미실행 |
| 테스트 격리 | LC HTTPS/callback URL, 전용 ECS service/task family, Mongo DB/계정과 S3 bucket, Redis 범위 | 실제 리소스·연결 확인 필요 |
| AI | API/worker 각각 준비, private HTTPS endpoint, 방향별 secret 참조, 테스트 Redis/임시 S3 | 이전 인계 기준, 실제 배포 미확인 |
| DB/콘텐츠 | replica-set, catalog 하루 3문항·BSON int, 6 collections/9 indexes/TTL 없음, Day 1 및 이후 날짜 콘텐츠 | 로컬 fixture 검증 완료, 실제 테스트 DB 미검증 |
| 권한/네트워크 | LC→S3 PUT/HEAD/GET, LC→AI HTTPS, AI→LC callback, AI worker 외부 egress | 실제 IAM/SG/TLS 미검증 |
| 출시 범위 | Billing saga/reconciliation·AttemptGroup writer/publisher·UserMerged 범위 밖 flag는 테스트에서 OFF 유지 | 실제 배포 flag 인계 필요 |

`MONGODB_DATABASE`는 명시적으로 테스트 DB를 지정하고 기본값 `to-teacher-app`을 그대로 사용하지 않는다. S3 key prefix 환경변수만으로 데이터가 격리된다고 가정하지 않는다. 기존 key 형식은 유지하고 실제 bucket/DB/queue 격리를 확인한다. 실제 secret·Access Token·Mongo URI 원문은 이 문서에 기재하지 않는다.

### 0.3 남은 사용자/담당자 확인

- Identity 담당: 배포한 서버의 공개 연결값·실제 TTL/claim·로그인/재발급 검증 결과. base URL을 JWT issuer/JWKS path로 자동 확정하지 않는다.
- 인프라 담당/사용자: LC 테스트 주소와 AI private HTTPS 경로, 사용할 테스트 데이터 리소스, service/task 이름·CPU/메모리·수량·비용, 최초 활성 KST 날짜.
- 프론트/AI 담당: 테스트 base URL·양방향 callback, 실제 휴대폰 녹음과 정상/무발화/실패 E2E 참여 준비.
- 새 Jira 이슈 생성이나 CI/CD 신설은 아직 실행하지 않았다. 관련 구현 이력은 TMI-126이다.

### 0.4 2026-09-22 로컬 검증 결과와 한계

| 검증 | 결과 |
| --- | --- |
| `./gradlew test --tests '*Challenge*' --tests '*Identity*' --tests '*Jwt*' --tests '*Auth*'` | 104 tests, 실패/오류/skip 0 |
| `./gradlew test` | 543 tests, 실패/오류/skip 0 |
| `node --test scripts/mongodb/challenge-10s-prepare.test.js` | 7 tests 통과; 실제 DB 접속 없음 |
| `./gradlew mongoIntegrationTest --tests '*ChallengeMongoIntegrationTest'` | 최초 Ryuk 초기화 실패: Docker API 1.32 거절, 최소 1.40 요구 |
| `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew mongoIntegrationTest --tests '*ChallengeMongoIntegrationTest'` | 격리 MongoDB에서 29 tests 통과, 실패/오류/skip 0 |

Docker API 호환 설정은 기존 rollout 문서에 기록된 값을 테스트 프로세스에만 적용했다. Testcontainers cleanup을 비활성화하거나 Docker 전역 설정/프로젝트 의존성을 변경하지 않았다. 이번 실행은 Challenge 통합 suite만 해당하며 전체 Mongo 통합 suite, 실제 AI 이미지 build, AWS 네트워크·권한·부하와 모바일 E2E는 실행하지 않았다. 전체 로컬 테스트는 `clean` 없이 실행했다.

근거: `src/main/resources/application.yml`, `AuthStartupValidator`, `ChallengeProperties`, `ChallengeStartupValidator`, `ChallengeCatalog`, `scripts/mongodb/challenge-10s-prepare.js`, `.github/workflows/deploy-staging.yml`, `docs/codex/TEN_SECOND_CHALLENGE_ROLLOUT.md`. 다음 절은 기존 상세 인계이며 실제 자원 존재·배포 완료 증빙은 아니다.

### 0.5 2026-09-23 실행 준비 요청 및 콘솔 inventory

- 사용자 요청 범위: LC 테스트 HTTPS/DB/음성 S3/catalog·index, Challenge AI API/worker·Redis/임시 S3, LC→AI 및 AI→LC 연결/방향별 인증정보 주입 준비.
- Chrome 접근은 미승인이었으나 이후 사용자가 명시적으로 요청한 인앱 Sandbox 페이지에서 직접 로그인했다. 해당 인앱 AWS 콘솔로 읽기 전용 조회했으며 Chrome 재접근·세션/credential 추출은 하지 않았다.
- CLI는 여전히 `NoCredentials`/profile 없음이다. 콘솔에서 계정 `889384901776`, 서울 `ap-northeast-2`를 확인했다. `tosunsaeng-staging-cluster`의 서비스는 기존 AI/Identity/LC와 Identity-test 총 4개이며 각각 task 1개가 실행 중이다. LC-test 및 Challenge AI API/worker 서비스는 해당 목록에 없다.
- Identity-test는 task definition `tosunsaeng-identity-test:3`, desired/running 1, deployment 성공, target healthy 1이다. 기존 `tosunsaeng-staging-alb`의 `tosunsaeng-identity-test-tg`에 컨테이너 8081로 연결된다. VPC `vpc-04068339482839ff1`, subnets `subnet-0fa638ca8effafa81`/`subnet-0dc3e0343bb98e3e3`, SG `sg-03c6bd60c4026a227`, public IP OFF를 확인했다. 이 SG의 권한 적합성이나 subnet route를 검증한 것은 아니다.
- 서울 ECR에는 `tosunsaeng-ai`, `tosunsaeng-ai-learning-worker`, `tosunsaeng-identity`, `tosunsaeng-learning-core`가 있다. AI-learning-worker의 이미지 7개는 2026-08-31 생성이며 `contract-v1`과 `staging` tag를 확인했다. 이름만으로 현재 `app-ai-learning` Challenge 배포본이라고 판단하지 않는다. 승인 commit과 digest 연결 증빙이 필요하다.
- 다음 확인은 LC 테스트 도메인/DNS 관리, 승인 AI 이미지, 테스트 DB/catalog 공급 경로, Redis/S3 격리와 실행 사양/비용이다. Access Key/Secret Key/실제 토큰을 채팅으로 받지 않는다. IAM/SG/public exposure 변경은 실행 시 확인하고 새 credential 입력은 사용자에게 넘긴다.
- 접근 뒤 순서: account/region·기존 cluster/VPC/subnet/SG/ALB/인증서 inventory → 테스트 도메인·데이터 격리·사양/비용 확정 → 승인된 테스트 리소스만 준비 → 비밀 저장소 참조로 설정 연결 → feature OFF 배포 → DB/catalog 검증과 승인된 Day1 ON → E2E. 기존 staging workflow는 재사용 실행하지 않는다.
- 2026-09-22 코드 리뷰의 S3 body 다운로드 전체 timeout 및 순차 dispatch에 따른 Callback timeout 처리 지연은 아직 미수정이다. 리소스 준비와 별개로 활성화 전 보완·회귀 검증이 필요하며, 이번 요청으로 수정 완료됐다고 간주하지 않는다.

## 1. 배포 대상

사용자 지정 AI 저장소는 `Too-Much-I/app-ai-learning`이다. 기존 모의고사 AI를 복제하거나 교체하지 않는다.

- 확인한 원격 main: `2391a944010f816016c9263e507a2850c5b5c07a`.
- 확인 자료: README, Dockerfile, docker-compose.yml, docs/ecs-deployment.md, docs/challenge-v1-review.md, app/config.py, app/api/server.py, app/jobs/worker.py.
- 실제 배포 시 승인된 commit을 고정하고 이미지 digest로 식별한다. 원격 main의 후속 변경을 무검토 배포하지 않는다.
- 일반 Dockerfile 기반 ECS를 사용한다. 레거시 Lambda/Function URL 배포 스크립트는 실행하지 않는다.

기존 `tosunsaeng-staging-cluster`의 운영 Identity/LC/AI는 유지한다. 아래 Identity-test는 존재를 확인했고 나머지는 신규 생성 후보다.

| 신규 서비스 후보 | 역할 |
| --- | --- |
| tosunsaeng-identity-test-service | MEMBER 발급용 별도 Identity |
| tosunsaeng-learning-core-test-service | 챌린지 API·S3 업로드·AI 연동 |
| tosunsaeng-ai-learning-test-api | app-ai-learning HTTP 접수 API |
| tosunsaeng-ai-learning-test-worker | app-ai-learning 비동기 STT·평가·콜백 |

AI API와 worker는 같은 이미지의 별도 task definition/service로 운영한다. 기존 AI의 worker 수나 command를 복사하지 않는다. 초기 수량·CPU·메모리는 비용 및 120초 결과 대기 E2E를 확인해 결정한다.

- API 기본 command: `python -m uvicorn app.main:app --host 0.0.0.0 --port 8000`.
- Worker command override: `python -m app.jobs.worker`.
- API 점검: `/health`, `/ready`. Worker는 HTTP API 서비스가 아니므로 같은 HTTP health check를 복사하지 않는다.
- API/worker 간 파일시스템 공유나 EFS는 필요 없다. API가 AI 전용 임시 S3에 음성을 저장하고 Redis에 참조를 넣으면 worker가 읽는다.

## 2. LC와 AI 설정 매핑

| Learning Core | app-ai-learning | 관계 |
| --- | --- | --- |
| CHALLENGE_AI_ENDPOINT | API HTTPS 주소 + `/v1/challenges/evaluations` | LC 호출 목적지 |
| CHALLENGE_AI_OUTBOUND_CREDENTIAL | LEARNING_CORE_TO_AI_CREDENTIAL | 동일한 요청 방향 secret |
| CHALLENGE_AI_CALLBACK_CREDENTIAL | LEARNING_RESULT_CALLBACK_TOKEN | 동일한 콜백 방향 secret |
| 테스트 LC 콜백 HTTPS URL | LEARNING_RESULT_CALLBACK_URL | `/internal/v1/challenges/grading/callback`까지 포함한 고정 전체 URL |

요청 방향과 콜백 방향의 secret은 서로 달라야 한다. 사용자 JWT/Firebase 토큰을 AI에 전달하지 않는다. 비밀값은 Secrets Manager 등 승인된 저장소에서 주입하며 Git/문서/로그에 기록하지 않는다.

AI 공통 준비값:

- `REDIS_URL`: 테스트 Redis 연결 및 인증/TLS 설정.
- `LEARNING_REDIS_QUEUE`, `LEARNING_REDIS_PROCESSING_QUEUE`, `LEARNING_REDIS_JOB_PREFIX`: 테스트 전용 namespace. Redis Cluster를 사용하면 관련 key의 공통 hash tag를 유지한다.
- `LEARNING_AUDIO_BUCKET`: AI API→worker 전달용 비공개 임시 버킷. LC 원본 업로드 버킷과 분리한다.
- `AWS_REGION=ap-northeast-2`.
- `OPENAI_API_KEY`: worker가 사용하는 제한된 테스트 credential.
- callback URL/token: worker 시작 전 필수 준비.

API와 worker는 같은 테스트 Redis namespace 및 AI 임시 버킷을 가리켜야 한다. 운영 Redis 재사용은 자동 승인하지 않는다. 별도 테스트 인스턴스를 우선 검토하며 공유하려면 namespace 외에 ACL·용량·내구성·장애 영향까지 확인한다.

## 3. 네트워크·권한·보존

- AI 평가는 private HTTPS 경로를 준비한다. 기존 운영 AI의 내부 HTTP alias를 재사용하지 않는다. LC staging/prod는 HTTPS를 요구하므로 AI 문서의 초기 HTTP 예시를 그대로 사용하지 않는다.
- 내부 ALB/TLS 등 구체적 경로와 DNS·인증서·SG는 별도 확정한다. 기존 인터넷 ALB 재사용만으로 private AI 요건을 충족한다고 가정하지 않는다.
- API task: AI 임시 버킷 `challenge/*`의 PutObject/DeleteObject. Worker: GetObject/DeleteObject. 실제 암호화 방식에 따라 필요한 KMS 권한도 검토한다.
- execution role의 이미지 pull/log/secret 접근과 task role의 S3 접근을 구분한다. static AWS credential은 추가하지 않는다.
- worker에서 OpenAI 및 테스트 LC 콜백으로 나갈 egress가 필요하다.
- AI 문서는 terminal 임시 음성 삭제와 orphan 2일 lifecycle을 권장한다. AI 임시 버킷에 한해 재시도/장애 복구와 보존 요구를 확인해 적용하고 LC 원본 버킷 정책에는 복사하지 않는다.
- `/ready` 성공은 Redis ping과 bucket/credential 설정 존재 확인이며 실제 S3 권한·OpenAI·worker 처리·콜백 성공의 증거가 아니다.

## 4. Identity 및 프론트

### 4.1 2026-09-21 사용자 확정 기준

| 항목 | 확정 기준 |
| --- | --- |
| Firebase | 기존 `to-teacher-firebase` 프로젝트 재사용, 새 프로젝트 생성 없음 |
| 인증 provider | Google 및 가상 테스트 전화번호. Apple/Kakao는 검증 제외하되 기존 설정을 임의로 끄지 않음 |
| Access Token TTL | `PT30M` |
| Refresh Token TTL | `P14D` |
| 재발급 응답 복구 | ON, 복구 기간 `PT2M` |
| Identity 격리 | 별도 테스트 DB/접근 계정/JWT 서명 키/issuer, 테스트 전용 전화번호 fingerprint 키 및 재발급 복구 암호화 키 |
| 제외 범위 | Guest 승격/병합, Billing 외부 연동. 범위 밖 event publisher는 테스트 환경에서 OFF |

위 값은 배포할 목표 설정이며 현재 서버의 유효 설정을 확인했다는 의미가 아니다. Billing 전송 제외와 가입 필수 내부 설정을 구분한다.

Firebase 사용자 및 인증 설정은 프로젝트 안에서 공유된다. 변경 전 실제 운영 Firebase Auth 의존성을 확인한다. 별도 Identity DB/서명 키만으로 Firebase까지 격리되는 것은 아니다. 개발 전용 Google 계정/가상 번호를 사용하고, 사용자 삭제·provider 변경·미완료 가입 자동 정리 등 Firebase 사용자에 영향을 주는 작업의 설정/대상 범위를 사전 점검한다. 이번 검증 밖의 해당 작업은 안전성이 확인되기 전 테스트에서 활성화하지 않으며 운영 설정을 임의로 변경하지 않는다. 공유 Firebase ID Token은 프로젝트만으로 운영/테스트 Identity를 구분하지 못하므로 테스트 앱의 Identity base URL도 고정·검증한다.

### 4.2 유지할 계약과 검증

- MEMBER 제한을 유지하고 테스트 게스트 예외를 추가하지 않는다.
- 신규 가입: Google 로그인→Identity exchange→ENROLLMENT_REQUIRED/enrollmentId→같은 Firebase UID에 전화번호 link→ID Token 강제 갱신→signup.
- signup은 enrollmentId, 강제 갱신한 firebaseIdToken, nickname, 개인정보/이용약관 동의 및 각각의 실제 정책 버전을 사용한다. 기존 회원은 exchange로 로그인한다.
- 재발급 복구 ON에 맞춰 프론트 single-flight, 동일 논리 요청의 lowercase UUID v4 Idempotency-Key 유지, Access/Refresh Token 동시 교체 및 절대 만료 헤더 처리를 적용한다. 재시도 수신 시점에 expiresIn을 다시 더하지 않는다.
- 테스트 issuer·JWKS·서명 키와 DB/session을 운영에서 분리하고 양방향 토큰 거절을 검증한다.
- 사용자 JWT의 표준 aud 배열은 `["tosunsaeng-learning-core", "tosunsaeng-billing"]`을 유지한다. LC는 `tosunsaeng-learning-core` 포함을 검증하고 `account_type=MEMBER`를 요구한다.
- 테스트 LC의 Mongo DB/계정·catalog/index·KST Day1과 음성 버킷을 독립 준비한다. Firebase/Identity 실제 활성화 상태는 별도 인계받는다.

### 4.3 Identity 미확정값 및 인계

- 2026-09-23 담당 인계: HTTPS base URL과 배포 설정의 issuer는 `https://identity-test.to-teacher.com`이며 issuer 끝에 `/`가 없다. 실제 발급 토큰의 iss 검증은 아직이다.
- JWKS URL은 `https://identity-test.to-teacher.com/.well-known/jwks.json`, 공개 kid는 `tosunsaeng-identity-test-rsa-1`, 알고리즘은 RS256이다. 담당자는 공개 URL 정상 응답을 확인했으며 이 작업에서 독립 재검증하지 않았다.
- 배포 commit은 `88ff5bedc1ee661ccd38a8a1d2c4dbf3b9f03f2d`, ECS Task Definition은 `tosunsaeng-identity-test:3`으로 인계받았다.
- 담당 검증 범위는 health 200/UP, JWKS 응답, Mongo transaction 지원 로그와 기존 자동 테스트 958개 통과다. 테스트 서버에서의 가입·로그인·재발급 성공 및 실제 MEMBER token 검증은 미완료다.
- 발급 코드/기존 테스트의 claim 계약은 UUID sub, account_type=MEMBER, aud 배열에 tosunsaeng-learning-core/tosunsaeng-billing이다. 최초 발급·재발급 token 모두 실제 환경에서 검증해야 한다. 실제 token 원문을 이 문서에 보관하지 않는다.
- 실제 테스트 DB와 보안 키 주입 방식/secret 참조. 비밀값은 문서에 기록하지 않는다.
- 실제 약관 버전, 활성 기능·의존성 및 실제 TTL은 추가 확인한다.
- 배포 후 주소/issuer/JWKS/kid/실제 TTL/commit/활성 기능 및 가입·로그인·재발급 검증 결과만 공유한다.

LC 인증 설정에 반영할 인계값(환경에 실제 적용하지 않음):

```text
APP_AUTH_MODE=jwt
IDENTITY_ISSUER=https://identity-test.to-teacher.com
IDENTITY_JWK_SET_URI=https://identity-test.to-teacher.com/.well-known/jwks.json
IDENTITY_AUDIENCE=tosunsaeng-learning-core
```

kid는 LC 환경변수에 고정하지 않고 JWKS 기반 RS256 검증/키 선택을 유지한다.

## 5. 실행 전 미확정값과 순서

CloudShell 실조회 후속: 사용자가 새 환경 실행을 완료했고 `lc-test-redis-check`의 `INFO keyspace` 출력은 db1 keys=1127/expires=1127 한 줄이었다. 조회 시 다른 DB에는 키가 없다는 뜻이지 consumer의 미사용/미예약 증거는 아니다. `INFO memory`는 maxmemory=402653184(384MiB), maxmemory_policy=volatile-lru를 확인했다. DB2는 잠정 후보일 뿐 확정하지 않는다. VPC 셸에서 기존 LC/AI의 task definition metadata만 조회하도록 제한한 ECS describe-services는 응답이 없어 설정 대조를 완료하지 못했다. Ctrl+C 중단을 시도했으나 프롬프트 복귀가 확인되지 않았고 이후 입력한 memory 요약 명령의 실행 여부도 미확정이다. shell에는 쓰기 명령을 보내지 않았다. 캐시 접속은 검증됐지만 consumer DB 번호 대조와 테스트 SG 접근·점검용 SG 정리가 남으며 Redis template은 미확정 상태다.

CloudShell 승인 실행(2026-09-23): 사용자의 명시적 승인 후 연결을 제출했다. AWS 진행 상세는 CloudShell용 `elasticache-cloudshell-tosunsaeng-staging-redis` (`sg-0f83bb7d8c5b6b124`), 캐시용 `cloudshell-elasticache-tosunsaeng-staging-redis` (`sg-0f42223f236bb40ee`) 생성, 기본 egress 제거, 연결 규칙 설정 및 캐시 SG 연결 성공100%를 보고했다. 마지막 상세 상태는 Modifying이며 Available 복귀/실제 규칙 source 대조는 후속이다. 기존 SG 자체는 변경하지 않는 자동 연결 흐름이다. 새 VPC CloudShell 환경 폼에 이름 `lc-test-redis-check`와 `valkey-cli -h tosunsaeng-staging-redis.pzyca7.ng.0001.apn2.cache.amazonaws.com -p 6379 INFO keyspace`를 준비했다. 브라우저 iframe 조작 오류로 생성 및 실행 버튼은 누르지 못했으며 사용자 handoff 대기다. VPC 환경 생성 및 캐시 쿼리 성공을 주장하지 않는다. 점검 완료 후 새 접근 연결/SG 정리를 확인해야 하며 아직 제거하지 않았다. DB 번호와 LC Redis 환경값은 미확정으로 유지한다.

재사용 후속 관측: CloudWatch 확대 보기의 최근3시간·5분 평균 그래프에서 DB 메모리 사용률 약5.51~5.60%, EngineCPUUtilization 약0.70~0.73%를 확인했다. 이전 그래프 로딩 한계는 이 두 지표에 한해 해소됐다. 장기 최대 부하·다른 지표·추가 테스트 부하는 아직 검증하지 않았다. DB 점유 조회를 위한 캐시에 연결→CloudShell 모달은 기존 SG 미수정, CloudShell용 새 SG와 TCP6379 inbound 추가를 안내했다. 이는 권한 확대이므로 연결 최종 제출 전 사용자 승인 대기이며 조회 명령은 아직 실행하지 않았다. 실제 SG source/연결 범위와 점검 후 접근 제거도 확인해야 한다. DB 번호를 아직 배정하지 않는다.

기존 Valkey 재사용 검증(2026-09-23, read-only): `tosunsaeng-staging-redis`는 cluster mode disabled·단일 cache.t4g.micro·default.valkey9의 `databases=16`이므로 논리 DB 0~15 분리 구성이 가능하다. 기본 endpoint는 `tosunsaeng-staging-redis.pzyca7.ng.0001.apn2.cache.amazonaws.com:6379`, VPC는 `vpc-04068339482839ff1`이다. 캐시 SG `sg-0f8528393d749b3a9`는 TCP6379를 AI SG `sg-0aac51a28efa84789`와 기존 LC SG `sg-00bc88844e6f59f15`에서만 허용한다. 새 LC 테스트 SG의 접근은 별도 승인/검증이 필요하며 기존 SG를 무조건 재사용하지 않는다. 전송 암호화 OFF, 저장 암호화 ON, 사용자 그룹 없음, Multi-AZ/자동 failover OFF다. CloudWatch 그래프가 로딩 상태로 지속되어 실제 부하·메모리 여유는 미확인이다. 미사용 DB 번호도 아직 확인하지 않았다. 데이터 값을 읽지 않는 `INFO keyspace`/`INFO memory`/`INFO stats` 및 기존 consumer 설정 대조를 승인된 VPC 접속 경로에서 수행한 뒤 번호를 예약해야 한다(빈 DB만으로 미사용 확정 금지). DB 분리는 접근권한·부하·장애 격리가 아니며 기존 캐시 설정은 변경하지 않았다. 배포 초안의 Redis placeholder를 유지한다.

IAM 생성 완료(2026-09-23): 사용자 승인으로 `tosunsaeng-learning-core-test-execution-role`과 `tosunsaeng-learning-core-test-task-role`을 생성했고 각각 `LearningCoreTestExecution`, `LearningCoreTestAudio` 인라인 정책 1개 연결과 ARN을 확인했다. `deployment/learning-core-test.iam-proposal.json`의 정책 범위로 적용했고 ECS template 역할 ARN도 반영했다. 아래 IAM 승인 대기 기록은 이전 경과다. 기존 역할/서비스는 변경하지 않았다. ECS 실제 역할 사용 및 Secret/S3 연결은 아직 미검증이며 로그 그룹 사전 생성도 남는다.

IAM/Redis 후속: `deployment/learning-core-test.iam-proposal.json`에 아직 적용하지 않은 두 전용 역할 정책을 준비했다. 기존 역할은 수정하지 않는다. 실행 역할의 ECR auth token만 Resource=*가 필요하며 image pull은 LC repository로 제한한다. 로그 그룹은 사전 생성이 필요하며 역할에 CreateLogGroup은 부여하지 않는다. 앱 역할은 테스트 버킷 전체 GetObject(문제/모범 음성의 기존 key 지원), temp/* PutObject만 허용하고 List/Delete는 제외한다. Secret 값은 기록하지 않았다. 실제 IAM 부여는 사용자 승인 대기다. 서울 Valkey에는 `tosunsaeng-staging-redis`(Available, cache.t4g.micro, 표시 엔진 9.1.0)가 존재한다. 기존 staging 캐시는 그대로 유지하며 테스트 Redis 선택/비용은 별도 확인한다.

최신 상태: 사용자 명시적 승인 후 서울에 `tosunsaeng-test-audio`를 생성했고 콘솔 성공 메시지와 객체 0개를 확인했다. ACL 비활성·퍼블릭 전체 차단·SSE-S3·versioning 비활성으로 생성했다. ECS 초안 `AWS_S3_BUCKET_NAME`에 반영했다. 아래 생성 대기 기록은 이전 경과이며 이제 해소됐다. IAM 접근권한/실제 업로드·다운로드 검증, lifecycle와 LC 배포는 아직 수행하지 않았다.

사용자 후속 이름 선택: 아래 최초 후보 대신 `tosunsaeng-test-audio`를 생성 폼에 입력했다. 계정 ID 접미사는 필수가 아니며 생성 제출 시 글로벌 이름 가용성을 확인해야 한다. 아직 생성하지 않았고 task template도 미확정 상태를 유지한다.

2026-09-23 후속 inventory: S3 전체 목록의 5개 기존 버킷에는 LC 테스트 전용 버킷이 없었다. 서울 ElastiCache Redis OSS 목록은 0개이며 Valkey 및 자체 호스팅 Redis 유무는 미확인이다. 기존 리소스는 변경하지 않았다. 새 S3 이름 `tosunsaeng-learning-core-test-audio-889384901776`을 생성 폼에 준비했으며 서울·ACL 비활성·퍼블릭 전체 차단·SSE-S3·versioning 비활성 기본값으로 생성 승인 대기 중이다. 아직 버킷 생성, IAM 권한 부여, Redis 생성 및 ECS 배포를 수행하지 않았다. 사용량에 따른 S3 비용을 안내하고 승인 후 생성 성공을 확인해야 ECS 초안에 실제 버킷을 반영한다. lifecycle/자동 삭제 정책은 추가하지 않는다.

1. 서비스/ECR/task family 이름, CPU·메모리·초기 task 수와 비용 승인.
2. 테스트 LC callback URL 및 private AI HTTPS 경로, DNS/TLS/SG와 egress 확정.
3. 테스트 Redis 및 AI 임시 S3, LC DB/S3, secret ARN·task/execution role 준비.
4. 테스트 Identity 발급 검증 및 LC 설정 연결. Challenge catalog/index 준비.
5. AI 이미지 build/push, worker와 API 신규 배포. 기존 운영 서비스는 변경하지 않음.
6. 준비 상태 확인 뒤 실제 S3→Redis→worker→OpenAI→LC 콜백 E2E. 정상/무발화/실패/중복/늦은 콜백 및 120초 대기·최대 3 generation 검증.
7. 테스트 대상만 갱신하는 CI/CD 추가. 조회 시 AI 저장소에는 `.github/workflows` 파일이 없었으며 기존 운영 AI workflow를 그대로 사용하지 않음.

테스트 주소·리소스·secret이 준비되지 않았으므로 이 문서는 배포 완료 증빙이 아니다. AI 테스트·이미지 빌드·AWS E2E도 이번 저장소 검토에서 실행하지 않았다.
