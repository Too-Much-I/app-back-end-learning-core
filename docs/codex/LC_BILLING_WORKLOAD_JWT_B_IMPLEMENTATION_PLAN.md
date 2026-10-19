# LC–Billing workload JWT — LC B 구현 준비 계획

- 날짜: 2026-10-13 / 관련: TMI-116·TMI-128·TMI-122
- 상태: 문서 동기화·범위 수락 이후 사용자 「어 해줘」로 **LC B 코드·테스트 구현 승인 접수**. C 도구·배포/활성화 제외. 아래 최초 준비 계획의 실행 결과는 [B 검증 기록](LC_BILLING_WORKLOAD_JWT_B_VALIDATION.md)을 따른다.
- 근거: 사용자 첨부 Billing B 계획 및 startup 조합표, [A 수락](../contracts/LC-BILLING-WORKLOAD-A-ACCEPTANCE-R1.md), [LC 수락 조합표](../contracts/LC-BILLING-WORKLOAD-SENDER-MAPPING-R1.md), [ADR](../adr/ADR-LC-BILLING-WORKLOAD-JWT.md).

## 1. 5줄 결론

1. LC 담당 범위는 두 Billing client JWT 발신, 공통 key/flag 준비 검증, publisher 단독 index 준비와 관련 테스트다.
2. J/S/H/R/W/P 규칙은 64조합 중22허용/42거절이며 상세 표를 수락한다.
3. **W OFF/P ON은 기존 outbox drain용으로 허용하며 writer Transaction을 요구하지 않는다.**
4. **index 검증은 W OR P에서 수행하고 성공하기 전 publisher 발신을 허용하지 않는다.**
5. A 규격 및 fixtures-1 bytes/hash는 유지하고 승인된 LC 코드·테스트만 구현한다.

## 2. 반드시 유지할 경계·담당

| 담당 | 후속 B 범위 |
| --- | --- |
| LC | Reservation/phone/status/reconciliation 발신과 AttemptGroup 발신의 JWT 전환, startup/index/실제 sender 테스트 |
| Billing | verifier registry·exact 인가·독립 receiver flags·공유 TLS·구 IAM permitAll/다른 port 우회 차단, 실제 HTTP chain 테스트 |
| 양측 | 같은 fixture/hash와 HTTP27 결과 대조, 키 배포 manifest 일치·준비 상태/운영 gate는 별도 단계 |

여섯 POST의 body/성공DTO/업무 오류/operationId/Idempotency-Key/eventId/digest·무료권/owner/TTL/Session/terminal을 변경하지 않는다. 공개 앱 API·BaseResponse·retryCount·AI user_id=examId·S3/Redis 계약을 유지한다. 새 Mongo schema, 공개 repair API, C requeue 도구는 추가하지 않는다. Billing/Identity 저장소는 LC 작업에서 수정하지 않는다.

## 3. LC가 수락한 상세 구현 단위

### LC-B1 — 설정·키 준비·startup

- A에서 확정한 sender-enabled/environment/private-jwk 명칭과 default OFF/빈 값. 임의 issuer/audience/TTL override나 SigV4/무인증 fallback은 없다.
- [startup 조합표](../contracts/LC-BILLING-WORKLOAD-SENDER-MAPPING-R1.md)의 거절 우선순위와 허용 사례를 적용한다. H/R→S 유지, J OFF+(S OR P) 거절, W는 독립. 모든 기존 recovery DB/Transaction/lease/Sentry alert 조건 유지.
- J ON에서 LC private JWK의 kid·RS256 RSA 유효성 및 self sign/verify, J OFF 미파싱. 준비만 ON이면 발신하지 않는다. 런타임 private-key parser의 상세 필드/부정 사례는 A 공개키/claim 계약을 바꾸지 않는 B 테스트로 작성한다.
- S/P ON은 동일 HTTPS origin, userinfo/query/fragment 금지, origin path 제한과 기존 timeout 양수 검증. 현재 local HTTP 허용 validator와 SigV4 region 검증은 JWT 발신 목표에 맞게 변경하되 외부 fixture/mock 테스트는 실제 AWS 없이 구성한다.
- S3용 AWS provider/SDK는 보존한다. BILLING_AWS_REGION의 두 signer 관련 검증/사용처만 제거 대상으로 inventory하며 다른 AWS 사용처를 일괄 삭제하지 않는다.

### LC-B2 — 두 client의 실제 JWT 전송

- Reservation과 AttemptGroup interface/업무 조립은 유지하며 두 SigV4 transport를 JWT signer로 교체한다. 실제 HTTP 시도 직전에 fresh token/jti, A의 issuer/sub/목적aud/시간으로 발급한다.
- 원 JSON bytes·Idempotency-Key·canonical payload/digest/eventId는 유지한다. retry/backoff/circuit·status-first·lease/owner/terminal 조건을 바꾸지 않는다.
- traceparent CLIENT span 및 attempt별 새 span, baggage 금지, redirect 금지·응답 크기 제한·connect/read PT2S/PT5S 기본값 유지.
- runtime sender는 test-only oracle에 의존하지 않는다. token/Authorization/private key/body 원문을 exception·log·trace·test report에 출력하지 않는다.

### LC-B3 — Publisher-only drain/index 준비

- W OFF/P ON을 금지하는 P→W 검증을 추가하지 않는다. writer의 MongoTransactionManager/TransactionOperations를 publisher 필수 dependency로 만들지 않는다.
- `AttemptGroupEventIndexValidator`를 W OR P의 준비 검증으로 확대한다. outbox의 기존 unique/claim/TTL와 state `_id` 조건은 조합표에 정확히 기록했다. 검증 실패 시 index 이름/고정 사유만 안전하게 알리고 기동/발신을 차단한다.
- 초기 index 점검과 scheduler claim/send 순서를 명시적으로 보장한다. AppContext 생성 타이밍에만 암묵적으로 기대지 않고 준비 전 send=0을 테스트한다.
- 기존 ensure/migration 경계를 유지하며 key/options 불일치 시 index를 자동 drop/recreate하지 않는다. 실DB index 수정·allowlist/backfill·retention 변경은 이번 문서 승인에 포함하지 않는다.
- 기존 PENDING/lease 만료 IN_FLIGHT drain과 circuit 해제 경로 유지. event 생성이나 payload rewrite, DEAD_LETTER 자동 requeue 금지.

### LC-B4 — 검증 및 양측 회신

- 64개 flag 기대값을 data-driven test로 검증: 키/업무/저장소가 정상일 때22허용/42거절. 각 키·origin·timeout·index·lease/alert 부정 조건은 별도 case다.
- 실제 ApplicationContext에서 W OFF/P ON, writer Transaction bean 부재, 필요한 publisher/client/store/gate 존재, P가 OFF일 때 발신 없음과 W OR P index 검증을 확인한다.
- 격리 Mongo replica set에서 필수 index 누락/option충돌/DB 오류, readiness 이전 claim/send 차단, 기존 event 전달·lease 경쟁/응답 유실, 원 eventId/body/digest 유지·새event 미생성 및 DEAD_LETTER 미claim을 검증한다.
- signer 실제 출력은 A의 claim/서명/시간/jti·선택 audience에 맞게 검증하고 재시도마다 새token/같은body인지 검사한다. 실제 signer의 공개 성분과 fixture key의 역할을 구분한다. fixture의 폐기된 private key를 복원하거나 fixture를 재생성하지 않는다.
- fixtures-1 token72/hash1은 그대로 회귀한다. HTTP27 중 Billing chain의 인가/DB무호출은 Billing 담당, LC는 실제 sender/header·error mapping·transport 검증을 담당한다. test-only 모형으로 Billing chain 통과를 주장하지 않는다. 추가 startup/signer case는 별도 파일/revision으로 관리한다.
- `./gradlew clean test`, 필요한 `./gradlew mongoIntegrationTest`, 관련 migration 테스트 및 `git diff --check`를 각 변경 범위에서 실행한다. A의713개 통과는 B 완료 증거가 아니다.
- 401/403 circuit, 빈 ingress404/업무 OPERATION_NOT_FOUND 구분, CONFIRMED/CANCELED/EXPIRED 불변, phone/owner·기존 무료/사용자 인증 회귀와 비로깅을 검증한다.

## 4. 결정·위험·완료 기준

상세 조합표와 LC-B1~4 담당 범위를 수락하고 후속 B 착수 승인을 받았다. 두 client/공통 issuer/index 준비 검증과 테스트를 구현했다. ADR/AGENTS도 승인 범위로 갱신했으며 전체 공동 B 완료와 배포 판정은 별개다.

W/P index 조건 확대는 현재 존재하는 격차의 후속 수정 계획이다. index 이름만 존재해도 안전하다고 판단하지 않는다. 기존 데이터가 unique 조건에 어긋나면 fail-closed하고 별도 운영 판단으로 넘기며 문서 작업에서 데이터를 고치지 않는다.

정적 의존성 확인은 전체 AppContext나 실제 drain 성공 증명이 아니다. legacy sender/진행 중 saga/outbox·origin/key inventory 없이 OFF 기본값을 근거로 배포하지 않는다. 구버전 롤백이 SigV4 또는 인증 우회를 다시 열지 않도록 양측 버전·키·진행 중 증거를 보존한다.

B 완료는 실제 sender/chain·부정/회귀 테스트 및 양측 증거로 판단한다. C 복구 도구·실TLS/DNS/ALB/SG·키 회수 SLA·운영 inventory/E2E·배포·활성화는 별도 승인이다. Billing 준비 확인 후 LC 발신 활성화 순서를 유지한다.

## 5. 부록 — 이번 LC 코드 대조 근거

| 현재 근거 | 확인 사실 / 후속 계획 |
| --- | --- |
| `AttemptGroupEventConfiguration` | W에서만 Transaction manager/template 생성. P 단독에 강제하지 않음 |
| `AttemptGroupOutboxPublisher` | P 조건, TransactionOperations 생성자 의존 없음 |
| `AttemptGroupOutboxStore` / `AttemptGroupPublisherStateStore` | MongoTemplate 단일 document lease/CAS·singleton gate 사용 |
| `AttemptGroupStateCoordinator` / Backfill / SummaryCompletion | ObjectProvider Transaction 의존. 전체 context 검증은 B에서 추가 |
| `AttemptGroupEventIndexValidator` | 현재 W-only, 기존 uq/claim/TTL ensure. B에서 W OR P+준비 순서 검증 |
| `BillingSagaConfigurationValidator` | H→S 및 origin/timeout/region 검사. JWT 준비에 맞게 변경 계획 |
| `ReservationReconciliationStartupValidator` | R에서 saga/transaction/bounded HTTP/Sentry 조건 검사, 유지 |

위 부록은 최초 문서 준비 시점의 코드 대조 기록이다. 후속 B 구현은 W OR P index 검사와 JWT sender로 전환했다. 변경·검증 상세는 B 검증 기록을 따른다. Billing 저장소·실제 DB·배포 변경은 없다.
