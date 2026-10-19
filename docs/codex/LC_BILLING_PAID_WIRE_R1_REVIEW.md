# LC 공동 기술 계약 R1 검토

- 날짜: 2026-10-14
- 대상: 사용자 전달 `lc-billing-paid-exam-wire-r1-draft-1`
- 관련: PLAN-011/012/013, TMI-116/TMI-128/TMI-122; lifecycle TMI-125
- 판정: 구조는 구현 가능한 방향이나 아래 정합성·호환 경계를 보완한 뒤 exact 공동 동결 필요. 이번 검토는 계약 최종 수락·구현/배포 승인이 아니다.
- 후속 상태: draft-2의 §9에서 아래 주요 보완 요청을 반영했다. 현재 검토 판정과 잔여 문구는 마지막 「draft-2 재검토」를 따른다.

## 수용 가능한 설계 방향

- restartContext에 원 대상과 INITIAL 문제 후보를 고정하고 기존 top-level mock는 replacement 후보로 유지하는 방안. phone과의 배타성, optional object의 absent/null 구분, strict reader 유지.
- source와 transition을 reserve/status에서 동일하게 복구하고 operation/Session에 영속하는 방안. TRIAL_TO_PAID는 Billing이 최초 trial을 증명하며 source enum만으로 권리를 판정하지 않는다.
- reserve 시 계획과 confirm 성공 증거를 구분하고, LC 로컬 마무리까지 기존 operation/guard/무TTL 복구를 유지하는 방안.
- CANCELED+영속 confirmRejection으로 irreversible 거절을 표현하는 방향. 단순 오류 응답이나 RESERVED만으로 보상하지 않는다.
- 기접수에서 파생되는 Summary 최초 생성/시스템 재시도 명시로 직전 Job 문구 의견은 해소됐다.
- 별도 역방향 profile/fixtures, eventId→version→stale 검사, legacy exact manifest 방향. 기존 A 계약/fixtures-1은 유지한다.

## 동결 전 보완 요청

### 1. restart 검증보다 기존 operation 재현 우선

성공한 전환이 current Session을 바꾸므로 같은 key 재전송에 current-owner/session 검증을 먼저 적용하면 정상 replay가 TRANSITION_CONTEXT_CONFLICT가 될 수 있다. 인증·기존 caller/owner fence 및 canonical 요청 동일성을 확인한 뒤 저장된 동일 operation 결과를 재현하고, 가변 current Session 기준 stale 검사는 신규 operation에 적용하는 순서를 명문화한다. 권한을 잃은 owner의 재호출까지 허용한다는 의미는 아니다. command TTL 뒤 영속 snapshot 재현에도 같은 원칙을 적용한다.

### 2. 삭제 continuation 실제 매핑과 누락 restartContext

현재 LC의 삭제 continuation은 phone API가 아니다. ExamSessionManager가 삭제 보존 증거의 sourceExamId/group/mock을 PreparedAssignment와 operation에 고정하고, saga는 일반 reserve 3-field를 보낸다. commitReservedSession 단계에서 DeletedExamContinuationStore.transferForCommittedSession을 호출한다.

따라서 새 계약에서 삭제 보존 sourceExamId를 restartContext.previousSessionId로 보낼지, 별도 호환 경로로 유지할지 결정해야 한다. LC에 원 Session이 없어도 Billing 저장 근거로 유효성을 검증할 수 있어야 하며 최신 Session으로 임의 대체하지 않는다. 보존 증거의 transfer 시점도 confirm 전 실패/보상에서 복구 가능한지 함께 검증한다.

Billing-linked group가 존재하는데 restartContext가 없는 요청의 결과도 확정해야 한다. 신규 일반 INITIAL로 무조건 취급하면 안 된다. 구 reader/worker drain과 별개로 기존3-field replacement를 허용할 조건 또는 명시적 거절을 정의한다. phone discovery 결과와 ACTIVE paid가 충돌하는 경우의 exact 오류/선택도 필요하다.

### 3. source/type/mock 조합과 OTHER_SOURCE_CHANGE 완료 정책

TRIAL_TO_PAID는 신규 INITIAL/SUBSCRIPTION/새 group/exact 원 대상/initialMock가 모두 일치해야 한다. REPLACEMENT에는 transition이 없고 immutable 권리를 유지한다. 새 group가 원 group와 다름, 새 Session이 원 Session과 다름, transition object 누락/불필요한 존재 등 부정 조합을 표와 fixture로 고정한다.

OTHER_SOURCE_CHANGE는 여러 source 전환을 한 값으로 묶는다. 최초trial→paid 밖에서 종료 전 접수 작업을 처리할지 무시할지 등 실제 LC 행동이 아직 '기존 종료 정책'에 위임돼 있다. 모든 transition에 같은 기접수 완료 예외를 적용하지 말고 유형별 동작을 명시한다. 허용 정책이 미확정이면 해당 경로를 activation 범위에서 제외한다.

### 4. 거절 snapshot과 terminal 오류 replay

confirmRejection은 CANCELED에서만 허용하고 RESERVED/CONFIRMED/EXPIRED 조합은 거절하도록 명시한다. rejectedAt과 기존 terminalAt의 관계(권고: 같은 Transaction에서 고정한 동일 시각), 일반 CANCELED와 revoke 거절의 공개 오류 차이, cancel 성공 DTO가 거절 증거를 생략할 때 status로 확인하는 절차를 동결한다.

TRANSITION_CONTEXT_CONFLICT가 reservation 생성 전에 발생해도 같은 key 실패를 보존하는 범위와 TTL 후 재현을 정해야 한다. 같은 실패를 재현한다는 약속을 현재 가변 상태의 재검증만으로 구현하지 않는다. 이미 reserve/confirm 성공한 operation의 응답 유실을 이 오류로 terminal 처리하지 않는다.

### 5. 공개 오류 호환 및 pending의 범위

새 EXAM_CREATION_PENDING/EXAM_TRANSITION_CONFLICT/ENTITLEMENT_REVOKED 이름은 현재 ErrorStatus에 없다. 하지만 현행에는 409 EXAM_CREATION_PROCESSING, 503 BILLING_TEMPORARILY_UNAVAILABLE 및 Retry-After:1 사용 경로가 있다. 새503/Retry-After:2로 전부 치환하는 것은 기존 계약 유지가 아니라 별도 공개 오류 변경이다.

새 유료 전환 미완료에만 새 pending을 적용할지, 기존 일반 saga·다른 key guard 충돌까지 바꿀지 범위를 명시하고 앱 reader 호환을 확인한다. UNKNOWN operation의 pending 표시는 유지할 수 있지만 내부 빈 ingress404 CONTRACT_ERROR·auth circuit·격리/경보를 일반 일시 장애로 합쳐 무조건 재시도하지 않는다. pending은 신규 key 재생성 허용이 아니다.

### 6. 역방향 event·digest 상세

reason이 이전 R2의 PAYMENT_REFUNDED/PAYMENT_REVOKED에서 PURCHASE_REFUNDED/PURCHASE_REVOKED로 변경됐다. 미구현 초안 간 변경임을 명시하고 어느 enum으로 동결할지 확인한다. 두 표현을 임의 혼용/alias하지 않는다.

표 순서의 compact JSON만으로 언어 간 동일 bytes가 자동 보장되지 않는다. escaping(한글·slash·제어문자·surrogate), invalid UTF-8, Unicode normalization 여부, SHA-256 인코딩, semantic에서 eventId만 제거한 순서, int64 경계와 millisecond 시각을 golden bytes로 고정한다. body unknown field400과 unknown schema/producer/scope422의 우선순위도 정의한다.

처음 관측한 higher version과 뒤늦은 lower version의 confirmedSessionId가 다른 경우 stale204인지 불변 위반409인지 명시한다. version별 충돌 검사는 이전 version 증거가 없으면 비교할 수 없으므로 최초 예외 고정과 별도 검증이 필요하다. wrong path403의 경계는 기존 사용자/Identity route를 가로채지 않도록 정확한 protected path 공간과 chain/port inventory를 바탕으로 정한다.

### 7. fixture·보존 gate

W01~W16/R01~R06은 제작 범위로 적절하다. 위 replay 우선순위, restart 누락·삭제 continuation·phone 충돌, type/source 부정조합, 일반cancel/거절 구분, 오류 호환, digest bytes와 예외Session 역순을 세부 케이스로 추가한다. 현재 token/bytes/hash나 실행 결과가 없으므로 테스트 통과나 전체 계약 동결로 보고하지 않는다.

terminal tombstone 폐기·개인정보 제거·legacy manifest 삭제 연계 및 실제 배포 origin/port/키 회수 SLA는 명시된 대로 미확정이다. 유료/revoke 활성화 전에 필요한 항목과 배포하지 않는 fixture 검증을 구분한다. product policy를 다시 선택할 필요는 없지만 OTHER_SOURCE_CHANGE의 실제 허용 행동은 현재 포괄 문구만으로 구현하지 않는다.

## 실행 범위

LC 코드의 삭제 continuation/오류/상태 DTO를 읽기 확인했다. Billing 저장소·실DB·AWS를 조회/수정하지 않았고 runtime/API/A계약/fixture를 변경하거나 테스트를 실행하지 않았다. 문서 검토 및 작업 기록만 갱신했다.

## draft-2 재검토 — 2026-10-14

사용자 첨부 lc-billing-paid-exam-wire-r1-draft-2 전체를 확인했다. §9.1의 replay/영속실패 receipt, §9.2의 삭제 claim·confirm 후 transfer/누락context/phone 충돌, §9.3의 부정조합·OTHER_SOURCE_CHANGE 활성화 제외, §9.4의 CANCELED 거절 정합성, §9.5 기존 공개409/503 유지, §9.6 enum/bytes/역순예외/namespace, §9.7 추가fixture에 앞선 주요 의견이 반영됐다. 해당 보완의 기술 방향에 추가 이견은 없다. 이전 요청을 그대로 미해결 목록으로 반복하지 않는다.

문구 정합성 보완 두 곳:

1. §1의 'operationId는 header에서 받고 body에 중복하지 않는다'는 모든 여섯 POST에 적용하면 현행 status와 충돌한다. LC status는 Idempotency-Key를 넣지 않고 POST /internal/v1/reservations/status body의 userId/operationId로 조회한다. reserve/해당 command에 대한 문구로 범위를 한정하고 status 기존 body를 명시해야 한다. status를 command로 바꾸는 변경은 요청하지 않는다.
2. §8 수락 요청의 '중간503/공개409'는 §4/9.5에 맞춰 '마무리/guard 기존409·일시 장애 기존503·신규 두409'로 정리하면 철회된 EXAM_CREATION_PENDING503 제안과 혼동되지 않는다.

fixture 제작 명세로 진행할 수 있는 수준에 가까워졌지만 exact 공동 동결/제작 실행 승인은 별개다. golden bytes/token/hash·양측 독립실행은 아직 없다. OTHER_SOURCE_CHANGE/phone+paid 제외의 제품 영향, 삭제continuation 제안 수락, 보존·삭제manifest 및 실제 connector/키·E2E gate는 문서에 명시된 미확정 상태로 유지한다. 코드/DB/배포 변경이나 테스트 재실행은 하지 않았다.

## draft-3 재검토 — 2026-10-14

사용자 첨부 lc-billing-paid-exam-wire-r1-draft-3를 직전 draft-2와 diff 대조했다. §1 command header 범위와 status body userId/operationId·읽기전용 예외, §8 기존409/503·신규 두409 구분이 반영돼 마지막 두 문구 의견은 해소됐다. revision/보완일과 §10 설명 외 추가 wire 변경은 없다. 이번 변경 범위에 추가 보완 의견은 없다.

다음 단계는 승인 후 별도 revision의 공동 fixture 제작과 golden bytes/hash 대조·양측 독립 검증이다. 현재 문서 검토 완료를 계약 최종 동결/fixture 실행/제품 구현/배포 승인으로 취급하지 않는다. 문서가 명시한 전환 제외의 제품 영향·삭제continuation 수락·보존/배포 gate는 남아 있다. 코드/DB/테스트 변경 없음.
