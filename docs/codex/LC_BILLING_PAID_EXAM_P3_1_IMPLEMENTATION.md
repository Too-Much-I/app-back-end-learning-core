# P3.1 MEMBER 대기·동일 요청 재개 구현

- 날짜: 2026-10-14 / 관련 TMI-116·TMI-128·TMI-122, 선행 TMI-218
- 브랜치: `feat/billing-paid-exam-p3`. 이전 P3 미커밋 변경 위 후속 작업이다.

## 1. 5줄 결론

1. strict MEMBER pending을 기존 operation에 영속하고 동일 요청/digest에 결속했다.
2. HTTP만 현재 pending·안전 조건을 확인해 같은 reserve를 재개하며 worker는 status만 관측한다.
3. 순수 대기는61~65초 간격으로 관측하며 장애 횟수를 소모하지 않는다. 실제 장애24시간/200회 한도는 유지한다.
4. NEEDS_REVIEW는 운영 확인이 기본이며 과거 순수 MEMBER 소진 인과 증거가 있는 경우만 한 번 재개한다.
5. 공개 route/자동 schema2 scheduler는 미연결·paid 기동 차단 유지다. 실DB·배포·commit/push는 수행하지 않았다.

## 2. 반드시 읽어야 하는 내용

`MemberWaitEvidence`는 최초/최근 관측, WAITING/RESUMING/INTERRUPTED/RESOLVED 처분, 순수 대기 누적 시간과 과거 소진 증거를 담는다. 원문 JWT/응답을 저장하지 않고 원 operation/session/mock/digest를 유지한다.

현재 pending은 재개 검토 근거이지 권리 승인 증거가 아니다. PREPARED·자기 guard·예약/신규 Session/commit/confirm/cancel 전송 증거 부재, owner/lifecycle 및 공통 lease/version을 확인하고 RESUMING을 먼저 영속한다. reserve 유실 뒤에는 status-first다. 이전 Session은 신규 Session 부재 검사 대상이 아니다.

순수 MEMBER 대기로는 새 소진 증거를 만들지 않는다. 예외는 이미 감사된 영속 과거 증거만 소비한다. 일반 `retry_exhausted`를 추측으로 보강하는 migration/import/운영 도구는 없다. 긍정 테스트는 과거 증거를 명시 주입한 것이며 실제 운영 데이터 전환 검증이 아니다. 원 증거·재개 시각을 보존하고 반복 격리 해제를 금지한다.

최초 생성+120일 정각부터 재개를 금지하고 guard/미완료 증거를 보존한다. NEEDS_REVIEW와 기존 `state_inconsistent` 경보 분류의 pending metadata를 남긴다. 실제 Sentry 수신은 미검증이다.

## 3. 결정 및 다음 단계

추가 제품 결정은 없다. 공개 연결·schema2 자동 scheduler·revoke·삭제/lifecycle 후속은 별도 단계다. 앱 purchase-account 보강과 Billing 실서버 receipt/owner 동작은 공동 검증이 필요하다. E06-owner 문구 정정은 공동 최종 검증·활성화 전 새 revision으로 해결하며 원 fixture를 덮어쓰지 않는다.

## 4. 위험 및 배포 gate

- 실제 Billing/Identity/TLS/E2E, 강제 종료·실 Mongo failover 및 전체 공동 상태 시나리오 완료를 주장하지 않는다.
- 실제 LC 서비스와 replica-set Transaction을 테스트하되 Billing 네트워크 경계는 mock했다. test-only oracle로 제품 구현을 대신하지 않았다.
- 자동 scheduler 연결·실환경 index/구 writer drain·설정 조합·Sentry 전달·앱 same-key 보강을 확인하기 전 활성화하지 않는다.
- 원인 불명/혼합 소진 및 auth/owner 차단 자동 해제는 없다. 상세 기록120일과 미완료 무TTL은 별도다.

## 5. 변경 파일과 유지 계약

| 파일 | 책임 |
| --- | --- |
| `MemberWaitEvidence.java` | 영속 이력·과거 소진 인과·대기 시간 계산 |
| `ReservationRecovery.java` | embedded 이력·cancel 전송 증거·예산 기준값 |
| `ReservationOperationExecution.java` | 검증된 격리의 제한적 lease/CAS 획득·해제 |
| `ReservationRecoveryStore.java` | 순수 대기 제외·실제 장애 예산 유지 |
| `PaidExamLifecycleService.java` | 안전 조건·관측/재개 Tx·version 검증·120일 차단 |
| `PaidExamRecoveryService.java` | HTTP same-key 재개·worker 관측·실제 장애 구분 |
| `PaidExamP3MongoTest.java`, `MemberWaitEvidenceTest.java` | 실제 LC/격리 Mongo 및 이력 단위 검증 |
| AGENTS·계획·ADR·CURRENT_STATE·WORKLOG | 승인/구현/후속 gate 기록 |

공개 URL/Method/body/성공 DTO/BaseResponse, AI `user_id=examId`, retryCount, S3/Redis, A/R2/simplified fixture bytes 유지. schema1 scheduler/legacy saga 분리 유지. 별도 collection/index나 회원증거 수집 시스템은 추가하지 않았다.

예상 밖 제품 변경 없음. 이전 P3 미커밋 변경·사용자 `.DS_Store`·동시 PLAN-012 문서 기록 보존. Billing/Identity 저장소 변경 없음.

## 6. 검증 기록

- `clean test mongoIntegrationTest bootJar` 성공 뒤 추가 경계 검사를 포함해 `test mongoIntegrationTest bootJar` 재실행 성공. XML 집계: 단위1,147개(기존 조건부1 skip)·Mongo208개(31개 P3/P3.1), 실패/오류0. bootJar·diff check 통과.
- 신규 Mongo11개: 영속 대기/worker reserve0·횟수0, HTTP 동일 요청 재개, 일반 소진 거절, 과거 소진1회 재개, cleanup 복귀/stale version, cancel 증거 거절,120일 정각,404 비대기, 실제 장애 횟수/시간 예산, auth circuit/동시 lease.
- 단위4개: 대기 구간 누적, 요청 결속, 혼합/누락 원인 거절, 증거 보존과 반복 해제 거절.
- Node migration 관련28개 통과, 기존 세 fixture 묶음 SHA256SUMS25개 일치. 원본 재생성/수정 없음.
- 검증 중 mock 재설정과 auth circuit 테스트 초기화 누락/예외 기대값을 수정했다. 제품 auth 차단은 유지했다.
