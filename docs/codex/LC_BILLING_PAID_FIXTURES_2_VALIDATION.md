# LC paid fixtures-2 독립 검증·인수 기록

## 5줄 결론

1. `lc-billing-paid-exam-r2-fixtures-2`를 Billing 원본 그대로 인수했고 SHA256SUMS 포함14파일의 bytes가 동일하다.
2. LC 자체 Java/JCA/Jackson/Instant 기반 오프라인 검증89개가 실패·오류·skip 없이 통과했다.
3. 최종 `./gradlew clean test`948개도 실패·오류·skip 없이 통과했다(89개 포함, 합산하지 않음).
4. 실제 HTTP/서비스/Transaction·상태26시나리오는 실행하지 않았고 S15/V07은 보류한다.
5. 제품 코드·DB·배포·활성화·A 계약/fixtures는 변경하지 않았으며 최종 공동 계약 동결은 별도다.

## 반드시 읽어야 하는 내용

- 날짜: 2026-10-14. 관련 Billing PLAN-011/012/013, LC TMI-116/TMI-128/TMI-122 및 lifecycle TMI-125.
- 사용자 승인 범위: 원본 revision/SHA256SUMS bytes를 유지한 LC 자체 독립 검증. 실제 서비스 구현/배포/활성화 제외.
- 생산자: `/Users/msde76/billing/src/test/resources/contracts/lc-billing-paid-exam-r2-fixtures-2`.
- LC 인수본: `src/test/resources/contracts/lc-billing-paid-exam-r2-fixtures-2`.
- SHA256SUMS 자체 SHA-256: `bb4458f3e7b6ed801795fbd652fc41e2d5e6c6c2d752b9ef693d6f43e4893659`. 테스트에 이 값을 고정하고 목록13파일도 전부 검증한다.
- 기준 계약: `lc-billing-paid-exam-contract-r2-draft-2`, 생산자 manifest의 계약 SHA-256 `3210c7eef1fb2f9fdde101c2384c86a5bbde1f9be346da0651c971e357444c52`를 고정 대조한다. 계약 원문은 앞선 검토에서 확인한 값이며 이번 테스트가 전체 계약 실행을 증명하지 않는다.
- 원본 README/manifest의 `lcIndependentValidation=false`는 생산 당시 상태이므로 수정하지 않았다. 이번 LC 검증 결과는 본 기록으로 별도 수락한다.

## 사용자가 결정해야 하는 사항

이번 오프라인 검증을 마치는 데 추가 선택은 없다. 공동 최종 동결, 미확정 S15/V07 정상 기대값, 서비스 구현 착수 및 실환경 검증/활성화는 별도 합의·승인으로 진행한다. 이 결과를 미실행 업무 정책이나 권한 확대의 승인으로 사용하지 않는다.

## 주요 위험과 미확인 사항

- HTTP12개는 독립 오프라인 판정표 대조다. 실제 Spring security chain/connector/port/TLS/no-store/DB 무호출을 검증하지 않았다.
- wire6/status3은 정적 DTO 조합 검증이다. 실제 reserve/confirm/replay/무료권 복원/권리 선택의 실행 결과가 아니다.
- state-scenarios26개의 `NOT_EXECUTED_REQUIRES_IMPLEMENTATION` 표시만 확인했다. 경합·원자성·unknown commit·durable 접수·legacy·삭제/보존이 통과했다고 해석하지 않는다.
- S15(phone/삭제 continuation 정상 경로), V07(보존/purge 상세)은 계속 보류한다. 운영 origin/port/키/trust 및 E2E도 별도 gate다.
- 현재 fixture 입력을 검증하는 test-only oracle이다. 런타임 verifier/serializer로 설치하거나 모든 가능한 입력의 검증 완전성을 주장하지 않는다.

## 직접 관련된 구현과 독립성

새 테스트 `src/test/java/web/tosunsaeng/contracts/LcBillingPaidFixtureTest.java`는 Billing Python 검사기/Node 생성기/제품 codec을 호출하거나 복사하지 않는다. 기존 LC A oracle의 JCA/Jackson 접근을 참고해 역방향 프로필과 paid R2 canonical/body/wire를 테스트 전용으로 구현했다. A 테스트 source는 변경하지 않았다.

- 원문 JWT의 header.payload bytes를 JCA SHA256withRSA로 검증한다. claims를 재직렬화해 검증하지 않으며 기대 상태는 결과 비교에만 사용한다.
- JWK로 RSA public key를 구성하고 PEM SPKI bytes와 대조한다. kid/keySet을 고정하며 key setup 오류를 기대401로 삼키지 않는다. 원격 JWKS/네트워크는 사용하지 않는다.
- Jackson duplicate/trailing 거절, UTF-8 strict decode, lone surrogate 거절, 정수 NumericDate 및 java.time.Instant 나노초 비교를 사용한다.
- 문자열은 scalar 단위로 직렬화하고 event는 계약 필드 순서로 재배열한다. raw input→canonical bytes/hash 및 round-trip을 검증한다. Unicode normalization/float 시간 변환을 하지 않는다.
- 실패 시 token/parser 원문을 출력하지 않는다. 테스트 리소스의 생산자 발급 가짜 서명 token만 인수하며 실 Secret/Token/private key를 기록하지 않는다.

## 부록 — 실행 증거와 범위

실행 환경: OpenJDK21.0.11 Microsoft build, Gradle Wrapper9.5.1(gradle-wrapper.properties 기준). 기존 의존성의 Jackson/JUnit/JCA를 사용했고 새 의존성은 없다.

| 독립 검사 | 개수 | 결과 | 한계 |
| --- | ---: | --- | --- |
| 원본 SHA256SUMS·13파일·manifest/보류 범위 | 1 | PASS | 업무 시나리오 실행 아님 |
| canonical bytes/hash/round-trip | 18 | PASS | event 및 문자열 codec fixture |
| invalid event·크기/JSON/형식/지원 오류 우선순위 | 13 | PASS | test-only body oracle |
| 정확 epoch nanoseconds | 5 | PASS | 고정 clock |
| raw RSA token·rotation·시간 ±1ns·principal | 31 | PASS | 실제 HTTP 아님 |
| HTTP 판정표 | 12 | PASS | 오프라인 모형, 서버 없음 |
| reserve 요청/응답 조합 | 6 | PASS | structural only |
| status/거절 조합 | 3 | PASS | structural only |
| 합계 | 89 | 실패0/오류0/skip0 | runtime 미검증 |

명령과 결과:

```sh
./gradlew test --tests '*LcBillingPaidFixtureTest'
./gradlew clean test
diff -qr /Users/msde76/billing/src/test/resources/contracts/lc-billing-paid-exam-r2-fixtures-2 src/test/resources/contracts/lc-billing-paid-exam-r2-fixtures-2
# LC fixture 폴더에서:
shasum -a 256 -c SHA256SUMS
git diff --check
```

선별검사89개 통과 후 SHA256SUMS 자체 hash pin을 추가했고 최종 clean test도 실행했다. 전체 JUnit XML 합계948개, 실패0/오류0/skip0이며 paid fixture89개도 동일하게 통과했다. 캐시 sandbox 접근 오류는 승인된 Gradle 실행으로 해소했다. 기존 deprecated/unchecked 컴파일 경고는 범위 밖으로 유지했다.

케이스별 결과는 `build/test-results/test/TEST-web.tosunsaeng.contracts.LcBillingPaidFixtureTest.xml`의 fixture ID별 testcase89개에 기록된다. 테스트가 fixture ID 중복과 각 파일 건수를 확인한다. HTML 보고서는 `build/reports/tests/test/index.html`이다. build 산출물은 다음 clean 시 재생성되므로 재현 가능한 source/원본 및 이 요약을 함께 유지한다.

인수 후와 최종 테스트 뒤 `diff -qr` 차이 없음, LC SHA256SUMS13개 모두OK. 기존 revision 재생성이나 Billing 파일 수정 없음. 기존 dirty .DS_Store/문서 변경은 보존했고, 이번 변경은 새 테스트1개/인수 리소스14개/본 기록/CURRENT_STATE/WORKLOG에 한정된다. 공개 API/AI user_id=examId/S3/Redis 계약·feature flag·제품 코드 변경, 실제 DB/외부 HTTP/Jira/commit/push는 없다.
