# LC–Billing workload JWT — A 단계 최종 수락 기록

- 확정일: 2026-10-13
- 승인 근거: 사용자 요청 「확정해줘」
- 관련: TMI-116 / TMI-128 / TMI-122
- 상태: **LC의 A 인증 계약·fixtures-1 해시·발신 설정 명칭 최종 수락 완료**
- 범위: LC 저장소의 계약 확정. Billing 저장소 수정/외부 회신 전송 및 B/C 구현·배포·활성화는 수행하지 않았다.

## 1. 5줄 결론

1. [A 인증 명세](LC-BILLING-WORKLOAD-AUTH-R1.md)의 `lc-billing-workload-auth-r1` 규칙과 테스트 기대 결과를 LC가 최종 수락한다.
2. `lc-billing-workload-auth-r1-fixtures-1`의 아래 정확한 byte hash를 동결한다.
3. [발신 설정 매핑](LC-BILLING-WORKLOAD-SENDER-MAPPING-R1.md)의 신규 세 명칭과 공통 준비 flag 의미를 확정한다.
4. 양측 token/hash 독립 검증 완료와 미실행 HTTP27·B/C 범위를 구분한다.
5. 다음은 startup flag 조합표·B 계획/ADR/AGENTS 동기화 준비이며, B 구현 착수 및 C/배포는 별도 승인이다.

## 2. 확정한 계약

이 문서는 기존 인수본의 「후보/LC 수락 대기」 상태를 갱신한다. 인증 wire의 상세 규칙은 A 명세 §2 전체와 테스트 명세 §3을 따른다. 요약이 원문 규칙을 대체하지 않는다.

| 항목 | 확정값 |
| --- | --- |
| 서명 | LC 전용 RSA 2048-bit 이상 / RS256, 초기 배포형 공개 JWK set |
| 환경 | test / staging / production, 환경별 키·신뢰 분리 |
| issuer | `urn:tosunsaeng:workload:learning-core:{env}` |
| subject | `learning-core-service` |
| Reservation audience | `tosunsaeng-billing:reservations:{env}`, 명세의 다섯 POST만 |
| AttemptGroup audience | `tosunsaeng-billing:attempt-group-events:{env}`, 명세의 event POST만 |
| 시간 | 발신 exp=iat+60, nbf를 발급하면 iat와 동일. 수신 TTL 1~60초 / skew30초, 소수초 now와 정수 NumericDate 규칙은 A 명세 그대로 |
| 전송 | 매 HTTP 시도 새 token/jti, 원 operation/event/body/digest 유지, redirect 금지 |
| 키 신뢰 | 원격 key fetch/fallback 없음. LC/Identity trust·principal·권한 독립 |

Bearer exact 문법, 추가 payload claim 허용/무시 및 추가 JOSE header 거절, 모든 query(빈 component 포함) 거절, 401/403/404 우선순위, 교차 서비스 ON/OFF verifier 매트릭스, TLS 미기동 transport 구분, 회전·비밀 비노출 규칙을 A 명세대로 수락한다. 검증 안 된 issuer는 로컬 고정 verifier 선택 힌트일 뿐 principal이 아니다.

body 없는 ingress404는 기존 LC Reservation CONTRACT_ERROR/AttemptGroup DEAD_LETTER 분류를 유지하며 업무 OPERATION_NOT_FOUND와 구분한다. 어느 것도 취소·미소비를 확정하지 않는다. 기존 status-first/terminal 불변·원본 보존을 유지하고 자동 repair-confirm/새 operation/event로 우회하지 않는다.

기존 여섯 API method/path/body/성공DTO/업무오류/멱등성·원장은 유지한다. 사용자 공개 API·AI·S3/Redis 계약도 변경하지 않는다.

## 3. fixtures-1 동결 및 증거

| 파일 | SHA-256 |
| --- | --- |
| public-jwks.json | db1fcf9eaeec3591a8c422ead344af61cbf25d6808ab377d61e935517f2ae0b2 |
| token-cases.json | e783060182033842a40e698b0ed31ee30db8691e71438bc6f707f0becd8c5641 |
| http-cases.json | 3c3ca0d33f247c9915b0e59a875858d46d583fb5e8fe79f88b9903a9529674a1 |

LC token72/hash1 성공 및 전체713/실패0/오류0/skip0은 [검증 결과](../codex/LC_BILLING_FIXTURES_1_VALIDATION.md)를 따른다. Billing token/hash73·전체1004와 LC byte/XML 대조 완료는 사용자 전달 Billing 접수 회신에 근거한다. 양 저장소 결과를 합산하지 않는다.

fixture bytes를 이번에 변경/재생성하지 않는다. 변경 시 새 revision/hash 및 양측 수락이 필요하다. HTTP27은 기대 결과만 수락하며 실행 통과가 아니다. 실제 키·token을 명세/회신에 기록하지 않는다.

## 4. 발신 설정 명칭 최종 수락

| property | 환경변수 | 의미 |
| --- | --- | --- |
| `app.billing.workload-jwt.sender-enabled` | `BILLING_WORKLOAD_JWT_SENDER_ENABLED` | 기본 false, 두 client 공통 인증 준비 gate. 인증 방식 선택 아님 |
| `app.billing.workload-jwt.environment` | `BILLING_WORKLOAD_JWT_ENVIRONMENT` | 위 세 환경 exact, 결제 sandbox enum과 별개 |
| `app.billing.workload-jwt.private-jwk` | `BILLING_WORKLOAD_JWT_PRIVATE_JWK` | Secret 주입, 기본 빈 값. ON 필수검증/OFF 미파싱 |

기존 BILLING_BASE_URL/업무 flags/timeout 명칭과 PT2S/PT5S 기본값을 유지한다. issuer/sub/audience/TTL/skew의 임의 운영 override는 추가하지 않는다. SigV4/무인증 fallback은 제공하지 않으며 S3용 공용 AWS credential provider를 보존한다.

업무 발신 ON·JWT 준비 OFF 구성은 B 전환에서 기동 거절한다. **어떤 flag 조합이 발신/필수 bean을 구성하는지에 대한 상세 표는 B 준비에서 실제 의존성과 함께 확정한다.** phone→saga 및 reconciliation→saga 의존을 유지한다. writer OFF/publisher ON은 outbox drain 가능성과 Transaction bean 의존을 확인하며 임의 금지하지 않는다. 신규 명칭 확정이 현재 runtime 환경변수 추가나 기능 ON을 뜻하지 않는다.

## 5. 남은 범위·위험과 다음 작업

- B 준비: 정확한 startup 조합표, sender key 검증·공유 provider 영향, 양측 구현 분담·ADR/AGENTS 변경안을 작성한다. 기존 Lattice 전제는 B 착수 전 승인 범위에 맞춰 동기화한다.
- B 구현: 별도 승인 후 기본 OFF sender/verifier/chain 및 HTTP27·key startup·fresh token·비로깅·업무 retry를 실제 구현으로 검증한다. 이번 계약 수락은 코드 작성 요청이 아니다.
- C: 승인 DEAD_LETTER 재처리 도구·CAS/lease/fence/감사/상한·복구 E2E는 별도 승인/구현이다. 도구가 없는데 준비된 것으로 간주하지 않는다.
- 실제 origin/port/키 담당·회수 SLA·미완료 saga/outbox inventory·TLS/ALB·Mongo/E2E·배포/활성화는 별도 검증과 승인이 필요하다.
- Billing 수신 준비 후 LC 발신을 활성화하며 진행 중 복구와 원본 증거를 일괄 중단/삭제하지 않는다.
- Billing 정본 반영은 Billing 담당 작업이다. 이번 LC 수락 기록만으로 타 저장소가 갱신됐다고 보고하지 않는다.
