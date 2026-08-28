# Billing/Entitlement 상품·사용권 계약 결정서

- 작성일: 2026-08-20
- 상태: B1·B2 기본 상품·결제 채널·P1·P2·P4·B9 확정, B3~B8 일부 운영 정책 승인 필요
- 관련 Identity Jira: `TMI-95`, `TMI-98`
- Billing/Learning Core 후속 Jira: 별도 키 미제공

## 1. 이번에 확정된 사항

### B1. 배포 경계

새로운 하나의 `Billing/Entitlement` 서버로 시작한다.

이 서버가 다음을 함께 소유한다.

- 상품과 서버 기준 가격
- 주문·인앱결제 시도·스토어 server notification·취소·환불 원장
- 유료·프로모션 credit ledger
- unlimited pass
- 무료 1회 `TrialClaim`
- Identity phone eligibility binding consumer
- 시험 `Reservation`
- 출석·연속 로그인 reward
- 추천인·coupon 발급/사용 원장
- `UserMerged` consumer와 entitlement owner 이전

Learning Core와 Identity에 결제 원장이나 credit balance를 복제하지 않는다.

### B2. 기본 상품·보상

#### 시험 비용

```text
모의고사 1회 = 10 credits
```

credit는 음수가 아닌 정수 단위로만 발급·예약·사용한다. 서로 다른 grant source의 credit를 합쳐 10 credits를 만들 수 있다.

#### 유료 상품

| SKU 예시 | 가격 | 기본 지급 |
| --- | ---: | --- |
| `CREDIT_5` | 5,000원 | 5 credits |
| `CREDIT_10` | 10,000원 | 10 credits |
| `UNLIMITED_3D` | 30,000원 | 3일 무제한 |
| `CREDIT_100` | 50,000원 | 100 credits |

5 credits 상품 하나만으로는 시험을 시작할 수 없고, 출석·추천·coupon 또는 추가 구매 credit와 합산해야 한다.

#### 첫 구매 보너스

검증된 휴대전화 번호를 기준으로 **첫 credit 상품 구매 시 credit를 2배 지급**하는 것으로 확정한다.

- `CREDIT_5`: 5 + bonus 5 = 10
- `CREDIT_10`: 10 + bonus 10 = 20
- `CREDIT_100`: 100 + bonus 100 = 200
- `UNLIMITED_3D`: credit 상품이 아니므로 2배 적용 의미가 없음

`UNLIMITED_3D`를 먼저 구매해도 이후 첫 credit 상품 구매에 bonus를 적용한다. 첫 구매 여부는 userId가 아니라 verified phone eligibility 기준으로 한 번만 인정하고 merge·탈퇴·환불로 다시 열지 않는다. base와 bonus는 같은 purchase group으로 연결한다.

#### 3일 무제한

30,000원 상품은 다음 계약의 무제한 시험 사용권으로 확정한다.

- 결제 후 30일 안에 첫 시험 reserve가 발생하면 활성화
- 활성화 시점부터 정확히 72시간 사용
- 활성 기간 중 `Asia/Seoul` 기준 서로 다른 3개 날짜에 Billing daily check-in하면 24시간 한 번 연장
- 같은 상품을 재구매하면 pass를 별도로 보존하고 기존 pass와 자동 병합하지 않음
- 활성 pass가 끝난 뒤 다음 pass를 활성화
- 미활성·미사용 pass만 환불 허용

#### 연속 로그인 reward

```text
day 1..7 = 0, 1, 1, 1, 2, 2, 3 credits
주간 합계 = 10 credits = 시험 1회
```

Identity login/reissue 횟수를 세지 않고 Billing의 인증된 daily check-in API가 `Asia/Seoul` 날짜별 한 번만 지급한다.

아직 확정할 세부사항:

- day 7 다음 날 새 7일 cycle의 day 1로 돌아갈지
- 결석 시 즉시 day 1로 reset할지
- 단순 앱 check-in과 실제 시험 이용 중 어느 것을 출석으로 볼지

권장안은 연속 7일 cycle을 반복하고 day 7 다음 날 새 cycle day 1로 돌아가며, 하루를 놓치면 다음 check-in이 day 1부터 시작하는 것이다.

#### 추천인 code

추천인 code는 다음 계약으로 확정한다.

- 입력자와 추천인에게 각각 10 credits 지급
- 입력자의 verified phone 인증과 첫 유료 인앱결제 `CAPTURED` 후 양쪽에 한 번만 지급
- 검증된 휴대전화 번호당 추천 code 입력 1회
- self-referral 금지
- 같은 device·store account·payment instrument 등 abuse signal은 지급 보류·운영 심사에 사용
- 첫 결제 환불 시 미사용 추천 grant는 revoke하고 이미 사용한 부족분은 adjustment/debt로 기록

#### coupon

coupon code별로 정해진 credits를 지급한다.

coupon에는 최소 다음 서버 속성이 필요하다.

- code digest 또는 안전한 lookup representation
- campaign, grant credits, startsAt, expiresAt
- 전체/사용자별 사용 한도
- verified phone당 사용 한도
- 대상 상품·첫 구매 bonus와의 stacking 규칙
- enabled/revoked 상태

클라이언트가 coupon credit 수량을 보내지 않고 서버 catalog가 결정한다.

### B9. 무료 모의고사 1회 기준

무료 모의고사는 canonical userId당이 아니라 **검증된 휴대전화 번호당 1회**로 확정한다.

- Identity는 raw phone이 아닌 consumer-scoped retained fingerprint candidate를 event로 전달한다.
- Billing이 candidate 기준 `TrialClaim` unique를 소유한다.
- 계정 merge·탈퇴·재가입으로 claim을 되돌리거나 다시 지급하지 않는다.
- 이는 실제 자연인 KYC가 아니므로 여러 번호 보유까지 차단하지는 않는다.

무료 1회는 일반 10 credits로 지급하지 않고 `FREE_EXAM_ONCE` entitlement로 두는 것을 권장한다. 그래야 환불 가능한 paid credit, coupon과 무료 claim을 섞지 않고 번호당 한 번을 명확히 추적할 수 있다.

## 2. B3 권장안 — entitlement 단위·만료·환불

### 2.1 원장 모델

mutable balance 하나만 저장하지 않고 immutable ledger와 grant별 remaining을 둔다.

```text
CreditGrant
- grantId
- userId
- source: PURCHASE | FIRST_PURCHASE_BONUS | LOGIN_STREAK | REFERRAL | COUPON | ADMIN
- originalCredits
- remainingCredits
- grantedAt
- expiresAt nullable
- paymentId/campaignId/referralClaimId nullable
- status

UnlimitedPass
- passId, userId, sourcePaymentId
- duration, activatedAt, expiresAt
- extensionGrantedAt
- status

FreeTrialEntitlement
- trialClaimId, userId
- benefitType=FREE_EXAM_ONCE
- status
```

balance snapshot은 조회 최적화용 projection으로만 두고 ledger 합계와 일치 검증이 가능해야 한다.

### 2.2 사용 우선순위

권장 순서:

1. 만료가 가장 가까운 promotional credits
2. 그다음 오래된 promotional credits
3. 그다음 오래된 paid credits

Reservation은 어떤 grant에서 몇 credits를 hold했는지 allocation을 저장한다. confirm은 같은 allocation을 consume하고 cancel은 정확히 원래 grant로 돌린다.

### 2.3 만료

구매 credit의 만료는 전자상거래·선불성 정책과 표시 의무 검토가 필요하므로 임의 기간을 확정하지 않는다.

권장 방향:

- paid credits: 짧은 임의 만료를 두지 않고 법무·PG 정책 승인 기간 적용
- first purchase bonus: 별도 promotional expiry
- login/referral/coupon: campaign별 expiry
- free exam: 별도 product expiry가 확정되지 않으면 claim 후 사용 가능 상태 유지
- unlimited pass: 활성화 뒤 시간 기반 만료

### 2.4 환불·chargeback

권장 불변식:

- 결제 전액 환불은 연결된 base/bonus grant가 모두 미사용일 때 자동 처리
- 환불 시 남은 first-purchase bonus는 함께 revoke
- 일부 사용 뒤 부분 환불 공식은 법무·PG 정책 승인 후 서버에 고정
- refund가 first-purchase eligibility를 다시 열지 않음
- chargeback으로 이미 쓴 paid value가 회수되면 adjustment/debt를 기록하고 새 reserve를 차단
- 결제·환불 원문 payload를 ledger에 그대로 저장하지 않음

첫 구매 bonus를 먼저 사용한 뒤 paid credits를 전액 환불하는 악용을 막으려면 base와 bonus를 같은 purchase group으로 묶어 refund eligibility를 판단한다.

## 3. B4 권장안 — Reservation

상태:

```text
RESERVED → CONFIRMED
RESERVED → CANCELED
RESERVED → EXPIRED
```

규칙:

- reserve TTL 초기값: 5분
- reserve idempotency key: Learning Core가 만든 `examCreationOperationId`
- 동일 operation/user/product 재요청: 같은 reservation 반환
- 다른 user/payload로 같은 operation key: conflict
- confirm/cancel은 idempotent
- confirmed는 cancel로 되돌리지 않음
- cancel은 confirmed credit를 복구하지 않음
- unlimited pass도 사용량 차감 없이 reservation/usage audit을 생성
- 한 user는 동시에 하나의 active exam creation reservation만 허용하는 방향 권장

현재 Learning Core 코드에는 idempotency key 처리가 없지만 Billing의 2026-08-26 최신 계약은 공개 시험 생성 API의 필수 `Idempotency-Key`를 승인했다. 구현 목표는 다음과 같다.

1. 앱이 필수 lowercase UUID v4 `Idempotency-Key` header를 보내고 Learning Core가 operation ID로 사용 — 확정
2. 같은 key의 transport retry는 같은 Session 결과를 반환
3. 의도적 restart는 새 key·새 examId를 사용하고 기존 Session을 `ABANDONED_RESTARTED`로 종료

Request Body 없음과 기존 성공 Response DTO는 유지한다. 필수 header 추가는 앱·Learning Core 동시 rollout과 구버전 앱 호환 전략이 필요한 외부 계약 변경이다.

## 4. B5 권장안 — Session commit과 confirm 장애

정상 흐름:

```text
1. Learning Core operation ID 생성/수신
2. Billing reserve
3. Learning Core Mongo Transaction에서 ExamSession + reservationId 저장
4. commit 성공 확인
5. Billing confirm
6. confirm 성공 뒤 시험 생성 성공 응답
```

장애 규칙:

- reserve 실패: Session을 만들지 않음
- reserve 성공 + Session 확실한 rollback: cancel
- Session commit 여부 불명: cancel하지 않고 examCreationOperationId/reservationId로 Session 재조회
- Session 존재 + confirm timeout: 같은 reservation confirm 재시도, 사용자에게 중복 Session을 만들지 않음
- Session 없음 + reservation 만료 전: cancel
- confirm 결과 불명: Billing 상태 조회 후 수렴
- expired reservation인데 Session 존재: reconciliation 전용 confirm/repair 정책으로 운영 경보

Learning Core는 `reservationId`, `examCreationOperationId`, entitlement type을 내부 Session metadata로 저장하되 외부 기존 Response DTO에는 추가하지 않는다.

reconciliation은 최소 다음 두 방향을 검사한다.

- Billing `RESERVED`인데 Learning Core Session 있음 → confirm
- Billing `RESERVED`인데 Session 없음 → cancel/expire
- Learning Core Session 있는데 Billing reservation 없음/취소됨 → 사용자 진행 차단과 고심각도 복구

## 5. B6 권장안 — 인앱결제 notification과 결제 상태

결제 채널은 **Apple In-App Purchase와 Google Play Billing만 사용**하는 것으로 확정한다. 현재 범위에 웹 checkout과 웹 PG를 포함하지 않는다. Billing은 `APPLE_APP_STORE`, `GOOGLE_PLAY` provider adapter를 같은 내부 주문·entitlement 원장에 연결한다. 웹 PG 추가는 이번 계약의 예정 범위가 아니며 향후 별도 제품 결정으로만 추가한다. 실제 배포 국가·스토어 정책, 상품 유형과 가격 구간은 출시 직전에 다시 확인한다.

```text
CREATED
→ PENDING
→ CAPTURED
→ PARTIALLY_REFUNDED
→ REFUNDED

CREATED/PENDING → FAILED | CANCELED | EXPIRED
CAPTURED → CHARGEBACK
```

규칙:

- SKU·가격·currency는 Billing server catalog가 결정
- client 성공 화면이나 client receipt만으로 CAPTURED나 credit grant 처리 금지
- Apple/Google server 검증과 검증된 server notification을 단일 진실 공급원으로 사용
- unique `(provider, providerEventId)` inbox
- unique `(provider, providerPaymentId)` payment
- unique internal orderId와 client idempotency key
- 같은 webhook eventId/같은 digest는 2xx no-op
- 같은 eventId/다른 digest는 conflict·보안 경보
- 역순 notification은 현재 store 거래 상태를 재조회하거나 단조 상태 규칙으로 no-op
- CAPTURED 전이와 base/bonus entitlement grant를 Billing local Transaction에서 함께 처리
- notification 응답 유실은 같은 event 재수신으로 수렴

Apple App Store Server API/Notifications와 Google Play Developer API/Real-time Developer Notifications의 transaction ID, signature/token 검증, refund/revoke와 timeout 계약을 provider별 adapter contract로 추가한다.

## 6. B7 권장안 — `UserMerged`와 Billing

Identity는 같은 logical `UserMerged`를 Learning Core와 Billing 각각에 독립적으로 deliver해야 한다. consumer별 delivery 상태를 따로 가져야 하며 Learning Core 성공이 Billing 성공을 대신하지 않는다.

Billing merge Transaction:

```text
source/target billing guard를 결정적 순서로 touch
→ source credit grants owner를 target으로 변경
→ source unlimited/free entitlements owner를 target으로 변경
→ reservation·attendance·referral/coupon claim owner를 target으로 변경
→ payment/refund 원장의 purchaser owner를 target으로 연결하되 원 거래 ID 보존
→ TrialClaim/phone candidate abuse ledger는 삭제·재지급하지 않음
→ source guard MERGED deny
→ inbox PROCESSED
→ commit
```

credit는 합산 balance만 새로 쓰지 않고 기존 grant ledger의 owner를 이전해 출처·만료·환불 관계를 보존한다. 두 사용자에게 unlimited pass가 있어도 삭제하지 않고 각 pass를 보존하며 access evaluator가 현재 유효한 pass가 하나 이상인지 판단한다.

source/target 결제·reserve·reward write도 같은 billing guard를 touch해야 한다. source actor를 target alias로 사용하지 않는다.

## 7. B8 권장안 — TrialClaim·abuse ledger 보존

“검증 번호당 평생 1회”를 정확히 지키려면 claim evidence를 계정 탈퇴 뒤에도 보존해야 한다. 그러나 candidate도 pseudonymous personal data이므로 무기한 보존을 기술 기본값으로 두지 않는다.

권장 정책:

- `TrialClaim`은 user merge·탈퇴·binding revoke로 삭제하지 않음
- raw phone, last4, Identity PhoneIdentity fingerprint는 저장하지 않음
- benefit-scoped retained candidate와 keyVersion, claimedAt, benefitType, source event만 최소 저장
- Identity key rotation의 retained candidate reference가 사라질 때까지 지원
- consumer inbox: Identity ADR 기준 120일
- binding high-water/revoke tombstone: producer 최대 replay window보다 길게
- TrialClaim/abuse ledger: 무료시험 program lifetime 또는 개인정보·법무가 승인한 명시적 최대 기간

법무상 최대 기간을 정해 삭제하면 삭제 뒤 같은 번호가 다시 무료 혜택을 받을 수 있다는 제품 trade-off를 함께 승인해야 한다. 번호 재할당을 새 실제 사용자로 인정할지 기존 claim을 유지할지도 별도 운영 정책이 필요하다.

## 8. 세부 product 결정 상태

### P1. 첫 구매 2배

- credit 상품 3개에만 적용하는가?
- `CREDIT_100` 첫 구매도 200 credits인가?
- 첫 구매 unique 기준을 verified phone으로 하는가?

확정: 모두 예. 첫 구매는 첫 credit 상품 구매를 뜻하며, 그 전에 unlimited pass를 구매했는지는 영향을 주지 않는다.

### P2. 무제한권과 출석

- 첫 reserve부터 72시간인가?
- 3개 KST 날짜 daily check-in이면 24시간 한 번 연장인가?
- 재구매 pass는 별도 보존하고 자동 병합하지 않는가?

확정: 모두 예. 추가로 구매 후 30일 안에 활성화하고 미활성·미사용 pass만 환불한다.

### P3. 연속 로그인

- 7일 cycle 반복인가?
- 결석 다음 check-in은 day 1 reset인가?
- Billing daily check-in을 기준으로 하는가?

미확정. 구현 전 승인 필요.

### P4. 추천인

- 입력자와 추천인 모두 10 credits인가?
- 입력자의 verified phone + 첫 CAPTURED 결제 뒤 지급인가?
- phone당 추천 code 입력 1회인가?

확정: 모두 예. self-referral과 abuse signal에 대한 지급 보류도 적용한다.

## 9. 구현 착수 전 외부 결정

- Apple·Google store product ID, 상품 유형과 실제 가격 구간
- paid/promotional credit 만료
- 부분 사용 후 환불 공식과 chargeback
- 필수 `Idempotency-Key` header의 앱·Learning Core rollout 계획
- coupon stacking/한도/만료
- TrialClaim 법무·개인정보 보존 기간
- 번호 재할당 정책

위 결정을 채운 뒤 Billing API, entity와 Learning Core 연동 최종 구현 계획을 작성한다.

## 10. `Idempotency-Key` 계약 선택지

### I1. Key를 누가 만드는가

#### A. 공개 앱 API에서 필수 UUID v4 header — 확정

- `POST /api/v1/exams`가 필수 lowercase UUID v4 `Idempotency-Key` header를 받는다.
- 신규 앱은 시험 시작 버튼을 누른 시점에 UUID v4를 한 번 만들고 최종 결과가 확정될 때까지 같은 값을 재사용한다.
- header가 없는 요청은 `INVALID_IDEMPOTENCY_KEY`로 거절한다. 앱 rollout 전에는 Billing 시험 생성 gate를 활성화하지 않는다.

장점: 모든 앱 시험 생성 요청을 하나의 operation으로 추적해 이중 Session·차감을 막는다. Request Body와 성공 Response DTO는 유지한다.

단점: header가 없는 구버전 앱은 실패하므로 앱 선배포·강제 업데이트 또는 명시적인 전환 gate가 필요하다.

#### B. 공개 API optional·신규 앱만 필수 — 미채택

장점: 구버전 client 요청을 즉시 거절하지 않는다.

단점: header 없는 요청은 응답 유실 뒤 중복 Session·차감을 전 구간에서 막을 수 없어 채택하지 않았다.

#### C. Learning Core가 항상 생성

장점: client 변경이 없다.

단점: response loss 뒤 새 HTTP 요청을 이전 요청과 연결할 수 없어 가장 중요한 이중 생성 문제를 해결하지 못한다.

### I2. Key 형식과 scope

#### A. UUID v4, `(canonicalUserId, operationType, key)` unique — 권장

- header는 lowercase canonical UUID, 최대 36자 고정
- 개인정보·userId·시간·device ID를 key에 넣지 않음
- 같은 사용자의 같은 route/operation에서만 동일 key로 판정
- 같은 key의 normalized request fingerprint가 다르면 `IDEMPOTENCY_KEY_CONFLICT`

장점: 추측·충돌 위험이 작고 사용자 간 우연한 동일 UUID가 서로를 막지 않는다.

단점: client가 UUID 생성·로컬 보존 규칙을 지켜야 한다.

#### B. 전역 unique key

장점: 구현이 단순하다.

단점: 다른 사용자의 우연하거나 악의적인 key 선점이 충돌을 만들 수 있다.

#### C. client 임의 문자열

장점: client 구현 자유도가 높다.

단점: 개인정보 포함, 과도한 길이, 정규화와 충돌 문제가 늘어난다.

### I3. 보존기간

#### A. Session에 operation ID는 수명 전체 보존, command state는 7일 — 권장

- 생성된 `ExamSession.examCreationOperationId`는 Session과 함께 보존한다.
- 처리 중·실패 command/reconciliation 상태는 terminal 이후 7일 보존한다.
- Billing reservation은 별도의 확정된 5분 TTL을 유지한다.

장점: 늦은 app 재시도도 원래 Session을 찾을 수 있고 reservation TTL과 request dedupe 기간을 혼동하지 않는다.

단점: operation ID unique index와 cleanup job이 필요하다.

#### B. 24시간 또는 7일 뒤 key 완전 재사용 허용

장점: 저장량이 작다.

단점: 오래 지연된 재시도가 새 시험과 차감으로 처리될 수 있다.

#### C. reservation TTL과 같은 5분

장점: 가장 단순하다.

단점: 모바일 timeout·앱 종료·네트워크 복구에 너무 짧아 비권장이다.

### I4. 동일 key 재호출 결과

#### A. 완료 결과 재사용 + 처리 중에는 `409` — 권장

- 완료된 같은 key: 기존 Session을 이용해 동일한 성공 DTO를 200으로 재구성
- 처리 중: `409 EXAM_CREATION_PROCESSING`과 `Retry-After`
- 이전 시도가 안전하게 실패해 Session과 confirmed charge가 모두 없음: 같은 operation으로 재실행
- 같은 key지만 user/operation/fingerprint가 다름: `409 IDEMPOTENCY_KEY_CONFLICT`, 자동 재시도 금지

장점: 신규 polling endpoint 없이 기존 동기 API와 성공 DTO를 유지한다.

단점: 앱이 409 processing과 conflict를 code로 구분해야 한다.

#### B. 처리 중이면 `202 Accepted`와 별도 status API

장점: 비동기 상태가 HTTP 의미상 명확하다.

단점: 신규 응답 DTO·polling API가 필요해 1차 외부 계약 범위가 커진다.

#### C. 처리 중이어도 새 시험 생성

장점: 구현이 쉽다.

단점: 이중 차감과 중복 Session을 허용하므로 채택하지 않는다.

## 11. Billing 오류 공개 계약 선택지

Billing 내부 오류를 Learning Core가 그대로 client에 전달하지 않고 안정적인 공개 code로 변환한다. 기존 `BaseResponse { isSuccess, code, message, result }` 구조는 유지한다.

### E1. 오류 노출 수준

#### A. client 행동 기준의 안정적인 code로 mapping — 권장

장점: 앱이 구매 화면 이동, 잠시 후 재시도, 로그인 갱신을 정확히 구분하며 Billing 내부 구조·provider 변경이 외부 계약에 새지 않는다.

단점: Learning Core에 명시적 mapping과 contract test가 필요하다.

#### B. Billing 내부 code를 그대로 pass-through

장점: 초기 구현이 빠르다.

단점: Billing refactor와 Apple/Google provider 차이가 앱 계약을 깨고 내부 정보를 노출할 수 있다.

#### C. 모두 `COMMON500`

장점: 외부 계약이 작다.

단점: 잔액 부족도 장애처럼 보이고 앱이 결제 유도와 안전한 재시도를 구분할 수 없다.

### E2. 사용권 부족 HTTP status

#### A. `402 Payment Required` — 권장

장점: 결제·credit 충전이 필요한 상태를 가장 직접적으로 표현하고 앱의 구매 화면 분기가 명확하다.

단점: 일부 팀과 모니터링 도구에 402 운영 경험이 적다.

#### B. `409 Conflict`

장점: 현재 사용자 entitlement 상태와 시험 시작의 충돌이라는 해석이 가능하고 흔히 다뤄진다.

단점: 처리 중·멱등 충돌과 같은 409 계열이 많아 code 의존도가 커진다.

#### C. `403 Forbidden`

장점: 권한 없음으로 단순하게 표현된다.

단점: 인증·정책 차단과 결제 필요를 혼동하므로 비권장이다.

### E3. 일시 상태와 시스템 장애

#### A. domain processing은 `409`, 인프라 장애는 `503` — 권장

- phone binding/결제 grant/동일 시험 생성 처리 중: 409 + 안정적인 code + `Retry-After`
- Billing timeout, circuit open, server unavailable: 503 `BILLING_TEMPORARILY_UNAVAILABLE` + `Retry-After`
- rate limit: 429 `BILLING_RATE_LIMITED` + `Retry-After`

장점: 사용자 상태가 준비 중인 경우와 서버 장애를 운영·앱 모두 구분할 수 있다.

단점: code와 retry 표가 필요하다.

#### B. 일시 실패는 모두 `503`

장점: client retry 정책이 단순하다.

단점: phone binding 지연 같은 정상 비동기 상태가 장애 지표를 오염시킨다.

#### C. 모두 `202`로 접수

장점: 비동기 처리를 자연스럽게 표현한다.

단점: 시험 생성 status API와 새 DTO가 필요하고 성공·실패 최종화를 client가 추가 polling해야 한다.

### E4. Session commit 뒤 confirm 결과 불명

#### A. Session을 `ENTITLEMENT_CONFIRMING` 내부 상태로 유지하고 `503` — 권장

- 사용자에게 성공 시험을 아직 노출하지 않음
- app은 같은 `Idempotency-Key`로 재시도
- Learning Core는 Billing status 조회·confirm 재시도와 reconciliation으로 수렴
- confirm되면 동일 요청에 200 기존 성공 DTO 반환

장점: 새 외부 status API 없이 Session과 차감 중 하나만 남는 문제를 복구할 수 있다.

단점: 내부 상태와 durable reconciliation이 필요하고 일시적으로 사용자가 기다려야 한다.

#### B. `202`와 생성 상태 polling

장점: 긴 장애에도 request thread를 붙잡지 않는다.

단점: 외부 API·DTO·client flow가 늘어난다.

#### C. 즉시 Session 삭제·cancel

장점: 겉보기 흐름이 단순하다.

단점: confirm이 실제 성공했는데 응답만 유실된 경우 charge만 남길 수 있어 비권장이다.

### E5. 권장 공개 오류표

| HTTP | code 예시 | 의미 | app 자동 재시도 |
| ---: | --- | --- | --- |
| 400 | `IDEMPOTENCY_KEY_INVALID` | key 형식 오류 | 아니오, 새 올바른 key 필요 |
| 401 | 기존 인증 code | Access Token 문제 | 토큰 갱신 정책에 따름 |
| 402 | `ENTITLEMENT_INSUFFICIENT` | 유효 pass/free/credit 부족 | 아니오, 구매·충전 화면 |
| 409 | `ENTITLEMENT_ELIGIBILITY_PROCESSING` | phone binding 처리 중 | 예, 같은 key |
| 409 | `PAYMENT_GRANT_PROCESSING` | store 결제는 확인됐지만 grant 처리 중 | 예, 같은 key |
| 409 | `EXAM_CREATION_PROCESSING` | 동일 operation 처리 중 | 예, 같은 key |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` | key를 다른 operation에 재사용 | 아니오, client bug 처리 |
| 409 | `ACCOUNT_MERGED_TOKEN_REJECTED` | merge된 source token | 아니오, 재로그인 |
| 429 | `BILLING_RATE_LIMITED` | Billing 호출 제한 | 예, 같은 key |
| 503 | `BILLING_TEMPORARILY_UNAVAILABLE` | timeout·circuit open·장애 | 예, 같은 key |

`Retry-After`는 409 processing, 429와 503에만 사용한다. client가 자동 재시도할 때는 반드시 같은 `Idempotency-Key`를 사용하고 exponential backoff+jitter를 적용한다. Error `result`에는 balance, raw provider code, reservation 내부 상태, candidate와 transaction identifier를 넣지 않는다.

## 12. 권장 승인 패키지

- I1-A: 앱 시험 생성 API의 UUID v4 header 필수
- I2-A: client UUID v4 + user/operation scope
- I3-A: Session lifetime mapping + terminal command 7일
- I4-A: 완료 결과 재사용, processing 409
- E1-A: 안정적인 public mapping
- E2-A: 사용권 부족 402
- E3-A: domain processing 409 / infra 503 / rate limit 429
- E4-A: confirm 불명은 내부 pending + 503 + reconciliation
- E5 표와 `Retry-After`, same-key retry 적용

이 패키지는 기존 시험 생성 Request Body와 성공 Response DTO를 변경하지 않는다. 추가되는 외부 계약은 필수 `Idempotency-Key` request header, 오류 code/status와 일부 `Retry-After` response header다.

## 13. 앱 종료·미완료 시험과 idempotency의 구분

`Idempotency-Key`는 사용권이나 “무료 재응시권”이 아니라 **한 번의 시험 Session 생성 command**를 식별한다. 따라서 서로 다른 두 Session이 같은 key를 공유해서는 안 된다.

### 기본 예시

```text
첫 시험 시작 command K1
→ reservation R1
→ ExamSession E1
→ confirm

앱 강제 종료 후 재실행
→ 새 시험 시작 command K2
→ E1을 ABANDONED_RESTARTED로 종료
→ 새 ExamSession E2를 처음부터 생성
```

사용자가 앱을 다시 열어 시험을 시작하면 `K2 → E2`처럼 새 key와 새 Session을 사용한다. 단, K1 요청의 HTTP 응답 자체를 받지 못해 transport retry하는 짧은 구간에서는 K1을 재사용해 E1 중복 생성만 막는다. 새 Session을 무료 replacement로 허용할지는 idempotency가 아니라 별도의 restart product policy다.

### U1. 기존 Session 이어풀기 — 사용자 결정으로 제외

- 앱 종료만으로 `IN_PROGRESS` Session을 abandon하지 않음
- app은 `examId`와 최초 `Idempotency-Key`를 persistent local storage에 보관
- relaunch 시 기존 examId로 문제·진행 상태를 복구
- create 성공 응답을 받았는지 불명확할 때만 같은 key로 `POST /api/v1/exams` replay
- 새 key로 create했는데 active Session이 있으면 새 차감 대신 active-session conflict/복구 흐름

장점: 이중 차감이 없고 이미 노출된 문제지·업로드·채점 결과를 그대로 이어가며 abuse가 적다.

단점: 이전 문항 결과·업로드·채점 상태 복구 분기가 복잡하고 사용자가 처음부터 다시 풀기를 원한다는 확정 정책과 맞지 않는다. 따라서 구현하지 않는다.

### U2. 미완료 Session을 새 Session으로 교체 — 시작 방식 확정

- 새 `Idempotency-Key`, 새 examId를 사용
- 새 Session은 `replacementOfExamId`와 기존 entitlement consumption을 연결
- 원 Session은 `ABANDONED_RESTARTED`
- 원 Session의 문제 결과·upload·grading Job·summary를 새 Session에 복사하지 않음
- 늦게 도착한 원 Session Callback은 새 Session에 쓰지 않고 abandoned 정책으로 no-op
- 원 Session과 관련 DB/S3 감사 자료는 즉시 삭제하지 않음
- 새 Session에는 동일 `mockExamId`를 배정해 반복 restart로 다른 문제지를 탐색하지 못하게 함
- 추가 차감·무료 교체 횟수·가능 시간은 아래 R 정책으로 별도 확정

장점: 이전 채점 결과와 진행 상태를 복구할 필요 없이 항상 깨끗한 Session에서 처음부터 다시 시작할 수 있다.

단점: 반복 restart와 AI 호출 비용 abuse, 원 Session의 늦은 Callback 경합과 Billing consumption 연결이 생긴다.

### U3. 미완료여도 새 Session은 새 차감

장점: Billing 원장이 가장 단순하고 반복 문제지 탐색을 억제한다.

단점: 앱 crash·OS 종료·네트워크 장애에도 다시 결제하게 되어 사용자 경험이 나쁘다.

### 확정된 시작 정책

- 이어풀기와 active Session 복구 API는 제공하지 않음
- 앱 재실행 후 시작: 기존 미완료 E1 abandon, 새 key K2와 새 examId E2
- 이전 Session의 결과·파일·Job·Callback 상태를 새 Session에 승계하지 않음
- 생성 response loss에 대한 transport retry만 같은 K1을 사용해 같은 E1을 반환
- 서로 다른 Session에 같은 `Idempotency-Key` 할당 금지

### R1. 매 restart마다 새 entitlement 차감

장점: Billing 원장이 단순하고 restart·AI 비용 abuse를 가장 강하게 막는다.

단점: 실수로 앱을 닫거나 OS가 종료해도 10 credits가 다시 필요해 사용자 경험이 나쁘다.

### R2. 제한된 무료 replacement

- 최초 confirmed consumption에 replacement group을 연결
- 최초 시작 후 24시간 안에 최대 3회까지 추가 차감 없이 새 Session 생성
- 매번 새 key·새 examId, 동일 mockExamId
- 네 번째 restart 또는 24시간 이후에는 새 entitlement 차감
- completed Session은 replacement 불가

장점: 앱 종료 사용자 보호와 반복 restart abuse 제한 사이 균형이 좋다.

단점: replacement count/window와 Billing–Learning Core 원자적 수렴을 구현해야 하며 3회 이후 장애 사용자의 운영 문의가 생길 수 있다.

### R3. 완료할 때까지 무제한 무료 replacement — 확정

최초 시험 시작에서 유효한 entitlement를 한 번 소비하고 `AttemptGroup`을 `OPEN`으로 만든다.

```text
AttemptGroup G1
- entitlementConsumptionId C1: 최초 시작에서 10 credits/free claim/pass usage 소비
- mockExamId: group 동안 고정
- status: OPEN → GRADING → COMPLETED 또는 RETAKE_AVAILABLE → OPEN
- sessions: E1, E2, E3 ...
- activeSessionId: 항상 최대 1개
```

- E1을 중단하고 새로 시작할 때 새 key K2·새 Session E2를 만들되 C1을 다시 차감하지 않음
- restart 횟수와 시간 제한 없음
- 기존 Session은 새 Session commit과 함께 `ABANDONED_RESTARTED`
- 한 사용자에게 OPEN AttemptGroup과 active Session은 각각 최대 하나
- group이 `COMPLETED`된 뒤 새 시험은 새 entitlement consumption과 새 group 필요
- unlimited pass가 유효할 때 G1을 열었다면 pass 만료 뒤에도 G1 완료 전 replacement는 허용하되 다른 group을 새로 열지는 못함

모든 필수 문항 `retryCount=0` 제출이 접수되면 group을 완료시키지 않고 `GRADING`으로 전환한다. **사용자가 조회 가능한 필수 문항 피드백과 유효한 종합 점수·Summary가 모두 저장된 시점**에만 `COMPLETED`로 닫는다. 채점 복구가 최종 실패하면 `RETAKE_AVAILABLE`로 전환해 같은 consumption으로 새 Session을 무료 생성할 수 있게 한다. 외부 기존 시험 status/response 필드는 그대로 유지한다.

장점: 사용자가 완료하지 못한 시험에 다시 결제하지 않으면서 최초 entitlement는 즉시 한 번 소비하므로 같은 credits를 다른 시험에 중복 사용할 수 없다.

단점: 반복 초기화와 AI 호출 비용 abuse가 가능하며 AttemptGroup·동시성 guard·abandoned Callback no-op와 group close reconciliation이 필요하다.

현재 `ExamSessionManager.startNew()`의 기존 active abandon 방향은 새 제품 결정과 맞지만, Billing 연동 후에는 replacement authorization, 기존 Session abandon, 새 Session insert와 group active pointer가 실패 시 한쪽만 남지 않도록 command와 reconciliation을 추가해야 한다. U2 시작 방식과 R3 차감 정책은 확정됐고, group 완료 시점을 모든 필수 최초 제출 접수로 할지 최종 승인이 필요하다.

### R3에서도 `Idempotency-Key`가 필요한 이유

무료 replacement와 멱등성은 다른 문제다.

```text
사용자의 한 번의 restart 동작 K2
→ network timeout
→ app가 K2 재전송
```

key가 있으면 두 호출 모두 E2 하나로 수렴한다. key가 없으면 첫 호출이 E2를 만들고 응답만 유실된 뒤 두 번째 호출이 E2를 즉시 abandon하고 E3까지 만들 수 있다. 추가 credit 차감이 없더라도 중복 Session, active pointer 경합, 문제지 조립·S3·Redis·AI 비용과 Callback 혼선이 발생한다.

- 각 의도적인 restart는 새 key: K1/E1, K2/E2, K3/E3
- 같은 restart의 transport retry는 같은 key: K2 replay → E2
- Learning Core → Billing replacement authorization도 같은 operation ID로 멱등 처리
- `Idempotency-Key`는 차감 횟수가 아니라 Session 생성 횟수를 exactly-once에 가깝게 수렴시킨다.

## 14. 시험 시작 차감 시점과 서버 오류 복구

### 14.1 차감은 `reserve → Session commit → confirm`

Session 생성 요청을 받자마자 영구 차감하지 않는다.

```text
1. Learning Core → Billing reserve
2. Billing: 사용할 entitlement/credit를 5분 hold
3. Learning Core: ExamSession + operation/reservation/group metadata commit
4. Learning Core → Billing confirm
5. Billing: 최초 entitlement consumption + AttemptGroup OPEN 확정
6. confirm 성공 뒤에만 client에 시험 생성 200 응답
```

- `reserve`: 다른 시험이 같은 credit/free entitlement를 동시에 쓰지 못하게 잠그지만 아직 최종 소비는 아님
- `confirm`: Session commit이 확인된 뒤 최초 소비를 확정하는 시점
- R3 replacement: 새 Session마다 새 operation/reservation audit은 만들지만 같은 OPEN group의 consumption을 재사용하므로 추가 차감 없음

여기서 5분은 사용자 대기 시간이 아니라 reservation의 최대 유효시간(TTL)이다. 정상 요청은 reserve, Session 저장, confirm을 수초 안에 연속 처리하고 곧바로 시험 생성 응답을 반환한다. 5분은 Learning Core가 중간에 종료되어 cancel을 보내지 못하더라도 hold가 영구히 남지 않게 하는 안전장치다.

TTL은 `RESERVED` 상태에만 적용된다. 정상적으로 Session이 저장되면 시험 응답을 보내기 전에 `RESERVED → CONFIRMED/CONSUMED`로 전이하며, 이 순간 reservation 만료 대상에서 제외된다. 따라서 사용자가 시험을 보는 동안 5분이 지나도 credits가 다시 `AVAILABLE`로 풀리지 않는다. confirm되지 않은 Session은 client에 사용 가능한 시험으로 노출하지 않는다.

예를 들어 사용자가 paid credits 10개를 가지고 시험 시작을 누르면 Billing은 해당 10개를 `AVAILABLE`에서 즉시 삭제하지 않고 reservation에 잠시 배정한다. 이 동안 다른 동시 시작 요청은 같은 10개를 사용할 수 없다. ExamSession 저장에 실패하면 cancel 또는 TTL 만료로 hold를 풀어 10개를 다시 사용할 수 있게 한다. ExamSession이 확실히 저장된 뒤 confirm이 성공하면 그때 10개를 최초 AttemptGroup의 소비로 확정한다.

Session 저장은 단순 메모리 생성이 아니라 MongoDB에 `examId`, 사용자, operation/reservation 식별 관계가 durable commit됐다는 뜻이다. 따라서 confirm 뒤 앱이 종료되어도 서버가 어떤 시험과 소비가 연결됐는지 복구할 수 있다. confirm 응답만 유실되면 새 Session이나 새 차감을 만들지 않고 같은 operation으로 Billing 상태 조회와 confirm 재시도를 수행한다.

### 14.2 사용할 재화의 우선순위

#### S1. 서버 자동 선택 — 권장

```text
1. 현재 유효한 unlimited pass
2. FREE_EXAM_ONCE
3. 만료가 가까운 promotional credits
4. 오래된 paid credits
```

장점: 유료 credit를 최대한 보존하고 client가 entitlement ID나 재화 종류를 보내지 않아도 되며 race와 위변조가 적다.

단점: 사용자가 무료권을 나중에 남겨두고 paid credit를 먼저 쓰는 선택은 할 수 없다.

#### S2. 사용자가 결제 수단 선택

장점: 무료권·credit 사용 시점을 사용자가 통제할 수 있다.

단점: 공개 Request 계약과 UI가 늘고, 선택 뒤 잔액 변경·pass 만료 race와 잘못된 entitlement ID 검증이 필요하다.

#### S3. paid credits를 무료권보다 먼저 사용

장점: 무료 체험권을 나중에 보존할 수 있다.

단점: 무료 기회가 있는데 유료 credit가 먼저 사라져 사용자 불만 가능성이 커 비권장이다.

S1을 적용하면 unlimited pass 사용에는 credit 숫자 차감이 없고 usage audit만 남긴다. free entitlement는 최초 confirm에서 같은 phone claim의 consumption group에 묶이며, credits는 reserve allocation 10개가 confirm에서 consume된다. 5 credits만 있고 pass/free entitlement가 없으면 reserve가 `ENTITLEMENT_INSUFFICIENT`로 실패한다.

### 14.3 오류 단계별 결과

| 오류 시점 | Session | Billing 상태 | 사용자 결과 |
| --- | --- | --- | --- |
| Billing reserve 전/실패 | 없음 | 소비 없음 | 402 또는 503, 차감 없음 |
| reserve 성공 후 Session rollback | 없음 | cancel/TTL expire | hold 반환, 차감 없음 |
| Session commit 여부 불명 | 재조회로 판정 | 즉시 cancel 금지 | operation 조회 후 confirm 또는 cancel |
| Session commit 성공, confirm timeout | 있음, client 성공 미노출 | 상태 조회·confirm 재시도 | 같은 key 재시도, 이중 차감 없음 |
| confirm 성공 뒤 앱/서버 종료, 필수 제출 미완료 | 기존 Session abandon 가능 | AttemptGroup OPEN | 새 key·새 examId 무료 replacement |
| 모든 필수 최초 제출 접수 | 제출 완료 Session | AttemptGroup GRADING | 동일 Session grading retry/reconciliation, 잠시 새 시작 차단 |
| grading 복구 성공 | 결과·Summary 저장 | AttemptGroup COMPLETED | 점수·피드백 제공, 다음 시험은 새 소비 |
| grading 복구 최종 실패 | 실패 Session 보존 | AttemptGroup RETAKE_AVAILABLE | 새 key·새 examId 무료 replacement |
| confirmed consumption은 있는데 Session이 복구 불가 | 없음/손상 | AttemptGroup OPEN | 같은 group에 무료 replacement, 재차감 없음 |
| retake도 제공할 수 없는 장기 서비스 장애 | 복구 불가 | consumption 보상 전이 | 원 source의 credits/free entitlement를 멱등 복원 |

### 14.4 무료권과 TrialClaim 오류 처리

검증 phone당 claim uniqueness는 reserve 과정에서 확보할 수 있지만 Session rollback 때문에 claim 기록을 삭제하지 않는다.

- `TrialClaim`: 해당 phone benefit이 어느 canonical owner에 귀속됐는지 보존
- free entitlement/reservation: cancel되면 다시 `AVAILABLE`
- 최초 confirm: consumption과 AttemptGroup OPEN
- 미완료 replacement: 동일 group으로 추가 차감 없음
- group 완료: 같은 phone에는 새 무료 group을 만들지 않음

따라서 reserve 뒤 장애가 발생해도 무료 기회를 잃지 않고, claim 삭제·재생성 race로 같은 phone에 두 번 지급하지도 않는다.

### 14.5 제출·채점·제품 완료 기준

R3에서는 다음 세 시점을 구분한다.

1. `OPEN`: 필수 submit 일부가 아직 접수되지 않음. 앱/서버 종료 시 즉시 무료 replacement 가능.
2. `GRADING`: 모든 필수 `retryCount=0` submit과 durable QuestionGradingJob 생성 완료. 새 Session을 잠시 열지 않고 기존 채점 복구 수행.
3. `COMPLETED`: 필수 문항 피드백과 유효한 종합 점수·Summary가 저장돼 사용자가 조회 가능. 이때만 group 종료.

`GRADING`에서 자동/사용자 grading retry와 reconciliation이 성공하면 `COMPLETED`로 간다. 확정된 retry policy를 모두 소진해도 필수 결과를 만들지 못하면 `RETAKE_AVAILABLE`로 전환한다.

- `RETAKE_AVAILABLE`에서 사용자가 다시 시작하면 새 key·새 examId, 동일 mockExamId로 새 Session을 만들고 group을 `OPEN`으로 되돌림
- 이전 실패 Session의 결과·Job은 새 Session에 승계하지 않음
- retake 시작 뒤 이전 Session의 늦은 Callback은 attempt generation/currentSession fencing으로 no-op
- 반복 grading 실패도 같은 규칙으로 횟수 제한 없이 다시 retake 가능
- 단순 화면 조회, upload URL 발급 또는 S3 upload만으로는 `GRADING`이나 `COMPLETED`가 아님

현재 코드의 `ExamSession.COMPLETED`는 Summary Callback 뒤 설정되므로 결과 성공의 출발점으로 활용할 수 있지만, Billing group 전이는 별도 durable outbox와 reconciliation으로 전달한다. 모든 Question Job·ExamResult·SummaryJob·ExamSummary 불변식을 확인한 뒤에만 group close를 발행한다. 외부 status·결과 DTO는 변경하지 않는다.

### 14.5.1 결과를 끝내 제공할 수 없을 때의 보상

정상 정책은 credit 환불보다 무료 retake다. 그러나 Billing/Learning Core 장애가 장기화돼 retake Session 자체도 제공할 수 없다면 운영 보상 command로 최초 consumption을 멱등 취소한다.

- paid/promotional credits: 원래 allocation의 grant들에 정확히 복원
- `FREE_EXAM_ONCE`: TrialClaim은 유지하되 entitlement consumption을 취소하고 같은 owner에게 다시 `AVAILABLE`
- unlimited pass usage: usage audit을 취소 표시하며 credit 환급은 없음
- 이미 `COMPLETED` 결과가 존재하면 보상 금지
- 같은 compensation operation ID 재실행은 no-op

따라서 사용자는 “차감됐지만 점수도 피드백도 없고 재응시도 못 하는” terminal 상태에 남지 않는다.

### 14.6 권장 확정안

- S1 자동 우선순위: unlimited → free once → promotional → paid
- 최초 Session의 confirm에서 entitlement 한 번 소비
- confirm 전 장애는 cancel/expire로 전액 복구
- confirm 후 제출 미완료 장애는 R3 무료 replacement
- 모든 필수 최초 submit 접수 뒤에는 GRADING으로 전환해 grading 복구 수행
- 유효한 필수 피드백·점수·Summary가 조회 가능할 때만 COMPLETED
- grading 복구 최종 실패는 RETAKE_AVAILABLE로 전환해 같은 consumption의 무료 restart
- retake도 제공할 수 없는 장기 장애는 원 entitlement source를 멱등 복원
- Billing/Session 불일치는 operation ID와 reconciliation으로 수렴
