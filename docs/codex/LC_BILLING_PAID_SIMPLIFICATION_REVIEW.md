# 유료 시험 단순화 — LC 최소 wire·fixture 검토

- 날짜: 2026-10-14 / 관련 PLAN-011/012/013, TMI-116/TMI-128/TMI-122 및 TMI-125
- 사용자 전달 최신 승인 방향을 기준으로 한 기술 제안. draft-3 snapshot/A 인증/fixtures는 수정하지 않는다. 신규 exact 명칭과 양측 업무 판정은 공동 동결 전 후보다. 구현/DB/배포 작업 아님.

## 1. 핵심 결론

단일 mockExamId에 원 group/Session과 **LC 서버가 최초 준비 시 고정한 시작 사유**를 함께 보내는 방안을 권고한다. 문제 ID 일치 여부·Billing OPEN/GRADING 상태·source enum으로 재응시를 추정하지 않는다. 문제 선택과 Billing의 소비/권리 선택은 별개다. 모든 전환에 기접수 완료를 허용한다는 최신 정책은 이전의 전환 type별 LC 처리 분기를 대체한다.

## 2. 최소 요청 후보

기존 reserve userId/sessionId/mockExamId 및 Idempotency-Key는 유지한다. 앱 공개 API/body/성공DTO에 새 필드를 추가하지 않는다.

| restartContext 필드 | 제안 |
| --- | --- |
| previousAttemptGroupId | 기존 exact 원 group |
| previousSessionId | 기존 exact 원 Session 또는 삭제 보존 증거의 sourceExamId |
| startReason | RESTART_INCOMPLETE / NEW_AFTER_SUBMISSION / NEW_AFTER_COMPLETION 후보 |

initialMockExamId는 제거한다. top-level mockExamId 하나는 LC가 실제 새 Session에 사용할 문제다. 이전 문제 echo가 필요한 RESTART_INCOMPLETE에서만 Billing의 원 group mock와 같아야 한다. NEW_AFTER_SUBMISSION/COMPLETION에서 원 group mock와 다를 수 있으나, 차이 자체가 새로운 권리의 근거는 아니다.

- RESTART_INCOMPLETE: 필수 retry0 답안 일부 또는 미접수임이 확인됨. 같은 문제를 사용한다. Billing이 동일 유효 권리로 판단하면 기존 group REPLACEMENT, 다른 유효 권리로 전환 가능하면 새 group INITIAL도 가능하다. 사유가 RESTART라고 무료권 사용이나 replacement를 강제하지 않는다.
- NEW_AFTER_SUBMISSION: 모든 필수 retry0 답안의 durable 접수 완료, 결과/요약은 미완료. Billing 상태가 OPEN이어도 기존 consumption의 REPLACEMENT로 처리하지 않고 별도 신규 권리 판정 후 새 group INITIAL이다. 같은 mock가 선정돼도 이 원칙은 같다.
- NEW_AFTER_COMPLETION: 이미 완료된 원 시험을 기준으로 새로 시작하는 경우. 새 group INITIAL과 신규 권리 판정이며 기존 완료 기록을 유지한다. 완료판정과 접수증거를 구분하기 위한 세 번째 값이다. COMPLETE 상태와 별도 접수증거를 항상 동일하게 증명할 수 있다고 가정하지 않는다.
- 기존 시험이 없거나 Billing 미연결 legacy만 있으면 restartContext 없이 일반 INITIAL, 원 legacy 대상/선정 근거는 LC operation에만 보존한다. source 없는 legacy를 Billing 그룹에 연결하지 않는다.
- 접수 증거가 없거나 충돌하면 INCOMPLETE로 임의 강등하지 않고 복구/검증 대상으로 삼는다. 기존 legacy 이용 규칙을 일괄 차단하는 변경과는 구분한다.

startReason은 인증된 LC의 서버 증언이며 Billing이 원 group/session/caller/owner를 검증한 뒤 사용한다. 클라이언트 입력값이 아니다. 사유·선정mock·원대상을 canonical request digest와 operation에 고정한다. 같은 key replay는 이후 접수가 완료돼도 최초 사유/문제를 유지한다. 새로운 사용자 요청만 최신 접수 상태를 다시 판정한다. Billing은 사유를 받아도 무료권 추가 지급/소비 면제·환불 source replacement를 허용하지 않는다.

## 3. LC 접수 증거 및 준비 경합

현재 NotificationSubmissionTracker는 새 사용자 Job insert와 같은 Transaction에서 필수 retry0 문항 receipt를 쓰고 전부 접수되면 ExamSession.submissionCompletedAt을 남긴다. 완료 receipt는 3일 뒤 삭제되지만 Session 완료 marker는 남는다. 다만 **알림 tracking flag가 켜졌을 때만 실행**되므로 marker 없음이 부분접수의 증명이 아니다.

유료 기능의 필수 접수 증거는 OS/알림 tracking 설정과 독립적으로 보장해야 한다. 현행 Job의 activityUserSubmission 구분과 필수 문항 집합을 활용해 신규 서버 접수와 복구로 재구성한 Job을 구분한다. 기존 AttemptGroupEvidenceEvaluator는 Job/결과를 함께 사용하므로 그 gradingReady를 검토 없이 durable 접수 판정으로 재사용하지 않는다. 이전 Session의 불변 required question 집합과 중복제거·retry0 조건을 정의한다.

준비 operation 저장과 마지막 답안 접수는 같은 Session version/fence의 Transaction/CAS로 순서화한다. 준비가 먼저면 INCOMPLETE와 같은mock가 고정되고 뒤늦은 접수가 이를 바꾸지 않는다. 마지막 접수가 먼저면 NEW_AFTER_SUBMISSION으로 고정한다. confirm까지 기존 Session을 미리 막지 않으며 준비 이후 실제 종료 전 접수된 Job도 로컬 접수/종료 순서에 따라 완료한다. owner·삭제/탈퇴 fence를 함께 지킨다.

## 4. 문제 선정의 현행 차이

ExamSessionManager.selectExam은 완료 횟수가 가장 적은 문제를 고르고 동률이면 catalog sequence를 사용한다. 요약 대기 시험은 현행 완료 count에 아직 포함되지 않을 수 있다. 따라서 '기존 정책으로 다음 시험 선택'은 **이전과 다른 문제 보장**이 아니다. 같은mock가 선택돼도 NEW_AFTER_SUBMISSION은 INITIAL이다.

후속 사용자 승인 인계 §1/9에 따라 전체 접수·채점/요약 대기에서는 직전mock를 제외한 적격 후보에 기존 count/sequence 규칙을 적용한다. 다른 적격 후보가 실제0개일 때만 직전mock를 허용한다. catalog 장애를 후보0개로 취급하지 않는다. 이미 완료된 시험 이후에는 기존 선정 규칙을 유지한다. 접수 완료를 실제 결과 COMPLETED나 Billing 소비 완료로 위조하지 않는다. 이는 승인된 목표 동작이며 현행 코드 구현 완료를 뜻하지 않는다.

## 5. 최소 응답·마무리

- entitlementSource=EXAM_PASS/SUBSCRIPTION은 유지한다. source·kind·mock·신규Session/group 및 원 연결을 snapshot→operation→Session/복구 증거에 보존한다.
- transition.type과 복원 세부값은 제거하고, Billing-linked 원 group에서 다른 group INITIAL로 전환한 경우 transition={previousAttemptGroupId,previousSessionId}를 반환하는 안을 권고한다. REPLACEMENT/원대상 없는 INITIAL에는 없음. 일반 완료 후 신규 INITIAL에서 원 group 연결 object를 포함할지도 같은 조건으로 명시해야 하며, object 존재만으로 완료된 원 시험을 종료하지 않는다.
- LC는 저장된 요청 사유와 응답 kind/group/mock 조합을 검증한다. startReason echo는 필수 아님: 동일 operation의 요청 snapshot과 대조한다. NEW_AFTER_SUBMISSION/COMPLETION에 REPLACEMENT가 오면 계약 오류다. RESTART_INCOMPLETE에 INITIAL이면 새group/exact transition, REPLACEMENT면 같은group/동일원mock를 확인한다.
- 전체 전환에서 confirm 성공 뒤 신규 제출 차단·새 Session 활성·operation 성공을 LC Transaction으로 저장한다. 이미 완료/삭제된 대상을 부활시키지 않는다. 전체 제출 판정이 Billing 복원 자격을 대체하지 않으며 복원은 Billing 내부 최초 trial consumption 증거와 기존 완료/confirm 경합 규칙에 따른다.
- phone/삭제 continuation에 startReason을 어떤 증거로 채울지 별도 동결한다. 삭제 보존 모델에 접수판정 증거가 없다면 원 Session 없음만으로 INCOMPLETE를 만들지 않는다. phone 6-field와 restartContext 배타성도 유지 또는 개정 여부를 명시한다. 모든 전환의 LC 완료정책 확정만으로 owner/권리 조합까지 활성화하지 않는다.

## 6. 불변 revoke 및 거절 시각

- accessVersion 제거, high-water/version별 semantic history 제거. 논리 group key는 환경+인증producer+consumerScopeId+attemptGroupId로 유지한다. 최초 eventId와 body/confirmedSessionId(null 포함)·reason·occurredAt을 고정한다.
- Billing은 group당 최초 outbox 하나, LC는 eventId와 group 양쪽 unique 제약 및 Transaction으로 원자적 inbox/deny를 저장한다. 동일 eventId+동일 canonical digest는204, 같은ID다른내용은 EVENT_ID_CONFLICT409 후보. 같은group에 다른eventId 또는 다른차단내용은 GROUP_REVOKE_CONFLICT409 후보로 권고한다. 새ID 동일의미도 허용할지 반드시 명시하되, 본 권고는 최초eventId 고정 정책에 맞춰 충돌로 처리한다.
- 단일 canonical SHA-256만 사용하고 별도 semantic digest는 제거한다. raw JSON 공백/필드순서가 아닌 검증 후 canonical bytes를 비교하는 기존 규칙을 재사용한다. sender 재시도는 원 canonical body/eventId를 유지하고 JWT만 갱신한다.
- 최초 차단 뒤 추가 환불/금액정정은 새 revoke를 만들지 않는다. 금전 이력만 Billing에 남고 예외 Session을 변경하지 않는다. 새 동시 revoke 후보도 Billing unique/CAS에서 최초 사실로 수렴해야 한다.
- inbox TTL과 group deny/최초eventId·digest/예외 증거 보존을 분리한다. group 증거까지 삭제하면 새로운 event를 최초로 받아들일 위험이 있으므로 안전폐기/replay 차단·개인정보 purge manifest는 계속 필요하다.
- confirmRejection은 code만 유지하고 시각은 terminalAt을 사용한다. CANCELED에서만 허용, 일반cancel과 구분, 성공confirm 보존/TTL 뒤 재현/status body/기존409·503·신규두409는 유지한다.

## 7. fixture 교체 범위

draft-3의 파일을 생성하거나 덮어쓰지 않고 새 공동 정본/revision에서 아래를 반영한다. 실제 fixture/hash/테스트 실행은 이번 검토에 포함하지 않는다.

| 기존 영역 | 교체/추가 |
| --- | --- |
| W01~W07/W20 요청·조합 | initialMock/type 제거, 시작사유별 kind/group/mock·원대상 검증, 부분접수 동일mock, 전체접수대기+BillingOPEN 신규INITIAL, 같은mock 신규INITIAL |
| W06/W17/W18 replay·경합 | 마지막접수와준비 양순서, 준비후접수완료여도 같은key 최초사유 재현, 사유변경 같은key conflict, 증거불명·trackingOFF·receiptTTL |
| W11~W14/W16 | 모든권리전환의 로컬마무리/기접수·파생Summary 완료, 부분접수 비COMPLETED, 종료뒤 신규접수 거절, lifecycle 우선 |
| W03/W04 | 최초trial만 복원1회, 복원/초대권 재복원0, 전체접수만으로 추가복원 없음, 늦은완료와복원 공존, 공통transition |
| W19 | phone/삭제 접수증거·startReason 매핑·owner검증·claim/transfer 및 unknown복구 |
| W09/W21 | rejectedAt 제거, CANCELED+code+terminalAt, 금지상태조합/일반cancel 구분 |
| R03~R05/R08/R09 | version/stale/semantic 삭제, eventId/group 양unique 충돌·동시첫수신·최초null고정·후속금액정정 동일사실·새ID동일group거절 |
| R01/R02/R06/R07/R10 | JWT/port/OFF/크기/Unicode·strict·삭제/탈퇴/보존은 유지, 새필드목록으로 golden bytes/hash 재계산; 구accessVersion 등은 unknownfield거절 |

추가 권리 조합의 허용 여부, 단일 선정에서 같은mock 허용, startReason exact명칭, phone/삭제 적용, 공통transition 존재조건, 그룹충돌 오류, 보존/삭제는 공동 정본에 명시한 뒤 fixtures를 만든다. 기존 A fixtures-1 bytes/hash는 변경하지 않는다.

## 8. 후속 문제 선정 확정 검토 — 2026-10-14

첨부 §1/9에서 직전mock 제외·실제 다른 후보0개일 때만 동일mock 허용, catalog 장애 fallback 금지, 실제 완료count 위조 금지, 알림tracking과 독립 접수증거 및 최초 판정/replay 고정이 반영됐다. 이 보완 방향에 추가 이견 없다. 앞 절의 동일mock 허용 조건은 더 이상 정책 미정이 아니며 위 확정 조건을 따른다.

신규fixture에 다른 후보 존재/실제0개/조회장애/선정오류, 준비 후 catalog·요약상태 변화에도 같은key 사유/mock 유지, 마지막접수와 준비 양순서를 포함한다. fallback으로 같은mock가 선택돼도 NEW_AFTER_SUBMISSION은 새권리 INITIAL이고 기존consumption REPLACEMENT가 아니다. 부분접수·완료후·기존시험없음 경로의 회귀도 함께 확인한다.

startReason exact enum·phone/삭제 증거 매핑·공통transition 조건·그룹불변revoke 오류/보존은 아직 공동 동결 대상이다. 이번 첨부를 기존 후보 enum 최종 수락이나 fixture 제작/코드 구현/배포 지시로 해석하지 않았다. 기록·검토서만 갱신, 테스트 재실행 없음.

## 9. 통합 계약 R2 draft-1 검토 — 2026-10-14

사용자 전달 lc-billing-paid-exam-contract-r2-draft-1 전체를 검토했다. 최신 단순화 규칙이 통합돼 공동 회신 요청1~3의 기술 내용에 추가 이견 없다. 구draft의 두문제후보/type/accessVersion/rejectedAt을 구현 기준으로 되돌리지 않는다. 이는 검토 의견이며 전체 계약의 최종 동결·fixture 실행·제품 구현/배포 완료가 아니다.

- startReason 세값(RESTART_INCOMPLETE/NEW_AFTER_SUBMISSION/NEW_AFTER_COMPLETION), 단일mock·서버고정/replay, source/kind/transition 조합을 오프라인fixture 기준으로 삼을 수 있다. NEW_AFTER_SUBMISSION/COMPLETION은 BillingOPEN/동일mock에도 신규권리 INITIAL이며 접수만으로 무료권이 생기지 않는다.
- 모든 전환의 공통 LC 마무리와 기접수/파생Summary 완료, Billing의 최초trial consumption당 복원판정, confirm 뒤 LC마무리 commit까지 operation복구를 유지하는 방향에 이견 없다.
- 불변eventId/group 양unique·동일ID동일digest204/내용충돌409/같은group다른ID409, 단일canonical SHA-256 및 terminalAt 재사용에 이견 없다.
- 기존 'startReason/transition/충돌 값 미정' 검토 항목은 이번 정본에서 구체화됐으므로 다시 명칭 선택을 요구하지 않는다. 실제 golden bytes/hash와 독립검증에 따른 최종 수락은 별도다.

잔여 담당: LC는 phone/삭제 접수증거의 존재·보존 및 신규writer/legacy manifest·claim/transfer/lifecycle·receiver namespace를 확인한다. Billing은 phone+paid 등 권리선택 정상경로와 retained 원group/session/owner 증거·실패receipt/금융보존을 확인한다. 양측은 지원조합/보존·삭제/replay gate와 실제 키/port/E2E를 동결한다. S15/V07 미확정 정상분기를 테스트 모형으로 통과 처리하지 않는다.

fixture 세부화 시 기존 범위 안에서 다음을 명시할 것을 권고한다: inbox TTL 뒤 group에 최초eventId/digest가 남아 있는 동일 재전송은204이며 새ID는409; 이전 시험의 늦은완료가 신규group guard를 해제하지 않음; 새source/사유로 권리부족이면 기존시험을 선종료하지 않음. 이는 추가 제품 정책이 아니라 현재 불변식의 회귀 사례다.

다음은 제작 범위 승인 후 새revision의 JSON/HTTP/token/golden bytes 제작·hash대조·독립검증이다. 이번에는 문서/작업기록만 갱신했으며 fixture 생성·테스트·코드/DB/배포는 수행하지 않았다.

## 10. 통합 R2 draft-2 재검토 — 2026-10-14

직전 통합 draft-1과 사용자 첨부 lc-billing-paid-exam-contract-r2-draft-2를 diff 대조했다. 권리부족 시 이전시험 선종료 금지, 늦은완료의 신규group/operation guard 불해제, inboxTTL 뒤 retained group 증거에 따른 동일재전송204/충돌409가 본문과 S09/S12/V01에 반영됐다. 담당분리 및 S15/V07 미확정 기대값을 꾸미지 않는 경계도 반영됐다. 이번 보완 범위 추가 이견 없음.

이제 1~3 기술 내용을 기준으로 승인된 범위의 오프라인fixture를 구체화할 수 있다. phone/삭제 정상분기·보존/replay/실환경 gate는 별도로 남는다. 새 정책·명칭 선택을 반복 요구하지 않는다. 이번 재검토는 fixture 제작 실행·양측 검증·전체 계약 최종동결·제품구현/배포 승인이 아니다. 코드/DB/테스트 변경 없음.
