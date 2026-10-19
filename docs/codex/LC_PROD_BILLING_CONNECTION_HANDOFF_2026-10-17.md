# LC–Billing 운영 연결 사전 대조

## 1. 5줄 결론

1. TMI-116/TMI-217/TMI-220: Azure test 배포 다음 작업으로 운영 인증·TLS 연결의 인계 항목을 대조했다.
2. LC 발신 공개 JWK는 로컬 재검증 성공: RSA3072, RS256, verify 전용, 비밀 필드 없음, 기존 SPKI 지문 일치.
3. Billing 회신을 접수했다. 환불 공개 JWK 독립 검사 통과, scope 연결 기준은 `tosunsaeng-billing-prod`, 예정 origin은 `https://billing-workload-prod.to-teacher.com`이다. 실제 주입·연결 완료는 아니다.
4. 운영 태스크 Secret 주입·서비스8444 연결·ALB 전달/차단 규칙·재배포는 아직 이번 작업으로 실행하지 않았다.
5. 실제 삭제 검증·운영19건 정리·legacy 최종 봉인 제외를 유지한다. 이 문서는 운영 활성화 승인이 아니다.

## 2. 반드시 읽을 내용

test의 scope·키·호스트를 운영으로 복사하거나 이름을 치환해 확정하지 않는다. prod Spring profile과 production workload 환경을 구분한다. 키 준비와 실제 인증/TLS 연결 통과는 다르다.

| 방향 | LC 설정/자료 | 다음 확인 |
| --- | --- | --- |
| LC→Billing | `/private/tmp/lc-production-billing-public-jwks.json` | Billing 회신상 운영 Secret 등록·지문 일치, 실행 태스크 미주입·receiver OFF |
| LC→Billing | BILLING_BASE_URL | `https://billing-workload-prod.to-teacher.com` 예정값. DNS/ALB/8443 연결 전 호출하지 않음 |
| LC→Billing | BILLING_WORKLOAD_JWT_ENVIRONMENT=production | 운영 signer Secret 주입과 환경별 trust 대조 |
| Billing→LC | BILLING_ACCESS_EVENTS_BILLING_PUBLIC_JWK_SET | `/private/tmp/billing-production-refund-public-jwks-20261017.json` 독립 검사 통과. LC 설정 저장소 등록·실제 주입 미실행 |
| Billing→LC | BILLING_ACCESS_EVENTS_CONSUMER_SCOPE_ID | `tosunsaeng-billing-prod` 연결 기준 수락. Billing trial expected scope 주입 및 실제 발신 body 대조는 후속 |
| Billing→LC | BILLING_ACCESS_EVENTS_ENVIRONMENT=production | issuer/sub/aud/kid 계약 대조 |
| Billing→LC | 전용 TLS8444·기존 준비 TG | 기존 SG/TG를 현재 상태 재조회 후 서비스·ALB 연결 범위 승인 |

## 3. 필요한 인계·승인

Billing의 공개키·scope 기준·예정 origin·LC 공개키 등록 결과 인계를 접수했다. private JWK·PKCS12 암호·사용자 토큰을 채팅으로 전달하지 않는다. 임시 공개키 파일은 실제 적용 시 지문을 다시 확인하며 Git에 키 원문을 추가하지 않는다.

인계값 확인 후 실제 AWS 현재 상태를 읽기 재검증하고 태스크 주입/8444 서비스 연결/ALB exact 전달 및 차단 규칙의 변경안을 승인받는다. 기능 ON은 manifest/owner/index/공동검증 조건과 함께 별도 결정한다. 다른 작업에서 이미 적용한 내용이 있으면 재적용하지 않는다.

## 4. 주요 위험과 미확인

- 문서에는 과거 prod7/Billing 미구축 관측과 후속 prod 구축 기록이 함께 있다. 최신 실제 상태를 이번 문서 점검만으로 단정하지 않는다.
- 운영19건 부모 미연결과 과거 개발 owner2건 처리, 삭제 DDL/TTL 승인은 해결되지 않았다.
- LC production readiness 커밋9fb8f71은 이전 브랜치에 있고 Azure PR49에는 포함하지 않았다. 운영 전달 범위에 포함되는지 실제 배포 전 확인한다.
- legacy 최종 봉인은 사용자 제외 범위다. 현재 구 writer가 만드는 시험을 누락한 목록으로 강제 활성화하지 않는다.
- Billing 회신상 PaymentRefundConfiguration은 test/SANDBOX 밖의 환불 processing ON을 거절한다. 운영 지원 코드·검증이 선행돼야 하며 LC 설정만으로 우회하거나 양측 활성화 완료로 기록하지 않는다.

## 5. 수행 검증·근거

- LC `application.yml`의 실제 환경변수 이름과 운영 인증/TLS·index 사전 준비 문서를 대조했다.
- Billing `docs/runbooks/billing-prod-foundation-2026-10-17.md` 및 `prod-coexistence-readiness-2026-10-17.md`를 읽기 확인했다. Billing 저장소 수정·외부 메시지 전송 없음.
- LC 공개키 kid `lc-production-billing-20261017-r1`, SPKI SHA256 `01a343c71331a9420ef6dbc8eceec006de85197b7f85f07febbf59e4dfd93b73` 일치. 실제 AWS Secret 내용·서비스 상태는 이번에 재조회하지 않았다.
- 제품 코드/API 변경이 없어 Gradle은 재실행하지 않았다. 로컬 공개키 검사 및 문서 diff 검사만 수행했다. 기존 dirty 파일 보존, commit/push/배포·실DB 변경 없음.

## 6. Billing 회신 접수·LC 독립 검사

- 회신: `/Users/msde76/.codex/worktrees/plan012-refund/billing/docs/contracts/LC-production-billing-connection-response-2026-10-17.md`.
- LC가 전달받은 공개 JWK를 Node crypto로 독립 파싱했다. keys1개·공개7필드만 허용, RSA3072/RS256/sig/verify 전용, kid `billing-production-refund-workload-jwt-20261017` 확인.
- SPKI SHA256 `7586c3b985f3df5acdea7ae06d3c3a6390abe3d405c428c7931bbc6315c40a76`가 회신과 일치. 이는 파일 검증이며 AWS Secret/private key와의 서명 쌍 검증 및 실제 HTTP 인증은 LC에서 이번에 수행하지 않았다.
- LC 공개키 운영 Secret 등록·지문 일치 및 Billing prod3 설정/DNS 미연결은 Billing 측 확인 결과로 접수했다. LC가 AWS를 독립 재조회한 결과로 표현하지 않는다.
- 다음 순서: Billing 환불 운영 지원 보완 및 workload 경로 준비 → LC 현행 AWS 설정 읽기 대조·정확한 연결 변경 승인 → 양측 주입/배포·인증/TLS 검증. 업무 기능 ON과 legacy 봉인·실제 삭제 검증은 별도 범위다.

## 7. LC 운영 AWS 독립 재조회

- 전용 CloudShell 세션에서 ECS/ELB/EC2 describe만 실행했다. LC prod:8, PRIMARY COMPLETED, running1/pending0. 컨테이너 이름의 test 문자열과 무관하게 조회 대상은 prod 서비스다.
- 실행 태스크는8080만 노출하고 서비스 대상 그룹도8080 하나다. Billing Secret 주입 항목 없음. 명시된 saga/reconciliation/phone flag는 false다. 나머지 flag의 미주입은 실행 이미지의 기본값까지 검증했다는 뜻이 아니다.
- 준비된 lc-prod-access-events-8444는 HTTPS8444/IP, health HTTP8080 /actuator/health이며 LoadBalancerArns는 빈 배열이다. prod SG의8444는 기존 ALB SG에서만 허용된 상태를 재확인했다.
- 공유 ALB에서 관련 환불 route는 test host 전용38/39 규칙만 확인됐다. 운영 환불 전달과 billing-workload-prod host 규칙은 이 조회에 없었다. DNS 및 Billing 서비스의 최신 배포 상태까지 확인한 결과는 아니다.
- 로컬 AccessEventTls.connector는 receiver OFF이면 connector를 만들지 않는다. 따라서 OFF 상태에서 포트/TG만 붙인 것을 TLS 연결 성공으로 보고할 수 없다. health가8080이므로 TG healthy만으로8444 TLS 성공을 증명하지도 못한다.
- 다음 변경은 LC 운영 공개키 trust 저장/설정 주입 준비, readiness 코드 전달 범위 확인 후 별도 승인된 재배포다.8444 연결과 수신 ON은 실제 인증/TLS 검증 및 runtime gate를 갖춘 별도 변경으로 다룬다. 이번은 AWS 변경·재배포·기능 ON·DB 작업 없음.

## 8. 후속 승인: 미배포 운영 태스크9 등록

- 사용자 후속 요청으로 현재 서비스 prod8을 기준으로 `tosunsaeng-learning-core-prod:9`를 등록했다. 서비스 update/run-task는 하지 않았다. 실행 서비스는 prod8, running1/pending0 유지다.
- 공개 JWK는 태스크 environment에 등록했다. 별도 Secret/IAM 추가 없이 RSA3072 및 기존 SPKI 지문을 CloudShell에서 재검증했다. 예정 origin, production 환경, scope, TLS alias/port 설정을 포함했다.
- 기존 승인된 두 Secret의 PRIVATE_JWK/PKCS12_BASE64/KEYSTORE_PASSWORD 참조3개를 추가했다. 실행 역할의 exact 두 ARN GetSecretValue 권한을 재확인했으며 비밀값은 읽거나 출력하지 않았다.
- 신규 sender/receiver/paid/production approval/deletion sender는 명시 false. 기존 saga/reconciliation/phone/outbox OFF 및 나머지 설정은 보존했다. 이미지·포트8080·역할·서비스 네트워크는 변경하지 않았다.
- AWS readback의 environment 배열 순서 차이로 최초 직접 동등성 검사는 실패했다. name 정렬 후 전 항목 일치 및 나머지 태스크/컨테이너 필드 일치를 검증했다. 중복 등록 없이 태스크9 하나만 생성했다.
- 배포 workflow는 최신 family revision이 아닌 현재 서비스의 taskDefinition을 읽으므로 이 준비 정의가 자동으로 반영되지는 않는다. 후속 배포 시 이미지/readiness 포함 여부와 기존 구앱 무료 설정을 재확인해 명시 병합한다. 태스크9 자체를 검증된 배포본으로 간주하지 않는다.
- TLS8444 포트 매핑/서비스 연결·수신ON·실제 인증/TLS·Billing DNS/runtime 검증은 미완료다. IAM/네트워크/DB/manifest/commit/push 변경 없음. 증빙 `/private/tmp/lc-prod-task9-prepared.png`.

## 9. 배포 전 코드 점검: 최종 봉인 제외와 운영 전환의 충돌

- 후속 진행 요청에 따라 로컬 코드의 기동/구앱 경로를 정적 점검했다. `AccessEventRolloutGate.afterPropertiesSet`는 receiver ON 시 `PaidExamTestActivation.requireReady`를 호출한다. production 승인뿐 아니라 paid/saga/signer/receiver/owner ON, production 환경 일치, sealed runtime 및 index 검증이 모두 필요하다.
- `LegacyExamRuntimeConfiguration`은 paid ON에서만 생성되며 verifySealed를 통과해야 한다. `TransitionFreeExamService.start`는 legacy-create ON·owner·runtime 중 하나라도 없으면 기존409로 거절한다. 따라서 새 공존 코드를 paid OFF로 운영 전달하면 구앱 신규 생성이 계속 가능하다고 가정할 수 없다.
- 이전 안내의 ‘배포 후 TLS 검증’에는 이 선행 조건이 빠져 있었다. 미배포 태스크9는 연결 설정 보관용이며, 수신만 먼저 ON하거나 최신 코드로 이미지만 교체해 배포할 준비가 끝난 상태가 아니다.
- 사용자 최종 봉인 제외를 유지해 실제 배포·수신ON·봉인·코드 우회 변경을 수행하지 않았다. Billing 운영 readiness 확인 후 최종 목록/전환 구간을 별도 승인받는 경로를 권장한다. 봉인 전에 수신만 검증하려면 기존 공동 rollout gate를 분리하는 별도 설계·구현 승인이 필요하며 이번에 임의 완화하지 않는다.
- 이 점검은 로컬 코드/기존 테스트 명세 정적 대조다. AWS 최신 상태 재조회·Gradle/공동E2E 실행 결과가 아니다.
