# Learning Core `UserMerged` 계약 결정 가이드

- 작성일: 2026-08-20
- 입력 문서: Identity `UserMerged` schema v1 구현 인계서, Learning Core 구현 사전 검토
- 상태: 권장 기본 패키지 확정, 운영값·측정 gate 이행 전
- Jira: 별도 이슈 키 미제공

## 확정 결과 — 2026-08-20

사용자 승인에 따라 아래 조합을 Learning Core의 구현 기준으로 확정한다.

```text
C1-A  별도 비대칭 workload JWT
C2-A  direct Transaction, 단 staging 승인 기준 실패 시 C2-B로 계약 개정
C3-A  source/target 양쪽 ownership guard
C4-A  target 활성 우선, source-only 활성은 target으로 이전
C5-A  완료·폐기 history 합집합 보존
C6-A  기존 PUT URL 최대 5분 잔여 capability 명시적 수용
C7-A  Callback 전체 Transaction 경계
C8-A  producer 최종 target 불변식 + consumer fail-closed
C9    권장 HTTP status 표와 204 빈 body
C10-A 인프라 TLS/network 제한 + 앱 workload 검증
C11-A publisher OFF 상태의 단계적 guard 전환
```

이번 확정은 **구현 방향과 위험 수용 정책의 확정**이다. 아직 존재하지 않는 issuer, JWKS URI, audience, principal, TTL 같은 운영값을 임의로 확정했다는 의미는 아니며 production 활성화 승인도 아니다.

남은 외부·측정 gate는 다음과 같다.

1. Identity·인프라와 C1의 실제 credential profile 및 rotation 절차를 채운다.
2. Identity가 C8 producer 불변식과 C9 status/retry 표를 인계서 개정본에 반영한다.
3. staging/prod Mongo transaction 지원과 retry 조건을 확인한다.
4. production 유사 이력에서 C2 direct Transaction 성능을 측정한다. 합의한 기준에 실패하면 자동으로 timeout을 늘리지 않고 C2-B 계약 개정 절차로 전환한다.
5. C10의 TLS 종료점, network allowlist와 trusted proxy 책임을 배포 문서에 확정한다.
6. C11의 구버전 writer drain, guard backfill과 staging E2E를 완료하기 전에는 publisher와 merge feature를 활성화하지 않는다.

특히 C4-A와 C6-A의 결합에 따라 다음 위험을 명시적으로 수용한다.

> Learning Core API actor 권한은 merge commit 즉시 폐기한다. 다만 merge 전에 위임된 S3 Presigned PUT capability는 발급 시점부터 최대 5분간 유효할 수 있으며, source-only 활성 시험이 target으로 이전되는 경우 같은 Object Key에 대한 잔여 쓰기 가능성을 v1에서 수용한다.

## 1. 결정 원칙

모든 항목을 같은 방식으로 표결하면 안 된다.

- **필수 불변식**: 다른 선택지가 기능 요구를 충족하지 못하므로 기술 계약으로 고정한다.
- **제품·보안 정책**: 어느 위험과 사용자 경험을 선택할지 담당자가 승인한다.
- **측정 후 결정**: staging 수치가 없으면 direct/async 모델을 확정하지 않는다.
- **배포·인프라 계약**: 애플리케이션 저장소 밖의 책임자와 증빙을 남긴다.

메신저 합의만으로 완료 처리하지 않고, 최종 결정은 이 문서의 Decision ID와 함께 Identity 인계서 개정본 또는 공동 ADR에 기록한다. wire payload를 바꾸지 않는 의미 보완은 schema v1 계약 개정으로 관리할 수 있지만, 필드 변경은 별도 schema version 검토가 필요하다.

## 2. 우선 결정표

| ID | 쟁점 | 종류 | 확정안 | 남은 활성화 gate | 상태 |
| --- | --- | --- | --- | --- | --- |
| C1 | workload 인증 | 보안 정책 | 별도 비대칭 서명 workload JWT + 전용 audience/principal | 실제 profile·rotation 공동 승인 | 방향 확정 |
| C2 | HTTP 처리 모델 | 측정 후 결정 | direct Transaction을 우선 검증하고 기준 실패 시 durable inbox + worker로 계약 개정 | Mongo 지원·staging P99 | 조건부 확정 |
| C3 | ownership guard | 필수 불변식 | source/target 양쪽 guard를 결정적 순서로 같은 Mongo Transaction에서 touch | transaction·동시성 테스트 | 확정 |
| C4 | 활성 시험 충돌 | 제품 정책 | target 활성 시험 우선, source만 활성일 때는 target으로 이전 | 없음 | 확정 |
| C5 | 완료·폐기 이력 | 제품 정책 | source 이력을 삭제하지 않고 모두 target history로 이전 | 배정 회귀 테스트 | 확정 |
| C6 | 기존 Presigned PUT URL | 보안 정책 | 최대 5분 잔여 capability를 수용하고 Learning Core API 권한은 즉시 차단 | 승인 문구 운영 문서 반영 | 위험 수용 확정 |
| C7 | Callback 경합 | 필수 불변식 | Session 재조회 + 현재 owner guard touch + 결과/Job 전이를 같은 Transaction에서 처리 | Callback 경합 테스트 | 확정 |
| C8 | 상충 merge event | producer/consumer 계약 | producer 최종 target 불변식 + consumer fail-closed | Identity 인계서 반영 | 방향 확정 |
| C9 | HTTP status | 공동 계약 | 명확한 단일 status 표로 고정, 일시적 처리 경합은 `503` | Identity publisher 확인 | 방향 확정 |
| C10 | TLS/network 책임 | 인프라 계약 | 인프라에서 HTTPS·접근 제한, 앱에서 workload credential 검증 | TLS·network 실제 구성 | 방향 확정 |
| C11 | guard 전환 배포 | 배포 계약 | publisher OFF 상태로 writer 전환·구버전 drain·backfill 완료 후 consumer 활성화 | runbook·E2E 완료 | 확정 |

## 3. 선택지와 장단점

### C1. workload credential

#### A. 별도 비대칭 서명 workload JWT와 JWKS — 권장

사용자 Access JWT와 다른 issuer 또는 명시적으로 분리된 credential profile, 전용 audience, Identity workload principal을 사용한다.

- 장점: 사용자 토큰과 권한 경계가 명확하고, private key를 consumer에 배포하지 않으며, `kid` 기반 rotation/overlap이 가능하다.
- 단점: issuer/JWKS 가용성, cache, rotation 운영이 필요하다.
- 확정해야 할 값: credential type, issuer, JWKS URI, 허용 algorithm, audience, principal claim/value, TTL, clock skew, rotation/overlap.

#### B. shared HMAC secret JWT

- 장점: 초기 구현이 단순하고 별도 JWKS가 필요 없다.
- 단점: producer와 consumer가 같은 signing secret을 보유해 신뢰 경계가 약하고, 유출 영향과 rotation 비용이 크다. 사용자 JWT와 혼동될 가능성도 높다.
- 판정: 운영 계약으로 권장하지 않는다.

#### C. mTLS만 사용

- 장점: 전송 계층에서 workload를 강하게 식별할 수 있다.
- 단점: 인증서 발급·rotation·proxy 전달 책임이 복잡하고 애플리케이션 principal/audience 검증이 약해질 수 있다.
- 판정: 비대칭 JWT에 더하는 방어선으로는 가능하지만 v1의 유일한 애플리케이션 인증 수단으로는 별도 인프라 설계가 필요하다.

**확정 방법**: Identity가 실제 발급 가능한 credential의 staging 샘플 claim 명세와 JWKS rotation 절차를 제시하고, Learning Core가 정상·만료·issuer/audience/principal/algorithm 불일치 검증 결과를 남긴다. 실제 token은 문서에 기록하지 않는다.

### C2. 요청 안 direct Transaction과 async 처리

#### A. 요청 안에서 migration commit 후 `204` — 조건부 권장

- 장점: `204`의 의미가 “소유권 이전과 source 차단 완료”로 단순하며, 별도 worker 상태와 지연 구간이 없다.
- 단점: 사용자 이력이 많으면 Mongo transaction과 Identity의 5초 read timeout이 충돌한다. timeout 후 duplicate 처리 경합도 커진다.
- 채택 조건: production 유사 데이터 상한에서 transaction P99와 최대치가 합의한 예산 안에 들어오고, replica set/sharded transaction 및 retry가 검증돼야 한다.

5초 전체를 DB에 사용하지 않는다. 예시 승인 기준은 **P99 2초 이하**로 두어 네트워크·인증·재시도에 여유를 남기는 것이다. 실제 기준은 양 팀이 staging 수치로 확정한다.

#### B. durable inbox commit 후 worker 처리

- 장점: 큰 이력과 일시 장애에 강하고 HTTP timeout과 migration 시간을 분리할 수 있다.
- 단점: source 차단과 데이터 이전 사이의 상태를 명시해야 하며, 재처리·DLQ·운영 UI/alert가 추가된다. 현재 인계서의 direct v1 계약을 개정해야 한다.
- 필요 계약: inbox 수락 응답의 의미, source deny 적용 시점, worker SLA, 영구 실패 복구, target이 migration 중 보게 될 상태.

#### C. direct 시도 후 timeout이면 background 전환

- 장점: 짧은 요청은 빠르게 끝낼 수 있다.
- 단점: 같은 event가 direct와 worker에서 동시에 처리될 수 있고 응답 의미가 모호해진다.
- 판정: v1에서는 채택하지 않는다.

**확정 방법**: 먼저 문서 수 분포와 transaction 부하를 측정한다. 승인 기준 통과 시 A, 실패 시 timeout만 늘리지 말고 B로 인계서를 개정한다.

### C3. source/target 동시성 guard

#### A. source와 target guard를 모두 획득 — 필수

- 장점: source write, target write, migration을 같은 Mongo write-conflict 경계에 놓을 수 있다.
- 단점: 모든 사용자 소유 write와 Callback을 transaction으로 바꾸고 guard를 touch해야 한다.
- 세부 계약: 두 UUID를 canonical 문자열 순서로 정렬해 guard를 획득하고, source는 `ACTIVE -> MERGED`, target은 `ACTIVE` 확인과 revision touch를 수행한다.

#### B. source guard만 획득

- 장점: 변경 범위가 작다.
- 단점: target의 동시 write와 migration을 직렬화하지 못해 target 데이터를 덮거나 unique 충돌을 잘못 처리할 수 있다.
- 판정: 요구사항을 충족하지 못한다.

#### C. Redis/global lock

- 장점: 구현 표면이 단순해 보일 수 있다.
- 단점: Mongo commit과 원자적이지 않고 lock 만료·프로세스 종료 시 안전성을 증명하기 어렵다. 기존 Redis 구조도 불필요하게 확대한다.
- 판정: Mongo guard의 대체재로 사용하지 않는다.

### C4. 활성 시험 충돌

결정은 아래 세 경우를 모두 포함해야 한다.

| source 활성 | target 활성 | 권장 결과 |
| --- | --- | --- |
| 없음 | 없음 | 이전할 활성 시험 없음 |
| 있음 | 없음 | source 시험의 `examId`를 유지하고 target 소유의 활성 시험으로 이전 |
| 없음 | 있음 | target 활성 시험 유지 |
| 있음 | 있음 | target 활성 시험 유지, source 활성 시험은 `ABANDONED`로 전환 후 target 이력으로 이전 |

#### A. 위의 target 우선 정책 — 권장

- 장점: 기존 MEMBER의 진행을 보호하면서 Guest만 진행한 경우에는 학습 연속성을 보존한다.
- 단점: 양쪽 활성일 때 Guest 진행이 중단된다. source만 활성인 경우 C6의 기존 PUT URL 위험을 함께 수용해야 한다.

#### B. source 활성 시험은 항상 abandon

- 장점: merge 이후 source가 발급받은 PUT URL과 늦은 Callback이 target의 활성 시험에 영향을 주지 않아 가장 단순하다.
- 단점: target에 활성 시험이 없어도 Guest 진행을 잃어 merge의 사용자 경험이 나빠진다.

#### C. 최근 활동 시험 우선

- 장점: 가장 최근 진행을 살릴 가능성이 높다.
- 단점: 신뢰할 활동 시각/revision 계약이 새로 필요하고 기존 MEMBER 진행을 버릴 수 있다. 동률과 clock 기준도 추가된다.

#### D. 두 활성 시험의 진행 상태를 하나로 합침

- 장점: 이론상 진행 손실이 최소다.
- 단점: 서로 다른 `mockExamId`, question 결과, retry, summary 상태를 합치는 의미가 정의돼 있지 않고 기존 계약을 크게 바꾼다.
- 판정: v1 범위에서 제외한다.

양쪽 활성 충돌로 source 시험을 abandon하면 진행 중 Question/Summary Job과 늦은 Callback은 기존 abandoned-session 정책으로 무시한다. source만 활성이라 target으로 이전한 시험은 같은 `examId`로 채점을 계속한다.

### C5. 완료·폐기 이력과 다음 시험 배정

#### A. source와 target 이력을 합집합으로 보존 — 권장

- 장점: 학습 기록을 잃지 않고 “계정 merge” 의미에 맞다.
- 단점: 합쳐진 완료 횟수로 다음 시험 cycle/sequence가 달라질 수 있다.
- 세부 계약: `examId`는 바꾸지 않고 직접 owner인 Session/Result/Summary의 `userId`만 target으로 이전한다. 다음 시험 배정은 합쳐진 target history를 기준으로 기존 알고리즘을 그대로 적용한다.

#### B. target 이력만 유지

- 장점: 충돌과 배정 계산이 단순하다.
- 단점: Guest 학습 데이터가 사라져 merge 목적과 상충한다.

#### C. source 이력을 별도 archive로 숨김

- 장점: 원본은 보존하면서 현재 history 계산에서 제외할 수 있다.
- 단점: 새 상태와 조회 규칙, 복구 정책이 필요하고 외부 응답 의미가 복잡해진다.

### C6. merge 전에 발급된 S3 Presigned PUT URL

Presigned URL은 발급 후 Learning Core marker만으로 취소할 수 없다. 따라서 “merge commit 이후 source write는 항상 거절”의 범위를 **Learning Core HTTP API**로 한정할지 결정해야 한다.

#### A. 최대 5분 잔여 capability 수용 — v1 권장

- 장점: S3 Object Key와 현재 업로드 흐름을 바꾸지 않고 구현할 수 있다. source JWT의 submit·재발급·조회 API는 merge commit 즉시 차단된다.
- 단점: 기존 PUT URL로 같은 object key에 만료 전 업로드/덮어쓰기가 가능하다. source-only 활성 시험을 target으로 이전하는 C4-A와 결합하면 이 위험이 target 시험에 이어진다.
- 승인 문구: “Learning Core API actor 권한은 merge commit 즉시 폐기된다. merge 전에 위임된 S3 PUT capability는 발급 시점부터 최대 5분간 유효할 수 있으며, 이 잔여 위험을 v1에서 수용한다.”

#### B. source 활성 시험을 항상 abandon — C4-B와 결합

- 장점: 늦은 PUT이 target 활성 시험 처리에 사용되지 않는다.
- 단점: Guest 시험 진행을 보존하지 못한다. S3 쓰기 자체가 취소되는 것은 아니며 단지 결과가 비활성 시험에 고립된다.

#### C. revocable/nonce upload 설계

- 장점: merge 뒤 이전 capability를 실질적으로 무효화하거나 새 generation만 인정할 수 있다.
- 단점: Object Key, presign/submit 계약, 정리 작업과 인프라가 바뀌는 별도 기능이다. 현재 호환성 규칙상 명시적 범위 승인이 필요하다.

단순 TTL 단축은 위험 창을 줄일 뿐 취소를 보장하지 않으며 모바일 업로드 실패율을 높일 수 있다. C6은 C4와 반드시 한 묶음으로 승인한다.

### C7. Callback과 merge 경합

#### A. Session 조회·현재 owner guard touch·결과 저장·Job 전이를 같은 Transaction에서 처리 — 필수

- 장점: merge가 먼저 commit하면 Callback 재시도가 target owner를 다시 읽고, Callback이 먼저 commit하면 migration이 그 결과까지 함께 이전한다.
- 단점: Feedback, Summary, Azure, SpeechAce Callback과 관련 Job 전이를 모두 점검해야 한다.

#### B. 저장 직전에 Session을 한 번 더 조회

- 장점: 구현이 작다.
- 단점: 재조회와 insert 사이에 다시 merge가 가능해 TOCTOU가 남는다.
- 판정: 요구사항을 충족하지 못한다.

Callback은 사용자 API가 아니므로 source JWT 거절 로직을 적용하지 않는다. 외부 `user_id`는 계속 `examId`로 해석한다.

### C8. 상충 event와 merge chain

#### A. producer 불변식 + consumer fail-closed — 권장

Identity가 다음을 v1 producer 불변식으로 보장한다.

- source는 event 생성 시 ACTIVE GUEST이며 한 번만 merge된다.
- target은 최종 ACTIVE MEMBER이고 같은 merge 흐름에서 source가 되지 않는다.
- at-least-once 재전송은 반드시 같은 `eventId`를 사용한다.

Learning Core는 다음을 적용한다.

- 같은 eventId/같은 semantic digest: `204` no-op.
- 같은 eventId/다른 digest: `409`, mutation 없음, 보안·계약 경보.
- source가 이미 같은 target으로 MERGED지만 다른 eventId: `409`, mutation 없음, producer 중복 생성 경보.
- source가 다른 target으로 MERGED: `409`, mutation 없음, 고심각도 경보.
- target이 MERGED: `409`, mutation 없음, 고심각도 경보.

- 장점: consumer가 순서나 chain을 추측하지 않고 오류를 조기에 드러낸다.
- 단점: producer 버그는 자동 복구되지 않고 격리·수동 대응이 필요하다.

#### B. consumer가 merge chain을 따라 최종 target을 계산

- 장점: 순서가 어긋난 event를 일부 흡수할 수 있다.
- 단점: cycle, out-of-order, stale event, 권한 승격 의미가 복잡하고 wire event의 “최종 target” 계약이 약해진다.
- 판정: v1 범위에서 제외한다.

#### C. 같은 source/target이면 다른 eventId도 성공 처리

- 장점: producer의 중복 outbox 생성에 관대하다.
- 단점: eventId 영구 멱등성 계약 위반을 숨기고 audit이 모호해진다.
- 판정: producer가 동일 merge를 새 eventId로 재발행할 합법적 사유를 별도 정의하지 않는 한 채택하지 않는다.

### C9. HTTP status와 response body

권장 고정표는 다음과 같다.

| 상황 | status | 재시도 |
| --- | --- | --- |
| 신규 commit, 동일 duplicate | `204` 빈 body | 아니오 |
| malformed JSON | `400` | 아니오 |
| payload 4 KiB 초과 | `413` | 아니오 |
| `Content-Type` 불일치 | `415` | 아니오 |
| 필드 의미 검증 실패, unknown schema | `422` | 아니오 |
| eventId payload conflict, guard/target 상태 상충 | `409` | 아니오 |
| credential 누락·유효성 실패 | `401` | 아니오 |
| 유효한 credential이나 principal 불허 | `403` | 아니오 |
| rate limit | `429` + 가능하면 `Retry-After` | 예 |
| 처리 winner 미확정, 일시 DB/내부 장애 | `503` + 가능하면 `Retry-After` | 예 |

`425`도 publisher retry 대상이지만 TLS early data 의미와 혼동되므로 일반적인 처리 경합에는 `503`을 권장한다. 오류 body가 필요하면 민감정보 없이 stable internal code와 correlation ID만 포함한다. 공개 사용자 API의 `BaseResponse`를 internal `204`에 적용하지 않는다.

### C10. HTTPS와 network 접근 제한

#### A. ALB/reverse proxy TLS 종료 + network allowlist + 앱 credential 검증 — 일반적 권장

- 장점: 인증서와 ingress를 인프라에서 중앙 관리하고 애플리케이션은 workload claim 검증에 집중한다.
- 단점: trusted proxy와 forwarded-header 범위를 잘못 잡으면 scheme/IP 판단을 신뢰할 수 없다.

#### B. 애플리케이션까지 end-to-end TLS/mTLS

- 장점: 내부 hop도 암호화·상호 인증할 수 있다.
- 단점: 인증서 배포와 rotation, health check, proxy 구성이 복잡하다.

#### C. public ingress + workload JWT만 검증

- 장점: 인프라 구성이 단순하다.
- 단점: endpoint 노출과 공격 표면이 커지고 방어선이 하나뿐이다.
- 판정: 가능한 경우 피한다.

**확정 방법**: TLS 종료점, trusted forwarded-header 정책, security group/ingress allowlist, WAF/body limit 책임, 장애 시 담당자를 배포 문서에 기록한다. 애플리케이션의 token 검증은 어느 선택에서도 생략하지 않는다.

### C11. guard 도입과 배포 순서

#### A. traffic을 유지하는 단계적 전환 — 권장

1. Identity merge와 publisher를 OFF로 유지한다.
2. transaction/guard schema와 index를 먼저 배포한다.
3. 모든 user-owned writer와 Callback이 guard를 같은 Transaction에서 touch하도록 배포한다.
4. 구버전 인스턴스를 모두 drain하고 더 이상 guard를 우회하는 writer가 없음을 확인한다.
5. 기존 owner guard를 ACTIVE로 backfill하고 누락 검증을 수행한다.
6. consumer를 배포하되 ingress/publisher는 아직 차단한다.
7. staging E2E와 부하 기준을 통과한 뒤 publisher, 마지막으로 merge feature를 활성화한다.

- 장점: 중단 없이 안전 경계를 단계적으로 만든다.
- 단점: feature flag와 배포 관찰이 필요하다.

#### B. maintenance window에서 writer 중단 후 일괄 전환

- 장점: 구버전 writer 경합이 없고 순서가 단순하다.
- 단점: 서비스 중단과 운영 조율이 필요하다.

publisher가 켜진 상태에서 구버전 writer와 신버전 writer를 혼용하는 방식은 허용하지 않는다.

## 4. 권장 기본 패키지

별도 요구가 없다면 다음 조합을 공동 검토안으로 사용한다.

```text
C1-A  별도 비대칭 workload JWT
C2-A  direct Transaction, 단 staging P99 승인 기준 실패 시 C2-B로 계약 개정
C3-A  source/target 양쪽 ownership guard
C4-A  target 활성 우선, source-only 활성은 target으로 이전
C5-A  완료·폐기 history 합집합 보존
C6-A  기존 PUT URL 최대 5분 잔여 capability 명시적 수용
C7-A  Callback 전체 Transaction 경계
C8-A  producer 최종 target 불변식 + consumer fail-closed
C9    권장 status 표와 204 빈 body
C10-A 인프라 TLS/network 제한 + 앱 workload 검증
C11-A publisher OFF 상태의 단계적 guard 전환
```

이 패키지에서 제품·보안이 반드시 직접 승인해야 하는 핵심은 `C4-A + C6-A`다. “merge 후 source에서 비롯된 S3 write 가능성도 0이어야 한다”면 `C4-B + C6-B`를 선택하거나, 별도 범위로 `C6-C`를 설계해야 한다.

## 5. 실제 확정 절차

### 1차: 문서 결정 회의

- 제품: C4, C5, C6 승인
- Identity/보안: C1, C8 승인
- Learning Core: C3, C7, C9 승인
- 인프라/DBA: C10과 Mongo transaction 지원 여부 승인

결과는 `C4-A` 같은 선택 ID, 승인자 역할, 결정일, 선택 이유, 재검토 조건으로 공동 ADR에 기록한다.

### 2차: 측정 gate

- production 유사 user history 문서 수의 P50/P95/P99/max 확보
- direct transaction 처리시간 P50/P95/P99/max와 retry/timeout 측정
- Mongo transaction 지원, write concern, transient retry 검증
- Identity 5초 timeout과 네트워크 예산을 포함한 E2E 측정

수치가 승인 기준을 통과해야 C2-A를 확정한다. 실패하면 C2-B로 문서를 개정한다.

### 3차: 계약 freeze

최종 산출물은 다음 네 가지다.

1. Identity 인계서 개정본: credential profile, producer 불변식, status/retry, timeout
2. Learning Core ADR: guard, Callback transaction, 활성 시험/history/Presigned 정책
3. 배포 runbook: writer 전환, drain, backfill, feature flag, rollback
4. staging E2E 결과: 인증, duplicate, response loss, 상충 event, 동시 write, process kill, P99

네 산출물에 `TBD`가 남지 않고 승인 기준을 통과하기 전에는 production publisher를 활성화하지 않는다.

## 6. 최종 결정 기록 템플릿

```text
Decision ID:
선택안:
결정일:
승인 역할:
결정 이유:
수용한 위험:
구현 acceptance criteria:
재검토 조건:
연결 Jira/ADR:
```
