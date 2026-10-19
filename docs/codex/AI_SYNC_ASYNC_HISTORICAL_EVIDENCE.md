# AI 동기 응답 → 비동기 접수의 이력 근거와 실험 범위

확인일: 2026-09-08. GitHub 읽기 전용 조회와 로컬 웹 git 이력 확인. AI/웹 저장소를 수정하거나 과거 배포를 실행하지 않았다.

## 1. 5줄 결론

1. **구현 사실**: AI `fd43a983c9b9`의 `/evaluations`는 평가 함수를 실행하고 Callback을 처리한 뒤 응답한다. [당시 API](https://github.com/Too-Much-I/web-ai/blob/fd43a983c9b9976e9320fa426a81d5ee0b9c0cea/app/api/server.py#L209)
2. **구현 사실**: 당시 `EVALUATION_CALLBACK_SERIAL_MODE` 기본값은1이고, 이때 feedback Callback도 기본 sync다. [설정](https://github.com/Too-Much-I/web-ai/blob/fd43a983c9b9976e9320fa426a81d5ee0b9c0cea/app/api/server.py#L39)
3. **구현 사실**: 2026-07-12의 `1134e1c5f454`에 Redis queue 등록 후202를 반환하는 경로와 별도 worker가 추가됐다. [변경 커밋](https://github.com/Too-Much-I/web-ai/commit/1134e1c5f454b2aaa2a3da11e2113bc37c8598e2)
4. **구현 사실**: 같은 커밋의 compose는 queue mode 기본값을redis로 지정하지만 API 코드 자체 기본값은sync다. 코드/compose만으로 실제 운영 전환 시점을 확정할 수 없다. [compose](https://github.com/Too-Much-I/web-ai/blob/1134e1c5f454b2aaa2a3da11e2113bc37c8598e2/docker-compose.yml)
5. **실험 계획/범위**: 위 HTTP 순서를 모의 AI로 재현하고 백엔드는 같은 웹89c8f9d로 고정한다. 실제 과거 Python/LLM 배포 전체를 실행하는 실험이 아니다. [실험 코드](../../scripts/perf/run-comparison.mjs)

## 2. 반드시 읽어야 하는 내용

당시 API가 `async def`라는 사실만으로 빨리 접수 응답을 반환한 것은 아니다. 다음 순서를 실제 함수에서 확인했다.

```python
# fd43a983c9b9, create_evaluation
return execute_evaluation_request(...)

# execute_evaluation_request
evaluation_result = EvaluationPipeline().evaluate(...)
evaluation_response = build_evaluation_response(evaluation_result)
enqueue_feedback_callback(background_tasks, evaluation_response)
return evaluation_response
```

기본 설정의 Callback은 HTTP 응답을 기다리는 호출이다. 따라서 백엔드 요청 스레드가 AI HTTP 응답을 기다리고, AI도 그 백엔드의 Callback 응답을 기다리는 상태가 가능하다. 요청 pool이 꽉 차면 Callback 처리도 대기하며 timeout으로 풀리는 상황을 만들 수 있다. 이는 단순히 AI가2초 계산한다는 것에 더해 **응답 전 Callback 완료를 기다리는 순서**의 영향이다.

신규 경로는 다음과 같다.

```python
# 1134e1c5f454, create_evaluation
if EVALUATION_QUEUE_MODE == "redis":
    response.status_code = 202
    return enqueue_evaluation_job(...)
```

queue에 등록한 다음 worker에서 평가와 Callback을 수행한다. async 접수 뒤에도 평가 시간이 없어지는 것은 아니다.

## 3. 결정해야 하는 사항

- 이번 수치는 “과거 코드의 응답 순서를 근거로 한 동일 백엔드 대조 실험”으로 사용한다.
- 실제 당시 운영 개선 수치를 주장하려면 당시 환경변수/이미지/worker 수/로그와 지연 분포가 필요하다. 이번에는 운영 설정을 조회하지 않았다.
- 단순 대기 시간만 분리한 실험이 필요하면 Callback을 응답 뒤로 보낸 동기 대조군을 별도로 추가한다. 이번 주 비교는 실제 기본 코드에 있던 **응답 전 동기 Callback**을 포함한다.

## 4. 주요 위험과 미확인 사항

- 과거 코드는 serial callback lock 기본값1이고, async FastAPI handler에서 동기 평가 함수를 직접 실행한다. 이번 Node fixture는 동일한64개 worker 상한을 양쪽에 주어 **당시 Python event loop/serial lock/worker 수를 재현하지 않는다**. AI 처리 용량 변화 효과를 섞지 않고 응답 순서를 비교하기 위한 통제다.
- 문항2초·접수50ms·요약100ms는 선택한 실험값이지 당시 실측 latency가 아니다. 실제 provider 요청·음성 처리·파일 변환·Redis queue durability·AI 재시작은 실행하지 않는다.
- 고정 웹 backend는8월31일89c8f9d이며7월11일 backend 전체가 아니다. 두 군에 같은 backend를 써서 제품 변화 효과를 통제한다.
- backend read timeout5초, Callback timeout10초, driver 요청 timeout10초, 관찰 deadline15초는 실험 설정이다. HTTP 실패와 결과 최종 도착을 별도로 집계한다. timeout이 발생해도 AI 작업은 이미 수행될 수 있다.
- 웹7월13일 `7b62a29`에는 Tomcat max20/min-spare4/max-connections100 설정 추가 이력이 있다. 이번 max20은 그 규모와 같지만 min-spare/max-connections와 당시 전체 자원까지 재현하지 않는다.
- 같은 노트북의 JVM 설정을 맞춘 짧은 실험이다. CPU hard isolation·장기 capacity·생산 환경 SLO를 입증하지 않는다.

## 5. 현재 작업과 직접 관련된 설명

기존 웹↔앱 실험은 두 군 모두 AI 접수50ms였으므로 동기 채점 완료 대기 차이를 비교하지 못했다. 이번 실험은 다음 두 군에 **같은 웹 backend와 같은 AI 처리/Callback 함수**를 사용한다.

| 구분 | 동기 군 | 비동기 군 |
| --- | --- | --- |
| AI HTTP 응답 | 평가와 Callback 응답 뒤200 | 접수50ms 뒤202 |
| 문항 처리 시간 | 2,000ms | 2,000ms |
| Summary 처리 시간 | 100ms | 100ms |
| AI worker 상한 | 64 | 64 |
| Callback | 처리 후, HTTP 응답 전 | 처리 후, 접수 응답 이후 |
| Backend | 동일 웹89c8f9d | 동일 웹89c8f9d |
| Tomcat max worker | 20 | 20 |

낮은 부하0.25시험/s×8초와 겹치는 부하1시험/s×6초를 각3회 비교한다. 문항11개를30ms 간격으로 제출하는 압축된 부하다. 실제 사용자 수로 환산하지 않는다. 단일 시험에11개 AI 요청이 잠시 겹치므로 동기 군은 두 시험만 겹쳐도 pool20개를 채울 수 있다.

## 6. 부록 — 재현

기존 `prepareComparison`으로 준비한 실행기를 사용한다. 이전 웹/앱 원시 결과는 덮어쓰지 않는다.

```sh
node scripts/perf/run-comparison.mjs --ai-timing --smoke
node scripts/perf/run-comparison.mjs --ai-timing --output=docs/codex/measurements/ai-sync-async-2026-09-08.json
node scripts/perf/summarize-ai-timing.mjs docs/codex/measurements/ai-sync-async-2026-09-08.json
```

준비/격리 원칙은 [기존 실행기 README](../../scripts/perf/README.md)를 따른다. 이번 옵션은 기존 default 웹/앱 비교와 별도 결과/로그 이름을 사용한다. marker/업무 이력은 CURRENT_STATE와 WORKLOG에 기록한다. 과거 사건에 임의 Jira 번호를 부여하지 않는다.
