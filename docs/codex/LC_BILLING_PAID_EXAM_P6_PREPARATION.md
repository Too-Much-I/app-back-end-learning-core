# LC P6 — legacy manifest 읽기 전용 준비

- 날짜: 2026-10-14 / 브랜치: `feat/billing-paid-exam-p6`
- 관련: TMI-116/TMI-128/TMI-122/TMI-125, TMI-193/TMI-136·TMI-217/TMI-220, 선행 TMI-218.

## 1. 5줄 결론

1. P5/P5.1 PR #41을 CI 통과 후 develop `e894408bbe3a2d53547cb0aa761b5870fa9f55ad`에 머지하고 새 P6 브랜치를 만들었다.
2. P6는 서버 exact 시험 목록의 context-bound SHA-256 검증과 읽기 전용 Mongo snapshot 사전 점검을 구현했다.
3. 목록 포함 여부는 기존 시험 분류일 뿐 이용 권한이 아니다. 상태 누락·source 누락만으로 진행을 허용하지 않는다.
4. 신규 source writer/공개 API 연결·목록 봉인/적용·기존자료 backfill·실DB/배포·활성화는 하지 않았다.
5. 실제 writer drain/reader coverage 입증, P6.1 보존·TMI-220 공동 의미 보완은 다음 gate다. 기존 rollout 차단을 유지한다.

## 2. 반드시 읽어야 하는 내용

### 변경 파일·동작

- `src/main/java/web/tosunsaeng/domain/exams/rollout/LegacyExamManifest.java`: 중복/형식/크기 검증, 정렬·환경/DB/revision 결속 fingerprint, immutable exact membership. `toString`에는 요약 metadata만 포함한다.
- `PaidExamCutoverInspector.java`: 명시적으로 구성·호출하는 준비 유틸리티. Spring 자동 bean/공개 route/CLI 없음. candidate는 미해결 생성 operation과 Billing 연결 자료를 거절하고, 실제 exam_sessions snapshot에서 hash/count만 반환한다. Session/상태/권리/원장을 변경하지 않는다.
- `verifySealed`는 별도 승인된 서버 summary와 봉인 state/entries 전체를 한 Mongo snapshot에서 대조한다. readerCoverageVerified·writersDrained·sealedAt 및 정확한 환경/DB/revision/hash/count가 필요하다. 삭제된 원 Session을 재생성하지 않으며 기존 ID에 유료 metadata가 생기면 거절한다.
- manifest/state 컬렉션의 `_id` index·TTL 부재를 읽기 확인한다. 없는 컬렉션/index를 설치하거나 충돌 index를 삭제하지 않는다. 기존 saga/reconciliation/outbox/revoke validator를 대체하지 않는다.
- `LegacyExamManifestTest`, `PaidExamCutoverMongoTest`: fingerprint 독립 고정값, malformed/중복·초과, 무수정 snapshot, 상태 누락 legacy, drain/미완료, 목록 변조·누락, 새 시험 혼입·삭제 비부활, 잘못된 DB/revision, TTL/컬렉션 누락 검증.
- 계획/ADR/CURRENT_STATE/WORKLOG에 현재 구현 범위와 미연결 gate 기록.

### 불변 계약

공개 API URL/Method/body/DTO/BaseResponse, AI user_id=examId/Callback JSON, retryCount·S3·Redis, Billing wire·공동 fixture bytes를 유지한다. 이전 source 없는 기록을 EXAM_PASS로 채우지 않고 소비/무료권 복원 증거로 바꾸지 않는다. 현재 runtime 선택/접수/권한 reader에는 아직 연결하지 않았다.

## 3. 다음 작업 전 확인·결정 사항

제품 정책 재선택은 없다. 실제 목록의 생성·봉인·설치 도구와 writer 전환 연결은 운영 drain 증거와 접근제한 artifact 전달 절차를 먼저 정해야 한다. 이번 유틸리티는 summary만 만들며 실제 ID artifact를 파일로 출력하거나 DB에 쓰지 않는다. 별도 readerCoverageVerified 플래그가 있다는 이유로 검증 자체가 수행됐다고 보고하지 않는다.

TMI-220은 이전 Session 기접수 작업과 revoke 정책의 공동 보완 대기다. 이번 단계는 이를 수락하거나 P5 accessRevoked 검사를 완화하지 않는다. P6.1의120일/hold/unknown·최종 purge도 이번 읽기 전용 목록 준비와 구분한다.

## 4. 위험과 배포 전 확인

- `writersDrained=true`는 호출자의 승인된 증거에 대한 확인값이다. 인스턴스를 정지하거나 분산 writer fence를 자동 확보하지 않는다. snapshot은 여러 collection 조회를 같은 시점으로 묶지만 이후 구writer 재개를 방지하지 않는다.
- hash만으로 승인자를 인증하지 않는다. `expected` summary는 클라이언트 요청이 아닌 별도 승인된 서버 artifact에서 받아야 한다. 같은 DB 안의 state/hash를 그대로 신뢰 입력으로 삼으면 안 된다.
- `containsLegacy=true`여도 owner/삭제/탈퇴/revoke/terminal·기접수 증거 검사를 별도로 적용해야 한다. manifest 밖 source 없는 자료는 legacy가 아니지만 이 유틸리티 자체가 유료 사용 권한을 부여하지도 않는다.
- 실제 production inventory·최신 건수·여러 writer 혼재·전체 index coverage·실제 certificate/ALB·V07 미검증. 과거1493/146건은 재사용하지 않았다.
- 목록은 최대100,000건으로 제한하고 초과/실패는 고정 오류로 거절한다. 대규모 streaming/최종 retention은 별도 필요성 확인 후 설계한다. 기존 오류 원문/문서ID/URI를 반환하거나 기록하지 않는다.

## 5. 전환 runbook — 실행하지 않은 순서

1. 실제 배포 inventory에서 모든 구writer·진행 요청/생성 guard를 drain하고 재개 방지 책임자·증거를 확인한다. drain 중단/새 writer 관측 시 이후 단계를 폐기하고 다시 inventory한다.
2. read-only candidate snapshot을 새로 만들고 승인된 revision/hash/count를 따로 보관한다. 실제 ID artifact는 접근제한 저장소에서만 전달하고 일반 로그/보고서에는 남기지 않는다.
3. 별도 승인된 설치 도구로 entries/state를 준비·검증·봉인한다. 이번에는 설치 도구나 apply 기능을 만들지 않았다. 원 Session 수정 없음.
4. 실제 조회/작성 경로별 owner/legacy/source/120일·삭제/revoke coverage와 기존 index validator를 검증한다. 증명 후에만 readerCoverageVerified를 확정한다.
5. 봉인 state와 별도 expected summary 대조 및 원 Session 불변을 확인한다. 아직 source 없는 새 writer가 목록에 편입되지 않는지 확인한다.
6. 별도 배포 승인·staging E2E·TMI-220/V07 등 잔여 gate 해소 후 runtime reader/source writer 연결·전환한다. 이번 rollout gate는 제거하지 않았다.
7. 장애 시 신규 생성 traffic을 먼저 drain하고 이미 생긴 paid operation/deny·복구 reader를 보존한다. flag OFF만으로 옛 생성 경로로 우회하거나 manifest에 새 시험을 추가하지 않는다.

## 6. 내부 artifact 규격·검증 근거

외부 공동 wire가 아닌 LC 내부 준비 규격 v1이다. 엔트리는 `{_id: examId, revision}`만 허용하며 `exam_legacy_manifest_entries`의 `_id`를 사용한다. state는 `exam_paid_rollout_state`, `_id=legacy-v1`, status=SEALED, environment/database/revision/sha256/count/sealedAt/writersDrained/readerCoverageVerified다. 추가 public 설정은 없다.

SHA256 입력은 다음 순서다. 각 문자열은 **UTF-8 byte length를4byte big-endian 정수로 기록한 뒤 bytes**를 붙인다: `lc-legacy-manifest-v1`, environment, database, revision. 다음에 건수를4byte big-endian으로 붙이고 examId를 Java String 자연순(UTF-16 code unit)으로 정렬해 같은 framing으로 붙인다. 중복/비정상 surrogate/control/blank/128자 초과 ID는 거절한다. 목록 순서 변경만으로 hash가 변하지 않으며 환경/DB/revision·대상 변경은 hash를 바꾼다. Node crypto로 독립 계산한 테스트 전용 고정값을 Java 테스트에서 확인한다. 공동 fixture를 재생성하지 않는다.

시험/DB/인증정보 실제값을 fixture로 사용하지 않는다. 테스트 중 Mongo 데이터 삽입/삭제는 Testcontainers의 격리 DB에만 수행한다. 전체 실행 결과는 CURRENT_STATE와 이번 WORKLOG 항목에 기록한다.

최종 검증: `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew clean test mongoIntegrationTest bootJar` 성공 및 마지막 출력 최소화 보완 후 `test mongoIntegrationTest bootJar` 재실행 성공. XML 단위1,252개 실패/오류0·기존1 skip, Mongo248개 실패/오류/skip0. 이번 P6 단위4/Mongo10개 추가. Node mongodb121개·기존 fixture SHA256SUMS34항목·diff check 통과. 실제 DB/수신활성화 테스트가 아닌 격리 검증이다.

## 7. 범위 점검

예상 밖 이번 제품 변경 없음. P5 CI finalize/revoke 경합 테스트 보완은 P5 전달 커밋에 포함했다. P6 전체 회귀에서 다른 P5 동시수신 테스트도 동일하게 일시503을 허용하지 않아 실패했으며, 이번 브랜치에서 같은 이벤트의 재전송 이후 단일fact·204/409 수렴을 검증하도록 보완했다. 제품 retry/권한 정책 변경이나 검증 제외는 없다. 기존 사용자 `.DS_Store` 파일을 보존했다. Billing/Identity 코드·Jira·실DB/index·기능설정 변경 없음. P6 변경은 별도 요청 전 커밋/푸시하지 않는다.
