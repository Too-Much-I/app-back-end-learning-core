# LC 유료 시험 P3 — 확정 후 로컬 마무리·status-first 복구 기반

- 날짜: 2026-10-14 / 브랜치: `feat/billing-paid-exam-p3`
- 시작점: P2/P2.1 PR #38 merge `001b5751b1a532a297db4b1e5b92e418344e3e60`
- 관련: TMI-116/TMI-128/TMI-122, 선행 P1 TMI-218. 신규 Jira 생성 없음.
- 범위: 실제 LC 서비스 코드와 격리 Mongo 검증. 공개 route/예약 scheduler 연결과 paid 활성화는 계속 차단한다. P3.1 MEMBER 재개와 P4~P6 전체 완료로 해석하지 않는다.

## 동작·저장 경계

1. P1/P2에서 준비된 operation과 요청 digest를 받아 같은 owner Transaction·공유 lease/version으로 발신 marker를 먼저 저장한다. 새 HTTP 호출만 reserve할 수 있고 유실 뒤에는 status부터 확인한다.
2. RESERVED를 검증하면 새 Session을 `ENTITLEMENT_CONFIRMING`, `active=false`로 저장한다. source/연결 snapshot과 필수집합 coverage를 복사하되 답안·Job·점수를 승계하지 않는다. 기존 시험은 이때 닫지 않는다.
3. confirm 전 durable marker를 저장하고 HTTP는 Transaction 밖에서 실행한다. 짧은 confirm 성공/거절 응답은 full evidence를 덮어쓰지 않으며 다음 status pass에서 권위 결과를 확인한다. 내부 호출 반환 operation이 SESSION_COMMITTED이면 아직 공개 성공 대상이 아니다.
4. full CONFIRMED의 귀속·불변 source를 검증한 뒤 같은 Transaction에서 exact 원 Session 신규접수 차단, 새 Session 활성화, operation SUCCEEDED/guard 해제/120일 결과 보존을 저장한다. 다른 활성 시험이 끼어 있거나 owner/원 대상/새 Session이 달라졌으면 전체 rollback한다.
5. full CANCELED/EXPIRED 또는 CANCELED+confirmRejection에서만 exact 대기 Session을 보상한다. 거절은 ENTITLEMENT_REVOKED로 재현한다. RESERVED/404/timeout/응답 유실만으로 guard 해제·보상하지 않는다. 성공 confirm을 cancel하지 않는다.
6. status 전용 PRE_RESERVE_REJECTED는 PREPARED·예약/Session/commit/confirm 전송 증거 부재, 요청 digest·owner·lease 조건을 모두 만족할 때만 FAILED_TERMINAL로 종료한다. MEMBER pending은 이 증거가 아니다.

`submissionsClosedAt/submissionsClosedByOperationId`는 새 접수 차단 사실이다. 원 Session을 ABANDONED로 일괄 변경하지 않으므로 이미 접수된 Job을 취소하지 않는다. 기존 terminal 상태도 유지한다. 업로드 진입 및 신규 Question Job/receipt 생성에서 닫힌 Session을 거절하고, guard 경로의 기존 Job replay는 유지한다. 부분접수만으로 COMPLETED를 만들지 않는다. P4의 모든 callback/파생 Summary/삭제·merge 경합 경로 검증은 별도다.

LC `terminalAt`은 최초 로컬 terminal commit 시각이며 `confirmedAt`/embedded status의 terminalAt은 Billing 최초 확정 시각을 보존한다. terminal 결과는 로컬 terminal+120일 보존, 미완료에는 purgeAt을 설정하지 않는다. 반복 결과 조회는 보존 시각을 연장하지 않는다. 이미 보존된 성공의 replay도 현재 actor/owner/삭제·Session 존재를 확인하며 지운 Session을 재생성하지 않는다.

## 복구와 남은 연결

`PaidExamRecoveryService`의 HTTP/worker는 같은 `ReservationOperationExecution` lease를 사용한다. worker 전용 schema2 claim은 명시적 호출용이며 기존 schema1 due-query/scheduler는 변경하지 않았다. worker는 status+confirm 최대2회, 기존24시간/200회 budget을 재사용한다. reserve/continuation discovery/Session 생성은 하지 않는다. Session 없는 RESERVED는 증거만 보존하고 HTTP 복귀 또는 후속 운영 처리를 기다린다.

MEMBER/J3 외 신규 계약 오류는 안전 격리, owner는 BLOCKED_OWNER, auth는 circuit 차단이다. P3.1의 MEMBER 원인 이력/좁은 재개 및 schema2 auth probe/자동 스케줄 편입·precommit cleanup 운영 연결은 후속이다. 이번 내부 서비스가 준비됐다고 공개 saga/worker 전체 전환이나 자동 복구 운영 완료를 주장하지 않는다. `PaidExamActivationGate`는 여전히 ON 기동을 거절한다.

unknown commit은 성공·실패를 추정하지 않으며 다음 pass의 fresh majority operation/status로 수렴한다. 테스트의 commit-ack 유실은 실제 Mongo commit 이후 예외를 주입한 범위이고 실제 네트워크 partition/failover E2E 증거가 아니다.

## 변경 파일

- 신규 `PaidExamLifecycleService`, `PaidExamRecoveryService`, `PaidExamP3MongoTest`.
- `ExamSession`: 접수차단/대기활성/안전보상 메서드. `ExamCreationOperation`: paid terminal120일.
- `ReservationOperationExecution`, `ReservationRecovery`: schema2 명시 claim·confirm dispatch·unknown outcome 처리.
- `PaidReservationEvidenceStore`: 기존 canonical digest 공유. `ExamGradingService`, `ExamSubmissionEvidence`, `ExamServiceImpl`: 신규접수/업로드 차단 경계. `ExamGradingServiceTest`: 대기/닫힌 시험 및 기존 Job replay 검증.
- `PaidExamActivationGate`, 계획/ADR/보고서/CURRENT_STATE/WORKLOG: 잔여 gate와 구현 상태 기록.

## 계약·위험·배포 전 확인

공개 URL/Method/body/성공 DTO/BaseResponse, AI `user_id=examId`·callback, retryCount, S3/Redis key는 유지한다. 새 collection/index/실DB 변경·Billing/Identity 코드 수정 없음. P2/P2.1만 커밋/푸시/develop 머지했으며 P3는 로컬 미커밋 상태다. 이번 P3 구현의 예상 밖 제품 변경은 없다. 사용자 DS_Store와 동시 문서 기록은 보존했다.

사용자 승인으로 E06-owner fixture 문구 정정은 병행 추적으로 미뤘다. 원 fixture bytes/hash는 불변이며 공동 최종 검증·활성화 전 신규 revision으로 정정·대조해야 한다. P3.1 MEMBER/owner 복구, P4 기접수 완료 전체 매트릭스, P4.1 생성/삭제 fence, P5 revoke, P6 legacy/purge 및 실환경 gate를 완료하기 전 public/scheduler/paid flag를 켜지 않는다. 전체 공동 상태28개 완료를 주장하지 않는다.

## 검증

`JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew clean test mongoIntegrationTest bootJar` 성공. 단위1,143개 실패/오류0·기존 조건부1 skip, Mongo197개 실패/오류/skip0, bootJar 성공이다. P3 단위2개·Mongo20개가 추가됐다. 최종 confirmed snapshot 공통 검증 연결 후 `test mongoIntegrationTest bootJar` 전체 재검증도 성공했다. 원 fixture25개 checksum과 diff check 통과. Node 배포/마이그레이션23개도 통과했다.

격리 Mongo 테스트는 생성 전/후·confirm 유실/status 복구·영속거절·owner/삭제/다른current·stale source/lease·동시 확정·접수 증거·J3·worker reserve0·terminal replay·commit ACK 유실 주입·digest 불일치를 포함한다. 외부 Billing/Identity와 실환경 통합 테스트 또는 공동 상태28개 전체 검증은 아니다.

P2/P2.1 전달: 커밋 `0cdb458`, PR #38, develop merge `001b575`. GitHub Verify run38532796134 성공 후 머지했고, 기존 테스트 서버 자동 배포 run38533145337은 health 검증을 포함해 success를 확인했다. P3는 이 배포에 포함되지 않았다. 설정/운영 배포 변경 없음.
