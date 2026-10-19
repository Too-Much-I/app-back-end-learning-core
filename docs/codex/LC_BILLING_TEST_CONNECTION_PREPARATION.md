# LC test Billing 연결 준비 — 2026-10-16

관련 TMI-116/128/217/125/220. 연결 준비이며 기능 활성화·공동 E2E 완료가 아니다.

## LC test 필수 조합 ON·TLS smoke — 2026-10-17

최종 ECS 배포 task41 COMPLETED·실행1/보류0/실패0 확인. 새8080/8444 대상 healthy, 구task 제거 완료. 증거 `/private/tmp/lc-test-task41-activation-20261017.png`.

- 사용자 「해줘」로 test 활성화/검증 진행. task40을 복제한 task41에서 paid/test-activation-approved/saga/workload sender/access receiver를 true로 변경하고 봉인 summary의 environment/database/revision/SHA256/count를 명시했다. 기존 user-merged writer=true 유지. 등록 후 environment 이외 전체 등록 가능 설정 동일 비교 통과: 이미지·Secret·command·roles·ports 보존, 서비스 네트워크/target 변경 없음.
- Java 앱 시작 및 Tomcat8080(http)/8444(https) 기동 확인. 이는 이번 필수 ON 조합의 startup 검증 성공이며 전체 업무 E2E는 아니다.
- 기존 test 사설 네트워크에서 Secret/task role/port 없는 공식 Python 일회성 태스크로 HTTPS 인증서 검증·redirect 금지·timeout12초·원문 응답 비출력 smoke 수행. 첫 실행은 rolling 중 LC access502, 재실행은 구task 제거 후 health200/LC access401 body0 no-store/Billing status401 body0 no-store. 공인 TLS와 ALB→LC TLS 전달/무인증 차단을 확인했으며 ALB의 backend 인증서 신뢰 검증이나 정상 JWT 인가 성공을 증명하지 않는다.
- 두 smoke 태스크 exit0/STOPPED, 임시 정의 INACTIVE. 첫502를 통과 처리하지 않고 재검증 결과와 구분한다. 로컬 curl은 DNS 실패해 검증 근거에서 제외했다.
- phone/reconciliation/AttemptGroup writer·publisher/삭제sender는 OFF 유지. Billing task4의 LC workload 수신=true지만 paid/phone/event/deletion 업무 준비는 별도다. Billing 인계 기록상 refund processing/publisher OFF·DB ALL_BLOCKED이며 이 턴에서 Billing DB를 직접 재조회하지 않았다. reconciliation 경보 max-age/max-attempts도 별도 운영값 확인이 필요하다.
- 실제 시험/예약/환불/삭제·정상서명 요청은 보내지 않았다. Billing/Identity·운영·IAM/Secret·DB·제품코드·commit/push 변경 없음. 후속은 Billing 업무 활성화 협의, 정상 JWT·same-key·환불/삭제/복구 공동 검증이다. LC만 ON한 것으로 전체 결제 연동 완료를 선언하지 않는다.

## 복구/outbox 설치·빈 manifest 봉인 완료 — 2026-10-17 01:21 KST

1. 사용자 설치/봉인 요청 및 별도 outbox TTL 승인으로 LC test에만 복구 index6개, outbox index3개를 설치했다. 충돌 시 중단하며 drop/recreate나 업무 문서 수정은 하지 않았다.
2. `legacy-v1` SEALED를 majority Transaction으로 저장했다. revision `lc-test-legacy-20261017-r1`, count0, SHA256 `66508198c1deae88e5b39b6d9ea88d01c70b5250a074d8a671d821933b274070`, sealedAt `2026-10-16T16:19:41.187Z`다. 기존 Session source backfill은 없다.
3. 별도 읽기 전용 태스크가 `2026-10-16T16:21:00.753Z`에 새 snapshot으로 Session/operation/manifest entry0·rollout state1 및 같은 봉인값을 확인했다. 새9개 index의 key순서/unique/partial/TTL/sparse/hidden/collation 모두 exact 일치했다. manifest/state는 `_id_`만 있고 TTL이 없다.
4. 기존 task40·서비스 실행1/보류0·네트워크/target 연결은 동일하다. paid/saga/reconciliation/writer/publisher OFF 확인. 임시 설치/재조회 태스크 모두 exit0/STOPPED, task definition 두 개는 INACTIVE 처리했으며 감사 로그는 보존했다.
5. **기능 활성화 완료가 아니다. 봉인으로 기존 생성 경로는409로 차단되며 paid는 여전히 OFF다.** 실8444 TLS·공동 E2E·Java startup 전체 검증 및 별도 완료조회 성능index 준비는 남아 있다.

### 봉인 근거와 한계

- writer drain 근거: 사용자 승인 test 무생성 운영 조건, 단일 task40의 확인된 배포 이미지, saga/reconciliation/writer/publisher OFF, 설치 전/봉인 Transaction/설치 후의 시험·operation0 확인. 서비스 전체 중지나 분산 writer lock을 실행했다는 뜻은 아니다. 준비 중 생성/재배포하지 않는 조건을 사용했다.
- reader coverage 근거: 배포 이미지 `sha256:5abc96b4aa7f0b53591fb2ae6485520f217d78256da6acdc533cf52e31a293b0`의 PR47 legacy reader/writer guard 포함 확인, 이번 `LegacyExamRuntimeTest`7/Configuration3/Manifest4/WriterGuard3 총17 통과, 실제 legacy 대상0. 실서버에서 Java `verifySealed`를 호출해 통과한 것으로 보고하지 않는다.
- `prepare-paid-test-mongo.js`는 exact test 대상·명시 승인/attestation·빈 시험/operation·index 옵션을 검사하고 새 보조 collection5개와 custom index9개 및 봉인 state1개만 만든다. outbox TTL은 expiresAt이 있는 문서를 자동 영구 삭제할 수 있으며 이번에는 outbox가 비어 있었다. 미완료 데이터에 expiry를 부여하지 않았다.
- Node 검사기 테스트27개 통과(기존 읽기13+설치14). 정상 설치/동일 봉인 replay, 비어 있지 않음/충돌/승인 누락 무쓰기 검증 포함. 제품 코드·공개/AI/S3/Redis 계약·운영/Billing/Identity·IAM/Secret·기능 설정·commit/push 변경 없음.
- 아래 읽기 점검 및 이전 준비 항목은 **각 당시의 이력**이다. 미설치/미봉인 표기는 이번 결과로 대체한다.

## 일회성 ECS 읽기 점검 성공 — 2026-10-17 01:07 KST

1. 브라우저 재연결 후 사용자 승인 범위의 Fargate 점검 1개가 exit0/STOPPED로 종료됐다. LC test task40/네트워크/로드밸런서 연결은 실행 전후 동일, 서비스 실행1이다.
2. 동일 Mongo snapshot에서 시험0/활성시험0/Billing연결시험0/생성작업0/미완료작업0/manifest entry0/rollout state0을 확인했다. 관측 완료 시각은 `2026-10-16T16:07:32.162Z`다.
3. 아래 필수 index15개의 key 순서·unique·partial·TTL 및 sparse/hidden/collation을 조회값과 소스 기준으로 비교해 모두 일치했다. Spring Java validator를 실제 서버에서 실행한 결과는 아니다.
4. 복구 index6개, outbox3개+publisher state 기본 index, manifest/state 컬렉션은 미설치다. 완료 조회 성능용 index도 없다. **전체 활성화 준비 완료가 아니다.**
5. DB 쓰기·새 index/TTL 설치·기능 ON은 하지 않았다. 기존 시험 목록의 hash 후보만 산출했으며 writer drain attestation·DB 봉인은 하지 않았다.

### 동일 snapshot의 빈 legacy 목록 후보

- environment `test`, database `to-teacher-learning-core-test`
- revision `lc-test-legacy-20261017-r1`, count `0`
- SHA-256 `66508198c1deae88e5b39b6d9ea88d01c70b5250a074d8a671d821933b274070`
- `LegacyExamManifest`의 UTF-8 length framing/정렬 규칙으로 계산했다. 별도 시점의 index metadata를 snapshot과 혼동하지 않는다.
- 서버 전체 정지가 필요하다는 뜻은 아니다. 기존 승인대로 test 생성 요청을 자제하고 설치 직전 진행 writer/목록을 다시 확인한 뒤 reader coverage 근거와 함께 봉인해야 한다. 현재 빈 목록 관측을 미래 신규 시험의 legacy 편입 허가로 사용하지 않는다.

### 정확히 일치한 index

| 범위 | 확인 개수 | 이름 |
| --- | ---: | --- |
| saga operation | 4 | uniq_exam_creation_user_operation, uniq_exam_creation_active_user, idx_exam_creation_state_updated, ttl_exam_creation_purge |
| saga Session | 3 | uniq_exam_sessions_creation_operation, uniq_exam_sessions_billing_reservation, idx_exam_sessions_attempt_group_created |
| 시험 배정 | 2 | uniq_exam_sessions_active_user, uniq_mock_exams_mock_exam_id |
| ownership | 4 | user_ownership_guards._id_, user_merged_inbox_events._id_, idx_exam_results_user, idx_exam_summaries_user |
| revoke | 2 | uq_paid_revoke_group, uq_paid_revoke_event |

partial은 각각 activeGuard=true, active=true, creationOperationId/billingReservationId의 BSON string, purgeAt의 BSON date로 정확히 일치한다. operation purge TTL은0이며 다른 위 index에는 TTL이 없다. 모든 위 index의 sparse/hidden=false, collation 없음 확인. `_id_`는 Mongo intrinsic unique이며 listIndexes의 unique 생략을 비고유로 해석하지 않는다. revoke collection 전체 TTL 없음도 확인했다.

기준 소스: `BillingSagaIndexValidator`, `ExamAssignmentIndexValidator`, `UserMergedIndexValidator`, `AccessRevocationStore`. `idx_exam_sessions_user_completed_mock_exam`은 누락이며 현재 validator의 warning 성능 index다.

### 남은 설치/검증

- operation의 `reservation_recovery_early_due`, `reservation_recovery_early_pending`, `reservation_recovery_due`, `reservation_recovery_lease`, `reservation_recovery_alert` 및 control의 `reservation_recovery_alert` 없음. control 컬렉션도 없다.
- `attempt_group_event_outbox`, `attempt_group_publisher_state` 없음. writer OR publisher 기동 전에 필수 구조 확인이 필요하다. outbox TTL을 이번 읽기 점검 승인으로 설치하지 않는다.
- `exam_legacy_manifest_entries`, `exam_paid_rollout_state` 없음. 빈 목록도 명시적 state/기본 index 설치와 근거 있는 봉인이 필요하다.
- 실제8444 TLS/서명 HTTP 및 양측 업무 연동은 별도 검증이다.

점검은 별도 `lc-test-readonly-inventory-20261017:1`에서 공식 mongo:8.0의 mongosh만 최대150초 실행했다. 기존 실행 역할로 Mongo Secret 필드 하나만 주입하고 앱/다른 Secret/task role/port mapping은 추가하지 않았다. URI와 credential은 출력·파일 저장하지 않았고 DB 수정 명령은 없다. 종료 후 임시 task definition은 비활성화하며 감사용 CloudWatch 보고서는 유지한다. 기존 다른 Codex terminal/파일은 미사용이다.

로컬 점검 스크립트에 index 상세 옵션·동일 snapshot manifest 후보 계산을 추가했다. Node 테스트13개/문법 검사 통과. 제품 코드/API 변경이 없어 Gradle은 재실행하지 않았다.

### 후속 실제 상태 확인 — 2026-10-17

ECS API에서 task40 COMPLETED/running1/pending0 및8080·8444 target healthy를 확인했다. 구8080 target은 draining이다. health는HTTP8080 기준이므로8444 TLS handshake/서명 수신 검증 완료는 아니다. 기능은OFF다.

사용자 승인으로 전용 CloudShell terminal을 새로 만들었고 기존 타 작업 terminal은 사용하지 않았다. LC test Secret 값은 출력하지 않고 연결 subprocess 환경에서만 사용했다. 정확한 test host/DB에 제한시간을 둔 ping은 MongoServerSelectionError로 실패했다. 네트워크 원인은 아직 확정하지 않았으며 Atlas 접근 확대는 하지 않았다. 기존 test 네트워크에서 일회성 읽기 전용 ECS 점검 태스크 실행 승인을 요청했다. 목록 snapshot/봉인 및 전체 index exact 검증은 미완료다.

## 테스트 TLS 경로 적용 — 2026-10-17

- 사용자 적용 직전 승인 후 LC test SG `sg-0245da61307f3e190`에 기존 ALB SG `sg-0e5462fa65a22ded8` 소스 TCP8444 한 개 추가: `sgr-07879a815a49c1b84`. 기존8080 inbound 및 outbound3개 보존.
- 신규 target group `lc-test-access-events-8444` (`9c865cbc14dda4c7`), IP/IPv4/HTTPS8444/HTTP1, 기존 VPC. health는 기존 HTTP8080 `/actuator/health`/200, 30초 간격·5초 timeout·healthy5/unhealthy2. 이는 앱 기동 health이며8444 TLS 수신 성공 증명이 아니다.
- 공유 ALB HTTPS443에 priority38(host api-test + exact POST `/internal/v1/entitlements/access/events` + source `13.124.57.130/32` → 신규 group),39(host api-test + exact 동일path → body없는404) 생성. 기존17개 규칙/priority40·staging/prod/Billing 대상 보존 확인.
- task40: task39 복제 후 portMapping TCP8444 및 `BILLING_ACCESS_EVENTS_PORT=8444`만 추가. 기존env68·Secret7·FCM/TLS helper command·이미지/roles/flags 유지, 새env69. 수신/paid 등 OFF. 서비스 기존8080 target 보존하고8444 target 추가 배포 요청. 최초 제출은 리스너 미지정 UI 검증으로 미실행, HTTPS443 지정 후 실제 배포 요청됨. 최종 안정화는 후속 확인.
- 로컬 외부 읽기 확인: 기존 health200/TLS verify0, 무인증 환불 POST404/body0bytes/TLS verify0. 이는 NAT 밖 차단·공인 TLS 확인이며 Billing 서명/ALB→8444 handshake·업무E2E 검증이 아니다. ALB 자체 빈404에 app의 no-store 헤더 동작까지 있다고 주장하지 않는다.
- Atlas 로그인 회복. 현재 Session0/operation0 무필터 Find 확인, mock ID unique 및 결과/요약 userId 인덱스 이름·key/Ready 확인, owner/inbox 각 index1개 확인. 기존 active-user partial 조건 등 정확한 전체 옵션은 미확인. `$indexStats` 읽기 집계에서도 spec 필드가 반환되지 않아 성공으로 취급하지 않았다. snapshot 목록/reader 증거 봉인·잔여 복구/outbox 준비는 미완료.
- 별도 새 CloudShell 세션 사용을 요청했다. 기존 다른 Codex의 CloudShell을 사용하지 않았고 IAM/DB 권한 확대·운영/Billing 변경 없음. snapshot 실행 수단 확보 전 boolean만 넣어 봉인하지 않는다.

## 목록·잔여 index·TLS 후속 확인 — 2026-10-16

- Atlas가 로그인 화면으로 전환돼 사용자에게 재로그인을 요청했다. 이번에는 DB를 재조회하지 못했으며 지난 시험0건을 새 snapshot/확정 목록으로 재사용하지 않았다. 목록 봉인 및 잔여 index 검증은 미완료다.
- AWS 23:57 KST 화면 기준 LC test task39/실행1·보류0/배포성공/ALB 정상1·비정상0. 현재 서비스 연결은8080 하나다.
- LC test SG `sg-0245da61307f3e190`의 inbound는 기존 ALB SG `sg-0e5462fa65a22ded8`에서 오는 TCP8080 하나. VPC `vpc-04068339482839ff1`. 공유 ALB HTTPS443 리스너는17규칙이며 api-test 전체 전달은 priority40, 기존 staging/prod/Billing 규칙도 존재한다.
- 적용 직전 승인 요청안: 전용 HTTPS8444 target group 및 LC test 서비스 연결, ALB SG→LC SG TCP8444 한정 허용. priority40 앞에 api-test 호스트+exact POST `/internal/v1/entitlements/access/events`+test NAT `13.124.57.130/32` 전달과 동일 경로의 나머지 요청 차단을 추가한다. 기존 규칙/8080 보존, 공개 인터넷 전체8444 개방 없음. 신규 port가 실제 뜨는 수신 ON은 DB gate 이후다. 현재 미적용.
- 동일 NAT workload를 네트워크만으로 Billing이라고 인증하지 않는다. 전용 JWT 신뢰 분리가 필요하며 ALB→target TLS는 암호화와 대상 인증서 검증을 구분한다. 현재 TLS handshake/signed HTTP 성공 증거 없음.
- Mongo snapshot/reader 검증 근거 없이 writersDrained/readerCoverageVerified를 true로 봉인하지 않는다. 서버 전체 중지를 필수로 추가하지 않는다. 공유 CloudShell 미사용, 비밀정보 조회/기록 없음.

## Atlas test DB 구조 추가 — 2026-10-16

- 대상은 위에서 확인한 LC test DB 하나다. 새 빈 `exam_creation_operations`, `billing_access_revocations` 컬렉션을 생성했다. 기존 컬렉션/문서/index 삭제·교체 없음.
- operation 추가4: `uniq_exam_creation_user_operation` (userId/operationId unique), `uniq_exam_creation_active_user` (userId/activeGuard unique, partial activeGuard=true), `idx_exam_creation_state_updated` (state/updatedAt), `ttl_exam_creation_purge` (purgeAt, expireAfterSeconds=0, partial BSON date). 모두 ASC이며 Atlas Ready 확인.
- TTL은 사용자 「테스트 생성 작업 TTL 추가 승인」 후 적용했다. 명시 purgeAt이 있는 작업만 자동 삭제하며 다른 컬렉션에 TTL을 추가하지 않았다. 미완료 작업의 무TTL 계약은 유지한다.
- Session 추가3: `uniq_exam_sessions_creation_operation` (userId/creationOperationId unique, partial string), `uniq_exam_sessions_billing_reservation` (billingReservationId unique, partial string), `idx_exam_sessions_attempt_group_created` (attemptGroupId/createdAt). 기존4개 유지, 추가3개 Ready 확인.
- revoke 추가2: `uq_paid_revoke_group` (environment/producer/consumerScopeId/attemptGroupId), `uq_paid_revoke_event` (environment/producer/consumerScopeId/eventId). 모두 ASC unique, TTL/partial/sparse/custom collation 없음. 생성 후 key 순서·unique·Ready 확인.
- 검증은 UI 입력/목록/Ready 대조다. 실제 Java 전체 index 검증·signed HTTP·업무 E2E 통과를 의미하지 않는다. 기존 active-user partial 원문/owner/mock 옵션, reconciliation/outbox 및 manifest 봉인 준비는 후속이다.
- test의 기존 승인대로 생성 요청을 보내지 않는 동안 진행 writer를 확인한다. 서버 전체 중지를 필수로 추가하지 않는다. 별개 GUI 조회를 동일 snapshot으로 간주하거나 readerCoverageVerified/writersDrained를 근거 없이 기록하지 않는다.
- 서버 설정/네트워크/기능 flags/재배포·Billing/Identity 변경 없음. 결과 캡처: `/private/tmp/lc-test-revoke-indexes-20261016.jpg`.

## Atlas 콘솔 읽기 점검 — 2026-10-16

- 기존 로그인된 `tosunsaeng-test` 프로젝트/Cluster0의 `to-teacher-learning-core-test`만 별도 탭에서 조회했다. 다른 Codex CloudShell/Billing 작업 탭은 사용하지 않았다.
- `exam_sessions` 문서 탭에서 무필터 Find와 `This collection has no data`, Documents0을 확인했다. 목록 통계만으로 0을 판단하지 않았다.
- 컬렉션 검색에서 `exam_creation_operations`는 No results found. `exam_` 검색에는 exam_results/exam_sessions/exam_submission_receipts/exam_summaries만 표시됐으며 legacy entries/rollout state는 없었다. billing_access 검색은 결과없음, attempt_group 검색은 Billing DB의 attempt_groups만 존재하고 LC 해당 컬렉션은 없었다. 이는 콘솔의 현재 조회 근거이며 여러 조회를 하나의 snapshot으로 간주하지 않는다.
- exam_sessions 인덱스는 `_id_`, `uniq_exam_sessions_active_user`, `idx_deletion_owner_inventory`, `notification_submission_date` 네 개다. active user는 userId ASC/unique/partial/Ready 확인; partial 조건 원문까지 검증한 것은 아니다. 필수 saga 인덱스 `uniq_exam_sessions_creation_operation`, `uniq_exam_sessions_billing_reservation`, `idx_exam_sessions_attempt_group_created`는 없었다.
- owner guard/inbox 컬렉션은 존재한다. 개별 데이터나 전체 index 옵션 검증은 미실행이다. 학습·챌린지 등 다른 기능 데이터가 있어 DB 전체 초기화/삭제 대상이 아니다.
- 결론: 과거 시험 대량이관보다는 현재 비어 있는 시험 영역의 필수 collection/index·빈 manifest 준비가 필요하다. 최종 전환 직전 새 시험/미완료 요청 부재를 다시 확인하고 일관된 시점의 증거 및 writer 중단 조건을 충족해야 한다. 이번에 목록 봉인·DB 생성/변경·TLS 경로·기능 활성화는 하지 않았다.

## 관련 기능 일괄 활성화 준비 — CLI 인증 대기

- 사용자 후속 요청으로 test 준비·활성화를 묶어 진행하되 실제 네트워크 접근 확대는 적용 직전 별도 확인한다. 작업 중 수동 시험 생성/삭제 요청 중단을 안내했다. 이 안내 자체를 writer drain 완료 증거로 보지 않는다.
- task39에서 active profile/deployment=test, DB=`to-teacher-learning-core-test` 확인. 해당 Mongo Secret의 호스트는 `cluster0.lkpybbj.mongodb.net`; 로컬 `.env.docker.local`의 다른 클러스터/DB는 사용하지 않는다. Secret은 호스트 대조 외 원문 출력/저장하지 않았다.
- 기존 삭제 command/worker/read·writer fence/continuation/aggregate는 이미 ON이다. paid/saga/JWT sender/access receiver/phone/reconciliation/AttemptGroup writer·publisher는 OFF다. 기존 기능 상태를 무조건 모두 OFF로 가정하지 않는다.
- Billing runbook상 LC 일반 예약 경로는 준비됐으나 phone/AttemptGroup 기능과 삭제 ingress·역방향 발신 준비는 별도로 남는다. LC만 ON한 것으로 전체 연동 완료를 주장하지 않는다.
- 로컬 AWS STS 조회는 `NoCredentials`로 실패했다. 사용자에게 `aws login`으로 계정889384901776 연결을 요청했다. 다른 Codex의 CloudShell/운영 DB 접속정보를 가져다 쓰지 않는다.
- `node scripts/deployment/inspect-paid-test.cjs`는 계정/Secret/host/database 고정 읽기 전용 도구다. URI는 child 환경으로만 전달하며 argv/파일/로그에 넣지 않는다. 건수는 하나의 read-only snapshot에서, collection/index 메타데이터는 별도 조회한다. 목록 설치·index 생성·purge·활성화 기능은 없다. 현재 index 이름/키/옵션 존재를 보고할 뿐 기동 validator 전체 통과 증거가 아니다.
- 새13개 포함 배포 Node43개/JS syntax/diff 통과. snapshot 실행 검사는 모형 테스트이며 실제 Mongo 실행은 인증 대기다. manifest export/install 및 실제 연결·공동E2E는 미실행이다.

## TLS Secret 참조·시작 명령 연결

최종 결과: task39 배포 `ohJnK1tjqf3XG9_QARQwh`는 2026-10-16 23:06 KST 완료(4분45초). 신규 태스크 애플리케이션 Started·기존8080 기동, 실행1/보류0·ALB 정상1/비정상0 및 외부 health HTTP200 확인. 표시한 시작 로그에서 오류 표식은 없었다. ECS Secret 참조를 포함한 기동 성공이며 수신OFF라 PKCS12 파일 생성/유효성·실TLS/환불 E2E 성공을 의미하지 않는다. 아래 진행 중 기록은 완료 전 이력이다.

- 후속 사용자 요청으로 실제 서비스의 task38/이미지 digest를 확인하고 task39를 생성했다. 환경67개/Secret5개를 보존해 TLS Secret의 PKCS12_BASE64·KEYSTORE_PASSWORD 필드 참조2개와 alias1개만 추가했다. 최종 환경68개/Secret7개다.
- task38에는 FCM 인증파일을 만드는 ECS entryPoint/command override가 있어 Dockerfile ENTRYPOINT가 실행되지 않는 것을 확인했다. 기존 FCM 파일 생성·권한·환경 제거는 그대로 유지하고 마지막 `exec java -jar /app/app.jar`만 `exec sh /app/access-events-entrypoint.sh java -jar /app/app.jar`로 연결했다. 이미지·roles·ports·기능 flags는 불변이다.
- JSON 편집 결과를 메모리에서 원본과 대조해 기존 설정 보존 및 의도한 내용 일치를 확인했다. 비밀값은 출력/문서에 복제하지 않았다.
- test 서비스에 task39 배포 요청, ID `ohJnK1tjqf3XG9_QARQwh`. 이 기록 시 교체 진행 중이며 최종 기동/ALB 확인은 후속이다. 수신 OFF에서는 PKCS12 파일 생성/실TLS 검증을 수행하지 않는다. ALB/SG·DB/index·Billing/Identity·활성화 변경 없음.

## TLS 주입 코드 test 배포 완료

- 사용자 전달 승인 후 fd55208 커밋·PR #47 전체 CI38909980587 성공·develop 0d2aeb9 머지 완료.
- 자동배포38910572703의 ECS 안정화와 `https://api-test.to-teacher.com/actuator/health` 검사가 2026-10-16 22:56 KST 성공했다. 배포는 기존 task37을 읽어 이미지/test 프로필만 렌더링했다.
- 배포 이미지 digest: `sha256:5abc96b4aa7f0b53591fb2ae6485520f217d78256da6acdc533cf52e31a293b0`.
- 아래 코드 미전달/배포 대기 기록은 이 결과로 대체한다. 기능 OFF와 기존 설정은 유지하며 TLS Secret 필드 ECS 참조·실제 TLS 수신/서명 HTTP·ALB/SG·DB/index·paid 활성화는 이번 배포에서 수행하지 않았다. 운영/Billing/Identity 수정 없음.

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
