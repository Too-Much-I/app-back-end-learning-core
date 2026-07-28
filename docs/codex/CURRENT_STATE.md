# Learning Core Current State

## Last updated

- 2026-07-28

## Current branch

- `test/TMI-11-auth-e2e`
- HEAD는 `origin/main`과 동일한 `bc15c50`이며 PR #9의 최신 피드백 조회·종합 피드백 저장소 분리 변경이 merge되어 있다.
- 현재 working tree에는 기존 Jira 기록 변경과 TMI-11 E2E 스크립트·README·JWT 계약 문서 변경이 있다. Codex는 commit과 push를 수행하지 않았다.

## Current Jira issue

- [`TMI-11`](https://to-teacher.atlassian.net/browse/TMI-11) — [Integration] Identity·Learning Core E2E 인증 테스트 및 JWT 계약 확정
- 이슈 유형: `작업` (ID `10003`)
- Jira 상태: `해야 할 일` (상태 ID `10000`, resolution 없음)
- 우선순위: `High` (ID `2`)
- 담당자: 미지정
- 생성 시각: `2026-07-28T12:30:27.701+0900`
- Identity 회원가입·로그인부터 Learning Core 시험 소유권, 실패 Token, Refresh Token Rotation·로그아웃, 공개 AI Callback, Python AI `user_id = examId` 계약까지 실제 두 서버 E2E로 검증하는 작업이다.
- Atlassian MCP로 이슈 설명과 완료 조건을 재조회해 이번 구현 기준으로 사용했다. Jira 댓글·필드·상태는 변경하지 않았다.
- 로컬 구현과 정적 검증, 두 저장소 전체 테스트는 완료했다. 실제 Identity 8081과 Learning Core 8080이 실행 중이지 않아 두 서버 E2E 실행과 직접 `ExamSession.userId` 확인은 대기 중이다.

## Related completed Jira issue

- `TMI-10` — [Learning Core] Identity JWKS 기반 JWT 인증 연동
- Jira 상태: `완료` (상태 ID `10003`, resolution `완료` ID `10000`)
- 2026-07-28 테스트 결과와 PR #8을 Jira 댓글 ID `10001`로 기록했다.
- 완료 전환 ID `41` 실행 후 상태와 resolution을 재조회해 확인했다.

## Completed

- 기존 웹 POC 백엔드에서 앱용 Learning Core 분리
- trial API와 terminate API 제거
- `ExamSession`에 `examId`, 실제 `userId`, `createdAt` 저장
- `CurrentUserProvider` 추상화와 `LegacyCurrentUserProvider` 유지
- `ExamResult.userId` 저장과 시험 소유권 검증 유지
- Feedback Callback의 `examId -> ExamSession -> 실제 userId` 매핑 유지
- Spring Security OAuth2 Resource Server 의존성 추가
- `APP_AUTH_MODE` 기반 Legacy/JWT 조건부 보안 구성 추가
- 기본값 `legacy`에서 기존 전체 `permitAll` 웹 흐름 유지
- `jwt` 모드에서 Identity JWKS를 명시적으로 사용하는 RS256 검증 구성
- issuer, audience, exp, nbf, UUID subject 검증 구성
- `JwtCurrentUserProvider`에서 JWT `sub`를 실제 사용자 UUID로 변환
- JWT 모드의 사용자용 API 인증 강제와 Callback·Swagger·OpenAPI·health 공개 경로 구성
- Security 계층의 401/403을 기존 `BaseResponse` JSON 구조로 반환
- 테스트용 JWKS endpoint와 합성 RSA 키를 이용한 JWT Resource Server 통합 테스트 추가
- PR [#8](https://github.com/Too-Much-I/app-back-end-learning-core/pull/8) merge 완료 및 CodeRabbit 체크 성공 확인
- Jira `TMI-10` 테스트·PR 댓글 등록과 완료 처리
- AI 문항 피드백을 요청한 `examId + questionNumber + retryCount` 범위의 최신 `_id` 문서로 조회하도록 보완
- 종합 피드백을 문항별 `exam_results`와 분리된 `exam_summaries` 컬렉션에 저장하도록 보완
- 종합 피드백 조회 시 `exam_summaries`의 최신 `_id` 문서를 우선하고, 분리 전 `exam_results`의 최신 종합 문서를 fallback하도록 보완
- PR #9로 최신 피드백 조회·종합 피드백 저장소 분리 변경을 `main`에 merge
- Identity·Learning Core E2E 인증 통합 테스트 후속 Jira Payload 초안과 지원 필드 검증 완료
- Jira `TMI-11` 생성과 제목·설명·유형·상태·우선순위 재조회 검증 완료
- `scripts/e2e/auth-integration-test.sh`에 실제 두 서버용 JWT 인증 E2E 자동화 추가
- `scripts/e2e/README.md`에 실행 전제, 환경변수, 정리 정책, 수동 DB 검증과 운영 실행 금지 안내 추가
- `docs/contracts/identity-learning-jwt.md`에 RS256·`kid`·Claim·JWKS·사용자 식별·AI·로그아웃 계약 확정

## Authentication modes

### Legacy

- `APP_AUTH_MODE=legacy` 또는 환경변수 미설정 시 활성화된다.
- `LegacyCurrentUserProvider`만 `CurrentUserProvider`로 등록한다.
- JWT Resource Server와 `JwtDecoder`는 등록하지 않는다.
- Identity 서버와 Authorization 헤더 없이 기존 API를 호출할 수 있다.

### JWT

- `APP_AUTH_MODE=jwt`일 때만 활성화된다.
- `JwtCurrentUserProvider`만 `CurrentUserProvider`로 등록한다.
- `IDENTITY_JWK_SET_URI`를 직접 사용하므로 OIDC discovery를 전제하지 않는다.
- RS256 서명, issuer, audience, exp, nbf, UUID subject를 검증한다.
- JWT `sub`를 정규화된 UUID 문자열로 반환해 `ExamSession.userId`와 소유권 검증에 사용한다.

## Public paths in JWT mode

- `/api/v1/exams/callback/**`
- `/swagger-ui/**`
- `/swagger-ui.html`
- `/v3/api-docs`와 `/v3/api-docs/**`
- `/actuator/health`와 하위 경로. 현재 Actuator 의존성은 없어 실제 health endpoint는 생성되지 않는다.

## Protected paths in JWT mode

- 위 공개 경로를 제외한 모든 요청은 authenticated다.
- 시험 생성, 상태·종합·문항 결과 조회, 업로드 URL, 음성 제출, 문항 Polling 등 기존 사용자용 시험 API가 포함된다.
- `examId`를 받는 사용자용 API의 기존 `ExamSession.userId == CurrentUserProvider.getCurrentUserId()` 소유권 검증을 유지한다.

## TMI-11 E2E automation

- `IDENTITY_BASE_URL` 기본값은 `http://localhost:8081`, `LEARNING_CORE_BASE_URL` 기본값은 `http://localhost:8080`이다.
- 스크립트는 Identity health·JWKS, 두 사용자 회원가입/로그인, JWT Claim, Learning Core 401/403·시험 소유권, 잘못된 Token, Refresh Rotation·재사용 탐지, 단일·전체 로그아웃, 공개 Feedback Callback을 단계별 검증한다.
- Access/Refresh Token과 Token 또는 URL을 포함할 수 있는 전체 응답은 출력하지 않고, 실패 시 단계·HTTP 상태·최상위 안전 필드만 출력한다.
- 임시 파일은 제한된 권한의 임시 디렉터리에 두고 `trap`으로 삭제한다.
- 만료·잘못된 issuer·잘못된 audience Token은 공개 API로 안전하게 생성하지 않고 기존 `JwtSecurityIntegrationTest` 검증을 사용한다.
- 사용자·시험 삭제 API가 없으므로 계정과 시험 문서는 자동 삭제하지 않는다. 기본 모드에서는 남은 Refresh Session만 로그아웃하며 직접 DB 검증은 수동 항목이다.

## Latest feedback lookup assessment

- Azure 문항 피드백은 `examId + questionNumber + retryCount` 조건에 `OrderByIdDesc`를 적용해 해당 회차의 최신 문서를 조회한다.
- AI 문항 피드백인 `ExamResult`도 `examId + questionNumber + retryCount` 조건에 `OrderByIdDesc`를 적용한 Repository 단건 조회로 최신 문서를 선택한다.
- 0회차 조회는 기존 `retryCount=null` 문서를 0으로 해석하던 호환성을 유지하기 위해 `retryCount in [0, null]` 조건을 사용한다.
- 문항 피드백 API는 클라이언트가 전달한 `retryCount` 회차를 조회하며 가장 큰 retryCount를 자동 선택하지 않는다.
- 신규 종합 피드백은 같은 MongoDB 연결의 별도 `exam_summaries` 컬렉션에 저장하고 `examId + OrderByIdDesc`로 최신 문서를 조회한다.
- `exam_summaries`가 비어 있으면 분리 전 `exam_results`에서 `totalScore != null`인 최신 `_id` 문서를 조회해 기존 데이터를 계속 제공한다.

## Important contracts

- JWT `sub`는 실제 `userId`다.
- Access Token은 RS256이며 Header의 `kid`가 Identity JWKS Public Key를 선택한다.
- JWT issuer는 환경별 Identity 설정값이고 `scope`는 공백 구분 문자열이다.
- JWT audience는 `tosunsaeng-learning-core`다.
- Python AI 요청의 `user_id`는 계속 `examId`다.
- AI Callback의 `user_id`도 `examId`로 해석한다.
- 실제 `userId`를 Python AI 서버로 보내지 않는다.
- 클라이언트 Request Body, Path, Query, Response DTO에 `userId`를 추가하지 않는다.
- 기존 API URL·Method·Parameter·DTO·`BaseResponse`·`retryCount` 계약을 유지한다.
- 기존 Redis Key·TTL, S3 Presigned URL·Object Key, 음성 제출·Polling 흐름을 유지한다.
- 종합 피드백 저장소 분리는 MongoDB 연결·database 설정을 추가하지 않고 컬렉션만 `exam_summaries`로 분리했다.
- 운영 앱에서는 Legacy 모드를 금지한다.
- `logout`과 `logout-all`은 Refresh Session을 폐기하지만 기존 Access Token의 즉시 무효화를 보장하지 않는다.

## Test status

- `./gradlew clean test` 성공: 56개 테스트, 실패·오류·건너뜀 0개
- 기본 Legacy 모드, 무인증 Legacy API 접근, Legacy/JWT 빈 상호 배타 등록 검증 성공
- 테스트용 JWKS HTTP endpoint를 통한 유효 RS256 Token 시험 생성과 `ExamSession.userId` 저장 검증 성공
- 동일 사용자 접근 성공, 다른 사용자 접근 BaseResponse 403 검증 성공
- Token 없음, 잘못된 서명, 만료, 미래 nbf, 잘못된 issuer·audience, UUID가 아닌 sub의 BaseResponse 401 검증 성공
- AI Callback 무인증 접근과 Callback `user_id = examId` 흐름 검증 성공
- 기존 테스트에서 AI multipart/summary 요청의 `user_id = examId`, 외부 userId 미노출, Callback 실제 userId 매핑 검증 성공
- `git diff --check` 성공
- 실제 Identity 프로세스·Atlas·AWS·Redis·Python AI 서버는 테스트에서 호출하지 않았다.
- Jira 완료 처리 작업에서는 애플리케이션 코드를 변경하지 않아 테스트를 다시 실행하지 않았고, 직전 구현 작업의 53개 전체 성공 결과를 댓글에 기록했다.
- 최신 문항 피드백 조회 여부 분석 작업에서는 코드를 변경하지 않아 테스트를 실행하지 않았다.
- 같은 문항·retryCount의 구·신규 `ExamResult`가 함께 있을 때 최신 결과를 응답하는 테스트와 0회차 null 호환 조회 검증 성공
- 종합 Callback이 `ExamSummary`만 저장하고 `ExamResult`에는 저장하지 않는지, `ExamSession.userId` 매핑과 Redis 완료 상태가 유지되는지 검증 성공
- 최신 `exam_summaries` 조회와 새 컬렉션이 비어 있을 때 최신 legacy `exam_results` 종합 문서 fallback 검증 성공
- Atlassian MCP에서 TMI 프로젝트, `작업` 유형, 설명 필드와 `High` 우선순위 지원 여부를 읽기 전용으로 확인했다. 애플리케이션 코드를 변경하지 않아 이번 초안 작업에서는 Gradle 테스트를 다시 실행하지 않았다.
- Atlassian MCP로 `TMI-11`을 생성한 뒤 제목·설명·프로젝트·이슈 유형·상태·우선순위를 재조회해 승인된 Payload 반영을 확인했다. 애플리케이션 코드는 변경하지 않았다.
- Stop Hook 보완 기록의 필수 marker 단일 존재와 `git diff --check`를 검증했다.
- TMI-11 정적 검증: `bash -n scripts/e2e/auth-integration-test.sh`, JWKS/Claim jq filter 샘플, 비대화형 비밀번호 누락 오류, `git diff --check` 성공
- ShellCheck는 로컬에 설치돼 있지 않아 자동 설치하거나 실행하지 않았다.
- Learning Core `./gradlew clean test` 성공: 56개, 실패·오류·건너뜀 0개. 기존 `ExamServiceImpl` unchecked 경고만 남았다.
- Identity 저장소 `./gradlew clean test` 성공: 138개, 실패·오류·건너뜀 0개. Identity 소스와 추적 파일은 변경하지 않았다.
- 기본 8081/8080 포트 모두 연결되지 않아 실제 E2E 스크립트는 실행하지 않았다.

## Existing HMAC code

- `JwtAuthenticationFilter`, `JwtTokenProvider`, `jwt.secret` 설정과 JJWT 의존성은 삭제하지 않았다.
- `JwtTokenProvider`의 자동 Component 등록과 기존 Security Filter 연결은 제거해 현재 두 모드 모두에서 HMAC Filter가 사용되지 않는다.
- JWT 모드는 OAuth2 Resource Server만 사용하며 HMAC Filter를 사용하지 않는다.
- HMAC 코드와 JJWT 의존성의 최종 삭제 여부는 별도 후속 정리가 필요하다.

## Known risks

- Jira `TMI-10`은 사용자 요청에 따라 완료 처리됐지만 실제 Identity와 Learning Core 로컬 E2E는 수행하지 않았다. 테스트용 JWKS 경계 통합 검증으로 대체한 상태다.
- 배포 환경에서 JWT 모드를 활성화하기 전에 실제 Identity issuer·JWKS 주소와 audience 설정을 확인해야 한다.
- Nimbus 기본 clock skew가 적용되므로 exp와 nbf 경계에는 표준 허용 오차가 있다.
- JWKS key rotation과 Identity 장애 시 캐시 동작은 실제 환경에서 별도 점검이 필요하다.
- AI Callback은 의도대로 공개 상태이며 서비스 간 인증은 범위 밖이다.
- `APP_AUTH_MODE`는 `legacy` 또는 `jwt`만 사용해야 하며 다른 값은 유효한 인증 구성을 만들지 않는다.
- 최신 저장 순서는 MongoDB가 자동 생성하는 `_id` 내림차순을 기준으로 판단한다.
- 기존 `exam_results`의 종합 문서는 삭제·이관하지 않고 읽기 fallback으로 유지한다.
- 물리적으로 별도 MongoDB database나 클러스터를 요구한다면 별도 연결 설정과 운영 값이 추가로 필요하다. 현재 구현은 같은 database 내 컬렉션 분리다.
- 데이터가 커지면 `exam_summaries`의 `examId + _id` 조회용 복합 인덱스를 운영 환경에서 검토해야 한다.
- TMI-11 스크립트 구현은 완료했지만 실제 Identity 8081과 JWT 모드 Learning Core 8080이 기동되지 않아 실서버 E2E 결과는 아직 없다.
- Jira 완료 조건의 `ExamSession.userId == JWT sub` 직접 DB 비교는 MongoDB 자격증명을 스크립트에 넣지 않기 위해 수동 검증으로 남겼다. 소유권 200/403 시나리오는 API 경계에서 간접 검증한다.
- AI Callback은 사용자 JWT 없이 공개 상태이며 서비스 간 인증은 아직 없다.
- 사용자·시험 삭제 API가 없어 로컬 E2E 계정과 시험 문서는 테스트 DB에 남으며 운영자 정리가 필요하다.

## Next

- Identity를 8081, Learning Core를 JWT 모드 8080으로 기동한 뒤 `scripts/e2e/auth-integration-test.sh`를 실행한다.
- 실제 E2E 성공 후 출력된 수동 확인 식별자로 `exam_sessions.userId`와 JWT `sub`를 폐기 가능한 로컬 DB에서 비교한다.
- 배포 환경에서 `APP_AUTH_MODE=jwt` 전환 전 issuer·JWKS·audience와 네트워크 접근성을 확인한다.
- HMAC 코드·`JWT_SECRET_KEY` 설정·JJWT 의존성의 제거 여부를 별도 이슈에서 결정한다.
- Jira `TMI-10`은 완료됐으므로 후속 위험은 별도 Jira 이슈로 추적한다.
- 물리적으로 다른 MongoDB database가 필요한지 확인하고, 필요하면 별도 MongoTemplate·자격증명·배포 환경변수 범위를 정의한다.
- 운영 데이터 규모에 따라 `exam_summaries` 조회 인덱스와 legacy 종합 문서 이관·보존 정책을 결정한다.
- 실제 E2E와 수동 DB 검증이 끝나면 준비된 완료 댓글 초안을 검토하고, Jira 댓글·상태 변경은 사용자 승인 후 별도로 수행한다.
- 사용자가 변경분을 검토한 뒤 commit과 push를 수행한다.
