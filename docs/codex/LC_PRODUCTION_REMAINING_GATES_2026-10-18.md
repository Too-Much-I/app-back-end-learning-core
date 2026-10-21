# 운영 잔여 조건 실측

## 삭제 후 전체 준비 검사 재실행

- 운영 `to-teacher-app` 읽기 재검사: 전체 검사 대상 orphan0, owner 불일치0, 활성 삭제owner 중복0, index충돌0, 전달TTL위반0. 개별 읽기 집계이며 고정 snapshot은 아니다.
- 표준명세24개는 여전히 미설치: non-TTL21개(선택적 일별집계 unique1개 포함), 삭제 operations/commands/targets의 expiresAt TTL3개. 별도 Billing 전달 index1개도 미설치다. 정확 key/options는 `scripts/mongodb/learning-record-deletion-prepare.js` 및 BillingDeletionConfiguration을 따른다.
- 적용안: 기존 도구 적용에는 writer drain 증거가 선행돼야 한다. TTL3개는 삭제 증거 문서의 expiresAt 도달 후 영구 삭제이며 학습 원문 TTL이 아니다. 미완료/미전달 문서에 expiresAt을 넣지 않는 조건을 유지한다. 기존 승인된 생성operation/outbox TTL과 별도다.
- rollout/v1은 부재다. writersDrained/inventoryApproved/writerPathsVerified/storageVerified/billingDeliveryVerified는 확인 근거가 없으며 true로 임의 생성하지 않는다.
- 준비 도구 Node11개 통과. 이번에는 DDL/rollout/서버중단/봉인/배포 없음. 다음은 writer 제한·배출 범위와 index/TTL 적용 승인 후 재검사·설치·readback이며 기동승인 근거는 별도 확인한다.

## 2026-10-18 후속 승인 정리 완료

사용자 「아냐 그냥 지워줘」 승인으로 사본과 동일한 운영 미연결 결과4건·Job15건을 단일 Transaction으로 삭제했다. 두 컬렉션의 대상 잔여/미연결 각각0을 확인했다. 복구 사본은 로컬 제한 폴더에 보존한다. AI 실행 부재는 미검증이며 이를 완료로 보고하지 않는다. 아래19건 차단은 이전 관측 이력이다. 전체 삭제 준비 검사/인덱스/rollout·Billing 연결·봉인은 별도 잔여로, 이번 삭제만으로 운영 준비 완료가 아니다.

## 삭제 준비 후속 검토 — 미연결 자료 처리 결정 필요

- 기존 운영 읽기 증거의19건은 결과4건·문항Job15건이며 부모 시험2개가 없다. 이번 후속은 코드/기존 증거 대조이며 운영 DB 재조회가 아니다.
- 준비 도구는 orphanChildren이 있으면 DDL 전 거절한다. 실제 DeletionWorker도 사용자별 부모 없는 결과/요약을 발견하면 UnsafeEvidence로 격리한다. 따라서 설치 검사만 건너뛰는 방식은 삭제 기능 준비 완료가 아니다.
- 자료를 버려도 된다는 근거는 없다. 부모 재생성·Job 상태 강제 변경·검사 제외·삭제를 수행하지 않았다. 보존한 채 처리하려면 별도 격리/소유권/삭제 완료 의미를 먼저 설계하고 승인받아야 한다.
- Billing 전달 문서는 후보 ECR 전달과 prod:5 유지·기능OFF를 기록한다. 이번 문서 확인은 최신 AWS 실측을 대신하지 않으며 실제8443/수신 준비 완료로 간주하지 않는다.
- 다음 결정은19건의 원인/소유권 추가 조사 또는 별도 보존·격리 설계 범위다. 결정 전 서비스 중단·최종 봉인·삭제 index/TTL 설치를 진행하지 않는다.

2026-10-18 / TMI-116·TMI-128·TMI-193·TMI-217·TMI-220. 읽기 점검만 수행했다.

## 결과

| 대상 | 이번 확인 | 판단 |
| --- | --- | --- |
| LC 삭제 준비 index | 준비 도구 전체 명세24개 미설치, 충돌0 | 설치 완료 아님. 명세에는 선택적인 집계 index와 TTL3개 포함 |
| 삭제 전달 index | prepared/state/nextAttemptAt/_id 조합 없음 | 별도 준비 필요 |
| 삭제 rollout/v1 | 문서 없음 | writersDrained/inventoryApproved/writerPathsVerified/storageVerified/billingDeliveryVerified 승인 근거 없음 |
| 데이터 정합성 | 부모 미연결 하위 문서19건, owner 불일치0, 활성 삭제 owner 중복0 | 기존 preflight는 미연결19건 때문에 DDL을 거절함 |
| 전달 보존 | billingDelivery 존재+expiresAt 존재0건 | 현시점 위반0건이며 미래 보존 검증을 대신하지 않음 |
| Sentry | 기존 탭이 TMI 로그인 화면 | 경보 수신/Alert Rule·수신자 미확인. 로그인 필요 |
| Billing | prod:5 실행1/대기0, 서비스8083, 태스크8082/8083 |8443 없음. LC수신·workloadJWT수신·삭제·환불publisher 명시false |
| LC | prod:8 실행1/대기0, 서비스8080 | 기존 운영 유지 |

DB는 기존 로컬 LC 연결 설정의 host 및 `to-teacher-app` DB를 exact 검사하고 mongosh로 index/rollout/집계만 읽었다. 첫 제한 환경 시도는 실패했고 네트워크 허용 실행에서 성공했다. URI·비밀값·문서 식별자·학습 원문은 출력/저장하지 않았다. 다중 조회는 고정 snapshot이 아니므로 실행 직전 재검사한다.

Azure는 수정된 `exam_id` 기준으로 검사했다. 이번19건은 전체 부모 미연결 합계이며 건별 원인이나 불필요 데이터라는 판정이 아니다. 이를 삭제하거나 preflight를 우회하지 않았다. rollout 문서 부재를 자동으로 true값 생성하는 근거로 쓰지 않는다.

## 다음에 필요한 작업

1. Sentry 로그인 후 기존 운영 경보·Alert Rule·수신 근거 확인. 없으면 비식별 시험 경보 발송 범위를 별도 확인한다. 이번에는 경보를 발송하지 않았다.
2. 미연결19건 처리 방침과 삭제 준비 도구의 안전한 적용 경계를 확정한다. 인덱스 설치/TTL3개/rollout 변경은 읽기 점검과 별도 승인 대상이다. 기존 시험을 삭제해 통과시키지 않는다.
3. Billing 담당의 후보·8443·수신 준비와 공동 전환 순서를 확인한다. 임의로 Billing 설정을 바꾸거나 메시지를 보내지 않았다.
4. 이 조건 충족 전 신규 생성 제한·봉인을 시작하지 않는다.

## 검증 및 비변경

삭제 준비 도구 Node11개 통과(dry-run 비쓰기/Azure 필드/TTL/정합성 실패 경계). 제품 코드 변경 없어 Gradle 미실행. AWS describe·운영 DB 읽기 외 원격 변경 없음. 문서 갱신 및 임시 읽기 검사기만 작성했으며 DB DDL/삭제/봉인/서버 중단/배포/기능ON/commit/push는 미실행이다.

## 후속 Sentry 로그인 후 확인

- 사용자 로그인 완료 후 조직 tmi-99의 `to-teacher-learning-core` 프로젝트를 읽기 조회했다. 로그인 차단은 해소됐다.
- 기존 Alert3828472 `Send a notification for high priority issues`는 활성 상태, 모든 환경 대상, Email 동작이다. 신규/기존 이슈를 Sentry가 high priority로 표시할 때 Suggested Assignees, 없으면 Recently Active Members에게 알리는 설정이다. 고정 수신자를 확인한 것은 아니다.
- 최근14일 trigger6회, 마지막2026-10-13 09:03 UTC. 이는 Sentry 규칙 실행 이력이며 이메일 받은편지함 도착이나 운영환경 사건만의 이력이 아니다.
- LC 프로젝트·전체환경·최근14일·`capture.source:billing_reservation_reconcile` 검색은 일치 이슈0건이다. 기간 밖 이력 부재나 전송 장애를 단정하지 않는다. 현재 복구 기능OFF와 구분하며 실제 운영 복구 경보 실수신은 여전히 미검증이다.
- 소스 ReservationReconciliationAlertReporter는 위 source tag와 reconciliation.reason을 기록하며 SDK_ACCEPTED가 전달 ACK가 아님을 명시한다. 기존 high-priority 조건만으로 전용 복구 경보 전부의 통지를 보장할 수 없다.
- 다음은 운영 복구 전용 조건과 명시 수신자를 정한 경보 규칙 보완, 비식별 시험 경보1건의 Sentry 수집 및 수신자 도착 확인이다. 이번에는 규칙 편집·경보 발송·서비스/DB 변경을 하지 않았다. 신규 규칙/수신자·시험 발송은 별도 확인한다.

### 지정 수신자 승인 후 생성 시도

- 사용자가 지정 이메일로 전용 경보·시험 알림1건을 승인했다. 새 경보 양식에서 LC 프로젝트와 prod 환경을 선택하고 Member 수신자 선택을 확인했다.
- 지정 주소 검색은0건이며 현재 선택 목록에는 다른 기존 회원1명만 표시됐다. 현재 알림 동작은 임의 이메일 입력이 아니라 기존 Member/Team/Suggested Assignees 선택 방식이다. 지정 주소로 발송 가능한 상태가 아니므로 다른 회원에게 대신 보내지 않았다.
- 수신자 확보 전 생성/시험 발송을 하지 않고 미저장 양식을 Cancel로 닫았다. 기존 Alerts4개 목록 유지 확인. 계정 초대·접근 권한 변경·기존 규칙 수정 없음. 지정 주소를 사용할 수 있는 회원/알림 이메일 설정 준비 또는 다른 수신자 지정을 기다린다.

### 등록 계정으로 변경 승인·경보 생성 및 시험 수신 완료

- 사용자 「그럼 그냥 현재 등록된 계정으로 해줘」로 수신 대상 변경을 승인했다. 새 Alert6187357을 생성했고 기존 규칙은 수정하지 않았다. [경보](https://tmi-99.sentry.io/monitors/alerts/6187357/).
- 프로젝트 to-teacher-learning-core, 환경 prod, `capture.source` equals `billing_reservation_reconcile`, `An event or issue activity is captured`, 매 trigger 알림. high-priority 의존/신규 issue만의 제한 없이 해당 태그 이벤트에 적용하며 수신자는 현재 등록된 Member1명으로 고정했다.
- UI 내장 Send Test Notification을 정확히1회 실행, `Notification fired!` 확인. 사용자 「어 왔어」로 메일함 도착까지 확인했다. 증빙 `/private/tmp/lc-prod-recovery-alert-test-20261018.jpg`.
- 이로써 Sentry 경보 설정/시험 알림 수신 확인은 완료다. 내장 시험 알림은 LC SDK→Sentry 실제 복구 이벤트 수집·필터 매칭·원 operation 복구의 E2E 검증이 아니다. 정상 trigger 이력0은 이 구분에 부합하며 장애로 판단하지 않는다.
- 앱 서버·DB·기능 flag·봉인·배포는 변경하지 않았다. 계정 초대·권한 확대 없음. 삭제 DB/Billing readiness 및 배포 후 실제 LC 발신 검증은 남는다.
