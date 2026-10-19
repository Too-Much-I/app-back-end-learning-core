# recoveryCycle 증가 누락 진단 (2026-10-03)

관련: TMI-193 / 상위 TMI-136. 최초 진단 후 사용자 승인으로 로컬 수정 완료, 배포 없음.

## 후속 수정 결과

- `QuestionGradingJobRecoveryImpl` custom repository fragment로 변경해 일반 Update 빌더를 사용한다. Mongo CAS query는 기존 `_id`/COMPLETED/`$ifNull(recoveryCycle, 0)`/expected cycle 비교를 유지한다.
- cycle은 CAS로 검증한 expected+1을 `$set`하고 version은 `$inc`한다. 기존 명시 null 값에 `$inc`하면 Mongo 오류가 나므로, missing/null을 모두 0→1로 처리하는 안전한 successor를 사용한다. 정수 overflow는 `Math.addExact`로 거절한다.
- 호출자 transaction을 그대로 사용하며 새 transaction은 만들지 않는다. reset 필드/수정 건수 반환/외부 API 계약을 유지한다.
- 통계 repository hook에 fragment 메서드를 명시적으로 포함해 삭제 fence와 raw transition 기록을 유지한다.
- 실제 Mongo 회귀에 0→1→2, missing/null→1, CAS loser, 두 요청 동시 경쟁, 오래된 failure claim 차단, version/reset, rollback을 추가했다. Boot repository 자동 탐색에서도 fragment 실행을 확인하고 통계 중복 방지·fence 거절·통계 rollback을 검증한다.
- 아래 원인/대조 항목은 수정 전 진단 기록이다. 최종 검증 수치는 WORKLOG/CURRENT_STATE 참조.

## 원인

현재 Spring Data MongoDB 4.4.2의 문자열 `@Update`와 `@Version` 조합이다.

1. `AbstractMongoQuery.createUpdate`는 annotation JSON을 `BasicUpdate`로 만든다.
2. `BasicUpdate`는 별도 Document에 명령을 보관하지만, 상속한 `Update.modifies`는 `keysToUpdate`만 확인한다. 따라서 JSON에 version 증가가 있어도 `modifies("version")`는 false다.
3. `QueryOperations.increaseVersionForUpdateIfNecessary`가 version 증가를 추가한다.
4. `BasicUpdate.inc`는 `$inc`의 기존 항목과 병합하지 않고 단일 항목 Map으로 교체한다. recoveryCycle 증가가 사라진다.

repository의 의도는 `$inc: {recoveryCycle: 1, version: 1}`이나 driver CommandListener에서 관측한 실제 전송은 `$inc: {version: 1}`이었다. status는 PENDING으로 바뀌고 update count=1이지만 recoveryCycle=0이다.

## 재현과 대조

- 격리 Testcontainers Mongo 7.0.14, 새 MongoClient/MongoTemplate/MongoRepositoryFactory를 사용했다. 통계 callback/hook, 삭제 fence, Spring application 설정은 설치하지 않았다.
- 최초 기대값1 assertion은 실제0으로 실패했다. 이는 진단 재현이며 전체 테스트 성공으로 계산하지 않는다.
- 이어 실제0을 확인한 뒤 일반 `new Update().inc("recoveryCycle", 1).inc("version", 1)`을 동일 엔티티에 적용한 대조 테스트는 값1을 확인하며 통과했다(1 test).
- 테스트용 임시 파일은 진단 후 제거했다. 제품 테스트/코드의 기대값을 완화하지 않았다. 이번 턴 전체 회귀는 실행하지 않았다.
- 의존성 sources.jar에서 위 네 단계 구현을 직접 확인했다.

## 영향과 권장 수정

로컬 통계 fixture만의 문제가 아니다. 같은 repository/의존성의 실제 실행 경로에도 적용된다. 단, 원격 서비스의 실제 발생 여부나 배포 버전은 이번에 확인하지 않았다.

`ExamGradingService.reopenCompletedMissingResult`는 다시 읽은 값이 기존 cycle+1인지 확인한다. 증가 누락이면 재전송하지 않고 waiting으로 반환한다. dispatchAttempt가 초기화되고 cycle이 재사용되므로 이후 같은 dispatchAttempt를 얻는 경우 오래된 실패 처리와 새 시도를 구분하는 fence도 약해질 수 있다.

`QuestionGradingJobRepository.reopenCompletedMissingResult`를 custom repository/MongoTemplate의 일반 Update 빌더 방식으로 변경하는 것을 권장한다. 기존 `_id`/COMPLETED/expectedRecoveryCycle CAS 조건, null cycle의 0 처리, reset 필드, version 증가, owner transaction 및 통계·삭제 repository hook 연결을 모두 유지해야 한다. 메서드 annotation 제거 시 hook이 누락되지 않도록 명시적인 전이 기록 경로도 검토해야 한다. @Version 제거 또는 annotation에서 version만 삭제하는 것은 해결책이 아니다.

수정 후 실제 Mongo에서 0→1→2, null→1, CAS loser, old claim 실패 차단, 재전송·통계 once 및 transaction rollback을 검증한다. 신규 통계는 durable 상태 전이와 별도 retrySequence에 의존하므로 이번 counter 결함과 분리돼 있지만, 복구 동작 자체는 수정이 필요하다.
