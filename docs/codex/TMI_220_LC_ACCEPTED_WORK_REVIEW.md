# TMI-220 — LC 기접수 작업 보완 검토·회신

> 후속 구현: 사용자 「보완 해줘」 이후의 코드·검증 상태는 [구현 보고](TMI_220_LC_ACCEPTED_WORK_IMPLEMENTATION.md)를 따른다. 아래는 선행 정적 검토 당시 기록이며 공동 E2E는 여전히 미완료다.

- 날짜: 2026-10-14 / 기준: Billing `TMI-220-REFUND-PROTECTED-SESSION-CLARIFICATION.md` 사용자 승인 보완본.
- LC 기준: develop 머지 e894408 및 현재 `feat/billing-paid-exam-p6` 작업 트리. 정적 검토만 수행했다. 새 제품 코드/테스트/fixture/DB/배포 변경·공동 E2E 실행 없음.
- 관련: TMI-220/TMI-217/TMI-219, LC TMI-116/TMI-128/TMI-122/TMI-125·TMI-193/TMI-136.

## 1. 5줄 결론

1. 승인된 보완 방향을 LC 설계 기준으로 접수했다. 현재 LC가 이미 지원한다는 의미는 아니다.
2. retry0 receipt/전체접수 marker·접수차단 시각·confirm snapshot은 재사용 가능하지만 환불 후 과거 Session 작업을 허용하는 검증은 추가해야 한다.
3. 신규사용 차단은 그대로 두고 exact 기접수 작업/파생 Summary/늦은 상태 전이만 좁은 증거 검증으로 분리해야 한다.
4. 단일 confirmedSessionId·불변 revoke·공개 API/AI/S3/Redis wire는 변경할 필요가 없다는 방향이며 내부 증거와 모든 처리 경로의 회귀가 필요하다.
5. C01~C06 및 H14~H16은 아직 미실행이다. Billing 수정본에서 기존 H11~H13 보존·새 항목 H14~H16 재번호와 접수/TTL/순서 보완을 확인했다.

## 2. 반드시 읽어야 하는 내용 — Billing 회신

> 보완 기준을 접수했습니다. LC에는 retry0 영속 접수 receipt, 필수집합/전체접수 완료 marker, 이전 Session 접수차단 시각과 operation 연결, 예약/confirm 귀속 snapshot이 있어 재사용할 수 있습니다. 다만 현재는 accessRevoked가 기접수 처리보다 먼저 거절되며 기존 Job 제출표시만으로는 접수의 순서를 충분히 입증하지 않으므로 그대로 지원하지는 않습니다.
>
> LC는 신규 제출·새 녹음/새 Session 차단을 유지하고, 환불 전 confirm과 재시작 전 exact 접수·원 owner/source/lifecycle을 검증한 이전 Session의 작업만 별도로 허용하도록 검토했습니다. 단일 confirmedSessionId나 이벤트 body를 변경하지 않고 기존 채점/Callback/복구/파생 Summary 및 원 Session 상태 이벤트까지 함께 대응해야 합니다. 보호 대상으로 지정된 현재 Session의 기존 정책을 과거 Session 예외와 혼합하지 않습니다.
>
> Billing은 과거 Session 이벤트의 귀속/허용 전이 및 현재 group 포인터·보호 Session·새 guard 무변경을 담당해 주세요. C01~C06/H14~H16은 아래 행렬대로 LC 단위·실제 Job/격리 Mongo·Billing 검증·양측 HTTP 검증을 분리합니다. 이번 회신은 정적 검토이며 구현/공동 E2E/배포 완료가 아닙니다.

이 회신 문서는 LC 저장소에만 작성했다. Billing 파일 수정이나 타 채팅/외부 서비스 전송은 하지 않았다.

## 3. 결정·공동 확인 사항

- 추가 제품 선택은 필요 없다. 구현 전에 재시작 경계(LC의 원 Session 접수차단 Transaction/operation), confirm 성공과 revoke 적용 순서의 신뢰 근거를 양측 테스트 입력으로 고정한다. 서버 간 시각 숫자 비교만으로 commit 선후나 정당한 귀속을 추정하지 않는다. 동시각/응답유실/마무리 지연도 포함한다.
- 신규/새 녹음 입력과 기존 exact Job의 시스템 재시도는 다르다. 이미 적법하게 접수한 retryCount>0 Job이 있다면 그 exact 작업의 과거 접수 증거가 필요하며, 새 retryCount>0 생성 허용으로 확대하지 않는다.
- 2026-10-14 후속 재확인: 인계서 §6에서 기존 H11~H13(다른권리/TTL·전파/인증)은 유지하고 새 과거Session/부분접수/늦은event를 H14/H15/H16으로 정정한 것을 확인했다. 번호 중복 지적은 해소됐다. C01·C02→H14, C03→H15, C04·C05→H16, C06은 공통 음성 경계다. 원 fixture ID/bytes는 변경하지 않는다.
- 보완 문서 §3.1에서 pendingAt의 최초접수 근거 사용 금지, receipt 전체접수 후3일 TTL과 marker 사용 한계, Tx/operation·권위 저장 사실/fence 기준 순서, Callback/복구/시스템재시도/Summary/terminal/outbox 및 Billing 과거event 분담을 확인했다. LC 검토 요청과 문서상 정합하며, 실제 증거 reader·경합 테스트 구현은 여전히 후속이다.

## 4. 위험·미확인 사항

- `QuestionGradingJob.activityUserSubmission`은 실제 제출 유래 표시지만 접수시각이나 현재 전달된 job 필드의 exact 귀속을 모두 증명하지 않는다. `pendingAt`은 `reopenMissingResult`에서 바뀌므로 최초 접수시각으로 사용할 수 없다.
- `ExamSubmissionEvidence` receipt는 retry0만 기록한다. 전체접수 완료 후3일 TTL이 있으므로 이후에는 불변 필수집합과 전체접수 marker를 사용해야 한다. 부분접수/기존 Job 재시도/다른 retryCount에 같은 추론을 적용하면 안 된다.
- 현재 `processable`은 COMPLETED를 일괄 차단하지 않는다. 확정 종료 이후 새로운 상태/결과 mutation 금지와 이미 저장된 결과의 멱등 no-op/조회는 구분해 검증해야 한다. 모든 완료 Callback을 무조건 새로 처리하거나 기존 결과를 지워서는 안 된다.
- 확인된 과거 owner/source/confirm/접수 증거가 없는 자료는 거절/보류하며 소급 생성·owner alias하지 않는다. source 없음·Job 존재·파일/결과 존재만으로 예외를 부여하지 않는다.
- TMI-220 문서의 Billing 구현 예정 설명을 실제 Billing 코드 완료/테스트 성공으로 재검증하지 않았다. 이 작업은 LC 근거 검토다. runtime 활성화 차단과 기존 P6/V07 gate는 유지한다.

## 5. 필요한 LC 변경 범위

| 영역 | 현재 근거/차이 | 필요한 변경 방향 |
| --- | --- | --- |
| 접수 증거 | receipt acceptedAt, requiredQuestionNumbers, submissionCompletedAt; Job 제출표시 있음 | retry0 기존 증거 재사용. Job 경로는 immutable 최초 접수 근거·exact exam/question/retry/mock/job 귀속 검증 보강. 새 collection/문항 배열 복제는 우선하지 않음 |
| confirm·재시작·revoke 연결 | paidReservation/entitlementConfirmedAt, submissionsClosedAt/ByOperationId, 단일group revoke fact | 원 Session confirm·source/owner·원 group·재시작 operation 및 revoke 사실을 함께 검증. 접수/close/revoke 동시성을 같은 owner/Session Transaction fence 안에서 확인 |
| 권한 policy | processable의 accessRevoked 선행 거절, Job boolean이면 허용하는 분기 | 신규사용 policy는 유지. 과거 기접수 처리 전용 검증 결과를 사용하되 모든 accessRevoked를 무시하는 옵션은 금지 |
| Question/Callback/복구 | GradingService와 ServiceImpl에 별도 선행 processable·revoked 검사 | dispatch/Callback 저장/누락Job복구/시스템 재시도마다 동일 exact 근거 검증. 공개 submit·upload·새 녹음 생성은 계속 거절 |
| Summary | 전체접수 marker/필수집합 검증 후 기존 Job/결과 조건 | 전 문항 접수와 기존 채점·요약 조건 모두 필요. 부분접수는 해당 문항만, 파생Summary는 별도 적격성 확인. Scheduler/Callback/누락Summary복구까지 적용 |
| 원 Session terminal/outbox | ExamSession.requireTerminalAvailable도 accessRevoked를 거절 | 증거검증된 과거 Session에 한한 terminal 전이 통로 필요. 일반 terminal 메서드의 blanket 차단 삭제 금지. 원 Session projection/event만 갱신하고 새 Session/operation guard는 불변 |
| 삭제/탈퇴/owner | 기존 owner Tx와 deletion fence 재사용 가능 | 처리 직전 재검증. 결과/Session/계정 비부활, 기존 성공 재현과 신규 mutation 구분 |

`AccessRevocationStore`의 accessRevoked projection은 신규사용 차단 사실로 유지하는 방향이다. 보호 ID 변경/다중 ID/새 eventVersion을 만들지 않는다. 검증기를 구현하더라도 기존 격리 테스트의 blanket-deny 기대값을 새 좁은 예외의 양성·음성 검증으로 나누어야 하며 단순 테스트 삭제는 아니다.

## 6. 부록 — 테스트 대응과 코드 근거

| 공동 ID | LC 검증 예정 | Billing/공동 확인 | 현재 상태 |
| --- | --- | --- | --- |
| C01 / H14 | confirm→접수→재시작→revoke 후 S1 dispatch/Callback/기존 시스템 재시도, 보호 S2 회귀 | 동일 불변event 실제HTTP→LC 저장/Job 처리, 양측 원 대상 귀속 | 미실행; 현 LC blanket deny와 충돌 |
| C02 / H14 음성 | 새 submit/upload·새 retryCount·새 Session 거절, Job ID 위장/잘못된 mock·question/retry 거절 | 환불 source 재사용0 | 기존 차단은 있으나 새 예외 적용 후 재검증 필요 |
| C03 / H15 | 부분접수 문항만 허용, Summary/COMPLETED0; 전체접수 후 TTL·파생Summary·채점요건 양성/음성 | 부분결과로 Billing 전체완료 처리0 | 기존 P4 기반 있음, 환불 결합 미실행 |
| C04 / H16 lifecycle | 삭제/탈퇴/owner변경/확정terminal과 Callback·retry 양순서, 일시실패와 구별 | 지연event 비부활·멱등 no-op/오류 경계 | 새 예외와의 경합 미실행 |
| C05 / H16 경합 | S1 결과/terminal/outbox만 변경, 중복·역순·동시 S2/새operation 비교 | group.activeSessionId/S2/새guard 변경0, Billing revision·상태 전진 | 양측 독립+실제HTTP 필요, 미실행 |
| C06 | confirm/source/owner/receipt 불일치·미확정·시각역전·동시각·3일 TTL·재시도 pendingAt 변경 | 권위confirm/revoke 및 과거Session 귀속 불명 시 거절/보류 | 미실행; raw 값만의 허용 금지 |

이번 테스트를 이전1,252개 단위/248개 Mongo 성공 건수에 포함하지 않는다. 이번에는 코드 변경이 없어 Gradle을 재실행하지 않고 문서/코드 정적 대조와 diff check만 수행했다.

주요 코드 근거(저장소 상대 경로):

- `domain/exams/application/ExamSubmissionEvidence.java`: retry0 receipt·전체 marker·3일 TTL, 접수차단과 같은 version write fence.
- `domain/exams/domain/entity/QuestionGradingJob.java`: activityUserSubmission·reopenMissingResult의 pendingAt 변경.
- `domain/exams/application/PaidExamLifecycleService.java`: CONFIRMED에서 exact 이전 close·새 activation·operation 성공을 원자 처리.
- `domain/exams/access/AccessRevocationStore.java`: 단일 immutable group fact와 비보호 Session projection.
- `domain/exams/application/ExamAcceptedWorkPolicy.java`, `ExamGradingService.java`, `ExamServiceImpl.java`, `SummaryDispatchScheduler.java`: 선행 deny와 처리 경로.
- `domain/exams/attemptgroup/application/AttemptGroupEvidenceEvaluator.java`, `domain/exams/domain/entity/ExamSession.java`: Summary 적격성과 terminal 추가 차단.
- 기존 `ExamAcceptedWorkPolicyTest`, `PaidExamP3MongoTest`, `AccessRevocationMongoTest`: 재사용할 기반 회귀이며 C01~C06 공동 통과 증거는 아니다.

이번 변경은 본 회신 문서·CURRENT_STATE·WORKLOG뿐이다. 기존 P6 dirty 코드/문서와 사용자 파일은 보존했으며 예상 밖 제품 변경, 외부 전송·commit/push·DB·배포는 없다. 다음은 별도 구현 요청 후 위 처리 경로를 함께 보완하고 각 계층의 실제 결과를 분리 보고하는 것이다.
