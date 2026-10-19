# 모의고사 상태 정리 오류 복구: 1차 안전 보완

- 요청: fix 브랜치 구현·commit/push, 테스트 검증 후 운영 배포.
- 관련 기존 Jira: TMI-118/TMI-125. 신규 Jira 없음.
- 브랜치: `fix/exam-coordination-recovery`.

## 반영 범위

- coordinator의 standalone 상태정리 Transaction을 일시 오류에 한해 최초 포함 최대3회,25/50ms 간격으로 재시도한다. 실패한 Transaction은 종료한 뒤 다시 연다.
- 참여 중인 Transaction은 이 경계에서 재시도하지 않는다. owner/삭제 guard, Session/outbox 원자성, 외부 AI/Billing 전송은 변경하지 않는다.
- unknown commit은 중첩된 duplicate/transient 분류보다 우선하며 자동 업무 재실행하지 않는다. Summary completion의 기존 unknown commit 재실행도 제거한다. 저장 증거 확인 없이 성공으로 변환하지 않는다.
- duplicate/optimistic 충돌도 다시 읽고 처리하며 소진시 성공으로 오판하지 않는다.
- worker의 한 후보 실패가 다음 후보를 막지 않게 한다. 실패 후보를 삭제/완료 처리하지 않는다.
- code/고정 label/시도 횟수만 기록하며 원문 메시지를 남기지 않는다. 재시도와 복구는DEBUG, 최종실패는 프로세스당1분간격WARN으로 제한한다. `failureObservationsSinceLastLog`는 계층별 중복관측을 포함하며 고유요청 수가 아니다. worker 경계의 attempt0은 내부시도수 미집계를 뜻한다.

## 명시적 보류

- 재시도 소진 뒤 성공응답으로 바꾸는 P2는 이번1차 패치에서 시행하지 않는다. 무료/legacy Session은 기존 Billing worker의 후보가 아닐 수 있으며 모든접수경로의 durable복구보장은 아직 없다. 계획서의 실패시보호 조건에 따라 기존오류를 유지한다.
- unknown commit의 자동 증거 판정·성공처리도 추가하지 않는다. 이 패치는 자동재실행을 막고 기존오류처리를 유지한다.
- 기존 API/DTO/v1·v2 생성/Billing·AI/S3·Redis 계약과 앱동작은 바꾸지 않는다. DB migration/신규컬렉션/설정활성화 없음.

## 검증

- 일시 오류2회 후성공/3회소진, 중첩unknown우선, 참여Transaction비재시도, 영구/무label오류 비재시도, worker후속후보 진행 단위테스트.
- 실제 격리 Mongo replica-set에서 Session 읽기로스냅샷고정→별도raw-driver owner갱신→coordinator owner갱신 충돌을 주입한다. 5회반복 모두 새Transaction2회/최종revision2로 재시도와단일커밋을 검증한다.
- 전체단위1410개(기존skip1), Mongo통합358개(기존skip2) 실패0 확인. 로컬Docker API오류는 `JAVA_TOOL_OPTIONS=-Dapi.version=1.44`로 해결했으며 제품코드/CI옵션은 바꾸지 않았다.
- 후속 로그제한 변경 후 전체검증 재실행과 GitHub CI/test배포/prod배포 확인은 진행 중이다. 완료 결과는 WORKLOG/CURRENT_STATE 및 PR에 별도 기록한다.

전체계획15개시나리오 및 P2 완료를 주장하지 않는다. 이번 배포는 제한재시도와unknown안전성·관측 보완이다.
