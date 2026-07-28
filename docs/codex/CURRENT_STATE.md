# Learning Core Current State

## Last updated

- 2026-07-28

## Current branch

- `main`

## Current Jira issue

- `TMI-10` — [Learning Core] Identity JWKS 기반 JWT 인증 연동
- Jira 상태: `진행 중` (상태 ID `10001`)
- 이번 구현 작업에서는 Jira 댓글과 상태를 변경하지 않았다.

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

## Important contracts

- JWT `sub`는 실제 `userId`다.
- JWT audience는 `tosunsaeng-learning-core`다.
- Python AI 요청의 `user_id`는 계속 `examId`다.
- AI Callback의 `user_id`도 `examId`로 해석한다.
- 실제 `userId`를 Python AI 서버로 보내지 않는다.
- 클라이언트 Request Body, Path, Query, Response DTO에 `userId`를 추가하지 않는다.
- 기존 API URL·Method·Parameter·DTO·`BaseResponse`·`retryCount` 계약을 유지한다.
- 기존 Redis Key·TTL, S3 Presigned URL·Object Key, 음성 제출·Polling 흐름을 유지한다.

## Test status

- `./gradlew clean test` 성공: 53개 테스트, 실패·오류·건너뜀 0개
- 기본 Legacy 모드, 무인증 Legacy API 접근, Legacy/JWT 빈 상호 배타 등록 검증 성공
- 테스트용 JWKS HTTP endpoint를 통한 유효 RS256 Token 시험 생성과 `ExamSession.userId` 저장 검증 성공
- 동일 사용자 접근 성공, 다른 사용자 접근 BaseResponse 403 검증 성공
- Token 없음, 잘못된 서명, 만료, 미래 nbf, 잘못된 issuer·audience, UUID가 아닌 sub의 BaseResponse 401 검증 성공
- AI Callback 무인증 접근과 Callback `user_id = examId` 흐름 검증 성공
- 기존 테스트에서 AI multipart/summary 요청의 `user_id = examId`, 외부 userId 미노출, Callback 실제 userId 매핑 검증 성공
- `git diff --check` 성공
- 실제 Identity 프로세스·Atlas·AWS·Redis·Python AI 서버는 테스트에서 호출하지 않았다.

## Existing HMAC code

- `JwtAuthenticationFilter`, `JwtTokenProvider`, `jwt.secret` 설정과 JJWT 의존성은 삭제하지 않았다.
- `JwtTokenProvider`의 자동 Component 등록과 기존 Security Filter 연결은 제거해 현재 두 모드 모두에서 HMAC Filter가 사용되지 않는다.
- JWT 모드는 OAuth2 Resource Server만 사용하며 HMAC Filter를 사용하지 않는다.
- HMAC 코드와 JJWT 의존성의 최종 삭제 여부는 별도 후속 정리가 필요하다.

## Known risks

- Jira 완료 조건의 실제 Identity와 Learning Core 로컬 E2E는 이번 요청의 외부 서버 호출 금지 조건에 따라 수행하지 않았다. 테스트용 JWKS 경계 통합 검증으로 대체했다.
- 배포 환경에서 JWT 모드를 활성화하기 전에 실제 Identity issuer·JWKS 주소와 audience 설정을 확인해야 한다.
- Nimbus 기본 clock skew가 적용되므로 exp와 nbf 경계에는 표준 허용 오차가 있다.
- JWKS key rotation과 Identity 장애 시 캐시 동작은 실제 환경에서 별도 점검이 필요하다.
- AI Callback은 의도대로 공개 상태이며 서비스 간 인증은 범위 밖이다.
- `APP_AUTH_MODE`는 `legacy` 또는 `jwt`만 사용해야 하며 다른 값은 유효한 인증 구성을 만들지 않는다.

## Next

- 실제 Identity를 로컬에서 기동해 발급 Access Token으로 Learning Core E2E를 수행한다.
- 배포 환경에서 `APP_AUTH_MODE=jwt` 전환 전 issuer·JWKS·audience와 네트워크 접근성을 확인한다.
- HMAC 코드·`JWT_SECRET_KEY` 설정·JJWT 의존성의 제거 여부를 별도 이슈에서 결정한다.
- 실제 E2E까지 통과한 뒤 Jira `TMI-10` 완료 전환과 댓글 등록 여부를 사용자가 결정한다.
