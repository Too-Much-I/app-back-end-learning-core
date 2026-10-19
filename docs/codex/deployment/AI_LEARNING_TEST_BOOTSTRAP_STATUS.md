# AI Learning 테스트 배포 상태

## 결론

- 관련 TMI-126. main `2391a944010f816016c9263e507a2850c5b5c07a`를 테스트 배포했다.
- 새 ALB 없이 기존 ALB의 전용 host/source-IP 규칙을 사용한다. private ALB와 동등한 구성은 아니다.
- 서비스 `tosunsaeng-ai-learning-test-service`, Task Definition `tosunsaeng-ai-learning-test:2`: 0.5 vCPU/1GiB, API/worker 컨테이너2개, desired1.
- 로컬 Python3.12/amd64 테스트133개 통과(경고1개), AWS 이미지 빌드/ECR push 완료. 실제 음성/provider/Callback E2E와 LC 활성화는 미완료다.
- 최종 desired/running/pending=1/1/0, rollout COMPLETED, ALB healthy. 동일 VPC probe에서 Redis PING·S3 Put/Get/Delete·HTTPS health/ready200·미인증401 확인. 외부 출발지403도 확인했다.

## 리소스와 접근 경계

| 항목 | 값 |
| --- | --- |
| API 주소 | https://ai-test.to-teacher.com |
| 평가 endpoint | https://ai-test.to-teacher.com/v1/challenges/evaluations |
| Callback | https://api-test.to-teacher.com/internal/v1/challenges/grading/callback |
| ECR | tosunsaeng-ai-learning-worker:test-2391a94 |
| 배포 digest | sha256:cf2ea719b9921cfeb6c32c54961447cf9a8d0a25cd1314809e77fc681003ca35 |
| ECS cluster | tosunsaeng-staging-cluster |
| Task SG | sg-00b8d3208c3fd7705 |
| Target group | tosunsaeng-ai-test-tg/2cf078f19e6d90a0 |
| 인증서 | dac51c42-a317-4b94-8f78-dff6fb7ba1ac, ISSUED |
| ALB allow | priority50, ai-test host AND source13.124.57.130/32 |
| ALB deny | priority51, ai-test host 나머지403 |
| 임시 S3 | tosunsaeng-test-ai-audio, private/ACL off/SSE-S3 |
| 로그 | /ecs/tosunsaeng-ai-learning-test, 7일 |

NAT IP는 LC 전용 신원이 아니라 같은 NAT를 쓰는 서비스가 공유한다. 방향별 service credential 검증을 유지한다. 외부 CloudShell의 HTTPS 요청은403을 확인했다. Task는 public IP 없이 기존 private subnet 두 개에 배치한다. inbound는 ALB SG의 TCP8000, outbound는 HTTPS443 및 Redis SG의 TCP6379만 허용한다. 기존 Redis SG에는 새 AI SG의6379만 추가했다. 기존 ALB의 다른 규칙/기본 인증서/운영 서비스는 변경하지 않았다.

## 권한·설정

- execution role: `tosunsaeng-ai-learning-test-execution-role`. 해당 ECR pull, 해당 로그 그룹 stream/write, 사용자 지정 AI Secret 조회만 허용한다. ECR authorization token은 AWS 요구에 따라 Resource*다.
- task role: `tosunsaeng-ai-learning-test-task-role`. 새 임시 bucket `challenge/*` Get/Put/Delete만 허용한다.
- 양쪽 role trust는 ecs-tasks service와 해당 계정/서울 ECS SourceArn으로 제한한다. 동일 task의 두 컨테이너는 task role을 공유한다.
- Secret4개 존재/비어 있지 않음 및 양방향 credential 상이 여부를 CloudShell 메모리 내에서 boolean으로만 검증했다. 원문은 출력/파일 저장하지 않았다. ECR 로그인은 pipe를 사용하고 push 뒤 docker logout 완료했다.
- Redis는 기존 cache의 테스트 DB2를 LC와 공유하되 새 `app-ai-learning-test:{learning-jobs}:pending`, `processing`, `job` namespace로 분리한다. 신규 Redis 인스턴스/기존 키 변경은 없다. 논리적 분리이며 메모리·장애·관리 명령은 공유한다.
- 모델/추적/토큰 예산은 사용자 요청값을 적용했다. TRACE_CONTENT는 코드 미지원이라 추가하지 않았고 원문 차단은 유지한다. 업로드는 기존 계약상2MiB다.
- 2026-09-28 사용자 추가 요청에 따라 revision2의 worker `LLM_MODEL=gpt-6-luna`로 변경했다. `LLM_REASONING_EFFORT=none`, `LLM_MAX_OUTPUT_TOKENS=10000`, `OPENAI_TRANSCRIBE_MODEL=gpt-transcribe` 유지. OpenAI Docs 스킬로 공식 Responses/none 지원을 확인했으며 계정별 provider 실제 호출 성공은 아직 미검증이다. 동일 이미지, 1/1/0·COMPLETED·ALB healthy 및 새 task HEALTHY를 확인했다.
- 실제 ECS 정의와 같은 비밀값 없는 설정: `ai-learning-test.task-definition.template.json`.

## 남은 검증·운영 작업

- 일회성 검증 task `ff9f1a4359504de7b8544d638dd2e7de`는 STOPPED다. 검증을 수행한 worker exit0, 대기용 API 컨테이너는 essential worker 종료에 따라 중지(exit137)됐다. 서비스 task `556f16b9a90f40058cf6dc9e73fa9f26`와 구분한다. 합성 S3 검사 객체는 생성 후 삭제 완료했다.
- 기존 LC-test/운영 LC 모두1/1/0 및 COMPLETED 확인. AI API/worker 새 서비스만 배포했으며 LC 설정/기존 운영 서비스를 수정하지 않았다.
- OpenAI 모델 계정 지원 및 실제 AAC-LC 음성 채점·LangSmith 전달·LC Callback E2E는 아직 미검증이다.
- LC 테스트 DB에 100일/300문제 복사 및 전체 JSON 일치 검증, 6컬렉션/9필수 인덱스 준비 완료. LC 실행 역할의 해당 AI Secret 읽기도 승인 범위로 추가했다. LC test:4 활성화는 UserOwnedTransactionExecutor bean 누락으로 기동 실패하여 test:3/OFF로 복구했다. 독립 활성화 구성 수정·새 이미지 배포 후 재시도해야 한다. 운영 사용자 데이터/음성은 복사하지 않았다.
- 새 S3 orphan lifecycle2일은 아직 미적용이다. worker 자체 terminal cleanup 외 실패 잔여 객체 정리 정책을 적용 전 확정한다.
- 공유 Redis의 TLS/AUTH/HA 미지원은 테스트 한계이며 production 기준 충족으로 간주하지 않는다.
- Task sizing은 초기 테스트값이다. 실제 채점 부하와 OOM/worker 처리량을 확인한 뒤 조절한다.
- 이번 배포는 수동 bootstrap이다. AI 저장소의 CI workflow/자동 배포는 추가하지 않았으며 commit/push도 하지 않았다.
