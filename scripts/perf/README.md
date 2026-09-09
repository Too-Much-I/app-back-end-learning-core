# 웹 POC ↔ 앱 모의고사 흐름 로컬 비교

AI의 동기 응답/비동기 접수 순서만 대조하는 `--ai-timing` 옵션은 [이력·실험 조건](../../docs/codex/AI_SYNC_ASYNC_HISTORICAL_EVIDENCE.md)과 [결과](../../docs/codex/AI_SYNC_ASYNC_COMPARISON_RESULTS.md)를 따른다. 기존 default 비교 결과와 별도 JSON을 사용한다.

운영 서비스를 실행하는 도구가 아니다. 실제 Controller/Service 및 Spring Data repository를 조합한 **격리 비교용 실행기**다. 웹 원본은 읽기만 하며 출력은 앱의 `build/poc-comparison`에 둔다. 제품의 application.yml·환경변수·credential·component scan을 불러오지 않는다. S3 signing은 Mock, 다운로드/AI/Callback은 loopback fixture이며 외부 AI/S3 요청을 거부한다.

## 문항별 채점 ↔ 11문항 종료 후 채점

최종 측정·해석·보고서용 문구는 [비교 결과](../../docs/codex/GRADING_START_COMPARISON_RESULTS.md)를 참고한다.

별도 `run-grading-schedule.mjs`는 **같은 앱 백엔드**에서 모의 AI의 작업 시작 gate만 비교한다. 두 군 모두 문항별 submit·50ms 접수 응답을 유지하고, `after-eleven` 군은 시험 종료 및 11문항 접수를 모두 확인한 뒤 동일 FIFO worker pool에 작업을 넣는다. 실제 제품의 시험 완료 API·일괄 업로드·11문항 통합 LLM 프롬프트를 구현한 것이 아니다. 웹 소스는 이 실험에서 조회·컴파일하지 않는다.

```sh
./gradlew clean test -I scripts/perf/comparison.init.gradle prepareAppComparison
node --test scripts/perf/grading-schedule-model.test.mjs
node scripts/perf/run-grading-schedule.mjs --smoke
node scripts/perf/run-grading-schedule.mjs --output=docs/codex/measurements/grading-schedule-2026-09-08.json
node scripts/perf/summarize-grading-schedule.mjs docs/codex/measurements/grading-schedule-2026-09-08.json
```

- 실험 결과 JSON은 `wx`로 생성하여 기존 파일 덮어쓰기를 거부한다. 재실행 시 새 경로를 지정한다. smoke는 준비 확인용 압축 burst이며 본 결과에 포함하지 않는다.
- 준비·응답을 본뜬 가정 시간표는 Q1~11의 간격 `[90,90,75,75,18,18,33,63,18,33,105]`초, 합618초를20배 압축하여30.9초로 실행한다. Directions·질문 음성 길이·기기 처리·업로드 시간은 포함하지 않는다. 실제 배포 앱 시간표를 추출한 값이나 공식 전체 시험 시간이라는 주장이 아니다.
- 모의 문항 연산은 `[600,1000,1800,1300,800,1500,1100,2000,900,1600,2200]`ms(합14.8초), Summary300ms다. 양군 동일하며 타이머로 구현한다. 실제 AI provider 속도/품질/비용은 측정하지 않는다. HTTP/DB/Polling은20배 축소하지 않으므로 측정 시간을20배 곱해 운영 대기 시간으로 환산하지 않는다.
- 단일 시험/worker4, 6개 시험/worker4(시작 간격500ms), 단일 시험/worker16, Q5 제출 직후 중단2시험/worker4를 각각 양군3회 실행한다. 순서는 문항별→종료후 / 종료후→문항별 / 문항별→종료후다. JVM마다2시험 warm-up을 별도 실행한다. 중도 포기는 클라이언트가 나머지 문항을 제출하지 않는 모델이며 기존 queue/작업을 취소하지 않는다.
- 주지표는 마지막 답변 준비/최종 submit 시작 시점부터 COMPLETED·11문항 summary·모든 Callback ACK 확인까지다. 최종 submit 성공 응답 이후 대기 시간도 별도 기록한다. Polling200ms, 최종 답변부터60초 관찰 deadline이다. 단일 시험 조건의 회차별 p95는 한 표본 값 자체이므로 통계적 tail 추정이 아니다.
- 작업 pool4/16은 가정값이며 Summary와 Callback ACK 대기도 같은 slot을 사용한다. `aiPeakWaiting`은 **실행 자격을 얻은 queue**의 최대 길이로, 종료 전 보류 문항은 포함하지 않는다. 보류 문항은 별도 sample과 `heldNotExecuted`로 기록한다. 영속 queue/재시작/재시도/장애는 재현하지 않는다.
- 양군 모두 AI HTTP 접수 수는 같다. 중도 포기 비용 비교에는 모의 연산 시작 횟수를 사용하며 실제 요금으로 환산하지 않는다. 종료 전 이미 완료된 문항 결과의 제품 가치는 이번 실험에서 평가하지 않는다.
- 24개 실행의60개 흐름 중48개는 완주,12개는 의도적 중도 포기 조건이다. 전체588submit과48Summary가 기대값이다. 중도 포기를 실패 시험으로 합산하지 않는다. 기존 비교의 CPU/전체 wiring/외부 시스템 제외 경계도 동일하게 적용한다.
- `clean`은 기존 `build/` 로그·임시 classpath·테스트 보고서를 재생성한다. 이전 실험의 `docs/codex/measurements/` 원시 JSON은 보존한다.

## 실행

```sh
./gradlew -I scripts/perf/comparison.init.gradle -PcomparisonWebRoot=/Users/msde76/IdeaProjects/web-back-end prepareComparison
node scripts/perf/run-comparison.mjs --smoke
node scripts/perf/run-comparison.mjs --output=docs/codex/measurements/poc-comparison-2026-09-08.json
node scripts/perf/summarize-comparison.mjs docs/codex/measurements/poc-comparison-2026-09-08.json
```

Docker·Gradle cache 접근이 필요하다. MongoDB7.0.14/Redis7.2-alpine Testcontainers를 생성하고 JVM 종료 시 이 실험이 생성한 컨테이너만 종료/제거한다. 운영 컨테이너·볼륨은 건드리지 않는다. 프로세스 강제 종료 시 실험 컨테이너 정리 상태를 확인한다. 결과 경로는 재실행하면 덮어쓰므로 과거 결과 보존이 필요하면 새 `--output` 경로를 지정한다.

## 범위와 자원

- 실제 공개 submit/status/summary/feedback callback Controller와 해당 Service, Mongo repository를 실행한다. 자동 스캔 대신 비교용 composition root로 연결하므로 production Spring wiring 전체를 검증하지 않는다.
- 로그인/JWT·Identity/Billing·서비스 간 네트워크·Sentry·회원 병합·예약 Transaction·전체 Summary API 내용 품질은 비교하지 않는다. 사용하지 않는 BillingExamCreationSaga/ExamReadService/ModelAnswerCatalogService는 Mock이다.
- 시험 생성/권리 부여/음성 업로드는 측정 전에 fixture로 준비한다. 앱은 고정 test user 소유권 검증을 유지한다. 실제 사용자 활성 시험 제약을 통한 다사용자 생성 부하는 포함하지 않는다.
- 동일 JVM heap512MiB, `ActiveProcessorCount=2`, Tomcat20 worker로 순차 실행한다. **ActiveProcessorCount는 OS CPU hard limit이 아니다.** 동일 노트북의 JVM 설정 비교이며 CPU 사용량을 완전히 격리한 배포 환경 비교가 아니다.
- Mongo CPU1/메모리512MiB, Redis CPU0.5/메모리128MiB는 Docker 제한이다. AI/load driver는 별도 Node 프로세스다. 공통 examId 조회 index를 사용하며 production index 전체를 복제하지 않는다.
- 웹/앱 모두 같은 Spring Boot3.4.2 및 앱 testRuntimeClasspath 라이브러리를 사용한다. 웹 원본 배포 이미지 전체의 성능 비교가 아닌 애플리케이션 로직 차이의 제한된 비교다.

## 측정 정의

- 정상: 초당1/5/15시험 시작률, 조건당8초, 각3회. 시행 순서 web→app / app→web / web→app, JVM 시작마다4시험 warm-up. 짧은 탐색 실험이며 장시간 steady-state/SLO 한계 측정이 아니다.
- 각 시험의11문항을30ms 간격으로 제출한다. 실제 시험 시간을 재현하지 않고 제출 burst를 압축한다. 따라서 시험 시작률을 실제 동시 사용자 수로 환산하지 않는다.
- AI 접수 지연50ms, 문항 Callback 지연500ms, Summary Callback100ms. 실제 AI 추론·비용·품질이나 제한된 worker queue를 모델링하지 않는다. 의도적으로 AI 멱등 처리를 하지 않아 백엔드가 내보낸 중복 HTTP 요청 수를 센다.
- 중복: 각 문항 같은 retryCount=0을2번 제출. 초당5시험×8초×3회/버전. 정상 입력에 대한 일반적인 비용 절감률로 확대하지 않는다.
- 순서 역전: 1번 문항 Callback만2500ms 지연, 나머지500ms. 초당1시험×4초×3회/버전. AI fixture는 Summary 요청을 받으면 문항 완료를 별도로 기다리지 않는다. backend의 조기 요약/완료 방어를 검증하는 조건이며 실제 AI가 동일하게 처리한다는 가정은 하지 않는다.
- Polling200ms. API status=COMPLETED인데 summary.totalSolvedQuestions가11이 아니면 해당 시험을 `premature`로1번 센다. 시험 완료는 COMPLETED·11문항 Summary 조회 및11개 문항/요약 Callback 성공 응답을 모두 확인한 시점이다.
- 최종 완료 p95는 예정된 시험 시작 시각부터 계산한다. 늦게 보낸 요청을 감추지 않도록 generatorLag도 기록한다. 각 시험 관찰 deadline은 실제 시작부터15초다.
- `earlySummary`는 모든 문항 callback ACK를 모으기 전에 summary 요청을 받은 횟수다. DB commit과 ACK 사이 차이가 있으므로 이것만으로 제품 오류라고 단정하지 않는다. 보고서의 주된 정합성 지표는 `premature`다.
- Tomcat executor activeCount를20ms마다 샘플링한다. blocked AI 대기 스레드만이 아니라 submit/status/callback을 처리하는 전체 worker다. 별도 summary executor는 포함하지 않는다.
- 전체 elapsed에는 drain 및300ms 정리 대기가 포함된다. completePerMinuteIncludingDrain은 이번 유한 배치의 관측 처리량이지 지속 가능한 최대 처리량이 아니다. completeWithinOfferWindow도 초기 ramp-up 영향을 받는다.
- 통계 요약은3회별 p95의 중앙값과 범위다. 합쳐진 모든 요청의 p95나 신뢰구간과 혼동하지 않는다. 표본이 적은 조건의 시험 p95는 사실상 최대값에 가깝다.
- Java console 로그는 양쪽 모두 ERROR 수준이다. 구조화 로깅 비용 비교가 아니다.

원시 결과 JSON에 요청별 시간·시험별 완료 시간·thread samples를 보존한다. compile 산출물과 로그는 build 아래에서 후속 실행 시 바뀔 수 있다. 실행 이후 source hash는 동시 편집 방지나 변경 전 snapshot 보존을 대신하지 않는다.
