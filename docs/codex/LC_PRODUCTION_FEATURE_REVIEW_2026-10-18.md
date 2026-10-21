# LC 운영 기능 조합 및 Billing 준비 대조

2026-10-18 / TMI-116·TMI-128·TMI-217·TMI-220. 설정안 검토이며 실제 활성화·배포 승인이 아니다.

## 결론

기본 gate만 ON하는 것으로 전체 유료 운영 준비가 끝나지 않는다. [등록 불가 초안](LC_PRODUCTION_TASK_DRAFT_2026-10-18.json)에 복구·상태이벤트·phone의 목표 ON을 추가했다. 후속 사용자 승인으로 경보 재전송 한도24시간/200회를 초안에 반영했다. 삭제 준비·Sentry 실수신 확인은 미완료이며 봉인 전에 해소한다.

## 기능별 목표

| 기능 | 목표 설정 | 선행조건 / OFF 비용 |
| --- | --- | --- |
| 생성 복구 | BILLING_RECONCILIATION_ENABLED=true | saga·signer·Mongo Tx·복구 index/control·enabled Sentry 및 경보 재전송 한도 필수. OFF는 클라이언트가 돌아오지 않는 작업의 자동 수렴을 보장하지 못함 |
| 상태 이벤트 | ATTEMPT_GROUP_EVENT_WRITER_ENABLED=true, PUBLISHER_ENABLED=true | outbox index/state와 Billing 수신 준비. publisher만 켜는 drain은 가능하지만 신규 운영 목표는 둘 다 ON. OFF면 Billing에 시험 상태가 정상 전달된다고 할 수 없음 |
| 재가입 continuation | BILLING_PHONE_CONTINUATION_ENABLED=true | saga·signer·Billing phone/owner 검증 준비. OFF는 이전 무료 권리 발견을 건너뛰므로 승인된 재가입 동작을 제공한다고 할 수 없음 |
| 학습 삭제 | command/read-fence/writer-fence/worker/billing-continuation 및 Billing deletion sender 함께 준비 | 아래 삭제 rollout 및 저장소 검증 전 ON 금지. OFF 유지는 임시 미지원 상태이며 전체 출시 완료로 간주하지 않음 |
| 삭제 집계 | aggregate OFF 유지안 | 별도 통계 기능이며 ON하려면 daily aggregate index/coverage 필요. 안전 fence를 대신하지 않음 |

위 ON은 로컬 초안의 목표값이다. 현재 실행 서버는 OFF 그대로다. 최종 삭제 준비가 안 됐다는 이유로 삭제를 임의 제외한 완전 출시안을 확정하지 않는다.

## 경보와 복구의 한도 구분

ReservationReconciliationProperties의 작업 복구 상한 기본값은24시간/200회다. 이것과 Sentry 경보 전송 재시도 한도는 별개다. 경보 한도는 기본값이 없으며 RUNBOOK에서도 운영 승인 전24시간/200회를 확정하지 않았다고 명시한다.

- 2026-10-18 사용자 「어」로 승인: BILLING_RECONCILIATION_ALERT_MAX_AGE=PT24H, BILLING_RECONCILIATION_ALERT_MAX_ATTEMPTS=200.
- 로컬 초안의 environment override에 반영했다. 서버 적용·worker 활성화 승인이 아니며 Sentry 수신자/Alert Rule·실수신 확인은 여전히 필요하다. 제품 코드 기본값은 만들지 않는다.
- 복구 connect<=2초/read<=5초, attempt>=14초, lease>=attempt+5초 및 기존20초 attempt/30초 lease 기본값을 유지한다. 경보 한도 소진은 결제 결과 추정/작업 삭제 허가가 아니다.

## 삭제의 별도 준비

DeletionActivationBarrier는 하나라도 ON이면 read/writer fence와 billing-continuation을 함께 요구하고, command는 worker를 요구한다. Billing sender는 signer/writer-fence/worker 외에 다음 증거를 요구한다.

- learning_record_deletion_rollout/v1: writersDrained, inventoryApproved, writerPathsVerified, command ON 시 storageVerified, sender ON 시 billingDeliveryVerified.
- 삭제 operations/commands/targets/continuations 및 cleanup 대상 index, 활성 작업의 TTL 부재, 전달용 due index와 billingDelivery 문서의 expiresAt 보호.
- 기존 운영 준비 기록에는 삭제24개 index 미설치·정합성 보류가 있다. Azure 보완 코드 전달은 이 DB 증거 설치 완료와 다르다. 이번에는 실DB를 조회하지 않았으므로 현재도24개 미설치라고 단정하지 않는다.

검증하지 않은 rollout 값을 true로 쓰거나 TTL을 임의 설치하지 않는다. 삭제 sender만 ON하는 방식은 기동 조건을 충족하지 않는다.

## Billing 실제 상태 — 이번 읽기 조회

ECS에서 LC prod:8/Billing prod:5 각각 실행1·대기0. Billing 실행 이미지는 sha256:5f1723774ca6beb43c27af0a610d8e0fecf512aa70071400b08eacf2334954a9로 전달 후보 e5666d53…와 다르다. 태스크 포트8082/8083, 서비스 연결8083이며8443이 없다.

명시 environment의 BILLING_WORKLOAD_JWT_RECEIVER_ENABLED, BILLING_WORKLOAD_LEARNING_CORE_RECEIVER_ENABLED, BILLING_EXAM_DELETION_ENABLED, BILLING_PAYMENT_REFUND_PUBLISHER_ENABLED는 false다. purchase-account/member-evidence도 false다. 실제 앱의 모든 설정 우선순위나 endpoint를 검사한 결과는 아니지만, 현재 태스크에8443이 없고 전달 후보가 미배포인 사실은 확인했다.

Billing에 필요한 확인은 후보 이미지/TLS 시작명령·8443 연결, LC 예약/status/confirm/cancel/phone·상태이벤트 수신, MEMBER purchase-account 경로, 삭제 수신 및 환불 발신을 어떤 순서로 준비할지다. 관련 플래그를 LC가 추측하여 일괄 ON하지 않는다. 이번에는 Billing 저장소·서버 변경이나 타 스레드 메시지를 보내지 않았다.

## 다음 순서

1. 승인된 경보 운영값의 실제 주입 전 Sentry 수신 근거, 삭제 DB/rollout·storage 근거를 확인한다.
2. Billing 담당과 위 전용 수신/후보 배포 준비 및 공동 전환 순서를 확인한다.
3. 선행조건을 갖춘 뒤 생성 제한/배출 → 최종 목록 봉인 → 양측 배포/연결 → 인증/TLS·앱 검증 → 생성 개방을 실행한다.

이번은 코드 정적 대조·AWS describe/list·JSON 검사이며 Gradle/실DB/공동 HTTP 테스트는 미실행이다. 코드·서비스·권한·ALB·DB·봉인·commit/push 변경 없음.

근거: ReservationReconciliationStartupValidator/Properties, BillingWorkloadConfiguration, PaidExamCreationSaga, DeletionActivationBarrier/StartupValidator, BillingDeletionConfiguration 및 BILLING_RESERVATION_RECONCILIATION_RUNBOOK.md.
