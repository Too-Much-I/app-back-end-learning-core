# 1차 업데이트 기간제 이용권 포함 범위 영향 분석

## 1. 5줄 결론

1. 사용자 결정에 따라 credit 상품이 아니라 **1일·3일·7일·14일·30일 동안 무제한 응시하는 기간형 상품**을 기준으로 한다.
2. 사용자는 “구매한 기간만 사용하는 기간제 이용권”으로 확정했으며 자동 갱신 subscription은 1차 범위에서 제외한다.
3. 기간제 이용권도 Apple·Google 거래 검증, server notification, 구매 복원·환불과 Billing 공개 API가 필요하므로 1차 범위는 여전히 상당히 늘어난다.
4. 기간제 이용권 MVP는 현재 남은 출시 작업 대비 약 50~80%, 1 backend+1 mobile 병렬 기준 약 4~6주 추가를 예상한다. 다섯 상품 모두 자동 갱신이면 약 80~140%, 6~10주 이상이 안전하다.
5. credit와 첫 구매 2배는 완전히 제거한다. 출석 연장·추천·coupon 등은 현행 계약에 포함하지 않고, 이후 도입 시 별도 설계한다.

## 2. 이번에 바뀐 상품 방향

과거 credit 상품 초안은 폐기됐으며 현재 또는 후속 구현의 기준이 아니다. 현행 목표 제품에는 credit 잔액·지급·소비·충전 API가 없다.

| 항목 | 새 기준 |
|---|---|
| 과금 단위 | credit 수량이 아니라 이용 가능 기간 |
| 상품 | 1일, 3일, 7일, 14일, 30일 |
| 이용 범위 | 활성 기간 동안 모의고사 무제한 시작 |
| 시험 중 만료 | pass가 유효할 때 시작한 AttemptGroup은 완료 전 재시작까지 허용 |
| 시험 완료 후 | 이용권이 아직 유효하면 다음 시험 시작 가능 |
| 만료 후 | 기존 미완료 AttemptGroup의 완료 전 재시작 외에는 새 시험 그룹 생성 불가 |

따라서 `10 credits = 시험 1회`, credit 잔액 합산과 credit 차감 UI는 새 상품의 프론트·Billing 계약에서 제거해야 한다. 무료시험 1회는 별도 `FREE_EXAM_ONCE` entitlement로 유지할 수 있다.

## 3. 확정 결과: 기간제 무제한 이용권

### 기간제 무제한 이용권 — 확정

사용자가 상품을 한 번 구매하면 해당 기간만 사용할 수 있고 자동 결제되지 않는다.

```text
1일권 구매 → 활성화 → expiresAt까지 무제한 → 만료
다시 사용하려면 사용자가 새로 구매
```

장점:

- 1일·3일·7일·14일·30일을 같은 제품 모델로 제공하기 쉽다.
- 해지, 갱신 실패, grace period와 billing retry가 없다.
- 사용자가 결제 금액과 종료 시점을 이해하기 쉽다.
- 첫 릴리스의 CS·환불·스토어 상태 동기화가 상대적으로 단순하다.

단점:

- 만료 때 자동 매출이 발생하지 않는다.
- 반복 구매 UX와 구매 알림이 필요하다.
- Apple·Google에서 사용할 정확한 상품 유형과 심사 표현은 출시 국가·스토어 설정에서 확인해야 한다.

### 자동 갱신 구독 — 1차 제외

정기 결제 동의 후 취소 전까지 스토어가 반복 결제한다.

추가로 필요한 정책:

- plan별 갱신 주기와 실제 스토어 지원 단위
- 무료 체험·intro offer 여부
- 사용자 취소와 현재 기간 종료
- 결제 실패, grace period, account hold
- upgrade/downgrade와 proration
- 가격 인상 동의
- 가족 공유 여부
- App Store/Play 구독 관리 화면 연결

장점:

- 반복 매출과 장기 유지에 유리하다.
- 사용자가 매번 다시 구매하지 않아도 된다.

단점:

- 1일·3일처럼 매우 짧은 상품까지 자동 갱신하는 UX·스토어 매핑이 복잡하다.
- 갱신·취소·유예·복구 상태가 많아 backend와 mobile 테스트 범위가 크게 늘어난다.
- “한 달”을 30일로 볼지 calendar month/스토어 billing period로 볼지도 달라진다.

## 4. 현재 구현돼 있는 기반

- 검증 phone당 무료시험 1회 eligibility·TrialClaim
- entitlement grant·ledger와 시험 Reservation
- Learning Core `reserve → ExamSession durable commit → confirm`
- AttemptGroup `OPEN/GRADING/COMPLETED/RETAKE_AVAILABLE`
- pass가 유효할 때 시작한 시험의 완료 전 추가 차감 없는 재시작 정책
- phone 재가입 continuation과 owner rebind 기반

관련 구현 이력은 Billing `TMI-110`, `TMI-112`, `TMI-113`, `TMI-115`, `TMI-117`, `TMI-120`, Learning Core `TMI-116`, `TMI-118`, `TMI-122`, `TMI-125`다.

Billing 계약에는 후속 기간형 권리를 위한 `PREMIUM_SUBSCRIPTION`과 `SubscriptionEntitlement` 방향이 예약돼 있지만, 실제 plan·Store·renewal·cancel·expiry 구현은 아직 없다.

## 5. 새로 구현해야 하는 범위

| 영역 | 기간제 이용권 필수 작업 | 자동 갱신 시 추가 작업 |
|---|---|---|
| 상품 | 5개 plan과 store product ID·가격 매핑 | subscription group/base plan·offer 관계 |
| 모바일 | StoreKit 2·Play Billing 구매 및 복원 | 구독 관리·해지 화면, 갱신 상태 UX |
| Billing API | 상품 조회, 구매 제출/상태, 현재 이용권, 복원 | 구독 변경·갱신 상태 API |
| 거래 검증 | Apple·Google server-side 검증 | original transaction·renewal chain 검증 |
| 원장 | order/payment/store transaction/pass grant | renewal period별 payment·entitlement 연결 |
| notification | 구매·취소·환불·revoke | renewal·grace·billing retry·expired 이벤트 |
| 권리 | activatedAt·expiresAt·상태와 우선순위 | auto-renew status·graceUntil·change plan |
| 환불 | 미활성/미사용 또는 승인된 회수 정책 | 현재 period·renewal refund와 revoke |
| 운영 | 복원·reconciliation·알람·수동 보상 | 갱신 누락·유예·가격 변경 운영 |
| QA | 5개 기간, 중복 구매, 만료 경계 | renewal/cancel/grace/upgrade/down-grade 조합 |

## 6. 권장 기간형 entitlement 모델

```text
SubscriptionEntitlement
- entitlementId
- ownerUserId
- planCode: UNLIMITED_1D | 3D | 7D | 14D | 30D
- sourcePaymentId
- status: PENDING_ACTIVATION | ACTIVE | EXPIRED | REVOKED
- purchasedAt
- activatedAt
- expiresAt
- policyVersion
```

권장 정책:

1. 결제가 `CAPTURED`되고 서버 검증된 뒤에만 entitlement를 만든다.
2. 구매가 `CAPTURED`로 서버 검증된 시점에 즉시 활성화하고 그 시각부터 기간을 계산한다.
3. 서버 시각으로 `activatedAt ≤ now < expiresAt`일 때 새 AttemptGroup을 열 수 있다.
4. 활성 이용권에는 시험 횟수 차감 없이 usage audit만 남긴다.
5. 이용권 만료 직전에 시작한 시험은 그 AttemptGroup이 완료될 때까지 추가 결제 없이 처음부터 재시작할 수 있다.
6. 여러 이용권 구매 시 즉시 겹쳐 쓸지 queue로 보존할지 확정한다. 권장안은 활성 이용권 종료 뒤 다음 이용권 활성화다.
7. client clock은 권한 판단에 사용하지 않고 Billing server time을 기준으로 한다.

## 7. 1차 권장 범위

기간제 이용권을 1차에 포함한다면 다음 폐쇄 루프까지 구현한다.

- 1·3·7·14·30일 상품 조회
- Apple·Google 구매
- Billing server 거래 검증
- 결제 pending/captured/cancel 상태 조회
- 이용권 활성화와 서버 기준 expiresAt
- 현재 이용권 조회
- 시험 생성 시 active entitlement 자동 선택
- 구매 복원
- refund/revoke notification 처리
- 중복 transaction·notification 멱등성
- 앱 crash·응답 유실 reconciliation

제품에서 완전히 제거:

- 모든 credit 상품·잔액·지급·차감·원장과 관련 API/UI
- 첫 구매 할인·기간 2배·credit 2배

이후 도입 시 별도 계약이 필요한 범위:

- 출석 시 기간 연장
- 추천인 보상
- coupon
- 여러 이용권 동시 활성화·선물
- plan upgrade/downgrade
- 자동 갱신을 선택한 경우의 grace·billing retry 고도화

## 8. 일정 영향

스토어 계정·상품 등록 권한이 준비되고 1 backend와 1 mobile 개발자가 병렬 작업하는 거친 추정이다. 심사 대기는 별도다.

| 범위 | 추가 엔지니어 작업량 | 추가 달력 일정 | 현재 남은 출시 작업 대비 |
|---|---:|---:|---:|
| 무료시험까지만 | 기존 안정화 | 기준선 | 기준선 |
| 5종 기간제 이용권 | 약 8~12 engineer-weeks | 약 4~6주 | 약 +50~80% |
| 5종 자동 갱신 구독 | 약 14~22+ engineer-weeks | 약 6~10주 이상 | 약 +80~140% |
| 자동 갱신 + promotion/추천/coupon | 약 20+ engineer-weeks | 10주 이상 가능 | +140% 이상 가능 |

한 명이 backend·mobile·스토어 설정을 순차 수행하면 더 길어진다. 실제 store product type과 심사 요구가 확정된 뒤 다시 산정해야 한다.

## 9. 줄이면 안 되는 안전 항목

- client 결제 성공만으로 이용권 지급 금지
- Apple·Google server-side 거래 검증
- store transaction ID unique와 purchase command 멱등성
- notification 검증과 inbox 멱등성
- CAPTURED 이후에만 entitlement 생성
- server time 기반 활성·만료 판단
- 응답 유실 후 구매 상태 조회·복원
- refund/revoke/chargeback 반영
- sandbox와 production credential·product 완전 분리
- payment와 entitlement의 감사 가능한 원장 연결

## 10. 구현 전 결정해야 할 사항

1. 실제 가격과 store product ID·판매 국가
2. 활성 이용권 중 추가 구매를 금지할지
3. 환불·chargeback 시 이미 열린 미완료 AttemptGroup을 계속 보장할지
4. 만료 시각 표시와 offline 처리
5. iOS·Android 동시 출시인지 한 플랫폼 canary인지
6. 결제 CS·수동 reconciliation 담당자와 runbook

## 11. 출시 gate

- Apple sandbox·Google license tester의 5개 상품 실거래
- 결제 성공 응답 유실 뒤 복원
- transaction 중복 제출과 notification 중복·역순
- pending→captured, cancel, refund, revoke, chargeback
- 활성·만료 경계와 서버 clock 검증
- 이용권 만료 직전 시작한 AttemptGroup의 완료 전 재시작
- 여러 이용권 구매·queue 정책
- Guest merge·phone 재가입과 payment owner 동시성
- 이용권 활성 직후 Learning Core reserve
- 앱 crash·offline·재설치·다른 기기 복원
- production product/credential 오연결 방지와 운영 알람

## 부록 A. 근거

- 현재 진행 상태: `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`
- 결제 경계: `docs/codex/FIRST_RELEASE_BILLING_BOUNDARY_REVIEW.md`
- 기존 상품·entitlement 결정: `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`
- Billing 최신 계약 결정: `/Users/msde76/billing/docs/codex/CONTRACT_DECISIONS.md`
- Billing 현재 구현: `/Users/msde76/billing/src/main/java/web/tosunsaeng/billing`
