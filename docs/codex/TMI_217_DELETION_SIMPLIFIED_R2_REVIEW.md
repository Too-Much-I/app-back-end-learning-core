# TMI-217 삭제 단순화 R2 — LC 검토 회신

- 날짜: 2026-10-16 (Asia/Seoul)
- 상태: 승인 방향의 LC 설계 적합성 확인. exact schema/fixture 공동 동결·제품 구현·배포 완료가 아니다.
- 원문: [Billing R2](/Users/msde76/.codex/worktrees/plan012-refund/billing/docs/contracts/TMI-217-DELETION-SIMPLIFIED-R2.md). 기존 draft-3/R1의 상충하는 재삭제·Billing 독립 사용자 인가 요구는 R2로 대체한다.

## 1. 5줄 결론

1. S1: 사용자 삭제 인가를 LC에서 완결하는 역할을 수락한다. 현재 owner/merge/탈퇴/삭제 경합 검증을 생략하지 않는다.
2. S2: 삭제 요청별 event와 같은 전송의 불변 재시도를 수락한다. operation 간 최초 fact 재사용·전역 Session unique는 요구하지 않는다.
3. S3: 원문 정리와 Billing 성공 대기를 분리하고 필요한 empty204 전 새 시험 차단을 유지하는 방향을 수락한다. 현재 runtime에는 추가 연결이 필요하다.
4. S4: 이미 삭제된 exact 대상의 no-op과 event 충돌 우선 검사를 수락한다. Billing 최소 저장 schema·경합 fixture는 공동 확인 후 동결한다.
5. 이번은 LC 문서 회신 준비이며 코드·DB·fixture·배포·외부 전송을 수행하지 않았다. 환불 자료는 계약 준비의 선행조건이 아니다.

## 2. 반드시 읽어야 하는 내용

LC는 인증된 현재 사용자와 실제 Session 귀속을 검증해 서버가 확정한 대상만 전달한다. Billing의 새 guestMergeEvidence, Identity 이력 추가, 삭제용 과거 merge backfill은 요구하지 않는다. 기존 creationOwner 및 다른 기능의 검증을 제거한다는 뜻은 아니다.

같은 전송 재시도는 원 eventId/body를 유지하고 JWT만 새로 발급한다. 새 삭제 operation은 새 eventId를 사용할 수 있다. 보존 중 같은 eventId/다른 body는 이미 삭제된 대상이어도 먼저409 EVENT_ID_CONFLICT다. 다른 eventId의 이미 삭제된 exact Session/group/environment는 연결 증명 뒤 empty204 no-op이며 최초 삭제 사실·현재 guard·권리를 덮어쓰지 않는다. Session과 정확한 삭제 증거가 모두 없으면 성공을 추정하지 않는다.

원문 정리 전에 불변 전송자료를 영속화하고 네트워크는 Transaction 밖에서 호출한다. 로컬 성공은 해당 전송과 결속한다. 원문 정리는 Billing 장애와 독립 진행하지만 필요한 전달의204를 확인하기 전 새 시험은 허용하지 않는다. 별도 ACK API는 추가하지 않는다.

## 3. 결정 및 공동 확정 사항

사용자 제품 정책을 다시 선택할 필요는 없다. 다음 기술 명세는 구현 전 공동 동결 대상이다.

- 기존 전용 인증·7필드 wire의 exact 규격과 LC sender 매핑, 오류별 재시도/격리 및 성공 기록 조건.
- Billing의 최초 Session deny와 event별 멱등 증거의 최소 저장 구조. 다른 event의 no-op도 보존기간 내 같은ID/다른내용 충돌을 검출해야 하며 무한 배열은 사용하지 않는다.
- LC target의 요청별 불변 payload/전달 상태, 필요한 전달 전체의 성공 판정 및 원문 정리 후 복구 경계.
- 미전달 증거의 기존30일 TTL 제외와 완료/deny/멱등 목적별 보존·정리 조건. 무기한 보존이나 실제 purge를 승인하지 않는다.

## 4. 위험·미확인 사항

- LC 인가 오류/침해로 잘못된 유효 요청이 발생하면 Billing이 사용자 권한을 독립 재판정하지 못하는 위험은 승인된 절충이다.
- Billing은 실제 Session/group/환경 연결과 현재 owner/activeSession/source에 따른 변경 영향의 Tx/CAS 검증을 유지해야 한다. 과거 Session은 exact deny만 적용하고 다른 현재 시험·guard·권리를 보존한다. 영향 근거 불명은 unresolved다.
- 현재 LC DeletionWorker는 로컬 준비/봉인 뒤 새 학습 허용을 기록한다. Billing204 대기 연결 없이 새 정책이 구현됐다고 볼 수 없다.
- 기존 operation/target/command의30일 보존 경로가 미전달 증거를 제거하지 않도록 함께 수정해야 한다. Billing 최신 구현이나 공동 경합 테스트는 이번에 검증하지 않았다.

## 5. 구현 준비·검증 범위

S1은 현재 사용자 guard·탈퇴 검사와 target 준비/봉인을 연결해 merge/삭제/생성 양순서 경합을 검증한다. 단순 creationOwner 일치나 최초 요청 시점 검사만으로 이후 소유권 검증을 대체하지 않는다.

S2는 기존 operation-scoped DeletionTarget을 활용할 수 있다. 불변 payload/eventId 및 전달 상태 필드는 신규 구현 대상이다. S3는 원문 cleanup과 새 학습 checkpoint를 분리하고 미전달 retention을 보호한다. 전용 JWT purpose/sender도 추가 구현 대상이다.

S4 fixture는 W10을 다른event/같은삭제대상204 no-op으로, W11을 같은전송 불변/새요청 독립으로 수정한다. O03/O08/O09는 LC 인가와 Billing 대상·현재권리 보호를 나눠 검증한다. 같은event 충돌 우선, 증거 없는 대상, 잘못된group/환경, 응답 유실, merge/phone/환불 경합, 새guard 불변,30일 경계 미전달 보존도 포함한다. 기존 결과를 새 정책의 통과 증거로 사용하지 않는다.

## 6. 부록 — LC 코드 근거

| 파일 | 확인한 현재 동작 | 필요한 변경 |
| --- | --- | --- |
| [DeletionCommandService](../../src/main/java/web/tosunsaeng/domain/learningrecorddeletion/application/DeletionCommandService.java) | owner guard/탈퇴 검사, 동일key replay, 생성중 삭제409 및 Transaction 순서화 | exact 전송 대상 인가·봉인 경합 검증 유지 |
| [DeletionTarget](../../src/main/java/web/tosunsaeng/domain/learningrecorddeletion/domain/DeletionTarget.java) | deletionId/type/aggregateId로 요청별 식별, 로컬 정리 상태·expiry | 요청별 불변 전송자료·성공 상태 |
| [DeletionWorker](../../src/main/java/web/tosunsaeng/domain/learningrecorddeletion/application/DeletionWorker.java) | 로컬 준비/봉인 후 allowNewLearning, expiry 전파 | 필요한204 checkpoint·cleanup 분리·미전달 보존 |
| [DeletionOperation](../../src/main/java/web/tosunsaeng/domain/learningrecorddeletion/domain/DeletionOperation.java) | 보존 hold와 완료 후30일 경계 | 전달 미완료 때 관련 증거 전체의 TTL 방지 |

정적 대조와 문서 diff 검사만 수행했다. 제품 동작 변경이 없어 Gradle/실DB 테스트를 재실행하지 않았다. 기존 P6.1 미커밋 변경과 사용자 파일은 보존하며 공개 API/AI/S3/Redis 및 기존 fixture 원본은 변경하지 않는다. 다음 단계는 exact 저장·오류·fixture revision 합의 후 별도 승인된 LC 구현이다.
