# LC–Billing 유료 시험 단순화 exact 계약 R1

- 날짜: 2026-10-14 / revision: lc-billing-paid-exam-simplified-exact-r1-draft-3
- 상태: 공동 검토용 규격 제안. 제품 정책은 승인됐으나 이 exact 규격의 양측 수락·새 fixture·runtime 검증은 미완료.
- 관련: PLAN-011/012/013, TMI-116/TMI-128/TMI-122/TMI-125, LC 삭제 TMI-193/TMI-136.
- 정책: [단순화 승인](PLAN-011-SIMPLIFICATION-DECISION-2026-10-14.md). 기반: [R2 draft-2](LC-BILLING-PAID-EXAM-CONTRACT-R2.md), [J3/J4 draft-2](LC-BILLING-PAID-EXAM-J3-J4-ADDENDUM-R1.md).

## 1. 5줄 결론

1. MEMBER pending은 내부503 MEMBER_EVIDENCE_PENDING, LC는 기존503으로 안내하고 앱의 purchase-account 보강으로 같은 요청을 재개한다.
2. 생성 중 삭제는 기존409 EXAM_CREATION_PROCESSING으로 미접수, 삭제된 시험의 과거 성공 key는 기존404 EXAM_4004로 수렴한다.
3. 120일은 UTC Duration120일이며 최초 접수부터 계산한다. 정각부터 보장 제외, 미완료/금융/소비/복원 근거는 자동 삭제하지 않는다.
4. phone은 target paid-first, 응답의 phone echo와 새 INITIAL group를 구분해 저장하며 자격 있는 원 consumption당 target 복원1회다.
5. 아래 E/T/P/C 검증 명세를 양측 수락한 뒤 별도 fixture revision을 제작한다. 기존 A/R2/fixtures-2 bytes는 유지한다.

## 2. 적용·우선순위

본 문서는 승인된 단순화의 오류·기간·phone 조합만 구체화한다. 일반 reserve/status source/transition, confirm 성공 보존, 무료권 미복원 삭제 정책, workload 보안/16KiB/strict JSON은 기반 R2/J3를 따른다. 차이는 다음과 같다.

- phone INITIAL은 본문 §5에 명시한 신규 허용 조합이다.
- 영구 same-key 보장 및 생성 중 삭제 대기열은 제거한다.
- 생성/삭제 public 오류의 정확 값은 §3을 따른다.
- R2 전체 문구를 대체하거나 기존 fixture의 보장 범위를 소급 확대하지 않는다.

새 삭제 route·인증 allowlist/과거 owner 판정·자료별 최종 폐기는 [회신 반영 R2](PLAN-011-REMAINING-TECHNICAL-REVIEW-RESPONSE-R2.md)의 별도 검토 범위다. 본 문서 수락만으로 그 경로도 완료됐다고 보고하지 않는다.

## 3. 오류·응답 규격

### 3.1 내부 LC→Billing 오류

기존 `code,message,retryable,correlationId` 네 필드를 사용한다. null/타입 coercion/unknown/duplicate/trailing을 거절한다. correlationId는 응답별 진단 문자열이며 동일 요청 귀속/멱등성 판단에 사용하지 않는다. 업무 오류 body에는 token·userId·source 원문을 출력하지 않는다.

| 조건 | HTTP / code | retryable / Retry-After | 고정 message |
| --- | --- | --- | --- |
| source가 MEMBER 근거를 요구하지만 근거 없음 | 503 MEMBER_EVIDENCE_PENDING | true / 1 | The operation is waiting for member evidence and request resumption. |
| 현재 owner/fence 불일치 | 409 OPERATION_OWNER_MISMATCH | false / 없음 | The operation owner does not match the current owner. |
| 일반 저장소/통신 불가용 | 503 BILLING_TEMPORARILY_UNAVAILABLE | true / 1 | 기존 오류 message 유지 |
| J3 pre-reserve terminal status 증거 | 409 RESERVE_REJECTED_BEFORE_CREATION | false / 없음 | The reserve operation was rejected before reservation creation. |
| 알려진 operation 없음 | 404 OPERATION_NOT_FOUND | false / 없음 | 기존 오류 message 유지 |

MEMBER pending은 자격 없음/Guest로 확정하는 오류가 아니다. lifecycle WITHDRAWN이나 권한 실패를 pending으로 덮어쓰지 않는다. 검사 순서는 서비스 인증/형식 → 현재 owner/lifecycle deny → 기존 operation/digest/replay → 신규 source에 필요한 MEMBER 근거/자격 검사다. 이미 확정된 성공은 새로운 MEMBER 보강 부재만으로 실패로 바꾸지 않는다. 현재 접근 허용 owner 검사는 replay에도 필요하다.

MEMBER pending 전에 새 예약/consumption/복원 결과를 만들지 않는다. §3.4의 비종료 대기 receipt는 먼저 commit하며 status 조회에도 사용한다. LC는 PREPARED operation과 원 요청을 보존하며 앱의 같은 key 재시도로 재개한다. worker는 status-only 경계를 유지해 reserve를 새로 보내지 않는다. status OPERATION_NOT_FOUND만으로 보상/guard 해제하지 않는다. 이미 있는 예약/작업은 기존 단계의 status/confirm 복구를 따르며 필요한 증거가 없으면 대기한다.

### 3.2 LC 공개 오류

기존 BaseResponse 형식을 유지한다. 실패 result=null은 현재 serializer처럼 **생략**한다. 내부 retryable/correlationId를 공개 body에 추가하지 않는다.

| 호출/조건 | HTTP / code | message | Retry-After |
| --- | --- | --- | --- |
| 시험 생성: MEMBER pending | 503 BILLING_TEMPORARILY_UNAVAILABLE | 시험 사용권을 일시적으로 확인할 수 없습니다. | 1 |
| 삭제 요청: 생성 operation 진행 중 | 409 EXAM_CREATION_PROCESSING | 시험 준비 중입니다. 잠시 후 다시 시도해 주세요. | 1 |
| 일반 시험 생성 operation 진행 중 | 409 EXAM_CREATION_PROCESSING | 시험 생성이 처리 중입니다. 같은 요청으로 다시 시도해 주세요. | 1 |
| 삭제된 Session의 과거 성공 key replay | 404 EXAM_4004 | 해당 모의고사 세션을 찾을 수 없습니다. | 없음 |

생성 중 삭제409는 기존 code를 재사용하되 삭제 endpoint의 안내 message만 맞춘다. 공개 code가 같아도 앱은 호출 endpoint를 기준으로 처리한다. 시험 생성은 같은 key 재시도이고, 삭제는 접수되지 않았으므로 사용자가 삭제 요청을 다시 시도한다. 삭제용 pending operation/event/queue를 생성하지 않는다. 이 안내가 현재 DeletionExceptionAdvice에 이미 구현됐다는 뜻은 아니다.

삭제가 먼저 접수된 상태에서 신규 학습을 요청하면 기존 LEARNING_RECORD_DELETION_IN_PROGRESS 등 현 삭제 오류 계약을 유지한다. 삭제 중 오류를 MEMBER pending이나 생성409로 바꾸지 않는다. 삭제된 성공 key의404는 금융 취소·무료 미소비를 의미하지 않는다.

### 3.3 MEMBER 최초 보강

앱은 기존 POST /api/v1/payments/purchase-account를 유효 Identity JWT로 호출한다. endpoint의 MEMBER claim/scope 및 issuer/audience/시간 검증 뒤 증거를 계정/lifecycle와 한 Transaction에 저장한다. userId는 verified sub, environment는 서버 신뢰 설정에서 얻는다. 기존 stable ref를 회전/재생성하지 않는다. WITHDRAWN/기존 recovery fence를 우회하지 않는다.

LC public503은 모든 일시 장애에 쓰이므로 앱이503만 보고 회원 증거 부족이라고 단정하지 않는다. 로그인 후 결제/복원권 준비 단계에 해당 endpoint를 선호출하고, 사용자 재시도 때에도 중복 호출을 제한한다. 앱이 원 시험 생성 key를 보존해야 하며 보강 때문에 새 key를 만들지 않는다. worker의 독자 증거 수집/Identity 신규 API/원문 token 저장은 없다.

### 3.4 MEMBER 대기의 영속 기록과 같은 key 재개 (draft-2)

LC 검토에 따라 단순 PREPARED+503만으로 재개를 보장하지 않는다. 현 worker는 precommit stale 뒤 beginCleanup을 호출하며, saga는 CLEANUP_PRECOMMIT의 PREPARED/RESERVED HTTP 재시도를 막는다. 아래는 그 경계의 신규 구현 요구이며 현재 동작 완료가 아니다.

#### Billing: 비종료 대기 사실

MEMBER_EVIDENCE_PENDING을 내기 전 같은 caller/user/operation/RESERVE key와 canonical digest에 **비종료 대기 사실**을 commit한다. 기존 제안 operation receipt 안에 `WAITING_MEMBER_EVIDENCE` 단계로 표현하며 별도 작업 시스템을 만들지 않는다. 요청의 session/mock/context/phone 근거는 불변으로 남기고 이 시점에 예약·hold·소비·복원은 만들지 않는다. source 선택 결과가 아직 확정되지 않았으면 확정 snapshot을 발명하지 않는다.

- status도 exact key의 대기 사실을 읽어 동일503 MEMBER_EVIDENCE_PENDING/true/Retry-After1을 반환한다. GET식 조회 의미는 유지하며 **status에서 증거 보강·예약 생성·대기 삭제를 하지 않는다**.
- 앱이 purchase-account 보강을 끝내도 status는 reserve가 재개될 때까지 이 대기를 재현한다. 의미는 “이 operation이 MEMBER 보강 후 요청 재개를 기다림”이며 현재 증거가 여전히 없다고 단정하는 문구가 아니다. 따라서 §3.1 고정 message를 `The operation is waiting for member evidence and request resumption.`으로 갱신한다.
- 같은 key 다른 payload는 IDEMPOTENCY_KEY_CONFLICT. 허용 owner의 같은 key HTTP reserve만 현재 근거를 다시 확인해 대기→예약/최종 결과를 한 Transaction으로 바꿀 수 있다. 같은 대기 응답을 terminal 실패로 고정하지 않는다.
- 예약/성공/확정 거절이 이미 있으면 이전 대기보다 그 권위 결과가 우선한다. 성공을 대기로 덮어쓰지 않는다. unknown commit은 exact receipt/Reservation으로 대조하며 모순은 격리한다.
- 늦은 최초 reserve가 이미 예약을 만들었을 가능성은503 또는404만으로 배제하지 않는다. 현재 authoritative status와 요청 key/digest가 복구 기준이다.

이 확장은 최초 대기 응답 유실을 처리하기 위한 신규 receipt/status 규격이다. 원 R2/fixtures-2가 이를 검증했다고 보고하지 않는다.

#### LC: PREPARED와 별도의 대기 이유

LC는 `PREPARED`를 유지하되 recovery에 `waitingReason=MEMBER_EVIDENCE`, `waitingSince`, `lastObservedAt`를 영속하는 안이다. 상태 필드 exact 이름은 이 후보를 LC 저장 모델에 매핑해 공동 확인한다. 최초 waitingSince는 갱신하지 않고 observedAt은 원격 시각으로 간주하지 않는다. 원 owner/key/digest/session/mock·생성시각·guard를 보존한다. 사용자 JWT 원문은 저장하지 않는다.

strict reserve/status의503/exact code/boolean true/네 field가 맞고 PREPARED·reservationId 없음·신규 Session/commit marker 없음일 때만 대기 표식을 기록한다. 새 Session이 비활성이어도 존재하면 모순으로 격리한다. 기존 이전 시험의 Session은 검사 대상과 구분한다.

대기 표식은 일반 precommit stale cleanup에서 제외한다. 앱 보강을 기다리는 시간을 원격 장애 재시도 횟수로 계속 소모하지 않고 별도 관측 대상으로 둔다. 실제 통신 실패/다른 원인의 기존24시간/200회 격리는 완화하지 않는다.120일이 지나면 자동 재개 대신 운영 확인이며 대기 증거/guard를 나이만으로 지우지 않는다.

worker는 다음 순서로 분기한다.

1. lease·owner/fence 검증, 이미 기록된 MEMBER 대기는 일반 cleanup보다 먼저 분기한다. worker는 reserve를 호출하지 않는다.
2. 최초 응답 유실로 대기 표식이 없는 PREPARED도, dispatch가 있었다면 파괴적 cleanup/cancel 결정 전 status를 읽어 MEMBER 대기 여부를 확인한다. 이때도 owner/lease/version을 유지한다.
3. status MEMBER pending이면 안전 조건 아래 대기를 기록/복원한다. status 예약/terminal이면 기존 예약 복구 정책을 따르고 MEMBER 대기로 돌리지 않는다.
4. OPERATION_NOT_FOUND·빈404·인증/계약 오류는 MEMBER 대기 증거가 아니다. 임의 대기 전환/guard 해제/새 reserve를 하지 않는다.

#### HTTP same-key 재개 조건

앱의 purchase-account 성공 주장만 신뢰하지 않는다. Billing이 실제 근거를 다시 검증한다. LC HTTP는 현재 actor/owner·삭제/탈퇴 deny·동일 canonical 요청을 확인하고 유효 lease를 얻는다. 동일 local Transaction에서 PREPARED·정확 key/version·MEMBER waiting 표식·신규 Session/commit marker/예약/cancel dispatch 근거 부재·자기 guard를 검사해 **앱 재개 시도 중**으로 전환한다. 일반 cleanup이 이 시도를 가로채지 않도록 HTTP와 worker는 같은 lease/version fence를 사용한다.

원격 호출 전 MEMBER 대기 이력을 지우지 않는다. reserve가 다시 pending이면 대기로 돌아가고, 응답이 유실되면 원 요청을 보존한 채 status-first로 확인한다. HTTP/worker가 관측한 version이 달라졌으면 다시 읽고 분류한다. 상대 성공을 덮어쓰거나 같은 key에 다른 Session을 만들지 않는다.

#### 이미 cleanup·격리된 경우

| 현재 상태 | 재개 조건 |
| --- | --- |
| MEMBER 대기이며 아직 cleanup 전 | 위 HTTP same-key CAS 후 reserve 가능 |
| CLEANUP_PRECOMMIT + PREPARED | Billing status의 durable MEMBER pending, 신규 Session/예약/commit marker 없음, cancel 발송·unknown cancel 없음, 동일 owner/lease/version·guard일 때만 cleanup intent를 MEMBER 대기로 CAS 전환 |
| timeout/retry 소진 격리(NEEDS_REVIEW 포함) | **동일 operation의 MEMBER 대기로 인한 소진이라는 영속 증거**와 위 안전 조건이 모두 충족될 때만 아래 좁은 예외로 제한적 재개. retry_exhausted만으로는 불충분 |
| BLOCKED_AUTH/BLOCKED_OWNER, 위 예외를 충족하지 않는 NEEDS_REVIEW 또는 불명 원인 | 앱 보강만으로 해제 금지. 기존 권한/운영 확인 절차 필요 |
| RESERVED/SESSION_COMMITTED/CANCEL_PENDING/terminal | PREPARED로 되돌리지 않음. 기존 status-first 복구/terminal replay 유지 |

**격리 판정 우선순위:** BLOCKED_AUTH/BLOCKED_OWNER와 현재 owner/삭제/탈퇴 deny는 우선 적용하며 이 예외로 해제하지 않는다. NEEDS_REVIEW는 기본 운영 확인이다. 다만 (1) 같은 operation/key/digest에 결속된 영속 MEMBER 대기 관측, (2) 해당 대기에서 소진 격리로 전이했음을 연결하는 영속 원인 기록, (3) 현재 Billing status의 MEMBER pending, (4) 위 PREPARED·신규 Session/예약/cancel 근거 부재와 owner/lease/version/guard 안전 조건이 모두 확인되면 **MEMBER 대기로 인한 소진에 한해서만** 제한적 재개를 허용한다. 이 예외가 일반 NEEDS_REVIEW 금지 행보다 우선한다. 단순 retry_exhausted 문자열·현재 pending·앱 보강 성공 중 하나만으로 원인을 추정하지 않는다. 이력이 없거나 다른 격리 원인이 섞이면 운영 확인을 유지한다. 재개 시 근거와 이전 격리 이유를 기록하고 원 이력을 지우지 않는다.

재개 검사는 owner·lease·version을 조회 전후 확인하고, 오래된 worker lease가 만료된 뒤에도 과거 결과로 쓰지 못하게 한다. cancel이 이미 전송됐거나 결과가 불명인 작업은 status pending만으로 초기화하지 않는다. status pending와 로컬 예약/성공 근거가 충돌하면 계약 모순으로 격리한다. 신규 reserve 허용은 **HTTP 사용자 같은 key 재개**에 한하며 worker reserve 금지는 그대로다.

## 4. 120일 시간·보존·재전송 경계

### 4.1 정의

- `D = Duration.ofDays(120) = 10,368,000초`. 달력4개월·지역 자정·영업일이 아니다.
- `firstAcceptedAt`: LC가 해당 user/key의 첫 유효 생성 요청과 PREPARED 또는 영속 terminal 실패를 처음 commit한 서버 시각. 재전송/worker lease/앱 재로그인으로 갱신하지 않는다. 인증/형식 거절로 operation이 없으면 이 시각도 없다.
- `guaranteeUntil = firstAcceptedAt + D`. `now < guaranteeUntil`은 보장 창 안, `now >= guaranteeUntil`은 보장 창 밖이다.
- `terminalAt`: 최초 권위 terminal 결과의 commit 시각. duplicate/replay로 갱신하지 않는다.
- `detailPurgeEligibleAt = terminalAt + D`. 실제 purge는 아래 안전 조건을 추가 확인한 후 수행한다.

운영 Mongo 저장 시각은 UTC millisecond로 한 번 정규화해 보존하는 안이다. 테스트는 경계 바로 전/정각/직후를 검증하며 float 변환을 쓰지 않는다. JWT의 기존 나노초 clock 규칙을 이 업무 millisecond 규칙으로 변경하지 않는다.

### 4.2 창 안/밖의 동작

| 상태 | 동작 |
| --- | --- |
| 창 안·같은 key/payload | 현재 owner 접근 허용 후 최초 결과/기존 operation 복구. 같은 key 다른 payload는 conflict |
| 창 안·삭제된 성공 Session | 금융 결과는 보존하지만 공개 replay는 §3의404. 재생성 금지 |
| 창 밖·기존 receipt/operation 남음 | 현재 owner/삭제 규칙을 적용하고 남은 결과 재현. 보장기간 밖이라는 이유로 살아 있는 결과를 지우거나 다른 결과 생성 금지 |
| 창 밖·관련 key 근거 모두 정당하게 폐기됨 | UUID의 과거 사용을 구별하지 못할 수 있음. 신규 요청 판정/새 소비 가능성은 승인된 위험 |
| unresolved/unknown commit/격리 | 창 밖이어도 같은 key가 새 operation으로 재생성되지 않도록 해당 진행 증거 유지, 운영 확인 |

새 서버 발급 시작권이나 영구 key 저장을 요구하지 않는다. LC가 새 PREPARED 시각을 붙이면 과거 UUID 탐지가 해결된다고 주장하지 않는다. 앱은 저장한 원 요청이120일을 넘으면 자동 재전송하지 않는다. 서버가 나이를 모르는 과거 UUID 직접 입력까지 보장하는 정책은 아니다.

기존 operation을 가진 자동 복구는 firstAcceptedAt 기준120일 정각부터 운영 확인으로 전환한다. 기존24시간/200회 격리 등 더 이른 제한이 먼저 적용된다. 단순 시계 역행으로 이미 격리/종료된 상태를 자동 재개하지 않는다. 큰 clock 이상은 자동 purge를 중단하고 관측한다. 운영자의 보존 근거 확인 후 같은 사실을 재처리하는 절차는 별도다.

### 4.3 Billing·LC 보존 정합성

120일 보장은 public LC 최초 접수 기준이다. Billing은 LC보다 나중에 receipt를 만들 수 있으므로 Billing의 최초 receipt 생성+120일보다 앞서 key 전용 증거를 지우지 않는다. LC receipt는 로컬 guaranteeUntil과 detailPurgeEligibleAt 중 늦은 시각까지 유지한다. Billing 상세는 Billing 최초 terminalAt+D, terminal 이전 증거는 미완료 보호를 적용한다. 서비스 간 skew 때문에 연결된 복구가 살아 있으면 purge하지 않는다.

기존 LC terminal7일·Billing command TTL은 유일 증거로 유지할 수 없다.120일 reader/receipt가 준비된 뒤 단기 cache TTL은 유지할 수 있으나 최초 key·결과 복구 근거 coverage를 양측 검사한다. 배포 이전 요청을 확인 없이 새 보장으로 backfill하지 않으며 cutover 대상과 기존 기록의 호환 범위를 이관 표에 기록한다.

상세 기록이120일 경과해도 금융/Claim/consumption/복원1회/권한 차단 근거는 기존 목적별 보존을 따른다. 미완료·분쟁/운영 hold가 있으면 삭제하지 않고 담당/사유/재검토일을 기록한다. 무기한 개인정보 보존으로 대체하지 않는다. 금융5년·Claim3년·backup35일·Identity 탈퇴 원천 보존은 이번120일 규칙으로 변경하지 않는다.

backups 복원 시 기존 firstAcceptedAt/terminalAt를 유지하고 현재 시각으로 재설정하지 않는다. 만료·삭제 규칙과 deny를 적용한 뒤 트래픽을 연결한다. 보장 창 안에서 결과 증거를 잃은 백업을 정상 복구 완료로 보고하지 않는다.

## 5. phone 권리 선택·snapshot·복원

### 5.1 요청/응답 조합

기존 discovery의 reason/id/attemptGroupId/mock를 저장한다. reserve의 기존 phone3필드(continuationReason/id/expectedAttemptGroupId)는 all-or-none이며 restartContext와 배타다. source/owner가 검증된 요청만 처리한다.

| 결과 | kind/source | group/mock | phone echo / transition |
| --- | --- | --- | --- |
| target 유효 paid 선택 | INITIAL/SUBSCRIPTION | 새group != expectedAttemptGroupId, 요청 mock=discovery mock | reason=PHONE_REJOIN, id=요청id, transition 없음 |
| paid 없음·유효 원 무료 | REPLACEMENT/EXAM_PASS | group=expectedAttemptGroupId, mock=discovery mock | 동일 reason/id, transition 없음 |
| paid 불명/owner 불명 | pending/owner-blocked | 결과 snapshot 새로 확정 안 함 | 무료 fallback 금지 |

phone INITIAL은 target LC에 원 source Session을 종료하라는 지시를 보내지 않기 때문에 transition이 없다. 이는 **일반 restartContext INITIAL의 transition 필수 규칙과 구분되는 예외**다. reserve/status는 최초 source/kind/group/session/mock/reason/id를 동일하게 재현한다. expectedAttemptGroupId는 원 요청에 남기며 응답 새group로 덮어쓰지 않는다. actual sessionId는 요청 신규 Session이며 원 Session을 재사용하지 않는다.

### 5.2 discovery/reserve 경합

- discovery GRADING pending·불명·오류는 paid로 우회하지 않는다. 기존 COMPLETED rebind NOOP 유지.
- discovery204면 phone echo를 만들지 않고 일반 신규 INITIAL로 판정한다.
- 신규 reserve에서 current rebind ID/owner epoch/원group 상태를 다시 확인한다. 이미 성공한 same-key replay는 current context stale 검사보다 우선하되 owner 접근 fence는 유지한다.
- discovery 뒤 paid가 생성되면 reserve commit의 공통 account fence 순서에 따라 paid 선택 가능. reserve 전 정상 만료됐으면 다른 유효 paid 또는 허용 원 무료를 선택한다. 환불 source를 무료로 바꿔 이미 확정된 예약을 덮어쓰지 않는다.
- reserve 뒤 정상 만료는 기존 D1, refund 선행 confirm은 영속 거절. 같은 operation의 source를 재선택하지 않는다.
- discovery와 reserve 사이 원group가 완료/owner가 다시 바뀌어 정상 continuation 근거가 사라졌으면409 TRANSITION_CONTEXT_CONFLICT. same-key terminal/status는 J3 규칙을 적용하고 클라이언트가 과거 무료소비를 임의 재사용하지 않는다.

### 5.3 복원 exact 예제와 Transaction

A가 최초 trial consumption C를 사용했고 기존 승인된 PHONE_REJOIN으로 현재 trial owner가 B가 됐다. B의 구매 P로 새 INITIAL confirm할 때 다음 조건을 같은 Billing Transaction에서 검증한다.

1. rebind의 source A/target B/transitionId와 current subject owner B·epoch가 일치한다.
2. 원 C가 최초 trial 소비이며 완료/명시 삭제/기지급 복원 등 부적격이 아니다. 접수만으로 완료를 추정하지 않고 기존 완료/confirm commit 순서로 판정한다.
3. B의 MEMBER 증거·lifecycle와 구매 P의 자격이 유효하다. 원 A의 paid/복원권을 B의 권리로 사용하지 않는다.
4. 원 consumption C를 unique source로 하는 복원 지급이 없다.

충족하면 **B에게 새로운 복원 grant1개**를 지급하고 paid confirm/원 group replacement 차단/ledger/receipt를 함께 commit한다. 기존 A의 grant를 이전하는 것이 아니다. A 또는 B에게 C로 이미 지급된 기록이 있으면 추가 지급0이며 기존 grant 수혜자를 바꾸지 않는다. 이후 B→다른 계정 재가입도 같은 C unique로 중복을 차단한다.

원 완료가 먼저 commit되면 그 상태로 자격을 평가하고, 복원 confirm이 먼저 commit된 뒤 완료가 도착하면 지급을 회수하지 않는다. 삭제가 먼저 적용된 원 Session으로 복원하지 않는다. paid confirm/복원이 먼저면 뒤 삭제가 지급을 회수하지 않는다. 환불이 먼저면 confirm 거절/복원0, confirm이 먼저면 기존 환불 정책을 따르며 같은 confirm을 취소로 바꾸지 않는다.

T2는 원 group version·target account/lifecycle·공통 guard·grant source unique·receipt를 한 ClientSession으로 처리한다. 같은 소비의 두 confirm/다른계정 전환 경합은 owner epoch 및 C unique로1회 수렴한다. 원 Session 완료 event는 target 새 group/operation guard를 해제하지 않는다.

## 6. 생성 중 삭제의 원자 판정

삭제 API는 현재 actor/lifecycle 검증 후 생성 준비와 공유하는 사용자 fence를 같은 local Transaction으로 쓴다. **활성 생성 operation이 있으면** 삭제 operation/inbox/대기열을 생성하지 않고 §3의409를 반환한다. 감사용 요청 관측은 가능하나 실행 가능한 삭제 command로 저장하지 않는다.

생성 완료는 Billing confirm 성공 또는 권위 실패를 확인하고 LC 마무리가 commit돼 활성 생성 guard가 해제된 상태다. 문서 생성 여부나 단순 Session ACTIVE만으로 판단하지 않는다. 복구 중/unknown은 생성 중으로 유지한다.

삭제가 먼저 fence를 얻고 접수 commit했다면 새 생성은 기존 삭제 pending으로 차단한다. 반대 순서면 삭제 미접수이며 기존 생성이 계속 진행한다. 따라서 생성 finalize를 막은 채 그 완료를 기다리는 삭제 queue는 없다. 응시 중 정상 시험은 생성 operation이 완료됐으면 삭제 가능하다.

접수된 삭제는 원문 정리와 Billing 전달/ACK를 분리한다. Billing 장애 때문에 원문 개인정보 정리를 무한 중단하지 않는다. 새 학습 허용 시점과 삭제 event 인증/귀속은 별도 삭제 전달 계약을 따른다.

## 7. 공동 검증 명세 — 미제작/미실행

제안 fixture revision: `lc-billing-paid-exam-simplified-exact-r1-fixtures-1`. 양측 규격 수락 후 생성하며 기존 bytes를 덮어쓰지 않는다. static oracle/HTTP 모형과 실제 DB·worker 실행을 분리한다.

| ID | 사례/기대 |
| --- | --- |
| E01 | MEMBER pending 내부503/4-field/true/Retry-After1 → 공개 기존503, 신규 소비0 |
| E02 | 아래 E02-a~h 대기 영속/재개·유실/cleanup 경합 검증, worker reserve 호출0 |
| E02-a | 최초 pending commit·LC 대기 저장·앱 보강 후 same-key 성공/동일 Session·stable ref |
| E02-b | 최초 pending 응답 유실→status pending으로 LC 대기 복구, OPERATION_NOT_FOUND를 대기로 오인하지 않음 |
| E02-c | 보강 전/후 status는 기록된 대기 반환, status에서 reserve/원장 생성0 |
| E02-d | HTTP 재개/worker cleanup CAS 양순서·lease 만료·stale version 쓰기 거절 |
| E02-e | 안전한 PREPARED cleanup만 대기로 복원; 신규 pending Session/예약/cancel unknown 있으면 해제0 |
| E02-f | owner/탈퇴/삭제/auth 차단·불명 격리는 보강 성공만으로 해제0. NEEDS_REVIEW+retry_exhausted만 있으면 해제0; 동일 MEMBER 대기→소진 영속 원인과 현재 안전 조건이 모두 있으면 제한적 재개; 원인 혼재/이력 누락은 운영 확인 |
| E02-g | 재개 reserve 성공 응답 유실·unknown commit→status 권위 결과 복구, 대기 덮어쓰기0 |
| E02-h | 대기120일 상한·waitingSince 고정·일반 재시도격리 유지·처리중 TTL삭제0 |
| E03 | WITHDRAWN 선행/늦은 MEMBER 증거는 부활0, JWT 원문 저장0 |
| E04 | 생성 중 삭제409·실행가능 deletion row0, 일반 응시 Session 삭제 가능 |
| E05 | 삭제 완료 뒤 과거 성공 key404 EXAM_4004/result 생략, 금융 성공 보존·Session 재생성0 |
| E06 | J3 strict route/HTTP/code/retryable/4-field와 owner-blocked 기존 회귀 유지 |
| T01 | firstAcceptedAt+D−1ms/정각/+1ms 보장 창 true/false/false |
| T02 | 재전송/lease/로그인/clock 변경이 firstAcceptedAt를 갱신하지 않음 |
| T03 | terminalAt+D 상세정리 경계·최소 근거 유지·unresolved 자동 삭제0 |
| T04 | 원 operation7일 cache 삭제 후120일 내 receipt replay, 다른 payload conflict |
| T05 |120일 이후 모든 key 전용 근거 폐기 뒤 같은 UUID가 새 요청일 수 있음(승인 위험). 남은 소비/복원 unique는 유지 |
| T06 | backup 원 시각 보존·만료/deny 적용,24시간/200회 조기격리 유지 |
| P01 | phone paid INITIAL/새group/echo/transition없음·status 동일 snapshot |
| P02 | phone free REPLACEMENT/원group·paid 불명 fallback0 |
| P03 | discovery pending/204/원group완료·paid 신규생김/만료·revoke 조합 |
| P04 | sourceA→targetB, C당 B복원1회·A기지급시0·반복재가입0 |
| P05 | 새 INITIAL에 구group 강제/echo유실/transition추가/다른mock/source 변조 거절 |
| C01 | 생성 fence 선행→삭제 미접수, 삭제 fence 선행→새 생성 차단 |
| C02 | paid confirm/원 완료·삭제·refund 양 commit 순서, unknown commit replay |
| C03 | 두 confirm/두owner 경쟁 시 C 복원1회·원claim 보존·새guard 보호 |

원자성/경합 사례는 replica-set에서 실제 실패 주입으로 검사해야 한다. 가짜 JSON 예상 결과를 실행 통과로 보고하지 않는다. 신규 deletion route security 테스트는 별도 A 보완/삭제 fixture와 연결한다.

## 8. 양측 확인 항목과 변경 경계

LC: 공개 오류 code/message/result 생략·삭제 미접수 fence·120일 저장 reader·phone snapshot 예외/owner 검증·앱 보강 흐름 확인.
Billing: 내부 pending/거절 DTO·same-key 복구·source 선택·single session/unique/CAS·receipt와 금융 근거 분리 확인.
Identity: 기존 claim/fence 방향 확인은 사용자 전달 회신으로 접수됐으며 운영 issuer/JWKS/flags는 별도 검증한다. 새 API/claim은 추가하지 않는다.

120일 이전 기록 coverage/clock 편차·신규 삭제 route/옛 owner fence·각 최소 근거의 최종 폐기는 계속 해당 담당 설계로 확인한다. 정책을 다시 선택하는 요청은 아니다. 본 exact 문서와 별도 미완료 항목을 구분해 수락 기록을 남긴다.

읽기 근거: LC ErrorStatus의 EXAM_CREATION_PROCESSING/EXAM_4004/BILLING_TEMPORARILY_UNAVAILABLE, BaseResponse의 NON_NULL result, DeletionFailure/DeletionExceptionAdvice, BillingReservationClient, BillingExamCreationTransactionService의 기존7일 terminal 보존 및 Billing ReserveCommand. LC 코드는 읽기만 했고 수정/실행하지 않았다.

문서와 계획 참조/작업 기록만 변경했다. 제품/DB/기존 계약·fixture bytes/배포/Jira/외부 메시지/commit/push 없음. 링크/diff/hash 검사만 수행하며 Gradle은 실행하지 않는다.


### 2026-10-14 LC draft-1 검토 접수·draft-2 보완

사용자 전달 LC 회신에서 공개 오류/120일 경계/phone INITIAL·복원 방향에 추가 이견 없음으로 접수했다. 이번 변경은 MEMBER 보강 same-key 재개의 §3.4 및 E02 세부 사례다. 현 LC saga CLEANUP_PRECOMMIT gate와 reconciliation beginCleanup/status/cancel 경로를 읽기 확인했다. 신규 대기 receipt/status 규격은 양측 추가 검토 대상이며 테스트 통과/구현 완료가 아니다.


### 2026-10-14 LC draft-2 후속 — draft-3 격리 우선순위

LC는 retry 소진도 NEEDS_REVIEW로 저장하므로 §3.4 두 행의 겹침을 지적했다. MEMBER 대기 때문에 소진됐다는 영속 원인과 모든 현재 안전 조건이 확인된 경우만 일반 NEEDS_REVIEW 금지의 예외로 명시했다. retry_exhausted만으로는 해제하지 않는다. E02-f의 양/음성 기대를 보완했으며 추가 정책 선택·제품/fixture 실행은 수행하지 않았다.


### 2026-10-14 LC draft-3 검토 완료 접수

사용자 전달 LC 회신에 따라 격리 예외 우선순위와 E02-f가 정확히 반영됐으며 이번 수정 범위에 추가 이견 없음을 접수한다. 이전 LC 보완 의견은 해소됐고 다시 미반영으로 취급하지 않는다. 다음은 범위를 한정한 공동 수락 기록 → 새 fixture 제작 → hash 대조·LC 독립 검증이다. 이번 회신 자체를 최종 공동 수락이나 fixture 제작/제품 구현·배포 승인으로 확대하지 않는다.

별도 삭제 route 인증/오류·과거 owner 처리·자료별 최종 보존/폐기 조건은 여전히 남아 있으며 본 draft-3 검토 완료로 해소된 것이 아니다. 이번 접수는 사용자 전달 보고에 근거하며 LC 저장소/테스트를 별도로 재확인하지 않았다.
