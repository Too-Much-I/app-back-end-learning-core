# LC 유료 시험 P2 구현 — reserve/status 및 증거 저장

- 날짜: 2026-10-14
- 브랜치: `feat/billing-paid-exam-p2`, 시작점 develop `989c4ff`
- 관련: P1 TMI-218, TMI-116/TMI-128/TMI-122; PLAN-011/012/013. P2 전용 Jira는 이번에 생성하지 않았다.

## 1. 5줄 결론

1. 기존 workload JWT HTTP client에 `reservePaid`/`statusPaid`를 추가했다. 공개 생성 경로에는 연결하지 않는다.
2. 단일 mock와 세 필드 restartContext, source/transition/confirmRejection을 검증한다.
3. 원 요청 digest 및 검증한 전체 snapshot을 기존 operation/Session 안에 저장한다. 새 collection/TTL은 없다.
4. 정상 fixture9개를 실제 HTTP client로 검증하고 누락/변조/금지 조합·영속 rollback 테스트를 추가했다.
5. P2.1 내부 오류·phone 확장, P3 활성화/worker 복구 및 P4~P6는 후속이며 기존 paid 활성화 차단을 유지한다.

## 2. 반드시 읽어야 하는 내용

일반 신규/재시작의 P2 client·검증·저장 기반이다. 기존 HTTP saga의 `paidSelection` 차단, schema2에 대한 구 worker 제외와 `PaidExamActivationGate`는 그대로다. 현재 앱에서 유료 생성 경로가 작동한다는 뜻이 아니다. 새 메서드를 공개 요청이나 worker에서 호출하도록 연결하지 않았다.

- `PaidReservationProtocol.request(op)`는 P1의 고정 snapshot만 사용하며 catalog/현재 시험을 재조회하지 않는다. phone field와 혼합할 수 없는 typed Request이며 null restart는 발신 JSON에서 생략한다. 클라이언트가 raw JSON으로 요청 field를 정하는 API를 추가하지 않았다.
- 부분 restart/잘못된 UUID/원 Session 재사용·INCOMPLETE의 문제 변경은 발신 전 거절한다. 원 Session은 있지만 group가 없는 경우 legacy로 추론하지 않고 P6 exact manifest 경로 준비 전 거절한다.
- 신규 paid reserve/status는 source를 필수 검증한다. 기존 일반/phone 메서드는 기존 source 없는 응답도 읽어 reader-first 호환을 유지한다. 새 optional 응답 object의 명시 null·부분/unknown/duplicate/trailing/coercion은 거절한다.
- `NEW_AFTER_SUBMISSION/COMPLETION`의 REPLACEMENT는 거절한다. 일반 restart INITIAL은 새 group과 exact 원 대상 transition, REPLACEMENT는 원 group/문제·transition 없음이다.
- `confirmRejection`은 CANCELED+terminalAt+ENTITLEMENT_REVOKED만 허용한다. terminal snapshot이 성공/거절/일반취소 사이에서 바뀌거나 RESERVED로 돌아가는 것을 저장하지 않는다.

## 3. 저장 경계와 후속 연결

`PaidReservationEvidenceStore.prepareRequest`는 owner Transaction에서 요청 digest를 먼저 저장한다. 요청 필드 순서의 compact JSON에 대한 로컬 SHA-256으로, Billing의 digest 값을 전송/비교하는 신규 wire field가 아니다. 이후 원격 HTTP는 이 Transaction 밖에서 호출해야 한다. `observe`는 전체 reserve/status snapshot만 받아 같은 owner Transaction에서 operation과 이미 존재하는 exact Session의 evidence를 함께 저장한다. Session이 없으면 생성하지 않는다.

P0 후보 필드는 이번 구현에서 다음 내부 경로로 매핑했다.

| 의미 | 구현 저장 경로 |
| --- | --- |
| 시작 사유/필수집합 | 기존 P1 `paidSelection` |
| 원 group/Session/mock | 기존 `expectedAttemptGroupId`/`replacementSourceSessionId`/`expectedMockExamId` |
| 고정 요청 digest | operation `paidRequestDigest` |
| source/transition/거절 및 예약 귀속/상태 증거 | operation 및 Session의 `paidReservation.snapshot` |
| 신규 schema 구분 | P1 `paidSelection` 및 recovery schema2, 접수 `submissionEvidenceVersion=1` |

embedded snapshot은 DTO 원문 bytes/Token이 아닌 검증된 필드만 보관한다. source·transition·reservation/group/Session/mock·expiresAt은 불변 대조한다. abbreviated confirm/cancel은 이 전체 snapshot 저장 API에 입력하면 거절되며 기존 evidence를 null로 덮어쓰지 않는다. 기존 confirm/cancel 성공 DTO는 그대로다. 이후 P3는 별도 confirm 성공 증거와 전체 status evidence를 구분하여 상태를 전이해야 한다.

이 저장 API는 operation 상태/기존 예약 coordination 필드를 `RESERVED`로 전이하거나 activeGuard를 해제하지 않는다. 원자적 상태 전이·lease·dispatch marker·unknown commit 복구는 P3가 기존 실행 경계와 묶어야 한다. 기존 성공 응답을 그대로 재현해야 하는 공개 API도 여기서 호출하지 않는다.

## 4. 위험·미확인 및 배포 gate

- P2.1의 strict 오류 DTO/신규 MEMBER·owner·J3 분류, phone paid INITIAL 예외는 미구현이다. 기존 오류 mapper를 신규 흐름 활성화 근거로 삼지 않는다.
- 기존 generic confirm/cancel DTO의 source 부재와 authoritative status에서의 source/transition 부재는 다른 의미다. 후자는 계약 오류다.
- legacy manifest·삭제 continuation·최종 purge는 이번에 구현하지 않았다. missing group/source만으로 신규 일반 INITIAL을 허용하지 않는다.
- owner/삭제 fence/Session CAS rollback은 로컬 격리 Mongo로 검증하며 실제 Billing/Identity/앱/운영 DB와 E2E는 미실행이다. 공동 상태 시나리오28개 전체 완료가 아니다.
- 설정/실DB/index/운영·테스트 배포/활성화/commit/push는 수행하지 않았다. P2 전용 Jira 등록·P2.1/P3 범위 확인이 다음 단계다.

## 5. 변경 파일과 외부 계약

- 신규: `PaidReservationProtocol`, `PaidReservationEvidence`, `PaidReservationEvidenceStore`, `PaidReservationClientTest`, `PaidReservationProtocolTest`.
- 수정: `BillingReservationClient`, `JwtBillingReservationClient`, `ReservationSnapshotValidator`, `ExamCreationOperation`, `ExamSession`, `PaidExamP1MongoTest`(P2 영속화4개 추가), AGENTS/구현계획/P0/ADR/작업기록.
- 공개 URL/Method/Request/Response/BaseResponse, AI `user_id=examId`/Callback JSON, retryCount, S3/Redis key와 workload A 인증은 변경하지 않았다. 새 HTTP 호출도 동일 transport·JWT 발급·timeout·redirect 금지/16KiB 응답 상한을 사용한다.
- 기존 미커밋 머지 결과 WORKLOG/CURRENT_STATE 및 DS_Store는 보존했다. Billing/Identity 저장소 변경이나 예상 밖 제품 변경은 없다.

## 6. 검증

최종 실행: `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew clean test mongoIntegrationTest bootJar` 성공. 단위1,102개 실패/오류0·기존 LearningImportedContentTest 조건부1 skip, Mongo176개 실패/오류/skip0, bootJar 성공이다. 신규 P2 단위17개와 Mongo4개를 포함한다. Docker29 API 호환 옵션은 테스트 실행에만 지정했으며 전역 설정/의존성은 변경하지 않았다. 원 fixture는 재생성하지 않았고 A3+R2 13+simplified9개 checksum이 모두 일치한다. `git diff --check` 통과.

- HTTP: R2 reserve6/status3 원 fixture를 실제 loopback HTTP client에 연결했다. exact 요청 body·header, status header 없음, JWT 재발급/동일 body, source 부재 legacy 호환, malformed/partial/unknown/duplicate/trailing/coercion·원대상 변조·잘못된 kind 및 거절 조합을 검사한다.
- Protocol: 고정 사유·단일mock, digest 불변, 누락group legacy 오인 금지, 원 Session 재사용·phone 혼합 거절을 검사한다.
- Mongo: request digest와 예약 evidence 재조회, Session 부재 시 미생성/guard 유지, exact Session 영속, terminal 역행/짧은 confirm 거절, source 변조 및 다른 owner Session에서 전체 rollback을 검사한다.
