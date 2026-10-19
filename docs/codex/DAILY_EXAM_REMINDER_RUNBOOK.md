# TMI-198 일일 모의고사 알림 운영 준비

## 1. 5줄 결론

1. 서버 구현은 기본OFF이며 Firebase/APNs 설정·모바일 변경·배포·실제 발송을 수행하지 않았다.
2. 모든 필수 최초 제출의 실제 접수 시각으로 완료일을 판정하고, 채점 상태나 이용권은 보지 않는다.
3. KST21:00~21:15에 등록된 적격 MEMBER 기기마다 최대1회 HTTP 시도한다. timeout·실패·불명은 재시도하지 않는다.
4. writer/인덱스/lifecycle 준비와 하루 전체 추적 확보 후 dry-run부터 시작한다.
5. OS 권한만 사용하는 제품 결정은 광고성·야간 알림 요건 확인을 대체하지 않는다.

## 2. 반드시 확인할 사항

[모바일 계약](../contracts/notification-device-api.md)의 설치 secret·OS 상태 보고·로그아웃·클릭 처리를 먼저 연동한다. 기존 회원/미시작 회원은 등록된 기기 목록을 통해 포함된다. Identity 전체 회원 동기화나 Billing 권리 조회는 없다.

FCM HTTP 성공은 접수 성공이며 표시·열람 성공이 아니다. 최종 확인 직후의 제출/로그아웃/권한 변경과 네트워크 경계의 경쟁을 완전히 제거할 수 없다. 전송 전 DB marker만 기록하고 crash하면 알림이 누락될 수 있다. 이는 재발송 금지 정책의 의도된 절충이다.

## 3. 설정 / 출시 gate

모든 항목은 Spring configuration property이며 Spring 표준 환경변수 매핑으로 주입한다. 저장소 설정 파일에 운영 credential을 추가하지 않는다.

| 속성 | 기본값 / 조건 |
| --- | --- |
| `app.notifications.tracking-enabled` | false. Job/receipt/Session을 같은 Mongo Transaction으로 기록 |
| `app.notifications.api-enabled` | false. 기기 API 제공, JWT 필요 |
| `app.notifications.sending-enabled` | false. scheduler 활성화; tracking/API/readyAt와 lifecycle gates 필요 |
| `app.notifications.dry-run` | true. 대상 카운터만 기록, delivery marker·OAuth·FCM 호출 없음 |
| `app.notifications.tracking-ready-at` | 미설정. 구 writer 완전 drain 후 증빙한 UTC Instant; 해당 KST 하루 시작 이전이어야 그날 발송 |
| `app.notifications.batch-size` | 100, 허용1~100. instance별 순차 처리, stable installation cursor |
| `app.notifications.poll-ms` | 1000. sweep 뒤 처음부터 반복해 새 기기 포함 |
| `app.notifications.project-id` | 실제 발송에만 필요, Firebase project ID |
| `app.notifications.credential-file` | 실제 발송에만 필요, 외부 secret-mounted 절대 경로. JSON service_account/project_id 일치 검증 |

API/sender는 `app.auth.mode=jwt`가 필수다. sender는 기존 `app.user-merged.writer-enabled`, `consumer-enabled`, `source-deny-enabled`, `app.user-withdrawn.consumer-enabled`, `deny-gate-enabled`가 모두 켜져 있어야 한다. 추적 단독은 이 flag들 없이도 자체 Mongo manager로 동작한다. 기존 manager의 Transaction이 있으면 그대로 참여한다. Learning record deletion 활성화 시 기존 fence·worker gate 역시 충족해야 한다.

기동 시 collection, unique/TTL/query index의 exact key/options와 실제 rollback write Transaction을 검사한다. replica set이 없거나 준비가 덜 됐으면 기동을 거절한다. 알림 때문에 별도 feature flag를 몰래 켜지 않는다.

## 4. 인덱스 준비와 단계별 활성화

1. 대상 Learning Core DB를 명시하고 notification/submit 구 writer를 drain한다. 자동 운영 실행 없음.
2. 외부에서 `MONGODB_URI`, `MONGODB_DATABASE`를 안전하게 주입하고 `node scripts/mongodb/notification-prepare.js`로 dry-run한다. credential·문서 내용은 출력하지 않는다.
3. 검토/승인 후 `NOTIFICATION_PREPARE_APPLY=true`, `NOTIFICATION_WRITERS_DRAINED=true`로 같은 스크립트를 실행한다. collection/index만 준비하며 중복 데이터 수정·기존 제출 시간 backfill·발송은 하지 않는다.
4. tracking/API 선배포와 구 instance drain. ready-at를 정확히 기록하고 최소 다음 KST 하루 전체의 추적을 확보한다.
5. 예전 시험은 Job pendingAt이나 채점 completedAt에서 제출 날짜를 추정하지 않는다. ready-at 이전 생성/생성일 미상 + submissionCompletedAt 미상의 시험을 가진 회원은 보수적으로 제외한다. 오래된 폐기 시험도 이 조건에 해당할 수 있어 별도 검증된 backfill/정책 검토 전까지 알림이 누락될 수 있다. 이 구현에서 과거 데이터 자동 보정은 하지 않는다.
6. sending-enabled + dry-run으로 집계 확인. 소규모 검증은 실제 사용자 없는 staging과 테스트 기기만 등록한 DB에서 수행한다. production allowlist 기능은 이 구현에 없으므로 운영 사용자 DB에서 테스트 발송하지 않는다.
7. Firebase/APNs 환경 일치, 제한된 service account 권한, mounted secret 접근, DB 저장 암호화/RBAC, egress/TLS, 모바일 iOS/Android foreground/background/종료/권한 거부/클릭/TTL, 야간 요건을 검증한 후 별도 승인으로 실제 발송 활성화.

## 5. 이력과 장애 대응

- 발송 marker는 동일 user/date/type/installation unique + `_id`로 중복을 막는다. 후보는 예약하기 전 durable PENDING 상태를 만들지 않고 트랜잭션으로 바로 SENDING을 기록한다. 따라서 창 종료 미시도 대상은 이력 없이 폐기되며 다음 날 과거 알림을 전송하지 않는다.
- marker 이후 ACCEPTED/FAILED/UNKNOWN/SKIPPED는 당일 재전송하지 않는다. 토큰 교체도 새 기회가 아니다. SENDING60초 초과는 bounded maintenance로 UNKNOWN이 된다.
- 외부 전송은 DB Transaction 밖. Apache HTTP client 자동 retry/redirect를 끄고 localhost disconnect/redirect 테스트로 단일 HTTP 시도를 검증한다. timeout은 connect3초/read5초다.
- 401/403 또는 OAuth 인증 거부는 `notification_control`의 `fcm-circuit.authBlocked=true`로 전 instance 차단한다. 자동 probe/재시작 해제 없음. credential/권한 수정·검증 후 승인된 운영 절차로 해당 필드만 해제한다. delivery 이력 삭제나 상태 되돌리기로 재시도하지 않는다.
- 429/503의 Retry-After(초/HTTP-date)를1초~1일로 제한해 아직 미시도인 기기만 일시 정지한다. OAuth 일시 실패도60초 정지한다. 전역 circuit 저장이 실패하면 해당 sweep은 실패하고 marker가 남으므로 해당 기기는 재시도하지 않는다.
- 상태 카운터 `notification.daily_reminder{outcome=...}`는 고정 값만 사용한다. AUTH_BLOCKED/SWEEP_FAILURE/LOCAL_FAILURE 증가를 운영 경보와 연결해야 한다. raw exception·token·user/exam ID를 metric/log에 넣지 않는다.
- delivery 모든 상태: 해당 KST 날짜 종료+30일 TTL. suppression: 다음날 종료까지 유지(효력은 dateKst 당일만). 기기90일 미관측 시 토큰·owner 제거, proof tombstone 보존. API/sender가 켜져 있으면 bounded 유지 작업 실행. 모두OFF 동안 TTL 자체는 Mongo가 처리하지만 비활성 기기 정리는 scheduler 재활성화 때 수행한다.
- 사용자 승인 보존 변경: 시험 전체 최초 제출 완료와 같은 Transaction에서 해당 시험의 모든 `exam_submission_receipts.expiresAt`을 `submissionCompletedAt + 72시간`으로 설정한다. `submission_receipt_ttl` 인덱스는 expiresAt의 absolute TTL(0초)이다. 미완료 시험은 expiresAt이 없어 만료되지 않고, Session의 완료 시각은 계속 남는다. 완료 이후 replay는 영수증을 재생성하거나 만료를 연장하지 않는다. Mongo TTL monitor가 비동기로 삭제하므로 정확히72시간에 즉시 삭제되는 보장은 없다.
- 이 변경을 배포하기 전에 최신 notification-prepare 스크립트로 새 TTL 인덱스를 준비해야 한다. 기존 버전에서 이미 완료됐지만 expiresAt이 없는 영수증의 자동 backfill은 수행하지 않는다. 현재 테스트 환경 추적은 아직 비활성이지만 다른 환경에서 이전 버전 추적을 켰다면 배포 전 해당 데이터 inventory와 별도 backfill 검토가 필요하다. `exam_sessions`에 TTL을 추가하지 않는다.
- 탈퇴는 token 제거+당일 억제, 병합은 source 기기 해제와 source 당일 완료/시도/억제의 target 억제, 학습 삭제는 요청 당일 억제와 sealed exam의 receipt 삭제를 포함한다. 기존 delivery payload/userId를 target으로 rewrite하지 않는다.

## 6. 검증과 남은 범위

단위/계약 테스트, 격리 replica-set Mongo 테스트, migration Node 테스트를 사용한다. 실제 Firebase 인증·전송과 모바일 표시 검증은 하지 않았다. TMI-63 채점 완료 Push, TMI-58 모바일 구현, 별도 수신 동의/앱 토글/22시 추가 알림/이용권 조회/배포는 이번 변경에 없다.

rollback은 sending-enabled=false부터 적용한다. 추적/API는 유지 가능하며 기존 submit·Polling·AI/S3/Redis 외부 계약은 바뀌지 않는다. 이미 FCM이 접수한 알림은 회수할 수 없다.
