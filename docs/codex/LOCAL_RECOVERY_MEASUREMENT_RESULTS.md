# 로컬 재채점·장애 복구 검증 결과

측정일: 2026-09-08 KST. 관련 이력: TMI-25·TMI-128. Jira 변경 없음.

## 1. 5줄 결론

1. **검증 사실**: 11문항 중 실패 0·1·3·11개인 네 fixture에서 문항 채점 전송은 각각 0·1·3·11회였다. [테스트](../../src/test/java/web/tosunsaeng/domain/exams/application/ExamGradingServiceTest.java)
2. **검증 사실**: 네 fixture 모두 완료 문항 재전송 0회, 즉시 같은 복구를 재요청했을 때 추가 문항 전송 0회였다. [테스트](../../src/test/java/web/tosunsaeng/domain/exams/application/ExamGradingServiceTest.java)
3. **검증 사실**: 격리 MongoDB의 Reservation 복구·경합·격리·경보 테스트 31개가 모두 통과했다. 운영 복구율이 아니라 명세 검증이다. [통합 테스트](../../src/mongoIntegrationTest/java/web/tosunsaeng/domain/exams/billing/reconciliation/ReservationReconciliationMongoIntegrationTest.java)
4. **검증 사실**: 이번 일반 테스트 전체 재실행은 543개 통과, 실패·오류·skip 0개다. [실행 보고서](../../build/reports/tests/test/index.html)
5. **미측정**: API p95·요청 스레드 점유 전후 비교와 실제 장애 발생부터 복구까지 걸리는 시간은 이번 검증에 포함되지 않는다. [전송 구현](../../src/main/java/web/tosunsaeng/domain/exams/application/GradingDispatchService.java), 아래 §4·§5 참조.

## 2. 반드시 읽어야 하는 내용

### 문항 재전송 횟수

| 전체 문항 | 완료 문항 | 실패 문항 | 완료 문항 재전송 | 실패 문항 전송 | 즉시 동일 복구 재요청 후 추가 전송 |
| --- | --- | --- | --- | --- | --- |
| 11 | 11 | 0 | 0 | 0 | 0 |
| 11 | 10 | 1 | 0 | 1 | 0 |
| 11 | 8 | 3 | 0 | 3 | 0 |
| 11 | 0 | 11 | 0 | 11 | 0 |

실제 `ExamGradingService.retryExam`을 호출하고 Mockito로 `GradingDispatchService.dispatchQuestion` 호출 수와 문항 번호를 검증했다. DB repository·Redis·S3·AI 전송은 Mock이다. 완료 결과와 COMPLETED Job이 함께 있는 fixture이며, 최초 응시 retryCount=0과 전송 1회 뒤 실패한 상태를 사용했다. 서비스 호출 횟수이지 실제 AI HTTP 요청·모델 추론·과금 건수가 아니다. Summary 예약/전송은 문항 호출 집계에 포함하지 않는다. 즉시 재요청 검증은 timeout 이전이며 모든 시간대의 재요청을 영구 차단한다는 뜻이 아니다.

**보고서 권장 문구**: “11문항 모의고사의 완료·실패 상태를 조합한 4개 로컬 테스트에서 완료 문항 재전송 0건을 검증했다. 특히 8문항 완료·3문항 실패 상황에서는 실패한 3문항만 채점 전송 대상으로 선정했으며, 즉시 재요청에 따른 중복 전송도 발생하지 않았다.”

**분석·추론**: 3문항 실패 예시에서 전체 11문항을 다시 보내는 가상 기준과 비교하면 문항 전송 8회, 약 72.7%를 회피한다. 그러나 변경 전 서버를 실행한 결과가 아니므로 “실제 호출/비용 72.7% 개선” 성과로 쓰지 않는다.

## 3. 결정해야 하는 사항

- 지금 보고서에는 위 호출 수와 “Reservation 복구·안전장치 31개 테스트 통과”를 사용하는 것을 권장한다.
- API 성능 비교를 계속하려면 비교할 변경 전 커밋/버전과 대표 API를 먼저 확정한다. 현재 앱 저장소 밖의 과거 웹 POC 조회·실행은 이번 범위에 포함하지 않았다.
- 실제 복구 시간은 AI 채점 복구와 Billing Reservation 복구 중 어느 흐름인지, 시작점·완료 조건·장애 지속 시간을 확정한 뒤 별도 측정한다.

## 4. 주요 위험과 미확인 사항

- 테스트 31/31 통과를 “장애 31건 모두 자동 복구” 또는 “운영 복구 성공률 100%”로 바꾸지 않는다. 의도적으로 차단·격리하고 증거를 보존하는 성공 테스트도 포함된다.
- MongoDB 7.0.14 replica-set은 실제 격리 컨테이너다. Billing과 Sentry는 Mock이고 commit 응답 유실은 Transaction wrapper에서 예외를 주입했다. 실제 네트워크 단절·AWS·Atlas·AI 장애 실험은 아니다.
- MutableClock을 사용하고 worker 메서드를 직접 호출한다. 61초 시간 이동 후 성공하는 테스트는 실제 61초 복구 측정이 아니다. poll 대기·실제 원격 지연·재시작·네트워크 지연을 포함하지 않는다.
- JUnit 실행 시간이나 Gradle 전체 소요 시간을 API p95 또는 사용자 관점 복구 시간으로 쓰지 않는다.
- 채점 결과 대기는 Callback/Polling으로 분리되지만 현재 문항 전송에는 blocking `RestTemplate.postForEntity`가 있다. “모든 요청 스레드 점유 제거”는 부정확하다.
- TMI-128은 현재 작업 트리의 미커밋 구현이며 기본 OFF다. 테스트 통과는 배포·운영 활성화 완료를 뜻하지 않는다.
- 일반 테스트 전체는 다시 실행했지만 Mongo 통합 테스트 전체/Node 테스트는 이번에 재실행하지 않았다. 이번 Mongo 결과는 해당 클래스 31개만이다.

## 5. 현재 작업과 직접 관련된 설명

### 이번 변경·호환성·배포 경계

- 변경: `ExamGradingServiceTest`에 parameterized 테스트 1개(4개 실행 case), 이 결과 문서 및 CURRENT_STATE/WORKLOG 기록만 추가했다. application runtime 코드는 변경하지 않았다.
- 유지: 기존 공개 API·DTO·BaseResponse·AI user_id=examId·retryCount·S3/Redis/Billing 계약과 운영 설정을 변경하지 않았다.
- diff 확인: 위 4개 파일 외 이번 의도적 수정은 없다. 기존 사용자/동시 작업의 runtime·AGENTS·문서 변경과 미추적 파일을 보존했다.
- 배포 전: 이번 테스트·문서는 runtime 배포가 필요 없다. Reservation 기능 활성화는 기존 [RUNBOOK](BILLING_RESERVATION_RECONCILIATION_RUNBOOK.md)의 index·구버전 drain·staging 경합/E2E·Billing/Lattice·Sentry 실제 수신 gate를 유지한다.

### 다음 측정 계획 — 아직 실행하지 않음

| 항목 | 필요한 통제 조건 | 기록할 값 |
| --- | --- | --- |
| API 전후 비교 | 실제 변경 전/후 버전, 같은 CPU/메모리/JVM/스레드 풀·데이터·AI 지연·요청률, 동일 warm-up 및 반복 | API별 p50/p95/p99, 성공률·timeout/오류율, busy request threads, 처리량 |
| AI 복구 시간 | 고정 음성 fixture, AI stub 실패→회복, 실제 Callback·Polling, 동일 timeout/retry 설정 | 장애 시작/복구 가능 시점/최종 결과 확정 시각, 완료·실패 대상 전송 수 |
| Reservation 복구 시간 | 격리 Billing stub+Mongo, 실제 scheduler, 가상 시계 없이 동일 설정·반복 | 장애 유형별 정상 수렴/보상/격리 건수, 성공 케이스 시간 분포, 미해결 건수와 관찰 종료 시점 |

이 저장소 docs/scripts/build 검색에서 재사용할 k6/Gatling/JMeter 부하 harness는 확인하지 못했다. 정상 응답만 빨라지고 실패가 증가하는 결과를 성능 개선으로 오인하지 않도록 오류율도 함께 측정한다. 측정 계획은 운영 부하 실행 승인이 아니다.

## 6. 부록 — 재현 명령과 전체 검증 범위

기준: `feat/TMI-128-billing-reservation-reconciliation`, HEAD `cb5f6ee` + 기존 dirty 작업 트리 + 이번 테스트. 커밋만 checkout해서는 현재 결과를 동일하게 재현할 수 없다.

```sh
./gradlew test --tests web.tosunsaeng.domain.exams.application.ExamGradingServiceTest --rerun-tasks
JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew mongoIntegrationTest --tests web.tosunsaeng.domain.exams.billing.reconciliation.ReservationReconciliationMongoIntegrationTest --rerun-tasks
./gradlew test --rerun-tasks
git diff --check
```

| 실행 | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- |
| ExamGradingServiceTest 대상 실행 | 61 | 0 | 0 | 0 |
| ReservationReconciliationMongoIntegrationTest 대상 실행 | 31 | 0 | 0 | 0 |
| 일반 test 전체 재실행(위 61개 포함) | 543 | 0 | 0 | 0 |

대상 실행 61개는 전체 543개에 포함되므로 중복 합산하지 않는다. `--rerun-tasks`로 cache/up-to-date 결과가 아닌 재실행을 했다. runtime 변경이 없는 테스트 보강으로 clean 대신 전체 test 재실행을 사용했다. Mongo 실행은 최초 sandbox의 Gradle cache lock 접근 거부 후 승인된 실행으로 재시도했다. Docker API 1.44는 해당 테스트 프로세스 호환성 설정이며 운영 설정을 바꾸지 않았다.

XML 근거: [채점 테스트](../../build/test-results/test/TEST-web.tosunsaeng.domain.exams.application.ExamGradingServiceTest.xml), [Reservation 테스트](../../build/test-results/mongoIntegrationTest/TEST-web.tosunsaeng.domain.exams.billing.reconciliation.ReservationReconciliationMongoIntegrationTest.xml). build 산출물은 후속 실행으로 덮어써질 수 있으므로 이 문서에 실행 결과를 기록했다.

### Reservation 31개 테스트 목록 — 모두 통과

아래는 각 테스트 메서드에 대응하며 운영 사고 건수 집계가 아니다. 원문은 [통합 테스트 파일](../../src/mongoIntegrationTest/java/web/tosunsaeng/domain/exams/billing/reconciliation/ReservationReconciliationMongoIntegrationTest.java)을 따른다.

| 번호 | 검증 시나리오·기대 결과 |
| --- | --- |
| 1 | 미전송 PREPARED를 Billing 호출 없이 종료 |
| 2 | 불명 404에서 cleanup intent·guard 보존, 새 reserve 없음 |
| 3 | 지연 reserve 발견 후 동일 식별자로 취소 |
| 4 | Session commit 원래 시각으로 confirm 복구 |
| 5 | confirm 응답 유실 후 두 번째 worker 실행의 status로 성공 수렴, confirm 총 1회 |
| 6 | 원격 만료 시 confirming Session만 원자적으로 abandon, 증거 보존 |
| 7 | 모순된 그룹 또는 Session 누락 시 격리 |
| 8 | 다른 instance가 lease를 획득하면 오래된 lease의 쓰기 차단 |
| 9 | cleanup과 Session commit 동시 성공 불가 |
| 10 | Session insert 뒤 실패 시 operation과 함께 Transaction rollback |
| 11 | cleanup commit 응답 유실 시 새 확인 전 cancel 금지, 다음 실행에서 수렴 |
| 12 | 최종 commit 응답 유실 시 majority evidence로 성공 확인, cancel 없음 |
| 13 | 공유 인증 circuit·단일 probe·operation별 중복 경보 방지 |
| 14 | retry 예산 소진·owner deny 시 guard 보존, 원격 호출 없음 |
| 15 | 24시간 복구 예산 소진 시 guard를 해제하지 않고 격리 |
| 16 | worker OFF에서 HTTP 인증 circuit 고착 방지 |
| 17 | OFF·legacy operation 자동 실행 방지 |
| 18 | HTTP와 worker 동시 claim 승자 1개 |
| 19 | 실제 Mongo write conflict에서 cleanup/commit 직렬화 |
| 20 | Sentry SDK 실패 시 pending 보존·매 poll 재경보 방지 |
| 21 | pending 경보 retry 소진 시 미접수 증거 보존 |
| 22 | 5분 조기 경보 경계·1회 생성·업무 retry 일정 유지 |
| 23 | 동시 조기 경보 poller incident 1개 생성 |
| 24 | 재시작과 이후 격리에도 기존 조기 경보 pending 보존 |
| 25 | 조기 경보 SDK 실패 시 동일 incident 재시도·소진 증거 보존 |
| 26 | 완료·legacy·OFF·기존 격리 조기 경보 제외 |
| 27 | 전역 auth 경보가 개별 조기 경보로 확산되지 않음 |
| 28 | 조기 경보 enqueue batch 제한·claimed operation 보존 |
| 29 | 실제 HTTP saga 메서드의 공유 fence·동일 key replay |
| 30 | cleanup intent 뒤 HTTP replay의 reserve/commit 차단 |
| 31 | worker 1회 실행에서 세 번째 원격 호출 차단 |
