# Billing paid fixtures-1 읽기 검토

- 날짜: 2026-10-14 / 관련 PLAN-011/012/013, TMI-116/TMI-128/TMI-122, TMI-125
- revision: lc-billing-paid-exam-r2-fixtures-1
- 원본: /Users/msde76/billing/src/test/resources/contracts/lc-billing-paid-exam-r2-fixtures-1
- 범위: 사용자 요청으로 Billing 원본/생성기/검사기/보고서를 읽고 기존 검사기를 재실행. Billing 파일 수정·fixture 복사/재생성·실제 서비스/DB/배포 없음. LC 독립 구현 검증은 수행하지 않았다.

## 확인 결과

- 계약 원문은 직전 사용자 전달 R2 draft-2와 diff 차이 없음. SHA-256 `3210c7eef1fb2f9fdde101c2384c86a5bbde1f9be346da0651c971e357444c52`가 manifest와 일치한다.
- shasum -a 256 -c SHA256SUMS:12개 모두OK.
- python3 scripts/contracts/verify-paid-exam-fixtures-r2.py 재실행: OFFLINE_CHECKS_PASSED. canonical9/invalid13/token22/HTTP판정표12/wire예시6/status예시3.
- 상태 시나리오26개는 NOT_EXECUTED_REQUIRES_IMPLEMENTATION으로 명시. HTTP는 로컬 판정모형이고 실제 Spring chain/port 테스트가 아니다. S15/V07 보류·LC독립검증false·runtimeTestedfalse 표시 적절.
- 생성기 source는 ephemeral RSA private key를 메모리에서 생성하고 public JWK/PEM만 저장하도록 작성됐다. 이번 검토에서 생성기를 실행하지 않았다. 기존 A fixture 경로에 git status 변경 표시 없음.

## 보완점 1 — canonical 문자열 생성의 escape 치환

generate-paid-exam-fixtures-r2.mjs:13은 JSON.stringify 뒤 정규식으로 짧은 control escape를 치환한다. 이 방식은 원래 문자열에 문자 그대로의 역슬래시와 n이 있을 때 escape된 역슬래시의 두 번째 문자부터 매칭해 문자열 의미를 바꾼다. 비민감 문자열만으로 같은 표현식을 실행해 JSON.parse(output)===input이 false임을 재현했다.

현재9개 canonical vector에는 literal backslash+n/t/b/f/r 사례가 없어 기존 검사 통과와 모순되지 않는다. 생성기를 문자/scalar 단위 escape로 수정하고 이 회귀 및 연속 역슬래시+실제 control 혼합 사례를 추가할 필요가 있다. 원본 revision은 보존하고 fixture 변경은 새 revision/hash로 전달해야 한다. 기존 fixture bytes가 모두 잘못됐다고 판정한 것은 아니다.

## 보완점 2 — 고정 now 정밀도

verify-paid-exam-fixtures-r2.py의 epoch()는 datetime.fromisoformat(...).timestamp()의 float를 Decimal로 바꾼다. datetime 단계에서 마이크로초 아래를 잃으며 float 경로도 exact 소수초를 보장하지 않는다. `2026-10-14T00:01:29.999999999Z`와 다음 정수초의 차이를 실제1ns가 아닌1µs로 표현함을 재현했다.

현재 token22개는 주로 정수초와 millisecond 경계라 모두 통과한다. 이 결과를 A 규칙의 나노초 고정clock 독립검증으로 확대할 수 없다. 초/소수부분을 직접 분리해 정수+Decimal로 계산하고 skew/expiry 경계 ±1ns를 추가하는 것을 권고한다. 현재22개에서 인증 결과가 틀렸다고 보고한 것은 아니다.

## 다음 단계

Billing에 위 도구 보완·새 회귀 사례를 전달할 수 있다(이번에는 외부 메시지 미전송). 원본은 고치지 않고 수정본 revision/hash를 받아 LC 자체 parser/crypto/serializer로 독립검증한다. 기존 검사기 재실행은 LC 독립검증을 대체하지 않는다. 이후 실제 sender/receiver/Transaction/경합과 보존/배포 gate는 별도다.

이번 검토는 전체 Gradle 테스트를 실행하지 않았다. Secret/Token 원문을 보고서/출력에 기록하지 않았으며 fixture 원문 token도 출력하지 않았다.

## 2026-10-14 후속 — fixtures-2 수정 재검토

이전 두 도구 지적은 수정 범위에서 해소됐다. 새 revision은 `lc-billing-paid-exam-r2-fixtures-2`이며 제품 계약 변경은 없다. 최종 공동 동결이나 LC 자체 독립 검증 완료를 의미하지 않는다.

- 생성기는 Unicode scalar별 escaping으로 바뀌었다. 파일 생성 부분을 실행하지 않고 실제 source의 canonical 함수만 격리해 문자열 fixture14개를 원 입력 round-trip 및 기대 bytes와 비교, 모두 통과했다. 고립 surrogate2개도 거절했다. literal backslash5개와 연속 backslash/control 혼합4개 회귀가 포함된다.
- 검사기 epoch는 float timestamp 대신 정수초와 소수부 Decimal을 사용한다. 이전 재현 입력의 간격이 정확히1ns임을 재확인했다. clock5 및 expiry/iat/nbf ±1ns를 포함한 token31개 모두 통과했다.
- Billing root에서 `python3 scripts/contracts/verify-paid-exam-fixtures-r2.py` 실행: hashFiles13/canonical18/exactNanosecondClock5/invalid13/tokens31/HTTP판정표12/wire6/status3 통과. 새 fixture 폴더의 `shasum -a 256 -c SHA256SUMS`도13개 모두OK. 계약hash 대조도 검사기에 포함돼 통과했다.
- 수정 검사기에 기존 fixtures-1 경로를 명시한 실행도 기존12파일/canonical9/token22 등 모두 통과했다. 이전 revision 파일의 현재 무결성 확인이며 이번 턴에서 수정 전 전체 byte snapshot을 새로 확보한 것은 아니다.
- 실제HTTP·상태26시나리오·LC 독립 parser/crypto 검증은 여전히 미실행, S15/V07 보류. 이번 범위의 추가 지적은 없다. 다음은 새 revision bytes/hash를 고정해 LC 자체 독립 검증하는 단계다.

Billing 원본/fixture/제품/API/DB/배포는 수정하지 않았다. LC 검토 기록과 CURRENT_STATE/WORKLOG만 갱신했으며 제품 변경이 없어 전체 Gradle은 미실행했다. 기존 dirty 변경을 보존했다. 관련 PLAN-011/012/013 및 TMI-116/TMI-128/TMI-122, TMI-125.
