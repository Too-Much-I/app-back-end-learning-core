# LC 운영 배포 기록 — 2026-10-18

## 5줄 결론

1. 사용자 승인으로 기존 LC 서버를 중단하고 기존 시험 1,748건의 exact 목록을 봉인했다. 기존 시험·답안 원문은 변경하지 않았다.
2. 새 운영 task `tosunsaeng-learning-core-prod:10`을 배포했다. running1/pending0, 공개 헬스 `200 UP`, ALB8080/8444 모두 healthy 확인. 이전 태스크의 ALB draining 정리는 진행 중이다.
3. **구앱 v1 무료 생성 ON**, 새 앱 v2 Billing 필수 경로 ON이다. 새 경로 실패의 무료 fallback은 없다.
4. 삭제 인덱스(승인 TTL3개 포함)·전달 인덱스·readiness를 준비하고, 승인된 제한 환불8444 전달과 나머지404 차단을 연결했다.
5. 실제 생성·제출·삭제·환불 E2E는 배포 후 프론트/양측 검증 대상이다. 완료했다고 기록하지 않았다.

## 반드시 알아둘 사항

- 봉인 이후 `prod:8` 재기동 금지. 기존 목록에 없는 무료 시험은 새 공존 코드의 명시 출처로 생성해야 한다.
- 삭제 `billingDeliveryVerified=false`를 유지한다. `storageVerified=true`의 근거는 테스트 검증 및 운영 bucket/Redis/IAM 준비이며 실제 운영 삭제 성공이 아니다.
- 답안학습 writer/grading 및 삭제 aggregate는 기존 OFF 유지. 기존 공개 API/DTO·AI user_id=examId·S3/Redis 계약 불변.
- 기존8080 경로와 다른 서비스 규칙 보존. 이번 Git commit/push 없음. 사용자 DS_Store와 기존 dirty 보존.

## 적용 근거

- 이미지 태그: `prod-frontcheck-3cde8dd`.
- 원격 digest: `sha256:02e658f3ad7b878b9889f47941c3a6faba341216de8cd6b2a2877078a5fa0c4b`.
- JAR SHA256: `3cde8dd70f2656df001d4aede6e04038a8da9a24036efb85f50490ad48ae0968`.
- 봉인 revision: `lc-prod-legacy-20261018`, count 1748.
- 목록 SHA256: `1f9db0dd6496ee4b028e413ef70a9a52db254beca715d4e89b4fcaedd440cc1a`.
- 중단 확인: desired/running/pending=0, RUNNING tasks=0 후 봉인. `SEALED_VERIFIED`, businessDocumentsModified=false.
- 봉인 컬렉션 부재는 명시 추가 승인 후 생성했다. 삭제 준비 missing/conflicts=0, TTL3개와 delivery index 확인.
- ALB78: host api-prod.to-teacher.com, POST /internal/v1/entitlements/access/events, source13.124.57.130/32 → LC8444. ALB79의 나머지 동일경로404 유지.
- 처음8444 서비스 연결은 TG의 ALB 미연결로 실패해 기존8080으로 우선 기동했다. 이후 승인된 규칙 생성과8080/8444 서비스 연결 완료.
- 앱 Tomcat8080/8444 및 Started 로그 확인. 06:09 UTC 공개 /actuator/health=200 UP 확인. 초기502는 기동 중 관측이며 최종 결과와 구분한다.
- 후속 ALB8080/8444 모두 healthy. 외부 대역의 무인증 환불 POST404 확인. ECS의 이전 deployment/draining 정리가 남아 있어 rollout COMPLETED까지 확인했다는 의미는 아니다. 운영 서비스는 새 prod10 running1/pending0이며 앱의 실제 업무 검증은 별도다.

## 검증 범위

- clean test:1402통과, 기존1skip, 실패0.
- 기동/삭제 wiring 격리 Mongo:13통과, skip/실패0.
- real204/ACK·실제 원문 정리·환불 서명 요청·프론트 시험 생성/제출은 미실행.
- 관련 TMI-116/TMI-193/TMI-217/TMI-220. 적용 설정은 `LC_PRODUCTION_TASK_DRAFT_2026-10-18.json`, 상세 이력은 WORKLOG 참조.
