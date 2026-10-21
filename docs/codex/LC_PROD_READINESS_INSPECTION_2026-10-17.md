# LC prod 전환 준비 읽기 점검 — 2026-10-17

## 결론

현재 운영 서비스는 기존 설정으로 동작 중이며 새 v1/v2 공존·Billing 연동 버전을 바로 배포할 준비는 끝나지 않았다. 이번에는 읽기 점검과 문서 기록만 수행했다. 운영 DB/index/manifest·키·권한·네트워크·서비스·브랜치는 변경하지 않았다.

관련: TMI-116/TMI-128/TMI-217/TMI-220/TMI-193.

## 실제 확인

| 대상 | 관측 |
| --- | --- |
| LC prod ECS | `tosunsaeng-learning-core-prod:7`, running1/pending0, rollout COMPLETED |
| 이미지 | `sha256:0b566cc1cd7b73ac87a0be133aa834f84333bb16b1a44db12d36b09bc9958fd0` |
| profile / DB | prod / `to-teacher-app`; 서비스 Secret의 host와 로컬 읽기 접속 host 일치 확인 |
| Billing 업무 설정 | creation saga/phone/reconciliation/outbox writer/publisher OFF |
| 새 기동 설정 | paid production approval, workload JWT/access-events/legacy 관련 환경변수 및 전용 Secret 주입 없음 |
| LC 포트 / 대상 그룹 | 8080만 등록; 8444 수신 대상 연결 없음 |
| 운영 ALB | api-prod 일반 요청은 LC8080, challenge evaluation은 기존 AI로 전달. 조회한 규칙에서 별도 운영 Billing/환불 수신 경로 없음 |
| Billing ECS | 동일 클러스터 서비스 목록에는 test만 존재. `tosunsaeng-billing-prod-service`는 MISSING. 다른 환경의 Billing DB/서비스 전체 부재라는 의미는 아님 |
| Git | GitHub main `89defb7`과 readiness `9fb8f71` 비교 diverged, 보완 쪽 ahead73/behind2. 단순 fast-forward 또는 배포본 동일성 확인 완료로 보지 않음 |

Secret 값·URI·사용자/시험 식별자·문제 원문은 보고서에 남기지 않았다. 전용 키 자체가 AWS 전체에 존재하지 않는다는 판단은 하지 않았고, 현재 운영 task에 주입되지 않은 사실만 확인했다.

## 운영 DB 메타데이터

- `exam_sessions` 1,718건, `mock_exams` 20건, `user_ownership_guards` 77건.
- `exam_creation_operations`, `exam_legacy_manifest_entries`, `exam_paid_rollout_state` collection 없음.
- `attempt_group_event_outbox`, `attempt_group_publisher_state`, `billing_access_revocations`, `reservation_reconciliation_control` collection 없음.
- `learning_record_deletion_operations`, `learning_record_billing_continuations` collection 없음.
- 기존 Session의 active-user unique 및 완료/알림 조회 index, 문제 ID unique를 확인했다. owner guard는 기본 `_id` index만 조회됐다. `_id`의 내재 unique 의미를 옵션 생략 때문에 부정하지 않는다.
- 신규 collection의 필수 index/봉인도 미준비다. 현재 existing index 전체가 신규 validator 요구를 충족한다는 결론은 아니다.
- 여러 읽기 조회의 관측이며 고정 snapshot/manifest 봉인이 아니다. 1,718을 미래 전환 대상 건수로 고정하지 않는다. 실제 전환 직전 새 작성 경계를 통제하고 exact 목록을 재확정해야 한다.

## 배포 전 남은 순서

1. 테스트에서 구앱/신앱 생성 후 제출·채점·Summary 및 주요 복구/삭제/환불 연동 결과 확인. 생성 성공 사용자 확인과 구분한다.
2. Billing prod 서비스·DB/schema·운영 인증/TLS/scope 연결 준비 및 LC 설정과 공동 대조.
3. 기존 운영 시험 보존 정책에 따라 exact legacy 목록 봉인과 신규 collection/index·owner 준비. 기존 Session source 일괄 수정 금지.
4. main 차이 검토, 운영 준비 commit 포함 배포본 확정, 구앱 v1 무료 설정과 production 기동 조건 동시 검증.
5. 별도 적용 승인 후 운영 배포·실앱 smoke 및 롤백 판단. 기존 서비스 유지가 기본이며 새 코드에 paid OFF만 적용하는 것을 안전 롤백으로 가정하지 않는다.

현재 v1 무료도 필수 Legacy/runtime/owner 준비가 필요하다. 따라서 코드만 먼저 올리면 기존 구앱의 시험 생성이 차단될 위험이 있다.
