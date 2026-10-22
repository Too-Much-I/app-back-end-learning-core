# Callback write isolation (TMI-128)

## 5줄 결론

1. Callback의 공용 사용자 guard write를 시험별 CORE/AZURE write fence로 분리한다.
2. 인증·삭제·회원 통합·탈퇴·환불 제한, API/AI payload 및 완료 조건은 유지한다.
3. Callback entry와 persistence hook 모두 같은 scope를 사용한다.
4. lifecycle bridge 먼저 배포하고 구 task가 사라진 뒤 isolation을 켠다.
5. 같은 lane/결과 문서 경합은 여전히 가능하며 모든 Mongo112 해결을 보장하지 않는다.

## 반드시 읽을 내용

- `CALLBACK_WRITE_FENCES_LIFECYCLE_ENABLED=true`, `CALLBACK_WRITE_FENCES_ISOLATION_ENABLED=false`가 준비 단계다. 기존 owner writer가 필요하다.
- 시험별 두 lane을 삭제·통합·탈퇴·revoke Transaction에서 함께 touch한다. 콜백은 해당 lane만 갱신하면서 기존 상태 검사를 유지한다.
- 기동 때 `exam_callback_write_fences` 컬렉션을 준비한다. `_id` 유일 인덱스를 사용하며 기존 데이터 backfill은 필요 없다. 최초 upsert도 lifecycle 경합에 참여한다.
- 신규 문서는 lane+examId, revision, updatedAt만 보관한다. owner/음성/답안은 보관하지 않는다. TTL이나 자동 purge는 이번에 추가하지 않는다. 삭제 후 남는 최소 fence의 안전한 정리는 별도 retention 검증 과제다.

## 사용자 결정

- 테스트 후 운영 전달은 승인됐다. 별도 제품 정책 변경은 없다.

## 위험·미확인

- 정확한 기존 Mongo112 충돌 문서는 미확정이다. 공용 hot-document 경합을 줄이는 수정이며 전체 오류 제거 주장이 아니다.
- lifecycle은 해당 소유자 Session 목록의 모든 lane을 갱신한다. bulk 사용으로 왕복을 줄이지만 대규모 이력에서 Transaction 지연 관측이 필요하다.
- Mongo 격리 테스트와 실제 앱 제출 E2E는 구분한다. 실제 앱 검증 완료로 보고하지 않는다.

## 배포·복구

1. CI/격리 Mongo 회귀를 통과하고 test에 기존 main 수정과 함께 배포한다.
2. lifecycle=true/isolation=false 태스크로 전부 교체됐는지 ECS deployment/task 목록과 ALB health로 확인한다.
3. isolation=true로 교체하고 health/로그/설정 및 구 태스크 drain을 확인한다.
4. test 검증 후 prod에도 같은 두 단계를 적용한다. 기존 task definition과 이미지 digest를 기록한다.
5. 복구 시 먼저 isolation=false로 전부 교체한다. 격리 writer가 남아 있는 동안 lifecycle 없는 이전 코드로 직접 복귀하지 않는다.

ECS test/prod 조회 시점 설정은 desired=1, minimumHealthyPercent=100, maximumPercent=200, ROLLING, health grace=300초다. 기존 서비스를 먼저 중단하지 않으며 실제 새 task 용량/health와 drain을 확인해야 한다.

## 상세 근거

- `CallbackWriteFence`: scope 및 lane, lifecycle bulk barrier.
- `UserOwnershipGuardService`: scoped read-check+lane write; 일반 writer 경로 유지.
- `ExamServiceImpl`: Azure 별도 lane, 나머지 기존 callback CORE.
- `DeletionCommandService`, `DeletionWorker`, `UserMergedTransactionService`, `UserWithdrawnEventTransactionService`, `AccessRevocationStore`: lifecycle bridge.
- `CallbackWriteFenceMongoTest`, `DeletionFlowMongoTest`, `DeletionWiringMongoTest`: 저장 hook·동시 lane·삭제 경합·실제 wiring 검증.
