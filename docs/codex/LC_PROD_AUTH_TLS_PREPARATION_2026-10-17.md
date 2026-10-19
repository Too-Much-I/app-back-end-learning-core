# LC 운영 데이터·인증/TLS 준비 — 2026-10-17

## 5줄 결론

1. 사용자 적용 승인 후 LC 운영 전용 JWT RSA3072 및 별도 TLS RSA3072 키를 새로 생성했다.
2. Secrets Manager 신규 두 항목 저장과 운영 실행 역할의 exact 두 ARN 읽기 권한 추가를 완료했다.
3. Secret 재조회·PKCS12 복호화·공개키 지문 대조가 통과했다. 실제 TLS 연결/E2E는 미실행이다.
4. 운영 데이터는 읽기 점검 상태이며 index 설치·기존 시험 목록 봉인은 미완료다.
5. prod task7 running1/pending0 유지. 배포·기능 ON·네트워크 변경·Billing 변경은 없다.

## 반드시 읽어야 하는 내용

이번은 인증 자료 준비이며 운영 활성화 완료가 아니다. 기존 시험을 생성하는 구 writer가 실행 중이라 목록을 지금 최종 봉인하지 않는다. 전환 직전 writer 경계를 통제한 뒤 exact 목록을 다시 확정한다. 기존 source backfill·시험 수정·삭제는 하지 않는다. 앞선 관측 1,718건은 고정 전환 건수가 아니다.

## 승인 및 후속 결정

사용자는 운영 키·TLS 생성/두 Secret 저장 및 `tosunsaeng-learning-core-prod-execution-role`의 두 Secret 읽기를 명시 승인했다. TTL 자동 삭제 인덱스, SG/ALB 접근 확대 및 서비스 배포는 이번 승인 대상이 아니다. 해당 변경은 exact 대상과 영향 확인 후 적용 승인받는다.

## 준비된 인증 자료

| 항목 | 실제 값 |
| --- | --- |
| JWT Secret | `arn:aws:secretsmanager:ap-northeast-2:889384901776:secret:tosunsaeng/prod/learning-core/billing-workload-jwt-vIhUfy` |
| JWT JSON field | `PRIVATE_JWK` (JWK JSON 문자열) |
| kid | `lc-production-billing-20261017-r1` |
| 공개키 SPKI SHA256 | `01a343c71331a9420ef6dbc8eceec006de85197b7f85f07febbf59e4dfd93b73` |
| TLS Secret | `arn:aws:secretsmanager:ap-northeast-2:889384901776:secret:tosunsaeng/prod/learning-core/access-events-tls-PgvW4j` |
| TLS JSON fields | `PKCS12_BASE64`, `KEYSTORE_PASSWORD` |
| TLS alias / SAN | `lc-prod-access-events` / `lc-prod-access-events.internal` |
| TLS 성격 | ALB 뒤 내부용 자체 서명, 생성 시점부터365일, notBefore 5분 여유 |
| 인증서 DER SHA256 | `d7d5888397e0e08dca9125be82ff5ea0284a1c69467fe093addac02e5c8ce80a` |
| 추가 inline policy | `ReadLcProductionBillingAndTls`: `secretsmanager:GetSecretValue`, 위 두 ARN만 |

기존 inline policy 두 개는 유지했다. 키/암호를 출력하거나 저장소에 기록하지 않았으며 생성/저장은 CloudShell 메모리에서 수행했다. 공개키만 `/private/tmp/lc-production-billing-public-jwks.json`에 내보냈다. 공개 JWK는 `key_ops=[verify]`, 비밀 필드 없음, RSA3072이며 원 키와 SPKI 지문 일치를 확인했다.

## 연결 시 사용할 매핑 — 아직 주입하지 않음

- `BILLING_WORKLOAD_JWT_PRIVATE_JWK` ← JWT Secret `PRIVATE_JWK`.
- `BILLING_ACCESS_EVENTS_TLS_PKCS12_BASE64` ← TLS Secret `PKCS12_BASE64`.
- `BILLING_ACCESS_EVENTS_TLS_KEY_STORE_PASSWORD` ← TLS Secret `KEYSTORE_PASSWORD`.
- `BILLING_ACCESS_EVENTS_TLS_KEY_ALIAS=lc-prod-access-events`.
- Spring profile `prod`, workload/access-events wire environment `production`. test 키·승인·scope 재사용 금지.
- Billing이 검증할 LC 공개 JWK만 전달한다. Billing 운영 역방향 공개키·scope·origin은 별도 인계 확인이 필요하다.
- 기존 Identity 사용자 JWT/삭제·탈퇴 인증 계약은 변경하지 않는다.

## 데이터·네트워크 잔여 준비

기존 [읽기 점검](LC_PROD_READINESS_INSPECTION_2026-10-17.md)에 따라 신규 operation/legacy/outbox/revoke/삭제 관련 collection/index 준비가 남아 있다. test의 빈 manifest 설치 스크립트를 production에 그대로 실행하지 않는다. index는 실제 Java validator와 dry-run 대조 후 적용하며 충돌 index drop/recreate를 하지 않는다.

`exam_creation_operations.purgeAt` 및 `attempt_group_event_outbox.expiresAt` TTL은 별도 영구 삭제 승인 필요다. 미완료 operation·미전달 outbox는 삭제 대상이 아니며 보존 시각 생성 규칙을 함께 확인한다. 나머지 삭제/owner index도 개별 검토한다.

현재 prod SG는 `sg-0372f877eda5ef96f`, 태스크 public IP는 DISABLED다. 서비스는8080만 연결돼 있다. 8444 대상 그룹/ALB exact route 및 허용 발신 대역은 Billing 운영 배포 inventory와 대조 후 결정한다. test NAT/SG를 임의 복사하지 않는다.

## 검증·범위

- AWS 두 Secret 저장값 재조회 성공, PKCS12 복호화 및 원 인증서 일치, IAM policy exact 조회 일치.
- 로컬 공개 JWK RSA3072·지문 확인 성공.
- prod ECS task7/running1/pending0 재확인. 실행 역할 권한 준비 외 서비스 설정 변경 없음.
- 제품 코드 변경이 없어 Gradle 미실행. 실제 listener 기동·TLS/auth·DB 정합성·공동 E2E 통과를 주장하지 않는다.
- 관련 TMI-116/TMI-128/TMI-217/TMI-220/TMI-193. 기존 dirty 파일과 사용자 DS_Store를 보존했고 commit/push/merge하지 않았다.
