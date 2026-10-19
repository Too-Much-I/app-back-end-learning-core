# LC 유료 시험 P4.1 — 생성 중 삭제 미접수·오류 응답

- 날짜: 2026-10-14
- 범위: 사용자 「어 구현해줘」, `feat/billing-paid-exam-p4`. P4 변경 보존, commit/push/배포/활성화 없음.
- 관련: TMI-116/TMI-128/TMI-122, TMI-193/TMI-136, 공동 삭제 계약 TMI-217.

## 1. 5줄 결론

1. 생성이 진행 중이면 기존 삭제 API에서409로 거절하며 삭제 command/operation/target 또는 알림 suppression을 만들지 않는다.
2. 생성 준비와 삭제 접수는 같은 owner guard를 쓰는 Transaction으로 순서화한다. Session ACTIVE 여부로 생성완료를 판단하지 않는다.
3. 삭제가 먼저면 기존 신규 생성 차단을 유지한다. 이미 접수된 삭제의 동일 key는 기존 결과를 재현한다.
4. 삭제로 숨겨졌거나 물리 삭제된 성공 Session replay는404이며 기존 금융성공/operation을 취소하거나 재생성하지 않는다.
5. 신규 Billing 삭제 sender/ACK·owner 증거·purge는 이번에 연결하지 않았다. 공개 paid route/자동 schema2 scheduler·paid 기동 차단은 유지한다.

## 2. 반드시 읽어야 하는 내용

| 경계 | 구현 결과 |
| --- | --- |
| 생성 중 삭제 | 409 `EXAM_CREATION_PROCESSING`, “시험 준비 중입니다. 잠시 후 다시 시도해 주세요.”, `Retry-After: 1` |
| 일반 생성 중 | 기존409/“시험 생성이 처리 중입니다. 같은 요청으로 다시 시도해 주세요.” 유지 |
| MEMBER 대기 | 기존503 `BILLING_TEMPORARILY_UNAVAILABLE`/기존 문구/Retry-After1 유지 |
| 삭제 선행 | 기존 `LEARNING_RECORD_DELETION_IN_PROGRESS` 유지 |
| 삭제된 성공 replay | 404 `EXAM_4004`, “해당 모의고사 세션을 찾을 수 없습니다.”, Retry-After 없음 |

오류 BaseResponse는 기존 isSuccess/code/message이며 null result는 생략한다. 내부 retryable/correlationId/userId를 추가하지 않는다. 공개 URL/Method/body/성공 DTO, AI user_id=examId/Callback, retryCount/S3/Redis, Billing wire와 fixture bytes는 불변이다.

## 3. 결정·후속 확인 사항

이번 승인 범위의 추가 제품 선택은 없다. 다음 구현 전에 TMI-217 신규 삭제 계약의 exact body/audience/owner 증거/보존 수락과 fixture를 확인해야 한다. Billing handoff draft-3의48개 항목은 계획이며 실행 통과가 아니다. 단일 Guest→Member UserMerged 증거 검토와 phone 재가입은 구분한다. 범용 owner 계보를 만들지 않는다.

## 4. 위험과 배포 gate

- 기존 삭제 worker의 원문정리/ACK/새학습 checkpoint는 여전히 이전 구현이다. 이번 미접수409만으로 신규 Billing 연계 삭제가 완성됐다고 보지 않는다.
- 후속 설계: 원문 제거 전에 최소 불변 삭제 fact/eventId 보존 → 원문 cleanup과 Billing 전달을 독립 진행 → 필요한 ACK 확인 전 신규 시험 차단. ACK 지연은 개인정보 정리를 무한 대기시키지 않는다. 기존 continuation claim/transfer를 명시 삭제 전달로 재사용하지 않는다.
- checkpoint의 실제 field·sender/route/auth·owner/legacy coverage·최종 purge는 공동 수락 후 구현한다. 기존24시간/200회 격리는120일 보존과 별개다.
- 실DB/index·Billing/Identity 통합·E2E·기능 설정은 미실행. legacy P6/배포 gate 및 실제 index/query 성능 확인이 남아 있다.
- 기존 사용자 DS_Store·P4 변경·동시 TMI-217 문서 기록을 보존했다. 이번 범위 밖 제품 변경 없음.

## 5. 구현 근거

- `DeletionCommandService`: owner touch 및 기존 command replay 뒤 생성 activeGuard 또는 PREPARED/RESERVED/SESSION_COMMITTED/CANCEL_PENDING을 확인한다. 불일치 guard=false인 nonterminal도 보수적으로 거절한다. 알림 suppression과 삭제 insert 이전이며 rollback으로 row0 유지.
- `DeletionExceptionAdvice`: 삭제용 문구/Retry-After만 추가. 일반 시험 생성 오류 enum의 문구는 변경하지 않는다.
- `PaidExamLifecycleService.replay`: 공유 owner Transaction에서 actor·withdrawal·merge를 먼저 검증한다. terminal success의 Session 부재/삭제 hidden을404로 처리하고 정상 snapshot 검증을 유지한다. 이 경로는 Session/operation 변경·Billing HTTP를 수행하지 않으며 finalize의 삭제 deny 예외를 만들지 않는다.
- 테스트: `DeletionApiTest`, `PaidExamPublicErrorTest`, `PaidExamP1MongoTest`, `PaidExamP3MongoTest`. 실제 생성/삭제 서비스와 격리 Mongo replica set을 사용하며 공개 오류 직렬화 검증은 공개 paid 연결 완료를 의미하지 않는다.

## 6. 검증 결과

- 집중 삭제 API·P1/P3 Mongo 테스트 성공.
- `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew clean test mongoIntegrationTest bootJar` 성공. 추가 부정 테스트 후 `test mongoIntegrationTest bootJar` 재실행 성공. 최종 XML: 단위1,164개 실패/오류0·기존1 skip, Mongo224개 실패/오류/skip0. 이번 단위5·Mongo12개 추가.
- `node --test scripts/mongodb/*.test.js`:121개 통과.
- 기존 네 fixture 묶음 SHA256SUMS34항목 일치, 원본 수정 없음.
- `git diff --check` 통과. 기존 deprecation/unchecked 경고는 무관한 일괄 수정 없이 유지했다.
