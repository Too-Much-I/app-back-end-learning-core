# LC 운영 후보 패키지 — 로컬 준비

## 1. 결론

1. TMI-116/TMI-217/TMI-220: 운영 중단 없이 현재 LC 작업트리를 빌드·검증하는 준비 작업이다.
2. HEAD `9fb8f718eb7402ab0a2d7c49b981699cdf856423`에 공존/production readiness가 있고, 미커밋 Azure 삭제 필드 보완도 후보에 포함한다. HEAD만으로 이 후보를 재현할 수 없다.
3. 단위 테스트 총1399, 실패0/오류0/기존skip1; 격리 Mongo337·배포 Node88 통과. JAR 및 linux/amd64 로컬 이미지 빌드 성공.
4. 이 문서는 배포 가능한 ECS 등록 JSON이 아니다. 봉인값과 원격 이미지 digest가 없으므로 운영 실행하지 않는다.
5. commit/push·이미지 원격 전달·태스크 등록·실DB/봉인/배포·기능 ON은 수행하지 않는다.

## 2. 반드시 읽을 내용

구앱 무료 생성도 새 legacy runtime을 필요로 한다. 새 이미지만 기존 prod8 설정에 덮어쓰거나, OFF 준비 prod9를 그대로 배포하지 않는다. 운영 태스크의 기존 command/entryPoint와 SPRING_APPLICATION_JSON은 새 이미지 설정보다 우선할 수 있다. 기존 비밀 주입/기동 전처리를 확인하지 않고 override를 통째로 제거하지 않는다.

소스 집합 지문은 git tracked+untracked(ignored 제외) 중 src/, gradle/, gradlew, settings.gradle, build.gradle, Dockerfile, .dockerignore, access-events-entrypoint.sh를 선택하고 모든 .DS_Store를 제외했다. 경로 정렬 후 UTF-8 `경로 + NUL + 파일SHA256 + LF`를 SHA256으로 집계한다. 테스트 포함517파일, `28e77ce237565b3ee0938e4bf42845ffa8e288097820874cad0d18de11bfbe81`. 이것은 Git commit/원격 image digest가 아니다.

로컬 bootJar: `build/libs/app-back-end-learning-core-0.0.1-SNAPSHOT.jar`, SHA256 `45288e57458245030d7d75a3a892b8a7ab500a6469a863288fc354b073ae6f8f`. Docker는 컨테이너 안에서 별도 빌드하므로 JAR bytes가 동일하다고 가정하지 않는다.

## 3. 적용 전 확정할 사항

- 최종 소스 커밋/원격 linux/amd64 digest. 이번 후보는 dirty 포함 로컬 검토본이다.
- 실제 봉인 revision/hash/count, 최신 owner/index 검사 및 실행 승인.
- 생성 제한 시간·검증 계정, 기존 제출/Callback/worker 종료 및 복구 리허설.
- 기능별 publisher/reconciler/phone/삭제 조합: 기존 OFF값을 무조건 ON으로 치환하지 않는다. 공동 준비와 의존성 검증 뒤 확정한다.

## 4. 전환 설정 변경 명세 — 미적용

| 항목 | 후보 요구사항 |
| --- | --- |
| 대상 | prod 서비스/실행 역할 유지, 기존8080/TG 유지 및 승인된8444/TG 추가 |
| 이미지 | 위 소스 보완을 포함한 linux/amd64 immutable digest, 현재 구 digest 재사용 금지 |
| 시작 | 이미지 기본 `sh /app/access-events-entrypoint.sh`, 인자 `java -jar /app/app.jar`; 기존 태스크 전처리는 별도 보존·연결 검증 |
| 환경 | SPRING_PROFILES_ACTIVE=prod, APP_DEPLOYMENT_ENVIRONMENT=prod |
| 공통 기동 gate | BILLING_PAID_EXAM_ENABLED, BILLING_CREATION_SAGA_ENABLED, BILLING_WORKLOAD_JWT_SENDER_ENABLED, BILLING_ACCESS_EVENTS_RECEIVER_ENABLED, BILLING_PAID_EXAM_PRODUCTION_ACTIVATION_APPROVED 및 USER_MERGED_WRITER_ENABLED=true가 필요(실제 적용 승인 아님) |
| 구앱 | BILLING_LEGACY_EXAM_CREATE_ENABLED=true, BILLING_PAID_EXAM_TEST_ACTIVATION_APPROVED=false |
| trust | BILLING_WORKLOAD_JWT_ENVIRONMENT, BILLING_ACCESS_EVENTS_ENVIRONMENT, BILLING_PAID_EXAM_LEGACY_ENVIRONMENT 모두 production |
| 연결 | BILLING_BASE_URL=https://billing-workload-prod.to-teacher.com, BILLING_ACCESS_EVENTS_CONSUMER_SCOPE_ID=tosunsaeng-billing-prod |
| 봉인 | BILLING_PAID_EXAM_LEGACY_DATABASE=to-teacher-app; REVISION/SHA256/COUNT는 최종 봉인 readback 값만 사용 |
| TLS | BILLING_ACCESS_EVENTS_PORT=8444, alias=lc-prod-access-events, 준비된 prod Secret 참조 유지; 실제 키는 패키지에 포함하지 않음 |
| 종료 | 현재 stopTimeout30초/TG drain300초는 서로 다른 상한. 시간 경과만으로 writer/Callback 완료 판정 금지 |

SPRING_APPLICATION_JSON의 동일 속성이 환경변수를 가릴 수 있으므로 등록 전 유효 설정을 대조한다. 사용자 인증·DB·Redis·S3·AI·Identity 설정은 기존 운영값을 보존한다. 공개 API/DTO 및 AI user_id=examId 계약은 불변이다.

## 5. 검증 범위

후속 [production JWT/TLS 격리 검사](LC_CANDIDATE_PRODUCTION_TLS_REHEARSAL_2026-10-18.md): 가짜 sealed manifest/owner/index·임시 키로 실제 prod 프로필/paid ON 기동, HTTPS 인증서 검증·401/403/404·정상204 중복 수신 및 문서1개 확인 시나리오1개 통과. 아래 최초 smoke의 한계를 보완하지만 운영 trust/ALB/ECS·Billing E2E·진행중 요청 배출까지 입증하지 않는다.

후속 [실제 프로세스 리허설](LC_CANDIDATE_PROCESS_REHEARSAL_2026-10-18.md): 이 후보 이미지의 local 앱 기동→SIGTERM→새 컨테이너 기동→늦은 Azure/Summary Callback 멱등 완료 시나리오1개 통과. 아래 최초 smoke3개와 별도다. production paid ON·TLS·in-flight 요청 배출의 증거로 확대하지 않는다.

### 로컬 이미지 및 실제 기동 명령 확인

- 로컬 tag `lc-prod-candidate:20261018-amd64-local`, image inspect ID `sha256:c84d9216a2f9aeb9eb6f626fb37cfb4460f0b907056baa7284177c8106154cac`, architecture amd64. 원격 ECR digest가 아니며 push하지 않았다.
- Docker manifest `sha256:cd444c770890c38ec85db1314d5dc5150e468c416f4d7b42dccc7cb1bea8d100`; image config `sha256:d9e24104b24ffced4ec6b9a052d0f14b755fad38c8ce4f0d32ecc2d4de1a4c45`.
- 최초 platform 미지정 빌드는 완성 전 중단했고, 실제 workflow와 같은 linux/amd64로 다시 빌드했다. 기존 이미지·컨테이너는 삭제하지 않았다.
- 네트워크 없는 일회성 컨테이너에서 OFF Java21 실행 및 ON 가짜bytes TLS 파일 생성/600권한/base64 환경 제거를 각각 확인했다. 컨테이너는 --rm으로 정리됐다. 실제 Java 앱 ON 기동/인증서 검증은 아니다.
- AWS prod9의 entryPoint는 `/bin/sh -c`, command1개이며 TLS 스크립트 호출이 없다. prod8과 동일한 명령이며 마지막 `exec java -jar /app/app.jar`가 정확히1회 있고 앞에 전처리가 존재한다. 명령 원문·비밀 JSON은 출력/저장하지 않았다.
- 준비 도구 `scripts/deployment/prepare-production-startup.cjs`는 위 모양만 수락하여 prefix와 공백/기타 설정을 보존하고 마지막 exec만 `exec sh /app/access-events-entrypoint.sh java -jar /app/app.jar`로 바꾸는 순수 함수다. 원본 불변·변형/중복/재적용 거절8개 통과. AWS 호출·파일 저장·자동 적용 기능은 없다. 실제 태스크 적용 시 현재 명령을 다시 대조해야 한다.
- 따라서 기존 명령을 그대로 쓰면 새 TLS entrypoint를 우회하는 문제가 확인됐다. 위 변경은 아직 AWS에 적용하지 않았다.
- 가짜 prefix 환경변수가 TLS 스크립트를 거쳐 자식에 전달되는 컨테이너 검사도 통과했다. 이미지 검사는 합계3개이며 앱 전체 기동 검사는 아니다.
- Mongo 최초 UP-TO-DATE 결과를 이번 실행 증거로 사용하지 않았다. 강제 재실행 때 Docker API 호환 오류로19개 클래스가 초기화 실패했고 소켓 지정만으로 해결되지 않았다. 기존 기록대로 테스트 프로세스에만 `JAVA_TOOL_OPTIONS=-Dapi.version=1.44`를 지정한 강제 실행에서337개 실패/오류/skip0을 확인했다. 영구 Docker/프로젝트 설정 변경 없음.

TLS shell 테스트는 가짜 bytes를 사용한 파일 주입·권한·실패 경계 검사이며 실제 Java PKCS12 검증/운영 TLS 통과가 아니다. 실제 앱 ON 기동은 sealed runtime/owner/index와 trust 조건을 갖춘 격리 환경에서 별도 확인한다. 운영 DB를 후보 기동 테스트 대상으로 사용하지 않는다.

## 6. 관련 자료

- [전환 실행안](LC_PRODUCTION_CUTOVER_RUNBOOK_2026-10-18.md)
- [운영 readiness](LC_PRODUCTION_ACTIVATION_PREPARATION.md)
- [연결 인계](LC_PROD_BILLING_CONNECTION_HANDOFF_2026-10-17.md)
- [Azure 보완 전달](LC_AZURE_TEST_DELIVERY_2026-10-17.md)
