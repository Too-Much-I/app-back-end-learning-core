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
