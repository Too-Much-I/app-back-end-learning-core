# TMI-217 삭제 후 INITIAL·paid 생성 경로 연결

## 1. 5줄 결론

- 2026-10-16 사용자 후속 구현 승인으로 LC 코드와 격리 테스트만 변경했다.
- 전체 삭제 ACK checkpoint와 exact 원 시험 증거를 확인한 요청만 새 INITIAL로 연결한다.
- 부분 접수는 동일 문제, 전체 접수는 다음 문제 선정이며 최초 요청 판정을 고정한다.
- 공개 생성 adapter와 schema2 worker 분기를 연결했지만 paid 활성화 차단은 유지한다.
- legacy runtime·실 Billing 공동 검증·실환경 전환은 완료하지 않았다.

## 2. 반드시 읽어야 하는 내용

삭제된 Session 대신 `DeletedExamContinuation`에 보존한 sourceCreatedAt/paidStartReason과 삭제 target의 ACK를 사용한다. operation에는 deletionId/eventId를 고정하고 기존 restartContext wire를 사용한다. ACK의 payload hash, eventId, actor, deletionId, group, Session이 일치해야 한다. ACK 전 또는 coverage/순서 근거가 없는 과거 자료는 임의 INITIAL로 우회하지 않는다.

예약 응답은 INITIAL·새 group·exact transition만 허용한다. Billing confirm/status 증거 뒤 local Transaction에서 새 Session 활성·operation 성공·삭제 증거 TRANSFERRED를 함께 기록한다. 원 Session/답안/Job은 재생성하지 않는다. 영속적인 예약 전 거절로 terminal이 확인되면 claim을 해제하지만 timeout만으로는 해제하지 않는다.

`PaidExamCreationSaga`는 최초 고정/요청 digest 저장 뒤 reserve+confirm/status를 기존 lifecycle로 수행한다. 같은 key는 같은 operation으로 돌아가며 성공 Session이 삭제됐다면 404를 반환한다. phone discovery 전 owner gate와 이후 준비 Transaction을 유지한다. 기존 schema1 operation은 기존 saga로 drain한다.

공개 URL/Method/body/성공 DTO/BaseResponse, AI user_id=examId, S3/Redis 및 Billing wire를 변경하지 않았다. `app.billing.paid-exam.enabled` 기본 false와 `PaidExamActivationGate`의 ON 거절을 유지한다. 자동복구의 schema2 조회/dispatch/auth probe는 해당 flag 뒤에 있으며 worker는 새 reserve나 Session을 만들지 않는다.

## 3. 사용자 결정 사항

이번 변경에는 새 제품 정책 결정이 필요하지 않다. 배포·활성화·실DB/index·purge·commit/push는 수행하지 않았으며 별도 승인 대상이다.

## 4. 위험·미확인 사항

- source가 없는 출시 전 시험의 manifest 기반 runtime 경로는 여전히 후속 개발이다. source 누락을 legacy 증명으로 사용하지 않는다.
- 과거 삭제 증거에 coverage/sourceCreatedAt이 없거나 최신 대상 순서가 모호하면 fail-closed한다. 이번에 과거 데이터를 backfill하지 않았다.
- 실 Billing receiver와 연결한 HTTP/TLS·양측 31개 경계/경합·rollout/index 확인은 미실행이다. 로컬 fake Billing 성공을 공동 E2E 통과로 해석하지 않는다.
- 공개 paid 경로 전체의 실환경 승인이나 전체 개발 완료가 아니다. 기동 차단을 해제하기 전에 legacy·phone/owner·오류 복구와 공동 계약 회귀를 함께 검증해야 한다.

## 5. 변경 범위와 검증

주요 파일:

- `PaidExamCreationSaga`, `ExamServiceImpl`: default-off 공개 adapter 연결.
- `PaidExamPreparationService`, `PaidExamLifecycleService`, `PaidReservationProtocol`, `ExamCreationOperation`: 삭제 증거 고정·INITIAL 검증·원자적 마무리.
- `DeletedExamContinuation`, `DeletedExamContinuationStore`: 접수 판정 보존·ACK 검증·claim/terminal 처리.
- `ReservationRecoveryStore`, `BillingReservationReconciliationScheduler/Service`: schema2 조회·worker/status auth probe 분기.
- `DeletionFlowMongoTest`: 실제 격리 Mongo Transaction + fake Billing component 검증.

추가 검증은 부분 접수 동일 문제/INITIAL·same-key 1회 reserve·삭제 후 성공 replay 404, 전체 접수 다음 문제/ACK 전 거절, REPLACEMENT 거절, coverage 누락 차단, schema2 OFF/ON 조회, 영속 거절 claim 해제와 비부활이다. 최종 `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew clean test mongoIntegrationTest bootJar` 성공: 단위1,306개 중1,305통과·기존1skip, Mongo272개 전부통과, 실패/오류0. `git diff --check` 통과. 최초 집중실행에서 테스트의 import 누락과 due시각 도달 전 조회를 수정한 뒤 재검증했다.

기존 P6.1/삭제 sender dirty와 사용자 `.DS_Store`를 보존했다. Billing/Identity·기존 fixture bytes·실DB·운영 설정은 변경하지 않았다.

## 6. 근거

- [삭제 sender 구현](TMI_217_LC_DELETION_SENDER_IMPLEMENTATION.md)
- [삭제 exact R2 수락](TMI_217_DELETION_EXACT_R2_ACCEPTANCE.md)
- [전체 구현 계획](LC_BILLING_PAID_EXAM_IMPLEMENTATION_PLAN.md)
- Billing 읽기 확인: `PaidReservationService`의 삭제 fact CURRENT_GROUP_BLOCKED/new INITIAL 경로와 `Reservation.withTransitionContext`의 exact 원 대상 응답. Billing 저장소에는 쓰지 않았다.
