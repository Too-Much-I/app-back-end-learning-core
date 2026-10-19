# 지정 ECS 서비스 정리 사전 점검 — 2026-10-19

## 1. 5줄 결론

1. [확인] 테스트 챌린지는 LC test에서 아직 ON이므로 AI만 즉시 중지하는 것은 권장하지 않는다.
2. [확인] 현재 LC prod는 별도 prod AI를 참조하며 구 AI와 운영 AI의 큐 이름도 분리되어 있다.
3. [확인] 구 Identity는 이미 실행0이며 자동 확장이 일시 정지됐지만 staging ALB 경로는 남아 있다.
4. [계획] 신규 호출 차단·진행 작업 확인 후 실행 수0부터 적용하고, 삭제는 이후 별도 결정한다.
5. [미확인] 큐의 실제 잔여 건수와 모든 외부 소비자 부재를 확정하지 않았으며 서비스 변경은 전혀 실행하지 않았다.

위 결론의 설정 근거는 아래 부록의 서울 ECS/ALB/Application Auto Scaling 읽기 조회 및 GitHub workflow다. TMI-126은 테스트 챌린지 배포의 과거 참고 이력이며 신규 Jira는 없다.

## 2. 반드시 읽어야 하는 내용

- [확인] LC test:45의 CHALLENGE_ENABLED=true, CHALLENGE_AI_ENDPOINT=https://ai-test.to-teacher.com/v1/challenges/evaluations. 테스트 챌린지 기능을 중지하고 기접수 작업을 처리한 뒤 AI Learning test를 내리는 순서가 필요하다. 설정 바인딩: [application.yml](../../src/main/resources/application.yml), 기능 조립: [ChallengeConfiguration.java](../../src/main/java/web/tosunsaeng/domain/challenge/ChallengeConfiguration.java).
- [확인] LC prod:10의 AI_SERVER_URL=http://tosunsaeng-ai-prod:8000. test:45는 http://127.0.0.1:9이므로 구 AI를 사용하지 않지만 정상적인 모의고사 AI 연결도 아니다. 이전 fail-fast 의도는 [테스트 배포 인계](CHALLENGE_TEST_DEPLOYMENT_HANDOFF.md)에 기록돼 있다.
- [확인] 운영 IDENTITY_ISSUER에는 identity-staging 도메인 문자열이 남아 있으나 IDENTITY_JWK_SET_URI는 identity-prod 도메인이다. issuer 문자열을 없애거나 변경하는 것은 이번 정리 범위가 아니다. 코드도 issuer 일치 검사와 JWKS 조회를 분리한다: [SecurityConfig.java](../../src/main/java/web/tosunsaeng/global/config/SecurityConfig.java).

## 3. 결정해야 하는 사항

- [계획] 테스트 챌린지 OFF 및 AI Learning test 실행0, 구 AI drain 후 실행0, 구 Identity 서비스 삭제를 각각 승인할지 결정한다. 현재 요청은 확인이며 변경 승인이 아니다.
- [계획] 구 서비스 전용 ALB rule/target group 및 workflow 정리는 서비스 중지와 구분한다. 공유 ALB/클러스터/Redis/DB/Secret/IAM은 보존한다.

## 4. 주요 위험과 미확인 사항

- [미확인] Redis 큐의 대기/진행 건수, 임의 외부 클라이언트 및 과거 클라이언트 사용은 이번에 검증하지 않았다. 설정 분리만으로 무트래픽을 증명하지 않는다.
- [확인] AI main의 수동 ECS workflow가 구 AI를 계속 대상으로 한다. push 트리거는 주석 처리되어 있다. 이 workflow가 실행 수를 자동1로 만든다는 증거는 없지만 폐기 대상 배포 경로로 남아 있다.
- [확인] Identity main workflow는 retired 처리되어 실행을 거절한다. 원격 develop workflow에는 main→구 서비스 분기가 남아 있으므로 향후 머지 시 다시 도입하지 않도록 정리할 필요가 있다. 로컬 파일과 원격 develop이 다르므로 로컬만으로 판단하지 않았다.
- [미확인] EventBridge 등 별도 자동화 전수조사와 모든 브랜치 workflow 조사는 수행하지 않았다.

## 5. 현재 작업과 직접 관련된 설명

[추론] 현 설정상 구 AI 중지가 LC prod 모의고사 요청 경로나 운영 큐 소비자를 직접 제거하지는 않는다. 다만 구 큐 진행 작업 확인 후 중지해야 한다. Identity는 이미0이므로 서비스 삭제가 현재 실행 중인 태스크를 추가 중단시키지는 않지만 staging 경로는 여전히 남아 있다.

## 6. 상세 근거 부록

2026-10-19 서울 tosunsaeng-staging-cluster에서 콘솔 및 CloudShell boto3의 describe/list 계열로 조회. Secret 값 조회, ECS Exec, DB 쓰기, scale/delete/update, 배포는 수행하지 않았다.

| 대상 | 확인된 설정 |
| --- | --- |
| AI Learning test | task:3, desired/running1/1, API+worker, Redis DB2, ai-test-tg |
| 구 AI | task:17, 1/1, API+worker4, Service Connect tosunsaeng-ai |
| prod AI | task:2, 1/1, API+worker4, Service Connect tosunsaeng-ai-prod |
| 구 AI 큐 | Redis DB1, eval_agent:evaluation_jobs 및 :processing, job prefix eval_agent:evaluation_job |
| prod AI 큐 | 같은 Redis DB1, eval_agent-prod:evaluation_jobs 및 :processing, job prefix eval_agent-prod:evaluation_job |
| 구 Identity | task:23, 0/0, identity-tg, scaling min1/max3이나 scale-in/out/scheduled 모두 suspended |
| AI 자동 확장 | 지정 AI2개 scalable target 없음; ECS scheduled actions 목록 비어 있음 |
| ALB HTTPS rule10 | identity-staging.to-teacher.com → 구 identity-tg |
| ALB HTTPS rule30/60 | identity-test / identity-prod → 각각 test/prod 전용 target group |
| ALB HTTPS rule50/51 | ai-test는 기존 source-IP 제한 forward, 나머지 fixed-response |

원격 코드 근거:

- [AI main 수동 ECS workflow](https://github.com/Too-Much-I/web-ai/blob/297145f01de882b6f074a92af0b169f0ea1de615/.github/workflows/deploy-app-ecs.yml): ECS_SERVICE=tosunsaeng-ai-service.
- [AI Redis 큐 설정](https://github.com/Too-Much-I/web-ai/blob/297145f01de882b6f074a92af0b169f0ea1de615/app/jobs/redis_queue.py): 큐 환경변수 의미 참고. 배포 이미지 소스와의 완전 일치 검증은 아니다.
- [Identity main](https://github.com/Too-Much-I/app-back-end-identity/blob/main/.github/workflows/deploy-staging.yml), [Identity develop](https://github.com/Too-Much-I/app-back-end-identity/blob/develop/.github/workflows/deploy-staging.yml): 조회 시점의 브랜치 파일이며 이후 변경 가능.

변경 파일은 본 점검 문서 및 CURRENT_STATE/WORKLOG만이다. 제품 테스트·배포는 필요 없어 실행하지 않았으며 문서 diff whitespace를 검사한다. 다른 작업의 기존 변경은 보존한다.
