# 웹 POC ↔ 앱 모의고사 백엔드 비교 실험

실행일: 2026-09-08. 관련 구현 배경: TMI-25. 신규 Jira 생성·변경 없음.

## 1. 5줄 결론

1. **실측**: 실제 Controller/Service·격리 MongoDB/Redis·모의 AI로 30개 조건 실행, 총1,272개 시험 흐름을 최종 완료했다. [원시 결과](measurements/poc-comparison-2026-09-08.json)
2. **실측**: 각 문항을2번 제출한 조건에서 AI 문항 HTTP 전송이 웹2,640회→앱1,320회로50% 줄었다. 실제 AI 과금 절감률은 아니다. [집계](measurements/poc-comparison-2026-09-08.summary.json)
3. **실측**: 1번 문항 Callback을 늦추고 AI가 요약 요청에 즉시 응하는 조건에서 조기 완료 표시가 웹12/12건, 앱0/12건이었다. 운영 발생률은 아니다. [실험 정의](../../scripts/perf/README.md)
4. **실측**: 정상 초당15시험에서 submit p95는 웹55.2ms, 앱61.4ms였다. 이번 실험은 일반 응답 속도·최대 처리량 개선을 입증하지 못했다. [집계](measurements/poc-comparison-2026-09-08.summary.json)
5. **판단**: 보고서에는 속도 향상보다 중복 전송 방지·완료 상태 정합성 개선을 성과로 쓰는 것이 적합하다. 생산 환경 전체/MSA·과거 동기 장애 재현 결과로 확대하지 않는다. [범위와 제약](../../scripts/perf/README.md)

## 2. 반드시 읽어야 하는 내용

### A. 보고서에 가장 적합한 성과

| 항목 | 웹 POC | 앱 | 해석 |
| --- | ---: | ---: | --- |
| 중복 제출 시험 수 | 120 | 120 | 40시험씩3회, 문항마다2번 submit |
| AI 문항 요청 HTTP 수 | 2,640 | 1,320 | 백엔드→모의 AI 전송50% 감소 |
| AI 요약 요청 HTTP 수 | 240 | 120 | 중복 요약 전송도50% 감소 |
| 중복 조건 Tomcat 평균 사용 worker | 5.35 | 3.57 | 각 회차 평균의 중앙값, 이 조건에서 약33% 감소 |
| 순서 역전 조건 시험 수 | 12 | 12 | 4시험씩3회 |
| 11문항 결과 전 조기 COMPLETED 노출 | 12 | 0 | 최종적으로는 웹도12건 모두 결과가 모임 |

**보고서 권장 문구**

> 기존 웹 POC와 개선된 앱 모의고사 백엔드를 격리된 동일 설정의 시험 환경에서 비교했다. 120개 시험의 문항을 각각 두 번 제출하는 조건에서 AI 문항 전송을 2,640회에서 1,320회로 줄였으며, 결과 Callback 순서를 변경한 12개 시험에서는 기존 POC의 조기 완료 표시 12건이 앱에서 발생하지 않음을 확인했다. 일반 부하에서는 두 버전 모두 시험을 완료했으나, 앱의 추가 상태 관리에 따른 응답 비용도 관측되어 속도 개선과 정합성 개선을 구분했다.

마지막 문장의 상태 관리 비용은 아래의 코드 기반 해석이며 단독 원인을 profiling으로 입증한 것은 아니다. 더 엄격하게는 “앱의 응답 시간이 더 길게 관측되어”로 바꿀 수 있다.

### B. 정상 부하에서는 속도 개선이 나오지 않았다

아래 시간/스레드 값은 **3회별 p95 또는 평균의 중앙값**이다. 모든 요청을 합친 p95나 통계적 신뢰구간이 아니다.

| 초당 시험 시작 | 버전별 시험 수 | 웹/앱 완료 수 | submit p95 웹→앱 | 최종 결과 p95 웹→앱 | 평균 사용 worker 웹→앱 |
| ---: | ---: | --- | --- | --- | --- |
| 1 | 24 | 24 / 24 | 61.9→71.7ms | 1,176.1→1,213.7ms | 0.69→0.90 |
| 5 | 120 | 120 / 120 | 57.2→63.2ms | 1,158.7→1,167.3ms | 2.79→3.32 |
| 15 | 360 | 360 / 360 | 55.2→61.4ms | 1,148.4→1,163.4ms | 7.82→9.41 |

정상 부하의 HTTP/Callback 실패는 양쪽 모두0건이었다. 최고 조건도 모든 시험이 완료되어 **어느 쪽의 최대 처리 한계가 더 높은지는 알 수 없다.** “앱이 초당15시험까지만 처리한다” 또는 “동시 사용자15명을 지원한다”는 해석도 틀리다.

## 3. 결정해야 하는 사항

- 현재 보고서: 중복 AI 전송50% 감소와 조기 완료 방지를 우선 선택하는 것을 권장한다. 테스트 조건과 모의 AI 사용을 함께 적는다.
- 최대 처리 능력까지 필요하면 동일 총CPU hard limit을 적용한 독립 컨테이너/host, 긴 warm-up·측정 구간과 사전 SLO를 정해 추가로 부하를 올린다. 이번 결과만으로 운영 capacity를 산정하지 않는다.
- 과거 “동기 채점으로 thread가 고갈된 장애”를 정량화하려면 당시 웹/AI 기준 버전을 추가로 확정한다. 이번 웹89c8f9d도 Callback/Polling 구조이므로 당시 동기 장애를 재현한 것이 아니다.
- 웹에서 발견한 조기 완료·중복 전송을 이번에 수정하지 않았다. 웹은 명시된 수정 금지 경계를 유지한다.

## 4. 주요 위험과 미확인 사항

1. **범위 제한**: production 서비스 전체가 아니라 공통 모의고사 submit/status/summary/feedback callback 흐름이다. 시험 생성·음성 업로드·JWT·Identity·Billing·Sentry·앱UI·예약/회원병합 Transaction 등은 제외했다. 앱의 사용자 소유권 검증은 고정 test user와 미리 저장한 Session으로 실행했다.
2. **실행 환경**: Spring MVC/Tomcat과 실제 Service·Spring Data repository를 비교용 composition root로 구성했다. production component scan·SecurityFilterChain·전체 AOP wiring 검증이 아니다. 앱과 웹 모두 앱의 dependency classpath를 사용했다. 원본 배포 이미지끼리의 비교가 아니다.
3. **자원 제한의 차이**: JVM heap512MiB·ActiveProcessorCount2·Tomcat20 worker는 같지만 ActiveProcessorCount는 CPU hard limit이 아니다. Mongo CPU1/512MiB, Redis CPU0.5/128MiB만 Docker hard limit이다. 동일 노트북에서 순차 실행했으며 다른 host 작업 영향/CPU 경합을 완전히 배제하지 못했다.
4. **짧은 실험**: JVM별4시험 warm-up, 정상/중복 조건8초·순서 역전4초를3회 반복했다. steady-state·장기 메모리 증가·장애 확률·통계적 유의성 측정이 아니다. 부하 조건 순서는 회차 내 고정이라 JIT/캐시가 높은 부하 조건에서 더 따뜻할 수 있다. 부하가 높을수록 latency가 조금 줄어든 것을 확장성 이득이라고 해석하지 않는다.
5. **AI 모형**: 접수50ms·문항500ms·요약100ms를 고정했다. 실제 LLM·worker 처리량/queue·rate limit·모델 품질/비용을 모델링하지 않는다. 1번 문항만2,500ms 지연한 조건의 요약은 fixture가 모든 문항을 기다리지 않고 응한다. 실제 AI가 자체적으로 모든 문항을 기다린다면 운영 증상은 달라질 수 있다.
6. **중복 요청 조건**: 문항마다 의도적으로2번 제출했다. 정상 이용 전체에서50% 비용 절감이라는 뜻이 아니다. AI stub은 멱등 키를 이용해 중복 추론을 제거하지 않으며, 집계는 backend가 보낸 HTTP 수다.
7. **완료 시간**: 예정된 시험 시작부터 COMPLETED·11문항 summary 조회·모든 문항 및 요약 Callback ACK까지 측정했다. 생성/녹음 시간은 제외하고 문항 제출을30ms 간격으로 압축했다. Polling200ms의 관찰 오차가 있다.
8. **기준 버전**: 웹은 로컬89c8f9d(2026-08-31), 앱은 cb5f6ee(2026-09-07)+기존 미커밋 작업이다. 웹 remote 최신인지 확인하지 않았고 immutable app snapshot을 새로 만들지 않았다. 종료 후 source digest는 [집계 파일](measurements/poc-comparison-2026-09-08.summary.json)에 기록했다.
9. **제외된 계측**: 구조화 로그 비용은 양쪽 ERROR log level이므로 비교하지 않았다. Java process CPU/RSS, 네트워크 bytes, 실제 AI 비용, DB query별 profiling과 SLO 임계 부하는 미측정이다.

## 5. 현재 작업과 직접 관련된 설명

### 구현에서 확인되는 차이

- **구현 사실**: 웹 `submitAudio`는 요청마다 오디오 다운로드·AI 전송을 수행한다. 앱은 결정적 Job과 저장 결과를 확인하고 최초 요청만 전송한다. [웹 원문](/Users/msde76/IdeaProjects/web-back-end/src/main/java/web/tosunsaeng/domain/exams/application/ExamServiceImpl.java), [앱 Job 처리](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamGradingService.java)
- **구현 사실**: 웹은11번 문항 Callback에서 Summary를 요청하고 totalScore가 오면 Redis를 COMPLETED로 바꾼다. 앱은 필수 문항 완료를 확인한 뒤 Summary를 시작한다. [웹 원문](/Users/msde76/IdeaProjects/web-back-end/src/main/java/web/tosunsaeng/domain/exams/application/ExamServiceImpl.java), [앱 Callback](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamServiceImpl.java), [앱 Summary gate](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamGradingService.java)
- **분석·추론**: 앱은 소유권/Session·Job·기존 결과 확인과 상태 계산을 추가 수행하므로 정상 요청의 DB 작업이 많아질 수 있다. 측정된 지연/worker 증가의 정확한 원인별 비중은 profiling하지 않았다.
- **해석 주의**: 순서 역전 시 웹 최종 관찰 완료 p95약2.63초, 앱약2.88초다. 웹은 요약을 먼저 요청하고 앱은 마지막 결과를 기다린 뒤 요청하는 차이가 있으므로 “웹의 올바른 요약이 더 빨리 생성됐다”는 비교가 아니다. 모의 Summary 텍스트의 품질은 검사하지 않았다.

### 이번 변경·유지 계약·배포 전 확인

- 추가: `scripts/perf/` 비교 빌드/Java 실행기/Node AI·부하 driver/집계기/README, 이 결과 문서, 원시·집계 JSON. CURRENT_STATE/WORKLOG를 갱신한다.
- application runtime·기존 테스트·웹 원본은 이번 작업에서 변경하지 않았다. 공개 API/DTO/BaseResponse·retryCount·AI user_id=examId·S3/Redis/Billing 계약과 운영 설정을 유지했다.
- 기존 dirty AGENTS/runtime/다른 사용자 문서와 이전 시험 테스트 추가를 보존했다. 예상 밖의 제품 코드 변경은 없다. 실험용 테스트 데이터를 담은 임시 컨테이너는 실험 JVM 종료 시 정리하며 결과 JSON은 보존한다.
- 배포 필요 없음. 실제 AWS/Atlas/AI/Sentry를 호출하지 않았다. 운영 적용 성과로 보고하려면 별도 staging E2E·운영 환경/부하 검증이 필요하다.

## 6. 부록 — 전체 표와 근거

### 실행 조건별 상세 집계

시간은3회별 p95의 중앙값이며, 괄호는 최소–최대다. 각 시험 수는3회 합계다.

| 조건 | 버전 | 시험 완료 | submit p95 ms | 완료 p95 ms | worker 평균 중앙값 | worker p95 중앙값 |
| --- | --- | --- | --- | --- | ---: | ---: |
| 정상1시험/s | 웹 | 24/24 | 61.94 (61.69–62.09) | 1176.15 (1172.21–1176.55) | 0.69 | 2 |
| 정상1시험/s | 앱 | 24/24 | 71.72 (71.32–75.28) | 1213.65 (1211.07–1216.67) | 0.90 | 3 |
| 정상5시험/s | 웹 | 120/120 | 57.22 (57.01–57.46) | 1158.66 (1158.65–1159.56) | 2.79 | 5 |
| 정상5시험/s | 앱 | 120/120 | 63.19 (63.18–64.39) | 1167.34 (1166.78–1167.81) | 3.32 | 5 |
| 정상15시험/s | 웹 | 360/360 | 55.16 (54.94–55.26) | 1148.40 (1148.34–1149.81) | 7.82 | 10 |
| 정상15시험/s | 앱 | 360/360 | 61.43 (61.13–61.76) | 1163.37 (1163.05–1163.70) | 9.41 | 13 |
| 중복5시험/s | 웹 | 120/120 | 56.63 (56.55–56.73) | 1158.51 (1157.20–1158.51) | 5.35 | 8 |
| 중복5시험/s | 앱 | 120/120 | 61.09 (61.09–61.42) | 1164.04 (1161.65–1164.21) | 3.57 | 5 |
| 순서 역전1시험/s | 웹 | 12/12 | 58.63 (58.42–58.72) | 2633.82 (2629.77–2637.67) | 0.48 | 2 |
| 순서 역전1시험/s | 앱 | 12/12 | 72.97 (69.89–73.31) | 2884.88 (2884.00–2889.87) | 0.62 | 2 |

모든30개 실행에서 HTTP/Callback 오류0, 시험 관찰 deadline 초과0이었다. 단, 순서 역전 웹12건의 중간 완료 표시 정합성 문제는 별도 집계했다. generator 시작 지연 최대는 모든 조건에서 약1.34ms 이내였다. 각 조건의 polling 시간·thread 원시 sample·완료 시각·단일 요청 시간은 아래 JSON에 있다.

### 재현·검증 근거

- [원시30개 실행 결과](measurements/poc-comparison-2026-09-08.json)
- [3회 집계·버전·source digest](measurements/poc-comparison-2026-09-08.summary.json)
- [실행 조건/재현 명령/제외 범위](../../scripts/perf/README.md)
- [Java 실제 Controller/Service 구성](../../scripts/perf/java/ComparisonServer.java)
- [AI fixture·실제 HTTP 부하·완료 판정](../../scripts/perf/run-comparison.mjs)
- [통계 집계와 결과 무결성 assert](../../scripts/perf/summarize-comparison.mjs)
- [원본 소스 읽기 전용 컴파일 설정](../../scripts/perf/comparison.init.gradle)

`prepareComparison` 컴파일과 Node smoke를 수행한 뒤 스레드 sampler의 타입 불일치를 수정했다. 최초 smoke의 worker sample이0개라 해당 스레드 수치는 버리고, 본 실험은 sampler 오류/빈 sample이면 실패하도록 보강한 버전으로 새로 실행했다. main30개 결과를 smoke와 합치지 않았다. 최초 Java 컴파일의 SDK builder/Tomcat 계측 및 Lombok classpath 오류도 실행기에서만 수정했다.

본 실험은 성공 종료했고 집계기의30행·3회 반복·시험/요청 수·0–20 worker 범위 검증을 통과했다. 제품 회귀 테스트와 최종 문서/diff 검증 결과는 CURRENT_STATE/이번 WORKLOG에 함께 기록한다.
