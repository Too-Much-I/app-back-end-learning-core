# LC 단순화 exact fixtures-1 인수·독립 검증

## 1. 5줄 결론

1. 2026-10-14 전달 ZIP에서 신규 fixture10파일을 원본 그대로 인수했다(SHA256SUMS 포함).
2. LC 자체 Java 검증80벡터+hash/범위1개, 총81개가 실패/오류/skip0으로 통과했다.
3. `./gradlew clean test` 전체1,029개도 실패/오류/skip0으로 통과했다(81개 포함).
4. 상태28개는 계속 미실행이며 실제 서비스/HTTP/worker/Transaction 테스트가 아니다.
5. 코드 변경은 test-only이고 기존 A/R2/fixtures-2·제품/DB/배포/활성화는 변경하지 않았다.

## 2. 반드시 읽어야 하는 내용

- 관련 PLAN-011/012/013, TMI-116/TMI-128/TMI-122/TMI-125, 삭제 TMI-193/TMI-136.
- 전달물: `/tmp/LC-BILLING-PAID-SIMPLIFIED-EXACT-R1-FIXTURES-1.zip`.
- LC 원본 인수: `src/test/resources/contracts/lc-billing-paid-exam-simplified-exact-r1-fixtures-1/`.
- fixture revision: `lc-billing-paid-exam-simplified-exact-r1-fixtures-1`.
- 계약 revision: `lc-billing-paid-exam-simplified-exact-r1-draft-3`.
- 계약 SHA-256: `79bcfe3d79225c21d712791266bea10d4aed9eed3dc0529ddf86f7b29673a482`.
- SHA256SUMS 자체 SHA-256: `35f7d437fb562e1427efe385ffaf1721a60f4780fcaacf495c2a0c7e787c2436`.

ZIP의 Billing 범위 한정 수락 기록을 읽고 위 revision/hash를 대조했다. 신규 fixture10파일은 ZIP entry와 직접 byte 비교해 동일함을 확인했다. contract-snapshot.md도 현재 Billing exact 계약 원문과 byte 동일하다. checksum 대상9파일 모두 일치하고 checksum 자체 hash도 테스트에 고정했다. 원본 README/manifest의 제작 당시 lcIndependentValidation=false는 수정하지 않으며 이번 LC 결과는 이 기록으로 구분한다.

이번 LC 인수는 검토된 규격의 원본 동일성과 아래 독립 오프라인 결과를 확인하는 범위다. Billing의 한정 수락을 전체 공동 계약 동결/제품 구현 승인으로 확대하지 않는다. 새 삭제 route/인증·과거 owner 삭제·자료별 최종 purge·실환경 gate는 제외한다.

## 3. 결정·후속 사항

새 fixture 인수·오프라인 검증의 추가 차단사항은 발견되지 않았다. 다음 구현 준비에서는 승인된 범위의 계획/ADR/AGENTS 동기화와 별도 구현 승인이 필요하다. 별도 삭제/보존 gate는 해당 계약에서 계속 확인한다. 상태28개의 실제 실행은 구현 뒤 테스트 계획으로 관리한다.

## 4. 주요 위험과 미확인 사항

- MEMBER 재개29개는 입력된 영속증거/lease/owner 등 boolean 사실의 **판정 모형**이다. 실제로 그 사실을 안전하게 생성/조회하거나 CAS/Transaction으로 처리한다는 증거가 아니다.
- phone11개는 선택된 immutable field subset을 비교한다. 실제 전체 HTTP DTO codec, 권리 선택·target 복원1회 Transaction을 검증하지 않는다.
- 오류22개는 strict raw JSON/category 모형, 공개4개는 envelope 예시 대조다. 실제 HTTP advice·Retry-After 송신/DB 무호출 테스트는 아니다.
- 시간14개는 UTC millisecond와 Duration120일의 경계·hold/unresolved predicate 검증이다. 실제 purge/백업 복원·금융/소비 보존 구현은 아니다.
- Billing이 보고한 파일변조 등 mutation 검사는 이번 턴에 별도 재실행하지 않았다. LC는 원본 hash pin과 전달된 정상/부정 벡터를 자체 구현으로 검증했다.

## 5. 직접 관련된 구현

`src/test/java/web/tosunsaeng/contracts/LcBillingSimplifiedFixtureTest.java`를 추가했다. Billing 검사기/생성기를 실행하거나 제품 모델로 import하지 않는다. 전달 verifier는 검증 범위 파악을 위해 읽었지만 Python source를 LC 검사기로 복사하지 않았다. LC 테스트는 Java/Jackson strict duplicate/trailing·UTF-8/scalar 검사, java.time.Instant/Duration, JCA SHA-256 및 계약 기반 별도 판정 코드를 사용한다. 예상 label은 비교에만 사용하며 모델 입력으로 넘기지 않는다.

계약 snapshot과 SHA256SUMS 자체 hash를 pin하고 대상 파일 집합/고유 case ID/상태28개 미실행 표시·계약 traceability를 확인한다. 원본 fixture 내용이나 예상값을 바꾸지 않는다. 생성기/새 private key/네트워크/실제DB 호출은 없다.

## 6. 부록 — 실행 결과

| 범위 | 개수 | 결과 |
| --- | ---: | --- |
| 내부 raw 오류 분류 | 22 | PASS |
| 공개 오류 예시 | 4 | PASS |
|120일 시간 경계 | 14 | PASS |
| MEMBER 재개 판정 모형 | 29 | PASS |
| phone snapshot subset | 11 | PASS |
| 원본hash/manifest/범위/상태표시 | 1 | PASS |
| LC 신규 합계 | 81 | 실패0/오류0/skip0 |
| LC 전체 clean test | 1,029 | 실패0/오류0/skip0, 신규81포함 |
| 상태 시나리오 | 28 | NOT_EXECUTED_REQUIRES_IMPLEMENTATION |

실행 명령:

```sh
./gradlew test --tests '*LcBillingSimplifiedFixtureTest'
./gradlew clean test
# 인수 fixture 폴더에서
shasum -a 256 -c SHA256SUMS
```

선별/전체 모두 BUILD SUCCESSFUL. 기존 Gradle9.5.1/Java21 toolchain과 기존 Jackson/JUnit 의존성을 사용했으며 dependency/build 변경은 없다. 기존 deprecated/unchecked 경고는 유지했다. 케이스별 결과는 `build/test-results/test/TEST-web.tosunsaeng.contracts.LcBillingSimplifiedFixtureTest.xml`, HTML은 `build/reports/tests/test/index.html`이다. build 산출물은 다음 clean에서 재생성된다.

이번 변경 파일은 신규 테스트1개·원본 리소스10개·본 기록/CURRENT_STATE/WORKLOG다. ZIP의 생성기/검사기는 LC runtime이나 scripts에 설치하지 않았다. 기존 dirty 문서는 보존했으며 예상 밖 제품변경은 없다. Billing/Identity/DB/배포/활성화/Jira/외부전송/commit/push 변경 및 Secret/Token 기록 없음. `git diff --check`와 현재 WORKLOG marker 단일 출현을 확인했다.
