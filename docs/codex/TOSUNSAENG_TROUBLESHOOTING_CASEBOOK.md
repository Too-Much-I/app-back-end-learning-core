# 토선생 트러블슈팅 사례 조사

- 조사일: 2026-09-07
- 코드 확인 기준: Learning Core `develop@cb5f6ee`
- 조사 자료: 앱 관련 채팅 4개 작업의 선택된 대화 페이지, Learning Core·Identity·Billing WORKLOG, Learning Core 수정 커밋과 현재 소스·테스트
- 추가 자료: 팀원 JINI의 토선생 회고 3편. 팀 운영 장애 TS-23·24를 보강했으며 본인 기여·복구 결과는 별도로 확인해야 한다. [회고 대조 상세](TOSUNSAENG_TEAM_RETROSPECTIVE_BACKEND_SUPPLEMENT.md).
- 설계·정책 변화는 [구현 후 수정·설계 진화 사례집](TOSUNSAENG_DESIGN_EVOLUTION_CASEBOOK.md)에 별도로 정리했다. EV 18개에는 기존 TS와 겹치는 변경, 후속 확장, 구현 전 결정도 있어 TS 24개와 독립 사건 수로 합산하지 않는다.
- 읽기 안내: 1~4절을 먼저 읽고, 관심 있는 사례만 5절에서 선택한다. 전체 후보·근거 목록은 6절에 둔다.

## 1. 5줄 결론

1. **[확인]** 이미 포트폴리오에 쓸 트러블슈팅 경험이 충분하다. 파트 점수 오류는 사용자 문제 제기·정책 결정·수정·검증까지 연결된다. 근거: [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:2039).
2. **[확인]** AI 재시도의 구체적 출발점은 빈 종합 피드백이며, Summary 단독 재생성과 세대 검증으로 발전했다. 근거: [복구 계획](/Users/msde76/app-back-end-learning-core/docs/codex/FEEDBACK_GENERATION_RECOVERY_PLAN.md), 채팅 C1.
3. **[확인]** CI의 간헐적 동시성 테스트 실패와 환경변수 우선순위 충돌은 원인과 수정 결과가 명확한 사례다. 근거: [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:1657), [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:2102).
4. **[확인]** Mongo 트랜잭션 취소·unknown commit·모범답안 사전 노출은 리뷰에서 발견해 수정한 결함이다. 근거: [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:6441), [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:8659), [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:1284).
5. **[권장]** 본문은 아래 추천 3개로 시작하고 나머지는 선택용 부록으로 활용한다. 사례별 코드·테스트·한계는 5절, 전체 24개 후보와 채팅 출처는 6절에서 연결한다. 팀원 회고의 운영 장애 2개는 본인 수정 사례와 구분한다. [회고 대조](TOSUNSAENG_TEAM_RETROSPECTIVE_BACKEND_SUPPLEMENT.md).

## 2. 내가 반드시 읽어야 하는 내용

추천하는 첫 세 가지는 **빈 종합 피드백 복구(TS-01)**, **Mongo 트랜잭션 hotfix(TS-08)**, **CI 동시성 테스트 실패(TS-05)**다. 각각 사용자 경험, DB 정합성, 재현·테스트 능력을 보여준다. 설명하기 쉬운 사례부터 준비하려면 **파트 점수 집계 오류(TS-04)**와 **CI 환경변수 충돌(TS-06)**을 먼저 골라도 좋다.

작업 기록은 실제 문제와 수정 과정을 복원하는 데 유용하다. 다만 기능 계획, 예방 설계, 리뷰 finding, 발생 로그와 검증 성공은 서로 다른 증거다. 여기서는 각 사례 첫 줄에 발견 경위를 표시했다. “운영 중 발생했다”는 문장은 운영 증거가 있는 경우에만 사용한다.

읽을 때는 “어디서 증상이 보였나 → 무엇을 비교해서 원인을 좁혔나 → 무엇을 고쳤나 → 같은 실패를 어떻게 다시 검증했나” 네 질문에 답할 수 있으면 된다. 코드 전체를 외우기보다 실제 사례 하나를 이 순서로 설명하는 것이 우선이다.

팀원 회고에는 **특정 시험지의 S3 음성 누락(TS-23)**과 **약 10분 채점 대기 후 이탈(TS-24)**이 나온다. 사용자 영향이 구체적인 운영 소재지만, 글에 없는 복구 성공이나 본인 기여까지 확정한 것은 아니다. 기존 16개 상세 수정 사례·6개 진단 후보에 팀 보고 사례 2개를 추가한 총 24개 후보이지, 독립 운영 장애 24건이라는 뜻은 아니다.

## 3. 내가 결정해야 하는 사항

- **본문 사례 선택:** 추천 3개 또는 이해하기 쉬운 TS-04·05·06 조합 중 본인이 설명할 수 있는 항목을 선택한다.
- **본인 역할:** 문제를 처음 발견한 사람, 정책을 결정한 사람, 코드를 작성한 사람, 검증한 사람을 실제 기여대로 적는다. AI와 함께 분석·수정했다면 그 과정에서 본인이 내린 결정과 검증을 설명한다.
- **수치 사용:** 아래 과거 테스트 수는 날짜별 검증 증거다. 응답시간·장애율·비용 개선은 별도 측정치가 있을 때 채운다.

## 4. 주요 위험과 미확인 사항

- 채팅 전체를 자동으로 모두 읽은 것은 아니다. 접근 가능한 작업 목록과 관련 대화 페이지를 선택 조회했고, 나머지는 저장된 WORKLOG와 커밋으로 보완했다. 삭제되거나 연결되지 않은 대화는 조사 범위 밖이다.
- WORKLOG에 같은 작업의 종료 기록과 중복 기록이 있어 사건 수로 중복 계산하지 않았다. TS-02·03은 동일 TMI-25 수정 묶음의 서로 다른 원인이다.
- 커밋 제목만으로 분류하지 않았다. 예를 들어 `98730c9`는 점수 오류라는 제목이지만 실제 변경은 CI workflow이고, `a2c4fb6`에는 점수 수정과 Summary 복구가 함께 들어 있다.
- 아래 테스트 결과는 **당시 실행 기록**이다. 이번 문서 조사에서 애플리케이션 테스트를 새로 실행한 결과가 아니다.
- 09-05 Docker 부재 기록 뒤 09-07에는 실제 Mongo 테스트 성공 기록이 추가됐다. 이전 시점의 실패를 현재의 영구 차단 상태로 쓰지 않는다.
- 운영 수치, 사용자 피해, 보안 유출, 실제 교착 발생을 추정해 사실처럼 적지 않는다. 외부 IAM·Sentry·RevenueCat 진단은 후속 성공 증거가 없는 부분을 미확인으로 남겼다.

## 5. 현재 작업과 직접 관련된 설명

### 5.1 구현과 검증 근거가 있는 상세 사례

#### TS-01. 빈 종합 피드백을 성공으로 처리하던 문제

- 발견 경위: **사용자 제기 + 코드 수정**
- 시점·관련 이슈: 2026-08-11 기록, 08-17 커밋 / TMI-25

**증상:** 문항별 채점은 끝났지만 AI의 `partFeedback`이 빈 객체인 경우에도 시험을 완료할 수 있었다.

**원인을 좁힌 과정:** 채팅에서 빈 피드백의 실패 처리와 Summary 단독 재생성을 요청한 내용을 확인했다. Callback의 유효성 검사와 Summary 시작 조건을 추적하니 빈 Map 저장 및 Job 상태만으로 완료를 인정하는 공백이 있었다.

**수정:** 빈/null 피드백을 `FEEDBACK_GENERATION_FAILED`로 기록하고 실제 필수 문항 결과가 모두 있을 때 Summary만 재생성한다. 이 실패에 대한 사용자 retry에서만 generation을 증가시키고, 같은 세대의 통신 재시도는 같은 멱등 키를 사용한다.

**검증:** 반복 generation·동시 retry·늦은 Callback·결과 없는 COMPLETED Job 복구와 오류 응답 테스트를 추가했다. 당시 전체 351개 테스트 성공 기록이 있다.

**결과와 한계:** 기존 정상 문항을 다시 채점하지 않고 실패 단계만 복구한다. 과거 저장된 빈 Summary의 운영 데이터 보정과 실제 AI echo 검증은 별도다. 운영 장애 건수나 비용 절감률은 미측정이다.

**근거:** [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:1994), [복구 계획과 계약](/Users/msde76/app-back-end-learning-core/docs/codex/FEEDBACK_GENERATION_RECOVERY_PLAN.md), [복구 서비스](/Users/msde76/app-back-end-learning-core/src/main/java/web/tosunsaeng/domain/exams/application/ExamGradingService.java), [오류 응답 테스트](/Users/msde76/app-back-end-learning-core/src/test/java/web/tosunsaeng/domain/exams/api/FeedbackGenerationFailureApiContractTest.java); 커밋 `a2c4fb6`. 채팅 C1 참조.

#### TS-02. 이전 AI 요청의 늦은 실패가 새 재시도 상태를 덮는 경쟁 조건

- 발견 경위: **리뷰 발견 + 재현 테스트 + 수정**
- 시점·관련 이슈: 2026-07-29 / TMI-25

**증상:** 첫 요청이 지연되는 동안 두 번째 시도를 시작했는데, 첫 요청의 늦은 실패가 두 번째 PROCESSING을 FAILED로 바꿀 수 있었다.

**원인을 좁힌 과정:** 실패 처리 함수가 자신이 선점한 시도를 검증하지 않고 최신 Job을 다시 읽어 저장했다. `시도 1 대기 → 시도 2 선점 → 시도 1 실패` 순서로 문제를 재현했다.

**수정:** claim에 Job·dispatchAttempt·선점 시각을 고정하고 해당 PROCESSING 시도에만 조건부 실패 update를 수행한다. 이미 다음 시도로 이동했으면 갱신하지 않는다.

**검증:** Question과 Summary 모두에서 뒤늦은 실패 후 최신 PROCESSING/2와 실패 정보 없음이 유지되는 테스트를 추가했다. 당시 전체 142개 성공 기록이다.

**결과와 한계:** 재시도 자체가 새 작업의 상태를 훼손하지 않도록 보강했다. 실제 운영에서 이 경합이 발생했다는 로그를 확인한 사례는 아니며, 리뷰와 재현으로 예방한 결함이다.

**근거:** [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:573), [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:591), [Question claim](/Users/msde76/app-back-end-learning-core/src/main/java/web/tosunsaeng/domain/exams/application/QuestionDispatchClaim.java), [Job 저장소](/Users/msde76/app-back-end-learning-core/src/main/java/web/tosunsaeng/domain/exams/domain/repository/QuestionGradingJobRepository.java); 관련 커밋 `3748ddf`.

#### TS-03. 마지막 문항 Callback 안에서 Summary를 동기로 호출하던 병목

- 발견 경위: **리뷰 발견 + 구조 수정**
- 시점·관련 이슈: 2026-07-29 / TMI-25

**증상:** AI가 마지막 문항 Callback을 보내고 응답을 기다리는 중 Learning Core가 같은 AI에 Summary를 요청하면, AI 처리 자원이 부족할 때 상호 대기나 긴 timeout으로 이어질 수 있었다.

**원인을 좁힌 과정:** Callback 완료 경로가 Summary HTTP 완료까지 기다리는 호출 관계를 확인했다. 일반적인 최초 동기 채점의 Thread 고갈 경험과는 다른, Callback 내부의 추가 결합 문제다.

**수정:** Callback은 Summary PENDING 확보와 작업 예약까지 처리하고, 크기가 제한된 별도 executor가 Job을 선점한 뒤 Summary HTTP를 호출하도록 분리했다. queue 거절 때는 PENDING을 보존하고 connect/read timeout을 설정했다.

**검증:** 중복 예약에서 HTTP 단일 호출, queue rejection 시 PENDING 보존과 Callback/retry 실행 조건 분리를 검증했다. TS-02와 같은 수정 묶음의 142개 테스트 기록이다.

**결과와 한계:** Callback 응답 경로에서 Summary 접수 대기를 분리했다. 실제 deadlock 발생을 증명한 자료는 없으므로 병목·교착 위험 제거로 표현한다. 문항 submit의 AI 접수 호출은 여전히 blocking이다.

**근거:** [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:591), [Summary 예약기](/Users/msde76/app-back-end-learning-core/src/main/java/web/tosunsaeng/domain/exams/application/SummaryDispatchScheduler.java), [executor 설정](/Users/msde76/app-back-end-learning-core/src/main/java/web/tosunsaeng/global/config/GradingConfig.java), [예약기 테스트](/Users/msde76/app-back-end-learning-core/src/test/java/web/tosunsaeng/domain/exams/application/SummaryDispatchSchedulerTest.java).

#### TS-04. 재답변 점수가 최초 시험의 파트 점수에 누적되던 오류

- 발견 경위: **사용자 제기 + 원인 확인 + 수정**
- 시점·관련 이슈: 2026-08-17 / 별도 Jira 없음

**증상:** 사용자가 재답변한 뒤 파트별 점수가 함께 더해지는 현상을 제기했다.

**원인을 좁힌 과정:** summary 조회가 모든 ExamResult에서 questionNumber·score만 검사하고 retryCount를 필터링하지 않았다. totalSolvedQuestions는 이미 0회차만 집계했고 totalScore는 별도 Summary 값이어서 문제 범위를 partScores로 좁혔다.

**수정:** 사용자가 선택한 최초 응시 정책에 맞춰 `retryCount == 0`만 파트 점수 합산에 포함했다.

**검증:** 최초 5점·재답변 9점·회차 null 4점 fixture에서 파트 점수가 5점인지 검사했다. 당시 전체 352개 성공 기록이 있고 현재에도 해당 회귀 테스트가 존재한다.

**결과와 한계:** 파트별 점수와 최초 시험이라는 제품 의미를 맞췄다. `retryCount=null` legacy 결과도 제외되므로 이전 데이터의 의미 확인이 필요하다.

**근거:** [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:2039), [점수 집계](/Users/msde76/app-back-end-learning-core/src/main/java/web/tosunsaeng/domain/exams/application/ExamServiceImpl.java:681), [집계 회귀 테스트](/Users/msde76/app-back-end-learning-core/src/test/java/web/tosunsaeng/domain/exams/application/ExamOwnershipServiceTest.java:372); 커밋 `a2c4fb6`. 채팅 C2에 최초 문제 제기와 정책 결정이 남아 있다.

#### TS-05. 로컬에서는 통과하고 CI에서는 간헐적으로 실패한 동시성 테스트

- 발견 경위: **실제 CI 실패 + 재현 분석 + 수정**
- 시점·관련 이슈: 2026-08-07 / 별도 Jira 없음

**증상:** `concurrentStartsLeaveExactlyOneActiveSessionAndNeverReuseExamId`가 GitHub Actions에서 `TooFewActualInvocations`로 실패했다.

**원인을 좁힌 과정:** 최종 활성 Session 1개와 서로 다른 examId는 이미 충족했지만 테스트가 insert 3회를 강제했다. 스레드가 snapshot을 읽는 시점에 따라 정상적으로 2회 또는 3회 호출될 수 있었다.

**수정:** 동시성 테스트는 최종 불변식을 검증하도록 바꾸고, 첫 insert가 반드시 DuplicateKey를 던지는 별도 결정적 테스트로 충돌 재시도 경로를 검증했다.

**검증:** 문제 테스트를 `--rerun-tasks`로 10회 반복해 10/10 성공했고 당시 전체 296개 테스트가 성공했다. sleep·테스트 비활성화로 회피하지 않았다.

**결과와 한계:** 정상적인 스케줄 차이를 결함으로 오판하던 테스트를 안정화했다. Mock 기반 시험이므로 실제 Mongo 동시성 검증과 역할을 구분한다.

**근거:** [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:1657), [동시성·충돌 테스트](/Users/msde76/app-back-end-learning-core/src/test/java/web/tosunsaeng/domain/exams/application/ExamSessionManagerTest.java); 커밋 `2c01fcb`.

#### TS-06. 배포 환경변수가 테스트 설정을 덮어써 CI가 실패한 문제

- 발견 경위: **실제 CI 실패 + 원인 확인 + 수정**
- 시점·관련 이슈: 2026-08-17 / 별도 Jira 없음

**증상:** CI가 exit code 1로 실패했고 화면에는 Node.js·setup-java 사용 중단 경고도 함께 표시됐다.

**원인을 좁힌 과정:** 실패 step과 assertion을 추적해 직접 원인이 Sentry release 설정 테스트임을 확인했다. job 전역의 commit SHA release가 Spring test profile 값을 덮어썼다.

**수정:** Run tests step에서만 test release를 지정하고 배포 step의 commit SHA release는 유지했다. action 버전 경고는 별도로 v5 갱신했다.

**검증:** 같은 환경변수를 주입해 전체 352개 테스트 성공과 YAML 파싱을 확인했다. 해당 시점에는 AWS 배포를 재실행하지 않았다.

**결과와 한계:** 경고 메시지와 실제 실패 원인을 구분하고 환경변수 범위를 테스트 단계에 맞춰 격리했다. 전체 배포 성공까지 입증한 사례로 확대하지 않는다.

**근거:** [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:2102), [staging workflow](/Users/msde76/app-back-end-learning-core/.github/workflows/deploy-staging.yml:45); 커밋 `98730c9`. 이 커밋의 제목은 점수 수정이지만 실제 diff는 workflow 수정이다. 채팅 C2 참조.

#### TS-07. 없는 MongoDB 컬렉션 때문에 read-index dry-run이 중단된 문제

- 발견 경위: **실제 스크립트 실패 + 수정**
- 시점·관련 이슈: 2026-08-04 / TMI-61

**증상:** 아직 생성되지 않은 exam_summaries 컬렉션에서 인덱스를 조회하자 `NamespaceNotFound`로 사전검사가 중단됐다.

**원인을 좁힌 과정:** getIndexes의 Promise rejection이 동기 try/catch 밖에서 발생했다. 신규 환경에서 컬렉션이 없는 상태와 인증·연결 실패를 구분할 필요가 있었다.

**수정:** helper와 호출부에 async/await를 적용하고 code 26 또는 NamespaceNotFound만 빈 인덱스 목록으로 처리했다. 인증·권한·네트워크 오류는 원래 오류를 전파한다.

**검증:** missing collection 두 오류 표현, dry-run 무쓰기, apply 인덱스 생성과 다른 오류 전파 테스트 8개를 추가했다. 당시 전체 Node 76개 성공 기록이다.

**결과와 한계:** 신규 환경에서도 생성 예정 인덱스를 출력할 수 있게 했다. 당시 실제 운영 Mongo apply는 수행하지 않았다.

**근거:** [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:1411), [인덱스 스크립트](/Users/msde76/app-back-end-learning-core/scripts/mongodb/create-exam-read-indexes.js:144), [회귀 테스트](/Users/msde76/app-back-end-learning-core/scripts/mongodb/create-exam-read-indexes.test.js); 커밋 `3a1cf9a`, PR #19.

#### TS-08. DuplicateKey를 catch해도 Mongo 트랜잭션이 살아나지 않는 문제

- 발견 경위: **병합 후 리뷰 발견 + hotfix**
- 시점·관련 이슈: 2026-09-01 / TMI-118

**증상:** Summary insert 중 중복 키 오류를 잡고 같은 트랜잭션에서 Job 완료와 Session·Outbox 변경을 계속 수행했다.

**원인을 좁힌 과정:** Mongo는 중복 키 오류로 트랜잭션을 취소한다. Java catch는 이를 복구하지 않으며, 내부 coordinator가 참여 트랜잭션의 예외를 삼켜도 outer rollback-only 상태가 남았다.

**수정:** 중복·경합 오류를 트랜잭션 밖으로 전파하고 종료 후 새 트랜잭션에서 Summary·Job·Session·Outbox 전체를 최대 3회 재시도한다. Job 완료 실패는 rollback-only로 처리하고 기존 Summary identity도 검증한다.

**검증:** 중복 insert 후 새 트랜잭션 재실행, Job false 시 rollback, unknown commit wrapper와 owner 충돌, 내부 경합 전파 등 신규 5개 테스트와 당시 전체 444개 성공 기록이 있다.

**결과와 한계:** 부분 성공처럼 보이는 흐름을 원자적인 전체 작업으로 정리했다. 최초 hotfix 검증은 Mock 기반이며 운영 장애 발생 기록이 아닌 리뷰 발견 사례다. 다른 UserMerged의 unknown commit 처리와 재시도 정책을 동일시하지 않는다.

**근거:** [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:6441), [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:6458), [Summary 트랜잭션](/Users/msde76/app-back-end-learning-core/src/main/java/web/tosunsaeng/domain/exams/attemptgroup/application/AttemptGroupSummaryCompletionService.java), [hotfix 테스트](/Users/msde76/app-back-end-learning-core/src/test/java/web/tosunsaeng/domain/exams/attemptgroup/application/AttemptGroupSummaryCompletionServiceTest.java); 커밋 `4781723`, PR #26.

#### TS-09. Part 4 결과 화면에서 질문 문장이 빠진 문제

- 발견 경위: **응답 누락 확인 + 수정**
- 시점·관련 이슈: 2026-08-25 / 별도 Jira 없음

**증상:** Part 4 결과 상세에는 표가 나오지만 해당 질문 문장이 제공되지 않았다.

**원인을 좁힌 과정:** 시험 생성·prompt·결과 상세의 세 변환 경로를 대조했다. 앞의 두 경로는 공통 converter에서 text를 채우지만 결과 상세 전용 converter만 이를 생략했다.

**수정:** 결과 상세 converter에 기존 Question.question을 기존 DTO의 text로 매핑하는 한 줄을 추가했다.

**검증:** 세 경로의 text와 tableContext 회귀 테스트를 보강했고 당시 전체 352개 테스트 성공 기록이다.

**결과와 한계:** 이미 존재하는 응답 필드를 활용해 앱이 질문과 표를 함께 표시할 수 있게 했다. 원본 catalog의 question 자체가 null이면 여전히 생략되므로 데이터와 변환 경로를 모두 확인해야 한다.

**근거:** [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:3053), [Converter](/Users/msde76/app-back-end-learning-core/src/main/java/web/tosunsaeng/domain/exams/converter/ExamConverter.java), [API 계약 테스트](/Users/msde76/app-back-end-learning-core/src/test/java/web/tosunsaeng/domain/exams/api/ExamReadApiContractTest.java); 커밋 `514fb49`.

#### TS-10. 제출·채점 전에 모범답안 음성이 노출되던 결함

- 발견 경위: **보안 리뷰 발견 + 수정**
- 시점·관련 이슈: 2026-08-04 / 별도 Jira 없음

**증상:** 시험 소유자가 아직 제출하지 않은 문항 결과 API를 호출해 모범답안 음성 URL과 단어 정보를 얻을 수 있었다.

**원인을 좁힌 과정:** 소유권 검증은 통과하지만 matching ExamResult 존재 여부와 무관하게 buildModelAnswer가 실행됐다. 소유권과 학습 단계별 공개 권한은 다른 조건이었다.

**수정:** 해당 회차 결과가 존재할 때만 모범답안을 조립하고 결과가 없으면 catalog 조회·Presign·단어 시퀀스 조회를 건너뛴다.

**검증:** 제출 전·없는 회차·처리 중 결과 없음에서 미노출 및 Presigner 0회 테스트 3개를 추가했다. 당시 전체 248개 성공 기록이다.

**결과와 한계:** 사용자 소유 리소스에서도 답안 공개 시점을 통제했다. 실제 사용자의 답안 사전 열람이 발생했다고 주장할 자료는 없다.

**근거:** [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:1284), [모범답안 검증 테스트](/Users/msde76/app-back-end-learning-core/src/test/java/web/tosunsaeng/domain/exams/application/ExamQuestionModelAnswerTest.java); 관련 커밋 `92c01ed`, `b57a64c`.

#### TS-11. Sentry에서 예외 위치를 잃는 문제와 중복 수집 위험

- 발견 경위: **관측성 리뷰 발견 + 수정**
- 시점·관련 이슈: 2026-08-10~11 / 별도 Jira 없음

**증상:** 예외 메시지에 민감값이 있을 수 있어 captureMessage를 쓰자 원본 stack이 없어 장애 발생 위치를 찾기 어려웠다. 명시적 수집과 Logback 자동 수집을 함께 켜면 중복 이벤트 위험도 있었다.

**원인을 좁힌 과정:** 전역 Advice·resolver·Logback·SDK scope와 최종 transport 경로를 대조하고 당시 사용 SDK의 동작을 확인했다.

**수정:** captureException으로 예외 타입·stack을 보존하면서 메시지와 민감 context를 최종 sanitizer에서 제거했다. 예상 밖 예외 수집 경로를 통제하고 호출별 scope를 격리했다.

**검증:** recording transport에서 실제 직렬화 event/envelope의 민감 marker 부재, stack 보존, handled/unhandled 수집 건수, 4xx 미수집과 연속 요청 scope 격리를 검증했다.

**결과와 한계:** 장애 위치 분석과 데이터 최소 수집을 함께 구현했다. 실제 정보 유출이나 중복 Issue 발생을 확인한 사례가 아니라, 기존 관측 한계와 수집 위험을 개선한 사례다.

**근거:** [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:1934), [운영 보완 계획](/Users/msde76/app-back-end-learning-core/docs/codex/SENTRY_PRODUCTION_HARDENING_PLAN.md), [정제기](/Users/msde76/app-back-end-learning-core/src/main/java/web/tosunsaeng/global/sentry/SentryEventSanitizer.java), [최종 수집 검증](/Users/msde76/app-back-end-learning-core/src/test/java/web/tosunsaeng/global/sentry/SentryPipelineIntegrationTest.java); 커밋 `3b30e30`, 채팅 C1.

#### TS-12. DB commit 결과가 불명확할 때 사용자 병합 응답을 확정하지 못하는 문제

- 발견 경위: **리뷰 발견 + 후속 수정**
- 시점·관련 이슈: 2026-09-05 / TMI-125

**증상:** UserMerged 처리에서 Mongo commit 결과가 불명확하면 이미 반영됐는지 판정하지 못하고, Spring wrapper 예외에 따라 500이 반환될 수 있었다.

**원인을 좁힌 과정:** 기존 구현은 blind mutation replay는 막았지만 unknown commit에서 inbox를 재조회하지 않았다. advice도 모든 트랜잭션 wrapper를 처리하지 않았다.

**수정:** 트랜잭션 밖에서 eventId와 digest로 inbox를 제한 시간 재조회해 동일 event 완료는 204, 상충 내용은 409, 미확정은 503과 Retry-After로 수렴한다. wrapper 예외도 전용 응답에 매핑한다.

**검증:** 동일 digest·다른 digest·조회 불가 테스트와 replica-set suite를 보강했다. 09-05 전체 496개 성공, 당시 Docker 부재 기록이 있고 09-07에는 후속 전체 Mongo suite 성공 기록이 있다.

**결과와 한계:** 이미 성공한 병합을 재실행할 위험과 불확실한 상태의 무조건 성공 응답을 피한다. 실제 네트워크 단절을 포함한 운영 장애 주입 증명 범위는 별도 확인해야 한다.

**근거:** [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:8659), [후속 수정 계획](/Users/msde76/app-back-end-learning-core/docs/codex/TMI-125_FOLLOWUP_PRODUCTION_SAFETY_FIX_PLAN.md), [병합 consumer](/Users/msde76/app-back-end-learning-core/src/main/java/web/tosunsaeng/domain/usermerge/application/UserMergedConsumerService.java), [unknown commit 테스트](/Users/msde76/app-back-end-learning-core/src/test/java/web/tosunsaeng/domain/usermerge/application/UserMergedConsumerServiceTest.java); 커밋 `c1fc803`, PR #29.

#### TS-13. 외부 응답과 기존 데이터의 검증 누락을 발견한 사례

- 발견 경위: **리뷰 발견 + 수정**
- 시점·관련 이슈: 2026-09-05 / TMI-125, 관련 TMI-122

**증상:** phone continuation에서 잘못된 attemptGroupId를 저장할 수 있었고, 소유권 migration이 Session 없는 결과나 owner 불일치를 사전에 잡지 못했다.

**원인을 좁힌 과정:** 일반 reserve는 UUID 검증이 있으나 phone discovery만 opaque text를 허용했다. migration은 개별 필드·index를 검사했지만 컬렉션 간 참조 정합성을 검사하지 않았다.

**수정:** client decode와 durable operation 저장 직전 UUID v4 검증을 추가했다. migration에는 $lookup 기반 orphan·owner mismatch 건수 검사를 넣고 한 건이라도 있으면 dry-run/apply를 차단했다.

**검증:** invalid discovery와 데이터 사전검사 테스트를 추가했다. 당시 Node migration 7개와 전체 Java 496개 성공 기록이다.

**결과와 한계:** 잘못된 외부 값과 오염된 기존 데이터를 신규 상태로 확산시키지 않게 했다. 실제 운영 데이터 오염을 발견·정리했다는 주장은 하지 않는다.

**근거:** [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:8659), [시험 생성 Saga](/Users/msde76/app-back-end-learning-core/src/main/java/web/tosunsaeng/domain/exams/application/BillingExamCreationSaga.java), [병합 사전검사](/Users/msde76/app-back-end-learning-core/scripts/mongodb/user-merged-prepare.js), [사전검사 테스트](/Users/msde76/app-back-end-learning-core/scripts/mongodb/user-merged-prepare.test.js); 커밋 `c1fc803`.

#### TS-14. migration 도중 완료된 시험이 다시 활성화될 수 있는 경쟁 조건

- 발견 경위: **리뷰 발견 + 수정**
- 시점·관련 이슈: 2026-07-30 / TMI-31

**증상:** legacy Session을 보정하는 중 다른 writer가 완료 결과를 저장하면, 오래된 사전조회에 기반한 migration이 완료된 시험을 활성화할 수 있었다.

**원인을 좁힌 과정:** 사전검사와 apply 사이에 Session 및 완료 증거가 바뀔 수 있었고, 여러 컬렉션을 읽는 절차를 하나의 조건부 update만으로 원자화할 수 없었다.

**수정:** legacy writer 중지 전제, 활성화 직전 최신 Session·완료 증거 재조회, 조건부 갱신과 적용 후 active/completion/index 교차검증을 추가했다.

**검증:** migration 경쟁 조건과 sequence 범위 검증을 보강했다. 당시 Node 49개·Java 205개 성공 기록이다.

**결과와 한계:** 데이터 이관에서도 동시성 전제와 사후 검증이 필요하다는 사례다. writer-stop flag 자체가 실제 서비스 쓰기를 중지시키는 것은 아니므로 maintenance 절차가 필요하다.

**근거:** [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:920), [시험 배정 migration](/Users/msde76/app-back-end-learning-core/scripts/mongodb/tmi-31-migrate-exam-assignment.js), [migration 테스트](/Users/msde76/app-back-end-learning-core/scripts/mongodb/tmi-31-migrate-exam-assignment.test.js); 관련 구현 커밋 `2c81887`.

#### TS-15. Docker가 있어도 Testcontainers 통합 테스트가 기동하지 못한 문제

- 발견 경위: **실제 로컬 테스트 실패 + 환경 수정**
- 시점·관련 이슈: 2026-09-07 / TMI-126

**증상:** 실제 Mongo 통합 테스트를 실행하는 과정에서 Testcontainers와 로컬 Docker daemon이 통신하지 못했다.

**원인을 좁힌 과정:** 당시 Testcontainers의 기본 Docker API 1.32가 daemon의 최소 허용 버전보다 낮은 환경 호환성 문제임을 확인했다. 09-05의 Docker daemon 부재와는 다른 원인이었다.

**수정:** 테스트 프로세스에만 `JAVA_TOOL_OPTIONS=-Dapi.version=1.44`를 주입해 로컬 daemon과 호환시켰다.

**검증:** 이 설정으로 Java 일반 529개와 Mongo 통합 40개를 전체 실행해 실패·skip 0을 확인한 09-07 기록이 있다.

**결과와 한계:** 통합 테스트를 건너뛰지 않고 실제 DB 검증을 실행할 수 있게 했다. 해당 API override는 그 환경의 해결책이며 모든 환경에 필수인 설정으로 소개하지 않는다.

**근거:** [실행 기록과 환경 설명](/Users/msde76/app-back-end-learning-core/docs/codex/TEN_SECOND_CHALLENGE_ROLLOUT.md:95), [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:9505), 채팅 C3. 구현 묶음 `4050ee3`.

#### TS-16. mongosh가 사전검사를 끝내기 전에 종료되던 문제

- 발견 경위: **실제 통합 테스트 발견 + 수정**
- 시점·관련 이슈: 2026-09-07 / TMI-126

**증상:** Challenge 사전검사 스크립트를 실제 mongosh로 실행하자 비동기 검사가 끝나기 전에 종료되는 문제가 나타났다.

**원인을 좁힌 과정:** Node 문법·함수 수준 검증만으로는 mongosh --file 실행 수명주기를 검증하지 못했다. 비동기 호출의 completion value가 사라져 쉘이 완료를 기다리지 않는 경로를 찾았다.

**수정:** mongosh 분기에서 runMongosh Promise가 마지막 completion value로 유지되도록 정리하고 오류는 안전한 고정 메시지와 실패 종료로 처리했다.

**검증:** 실제 Testcontainers Mongo 안에서 mongosh dry-run/apply의 종료 코드와 출력, 제거한 인덱스의 재생성, 기준일과 완료 결과 보존을 검증했다. 수정 후 Mongo 40개·Node 90개를 포함한 전체 검증 성공 기록이다.

**결과와 한계:** 스크립트의 종료 코드뿐 아니라 의도한 DB 검사가 실제 실행됐는지 검증했다. 같은 커밋의 다른 Challenge 기능과 구분해 테스트 중 발견한 결함으로 서술할 수 있다.

**근거:** [mongosh 실행 분기](/Users/msde76/app-back-end-learning-core/scripts/mongodb/challenge-10s-prepare.js:119), [실제 Mongo·mongosh 테스트](/Users/msde76/app-back-end-learning-core/src/mongoIntegrationTest/java/web/tosunsaeng/domain/challenge/ChallengeMongoIntegrationTest.java), [작업 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:9505); 커밋 `4050ee3`, 채팅 C3.

### 5.2 포트폴리오에 바로 사용할 문장 후보

아래 문장의 “구현·분석·검증” 표현은 실제 본인 역할에 맞춰 선택한다.

- **AI 결과 복구:** “AI가 빈 종합 피드백을 반환해도 시험이 완료되는 문제를 분석했습니다. 문항 결과의 실제 존재 여부와 Summary 유효성을 검사하고, 완료 문항 재채점 없이 Summary만 재생성하도록 개선했습니다. 재생성 세대 검증으로 늦은 Callback의 상태 덮어쓰기를 방지했습니다.” 근거: TS-01·02.
- **Mongo 트랜잭션:** “병합 코드 리뷰에서 중복 키 예외를 잡은 뒤 이미 취소된 Mongo 트랜잭션을 계속 사용하는 결함을 발견했습니다. 전체 Summary·Job·Session·Outbox 작업을 새로운 트랜잭션으로 재시도하도록 변경하고 부분 commit 방지 테스트를 추가했습니다.” 근거: TS-08.
- **CI 동시성:** “CI에서 간헐적으로 실패하던 테스트를 스레드 실행 순서별로 분석해, 정상 상황에서도 저장 호출이 2회 또는 3회가 될 수 있음을 확인했습니다. 최종 상태 불변식 테스트와 결정적 충돌 테스트를 분리하고 10회 반복 검증했습니다.” 근거: TS-05.
- **환경 설정:** “GitHub Actions 실패를 추적해 배포용 환경변수가 Spring 테스트 설정을 덮어쓰는 것을 확인했습니다. 테스트 step에만 환경변수를 지정해 테스트와 배포의 release 값을 분리했습니다.” 근거: TS-06.
- **실제 DB 검증:** “Node 단위 검증을 통과한 스크립트가 실제 mongosh에서는 비동기 검사 완료 전에 종료되는 문제를 통합 테스트로 발견했습니다. Promise completion value를 보존하고 격리 Mongo에서 실행 결과·인덱스·기존 데이터 보존을 검증했습니다.” 근거: TS-16.

### 5.3 팀원 회고로 확인한 운영 장애 — 개인 기여·복구 결과 추가 확인

#### TS-23. 특정 시험지의 S3 음성이 없어 신규 사용자가 Q5에서 멈춘 장애

- **발견 경위 [팀원 회고]:** 첫날 4명, 다음 날 5명이 Q4 이후 멈췄지만 기존 계정 테스트는 성공했다. 계정 초기화 후 신규 사용자 흐름으로 시험을 시작하자 Q5 음성 로드 실패가 재현됐다.
- **원인 [팀원 회고]:** 콘텐츠 정리 중 파일을 복사하지 않고 이동해 MockExam1의 S3 음성이 없어졌다. 정상 테스트는 다른 시험지를 사용해 문제를 놓쳤다.
- **현재 코드 연결 [구현 확인]:** 시험지 배정은 사용자 완료 이력·sequence의 영향을 받는다. 다운로드 URL은 시험지 ID·문항 번호로 Key를 만들며, 해당 Presigned GET 발급 경로는 파일 존재 여부를 확인하지 않는다.
- **배운 점 [추론]:** API 응답 성공과 콘텐츠 접근·시험 진행 성공은 별개다. 계정·시험지·문항·리소스까지 재현 조건을 맞춰야 한다. 공유 콘텐츠 변경은 서버 분리만으로 격리되지 않는다.
- **재발 방지 [제안]:** 필수 리소스 manifest·접근성 검사, 신규/기존 계정 배정별 smoke, 복사 후 검증·전환하는 콘텐츠 변경 절차. 이번에 구현한 기능은 아니다.
- **미확인:** 정확한 HTTP 오류, 복구 작업·재검증 성공, 본인 담당 범위. S3 장애를 고쳤다고 단정하기 전에 확인한다.

**근거:** [장애 회고](https://velog.io/@jinjinjara1022/토선생-개발-일지-01-멈춰버린-서비스), [배정](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamSessionManager.java:291), [S3 URL](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamServiceImpl.java:77), [상세 대조·문장 후보](TOSUNSAENG_TEAM_RETROSPECTIVE_BACKEND_SUPPLEMENT.md).

#### TS-24. AI 피드백 복구가 반복되며 사용자가 약 10분 기다리다 이탈

- **발견 경위 [팀원 회고]:** 모델 변경 후 출력 언어·형식 문제가 반복됐고 복구가 지연·전체 실패로 이어졌다. 실제 사용자가 피드백을 약 10분 기다리다 결과를 받지 못하고 이탈했다.
- **현재 코드 연결 [구현 확인]:** 문항·Summary Job, 상태·timeout·최대 dispatch, 완료 문항 skip, 빈 Summary 실패 판정과 선택적 재생성으로 연결할 수 있다. 세부 수정은 TS-01·02·03에 있다.
- **책임 구분:** AI 담당자의 잘못된 필드 repair와 백엔드의 문항/작업 복구는 다르다. 재시도 단계별 제한이 전체 사용자 대기시간 상한을 자동 보장하지는 않는다.
- **미확인:** 당시 즉시 조치와 현재 앱 개선의 인과·시점, 본인 역할, 전후 대기시간·이탈률. 이 회고를 동기 호출의 스레드 고갈 증거로 사용하지 않는다.

**근거:** [7월 회고](https://velog.io/@jinjinjara1022/아이디어에서-첫-사용자까지-토선생-개발-7월-회고), [8월 AI repair 개선](https://velog.io/@jinjinjara1022/일단-되게에서-제대로-되게로-토선생-개발-8월-회고), [백엔드 복구](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamGradingService.java), [상세 대조](TOSUNSAENG_TEAM_RETROSPECTIVE_BACKEND_SUPPLEMENT.md).

## 6. 부록: 상세 조사 근거와 전체 후보 표

### 6.1 전체 24개 후보

| ID | 소재 | 발견·확인 수준 | 선택 기준 |
|---|---|---|---|
| TS-01 | 빈 종합 피드백을 성공으로 처리하던 문제 | 사용자 제기 + 코드 수정 | 대표 추천 |
| TS-02 | 이전 AI 요청의 늦은 실패가 새 재시도 상태를 덮는 경쟁 조건 | 리뷰 발견 + 재현 테스트 + 수정 | 상세 5.1절 |
| TS-03 | 마지막 문항 Callback 안에서 Summary를 동기로 호출하던 병목 | 리뷰 발견 + 구조 수정 | 상세 5.1절 |
| TS-04 | 재답변 점수가 최초 시험의 파트 점수에 누적되던 오류 | 사용자 제기 + 원인 확인 + 수정 | 상세 5.1절 |
| TS-05 | 로컬에서는 통과하고 CI에서는 간헐적으로 실패한 동시성 테스트 | 실제 CI 실패 + 재현 분석 + 수정 | 대표 추천 |
| TS-06 | 배포 환경변수가 테스트 설정을 덮어써 CI가 실패한 문제 | 실제 CI 실패 + 원인 확인 + 수정 | 상세 5.1절 |
| TS-07 | 없는 MongoDB 컬렉션 때문에 read-index dry-run이 중단된 문제 | 실제 스크립트 실패 + 수정 | 상세 5.1절 |
| TS-08 | DuplicateKey를 catch해도 Mongo 트랜잭션이 살아나지 않는 문제 | 병합 후 리뷰 발견 + hotfix | 대표 추천 |
| TS-09 | Part 4 결과 화면에서 질문 문장이 빠진 문제 | 응답 누락 확인 + 수정 | 상세 5.1절 |
| TS-10 | 제출·채점 전에 모범답안 음성이 노출되던 결함 | 보안 리뷰 발견 + 수정 | 상세 5.1절 |
| TS-11 | Sentry에서 예외 위치를 잃는 문제와 중복 수집 위험 | 관측성 리뷰 발견 + 수정 | 상세 5.1절 |
| TS-12 | DB commit 결과가 불명확할 때 사용자 병합 응답을 확정하지 못하는 문제 | 리뷰 발견 + 후속 수정 | 상세 5.1절 |
| TS-13 | 외부 응답과 기존 데이터의 검증 누락을 발견한 사례 | 리뷰 발견 + 수정 | 상세 5.1절 |
| TS-14 | migration 도중 완료된 시험이 다시 활성화될 수 있는 경쟁 조건 | 리뷰 발견 + 수정 | 상세 5.1절 |
| TS-15 | Docker가 있어도 Testcontainers 통합 테스트가 기동하지 못한 문제 | 실제 로컬 테스트 실패 + 환경 수정 | 상세 5.1절 |
| TS-16 | mongosh가 사전검사를 끝내기 전에 종료되던 문제 | 실제 통합 테스트 발견 + 수정 | 상세 5.1절 |
| TS-17 | AWS 배포 중 AccessDenied | 발생 로그 진단·정책 수정안 | 추가 근거 확인 후 사용 |
| TS-18 | Identity staging 기동 실패 | 발생 로그 원인 확정·설정 안내 | 추가 근거 확인 후 사용 |
| TS-19 | Sentry smoke 이벤트가 화면에 보이지 않음 | 단계별 진단·최종 수신 미확인 | 추가 근거 확인 후 사용 |
| TS-20 | Identity→Billing 인증 방식 불일치 | 통합 전 계약 리뷰·후속 구현 기록 | 추가 근거 확인 후 사용 |
| TS-21 | Firebase 탈퇴 후 로그인·재가입 교착 | 정적 lifecycle 진단·후속 개발 소재 | 추가 근거 확인 후 사용 |
| TS-22 | RevenueCat credential 파일 저장 후 검증 실패 | 진단 진행·원인 미확정 | 추가 근거 확인 후 사용 |
| TS-23 | 특정 시험지 S3 음성 누락·신규 사용자 Q5 중단 | 팀 회고의 운영 발생·원인 보고, 개인 기여·복구 미확인 | 운영 재현·콘텐츠 정합성 소재 |
| TS-24 | AI repair 반복·약 10분 대기 후 사용자 이탈 | 팀 회고의 운영 발생, 현재 복구 구현과 직접 인과 미확인 | TS-01·02·03 배경 보강 |

### 6.2 진단·외부 연동 후보의 상세 근거

#### TS-17. AWS 배포 중 AccessDenied

- 시점·상태: 2026-08-21, **발생 로그 진단·정책 수정안**
- 확인한 내용: OIDC role assume 성공 이후 DescribeTaskDefinition 권한에서 실패했고, 후속 실행은 배포 후 ListTasks 검증에서 실패했다. 실패 action·resource와 실행 단계를 구분해 필요한 읽기 권한 수정안을 제시했다.
- 남은 확인: 실제 IAM 변경·최종 성공은 이번 조사에서 재검증하지 않았다. 과거 정책을 현재 AWS 권장 정책으로 그대로 복사하지 않는다.
- 근거: [배포 진단 기록](/Users/msde76/app-back-end-learning-core/docs/codex/WORKLOG.md:2585)

#### TS-18. Identity staging 기동 실패

- 시점·상태: 2026-08-15, **발생 로그 원인 확정·설정 안내**
- 확인한 내용: 긴 UnsatisfiedDependencyException의 최하위 원인이 QUALITY_REVIEW_CONSENT_VERSION placeholder 누락이었다. ConsentPolicy 생성 실패가 상위 서비스까지 전파되는 경로를 확인하고 해당 ECS container의 runtime 설정을 안내했다.
- 남은 확인: 당시 기록은 원인 확인과 배포 수정 안내까지다. 새 revision의 실제 health 성공 증거는 별도로 붙여야 한다.
- 근거: [Identity 기록](/Users/msde76/identity/docs/codex/WORKLOG.md:3892)

#### TS-19. Sentry smoke 이벤트가 화면에 보이지 않음

- 시점·상태: 2026-08-11, **단계별 진단·최종 수신 미확인**
- 확인한 내용: 기본 profile로 인해 staging-only bean 미생성 → staging 적용 후 release 검증 실패 → capture 요청 성공 순으로 문제를 좁혔다. SDK event ID 발급은 서버 수신 확인과 다름을 구분했다.
- 남은 확인: 최종 transport/UI 원인은 이 기록에서 확정되지 않았다. 완전 해결 사례보다 단계별 진단 사례로 활용한다.
- 근거: [Identity 기록](/Users/msde76/identity/docs/codex/WORKLOG.md:1334)

#### TS-20. Identity→Billing 인증 방식 불일치

- 시점·상태: 2026-08-31 이후 / TMI-123, **통합 전 계약 리뷰·후속 구현 기록**
- 확인한 내용: Identity는 Bearer workload JWT를 보내지만 Billing ingress는 VPC Lattice AWS_IAM의 SigV4를 요구했다. delivery result의 Retry-After 전달 공백도 함께 찾아 adapter·재시도 계약 보완으로 연결했다.
- 남은 확인: 양쪽 서비스의 단위 테스트만으로 운영 ingress 통과를 보장하지 않는다. 코드 후속 병합 기록과 실제 Lattice E2E를 구분한다.
- 근거: [Identity 기록](/Users/msde76/identity/docs/codex/WORKLOG.md:6993), [Billing 후속 병합 기록](/Users/msde76/billing/docs/codex/WORKLOG.md:2743)

#### TS-21. Firebase 탈퇴 후 로그인·재가입 교착

- 시점·상태: 2026-08-24 이후 / 관련 TMI-103·104·107, **정적 lifecycle 진단·후속 개발 소재**
- 확인한 내용: WITHDRAWN User의 Firebase mapping이 남아 기존 로그인으로만 진입하고 신규 가입으로 갈 수 없는 경로를 찾았다. tombstone·외부 계정 정리·mapping/phone 해제를 단계별 lifecycle로 다뤄야 함을 정리했다.
- 남은 확인: 단순 mapping 삭제는 안전한 해결책이 아니다. 후속 구현과 운영 재가입 성공은 본인 기여 범위에 맞춰 추가 증빙 후 사용한다.
- 근거: [Identity 기록](/Users/msde76/identity/docs/codex/WORKLOG.md:5039)

#### TS-22. RevenueCat credential 파일 저장 후 검증 실패

- 시점·상태: 2026-09-07, **진단 진행·원인 미확정**
- 확인한 내용: File saved와 외부 API 권한 검증 완료를 구분하고 validator 세부 오류와 계정·앱 권한·전파 상태 확인 순서를 정리한 기록이다.
- 남은 확인: 현재 검토 자료로 실패 endpoint나 최종 원인을 특정하지 못한다. 해결 완료나 키 교체 성공 사례로 쓰지 않는다.
- 근거: [Billing 진단 기록](/Users/msde76/billing/docs/codex/WORKLOG.md:3517)

### 6.3 채팅을 실제로 확인한 범위

| 출처 | 작업 제목 | 확인한 대화 |
|---|---|---|
| C1 | 지금 로직들이 잘 돌아가는지 모니터링 하기 위해 로그를 추가하고 싶은데 어디 추가할 지 계획을 짜줘 | 최근 6개와 이전 10개 turn 페이지. 빈 partFeedback 재생성 요구, generation 증가 조건, Sentry 정책 피드백, 구현 완료 보고 |
| C2 | 지금 파트별 점수를 가져올 때 점수가 재시도 한 것도 다 더해서 가져오는 것 같은데 맞아? | 전체 5개 turn. 최초 문제 제기, retryCount=0 결정, 구현·검증, CI 실패 메시지와 수정 보고 |
| C3 | 정리 작업 기록 날짜별 분류 | 최근 6개 turn. 10초 챌린지 구현 중 실제 mongosh 조기 종료 발견, 전체 검증 결과와 PR 후 다음 단계 |
| C4 | Billing 서버 구현 범위 정리 | 최근 6개 turn. 종단 구현 상태·과거 계획과 실제 완료 시점 대조 |

채팅 식별자: C1 `019fdad5-d9ba-7530-a8f9-bc587f15ff19`, C2 `01a00fd0-a6ed-7ce0-b277-eb095fcbe741`, C3 `019ffdf1-3714-7622-9ec4-0025b3473a74`, C4 `01a037c0-e002-7ea2-b2fd-cb8fb8c4a293`. 이는 출처 재검색용이며 공개 포트폴리오에 넣을 필요는 없다. 접근 가능한 활성 작업 목록도 조회했고 보관 작업 조회 결과는 0개였다.

채팅의 긴 지시문은 과거 작업의 증거로만 읽었다. 현재 작업에서 과거 코드 변경·배포 지시를 재실행하지 않았다.

### 6.4 테스트 결과의 날짜와 의미

| 작업 시점 | 사례 | 당시 검증 기록 | 해석 |
|---|---|---|---|
| 07-29 | TS-02·03 | Java 142개 성공 | 같은 수정 묶음, 성과 중복 계산 금지 |
| 07-30 | TS-14 | Java 205개·Node 49개 성공 | 실제 운영 migration 완료 증거는 아님 |
| 08-04 | TS-07·10 | Node 76개 / Java 248개 성공 | 서로 다른 작업의 검증 기록 |
| 08-07 | TS-05 | 반복 10/10·Java 296개 성공 | 해당 flaky 테스트 재발 방지 검증 |
| 08-11 기록 | TS-01 | Java 351개 성공 | 구현 기록 날짜와 08-17 commit 날짜 구분 |
| 08-17·25 | TS-04·06·09 | Java 352개 성공 | 각 수정 당시 기록, 이번 재실행 아님 |
| 09-01 | TS-08 | 신규 5개·전체 Java 444개 성공 | Mock 트랜잭션 제어 검증 |
| 09-05 | TS-12·13 | Java 496개·Node 7개 성공, Docker 실행 실패 | 당시 환경 한계 |
| 09-07 | TS-15·16 및 후속 전체 검증 | Java 529개·Mongo 40개·Node 90개 성공 | 격리 Mongo 검증, production E2E와 구분 |

### 6.5 이번 조사에서 한 검증과 남은 작업

- 코드·테스트·설정은 읽기 전용으로 대조했다. 문서에 연결한 로컬 파일과 지정 행, 수정 커밋의 존재 여부를 검사한다.
- 애플리케이션 변경이 없어 Gradle 테스트를 새로 실행하지 않는다. 문서 링크·형식과 `git diff --check`로 이번 변경을 검증한다.
- 채팅 원문이나 운영 payload를 그대로 복제하지 않고 문제·원인·결정·검증만 추출했다. 실제 계정·ARN·인증 값·사용자 음성·전체 AI 응답은 포함하지 않는다.
- 본문 사례를 선택한 뒤 해당 diff 1개, 회귀 테스트 1~2개, 안전하게 정리한 오류 로그를 붙이면 면접에서 설명할 증거 묶음이 된다.
