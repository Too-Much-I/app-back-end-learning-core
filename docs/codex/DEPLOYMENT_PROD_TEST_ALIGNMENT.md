# LC 배포 프로필 prod/test 정리

## 결론

- 사용자 배포 환경 기준은 prod/test 두 개다. main→prod, develop→test를 배포 렌더러에서 명시한다.
- 현재 test ECS task35의 staging 프로필은 이번 코드가 전달·배포될 때 test로 바뀌도록 준비했다. 아직 실환경 적용하지 않았다.
- `APP_DEPLOYMENT_ENVIRONMENT`는 로컬 자동 테스트와 서버를 구분한다. ECS에서는 누락을 허용하지 않는다.
- 배포 환경과 active profile은 정확히 일치해야 하고 JWT 인증이 필수다. 혼합 profile·staging/production 등의 배포 값은 거절한다.
- 기존 API/Billing wire·fixtures·실DB·키·활성화는 변경하지 않는다.

## 안전 검증

배포된 test에는 AuthStartupValidator의 Legacy 금지·로컬 issuer/JWKS·placeholder audience 차단을 그대로 적용한다. 시험배정/Billing/UserMerged/UserWithdrawn index 검사, Billing/UserMerged/UserWithdrawn/AttemptGroup 트랜잭션 probe, 관련 설정과 Challenge·삭제 검증도 기존 제한 환경처럼 동작한다. UserWithdrawn index는 생성이 아닌 기존 index 검사 경로다.

배포 환경이 없는 local/test 자동 테스트는 기존 동작을 유지한다. ECS metadata 환경변수가 있으면 배포 환경 누락을 거절하고 strict 경로를 선택한다. 이전 staging 프로필의 strict 판정은 후방 안전 호환으로만 남아 있으며 새 배포 대상에 staging을 제공하지 않는다.

## 배포 전 남은 확인

- 신규 startup probe는 실제 DB에서 canary Transaction 후 rollback을 수행한다. 실제 서버 전환은 별도 배포 승인과 DB/index 준비 확인 후 수행한다.
- 기존 cluster·배포 workflow 파일명·concurrency group·main health URL의 staging 이름은 실제 리소스/라우팅 식별자라 유지했다. 특히 main의 기존 `api-staging` 주소가 의도한 운영 주소인지 별도 확인해야 하며 새 prod 도메인을 추정하지 않았다.
- 서버 flag는 자동으로 켜지 않는다. 이번 변경은 test 프로필 정합성 확보이며 Billing legacy manifest·JWT/TLS·공동E2E의 준비 완료가 아니다.
- Billing 계약의 environment=production은 Spring profile=prod와 다른 계층이다. 기존 wire와 fixture bytes는 유지한다.

## 검증 범위

단위 테스트는 배포 target/profile 불일치·혼합·지원하지 않는 값·ECS 누락·Legacy/로컬 JWT/placeholder 거절과 index/probe 조건을 확인한다. 실제 서비스 통합 기동·AWS/DB 변경 및 공동 Billing E2E는 수행하지 않는다. 최종 실행 결과는 WORKLOG/CURRENT_STATE에 기록한다.
