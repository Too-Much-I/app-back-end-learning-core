# LC–Billing workload-auth-r1 — A 단계 동결 후보·공동 테스트 명세

> 2026-10-13 사용자 승인으로 LC의 A 최종 수락을 완료했다. 현행 수락 상태·동결 hash·설정 명칭·남은 범위는 [A 최종 수락 기록](LC-BILLING-WORKLOAD-A-ACCEPTANCE-R1.md)을 따른다. 아래 「후보/LC 검증·수락 미완료」 표현은 Billing 인수 당시 이력이다. 인증 규칙 본문은 변경하지 않았으며 B/C 구현·HTTP 검증·배포는 여전히 미완료다.

- 작성일: 2026-10-13 / revision: `lc-billing-workload-auth-r1`
- 상태: Billing exact 동결 후보. 사용자 전달 LC 방향 검토 의견을 반영했으나 이 문서의 LC 최종 수락/독립 실행·B 코드 구현·배포/활성화는 완료되지 않았다. fixtures-1 제작과 Billing 기존 codec의 token/hash 검증은 완료했으며 신규 LC HTTP 동작 검증과 구분한다.
- 범위: [공동 계약 §16](BILLING_SERVICE_INTEGRATION_CONTRACT.md#16-lc-jwt-전환의-공동-활성화404-보존복구-절차)의 여섯 LC→Billing POST. 기존 Identity r1·사용자 JWT·업무 wire와 원장은 유지한다.

## 1. 제안 요약

1. LC 전용 RS256, 환경별 키·issuer, 두 목적별 audience와 TTL 60초·skew 30초를 제안한다. 검증 모델은 기존 Identity r1을 재사용한다.
2. 공유 TLS에서도 LC/Identity의 신뢰·인가·수신 flag는 독립이며, LC IAM permitAll 우회는 B 구현에서 제거한다.
3. 살아 있는 서버의 ingress 차단은 body 없는 404다. 포트 미기동·서버 미도달은 전송 오류로 구분하고 HTTP 404를 보장하지 않는다.
4. query는 이름·값과 무관하게 모두 거절하며 빈 query component도 거절한다.
5. 공동 테스트 사례·기대 결과와 양측 분담을 정의했다. LC 최종 수락, 공동 fixture 실행 및 ADR/AGENTS 동기화 후 B 구현을 별도 승인한다.

## 2. 반드시 읽어야 하는 exact 후보

### 2.1 JWT/키

| 항목 | 값/규칙 |
| --- | --- |
| env | `test`, `staging`, `production`만. env별 LC 서명키/신뢰set 분리 |
| iss | `urn:tosunsaeng:workload:learning-core:{env}` exact |
| sub | `learning-core-service` exact |
| aud | 단일 문자열, 아래 목적값 exact. 배열/multi-aud 불허 |
| header | `alg=RS256`, `typ=JWT`, `kid` 세 필드만. jku/x5u/crit 등 추가 field 불허 |
| 필수 claim | iss/sub/aud/iat/exp/jti. optional nbf 검증. 추가 claim은 허용하되 무시하며 권한 근거로 사용하지 않음 |
| jti | lowercase UUIDv4, 매 HTTP 시도마다 새 token/jti 발급. 업무 event/operation ID를 대체하지 않음 |
| 시간숫자 | iat/exp/nbf는 비음수 int64 JSON integer. 문자열/소수/지수/overflow 불허 |
| 발급 | iat=현재 정수 epoch,exp=iat+60. nbf 포함 시 nbf=iat |
| TTL/검증 | 1≤exp-iat≤60,iat≤now+30,now<exp+30. nbf가 있으면 iat≤nbf<exp 및nbf≤now+30. now 소수초 유지 |
| JSON/token | duplicate/trailing/손상 UTF-8·compact token 거절,token ≤ 16 KiB. 단일 Authorization header의 정확한 Bearer 형식 |
| 공개키 | 배포형 public JWK set,RSA≥2048,keys 1~20개,set ≤ 64 KiB,유일한 nonblank kid ≤ 256자,kty=RSA/alg=RS256/use=sig/key_ops=[verify] 필수 |
| JWK추가 field | 공개n/e 외 private/임의 field 불허, 손상 key 하나면 ON 수신기 기동 실패. 기존r1 수학적 유효성 검사 재사용 |

Billing은 로컬 고정 신뢰 설정만 사용한다. 검증되지 않은 토큰의 iss/jku로 키 주소를 선택하지 않는다. unknown kid는 401이며 원격 refresh나 무인증 fallback은 없다. LC만 서명용 private key를 보유한다. 실제 키·토큰은 공동 fixture·저장소·로그에 넣지 않으며 기존 Identity/user 키를 재사용하지 않는다. JWT는 body 서명과 동등하지 않고 jti의 단일 사용을 강제하지 않는다. 탈취된 토큰은 유효기간 내 재사용될 수 있으므로 TLS·네트워크 경계가 필요하다.

### 2.1.1 Authorization 문법과 추가 claim

- HTTP header 이름 `Authorization`의 대소문자는 HTTP 규칙대로 구분하지 않는다. header는 정확히 하나여야 하며 중복 header와 쉼표로 합친 값은 401이다.
- 서버가 전달받은 header 값은 `Bearer`(대소문자 정확 일치) + ASCII SP 한 개(`0x20`) + compact JWT 형식이어야 한다. `bearer`·`BEARER`, 앞뒤 공백, 두 개 이상의 공백, 탭(`0x09`), 줄바꿈, 빈 token을 거절한다. trim·대소문자 변환·공백 정규화로 수락하지 않는다. HTTP parser/proxy가 제거한 공백은 애플리케이션에서 검출할 수 없으므로 원문 전달 여부는 실환경 gate에서 확인한다.
- compact JWT는 비어 있지 않은 세 segment를 점 두 개로 구분한다. 각 segment는 `[A-Za-z0-9_-]+`만 허용하며 padding `=`, 공백, 추가 점을 거절한다. 토큰 상한은 16 KiB다. 이 문법 통과 후에도 JSON·서명·필수 claim·시간 검증을 모두 수행한다.
- 추가 **payload claim**은 유효한 JSON 값이면 허용하되 무시한다. `scope`, `roles`, `userId` 등이 있어도 principal·권한·업무 userId를 결정하거나 필수 claim을 대신하지 않는다. optional `nbf`는 알려진 claim으로 반드시 검증한다. 전체 JSON의 duplicate field·trailing token·UTF-8·크기 제한은 추가 claim에도 적용한다. 추가 **JOSE header field**는 계속 거절한다.

### 2.2 목적·route

| aud | 허용POST |
| --- | --- |
| `tosunsaeng-billing:reservations:{env}` | `/internal/v1/reservations`, `/internal/v1/reservations/status`, `/internal/v1/reservations/continuations/phone`, `/internal/v1/reservations/{reservationId}/confirm`, `/internal/v1/reservations/{reservationId}/cancel` |
| `tosunsaeng-billing:attempt-group-events:{env}` | `/internal/v1/attempt-group-events` |

동적 reservationId는 한 path segment만 허용하며 기존 parser와 업무 검증을 유지한다. `*` 또는 `/internal/**` 전체 인가를 사용하지 않는다. encoded slash·trailing slash·중복 slash가 정확한 route 인가를 우회해 업무 처리되면 검증 실패다. JWT sub는 서비스를 식별한다. body의 userId는 LC가 확정한 사용자이며 query/header의 userId는 받지 않는다.

### 2.3 모든 query 거절

이 여섯 API는 query가 필요 없다. 이름·값·빈 값·알려진 이름 여부와 무관하게 모든 query component를 거절한다. `?x=1`, `?x=`, `?userId=...`, 중복 parameter와 빈 `?`를 포함한다. 서버가 받은 요청의 query string은 null이어야 한다. proxy가 빈 `?`를 제거하면 서버에서는 검출할 수 없으므로 원 request-target 전달 여부를 실제 네트워크 검증 단계에서 확인한다.

활성 수신 경계에서 query 검사는 JWT 형식 검사 단계에 수행하며 body 없는 401을 반환한다. 알려진 route의 수신 OFF·잘못된 포트 차단이 먼저 적용되는 경우에는 body 없는 404다. query를 무시하거나 정규화해 업무를 처리하지 않으며 URL로 userId를 전달하지 않는다.

### 2.4 수신/오류순서·transport

| 순서 | 조건 | 결과 |
| --- | --- | --- |
| 0 | 포트 미기동/서버 미도달/TLS 실패 | HTTP 응답 보장 없음. LC 기존 전송 오류 분류·retry·circuit 유지, 404 합성 금지 |
| 1 | 살아 있는 Billing HTTP 서버의 지정 LC route 수신 OFF/관련 업무 OFF/잘못된 local port | body 없는 404, no-store. token/query/body 검사보다 선행 |
| 2 | 활성 port의 Authorization/query/JWT 형식·issuer/key/서명/시간 검사 실패 | body 없는 401, no-store, DB/service 미호출 |
| 3 | 신뢰 검증된 token의 sub/aud/method/route 인가 실패 | body 없는 403, no-store, DB/service 미호출 |
| 4 | 인증/인가 통과 | 기존 strict body·DTO/204/InternalApiError 업무 응답 |

활성 workload 포트의 미허용 internal route는 토큰이 없으면 401, 유효한 신뢰 토큰이면 403이다. public path는 기존 ingress 차단 정책을 유지한다. 기존 LC IAM permitAll·다른 포트·direct 경로를 통한 우회 처리를 막는다. X-Forwarded-Port/Host로 local port를 판정하지 않는다.

body 없는 ingress 404는 LC Reservation의 CONTRACT_ERROR·AttemptGroup의 DEAD_LETTER로 분류한다. 업무 JSON의 code=OPERATION_NOT_FOUND는 별도 업무 오류다. 둘 다 취소·미소비가 확정됐다는 뜻이 아니다. 기존 status-first 복구, 원 이벤트 보존 및 담당 승인 후 한정된 재처리 절차를 따른다. 새 503 키 조회 분기를 추가하지 않으며 활성 수신기의 키가 손상되면 기동 실패다.

### 2.4.1 활성 서비스의 verifier 선택과 교차 서비스 응답

양쪽 ON일 때는 환경별 LC·Identity verifier를 각각 구성한다. bounded strict parsing으로 얻은 **미검증 iss는 로컬 고정 allowlist에서 verifier를 선택하는 힌트로만** 사용한다. ON 서비스의 exact issuer에 대응하는 verifier 한 개를 선택하며 원격 키 조회·키 집합 합치기·다른 verifier로의 fallback은 없다. 선택된 verifier가 해당 서비스의 키로 서명을 검증하고 같은 issuer·형식·시간을 재검증한 뒤에만 신뢰 principal을 만든다. kid가 같아도 다른 서비스의 키를 검색하지 않는다.

OFF 서비스의 공개키는 읽거나 파싱하지 않고 verifier도 구성하지 않는다. 따라서 그 issuer를 주장하는 토큰은 **활성 대상 route에서 401**이다. issuer 문자열만 보고 서비스 principal을 만들거나 403을 반환하지 않는다. 반대로 **요청 대상 route 자체가 OFF**면 기존 ingress 우선순위에 따라 살아 있는 서버는 token 검사 전 404를 반환한다.

| Identity 수신 | LC 수신 | 요청 | 기대 응답 |
| --- | --- | --- | --- |
| ON | ON | 정상 Identity token → LC route | Identity 서명·issuer·시간 검증 후 목적 인가 실패 403 |
| ON | ON | 정상 LC token → Identity route | LC 서명·issuer·시간 검증 후 목적 인가 실패 403 |
| OFF | ON | Identity token → LC route | Identity verifier 없음: 401 |
| ON | OFF | LC token → Identity route | LC verifier 없음: 401 |
| OFF | ON | 임의 token → Identity route | 대상 OFF: 살아 있는 서버의 404 |
| ON | OFF | 임의 token → LC route | 대상 OFF: 살아 있는 서버의 404 |
| ON | ON | 상대 issuer를 주장하나 서명 변조·wrong key/환경·만료 | 인증 실패 401, issuer 문자열만으로 403 금지 |
| OFF | OFF | 공유 TLS port 요청 | connector 미기동: HTTP 응답 보장 없음 |

이는 B 구현의 후보 명세다. 현재 Identity 전용 filter에는 공동 verifier 선택이 구현되지 않았으며, 기존 Identity 구현의 테스트를 위 매트릭스 검증으로 취급하지 않는다.

### 2.5 공유TLS·설정·rotation

Billing의 `billing.workload-jwt.receiver-enabled`는 기존 Identity 수신 설정으로 유지한다. 같은 namespace 아래 신규 LC 설정으로 `learning-core-receiver-enabled=false`, `learning-core-public-jwk-set` 기본 빈 값을 제안한다. port·환경·TLS keystore는 공유하고 켜진 서비스의 공개키만 필수 검증한다. Identity 또는 LC가 ON이면 connector를 기동하며 둘 다 OFF면 기동하지 않는다. OFF 서비스는 다른 살아 있는 ingress에서도 차단한다. 한 서비스의 OFF가 다른 ON 서비스를 끄지 않으며 키·권한은 독립이다. TLS 키와 JWT 서명키는 별도 자료다.

LC 담당자는 기존 BILLING_BASE_URL/flag와 신규 key/env 설정의 명칭·매핑을 수락해야 한다. 실제 주소·port·키·DB·stream·네트워크 값은 아직 확정하지 않았다. redirect 금지, body 16 KiB 상한, timeout, traceparent 전달, baggage 금지 및 업무 retry는 기존 LC 계약을 유지한다.

정상 rotation은 모든 Billing receiver에 old+new 공개키 배포 → 수락 확인 → LC의 new 키 발급 전환 → old 키 발급 replica 완전 종료 확인 → 최소 120초(TTL 60초+미래 iat 30초+expiry skew 30초) 후 old 신뢰 제거 순서다. 긴급 유출 시에는 기다리지 않고 신뢰 제거·호출 차단을 우선한다. 담당·SLA와 배포 완료 증거는 운영 단계에서 확인한다.

## 3. 공동 테스트 명세와 양측 결정 사항

### 3.1 공통 case 목록 — 그룹 전체의 실행 결과 아님

고정 clock은 `2026-10-13T00:00:00Z`이며 테스트 전용 LC/Identity/user 키를 사용한다. 아래는 사례 명세다. 2026-10-13 [fixtures-1](../../src/test/resources/contracts/lc-billing-workload-auth-r1/README.md)에 signed token 72개·HTTP 기대 사례 27개와 hash를 제작했다. Billing 기존 codec의 token/hash 73개가 통과했다. 신규 LC HTTP/양측 공동 검증은 별도이며 상세 결과는 fixture README와 작업기록을 따른다. NumericDate·duplicate JSON 사례는 직접 서명한 원문으로 검증해 serializer가 잘못된 필드를 정규화하지 않게 한다.

| ID | 사례 | 기대/검증 주체 |
| --- | --- | --- |
| AUTH-01 | LC 정상 token, 여섯 POST의 정상 body | 기존 업무 성공/DTO, Billing verifier·LC sender |
| AUTH-02 | 만료/iat미래/nbf미래 경계 | iat=now+30허용,+31거절; now=exp+30은거절,직전허용; nbf도동일skew. 양측 clock 소수초 검증 |
| AUTH-03 | TTL0/61,nbf<iat/nbf=exp | 401,TTL1/60·nbf=iat 정상 |
| AUTH-04 | 음수/문자열/소수/지수/int64overflow | 401,JSON정수만 |
| AUTH-05 | alg/typ/extraheader·missing/unknownkid·서명변조 | 401,service 미호출 |
| AUTH-06 | iss/wrongenv/userJWT/비신뢰key | 401 |
| AUTH-07 | 정상LCissuer서명·wrongsub/wrongaud·crosspurpose | 403 |
| AUTH-08 | §2.4.1 교차 서비스·ON/OFF 매트릭스 | 양쪽 ON의 정상 교차 token은 서명 검증 후 403. 발급 서비스 OFF·서명 변조는 활성 대상에서 401. 대상 OFF는 살아 있는 서버의 404. 같은 kid·서로 다른 키로 신뢰 분리와 fallback 금지도 검증 |
| AUTH-09 | missingclaim·arrayaud·jti대문자/nonv4·duplicateJSON/trailing/UTF8/oversize | 401 |
| AUTH-10 | §2.1.1 Bearer 대소문자·SP·탭·앞뒤 공백·중복/쉼표·compact segment 문법 | 정상 `Bearer`+단일 SP만 형식 수락, 나머지 401. header 이름 대소문자는 허용. token padding/빈 segment/추가 점은 401 |
| AUTH-11 | 추가 payload claim과 추가 JOSE header 구분 | 정상 token의 미지 claim/string·object·array·null은 무시하고 기존 결과 유지. scope/roles/userId로 권한 확장 금지. 필수 claim 누락·잘못된 nbf·추가 claim 내 duplicate field·추가 JOSE header는 401 |
| QUERY-01 | 유효token+query x=1/x=/userId/중복parameter | 401,body decode/service 미호출 |
| QUERY-02 | query string=""(빈?) | 401,query string=null정상 |
| QUERY-03 | query+invalidtoken 또는wrongpurpose | 401,query우선 |
| INGRESS-01 | LC OFF/잘못된살아있는localport+invalidtoken/query | 빈 404,no-store,service 미호출 |
| INGRESS-02 | IdentityON/LC OFF·LC ON/IdentityOFF·둘다ON | connector/해당route·키/권한독립,비활성방향404 |
| INGRESS-03 | 둘다OFF→TLSport미기동 | connector없음,HTTP404기대하지않음. mocktransport오류분류/실network는C |
| INGRESS-04 | 활성portwrongmethod/unknowninternalroute | 정상 신뢰 token403,토큰 없음 401 |
| INGRESS-05 | encodedpath/forwardedport·public/기존IAMdirect우회 | 업무미호출,exact인가실패/기존ingress차단 |
| BUSINESS-01 | status OPERATION_NOT_FOUND JSON vs ingress빈 404 | LC분류분리,취소/미소비 확정으로 해석하지 않음 |
| BUSINESS-02 | sameoperation/event+새JWT/jti/응답유실 | 기존멱등수렴·status-first,원body/digest유지 |
| BUSINESS-03 | RESERVED/CONFIRMED/CANCELED/EXPIRED | 기존TTL/terminal불변,재발급·repair-confirm없음 |
| KEY-01 | ON키누락/손상/duplicatekid/RSA<2048/privatefield | startup 실패;OFF 서비스 key 미파싱 |
| KEY-02 | old+new→new발급→old신뢰제거 | overlap수락·제거뒤old401,긴급폐기테스트 |
| PRIVACY-01 | 인증실패·signing/startup·HTTP오류 | token/Authorization/private key/body 원문 log·trace 없음 |
| REPLAY-01 | AttemptGroup ingress404→DEAD_LETTER | 원 eventId/payload/digest/실패 이력 보존, C 도구 선행 |
| REPLAY-02 | 담당 승인 후 한정된 requeue·worker 경합/부분 실패 | 동일 event 보존·CAS/lease/fence·감사, C 완료 증거 |

### 3.2 A/B/C 증거와 담당

- **A — 계약·테스트 명세:** Billing은 이 명세의 route·기대 결과와 기존 r1 시간 검증 모델을 제공한다. LC는 claim·오류 분류·발신 설정 manifest와 도구 범위를 최종 수락한다. 양측은 테스트 전용 signed cases, 시간·HTTP·query·port manifest와 SHA-256 목록을 같은 revision으로 수락한다. 현재 fixtures-1 제작과 Billing 기존 codec의 token/hash 검증을 완료했으며 LC hash 수락·독립 실행과 신규 HTTP 실행은 미완료다. 기존 Billing 13개·Identity 16개 시간 fixture 결과를 LC 검증 결과로 취급하지 않는다.
- **B — 인증 구현:** Billing은 LC verifier·인가·공유 connector의 독립 flag와 IAM 우회 제거를 구현한다. LC는 두 client의 JWT 발신을 구현하고 기존 trace·업무 retry를 유지한다. A 수락 및 ADR/AGENTS 동기화 후 코드 작업을 별도 승인한다. 인증·query·ingress·키·비로깅 검사, 기존 무료·Identity·user·업무 회귀와 양측 clean test를 수행하며 기본 OFF를 유지한다.
- **C — 복구 도구·E2E:** LC의 DEAD_LETTER 도구는 별도 승인·구현·검증이 필요하다. 원 eventId/body/digest·실패 이력을 유지하고 dry-run·권한·작업 상한·감사·CAS/lease/fence를 검증한다. Billing은 멱등 수신·status-first 측을 검증한다. 실제 TLS·키·라우팅·미완료 inventory와 재처리·응답 유실 E2E를 확인한 뒤 Billing 수신 준비 → LC 발신 활성화 순서를 별도 승인한다. DB 직접 수정과 무인증 fallback은 허용하지 않는다.

## 4. 남은 확정 사항

- LC의 최종 수락, [sender 설정 명칭 매핑안](LC-BILLING-WORKLOAD-SENDER-MAPPING-R1.md) 수락, fixtures-1 hash 수락·LC 독립 실행과 B/C의 나머지 fixture 제작·실행이 남았다. A 명세 작성을 공동 검증 완료로 보고하지 않는다.
- TLS 포트 미기동 시 HTTP 응답을 가정하지 않는다. ALB/proxy가 반환하는 404/502/503과 Billing의 응답을 staging에서 구분해 관측하며 기존 전송 오류 분류를 유지한다.
- port·TLS 기동, 키 담당·회수 SLA, 재처리 담당·상한과 활성 inventory는 운영 단계에서 확정한다. 실제 credential이나 개인정보·금융 증거는 명세에 넣지 않는다.
- 현재 ADR/AGENTS의 LC Lattice 규칙은 이후 범위 승인·공동 수락에 따라 동기화할 대상이다. 이번 문서 작업으로 runtime 계약을 교체하지 않았다.
