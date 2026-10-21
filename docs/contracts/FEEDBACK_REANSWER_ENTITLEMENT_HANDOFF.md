# 완료 시험 재답변 자격 조회 준비

## 5줄 결론

1. 완료 시험의 `retryCount > 0` 재답변은 현재 유효한 유료 이용권 또는 사용 가능한 무료권 1장 이상이 있을 때 허용한다.
2. 조회 시 무료권 발급·예약·차감은 없다. 유효 유료권도 없고 무료권도 0이면 마지막 시험 완료 직후라도 거절한다.
3. 부족 확정 시 기존 HTTP 402 / `ENTITLEMENT_INSUFFICIENT`를 재사용한다.
4. 사용자 구현 승인 후 draft-5 기반 LC 전용 workload 조회·Job 승인 증거·후속 채점 연결을 구현했다. Billing public 사용자 JWT 조회는 사용하지 않는다.
5. 기본 OFF이며 공동 fixture/실제 연결 검증·배포·활성화는 미완료다. 아래 준비 단계 조사 기록은 구현 전 이력이다.

## 2026-10-21 LC 구현 상태 (draft-5, 공동 동결 아님)

- 브랜치 `codex/fix-feedback-reanswer-entitlement`; `BILLING_REANSWER_ENABLED=false` 기본값. ON에는 기존 workload sender와 user-merged writer(owner Transaction), Billing HTTPS origin이 필요하다.
- 전용 `POST /internal/v1/entitlements/reanswer-eligibility`, audience `tosunsaeng-billing:reanswer-eligibility:{env}`, body `userId` 하나. 기존 서명키/issuer/sub를 사용하며 앱 토큰을 전달하지 않는다. 연결2초·요청5초, redirect 금지·호출당 application retry 없음·16KiB 응답 제한.
- upload-url 사전 조회와 submit 최종 조회는 별개다. 완료 Session의 retryCount>0만 대상이며 기존 closure는 삭제하지 않는다. 기존 Job은 승인 증거를 소급 보강하지 않는다.
- `QuestionGradingJob.reanswerApproval` embedded BSON: `type`, `revision`, `userId`, `checkedAt`, `validUntil` 모두 string. 시각 원 문자열의0~9자리 소수초를 유지한다. revision은 명시적으로 `lc-billing-feedback-reanswer-eligibility-r1-draft-5`이며 최종 동결로 위장하지 않는다. 기존 `firstUserSubmittedAt` Instant/BSON Date 유지, Job과 증거는 같은 owner Transaction으로 최초 저장한다.
- 응시권 부족402 ENTITLEMENT_INSUFFICIENT / 실제 탈퇴401 ACCOUNT_WITHDRAWN / timeout·PENDING·만료503 BILLING_TEMPORARILY_UNAVAILABLE(Retry-After1) / 서버 인증·경로·응답 계약 결함500 COMMON500. 미래 checkedAt·시계역행은500. 원문/token 없이 고정 category/status의 예외로 기존500 경보 경로를 사용한다.
- 승인 원 사용자 snapshot은 이후 owner migration 때 덮어쓰지 않는다. 현재 owner·삭제·탈퇴·해당 Session 환불 차단은 계속 우선한다. 승인 유효시간은 신규접수에만 적용하고 같은 durable Job 후속 채점에 적용하지 않는다.
- 기존 Reservation/AttemptGroup circuit은 변경하지 않았다. 해당 circuit은 별도 worker 목적용이므로 재답변 조회와 결합하지 않았다. 신규 조회의 지속적 인증 실패 차단 운영 방법은 공동 활성화 점검에 포함한다. 새로운 자동복구 worker/재조회 loop는 없다.

### 검증 및 배포 전 남은 일

- `./gradlew clean test`: 전체 단위/회귀 테스트 통과(최종 결과는 WORKLOG). 종료된 완료시험 제출·증거 저장·중복 replay·만료 후 완료, 무효 응답/오류 구분, 전용 JWT 목적, 기본OFF/기동 의존 조건 검증 포함.
- `mongoIntegrationTest --tests '*ReanswerApprovalMongoTest'`: Docker provider 연결 실패로 실제 Mongo 검증은 미실행. 테스트 코드는 Job+증거 rollback 및 BSON string 나노초 왕복을 검사하도록 추가했다.
- Billing 새 endpoint/auth 배포와 공동 golden fixtures, 격리 Mongo 원자성 검증, owner/삭제/환불 동시 경합 E2E, 양 서버 시각 동기화 및 실제 private HTTPS 연결 검증 후 활성화한다. 현재 테스트는 Billing 실제 실행·무차감 DB 검증을 대체하지 않는다.
- 커밋·푸시·배포·실DB/index 변경 및 Billing 저장소 변경 없음. 기존 A fixture bytes와 공개 API/AI/S3/Redis 계약 유지.

## 반드시 읽을 내용

- 완료 여부와 별개로 다음 시험 confirm 시 이전 Session에 submissionsClosedAt이 기록되어 현재 재답변도 차단된다.
- 이 marker를 삭제하거나 기존 금융/교체 증거를 변경하지 않는다.
- 접수만 열어서는 안 된다. dispatch/Callback의 기접수 증거 검사도 허용된 재답변의 durable Job을 인정해야 한다.
- 교체된 미완료 시험, 환불/삭제/탈퇴/소유권 제한은 유지한다. retryCount 양수만으로 Callback을 허용하지 않는다.
- 기존 공개 URL/DTO/BaseResponse, AI user_id=examId, S3/Redis key, retry0 종합요약 기준은 유지한다.
- 사용자 공유 기준: 이미 소비한 시험의 재응시 가능 여부는 잔여권으로 인정하지 않는다. 기존 권리 조회를 활용하되 재답변 자격 판정은 분리한다.

## 추가로 필요한 기술 계약

### 2026-10-21 사용자 수락 사항

- Billing `LC-BILLING-FEEDBACK-REANSWER-ELIGIBILITY-R1-DRAFT.md` draft-1의 추가 제안 두 가지에 대해 사용자 확인을 받았다.
- **미발급 최초 무료권은 제외한다.** 발급 가능 상태만으로 ALLOWED를 반환하지 않으며 현재 유효 유료권 또는 이미 발급돼 사용 가능한 무료권1장 이상이 필요하다. 소비한 그룹의 retake도 제외한다.
- **최대5초 관측 기반 접수 경합을 수락한다.** 조회 후 소진·권리 변경과 아직 전달되지 않은 deny 때문에 서로 다른 Job 여러 건이 접수될 수 있으며1건 상한을 보장하지 않는다.5초는 commit 완료나 환불/탈퇴 이벤트 전파의 상한이 아니다.
- LC는 신규접수 Transaction 최종 guard 검사에서 요청 시작부터 monotonic 경과시간<5초 및 현재시각<validUntil을 모두 확인한다. 유료만으로 허용 시 validUntil은 유료권 만료시각을 넘지 않는다. 경계값/만료 응답은 승인으로 사용하지 않으며 JWT skew를 더하지 않는다.
- 조회 결과를 다른 사용자/Session/문항/새 녹음에 공유하지 않는다. upload-url 사전 확인과 submit 최종 조회를 분리한다. 이미 영속 접수된 같은 Job은 잔여 소진만으로 중단하지 않으며 기존 환불/삭제/탈퇴/소유권 deny가 우선한다.
- Billing 초안의 미발급trial 인정안과 “한 건” 표현은 위 결정에 맞춰 수정할 필요가 있다. 이번 수락은 정책 결정이며 Billing 문서 수정/외부 발송/공동 fixture 동결/제품 구현/배포 완료를 뜻하지 않는다.

사용자는 Billing에 LC 전용 무차감 자격 조회를 추가하는 방향을 확인했다. workload 인증의 정확한 규격은 후속 합의 대상이다.
정확한 endpoint·audience·body·response를 LC가 임의로 신설하거나 이미 존재한다고 가정하지 않는다.

- 입력은 LC가 검증한 현재 사용자이며, 현재 자격을 Billing이 판정한다.
- 자격 있음/없음/판정 보류를 구분한다. 장애·보류·인증 실패를 응시권 0으로 취급하지 않는다.
- 판정 기준은 현재 유효 유료권 OR 사용 가능 무료권 1장 이상이다. 단순 canTakeExam/retake를 사용하지 않는다.
- 응답시각/유효시간, 조회와 로컬 접수 사이 만료·환불 경합, same-Job 재전송 처리 기준을 확인한다.
- 사용자 공유 기준: 신규 녹음의 최초 접수 직전에 조회한다. 이미 접수된 Job은 잔여권 소진만으로 중단하지 않는다. 접수 증거 저장과 만료·환불 경합의 구체적 처리 규격은 공동 확인 대상이며 환불·삭제·소유권 보호는 계속 적용한다.
- 앱 토큰 전달 방식은 별도 인증·전달 계약 없이는 채택하지 않는다.
- reserve→cancel로 조회를 흉내내거나 LC에서 Billing DB를 직접 읽어 잔여권을 계산하지 않는다.

## 구현 및 검증 범위

- 완료/재답변/현재 사용자/문항 유효성/현재 자격의 공통 접수 정책.
- upload-url과 submit의 일관된 거절, submit 최종 권한 재확인 및 소유권 Transaction 보호.
- 허용된 재답변의 durable 접수 표식 및 후속 dispatch/Callback 검사 일치.
- legacy 및 유료 완료 시험, 다음 시험 생성 전후, 기존 closure 문서, 잔여0, 조회 장애, 환불·삭제·merge 경합, 멱등 replay, 요약 불변 테스트.
- 커밋/푸시/배포/실DB 보정은 이번 준비에서 수행하지 않는다.

## 조사 근거

- LC `BillingReservationClient`: reserve/confirm/cancel/status/phone continuation만 존재.
- LC `PaidExamLifecycleService`: confirm 성공 시 이전 Session 제출 차단.
- LC `ExamGradingService`, `ExamAcceptedWorkPolicy`: 신규접수/후속 채점의 closure 검사.
- Billing 기존 계약 `BILLING_SERVICE_INTEGRATION_CONTRACT.md`의 public `GET /api/v1/entitlements`: 사용자 JWT/Billing audience/billing:read.
- Billing `ExamAvailability`: canTakeExam은 newAttempt 또는 retake 허용으로 산출되므로 잔여권 보유와 동일하지 않다.
- Billing `PaidQueryResponse`: 수량 대신 active/만료시각으로 기간형 권리를 표현한다.

Billing 근거는 로컬 읽기 확인이며 운영 API/설정 검증이나 Billing 코드 변경이 아니다.
