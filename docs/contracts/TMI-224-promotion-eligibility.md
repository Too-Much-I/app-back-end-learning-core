# TMI-224 테스트 프로모션 적격 조회 계약

> 2026-10-28 운영 전달: 아래 테스트 계약의 목적·field·조건을 유지하며 운영 환경값은 production이다. 운영 issuer/audience의 환경 suffix는 production으로 맞추고 기존 운영 LC 키만 사용한다. 운영 main에 본 과제 변경만 전달하며 기본 OFF, 활성화는 dry-run 우선이다. 테스트키/테스트 endpoint를 운영에 사용하지 않는다. 실제 적격조회 및 단말 E2E는 별도 검증한다.

## 5줄 결론

1. LC는 Billing의 과거 비전액환불 구매 여부와 Identity의 현재 광고동의를 독립 인증으로 조회한다.
2. 기존 사용자 API와 결제 원장/권리/21시 알림은 변경하지 않는다.
3. 미확인·장애·동의없음·탈퇴는 발송하지 않는다.
4. 수·목 KST12:00 이상12:15 미만, 캠페인 접수기간 안에서 계정/캠페인/일자당 한 번 시도한다.
5. 기본OFF/dryRun이며 코드검증과 실환경 연결/실수신 완료는 별개다.

## 반드시 읽어야 하는 내용

| 대상 | POST 경로 | audience(test) | 환경변수 |
|---|---|---|---|
| Billing | /internal/v1/eligibility/promotion/query | tosunsaeng-billing:promotion-eligibility:test | BILLING_PROMOTION_ELIGIBILITY_ENABLED |
| Identity | /workload/v1/marketing-push/eligibility | tosunsaeng-identity:marketing-push-eligibility:test | MARKETING_ELIGIBILITY_ENABLED |

두 요청 body는 canonical UUID의 `userId` 하나다. 응답은 wrapper 없이 `userId`, boolean `eligible`, UTC `checkedAt` 세 필드. 성공은200/no-store. 동의 여부·결제상세·개인정보를 추가 반환하지 않는다.
JWT: RS256, typ=JWT, kid, issuer=urn:tosunsaeng:workload:learning-core:test, sub=learning-core-service, 목적별 audience 단일값, iat=nbf, exp=iat+60초, canonical UUID jti. 기존 LC 발신키를 재사용하며 사용자 토큰/Identity 사용자 서명키로 대체하지 않는다. Identity에는 LC 공개 JWK set만 등록한다. Billing은 기존 LC 전용 수신키와 TLS connector/port 검증을 유지한다.
Identity 설정: MARKETING_ELIGIBILITY_ENVIRONMENT=test, MARKETING_ELIGIBILITY_LC_PUBLIC_JWKS=공개키세트(실제 값은 문서/저장소에 기록하지 않음).
LC 설정: PROMOTION_PUSH_ENABLED, PROMOTION_PUSH_DRY_RUN, PROMOTION_PUSH_BILLING_ENDPOINT, PROMOTION_PUSH_IDENTITY_ENDPOINT, PROMOTION_PUSH_BATCH_SIZE(1~100,기본20). 기본값 enabled=false/dryRun=true. HTTPS exact path, redirect/자동재시도/양성cache 없음. 조회당 연결2초/본문포함 총3초, 응답16KiB 상한, checkedAt 과거10초/미래30초 범위.

## 결정할 사항

기존 프론트 route=CASHBACK_EVENT_HOME, type=CASHBACK_EVENT, 수신거부=설정 > 알림 > 이벤트·혜택 알림을 재사용한다. 추가 프론트 route 결정은 없다. 테스트 환경만 연결하고 운영은 변경하지 않는다.

## 위험과 미확인 사항

- FCM 접수 성공은 단말 실제 수신을 보장하지 않는다. 테스트 앱 클릭/철회 E2E 별도 필요.
- 발송 직전 재검사 이후 발생한 철회·신청과 외부 FCM 전송은 분산 원자성을 보장하지 않는다.
- durable marker 이후 crash/unknown commit/조회실패는 같은 날 재발송하지 않는다. 중복 방지를 우선하므로 누락 가능.
- 캠페인 접수기간과 발송창 밖에서 누적 발송하지 않는다. 전역 FCM auth/rate circuit은 기존 알림과 공유한다.
- Billing 조회는 한 계정 최대1000개 구매를 처리하고 상한 초과/불확실한 readiness는 발송하지 않는다. 무제한 결제 이력 스캔이 아니다.

## 현재 구현과 운영 순서

1. 테스트 코드 배포(기능OFF). Billing은 기존 전용 TLS target으로 exact 새 경로가 전달되는지 확인한다.
2. Identity에 LC 공개키·test issuer 환경을 연결하고 두 reader를 활성화한다. 새 키 생성/키 원문 노출 없이 기존 LC 키 사용.
3. LC 기존 campaign/notification index·lifecycle gate·FCM 인증과 workload sender를 검증한다. 신청 collection 및 notification_control 기존 TTL 사용, 새 collection/index 필요 없음.
4. LC dryRun으로 조회 연동 확인 후 실제 발송 활성화. 기존21시 알림 설정은 변경하지 않는다.
5. 검증 계정 수신/클릭, 광고동의 해제 후 제외 확인. 롤백은 PROMOTION_PUSH_ENABLED=false로 추가 sweep 중지, marker 삭제/재전송하지 않는다.

## 부록: 적격 의미 및 근거

Billing: ACTIVE account/lifecycle, 검증된 MEMBER evidence, 동일 계정의 성공 구매+entitlement 기록. 현재 기간이 만료돼도 포함한다. VERIFIED/ALLOWED/READY와 최초 검증 증거가 필요하다. REFUNDED는 STORE_PAID amount와 STORE_REFUND_GROSS refundedTotal의 통화/Decimal128을 확인하고 0<=환불액<결제액만 포함한다. 전액·불명·REVOKED 제외. 환불 후 다른 정상 구매가 있으면 포함한다. 기존 snapshot/readiness를 재사용하고 원장/권리를 쓰지 않는다.
Identity: ACTIVE MEMBER + 현재 버전 marketingPushConsented=true + consentedAt 존재. 공개 동의 API와 동일한 유효성 규칙(철회 후 같은 밀리초 재동의도 boolean 상태가 기준).
LC: ACTIVE MEMBER 기기, AUTHORIZED, 관측30일/활동90일 이내, 탈퇴/병합/학습기록삭제 차단 없음, 캠페인 신청0~3회. 기기 소유자/version 재확인 및 로컬 Tx marker 예약 뒤 결제/동의 재조회, 그 뒤 최신 기기/상한 재확인. 토큰을 marker에 저장하지 않는다.

코드: 각 서비스 domain/promotion, Identity domain/user/marketing, LC domain/notification/Promotion* 및 BillingWorkloadTokenIssuer. 격리 테스트는 Promotion*Test, MarketingEligibility*Test 및 LC NotificationMongoIntegrationTest에 있다.
