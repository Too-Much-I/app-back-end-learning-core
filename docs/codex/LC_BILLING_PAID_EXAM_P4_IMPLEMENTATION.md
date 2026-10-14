# P4 신규 접수 차단·기접수 작업 분리

- 날짜: 2026-10-14 / 브랜치 `feat/billing-paid-exam-p4`
- 관련 TMI-116/TMI-128/TMI-122·선행 TMI-218. 삭제 후속 TMI-193/TMI-136 및 공동 TMI-217은 별도다.

## 1. 5줄 결론

1. P3/P3.1·fixtures-2를 commit136602a/PR39로 전달하고 CI 통과 후 develop2512c1e에 머지했다.
2. 머지 결과에서 P4 브랜치를 만들고 기존 Job·receipt·완료 marker 기반 공통 접수 증거 검사를 구현했다.
3. 접수 차단 뒤 신규 문항/새 녹음은 금지하되 접수된 문항 Callback·복구와 전체 접수 후 파생 Summary는 허용한다.
4. S3 파일/채점 결과만으로 접수 사실을 만들지 않는다. 부분 접수는 결과가 있어도 전체 Summary/COMPLETED로 승격하지 않는다.
5. P4는 미커밋/미푸시/미배포다. P4.1 삭제 연결·P5/P6·공개 paid/자동 scheduler 활성화는 후속이다.

## 2. 사용자가 읽어야 하는 동작

| 경계 | 처리 |
| --- | --- |
| 확인 대기 새 Session | submit/retry/Callback/요약 처리 불가, 기존 공개 오류 사용 |
| 전환 후 원 Session 신규 submit/새 retryCount | 거절, 새 Job/receipt 없음 |
| 이미 접수된 Job의 같은 submit replay | 기존 결과/상태 재현, 새 접수 없음 |
| 기접수 문항 feedback/Azure/SpeechAce Callback | 현재 owner/삭제/terminal 검증 후 원 Session에 저장 |
| 누락 Job 복구 | 실제 접수 Job 표시 또는 retry0 receipt/전체접수 marker 필요; S3만으로 복구 불가 |
| 전체 접수 후 Summary 최초 생성/재시도/완료 | 전체접수 marker와 결과 완료·generation 검증 유지; 원 Session에서 처리 |
| 부분 접수 | 접수된 문항만 완료. Summary/전체 COMPLETED 금지 |
| 조회/Polling/기존 GET URL | 기존 소유권·삭제·DTO 계약 유지 |

`ExamAcceptedWorkPolicy`를 공통 판단으로 사용한다. 신규 coverage Session과 접수차단 Session에 durable 증거를 요구하며 기존 비coverage·미차단 legacy의 callback/복구 계약은 유지한다. boolean `activityUserSubmission`은 기존 실제 submit Job의 표시이고 시스템 복원 Job에는 새로 붙이지 않는다. retry0의 누락 Job은 기존 receipt 또는 Session 전체접수 marker로 확인한다. 완료 receipt가3일 TTL로 없어져도 전체접수 marker와 필수목록 snapshot은 남는다.

신규 collection/Session 문항목록 중복/새 key나 nonce를 추가하지 않았다. 원 Session의 채점 lifecycle을 ABANDONED로 바꾸지 않는다. 완료 event와 결과는 원 exam/group에 남고 새 operation/권리를 변경하지 않는다.

## 3. 결정·다음 단계

이번 P4 내부 구현에 추가 제품 결정은 없다. 다음은 P4.1 생성중 삭제 미접수/공개 오류 연결이며 TMI-217 삭제 계약 최신 보완과 정합성을 먼저 확인해야 한다. P4가 삭제 queue/ACK/신규 삭제 route 구현을 포함하지 않는다.

## 4. 위험 및 배포 전 확인

- 공개 paid route·자동 schema2 worker·paid 기동 차단 유지. 실환경 권리 전환/E2E·공동 상태28개 완료를 주장하지 않는다.
- 비coverage 과거 시험에 새 차단을 적용할 때의 legacy manifest/접수증거 coverage는 P6 전환 gate다. 증거 없는 Session을 무료권/완료로 추론하거나 자동 backfill하지 않는다.
- 기존 owner/삭제 Transaction과 terminal/deletion deny가 공통 증거보다 우선한다. 이번에 별도 delete/merge 예외나 재생성 경로는 만들지 않았다. 실제 다중 서비스 경합/owner 이전 이후 배포 검증은 남아 있다.
- P3 CI 첫 실행은 변경하지 않은 LearningQuotaTransactionTest 동시성 검사1개 실패, 같은 커밋 재실행은 성공했다. 해당 불안정성의 근본 수정은 이번 범위 밖이며 기록을 남긴다.
- P3 머지의 기존 테스트 자동 배포는 별도 실행이다. P4 로컬 코드가 배포됐다는 의미가 아니다. 최종 배포 관측은 CURRENT_STATE/WORKLOG 참조.

## 5. 변경 파일·유지 계약

- `ExamAcceptedWorkPolicy`: 문항/요약 처리 공통 증거 gate.
- `ExamSubmissionEvidence`: 기존 receipt/완료 marker 읽기 검사; 접수차단 이후 시각의 receipt 거절.
- `ExamGradingService`: 신규접수/재전송 구분, 누락 Job·문항 완료·요약 생성/완료·재시도 gate 및 Tx 내부 재검사.
- `ExamServiceImpl`: feedback/Azure/SpeechAce/summary 공통 Callback 검증.
- `SummaryDispatchScheduler`: 전체접수 증거 없는 요약 dispatch 거절.
- `AttemptGroupEvidenceEvaluator`: coverage 필수집합 snapshot 사용, 전체접수 없으면 GRADING/COMPLETED로 승격 금지.
- `ExamAcceptedWorkPolicyTest`, `ExamGradingServiceTest`, `FeedbackCallbackServiceTest`, `PaidExamP3MongoTest`: 정책/실서비스·Mongo 회귀.
- AGENTS/계획/ADR/현재상태/작업기록 갱신. 사용자 DS_Store와 동시 TMI-217 문서 기록 보존, 예상 밖 제품 변경 없음.

공개 URL/Method/Parameter/성공 DTO/BaseResponse, AI `user_id=examId`·Callback JSON, retryCount, S3 Object/Redis key, Billing wire 및 A/R2/fixtures-1/2 bytes 불변. Billing/Identity 저장소·실DB·기능 설정 변경 없음.

## 6. 실행 검증

- `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew clean test mongoIntegrationTest bootJar`, 테스트 fixture 연결 수정 후 `test mongoIntegrationTest bootJar`, 부정 테스트 강화 후 `./gradlew test`.
- 단위1,159개 실패/오류0·기존 조건부1 skip. Mongo212개 실패/오류/skip0. 이번 단위10개·Mongo4개 추가. bootJar 성공.
- Mongo: 접수와 finalize 동시경합/접수 선행·차단 선행, 부분접수/신규 Job rollback, 전체 marker의 receipt TTL 후 복구, 누락 Job의 원 receipt와 차단 이후 잘못된 receipt 거절. 기존 owner/deletion/replay 회귀도 전체 실행했다.
- 단위: 신규/기존 Job 구분·S3-only 복원 거절, 실제 Callback 원 Session 저장/무접수 저장 거절, 완료 결과가 있어도 전체접수 없는 Summary 금지, 접수 완료 후 Summary 최초생성/중복1개 및 terminal 우선 검증.
- 검증 중 가짜 Job 저장소의 activityUserSubmission 복사 누락과 직접 Mongo factory의 custom repository fragment 연결을 수정했다. 검사를 완화하지 않았다.
- Node migration 관련28개, 네 fixture 묶음34개 SHA256SUMS 및 diff check 통과. 실제 DB/AWS/Sentry 변경이나 AI 실호출 없음.
