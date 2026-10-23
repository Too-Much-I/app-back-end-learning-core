# 채점 보호 문서 쓰기 제거

## 결론

- 확인된 운영 retry 실패는 user_ownership_guards revision 갱신의 Mongo112다.
- 모의고사 Job 변경·콜백에서 공용/시험별 보호 문서 쓰기를 읽기 검사로 전환한다.
- 이미 결과와 Job이 완료된 문항은 retry에서 쓰기 Transaction을 생략한다.
- 일반 writer 및 삭제/통합/탈퇴 자체의 보호 쓰기는 유지한다.
- 사용자 승인으로 상태 확인 이후 lifecycle 변경에 따른 늦은 저장 위험을 수용한다.

## 동작과 범위

UserOwnershipGuardService.gradingReadChecks는 스레드 한정 중첩 scope다. touchActive는 이 범위에서 ACTIVE/merged/withdrawn 상태만 조회하며 owner guard 생성·revision 변경·callback lane 갱신을 하지 않는다. finally에서 이전 scope를 복원한다. 신규 설정은 없다.

ExamGradingService의 Job Transaction과 ExamServiceImpl의 Azure/SpeechAce/Feedback callback은 executeGrading을 사용한다. Feedback/전체 요약 진입에는 동일 scope를 적용하여 AttemptGroup 요약 완료·저장 hook·후속 조정도 공용 guard를 갱신하지 않는다. DeletionPersistenceFence의 sealed/owner/삭제 상태 검사는 그대로 실행한다. 공개 권한, 접수 증거, 환불 보호 Session 판단, 결과와 Job 실제 저장·조건부 갱신·중복방지는 유지한다.

신규 시험 생성, Billing 예약, Challenge/별도 학습 및 lifecycle writer를 전역으로 끄지 않는다. 기존 callback fence collection·설정·레거시 executor는 이전 코드와 lifecycle 경로 호환을 위해 보존한다. 신규 기능 ON이나 DB migration은 필요 없다.

## 수용한 위험

읽기는 변경과 commit을 직렬화하지 않는다. 삭제/탈퇴/통합/환불 상태 확인 직후 상태가 바뀌면 처리 중 요청이 늦게 저장될 수 있다. 격리 Mongo 테스트는 삭제 후 late insert 가능성을 명시적으로 재현한다. 자동 재삭제는 보장하지 않으며 같은 Job·Session·결과에 대한 실제 DB 경합은 남는다. 환불 전 접수된 보호 Session 완료는 원래 허용 정책이다.

## 검증·배포

단위 테스트: 완료 Job retry 쓰기 생략, 기존 처리중/시간초과/상한 및 권한 회귀.
Mongo 테스트: 진입/저장 hook의 owner·lane 불변, 다른 owner touch와 동시 저장, SEALED/withdrawn/merged 거절, scope 예외 복원, 승인된 late insert.
전체 clean test, mongoIntegrationTest, Node deployment/migration 검증 후 develop 테스트 배포→main 운영 배포. 실제 앱 E2E는 별도이며 서버 health만으로 기능 성공을 주장하지 않는다.

문제 시 직전 이미지로 롤백할 수 있으나 이미 늦게 저장된 데이터가 자동 복구/정리되지는 않는다. 보호 설정을 끄거나 기존 fence를 삭제하지 않는다.
