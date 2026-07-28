# Learning Core Current State

## Last updated

- 2026-07-28

## Current branch

- `refactor/TMI-14-jwt-production-hardening`
- HEAD는 `bc19caf` (`Merge pull request #10 from Too-Much-I/test/TMI-11-auth-e2e`)이며 `origin/main`과 동일하다.
- 현재 브랜치에는 upstream이 설정돼 있지 않다.
- working tree에는 TMI-14의 제한 예외, 인증 설정·Startup 검증·Legacy profile 차단, 미사용 HMAC/JJWT/Secret 제거, 테스트·README·계약·Codex 기록 변경만 있다. Codex는 commit과 push를 수행하지 않았다.

## Latest completed Jira issue

- [`TMI-11`](https://to-teacher.atlassian.net/browse/TMI-11) — [Integration] Identity·Learning Core E2E 인증 테스트 및 JWT 계약 확정
- 이슈 유형: `작업` (ID `10003`)
- Jira 상태: `완료` (상태 ID `10003`, resolution `완료` ID `10000`)
- 우선순위: `High` (ID `2`)
- 담당자: 미지정
- 생성 시각: `2026-07-28T12:30:27.701+0900`
- Identity 회원가입·로그인부터 Learning Core 시험 소유권, 실패 Token, Refresh Token Rotation·로그아웃, 공개 AI Callback, Python AI `user_id = examId` 계약까지 실제 두 서버 E2E로 검증하는 작업이다.
- 완료 댓글 ID `10002`에 구현 파일, 자동화 범위, JWT 계약, 정적·Gradle 테스트 결과, 실제 서버 E2E 미실행과 수동 DB 확인 잔여 항목을 기록했다.
- 사용자 요청에 따라 완료 전환 ID `41`을 실행하고 상태와 resolution을 재조회해 확인했다.
- 로컬 구현과 정적 검증, 두 저장소 전체 테스트는 완료했다. 실제 Identity 8081과 Learning Core 8080이 실행 중이지 않아 두 서버 E2E 실행과 직접 `ExamSession.userId` 확인은 후속 운영 검증으로 남아 있다.

## Current Jira issue

- 진행 중 작업: Jira TMI-14
- [`TMI-14`](https://to-teacher.atlassian.net/browse/TMI-14) — [Learning Core] 운영 JWT 모드 강제 및 Legacy/HMAC 인증 정리
- 프로젝트: `TMI` (ID `10000`)
- 이슈 유형: `작업` (ID `10003`)
- Jira 상태: `진행 중` (상태 ID `10001`, 상태 범주 `진행 중`)
- 우선순위: `High` (ID `2`)
- 담당자: 설정됨 (개인 식별 정보는 기록하지 않으며 이번 작업에서는 변경하지 않음)
- 스프린트·에픽·라벨: 생성 Payload에서 설정하지 않았고 라벨은 빈 값으로 재조회됐다.
- Atlassian MCP로 동일 제목 이슈가 없음을 확인한 뒤 승인된 제목과 설명을 그대로 사용해 한 건 생성했다.
- 생성 후 제목·설명·프로젝트·유형·우선순위·기본 상태·담당자·라벨을 재조회해 확인했으며 상태 전환은 수행하지 않았다.
- 2026-07-28 읽기 전용 재조회 기준 현재 상태는 `해야 할 일`(상태 ID `10000`)이며, 사용 가능한 전환은 `해야 할 일`(전환 ID `11`), `진행 중`(`21`), `검토 중`(`31`), `완료`(`41`)이다.
- 이번 재조회에서는 Jira 이슈 수정·댓글·상태 전환 API를 호출하지 않았다.
- 사용자 요청에 따라 전환 직전 상태와 가용 전환을 다시 확인하고 전환 ID `21`만 사용해 `진행 중`으로 변경했다. 후속 상세 조회로 상태 ID `10001`을 확인했다.
- 전환 요청에는 다른 필드·댓글·업데이트를 포함하지 않았고 다른 Jira 이슈를 수정하지 않았다.
- 구현 착수 전 Atlassian MCP로 설명과 완료 조건을 재조회했고, 사용자가 TMI-14에 한해 “JWT 인증 강제 제외” 규칙의 제한 예외를 명시적으로 승인했다. 예외 범위는 staging/prod Startup 검증·Legacy 차단·auth mode 검증·미사용 HMAC 정리뿐이다.
- 제한 예외를 `AGENTS.md`에 추가하고 전체 규칙을 재검토한 뒤 구현했다. 기존 JWT 보호·공개 경로, local/test Legacy와 외부 계약은 변경하지 않았다.
- 타입 안전 `AuthMode`, Startup Validator, profile 격리, HMAC/JJWT/`JWT_SECRET_KEY` 제거와 관련 문서·테스트 구현을 완료했다. Jira 댓글·필드·상태는 변경하지 않아 이슈는 계속 `진행 중`이다.
- 공개 API·DTO·`BaseResponse`, AI `user_id = examId`, `retryCount`, Redis·S3와 시험 소유권 계약에서는 Jira와 저장소 규칙 간 충돌이 확인되지 않았다.

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
- Learning Core 운영 JWT 모드 강제 및 Legacy/HMAC 인증 정리 후속 Jira Payload 초안과 지원 필드 검증 완료
- Jira `TMI-14` 생성과 승인된 제목·설명·유형·우선순위·기본 상태 재조회 검증 완료
- Jira `TMI-14` 생성 turn의 전용 Stop Hook marker 기록 완료
- Jira `TMI-14` 현재 상태와 가능한 전환의 읽기 전용 조회 완료
- Jira `TMI-14`를 다른 필드 변경 없이 `진행 중`으로 전환하고 재조회 검증 완료
- Jira `TMI-11` 생성과 제목·설명·유형·상태·우선순위 재조회 검증 완료
- Jira `TMI-11` 작업 결과 댓글 ID `10002` 등록과 완료 처리
- `scripts/e2e/auth-integration-test.sh`에 실제 두 서버용 JWT 인증 E2E 자동화 추가
- `scripts/e2e/README.md`에 실행 전제, 환경변수, 정리 정책, 수동 DB 검증과 운영 실행 금지 안내 추가
- `docs/contracts/identity-learning-jwt.md`에 RS256·`kid`·Claim·JWKS·사용자 식별·AI·로그아웃 계약 확정
- Jira TMI-14 제한 예외를 `AGENTS.md`에 명시하고 다른 작업·경로·외부 계약으로 확대되지 않음을 재검토
- `AuthMode.LEGACY`·`AuthMode.JWT`와 `AuthProperties`를 추가하고 소문자 `legacy`·`jwt`만 허용하도록 설정 바인딩 검증
- staging/prod에서 JWT 모드와 비로컬 issuer·JWKS URL·audience를 강제하고 설정 형식만 검사하는 `AuthStartupValidator` 추가
- Legacy Provider와 Legacy SecurityFilterChain을 `local`·`test` profile로 제한하고 staging/prod 강제 등록 탐지 추가
- 미사용 `JwtAuthenticationFilter`, `JwtTokenProvider`, JJWT 의존성, `jwt.secret`, `JWT_SECRET_KEY` 설정 제거
- 기존 OAuth2 Resource Server, RS256·issuer·audience·timestamp·UUID subject 검증과 보호·공개 경로 유지
- README와 JWT 계약·E2E 실행 문서에 local/test Legacy와 staging/prod JWT 환경 규칙 반영

## Authentication modes

### Legacy

- `APP_AUTH_MODE=legacy` 또는 모드 설정 누락은 `AuthMode.LEGACY`로 해석되지만 `local`·`test` profile에서만 활성화된다.
- active profile이 없거나 staging/prod에서 Legacy 모드를 선택하면 시작에 실패한다.
- `LegacyCurrentUserProvider`만 `CurrentUserProvider`로 등록한다.
- JWT Resource Server와 `JwtDecoder`는 등록하지 않는다.
- Identity 서버와 Authorization 헤더 없이 기존 API를 호출할 수 있다.

### JWT

- `APP_AUTH_MODE=jwt`일 때만 활성화된다.
- `JwtCurrentUserProvider`만 `CurrentUserProvider`로 등록한다.
- `IDENTITY_JWK_SET_URI`를 직접 사용하므로 OIDC discovery를 전제하지 않는다.
- RS256 서명, issuer, audience, exp, nbf, UUID subject를 검증한다.
- JWT `sub`를 정규화된 UUID 문자열로 반환해 `ExamSession.userId`와 소유권 검증에 사용한다.

## TMI-14 Startup validation

- `APP_AUTH_MODE`는 소문자 `legacy`와 `jwt`만 허용한다. 빈 값, 대문자 표기, 오타와 지원하지 않는 값은 안전한 오류로 시작 실패하며 Legacy로 fallback하지 않는다.
- JWT 모드는 모든 profile에서 issuer·JWKS URL·audience의 존재와 HTTP(S) URI 형식을 검증한다.
- 공통 설정에는 Identity 기본값을 두지 않고 `application-local.yml`에서만 localhost issuer·JWKS URL과 개발 audience 기본값을 제공하므로 staging/prod 누락이 local 기본값으로 숨지 않는다.
- staging/prod는 JWT 모드만 허용하고 localhost·loopback Identity URL과 placeholder audience를 거부한다.
- 검증 단계에서 Identity 또는 JWKS endpoint로 네트워크 요청을 보내지 않는다.
- staging/prod에서 `LegacyCurrentUserProvider` 또는 `legacySecurityFilterChain`이 강제로 등록되면 시작 실패한다.
- local/test에서는 명시적으로 Legacy를 사용할 수 있고 Identity 연결 없이 기존 웹 호환 흐름이 동작한다.

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

- `./gradlew clean test` 성공: 88개 테스트, 실패·오류·건너뜀 0개
- AuthMode의 legacy/JWT 변환, 누락 시 local Legacy 기본값, 빈 값·대문자·오타 실패 검증 성공
- local/test Legacy 성공, profile 없는 Legacy와 staging/prod Legacy 실패, staging/prod 정상 JWT 설정 성공 검증
- staging/prod issuer·JWKS URL·audience 누락, URI 형식 오류, localhost·loopback, placeholder audience 실패 검증
- local/test Legacy Provider 등록과 staging/prod 미등록, 강제 Legacy Provider·FilterChain 등록 실패 검증
- Legacy/JWT 모드별 `CurrentUserProvider`, `SecurityFilterChain`, `JwtDecoder` 단일 등록 검증
- HMAC 두 클래스 부재, JJWT 의존성과 `JWT_SECRET_KEY`·`jwt.secret` 활성 설정 부재 검증
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
- Atlassian MCP에서 운영 JWT 보안 정리용 TMI 생성 권한과 `작업`·설명·`High` 필드를 재검증하고 동일 제목 중복이 없음을 확인한 뒤 `TMI-14`를 생성했다. 생성 후 승인된 제목·설명, `작업`, `High`, 기본 상태 `해야 할 일`, 담당자 미지정과 빈 라벨을 재조회했다. 초안 및 생성 turn의 필수 marker가 각각 정확히 한 번 존재하고 `git diff --check`가 성공했다. 애플리케이션 코드는 변경하지 않아 Gradle 테스트를 실행하지 않았다.
- Atlassian MCP로 `TMI-14`의 현재 상태 `해야 할 일`과 사용 가능한 전환 `해야 할 일(11)`·`진행 중(21)`·`검토 중(31)`·`완료(41)`를 직접 조회했다. Jira 변경 API와 애플리케이션 코드는 호출·수정하지 않아 Gradle 테스트를 실행하지 않았다.
- Atlassian MCP로 `TMI-14`의 `진행 중` 전환 ID `21`을 실행하고 상태 ID `10001`을 후속 재조회했다. 전환 Payload에는 다른 필드·댓글·업데이트가 없었고 다른 Jira 이슈를 수정하지 않았다. 애플리케이션 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.
- TMI-14 구현 전 `AGENTS.md`, CURRENT_STATE와 Jira 설명·완료 조건을 대조했다. “JWT 인증 강제” 금지와 staging/prod JWT 모드 강제 요구의 충돌로 구현을 시작하지 않았고, 코드 변경이 없어 인증 모드 테스트와 Gradle 테스트를 실행하지 않았다.
- Atlassian MCP로 `TMI-11`을 생성한 뒤 제목·설명·프로젝트·이슈 유형·상태·우선순위를 재조회해 승인된 Payload 반영을 확인했다. 애플리케이션 코드는 변경하지 않았다.
- Stop Hook 보완 기록의 필수 marker 단일 존재와 `git diff --check`를 검증했다.
- TMI-11 정적 검증: `bash -n scripts/e2e/auth-integration-test.sh`, JWKS/Claim jq filter 샘플, 비대화형 비밀번호 누락 오류, `git diff --check` 성공
- ShellCheck는 로컬에 설치돼 있지 않아 자동 설치하거나 실행하지 않았다.
- Learning Core `./gradlew clean test` 성공: 56개, 실패·오류·건너뜀 0개. 기존 `ExamServiceImpl` unchecked 경고만 남았다.
- Identity 저장소 `./gradlew clean test` 성공: 138개, 실패·오류·건너뜀 0개. Identity 소스와 추적 파일은 변경하지 않았다.
- 기본 8081/8080 포트 모두 연결되지 않아 실제 E2E 스크립트는 실행하지 않았다.

## HMAC cleanup

- 저장소 전수 검색에서 `JwtAuthenticationFilter`와 `JwtTokenProvider`는 Bean·FilterChain·비즈니스 코드에서 사용되지 않고 서로만 참조함을 확인했다.
- 두 HMAC 클래스와 전용 JJWT API·runtime 의존성을 삭제했다.
- `application.yml`과 테스트 설정에서 `jwt.secret`·`JWT_SECRET_KEY`를 제거했으며 공유 HMAC Secret은 더 이상 필요하지 않다.
- 활성 런타임 소스·설정·빌드에서 HMAC 클래스, `addFilterBefore`, JJWT와 공유 Secret 잔여 사용처가 없음을 확인했다.
- JWT 인증 책임은 기존 Identity JWKS 기반 OAuth2 Resource Server에만 남아 있다.

## Known risks

- Jira `TMI-10`은 사용자 요청에 따라 완료 처리됐지만 실제 Identity와 Learning Core 로컬 E2E는 수행하지 않았다. 테스트용 JWKS 경계 통합 검증으로 대체한 상태다.
- 배포 환경에서 JWT 모드를 활성화하기 전에 실제 Identity issuer·JWKS 주소와 audience 설정을 확인해야 한다.
- Nimbus 기본 clock skew가 적용되므로 exp와 nbf 경계에는 표준 허용 오차가 있다.
- JWKS key rotation과 Identity 장애 시 캐시 동작은 실제 환경에서 별도 점검이 필요하다.
- AI Callback은 의도대로 공개 상태이며 서비스 간 인증은 범위 밖이다.
- `APP_AUTH_MODE`는 소문자 `legacy` 또는 `jwt`만 허용되며 잘못된 값은 시작 실패한다.
- 최신 저장 순서는 MongoDB가 자동 생성하는 `_id` 내림차순을 기준으로 판단한다.
- 기존 `exam_results`의 종합 문서는 삭제·이관하지 않고 읽기 fallback으로 유지한다.
- 물리적으로 별도 MongoDB database나 클러스터를 요구한다면 별도 연결 설정과 운영 값이 추가로 필요하다. 현재 구현은 같은 database 내 컬렉션 분리다.
- 데이터가 커지면 `exam_summaries`의 `examId + _id` 조회용 복합 인덱스를 운영 환경에서 검토해야 한다.
- TMI-11 스크립트 구현은 완료했지만 실제 Identity 8081과 JWT 모드 Learning Core 8080이 기동되지 않아 실서버 E2E 결과는 아직 없다.
- Jira 완료 조건의 `ExamSession.userId == JWT sub` 직접 DB 비교는 MongoDB 자격증명을 스크립트에 넣지 않기 위해 수동 검증으로 남겼다. 소유권 200/403 시나리오는 API 경계에서 간접 검증한다.
- AI Callback은 사용자 JWT 없이 공개 상태이며 서비스 간 인증은 아직 없다.
- 사용자·시험 삭제 API가 없어 로컬 E2E 계정과 시험 문서는 테스트 DB에 남으며 운영자 정리가 필요하다.
- TMI-14의 가능한 전환 목록은 조회 시점의 워크플로와 현재 사용자 권한 기준이므로 실제 전환 직전에 다시 확인해야 한다.
- Startup Validator는 설정 형식과 로컬 URL 사용 여부만 확인하며 실제 staging/prod Identity·JWKS 네트워크 도달성은 배포 전 별도 확인이 필요하다.
- staging/prod 전체 애플리케이션을 실제 운영 인프라 설정으로 기동하는 smoke test는 수행하지 않았고 외부 호출 없는 ApplicationContext 검증으로 대체했다.

## Next

- Identity를 8081, Learning Core를 JWT 모드 8080으로 기동한 뒤 `scripts/e2e/auth-integration-test.sh`를 실행한다.
- 실제 E2E 성공 후 출력된 수동 확인 식별자로 `exam_sessions.userId`와 JWT `sub`를 폐기 가능한 로컬 DB에서 비교한다.
- 배포 환경에서 `APP_AUTH_MODE=jwt` 전환 전 issuer·JWKS·audience와 네트워크 접근성을 확인한다.
- 실제 배포 전에 staging/prod에 `APP_AUTH_MODE=jwt`, 환경별 issuer·JWKS URL·audience와 나머지 인프라 설정을 주입해 smoke test한다.
- Jira TMI-14 완료 댓글 초안을 사용자가 검토한 뒤 댓글 등록과 상태 변경 여부를 직접 결정한다.
- Jira `TMI-14`의 추가 상태 전환은 사용자가 다시 명시적으로 요청하기 전에는 수행하지 않으며, 요청 시 전환 목록을 다시 조회한다.
- Jira `TMI-10`은 완료됐으므로 후속 위험은 별도 Jira 이슈로 추적한다.
- 물리적으로 다른 MongoDB database가 필요한지 확인하고, 필요하면 별도 MongoTemplate·자격증명·배포 환경변수 범위를 정의한다.
- 운영 데이터 규모에 따라 `exam_summaries` 조회 인덱스와 legacy 종합 문서 이관·보존 정책을 결정한다.
- Jira `TMI-11`은 완료 처리됐으며 실제 서버 E2E나 수동 DB 검증에서 문제가 발견되면 이슈를 다시 열거나 별도 후속 이슈로 추적한다.
- 사용자가 변경분을 검토한 뒤 commit과 push를 수행한다.
