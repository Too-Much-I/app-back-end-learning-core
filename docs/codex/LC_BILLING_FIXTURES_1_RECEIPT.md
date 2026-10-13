# fixtures-1 원본 인수 기록

- 날짜: 2026-10-13
- 관련: TMI-116 / TMI-128 / TMI-122
- 상태: 원본 복사·byte hash 확인 완료. 독립 JWT 검증/HTTP 실행/설정명 수락/계약 동결은 미완료.
- 출처: `/Users/msde76/billing` 로컬 working tree. 조회 당시 HEAD `ee164ac311fa8040391376730e9338bf713dc648`이나 해당 fixture·명세·매핑 파일은 untracked이므로 이 commit의 산출물이라고 보증하지 않는다.

## 인수 범위

- `src/test/resources/contracts/lc-billing-workload-auth-r1/`의 public-jwks.json, token-cases.json, http-cases.json, SHA256SUMS, README.md를 동일 상대 위치로 원본 복사했다.
- `docs/contracts/LC-BILLING-WORKLOAD-AUTH-R1.md` 및 `LC-BILLING-WORKLOAD-SENDER-MAPPING-R1.md`를 동일 상대 위치로 원본 복사했다. Billing 작성 후보이며 LC 최종 수락본으로 변경하지 않았다.
- Billing 저장소는 수정하지 않았고 generator/테스트/네트워크 호출을 실행하지 않았다. 테스트 전용 signed fixture를 재생성·정규화하지 않았다. private key는 인수 대상이 아니다.

## 검증

원본 및 LC 복사본 각각 fixture 디렉터리에서 `shasum -a 256 -c SHA256SUMS` 실행, 세 JSON 모두 OK.

| 파일 | SHA-256 |
| --- | --- |
| public-jwks.json | db1fcf9eaeec3591a8c422ead344af61cbf25d6808ab377d61e935517f2ae0b2 |
| token-cases.json | e783060182033842a40e698b0ed31ee30db8691e71438bc6f707f0becd8c5641 |
| http-cases.json | 3c3ca0d33f247c9915b0e59a875858d46d583fb5e8fe79f88b9903a9529674a1 |

README/SHA256SUMS/명세/매핑 문서는 원본과 `cmp` 일치 확인. 해시 검증은 동일 bytes 인수 증거이지 JWT 인증·인가 테스트 통과나 발행자 서명 검증 증거가 아니다.

## 다음 단계

72개 원문 token의 고정 clock 기반 LC 독립 검증 및 설정 매핑 수락 검토가 남아 있다. HTTP27개는 B 구현까지 미실행을 유지한다. 원문 token은 보고/로그에 노출하지 않으며 이 테스트 자료를 운영 키/토큰으로 사용하지 않는다. B/C 코드 구현과 AWS 배포는 이번 인수 요청의 범위에 포함하지 않았다.
