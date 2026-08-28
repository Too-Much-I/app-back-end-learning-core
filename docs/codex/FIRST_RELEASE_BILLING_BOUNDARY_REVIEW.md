# 1차 업데이트 Billing·Entitlement 경계 검토

- 검토일: 2026-08-20
- 1차 범위: SNS를 포함한 로그인, 결제, 검증된 휴대전화 번호당 무료 모의고사 1회
- 관련 Identity Jira: `TMI-90`, `TMI-95`, `TMI-98`
- Billing/Learning Core 후속 Jira: 별도 키 미제공

## 1. 결론

Billing 서버는 1차 업데이트 완료 뒤 만드는 후속 서버가 아니다. 결제와 무료 모의고사 1회를 1차 production 범위에 포함한다면 **Billing/Entitlement consumer가 1차 업데이트의 선행·병렬 의존성**이다.

초기에는 다음을 서로 다른 배포 서비스 두 개로 나누지 않고 하나의 `Billing/Entitlement` bounded context와 서비스로 구현하는 편이 적절하다.

- 주문·결제 시도·PG webhook·환불
- 유료 사용권 원장
- Identity `PhoneEligibilityBinding` consumer
- 검증된 휴대전화 번호당 `TrialClaim` unique 원장
- 무료·유료 `UserEntitlement`
- 시험 사용권 `reserve / confirm / cancel / reconcile`
- user merge 시 entitlement 소유권 이전

규모나 조직 소유권이 실제로 달라질 때 Billing과 Entitlement를 나눌 수 있지만, 첫 릴리스부터 분리하면 결제 성공과 권한 지급 사이의 분산 일관성만 늘어난다.

Firebase/Identity Platform에서 말하는 `billing`은 Kakao Generic OIDC 등 Firebase 기능 사용료 설정이고, 앱의 결제·사용권을 소유하는 Billing 서버와 다른 개념이다.

## 2. 서비스별 책임

| 책임 | 서비스 |
| --- | --- |
| SNS credential 검증, 회원가입, 로그인, canonical userId, verified phone | Identity |
| phone eligibility candidate와 binding revision event | Identity |
| 인앱결제 거래 검증·store notification·환불·결제 원장 | Billing/Entitlement |
| 무료 1회 중복 방지 `TrialClaim` | Billing/Entitlement |
| 무료·유료 시험 사용권과 reservation | Billing/Entitlement |
| 시험 생성·진행·채점·결과 | Learning Core |
| 앱 로그인 UI·Apple/Google 인앱결제 SDK와 결제 UI | Client |

Identity는 `TrialClaim`, `UserEntitlement`, 시험 사용권과 결제 상태를 저장하지 않는다. Learning Core도 phone eligibility candidate, 결제 원장이나 무료 혜택 중복 방지 원장을 저장하지 않는다.

## 3. 권장 구현 순서

```text
1. Identity 로그인·가입과 verified phone 계약
2. Billing/Entitlement 기본 서비스와 데이터 원장
3. Identity PhoneEligibilityBinding consumer
4. 무료 TrialClaim + Entitlement reserve/confirm
5. Learning Core 시험 생성 gate 연동
6. PG 결제·webhook + paid entitlement grant
7. UserMerged entitlement 이전
8. staging E2E·reconciliation·운영 활성화
```

SNS 로그인 구현을 전부 끝낸 뒤 Billing을 시작할 필요는 없다. API와 event 계약을 먼저 동결한 뒤 1~4를 병렬 진행하는 것이 맞다. Identity 기존 계약상 eligibility consumer가 준비되지 않으면 production signup flag도 열면 안 된다.

## 4. 무료 모의고사 1회 흐름

현재 Identity 계약의 무료 정책은 userId당 1회가 아니라 **검증된 휴대전화 번호당 1회**다. raw phone이나 Identity 내부 PhoneIdentity fingerprint를 Billing에 전달하지 않고 consumer-scoped candidate만 전달한다.

이는 실제 자연인당 1회를 완전히 보장하는 KYC가 아니다. 한 사람이 여러 번호를 보유하거나 번호가 재할당되는 경우의 abuse·보존 정책은 별도다. 1차 요구사항의 “인당 1회”가 검증 번호당 1회를 의미하는지 제품 문구로 확정해야 하며, 실제 자연인 기준이 필요하면 현재 범위를 넘어서는 추가 본인확인 수단이 필요하다.

```text
회원가입/phone 변경
→ Identity PhoneEligibilityBindingVerified event
→ Billing inbox + revision high-water + current binding commit

사용자가 첫 시험 생성 요청
→ Learning Core가 JWT sub의 canonical userId 확인
→ Billing reserve 요청
→ Billing이 current binding과 retained candidate를 확인
→ TrialClaim unique + 무료 Entitlement + reservation을 원자 처리
→ Learning Core가 시험 Session 생성
→ 성공 시 Billing confirm
→ 실패 시 cancel
→ timeout/프로세스 종료는 reconciliation으로 수렴
```

binding event 수신만으로 무료 사용권을 미리 지급하지 않는다. 실제 첫 시험 요청에서 silent claim하고 `TrialClaim` unique constraint로 중복을 막는다.

클라이언트가 `userId`, 무료 여부, 결제 완료 여부나 entitlement 수량을 Learning Core에 보내게 하지 않는다. 서비스 간 인증된 호출에서 서버가 canonical userId와 reservation을 다룬다.

## 5. 유료 결제 흐름

```text
Client → Apple/Google: 인앱결제
Client → Billing: store transaction proof 전달
Billing → Apple/Google server API: 거래 검증
Apple/Google → Billing: server notification
Billing: notification eventId/store transaction id 멱등 처리
Billing: payment CAPTURED + paid Entitlement grant commit
Learning Core → Billing: 시험 시작 reserve
Learning Core: Session 생성
Learning Core → Billing: confirm 또는 cancel
```

클라이언트 성공 화면이나 client receipt만으로 권한을 지급하지 않는다. Billing의 Apple/Google server 검증과 검증된 server notification을 단일 진실 공급원으로 사용한다. 웹 checkout과 웹 PG는 현재 범위에 포함하지 않는다.

시험 생성 API의 기존 Request Body 없음 계약을 유지하려면 Learning Core 내부에서 reserve를 수행한다. 성공 Response DTO는 유지하고, 권한 없음·결제 처리 중·Billing 일시 장애의 오류 code와 retry semantics는 별도 계약으로 확정한다.

## 6. `UserMerged`와 Billing

Learning Core `UserMerged` consumer 계획은 source 학습 데이터를 target으로 옮기고 source JWT를 차단한다. 무료/유료 사용권은 Learning Core DB에 없으므로 이 계획만으로 Billing 권한은 이전되지 않는다.

Billing/Entitlement도 다음 merge 처리가 필요하다.

- source reservation·entitlement를 target canonical owner로 이전 또는 합산
- source와 target의 TrialClaim을 삭제하거나 다시 지급하지 않음
- 이미 사용한 phone candidate의 abuse/claim ledger 유지
- paid entitlement 수량·환불 관계 보존
- source actor의 Billing 요청 차단
- eventId 멱등성과 source/target 동시 쓰기 직렬화

Identity `UserMerged`를 Billing에도 전달하는 fan-out/subscription 계약이나 Billing 전용 event delivery가 별도 필요하다. 현재 Learning Core consumer 계획에는 이 Billing consumer가 포함돼 있지 않다.

## 7. 현재 `UserMerged` 계획의 저장소별 변경 범위

### Learning Core 저장소

현재 최종 구현 계획의 애플리케이션 코드 변경 주 대상이다.

- Mongo Transaction과 ownership guard
- 기존 시험 writer·Callback 전환
- internal `UserMerged` endpoint와 workload 인증
- inbox와 세 ownership collection migration
- migration script, 테스트, 관측과 runbook

### Identity 저장소

현재 Learning Core 계획이 Identity 애플리케이션 코드를 직접 수정한다는 뜻은 아니다. 다만 production 연동에는 Identity 측 작업이 남는다.

- C8 final target/source 불변식과 C9 status/retry를 인계서에 반영
- workload credential 발급 방식과 audience/principal 확정
- publisher endpoint·timeout·feature flag 설정
- staging duplicate/response-loss E2E
- 필요 시 현재 publisher가 확정 계약을 충족하지 않는 부분 보완

Identity의 `UserMergedOutbox`와 publisher가 이미 계약을 충족하면 코드 변경 없이 문서·설정·E2E만 필요할 수 있다. 실제 차이는 구현 착수 전 Identity 코드와 확정 계약을 다시 대조해 결정한다.

### 인프라

- private HTTPS ingress, TLS 종료와 network allowlist
- workload identity issuer/JWKS/audience/principal
- Learning Core Mongo Transaction 지원
- metric/alert와 publisher kill switch

### Client

`UserMerged` consumer 자체는 client 변경 없이 투명하게 동작해야 한다. 결제·무료시험 UX에는 로그인, 결제 시작/완료, 권한 부족·처리 중 오류 표현이 별도 필요하다.

### Billing/Entitlement 저장소

현재 존재 여부와 무관하게 별도 서비스/Jira 범위다. Learning Core `UserMerged` 계획에 포함되지 않는다.

## 8. 1차 업데이트를 위한 권장 작업 묶음

### Track A — Identity/Auth

- Firebase email/password·Google·Apple과 조건 충족 시 Kakao
- phone proof·signup·Guest upgrade/merge
- PhoneEligibilityBinding publisher
- UserMerged publisher

### Track B — Billing/Entitlement

- service skeleton과 workload authentication
- binding consumer/inbox/revision
- TrialClaim·Entitlement·Reservation 원장
- Apple/Google 거래 검증·server notification·refund/revoke
- UserMerged consumer

### Track C — Learning Core

- UserMerged consumer 최종 계획 구현
- 시험 생성 전 Billing reserve, 생성 후 confirm/cancel
- Billing 장애와 reconciliation 계약
- 기존 공개 시험 API 호환 테스트

### Track D — Client/Infra

- Firebase SDK와 provider login/link
- 결제 UI와 Apple/Google 인앱결제 SDK
- workload/network/secretless credential
- staging E2E와 kill switch

1차 릴리스는 네 track의 production gate를 모두 통과해야 완료다. SNS 로그인이 동작한다는 이유만으로 signup, 무료시험 또는 결제를 부분 활성화하지 않는다.

## 9. 다음에 먼저 확정할 계약

1. Billing/Entitlement는 새로운 하나의 배포 서비스로 시작하기로 확정
2. 10 credits=시험 1회, 5천원=5 credits, 1만원=10 credits, 3만원=3일 무제한+3일 출석 시 1일 연장, 5만원=100 credits와 첫 구매 2배·연속 로그인·추천·coupon 보상을 기본 상품으로 확정
3. entitlement 단위와 만료·환불 시 회수 정책
4. reserve TTL, confirm/cancel과 reconciliation 규칙
5. 시험 Session commit과 reservation confirm 사이 장애 처리
6. Apple/Google notification event의 멱등 key와 결제 상태 전이
7. UserMerged fan-out과 Billing merge 충돌 정책
8. 무료 TrialClaim·abuse ledger 보존 기간
9. “인당 1회”는 검증된 휴대전화 번호당 1회로 확정

1·2·9의 세부 결정과 3~8 권장안·남은 product 질문은 `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`에 기록했다. 이를 승인하면 Billing과 Learning Core를 병렬 구현할 수 있다.

## 10. 확정 계약이 기존 구현 계획에 미치는 영향

### 10.1 Learning Core

기존 `USER_MERGED_CONSUMER_IMPLEMENTATION_PLAN.md`는 학습 데이터 owner 이전만 다루므로 그 계약을 Billing 코드까지 넓히지 않는다. 대신 별도의 Learning Core–Billing 연동 구현 계획이 필요하다.

현재 `ExamServiceImpl.createExamSession()`은 곧바로 `ExamSessionManager.startNew()`를 호출하고, `startNew()`은 기존 진행 중 Session을 abandon한 뒤 새 Session을 insert한다. 결제 적용 뒤에는 이 경계를 다음 saga로 바꿔야 한다.

```text
examCreationOperationId 생성/복원
→ Billing reserve
→ Learning Core Transaction에서 기존 활성 Session 정책 + 새 ExamSession + reservation metadata commit
→ Billing confirm
→ 성공 응답
```

추가 계획에 포함할 항목:

- Billing client port와 workload authentication, timeout·retry·circuit breaker
- `ExamSession` 내부 `reservationId`, `examCreationOperationId`, entitlement source metadata
- confirm/cancel durable retry 또는 reconciliation job
- 동일 operation 재요청 시 abandon·새 insert·재차감을 반복하지 않는 멱등성
- 필수 UUID v4 `Idempotency-Key` header와 기존 body/response 비변경 계약 반영
- insufficient entitlement, binding processing, Billing unavailable의 오류 code와 retry semantics
- reserve 성공 후 Session rollback, commit 결과 불명, confirm timeout과 프로세스 재시작 테스트
- `UserMerged`와 reserve/confirm 동시 실행 시 source 차단과 Billing owner 수렴 E2E

CreditGrant, store 거래, TrialClaim, streak, 추천과 coupon 원장은 Learning Core에 추가하지 않는다.

### 10.2 Identity

`PhoneEligibilityBinding` producer/publisher는 이미 Billing을 consumer owner로 전제하므로 상품 가격·Apple/Google 인앱결제·추천 보상 때문에 신규 Identity 도메인이나 payload 필드를 추가하지 않는다. 첫 구매·추천·무료시험 unique는 같은 consumer-scoped candidate를 사용하는 Billing 원장이 판정하며 로그인 streak도 Identity 로그인 event가 아니라 Billing daily check-in으로 처리한다.

반면 현재 `UserMergedPublisherProperties`는 endpoint와 audience가 각각 하나이고 `UserMergedOutbox`도 event 전체에 delivery status 하나만 가진다. Learning Core와 Billing이 각각 성공·실패·재시도 상태를 가져야 하므로 production 전에 다음 중 하나가 필요하다.

1. Identity에 consumer별 delivery record `(eventId, consumerId)`와 endpoint/audience allowlist를 추가 — 현재 direct HTTPS 방식의 권장안
2. Identity는 durable fan-out broker 한 곳에 publish하고 broker가 두 consumer에 독립 delivery — 새 broker가 승인될 때만 선택

Learning Core 성공을 Billing 성공으로 간주하거나 한 consumer가 다른 consumer로 event를 전달하는 체인은 허용하지 않는다. Identity 쪽 실제 코드 변경은 이 fan-out 확장과 환경별 endpoint/audience, workload credential 설정이며, phone eligibility payload와 사용자 공개 API는 변경하지 않는다.

### 10.3 별도 계획·Jira 경계

- Learning Core `UserMerged` consumer: 기존 계획 유지
- Learning Core Billing reserve/confirm/reconcile: 신규 구현 계획·Jira 필요
- Billing/Entitlement 서버와 Apple/Google adapter: 신규 서비스 계획·Jira 필요
- Billing `PhoneEligibilityBinding` consumer와 `UserMerged` consumer: Billing 계획에 포함
- Identity `UserMerged` multi-consumer fan-out: Identity 후속 Jira 필요
- Identity phone eligibility publisher: 코드 재설계보다 설정·staging E2E가 우선이며 계약 차이가 발견될 때만 보완

## 11. 권장 실행 순서

전체 작업을 Identity → Billing → Learning Core 순서로 완전히 직렬화하지 않는다. 내부 계약을 먼저 고정하고 각 서비스는 feature flag OFF 상태로 병렬 구현한 뒤 consumer부터 순서대로 활성화한다.

### Phase 0 — 계약과 Jira 동결

코드를 수정하기 전에 다음 계약을 함께 확정한다.

1. Identity → Billing `PhoneEligibilityBinding` v1 endpoint/audience와 scope
2. Identity → Learning Core/Billing `UserMerged` consumer별 delivery 상태와 인증
3. Learning Core → Billing `reserve/confirm/cancel/status/reconcile` API
4. Apple/Google transaction 검증·notification의 내부 payment 상태 mapping
5. 필수 `Idempotency-Key`, Billing 오류 code와 client retry semantics의 앱·Learning Core rollout

Jira는 최소 Identity fan-out, Billing service, Learning Core `UserMerged`, Learning Core Billing 연동, Client IAP, staging E2E로 나눈다.

### Phase 1 — feature flag OFF 병렬 구현

- Identity: `UserMerged` consumer별 delivery record·endpoint/audience allowlist 구현
- Billing: service skeleton, workload 인증, phone binding consumer, entitlement ledger와 reservation 구현
- Learning Core: 기존 `UserMerged` 계획 구현과 별도 Billing 연동 계획 작성·stub/contract test 준비
- Client: Apple/Google product와 SDK·transaction proof 전달 계약 준비

Identity 코드를 먼저 병합할 수는 있지만 Billing endpoint가 없는 production에서 publisher를 활성화하지 않는다.

### Phase 2 — Billing 핵심과 Learning Core 연동

1. Billing의 TrialClaim, credits/pass, reserve/confirm/cancel과 reconciliation 완성
2. Learning Core에 operation 멱등성, Session reservation metadata와 durable confirm/cancel reconciliation 구현
3. Billing `UserMerged` consumer와 source write 차단 구현
4. 서비스별 단위·Transaction·동시성·계약 테스트

Learning Core는 Billing 구현 완료 뒤 계획을 처음 작성하지 않고 Phase 0~1에 API 계약과 실패 의미를 먼저 확정해 mock Billing으로 개발한다.

### Phase 3 — Apple·Google 인앱결제

- Apple/Google server-side 거래 검증 adapter
- server notification inbox 멱등성
- capture/refund/revoke와 CreditGrant/pass Transaction
- 첫 구매 2배와 추천 양방향 지급
- sandbox/license tester E2E

### Phase 4 — staging 활성화 순서

```text
Billing·Learning Core consumer endpoint 배포 (feature OFF)
→ workload credential/TLS/network 검증
→ Identity PhoneEligibility publisher → Billing 활성화
→ 무료시험 reserve/confirm E2E
→ Identity UserMerged의 Learning Core·Billing delivery 활성화
→ merge·중복·timeout·response loss E2E
→ Apple/Google sandbox 결제·환불 E2E
→ Learning Core Billing enforcement canary
→ 마지막에 signup/Guest merge production flag 활성화
```

consumer가 하나라도 준비되지 않았거나 reconciliation backlog·dead-letter가 정상 범위를 벗어나면 producer/merge flag를 열지 않는다.

## 12. staging·production 배포 환경 분리

현재 `tosunsaeng-staging-cluster`는 업데이트 전 통합 검증 환경으로 유지하고, 실제 사용자 트래픽은 신규 `tosunsaeng-prod-cluster`에서 처리한다. staging 클러스터를 운영으로 전환한 뒤 별도 test 클러스터를 만드는 방식은 이름·설정·운영 이력의 혼선을 만들므로 채택하지 않는다.

클러스터 분리만으로는 장애·데이터 오염 범위가 격리되지 않는다. 최소한 다음 리소스와 설정을 환경별로 구분한다.

- ECS service, task definition revision, target group, autoscaling과 CloudWatch log group
- 도메인·ALB listener/routing과 환경별 workload audience/credential
- Secrets Manager 또는 Parameter Store 경로와 ECS task role
- MongoDB database와 database user; staging과 production의 mutable data·credential 공유 금지
- Redis instance가 최선이며 초기 비용상 공유가 불가피하면 ACL·credential·key namespace를 강하게 분리하고 production 전용 전환 계획 보유
- S3 bucket이 최선이며 prefix 분리를 택하면 IAM policy로 상대 환경 prefix 접근을 차단
- Apple/Google sandbox·license tester credential과 production credential/product configuration
- Sentry environment, alarm, dashboard, backup·retention 및 production deletion protection

초기 비용을 줄이기 위해 같은 AWS account와 VPC를 사용할 수는 있다. 이 경우에도 cluster·service·target group·security group 규칙·task role·secret·data store logical boundary를 명시적으로 나눈다. 결제·개인정보의 blast radius를 더 강하게 격리할 시점에는 production AWS account 자체를 분리한다.

배포 artifact는 staging에서 검증한 immutable image digest를 production에 승격한다. 환경별로 다시 빌드한 이미지를 배포하지 않는다.

```text
develop/release candidate image digest
→ tosunsaeng-staging-cluster
→ Identity/Billing/Learning Core + Apple/Google sandbox E2E
→ 동일 image digest와 승인된 production configuration
→ tosunsaeng-prod-cluster canary/health check
→ production feature flag 단계적 활성화
```

production 활성화 전에는 backup/restore rehearsal, rollback, alarms, least-privilege task role, Billing reconciliation backlog와 `UserMerged` consumer별 delivery 상태를 확인한다. AWS Console 링크는 이 검토 환경에서 로그인 화면으로 전환되어 기존 cluster의 service·capacity provider·VPC 구성은 확인하지 못했으므로, 실제 생성 전에 해당 항목을 별도 inventory로 확정한다.

### 12.1 브랜치별 자동 배포

브랜치와 환경은 다음처럼 고정한다.

| Git 기준 | 배포 환경 | ECS cluster | 권장 보호 장치 |
|---|---|---|---|
| `develop` push/merge | staging | `tosunsaeng-staging-cluster` | test 성공 후 자동 배포 |
| `main` PR merge | production | `tosunsaeng-prod-cluster` | protected branch, test 성공, production environment 승인 또는 즉시 자동 배포 |

현재 `.github/workflows/deploy-staging.yml`은 `main` push에서 staging을 배포하므로 production 분리 시 trigger를 `develop`로 변경해야 한다. production은 별도 workflow와 GitHub Environment를 사용하고 staging용 role·URL·cluster/service·secret을 재사용하지 않는다. 두 workflow가 같은 이름의 repository variable을 공유하기보다 `staging`과 `production` GitHub Environment에 각각 환경별 variable/secret과 OIDC role을 둔다.

간단한 1차 방식은 각 브랜치에서 test→image build→해당 환경 배포를 실행하는 것이다. 더 강한 승격 보장이 필요하면 staging에서 검증한 release candidate의 ECR image digest를 기록하고 `main` merge 후 production workflow가 그 digest를 그대로 배포한다. production에서 같은 source를 다시 build하면 소스가 같아도 staging에서 검증한 binary와 완전히 동일하다는 보장은 약해진다.

production 자동 배포의 전제는 `main` 직접 push 금지, 필수 PR review·CI status check, workflow concurrency, ECS stability/health check 실패 처리와 직전 task definition rollback 절차다. Identity·Billing·Learning Core는 저장소별 workflow를 갖더라도 같은 release gate와 호환 가능한 계약 버전 순서로 배포한다.
