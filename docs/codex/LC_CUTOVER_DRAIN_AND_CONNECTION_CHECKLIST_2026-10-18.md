# LC 전환 배출·복구 및 공동 연결 확인표

## 1. 결론

1. TMI-116/TMI-217/TMI-220: 로컬 후보 기동·TLS/JWT 및 늦은 Callback 완료 검사는 통과했지만 운영 전환 실행 검증은 아니다.
2. 생성 유입 차단만으로 진행 중 요청·복구 writer가 멈추지는 않는다. 봉인 전 모든 생성 writer의 배출 근거가 필요하다.
3. ECS 종료 30초를 작업 완료 보장으로 사용하지 않는다. 복구 scheduler 기본 종료 대기는 순차 최대 20초+30초다.
4. Summary의 프로세스 메모리 대기열은 보존되지 않는다. durable Job·늦은 Callback 또는 기존 사용자 재채점 경로로 복구하며 자동 전체 재전송을 약속하지 않는다.
5. 다음 외부 실행에는 양측 담당 확인, 제한 방식·시간·검증 계정 및 변경 승인 필요. 이번에는 실제 중단·봉인·배포·발신을 하지 않았다.

## 2. 배출과 복구를 구분하는 기준

| 대상 | 전환 전 확인 | 중단/결과 불명 시 처리 |
| --- | --- | --- |
| 생성 HTTP·Session 생성 worker | v1/v2와 직접 접근을 포함한 유입 통제, 기존 요청 종료, 재기동/다른 인스턴스가 생성하지 못하는 근거 | writer 배출 증거가 없으면 봉인 금지. 미완료 operation은 삭제하지 않음 |
| Billing reserve/confirm | 실제 활성 worker·lease 및 미완료 operation 확인 | 기존 operation의 status-first 복구. 새 key 재발급·확정 소비 cancel 금지 |
| 답안 submit | durable 접수 전/후 구분, 진행 중 HTTP 완료 또는 실패 확인 | 응답 유실을 미접수로 단정하지 않음. 기존 문항/retry 계약으로 확인 |
| Summary 대기/처리 | Job 상태·attempt·접수 증거 유지 | 늦은 Callback 멱등 처리. Callback 없으면 timeout/상한·owner/삭제 검사를 거친 기존 재채점 경로 사용 |
| 삭제 전달·outbox | 미전달·lease·재시도 하한과 원 event 유지 | 같은 event 재시도, ACK 유실은 미반영 증거가 아님. TTL/새 event로 우회 금지 |

관측용 backlog=0, 건수 두 번 일치, ALB health, ECS COMPLETED 중 어느 하나만으로 배출을 선언하지 않는다. 현재 확인한 지표만으로 전체 writer 배출을 자동 증명하는 기능은 없다. 증명이 어려워 태스크 전체 중단이 필요하다면 기존 제출/Callback 중단 영향과 송신측 재시도 조건까지 별도 승인한다.

## 3. 종료 시간의 실제 한계

- 마지막 AWS 관측의 stopTimeout은 30초이며 TG drain은 300초다. 이번 AWS 재조회는 하지 않았다.
- `BillingReservationReconciliationScheduler.stop`: poller를 attemptTimeout만큼, workers를 lease만큼 순서대로 기다린다. 기본 20초/30초는 최대 대기 예산이며 실제로 항상 50초 걸린다는 뜻은 아니다. 해당 기능 OFF면 같은 대기 동작을 전제하지 않는다.
- `DeletionScheduler`와 `ChallengeScheduler`는 최대 35초 대기 코드가 있다. `BillingDeletionScheduler`는 shutdownNow를 사용한다. 활성 기능·실제 설정별 검증이 필요하다.
- `GradingConfig`는 Summary executor의 waitForTasksToCompleteOnShutdown=false다. 전체 종료 timeout 증가만으로 Summary 대기열 완료가 보장되지 않는다.
- 따라서 임의로 stopTimeout만 늘리거나 모든 flag를 켜지 않는다. 최종 활성 조합을 먼저 정하고 in-flight 종료 리허설로 검증한다. 이번 검사는 실제 느린 HTTP 요청 중 SIGTERM 시험이 아니다.

## 4. 공동 연결 검증 준비표 — 아직 실환경 미실행

| 순서 | 검증 | 통과 증거/권한 경계 |
| --- | --- | --- |
| 1 | 양측 현재 task/image·flag·Secret 참조·scope·JWK 지문 | 비밀값 없이 비교, 미배포 준비본과 실행본 구별 |
| 2 | DNS/인증서·8443/8444 TG/ALB·발신 NAT | 양방향 실제 경로 검증, 공개8080 health로 대체 금지 |
| 3 | 수신 OFF/공개 port 차단·미인증/잘못된 audience | 해당 배포 단계의 404/401/403 계약 대조; 임의 유효 event 생성 없음 |
| 4 | 승인된 유효 JWT·event 전달 | 지정된 테스트 권리/Session 범위에서만 수행. 204와 durable 저장·중복1회 확인 |
| 5 | v1/v2 생성·same-key·제출/Callback | 지정 계정으로 실제 소비 영향 승인 후 수행. v2 장애 시 무료 fallback 없음 |
| 6 | 환불/삭제·응답 유실·과거 Session 경합 | 양측 동일 fixture·event/operation 근거 대조. 다른 사용자/권리 보호 |

연결 기준은 LC `https://api-prod.to-teacher.com/internal/v1/entitlements/access/events`, Billing origin `https://billing-workload-prod.to-teacher.com`, scope `tosunsaeng-billing-prod`, 보고된 발신 NAT `13.124.57.130/32`다. 예정/보고값이며 적용 직전 독립 재확인한다. 정상서명 환불 테스트도 차단 데이터를 쓰므로 단순 읽기 점검이 아니다.

## 5. 실행 전 남은 선택

- 양측 배포 후보/최종 활성 기능 조합과 제한·배출 수단을 확정한다.
- 신규 생성 제한 시간과 최대 허용 중단 시간, 검증 계정·금전/삭제 영향 범위를 지정한다.
- 실제 중단·DB 봉인·태스크/ALB 연결·업무 발신을 승인받은 후 실행한다. 그 전에는 로컬 통과를 근거로 운영을 변경하지 않는다.

## 6. 이번 검증 및 참조

종료/복구 코드를 정적 대조하고 대상 Gradle 검사를 재실행했다. XML 확인 결과 실제 실행은 `SummaryDispatchSchedulerTest` 13개/실패0/오류0/skip0이다. 함께 지정한 `BillingReservationReconciliation*Test` 패턴에 일치하는 실행 결과는 없으므로 Billing 복구 테스트 통과로 계산하지 않는다. Summary timeout 전 재claim 금지·경계 이후 재claim 및 이전 runnable 중복 방지 검사를 포함한다. 이는 모의 저장소 기반 검사이며 ECS 실제 drain의 증거가 아니다.

- [후보 TLS 검증](LC_CANDIDATE_PRODUCTION_TLS_REHEARSAL_2026-10-18.md)
- [실제 프로세스 재시작 검증](LC_CANDIDATE_PROCESS_REHEARSAL_2026-10-18.md)
- [전환 실행안](LC_PRODUCTION_CUTOVER_RUNBOOK_2026-10-18.md)
- 코드: `GradingConfig`, `SummaryDispatchScheduler`, `ExamGradingService.retryExam/retrySummaryIfEligible`, `BillingReservationReconciliationScheduler`, `ReservationReconciliationProperties`.
