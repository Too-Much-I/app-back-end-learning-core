# LC 운영 코드·배포 기록 동기화 — 2026-10-19

## 결과

- 코드 커밋 `4a7341a`를 `codex/lc-production-candidate-20261018`에 푸시했다.
- 저장된 운영 배포 산출물과 현재 소스로 재빌드한 JAR의 SHA256이 정확히 일치했다. 이번 작업에서 AWS 실행 상태를 새로 조회한 것은 아니다.
- 삭제·알림 Jira의 구현/배포 현황과 미검증 항목을 분리했다.
- 기존 작업 기록과 준비 문서는 보존한다. 과거 OFF/미커밋 표현은 당시 상태이며 최신 결과는 이 문서를 기준으로 읽는다.
- main/develop 머지, 운영 재배포, DB/설정 변경은 수행하지 않았다.

## 산출물 근거

[운영 배포 기록](LC_PRODUCTION_DEPLOYMENT_2026-10-18.md)의 prod task10과 이미지 `prod-frontcheck-3cde8dd`가 비교 기준이다.

- 이미지 digest: `sha256:02e658f3ad7b878b9889f47941c3a6faba341216de8cd6b2a2877078a5fa0c4b`
- 배포 JAR 및 재빌드 JAR SHA256: `3cde8dd70f2656df001d4aede6e04038a8da9a24036efb85f50490ad48ae0968`
- 비교 원본 보관: `/private/tmp/lc-deployed-3cde8dd-20261019.jar` (로컬 임시 자료이며 장기 보존 보장 없음)

코드 커밋은 exact 삭제 preflight 대상 제한, 삭제 기동의 billingDeliveryVerified 선행 요구 제거, 관련 격리 테스트 및 DB 점검/준비 스크립트 등 11개 파일이다. storage/index/retention/auth/fence 조건은 유지한다. 실제 전달 검증 완료 값을 임의로 설정하지 않는다.

fetch 기준 develop은 `509fee0`(Azure 보완 PR49)이며 동등 수정은 현재 후보 소스에 이미 포함되어 중복 cherry-pick하지 않았다. main은 `89defb7`로 후보와 차이가 있다. 후보 푸시를 main 동기화 완료로 보고하지 않는다.

## 재실행 검증

| 검증 | 결과 |
| --- | --- |
| `./gradlew test bootJar --rerun-tasks --no-daemon` | 성공; 1,403개 중 1,402 통과, 기존 1 skip, 실패/오류 0 |
| 삭제 Flow/Wiring/Startup Mongo 격리 테스트 | 39 통과, 실패/오류/skip 0 |
| `node --test scripts/deployment/*.test.js` | 88 통과, 실패/skip 0 |
| 운영 산출물과 재빌드 JAR hash | 일치 |
| diff 공백·비밀값 패턴 검사 | 통과; 테스트용 가짜 URI 제외 실제 비밀값 발견 없음 |

Mongo 검증은 Docker 호환 설정 `JAVA_TOOL_OPTIONS=-Dapi.version=1.44`로 세 삭제 테스트 클래스를 실행했다. 전체 Mongo suite나 운영 실제 삭제/환불 요청 검증은 이번 결과에 포함하지 않는다.

## Jira 정리

| 이슈 | 이번 반영 | 남은 검증 |
| --- | --- | --- |
| TMI-193 | 설명 최신화, 해야 할 일→검토 중 | 운영 삭제→Billing204→LC ACK 및 Mongo/S3/Redis 정리; 통계 OFF |
| TMI-198 | 설명 최신화, 해야 할 일→검토 중 | 정규 scheduler와 iOS 표시/클릭; 수동 11건 FCM 수락은 전체 실제 수신 증거가 아님 |
| TMI-116 | 완료 유지, v1 무료/v2 paid 및 workload JWT 후속 댓글 | 잔여 공동 E2E 구분 |
| TMI-217 | LC 구현·배포·테스트 댓글, 상태 변경 안 함 | 실제204/ACK 공동 확인 |
| TMI-220 | LC8444/TLS/JWT 배포 댓글, 상태 변경 안 함 | 공동 환불 경합 E2E |

TMI-217/220은 재조회 시 검토 중이었으나 이번 작업에서 전이한 것이 아니다. Billing 완료 여부를 LC 결과로 대신 판정하지 않는다.

## 후속

main 정리는 자동 운영 배포를 수반하므로 별도 승인 후 현재 배포 산출물·브랜치 차이를 기준으로 진행한다. 봉인 이후 과거 prod8로 임의 복귀하지 않는다. 미검증 시나리오는 구현 미완료와 구분하며 완료로 닫지 않는다.
