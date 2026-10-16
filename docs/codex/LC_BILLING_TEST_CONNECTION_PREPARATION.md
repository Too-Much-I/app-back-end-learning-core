# LC test Billing 연결 준비 — 2026-10-16

관련 TMI-116/128/217/125/220. 연결 준비이며 기능 활성화·공동 E2E 완료가 아니다.

## 후속 배포 준비 확인

- 배포 관련 Node 테스트30개를 재실행해 실패/skip0을 확인했다. Java/실제 TLS/E2E 테스트를 새로 실행한 것은 아니다.
- TLS entrypoint는 아직 로컬 변경이다. 현재 배포 workflow는 develop에 머지된 PR의 결과만 test 이미지로 배포하며, 실행 중 task definition을 읽어 이미지 참조를 교체한다. 이번 TLS 변경의 commit/push/PR/develop 머지·자동 test 배포 승인 확인이 다음 단계다. main/prod는 제외한다.
- 이미지 전달 후 ECS의 다음 두 항목은 평문 environment가 아닌 secrets.valueFrom으로 등록한다. 기존 환경·Secret·roles·ports는 보존하고 적용 전 최신 task와 대조한다.
  - `BILLING_ACCESS_EVENTS_TLS_PKCS12_BASE64`: 아래 TLS Secret ARN + `:PKCS12_BASE64::`
  - `BILLING_ACCESS_EVENTS_TLS_KEY_STORE_PASSWORD`: 아래 TLS Secret ARN + `:KEYSTORE_PASSWORD::`
- 비밀이 아닌 alias는 `BILLING_ACCESS_EVENTS_TLS_KEY_ALIAS=lc-test-access-events`다. base64 주입과 `BILLING_ACCESS_EVENTS_TLS_KEY_STORE` 경로 설정을 동시에 사용하지 않는다.
- receiver=false에서는 파일을 만들거나 PKCS12를 파싱하지 않는다. 따라서 OFF 배포 성공은 Secret 주입/일반 앱 기동 증거일 뿐 TLS listener 검증이 아니다. 수신 활성화·port/ALB/SG·DB/index는 별도 gate로 남긴다.

## 2026-10-16 TLS Secret 저장 확인·읽기 권한 완료

- 사용자 저장 완료 회신 후 Secret `tosunsaeng/test/learning-core/access-events-tls`와 비어 있지 않은 `PKCS12_BASE64`, `KEYSTORE_PASSWORD` 필드 존재를 확인했다. AWS 저장값의 실제 PKCS12 런타임 검증은 아직 아니다.
- 확인 ARN: `arn:aws:secretsmanager:ap-northeast-2:889384901776:secret:tosunsaeng/test/learning-core/access-events-tls-s7dCLh`.
- 승인된 `tosunsaeng-learning-core-test-execution-role`에 inline policy `LearningCoreTestAccessTlsRead`를 추가하고 저장된 내용을 재확인했다. 위 exact ARN에 대한 `secretsmanager:GetSecretValue`만 허용한다. 기존 네 정책은 보존했고 총 다섯 정책을 확인했다.
- 아래의 Secret/IAM 미완료 기록은 이 확인으로 대체한다. TLS Secret ECS 참조·새 entrypoint 이미지 배포·ALB/SG·DB/index·기능 활성화는 수행하지 않았다. 서버 task37과 기능 OFF 상태는 유지하며 로컬 비밀 임시 JSON도 아직 보존돼 있다.

## 2026-10-16 TLS 파일 주입 준비·사용자 입력 인계

- `scripts/deployment/access-events-entrypoint.sh`와 Dockerfile 연결을 로컬 구현했다. OFF는 payload 미파싱/환경에서 제거 후 기존 Java 실행. ON은 encoded payload 또는 기존 absolute mount 경로를 사용하되 혼합 입력을 거절한다. base64 최대64KiB/decoded48KiB, 임시dir0700/file0600, 오류 고정 문구, Java exec 전에 encoded 환경변수 제거. 실제 PKCS12/alias/certificate 검증은 기존 AccessEventTls가 수행한다. Java는 PID1로 실행하며 정상 exec 후 임시 파일은 태스크 파일시스템 수명에 따른다.
- Node 배포 테스트30개(신규14 포함), shell syntax/diff 검사 통과. 기존 LC 로컬 이미지에서 network none/read-only root/tmpfs·비root로 가짜 자료를 주입해0600·payload env 제거·정확 bytes smoke 통과. 새 최종 이미지 빌드/실제 TLS connector/E2E 통과는 아니다. 코드 미커밋/미배포.
- 적용 직전 사용자 승인으로 LC 전용 RSA3072 자체서명 인증서/암호화 PKCS12를 로컬 생성했다. alias `lc-test-access-events`, CN/SAN `lc-test-access-events.internal`, 유효기간 2026-10-16 22:28:32~2027-10-16 22:28:32 KST. certificate DER SHA-256 `337c968d050eae38127b59e8834e730d681650047b88638dd7ec9cdb88655ae9`. OpenSSL PKCS12 암호 검증 성공. 평문 private PEM과 중간 PKCS12 파일은 생성 절차에서 제거했다.
- AWS 업로드용 JSON은 `/private/tmp/lc-access-tls-HjOyM2/aws-secret.json`에0600, 부모0700으로 남아 있다. 암호화 PKCS12와 password가 함께 있어 비밀 파일로 취급한다. 저장소·보고서에 내용 복제 금지. AWS Secret 저장/재조회가 끝난 후 이 정확한 임시파일의 정리는 별도로 확인한다.
- Secret exact name `tosunsaeng/test/learning-core/access-events-tls`의 기존 항목이 없음을 확인하고 생성 화면의 기타→일반 텍스트 단계까지 열었다. 비밀값 전달을 위한 native Terminal 접근이 도구 안전 제한으로 거절돼 입력하지 않았다. 다른 UI/우회 경로로 제한을 회피하지 않는다. 사용자에게 직접 일반 텍스트 입력→위 이름으로 저장을 인계한다. Secret 생성/IAM 추가/TLS 주입·네트워크/DB 변경은 아직 없다.
- 저장 완료 후 반환 ARN을 확인해 승인된 LC test 실행 역할에 그 Secret1개 GetSecretValue만 추가한다. PKCS12_BASE64→BILLING_ACCESS_EVENTS_TLS_PKCS12_BASE64, KEYSTORE_PASSWORD→BILLING_ACCESS_EVENTS_TLS_KEY_STORE_PASSWORD로 참조하고 alias를 설정하는 후속이 필요하다. 네트워크 경로·기존 시험 목록/index·기능 활성화는 별도 준비가 남는다.

## 2026-10-16 태스크37 등록·설정 배포

최종 결과: 22:20 KST 배포 완료(4분5초), 서비스 task37 실행1/보류0·배포성공·ALB 정상1/비정상0 확인. 이전task 중지/드레인 및 새task Started를 확인했다. 교체 완료 후 외부health200·무인증history401 재확인. ECS Secret 주입을 포함한 기동 성공이며 sender/receiver OFF이므로 Billing 서명 요청/환불 수신 성공 증거는 아니다.

- 사용자 후속 요청으로 task36에서37을 생성했다. 기존 이미지/58개 환경변수/4개 Secret 참조와 나머지 task 설정을 보존하고 변경할9개 환경변수 및 발신키 Secret 참조1개만 추가했다. 편집기 전체 JSON을 재읽어 작성 내용과 일치함을 확인한 뒤 등록했다.
- 추가: Billing URL, workload JWT environment=test/sender=false, access environment=test/scope=tosunsaeng-billing-test/수정된 public JWK set/receiver=false, paid=false/test-activation-approved=false. 새 secret 참조는 기존 LC 발신키의 PRIVATE_JWK field다. 역방향 private key는 LC에 주입하지 않는다.
- LC test 서비스에 task37 설정 배포를 요청했다. 배포 ID fpo8PbLjnF9u6h1I3Jf8c, 새 task c04ae329bc3440ffbb96d8f7e7f9cbc5. 이 항목 작성 시 새 task RUNNING·애플리케이션 Started 로그 확인, 표시 로그에 APPLICATION FAILED/Exception class 없음. 최종 교체/ALB 성공은 후속 결과로 기록한다.
- 기존 빌드의 실제 AccessWorkloadVerifier를 Java21 source launcher로 호출해 수정 공개키 로드와 malformed token401 확인. JShell은 sandbox socket 제한 및 local engine classpath 문제로 검증하지 못했고 종료한 뒤 Java launcher로 대체했다. 서명 token/네트워크 인증 성공 검증은 아니다. 외부 health200·무인증history401 관측은 rolling 중일 수 있으므로 신태스크 성공 근거와 구분한다.
- TLS·legacy 목록/index·Billing 발신 연결·공동 E2E가 남아 있다. paid/sender/receiver는 OFF이며 DB/index/기존 시험·IAM 추가 변경은 없다. 기존 운영/별도 서비스는 수정하지 않았다.

## 적용 완료

- 사용자가 적용 직전 승인한 범위대로 `tosunsaeng-learning-core-test-execution-role`에 인라인 정책 `LearningCoreTestBillingSigningKeyRead`를 추가했다.
- 허용 action은 `secretsmanager:GetSecretValue` 하나, Resource는 `arn:aws:secretsmanager:ap-northeast-2:889384901776:secret:tosunsaeng/test/learning-core/billing-workload-jwt-mRtG3m` 하나다. wildcard·다른 Secret·운영/Billing 역할 변경 없음.
- 저장 후 정책 JSON 및 정책 3→4개를 확인했다. 기존 Execution/ChallengeCredentials/FcmSecretRead 정책 보존.
- 기존 Secret의 주입 필드는 `PRIVATE_JWK`. 비밀값을 출력/파일 저장하지 않고 공개 구성요소 kty/kid/n/e만 Billing task3의 verifier와 비교해 일치를 확인했다. kid는 `lc-test-billing-20261016-r1`, alg는 RS256이다. 실제 서명/HTTP 인증 성공 검증과는 구분한다.

## LC 태스크에 사용할 매핑 — 아직 미적용

| 대상 | 값/방식 |
| --- | --- |
| BILLING_BASE_URL | `https://billing-workload-test.to-teacher.com` |
| BILLING_WORKLOAD_JWT_ENVIRONMENT | `test` |
| BILLING_WORKLOAD_JWT_PRIVATE_JWK | ECS secrets의 valueFrom으로 위 exact ARN에 `:PRIVATE_JWK::` 접미. 평문 environment 사용 금지 |
| 활성화 | 현재 OFF 유지. 키 참조만으로 paid/receiver/legacy 준비를 통과한 것으로 판단하지 않음 |

아직 task revision 생성·서비스 재배포·키 주입은 하지 않았다. IAM 정책 확인은 실제 ECS Secret 주입 성공 증거가 아니다.

## 남은 실행 조건

### 2026-10-16 수정본 재접수 — key_ops 누락 해소

후속 전달본은 `key_ops=["verify"]`를 포함하며 단일 RSA3072/RS256/use=sig·허용 공개 필드·기존 kid를 확인했다. 667 bytes, 파일 SHA-256은 `8ff85f90bd0d00bdcf69a25b8d0ad3996b761934cc1e52ef6a318a2947d4feb9`다. Node crypto로 계산한 SPKI DER SHA-256 `947df008ad59faf6e27a794ac6c8388df6b6e037263453c48805fe5d35beff49`는 갱신 인계서의 수정 전후 지문과 일치한다. 이전 파일은 덮어써진 상태이므로 이번 작업에서 이전 bytes를 직접 재비교한 것은 아니다.

아래 이전 누락 사유는 해소됐고 이 수정본을 LC 등록 후보로 수락한다. scope는 `tosunsaeng-billing-test`, environment는 `test`다. 실제 Java verifier/서명 HTTP·서버 등록·TLS·DB·배포·활성화는 아직 미실행이다. 인계서에 따르면 AWS Secret 내부 공개 JWK 원본은 미수정이므로 등록 시 반드시 위 수정본/hash를 사용한다. 공개키 전달 승인을 역방향 private key 읽기 권한으로 해석하지 않는다.

### 2026-10-16 역방향 공개키 접수 검토

사용자가 전달한 `/private/tmp/billing-test-refund-public-jwks.json`은 646 bytes, SHA-256 `a47d2266640b43104947d86cba3f8379a1c06386d59a521549854e33d739dbed`다. Node crypto로 RSA3072 공개키 파싱, RS256/use=sig, 단일 키 및 비밀키 필드 부재를 확인했다. 인계서의 environment=test, issuer/sub/audience는 LC verifier와 일치하며 consumerScopeId는 `tosunsaeng-billing-test`다.

단, 전달본에는 `key_ops`가 없다. `AccessWorkloadVerifier`는 정확히 `["verify"]`를 요구하므로 현재 파일은 등록 승인 불가다. 동일 키의 공개 JWK에 해당 필드를 보완한 새 전달본/hash를 받아 재확인한다. 원본 파일 및 verifier를 수정하거나 필수 검증을 완화하지 않았다. 실제 Java verifier 실행·서명/HTTP E2E·서버 등록은 미실행이다. 아래 공개키 확인 항목은 이 형식 보완이 남은 상태로 읽는다.

1. 역방향 Billing→LC 환불 전용 공개 JWK set·consumerScopeId 및 발신 설정을 양측에서 확인해야 한다. LC 발신키를 역방향 키로 재사용하지 않는다. 이번 LC 작업으로 Billing 저장소/발신 설정은 변경하지 않는다.
2. LC 환불 수신은 별도 TLS 포트와 실제 PKCS12 파일/alias/password가 필요하다. 현 Dockerfile은 Java 직접 실행이며 Secret의 base64를 파일로 만드는 entrypoint는 없다. 파일 주입 수단·LC 전용 인증서·제한된 ALB/SG 경로를 준비하고 접근 확대는 적용 직전 승인받는다. Billing TLS Secret을 LC에 임의 공유하지 않는다.
3. LC test DB의 미완료 operation/guard·기존 시험 inventory·index를 실제 조회해야 한다. 지금까지 실DB 조회/설치는 하지 않았다. manifest export/install 수단은 준비가 남았으며 기존 inspector는 요약만 반환한다. 서로 다른 snapshot의 목록/요약을 합쳐 봉인하지 않는다.
4. 목록/hash 승인 및 필수 index 검증 후에만 test 활성화 조합을 적용한다. Session source backfill·기존 기록 삭제·unknown 작업 정리는 하지 않는다.

권한 추가 외 서버 설정/DB/배포 변경 없음. 운영 및 기존 별도 서비스는 건드리지 않았다. 제품 코드가 바뀌지 않아 Gradle은 재실행하지 않았다.
