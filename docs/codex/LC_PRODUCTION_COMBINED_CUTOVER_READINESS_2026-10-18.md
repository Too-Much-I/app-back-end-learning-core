# LC 인덱스·봉인 통합 전환 준비

- 관련: TMI-116/TMI-193/TMI-217/TMI-220.
- 상태: 사전 조건 대조. 중단/DDL/봉인/배포 실행 없음.

## 최신 근거

### LC 삭제 경로 재검증

- 삭제 패키지 단위 테스트7개 클래스/58개 실행, 실패·오류·skip0.
- DeletionFlowMongoTest/DeletionWiringMongoTest를 실제 격리 Testcontainers Mongo로 실행해 성공했다. 저장된204에 따른 checkpoint/삭제 근거 보존, 응답 유실 재시도와 기동 조립 등 기존 회귀 검증이다. Billing 응답은 모의이며 운영 key/TLS/상대204/S3권한 검증은 아니다.
- 첫 격리 실행은 Docker29와 테스트 클라이언트 API 호환 문제로 초기화 실패했다. 기존 기록의 JAVA_TOOL_OPTIONS=-Dapi.version=1.44를 해당 실행에만 적용한 재실행은 성공했다. 전역 Docker/프로젝트 설정은 변경하지 않았다.
- 제품코드 변경/운영 DB쓰기/서버 중단/봉인은 없다. 로컬 검증 결과로 운영 rollout 검증값을 true로 만들지 않는다.

### Billing 회신 접수

`Billing-LC-combined-cutover-readiness-response-2026-10-18.md`를 읽고 아래 선행조건을 접수했다. 회신은 실행 승인/현재 AWS 실측이 아니며 아직 다른 채팅으로 답신하지 않았다.

- paid 예약/MEMBER 복구/상태/삭제는 ACCOUNT fence를 사용하므로 PURCHASE_BLOCKED로 검증할 수 없고 승인된 OPEN 전환이 필요하다. ALL_BLOCKED도 모든 무료 writer를 막지 않으므로 LC 생성 및 공개 purchase-account 유입 제한을 별도로 확인한다.
- OPEN 전 Identity billing:purchase·MEMBER/owner 자료·Billing attribution coverage를 검증한다. LC legacy 봉인은 Billing coverage의 대체 근거가 아니다.
- 삭제는 exact route 허용·목적 JWT·Billing commit 후204·LC durable ACK 저장까지 검증한다. 단순 정상서명 HTTP 성공이나 네트워크 연결로 billingDeliveryVerified를 대신하지 않는다.
- 선행검증 순환이 있다: LC sender 기동에는 billingDeliveryVerified가 필요하지만 해당 값을 확보하려면 sender/204저장을 검증해야 한다. 전체 서비스의 gate를 해제하지 않고 수행할 별도 사전검증 절차와 승인된 검증Session/자료·증거 저장 방식을 먼저 합의해야 한다. 아직 구현/실행/공동 수락된 절차가 아니다.
- 환불 publisher는 LC8444 수신 준비 전OFF 유지. 환불 업무/실제 Store 환불을 이번 준비에 자동 포함하지 않는다.

다음 구체 작업은 **사전 삭제 전달/204 저장 검증 절차 정의와 Identity·coverage 근거 확보**다. 그전에는 중단 시간을 잡거나 rollout 값을 true로 채우지 않는다.

1. LC 운영은 직전 AWS 실측 prod:8/실행1/대기0/8080이다. 배포 후보 immutable digest와 TLS 시작 변환은 준비돼 있으나 task 초안은 DRAFT_NOT_REGISTERABLE이다.
2. Billing 최신 `billing-prod-lc-receiver-8443-2026-10-18.md`는 prod:6/8443 healthy와 DNS·TLS·인증 거절 검증 완료를 보고한다. 이전 prod:5/8443 없음 기록을 현행으로 사용하지 않는다. 이번에는 Billing AWS를 독립 재조회하지 않았다.
3. 같은 Billing 문서는 DB ALL_BLOCKED 유지, owner/attempt 업무OFF·삭제 경로 미허용을 명시한다. 네트워크/JWT 수신 준비를 유효 reserve/confirm/phone/상태/삭제 처리 준비로 확대하지 않는다. 정상서명 업무 E2E도 미실행이다.
4. LC 삭제 정합성 검사는19건 승인 정리 후 통과했다. 표준24개+별도전달1개 index 미설치, 삭제 rollout/v1 부재는 여전하다. 정합성 통과만으로 storageVerified/writerPathsVerified/billingDeliveryVerified를 true로 만들 수 없다.

## 중단 전에 해소할 항목

### LC 설정 구체화

task 초안에 `conditionalDeletionEnvironmentOverrides`를 추가했다. command/read-fence/writer-fence/worker/billing-continuation/sender6개를 함께 ON하는 후보이며 aggregate는OFF다. 실제 환경변수명과 application.yml 매핑을 대조했다. 준비 검증 전 기존 proposedEnvironmentOverrides에 합치거나 ECS에 등록하지 않는다.

| 준비 근거 | 확보할 증거 | 현재 판단 |
| --- | --- | --- |
| writersDrained | 실제 유입 제한·구 요청/worker 종료·재유입 통제 | 중단 후 확인, 선언만으로 충족 불가 |
| inventoryApproved | 중단 후 전체 부모/owner/중복 및 index 재검사 | 직전 정합성 통과, 전환 직전 재검사 필요 |
| writerPathsVerified | 새 이미지의 공통 owner/삭제 fence·Callback/재시도 경로 검증과 적용 범위 | 로컬 코드 경로 있음, 운영 전환 근거 별도 |
| storageVerified | 운영 task의 S3 버전/삭제 권한·exact prefix·Redis 및 답안학습 원격 purge 경로 | 모의 테스트로 운영 권한을 대체할 수 없음 |
| billingDeliveryVerified | 정확 삭제 route·전용JWT·동일event 재시도·204 저장 연계 검증 | 최신 Billing 인계서에서는 삭제 경로 미허용 |

DeletionConfiguration은 답안학습 bucket과 원격 purge도 DeletionStorage에 연결한다. 시험 S3/Redis만 확인하고 전체 storageVerified를 선언하지 않는다. 이번에 실제 학습 원문/파일 삭제 시험은 수행하지 않았다.

### Billing 담당에게 확인할 내용 — 미전송

> LC는 인덱스 설치·기존 시험 봉인·공존 코드 배포를 한 중단 구간으로 준비 중입니다. prod:6의8443/TLS 준비는 인계서로 확인했습니다. 전환 시 reserve/confirm/status·phone/attempt-group 업무와 삭제 event 수신을 처리할 수 있는 최종 설정, ALL_BLOCKED 전환 조건, 삭제 route 허용 및 정상서명 검증 순서를 회신해 주세요. 환불 publisher는 LC8444 준비·검증 전 발신하지 않는 조건을 유지합니다. 이번 요청은 Billing 설정 변경을 LC가 대신 승인하는 것이 아닙니다.

- Billing 담당: 현재 유료 시험·phone·상태 이벤트 및 삭제 수신의 exact 활성화 조건, ALL_BLOCKED 해제 조건/승인과 실행 순서, 정상서명 검증 대상을 확인한다. LC 작업에서 Billing 설정을 변경하지 않는다.
- LC: 삭제 writer 경로·저장소 삭제 권한/정리 경로·Billing 전달 검증의 근거와 최종 활성 설정표를 확정한다. 기동 통과용으로 검증값을 위조하지 않는다.
- LC 배포본: 현재 task와 준비 Secret 참조를 대조하고 삭제 기능 조합까지 반영한다. 최종 봉인값은 중단 후 실제 revision/hash/count만 주입한다.
- 전환 검증: 구앱 무료 생성/기존 제출과 v2 same-key·Billing 정상서명·삭제/환불 검증의 계정/영향 범위와 양측 담당 준비가 필요하다. 비인증401/404만으로 업무 통과를 선언하지 않는다.

## 준비 완료 후 같은 중단 구간에서 실행

1. 유입 제한과 기존 HTTP/worker 배출을 확인한다. task 종료만으로 모든 기접수 채점 완료를 선언하지 않는다.
2. DB 정합성을 재검사하고 승인된 index/TTL을 설치·readback한다. rollout 검증값은 실제 근거가 확인된 항목만 기록한다.
3. 정확한 기존 시험 집합을 봉인하고 revision/hash/count를 재검증한다.
4. 확정된 새 공존 코드와 양측 연결 설정을 적용한다. 구 writer가 재개되지 않도록 한다.
5. TLS/auth/업무 검증 및 구 task 배출을 확인한 뒤 유입을 재개한다.

봉인 전 실패는 기존 서비스 안전 재개 가능성을 확인한다. 봉인 후 실패는 생성 제한을 유지하며 구 prod:8로 단순 복귀하지 않는다. 위 사전 조건이 남은 지금은 중단하지 않는다.
