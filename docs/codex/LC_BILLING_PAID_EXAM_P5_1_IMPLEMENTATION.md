# LC P5.1 — revoke ON 구성·전용 TLS 연결

- 날짜: 2026-10-14 / 브랜치: `feat/billing-paid-exam-p5`
- 관련: TMI-116/TMI-128/TMI-122, TMI-193/TMI-136·공동 TMI-217, 선행 TMI-218.

## 1. 5줄 결론

1. P5 구성요소를 Spring ON security chain·verifier/store bean과 전용 TLS connector로 연결했다.
2. OFF이면 DB/키/TLS 구성 없이 기존 namespace404와 앱 경로를 유지한다.
3. ON 구성은 owner writer 활성·필수 index·환경/키·서로 다른 포트·PKCS12 인증서를 점검한다.
4. 격리 HTTPS/실제 Spring security chain·Mongo 회귀와 전체 테스트/빌드가 통과했다.
5. 실제 앱 ON은 별도 rollout gate에서 계속 거절한다. P6 legacy/owner/V07 검증·실환경 배포 완료가 아니다.

## 2. 반드시 읽어야 하는 내용

이번 요청은 연결 코드와 격리 검증이다. 커밋/푸시/배포·실DB/index 적용·실제 키나 환경변수 설정은 하지 않았다. 이전 P5 미커밋 변경과 사용자 파일을 보존했다.

### 변경 파일과 동작

- `src/main/java/web/tosunsaeng/domain/exams/access/AccessEventProperties.java`: 환경변수 바인딩, 내부/공개 포트 분리. credential이 출력되는 toString을 만들지 않는다.
- 같은 디렉터리 `AccessEventRuntimeConfiguration.java`: ON verifier/store 생성, owner writer와 기존 필수 index 점검 후 TLS 준비, 내부 포트의 앱 경로404 격리 필터.
- `AccessEventTls.java`: 마운트된 절대경로 PKCS12에서 private key/인증서·alias·유효기간을 확인하고 TLS1.2/1.3 전용 listener 구성. 예외에 원문 경로/비밀번호/cause를 포함하지 않는다.
- `AccessEventConfiguration.java`: ON에서 실제 verifier/store를 쓰는 chain, OFF에서는 두 bean을 조회하지 않는다.
- `AccessEventRolloutGate.java`: P6 legacy/lifecycle/V07 전 receiver ON을 거절하는 별도 안전장치. 이를 우회하는 설정은 추가하지 않았다.
- `BillingExamCreationTransactionService.java`: 기존 생성 commit/finalize에서도 group deny 확인. 이미 revoke된 Session을 활성화하지 않는다.
- `ExamSessionRepository.java`: 직접 entitlement 확정 update의 조건에 accessRevoked 제외.
- `ExamSubmissionEvidence.java`: revoke된 Session의 신규 retry0 receipt 생성 거절.
- `application.yml`: `BILLING_ACCESS_EVENTS_` 접두사의 RECEIVER_ENABLED/ENVIRONMENT/CONSUMER_SCOPE_ID/BILLING_PUBLIC_JWK_SET/PORT/TLS_KEY_STORE/TLS_KEY_STORE_PASSWORD/TLS_KEY_ALIAS 환경변수 매핑. 기본 OFF, 나머지 빈 값/port0이며 실제 배포값은 정하지 않았다.
- 테스트: `AccessReceiverSecurityTest`, `AccessReceiverTlsTest`, `BillingExamCreationTransactionServiceTest`, `PaidExamP3MongoTest`. AGENTS·계획/ADR·CURRENT_STATE·WORKLOG 동기화.

### 유지한 외부 계약

기존 공개 URL/Method/body/성공 DTO/BaseResponse, AI `user_id=examId`/Callback JSON, retryCount·S3·Redis 계약은 변경하지 않았다. Billing 발신 wire와 공동 fixture bytes도 불변이다. 새 설정은 역방향 수신 전용이며 공개 사용자 인증을 변경하지 않는다.

## 3. 결정 및 다음 작업 전 확인

이번 연결에 추가 제품 정책 선택은 필요 없다. 다음은 기존 전체 writer·owner/lifecycle coverage와 P6 legacy 구분/V07 보존·삭제 근거를 확인하는 단계다. 신규 삭제 sender/ACK는 TMI-217의 별도 공동 계약이다. 해당 검증과 명시적 배포 승인 없이 rollout gate를 제거하거나 수신 설정을 켜지 않는다.

## 4. 남은 위험과 배포 전 확인

- 격리 HTTPS는 제품 connector/filter의 실제 TLS handshake이며 Spring security chain은 별도 컨텍스트로 검증했다. 전체 앱 ON 기동·실제 Billing/ECS/ALB 통합 테스트 성공을 뜻하지 않는다.
- 실제 origin/port·네트워크 접근제어·인증서 배포/교체·JWT 키 회수 SLA, ALB의 빈 query/encoded path/헤더 보존은 미검증이다.
- owner writer 활성과 전체 writer 동시성 coverage는 다르다. 이번 기존 생성/receipt 회귀만으로 모든 legacy/merge/삭제/탈퇴 경계를 완료했다고 주장하지 않는다.
- 필수 index는 검증만 하며 자동 생성/재작성하지 않는다. V07 최종 purge·deny/보호 Session 증거 보존은 별도이며 no-TTL을 영구 개인정보 보존 정책으로 해석하지 않는다.

## 5. 실행한 테스트와 결과

- `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew clean test mongoIntegrationTest bootJar`: 성공. XML 합계 단위1,248개 실패/오류0·기존1 skip, Mongo238개 실패/오류/skip0.
- HTTPS 테스트: 임시 키/localhost 인증서를 테스트 디렉터리에 생성, 해당 인증서만 신뢰하는 클라이언트 사용. 정상204/SSL session, 내부포트 앱경로404, 공개포트 revoke404 및 X-Forwarded-Port 우회 불가, 공개 앱경로 유지.
- Spring 테스트: 실제 ON chain→원문 signed fixture→store 호출, OFF 시 잘못된 키/없는 TLS 파일·Mongo/owner 없이 구성, rollout guard 차단.
- 회귀: 기존 writer commit/finalize deny, 실제 Mongo 직접 confirm update0, revoke된 Session 접수 receipt 무생성. 기존 P5 동시성/rollback/원본 token 테스트 포함.
- 초기 테스트 컨텍스트의 refresh 전 bean 접근과 새 테스트의 잘못된 getter를 수정한 후 위 전체 clean 실행을 통과했다. 제품 테스트 실패를 숨기거나 검사를 제외하지 않았다.
- `node --test scripts/mongodb/*.test.js`:121개 성공. 공동 SHA256SUMS34항목 일치. `git diff --check` 성공.

## 6. 실제 diff와 작업 범위

예상 밖 이번 제품 변경은 없다. 기존 P5 미커밋 코드/문서 및 전달 기록은 유지했고 사용자 `.DS_Store` 변경/새 파일은 수정·정리하지 않았다. Billing/Identity 저장소, Jira, 실제 DB/AWS 설정 변경 없음. 실제 credential/토큰을 문서·로그에 추가하지 않았다. 이번 변경은 미커밋 상태다.
