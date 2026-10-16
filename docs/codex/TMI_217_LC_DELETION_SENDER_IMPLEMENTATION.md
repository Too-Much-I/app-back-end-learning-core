# TMI-217 LC 삭제 전달·204 checkpoint 구현

- 날짜: 2026-10-16 / 기준: [동결 계약](TMI_217_DELETION_EXACT_R2_ACCEPTANCE.md), draft-3 > draft-2 > draft-1.
- 상태: LC 발신·저장·cleanup/ACK 분리 구현. 공동 E2E·전체31개 검증·배포/활성화 완료 아님.

## 1. 5줄 결론

1. 삭제 전용 workload audience와 매 시도 신규 JWT, HTTPS origin/redirect 금지·5초 timeout/16KiB 응답 상한을 추가했다.
2. 기존 target에7필드 payload·event/digest·lease/attempt/floor/전달상태를 저장하고 유효204를 동일version/lease에만 ACKED로 반영한다.
3. linked 원문 정리는 전달 대기/격리와 독립 진행하고 모든 필요한 ACKED 및 기존 안전조건을 확인한 owner Transaction에서 새 학습 checkpoint를 기록한다.
4. linked operation/target/command는 기존30일TTL 대상에서 제외하고 전체완료시 최초completedAt+120일을 검토시각으로 저장한다. purger 없음.
5. 기본OFF·기존 rollout gate 유지. 삭제 후 새 INITIAL의 기존 continuation 연결 및 실제 양측 HTTP/경합/E2E는 별도 잔여 작업이다.

## 2. 반드시 읽어야 하는 내용

같은 전송은 최초 payload/event/digest를 재사용하고 새 삭제 operation은 새 event를 생성한다. payload는 Mongo의7필드 document이며 HTTP에는 고정순서 canonical을 사용한다. 실제 LC Session ID의 기존 path-safe 규칙을 유지하며 임의 외부 Session 문자열을 새로 허용하지 않는다.

200번째 시도의 유효성공은 저장하되201번째 발신은 없다. 최초claim+24시간 정각부터 다음 발신을 거절하고 응답 시각만 기한을 넘은 성공은 유효 lease/version이면 저장한다. Retry-After가 invalid/누락이어도 기존 floor를 보존한다. expired claim은 소진된 attempt로 backoff한 뒤 재시도한다. 원시각/attempt 자동reset과 수동재개 API는 없다.

HTTP는 Mongo Transaction 밖이다. claim/ACK에는 기존 owner guard와 target version을 사용한다. cleanup이 target version을 변경한 동안 도착한 응답은 ACK로 저장하지 않는다. unknown commit은 성공으로 추정하지 않고 원전송의 durable 상태/lease로 수렴한다. 새 사용자 입력 권한을 delivery 자체가 생성하지 않는다.

## 3. 설정·운영 gate

- 신규 `app.billing.exam-deletion.sender-enabled` / `BILLING_EXAM_DELETION_SENDER_ENABLED`, 기본false.
- origin은 기존 BillingSagaProperties base-url, signing/environment는 기존 workload-jwt 설정을 재사용한다. 새 audience는 `tosunsaeng-billing:exam-deletion-events:{env}`다. S3 AWS provider는 변경하지 않았다.
- ON에는 workload sender·삭제 writer fence/worker 및 기존 DeletionStartupValidator가 필요하다. rollout `v1.billingDeliveryVerified=true`와 아래 nonunique/nonpartial/nonTTL 인덱스·linked target expiresAt 부재를 읽기 확인한다. 이 검사는 migration 또는 운영승인 대신이 아니다.
- 필요한 인덱스 key: `{prepared:1, "billingDelivery.state":1, "billingDelivery.nextAttemptAt":1, _id:1}` on `learning_record_deletion_targets`. 실제 설치/DDL 도구 실행은 하지 않았다. 기존 `_id`/target version을 유지하며 전역Session unique나 새 collection은 없다.
- linked target 생성에는 신뢰할 수 있는 workload environment가 필요하다. sender OFF를 ACKED/UNLINKED로 해석하지 않는다. 기존 partially prepared/TTL 자료와 old reader/writer를 inventory하고 drain한 뒤 승인된 migration/rollback 검증을 해야 한다.

## 4. 남은 위험·미확인 사항

- 실제 Billing 서버와 연결한 HTTP/TLS·프록시·양측 Tx 경합은 미실행이다. 전송 테스트의 mock204는 Billing 실제commit 증거가 아니다. Billing1,749개는 전달보고이며 LC 결과와 합산하지 않는다.
- 기존 ExamSessionManager/DeletedExamContinuationStore는 삭제 continuation의 REPLACEMENT/원group 전제를 가진다. Billing의 삭제후 새INITIAL 처리와 신규paid 생성 runtime 연결은 별도 완료가 필요하다. 이번 발신·새학습 checkpoint만으로 전체 유료/삭제 rollout 준비 완료라고 판단하면 안 된다.
- 전체31개 R/H/C를 모두 실행한 것으로 표시하지 않는다. 실제 HTTP parser/네트워크·동시 sender/cleanup/owner/withdrawal 및 재개 운영 절차 검증이 남는다. 기존 객체/필드 불일치가 있으면 성공을 추정하지 않는다.
- 응답 framing의 empty204/Content-Length/Transfer-Encoding 모순을 거절하지만 실제 intermediary가 잘못된body를 제거하는 동작까지 본 단위테스트가 증명하지 않는다.
- 개인정보 purge·수동재개·legacy backfill은 구현하지 않았다. linked 상세의120일 도달은 자동삭제나 운영 차단 해제 시점이 아니다.

## 5. 테스트·변경 범위

실행: `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew clean test mongoIntegrationTest bootJar`. 격리 Testcontainers Mongo만 사용한다. 최종 수치는 CURRENT_STATE/WORKLOG에 기록한다.

추가 단위테스트26개: immutable payload/독립event, deadline/200회/floor, HTTP응답분류·503 strict/opaque correlation/중복/트레일링·응답상한·헤더 overflow. 기존 실제 서명 JWT 검증은 세 purpose 모두 실행하도록 확장했다.

추가 Mongo4개: delivery격리후31일에도 cleanup/증거유지, persisted204 checkpoint/전체완료120일검토, cleanup version경합후same-payload 복구, senderOFF linked gate. 기존 삭제/공개/AI/paid 테스트는 전체 회귀에 포함한다.

기존 공개 URL/Method/DTO/BaseResponse·AI user_id=examId·retryCount·S3/Redis key는 불변이다. 이번 변경은 아래 파일/설정/테스트/문서에 한정한다. 기존 P6.1 dirty·사용자 DS_Store는 보존했고 무관한 수정은 하지 않았다. Billing/Identity·Jira·실DB/환경설정·배포·commit/push는 변경하지 않았다.

## 6. 부록 — 주요 파일

- [BillingDeletionDelivery](../../src/main/java/web/tosunsaeng/domain/learningrecorddeletion/domain/BillingDeletionDelivery.java): payload document·budget/floor/상태.
- [BillingDeletionClient](../../src/main/java/web/tosunsaeng/domain/learningrecorddeletion/infrastructure/BillingDeletionClient.java): HTTPS·bounded응답/오류.
- [BillingDeletionSender](../../src/main/java/web/tosunsaeng/domain/learningrecorddeletion/application/BillingDeletionSender.java), [Scheduler](../../src/main/java/web/tosunsaeng/domain/learningrecorddeletion/application/BillingDeletionScheduler.java): claim/send/ACK 및 fixed-delay5초·최대20건순차.
- [DeletionWorker](../../src/main/java/web/tosunsaeng/domain/learningrecorddeletion/application/DeletionWorker.java), DeletionTarget/DeletionOperation: 원문제거 전payload 원자저장·cleanup/checkpoint/retention 분리.
- [BillingDeletionConfiguration](../../src/main/java/web/tosunsaeng/domain/learningrecorddeletion/config/BillingDeletionConfiguration.java), DeletionConfiguration/application.yml: defaultOFF 및 기동검증.
- BillingWorkloadTokenIssuer 및 해당 테스트: 전용purpose 추가. AGENTS에 이번 승인범위 기록.
