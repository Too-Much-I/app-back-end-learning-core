# 학습 전용 Lambda 전달 소스

Learning Core 담당자가 다른 저장소를 찾지 않고 intake/worker를 빌드하도록 2026-10-13의 `app-ai-learning` 학습 런타임을 함께 전달한다. 원본과 일치하는 소스 해시는 `source-manifest.json`에 있다. 일반 시험/챌린지 AI 코드는 포함하지 않는다. 문제은행·사용자 음성·환경파일·API 키는 없다.

## 테스트 Lambda 배포 상태 — 2026-10-13

- 서울 `tosunsaeng-answer-learning-staging` 스택의 intake/worker 2개를 이 소스로 업데이트했다. 이름의 staging은 현재 테스트 학습 전용 스택이다. CloudFormation `UPDATE_COMPLETE`, 두 함수 `Active / Successful`과 패키지 SHA-256 일치를 확인했다.
- ZIP: 22,082,587 bytes, SHA-256 `3c4196f8b1451fdd5dbbb798b1d92f0ddd773619bc4c99f7dfb90fd5043babf2`. 기존 code bucket의 `answer-learning-deploy/<SHA-256>/lambda.zip`에 SSE-S3와 checksum 검증으로 저장했다. 기존 패키지를 덮어쓰지 않았다.
- 기존 템플릿을 사용하고 `CodeKey`만 바꿨다. 다른 파라미터, 함수 runtime/역할/환경설정, 리소스 12개의 실제 ID, Function URL/IAM 인증과 backend 호출 정책 본문은 그대로다. 변경 계획의 URL/정책 항목은 같은 intake ARN 참조에 대한 동적 항목이며 실제 리소스 교체나 접근 권한 확대는 없었다.
- 합성 테스트 15개 통과. 배포 후 IAM 서명 `/health`에서 `deletionContract=v1` 확인, 미서명 호출 403 확인. 새 합성 UUID로 DELETE 반복 2회, 세 attempt의 비개인정보 tombstone/GET 404, 삭제된 작업의 지연 worker 전달 ACK를 검증했다. 기존 사용자 자료는 조회·삭제하지 않았다. 합성 표식 3개는 기존 30일 TTL로 만료된다.
- 실제 STT/Decisions 모델 호출은 수행하지 않았다. Lambda 코드·계약 검증과 사용자 음성 채점 정확도/백엔드 종단 검증을 구분한다. 런타임 키 값은 읽거나 기록하지 않았다.
- 변경 전후 구성·changeset·checksum·실환경 검증은 Git 제외 `build/answer-learning-lambda-release-20261013/`에 보관한다. 현재 ECS 활성화 상태와 남은 백엔드 배포는 [테스트 환경 적용 문서](../../docs/answer-learning/test-release.md)를 따른다.

## 계약

- `POST /v1/learning/evaluations`: immutable S3 음성·미리 준비된 체크리스트로 작업 접수.
- `GET /v1/learning/evaluations/{submissionId}:{attempt}`: 동일 작업 상태/결과 조회.
- **`DELETE /v1/learning/evaluations/{submissionId}`**: 1~3회 시도 전체를 `deleted` tombstone으로 교체한다. 응답 `result={submissionId,purged:true}`. 원래 payload/전사/결과를 제거하며 tombstone은 30일 TTL이다. 반복 요청과 삭제 후 늦은 요청에 안전하다.
- `/health`: `deletionContract=v1`. 새 계약 배포 확인용이며 실제 채점을 실행하지 않는다.

모두 backend IAM Function URL 인증을 사용한다. Worker 저장은 lease token과 현재 상태를 함께 비교한다. 삭제/만료 후 도착한 STT/채점 결과는 저장하지 않고, 삭제된 작업의 SQS 재전달은 처리 완료로 응답한다. 7일 작업 TTL/24시간 backend 재시도 제한보다 tombstone을 오래 유지한다. 실패한 삭제를 404만으로 성공 처리하지 않는다.

## 로컬 검증

이 디렉터리에서 별도 Python 환경을 사용한다.

```sh
python3 -m pip install -r requirements-practice-lambda.txt pytest==9.1.1
python3 -m pytest -q
```

합성 체크리스트·가짜 provider·메모리 저장소로 검사한다. AWS·OpenAI 호출이나 키 설정이 필요 없다.

## 빌드

Python 3.12, Linux x86_64 런타임이다. Mac용 의존성을 그대로 압축하지 않는다.

```sh
mkdir -p build/dependencies
python3 -m pip install --target build/dependencies \
  --platform manylinux2014_x86_64 --python-version 3.12 --implementation cp \
  --only-binary=:all: -r requirements-practice-lambda.txt
python3 scripts/build_practice_lambda.py \
  --dependencies build/dependencies --output build/answer-learning-lambda.zip
```

빌드 스크립트는 지정한 소스와 의존성만 압축하고 SHA-256을 출력한다. `infra/practice-learning-stack.json`은 기존 학습 전용 스택 템플릿이다. 이후 배포도 기존 테스트 스택의 `CodeKey`를 새 패키지로 갱신하고 `CodeBucket/AudioBucket/RuntimeSecretArn` 등 기존 파라미터를 유지한다. 위 빌드 명령 자체는 배포하지 않는다. 실제 적용 시 [테스트 환경 절차](../../docs/answer-learning/test-release.md)에 따라 변경 계획을 확인하고 배포 후 계약을 검증한다.

이전 코드 패키지는 보존했지만 새 백엔드가 삭제 계약을 사용하기 시작한 뒤에는 `deletionContract=v1`이 없는 이전 패키지로 단순 복귀하지 않는다. 장애 시 신규 채점 접수부터 끄고 기존 기록 정리 계약을 유지하는 수정 버전을 배포한다.

OpenAI 키는 기존 Secrets Manager의 `OPENAI_API_KEY`에서 worker만 읽는다. 프론트·Learning Core·intake에 키를 전달하지 않는다. 전사/체크리스트 판정은 기존 OpenAI STT/Decisions 경로이며 Jev fallback은 없다.
