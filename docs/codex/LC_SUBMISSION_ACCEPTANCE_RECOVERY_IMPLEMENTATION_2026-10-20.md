# 제출 접수 응답·후속 복구 최소 구현

- 일자: 2026-10-20
- 관련 Jira: TMI-118/TMI-125. 신규 Jira 없음.
- 상태: 로컬 구현·최종 회귀 통과. commit/push/배포 및 실DB 변경 없음.

## 1. 5줄 결론

1. `submitQuestion` 정상 반환 뒤 상태 정리의 알려진 transient 오류만 정상 접수 응답과 분리했다.
2. 기존 접수 증거와 복구 적격 Session 또는 명시 무료 출처를 확인하고 owner·삭제·공개 접근을 재검증한다.
3. 상태 정리는 기존 최대 3회 재시도, 이후 기존 worker/outbox를 재사용한다. 새 채점/과금 요청은 보내지 않는다.
4. worker는 `_id` keyset 순회로 실패/진행 중 앞 후보에 묶이지 않게 했다. DB 반환 개수도 batch로 제한한다.
5. 콜백·요약·unknown commit 성공 전환과 새 저장 구조는 추가하지 않았다. 공개 API는 불변이다.

## 2. 변경 파일과 동작

- `ExamServiceImpl`: submit 후처리만 신규 coordinator 진입점으로 연결. 정상 응답 DTO/status 그대로 유지.
- `AttemptGroupStateCoordinator`: 실패 분류, 기존 접수 증거 확인, 복구 적격/명시 무료 구분과 보호 재검증. 성공 경로에는 추가 읽기 없음.
- `UserOwnedTransactionExecutor`: 실패 뒤 읽기 전용 ACTIVE guard·삭제 fence 확인. 새 Transaction이나 writer fence 대체가 아님.
- `ExamSessionRepository`, `AttemptGroupStateReconciler`: 안정적인 `_id` 순회/페이지 크기 제한/끝에서 순환. 후보별 오류 격리 유지. 재시작 시 cursor만 초기화되고 DB 후보는 보존된다.
- 단위 테스트: `AttemptGroupSubmissionRecoveryTest`, `AttemptGroupStateReconcilerTest`, `ExamOwnershipServiceTest`, `DeferredExamCoordinationAccessTest`.
- 격리 Mongo 테스트: `AttemptGroupOwnerConflictMongoTest`에 durable 접수 증거→3회 소진→새 worker 복구→terminal/outbox 1건 및 keyset 조회 검증 추가.

## 3. 유지한 계약과 남은 경계

- URL/Method/DTO/BaseResponse/실제 반환 상태, AI user_id=examId, retryCount, S3/Redis Key 불변. 정상 접수는 채점 완료를 의미하지 않는다.
- 저장·dispatch·캐시 실패, unknown commit, 영구 오류, 단순 duplicate, 증거 없는 legacy, 보호 거절은 무조건 성공 처리하지 않는다.
- receipt/marker 증명과 worker 대상 조건이 모두 맞는 유료 시험만 지연 복구를 약속한다. 명시 TRANSITION_FREE_V1은 기존 검증된 출처를 사용하며 Billing 복구 대상이라고 주장하지 않는다.
- 이번 변경은 처음 발생한 10/19 Mongo 원인을 확정하지 않는다. 모든 종류의 500을 제거하는 변경이 아니다.
- 기존 1분 rate-limited 실패 경보와 고정 분류 DEBUG 지연 로그를 재사용한다. 별도 영속 지연시간/SLA 관측 체계는 추가하지 않았다.

## 4. 검증 및 배포 전 확인

- 로컬 unit·Mongo 전체 회귀를 실행한다. Docker 호환 옵션 `JAVA_TOOL_OPTIONS=-Dapi.version=1.44`은 실행 환경에만 지정하며 제품/CI 설정에 넣지 않는다.
- 격리 Mongo 재시작 검증은 새 coordinator/worker 인스턴스가 기존 DB 증거만으로 수렴하는 테스트다. 실제 ECS 종료/재배포 E2E나 전체 AI 제출 실험은 아니다.
- 운영 DB 조회나 index 변경 없이 기존 `_id_`로 조회 가능한 keyset을 사용했다. test 환경에서 실제 후보량·순회 지연·쿼리 비용, writer/publisher 설정과 owner/삭제 경합을 배포 전 확인해야 한다.
- 지속 재시작이나 DB 영구 장애 중 완료를 보장하지 않는다. DB 후보를 보존하고 정상 동작 재개 시 순회하도록 한다.
- 운영 증거 없는 과거 시험을 자동 보정하지 않는다. 예상 밖 사용자 파일(.DS_Store, 기존 기록/다른 문서)은 보존했다.

## 5. 최종 검증 결과 및 다음 작업

- `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew clean test mongoIntegrationTest`: 성공. 마지막 누락 Session 테스트 추가 후 `./gradlew test mongoIntegrationTest` 동일 옵션으로 재검증 성공(Mongo 태스크는 제품/통합 소스 변경 없어 UP-TO-DATE).
- 최종 XML: unit 1,428건(실패/오류 0, 기존 skip 1), Mongo 360건(실패/오류 0, 기존 skip 2). diff check 통과.
- 직접 검증: 3회 소진 후 정상 반환, 무료 출처, 누락/불명/영구/duplicate 거절, owner/삭제 재검증, 참여 Transaction 비억제, 제출 실패 전파, 성공 시 추가 조회 없음, worker 순회/재시작, 격리 Mongo의 durable 증거 기반 terminal/outbox 1건 수렴.
- 실제 HTTP 응답 유실·AI 전송·ECS 재기동·실제 Billing 전달 E2E는 수행하지 않았다. A01~A18 모든 실환경 시나리오 완료를 주장하지 않는다.
- 이번 변경은 제품 5파일·테스트 5파일 및 계획/보고서/기록이다. 기존 dirty 문서 및 사용자 .DS_Store 등은 보존했고 관련 없는 소스 변경은 없다.
- 다음 작업은 변경 리뷰 후 승인된 범위의 commit/push·test 배포 검증이다. 실제 후보량에서 keyset 쿼리 비용/순회 지연과 writer/publisher 활성 상태를 확인한 뒤 prod 전달을 결정한다. 이번 요청으로 배포하지 않았다.
