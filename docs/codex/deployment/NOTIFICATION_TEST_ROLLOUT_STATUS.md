# TMI-198 테스트 알림 단계별 활성화 (2026-10-07)

## 5줄 결론

1. 대상은 develop / api-test / `tosunsaeng-learning-core-test-service`이며 운영 서비스는 변경하지 않는다.
2. 커밋 `a77a53f907e3ee91528e1d14afe17a801fc6b42a`의 코드 배포와 CI 헬스 체크가 성공했다.
3. 사용자 승인 범위는 일시 중지·인덱스 준비·기기 API/제출 추적 ON 및 `APP_UPDATE_REQUIRED=false`다.
4. 실제 발송은 OFF, dry-run은 true로 유지한다. FCM/탈퇴 연동/모바일 검증은 별도 준비 대상이다.
5. 기존 writer 종료·12개 인덱스 적용·test revision 17 기동 검증을 완료했다. ECS COMPLETED, ALB healthy, HTTPS health 200/UP이다.

## 반드시 읽을 사항

- [CI 실행](https://github.com/Too-Much-I/app-back-end-learning-core/actions/runs/37573651836): workflow_dispatch, unit/migration 및 Mongo replica-set integration, image build/push, ECS 배포, health 모두 성공.
- 직접 develop push 실행에서는 merge gate가 배포를 건너뛰어 별도 workflow_dispatch를 실행했다. 새 코드는 test task revision 16에 배포됐다.
- 인덱스 사전 점검: `notification-prepare.js`를 위 고정 커밋에서 읽어 테스트 전용 일회성 Fargate task로 실행했다. dry-run exit 0, `{apply:false, collections:6, indexes:12}`.
- 데이터 inventory: receipts/devices/deliveries 각각 0건. 문서·토큰 원문을 조회하거나 보존하지 않았다. 첫 보조 집계 명령은 mongosh top-level await 문법 오류로 실패했고 async wrapper로 재실행해 exit 0을 확인했다. 저장소 migration 자체는 첫 실행부터 통과했다.
- 실행 환경은 기존 테스트 DB 준비 task의 Mongo 7 이미지·secret reference·execution role와 테스트 서비스 네트워크를 재사용했다. 신규 IAM 권한·credential은 만들지 않았다.

## 완료 증거

- 기존 task STOPPED, 서비스 desired/running/pending 모두 0 및 남은 서비스 task 없음 확인. drain 확인 시각: `2026-10-07T05:11:06.853838Z`.
- 인덱스 apply task exit 0, `{apply:true, collections:6, indexes:12}`. 저장소 스크립트의 exact key/options 사후 검증까지 통과했다.
- 설정 revision: `tosunsaeng-learning-core-test:17`. revision 16의 기존 설정을 보존하고 `APP_UPDATE_REQUIRED=false` 및 `SPRING_APPLICATION_JSON`에 아래 속성만 추가했다. 기존 JSON 설정이 없음을 확인했다. 최초 등록 요청은 빈 tags 필드 때문에 AWS가 거절했고, 빈 필드를 생략한 재요청은 성공했다.
  - `app.notifications.tracking-enabled=true`
  - `app.notifications.api-enabled=true`
  - `app.notifications.sending-enabled=false`
  - `app.notifications.dry-run=true`
  - `app.notifications.tracking-ready-at=2026-10-07T05:11:49.181835Z`
- 위 ready-at은 구 writer 완전 종료 뒤 새 설정을 등록한 실제 UTC 시각이다. 중지 구간에 제출은 접수되지 않는다. 이후 완전한 KST 날짜의 추적을 확인하기 전 발송을 켜지 않는다.
- ECR의 `test-a77a53f907e3ee91528e1d14afe17a801fc6b42a` 태그 digest와 task image 일치 확인: `sha256:22a878a5eff35f8c773394eb24682795653e573e04740b718a9cb7c3656b6d06`.
- 중지 상태에서 revision 17을 선택하고 안정화된 뒤 desired 1 복원. 최종 ECS COMPLETED / desired 1 / running 1 / pending 0 / ALB healthy 확인. ECS container 자체 health는 별도 health check 미설정으로 UNKNOWN이며 ALB 및 HTTPS 결과와 구분한다.
- `https://api-test.to-teacher.com/actuator/health`: HTTP 200, status UP. 기동 로그의 Started 신호 확인(05:14:16Z), 표본 내 startup failure 및 Exception 신호 없음. 알림 configuration의 index/rollback Transaction 기동 gate를 통과했다. 초기 ALB unhealthy는 기존 30초 간격·5회 연속 성공 정책에 따라 healthy로 수렴했다.
- revision 16과 17의 등록 가능 task 필드를 메모리에서 비교해 위 두 환경변수 외 설정·image·secret reference·role 불변을 확인했다. 실제 JWT를 이용한 기기 등록/제출 E2E 및 FCM 실기기 발송은 수행하지 않았다.
- 실제 발송: 수행하지 않음.

## 위험과 다음 단계

- 완료된 시험 receipt에만 완료시각+72시간 TTL을 설정하고 미완료 receipt 및 Session 완료시각을 유지한다. TTL 삭제는 비동기다.
- 추적 준비 시각 이전의 시험 제출 완료일을 추정하거나 backfill하지 않는다. 발송 활성화 전 하루 전체 KST 추적과 lifecycle/FCM/mobile 출시 gate를 충족해야 한다.
- 이번 변경으로 기존 공개 시험 API·AI user_id=examId·S3·Redis 계약은 바뀌지 않는다. 회원 JWT를 사용하는 기기 등록과 실제 시험 제출의 앱 E2E는 별도 검증 대상이다.
- DB 인덱스는 유지하고 문제가 생기면 sender OFF를 유지한 채 API/tracking을 이전 설정으로 되돌리는 것이 rollback 경계다.
