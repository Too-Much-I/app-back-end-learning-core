# Simplified fixtures-2 LC 독립 재검증

- 날짜: 2026-10-14
- revision: `lc-billing-paid-exam-simplified-exact-r1-fixtures-2`
- 관련: LC TMI-116/TMI-128/TMI-122·선행 TMI-218, Billing TMI-216

## 1. 5줄 결론

1. 전달 ZIP과 SHA256SUMS의 hash가 Billing 인계서와 일치한다.
2. ZIP10파일은 Billing 원본과 byte 동일하고 별도 fixtures-2 디렉터리로 인수했다.
3. fixtures-1은 보존했다. 업무 데이터 변경은 E06-owner message 하나뿐이다.
4. LC 자체 오프라인80벡터 및 실제 client의 로컬 HTTP 검증에서 정정된 OWNER_BLOCKED 판정이 통과했다.
5. E06-owner 불일치는 해소됐다. 상태28개 전체 실행·실환경 인증/배포·다른 공동 gate 완료를 의미하지 않는다.

## 2. 반드시 읽어야 하는 내용

정정된 메시지는 `The operation owner does not match the current owner.`다. HTTP409·OPERATION_OWNER_MISMATCH·retryable=false·기대 OWNER_BLOCKED는 그대로다. LC 제품 decoder는 이미 정확한 문구를 요구하므로 runtime 코드를 바꿀 필요가 없었다.

기존 fixtures-1의 잘못된 문구를 실제 client가 CONTRACT_ERROR로 거절하는 회귀를 별도 유지했다. fixtures-2 actual-client 테스트에서는 이전 E06-owner 기대값 예외를 제거하여 원 manifest 기대값대로 검증한다.

생산자 manifest의 `lcIndependentValidation=false` 등은 byte 보존을 위해 수정하지 않았다. LC 실행 증거는 이 보고서와 JUnit XML로 별도 기록한다.

## 3. 결정·후속

이번 E06-owner 문구 수락에 추가 제품 결정은 없다. 이후 LC 테스트 기준은 fixtures-2이며 fixtures-1은 원본/부정 회귀용으로 유지한다. T04·삭제/보존·전체 상태 시나리오·실환경 연동과 공개 route/자동 scheduler/paid 활성화 gate는 별도다.

## 4. 위험·검증 한계

- LC offline 모델80개는 제품 상태 전이 실행이 아니다. frozen state-scenarios28개는 미실행 표기를 보존한다.
- 실제 JwtBillingReservationClient 테스트는 loopback HTTP server/테스트 signer를 사용한다. 실제 Billing 서버·TLS/JWT 수신 chain·DB/E2E 검증은 아니다.
- 이번은 테스트/fixture/문서만 변경하여 Mongo 통합·bootJar를 재실행하지 않았다. 앞선 P3.1 Mongo208개 결과와 이번 결과를 합산하지 않는다.
- 이전 P3/P3.1 미커밋 제품 변경·사용자 DS_Store·동시 문서 기록은 보존했다. 예상 밖 이번 제품 변경 없음. Billing 저장소/실DB/배포/활성화/commit/push 변경 없음.

## 5. 변경 파일 및 검증

- 신규 `src/test/resources/contracts/lc-billing-paid-exam-simplified-exact-r1-fixtures-2/`10파일: ZIP bytes 그대로 인수, 덮어쓰기/재생성 없음.
- `LcBillingSimplifiedFixtureTest`: fixtures-2 hash/revision, owner message exact 검사, 구 원본 hash·단일 message delta/부정 판정 검증. 총82개=80벡터+hash/scope1+구원본/delta1, 실패/오류/skip0.
- `PaidReservationClientTest`: fixtures-2 오류22개를 실제 HTTP client로 실행, E06-owner 예외 제거 및 구 fixtures-1 거절 회귀 추가. 전체49개 실패/오류/skip0. 이49개는 오류22개 외 기존 phone/client 검사를 포함한다.
- 실행: `./gradlew test --tests '*LcBillingSimplifiedFixtureTest' --tests '*PaidReservationClientTest'`, 이후 `./gradlew test`. Java21.0.11 / Gradle9.5.1. 전체1,149개 실패/오류0·기존 조건부1 skip, diff check 통과.
- 기존 공개 API/DTO/BaseResponse·AI/S3/Redis·auth 정책 불변. 새 성공/오류 정책이나 제품 코드를 추가하지 않았다.

## 6. 인수 근거

| 대상 | SHA-256 |
| --- | --- |
| ZIP | `206c83a6ba8865650a07db3a56f67b210a0ad795d8b123cce811ef10ab2c6cb2` |
| SHA256SUMS | `4e91332dfa549da63d13f3e07a1ec208018e02a353ccd780f12b8570eb2d4845` |
| contract-snapshot.md | `79bcfe3d79225c21d712791266bea10d4aed9eed3dc0529ddf86f7b29673a482` |
| error-cases.json | `f7465b8868c1fd98c7b9d0fa74c0e4c0ab117963af498aecdd8ec5b8f79db0cc` |

SHA256SUMS9파일 검사 및10파일 Billing 원본 cmp 통과. 계약/나머지 데이터 파일 동일, README/revision/manifest/hash만 함께 갱신됐다. old SHA256SUMS 자체 hash와9파일 bytes도 LC 테스트에서 고정 검증한다. Billing 생성기/검사기 코드는 복사하거나 실행하지 않았다.
