# 1차 업데이트 범위 — 5종 기간제 무제한 이용권

## 1. 5줄 결론

1. 1차 유료 상품은 자동 갱신이나 credit이 아닌 **1일·3일·7일·14일·30일 기간제 무제한 이용권**이다.
2. Apple·Google 결제를 Billing이 서버 검증해 `CAPTURED`로 확정한 시점부터 기간을 계산하고, 만료되면 자동 결제 없이 종료한다.
3. 활성 이용권 중에는 새 모의고사를 횟수 차감 없이 시작할 수 있고, 만료 전에 시작한 시험은 완료될 때까지 추가 결제 없이 처음부터 재시작할 수 있다.
4. 1차에는 구매·검증·활성화·현재 이용권·복원·환불·스토어 알림의 안전한 폐쇄 루프까지 포함한다.
5. credit 전체와 첫 구매 2배는 제품 계약에서 완전히 제거하고, 출석 연장·추천인·coupon과 복잡한 plan 변경은 이후 별도 재설계 대상으로 미룬다.

## 2. 확정 상품 모델

| planCode | 사용자 표시 | 유효시간 | 자동 갱신 |
|---|---|---:|---|
| `UNLIMITED_1D` | 1일 이용권 | 결제 확정부터 24시간 | 없음 |
| `UNLIMITED_3D` | 3일 이용권 | 결제 확정부터 72시간 | 없음 |
| `UNLIMITED_7D` | 7일 이용권 | 결제 확정부터 168시간 | 없음 |
| `UNLIMITED_14D` | 2주 이용권 | 결제 확정부터 336시간 | 없음 |
| `UNLIMITED_30D` | 한 달 이용권 | 결제 확정부터 720시간 | 없음 |

- “한 달”은 calendar month가 아니라 정확히 30일로 정의한다.
- 기간 계산은 client 시계가 아닌 Billing의 UTC 서버 시각을 사용한다.
- 표시 시각만 사용자 timezone으로 변환한다.
- 상품 가격과 Apple·Google product ID는 Store 등록 전에 별도 확정한다.
- 무료시험 1회는 `FREE_EXAM_ONCE`로 별도 유지한다.

### 2.1 전체 1차 업데이트 묶음

| 트랙 | 1차 포함 내용 | 현재 상태 |
|---|---|---|
| Identity | Google·Apple 로그인, 동일 Firebase User의 필수 phone 인증, Guest 승격·통합, 탈퇴 lifecycle | 핵심 구현 존재, 실제 모바일·staging rollout 필요 |
| 무료시험 | 검증 phone당 `FREE_EXAM_ONCE` 1회, 재가입·merge 중복 방지 | 핵심 구현 존재, cross-service E2E 필요 |
| 유료 이용권 | 1·3·7·14·30일 비자동갱신 무제한 이용권 | 신규 구현 필요 |
| 시험 생성 | `Idempotency-Key`, reserve→Session commit→confirm, 권리 부족·장애 처리 | 핵심 구현 존재, 이용권 evaluator 확장 필요 |
| 시험 완료 | OPEN/GRADING/COMPLETED/RETAKE_AVAILABLE, 완료 전 추가 결제 없는 처음부터 재시작 | 핵심 구현 존재, staging E2E 필요 |
| 기존 시험 | 문제 제공, S3 음성 제출, AI 채점, polling, Summary·이력·채점 복구 | 구현 유지 |
| 소유권 | 탈퇴·phone 재가입·Guest UserMerged의 시험/결제 권리 정합성 | 구현 기반 존재, TMI-125 병합·통합 검증 필요 |
| 배포·운영 | prod/staging 격리, Lattice/IAM, Mongo transaction/index, alarm·rollback | 출시 gate 미완료 |

10초 챌린지는 기존 1차 진행표에 포함돼 있지만 runtime backend가 아직 없다. 로그인·무료시험·기간제 결제와 같은 배포에 반드시 포함하면 별도 Challenge 개발·AI·콘텐츠 E2E 일정이 추가된다. 일정 보호가 우선이면 1.1 업데이트로 분리하고, 같은 1차에 유지하려면 기간제 결제 4~6주 추정에 Challenge 작업을 별도로 더해야 한다.

## 3. 사용자 흐름

### 3.1 구매

```text
앱이 Billing 상품 조회
→ StoreKit 2 / Google Play Billing 결제
→ 앱이 store 거래 증명을 Billing에 제출
→ Billing이 Apple/Google server API로 검증
→ 검증 거래를 CAPTURED로 기록
→ 기간제 entitlement ACTIVE 생성
→ 앱이 현재 이용권 조회
```

앱의 결제 성공 callback만으로 이용권을 지급하지 않는다. Billing 서버 검증이 완료된 거래만 권한의 근거다.

### 3.2 시험 시작

```text
POST /api/v1/exams + 새 Idempotency-Key
→ Learning Core가 Billing reserve
→ 활성 기간제 이용권 확인
→ ExamSession 저장
→ Billing confirm
→ 시험 시작
```

- 앱은 `planCode`, entitlementId, 결제 여부를 시험 생성 body에 보내지 않는다.
- Learning Core가 활성 이용권을 자동 선택한다.
- 활성 이용권 사용에는 횟수 차감이 없고 usage audit만 남긴다.
- 시험 생성 Request Body와 성공 Response DTO는 기존 계약을 유지한다.

### 3.3 시험 중 이용권 만료

- 이용권이 유효할 때 새 AttemptGroup을 열었으면 해당 시험은 완료할 때까지 보호한다.
- 문제를 모두 제출하지 않은 `OPEN` 시험은 추가 결제 없이 새 examId로 처음부터 재시작할 수 있다.
- `GRADING`은 기존 채점·Summary 복구를 우선한다.
- 복구가 최종 실패한 `RETAKE_AVAILABLE`도 추가 결제 없이 처음부터 재시작할 수 있다.
- `COMPLETED` 뒤 이용권이 만료됐다면 다음 새 시험은 시작할 수 없다.

### 3.4 만료와 재구매

- `expiresAt` 도달 시 `EXPIRED`가 된다.
- 자동 결제하거나 자동 연장하지 않는다.
- 다시 이용하려면 사용자가 상품을 새로 구매한다.
- 1차에서는 활성 이용권이 있는 동안 추가 이용권 구매를 막는 것을 권장한다. queue·기간 합산·중첩은 후속 범위다.

### 3.5 복원

- 앱 재설치, 기기 변경, 결제 응답 유실 시 “구매 복원”을 제공한다.
- Billing은 store 거래를 다시 검증하고 동일 transaction이면 기존 payment/entitlement를 반환한다.
- 복원은 남은 기간을 새로 시작하거나 연장하지 않는다.
- 이미 만료된 이용권은 구매 이력으로 확인할 수 있어도 다시 활성화하지 않는다.

## 4. 1차 포함 범위

### 4.1 Billing 서버

- 5종 상품 catalog와 환경별 Store product ID 매핑
- 공개 상품 조회 API
- 공개 구매 제출 API와 command idempotency
- 공개 구매 상태 조회 API
- 현재 활성 이용권 조회 API
- 구매 복원 API
- Apple App Store server-side 거래 검증 adapter
- Google Play server-side 거래 검증 adapter
- order·payment·store transaction 원장
- 기간제 entitlement와 activation/expiry
- transaction ID unique와 payload digest 충돌 검증
- Apple Server Notifications inbox
- Google RTDN inbox
- notification 중복·역순·응답 유실 처리
- refund·revoke·chargeback 반영
- 결제/entitlement reconciliation worker
- UserMerged owner rebind와 결제 원장 보존
- metric·alert·관리자용 안전한 reconciliation runbook

### 4.2 Learning Core

- 기존 Billing Reservation의 entitlement evaluator가 활성 기간제 이용권을 인식
- 이용권 기반 Reservation은 수량 차감 없이 usage audit 생성
- pass 유효 시 INITIAL AttemptGroup 생성
- pass 만료 뒤에도 기존 미완료 AttemptGroup replacement 허용
- 권리 없음·결제 처리 중·Billing 장애의 공개 오류 mapping 유지/확장
- 기존 `Idempotency-Key`, Session commit, confirm/status/cancel 복구 재사용
- 기존 시험·S3·AI·Polling·Summary 계약 유지

Learning Core가 payment, product catalog 또는 Store 검증을 직접 구현하지 않는다.

### 4.3 모바일 앱

- 5종 상품 목록·가격 표시
- iOS StoreKit 2 구매
- Android Google Play Billing 구매
- 구매 pending·성공·취소·실패 UI
- Billing 구매 제출과 상태 polling
- 현재 이용권·남은 시간 표시
- 구매 복원
- 이용권 없음 시 구매 화면 이동
- 시험 생성의 lowercase UUID v4 `Idempotency-Key` 유지
- 결제 중 앱 종료·재실행 시 상태 복구
- 환불/revoke 후 접근 종료 UX

### 4.4 Store·운영 인프라

- Apple·Google의 5종 상품 등록
- sandbox/tester와 production product 완전 분리
- server notification endpoint·인증 구성
- Apple/Google server API credential의 secret store 주입
- Billing ECS·Mongo index·backup/restore
- 결제 dashboard, alarm과 reconciliation backlog
- CS용 거래 조회·복원·보상 runbook
- 개인정보처리방침·이용약관·환불 안내와 Store 심사 문구 반영

## 5. 영구 제거와 이후 범위

### 5.1 제품에서 완전히 제거

- credit 충전·잔액·10-credit 차감
- credit grant·ledger·reservation·consumption·관련 API와 UI
- 첫 구매 기간 2배·credit 2배·가격 할인

이 항목은 1차 이후 구현 후보가 아니다.

### 5.2 이후 별도 재설계

- 자동 갱신 subscription
- 무료 체험 후 자동 결제
- grace period·billing retry·account hold
- plan upgrade/downgrade·proration
- 활성 이용권 추가 구매와 queue·기간 합산
- 출석 시 기간 연장
- 연속 로그인 보상
- 추천인 코드
- coupon 발급·입력
- 이용권 선물·가족 공유
- 웹 PG·웹 checkout
- 운영자가 임의로 기간을 수정하는 일반 admin UI
- 일부 사용 후 자동 부분 환불 공식

## 6. 상태 모델

### Payment

```text
CREATED
→ VERIFICATION_PENDING
→ CAPTURED
또는 CANCELED / FAILED
CAPTURED → REFUNDED / CHARGEBACK
```

### FixedTermEntitlement

```text
ACTIVE
→ EXPIRED
또는 REVOKED
```

권장 필드:

- `entitlementId`
- `ownerUserId`
- `planCode`
- `sourcePaymentId`
- `durationSeconds`
- `activatedAt`
- `expiresAt`
- `status`
- `policyVersion`

Payment와 entitlement는 별도 상태지만 `sourcePaymentId`로 감사 가능하게 연결한다.

## 7. 핵심 정책

### 권리 선택 순서

1. 이미 열린 AttemptGroup의 replacement 권리
2. 현재 활성 기간제 이용권
3. 검증 phone당 무료시험 1회
4. 없으면 `ENTITLEMENT_INSUFFICIENT`

이 순서는 활성 이용권을 구매한 사용자가 무료 1회를 잃지 않게 한다.

### 중복 구매

- 같은 store transaction은 entitlement를 한 번만 생성한다.
- 같은 purchase command와 같은 payload는 같은 결과를 반환한다.
- 같은 command에 다른 transaction/payload가 오면 conflict로 거절한다.
- 활성 이용권 보유 중 다른 상품 구매는 1차에서 UI와 서버 모두 차단한다.

### 환불·회수

- Store가 환불·revoke·chargeback을 통지하면 남은 이용권을 `REVOKED` 처리한다.
- 이미 완료된 시험 결과는 삭제하지 않는다.
- 이미 열린 AttemptGroup은 일반 환불이면 계속 허용할지 즉시 차단할지 법무·스토어 정책 확인 후 최종 확정한다.
- refund 원문 전체나 결제 credential을 일반 로그에 남기지 않는다.

## 8. 프론트 공개 API 계약 작성 범위

정확한 URL과 DTO는 Billing 구현 전에 별도 계약 문서로 고정한다. 필요한 capability는 다음 다섯 가지다.

| capability | 목적 |
|---|---|
| 상품 조회 | Store product와 서버 plan 매핑 확인 |
| 구매 제출 | store 거래 증명을 서버 검증 요청 |
| 구매 상태 | pending/응답 유실 복구 |
| 현재 이용권 | `planCode`, `activatedAt`, `expiresAt`, 상태 표시 |
| 구매 복원 | 재설치·기기 변경의 서버 권리 복구 |

응답에 provider secret, raw receipt, 내부 payment document와 owner userId를 노출하지 않는다.

## 9. 오류 UX

| 상황 | 앱 처리 |
|---|---|
| 사용자가 Store 결제 취소 | 오류 toast보다 정상 취소 상태로 구매 화면 유지 |
| Store pending | 이용권을 먼저 열지 않고 처리 중 표시·상태 조회 |
| 서버 검증 일시 실패 | transaction을 버리지 않고 같은 command로 재시도 |
| 결제는 됐지만 응답 유실 | 구매 상태 조회·복원으로 수렴 |
| 중복 transaction | 기존 성공 결과 반환 |
| 이용권 없음 | 구매 화면으로 이동 |
| Billing 장애 | 미구매로 단정하지 않고 일시 장애 안내 |
| 이용권 만료 | 진행 중 보호 시험 여부를 확인하고 새 시험만 차단 |
| 환불/revoke | 현재 이용권 갱신 후 새 시험 차단 |

## 10. 구현 순서

1. 상품·활성화·환불 계약과 Store product ID 확정
2. Billing payment/entitlement schema·index·상태 머신
3. Billing 공개 API와 fake Store adapter
4. Apple server verification·notification
5. Google server verification·RTDN
6. Learning Core entitlement evaluator 연결
7. iOS StoreKit 2와 구매·복원 UI
8. Android Play Billing과 구매·복원 UI
9. 중복·응답 유실·환불·만료 component test
10. sandbox cross-service E2E
11. staging canary와 운영 runbook 검증
12. production product·credential로 immutable image 승격

## 11. 완료 조건

- 다섯 상품이 양 Store sandbox에서 구매된다.
- client 결과가 아니라 Billing server 검증 뒤에만 이용권이 열린다.
- 같은 transaction·notification이 반복돼도 이용권은 하나다.
- 결제 응답 유실·앱 종료·재설치 후 복원된다.
- Billing server 시각 기준으로 정확히 만료된다.
- 활성 기간에는 여러 시험을 횟수 차감 없이 시작할 수 있다.
- 만료 전에 시작한 미완료 시험은 완료 전 재시작할 수 있다.
- 만료 뒤 새 AttemptGroup은 차단된다.
- refund·revoke·chargeback이 새 시험 권한에 반영된다.
- Guest merge·phone 재가입 후 canonical owner에서 이용권을 조회할 수 있다.
- 실제 Lattice·Mongo replica-set·multi-instance·alarm·rollback E2E가 통과한다.

## 12. 일정 추정

스토어 계정과 상품 등록 권한이 준비되고 backend 1명과 mobile 1명이 병렬 작업하는 경우 약 **4~6주**, 8~12 engineer-weeks의 추가 작업을 예상한다. Store 심사 지연과 환불 정책 재검토는 별도다.

| 주차 | 목표 |
|---|---|
| 1주 | 계약, product ID, schema, fake adapter |
| 2주 | Billing API·원장·기간 entitlement |
| 3주 | Apple/Google 검증·notification |
| 4주 | iOS/Android 구매·복원, Learning Core 연동 |
| 5주 | failure E2E·환불·만료·owner merge 검증 |
| 6주 | canary, Store 심사 대응, 운영 안정화 buffer |

## 13. 남은 최종 결정

1. 각 상품의 실제 가격과 Store product ID
2. 활성 이용권 중 추가 구매를 서버에서 완전히 금지할지
3. 환불/revoke 시 이미 열린 미완료 AttemptGroup을 계속 보장할지
4. iOS·Android 동시 출시인지 플랫폼별 canary인지
5. 상품 노출 순서와 추천 상품
6. 결제 CS·수동 reconciliation 담당자

## 부록 A. 관련 문서

- 범위 영향: `docs/codex/FIRST_RELEASE_PAYMENT_SCOPE_IMPACT.md`
- 전체 진행 상태: `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`
- Billing 경계: `docs/codex/FIRST_RELEASE_BILLING_BOUNDARY_REVIEW.md`
- 시험 권리 정책: `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`
- 프론트 비로그인 변경: `docs/codex/FRONTEND_NON_LOGIN_UPDATE_GUIDE.md`
- Billing 최신 결정: `/Users/msde76/billing/docs/codex/CONTRACT_DECISIONS.md`
