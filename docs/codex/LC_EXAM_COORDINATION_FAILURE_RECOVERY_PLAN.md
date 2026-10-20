# 모의고사 접수 후 상태 정리 오류 복구 계획

- 작성일: 2026-10-19
- 상태: 1차 제한 재시도·worker 격리 구현 및 test/prod 배포 완료. P2 정상 접수 응답 전환은 후속 구현 대상.
- 2026-10-19 후속 상세안: [정상 접수 응답·후속 상태 복구 계획](LC_SUBMISSION_ACCEPTANCE_RECOVERY_PLAN.md). 아래 본문은 최초 계획 기준이며 현재 구현 상태는 [1차 보고서](LC_EXAM_COORDINATION_RECOVERY_IMPLEMENTATION_2026-10-19.md), P2의 최신 범위·완료 조건은 후속 상세안을 우선한다.
- 신규 Jira: 없음. 관련 구현 이력: TMI-118(AttemptGroup), TMI-125(owner 보호).
- 승인 범위: 이번 요청은 문서 작성만. 코드 수정·실DB 변경·AWS 설정·commit/push·배포는 포함하지 않는다.

## 1. 5줄 결론

1. 채점 접수 성공 후 별도 상태 정리에서 발생한 DB 예외가 제출 API의 500으로 전파되는 구조를 보완한다.
2. 확인된 일시적 트랜잭션 오류만 상태 정리 단위로 최대 3회 시도하며, 음성 전송·채점 요청 전체는 반복하지 않는다.
3. 재시도 소진 후에도 접수와 안전한 후속 복구가 입증된 경우에만 기존 정상 접수 응답을 유지한다. 모든 오류를 무시하지 않는다.
4. 저장 성공 여부 불명, 삭제·탈퇴·회원 통합 차단, 영구 오류는 일반 충돌과 분리하고 보호를 유지한다.
5. 공개 API·DTO·Billing/AI 계약은 유지한다. 실제 경합 테스트와 안전한 진단 로그를 갖춘 뒤 별도 승인으로 배포한다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 2.1 현재 문제와 수정 결과

현재 제출 경로는 `채점 접수/전송 → 시험·Billing 그룹 상태 정리 → 응답`이다. 앞 단계가 성공해도 상태 정리에서 예외가 발생하면 앱은 실패 응답을 받는다. 이미 전달된 채점은 계속 진행할 수 있어 사용자 화면과 서버 처리 결과가 달라진다.

수정 후에는 상태 정리의 일시적 실패를 서버가 제한적으로 재시도한다. 복구 가능한 후처리만 남은 경우에는 앱에 제출 실패로 오인시키지 않는 것을 목표로 한다. 정상 접수 응답은 **문항 채점 완료나 전체 시험 완료를 뜻하지 않는다.** 기존 상태 조회 의미를 유지한다.

근거: [ExamServiceImpl](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamServiceImpl.java), [AttemptGroupStateCoordinator](../../src/main/java/web/tosunsaeng/domain/exams/attemptgroup/application/AttemptGroupStateCoordinator.java).

### 2.2 변경 범위

- 상태 정리 트랜잭션의 오류 분류·제한 재시도.
- 제출 및 콜백의 접수/저장과 후처리 실패 경계. 콜백은 실제 저장 증거가 있을 때만 성공 처리 가능하다.
- 기존 주기 상태 정리 작업의 복구 적격성·작업별 실패 격리와 후속 수렴 검증.
- 관련 요약 완료 경로의 unknown commit 분류 정합성.
- 비밀정보 없는 오류 관측 및 격리 회귀 테스트.

신규 큐·컬렉션·외부 API·인덱스는 기본 범위에 넣지 않는다. 기존 복구 구조로 보장할 수 없다면 정상 응답 전환은 보류하고, 추가 영속 구조가 필요한 이유를 별도로 제안한다.

### 2.3 유지할 계약

- v1 무료/v2 Billing **생성 정책**과 기존 제출·조회·콜백 URL/Method/DTO/BaseResponse 불변.
- `retryCount`, AI `user_id=examId`, S3 Object Key/Presigned URL, Redis Key 불변.
- Billing reserve/confirm/이용권 소비를 이 재시도에 포함하지 않는다.
- 사용자 소유권, 삭제/탈퇴/통합 차단, 환불 후 exact Session 기접수 보호 규칙 불변.
- 전체 시험 완료 조건·접수 증거·terminal event/outbox 멱등성 불변.
- 사용자 재녹음과 내부 상태 정리 재시도를 혼동하지 않는다.

앱 수정은 요구하지 않는 방향이다. 기존 성공 응답은 기존 DTO의 실제 접수 상태로 반환하며, 거짓 완료나 새 공개 오류 형식을 도입하지 않는다.

## 3. 사용자가 결정해야 하는 사항

- 제품 정책 재선택은 필요 없다. 위 안전 조건을 유지한 서버 내부 보완을 권장한다.
- 다음 구현 착수, Jira 등록 위치, commit/push 및 test/prod 배포는 별도 요청으로 진행한다.
- 기존 worker로 복구 보장이 불가능한 경로가 발견되면 해당 경로의 오류를 무시하지 않는다. 범위 확대가 필요한 경우 결정사항을 다시 제시한다.

## 4. 주요 위험과 미확인 사항

| 항목 | 현재 판단 / 대응 |
| --- | --- |
| 최근 오류의 정확한 Mongo 원인 | 10/12는 code112 WriteConflict 확정. 10/18·19는 상세코드가 없어 동일 원인으로 확정하지 않는다. 오류 클래스 전체를 재시도 대상으로 삼지 않는다. |
| 이미 접수된 작업 중복 실행 | 상태 정리 Transaction만 반복한다. AI 전송이나 submit 전체 재호출 금지. |
| unknown commit | 성공/실패 불명이므로 업무 Transaction 자동 재실행 금지. 새 읽기에서 신뢰 가능한 영속 증거로 판단하고, 불충분하면 기존 실패 처리·경보 유지. |
| 보호 차단 은폐 | owner/삭제/탈퇴/revoke 차단은 재시도 소진과 구별한다. 무조건 정상 응답 금지. |
| worker 미대상·기능 OFF | 기존 후보 조회 조건과 실제 worker 활성 상태를 검증해야 한다. 대상 누락/비활성 상태에서 복구를 약속하지 않는다. |
| 상태 이벤트 중복 | unique event slot, Session version CAS, terminal marker와 outbox 원자성을 유지한다. 충돌 예외 자체를 완료 증거로 사용하지 않는다. |
| 같은 사용자 경합 증가 | HTTP·콜백·scheduler가 같은 guard를 갱신한다. 실제 경쟁 writer는 미확정이며, 이번에 guard 제거/사용자별 보호 분할을 하지 않는다. |
| 요약 완료 재시도 | 현재 SummaryCompletionService는 unknown commit도 retryable로 분류한다. 이번 제출 오류의 호출 경로는 아니지만 비재실행 원칙과 맞추어 회귀 검증해야 한다. |

## 5. 구현 계획

### P1. 실패 분류와 상태 정리 재시도

1. cause chain에서 Mongo error label을 확인하는 좁은 분류를 만든다. 기존 공통 분류 재사용 가능성을 먼저 검토하고 관련 없는 전체 트랜잭션 리팩터링은 피한다.
2. `UnknownTransactionCommitResult`가 있으면 다른 retryable 원인보다 우선한다. cause 순서 때문에 unknown commit이 가려지지 않도록 한다.
3. 명확한 `TransientTransactionError`는 기존 트랜잭션 종료 후 **새 트랜잭션**으로 재시도한다. 최대 3회는 최초 1회+추가 2회이며 대기는 25ms, 50ms다. 실패한 트랜잭션 안에서 계속 쓰지 않는다.
4. `DuplicateKeyException`/낙관적 잠금 충돌은 기존 멱등 슬롯 의미를 검증한다. 임의 중복키 오류를 정상 수렴으로 간주하지 않으며 필요하면 영속 결과를 재조회한다.
5. 외부 AI/Billing 호출을 재시도 Transaction 밖에 유지한다. 사용 중인 Transaction manager와 propagation을 확인한다.
6. 요약 저장·완료의 원자 경계는 유지하며 unknown commit 전체 재실행만 차단한다. 결과 증거 부족 시 성공으로 판정하지 않는다.

### P2. 접수 응답과 후처리 경계 분리

상태 정리 실패를 지연 처리로 바꿀 수 있는 조건은 **모두** 충족해야 한다.

- exact exam/question/retry 또는 callback generation에 대한 접수/저장 증거가 커밋되어 있다.
- 현재 owner/삭제/탈퇴 등 해당 경로의 접근 보호를 통과한다.
- 실패가 알려진 일시적 상태 정리 오류이며 unknown commit/영구 오류가 아니다.
- 해당 Session이 실제 기존 복구 후보 조회에 포함되고 worker가 활성·실행 가능한 구성이며, 실패 뒤에도 후보로 남는다.
- 후속 상태 이벤트를 중복 없이 생성하고 완료까지 수렴하는 테스트가 통과한다.

조건을 만족하면 기존 DTO로 정상 **접수** 응답을 유지하고 정리 지연을 관측한다. 조건 확인 자체가 실패하면 안전하게 기존 오류 처리를 유지한다. 응답이 이미 저장된 작업을 다시 실행하게 만들지 않도록 동일 요청 replay도 검증한다.

무료/legacy 시험은 Billing worker 대상이 아닐 수 있다. 이를 유료 시험과 동일하게 복구된다고 가정하지 않는다. 불필요한 상태 정리 호출을 생략할 수 있는지는 기존 보호 의존성을 검토한 후 결정하며 owner 검증을 제거하는 지름길로 사용하지 않는다.

### P3. 기존 worker의 수렴과 관측

- 한 후보의 실패가 이후 후보 전체 처리를 중단시키지 않도록 후보별 실패 처리를 검토한다. 보호 실패를 성공으로 바꾸지 않고 원인별 경보를 남긴다.
- terminal 전환이 완료된 시험은 후보에서 정상 제외되고, 진행 중 시험과 일시 실패한 정리는 기존 정책에 따라 다음 주기에서 다시 확인되어야 한다. 정리 호출 성공 자체를 시험 완료로 간주하지 않는다.
- 매 주기 동일 오류를 무제한 경보하지 않도록 집계/중복 제한을 적용하되 장기 지연을 숨기지 않는다. 새 circuit/영속 상태 도입이 필요하면 범위 확대 승인 대상으로 둔다.
- 로그/Sentry에는 고정된 처리 단계, 예외 종류, Mongo code/codeName 및 허용한 label, 시도 횟수, 복구/소진 여부, 기존 상관 ID만 허용한다.
- URI·token·사용자 발화·응답 원문·전체 Mongo exception message는 기록하지 않는다. 기존 sanitizer를 통째로 해제하지 않는다.

### P4. 검증과 배포 기준

단위 테스트 후 격리 Mongo replica-set에서 실제 Transaction 경합을 반복 검증한다. 운영 DB에 충돌을 일으키거나 실제 사용자 시험을 재실행하지 않는다.

- 관련 집중 테스트 → 프로젝트 기본 `./gradlew clean test` → 기존 Mongo 통합 테스트 태스크로 검증한다. 테스트 환경의 Docker/Java 요구사항을 먼저 확인한다.
- 제출뿐 아니라 콜백·요약·worker의 Transaction 경계를 각각 검증한다.
- test 배포는 별도 승인 후 수행한다. 앱 제출 응답, 문항 완료, 시험 완료, 상태 이벤트 전달을 구분해 확인한다.
- prod 배포는 별도 승인과 test 증거 후 진행한다. 오류 수뿐 아니라 재시도 소진·미수렴 후보·중복 이벤트 여부를 관측한다.
- unknown commit 재실행, 보호 우회, 중복 전송/이벤트 또는 장기 미수렴이 발생하면 배포를 중단한다. 롤백은 호환되는 이전 이미지/설정으로 수행하며 실데이터 삭제·되돌리기를 하지 않는다. 이번 범위는 DB migration을 만들지 않는다.

## 6. 부록: 조사 근거와 테스트 표

### A. 운영 근거

| 일자 / Sentry | 확인 사실 | 한계 |
| --- | --- | --- |
| 10/12 / 7787409483 | owner guard WriteConflict112/TransientTransactionError, 콜백 재전송 후 성공 | 이번과 경쟁 writer가 같다는 증거 없음 |
| 10/18 / 7798380883 | 콜백 후 coordinator owner 갱신500, 후속 전체시험 완료 | Mongo 상세코드 미확인 |
| 10/19 / 7799688833 | 문항3 전송 성공→34ms 뒤 제출500→약24초 뒤 문항완료 | 정확 Mongo 원인 미확인; 전체시험은 조회 당시 IN_PROGRESS |

10/19 오류 시험은 팀원 검증계정의 21회이며 v2 Billing 생성흐름의 `SUCCEEDED/INITIAL/NEW_AFTER_COMPLETION` 증거가 있다. 수동완료6회를 원시험으로 참조했다. 기록 입력이 다음 회차 선택에 영향을 준 것과 이번 DB 예외의 인과관계는 별개다. 수동 입력 전에도 유사 사례가 있어 수동입력이 유일 원인이라는 결론을 내리지 않는다. 전체 빈도/오류율을 조사한 것은 아니다.

### B. 코드 근거

| 코드 | 확인 사실 / 수정 후보 |
| --- | --- |
| [ExamServiceImpl](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamServiceImpl.java) | submitQuestion 반환 뒤 retry0 reconcile 호출, 그 뒤 성공 DTO 반환 |
| [AttemptGroupStateCoordinator](../../src/main/java/web/tosunsaeng/domain/exams/attemptgroup/application/AttemptGroupStateCoordinator.java) | 직접 Transaction 및 중복/낙관적 잠금 catch, eligible 판단 전 owner touch |
| [UserOwnershipGuardService](../../src/main/java/web/tosunsaeng/domain/usermerge/application/UserOwnershipGuardService.java) | 사용자 ACTIVE 보호문서 revision/updatedAt 갱신 |
| [UserOwnedTransactionExecutor](../../src/main/java/web/tosunsaeng/domain/usermerge/application/UserOwnedTransactionExecutor.java) | 기존 최대3회/25·50ms retry, unknown commit 우선 분류 참고 |
| [AttemptGroupSummaryCompletionService](../../src/main/java/web/tosunsaeng/domain/exams/attemptgroup/application/AttemptGroupSummaryCompletionService.java) | 요약·완료 원자성 유지 필요, 현재 unknown commit retry 분류 보완 대상 |
| [AttemptGroupStateReconciler](../../src/main/java/web/tosunsaeng/domain/exams/attemptgroup/application/AttemptGroupStateReconciler.java) | 기존 주기 후보 처리 |
| [ExamSessionRepository](../../src/main/java/web/tosunsaeng/domain/exams/domain/repository/ExamSessionRepository.java) | CONFIRMED/group/projection 존재, terminal 없는 후보 조건 |
| [GlobalExceptionAdvice](../../src/main/java/web/tosunsaeng/global/exception/GlobalExceptionAdvice.java) | 공개500 로그에 exception class만 출력, 상세분류 관측 부족 |

Git 이력상 submit후reconcile은 63d0f7d(TMI-118,9/1), owner touch는60742be(TMI-125,9/5), 삭제 coordination 연결은4e60ba8(10/3)이다. 오늘 추가한 v2 route만의 신규 오류 구조가 아니다. 상세 운영 확인은 [WORKLOG](WORKLOG.md)에 보존한다.

### C. 필수 회귀 검증

| 번호 | 시나리오 | 기대 결과 |
| --- | --- | --- |
| R01 | 첫 상태정리 성공 | 추가시도 없음, 기존 응답 |
| R02 | transient 1·2회 후 성공 | 새 Transaction, 최대3회, AI 전송1회 |
| R03 | 3회 모두 transient·복구조건 충족 | 정상접수 유지, 후보 유지, worker 수렴 |
| R04 | 소진·worker OFF/미대상/증거 부족 | 무조건 성공 금지, 기존 오류·관측 |
| R05 | unknown commit 단독/중첩label | 업무 재실행 없음, 영속 증거 없으면 성공 금지 |
| R06 | 삭제/통합/탈퇴와 경합 | 차단 의미 유지, 늦은 저장·응답 우회 없음 |
| R07 | 영구 오류·불명 Mongo 오류 | 재시도/정상응답 변환 금지, 안전한 원인 기록 |
| R08 | HTTP·콜백·scheduler 동시 실행 | 접수/결과 유실 없음, 상태 이벤트1회 |
| R09 | 동일 submit/callback 재전송 | 기존 멱등 결과, 중복채점 없음 |
| R10 | terminal 생성 중 충돌·중복키 | exact 기존결과 확인, 이벤트 중복 없음 |
| R11 | 한 worker 후보 실패 | 다음 후보 처리 가능, 실패 원인 관측 유지 |
| R12 | v1무료/legacy/v2paid·writer OFF | 기존 보호/API 유지, 무료에 Billing 복구 가정 금지 |
| R13 | 요약 저장 중 transient/unknown | 원자성 유지, unknown 업무 재실행 없음 |
| R14 | 환불 후 기접수/새접수 | 기존 exact Session 보호·신규접수 차단 유지 |
| R15 | 로그/Sentry 입력에 비밀·발화 포함 | allowlist 정보만 남고 원문 제거 |

완료 조건은 위 검증과 기존 회귀 통과, 공개계약 불변, 조건부 지연처리의 영속 복구 증거 확보다. 일시 충돌을 재현하지 못했거나 운영 원인코드가 미확인이라면 그 한계를 결과 보고에 남긴다.
