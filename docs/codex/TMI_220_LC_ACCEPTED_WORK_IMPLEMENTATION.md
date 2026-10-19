# TMI-220 LC 과거 Session 기접수 보완 구현

- 날짜: 2026-10-14. 사용자 「보완 해줘」 승인, LC 코드·격리 검증 범위. 관련 TMI-220/217·LC TMI-116/128/122/125/193/136.
- 선행 [검토 기록](TMI_220_LC_ACCEPTED_WORK_REVIEW.md)은 당시 상태이며 현재 구현 상태는 이 보고서를 따른다.

## 1. 5줄 결론

1. 환불로 차단된 과거 Session도 confirm·owner·재시작 경계와 exact 접수 증거가 있으면 기존 작업만 처리한다.
2. 신규 submit/녹음/권리 사용 차단은 유지하며 기존 grading/retry는 근거가 있는 작업의 복구만 허용한다.
3. 부분 접수는 해당 문항만 허용한다. 전체접수 marker와 기존 채점 조건이 있어야 Summary·terminal로 진행한다.
4. 신규 내부 증거를 자동 backfill하지 않는다. 증거가 없는 기존 자료는 계속 fail-closed한다.
5. LC 로컬 검증과 Billing/공동 HTTP·경합 검증은 별개다. 배포·기능 활성화·commit/push는 수행하지 않았다.

## 2. 반드시 읽어야 하는 내용

- `AcceptedWorkClosure`는 기존 paid finalize Transaction이 이전 Session을 닫을 때 confirm snapshot·원 owner·source·group/reservation/creation operation·closing operation을 고정한다. `entitlementConfirmedUserId`는 paid 활성화 시 저장한다. owner가 달라졌거나 confirm 근거가 누락되면 예외를 만들지 않는다.
- revoke의 기존 원자적 projection에 event ID/owner를 함께 저장한다. 단일 `confirmedSessionId`·불변 event·wire·group별 차단 fact는 그대로다. remote occurredAt과 로컬 시각의 숫자 비교로 confirm/revoke 순서를 추정하지 않는다.
- `QuestionGradingJob.firstUserSubmittedAt`은 실제 새 접수에서 한 번만 기록한다. 시스템 복구의 `pendingAt` 변경과 분리하며 기존 boolean-only Job에 시각을 소급 채우지 않는다. exact exam/mock/question/retry/job 귀속 불일치는 receipt fallback도 거절한다.
- retry0은 기존 receipt 및 전체접수 marker를 재사용한다. 전체접수 후 receipt 3일 TTL이 지나도 marker/필수집합으로 증명할 수 있다. retry>0의 근거 없는 Job이나 부분접수 누락을 전체접수로 추론하지 않는다.
- Callback/채점 재시도/파생 Summary는 공통 policy를 사용한다. 기존 whole-exam grading retry의 owner 검사와 Transaction fence를 유지하며, 결과 누락 재개/최대시도 처리/dispatch 전에도 해당 작업 근거를 재확인한다.
- 과거 Session terminal은 전용 검증 경로를 사용한다. 일반 terminal 메서드의 revoke 거절을 제거하지 않는다. 기존 coordinator가 원 Session의 projection/outbox만 기록하며 새 Session/creation guard를 수정하지 않는다.

## 3. 결정 사항·위험 및 배포 조건

- 추가 제품 정책 선택은 없다. 기존 closure/confirmed-owner 증거가 없는 데이터의 자동 migration은 이 구현에 포함하지 않는다.
- 삭제/탈퇴/owner fence 및 확정 terminal이 예외보다 우선한다. 원 접수·차단 증거의 최종 보존/purge는 기존 P6.1/V07 gate를 유지한다.
- Billing의 과거 이벤트 수신 구현과 C05/H16 양측 동시성·실제 HTTP 순서/응답유실 검증은 미실행이다. LC 테스트를 공동 완료로 표시하지 않는다.
- 기존 paid rollout 차단·default-off를 유지한다. 실제 DB/index 변경, legacy 목록 적용, 키/ALB/TLS 설정 및 활성화 승인은 별도다.

## 4. 검증 범위

| 범위 | 이번 LC 검증 | 남은 범위 |
| --- | --- | --- |
| C01/C02 → H14 | exact 과거 Job·immutable 접수시각, 실제 Callback 저장, 실제 기존 grading retry, 신규녹음/미접수 복구 거절 | Billing 연결 HTTP/E2E |
| C03 → H15 | 부분접수 Summary 거절, 전체접수 파생 Summary, receipt 제거 후 marker 및 원 Session terminal | 양측 늦은 상태 이벤트 처리 |
| C04 → H16 | owner/terminal 음성 단위, 실제 Mongo 삭제 fence 우선 | 탈퇴/삭제/Callback 전체 양순서 경합 조합 |
| C05 → H16 | 실제 replacement→중복 revoke 후 S2/operation 불변, 과거 terminal 후 S2 불변 | 동시 outbox/수신 및 Billing current group/new guard 공동 검증 |
| C06 | confirm/owner/source/closure 누락·귀속 불일치, pendingAt 변경, revoke-before-close·same-close replay, marker TTL 경계 | 양측 장애 주입 전체 조합 |

`RevokedAcceptedWorkPolicyTest`, `ExamGradingServiceTest`, `FeedbackCallbackServiceTest`, `PaidExamP3MongoTest`에 회귀를 추가했다. Mongo는 격리 Testcontainers replica-set이며 실제 DB를 사용하지 않는다. 이 표는 공동 fixture 전체 실행 완료 표가 아니다. 원본 fixture bytes/hash는 변경하지 않는다.

최종 실행 결과는 WORKLOG/CURRENT_STATE에 기록한다. 공개 API/DTO/BaseResponse·AI `user_id=examId`/Callback·retryCount·S3/Redis·Billing wire 불변. 기존 P6 dirty 변경과 사용자 `.DS_Store`를 보존했고 예상 밖 제품 변경은 없다. Billing/Identity/Jira/배포 변경 및 외부 발송 없음.
