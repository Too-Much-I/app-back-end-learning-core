# LC 후보 production 프로필·JWT·TLS 격리 검증

## 1. 결론

1. TMI-116/TMI-217/TMI-220: 실제 후보 이미지의 production 설정 조합 기동 시나리오 1개가 통과했다(실패/오류/skip 0).
2. prod 프로필·paid gate·owner/index·가짜 빈 SEALED manifest를 사용했고 runtime 검증을 우회하지 않았다.
3. 임시 RSA 키와 PKCS12로 실제 HTTPS 인증서 검증·JWT 인증·환불 중복 저장을 확인했다.
4. 외부 연결 없는 Docker internal network, 신규 Mongo/Redis만 사용했다. 운영 설정·데이터·키는 사용하거나 변경하지 않았다.
5. 운영 ALB/ECS 연결·양측 업무 E2E·진행 중 요청 배출을 검증한 것은 아니며 지금 서버를 중단해도 된다는 결론은 아니다.

## 2. 확인한 범위

검사 파일: `src/mongoIntegrationTest/java/web/tosunsaeng/domain/exams/CandidateProductionTlsTest.java`.
이미지는 기존 로컬 후보 `lc-prod-candidate:20261018-amd64-local`, exact ID `sha256:c84d9216a2f9aeb9eb6f626fb37cfb4460f0b907056baa7284177c8106154cac`를 강제한다. 이번에는 제품 코드 수정이나 이미지 재빌드를 하지 않았다.

- 공개 8080 health: 200, 공개 포트의 환불 경로: 404.
- 전용 8444 HTTPS: 임시 CA로 인증서를 검증한다(`-k` 미사용).
- 전용 포트 환불 경로의 인증 없음: 401; 앱 일반 경로: 404.
- 유효 서명이나 잘못된 audience: 403.
- 올바른 JWT의 동일 이벤트 두 번: 각각 204; Mongo 차단 문서 1개, Session 생성 0개.
- 임시 private JWK와 토큰은 컨테이너 내 파일 전달만 사용하고 보고서/저장소에 값을 기록하지 않는다.

## 3. 실행 근거

2026-10-18 04:05:33 UTC 시작, JUnit 시나리오 1개/실패 0/오류 0/skip 0, Gradle 성공.

```sh
LC_PROCESS_REHEARSAL=true JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew mongoIntegrationTest --tests '*CandidateProductionTlsTest' --no-daemon
```

일반 CI에서는 opt-in 환경변수 없으면 skip된다. 이전 전체 회귀 결과에 이번 검사를 합쳐 전체 재실행한 것으로 표시하지 않는다. 임시 컨테이너/network는 자동 정리하며 후보 앱 컨테이너가 남지 않았음을 별도 확인했다. 기존 로컬 이미지는 유지했다.

## 4. 남은 위험과 다음 작업

- Billing/Identity는 연결되지 않은 가짜 주소다. LC→Billing reserve/confirm·사용자 JWT·실제 v1/v2 생성 성공 및 환불 보호 Session 처리는 이번 검사가 아니다.
- publisher/reconciler/phone/삭제 등 모든 기능을 ON한 검사가 아니다. 실제 전환 조합을 별도로 대조한다.
- 가짜 빈 manifest 검증을 운영 nonempty 목록 봉인 증거로 사용하지 않는다.
- 앞선 재시작 검사는 늦은 Callback 완료를 확인했지만, 실제 제출 진행 중 SIGTERM·ALB drain·Callback이 오지 않는 작업 자동복구는 남아 있다.
- 운영 전에는 최종 소스/원격 이미지 확정, 기존 태스크 전처리를 보존하는 TLS 시작 명령 적용안, 양측 준비와 생성 제한/배출 절차·검증 계정·실행 승인을 확인해야 한다.
- AWS/실DB/봉인/배포/commit/push는 이번 작업에 포함하지 않았다. 공개 API/DTO·AI user_id=examId·S3/Redis 계약은 불변이며 기존 dirty 변경도 보존했다.

## 5. 참조

- [후보 패키지](LC_PRODUCTION_CANDIDATE_PACKAGE_2026-10-18.md)
- [프로세스 재시작 검사](LC_CANDIDATE_PROCESS_REHEARSAL_2026-10-18.md)
- [전환 실행안](LC_PRODUCTION_CUTOVER_RUNBOOK_2026-10-18.md)
