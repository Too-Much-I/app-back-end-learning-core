# Learning Core Codex WORKLOG

이 파일은 Codex 작업 이력을 보존하는 append-only 기록이다.

- 과거 기록은 수정하거나 삭제하지 않는다.
- 각 Codex 작업이 끝날 때 파일 끝에 새 항목을 append한다.
- Secret, Token, MongoDB URI, AWS Key, RSA Private Key, 사용자 개인정보는 기록하지 않는다.

## 2026-07-28 — Learning Core 기준 상태 기록

<!-- codex-turn:learning-core-baseline-20260728 -->

- 날짜: `2026-07-28`
- 브랜치: `chore/learning-core-codex-worklog`
- Jira 이슈 키: `TMI-10` (다음 작업, 구현 미착수)
- 작업 목표: 앱용 Learning Core 분리 이후 현재까지 완료된 사용자 식별·시험 소유권·AI Callback 연동 상태를 작업 기록 자동화 도입 전 기준선으로 남긴다.
- 변경 파일: 이 항목은 기존 완료 작업의 요약이며 이번 기준선 작성에서 애플리케이션 파일을 변경하지 않았다. 기존 완료 작업의 주요 관련 파일은 `ExamRestController.java`, `ExamService.java`, `ExamServiceImpl.java`, `ExamSession.java`, `ExamSessionRepository.java`, `ExamResult.java`, `ExamConverter.java`, `CurrentUserProvider.java`, `LegacyCurrentUserProvider.java`와 관련 시험·Callback·소유권 테스트다.
- 구현 내용: 기존 웹 POC 백엔드에서 앱용 Learning Core를 분리했고 trial·terminate API를 제거했다. 시험 생성 시 `ExamSession`에 `examId`와 실제 `userId`를 저장하도록 구성했으며 `CurrentUserProvider` 추상화와 고정 개발 UUID를 반환하는 `LegacyCurrentUserProvider`를 추가했다. `ExamResult.userId`를 추가하고 Feedback Callback의 `user_id`를 `examId`로 해석해 `ExamSession.userId`를 결과에 저장한다. 사용자용 시험 API 6개에 소유권 검증을 적용했다.
- 실행한 테스트와 결과: 기준 상태상 기존 Learning Core 전체 테스트는 성공했다. 이 기준선 기록 자체에서는 애플리케이션 테스트를 재실행하지 않았다.
- 유지한 외부 계약: Python AI 요청과 Callback의 `user_id = examId` 계약, 기존 공개 API URL·Method·Parameter·Request·Response DTO·BaseResponse, `retryCount`, Redis 상태·Lock, S3 Presigned URL·Object Key 및 음성 제출·Polling 흐름을 유지했다. 실제 `userId`는 클라이언트 계약이나 Python AI 요청에 추가하지 않았다.
- 결정사항: 실제 사용자 식별자는 내부 DB 저장과 시험 소유권 검증에만 사용하고, Identity 연동 전까지 Legacy 개발 모드를 유지한다. 다음 인증 연동은 Identity JWKS 기반 RS256 검증을 기준으로 진행한다.
- 위험 요소: `LegacyCurrentUserProvider`가 남아 있고 기존 HMAC 기반 `JwtAuthenticationFilter`와 `JwtTokenProvider`가 존재할 수 있다. AI Callback의 서비스 간 인증은 아직 없으며 Legacy와 JWT 모드를 동시에 활성화하면 Bean 충돌 가능성이 있다. Identity와 Learning Core의 실제 E2E 연동은 수행하지 않았다.
- 다음 작업: Jira `TMI-10`에서 OAuth2 Resource Server, Identity JWKS 기반 RS256 서명 검증, issuer·audience 검증, `JwtCurrentUserProvider`, Legacy/JWT 모드 분리, 사용자용 API 인증 강제와 AI Callback 공개 정책을 구현·검증한다.

## 2026-07-28 — Codex 작업 기록 자동화 구성

<!-- codex-turn:learning-core-worklog-bootstrap-20260728 -->

- 날짜: `2026-07-28`
- 브랜치: `chore/learning-core-codex-worklog`
- Jira 이슈 키: `TMI-10` (다음 예정 작업이며 이번 구성에서는 구현하지 않음)
- 작업 목표: Learning Core 애플리케이션 코드를 변경하지 않고, 저장소 현재 상태 주입과 turn별 append-only 작업 기록을 자동화하는 Codex Hook 체계를 구성한다.
- 변경 파일: `AGENTS.md`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`, `.codex/hooks.json`, `.codex/hooks/load_current_state.py`, `.codex/hooks/inject_worklog_instruction.py`, `.codex/hooks/enforce_worklog.py`
- 구현 내용: `AGENTS.md`에 구현·분석·리뷰 작업의 WORKLOG append 및 CURRENT_STATE 갱신 규칙을 추가했다. SessionStart는 Git 루트를 찾아 CURRENT_STATE를 추가 컨텍스트로 출력하고 파일이 없으면 조용히 종료한다. UserPromptSubmit은 `turn_id` marker와 기록 지침을 주입한다. Stop은 marker가 없으면 1차 종료를 차단하고, 재실행에도 누락되면 날짜·브랜치·알 수 있는 Jira 키·`git status --short`·`git diff --stat`·누락 안내만 담은 fallback을 append한 뒤 종료를 허용한다.
- 실행한 테스트와 결과: Hook Python 파일 3개의 `python3 -m py_compile`과 `hooks.json` JSON 형식 검사가 성공했다. 격리된 임시 Git 저장소에서 SessionStart 상태 주입과 파일 부재 시 무오류 종료, UserPromptSubmit marker 단일 주입, Stop의 기존 marker 허용·1차 차단·2차 fallback 생성 및 Jira·Git 요약 기록을 검증해 모두 통과했다. `./gradlew clean test`는 기본 샌드박스에서 사용자 Gradle 캐시 접근 제한으로 한 차례 실행 전 차단됐고, 승인된 캐시 접근으로 재실행해 `BUILD SUCCESSFUL`과 34개 테스트의 실패·오류·건너뜀 0개를 확인했다. `git diff --check`도 통과했다.
- 유지한 외부 계약: Java 애플리케이션 코드, `build.gradle`, `application.yml`, `SecurityConfig`를 변경하지 않았다. 공개 API URL·Method·Parameter·DTO·BaseResponse, `retryCount`, Python AI `user_id = examId`, Callback JSON, Redis, S3, 음성 제출 및 Polling 흐름을 그대로 유지했다.
- 결정사항: Python 3 표준 라이브러리만 사용하고 모든 Hook 명령과 스크립트에서 `git rev-parse --show-toplevel`을 기준으로 저장소 루트를 결정한다. WORKLOG는 append-only로 운영하고 CURRENT_STATE는 최신 상태만 유지한다. 이번 세션은 Hook 활성화 전에 시작되어 실제 `turn_id` 대신 단일 bootstrap marker를 기록하며, 다음 사용자 프롬프트부터 실제 turn marker를 사용한다.
- 위험 요소: 프로젝트와 각 Hook 정의의 현재 hash를 신뢰하기 전에는 Hook이 실행되지 않는다. Stop의 2차 fallback은 최소 진단 정보만 보존하므로 정상 작업 요약이나 CURRENT_STATE 갱신을 대신하지 못한다. TMI-10의 JWT 인증 구현과 Identity–Learning Core E2E 검증은 아직 시작하지 않았다.
- 다음 작업: Codex에서 프로젝트와 세 Hook을 검토·신뢰한 뒤 새 세션 또는 다음 프롬프트로 Hook 동작을 활성화한다. 이후 Jira `TMI-10` 설명과 완료 조건을 확인하고 Identity JWKS 기반 JWT 인증 연동을 시작한다.

## 2026-07-28 — Jira TMI-10 상태 및 전환 읽기 전용 조회

<!-- codex-turn:019fa633-67b9-7e01-88b5-7b415d81d41c -->

- 날짜: `2026-07-28`
- 브랜치: `main`
- Jira 이슈 키: `TMI-10`
- 작업 목표: Atlassian MCP로 `TMI-10`의 현재 상태와 현재 권한에서 가능한 상태 전환을 조회하고, Jira 데이터는 변경하지 않는다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`
- 조회 결과: 이슈 제목은 `[Learning Core] Identity JWKS 기반 JWT 인증 연동`이고 현재 상태는 `진행 중`(상태 ID `10001`)이다. 사용 가능한 전환은 `해야 할 일`(전환 ID `11`, 대상 상태 ID `10000`), `검토 중`(전환 ID `31`, 대상 상태 ID `10002`), `진행 중`(전환 ID `21`, 대상 상태 ID `10001`, 현재 상태로의 self-transition), `완료`(전환 ID `41`, 대상 상태 ID `10003`)이며 모두 조회 응답에서 `isAvailable=true`였다.
- 변경한 동작: 애플리케이션 동작과 Jira 이슈 상태를 변경하지 않았다. Jira 상태 전환 API는 호출하지 않았다.
- 유지한 외부 계약: 공개 API URL·Method·Parameter·Request·Response DTO·BaseResponse, Python AI의 `user_id = examId`, Callback JSON, `retryCount`, Redis, S3, 음성 제출과 Polling 흐름을 변경하지 않았다.
- 실행한 테스트와 결과: Atlassian MCP의 이슈 상세 조회와 가능한 전환 조회가 성공했다. Rovo 통합 검색은 대상 인스턴스에 앱이 설치되지 않아 `403`을 반환했지만, 접근 가능한 Jira 리소스를 식별한 뒤 전용 읽기 API 조회는 성공했다. `git diff --check`도 성공했다. 애플리케이션 코드를 변경하지 않아 `./gradlew clean test`는 실행하지 않았다.
- 위험 요소: 가능한 전환은 조회 시점의 워크플로와 호출 사용자 권한에 따라 달라질 수 있다. Rovo 통합 검색은 현재 사용할 수 없지만 Jira 이슈 상세·전환 전용 API는 사용할 수 있다.
- 다음 작업 전에 확인할 사항: 실제 상태 변경이 필요해지면 전환 직전에 가능한 전환을 다시 조회하고, 사용자가 지정한 대상 상태를 확인한 뒤에만 전환한다.

## 2026-07-28 — Jira TMI-10 진행 중 전환

<!-- codex-turn:019fa635-ca81-73a0-ba14-0a6ee532ada3 -->

- 날짜: `2026-07-28`
- 브랜치: `main`
- Jira 이슈 키: `TMI-10`
- 작업 목표: `TMI-10`만 방금 확인한 `진행 중` 상태로 전환하고 다른 필드와 다른 이슈는 수정하지 않는다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`
- 변경한 동작: 전환 직전 `TMI-10`의 현재 상태와 가능한 전환을 다시 조회한 뒤, 다른 필드나 update payload 없이 전환 ID `21`만 전달했다. 이슈가 이미 `진행 중`이어서 현재 상태로의 self-transition이 실행됐으며, 후속 조회에서도 상태는 `진행 중`(상태 ID `10001`)으로 확인됐다. 다른 이슈에는 요청을 보내지 않았다.
- 유지한 외부 계약: 애플리케이션 코드와 공개 API URL·Method·Parameter·Request·Response DTO·BaseResponse, Python AI의 `user_id = examId`, Callback JSON, `retryCount`, Redis, S3, 음성 제출과 Polling 흐름을 변경하지 않았다.
- 실행한 테스트와 결과: Atlassian MCP로 전환 전 이슈 상태 및 전환 ID `21`의 사용 가능 여부를 확인했고, 전환 호출과 전환 후 이슈 상태 조회가 모두 성공했다. `git diff --check`도 성공했다. 애플리케이션 코드를 변경하지 않아 `./gradlew clean test`는 실행하지 않았다.
- 위험 요소: 동일 상태 self-transition이므로 보이는 상태명은 전환 전후 모두 `진행 중`이다. Jira는 상태 전환에 따른 이력과 `updated` 같은 시스템 관리 메타데이터를 자동 기록할 수 있지만, 다른 사용자 편집 필드는 전환 요청에 포함하지 않았다.
- 다음 작업 전에 확인할 사항: 추가 Jira 변경은 사용자의 별도 지시가 있을 때만 수행하고, 상태 변경 전에는 가능한 전환을 다시 조회한다.

## 2026-07-28 — Jira TMI-10 Identity JWKS 기반 JWT 인증 연동

<!-- codex-turn:019fa639-b135-7bc3-8557-57798961dc66 -->

- 날짜: `2026-07-28`
- 브랜치: `main`
- Jira 이슈 키: `TMI-10`
- 작업 목표: Learning Core에 Identity JWKS 기반 OAuth2 Resource Server를 연동하되 기본 Legacy 호환, 시험 소유권, Callback 매핑, Python AI와 기존 공개 API 계약을 유지한다.
- Jira 기준: Atlassian MCP 전용 Jira 읽기 API로 `TMI-10`의 설명과 완료 조건을 조회해 구현 기준으로 사용했다. 이슈 상태는 `진행 중`이며 이번 작업에서는 Jira 댓글이나 상태를 변경하지 않았다. Rovo 통합 검색은 대상 인스턴스에 앱이 없어 403이었지만 Jira 상세 직접 조회는 성공했다.
- 변경 파일: `build.gradle`, `src/main/resources/application.yml`, `SecurityConfig.java`, `CurrentUserProvider` 구현체인 `LegacyCurrentUserProvider.java`와 신규 `JwtCurrentUserProvider.java`, `JwtTokenProvider.java`, 신규 `JwtAudienceValidator.java`, `JwtSubjectValidator.java`, `SecurityErrorResponseHandler.java`, 신규 `JwtCurrentUserProviderTest.java`, `LegacySecurityIntegrationTest.java`, `JwtSecurityIntegrationTest.java`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- Legacy/JWT 구성: `APP_AUTH_MODE` 기본값을 `legacy`로 두고 Legacy에서는 `LegacyCurrentUserProvider`와 전체 `permitAll`만 활성화한다. `APP_AUTH_MODE=jwt`에서만 `JwtCurrentUserProvider`, `JwtDecoder`, OAuth2 Resource Server와 사용자 API `authenticated` 정책을 활성화한다. Legacy에서는 Identity 서버와 Authorization 헤더 없이 기존 요청이 동작한다.
- JwtDecoder와 Validator: `IDENTITY_JWK_SET_URI`를 직접 사용하는 `NimbusJwtDecoder`를 구성해 OIDC discovery 없이 RS256만 허용했다. Spring 기본 issuer·timestamp Validator로 issuer, exp, nbf를 확인하고, 별도 audience Validator와 UUID subject Validator를 결합했다.
- 현재 사용자 처리: `JwtCurrentUserProvider`는 `SecurityContext`의 `JwtAuthenticationToken` 또는 JWT principal에서 `sub`를 읽고 `UUID.fromString`으로 검증·정규화해 실제 `userId`로 반환한다. 시험 생성은 이 값을 기존 흐름대로 `ExamSession.userId`에 저장하고 사용자용 examId API는 기존 소유권 검증을 계속 사용한다.
- 공개/보호 정책: JWT 모드에서 `/api/v1/exams/callback/**`, Swagger UI, OpenAPI, health 경로는 공개하고 그 밖의 요청은 authenticated로 설정했다. 인증 실패와 Security 접근 거부는 기존 `BaseResponse`와 `COMMON401`/`COMMON403` 구조로 JSON 응답한다. 비즈니스 소유권 불일치도 기존 `BaseResponse` 403 흐름을 유지한다.
- 유지한 외부 계약: Controller URL·HTTP Method·Path/Query/Request Body·Response DTO를 변경하지 않았다. 실제 `userId`를 외부 Request/Response에 추가하지 않았고, Python AI multipart와 summary 요청의 `user_id = examId`, Callback `user_id = examId`, Callback URL·JSON, `retryCount`, Redis Key·TTL, S3 Presigned URL·Object Key, 음성 제출·Polling 흐름을 유지했다.
- 테스트와 결과: 테스트 프로세스 내부의 임시 JWKS HTTP endpoint와 합성 RSA 키를 사용하고 MongoDB·Redis·AWS S3·Python AI 서버는 Mock 처리했다. 기본 Legacy, 무인증 Legacy 접근, JWT 무토큰 401, 유효 RS256 시험 생성, `sub -> ExamSession.userId`, 동일 사용자 200, 다른 사용자 403, 잘못된 서명·만료·미래 nbf·issuer·audience·비 UUID sub 401, 무인증 AI Callback, 외부 userId 미노출을 검증했다. `./gradlew clean test`는 53개 테스트 모두 성공했고 실패·오류·건너뜀은 0개였다. `git diff --check`도 성공했다.
- 기존 HMAC 상태: `JwtAuthenticationFilter`, `JwtTokenProvider`, `jwt.secret` 설정과 JJWT 의존성은 삭제하지 않았다. 다만 `JwtTokenProvider` 자동 등록과 Security Filter 연결을 제거해 현재 Legacy/JWT 어느 모드에서도 HMAC Filter는 사용되지 않으며 JWT 모드에는 표준 Resource Server Filter만 적용된다. 최종 삭제는 별도 후속 정리가 필요하다.
- 위험 요소: Jira 완료 조건에 포함된 실제 Identity 프로세스와 Learning Core의 로컬 E2E는 외부 서버를 호출하지 말라는 이번 테스트 조건에 따라 수행하지 않았고 테스트용 JWKS 경계 통합 검증으로 대체했다. 배포 전 실제 issuer·JWKS·audience와 네트워크, key rotation·캐시 동작을 확인해야 한다. AI Callback 서비스 간 인증은 범위 밖이라 공개 상태다. Nimbus 기본 clock skew가 exp·nbf 경계에 적용된다.
- 다음 작업 전에 확인할 사항: 실제 Identity 발급 Token으로 로컬 E2E를 완료하고, 배포 환경의 `APP_AUTH_MODE=jwt` 설정과 JWKS 접근성을 검증한다. HMAC 코드·`JWT_SECRET_KEY`·JJWT 의존성 제거는 별도 범위로 결정하며 Jira 댓글·상태 변경과 Git commit·push는 사용자가 수행한다.

## 2026-07-28 — Jira TMI-10 테스트·PR 댓글 및 완료 처리

<!-- codex-turn:019fa66e-77b9-7f11-9ff6-4cb64478e18a -->

- 날짜: `2026-07-28`
- 브랜치: `feat/TMI-10-identity-jwt-integration`
- Jira 이슈 키: `TMI-10`
- 작업 목표: TMI-10에 검증된 테스트 결과와 실제 PR을 댓글로 남기고, 직전에 조회한 Jira 워크플로의 완료 전환만 실행한다.
- PR 확인: GitHub PR [#8 TMI-10 feat: integrate Identity JWT authentication](https://github.com/Too-Much-I/app-back-end-learning-core/pull/8)이 `MERGED` 상태이고 head는 `feat/TMI-10-identity-jwt-integration`, base는 `main`, CodeRabbit 체크는 `SUCCESS`임을 읽기 조회로 확인했다.
- Jira 댓글: PR #8 링크, `./gradlew clean test` 53개 성공·실패 0·오류 0·건너뜀 0, `git diff --check` 성공, Legacy/JWT·JWKS·소유권·401/403·Callback·AI 계약 검증 결과를 TMI-10 댓글 ID `10001`로 등록했다. 실제 Identity 프로세스 E2E 미실행과 HMAC 후속 정리 필요성도 함께 기록했다.
- Jira 전환: 실행 직전 TMI-10이 `진행 중` 상태 ID `10001`이고 `완료` 전환 ID `41`이 `isAvailable=true`임을 재조회했다. 댓글 등록 성공 후 전환 ID `41`만 실행했고, 후속 조회에서 상태 `완료` ID `10003`과 resolution `완료` ID `10000`을 확인했다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. 애플리케이션 코드와 설정은 변경하지 않았다.
- 테스트와 결과: 이번 Jira 행정 작업에서는 코드를 변경하지 않아 테스트를 다시 실행하지 않았다. Jira 댓글에는 직전 TMI-10 구현 작업에서 실제 실행해 확인한 `./gradlew clean test` 53개 전체 성공 결과를 사용했다. GitHub PR 상태·체크, Jira 댓글 응답, 완료 전환과 최종 상태 재조회가 모두 성공했다.
- 유지한 외부 계약: 공개 API URL·Method·Parameter·Request·Response DTO·BaseResponse, Python AI `user_id = examId`, Callback JSON, `retryCount`, Redis, S3, 음성 제출·Polling 흐름을 변경하지 않았다. Jira TMI-10 외 다른 이슈와 다른 필드는 수정하지 않았다.
- 위험 요소: TMI-10은 사용자 요청에 따라 완료됐지만 실제 Identity 프로세스를 기동한 로컬 E2E는 수행하지 않았다. 테스트용 JWKS endpoint 경계 통합 검증으로 대체했으며, 실제 issuer·JWKS 네트워크와 key rotation·캐시 동작은 배포 전 별도 확인이 필요하다.
- 다음 작업 전에 확인할 사항: 실제 Identity E2E와 HMAC/JJWT 정리는 별도 Jira 이슈로 추적한다. Git commit과 push는 사용자가 수행한다.

## 2026-07-28 — 문항 피드백 최신 결과 조회 여부 분석

<!-- codex-turn:019fa673-c7ec-7f42-b59c-e2bb6406289f -->

- 날짜: `2026-07-28`
- 브랜치: `feat/TMI-10-identity-jwt-integration`
- Jira 이슈 키: `TMI-10` (완료 상태의 관련 코드 분석 맥락)
- 작업 목표: 현재 문항별 피드백 조회가 같은 문항·회차에서 가장 최신의 저장 결과를 보장하는지 코드와 관련 변경 이력을 확인한다.
- 분석 결과: `getExamQuestion`은 Azure 결과에 대해서는 `findFirstByExamIdAndQuestionNumberAndRetryCountOrderByIdDesc`를 사용하므로 요청된 retryCount 안에서 최신 `_id` 문서를 선택한다. 반면 AI 문항 피드백 `ExamResult`는 정렬 조건이 없는 `findByExamId` 전체 결과를 Java Stream으로 필터링한 뒤 `findFirst()`를 사용하므로 동일 `examId + questionNumber + retryCount` 문서가 둘 이상이면 최신 결과가 보장되지 않는다.
- retryCount 의미: 문항 피드백 API는 클라이언트가 전달한 retryCount를 그대로 조회한다. 가장 큰 retryCount를 자동 선택하지 않으며, `totalRetryCount`는 응답용 누적 횟수 계산에만 사용한다.
- 추가 발견: 종합 피드백도 정렬 없는 `findByExamId` 결과에서 `totalScore != null`인 첫 문서를 선택하므로 중복 summary 문서의 최신성을 보장하지 않는다. `ExamResult`에는 별도 `createdAt` 필드가 없다.
- 변경 이력 확인: 커밋 `2d4381a`의 “최근 로직만 조회” 수정은 Azure Repository와 Azure 조회 호출에만 `OrderByIdDesc`를 추가했으며 AI `ExamResult` 선택 로직은 변경하지 않았다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. 애플리케이션 코드는 변경하지 않았다.
- 테스트와 결과: 읽기 전용 분석이며 코드 변경이 없어 테스트를 실행하지 않았다. 기존 문항 조회 테스트는 단일 `ExamResult`만 사용하므로 중복 결과 중 최신 문서를 선택하는 동작을 검증하지 않는다.
- 유지한 외부 계약: 공개 API URL·Method·Parameter·Request·Response DTO·BaseResponse, Python AI `user_id = examId`, Callback JSON, `retryCount`, Redis와 S3 흐름을 변경하지 않았다. Jira 상태와 댓글도 변경하지 않았다.
- 위험 요소: AI Callback이 같은 문항·retryCount 결과를 중복 저장하면 MongoDB의 정렬되지 않은 반환 순서에 따라 과거 피드백이 선택될 수 있다. 클라이언트가 최신 retryCount를 전달하더라도 해당 회차 안의 중복 문서 문제는 남는다.
- 다음 작업 전에 확인할 사항: 최신 결과 보장이 요구되면 `ExamResultRepository`에 동일 식별 조건과 명시적 내림차순 정렬을 사용하는 단건 조회를 추가하고, 같은 회차의 신규·구 문서를 함께 둔 테스트를 작성한다. 종합 피드백의 최신 선택도 같은 범위에 포함할지 결정한다.

## 2026-07-28 — AI 문항 피드백 최신 결과 조회 보장

<!-- codex-turn:019fa677-b2df-7c41-9934-5852f01cbedb -->

- 날짜: `2026-07-28`
- 브랜치: `feat/TMI-10-identity-jwt-integration`
- Jira 이슈 키: `TMI-10` (완료 후 후속 보완)
- 작업 목표: 문항별 피드백 API가 클라이언트가 요청한 examId·questionNumber·retryCount 범위에서 가장 최근에 저장된 AI `ExamResult`를 반환하도록 수정한다.
- 변경 파일: `ExamServiceImpl.java`, `ExamResultRepository.java`, `ExamOwnershipServiceTest.java`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 구현 내용: `ExamResultRepository`에 `findFirstByExamIdAndQuestionNumberAndRetryCountInOrderByIdDesc` 단건 조회를 추가했다. `getExamQuestion`의 정렬 없는 전체 결과 Stream `findFirst()` 선택을 제거하고 Repository가 `_id` 내림차순으로 반환한 최신 문서를 사용한다.
- Legacy 데이터 호환: 기존 로직이 null retryCount를 0회차로 해석하던 동작을 유지하기 위해 0회차 요청은 retryCount 조건 `[0, null]`을 사용하고, 1회차 이상은 요청된 retryCount만 조회한다.
- retryCount 계약: 클라이언트가 전달한 retryCount 회차를 조회하는 기존 API 의미를 유지했다. 가장 큰 retryCount를 자동 선택하거나 attemptId를 추가하지 않았다.
- 테스트 추가: 같은 문항·retryCount에 과거 점수와 최신 점수가 함께 있는 상황에서 최신 점수가 응답되는지 검증했다. 기존 0회차 문항 조회 테스트에서는 `[0, null]` 조회 조건도 확인했다.
- 실행한 테스트와 결과: 집중 `ExamOwnershipServiceTest` 성공 후 `./gradlew clean test`를 최종 재실행해 54개 테스트 모두 성공했고 실패·오류·건너뜀은 0개였다. 기존 `ExamServiceImpl`의 unchecked 경고만 남았으며 이번 변경과 무관하다. `git diff --check`도 성공했다.
- 유지한 외부 계약: 공개 API URL·HTTP Method·Path/Query Parameter·Request/Response DTO·BaseResponse를 변경하지 않았다. Python AI `user_id = examId`, Callback JSON, 기존 retryCount, Redis Key·TTL, S3 Object Key·Presigned URL, 음성 제출·Polling 흐름을 유지했다.
- Jira 변경: 완료 상태인 TMI-10의 댓글·상태·resolution을 변경하지 않았다.
- 남은 위험 요소: 최신 문항 AI 피드백과 Azure 피드백은 보장되지만 종합 summary는 여전히 정렬 없는 첫 summary 문서를 선택한다. MongoDB가 자동 생성하는 `_id`의 내림차순을 저장 최신성 기준으로 사용한다.
- 다음 작업 전에 확인할 사항: 종합 피드백 최신 선택과 중복 문항 결과의 장기 보관·정리 정책이 필요하면 별도 Jira 범위로 결정한다. Git commit과 push는 사용자가 수행한다.

## 2026-07-28 — 종합 피드백 저장소 분리 및 최신 결과 조회

<!-- codex-turn:019fa680-0c1c-7751-a5e7-345c24f5a904 -->

- 날짜: `2026-07-28`
- 브랜치: `feat/TMI-10-identity-jwt-integration`
- Jira 이슈 키: `TMI-10` (완료 후 후속 보완)
- 작업 목표: 종합 피드백을 문항별 피드백과 분리해 저장하고, 종합 피드백 API가 해당 시험의 가장 최근 저장 결과를 반환하도록 수정한다.
- 저장소 분리: 기존 MongoDB 연결과 database 설정은 유지하면서 신규 `ExamSummary`를 `exam_summaries` 컬렉션에 저장한다. `totalScore`가 있는 AI Callback은 Redis 상태를 기존과 동일하게 `COMPLETED`로 갱신한 뒤 `ExamSummaryRepository`에만 저장하며 `ExamResultRepository`에는 저장하지 않는다. 문항 Callback은 기존 `exam_results` 저장과 11번 문항의 종합 요청 트리거를 유지한다.
- 최신 조회: `ExamSummaryRepository.findFirstByExamIdOrderByIdDesc`로 신규 종합 피드백의 최신 `_id` 문서를 조회한다. 새 컬렉션에 문서가 없는 기존 시험은 `ExamResultRepository.findFirstByExamIdAndTotalScoreIsNotNullOrderByIdDesc`로 분리 전 legacy 종합 문서 중 최신 건을 반환한다. 파트 점수와 풀이 문항 수는 계속 문항별 `ExamResult`에서 계산한다.
- 변경 파일: 신규 `ExamSummary.java`, `ExamSummaryRepository.java`; 수정 `ExamServiceImpl.java`, `ExamConverter.java`, `ExamResult.java`, `ExamResultRepository.java`, `FeedbackCallbackServiceTest.java`, `ExamOwnershipServiceTest.java`, `ExamServiceImplTest.java`, `JwtSecurityIntegrationTest.java`, `LegacySecurityIntegrationTest.java`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 테스트 추가: 종합 Callback의 별도 컬렉션 저장과 문항 컬렉션 미저장, 실제 `ExamSession.userId` 보존, Redis 완료 전환, 문항 Callback의 기존 컬렉션 유지, 신규 종합 컬렉션 최신 조회, 신규 컬렉션 미존재 시 legacy 최신 종합 문서 fallback을 검증했다.
- 실행한 테스트와 결과: 집중 `FeedbackCallbackServiceTest`와 `ExamOwnershipServiceTest` 성공 후 `./gradlew clean test`를 실행해 56개 테스트 모두 성공했고 실패·오류·건너뜀은 0개였다. 실제 Identity·Atlas·AWS·Redis·Python AI 서버는 호출하지 않았다. 기존 `ExamServiceImpl` unchecked 경고만 남았으며 이번 작업과 무관하다.
- 유지한 외부 계약: 공개 API URL·HTTP Method·Path/Query Parameter·Request/Response DTO·`BaseResponse`를 변경하지 않았다. Python AI 요청과 Callback의 `user_id = examId`, Callback URL·JSON, `retryCount`, Redis Key·TTL, S3 Object Key·Presigned URL, 소유권 검증, 음성 제출·Polling 흐름을 유지했다. 내부 실제 `userId`는 외부 응답에 노출하지 않았다.
- Jira 변경: 완료 상태인 TMI-10의 댓글·상태·resolution을 변경하지 않았다. Git commit과 push도 수행하지 않았다.
- 남은 위험 요소: 최신성 판단은 MongoDB 자동 생성 `_id` 내림차순 기준이다. 기존 `exam_results` 종합 문서는 삭제·이관하지 않고 fallback으로 유지한다. 현재 분리는 같은 MongoDB database 안의 별도 컬렉션이며 물리적으로 다른 database나 클러스터가 필요하면 별도 연결·자격증명·배포 설정이 필요하다. 대량 데이터 환경에서는 `examId + _id` 복합 인덱스를 검토해야 한다.
- 다음 작업 전에 확인할 사항: 물리 database 분리 필요 여부와 legacy 종합 데이터의 이관·보존 정책, 운영 데이터 규모에 따른 조회 인덱스를 결정한다. Git commit과 push는 사용자가 수행한다.

## 2026-07-28 — 최신 피드백 변경분용 새 Git 브랜치 준비

<!-- codex-turn:019fa68c-a2df-7fc1-91a1-efb5385c2076 -->

- 날짜: `2026-07-28`
- 브랜치: `fix/TMI-10-latest-feedback`
- Jira 이슈 키: `TMI-10` (완료 후 후속 보완)
- 작업 목표: 문항·종합 피드백 최신 조회 및 종합 저장소 분리 변경분을 새 브랜치로 옮겨 GitHub에 올릴 수 있도록 준비한다. Jira와 다른 외부 시스템은 변경하지 않는다.
- 수행 결과: 기존 `feat/TMI-10-identity-jwt-integration`에서 로컬 브랜치 `fix/TMI-10-latest-feedback`을 새로 생성하고 모든 working tree 변경분을 그대로 보존했다.
- GitHub 반영 상태: 저장소 `AGENTS.md`는 Codex의 `git commit`과 `git push` 직접 실행을 금지하므로 commit과 원격 push는 수행하지 않았다. 따라서 원격 GitHub에는 아직 이 브랜치와 변경분이 생성되지 않았다.
- 변경 파일: 이번 요청에서 애플리케이션 코드는 추가로 변경하지 않았고, 브랜치 상태를 반영하기 위해 `docs/codex/WORKLOG.md`와 `docs/codex/CURRENT_STATE.md`만 갱신했다.
- 실행한 테스트와 결과: 이번 요청은 브랜치 생성과 기록 작업이며 애플리케이션 코드가 추가 변경되지 않아 테스트를 다시 실행하지 않았다. 직전 구현 검증의 `./gradlew clean test` 56개 전체 성공 결과를 유지한다.
- 유지한 외부 계약: 공개 API URL·Method·Parameter·DTO·`BaseResponse`, Python AI와 Callback의 `user_id = examId`, `retryCount`, Redis, S3, 소유권 검증 및 인증 구성을 변경하지 않았다. Jira TMI-10의 댓글·상태·resolution도 변경하지 않았다.
- 남은 위험 요소: 변경분은 아직 commit되지 않은 working tree 상태이므로 원격 백업과 PR 생성이 되지 않았다.
- 다음 작업 전에 확인할 사항: 사용자가 변경 파일을 검토한 뒤 commit하고 `git push -u origin fix/TMI-10-latest-feedback`을 실행한다.

## 2026-07-28 — Identity·Learning Core E2E 인증 통합 테스트 Jira 초안

<!-- codex-turn:019fa6bf-9fba-7771-ba07-ef3fad00bc27 -->

- 날짜: `2026-07-28`
- 브랜치: `main`
- Jira 이슈 키: 없음 (초안만 작성했으며 Jira 이슈를 생성하지 않음)
- 작업 목표: Atlassian MCP로 TMI 프로젝트의 생성 메타데이터를 확인하고 `[Integration] Identity·Learning Core E2E 인증 테스트 및 JWT 계약 확정` 작업 이슈의 최종 전송 Payload를 작성한다.
- Atlassian 확인 결과: TMI 프로젝트(ID `10000`)에 현재 사용자 기준 이슈 생성 권한이 있고, `작업` 유형(ID `10003`)과 설명 필드를 사용할 수 있다. 우선순위 필드는 선택 사항이며 `High`(ID `2`)가 허용되어 제안 Payload에 포함했다.
- 초안 범위: Identity 회원가입·로그인·Token 발급·사용자 조회, Learning Core 401/403·시험 생성·소유권 격리, 잘못된 서명·만료·issuer·audience, Refresh Token Rotation·로그아웃, 공개 AI Callback, Python AI `user_id = examId`와 실제 `userId` 비노출 계약을 포함했다.
- 변경한 동작: Jira 생성·수정·댓글·상태 전환 API를 호출하지 않았다. 애플리케이션 코드와 설정도 변경하지 않았다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 유지한 외부 계약: 공개 API URL·Method·Parameter·Request·Response DTO·`BaseResponse`, Python AI와 Callback의 `user_id = examId`, 실제 `userId` 비노출, `retryCount`, Redis, S3, 음성 제출·Polling 흐름을 변경하지 않았다.
- 실행한 테스트와 결과: Atlassian MCP의 접근 가능 리소스, 생성 가능한 TMI 프로젝트, 프로젝트 이슈 유형, `작업` 생성 필드 조회가 성공했고 `git diff --check`도 성공했다. 애플리케이션 코드 변경이 없어 `./gradlew clean test`는 실행하지 않았다.
- 남아 있는 위험 요소: Jira 프로젝트 설정과 생성 필드는 실제 생성 시점 전에 변경될 수 있다. 초안 단계이므로 아직 이슈 키와 워크플로 상태가 없다.
- 다음 작업 전에 확인할 사항: 사용자가 최종 Payload를 검토하고 명시적으로 생성을 요청하면 생성 직전 TMI `작업` 메타데이터와 `High` 지원 여부를 다시 확인하고, 승인된 필드만 전송한다.

## 2026-07-28 — Jira TMI-11 E2E 인증 통합 테스트 이슈 생성

- 날짜: `2026-07-28`
- 브랜치: `main`
- Jira 이슈 키: `TMI-11`
- 작업 목표: 승인된 최종 Payload로 TMI 프로젝트에 Identity·Learning Core E2E 인증 통합 테스트 `작업` 이슈를 한 건 생성하고 반영 결과를 검증한다.
- 생성 전 확인: 현재 사용자에게 TMI 이슈 생성 권한이 있고 `작업` 유형(ID `10003`)과 `High` 우선순위(ID `2`)가 계속 지원되는지 재조회했다. 동일 프로젝트에서 `E2E 인증 테스트` 문구가 포함된 기존 이슈가 없는 것도 확인했다.
- 생성 결과: [`TMI-11`](https://to-teacher.atlassian.net/browse/TMI-11)을 제목 `[Integration] Identity·Learning Core E2E 인증 테스트 및 JWT 계약 확정`, 유형 `작업`, 우선순위 `High`로 생성했다. 초기 상태는 `해야 할 일`(상태 ID `10000`), resolution은 없음, 담당자는 미지정이다.
- 설명 반영: 승인된 배경, 검증 범위 20개, JWT 계약, 구현 결과물, 완료 조건, 범위 제외를 Markdown 설명으로 전송했다. 민감한 Token 원문, RSA Private Key, MongoDB URI나 사용자 개인정보는 기록하지 않았다.
- 생성 후 검증: 이슈를 다시 조회해 프로젝트 `TMI`, 제목, 설명, 유형 `작업`, 상태 `해야 할 일`, 우선순위 `High`가 전송 Payload와 일치함을 확인했다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. 애플리케이션 코드와 설정은 변경하지 않았다.
- 유지한 외부 계약: 공개 API URL·Method·Parameter·Request·Response DTO·`BaseResponse`, Python AI와 Callback의 `user_id = examId`, 실제 `userId` 비노출, `retryCount`, Redis, S3, 음성 제출·Polling 흐름을 변경하지 않았다.
- 실행한 테스트와 결과: Atlassian MCP의 생성 권한·메타데이터·중복 후보 조회, 이슈 생성, 생성 후 상세 재조회가 모두 성공했고 `git diff --check`도 성공했다. 애플리케이션 코드 변경이 없어 `./gradlew clean test`는 실행하지 않았다.
- 남아 있는 위험 요소: 이슈 생성만 완료됐으며 실제 Identity·Learning Core E2E 구현과 실행은 아직 시작하지 않았다. 담당자가 지정되지 않았다.
- 다음 작업 전에 확인할 사항: `TMI-11`을 구현 기준으로 사용할 때 Identity와 Learning Core 두 저장소의 현재 브랜치·실행 환경·테스트 데이터 정리 방식을 확인하고, 상태 변경이나 담당자 지정은 별도 지시에 따라 수행한다.

## 2026-07-28 — Jira TMI-11 생성 작업 Stop Hook 기록 보완

<!-- codex-turn:019fa6c5-05d6-7dc0-88ce-767be4266c3c -->

- 날짜: `2026-07-28`
- 브랜치: `main`
- Jira 이슈 키: `TMI-11`
- 작업 목표: 현재 turn의 Jira 이슈 생성 결과가 전용 marker와 함께 WORKLOG 끝에 기록되도록 보완하고 CURRENT_STATE를 최신화한다.
- 작업 결과: 앞서 생성·재조회한 [`TMI-11`](https://to-teacher.atlassian.net/browse/TMI-11)의 제목, 유형 `작업`, 상태 `해야 할 일`, 우선순위 `High`, 담당자 미지정 상태를 기록했다. 후크 대응 중 Jira 생성·수정·댓글·상태 전환 API는 추가로 호출하지 않았다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. 애플리케이션 코드와 설정은 변경하지 않았다.
- 유지한 외부 계약: 공개 API URL·Method·Parameter·Request·Response DTO·`BaseResponse`, Python AI와 Callback의 `user_id = examId`, 실제 `userId` 비노출, `retryCount`, Redis, S3, 음성 제출·Polling 흐름을 변경하지 않았다.
- 실행한 검증과 결과: 새 marker가 WORKLOG에 정확히 한 번 존재함을 확인했고 `git diff --check`도 성공했다. 애플리케이션 코드 변경이 없어 `./gradlew clean test`는 실행하지 않았다.
- 남아 있는 위험 요소: `TMI-11`의 실제 Identity·Learning Core E2E 구현·실행은 아직 시작하지 않았고 담당자가 지정되지 않았다.
- 다음 작업 전에 확인할 사항: 후속 구현 전 두 저장소의 현재 브랜치·로컬 실행 환경·테스트 계정 생성 및 정리 방식을 확인한다.

## 2026-07-28 — Jira TMI-11 Identity·Learning Core JWT E2E 자동화

<!-- codex-turn:019fa6c9-cf9f-7c40-b480-5bd85b70d947 -->

- 날짜: `2026-07-28`
- 브랜치: `test/TMI-11-auth-e2e`
- Jira: TMI-11
- 작업 목표: 실제 실행 중인 Identity와 JWT 모드 Learning Core 사이의 인증 계약을 한 번에 검증하는 로컬 E2E 스크립트와 실행·계약 문서를 추가한다.
- Jira 확인: Atlassian MCP로 `TMI-11`의 설명, 검증 범위 20개, JWT 계약과 완료 조건을 재조회해 구현 기준으로 사용했다. 이슈는 `해야 할 일`, High, 담당자 미지정 상태이며 Jira 댓글·필드·상태는 변경하지 않았다.
- 변경 파일: `scripts/e2e/auth-integration-test.sh`, `scripts/e2e/README.md`, `docs/contracts/identity-learning-jwt.md`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 자동화한 동작: Identity health와 공개 JWKS 구조, 첫·두 번째 사용자 회원가입/로그인, `/users/me`, JWT Header·필수 Claim, Learning Core 무토큰 401, 시험 생성, 동일 사용자 조회, 다른 사용자 403, 임의·변조·누락 Token 401, Refresh Rotation·재사용 탐지, 단일·전체 로그아웃, 무인증 Feedback Callback 도달을 검증한다.
- 안전성 및 정리: 민감한 인증값과 URL을 포함할 수 있는 전체 응답을 출력하지 않고, 실패 시 단계명·HTTP 상태·최상위 안전 필드만 출력한다. 제한된 임시 디렉터리와 `trap` 정리를 사용하며 기본 모드에서는 남은 Refresh Session을 로그아웃한다. 사용자·시험 삭제 API가 없어 계정과 시험 문서는 자동 삭제하지 않는다.
- JWT 계약: RS256, 필수 `kid`, UUID 실제 사용자 `sub`, 환경별 issuer, audience `tosunsaeng-learning-core`, 공백 구분 `scope`, Identity JWKS 기반 Learning Core 로컬 검증을 문서화했다. 클라이언트 요청·응답에 실제 사용자 식별자를 추가하지 않았고 Python AI 및 Callback의 `user_id = examId`를 유지했다. 운영 앱의 Legacy 모드를 금지하고 로그아웃이 기존 Access Token 즉시 무효화를 보장하지 않음을 명시했다.
- 정적 검증: `bash -n scripts/e2e/auth-integration-test.sh`, JWKS·Claim jq filter 샘플 검증, 비대화형 환경의 비밀번호 누락 시 명확한 exit code 2 확인, `git diff --check`가 성공했다. ShellCheck는 설치돼 있지 않아 자동 설치하지 않았다.
- 자동 테스트: Learning Core `./gradlew clean test`는 56개, Identity `./gradlew clean test`는 138개가 모두 성공했고 두 실행 모두 실패·오류·건너뜀 0개였다. Identity 소스와 추적 파일은 변경하지 않았다. Learning Core의 기존 unchecked 경고는 이번 범위와 무관해 수정하지 않았다.
- E2E 실행 결과: 기본 Identity 8081과 Learning Core 8080 포트 모두 연결되지 않아 실제 두 서버 E2E는 실행하지 않았다. 스크립트 생성과 정적 검증까지만 완료했다.
- 유지한 외부 계약: 기존 공개 API URL·Method·Path/Query Parameter·Request/Response DTO·`BaseResponse`, `retryCount`, S3 Presigned URL·Object Key, 음성 제출·Polling, Redis 상태·Lock, AI 요청과 Callback URL·JSON을 변경하지 않았다. Learning Core Java 기능과 Identity 코드는 수정하지 않았다.
- 남아 있는 위험 요소: 실제 두 서버 버전의 응답과 네트워크를 통한 E2E 결과가 없고, JWKS rotation·캐시는 검증 범위 밖이다. 공개 AI Callback에는 서비스 간 인증이 없다. `ExamSession.userId`와 JWT `sub` 직접 비교는 자격증명을 자동화에 넣지 않기 위해 수동 항목이며, 로컬 E2E 계정과 시험 문서는 운영자가 정리해야 한다.
- 다음 작업 전에 확인할 사항: Identity 8081과 JWT 모드 Learning Core 8080 및 필요한 개발 인프라를 기동해 스크립트를 실행하고, 폐기 가능한 로컬 DB에서 `exam_sessions.userId`를 수동 확인한다. 실제 E2E 성공 뒤 Jira 완료 댓글 초안을 검토하되 댓글·상태 변경과 Git commit·push는 사용자가 수행한다.

## 2026-07-28 — Jira TMI-11 완료 처리

<!-- codex-turn:019fa6e1-d7cf-7a20-8a98-f7fd99103156 -->

- 날짜: `2026-07-28`
- 브랜치: `test/TMI-11-auth-e2e`
- Jira: TMI-11
- 작업 목표: 사용자 승인에 따라 TMI-11 구현·검증 결과와 남은 위험을 Jira에 정리하고 이슈를 완료 처리한다.
- 완료 전 확인: 이슈가 `해야 할 일`(상태 ID `10000`, resolution 없음)이었고 `완료` 전환 ID `41`이 사용 가능한 상태임을 Atlassian MCP로 확인했다.
- Jira 댓글: 구현 파일, E2E 자동화 범위, 확정 JWT 계약, Learning Core 56개·Identity 138개 전체 테스트 성공, ShellCheck 미설치, 실제 두 서버 E2E 미실행, 수동 `ExamSession.userId == JWT sub` 확인 필요와 알려진 위험을 댓글 ID `10002`로 기록했다.
- Jira 상태: 완료 전환 ID `41`을 실행한 뒤 상태 `완료`(ID `10003`)와 resolution `완료`(ID `10000`), 댓글 ID `10002` 반영을 재조회해 확인했다.
- Git 상태: 사용자 작업으로 HEAD `804e9a7`이 `origin/test/TMI-11-auth-e2e`와 동일하며 `origin/main`보다 1 commit 앞서 있다. 이번 Jira 처리에서 Codex는 commit과 push를 수행하지 않았다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. 애플리케이션 코드, E2E 스크립트, Identity 저장소는 변경하지 않았다.
- 실행한 검증: Jira 상태·resolution·댓글을 재조회했다. 애플리케이션 코드 변경이 없어 Gradle 테스트를 다시 실행하지 않았으며 직전 Learning Core 56개와 Identity 138개 전체 성공 결과를 Jira에 기록했다.
- 유지한 계약: 공개 API URL·Method·Parameter·DTO·`BaseResponse`, Python AI와 Callback의 `user_id = examId`, 실제 `userId` 비노출, Redis·S3·음성 제출·Polling·JWT 계약을 변경하지 않았다. 민감한 인증값이나 인프라 자격증명을 Jira 또는 작업 기록에 남기지 않았다.
- 남아 있는 위험 요소: 완료 댓글에 명시한 대로 실제 Identity 8081·Learning Core 8080 E2E와 직접 DB 비교는 아직 수행하지 않았다. AI Callback 서비스 간 인증과 JWKS rotation·캐시 검증도 후속 범위다.
- 다음 작업 전에 확인할 사항: 실제 서버 E2E와 폐기 가능한 로컬 DB 수동 검증에서 문제가 발견되면 TMI-11을 다시 열거나 별도 후속 이슈로 추적한다. 현재 기록 변경의 commit과 push는 사용자가 수행한다.

## 2026-07-28 — Learning Core 운영 JWT 보안 정리 Jira 초안

<!-- codex-turn:019fa6e7-f8eb-7221-8ab1-08c87480adce -->

- 날짜: `2026-07-28`
- 브랜치: `test/TMI-11-auth-e2e`
- Jira 이슈 키: 없음 (초안만 작성했으며 Jira 이슈를 생성하지 않음)
- 작업 목표: Atlassian MCP로 TMI 프로젝트의 생성 메타데이터를 확인하고 `[Learning Core] 운영 JWT 모드 강제 및 Legacy/HMAC 인증 정리` 작업 이슈의 최종 전송 Payload를 작성한다.
- Atlassian 확인 결과: 현재 사용자에게 TMI 프로젝트(ID `10000`)의 이슈 생성 권한이 있고, `작업` 유형(ID `10003`)과 설명 필드를 사용할 수 있다. 우선순위 필드는 선택 사항이며 `High`(ID `2`)가 허용되어 제안 Payload에 포함했다.
- 초안 범위: 운영·스테이징에서 JWT 모드 강제, Legacy Provider 활성화 차단, 인증 모드 값과 Identity issuer·JWKS URL·audience 검증, 로컬·테스트 전용 Legacy 허용, 기존 HMAC 코드·Secret 설정·JJWT 의존성의 사용 여부 분석과 미사용 항목 제거, 문서·회귀 테스트·작업 기록 갱신을 포함했다.
- 변경한 동작: Jira 생성·수정·댓글·상태 전환 API를 호출하지 않았고 애플리케이션 코드와 설정도 변경하지 않았다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 유지한 외부 계약: 기존 API URL·HTTP Method·Request/Response DTO·`BaseResponse`, `retryCount`, Redis Key·TTL, S3 Object Key, Python AI와 Callback의 `user_id = examId`, Feedback Callback의 `examId -> ExamSession -> 실제 userId`, 시험 소유권 검증을 변경하지 않았다.
- 실행한 검증과 결과: Atlassian MCP의 접근 가능 리소스, 생성 가능한 TMI 프로젝트, 프로젝트 이슈 유형과 `작업` 생성 필드 조회가 성공했고 `High` 지원을 확인했다. 필수 작업 marker가 WORKLOG에 정확히 한 번 존재하고 `git diff --check`가 성공했다. 애플리케이션 코드 변경이 없어 `./gradlew clean test`는 실행하지 않았다.
- 남아 있는 위험 요소: Jira 프로젝트 설정과 생성 필드는 실제 생성 시점 전에 변경될 수 있다. 초안 단계이므로 이슈 키·상태·담당자가 없고, HMAC 코드와 JJWT의 실제 제거 가능 여부는 구현 시 저장소 전수 분석이 필요하다.
- 다음 작업 전에 확인할 사항: 사용자가 최종 Payload를 검토하고 명시적으로 생성을 요청하면 생성 직전 TMI `작업` 메타데이터와 `High` 지원 여부를 다시 확인하고 승인된 필드만 전송한다. Git commit과 push는 사용자가 수행한다.

## 2026-07-28 — Jira TMI-14 운영 JWT 보안 정리 이슈 생성

- 날짜: `2026-07-28`
- 브랜치: `test/TMI-11-auth-e2e`
- Jira 이슈 키: `TMI-14`
- 작업 목표: 사용자가 승인한 Payload로 TMI 프로젝트에 Learning Core 운영 JWT 모드 강제 및 Legacy/HMAC 인증 정리 `작업` 이슈를 한 건 생성한다.
- 생성 전 확인: 현재 사용자에게 TMI 이슈 생성 권한이 있고 `작업` 유형(ID `10003`), 설명 필드와 `High` 우선순위(ID `2`)가 계속 지원되는지 재조회했다. 동일 제목의 기존 이슈가 없는 것도 확인했다.
- 생성 결과: [`TMI-14`](https://to-teacher.atlassian.net/browse/TMI-14)를 승인된 제목과 설명, 유형 `작업`, 우선순위 `High`로 생성했다. 초기 상태는 기본 상태 `해야 할 일`(상태 ID `10000`)이다.
- 생략한 필드: 담당자, 스프린트, 에픽, 라벨, 상위 항목과 상태 전환을 생성 Payload에 포함하지 않았다. 생성 후 담당자 미지정과 빈 라벨을 확인했다.
- 생성 후 검증: 이슈를 다시 조회해 프로젝트 `TMI`, 제목과 설명, 유형 `작업`, 우선순위 `High`, 상태 `해야 할 일`이 승인된 Payload와 일치함을 확인했다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. 애플리케이션 코드와 설정은 변경하지 않았다.
- 유지한 외부 계약: 기존 API URL·HTTP Method·Request/Response DTO·`BaseResponse`, `retryCount`, Redis Key·TTL, S3 Object Key, Python AI와 Callback의 `user_id = examId`, Feedback Callback의 `examId -> ExamSession -> 실제 userId`, 시험 소유권 검증을 변경하지 않았다.
- 실행한 검증과 결과: Atlassian MCP의 생성 권한·메타데이터·중복 후보 조회, 이슈 생성과 생성 후 상세 재조회가 모두 성공했다. 필수 작업 marker가 WORKLOG에 정확히 한 번 존재하고 `git diff --check`가 성공했다. 애플리케이션 코드 변경이 없어 `./gradlew clean test`는 실행하지 않았다.
- 남아 있는 위험 요소: 이슈 생성만 완료됐으며 운영 JWT 강제와 Legacy/HMAC/JJWT 정리 구현은 아직 시작하지 않았다. 담당자는 미지정 상태다.
- 다음 작업 전에 확인할 사항: TMI-14 구현 브랜치와 운영·스테이징 Spring profile 구성을 확인하고, HMAC 코드·`JWT_SECRET_KEY`·JJWT의 실제 사용처를 전수 분석한다. Git commit과 push는 사용자가 수행한다.

## 2026-07-28 — Jira TMI-14 생성 작업 Stop Hook 기록 보완

<!-- codex-turn:019fa6ed-61a2-7961-83d8-695481c19430 -->

- 날짜: `2026-07-28`
- 브랜치: `test/TMI-11-auth-e2e`
- Jira 이슈 키: `TMI-14`
- 작업 목표: 현재 turn에서 생성한 TMI-14 결과가 전용 marker와 함께 WORKLOG 끝에 기록되도록 보완하고 CURRENT_STATE를 최신화한다.
- 작업 결과: 앞서 생성·재조회한 [`TMI-14`](https://to-teacher.atlassian.net/browse/TMI-14)의 제목, 유형 `작업`, 기본 상태 `해야 할 일`, 우선순위 `High`, 담당자 미지정과 빈 라벨 상태를 기록했다. 후크 대응 중 Jira 생성·수정·댓글·상태 전환 API는 추가로 호출하지 않았다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. 애플리케이션 코드와 설정은 변경하지 않았다.
- 유지한 외부 계약: 기존 API URL·HTTP Method·Request/Response DTO·`BaseResponse`, `retryCount`, Redis Key·TTL, S3 Object Key, Python AI와 Callback의 `user_id = examId`, Feedback Callback의 `examId -> ExamSession -> 실제 userId`, 시험 소유권 검증을 변경하지 않았다.
- 실행한 검증과 결과: TMI-14 생성 결과는 앞선 Atlassian MCP 상세 재조회로 확인했다. 초안 및 생성 turn의 필수 marker가 각각 WORKLOG에 정확히 한 번 존재하고 `git diff --check`가 성공했다. 애플리케이션 코드 변경이 없어 `./gradlew clean test`는 실행하지 않았다.
- 남아 있는 위험 요소: TMI-14의 실제 운영 JWT 강제와 Legacy/HMAC/JJWT 정리 구현은 아직 시작하지 않았고 담당자는 미지정 상태다.
- 다음 작업 전에 확인할 사항: 구현 전 현재 브랜치와 운영·스테이징 Spring profile을 확인하고 HMAC 코드·`JWT_SECRET_KEY`·JJWT 사용처를 전수 분석한다. Git commit과 push는 사용자가 수행한다.

## 2026-07-28 — Jira TMI-14 현재 상태 및 전환 조회

<!-- codex-turn:019fa6f2-9cf6-7480-ac79-7ae82a26d7ed -->

- 날짜: `2026-07-28`
- 브랜치: `test/TMI-11-auth-e2e`
- Jira 이슈 키: `TMI-14`
- 작업 목표: Atlassian MCP로 TMI-14의 현재 상태와 현재 사용자에게 가능한 상태 전환을 읽기 전용으로 조회한다.
- 조회 결과: 현재 상태는 `해야 할 일`(상태 ID `10000`)이다. 사용 가능한 전환은 `해야 할 일`(전환 ID `11`), `진행 중`(`21`), `검토 중`(`31`), `완료`(`41`)이며 모두 조회 시점에 `isAvailable=true`였다.
- 변경한 동작: Jira 이슈 수정·댓글·상태 전환 API를 호출하지 않아 TMI-14의 상태와 내용은 변경되지 않았다. 애플리케이션 코드와 설정도 변경하지 않았다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 유지한 외부 계약: 기존 공개 API URL·HTTP Method·Parameter·Request/Response DTO·`BaseResponse`, `retryCount`, Redis·S3·음성 제출·Polling, Python AI와 Callback의 `user_id = examId`, 실제 `userId` 비노출 계약을 변경하지 않았다.
- 실행한 검증과 결과: 접근 가능한 Atlassian 리소스를 확인한 뒤 Jira 이슈 상세와 전환 목록 직접 조회가 성공했다. 통합 검색은 해당 인스턴스에 검색 앱이 설치되지 않아 403이었으나 직접 조회에는 영향이 없었다. 애플리케이션 코드 변경이 없어 `./gradlew clean test`는 실행하지 않았다.
- 남아 있는 위험 요소: 전환 목록은 조회 시점의 워크플로와 현재 사용자 권한 기준이며 Jira 설정이나 권한이 바뀌면 달라질 수 있다. 동일 상태인 `해야 할 일`로의 전환도 워크플로가 제공한다.
- 다음 작업 전에 확인할 사항: 실제 상태 전환이 필요하면 실행 직전에 전환 목록을 다시 조회하고 사용자의 명시적 승인을 받은 전환 ID만 사용한다. Git commit과 push는 사용자가 수행한다.

## 2026-07-28 — Jira TMI-14 진행 중 전환

<!-- codex-turn:019fa6f4-3aa3-7be2-9a11-3d6608a69b05 -->

- 날짜: `2026-07-28`
- 브랜치: `test/TMI-11-auth-e2e`
- Jira 이슈 키: `TMI-14`
- 작업 목표: 사용자 요청에 따라 TMI-14만 `해야 할 일`에서 `진행 중`으로 전환하고 다른 Jira 필드와 이슈는 변경하지 않는다.
- 전환 전 확인: TMI-14의 현재 상태가 `해야 할 일`(상태 ID `10000`)이고 `진행 중` 전환 ID `21`이 `isAvailable=true`인지 Atlassian MCP로 다시 확인했다.
- 변경한 동작: TMI-14에 전환 ID `21`만 전달했다. 전환 요청에 `fields`, `update`, 댓글을 포함하지 않았고 다른 Jira 이슈 API를 호출하지 않았다.
- 전환 결과: 전환 응답과 후속 상세 재조회에서 TMI-14가 `진행 중`(상태 ID `10001`, 상태 범주 `진행 중`)으로 변경됐음을 확인했다. 다른 Jira 필드는 수정하지 않았다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. 애플리케이션 코드와 설정은 변경하지 않았다.
- 유지한 외부 계약: 기존 공개 API URL·HTTP Method·Parameter·Request/Response DTO·`BaseResponse`, `retryCount`, Redis·S3·음성 제출·Polling, Python AI와 Callback의 `user_id = examId`, 실제 `userId` 비노출 계약을 변경하지 않았다.
- 실행한 검증과 결과: 전환 전 상태·전환 가능 여부 조회, 전환 ID `21` 실행, 전환 후 상태 직접 재조회가 모두 성공했다. 애플리케이션 코드 변경이 없어 `./gradlew clean test`는 실행하지 않았다.
- 남아 있는 위험 요소: Jira 상태만 `진행 중`으로 바뀌었으며 TMI-14의 실제 운영 JWT 강제와 Legacy/HMAC/JJWT 정리 구현은 아직 시작하지 않았다.
- 다음 작업 전에 확인할 사항: 구현 전에 현재 브랜치와 운영·스테이징 Spring profile, HMAC 코드·`JWT_SECRET_KEY`·JJWT 사용처를 확인한다. 추가 상태 전환은 다시 명시적으로 요청된 경우에만 수행하고 Git commit과 push는 사용자가 수행한다.

## 2026-07-28 — Jira TMI-14 구현 전 계약 충돌 확인

<!-- codex-turn:019fa6f8-826b-7390-91db-40064daebbaf -->

- 날짜: `2026-07-28`
- 브랜치: `test/TMI-11-auth-e2e`
- Jira: TMI-14
- 작업 목표: `AGENTS.md`, CURRENT_STATE, Jira TMI-14 설명과 완료 조건을 구현 기준으로 대조하고 충돌이 없을 때만 운영 JWT 강제 및 Legacy/HMAC 정리를 시작한다.
- Jira 조회 결과: Atlassian MCP로 TMI-14의 상태 `진행 중`, 운영·스테이징 JWT 모드 강제, Legacy Provider 차단, 잘못된 auth mode 기동 실패, 미사용 HMAC/JJWT/`JWT_SECRET_KEY` 제거, 전체 테스트와 외부 계약 유지 완료 조건을 재확인했다. Jira 댓글·필드·상태는 변경하지 않았다.
- 확인한 충돌: `AGENTS.md`의 현재 작업 범위 제외 항목은 “JWT 인증 강제”를 금지하지만 TMI-14의 핵심 범위와 완료 조건은 staging/prod에서 JWT 모드를 강제하는 것이다. 현재 JWT 모드의 기존 보호 경로를 유지하고 Startup 설정 검증만 추가하는 좁은 구현도 이 금지의 명시적 예외인지 문서만으로 확정할 수 없다.
- 충돌하지 않는 계약: 공개 API URL·Method·Parameter·Request/Response DTO·`BaseResponse`, `retryCount`, Redis·S3, Python AI와 Callback의 `user_id = examId`, 실제 `userId` 비노출과 시험 소유권 계약은 Jira와 `AGENTS.md`가 일치한다.
- 작업 결과: 사용자 지침에 따라 충돌을 먼저 보고하고 애플리케이션 코드·설정·테스트·README의 구현 변경은 시작하지 않았다. Jira에도 댓글이나 상태 변경을 적용하지 않았다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`만 갱신했다.
- 실행한 검증과 결과: `AGENTS.md`와 CURRENT_STATE 전체 확인, Atlassian MCP의 TMI-14 설명·완료 조건 재조회가 성공했다. 코드 변경이 없어 인증 모드 테스트와 `./gradlew clean test`는 실행하지 않았다.
- 남아 있는 위험 요소: TMI-14가 `AGENTS.md`의 “JWT 인증 강제” 금지에 대한 명시적 예외인지 확인되기 전 구현하면 저장소 운영 규칙을 위반할 수 있다.
- 다음 작업 전에 확인할 사항: 사용자가 TMI-14를 해당 금지의 명시적 예외로 승인하는지, 그리고 예외 범위를 staging/prod Startup 검증과 Legacy 차단으로 한정하고 기존 JWT 보호 경로는 유지할지 확인한다. Git commit과 push는 사용자가 수행한다.

## 2026-07-28 — Jira TMI-14 운영 JWT Startup 강화 및 HMAC 정리

<!-- codex-turn:019fa705-1256-70f1-925c-1f056f568e80 -->

- 날짜: `2026-07-28`
- 브랜치: `refactor/TMI-14-jwt-production-hardening`
- Jira: TMI-14
- 작업 목표: staging/prod의 JWT 모드와 필수 Identity 설정을 fail-closed로 검증하고 local/test Legacy를 유지하면서, 실제 미사용 HMAC JWT 코드·JJWT·공유 Secret 설정을 제거한다.
- 기준 확인: 작업 전 `AGENTS.md`와 CURRENT_STATE를 읽고 Atlassian MCP로 TMI-14 설명·완료 조건을 재조회했다. 사용자가 TMI-14에 한해 “JWT 인증 강제 제외” 규칙의 제한 예외를 승인해 `AGENTS.md`에 전용 예외를 추가하고 전체 규칙을 재독했다. 예외는 Startup 검증·Legacy 차단·auth mode 검증·미사용 HMAC 정리에만 한정되며 기존 보호·공개 경로와 외부 계약을 완화하지 않는다.
- 사용처 분석: `JwtAuthenticationFilter`·`JwtTokenProvider`는 Bean 등록, FilterChain 연결, 비즈니스 호출 없이 서로만 참조했고 JJWT와 `jwt.secret`은 이 코드에만 사용됐다. `SecurityConfig`, 두 `CurrentUserProvider`, 표준 JWT validator와 401/403 handler는 실제 사용 중이므로 유지했다.
- 인증 모드 타입 안전성: `AuthMode.LEGACY`·`JWT`, strict 소문자 converter와 `AuthProperties`를 추가했다. `legacy`·`jwt`만 허용하고 빈 값·대문자·오타·지원하지 않는 값은 지원 값만 안내하는 오류로 시작 실패하며 Legacy로 fallback하지 않는다.
- 운영 Startup 검증: `AuthStartupValidator`가 JWT 설정의 비어 있지 않은 HTTP(S) issuer·JWKS URL과 audience를 검증한다. 공통 설정의 Identity 기본값은 비우고 localhost 개발 기본값은 `application-local.yml`로 분리해 staging/prod 누락이 숨지 않게 했다. staging/prod는 JWT만 허용하고 localhost·loopback URL과 placeholder audience를 거부하며 Legacy Provider·FilterChain 강제 등록도 차단한다. 검증 과정에서 외부 네트워크 요청은 하지 않는다.
- Legacy 범위: `LegacyCurrentUserProvider`와 Legacy SecurityFilterChain에 `local`·`test` profile 조건을 추가했다. local/test Legacy는 고정 개발 UUID와 기존 전체 `permitAll` 호환을 유지하고 Identity·JWKS 없이 동작한다. JWT Provider·FilterChain과 동시에 등록되지 않는다.
- HMAC 정리: 미사용 `JwtAuthenticationFilter.java`, `JwtTokenProvider.java`, JJWT 3개 의존성, `application.yml`과 테스트 설정의 `jwt.secret`·`JWT_SECRET_KEY`를 제거했다. JWT 인증은 기존 `NimbusJwtDecoder`와 OAuth2 Resource Server만 담당한다.
- 문서 변경: UTF-16 한 줄이던 README를 UTF-8로 변환해 local Legacy, local JWT, staging/prod 환경변수와 fail-closed 정책, 공개·보호 경로 유지, HMAC 제거를 문서화했다. JWT 계약과 E2E 실행 문서도 profile·Secret 제거 상태에 맞게 갱신했다.
- 변경 파일: `AGENTS.md`, `README.md`, `build.gradle`, `docs/contracts/identity-learning-jwt.md`, `scripts/e2e/README.md`, `SecurityConfig.java`, `LegacyCurrentUserProvider.java`, 신규 `global/config/auth/*`, 삭제한 HMAC 두 클래스, `application.yml`, 신규 `application-local.yml`, `application-test.yml`, 인증 관련 신규·기존 테스트, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 유지한 런타임 보안: JWT `PUBLIC_ENDPOINTS` 배열과 `.anyRequest().authenticated()`를 변경하지 않았다. Callback·Swagger·OpenAPI·기존 health 경로 공개 정책, STATELESS·CSRF/form/basic 비활성화와 401/403 `BaseResponse`를 유지했다.
- 유지한 외부 계약: 공개 API URL·Method·Path/Query Parameter·Request/Response DTO·`BaseResponse`, `retryCount`, Redis Key·TTL, S3 Object Key·Presigned URL, 음성 제출·Polling, `mock_exam_003`, ExamSession·ExamResult·시험 소유권을 변경하지 않았다. Python AI 요청과 Callback의 `user_id = examId`, Callback의 `examId -> ExamSession -> 실제 userId`, 클라이언트 실제 `userId` 비노출을 유지했다.
- 테스트 결과: 인증 집중 테스트를 반복 실행한 뒤 `./gradlew clean test`에서 88개 테스트가 모두 성공했고 실패·오류·건너뜀은 0개였다. local/test Legacy, staging/prod Legacy 실패·정상 JWT 성공, 필수 설정 누락·URI·localhost·placeholder 실패, 강제 Legacy Bean/Chain 차단, 기존 JWT 401/403·Callback 공개·소유권·AI 계약, HMAC 제거를 검증했다. 실제 Identity·Atlas·Redis·AWS S3·Python AI 서버는 호출하지 않았다.
- 정적 검증: `git diff --check` 성공, 활성 런타임 소스·설정·빌드의 HMAC 클래스·JJWT·`JWT_SECRET_KEY`·`jwt.secret`·커스텀 Filter 등록 잔여 사용처 없음, Private Key·AWS Key·자격증명 포함 MongoDB URI·JWT 원문 패턴 없음, 도메인 API/DTO 파일 변경 없음과 기존 보안 경로 불변을 확인했다. 기존 `ExamServiceImpl` unchecked 경고는 범위 밖이라 수정하지 않았다.
- Jira 처리: Jira 설명과 완료 조건은 읽기 전용으로만 조회했다. Jira 댓글·필드·상태는 변경하지 않았으며 이슈는 계속 `진행 중`이다. 완료 댓글은 최종 보고에 초안으로만 제공한다.
- 남아 있는 위험 요소: Startup Validator는 설정과 URI 형식만 검사하므로 실제 staging/prod Identity·JWKS 도달성, JWKS rotation·캐시와 두 서버 E2E는 배포 전 별도 검증이 필요하다. AI Callback 서비스 간 인증은 기존 위험으로 남아 있다.
- 다음 작업 전에 확인할 사항: 실제 배포 환경에서 JWT 설정과 나머지 인프라 값을 안전하게 주입해 staging/prod smoke test를 수행한다. 사용자가 변경분과 Jira 댓글 초안을 검토한 뒤 Jira 댓글·상태 변경, Git commit·push를 직접 수행한다.

## 2026-07-28 — Jira TMI-14 완료 전환

<!-- codex-turn:019fa736-396b-70a3-b326-85c66986b732 -->

- 날짜: `2026-07-28`
- 브랜치: `refactor/TMI-14-jwt-production-hardening`
- Jira: TMI-14
- 작업 목표: 사용자가 확인한 PR 병합과 테스트 성공을 근거로 TMI-14만 방금 확인한 `완료` 상태로 전환하고 다른 필드와 다른 Jira 이슈는 변경하지 않는다.
- 전환 전 확인: Atlassian MCP 직접 조회에서 TMI-14의 상태가 `진행 중`(상태 ID `10001`)이고 `완료` 전환 ID `41`이 사용 가능함을 확인했다.
- 변경한 동작: TMI-14에 전환 ID `41`만 전송했다. 전환 요청에 `fields`, `update`, 댓글을 포함하지 않았고 다른 Jira 이슈를 수정하지 않았다.
- 전환 결과: 후속 상세 조회에서 TMI-14가 `완료`(상태 ID `10003`, 상태 범주 `완료`)로 변경됐고 resolution이 `완료`(ID `10000`)로 설정됐음을 확인했다. resolution은 완료 워크플로가 자동으로 설정했으며 별도 필드 변경으로 지정하지 않았다.
- Jira 댓글: 등록하지 않았다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. 애플리케이션 코드와 설정은 변경하지 않았다.
- 유지한 외부 계약: 기존 공개 API URL·HTTP Method·Parameter·Request/Response DTO·`BaseResponse`, `retryCount`, Redis Key·TTL, S3 Object Key, Python AI와 Callback의 `user_id = examId`, 실제 `userId` 비노출과 시험 소유권 계약을 변경하지 않았다.
- 검증 결과: 사용자가 TMI-14 PR 병합과 테스트 성공을 확인했다. 이번 Jira 전환 작업에는 애플리케이션 코드 변경이 없어 Gradle 테스트를 다시 실행하지 않았다.
- 남아 있는 위험 요소: Startup Validator는 설정 형식만 검사하므로 실제 staging/prod Identity·JWKS 도달성, JWKS rotation·캐시와 배포 smoke test는 별도 확인이 필요하다. AI Callback 서비스 간 인증은 기존 위험으로 남아 있다.
- 다음 작업 전에 확인할 사항: 실제 배포 환경에 JWT와 인프라 설정을 안전하게 주입해 staging/prod smoke test를 수행한다. Jira 재전환이나 댓글 등록은 사용자가 명시적으로 요청하는 경우에만 수행하며 Git commit과 push는 사용자가 수행한다.

## 2026-07-28 — Learning Core 채점 복구·멱등성 Jira Payload 초안

<!-- codex-turn:019fa75f-57d9-7ec3-a711-6590a15eae35 -->

- 날짜: `2026-07-28`
- 브랜치: `refactor/TMI-14-jwt-production-hardening`
- Jira 이슈 키: 없음. 사용자 요청에 따라 초안만 작성했고 이슈를 생성하지 않았다.
- 작업 목표: TMI 프로젝트에 생성할 `[Learning Core] 시험 단위 재채점 및 AI 채점·Callback 멱등성 보장` 작업 이슈의 최종 Payload 초안을 작성하고 실제 지원 필드를 검증한다.
- 사전 확인: 저장소의 `AGENTS.md`와 `docs/codex/CURRENT_STATE.md` 전체를 먼저 읽고 외부 API·`retryCount`·Redis/S3·Python AI `user_id = examId`·사용자 소유권 계약을 초안 기준에 반영했다.
- Atlassian MCP 확인: TMI 프로젝트(ID `10000`)에 대한 생성 권한, `작업` 유형(ID `10003`), 설명 필드와 `High` 우선순위(ID `2`) 지원을 읽기 전용으로 확인했다. 동일 제목 후보는 조회되지 않았다.
- 초안 결과: 배경, 유지·신규 외부 API, retryCount 0 시험 복구 규칙, 문항 채점 Job, 전체 요약 Job, 네 종류 Callback의 논리 키 멱등성, 기존 ExamStatus와 Redis 역할, 안정적인 AI `Idempotency-Key`, 완료 조건과 범위 제외를 구조화했다.
- 실제 전송 예정 필드: `projectKey=TMI`, `issueTypeName=작업`, 제목, Markdown 설명, `additional_fields.priority.id=2`만 도메인 필드로 전송한다. 담당자·라벨·상위 항목·스프린트·상태 전환은 포함하지 않는다.
- Jira 변경 여부: `createJiraIssue`, 수정, 댓글, 전환 API를 호출하지 않았으며 Jira 데이터는 변경되지 않았다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. 애플리케이션 코드와 설정은 변경하지 않았다.
- 유지한 외부 계약: 기존 submit·전체 상태 API, Request/Response DTO·`BaseResponse`, 기존 `retryCount`, Redis Key·S3 Key, 음성 제출·Polling, Python AI 요청과 Callback의 `user_id = examId`, 실제 `userId` 비노출과 시험 소유권 검증을 변경하지 않았다. 신규 시험 단위 retry API는 Payload 초안에만 포함했다.
- 실행한 검증과 결과: Atlassian MCP 메타데이터와 동일 제목 후보 조회, `git diff --check`, 전용 marker의 정확히 한 번 존재 확인이 모두 성공했다. 문서 변경만 있어 `./gradlew clean test`는 실행하지 않았다.
- 남아 있는 위험 요소: `PENDING` timeout의 구체값, 시험별 필수 문항 집합, 신규 retry API 성공 응답 세부 계약이 아직 정해지지 않았다. Learning Core의 `Idempotency-Key` 전송만으로 Python AI 내부 멱등 처리가 자동 보장되지는 않는다.
- 다음 작업 전에 확인할 사항: 사용자가 초안을 승인하고 명시적으로 생성 요청한 경우에만 검증된 Payload를 전송한다. 구현 전 미정 정책과 Python AI 멱등 키 처리의 별도 이슈 분리 여부를 확정하며 Git commit과 push는 사용자가 수행한다.

## 2026-07-28 — Jira TMI-25 채점 복구·멱등성 작업 생성

<!-- codex-turn:019fa769-4107-7c71-a088-d1d3b64d4f50 -->

- 날짜: `2026-07-28`
- 브랜치: `refactor/TMI-14-jwt-production-hardening`
- Jira: [`TMI-25`](https://to-teacher.atlassian.net/browse/TMI-25)
- 작업 목표: 사용자가 승인한 Payload를 그대로 사용해 TMI 프로젝트에 `[Learning Core] 시험 단위 재채점 및 AI 채점·Callback 멱등성 보장` 작업 이슈를 생성한다.
- 생성 Payload: 프로젝트 `TMI`, 이슈 유형 `작업`(ID `10003`), 승인된 제목과 Markdown 설명, 우선순위 `High`(ID `2`)만 전송했다.
- 생략한 필드: 담당자, 라벨, 상위 항목, 스프린트, 에픽과 상태 전환을 전송하지 않았다.
- 생성 결과: Jira `TMI-25`가 기본 상태 `해야 할 일`(상태 ID `10000`)로 생성됐다.
- 생성 후 검증: 상세 재조회에서 프로젝트 `TMI`, 승인된 제목·설명, 유형 `작업`, 우선순위 `High`, 상태 `해야 할 일`, 담당자 미지정과 빈 라벨을 확인했다. Jira 수정·댓글·상태 전환 API는 호출하지 않았다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. 애플리케이션 코드와 설정은 변경하지 않았다.
- 유지한 외부 계약: 기존 submit·전체 상태 API, Request/Response DTO·`BaseResponse`, 기존 `retryCount`, Redis Key·S3 Key, 음성 제출·Polling, Python AI 요청과 Callback의 `user_id = examId`, 실제 `userId` 비노출과 시험 소유권 검증을 변경하지 않았다. 신규 시험 단위 retry API는 Jira 구현 범위로만 기록했다.
- 실행한 검증과 결과: Atlassian MCP 이슈 생성과 생성 후 상세 재조회, `git diff --check`, 전용 marker의 정확히 한 번 존재 확인이 모두 성공했다. 문서 변경만 있어 `./gradlew clean test`는 실행하지 않았다.
- 남아 있는 위험 요소: `PENDING` timeout 구체값, 시험별 필수 문항 집합, 신규 retry API 성공 응답의 세부 계약은 구현 전에 확정해야 한다. Python AI가 `Idempotency-Key`를 실제 처리하는 작업은 별도 이슈로 분리할 수 있다.
- 다음 작업 전에 확인할 사항: TMI-25 구현 전 미정 정책을 확정한다. Jira 상태 변경, 댓글 등록, Git commit과 push는 별도 명시적 요청이 있을 때만 수행한다.

## 2026-07-28 — Jira TMI-25 현재 상태 및 전환 조회

<!-- codex-turn:019fa76c-d011-7b72-8e5e-34efeab63cb3 -->

- 날짜: `2026-07-28`
- 브랜치: `refactor/TMI-14-jwt-production-hardening`
- Jira: [`TMI-25`](https://to-teacher.atlassian.net/browse/TMI-25)
- 작업 목표: 직전에 생성한 TMI-25의 현재 상태와 현재 사용자에게 가능한 상태 전환을 Atlassian MCP로 읽기 전용 조회한다.
- 현재 상태: `해야 할 일`(상태 ID `10000`, 상태 범주 `해야 할 일`)이다.
- 가능한 전환: `해야 할 일`(전환 ID `11`), `검토 중`(`31`), `진행 중`(`21`), `완료`(`41`)이며 조회 시점에 모두 `isAvailable=true`였다.
- Jira 변경 여부: 이슈 상세와 전환 목록 조회만 수행했다. 상태 전환, 필드 수정, 댓글 등록 API는 호출하지 않아 TMI-25의 상태와 내용은 변경되지 않았다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. 애플리케이션 코드와 설정은 변경하지 않았다.
- 유지한 외부 계약: 기존 공개 API·DTO·`BaseResponse`, `retryCount`, Redis·S3, Python AI와 Callback의 `user_id = examId`, 실제 `userId` 비노출과 시험 소유권 계약을 변경하지 않았다.
- 실행한 검증과 결과: Atlassian MCP의 TMI-25 상세 조회와 전환 목록 조회, `git diff --check`, 전용 marker의 정확히 한 번 존재 확인이 모두 성공했다. 문서 변경만 있어 `./gradlew clean test`는 실행하지 않았다.
- 남아 있는 위험 요소: 전환 목록과 가용성은 조회 시점의 Jira 워크플로와 현재 사용자 권한 기준이므로 이후 달라질 수 있다. 현재 상태와 같은 `해야 할 일`로의 전환도 제공된다.
- 다음 작업 전에 확인할 사항: 실제 전환이 요청되면 직전에 전환 목록을 다시 조회하고 사용자가 승인한 전환 ID만 사용한다. Jira 댓글·다른 필드 변경, Git commit과 push는 별도 요청이 있을 때만 수행한다.

## 2026-07-28 — Jira TMI-25 진행 중 전환

<!-- codex-turn:019fa76e-5891-7863-9041-cd7454511e72 -->

- 날짜: `2026-07-28`
- 브랜치: `refactor/TMI-14-jwt-production-hardening`
- Jira: [`TMI-25`](https://to-teacher.atlassian.net/browse/TMI-25)
- 작업 목표: 사용자 요청에 따라 직전에 확인한 TMI-25의 `진행 중` 전환만 적용하고 다른 Jira 필드와 이슈는 수정하지 않는다.
- 전환 전 확인: TMI-25의 상태가 `해야 할 일`(상태 ID `10000`)이고 `진행 중` 전환 ID `21`이 `isAvailable=true`인지 Atlassian MCP로 다시 확인했다.
- 변경한 동작: TMI-25에 전환 ID `21`만 전송했다. 전환 요청에 `fields`, `update`, 댓글을 포함하지 않았고 다른 Jira 이슈를 호출하지 않았다.
- 전환 결과: 전환 응답과 후속 상세 재조회에서 TMI-25가 `진행 중`(상태 ID `10001`, 상태 범주 `진행 중`)으로 변경됐음을 확인했다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. 애플리케이션 코드와 설정은 변경하지 않았다.
- 유지한 외부 계약: 기존 공개 API·DTO·`BaseResponse`, `retryCount`, Redis·S3, Python AI와 Callback의 `user_id = examId`, 실제 `userId` 비노출과 시험 소유권 계약을 변경하지 않았다.
- 실행한 검증과 결과: 전환 전 상태·전환 가용성 조회, 전환 ID `21` 적용, 전환 후 상태 재조회, `git diff --check`, 전용 marker의 정확히 한 번 존재 확인이 모두 성공했다. 문서 변경만 있어 `./gradlew clean test`는 실행하지 않았다.
- 남아 있는 위험 요소: Jira 상태만 `진행 중`으로 변경됐으며 TMI-25의 채점 복구·멱등성 구현은 아직 수행하지 않았다.
- 다음 작업 전에 확인할 사항: 구현 전 `PENDING` timeout, 필수 문항 집합, 신규 retry API 성공 응답 세부 계약과 Python AI 멱등 키 처리 분리 여부를 확정한다. 추가 Jira 변경과 Git commit·push는 별도 요청이 있을 때만 수행한다.

## 2026-07-28 — Jira TMI-25 구현 전 채점 복구·멱등성 정적 분석

<!-- codex-turn:019fa771-88e6-7783-a0af-34cc0fe739e6 -->

- 날짜: `2026-07-28`
- 브랜치: `refactor/TMI-14-jwt-production-hardening`
- Jira: [`TMI-25`](https://to-teacher.atlassian.net/browse/TMI-25)
- 작업 목표: `AGENTS.md`, CURRENT_STATE와 Jira TMI-25 설명·완료 조건을 기준으로 시험 생성부터 S3 업로드·submit·AI 요청·Redis Polling·네 종류 Callback·ExamResult/ExamSummary·전체 요약 Trigger·소유권·Mongo/S3/Clock/테스트 구조를 구현 전에 정적으로 분석한다.
- Jira 확인: Atlassian MCP로 TMI-25의 승인된 제목, 설명, 완료 조건, 상태 `진행 중`, 우선순위 `High`를 읽기 전용으로 재조회했다. Jira 댓글·필드·상태는 변경하지 않았다.
- 현재 제출 흐름: 시험 생성은 Redis `PENDING`을 먼저 기록하고 `mock_exam_003` 문제지를 조립한 뒤 `ExamSession(examId, 실제 userId, createdAt)`을 저장한다. upload-url은 기존 `temp/{examId}/q_{questionNumber}_r{retryCount}.wav` PUT URL만 발급하며 Job을 만들지 않는다. submit은 전체 Redis 상태를 `PROCESSING`으로 덮어쓰고 같은 S3 파일을 `byte[]`로 다시 내려받아 Job·Lock·결과 확인 없이 AI multipart 요청을 보낸다.
- 중복 분석: 동일 submit N회는 최대 N회 S3 다운로드와 N회 AI 요청을 만든다. 동일 Feedback·SpeechAce·Azure·전체 요약 Callback N회는 각각 N개의 새 결과 문서를 만들 수 있고, 동일 11번 Feedback Callback은 최대 N개의 전체 요약 요청을 시작할 수 있다.
- 상태·요약 분석: 전체 상태 조회는 Mongo 결과나 Job을 보지 않고 Redis 단일 값만 사용하며 miss를 `FAILED`로 해석한다. `progressPercent`는 실제 계산 없이 항상 `60`이다. 11번 Callback은 다른 문항 완료 여부와 `retryCount=0` 여부를 확인하지 않고 공용 `CompletableFuture`에서 요약을 요청한다.
- 저장·호환 분석: 현재 모든 결과 엔티티에는 `@Version`·논리 Unique Index가 없고, 기존 중복 문서와 `retryCount=0/null/missing` 혼재 가능성이 있어 기존 결과 컬렉션에 Unique Index를 즉시 추가하면 충돌할 수 있다. 신규 Job에는 결정적 `_id`를 안전하게 사용할 수 있으며 기존 결과의 신규 저장에도 사용할 수 있지만 ObjectId와 문자열 `_id` 혼재 시 `_id DESC`가 시간순을 보장하지 않아 결정적 ID 우선·legacy 최신 fallback 조회가 필요하다.
- 인프라 분석: AWS S3 SDK 의존성으로 `S3Client` 타입은 사용 가능하지만 현재 Bean과 `headObject` 호출은 없고 `S3Presigner`만 있다. Clock Bean과 Mongo 원자 claim 구현, Redis Lock, MongoTransactionManager는 없다. 새 Repository를 기존 `web.tosunsaeng.domain.exams.domain.repository` 아래에 두면 현재 `@EnableMongoRepositories` 범위에서 scan된다.
- 최소 설계 결론: `question:{examId}:{questionNumber}:{retryCount}`와 `summary:{examId}:v1` 결정적 ID의 Question/Summary Job, 상태·dispatchAttempt·필수 timestamp·실패 정보·`@Version`, Mongo 조건부 `findAndModify` claim, UTC Clock, S3 `headObject`, 안정적인 AI `Idempotency-Key`, 결과 `insert`와 `DuplicateKeyException` 성공 처리, 모든 필수 retry 0 Feedback 완료 gate를 사용한다. 시험 단위 retry는 retry 0만 복구하고 사용자 재녹음 retryCount>0은 제외한다.
- 전체 상태 결론: Mongo Question/Summary Job을 기준으로 `PENDING/PROCESSING/FAILED/COMPLETED`와 실제 진행률을 산정하고 기존 Redis Key·TTL은 캐시로만 유지한다. Redis miss는 Mongo에서 재산정하며 문항 Polling도 Job 네 상태와 legacy 결과 fallback을 사용한다.
- 테스트 계획: 고정 Clock 경계, 동일 submit·retry 동시 claim, 상태별 retry 행렬, S3 존재·404·403·장애, 네 Callback 순차·동시 중복, 11번 단독 요약 금지, 전체 문항 완료 후 요약 1회, 요약만 재시도, Redis miss·실제 progress, legacy null·중복·결정적 ID 조회, 소유권과 외부 계약을 검증한다. Mongo `findAndModify`의 원자성은 disposable Mongo 기반 Repository 통합 테스트가 필요하다.
- 변경 파일: Stop Hook 요구에 따라 `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`만 갱신했다. 애플리케이션 코드·설정·테스트 파일은 생성·수정·삭제하지 않았다.
- 유지한 외부 계약: 기존 API URL·Method·Path/Query·Request/Response DTO·`BaseResponse`, `retryCount`, Redis Key·TTL, S3 Key·Presigned URL, 음성 제출·Polling, Python AI 요청과 Callback의 `user_id = examId`, 실제 `userId` 비노출과 사용자용 API 소유권 검증을 변경하지 않았다.
- 실행한 검증과 결과: `AGENTS.md`, CURRENT_STATE, 관련 Java·설정·테스트 전체 정적 확인, Atlassian MCP Jira 재조회와 `git diff --check`가 성공했다. 구현 변경이 없고 분석 중 파일 무변경 지침이 있었으므로 `./gradlew clean test`는 실행하지 않았다.
- 남아 있는 위험 요소: `PENDING` timeout, 필수 문항 집합의 세션 고정 방식, 신규 retry API 성공 응답 세부 계약이 미정이다. Python AI가 멱등 키를 실제 처리하기 전에는 Mongo claim과 외부 HTTP 사이 crash window의 AI 중복 실행을 완전히 제거할 수 없고, S3 객체 부재가 권한에 따라 404 대신 403으로 나타날 수 있다.
- 다음 작업 전에 확인할 사항: 위 세 정책과 Python AI 멱등 처리 분리 여부를 확정한 후에만 최대 두 단계로 구현한다. 구현 후 외부 인프라를 Mock 또는 disposable 환경으로 격리한 전체 테스트를 실행하며 Git commit과 push는 사용자가 수행한다.

## 2026-07-28 — Jira TMI-25 API 호환성 규칙 제한 예외 기록

<!-- codex-turn:019fa78c-55af-7003-bc2d-84b6f3f62a5a -->

- 날짜: `2026-07-28`
- 브랜치: `refactor/TMI-14-jwt-production-hardening`
- Jira: [`TMI-25`](https://to-teacher.atlassian.net/browse/TMI-25)
- 작업 목표: 사용자가 승인한 TMI-25 전용 API 변경 금지 규칙의 제한 예외를 `AGENTS.md`에 명시하고 다른 작업으로 확대되지 않도록 허용·금지 범위를 고정한다.
- 허용 범위 기록: 신규 `POST /api/v1/exams/{examId}/grading/retry`, 신규 API 전용 Request/Response DTO, Question/Summary Job, 외부 계약을 유지한 submit 멱등화, 기존 URL·Method·필드를 유지한 status 내부 처리, 네 Callback 저장 멱등화와 모든 필수 retry 0 문항 완료 기준 요약 Trigger를 허용했다.
- 금지 범위 기록: 기존 API URL·Method·Request Parameter·Response 필드, retryCount 의미, AI `user_id = examId`, Redis Key, S3 Object Key와 소유권 검증 변경을 금지했다. retryCount>0 새 녹음의 시험 전체 복구, 프론트 문항 목록 전달, 별도 외부 summary retry API도 금지했다.
- 추가 경계: 신규 시험 단위 retry API의 Request Body 없음 계약을 유지하고, 예외는 Jira TMI-25에만 적용되며 완료 후나 다른 작업에 자동 적용되지 않음을 명시했다.
- Jira 변경 여부: Jira 이슈를 조회·수정·댓글·전환하지 않았다. 현재 이슈 키는 기존 작업 문맥과 CURRENT_STATE의 TMI-25를 사용했다.
- 변경 파일: `AGENTS.md`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. 애플리케이션 코드·설정·테스트 파일은 변경하지 않았다.
- 유지한 외부 계약: 이번 문서 변경 자체는 모든 런타임 계약을 변경하지 않았다. 향후 구현에서도 명시적으로 허용된 신규 API 외의 기존 URL·Method·Parameter·Response DTO·`BaseResponse`, retryCount, Redis/S3 Key, AI·Callback `user_id = examId`, 사용자 소유권을 유지해야 한다.
- 실행한 검증과 결과: 변경 문서 정적 확인, 전용 marker의 정확히 한 번 존재 확인과 `git diff --check`를 수행한다. 애플리케이션 코드 변경이 없어 `./gradlew clean test`는 실행하지 않는다.
- 남아 있는 위험 요소: 예외 범위를 일반 API 재설계나 retryCount>0 복구, 외부 summary retry API로 확대하면 사용자 승인 범위를 벗어난다. TMI-25 구현 전 미정인 `PENDING` timeout, 필수 문항 집합 고정 방식과 신규 retry 응답 세부 계약은 여전히 확정해야 한다.
- 다음 작업 전에 확인할 사항: 실제 구현에서 허용 목록과 금지 목록을 체크리스트로 사용하고 외부 계약 테스트로 위반 여부를 검증한다. Git commit과 push는 사용자가 수행한다.

## 2026-07-28 — Jira TMI-25 시험 단위 채점 복구·멱등성 구현

<!-- codex-turn:019fa790-a59c-7c11-ad61-47b0712575ae -->

- 날짜: `2026-07-28`
- 브랜치: `feat/TMI-25-grading-retry-idempotency`
- Jira: [`TMI-25`](https://to-teacher.atlassian.net/browse/TMI-25)
- 작업 목표: 프론트가 전체 상태만 Polling하고 시험 단위 retry API 하나를 호출하면 백엔드가 최초 응시 문항과 전체 요약 Job을 기준으로 실패·timeout 작업만 원자적으로 재전송하도록 구현한다.
- Jira 변경 여부: 이슈 설명과 완료 조건은 구현 기준으로만 사용했다. Jira 댓글·필드·상태·다른 이슈는 변경하지 않았으며 TMI-25는 계속 `진행 중`이다.
- 변경 파일: `AGENTS.md`; `application.yml`; `S3Config`, 신규 `GradingConfig`·`GradingProperties`; 신규 `GradingKeys`·`GradingDispatchService`·`ExamGradingService`; `ExamService`·`ExamServiceImpl`·`ExamRestController`; `ExamResponseDTO`·`ExamConverter`; 신규 Job entity/enums/repositories와 결과 repositories; `ExamServiceImplTest`·`ExamOwnershipServiceTest`·`FeedbackCallbackServiceTest`·JWT/Legacy 보안 테스트; 신규 `ExamGradingServiceTest`·`GradingDispatchServiceTest`·`GradingPropertiesTest`; `docs/codex/WORKLOG.md`·`docs/codex/CURRENT_STATE.md`를 변경했다.
- 문항 Job: `_id=question:{examId}:{questionNumber}:{retryCount}`에 상태, S3 fileKey, dispatchAttempt, pending/processing/dispatch/completed/failed 시각, 실패 사유와 `@Version`을 저장한다. submit은 결정적 `insert` 성공자만 PROCESSING으로 optimistic claim하고 AI를 호출하며 동일 Job 재호출은 기존 상태만 반환한다.
- 시험 retry: `mock_exam_003`의 `MockExam.questions`를 정렬된 예상 문항으로 사용하고 retryCount 0만 검사한다. COMPLETED는 건너뛰고 fresh PENDING/PROCESSING은 waiting, stale PENDING/PROCESSING과 시도 한도 미만 FAILED만 claim한다. 한도에 도달해도 아직 fresh PROCESSING이면 현재 시도를 기다리고 timeout 이후 FAILED로 고정한다.
- S3 복구: Job이 없을 때 기존 `temp/{examId}/q_{questionNumber}_r0.wav`에 SDK `HeadObject`를 실행한다. 404만 missingSubmission으로 분류하고 객체가 있으면 Job을 복구해 dispatch하며 403과 인프라 오류는 missing으로 오인하지 않는다.
- Callback 멱등성: Feedback·SpeechAce·Azure·Summary는 legacy 논리 결과를 먼저 확인한 뒤 `feedback|speechace|azure:{examId}:{questionNumber}:{retryCount}` 또는 `summary:{examId}:v1` 결정적 `_id`로 `insert`한다. 동시 `DuplicateKeyException`은 성공으로 흡수하고 기존 결과나 중복 문서는 삭제하지 않으며 Unique Index와 자동 마이그레이션을 추가하지 않았다.
- 요약 Job: `_id=summary:{examId}:v1`과 Question Job과 같은 상태·시각·attempt·failure·`@Version` 구조를 사용한다. 11번 특별 Trigger를 제거하고 모든 필수 retryCount 0 Feedback 결과 또는 COMPLETED Job이 있을 때만 한 요청을 claim한다. 시험 retry에서 문항 작업이 남아 있으면 요약을 건드리지 않고, 모든 문항 완료 후 fresh PROCESSING 대기·stale PROCESSING/FAILED 재전송·COMPLETED 무동작을 적용한다.
- 전체 상태: retryCount 0 결과와 Question/Summary Job을 일괄 조회해 FAILED 우선, active PROCESSING, 미제출 PENDING, 문항 완료·요약 미완료 PROCESSING, 전체 완료 COMPLETED로 산정하고 기존 `exam:status:{examId}`·1시간 TTL에 projection한다. 기존 status DTO와 `progressPercent=60`은 변경하지 않았다.
- AI 계약: 기존 Question multipart와 Summary JSON Body, `mock_exam_003`, `user_id = examId`를 유지했다. Header만 Question Job ID 또는 Summary Job ID의 안정적인 `Idempotency-Key`로 추가했으며 실제 userId는 보내지 않는다.
- 설정: `GRADING_PENDING_TIMEOUT` 기본 `PT1M`, `GRADING_PROCESSING_TIMEOUT` 기본 `PT3M`, `GRADING_MAX_DISPATCH_ATTEMPTS` 기본 `3`을 타입 안전하게 바인딩하고 timeout 양수·attempt 1 이상을 검증한다. 신규 시간 로직은 고정 가능한 UTC `Clock` Bean을 사용한다.
- 기존 데이터 호환: retryCount `null`을 0으로 읽는 기존 조회를 유지하고, legacy 결과가 있거나 Callback이 먼저 도착하면 누락 COMPLETED Job을 지연 복구한다. 기존 `ExamResult`·`ExamSummary` 조회와 legacy summary fallback을 유지하며 애플리케이션 시작 시 DB 정리·백필을 실행하지 않는다.
- 유지한 외부 계약: 허용된 신규 `POST /api/v1/exams/{examId}/grading/retry` 외 기존 URL·Method·Path/Query·Request/Response DTO·`BaseResponse`, retryCount 의미, Redis Key·TTL, S3 Key·Presigned URL, AI Callback JSON, 음성 제출·Polling, 사용자 소유권 검증을 유지했다. 별도 summary retry API와 프론트 문항 목록 입력은 추가하지 않았다.
- 실행한 테스트와 결과: TMI-25 집중 테스트가 성공했고 `./gradlew clean test`에서 126개 테스트 모두 실패·오류·건너뜀 없이 `BUILD SUCCESSFUL`이었다. Mockito 저장소로 결정적 insert와 optimistic version 충돌을 재현해 동시 submit·시험 retry의 문항당 dispatch 1회를 검증했으며 실제 Atlas·Redis·S3·AI 서버를 호출하지 않았다.
- 추가 검증: `git diff --check`, 기존/현재 Controller Mapping 비교, AI Body·`user_id`·retryCount·Redis/S3 Key 검색, 신규 로직의 직접 `Instant.now`/`LocalDateTime.now` 부재 확인, AWS Key·Private Key·credential 포함 Mongo URI·JWT literal 패턴 검색이 모두 통과했다. Stop Hook marker는 이 항목에 정확히 한 번 기록한다.
- 남아 있는 위험 요소: Learning Core의 원자 claim과 안정적인 Header만으로는 Python AI 내부 계산 중복까지 막을 수 없다. Job claim과 외부 HTTP 사이 crash window도 있으므로 Python AI가 두 멱등 키를 실제 저장·재사용해야 한다. 운영 S3 IAM의 HeadObject 권한, Mongo 신규 컬렉션 생성 권한, 기존 RestTemplate의 timeout·전체 음성 `byte[]` 메모리 사용을 staging에서 확인해야 한다.
- 다음 작업 전에 확인할 사항: 사용자가 변경분을 검토해 commit/push하고, Python AI의 Question/Summary `Idempotency-Key` 처리 작업을 별도 이슈로 진행한다. Jira 완료 댓글 등록이나 상태 전환은 별도 명시적 요청이 있을 때만 수행한다.

## 2026-07-28 — TMI-25 main 기준 코드 리뷰

<!-- codex-turn:review-20260728-bc15c504 -->

- 날짜: `2026-07-28`
- 브랜치: `feat/TMI-25-grading-retry-idempotency`
- Jira: [`TMI-25`](https://to-teacher.atlassian.net/browse/TMI-25)
- 작업 목표: 사용자가 지정한 merge base `bc15c504b4130e011cbb476d71a37e98e1d8a862`를 기준으로 `git diff` 전체를 검토하고 정확성·동시성·외부 계약·legacy 호환 문제를 우선순위화한다.
- 변경 파일: 리뷰 대상 애플리케이션 코드는 수정하지 않았고 작업 기록 규칙에 따라 `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`만 갱신했다. Git commit과 push, Jira 댓글·필드·상태 변경은 수행하지 않았다.
- 리뷰 결과: 이전 dispatch attempt가 timeout 후 실패하면 이미 claim된 다음 Question/Summary attempt를 FAILED로 덮어쓸 수 있는 경쟁 조건, 마지막 Feedback Callback 안의 동기 Summary POST로 인한 AI 단일 worker 교착 가능성, legacy 결과만 있고 Job이 없는 회차의 submit 중복 dispatch, `retry_count=null` legacy Azure 결과를 중복으로 버리면서 정확히 0만 조회하는 비호환을 actionable finding으로 확정했다.
- 유지한 외부 계약: 허용된 TMI-25 신규 retry API 외 기존 공개 URL·Method·Parameter·Response DTO·`BaseResponse`, `retryCount`, Redis Key·TTL, S3 Object Key, Python AI·Callback `user_id = examId`, 실제 `userId` 비노출과 사용자용 소유권 검증은 리뷰 중 변경하지 않았다.
- 실행한 검증과 결과: `git diff bc15c504b4130e011cbb476d71a37e98e1d8a862`, 변경 파일·커밋·관련 기존 코드와 테스트 정적 분석, `git diff --check`, `bash -n scripts/e2e/auth-integration-test.sh`를 수행했고 정적 검증은 성공했다. `./gradlew clean test`는 사용자 홈 Gradle lock 쓰기 제한으로 실패했고, cache를 `/tmp`에 복제한 재시도도 샌드박스가 Gradle file-lock contention socket 생성을 거부해 실행되지 않았다. 기존 `build/test-results`의 최신 XML은 126개 테스트, 실패·오류·건너뜀 0개다.
- 남아 있는 위험 요소: Mockito 저장소 테스트는 실제 Mongo `@Version` 동작과 늦게 종료되는 HTTP attempt 간 경쟁을 재현하지 않으며, 현재 `RestTemplate`에는 timeout이 없다. Python AI의 `Idempotency-Key` 처리 여부도 이 저장소만으로 확인할 수 없다.
- 다음 작업 전에 확인할 사항: 네 finding을 수정하고 늦은 이전 attempt 실패·Callback/AI 단일 worker 경계·legacy Job 부재 submit·Azure null retry 조회 회귀 테스트를 추가한 뒤, socket 사용이 허용된 환경에서 `./gradlew clean test`를 다시 실행한다.

## 2026-07-29 — TMI-25 main 기준 코드 리뷰 재검증

<!-- codex-turn:review-20260729-bc15c504 -->

- 날짜: `2026-07-29`
- 브랜치: `feat/TMI-25-grading-retry-idempotency`
- Jira: [`TMI-25`](https://to-teacher.atlassian.net/browse/TMI-25)
- 작업 목표: 사용자가 지정한 merge base `bc15c504b4130e011cbb476d71a37e98e1d8a862` 기준 전체 diff를 다시 검토하고 이전 리뷰 기록과 독립적으로 actionable finding을 확정한다.
- 변경 파일: 리뷰 대상 애플리케이션 코드는 수정하지 않았고 작업 기록 규칙에 따라 `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`만 갱신했다. Git commit·push와 Jira 변경은 수행하지 않았다.
- 리뷰 결과: timeout 뒤 새 attempt가 claim된 후 이전 HTTP dispatch가 늦게 실패하면 최신 Question/Summary Job을 FAILED로 덮어쓰는 경쟁 조건, Feedback Callback 안의 동기 Summary POST로 인한 단일·포화 AI worker 교착, legacy `ExamResult`만 존재하는 동일 submit의 재전송, `retry_count=null` legacy Azure 중복 억제와 정확히 0인 조회 조건의 불일치를 각각 P1·P1·P2·P2 finding으로 재확인했다.
- 유지한 외부 계약: 허용된 신규 시험 단위 retry API 외 기존 공개 API·DTO·`BaseResponse`, `retryCount`, Redis/S3 Key, Python AI·Callback `user_id = examId`, 실제 `userId` 비노출과 시험 소유권 계약은 변경하지 않았다.
- 실행한 검증과 결과: 전체 diff·관련 base 구현·현재 테스트를 정적으로 추적했고 `git diff --check`와 `bash -n scripts/e2e/auth-integration-test.sh`는 성공했다. `./gradlew clean test`는 사용자 홈 wrapper lock 쓰기 제한으로 시작되지 않았고, 별도 `/tmp` Gradle home 재시도도 sandbox의 file-lock contention socket 금지로 시작되지 않았다. 기존 XML 결과는 126개 테스트, 실패·오류·건너뜀 0개다.
- 남아 있는 위험 요소: 실제 Mongo optimistic locking과 지연 HTTP 응답 경쟁은 Mockito 테스트로 재현되지 않았고 Python AI worker·`Idempotency-Key` 구현은 이 저장소만으로 검증할 수 없다.
- 다음 작업 전에 확인할 사항: 네 finding을 수정한 회귀 테스트를 추가하고 socket 사용이 허용된 환경에서 `./gradlew clean test`를 다시 실행한다. Jira 상태 변경과 Git commit·push는 사용자가 수행한다.

## 2026-07-29 — TMI-25 채점 복구 변경 정밀 재리뷰

<!-- codex-turn:019fab73-024a-7842-b367-3edd9901f8dd -->

- 날짜: `2026-07-29`
- 브랜치: `feat/TMI-25-grading-retry-idempotency`
- Jira: [`TMI-25`](https://to-teacher.atlassian.net/browse/TMI-25)
- 작업 목표: AGENTS.md와 TMI-25 완료 조건을 기준으로 동일 submit·동시 시험 retry·Mongo claim·늦은 HTTP 실패·Callback/요약 멱등성·legacy 결과·attempt 상한·S3·Redis·외부 계약을 심각도 순으로 재검토한다.
- 비교 기준: 로컬 `main`은 `bc15c504b4130e011cbb476d71a37e98e1d8a862`으로 뒤처져 있고 브랜치는 저장된 `origin/main` `3746464` 위의 TMI-25 커밋 `fb354b6`이므로 TMI-25 고유 diff `3746464..fb354b6`를 주 기준으로 사용하고 로컬 main 전체 diff도 확인했다.
- Jira 확인: Atlassian MCP의 OAuth 재인증이 필요해 TMI-25 실시간 조회는 실패했다. Jira 변경 API는 호출하지 않았으며 저장소에 기록된 승인 설명·완료 조건과 현재 대화의 Jira 원문을 기준으로 검토했다.
- HIGH finding: 이전 Question/Summary HTTP dispatch가 timeout 뒤 늦게 실패하면 failure helper가 claimed attempt/version을 조건으로 쓰지 않고 최신 Job을 재조회해 다음 PROCESSING attempt를 FAILED로 덮을 수 있다. 마지막 Feedback Callback은 Summary POST까지 동기 실행하므로 AI worker가 단일·포화 상태이면 교착하거나 timeout 없는 Callback 스레드를 무기한 점유할 수 있다.
- MEDIUM finding: 기존 `ExamResult`만 있고 Job이 없는 회차의 동일 submit은 신규 Job을 만들고 AI를 재호출한다. 중복 Feedback도 항상 Summary gate를 호출하므로 기존 Summary가 FAILED 또는 timeout이면 실제 재dispatch가 발생한다. Azure retry 0 Callback은 legacy null 문서를 보고 신규 저장을 생략하지만 조회는 정확히 0만 사용해 결과가 계속 보이지 않을 수 있다.
- finding 없음: 현재 코드만 동시에 실행되는 조건에서 동일 신규 submit과 동시 시험 retry는 결정적 `_id`와 `@Version`으로 단일 dispatch된다. 네 Callback 저장의 결정적 ID·legacy 존재 확인, 11번 특별 Trigger 제거, 모든 필수 retryCount 0 gate, Summary timeout/FAILED retry, retryCount>0 제외, S3 403 전파, dispatchAttempt 상한, Mongo 기준 전체 상태, 기존 API·DTO·retryCount·Redis/S3 Key와 AI `user_id = examId`에서는 별도 actionable finding을 확인하지 않았다.
- 변경 파일: 리뷰 대상 애플리케이션·테스트 코드는 수정하지 않았다. 작업 기록 규칙에 따라 `docs/codex/WORKLOG.md` 끝에 이 항목을 append하고 `docs/codex/CURRENT_STATE.md`만 최신화했다. Git commit·push와 Jira 댓글·필드·상태 변경은 수행하지 않았다.
- 실행한 검증과 결과: TMI-25 고유 diff와 관련 base·현재 소스·테스트를 정적으로 추적하고 11번 특례, `@Version` claim, 결정적 결과 ID, timeout/attempt, Redis/S3/API/AI 계약을 검색했다. `git diff --check`는 성공했고 기존 XML 결과는 126개 테스트·실패·오류·건너뜀 0개다. 코드 변경 없는 리뷰이므로 Gradle 테스트는 다시 실행하지 않았다.
- 남아 있는 위험 요소: 실제 Mongo optimistic locking과 지연 HTTP의 교차 attempt 경쟁은 Mockito 저장소 테스트로 재현되지 않으며, Python AI의 `Idempotency-Key` 처리와 실제 worker 모델은 이 저장소만으로 검증할 수 없다.
- 다음 작업 전에 확인할 사항: HIGH 2건과 MEDIUM 3건을 수정하고 claimed attempt 조건 실패 전이, Callback 이후 Summary 비동기 경계, legacy result-only submit, duplicate Feedback+FAILED/timeout Summary, Azure null retry 조회 회귀 테스트를 추가한다.

## 2026-07-29 — TMI-25 리뷰 HIGH/MEDIUM 회귀 수정

<!-- codex-turn:019fabe5-0846-74a0-a52e-ed6320230252 -->

- 날짜: `2026-07-29`
- 브랜치: `feat/TMI-25-grading-retry-idempotency` (HEAD `fb354b6`, 수정은 미커밋 작업 트리)
- Jira: [`TMI-25`](https://to-teacher.atlassian.net/browse/TMI-25)
- 작업 목표: 기존 Question/Summary Job·시험 retry·Callback 멱등 구조를 유지하면서 이전 attempt 실패 덮어쓰기, Callback의 동기 Summary HTTP, legacy 결과 submit, Callback/retry Summary gate 혼합, Azure retry 0 legacy 조회 불일치를 수정한다.
- Jira 확인: Atlassian MCP 재조회는 OAuth `unauthorized_client`로 실패했다. 저장된 Jira 설명·완료 조건과 사용자의 현재 원문을 기준으로 구현했으며 Jira 댓글·필드·상태 변경 API는 호출하지 않았다.
- HIGH 1 수정: `QuestionDispatchClaim`, `SummaryDispatchClaim`에 `jobId + dispatchAttempt + claimedAt`을 고정하고 Question/Summary Repository의 `_id + PROCESSING + claimedAttempt` 조건 update로만 실패 전이한다. 0건이면 이전 attempt의 늦은 실패로 무시하며 최신 attempt나 COMPLETED를 재조회·`save()`로 덮지 않는다.
- HIGH 2 수정: `SummaryDispatchScheduler`와 bounded `ThreadPoolTaskExecutor`를 추가했다. Callback은 모든 필수 retry 0 완료 시 Summary PENDING을 확보하고 task만 제출하며 worker가 `@Version` claim에 성공한 경우에만 HTTP를 호출한다. queue rejection은 PENDING을 유지하고 AI connect/read timeout 기본값은 `PT3S`/`PT30S`다.
- MEDIUM 1 수정: submit의 Job insert 전에 `retryCount=0/null/missing` compatible Feedback 결과를 확인하고 COMPLETED Question Job을 지연 복구한다. 기존 non-COMPLETED Job보다 저장 결과를 우선해 완료 보정하며 결과 복사나 AI 재호출을 하지 않는다.
- MEDIUM 2 수정: Callback용 `ensureSummaryStartedIfReady`는 신규·기존 PENDING만 scheduler에 전달하고 FAILED/stale PROCESSING을 재시도하지 않는다. grading retry의 `retrySummaryIfEligible`만 FAILED·stale PENDING/PROCESSING과 max attempts를 판정해 recovery task를 제출한다.
- MEDIUM 3 수정: Azure retry 0 조회를 결정적 ID → 정확한 0 → BSON null → 필드 누락 순서로 분리했고 retryCount>0은 정확한 회차만 허용한다. null/missing 쿼리는 `_id` 혼합 정렬에 의존하지 않는다.
- 변경 파일: `ExamGradingService`, `ExamServiceImpl`, `GradingDispatchService`, Question/Summary/Azure Repository, `GradingConfig`, `GradingProperties`, `RestTemplateConfig`, `application.yml`, 신규 두 claim과 `SummaryDispatchScheduler`, 관련 서비스·dispatcher·설정 테스트, `SummaryDispatchSchedulerTest`, `GradingInfrastructureConfigTest`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`다.
- 유지한 외부 계약: 기존 API URL·Method·Parameter·DTO·`BaseResponse`, 신규 `POST /api/v1/exams/{examId}/grading/retry`, `retryCount`, Redis Key·TTL, S3 Object Key, Callback URL·JSON, `progressPercent=60`, 시험 소유권, Python AI Question/Summary `user_id = examId`와 기존 multipart/summary Body를 변경하지 않았다.
- 실행한 테스트와 결과: finding 집중 테스트와 `./gradlew clean test`가 성공했다. 전체 142개 테스트, 실패·오류·건너뜀 0개다. attempt 1 HTTP 대기 → timeout attempt 2 claim → attempt 1 늦은 실패를 Question/Summary 모두 재현했고 최종 PROCESSING/2와 null 실패 정보를 확인했다. 중복 scheduler task 단일 HTTP, queue rejection PENDING, claimed timeout 실패, Callback/retry gate, legacy null/0 submit 복구, Azure null/missing/positive retry, executor·timeout 설정도 검증했다.
- 추가 검증: `git diff --check`, 신규 로직의 직접 `Instant.now`/`LocalDateTime.now` 부재, Controller/DTO/GradingKeys 무변경, AI `user_id = examId`, Redis/S3 Key와 고정 progress 검색, 11번 특별 Trigger·이전 Summary 메서드 부재, AWS Key·Private Key·credential Mongo URI·JWT literal 패턴 검색이 모두 통과했다.
- 자체 리뷰: 요청된 HIGH/MEDIUM finding은 재현 테스트로 닫혔고 현재 변경에서 추가 HIGH/MEDIUM finding은 확인하지 않았다. 실제 Mongo Atlas·S3·Redis·Python AI 서버는 테스트에서 호출하지 않았다.
- 남아 있는 위험 요소: Learning Core의 안정적인 `Idempotency-Key`만으로 Python AI 내부 계산 중복은 막을 수 없으며 DB claim과 외부 HTTP 사이 crash window도 남는다. 배포 전 실제 Mongo `@Version`/repository update, S3 HeadObject IAM, bounded queue·timeout 적정값과 전체 음성 `byte[]` 메모리 사용을 staging에서 확인해야 한다.
- 다음 작업 전에 확인할 사항: 사용자가 diff를 검토해 commit/push하고 Python AI가 Question/Summary `Idempotency-Key`를 저장·재사용하는 별도 작업을 진행한다. Jira 완료 댓글과 상태 전환은 별도 명시적 요청이 있을 때만 수행한다.

## 2026-07-29 — TMI-25 main 기준 전체 변경 재리뷰

<!-- codex-turn:review-20260729-final-bc15c504 -->

- 날짜: `2026-07-29`
- 브랜치: `feat/TMI-25-grading-retry-idempotency` (HEAD `fb354b6`, 리뷰 시점의 미커밋 회귀 수정 포함)
- Jira: [`TMI-25`](https://to-teacher.atlassian.net/browse/TMI-25)
- 작업 목표: 사용자가 지정한 merge base `bc15c504b4130e011cbb476d71a37e98e1d8a862` 기준 `git diff` 전체를 다시 검토하고 정확성·성능·인증 Startup 검증·E2E 검증의 actionable finding을 확정한다.
- 변경 파일: 리뷰 대상 애플리케이션·테스트 코드는 수정하지 않았다. 작업 기록 규칙에 따라 `docs/codex/WORKLOG.md`에 이 항목을 append하고 `docs/codex/CURRENT_STATE.md`만 최신화했다. Git commit·push와 Jira 댓글·필드·상태 변경은 수행하지 않았다.
- 리뷰 결과: 시험 retry가 여러 Question의 S3/AI HTTP를 요청 스레드에서 직렬 실행해 장애 시 수분 동안 Tomcat 스레드를 점유하는 P1을 확인했다. 세션 생성 시점 문항 집합 미고정, legacy null/missing Azure fallback 무정렬, 확장 IPv6 loopback Startup 검증 우회, 이미 재사용 탐지로 폐기된 Refresh Token을 사용하는 단일 logout E2E, 한 세션만으로 `logout-all`을 검증하는 문제를 P2로 확인했다.
- 유지한 외부 계약: 허용된 신규 시험 단위 retry API 외 기존 공개 API·DTO·`BaseResponse`, `retryCount`, Redis/S3 Key, Python AI·Callback `user_id = examId`, 실제 `userId` 비노출과 사용자용 소유권 계약은 리뷰 중 변경하지 않았다.
- 실행한 검증과 결과: 전체 diff·base 구현·현재 소스와 관련 테스트를 정적으로 추적했고 `git diff --check bc15c504b4130e011cbb476d71a37e98e1d8a862`와 `bash -n scripts/e2e/auth-integration-test.sh`는 성공했다. `./gradlew clean test`는 사용자 홈 Gradle lock 쓰기 제한으로 시작되지 않았고, `/tmp` Gradle home 및 기존 배포본을 사용한 재시도도 각각 네트워크 차단과 sandbox의 file-lock contention socket 금지로 시작되지 않았다. 기존 `build/test-results` XML은 현재 소스 컴파일 이후 142개 테스트, 실패·오류·건너뜀 0개를 기록한다.
- 남아 있는 위험 요소: 실제 Mongo 정렬·인덱스와 S3/AI 지연, Python AI의 멱등 키 처리, 실제 Identity·Learning Core E2E는 이 리뷰 환경에서 실행하지 않았다. 로컬 `.idea`의 비추적 개발자 설정은 리뷰 대상 diff와 작업 기록에 포함하지 않았다.
- 다음 작업 전에 확인할 사항: finding별 회귀 테스트를 추가하고 socket 사용이 허용된 환경에서 `./gradlew clean test`와 실제 두 서버 E2E를 다시 실행한다. Jira 상태 변경과 Git commit·push는 사용자가 수행한다.

## 2026-07-29 — TMI-25 main 기준 전체 변경 리뷰 재검증

<!-- codex-turn:review-20260729-repeat-bc15c504 -->

- 날짜: `2026-07-29`
- 브랜치: `feat/TMI-25-grading-retry-idempotency` (HEAD `fb354b6`, 미커밋 회귀 수정 포함)
- Jira: [`TMI-25`](https://to-teacher.atlassian.net/browse/TMI-25)
- 작업 목표: 사용자가 지정한 merge base `bc15c504b4130e011cbb476d71a37e98e1d8a862` 기준 전체 작업 트리를 다시 검토하고 우선순위가 있는 actionable finding을 확정한다.
- 변경 파일: 리뷰 대상 애플리케이션·테스트 코드는 수정하지 않았다. 작업 기록 규칙에 따라 `docs/codex/WORKLOG.md` 끝에 이 항목을 append하고 `docs/codex/CURRENT_STATE.md`의 최신 리뷰 상태만 갱신했다. Git commit·push와 Jira 댓글·필드·상태 변경은 수행하지 않았다.
- 리뷰 결과: 여러 문항의 동기·직렬 S3/AI dispatch로 요청/Tomcat 스레드가 장시간 점유되는 P1을 확인했다. 세션별 문항 집합 미고정, legacy Azure fallback 무정렬, loopback IP 표기 우회, 이미 폐기된 Refresh Token을 사용하는 단일 logout 검증, 활성 Session 하나만 사용하는 logout-all 검증을 각각 P2로 확정했다.
- 유지한 외부 계약: 허용된 신규 `POST /api/v1/exams/{examId}/grading/retry` 외 기존 공개 API·DTO·`BaseResponse`, `retryCount`, Redis/S3 Key, Python AI·Callback `user_id = examId`, 실제 `userId` 비노출과 사용자용 소유권 계약은 리뷰 중 변경하지 않았다.
- 실행한 검증과 결과: `git diff bc15c504b4130e011cbb476d71a37e98e1d8a862`, 관련 base·현재 소스·테스트 정적 추적, `git diff --check`와 `bash -n scripts/e2e/auth-integration-test.sh`를 수행했고 정적 검증은 성공했다. Java URI 표기 확인으로 확장 IPv6·IPv4-mapped IPv6·정수형 IPv4 loopback이 현재 문자열 검사에서 누락됨을 재현했다.
- 테스트 결과: `./gradlew clean test --no-daemon`은 사용자 홈 Gradle wrapper lock 쓰기 제한으로 시작되지 않았다. Gradle wrapper/cache를 `/tmp`의 쓰기 가능한 home으로 복제한 offline 재시도도 sandbox가 Gradle file-lock contention socket 생성을 금지해 시작되지 않았다. 기존 `build/test-results` XML은 현재 소스로 컴파일된 142개 테스트, 실패·오류·건너뜀 0개다.
- 남아 있는 위험 요소: 실제 Mongo legacy 문서 선택 순서, S3/AI 지연 시 retry API 응답 시간, Python AI의 `Idempotency-Key`, 실제 Identity·Learning Core E2E는 이 환경에서 실행하지 않았다.
- 다음 작업 전에 확인할 사항: 여섯 finding별 회귀 테스트를 추가하고 socket 사용이 허용된 환경에서 전체 Gradle 테스트와 실제 두 서버 E2E를 다시 실행한다. Jira 상태 변경과 Git commit·push는 사용자가 수행한다.

## 2026-07-29 — Codex 마지막 세션 재개 요청 확인

<!-- codex-turn:019fac7a-23dd-7f40-8dbe-dcf8c6df9e9f -->

- 날짜: `2026-07-29`
- 브랜치: `feat/TMI-25-grading-retry-idempotency` (HEAD `fb354b6`, 기존 미커밋 변경 유지)
- Jira: [`TMI-25`](https://to-teacher.atlassian.net/browse/TMI-25)
- 작업 목표: 사용자 입력 `codex resume --last`의 실행 필요 여부를 확인한다.
- 처리 결과: 현재 대화가 이미 마지막 세션을 이어서 실행 중이므로 중첩된 대화형 Codex 프로세스는 시작하지 않았다.
- 변경 파일: 작업 기록 규칙에 따라 `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`만 갱신했다. 애플리케이션·테스트 코드, Jira, Git 상태는 변경하지 않았다.
- 실행한 테스트: 코드 변경이 없어 Gradle 테스트를 다시 실행하지 않았다.
- 다음 작업 전에 확인할 사항: 사용자가 원하면 현재 세션에서 TMI-25 리뷰 또는 finding 수정을 계속한다. Atlassian 로그인이 필요하면 로컬 터미널에서 인증 명령을 완료한다.

## 2026-07-29 — Jira TMI-25 완료 전환

<!-- codex-turn:019fac8e-40ff-7883-ac66-b9dbf1c56954 -->

- 날짜: `2026-07-29`
- 브랜치: `feat/TMI-25-grading-retry-idempotency` (HEAD `fb354b6`, 기존 미커밋 변경 유지)
- Jira: [`TMI-25`](https://to-teacher.atlassian.net/browse/TMI-25)
- 작업 목표: 사용자 요청에 따라 TMI-25만 완료 상태로 닫는다.
- 실행 내용: Atlassian MCP로 전환 직전 상태 `진행 중`과 사용 가능한 `완료` 전환 ID `41`을 읽기 전용으로 확인한 뒤, TMI-25에 전환 ID `41`만 적용했다. Payload에는 필드·댓글·history metadata·update를 포함하지 않았다.
- 결과: 후속 상세 조회에서 상태 `완료`(ID `10003`, 완료 범주)와 resolution `완료`(ID `10000`)를 확인했다. 다른 Jira 이슈와 다른 필드는 수정하지 않았다.
- 변경 파일: 작업 기록 규칙에 따라 `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`만 갱신했다. 애플리케이션·테스트 코드는 변경하지 않았고 Git commit·push를 수행하지 않았다.
- 실행한 테스트: Jira 상태와 문서만 변경해 Gradle 테스트는 다시 실행하지 않았다. `git diff --check`로 기록 변경을 검증한다.
- 남아 있는 위험 요소: 완료 댓글은 등록하지 않았으며 작업 트리에는 이번 Jira 종료 기록 문서 변경만 남아 있다.
- 다음 작업 전에 확인할 사항: TMI-25를 다시 열거나 댓글을 추가하려면 별도 명시적 요청이 필요하다.

## 2026-07-29 — 사용자별 모의고사 순차 배정 Jira Payload 초안

<!-- codex-turn:019fac91-aa46-7d70-9141-d1a7771a14bf -->

- 날짜: `2026-07-29`
- 브랜치: `main` (HEAD `b71b54b`)
- Jira: 미생성 — 프로젝트 `TMI`
- 작업 목표: `[Learning Core] 사용자별 모의고사 순차 배정 및 순환 제공` 작업 이슈의 최종 Payload를 작성하되 Jira에는 아직 생성하지 않는다.
- Atlassian 확인: 프로젝트 `TMI`(ID `10000`)에 이슈 생성 권한이 있고 이슈 유형 `작업`(ID `10003`), 설명 필드와 우선순위 `High`(ID `2`)를 지원한다. 동일 제목의 기존 이슈는 검색 결과에서 확인되지 않았다.
- Payload 범위: 사용자별 활성 MockExam 순차·순환 배정, 최소 완료 횟수와 sequence 기반 선택, 활성 ExamSession 재사용·동시 생성 단일화, 선택된 `mockExamId`의 S3·조회·AI·retry·summary 전 과정 연동, legacy `mockExamId=null`의 `mock_exam_003` fallback, Summary Callback 뒤 세션 완료 처리를 설명과 완료 조건에 포함했다.
- 실제 전송 예정 필드: `projectKey`, `issueTypeName`, `summary`, Markdown `description`, `additional_fields.priority`만 포함한다. 담당자·라벨·스프린트·에픽·상위 항목·상태 전환은 설정하지 않는다.
- 변경 파일: 작업 기록 규칙에 따라 `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`만 갱신했다. 애플리케이션·테스트 코드와 Jira는 변경하지 않았고 Git commit·push를 수행하지 않았다.
- 실행한 테스트: 코드 변경이 없어 Gradle 테스트는 다시 실행하지 않았다. 문서 변경은 `git diff --check`로 검증한다.
- 남아 있는 위험 요소: 실제 구현 전 동시 활성 세션 보장을 위한 DB 제약·원자 claim 방식과 기존 세션/MockExam 데이터의 `sequence`, `active`, `mockExamId` 호환 전략을 확정해야 한다.
- 다음 작업 전에 확인할 사항: 사용자가 Payload를 승인하면 동일 제목·설명과 `High`로 Jira 작업 이슈를 생성하고, 담당자·스프린트·에픽·라벨·상태는 기본값으로 둔다.

## 2026-07-29 — Jira TMI-31 생성

<!-- codex-turn:019fac96-6684-7260-ba3b-44f2a5f0ceb3 -->

- 날짜: `2026-07-29`
- 브랜치: `main` (HEAD `b71b54b`)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31)
- 작업 목표: 사용자가 승인한 `[Learning Core] 사용자별 모의고사 순차 배정 및 순환 제공` Payload를 프로젝트 TMI의 작업 이슈로 생성한다.
- 실행 내용: Atlassian MCP 생성 요청에는 프로젝트 `TMI`, 이슈 유형 `작업`, 승인된 제목·Markdown 설명, 우선순위 `High`만 포함했다. 담당자·라벨·스프린트·에픽·상위 항목·상태 전환은 전송하지 않았다.
- 결과: Jira `TMI-31`(ID `10030`)이 생성됐다. 후속 상세 조회에서 승인된 제목·설명, 이슈 유형 `작업`(ID `10003`), 우선순위 `High`(ID `2`), 기본 상태 `해야 할 일`(ID `10000`), 담당자 미지정과 빈 라벨을 확인했다.
- 변경 파일: 작업 기록 규칙에 따라 `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`만 갱신했다. 애플리케이션·테스트 코드는 변경하지 않았고 Git commit·push를 수행하지 않았다.
- 실행한 테스트: Jira와 문서 기록만 변경해 Gradle 테스트는 다시 실행하지 않았다. 문서 변경은 `git diff --check`로 검증한다.
- 남아 있는 위험 요소: 구현 전 사용자당 활성 ExamSession 하나를 동시성 하에서도 보장할 DB 제약·원자 생성 방식과 legacy 데이터 fallback 범위를 확정해야 한다.
- 다음 작업 전에 확인할 사항: 구현을 시작하기 전에 Jira 설명과 현재 코드의 시험 생성·MockExam 조회·S3/AI/retry/summary 전파 지점을 기준으로 정적 분석한다. Jira 상태 변경은 별도 명시적 요청이 있을 때만 수행한다.

## 2026-07-29 — TMI-31 사용자별 모의고사 순차·순환 배정 구현

<!-- codex-turn:019fac9e-e037-79a1-bdae-2dd7beaf332b -->

- 날짜: `2026-07-29`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD `b71b54b`, 구현은 미커밋 작업 트리)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 상태 `해야 할 일`; Atlassian MCP로 설명과 완료 조건을 먼저 읽었고 댓글·필드·상태를 변경하지 않았다.
- 작업 목표: 기존 `POST /api/v1/exams` 계약을 유지하면서 사용자별 활성 MockExam을 완료 횟수와 숫자 sequence 순으로 순차·순환 배정하고, 진행 중 세션 재사용·동시 생성 단일화·Summary 성공 기반 완료와 선택된 `mockExamId` 전파를 구현한다.
- 순차·순환 선택: 활성·비어 있지 않은 시험만 catalog에 포함하고 `sequence>=1`을 검증한다. `active=null`은 true, `sequence=null`은 `mockExamId` 끝 숫자를 임시 sequence로 해석한다. 활성 시험의 중복 또는 해석 불가 sequence는 `EXAM_5001`로 실패한다. 현재 사용자의 `completedAt != null` 세션만 시험별 완료 횟수로 집계해 최소 횟수 후보 중 sequence가 가장 작은 시험을 선택하고 `cycleNumber=completionCount+1`로 저장한다.
- 활성 세션 재사용: `active=true` 또는 legacy `active` 누락/null이면서 미완료인 현재 사용자 세션을 먼저 조회한다. 있으면 새 examId나 다음 시험을 만들지 않고 같은 세션·문제지로 `CreateSessionResult`를 재구성하되 문제·가이드 Presigned URL은 새로 발급한다. Redis 상태가 없으면 Mongo Question/Summary Job과 결과를 기준으로 기존 Key/TTL에 복구한다.
- 동시 생성 방지: 신규 세션은 repository `insert`를 사용하고 `{userId:1}` + partial `{active:true}` unique index `uniq_exam_sessions_active_user`를 전제로 한다. 동시 loser의 `DuplicateKeyException`은 500으로 노출하지 않고 승자 활성 세션을 재조회하며, 승자가 재조회 전에 완료되는 드문 경쟁에는 세 번의 bounded 재시도를 적용한다.
- 완료 처리: Summary Callback이 `ExamSummary` 저장을 신규 또는 기존 멱등 성공으로 확인한 뒤에만 기존 `Clock` 시각으로 `completedAt`을 설정하고 `active=false`로 전환한다. `completedAt is null` 조건 원자 update라 중복 Callback은 no-op이며 저장 실패, Summary PROCESSING 또는 문항 결과 완료만으로는 세션을 완료하지 않는다.
- `mockExamId` 전파: 세션 선택값을 문제 조회, `questions/{mockExamId}/q_N.wav`, `part3_intro.wav`, `QuestionGradingJob`, 문항 AI multipart, 시험 grading retry 예상 문항, `SummaryGradingJob`, Summary AI JSON과 문항 상세 결과 조회에 사용한다. 기존 Job 값이 없으면 세션을 조회하고 세션도 없거나 null/blank이면 `mock_exam_003` legacy fallback만 사용한다. Callback 결과는 세션의 canonical 시험 ID로 저장한다.
- 기존 데이터 호환: `ExamSession`에 `mockExamId`, `cycleNumber`, `active`, `completedAt`을 추가하되 자동 migration을 하지 않는다. legacy null `mockExamId`는 `mock_exam_003`으로 해석하고 active 누락/null 미완료 세션은 재사용 가능하다. 여러 legacy 후보가 있으면 런타임은 최신을 선택하고 경고하며 migration apply는 운영자 정리 전 중단한다.
- migration: `scripts/mongodb/tmi-31-migrate-exam-assignment.js`와 README를 추가했다. 기본 dry-run이며 `TMI31_APPLY=true`일 때만 MockExam/Session legacy 필드 보정과 partial unique index 생성을 수행한다. 실제 Mongo URI나 Secret은 저장하지 않았고 애플리케이션 시작 시 데이터·인덱스를 자동 생성하거나 수정하지 않는다.
- 변경 파일: `ExamSessionManager`, `MockExamCatalogService`, `ExamServiceImpl`, `ExamGradingService`, `GradingDispatchService`, `GradingKeys`, Question/Summary claim·scheduler·converter, `ExamSession`, `MockExam`, Question/Summary Job, `ExamSessionRepository`, `ErrorStatus`, Mongo migration 스크립트·README, 관련 application/security 테스트, 신규 manager/catalog 테스트와 Codex 문서다.
- 유지한 외부 계약: `POST /api/v1/exams` URL·Method·Request Body 없음, 기존 `CreateSessionResult` 필드와 `BaseResponse`, 나머지 공개 API URL·Method·Parameter·DTO, `retryCount`, Redis Key·TTL, 제출 S3 Key, 음성 제출·Polling, AI Callback JSON을 변경하지 않았다. Python AI Question/Summary `user_id`와 Callback `user_id`는 계속 `examId`이며 실제 사용자 UUID를 AI로 보내거나 외부 DTO에 추가하지 않았다.
- 테스트와 검증: 집중 테스트와 `./gradlew clean test --no-daemon`이 성공했다. 전체 169개 테스트, 실패·오류·건너뜀 0개다. 선택·순환 30개 요구 범주, 활성 재사용·Presigned URL 갱신·Redis 복구, 동시 unique 충돌, Summary 완료 원자성, 전 과정 `mockExamId`, legacy fallback, API DTO와 AI `user_id=examId` 회귀를 외부 인프라 없이 검증했다. `git diff --check`, migration `node --check`, API/DTO·hardcode·AI user_id·Secret·직접 시간 호출 검색도 통과했다.
- 남아 있는 위험 요소: 운영 partial unique index 설치 전에는 다중 인스턴스 동시 생성 보장이 완성되지 않는다. 실제 Atlas·Redis·S3·Python AI는 호출하지 않았고, 여러 legacy 활성 세션·중복 sequence·호환되지 않는 기존 인덱스는 dry-run 보고를 바탕으로 운영자가 먼저 조정해야 한다. 진행 중 시험지의 삭제·빈 문제지화·문항 변경은 재사용과 완료 기준에 영향을 줄 수 있다.
- Jira 완료 댓글 초안: `TMI-31 구현 완료: 기존 POST /api/v1/exams 계약을 유지하면서 사용자별 완료 횟수+sequence 순차·순환 배정, 활성 세션 재사용, partial unique index 기반 동시 생성 단일화, Summary 저장 성공 후 원자적 완료, 선택된 mockExamId의 S3·Job·AI·retry·summary·조회 전파와 legacy fallback을 구현했습니다. dry-run migration/index 스크립트를 추가했고 전체 169개 테스트 및 정적 계약·Secret 검사가 통과했습니다. 운영 적용 전 migration dry-run과 staging 실제 MongoDB·Redis·S3·AI smoke test가 필요합니다.` 이 초안은 Jira에 등록하지 않았다.
- 다음 작업 전에 확인할 사항: 운영 DB를 백업하고 migration dry-run 결과를 검토해 legacy 충돌을 정리한 뒤 명시적 apply로 index를 설치한다. staging에서 동시 세션 생성, 순환 배정, Summary 완료 전이와 실제 S3/AI `mockExamId` 전파를 smoke test한다. 사용자가 diff를 검토해 commit과 push를 수행하며 Jira 댓글·상태 변경은 별도 요청 시에만 진행한다.

## 2026-07-29 — TMI-31 stream 중단 후 최종 검증 재개

<!-- codex-turn:019facd2-7ec4-78b3-80d0-64355d223820 -->

- 날짜: `2026-07-29`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD `b71b54b`, 기존 미커밋 구현 유지)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 상태 `해야 할 일`; Jira 댓글·필드·상태는 변경하지 않았다.
- 작업 목표: WebSocket/HTTPS stream 중단 시점의 Git diff와 완료 상태를 먼저 확인하고, 이미 완료된 TMI-31 구현을 다시 작성하거나 되돌리지 않은 채 남은 전체 재검토·검증·기록만 마무리한다.
- 마지막 완료 상태 확인: 사용자별 sequence/완료 횟수 선택, 활성 세션 재사용, partial unique index 충돌 복구, Summary 저장 후 완료, 전 과정 `mockExamId`, legacy fallback, dry-run migration과 관련 테스트가 작업 트리에 그대로 있음을 확인했다. 새 구현을 중복 적용하거나 기존 변경을 되돌리지 않았다.
- 재검토 결과: Controller·Request/Response DTO·`BaseResponse`에는 diff가 없고 AI Question/Summary `user_id`는 `claim.examId()`로 유지된다. 운영 코드의 `mock_exam_003`은 `LEGACY_MOCK_EXAM_ID` 한 곳으로 제한됐으며 신규 시간 로직은 기존 UTC `Clock`만 사용한다. 변경 코드에서 추가로 수정해야 할 정확성·계약 위반은 확인하지 않았다.
- 검증 결과: `git diff --check`와 migration `node --check`가 성공했다. `./gradlew clean test --no-daemon`은 clean compile부터 실행해 전체 169개, 실패·오류·건너뜀 0개로 성공했고 기존 `ExamServiceImpl` unchecked 컴파일 경고만 남았다. XML 집계도 `tests=169 failures=0 errors=0 skipped=0`을 확인했다. Secret 패턴 검색은 결과가 없었다.
- 변경 파일: 재개 구간에서는 애플리케이션·테스트 코드를 변경하지 않았고 `docs/codex/CURRENT_STATE.md`를 최종 상태로 보완하고 `docs/codex/WORKLOG.md` 끝에 이 항목만 append했다. 기존 TMI-31 애플리케이션·테스트·migration 변경은 그대로 보존했다.
- 남아 있는 위험 요소: 운영 apply 전에는 partial unique index 기반 다중 인스턴스 단일 활성 세션 보장이 완성되지 않는다. Summary insert와 세션 완료 update 사이 crash window는 동일 Callback 재전달로 복구해야 한다. 실제 Atlas·Redis·S3·Python AI 연동은 실행하지 않았다.
- 다음 작업 전에 확인할 사항: 운영 DB 백업과 migration dry-run 결과를 검토하고 legacy 충돌을 정리한 뒤 명시적 apply로 `uniq_exam_sessions_active_user`를 설치한다. staging에서 동시 생성·순환 배정·Summary Callback 재시도·S3/AI 전파를 smoke test한다. commit과 push는 사용자가 수행한다.
- 추가 최종 검증: 현재 diff를 확인한 뒤 사용자 지정 명령 `./gradlew clean test`를 다시 실행했고 `BUILD SUCCESSFUL`, XML 기준 169개·실패·오류·건너뜀 0개를 확인했다. 이 재실행에서도 기존 `ExamServiceImpl` unchecked 경고만 남았다.

## 2026-07-29 — TMI-31 application 패키지 역할 분석

<!-- codex-turn:019face6-7b48-7ea1-bb0f-88b539ee317c -->

- 날짜: `2026-07-29`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD `b71b54b`, 기존 미커밋 구현 유지)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 상태 `해야 할 일`; Jira 댓글·필드·상태를 변경하지 않았다.
- 작업 목표: 현재 `domain/exams/application` 패키지의 각 파일이 맡는 책임과 상호 호출 관계를 코드 기준으로 정리한다.
- 분석 범위: `ExamService`, `ExamServiceImpl`, `ExamSessionManager`, `MockExamCatalogService`, `ExamGradingService`, `SummaryDispatchScheduler`, `GradingDispatchService`, `QuestionDispatchClaim`, `SummaryDispatchClaim`, `GradingKeys`의 전체 메서드와 `ExamRestController`의 호출 지점을 읽기 전용으로 확인했다.
- 구조 요약: `ExamServiceImpl`이 API 파사드로 소유권·S3·Callback·조회 흐름을 조율하고, 세션 선택은 `ExamSessionManager`와 `MockExamCatalogService`, Job 상태·retry·Redis projection은 `ExamGradingService`, 비동기 Summary claim은 `SummaryDispatchScheduler`, 실제 Python AI HTTP 전송은 `GradingDispatchService`가 담당한다. 두 Claim record는 claim 시점 데이터를 고정하고 `GradingKeys`는 결정적 식별자와 legacy fallback을 중앙화한다.
- 변경 파일: 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`를 갱신하고 `docs/codex/WORKLOG.md` 끝에 이 항목만 append했다. 애플리케이션·테스트·migration 코드는 변경하지 않았다.
- 유지한 계약: 기존 공개 API URL·Method·Parameter·DTO·`BaseResponse`, `retryCount`, Redis/S3 Key, AI·Callback `user_id=examId`, 선택된 `mockExamId` 전파와 실제 사용자 UUID 비노출 계약을 변경하지 않았다.
- 실행한 검증: 코드 변경 없는 설명 작업이므로 Gradle 테스트는 다시 실행하지 않았다. 직전 `./gradlew clean test`의 169개·실패·오류·건너뜀 0개 결과를 유지하며 문서 변경에는 `git diff --check`를 실행한다.
- 남아 있는 위험 요소: 이번 작업은 역할 설명만 수행해 새 구현 위험은 없다. 이후 책임 분리나 리팩터링을 진행한다면 `ExamServiceImpl`의 API 계약과 `ExamGradingService`의 멱등·동시성 상태 전이를 별도 회귀 테스트로 보호해야 한다.
- 다음 작업 전에 확인할 사항: 구조 변경 요청이 생기면 기존 외부 계약을 유지한 채 대상 책임과 테스트 범위를 먼저 확정한다. commit과 push는 사용자가 수행한다.

## 2026-07-29 — TMI-31 main 기준 코드 리뷰

<!-- codex-turn:review-20260729-tmi31-b71b54b -->

- 날짜: `2026-07-29`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD `b71b54b`, 기존 미커밋 구현 유지)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 상태 `해야 할 일`; Atlassian MCP로 제품 규칙·선택 알고리즘·완료 조건을 읽기 전용 재확인했고 댓글·필드·상태는 변경하지 않았다.
- 작업 목표: 사용자가 지정한 merge base `b71b54bb4ff871a8e082cd6d94a34007c84b062c` 기준 tracked diff와 신규 미추적 구현·migration 파일을 함께 검토하고 우선순위가 있는 actionable finding을 확정한다.
- 리뷰 결과: main에서 생성된 legacy Session은 Summary가 저장됐어도 `active`·`completedAt`이 없으므로 신규 쿼리/migration에서 진행 중으로 재사용되어 다음 시험 배정이 영구 차단되는 P1을 확인했다. 별도 `MONGODB_DATABASE`를 무시하는 migration 실행 예시, 완료 이력 조회를 지원하지 못하는 partial-only 인덱스, 서로 다른 sequence에서 중복 `mockExamId`를 허용해 완료 횟수와 배정을 합치는 문제를 P2로 확인했다.
- 변경 파일: 리뷰 대상 애플리케이션·테스트·migration 코드는 수정하지 않았다. 작업 기록 규칙에 따라 `docs/codex/CURRENT_STATE.md`를 최신화하고 `docs/codex/WORKLOG.md` 끝에 이 항목만 append했다. Git commit·push와 Jira 쓰기 작업은 수행하지 않았다.
- 유지한 외부 계약: 리뷰 중 기존 공개 API URL·Method·Parameter·DTO·`BaseResponse`, `retryCount`, Redis/S3 Key, AI·Callback `user_id=examId`, 실제 사용자 UUID 비노출과 시험 소유권 코드를 변경하지 않았다.
- 실행한 검증: 전체 diff·base 세션 생성/Callback·현재 세션 선택/완료·catalog·migration·인덱스 흐름을 정적으로 추적했다. `git diff --check`, 신규 파일 `git diff --no-index --check`, `node --check scripts/mongodb/tmi-31-migrate-exam-assignment.js`가 성공했다.
- 테스트 결과: fresh `./gradlew clean test --no-daemon`은 sandbox가 사용자 Gradle wrapper lock 쓰기를 금지해 시작되지 않았다. 기존 cache를 `/tmp`의 쓰기 가능한 Gradle home으로 clone한 offline 직접 실행도 Gradle file-lock contention socket 생성이 금지되어 시작되지 않았다. 직전 현재 소스의 `build/test-results` XML은 18개 suite, 169개 테스트와 실패·오류·건너뜀 0개를 기록한다.
- 남아 있는 위험 요소: 실제 MongoDB explain/index build, legacy Summary 데이터 migration, Atlas·Redis·S3·Python AI 연동은 실행하지 않았다. Summary insert와 Session 완료 update 사이 crash window와 rolling deployment 중 구버전 Session 쓰기도 운영 검증이 필요하다.
- 다음 작업 전에 확인할 사항: 네 finding을 수정하고 summarized legacy Session backfill/복구, 명시적 DB 선택 migration, 완료 이력 explain/index, duplicate `mockExamId` catalog 실패 회귀 테스트를 추가한 뒤 socket 사용이 허용된 환경에서 `./gradlew clean test`와 staging migration dry-run을 다시 실행한다.

## 2026-07-29 — TMI-31 최종 코드 리뷰

<!-- codex-turn:019fad07-1933-77c1-b0f8-c4336cb676bb -->

- 날짜: `2026-07-29`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD 및 main merge base `b71b54bb4ff871a8e082cd6d94a34007c84b062c`, 기존 미커밋 구현 유지)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 상태 `해야 할 일`; Atlassian MCP로 제품 규칙·선택 알고리즘·완료 조건을 읽기 전용 재확인했고 댓글·필드·상태는 변경하지 않았다.
- 작업 목표: AGENTS.md와 Jira TMI-31을 기준으로 사용자 지정 14개 정확성·동시성·완료·전파·호환성·migration 항목을 최종 검토하고 HIGH·MEDIUM·LOW finding만 확정한다.
- HIGH finding: main에서 생성된 legacy Session은 완료 Summary가 있어도 `active`, `completedAt`이 모두 없으며, 현재 재사용 쿼리와 migration은 Summary를 확인하지 않고 이를 활성 세션으로 취급한다. 기존 완료 사용자는 같은 시험을 계속 재사용해 순차·순환 배정이 차단된다.
- MEDIUM findings: partial unique index 미적용 환경에서는 두 동시 insert가 모두 성공할 수 있고 애플리케이션 검증·대체 원자화가 없다. migration 명령은 별도 `MONGODB_DATABASE`를 선택하지 않는다. 완료 이력 조회는 partial active index를 사용할 수 없어 이력 증가 시 전체 scan 위험이 있다. 서로 다른 sequence의 활성 문서가 같은 `mockExamId`를 가져도 catalog와 migration이 거부하지 않는다.
- LOW finding: 없음.
- finding 없음으로 확인한 범위: 최소 완료 횟수+sequence 선택과 cycle 증가, 사용자별 repository 격리, 정상 활성 세션 재사용, index 적용 시 DuplicateKey winner 재조회, Summary 저장 뒤 완료와 중복 완료 시각 불변, 선택된 `mockExamId`의 문제·S3·Question AI·retry·Summary AI 전파, legacy null의 003 fallback, inactive·빈 시험 제외, 중복/해석 불가 sequence 실패, 기존 API·DTO·Redis/S3·AI `user_id=examId` 계약, migration 기본 dry-run이다.
- 변경 파일: 리뷰 대상 애플리케이션·테스트·migration 파일은 수정하지 않았다. 필수 기록을 위해 `docs/codex/CURRENT_STATE.md`를 최신화하고 `docs/codex/WORKLOG.md` 끝에 이 항목만 append했다. Git commit·push는 수행하지 않았다.
- 실행한 검증: 전체 tracked diff와 신규 파일, base의 legacy Session 생성, 현재 세션 선택·완료·catalog·Job/AI 전파·migration/인덱스·Controller/DTO 계약을 정적으로 추적했다. `git diff --check`와 `node --check scripts/mongodb/tmi-31-migrate-exam-assignment.js`는 성공했다.
- 테스트 결과: 현재 소스에서 `./gradlew clean test`가 `BUILD SUCCESSFUL`로 끝났고 XML 집계는 169개 테스트, 실패·오류·건너뜀 0개다. 기존 `ExamServiceImpl` unchecked 경고만 남았다.
- 남아 있는 위험 요소: 실제 MongoDB explain/index build와 운영 legacy Summary backfill, Atlas·Redis·S3·Python AI 연동은 실행하지 않았다. 다섯 finding을 해소하기 전에는 기존 사용자 순환과 미적용/오적용 index 환경의 동시성 보장을 완료로 판단하기 어렵다.
- 다음 작업 전에 확인할 사항: summarized legacy Session 복구 정책과 backfill, index 필수성 검증 또는 대체 원자화, 명시적 application DB 선택, 완료 이력 supporting index, duplicate `mockExamId` 검증/인덱스를 설계하고 각각 회귀 테스트를 추가한다. Jira와 Git 변경은 별도 사용자 요청에만 수행한다.

## 2026-07-30 — TMI-31 HIGH/MEDIUM 리뷰 finding 수정

<!-- codex-turn:019fb0a1-4b49-7e93-acf8-870755f9cb64 -->

- 날짜: `2026-07-30`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD `b71b54b`, 전체 변경은 미커밋 작업 트리)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 상태 `해야 할 일`; Atlassian MCP로 설명·완료 조건·현재 상태를 읽기 전용 재확인했고 댓글·필드·상태를 변경하지 않았다.
- 작업 목표: 기존 순차·순환 선택, 진행 중 세션 재사용, Summary 저장 후 완료와 `mockExamId` 전파 구조 및 모든 외부 계약을 유지하면서 최종 리뷰의 HIGH 1건과 MEDIUM 4건만 수정한다.
- HIGH legacy 완료 판정: `ExamSession.active`를 legacy null/누락을 보존하는 `Boolean`으로 바꾸고, 활성/legacy 후보를 사용자별 최신순으로 조회한 뒤 null active+null completedAt만 `ExamSummary`를 확인한다. 결정적 Summary ID와 examId 기반 legacy ObjectId 문서를 합쳐 조회하고, Summary가 있으면 재사용하지 않는다. ObjectId 생성 시각, `SummaryGradingJob.completedAt`, 마지막 호환 Clock 순으로 완료 시각을 해석해 active/completedAt이 여전히 없는 문서만 조건부 원자 보정한다. 중복 Summary는 임의 선택 없이 `EXAM_5002`로 실패한다.
- MEDIUM active index 검증: 신규 `ExamAssignmentIndexValidator`가 `uniq_exam_sessions_active_user`의 이름, `{userId:1}` 키, unique, `{active:true}` partial 조건을 정확히 확인한다. staging/prod는 누락·오정의·조회 실패 시 fail-closed하고 local은 경고한다. `test` profile에서는 컴포넌트를 제외하고 단위 테스트의 mock IndexOperations만 사용한다. `uniq_mock_exams_mock_exam_id`도 운영 필수로 검증하고 완료 조회 인덱스는 성능 경고로 분리했다.
- MEDIUM DB 선택 migration: Node entrypoint가 `MONGODB_URI`, 필수 `MONGODB_DATABASE`, 선택 `TMI31_APPLY`를 처리하고 같은 파일을 `mongosh --nodb --quiet` payload로 실행한다. URI 내 DB와 무관하게 `getSiblingDB(MONGODB_DATABASE)`를 사용하며 공백/시스템 DB를 거부하고 선택 DB·collection·예정 변경 수를 dry-run과 apply 직전에 출력한다. URI와 자격증명은 출력하지 않는다.
- MEDIUM 완료 횟수 집계: `ExamSessionCompletionQuery`가 Mongo aggregation으로 현재 `userId`와 `completedAt exists/non-null`만 필터링하고 null `mockExamId`를 `mock_exam_003`으로 치환해 시험별 count만 반환한다. 전체 완료 Session Entity 목록 조회를 제거했다. migration은 `{userId:1, completedAt:1, mockExamId:1}`의 `idx_exam_sessions_user_completed_mock_exam`을 apply에서 생성한다.
- MEDIUM catalog identity: 전체 catalog에서 null/blank/앞뒤 공백/중복 `mockExamId`를 거부하고 활성 sequence 검증을 유지한다. Repository 단건 Optional 대신 List 조회를 사용하며 2개 이상이면 설정 오류로 실패한다. 문제 상세 조회와 retry 예상 문항도 같은 safe catalog lookup을 사용한다. migration은 실제 저장 필드 `mock_exam_id`의 중복 목록과 sequence/active metadata, null/blank 문서와 기존 인덱스 충돌을 보고하고 충돌이 없을 때만 `uniq_mock_exams_mock_exam_id`를 만든다.
- migration backfill: Summary `completedAt`, Summary `createdAt`, Summary ObjectId 시각, Summary Job `completedAt` 순으로 과거 완료 시각을 산출한다. 중복 Summary나 시각 미해결은 apply를 중단하고 현재 시각을 임의 저장하지 않는다. Summary orphan, Summary examId 누락, legacy 완료 Session 수와 시각 산출 방법을 dry-run에 표시한다. `TMI31_APPLY=true`일 때만 조건부 backfill, legacy 필드 보정과 세 인덱스 생성을 수행한다.
- 변경 파일: `ExamSession`, `ExamSessionRepository`, `ExamSummaryRepository`, `MockExamRepository`, `ExamSessionCompletionQuery`, `ExamSessionManager`, `MockExamCatalogService`, `ExamServiceImpl`, `ExamGradingService`, `ErrorStatus`, `ExamAssignmentIndexValidator`, Mongo migration 스크립트·README·Node 테스트, manager/catalog/aggregation/index 및 기존 service/security 회귀 테스트, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`다. 기존 TMI-31의 나머지 미커밋 변경은 보존했다.
- 유지한 외부 계약: `POST /api/v1/exams` URL·Method·Request Body 없음, `CreateSessionResult`·`BaseResponse`, 시험 소유권, `retryCount`, Redis Key·TTL, S3 Object Key, Callback JSON, grading retry·멱등성, `progressPercent`, 실제 UUID 비노출을 변경하지 않았다. AI Question/Summary와 Callback의 `user_id`는 계속 `examId`다.
- 테스트와 검증: migration 두 파일 `node --check`와 Node 단위 테스트 11개가 성공했다. finding 집중 Java 테스트가 성공했고 최종 `./gradlew clean test`가 `BUILD SUCCESSFUL`로 끝났다. XML 집계는 suite 20개, Java 191개, failures/errors/skipped 0개다. 테스트는 실제 Atlas·Redis·S3·Python AI·Sentry를 호출하지 않았다.
- 추가 검증: `git diff --check`, 인덱스 이름과 `mock_exam_id`/`mockExamId` 저장 필드 검색, migration 기본 dry-run·apply guard, API/DTO·AI user_id·Redis/S3 계약과 Secret 패턴을 재확인한다. 기존 `ExamServiceImpl` unchecked 컴파일 경고는 작업 범위 밖이라 유지한다.
- 남아 있는 위험 요소: 이 환경에는 `mongosh`와 실제 MongoDB가 없어 실제 staging dry-run, index build/explain, 대규모 backfill을 실행하지 않았다. 정확한 legacy Summary 완료 시각을 얻지 못하면 runtime 지연 보정은 호환 Clock을 사용하며 경고하지만 migration apply는 안전하게 중단한다. local은 필수 인덱스 누락을 경고만 하므로 다중 인스턴스 보장은 staging/prod와 달리 제공하지 않는다. Summary 저장과 Session 완료 갱신 사이 crash window는 Callback 재전달로 복구해야 한다.
- Jira 완료 댓글 초안: `TMI-31 리뷰 finding 수정 완료: summarized legacy Session을 Summary 증거로 완료 판정하고 조건부 backfill하며, staging/prod에서 active partial unique 및 mock_exam_id unique 인덱스를 시작 시 검증하도록 보강했습니다. 완료 횟수는 Mongo aggregation과 supporting index로 집계하고 migration은 필수 MONGODB_DATABASE를 명시 선택해 legacy Summary backfill·중복/시각 충돌·세 인덱스를 기본 dry-run으로 검증합니다. 기존 POST /api/v1/exams, DTO, Redis/S3, retryCount와 AI user_id=examId 계약을 유지했고 Java 191개 및 migration 11개 테스트가 성공했습니다. 운영 적용 전 backup, staging dry-run/apply와 index/explain·동시 생성 smoke test가 필요합니다.` 이 초안은 Jira에 등록하지 않았다.
- 다음 작업 전에 확인할 사항: 운영 DB backup 후 명시적 `MONGODB_DATABASE`로 dry-run 결과를 검토하고 중복 Summary/MockExam ID·다중 legacy 활성 후보·인덱스 충돌·시각 미해결을 수동 정리한다. clean dry-run 뒤 `TMI31_APPLY=true`를 실행하고 staging/prod 기동 검증, index explain, 동시 생성·순환·Summary Callback smoke test를 수행한다. commit과 push, Jira 댓글·상태 변경은 사용자가 직접 수행한다.

## 2026-07-30 — TMI-31 main 기준 재코드 리뷰

<!-- codex-turn:review-20260730-tmi31-b71b54b -->

- 날짜: `2026-07-30`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD 및 main merge base `b71b54bb4ff871a8e082cd6d94a34007c84b062c`, 전체 구현은 미커밋 작업 트리)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 기록상 상태 `해야 할 일`; 이번 리뷰에서 Jira 댓글·필드·상태를 변경하거나 Atlassian 쓰기 API를 호출하지 않았다.
- 작업 목표: 사용자 지정 merge base 기준 `git diff`와 신규 미추적 구현·MongoDB migration을 독립적으로 재검토하고, 수정 가치가 확실한 정확성·호환성·운영 finding만 우선순위화한다.
- P1 finding: summary 컬렉션 분리 전에는 전체 종합 결과가 `exam_results`의 `totalScore != null` 문서로 저장됐고 현재 `ExamServiceImpl.getExamSummary`, Callback 멱등 확인과 `ExamGradingService.hasSummaryResult`도 이를 legacy fallback으로 계속 지원한다. 그러나 `ExamSessionManager.findSummaryEvidence`와 migration 입력은 `exam_summaries`만 확인하므로 해당 완료 Session을 미완료로 재사용하고 다음 순차 배정을 막는다.
- P2 finding: runtime catalog는 inactive 또는 양수 문항이 없는 MockExam을 sequence 검증 전에 제외하지만 migration `inspectCatalog`는 `deriveSequence`를 먼저 호출한다. 따라서 sequence가 없고 ID 끝 숫자도 없는 retired/empty 문서가 배정과 무관한데도 dry-run을 실패시켜 필수 인덱스 apply와 staging/prod startup을 차단한다.
- 변경 파일: 리뷰 대상 애플리케이션·테스트·migration 코드는 수정하지 않았다. 작업 기록 규칙에 따라 `docs/codex/CURRENT_STATE.md`를 최신화하고 `docs/codex/WORKLOG.md` 끝에 이 항목만 append했다. Git commit·push는 수행하지 않았다.
- 변경한 동작 및 유지한 계약: 리뷰 기록 외 동작 변경은 없다. 공개 API URL·Method·Parameter·Request/Response DTO·`BaseResponse`, 시험 소유권, `retryCount`, Redis/S3 Key, AI/Callback JSON과 `user_id=examId`, 실제 사용자 UUID 비노출 계약을 그대로 유지했다.
- 실행한 테스트: `node --check scripts/mongodb/tmi-31-migrate-exam-assignment.js`와 `node --test scripts/mongodb/tmi-31-migrate-exam-assignment.test.js`를 실행했다. Java는 사용자 지정 `./gradlew clean test`를 시도했고, 쓰기 가능한 `/tmp` Gradle home으로 offline 재시도했다.
- 테스트 결과: migration Node 테스트 11개는 모두 성공했다. Gradle 기본 실행은 sandbox가 사용자 Gradle wrapper lock 쓰기를 금지해 시작되지 않았고, `/tmp` 재시도도 sandbox가 Gradle file-lock contention socket을 금지해 시작되지 않았다. 현재 모든 소스보다 나중 시각의 기존 XML 결과는 Java 191개, failures/errors/skipped 0개를 기록한다.
- 남아 있는 위험 요소: 위 두 finding 외에도 실제 Atlas migration/index build·explain, Redis·S3·Python AI 연동과 Summary 저장/Session 완료 사이 crash window는 이번 환경에서 검증하지 않았다.
- 다음 작업 전에 확인할 사항: runtime과 migration 모두 분리 전 `exam_results` 종합 문서를 완료 증거로 포함하고, migration sequence 검증을 실제 assignable 문서에만 적용하는 회귀 테스트를 추가한다. 이후 socket/lock 사용이 허용된 환경에서 `./gradlew clean test`와 staging backup 기반 migration dry-run을 재실행한다. commit과 push는 사용자가 수행한다.

## 2026-07-30 — TMI-31 이전 HIGH/MEDIUM 수정 재검증

<!-- codex-turn:019fb0d8-6d54-7cf0-b91d-d42100e9fbf9 -->

- 날짜: `2026-07-30`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD 및 main merge base `b71b54bb4ff871a8e082cd6d94a34007c84b062c`, 전체 구현은 미커밋 작업 트리)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 상태 `해야 할 일`; Atlassian MCP로 설명·선택 알고리즘·완료 조건을 읽기 전용 재확인했고 댓글·필드·상태를 변경하지 않았다.
- 작업 목표: main 기준으로 이전 HIGH 1건과 MEDIUM 4건의 해소 여부를 재검토하고, legacy 완료 판정·운영 인덱스·migration DB·완료 집계·catalog 고유성·외부 계약의 남은 HIGH/MEDIUM/LOW finding만 보고한다.
- HIGH finding: runtime `ExamSessionManager`와 migration은 `exam_summaries`만 완료 증거로 읽고, 현재 조회·멱등 로직이 계속 지원하는 분리 이전 `exam_results.totalScore != null` 종합 문서를 확인하지 않는다. 해당 legacy Session은 진행 중으로 재사용되거나 migration에서 `active=true`로 보정되어 다음 시험 배정이 영구 차단될 수 있다.
- MEDIUM finding: migration `inspectCatalog`는 assignable 판정보다 `deriveSequence`를 먼저 실행한다. runtime이 제외하는 inactive 또는 양수 문항이 없는 시험도 ID에서 sequence를 해석할 수 없으면 dry-run/apply 전체를 실패시켜 필수 인덱스 설치와 staging/prod 기동을 차단한다.
- 해소 확인: staging/prod의 active partial unique index 시작 검증은 이름·키·unique·partial 정의 불일치까지 fail-closed한다. migration은 `MONGODB_DATABASE`를 필수로 받아 `getSiblingDB`로 명시 선택하고 시스템 DB를 거부한다. 완료 횟수는 사용자·완료 조건의 Mongo aggregation과 supporting index를 사용하며 전체 Entity를 로드하지 않는다. runtime과 migration은 중복/null/blank `mockExamId`를 거부하고 실제 저장 필드 `mock_exam_id`에 unique index를 요구한다.
- 유지한 계약: Controller·Request/Response DTO·`BaseResponse`에는 main 대비 diff가 없고 `POST /api/v1/exams` Request Body 없음, 시험 소유권, `retryCount`, Redis Key/TTL, 제출 S3 Key, AI/Callback `user_id=examId`와 실제 사용자 UUID 비노출 계약을 유지한다.
- 변경 파일: 사용자 요청에 따라 애플리케이션·테스트·migration 파일은 수정하지 않았다. Stop Hook 기록 요구에 따라 `docs/codex/WORKLOG.md`에 이 항목을 append하고 `docs/codex/CURRENT_STATE.md`만 최신화했다. Git commit·push와 Jira 쓰기 작업은 수행하지 않았다.
- 실행한 검증: `git diff --check main --`, `node --check scripts/mongodb/tmi-31-migrate-exam-assignment.js`, `node --test scripts/mongodb/tmi-31-migrate-exam-assignment.test.js`를 실행했다.
- 테스트 결과: 세 검증 모두 성공했고 migration Node 테스트는 11개 모두 통과했다. 코드 변경 없는 리뷰여서 Gradle 테스트는 다시 실행하지 않았다.
- 남아 있는 위험 요소: 두 finding이 해결되기 전에는 분리 이전 종합 결과를 가진 기존 사용자의 순환 배정과 retired/empty catalog가 존재하는 운영 migration을 완료로 판단할 수 없다. 실제 Atlas migration/index build·explain과 Redis·S3·Python AI 연동은 실행하지 않았다.
- 다음 작업 전에 확인할 사항: runtime과 migration에 `exam_results.totalScore != null` 완료 증거와 안전한 시각 산출을 추가하고, migration sequence 검증을 assignable 시험에만 적용한 뒤 회귀 테스트와 `./gradlew clean test`, staging backup 기반 dry-run을 실행한다. commit과 push는 사용자가 수행한다.

## 2026-07-30 — TMI-31 legacy totalScore 및 배정 제외 catalog finding 수정

<!-- codex-turn:019fb0e5-293e-7a02-b4c2-a7b930abcb14 -->

- 날짜: `2026-07-30`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD 및 main merge base `b71b54bb4ff871a8e082cd6d94a34007c84b062c`, 전체 구현은 미커밋 작업 트리)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 상태 `해야 할 일`; AGENTS.md·CURRENT_STATE·Jira 설명과 완료 조건을 재확인했고 Jira 댓글·필드·상태를 변경하지 않았다.
- 작업 목표: 이전 최종 리뷰의 HIGH `exam_results.totalScore` 완료 증거 누락과 MEDIUM 배정 제외 MockExam sequence 강제만 최소 범위로 수정하면서 기존 순차·순환 선택, 활성 세션 재사용, 동시 생성 방지, Summary 완료 처리와 `mockExamId` 전파 구조를 유지한다.
- HIGH 수정 파일: `ExamCompletionEvidenceService.java`, `ExamSessionManager.java`, 두 application 테스트, Mongo migration 스크립트·Node 테스트·README다. Mongo projection으로 `exam_summaries`와 같은 `examId`의 `exam_results.totalScore != null`을 완료 증거로 합치고 `totalScore=null` 문항 결과는 제외한다.
- Runtime 완료 판정과 backfill: `active=false` 또는 `completedAt != null`은 재사용하지 않고, `active=true` 신규 세션은 그대로 재사용한다. null/missing active와 null completedAt인 legacy 후보만 완료 증거를 조회해 증거가 있으면 제외한다. 모든 증거의 가장 이른 명시 시각, 실제 BSON ObjectId 생성 시각, `ExamSession.createdAt` 근사값 순으로 완료 시각을 정하고 기존 값이 여전히 없는 경우에만 `active=false`, `completedAt`을 조건부 원자 갱신한다. 시각이 없어도 완료 세션으로 판정하고 활성으로 되돌리지 않는다.
- 완료 횟수 호환: 완료 증거가 있는 legacy Session을 동일 문서에 지연 backfill한 뒤 기존 사용자별 `completedAt != null` Mongo aggregation을 재사용하므로 Summary와 legacy totalScore가 함께 있어도 한 Session을 한 번만 센다. 동시 backfill은 별도 Session이나 완료 기록을 생성하지 않는다.
- Migration 완료 증거: `exam_summaries`와 `exam_results.totalScore != null`을 Session별로 합쳐 가장 이른 신뢰 가능한 시각을 사용한다. dry-run은 각 증거 출처·overlap·중복·orphan·시각 산정 방식·unresolved와 예정 backfill을 출력하며, `TMI31_APPLY=true`에서만 완료 Session을 `active=false`와 `completedAt`으로 보정한다. 완료 증거가 있는 Session을 `active=true`로 만들지 않는다.
- MEDIUM 수정 파일: `scripts/mongodb/tmi-31-migrate-exam-assignment.js`, 해당 Node 테스트와 README다. catalog 검사는 `INACTIVE`, `EMPTY_QUESTIONS`, `MISSING_ID`, `INVALID_ACTIVE` 제외 사유를 sequence 해석 전에 판정하고 assignable 문서에만 필수 `deriveSequence`, sequence 중복 검증과 sequence/active 보정을 적용한다. 제외 문서는 별도로 보고하되 해석 불가능한 ID로 migration/index 계획을 막거나 임의 활성화하지 않는다.
- 중복 검증 범위: sequence uniqueness는 assignable 시험끼리만 적용하고 `mockExamId` uniqueness는 단건 조회 모호성을 막기 위해 inactive/empty를 포함한 전체 catalog에 계속 적용한다.
- 유지한 외부 계약: `POST /api/v1/exams` URL·Method·Request Body 없음, `CreateSessionResult`·`BaseResponse`, 순차·순환 알고리즘, 사용자별 완료 이력 격리, active partial unique 정책, 소유권, `retryCount`, Redis Key/TTL, 제출 S3 Key, Callback JSON, grading retry·멱등성, `progressPercent`, AI/Callback `user_id=examId`와 실제 사용자 UUID 비노출을 변경하지 않았다.
- 테스트와 검증: `git diff --check main --`와 migration `node --check`가 성공했다. Node migration 테스트 25개와 집중 Java 테스트가 성공했고 최종 `./gradlew clean test`도 `BUILD SUCCESSFUL`로 끝났다. XML 기준 Java 200개, failures/errors/skipped 0개이며 실제 Atlas·Redis·S3·Python AI·Sentry는 호출하지 않았다. 실제 Secret·URI·AWS Key 패턴도 발견되지 않았다.
- 변경 파일: 위 application·migration·테스트·README와 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`다. 기존 TMI-31의 다른 미커밋 변경은 다시 작성하거나 되돌리지 않았다. Git commit·push는 수행하지 않았다.
- 남아 있는 위험 요소: 실제 Atlas에서 migration dry-run/apply, 세 인덱스 build와 aggregation explain, 다중 인스턴스 동시 생성, Redis·S3·Python AI end-to-end는 실행하지 않았다. `ExamSession.createdAt`을 완료 시각으로 쓰는 경우는 정확한 Callback 시각이 없는 legacy 데이터의 근사값이며 dry-run/README에 표시된다.
- Jira 완료 댓글 초안: `TMI-31 최종 review finding 수정 완료: legacy exam_summaries뿐 아니라 exam_results.totalScore 결과도 완료 증거로 판정해 진행 중 세션 재사용에서 제외하고 조건부 backfill하며, migration도 두 증거를 합쳐 가장 이른 신뢰 가능한 완료 시각을 사용합니다. inactive·빈 등 배정 제외 MockExam은 sequence 해석 전에 분류해 정상 catalog와 필수 인덱스 적용을 막지 않으며 sequence 중복은 assignable끼리, mockExamId 중복은 전체 catalog에서 검증합니다. 기존 API·DTO·Redis·S3·retryCount와 AI user_id=examId 계약을 유지했고 Java 200개와 migration 25개 테스트가 성공했습니다. 운영 적용 전 backup 기반 dry-run/apply와 staging index·동시성·외부 연동 smoke test가 필요합니다.` 이 초안은 Jira에 등록하지 않았다.
- 다음 작업 전에 확인할 사항: 운영 DB backup 후 명시적 `MONGODB_DATABASE`로 dry-run 결과의 legacy evidence 충돌·orphan·완료 시각 근사와 제외 catalog를 검토한다. clean 결과에서만 `TMI31_APPLY=true`로 적용하고 staging/prod 시작 검증, index explain, 동시 생성·순환·Summary Callback·S3/AI 전파를 smoke test한다. commit과 push는 사용자가 수행한다.

## 2026-07-30 — TMI-31 최종 명령 재검증

<!-- codex-turn:019fb13a-fda9-7a70-be37-3d0ead4cb946 -->

- 날짜: `2026-07-30`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD `b71b54b`, 전체 TMI-31 구현은 미커밋 작업 트리)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 상태 `해야 할 일`; Jira 댓글·필드·상태를 변경하지 않았다.
- 작업 목표: 사용자가 지정한 `git diff --check`, `node --check scripts/mongodb/tmi-31-migrate-exam-assignment.js`, `./gradlew clean test`를 현재 작업 트리에서 다시 실행해 최종 상태를 확인한다.
- 실행 결과: `git diff --check`와 migration `node --check`는 출력 없이 종료 코드 0으로 성공했다. `./gradlew clean test`는 clean부터 전체 task를 실행해 `BUILD SUCCESSFUL`로 완료됐고 XML 기준 Java 200개 테스트, failures/errors/skipped 0개다.
- 변경 파일: 검증 과정에서는 애플리케이션·테스트·migration 코드를 수정하지 않았다. 작업 기록 규칙에 따라 `docs/codex/CURRENT_STATE.md`를 최신화하고 `docs/codex/WORKLOG.md` 끝에 이 항목만 append했다.
- 유지한 외부 계약: `POST /api/v1/exams`, Request Body 없음, 기존 DTO·`BaseResponse`, 소유권, `retryCount`, Redis Key/TTL, S3 Key, Callback JSON, AI `user_id=examId`와 실제 사용자 UUID 비노출 계약에 변경이 없다.
- 경고와 위험: 기존 `ExamServiceImpl` unchecked compile 경고만 다시 확인됐다. 실제 Atlas migration/index, Redis·S3·Python AI 연동은 이번 명령 재검증 범위에 포함하지 않았다.
- Git/Jira: commit과 push를 수행하지 않았고 Jira 쓰기 작업도 수행하지 않았다.

## 2026-07-30 — TMI-31 main 기준 독립 최종 코드 리뷰

<!-- codex-turn:review-20260730-tmi31-b71b54b-final -->

- 날짜: `2026-07-30`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD 및 main merge base `b71b54bb4ff871a8e082cd6d94a34007c84b062c`, 구현은 미커밋 작업 트리)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 기록상 상태 `해야 할 일`; 이번 리뷰에서 Jira 댓글·필드·상태를 변경하지 않았다.
- 작업 목표: 사용자 지정 merge base 기준 tracked diff와 신규 미추적 application·migration·테스트 파일을 독립적으로 검토하고 수정 가치가 확실한 정확성·운영·저장소 규칙 finding을 우선순위화한다.
- P1 finding: migration APPLY가 기존 main 인스턴스의 Callback 쓰기와 겹치면 snapshot 뒤 저장된 Summary를 보지 못한 `activateIncompleteLegacy` 계획이 해당 Session을 `active=true`로 만든다. 기존 Callback은 Session 완료 필드를 쓰지 않고 신규 Manager는 `active=true`에서 완료 증거를 확인하지 않으므로 완료 시험이 영구 재사용될 수 있다. 구버전 writer quiescence 또는 activation/index 전 현재 증거 재검증이 필요하다.
- P2 finding: migration sequence 검사는 Java `Integer` 상한이 아니라 JavaScript safe integer까지만 허용한다. `2147483648` 이상의 명시 sequence나 ID suffix를 APPLY하면 `MockExam.sequence` 역직렬화가 overflow로 실패하므로 두 경로 모두 signed 32-bit 범위에서 거부해야 한다.
- P3 finding: `docs/codex/WORKLOG.md`의 기존 `019fac7a-...` 항목 branch 기록을 append가 아니라 수정해 append-only 작업 기록 규칙과 기존 이력을 훼손한다. 원문 복원 후 정정이 필요하면 새 항목을 append해야 한다.
- 변경 파일: 리뷰 대상 application·migration·테스트 코드는 수정하지 않았다. 작업 기록 규칙에 따라 이 항목을 `docs/codex/WORKLOG.md` 끝에 append하고 `docs/codex/CURRENT_STATE.md`를 최신 리뷰 상태로 갱신했다.
- 변경한 동작: 리뷰 기록 외 애플리케이션 동작 변경은 없다.
- 유지한 외부 API 계약: Controller·Request/Response DTO·`BaseResponse`, `POST /api/v1/exams` Request Body 없음, 소유권, `retryCount`, Redis/S3 Key, AI·Callback JSON과 `user_id=examId`, 실제 사용자 UUID 비노출 계약에 리뷰 중 변경을 가하지 않았다.
- 실행한 테스트: `git diff --check b71b54bb4ff871a8e082cd6d94a34007c84b062c --`, 신규 파일 whitespace 검사, migration 두 파일 `node --check`, `node --test scripts/mongodb/tmi-31-migrate-exam-assignment.test.js`, `./gradlew clean test`, 쓰기 가능한 `/tmp` Gradle home의 offline `./gradlew clean test --no-daemon --offline`을 실행 또는 시도했다. sequence 상한 누락은 Node 함수 호출과 Spring `NumberUtils` overflow 확인으로 재현했다.
- 테스트 결과: tracked/untracked whitespace 검사와 Node syntax·25개 migration 테스트는 성공했다. fresh Gradle 두 시도는 각각 사용자 홈 wrapper lock 쓰기 제한과 sandbox의 Gradle file-lock contention socket 금지로 task 시작 전에 실패했다. 현재 소스보다 나중에 생성된 기존 class/XML은 Java 200개, failures/errors/skipped 0개를 기록한다.
- 남아 있는 위험 요소: 위 세 finding 외 실제 Atlas migration dry-run/apply·index build, live rolling cutover, Redis·S3·Python AI E2E는 실행하지 않았다. 실제 운영 데이터 규모에서 migration의 전체 `toArray()` 메모리 사용도 이번 환경에서 측정하지 않았다.
- 다음 작업 전에 확인할 사항: 구버전 writer를 정지하거나 DB 수준 cutover guard를 둔 상태에서 evidence를 재검증하도록 migration을 보강하고, Java int 상한 및 live Callback race 회귀 테스트를 추가한다. 기존 WORKLOG 원문을 복원한 뒤 socket 사용이 허용된 환경에서 `./gradlew clean test`와 backup 기반 staging migration을 재실행한다. commit과 push는 사용자가 수행한다.

## 2026-07-30 — TMI-31 사용자 지정 11개 항목 최종 리뷰

<!-- codex-turn:019fb14e-023f-7af3-8a9c-9c572ae8d8b8 -->

- 날짜: `2026-07-30`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD 및 main merge base `b71b54bb4ff871a8e082cd6d94a34007c84b062c`, 구현은 미커밋 작업 트리)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 상태 `해야 할 일`; Atlassian MCP로 제품 규칙·선택 알고리즘·완료 조건을 읽기 전용 재확인했고 댓글·필드·상태를 변경하지 않았다.
- 작업 목표: AGENTS.md를 기준으로 main 대비 tracked diff와 신규 미추적 파일을 검토하고 legacy 완료 증거, 중복 집계, 조건부 backfill, 순환 선택, 활성 재사용·동시성, 운영 인덱스, migration DB/catalog, `mockExamId` 전파와 외부 계약의 HIGH·MEDIUM·LOW finding만 확정한다.
- HIGH finding: migration이 Session·Summary·legacy totalScore를 snapshot한 뒤 `activateIncompleteLegacy`를 적용하기 전 완료 증거를 재조회하지 않는다. 구버전 main Callback이 그 사이 Summary를 저장하면 Session 필드 조건은 그대로 통과해 `active=true`가 되고, 신규 Manager가 명시적 active를 신뢰해 완료 시험을 재사용할 수 있다.
- MEDIUM finding: migration의 명시 sequence와 ID suffix 검증은 JavaScript safe integer까지만 허용한다. Java Entity가 `Integer`이므로 `2147483648` 이상을 적용하면 Spring Mongo 변환 overflow로 catalog 로드와 시험 생성이 실패할 수 있다.
- LOW finding: main 대비 diff가 기존 `019fac7a-...` WORKLOG 항목의 branch/HEAD 한 줄을 과거 값에서 현재 main 값으로 수정했다. AGENTS.md의 append-only 규칙에 따라 원문 복원과 별도 정정 append가 필요하다.
- 해소 확인: 정상 runtime은 `exam_summaries`와 `exam_results.totalScore != null`을 Session당 하나의 완료 증거로 backfill하고 Mongo aggregation은 Session 문서 하나만 센다. backfill 필터는 null/missing active·completedAt만 갱신한다. 선택은 completionCount 후 sequence, 기존 활성 세션 우선, duplicate insert 복구 구조이며 staging/prod는 필수 partial unique index와 catalog ID unique index 누락·불일치 시 fail-closed한다.
- 추가 확인: migration은 필수 `MONGODB_DATABASE`를 `getSiblingDB`로 선택하고 inactive/empty 문서를 sequence 강제 전에 제외하며, sequence 중복은 assignable끼리·`mockExamId` 중복은 전체 catalog에서 거부한다. 선택된 ID는 문제/가이드 S3, Question/Summary Job, 문항·Summary AI, grading retry와 상세 조회에 전파되고 legacy Session/Job만 `mock_exam_003`으로 fallback한다.
- 유지한 계약: Controller·Request/Response DTO·`BaseResponse`에는 main 대비 diff가 없다. `POST /api/v1/exams` Request Body 없음, 소유권, `retryCount`, Redis Key/TTL, 제출 S3 Key, Callback JSON, `progressPercent`, AI/Callback `user_id=examId`와 실제 사용자 UUID 비노출 계약을 유지한다.
- 실행한 검증: `git diff --check main --`와 migration `node --check`가 성공했다. 코드 변경 없는 리뷰라 Gradle은 다시 실행하지 않았고, 직전 동일 application 소스의 `./gradlew clean test`는 Java 200개, failures/errors/skipped 0개로 성공했다.
- 변경 파일: 사용자 요청에 따라 application·migration·테스트 파일은 수정하지 않았다. 상위 작업 기록 지침에 따라 `docs/codex/WORKLOG.md` 끝에 이 항목을 append하고 `docs/codex/CURRENT_STATE.md`만 최신화했다. 기존 WORKLOG 항목은 이번 turn에서 수정하거나 삭제하지 않았다.
- 다음 작업 전에 확인할 사항: migration apply 전에 구버전 writer quiescence 또는 evidence 재검증을 보장하고 Java `Integer.MAX_VALUE` 상한 테스트를 추가한다. 기존 WORKLOG 원문은 별도 수정 turn에서 복원하되 정정 설명은 새 항목으로 append한다. Git commit·push와 Jira 쓰기는 수행하지 않았다.

## 2026-07-30 — TMI-31 migration 경쟁 조건·sequence 경계·WORKLOG 이력 수정

<!-- codex-turn:019fb15f-f67c-7d93-ae6d-3357e9ab672c -->

- 날짜: `2026-07-30`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD 및 main merge base `b71b54bb4ff871a8e082cd6d94a34007c84b062c`, 전체 구현은 미커밋 작업 트리)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 상태 `해야 할 일`; AGENTS.md, CURRENT_STATE와 Jira 설명을 다시 읽었으며 Jira 댓글·필드·상태를 변경하지 않았다.
- 작업 목표: 최종 리뷰의 HIGH migration stale activation, MEDIUM Java `Integer` sequence overflow, LOW WORKLOG 원문 훼손을 최소 범위로 수정하면서 기존 순차·순환 선택, 완료 증거 판정, partial unique index, Summary 완료 처리와 `mockExamId` 전파 구조를 유지한다.
- HIGH 수정: migration apply는 `TMI31_LEGACY_WRITER_STOPPED=true`를 필수로 요구해 구버전 Learning Core와 Callback writer가 중지되지 않은 적용을 fail-closed한다. 각 legacy Session 활성화 직전에 Session과 `exam_summaries`, `exam_results.totalScore != null`을 실제 DB에서 다시 조회하고, 새 완료 증거가 있으면 `completion-evidence-detected-during-final-recheck`로 집계해 조건부 `active=false`/`completedAt` backfill을 수행한다. 최신 Session에 `active` 또는 `completedAt`이 이미 있거나 문서가 삭제됐으면 덮어쓰지 않는다.
- apply 후 검증: 현재 Session·두 완료 증거 컬렉션·인덱스를 다시 읽어 `active=true`와 완료 증거/`completedAt`의 동시 존재, 사용자당 복수 active Session, 완료 증거와 null/true active, 필수 인덱스 누락·불일치를 검사하고 하나라도 남으면 성공 종료하지 않는다. 서로 다른 컬렉션을 단일 원자 조건으로 묶을 수 없어 writer-stop maintenance window가 정확성의 필수 전제임을 코드 주석과 README에 기록했다.
- Runtime 제한 방어: `active=true`이면서 `cycleNumber`가 없는 legacy 의심 Session만 완료 증거를 추가 확인한다. 증거가 있으면 조건부 비활성 완료 backfill 후 재사용하지 않고, `cycleNumber`가 있는 신규 정상 active Session은 기존 빠른 경로로 재사용해 매 요청의 불필요한 증거 조회를 피한다.
- MEDIUM 수정: migration의 명시 sequence와 `mock_exam_id` suffix 파생값을 공통 `JAVA_INTEGER_MAX=2147483647` 기준 `1..2147483647`로 제한했다. assignable 문서의 문자열·소수는 `NON_INTEGER_SEQUENCE`, 0·음수는 `NON_POSITIVE_SEQUENCE`, Java 상한 초과는 `JAVA_INTEGER_OVERFLOW`, 파생 불가는 `UNPARSABLE_SEQUENCE_SUFFIX`로 구분해 apply를 차단한다. inactive/empty 등 배정 제외 문서는 진단만 남기고 전체 migration을 막지 않는다. Runtime catalog는 suffix를 `Integer.parseInt`로 검증하고 Mongo mapping 변환 오류를 내부 BSON 정보 없는 catalog 설정 오류로 변환한다.
- LOW 수정: 과거 marker `019fac7a-23dd-7f40-8dbe-dcf8c6df9e9f` 항목에서 잘못 변경됐던 branch/HEAD 한 줄을 main 원문인 `feat/TMI-25-grading-retry-idempotency` / `fb354b6` 및 기존 미커밋 변경 문구로 정확히 복원했다. 과거 항목의 다른 문구와 marker는 변경하지 않았고, 잘못된 수정과 현재 TMI-31 branch/HEAD 정보는 이 신규 항목에만 기록했다.
- 변경 파일: `scripts/mongodb/tmi-31-migrate-exam-assignment.js`, 해당 Node 테스트와 README, `ExamSessionManager`, `ExamSessionRepository`, `MockExamCatalogService`, 관련 Java 테스트, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`다. 기존 TMI-31의 다른 미커밋 변경은 다시 작성하거나 되돌리지 않았다.
- 유지한 외부 계약: Controller·Request/Response DTO·`BaseResponse`에는 main 대비 diff가 없다. `POST /api/v1/exams` URL·Method·Request Body 없음, `CreateSessionResult`, 소유권, `retryCount`, Redis Key/TTL, 제출 S3 Key, Callback JSON, `progressPercent`, AI/Callback `user_id=examId`와 실제 사용자 UUID 비노출을 유지했다.
- 테스트와 검증: `git diff --check`, migration과 테스트 파일 `node --check`, migration Node 테스트 49개가 성공했다. 집중 Java 테스트를 포함한 `./gradlew clean test`는 `BUILD SUCCESSFUL`이며 XML 기준 Java 205개, failures/errors/skipped 0개다. 신규·미추적 파일 whitespace, `mock_exam_003` legacy 상수 제한, AI `user_id`, Redis/S3 계약과 직접 시간 호출을 검색했고 실제 AWS Access Key, 자격증명 포함 Mongo URI, private key 패턴은 0건이었다. 실제 Atlas·Redis·S3·Python AI는 호출하지 않았다.
- 남아 있는 위험 요소: 실제 Atlas backup 기반 dry-run/apply, 세 인덱스 build와 aggregation explain, 다중 인스턴스 동시 생성, Redis·S3·Python AI end-to-end는 아직 실행하지 않았다. apply의 writer-stop 환경변수는 운영자의 maintenance window 수행을 기술적으로 대체하지 않으며, migration은 현재 대상 컬렉션을 `toArray()`로 읽으므로 운영 데이터 규모의 메모리 사용을 staging에서 확인해야 한다.
- Jira 완료 댓글 초안: `TMI-31 최종 review finding 수정 완료: apply에 legacy writer 중지 precondition을 추가하고 Session 활성화 직전 두 완료 증거 컬렉션과 최신 Session을 재조회하며, 적용 후 active/completion/index 교차검증이 남으면 실패하도록 보강했습니다. sequence는 명시값과 ID suffix 모두 Java Integer 범위로 제한했고 runtime도 mapping overflow를 안전한 catalog 오류로 처리합니다. 과거 WORKLOG 원문도 복원했습니다. 기존 API·DTO·Redis·S3·retryCount와 AI user_id=examId 계약을 유지했고 migration 49개와 Java 205개 테스트가 성공했습니다. 운영 적용 전 backup, maintenance window, dry-run/apply와 staging 외부 연동 smoke test가 필요합니다.` 이 초안은 Jira에 등록하지 않았다.
- 다음 작업 전에 확인할 사항: README 순서대로 트래픽 차단, 구버전 writer 종료, DB backup, dry-run 충돌 검토 후 두 apply 환경변수로 적용하고 최종 교차검증 성공을 확인한 뒤 신규 버전을 기동한다. 사용자가 diff를 검토해 commit·push하며 Jira 댓글·상태 변경은 별도 요청이 있을 때만 수행한다.

## 2026-07-30 — TMI-31 main 기준 최종 코드 재리뷰

<!-- codex-turn:019fb1d4-e7cc-73d0-9cc0-0360247ba6f2 -->

- 날짜: `2026-07-30`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD 및 main merge base `b71b54bb4ff871a8e082cd6d94a34007c84b062c`, 구현은 미커밋 작업 트리)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 기록상 상태 `해야 할 일`; 이번 리뷰에서 Jira 댓글·필드·상태를 변경하지 않았다.
- 작업 목표: 사용자 지정 merge base의 tracked diff와 신규 미추적 application·migration·테스트 파일을 독립적으로 다시 검토하고 수정 가치가 확실한 정확성·성능·보안·호환성 finding을 우선순위화한다.
- 리뷰 결과: 신규 actionable finding을 확인하지 않았다. 사용자별 완료 횟수와 sequence 선택, 활성 Session 재사용·partial unique index 충돌 복구, legacy Summary/totalScore 완료 증거와 조건부 backfill, Summary 저장 성공 뒤 완료, selected `mockExamId`의 문제·S3·Question/Summary Job·AI·retry·상세 조회 전파와 migration writer-stop/final verification 경로를 추적했다.
- 변경 파일과 동작: 리뷰 대상 애플리케이션·migration·테스트 코드는 수정하지 않았다. 작업 기록 규칙에 따라 `docs/codex/WORKLOG.md` 끝에 이 항목을 append하고 `docs/codex/CURRENT_STATE.md`를 최신 리뷰 상태로 갱신했다. 애플리케이션 동작 변경은 없다.
- 유지한 외부 계약: Controller·Request/Response DTO·`BaseResponse`에는 merge base 대비 diff가 없다. `POST /api/v1/exams` Request Body 없음, 사용자 소유권, 실제 userId 비노출, `retryCount`, Redis Key/TTL, 제출 S3 Key, Callback JSON, AI/Callback `user_id=examId` 계약이 유지된다.
- 실행한 테스트와 결과: `git diff --check`와 신규 파일 whitespace 검사, migration·테스트 파일 `node --check`, `node --test scripts/mongodb/tmi-31-migrate-exam-assignment.test.js`를 실행했고 Node 49개가 모두 성공했다. 정확한 `./gradlew clean test`는 사용자 Gradle home lock 파일 쓰기가 sandbox에서 거부돼 task 시작 전에 실패했고, writable `/tmp`에 cache를 복제한 `--no-daemon --offline` 재시도도 Gradle file-lock contention UDP socket 생성이 거부돼 task 시작 전에 실패했다. 현재 application 소스보다 최신인 2026-07-30 16:02 기존 XML은 Java 205개와 failures/errors/skipped 0개를 기록한다.
- 보안·정적 확인: tracked/untracked 변경에서 실제 AWS Access Key, 자격증명 포함 Mongo URI와 private key 패턴을 확인하지 못했다. 실제 운영 인프라는 호출하지 않았다.
- 남아 있는 위험 요소: 이번 sandbox에서는 fresh Gradle task를 재실행하지 못했다. 실제 Atlas backup 기반 migration dry-run/apply, 세 인덱스 build와 aggregation explain, 다중 인스턴스 동시 생성, Redis·S3·Python AI staging E2E도 수행하지 않았다.
- 다음 작업 전에 확인할 사항: socket 사용이 허용된 환경에서 `./gradlew clean test`를 다시 실행하고, README의 maintenance-window 순서대로 backup·dry-run·apply·최종 교차검증 후 staging 외부 연동 smoke test를 수행한다. 사용자가 diff를 검토해 commit과 push를 수행한다.

## 2026-07-30 — TMI-31 추가 HIGH/MEDIUM/LOW 해소 최종 리뷰

<!-- codex-turn:019fb1ed-c009-7653-af28-b4e5c31ee6c7 -->

- 날짜: `2026-07-30`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD 및 main merge base `b71b54bb4ff871a8e082cd6d94a34007c84b062c`, 구현은 미커밋 작업 트리)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 기록상 상태 `해야 할 일`; Jira 댓글·필드·상태를 변경하지 않았다.
- 작업 목표: main 기준 추가 HIGH migration snapshot 경쟁, MEDIUM Java Integer sequence overflow, LOW WORKLOG append-only 훼손의 해소 여부와 기존 API·AI·Redis·S3·grading 계약을 파일 수정 없이 최종 리뷰한다.
- 리뷰 결과: 새로운 HIGH, MEDIUM, LOW finding을 확인하지 않았다. 초기 snapshot 뒤 생성된 `exam_summaries` 또는 `exam_results.totalScore != null` 증거는 legacy Session 활성화 직전 실제 DB 재조회에서 발견되어 `active=true` 적용 대신 조건부 완료 backfill 대상이 된다. apply 성공 직전에도 현재 Session·두 증거 컬렉션·필수 인덱스를 교차검증하며 active Session에 완료 증거가 있으면 non-zero로 종료한다.
- Writer 중지 전제: apply는 정확한 `TMI31_LEGACY_WRITER_STOPPED=true` 없이는 시작하지 않는다. 이 값은 외부 프로세스 자동 탐지가 아니라 운영자가 실제 maintenance window와 writer 종료를 완료했다는 명시적 승인값이며, 활성화 직전 재조회와 종료 전 교차검증이 함께 보강한다. 승인값을 실제 상태와 다르게 설정하는 운영 위반까지 자동 판별하지 못하는 점은 README의 명시적 전제로 유지된다.
- Sequence 확인: assignable explicit sequence와 ID suffix 모두 `1..2147483647`만 허용한다. 상한 초과는 `JAVA_INTEGER_OVERFLOW`로 migration을 차단하고 Runtime suffix는 `Integer.parseInt`, Mongo mapping overflow는 catalog 설정 오류로 안전하게 실패한다.
- WORKLOG 확인: `git diff --unified=0 main -- docs/codex/WORKLOG.md`에 기존 행 삭제·수정이 없고 과거 branch/HEAD 원문이 복원된 상태다. 이번 리뷰 기록은 파일 끝에 새 항목으로만 append했다.
- 외부 계약 확인: Controller·Request/Response DTO·`BaseResponse`는 main 대비 diff가 없다. `retryCount`, Redis `exam:status:{examId}`와 1시간 TTL, 제출 S3 Key, Callback JSON, AI Question/Summary `user_id=examId`와 grading 멱등성 계약이 유지된다.
- 검증: `git diff --check`, migration `node --check`, Node migration 테스트 49개가 성공했다. 현재 source의 기존 Gradle XML 보고서는 Java 205개와 failures/errors/skipped 0개다. 이번 리뷰에서는 애플리케이션·migration·테스트 코드를 수정하지 않았고 fresh Gradle을 재실행하지 않았다.
- 변경 파일: 종료 기록 규칙에 따라 `docs/codex/WORKLOG.md` 끝에 이 항목을 append하고 `docs/codex/CURRENT_STATE.md`만 최신화했다. Git commit·push와 Jira 쓰기는 수행하지 않았다.
- 남아 있는 운영 전제: 실제 Atlas apply의 성공 보장은 README 순서대로 writer를 실제 중지한 maintenance window에서 실행하는 경우에 한한다. 실제 Atlas migration/index와 Redis·S3·Python AI staging E2E는 이 리뷰에서 실행하지 않았다.

## 2026-07-31 — TMI-31 문항별 피드백 응답 구조 분석

<!-- codex-turn:019fb5d2-8f65-7123-91fc-cbe49a7d281e -->

- 날짜: `2026-07-31`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD `b71b54b`, 전체 TMI-31 구현은 미커밋 작업 트리)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 기록상 상태 `해야 할 일`; Jira 댓글·필드·상태를 변경하지 않았다.
- 작업 목표: 현재 문항별 AI 피드백이 저장되고 프론트에 반환되는 API 흐름과 실제 Response DTO를 코드 기준으로 설명한다.
- 분석 결과: AI는 `POST /api/v1/exams/callback/feedback`으로 `user_id=examId`, 문항·회차·점수·transcript·feedback·spoken word 데이터를 전송한다. Callback 응답은 저장 성공을 나타내는 `BaseResponse<Void>`이며 피드백 본문은 즉시 반환하지 않는다.
- 프론트 흐름: `GET /api/v1/exams/{examId}/questions/status`로 문항·회차 상태를 폴링하고, 완료 후 `GET /api/v1/exams/{examId}/questions?questionNumber=...&retryCount=...`에서 `BaseResponse<QuestionResult>`를 조회한다. 응답은 examId와 단일 question 객체에 part/question/retry, 전체 재시도 횟수, 5분 제출 음성 URL, 점수·transcript, AI feedback, Azure feedback, spoken word sequence와 선택된 MockExam의 questionInfo를 결합한다.
- 조회 규칙: 사용자 소유권을 먼저 확인하고 Session의 `mockExamId` 문제지를 사용한다. 요청한 retry의 최신 결과를 선택하며 retry 0은 legacy null retry도 호환한다. Azure도 같은 회차를 사용한다. 채점 결과가 아직 없어도 상세 조회는 실패하지 않고 점수·transcript·Azure 등 null 필드는 바깥 question 객체에서 생략될 수 있으며, feedback 객체는 생성되어 문제지의 correctedAnswer가 들어갈 수 있다. 따라서 정상 UI 흐름은 status가 `COMPLETED`인 뒤 상세를 조회하는 방식이다.
- 변경 파일: 분석 기록을 위해 `docs/codex/WORKLOG.md` 끝에 이 항목을 append하고 `docs/codex/CURRENT_STATE.md`를 최신화했다. 애플리케이션·migration·테스트 코드는 수정하지 않았다.
- 실행한 검증: Controller, Service, Converter, Request/Response DTO, BaseResponse, ErrorStatus와 관련 문항 조회 테스트를 읽기 전용으로 확인했다. 코드 변경이 없어 Gradle 테스트는 다시 실행하지 않았다.
- 유지한 계약: 공개 API URL·Method·Parameter·DTO·`BaseResponse`, 실제 userId 비노출, AI/Callback `user_id=examId`, retryCount, Redis·S3 계약을 변경하지 않았다. Secret과 Token은 기록하지 않았다.

## 2026-07-31 — TMI-31 문항별 retry 점수 배열 응답 추가

<!-- codex-turn:019fb5d7-3c52-7f41-a7da-4d6d39f18444 -->

- 날짜: `2026-07-31`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD `b71b54b`, 변경은 미커밋 작업 트리)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 기록상 상태 `해야 할 일`; Jira 댓글·필드·상태를 변경하지 않았다.
- 작업 목표: 문항별 상세 피드백 응답에 동일 examId·questionNumber의 retryCount별 점수 배열을 추가한다.
- 응답 계약: 기존 `QuestionResult.question`에 `retryScores`를 additive 필드로 추가했다. JSON은 `[{"retryCount":0,"score":2.0},{"retryCount":1,"score":2.0}]` 형태이며 retryCount 오름차순으로 반환한다. 기존 필드는 삭제하거나 이름을 변경하지 않았다.
- 집계 규칙: 같은 examId와 questionNumber 결과만 사용하고 legacy `retryCount=null`은 0으로 해석한다. 동일 retry 문서가 여러 개면 기존 단건 조회와 동일하게 `_id`가 가장 큰 최신 문서 하나를 선택한다. 그 최신 문서의 score가 null이면 해당 retry는 배열에서 제외해 오래된 점수를 대신 노출하지 않는다.
- 변경 파일: `ExamResponseDTO.java`, `ExamServiceImpl.java`, `ExamConverter.java`, `ExamResultTest.java`, `ExamOwnershipServiceTest.java`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`다.
- 테스트: DTO JSON 배열 직렬화, 서로 다른 문항 제외, legacy null retry와 explicit 0 병합, 동일 retry 최신 점수 선택, 점수 없는 최신 결과 제외, retry 오름차순을 추가 검증했다. 집중 테스트와 `./gradlew clean test`가 성공했고 XML 기준 Java 207개, failures/errors/skipped 0개다. `git diff --check`도 성공했다.
- 유지한 외부 계약: 문항 상세 조회 URL·Method·Query Parameter와 기존 Response 필드, `BaseResponse`, 사용자 소유권, 실제 userId 비노출, AI/Callback `user_id=examId`, retryCount 의미, Redis Key/TTL, S3 Key와 grading 멱등성은 유지했다. 이번 사용자 요청에 따라 문항 상세 Response에 새 배열 필드만 명시적으로 추가했다.
- Git/Jira: commit·push와 Jira 댓글·필드·상태 변경을 수행하지 않았다. Secret과 Token은 기록하지 않았다.

## 2026-07-31 — TMI-31 retry 세부 피드백 점수 배열 응답 제안

<!-- codex-turn:019fb5dd-5912-7ee3-b3db-1e9a277bc385 -->

- 날짜: `2026-07-31`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD `b71b54b`, 기존 변경은 미커밋 작업 트리)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 기록상 상태 `해야 할 일`; Jira 댓글·필드·상태를 변경하지 않았다.
- 작업 목표: 현재 문항 상세의 `feedback.pronunciationFluencyScore`, `contentRelevanceScore`, `detailedScores`를 retry별로 비교할 수 있는 추가 배열 응답 형태를 애플리케이션 수정 전에 제안한다.
- 제안 계약: 기존 `feedback`과 `retryScores`를 유지하고 `question.retryFeedbackScores`에 `retryCount`, `pronunciationFluencyScore`, `contentRelevanceScore`, `detailedScores`를 담는 객체를 retryCount 오름차순으로 배치한다. `retryCount=0`만 의미하는지, 0부터 모든 retry 이력을 의미하는지는 사용자 확인 후 구현한다.
- 변경 파일: 이 단계에서는 애플리케이션·테스트 코드를 수정하지 않았다. 작업 기록 규칙에 따라 `docs/codex/WORKLOG.md`에 이 항목을 append하고 `docs/codex/CURRENT_STATE.md`를 갱신했다.
- 검증: 현재 `ExamResponseDTO`, `ExamServiceImpl`, `ExamConverter`의 문항 상세 조립 경로를 읽기 전용으로 확인했다. 코드 변경이 없어 Gradle 테스는 다시 실행하지 않았다.
- 유지 계약: 공개 API URL·Method·Query Parameter, 기존 응답 필드, `BaseResponse`, 소유권, AI/Callback `user_id=examId`, retryCount 의미, Redis·S3·grading 계약은 수정하지 않았다. Secret과 Token은 기록하지 않았다.

## 2026-07-31 — TMI-31 최초 응시 세부 피드백 비교 배열 추가

<!-- codex-turn:019fb5e1-5532-7bf1-9b4e-93a88980cde4 -->

- 날짜: `2026-07-31`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD `b71b54b`, 변경은 미커밋 작업 트리)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 기록상 상태 `해야 할 일`; Jira 댓글·필드·상태를 변경하지 않았다.
- 변경 동작: 문항 상세 `question` 객체에 `retryFeedbackScores` 배열을 추가했다. 요청한 현재 retry의 기존 `feedback`은 그대로 반환하고, 새 배열은 최초 응시 `retryCount=0`의 `pronunciationFluencyScore`, `contentRelevanceScore`, `detailedScores`만 한 객체로 반환한다. 최초 피드백이 없으면 빈 배열이다.
- 선택 규칙: 동일 `examId + questionNumber`만 사용하고 legacy `retryCount=null`을 0으로 해석한다. null과 명시적 0을 포함해 최초 응시 문서가 중복되면 기존 문항 상세 정책과 맞게 `_id`가 가장 큰 최신 문서 하나만 사용하며 retry 1 이상은 배열에 넣지 않는다.
- 변경 파일: `ExamResponseDTO.java`, `ExamServiceImpl.java`, `ExamConverter.java`, `ExamResultTest.java`, `ExamOwnershipServiceTest.java`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 테스트: DTO JSON 직렬화와 서비스 조립 테스트에서 최초 응시 한 건만 반환, legacy null/0 병합, 최신 0회차 선택, retry 1 이상 제외, 현재 retry `feedback` 유지와 세부 점수 형태를 확인했다. 집중 테스트와 `./gradlew clean test`가 성공했고 XML 기준 Java 207개, failures/errors/skipped 0개이다. `git diff --check`도 성공했다.
- 유지한 계약: 문항 상세 URL·Method·Query Parameter, 기존 Response 필드와 `BaseResponse`, 소유권, AI/Callback `user_id=examId`, retryCount 의미, Redis Key/TTL, S3 Key, Callback JSON과 grading 멱등성은 유지했다. 사용자가 명시적으로 요청한 문항 상세 응답 배열만 추가했다.
- 남은 위험: 기존 ObjectId와 결정적 String `_id`가 한 retry에 혼재한 legacy 중복은 문자열 `_id` 정렬이 실제 생성 시각과 다를 수 있는 기존 호환 위험이 남아 있다. Git commit·push와 Jira 쓰기는 수행하지 않았고 Secret과 Token은 기록하지 않았다.

## 2026-07-31 — TMI-31 문항 상세 프론트 응답 계약 정리

<!-- codex-turn:019fb5e8-1525-7810-a8f7-95909109ff9b -->

- 날짜: `2026-07-31`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD `b71b54b`, 변경은 미커밋 작업 트리)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31), 기록상 상태 `해야 할 일`; Jira 댓글·필드·상태를 변경하지 않았다.
- 작업 목표: 프론트엔드에 전달할 현재 `GET /api/v1/exams/{examId}/questions?questionNumber=...&retryCount=...` 성공 응답 계약을 실제 Controller·DTO·Converter 기준으로 정리한다.
- 응답 계약: HTTP 200의 `BaseResponse` 필드는 `isSuccess`, `code=COMMON_200`, `message=성공입니다.`, `result`이다. `result.question.feedback`은 query의 현재 retry 피드백, `retryScores`는 점수가 있는 retry별 총점 이력, `retryFeedbackScores`는 비교 기준인 canonical retry 0 세부 점수 한 건이다.
- optional 규칙: `PartResultDTO`의 null 필드와 `QuestionDTO`의 null 필드는 JSON에서 생략될 수 있다. 최초 피드백이 없으면 `retryFeedbackScores=[]`이며, Azure 결과가 없으면 `azureFeedback`은 생략될 수 있다. 문항 조회는 소유권 확인 후 응답한다.
- 변경 파일: 애플리케이션·테스트 코드는 수정하지 않았다. 작업 기록 규칙에 따라 `docs/codex/WORKLOG.md`에 이 항목을 append하고 `docs/codex/CURRENT_STATE.md`를 갱신했다.
- 검증: `ExamRestController`, `BaseResponse`, `SuccessStatus`, `ExamResponseDTO`, `ExamConverter`를 읽기 전용으로 확인했다. 코드 변경이 없어 Gradle은 다시 실행하지 않았고 직전 `./gradlew clean test`는 Java 207개, failures/errors/skipped 0개로 성공한 상태다.
- 유지한 계약: API URL·Method·Query Parameter, 기존 Response 필드, `BaseResponse`, 소유권, AI/Callback `user_id=examId`, retryCount, Redis·S3·grading 계약을 변경하지 않았다. Git commit·push와 Jira 쓰기를 수행하지 않았고 Secret과 Token은 기록하지 않았다.

## 2026-07-31 — TMI-31 Jira 완료 전환

<!-- codex-turn:019fb664-4d0f-79d1-93e0-23a09560acb5 -->

- 날짜: `2026-07-31`
- 브랜치: `feat/TMI-31-sequential-exam-assignment` (HEAD `b71b54b`, 코드 변경은 미커밋 작업 트리)
- Jira: [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31) 상태를 사용자의 명시적 요청에 따라 완료로 전환했다.
- 전환 전 확인: 상태는 `해야 할 일`(ID `10000`), resolution은 null이었고, 사용 가능한 `완료` 전환 ID는 `41`이었다.
- 실행 범위: `transitionJiraIssue`에 이슈 키 `TMI-31`과 transition ID `41`만 전달했다. 댓글, 설명, 담당자, 우선순위, 라벨 및 다른 필드는 변경하지 않았다.
- 전환 후 확인: 재조회에서 상태 `완료`(ID `10003`, done category)와 resolution `완료`(ID `10000`)를 확인했다.
- 변경 파일: 애플리케이션·테스트 코드는 수정하지 않았다. 작업 기록 규칙에 따라 `docs/codex/WORKLOG.md`에 이 항목을 append하고 `docs/codex/CURRENT_STATE.md`의 현재 Jira 상태를 갱신했다.
- 검증: Atlassian MCP 재조회로 상태와 resolution을 확인했다. 코드 변경이 없어 Gradle은 다시 실행하지 않았으며, Git commit·push는 수행하지 않았고 Secret과 Token은 기록하지 않았다.

## 2026-07-31 — AWS S3 Default Credentials Provider 전환

<!-- codex-turn:019fb693-395c-70b2-b685-e59026def3bc -->

- 날짜: `2026-07-31`
- 브랜치: `chore/add-actuator-health` (HEAD `b70d03f`, 변경은 미커밋 작업 트리)
- 작업 목적: Learning Core의 프로젝트 전용 AWS Access Key/Secret Key와 static credential 의존을 제거하고, 로컬 JVM·로컬 Docker·ECS에서 AWS SDK v2 Default Credentials Provider Chain을 사용하도록 전환한다. 이번 작업에 별도 Jira 이슈 키는 제공되지 않았고 Jira 댓글·필드·상태를 변경하지 않았다.
- 기존 구조와 제거 사항: `S3Config`의 Access Key/Secret Key `@Value`, basic/static credential 생성과 운영·테스트 YAML의 credentials property를 제거했다. 프로젝트 전용 AWS key 환경변수는 더 이상 애플리케이션 시작 조건이나 지원 계약이 아니다. 기존 사용자 작업인 Actuator 의존성과 Health 설정은 보존했다.
- 최종 구조: 공유 `DefaultCredentialsProvider` Bean을 `S3Client`와 `S3Presigner`에 주입했다. 기존 `AWS_REGION`, `AWS_S3_BUCKET_NAME`, S3 Object Key, Presigned URL 만료와 Upload/Download 흐름은 유지했다. AWS shared/SSO profile을 지원하도록 동일 AWS SDK 버전의 `sso` 모듈을 추가했으며 Spring Cloud AWS 자동 구성과 중복 S3 Bean은 없다.
- 로컬 실행: JVM은 AWS CLI profile·AWS SSO·SDK 표준 환경변수를 사용할 수 있다. Docker는 non-root `app`의 `/app/.aws`에 host profile을 read-only mount하고 `AWS_PROFILE`을 지정하도록 README와 `.env.example`에 기록했다. 추적되지 않은 `.env.docker.local`에서는 AWS credential 변수 행만 값 노출 없이 제거하고 나머지 설정을 보존했다.
- ECS 정책: 컨테이너에 AWS key를 주입하지 않고 ECS Task Role의 임시 자격 증명을 Container Credentials Provider로 사용한다. Task Execution Role과 Task Role의 차이, 현재 코드에 필요한 `s3:GetObject`와 `s3:PutObject` 최소 권한을 실제 ARN 없이 문서화했다.
- 추가 테스트: `S3ConfigTest` 5개와 `S3ConfigurationContractTest` 4개, 총 9개를 추가했다. credential property 없이 S3 두 Bean 생성, Default Provider 사용과 static provider 배제, Region/Bucket 계약, 운영·테스트 YAML 및 `.env.example`의 key 미요구를 실제 AWS 네트워크 호출 없이 검증한다.
- 전체 테스트 결과: 집중 테스트 9개와 `./gradlew clean test`가 성공했다. XML 기준 총 216개, failures/errors/skipped 0개이며 기존 `ExamServiceImpl` unchecked 경고 외 새 오류는 없다. `git diff --check`도 성공했다.
- Docker 검증: `docker buildx build --platform linux/amd64 --load`가 성공했다. credential 변수 없는 env 파일과 `AWS_PROFILE=default`로 컨테이너를 실행했으며 AWS credential 파일이 없는 상태에서도 `/actuator/health`가 HTTP 200과 `UP`을 반환했다. 검증 컨테이너는 종료·정리했다.
- 검색·보안 검증: 운영 코드와 설정에서 프로젝트 전용 AWS key 이름, static/basic credential class와 credentials property가 제거됐다. README의 제거 설명과 이를 금지하는 테스트 문자열만 남아 있으며 실제 AWS Key 패턴은 발견되지 않았다. Secret, Token, 실제 Key, URI, Profile 내용과 Presigned URL은 코드·로그·문서·이 기록에 남기지 않았다.
- 남은 위험: 현재 host에 `~/.aws`가 없어 read-only profile mount 및 실제 AWS Profile/SSO를 사용한 Presigned Upload/Download Smoke Test는 수행하지 못했다. AWS 콘솔의 ECS Task Role 생성·연결과 실제 Bucket 정책은 제외 범위이며 배포 전 별도 검증이 필요하다.
- Git/Jira: commit, push, PR 생성과 Jira 댓글·필드·상태 변경을 수행하지 않았다.

## 2026-07-31 — AWS S3 자격 증명 전환 독립 코드 리뷰

<!-- codex-turn:019fb6aa-95cc-7723-b597-521ee0b80c8f -->

- 날짜: `2026-07-31`
- 브랜치: `chore/add-actuator-health` (HEAD와 merge base 모두 `b70d03f38afc239849086fef6549bc3af47c89f6`, 변경은 미커밋 작업 트리)
- 작업 범위: `git diff b70d03f38afc239849086fef6549bc3af47c89f6`의 tracked 변경과 신규 `.env.example`, S3 설정 테스트 2개를 함께 검토했다. 이번 리뷰에 별도 Jira 이슈 키는 제공되지 않았고 Jira 댓글·필드·상태를 변경하지 않았다.
- 리뷰 결과 P1: Runtime에는 `software.amazon.awssdk:sso`만 있고 `ssooidc`와 `sts`가 없다. 현대식 `sso_session` profile은 `SsoOidcProfileTokenProviderFactory`, Web Identity는 `StsWebIdentityCredentialsProviderFactory`를 reflection으로 찾다가 첫 Presigned URL 또는 `HeadObject` 시점에 실패하므로 README에 명시한 두 credential source가 동작하지 않는다.
- 리뷰 결과 P2: README의 host `~/.aws` read-only bind mount는 Dockerfile이 만든 별도 UID의 non-root `app` 사용자로 실행된다. host AWS 파일이 통상적인 owner-only 권한이면 UID가 다른 컨테이너 사용자가 읽지 못해 local Docker profile 인증이 실패하므로 UID/GID 또는 안전한 파일 소유권 전달 방식을 보완해야 한다.
- 검증: 현재 resolved classpath를 사용한 외부 인프라 없는 Java probe에서 현대식 SSO profile은 `ssooidc` class 부재, Web Identity는 `sts` class 부재로 실패함을 확인했다. `git diff --check`는 기록 갱신 전 성공했고, 변경 구현 당시 XML은 Java 216개와 failures/errors/skipped 0개를 기록한다.
- 테스트 제한: 정확한 `./gradlew clean test`를 실행했으나 sandbox가 사용자 Gradle wrapper distribution lock 파일 쓰기를 허용하지 않아 task 시작 전에 중단됐다. 실제 AWS, MongoDB, Redis, Python AI 서버와 Sentry는 호출하지 않았다.
- 변경 파일과 계약: 리뷰 대상 애플리케이션·테스트 코드는 수정하지 않았고 작업 기록 규칙에 따라 `docs/codex/WORKLOG.md` 끝에 이 항목을 append하고 `docs/codex/CURRENT_STATE.md`를 갱신했다. 공개 API URL·Method·Parameter·DTO·`BaseResponse`, 실제 userId 비노출, AI/Callback `user_id=examId`, retryCount, Redis Key, S3 Object Key와 Presigned URL 형식은 변경하지 않았다.
- 남은 조치: `ssooidc`·`sts` 의존성과 local Docker profile 파일 접근 방식을 수정한 뒤 credential resolution 단위 테스트와 `./gradlew clean test`를 다시 실행해야 한다. Git commit·push는 수행하지 않았고 Secret과 Token은 기록하지 않았다.

## 2026-07-31 — AWS S3 Default Credentials 최종 리뷰 요청

<!-- codex-turn:019fb6bc-3c99-7b72-88c6-a385289f390e -->

- 날짜: `2026-07-31`
- 브랜치: `chore/add-actuator-health` (HEAD와 main 모두 `b70d03f`, 변경은 미커밋 작업 트리)
- 작업 목적: AGENTS.md와 Codex 상태·이력을 기준으로 Static Credential 제거, Default Provider, SDK 모듈, Profile/SSO, ECS, Region/Bucket, Presign, Health, Docker, 테스트와 외부 계약을 main 대비 읽기 전용 최종 리뷰한다.
- 진행 상태: 직전 독립 리뷰의 `ssooidc`·`sts` 누락과 Docker profile UID 권한 문제를 확인한 뒤 사용자의 이어서 작업 요청으로 같은 검토를 계속했다. 애플리케이션·설정·테스트 파일은 수정하지 않았다.
- 보안·외부 작업: Secret, Token, 실제 Key, URI와 Presigned URL을 기록하지 않았으며 Git commit·push·PR 생성과 Jira 댓글·필드·상태 변경을 수행하지 않았다. 이번 리뷰에 별도 Jira 이슈 키는 없다.

## 2026-07-31 — AWS S3 Default Credentials main 기준 최종 리뷰

<!-- codex-turn:019fb6d8-414d-7010-b65a-29809288045a -->

- 날짜: `2026-07-31`
- 브랜치: `chore/add-actuator-health` (HEAD, main, merge base 모두 `b70d03f38afc239849086fef6549bc3af47c89f6`, 변경은 미커밋 작업 트리)
- 리뷰 결과: HIGH 1건, MEDIUM 1건, LOW 2건을 확정했다. HIGH는 README가 지원한다고 명시한 현대식 SSO/Web Identity/assume-role credential source에 필요한 `ssooidc`와 `sts` runtime 모듈이 없는 문제다. MEDIUM은 native Linux처럼 host UID와 image `app` UID가 다른 환경에서 owner-only AWS profile bind mount를 읽지 못하는 문서 예시다.
- LOW 결과: `.dockerignore`가 `.aws`를 제외하지 않아 project-local profile 복사본이 build context로 전달될 수 있다. 또한 신규 S3 테스트는 Bean 생성과 `S3Client` Region만 실행하며 실제 credential resolution, `S3Presigner` Region과 credential 없는 Actuator 요청을 검증하지 않아 누락 모듈 상태에서도 모두 통과한다.
- 정상 확인: Static Key와 static/basic credential 구현은 운영 코드·설정에서 제거됐고 하나의 Default Provider Bean이 두 S3 Bean에 주입된다. Region/Bucket과 Presign Object Key·Method·만료·소유권, ECS Task Role과 최소 S3 권한, JWT·AI·Redis·grading·Callback·시험 배정·MongoDB·외부 API/DTO 계약에는 main 대비 회귀를 확인하지 않았다. Spring의 inferred destroy lifecycle로 S3 두 Bean과 closeable Provider가 종료 대상이 된다.
- 의존성 검증: `dependencyInsight`에서 포함된 AWS SDK 모듈은 모두 `2.29.52`였고 version 혼합은 없었다. `ssooidc`와 `sts` 조회는 runtimeClasspath에 일치하는 dependency가 없음을 각각 확인했다.
- Docker 검증: 가짜 owner-only 파일을 사용한 현재 macOS Docker Desktop probe에서는 bind mount 파일 UID가 container `app` UID로 매핑되어 읽기에 성공했다. 이 결과는 UID를 그대로 보존하는 native Linux Docker의 실패 가능성을 제거하지 않으므로 실행 예시는 여전히 portable하지 않다.
- 테스트와 정적 검증: `./gradlew clean test`가 성공했고 XML 기준 216개, failures/errors/skipped 0개다. `git diff --check main --`도 성공했다. 실제 AWS, Atlas, Redis, Python AI와 Sentry는 호출하지 않았다.
- 변경 파일: 리뷰 대상 애플리케이션·설정·테스트 코드는 수정하지 않았다. 종료 기록 규칙에 따라 `docs/codex/WORKLOG.md` 끝에 이 항목을 append하고 `docs/codex/CURRENT_STATE.md`만 최신화했다.
- 후속 조치: 같은 AWS SDK 버전의 `ssooidc`·`sts`를 추가하거나 문서 지원 범위를 축소하고, Docker UID/GID 또는 안전한 profile 복사 방식을 문서화한다. `.aws` ignore와 offline credential-resolution·Presigner Region·Health 회귀 테스트를 보강한 뒤 전체 테스트를 다시 실행한다.
- Git/Jira/보안: commit·push·PR 생성과 Jira 댓글·필드·상태 변경을 수행하지 않았다. 별도 Jira 이슈 키는 없으며 Secret, Token, 실제 Key, URI, Profile 내용과 Presigned URL을 기록하지 않았다.

## 2026-07-31 — 문항 단건 결과의 모범답안 음성 계약 확정

<!-- codex-turn:019fb704-87e0-7343-95d2-190170e74d61 -->

- 날짜: `2026-07-31`
- 브랜치: `chore/add-actuator-health` (HEAD `b70d03f`, 기존 AWS/Actuator 변경과 이번 변경 모두 미커밋 작업 트리)
- 작업 목적: 기존 문항 단건 API 계약을 유지하면서 Part 1 Question 1·2에만 모범답안 음성 URL과 단어 시퀀스를 추가하고, 최종 응답 계약 변경에 따라 `modelAnswer.text`를 완전히 제거한다. 이번 작업에 별도 Jira 이슈 키는 없으며 Jira 댓글·필드·상태를 변경하지 않았다.
- 최종 응답 계약: `ExamResponseDTO.PartResultDTO`에 nullable `modelAnswer`를 additive로 추가했다. `ModelAnswerResponse`는 `audioUrl`, `spokenWordSequence` 두 필드만 가지며 모범답안 문장, reference/script/transcript/answer 계열 필드는 DTO·Builder·JSON·OpenAPI·README에 없다. null인 `modelAnswer`는 기존 `NON_NULL` 정책으로 필드 자체를 생략한다.
- MongoDB 처리: MongoDB `model_answer` 데이터는 유지하며 조회·수정·삭제하지 않았다. 해당 컬렉션을 문항 단건 응답에 매핑하지 않고, 모범답안 텍스트 존재 여부를 성공 조건이나 오류 조건으로 사용하지 않는다. `questionInfo.referenceText`를 포함한 기존 문제 정보는 그대로 유지했다.
- 오디오 URL: 소유권 확인을 통과한 `ExamSession`에서 실제 `mockExamId`를 해석하고 `{mockExamId}/part1_a{questionNumber}.wav` Key를 결정해 기존 `S3Presigner`로 60분 Presigned GET URL을 생성한다. 고정 시험지 ID, HeadObject, static credential, 새 AWS 환경변수와 URL DB 저장은 추가하지 않았다.
- 단어 시퀀스: `mock_exam_004` q1 55개와 q2 53개 데이터를 classpath catalog로 추가했다. q1/q2를 독립 선택하고 내부 record를 응답 DTO로 변환해 index·segmentIndex·wordIndex, Long offset·duration, Double accuracyScore·pronunciationScore와 errorType을 손실 없이 유지한다. 사용자 음성 `question.spokenWordSequence`와 모범답안 음성 `question.modelAnswer.spokenWordSequence`는 별도 소스로 조립한다.
- 적용 판정: Mongo 원본 `Question.partNumber=1`이며 `questionNumber=1` 또는 `2`일 때만 `modelAnswer`를 조립한다. retryCount가 달라도 같은 시험지·문항 metadata와 Object Key를 사용하며, 다른 Part·문항에는 null·빈 객체 대신 필드를 생략한다. metadata가 없는 다른 시험지에 `mock_exam_004` 데이터를 임의 fallback하지 않는다.
- 추가·수정 테스트: catalog q1/q2 분리와 수치 타입, Q1/Q2 응답·S3 Key·실제 Session ID, retry 독립성, 사용자/모범답안 URL·시퀀스 분리, 기존 feedback·questionInfo 회귀, 비대상 필드 생략, DTO 필드 제한·금지 필드 비직렬화, OpenAPI 설명, 기존 URL·Query·Summary 계약을 추가 검증했다. 기존 소유권·Callback·서비스 직접 생성 테스트와 JWT WebMvc slice에는 새 의존성 Mock을 추가했으며 실제 AWS API를 호출하지 않았다.
- 전체 테스트 결과: 집중 테스트 후 `./gradlew clean test`를 실행했고 XML 기준 Java 229개, failures/errors/skipped 0개로 성공했다. 기존 `ExamServiceImpl` unchecked 경고 외 새 컴파일 오류는 없다. `git diff --check`도 성공했다.
- 검색·보안 검증: 문항 단건 운영 경로에는 Mongo `model_answer` 조회, 모범답안 문장 매핑과 text 직렬화가 없다. `part1_a1.wav`·`part1_a2.wav`는 실제 Session ID 기반 Key를 검증하는 테스트에서 확인했고 운영 구현은 `part1_a%d.wav` 형식이다. `mock_exam_003`은 기존 legacy fallback·기존 문서·테스트에만 남으며 신규 응답 구현은 고정값으로 사용하지 않는다. static credential과 프로젝트 전용 AWS key 문자열은 기존 금지 계약 테스트·설명에만 있고 이번 기능에 추가하지 않았다.
- 유지 계약: 공개 API URL·Method·Path/Query·`BaseResponse`, `/summary`, 기존 feedback·azureFeedback·사용자 audioUrl·spokenWordSequence·questionInfo, JWT·Guest JWT·소유권, Redis Key/TTL, 사용자 제출 S3 Key, AI·Callback `user_id=examId`, grading retry·멱등성·시험 배정·Default Credentials·Health를 변경하지 않았다.
- 남은 위험: 제공된 단어 시퀀스는 `mock_exam_004`만 포함한다. 다른 활성 MockExam의 Part 1 Question 1·2에도 같은 응답이 필요하면 해당 시험지의 실제 q1/q2 metadata를 별도로 추가해야 하며 다른 시험지 데이터를 재사용해서는 안 된다. 기존 미커밋 AWS 설정 리뷰에서 확인된 `ssooidc`/`sts` 모듈과 Docker UID portability 위험은 이번 범위 밖이라 수정하지 않았다.
- 보안·외부 작업: Secret, Token, 실제 AWS 자격 증명·URI·Presigned URL을 코드·로그·문서에 기록하지 않았다. Git commit·push·PR 생성과 Jira 댓글·필드·상태 변경을 수행하지 않았다.

## 2026-07-31 — AI 문항 채점 client source 전달 방식 분석

<!-- codex-turn:019fb711-2576-7472-a169-8337e65e9874 -->

- 날짜: `2026-07-31`
- 브랜치: `chore/add-actuator-health` (HEAD `b70d03f`, 작업 트리의 기존 변경은 유지)
- 작업 목적: 문제 채점 요청에서 앱과 웹 출처를 Python AI에 전달하기 위해 `submitQuestion`에 String을 추가하는 방안이 현재 멱등 Job·retry 구조와 외부 계약에 적합한지 분석한다. 이번 작업에 별도 Jira 이슈 키는 없다.
- 현재 흐름: 공개 submit API는 `examId`, `questionNumber`, 기본값 0인 `retryCount`를 받아 소유권 확인 후 `ExamGradingService.submitQuestion`을 호출한다. 최초 요청은 결정적 `QuestionGradingJob`을 생성·claim하고 `QuestionDispatchClaim`을 거쳐 AI multipart에 `user_id=examId`, mockExam/part/question/retry와 음성만 전송한다. 시험 단위 retry와 stale recovery도 저장된 Job에서 새 Claim을 만든다.
- 분석 결론: 현재 앱 Learning Core와 웹 POC 백엔드는 별도이므로 앱이 자유 String을 submit API로 보내게 하는 방식보다 이 백엔드가 `app` 출처를 결정해 AI outbound에 추가하는 방식이 단순하고 신뢰 가능하다. Python AI가 신규 필드를 optional로 받고 기존 웹 요청의 누락을 `web`으로 해석하면 기존 웹 POC 수정과 공개 submit API 변경을 피할 수 있다.
- retry 주의점: `submitQuestion` 메서드 인자에만 source를 추가하면 최초 dispatch 후 시험 retry·scheduler recovery 경로에서 값이 소실된다. 동일 백엔드가 앱과 웹을 함께 처리해야 한다면 API 경계에서 optional Header를 받고 `ClientSource` enum으로 검증하며 `QuestionGradingJob`과 `QuestionDispatchClaim`에 불변 source를 저장·전파해야 한다. Job ID와 `Idempotency-Key`에는 source를 포함하지 않고 최초 저장값을 유지하는 편이 멱등성에 안전하다.
- 계약 제안: Python과 `client_source` 같은 최종 JSON/multipart 필드명, 허용값 `app|web`, 누락 시 `web` 처리 여부를 먼저 합의한다. source는 분석·routing metadata로만 사용하고 JWT 인증, 시험 소유권, `user_id=examId`, retryCount, Redis/S3 Key와 Callback JSON에는 사용하지 않는다. 요약 AI 요청도 구분이 필요할 때만 같은 정책을 별도로 적용한다.
- 변경·검증: 이번 turn에서는 설계 분석과 Codex 기록만 수행했으며 애플리케이션·테스트·설정은 수정하지 않았다. 코드 변경이 없어 Gradle 테스트는 다시 실행하지 않았다. Secret, Token, 실제 URI와 Presigned URL을 기록하지 않았고 Git commit·push·PR 생성 및 Jira 댓글·필드·상태 변경을 수행하지 않았다.

## 2026-07-31 — 앱 문항 채점 AI 요청에 client source 추가

<!-- codex-turn:019fb714-3efb-7fa2-bfec-061143a9c15a -->

- 날짜: `2026-07-31`
- 브랜치: `chore/add-actuator-health` (HEAD `b70d03f`, 기존 작업 트리 변경을 보존한 미커밋 상태)
- 작업 목적: 앱과 웹 백엔드가 별도라는 확정에 따라 앱 Learning Core가 Python AI로 보내는 문항 채점 요청에 앱 출처를 전달한다. 이번 작업에 별도 Jira 이슈 키는 없다.
- 구현: `GradingDispatchService.dispatchQuestion`의 기존 multipart에 `client_source=app`을 추가했다. 필드명과 값은 상수로 중앙화했으며 클라이언트 Request, Controller, `ExamService.submitAudio`, `ExamGradingService.submitQuestion`, Job과 Claim 시그니처는 변경하지 않았다.
- retry·멱등성: 최초 submit과 시험 단위 retry·stale recovery가 같은 dispatch 메서드를 사용하므로 모든 앱 문항 AI 요청에 source가 포함된다. 결정적 Question Job ID, `Idempotency-Key`, retryCount와 기존 S3 Key는 변경하지 않았고 source를 인증·소유권에 사용하지 않는다.
- 유지 계약: 기존 multipart의 `user_id=examId`, `mock_exam_id`, `part_number`, `question_number`, `retry_count`, `audio_file`을 그대로 유지한다. 전체 요약 AI JSON과 세 종류 Callback JSON에는 `client_source`를 추가하지 않았으며 공개 API URL·Method·Parameter·Request/Response DTO·`BaseResponse`도 변경하지 않았다.
- 테스트: `GradingDispatchServiceTest`에서 Question Body의 `client_source=app`과 Summary Body의 `client_source` 미포함을 검증했다. 집중 테스트가 성공했고 최종 `./gradlew clean test`도 성공했다. XML 기준 Java 229개, failures/errors/skipped 0개이며 기존 `ExamServiceImpl` unchecked 경고 외 새 오류는 없다. `git diff --check`도 성공했다.
- 문서·연동: README에 앱 문항 AI 요청의 source와 공개 submit API·Summary·Callback 비변경 범위를 기록했다. Python AI는 multipart의 `client_source=app`을 읽도록 연동해야 하며 기존 웹 POC 요청의 필드 누락을 `web`으로 해석하는 처리는 Python 측 후속 검증 사항이다.
- 보안·외부 작업: 실제 AWS, MongoDB, Redis, Python AI와 Sentry를 호출하지 않았고 Secret, Token, 실제 URI와 Presigned URL을 기록하지 않았다. Git commit·push·PR 생성과 Jira 댓글·필드·상태 변경을 수행하지 않았다.

## 2026-07-31 — ExamGradingService와 GradingDispatchService 역할 분석

<!-- codex-turn:019fb718-9217-7243-bba1-645cef03c43b -->

- 날짜: `2026-07-31`
- 브랜치: `chore/add-actuator-health` (HEAD `b70d03f`, 기존 작업 트리 변경 유지)
- 작업 목적: `ExamGradingService`와 `GradingDispatchService`가 현재 채점 흐름에서 각각 담당하는 책임과 분리 이유를 코드 기준으로 설명한다. 이번 작업에 별도 Jira 이슈 키는 없다.
- `ExamGradingService`: 결과 선조회, retryCount 정규화, 결정적 Question/Summary Job 생성, optimistic claim, 중복 submit 멱등 처리, timeout·max attempts 기반 retry, 실패·완료 전이, 전체 상태와 Redis projection, Summary gate를 관리하는 orchestration/state-machine 계층이다. AI 점수를 직접 계산하지 않는다.
- `GradingDispatchService`: claim된 `QuestionDispatchClaim`·`SummaryDispatchClaim`을 받아 S3 Presigned GET과 음성 다운로드, AI multipart/JSON Body와 `Idempotency-Key` 생성, Python AI HTTP 호출을 수행하는 integration/transport 계층이다. Repository·Redis·retry 정책을 소유하지 않는다.
- 실패 경계: Dispatch가 외부 I/O 실패를 예외로 알리면 Grading이 현재 dispatch attempt와 Job 상태를 FAILED로 전이하고 전체 상태를 갱신한다. 이 분리로 retry·멱등성 상태 테스트와 실제 HTTP 계약 테스트를 독립적으로 유지한다.
- client source 위치: `client_source=app`은 앱 전용 백엔드가 만드는 AI wire metadata이고 Job identity나 retry 판단에 쓰이지 않으므로 Dispatch에 두는 것이 현재 책임 경계에 맞다. 최초 submit과 recovery 모두 같은 Dispatch 경로를 사용한다.
- 변경·검증: 이번 turn은 읽기 전용 코드 분석과 Codex 기록 갱신만 수행했다. 애플리케이션·테스트 코드는 수정하지 않아 Gradle 테스트를 다시 실행하지 않았다. Secret, Token, 실제 URI와 Presigned URL을 기록하지 않았고 Git commit·push·PR 생성과 Jira 댓글·필드·상태 변경을 수행하지 않았다.

## 2026-07-31 — AWS Default Credentials 리뷰 finding 후속 수정

<!-- codex-turn:019fb719-d36d-7c43-9d4a-174081adfb5f -->
<!-- codex-turn:019fb71e-1b5e-7811-b15a-8325e12959dc -->
<!-- codex-turn:019fb724-743b-7a50-9f0d-e65420bf7b16 -->
<!-- codex-turn:019fb736-ab5e-7fb2-b0a9-8b471b0946cc -->
<!-- codex-turn:019fb73a-5e19-74f0-ae12-c63c92eda46f -->

- 날짜·범위: `2026-07-31`, 브랜치 `chore/add-actuator-health`, HEAD·main `b70d03f`. AWS Default Credentials 전환 리뷰의 누락 모듈, local Docker 권한, build context와 테스트 finding을 후속 수정했다. 별도 Jira 이슈 키는 없다.
- AWS SDK BOM: `2.29.52` BOM 하나로 직접 버전 선언을 제거하고 `s3`, `sso`, `ssooidc`, `sts`를 선언했다. `runtimeClasspath`와 dependency insight에서 이 네 모듈 및 transitive `auth`·`profiles`를 포함한 AWS SDK가 모두 `2.29.52`로 해석되고 혼합 버전이 없음을 확인했다.
- Credential 지원: 기존 공유 `DefaultCredentialsProvider` Bean과 이를 함께 사용하는 `S3Client`·`S3Presigner` 구조를 유지했다. 일반 Profile, IAM Identity Center의 현대식 `sso_session`, Assume Role Profile, Web Identity, ECS Container Credentials와 EC2 Instance Profile 경로에 필요한 runtime 모듈을 갖췄다.
- local Docker: Docker Desktop for macOS의 read-only `.aws` mount 예시를 유지하고, native Linux는 host UID/GID와 image app group `999`, `HOME=/app`을 함께 지정하도록 문서화했다. 현재 Docker Desktop의 owner-only 빈 모의 Profile·SSO cache로 non-root, Java `user.home=/app`, JAR 읽기, `/tmp` 쓰기와 read-only 접근을 확인했으며 실제 native Linux host 실행 성공을 주장하지 않는다.
- Docker 보안: `.dockerignore`에 root와 중첩 `.aws` 제외 규칙을 추가했다. linux/amd64 이미지를 새로 빌드했고 image의 `/app/.aws` 부재, 기본 non-root `app`, `SIGTERM`, JAR 읽기와 `/tmp` 쓰기를 확인했다.
- 테스트 보강: SSO·SSOOIDC·STS·Profile·Web Identity classpath 5개, S3 Bean·공유 Provider·Client/Presigner Region·지연 credential 조회 계약 7개, credential 조회 없는 Health 1개를 추가·보강했다. 기존 설정 계약 4개를 합쳐 S3 credential 관련 17개가 성공했다.
- 전체 검증: `git diff --check` 사전 검사, `./gradlew clean test`, runtime dependency report와 insight를 실행했다. 전체 Java 테스트는 XML 기준 237개, failures/errors/skipped 0개다. 민감 패턴 검색에서도 실제 Key, private key, 자격증명 포함 URI와 Presigned URL 서명값을 찾지 못했다.
- Docker Health: Credential 환경변수와 Profile mount 없이 새 이미지를 실행해 `/actuator/health` HTTP 200과 `UP`을 확인했다. Health 응답은 status와 기존 probe group 이름만 포함했고 검증 컨테이너는 SIGTERM으로 종료했다.
- 유지 계약: S3 Region·Bucket, Object Key, Presigned URL 만료·Method·서명 조건, Upload/Submit API, retryCount, Redis Key/TTL, AI·Callback `user_id=examId`, grading retry·멱등성, JWT·Guest JWT, 시험 배정과 기존 `modelAnswer` 응답을 변경하지 않았다. 기존 작업 트리의 별도 사용자 변경도 보존했다.
- 남은 위험: 실제 AWS Profile/SSO로 S3를 호출하는 Smoke Test, 실제 native Linux Docker host, ECS Task Role과 대상 Bucket IAM 정책은 로컬 범위를 넘어 수행하지 않았다. 배포 전 해당 환경에서 별도 확인해야 한다.
- 외부 작업·보안: Git commit·push·PR 생성과 Jira 댓글·필드·상태 변경을 수행하지 않았다. Secret, Token, 실제 Credential·Profile 내용·URI와 Presigned URL을 기록하지 않았다.

## 2026-07-31 — 시험 세션 문항 prompt 조회 API 추가

<!-- codex-turn:019fb749-975c-77c2-9de4-853d24e09412 -->

- 날짜·범위: `2026-07-31`, 브랜치 `chore/add-actuator-health`, HEAD·main `b70d03f`. 별도 Jira 이슈 키 없이 세션에 실제 배정된 문제지의 문항을 다시 조회하는 API를 추가했다.
- API 계약: `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`, Request Body와 Query Parameter 없음, 기존 `BaseResponse` 안에 `QuestionDTO`를 반환한다. 기존 URL·Method·DTO는 변경하지 않은 additive API다.
- 인증·소유권: JWT 모드에서는 기존 Bearer 인증과 검증된 JWT `sub` UUID를 사용한다. 서비스가 examId로 `ExamSession`을 찾고 `ExamSession.userId`와 현재 사용자를 비교한 뒤에만 문제지를 조회하므로 다른 사용자는 `COMMON403`으로 차단된다. local/test legacy 정책은 유지했다.
- 시험지·문항 조회: `ExamSession.mockExamId`를 기존 호환 정책으로 해석하고 중복 안전성을 가진 `MockExamCatalogService.getRequiredExam`으로 문제지를 조회한 뒤 questionNumber가 일치하는 문항을 선택한다. Session이 없는 경우 기존 `EXAM_4004`, 문항이 없으면 기존 `EXAM_4002`를 사용한다.
- Part별 응답: 기존 세션 생성의 `QuestionDTO`와 URL 조립 helper를 재사용해 Part, 문항 번호, 지문·참고문구·Part 안내, 이미지·표, 준비·답변 시간과 문제 음성 URL을 반환한다. Part 3은 기존 안내 음성 URL도 반환하며 내부 userId·mockExamId·Mongo ID는 노출하지 않는다.
- 유지 계약: 문제 음성 S3 Key와 60분 만료, 세션 생성 응답, Upload/Submit API, 사용자 음성 Key, retryCount, Redis Key/TTL, Python AI `user_id=examId`, Callback JSON, grading retry·멱등성, 순차·순환 배정을 변경하지 않았다.
- 테스트: URL과 PathVariable 계약, Session에 저장된 문제지 선택, Part 3 안내 음성, 문항 없음 시 Presign 미호출, Session 없음·타 사용자 선차단, JWT 미제공 401·소유자 200·타 사용자 403과 외부 ID 비노출을 검증했다. 집중 테스트 55개가 성공했다.
- 전체 검증: `./gradlew clean test`가 성공했고 XML 기준 Java 245개, failures/errors/skipped 0개다. `git diff --check`도 성공했다.
- 외부 작업·보안: Git commit·push·PR 생성과 Jira 댓글·필드·상태 변경을 수행하지 않았다. Secret, Token, 실제 Credential·URI와 Presigned URL을 기록하지 않았다.

## 2026-07-31 — main 기준 누적 변경 코드 리뷰

<!-- codex-turn:7d9d2dbd-1506-4369-a0d7-ec9679bc1556 -->

- 날짜·범위: `2026-07-31`, 브랜치 `chore/add-actuator-health`, HEAD·main·지정 merge base `b70d03f38afc239849086fef6549bc3af47c89f6`. `git diff b70d03f38afc239849086fef6549bc3af47c89f6`의 추적 변경과 작업 트리의 신규 미추적 운영·리소스·테스트 파일을 함께 독립 리뷰했다. 별도 Jira 이슈 키는 제공되지 않았다.
- 리뷰 결과: 수정 가치가 확실한 correctness finding을 확인하지 않았다. Actuator Health, AWS Default Credentials Provider Chain, 문항 상세의 선택적 `modelAnswer`, AI 문항 multipart의 `client_source=app`, 신규 prompt API와 관련 테스트를 변경 전 흐름 및 저장소 규칙과 대조했다.
- 계약 확인: 기존 공개 API URL·Method·Parameter·`BaseResponse`, retryCount, Redis Key/TTL, 기존 사용자 음성 및 S3 Object Key, AI·Callback `user_id=examId`와 Callback JSON은 유지된다. 신규 prompt API는 `ExamSession.userId` 소유권 확인 뒤 Session의 `mockExamId`를 사용하며, 외부 응답에 실제 userId를 추가하지 않는다.
- 정적 검증: `git diff --check b70d03f38afc239849086fef6549bc3af47c89f6`가 성공했다. 모델답안 catalog JSON의 q1 55개·q2 53개 엔트리에 대해 필수 필드, 연속 index, 양수 duration과 중복 여부를 확인했고 이상이 없었다. 운영 소스·설정·문서 대상 민감 패턴 검사에서도 실제 AWS Key, private key, 자격증명 포함 Mongo URI와 서명된 Presigned URL을 찾지 못했다.
- 테스트 검증: 저장소 필수 명령 `./gradlew clean test`를 실행했으나 sandbox가 사용자 Gradle wrapper lock 쓰기를 허용하지 않아 task 시작 전에 중단됐다. writable 임시 `GRADLE_USER_HOME`과 read-only dependency cache를 사용한 offline 재시도도 sandbox의 file-lock UDP socket 제한으로 task 시작 전에 중단됐다. 현재 source보다 나중에 생성된 기존 XML은 Java 245개, failures/errors/skipped 0개이며 변경된 운영·테스트 class timestamp도 현재 source 이후임을 확인했다.
- 변경 파일: 리뷰 대상 애플리케이션·설정·테스트는 수정하지 않았다. 종료 기록 규칙에 따라 `docs/codex/WORKLOG.md`에 이 항목만 append하고 `docs/codex/CURRENT_STATE.md`를 갱신했다.
- 남은 환경 검증: 실제 AWS Profile/SSO, native Linux Docker와 ECS Task Role/Bucket IAM smoke test, Python AI의 `client_source=app` 수신 처리는 로컬 리뷰 범위를 넘어 수행하지 않았다. Git commit·push·PR 생성 및 Jira 댓글·필드·상태 변경도 수행하지 않았다.

## 2026-07-31 — AWS Default Credentials 후속 변경 main 기준 최종 리뷰

<!-- codex-turn:019fb75c-b1fe-7f01-b8e0-8979d28c7cc5 -->

- 날짜·범위: `2026-07-31`, 브랜치 `chore/add-actuator-health`, HEAD·main `b70d03f38afc239849086fef6549bc3af47c89f6`. tracked diff와 신규 미추적 운영·리소스·테스트 파일을 함께 리뷰했다. 이번 리뷰에 별도 Jira 이슈 키는 없다.
- 이전 finding 확인: AWS SDK BOM `2.29.52`가 `s3`, `sso`, `ssooidc`, `sts`와 transitive `auth`·`profiles`를 단일 버전으로 해석한다. Default Credentials Provider의 ECS Container Credentials 경로와 SSO OIDC·STS reflection factory가 runtime에 있으며, 공유 Provider를 S3Client·S3Presigner가 사용하고 Bean 생성 시 credential을 조회하지 않는다.
- Docker·Health 확인: 기존 linux/amd64 image는 non-root `app` UID/GID `999`, `/app` mode `750`, JAR mode `644`, `/tmp` mode `1777`이다. host UID/GID와 supplementary app group, `HOME=/app` 실행에서 Java `user.home=/app`, JAR 읽기와 `/tmp` 쓰기가 유지된다. `.dockerignore`는 root·중첩 `.aws`, `.env` 계열과 key 확장자를 제외하고 runtime image는 app JAR만 복사한다. Health 통합 테스트는 credential resolve를 호출하지 않고 HTTP 200·UP과 제한된 응답 필드를 검증한다.
- 리뷰 finding: HIGH 없음, MEDIUM 1건, LOW 없음이다. `README.md`의 macOS/native Linux 예시는 전체 SSO cache를 read-only mount하면서 현대식 `sso_session`을 지속 지원한다고 안내한다. AWS SDK `2.29.52`의 SSO OIDC provider는 만료 임박 토큰을 갱신한 뒤 같은 cache에 저장하므로 read-only mount에서는 장시간 실행 중 저장이 실패할 수 있다. host 측 SSO login 갱신 절차와 제한을 문서화하거나, host 원본은 read-only로 유지하면서 container-owned 임시 writable cache를 쓰는 안전한 방식을 설계해야 한다.
- 검증: `./gradlew dependencies --configuration runtimeClasspath`, AWS 전체 `dependencyInsight`, fresh `./gradlew clean test --no-daemon`과 `git diff --check main --`가 성공했다. XML 기준 Java 245개, failures/errors/skipped 0개이며 S3 credential 관련 17개도 모두 성공했다. 실제 Key·private key·자격증명 포함 URI·Presigned URL signature 패턴은 발견되지 않았다.
- 회귀 확인: 기존 S3 Region·Bucket·Object Key·Presigned URL 만료/Method, 시험 API·`BaseResponse`, JWT·Guest JWT, retryCount, Redis, grading retry·멱등성, Callback JSON과 AI `user_id=examId`에는 main 대비 회귀를 확인하지 않았다.
- 변경·외부 작업: 리뷰 대상 애플리케이션·설정·테스트 파일은 수정하지 않았고 Codex 기록만 갱신했다. 실제 AWS Profile/SSO, native Linux host와 ECS Task Role smoke test는 수행하지 않았다. Git commit·push·PR 생성과 Jira 댓글·필드·상태 변경을 수행하지 않았으며 Secret, Token, Profile 내용과 Presigned URL을 기록하지 않았다.

## 2026-08-04 — 로컬 Docker SSO read-only cache 운영 절차 보완

<!-- codex-turn:019fca3a-ea29-7c21-89e9-0f050c6bb1c4 -->

- 날짜·범위: `2026-08-04`, 브랜치 `chore/add-actuator-health`, HEAD·main `b70d03f`. 별도 Jira 이슈 키 없이 AWS Default Credentials 최종 리뷰의 MEDIUM finding 하나만 최소 범위로 수정했다.
- finding 수정: macOS와 native Linux에서 host `.aws` 전체를 read-only로 mount한 컨테이너가 현대식 SSO cache를 장시간 자동 갱신한다고 보장할 수 없던 문서 문제를 해소했다. read-write mount, credential 복사, world-readable 권한과 static key 방식은 도입하지 않았다.
- read-only SSO 정책: 각 개발 세션 전에 host AWS CLI에서 예시 Profile로 SSO 로그인하고 caller identity 검증 결과를 stdout에 내보내지 않도록 안내했다. 컨테이너는 이미 유효한 host Profile과 SSO cache를 읽기만 하며 cache를 수정하지 않는다.
- 만료 복구: SSO session 또는 Credential 만료, Presigned URL 생성 및 S3 credential 오류가 발생하면 host에서 다시 로그인·검증하고 기존 `--rm` 컨테이너를 종료한 뒤 같은 실행 명령으로 재시작하도록 문서화했다. 실행 중 컨테이너가 재로그인 결과를 즉시 읽는다고 보장하지 않는다.
- 실행 환경: macOS의 read-only mount를 유지했고 native Linux는 host UID/GID, image app group GID `999`, `HOME=/app`과 read-only mount를 유지했다. 일반 Shared Credentials Profile은 해당 credential 자체의 만료 정책을 따르며, IAM Identity Center SSO Profile은 host 로그인과 만료 시 재로그인·재시작 절차를 따른다.
- ECS 영향: ECS에는 Profile mount, SSO login과 static Access Key 환경변수를 사용하지 않으며 기존 Task Role의 ECS Container Credentials 흐름을 그대로 유지한다.
- 검증: `git diff --check`가 성공했고 `./gradlew clean test`는 XML 기준 Java 245개, failures/errors/skipped 0개로 성공했다. README에 host 로그인·credential 검증, read-only 제한, 만료 복구, 컨테이너 재시작과 ECS Task Role 설명이 있음을 확인했다.
- 남은 위험·보안: 실제 AWS Profile/SSO S3 smoke test와 실제 native Linux host 검증은 수행하지 않았다. Secret, Token, 실제 Credential·Profile 내용·URI와 Presigned URL을 기록하지 않았고 Git commit·push·PR 생성 및 Jira 댓글·필드·상태 변경을 수행하지 않았다.

## 2026-08-04 — 프론트 문항별 피드백·모범답안 응답 구조 분석

<!-- codex-turn:019fca40-e1cc-77e3-995c-0fdf66c66f50 -->

- 날짜·범위: `2026-08-04`, 브랜치 `chore/add-actuator-health`, HEAD `b70d03f`. 현재 미커밋 작업 트리의 Controller, 응답 DTO, Converter, Service, 모범답안 catalog와 계약 테스트를 기준으로 프론트 문항별 피드백 응답 구조를 분석했다. 별도 Jira 이슈 키는 없다.
- 공개 계약: 프론트는 `GET /api/v1/exams/{examId}/questions?questionNumber={questionNumber}&retryCount={retryCount}`의 HTTP 200 `BaseResponse.result.question`을 받는다. `questionNumber`는 필수, `retryCount`는 선택·기본값 `0`이며 기존 URL·Method·Query·`BaseResponse`를 변경하지 않았다.
- 피드백 구조: 요청 회차의 최신 결과는 `question.feedback`에 camelCase로 반환한다. `retryScores`는 회차별 최신 총점, `retryFeedbackScores`는 최초 응시 retry 0의 비교용 세부 점수 한 건만 담는다. `azureFeedback` 내부만 snake_case이고, PartResult의 null 선택 필드는 생략될 수 있다.
- 텍스트 답안 구분: `feedback.correctedAnswer`는 AI Callback 값이 아니라 Session에 배정된 원본 `Question.corrected_answer`를 조회 시 덮어써 반환한다. `feedback.recommendedAnswer`는 AI Callback의 `feedback.recommended_answer`를 저장한 회차별 추천 답안이며 응답에서는 camelCase다.
- 음성 모범답안 구분: `question.modelAnswer`에는 텍스트를 넣지 않고 `audioUrl`, `spokenWordSequence` 두 필드만 보낸다. 원본 문제가 Part 1 Question 1·2이고 해당 시험지 metadata가 있을 때만 필드를 만들며, 나머지는 `null`이 아니라 필드 자체를 생략한다. 현재 catalog는 `mock_exam_004` q1·q2만 제공한다.
- 음성 소스: `modelAnswer.audioUrl`은 소유권 확인 후 `ExamSession.mockExamId`로 만든 `{mockExamId}/part1_a{questionNumber}.wav`의 60분 Presigned GET URL이다. 사용자 녹음 `question.audioUrl`/`question.spokenWordSequence`, 문제 출제 음성 `question.questionInfo.audioUrl`과 서로 다른 데이터이며 retryCount가 바뀌어도 모범답안 소스는 같다.
- 변경 파일: 애플리케이션·테스트 코드는 수정하지 않았고 종료 기록을 위해 `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`만 갱신했다. Secret, Token과 실제 URL을 기록하지 않았고 Git commit·push·PR 생성 및 Jira 변경을 수행하지 않았다.
- 검증: `ModelAnswerResponseContractTest`, `ExamResultTest`, `ExamQuestionModelAnswerTest` 집중 테스트를 실행해 총 12개, failures/errors/skipped 0개로 성공했다. 분석 전 sandbox의 Gradle lock 권한으로 첫 실행은 task 시작 전에 중단됐고 승인된 동일 명령 재실행은 성공했다. 애플리케이션 구현 변경이 없어 `./gradlew clean test` 전체 실행은 생략했다.
- 남은 위험·다음 확인: 이 내용은 현재 로컬 미커밋 작업 트리 기준이며 실제 배포 버전은 확인하지 않았다. 프론트는 텍스트 모범답안이 필요하면 `feedback.correctedAnswer`와 회차별 `feedback.recommendedAnswer`를 구분하고, 음성 UI는 `question.modelAnswer` 존재 여부를 검사해야 한다. `mock_exam_004` 외 시험지에도 음성 모범답안이 필요하면 시험지별 실제 q1·q2 metadata를 먼저 추가해야 한다.

## 2026-08-04 — main 기준 누적 변경 코드 재리뷰

<!-- codex-turn:019fca4a-f602-7e91-a6d5-0516baf51183 -->

- 날짜·범위: `2026-08-04`, 브랜치 `chore/add-actuator-health`, HEAD·main·지정 merge base `b70d03f38afc239849086fef6549bc3af47c89f6`. 해당 기준 tracked diff와 신규 미추적 운영·리소스·테스트 파일을 함께 리뷰했으며, 이번 리뷰에 별도 Jira 이슈 키는 없다.
- 리뷰 finding: P1 1건이다. `mock_exam_004` Part 1 문항 1·2의 소유자는 시험 세션 생성 직후 아직 요청 회차의 `ExamResult`가 없어도 문항 결과 API를 직접 호출해 모범답안 Presigned URL과 전체 단어 시퀀스를 받을 수 있다. 문항 제출·채점 완료 전 발음 안내가 노출되지 않도록 완료된 응시가 있을 때만 `modelAnswer`를 조립해야 한다.
- 재현 확인: 독립 서비스 probe에서 `feedbackResultExists=false`, `modelAnswerReturned=true`를 확인했다. 원인은 `targetDoc`가 null일 수 있는 조회 직후 `buildModelAnswer`를 조건 없이 호출하는 경로다.
- 계약 확인: 위 finding 외에는 기존 공개 API URL·Method·Parameter·`BaseResponse`, retryCount, 사용자 소유권과 외부 userId 비노출, Redis Key/TTL, 기존 사용자 음성 및 S3 Object Key, AI·Callback `user_id=examId`와 Callback JSON의 별도 회귀를 확인하지 않았다.
- 테스트·정적 검증: 필수 `./gradlew clean test --no-daemon`은 sandbox의 사용자 Gradle lock 쓰기 제한으로 task 시작 전에 중단됐고, writable offline Gradle 재시도도 file-lock UDP socket 제한으로 시작하지 못했다. 현재 compiled classes를 직접 JUnit launcher로 실행한 결과 239개 중 229개가 성공했고, 10개 실패는 모두 sandbox가 `JwtSecurityIntegrationTest`의 localhost JWKS server bind를 거부한 환경 실패였다. `git diff --check`와 catalog JSON 구조·민감 패턴 검사는 성공했다.
- 변경 파일: 리뷰 대상 애플리케이션·설정·테스트 코드는 수정하지 않았다. 종료 기록 규칙에 따라 `docs/codex/WORKLOG.md`에 이 항목을 append하고 `docs/codex/CURRENT_STATE.md`를 최신 리뷰 결과로 갱신했다.
- 남은 위험·다음 확인: 모범답안 조립을 완료된 응시 이후로 제한하는 회귀 테스트를 추가한 뒤 제한 없는 환경에서 `./gradlew clean test`를 다시 실행해야 한다. 실제 AWS Profile/SSO, native Linux Docker, ECS Task Role/Bucket IAM과 Python AI의 `client_source=app` 수신은 로컬 리뷰 범위를 넘어 확인하지 않았다. Git commit·push·PR 생성 및 Jira 변경을 수행하지 않았고 Secret·Token·실제 Credential·URI·Presigned URL을 기록하지 않았다.

## 2026-08-04 — AWS SSO 후속 정책 포함 main 대비 전체 변경 최종 리뷰

<!-- codex-turn:019fca6b-cd6c-73d2-b60f-4d908dcf2b5d -->

- 날짜·범위: `2026-08-04`, 브랜치 `chore/add-actuator-health`, HEAD·main `b70d03f38afc239849086fef6549bc3af47c89f6`. tracked diff와 신규 미추적 운영·리소스·테스트 파일을 함께 최종 리뷰했으며 별도 Jira 이슈 키는 없다.
- 리뷰 결과: HIGH 1건, MEDIUM 없음, LOW 없음이다. `ExamServiceImpl.getExamQuestion`은 요청 회차의 `ExamResult`가 없어도 `mock_exam_004` Part 1 문항 1·2의 `modelAnswer`를 조건 없이 조립해, 세션 소유자가 제출·채점 전에 모범답안 음성과 전체 단어 시퀀스를 조회할 수 있다. 완료된 응시 증거가 있을 때만 조립하고 no-result 회귀 테스트를 추가해야 한다.
- 이전 SSO MEDIUM 확인: README는 macOS와 native Linux 모두 host 사전 SSO 로그인과 stdout을 숨긴 credential 검증을 안내한다. 컨테이너는 host `.aws`를 read-only로 읽고 내부 token cache 자동 갱신을 보장하지 않으며, 만료 시 host 재로그인·검증 후 기존 컨테이너를 중지하고 재시작한다. read-write mount와 world-readable 권한 안내는 없다.
- Docker·ECS 확인: Dockerfile의 기본 non-root `app`을 유지한다. 현재 linux/amd64 image에서 UID/GID `999:999`, `/app` mode `750`, JAR `644`, `/tmp` `1777`을 재확인해 native Linux의 host UID/GID·`HOME=/app`·supplementary group `999` 설명과 일치한다. ECS는 Profile mount·SSO login·static Access Key 없이 Task Role의 Container Credentials를 사용한다.
- AWS 구성 확인: BOM `2.29.52`와 `s3`·`sso`·`ssooidc`·`sts`가 유지되고 dependency insight에서 모든 AWS SDK runtime 모듈이 `2.29.52`로 해석됐다. S3Client와 S3Presigner는 공유 Default Credentials Provider를 사용하고 Health는 credential을 resolve하지 않는다.
- 계약 확인: 위 HIGH 외 기존 시험·S3·JWT·Guest·grading API, `BaseResponse`, retryCount, Redis Key/TTL, 기존 S3 Object Key, Callback JSON과 AI `user_id=examId`에 별도 회귀를 확인하지 않았다. 실제 Key·private key·자격증명 포함 URI·서명된 Presigned URL 패턴도 발견되지 않았다.
- 검증: `git diff --check`, AWS runtime dependency insight와 fresh `./gradlew clean test`가 성공했다. XML 기준 Java 245개, failures/errors/skipped 0개다.
- 변경·외부 작업: 리뷰 대상 애플리케이션·설정·테스트 파일은 수정하지 않고 종료 기록을 위해 CURRENT_STATE를 갱신하고 이 WORKLOG 항목만 append했다. Git commit·push·PR 생성과 Jira 댓글·필드·상태 변경을 수행하지 않았으며 Secret, Token, 실제 Credential·Profile 내용·URI와 Presigned URL을 기록하지 않았다.

## 2026-08-04 — 채점 전 modelAnswer 노출 HIGH finding 수정

<!-- codex-turn:019fca8c-aba7-7391-a3e1-809a4c367db5 -->

- 날짜·범위: `2026-08-04`, 브랜치 `chore/add-actuator-health`, HEAD·main `b70d03f38afc239849086fef6549bc3af47c89f6`. 별도 Jira 이슈 키 없이 최종 리뷰의 modelAnswer HIGH finding 하나만 최소 범위로 수정했다.
- 기존 노출: 소유자가 `mock_exam_004` Part 1 문항 1·2를 제출·채점하기 전에 문항 결과 API를 호출해도 `targetDoc` null 여부와 무관하게 `buildModelAnswer`가 실행되어 모범답안 Presigned URL과 전체 단어 시퀀스가 조립됐다.
- 차단 정책: 요청 retryCount를 기존 canonical 규칙으로 해석하고 matching `ExamResult`가 있을 때만 결과 조회 가능 완료 증거로 인정한다. 결과가 없으면 사용자 녹음 URL과 `buildModelAnswer`를 모두 건너뛰므로 model-answer catalog 조회, 모범답안 S3 Key 조립, Presign과 단어 시퀀스 조회가 발생하지 않는다.
- 최종 제공 조건: 기존 소유권 검증, matching canonical retry 결과, 기존 완료 증거 정책, Part 1 Question 1·2, 해당 `mockExamId` 메타데이터를 모두 만족할 때만 `modelAnswer.audioUrl`과 `spokenWordSequence`를 반환한다. `modelAnswer.text`는 추가하지 않았다.
- 테스트: 제출 전, 존재하지 않는 retryCount, 처리 중 결과 없음의 미노출·Presigner 0회·catalog 미조회 테스트 3개를 추가했다. 기존 완료 Q1·Q2, 다른 Part 필드 생략, 타 사용자 403 선차단, feedback·retryScores·retryFeedbackScores 회귀 테스트도 유지했다. 결과 없음에 따라 미사용이 된 기존 test URL stubbing만 제거했다.
- 검증: 관련 `ExamQuestionModelAnswerTest`와 `ExamOwnershipServiceTest` 44개가 성공했다. `git diff --check`와 fresh `./gradlew clean test`도 성공했고 XML 기준 Java 248개, failures/errors/skipped 0개다.
- 유지 계약: 기존 문항 결과 API URL·Method·Query와 HTTP/오류 응답, `BaseResponse`, 완료 결과의 사용자 녹음·문제 출제 음성·모범답안 음성 구분, retryCount, JWT·Guest·소유권, S3 기존 Key, Redis, grading, AI/Callback 계약을 변경하지 않았다. History, Retries, Notification 기능은 수정하지 않았다.
- 변경 파일: `ExamServiceImpl`, `ExamQuestionModelAnswerTest`, `ExamOwnershipServiceTest`, README와 Codex 상태·기록 파일이다. Git commit·push·PR 생성과 Jira 댓글·필드·상태 변경을 수행하지 않았으며 Secret, Token, 실제 Credential·URI와 Presigned URL을 기록하지 않았다.

## 2026-08-04 — modelAnswer HIGH finding 수정 좁은 재검토

<!-- codex-turn:019fca90-3a07-70c2-9456-9e919ba690c7 -->

- 날짜·범위: `2026-08-04`, 브랜치 `chore/add-actuator-health`, HEAD `b70d03f`. 별도 Jira 이슈 키 없이 `ExamServiceImpl.getExamQuestion`, 직접 관련된 modelAnswer 테스트와 Presigned URL 생성 경로만 재검토했다.
- 리뷰 결과: HIGH 없음, MEDIUM 없음이다. 요청한 `questionNumber`와 canonical `retryCount`의 결과가 없으면 `buildModelAnswer`, model-answer catalog 조회와 Presigned GET URL 생성이 모두 실행되지 않는다.
- 동작 확인: 제출 전·처리 중·존재하지 않는 retry 회차에는 `modelAnswer`가 생략되고, 완료 결과가 있는 Part 1 문항 1·2에는 기존대로 반환된다. 다른 사용자 시험은 소유권 검사에서 403으로 차단되어 모범답안 조회와 Presigner가 실행되지 않는다.
- 테스트: `ExamQuestionModelAnswerTest` 8개와 `ExamOwnershipServiceTest` 36개, 총 44개가 failures/errors/skipped 0개로 성공했다.
- 변경·외부 작업: 애플리케이션·테스트 파일은 수정하지 않았고 Stop Hook 기록을 위해 Codex 문서만 갱신했다. Git commit·push·PR 생성과 Jira 쓰기 작업을 수행하지 않았으며 Secret과 Token을 기록하지 않았다.

## 2026-08-04 — 완료 시험 이력 및 재답변 회차 조회 API 구현

<!-- codex-turn:019fcaa6-742a-73a2-8ce0-df7043707e1e -->

- 날짜·범위: `2026-08-04`, 브랜치 `feat/exam-history-retries`, HEAD `20be0c2`. 완료 모의고사 전체 이력용 `GET /api/v1/exams/history`와 특정 시험의 재답변 문항·저장 회차 목록용 `GET /api/v1/exams/{examId}/retries`를 additive로 구현했다.
- Jira: 사용자가 이슈 등록을 명시적으로 요청했지만 현재 세션에 Jira/Atlassian connector와 이슈 생성 도구가 없어 이슈를 만들지 못했다. 키를 임의 생성하거나 자격증명을 찾지 않았고 Jira 댓글·필드·상태는 변경하지 않았다.
- JWT sub·소유권: 두 API 모두 기존 JWT 모드의 Bearer 보호 범위에 속하며 현재 사용자 ID는 검증된 JWT `sub` UUID에서만 얻는다. History 요청에 userId를 받지 않고, Retries는 Session을 먼저 조회해 `ExamSession.userId`와 현재 사용자를 비교하며 기존 404 `EXAM_4004`와 403 `COMMON403`을 유지한다. local/test Legacy Guest 호환은 변경하지 않았다.
- History 완료 기준: `exam_sessions`에서 현재 사용자이고 `completedAt`이 존재하는 Session만 완료로 본다. `active=false` 단독 판정은 사용하지 않아 completedAt 없는 inactive Session은 제외하고, active null/missing Legacy 완료 Session은 포함한다. `completedAt DESC`, 동일 시각 `examId DESC`로 결정적 정렬한다.
- History 결합·응답: Session의 MockExam title, cycleNumber와 완료 시각을 제공하고 신규 `exam_summaries`의 `_id DESC` 최신 문서를 우선한다. 신규 Summary가 없을 때만 `exam_results.totalScore != null` 최신 Legacy 종합 문서를 사용한다. 모든 컬렉션을 batch 조회해 시험별 N+1을 만들지 않았고 Summary가 없는 완료 시험은 null score/level과 `summaryAvailable=false`로 계속 반환한다. 내부 userId와 mockExamId는 노출하지 않는다.
- Retries 기준 데이터: `question_grading_jobs`의 examId, questionNumber, retryCount, 기존 `PENDING`/`PROCESSING`/`COMPLETED`/`FAILED` status를 1차 기준으로 사용한다. `dispatchAttempt`는 AI 재전송 횟수이고 사용자 retryCount와 다르므로 조회 projection·응답·결합 Key에 사용하지 않았다.
- Legacy 회차 fallback: 같은 examId의 문항별 `exam_results`를 한 번에 조회해 questionNumber와 canonical retryCount로 합쳤다. null retryCount는 0, 결과만 있는 회차는 `COMPLETED`, Job과 결과가 중복되면 Job status 우선이다. 중복을 제거하고 retryCount 1 이상이 실제로 있는 문항만 포함하며, 저장된 최초 0회차는 비교용으로 포함하지만 존재하지 않는 0회차는 만들지 않는다. questions와 attempts는 오름차순이다.
- 빈 목록·정보 최소화: 완료 이력이 없으면 200과 `histories=[]`, 재답변 문항이 없으면 200과 `questions=[]`다. Retries에는 상세 score, feedback, Transcript, audio URL, S3 Key, failureReason, AI payload를 포함하지 않고 회차 선택 후 기존 문항 단건 API를 사용한다.
- MongoDB 인덱스: 기존 TMI-31 migration과 운영 정책을 확인한 뒤 별도 `create-exam-read-indexes.js`를 추가했다. 기본 dry-run, 명시적 database 선택, URI 비출력, exact ordered-key 검사와 명시적 apply를 사용하며 호환 인덱스는 중복 생성하지 않고 충돌은 fail-closed한다. 세 인덱스는 `exam_sessions {userId:1, completedAt:-1, _id:-1}`, `question_grading_jobs {examId:1, questionNumber:1, retryCount:1}`, `exam_results {examId:1, questionNumber:1, retryCount:1}`다. 실제 DB apply는 수행하지 않았다.
- 추가 테스트: History/Retries 서비스 6개, batch Repository 계약 3개, Controller·OpenAPI mapping 계약 3개, JWT 인증·sub·소유권 응답 6개, Mongo index migration 7개로 총 25개를 추가했다. `/history` 정적 경로, `/{examId}/retries`, 기존 endpoint mapping, 신규/Legacy Summary 우선순위, active/completedAt 조합, deterministic sorting, 모든 Job 상태, null retry, dedupe, no synthetic 0, dispatchAttempt 미사용과 상세 데이터 미노출을 검증했다.
- 검증 결과: 신규 Java 집중 테스트 37개가 성공했다. 저장소 필수 `./gradlew clean test`는 전체 Java 266개, failures/errors/skipped 0으로 성공했고 MongoDB 스크립트 전체 `node --test scripts/mongodb/*.test.js`는 56개, failures 0으로 성공했다. `git diff --check`도 성공했다.
- 회귀·보안: 기존 시험 생성, 문항 단건 피드백, Summary, status, question status, submit, grading retry와 `BaseResponse`, retryCount·dispatchAttempt, 멱등성, Redis Key/TTL, S3, Default Credentials, ECS Task Role, AI/Callback `user_id=examId`, Callback JSON, 순차·순환 배정, modelAnswer `audioUrl`·`spokenWordSequence`, Health 계약을 유지했다. Secret, Token, 실제 URI, AWS Credential, Presigned URL은 코드·로그·문서에 기록하지 않았다.
- 외부 작업·남은 위험: Git commit·push·PR 생성은 하지 않았다. 운영 MongoDB의 실제 explain과 인덱스 dry-run/apply, 혼합 BSON `_id` 타입 중복 Summary 정렬과 staging Bearer smoke test는 로컬 Mock 기반 검증 범위 밖이다.

## 2026-08-04 — History/Retries Jira 이슈 생성 재시도

<!-- codex-turn:019fcb06-9409-7053-955f-9f3e7dd028eb -->

- 요청·범위: 사용자가 완료 시험 History 및 재답변 회차 조회 구현에 대한 Jira 이슈 생성을 다시 요청해, 외부 Jira 쓰기 수단만 재확인했다.
- 재탐색 결과: 현재 세션의 전체 도구 목록에 Jira/Atlassian connector, 이슈 생성 도구와 lazy tool search가 없었다. 로컬 실행 환경에도 `jira`, `acli`, `atlas` CLI가 설치되어 있지 않았다.
- 처리 결과: 인증정보를 요구하지 않는 이슈 생성 경로가 없어 Jira 이슈를 생성하지 못했으며 이슈 키도 없다. 키를 임의 생성하거나 Secret·Token·환경 자격증명을 찾지 않았다.
- 변경·보안: Jira 댓글·필드·상태를 변경하지 않았고 Git commit·push·PR도 수행하지 않았다. 애플리케이션·테스트·migration 코드는 변경하지 않았으며 `docs/codex/CURRENT_STATE.md` 갱신과 이 WORKLOG append만 수행했다.
- 검증 정책: 코드 변경이 없는 connector 가용성 재확인 작업이므로 Gradle·Node 테스트는 다시 실행하지 않았다. 기존 직전 전체 검증 결과는 Java 266개와 MongoDB 스크립트 56개 모두 성공 상태다.

## 2026-08-04 — TMI-61 완료 시험 History/Retries Jira 이슈 생성

<!-- codex-turn:019fcb07-e3a0-7433-be0a-102822598fca -->

- 요청·결과: 사용자 요청에 따라 `TMI` 프로젝트에 [`TMI-61`](https://to-teacher.atlassian.net/browse/TMI-61) `[Learning Core] 완료 시험 이력 및 재답변 회차 조회 API`를 `작업` 타입으로 생성했다.
- 설명 범위: JWT `sub` 기반 사용자 식별, `ExamSession.completedAt` 완료 기준, 신규/Legacy Summary batch 결합과 신규 우선 fallback, `question_grading_jobs` 우선 및 `exam_results` Legacy fallback, 사용자 `retryCount`·Job 상태 제공, `dispatchAttempt`·상세 피드백 비노출, 소유권·기존 계약 유지와 테스트 완료 조건을 기록했다.
- Jira 검증: 생성 응답과 읽기 전용 상세 재조회로 이슈 키·제목·설명·프로젝트·유형을 확인했다. 기본 상태는 `해야 할 일`, 기본 우선순위는 `Medium`, 담당자는 미지정이고 라벨은 비어 있다.
- 변경 범위: Jira/PR 완료 댓글 초안은 등록하지 않았고 상태 전환·담당자·라벨·다른 이슈는 변경하지 않았다. 애플리케이션·테스트·migration 코드는 수정하지 않았으며 종료 기록을 위해 `docs/codex/WORKLOG.md`와 `docs/codex/CURRENT_STATE.md`만 갱신했다.
- 테스트·위험: Jira 생성과 문서 기록만 수행해 Gradle·Node 테스트는 다시 실행하지 않았다. 직전 구현에서 성공한 Java 266개와 MongoDB 스크립트 56개는 기존 결과이며 이번 turn의 재실행 결과가 아니다. 이슈는 아직 `해야 할 일` 상태이므로 완료 댓글 등록이나 상태 전환이 필요하면 별도 요청으로 처리해야 한다.
- 보안·Git: Secret과 Token을 조회하거나 기록하지 않았고 Git commit·push·PR 생성은 수행하지 않았다. 기존 공개 API·`BaseResponse`, 사용자 소유권, `retryCount`, Redis/S3 및 AI/Callback 계약은 변경하지 않았다.

## 2026-08-04 — TMI-61 History/Retries 지정 범위 코드 리뷰

<!-- codex-turn:019fcb0a-333c-7203-8268-dd6738bababa -->

- 범위: Jira `TMI-61`의 `GET /api/v1/exams/history`, `GET /api/v1/exams/{examId}/retries`에 한해 `ExamRestController`, `ExamReadService`, 신규 응답 DTO, 관련 Repository, `create-exam-read-indexes.js`와 관련 서비스·계약·보안·Node 테스트를 검토했다.
- 결과: HIGH 없음, MEDIUM 1건이다. History의 신규 Summary batch query는 `exam_summaries`에서 `examId IN (...)`과 `{examId:1, _id:-1}` 정렬을 사용하지만 인덱스 스크립트는 `exam_sessions`, `question_grading_jobs`, `exam_results`만 다룬다. 전역 collection scan과 blocking sort 위험이 있으므로 `exam_summaries` query shape용 인덱스와 검증을 추가해야 한다.
- 요구사항 확인: completedAt이 있는 현재 사용자 Session만 포함하고 `completedAt DESC`, 동일 시각 `examId DESC`로 정렬한다. MockExam·신규 Summary·Legacy Summary는 고정 개수 batch 조회하며 Summary가 없어도 null score/level과 `summaryAvailable=false`로 목록을 유지한다.
- Retries 확인: Session 소유권을 선검증해 타 사용자는 `COMMON403`이고 attempt 조회를 실행하지 않는다. 결과는 `(questionNumber, canonical retryCount)` Key로 dedupe하며 Job이 Legacy Result보다 우선하고 `dispatchAttempt`는 projection·결합·응답에 사용하지 않는다. retryCount 1 이상이 없는 문항은 제외하고 빈 결과는 200과 빈 배열이다.
- 계약 확인: 기존 `GET /{examId}/questions`와 `GET /{examId}/summary` URL·Method·Parameter·응답 DTO 및 `BaseResponse`는 변경되지 않았다. 신규 DTO에도 실제 userId, mockExamId, 상세 피드백, Transcript, S3/음성 정보가 노출되지 않는다.
- 검증: 관련 Java 6개 클래스 총 40개와 Node 인덱스 테스트 7개가 failures/errors/skipped 0개로 성공했고 `git diff --check`도 성공했다. 첫 Gradle 실행은 sandbox의 사용자 Gradle cache lock 권한으로 task 시작 전에 중단됐고 승인된 동일 명령은 성공했다. 실제 MongoDB `explain`과 인덱스 dry-run/apply는 수행하지 않았다.
- 변경·보안: 사용자 요청에 따라 애플리케이션·테스트·인덱스 스크립트는 수정하지 않았다. 저장소 필수 기록을 위해 `docs/codex/WORKLOG.md`와 `docs/codex/CURRENT_STATE.md`만 갱신했다. Secret·Token을 기록하지 않았고 Git commit·push·PR 및 Jira 댓글·필드·상태 변경은 수행하지 않았다.

## 2026-08-04 — TMI-61 History/Retries 리뷰 Stop Hook 기록 보완

<!-- codex-turn:019fcb0a-333c-7d23-b3df-374490ab245a -->

- Stop Hook 요청에 따라 현재 turn의 Jira `TMI-61` History/Retries 지정 범위 리뷰 기록을 append했다. 과거 WORKLOG 항목은 수정하거나 삭제하지 않았다.
- 리뷰 결과는 HIGH 없음, MEDIUM 1건으로 유지한다. `exam_summaries`의 `examId IN (...)`, `{examId:1, _id:-1}` History batch query를 지원하는 인덱스가 `create-exam-read-indexes.js`에 없어 데이터 증가 시 collection scan과 blocking sort 위험이 있다.
- 확인 항목 1~10은 모두 충족했고 관련 Java 40개, Node 7개와 `git diff --check`가 성공했다. 실제 MongoDB `explain`과 인덱스 dry-run/apply는 수행하지 않았다.
- 애플리케이션·테스트·인덱스 스크립트, 공개 API 계약과 Jira 댓글·필드·상태는 변경하지 않았다. Stop Hook 기록을 위해 `docs/codex/WORKLOG.md`와 `docs/codex/CURRENT_STATE.md`만 갱신했으며 Secret·Token을 기록하지 않고 Git commit·push·PR도 수행하지 않았다.

## 2026-08-04 — TMI-61 Summary batch 인덱스 MEDIUM 수정

<!-- codex-turn:019fcb1b-c5ae-7aa2-a073-4048c528fd03 -->

- 범위·결과: Jira `TMI-61` History/Retries targeted review에서 확인한 MEDIUM 하나만 수정했다. `ExamSummaryRepository.findHistoryCandidatesByExamIdIn`의 여러 `examId` batch 조회와 `{examId:1, _id:-1}` 정렬에 맞춰 `exam_summaries` 인덱스 `idx_exam_summaries_exam_id_latest`, Key `{examId:1, _id:-1}`를 선언형 read-index 계획에 추가했다.
- 스크립트 정책: 기본 dry-run, `EXAM_READ_INDEXES_APPLY=true`의 명시적 apply, 모든 충돌의 쓰기 전 검사, idempotent 실행, apply 후 인덱스 재검증과 애플리케이션 자동 적용 금지를 유지했다. 실제 문서 읽기·수정 없이 인덱스 metadata만 검사하고 누락 인덱스만 생성한다.
- 호환·충돌: 같은 이름·같은 Key, 다른 이름·같은 Key는 재생성하지 않는다. 다른 이름의 더 긴 인덱스는 `{examId:1, _id:-1}`가 leading ordered prefix이고 기존 incompatible option이 없을 때만 호환으로 인정한다. 확정 이름의 다른 정의는 쓰기 전 오류이며 `{examId:-1, _id:-1}`, `{_id:-1, examId:1}`, `{examId:1}`은 호환으로 취급하지 않는다.
- Node 테스트: Summary 인덱스 계획·이름·정확한 Key, dry-run createIndex 0회, apply에서 누락 Summary 인덱스만 생성, 동일 정의 idempotency, 다른 이름 동일 Key 중복 방지, 긴 prefix 호환, 동일 이름 다른 Key 충돌 무쓰기, 역방향·순서 불일치·짧은 Key 배제와 기존 세 컬렉션 계획 회귀를 추가했다. 실제 MongoDB에는 연결하지 않았다.
- 문서: `scripts/mongodb/README.md`에 Summary 인덱스 용도와 Staging/운영 apply 후 실행할 `explain("executionStats")` 예시를 추가했다. IXSCAN과 선택 인덱스, COLLSCAN·blocking SORT 부재, `totalDocsExamined`를 확인하도록 했으며 실제 apply나 explain 성공으로 기록하지 않았다.
- 검증: `git diff --check`, `node --test scripts/mongodb/*.test.js` 전체 63개, `./gradlew test --tests '*ExamRead*' --tests '*JwtSecurityIntegrationTest*' --tests '*LegacySecurityIntegrationTest*'` Java 37개가 failures/errors/skipped 0개로 성공했다. Java 운영 코드를 변경하지 않아 전체 `clean test`는 PR 직전 통합 검증으로 남겼다.
- 계약·외부 작업: History/Retries URL·Method·완료 판정·정렬·Summary fallback, Repository 메서드, DTO, Bearer 인증, 소유권, 문항 단건·Summary API, retryCount, dispatchAttempt, modelAnswer와 MongoDB 문서 구조는 변경하지 않았다. 실제 DB apply·explain, Git commit·push·PR 및 Jira 댓글·필드·상태 변경은 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-08-04 — TMI-61 Summary 인덱스 좁은 재검토

<!-- codex-turn:019fcb26-5bb4-7882-912b-5a8f9acc0f61 -->

- 범위: Jira `TMI-61`의 `ExamSummaryRepository`, `scripts/mongodb/create-exam-read-indexes.js`, `scripts/mongodb/create-exam-read-indexes.test.js`만 검토했다. 결과는 HIGH 없음, MEDIUM 1건이다.
- MEDIUM finding: `hasIncompatibleOptions`는 unique, sparse, partial, collation만 거부하고 `hidden:true`를 거부하지 않는다. 따라서 정확한 `{examId:1, _id:-1}` 또는 호환되는 긴 prefix 인덱스가 hidden이어도 compatible로 분류되어 생성과 최종 검증을 건너뛴다. MongoDB Query Planner는 hidden 인덱스를 사용하지 않으므로 스크립트 성공 뒤에도 History query가 COLLSCAN과 blocking SORT에 남을 수 있다.
- 확인 결과: `idx_exam_summaries_exam_id_latest`, Key `{examId:1, _id:-1}`는 Repository의 `examId IN (...)`, `{examId:1, _id:-1}` 정렬과 일치한다. 기본 dry-run, 명시 apply, 동일/다른 이름 idempotency, 확정 이름의 다른 Key 충돌 시 apply 무쓰기와 기존 `exam_sessions`, `question_grading_jobs`, `exam_results` 계획은 유지된다.
- 검증: `node --test scripts/mongodb/create-exam-read-indexes.test.js` 14개와 `git diff --check`가 성공했다. 별도 Node probe는 `{name:"hidden_summary", key:{examId:1,_id:-1}, hidden:true}`가 오류 없이 compatible로 분류되고 Summary 생성 계획에서 제외되는 것을 재현했다. 실제 MongoDB 연결·apply·explain은 수행하지 않았다.
- 변경·외부 작업: 리뷰 대상 Repository·스크립트·테스트는 수정하지 않고 종료 규칙에 따라 Codex 기록 문서만 갱신했다. Git commit·push·PR 및 Jira 댓글·필드·상태 변경은 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-08-04 — TMI-61 hidden 인덱스 MEDIUM 수정

<!-- codex-turn:019fcb29-8c09-7d42-929b-a1a59179cfff -->

- 범위·결과: Jira `TMI-61`의 read-index 스크립트에서 hidden 인덱스를 usable compatible index로 오판하던 targeted review MEDIUM 하나만 수정했다. `hasIncompatibleOptions`에 `index.hidden === true` 판정을 추가했다.
- 다른 이름 hidden 정책: 정확한 `{examId:1, _id:-1}` 또는 `{examId:1, _id:-1, ...}` compatible prefix가 `hidden:true`이면 호환·생성 생략 근거로 사용하지 않고 visible 목표 인덱스를 생성 계획에 남긴다. `hidden:false`와 hidden 필드 누락은 기존 visible 인덱스 정책을 유지한다.
- 동일 이름 hidden 정책: `idx_exam_summaries_exam_id_latest`와 같은 이름의 hidden 인덱스는 컬렉션 이름, 인덱스 이름, expected/actual Key, `hidden=true`, 자동 drop/unhide 미수행을 포함한 충돌 메시지로 apply 전에 실패한다. 하나라도 오류가 있으면 `applyIndexPlan`은 createIndex를 수행하지 않는다.
- 무변경 보장: 스크립트에 `dropIndex`나 `collMod` 호출을 추가하지 않았고 기존 인덱스를 자동 수정·unhide·삭제하지 않는다. 기본 dry-run, 명시 apply, visible exact/prefix idempotency, unique/sparse/partial/collation 비호환 정책과 기존 `exam_sessions`, `exam_summaries`, `question_grading_jobs`, `exam_results` 계획은 유지했다.
- Node 테스트: exact hidden과 prefix hidden 배제·visible 생성 계획, same-name hidden 상세 충돌, createIndex/dropIndex/collMod 0회, `hidden:false`와 hidden 필드 누락 호환, 다른 이름 visible 동일 Key 중복 방지, 기존 네 옵션 충돌과 네 컬렉션 계획 회귀를 추가·유지했다. read-index 19개와 전체 MongoDB 스크립트 68개가 failures 0으로 성공했다.
- 검증·외부 작업: `git diff --check`가 성공했다. Java 운영 코드·Repository·API 계약을 변경하지 않아 Java 테스트는 재실행하지 않았다. 실제 MongoDB 연결·apply·explain, Git commit·push·PR 및 Jira 댓글·필드·상태 변경은 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-08-04 — TMI-61 hidden 인덱스 수정 후 targeted review

<!-- codex-turn:019fcb2e-34e7-7812-9337-203286aca41a -->

- 범위: `scripts/mongodb/create-exam-read-indexes.js`와 직접 관련된 Node 테스트에서 hidden 호환 판정, 동일 이름 hidden 충돌의 무쓰기 보장, visible 인덱스 idempotency만 재검토했다.
- 결과: HIGH·MEDIUM finding은 없다. `hidden:true`는 `hasIncompatibleOptions`에서 거부되며 다른 이름의 exact/prefix hidden 인덱스는 생성 생략 근거가 되지 않는다.
- 동일 이름 hidden 인덱스는 상세 충돌을 계획에 기록하고 `applyIndexPlan`이 오류 존재 시 즉시 빈 결과를 반환한다. `createIndex`, `dropIndex`, `collMod` 호출 없이 실패하며 자동 drop/unhide도 수행하지 않는다.
- `hidden:false` 또는 hidden 필드가 없는 동일 Key 인덱스와 다른 이름의 visible exact/prefix 인덱스는 기존대로 호환되어 중복 생성되지 않는다.
- 검증: `node --test scripts/mongodb/create-exam-read-indexes.test.js` 19개가 failures/errors/skipped 0으로 성공했다. 실제 MongoDB 연결·apply·explain은 수행하지 않았다.
- 변경·외부 작업: 리뷰 대상 코드와 테스트는 수정하지 않았고 필수 Codex 기록 문서만 갱신했다. Git commit·push·PR 및 Jira `TMI-61` 댓글·필드·상태 변경은 수행하지 않았으며 Secret·Token을 기록하지 않았다.

## 2026-08-04 — TMI-61 존재하지 않는 컬렉션 Dry-run 보정

<!-- codex-turn:019fcbab-e395-73b1-9913-6a00da7755d0 -->

- 범위·원인: Jira `TMI-61` read-index dry-run에서 아직 생성되지 않은 `exam_summaries`에 대한 비동기 `getIndexes()` 실패가 동기 `try/catch` 밖에서 발생해 `NamespaceNotFound`로 중단되던 문제만 최소 수정했다.
- 처리 방식: 인덱스 조회 helper와 호출부를 `async/await`로 변경하고 MongoDB 오류의 `code === 26` 또는 `codeName === "NamespaceNotFound"`인 경우에만 기존 인덱스를 빈 배열로 처리한다. 인증 실패, 네트워크 오류, 권한·명령 오류와 알 수 없는 MongoDB 오류는 원래 오류 객체를 그대로 전파한다.
- dry-run/apply: 컬렉션이 없으면 해당 인덱스를 생성 예정 계획에 포함한다. dry-run은 `createCollection`, `createIndex`, `dropIndex`, `collMod`를 호출하지 않으며, apply는 별도 빈 문서나 명시적 컬렉션 생성 없이 기존 `createIndex` 경로를 사용한다. 실제 MongoDB apply는 수행하지 않았다.
- 회귀 정책: visible 인덱스 idempotency, hidden 인덱스 비호환, 동일 이름·다른 정의 충돌의 전체 쓰기 전 차단과 자동 drop/unhide 금지를 유지했다.
- 테스트·검증: NamespaceNotFound 두 표현, 누락 Summary 계획, dry-run 무쓰기, apply createIndex, 인증·네트워크·알 수 없는 오류 전파에 대한 Node 테스트 8개를 추가했다. `node --test scripts/mongodb/*.test.js` 전체 76개와 `git diff --check`가 성공했다.
- 외부 작업·보안: Java·Repository·공개 API는 변경하지 않았고 실제 DB 연결·apply·explain, Git commit·push·PR 및 Jira 댓글·필드·상태 변경을 수행하지 않았다. Secret과 Token을 조회하거나 기록하지 않았다.

## 2026-08-04 — Learning Core AWS Secrets Manager 대상 설정 분류

<!-- codex-turn:019fcbe0-bce2-7290-9c85-1d207f951d44 -->

- 범위: 현재 체크아웃된 Learning Core의 tracked `.env.example`, Spring 설정, 인증·S3 구성과 운영 문서를 읽어 AWS Secrets Manager 대상 환경변수 이름을 분류했다. 실제 로컬·운영 환경변수, Secret 값, Token, 자격증명 파일은 조회하지 않았다. 별도 Jira 이슈 키는 없다.
- Secrets Manager 대상: 자격증명이 포함되는 `MONGODB_URI`는 필수 대상이다. 서버 오류 수집 권한 성격의 `SENTRY_DSN`은 보호 저장을 권장한다. 현재 checkout에 선언되지 않은 Expo Push 또는 Redis 인증이 도입되는 경우에만 각각의 Provider Access Token과 Redis AUTH 값도 추가 대상이다.
- Secrets Manager 비대상: `MONGODB_DATABASE`, Redis host/port, AWS Region·S3 Bucket, Identity issuer·JWKS URL·audience, profile/auth mode, prefix·timeout·thread·port·sampling 값은 일반 구성으로 분류했다. Learning Core는 JWT Private Key나 공유 JWT Secret을 보관하지 않는다.
- AWS 인증: 장기 AWS Access Key/Secret Key를 Secrets Manager에서 애플리케이션 환경변수로 주입하지 않고 ECS Task Role의 임시 자격증명을 사용하는 현재 정책을 유지해야 한다. ECS agent의 Secret 주입 권한은 Task Execution Role, 런타임 S3 권한은 Task Role로 분리한다.
- 결과·외부 작업: 분석 및 문서 기록만 수행해 애플리케이션·설정·테스트 코드는 변경하지 않았고 테스트를 재실행하지 않았다. AWS Secrets Manager 생성·갱신·조회, Git commit·push·PR 및 Jira 쓰기 작업은 수행하지 않았다.

## 2026-08-05 — AI 서버 주소 환경변수 여부 확인

<!-- codex-turn:019fd0b9-296a-7a02-b364-9927ad30d47f -->

- 범위·결과: 현재 `main`의 AI 채점 전송 구현과 Spring 설정·환경변수 예시를 읽기 전용으로 확인했다. 별도 Jira 이슈 키는 없다.
- AI 주소: 문항·Summary 전송에 사용하는 AI endpoint는 `GradingDispatchService`의 정적 상수로 고정되어 있으며 환경변수나 Spring property로 바인딩되지 않는다. `.env.example`에도 AI 주소 환경변수는 없다.
- 환경변수 적용 범위: AI 연동 설정 중 환경변수로 처리되는 것은 현재 연결 timeout과 읽기 timeout이며, endpoint 자체는 환경별로 교체할 수 없는 상태다.
- 변경·검증: 사용자 요청은 현황 확인이므로 애플리케이션·설정·테스트 코드는 수정하지 않았고 테스트를 재실행하지 않았다. 필수 Codex 기록만 갱신했으며 실제 Secret·Token·실행 환경값은 조회하거나 기록하지 않았다. Git commit·push·PR 및 Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-05 — AI 서버 base URL 환경변수화

- 범위·결과: 고정 AI endpoint를 `AI_SERVER_URL` 환경변수 기반의 `app.grading.ai-server-url` 설정으로 전환했다. 별도 Jira 이슈 키는 없다.
- 설정: 환경변수는 AI 서버 base URL을 받으며 기본값과 `.env.example` 예시는 `http://tosunsaeng-ai:8000`이다. `GradingProperties`는 이를 `URI`로 바인딩하고 absolute HTTP(S), host 존재, user-info/query/fragment 부재를 검증한다.
- 계약 유지: `GradingDispatchService`는 base URL의 trailing slash 유무와 관계없이 기존 `/evaluations` 경로를 정확히 한 번 결합한다. 문항 multipart와 Summary JSON, `user_id=examId`, `mock_exam_id`, `Idempotency-Key`와 `client_source` 계약은 변경하지 않았다.
- 문서·테스트: `.env.example`과 README에 base URL 계약을 기록하고, 설정된 다른 host 사용·문항/Summary endpoint·trailing slash·URL 검증 테스트를 추가·보완했다. 관련 집중 테스트와 `./gradlew clean test` 전체 Java 267개가 failures/errors/skipped 0으로 성공했으며 `git diff --check`도 성공했다.
- 외부 작업: 실제 AI 서버·S3·MongoDB·Redis를 호출하지 않았고 실제 Secret·Token을 조회하거나 기록하지 않았다. Git commit·push·PR 및 Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-05 — AI 서버 URL 환경변수화 Stop Hook 기록 보완

<!-- codex-turn:019fd0ba-79ae-7472-af1a-57b2481c1a74 -->

- 현재 turn에서 고정 AI endpoint를 `AI_SERVER_URL` 기반 base URL 설정으로 전환하고 기존 `/evaluations` 경로, AI 요청 body·header 계약을 유지한 작업 기록을 보완했다. 별도 Jira 이슈 키는 없다.
- `.env.example`의 base URL 예시, URI 검증, trailing slash 처리와 관련 테스트를 포함하며 전체 Java 267개와 `git diff --check` 성공 상태는 동일하다.
- 실제 외부 시스템 호출, Git commit·push·PR 및 Jira 쓰기는 수행하지 않았고 Secret과 Token을 조회하거나 기록하지 않았다.

## 2026-08-06 — 시험 세션 생성 오디오 URL 흐름 확인

<!-- codex-turn:019fd4e4-5653-7eb1-8312-2a309cc5489d -->

- 범위·결과: `POST /api/v1/exams`의 Controller, `ExamServiceImpl`과 응답 DTO 변환 흐름을 읽기 전용으로 확인했다. 별도 Jira 이슈 키는 없다.
- 세션 생성 응답의 각 문제 `audioUrl`은 `questions/{mockExamId}/q_{questionNumber}.wav` 객체에 대한 60분 S3 Presigned GET URL이다. Part 3은 `questions/{mockExamId}/part3_intro.wav`의 60분 `guideAudioUrl`도 함께 제공한다.
- 사용자 녹음 업로드 URL은 세션 생성 응답에 포함되지 않으며 기존 별도 upload-url API가 `temp/{examId}/q_{questionNumber}_r{retryCount}.wav` Key의 Presigned PUT URL을 발급한다.
- 애플리케이션·설정·테스트 코드는 변경하지 않았고 테스트는 재실행하지 않았다. 실제 S3, Secret, Credential, Token 또는 Presigned URL에 접근하거나 발급하지 않았으며 Git·Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-06 — 시험 세션 발행 로그 확인

<!-- codex-turn:019fd4f6-8452-7aa3-bb74-047112faae97 -->

- 범위·결과: `POST /api/v1/exams`의 `createExamSession`과 `ExamSessionManager.findOrCreate` 분기 및 현재 로깅 설정을 읽기 전용으로 확인했다. 별도 Jira 이슈 키는 없다.
- 신규 `ExamSession`이 실제 insert된 경우에만 INFO 레벨로 `정규 모의고사 세션 생성 완료`와 `examId`, `mockExamId`가 출력된다. 기본 Spring INFO 로깅에서는 보이는 로그다.
- 사용자의 진행 중 활성 세션을 재사용하거나 동시 생성 충돌 뒤 기존 세션을 선택한 경우 `assignment.created()`가 false이므로 현재 세션 발행 INFO 로그는 출력되지 않는다. Controller 요청 자체를 기록하는 별도 access log도 애플리케이션 설정에서 확인되지 않았다.
- 애플리케이션·설정·테스트 코드는 변경하지 않았고 테스트는 재실행하지 않았다. Secret·Token·Credential을 조회하거나 기록하지 않았으며 Git·Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-06 — ECS 로그 미수집 원인 진단

<!-- codex-turn:019fd4fe-3d95-7150-92d7-7d685f36d9bd -->

- 범위·결과: Learning Core의 Docker 실행 방식, Spring 로깅 설정, ECS 관련 저장소 문서를 읽기 전용으로 확인했다. 별도 Jira 이슈 키는 없다.
- 애플리케이션은 커스텀 파일 appender 없이 `java -jar`로 실행되어 Spring 기본 INFO 로그를 컨테이너 stdout/stderr로 출력한다. 따라서 Spring 기동 로그까지 CloudWatch에 전혀 없다면 세션 생성 조건부 로그보다 ECS Container Definition의 `awslogs` 설정, Log Group/Region/stream 선택 또는 Task Execution Role 권한을 우선 확인해야 한다.
- 저장소에는 실제 ECS Task Definition이나 배포 IaC가 없어 `logConfiguration`을 정적으로 확인할 수 없다. 로컬 AWS CLI로 읽기 전용 ECS 조회를 시도했으나 자격 증명이 구성되지 않아 실제 Task revision, Log Group 및 CloudWatch 상태는 확인하지 못했다.
- 특정 세션 요청 로그만 없는 경우에는 진행 중 세션 재사용 시 `assignment.created() == false`여서 생성 INFO 로그가 생략되고, 별도 HTTP access log도 활성화되어 있지 않은 기존 동작이 설명이 된다.
- 애플리케이션·인프라·테스트 코드는 변경하지 않았고 테스트는 재실행하지 않았다. Secret·Token·Credential 값을 조회하거나 기록하지 않았으며 Git·Jira 및 AWS 쓰기 작업은 수행하지 않았다.

## 2026-08-06 — S3 업로드 이후 채점 미진행 진단

<!-- codex-turn:019fd55a-a81f-7db1-9722-2f12cb5a26d3 -->

- 범위·결과: Presigned PUT 발급, 문항 submit, `QuestionGradingJob`, S3 재다운로드, AI `/evaluations` 전송, Callback과 예외 처리 흐름을 읽기 전용으로 확인했다. 별도 Jira 이슈 키는 없다.
- 업로드는 앱이 S3로 직접 수행하므로 Learning Core는 업로드 완료를 자동 감지하지 않는다. 앱이 동일 `examId`, `questionNumber`, `retryCount`로 별도 submit API를 호출해야 Job 생성과 채점 전송이 시작된다.
- submit 이후 Learning Core는 `temp/{examId}/q_{questionNumber}_r{retryCount}.wav`를 Presigned GET으로 다시 읽고 AI 서버에 multipart POST한다. 따라서 Put 성공은 ECS Task Role의 `s3:GetObject`, 동일 retry Key 존재 또는 AI endpoint 도달 가능성을 보장하지 않는다.
- 현재 ECS에 `AI_SERVER_URL`이 없으면 로컬 Docker용 기본값 `http://tosunsaeng-ai:8000`을 사용한다. ECS Service Connect/Cloud Map 또는 같은 Task 내 올바른 통신 경로가 없으면 DNS/연결 실패가 가능하다. Task Role에 Put만 있고 Get이 없을 때도 동일하게 dispatch가 실패한다.
- 초기 dispatch의 RuntimeException은 Job을 `FAILED`와 `QUESTION_DISPATCH_FAILED`로 전이한 뒤 `EXAM_4001`로 변환되지만 원래 S3/HTTP 예외를 기록하지 않는다. submit 진입·S3 fetch·AI 전송 성공 로그도 없어 CloudWatch 로그만으로 실패 구간을 구분할 수 없는 관측성 공백이 있다.
- 진단 기준: submit 미호출은 앱 흐름, 401/403은 인증·소유권, 500 `EXAM_4001`은 S3 GET 또는 AI 전송, 200 `PROCESSING` 후 정체는 AI 처리·Callback 도달을 우선 확인한다. 기존 FAILED Job은 같은 submit 재호출로 자동 재전송되지 않아 원인 수정 후 시험 단위 grading retry 경로가 필요하다.
- 애플리케이션·인프라·테스트 코드는 변경하지 않았고 테스트는 재실행하지 않았다. 실제 AWS·MongoDB·AI 서버에 접근하지 않았고 Secret·Token·Credential을 조회하거나 기록하지 않았으며 Git·Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-06 — AI 문항 채점 전송 로그 확인

<!-- codex-turn:019fd581-d9e2-74e0-9c50-43e5192f4549 -->

- 범위·결과: 문항 submit에서 `GradingDispatchService.dispatchQuestion`을 거쳐 AI `/evaluations`로 전송하는 경로의 로깅 여부를 재확인했다. 별도 Jira 이슈 키는 없다.
- Learning Core는 현재 submit 진입, S3 음성 다운로드 시작·완료, AI 요청 직전, AI 응답 성공을 로그로 남기지 않는다. `GradingDispatchService` 자체에도 logger가 없다.
- AI 전송 RuntimeException은 `ExamGradingService`가 Job을 `FAILED`와 `QUESTION_DISPATCH_FAILED`로 전이하고 API `EXAM_4001`로 변환하지만 원본 예외 유형·대상 endpoint·실패 단계를 로그로 남기지 않는다. stale 실패 update가 0건일 때만 DEBUG 로그가 있다.
- AI가 결과를 반환해 `/api/v1/exams/callback/feedback`에 도달하면 Controller의 Callback 수신 INFO 로그가 출력된다. 따라서 outbound 전송 로그와 Callback 수신 로그는 현재 비대칭이다.
- 애플리케이션·테스트 코드는 변경하지 않았고 테스트는 재실행하지 않았다. 실제 AI·AWS·MongoDB에 접근하지 않았으며 Secret·Token·Credential을 조회하거나 기록하지 않았고 Git·Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-06 — FAILED Question Job 재요청 동작 확인

<!-- codex-turn:019fd587-62a2-7933-ab3d-60d02b48fa6a -->

- 범위·결과: 동일 문항 submit 재호출과 시험 단위 grading retry가 `FAILED` Question Job을 처리하는 방식을 읽기 전용으로 확인했다. 별도 Jira 이슈 키는 없다.
- 같은 `examId`, `questionNumber`, canonical `retryCount`의 submit은 동일한 결정적 Job ID를 사용한다. 기존 Job 때문에 insert가 Duplicate Key가 되면 상태만 조회해 즉시 반환하므로 기존 상태가 `FAILED`이면 AI 서버를 다시 호출하지 않고 HTTP 200의 `result.status=FAILED`를 반환한다.
- 최초 AI dispatch가 실제 실패한 요청은 Job을 `FAILED`로 전이한 뒤 해당 요청에는 500 `EXAM_4001`을 반환한다. 그 뒤 같은 submit을 다시 호출할 때 위 중복 경로로 들어가 200/FAILED가 되는 흐름이다.
- `POST /api/v1/exams/{examId}/grading/retry`는 최초 응시 `retryCount=0`의 FAILED Job을 즉시 retry 대상으로 판정하고 max dispatch attempts 미만이면 PROCESSING으로 다시 claim한 뒤 AI로 전송한다. 기본 최대 전송 시도는 3회다.
- 사용자 재답변인 `retryCount>0` Job은 시험 단위 grading retry의 복구 대상이 아니다. 다른 retryCount로 업로드·submit하면 새 Job이지만 이는 실패한 동일 Job의 재전송이 아니라 새로운 사용자 답변 회차다.
- 애플리케이션·테스트 코드는 변경하지 않았고 테스트는 재실행하지 않았다. 실제 AI·AWS·MongoDB에 접근하지 않았으며 Secret·Token·Credential을 조회하거나 기록하지 않았고 Git·Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-06 — 문항 submit 및 AI 전달값 확인

<!-- codex-turn:019fd5a2-052f-7cf2-bbf6-a78664e476bb -->

- 범위·결과: 앱 submit API 계약과 Learning Core가 AI `/evaluations`에 조립하는 multipart 필드·헤더를 읽기 전용으로 확인했다. 별도 Jira 이슈 키는 없다.
- 앱은 `POST /api/v1/exams/{examId}/questions/{questionNumber}/submit?retryCount={retryCount}`를 Bearer 인증과 함께 호출하며 Request Body는 없다. `retryCount`를 생략하면 0이다. userId, S3 URL, fileKey와 음성 본문은 앱 submit 요청에 포함하지 않는다.
- Learning Core는 Session 소유권을 확인한 뒤 `temp/{examId}/q_{questionNumber}_r{retryCount}.wav`를 S3에서 읽고 AI multipart에 `user_id=examId`, Session의 `mock_exam_id`, 문항 번호에서 계산한 `part_number`, `question_number`, canonical `retry_count`, `client_source=app`, `audio_file`을 전달한다. 실제 사용자 ID는 AI에 보내지 않는다.
- AI 요청은 `${AI_SERVER_URL}/evaluations`, `Content-Type: multipart/form-data`이며 `Idempotency-Key`는 `question:{examId}:{questionNumber}:{retryCount}` 형식의 결정적 Job ID다. multipart 파일명은 현재 `q_{questionNumber}_r{retryCount}.webm`이다.
- 애플리케이션·테스트 코드는 변경하지 않았고 테스트는 재실행하지 않았다. 실제 AI·AWS·MongoDB에 접근하지 않았으며 Secret·Token·Credential을 조회하거나 기록하지 않았고 Git·Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-06 — AI 요청 미인식 wire contract 진단

<!-- codex-turn:019fd5a6-0adf-70b1-9b01-04b0b0adeda6 -->

- 범위·결과: Learning Core의 AI endpoint 조립과 multipart 계약을 테스트 및 로컬의 기존 웹 POC 백엔드 구현과 읽기 전용으로 대조했다. Python 채점 서버 소스는 로컬에 없어 실제 Form 선언과 처리 분기는 확인하지 못했다. 별도 Jira 이슈 키는 없다.
- 현재 `AI_SERVER_URL`은 base URL 계약이고 Learning Core가 `/evaluations`를 추가한다. ECS 값에 이미 `/evaluations`가 포함되면 실제 target이 `/evaluations/evaluations`가 될 수 있으며 현재 설정 검증은 path 포함을 거부하지 않는다.
- 기존 웹 채점 요청과 비교한 앱 요청의 주요 차이는 `client_source=app` 추가, 고정 시험 ID 대신 Session의 실제 `mock_exam_id`, 결정적 `Idempotency-Key`, 환경변수 기반 endpoint다. AI가 웹 요청은 처리하고 앱 요청만 인식하지 못하면 이 네 차이를 우선 확인해야 한다.
- multipart의 기존 핵심 필드 `user_id=examId`, part/question/retry와 `audio_file`은 유지된다. S3 Key는 `.wav`지만 multipart 파일명은 기존과 같이 `.webm`이며, AI가 확장자나 part Content-Type을 엄격히 검증한다면 실제 음성 형식과 함께 확인이 필요하다.
- submit이 `PROCESSING`이면 RestTemplate 관점에서 AI endpoint가 2xx를 반환한 것이므로 AI 내부 source/mockExam/idempotency 처리 또는 Callback을 확인한다. `EXAM_4001` 또는 FAILED이면 AI 4xx/5xx, 잘못된 path, multipart validation 또는 전송 실패 가능성이 있으며 원본 응답은 현재 Learning Core 로그에 남지 않는다.
- 애플리케이션·테스트 코드는 변경하지 않았고 테스트는 재실행하지 않았다. 실제 AI·AWS·MongoDB·네트워크에 접근하지 않았으며 Secret·Token·Credential을 조회하거나 기록하지 않았고 Git·Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-06 — 문항 채점 submit·AI outbound 진단 로그 추가

<!-- codex-turn:019fd5b2-9e4e-7473-87f3-6c575c4e81a3 -->

- 범위·결과: 별도 Jira 이슈 키 없이 `ExamGradingService.submitQuestion`과 재전송 claim, `GradingDispatchService.dispatchQuestion`에 문항 채점 진단 로그를 추가했다.
- INFO 로그: submit 시작의 job/exam/문항/retry, 신규 Job 생성, 기존 Job의 status·dispatchAttempt 반환, AI 호출 직전의 job/fileKey/attempt, dispatch 성공, 실제 multipart POST 시작의 안전한 AI URI·fileKey·audioSize와 완료 HTTP status를 기록한다. grading retry 재전송도 호출 전·성공·실패 로그를 동일하게 남긴다.
- ERROR 로그: dispatch 실패의 jobId, 예외 타입과 정제 메시지를 기록한다. 원본 Throwable stacktrace는 Presigned URL과 서명 쿼리를 포함할 수 있어 직접 출력하지 않고 HTTP(S) URI와 authorization/token/secret/signature/credential 형태 값을 치환하며 단일 행 500자로 제한했다.
- 계약 유지: Question Job 생성·중복·FAILED·retry·dispatch 상태 전이, 최대 시도, S3 Key, AI multipart 필드·`Idempotency-Key`, endpoint와 공개 API·DTO·응답 계약은 변경하지 않았다.
- 테스트: 신규·중복 Job 로그, 호출 직전·성공·실패 로그, Presigned URL·서명 미노출, 실제 AI multipart 시작·완료 URI/크기/status 로그를 `CapturedOutput`으로 검증했다. 집중 테스트와 `./gradlew clean test`가 성공했고 XML 기준 Java 268개, failures/errors/skipped 0개다. `git diff --check`도 성공했다.
- 외부 작업: 실제 AI·AWS·MongoDB·ECS를 호출하지 않았고 실제 Secret·Token·Credential·Presigned URL을 기록하지 않았다. Git commit·push·PR 및 Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-06 — 문제 전용 조회 API 확인

<!-- codex-turn:019fd5b9-5717-75e0-91b6-595628d6d7a5 -->

- 범위·결과: 특정 시험 문항의 문제 정보만 반환하는 Controller, 서비스와 `QuestionDTO` 계약을 읽기 전용으로 확인했다. 별도 Jira 이슈 키는 없다.
- 전용 API는 `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`다. JWT 모드에서는 Bearer 인증과 `ExamSession.userId` 소유권 검증을 수행하며 Session의 실제 `mockExamId` 문제를 조회한다.
- 응답은 part, questionNumber, text, referenceText, partIntroText, image/table, 준비·답변 시간과 60분 출제 음성 Presigned GET URL을 포함한다. Part 3은 guideAudioUrl도 제공하며 채점 상태·점수·피드백·retryCount·사용자 녹음은 포함하지 않는다.
- 세션 생성 `POST /api/v1/exams`도 전체 문제 목록을 반환하지만, 특정 문항만 다시 조회할 때는 prompt API가 해당 목적의 계약이다. 기존 문항 결과 `GET /api/v1/exams/{examId}/questions`와는 별도다.
- 애플리케이션·테스트 코드는 변경하지 않았고 테스트를 재실행하지 않았다. 실제 S3·Secret·Token·Credential에 접근하지 않았으며 Git·Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-06 — 항상 신규 시험 세션 생성 및 ABANDONED 생명주기

<!-- codex-turn:019fd5df-0ca6-7372-bd99-1ef61669ffe2 -->

- 범위·결과: 별도 Jira 이슈 키 없이 `POST /api/v1/exams`가 진행 중 Session을 재사용하지 않고 매 요청마다 새 `examId`의 `IN_PROGRESS` Session을 생성하도록 변경했다. 같은 사용자의 기존 `IN_PROGRESS` Session은 데이터·Job·결과·S3 객체를 복사하거나 삭제하지 않고 모두 `ABANDONED`, `active=false`로 조건부 전이한다.
- 상태·호환성: 내부 `ExamSessionStatus`에 `IN_PROGRESS`, `COMPLETED`, `ABANDONED`를 추가했다. 신규·완료 업데이트는 status와 기존 active/completedAt을 함께 유지하고, status가 없는 기존 문서는 completedAt과 active를 이용한 legacy 해석 및 완료 증거 backfill을 계속 지원한다. 외부 DTO에는 status나 userId를 추가하지 않았다.
- 동시성: 기존 운영 필수 partial unique 인덱스 `uniq_exam_sessions_active_user`의 `{userId:1}`, partial `{active:true}`를 동시 생성 직렬화 경계로 유지했다. 기존 Session을 조건부 ABANDON한 뒤 insert하며, 동시 insert Duplicate Key가 발생하면 새로 활성화된 Session까지 다시 조건부 종료하고 새 ID 생성·insert를 재시도한다. 동시 시작 테스트에서 각 요청은 서로 다른 ID를 만들고 최종 `IN_PROGRESS` Session은 한 개만 남음을 검증했다.
- Callback·재시도: Feedback/Summary, SpeechAce, Azure Callback은 Session을 먼저 조회하고 `ABANDONED`이면 결과 저장, Question/Summary Job 완료, Session 완료와 Summary trigger를 수행하지 않는 멱등 no-op으로 처리하며 exam/question/retry/job 식별 로그를 남긴다. Summary 비동기 dispatch도 시작 전과 claim 후 Session을 다시 검사한다. 시험 단위 `grading/retry`는 `IN_PROGRESS`만 허용하고 `ABANDONED`는 `EXAM_4007`, `COMPLETED`는 `EXAM_4008`의 409 비즈니스 오류로 차단한다.
- 기존 계약 유지: 새 시험의 최초 submit은 새 examId 기반 `retryCount=0` Job으로 시작하고 과거 Job·결과를 상속하지 않는다. 동일 examId/question/retryCount submit 멱등성, 사용자 주도 재답변의 같은 examId·증가된 retryCount, 완료 시험 재답변, AI `user_id=examId`, S3 Key, Summary·문항 단건 API와 BaseResponse 계약은 유지했다.
- 테스트: 세션 최초·교체·완료 보존·다중 비정상 활성 데이터·legacy·동시 시작, 새 retry 0, ABANDONED/활성 Callback, ABANDONED/COMPLETED grading retry 차단, 완료 시험 사용자 재답변, Summary dispatch race와 Repository annotation 계약을 추가·갱신했다. `git diff --check`와 `./gradlew clean test`가 성공했으며 XML 기준 Java 272개, failures/errors/skipped 0개다.
- 외부 작업: 실제 MongoDB·Redis·S3·AI 서버를 호출하거나 운영 인덱스를 변경하지 않았고 Secret·Token·Credential을 조회하거나 기록하지 않았다. Git commit·push·PR 및 Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-06 — Question submit PENDING·PROCESSING 전이 확인

<!-- codex-turn:019fd5fe-ae4a-7f20-a8d4-26893600946e -->

- 범위·결과: 별도 Jira 이슈 키 없이 문항 submit의 `QuestionGradingJob` 생성, claim, AI 동기 HTTP 호출과 응답 상태 변환을 읽기 전용으로 확인했다.
- 최초 submit은 MongoDB에 `PENDING`, `dispatchAttempt=0`, `pendingAt`의 Job을 먼저 insert한다. 같은 HTTP 요청 안에서 즉시 optimistic-lock claim을 수행해 `PROCESSING`, `dispatchAttempt=1`, `processingStartedAt`으로 저장한 다음 AI `/evaluations`를 호출한다.
- 정상 경로의 submit 응답은 AI endpoint가 2xx를 반환한 뒤 `PROCESSING`으로 반환된다. 따라서 PENDING은 아직 claim되지 않은 대기 상태이고, 별도 초기 dispatch queue/worker가 없는 현재 구조에서는 매우 짧은 내부 상태라 일반 클라이언트 응답에서 보이지 않는다.
- AI 호출 실패는 claim된 Job을 `FAILED`로 전이하고 API 오류를 반환한다. PROCESSING 저장 뒤 프로세스가 종료되는 경우에는 timeout 이후 기존 grading retry가 복구하는 구조다.
- 판단: 현재 상태 의미에는 PROCESSING 응답이 일관된다. submit에서 PENDING을 사용자에게 반환하려면 단순 응답값 변경이 아니라 insert 후 즉시 반환하고 별도 Worker가 원자적으로 claim·dispatch하도록 구조를 바꿔야 한다. 이번 확인에서는 코드와 테스트를 변경하거나 재실행하지 않았다.
- 외부 작업: 실제 AI·MongoDB·Redis·S3를 호출하지 않았고 Secret·Token·Credential을 조회하거나 기록하지 않았다. Git commit·push·PR 및 Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-06 — 채점 상태 용어 의미 확인

<!-- codex-turn:019fd601-83d8-7f03-86e0-00b88c958a2a -->

- 범위·결과: 별도 Jira 이슈 키 없이 사용자가 기대한 `PENDING=AI 채점 시작 대기`, `PROCESSING=AI가 실제 채점 계산 중` 의미와 현재 Learning Core 구현의 의미를 비교했다.
- 현재 `PENDING`은 Learning Core가 Question Job을 만들었지만 아직 dispatch claim하지 않은 매우 짧은 내부 대기 상태다. `PROCESSING`은 Learning Core가 dispatch를 claim한 순간부터 AI HTTP 요청과 Callback 대기 전체를 포함하며, AI가 내부 계산을 실제 시작했음을 보장하지 않는다.
- Learning Core가 받는 현재 신호는 outbound HTTP 성공과 최종 Callback뿐이므로 AI 내부 queue와 실제 계산 시작을 구분할 수 없다. 정확한 구분에는 AI의 accepted/started 상태 계약 또는 별도 상태 Callback이 필요하다.
- 이번 확인에서는 애플리케이션·테스트 코드를 변경하거나 테스트를 재실행하지 않았다. 실제 외부 시스템 호출, Secret·Token·Credential 조회·기록, Git commit·push·PR 및 Jira 쓰기 작업도 수행하지 않았다.
- 추가 확인: 시험 단위 grading retry는 FAILED를 즉시, PENDING은 기본 1분 timeout 이후, PROCESSING은 기본 3분 timeout 이후 재전송 대상으로 판정하며 최대 dispatch 횟수 기본값은 3이다. Summary Job도 같은 PENDING/PROCESSING timeout 판정을 사용한다.
- 프론트는 오래된 PROCESSING도 복구 UI 대상으로 포함할 수 있지만 같은 submit을 반복하지 않고 기존 `POST /api/v1/exams/{examId}/grading/retry`를 호출해야 한다. 실제 eligibility와 원자적 claim은 설정값·Job timestamp·attempt를 아는 백엔드 판단을 따른다.

## 2026-08-06 — 장기 PROCESSING 프론트 재시도 기준 확인

<!-- codex-turn:019fd603-9452-7782-882c-55e07902878e -->

- 범위·결과: 별도 Jira 이슈 키 없이 현재 Question/Summary Job의 retry eligibility와 프론트 복구 호출 방식을 읽기 전용으로 확인했다.
- FAILED는 즉시, PENDING은 기본 1분 timeout 이후, PROCESSING은 기본 3분 timeout 이후 시험 단위 grading retry 대상이며 최대 dispatch 시도 기본값은 3이다. 따라서 프론트 복구 UI에는 장기 PROCESSING도 포함하는 것이 현재 상태 의미와 일치한다.
- 동일 submit 재호출은 결정적 기존 Job 상태만 반환하고 재전송하지 않는다. 프론트는 `POST /api/v1/exams/{examId}/grading/retry`를 사용하고 실제 timeout·attempt eligibility와 원자적 claim은 백엔드 응답을 기준으로 처리해야 한다.
- PROCESSING은 AI 실제 계산뿐 아니라 outbound 및 Callback 대기도 포함하므로 너무 짧은 프론트 timeout은 중복 전송 가능성을 높인다. 재전송은 동일 Idempotency-Key를 사용하지만 AI 서버의 멱등 처리도 함께 보장되어야 한다.
- 애플리케이션·테스트 코드는 변경하거나 재실행하지 않았다. 실제 외부 시스템 호출, Secret·Token·Credential 조회·기록, Git commit·push·PR 및 Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-06 — Part 4 문항 조회 tableImageUrl 응답 전환

<!-- codex-turn:019fd605-1277-7e01-9d2a-ef505e75d8c3 -->

- 범위·결과: 별도 Jira 이슈 키 없이 문항 단건 `GET /api/v1/exams/{examId}/questions?questionNumber={number}&retryCount={optional}`의 Part 4 `questionInfo`를 DB 이미지 URL 중심으로 최소 변경했다. 전체 문항 목록 API로 바꾸지 않았고 Summary API도 변경하지 않았다.
- DB·응답 매핑: MongoDB의 `table_image_url`을 `Question.tableImageUrl`에 `@Field("table_image_url")`로 매핑하고 API에는 camelCase `tableImageUrl`로 그대로 전달한다. URL 결합·재작성·인코딩·Presigned URL 생성과 기본값 주입은 수행하지 않는다.
- 응답 단순화: Part 4 단건 `questionInfo`는 `part`, `questionNumber`, `tableImageUrl`만 직렬화한다. 기존 text, referenceText, partIntroText, audioUrl, guideAudioUrl, imageUrl, tableContext, prepTimeSec, speakTimeSec는 이 응답에서 제외하되 기존 `tableContext` 모델과 저장 데이터는 세션·prompt·내부 호환을 위해 삭제하지 않았다. Part 1·2·3·5·6·7은 기존 변환을 유지한다.
- 누락 정책: Part 4의 `table_image_url`이 null·빈 문자열·공백이면 임의 URL이나 NPE 대신 기존 카탈로그 설정 오류 `EXAM_5001`을 반환한다.
- AI 회귀: Part 4 답변 submit의 기존 multipart `part_number`, S3 음성, `Idempotency-Key` 계약을 유지했고 프론트 표시용 table image/context를 AI payload에 새로 포함하지 않았다. 실제 MongoDB·S3·AI 호출은 수행하지 않았다.
- 테스트: snake_case Mongo 매핑, camelCase JSON, URL 무변환, 상세 필드 미노출, URL 누락 세 경우, 다른 Part 6개 회귀와 Part 4 AI dispatch 계약을 검증했다. 집중 테스트와 `./gradlew clean test`가 성공했고 XML 기준 Java 286개, failures/errors/skipped 0개다. `git diff --check`도 성공했다.
- 외부 작업: Secret·Token·Credential을 조회하거나 기록하지 않았고 Git commit·push·PR 및 Jira 쓰기 작업은 수행하지 않았다.

## 2026-08-06 — Part 4 문제 전달 경로 확인

<!-- codex-turn:019fd633-1da2-7de3-8fb8-2098f66aada6 -->

- 범위·결과: 별도 Jira 이슈 키 없이 Part 4 문제가 현재 각 공개 API에서 어떤 DTO로 전달되는지 Controller, Service, Converter를 읽기 전용으로 확인했다.
- 채점 결과 단건 `GET /api/v1/exams/{examId}/questions?questionNumber={number}&retryCount={optional}`에서는 Part 4 원본의 Mongo `table_image_url`을 `question.questionInfo.tableImageUrl`로 가공 없이 전달하며, `questionInfo`에는 part·questionNumber·tableImageUrl만 포함한다. 외부 채점 결과·상태 필드는 기존 `PartResultDTO` 위치에 그대로 유지된다.
- 시험 생성 `POST /api/v1/exams`와 문제 전용 `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`는 별도 `toQuestionPrompt` 경로에서 기존 공통 `toQuestionDTO`를 사용한다. 이 경로는 현재 tableContext를 반환하고 tableImageUrl을 채우지 않으므로, 방금 적용한 URL-only 정책은 채점 결과 단건 API에만 적용돼 있다.
- 현재 문항 번호 매핑에서 Part 4는 Question 8~10이다. Part 판정용 문항 번호 매핑과 catalog의 `part_number`가 불일치하면 외부 partNumber와 questionInfo.part가 달라질 수 있으므로 catalog 정합성을 유지해야 한다.
- 이번 확인에서는 코드·테스트를 수정하거나 재실행하지 않았다. 실제 MongoDB·S3·AI 및 Secret·Token·Credential에 접근하지 않았고 Git·Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-06 — 배포 후 Part 4 기존 tableContext 응답 원인 확인

<!-- codex-turn:019fd63e-f6b8-7253-91d7-06e2829a68bd -->

- 범위·결과: 별도 Jira 이슈 키 없이 배포 환경에서 Part 4 Question 8~10이 text, audioUrl, tableContext 형태로 반환된 원인을 현재 Controller·Service·Converter 경로와 대조했다.
- 해당 형태는 여러 문항과 문제 음성 URL을 함께 조립하는 `POST /api/v1/exams`의 세션 생성 응답이다. `createExamSession`은 각 문항을 `toQuestionPrompt`로 보내고, 이 메서드는 기존 공통 `ExamConverter.toQuestionDTO`를 사용하므로 text·audioUrl·tableContext를 채우며 tableImageUrl은 채우지 않는다.
- 직전 URL-only 구현은 `ExamConverter.toQuestionResult` 내부의 Part 4 전용 `toQuestionInfoDTO`에만 적용된다. 따라서 `GET /api/v1/exams/{examId}/questions?questionNumber={number}&retryCount={optional}`에서는 tableImageUrl만 포함되지만 세션 생성 및 prompt API에는 적용되지 않는다.
- 결론: 새 코드가 실행되지 않은 현상이 아니라 서로 다른 변환 경로의 범위 차이다. 실제 시험 시작 화면에서도 이미지 URL만 사용하려면 세션 생성과 `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`의 Part 4 변환을 별도 승인 범위로 함께 변경해야 한다.
- 사용자가 제시한 Presigned URL, 임시 자격 정보와 서명 값은 작업 기록에 복사하지 않았다. 이번 확인에서는 애플리케이션·테스트·외부 시스템을 변경하지 않았고 Git·Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-06 — 시험 시작 Part 4 tableImageUrl 응답 적용

<!-- codex-turn:019fd641-0450-7ac3-8ee9-cf5e484f4f3f -->

- 범위·결과: 별도 Jira 이슈 키 없이 `POST /api/v1/exams`의 `result.questions` 배열에 포함되는 Part 4 문항을 table image 기반 응답으로 변경했다. 기존 `GET /api/v1/exams/{examId}/questions` 단건 결과 API와 별도 prompt API 계약은 변경하지 않았다.
- 변환: 세션 생성 전용 `ExamConverter.toCreateSessionQuestionDTO`를 추가했다. Part 4는 기존 text와 발급된 audioUrl을 유지하고 Mongo `table_image_url`에서 매핑된 Java `tableImageUrl`을 가공 없이 전달하며, DTO의 tableContext를 null로 만들어 NON_NULL JSON 직렬화에서 제외한다.
- 범위 격리: Part 1·2·3·5·6·7은 기존 `toQuestionDTO` 결과를 그대로 유지한다. `Question.tableContext`와 `TableItem` 내부 모델 및 Mongo `table_context` 매핑은 삭제하거나 변경하지 않아 AI·내부 데이터 흐름에 영향을 주지 않는다.
- 서비스: `createExamSession`만 세션 생성 전용 변환을 사용하고 공통 오디오 URL 조립을 재사용한다. `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`는 기존 `toQuestionDTO` 경로를 계속 사용한다.
- 테스트: Part 4 DB 모델 값과 세션 DTO URL의 정확한 동일성, tableContext 미노출, text·audioUrl 유지, 실제 POST BaseResponse JSON, Part 1·2·3·5·6·7의 기존 tableContext 유지와 기존 Mongo snake_case 매핑을 검증했다. 테스트 메서드 3개를 추가했고 parameterized 실행을 포함해 전체 테스트는 286개에서 295개로 증가했다.
- 검증: 집중 테스트와 `./gradlew clean test`가 성공했으며 XML 기준 tests/failures/errors/skipped는 295/0/0/0이다. `git diff --check`도 성공했다. 기존 unchecked 경고 외 신규 경고는 확인되지 않았다.
- 외부 작업: 실제 MongoDB·S3·AI를 호출하지 않았고 사용자 제공 Presigned URL·임시 자격 정보·Secret·Token을 코드·테스트·문서에 기록하지 않았다. Git commit·push·PR 및 Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-06 — Part 4 시험 시작 응답 기록 동기화

<!-- codex-turn:019fd641-0450-7732-8c1b-6375b0b28a15 -->

- 범위·결과: 별도 Jira 이슈 키 없이 현재 turn의 작업 기록 누락 hook을 처리했다. 과거 WORKLOG 항목은 수정하거나 삭제하지 않고 이 항목을 파일 끝에 append했다.
- 현재 구현 상태: `POST /api/v1/exams`의 Part 4 문항은 text·audioUrl을 유지하고 DB `table_image_url`의 원본 값을 `tableImageUrl`로 반환하며 tableContext는 프론트 JSON에서 제외한다. Part 1·2·3·5·6·7, 기존 문항 단건 API와 AI 내부 모델은 유지된다.
- 검증 상태: 직전 전체 `./gradlew clean test` 결과는 Java 295개, failures/errors/skipped 0개이고 `git diff --check`가 성공했다. 이번 기록 동기화에서는 애플리케이션·테스트 코드를 변경하거나 테스트를 다시 실행하지 않았다.
- 외부 작업: 실제 외부 시스템을 호출하지 않았고 Secret·Token·Credential을 기록하지 않았다. Git commit·push·PR 및 Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-07 — GitHub Actions ExamSession 동시성 테스트 실패 진단

<!-- codex-turn:019fd9e5-d960-70e2-a83b-6229fe61f82f -->

- 범위·결과: 별도 Jira 이슈 키 없이 GitHub Actions에서 `ExamSessionManagerTest.concurrentStartsLeaveExactlyOneActiveSessionAndNeverReuseExamId`가 `TooFewActualInvocations`로 실패한 원인을 테스트와 Manager 구현에서 읽기 전용으로 확인했다.
- 직접 원인: 테스트의 두 initial lookup은 latch 해제 뒤에 `sessions` snapshot을 읽는다. 한 스레드가 먼저 Session을 insert한 후 다른 스레드가 snapshot을 읽으면 두 번째 스레드는 첫 Session을 ABANDONED로 전이하고 자신의 Session을 충돌 없이 insert하므로 총 insert 호출은 2회다. 두 스레드가 모두 빈 snapshot을 먼저 읽는 스케줄에서는 한 번의 Duplicate Key와 재시도로 총 3회가 된다.
- 실패 의미: 최종 active Session 1개, 서로 다른 examId, 두 생성 결과와 보존 문서 2개 검증은 먼저 통과했고, line 282의 `times(3)`만 2회 실행을 허용하지 않아 실패했다. 로컬과 CI의 thread scheduling 차이에 따라 결과가 달라지는 테스트 flakiness이며 이 로그만으로 운영 비즈니스 로직 실패를 의미하지 않는다.
- 권장 수정: collision 경로를 검증하려면 두 initial lookup의 snapshot을 latch 대기 전에 고정해 Duplicate Key를 결정적으로 발생시킨다. 또는 동시성 테스트는 최종 불변식만 검증하고 Duplicate Key 재시도는 별도 deterministic 단위 테스트로 분리한다. 단순히 CI 재실행만 하는 것은 재발 가능성을 남긴다.
- 이번 진단에서는 애플리케이션·테스트 코드를 수정하거나 테스트를 재실행하지 않았다. 실제 MongoDB와 외부 시스템을 호출하지 않았고 Secret·Token·Credential을 기록하지 않았으며 Git·Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-07 — GitHub Actions flaky ExamSession 테스트 수정

<!-- codex-turn:019fd9eb-f015-7830-9818-3a1f66b4746f -->

- 범위·결과: 별도 Jira 이슈 키 없이 `ExamSessionManagerTest`만 수정했다. production 코드는 변경하지 않았고 기존 동시 시험 시작 로직과 사용자별 최종 active Session 하나 정책을 유지했다.
- flaky assertion 제거: `concurrentStartsLeaveExactlyOneActiveSessionAndNeverReuseExamId`에서 thread scheduling에 따라 2회 또는 3회가 될 수 있는 Repository `insert()`의 정확한 `times(3)` 검증을 제거했다. 같은 examId 미재사용, 두 결과 모두 created, 최종 active 1개, 문서 2개 보존이라는 observable final invariant는 그대로 유지했다.
- deterministic retry 테스트: `duplicateKeyDuringSessionCreationRetries`를 추가해 첫 `insert()`는 Mockito가 반드시 `DuplicateKeyException`을 던지고, retry lookup에서 concurrent active Session을 ABANDONED 처리한 뒤 두 번째 `insert()`가 저장 결과를 반환하도록 고정했다. 정상 Assignment 반환, IN_PROGRESS 상태, 다른 examId, insert 2회와 abandon 수행을 검증한다.
- 반복 검증: 문제가 발생했던 단일 동시성 테스트를 사용자 제시 명령의 `--rerun-tasks` 방식으로 10회 반복했고 10/10 성공했다. `Thread.sleep`, `@Disabled`, `times(2)` 또는 `atLeast` 완화는 사용하지 않았다.
- 전체 검증: `./gradlew test`가 성공했고 XML 기준 tests/failures/errors/skipped는 296/0/0/0이다. `git diff --check`도 성공했다.
- 외부 작업: 실제 MongoDB와 외부 시스템을 호출하지 않았고 Secret·Token·Credential을 기록하지 않았다. Git commit·push·PR 및 Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-07 — History 응답 및 retriedQuestionCount 경로 확인

<!-- codex-turn:019fda14-5b70-70a2-bd6c-0bb59cce9153 -->

- 범위·결과: 별도 Jira 이슈 키 없이 `GET /api/v1/exams/history?page=0&size=20`의 Controller, DTO, Service, Repository와 retries 집계 경로를 읽기 전용으로 확인했다.
- 인증·대상: JWT 모드에서 공개 endpoint가 아니므로 Bearer 인증이 필요하고 JWT sub UUID를 현재 userId로 사용한다. 현재 사용자의 `completedAt != null` Session만 포함하며 completedAt DESC, examId DESC로 정렬한다.
- 응답: 현재 `ExamHistoryResult`는 totalCount와 histories만, 각 `ExamHistoryItem`은 examId, title, cycleNumber, completedAt, totalScore, levelEstimate, summaryAvailable만 제공한다. 신규 ExamSummary를 우선하고 없으면 legacy ExamResult total summary를 fallback한다.
- 페이지네이션: Controller 메서드에는 page·size RequestParam과 Pageable이 없다. 따라서 해당 query parameter는 바인딩되지 않고 무시되며 완료 이력 전체와 전체 개수만 반환한다. page, size, totalPages, hasNext 계약은 없다.
- retriedQuestionCount: 현재 저장소의 main 소스·테스트 및 Git 전체 이력 검색에 해당 필드가 없고 History 서비스는 Question Job/문항 Result를 조회하지 않는다. Learning Core 현재 코드가 이 필드를 직렬화할 수 없으므로 배포 응답에 있다면 다른 이미지·커밋 또는 클라이언트/BFF 등 다른 계층의 보강 여부를 확인해야 한다.
- 관련 retries 의미: 별도 `GET /api/v1/exams/{examId}/retries`는 Job과 legacy Result를 `(questionNumber,retryCount)`로 합치고 Job을 우선한 뒤 retryCount 1 이상이 하나라도 있는 서로 다른 questionNumber만 반환한다. 이 questions 배열 크기를 count로 사용한다면 2는 추가 시도 총횟수가 아니라 재답변한 문항 2개라는 의미다. 이 계산은 현재 History에 결합되지 않는다.
- 이번 확인에서는 애플리케이션·테스트를 수정하거나 테스트를 재실행하지 않았다. 실제 API·MongoDB·외부 시스템에 접근하지 않았고 Access Token·Secret·Credential을 기록하지 않았으며 Git·Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-07 — TMI-61 History retriedQuestionCount 추가

- 범위·결과: 관련 Jira는 `TMI-61`이며 Jira 쓰기는 수행하지 않았다. `GET /api/v1/exams/history`의 각 항목에 `retriedQuestionCount`를 additive로 추가했다. 기존 page·size 미지원, 완료 판정, 정렬, Summary fallback과 다른 API 계약은 변경하지 않았다.
- 필드 의미: `retriedQuestionCount`는 해당 시험에서 사용자 `retryCount >= 1`인 저장 회차가 하나라도 존재하는 서로 다른 `questionNumber` 수다. 같은 문항의 retry 1·2·3 및 Job/Legacy Result 중복은 Set으로 한 문항만 계산하고, 해당 문항이 없으면 0을 반환한다. `dispatchAttempt`와 Job 상태는 count 값으로 사용하지 않는다.
- 조회·성능: 완료 History의 전체 examId Set으로 `question_grading_jobs`와 `exam_results`를 각각 한 번씩 batch 조회하고 examId·questionNumber·retryCount만 projection한다. 시험별 Repository 호출을 만들지 않았으며 기존 `{examId:1, questionNumber:1, retryCount:1}` 인덱스의 examId prefix를 재사용해 인덱스 스크립트는 변경하지 않았다.
- 문서·계약: DTO Schema, Controller OpenAPI 설명과 README를 갱신했다. JWT sub 사용자, completedAt 필터, completedAt DESC/examId DESC, 신규 Summary 우선·Legacy fallback, 내부 userId·mockExamId 비노출은 유지된다.
- 테스트: Job/Legacy batch union, 같은 문항·여러 회차 dedupe, retryCount 0 제외, 다른 examId 방어, count 0, API JSON의 `retriedQuestionCount: 2`, 최소 projection과 N+1 미사용을 검증했다. 집중 테스트와 `./gradlew clean test`가 성공했고 XML 기준 tests/failures/errors/skipped는 297/0/0/0이다. `git diff --check`도 성공했다.
- 외부 작업: 실제 MongoDB apply·query explain·API 호출은 수행하지 않았다. Secret·Token·Credential을 기록하지 않았고 Git commit·push·PR 및 Jira 댓글·필드·상태 변경을 수행하지 않았다.

## 2026-08-07 — TMI-61 History retriedQuestionCount 최종 확인

<!-- codex-turn:019fda16-f53e-70f1-a1f6-39d04483e4a6 -->

- 범위·결과: Jira `TMI-61`의 History 응답 `retriedQuestionCount` 추가 상태를 최종 확인했다. 해당 값은 시험별 `retryCount >= 1`인 고유 `questionNumber` 수이며, 같은 문항의 여러 회차와 Job/Legacy 중복은 한 번만 계산하고 없으면 0을 반환한다. `dispatchAttempt`는 계산에 사용하지 않는다.
- 구현·계약: 완료 History examId 전체를 QuestionGradingJob과 Legacy ExamResult에서 batch 조회하여 N+1을 피했다. JWT sub, completedAt 완료 판정, History 정렬, Summary fallback, 기존 시험 API 계약은 유지했다.
- 검증: 이번 turn에서 `ExamReadServiceTest`, `ExamReadApiContractTest`, `ExamReadRepositoryContractTest`를 재실행해 성공했고 `git diff --check`도 성공했다. 앞선 전체 `./gradlew clean test`는 Java 297개, failures/errors/skipped 0개로 성공한 상태다.
- 외부 작업: 실제 DB apply·explain은 수행하지 않았고 Secret·Token·Credential을 기록하지 않았다. Git commit·push·PR과 Jira 쓰기 작업도 수행하지 않았다.

## 2026-08-07 — TMI-61 History 현재 응답 계약 확인

<!-- codex-turn:019fda24-b264-7411-8b25-dc0a11e1f341 -->

- 범위·결과: Jira `TMI-61`과 관련해 `GET /api/v1/exams/history?page=0&size=20`의 Controller, DTO, BaseResponse, API 계약 테스트를 읽기 전용으로 확인했다.
- 실제 응답: `result` 하위는 `exams`가 아니라 `totalCount`와 `histories`다. History 항목은 `examId`, `title`, `cycleNumber`, `completedAt`, `totalScore`, `levelEstimate`, `summaryAvailable`, `retriedQuestionCount`를 반환한다.
- 미지원 필드: 현재 DTO에 `status`, `maxScore`, `startedAt`, `page`, `size`, `totalElements`, `totalPages`, `hasNext`는 없다. Controller가 page·size를 바인딩하지 않아 쿼리 파라미터는 무시되고 완료 History 전체를 반환한다.
- 시간·카운트: `completedAt`은 `LocalDateTime`으로 timezone suffix `Z`가 없는 형태로 직렬화된다. `retriedQuestionCount`는 `retryCount >= 1`이 있는 고유 문항 수로, Job/Legacy 중복과 여러 재답변 회차를 한 문항으로 계산한다.
- 외부 계약·작업: 이번 확인에서 애플리케이션·테스트 코드는 변경하지 않았고 기존 BaseResponse와 History 계약을 유지했다. Secret·Token을 기록하지 않았고 Git·Jira 쓰기 작업이나 외부 시스템 호출을 수행하지 않았다.

## 2026-08-07 — TMI-61 History status·maxScore·startedAt 추가

- 범위·결과: Jira `TMI-61`의 `GET /api/v1/exams/history` 항목에 요청된 `status`, `maxScore`, `startedAt` 세 필드만 additive로 추가했다. `totalCount`/`histories`, page·size 미지원, 완료 판정·정렬·Summary fallback·`retriedQuestionCount` 계약은 유지했다.
- 매핑: `status`는 Legacy 세션도 보정하는 `ExamSession.effectiveStatus()`, `startedAt`은 `ExamSession.createdAt`, `maxScore`는 현재 모의고사 고정 만점 200으로 반환한다. 추가 Repository 조회나 MongoDB 문서·인덱스 변경은 없다.
- 테스트: Service 매핑, API JSON의 `COMPLETED`/`200`/`startedAt`, JWT sub 소유 이력 응답과 내부 식별자 비노출을 검증했다. standalone MockMvc도 운영 Spring 날짜 직렬화와 같게 ISO-8601 문자열을 사용하도록 테스트 ObjectMapper를 구성했다.
- 검증: 집중 테스트와 `./gradlew clean test`가 성공했다. XML 기준 tests/failures/errors/skipped는 297/0/0/0이고 `git diff --check`도 성공했다.
- 외부 작업: Secret·Token·Credential을 기록하지 않았고 실제 DB·API·외부 시스템을 호출하지 않았다. Git commit·push·PR과 Jira 댓글·필드·상태 변경도 수행하지 않았다.

## 2026-08-07 — TMI-61 History 세 필드 추가 turn 종료 기록

<!-- codex-turn:019fda27-3dca-7c71-bb14-e9e5abdf5878 -->

- Jira `TMI-61` History 항목에 `status`, `maxScore`, `startedAt`을 추가한 현재 turn을 최종 기록했다. 매핑은 각각 `ExamSession.effectiveStatus()`, 고정 만점 200, `ExamSession.createdAt`이다.
- 기존 `totalCount`/`histories`, 완료 판정·정렬·Summary fallback·`retriedQuestionCount`와 기타 시험 API 계약은 유지됐다. Legacy 세션에 `createdAt`이 없으면 `startedAt` 또한 null이다.
- 집중 테스트와 전체 `./gradlew clean test`가 성공했고 tests/failures/errors/skipped는 297/0/0/0이다. Secret·Token을 기록하지 않았고 Git·Jira 쓰기 작업을 수행하지 않았다.

## 2026-08-07 — TMI-61 History 최종 응답 구조 안내

- Jira `TMI-61`의 현재 Controller·DTO·Service·BaseResponse를 읽기 전용으로 재확인했다. 응답은 `result.totalCount`와 `result.histories`로 구성된다.
- 각 History 항목은 `examId`, `title`, `status`, `cycleNumber`, `startedAt`, `completedAt`, `totalScore`, `maxScore`, `levelEstimate`, `summaryAvailable`, `retriedQuestionCount`를 반환한다. `maxScore`는 200이고 `status`는 유효 ExamSession 상태며, 시간 필드는 `LocalDateTime` ISO-8601 문자열이다.
- `page`, `size`, `totalElements`, `totalPages`, `hasNext`는 현재 응답에 없고 page·size query parameter는 무시된다. 이번 안내에서 애플리케이션·테스트 코드는 변경하지 않았고 테스트도 재실행하지 않았다.
- Secret·Token·Credential을 기록하지 않았고 Git·Jira 쓰기 작업과 외부 시스템 호출을 수행하지 않았다.

## 2026-08-07 — TMI-61 History 응답 구조 안내 turn 종료

<!-- codex-turn:019fda2e-db9d-7b41-aae0-a8020cab992d -->

- Jira `TMI-61` History의 현재 성공 응답이 BaseResponse와 `result.totalCount`, `result.histories`로 구성되며, History 항목에 `status`, `maxScore`, `startedAt`, `retriedQuestionCount`를 포함한다는 안내를 최종 기록했다.
- `maxScore` 200, `startedAt`/`completedAt`의 `LocalDateTime` 형식, Legacy `startedAt: null`, Summary 누락 시 점수·레벨 null 정책을 함께 확인했다. page·size와 pagination metadata는 현재 지원하지 않는다.
- 이번 turn은 읽기 전용 안내로 애플리케이션·테스트 코드를 변경하지 않았고 테스트도 재실행하지 않았다. Secret·Token을 기록하지 않았고 Git·Jira 쓰기 작업을 수행하지 않았다.

## 2026-08-07 — Part 4 tableImageUrl 응답 경로 확인

<!-- codex-turn:019fda79-d841-7190-9c56-3b9b781565ba -->

- 범위·결과: 별도 Jira 이슈 키 없이 Part 4 문항의 DB `table_image_url` → Java `tableImageUrl` → API 응답 경로를 Controller, Service, Converter, Entity, 테스트로 읽기 전용 확인했다.
- 시험 시작: `POST /api/v1/exams`의 `questions` 배열은 Part 4에서 저장된 `tableImageUrl`을 그대로 반환하고 `tableContext`를 null로 설정해 JSON에서 제외한다.
- 채점 결과 문항 단건: `GET /api/v1/exams/{examId}/questions?questionNumber=...&retryCount=...`의 `questionInfo`는 Part 4에서 `part`, `questionNumber`, `tableImageUrl`만 반환하고 `tableContext`와 기타 표 상세를 노출하지 않는다.
- 주의·발견: 별도 `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`는 현재 `toQuestionDTO()`를 사용해 `tableContext`를 매핑하고 `tableImageUrl`을 매핑하지 않는다. 따라서 “문제 조회”가 prompt API를 의미하면 아직 이미지 URL 방식으로 일치하지 않는다.
- 이번 확인에서 애플리케이션·테스트 코드를 변경하지 않았고 테스트도 재실행하지 않았다. Secret·Token을 기록하지 않았고 Git·Jira 쓰기나 외부 시스템 호출을 수행하지 않았다.

## 2026-08-07 — Part 4 table_context 세 API 통일 계획

<!-- codex-turn:019fda8d-d3b2-7cd1-a85b-c90f9e9ae069 -->

- 범위·결과: 별도 Jira 이슈 키 없이 `POST /api/v1/exams`, `GET /api/v1/exams/{examId}/questions?questionNumber=...&retryCount=...`, `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt` 세 경로의 Part 4 표 응답을 DB `table_context` 기준으로 통일하는 계획을 수립했다. 이번 turn은 계획만 작성했고 구현은 시작하지 않았다.
- 계약 가정: Part 4 외부 응답은 `tableImageUrl`을 제외하고 API camelCase `tableContext`만 반환한다. MongoDB `table_context` → Java/API `tableContext`, 내부 `session_title` → API `sessionTitle`의 네이밍 매핑만 적용하고 title/location/date/fee/items 값은 재구성·기본값 생성 없이 그대로 전달한다.
- 예정 구현: 시험 시작 Mapper의 Part 4 image 치환을 제거해 기존 `tableContext`를 유지하고, 채점 결과 `questionInfo` Mapper는 `part`, `questionNumber`, `tableContext`를 반환하도록 변경한다. prompt 경로는 이미 `tableContext`를 매핑하므로 계약 테스트를 보강한다.
- 검증·오류 정책 계획: 현재 `requirePartFourTableImageUrl` 검증을 `tableContext` 기준으로 교체하고, Part 4 `table_context` 누락을 NPE로 처리하지 않도록 기존 catalog configuration 오류 정책을 유지한다. 세 API JSON, Mongo snake_case/camelCase, 중첩 items, Part 1·2·3·5·6·7, AI 채점 회귀를 테스트한다.
- 주의·배포: 현재 `tableImageUrl`을 사용하는 프론트에는 breaking response change이므로 배포 순서 조정이 필요하다. 적용 전 운영·Staging `mock_exams.questions[].table_context`가 모든 Part 4 문항에 존재하는지 읽기 전용으로 확인해야 한다.
- 이번 계획 수립에서 애플리케이션·테스트 코드를 변경하지 않았고 테스트도 재실행하지 않았다. Secret·Token을 기록하지 않았고 Git·Jira 쓰기와 외부 DB 조회를 수행하지 않았다.

## 2026-08-07 — TMI-61 Retries 응답 비교 turn 종료

<!-- codex-turn:019fda7d-a6ae-76d1-b300-2e79e4e83a9a -->

- Jira `TMI-61` Retries 현재 응답이 문항별 `totalAttemptCount`, `latestRetryCount`와 회차별 `retryCount`, `status`를 반환하며, 사용자 제시안의 `score`, `completedAt`은 반환하지 않는다는 비교 결과를 최종 기록했다.
- Job/Legacy `(questionNumber,retryCount)` dedupe, Job status 우선, Legacy-only `COMPLETED`, `retryCount >= 1` 문항만 포함, 실제 저장된 0회차만 포함하는 현재 계약을 재확인했다.
- 이번 turn은 읽기 전용 비교로 애플리케이션·테스트 코드를 변경하지 않았고 테스트도 재실행하지 않았다. Secret·Token을 기록하지 않았고 Git·Jira 쓰기 작업을 수행하지 않았다.

## 2026-08-07 — TMI-61 Retries score·completedAt 추가

- 범위·결과: Jira `TMI-61`의 `GET /api/v1/exams/{examId}/retries` 회차 항목에 `score`, `completedAt`을 additive로 추가했다. 기존 `retryCount`, `status`, 문항별 `totalAttemptCount`, `latestRetryCount`와 BaseResponse 구조는 유지했다.
- 데이터 출처: `score`는 `ExamResult.score`, `completedAt`은 `QuestionGradingJob.completedAt` `Instant`를 반환한다. Job이 없는 Legacy Result-only 회차는 완료 시각 필드가 없으므로 `completedAt=null`이고, Result가 없는 Job-only 회차는 `score=null`이다.
- 결합 규칙: `(questionNumber,retryCount)` dedupe, Job status 우선, Legacy-only `COMPLETED`, 실제 저장된 회차만 반환하는 정책을 유지했다. Job과 Result가 겹치면 Job의 status/completedAt과 Result의 score를 함께 보존한다.
- Repository·보안: 기존 단일 examId 조회에서 Job `completedAt`과 Result `score`만 projection에 추가했다. `dispatchAttempt`, failure reason, feedback, Transcript, 음성 URL, 내부 userId는 여전히 노출하지 않는다. MongoDB 문서·인덱스 변경은 없다.
- 테스트·검증: Result score와 Job completedAt 결합, Legacy completedAt null, API ISO-8601 `Z` 직렬화, Repository 최소 projection, JWT sub 소유 응답을 검증했다. 집중 테스트와 `./gradlew clean test`가 성공했고 tests/failures/errors/skipped는 298/0/0/0이다. `git diff --check`도 성공했다.
- 외부 작업: Secret·Token·Credential을 기록하지 않았고 실제 DB·API·외부 시스템을 호출하지 않았다. Git commit·push·PR과 Jira 댓글·필드·상태 변경도 수행하지 않았다.

## 2026-08-07 — TMI-61 Retries 현재 응답 구조 비교

- 범위·결과: Jira `TMI-61`의 `GET /api/v1/exams/{examId}/retries` Controller, DTO, Service, Repository projection, 계약 테스트를 읽기 전용으로 확인하고 사용자 제시 응답과 비교했다.
- 현재 구조: `result` 하위에 `examId`, `questions`가 있고 문항 항목은 `partNumber`, `questionNumber`, `totalAttemptCount`, `latestRetryCount`, `attempts`를 반환한다. 각 attempt는 `retryCount`, `status` 두 필드만 반환하며 status는 `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`다.
- 제시안과 차이: 현재 API에는 attempt별 `score`, `completedAt`이 없고 대신 `status`가 있다. 현재에만 문항별 `totalAttemptCount`가 있다. Repository도 score·완료 시각을 projection하지 않는다.
- 결합 규칙: `QuestionGradingJob`과 Legacy `ExamResult`를 `(questionNumber,retryCount)`로 dedupe하고 Job이 있으면 Job status가 우선한다. Legacy Result만 있는 회차는 `COMPLETED`로 표시한다. `retryCount >= 1`이 하나도 없는 문항은 제외하고, 재답변 문항의 저장된 0회차는 포함하되 없는 0회차를 생성하지 않는다.
- 이번 확인에서 애플리케이션·테스트 코드를 변경하지 않았고 테스트도 재실행하지 않았다. Secret·Token을 기록하지 않았고 Git·Jira 쓰기나 외부 시스템 호출을 수행하지 않았다.

## 2026-08-07 — Part 4 table_context 통일 계획 종료 기록

- 위에 기록한 Part 4 세 API `tableContext`-only 통일 계획을 WORKLOG 끝에 추가로 확정했다. 해당 계획의 turn marker는 같은 작업 항목에 정확히 한 번만 기록되어 있다.
- 구현 전제는 Part 4 응답에서 `tableImageUrl`을 제외하고 DB `table_context` 내용을 API `tableContext`로 전달하는 것이다. 현재 프론트와 데이터 존재 여부를 확인한 뒤 구현·배포해야 한다.
- 이번 turn에서 애플리케이션·테스트 코드는 변경하지 않았고 테스트도 재실행하지 않았다. Secret·Token을 기록하지 않았고 Git·Jira 쓰기를 수행하지 않았다.

## 2026-08-07 — Part 4 table_context 비정형 원본 전달 계획 보정

<!-- codex-turn:019fda92-666b-73c3-ac7d-2f1a71477442 -->

- 사용자 확인에 따라 이전의 고정 `Question.TableContext(title/location/date/fee/items)` DTO 계획을 보정했다. 현재 고정 타입은 MongoDB `table_context`의 알려지지 않은 키를 보존하지 못하고, 없는 고정 필드를 null로 직렬화할 수 있으므로 “DB 구조 그대로” 계약을 충족하지 못한다.
- 최종 계획은 MongoDB 최상위 필드명 `table_context`만 Java/API의 `tableContext`에 연결하고, 그 값은 `Map<String, Object>` 등 JSON 호환 비정형 객체로 읽어 세 API에 재구성 없이 전달하는 것이다. 중첩 객체·배열·null과 임의 키를 보존하며 Map 키에는 camelCase 변환을 적용하지 않는다. 따라서 DB 내부 키가 `session_title`이면 응답 내부에서도 `session_title`을 유지한다.
- 적용 대상은 `POST /api/v1/exams`, 채점 결과 문항 단건 `GET /api/v1/exams/{examId}/questions?questionNumber=...&retryCount=...`, prompt `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`다. Part 4의 `tableImageUrl`은 제외하고 각 응답의 기존 공통 필드는 유지한다.
- 구현 시 `Question.tableContext`와 `ExamResponseDTO.QuestionDTO.tableContext`의 고정 타입 의존을 제거하고, Converter 세 경로가 같은 원본 Map을 사용하게 한다. 누락 검증은 null 여부만 확인하고 title/items 같은 특정 스키마는 강제하지 않는다. 임의 중첩 키를 가진 실제 Mongo Document 매핑 및 세 API JSON의 deep equality 테스트를 추가한다.
- 이번 turn은 계획 보정만 수행했고 애플리케이션·테스트 코드는 변경하거나 실행하지 않았다. Secret·Token을 기록하지 않았고 Git·Jira 쓰기와 외부 DB 조회를 수행하지 않았다.

## 2026-08-07 — Part 4 세 API 동일 tableContext 정책 확인

<!-- codex-turn:019fda94-988b-7d21-a6eb-a0637eb451b4 -->

- 별도 Jira 이슈 키 없이 시험 시작 `POST /api/v1/exams`, 채점 결과 문항 단건 `GET /api/v1/exams/{examId}/questions?questionNumber=...&retryCount=...`, 문제 prompt `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt` 세 API에 동일한 Part 4 정책을 적용한다는 점을 확인했다.
- 세 경로 모두 MongoDB `table_context`를 API 최상위 `tableContext`에 연결하고, 내부 객체·배열·null·임의 키는 고정 DTO 재구성이나 키 이름 변환 없이 동일하게 전달한다. 각 API의 기존 외곽 응답과 공통 필드만 경로별로 유지한다.
- Part 4 `tableImageUrl`은 세 경로에서 표 데이터의 대체 소스로 사용하지 않는다. 이번 turn은 계획 확인만 수행했고 애플리케이션·테스트 코드는 변경하거나 실행하지 않았다. Secret·Token을 기록하지 않았고 Git·Jira 쓰기와 외부 DB 조회를 수행하지 않았다.

## 2026-08-07 — Part 4 비정형 tableContext 세 API 최종 구현 계획

<!-- codex-turn:019fda97-23b1-7ee2-834b-98219a933e99 -->

- 별도 Jira 이슈 키 없이 Part 4 표 데이터 통일 작업의 전체 구현 계획을 확정했다. 대상은 시험 시작, 채점 결과 문항 단건, 문제 prompt 세 API이며 모두 MongoDB `table_context`를 API `tableContext`로 전달한다.
- 도메인과 응답 DTO의 고정 `Question.TableContext` 의존을 `Map<String, Object>` 형태로 교체해 중첩 객체·배열·null·임의 키를 보존한다. 최상위 필드명만 camelCase로 매핑하고 내부 Map 키와 값은 변환·보강·삭제하지 않는다.
- Converter 세 경로를 같은 원본 전달 정책으로 맞추고 Part 4 `tableImageUrl`은 제외한다. `table_context`가 null인 경우에만 기존 catalog configuration 오류를 사용하며 특정 title/items 등 하위 스키마는 검증하지 않는다. Part 1·2·3·5·6·7, AI 요청·Callback, Summary 및 각 API 외곽 계약은 유지한다.
- 테스트 계획은 실제 Mongo Document의 임의 구조 매핑, 세 API의 JSON deep equality와 `tableImageUrl` 미노출, 내부 snake_case 키 보존, 누락 오류, 다른 Part와 AI 흐름 회귀를 포함하며 구현 후 집중 테스트와 `./gradlew clean test`, `git diff --check`를 실행한다.
- 이번 turn은 최종 계획 정리만 수행했고 애플리케이션·테스트 코드는 변경하거나 실행하지 않았다. Secret·Token을 기록하지 않았고 Git·Jira 쓰기와 외부 DB 조회를 수행하지 않았다.

## 2026-08-07 — TMI-77 Part 4 비정형 table_context 세 API 통일 구현

<!-- codex-turn:019fda9f-a812-7020-ae20-687cc758f48d -->

- Jira `TMI-77` `[Learning Core] Part 4 table_context 원본 응답 통일` 작업을 사용자 요청에 따라 먼저 생성한 뒤 구현했다. Jira 댓글·상태·필드 추가 변경은 수행하지 않았다.
- Mongo 매핑: `Question.tableContext`를 고정 `Question.TableContext`에서 `Map<String, Object>`로 변경하고 `@Field("table_context")`는 유지했다. 실제 `MappingMongoConverter` 테스트로 임의 키, 중첩 객체·배열, null과 snake_case 내부 키가 고정 필드 주입이나 이름 변환 없이 보존되는 것을 검증했다.
- API 계약: 시험 시작 `POST /api/v1/exams`의 `questions[]`, 채점 결과 문항 단건 `GET /api/v1/exams/{examId}/questions`의 `questionInfo`, 문제 prompt `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`가 모두 같은 원본 `tableContext` Map을 반환한다. 응답 DTO의 `tableImageUrl` 필드는 제거했지만 Mongo `table_image_url` 내부 필드는 삭제하거나 문서를 변경하지 않았다.
- 오류·회귀: Part 4 `table_context=null`은 기존 `_EXAM_CATALOG_CONFIGURATION_ERROR`로 처리하고 빈 객체는 그대로 반환한다. Part 1·2·3·5·6·7 공통 필드, API URL·파라미터·BaseResponse, AI 요청에서 table context를 보내지 않는 계약, JWT 소유권과 Summary 흐름은 유지했다.
- 테스트: Mongo 원본 매핑, 세 서비스 경로와 세 JSON 응답, `tableImageUrl` 미노출, null/empty, 내부 null과 snake_case, 다른 Part, AI dispatch, 문항 API 경로, JWT 보안 집중 테스트가 성공했다. `./gradlew clean test`는 tests/failures/errors/skipped `303/0/0/0`, `git diff --check`도 성공했다.
- 외부 작업: 실제 MongoDB 조회·수정이나 데이터 마이그레이션은 수행하지 않았다. Secret·Token을 기록하지 않았고 Git commit·push·PR은 생성하지 않았다.

## 2026-08-07 — TMI-77 Jira 완료 처리

- 사용자 요청에 따라 Jira `TMI-77`을 워크플로 상태 `완료`로 전환했고, 조회 결과 상태 category가 `done`, resolution이 `완료`인 것을 확인했다.
- Jira 댓글이나 설명·기타 필드는 변경하지 않았다. 애플리케이션·테스트 코드는 추가 변경하지 않았고 테스트도 다시 실행하지 않았다. Secret·Token을 기록하지 않았으며 Git commit·push·PR은 생성하지 않았다.

## 2026-08-07 — TMI-77 Jira 완료 처리 turn 기록

<!-- codex-turn:019fdaa8-5032-7e22-8442-80f575289057 -->

- Jira `TMI-77`의 상태와 resolution이 `완료`임을 재확인하고 현재 turn 기록을 마쳤다. Jira 댓글·기타 필드와 애플리케이션·테스트 코드는 추가 변경하지 않았다.
- Secret·Token을 기록하지 않았고 Git commit·push·PR을 생성하지 않았다. Jira 종료 후 코드 테스트는 재실행하지 않았으며 문서 변경에 대한 `git diff --check`만 확인한다.

## 2026-08-07 — TMI-77 Part 4 table_context 운영 로그 검토

<!-- codex-turn:019fdac4-b305-72f2-8190-24284ca0f306 -->

- 완료된 Jira `TMI-77` 구현의 운영 가시성 후속 검토로, 시험 시작·채점 결과 문항 단건·prompt 세 경로에는 현재 Part 4 `table_context` 매핑 성공/누락 전용 로그가 없고 누락 시 catalog 예외만 발생함을 확인했다.
- 권장안은 Converter를 순수 변환기로 유지하고 `ExamServiceImpl`의 세 API 경계에서만 Part 4 성공을 `INFO`, `table_context=null`을 `WARN`으로 기록하는 것이다. 허용 정보는 operation, examId, questionNumber, Map fieldCount이며 tableContext 원문·내부 키·값, 사용자 ID, URL·Token은 기록하지 않는다.
- 이번 turn은 로그 설계 검토만 수행했고 운영 코드와 테스트는 변경하거나 실행하지 않았다. Jira `TMI-77` 상태는 완료로 유지하며 Jira·Git 쓰기 작업을 수행하지 않았다.

## 2026-08-07 — Learning Core 운영 모니터링 로그 범위 분석

<!-- codex-turn:019fdac5-c7ac-79d2-9079-6bc449aeb6d7 -->

- 현재 로그는 시험 세션 생성, 문항 submit과 Job 생성·재사용, Question AI 전송, Feedback·SpeechAce·Azure Callback 저장과 중복 멱등 처리를 일부 추적한다. Sentry는 error 이벤트, Actuator는 health endpoint만 설정되어 있어 INFO/WARN 추가만으로 자동 모니터링이나 알림이 완성되지는 않는다.
- 우선 보완 대상은 Summary 전송의 시작·성공·실패와 `jobId`/attempt, 시험 단위 retry의 집계 결과, Job 상태 전환, AI submit-to-callback 소요 시간이다. 폴링 API는 호출마다 INFO를 남기지 않고 상태가 바뀌는 순간만 기록하는 방향을 권장한다.
- 로그 형식은 안정적인 `event`, `outcome`, `jobId`, `examId`, `questionNumber`, `retryCount`, `dispatchAttempt`, `durationMs`, `reason` 키로 통일하고, INFO는 정상 상태 전환, WARN은 복구 가능한 이상, ERROR는 실제 실패에 한정하는 안을 제시했다.
- 기존 로그 중 실제 `userId`와 abandoned exam ID 목록, S3 `fileKey`, 예외 원문·`printStackTrace()`는 노출·검색 일관성 관점에서 먼저 정리할 후보로 확인했다. 음성, Transcript, Callback 원문, `tableContext` 원문·키·값, Presigned URL, Token·Secret은 새 로그에 포함하지 않는다.
- 이번 turn은 제안·정적 분석만 수행해 애플리케이션·테스트 코드를 변경하지 않았고 테스트를 실행하지 않았다. 완료된 Jira `TMI-77`의 코드와 상태는 변경하지 않았으며 Jira·Git 쓰기 작업도 수행하지 않았다.

## 2026-08-07 — Learning Core AI 통신 로그 정리

<!-- codex-turn:019fdaca-a026-7b02-ad4c-58a6aeebab21 -->

- Question AI 전송 한 번에 남던 submit 시작, Job 생성, 호출 직전, HTTP POST 시작·완료, 성공 로그를 정리했다. 기본 INFO에서는 최종 성공 이벤트 한 줄만 남고, 실제 Question Job 실패 전이가 저장된 경우에만 ERROR 한 줄을 남긴다. 이전 attempt의 늦은 실패는 최신 Job을 덮지 않으며 ERROR 없이 DEBUG로만 기록한다.
- Controller의 Feedback Callback 수신 로그와 HTTP adapter의 AI URI·S3 `fileKey`·오디오 크기 로그를 제거했다. 핵심 Feedback/Summary 저장 성공만 INFO로 유지하고, 중복 멱등 처리와 SpeechAce·Azure 보조 Callback은 DEBUG로 낮췄다. Callback 원문, 음성, Transcript와 Presigned URL은 기록하지 않는다.
- Summary dispatch에는 성공 또는 실제 실패 전이 한 줄과 `durationMs`를 추가하고, executor rejection은 WARN 한 줄로 남겼다. 시험 단위 retry는 문항 목록 대신 retried/waiting/missing 개수, Summary action과 전체 상태를 INFO 한 줄로 집계한다.
- 시험 세션 시작 시 실제 `userId`와 abandoned exam ID 목록을 기록하던 중복 로그를 제거하고, 생성된 `examId`, `mockExamId`, cycle, abandoned 개수만 한 줄로 남겼다. 전역 예외 처리의 `printStackTrace()`와 JSON 파싱 예외 원문 두 줄도 원문 없는 WARN 이벤트 한 줄로 정리했으며 기존 오류 응답 계약은 바꾸지 않았다.
- 로그 전용 집중 테스트와 stale attempt 동시성 테스트를 보강했다. 최종 `./gradlew clean test`는 tests/failures/errors/skipped `303/0/0/0`, `git diff --check`도 성공했다. 기존 공개 API·DTO·BaseResponse, `retryCount`, AI `user_id=examId`, Callback JSON, Redis Key/TTL과 S3 Object Key는 변경하지 않았다.
- 관련 완료 Jira `TMI-25`의 채점·Callback 동작과 `TMI-77`의 Part 4 응답 동작은 유지했고 Jira 상태·댓글·필드는 변경하지 않았다. 실제 외부 AI·AWS·MongoDB·Redis·Sentry는 호출하지 않았으며 Git commit·push·PR도 수행하지 않았다.

## 2026-08-07 — Learning Core 운영 모니터링 로그 추가 계획

<!-- codex-turn:019fdad6-532f-7f41-af6a-f4c1c9236b6d -->

- 별도 Jira 이슈 키 없이 현재 워킹 트리의 시험 생성, 소유권 검증, S3 업로드 URL, submit, Question/Summary Job, 시험 단위 retry, AI 전송, 네 종류 Callback, 전체·문항 Polling과 전역 예외 흐름을 정적 분석했다. 기존 사용자 미커밋 코드와 직전 로그 정리 변경은 수정하거나 되돌리지 않았다.
- 현재 기준 로그에는 `exam.session.created`, Question/Summary dispatch 성공·실패와 소요 시간, retry 집계, Feedback/Summary 저장, 보조 Callback 중복, executor rejection과 completion race가 있다. Sentry error 수집과 Actuator health는 설정되어 있으나 request/trace correlation, 로그 수집기·대시보드·알림 정의는 저장소에서 확인되지 않았다.
- 1순위 구현 지점은 `ExamSessionManager`의 이전 세션 폐기와 Summary 성공 후 완료 전이, `ExamGradingService`의 Question/Summary Job 완료·최대 attempt 도달·Summary Trigger 결정, `ExamServiceImpl`의 Callback 거절과 저장 후 Job 완료 연결, `GlobalExceptionAdvice`·`SecurityErrorResponseHandler`의 안전한 4xx/5xx 경계다. 정상 Polling 조회는 INFO로 남기지 않고 실제 상태 전이 또는 비정상 장기 체류만 관측한다.
- 2순위는 내부 requestId를 MDC에 넣는 요청 필터와 `GradingDispatchService`의 S3 다운로드/AI POST 단계 구분이다. 외부 응답 헤더나 DTO를 바꾸지 않고 requestId는 같은 HTTP 요청 안의 로그만 연결하며, 비동기 submit·Callback은 기존 `examId`와 `jobId`로 연결한다.
- 로그 표준은 `event`, `outcome`, `reason`, `examId`, `jobId`, `questionNumber`, `retryCount`, `dispatchAttempt`, `fromStatus`, `toStatus`, `durationMs`의 안정적인 key-value 형식으로 한다. INFO는 중요한 정상 전이, WARN은 복구 가능 이상·거절·최대 시도 도달, ERROR는 실제 Job 실패 전이와 예상하지 못한 5xx에만 사용하고 Sentry 중복 이벤트가 생기지 않도록 한 경로만 유지한다.
- 실제 `userId`, Authorization/JWT, Secret·Token, Presigned URL, S3 URL·Object Key, 음성 크기·내용, Transcript, Callback 원문, Azure/SpeechAce 원문, Feedback 전문과 `tableContext` 원문·키·값, 예외 메시지는 기록하지 않는다. 외부 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, `retryCount`, Redis Key/TTL과 S3 Object Key 계약은 변경하지 않는다.
- 검증 계획은 `OutputCaptureExtension`으로 이벤트명·level·필수 식별자·정확한 1회 기록·민감값 미포함을 확인하고, 동시 submit/Callback의 stale attempt와 중복 Callback이 ERROR를 만들지 않는지 회귀 테스트한다. 구현 후 집중 테스트, `./gradlew clean test`, `git diff --check`를 실행한다.
- 로그만으로는 도착하지 않은 Callback을 직접 관측할 수 없으므로 CloudWatch 등 수집 대상 확정 후 PROCESSING 장기 체류, dispatch failure, executor rejection, max attempts, completion race, 5xx를 필터·대시보드·알림으로 연결해야 한다. 이번 turn은 계획과 작업 기록 문서만 변경했으며 애플리케이션·테스트 코드와 Jira·Git 상태는 변경하지 않았다.

## 2026-08-07 — Learning Core 운영 모니터링 로그 구현

<!-- codex-turn:019fdadf-73ee-7351-80fc-95cad908a3be -->

- 별도 Jira 이슈 키 없이 앞서 확정한 모니터링 로그 계획을 구현했다. HTTP 요청마다 내부 UUID `requestId`를 MDC에 설정하고 요청 종료 시 복원하며, 외부 응답 헤더·DTO에는 노출하지 않았다. Summary 비동기 실행에도 MDC를 복사·복원하도록 TaskDecorator를 적용했다.
- 시험 세션 폐기·생성 충돌 재시도·완료 전이, Question/Summary Job 완료·최대 시도 도달, Summary Trigger 판단·예약, Callback 분류 거절, 사용자 소유권 거절과 읽기 데이터 누락을 안정적인 `event` key-value 로그로 추가했다. 정상 Polling과 일반 요청은 DEBUG로 제한하고 실제 상태 전이는 한 번만 INFO/WARN/ERROR로 기록한다.
- S3 다운로드와 AI POST 실패를 안전한 stage로 구분하고 각 단계 소요 시간을 기록했다. Presigned URL·S3 Object Key·예외 메시지·payload는 기록하지 않으며, 전역 4xx/5xx와 인증 401/403 로그도 URI 등 제한된 메타데이터와 예외 타입만 사용한다. 잘못된 JSON은 Sentry로 전송하지 않고 예상하지 못한 5xx만 메시지·원문 없는 단일 Sentry 이벤트로 보낸다.
- 실제 `userId`, Authorization/JWT, Secret·Token, 음성·Transcript, Callback/Feedback/Azure/SpeechAce/tableContext 원문이나 내부 키·값은 로그에 포함하지 않았다. 공개 API URL·Method·Request/Response DTO·`BaseResponse`, `retryCount`, AI/Callback `user_id=examId`, Redis Key/TTL, S3 Object Key 계약은 변경하지 않았다.
- 로그 캡처, requestId 비노출·정리, MDC 비동기 전파·복원, 상태 전이 정확히 1회, 중복/동시 Callback과 stale attempt, 안전한 401/403·4xx/5xx·Sentry, 민감값 미포함 테스트를 추가·보강했다. 집중 테스트와 `./gradlew clean test --no-daemon`이 성공했고 전체 tests/failures/errors/skipped는 `316/0/0/0`이다. `git diff --check`도 성공했다.
- 실제 MongoDB·Redis·AWS S3·Python AI·Sentry는 호출하지 않았고 Git commit·push·PR과 Jira 쓰기는 수행하지 않았다. CloudWatch 로그 그룹·retention·metric filter·dashboard·alarm은 저장소 밖 운영 설정이라 남아 있으며, Callback 미도착과 장기 `PROCESSING` 탐지는 별도 metric/watchdog 정책이 필요하다.
