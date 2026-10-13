# ADR — LC→Billing workload JWT 전환의 구현 준비

- 날짜: 2026-10-13
- 관련: TMI-116 / TMI-128 / TMI-122
- 상태: **계약·구현 준비 수락 후 사용자 B 구현 승인 접수. LC 코드 구현, 배포/활성화는 미승인·미수행.**

## 결정 요약

1. Lattice 사용 불가 제약에 따라 LC의 여섯 Billing POST 인증 목표를 A 확정 workload JWT로 전환한다.
2. 기존 업무 wire·멱등성·복구·trace와 S3 AWS provider를 유지한다.
3. startup 조합표의22허용/42거절을 수락하고 W OFF/P ON drain을 허용한다.
4. index 준비 검증은 W OR P에서 요구하며 publisher-only에 writer Transaction을 강제하지 않는다.
5. 후속 사용자 승인으로 LC B 코드·격리 테스트까지 진행한다. 인프라·실DB·배포·C도구는 변경하지 않는다.

## 문맥·정본

기존 TMI-116/phone/reconciliation/AttemptGroup 문서는 SigV4 `vpc-lattice-svcs`와 Lattice security를 전제로 했다. 후속 B 구현은 두 client를 JWT로 교체한다. 이 ADR은 **B 전환 범위에 한해서만** 기존 인증 전제를 대체하며 과거 구현 사실을 수정하지 않는다.

- [A 수락](../contracts/LC-BILLING-WORKLOAD-A-ACCEPTANCE-R1.md)
- [동결 인증 규격](../contracts/LC-BILLING-WORKLOAD-AUTH-R1.md)
- [수락 startup 조합표](../contracts/LC-BILLING-WORKLOAD-SENDER-MAPPING-R1.md)
- [LC B 계획](../codex/LC_BILLING_WORKLOAD_JWT_B_IMPLEMENTATION_PLAN.md)

상세 claim·시간·인가·오류·키 회전 규칙과 fixture hash는 위 A 정본을 따른다. 이 ADR은 이를 변경하지 않는다.

## 결정·영향

- LC 전용 signer와 두 목적별 JWT를 사용하고 Billing-only SigV4 인증 의존을 제거하는 것이 B 목표다. 기존 공용 AWS provider/SDK와 S3를 제거하지 않는다.
- H/R→S 유지. J OFF+(S OR P)는 파싱 전 거절, J ON 키 검증, J OFF 키 미파싱. 기존 DB/lease/alert/timeout 검증 유지. W OFF/P ON은 기존 event drain이고 신규 event 생성/DEAD_LETTER requeue 허용이 아니다.
- W OR P가 index 준비 경계다. 기존 uq_session_slot/claim/TTL 및 `_id` 조건 검증, 성공 전 scheduler 발신 차단, 충돌 시 fail-closed. 실DB index 생성·정리/재구성은 별도 운영 승인이다.
- 기존 문서의 Lattice exact-route/IAM gate는 승인 후 B의 workload exact-auth/HTTPS/네트워크 준비와 양측 positive/negative 테스트로 대체한다. feature default-off, 원본 보존·status-first·운영 승인 gate는 완화하지 않는다.
- Billing verifier/connector/IAM permitAll 제거는 Billing 담당이며 LC가 타 저장소를 수정하지 않는다. 사용자 JWT/Identity 서비스 인증·공개 API·AI·업무 원장 계약은 유지한다.

## 남은 결정·검증

B 코드 착수 승인은 접수했다. 실행 증거와 미검증 경계는 [LC B 검증 결과](../codex/LC_BILLING_WORKLOAD_JWT_B_VALIDATION.md)를 따른다. C 도구와 실제 키/TLS/ALB/네트워크/E2E·배포/활성화는 별도다. 로컬 테스트를 배포 환경 연결 성공으로 보고하지 않는다.
