# LC 후보 실제 프로세스 재시작 리허설

## 1. 결론

1. TMI-116/TMI-217/TMI-220: 기존 모의 scheduler 검사와 별개로 실제 후보 이미지의 Spring 앱을 시작·종료·재시작하는 검사를 추가했다.
2. 검증 대상은 로컬 후보 image ID `sha256:c84d9216a2f9aeb9eb6f626fb37cfb4460f0b907056baa7284177c8106154cac`다. 다른 tag 대상이면 실패한다.
3. local 프로필·신규 가짜 Mongo/Redis만 사용한다. 운영 Secret/DB/사용자·AWS 설정은 사용하지 않는다.
4. 외부 송신이 차단된 Docker internal network에서 공개 포트 없이 컨테이너 내부 HTTP로 검사한다.
5. 이 검사는 production paid ON·JWT/TLS·ECS/ALB drain·진행 중 제출 경합까지 검증했다는 뜻이 아니다.

## 2. 실제 검사 범위

`CandidateProcessRestartTest`의 단일 시나리오에 다음 검증을 포함한다.

- 임시 replica set 및 Redis와 후보 앱 기동, HTTP health200.
- 가짜 완료 문항 결과/접수 Job와 PROCESSING Summary Job를 Mongo에 직접 준비한다. 실제 submit API를 거친 fixture는 아니다.
- 실행 중인 첫 앱에 SIGTERM을 전달하고 프로세스 종료·OOM 아님·exit0 또는143을 확인한다. 종료 직전 진행 중 HTTP 요청을 인위적으로 유지하는 검사는 아니다.
- 같은 Mongo에서 Summary Job 전체 BSON이 변하지 않았는지 확인한다.
- 새 앱 컨테이너로 다시 기동해 늦은 Azure Callback 두 번을 보내고 결과1개·원 시험 연결을 확인한다.
- 그동안 Summary Job는 그대로 유지한다. 이후 늦은 Summary Callback 두 번을 보내 결과1개, Summary Job와 Session의 COMPLETED를 확인한다.
- 새 앱 자체의 실제 HTTP controller/service/repository/Redis 경로를 사용한다. AI 서버가 직접 보낸 요청은 아니며 가짜 payload를 재전송한다.

## 3. 실행 및 한계

최종 실행: 2026-10-18 03:51:46 UTC 시작, 시나리오1개/실패0/오류0/skip0, Gradle 성공. exact image·정상 종료 조건을 포함한 최종 코드 기준이다. 종료 후 해당 후보 앱 컨테이너가 남아 있지 않음을 확인했다. 임시 Mongo/Redis/network는 try-with-resources로 정리하며 기존 로컬 이미지는 보존했다.

```sh
LC_PROCESS_REHEARSAL=true JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew mongoIntegrationTest --tests '*CandidateProcessRestartTest' --no-daemon
```

특정 로컬 후보 이미지가 필요한 opt-in 검사다. 환경변수 없이 일반 CI에서는 건너뛰므로 일반 Mongo 회귀 통과를 이 시나리오 통과로 계산하지 않는다. 앱 이미지는 재빌드하지 않았고 새로 추가한 것은 검사 코드뿐이다. 이전 소스 집합 hash는 새 테스트 추가 전 기록으로 보존한다.

초기 harness는 internal network에서 host published port를 기대하여 Mongo 시작 확인에 실패했다. 네트워크 격리를 풀지 않고 포트 공개 자체를 제거한 뒤 mongosh/HTTP를 컨테이너 내부 exec로 전환했다. 외부 서비스 설정을 변경해 우회하지 않았다.

## 4. 남은 운영 전환 검증

- production paid gate·owner/index/manifest·JWT/TLS 조합의 전체 앱 기동은 후속 [격리 검사](LC_CANDIDATE_PRODUCTION_TLS_REHEARSAL_2026-10-18.md)에서 가짜 자료로 통과했다. 운영 자료/키/네트워크 연결 검증은 여전히 별도다.
- 실제 접수중 요청/worker의 배출과 종료 경합, Callback 송신측 재시도 보장.
- Callback이 영원히 오지 않는 Job의 자동 또는 사용자 retry 경로. 이번 검사는 **늦은 Callback으로 완료**하는 경우이며 자동 재전송 성공이 아니다.
- 삭제/탈퇴·환불·owner 변경과 동시에 재시작하는 시나리오 및 양측 공동 연결.
- 운영 이미지 전달/명시 태스크 설정 적용·전환 시간과 검증 계정 승인. 서버부터 중단하지 않는다.

## 5. 참조

- [후보 패키지](LC_PRODUCTION_CANDIDATE_PACKAGE_2026-10-18.md)
- [전환 실행안](LC_PRODUCTION_CUTOVER_RUNBOOK_2026-10-18.md)
- 검사: `src/mongoIntegrationTest/java/web/tosunsaeng/domain/exams/CandidateProcessRestartTest.java`
