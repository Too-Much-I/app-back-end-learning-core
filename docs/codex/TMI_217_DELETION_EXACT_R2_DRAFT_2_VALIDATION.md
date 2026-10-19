# TMI-217 draft-2 재확인·LC 오프라인 독립 검증

- 날짜: 2026-10-16 (Asia/Seoul)
- revision: `lc-billing-deletion-exact-r2-draft-2`
- 대상: [Billing draft-2](/Users/msde76/.codex/worktrees/plan012-refund/billing/docs/contracts/TMI-217-DELETION-EXACT-R2-DRAFT-2.md), 동 revision fixture README/bytes.json/boundaries.json/SHA256SUMS.
- 상태: 주요 보완 반영 확인, 제한된 raw fixture 독립 검증 완료. 전체 fixture 동결·제품 구현·HTTP/DB 공동 검증 완료 아님.

## 1. 5줄 결론

1. E1의200번째 성공/201번째 금지·24시간 정각 발신금지·응답 분류 보완은 LC 요청과 일치한다.
2. E2 저장·원자성 방향은 유지하며 E3 delivery 격리가 안전한 cleanup을 중단하지 않는다는 보완을 확인했다.
3. E4 최초 전체completedAt+120일 review-only와 linked 관련문서의 완료후30일TTL 제외는 LC 제안과 일치한다.
4. E5는2개 파일 hash 일치, raw7개(독립 canonical3/strict 거절4) 및 별도 음성 대조3개 통과다. 경계31개는 실행하지 않았다.
5. retryNotBefore의 invalid-header 처리 문구를 정리하고 남은 scalar/JWT/실제 sender·DB·worker 검증을 완료해야 전체 동결할 수 있다.

## 2. 반드시 읽어야 하는 내용

LC는 Billing 검사기/생성기를 실행하거나 기대값으로 출력을 생성하지 않고 별도 Python 표준 라이브러리 test-only 검증기를 작성했다. 원문을 UTF-8 strict로 읽고 중복 키·소수형 숫자·lone surrogate를 보존/검사한다. B01~B03의 canonical을 입력에서 별도로 산출한 뒤 제공된 bytes/hash와 비교했다. B03은 serializer 검증이지 해당 Session 문자열을 제품 API가 허용한다는 증명이 아니다.

B04~B07의 거절은 이 독립 검사기의 입력 검증 결과이며 HTTP400을 실제 서버에서 관측한 것이 아니다. 31개 R/H/C 사례는 명세를 읽고 ID/개수를 확인했을 뿐, 모형으로 실행해 제품 테스트 통과로 대체하지 않았다. 이전44개나 다른 fixture 통과 수와 합산하지 않는다.

## 3. 결정·남은 기술 확인

추가 제품 정책 선택은 필요하지 않다. 초안의 다음 문구는 exact 동결 전 정리한다.

- Retry-After 절의 `invalidheader때retryNotBefore=null`은 바로 뒤의 `기존보존된더늦은최소시각은단축하지않는다`와 충돌한다. **기존 하한이 없을 때만 null, 있으면 invalid/누락/더 이른 헤더로 지우거나 단축하지 않음**으로 명시할 것을 요청한다. 스케줄도 max(backoff, 새 유효헤더, 기존 하한)를 사용하고 하한이24시간 경계 이상이면 운영확인을 유지한다. 기존 미래 하한 이후 invalid/duplicate header를 만나는 사례를 추가한다.
- correlationId는 종전 문서에서 불투명 요청 추적값으로만 정의됐다. draft-2의503 `correlationId형식` 검증이 별도 패턴/길이를 뜻한다면 이를 명시하고, 임의 UUID 제한을 LC에서 추가하지 않는다. 최종 schema/positive503 fixture에서 확인한다.

나머지 E1~E4 보완은 설계상 이견 없다. 시간 경계는 다음 발신을 제한하며 유효하게 시작한200번째 요청의 성공은 응답시24시간 경과만으로 버리지 않는다. lease/version/digest 불일치면 성공을 저장하지 않는 규칙은 유지한다. cleanup 자체의 안전중단은 delivery 격리와 별개다. 아직 runtime 동작으로 구현된 것은 아니다.

## 4. 주요 위험·미확인 사항

- 31개 시나리오의 실제 HTTP·Tx/CAS·worker 테스트, 전체 scalar raw suite, 새 목적의 signed JWT/port/query matrix는 미실행/미완성이다.
- 이7개만으로 모든 strict JSON·UUID/시각·framing·인증 경계가 검증됐다고 볼 수 없다.
- cleanup의 target version 갱신 때문에 유효 원격 성공이 로컬에 저장되지 않는 경합은 실제 복구 테스트가 필요하다. 상한 소진은 운영확인으로 남고 자동 budget reset은 없다.
- Billing 문서/fixture 원본은 수정하지 않았으며 실제 DB/index·운영키·환경·배포를 조회/변경하지 않았다. 기존 P6.1 제품 코드 변경과 사용자파일은 보존했다.

## 5. 검증 방법과 결과

독립 검사기: [verify_deletion_draft2.py](../../scripts/contracts/verify_deletion_draft2.py)

```sh
python3 scripts/contracts/verify_deletion_draft2.py /Users/msde76/.codex/worktrees/plan012-refund/billing/src/test/resources/contracts/lc-billing-deletion-exact-r2-draft-2
```

| 범위 | 결과 |
| --- | --- |
| SHA256SUMS | bytes.json/boundaries.json 두 파일 정확 bytes 일치 |
| B01/B02 | 필드순서 차이에도 독립 canonical bytes/hash 일치 |
| B03 | Unicode/quote/backslash/control 독립 escaping bytes/hash 일치 |
| B04 | strict UTF-8 디코딩 거절 |
| B05 | JSON escape로 전달된 lone surrogate 거절 |
| B06 | 원문1.0을 정수1로 coercion하지 않고 거절 |
| B07 | 동일값 중복 JSON 키도 거절 |
| 추가 음성 대조3개 | trailing object·boolean schema·decimal schema 거절 |
| boundaries.json | 31개 ID 중복 없음, 실제 동작 미실행 |

문서 링크·diff·WORKLOG marker 검사 수행. Gradle은 실행하지 않았다: 제품 코드 변경이 없는 오프라인 fixture 검증이며 새 sender/HTTP/DB의 통과 증거를 만들 단계가 아니다. 새 검증 스크립트와 본 보고서/CURRENT_STATE/WORKLOG만 이번에 변경했다. commit/push·외부 회신 전송·Jira 변경 없음.

## 6. 부록 — 관측 hash와 후속 순서

| 파일 | SHA-256 |
| --- | --- |
| bytes.json | d839cceaf610d5c019df01cc1d8e8e1d206a62f4fa1eaf94a06887bbabf62433 |
| boundaries.json | 534f4b801f4d9bad401d7455f97d676e85d5bfb39a00638341ecd396c7b9a2d7 |

원문/기대값을 재생성하지 않았다. 다음은 위 두 명확화와 빠진 raw scalar/JWT 자료 보완, 양측 독립 검증 및 승인된 구현 후 실제31개 경계·경합 검증이다. 제품 구현·배포 활성화는 이번 오프라인 검증의 승인 범위가 아니다.
