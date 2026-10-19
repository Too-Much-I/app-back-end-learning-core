# LC 운영 배포 설정 초안 대조

- 날짜: 2026-10-18. TMI-116/TMI-217/TMI-220.
- 범위: 읽기 전용 AWS 조회, 로컬 변경 명세 작성, 기동 변환 검사. 실제 배포 설정 변경 승인/완료가 아니다.
- 산출물: [등록 불가 설정 초안](LC_PRODUCTION_TASK_DRAFT_2026-10-18.json). ECS register-task-definition 입력이 아닌 변경 명세다. Secret 값·원본 전체 태스크는 저장하지 않았다.

## 최신 관측

전용 CloudShell 세션에서 ECS describe/list로 LC prod:8 실행1/대기0, 서비스8080 연결만 확인했다. 준비 prod:9도8080만 있다. Billing prod:5 실행1/대기0를 확인했으나 실제 Billing 수신 기능·TLS·업무 readiness는 확인하지 않았다. 이번에 ALB/DB를 다시 조회하지 않았다.

prod8/9의 entryPoint와 command는 동일하다. 기존 전처리를 보존하고 마지막 `exec java -jar /app/app.jar`만 TLS entrypoint 호출로 바꾸는 모양을 메모리에서 검증했다. 원본 task는 불변이다. prod9의 기존/추가 Secret 참조8개를 유지하며 신규 비밀값을 읽지 않았다.

SPRING_APPLICATION_JSON은 prod8/9 동일하고 app.notifications 속성6개뿐이다. 이번 변경 대상 Billing/owner/deployment 속성을 가리는 항목은 없다. 알림 설정은 보존한다. command prefix 원문과 비밀 설정을 보고서에 복제하지 않는다.

## 준비된 변경

- 전달 검증된 immutable image digest 사용, 기존8080 유지 및8444 추가 명세.
- prod 환경과 production wire 환경 구분, 기존 prod9 공개키/Secret 참조 재사용.
- paid/saga/signer/receiver/production 승인 및 구앱 무료 생성 gate의 후보값 명시. 실제 서버는 변경하지 않았다.
- 봉인 revision/hash/count는 아예 미기입. 임시0건/가짜hash로 채우지 않는다.
- 기존 IAM/리소스/로그/네트워크/DB·Redis·S3·AI·Identity·알림 설정 보존.

## 아직 배포를 막는 조건

1. 현재 reconciliation, attempt-group writer/publisher, phone continuation, learning-record deletion 관련 기능과 Billing deletion sender는 OFF다. 기본 기동 gate만 켠 초안을 전체 유료 운영 준비 완료로 간주하지 않는다. 각 조합·복구 alert 한도·수신측 준비·index를 확인하고 최종 변경표를 확정해야 한다.
2. Billing prod5가 실행 중이라는 사실은 새 workload 후보·내부 수신·환불 발신이 준비됐다는 증거가 아니다. 양측 실행 설정과 연결 검증 순서를 확인해야 한다.
3. 생성 제한 및 기존 writer 배출을 확인한 후 최종 inventory/봉인 readback과 owner/index 검사를 한다. 이번에는 생성 제한·배출·봉인을 하지 않았다.
4. 명시 전환 승인 후 task 등록/배포·8444 연결 및 인증/TLS·앱 검증을 진행한다. 구앱 검증 가능과 v2 검증 완료를 구분하며, 새 쓰기 이후 구 이미지 자동 rollback은 금지한다.

따라서 이번 산출물은 **설정 변경 초안과 현재 상태 대조 완료**이며, **봉인값만 넣으면 되는 최종 배포본은 아니다**. AWS에 새 task를 등록하거나 기능을 ON하지 않았다.

## 검증

- prepare-production-startup.test.js:8개 통과.
- 초안 JSON 파싱 및 봉인값 미기입 검사 통과.
- 실제 prod 명령의 shape·전처리 보존 메모리 검사 통과. 실제 새 앱 기동 검사는 아니다.
- 제품 코드 변경 없음으로 Gradle 미실행. 기존 테스트/fixture 원본 보존.
- commit/push·서비스 변경·봉인·DB·IAM·ALB 변경 없음.
