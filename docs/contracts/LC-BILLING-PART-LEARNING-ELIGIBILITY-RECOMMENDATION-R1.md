# 파트별 학습 — LC/Billing 최소 연동 권장안 R1

- 작성일: 2026-10-23
- revision: `lc-billing-part-learning-eligibility-recommendation-r1-review-2`
- 상태: Billing 검토 회신 반영본. 공동 최종 wire 동결·fixture 실행·제품 구현·배포 완료가 아니다.
- 기준: [학습 계획](../codex/PART_LEARNING_ACCESS_RESUME_IMPLEMENTATION_PLAN.md), 사용자 전달 Billing 보완본, [기존 workload 인증](LC-BILLING-WORKLOAD-AUTH-R1.md), [A 수락 기록](LC-BILLING-WORKLOAD-A-ACCEPTANCE-R1.md).
- 기존 인증/fixture 원본과 기존 재답변 API를 수정하지 않는다. Billing 회신 `LC-BILLING-PART-LEARNING-ELIGIBILITY-R1-REVIEW.md` 및 참조 `lc-billing-feedback-reanswer-eligibility-r1-draft-5` §8/9/13을 대조했다. 후자의 SHA-256은 `51fd0fb8fa43cffcf812fbaae717a43b04c1dfe937dcd30958b5750a819d746e`이며 로컬 원문 hash와 일치했다. 이는 참조 식별 증거이지 신규 fixture 동결 증거가 아니다.

## 1. 권장 결론

유료 학습 콘텐츠 제공과 신규 채점 접수에 유료권 전용 읽기 조회를 연결한다. 요청 간 캐시·세트 시작 기록·승인 receipt·새 컬렉션·복구 worker·분산 예약은 만들지 않는다. 첫 무료 세트·기존 결과 조회·기접수 Job 처리는 별개다.

현재 유효한 유료권만 인정하고 무료 체험/복원/초대/추천인 권리는 제외한다. 학습 조회로 발급·활성화·예약·차감·기간 변경·원장 쓰기를 하지 않는다. 기존 재답변의 무료권 허용 및 요청별 조회 규칙은 유지한다.

## 2. 요청과 인증

```http
POST /internal/v1/entitlements/part-learning-eligibility
Content-Type: application/json
Authorization: Bearer <LC workload JWT>
```

```json
{"userId":"11111111-1111-4111-8111-111111111111"}
```

- 위 UUID는 문서용 가짜 값이다. 실제 값은 LC가 인증한 현재 사용자 UUID이며 앱 body/path/query에서 받지 않는다.
- 요청 JSON은 userId 한 필드만. 필수 lowercase canonical UUID, 문자열이며 null/unknown/duplicate/trailing/coercion을 거절한다. UUID v4만으로 제한하지 않는다.
- 멱등키와 query는 사용하지 않는다. redirect 금지, 요청 내 자동 재시도 없음. 연결2초/요청5초는 기존 HTTP client의 timeout 의미까지 대조한다.
- 기존 LC 전용 서명키·issuer·sub·JWT parser/시간/rotation 규칙을 재사용하되 audience는 단일 문자열 `tosunsaeng-billing:part-learning-eligibility:{env}`로 분리한다. 이 aud는 이 exact POST만 허용한다.
- env는 기존 workload 설정의 exact 값을 사용한다. 배포 프로필명 prod 등을 JWT 환경 문자열로 임의 치환하지 않는다. test/prod 배포 설정과 실제 issuer/aud 대응을 공동 확인한다.
- 새 route 인가는 기존 인증 계약의 확장이다. 키 재사용만으로 route가 이미 허용된 것으로 보지 않는다. 다른 목적 aud, 사용자 JWT, 잘못된 서비스 토큰은 허용하지 않는다.
- 요청/응답 body 상한은 각각16 KiB(16,384 bytes)다. 요청 초과는 내부400 INVALID_REQUEST, 응답 초과는 LC 계약 오류다. 내부 오류 JSON은 draft-5 §8의 code/message/retryable/correlationId 네 필드를 재사용한다. correlationId는 응답별 lowercase UUID v4다. unknown/null/누락/중복은 거절하며 인증 경계 빈 body를 JSON으로 강제 파싱하지 않는다.
- 모든 HTTP 응답은 Cache-Control:no-store다. 신규 route 인가와 기본 OFF flag/전달 경로 준비가 필요하며 기존 키 사용만으로 호출 가능하다고 판단하지 않는다. 기존 재답변 route/flag는 그대로 유지한다.
- 토큰·키·사용자 body 원문은 로그/경보에 기록하지 않는다.

## 3. 응답 wire 권장값

업무 판정은 HTTP200, wrapper 없이 아래 다섯 필드만 반환한다. 모든 필드는 필수이며 validUntil만 nullable이다.

```json
{"userId":"11111111-1111-4111-8111-111111111111","decision":"ALLOWED","reason":"ACTIVE_PAID_ENTITLEMENT","checkedAt":"2026-10-23T09:00:00Z","validUntil":"2026-10-23T09:00:05Z"}
```

| decision | reason | validUntil |
| --- | --- | --- |
| ALLOWED | ACTIVE_PAID_ENTITLEMENT | 필수 non-null 시각 |
| DENIED | PAID_ENTITLEMENT_REQUIRED | null |
| DENIED | ACCOUNT_WITHDRAWN | null |
| PENDING | ENTITLEMENT_STATE_UNCONFIRMED | null |

위 표 이외의 조합은 계약 오류로 처리한다.

- LC는 userId가 요청 사용자와 exact 일치하는지 검증한다. 다른 사용자 결과로 허용하지 않는다.
- unknown/duplicate/trailing 필드, 누락, 잘못된 타입/enum/조합은 계약 오류다. 비허용 validUntil은 생략이 아니라 null이다.
- checkedAt/validUntil은 UTC `Z` 문자열로 소수부 없음 또는1~9자리까지 허용하는 안을 권장한다. Instant 정밀도를 보존하고 밀리초로 잘라 경계를 바꾸지 않는다.
- reason 문자열은 이 문서의 제안값으로 동결 요청한다. 기존 재답변 reason을 변경하는 요청이 아니다.

## 4. Billing 판정과 시각 경계

- 현재 사용자 소유의 검증된 유료권이 `startsAt <= checkedAt < endsAt`이고 환불/취소/탈퇴 등 deny가 없으면 ALLOWED다.
- 활성 paid가 확인되면 다른 구매의 pending sync만으로 PENDING으로 바꾸지 않는다. 다만 사용자 귀속·선택한 권리 자체가 불명이면 부족으로 단정하지 않는다.
- Billing의 기존 단일 ACTIVE slot 판정과 기간 중첩 정합성 검사를 유지한다. 복수 ACTIVE에서 최대 종료 시각을 선택하지 않는다. 중첩 정합성 실패는 기존503으로 처리하며 별도 구매 ID를 LC에 보내지 않는다.
- `validUntil = min(checkedAt + 5초, 현재 단일 ACTIVE slot의 activeEndsAt)`이다. continuousAccessEndsAt이나 후속 scheduled 종료를 사용하지 않는다. 정확히 activeEndsAt이면 불허한다.
- LC는 ALLOWED의 `checkedAt < validUntil <= checkedAt + 5초`를 검증한다. 모순된 시간은500 계약 오류다.
- 수신 시와 콘텐츠 제공 결정/신규 접수 직전에 `now < validUntil`을 확인한다. 정확히 경계이거나 이미 지났으면503이며 새 조회를 자동 발행하지 않는다. 네트워크가 응답 유효시간을 소진한 것은 이용권 부족402가 아니다.
- 미래 checkedAt 허용오차는 추가하지 않는다. 수신 시 `checkedAt <= receivedAt < validUntil`, 사용 시 `receivedAt <= guardNow < validUntil`이다. 호출 직전 monotonic 시작값으로 수신/사용 시 모두 `0 <= elapsed < 5초`를 확인하며 같은 요청에서 시작값을 재설정하지 않는다.
- 단순 만료/지연/elapsed5초 도달은503, 미래 checkedAt·시계 역행·손상/모순 시간은500+운영 경보다. JWT의30초 skew를 업무 판정에 적용하지 않는다. 양측 시계 동기화는 활성화 전 확인하며 이 검사가 분산 원자성을 제공한다고 보지 않는다.
- LC 원격 HTTP는 LC의 쓰기 Transaction 밖에서 수행하고 로컬 접수 전 시간·소유권·삭제 검사를 유지한다. Billing 내부는 기존 EntitlementSnapshotExecutor의 읽기 전용 Mongo snapshot Transaction을 사용한다. checkedAt은 snapshot 시작 전에 고정하고 snapshot 재시도로 연장하지 않는다. 읽기 Transaction은 원장 쓰기가 아니다.
- Billing 기존 reader의 recovery/readiness·MEMBER evidence·귀속/구매/원장/lifecycle 정합성을 유지한다. unrelated pending sync를 이유로 차단하지 않는 규칙이 실제 귀속 충돌·미처리 환불 증거를 무시하는 것은 아니다. 신규 endpoint에서 무료 컬렉션/예상 trial 판정은 조회하지 않는다.

## 5. LC 연결 지점

| 경로/동작 | 권장 처리 |
| --- | --- |
| 첫 무료 세트 | Billing 미호출, 기존 인증/삭제 보호 유지 |
| 목록·진도·기존 결과·polling | Billing 미호출. 목록에 유료 원문을 섞지 않음 |
| 유료 unit 상세 | Part1 포함, 본문/모범답안/음성 URL 제공 결정 전 조회 |
| 별도 유료 media URL | 서버에서 소속 unit 확인 후 조회. 무료/공용 썸네일 구분 |
| capabilities | 준비 상태만이면 미조회. 유료 원문을 포함하면 제공 부분 검사 |
| Part2~5 create | 현재 awaiting_audio 준비만 하므로 미조회. 현행 생성 한도 적용은 유지 |
| upload-url | 사용자 녹음 업로드용 URL이면 소유권 등 기존 검증만. 유료 콘텐츠 다운로드와 구분 |
| 신규 submit | 음성 freeze 처리 뒤, queue 저장 전 조회 및 유효시간 검사 |
| 이미 queue된 동일 요청 replay | 기존 접수/owner 근거로 재현, 신규 조회 없음 |
| 접수된 실패 Job retry/worker/결과 저장 | 기존 queuedAt·상태 등 실제 접수 근거가 있는 처리만 재조회 예외. failed만으로 허용하지 않음 |
| Part1 completion | 콘텐츠 유료 검사는 앞단 상세/media에서 수행. 기존 완료 기록 API를 유지하고 새 학습 승인 receipt를 요구하지 않음 |

신규 submit 조회가 거절/만료돼도 queue에 넣지 않는다. 기존 awaiting_audio 및 파일 처리 재시도 경로로 다시 요청할 수 있어야 하며 별도 보상 worker는 추가하지 않는다. S3 처리와 DB queue 실패 경로는 테스트로 확인한다.

이미 제공한 콘텐츠·음성 URL은 회수하지 않는다. URL의 기존 만료는 조회5초와 별개다. 콘텐츠 조회 허용을 나중 submit의 허용으로 재사용하지 않는다. 사용자 인증·현재 소유권·삭제·탈퇴 검사는 무료/유료 및 기접수 예외 모두에 유지한다.

## 6. LC 공개 오류 매핑

| 상황 | HTTP / code | 처리 |
| --- | --- | --- |
| 정상 DENIED / PAID_ENTITLEMENT_REQUIRED | 402 ENTITLEMENT_INSUFFICIENT | 학습 문구: 유료 이용권이 필요합니다 |
| 정상 DENIED / ACCOUNT_WITHDRAWN | 401 ACCOUNT_WITHDRAWN | 기존 탈퇴 안내 |
| PENDING·timeout·일시 transport 장애·문서화된 일시 Billing 오류 | 503 BILLING_TEMPORARILY_UNAVAILABLE | 학습 문구: 학습 이용권을 일시적으로 확인할 수 없습니다 |
| 정상 ALLOWED이나 사용 전 validUntil 경과 | 503 BILLING_TEMPORARILY_UNAVAILABLE | 자동 재시도/queue 없음 |
| 내부400/401/403/404·잘못된 경로·계약 오류 | 500 COMMON500 | 운영 경보, 사용자 로그인 오류로 전달 금지. Retry-After 없음 |

LearningExceptionAdvice에 명시 mapping을 추가한다. 알 수 없는 예외를 현재 generic503으로 보내는 경로에 내부 인증/계약 오류가 섞이지 않게 한다. 앱 자체400/401/403 및 BaseResponse 구조와 기존 모의고사 문구는 변경하지 않고 학습 문구만 적용한다. 경보에는 고정 분류·비민감 진단값만 남긴다. 단순 연결 실패를 주소 오류로 단정하지 않으며 확인된 인증/신뢰/주소 결함만500으로 분류한다.

내부400은 `INVALID_REQUEST / The request is invalid. / false`, 내부503은 `BILLING_TEMPORARILY_UNAVAILABLE / Billing is temporarily unavailable. / true`와 Retry-After:1이다. 공개 일시503도 Retry-After:1을 사용한다. OFF/잘못된 local port의 빈404, 활성 인증/query 실패 빈401, 인증 후 목적/method 인가 실패 빈403 우선순위를 유지한다. 요청 내 자동 재시도0이며 기존 경보/circuit을 재사용하고 새 복구 시스템은 만들지 않는다.

## 7. 최소 검증 목록

아래는 명세이며 실행 결과가 아니다. 가짜 사용자·고정 시각·테스트 키만 사용하고 기존 fixture 원본은 보존한다.

| ID | 검증 |
| --- | --- |
| L01 | 무료 세트/목록/polling/create 미호출, Part1 포함 유료 상세/media 조회 |
| L02 | 신규 submit만 queue 직전 조회, 기접수 replay/retry는 재조회 없음. 접수 증거 없는 실패 상태는 우회 불가 |
| L03 | 무료권만 보유 거절, 유효 paid 허용, unrelated pending sync 비차단 |
| L04 | startsAt/activeEndsAt 직전·정각·직후 및 환불/취소/탈퇴/PENDING. ACTIVE 중첩503, 후속 scheduled 종료로 유효기한 연장 없음 |
| L05 | validUntil 도착 전 소진·정각 거절, 나노초 정밀도·미래checkedAt 무허용·monotonic5초 경계·wall clock 역행·같은 요청 시계 초기화 금지 |
| L06 | userId 불일치·필드/enum/null/조합·strict JSON·크기 상한 |
| L07 | 전용 aud 성공·타 목적 aud/사용자 JWT·OFF/다른 port. 내부400/401/403/404→공개500 |
| L08 | 앱 자체400/401/403 유지, 업무402/401와 일시503/결함500 구분·Retry-After·학습 문구·요청 내 재시도0 |
| L09 | 요청 간 캐시 없음, 구매 후 다음 요청 새 조회, 읽기 조회 전후 권리/기간/원장 불변 |
| L10 | freeze 성공 후 조회 실패/만료·queue 실패·같은 요청 재시도, 무승인 queue 없음 |
| L11 | 기존 owner/삭제/탈퇴·기접수 근거 확인, 세트 시작/receipt 저장소 추가 없음 |
| L12 | 기존 재답변/모의고사 비영향, 기존 S3/AI 계약 유지 |

학습 신규 생성300회·실패 재시도 일일 차감 제외·UTC 초기화는 LC 계획의 별도 변경이며 Billing 조회 정책이나 비용 차감과 결합하지 않는다. 이어 풀기는 앱의 기존 목록 기반 계산을 유지한다.

## 8. Billing 수락 요청 및 다음 순서

1. 전용 route/aud·네 가지 응답 조합·읽기 전용 paid 판정 수락 여부.
2. 위에 대조한 draft-5 revision/hash 및16KiB·오류/엄격 시각 규칙을 새 계약 참조로 확인. 최대 종료 선택·미래1초 허용 후보는 폐기했으며 추가 구조는 요청하지 않는다.
3. LC 연결 지점/무캐시/시각 경계와 L01~L12를 공동 검토한 뒤 별도 revision으로 동결.
4. 동결 후 LC/Billing 구현과 독립 테스트, 이후 연동 테스트. 배포·활성화는 별도 승인.

이번 문서 작성으로 타 저장소 수정·실DB 조작·외부 전송·Jira·commit/push·배포를 수행하지 않았다.
