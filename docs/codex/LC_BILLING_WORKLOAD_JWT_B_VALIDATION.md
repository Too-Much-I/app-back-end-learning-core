# LC B workload JWT — 구현·검증 회신

- 날짜: 2026-10-13 / 관련: TMI-116·TMI-128·TMI-122
- 범위: 사용자 B 착수 승인에 따른 LC 구현. Billing 저장소, 실제 DB/AWS 설정, C 도구, 배포·활성화는 변경하지 않았다.
- A 인증 계약·설정 세 명칭과 fixtures-1 bytes/hash는 유지했다. LC 구현 검증이며 양측 전체 B/C 완료 선언은 아니다.

## 구현

- Reservation/phone/status/reconciliation의 공통 client와 AttemptGroup client를 workload JWT로 교체했다. 두 SigV4 signer 의존만 제거했고 S3 공용 AWS provider/SDK는 보존했다.
- 매 HTTP 시도마다 RS256, LC 환경별 issuer, `sub=learning-core-service`, 목적별 단일 문자열 audience, 정수 `iat=nbf`, TTL 60초, fresh lowercase UUIDv4 jti를 발급한다. fallback·원격 키 조회·토큰 캐시는 없다.
- `BILLING_WORKLOAD_JWT_SENDER_ENABLED=false` 기본값, environment/private-jwk 기본 빈 값이다. J OFF에서 private JWK getter/파싱 없이 비활성이고 발급 시도는 거절한다. J ON은 실제 발신 flag와 무관하게 키 self sign/verify를 수행한다.
- J/S/H/R/W/P 조합22허용/42거절, H/R→S, J OFF+(S 또는 P) 차단. 발신 ON은 동일 HTTPS origin과 양수 timeout을 요구한다. 기존 region 설정은 호환을 위해 남겼지만 Billing 서명에는 쓰지 않는다.
- W OFF/P ON drain 허용. index 검증 조건을 W OR P로 확대하고 publisher의 명시적 초기화 의존으로 검사 성공 전에 claim/send할 수 없도록 했다. 기존 unique/claim/TTL index 및 outbox/state `_id_`를 검사한다. key 순서·방향/unique/sparse/partial/TTL/hidden/collation 불일치는 fail-closed하며 자동 drop/recreate하지 않는다. 기존 ensure 방식으로 누락 index/빈 state collection만 준비한다.
- 업무 body, Idempotency-Key, canonical payload/eventId/digest, retry/auth circuit/status-first/lease·retention은 유지했다. DEAD_LETTER 자동 requeue나 신규 event 생성은 추가하지 않았다. 앱 공개 API/AI/S3/Redis 계약 불변이다.

## LC private JWK 입력 형식

단일 JSON object이며 필드는 정확히 `kty,alg,use,key_ops,kid,n,e,d,p,q,dp,dq,qi`다. `kty=RSA`, `alg=RS256`, `use=sig`, `key_ops=["sign"]`, nonblank kid 최대256자, RSA modulus 최소2048bit, 최대64KiB 입력을 요구한다. 중복/추가 JSON 필드·trailing JSON·public-only key·CRT 불일치·유효하지 않은 RSA key는 거절한다. 이는 LC private 입력 형식이며 A public JWKS의 `key_ops=["verify"]` 계약을 변경하지 않는다.

실키는 Secret 주입 대상으로만 다룬다. 예제 private key나 원문 토큰을 문서/로그에 넣지 않는다. 로컬 self-check는 Billing 배포 public key와의 일치를 증명하지 않으며, 활성화 전 kid/n/e manifest 대조와 실제 서명 smoke가 별도로 필요하다.

## 실행 증거

| 검증 | 결과/범위 |
| --- | --- |
| `./gradlew clean test bootJar` 및 최종 `./gradlew test bootJar` | 단위·계약859개, 실패/오류/skip0; 실행 JAR 빌드 성공 |
| `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew mongoIntegrationTest` | 격리 Mongo replica-set146개, 실패/오류/skip0 |
| `node --test scripts/mongodb/*.test.js` | migration121개 통과; 실제 DB 미접속 |
| fixtures-1 raw token/hash 회귀 | 기존73개 포함, 세 JSON SHA256SUMS OK, bytes 수정/재생성 없음 |
| `git diff --check` | 통과 |

Java21/Gradle9.5.1. 최초 Mongo 명령은 Docker29와 기존 Testcontainers1.20.4의 API 호환 문제로 컨테이너 초기화에 실패했다. 위 실행 시 전용 JVM 옵션으로 API1.44를 선택한 재실행이 통과했다. 프로젝트/Docker 영구 설정은 바꾸지 않았다. 기존 deprecated/unchecked 경고는 남아 있다.

추가 테스트가 확인한 경계:

- 64조합 순수 검증과 실제 Spring property binding/issuer context 검증. 전체 서비스·실환경 기동64회를 뜻하지 않는다.
- 실제 publisher/client/state bean 생성과 index 초기화 의존, 준비 실패 시 publisher 생성 차단. 이 context 테스트는 scheduler를 실행하지 않으며 store를 mock한다. 별도 격리 Mongo 테스트는 실제 store/gate/publisher를 writer Transaction 없이 직접 실행한다.
- 실제 loopback HTTP sender의 서명/audience, 같은 operation 재시도에서 fresh jti·동일 body/key, AttemptGroup 동일 body/trace, redirect 미추종. 테스트 전용 HTTP loopback 생성자를 사용하고 배포 startup은 HTTPS 필수다.
- 빈 ingress404와 JSON `OPERATION_NOT_FOUND`의 분리, 기존 auth/복구/trace 회귀. RSA 부정 입력 실패에 원문/원인 예외를 남기지 않는다.
- 격리 Mongo의 빈 collection index 생성·재확인, unique option 충돌 시 자동 수리 없음, 기존 outbox drain, 동시 claim 단일 승자, 응답 유실 후 만료 lease 재claim·구 lease ack 거절, 원 eventId/payload/digest 유지, 새 event 미생성 및 DEAD_LETTER 미claim.

## 미검증·다음 공동 gate

Billing receiver HTTP27 exact chain/port/ON-OFF·인가/실패 시 DB 무호출은 LC 테스트로 대체하지 않았다. Billing 측 실제 실행 결과와 대조해야 한다. 실제 양측 TLS/DNS/ALB/접속 경로, 배포 키 일치·회전/회수 SLA, 구버전 drain·운영 inventory, 실환경 E2E는 미수행이다. C 승인 requeue 도구와 배포·활성화는 별도 승인 후 진행한다. 이번 테스트 성공을 feature ON 승인으로 해석하지 않는다.

commit/push 및 Jira 상태 변경은 수행하지 않았다. 기존 사용자 변경은 보존했다.
