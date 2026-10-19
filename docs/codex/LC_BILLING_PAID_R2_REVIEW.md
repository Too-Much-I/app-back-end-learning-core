# LC 유료 시험 연동 R2 — 변경 검토·공동 확정 과제

- 날짜: 2026-10-13
- 관련: Billing PLAN-011/012/013, LC 기존 TMI-116/TMI-128/TMI-122; lifecycle 연계 TMI-125
- 입력: 사용자 전달 R2, 무료 모의고사권·유료 전환 복원 정책, production inventory 및 legacy 보존 승인.
- 상태: 정적 검토/LC 기술 제안. 제품 정책은 전달된 최신 승인을 따르나 exact wire/schema·신규 공개 오류·역방향 인증은 아직 공동 동결하지 않았다. 코드 구현·DB 변경·배포 승인이 아니다.
- 최신 검토: 후속 R2 §10 및 manifest 보완은 §8을 따른다. 본문의 별도 finalization 작업 대안·공개 성공 시점 미정·TRIAL fallback 정리 요청은 후속 전달본에서 해소됐다.
- inventory 수치/환경/승인은 사용자 전달 자료 기준이다. 이번에 production에 연결하거나 Billing DB 부재·실제 배포 설정을 독립 확인하지 않았다.

## 1. 정책 적용 우선순위

1. 기존 LC 시험은 기존 규칙과 기록을 보존한다. source 없는 시험을 EXAM_PASS로 일괄 채우거나 trial consumption/복원 근거로 만들지 않는다. 새 시험·의도적인 새 재응시부터 Billing reserve/confirm을 적용한다.
2. 신규 source 분류는 EXAM_PASS/SUBSCRIPTION이다. R1/R2 잔여 TRIAL wire·legacy 무료 fallback 문구는 신규 계약 기준으로 그대로 채택하지 않는다. 최초 체험/복원/초대의 구별과 복원 자격은 Billing 담당이다.
3. 최초 trial→paid 전환에서 Billing confirm·복원 지급·원 그룹 재응시 차단은 Billing Transaction, LC 기존 Session 종료는 confirm 성공 증거 뒤의 로컬 후속 단계다. 이미 접수된 무료 제출의 지연 완료와 복원권 동시 보유를 허용한다.
4. 구매만으로 종료/복원하지 않는다. 복원은 원 최초-trial consumption당 최대1회이며 복원권/초대권은 재복원하지 않는다. 보상권 Guest merge·만료형 무료권은 이번 범위 밖이다.
5. 새 제출 마감은 없다. 현행 gradingStartedAt 기반 GRADING30분은 유지한다. 환불 전 confirm된 exact Session 보호와 삭제/탈퇴/terminal 우선 규칙을 유지한다. 모든 종료 Session의 재활성화나 모든 전환 유형의 중첩 허용으로 확대하지 않는다.

## 2. 현재 코드에서 확인한 차이

| 위치 | 현행 동작 | 필요한 변경 |
| --- | --- | --- |
| BillingSagaConfiguration / JwtBillingReservationClient | unknown response field 거절; reserve/status에 source 없음 | strict DTO에 확정 필드만 추가, 모든 reader 선배포 |
| ExamCreationOperation / ReservationSnapshot / ExamSession | source 저장 없음 | reserve 성공 이후 source 영속·불변 검증, PREPARED와 legacy는 구분 |
| ExamSessionManager.prepareForBillingInTransaction | 기존 group가 있으면 replacement와 같은 mock을 미리 선택 | Billing이 paid-first로 새 INITIAL/group를 선택할 수 있는 준비·검증 흐름 필요 |
| BillingExamCreationSaga.validateReserved | replacementExpected에서 INITIAL 응답을 거절 | 증명된 전환만 INITIAL 허용. 단순 검증 삭제 금지 |
| BillingExamCreationTransactionService.commitReservedSession | confirm 전 새 Session commit에서 기존 진행 Session abandon | 전환 대상은 보존하고 CONFIRMED 뒤 exact 대상 종료 |
| finalizeConfirmed / reconciliation | SUCCEEDED를 terminal로 만들고 worker는 terminal이면 noop | 후속 종료 미완료가 사라지지 않는 durable 단계/별도 작업 필요 |
| ExamServiceImpl.ignoreAbandonedCallback | abandoned/retake Session 콜백 무시 | 전환 전 접수 증거가 있는 Job의 완료만 제한적으로 허용 |
| AttemptGroupStateCoordinator / ExamSession terminal 메서드 | IN_PROGRESS 중심 판정, abandoned terminal 전이 거절 | 신규 제출 차단과 접수된 작업의 결과 완성을 분리; 상태event v1 불변 |

이는 코드 읽기 결과이며 새로운 동작의 실행/테스트 성공을 의미하지 않는다.

## 3. 요청·응답 및 source 저장 제안

- 공개 POST /api/v1/exams는 body 없음, 기존 Idempotency-Key 의미와 성공 DTO/BaseResponse를 유지한다. 앱은 source/purchase/이전 group를 선택해서 보내지 않는다.
- internal reserve 기본3필드·phone continuation 계약을 우선 유지한다. 다만 전환 근거와 이전/새 group·Session을 양측이 동일 operation으로 식별할 exact 응답/status 계약은 추가로 필요하다. source 한 필드만으로 이 정보를 대체할 수 없다.
- reserve/status의 EXAM_PASS/SUBSCRIPTION을 client snapshot→operation→Session에 보존한다. reserve 실패/PREPARED에는 source가 미정일 수 있다. 신규 reserve 성공 이후 누락/unknown enum/타입 오류/불일치는 계약 오류다. 단순 ignoreUnknown 설정은 사용하지 않는다.
- confirm/cancel DTO 유지안은 가능하나, confirm 응답 유실 후에도 status에서 source와 전환 근거를 복구할 수 있어야 한다. 응답 생략을 이유로 기존 source를 null로 덮어쓰지 않는다.
- EXAM_PASS는 최초 trial뿐 아니라 복원권도 포함한다. LC는 EXAM_PASS만 보고 복원 자격을 판단하지 않는다. SUBSCRIPTION 역시 서로 다른 구매를 구분하지 못하므로 동일 source enum을 동일 권리라고 간주하지 않는다. immutable group/reservation 및 Billing 판정이 필요하다.
- phone continuation과 삭제된 시험 continuation도 source/group 검증 회귀 대상이다. 보상권에 기존 trial phone/Guest owner 규칙을 자동 적용하지 않는다.
- 신규 공개 ENTITLEMENT_REVOKED HTTP409/code/message와 terminal replay는 공동 확정 대상이다. 현행 client는 이 오류를 CONTRACT_ERROR로 처리하므로 category만이 아니라 saga/worker/실패 snapshot까지 변경해야 한다.

## 4. 전환·복구 제안

권장 논리 순서(단계명은 후보):

1. LC가 operation 및 전환 전 대상 Session/group/version을 고정한다. legacy 대상의 경우 Billing-linked trial이라고 주장하지 않는다.
2. Billing reserve 성공 snapshot과 전환 근거를 저장한다. 새 Session은 확인 대기 상태로 commit하되 기존 Session은 아직 종료하지 않는다. 확인 대기 Session의 제출·Job 실행과 기존 active 조회/단일 사용자 guard를 점검한다.
3. Billing confirm 성공 또는 authoritative status로 동일 operation/reservation/Session/source의 성공을 확인한다. Billing 내부 복원금액/수량/ledger를 LC가 계산하지 않는다.
4. LC 로컬 Transaction/CAS에서 새 Session 사용 가능 처리, 확정된 이전 Session의 신규 제출 차단과 전환 완료 증거를 저장한다. 이미 완료·삭제된 이전 Session을 abandon으로 덮어쓰거나 현재 다른 Session을 종료하지 않는다.
5. confirm 성공 응답 유실·LC 종료 실패는 동일 operation status-first와 영속 재시도로 수렴한다. 성공한 confirm을 cancel하지 않고 새 operation으로 다시 전환/지급하지 않는다.

현재 SUCCEEDED terminal noop/TTL 구조에 후속 종료를 단순 부착하면 복구가 유실될 수 있다. (a) 종료 완료까지 operation을 복구 가능 상태로 유지하거나 (b) confirm 확정과 같은 LC Transaction에서 별도 durable finalization 작업을 만든다. 공개 성공 반환 시점, 새 시험 재요청 guard, 미완료 작업 무TTL·알림·운영 복구를 함께 결정해야 한다. 새 분산 lock은 요구하지 않는다.

접수와 종료는 LC의 같은 Session/전환 fence를 CAS/Transaction으로 직렬화하는 방안을 권고한다. 기존 durable Job/접수 marker로 인정된 요청은 응답 유실 후에도 같은 접수로 처리한다. presigned URL 발급이나 클라이언트 시각만으로 접수됐다고 보지 않는다. 종료 후 새 retryCount/새 녹음·새 Job을 만드는 요청은 거절한다. 채점 복구 재실행과 사용자 새 재응시를 구분한다.

이미 접수된 Job의 callback·요약·outbox 완료는 허용하되 신규 제출·replacement를 다시 열지 않는다. 단순히 모든 ABANDONED callback을 허용하는 수정은 금지한다. 부분 제출 상태에서 종료된 경우 남은 문항을 새로 받지 않으며 기존 완료 evidence가 충족되지 않으면 COMPLETED를 만들지 않는다. 지연 COMPLETED를 Billing이 수용해도 원 그룹의 재응시 차단과 지급된 복원권은 유지돼야 한다.

환불-first confirm의 안전한 보상은 exact 권위 증거가 필요하다. RESERVED/404/transport/TTL 소실만으로 취소·미소비를 추정하지 않는다. 성공 confirm 보존, CANCELED/EXPIRED 및 durable 거절 증거의 exact status wire와 테스트를 공동 동결한다.

## 5. 기존 시험과 신규 writer 경계

전달된 inventory는 Session1,493건 모두 Billing 연결/source 부재, operation collection 없음이다. 따라서 현재 LC의 이 집합에 R2의 'proven legacy TRIAL operation' 경로를 적용할 근거가 없다. Billing prod 신규 구축이라면 해당 호환 경로는 기본 필요조건이 아니며, 별도 환경의 실제 증거가 있을 때만 검토한다.

권장 경계는 전환 직전 확정한 서버 측 Session 대상 manifest 또는 동등한 writer generation 증거다. source=null·현재 flag OFF·클라이언트 날짜로 판정하지 않는다. 원 Session을 일괄 수정할 필요 없이 조회 가능한 보호된 경계 저장소를 둘 수 있다. 실제 manifest에는 정확한 대상 매칭 정보가 필요하며 공개 보고서에는 식별자 대신 revision/hash/건수만 기록한다.

- 구 writer drain/새 writer 차단과 최종 inventory 경계를 함께 설계해 대상 누락·전환 후 시험의 legacy 편입을 막는다. 실제 전환 당시 DB snapshot/서버 증거 방식은 공동 확정한다.
- 기존 결과 열람과 기존 규칙의 접수/Job 복구는 source 부재만으로 막지 않는다. status 누락은 현행 effectiveStatus 등의 판정을 유지하며 active=true만으로 새 권한을 부여하지 않는다.
- legacy Session을 참고해 새 시험을 시작하더라도 신규 Session은 Billing INITIAL 등 승인된 신규 생성 경로를 따른다. 기존 legacy 시험에는 Billing group/consumption을 사후 생성하지 않고 유료 전환 복원 대상으로 삼지 않는다.
- 전환 자체가 기존146건을 종료시키지 않는다. 사용자의 후속 새 시험 시작에 따른 기존 Session 처리와 전환 실패 시 보존 범위를 명시한다. 기존 counts는 전환 직전 재확인하며 고정 대상 수로 사용하지 않는다.
- 기존 요청의 재전송/접수된 Job 복구는 새 시험과 구분한다. 서버가 같은 요청임을 입증할 증거가 없는 경우 임의 grandfather나 무조건 새 Billing 과금을 하지 않고 API별 정책을 확정한다.

## 6. R2 중 공동 동결이 남은 항목

| 항목 | LC 검토 의견 / 결정 필요 |
| --- | --- |
| source/legacy exact DTO | EXAM_PASS/SUBSCRIPTION 분류 수용 방향. R2 §1/6의 TRIAL fallback 잔여 문구 정리, missing/null 허용 단계와 환경별 legacy 증거 명시 |
| 전환 wire | INITIAL과 replacement 판정, 원/신규 group·Session·전환 식별 근거, reserve/confirm/status 재현 필드·version·mockExam 선택 규칙 |
| 후속 종료 | durable 단계/TTL/공개 응답 시점, confirm 후 종료 실패 retry 및 guard, 부분 접수·동일요청 replay·완료 경합 |
| 오류/보상 | ENTITLEMENT_REVOKED 공개 오류, authoritative 거절 증거와 confirm 성공 TTL 이후 복구, enum switch 전체 매핑 |
| 역방향 revoke | 최대16,384byte, proposed route와 issuer/sub/aud/키·flags는 별도 수락. 기존 A 발신 계약/fixtures 변경 없음. 기존 LC chain/port inventory 후 401/403/OFF404 우선순위 동결 |
| 순서/digest | eventId 충돌→동일 group/version 충돌→stale 순서 방향 적절. exact canonicalization·semantic 필드·null·timestamp/UUID normalization, high-water key와 confirmedSessionId 불변 규칙 동결 |
| 보존/lifecycle | inbox120일은 후보. 열린 protected Session/deny fence를 임의 TTL로 종료하지 않음. deletion/withdrawal/merge 뒤 최소 증거·비식별화·replay 안전 폐기와 409격리/fail-closed 저장 방식 필요 |
| 배포/rollback | 전체 API/worker reader-first, source/transition writer coverage, 구버전 task drain, snapshot 필드를 잃는 rollback 금지, revoke 및 후속 복구 준비 전 paid OFF |

R2는 앞선 제출 마감·16KiB·stale 우선순위 문제를 보완했다. 그러나 상단 최신 승인과 충돌하는 §8의 일반 무중첩 표현은 최초 trial→paid 한정 예외를 명시적으로 구분해야 한다. LC local 종료 이전 접수는 허용될 수 있지만 종료 뒤 새 접수는 금지이며, 다른 유형 전환까지 허용을 확대하지 않는다.

## 7. 필요한 검증과 다음 단계

- 기존 전체 테스트에 source strict reader/operation·Session 복구, 기존 replacement 예상 중 승인된 paid INITIAL, phone/삭제 continuation 회귀를 추가한다.
- confirm 전 실패 시 이전 Session 보존, confirm 유실/후속 종료 실패/unknown commit/동시 새 시험의 단일 전환·동일operation 수렴을 검증한다.
- 접수와 종료 양순서, 접수 응답 유실 replay, 종료 뒤 새 제출 거절, 기존 Job callback/summary/v1 terminal event, 부분 접수 비완료, 복원권 회수 없음은 양측 fixture로 검증한다.
- legacy manifest 누락/전환 이후 생성/상태 누락10건·비활성2건/기존결과·접수복구/legacy 무소비·무복원 및 전환 inventory 재확인을 검증한다.
- 역방향 JWT token/HTTP/port/ON-OFF·body size·충돌/역순·삭제/탈퇴 fixture는 별도 revision/hash로 만들고 양측 독립 실행한다. 실제 E2E와 키 배포 검증은 후속 승인 범위다.

다음은 공동 기술 계약과 LC 상세 구현 계획/허용 범위 확정이다. 이번 검토로 유료 연동 코드 구현·AGENTS 범위 확대·실제 migration/배포/활성화를 수행하지 않았다. production 점검과 테스트도 재실행하지 않았다.

## 8. 후속 R2 재검토 — 2026-10-13

이전 첨부와 diff 대조 결과 §1/6의 prod TRIAL fallback 제거·exact Session manifest, §8의 최초trial→paid 무중첩 예외 구분, 신규 §10의 후속 마무리 결정이 반영됐다. 해당 보완 방향에 추가 이견은 없다. 이전에 제시한 대안을 계속 미정으로 취급하지 않는다.

- 기존 생성 operation이 후속 종료까지 책임지고 별도 종료 전용 작업 시스템은 만들지 않는다. confirm 증거를 확보해도 마무리 전에는 미완료·guard 유지·무TTL, 기존 reconciliation으로 복구한다.
- 새 Session 사용 가능 처리·exact 이전 Session 신규 제출 차단·SUCCEEDED를 같은 LC Transaction에 저장한 뒤 공개 성공을 반환한다. 이전 Job 채점 완료까지 기다리지 않는다. 동일 key replay와 unknown commit 검증이 필요하다.
- legacy는 보호된 서버 저장소의 exact 시험 ID 목록으로 우선 설계하고 구 writer drain/최종 inventory로 경계를 확정한다. 이번 prod 전환에서 proven-TRIAL fallback은 제거한다. 목록 보존·탈퇴/삭제 연계는 여전히 상세 설계 대상이다.
- 기접수 작업 완료/새 제출 차단·부분 접수 비완료를 분리한다. §10.2의 '새 Job 차단'은 사용자 새 제출·재응시에 의한 Job을 뜻하도록 명시할 것을 권고한다. 이미 접수된 문항에서 뒤늦게 파생되는 Summary Job의 최초 생성과 시스템 재시도까지 금지하면 같은 절의 요약 완료 허용과 충돌한다. 파생 작업도 원 접수 증거에 묶어야 한다.
- 미확정 기술 계약은 원/신규 전환 근거 reserve/status DTO, INITIAL/REPLACEMENT·mock 선택, authoritative 거절/TTL 후 confirm 복구, 중간 공개 HTTP/code, 역방향 revoke 보안·보존manifest 및 공동 fixtures다. 별도 제출 마감이나 무료 복원 정책을 다시 결정할 필요는 없다.

문서 검토만 수행했다. 첨부의 설계 승인 기록을 exact 미확정 wire 구현·배포 승인으로 확대하지 않는다. 코드/DB/외부 서비스/테스트 실행 변경 없음.
