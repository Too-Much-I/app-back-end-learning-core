# LC–Billing workload-auth-r1 공동 fixture — fixtures-1

- 작성일: 2026-10-13. Billing 제작본이며 LC hash 수락·독립 실행·최종 계약 동결은 미완료다.
- 명세: [A 인증 후보](../../../../../docs/contracts/LC-BILLING-WORKLOAD-AUTH-R1.md).
- 테스트 전용 임시 RSA 키로 오프라인 생성했다. public JWK만 저장하며 private key는 메모리에서 생성·폐기했다. 이 토큰·키는 실제 서버나 운영 설정에 사용하지 않는다.

## 파일과 실행 범위

| 파일 | 내용 | 검증 범위 |
| --- | --- | --- |
| public-jwks.json | LC/Identity/user 및 LC next·overlap 공개키 | LC/Identity는 같은 kid와 서로 다른 실제 키를 사용해 신뢰 분리 검증 |
| token-cases.json | 72개 원문 서명 token, 고정 clock·verifier/keySet·기대 principal/인증 실패 | 기존 Billing WorkloadJwtCodec에 LC issuer·테스트 키를 주입해 실행. 새로운 LC HTTP chain이나 LC sender 실행이 아님 |
| http-cases.json | 27개 Bearer/query/교차 서비스·ON/OFF·포트·method/path 기대 결과 | B 구현용 입력/기대 결과. 모두 PENDING_B_LC_HTTP이며 아직 실행하지 않음 |
| SHA256SUMS | 위 세 JSON 파일의 정확한 UTF-8 byte SHA-256 | 양측이 동일 bytes를 사용했는지 확인 |

HTTP 정상 사례의 expectedStatus=null/expectedStage=BUSINESS_AFTER_AUTH는 인증 통과만 의미한다. 업무 body나 실제 성공 응답은 이 파일로 검증하지 않는다. 둘 다 OFF의 null/NO_HTTP_RESPONSE는 공유 connector 미기동이며 HTTP 상태를 기대하지 않는다. 그 외 실패 사례에는 DB/service 미호출을 B 단계에서 검증한다. 별도 업무 기능 flag는 켜진 것으로 가정한다.

이 묶음은 A 명세 27개 **그룹 전체의 실행 완료**를 뜻하지 않는다. body/멱등성·status-first·requeue·실제 TLS·encoded path·키 기동·비로깅 등은 명세에 남아 있으며 B/C에서 실행 가능한 fixture를 추가해야 한다. AUTH-08 매트릭스는 문서와 HTTP manifest의 기댓값이며 공동 verifier는 구현하지 않았다.

## Billing 독립 검증

```sh
./gradlew test --tests '*LcWorkloadSharedFixtureTest'
./gradlew clean test
```

테스트는 원문 signed token을 그대로 읽고 기존 codec로 검증한다. NumericDate·duplicate JSON을 재직렬화하거나 기대 결과를 생성기로 다시 계산하지 않는다. 유효하나 잘못된 sub/aud 사례는 codec에서 principal이 반환되는 것이 정상이며 인가 실패 403은 HTTP 단계의 별도 책임이다. Java 테스트가 SHA256SUMS를 검증한다.

## 2026-10-13 Billing 실행 결과

- `LcWorkloadSharedFixtureTest`: 원문 token 72개 + byte hash 검사 1개, 총 73개 통과.
- `./gradlew clean test`: 총 1,004개, 실패/오류/skip 0.
- HTTP manifest의 LC 동작은 미실행이다. LC hash 수락·독립 실행 및 신규 sender 명칭 수락도 남아 있다. HTTP header 이름 대소문자와 변조된 Identity token의 교차 인증 실패를 기대 결과에 포함했다.

## LC 독립 검증·수락 절차

1. 이 폴더의 세 JSON과 SHA256SUMS를 같은 revision으로 받아 byte hash를 확인한다. 다른 키·token으로 재생성하지 않는다.
2. 고정 clock/now·verifier/keySet을 사용해 각 원문 token의 인증 결과 및 정상 principal을 LC의 독립 검증기로 비교한다. 현재 SigV4 client만으로 이 검증을 수행했다고 보고하지 않는다.
3. HTTP manifest는 지금 기대 결과를 검토하고 B 단계의 실제 수신/발신으로 실행한다. 미구현 동작을 test-only 모형으로 대체해 통과했다고 보고하지 않는다.
4. LC는 revision·hash·실행 명령/버전·케이스별 결과·미실행 범위와 [설정 매핑안](../../../../../docs/contracts/LC-BILLING-WORKLOAD-SENDER-MAPPING-R1.md)의 exact 명칭 수락을 회신한다. 실패 입력의 token/private key 원문은 회신·로그에 첨부하지 않는다.

## 재생성

저장소 root에서 `node scripts/contracts/generate-lc-workload-fixtures.mjs`로 제작한다. Node 내장 crypto만 사용하며 네트워크/API 호출은 없다. 매번 새 임시 키를 생성하므로 byte/hash가 달라진다. 이미 공유한 fixtures-1을 임의 재생성하지 않으며 변경 시 새 revision·hash·양측 수락을 남긴다. 실키를 입력받는 옵션은 없다.
