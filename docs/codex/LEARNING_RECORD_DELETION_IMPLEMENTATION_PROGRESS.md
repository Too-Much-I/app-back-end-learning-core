# TMI-193 학습 기록 삭제 구현 진행 상황

## 5줄 결론

1. 2026-10-03 후속 구현에서 삭제 command/status API, 조회·쓰기·Callback 보호, Mongo/S3/Redis worker와 일별 통계를 추가했다. 출시 검증 완료를 의미하지 않는다.
2. 안전 checkpoint 전에는 기존 기록을 숨기고 쓰기를 막으며, 이후에는 처음 확정한 ID만 정리하여 새 학습을 보존한다.
3. 기존 Billing saga와 최소 승계 증거를 재사용한다. Billing 서버는 기동하지 않았으며 미연결 기록은 원격 Billing 호출 없이 정리한다.
4. 단위 580개, 격리 Mongo 111개가 통과했다. 실제 AWS·모바일·Billing E2E와 Billing 증거의 최종 보존기간 연결은 남아 있다.
5. 모든 flag는 기본 OFF다. 기동 시 index·replica set·rollout 증거를 요구하며 배포·원격 DDL·실제 사용자 데이터 삭제·commit/push는 하지 않았다.

## 반드시 읽을 내용

- 관련 Jira: [TMI-193](https://to-teacher.atlassian.net/browse/TMI-193), 상위 TMI-136.
- `DeletionConfiguration`이 writer-fence ON에서 Store·command·fence를 등록한다. read/writer/Billing 증거 보호를 함께 요구하며 command ON에는 worker가 필요하다. command만 OFF로 내려도 status 조회와 기존 fence를 유지할 수 있다.
- `DeletionStartupValidator`는 replica set, 정확한 unique/TTL index, staging/prod JWT 및 별도 rollout manifest를 검사한다. migration이 승인 manifest를 대신 작성하지 않는다. **검증하지 않은 manifest를 true로 채워 기동을 우회하지 않는다.**
- worker는 대상 root의 소유자·SEALED/prepared 증거를 확인하고 원본 root 제거 뒤 checkpoint를 기록한다. S3·Redis는 transaction 밖에서 처리하고 lease token을 재확인한다. 완료 전 대상별 S3/Redis 재확인·Mongo 잔여 검사를 수행한다.
- 본 작업은 Billing 없이도 할 수 있다. 실제 Billing 동일 그룹 REPLACEMENT/무추가차감 검증과 활성화는 후속이다. Billing 미연결 기록에 가짜 그룹이나 이용권을 만들지 않는다.

## 사용자가 결정할 사항

- 제품 정책을 다시 결정할 필요는 없다. 승인 계획의 계정 유지·이용권 미복원·그룹 승계·비식별 집계 방향은 유지한다.
- 실제 배포·DB migration·feature 활성화는 별도 승인과 완성된 rollout 검증 후에만 수행한다.

## 주요 위험과 미확인 사항

- 기존 서비스 경계와 synchronous Mongo BeforeSaveCallback, repository CAS hook에 fence를 연결했다. UserMerged OFF/삭제 ON과 양쪽 writer ON 기동·쓰기 테스트를 포함한다. 실제 배포의 모든 구 writer drain·실제 Callback/장애 경합 E2E는 아직 미검증이다.
- command commit 응답 유실은 fresh primary-majority mapping/operation으로 복구한다. worker는 fresh token/version 재조회와 단계 재실행을 사용한다. 테스트는 commit 후 예외 주입을 포함하지만 실제 네트워크 장애로 Mongo commit ACK를 유실시키는 전체 failure-injection 검증은 남았다.
- `cycleNumber`는 기존 시험 생성 응답/배정 의미를 유지하기 위한 최소 coordination metadata로 함께 보존한다. 답안·결과·점수·음성 key·질문 snapshot은 보존하지 않는다.
- `AVAILABLE`/`CLAIMED` 소스가 여러 개이거나 현재 유효 Session과 공존하면 추측하지 않고 정합성 오류로 중단한다. `TRANSFERRED` 소스는 후보에서 제외되므로 정상 승계 후에는 새 Session을 기존 방식대로 사용한다. 모순된 데이터의 자동 repair는 없다.
- 완료 operation/command/target은 completedAt+30일 TTL을 전파한다. target 전파가 끝나기 전에는 operation TTL을 보류한다. Billing continuation은 authoritative group lifecycle 및 미전달 outbox 보존 검증이 남아 **자동 TTL을 설치하지 않는다**. 계정별 거래 증거 장기 보존을 일반 통계로 재사용하지 않는다.
- 일별 18개 조합은 원본 marker와 같은 transaction에서 증가한다. legacy 최초 event와 이미 완료된 Job의 재구성은 미수집으로 취급한다. 과거 backfill·전체 기존 기록의 통계 복원은 수행하지 않는다. 수집 시작 manifest와 최초 부분 일자를 운영자가 검증해야 한다.
- recoveryCycle 후속 진단/수정: Spring Data 4.4.2 BasicUpdate의 version 자동 증가가 $inc를 덮어쓰는 원인을 확인하고, 사용자 승인으로 custom repository의 일반 Update 빌더로 변경했다. CAS 기반 expected+1 저장은 missing/null legacy도 처리하며 삭제 fence·통계 hook·transaction을 유지한다. 새 통계는 계속 별도 durable 전이/sequence를 사용한다. 상세 `RECOVERY_CYCLE_DIAGNOSIS.md`.
- Redis 명령 timeout이 lease 내에 끝나도록 실제 연결 설정을 확인해야 한다. S3 API별 timeout은 5초, worker lease는 30초이며 version/delete marker와 partial delete 오류를 처리한다. 원격 IAM·versioning·Object Lock·backup/log 30일/보안90일 보존은 변경·검증하지 않았다.

## 이번 구현과 변경 동작

| 위치 | 구현한 내용 |
| --- | --- |
| `src/main/java/web/tosunsaeng/domain/learningrecorddeletion/domain/` | command hash, 상태/안전 checkpoint/완료 조건, target Callback seal, content-free Billing continuation 및 claim/transfer/release 규칙 |
| `learningrecorddeletion/api/` | DELETE command, GET status, allowlisted projection·고정 오류, no-body/query, same-key replay와 command OFF 시 status 유지 |
| `learningrecorddeletion/infrastructure/DeletedExamContinuationStore.java` | 같은 Mongo transaction에서 owner touch, sealed target/예약 확인, 최소 증거 insert와 원본 Session delete. 기존 생성 operation insert와 claim, 새 Session commit과 transfer를 원자 처리 |
| `exams/application/ExamSessionManager.java` | 기존 Session이 없는 경우 별도 보존 source로 기존 PreparedAssignment 생성; 모순 시 INITIAL 우회 금지 |
| `exams/application/BillingExamCreationSaga.java` | local 삭제 source가 있으면 phone discovery보다 우선, continuation 연결 시 transaction insert 경로 사용 |
| `exams/application/BillingExamCreationTransactionService.java` | operation insert/Session commit/CANCELED·EXPIRED 처리에 선택적 claim/transfer/release 참여 |
| `learningrecorddeletion/application/DeletionWorker.java` | bounded inventory·coordination 대기·target seal·root 제거·checkpoint·S3/Mongo/Redis 정리·최종 검증·완료 retention 전파 |
| `learningrecorddeletion/infrastructure/DeletionPersistenceFence.java` | 지원하는 원본/Job/결과/receipt 저장은 owner transaction을 요구하고 SEALED target 재생성을 차단 |
| `learningrecorddeletion/analytics/` | synchronous save callback와 repository CAS hook, 원본 counted marker/retry sequence 및 비식별 일별 `$inc` |
| `learningrecorddeletion/config/` | flag 조합 및 replica/index/JWT/rollout/coverage 기동 검증, 조건부 bean 등록, 기본 OFF |
| `scripts/mongodb/learning-record-deletion-prepare.js` | index/worker/완료 TTL/일별 unique와 root·child 조회 index, 명시 DB·writer drain·orphan/owner mismatch/위험 TTL preflight. 콘텐츠 삭제 없음 |

### 승계 처리의 안전 경계

- 보존은 `PREPARING_RESTART` deletion과 `SEALED` target, 참조 inventory, non-terminal creation operation 부재를 요구한다. 유효한 Billing metadata가 없는 Session을 가짜 continuation으로 변환하지 않는다.
- `GRADING` 또는 미확정 entitlement는 보존/Session 삭제를 거절한다. terminal Session은 기존 outbox의 source/group/target 일치를 추가 확인하고 outbox를 수정하지 않는다.
- `CLAIMED`를 소스 부재로 취급해 INITIAL로 우회하지 않는다. consumed source를 재사용할 수도 없다.
- 원본 Session 삭제와 증거 insert, 생성 operation insert와 source claim, 새 Session insert와 transfer는 각각 동일 DB transaction에 참여한다. 별도 transaction을 중첩하거나 외부 Billing HTTP를 그 안에서 호출하지 않는다.
- CANCELED/EXPIRED·REPLACEMENT·동일 그룹·precommit 및 Session 부재가 확인된 claim만 해제한다. timeout/lease 만료/FAILED_TERMINAL은 해제 근거가 아니다. 이미 TRANSFERRED된 source는 되돌리지 않는다.
- OPEN/unresolved source에는 TTL을 두지 않는다. 실제 group lifecycle 증거와 retention 연결은 후속 작업이다.

## 검증과 유지 계약

- 새 단위 테스트: command canonicalization/프라이버시, 단계 생략/target 확대 차단, checkpoint 누락, 정리 지연과 위험 격리 구분, PUT 유효기간 전 완료 거절, 신규 projection allowlist, 경로 범위 제한, claim/transfer/release와 activation 차단.
- 격리 Mongo replica-set 테스트: 보존/삭제 atomicity·rollback, 미확정 예약/미봉인 target 차단, 기존 Billing transaction 연결, 중복 operation insert rollback, 동시 claim의 단일 winner, 모순된 INITIAL 응답의 Session rollback, Session 존재 시 claim 해제 금지.
- 기존 공개 API URL/Method/Request/Response/BaseResponse, 시험 retryCount, AI `user_id=examId` 및 Callback JSON, S3/Redis key 형식은 변경하지 않았다.
- 검증 결과: `./gradlew clean test` 통과 후 최종 코드 기준 `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew test mongoIntegrationTest`에서 단위580개/Mongo111개, `node --test scripts/mongodb/*.test.js` 114개 통과. 실패/오류/skip0, `git diff --check` 통과. Docker API 옵션은 테스트 프로세스에만 적용했고 실제 Billing/AWS/모바일 E2E는 실행하지 않았다.
- 신규 검증은 API envelope/날짜/command OFF, storage partial/version/prefix/Redis unknown, API replay/unknown commit, orphan 차단, old-only cleanup·새 기록 보존, cleanup delayed와 위험 격리, 정상 11문항25회 증가·중복/rollback/reopen/legacy coverage·source 삭제 후 aggregate 보존을 포함한다.

## 다음 구현과 배포 전 확인

1. 실제 staging 구 writer drain과 migration dry-run/orphan 검토. 승인 전 apply/flags를 켜지 않는다.
2. Billing 실제 OPEN 승계·무추가차감·status-first 복구 및 group lifecycle 기반 continuation terminal retention 연결. Billing OFF라도 보존 source가 있으면 비과금 INITIAL로 우회하지 않는다.
3. 실제 AWS 권한·versioning·PUT 만료 후 sweep·Redis timeout·로그/backup 보존·모바일 캐시 세대 E2E.
4. 전체 동시성/네트워크 failure injection·성능 gate와 운영 알림 수신 검증. recoveryCycle CAS는 로컬 수정/회귀 완료, 실제 서버 검증은 배포 후 수행한다.
5. 검증된 rollout/통계 coverage manifest 작성 후 별도 승인으로 활성화한다. 상세 절차는 `LEARNING_RECORD_DELETION_RUNBOOK.md`, 모바일 계약은 `../contracts/learning-record-deletion-api.md`를 따른다.

이번 변경 외 기존 CURRENT_STATE/WORKLOG/배포 상태 문서와 회고 문서의 사용자 변경을 보존했다. 관련 없는 런타임 파일을 변경하지 않았으며 Jira 완료 처리도 하지 않았다.
