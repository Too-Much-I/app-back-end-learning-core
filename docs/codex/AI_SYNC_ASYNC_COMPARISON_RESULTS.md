# AI 응답 대기의 동기/비동기 분리 — 대조 실험 결과

실행일: 2026-09-08. 과거 AI 소스의 호출 순서를 근거로 만든 **동작 재현 실험**이다. 당시 배포 이미지/환경/실제 LLM을 그대로 실행한 결과는 아니다.

## 1. 5줄 결론

1. **소스 확인**: 7월11일 AI `fd43a983c9b9`는 채점과 기본 동기 Callback을 처리한 뒤 HTTP 응답을 반환했다. 7월12일 `1134e1c5f454`에 Redis queue 등록 후202를 반환하는 경로가 추가됐다. [이력 근거](AI_SYNC_ASYNC_HISTORICAL_EVIDENCE.md)
2. **실측**: 같은 웹 백엔드·같은2초 모의 채점에서 낮은 부하의 submit p95는 동기2,071.4ms→비동기62.1ms였다. 접수 응답 시간이 약97% 줄었지만 최종 결과는 둘 다 약2.6초였다. [집계](measurements/ai-sync-async-2026-09-08.summary.json)
3. **실측**: 시험이 겹치는 조건에서 submit 실패는 동기198/198건, 비동기0/198건이었다. 동기는 HTTP500 120건과 driver timeout78건이었다. 운영 장애율이 아니다. [원시 결과](measurements/ai-sync-async-2026-09-08.json)
4. **실측**: 겹치는 부하의 Tomcat 사용 worker p95는20→2개,20개 전부 사용 중인 sample 비율 중앙값은78.9%→0%였다. [집계](measurements/ai-sync-async-2026-09-08.summary.json)
5. **판단**: “AI 계산이 빨라졌다”가 아니라 “채점·Callback 대기를 HTTP 접수 응답에서 분리하여 요청 pool 포화와 제출 오류를 완화했다”가 이번 실험에 맞는 성과다. [실험 코드](../../scripts/perf/run-comparison.mjs)

## 2. 반드시 읽어야 하는 내용

### A. 낮은 부하 — 빠른 접수와 빠른 채점은 다르다

0.25시험/s, 조건당2시험×3회. 각 군66개 submit이 모두 성공했다. 시간은3회별 p95의 중앙값이다.

| 측정 | 동기 응답 | 비동기 접수 |
| --- | ---: | ---: |
| submit 응답 p95 | 2,071.4ms | 62.1ms |
| 최종 결과 확인 p95 | 2,630.6ms | 2,624.9ms |
| Tomcat 사용 worker p95 | 11개 | 2개 |
| submit 실패 | 0/66 | 0/66 |

채점 시간은 두 군 모두2초로 고정했다. 낮은 부하의 최종 결과 시간은 거의 같고, 비동기는 결과를 먼저 만들어낸 것이 아니라 접수를 먼저 알리고 요청 스레드를 반환했다. 약97%는 **이 설정의 submit 응답 p95** 감소율이며 실제 채점 속도·운영 성능 개선율로 쓰지 않는다.

### B. 겹치는 부하 — 오류와 결과 도착을 분리해서 집계

1시험/s, 조건당6시험×3회. 각 군18시험,198개 submit이다. 모든 시험에서11문항을30ms 간격으로 제출해 응시 시간이 아닌 제출 burst를 압축했다.

| 측정 | 동기 응답 | 비동기 접수 |
| --- | ---: | ---: |
| submit 성공 | 0/198 | 198/198 |
| submit HTTP500 | 120 | 0 |
| submit driver10초 timeout | 78 | 0 |
| 최종 결과를15초 관찰 안에 확인 | 15/18시험 | 18/18시험 |
| Tomcat 사용 worker p95 | 20개 | 2개 |
| Tomcat 사용 worker 최대 | 20개 | 4개 |
| worker20개 모두 사용 중인 sample 비율 중앙값 | 78.9% | 0% |
| status Polling 응답 p95 | 9,881.4ms | 6.9ms |

동기 군의 실패198건은 **제출 HTTP 요청의 실패**다. AI에는 문항198회·Summary18회가 전달됐고 Callback 오류는0건이었다. 따라서 답변198개가 모두 소실됐거나 AI가 채점하지 못했다는 뜻이 아니다. 최종 결과를 기한 안에 확인하지 못한3시험은 마지막 관찰 status가FAILED였다. 관찰 종료 이후의 영구 실패/데이터 소실 여부는 판정하지 않았다.

동기 부하 조건은 성공한 submit이0건이다. 전체 submit p95약10초는 실패·timeout을 포함하는 관측값이며, 이를 정상 응답시간으로 취급하거나 비동기61ms와 나눠 “정상 처리가160배 빨라졌다”고 쓰지 않는다.

### 보고서 권장 문구

> 과거 AI 서버의 동기 응답 및 Callback 처리 순서를 분석하고, 동일 백엔드와 동일 모의 채점 시간을 사용한 대조 실험을 수행했다. 낮은 부하에서 비동기 접수의 제출 응답 p95는2,071ms에서62ms로 감소했으며 최종 채점 완료 시간은 약2.6초로 유지됐다. 중첩 제출 조건에서는 동기 방식의 요청 스레드 포화와 제출 오류를 재현했고, 비동기 접수 조건에서 사용 스레드 p95 20개→2개 및 제출 오류198/198건→0/198건을 확인했다.

실제 운영 전후 수치가 아닌 **소스 기반 재현 실험**이라는 설명을 보고서에도 함께 유지한다.

## 3. 결정해야 하는 사항

- 포트폴리오/소마 보고서에는 이번 동기/비동기 재현과 이전 [웹↔앱 중복·정합성 비교](POC_APP_COMPARISON_RESULTS.md)를 별개 성과로 묶는 것을 권장한다. 두 실험의 수치를 섞어 동일 전후 비교라고 표현하지 않는다.
- 실제 장애 당시 효과를 확정하려면 운영 환경변수·배포 이미지·AI 처리 지연과 worker/serial 설정이 필요하다. 현재 소스 이력만으로 실제 배포 전환 날짜를 단정하지 않는다.
- 순수하게 “채점 완료까지 HTTP만 대기”하는 효과를 추가 분리하려면 Callback을 응답 이후로 보내는 동기 대조군이 필요하다. 이번 sync는 과거 기본 설정에 있던 **응답 전 동기 Callback 대기**를 포함한다.

## 4. 주요 위험과 미확인 사항

- 실제 과거Python·LLM을 실행하지 않았다. 원래의 serial lock/event-loop 특성과 실제 Redis worker 수를 재현하지 않고 양쪽64worker로 통제했다. node 타이머가 모의 계산 시간이다.
- 현재 웹89c8f9d를 두 군에 동일하게 사용했다. 7월 당시 백엔드 버전 전체와 현재 앱의 차이를 비교한 실험이 아니다.
- AI 처리2초·접수50ms·요약100ms·backend AI read timeout5초·Callback/driver timeout10초·시험 관찰15초는 정한 실험값이다. 당시 실측값이 아니다.
- 병목 모델에는 Callback이 일반 사용자 API와 같은 Tomcat pool을 공유하는 점이 포함된다. 순수한 일방향 요청 지연과 Callback 상호 대기의 기여를 각각 수치로 분리하지 않았다.
- CPU hard isolation·장시간 steady-state·실제AI throughput/queue persistence·provider 장애·모델 품질·유료 비용·모바일 UI 복구는 측정하지 않았다.
- p95는3회별 p95의 중앙값이다. 낮은 부하의 시험은 군별6개뿐이라 최종 완료 p95 표본이 작다. 신뢰구간이나 일반화된 최대 사용자 수를 제시하지 않는다.
- 관찰 deadline은 실제 시험 시작 후15초다. 네트워크 요청을 기다리는 중에는15초를 넘긴 뒤 종료를 관찰할 수 있으므로 `unfinished.observedMs`가 약15.15초다. 완료로 인정할 때는 deadline을 다시 확인한다.
- 기존 default 웹↔앱 JSON은 보존했다. 이번 실행기 보강으로 synthetic Summary의 part_number를0으로 바로잡고 deadline 재확인/실패 요청 기록/종료·drain 제한을 추가했다. 이전 결과를 수정하거나 재생성하지 않았다.

## 5. 현재 작업과 직접 관련된 설명

### 왜 같은2초 채점인데 부하가 겹치면 더 오래 걸리는가

1. 사용자 submit이 Tomcat worker를 차지하고 AI HTTP 응답을 기다린다.
2. AI는 평가를 끝낸 뒤 backend Callback 응답을 기다린다.
3. worker20개가 submit 대기에 묶이면 Callback과 status 조회도 뒤에서 기다린다.
4. backend5초 timeout이 요청을 실패시키며 worker를 풀지만, 기다리던 요청이 다시 worker를 채운다. driver에는 HTTP500 또는10초 timeout이 보인다.
5. 비동기 군은 접수202를 먼저 돌려 worker를 반환하므로 Callback과 status를 처리할 여유가 생긴다.

실험 로그에서 `ResourceAccessException`과 `SocketTimeoutException: Read timed out`도 확인했다. 이는 영구 deadlock이나 JVM crash 증명이 아니라 **timeout으로 해소되는 요청 스레드 포화/상호 대기**의 재현이다. 실제 음성/credential을 사용하지 않았다.

### 변경·계약·배포

- 변경: 기존 Node driver에 `--ai-timing`,64worker queue와 응답 순서 대조·실패 응답 기록·deadline/종료 보호를 추가했다. 별도 집계기, 이력/결과 문서, 원시/집계 JSON 및 작업 기록을 추가했다.
- application runtime·기존 공개 API/DTO/BaseResponse·retryCount·AI user_id=examId·Callback JSON·S3/Redis/Billing 계약은 변경하지 않았다. fixture 변경은 제품 계약 변경이 아니다.
- 웹/AI 원본과 운영 설정은 수정하지 않았다. 기존 dirty 변경을 보존했고 이번 의도 범위 밖 제품 파일의 수정은 없다.
- 이번 작업은 배포 불필요하다. 실제 개선을 배포/운영에서 주장하려면 staging·실제AI 계약/queue·timeout/재시도·동시성 검증이 필요하다.

## 6. 부록 — 전체 실행 결과와 검증

본 실험은2부하×2응답모드×3회=12개 실행,48시험·528submit이다. warm-up과 smoke는 합산하지 않았다.

| 조건/모드 | 3회 합계 시험 | 관찰 내 완료 | submit 실패 | submit p95 범위 ms | worker p95 범위 |
| --- | ---: | ---: | ---: | --- | --- |
| 낮음/동기 | 6 | 6 | 0/66 | 2071.01–2073.62 | 11–11 |
| 낮음/비동기 | 6 | 6 | 0/66 | 61.84–62.30 | 2–2 |
| 중첩/동기 | 18 | 15 | 198/198 | 10001.71–10002.09 (실패 포함) | 20–20 |
| 중첩/비동기 | 18 | 18 | 0/198 | 61.20–61.49 | 2–2 |

동기 중첩3회는 각각 HTTP500 40건·driver timeout26건, 관찰 내5시험 완료/1시험 미완료였다. 비동기3회는 각각66submit 성공,6시험 완료였다. 각 군 AI 요청은 문항264회·요약24회로 같았고 callback오류0이었다. AI worker 관측 최대는 동기46/64, 비동기23/64여서 설정된64worker 상한에 닿지 않았다. generator 시작 지연 최대는 약1.32ms 이내였다.

- [역사적 소스 근거와 통제 조건](AI_SYNC_ASYNC_HISTORICAL_EVIDENCE.md)
- [원시12개 실행 결과](measurements/ai-sync-async-2026-09-08.json)
- [3회 통계·실행기 해시](measurements/ai-sync-async-2026-09-08.summary.json)
- [실험 실행기](../../scripts/perf/run-comparison.mjs)
- [집계 무결성 assert](../../scripts/perf/summarize-ai-timing.mjs)
- 실험 로그: `build/poc-comparison/timing-run-sync-1.log` 등. 로그는후속실행시 바뀔 수 있다. `http://ai-server:8000`은 원래 RestTemplate URI의 오류 표기이며 실제 전송은 interceptor가 loopback fixture로 제한했다.

검증: AI timing smoke와 본 실험exit0, Node syntax,12행/군별3회/실패·성공 요청 수/worker0–20/AI worker상한 집계 assert를 통과했다. 이번에 Java runtime을 변경하지 않아 Gradle 회귀 테스트는 재실행하지 않았으며 직전 작업의543개 통과 결과를 이번 실행으로 중복 보고하지 않는다.
