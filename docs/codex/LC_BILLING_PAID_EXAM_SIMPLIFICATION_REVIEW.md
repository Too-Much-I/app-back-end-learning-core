# LC 유료 시험 P0 단순화 검토

- 날짜: 2026-10-14
- 상태: 사용자 요청에 따른 검토·권고만. 승인된 계획/ADR/AGENTS·제품 코드·공동 계약·fixture는 변경하지 않았다.
- 후속(2026-10-14): 사용자가 표의 세 구조 단순화만 승인해 계획/P0/ADR/AGENTS에 반영했다. 아래 본문은 당시 검토 근거다. MEMBER 관측 축소·NEEDS_REVIEW 예외 제거·설정 조합 추가 단순화는 채택하지 않았다. 제품/공동 계약/fixture/DB 변경 없음.
- 관련: PLAN-011/012/013, TMI-116/TMI-128/TMI-122/TMI-125, TMI-193/TMI-136.

## 1. 5줄 결론

1. P0에서 결과 receipt·revoke inbox·Session 접수 목록을 미리 분리/중복한 부분은 줄일 여지가 있다.
2. 기존 operation을 120일 결과 증거로 사용하고, revoke는 그룹별 단일 문서로 저장하는 방안을 권한다.
3. 기존 접수 receipt와 submissionCompletedAt을 공통 도메인 기능으로 분리하면 Session 접수 목록을 중복 저장하지 않아도 된다.
4. MEMBER 확정 대기는 앱 재개 중심으로 관측 빈도를 낮출 수 있다. NEEDS_REVIEW 자동 재개 예외 제거는 별도 계약 변경을 필요로 하는 선택안이다.
5. confirm 후 전환·멱등성·owner/삭제 fence·미완료 무TTL은 유지한다. 용량·개인정보 위험과 드문 수동 복구 부담을 명시하고 변경 전 합의한다.

## 2. 반드시 읽어야 하는 내용

[P0 설계](LC_BILLING_PAID_EXAM_P0_DESIGN.md)는 제품 구현 전 내부 설계안이다. 기존 [구현계획](LC_BILLING_PAID_EXAM_IMPLEMENTATION_PLAN.md)의 제품 동작을 유지하면서 더 적은 저장 구조로 구현할 수 있다. 복잡성 감소율이나 개발기간 절감치는 실측하지 않았으며 보장하지 않는다.

### 권장하는 세 가지 구조 단순화

| 항목 | 현재 P0 → 권장안 | 줄어드는 일 | 감수할 비용/제약 |
| --- | --- | --- | --- |
| 생성 결과 보관 | operation7일+별도120일 receipt → 기존 operation 한 곳에서120일 결과 보장 | 새 collection·두 index·이중 reader·동일Tx 복제·이관/불일치 복구 제거 | 7일보다 오래 저장해 용량 증가 가능. terminal 불필요 metadata 최소화 및 개인정보/hold 정책 검토 필요 |
| 환불 차단 수신 | revoke 문서+inbox → revoke 한 문서에 최초 eventId/digest/수신시각 포함 | 별도 inbox·중복 unique·두 문서 동기화/TTL 승계 제거 | 독립적인 장기 이벤트 감사/관측 이력은 제공하지 않음. 차단 증거 보존은 별개로 필요 |
| 답안 접수 | receipt+완료 marker+Session accepted 문항 배열 → 기존 receipt+완료 marker 재사용 | acceptedInitialQuestionNumbers 중복 저장/동기화 제거 | 접수 중에는 기존 receipt 조회 필요. 필수집합 snapshot·coverage·같은Tx/version 직렬화는 유지 |

세 안은 필수 답안 전체 접수→다음 문제, 미접수→동일 문제, confirm 후 exact 시험 교체, 이미 접수된 채점/요약 완료 정책을 바꾸지 않는다. 다만 저장 구현을 지칭한 공동 테스트/문서(T04 등)는 변경안을 전달해 맞춰야 한다. 기존 fixture bytes는 직접 고치지 않는다.

## 3. 사용자가 결정할 사항

권장 묶음은 위 세 가지 + 확정 MEMBER 대기 관측 축소다. 지금 요청은 검토이므로 반영하지 않았다.

추가 리스크 수용 선택: **NEEDS_REVIEW에 들어간 작업은 예외 없이 운영 확인**으로 단순화할 수 있다. 정상 MEMBER 대기→앱 보강→같은 key 재개는 그대로 지원한다. 제거 대상은 이미 격리된 작업의 좁은 자동 재개 예외뿐이다.

- 장점: 대기→소진 원인을 입증해 격리를 자동 해제하는 특별 분기와 관련 이력 구조를 줄인다.
- 비용: 드문 격리에서 회원이 보강을 끝내도 바로 시험을 시작하지 못하고 운영 확인을 기다린다. guard는 유지하므로 새 key로 우회할 수 없다.
- 경계: exact draft-3/E02-f와 승인된 동작을 바꾸는 선택이다. 사용자 승인과 Billing 공동 보완 revision/추가 fixture가 필요하며 현재 계약대로라면 예외를 구현해야 한다. 혼합원인·권한 오류를 느슨하게 자동 해제하는 대안은 권하지 않는다.

## 4. 줄이지 않을 안전장치와 위험

| 유지할 것 | 제거하면 생기는 위험 |
| --- | --- |
| Billing confirm 뒤 원 시험 차단·새 시험 활성·operation 성공의 원자적 저장 | 이용권 실패에도 기존 시험이 사라지거나, 두 시험 사용 가능/어느 시험도 사용 불가 |
| 같은 key·고정 mock/원대상과 status-first 복구 | 응답 유실을 새 시험/추가 소비로 처리 |
| 생성·삭제·소유권의 공통 fence/lease/version | 이미 삭제/탈퇴한 시험 부활, 다른 사용자/다른 시험 변경 |
| 신규 제출과 기접수 채점/파생 Summary 구분 | 이전 시험 새 제출 허용 또는 정상 접수한 결과 유실 |
| 미완료·unknown·격리 작업 무TTL | 결과 불명을 미소비로 착각해 재지급/중복 생성 |
| 독립 workload JWT trust/정확한 route 및 strict DTO | 다른 서비스/사용자 토큰의 권한 혼동, 계약 모순 성공 처리 |
| legacy exact 경계 | source 누락 신규 시험을 무료 legacy로 허용 |

기존 DB 용량 우려가 있으므로 단순화를 위해 operation 전체 원문을 무조건120일 남기는 방식은 권하지 않는다. 대상은 작은 coordination metadata이며 답안/AI/원문 token을 복제하지 않는다. 현재 운영의 일일 생성량·평균 BSON 크기·인덱스 사용량을 조회하지 않았으므로 추가 용량은 미확인이다. 필요하면 별도 승인된 집계로 판단한다. 미해결·분쟁/hold·차단 기록은120일 TTL과 별개이고 최종 purge 계약 미확정은 남는다.

## 5. 구체적인 단순화 설계 검토

### 5.1 operation 하나로120일 보장

현재 operation에는 user/key, Session/mock, reservation/group, state, terminalAt, failureCategory, purgeAt, recovery가 이미 있다. 최신 wire에 필요한 source/transition/거절/digest/최초접수 필드는 어느 안에서도 추가해야 한다. 이 동일 정보를 새 receipt로 복제하는 것이 필수는 아니다.

- terminal operation을 동일 collection의 결과 증거로 남긴다. 새 terminalAt/firstAcceptedAt을 재시도로 갱신하지 않는다.
- `purgeAt >= max(firstAcceptedAt+120일, 최초terminalAt+120일)`과 보존 hold 확인을 함께 지킨다. unknown/미완료/격리는 purgeAt 없음.
- terminal 뒤 더 이상 필요 없는 lease/재시도 상세만 정리 가능하다. 결과·원대상·권위 사실·삭제 접근 검증·남은 경보 전달에 필요한 자료는 지우지 않는다. 실제 필드 allowlist/최종 보존은 별도 검토한다.
- 기존 `uniq_exam_creation_user_operation`과 `ttl_exam_creation_purge`를 활용한다. 기존7일 purgeAt을 쓰는 성공/거절/cleanup/복구 모든 경로를 점검해야 하며 상수 한 곳만120일로 바꾸는 작업이 아니다.
- 기존 요청은 자동 소급 보장하지 않는다. 삭제된 Session replay404/owner deny와 activeGuard=false 완료 상태는 유지한다.
- T04의 '원 operation7일 삭제 뒤 별도 receipt replay'는 구현 방식에 종속된다. 새 보완 테스트는 '단기 캐시와 관계없이120일 결과 보존/재시작 replay'를 검사해야 한다. 원 fixture의 기대를 조용히 바꿔 통과했다고 보고하지 않는다.

### 5.2 immutable revoke 하나로 dedupe와 deny

그룹당 최초 event와 내용이 영구적으로 바뀌지 않는 현재 제품 규칙에서는 같은 사실을 inbox에 다시 저장할 필요가 적다. 하나의 문서에 group key·eventId·canonicalDigest·최초 시각·reason·confirmedSessionId를 보존한다.

- 기존 설계의 group unique와 eventId unique 두 index는 유지한다. 단일 group unique만으로는 같은 eventId가 다른 group으로 재사용되는 충돌을 잡지 못한다.
- eventId 충돌→group 충돌 우선순위, 동일 event+digest204, 같은 group 다른 event409 규칙은 유지한다. 동시 insert duplicate-key는 fresh read로 분류한다.
- deny 조회가 이 collection의 최초 사실을 참조한다. 별도 Session 변경이 필요하면 해당 변경과 원자적으로 반영한다. 단일 collection이라고 모든 Transaction을 없애는 것은 아니다.
- confirmedSessionId(null 포함)·reason 불변, 삭제/탈퇴 우선, 학습자료 upsert 금지 유지. 장기 protected Session 증거를 inbox120일 정책에 맞춰 지우지 않는다.
- 장기 raw HTTP 감사 payload를 새로 저장하지 않으며 원문 없이 고정 outcome metric으로 관측한다. 감사/개인정보 정책상 별도 inbox가 정말 필요한지 공동 확인한다.

### 5.3 existing submission receipt 재사용

현재 NotificationSubmissionTracker.accepted는 새 사용자 Job 직후만 호출되고 retry0만 반영한다. 완료 전 receipt에는 TTL을 설정하지 않으며, 전체 완료 시 같은 Transaction으로 Session.submissionCompletedAt을 기록하고 receipt에 완료+3일 expiresAt을 설정한다. 따라서 완료 후 receipt가 사라져도 marker가 남는다.

필요한 것은 이 로직을 알림 flag에 종속되지 않는 공통 접수 기능으로 옮기는 것이다. 새 Session의 서버 필수집합/coverage는 여전히 고정하고 원 Session version을 마지막 접수·새 생성 준비가 함께 갱신하도록 한다. 신규 유료 경로 marker 없음은 coverage가 검증되고 receipt가 완전할 때만 미접수로 해석한다. 기존 tracking OFF/legacy 자료를 marker 부재만으로 미완료로 추정하지 않는다.

P0의 acceptedInitialQuestionNumbers 배열은 이 경우 생략 가능하다. pending receipt의 기존 index/용량 및 삭제 cleanup은 계속 필요하며 보존된 Job의 사용자 실제 접수 증거를 대신 지우지 않는다. 시스템이 복구한 Job을 새 사용자 접수로 세지 않는다.

### 5.4 MEMBER 확정 대기에서 매분 원격조회 줄이기

exact §3.4는 앱이 purchase-account 보강을 끝내도 reserve를 다시 보내기 전까지 status가 대기를 그대로 반환한다고 명시한다. 따라서 '이미 안전하게 확정된 대기'에 매분 status를 호출해도 회원 보강 완료를 자동으로 감지하거나 작업을 진전시키지 못한다.

로컬 확정 대기는 앱의 같은 key 재개 중심으로 두고 느린 bounded 관측/최대기한 점검으로 바꾸는 안을 권한다. 원격 반복 상태 확인 간격은 현재60초 설계보다 길게 조정할 수 있으며 정확 기본값은 별도 운영 합의로 둔다. 완전한 worker 제거는 권하지 않는다.

응답 유실/전송 중·unknown commit/늦은 reserve 가능성·계약 모순은 확정 대기와 구분해 기존 status-first 복구를 유지한다. HTTP 재개도 현재 owner/lease/version/guard·예약/Session/cancel 증거 확인을 건너뛰지 않는다. 대기120일 도달 시 운영 확인, 실제 장애24시간/200회·auth probe도 유지한다. 비용은 앱 복귀가 없는 상태에서 원격 변화 발견이 늦어질 수 있다는 점이다.

### 5.5 설정 조합과 기타 과잉 분리

운영 조합은 '기존 운영 / reader·revoke 준비 / 새 유료 생성 / 신규 생성 중지·기존 작업 drain' 네 가지 지원 구성을 문서화하고 배포 체크리스트로 검증하는 편이 이해하기 쉽다. 이를 위해 또 다른 mode 환경변수를 추가하지 않는다. 기존 J/S/H/R/W/P·새 N/V는 보안/배포 책임이 달라 무조건 하나로 합치지 않는다. 기존 publisher-only drain을 막거나 flag OFF가 구 legacy 무료 생성 우회가 되게 해서는 안 된다.

guaranteeUntil처럼 최초 시각에서 계산되는 값은 읽기 때 계산할 수 있다. 반면 purgeAt은 Mongo TTL용 절대시각이므로 별개다. 중복 resumeDispatch와 기존 dispatch/intent를 무조건 둘 다 저장하기보다 기존 필드로 표현 가능한지 먼저 검토한다. 재개 중 unknown 전송 근거를 잃는다면 필드 수 축소보다 안전성이 우선이다.

legacy는 출시 시 잠깐 생성 writer를 drain하고 exact 목록을 봉인하는 간단한 운영 절차를 우선한다. 무중단 복잡한 이중 writer/온라인 자동 backfill은 처음부터 만들지 않는다. createdAt 한 줄 또는 source 없음으로 대체하는 방식은 writer·시계·백업 경계 증거 없이 안전한 단순화가 아니다.

## 6. 부록 — 읽기 근거와 검증

- [P0](LC_BILLING_PAID_EXAM_P0_DESIGN.md) §5.1~5.4/6.1~6.2: 중복 field/collection과 minute poll 설계.
- [ExamCreationOperation](../../src/main/java/web/tosunsaeng/domain/exams/domain/entity/ExamCreationOperation.java): 기존 identity/state/terminal/purge/recovery metadata.
- [Transaction service](../../src/main/java/web/tosunsaeng/domain/exams/application/BillingExamCreationTransactionService.java): TERMINAL_RETENTION 7일과 각 terminal 경로.
- [Submission tracker](../../src/main/java/web/tosunsaeng/domain/notification/NotificationSubmissionTracker.java): tracking flag, 완료 marker 및 완료 전 무TTL/후3일 TTL.
- [ExamGradingService](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamGradingService.java): 신규 사용자 Job 접수와 tracker 연결.
- [QuestionGradingJob](../../src/main/java/web/tosunsaeng/domain/exams/domain/entity/QuestionGradingJob.java): 실제 사용자 접수와 복구 구분 marker.
- [Exact snapshot](../../src/test/resources/contracts/lc-billing-paid-exam-simplified-exact-r1-fixtures-1/contract-snapshot.md) §3.4/4: status 대기 의미·격리 예외·120일 보장.

이번 검토는 코드/문서 정적 읽기다. 운영DB 크기·성능·장애확률 측정이나 제품/fixture 테스트는 실행하지 않았다. 새 검토서 링크/공백과 작업 diff를 확인한다. 계획·ADR/AGENTS·runtime·DB·배포/활성화·Billing 저장소 변경 없음. 권장안 채택 시 공동 구현 문서와 필요한 새 fixture 보완부터 반영한다.
