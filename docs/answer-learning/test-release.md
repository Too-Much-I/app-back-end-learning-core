# 테스트 환경 적용 — 코드 추가 구현 없이 배포하기

이 문서는 `feat/answer-learning`의 프론트·Learning Core·전용 Lambda를 함께 적용하는 순서다. **2026-10-13 테스트 task role 권한, 최신 Lambda 배포와 테스트 ECS 채점 활성화 설정을 완료했다.** main/develop 머지와 새 백엔드 이미지 배포는 아직 실행하지 않았다. 다음 단계는 백엔드 브랜치 전달·develop PR 배포 후 실제 앱 종단 검증이다.

## 현재 테스트 ECS 설정

- 2026-10-13 14:12 KST 최종 검증: 테스트 서비스는 `tosunsaeng-learning-core-test:26`이며 `COMPLETED`, desired/running/pending `1/1/0`이다. 새 task의 ALB target은 healthy, 테스트 Core·Identity 및 운영 Core health는 모두 HTTP 200 / UP이다.
- 13:52 KST `:24` → `:25`에서 `ANSWER_LEARNING_*` 9개를 추가했고, 최신 Lambda 검증 후 `:25` → `:26`에서 채점 플래그 하나만 false → true로 바꿨다. 기존 환경변수 48개, secret 참조, 고정 image digest, task/execution role, CPU/메모리, 네트워크, 기존 rollout/자동 rollback 설정을 보존했다.
- `ANSWER_LEARNING_ENABLED=true`, dataset `ts-level-bank-40-per-part-20261007-v1`, content version `20261012-cda52eb5001a`, bucket `tosunsaeng-app-audio`, 기존 서울 IAM Lambda URL, region `ap-northeast-2`, daily submissions `60`, poll `1500ms`다.
- **`ANSWER_LEARNING_GRADING_ENABLED=true`**다. 최신 Lambda 배포와 IAM `/health`의 `deletionContract=v1`, 실제 삭제 계약 검증을 마친 뒤 적용했다.
- 기존 `USER_MERGED_WRITER_ENABLED`, consumer/source deny 및 `LEARNING_RECORD_DELETION_WRITER_FENCE_ENABLED`는 이미 true였고 변경하지 않았다. Mongo DB는 `to-teacher-learning-core-test` 그대로다.
- 현재 실행 이미지는 이전과 같다. **설정 완료가 학습 API 배포 완료를 의미하지 않는다.** 기능 브랜치의 새 백엔드 이미지 배포 후 목록을 확인할 수 있다. 정상 health는 전체 로그인/학습/채점 종단 검증을 대체하지 않는다.
- 다른 서비스 9개의 설정과 primary deployment ID는 그대로다. 검증 시작 때 이미 진행 중이던 테스트 Identity `:43` 배포가 독립적으로 완료돼 실행 수가 2 → 1로 바뀌었다. 이 서비스는 이번 작업에서 수정하지 않았다. 나머지 8개 서비스는 배포·실행 수도 동일했다.
- 최초 설정 증거는 Git 제외 `build/answer-learning-ecs-20261013/`, 최신 Lambda/`:26` 적용·검증 증거는 `build/answer-learning-lambda-release-20261013/`의 0600 파일에 보관한다. 설정 생성기 3개 검사와 전체 설정 보존 대조를 통과했다.

현재 이미지가 그대로인 상태에서 채점 설정 변경만 되돌릴 기준은 `:25`(grading=false), 최초 학습 설정 추가 전 기준은 `:24`다. 이후 새 학습 백엔드가 배포되거나 학습 기록이 생긴 뒤에는 과거 definition으로 코드를 되돌리지 말고, 현재 이미지와 기록 lifecycle을 유지하며 플래그만 변경한다.

## 현재 Lambda 배포 상태

- 서울 `tosunsaeng-answer-learning-staging`의 intake/worker 2개를 최신 전달 소스로 업데이트했다. CloudFormation `UPDATE_COMPLETE`, 함수 `Active / Successful`, 패키지 SHA-256 일치 확인.
- 기존 템플릿의 `CodeKey`만 변경했다. 기존 12개 리소스 ID, URL과 IAM 인증·호출 정책, runtime·역할·환경설정·secret 참조를 유지했다. 이전 패키지는 보존했다.
- 합성 테스트 15개와 실제 IAM health·미서명 403·반복 DELETE·모든 attempt의 비개인정보 tombstone·삭제된 작업의 지연 worker 전달 차단을 확인했다. 합성 표식 3개는 30일 TTL로 만료된다.
- 이번 작업에서 실제 사용자 녹음·STT·Decisions 모델 호출은 수행하지 않았다. 새 백엔드 이미지 배포 후 앱 → ECS → S3 → Lambda → Mongo 검증이 필요하다. 패키지 해시와 재배포 지침은 [Lambda README](../../lambda/answer-learning/README.md)를 따른다.

## 현재 적용된 권한

- 대상: `tosunsaeng-learning-core-test-task-role`. 클러스터의 서비스 10개를 조회해 테스트 Learning Core만 사용하는 역할임을 확인했다. execution role, 기존 staging/prod 역할은 변경하지 않았다.
- 추가: 인라인 정책 `LearningCoreTestAnswerLearningS3`는 [S3 정책 파일](backend-s3-policy.json)과 동일하다. 현재 출판 버전의 media 읽기, 학습 녹음 두 prefix의 저장·조회·정리에 한정한다. 출판 자료 수정·삭제 권한은 없다.
- 연결: `tosunsaeng-answer-learning-staging-BackendInvokePolicy-Te87n2HOPfmQ`. `tosunsaeng-learning-intake-staging` 한 함수의 IAM Function URL 호출만 허용한다. 관리형 정책 본문은 수정하지 않았다.
- 검증: 추가 정책과 실제 역할 각각 18개 IAM 시뮬레이션 통과, 기존 인라인 정책 3개·trust policy·permissions boundary 불변. 서비스 10개의 task definition·배포 ID·desired/running/pending 불변. 테스트 Core·Identity 및 운영 Core health가 모두 HTTP 200 / UP이었다.
- 권한 추가 자체는 서버 재배포/재시작을 동반하지 않았다. 후속 환경설정 배포는 위 `:25`/`:26` 기록과 구분한다. 실제 앱의 S3 업로드·Lambda 채점 종단 검증은 새 코드 배포 후 수행해야 한다. 권한 변경 전후 자료는 Git 제외 `build/answer-learning-iam-20261013/`에만 보관한다.

권한 추가만 되돌려야 한다면 **이번에 추가한 정책 두 개만** 아래 명령으로 제거한다. 기존 정책은 삭제하지 않는다. 학습 기능 활성화 이후에는 처리 중 제출과 삭제 작업의 영향을 먼저 확인한다.

```sh
aws iam delete-role-policy --profile tosunsaeng-learning \
  --role-name tosunsaeng-learning-core-test-task-role \
  --policy-name LearningCoreTestAnswerLearningS3
aws iam detach-role-policy --profile tosunsaeng-learning \
  --role-name tosunsaeng-learning-core-test-task-role \
  --policy-arn arn:aws:iam::889384901776:policy/tosunsaeng-answer-learning-staging-BackendInvokePolicy-Te87n2HOPfmQ
```

## 1. 전달물

- 프론트 기존 `feat/answer-learning` 변경 전체. 기존 앱 진입점과 학습 탭 사용. 샘플 진입점은 복사하지 않는다.
- 백엔드 현재 브랜치의 staged/unstaged/untracked 파일 전체. `lambda/answer-learning/`도 포함한다. 사용자 선택대로 아직 commit/push하지 않았다.
- 콘텐츠는 Atlas 테스트 DB에 이미 반영됐다. `to-teacher-learning-core-test`, 버전 `20261012-cda52eb5001a`, 체크리스트 320개다. 전체 콘텐츠를 Git에 추가하지 않는다.

## 2. Lambda와 task role 준비

1. [Lambda README](../../lambda/answer-learning/README.md)의 최신 패키지 배포와 `/health`의 `deletionContract=v1` 검증은 완료했다. 같은 소스를 다시 배포할 필요는 없다. 후속 소스 변경 시 패키지 해시와 변경 계획을 검토해 기존 **테스트용** 스택만 업데이트한다.
2. Learning Core **test task role**의 [S3 정책](backend-s3-policy.json)과 기존 `tosunsaeng-answer-learning-staging-BackendInvokePolicy-Te87n2HOPfmQ` 연결은 위 기록대로 완료했다. 배포 시 유지 여부를 확인하고 중복 생성하지 않는다. 기존 exam/challenge 정책은 보존한다. execution role에 붙이지 않는다.
3. 기존 JWT·UserMerged writer·삭제 transaction fence 및 rollout/index 검증을 유지한다. 신규 학습 시작에는 활성 owner transaction fence와 Mongo replica set이 필수다. 본 도구는 기존 rollout 승인 문서를 만들거나 관련 플래그를 임의로 켜지 않는다.

기존 스택 이름의 `staging`은 현재 준비된 학습 전용 테스트 스택을 뜻한다. prod의 데이터/역할/저장소로 자동 재사용하지 않는다. 새 환경을 만들 때에는 버킷/스택/secret/역할을 별도로 정한다.

## 3. 현재 테스트 task에 학습 설정만 추가

테스트 설정은 위 `:26` 기록대로 적용됐다. 이후 변경도 **테스트 서비스의 현재 task definition**을 다시 내보내 시작한다. 로컬 생성기는 기존 image·JWT·DB·exam bucket·secret 참조·sidecar를 유지한다. 현재 definition 전체에는 다른 환경값이 있을 수 있으므로 `build/` 밖에 공유하거나 Git에 넣지 않는다.

```sh
mkdir -p build/answer-learning-release
# 먼저 describe-services로 test-service의 현재 taskDefinition ARN을 확인한다.
aws ecs describe-task-definition --region ap-northeast-2 \
  --task-definition "$TEST_TASK_DEFINITION_ARN" --query taskDefinition \
  > build/answer-learning-release/current-task.json

python3 scripts/answer-learning/prepare_test_release.py \
  --task-definition build/answer-learning-release/current-task.json \
  --lambda-url "$VERIFIED_TEST_LEARNING_LAMBDA_URL" \
  --output build/answer-learning-release/ready-task.json
```

생성기는 `tosunsaeng-learning-core-test` family/container와 서울 Lambda URL만 허용한다. 기본 출력은 전체 출시용 `ANSWER_LEARNING_GRADING_ENABLED=true`이며 현재 적용 값도 true다. 최초 `:25`에서는 Lambda 선행 배포 전까지 이 값 하나만 false로 설정했다. 생성기 출력을 검토 없이 등록하지 않는다. `.env.example` 변경만으로 ECS 설정은 적용되지 않는다. 기존 `AWS_S3_BUCKET_NAME`, `MONGODB_DATABASE`, `AI_SERVER_URL`, `CHALLENGE_AI_ENDPOINT`는 변경하지 않는다.

다음은 backend feature 브랜치의 전체 변경을 검토·커밋·push하고 develop PR로 새 이미지를 배포하는 것이다. 사용자 선택대로 현재는 로컬 변경으로 유지한다. workflow는 현재 task definition의 image만 교체하므로 준비한 환경변수와 역할은 이어진다. 이 브랜치는 기존 main/prod 배포 경로를 변경하지 않는다.

## 4. 프론트 빌드와 확인

```dotenv
EXPO_PUBLIC_IDENTITY_API_BASE_URL=https://identity-test.to-teacher.com
EXPO_PUBLIC_LEARNING_API_BASE_URL=https://api-test.to-teacher.com
```

기존 Firebase/로그인 설정을 사용해 iOS/Android **새 네이티브 빌드**를 만든다. STT 모듈 때문에 OTA만으로는 적용되지 않는다. `LEARNING_SAMPLE`, `LEARNING_DEVICE_BACKEND`, `EXPO_PUBLIC_LEARNING_DEVICE_SESSION`은 일반 앱에서 제거한다.

실제 JWT로 목록/사진/표/음성, Part 1 완료·비교, Part 2~5 녹음/제출/결과, 재로그인 완료 유지, 게스트 통합, 기록 삭제와 재학습을 확인한다. 10개 프론트 API 계약은 변경하지 않았다. 기존 모의고사/챌린지/피드백/로그인 회귀도 함께 확인한다.

## 중단/되돌리기

신규 채점 접수만 중단할 때 `ANSWER_LEARNING_GRADING_ENABLED=false`, 학습 API 전체 중단은 `ANSWER_LEARNING_ENABLED=false`다. 삭제할 기존 제출이 남아 있으면 **학습 bucket/URL과 task role 정리 권한은 유지**한다. 삭제/통합 코드가 포함되기 전 서버 버전으로 내리면 신규 기록이 기존 수명주기에서 빠지므로 데이터 확인 없이 되돌리지 않는다.

자동 검사 통과는 실제 배포·사용자 테스트 완료를 뜻하지 않는다. 코드 외 선행 작업은 설정/IAM/Lambda 배포·네이티브 빌드이며 새로운 API 조립이나 UI 재이식은 필요 없다.
