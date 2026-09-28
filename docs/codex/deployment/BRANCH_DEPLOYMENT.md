# Learning Core 브랜치별 배포

## 5줄 결론

1. `main`은 기존 ECS 서비스·주소·AWS_ROLE_ARN을 유지한다. 리소스의 기존 `staging` 이름을 운영 이름으로 변경하지 않는다.
2. `develop`은 별도 테스트 서비스와 `AWS_TEST_ROLE_ARN`만 사용한다.
3. push와 수동 실행 모두 실행 ref로 대상을 결정하며 다른 브랜치/tag는 배포하지 않는다.
4. 현재 서비스의 task definition을 읽어 이미지 digest만 교체한다. DB/secret/feature flag를 운영에서 테스트로 복사하지 않는다.
5. 최초 인프라 생성은 이 workflow가 하지 않는다. 테스트 서비스가 없으면 이미지 push 전에 실패한다.

## 반드시 읽을 내용

| 항목 | main (기존 대상 유지) | develop |
| --- | --- | --- |
| ECS cluster | tosunsaeng-staging-cluster | 동일 cluster |
| ECS service | tosunsaeng-learning-core-service | tosunsaeng-learning-core-test-service |
| Task family / container | tosunsaeng-learning-core | tosunsaeng-learning-core-test |
| Health URL | https://api-staging.to-teacher.com/actuator/health | https://api-test.to-teacher.com/actuator/health |
| GitHub Repository variable | AWS_ROLE_ARN | AWS_TEST_ROLE_ARN |
| ECR repo | tosunsaeng-learning-core | 동일 repository |
| Image tags | SHA, staging | test-SHA, test |
| Concurrency | learning-core-staging-deploy | learning-core-test-deploy |

실행 경로는 `.github/workflows/deploy-staging.yml`, ref 분기는 `scripts/deployment/select-target.sh`에 있다.
main의 이름 없는 SHA 태그를 develop이 덮어쓰지 않는다. 실행 이미지에는 build 결과 digest를 사용한다.
같은 브랜치 배포는 직렬화하고 진행 중 배포를 취소하지 않는다. 수동 실행도 브랜치에 맞는 대상으로만 간다.

## 사용자가 준비할 설정

- GitHub Settings → Secrets and variables → Actions → Variables에 `AWS_TEST_ROLE_ARN`을 추가한다. 기존 `AWS_ROLE_ARN`은 유지한다. ARN은 credential 자체가 아니며 Access Key를 추가하지 않는다.
- 테스트 배포용 OIDC 역할은 ECS task/execution 역할과 별개다. 기존 두 ECS 역할을 이 변수에 넣지 않는다.
- 테스트 OIDC trust는 `aud=sts.amazonaws.com`, `sub=repo:Too-Much-I/app-back-end-learning-core:ref:refs/heads/develop`로 제한한다. main 역할도 main ref만 신뢰하는지 확인한다. 이번 코드 변경은 실제 IAM/GitHub 설정을 바꾸지 않는다.
- 테스트 배포 역할은 테스트 service의 Describe/Update, 필요한 task definition Describe/Register, LC ECR push, 그리고 **테스트 task/execution 역할만** PassRole 가능하게 제한한다. ECS RegisterTaskDefinition처럼 resource 제한이 지원되지 않는 권한은 AWS 지원 범위를 따르되 UpdateService/PassRole은 정확히 제한한다.
- 별도 GitHub Environment는 추가하지 않았으므로 environment 기반 OIDC sub로 바꾸지 않는다. 향후 도입 시 trust와 승인 규칙을 함께 변경한다.

## 주요 위험과 미확인 사항

- 이 workflow는 기존 서비스를 갱신하는 CD다. 마지막 확인 시 테스트 service가 없었으므로 현재 상태에서 develop push만으로 최초 서버가 만들어지지 않는다.
- 먼저 테스트 이미지 최초 업로드, 검증된 테스트 전용 task definition 등록, 전용 SG/로그/target group·HTTPS/DNS·ECS service 생성을 별도 승인 절차로 완료해야 한다. desired count 0인 service를 만들었다면 정상 배포 전에 별도로 1로 설정해야 한다. 이 workflow는 desired count를 바꾸지 않는다.
- ECS 초안의 placeholder를 그대로 등록하지 않는다. Mongo 테스트 secret/DB, Redis DB2, 테스트 S3, 테스트 JWT issuer/audience를 검증하고 Challenge/연동 flag OFF를 유지한다. 자동 migration/Challenge 활성화는 하지 않는다.
- ref 분기와 family/container 검증은 실수를 줄이지만 AWS 권한 격리를 대체하지 않는다. 테스트 역할에 운영 서비스 업데이트 권한을 주면 안 된다. ECR repository는 공유하므로 운영 배포는 mutable tag 대신 digest를 사용한다.
- 실제 IAM trust·AWS_TEST_ROLE_ARN·테스트 인프라 존재 여부는 이번 작업에서 원격 재검증하지 않았다. health 200만으로 MEMBER 인증·S3 권한·DB2 선택·채점을 증명하지 않는다.

## 검증 및 실행 순서

1. `node --test scripts/deployment/select-target.test.js`, `bash -n scripts/deployment/select-target.sh`, `./gradlew clean test`를 실행한다.
2. 최초 인프라·역할·GitHub 변수 설정을 완료한 뒤 사용자가 workflow/script/test 파일을 함께 commit/push한다. 자동화 에이전트는 commit/push하지 않는다.
3. develop 실행에서 테스트 서비스·test 태그·digest, service stability와 테스트 HTTPS health를 확인한다.
4. MEMBER 토큰으로 인증 검증, 테스트 Mongo/Redis/S3 경로를 확인한다. main 배포 대상이 그대로인지 리뷰한다.
5. 수동 실행 UI는 workflow가 기본 브랜치에도 있어야 나타날 수 있다. feature 브랜치에서 수동 실행하면 배포 job은 skip된다.

## 상세 근거

- 브랜치별 선택·미설정 role·지원하지 않는 ref·환경변수 주입 방지 회귀 테스트: `scripts/deployment/select-target.test.js`.
- 기존 단위·migration·Mongo replica-set integration gate는 workflow에 유지한다.
- 외부 API·BaseResponse·AI JSON·Redis/S3 key 계약과 Java runtime 코드는 변경하지 않는다.
