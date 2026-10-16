# TMI-217 deletion exact R2 draft-1 — LC E1~E5 회신

- 날짜: 2026-10-16 (Asia/Seoul)
- 대상 revision: `lc-billing-deletion-exact-r2-draft-1`
- 기준: [Billing 초안](/Users/msde76/.codex/worktrees/plan012-refund/billing/docs/contracts/TMI-217-DELETION-EXACT-R2-DRAFT-1.md), [LC R2 방향 수락](TMI_217_DELETION_SIMPLIFIED_R2_REVIEW.md).
- 상태: 정적 검토·조건부 기술 수락. 아래 보완 전 exact 동결, 구현 착수, 공동 테스트 통과를 뜻하지 않는다.

## 1. 5줄 결론

1. E1: 7필드 wire·415 포함 오류·empty204만 성공 원칙에 이견 없다. 재시도 수치는 구현 가능한 후보이나 경계·응답 분류를 보완해야 한다.
2. E2: 최초 fact와 event receipt의 분리·동일 Transaction 및 충돌 우선순위를 수락한다. Billing 실제 schema/코드 검증은 별도다.
3. E3: 기존 target 재사용·전체 필요한204·독립 원문 정리를 수락한다. 현재 LC 상태 머신 그대로는 전달 격리 시 cleanup도 멈추므로 분리 구현이 필요하다.
4. E4: 미전달30일TTL 제외·120일 정리 검토만 허용하는 방향을 수락한다. LC 상세의 기산점/필드와 보존 목적별 조건은 추가 명시가 필요하다.
5. E5: JSON 44개 사례를 정적으로 확인했다. 실행 가능한 raw bytes/경계·경합 사례 보완과 독립 실행 전에는 fixture 동결/테스트 통과가 아니다.

## 2. 반드시 읽어야 하는 내용

사용자 정책은 다시 결정할 필요 없다. 아래는 정책 변경이 아니라 구현 가능한 exact 계약으로 만들기 위한 보완이다. 기존 A/paid/simplified fixture bytes는 변경하지 않는다. 새 sender·DB/index·배포·purge는 수행하지 않았다.

| 항목 | LC 회신 | 동결 전 보완 |
| --- | --- | --- |
| E1 | route/audience, strict7필드, canonical 순서·escaping, 16,384bytes,415,409/503 분류 방향 수락 | Retry-After/시도 상한 경계, 압축 거절 상태, 인증·응답 판정 사례 |
| E2 | fact+receipt 원자성, 같은event 충돌 우선, 새event exact 삭제 no-op 수락 | 실제 current impact fence 및 unique/unknown commit 검증은 Billing 구현 책임 |
| E3 | 요청별 target payload/version, 전체ACK gate, 원문독립 수락 | cleanup와 delivery 격리 분리, 시각/상태 불변식 |
| E4 | 미전달 무TTL,120일 review-only, 최종purge 별도 수락 | LC 기산점/관련문서 retention, ACKED 이후 기존30일 정리 방지 |
| E5 | 12 wire+18 state+14 delivery의 의도 수락 | 아래 추가/정밀화 후 새 revision·bytes/hash 수락 |

## 3. 공동 확정 요청 — 구현 전 기술 보완

### E1 — 오류·재시도

- 24시간/200회,5초poll/20건순차,30초lease/5초timeout/16KiB응답상한은 LC에서 구현 가능한 후보로 검토했다. 기존 cleanup worker의 budget/backoff를 변경하는 값이 아니라 새 delivery 전용 값으로 명시한다. 처리시간이5초를 넘는 tick을 중첩 실행하지 않고 target별 lease/CAS로 다중 instance를 보호한다.
- 상한은 `now >= firstAttemptAt+24h` 또는 `attempts >= 200`이면 **다음 claim/send 금지**로 명시할 것을 제안한다. 200번째 시도의 유효한204는 저장할 수 있고 실패하면 격리한다. 발신 전 영속 증가·crash 소진과 최초 시각 불변을 유지한다. 수동 재개가 budget/원 시각을 어떻게 다루는지는 별도 규칙 없이 자동 reset하지 않는다.
- backoff의 n은 cleanup retryCount가 아니라 이 target의 실패한 delivery 시도다. crash 후 소진된 attempts로 보수 계산할지 별도 실패 수를 둘지 명시해야 한다. 유효 Retry-After가 정확히24시간 경계인 경우도 재발신할 수 없으므로 운영 확인으로 분류한다. 과거 HTTP-date/0은 backoff를 줄이지 않고 음수·overflow·중복은 invalid로 처리하는 경계를 fixture에 추가한다.
- 503의 파싱 가능한 업무 응답은 code/message/retryable/correlationId 계약 검증을 적용하고 모순은 계약 격리한다. HTML/파싱불가5xx의 일시 재시도와 구별한다. 응답 크기 초과, 예상 밖 1xx/2xx, 압축 요청 거절 HTTP/code 및 허용 Content-Type parameter도 명시해야 한다. 성공은 유효 lease/CAS 안의 empty204뿐이다.

### E2 — 저장·원자성

두 collection 제안은 재삭제 no-op과 event 충돌 감사를 최소 단위로 분리한다. receipt 재현은 현재 Session 재생성이나 guard 변경 없이204, 신규event는 exact fact/group 확인 또는 실제 대상 검증 후 처리한다. 새event no-op receipt도 같은 Transaction에서 저장해야 한다. 동일event/다른body 동시 경쟁과 서로 다른event/동일Session 경쟁을 별도로 검증한다. unknown commit은 fresh durable evidence 없이204로 처리하지 않는다.

최초 fact의 disposition·deletedAt·appliedAt은 후속 요청으로 변경하지 않는다. 개인정보 포함 최초fact의 최종 purge는 이 저장안 수락과 별개다. 현재 owner/activeSession/source 영향 판정 및 link/account/guard fence의 실제 경합 검증은 Billing에서 수행해야 한다.

### E3 — LC 상태·시각

`requestedAt`은 DeletionCommandService가 guard를 touch한 요청 Transaction에서 `writeBlocked=true` operation과 함께 저장한다. DeletionAccess가 이 operation을 조회해 새 공개 쓰기를 막으므로 **성공 commit된 요청의 논리적 접근차단 시각**으로 재사용할 수 있다. DB 실제 commit wall-clock이라는 뜻은 아니다. BSON에서 다시 읽은 millisecond 정밀도 값으로 payload를 한 번 고정하고 retry/unknown commit에서 clock.now로 재생성하지 않는다. 이미 허용된 GRADING drain/Callback seal과 신규 사용자 접근 차단 시각은 구분한다.

현재 worker는 SAFE_TO_START_LEARNING 뒤에 cleanup stage로 진행하고 operation NEEDS_REVIEW는 due 조회에서 제외한다. 따라서 delivery NEEDS_REVIEW를 그대로 operation 격리로 복사하면 원문 정리가 멈춘다. 준비/봉인·안전한 cleanup과 delivery 진행/운영확인을 분리하고, 기존 요청시각+24시간 전체작업 예산도 delivery 대기만으로 안전한 cleanup을 막지 않도록 분리해야 한다. 사용자 인가/자료 자체 불명은 별도의 안전 판정으로 남긴다.

LINKED에는 완전한 delivery, 검증된 UNLINKED에만 object 없음, 미분류/UNRESOLVED는 성공 아님을 유지한다. ACKED 외 ackedAt=null, 최초 발신 전 attempts=0/firstAttemptAt=null, 이후 firstAttemptAt 불변, NEEDS_REVIEW의 자동 due/lease 제거 등 상태 불변식도 명시할 것을 요청한다. 전송 실패의 고정 failureCode가 timeout/429/HTML5xx를 어떤 값으로 나타낼지 명시하거나 null을 허용해야 한다. 자유 텍스트 오류 저장은 하지 않는다.

### E4 — 보존

Billing receipt `processedAt+120일`의 review-only 기준은 수락 가능한 후보이며 재시도로 연장하지 않는다. 반면 LC delivery schema에는 검토시각 필드·기산점이 없다. LC는 **cleanup과 필요한 전달이 모두 끝난 최초 operation.completedAt+120일**을 상세 검토 기준으로 제안한다. ACKED된 일부 target도 operation 미완료면 기존30일 TTL로 삭제하지 않는다. 이는 새 사용자 정책이 아니라 공동 기술 확인 대상이며 아직 동결하지 않는다.

operation/target/command 중 복구에 필요한 자료 전체를 보호해야 한다. 완료 직후 기존 complete/finishRetention/expiry 전파가30일 TTL을 붙이는 경로도 linked 목적에 맞춰 차단해야 한다. 최초deny·권한/금융·미해결 증거와 검토용 상세는 별개다. reviewAt 도달은 삭제 승인도 멱등 보장 자동 종료도 아니며, 실제 receipt 삭제 이후 영구 충돌 검출을 보장하지 않는다. 실환경 inventory/migration은 후속 운영 승인 대상이다.

### E5 — fixture 보완

- D03의403은 삭제 verifier가 실제 서명을 검증할 수 있는 키로 서명된 wrong-purpose token일 때다. 미신뢰 purpose별 키면401이다. issuer 문자열만으로403으로 분류하지 않도록 두 사례를 구분한다.
- D07/D08에 operation 요청+24시간과 delivery 첫시도+24시간이 서로 다른 경우, delivery 격리 후 안전한 cleanup 지속, 일부ACKED·전체미완료/완료후30일 경계를 추가한다.
- D10에 target cleanup 갱신으로 version이 바뀐 응답, 동일event 다른body 동시경쟁, 재시도199/200/201 경계,24시간 직전/정각, stale204, crash 소진을 구체화한다.
- wire의 손상UTF8/lone surrogate는 설명 문자열이 아니라 원문 byte 입력으로 제작하고 각각 분리한다. canonical escaping/Unicode/순서의 기대 bytes·digest, UUID/시각/scalar 오류, 압축/인증우선/port/query 경계도 실행 가능한 벡터로 연결한다.
- W10/W11은 과거 인계서에서 재삭제 의미였으나 이번 manifest에서는 크기초과/UTF8이다. revision+ID로 인용하고 구번호→신규 S04/W11 등 의미 대응표를 제공해 기존 테스트를 오인하지 않도록 한다.

## 4. 주요 위험·미확인 사항

기존 작업을 막연히 ACK 대기로 멈추면 개인정보 cleanup이 지연되고, 반대로 기존 allowNewLearning을 유지하면 Billing 반영 전에 신규 시험이 열릴 수 있다. 현재 runtime 구현과 위 설계 수락은 다르다. Billing 코드/실제 인덱스·실환경 키·운영값은 이번 검토에서 확인하지 않았다. lifecycle/merge/withdrawal·TTL 및 실제 HTTP 응답 처리는 독립 테스트가 필요하다.

## 5. 이번 작업·다음 순서

이번은 LC 문서 회신 준비다. Billing 문서/fixture나 제품 코드를 수정하지 않았다. 위 기술 보완을 새 revision에 반영 → raw fixture/기대 bytes 확정 → 양측 hash 대조·독립 검증 → 별도 승인된 구현 순서로 진행한다. 현재44개는 테스트 명세 초안이며 통과 수가 아니다.

## 6. 부록 — 정적 검증 근거

- [DeletionCommandService](../../src/main/java/web/tosunsaeng/domain/learningrecorddeletion/application/DeletionCommandService.java), [DeletionAccess](../../src/main/java/web/tosunsaeng/domain/learningrecorddeletion/application/DeletionAccess.java): 요청 Transaction/읽기·쓰기 차단.
- [DeletionTarget](../../src/main/java/web/tosunsaeng/domain/learningrecorddeletion/domain/DeletionTarget.java): operation-scoped ID, 기존 version CAS, delivery 미구현.
- [DeletionWorker](../../src/main/java/web/tosunsaeng/domain/learningrecorddeletion/application/DeletionWorker.java), [DeletionOperation](../../src/main/java/web/tosunsaeng/domain/learningrecorddeletion/domain/DeletionOperation.java): stage 순서/NEEDS_REVIEW 조회 제외/24시간 예산/30일 retention.
- Billing cases.json을 읽기 전용 JSON parse·44개 ID 유일성·12/18/14 집계로 확인했다. SHA-256: `375ab7785126aac64e6336f782b396ddaf75d7e6c5d56bf082c9c0f27b866b27`. 이는 관측 hash이며 공동 동결·expected hash 검증이나80개 등 다른 fixture 결과와 합산하지 않는다.
- runtime/HTTP/Mongo 테스트는 실행하지 않았다. 문서만 변경하므로 Gradle 미실행. 기존 P6.1 dirty/사용자파일·공개API/AI/S3/Redis와 기존 fixture 원본을 보존한다.
