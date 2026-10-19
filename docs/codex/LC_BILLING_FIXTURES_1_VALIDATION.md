# LC fixtures-1 독립 검증 결과 및 Billing 회신

- 날짜: 2026-10-13
- 관련: TMI-116 / TMI-128 / TMI-122
- 입력 revision: `lc-billing-workload-auth-r1-fixtures-1`
- 상태: A 단계 token/byte hash 독립 검증 완료. HTTP·런타임 sender·B/C·배포 검증 완료 아님.

## 1. 5줄 결론

1. 원본 signed token 72개 모두 명세의 인증 결과 및 정상 subject/audience 기대와 일치했다.
2. 세 JSON의 byte SHA-256 모두 Billing manifest와 일치하며 fixture는 수정/재생성하지 않았다.
3. LC 전체 `./gradlew clean test`는 713개, 실패/오류/skip 0이다(공동 검증 73개 포함).
4. 설정 매핑의 신규 세 명칭은 LC 구현 후보로 충돌 없이 사용 가능하다. 런타임 설정 반영·공동 최종 동결은 하지 않았다.
5. HTTP27 및 신규 sender/chain·키 기동·복구 도구·실환경 검증은 B/C 단계에 남는다.

## 2. 반드시 읽어야 하는 검증 경계

새 코드는 [LcBillingSharedFixtureTest](../../src/test/java/web/tosunsaeng/contracts/LcBillingSharedFixtureTest.java) 한 개이며 `src/test`에만 있다. Billing codec나 테스트 소스를 가져오지 않고 Java JCA `SHA256withRSA`, 공개 JWK의 RSA key 및 Jackson strict JSON으로 독립 oracle을 작성했다. 기대 결과를 인증 로직에 전달하지 않고 결과 비교에만 사용한다. 원문 header/payload의 서명 입력은 재직렬화하지 않는다.

고정 now를 케이스별로 사용하며 nanosecond를 유지한다. duplicate JSON(추가claim 내부 포함), trailing JSON, 손상 UTF-8, 비정수/overflow NumericDate, TTL/skew, issuer/key 분리, rotation 등을 원문 입력으로 검증했다. 같은 kid여도 지정 verifier/keySet 밖의 키로 fallback하지 않는다. 추가 payload claim은 무시하고 subject/audience는 문자열 principal로 반환한다.

**wrong-sub/wrong-aud가 인증에 성공하는 것은 정상이다.** 403 route 인가는 실제 HTTP chain 책임이며 이 테스트는 구현하지 않는다. 테스트 oracle의 통과를 LC runtime verifier/sender 구현이나 Billing HTTP 수신 동작의 검증으로 주장하지 않는다. 키 파싱의 전체 부정 fixture·startup 검증 또한 이 결과에 포함하지 않는다.

## 3. 결과와 재현

| 실행 | 결과 |
| --- | --- |
| `./gradlew test --tests '*LcBillingSharedFixtureTest'` | 성공: 72 dynamic token cases + byte hash 1 |
| `./gradlew clean test` | 성공: 713 tests, failures 0, errors 0, skipped 0 |
| fixture 폴더 `shasum -a 256 -c SHA256SUMS` | public-jwks / token-cases / http-cases 모두 OK |

환경: Microsoft OpenJDK 21.0.11+10-LTS, Gradle wrapper 9.5.1. 최초 clean 실행은 sandbox의 사용자 Gradle cache lock 접근 제한으로 시작하지 못했으며, 권한 승인 후 같은 명령을 재실행해 성공했다. 기존 deprecated/unchecked 경고는 변경하지 않았다.

케이스별 결과는 `build/test-results/test/TEST-web.tosunsaeng.contracts.LcBillingSharedFixtureTest.xml` 및 `build/reports/tests/test/index.html`에 있다. `token-cases.json`의 72개 ID 각각이 JUnit dynamic test이며 전체 PASS(누락/skip 없음). report는 재빌드 시 교체되므로 결과 전달 시 revision/hash와 함께 보관한다. 실패 시 token/parser exception 원문을 assertion/report에 첨부하지 않는다.

| 파일 | 확인한 SHA-256 |
| --- | --- |
| public-jwks.json | db1fcf9eaeec3591a8c422ead344af61cbf25d6808ab377d61e935517f2ae0b2 |
| token-cases.json | e783060182033842a40e698b0ed31ee30db8691e71438bc6f707f0becd8c5641 |
| http-cases.json | 3c3ca0d33f247c9915b0e59a875858d46d583fb5e8fe79f88b9903a9529674a1 |

원본 출처와 미커밋 snapshot 상태는 [인수 기록](LC_BILLING_FIXTURES_1_RECEIPT.md)을 따른다. 동일 bytes 확인을 운영 토큰 신뢰나 배포 승인으로 해석하지 않는다.

## 4. 설정 매핑 검토·남은 결정

[매핑 후보 원문](../contracts/LC-BILLING-WORKLOAD-SENDER-MAPPING-R1.md)과 LC application.yml을 대조했다. 다음 명칭은 신규 후보로 사용 가능하며 기존 설정명과 충돌하지 않는다.

| 신규 property | env |
| --- | --- |
| `app.billing.workload-jwt.sender-enabled` | `BILLING_WORKLOAD_JWT_SENDER_ENABLED` |
| `app.billing.workload-jwt.environment` | `BILLING_WORKLOAD_JWT_ENVIRONMENT` |
| `app.billing.workload-jwt.private-jwk` | `BILLING_WORKLOAD_JWT_PRIVATE_JWK` |

- `BILLING_BASE_URL`, 기존 업무 flags 및 connect/read timeout PT2S/PT5S를 유지하는 후보에 기술적 이견 없다.
- sender flag는 인증 방식 선택이 아니고 준비 gate다. 기본 OFF, 업무 발신 ON인데 준비 OFF인 구성 거절은 B startup 구현/테스트 대상으로 둔다. saga뿐 아니라 reconciliation/publisher 및 의존 flags의 정확한 조합표가 필요하다.
- private JWK의 kid와 배포 public JWK의 kid 일치 및 키 유효성, OFF에서 미파싱은 B 검증 대상이다. 실제 Secret 값은 이 문서에 넣지 않는다.
- issuer/sub/두 audience·TTL은 명세에서 도출하고 임의 운영 override를 추가하지 않는 방향이다. SigV4 의존 제거 시 S3용 공용 AWS provider는 유지한다.
- 위는 기술적 후보 검토 결과이며 환경변수 추가·runtime 변경·배포 설정 적용은 하지 않았다. 최종 명칭 수락/ADR·AGENTS 갱신과 B 구현 범위 승인은 별도로 진행한다.

## 5. 미실행 범위와 다음 단계

HTTP27은 전부 미실행이다. Bearer/query/포트/ON-OFF/교차서비스 HTTP 인가·DB 무호출, 실제 sender fresh token, status-first와 같은 업무 요청 재시도, 키 startup/긴급 회전 운영·비로깅 전체 회귀, 승인 requeue/worker 경합, 실제 TLS/DNS/ALB와 E2E를 통과했다고 보고하지 않는다. Mongo integration task도 이번 test-only 변경에서 실행하지 않았다.

다음은 양측 A 결과·설정 매핑 최종 수락과 ADR/AGENTS 동기화, 별도 승인 후 B 구현이다. C 재처리 도구 및 실제 배포/활성화는 별도다. Billing 보고의 전체 1004개 테스트는 LC의 713개와 서로 다른 저장소 결과다.

## 6. 변경·계약 경계

이번 변경은 독립 테스트와 결과/작업 기록뿐이다. 제품 코드·공개 API/DTO/BaseResponse·AI user_id=examId·retryCount·S3/Redis·Billing 업무 wire는 변경하지 않았다. Billing 저장소, AWS, DB와 배포를 변경하지 않았으며 commit/push도 하지 않았다. 기존 dirty 문서/.DS_Store 및 앞선 인수 파일은 보존했다. 예상 밖의 이번 변경은 없다.
