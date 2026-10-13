# LC 발신 설정 명칭 매핑 — A 확정·LC startup 조합표 수락

> 2026-10-13 사용자 승인으로 LC는 신규 세 명칭과 공통 준비 gate 의미를 최종 수락했다. [A 최종 수락 기록](LC-BILLING-WORKLOAD-A-ACCEPTANCE-R1.md)이 현행 상태다. 아래 후보/LC 회신 대기 문구는 인수 당시 이력이며 상세 startup 조합표·B 구현/배포 승인은 별도로 남는다. runtime 설정은 아직 변경하지 않았다.

> 후속 문서 동기화 요청에 따라 아래 **startup 조합표와 LC 구현 분담을 검토·수락했다.** 이전 인수본의 상세 표 수락 대기 상태는 이 기록으로 대체한다. B 코드 착수/C 도구/배포/활성화는 승인되지 않았다. 구현 계획은 [LC B 계획](../codex/LC_BILLING_WORKLOAD_JWT_B_IMPLEMENTATION_PLAN.md)을 따른다.

- 작성일: 2026-10-13. LC 기존 설정은 읽기 확인했다. 신규 명칭은 제안이며 LC 설정·코드·배포를 변경하지 않았다.
- 기존 source: LC `src/main/resources/application.yml`의 `app.billing`·`app.attempt-group-events`, BillingSagaConfiguration, 두 SigV4 client. 현재 JWT sender 설정은 발견하지 못했다.

| 목적 | 현재 LC 설정/env | 권장 매핑 |
| --- | --- | --- |
| 두 client의 Billing origin | app.billing.base-url / app.attempt-group-events.billing-base-url → BILLING_BASE_URL | 이름 유지. B 전환 시 JWT 수신 HTTPS origin에 연결. 실제 주소/port는 운영 gate |
| Reservation saga | app.billing.creation-saga-enabled → BILLING_CREATION_SAGA_ENABLED | 업무 flag 유지. JWT 활성화 flag와 구분 |
| phone continuation | app.billing.phone-continuation-enabled → BILLING_PHONE_CONTINUATION_ENABLED | 유지. 인증 전환으로 권한·body 변경 없음 |
| saga reconciliation | app.billing.reconciliation.enabled → BILLING_RECONCILIATION_ENABLED | 유지. 미완료 saga를 일괄 끄는 전환 절차 금지 |
| AttemptGroup 작성/발신 | ATTEMPT_GROUP_EVENT_WRITER_ENABLED / ATTEMPT_GROUP_EVENT_PUBLISHER_ENABLED | 기존 업무 flag 유지. JWT flag와 별개 |
| Reservation timeout | BILLING_CONNECT_TIMEOUT / BILLING_READ_TIMEOUT | 기존 기본 PT2S/PT5S 유지 |
| AttemptGroup timeout | ATTEMPT_GROUP_CONNECT_TIMEOUT / ATTEMPT_GROUP_READ_TIMEOUT | 기존 기본 PT2S/PT5S 유지 |
| JWT 발신 준비 flag | 신규 app.billing.workload-jwt.sender-enabled → BILLING_WORKLOAD_JWT_SENDER_ENABLED | 기본 false. 두 client 공통 인증 준비 flag 후보. 업무 발신 ON인데 JWT 준비 OFF면 기동 오류 제안. SigV4/무인증 fallback 없음 |
| trust 환경 | 신규 app.billing.workload-jwt.environment → BILLING_WORKLOAD_JWT_ENVIRONMENT | test/staging/production exact. payment SANDBOX/PRODUCTION과 구분 |
| LC 서명키 | 신규 app.billing.workload-jwt.private-jwk → BILLING_WORKLOAD_JWT_PRIVATE_JWK | Secret 주입, 기본 빈 값. ON에서 LC RSA key 검증, OFF에서 읽지 않음. Identity/user 키 재사용 금지 |
| issuer/sub/audience·TTL/skew | 신규 운영 override 없음 | 환경으로 A 명세의 exact issuer·두 audience 도출, sub 고정. TTL 60초·skew 30초 고정. LC exact 수락 필요 |
| 기존 SigV4 region | BILLING_AWS_REGION | B 승인 범위의 두 signer 의존성 제거 대상. 다른 AWS 사용처는 별도 확인하며 현재 설정 삭제 안 함 |

`BILLING_WORKLOAD_JWT_SENDER_ENABLED`는 인증 방식 선택 옵션이 아니다. B 전환 버전에서 SigV4 또는 무인증으로 자동 전환하지 않는다. 실제 활성 사용처/진행 중 작업을 확인하고 수신 준비 후 발신 활성화하며, legacy drain·롤백과 기동 오류의 영향은 B 범위 승인 전에 양측 수락한다.

LC가 위 신규 세 명칭과 공통 flag 의미를 수락하거나 실제 프로젝트 명칭을 제시하면 정본에 동기화한다. LC 회신 전에는 설정 명칭 매핑을 공동 확정으로 보고하지 않는다. 공동 fixture의 claim 문자열과 설정 이름을 혼동하지 않는다.

## Startup 조합표 — LC 검토·수락, 2026-10-13

J=JWT sender-enabled, S=creation-saga-enabled, H=phone-continuation-enabled, R=reconciliation.enabled, W=AttemptGroup writer-enabled, P=publisher-enabled. 초기 기본값은 모두 OFF. 아래 규칙은 B 구현 목표이며 현재 runtime 성공을 뜻하지 않는다.

### 우선 적용하는 거절 규칙

| 조건 | 결과 |
| --- | --- |
| H ON / S OFF | 기존 phone→saga 의존으로 거절 |
| R ON / S OFF | 기존 recovery→saga 의존으로 거절 |
| J OFF / (S ON 또는 P ON) | private JWK 파싱 전에 flag 오류로 거절 |
| J ON / env·private key·kid·RSA 유효성 또는 self sign/verify 오류 | 기동 거절, 비밀 원문 출력 금지 |
| 위 오류 없고 필요한 기존 DB/index/lease/alert/timeout 조건 충족 | 허용, 실제 발신은 S/P 등 기존 업무 flag를 따름 |

첫 세 boolean 규칙을 64가지에 적용하면 **22허용/42거절**이다. LC에서 독립 규칙 계산으로 확인했으며 ApplicationContext 테스트 결과는 아니다. `P→W` 의존성을 추가하지 않는다.

| 용도 | J | S | H | R | W | P | 조건 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 전체 비활성 | OFF | OFF | OFF | OFF | OFF | OFF | private JWK 미파싱 |
| 키 준비만 | ON | OFF | OFF | OFF | OFF | OFF | 키 self-check, 발신 없음 |
| 시험 생성 | ON | ON | OFF | OFF | OFF | OFF | HTTPS origin·timeout·saga 조건 |
| 생성+phone | ON | ON | ON | OFF | OFF | OFF | continuation 조건 유지 |
| 생성+복구 | ON | ON | OFF | ON | OFF | OFF | recovery DB/index·lease·alert 유지 |
| 적재만 | OFF | OFF | OFF | OFF | ON | OFF | writer Transaction/index 필요, 발신 없음 |
| 적재+발신 | ON | OFF | OFF | OFF | ON | ON | writer/publisher 각각 조건 |
| 기존 outbox drain | ON | OFF | OFF | OFF | OFF | ON | publisher index/gate/lease/HTTP 필요, writer Transaction 불필요 |
| 전체 연동 | ON | ON | ON | ON | ON | ON | 모든 기존 조건 |

J ON은 exact environment와 LC 전용 RS256 private JWK(RSA≥2048/nonblank kid/유효키)를 검증하고 로컬 public 성분으로 self sign/verify한다. J OFF는 private JWK를 읽거나 파싱하지 않는다. 로컬 self-check와 Billing 배포키 일치는 별개이며 원격 fetch 없이 배포 manifest의 kid/n/e 대조·서명 smoke로 후자를 활성화 전에 확인한다. 실제 자료를 기록하지 않는다.

J ON/S OFF/P OFF는 origin 없이 키 준비만 허용한다. S 또는 P ON이면 동일한 HTTPS Billing origin과 해당 client의 양수 timeout을 검증한다. 기존 PT2S/PT5S 기본값과 retry/lease/alert 계약은 유지한다.

### Publisher 단독 ON의 index 준비

현재 `AttemptGroupEventIndexValidator`가 W에서만 실행되는 것은 확인된 구현 격차다. B에서 **W OR P**로 검증 경계를 확대한다. P 단독 ON도 다음 기존 index의 key 순서/options를 검증하고, 검증 실패·DB 오류·충돌이면 scheduler가 claim/send하기 전에 fail-closed한다.

| collection | 필수 index |
| --- | --- |
| attempt_group_event_outbox | `_id` unique (event ID) |
| attempt_group_event_outbox | `uq_attempt_group_session_slot`: sessionId ASC + eventSlot ASC, unique |
| attempt_group_event_outbox | `ix_attempt_group_claim`: status ASC + nextAttemptAt ASC + leaseUntil ASC + _id ASC |
| attempt_group_event_outbox | `ttl_attempt_group_outbox`: expiresAt ASC, expireAfterSeconds=0 |
| attempt_group_publisher_state | `_id` unique, 기존 singleton gate/lease CAS용 |

기존 startup ensure 동작과 명시적 migration 경계를 유지하되, 이름만 비교해 잘못된 option을 통과시키지 않는다. 기존 index의 drop/recreate·데이터 정리·schema 변경은 B에 포함하지 않는다. 필요한 기존 index가 없을 때 격리 테스트에서 기존 ensure 동작을 검증하고, 실DB 준비/수정은 별도 승인·rollout으로 다룬다.

W OFF/P ON은 기존 PENDING/회수 가능한 IN_FLIGHT 및 기존 auth circuit 정책에 따른 처리만 수행한다. 새 event/Session을 생성하거나 DEAD_LETTER를 자동 requeue하지 않는다. writer Transaction bean이 없어도 publisher context/기존 event 발신/lease 경쟁을 검증한다. 실제 ApplicationContext·Mongo drain·index 검증은 B에서 수행할 미완료 항목이다.
