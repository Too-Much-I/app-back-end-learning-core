# 모의고사 정상 접수 응답·후속 상태 복구 구현 계획

- 작성일: 2026-10-19
- 상태: 2026-10-20 최소 범위 로컬 구현·단위/격리 Mongo 회귀 통과 / 미배포
- 관련 Jira: TMI-118, TMI-125. 신규 이슈는 생성하지 않았다.
- 이번 승인 범위: 계획서와 작업 기록만. 코드·실DB·설정·commit/push·배포는 제외한다.
- 기존 [복구 계획](LC_EXAM_COORDINATION_FAILURE_RECOVERY_PLAN.md)의 P2 후속 상세안이다. 이미 배포된 제한 재시도·worker 실패 격리는 [1차 구현 보고서](LC_EXAM_COORDINATION_RECOVERY_IMPLEMENTATION_2026-10-19.md)를 따른다.

### 2026-10-20 최소 범위 R2 — 아래 최초 상세안보다 우선

- 사용자 복잡도 검토 및 구현 승인에 따라 submitAudio 성공 반환 이후의 알려진 transient 오류만 분리한다. 콜백·조회·요약 성공 처리 확대, unknown commit 결과 해결, 새 접수 타입/영속 marker/경보 체계는 이번에 구현하지 않는다.
- 기존 접수 receipt/완료 marker가 있는 복구 적격 유료 시험과 명시된 TRANSITION_FREE_V1 출처의 무료 시험만 적용한다. 전자는 기존 worker로 복구하고 후자는 Billing projection 자체가 불필요하다. 출처/증거가 없는 legacy는 성공으로 추정하지 않는다.
- 추가 조회는 coordinator 실패 시에만 수행한다. 실제 submit 저장·dispatch·캐시 실패는 기존 오류 그대로다. 성공 응답 전 owner/삭제 및 공개 접근을 다시 확인한다.
- 기존 worker는 `_id` keyset 순회로 DB 반환 개수를 제한하고 실패/진행 중 후보를 지나 다음 batch를 처리한다. 끝에서 처음으로 순환하며 재시작 시 DB에서 다시 순회한다. 새 컬렉션·인덱스·큐는 없다.
- 아래 A01~A18은 전체 설계 회귀 목록이다. 이번 구현의 직접 검증과 기존 회귀를 구분해 [구현 보고서](LC_SUBMISSION_ACCEPTANCE_RECOVERY_IMPLEMENTATION_2026-10-20.md)에 기록하며 실제 배포/E2E 완료를 주장하지 않는다.

## 1. 5줄 결론

1. 제출 접수가 확정됐다면 후속 상태 정리의 일시 DB 오류 때문에 앱에 제출 실패를 보내지 않는다.
2. 기존 정상 접수 DTO를 유지한다. 접수 성공을 문항 채점 완료·전체 시험 완료로 바꾸지 않는다.
3. 상태 정리는 최대 3회 시도 후에도 실패하면 영속 근거를 기반으로 worker가 재처리한다. 응답 후 메모리 작업에만 의존하지 않는다.
4. 저장 불명·소유권/삭제 차단·영구 오류는 일괄 성공 처리하지 않는다. AI 재전송이나 이용권 재차감을 상태 정리 재시도에 포함하지 않는다.
5. 접수 확정·재기동 복구·후보 누락 방지·중복 이벤트 방지를 격리 테스트로 검증한 뒤 별도 승인으로 배포한다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 바뀌는 동작

현재는 `제출 접수 → 상태 정리 → 앱 응답`이어서 마지막 정리만 실패해도 앱은 실패를 받는다.

목표는 `제출 접수 확정 → 상태 정리 시도 → 기존 정상 접수 응답`이다. 정리가 지연되면 서버가 남은 일을 계속 처리하며 앱은 기존 Polling으로 실제 채점 상태를 확인한다.

이는 무조건 예외를 무시하는 변경이 아니다. 정상 응답을 줄 수 있는 접수 근거와 정리 복구 경로를 함께 구현한다. 음성 파일이나 Job 문서 하나가 있다는 이유만으로 접수 성공을 추정하지 않는다.

### 이번 후속 구현의 범위

- 1차 대상은 `ExamServiceImpl.submitAudio`의 `submitQuestion` 반환 이후 상태 정리 경계다.
- 같은 정리 helper를 호출하는 콜백·조회·요약 경로는 호출부별로 분류한다. 공통 helper 전체에 무조건 catch를 넣지 않는다.
- 콜백 성공 응답 확대는 exact 결과·generation 저장 및 필수 후처리 근거를 독립적으로 입증한 경로만 적용한다. 요약 저장과 완료 상태가 같은 Transaction인 경로를 임의 분리하지 않는다.
- 기존 coordinator, Session, 접수 증거, Job, outbox, worker를 우선 재사용한다. 신규 메시지 큐·새 서비스는 만들지 않는다.
- Challenge, Billing/Identity 저장소, 실제 사용자 데이터 변경은 제외한다.

### 유지하는 계약

기존 URL·HTTP Method·Request/Response DTO·BaseResponse·HTTP 성공 상태와 Polling 의미를 유지한다. AI `user_id=examId`, retryCount, S3/Redis Key, Billing reserve/confirm·과금·환불·exact Session 기접수 보호도 불변이다. 앱 수정이나 새 공개 필드는 요구하지 않는다.

## 3. 사용자가 결정해야 하는 사항

- 제품 방향은 이미 합의됐다. 다시 선택할 필요 없다.
- 구현 착수와 커밋·푸시·배포는 이번 문서 작성과 구분한다.
- 기본안은 신규 컬렉션·인덱스 없이 기존 영속 상태를 복구 대상으로 재사용하는 것이다. 아래 증명 조건을 충족할 수 없다면 필요한 최소 내부 marker/인덱스와 비용·보존 정책을 문서에 제시한 뒤 범위를 확인한다. 계획만으로 실DB migration을 승인한 것으로 취급하지 않는다.

## 4. 주요 위험과 미확인 사항

| 항목 | 대응 및 완료 기준 |
| --- | --- |
| DB 문서 존재와 정상 접수 혼동 | exact exam/question/canonical retry와 현행 접수 receipt·Job의 committed 증거를 대조한다. 저장 전/롤백/다른 요청의 문서는 제외한다. |
| 이미 저장됐지만 AI 전송 실패 | 현재 `submitQuestion`이 AI 전송 실패를 오류로 반환하는 계약은 이번 후처리 수정으로 바꾸지 않는다. |
| 정리 실패 후 복구 marker 쓰기도 실패 | 실패한 뒤 새 marker를 쓰는 방식만으로 보장하지 않는다. 기존 후보가 실패 전부터 durable하게 존재하거나, 필요 marker가 접수 commit과 원자적으로 만들어져야 한다. |
| 재시작 또는 응답 직후 종료 | DB의 접수·Session 증거만으로 새 인스턴스가 후보를 다시 발견해야 한다. 메모리 큐·응답 후 비동기 실행만으로 대체하지 않는다. |
| worker 후보 제한·기아 | 현재 전체 조회 후 stream.limit(batchSize)다. 반복 선택되는 앞쪽 시험 때문에 뒤쪽 후보가 영구 누락되지 않도록 안정적인 순회/페이지 처리 전략을 검증한다. 단순 오래된 순 정렬만으로는 충분하지 않다. |
| worker 일시 중단 | 복구 의무가 DB에 남고 재개 후 수렴해야 한다. 영구 OFF·잘못된 구성은 지원하지 않는 조합으로 검증하고 조용히 성공 전환하지 않는다. |
| 무료/legacy 시험 | Billing 후보가 아님을 실패로만 취급하지 않는다. Billing 정리가 불필요한 정상 무료 시험은 해당 정리만 생략 가능한지 검증한다. 필수 owner/삭제 fence는 제출 경계에 유지한다. source 누락을 무료로 추론하지 않는다. |
| unknown commit | 접수 Transaction 불명과 후처리 Transaction 불명을 구분한다. 둘 다 업무 전체 자동 재실행 금지. 후처리는 새 신뢰 가능한 읽기로 exact outbox/Session 결과가 확인될 때만 완료로 판단한다. 미확인 경로는 이번 정상 전환에서 제외한다. |
| 삭제·회원 통합·환불 경합 | 기존 허용/거절 계약 유지. 복구가 삭제된 자료를 재생성하거나 owner를 되돌리면 안 된다. 보호로 차단된 건을 완료로 표시하지 않는다. |
| 현재 운영 원인 | 10/19 Mongo 상세 코드는 미확인이다. 이번 계획을 특정 WriteConflict 원인 확정이나 모든 500 해결 보장으로 설명하지 않는다. |

## 5. 구현 순서와 설계

### S1. 접수 성공 경계 명시

1. `submitQuestion`의 신규 접수, 동일 요청 replay, 기존 결과 반환, dispatch 실패, 보호 거절 분기를 표로 고정한다.
2. 정상 반환 뒤 사용할 내부 접수 결과에 exact 대상·반환 상태·commit 근거를 연결한다. 내부 타입의 정확한 이름은 구현 시 정하며 외부 DTO에는 노출하지 않는다.
3. receipt coverage가 있는 시험과 기존 Job 기반 시험을 구분한다. enum 하나나 AI 전송 로그만을 영속 접수 증거로 사용하지 않는다.
4. 접수 commit 불명은 별도 증거 조회 없이 성공으로 전환하지 않는다. 이 단계에서 새 submit이나 새 AI dispatch를 호출해 판정을 시도하지 않는다.

### S2. 상태 정리와 정상 응답 분리

1. 기존 제한 재시도(최초 포함 3회, 대기 25ms/50ms)를 유지한다.
2. catch 범위는 접수 확정 이후 coordinator 호출에 한정한다. 제출 저장·dispatch·Redis 실패를 포괄적으로 삼키지 않는다.
3. 정상 전환 조건은 접수 확정, 기존 보호 계약 만족, 알려진 일시 후처리 실패, 영속 복구 가능성이다. 정확한 허용 오류는 기존 retry 분류와 대조한다. DuplicateKey 자체는 성공 근거가 아니다.
4. 조건 충족 시 앞서 얻은 실제 접수 상태로 기존 DTO를 반환한다. 무조건 PROCESSING/COMPLETED로 덮어쓰지 않는다.
5. 정리가 이미 완료된 경우 exact event slot·Session 증거로 no-op한다. 보호 오류·영구 오류·증거 불명은 기존 오류와 경보를 유지한다.

### S3. 영속 복구 보장

기본안은 기존 Session 후보와 접수/결과 증거를 durable한 복구 의무로 재사용하는 것이다. 별도 실패 기록이 없어도 접수 commit 직후 프로세스를 종료했을 때 후보가 발견돼야 한다.

- 유료 시험: `CONFIRMED`, non-null `attemptGroupId`, non-null `attemptGroupProjectionStatus`, terminal 미생성 조건이 접수 시 이미 성립하는지 검증한다. 과거/잘못된 자료를 임의 보정하지 않는다.
- 무료/legacy: 명시된 출처와 기존 보호를 확인해 불필요한 Billing 정리 호출만 제거할 수 있는지 검증한다. 필요한 로컬 완료 처리는 그대로 유지한다.
- 후보 순회: 처리 실패·장기 IN_PROGRESS 후보가 batch를 독점하지 않도록 bounded 순회 및 인스턴스 재시작 시 순회 재개/재탐색을 검증한다. DB 읽기도 bounded하게 만들되 새 인덱스 필요 여부는 실행 계획 검토 후 제시한다.
- 복구 시 매번 현재 보호와 lifecycle을 검증한다. coordinator의 CAS·unique slot·Session/outbox 원자성을 유지한다.
- `정리 재시도 성공`, `채점 진행 중`, `terminal 이벤트 생성`, `publisher 전달 완료`를 구분한다. 정리 성공만으로 시험 완료 또는 Billing 반영 완료를 주장하지 않는다.
- 실제 Billing HTTP 전송은 기존 outbox publisher 책임이다. 정리 worker가 reserve/confirm/새 채점 요청을 보내지 않는다.
- 후보의 영속 발견을 증명할 수 없으면 구현 완료로 처리하지 않는다. 최소 추가 marker 설계가 필요할 경우 접수와의 원자성·정리/삭제 연동·retention까지 먼저 명시한다.

### S4. 관측·운영 대응

- 정상 접수/후처리 지연/복구 성공/보호 차단/증거 불명/영구 오류를 고정 분류로 구분한다.
- 기존 안전 로그·경보를 재사용하며 중복 제한 뒤에도 장기 미수렴을 식별할 수 있도록 한다. 최초 지연·최근 시도 관측의 기존 저장 근거와 경보 임계값은 구현 검토에서 확정하고, 이번에는 새 운영값을 설정하지 않는다.
- 사용자·시험·그룹 ID, 원문 payload, 음성·발화, Secret/Token을 metric tag나 일반 오류 로그에 추가하지 않는다.
- 장기 미수렴은 운영 조사 대상이며 자동 성공/삭제/환급으로 종료하지 않는다. 운영자는 실패 분류·worker 설정·후보 누락·outbox 상태를 읽기 점검한다. 수동 재전송·DB 보정은 별도 승인 대상이다.

### S5. 검증 및 배포

- 단위 테스트와 격리 replica-set Mongo 테스트로 아래 표를 검증한다. 실제 사용자 제출·운영 DB 충돌을 만들지 않는다.
- `./gradlew clean test`, `./gradlew mongoIntegrationTest`, `git diff --check` 및 기존 공개계약 회귀를 수행한다.
- 별도 배포 승인 후 test에서 오류 주입으로 정상 응답과 재기동 후 복구를 검증한다. 실제 과금·사용자 데이터 없이 통제된 시험을 사용한다.
- test 근거 후 별도 승인으로 prod에 동일 변경만 전달한다. main/develop 차이는 확인하고 관련 없는 코드를 함께 올리지 않는다.
- 롤백은 호환되는 이전 이미지로 하며 접수·outbox·복구 증거를 삭제하지 않는다. 새 영속 필드를 도입한다면 구버전 reader와 롤백 호환성도 먼저 검증한다.

## 6. 부록: 코드 근거와 완료 조건

### A. 확인된 현재 구현

| 파일 | 확인 내용 |
| --- | --- |
| [ExamServiceImpl](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamServiceImpl.java) | submitAudio: 보호→submitQuestion→retry0 reconcile→DTO. 다른 reconcile 호출부도 존재한다. |
| [ExamGradingService](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamGradingService.java) | 신규 Job/coverage receipt 저장, replay와 결과 반환, dispatch 실패의 별도 오류 경로가 있다. |
| [Coordinator](../../src/main/java/web/tosunsaeng/domain/exams/attemptgroup/application/AttemptGroupStateCoordinator.java) | writer OFF no-op. ON에서는 eligible 판단 전 owner touch가 있다. |
| [Repository](../../src/main/java/web/tosunsaeng/domain/exams/domain/repository/ExamSessionRepository.java) | reconciliation 후보는 CONFIRMED/group/projection/terminal 조건을 가진다. |
| [Reconciler](../../src/main/java/web/tosunsaeng/domain/exams/attemptgroup/application/AttemptGroupStateReconciler.java) | writer ON 조건·기본 1초 poll, 전체 조회 뒤 batch limit, 후보별 실패 격리. |
| [Retry](../../src/main/java/web/tosunsaeng/domain/exams/attemptgroup/application/AttemptGroupTransactionRetry.java) | 1차 배포의 제한 재시도·unknown 우선 분류. |

현재 코드가 아래 목표를 모두 만족한다는 뜻은 아니다. 운영 상태 재조회는 이번 계획 작성에서 수행하지 않았다.

### B. 필수 검증표

| 번호 | 시나리오 | 기대 결과 |
| --- | --- | --- |
| A01 | 정상 접수·정리 성공 | 기존 응답/상태 동일 |
| A02 | 정리 transient 1~2회 후 성공 | 새 Transaction 재시도, submit·AI dispatch 추가 호출 없음 |
| A03 | 정리 3회 소진·접수 확정 | 기존 정상 접수 응답, DB 기반 복구 대상 유지 |
| A04 | 접수 commit 직후/응답 직후 종료 | 새 인스턴스가 후보 발견·후속 수렴 |
| A05 | DB 장애 지속 뒤 회복 | 접수 증거 유실 없음, 복구 지연 관측, 회복 후 수렴 |
| A06 | 접수 저장 실패/롤백/unknown | 거짓 성공 없음, 업무 전체 자동 재실행 없음 |
| A07 | 정리 unknown commit | 업무 재실행 금지, exact 증거로만 완료 판정, 불충분하면 오류 유지 |
| A08 | 같은 제출 replay/동시 요청 | 현행 Job 재사용·허용된 전송 멱등성 유지, 새 채점/추가 소비 없음 |
| A09 | batchSize 초과·앞 후보 지속 실패 | 뒤 후보도 제한된 순회 내 처리, 재시작 후 영구 누락 없음 |
| A10 | HTTP·콜백·worker 경합 | 결과 유실 없음, event slot별 outbox 중복 없음 |
| A11 | 무료/legacy/paid·writer OFF | 출처별 올바른 처리, 무료 추론 금지, 보호 유지, paid 미복구를 숨기지 않음 |
| A12 | 삭제·통합·탈퇴 경합 | 기존 차단 유지, 삭제 자료 재생성/owner 복원 없음 |
| A13 | 환불·과거 Session 기접수 | exact 기접수 완료 허용과 새 접수 차단 유지 |
| A14 | 영구 오류·분류 불명·증거 조회 실패 | 무조건 성공 전환 없음, 안전한 관측 |
| A15 | 완료 결과 replay·retryCount>0 | 실제 기존 상태 유지, 최초응시 정리 범위 확대 없음 |
| A16 | 콜백 저장 실패/요약 Transaction 실패 | 공통 helper catch로 잘못 성공시키지 않음 |
| A17 | writer 일시 중지/재개·publisher 중단 | 의무 보존·재개 수렴, 로컬 정리와 Billing 전달 구분 |
| A18 | 개인정보 포함 예외 | 허용된 분류 정보만 기록 |

완료 조건: A01~A18 및 기존 회귀 통과, 신규 외부계약 변경 없음, 재시작·batch 초과에도 복구 누락 없음, 정상 응답을 반환한 접수에 대한 영속 근거와 복구 책임 확인. 배포 성공만으로 이 조건을 대체하지 않는다.
