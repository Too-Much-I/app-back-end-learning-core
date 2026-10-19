# PLAN-011 남은 기술안 R1 — LC 검토

> 2026-10-14 후속 사용자 승인: 본문의 당시 위험 분석은 역사로 보존한다. 사용자가 동일key120일 이후 새 요청/추가 소비 위험을 명시 수용했으므로 §3 V07의 '과거key 식별 불가를 이유로 purge 불가'는 더 이상 현행 차단 조건이 아니다. 생성 중 삭제 queue/대기는 미접수·재시도 안내로 대체한다. MEMBER 앱 보강·재가입 유료 우선/조건부 target 복원도 확정 방향이다. 현행 계획은 `LC_BILLING_PAID_EXAM_IMPLEMENTATION_PLAN.md` §3.1을 따른다. exact 신규 계약/fixture·자료별 금융/소비/권한/미완료 보존 의존성은 남는다.

## 1. 5줄 결론

1. 2026-10-14 `plan-011-remaining-technical-r1-draft-1`과 Billing의 승인 정책 기록을 읽었다. 정책과 신규 기술 후보를 구분한다.
2. M1의 검증된 MEMBER 증거 보존+기존 탈퇴 deny 방향은 최신 정책에 맞으며 매 시험 Identity 동기 조회를 다시 요구하지 않는다.
3. 명시 삭제의 재응시 포기·신규 권리 INITIAL은 최신 정책으로 접수하지만 삭제 전달/개인정보 정리와 생성 경합의 exact 동작은 보완해야 한다.
4. phone paid INITIAL은 기존 계약의 신규 예외다. 응답 echo·복원 귀속·discovery/reserve 경합을 동결해야 한다.
5. V07의120일 숫자는 아직 후보이며 과거 key 재입력의 나이 초기화 문제를 해결하기 전 tombstone 자동 폐기는 수락할 수 없다.

## 2. 반드시 읽어야 하는 내용

관련 PLAN-011/012/013, TMI-116/TMI-128/TMI-122, TMI-125 및 기존 LC 삭제 TMI-193/TMI-136. 확인한 정책 원문은 `/Users/msde76/billing/docs/contracts/PLAN-011-J4-S15-V07-DECISIONS-2026-10-14.md`다. 이 문서에 기재된 사용자 승인 근거를 접수했으며 이번 LC 턴에서 별도 제품 구현 승인을 받은 것은 아니다.

정책 방향은 다음과 같이 최신화해 해석한다.

- MEMBER→GUEST 없음·탈퇴 외 계정 상태 없음 전제에서 검증된 회원 유형 증거와 로컬 withdrawal deny를 결합하고 일부 전파 지연을 수용한다. 최신 실시간 ACTIVE 증명과 같다고 주장하지 않는다.
- 명시적 사용자 시험/학습 삭제는 그 시험의 재응시 포기다. 해당 consumption 재사용/삭제를 이유로 한 복원은 하지 않으며 기지급 복원권·다른 권리·유료 잔여기간은 보존한다.
- 기존 LC 계획의 삭제 continuation claim/transfer는 **명시 삭제 경로에 한해** 이 정책으로 대체할 후속 동기화가 필요하다. 시스템 cleanup/단순 이탈/phone 경로까지 일괄 폐기하지 않는다.
- 진행 중 evidence 유지·종료 후 목적별 최소 보존은 방향이며120일 등 exact 기간과 purge 조건은 아직 기술 제안이다.

## 3. 확인·보완할 사항

### M1 — 기술 방향에 이견 없음, 최초 보강과 장애 응답 명세 필요

검증된 purchase-account 경계를 재사용하고 worker가 정규화 증거를 읽는 방식은 승인 정책에 부합한다. 기존 계정 성공 조회에도 증거 저장, 늦은 증거로 WITHDRAWN 부활 금지, 사용자 JWT 미보관 원칙을 유지한다. Identity의 claim 권위/발급 fence·계정유형 안정성 확인은 담당 간 증거가 필요하다.

남은 exact 항목:

- 증거 없음의 내부 HTTP/code/retryable, LC 공개 pending 매핑·앱의 기존 endpoint 보강 유도·같은 key 복구 조건. 정상 entitlement 부족/GUEST와 혼동하지 않는다.
- 신규/기존 paid와 이미 발급된 MEMBER_GRANT에 증거가 없는 경우의 진입/보강 시퀀스. 앱 보강 전 worker가 보류돼도 guard/evidence를 버리지 않는다.
- purchase-account 재호출 시 stable ref 유지와 Lifecycle deny/복구 coverage fence의 원자적 우선순위, 구 계정 데이터 자동 backfill 금지 테스트.
- 5분 경보/1시간 운영 조치는 후보이며 기존 SLO와 비교 후 확정한다. feed coverage 불명 시 기존 safety fence를 임의 완화하지 않는다.

이번에 Billing PaymentController나 Identity 구현을 직접 재검증한 것은 아니다. 첨부의 현행 구현 설명과 정책 원문을 근거로 검토했다.

### D1/D2 — 삭제와 원격 ACK를 분리하되 명시적 checkpoint 필요

신규 route를 A 기존 allowlist에 몰래 포함하지 않고 별도 인증 보완 revision으로 관리하는 것은 적절하다. 예약 audience 재사용 자체는 아직 수락된 것으로 표시하지 않는다. exact route/method/port·OFF/auth/query 우선순위·issuer/sub/aud 및 old/new allowlist 회귀가 필요하다.

현재 LC worker는 PREPARING_RESTART/SAFE_TO_START_LEARNING 이후 S3/Mongo cleanup으로 진행한다. 새 ACK 대기를 그 앞에 단순 삽입하면 Billing 장애가 실제 자료 삭제까지 막을 수 있다. 다음을 구체화해야 한다.

1. 학습 접근 차단, 최소 전달 payload 영속, 원문 삭제 가능, Billing ACK, 신규 시험 허용, 삭제 operation 완료를 별도 checkpoint로 정의한다. ACK 전에도 자료 정리는 계속 가능하되 관련 새 생성은 차단하고 최소 전달 증거를 남긴다.
2. 생성이 이미 진행 중일 때 '삭제 요청 보존'이 public writeBlocked를 즉시 켜는지 구분한다. 삭제가 생성 종료를 기다리면서 그 생성의 finalize를 자기 deny로 막는 상호 대기를 만들지 않도록 공통 fence 순서를 정한다. 권리 성공은 보존하되 삭제 요청 때문에 학습자료를 재생성하지 않는다.
3. 그룹 전체가 아닌 exact Session 삭제 fact 적용 및 old Session 지연삭제를 검증한다. source owner 변경 후의 허용 event fence, 잘못된 귀속/미존재 group의 HTTP/code/재시도는 아직 exact가 필요하다.
4. 같은ID다른내용/다른ID동일사실에서 '동일사실'에 userId·schemaVersion 포함 여부와 정규화 입력 필드를 고정한다. 소유권 확인 후 중복204, 충돌 우선순위와 unknown schema·크기·DB장애·owner-blocked 오류 표를 만든다.
5. deletedAt은 접근 차단 commit 시각이며 재시도/개별 Session 물리삭제 완료 시각으로 바꾸지 않는다. 다중 Session inventory의 최초 payload 생성 시점·eventId 불변성을 명시한다.

### D3 — 최소 restartContext 방향 가능, 삭제 후 replay도 exact화 필요

보존한 실제 접수 판정으로 기존 세 startReason을 유지하고 Billing의 삭제 fact로 INITIAL만 허용하는 방향은 가능하다. source/group/mock/접수 evidence 없는 기존 삭제 row는 임의 추정하지 않는다.

- 일반 RESTART_INCOMPLETE→REPLACEMENT 규칙보다 명시 삭제 fact가 우선함을 조합표/fixture에 넣는다. 새 INITIAL source/transition은 기존 일반 restart 규칙대로 exact 원대상/새group과 대조한다.
- 새 key INITIAL과 **삭제 후 과거 성공 key replay**를 분리한다. 과거 금융 성공은 보존하지만 LC 공개 응답은 삭제된 Session을 재생성하거나 접근 가능한 성공으로 반환하면 안 된다. 기존 삭제 오류/404 등 어떤 응답으로 수렴할지 현재 공개 계약과 함께 명시한다.
- 삭제 정책의 대체 범위는 명시 사용자 삭제다. 기존 store의 transfer 경로를 전역 제거하기 전에 비삭제/phone 호출과 feature 조합을 확인한다.

### P1 — paid-first는 후보 유지, phone INITIAL exact 응답 필요

일반 restartContext와 phone의 배타성·source paid/보상권 비이전은 유지한다. 다만 아래는 기존 phone REPLACEMENT 계약을 확장하므로 별도 합의/fixture가 필요하다.

- paid INITIAL의 continuationReason/id echo 여부, expectedAttemptGroupId와 새 attemptGroupId 비교 규칙, transition 없음, reserve/status 불변 snapshot, LC operation 저장/검증 표.
- discovery에서 GRADING pending/204/오류가 나오는 경우와 reserve 시 paid 생김/만료/차단의 조합. paid 우선이 기존 GRADING pending·COMPLETED NOOP를 언제 넘지 않는지 명시한다. 존재 불명은 free fallback 금지다.
- source Session/group의 차단과 target 새 INITIAL·복원이 Billing에서 어떤 owner epoch/claim에 결속되는지, source 원 소비당1회와 복원 grant의 수령 owner를 명시한다. 재가입만으로 복원 자격을 만들거나 기존 rebind 허용범위를 확대하지 않는다.

### V07 —120일 재전송 제한만으로 tombstone 폐기 안전성 부족

현 LC saga는 같은 user/key의 operation·durable Session replay가 모두 없으면 새 operation을 준비한다. terminal operation에는 현재7일 보존이 적용된다. 따라서 다음 경로가 가능하다.

`오래된 operation/Session·실패 tombstone 폐기 → 앱이 과거 UUID key 재전송 → LC가 현재 시각의 PREPARED 생성 → 120일 age 검사 통과 → Billing에도 최초 요청처럼 도착`

이는 기존 operation의 원 생성시각을 검사하는 모든 worker/HTTP retry 제한을 넣어도, **처음 본 것처럼 재생성되는 공개 요청**에는 막힘이 없다. 첨부의 조건부 purge 보류는 적절하지만 이 사례를 명시적 폐기 차단 조건/fixture로 추가해야 한다.

- key 최초성/원 생성시각을 검증할 최소 fence 또는 동등한 증거가 사라진 후 신규와 과거를 구분하는 계약이 필요하다. LC가 새로 붙인 authenticated issuedAt만으로는 과거 UUID 재입력을 구별하지 못한다.
- 방법은 여기서 임의 채택하지 않는다. 증명이 없으면 자동 폐기를 켜지 않는다는 제한을 유지하되 그것을 무기한 개인정보 보존 정책으로 대신하지 않는다. 권한·개인정보 최소화/폐기 담당의 공동 결정을 받는다.
-120일은 생성시각 기준 자동 실행 제한이고 terminal 상세120일은 최초 terminalAt 기준이다. 둘의 기준·정각 경계·clock/backup 복원·승인 수동 재처리 조건을 구분한다.
- 기존 reservation 복구의24시간/200회 격리와 outbox 보존/재처리는 유지한다. 새120일 상한은 그때까지 계속 retry하거나 이전 격리를 자동 해제하는 승인이 아니다. unresolved evidence는120일 도달만으로 삭제하지 않는다.
- 무료3년/금융5년/legacy 종료후120일도 열린 operation·보호 Session·지연 event/백업과 개인정보 제거 조합 검증 뒤 확정한다.

## 4. 주요 위험·미확인 사항

M1/삭제 최신 정책 방향은 이전 검토를 진전시키지만 S15/V07이 모두 해결된 것은 아니다. 신규 route/phone 예외/보존은 기존89개 검증 범위 밖이며 A/R2 원본을 변경하지 않는다. 추가 정상 기대값을 만들기 전에 위 exact 조합을 동결한다. 테스트 모형의 통과로 실제 삭제/금융 Transaction 안전성을 주장하지 않는다.

## 5. 직접 관련된 후속 작업

Billing 회신에는 M1 보강·오류 명세, D1 checkpoint/생성 경합 및 삭제 오류 표, D3 삭제 replay 응답, P1 phone snapshot/owner 조합, V07 과거 key 재입력 경계의 다섯 항목을 요청한다. 이후 LC 계획의 P0/P3/P4/P6와 삭제 계획을 최신 정책에 맞춰 동기화하고 별도 contract/fixture revision을 검증한다. 이번에는 회신서만 작성했으며 계획/ADR/AGENTS나 제품을 변경하지 않는다.

## 6. 부록 — 근거와 작업 범위

- 사용자 첨부 `plan-011-remaining-technical-r1-draft-1` 및 Billing `docs/contracts/PLAN-011-J4-S15-V07-DECISIONS-2026-10-14.md` 읽기 확인.
- LC `src/main/java/web/tosunsaeng/domain/exams/application/BillingExamCreationSaga.java`: start/findOrPrepare의 증거 없는 key 신규 준비.
- LC `src/main/java/web/tosunsaeng/domain/exams/application/BillingExamCreationTransactionService.java`: TERMINAL_RETENTION7일.
- LC `src/main/java/web/tosunsaeng/domain/learningrecorddeletion/application/DeletionWorker.java`: REQUESTED/FENCED/WAITING_COORDINATION/PREPARING_RESTART/SAFE_TO_START_LEARNING 및 cleanup 순서.
- LC `src/main/java/web/tosunsaeng/domain/learningrecorddeletion/infrastructure/DeletedExamContinuationStore.java`: 기존 coordination/claim/transfer 경계. 실제 production wiring/활성화를 확인한 것은 아님.

본 검토서/CURRENT_STATE/WORKLOG만 변경. 기존 dirty 보존, 코드/계약/fixture/계획/ADR/AGENTS/타저장소/DB/배포/Jira/commit/push·외부전송·Secret/Token 기록 없음. 문서검토이므로 테스트 재실행 생략, git diff --check 수행. 이번 회신은 최종 계약수락/구현 승인 아님.
