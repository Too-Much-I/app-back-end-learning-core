# LC 유료 시험 전환 절차·도구 구현 기준

- 작성일: 2026-10-16
- 상태: 사용자 후속 요청에 따른 절차 초안 및 도구 구현 기준. 운영 담당자·환경·변경창 승인/도구 구현/실행 완료가 아니다.
- 관련: TMI-116/TMI-128/TMI-122/TMI-125/TMI-217/TMI-220/TMI-193/TMI-136.

## 1. 5줄 결론

1. 먼저 test에서 별도 승인된 공동 검증을 수행하고 실제 전환은 환경별로 새 목록·새 승인을 받는다.
2. 신규 생성 유입 차단과 모든 구writer/진행 요청 정리를 증명한 뒤 동일 snapshot에서 목록과 summary를 만든다.
3. raw 시험 ID 목록은 제한된 artifact로만 전달하고, 보고서에는 revision/hash/count·검증 결과만 남긴다.
4. 목록 설치·봉인 후 새 reader 검증과 양측 E2E를 통과해야 신규 생성 traffic을 연다. 현재 paid ON 기동차단은 그대로다.
5. 실패 시 신규 생성 차단을 유지하고 증거를 보존한다. flag OFF·state 삭제·원 시험 backfill을 자동 rollback으로 사용하지 않는다.

## 2. 반드시 읽어야 하는 내용

### 테스트 서버 단순 경로 — 사용자 운영 상황 확인

2026-10-16 사용자 확인: 테스트 서버는 사용자가 시험을 생성하지 않으면 새 시험이 생기지 않는 환경이다. 이는 사용자 제공 운영 정보이며 실제 서버 조회 결과는 아니다. 이 전제에서는 별도 traffic 차단 시스템이나 복잡한 중지 절차를 먼저 만들 필요 없이, 작업 동안 참여자가 생성 요청을 보내지 않는 방식으로 단순화한다. staging/production에 자동 적용하지 않는다.

1. 실제 대상이 test인지 확인하고 작업 중 수동 시험 생성·자동 테스트 실행·재배포를 하지 않는다.
2. 기존 미완료 생성 operation/guard와 자동 생성 요청을 보내는 작업이 없는지 최소 읽기 점검한다. 사용자가 새 요청을 보내지 않는 것과 기존 미완료 요청이 없는 것은 구분한다.
3. 문제가 없으면 기존 시험 목록 추출·검증·설치를 진행한다. 이 조건만을 이유로 서버 전체를 반드시 중지하지는 않는다. 생성 writer가 관측되면 중단하고 범위를 확인한다.
4. 별도 승인된 코드/설정 전환 후 직접 새 시험을 만들어 Billing reserve/confirm·LC 활성화와 기존 시험 보존을 검증한다.

기존 시험 목록 구분·source 필수·기동차단 해제에 필요한 검증은 생략하지 않는다. 자동 installer나 운영 수준의 traffic 제어 시스템 구축을 테스트 시작의 필수 선행으로 두지는 않으며, 실제 적용 수단과 대상은 실행 요청에서 확인한다. 이번 사용자 확인으로 DB 초기화/기존 시험 삭제·실제 배포/활성화를 실행한 것은 아니다.

### 실행 전 환경 확인

현재 [배포 workflow](../../.github/workflows/deploy-staging.yml)와 [대상 선택](../../scripts/deployment/select-target.sh)은 develop→test, main→staging endpoint를 사용한다. main을 production이라고 가정하지 않는다. 실제 AWS service/task/image digest·실제 연결 DB·Billing 대상 환경·JWT environment 일치 확인은 실행 직전 읽기 점검으로 수행한다. 이번에는 AWS/DB를 조회하지 않았다.

환경마다 별도 변경 기록에 대상 환경, 승인 commit/image, 담당자, 변경창, 제한 artifact 위치/접근자, 승인 summary 위치, 복구 담당자를 기록한다. credential/URI/private key/raw 시험 ID는 일반 보고서에 남기지 않는다. 값이 비어 있으면 운영 실행은 중지한다. test의 목록/hash를 staging/production에 복사하지 않는다.

### 역할·권한 분리

| 역할 | 책임과 제한 |
| --- | --- |
| 배포 담당 | 실제 인스턴스/예약 task/자동확장·재배포 유입 통제, 구writer drain 확인. workflow 실행 성공만으로 증명하지 않음 |
| 목록 생성 담당 | 읽기 전용 snapshot 조회·제한 artifact 생성. 원 Session/권리 변경 금지 |
| 검토 담당 | 대상 DB/환경·revision/hash/count·검증 증거를 별도 승인. 같은 DB의 state를 승인값으로 자동 채택 금지 |
| 설치 담당 | 승인 artifact와 exact 대상만 설치. 자격증명 최소권한·변경창 한정, index drop/Session 수정 금지 |
| LC/Billing 검증 담당 | 각 서비스 독립 결과와 공동 경합/E2E 결과 구분, 실패 시 traffic 재개 금지 |

역할은 책임 구분이며 담당자 이름이나 새로운 계정/IAM을 이번에 생성·지정하지 않았다. 이미 승인된 제한 저장소를 우선 사용하고 새 외부 서비스나 공개 링크를 만들지 않는다.

## 3. 결정·입력이 필요한 사항

- 첫 실행 환경과 변경창, 실제 구writer 중지/자동재개 차단 수단, 위 역할별 담당자.
- raw artifact를 둘 기존 제한 저장소 및 접근/전달/삭제 기준. ID 목록도 개인정보 연결 가능 자료로 다룬다. 임의 120일 TTL을 적용하지 않는다.
- 공동 검증 환경과 Billing 수신/권리 테스트 자료·JWT/TLS 담당, 기능 활성화/기동차단 해제 변경의 승인 범위.

제품의 문제선정·무료권 복원 정책을 다시 선택하는 항목은 아니다. 미확정 운영 입력을 임의의 실환경 값으로 채우지 않는다. 도구 코드는 가짜 ID·격리 DB로 구현할 수 있지만 실제 apply에는 별도 실행 승인이 필요하다.

## 4. 위험·중단 및 복구 기준

- `LegacyExamWriterGuard`는 state 설치 후 이번 코드를 사용하는 구writer의 재개를 막는다. guard 없는 구바이너리, 설치와 겹친 진행 요청, state 삭제/backup 복원을 막는 분산 lock이 아니다.
- 현재 `PaidExamActivationGate`는 ON을 무조건 거절한다. 설정만 켜면 시작하지 못한다. 로컬 테스트 성공이나 이 문서를 근거로 삭제하지 않으며 test 공동 검증용 해제도 명시적 검증 계획/승인 아래 별도 변경한다.
- candidate 검사에서 Billing 연결 Session이나 미완료 operation이 발견되면 중단한다. 현재 inspector는 모든 Session이 unlinked인 최초 전환 전제다. 기존 연결 자료를 source 없음으로 취급하거나 목록에 강제 편입하지 않는다. 별도 자료 분류·복구 검토가 필요하다.
- 목록 밖 신규 Session, writer 재개, 환경/hash 불일치가 관측되면 기존 후보 승인을 폐기하고 drain부터 다시 확인한다. 과거 1,493/146건을 현재 대상 수로 재사용하지 않는다.
- `writersDrained`/`readerCoverageVerified`는 외부 검증 증거를 참조하는 확인값이다. installer 성공만으로 true를 작성하지 않는다.
- 설치 중단/unknown commit: 읽기 재확인으로 같은 artifact가 정확히 저장됐는지 판단한다. 다르면 격리하고 자동 덮어쓰기·drop·state 삭제를 하지 않는다. 불명 상태에서 신규 생성은 계속 차단한다.
- 봉인 이후 장애: 신규 생성 traffic을 닫고 원래 operation·deny·삭제 ACK·receipt를 보존한다. 이미 confirm된 권리를 cancel하거나 새 key/무료권으로 보상하지 않는다. 같은 operation status-first 복구를 사용한다.
- 배포 rollback은 paid 자료/deny를 읽을 수 있는 검증된 버전만 후보로 삼는다. 구writer-only 바이너리로 복귀하거나 feature OFF만으로 traffic을 열지 않는다.

## 5. 단계별 실행 순서와 통과 증거

| 단계 | 수행 내용 | 다음 단계 조건 |
| --- | --- | --- |
| 0. 준비 | commit/image, 환경/DB, 키/TLS/index, 공동 검증 대상과 변경창 승인 확인 | 담당자·대상·복구 계획이 채워짐 |
| 1. 유입 차단 | 신규 시험 생성 유입 차단. 자동확장·예약 작업·자동배포가 구writer를 재개하지 않도록 통제 | 모든 writer 목록·현재 버전·통제 증거 확보 |
| 2. drain | 진행 생성 요청·lease/guard와 unknown 결과를 기존 복구로 정리. owner/delete 등 목록·소유권에 영향을 주는 변경도 export/install 구간에 통제 | 미완료/불명 생성 없음, 진행 writer 없음. 시간 경과만으로 성공 처리 금지 |
| 3. snapshot/export | 동일 Mongo snapshot에서 exact ID 집합과 환경/DB/revision/hash/count 생성 | 원자료 변경 없음, 중복·초과·Billing 연결 없음, artifact 독립 대조 |
| 4. 별도 승인 | summary 및 raw artifact 접근경로를 제한된 변경 기록으로 검토 | DB에서 읽은 값을 그대로 승인값으로 삼지 않음 |
| 5. install/verify | 별도 승인된 도구로 exact entries 준비, 승인값/DB 비교·index 검사 | 부분 설치는 재개 가능하되 다른 bytes면 중단, raw ID 로그 없음 |
| 6. seal | drain 유지 및 reader 검증 증거 확인 후 exact SEALED state를 기록, `verifySealed(expected)` 재실행 | 봉인/summary/목록 불변 일치. 이때도 신규 생성은 닫힘 |
| 7. 검증 배포 | 승인된 gate 해제 변경과 새 설정을 검증 환경에 적용. 재시작·OFF rollback 차단 포함 확인 | 양측 실제 HTTP/TLS/경합 검증, 실패/관측 기준 충족 |
| 8. 유입 재개 | 해당 환경 활성화 승인 후 제한된 검증 traffic부터 재개 | 새 writer만 생성, source 필수, legacy 기록/기접수 작업 보존 |

기존 Callback·삭제 작업을 무조건 버리는 절차가 아니다. export/install 중 정지해야 하는 실제 writer 범위와 callback 재전송/유실방지 방법은 배포 환경에서 확인한다. 처리되지 않은 요청을 “오래됨”으로 완료/삭제하지 않는다. 전체 채점 완료를 기다리는 것이 아니라 생성/목록 경계에 영향을 주는 진행 변경을 통제한다.

필수 공동 확인: 일반 INITIAL·동일 문제 재시작·전체접수 후 다음 문제, pending Session 사용 불가, confirm 성공 응답유실, MEMBER same-key 보강, phone paid/free, 생성/삭제 양순서, 삭제204 저장, 환불·과거 Session Callback/파생Summary, owner 이전/탈퇴 deny, 새 요청 source 누락 거절, 재시작과 state 유지. Billing 금융/복원1회는 Billing 증거 없이 LC mock 테스트로 통과 처리하지 않는다.

## 6. 후속 도구의 구현·테스트 기준

실행 가능한 CLI는 아직 없다. 아래는 구현 기준이며 가짜 명령을 제공하지 않는다.

- export: 실제 ID와 summary를 **같은 snapshot**에서 얻는다. 현재 `inspectCandidate`는 summary만 반환하므로 이후 별도 query로 ID를 덧붙여 같은 snapshot이라고 주장하지 않는다. 최대100,000건·엄격 ID·정렬/hash는 기존 `LegacyExamManifest` 규칙을 재사용한다.
- artifact: manifest semantic hash와 파일 bytes 검사를 구분하고 서로 대체하지 않는다. 원문 목록은 제한 파일로만 저장, stdout/report는 revision/hash/count/고정 오류만 사용한다. credential은 명령 인자나 출력에 넣지 않는다. 파일 권한·심볼릭링크/기존파일 덮어쓰기 거절을 검증한다.
- 기본 read-only: DB/환경/승인 summary와 artifact를 검사하되 write하지 않는다. apply는 별도 명시 옵션과 승인 입력·정확한 대상이 모두 있어야 한다. 운영 승인 boolean만으로 drain이 자동 증명된다고 표시하지 않는다.
- install: 단일 실행자 전제와 재실행 규칙을 명시한다. 대상 컬렉션이 비어 있거나 같은 revision의 동일 entries인 경우만 계속하고 다른 revision/내용은 중단한다. 부분 목록을 자동 삭제하지 않는다. 미완료 install은 운영 traffic 차단을 유지하며 SEALED로 간주하지 않는다.
- seal: existing schema의 필드를 유지한다. 외부 승인·drain/reader 증거가 있어야 최초 state 설치가 가능하며, 이미 같은 SEALED 값이면 읽기 검증만 한다. 다른 값의 자동 교체는 금지한다. entries/state에 TTL 없음과 `_id` index를 확인한다.
- 실패 출력: raw Mongo 예외/시험 ID/URI/키를 노출하지 않는다. timeout·unknown commit·파일변조·권한오류·필수값 누락은 고정 사유로 중지한다.
- 격리 테스트: 정상·재실행·중단후재개, artifact 변조/중복/초과/잘못된UTF, 다른DB/환경/hash, state 충돌, 기존 billing 자료/미완료guard, 원Session 무수정, 원문 비로깅, 새writer 혼입 감지. 실제 운영 데이터는 fixture로 쓰지 않는다.

## 7. 근거·이번 작업 범위

- [P6 inspector/manifest 규격](LC_BILLING_PAID_EXAM_P6_PREPARATION.md)
- [현재 runtime·reader·writer 구현과 한계](LC_BILLING_LEGACY_RUNTIME_IMPLEMENTATION.md)
- [120일 보존·purge 제외](LC_BILLING_PAID_EXAM_P6_1_RETENTION.md)
- 코드: `LegacyExamManifest`, `PaidExamCutoverInspector`, `LegacyExamRuntimeConfiguration`, `LegacyExamWriterGuard`, `PaidExamActivationGate`.

이번에는 문서만 작성하고 계획/현황/WORKLOG를 동기화했다. 제품 코드·API·fixture·DB·AWS·배포·Jira·commit/push 변경 없음. 기존 dirty와 사용자 파일을 보존했다. 코드 변경이 없어 Gradle은 재실행하지 않았으며 링크/필드 대조·diff 검사만 수행했다. 과거 테스트 결과를 이번 실행 결과로 재표기하지 않는다.
