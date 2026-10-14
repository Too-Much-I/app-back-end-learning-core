# LC 유료 시험 P5 — 역방향 revoke 구성요소 구현

> 후속 P5.1에서 ON bean 조립·전용 TLS connector와 기존 writer 방어를 구현했다. 아래는 P5 최초 완료 당시 기록이며 최신 상태는 [P5.1 보고](LC_BILLING_PAID_EXAM_P5_1_IMPLEMENTATION.md)를 따른다. 실제 활성화 차단은 유지한다.

- 날짜: 2026-10-14 / 브랜치: `feat/billing-paid-exam-p5`
- 관련: TMI-116/TMI-128/TMI-122, TMI-193/TMI-136·공동 TMI-217, 선행 TMI-218.
- 상태: 수신 verifier/codec/filter·단일 fact 저장·차단 projection·paid lifecycle 연결점 구현 및 격리 검증. **P5 전체 활성 연결 완료가 아니다.**

## 1. 5줄 결론

1. P4/P4.1 PR #40을 develop `f59cf284d7f8225e52190116ee13715d7ee06aed`에 머지했고 기존 테스트 배포/health도 성공했다.
2. 새 P5 브랜치에는 Billing 전용 JWT 검증, strict canonical event, ingress 오류 순서와 그룹별 단일 revoke 문서를 구현했다.
3. 같은 event/digest는204, event 충돌409를 group 충돌409보다 먼저 검사하며 보호 Session/null을 바꾸지 않는다.
4. fact 저장과 기존 비보호 Session의 accessRevoked projection은 같은 Transaction이다. 새 Session/계정 생성·소유권 이전·무료권/원장 변경은 없다.
5. 현재 Spring에서는 namespace OFF404만 연결한다. ON은 startup에서 거절하며 TLS connector·ON bean 조립·전체 기존 writer/legacy coverage 및 V07은 후속이다. P5 변경은 미커밋/미배포다.

## 2. 반드시 읽어야 하는 내용

### 구현된 부분

- `AccessWorkloadVerifier`: RS256/RSA2048+, exact issuer/sub/aud, locally deployed public JWK set, strict kid/key shape, 정수 NumericDate·소수초 clock·TTL/skew, 헤더·claim duplicate/trailing/UTF8 검증. 키 오류는 원문/cause 없는 고정 메시지다. 원격 fetch/다른서비스 키 fallback 없음.
- `AccessRevocationEvent`/`AccessEventJson`: raw16,384bytes, exact10필드, scalar Unicode/실제 UTC 날짜, canonical 제어문자 lowercase hex·필드 순서·SHA256. 413→400→422 순서. 기존 공동 fixture bytes 재생성 없음.
- `AccessEventFilter`: OFF/잘못된 localPort404 → query/Bearer/JWT401 → 검증된 wrong principal/method/path403 → decode/store. auth 오류 body없음/no-store, X-Forwarded-Port로 포트를 판단하지 않는다. 정상 sink 완료 후204.
- `AccessRevocationStore`: 환경/producer/scope별 group/event unique 두 개 사전 확인. TTL/partial/sparse/collation 불일치 거절. index 자동 생성/수정 없음. 트랜잭션 failure는 fresh primary majority로 재확인하고 unknown 결과를 성공으로 추정하지 않는다.
- 기존 owner guard를 수정하되 없는 계정 guard를 upsert하지 않는다. Sessions도 update-only. group 최초 fact의 eventId/digest/reason/occurredAt/보호Session/첫관측시각을 replay로 갱신하지 않는다.
- 비보호 Session의 `accessRevoked=true`와 version을 저장한다. `ExamAcceptedWorkPolicy`·신규 submit/upload 조건·paid 활성화와 terminal writer가 이 deny를 확인한다. flag OFF가 저장된 deny를 해제하지 않는다. 보호 대상에도 기존 삭제/탈퇴/owner/terminal 규칙이 우선한다.
- paid lifecycle는 optional store 연결점으로 pending insert/confirm dispatch/최종 activation에서 같은 group의 권한을 확인한다. 성공confirm을 cancel하지 않는다. 권위 CANCELED/거절 증거가 오면 deny가 있어도 안전 보상/guard 정리가 가능하다.

### 현재 연결되지 않은 부분

`AccessEventConfiguration`은 기존 Legacy/user/Identity/Challenge chain보다 분리된 access namespace를 기본OFF404로 처리한다. `BILLING_ACCESS_EVENTS_RECEIVER_ENABLED=true`는 고정 startup gate에서 실패한다. 실제 verifier/store ON bean 조립과 TLS connector를 아직 배포 가능한 형태로 등록하지 않았다. 격리 테스트는 실제 제품 filter/verifier/store를 직접 구성하며 실제 서비스 활성화를 의미하지 않는다.

공개 시험 API URL/Method/body/성공DTO/BaseResponse·AI user_id=examId/Callback·retryCount/S3/Redis·Billing 발신 계약은 불변이다. 신규 accessRevoked는 내부 persistence 필드이며 공개 DTO에 추가하지 않는다. 과거 결과 읽기와 새 사용 권한은 별개다.

## 3. 후속 결정·작업

제품 정책 재선택은 없다. 후속 P5 연결 작업은 지정 TLS connector/port/origin·키 배포와 회수 SLA, namespace inventory, 기존 legacy saga/직접 repository writer·merge/delete/withdrawal coverage를 확인한 뒤 ON wiring과 startup gate 전환을 구현해야 한다. V07 보존/삭제·개인정보 최종 purge는 공동 gate다. 새 Billing 삭제 sender/ACK(TMI-217)는 이번 revoke와 다른 계약이다.

## 4. 위험과 활성화 조건

- 현재 새 수신 ON은 불가능하다. 테스트 통과만으로 설정을 켜거나 startup guard를 제거하면 안 된다.
- 기존 owner와 다른 owner로 이전된 group의 보호예외/늦은 event 전체 처리는 V07 검증 전이다. 현재 코드는 과거 owner를 새 권한으로 alias하지 않는다. 원문 cleanup·ACK·legacy/탈퇴 purge도 완료로 주장하지 않는다.
- no TTL은 현재 미확정 보존자료를 삭제하지 않는 안전 경계이며 영구 개인정보 보존 정책 승인이 아니다. P6에서 실제 index/coverage inventory와 승인된 purge를 준비한다.
- TLS·ALB/proxy가 빈 query/경로/헤더 공백을 보존하는지 실환경 검증이 남는다. 현재 real HTTP 테스트는 loopback plain HTTP이며 실제 TLS 증거가 아니다.
- startup/전용 chain의 ON bean 연결 전, 미완료 operation의 revoke/confirm/cleanup 오류 분류·운영 격리와 기존 모든 writer drain을 확인한다. 단순 feature OFF만으로 남은 deny/fact/미완료 작업을 버리지 않는다.

## 5. 검증과 변경 파일

주요 파일은 `domain/exams/access/*`, `ExamSession`, `ExamAcceptedWorkPolicy`, `ExamServiceImpl`, `ExamGradingService`, `PaidExamLifecycleService`, 기본OFF application.yml이다. 계약/fixture 파일과 Billing/Identity 저장소는 수정하지 않았다.

- 제품 verifier에 원문 signed token31개, canonical18개, invalid-body13개, HTTP manifest11개를 적용했다. transport 미기동 항목은 별도 실환경 gate로 남기며 HTTP 상태를 합성하지 않는다.
- actual Tomcat loopback에서 정상/잘못된 query·빈?·method/path·auth-before-size·body400/413·타namespace를 확인했다. 첫 시도의 테스트 Tomcat 이중 init 실패를 테스트 초기화 순서 수정으로 해결했다.
- Spring 실제 security chain OFF404/Legacy catch-all 비침범·ON startup 차단 검증. key duplicate/set 오류의 원문/cause 비노출 확인.
- 격리 Mongo에서 atomic first/duplicate, event 우선 충돌, 동시같은/다른event, Session update 실패 rollback, 삭제된 계정/Session 비부활, index gate, 실제 signed fixture→filter→store→204를 검증했다.
- 실제 paid lifecycle의 revoke 전/후 pending, exact 보호Session finalize, unprotected revoke/finalize 동시경합, 권위취소 보상을 검증했다. `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew clean test mongoIntegrationTest bootJar` 성공 및 마지막 보상/설정 검증 보완 후 `test mongoIntegrationTest bootJar` 재실행 성공. 최종 단위1,243개 실패/오류0·기존1 skip, Mongo237개 실패/오류/skip0. 이번 단위79·Mongo13개 추가.
- Node Mongo migration121개·fixture SHA256SUMS34항목·diff check 통과. 실제 Mongo/AWS/index 적용은 없으며 격리 Testcontainers만 사용했다.

## 6. 전달 이력·범위 보존

PR #40 CI run38541952701 성공, merge f59cf284, 테스트 자동 배포 run38557338164 ECS/health 성공. 기능 설정은 바꾸지 않았다. P5 자체 commit/push/PR/배포는 하지 않았다. 기존 사용자 DS_Store와 앞선 전달 기록의 미커밋 변경을 보존했다. 이번 범위 밖 제품 변경은 없다.
