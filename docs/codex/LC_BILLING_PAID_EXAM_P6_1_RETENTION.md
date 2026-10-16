# LC P6.1 — 120일 결과 보관·안전한 정리 준비

- 날짜: 2026-10-14 / 브랜치: `feat/billing-paid-exam-p6-1`
- 승인: P6·TMI-220 전달/머지 이후 사용자 후속 선택 「LC P6.1: 120일 보관·안전한 정리 준비」.
- 관련: LC TMI-116/128/122/125·TMI-193/136, 공동 TMI-217/220. 별도 Jira 생성/변경 없음.

## 1. 5줄 결론

1. 새 paid operation의 최초접수 시각을 UTC millisecond로 고정하고 120일 정각부터 보장 창 밖으로 판정한다.
2. 기존 operation 한 건만 결과 근거로 보관하며 별도 receipt/복제 collection을 추가하지 않았다.
3. paid terminal은 `purgeAt`을 설정하지 않는다. 정리 검토 시각 `detailPurgeEligibleAt`만 기록한다.
4. 미완료 요청은 120일 정각부터 원격 재시도 대신 NEEDS_REVIEW로 유지한다. 24시간/200회 조기격리·owner/삭제/인증 fence는 유지한다.
5. 읽기 전용 적격성 검사를 제공한다. 실제 purge·일괄 이관·실환경 검사·기능 활성화·운영배포는 구현/실행하지 않았다.

## 2. 반드시 읽어야 하는 내용

### 저장과 재전송

- `ExamCreationOperation.firstAcceptedAt`: 새 paid selection을 고정하는 최초 PREPARED Transaction에서 기존 서버 createdAt를 millisecond로 정규화한다. 원 유효 요청의 서버 시각이며 lease/재전송/로그인/복구로 바뀌지 않는다. commit 결과가 불명확하면 기존 operation/guard를 유지한다.
- `guaranteeUntil = firstAcceptedAt + Duration.ofDays(120)`. 계산값으로 사용하며 별도 중복 저장을 요구하지 않는다. `now < guaranteeUntil`만 창 안이다. JWT의 nanosecond 규칙은 변경하지 않는다.
- `terminalAt`: 최초 로컬 마무리/영속 실패의 시각을 paid 경로에서 millisecond 정규화한다. Billing의 confirm 시각인 `confirmedAt`과 분리한다.
- `detailPurgeEligibleAt = max(guaranteeUntil, terminalAt + 120일)`. 정리 검토의 최소 시각일 뿐 삭제 권한이 아니다. 재전송이나 completeRecovery로 연장/초기화하지 않는다.
- 기존 Mongo `purgeAt` TTL index는 legacy 계약을 위해 유지한다. 새 paid terminal의 `purgeAt`은 null이므로 해당 TTL이 지우지 않는다. 미완료·격리 기록에도 TTL을 설정하지 않는다. 기존 paid 기록의 purgeAt를 일괄 unset하는 migration은 수행하지 않았다.
- 120일 밖에도 남은 terminal operation은 기존 owner/삭제검사를 거쳐 같은 결과를 replay한다. 성공 Session이 없거나 삭제됐으면 재생성하지 않는다. 모든 key 근거가 적법하게 폐기된 후 같은 UUID가 새 요청이 될 수 있는 승인 위험은 유지한다. 새 영구 key registry를 만들지 않는다.

### 자동 복구

- HTTP 진입, worker 시도 및 실제 원격 호출 직전에 paid 최초접수 창을 검사한다. 만료·최초접수 근거 없음·최초시각 이전 clock이면 기존 `NEEDS_REVIEW`, 고정 내부 사유 `paid_acceptance_window_closed`, 기존 경보 `state_inconsistent`로 남긴다. 원 guard/결과/operation은 유지하며 새 reserve나 cancel을 추정하지 않는다.
- 일반 retry 소진을 MEMBER 대기로 바꾸지 않는다. 이미 순수 MEMBER 대기로 격리된 제한적 재개 경로도 120일 만료 관측을 CAS로 영속 기록하므로 시계 역행만으로 다시 열리지 않는다. 원 MEMBER exhaustion proof는 보존한다.
- 기존 24시간/200회 예산이 먼저 적용된다. 120일까지 계속 자동 재시도하는 기능은 아니다. worker 새 reserve/Session 생성 금지는 유지한다.

## 3. 정리 검토·운영 준비

`PaidOperationRetentionInspector`는 Spring bean/CLI/scheduler가 아닌 명시 호출 read-only 도구다. 단일 operation을 primary-majority로 읽고 알려지지 않은 TTL index, paid 증거 누락, 미완료/activeGuard, 시각 경계, recovery/lease/미전달 경보를 검사한다. 식별자/원문 body/token을 보고 결과에 넣지 않는다.

호출자가 제공하는 `Preconditions`의 clock 정상·restore coverage·hold 없음·연결 작업 종료·목적별 보존 승인 값은 **외부 검증에 대한 명시적 확인값**이다. 이 도구가 Billing 원장, 분쟁, 법정 보존 또는 backup coverage를 자동 증명하지 않는다. 기본/누락 확인은 fail-closed다. `REVIEW_CANDIDATE`는 검토 후보일 뿐이며 최종 purger는 별도 승인 후 version/hold/연결 증거를 원자적으로 다시 확인해야 한다.

운영 순서(이번 미실행): writer drain → 대상/reader coverage inventory → 기존 TTL/시각/미완료 근거 검토 → backup 원 시각·deny 복원 확인 → 외부 보존/hold 확인 → 읽기 전용 후보 점검 → 별도 purge 설계/승인. 큰 clock 이상 또는 복원 coverage 불명 시 중단한다. deny/금융/무료권 소비·복원1회 근거와 학습 답안을 operation 120일 기준으로 삭제하지 않는다.

| 대상 | P6.1 처리 | 전환 전 확인 |
| --- | --- | --- |
| 새 paid operation | firstAcceptedAt·detailPurgeEligibleAt, TTL 미설정 | reader/서버 시간·동일 key 재시도 검증 |
| 이전 paid·최초시각 없음 | 자동 backfill 없음, 복구 운영 확인 | 원 commit 증거/기존 purgeAt·coverage 별도 승인 |
| 기존 legacy operation | 기존 7일 계약 유지 | 이미 소실된 key를 120일 보장 대상으로 추정하지 않음 |
| 미완료/unknown/격리 | 무TTL·guard/증거 유지 | 기존 조기격리·수동 복구 |
| 창밖 terminal 증거 | 접근검사 후 기존 결과 replay | 최종 목적별 보존·hold·연결작업 검토 |

## 4. 검증 매핑과 한계

| 항목 | 실행 가능한 LC 검증 |
| --- | --- |
| T01 | millisecond 정규화·−1ms/정각/+1ms, 창 직전 실제 status 마무리, 정각 이후 HTTP/worker 원격 무호출 |
| T02 | lease/복구로 최초·terminal/정리 경계 불변, 일반·MEMBER 격리의 clock 역행 비재개 |
| T03 | 모든 paid terminal writer의 TTL 미설정, unresolved/hold/lease/미전달 경보·clock/restore 확인 실패 거절 |
| T04 대체 저장 검증 | 기존 operation을 유지해 7일/120일 이후 실제 lifecycle replay, 원 시각 불변. 별도 receipt 없음 |
| T05 | 격리 DB에서 key 근거 제거를 모의한 뒤 같은 UUID가 새 operation이 될 수 있음을 확인. 실제 purge/소비는 미실행 |
| T06 | 격리 문서 backup round-trip 후 원 시각 유지·삭제된 성공 비부활, 기존200회 조기격리 유지 |

- `PaidOperationRetentionTest`와 `PaidExamP3MongoTest`에 신규 회귀를 추가했다. 원 fixture T04의 '7일 operation 삭제→별도 receipt'는 실행하지 않았고 통과로 표시하지 않는다. A/paid R2/simplified fixture bytes/hash는 불변이다.
- 실제 Billing 공동 HTTP/경합·법정 hold·운영 backup/restore와 최종 purge coverage 검증은 아니다. `REVIEW_CANDIDATE`가 구현 완료 또는 배포 허가를 뜻하지 않는다.
- 최종 명령/건수는 CURRENT_STATE와 WORKLOG에 기록한다. 공개 API/DTO/BaseResponse·AI user_id/Callback·retryCount·S3/Redis·Billing wire 불변.

## 5. 변경 파일·범위와 후속

- `ExamCreationOperation`: 내부 보존 시각/paid terminal TTL 분리.
- `PaidExamLifecycleService`, `PaidExamRecoveryService`, `ReservationOperationExecution`, `ReservationRecoveryStore`: 최초접수 기준 및 durable 운영확인 경계.
- `PaidOperationRetentionInspector`: 읽기 전용 검토; 테스트 2개 파일·계획/ADR/AGENTS/작업 기록 갱신.
- 기존 사용자 `.DS_Store`는 보존했고 제품 범위 밖 변경은 없다. P6 전달 PR #42와 P6.1 미커밋 변경을 구분한다.
- 남은 작업: 공동 T04 저장방식 설명/coverage, Billing·LC 환불 경합/E2E, legacy 전환·실제 index/backup/목적별 purge 승인. 기존 paid 활성화 차단을 해제하지 않았다.
