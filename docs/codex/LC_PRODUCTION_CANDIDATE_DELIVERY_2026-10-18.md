# LC 운영 후보 전달 — 서비스 배포 제외

## 결론

- TMI-116/TMI-217/TMI-220. 사용자 승인 범위는 LC 후보 commit/push·ECR 이미지 전달이다. main/develop 머지·workflow 실행·서비스 배포·봉인·기능 ON은 제외한다.
- 기존 HEAD9fb8f71의 readiness/공존과 Azure 삭제 필드 보완을 포함한다. Azure 보완은 develop의 별도 PR49에도 전달됐지만 현재 후보 부모에는 없어 이번 커밋에 포함한다.
- 사용자 DS_Store·기존 test 준비 자료·공유 WORKLOG/CURRENT_STATE 전체 이력은 커밋에서 제외한다. 기존 작업파일은 삭제/되돌리지 않는다.
- 후보 이미지 exact ID는 `sha256:c84d9216a2f9aeb9eb6f626fb37cfb4460f0b907056baa7284177c8106154cac`, amd64다. 태그 image inspect 실패 뒤 image ID inspect로 존재를 확인했다.
- 원격 전달 성공 여부·digest는 후속 검증 결과로 별도 기록한다. 이 문서 작성 자체는 ECR 전달/배포 완료가 아니다.

## 소스와 검사 근거

빌드 당시517파일의 경로+SHA256 집합을 재계산해 `28e77ce237565b3ee0938e4bf42845ffa8e288097820874cad0d18de11bfbe81` 일치를 확인했다. 집합 정의는 후보 패키지와 같으며 이후 추가된 CandidateProcessRestartTest/CandidateProductionTlsTest2개만 제외한다. 이 두 테스트는 제품 실행 코드를 바꾸지 않으며 기존 이미지에 대한 opt-in 리허설이다. Git commit에서 새로 빌드한 동일 bytes 이미지라고 주장하지 않는다.

이번 전달 직전 단위1399/실패0/오류0/기존skip1 및 시작명령·봉인·삭제 migration Node42/실패0 통과. 앞선 Mongo337 및 실제 후보 재시작/TLS 시나리오 각1개 결과는 과거 검증이며 이번 전체 Mongo 재실행으로 표시하지 않는다.

## 전달 구성

- Azure 삭제 제품3파일·Mongo/Node 검증 및 migration 수정.
- Summary 재시작 모델/manifest digest 검증과 실제 후보 프로세스/TLS opt-in 테스트.
- 순수 시작명령 변환·운영 봉인 도구 및 검증. 실DB 실행은 하지 않는다.
- 후보 패키지/봉인/배출/리허설/전환 실행 문서. 문서의 과거 AWS 관측은 실시간 현재값이 아니다.

## 운영 전에 남은 조건

실제 봉인값·최종 task 설정/기존 전처리 보존·양측 연결·진행 요청 배출·검증 계정과 업무 영향 범위를 확인해야 한다. 구앱 사용 가능이나 ECR 업로드만으로 신앱 유료 경로 공동 검증이 완료되지 않는다. 새 쓰기 후 구버전 자동 rollback 금지. 운영 서버는 이번 전달 중에도 유지한다.

## 전달 시도 결과

- 후보 소스 커밋 `3d02300`, 브랜치 `codex/lc-production-candidate-20261018` 원격 push 성공. main/develop 머지·PR 생성·workflow dispatch 없음.
- 기존 검증 이미지에 로컬 태그 `889384901776.dkr.ecr.ap-northeast-2.amazonaws.com/tosunsaeng-learning-core:prod-candidate-3d02300`을 추가했다. 기존 태그/이미지는 보존했다.
- ECR push는 blob HEAD에서 `403 Forbidden`으로 실패했다. 원격 태그 생성/최종 digest 검증은 완료되지 않았으며 ECR 전달 성공으로 보고하지 않는다.
- 로컬 AWS CLI는 NoCredentials 상태다. Docker 기존 인증의 만료/저장소 권한 중 원인을 확정하지 않았으며 IAM 변경이나 Secret/Token 출력으로 우회하지 않았다. 승인된 계정의 정상 ECR 인증 연결 후 같은 후보 전달을 재개해야 한다.
- 운영 서비스/task/ALB/기능·DB/봉인은 변경하지 않았다.

## CloudShell 재전달 완료

- 사용자 승인으로 기존 LC 전용 CloudShell 세션에서 전달했다. 기존 로그인 계정과 LC 저장소만 사용했고 IAM 권한을 추가하지 않았다. 위 로컬403은 이전 실패 기록이며 최종 전달은 아래와 같이 완료됐다.
- 로컬 Docker save 파일158,905,856bytes를4조각으로 순차 업로드했다. 홈 여유98MiB 때문에 전용 `/tmp/lc-ecr-3d02300-etwbdur_`에 옮겨 결합했으며 SHA256 `f7ffa931c8482fb3a089fe5bd8ced5a4ad27529a2c24c928ce1703eee3cc6bca`가 원본과 일치한다.
- CloudShell Docker load exit0, amd64 및 config ID `sha256:d9e24104b24ffced4ec6b9a052d0f14b755fad38c8ce4f0d32ecc2d4de1a4c45`, TLS entrypoint를 확인했다. Docker 저장 형식 차이로 로컬 index ID와 config ID를 혼동하지 않는다.
- ECR 태그 `prod-candidate-3d02300`은 전달 직전 ImageNotFound였고 신규 push exit0. 최종 URI: `889384901776.dkr.ecr.ap-northeast-2.amazonaws.com/tosunsaeng-learning-core@sha256:d4e51241021b1982167682ea8f4e5257872e53f12d1a186bb75e3f945300b499`.
- ECR batch_get_image로 위 digest와 config hash 일치·9개 layer를 확인했다. save/load/push 과정에서 registry manifest digest가 기존 로컬 index/manifest와 달라질 수 있으며 같은 digest라고 주장하지 않는다.
- 최종 ECS 읽기 확인: LC prod:8, running1/pending0 유지. 태스크 등록/서비스 업데이트/배포/봉인/DB 변경 없음. 새 후보를 실행하지 않았다.
- 전용 임시 Docker config의 ECR 인증은 logout exit0으로 제거했다. 인증값은 채팅/보고서에 출력하지 않았다. 공유 Docker 인증은 수정하지 않았다.
- 사용자가 요청한 CloudShell의 오래된 미사용 LC 이미지는 전달 전 목록에 없었다(mongo:8.0만 존재). 삭제0건이며 Mongo/다른 작업 파일 보존. 새로 전달한 후보 이미지와 전송 임시 파일은 남겨뒀고 후속 정리 대상이다. 로컬 원본은 `/private/tmp/lc-ecr-transfer.AlzZR1`에 보존했다.
