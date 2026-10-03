# TMI-193 삭제 기능 rollout·운영 절차

## 5줄 결론

1. 모든 flag 기본 OFF이며 이 작업에서 원격 설정/DB/IAM을 변경하지 않았다.
2. 먼저 구 writer drain, index/inventory dry-run, staging 동시성·저장소·모바일 검증을 끝낸다.
3. 완료 전 전체 사용자 조건으로 다시 삭제하지 않는다. sealed target ID가 유일한 물리 정리 범위다.
4. `CLEANUP_DELAYED`는 새 학습 허용, `NEEDS_REVIEW`는 차단·운영 확인이다. 임의로 writeBlocked를 해제하지 않는다.
5. Billing 최소 증거 terminal retention, 실제 E2E·성능/경보 수신은 남은 출시 조건이다. Jira 완료/운영 사용 가능 선언을 하지 않는다.

## 반드시 확인할 항목

- 전용 DB·bucket·Redis 연결과 replica-set transaction/majority 지원. 구 버전 writer가 남으면 새 fence를 우회할 수 있으므로 완전히 drain한다.
- `scripts/mongodb/learning-record-deletion-prepare.js`는 명시 `MONGODB_DATABASE`가 필요하다. 기본 dry-run이며 apply는 `LEARNING_RECORD_DELETION_PREPARE_APPLY=true`와 `LEARNING_RECORD_DELETION_WRITERS_DRAINED=true`를 모두 요구한다. URI/비밀번호를 문서·채팅·로그에 기록하지 않는다.
- migration은 active owner 중복, index 정의, 위험 TTL, root 없는 child·owner 불일치를 거절한다. 원본/결과를 자동 수정·삭제하지 않는다. 이미 삭제 작업이 진행 중이면 root가 먼저 제거된 정상 target도 orphan으로 보일 수 있으므로 신규 rollout 시 구 작업과 상태를 먼저 분류한다.
- 실행 역할은 해당 audio bucket의 `s3:GetBucketVersioning`, 제한된 `s3:ListBucket`/`s3:ListBucketVersions`, 해당 old prefix의 `s3:DeleteObject`/`s3:DeleteObjectVersion`이 필요하다. Object Lock/법적 보존/MFA delete는 별도 확인하고 우회하지 않는다. prefix 조건은 실제 IAM fixture로 검증한다.
- S3 versioning 상태·과거 version·delete marker, partial DeleteObjects 실패, 요청 이전 PUT로 재업로드, 6분 capability 대기 뒤 final sweep을 실제 staging에서 확인한다. catalog/shared audio는 정리 대상이 아니다.
- Redis 명령 timeout과 DB round trip이 lease 예산 안에 드는지 확인한다. DEL/EXISTS는 정확한 `exam:status:{examId}`만 사용한다. FLUSHDB/와일드카드 정리를 하지 않는다.

## 기동 증거와 flag

자동 승인/자동 DDL은 없다. 아래 manifest는 운영 검증 담당자가 **실제 확인한 경우에만** 별도 승인된 작업으로 작성한다. 미검증 상태에서 true를 넣어 기동을 우회하지 않는다.

- `learning_record_deletion_rollout`, `_id=v1`: `writersDrained`, `inventoryApproved`, `writerPathsVerified`; command ON 전에는 `storageVerified`, `mobileContractVerified`도 true여야 한다.
- 통계 ON 전 `learning_activity_collection_coverage`, `_id=v1`: `liveStartedAt`(실제 수집 시작 시각), `legacyPolicy=LEGACY_FIRST_EVENTS_UNCOVERED`. 최초 부분 KST 일자, 지표별 수집 coverage와 검증 범위를 부가 운영 문서에 남긴다. 사용자·시험 ID를 manifest에 넣지 않는다.
- read-fence/writer-fence/billing-continuation은 함께 준비한다. command ON은 worker ON을 요구한다. aggregate는 원본 transaction과 같은 경계에서 기록한다.
- 신규 접수 중단 시 command만 OFF로 내리고 기존 status/fence/worker는 유지한다. active 작업을 둔 채 전체 flags OFF나 구 binary로 rollback하지 않는다.
- staging/prod에서 JWT가 아닌 인증 모드는 기동을 거절한다. Identity/Billing 서버의 설정은 변경하지 않는다.

## worker·보존

- 전용 thread 2개, 각각 poll 10초/최대10회 claim. S3는 한 번에 최대1000개, Mongo child는 최대100개씩만 처리한다. Mongo 처리와 S3/Redis 호출은 분리한다.
- lease 30초, S3 개별 호출 timeout 5초. token/version 확인을 통과한 작업만 진척도를 저장한다. unknown commit에서 이전 mutable 문서를 재사용하지 않는다.
- 일시 오류는 5초→15초→1분→5분→15분 + 양의 jitter로 재시도한다. retry 200회 또는 24시간 경과 시 안전성에 따라 지연/격리로 분류한다. 성공한 inventory/삭제 batch 수를 실패 budget으로 쓰지 않는다.
- 정상 완료 operation/command/target은 완료 후30일이다. target retention 전파 완료 전에 operation TTL을 보류하여 target을 고아로 남기지 않는다. active/review에는 TTL이 없다.
- Billing continuation은 미해결/OPEN 동안 무TTL이며 terminal 및 outbox 보존 증거의 실제 연동이 남아 자동 purge하지 않는다. 이는 영구 보관 정책 승인이 아니라 rollout 미완료 항목이다.
- 일별 aggregate에는 사용자·시험·event ID가 없고 TTL이 없다. 원본 marker는 원본 삭제와 함께 제거하고 최종 집계를 차감하지 않는다. legacy/coverage 밖 문서 부재를 0으로 표시하지 않는다.
- 일반 로그/backup30일·보안 감사90일은 실제 인프라 설정과 개인정보 안내문으로 별도 검증한다. live 삭제가 backup 즉시 삭제를 뜻하지 않는다.

## NEEDS_REVIEW 대응

1. command 추가 접수는 해당 사용자 active guard가 막는다. 원본을 재노출하거나 같은 사용자 새 deletion을 만들지 않는다.
2. 제한된 운영 접근으로 operation의 status/stage/lastFailureCategory/retryCount/lease/target count와 참조 증거를 조회한다. 일반 로그/메트릭에는 사용자·시험·음성·provider 원문을 복사하지 않는다.
3. target count·소유자·root/child 참조, Billing operation status/Session commit/tombstone/outbox, Mongo majority commit 증거를 확인한다. timeout·lease 만료·404만으로 terminal을 추측하지 않는다.
4. sealed target에 신규 기록이 섞였거나 Billing 연결이 모순되면 관련 worker를 중지하고 증거를 보존한다. `deleteMany(userId)`·activeGuard/TTL 임의 해제·이용권 재지급은 금지한다.
5. 안전한 복구 predicate/범위와 rollback 검증을 별도 승인한 뒤 동일 operation/target으로 복구한다. 자동 repair/admin API는 제공하지 않는다.
6. `CLEANUP_DELAYED`라면 저장소/권한/timeout 문제를 복구하고 기존 worker의 자동 재시도를 관찰한다. 새 학습은 허용하되 추가 전체 삭제는 이전 완료까지 막는다.

관측은 worker의 sanitized temporary-failure counter 및 DB 상태별 개수/가장 오래된 요청을 사용한다. NEEDS_REVIEW/24시간 초과를 운영 알림에 연결하고 실제 수신을 검증해야 한다. 이 작업에서는 Sentry/AWS 알림 규칙이나 반복 모니터를 생성하지 않았다.

## 미확인·다음 검증

- 실제 Billing OPEN 동일 그룹 승계/무추가차감, CANCELED/EXPIRED/confirm 유실과 불명 commit E2E.
- 전체 old writer drain, Callback·UserMerged·새 학습과 삭제 경합, 네트워크 failure injection, 대량 inventory와 hot aggregate 부하.
- recoveryCycle 증가 유실은 custom repository의 일반 Update 빌더로 로컬 수정했다. CAS/transaction, null legacy, 삭제 fence·통계 hook 회귀를 포함하며 `RECOVERY_CYCLE_DIAGNOSIS.md` 참조. 원격 배포/실제 서버 검증은 수행하지 않았다.
- 모바일 이중 확인·동일 key 재전송·삭제 전 늦은 응답 폐기·checkpoint 후 새 cache 보존.

실행한 로컬 검증과 변경 파일은 `LEARNING_RECORD_DELETION_IMPLEMENTATION_PROGRESS.md` 및 WORKLOG를 참고한다.
