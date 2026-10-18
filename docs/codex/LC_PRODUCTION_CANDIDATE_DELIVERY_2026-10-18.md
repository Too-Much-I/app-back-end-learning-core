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
