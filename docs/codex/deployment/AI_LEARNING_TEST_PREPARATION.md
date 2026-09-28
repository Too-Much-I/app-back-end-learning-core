# 챌린지 AI 테스트 배포 사전 확인

## 결론

- 최신 배포 결과: 공유 ALB 출발지 제한 HTTPS 및 API/worker 동일 task 배포 완료, 1/1/0·COMPLETED·ALB healthy. 상세는 `AI_LEARNING_TEST_BOOTSTRAP_STATUS.md`를 따른다. 아래 사전 확인/미실행 문구는 최초 조사 시점 기록이다.
- 관련 TMI-126. 사용자 지정 `Too-Much-I/app-ai-learning`을 읽기 전용 검토했다.
- 검토 기준은 기본 브랜치 main, commit `2391a944010f816016c9263e507a2850c5b5c07a`이다. 실제 배포 브랜치 확정/배포 성공을 의미하지 않는다.
- Dockerfile은 Python3.12·ffmpeg·포트8000 API를 제공하며 worker는 같은 이미지에 별도 command를 적용한다.
- OpenAI Secret 참조, 양방향 credential, 비공개 HTTPS 네트워크와 추가 서비스 비용 확인이 필요하다. 아직 원격 리소스/이미지 push/서비스 기동은 수행하지 않았다.
- 운영 문제는 원본 DB 직접 연결 대신 문제 데이터만 테스트 DB로 복사하는 방향을 제안했다. 정확한 원본 DB·컬렉션·스키마·복사 범위는 사용자 확인 대기이며 복사는 미실행이다.

## 코드에서 확인한 구성

| 항목 | 확인 내용 |
| --- | --- |
| API | 기본 Docker command, uvicorn `app.main:app`, port8000 |
| Worker | `python -m app.jobs.worker` |
| 평가 API | `/v1/challenges/evaluations`, multipart v1 |
| Callback | `/internal/v1/challenges/grading/callback`, LC 구현과 일치 |
| 준비 상태 | `/health`, `/ready`; ready는 Redis·audio bucket·LC credential 확인 |
| API credential | `LEARNING_CORE_TO_AI_CREDENTIAL` |
| Worker 설정 | `OPENAI_API_KEY`, `LEARNING_RESULT_CALLBACK_URL`, `LEARNING_RESULT_CALLBACK_TOKEN` |
| 공통 저장소 | `REDIS_URL`, `LEARNING_AUDIO_BUCKET`, AWS_REGION 및 격리된 queue/job prefix |
| S3 권한 | API challenge/* Put/Delete, worker challenge/* Get/Delete; private bucket·암호화·2일 orphan cleanup 권장 |

근거: AI 저장소 README, docs/ecs-deployment.md, docs/challenge-v1-review.md, Dockerfile, docker-compose.yml, .env.example. `.github` 디렉터리는 없으며 ECS 자동 배포 workflow는 확인되지 않았다. Lambda 배포 스크립트/템플릿을 ECS 설정으로 대체 사용하지 않는다.

## 배포 전 결정·준비

- 사용자 승인: ECS 서비스1개/task1개에 같은 이미지의 API/worker 컨테이너2개를 배치한다. 기존 각0.5vCPU·1GiB 제안은 확정안이 아니며 공용 task의 자원 규모와 부하 검증은 남는다.
- 사용자 제공 Secret 참조는 `tosunsaeng/test/ai`의 `OPENAI_API_KEY`, `LANGSMITH_API_KEY`이다. 실제 값은 조회/기록하지 않는다. 추가 방향별 키 `LEARNING_CORE_TO_AI_CREDENTIAL`, `LEARNING_RESULT_CALLBACK_TOKEN`은 서로 다른 랜덤 값으로 사용자 등록 대기다.
- 원본 문제 위치는 사용자 지정 Cluster0 / `to-teacher-app` / `challenge_10s_questions`로 확인했다. 스키마 검증 및 테스트 DB 복사는 아직 수행하지 않았다.
- 사용자 요청 모델/설정: OPENAI_TRANSCRIBE_MODEL=gpt-transcribe, LLM_MODEL=gpt-5.6-luna, LLM_REASONING_EFFORT=none, LLM_MAX_OUTPUT_TOKENS=10000, LANGSMITH_TRACING=1, LANGSMITH_PROJECT=app-ai-learning-test, LANGSMITH_TRACE_MAX_STRING_LENGTH=12000. provider 계정의 실제 지원 여부와 비용/지연은 미검증이다.
- main2391a94의 LANGSMITH_TRACE_CONTENT는 미지원이다. content_for_trace는 원문 대신 길이/hash를 생성하고 sanitizer는 민감 필드를 제외한다. 원문 차단을 유지한다. AUDIO_UPLOAD_MAX_BYTES=10485760 요청도 코드에서 2097152로 제한되므로 배포값도 계약상2MiB에 맞춘다.
- 사용자 제공 queue/processing/job prefix 및 TTL604800/lease300/recovery30, upload dir, KEEP_AUDIO=0, LOG_LEVEL=INFO는 요청값으로 보존한다. redis://redis:6379/0은 Compose용이며 AWS endpoint와 미사용 논리 DB 확인 후 교체한다.
- 기존 ECS cluster/Redis 재사용 여부 및 Redis test namespace/DB 예약을 확인한다. 기존 공유 Redis는 TLS/HA/내구성 한계가 있어 운영 배포 기준을 충족했다고 주장하지 않는다.
- LC staging validator는 AI endpoint HTTPS를 요구한다. AI 계약은 private 경로를 요구하므로 public ALB host rule만 추가하는 방식은 사용하지 않는다. internal ALB 또는 TLS Service Connect의 구성/비용을 확정하고 IAM·SG 접근 확대는 적용 직전 확인한다.
- 양방향 service credential은 서로 달라야 하며 LC와 AI에 맞는 Secret key를 선택해 주입한다. credential 입력/저장은 사용자 인계하고 원문을 채팅/문서/로그에 남기지 않는다.
- 운영 문제의 문서 스키마가 challenge_10s_questions 계약과 일치하는지 확인한다. 시험 문제를 임의 변환하거나 사용자 학습 이력·음성·개인정보를 복사하지 않는다.
- LC Challenge 활성화는 AI/Callback·문제 seed·전용 collection/index·catalog 기준일 검증 뒤 진행한다. 현재 LC 정상 서비스와 OFF 설정 유지.

## 검증 범위

- 최신 상태: 사용자 승인으로 공유 public ALB의 source-IP 제한 HTTPS 경로를 테스트용 예외로 선택했다. NAT 출발지 13.124.57.130은 동일 NAT를 쓰는 서비스가 공유하므로 LC 단독 신원 증명이 아니며 방향별 인증을 유지한다. 새 ALB는 생성하지 않는다. ai-test.to-teacher.com 인증서 `dac51c42-a317-4b94-8f78-dff6fb7ba1ac`는 ISSUED이며 listener 연결/rule 생성은 미실행이다.
- Docker Python3.12/amd64의 격리 테스트133개 통과, Starlette httpx deprecation warning1개. 실제 provider/Callback E2E와 배포는 미실행이다. 소스 archive `/private/tmp/app-ai-learning-2391a94.tar.gz`는 Dockerfile/.dockerignore/requirements/app/tests만 포함하며 .env/.git는 제외했다. CloudShell 업로드는 확장의 파일 URL 접근 권한에 막혀 사용자 설정 대기다.
- 2026-09-28 AWS 재조회: Secret `arn:aws:secretsmanager:ap-northeast-2:889384901776:secret:tosunsaeng/test/ai-7MnVsF` 메타데이터 확인. 사용자 등록 완료 보고이며 JSON 실제 값/키 검증은 미실행이다. ECR `tosunsaeng-ai-learning-worker`는 존재한다. 신규 AI test service/learning 전용 역할/AI 임시 S3는 목록에 없다. 기존 ALB는 internet-facing `tosunsaeng-staging-alb` 한 개, Cloud Map은 HTTP namespace `tosunsaeng-staging`이 있다. namespace 존재만으로 Service Connect TLS 구성이 있다고 판단하지 않는다. 비공개 HTTPS 및 접근 범위/비용 승인 전 원격 변경 없음.
- 후속 확인: linux/amd64 `app-ai-learning-test:2391a94` 로컬 Docker build exit0 완료. ECR push/ECS 배포 및 컨테이너 runtime 테스트는 아직 미실행이다. 아래 미실행 항목은 최초 조사 시점 기록이다.
- Git 저장소 접근 및 main HEAD 확인, 임시 clone으로 배포/계약 문서와 코드 검토 완료. AI/LC runtime 코드는 수정하지 않았다.
- 로컬 pytest는 미설치로 실행되지 않았다. Python3.12 명령도 확인되지 않아 테스트 통과를 주장하지 않는다. 의존성 설치·Docker build·ECR push는 미실행이다.
- 다음 단계는 사용자 답변 반영, 격리된 Python3.12/Docker 테스트·이미지 build, AWS 현재 리소스 inventory와 비용/보안 범위 확정이다.
