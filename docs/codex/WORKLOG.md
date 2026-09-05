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

## 2026-08-08 — 운영 로그 한글화 방식 검토

<!-- codex-turn:019fdf22-23b0-7f71-b16e-f10326f405fe -->

- 별도 Jira 이슈 키 없이 현재 운영 로그의 가독성 개선 방향을 검토했다. 사람이 직접 읽는 고정 설명 문장은 한글로 바꾸되, CloudWatch 검색·metric filter·dashboard·alarm과 테스트의 안정적인 기준이 되는 `event`, `outcome`, `reason`, `stage`, 상태값 및 필드명은 영문 식별자로 유지하는 혼합형을 권장한다.
- 권장 형식은 `문항 채점 작업 완료 event=grading.question.job.completed outcome=success ...`처럼 한글 설명 뒤에 기존 구조화 key-value를 두는 방식이다. 이벤트 코드까지 한글화하면 향후 필터와 알림 조건을 모두 다시 연결해야 하고 표기 변경에도 취약해지므로 권장하지 않는다.
- 한글 설명도 동적 payload나 예외 메시지를 조합하지 않는 고정 문구로 제한한다. 실제 userId, Authorization/JWT, Secret·Token, URL·S3 Key, 음성·Transcript, Callback/채점/tableContext 원문을 기록하지 않는 기존 보안 원칙은 그대로 유지한다.
- 이번 turn은 의견 제시와 정적 확인만 수행했고 애플리케이션·테스트 코드는 변경하지 않았으며 테스트도 실행하지 않았다. 공개 API·AI/Callback·Redis/S3 계약과 Jira·Git 상태는 변경하지 않았다.

## 2026-08-08 — 운영 로그 한글 설명 적용

<!-- codex-turn:019fe110-0c54-74b1-ae92-13611ca79d3f -->

- 별도 Jira 이슈 키 없이 사용자 확인에 따라 운영 로그를 혼합형으로 변경했다. HTTP 요청·인증·전역 예외, 시험 세션·소유권·이력, Question/Summary Job·Trigger·dispatch, Callback, S3/AI 단계, MongoDB 인덱스와 MockExam 카탈로그 로그의 사람이 읽는 고정 설명을 한글로 바꿨다.
- `event`, `outcome`, `reason`, `stage`, 상태값과 구조화 필드명은 영문 식별자로 유지했다. 변경 전 HEAD와 현재 소스에서 추출한 `event` 코드 목록이 완전히 동일함을 정적 비교해 기존 검색·metric filter·dashboard·alarm 연결 기준을 보존했다.
- Sentry의 예상하지 못한 5xx 고정 메시지도 한글로 변경했지만 예외 타입 tag는 유지했다. 실제 userId, Authorization/JWT, Secret·Token, Presigned URL·S3 Key, 음성·Transcript, Callback/채점/tableContext payload와 예외 메시지를 로그에 추가하지 않았다.
- 로그 캡처 테스트는 한글 설명과 기존 영문 이벤트 코드가 함께 출력되는지 확인하도록 갱신했다. 세션 전이·소유권·채점 전송·Summary 예약/실패·Callback 분류·401/403·비즈니스/5xx 경계와 민감값 미포함 검증을 유지했다.
- 관련 집중 테스트와 `./gradlew clean test --no-daemon`이 성공했다. 전체 tests/failures/errors/skipped는 `316/0/0/0`이며 `git diff --check`도 성공했다. 기존 컴파일 경고는 이번 작업과 무관해 변경하지 않았다.
- 공개 API URL·Method·Request/Response DTO·`BaseResponse`, `retryCount`, AI/Callback `user_id=examId`, Redis Key/TTL과 S3 Object Key는 변경하지 않았다. 실제 MongoDB·Redis·AWS S3·Python AI·Sentry를 호출하지 않았고 Git commit·push·PR 및 Jira 쓰기도 수행하지 않았다.

## 2026-08-10 — Sentry DSN 적용 준비 상태 확인

<!-- codex-turn:019fea6d-cc45-79f0-a385-0ab0347036b9 -->

- 별도 Jira 이슈 키 없이 Sentry DSN 적용 가능 여부를 정적 확인했다. 저장소에는 `sentry-spring-boot-starter-jakarta` 의존성이 이미 있고 운영 설정은 실제 값을 코드에 저장하지 않은 채 `SENTRY_DSN` 환경변수를 참조한다.
- `send-default-pii=false`, profile 기반 environment, 기본 trace sampling 비활성화와 테스트용 비운영 DSN 설정이 존재한다. 예상하지 못한 5xx는 전역 예외 경계에서 안전한 고정 메시지와 예외 타입 tag로 명시적으로 수집하도록 구현되어 있다.
- 실제 DSN을 채팅·코드·문서에 전달하거나 기록할 필요는 없다. 배포 환경의 Secret에 `SENTRY_DSN`을 등록하고 애플리케이션을 재시작하면 되며, tracing이 필요할 때만 `SENTRY_TRACES_SAMPLE_RATE`를 별도로 정한다.
- 이번 turn은 읽기 전용 설정 확인과 안내만 수행했다. 애플리케이션·테스트 코드는 변경하지 않았고 테스트를 실행하지 않았으며 실제 Sentry 호출, 배포 환경 Secret 변경, Git commit·push·PR과 Jira 쓰기를 수행하지 않았다.

## 2026-08-10 — Sentry 운영 적합성 검토

<!-- codex-turn:019fea70-cd35-7cf0-9038-9afc0de62f77 -->

- 별도 Jira 이슈 키 없이 현재 Sentry 설정과 전역 예외 수집 방식을 정적 검토했다. DSN 환경변수 참조, `send-default-pii=false`, ERROR 이상 자동 수집, 기본 trace sampling 0과 4xx WARN 제외는 민감정보·노이즈를 줄이는 초기 오류 수집 설정으로 적절하다.
- 가장 큰 공백은 예상하지 못한 5xx를 `captureMessage`로 수집해 예외 타입 tag는 남지만 원본 스택트레이스가 Sentry 이벤트에 포함되지 않는다는 점이다. 현재 방식은 예외 메시지의 URL·Token 등 민감값 노출을 막는 대신 장애 발생 위치 분석력이 제한된다.
- `sentry.release`가 없어 이벤트를 배포 버전·커밋과 연결하기 어렵고, environment가 active Spring profile에 의존하므로 운영에서 profile 누락 시 local로 분류될 수 있다. 명시적인 `SENTRY_RELEASE`와 `SENTRY_ENVIRONMENT` 주입을 권장한다.
- Sentry SDK는 고정 버전이므로 현재 Spring Boot/Java 조합에서 동작은 가능하지만 정기적인 호환성·보안 업데이트 검토가 필요하다. 실제 DSN 주입 후 비민감 5xx 테스트 이벤트, environment/release, 중복 수집 여부와 프로젝트 Alert Rule은 별도 운영 검증 대상이다.
- 이번 turn은 분석과 작업 기록만 수행했다. 운영 코드·테스트·외부 시스템을 변경하거나 테스트하지 않았고 실제 DSN, Secret, Token을 조회·기록하지 않았으며 Git commit·push·PR과 Jira 쓰기도 수행하지 않았다.

## 2026-08-10 — Sentry 운영 보완 계획서 작성

<!-- codex-turn:019fea75-6103-75e1-b412-bf907ea3d42f -->

- 별도 Jira 이슈 키 없이 `docs/codex/SENTRY_PRODUCTION_HARDENING_PLAN.md`에 Sentry 운영 보완 계획서를 작성했다. 목적, 현재 상태, 보안·호환성 원칙, 목표 구조, 단계별 구현, 예상 파일, 자동 테스트, staging 검증, Alert Rule, 롤백, 완료 조건과 구현 전 결정 사항을 포함한다.
- 로컬 SDK 7.14.0 소스를 확인해 Spring Bean `BeforeSendCallback`, `IHub`, 예외 value와 stacktrace의 독립 수정, 기본 order 1의 `SentryExceptionResolver`를 실제 구현 근거로 사용했다. 예외 메시지를 제거하면서 타입·mechanism·stack frame을 보존하는 방식을 P0으로 정했다.
- 구현 순서는 environment/release 명시, 전역 event sanitizer, reporter 추상화와 `captureException`, 인메모리 transport 기반 중복·PII 테스트, staging smoke 검증이다. SDK 업데이트와 Sentry 프로젝트 Alert Rule은 capture 동작 안정화 후 별도 P1 단계로 분리했다.
- 실제 DSN을 저장하거나 공개 오류 API를 추가하지 않고, 공개 API·AI/Callback·Redis/S3 계약을 유지하도록 명시했다. 현재 전역 500 응답에 내부 예외 메시지가 전달될 수 있는 별도 위험은 외부 오류 응답 호환성 검토 없이 본 작업에 묶어 변경하지 않도록 기록했다.
- 이번 turn은 계획 문서만 작성했으며 애플리케이션·테스트 코드와 외부 시스템을 변경하지 않았고 테스트를 실행하지 않았다. 실제 자격정보를 조회·기록하지 않았으며 Git commit·push·PR과 Jira 쓰기도 수행하지 않았다.

## 2026-08-11 — Sentry 운영 보완 계획 피드백 반영

<!-- codex-turn:019fee54-0663-7f83-a978-3f38a82df2c6 -->

- 별도 Jira 이슈 키 없이 사용자의 Sentry 수집 정책 피드백을 `docs/codex/SENTRY_PRODUCTION_HARDENING_PLAN.md`에 반영했다. 초기 역할을 `Sentry=조사가 필요한 예외`, `CloudWatch=구조화 운영 로그`로 분리하고 `sentry.logging.enabled=false`로 Logback ERROR 자동 event 전송을 끄도록 확정했다.
- 예상하지 못한 Controller 5xx는 명시적 `captureException` 1건과 예외 원문 없는 CloudWatch ERROR 1건, 4xx와 grading·AI dispatch·Callback ERROR는 Sentry 0건으로 정했다. 추후 승인된 특정 운영 실패만 reporter로 명시 수집한다.
- `BeforeSendCallback`은 실패 시 원본을 보내지 않는 fail-closed 정책으로 정했다. message·exception value·request·transaction·user·breadcrumb·비허용 tag/extra/context뿐 아니라 stack local·절대 경로·source context·register·lock, mechanism 자유 형식 map과 SDK unknown field까지 제거 대상으로 보강했다.
- `BeforeSendCallback`이 attachment와 tracing transaction을 정제하는 경계는 아니라는 점을 추가했다. 초기 attachment는 금지하고 recording envelope의 attachment·transaction item 0건을 검증하며, tracing은 0을 유지하고 활성화 전 별도 transaction sanitizing을 설계하도록 했다.
- release는 CI source context와 ECS runtime 모두 `app-back-end-learning-core@<git-sha>`로 통일하고, Sentry 프로젝트의 IP 저장 방지 설정, 현재 SDK 7.14.0 우선 고정과 8.x 별도 업그레이드 원칙을 기록했다.
- recording transport 통합 테스트에 최종 직렬화 event의 가짜 민감 marker 부재, ControllerAdvice 5xx의 reporter·resolver·Logback·transport·CloudWatch 정확한 건수, grading ERROR의 Sentry 0건과 reporter 실패 시 기존 응답 유지 검증을 추가했다.
- reporter metadata는 SDK 7.14.0의 `captureException(Throwable, ScopeCallback)` 호출별 local scope에만 넣어 연속 요청 간 tag·requestId 누출을 막고, 두 event 연속 capture 회귀 테스트로 고정하도록 추가했다.
- 로컬 Spring MVC 6.2.2와 Sentry 7.14.0 소스를 추가 확인했다. 기본 exception resolver composite order 0이 Advice를 먼저 처리하고 Sentry resolver order 1은 처리되지 않은 예외만 보게 되는 현재 순서를 문서화했으며, 향후 framework·SDK 변경에 대비해 resolver 순서가 아니라 final transport 건수를 회귀 기준으로 삼았다.
- 이번 작업은 계획·상태·작업 기록 문서만 변경했다. 애플리케이션·테스트·배포 workflow·외부 Sentry를 변경하거나 실행하지 않았고 실제 DSN·Secret·Token을 조회·기록하지 않았다. 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, `retryCount`, Redis Key/TTL과 S3 Object Key 계약을 유지했다.
- `git diff --check`와 신규 계획서의 no-index whitespace 검사가 성공했다. 문서 전용 변경이라 `./gradlew clean test`는 실행하지 않았다.

## 2026-08-11 — Sentry 운영 보완 구현

<!-- codex-turn:019fee93-a1a6-78a0-a796-30b31bf2c1a1 -->

- 별도 Jira 이슈 키 없이 확정된 Sentry 운영 보완 계획의 P0 애플리케이션 구현과 자동 검증을 완료했다. 실제 DSN, 배포 workflow와 외부 Sentry 프로젝트 설정은 변경하지 않았다.
- `application.yml`과 test 설정에 명시적 environment/release, request body 비수집, resolver order 1과 `sentry.logging.enabled=false`를 적용했다. 일반 grading·AI dispatch·Callback ERROR는 CloudWatch 전용으로 남고 Logback ERROR가 자동 Sentry Issue가 되는 경로를 제거했다.
- `UnexpectedExceptionReporter`와 `SentryUnexpectedExceptionReporter`를 추가해 예상하지 못한 ControllerAdvice 5xx를 원본 stack이 있는 `captureException` 1건으로 전환했다. 같은 5xx는 예외 객체·메시지 없이 한글 구조화 CloudWatch ERROR 1건을 남기고, 4xx·JSON 파싱·비즈니스 오류는 Sentry에 보내지 않는다.
- fail-closed `SentryEventSanitizer`는 event/exception message, request/user/breadcrumb, transaction/fingerprint, 비허용 tag·context·extra, module/dist, stack local·절대 경로·source context·register·lock·주소, mechanism 자유 형식 map과 unknown field를 제거한다. 예외 type과 애플리케이션 stack, environment/release, 안전한 분류와 UUID requestId context만 보존한다.
- reporter의 호출별 scope에서 request/user/breadcrumb/attachment/tag/context뿐 아니라 session·propagation baggage·replay ID까지 초기화한다. 기본 resolver는 `SanitizedSentryExceptionResolver`로 교체해 unhandled MVC 예외도 같은 격리 경로를 사용하고, `UnhandledExceptionCaptureFilter`는 하위 필터 `ServletException`·`RuntimeException`을 1회 보고하며 request attribute로 resolver와 중복을 막는다. 연결 종료 가능성이 있는 `IOException`은 자동 Issue 대상에서 제외했다.
- 단위·MVC·recording transport 테스트로 handled/unhandled 1건, 안전한 ERROR 1건, 4xx와 Logback 자동 event 0건, 연속 capture scope 격리, parent attachment·session·baggage 제거, 최종 event/envelope의 가짜 민감 marker 부재, stack 보존, sanitizer·reporter 실패 시 기존 흐름 유지를 검증했다. 테스트는 실제 Sentry 네트워크를 호출하지 않는다.
- 최종 `./gradlew clean test --no-daemon`은 tests/failures/errors/skipped `332/0/0/0`으로 성공했다. `git diff --check`, 신규 파일 whitespace와 민감정보 패턴 검사도 성공했고 기존 `ExamServiceImpl` unchecked 경고만 남았다.
- 공개 API URL·Method·Request/Response DTO·`BaseResponse`, 실제 userId 비노출, AI/Callback `user_id=examId`, `retryCount`, Redis Key/TTL과 S3 Object Key는 변경하지 않았다. 기존 500 응답 body의 내부 예외 메시지 가능성은 호환성 때문에 별도 위험으로 유지했다.
- 운영 전 실제 환경의 DSN·environment·release 주입, CI/ECS release 일치, staging smoke, Sentry IP 저장 방지와 Alert Rule 설정이 남아 있다. SDK 8.x 업그레이드와 tracing 활성화도 별도 후속 작업이다. Git commit·push·PR과 Jira 쓰기는 수행하지 않았다.

## 2026-08-11 — Sentry 배포 환경변수 정리

<!-- codex-turn:019feebb-4400-7850-984d-708a0614ee0c -->

- 별도 Jira 이슈 키 없이 현재 `application.yml`, Dockerfile과 배포 파일 존재 여부를 기준으로 Sentry 런타임 환경변수를 정리했다. 저장소에는 ECS Task Definition이나 GitHub Actions 배포 Workflow가 없어 실제 주입은 저장소 밖 배포 설정에 남아 있다.
- staging/prod에는 `SENTRY_DSN`, `SENTRY_ENVIRONMENT`, `SENTRY_RELEASE`를 명시한다. DSN은 보호 저장소에서 주입하고, environment는 각각 `staging`·`prod`, release는 `app-back-end-learning-core@<git-sha>` 형식의 전체 Git SHA를 사용하며 같은 배포의 CI와 ECS runtime 값을 일치시킨다.
- `SENTRY_TRACES_SAMPLE_RATE`는 현재 미설정 또는 `0.0`을 유지한다. `SPRING_PROFILES_ACTIVE`는 기존 환경변수지만 staging/prod 값을 명시하고, Sentry 환경 오분류를 막기 위해 `SENTRY_ENVIRONMENT`도 별도로 설정한다.
- `SENTRY_AUTH_TOKEN`, `SENTRY_ORG`, `SENTRY_PROJECT`는 현재 런타임에 필요하지 않다. 향후 CI release 생성이나 source context 업로드를 도입할 때만 최소 권한 CI Secret과 비밀값이 아닌 프로젝트 식별 설정으로 별도 검토한다.
- 실제 DSN·Secret·Token은 조회하거나 기록하지 않았고 애플리케이션·테스트·배포 파일 및 외부 API·AI/Callback·Redis/S3 계약을 변경하지 않았다. 문서 전용 작업이라 Gradle 테스트는 다시 실행하지 않았으며 Git commit·push·PR과 Jira 쓰기도 수행하지 않았다.

## 2026-08-11 — 빈 종합 피드백 실패 및 선택적 재생성 계획

<!-- codex-turn:019feec7-082b-7181-8b15-ea05af231800 -->

- 별도 신규 Jira 이슈 키 없이 기존 `TMI-25`의 시험 단위 `POST /api/v1/exams/{examId}/grading/retry`와 Question/Summary Job을 기반으로 `docs/codex/FEEDBACK_GENERATION_RECOVERY_PLAN.md`를 작성했다.
- 현재 Summary Callback은 `partFeedback` null/empty를 검사하지 않고 Summary 저장, Summary Job 완료와 ExamSession 완료를 수행할 수 있다. 빈/null Map은 저장하지 않고 Summary Job을 `FAILED`, reason=`FEEDBACK_GENERATION_FAILED`로 전환하며 Session을 `IN_PROGRESS`로 유지하도록 계획했다.
- 프론트는 기존 status polling에서 exact code `FEEDBACK_GENERATION_FAILED`, message `피드백 생성에 실패했습니다.`를 기존 `BaseResponse` 오류로 받고 같은 grading retry API를 호출한다. Summary 조회도 동일 상태에서 같은 오류를 반환하고, AI Callback은 상태 영속화 후 200 delivery acknowledgement를 반환하는 안을 권장했다.
- 현재 Summary 준비 판단이 실제 최초 결과뿐 아니라 `QuestionGradingJob=COMPLETED`도 완료로 인정하는 공백을 확인했다. 배정된 MockExam의 현재 1~11번 모든 문항에 `retryCount=0` 또는 legacy null의 실제 `ExamResult`가 있어야 Summary를 시작하고, COMPLETED Job만 있는 문항은 결과 누락 복구 대상으로 분리한다.
- 프론트 retry 시 실패한 Summary Job을 다음 generation의 PENDING으로 먼저 재무장해 사용자 복구 의도를 durable하게 남긴다. 모든 결과가 있으면 Question AI 요청 0건과 Summary 1건, 누락 결과가 있으면 해당 문항만 복구하고 마지막 Callback 뒤 PENDING Summary 1건이 예약되도록 계획했다.
- 재생성 실패 후 다시 retry할 수 있도록 내부 `generationAttempt`와 generation별 `dispatchAttempt` 제한을 분리한다. 기존 Summary ID와 AI/Callback JSON은 유지하되 Python AI가 동일 `Idempotency-Key`를 캐시하면 재생성이 불가능하므로 generation별 키 허용 여부와 Callback 순서 보장을 구현 전에 확인하도록 기록했다.
- 기존 빈 Summary·COMPLETED Session은 별도 읽기 전용 집계와 승인된 데이터 복구 runbook 대상으로 분리했다. 구현이 아니므로 애플리케이션·테스트·외부 데이터와 공개 API·DTO·Redis/S3 계약을 변경하지 않았고 Gradle 테스트를 실행하지 않았다. 실제 Secret·Token·Callback payload를 조회하거나 기록하지 않았으며 Git commit·push·PR과 Jira 쓰기도 수행하지 않았다.

## 2026-08-11 — Summary generation fencing 계획 보강

<!-- codex-turn:019fef0a-95f1-72d1-8881-39ef176454a2 -->

- 별도 신규 Jira 이슈 키 없이 기존 `TMI-25` grading retry 기반의 `docs/codex/FEEDBACK_GENERATION_RECOVERY_PLAN.md`에 사용자가 확정한 `generationAttempt` 및 재시도·동시성 정책을 반영했다.
- Question AI Request/Callback은 유지하고 Summary Request/Callback에만 `generation_attempt`를 추가한다. Learning Core만 generation을 생성·증가시키며 Python AI는 요청 값을 그대로 echo하고, 값 누락 또는 현재 Job generation 불일치는 empty/valid Callback 모두 stale no-op하도록 확정했다.
- `generationAttempt`는 Summary Job이 `FAILED/FEEDBACK_GENERATION_FAILED`일 때 사용자 grading retry 요청으로만 증가한다. 새 generation은 PENDING과 `dispatchAttempt=0`으로 시작하고, 동일 generation의 transport timeout·전송 실패·retry는 generation을 바꾸지 않는다. legacy Job의 누락 generation은 1로 해석한다.
- Summary Scheduler/claim/dispatch/완료/실패에 generation 조건을 적용하고 외부 요청 직전에도 현재 generation을 다시 확인한다. Callback 선행 조회 직후 generation이 바뀌는 경합까지 막기 위해 실제 Summary 저장 전에 generation/version completion claim 또는 Mongo transaction을 요구하도록 보강했다.
- 실제 최초 `ExamResult` 없이 COMPLETED인 Question Job은 `QUESTION_RESULT_MISSING`으로 분류한다. version 또는 내부 recovery-cycle fence로 한 요청만 PENDING re-open하고 `dispatchAttempt=0`으로 초기화해 새 복구 사이클을 시작하며 Question wire 계약은 변경하지 않는다.
- Summary Idempotency-Key는 generation 1의 기존 키를 유지하고 generation 2 이상에서 generation suffix를 사용한다. 같은 generation transport retry는 같은 키를 쓴다. stale Callback, stale Scheduler claim, 동시 retry, Question missing-result re-open, legacy generation과 completion storage race 테스트를 계획에 추가했다.
- 프론트는 기존 status/Summary 조회의 HTTP 500과 exact `FEEDBACK_GENERATION_FAILED` code를 retry signal로 사용한다. 다른 기존 5xx/FAILED, 공개 API URL·Method·프론트 DTO·`BaseResponse`, `user_id=examId`, retryCount, Redis/S3 계약은 유지한다.
- Python AI를 먼저 배포하고 구버전 Summary 요청을 generation 1로 echo하는 전환 정책, generation별 Idempotency-Key 독립 처리와 in-flight 구버전 Callback 확인을 배포 순서에 추가했다. 기존 empty Summary 데이터는 별도 읽기 전용 집계와 승인된 runbook 대상으로 유지했다.
- 이번 turn은 계획·상태·작업 기록 문서만 변경했다. Gradle 테스트는 실행하지 않았고 실제 외부 시스템·Secret·Token·Callback payload, Git/Jira 상태를 변경하지 않았다. Git commit·push·PR도 수행하지 않았다.

## 2026-08-11 — TMI-25 Summary generation 재생성 및 누락 결과 복구 구현

<!-- codex-turn:019fef1e-6a85-7642-bc65-7c560eda93b8 -->
<!-- codex-turn:019fef3a-5c2a-7e61-9826-a1bf11ae18b6 -->
<!-- codex-turn:019fef45-24de-7660-86f3-3ff5b1676de5 -->

- Jira `TMI-25` 후속 구현으로 `docs/codex/FEEDBACK_GENERATION_RECOVERY_PLAN.md`의 `generationAttempt`, Summary 재생성, Scheduler/Callback fencing과 Question 결과 누락 복구를 적용했다.
- `SummaryGradingJob`에 legacy 기본 1인 generation과 completion claim을 추가했다. `FAILED/FEEDBACK_GENERATION_FAILED`에 대한 사용자 grading retry만 조건부 Mongo update로 generation을 1 증가시키고 `PENDING`, `dispatchAttempt=0`으로 재무장한다. 동일 generation transport retry는 generation을 바꾸지 않는다.
- Summary AI body/Callback DTO에만 `generation_attempt`를 추가했다. generation 1은 기존 Idempotency-Key를 유지하고 generation 2 이상은 `:generation:<n>` suffix를 사용한다. Scheduler 예약과 claim에 generation을 포함하고 상태 claim, AI 전송 직전, 전송 실패 처리에서 stale generation을 no-op하도록 했다.
- Summary Callback은 generation 누락·불일치를 payload 유효성과 관계없이 무시한다. 현재 generation의 null/empty `partFeedback`은 Summary와 완료 상태를 만들지 않고 `FAILED/FEEDBACK_GENERATION_FAILED`를 저장한다. valid Callback은 generation completion claim 후 Summary 저장, Job·Session 완료와 Redis projection 순으로 멱등 수렴한다.
- 실제 `retryCount=0` 또는 legacy null `ExamResult`만 Summary 준비 근거로 사용한다. 결과 없이 COMPLETED인 Question Job은 `QUESTION_RESULT_MISSING`으로 원자적 re-open하고 dispatch attempt를 0으로 초기화하며 내부 recovery cycle fence로 동시 복구와 stale 실패 갱신을 차단한다.
- status polling과 Summary 조회는 해당 실패를 HTTP 500, exact code `FEEDBACK_GENERATION_FAILED`, message `피드백 생성에 실패했습니다.`의 기존 `BaseResponse`로 반환한다. 공개 retry API body/response, 다른 5xx, Question AI 계약과 `user_id=examId`, retryCount, Redis/S3, 소유권 검증은 유지했다.
- 반복 generation 1→2→3, 동시 retry 단일 증가, stale Callback/worker, completion claim 우선, generation별 Idempotency-Key, legacy generation, COMPLETED+missing result의 dispatch reset·동시 복구, exact 오류 응답 계약 테스트를 추가했다. 최종 `./gradlew clean test`는 `351/0/0/0`, `git diff --check`는 성공했다.
- 실제 Python AI·운영 데이터·MongoDB·Redis·S3·Sentry·배포 설정은 변경하지 않았고 Secret·Token·Callback payload를 기록하지 않았다. Python AI 선배포, generation별 멱등 처리, in-flight 구버전 Callback과 기존 empty Summary 집계가 배포 전 확인 사항이다. Git commit·push·PR과 Jira 쓰기는 수행하지 않았다.

## 2026-08-14 — 8월 11~13일 작업 기록 날짜별 정리

<!-- codex-turn:019ffdf1-7bc6-7e11-9f09-532d6f9f5132 -->

- 기존 WORKLOG 원문은 수정하거나 삭제하지 않고, 연속된 작업의 주제와 진행 순서를 기준으로 2026-08-11부터 2026-08-13까지의 내용을 날짜별로 다시 묶었다.
- **2026-08-11 — Sentry 운영 보완:** 운영 보완 계획에 수집 정책 피드백을 반영하고, `Sentry=조사가 필요한 예외`, `CloudWatch=구조화 운영 로그`의 역할을 확정했다. 예상하지 못한 5xx만 안전하게 1회 수집하도록 reporter·sanitizer·resolver·filter와 격리 테스트를 구현했으며, 전체 테스트 `332/0/0/0`과 whitespace·민감정보 패턴 검사를 통과했다. 런타임에는 `SENTRY_DSN`, `SENTRY_ENVIRONMENT`, `SENTRY_RELEASE`가 필요하고 tracing은 기본 비활성 상태로 유지한다. 별도 Jira 이슈 키는 없다.
- **2026-08-12 — 종합 피드백 실패 복구 설계:** 기존 Jira `TMI-25`의 시험 단위 grading retry를 활용해 빈/null Summary feedback을 실패로 저장하고 선택적으로 재생성하는 계획을 작성했다. `generationAttempt`, generation별 멱등 키, Scheduler·dispatch·Callback fencing, `QUESTION_RESULT_MISSING` 복구, 기존 status/Summary 조회의 `FEEDBACK_GENERATION_FAILED` 노출과 Python AI 선배포 순서를 확정했다. 이 날짜의 범위는 계획·상태 문서 정리이며 애플리케이션 코드는 변경하지 않았다.
- **2026-08-13 — TMI-25 Summary 복구 구현:** Summary generation 재생성, stale worker/Callback 차단, null/empty feedback 실패 전이, 실제 최초 `ExamResult` 기반 Summary 준비 판정과 누락 Question 결과 재처리를 구현했다. 기존 body 없는 `POST /api/v1/exams/{examId}/grading/retry`, 공개 DTO·`BaseResponse`, `retryCount`, Question AI 계약, AI/Callback `user_id=examId`, Redis/S3와 소유권 검증 계약을 유지했다. 최종 `./gradlew clean test`는 `351/0/0/0`, `git diff --check`는 성공했다.
- 이번 정리는 문서만 변경했다. 애플리케이션·테스트·외부 시스템·운영 데이터·Jira 상태와 Git commit·push는 변경하지 않았고, 실제 Secret·Token도 조회하거나 기록하지 않았다.

## 2026-08-14 — 8월 11~13일 작업 내용 블로그 초안 정리

<!-- codex-turn:019ffdf4-e213-73c0-96e8-d62865e3d96c -->

- 사용자의 의도를 다시 확인해 2026-08-11부터 2026-08-13까지의 작업 내용을 저장소 문서용 요약이 아니라 대화창에서 활용할 블로그 글 초안으로 정리했다.
- 8월 11일은 Sentry 운영 보완 정책과 구현, 8월 12일은 Jira `TMI-25` 후속 종합 피드백 복구 설계, 8월 13일은 Summary generation 재생성과 누락 Question 결과 복구 구현·검증을 중심으로 구성했다.
- 이번 turn에서는 애플리케이션·테스트 코드를 변경하지 않았고 외부 API·AI Callback·Redis·S3 계약과 외부 시스템, Jira 상태, Git 상태를 변경하지 않았다. 문서 기록 외 변경이 없어 Gradle 테스트는 실행하지 않았다.

## 2026-08-17 — 파트별 점수의 재시도 포함 여부 분석

<!-- codex-turn:01a00fd1-387c-7fa1-8edc-5d26e57f9f04 -->

- 별도 Jira 이슈 키 없이 `GET /api/v1/exams/{examId}/summary` 응답의 `partScores` 산정 로직을 분석했다.
- `ExamServiceImpl.getExamSummary()`는 `examResultRepository.findByExamId(examId)`로 모든 `ExamResult`를 조회한 뒤, `questionNumber`와 `score`가 있는 모든 문서를 파트별로 그룹화해 합산한다. 점수 합산 필터에 `retryCount` 조건이 없으므로 최초 응시와 `retryCount>0` 재시도 점수가 모두 `partScores`에 더해진다.
- 반면 `totalSolvedQuestions`는 현재 `retryCount == 0`인 문서만 카운트하고, `totalScore`는 신규 `ExamSummary` 또는 legacy 종합 문서의 값을 그대로 사용하므로 재시도 점수 합산은 `partScores`에만 해당한다.
- 재시도를 제외해야 한다면 최초 응시만 합산할지, 문항별 최신/최고 점수를 사용할지 정책 확정과 회귀 테스트가 필요하다. 이번 turn은 분석과 작업 기록 갱신만 수행했고 애플리케이션·테스트 코드와 외부 API 계약을 변경하지 않았으며 Gradle 테스트는 실행하지 않았다.

## 2026-08-17 — 파트별 점수를 최초 응시로 제한

<!-- codex-turn:01a00fd4-81d7-7963-b1d6-dc54f600df85 -->

- 별도 Jira 이슈 키 없이 `GET /api/v1/exams/{examId}/summary` 응답의 `partScores`를 요청한 최초 응시 기준으로 수정했다.
- `ExamServiceImpl.getExamSummary()`의 파트별 합산 대상에 `retryCount == 0` 필터를 추가했다. `retryCount>0`인 재시도 결과와 `retryCount` 값이 없는 legacy 결과는 `partScores`에 포함되지 않는다.
- `ExamOwnershipServiceTest` 회귀 테스트에서 최초 응시 5점, 재시도 9점, `retryCount` 누락 결과 4점을 함께 제공하고 `partScores.part1=5.0`, part2 미포함, `totalSolvedQuestions=1`을 검증했다.
- 공개 API URL·Method·Request/Response DTO 필드·`BaseResponse`, `totalScore`, `totalSolvedQuestions`, `retryCount` 의미, AI/Callback `user_id=examId`, Redis/S3 계약과 소유권 검증은 변경하지 않았다.
- 특정 `ExamOwnershipServiceTest`와 최종 `./gradlew clean test`를 실행했고, 최종 tests/failures/errors/skipped `352/0/0/0`으로 성공했다. `git diff --check`도 성공했으며 기존 `ExamServiceImpl` unchecked 경고만 남았다.
- 이미 저장된 legacy `retryCount=null` 최초 응시 데이터가 있다면 새 기준에서 파트 점수에서 제외된다. 이번 요청의 명시적 기준은 `retryCount=0`이므로 별도 보정을 추가하지 않았다. 실제 외부 인프라나 데이터, Git commit·push는 변경하지 않았다.

## 2026-08-17 — 파트별 최초 응시 점수 집계 종료 기록 보완

<!-- codex-turn:01a00fd4-3831-7840-af22-6e6d67594e89 -->

- 별도 Jira 이슈 키 없이 이번 turn의 종료 hook에서 요구한 작업 기록을 보완했다.
- `GET /api/v1/exams/{examId}/summary`의 `partScores`는 `retryCount == 0`인 최초 응시 결과만 합산하도록 구현되었고, `retryCount>0` 재시도와 `retryCount=null` 결과는 제외된다.
- 회귀 테스트를 추가했으며 최종 `./gradlew clean test`는 tests/failures/errors/skipped `352/0/0/0`, `git diff --check`는 성공했다. 공개 API·DTO·`BaseResponse`, AI/Callback, Redis/S3 계약은 유지했다.
- 이 보완은 작업 기록 문서만 갱신했고 애플리케이션·테스트 코드, 외부 시스템, Git commit·push를 추가로 변경하지 않았다. Secret과 Token은 조회하거나 기록하지 않았다.

## 2026-08-17 — 파트별 최초 응시 점수 집계 구현 계획 정리

<!-- codex-turn:01a00fdc-3433-78b0-b2ca-37874a561258 -->

- 별도 Jira 이슈 키 없이 사용자에게 `partScores`를 `retryCount=0`인 최초 응시 기준으로 제한하는 구현 계획을 정리했다.
- 계획은 현행 `GET /api/v1/exams/{examId}/summary` URL·Method·Response DTO·`BaseResponse`를 유지하고, `ExamServiceImpl.getExamSummary()`의 `partScores` 집계 필터만 `retryCount == 0`으로 제한하는 최소 변경을 기준으로 한다.
- 회귀 검증은 최초 응시·재시도·`retryCount=null` 결과를 같은 시험에 구성해 최초 응시 점수만 파트별로 합산되는지 확인하고, `totalScore`·`totalSolvedQuestions`과 소유권 검증의 기존 동작을 유지하는 방식으로 구성한다.
- 최종 검증은 대상 단위 테스트, `./gradlew clean test`, `git diff --check`, 공개 계약 변경 여부 확인 순으로 수행하며, legacy `retryCount=null` 최초 응시 문서 제외을 배포 전 데이터 확인 사항으로 남긴다.
- 이번 turn은 이미 반영·검증된 구현의 계획을 설명하고 작업 기록 문서만 갱신했다. 애플리케이션·테스트 코드, 외부 시스템, Git commit·push를 추가로 변경하지 않았고 Secret·Token을 조회하거나 기록하지 않았다.

## 2026-08-17 — 파트별 최초 응시 점수 집계 구현 완료 확인

<!-- codex-turn:01a00fde-9f93-7233-b708-3dcd7de02b3a -->

- 별도 Jira 이슈 키 없이 사용자의 구현 요청을 기준으로 현재 작업 트리의 `partScores` 집계 코드와 회귀 테스트를 재확인했다.
- `ExamServiceImpl.getExamSummary()`는 `questionNumber`와 `score`가 있고 `retryCount == 0`인 `ExamResult`만 파트별로 합산한다. `retryCount>0` 재시도와 `retryCount=null` legacy 결과는 제외된다.
- `ExamOwnershipServiceTest.examSummaryPartScoresIncludeOnlyInitialAttempts()`는 최초 응시 5점만 `partScores`에 포함되고 재시도 9점과 null 회차 4점이 제외되는 것을 검증한다.
- 최종 `./gradlew clean test`를 재실행했고 tests/failures/errors/skipped `352/0/0/0`으로 성공했다. `git diff --check`도 성공했고 기존 `ExamServiceImpl` unchecked 경고만 남았다.
- 공개 API URL·Method·Request/Response DTO·`BaseResponse`, `totalScore`·`totalSolvedQuestions`, AI/Callback `user_id=examId`, `retryCount` 의미, Redis/S3와 소유권 계약을 유지했다. 실제 외부 인프라·데이터, Git commit·push는 변경하지 않았고 Secret·Token을 조회하거나 기록하지 않았다.
- 이번 turn에서는 요청한 코드와 테스트가 이미 현재 작업 트리에 존재해 애플리케이션 코드를 추가로 변경하지 않고 전체 검증과 작업 기록 갱신을 완료했다.

## 2026-08-17 — staging GitHub Actions 테스트 실패 수정

<!-- codex-turn:01a00fe8-7a65-7a20-976a-881330830841 -->

- 별도 Jira 이슈 키 없이 최신 staging GitHub Actions 실행 `32034974696`을 읽기 전용으로 확인했다. 실제 실패 step은 action의 Node.js 20 사용 중단 경고가 아니라 `Run tests`였고, `TosunsaengApplicationTests.sentryUsesSafeErrorOnlyConfiguration()` 1건이 실패했다.
- 원인은 workflow job 전역의 `SENTRY_RELEASE=app-back-end-learning-core@${{ github.sha }}`가 Spring test profile의 `sentry.release=app-back-end-learning-core@test`보다 높은 우선순위로 적용된 것이다. 그 결과 CI에서 테스트 assertion이 commit release 값을 받아 실패했다.
- `.github/workflows/deploy-staging.yml`의 `Run tests` step에만 `SENTRY_RELEASE=app-back-end-learning-core@test`를 명시해 테스트 설정을 격리했다. 배포 step에서는 기존 job 전역 commit release를 유지한다.
- Node.js 20 사용 중단 및 `setup-java v4` deprecated 경고를 제거하기 위해 `actions/checkout@v4`와 `actions/setup-java@v4`를 각각 `@v5`로 올렸다. 기타 AWS·Docker·ECS action과 배포 순서는 변경하지 않았다.
- CI와 동일하게 `SENTRY_RELEASE=app-back-end-learning-core@test` 환경에서 `./gradlew clean test --no-daemon`을 실행했고 tests/failures/errors/skipped `352/0/0/0`으로 성공했다. Ruby YAML parser 검증과 `git diff --check`도 성공했다. `actionlint`는 현재 환경에 설치되지 않아 실행하지 못했다.
- 공개 API·DTO·`BaseResponse`, AI/Callback, Redis/S3, ECS cluster·service·task definition·health URL 계약을 변경하지 않았다. workflow를 commit·push하거나 배포를 재실행하지 않았고 Secret·Token을 조회하거나 기록하지 않았다.
- 남은 확인 사항은 사용자가 변경을 commit·push한 뒤 GitHub-hosted `ubuntu-latest` runner에서 v5 action 초기화, 352개 테스트, AWS OIDC, ECR build/push, ECS 안정화와 health check가 순차적으로 성공하는지 확인하는 것이다.

## 2026-08-20 — Identity `UserMerged` consumer 구현 사전 검토

<!-- codex-turn:01a01d52-346d-7172-ad26-db7eac9317d7 -->

- 별도 Jira 이슈 키 없이 Identity Service의 `UserMerged` schema version 1 인계서를 현재 Learning Core `main` / `98730c9` 코드와 대조 검토했다. 작업 시작 시 worktree는 clean이었다.
- 직접 `userId` ownership은 `exam_sessions`, `exam_results`, `exam_summaries` 세 컬렉션에 있고, Question/Summary Job·Azure/SpeechAce 결과·Redis·S3는 `examId` 간접 귀속이므로 migration rewrite 대상이 아님을 확인했다.
- 구현 전 차단 사항으로 workload credential의 모든 TBD, source뿐 아니라 target guard도 획득하는 동시성 계약, 양쪽 활성 시험의 partial unique index 충돌 정책, Callback stale-owner 경합, merge 전에 발급된 S3 Presigned PUT URL의 최대 5분 잔여 권한, Mongo Transaction 지원과 direct transaction P99 검증을 기록했다.
- 현재 사용자용 단일 JWT SecurityFilterChain과 workload endpoint를 분리하고, 내부 endpoint는 별도 decoder/issuer/audience/principal 검증을 사용해야 한다. 204 빈 응답은 공개 `BaseResponse` 계약을 변경하지 않는다.
- content type과 payload 크기 실패의 `415`/`413`, source/target guard의 기존 MERGED 상태와 상충 event 처리, TLS 종료·network policy 책임 경계도 Identity와 구현 전 합의가 필요하다.
- 상세 inventory, 위험과 권장 구현 순서를 `docs/codex/USER_MERGED_CONSUMER_REVIEW.md`에 추가하고 `docs/codex/CURRENT_STATE.md`를 갱신했다. 애플리케이션·설정·테스트 코드는 변경하지 않아 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함 검증은 성공했다.
- 기존 공개 API URL·Method·Parameter·DTO·`BaseResponse`, 실제 userId 비노출, `retryCount`, Redis/S3 Key와 AI·Callback `user_id=examId` 계약을 유지하는 구현 방향이다. Git commit·push, Jira와 외부 인프라 변경은 수행하지 않았고 Secret·Token을 조회하거나 기록하지 않았다.

## 2026-08-20 — `UserMerged` 선행 계약 선택지와 확정 절차 정리

<!-- codex-turn:01a01d5b-e2f2-7240-9fb2-60c2af6f2590 -->

- 별도 Jira 이슈 키 없이 Identity 인계서와 Learning Core 사전 검토의 구현 차단 사항을 필수 불변식, 제품·보안 정책, 측정 후 결정, 배포·인프라 계약으로 분류했다.
- `docs/codex/USER_MERGED_CONTRACT_DECISIONS.md`를 추가해 workload 인증, direct/async 처리, source/target guard, 활성 시험과 history, Presigned PUT URL, Callback 경합, 상충 event, HTTP status, TLS/network, guard rollout의 선택지·장단점·권장안을 기록했다.
- 권장 기본 조합은 별도 비대칭 workload JWT, source/target 양쪽 guard, target 활성 우선과 source-only 활성 이전, history 합집합 보존, 기존 PUT URL의 최대 5분 잔여 capability 명시적 수용, Callback 단일 Transaction, producer 최종 target 불변식과 consumer fail-closed, `204` 빈 응답, publisher OFF 상태의 단계적 writer 전환이다.
- direct Transaction은 production 유사 staging에서 합의한 처리시간 기준을 통과할 때만 확정하고, 실패 시 timeout 연장이나 hybrid 처리 대신 durable inbox + worker로 계약을 개정하도록 decision gate를 두었다. P99 2초 이하는 5초 publisher timeout에 여유를 두기 위한 예시 기준이며 양 팀의 측정 승인값으로 최종 확정해야 한다.
- 제품·보안 공동 결정이 필요한 핵심 coupling은 활성 시험 정책과 merge 전 발급된 S3 PUT URL이다. source 유래 S3 write 가능성도 0이어야 하면 source 활성 시험을 항상 abandon하거나 revocable/nonce 업로드를 별도 승인 범위로 설계해야 한다.
- 기존 공개 API URL·Method·Parameter·DTO·`BaseResponse`, 클라이언트 실제 userId 비노출, `retryCount`, Redis Key/TTL, S3 Object Key, AI request/Callback `user_id=examId` 계약은 변경하지 않았다. 애플리케이션·설정·테스트 코드는 변경하지 않았고 외부 시스템, Git commit/push, Secret·Token을 다루지 않았다.

## 2026-08-20 — `UserMerged` 권장 계약 패키지 확정

<!-- codex-turn:01a01d7d-fd2f-7ce0-93ac-1a0e38d7c921 -->

- 별도 Jira 이슈 키 없이 사용자가 `docs/codex/USER_MERGED_CONTRACT_DECISIONS.md`의 권장 기본 패키지를 승인했다. 현재 브랜치는 `develop`이며 기존 미커밋 문서 작업을 보존했다.
- C1-A 별도 비대칭 workload JWT, C3-A source/target 양쪽 guard, C4-A target 활성 우선·source-only 활성 이전, C5-A history 합집합 보존, C6-A 기존 PUT URL 최대 5분 잔여 capability 수용, C7-A Callback 전체 Transaction, C8-A producer 최종 target 불변식·consumer fail-closed, C9 권장 status 표, C10-A TLS/network와 앱 인증 분리, C11-A 단계적 writer 전환을 구현 방향으로 확정했다.
- C2-A direct Transaction은 조건부 확정했다. Mongo Transaction 지원과 production 유사 staging의 공동 성능 기준을 통과하면 유지하고, 실패하면 timeout 연장이나 hybrid가 아니라 C2-B durable inbox + worker로 계약을 개정한다.
- 확정 문서에 구현 방향 승인과 production 활성화 승인을 구분했다. 실제 issuer, JWKS URI, audience, principal, TTL, clock skew, rotation 값은 Identity·인프라 공동 계약 전까지 미정으로 유지하며 임의의 값이나 Secret·Token을 기록하지 않았다.
- 사전 검토 문서의 완료 판정을 “구현 방향 확정, 운영값·측정 gate 이행 전”으로 갱신했다. Identity 인계서 반영, Mongo/P99, TLS/network, writer drain·guard backfill과 staging E2E 완료 전에는 publisher와 merge feature를 활성화하지 않는다.
- 기존 공개 API URL·Method·Parameter·DTO·`BaseResponse`, 실제 userId 비노출, AI request/Callback `user_id=examId`, `retryCount`, Redis Key/TTL, S3 Object Key와 음성 제출·Polling 흐름은 변경하지 않았다. 애플리케이션·설정·테스트 코드는 변경하지 않았다.

## 2026-08-20 — `UserMerged` consumer 최종 구현 계획 작성

<!-- codex-turn:01a01d82-ee8e-7f93-9ed2-1e9645fef09a -->

- 별도 Jira 이슈 키 없이 확정된 C1~C11 계약과 현재 `develop` 코드의 ownership/writer/security/Mongo 구성을 대조해 `docs/codex/USER_MERGED_CONSUMER_IMPLEMENTATION_PLAN.md`를 추가했다.
- 직접 migration 대상은 `exam_sessions`, `exam_results`, `exam_summaries`의 `userId`이며 Question/Summary Job, Azure/SpeechAce, Redis와 S3는 `examId` 간접 귀속으로 rewrite하지 않는 inventory를 유지했다.
- endpoint보다 먼저 Mongo Transaction/양쪽 guard를 도입하고 시험 생성·legacy 보정·Presigned PUT 발급·제출·재채점·Question/Summary Job과 Feedback/Summary/SpeechAce/Azure Callback writer를 guard 경계로 전환하도록 단계화했다.
- Mongo DB command와 S3 network·AI·Redis 외부 단계를 분리했다. S3Presigner의 PUT URL 로컬 서명은 guard와 같은 command에서 처리해 merge commit 뒤 새 capability가 발급되는 경합을 막고, 먼저 승인된 URL만 C6-A 최대 5분 잔여 위험에 포함하도록 계획했다.
- direct consumer Transaction의 inbox/digest, source/target 결정적 guard 획득, target 활성 우선, 세 컬렉션 owner rewrite, source MERGED deny와 `204`/`409`/`503` 수렴 순서를 구체화했다. Callback은 Session current owner를 재조회해 guard/result/Job과 함께 commit하도록 했다.
- 기존 `@Transactional`이 MongoTransactionManager 추가 후 의도치 않게 활성화되는 영향, active Transaction 안의 duplicate catch, unknown commit result, 외부 호출 장기 Transaction을 명시적 위험으로 잡고 TransactionTemplate 기반 command와 결정적 business key 재조회로 수렴하도록 계획했다.
- 별도 workload SecurityFilterChain, raw/chunked 4 KiB 제한, schema/digest 검증, migration dry-run/backfill/index, replica-set Mongo 동시성 테스트, staging Identity E2E/P99, 단계적 rollout과 processed merge 뒤 guard-unaware 구버전 rollback 금지를 완료 gate로 정했다.
- 기존 공개 API·DTO·`BaseResponse`, 실제 userId 비노출, AI/Callback `user_id=examId`, `retryCount`, Redis Key/TTL, S3 Object Key·Presigned URL·음성 제출·Polling 계약은 변경하지 않는 계획이다. 이번 작업은 문서 작성만 수행했으며 애플리케이션·설정·테스트 코드는 변경하지 않았다.

## 2026-08-20 — 1차 업데이트 Billing·Entitlement 경계와 `UserMerged` 변경 범위 검토

<!-- codex-turn:01a01d91-032c-7313-8807-948be96f37ea -->

- 관련 Identity Jira `TMI-90`, `TMI-95`, `TMI-98`의 계약 문서와 현재 Learning Core 계획을 대조했다. Billing/Learning Core 후속 Jira 키는 별도로 제공되지 않았으며 현재 브랜치는 `develop`이다.
- `docs/codex/FIRST_RELEASE_BILLING_BOUNDARY_REVIEW.md`를 추가해 SNS 로그인, 결제와 검증된 휴대전화 번호당 무료 모의고사 1회의 서비스 책임과 구현 순서를 정리했다.
- 결제와 무료 1회를 1차 production 범위에 포함하면 Billing 서버는 릴리스 이후가 아니라 선행·병렬 의존성이다. 초기에는 Billing과 Entitlement를 두 배포 서비스로 분리하지 않고 하나의 bounded context가 결제 원장, PG webhook, TrialClaim, UserEntitlement, reserve/confirm/cancel/reconcile와 merge 이전을 소유하는 안을 권장했다.
- Identity는 verified phone과 consumer-scoped eligibility binding event만 소유하고, binding 수신 자체는 혜택 지급이 아님을 유지했다. Billing이 retained candidate 기준 TrialClaim unique와 entitlement 원장을 관리하고 Learning Core는 시험 생성 전 reserve, Session commit 후 confirm하며 실패·timeout은 cancel/reconciliation으로 수렴한다.
- 현재 `UserMerged` consumer 최종 계획의 애플리케이션 코드 변경 주 대상은 Learning Core다. 다만 production에는 Identity의 C8/C9 문서 반영, workload credential·publisher 설정 또는 계약 미충족 코드 보완, 인프라 TLS/network/Mongo와 staging E2E가 필요하다. Billing의 entitlement 이전용 `UserMerged` consumer/fan-out은 현재 계획에 포함되지 않은 별도 서비스·Jira 범위다.
- Firebase/Identity Platform의 Kakao OIDC 관련 billing과 앱 결제 Billing 서버는 다른 개념임을 구분했다. 기존 공개 시험 API·DTO·`BaseResponse`, 실제 userId 비노출, AI/Callback `user_id=examId`, Redis/S3와 `retryCount` 계약은 변경하지 않았고 애플리케이션·설정·테스트 코드는 수정하지 않았다.
- 남은 제품 결정은 “인당 1회”를 검증된 휴대전화 번호당 1회로 승인할지 여부다. 실제 자연인 기준을 요구하면 여러 번호·번호 재할당을 다루는 KYC와 abuse/보존 정책이 별도 범위로 필요하다.

## 2026-08-20 — Billing/Entitlement 상품 확정 반영과 B3~B8 권장 계약

<!-- codex-turn:01a01d9e-6509-7523-9858-571d998c89da -->

- 관련 Identity Jira `TMI-95`, `TMI-98` 계약을 유지하며 사용자가 Billing/Entitlement를 새로운 단일 배포 서비스로 시작하고 검증된 휴대전화 번호당 무료 모의고사 1회를 적용하기로 확정했다. Billing/Learning Core 후속 Jira 키는 제공되지 않았고 현재 브랜치는 `develop`이다.
- 상품 기본안은 시험 1회 10 credits, 5천원 5 credits, 1만원 10 credits, 3만원 3일 무제한과 3일 출석 시 하루 연장, 5만원 100 credits, 첫 구매 credit 2배, 연속 로그인 `0,1,1,1,2,2,3`, 추천 code 10 credits와 coupon별 credit grant다.
- `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`를 추가해 immutable `CreditGrant` ledger, unlimited/free entitlement, grant allocation, 환불 group, 5분 reservation, Session commit 후 confirm saga, PG webhook 멱등 상태, Billing `UserMerged` owner 이전과 TrialClaim/abuse ledger 보존 권장안을 기록했다.
- 무료 1회는 일반 credits가 아니라 `FREE_EXAM_ONCE` entitlement로 두고 Identity consumer-scoped candidate의 `TrialClaim` unique로 검증 번호당 한 번을 보장하는 방향을 권장했다. merge·탈퇴·환불로 first-purchase와 free claim eligibility를 다시 열지 않는다.
- Learning Core 시험 생성의 이중 차감 방지를 위해 operation ID와 idempotent reserve/confirm/cancel이 필요하다. 기존 Request Body 없음은 유지하되 optional `Idempotency-Key` header를 추가하는 안이 권장이며 이는 외부 계약 추가이므로 별도 명시적 승인이 필요하다고 기록했다.
- 아직 확정하지 않은 항목은 첫 구매 2배의 SKU 범위, 무제한권 시작·출석·재구매, streak cycle/reset, 추천 양쪽 지급·첫 결제 gate, paid/promotional 만료, 부분 환불·chargeback, PG사, coupon stacking과 TrialClaim 법무 보존이다.
- 기존 공개 API·DTO·`BaseResponse`, 실제 userId 비노출, AI/Callback `user_id=examId`, Redis/S3 Key와 `retryCount`는 변경하지 않았다. 이번 작업은 계약 분석과 문서 갱신만 수행했으며 애플리케이션·설정·테스트 코드는 수정하지 않았다.

## 2026-08-20 — 모바일 결제 채널 선택 검토

<!-- codex-turn:01a01da5-c158-7c33-b126-d08c7be43132 -->

- 현재 제품이 앱용 Learning Core이고 시험 credit와 무제한권이 앱에서 소비되는 디지털 상품이라는 범위를 기준으로 결제 채널 선택을 검토했다. 관련 Identity Jira는 `TMI-90`, `TMI-95`, `TMI-98`이고 Billing/Learning Core 후속 Jira 키는 제공되지 않았다.
- 1차 출시는 Apple In-App Purchase와 Google Play Billing을 지원하고 웹 PG는 후속으로 추가하는 방향을 권장했다. 웹 PG까지 동시에 열면 checkout, webhook, 환불과 검증 경로가 추가되고 앱 내 디지털 상품의 외부 결제 유도는 배포 국가별 스토어 정책 위험이 있으므로 1차 범위를 늘리지 않는 판단이다.
- Billing은 provider별 검증 adapter와 단일 내부 주문·entitlement 원장을 사용해 향후 웹 PG를 추가할 수 있게 설계한다. 스토어 서버 검증·notification·환불/취소 이벤트를 권한 지급의 근거로 삼고 클라이언트 성공 화면만으로 지급하지 않는 기존 권장 계약을 유지한다.
- 이는 결제 채널 권장안이며 사용자의 최종 승인은 아직 받지 않았다. 실제 출시 국가·플랫폼과 최신 Apple/Google 정책, 수수료 프로그램은 출시 직전에 다시 확인해야 한다.
- 문서만 수정했으며 애플리케이션·설정·테스트 코드는 변경하지 않았다. `git diff --check`로 문서 형식을 검증하고 marker가 정확히 한 번 존재하는지 확인한다.

## 2026-08-20 — 인앱결제 및 P1·P2·P4 제품 계약 확정

- 사용자가 Apple In-App Purchase와 Google Play Billing만 사용하고 웹 PG를 현재 범위에서 제외하기로 확정했다. 관련 Identity Jira는 `TMI-90`, `TMI-95`, `TMI-98`이고 Billing/Learning Core 후속 Jira 키는 제공되지 않았다.
- 첫 구매 2배는 verified phone 기준 첫 credit 상품에 적용하며 `CREDIT_100` 첫 구매는 200 credits다. unlimited pass 선구매는 bonus 자격을 소진하지 않고 merge·탈퇴·환불로 자격을 다시 열지 않는 P1 계약을 확정했다.
- 3만원 pass는 구매 후 30일 안의 첫 reserve부터 72시간 사용하고 서로 다른 KST 3일의 Billing check-in을 완료하면 24시간 한 번 연장한다. 재구매 pass 별도 보존·순차 활성화와 미활성·미사용 pass만 환불하는 P2 계약을 확정했다.
- 추천 code 입력자와 추천인에게 각각 10 credits를 입력자의 verified phone 인증과 첫 유료 인앱결제 `CAPTURED` 후 한 번 지급한다. phone당 입력 1회, self-referral 금지, abuse 보류와 환불 시 revoke/debt를 포함하는 P4 계약을 확정했다.
- P3 연속 로그인 cycle/reset, credit 만료와 부분 환불, optional `Idempotency-Key`, coupon과 TrialClaim 보존·번호 재할당 정책은 아직 확정되지 않았다. Apple·Google의 실제 product ID·상품 유형·가격 구간과 최신 배포 국가 정책도 구현 전에 확인해야 한다.
- `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`와 `docs/codex/CURRENT_STATE.md`를 갱신했으며 애플리케이션·설정·테스트 코드는 변경하지 않았다. 문서 형식은 `git diff --check`로 검증한다.

## 2026-08-20 — 인앱결제 전용 및 상품 세부 계약 승인 반영

<!-- codex-turn:01a01da7-7170-78d2-9c06-6c235fe99d0a -->

- 사용자가 결제 채널을 Apple In-App Purchase와 Google Play Billing로 한정하고 웹 PG를 제외하는 안을 최종 승인했다. 관련 Identity Jira는 `TMI-90`, `TMI-95`, `TMI-98`이며 Billing/Learning Core 후속 Jira 키는 제공되지 않았다.
- 함께 승인된 항목은 verified phone 기준 첫 credit 상품 2배, 5만원 첫 구매 시 총 200 credits, 무제한권 선구매 시 bonus 자격 유지, 3만원 pass의 첫 reserve 기준 72시간과 KST 3일 출석 시 24시간 연장, 추천 입력자·추천인 각 10 credits와 전화번호 인증·첫 결제 완료 gate다.
- 확정 내용을 `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`와 `docs/codex/CURRENT_STATE.md`에 반영했다. 기존 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3와 `retryCount` 계약은 변경하지 않았다.
- 애플리케이션·설정·테스트 코드는 변경하지 않았으므로 Gradle 테스트는 실행하지 않았고 문서 형식과 marker 단일 포함 여부만 검증한다.

## 2026-08-20 — 확정 Billing 계약의 Learning Core·Identity 변경 영향 검토

- 현재 `develop`의 Learning Core 시험 생성 코드, `UserMerged` 최종 계획과 Identity `develop`의 TMI-96 PhoneEligibilityBinding publisher·TMI-98 UserMerged publisher 구현을 읽기 전용으로 대조했다. 관련 Identity Jira는 완료된 `TMI-95`, `TMI-96`, `TMI-98`이며 Billing/Learning Core 후속 Jira 키는 제공되지 않았다.
- Learning Core `UserMerged` 전용 계획은 학습 데이터 owner 이전 범위로 유지하고 Billing 코드를 포함하지 않는 것이 맞다. 대신 시험 생성 전에 Billing reserve, Session Transaction commit 뒤 confirm, rollback 시 cancel과 불명확한 상태 reconciliation을 다루는 별도 Learning Core–Billing 구현 계획·Jira가 필요하다고 판단했다.
- 현재 `ExamServiceImpl.createExamSession()`과 `ExamSessionManager.startNew()`은 Billing gate 없이 기존 활성 Session을 abandon하고 새 Session을 즉시 insert한다. 결제 적용 시 operation ID 멱등성, Session 내부 reservation metadata, durable confirm/cancel retry, 오류·timeout·재시작과 merge 경합 테스트가 추가돼야 하며 기존 공개 Request Body·Response DTO와 AI/Redis/S3 계약은 유지한다.
- Identity PhoneEligibilityBinding publisher는 외부 Billing consumer를 이미 전제하므로 상품 가격, Apple/Google 결제, 첫 구매·추천을 위해 Identity payload나 공개 API를 확장할 필요가 없다. first-purchase/referral/trial unique와 login check-in은 Billing이 소유한다.
- 현재 Identity `UserMergedPublisherProperties`는 endpoint/audience 하나이고 `UserMergedOutbox`도 event 전체의 delivery status 하나만 가진다. Learning Core와 Billing에 독립 전달하려면 consumer별 delivery record와 allowlist를 추가하거나 별도 durable fan-out broker를 도입해야 하며, 현재 direct HTTPS 구조에서는 consumer별 delivery record 확장을 권장한다.
- `docs/codex/FIRST_RELEASE_BILLING_BOUNDARY_REVIEW.md`와 `docs/codex/CURRENT_STATE.md`를 갱신했다. Identity 저장소와 양쪽 애플리케이션·설정·테스트 코드는 수정하지 않았고 분석 작업이므로 Gradle 테스트는 실행하지 않았다. 종료 전 `git diff --check`를 실행한다.

## 2026-08-20 — Learning Core·Identity 변경 필요사항 최종 기록

<!-- codex-turn:01a01da9-df7f-7ca3-9cef-f94b4f3217ef -->

- 확정된 Billing/Entitlement·인앱결제 계약이 기존 Learning Core `UserMerged` 구현 계획과 Identity publisher에 미치는 영향을 최종 정리했다. 관련 Identity Jira는 완료된 `TMI-95`, `TMI-96`, `TMI-98`이며 Billing/Learning Core 후속 Jira 키는 아직 없다.
- Learning Core `UserMerged` 계획은 학습 데이터 owner 이전 범위로 유지하고, Billing reserve→Session commit→confirm/cancel/reconcile와 operation 멱등성을 위한 별도 연동 구현 계획·Jira를 추가해야 한다.
- Identity PhoneEligibilityBinding payload는 변경하지 않는다. 현재 단일 endpoint/audience와 event당 상태 하나인 `UserMerged` publisher는 Learning Core와 Billing의 consumer별 delivery 상태·retry·dead-letter를 갖도록 확장해야 한다.
- 기존 공개 시험 API·DTO·`BaseResponse`, 실제 userId 비노출, AI/Callback `user_id=examId`, Redis/S3와 `retryCount` 계약은 유지한다. optional `Idempotency-Key` header, Billing 오류·retry semantics와 Identity fan-out 후속 Jira가 다음 승인·계획 대상이다.
- 분석 문서와 CURRENT_STATE만 갱신했으며 애플리케이션·설정·테스트 코드는 변경하지 않았다. Gradle 테스트는 실행하지 않았고 `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-20 — Identity·Billing·Learning Core 구현 및 연동 순서 확정

<!-- codex-turn:01a01daf-b782-7b50-98af-e205b5b1a9ac -->

- 관련 Identity Jira는 완료된 `TMI-95`, `TMI-96`, `TMI-98`이며 Billing/Learning Core 후속 Jira 키는 아직 없다. 사용자가 제안한 Identity 수정, Learning Core 계획 반영, Billing 생성, Learning Core 구현과 전체 연동 순서를 현재 계약·코드 경계에 맞게 검토했다.
- 전체를 완전히 직렬화하지 않고 Phase 0에서 Identity phone binding, multi-consumer `UserMerged`, Learning Core–Billing reserve/confirm/cancel/status, Apple/Google transaction과 optional idempotency/error 계약을 먼저 동결한 뒤 Jira를 서비스별로 분리하는 순서를 권장했다.
- Phase 1에서는 Identity consumer별 fan-out, Billing service foundation·binding consumer·ledger/reservation, Learning Core 기존 `UserMerged` 구현과 별도 Billing 연동 계획/contract stub, Client IAP를 feature flag OFF 상태로 병렬 진행한다. Identity 변경을 먼저 병합해도 Billing consumer 준비 전 publisher는 활성화하지 않는다.
- 이후 Billing 핵심과 Learning Core saga/reconciliation, Billing `UserMerged` consumer를 완성하고 Apple·Google adapter와 sandbox E2E를 진행한다. staging 활성화는 consumer endpoint/보안→phone eligibility→무료시험→두 `UserMerged` delivery→store 결제/환불→Billing enforcement 순이며 signup/Guest merge production flag는 마지막이다.
- `docs/codex/FIRST_RELEASE_BILLING_BOUNDARY_REVIEW.md`와 `docs/codex/CURRENT_STATE.md`를 갱신했다. 애플리케이션·설정·테스트 코드는 변경하지 않았고 기존 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3와 `retryCount` 계약을 유지했다. 문서 분석 작업이므로 Gradle 테스트는 실행하지 않았으며 `git diff --check`를 수행한다.

## 2026-08-20 — 시험 생성 멱등성과 Billing 공개 오류 계약 선택지 정리

- 관련 Identity Jira는 완료된 `TMI-95`, `TMI-96`, `TMI-98`이며 Billing/Learning Core 후속 Jira 키는 아직 없다. 현재 시험 생성 API, `BaseResponse`, `ErrorStatus`와 확정된 5분 reservation·reconciliation 계약을 기준으로 구현 전 선택사항을 분석했다.
- `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`에 key 생성 주체, 형식/scope, 보존기간, 동일 key 재호출 결과와 public 오류 mapping, 사용권 부족 status, processing/infra 구분, confirm 불명 처리의 선택지·장단점을 추가했다.
- 권장안은 기존 호환성을 위해 API header는 optional로 두되 신규 앱이 UUID v4를 필수 전송하고 `(canonicalUserId, operationType, key)`로 판정하는 것이다. operation ID는 Session 수명 동안 유지하고 terminal command는 7일 보존하며, 완료 replay는 기존 성공 DTO를 반환하고 처리 중은 409와 `Retry-After`를 사용한다.
- Billing 내부 오류는 그대로 노출하지 않고 사용권 부족 402, eligibility/payment/exam processing 409, rate limit 429, Billing 장애·confirm 불명 503으로 mapping한다. 자동 재시도는 processing/429/503만 같은 key로 수행하며 confirm 불명 Session은 삭제하지 않고 reconciliation으로 수렴하는 패키지를 권장했다.
- 이는 선택지 제시 단계로 아직 사용자 승인을 받지 않았다. 기존 시험 생성 Request Body·성공 Response DTO와 `BaseResponse`, 실제 userId 비노출, AI/Callback `user_id=examId`, Redis/S3와 `retryCount` 계약은 유지한다.
- 문서와 CURRENT_STATE만 갱신했고 애플리케이션·설정·테스트 코드는 변경하지 않아 Gradle 테스트는 실행하지 않았다. 종료 전 `git diff --check`를 실행한다.

## 2026-08-20 — Idempotency-Key·Billing 오류 계약 선택지 기록

<!-- codex-turn:01a01db2-35ed-7de1-959b-73d03b0e3450 -->

- 사용자가 시험 생성 `Idempotency-Key`와 Billing 공개 오류 계약에서 확정해야 할 항목별 선택지와 장단점을 요청했다. 관련 Identity Jira는 완료된 `TMI-95`, `TMI-96`, `TMI-98`이며 Billing/Learning Core 후속 Jira 키는 아직 없다.
- key 생성 주체, UUID v4와 user/operation scope, Session/command 보존기간, 완료 replay·processing 응답과 오류 mapping 수준, 사용권 부족 HTTP status, 정상 처리 지연과 장애 구분, confirm 결과 불명 수렴 방식을 A/B/C 선택지로 정리했다.
- 권장 패키지는 공개 header optional·신규 앱 필수, `(canonicalUserId, operationType, key)` unique, Session 수명 mapping과 terminal command 7일, 완료 200 replay·processing 409다. Billing 오류는 사용권 부족 402, processing 409, rate limit 429, 장애·confirm 불명 503으로 mapping하고 `Retry-After`와 same-key retry를 사용한다.
- 선택지는 `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`와 `docs/codex/CURRENT_STATE.md`에 반영했지만 사용자의 최종 승인은 아직 받지 않았다. 기존 시험 생성 Request Body·성공 DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3와 `retryCount` 계약은 유지한다.
- 애플리케이션·설정·테스트 코드는 변경하지 않아 Gradle 테스트는 실행하지 않았다. `git diff --check`와 현재 marker 단일 포함 여부를 검증한다.

## 2026-08-20 — 앱 종료·미완료 시험의 Session과 idempotency 구분

<!-- codex-turn:01a01dbc-d874-7312-b735-d93ff41b90ac -->

- 사용자가 시험 도중 앱을 종료한 뒤 다시 문제를 푸는 경우 Session ID와 `Idempotency-Key` 관계를 질문했다. 관련 Identity Jira는 완료된 `TMI-95`, `TMI-96`, `TMI-98`이며 Billing/Learning Core 후속 Jira 키는 아직 없다.
- idempotency key는 한 번의 Session 생성 command를 식별하므로 서로 다른 Session이 같은 key를 공유하지 않는다고 정리했다. 일반 앱 종료는 새 Session이 아니라 기존 `IN_PROGRESS` Session 이어풀기이며 새 reservation·차감이 없다. 생성 response loss일 때만 최초 key replay로 같은 examId를 반환한다.
- 명시적 새 응시는 새 key·새 examId와 별도 entitlement 소비다. 기술적 손상으로 기존 Session 복구가 불가능할 때만 새 key·새 examId를 기존 consumption에 연결하는 제한된 replacement 정책을 사용할 수 있으나 일반 종료에 적용하면 문제지 preview abuse와 Callback 경합이 생겨 비권장이다.
- 현재 `ExamSessionManager.startNew()`이 매 create마다 기존 active Session을 abandon하는 동작은 이 정책과 충돌한다. Billing 연동 구현 전에 active Session 복구와 명시적 새 응시 command를 분리하고, app persistent examId/key 또는 신규 active-session 조회 API 중 복구 계약을 확정해야 한다.
- `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`와 `docs/codex/CURRENT_STATE.md`를 갱신했다. 애플리케이션·설정·테스트 코드는 변경하지 않아 Gradle 테스트는 실행하지 않았고 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-20 — 이어풀기 제외와 미완료 시험 새 Session 정책 반영

- 사용자가 기존 채점 결과와 진행 상태 복구 오류를 피하기 위해 이어풀기를 제공하지 않고, 앱 재실행 후 무조건 새 시험 Session을 처음부터 시작하도록 제품 방향을 확정했다. 관련 Identity Jira는 완료된 `TMI-95`, `TMI-96`, `TMI-98`이며 Billing/Learning Core 후속 Jira 키는 아직 없다.
- 기존 미완료 Session은 `ABANDONED_RESTARTED`로 종료하고 새 `Idempotency-Key`와 새 examId를 사용한다. 이전 결과·upload·grading Job·summary를 새 Session에 복사하지 않으며 원 Session의 늦은 Callback은 abandoned 정책으로 no-op하고 감사 자료는 즉시 삭제하지 않는다.
- 생성 response loss의 transport retry만 기존 key로 같은 Session을 반환한다. 앱 재실행에 따른 새 시작은 반드시 새 key를 사용하며, 반복 restart로 문제지를 탐색하지 못하도록 replacement Session에는 동일 mockExamId를 유지하는 방향을 제안했다.
- 추가 차감 정책은 매 restart 새 차감(R1), 최초 시작 후 24시간 내 최대 3회 무료 replacement와 이후 새 차감(R2 권장), 완료까지 무제한 무료 replacement(R3)로 나눴다. 시작 방식은 확정됐지만 차감 정책은 사용자 승인이 더 필요하다.
- 현재 `ExamSessionManager.startNew()`의 active abandon 방향은 제품 결정과 맞지만 Billing replacement authorization·새 Session insert와의 장애 수렴이 추가돼야 한다. 기존 공개 API·성공 DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3와 retryCount 계약은 유지한다.
- `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`와 `docs/codex/CURRENT_STATE.md`만 갱신했고 애플리케이션·설정·테스트 코드는 변경하지 않아 Gradle 테스트는 실행하지 않았다. 종료 전 `git diff --check`를 수행한다.

## 2026-08-20 — 미완료 시험 무조건 새 Session 결정 기록

<!-- codex-turn:01a01dbf-5332-71e0-8bf2-6a02c1df45bd -->

- 사용자가 기존 채점 결과 복구와 진행 상태 오류를 피하기 위해 이어풀기를 완전히 제외하고 앱 재실행 뒤 항상 새 Session으로 처음부터 시작하는 정책을 확정했다. 관련 Identity Jira는 완료된 `TMI-95`, `TMI-96`, `TMI-98`이며 Billing/Learning Core 후속 Jira 키는 아직 없다.
- 이전 미완료 Session은 `ABANDONED_RESTARTED`, 새 시작은 새 UUID `Idempotency-Key`와 새 examId를 사용한다. 이전 결과·upload·Job·summary를 승계하지 않고 늦은 Callback은 abandoned no-op하며, 생성 HTTP 응답 유실의 transport retry에서만 같은 key로 같은 Session을 반환한다.
- replacement는 동일 mockExamId를 유지해 반복 restart로 다른 문제지를 탐색하지 못하게 하는 방향을 권장했다. 추가 차감은 R1 매번 차감, R2 최초 시작 후 24시간 내 최대 3회 무료, R3 완료 전 무제한 무료 중 아직 미확정이며 R2를 권장했다.
- 계약 결정서와 CURRENT_STATE는 이 상태로 최신화됐다. 애플리케이션·설정·테스트 코드는 변경하지 않았고 기존 공개 API·성공 DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3와 retryCount 계약을 유지했다.
- 문서 분석 작업이므로 Gradle 테스트는 실행하지 않았다. `git diff --check`와 현재 marker 단일 포함 여부를 검증한다.

## 2026-08-20 — 완료 전 무제한 무료 재시작과 idempotency 역할 확정

- 사용자가 R3 완료 전 무제한 무료 replacement를 선택하고 이 경우 `Idempotency-Key`가 필요한지 질문했다. 관련 Identity Jira는 완료된 `TMI-95`, `TMI-96`, `TMI-98`이며 Billing/Learning Core 후속 Jira 키는 아직 없다.
- 최초 시험 시작에서 10 credits, free claim 또는 pass usage를 한 번 소비해 OPEN AttemptGroup을 만들고, 이후 replacement Session은 같은 entitlement consumption과 고정 mockExamId에 연결해 추가 차감·횟수·시간 제한 없이 생성하는 R3 계약을 확정했다. group당 active Session은 하나이며 완료 뒤 새 시험은 새 consumption/group이 필요하다.
- 무료 replacement여도 key가 없으면 한 번의 restart 요청과 response loss 재전송이 E2·E3 두 Session을 연속 생성할 수 있다. 따라서 의도적인 각 restart는 새 key, 같은 restart의 transport retry는 같은 key를 사용해 Session 생성·Billing authorization·active pointer를 하나로 수렴시킨다.
- AI Summary 지연·실패가 무료 restart 권한을 계속 열지 않도록 AttemptGroup 완료를 모든 필수 문항 `retryCount=0` 제출이 정상 접수된 시점으로 분리하는 안을 권장했다. 현재 Session `COMPLETED`는 Summary Callback 뒤 전환되므로 `submissionCompletedAt` 같은 내부 marker가 필요하며 이 완료 시점 정의는 사용자 최종 승인이 남았다.
- `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`와 `docs/codex/CURRENT_STATE.md`를 갱신했다. 기존 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3와 retryCount 계약은 유지하며 애플리케이션·설정·테스트 코드는 수정하지 않았다.
- 문서 분석만 수행해 Gradle 테스트는 실행하지 않았고 종료 전 `git diff --check`를 수행한다.

## 2026-08-20 — R3 확정 및 Idempotency 유지 계약 기록

<!-- codex-turn:01a01dc1-e4f7-7660-8c05-de321e4730b8 -->

- 사용자가 완료 전 무제한 무료 replacement R3를 최종 선택했고, 무료 재시작에서 `Idempotency-Key`가 계속 필요한 이유를 Session 생성과 entitlement 소비를 분리해 설명했다. 관련 Identity Jira는 완료된 `TMI-95`, `TMI-96`, `TMI-98`이며 Billing/Learning Core 후속 Jira 키는 아직 없다.
- 최초 시작은 entitlement를 한 번 소비해 OPEN AttemptGroup을 만들고, 이후 의도적인 restart마다 새 key·새 examId를 같은 group에 연결한다. 같은 restart 요청의 response loss 재전송만 같은 key를 사용해 기존 Session을 반환하며 추가 차감은 없다.
- key가 없으면 한 번의 restart와 재전송이 E2와 E3를 연속 생성해 active pointer, Redis·S3·Job·Callback 혼선을 만들 수 있으므로 무료 여부와 무관하게 멱등 계약을 유지한다. group당 active Session은 하나이고 동일 mockExamId를 유지한다.
- AttemptGroup 완료 시점은 AI Summary 완료가 아니라 모든 필수 문항 `retryCount=0` 제출의 정상 접수 시점으로 권장했으며 이 세부 기준은 사용자 최종 승인이 남았다. 계약 결정서와 CURRENT_STATE는 이 상태로 최신화했다.
- 애플리케이션·설정·테스트 코드는 변경하지 않았고 기존 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3와 retryCount 계약을 유지했다. Gradle 테스트는 실행하지 않았으며 `git diff --check`와 현재 marker 단일 포함을 검증한다.

## 2026-08-20 — main 배포 Callback 미저장 원인 분석

<!-- codex-turn:01a01f94-09b5-7df1-bdf1-f900d769ef6f -->

- 현재 로컬 `main`과 `develop`이 동일한 `98730c9`를 가리키는 것을 확인하고, 배포 기준 `main`의 Feedback/Summary·SpeechAce·Azure Callback 저장 흐름을 정적으로 분석했다. 별도 신규 Jira 키는 없으며 관련 기존 범위는 `TMI-25`다.
- 가장 유력한 원인은 2026-08-17 `a2c4fb6`에서 도입된 Summary generation fencing이다. Summary 요청에는 `generation_attempt`를 보내지만 Python AI가 Callback에 같은 값을 echo하지 않거나 현재 `summary_grading_jobs.generationAttempt`와 다른 값을 보내면 `isCurrentSummaryGeneration()`이 false가 되고, Callback은 stale no-op으로 종료돼 `exam_summaries`에 저장되지 않는다.
- 이 경로는 의도적으로 HTTP 성공 응답을 유지하고 `missing_generation`·`generation_mismatch` 로그도 DEBUG라 운영 INFO 로그만 보면 Callback은 수신됐지만 저장 실패 원인이 보이지 않을 수 있다. 테스트 `summaryCallbackWithoutGenerationIsStaleNoOp`도 generation 없는 Summary Callback의 insert 0회를 계약으로 고정한다.
- 그 밖의 저장 없는 정상 응답 조건은 폐기된 ExamSession, 이미 저장된 중복 Callback, null/empty `part_feedback`, completion claim 상실이다. Summary 결과는 `exam_results`가 아니라 `exam_summaries`, 문항은 `exam_results`, SpeechAce는 `speechace_results`, Azure는 `azure_results`에 저장하므로 조회 컬렉션도 구분해야 한다.
- 운영 Secret·Token·실제 Callback payload나 DB를 조회하지 않았고 애플리케이션·설정·테스트 코드는 변경하지 않았다. 정적 분석 작업이라 Gradle 테스트는 실행하지 않았으며 외부 시스템·Jira 상태·Git commit·push를 변경하지 않았다.

## 2026-08-20 — Callback 저장 로그와 Mongo 문항 수 불일치 분석

<!-- codex-turn:01a01f98-1eff-7c53-9b94-08b4152aee90 -->

- 사용자가 제공한 운영 로그 발췌를 분석했다. 별도 신규 Jira 키는 없고 관련 기존 채점 멱등 범위는 `TMI-25`다. 실제 운영 DB나 Secret·Token·Callback payload 원문은 조회하지 않았다.
- 로그에는 서로 다른 두 시험이 섞여 있다. `ex_da814c87a9_0820_1425`는 14:25 UTC 이후 새로 생성되어 문항 1~5의 저장 완료가 기록됐고, 문항 9~11 저장 완료와 Summary 예약은 이전 시험 `ex_3871c98953_0820_1412`에 속한다. 따라서 최신 examId로 Mongo를 조회했을 때 1~5만 보이는 것은 첨부 로그와 일치한다.
- 이전 시험의 `grading.summary.trigger outcome=scheduled completedQuestionCount=11 expectedQuestionCount=11` 로그는 서비스가 그 examId의 `exam_results`를 실제 조회해 최초 응시 1~11번 결과를 모두 확인한 뒤 기록된다. 문항 저장 INFO도 `examResultRepository.insert()`가 정상 반환된 다음에만 남으므로 제공 자료 안에서는 저장 후 유실 증거가 없다.
- 애플리케이션 런타임에는 `exam_results` 삭제 경로가 없음을 정적으로 확인했다. 운영 확인은 두 examId를 각각 분리해 조회하고, 결정적 `_id=feedback:<examId>:<questionNumber>:0`과 연결된 `exam_sessions`를 비교해야 한다. 그래도 문서가 없다면 앱이 사용하는 `MONGODB_DATABASE`와 조회 중인 Atlas database/cluster가 같은지 확인해야 한다.
- 코드·설정·테스트와 외부 API·AI Callback·Redis/S3 계약은 변경하지 않았다. 분석 작업이라 Gradle 테스트는 실행하지 않았고 외부 시스템·Jira 상태·Git commit·push도 변경하지 않았다.

## 2026-08-20 — Summary 미저장 원인 재확인

<!-- codex-turn:01a01f9a-7356-7d72-883e-c97cc13f7d2f -->

- 사용자가 문항 결과는 서로 다른 examId를 혼동한 것이었음을 확인했고, 남은 Summary 미저장 현상이 앞서 분석한 `generation_attempt` fencing 때문인지 재확인했다. 별도 신규 Jira 키는 없으며 관련 기존 범위는 `TMI-25`다.
- 제공 로그에는 `summary:ex_3871c98953_0820_1412:v1` 요청이 `generationAttempt=1`로 전송된 기록은 있지만 Summary Callback 저장 완료 로그는 없다. Python AI Callback에 `generation_attempt`가 없으면 현재 main은 `missing_generation` stale no-op으로 HTTP 성공 응답만 반환하므로 현재 현상과 일치한다.
- 확정 확인은 Callback의 `generation_attempt=1` 포함 여부와 `summary_grading_jobs` 상태로 한다. Job이 PROCESSING이면 미도착·generation 누락/불일치·completion claim 잔류 가능성, FAILED와 `FEEDBACK_GENERATION_FAILED`이면 null/empty `part_feedback`, `exam_summaries` 문서와 저장 INFO가 있으면 정상 저장이다.
- 코드·설정·테스트·외부 계약과 외부 시스템·Jira·Git 상태는 변경하지 않았다. 실제 운영 payload·DB는 조회하지 않았고 분석 작업이라 Gradle 테스트는 실행하지 않았다.

## 2026-08-20 — web-ai Summary generation 배포 상태 확인

- 비공개 `Too-Much-I/web-ai` 저장소의 최신 `main`과 배포 workflow를 읽기 전용으로 확인했다. 별도 신규 Jira 키는 없고 Learning Core 측 관련 기존 범위는 `TMI-25`다.
- 최신 main은 `ef060d2`이며 GitHub Actions run `32333161432`가 2026-08-20 성공했다. workflow는 main push 시 offline test와 Compose 검증 후 Docker Hub에 SHA·latest 이미지를 게시하고, EC2에서 `ai-server`와 4개 `ai-worker`를 강제 재생성한다. 따라서 최신 main은 이미 배포된 상태다.
- 최신 AI 코드의 `parse_evaluation_request`, Redis job payload, sync/worker Summary 처리, `build_summary_response`, `build_feedback_callback_payload` 어느 경로에도 `generation_attempt` 보존·echo가 없다. 같은 main을 다시 배포해도 Summary 미저장은 해결되지 않으며, AI 코드가 Learning Core 요청값을 end-to-end 전달하도록 수정·테스트한 뒤 main에 병합해야 한다.
- 저장소와 Actions는 읽기 전용으로만 확인했고 commit·push·PR·workflow rerun·EC2 변경은 수행하지 않았다. Learning Core 코드·설정·테스트와 외부 계약도 변경하지 않았으며 Gradle 테스트는 실행하지 않았다.

## 2026-08-20 — web-ai 배포 안내 turn 기록 보완

<!-- codex-turn:01a01f9b-b8b9-7c93-b763-06bcc0c19023 -->

- 종료 훅 요구에 따라 직전 `web-ai` 배포 상태 확인과 사용자 안내 내용을 현재 turn 기록으로 보완했다. 별도 신규 Jira 키는 없으며 Learning Core Summary generation 관련 기존 범위는 `TMI-25`다.
- `web-ai` 최신 main `ef060d2`와 GitHub Actions run `32333161432`가 이미 성공적으로 배포됐지만, 최신 코드 자체에 `generation_attempt`의 JSON parsing, Redis payload, sync/worker 처리, Summary response와 Callback echo가 없음을 확인했다.
- 해결 절차는 AI 저장소에서 이 값을 end-to-end 보존하도록 구현하고 sync·Redis Callback 계약 테스트와 전체 offline test·Compose 검증을 통과시킨 뒤 PR을 main에 병합하는 것이다. main push workflow가 Docker SHA/latest 게시와 EC2 `ai-server`·`ai-worker` 교체를 자동 수행하므로 같은 기존 이미지만 재배포해서는 해결되지 않는다.
- 외부 저장소·Actions는 읽기 전용으로만 확인했고 실제 commit·push·PR·workflow rerun·EC2 변경은 수행하지 않았다. Learning Core 애플리케이션·설정·테스트와 외부 계약도 변경하지 않았으며 Secret·Token을 조회하거나 기록하지 않았다.

## 2026-08-20 — legacy Summary 성공 시각과 generation 배포 시점 대조

- 사용자가 제시한 `summary:ex_d9b6268627_0817_1308:v1` 문서가 `generation_attempt` 없이도 저장된 이유를 examId 생성 규칙과 GitHub Actions 배포 시각으로 대조했다. 별도 신규 Jira 키는 없으며 관련 기존 범위는 `TMI-25`다.
- examId suffix는 `MMdd_HHmm`이고 운영 로그의 UTC 시각과 일치한다. `0817_1308`은 2026-08-17 13:08 UTC, 한국 시간 22:08이다. generation fencing 커밋 `a2c4fb6`의 workflow는 13:24 UTC에 시작해 실패했고, 후속 `98730c9` 배포는 13:39 UTC에 시작해 13:51 UTC, 한국 시간 22:51에 성공했다.
- 따라서 해당 시험은 성공 배포 약 43분 전에 생성됐다. 이전 Learning Core는 Summary Callback에 `generation_attempt`를 요구하지 않았으므로 이 문서는 정상 저장될 수 있으며 현재 미저장 현상과 모순되지 않는다.
- 문서 자체에는 생성·저장 시각 필드가 없어 정확한 Summary Callback 시각은 이 문서만으로 확정할 수 없다. 최종 확인은 같은 examId의 `exam_sessions.completedAt` 또는 CloudWatch의 Summary 저장 완료 로그 시각으로 한다.
- 코드·설정·테스트와 외부 계약은 변경하지 않았고 Actions는 읽기 전용으로만 확인했다. 외부 시스템·Jira·Git 상태를 변경하지 않았으며 Secret·Token을 조회하거나 기록하지 않았다.

## 2026-08-20 — legacy Summary 시각 대조 turn 기록 보완

<!-- codex-turn:01a01fa0-5366-7680-ba4a-b4929f77b13a -->

- 종료 훅 요구에 따라 `ex_d9b6268627_0817_1308` Summary 성공 시각 분석을 현재 turn 기록으로 보완했다. 별도 신규 Jira 키는 없으며 관련 기존 범위는 `TMI-25`다.
- examId suffix 기준 시험 생성은 2026-08-17 13:08 UTC, 한국 시간 22:08이고, generation fencing이 포함된 `98730c9` 성공 배포는 13:51 UTC, 한국 시간 22:51에 완료됐다. 시험 생성은 성공 배포 약 43분 전이므로 구버전 백엔드에서 Summary가 저장된 현상과 일치한다.
- Summary 문서에는 저장 시각이 없으므로 정확한 Callback 완료 시각은 `exam_sessions.completedAt` 또는 CloudWatch 저장 완료 로그로 최종 확인해야 한다.
- 코드·설정·테스트·외부 시스템·Jira·Git 상태는 변경하지 않았으며 Actions는 읽기 전용으로만 확인했다. Secret·Token도 조회하거나 기록하지 않았다.

## 2026-08-21 — 시험 entitlement 차감 시점과 서버 오류 복구 계약 정리

<!-- codex-turn:01a021d5-5ff1-73a0-a28d-308c63290c25 -->

- 사용자가 시험 시작에 무료 기회·credits가 필요한 상황에서 실제 차감 시점과 서버 오류로 시험을 완료하지 못했을 때의 복구 방식을 질문했다. 관련 Identity Jira는 완료된 `TMI-95`, `TMI-96`, `TMI-98`이며 Billing/Learning Core 후속 Jira 키는 아직 없다.
- 차감은 요청 즉시가 아니라 Billing 5분 reserve hold→Learning Core Session/operation metadata commit→Billing confirm 순서이며 confirm에서 최초 entitlement consumption과 OPEN AttemptGroup을 확정한다고 정리했다. confirm 전 reserve/Session 실패는 cancel·expire로 복구해 차감하지 않는다.
- confirm 뒤 필수 submit 미완료 상태의 앱·서버 장애는 확정된 R3에 따라 새 key·새 examId를 같은 group에 연결하는 무료 replacement로 처리한다. confirmed consumption은 있는데 Session이 손상된 경우도 재차감하지 않고 replacement로 복구한다.
- 모든 필수 문항 retryCount=0 submit이 접수돼 durable QuestionGradingJob이 생성된 뒤 AI/Callback/summary가 실패한 경우에는 새 시험을 무료 발급하지 않고 기존 grading retry와 reconciliation으로 복구하는 완료 기준을 권장했다. 현재 Session COMPLETED는 Summary Callback 뒤이므로 별도 `submissionCompletedAt`/outbox가 필요하다.
- entitlement 선택은 unlimited pass→FREE_EXAM_ONCE→만료 임박 promotional credits→오래된 paid credits 자동 순서를 권장했다. client 선택 방식과 paid-first 대안의 장단점도 `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`에 추가했으며 자동 순서와 제출 완료 기준은 아직 사용자 승인을 받지 않았다.
- 계약 결정서와 CURRENT_STATE만 갱신했으며 애플리케이션·설정·테스트 코드는 변경하지 않았다. 기존 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3와 retryCount 계약을 유지했고 Gradle 테스트는 실행하지 않았다. `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-21 — 채점 최종 실패 시 무료 재응시·보상 계약 보완

<!-- codex-turn:01a021d9-b484-75e0-beb8-53fdedaa8cb0 -->

- 사용자가 confirm 뒤 최초 1회 소비의 의미와 모든 문제 제출 후 grading retry까지 실패하면 점수·피드백 없이 기회가 사라지는 문제를 지적했다. 관련 Identity Jira는 완료된 `TMI-95`, `TMI-96`, `TMI-98`이며 grading retry 관련 기존 범위는 `TMI-25`, Billing/Learning Core 후속 Jira 키는 아직 없다.
- 최초 1회 소비는 credits를 소멸시키고 결과를 보장하지 않는 의미가 아니라 하나의 AttemptGroup에 한 번 귀속해 다른 시험에 중복 사용하지 못하게 하는 의미라고 정리했다. submit 미완료 오류는 같은 OPEN group의 무료 새 Session으로 복구한다.
- 모든 필수 최초 submit 접수 시 group을 닫는 직전 권장안을 철회하고 `OPEN → GRADING → COMPLETED`로 분리했다. 필수 문항 피드백과 유효한 종합 점수·Summary가 조회 가능할 때만 COMPLETED이며, retry/reconciliation이 최종 실패하면 `RETAKE_AVAILABLE`로 전환해 같은 consumption으로 새 key·새 examId의 무료 재응시를 허용한다.
- retake 시작 뒤 이전 Session의 늦은 Callback은 attempt generation/current Session fencing으로 no-op하고, 반복 채점 실패도 같은 정책으로 횟수 제한 없이 retake할 수 있게 했다. retake Session 자체도 제공할 수 없는 장기 서비스 장애에는 completed 결과 부재를 확인한 뒤 원 paid/promotional allocation 또는 free entitlement를 멱등 복원하는 최종 보상 계약을 추가했다.
- `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`와 `docs/codex/CURRENT_STATE.md`를 최신화했다. 애플리케이션·설정·테스트 코드는 변경하지 않았고 기존 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3와 retryCount 계약을 유지했다. Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-21 — Idempotency-Key 필요성 재설명

<!-- codex-turn:01a021e2-2f33-7d31-8807-8d7955930db0 -->

- 사용자가 R3 무료 재시작 계약에서 `Idempotency-Key`가 필요한 이유를 다시 질문했다. 관련 Identity Jira는 완료된 `TMI-95`, `TMI-96`, `TMI-98`이며 Billing/Learning Core 후속 Jira 키는 아직 없다.
- key는 결제나 AttemptGroup 식별자가 아니라 사용자의 한 번의 Session create/restart command를 식별한다. timeout·response loss로 같은 동작이 재전송되면 같은 key로 기존 Session을 반환하고, 사용자가 의도한 다음 restart는 새 key로 새 Session을 만든다.
- key가 없으면 서버는 동일 동작 재전송과 의도적인 새 restart를 구분할 수 없어 한 번의 버튼 동작이 E2 생성 후 즉시 E3 생성으로 이어지고 Billing authorization, active pointer, Redis·S3·Job·Callback 혼선을 만들 수 있다. 추가 credit 차감이 없는 R3에서도 이 위험은 그대로다.
- `docs/codex/CURRENT_STATE.md`에 역할 구분을 보완했다. 애플리케이션·설정·테스트와 기존 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3와 retryCount 계약은 변경하지 않았고 Gradle 테스트는 실행하지 않았다.
- `git diff --check`와 현재 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — ECS staging·production 환경 분리 결정

<!-- codex-turn:01a021e3-f1c1-79f1-8b0a-70ebf2a5a110 -->

- 사용자가 현재 `tosunsaeng-staging-cluster` 하나를 운영과 업데이트 전 테스트용 두 환경으로 나눌지 질문했다. 관련 Identity Jira는 `TMI-95`, `TMI-96`, `TMI-98`이며 Billing·인프라 후속 Jira 키는 아직 없다.
- 기존 cluster는 staging 의미를 유지하고 실제 사용자용 `tosunsaeng-prod-cluster`를 신규 생성하는 방향을 권장·기록했다. staging을 production으로 전환하고 이름이 다른 test cluster를 추가하는 방식은 환경 의미와 운영 이력 혼선을 피하기 위해 채택하지 않는다.
- 같은 AWS account·VPC는 초기 비용상 공유할 수 있지만 ECS service/task definition/target group/log, 도메인·workload credential, task role/secret, MongoDB·Redis·S3 mutable data boundary, Apple/Google sandbox·production 설정은 환경별로 격리하도록 했다. staging 검증을 통과한 동일 immutable image digest만 production으로 승격한다.
- 제공된 AWS Console 링크는 읽기 전용으로 열었으나 현재 브라우저 세션에서 AWS 로그인 화면으로 전환되어 기존 cluster의 service, capacity provider, VPC와 subnet 구성은 확인하지 못했다. AWS 리소스를 생성·수정·삭제하거나 credential을 조회하지 않았다.
- `docs/codex/FIRST_RELEASE_BILLING_BOUNDARY_REVIEW.md`와 `docs/codex/CURRENT_STATE.md`만 갱신했다. 애플리케이션·설정·테스트 코드와 기존 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3 key 및 retryCount 계약은 변경하지 않았고 Gradle 테스트는 실행하지 않았다.
- `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-21 — develop→staging·main→production 자동 배포 검토

<!-- codex-turn:01a021e8-67bd-76f2-ad6f-efacf328df29 -->

- 사용자가 `develop` 반영 시 테스트 환경, `main` 반영 시 production 환경으로 자동 배포할 수 있는지 질문했다. 관련 Identity Jira는 `TMI-95`, `TMI-96`, `TMI-98`이며 Billing·인프라 후속 Jira 키는 아직 없다.
- 현재 `.github/workflows/deploy-staging.yml`을 확인한 결과 `main` push가 `tosunsaeng-staging-cluster`를 배포한다. 목표 구조에서는 이 trigger를 `develop`로 변경하고 production 전용 workflow를 `main` PR merge 기준으로 추가해야 한다.
- staging과 production은 별도 GitHub Environment, OIDC role, ECS cluster/service, health URL과 환경별 variable/secret을 사용한다. `main`은 직접 push를 막고 필수 PR review·CI status check를 통과한 merge만 production 자동 배포를 일으키도록 권장했다.
- 기본안은 브랜치별 test/build/deploy이고, 더 강한 공급망 동일성이 필요하면 staging에서 검증한 ECR image digest를 기록해 production에 그대로 승격한다. production 재빌드는 staging에서 실행된 binary와 완전 동일하다는 보장이 약하다는 점을 기록했다.
- 이번에는 배포 가능성과 현행 workflow를 분석하고 `docs/codex/FIRST_RELEASE_BILLING_BOUNDARY_REVIEW.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. GitHub Actions, AWS 리소스, 애플리케이션 코드, Secret·Token, 외부 API 계약은 변경하지 않았고 Gradle 테스트는 실행하지 않았다.
- `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-21 — 현재 구현과 확정 계획을 포함한 전체 앱 흐름 정리

<!-- codex-turn:01a021ec-6d88-7520-b8f1-270118daa6a8 -->

- 사용자의 요청에 따라 현재 Learning Core 코드, `CURRENT_STATE`, Billing/Entitlement·`UserMerged`·피드백 복구 계획을 대조해 전체 앱 흐름을 현재 구현과 후속 계획으로 구분해 정리했다.
- 현재 구현 흐름은 Legacy/JWT 사용자 식별, 시험지 순환 배정과 `ExamSession` 소유권, S3 Presigned PUT 업로드, `retryCount`별 submit, `QuestionGradingJob`/`SummaryGradingJob` 기반 AI dispatch·Callback 멱등 저장, Redis 상태 projection, Polling·요약·문항·이력·재답변 조회 및 시험 단위 채점 복구다.
- 확정된 1차 앱 목표 흐름은 Identity 로그인·verified phone(`TMI-90`, `TMI-95`, `TMI-98`), Billing/Entitlement의 무료 1회·Apple/Google 인앱결제·reserve/confirm, Learning Core의 Session/AttemptGroup과 완료 전 R3 무료 replacement, 결과 제공, `UserMerged` owner 이전, staging/prod 분리 배포다. 현재 Learning Core 기반 구현의 관련 Jira는 `TMI-14`, `TMI-25`, `TMI-31`이며 Billing/Learning Core 후속 Jira 키는 아직 제공되지 않았다.
- Billing 연동, `AttemptGroup`/R3, Learning Core·Billing `UserMerged` consumer, Identity multi-consumer fan-out과 production 배포 workflow는 아직 구현 완료가 아닌 계획 범위임을 명시했다. 애플리케이션·설정·테스트 코드는 변경하지 않았고 기존 공개 API·DTO·`BaseResponse`, `retryCount`, Redis/S3 Key, AI/Callback `user_id=examId` 계약을 유지했다.
- 분석 작업이므로 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-21 — web-ai AWS 배포 완료 상태 확인

<!-- codex-turn:01a021f1-6d8d-7f10-ae97-98accf93f774 -->

- 사용자가 `Too-Much-I/web-ai`의 AWS 배포 방법을 요청해 최신 main과 GitHub Actions 상태를 읽기 전용으로 확인했다. 별도 신규 Jira 키는 없으며 Summary generation 관련 기존 범위는 `TMI-25`다.
- 최신 main은 `883c45c6268cc4822dcf0d384cf24294d832312c`이고 제목은 `Echo generation attempt in feedback callbacks`이다. GitHub Actions run `32435961886`은 2026-08-21 01:22:59 UTC, 한국 시간 10:22:59에 성공 완료됐다.
- offline test, Docker Compose 검증, Docker 이미지 build/push, EC2 `SSH Command to Deploy` 단계가 모두 성공했다. workflow가 EC2의 `ai-server`와 `ai-worker`를 교체하므로 사용자가 추가로 수동 업로드하거나 EC2에서 git pull할 필요는 없다.
- 다음 운영 확인은 새 시험의 Summary Callback이 `generation_attempt`를 echo해 `exam_summaries` 저장과 `summary_grading_jobs=COMPLETED`로 수렴하는지 보는 것이다. 기존 누락 시험은 AI가 과거 200 응답을 성공으로 처리했을 수 있어 자동 복구를 기대하지 말고 기존 시험 단위 grading retry 흐름을 사용해야 한다.
- 외부 저장소와 Actions는 읽기 전용으로만 확인했고 commit·push·workflow rerun·EC2 조작은 수행하지 않았다. Learning Core 코드·설정·테스트와 외부 계약도 변경하지 않았으며 Secret·Token을 조회하거나 기록하지 않았다.

## 2026-08-21 — web-ai ECS·GitHub Actions 배포 전환 설계

<!-- codex-turn:01a021f4-53dc-70b0-aa31-e4c98ca44154 -->

- 사용자가 `Too-Much-I/web-ai`도 AWS ECS에 올리고 GitHub Actions로 자동 갱신하기 위해 필요한 정책·역할·코드와 적용 순서를 요청했다. 별도 신규 Jira 키는 없으며 AI Summary generation·멱등 처리 관련 기존 범위는 `TMI-25`다.
- ECS 배포는 cluster 자체를 갱신하는 것이 아니라 새 ECR image로 Task Definition revision을 등록하고 ECS Service를 갱신하는 방식이다. API와 worker는 command, health check, scaling이 달라 별도 task definition과 service로 구성하고 동일 immutable SHA image를 사용하도록 정리했다.
- 현재 AI API가 업로드 파일의 로컬 경로를 Redis에 넣고 worker가 읽는 구조이므로 workflow만 ECS로 바꾸면 서로 다른 Fargate task에서 파일이 보이지 않는다. 1차 전환은 공용 ElastiCache Redis와 EFS access point를 사용하고, 이미지에 포함된 mock exam data를 가리지 않도록 `/app/data`가 아닌 별도 mount path를 평가 관련 환경변수에 연결한다. 장기적으로는 queue payload를 S3 object key 방식으로 바꾸는 대안을 권장했다.
- IAM은 GitHub OIDC deploy role, ECS task execution role, 최소 권한 application task role과 AWS 관리 service-linked role로 분리한다. staging(`develop`)과 production(`main`)은 GitHub Environment, OIDC subject, cluster/service, Redis/EFS/secret/log를 분리하고 검증된 동일 image digest를 production으로 승격하는 방향을 권장했다.
- 실제 `web-ai` 코드·workflow, AWS·GitHub 설정, 외부 API/Callback 계약은 변경하지 않았다. Secret·Token을 조회하거나 기록하지 않았고 분석·문서 기록만 수행했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — 기존 web-ai ECS Service 자동 업데이트 범위 정정

<!-- codex-turn:01a021f8-0cb2-7633-9cf6-06acec6121d5 -->

- 사용자가 `web-ai`도 이미 백엔드처럼 ECS에 올라가 정상 동작 중이며 필요한 것은 GitHub Actions 자동 업데이트뿐이라고 정정했다. 별도 신규 Jira 키는 없으며 AI 채점·Callback 관련 기존 범위는 `TMI-25`다.
- 이에 따라 앞서 설명한 Redis, EFS, ALB, ECS cluster/service 신규 생성은 현재 작업에서 제외한다. 기존 AI ECS Service와 Task Definition의 container name, 기존 ECR repository, cluster/service name과 health URL을 그대로 사용한다.
- 최소 자동화는 GitHub OIDC deploy role assume, offline test, 동일 Docker image의 ECR SHA tag push, 현재 Service의 Task Definition 조회, container image render, 새 revision 등록과 Service update, stability 대기 및 health check다. API와 worker가 단일 ECS Service면 한 번만 배포하고 별도 Service라면 동일 image URI로 각각 갱신한다.
- 실제 `web-ai`, AWS, GitHub Actions와 Learning Core 애플리케이션·외부 계약은 변경하지 않았다. Secret·Token을 조회하거나 기록하지 않았고 분석 기록만 갱신했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — 공통 ECS cluster의 web-ai 배포 실값 조회 시도

<!-- codex-turn:01a021ff-5653-75b0-802c-f0817b0e8bfb -->

- 사용자가 Learning Core와 `web-ai`가 동일한 ECS cluster에 있다고 알려주며 자동 배포에 필요한 나머지 설정을 모두 확인할 수 있는지 요청했다. 별도 신규 Jira 키는 없으며 AI 채점·Callback 관련 기존 범위는 `TMI-25`다.
- 현재 저장소의 `.github/workflows/deploy-staging.yml`에서 region `ap-northeast-2`, cluster `tosunsaeng-staging-cluster`, OIDC `AWS_ROLE_ARN`, ECR build/push, 현재 Service Task Definition 조회·render·deploy와 health check 패턴을 확인했다. 이 공통 패턴은 AI workflow에 재사용할 수 있다.
- 실제 AI 리소스 조회를 위해 AWS CLI, GitHub CLI, in-app AWS Console과 비공개 GitHub 저장소를 읽기 전용으로 확인했다. AWS CLI credential은 없고 GitHub CLI 인증은 만료됐으며 브라우저의 AWS와 GitHub도 로그아웃 상태라 AI ECS Service, Task Definition family/revision, container name과 image/ECR, deploy role 권한, repository variables의 실제 값은 확정하지 못했다.
- AWS 로그인 화면을 사용자 handoff로 열어두었다. 로그인 뒤 같은 cluster의 AI Service→Task Definition→container/image/ECR→deployment configuration→IAM role→GitHub workflow/variables 순으로 읽기 전용 대조할 수 있다. 비밀번호·인증 코드·Secret·Token은 조회하거나 기록하지 않는다.
- AWS·GitHub·`web-ai`와 Learning Core 애플리케이션·외부 계약은 변경하지 않았다. 분석·기록 작업만 수행해 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — AI ECS container name·health 검증 위치 정리

<!-- codex-turn:01a02205-9130-7f00-93b7-ef8f9e03fcee -->

- 사용자가 공통 ECS cluster가 `tosunsaeng-staging-cluster`, AI Service가 `tosunsaeng-ai-service`라고 확정하고 GitHub Actions의 `CONTAINER_NAME`과 `HEALTH_URL`을 어디서 확인하는지 질문했다. 별도 신규 Jira 키는 없으며 AI 채점·Callback 관련 기존 범위는 `TMI-25`다.
- `CONTAINER_NAME`은 ECS Service가 참조하는 Task Definition revision의 `Container definitions`에서 `Name`을 확인하거나 Service의 실행 Task 상세 `Containers`에서 확인한다. Service name이나 Task Definition family를 임의로 container name으로 사용하지 않는다.
- `HEALTH_URL`은 ECS Task Definition의 단일 필드가 아니다. public ALB가 연결된 경우 Service의 Load balancing→Target Group health check path, ALB listener/rule과 Route 53 record를 조합해 `https://host/path`를 확정한다. `ai.to-teacher.com/ready`를 공개 DNS로 읽기 전용 검사했으나 host가 해석되지 않아 현재 workflow health URL로 사용할 수 없음을 확인했다.
- Learning Core 설정의 기본 `AI_SERVER_URL`은 `http://tosunsaeng-ai:8000`이므로 AI가 ECS Service Connect 또는 Cloud Map 내부 주소로만 노출됐을 가능성이 높다. 이 경우 GitHub-hosted runner의 public `curl` 검증을 제거하고 Task Definition container `healthCheck`와 ECS deploy action의 service stability, 필요 시 healthy task count 조회로 배포를 검증해야 한다.
- AWS Console은 로그인 화면으로 전환되어 실제 Task Definition container name, Service Connect/Cloud Map, ALB 연결 여부는 직접 확정하지 못했다. AWS·GitHub·`web-ai`와 Learning Core 코드·외부 계약은 변경하지 않았고 Secret·Token을 조회하거나 기록하지 않았다. 분석·기록 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — 단일 AI Task의 API·worker 5개 container 배포 방식 확정

<!-- codex-turn:01a0220b-bef8-7f20-8d26-c6d26f4452d6 -->

- 사용자가 `tosunsaeng-ai-service`의 실행 Task에 `ai-api`, `ai-worker-1`, `ai-worker-2`, `ai-worker-3`, `ai-worker-4` 다섯 container가 존재하고 `ai-api` 로그에서 `127.0.0.1`의 `GET /ready`가 200으로 응답한다고 확인했다. 별도 신규 Jira 키는 없으며 AI 채점·Callback 관련 기존 범위는 `TMI-25`다.
- 이 구성은 ECS Service 하나와 Task Definition 하나 안의 다섯 container를 함께 배포하는 구조다. 다섯 container가 같은 AI ECR repository/image를 사용하는 것이 Task Definition에서 확인되면 한 번 build/push한 immutable SHA image로 `amazon-ecs-render-task-definition`을 다섯 번 체인하고 최종 결과 Task Definition을 `tosunsaeng-ai-service`에 한 번 deploy해야 한다.
- `CONTAINER_NAME=ai-api` 하나만 render하면 API만 신규 image가 되고 worker 4개가 이전 image로 남아 API·worker 코드와 Redis job payload 호환성이 깨질 수 있다. workflow에는 API와 worker별 실제 container name을 모두 명시한다.
- localhost `/ready` 200 로그는 컨테이너 내부 readiness 요청이 성공하는 증거다. public `ai.to-teacher.com` DNS는 없으므로 GitHub runner의 public `HEALTH_URL` curl은 사용하지 않고 ECS task definition의 container `healthCheck`와 deploy action의 `wait-for-service-stability`로 검증한다. 실제 Task Definition에 `healthCheck`가 등록됐는지는 최종 확인이 필요하다.
- AWS·GitHub·`web-ai`와 Learning Core 코드·외부 계약은 변경하지 않았고 Secret·Token을 조회하거나 기록하지 않았다. 분석·기록 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — web-ai ECR repository 확인 위치 안내

<!-- codex-turn:01a0220d-cbad-75f0-af12-30db88bc6a80 -->

- 사용자가 GitHub Actions의 `ECR_REPOSITORY` 값을 AWS 어디에서 확인하는지 질문했다. 별도 신규 Jira 키는 없으며 AI 채점·Callback 관련 기존 범위는 `TMI-25`다.
- ECS `tosunsaeng-staging-cluster`→`tosunsaeng-ai-service`→현재 Task Definition revision→`ai-api` container의 Image URI 순서로 확인한다. registry hostname 뒤 `/`와 tag `:` 또는 digest `@sha256:` 사이의 값이 ECR repository name이다.
- 예를 들어 Image URI가 `<account>.dkr.ecr.ap-northeast-2.amazonaws.com/tosunsaeng-web-ai:<tag>`이면 workflow에는 `ECR_REPOSITORY: tosunsaeng-web-ai`를 사용한다. 계정 ID, registry hostname과 tag는 repository 값에 포함하지 않는다.
- `ai-worker-1`~`ai-worker-4`의 Image URI도 같은 repository인지 함께 대조해야 한다. repository가 다르면 한 image URI로 다섯 container를 일괄 교체하지 않고 각 image build/deploy 전략을 별도로 확인한다.
- AWS·GitHub·`web-ai`와 Learning Core 코드·외부 계약은 변경하지 않았고 Secret·Token을 조회하거나 기록하지 않았다. 분석·기록 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — web-ai ECR repository 실값 확정

<!-- codex-turn:01a0220e-85fd-7401-b10a-690f47aec243 -->

- 사용자가 현재 `ai-api` container의 ECR image URI를 제공해 repository name을 `tosunsaeng-ai`로 확정했다. 별도 신규 Jira 키는 없으며 AI 채점·Callback 관련 기존 범위는 `TMI-25`다.
- Image URI의 registry hostname 뒤 `/`와 digest `@sha256:` 사이가 repository이므로 GitHub Actions에는 `ECR_REPOSITORY: tosunsaeng-ai`를 사용한다. 현재 digest는 배포된 immutable image 식별자이며 repository 변수에는 포함하지 않는다.
- `ai-worker-1`~`ai-worker-4`도 같은 repository와 현재 digest를 사용하는지 Task Definition에서 대조해야 한다. 모두 같으면 workflow가 한 번 build/push한 새 SHA image URI를 다섯 container에 연속 render한 뒤 `tosunsaeng-ai-service`를 한 번 갱신한다.
- AWS·GitHub·`web-ai`와 Learning Core 코드·외부 계약은 변경하지 않았다. image digest는 Secret이 아니며 Secret·Token은 조회하거나 기록하지 않았다. 분석·기록 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — AI API·worker 동일 ECR image 사용 확정

<!-- codex-turn:01a0220f-fdcf-7070-817b-26230da9f9f7 -->

- 사용자가 `ai-api`, `ai-worker-1`, `ai-worker-2`, `ai-worker-3`, `ai-worker-4`가 모두 같은 `tosunsaeng-ai` repository와 동일 current image digest를 사용한다고 확인했다. 별도 신규 Jira 키는 없으며 AI 채점·Callback 관련 기존 범위는 `TMI-25`다.
- GitHub Actions 배포 전략은 Docker image 한 번 build, ECR `tosunsaeng-ai:${GITHUB_SHA}` 한 번 push, 동일 image URI를 다섯 container에 `amazon-ecs-render-task-definition`으로 순차 적용하고 마지막 rendered Task Definition을 `tosunsaeng-ai-service`에 한 번 deploy하는 것으로 확정됐다.
- render action은 기존 Task Definition을 기반으로 image 필드만 변경하므로 `ai-api`와 worker별 command, environment, secret, mount, CPU/memory 설정은 그대로 유지한다. public health URL은 없으므로 `ai-api` container readiness와 `wait-for-service-stability`로 검증한다.
- AWS·GitHub·`web-ai`와 Learning Core 코드·외부 계약은 변경하지 않았다. image digest는 Secret이 아니며 Secret·Token은 조회하거나 기록하지 않았다. 분석·기록 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — web-ai ECS 자동 배포 최종 적용 절차 정리

<!-- codex-turn:01a02210-be0b-78a1-a257-5f488afafeab -->

- 사용자가 지금까지 확인한 AI ECS 변수를 모두 넣어 실제 GitHub Actions 적용 절차를 정리해 달라고 요청했다. 별도 신규 Jira 키는 없으며 AI 채점·Callback 관련 기존 범위는 `TMI-25`다.
- 확정값은 region `ap-northeast-2`, ECR repository `tosunsaeng-ai`, cluster `tosunsaeng-staging-cluster`, service `tosunsaeng-ai-service`, container `ai-api`, `ai-worker-1`~`ai-worker-4`다. 다섯 container는 동일 ECR repository와 current digest를 사용한다.
- 기존 `web-ai` workflow의 offline test와 Compose validation을 유지하고 Docker Hub push·EC2 SSH 재기동을 제거한다. GitHub OIDC로 deploy role을 assume하고 ECR에 `${GITHUB_SHA}` tag image를 한 번 push한 뒤, 현재 Service의 Task Definition을 조회해 동일 image URI를 다섯 container에 순차 render하고 최종 revision을 Service에 한 번 deploy한다.
- public AI DNS가 없고 `ai-api` 내부 `/ready`가 200이므로 public `HEALTH_URL` curl은 제외하고 container health와 `wait-for-service-stability`를 사용한다. 기존 Task Definition의 command, environment, secret, mount와 resource 설정은 image render 과정에서 유지한다.
- 남은 AWS 실값은 현재 Task Definition의 execution role ARN과 task role ARN, 이를 제한적으로 pass할 GitHub deploy role ARN이다. GitHub에는 static AWS key 대신 repository/environment variable `AWS_ROLE_ARN`만 설정하고 OIDC trust를 `Too-Much-I/web-ai`의 배포 branch 또는 environment subject로 제한한다.
- 실제 `web-ai` workflow, AWS IAM/ECR/ECS와 GitHub 설정은 변경하지 않았다. Secret·Token을 조회하거나 기록하지 않았고 분석·기록 작업이라 Gradle 테스트는 실행하지 않았다. `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — 기존 AI ECS Task 역할 확인·생성 기준 정리

<!-- codex-turn:01a02213-2067-7b60-b122-ad5e5dec854e -->

- 사용자가 AI Task Role과 Task Execution Role을 새로 만드는 방법을 요청했다. 별도 신규 Jira 키는 없으며 AI 채점·Callback 관련 기존 범위는 `TMI-25`다.
- 이미 정상 실행 중인 `tosunsaeng-ai-service`에는 ECR pull·로그·Secret 주입에 필요한 Execution Role이 현재 Task Definition에 존재할 가능성이 높으므로 새로 만들기 전에 `executionRoleArn`과 `taskRoleArn`을 확인하고 기존 역할을 재사용해야 한다. Task Role은 애플리케이션이 AWS API를 직접 호출하지 않으면 null일 수 있으며 이 경우 생성하지 않는다.
- 새 Execution Role이 실제로 필요한 경우 trust principal은 `ecs-tasks.amazonaws.com`, 기본 관리 정책은 `AmazonECSTaskExecutionRolePolicy`다. Task Definition의 `secrets`가 참조하는 Secret/Parameter와 customer-managed KMS key가 있을 때만 해당 resource로 제한한 `GetSecretValue`/`GetParameters`/`kms:Decrypt`를 추가한다.
- 새 Task Role은 동일 trust를 사용하되 기본 broad policy를 부여하지 않고 AI 애플리케이션이 직접 호출하는 S3/EFS 등 실제 AWS API resource에만 최소 권한을 추가한다. Redis·외부 HTTP API·ECR pull·CloudWatch log 전송은 Task Role 권한이 아니다.
- 새 역할을 적용하려면 Task Definition 새 revision에 ARN을 지정해 Service를 배포하고 다섯 container 기동·Secret 주입·로그·readiness를 검증해야 한다. GitHub deploy role의 `iam:PassRole`은 최종 Task Definition에 사용된 role ARN만 허용한다.
- AWS·GitHub·`web-ai`와 Learning Core 코드·외부 계약은 변경하지 않았고 Secret·Token을 조회하거나 기록하지 않았다. 분석·기록 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — AI Task Definition 역할·health check 실값 확인

<!-- codex-turn:01a02216-c599-7ac0-b56a-83c1107716d9 -->

- 사용자가 현재 `tosunsaeng-ai:5` Task Definition JSON을 첨부해 기존 Task/Execution Role 존재 여부 확인을 요청했다. 별도 신규 Jira 키는 없으며 AI 채점·Callback 관련 기존 범위는 `TMI-25`다.
- 민감 환경값은 분석·기록 대상에서 제외하고 역할, container image, health check와 secret 이름 존재 여부만 구조적으로 확인했다. `taskRoleArn`은 null이므로 AI Task Role을 신규 생성하지 않는다.
- `executionRoleArn`은 기존 `arn:aws:iam::889384901776:role/tosunsaeng-ecs-execution-role`이다. 현재 다섯 Fargate container가 같은 private ECR digest로 실행되고 API credential 세 종류가 ECS secret 참조로 주입되므로 기존 Execution Role을 그대로 재사용하고 신규 생성하지 않는다.
- GitHub deploy role의 `iam:PassRole` resource에는 기존 Execution Role ARN 하나만 제한적으로 지정한다. Task Role ARN placeholder는 정책에서 제거한다.
- Task family는 `tosunsaeng-ai`, revision 5, `awsvpc`/Fargate, CPU 2048, memory 4096이다. `ai-api`와 worker 4개는 모두 essential이며 동일 image digest를 사용한다. `ai-api`에는 localhost port 8000 `/ready` health check가 30초 interval, 5초 timeout, 3 retries, 60초 start period로 등록돼 있고 worker에는 별도 health check가 없다.
- 따라서 public `HEALTH_URL` curl은 불필요하고 ECS container health 및 service stability로 배포를 검증한다. AWS·GitHub·`web-ai`와 Learning Core 코드·외부 계약은 변경하지 않았으며 Secret 값이나 Token은 조회·기록하지 않았다. 분석·기록 작업이라 Gradle 테스트는 실행하지 않았고 `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — web-ai GitHub OIDC deploy role 생성 절차 확정

<!-- codex-turn:01a02218-c9bb-7ba3-b3c8-1cedfe006141 -->

- 사용자가 `tosunsaeng-web-ai-github-deploy-role`을 AWS IAM에서 만드는 방법을 요청했다. 별도 신규 Jira 키는 없으며 AI 채점·Callback 관련 기존 범위는 `TMI-25`다.
- 기존 account의 GitHub OIDC provider `token.actions.githubusercontent.com`과 audience `sts.amazonaws.com`을 재사용하고, trust policy subject를 `repo:Too-Much-I/web-ai:ref:refs/heads/main`으로 제한한다. static AWS Access Key는 생성하지 않는다.
- Role의 최소 권한은 ECR authorization, 정확한 `tosunsaeng-ai` repository push, ECS describe/task definition register/tag, 정확한 `tosunsaeng-staging-cluster/tosunsaeng-ai-service` update와 기존 `tosunsaeng-ecs-execution-role` 하나에 대한 `iam:PassRole`이다. AI Task Role은 현재 null이므로 PassRole에 포함하지 않는다.
- 생성한 role ARN은 `Too-Much-I/web-ai` GitHub Actions repository variable `AWS_ROLE_ARN`에 저장한다. workflow는 `permissions: contents: read, id-token: write`와 `main` trigger를 사용하며 현재 trust에서는 GitHub `environment:`를 추가하지 않는다. Environment를 사용하려면 trust subject를 environment 형식으로 함께 변경해야 한다.
- 실제 AWS IAM, GitHub repository와 `web-ai` workflow는 변경하지 않았다. Secret·Token을 조회하거나 기록하지 않았고 분석·기록 작업이라 Gradle 테스트는 실행하지 않았다. `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — web-ai workflow 적용 팀 인계 범위 확정

<!-- codex-turn:01a02224-3944-7420-aa9c-71f07064ca74 -->

- 사용자가 GitHub OIDC deploy role 생성과 `Too-Much-I/web-ai` repository variable `AWS_ROLE_ARN` 등록을 완료한 뒤 AI 팀원에게 workflow만 적용하도록 요청하면 되는지 확인했다. 별도 신규 Jira 키는 없으며 AI 채점·Callback 관련 기존 범위는 `TMI-25`다.
- 남은 구현은 `web-ai/.github/workflows/deploy.yml` 하나의 배포 방식 전환이다. 기존 offline test·Docker Compose validation은 유지하고 Docker Hub push와 EC2 SSH 재기동을 제거한다.
- workflow는 `contents: read`, `id-token: write`로 OIDC role을 assume하고 `tosunsaeng-ai` ECR에 image를 한 번 build/push한다. 현재 `tosunsaeng-ai-service` Task Definition을 조회해 동일 immutable digest를 `ai-api`, `ai-worker-1`~`ai-worker-4`에 순차 render한 뒤 `tosunsaeng-staging-cluster`의 Service를 한 번 deploy하고 stability를 기다린다.
- 기존 Task Definition의 command, environment, ECS secret references, mount와 CPU/memory를 변경하지 않고 image만 교체한다. public DNS가 없으므로 `HEALTH_URL` curl은 추가하지 않으며 등록된 `ai-api` localhost `/ready` container health check를 사용한다.
- OIDC trust가 `repo:Too-Much-I/web-ai:ref:refs/heads/main`이므로 PR branch에서는 AWS assume이 실패하도록 유지하고 main merge/push 또는 main 대상 workflow_dispatch로 첫 실제 배포를 검증한다. 첫 run에서 다섯 container의 동일 새 digest, Service stability, API HEALTHY와 worker RUNNING 및 Callback 동작을 확인해야 한다.
- 이번 turn에서는 사용자가 완료했다고 보고한 IAM/GitHub 설정 외에 AWS·GitHub·`web-ai`와 Learning Core 코드·외부 계약을 변경하지 않았다. Secret·Token을 조회하거나 기록하지 않았고 분석·기록 작업이라 Gradle 테스트는 실행하지 않았다. `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — web-ai 신규 ECS workflow 파일 필요성 확인

<!-- codex-turn:01a02227-577a-7d02-bba3-7f83a7eda0b4 -->

- 사용자가 `web-ai` 저장소에 deploy 코드가 없는데도 자동 배포가 가능한지 질문했다. 별도 신규 Jira 키는 없으며 AI 채점·Callback 관련 기존 범위는 `TMI-25`다.
- 별도 `deploy.sh`나 애플리케이션 배포 코드는 필요 없지만 GitHub Actions workflow 파일은 필수다. 기존 workflow가 실제로 없다면 AI 팀원이 `.github/workflows/deploy-ecs.yml`을 신규 생성해야 하며 이 YAML이 배포 코드 역할을 한다.
- workflow는 기존 Dockerfile로 image를 build하고 기존 offline test·Compose validation을 수행한 뒤 OIDC, ECR push, 현재 Task Definition 조회, `ai-api`와 worker 4개 동일 digest render, `tosunsaeng-ai-service` deploy와 stability 검증을 포함해야 한다.
- 저장소에 기존 `.github/workflows/deploy.yml`이 있다면 신규 파일과 중복 trigger를 만들지 말고 기존 파일을 교체하거나 이름을 변경한다. 두 workflow가 동시에 같은 `main` push에서 실행되면 EC2와 ECS 또는 ECS 중복 배포가 발생할 수 있다.
- 실제 `web-ai` 파일, AWS IAM/ECS/ECR와 GitHub 설정은 변경하지 않았다. Secret·Token을 조회하거나 기록하지 않았고 분석·기록 작업이라 Gradle 테스트는 실행하지 않았다. `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — DescribeTaskDefinition IAM AccessDenied 진단

<!-- codex-turn:01a02269-a933-7be1-a72e-e1e6efe6fb9a -->

- 사용자가 첫 `web-ai` ECS GitHub Actions에서 현재 Task Definition 다운로드 단계의 `ecs:DescribeTaskDefinition` AccessDenied 로그를 제공했다. 별도 신규 Jira 키는 없으며 AI 채점·Callback 관련 기존 범위는 `TMI-25`다.
- STS principal이 `tosunsaeng-web-ai-github-deploy-role/GitHubActions`로 표시되므로 GitHub OIDC trust, `AWS_ROLE_ARN`과 role assume은 정상이다. 실패 원인은 해당 action이 authorization에서 resource `*`를 요구하는데 기존 안내 정책이 `tosunsaeng-ai:*` ARN으로 제한돼 statement가 매칭되지 않은 것이다.
- Role과 GitHub variable은 재생성하지 않는다. 연결된 deploy policy에서 `ecs:DescribeTaskDefinition` statement의 `Resource`만 `*`로 수정하고, ECR push repository, exact ECS Service update와 `tosunsaeng-ecs-execution-role` PassRole 범위는 유지한다.
- 정책 저장 후 실패한 workflow를 rerun한다. 정책 반영은 일반적으로 빠르지만 짧은 전파 지연이 있을 수 있다. 후속 AccessDenied가 발생하면 action·resource를 확인해 필요한 API만 AWS authorization model에 맞게 추가하고 broad managed deployment policy는 사용하지 않는다.
- 실제 AWS IAM, GitHub workflow와 `web-ai` 코드는 변경하지 않았다. Secret·Token을 조회하거나 기록하지 않았고 분석·기록 작업이라 Gradle 테스트는 실행하지 않았다. `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — web-ai deploy IAM policy 전체 수정본 제공

<!-- codex-turn:01a0226c-409e-7061-8241-0e968967831b -->

- 사용자가 현재 deploy role 정책 전체 JSON을 제공하고 `DescribeTaskDefinition` AccessDenied를 반영한 전체 수정본을 요청했다. 별도 신규 Jira 키는 없으며 AI 채점·Callback 관련 기존 범위는 `TMI-25`다.
- 전체 정책에서 `ReadAiTaskDefinitions` statement의 `Resource`만 `arn:aws:ecs:...:task-definition/tosunsaeng-ai:*`에서 `*`로 변경한다. 해당 API가 현재 authorization request에서 resource `*`로 평가되기 때문이다.
- ECR authorization과 정확한 `tosunsaeng-ai` repository push, exact ECS Service describe/update, Task Definition register/tag와 기존 `tosunsaeng-ecs-execution-role` PassRole 제한은 사용자가 제공한 범위를 그대로 유지한다.
- 실제 AWS IAM, GitHub workflow와 `web-ai` 코드는 변경하지 않았다. Secret·Token을 조회하거나 기록하지 않았고 분석·기록 작업이라 Gradle 테스트는 실행하지 않았다. `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — 전체 앱 계획의 최우선 실행 순서 확정

<!-- codex-turn:01a02276-6e0e-7c43-ad68-d041b7582590 -->

- 사용자가 전체 앱 계획에서 지금 가장 먼저 해야 할 일을 질문했다. 최우선은 개별 코드 구현이 아니라 Billing–Learning Core 시험 시작 saga와 서비스 간 event/API 계약을 동결하고 후속 Jira를 분리하는 Phase 0이라고 정리했다.
- 첫 계약 묶음은 Learning Core→Billing `reserve/confirm/cancel/status/reconcile`, 5분 reservation, Session commit·confirm 결과 불명 복구, `Idempotency-Key`, 안정적인 공개 오류 mapping, R3 `AttemptGroup`의 `OPEN/GRADING/COMPLETED/RETAKE_AVAILABLE` 상태와 완료 근거다.
- Jira는 최소 Identity `UserMerged` multi-consumer fan-out, Billing/Entitlement foundation, Learning Core Billing 연동·AttemptGroup, Client Apple/Google IAP·idempotency, staging E2E·배포 gate로 분리한다. 관련 기존 Jira는 Identity `TMI-90`, `TMI-95`, `TMI-98`, Learning Core 기반 `TMI-14`, `TMI-25`, `TMI-31`이며 Billing/Learning Core 후속 Jira 키는 아직 제공되지 않았다.
- 계약 동결 뒤에는 feature flag OFF로 Identity fan-out, Billing의 phone binding·TrialClaim·ledger·Reservation, Learning Core의 Billing client·contract test·Session metadata를 병렬 구현하는 순서를 권장했다. 애플리케이션·설정·테스트 코드는 변경하지 않았고 기존 외부 API·AI·Redis·S3 계약도 변경하지 않았다.
- 분석·우선순위 정리 작업이므로 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — Phase 0 계약 결정 영역과 선택지 가이드

<!-- codex-turn:01a02279-c441-7b61-82e2-231064ec4d15 -->

- 사용자가 계약 확정을 시작하기 위해 결정이 필요한 영역, 선택지와 장단점 설명을 요청했다. 기존 결정서와 현재 구현을 대조해 이미 확정된 제품·복구 정책과 아직 동결하지 않은 서비스 계약을 분리했다.
- 미확정 핵심은 공개 `Idempotency-Key` 정책과 replay, entitlement 선택, confirm 불명 처리, 공개 오류 mapping, AttemptGroup·consumption 소유권과 완료 증거, replacement authorization·동시성, Identity event fan-out, workload 인증, Apple/Google 거래 검증·notification 멱등성, reconciliation·rollout gate다.
- 권장 패키지는 기존 공개 body/response 유지와 신규 앱 UUID v4 key 필수, 완료 replay 동일 200·처리 중 409, 서버 자동 entitlement 선택, confirm 전 Session 비노출·503, 안정적인 client 행동 기준 오류, Learning Core의 AttemptGroup 상태 소유와 Billing의 consumption 소유, consumer별 direct HTTPS delivery, audience가 분리된 workload JWT, server-side store 검증과 notification inbox, 양쪽 operation status 기반 reconciliation이다.
- 이미 확정된 Billing/Entitlement 단일 서비스, Apple/Google 결제, 검증 phone당 무료 1회, 시험당 10 credits, 5분 reserve→Session commit→confirm, 이어풀기 제외와 R3 무료 replacement는 재결정 대상이 아님을 명시했다. 관련 기존 Jira는 Identity `TMI-90`, `TMI-95`, `TMI-98`, Learning Core `TMI-14`, `TMI-25`, `TMI-31`이며 Billing/Learning Core 후속 Jira 키는 아직 제공되지 않았다.
- 애플리케이션·설정·테스트 코드는 변경하지 않았고 기존 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3와 retryCount 계약을 유지했다. 분석 작업이므로 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — 배포 후 ECS ListTasks 검증 권한 누락 진단

<!-- codex-turn:01a0227c-8895-76e3-81cc-00f55f89f028 -->

- 사용자가 정책 수정 후 재실행한 GitHub Actions에서 `ecs:ListTasks` AccessDenied 로그를 제공했다. 별도 신규 Jira 키는 없으며 AI 채점·Callback 관련 기존 범위는 `TMI-25`다.
- 로그에 새 Task Definition `tosunsaeng-ai:6`과 새 expected ECR digest가 표시되므로 OIDC, ECR push, Task Definition register와 Service deploy 단계는 진행됐고, 실패는 배포된 running Task image/digest를 확인하는 마지막 read-only 검증 단계다.
- deploy role 정책에 `ecs:ListTasks`와 바로 이어질 가능성이 높은 `ecs:DescribeTasks`를 resource `*`로 함께 추가한다. ListTasks authorization이 동적 container-instance resource로 평가되므로 기존 특정 Task Definition/Service ARN statement로는 허용되지 않는다.
- ECR repository push, exact `tosunsaeng-ai-service` update와 기존 execution role PassRole 제한은 변경하지 않는다. 정책 저장 뒤 failed jobs를 rerun하고 ECS Service가 revision 6으로 안정화됐는지, 다섯 container가 동일 새 digest를 사용하는지 확인한다.
- 실제 AWS IAM, GitHub workflow와 `web-ai` 코드는 변경하지 않았다. 로그의 임시 AWS credential은 마스킹되어 있으며 Secret·Token을 조회하거나 기록하지 않았다. 분석·기록 작업이라 Gradle 테스트는 실행하지 않았고 `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — web-ai deploy IAM policy 최종 전체본 제공

<!-- codex-turn:01a0227e-0d4d-7900-a9bf-22db0716a177 -->

- 사용자가 `ecs:ListTasks` AccessDenied 수정까지 반영한 deploy role IAM 정책 전체 JSON을 요청했다. 별도 신규 Jira 키는 없으며 AI 채점·Callback 관련 기존 범위는 `TMI-25`다.
- 최종 정책은 ECR authorization, 정확한 `tosunsaeng-ai` repository push, 정확한 `tosunsaeng-ai-service` describe/update, Task Definition register/tag와 `tosunsaeng-ecs-execution-role` PassRole 제한을 유지한다.
- `ecs:DescribeTaskDefinition`, `ecs:ListTasks`, `ecs:DescribeTasks`는 AWS authorization request와 동적 Task 조회 특성에 맞춰 resource `*`의 read-only 권한으로 허용한다. 이로써 현재 Task Definition 다운로드와 배포 후 running Task image/digest 검증 권한을 모두 포함한다.
- 정책 저장 후 기존 Role과 GitHub `AWS_ROLE_ARN`은 변경하지 않고 failed jobs를 rerun하도록 안내했다. 실제 AWS IAM, GitHub workflow와 `web-ai` 코드는 변경하지 않았고 Secret·Token을 조회하거나 기록하지 않았다.
- 분석·기록 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — 시험 시작 reserve·Session commit·confirm 의미 설명

<!-- codex-turn:01a02280-7f2a-7a72-8eda-258cddb18d88 -->

- 사용자가 시험 시작의 `5분 reserve → Session 저장 → confirm`이 무엇을 의미하는지 질문했다. 관련 기존 Identity Jira는 `TMI-95`, `TMI-96`, `TMI-98`이며 Billing/Learning Core 후속 Jira 키는 아직 없다.
- 5분은 사용자가 기다리는 시간이 아니라 Billing reservation의 TTL이다. 정상 요청은 reserve, MongoDB ExamSession commit, confirm을 수초 안에 연속 수행한 뒤 성공 응답을 반환한다.
- reserve는 사용할 10 credits/free entitlement/pass 권리를 동시 요청에서 중복 사용하지 못하도록 임시 hold하지만 아직 영구 소비하지 않는다. Session 저장 실패 시 cancel 또는 TTL 만료로 hold를 반환하고, durable Session 저장 뒤 confirm이 성공할 때 최초 AttemptGroup 소비를 확정한다.
- Session 저장은 `examId`, 사용자, operation/reservation 식별 관계가 durable commit됐다는 뜻이다. confirm 응답 유실은 같은 operation의 Billing 상태 조회·confirm 재시도로 수렴하며 새 Session이나 이중 차감을 만들지 않는다. confirm 뒤 미완료 장애는 이미 확정한 R3 무료 replacement 정책을 적용한다.
- `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·설정·테스트, 외부 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3와 retryCount 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 분석·문서 작업이므로 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-21 — reservation TTL과 시험 중 consumption 상태 구분

<!-- codex-turn:01a02282-a5a2-7030-9637-36d2ca6cefaf -->

- 사용자가 5분 reservation 잠금이 시험 도중 풀려 같은 credits를 다시 사용할 수 있는 위험을 질문했다. 관련 기존 Identity Jira는 `TMI-95`, `TMI-96`, `TMI-98`이며 Billing/Learning Core 후속 Jira 키는 아직 없다.
- 5분 TTL은 `RESERVED` 상태에만 적용한다. 정상 요청은 Session durable commit 직후 client 성공 응답 전에 `RESERVED → CONFIRMED/CONSUMED`로 전이하며, confirmed consumption은 reservation TTL 만료 대상이 아니므로 시험 도중 credits가 다시 `AVAILABLE`로 풀리지 않는다.
- confirm되지 않은 Session은 client에 사용 가능한 시험으로 반환하지 않는다. TTL 만료는 Session commit 전에 Learning Core가 중단되어 cancel되지 못한 orphan hold를 회수하는 안전장치다.
- `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·설정·테스트, 외부 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3와 retryCount 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 분석·문서 작업이므로 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-21 — 앱 전용 저장소 범위 확정과 웹 참조 정리

<!-- codex-turn:01a02287-cdf7-7eb1-b3c5-77ff00ce5a0c -->

- 사용자가 웹과 앱은 서버가 분리되어 있으므로 현재 저장소에 웹 코드가 없다면 앞으로 앱만 대상으로 하라고 확정했다. 별도 신규 Jira 키는 없으며 관련 기존 범위는 Learning Core `TMI-14`, `TMI-25`, `TMI-31`, Identity `TMI-90`, `TMI-95`, `TMI-98`이다.
- 저장소 전체를 검색한 결과 웹 백엔드·웹 프론트 애플리케이션 코드는 포함돼 있지 않다. 웹 관련 항목은 기존 POC 복제 출처 설명, 과거 WORKLOG/CURRENT_STATE 기록, 레거시 Java package namespace `web.tosunsaeng`, 공유 Python AI 저장소 이름 `web-ai`다. `web-ai`는 웹 백엔드가 아니라 앱 Learning Core도 호출하는 AI 채점 서버다.
- `AGENTS.md`의 최우선 범위를 앱 클라이언트와 Python AI 계약으로 수정하고, 명시적 요청이 없으면 웹 저장소 조회·웹 동작 검증을 수행하지 않는다고 명시했다. `README.md`에도 앱 전용 서비스와 `web.tosunsaeng` namespace의 의미를 설명하고 Legacy 모드를 로컬·테스트 앱 개발 용도로 정정했다.
- 웹 호환성을 신규 설계 제약에서 제외했지만 현재 앱 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3와 retryCount 계약은 별도 명시적 변경 결정 전까지 유지한다. 과거 WORKLOG와 CURRENT_STATE의 역사적 웹 참조는 수정하거나 삭제하지 않았다.
- 애플리케이션·설정·테스트 코드는 변경하지 않았고 분석·문서 범위 변경 작업이므로 Gradle 테스트는 실행하지 않았다. `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — 루트 경로 무인증 COMMON401 로그 진단

<!-- codex-turn:01a02295-1567-7891-b5cb-92e3aa51bfca -->

- 사용자가 `GET /` 요청이 `COMMON401`과 `InsufficientAuthenticationException`으로 거절된 WARN 로그의 의미를 질문했다. 별도 Jira 이슈 키는 없다.
- `SecurityConfig`를 확인한 결과 JWT 모드의 공개 경로는 AI Callback, Swagger와 `/actuator/health`이고 그 밖의 모든 경로는 인증 대상이다. 루트 `/`는 공개 경로도 Controller endpoint도 아니지만 Security가 MVC 404 처리보다 먼저 실행되므로 Bearer 인증 없는 요청에는 의도한 401 `BaseResponse`를 반환한다.
- 이 로그 자체는 애플리케이션 장애나 JWT 서명 검증 실패가 아니라 인증 주체 없이 보호 경로에 접근했다는 의미다. 단발이면 브라우저·외부 probe로 볼 수 있고, 30~60초 간격으로 반복되면 ALB Target Group 또는 별도 uptime probe의 health path가 `/`인지 확인해 `/actuator/health`로 맞춰야 한다. repository의 staging deploy workflow health URL은 이미 `/actuator/health`다.
- 루트 경로를 불필요하게 `permitAll`로 바꾸지 않았고 애플리케이션·설정·테스트 코드는 변경하지 않았다. 분석 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — AI Summary Callback HTTP 200과 Backend 미저장 진단

<!-- codex-turn:01a0229c-6574-7241-8f58-14a79eefad78 -->

- 사용자가 `ex_e855ed97a6_0821_0429`의 Summary dispatch 성공과 AI의 Feedback Callback HTTP 200 로그를 제공했으나 Backend가 저장하지 않은 것 같다고 질문했다. 관련 기존 Jira는 `TMI-25`이며 신규 Jira 키는 없다.
- Backend는 04:34:14.642 UTC에 generation 1 Summary 요청을 전송했고 AI는 7.651초 뒤 04:34:22.293 UTC에 Callback 200을 받았다. 따라서 HTTP 콜백이 전혀 도달하지 않았을 가능성보다 Backend handler가 수신 후 정상 200 no-op 또는 실패 분기로 종료됐을 가능성이 높다.
- Callback API는 current generation 불일치/누락, duplicate Summary, abandoned Session, completion claim 상실을 저장 없이 처리해도 200을 반환하며 이 로그들은 DEBUG다. `part_feedback`가 null/empty이면 Summary Job을 `FAILED/FEEDBACK_GENERATION_FAILED`로 전환하고 WARN 후에도 Controller는 200을 반환한다. 정상 저장이면 `요약 채점 콜백 저장 완료`와 Summary Job 완료 INFO가 있어야 한다.
- 오늘 10:22 KST에 `web-ai` generation echo 수정 배포가 성공한 기록이 있어 이번 신규 시험의 원인을 과거 generation 누락으로 단정하지 않는다. 확정 확인 대상은 Callback의 `generation_attempt=1`과 non-empty `part_feedback`, `summary_grading_jobs`의 generation/status/failureReason/completion claim, `exam_summaries` 문서, `exam_sessions` abandoned/completed 상태와 Callback URL이 현재 app Backend를 가리키는지 여부다.
- 제공된 AWS ECS Task 링크는 in-app Browser에서 AWS sign-in 화면으로 전환돼 실제 Task/CloudWatch를 조회하지 못했다. 외부 시스템을 변경하지 않았고 Secret·Token을 조회하거나 기록하지 않았다. 애플리케이션·설정·테스트 코드는 변경하지 않았으며 분석 작업이라 Gradle 테스트는 실행하지 않았다. `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — 두 Summary Job PROCESSING 재전송 상태 추가 진단

- 사용자가 두 시험의 `summary_grading_jobs` 문서를 제공했다. 관련 기존 Jira는 `TMI-25`이며 신규 Jira 키는 없다.
- 두 Job 모두 generation 1, status `PROCESSING`, `dispatchAttempt=2`이고 completion claim, 완료·실패 시각과 실패 사유가 없다. 첫 시험은 04:34:14 최초 dispatch와 04:34:22 AI Callback 200 이후 완료되지 않아 04:41:08 두 번째 dispatch로 넘어갔다.
- current generation·non-empty feedback Callback이 현재 앱 Backend의 정상 저장 경로에 진입했다면 `claimSummaryCompletion()`이 `completionClaimedGeneration=1`을 먼저 기록한다. 해당 필드가 두 Job 모두 없으므로 valid Callback이 claim 단계까지 도달하지 않았으며, empty `part_feedback`였다면 `FAILED/FEEDBACK_GENERATION_FAILED`가 남고 자동 timeout 재전송에서도 제외되므로 현재 문서와 맞지 않는다.
- 우선 원인은 AI Callback target이 현재 app Backend가 아닌 다른 Backend인 경우와 실제 Callback metadata의 `generation_attempt` 누락/불일치다. 두 시험의 반복 패턴 때문에 일회성 optimistic-lock 경합 가능성은 낮다. `exam_sessions` abandoned 여부와 `exam_summaries` 부재를 확인한 뒤 AI worker의 Callback target host, 안전한 metadata 필드와 실제 ECS image digest가 generation echo 수정본인지 대조해야 한다.
- 애플리케이션·설정·테스트와 외부 시스템은 변경하지 않았고 Secret·Token을 조회하거나 기록하지 않았다. 분석 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`를 검증한다.

## 2026-08-21 — 두 Summary Job 추가 진단 turn 기록 보완

<!-- codex-turn:01a022a0-80ad-7e32-a861-7ec27dacbc8b -->

- 종료 훅 요구에 따라 직전 두 Summary Job `PROCESSING/dispatchAttempt=2` 진단의 현재 turn 기록을 보완했다. 관련 기존 Jira는 `TMI-25`이며 신규 Jira 키는 없다.
- 두 Job 모두 completion claim 없이 같은 generation의 재전송 상태여서 현재 앱 Backend의 valid Callback 저장 경로에 진입하지 못했다는 결론, 우선 확인 대상이 Callback target과 `generation_attempt` metadata라는 결론은 동일하다.
- 애플리케이션·설정·테스트와 외부 시스템은 변경하지 않았고 Secret·Token을 기록하지 않았다. 분석 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — 동일 Callback endpoint의 문항 성공·Summary 미저장 구분

<!-- codex-turn:01a022a7-5230-7350-bfd0-c55b088a59df -->

- 사용자가 AI의 문항과 Summary가 동일한 앱 Backend `FEEDBACK_CALLBACK_URL`을 사용하고 문항 Callback은 정상 저장된다고 확인했다. 관련 기존 Jira는 `TMI-25`이며 신규 Jira 키는 없다.
- 같은 `/api/v1/exams/callback/feedback` endpoint를 사용하는 것은 정상이며 문항 저장 성공으로 Callback 목적지·네트워크·public Security 경로는 사실상 확인됐다. 차이는 URL이 아니라 JSON 분기와 검증 계약이다.
- Summary는 root-level `generation_attempt`가 현재 `SummaryGradingJob.generationAttempt`와 일치해야 하고, `suggested_total_score` 또는 generation 필드로 Summary 분기에 들어가며, `part_feedback`가 null/empty가 아니어야 한다. snake_case가 아닌 `generationAttempt`, nested metadata 또는 누락 값은 DTO에서 current generation으로 인식되지 않아 HTTP 200 stale no-op이 된다.
- 제공된 두 Job에는 completion claim도 `FAILED/FEEDBACK_GENERATION_FAILED`도 없으므로 empty feedback보다 실제 worker Callback의 `generation_attempt` 누락·이름/위치 오류·불일치가 가장 유력하다. 원문 피드백 없이 Callback 직전 `user_id`, root generation 값, `part_feedback` key count만 안전하게 확인하는 것이 다음 단계다.
- 애플리케이션·설정·테스트와 외부 시스템은 변경하지 않았고 Callback URL 원문, Secret과 Token을 문서에 기록하지 않았다. 분석 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-21 — Summary generation wire field 이름 확인

- 사용자가 Summary Callback의 generation 값을 camelCase로 보내는지 질문했다. 관련 기존 Jira는 `TMI-25`이며 신규 Jira 키는 없다.
- 외부 AI Summary 요청과 Callback JSON은 최상위 snake_case `generation_attempt`를 사용한다. Java 내부 DTO 필드만 `generationAttempt`이고 `@JsonProperty("generation_attempt")`가 wire mapping을 담당한다.
- `generationAttempt`, nested `metadata.generation_attempt`, null 또는 누락은 계약이 아니며 current generation 검증을 통과하지 못할 수 있다. 값은 현재 Job과 같은 JSON number로 보내야 한다.
- 코드·설정·테스트와 외부 시스템은 변경하지 않았고 Secret·Token을 기록하지 않았다. 분석 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`를 검증한다.

## 2026-08-21 — Summary generation wire field turn 기록 보완

<!-- codex-turn:01a022a8-95cb-7060-a397-20e83f6b5abe -->

- 종료 훅 요구에 따라 직전 Summary generation JSON field 이름 확인의 현재 turn 기록을 보완했다. 관련 기존 Jira는 `TMI-25`이며 신규 Jira 키는 없다.
- 외부 AI 요청과 Callback은 root-level snake_case `generation_attempt`를 사용하고 Java 내부 DTO만 `generationAttempt`를 사용한다는 결론은 변경되지 않았다.
- 코드·설정·테스트와 외부 시스템은 변경하지 않았고 Secret·Token을 기록하지 않았다. 분석 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-24 — Billing/Entitlement Spring Initializr 구성 가이드

<!-- codex-turn:01a03152-6497-7ed1-829a-6ccf0e76d615 -->

- 사용자가 신규 Billing 서버 생성을 위한 Spring Initializr 설정을 질문했다. Billing/Learning Core 후속 Jira 키는 아직 제공되지 않았다.
- 기존 Identity와 Learning Core가 공통으로 사용하는 Gradle Groovy, Spring Boot 3.4.2, Java 21을 기준으로 group `web.tosunsaeng`, artifact/name `app-back-end-billing`, package `web.tosunsaeng.billing`, Jar를 권장했다.
- Initializr 의존성은 Spring Web, Validation, Spring Data MongoDB, Spring Security, OAuth2 Resource Server, Actuator, Lombok으로 최소화한다. Mongo transaction·unique constraint 동시성 검증을 위한 Testcontainers와 security-test, OpenAPI·Sentry 등은 생성 후 목적에 맞게 추가한다.
- Redis를 결제·credit·TrialClaim·Reservation 원장의 진실 공급원으로 사용하지 않으며 초기 생성에서 제외한다. JPA/SQL, Kafka/SQS, AWS SDK, OAuth2 Client, Apple/Google adapter도 현재 skeleton에 선제 추가하지 않는다.
- 애플리케이션 코드는 변경하지 않았고 Secret·Token을 기록하지 않았다. 분석·설정 가이드 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-24 — 신규 Billing skeleton 읽기 전용 점검

<!-- codex-turn:01a03155-8ebc-7370-83cc-3cda0bfa0e4e -->

- 사용자가 `/Users/msde76/billing`에 추가한 Billing 프로젝트를 수정하지 말고 확인해 달라고 요청했다. Billing/Learning Core 후속 Jira 키는 아직 제공되지 않았다.
- Java 21, Gradle Groovy, group `web.tosunsaeng`, package `web.tosunsaeng.billing`, 기본 `BillingApplication`, context test와 Gradle wrapper는 생성돼 있다. Jar packaging은 Spring Boot 기본값을 사용한다.
- Spring Boot plugin은 4.1.1이고 wrapper는 Gradle 9.5.1이라 기존 Identity·Learning Core의 Boot 3.4.2 기반과 다르다. 독립 서비스라 실행 자체가 반드시 불가능한 것은 아니지만 보안·설정·테스트 코드 재사용과 운영 버전 통일 여부를 구현 전에 의도적으로 결정해야 한다.
- 현재 의존성은 `spring-boot-starter`, `spring-boot-starter-test`, JUnit launcher뿐이다. Billing API에 필요한 Web, Validation, MongoDB, Security, OAuth2 Resource Server, Actuator, Lombok과 Mongo transaction 통합 테스트 의존성은 포함되지 않았다.
- `rootProject.name`과 `spring.application.name`은 `billing`이고 프로젝트 디렉터리는 아직 Git repository가 아니다. 이는 기동 차단 사항은 아니지만 기존 `app-back-end-*` 명명 규칙과 repository 생성 절차를 적용할지 확인해야 한다.
- Billing 파일은 수정하지 않았고 Gradle 실행으로 `.gradle`/`build`를 만들지 않기 위해 테스트도 실행하지 않았다. Secret·Token을 조회하거나 기록하지 않았으며 Learning Core에서는 작업 기록 문서만 갱신했다. `git diff --check`와 marker 단일 포함 여부를 검증한다.

## 2026-08-24 — Billing 저장소 연결과 기본 설정 완료

<!-- codex-turn:01a03157-bac8-7001-8ec1-ecb08ccbd692 -->

- 브랜치: Learning Core `develop`, Billing `develop`
- Jira: 없음
- 작업 목표: 신규 Billing 프로젝트를 GitHub 원격에 연결하고 기존 앱 서비스와 정렬된 빌드·설정·보안·에이전트 작업 기반을 완성한다.
- 변경 파일: Billing의 `build.gradle`, `settings.gradle`, `.gitignore`, `.env.example`, `application.yml`, `SecurityConfig.java`, 테스트 설정·보안 테스트, `AGENTS.md`, `.codex/hooks/*`, `docs/codex/*`; Learning Core의 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 구현 내용: Billing을 로컬 Git `develop` 브랜치로 초기화하고 빈 GitHub 저장소를 `origin`으로 연결했다. Spring Boot 3.4.2·Java 21, Web·Validation·MongoDB·Security Resource Server·Actuator·Lombok·Testcontainers를 적용하고 애플리케이션 이름, 포트 8082, 환경변수 Mongo 설정과 health-only 공개 보안을 추가했다. Billing 전용 Agent 규칙, current-state/worklog 문서와 자동 주입·검사 hook도 추가했다.
- 유지한 계약: Learning Core 코드와 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3/retryCount는 변경하지 않았다. Billing 결제·entitlement API, JWT/workload wire 계약과 Apple/Google 검증은 임의 구현하지 않았다.
- 테스트: Billing `./gradlew clean test` 성공. context와 미구현 endpoint 403 차단을 검증했고 실제 MongoDB·Identity·Learning Core·스토어는 호출하지 않았다. 최종 whitespace·hook·Git 상태 검증을 수행한다.
- 결정사항: Billing 애플리케이션 이름은 `app-back-end-billing`, 기본 로컬 포트는 8082이며 인증 계약 구현 전 endpoint는 fail-closed로 둔다. MongoDB는 향후 ledger·entitlement의 source of truth로 사용한다.
- 위험 요소: 사용자 JWT audience/JWKS, Learning Core workload 인증, API·idempotency, Mongo transaction/index, 스토어 server verification과 reconciliation은 후속 구현 대상이다.
- 다음 작업: Billing API와 서비스 간 인증 계약을 먼저 확정한 뒤 reservation과 immutable ledger를 구현한다. Git commit과 push는 사용자가 직접 수행한다.

## 2026-08-24 — Billing 계약 문서의 Billing 저장소 이전 기록

<!-- codex-turn:01a03169-0a24-7150-bc76-e049d9a61cda -->

- 브랜치: Learning Core `develop`, Billing `develop`
- Jira: 없음
- 작업 목표: Billing 구현 전 계약 선택지를 설명하고 이후 Billing 기록의 단일 위치를 Billing 저장소로 확정한다.
- 변경 파일: Billing의 `AGENTS.md`, `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`; Learning Core의 종료 훅 호환용 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 구현 내용: Billing에 기존 확정사항과 C1~C13의 API 경계, 인증, 멱등성, 오류, 사용권, Reservation, AttemptGroup, store, 만료·환불, 보상·개인정보 선택지와 장단점을 기록했다. 앞으로 Billing 계약 본문과 후속 작업기록은 Billing 저장소에만 추가한다.
- 유지한 계약: 기존 상품·10-credit 비용·phone당 무료 1회·immutable ledger·5분 RESERVED TTL·reserve→Session commit→confirm·R3 replacement·Apple/Google 전용 채널은 변경하지 않았다. Learning Core 애플리케이션·공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3/retryCount도 변경하지 않았다.
- 테스트: 문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. 두 저장소의 whitespace와 marker 단일 포함을 검증한다.
- 위험 요소: 활성 Codex task의 Stop hook이 Learning Core에 연결돼 있어 이번 호환성 기록이 필요했다. C1~C13은 사용자 승인 전이며 법무·스토어 검토가 필요한 항목이 남아 있다.
- 다음 작업: Billing의 `CONTRACT_DECISIONS.md`에서 C1-A~C8-A를 순서대로 승인하고, Billing 관련 기록은 Billing 저장소에서 계속한다.

## 2026-08-24 — Learning Core·Identity Sentry 이벤트와 이메일 조건 확인

<!-- codex-turn:01a03176-aecb-7583-9e21-595921287246 -->

- 브랜치: Learning Core `develop`, Identity `develop`
- Jira: 없음
- 작업 목표: 두 앱 서버에서 어떤 오류가 Sentry event가 되고 어떤 조건에서 이메일이 가능한지 현재 코드와 문서 기준으로 설명한다.
- 변경 파일: Learning Core와 Identity의 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·설정·테스트 코드는 변경하지 않았다.
- 구현 내용: Learning Core는 DSN이 주입되면 예상 밖 Controller 500과 unhandled Runtime/Servlet 예외를 수집하고 expected 4xx, JSON parse, 인증·비즈니스 거절, 단순 ERROR와 grading/AI/Callback 운영 로그는 제외함을 확인했다. Identity는 기본 Sentry disabled이며 enabled+DSN 환경에서 예상 밖 500을 명시 capture하고 expected 4xx와 ERROR 로그를 제외함을 확인했다.
- 구현 내용: 두 서비스 모두 Logback Sentry integration과 tracing을 끄고 sanitizer로 request/user/payload·예외 메시지를 제거한다. Sentry event와 이메일은 별개이고 실제 Alert Rule·수신자·임계값은 저장소 밖 Sentry 프로젝트 설정이므로 현재 활성 이메일 조건은 저장소에서 확정할 수 없다.
- 유지한 계약: 공개 API·DTO·`BaseResponse`, Security, AI/Callback `user_id=examId`, Redis/S3/retryCount와 Identity JWT 계약은 변경하지 않았다. Secret·Token·실제 DSN과 개인정보를 조회하거나 기록하지 않았다.
- 테스트: 코드 변경 없는 정적 분석이라 Gradle 테스트와 외부 Sentry 전송은 실행하지 않았다. 관련 설정·예외 handler·reporter·sanitizer·운영 계획을 읽고 종료 전 `git diff --check`, marker 단일 포함을 검증한다.
- 위험 요소: ECS runtime에 실제 enabled/DSN/environment가 주입됐는지와 Sentry 프로젝트 Alert Rule이 켜졌는지는 저장소 밖 상태라 별도 확인이 필요하다. event가 생성돼도 메일 rule이 없거나 environment/수신자 조건이 맞지 않으면 이메일은 오지 않는다.
- 다음 작업: Sentry의 Alerts에서 prod 신규 issue·regression·급증 rule과 이메일 action을 확인하고, Settings의 개인 notification 및 프로젝트 멤버 수신 설정을 대조한다.

## 2026-08-24 — 결제 연기와 SNS·무료시험·10초 챌린지 개정 계획

<!-- codex-turn:01a032ed-fff8-7351-863d-a5737a2aa780 -->

- 브랜치: Learning Core·Identity·Billing `develop`
- Jira: 없음
- 작업 목표: 결제 기능을 후속으로 미루고 SNS 로그인, verified-phone당 무료 모의고사 1회와 10초 챌린지를 우선하는 전체 흐름과 선행 작업을 정리한다.
- 변경 파일: Learning Core의 `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `CURRENT_STATE.md`, `WORKLOG.md`; Identity와 Billing의 `CURRENT_STATE.md`, `WORKLOG.md`; Billing의 `CONTRACT_DECISIONS.md`.
- 구현 내용: Identity의 Firebase/SNS·PhoneIdentity 기반은 이미 구현돼 있고 production lifecycle·실모바일/staging 검증이 남았음을 확인했다. 무료 1회는 결제가 없어도 TrialClaim unique와 reserve/confirm 원장이 필요하며 최소 Billing/Entitlement 소유를 권장했다. 10초 챌린지는 Learning Core 별도 domain으로 제품 계약부터 시작하도록 정리했다.
- 유지한 계약: canonical UUID userId, Firebase credential과 Identity JWT 경계, raw phone 비전달, 기존 시험 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3/retryCount를 변경하지 않았다. 기존 결제 계약은 삭제하지 않고 deferred 상태로 보존한다.
- 테스트: 분석·문서 작업만 수행해 Gradle 테스트와 외부 Firebase·Sentry·MongoDB 호출은 실행하지 않았다. 종료 전 세 저장소의 `git diff --check`, trailing whitespace와 marker 단일 포함을 검증한다.
- 결정사항: TrialClaim은 기존 결정대로 최소 Billing/Entitlement가 소유한다. 코드 구현 전 추가 승인이 필요한 것은 SNS provider 범위와 10초 챌린지 MVP이며 권장 조합은 Google+Apple 우선, MEMBER·일 1개·10초 녹음·reward 없음이다.
- 위험 요소: Billing 전체를 미룬다는 이유로 TrialClaim을 생략하면 같은 전화번호의 중복 무료시험을 막을 수 없다. 10초 챌린지를 기존 ExamSession/retryCount/Callback에 억지로 합치면 외부 계약과 상태가 복잡해진다.
- 다음 작업: 세 제품 결정을 확정한 뒤 Identity 탈퇴 lifecycle 1단계 Jira, Free Trial consumer Jira, Challenge MVP Jira를 분리한다.

## 2026-08-24 — 10초 영작 챌린지 프론트 API와 누락 계약 검토

<!-- codex-turn:01a0330a-76d4-72f0-8853-98fde728591d -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 프론트가 요청한 10초 챌린지 API와 하루 3문제·문제당 1회·AI 피드백 요구를 구현 가능한 상태·시간·멱등 계약으로 보완한다.
- 변경 파일: `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 구현 내용: 다섯 프론트 API에 더해 prompt 공개와 server deadline을 원자적으로 고정하는 start API가 필요함을 정리했다. 일일 진행, 문제 attempt, AI grading 상태를 분리하고 timeout도 유일한 attempt로 계산하며 submit idempotency와 비동기 AI Job/Callback·자동 retry를 권장했다.
- 유지한 계약: 기존 시험 API·ExamSession·`retryCount`·AI Feedback Callback을 challenge에 재사용하거나 변경하지 않는다. JWT `sub`에서 userId를 얻고 외부 Body에 userId를 추가하지 않으며 기존 시험 AI `user_id=examId`, Redis/S3와 `BaseResponse` 계약을 유지한다.
- 테스트: 분석·문서 작업만 수행해 Gradle 테스트와 외부 AI 호출은 실행하지 않았다. 종료 전 `git diff --check`, trailing whitespace와 marker 단일 포함을 검증한다.
- 결정사항: 하루 3문제, 문제당 최대 1회, 10초 text 영작, AI 간단 피드백은 제품 요구로 기록했다. prompt 언어, 1초 grace, timeout 표시, 순차 진행, 일일 문제 배정, AI 결과 필드는 사용자 승인 전 권장안이다.
- 위험 요소: GET 조회로 timer를 시작하면 prefetch·재조회 side effect가 생기고 client timer만 신뢰하면 10초 제한을 우회할 수 있다. AI 실패를 사용자 재응시로 처리하면 문제당 1회 정책과 사용자 경험이 충돌한다.
- 다음 작업: 미확정 10개 항목 중 prompt 원문과 영작 방식, timeout/grace, 순차 진행, feedback fields를 먼저 승인한 뒤 API URL·DTO·오류표·Jira를 확정한다.

## 2026-08-24 — 10초 client timer와 S3 직접 업로드 계약 반영

<!-- codex-turn:01a03310-bba7-7141-837f-62b2a21522a8 -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 프론트가 10초를 측정하고 S3에 답안을 직접 업로드한다는 확인을 challenge 계약에 반영한다.
- 변경 파일: `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 구현 내용: server deadline/start/grace 판정을 제거하고 attempt 생성 시 server-generated S3 key와 presigned URL을 발급하는 흐름으로 수정했다. server는 submitted/timed_out terminal과 문제당 단일 attempt를 관리하며 client timeout 통지 API가 추가로 필요함을 기록했다.
- 유지한 계약: 기존 시험 S3 object key, Presigned URL, ExamSession, retryCount, AI/Callback 계약은 변경하지 않았다. Challenge는 별도 key·attempt·AI Job 계약으로 계획하고 userId를 외부 Body나 S3 key에 넣지 않는다.
- 테스트: 문서 변경만 수행해 Gradle 테스트와 실제 S3·AI 호출은 실행하지 않았다. 종료 전 `git diff --check`, trailing whitespace와 marker 단일 포함을 검증한다.
- 결정사항: 10초는 client UX 기준이고 server가 준수 여부를 검증하지 않는다. 문제당 1회와 하루 3문제는 server가 unique/terminal 상태로 강제한다.
- 위험 요소: 변조 client는 10초 제한을 우회할 수 있으므로 reward·ranking·경쟁 기능 추가 시 server 검증 계약을 다시 도입해야 한다. S3 artifact 형식과 timeout completion 요청은 아직 미확정이다.
- 다음 작업: S3에 업로드하는 것이 text/JSON인지 이미지·다른 파일인지 확인한 뒤 upload URL·completion DTO와 object 검증 규칙을 확정한다.

## 2026-08-24 — 한국어 prompt 기반 영어 발화 audio 계약 확정

<!-- codex-turn:01a03317-e7f9-7373-8020-014f964f78d0 -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 10초 챌린지의 실제 입력과 S3 artifact가 한국어 prompt를 영어로 영작해 발음한 audio임을 계약에 반영한다.
- 변경 파일: `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 구현 내용: Question은 한국어 prompt를 반환하고 사용자는 영어 문장을 발음해 audio를 S3에 업로드한다. AI 결과 후보를 인식 transcript, corrected answer, 의미·문법·발음 feedback으로 정리하고 정답/참고 영어 문장은 결과 전까지 숨긴다.
- 유지한 계약: client가 10초를 측정하고 server는 deadline을 검증하지 않는 최신 결정을 유지한다. 기존 시험 음성 S3 Key, ExamSession, retryCount와 AI/Callback 계약은 변경하지 않고 challenge 전용 object·attempt·Job을 사용한다.
- 테스트: 문서 변경만 수행해 Gradle 테스트와 실제 S3·AI 호출은 실행하지 않았다. 종료 전 `git diff --check`, trailing whitespace와 marker 단일 포함을 검증한다.
- 결정사항: S3 artifact는 영어 발화 audio로 확정됐다. userId, 한국어 prompt·영어 transcript와 audio 원문을 로그·Sentry에 남기지 않는다.
- 위험 요소: canonical audio format과 10초 측정 범위, AI가 발음을 실제 평가할지 단순 ASR·영작 교정만 제공할지는 아직 미확정이다.
- 다음 작업: WAV 16 kHz mono와 M4A/AAC 중 하나를 AI·앱과 확정하고 timer 범위와 결과 DTO를 동결한다.

## 2026-08-24 — 10초 챌린지 프론트엔드 API 명세 Draft 작성

<!-- codex-turn:01a0331b-1feb-7cd1-8247-3a3b04bc49f9 -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 프론트가 10초 챌린지를 구현할 수 있도록 인증, 호출 순서, URL, DTO, 상태, S3 업로드, 오류와 재시도 규칙을 하나의 명세로 정리한다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 구현 내용: 오늘 진행도, 문제 조회, attempt·Presigned PUT 발급, S3 raw audio 업로드, `submitted|timed_out` 완료 통지, cursor 기반 날짜 이력과 날짜·문제 결과 API의 요청·응답 예시를 작성했다. 실제 제출물은 text가 아니라 한국어 prompt를 영어로 영작해 발음한 audio이며, userId·S3 key·client duration을 앱이 보내지 않는 계약을 명시했다.
- 유지한 계약: 기존 시험 공개 API·DTO·`BaseResponse`, ExamSession·retryCount, 기존 시험 S3 Object Key·Presigned URL·음성 제출, AI/Callback `user_id=examId` 계약은 변경하지 않았다. Challenge API는 별도 신규 계약이며 아직 애플리케이션에 구현하거나 배포하지 않았다.
- 테스트: 문서 변경만 수행해 Gradle 테스트와 실제 S3·AI 호출은 실행하지 않았다. `git diff --check`, marker 단일 포함과 문서 내 과거 text 제출 표현 부재를 검증한다.
- 위험 요소: canonical audio 형식·크기, 10초 범위, 순차 진행, 전 사용자 공통 콘텐츠, AI 결과 DTO, 자정 제출 유예와 과거 미완료 이력은 아직 미확정이다. 문서의 audio 예시값과 Challenge 오류 code는 v1 동결 전 초안이다.
- 다음 작업: 미확정 항목을 앱·AI와 확정한 뒤 Draft를 v1로 변경하고 Challenge 백엔드 Jira를 생성한다.

## 2026-08-24 — 10초 녹음·순차 진행·공통 일일 문제 확정

<!-- codex-turn:01a03325-7f16-7880-b8ba-ac33b51fd601 -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 사용자가 확정한 녹음 시간, 문제 진행 순서와 일일 문제 배정 정책을 10초 챌린지 계약과 프론트 명세에 반영하고 과거 미완료 날짜의 의미를 설명한다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 구현 내용: 앱이 영어 발화 녹음 길이를 최대 10초로 제한하고 server는 duration을 검증하지 않는 계약, 1→2→3 순차 진행, 같은 KST 날짜에 모든 사용자가 동일한 3문제를 푸는 계약을 확정 상태로 반영했다. 미래 순서 문제의 조회·attempt는 `CHALLENGE_PREVIOUS_QUESTION_INCOMPLETE`로 거절하는 프론트 동작도 명시했다.
- 유지한 계약: 실제 제출물은 영어 발화 audio이고 text·userId·client duration·S3 key를 앱이 보내지 않는다. 기존 시험 공개 API·DTO·`BaseResponse`, ExamSession·retryCount, 기존 시험 S3·AI/Callback 계약은 변경하지 않았으며 Challenge 애플리케이션 코드는 아직 구현하지 않았다.
- 테스트: 문서 변경만 수행해 Gradle 테스트와 실제 S3·AI 호출은 실행하지 않았다. `git diff --check`, marker 단일 포함과 세 문서의 미확정 항목 정합성을 검증한다.
- 위험 요소: 과거 미완료 날짜는 세 문제 중 일부만 terminal인 채 날짜가 지난 경우다. 완료 이력 API에서 이를 제외할지 별도 미완료 상태로 보여줄지는 아직 제품 결정이 필요하며, canonical audio 형식·AI 결과 DTO·자정 제출 처리도 미확정이다.
- 다음 작업: MVP history를 완료 날짜만 반환하도록 확정할지 결정하고 audio 형식·AI 결과 DTO·자정 정책을 순서대로 동결한다.

## 2026-08-24 — 일부 참여 history 확정과 자정 attempt 정책 권장

<!-- codex-turn:01a03329-b11d-7992-a13d-320613836908 -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 세 문제를 모두 풀지 않은 날짜도 history에 보여 달라는 제품 결정을 반영하고 자정 직전 attempt 제출 정책을 제안한다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 구현 내용: 한 문제라도 attempt가 생성된 모든 참여 날짜를 history에 반환하고, 과거 `dailyStatus=in_progress`를 재개 가능이 아닌 `일부 참여`로 표시하도록 확정했다. 세 문제 완료는 필수 조건이 아니며 history 예시에 일부 참여 날짜를 추가했다.
- 구현 내용: 자정 직전 attempt는 생성 당시 challengeDate에 고정하고 `submissionDeadlineAt=attemptCreatedAt+5분`까지 제출을 허용하는 안을 권장했다. 자정 후 이전 날짜 새 attempt는 금지하고 deadline 이후에는 `CHALLENGE_ATTEMPT_EXPIRED`로 거절하며, 이 5분은 10초 녹음 검증이 아니라 S3·네트워크 복구 시간이다.
- 유지한 계약: 녹음 길이 최대 10초, 1→2→3 순차 진행, 전 사용자 공통 일일 3문제와 영어 발화 audio 제출 계약을 유지했다. 기존 시험 공개 API·DTO·`BaseResponse`, ExamSession·retryCount, 기존 S3·AI/Callback 계약과 애플리케이션 코드는 변경하지 않았다.
- 테스트: 문서 변경만 수행해 Gradle 테스트와 실제 S3·AI 호출은 실행하지 않았다. `git diff --check`, marker 단일 포함과 history·rollover 문서 정합성을 검증한다.
- 위험 요소: 5분 제출 유효시간은 권장안이며 사용자 최종 승인이 필요하다. deadline을 도입하면 프론트가 `submissionDeadlineAt`을 보관하고 answer 서버가 현재 날짜가 아닌 attempt의 저장된 challengeDate로 처리해야 한다.
- 다음 작업: attempt 생성 후 5분 제출 유효시간을 승인한 뒤 audio 형식·AI 결과 DTO와 timeout completion 계약을 확정한다.

## 2026-08-24 — ChallengeAttempt 5분 제출 유효시간 확정

<!-- codex-turn:01a0332b-f1c2-7af1-a777-1658f2857120 -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 사용자가 승인한 자정 직전 ChallengeAttempt 제출 유효시간 정책을 프론트 명세와 제품 계약에 확정 상태로 반영한다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 구현 내용: attempt를 생성 당시 challengeDate에 고정하고 `submissionDeadlineAt=attemptCreatedAt+5분`까지 제출을 허용하는 정책을 권장에서 확정으로 변경했다. 자정 이후 이전 날짜의 새 attempt 생성은 금지하고 deadline 이후 제출은 `CHALLENGE_ATTEMPT_EXPIRED`로 거절한다.
- 유지한 계약: 5분은 10초 audio duration 검증이 아니라 S3 upload·네트워크 재시도·응답 유실 복구 시간이다. 녹음 길이 최대 10초, 순차 진행, 공통 일일 3문제, 일부 참여 history와 기존 시험 API·DTO·S3·AI 계약은 변경하지 않았다.
- 테스트: 문서 변경만 수행해 Gradle 테스트와 실제 S3·AI 호출은 실행하지 않았다. `git diff --check`, marker 단일 포함과 권장/TBD 표현 제거를 검증한다.
- 위험 요소: 프론트는 `submissionDeadlineAt`을 로컬에 보관해야 하고 answer 서버는 현재 날짜가 아니라 attempt에 저장된 challengeDate를 사용해야 한다. canonical audio 형식과 AI 결과 DTO·timeout completion 계약은 아직 미확정이다.
- 다음 작업: canonical audio 형식과 AI 결과 DTO·timeout completion 계약을 순서대로 확정한다.

## 2026-08-25 — 10초 종료 시 피드백 유실 UX 검토

<!-- codex-turn:01a0373d-d16f-7722-9904-fc8af4ed5101 -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 10초 종료를 `timed_out`으로 처리해 다음 문제로 넘길 때 사용자가 발화 답안·피드백을 받지 못하는 UX 문제를 검토하고 개선 계약을 제안한다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 분석 결과: 녹음 길이 10초 도달은 응시 실패가 아니라 정상적인 녹음 자동 종료다. 이를 `timed_out`으로 처리하면 실제 audio가 있어도 AI 요청을 만들지 않아 학습 콘텐츠의 핵심인 참고 답안과 개인화 피드백을 잃는다.
- 권장 계약: 앱은 10초에 녹음을 자동 종료하고 audio를 정상 제출한다. 서버는 제출 접수 즉시 참고 영어 문장을 반환해 다음 문제를 열고, AI transcript·교정·발음 피드백은 비동기로 채운다. 무음은 `feedbackType=no_speech`, 5분 안에 S3 제출 자체를 못 한 경우만 `expired`로 분리한다.
- 유지한 계약: 녹음 길이 최대 10초, 문제당 단일 attempt, 1→2→3 순차 진행, 공통 일일 3문제, attempt 5분 제출 유효시간, 일부 참여 history와 기존 시험 API·S3·AI 계약은 유지한다. Challenge 애플리케이션 코드는 아직 구현하지 않았다.
- 테스트: UX·계약 문서 분석만 수행해 Gradle 테스트와 실제 S3·AI 호출은 실행하지 않았다. `git diff --check`, marker 단일 포함과 Draft 문서의 상태·용어 정합성을 검증한다.
- 위험 요소: `timed_out` 제거와 submit 응답의 `referenceAnswer`, `feedbackType=no_speech`, server-controlled `expired`는 아직 사용자 최종 승인이 필요하다. 무음 판정 기준과 AI 결과 DTO도 AI 서버와 확정해야 한다.
- 다음 작업: 이 UX 개정안을 승인한 뒤 timeout completion을 제거하고 audio 형식·AI 결과 DTO를 동결한다.

## 2026-08-25 — 프론트 공개 attempt 상태 단순화와 cursor 설명

<!-- codex-turn:01a03755-7cc9-73c3-b2dd-613b6ce5e983 -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 프론트가 화면 이동에만 사용하는 문제 상태를 단순화하고 history cursor의 목적을 설명한다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 구현 내용: 공개 `attemptStatus`를 `not_started|submitted` 두 값으로 줄였다. server 내부 CREATED/UPLOADING은 공개 `not_started`, 정상 제출·무음·5분 만료 terminal은 공개 `submitted`로 projection하며 history의 timeout/expired count도 제거했다.
- 구현 내용: 내부 상태는 Presigned URL 재발급, 같은 attempt 복구, submit 멱등성과 5분 deadline 처리를 위해 유지한다. 공개 상태는 챌린지 화면과 결과 화면 선택에만 사용하고 결과 차이는 `gradingStatus`, nullable feedback과 참고 답안으로 표현한다.
- 구현 내용: cursor는 직전 응답에서 어디까지 반환했는지를 나타내는 opaque 다음 페이지 토큰이며, 첫 요청에는 생략하고 응답 `nextCursor`를 다음 요청에 그대로 보내 과거 이력을 이어 받는다고 명시했다. 전체 일괄 또는 월별 조회라면 필요 없지만 현재 Draft는 무기한 이력 목록 pagination을 위해 유지한다.
- 유지한 계약: 녹음 길이 최대 10초, 순차 진행, 공통 일일 3문제, 일부 참여 history, attempt 5분 제출 유효시간과 기존 시험 공개 API·S3·AI 계약은 변경하지 않았다. Challenge 애플리케이션 코드는 아직 구현하지 않았다.
- 테스트: 문서 변경만 수행해 Gradle 테스트와 실제 S3·AI 호출은 실행하지 않았다. `git diff --check`, marker 단일 포함과 공개·내부 상태 용어 정합성을 검증한다.
- 위험 요소: public `submitted`가 정상 audio 제출과 만료 terminal을 모두 뜻하므로 DTO 문서에서 이를 화면 이동용 projection이라고 계속 명확히 해야 한다. timeout UX와 AI 결과 DTO는 아직 최종 승인이 필요하다.
- 다음 작업: cursor pagination 유지와 timeout UX 개정안을 최종 승인한 뒤 audio 형식·AI 결과 DTO를 동결한다.

## 2026-08-25 — 월별 참여 이력과 날짜별 풀이 결과 조회 계약 변경

<!-- codex-turn:01a0375f-ca0a-7653-bc53-1cbeeaa690e7 -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 날짜마다 참여 여부와 풀이 수를 확인하고 특정 날짜의 단건 또는 전체 풀이 결과를 조회하는 프론트 요구에 맞춰 history·result 계약을 단순화한다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 구현 내용: history를 `GET /api/v1/challenges/history?yearMonth=YYYY-MM` 월별 조회로 변경하고 날짜마다 `participated`와 `solvedQuestionCount`를 반환하도록 했다. 월별 최대 31건이므로 cursor, size, nextCursor와 pagination을 제거했다.
- 구현 내용: `GET /api/v1/challenges/{challengeDate}/results?questionNumber={optional}`로 날짜 결과 API를 통합했다. 번호가 있으면 해당 문제만, 없으면 그날 공개 `attemptStatus=submitted`인 문제 전체를 번호순으로 반환하며 단건·다건 모두 `questions` 배열을 사용한다.
- 구현 내용: 공개 `attemptStatus=submitted`인 정상 audio 제출·무음·5분 만료 terminal은 참여와 풀이 수에 동일하게 포함하고 아직 terminal이 아닌 `not_started`만 제외한다. 날짜 또는 문제 풀이가 없으면 200과 빈 배열을 반환한다.
- 유지한 계약: 공개 `attemptStatus=not_started|submitted`, 녹음 길이 최대 10초, 순차 진행, 공통 일일 3문제, attempt 5분 제출 유효시간과 기존 시험 공개 API·S3·AI 계약은 변경하지 않았다. Challenge 애플리케이션 코드는 아직 구현하지 않았다.
- 테스트: 문서 변경만 수행해 Gradle 테스트와 실제 외부 호출은 실행하지 않았다. `git diff --check`, marker 단일 포함과 history/result URL·DTO 정합성을 검증한다.
- 위험 요소: 현재 월은 KST 오늘까지만 반환하고 미래 월은 400으로 두는 Draft 정책이며, timeout UX와 AI 결과 DTO는 아직 최종 승인 전이다.
- 다음 작업: 월별 history·optional questionNumber 계약을 프론트와 동결한 뒤 timeout UX, audio 형식과 AI 결과 DTO를 확정한다.

## 2026-08-25 — 날짜 count/detail 분리와 server 기준 rollover 보호 검토

<!-- codex-turn:01a03774-44af-7292-ba61-7a72307f0b77 -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: questionNumber 없는 날짜 조회는 풀이 수만 반환하도록 단순화하고, 자정 직전 캐시된 풀이 수로 새 날짜의 잘못된 다음 문제에 진입하는 경합을 방지한다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 구현 내용: `GET /api/v1/challenges/{challengeDate}/results`는 `challengeDate`와 `solvedQuestionCount`만 반환한다. optional `questionNumber`가 있으면 날짜 전체 풀이 수와 해당 문제의 단일 `question` 상세를 반환하고, 미풀이 문제는 `question=null`로 처리한다.
- 분석 결과: client timer나 기기 자정 비교만으로는 23:59:59 조회 후 00:00:01 attempt 요청 같은 race와 device clock skew를 막을 수 없다. 오늘 진행도에서 server 기준 `challengeDateExpiresAt`과 `expiresInSeconds`를 반환하고 timer 만료·foreground 복귀 시 재조회하는 안을 권장했다.
- 권장 계약: question·attempt 요청에 직전 응답의 `challengeDate`를 `X-Challenge-Date`로 보내고 server가 처리 직전 현재 KST 날짜와 비교한다. 불일치하면 mutation 없이 `409 CHALLENGE_DATE_CHANGED`와 최신 날짜·만료 정보를 반환한다.
- 유지한 계약: 월별 history의 `participated`·`solvedQuestionCount`, 공개 `attemptStatus=not_started|submitted`, 10초 녹음, 순차 진행, 공통 일일 3문제, attempt 5분 제출 유효시간과 기존 시험 공개 API·S3·AI 계약은 변경하지 않았다. Challenge 애플리케이션 코드는 아직 구현하지 않았다.
- 테스트: 문서 변경만 수행해 Gradle 테스트와 실제 외부 호출은 실행하지 않았다. `git diff --check`, marker 단일 포함과 count/detail·rollover DTO 정합성을 검증한다.
- 위험 요소: expires field만으로는 전송 중 자정 경합을 막지 못하므로 server-side `X-Challenge-Date` 검증을 생략하면 안 된다. timeout UX, rollover 보호와 AI 결과 DTO는 아직 최종 승인 전이다.
- 다음 작업: rollover 보호 계약과 timeout UX를 승인한 뒤 audio 형식·AI 결과 DTO를 동결한다.

## 2026-08-25 — Challenge 공개 상태와 AI 결과 표현 문구 정리

<!-- codex-turn:01a03784-f786-7953-a117-a86324ad5d43 -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: “`timed_out` 제거 후 결과 내용을 `feedbackType`과 `gradingStatus`로 표현”한다는 문구의 의미를 검토하고 앞서 합의한 프론트 상태 단순화와 일치하도록 바로잡는다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 분석 결과: `attemptStatus`는 챌린지 화면과 결과 화면을 선택하는 값이고 `gradingStatus`는 AI 처리 진행 상태다. 별도의 공개 `feedbackType`까지 필수화하면 프론트 조건 분기가 다시 늘어나므로 기존 합의와 맞지 않는다.
- 문서 변경: 공개 `attemptStatus=not_started|submitted`와 10초 자동 종료의 정상 제출을 확정 사항으로 명시했다. 공개 `feedbackType` enum은 제거하고 무음은 `transcript=null`, nullable `aiResult`, 참고 답안과 안내 문구로 표현하도록 정리했다.
- 유지한 계약: 문제당 1회 응시, 1→2→3 순차 진행, 전 사용자 공통 일일 3문제, attempt 생성 후 5분 제출 유효시간과 기존 시험 API·DTO·S3·AI 계약은 변경하지 않았다. Challenge 애플리케이션 코드는 아직 구현하지 않았다.
- 테스트: 문서 변경만 수행해 Gradle 테스트와 실제 외부 호출은 실행하지 않았다. `git diff --check`와 turn marker 단일 포함을 검증한다.
- 위험 요소: `aiResult`의 최종 필드와 AI 최종 실패 UI, rollover 보호 계약은 아직 미확정이다.
- 다음 작업: 자정 rollover 보호 계약을 최종 승인한 뒤 canonical audio 형식과 AI 결과 DTO를 동결한다.

## 2026-08-25 — Challenge 제출 후 AI 결과 미도착 동작 명확화

<!-- codex-turn:01a037a4-454f-79b3-8c60-dbe86849af2a -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: Challenge 제출 완료 뒤 AI Callback이 아직 오지 않거나 최종 실패한 경우 결과 조회와 프론트 UX가 어떻게 동작해야 하는지 명확히 한다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 분석 결과: `attemptStatus=submitted`는 사용자 답안 접수 완료이고 `gradingStatus=completed`와 별개다. 따라서 submitted 문제의 결과 조회를 AI 결과 존재 여부에 의존시키면 안 되며, Callback 전에도 HTTP 200과 참고 답안·제출 정보·`aiResult=null`을 반환해야 한다.
- 문서 변경: 프론트 polling 중단·앱 종료가 서버 grading job을 취소하지 않고, 재진입 시 최신 상태를 다시 조회하도록 명시했다. 자동 재시도 후 최종 실패해도 `gradingStatus=failed`, attempt와 참고 답안을 유지하며 submitted 문제의 404는 서버 정합성 오류로 정의했다.
- 유지한 계약: 공개 `attemptStatus=not_started|submitted`, 비동기 `gradingStatus`, 문제당 1회 응시, 전 사용자 공통 일일 3문제, 1→2→3 순차 진행, attempt 5분 제출 유효시간과 기존 시험 API·DTO·S3·AI 계약은 변경하지 않았다. Challenge 애플리케이션 코드는 아직 구현하지 않았다.
- 테스트: 문서 변경만 수행해 Gradle 테스트와 실제 AI Callback은 실행하지 않았다. `git diff --check`와 turn marker 단일 포함을 검증한다.
- 위험 요소: 프론트 foreground polling 상한과 서버 grading timeout·최대 자동 재시도·최종 `failed` 전환 시간은 아직 확정되지 않았다.
- 다음 작업: 위 시간 정책과 자정 rollover 보호를 확정한 뒤 canonical audio 형식과 AI 결과 DTO를 동결한다.

## 2026-08-25 — Challenge AI 최종 실패 응답 구조 확정

<!-- codex-turn:01a037af-0873-78f3-9a75-3e4bd94aa703 -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: Challenge AI 자동 재시도까지 실패했을 때 프론트 결과 조회 응답 구조를 명확히 한다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 확정 계약: 결과 조회 요청은 성공했으므로 HTTP 200과 기존 `BaseResponse`의 `isSuccess=true`, `code=COMMON_200`을 유지한다. 문제 DTO는 `attemptStatus=submitted`, `gradingStatus=failed`, `gradedAt=null`, `aiResult=null`을 반환하며 prompt·submittedAt·referenceAnswer를 유지한다.
- 보안·UX: 내부 예외명, AI 응답 원문, 재시도 횟수와 failureReason은 공개 DTO에 노출하지 않는다. 프론트는 `gradingStatus=failed`에서 polling을 중단하고 참고 답안과 피드백 생성 실패 안내를 표시한다.
- 유지한 계약: 공개 `attemptStatus=not_started|submitted`, 문제당 1회 응시, 전 사용자 공통 일일 3문제, 순차 진행, attempt 5분 제출 유효시간과 기존 시험 API·DTO·S3·AI 계약은 변경하지 않았다. Challenge 애플리케이션 코드는 아직 구현하지 않았다.
- 테스트: 문서 변경만 수행해 Gradle 테스트와 실제 AI 실패 시나리오는 실행하지 않았다. `git diff --check`와 turn marker 단일 포함을 검증한다.
- 위험 요소: 서버 grading timeout·최대 자동 재시도 횟수·최종 `failed` 전환 시간과 운영 retry 후 상태 전이 정책은 아직 미확정이다.
- 다음 작업: 위 시간·운영 retry 정책과 자정 rollover 보호를 확정한 뒤 canonical audio 형식과 AI 결과 DTO를 동결한다.

## 2026-08-25 — Challenge M4A/AAC 녹음 형식 확정

<!-- codex-turn:01a037b8-ce8d-7c10-aa9b-5f03937ffd7f -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 앱이 제공하는 10초 Challenge 녹음 파일 형식을 프론트·S3·AI 연동 계약에 반영한다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 확정 계약: 녹음 파일은 `.m4a` 확장자의 M4A 컨테이너와 AAC 코덱을 사용한다. Presigned PUT 응답과 S3 object metadata의 canonical `Content-Type`은 `audio/mp4`이고 server-generated object key도 `.m4a` 확장자를 사용한다.
- 유지한 계약: 앱은 최대 10초 녹음 후 raw binary를 Presigned URL에 PUT하고, Learning Core는 audio duration을 판정하지 않는다. 문제당 1회 응시, 공개 상태·비동기 채점·기존 시험 API·DTO·S3·AI 계약은 변경하지 않았다. Challenge 애플리케이션 코드는 아직 구현하지 않았다.
- 테스트: 문서 변경만 수행해 Gradle 테스트와 실제 M4A 업로드·AI 처리는 실행하지 않았다. `git diff --check`와 turn marker 단일 포함을 검증한다.
- 위험 요소: sample rate·channel·최대 파일 크기와 AI 서버의 M4A/AAC 직접 처리 또는 내부 변환 방식은 아직 확정되지 않았다.
- 다음 작업: 앱 실제 recorder 설정에서 sample rate·channel을 확인하고 AI 서버 호환성을 검증한 뒤 최대 파일 크기를 동결한다.

## 2026-08-25 — Billing 서버 구현 시작 범위 분석

<!-- codex-turn:01a037c1-29c9-7e50-9a12-d6a84b186961 -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 별도 Billing 저장소의 현재 골격과 Identity eligibility producer, Learning Core 시험 생성 흐름을 대조해 Billing에서 우선 구현할 범위와 선후관계를 정리한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md` 및 Billing 저장소의 동일 작업 기록 문서. Learning Core 애플리케이션 코드는 변경하지 않았다.
- 분석 결과: Identity의 `PhoneEligibilityBinding` producer는 구현됐지만 Billing consumer가 없고, Learning Core `POST /api/v1/exams`는 아직 Billing reserve 없이 기존 Session을 abandon한 뒤 새 `ExamSession`을 즉시 저장한다. 따라서 Store 결제보다 phone binding inbox/current binding, 무료 1회 TrialClaim·grant ledger, 멱등 reserve/confirm/cancel/status, 5분 만료와 reconciliation을 먼저 구현하는 것이 필요하다.
- 유지한 계약: 기존 시험 생성 URL·Method·Request Body·성공 Response DTO와 `BaseResponse`, retryCount, S3·Redis·AI/Callback 계약을 변경하지 않았다. 시험 1회 10 credits, `reserve → Session commit → confirm`, raw phone 비저장과 client userId 비신뢰 원칙도 유지했다.
- 테스트: 코드 변경이 없는 분석·문서 기록 작업이므로 Gradle 테스트는 실행하지 않았다. `git diff --check`, trailing whitespace와 turn marker 단일 포함을 검증한다.
- 위험 요소: Billing 사용자 token audience, Learning Core workload issuer·audience·principal·scope, internal API DTO·오류 mapping·idempotency 보존 규칙, TrialClaim 법적 보존 기간이 아직 확정되지 않았다. Billing 저장소의 프로젝트 파일 전체도 현재 Git 미추적 상태다.
- 다음 작업: 사용자가 Billing 초기 골격을 기준선으로 commit하고 C1~C8 최소 계약을 승인한 뒤, Billing phone eligibility consumer부터 별도 Jira로 나누어 구현한다. Learning Core 연동은 Billing reserve API가 확정된 후 별도 작업으로 진행한다.

## 2026-08-25 — Part 4 문항 상세 결과에 질문 텍스트 제공

<!-- codex-turn:01a037dd-a15b-7322-99ae-0380305e0f1c -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: `GET /api/v1/exams/{examId}/questions?questionNumber=8&retryCount=0`의 Part 4 결과 화면에서 표뿐 아니라 해당 질문 문장도 이해할 수 있도록 제공한다.
- 변경 파일: `src/main/java/web/tosunsaeng/domain/exams/converter/ExamConverter.java`, `src/test/java/web/tosunsaeng/domain/exams/application/ExamOwnershipServiceTest.java`, `src/test/java/web/tosunsaeng/domain/exams/api/ExamReadApiContractTest.java`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 변경 동작: Part 4 전용 `questionInfo` 변환이 원본 `Question.question`을 누락하던 문제를 수정해 기존 `QuestionDTO.text`에 매핑했다. 앱 JSON 경로는 `result.question.questionInfo.text`이며 `tableContext`와 함께 내려간다. 별도의 새 DTO 필드는 만들지 않았다.
- 유지한 계약: 기존 URL, GET Method, `questionNumber`·`retryCount` query parameter, `BaseResponse`, 기존 응답 필드와 Part 4 `tableContext`를 유지했다. 기존에 숨기던 `tableImageUrl`, reference/intro/audio/image/time 필드는 추가 노출하지 않았고 AI/Callback, Redis, S3, retryCount와 사용자 소유권 계약도 변경하지 않았다.
- 테스트: 핵심 `ExamOwnershipServiceTest`, `ExamReadApiContractTest`가 성공했고 `./gradlew clean test --no-daemon` 전체 테스트도 성공했다. XML 기준 tests/failures/errors/skipped는 `352/0/0/0`이며 기존 unchecked 경고만 남았다. `git diff --check`도 검증한다.
- 위험 요소: catalog의 Part 4 원본 `question` 값이 null인 문서는 `@JsonInclude(NON_NULL)`에 따라 `text`가 여전히 생략되므로, 실제 Mongo catalog의 8~10번 문항에 질문 문자열이 저장돼 있어야 한다.
- 다음 작업: 앱 결과 화면에서 `result.question.questionInfo.text`를 표와 함께 표시하고, staging의 실제 Part 4 8~10번 응답에서 catalog 질문 문장이 내려오는지 확인한다.

## 2026-08-25 — 세 문항 제공 API의 Part 4 text 경로 대조

<!-- codex-turn:01a037e1-4245-7e23-bc77-edc49c345515 -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 문제를 제공하는 API가 여러 개인 상황에서 Part 4 결과 상세 converter 한 곳만 수정해도 충분한지 전체 경로를 확인한다.
- 확인 결과: `POST /api/v1/exams`는 `result.questions[].text`, `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`는 `result.text`, `GET /api/v1/exams/{examId}/questions?questionNumber=&retryCount=`는 `result.question.questionInfo.text`로 문제 원문을 제공한다. `/questions/status`와 `/retries`는 상태·회차 metadata API이며 문제 원문 조회 API가 아니다.
- 구현 판단: 시험 생성과 prompt는 기존 공통 `ExamConverter.toQuestionDTO()`를 사용해 Part 4도 이미 `Question.question → text`를 매핑한다. 결과 상세만 Part 4 전용 `toQuestionInfoDTO()`가 text를 제외했으므로 운영 코드 수정은 해당 converter 한 곳이면 충분하다.
- 추가 변경: `ExamOwnershipServiceTest`의 Part 4 prompt 서비스 테스트와 `ExamReadApiContractTest`의 prompt API 테스트에 `text` 검증을 추가해 세 경로의 계약을 명시적으로 고정했다. 기존 URL·Method·parameter·`BaseResponse`, `tableContext`, AI/Callback, Redis, S3와 retryCount 계약은 유지했다.
- 테스트: 핵심 `ExamOwnershipServiceTest`, `ExamReadApiContractTest`와 `./gradlew clean test --no-daemon`이 성공했다. 전체 tests/failures/errors/skipped는 `352/0/0/0`이고 기존 unchecked 경고만 남았다.
- 위험 요소: 세 경로 모두 catalog 원본 `Question.question`을 사용하므로 Part 4 8~10번 Mongo 문서의 `question`이 null이면 `@JsonInclude(NON_NULL)`에 따라 각 `text`가 생략된다.
- 다음 작업: staging에서 동일 examId의 생성 응답, prompt 응답과 결과 상세 응답을 실제 Part 4 문항으로 비교하고 앱은 화면 종류에 맞는 세 JSON 경로를 사용한다.

## 2026-08-25 — 세 문항 제공 API 대조 turn 기록 보완

<!-- codex-turn:01a037e1-4245-73f3-8e28-be8ed8400f25 -->

- 브랜치: `develop`
- Jira: 없음
- 종료 hook 요구에 따라 세 문항 제공 API의 Part 4 `text` 대조 결과를 현재 turn marker로 보완 기록했다. 과거 WORKLOG 항목은 수정하거나 삭제하지 않았다.
- 시험 생성은 `result.questions[].text`, prompt 조회는 `result.text`, 결과 상세는 `result.question.questionInfo.text`를 사용한다. 앞의 두 경로는 기존부터 Part 4 원문을 제공했고 결과 상세의 Part 4 전용 converter만 누락돼 운영 코드 수정은 한 곳이면 충분하다.
- prompt 서비스·API 계약 테스트를 추가 보강했으며 핵심 테스트와 전체 `./gradlew clean test --no-daemon`이 성공했다. 전체 tests/failures/errors/skipped는 `352/0/0/0`이다.
- 기존 API URL·Method·parameter·`BaseResponse`, `tableContext`, AI/Callback, Redis, S3와 retryCount 계약은 유지했고 Secret·Token은 기록하지 않았다.

## 2026-08-25 — Part 4 text 수정의 main 반영 전 Git 상태 확인

<!-- codex-turn:01a037e5-11df-7a11-b101-ec9dc5f4bfef -->

- 브랜치: checkout은 `develop`이며 별도 Jira 키는 제공되지 않았다.
- 확인 결과: `develop`, 로컬 `main`, `origin/main`은 모두 커밋 `98730c9`를 가리킨다. Part 4 converter와 테스트 변경은 아직 미커밋 working tree 상태라 `develop`이나 `main` 브랜치 이력에 들어가지 않았다.
- main 반영 대상 운영 코드는 `ExamConverter.java` 한 개이고 회귀 테스트는 `ExamOwnershipServiceTest.java`, `ExamReadApiContractTest.java` 두 개다. 작업 기록 문서도 갱신돼 있으나 현재 working tree에는 이 작업과 무관한 AGENTS/README 및 여러 문서 변경이 함께 존재한다.
- 안전한 반영 방법은 관련 파일만 선택적으로 stage/commit하고 PR로 main에 merge하는 것이다. `git add .`나 전체 working tree commit은 다른 작업을 함께 main에 넣을 위험이 있어 사용하지 않는다.
- 직전 구현 검증에서 핵심 테스트와 전체 `./gradlew clean test --no-daemon` 352개가 성공했다. 이번 turn은 Git 상태만 읽기 전용으로 확인해 테스트를 재실행하지 않았다.
- Codex는 저장소 규칙에 따라 commit·push·main 배포를 수행하지 않았고 Secret·Token도 기록하지 않았다. `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-25 — Part 4 main 반영 전 Git 상태 turn 기록 보완

<!-- codex-turn:01a037e5-11df-7eb2-af60-4095630aedb2 -->

- 브랜치: checkout은 `develop`이며 Jira 키는 제공되지 않았다.
- 종료 hook 요구에 따라 현재 turn marker로 Git 상태 확인 결과를 보완 기록했다. 과거 WORKLOG 항목은 수정하거나 삭제하지 않았다.
- Part 4 converter와 테스트 변경은 아직 미커밋 working tree 상태이며 `develop`, 로컬 `main`, `origin/main`은 모두 `98730c9`다. 즉 어느 브랜치 이력에도 아직 반영되지 않았다.
- main 긴급 반영 시 관련 운영 코드·테스트와 이번 작업 기록 hunk만 선택적으로 stage/commit한 뒤 `develop → main` PR로 병합한다. 다른 AGENTS/README/문서 변경이 함께 있으므로 `git add .`는 사용하지 않는다.
- 직전 전체 테스트 352개는 실패·오류·건너뜀 없이 성공했다. 이번 보완에서는 테스트를 재실행하지 않았고 `git diff --check`를 검증한다.
- Codex는 commit·push·배포를 수행하지 않았으며 Secret·Token을 기록하지 않았다.

## 2026-08-25 — Part 4 text 수정 파일 선택적 stage

<!-- codex-turn:01a037e7-202b-7c43-9e6f-3bdaff377c69 -->

- 브랜치: checkout은 `develop`이며 Jira 키는 제공되지 않았다.
- 사용자의 즉시 main 반영 요청에 따라 Codex 권한 범위 안에서 커밋 대상 파일을 준비했다. 저장소 규칙상 commit·push·main merge는 수행하지 않는다.
- staged 파일은 `src/main/java/web/tosunsaeng/domain/exams/converter/ExamConverter.java`, `src/test/java/web/tosunsaeng/domain/exams/application/ExamOwnershipServiceTest.java`, `src/test/java/web/tosunsaeng/domain/exams/api/ExamReadApiContractTest.java` 세 개다.
- staged diff는 Part 4 결과 상세의 `questionInfo.text` 매핑 1줄과 시험 생성·prompt·결과 상세 text 회귀 테스트 보강만 포함하며 10 insertions/3 deletions다.
- AGENTS, README, WORKLOG/CURRENT_STATE와 다른 문서 변경은 stage하지 않아 이 커밋 대상에 섞이지 않는다. 직전 전체 테스트 352개는 성공했고 staged `git diff --check`도 통과했다.
- 다음 작업은 사용자가 이 staged 변경을 커밋하고 `develop`을 push한 뒤 develop→main PR을 병합하는 것이다. Secret·Token은 기록하지 않았다.

## 2026-08-25 — Part 4 선택적 stage turn 기록 보완

<!-- codex-turn:01a037e7-202b-73b2-af29-de945f072c04 -->

- 브랜치: checkout은 `develop`이며 Jira 키는 제공되지 않았다.
- 종료 hook 요구에 따라 Part 4 수정 파일의 선택적 stage 결과를 현재 turn marker로 보완 기록했다. 과거 WORKLOG는 수정하거나 삭제하지 않았다.
- staged 파일은 `ExamConverter.java`, `ExamOwnershipServiceTest.java`, `ExamReadApiContractTest.java` 세 개뿐이며 다른 AGENTS/README/문서는 stage되지 않았다.
- 변경은 Part 4 결과 상세 `result.question.questionInfo.text` 매핑과 세 문항 제공 경로의 회귀 테스트다. 기존 외부 API·`BaseResponse`, AI/Callback, Redis, S3와 retryCount 계약은 유지한다.
- 직전 전체 테스트 352개와 staged/unstaged `git diff --check`가 성공했다. Codex는 commit·push·main merge를 수행하지 않았으며 Secret·Token을 기록하지 않았다.
- 다음 작업은 사용자가 staged 변경을 commit하고 `develop`을 push한 뒤 develop→main PR을 병합하는 것이다.

## 2026-08-25 — Part 4 text 수정 PR 준비 상태 확인

<!-- codex-turn:01a037e9-c7cd-7f52-9c66-711e3d5dd0e7 -->

- 브랜치: `develop`, Jira: 없음.
- 사용자가 commit·push 이후 PR만 남았는지 확인을 요청했다. `HEAD`, `develop`, `origin/develop`은 모두 `514fb49`이고 `develop`은 `origin/main`보다 정확히 1커밋 앞서 있다.
- 커밋 제목은 `fix: expose Part 4 question text in result`이며 main 대비 변경은 `ExamConverter.java`, `ExamReadApiContractTest.java`, `ExamOwnershipServiceTest.java` 세 파일만 포함한다. diff 규모는 10 insertions/3 deletions다.
- `git diff --check origin/main...develop`이 성공해 whitespace 오류가 없고, 다른 AGENTS/README/문서 working tree 변경은 이 커밋과 원격 develop에 포함되지 않았다.
- 직전 전체 테스트 352개가 성공했으며 이번 turn은 Git 상태와 commit diff만 읽기 전용으로 확인해 테스트를 재실행하지 않았다. 기존 API·`BaseResponse`, AI/Callback, Redis, S3와 retryCount 계약은 유지된다.
- 결론: 남은 Git 절차는 `develop → main` PR 생성, CI 확인, review 후 merge다. Codex는 PR 생성·merge·배포를 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-08-25 — Part 4 PR 준비 상태 turn 기록 보완

<!-- codex-turn:01a037e9-c7cd-7c43-bde2-4d3315c1ea9e -->

- 브랜치: `develop`, Jira: 없음.
- 종료 hook 요구에 따라 PR 준비 상태 확인 결과를 현재 turn marker로 보완했다. 과거 WORKLOG 항목은 수정하거나 삭제하지 않았다.
- `develop`과 `origin/develop`은 `514fb49`이고 `origin/main`보다 정확히 1커밋 앞선다. 커밋과 push는 완료됐으며 main 대비 diff check도 성공했다.
- PR에는 `ExamConverter.java`, `ExamReadApiContractTest.java`, `ExamOwnershipServiceTest.java` 세 파일만 포함된다. 다른 working tree 문서는 원격 commit에 포함되지 않았다.
- 직전 전체 테스트 352개가 성공했고 기존 API·`BaseResponse`, AI/Callback, Redis, S3와 retryCount 계약을 유지했다.
- 남은 절차는 develop→main PR 생성, CI 확인, review와 merge다. Codex는 PR·merge·배포를 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-08-26 — 기존 인증과 Billing workload 방식 대조

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-learning-existing-auth-review -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: Billing workload 인증을 기존 서비스와 동일하게 구성할 수 있도록 Learning Core의 inbound 사용자 인증과 outbound 서버 호출 인증을 확인한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션·설정·외부 API는 변경하지 않았다.
- 구현 내용: JWT 모드는 Identity JWKS의 RS256 key로 issuer·audience·timestamp·UUID subject를 로컬 검증한다. AI dispatch는 Authorization 없이 idempotency key만 보내며 Billing·Identity event용 workload client는 아직 없다. 기존 메커니즘을 따르려면 사용자 Access Token forwarding이 아니라 Identity-issued workload 전용 JWT client와 짧은 cache/refresh를 추가해야 한다.
- 실행한 테스트와 결과: 읽기 전용 코드·문서 분석과 작업 기록만 수행해 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: 앱은 userId를 전달하지 않고 Identity Access Token의 UUID sub를 사용한다. Python AI `user_id=examId`, 공개 API·BaseResponse·AI Callback·S3·Redis 계약과 Secret·Token 비기록을 유지했다.
- 결정사항: 현재 재사용 가능한 server-to-server 인증 구현은 없다. Identity RS256/JWKS 패턴의 workload 전용 확장이 기존 구조 일치 권장안이며 사용자 token 재사용과 AI 무인증 패턴은 Billing에 적용하지 않는다.
- 위험 요소: Identity workload token endpoint와 client credential rotation, Billing audience·token_use·scope 검증, Learning Core token cache와 발급 장애 처리가 새로 필요하다.
- 다음 작업: 사용자가 C3-E를 승인하면 Billing API 계약에 맞춰 workload credential provider와 reserve/confirm/cancel/status client를 별도 Jira로 설계한다.

## 2026-08-26 — Billing C3-D Lattice/SigV4 승인 반영

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-learning-c3d-approved -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 사용자가 승인한 C3-D에 따라 Learning Core의 Billing outbound 인증 방향을 확정 기록한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션·설정·외부 API는 변경하지 않았다.
- 구현 내용: 기존 사용자 inbound ALB·Identity JWT는 유지하고 Billing reserve/confirm/cancel/status outbound만 ECS task role credential로 VPC Lattice 요청을 SigV4 서명한다. Identity workload token client/cache는 미구현 범위로 전환했다.
- 실행한 테스트와 결과: 계약 기록만 변경해 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: 앱 userId 비전달, Identity 사용자 JWT UUID sub, 공개 시험 API·BaseResponse·AI/Callback·S3·Redis, same operation id와 Secret 비기록 원칙을 유지했다.
- 결정사항: Learning Core task role에는 Billing reservation route만 허용하고 repair-confirm은 허용하지 않는다. 기존 Load Balancer를 Lattice로 이전하지 않는다.
- 위험 요소: SigV4 HTTP signer와 request body/header canonicalization, credential refresh, timeout·same-key retry, Lattice endpoint 설정과 staging negative test가 남아 있다.
- 다음 작업: Billing API DTO가 동결되면 SigV4 signer adapter와 reservation saga client를 별도 Jira로 구현한다.

## 2026-08-27 — Billing AGENTS.md 최신화 기록 동기화

<!-- codex-turn:01a041e7-056f-71e1-bf94-e66474f45bdc -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 별도 Billing 저장소의 `AGENTS.md`를 최신 확정 계약과 다른 앱 서버 수준의 작업 규칙으로 보강하고 현재 Learning Core 작업 기록을 동기화한다.
- 변경 파일: Learning Core의 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`; Billing 저장소의 `AGENTS.md`와 작업 기록 문서. Learning Core 애플리케이션 코드는 변경하지 않았다.
- 구현 내용: Billing 저장소 전용 변경 경계, 무료 최소 Entitlement 범위, TrialClaim 3년 보존, eligibility event 멱등성·Transaction, VPC Lattice AWS_IAM·ECS task role·SigV4와 코드 리뷰 우선순위를 Billing 규칙에 반영했다.
- 실행한 테스트와 결과: 규칙·문서만 변경해 Gradle 테스트는 실행하지 않았다. 양 저장소의 `git diff --check`, trailing whitespace와 marker 단일 포함을 검증한다.
- 유지한 계약: Learning Core 공개 API·DTO·BaseResponse, retryCount, S3·Redis·AI/Callback과 시험 소유권 계약은 변경하지 않았다. Billing의 `reserve → Session commit → confirm`과 workload route 분리도 유지했다.
- 결정사항: 신규 외부 계약이나 Learning Core 구현을 추가하지 않고 이미 승인된 Billing ADR·PLAN 내용을 에이전트 작업 규칙에 반영했다.
- 위험 요소: Billing 프로젝트는 아직 전체가 Git 미추적 상태이고 실제 eligibility consumer와 Learning Core Billing client는 미구현이다.
- 다음 작업: 사용자가 Billing 초기 기준선을 commit한 뒤 Billing PLAN-001을 구현하고, Billing Reservation API가 동결되면 Learning Core SigV4 client·시험 생성 saga를 별도 작업으로 진행한다.

## 2026-08-27 — Billing AGENTS.md 전용 계약 정교화 동기화

<!-- codex-turn:01a041f8-9c74-7fa1-bacd-cbdad5cac50a -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 별도 Billing 저장소의 `AGENTS.md`를 실제 Billing ADR·PLAN 기준의 전용 작업 규칙으로 정교화하고 현재 저장소 종료 기록을 동기화한다.
- 변경 파일: Learning Core `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`; Billing `AGENTS.md`와 작업 기록 문서. Learning Core 애플리케이션은 변경하지 않았다.
- 구현 내용: Billing 제품 MVP와 PLAN-001 범위 분리, internal API·service userId 예외, Reservation wire 계약, collection/index·TTL/보존 분리와 Billing 전용 리뷰 규칙을 반영했다.
- 실행한 테스트와 결과: 규칙·문서만 변경해 Gradle 테스트는 실행하지 않았다. 양 저장소 `git diff --check`, trailing whitespace와 marker 단일 포함을 검증한다.
- 유지한 계약: Learning Core 공개 API·DTO·BaseResponse·retryCount·S3·Redis·AI/Callback은 변경하지 않았다. Billing의 Lattice/SigV4, eligibility schema와 `reserve → Session commit → confirm`도 유지했다.
- 결정사항: 신규 외부 API나 제품 정책 없이 승인된 Billing ADR-001·ADR-002·PLAN-001을 에이전트 규칙에 정확히 반영했다.
- 위험 요소: Billing event consumer와 Reservation API, Learning Core Billing client는 아직 미구현이고 Billing baseline은 Git 미추적 상태다.
- 다음 작업: Billing PLAN-001 구현 후 Reservation API가 동결되면 Learning Core SigV4 client와 시험 생성 saga를 별도 작업으로 진행한다.

## 2026-08-27 — Billing 서비스 간 통합 계약서 작성 동기화

<!-- codex-turn:01a041fc-069d-7882-86b0-e20511939516 -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 별도 Billing 저장소에 Identity·Learning Core 연동 흐름을 설명하는 통합 계약서를 추가하고 현재 저장소 작업 기록을 동기화한다.
- 변경 파일: Learning Core `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`; Billing `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, `AGENTS.md`와 작업 기록 문서. Learning Core 애플리케이션은 변경하지 않았다.
- 구현 내용: Billing 문서에 Lattice/SigV4 인증, Identity eligibility event, Learning Core Reservation saga·AttemptGroup event, 멱등성·오류·reconciliation, 개인정보·보존과 배포·변경 체크리스트를 통합했다.
- 실행한 테스트와 결과: 문서만 변경해 Gradle 테스트는 실행하지 않았다. 양 저장소 `git diff --check`와 trailing whitespace를 검증한다.
- 유지한 계약: Learning Core 공개 API·DTO·BaseResponse·retryCount·S3·Redis·AI/Callback은 변경하지 않았다. Billing ADR-001·ADR-002를 세부 계약의 최종 기준으로 유지했다.
- 결정사항: 신규 wire 계약을 만들지 않고 기존 승인 계약을 서비스 간 전달 흐름 중심으로 정리했다.
- 위험 요소: Billing consumer·Reservation API·Learning Core Billing client와 Lattice 인프라는 아직 구현 전이다.
- 다음 작업: Billing PLAN-001 consumer 구현 뒤 Reservation 계약 단계에서 Learning Core contract fixture와 saga 계획을 함께 갱신한다.

## 2026-08-27 — Billing 통합 계약 외부 검토 확인

<!-- codex-turn:01a0422f-bec9-7440-ab16-e50f878cefe8 -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 첨부된 Billing 통합 계약 검토 4건을 Billing ADR·PLAN과 Identity·Learning Core 실제 코드에 대조한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`; Billing 저장소의 작업 기록 문서. 계약·애플리케이션 코드는 변경하지 않았다.
- 분석 결과: ADR inbox 불일치, Identity Bearer publisher와 SigV4 목표 차이, Learning Core 필수 Idempotency-Key 미구현은 유효하다. Identity Retry-After 미지원은 맞지만 eligibility 409는 EVENT_ID_CONFLICT 전용이므로 COMMAND_PROCESSING과 body code 구분 주장은 적용되지 않는다.
- 실행한 테스트와 결과: 읽기 전용 분석이라 Gradle 테스트는 실행하지 않았다. 양 저장소 `git diff --check`, trailing whitespace와 marker 단일 포함을 검증한다.
- 유지한 계약: 기존 Learning Core 공개 API·DTO·BaseResponse·retryCount·S3·Redis·AI/Callback은 변경하지 않았다. Billing의 필수 UUID v4 Idempotency-Key와 Reservation saga 목표도 변경하지 않았다.
- 결정사항: 구현 순서는 ADR 보정, Billing local consumer, Identity SigV4·Retry-After, 앱·Learning Core header와 saga가 적절하다고 판정했다.
- 위험 요소: 현재 controller는 Idempotency-Key를 받지 않아 Billing 연결 전에 앱·Learning Core 동시 rollout이 필요하다. 구버전 앱 호환 전략도 구현 계획에서 명시해야 한다.
- 다음 작업: 사용자가 수정을 요청하면 먼저 Billing ADR-001 Phase 0만 보정하고, 다른 저장소 변경은 각각 별도 범위로 진행한다.

## 2026-08-27 — Billing 필수 Idempotency-Key 계약 문서 정렬

<!-- codex-turn:01a04233-1bc7-7522-91a1-f78d90815693 -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: `TMI-95` 관련 Identity transport 계약 보정; Jira 변경 없음
- 작업 목표: Billing 최신 승인 계약과 Learning Core의 과거 optional Idempotency-Key 문서 불일치를 해소하고 코드 전환 전 조건을 명시한다.
- 변경 파일: `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`, `docs/codex/FIRST_RELEASE_BILLING_BOUNDARY_REVIEW.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`; Billing·Identity 관련 계약 문서와 작업 기록.
- 구현 내용: `POST /api/v1/exams`의 lowercase UUID v4 `Idempotency-Key`를 필수 목표 계약으로 정렬하고 Request Body 없음·기존 성공 DTO 유지, header 없는 요청 거절, 앱 선배포·호환 gate와 restart의 새 key·새 examId 규칙을 명시했다.
- 실행한 테스트와 결과: 문서만 변경해 Gradle 테스트는 실행하지 않았다. optional 계약 잔류, `git diff --check`와 trailing whitespace를 종료 전에 검증한다.
- 유지한 계약: 현재 애플리케이션 코드와 공개 API runtime, BaseResponse, retryCount, AI/Callback, Redis와 S3는 변경하지 않았다. Billing `reserve → Session commit → confirm` 목표를 유지했다.
- 결정사항: 최신 제품 계약은 header optional이 아니라 필수다. transport retry만 같은 key·같은 Session을 사용하고 의도적 restart는 새 key·새 Session을 사용한다.
- 위험 요소: 실제 controller와 ExamSession은 아직 header/operation metadata를 처리하지 않는다. 필수화 시 구버전 앱이 실패하므로 앱·Learning Core 동시 rollout 전략 없이 runtime을 변경하면 안 된다.
- 다음 작업: Billing Reservation API가 준비된 뒤 앱 header 지원, Learning Core validation·Session idempotency와 Billing saga를 하나의 별도 구현 작업으로 진행한다.

## 2026-08-27 — TMI-109 UserWithdrawn inbox·deny marker·Access Token gate 구현

<!-- codex-turn:01a0423b-8d3b-76e0-bb98-784f4d37b026-learning -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: `TMI-109`; Jira 댓글·필드·상태는 변경하지 않았다.
- 작업 목표: Identity의 `UserWithdrawn` event를 Learning Core에서 멱등 소비하고, 탈퇴 전에 발급된 사용자 Access Token을 잔여 유효기간 동안 로컬 marker로 차단한다.
- 변경 파일: `src/main/java/web/tosunsaeng/domain/withdrawal/**`, `src/test/java/web/tosunsaeng/domain/withdrawal/**`, `SecurityConfig`, `AuthProperties`, `AuthStartupValidator`, `SecurityErrorResponseHandler`, `ErrorStatus`, 애플리케이션·테스트 설정과 `.env.example`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 구현 내용: workload JWT 전용 `POST /internal/v1/events/withdrawn`, canonical UUID·schemaVersion·future skew 검증, semantic SHA-256 digest, eventId inbox와 userId deny marker의 단일 Mongo Transaction, duplicate 204·payload conflict 409, late event의 inbox-only 처리를 추가했다. 사용자 JWT 인증 뒤 marker를 조회해 active marker는 `401 ACCOUNT_WITHDRAWN`, store 장애는 fail-closed `503 WITHDRAWAL_DENY_GATE_UNAVAILABLE`로 거절하고 차단 시 SecurityContext를 비운다. public AI Callback과 workload endpoint는 사용자 deny gate에서 제외한다.
- 설정·운영: consumer는 기본 비활성이다. 사용자 JWT clock skew를 명시하고 workload issuer·JWKS·audience·principal·최대 수명과 event 미래 허용 오차, Access Token 최대 수명, marker·inbox TTL을 설정 경계로 분리했다. local은 TTL index를 보장하고 staging/prod는 정확한 기존 TTL index가 없으면 기동 실패한다.
- 실행한 테스트와 결과: withdrawal·인증 집중 테스트가 성공했고 `./gradlew clean test` 전체 회귀 테스트도 성공했다. XML 기준 tests/failures/errors/skipped는 `389/0/0/0`이다. 실제 Atlas, Redis, AWS, Python AI와 외부 JWKS는 호출하지 않았다.
- 유지한 계약: 기존 공개 API URL·Method·Request/Response DTO와 `BaseResponse`, `retryCount`, S3·Redis·Polling, Python AI 요청·Callback JSON과 `user_id=examId`, 사용자 소유권 검증을 변경하지 않았다. 사용자 Access JWT를 workload 인증에 재사용하지 않았고 Token·credential·개인정보를 저장하거나 로그에 남기지 않았다.
- 결정사항: Learning Core consumer를 Identity producer보다 먼저 배포한다. marker `blockedUntil`은 `withdrawnAt + maxAcceptedAccessTokenLifetime + allowedVerifierClockSkew`, inbox cleanup은 수신 시각과 별도 retention으로 계산하며 expired marker가 TTL monitor 지연으로 남아 있어도 요청은 허용한다.
- 위험 요소: 실제 production workload JWT profile과 Access Token 최대 TTL·clock skew·inbox retention은 아직 운영 승인 전이다. Mongo Transaction은 replica set이 필요하고 multi-instance concurrency·JWKS rotation·publisher 연동은 staging E2E로 검증해야 한다.
- 다음 작업: 설정값과 workload principal을 승인한 뒤 Learning Core consumer를 비활성 상태로 선배포하고 TTL index·replica set Transaction을 검증한다. 이후 Identity `UserWithdrawn` outbox·publisher·bounded backfill을 별도 Jira로 구현한다.

## 2026-08-27 — TMI-109 Learning Core 구현 계획서 작성

<!-- codex-turn:01a0425d-a443-76f3-9068-2f9a8912cb81 -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: `TMI-109`; Jira 댓글·필드·상태는 변경하지 않았다.
- 작업 목표: Identity의 `user-withdrawn-downstream-deny-marker-stage-5-plan.md`와 Learning Core 현재 초안 구현을 대조해 TMI-109 전용 실행 계획을 작성한다.
- 변경 파일: `docs/codex/TMI-109_USER_WITHDRAWN_CONSUMER_IMPLEMENTATION_PLAN.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션·테스트 코드는 변경하지 않았다.
- 문서 내용: v1 endpoint·payload·semantic digest, inbox와 deny marker 모델, 단일 Mongo Transaction·duplicate/conflict 수렴, workload/user filter-chain 분리, Access Token deny gate, TTL·startup·관측·privacy, 단계별 구현·테스트·배포·rollback과 완료 기준을 정리했다.
- 현재 구현 판정: 핵심 초안과 단위/MVC 테스트는 존재하지만 Identity 공유 digest golden vector, 실제 replica set Transaction rollback·unique race, multi-instance marker 가시성, production workload 인증 방식·TTL 운영값, staging E2E는 미완료 gate로 남겼다.
- 실행한 테스트와 결과: 문서만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`, 변경 문서 trailing whitespace와 turn marker 단일 포함을 검증한다.
- 유지한 계약: 기존 공개 API URL·Method·parameter·DTO·`BaseResponse`, retryCount, S3·Redis·Polling, Python AI 요청·Callback JSON과 `user_id=examId`, 사용자 소유권 계약은 변경하지 않았다.
- 위험 요소: 현재 workload JWT 코드는 초안이며 Identity 기준 production 인증 방식은 SigV4/OIDC 중 미확정이다. 운영값과 replica set·staging 증거 없이 consumer를 production 활성화하면 안 된다.
- 다음 작업: Phase 0 운영 계약을 승인한 뒤 golden contract test와 replica set Transaction/concurrency test부터 보강하고, Learning Core consumer를 Identity publisher보다 먼저 배포한다.

## 2026-08-27 — TMI-109 Learning Core 구현 계획서 검토

<!-- codex-turn:01a04263-106b-7ab0-a7d8-5af576731c7e -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: `TMI-109`; 공식 Jira 본문을 읽기 전용 조회했으며 댓글·필드·상태·링크는 변경하지 않았다.
- 작업 목표: `TMI-109_USER_WITHDRAWN_CONSUMER_IMPLEMENTATION_PLAN.md`를 Jira 완료 조건, Identity Stage 5 기준 계약, 현재 Learning Core 초안 코드와 대조 검토한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드와 검토 대상 계획서는 변경하지 않았다.
- 검토 결과: endpoint·v1 payload·semantic digest·inbox/marker Transaction·deny gate·TTL·consumer 선배포라는 전체 방향은 Jira 및 Identity 문서와 일치한다. 다만 단일 `app.user-withdrawn.enabled`가 endpoint와 gate를 함께 제거해 endpoint만 내리고 기존 marker gate를 유지한다는 rollback을 실행할 수 없고, 같은 userId·다른 eventId의 concurrent marker unique 충돌은 현재 inbox-only loser 재조회에서 계약상 409 대신 503으로 분류될 수 있다.
- 검토 결과: replica set 미지원 시 startup fail-fast를 acceptance로 두었지만 현재 계획의 구현 단계는 실제 capability probe 또는 배포 검증 방식이 명확하지 않다. Jira가 요구한 후속 Identity producer 이슈의 blocks 관계도 현재 issue link가 0건이다. golden digest vector, 실제 replica set rollback·unique race, multi-instance 가시성, production workload 인증·TTL 값과 staging E2E는 계획서가 올바르게 production blocker로 기록했다.
- 실행한 테스트와 결과: 읽기 전용 계획 검토라 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`와 turn marker 단일 포함을 검증한다.
- 유지한 계약: 기존 공개 API URL·Method·parameter·DTO·`BaseResponse`, retryCount, S3·Redis·Polling, Python AI 요청·Callback JSON과 `user_id=examId`, 사용자 소유권 계약을 변경하지 않았다.
- 위험 요소: 위 세 설계·추적 차이를 닫지 않고 production을 활성화하면 rollback 중 old token 차단이 해제되거나, 실제 conflict가 일시 장애로 오분류되고, standalone Mongo 오구성이 runtime까지 발견되지 않을 수 있다.
- 다음 작업: 계획서에 consumer/gate 독립 rollback 제어, marker unique race의 명시적 재분류 알고리즘과 replica set startup 검증 방식을 추가하고, Identity producer Jira를 생성·blocks 링크한 뒤 기존 Phase 0~5 gate를 수행한다.

## 2026-08-27 — TMI-109 계획서 검토 결과 독립 확인

<!-- codex-turn:01a04269-0cc3-72b3-8270-5d1bdb4cd582 -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: `TMI-109`; issue와 관련 이슈를 읽기 전용 조회했으며 댓글·필드·상태·링크는 변경하지 않았다.
- 작업 목표: 전달받은 TMI-109 계획서 검토 4건을 현재 계획서, Learning Core 초안 코드와 Jira 추적 상태에 독립 대조한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 계획서와 애플리케이션·테스트 코드는 변경하지 않았다.
- 확인 결과: 단일 `app.user-withdrawn.enabled`가 endpoint·workload chain·repository·deny gate를 함께 제거하므로 현재 rollback 문구를 실행할 수 없다는 지적은 유효하다. endpoint와 gate 플래그를 분리하고 consumer 활성은 gate 활성에 종속시키는 보완을 권장한다.
- 확인 결과: 동시 같은 userId·다른 eventId의 marker unique 충돌에서 loser가 자기 inbox만 재조회해 503이 될 수 있다는 지적도 유효하다. inbox 이후 userId marker를 bounded 재조회하고 다른 `sourceEventId`가 확정되면 409, winner 미가시성만 503으로 고정해야 한다.
- 확인 결과: `MongoTransactionManager` bean 생성만으로 standalone Mongo를 startup에서 거절하지 못한다. consumer 활성 startup에서 canary document를 실제 Transaction으로 write한 뒤 abort하고 잔존 0건을 확인하며, 실패 시 기동을 중단하는 capability probe와 증거 기록을 권장한다.
- Jira 확인: TMI-109의 issue link는 0건이고, `UserWithdrawn` 관련 검색에서도 producer/outbox/publisher 전용 후속 이슈를 찾지 못했다. 후속 Identity Jira를 생성한 뒤 TMI-109가 이를 blocks하도록 연결해야 한다.
- 실행한 테스트와 결과: 읽기 전용 분석과 기록 문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. `git diff --check`, trailing whitespace와 현재 turn marker 단일 포함을 검증한다.
- 유지한 계약: 기존 공개 API URL·Method·parameter·DTO·`BaseResponse`, retryCount, S3·Redis·Polling, Python AI 요청·Callback JSON과 `user_id=examId`, 사용자 소유권 계약은 변경하지 않았다.
- 위험 요소: 위 보완 전 production 활성화는 rollback 시 old Access Token 차단 해제, conflict의 503 오분류, Transaction 미지원 환경의 runtime 장애 위험이 있다.
- 다음 작업: 사용자가 계획서 수정을 요청하면 위 세 설계 결정을 Phase 2·4·6·Rollback·acceptance에 반영하고, 별도 승인 후 Identity producer Jira 생성과 blocks 링크를 수행한다.

## 2026-08-27 — TMI-109 production 보완 계획 및 후속 Jira 반영

<!-- codex-turn:01a0426b-779b-7e32-adb4-395d21114dca -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: `TMI-109`, 신규 `TMI-111`; `TMI-111`을 High `작업`으로 생성하고 `TMI-109 blocks TMI-111` 관계를 연결·재조회했다. 상태 전환과 댓글은 수행하지 않았다.
- 작업 목표: 조건부 승인 검토의 rollback, marker unique race, Mongo Transaction capability와 producer 추적 누락을 TMI-109 계획에 반영한다.
- 변경 파일: `docs/codex/TMI-109_USER_WITHDRAWN_CONSUMER_IMPLEMENTATION_PLAN.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션·테스트 코드는 변경하지 않았다.
- 계획 변경: 목표 설정을 `consumer-enabled`와 `deny-gate-enabled`로 분리하고 consumer 활성은 gate 활성에 종속시켰다. consumer rollback 시 endpoint·workload chain·Transaction 처리는 내리되 marker repository와 사용자 JWT gate는 유지하며, marker 생성 뒤 두 flag 동시 false rollback을 금지했다.
- 계획 변경: `DuplicateKeyException` loser가 eventId inbox를 먼저 확인한 뒤 inbox가 없으면 userId marker를 bounded 재조회한다. 다른 `sourceEventId`가 보이면 409, 같은 source인데 inbox가 없거나 winner가 아직 보이지 않으면 계속 재조회하고 제한 시간 안에도 미확정일 때만 503으로 처리한다.
- 계획 변경: staging/prod consumer startup에서 전용 probe collection에 canary를 실제 Transaction으로 write하고 abort한 뒤 잔존 0건을 확인한다. 실패하면 pod readiness를 열지 않고 rollout과 Identity publisher 활성화를 중단하며 기존 gate-enabled release를 보존한다.
- Jira 변경: Identity의 withdrawal outbox 원자 생성, at-least-once publisher, lease/retry/dead-letter, bounded backfill, workload 인증과 staging E2E를 `TMI-111` 범위로 기록했다.
- 실행한 테스트와 결과: 계획·기록 문서와 Jira 추적만 변경해 Gradle 테스트는 실행하지 않았다. 계획서 계약 검색, `git diff --check`, trailing whitespace와 Jira link 방향을 검증한다.
- 유지한 계약: 기존 공개 API URL·Method·parameter·DTO·`BaseResponse`, retryCount, S3·Redis·Polling, Python AI 요청·Callback JSON과 `user_id=examId`, 사용자 소유권 계약은 변경하지 않았다.
- 위험 요소: 실제 분리 flag, marker race 재분류와 startup probe는 아직 애플리케이션 코드에 구현되지 않았다. workload 인증·운영 TTL 값, replica set·multi-instance·staging E2E도 production blocker로 남아 있다.
- 다음 작업: TMI-109 구현 시 Phase 1 golden vector, Phase 2 replica set race, Phase 4 분리 설정·startup probe 순서로 보강한 뒤 전체 테스트와 staging gate를 통과한다. 그 후에만 TMI-111 publisher를 활성화한다.

## 2026-08-27 — TMI-109 수정 계획서 재검토

<!-- codex-turn:01a0426f-bec5-7951-8c84-8078d9d56c73 -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: `TMI-109`, 후속 Identity producer `TMI-111`; 두 이슈와 link를 읽기 전용 재조회했으며 댓글·필드·상태·링크를 변경하지 않았다.
- 작업 목표: 이전 검토 지적을 반영한 `TMI-109_USER_WITHDRAWN_CONSUMER_IMPLEMENTATION_PLAN.md`가 Jira·Identity 계약과 현재 초안 구현의 gap을 실행 가능하게 닫았는지 재검토한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드와 검토 대상 계획서는 변경하지 않았다.
- 검토 결과: 단일 flag를 `consumer-enabled`와 `deny-gate-enabled`로 분리하고 consumer-only 금지 조합, endpoint-only rollback과 smoke test를 명시해 old token 차단 해제 위험을 해소했다. unique loser가 eventId inbox 뒤 userId marker와 sourceEventId를 bounded backoff로 재조회해 같은 userId·다른 eventId를 409로 분류하도록 알고리즘·replica set acceptance도 구체화했다.
- 검토 결과: staging/prod consumer startup에 사전 생성 probe collection, TransactionTemplate canary write·abort, rollback 후 잔존 0건 확인과 readiness 차단을 추가해 standalone Mongo 또는 권한 오구성의 runtime 지연 발견 문제를 해소했다. 기존 golden digest, 실제 replica set concurrency, workload auth·multi-instance staging E2E와 운영값 승인 gate도 유지됐다.
- 남은 발견: 계획서와 Jira 설명은 `TMI-109 blocks TMI-111`을 요구하지만 실제 Jira REST link는 `TMI-109`에서 inward issue `TMI-111`, `TMI-111`에서 outward issue `TMI-109`로 조회되어 현재 의미는 `TMI-111 blocks TMI-109`다. 구현 rollout 전에 link 방향을 반대로 교정해야 한다.
- 실행한 테스트와 결과: 계획·Jira 재검토만 수행해 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`와 marker 단일 포함을 검증한다.
- 유지한 계약: 기존 공개 API URL·Method·parameter·DTO·`BaseResponse`, retryCount, S3·Redis·Polling, Python AI 요청·Callback JSON과 `user_id=examId`, 사용자 소유권 계약을 변경하지 않았다.
- 위험 요소: Jira dependency 방향을 교정하기 전 자동화·운영자가 producer를 선행 작업으로 오해할 수 있다. 계획서에 이미 기록된 production 운영값과 실제 인프라 E2E는 여전히 구현 완료 gate다.
- 다음 작업: Jira link를 `TMI-109 blocks TMI-111` 방향으로 교정한 뒤 계획서 Phase 0부터 진행한다. 구현 시 flag 분리, marker race 수렴, capability probe를 먼저 테스트로 고정한다.

## 2026-08-27 — TMI-109 Jira link 교정 준비와 구현 계획 설명

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: `TMI-109`, `TMI-111`; TMI-109 화면에서 현재 관계가 `다음에 의해 차단됨: TMI-111`임을 확인했다. 아직 link를 변경하지 않았다.
- 작업 목표: 잘못 연결된 Jira dependency만 올바른 `TMI-109 blocks TMI-111`로 교정하고, 애플리케이션 구현 없이 승인된 TMI-109 계획을 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션·테스트·계획서 코드는 변경하지 않았다.
- 확인 결과: 현재 Jira 관계는 producer `TMI-111`이 consumer `TMI-109`를 막는 반대 방향이다. 교정은 기존 link 한 건 해제 후 같은 Blocks 타입으로 반대 방향 link 한 건 생성하는 작업이며 issue 본문·상태·댓글은 건드리지 않는다.
- 구현 계획 요약: Phase 0 운영 계약 확정, Phase 1 wire/digest golden vector, Phase 2 Mongo Transaction·unique race, Phase 3 workload/user security와 deny gate, Phase 4 분리 flag·TTL·Transaction capability·관측, Phase 5 전체 회귀와 staging E2E, Phase 6 Learning Core 선배포 후 TMI-111 publisher 활성화 순서다.
- 실행한 테스트와 결과: 구현하지 않았으므로 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`를 검증한다.
- 유지한 계약: 공개 API·DTO·BaseResponse·AI/Callback·S3·Redis와 `user_id=examId`를 변경하지 않았다. Secret과 Token은 조회하거나 기록하지 않았다.
- 위험 요소: Jira link 삭제·재생성은 cloud 상태 변경이므로 브라우저 실행 직전 사용자 확인이 필요하다. 확인 전에는 기존 link를 유지한다.
- 다음 작업: 사용자 확인 후 기존 dependency link만 해제하고 `TMI-109 blocks TMI-111`로 재생성한 뒤 양 Jira 화면/REST로 방향을 재검증한다. 애플리케이션 구현은 별도 요청 전 시작하지 않는다.

## 2026-08-27 — TMI-109 링크 교정 대기 및 구현 계획 설명 종료 기록

<!-- codex-turn:01a04271-9457-7d21-9272-5b3a14bdc599 -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: `TMI-109`, `TMI-111`; 현재 `TMI-111 blocks TMI-109`인 반대 방향 관계를 확인했으며 Jira 변경은 아직 수행하지 않았다.
- 작업 목표: Jira dependency 교정 범위를 확정하고, 애플리케이션 구현을 시작하지 않은 채 TMI-109 구현 계획을 사용자에게 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션·테스트·계획서는 변경하지 않았다.
- 작업 결과: 기존 link 한 건 해제 후 `TMI-109 blocks TMI-111` link 한 건 생성이 필요한 상태임을 확인했다. 브라우저의 cloud link 삭제·생성은 실행 직전 확인이 필요해 사용자에게 확인을 요청하고 기존 관계를 유지했다.
- 계획 설명: 운영값·workload 인증 확정, wire/digest golden vector, Mongo Transaction·unique race, security와 deny gate, consumer/gate 분리 flag·TTL·startup capability probe·관측, 전체 회귀와 staging E2E, Learning Core 선배포 후 TMI-111 publisher 활성화 순서로 안내했다.
- 실행한 테스트와 결과: 구현 변경이 없어 Gradle 테스트는 실행하지 않았다. `git diff --check`와 marker 단일 포함을 종료 전에 검증한다.
- 유지한 계약: 기존 공개 API·DTO·BaseResponse, retryCount, S3·Redis·Polling, Python AI 요청·Callback JSON과 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 위험 요소: Jira link가 교정되기 전에는 dependency 표시가 실제 rollout 순서와 반대다. 애플리케이션 구현과 production 운영값·E2E는 계속 미착수 상태다.
- 다음 작업: 사용자가 link 삭제·재생성을 확인하면 Jira 관계만 교정하고 방향을 재검증한다. 애플리케이션 구현은 별도 요청 전 시작하지 않는다.

## 2026-08-27 — TMI-109 → TMI-111 Jira dependency 방향 교정

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: `TMI-109`, `TMI-111`; 사용자 확인 후 dependency link 방향만 변경했다. 상태·본문·댓글은 변경하지 않았다.
- 작업 목표: 반대로 연결된 `TMI-111 blocks TMI-109` 관계를 계획된 rollout 순서인 `TMI-109 blocks TMI-111`로 교정한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션·테스트·구현 계획서는 변경하지 않았다.
- Jira 변경: 기존 Blocks link 한 건을 해제하고, TMI-109를 inward issue·TMI-111을 outward issue로 하는 Blocks link 한 건을 생성했다.
- 검증 결과: TMI-109 재조회에는 `outwardIssue=TMI-111`, TMI-111 재조회에는 `inwardIssue=TMI-109`가 동일한 신규 link 한 건으로 나타났다. TMI-109 화면도 연결된 업무 항목을 `차단: TMI-111`로 표시한다.
- 실행한 테스트와 결과: 애플리케이션 구현이 없어 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`를 검증한다.
- 유지한 계약: 기존 공개 API·DTO·BaseResponse, retryCount, S3·Redis·Polling, Python AI 요청·Callback JSON과 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 삭제 결과와 복구성: 잘못된 방향의 Jira link만 삭제했으며 동일 link 자체는 복원하지 않았다. 필요한 관계는 반대 방향의 신규 link로 즉시 재생성되어 현재 의도한 dependency가 유지된다.
- 다음 작업: 별도 구현 요청 전에는 TMI-109 애플리케이션 작업을 시작하지 않는다. 구현 시 승인된 계획의 Phase 0 운영 계약 확정부터 진행한다.

## 2026-08-27 — TMI-109 dependency 교정 완료 상태 재동기화

- 날짜: 2026-08-27
- 현재 확인 브랜치: `feat/TMI-109-withdrawal-deny-gate-consumer`; 브랜치 생성이나 애플리케이션 구현은 이번 Jira 작업에서 수행하지 않았다.
- Jira: `TMI-109`, `TMI-111`; 기존 반대 방향 link를 삭제하고 `TMI-109 blocks TMI-111`로 교정 완료했다.
- 검증 결과: Jira API에서 TMI-109는 `outwardIssue=TMI-111`, TMI-111은 `inwardIssue=TMI-109`를 반환하며, TMI-109 화면은 `차단: TMI-111`로 표시한다. link는 한 건이며 순환 관계가 없다.
- 변경 범위: Jira dependency link와 작업 기록 문서만 변경했다. issue 본문·상태·댓글과 애플리케이션·테스트·구현 계획서는 변경하지 않았다.
- 실행한 테스트와 결과: 애플리케이션 변경이 없어 Gradle 테스트는 실행하지 않았다. `git diff --check`를 검증한다.
- 유지한 계약: 공개 API·DTO·BaseResponse, AI/Callback, S3·Redis와 `user_id=examId` 계약은 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 다음 작업: 사용자의 별도 구현 지시 전에는 이 turn에서 애플리케이션 구현을 진행하지 않는다.

## 2026-08-27 — TMI-109 dependency 교정 완료 종료 훅 기록

<!-- codex-turn:01a04274-3e06-7713-9aa6-117e77066ab1 -->

- 날짜: 2026-08-27
- 브랜치: `feat/TMI-109-withdrawal-deny-gate-consumer`
- Jira: `TMI-109`, `TMI-111`; 반대 방향 link를 제거하고 `TMI-109 blocks TMI-111` 관계로 교정했다.
- 작업 목표: Jira dependency 방향 교정 결과와 검증 상태를 종료 훅 요구 형식으로 기록한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션·테스트·계획서는 변경하지 않았다.
- 검증 결과: TMI-109 API는 `outwardIssue=TMI-111`, TMI-111 API는 `inwardIssue=TMI-109`를 반환하며 TMI-109 화면은 `차단: TMI-111`을 표시했다. 순환 link는 없다.
- 실행한 테스트와 결과: 애플리케이션 변경이 없어 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`와 marker 단일 포함을 검증한다.
- 유지한 계약: 공개 API·DTO·BaseResponse, retryCount, S3·Redis·AI/Callback과 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 다음 작업: 별도 구현 요청 전에는 TMI-109 애플리케이션 구현을 시작하지 않는다.

## 2026-08-27 — TMI-109 구현 내용 설명

- 날짜: 2026-08-27
- 브랜치: `feat/TMI-109-withdrawal-deny-gate-consumer`
- Jira: `TMI-109`; 후속 Identity producer는 `TMI-111`이며 dependency는 `TMI-109 blocks TMI-111`로 교정된 상태다.
- 작업 목표: 애플리케이션 구현을 수행하지 않고 TMI-109에서 구현할 동작·데이터·보안·운영·테스트 범위를 사용자에게 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션·테스트·계획서·Jira는 변경하지 않았다.
- 설명 내용: workload 전용 event endpoint, v1 validation·semantic digest, inbox/deny marker 단일 Mongo Transaction, duplicate/conflict 동시성 수렴, 사용자 JWT 이후 deny gate, 분리 feature flag, TTL index, Transaction startup probe, 낮은 cardinality 관측과 단계적 rollout을 하나의 흐름으로 정리했다.
- 외부 동작: active marker는 `401 ACCOUNT_WITHDRAWN`, marker store 장애는 fail-closed `503 WITHDRAWAL_DENY_GATE_UNAVAILABLE`, 동일 event duplicate는 body 없는 204, payload 또는 user 관계 conflict는 body 없는 409로 처리한다.
- 실행한 테스트와 결과: 설명 작업이라 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`를 실행한다.
- 유지한 계약: 기존 공개 API·DTO·BaseResponse, retryCount, S3·Redis·Polling, Python AI 요청·Callback JSON과 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 위험 요소: production workload 인증 방식과 운영 duration, 실제 replica set·multi-instance·publisher 연동 증거는 아직 구현 완료 gate다.
- 다음 작업: 사용자가 구현을 명시적으로 요청하면 계획의 Phase 0 운영값 확인 후 golden vector, Transaction/race, flag/probe 순서로 진행한다.

## 2026-08-27 — 1차 업데이트 전체 진행 상태 체크

<!-- codex-turn:01a04272-3afa-79c3-9cea-9eb1e2d7485d -->

- 날짜: 2026-08-27
- 브랜치: `feat/TMI-109-withdrawal-deny-gate-consumer`
- Jira: Identity `TMI-90`~`TMI-98`, `TMI-103`, `TMI-104`, `TMI-107`, `TMI-108`; Learning Core `TMI-109`; Billing `TMI-110`; Identity `TMI-111`; Challenge `TMI-102`, `TMI-105`, `TMI-106`. Jira는 읽기 전용 조회했고 상태·댓글·필드·링크를 변경하지 않았다.
- 작업 목표: 현재 정의된 1차 업데이트 `SNS 로그인 + 검증 전화번호당 무료 모의고사 1회 + 10초 챌린지`를 서비스별 production checklist로 재구성하고 실제 진행 상태를 확인한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션·테스트·계약 문서는 변경하지 않았다.
- Identity 결과: Firebase broker, PhoneIdentity, signup finalize, eligibility publisher, Guest upgrade/merge와 탈퇴 lifecycle의 관련 Jira는 완료다. 실제 모바일 Google/Apple/Phone 설정·staging E2E와 feature flag 활성화, Billing용 production SigV4 transport는 아직 완료 증거가 없다. `UserWithdrawn` producer/outbox/backfill `TMI-111`은 해야 할 일이다.
- Billing 결과: eligibility event consumer `TMI-110`은 완료이고 33개 테스트 기록이 있다. TrialClaim, FREE_EXAM_ONCE ledger, reserve/confirm/cancel/status, reconciliation, UserMerged consumer와 실제 Lattice/IAM/SG는 미구현이다.
- Learning Core 결과: 기존 JWT·시험 Session·배정·S3·AI 채점·Callback·polling 기반은 구현돼 있다. `TMI-109` withdrawal consumer는 초안과 테스트 기록이 있지만 미커밋·미추적이며 추가 production 보완 코드가 남았다. UserMerged consumer, Billing saga·필수 Idempotency-Key runtime, AttemptGroup/R3와 Challenge backend는 없다.
- Challenge·Client 결과: `TMI-105` 문제 생성은 Jira 완료, `TMI-102` UI와 `TMI-106` 채점 agent는 진행 중이다. Jira 설명과 해당 저장소 코드를 이번 범위에서 확인할 수 없어 완료 품질은 상태 이상으로 추정하지 않았다. Challenge Learning Core backend Jira·domain·API는 없고 audio 세부값·rollover·AI 결과/timeout 계약도 일부 미확정이다.
- 추적 결과: 최신 Jira 재조회에서 `TMI-109`의 outward issue가 `TMI-111`, `TMI-111`의 inward issue가 `TMI-109`로 확인돼 현재 dependency는 올바른 `TMI-109 blocks TMI-111`이다.
- 종합 판정: Phase 1 기반은 대부분 완료됐지만 무료시험 종단 vertical slice와 Challenge 서버 구현, 모바일·workload·staging production E2E가 남아 있어 1차 업데이트는 아직 출시 가능 상태가 아니다.
- 실행한 테스트와 결과: 읽기 전용 상태 분석과 기록 문서만 변경해 Gradle 테스트는 실행하지 않았다. Jira 상태와 각 저장소 branch/status를 조회하고 `git diff --check`, trailing whitespace와 turn marker 단일 포함을 검증한다.
- 유지한 계약: 기존 공개 API·DTO·`BaseResponse`, retryCount, S3·Redis·AI/Callback `user_id=examId`, phone candidate 비노출과 Billing/Learning Core 책임 경계를 변경하지 않았다.
- 위험 요소: Jira 완료와 production readiness는 동일하지 않다. 실제 workload 인증, mobile/provider 설정, replica set·multi-instance·response-loss, rollback과 environment 분리 E2E가 없으면 feature flag를 열면 안 된다.
- 다음 작업: 우선 TMI-109를 production 계획대로 완성·병합하고, Billing TrialClaim+Reservation vertical slice와 Learning Core reserve/confirm saga Jira를 만든다. 그와 병렬로 Challenge backend 계약의 남은 값을 동결하고 Learning Core 구현 Jira를 만든 뒤 staging 통합 순서로 진행한다.

## 2026-08-27 — TMI-109 구현 설명 기록 재동기화

- Jira: `TMI-109`, 후속 `TMI-111`; dependency는 `TMI-109 blocks TMI-111`이다.
- 결과: 구현 없이 consumer, inbox/marker Transaction, JWT deny gate, 분리 flag, concurrency 수렴, startup probe, TTL·관측·테스트·rollout 범위를 설명했다.
- 테스트: 코드 변경이 없어 Gradle 테스트를 실행하지 않았고 `git diff --check`가 통과했다.
- 계약: 공개 API·BaseResponse·AI/Callback·S3·Redis와 Secret 비기록 원칙을 유지했다.
- 다음 작업: 명시적 구현 요청 전에는 애플리케이션을 수정하지 않는다.

## 2026-08-27 — TMI-109 구현 설명 종료 훅 기록

<!-- codex-turn:01a04276-ab38-7382-a464-1bd3b5be7cac -->

- 날짜: 2026-08-27
- 브랜치: `feat/TMI-109-withdrawal-deny-gate-consumer`
- Jira: Learning Core `TMI-109`, 후속 Identity producer `TMI-111`; 관계는 `TMI-109 blocks TMI-111`이다.
- 작업 목표: TMI-109에서 실제 구현할 consumer·deny gate·운영 보완 범위를 사용자에게 설명하고 종료 기록을 남긴다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션·테스트·계획서·Jira는 변경하지 않았다.
- 설명 결과: workload endpoint와 v1 validation·digest, inbox/marker 단일 Transaction, duplicate 204·conflict 409·미확정 503, JWT 이후 active marker 401과 store 장애 503, TTL 직접 비교, consumer/gate flag 분리, Transaction startup probe, observability·replica set·staging E2E와 rollout 순서를 정리했다.
- 실행한 테스트와 결과: 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`와 marker 단일 포함을 검증한다.
- 유지한 계약: 공개 시험 API·DTO·BaseResponse, retryCount, S3·Redis·Polling, Python AI 요청·Callback JSON과 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 다음 작업: 사용자가 별도로 구현을 요청하면 Phase 0 운영값 확인 후 계획 순서대로 진행한다.

## 2026-08-28 — TMI-109 production 보완 구현 완료

<!-- codex-turn:01a045ff-9610-76c0-9fbf-1a846b547bab -->

- 날짜: 2026-08-28
- 브랜치: `feat/TMI-109-withdrawal-deny-gate-consumer`
- Jira: Learning Core `TMI-109`, 후속 Identity producer `TMI-111`; dependency는 `TMI-109 blocks TMI-111`이다. Jira 상태·본문·댓글·링크는 변경하지 않았다.
- 작업 목표: 승인된 TMI-109 계획에 따라 탈퇴 event consumer와 사용자 접근 deny gate 초안을 production rollout 가능한 구조로 보완한다.
- 변경 파일: `.env.example`, `README.md`, `src/main/resources/application.yml`, `src/test/resources/application-test.yml`, 전역 security/auth/error 설정, `src/main/java/web/tosunsaeng/domain/withdrawal/`의 API·application·config·domain·repository·security 코드, 대응하는 `src/test/java/web/tosunsaeng/domain/withdrawal/` 테스트, `docs/runbooks/TMI-109_USER_WITHDRAWN_MONGO_SETUP.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 변경하거나 추가했다. 작업 트리의 관련 없는 기존 변경과 문서는 보존했다.
- 구현 결과: `consumer-enabled`와 `deny-gate-enabled`를 분리하고 `true/false` 조합을 startup failure로 차단했다. deny repository는 gate 또는 consumer가 켜지면, inbox repository는 consumer가 켜질 때만 등록한다. gate-only rollback에서도 deny marker TTL 검증은 유지한다.
- 멱등성·동시성: marker unique race 후 inbox와 userId marker를 총 250ms, 10ms backoff, 최대 25회로 bounded 재조회한다. 다른 `sourceEventId`가 확인되면 409, 같은 source인데 inbox가 없거나 승자를 확정하지 못하면 503으로 처리한다.
- 운영 안전성: staging/prod에서 canary를 Mongo Transaction으로 insert한 뒤 rollback-only 처리하고 잔존 문서가 0건인지 확인하는 startup capability probe를 추가했다. `user_withdrawn_transaction_probe` collection과 inbox/deny TTL index의 정확한 준비 절차를 runbook에 기록했다.
- 계약·관측: semantic digest의 공유 golden vector SHA-256 `a956f71c53a448afbf657f9cc74a00a6ba1aed0571d43203d345bd44be489e27`을 테스트로 고정했고, 식별자를 label이나 log에 넣지 않는 delivery-lag metric을 추가했다. Secret과 Token은 추가하거나 기록하지 않았다.
- 유지한 외부 계약: 기존 공개 API URL·Method·Request/Response DTO·`BaseResponse`, `retryCount`, S3·Redis·Polling, Python AI 요청과 Callback JSON, `user_id=examId`, 사용자 소유권 검증은 변경하지 않았다. 신규 endpoint는 workload 전용 내부 event endpoint다.
- 실행한 테스트: withdrawal 집중 테스트, `./gradlew clean test --no-daemon`, `git diff --check`를 실행했다. 실제 AWS·Atlas·Redis·Python AI·Sentry는 호출하지 않았다.
- 테스트 결과: 전체 Java 테스트 402개, failures 0, errors 0, skipped 0으로 `BUILD SUCCESSFUL`이다. 기존의 작업 범위 밖 unchecked-operation compiler warning은 유지된다.
- 남아 있는 위험 요소: production workload 인증 방식/profile과 정확한 값, Access Token 최대 수명·verifier skew·retention·future-event skew 값이 승인되지 않았다. staging/prod replica set, probe collection, 정확한 TTL index를 실제 환경에 준비하고 실제 Transaction rollback·동시성, 다중 instance 가시성, workload-auth staging E2E를 검증해야 한다.
- 다음 작업 전 확인 사항: Learning Core를 먼저 배포하되 feature flag는 인프라와 운영값 검증 전 활성화하지 않는다. TMI-109 활성화와 smoke test 후 Identity `TMI-111` publisher/outbox/backfill을 연동하고 단계적으로 rollout한다. Git commit과 push는 사용자가 수행한다.

## 2026-08-28 — TMI-109 develop 병합 확인 및 Jira 완료 처리

<!-- codex-turn:01a0460c-0342-77a1-b78a-5f667e2cb6da -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: Learning Core `TMI-109`; 후속 Identity `TMI-111`과의 `TMI-109 blocks TMI-111` 관계를 유지했다.
- 작업 목표: TMI-109 구현이 원격 `develop`에 정상 병합됐는지 검증하고, 조건을 충족하면 Jira 이슈를 완료한다.
- GitHub 검증: PR [#23](https://github.com/Too-Much-I/app-back-end-learning-core/pull/23)은 head `feat/TMI-109-withdrawal-deny-gate-consumer`, base `develop`, 상태 `MERGED`이며 2026-08-28 10:44:51 KST에 merge commit `4baa4f20b7b179290dd743325ef7b251a408da47`로 병합됐다. CodeRabbit check는 `SUCCESS`다.
- Git 검증: `git fetch origin develop --prune` 후 `origin/develop`이 위 merge commit을 가리키는지 확인했다. merge commit의 두 parent는 기존 develop `514fb49c43342fb968b32b071cd0fadc2ad143dc`와 TMI-109 구현 `96e5c2021ef4ffa6a3e1069c69c08d31e974bb31`이다. 병합 diff에 withdrawal 코드·테스트·설정·runbook이 포함됐고 `git diff --check 4baa4f2^1..4baa4f2`가 통과했다. 깨끗한 로컬 `develop`을 `origin/develop`로 fast-forward해 동일 커밋으로 맞췄다.
- 테스트 근거: 구현 종료 시 `./gradlew clean test --no-daemon` 전체 Java 테스트 402개가 failures 0, errors 0, skipped 0으로 성공했다. 이번 병합 확인 turn에서는 같은 merge commit에 대해 Gradle 전체 테스트를 중복 실행하지 않았다.
- Jira 변경: TMI-109의 기존 상태 `해야 할 일`에서 사용 가능한 transition 41 `완료`를 실행했다. 변경 후 재조회에서 status `완료`, resolution `완료`를 확인했다. 이슈 본문·댓글·링크는 변경하지 않았다.
- 유지한 계약: 공개 API·DTO·`BaseResponse`, retryCount, S3·Redis·Polling, Python AI 요청·Callback JSON과 `user_id=examId` 계약은 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 남아 있는 위험 요소: merge와 Jira 구현 완료는 확인됐지만 production feature flag 활성화 조건은 별도다. replica set, probe collection, 정확한 TTL index, workload 인증 profile·운영 duration, 실제 Transaction·다중 instance·staging E2E가 남아 있다.
- 다음 작업 전 확인 사항: 운영 준비를 완료한 뒤 Learning Core consumer/gate를 단계적으로 활성화하고 smoke test 후 후속 Identity `TMI-111` publisher/outbox/backfill을 진행한다. 이번 기록 문서 변경은 로컬 미커밋 상태이며 Codex는 commit·push를 수행하지 않는다.

## 2026-08-28 — 10초 챌린지 attempt 생성 시점과 자정 경계 검토

<!-- codex-turn:01a04620-2d43-7c50-9e74-1b9a66f7290f -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없다. Jira 상태·본문·댓글·링크를 변경하지 않았다.
- 작업 목표: 프론트가 녹음을 완료한 뒤 제출 과정에서 attempt를 생성할 때 KST 날짜 변경으로 응시가 막히는지 현재 10초 챌린지 계약에 대조한다.
- 확인 문서: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`를 읽기 전용으로 확인했다.
- 결론: 우려가 유효하다. 프론트가 23:59:55에 이전 날짜 문제 녹음을 시작하고 00:00:05에 attempt 생성을 요청하면, server는 이전 `X-Challenge-Date`와 현재 KST 날짜 불일치로 mutation 없이 `409 CHALLENGE_DATE_CHANGED`를 반환한다. 이 경우 아직 기존 attempt가 없으므로 방금 녹음한 audio를 이전 날짜 문제에 제출할 수 없고, 새 날짜 문제도 달라질 수 있어 녹음을 재사용하면 안 된다.
- 확정된 호출 순서: `오늘 진행도 조회 → 문제 조회 → attempt 생성 및 Presigned PUT URL 발급 → attemptId/challengeDate 로컬 보관 → 최대 10초 녹음 → S3 PUT → answer 제출`이다. recorder는 attempt 생성 성공 뒤에만 시작해야 한다.
- 자정 처리: 자정 전에 생성된 attempt는 생성 당시 challengeDate에 고정하며 `submissionDeadlineAt=attemptCreatedAt+5분`까지 자정 이후에도 이전 날짜 제출을 허용한다. answer 처리는 현재 KST 날짜가 아니라 attemptId에 저장된 challengeDate와 S3 key를 기준으로 해야 한다. deadline을 넘긴 경우에만 `410 CHALLENGE_ATTEMPT_EXPIRED`로 종료한다.
- 프론트 오류 처리: attempt 생성 전에 날짜가 바뀌어 `CHALLENGE_DATE_CHANGED`가 오면 녹음을 시작하지 않고 캐시된 문제를 폐기한 뒤 오늘 진행도를 재조회한다. 이미 잘못된 순서로 녹음했다면 그 파일은 이전·새 날짜 어디에도 자동 제출하지 않고 안내 후 새 문제로 다시 녹음해야 한다.
- 변경 파일: 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·테스트·공개 계약 문서는 수정하지 않았다.
- 실행한 테스트와 결과: 코드 변경이 없는 계약 분석이므로 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`와 marker 단일 포함을 검증한다.
- 유지한 계약: 기존 시험 공개 API·DTO·`BaseResponse`, S3·Redis·AI/Callback과 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 다음 작업 전 확인 사항: 프론트의 실제 recorder 시작 handler가 attempt API 성공을 await하는지 확인하고, `23:59:55 녹음 시작`과 `23:59:59 attempt 응답 후 00:00:09 제출` 두 자정 경계 E2E를 반드시 추가한다.

## 2026-08-28 — 10초 챌린지 backend-only 자정 rollover 대안 검토

<!-- codex-turn:01a04620-2d43-7c50-9e74-1b9a66f7290f -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없다. Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 프론트의 현재 `녹음 후 attempt 생성` 순서를 바꾸지 않고 백엔드만으로 자정 경계 녹음 유실을 방지할 수 있는지 검토한다.
- 결론: attempt 생성 요청이 기존 `X-Challenge-Date`로 녹음 대상 날짜를 전달한다면 backend-only 해결이 가능하다. 서버가 현재 날짜 불일치 요청을 모두 409로 거절하지 않고, 요청 날짜가 직전 KST 날짜이며 자정 후 제한된 creation grace 안인 경우 이전 날짜 attempt 생성을 허용하면 된다.
- 저장 규칙: rollover attempt는 현재 날짜가 아니라 요청한 이전 `challengeDate`의 ChallengeDefinition, 문제 번호와 `(userId, challengeDate, questionNumber)` unique key에 귀속한다. 이전 날짜의 순차 진행 조건과 문제당 1회 제한도 동일하게 적용하고 새 날짜 attempt로 잘못 저장하지 않는다.
- 권장 안전장치: 프론트 계약을 늘리지 않으려면 question 조회 시 서버가 `(userId, challengeDate, questionNumber, viewedAt, expiresAt)` 형태의 짧은 view/recording lease를 내부에 기록한다. 자정 이후 이전 날짜 attempt 생성은 자정 전에 발급된 유효 lease가 있을 때만 허용한다. 이를 통해 임의의 과거 날짜 header로 뒤늦게 응시하는 것을 막는다.
- deadline 정책: creation grace와 제출 deadline을 분리해야 한다. 예를 들어 이전 날짜 attempt를 자정 후 짧은 creation grace 동안만 만들게 하고, 생성 성공 후에는 서버가 정한 upload/submission 복구 시간을 부여한다. 정확한 grace와 deadline duration은 운영·UX 계약으로 구현 전에 확정해야 하며 임의 값으로 고정하지 않았다.
- 한계: 현재 프론트가 `X-Challenge-Date` 또는 동등한 기존 날짜 식별자를 attempt 요청에 전혀 보내지 않는다면 서버는 녹음이 이전 날짜 문제인지 새 날짜 문제인지 알 수 없다. 이 경우 정확한 backend-only 처리는 불가능하고 최소한 기존 날짜 정보 전달이 필요하다.
- 변경 파일: 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션 코드·테스트·challenge 계약 문서는 수정하지 않았다.
- 실행한 테스트와 결과: 코드 변경이 없는 설계 검토라 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`와 marker 단일 포함을 검증한다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback 및 `user_id=examId` 계약을 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 다음 작업 전 확인 사항: 프론트 attempt 요청에 실제로 `X-Challenge-Date`가 포함되는지 먼저 확인하고, 포함된다면 backend rollover acceptance와 server-side lease, 자정 경계 통합 테스트를 구현 계획에 반영한다.

## 2026-08-28 — 10초 챌린지 backend-only rollover 종료 훅 동기화

<!-- codex-turn:01a04621-8c70-7893-a9ea-3fa2b8952e3f -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없으며 Jira를 변경하지 않았다.
- 작업 목표: 프론트의 `녹음 후 attempt 생성` 순서를 유지하는 backend-only 자정 rollover 대안과 종료 상태를 현재 turn marker로 기록한다.
- 결과: 기존 attempt 요청이 `X-Challenge-Date`를 전달한다면, 서버는 요청 날짜가 직전 KST 날짜이고 자정 후 제한된 creation grace 안이며 자정 전에 발급된 server-side question view/recording lease가 있을 때 이전 날짜 attempt 생성을 허용할 수 있다. attempt는 요청 날짜의 콘텐츠와 `(userId, challengeDate, questionNumber)` unique key에 귀속한다.
- 안전 조건: creation grace와 upload/submission deadline을 분리하고, 기존 순차 진행·문제당 1회 제한을 유지한다. 날짜 식별자가 없다면 녹음 대상 날짜를 판별할 수 없어 정확한 backend-only 처리는 불가능하다.
- 변경 파일: 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·테스트·공개 challenge 계약은 변경하지 않았다.
- 실행한 테스트와 결과: 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. `git diff --check`와 이 marker의 단일 포함을 종료 전에 검증한다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback과 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 다음 작업 전 확인 사항: 프론트의 실제 attempt 요청에 `X-Challenge-Date`가 포함되는지 확인하고, creation grace·submission deadline 운영값을 확정한 뒤 backend rollover와 자정 경계 테스트 구현 여부를 결정한다.

## 2026-08-28 — 10초 챌린지 임시 recording session 기반 rollover 검토

<!-- codex-turn:01a04625-d3fa-77f3-91b9-c6c93ed5f0af -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 자정 전에 기록한 임시 session 또는 시작 시각을 근거로, 자정 후에도 이전 날짜 attempt 생성을 안전하게 허용하는 방식을 검토한다.
- 결론: server-issued 임시 recording session을 사용하는 방식이 단순한 이전 날짜 grace보다 안전하다. 서버가 `userId`, `challengeDate`, `questionNumber`, `startedAt`, `expiresAt`을 저장하고 attempt 생성 시 session의 KST `startedAt` 날짜가 요청 `challengeDate`와 같으며 현재 시각이 `expiresAt` 이내인지 검증한다.
- 신뢰 경계: `startedAt`은 client Request 값이 아니라 server `Clock`으로 기록해야 한다. client timestamp만 받아 이전 날짜를 허용하면 변조로 과거 challenge를 시작할 수 있다.
- 원자성: session 확인·소비와 ChallengeAttempt insert는 같은 Mongo Transaction으로 처리한다. attempt의 `(userId, challengeDate, questionNumber)` unique 제약을 유지하고, 중복 요청은 기존 attempt로 멱등 수렴시킨다. session collection의 TTL은 정리 수단일 뿐 authorization 근거로 사용하지 않고 `expiresAt`을 직접 비교한다.
- deadline: 자정 뒤 attempt가 생성됐다는 이유로 제출 시간이 새로 5분 연장되면 안 된다. `submissionDeadlineAt=session.startedAt+허용시간`으로 고정하면 23:59:55에 시작한 session은 자정을 지나도 정해진 복구 시간까지만 제출할 수 있다.
- 프론트 무변경 대안: 기존 문제 GET 시 session을 server-side로 생성하거나 갱신하면 Request/Response 변경 없이 적용할 수 있다. 다만 이 시각은 실제 녹음 시작이 아니라 문제 조회 시각이므로 사용자가 문제를 오래 보고 있으면 session이 먼저 만료될 수 있다.
- 정확한 대안: 실제 녹음 시작 시각이 필요하면 녹음 직전에 server start/session API를 호출해야 하므로 최소한의 프론트 변경이 필요하다. 또는 프론트가 녹음 직전에 이미 호출하는 기존 endpoint가 있다면 그 요청에 session 생성을 결합할 수 있다.
- 변경 파일: 설계 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·테스트·challenge 공개 계약은 변경하지 않았다.
- 실행한 테스트와 결과: 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`와 marker 단일 포함을 검증한다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback 및 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 다음 작업 전 확인 사항: 프론트가 녹음 직전에 호출하는 기존 API가 있는지 확인한다. 없으면 문제 GET 시 session 생성의 UX 한계를 수용할지, 명시적 start API를 추가할지 결정하고 session TTL·submission deadline 값을 확정한다.

## 2026-08-28 — 10초 챌린지 attempt와 S3 URL 발급 분리 검토

<!-- codex-turn:01a0462c-e587-7d60-9f84-d3300c88e662 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 녹음 시작 시 attempt를 생성하고 녹음 완료 후 같은 attempt에 S3 Presigned URL을 연결하는 분리 계약을 검토한다.
- 권장 호출 순서: `오늘 진행도/문제 조회 → POST attempt → 최대 10초 녹음 → POST attempt upload-url → S3 PUT → POST answer/submit → 결과 polling`이다.
- 자정 처리: attempt 생성 시 서버 `Clock`으로 `createdAt`, `challengeDate`, `submissionDeadlineAt`을 확정한다. 23:59:55에 생성한 attempt는 날짜가 바뀌어도 deadline 전까지 동일 attemptId로 upload-url을 받고 이전 날짜 문제로 제출할 수 있다. 현재 날짜와 다시 연결하거나 이전 날짜의 새 attempt를 자정 후 생성할 필요가 없다.
- S3 연결: object key는 attemptId를 포함해 attempt 생성 시 결정·저장하거나 동일 입력에서 결정적으로 계산한다. upload-url endpoint는 JWT 소유권, attempt 상태와 deadline을 확인하고 같은 object key의 URL만 발급한다. URL 만료·응답 유실 시 동일 attemptId로 재발급하며 새 사용자 응시로 계산하지 않는다.
- 멱등성·동시성: attempt unique key `(userId, challengeDate, questionNumber)`를 유지해 중복 시작 요청은 기존 CREATED attempt를 반환한다. upload-url 재발급은 같은 key, submit은 동일 Idempotency-Key와 attemptId로 같은 결과에 수렴한다. 이미 SUBMITTED/EXPIRED인 attempt에는 새 upload target을 만들지 않는다.
- 상태: 내부 상태는 `CREATED → UPLOAD_READY/UPLOADING → SUBMITTED` 또는 deadline 후 `EXPIRED`로 관리할 수 있다. 기존 Draft의 공개 projection을 유지한다면 제출 전은 `not_started`, terminal 후는 `submitted`다.
- 제품 결정 필요: attempt 생성이 문제당 1회를 점유하므로 녹음 취소·앱 종료·S3 미업로드도 deadline 후 EXPIRED로 처리된다. 이 경우 참고 답안을 공개하고 history 풀이 수에 포함할지, 참여만 기록할지 구현 전에 확정해야 한다.
- 계약 영향: 현재 draft의 `POST attempt`가 attempt와 upload URL을 함께 반환하는 구조를 두 endpoint로 나누므로 프론트 호출 순서와 draft API 계약은 변경된다. 다만 해당 challenge API는 아직 구현·배포되지 않아 기존 운영 호환성 문제는 없다.
- 변경 파일: 설계 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·테스트·challenge 계약 문서는 변경하지 않았다.
- 실행한 테스트와 결과: 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`와 marker 단일 포함을 검증한다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback 및 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 다음 작업 전 확인 사항: attempt 생성이 녹음 취소에도 1회를 소비하는 정책, upload-url endpoint 경로, submission deadline과 Presigned URL TTL을 확정한 뒤 challenge draft 계약을 갱신한다.

## 2026-08-28 — 10초 챌린지 attempt·S3 URL 분리 계약 확정

<!-- codex-turn:01a0462c-e587-7d60-9f84-d3300c88e662 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 사용자가 승인한 `녹음 시작 attempt 생성`과 `녹음 완료 후 S3 Presigned URL 발급` 분리 구조를 구현 기준 challenge Draft 계약으로 확정한다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 변경했다. 애플리케이션·테스트 코드는 변경하지 않았다.
- 확정 호출 순서: `오늘 진행도 → 문제 조회 → POST attempt → 최대 10초 녹음 → POST /attempts/{attemptId}/upload-url → S3 PUT → answer 제출 → 결과 polling`이다.
- attempt 계약: `POST /api/v1/challenges/today/questions/{questionNumber}/attempt`는 upload 정보를 반환하지 않고 `attemptId`, `challengeDate`, `questionNumber`, 공개 `attemptStatus`, `submissionDeadlineAt`만 반환한다. 서버는 생성 시 `createdAt`, challengeDate, deadline과 attemptId 기반 `.m4a` S3 object key를 내부에 고정한다.
- upload-url 계약: `POST /api/v1/challenges/attempts/{attemptId}/upload-url`을 추가했다. JWT 소유권, attempt 상태와 deadline을 확인하고 동일 object key의 Presigned PUT URL만 발급한다. URL 만료·응답 유실 시 같은 attemptId로 재발급하며 새 응시로 계산하지 않고 URL 만료 시각은 submission deadline을 넘지 않는다.
- 자정 계약: 자정 전에 생성한 attempt는 저장된 challengeDate와 `attemptCreatedAt+5분` deadline을 유지한다. 자정 후 upload-url·answer 요청은 현재 날짜로 다시 판정하지 않아 기존 attempt를 이전 날짜 문제로 정상 완료할 수 있다. 자정 이후 이전 날짜의 새 attempt 생성은 계속 금지한다.
- 상태·멱등성: 내부 상태를 `NOT_CREATED → CREATED → UPLOAD_READY/UPLOADING → SUBMITTED 또는 EXPIRED`로 정리했다. attempt 중복 시작은 같은 attempt와 deadline, upload-url 재발급은 같은 object key, answer 재전송은 같은 Idempotency-Key로 수렴한다.
- 계약 영향: 프론트 명세를 Draft v0.5에서 v0.6으로 올렸다. 아직 challenge API가 구현·배포되지 않았으므로 기존 운영 API 호환성 영향은 없다.
- 실행한 테스트와 결과: 문서 계약만 변경해 Gradle 테스트는 실행하지 않았다. 오래된 결합 발급 문구와 heading 번호를 검색해 정리했고 종료 전에 `git diff --check`를 실행한다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback과 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 남아 있는 결정: sample rate·channel·최대 파일 크기, AI 결과 필드, grading retry/timeout과 MEMBER/Guest 범위는 여전히 미확정이다. 기존 Draft의 attempt 만료 terminal·참고 답안·history 반영 정책은 이번 분리 작업에서 변경하지 않았다.
- 다음 작업 전 확인 사항: 프론트·백엔드가 Draft v0.6 endpoint와 호출 순서를 함께 기준으로 삼는지 확인한 뒤 Challenge domain/API 구현 Jira와 구현 계획에 반영한다.

## 2026-08-28 — 10초 챌린지 Draft v0.6 계약 확정 종료 훅 기록

<!-- codex-turn:01a0462e-7adf-72a2-be13-1cffdd18a7c5 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없으며 Jira를 변경하지 않았다.
- 작업 목표: 사용자가 승인한 attempt 생성과 S3 upload-url 분리 계약의 반영 결과를 현재 turn marker로 기록한다.
- 결과: 프론트 명세를 Draft v0.6으로 갱신했다. 녹음 시작 전 `POST /api/v1/challenges/today/questions/{questionNumber}/attempt`로 attempt와 날짜·deadline을 확정하고, 녹음 후 `POST /api/v1/challenges/attempts/{attemptId}/upload-url`로 동일 S3 object key의 Presigned PUT URL을 발급·재발급한다.
- 자정·멱등성: 자정 이후에도 기존 attempt의 저장된 challengeDate와 deadline을 사용한다. 중복 attempt 시작은 같은 attempt, URL 재발급은 같은 object key, answer 재전송은 같은 Idempotency-Key로 수렴한다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 변경했다. 애플리케이션·테스트 코드는 변경하지 않았다.
- 테스트: 문서 계약 변경이라 Gradle 테스트는 실행하지 않았고 `git diff --check`가 통과했다. 오래된 attempt·URL 결합 발급 문구와 heading 번호도 검색해 정리했다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback 및 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 남은 사항: Challenge API는 아직 구현·배포되지 않았다. sample rate·channel·최대 파일 크기, AI 결과 필드, grading timeout/retry와 MEMBER/Guest 범위는 계속 미확정이다.
- 다음 작업 전 확인 사항: Draft v0.6을 프론트·백엔드 공통 기준으로 확인한 뒤 Challenge domain/API 구현 Jira와 구현 계획에 반영한다.

## 2026-08-28 — 10초 챌린지 프론트 계약 문서 위치 확인

<!-- codex-turn:01a04634-3cdc-7201-a69f-3a04f4e4b516 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 사용자가 요청한 10초 챌린지 프론트 계약 문서를 확인하고 이 창에서 열 수 있도록 제공한다.
- 확인 결과: 대상 문서는 `docs/contracts/ten-second-challenge-frontend-api.md`이고 현재 버전은 Draft v0.6, 작성일은 2026-08-28이다. 녹음 시작 attempt와 녹음 후 S3 upload-url 발급 분리, server 기준 날짜 rollover 보호가 반영돼 있다.
- 변경 파일: 이번 조회 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 프론트 계약 문서·애플리케이션·테스트 코드는 수정하지 않았다.
- 실행한 테스트와 결과: 문서 위치와 heading을 확인했으며 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`와 marker 단일 포함을 검증한다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback 및 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 다음 작업 전 확인 사항: 프론트는 Draft v0.6의 전체 호출 순서와 6.3 attempt 생성, 6.4 upload-url 발급 항목을 우선 확인한다.

## 2026-08-28 — 10초 챌린지 attempt 유효시간 1시간 확정

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 작업 목표: attempt 생성 시점부터 제출 가능한 유효시간을 5분에서 1시간으로 변경한다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 변경했다. 애플리케이션·테스트 코드는 변경하지 않았다.
- 계약 결과: `submissionDeadlineAt=attemptCreatedAt+1시간`으로 확정하고 프론트 명세를 Draft v0.7로 올렸다. 자정 전 생성된 attempt는 자정 후에도 deadline까지 저장된 challengeDate로 upload-url 발급·S3 PUT·answer 제출을 허용한다.
- 자정 예시: 23:59:50 KST attempt는 다음 날 00:59:50까지 유효하다. 22:50 KST attempt는 23:50 KST에 만료되어 자정까지 자동 연장되지 않는다.
- URL과 attempt 분리: attempt는 1시간 유효하지만 Presigned URL은 보안상 짧게 발급한다. 예시 URL은 5분 유효하며 만료 시 같은 attemptId와 S3 object key로 재발급하고 URL 만료 시각은 attempt deadline을 넘지 않는다.
- 상태·history: 1시간 안에 제출하지 못하면 EXPIRED terminal로 처리하고 기존 Draft대로 공개 `attemptStatus=submitted`, 참고 답안 제공과 history 풀이 수 반영 정책을 유지한다.
- 실행한 테스트와 결과: 문서 계약만 변경해 Gradle 테스트는 실행하지 않았다. 대상 계약 문서에서 이전 5분 표현과 자정 예시를 검색·교정했고 `git diff --check`를 종료 전에 검증한다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback 및 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 다음 작업 전 확인 사항: 프론트·백엔드가 Draft v0.7의 1시간 attempt deadline과 짧은 Presigned URL 재발급 정책을 공통 기준으로 사용해야 한다.

## 2026-08-28 — 10초 챌린지 attempt 1시간 계약 종료 훅 기록

<!-- codex-turn:01a04636-3385-7f22-8ab2-8000a8178d9a -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없으며 Jira를 변경하지 않았다.
- 작업 목표: 10초 챌린지 attempt 제출 유효시간을 1시간으로 확정한 결과를 현재 turn marker로 기록한다.
- 결과: 프론트 명세 Draft v0.7, API·상태 결정서와 출시 계획에서 `submissionDeadlineAt=attemptCreatedAt+1시간`으로 동기화했다. 23:59:50 KST 생성 attempt는 다음 날 00:59:50까지 원래 challengeDate로 제출할 수 있다.
- URL 정책: Presigned URL은 attempt보다 짧게 발급하고 만료 시 attempt deadline 전까지 동일 attemptId와 object key로 재발급한다. URL 만료 시각은 attempt deadline을 넘지 않는다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 변경했다. 애플리케이션·테스트 코드는 변경하지 않았다.
- 테스트: 문서 변경이라 Gradle 테스트는 실행하지 않았다. 이전 5분 계약 표현과 자정 예시를 검색해 교정했고 `git diff --check`가 통과했다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback 및 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 다음 작업 전 확인 사항: 프론트·백엔드가 Draft v0.7의 attempt 1시간 deadline과 Presigned URL 재발급 규칙을 구현 기준으로 사용해야 한다.

## 2026-08-28 — 전체 앱 프론트엔드 API 인계서 작성

<!-- codex-turn:01a0463a-cc04-73a1-8c34-a2fb851f8d7e -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: `TMI-102`, `TMI-105`, `TMI-106`, `TMI-109`, `TMI-110`, `TMI-111`의 현재 구현 경계를 문서에 반영했으며 Jira 자체는 조회하거나 변경하지 않았다.
- 작업 목표: 프론트가 현재 호출 가능한 Identity·Learning Core API와 1차 업데이트 예정 API를 상태·인증·요청·응답·호출 흐름별로 한 문서에서 확인하게 한다.
- 변경 파일: `docs/contracts/FRONTEND_API_HANDOFF.md`를 추가하고 이 작업 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 갱신했다. 기존 애플리케이션·테스트 코드와 다른 계약 문서는 변경하지 않았다.
- 변경한 내용: Identity의 LOCAL·Guest·Firebase/SNS·Token·사용자 API 17개와 Learning Core 모의고사 API 11개를 현재 컨트롤러·DTO·Security 설정 기준으로 정리했다. 공통 BaseResponse 차이, Bearer Token, Firebase enrollment·Guest upgrade/merge, 정책 동의, 탈퇴, S3 PUT·submit·polling, 시험 이력·재답변·채점 복구 예시를 포함했다.
- 계획/내부 경계: Billing 공개 프론트 API는 현재 0개임을 명시하고 무료 1회 TrialClaim·Entitlement·Reservation과 Learning Core Billing saga를 미구현으로 표시했다. 10초 챌린지 7개 endpoint는 Draft v0.7 링크와 함께 미구현으로 분리했다. AI callback, `UserWithdrawn`, eligibility workload와 JWKS는 프론트 호출 금지로 분류했다.
- 유지한 외부 계약: 기존 공개 API URL·Method·Parameter·DTO·BaseResponse, S3 Object Key, Redis, Python AI callback과 `user_id=examId` 계약은 변경하지 않았다. Secret·실제 Token·실제 운영 host를 문서에 넣지 않았다.
- 실행한 테스트와 결과: 문서 변경이므로 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`, 링크 대상 존재, endpoint 대조와 이 marker의 단일 포함을 검증한다.
- 남아 있는 위험 요소: Learning Core Presigned PUT URL은 코드상 5분 signature지만 응답 `expiresIn`은 60으로 생성돼 의미·단위가 일치하지 않는다. Billing 공개 계약과 10초 챌린지 API는 아직 구현 전이며 Challenge의 sample rate·channel·최대 파일 크기·AI result·rollover 최종 승인이 남아 있다.
- 다음 작업 전 확인 사항: backend가 upload URL 만료 계약을 정리하고, Billing/Challenge 구현 Jira와 최종 공개 DTO가 동결된 뒤 프론트 타입과 feature flag를 production 연동한다.

## 2026-08-28 — TMI-109·TMI-111 workload JWT 계약안 코드 대조 검토

<!-- codex-turn:01a046a4-67d1-7751-bca1-8a6f2052e37f -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: Learning Core `TMI-109`, 후속 Identity publisher `TMI-111`. Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 제안된 UserWithdrawn workload JWT 계약을 Learning Core의 실제 validator와 Identity의 기존 RS256/JWKS 발급 구조에 대조해 승인 가능 여부와 보완점을 판단한다.
- 확인 범위: Learning Core `UserWithdrawnConfiguration`, workload properties, audience/principal/maximum-lifetime validator와 security integration test, Identity의 JWT issuer/configuration/JWKS/controller·workload credential interface·publisher properties·JWT 계약을 읽기 전용으로 확인했다. Identity 파일은 수정하지 않았다.
- 승인 가능한 내용: RS256·`typ=JWT`·`kid`, 사용자 issuer와 분리한 workload issuer, 전용 audience `learning-core-user-withdrawn`, Identity 기존 RSA signing infrastructure와 JWKS 재사용, TTL PT2M·clock skew PT30S, 내부 JwtEncoder 로컬 발급·공개 token API 없음·요청별 새 token, HTTPS·redirect 금지와 제안된 retry 분류 방향은 적절하다.
- 실제 Learning Core 검증: RS256 알고리즘, issuer, audience list에 필수 값 포함, 설정된 단일 principal claim/value 일치, timestamp, 필수 `iat/exp`와 `exp-iat <= max-token-lifetime`을 검증한다. audience 배열은 현재 validator와 호환된다.
- 보완 1 principal: 제안대로 설정하면 실제 allowlist는 `service=identity` 하나이고 `sub=identity-service`는 강제되지 않는다. 권장안은 표준 `sub=identity-service`로 principal을 통일해 `principal-claim=sub`, `principal-value=identity-service`를 쓰는 것이다. custom `service`를 유지하려면 `sub`까지 별도 validator로 함께 강제하고 두 값 불일치를 거절해야 한다.
- 보완 2 시간 claim: 현재 maximum-lifetime validator는 `exp-iat` 길이만 제한하고 `iat`가 현재보다 먼 미래인지 직접 검증하지 않는다. 계약에 `nbf=iat`을 필수로 추가해 기존 timestamp validator가 PT30S skew로 검사하게 하거나 future-iat validator를 추가해야 한다. PT2M은 nominal lifetime이고 verifier skew 때문에 만료 수용 경계는 최대 약 2분 30초임을 운영 문서에 명시한다.
- 보완 3 header·replay: 현재 workload decoder는 RS256을 고정하지만 `typ=JWT`, `kid` 존재, `jti` 존재를 명시적 validator로 강제하지 않는다. 이를 필수 계약으로 둘 경우 rejection test와 validator 보완이 필요하다. `jti`는 token 고유성용이며 event replay 멱등성은 계속 payload `eventId` inbox가 담당하고 별도 jti 저장소를 추가할 필요는 없다.
- Identity 현황: 기존 user Access Token은 RS256, `kid`, issuer, audience, UUID sub, iat/exp/jti를 발급하고 표준 JWKS endpoint는 현재 RSA public key 한 개만 노출한다. `WorkloadIdentityCredentialProvider`는 interface만 있고 production 구현체가 없으므로 TMI-111에서 workload issuer·claim 발급을 구현해야 한다. 동일 signing key/JWKS 재사용은 가능하지만 old/new public key 동시 노출 rotation은 현재 미지원이다.
- 오류 정책 보완: 모든 2xx 성공, 408/425/429/5xx·timeout·connection failure retry, 400/409/413/422 영구 payload 실패 DLQ가 적절하다. 401/403뿐 아니라 404/405와 예상하지 못한 4xx도 payload DLQ가 아니라 endpoint/auth 배포 설정 오류로 격리·경보하는 편이 안전하다.
- 변경 파일: 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. Learning Core·Identity 애플리케이션과 계약 문서, Jira는 변경하지 않았다.
- 실행한 테스트와 결과: 코드 변경이 없는 분석이라 Gradle 테스트는 실행하지 않았다. 양 저장소 코드를 읽기 전용 대조했고 종료 전에 `git diff --check`와 marker 단일 포함을 검증한다.
- 유지한 계약: 사용자 Access Token의 `aud=tosunsaeng-learning-core`, Python AI `user_id=examId`, UserWithdrawn event wire·endpoint와 기존 공개 API를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 남아 있는 위험 요소: Identity JWKS 다중 키 rotation, workload provider 구현, `nbf/future iat`, principal 단일화 또는 이중 검증, header 필수성, 실제 HTTPS/no-redirect와 staging E2E가 완료되지 않았다.
- 다음 작업 전 확인 사항: principal을 `sub` 하나로 통일할지 `sub+service` 이중 검증할지 결정하고, `nbf=iat`과 header 필수 검증 범위를 확정한 뒤 TMI-111 Identity issuer와 TMI-109 Learning Core 설정·테스트를 같은 golden token으로 고정한다.

## 2026-08-28 — 10초 챌린지 실제 Mongo 콘텐츠 기반 계획·프론트 문서 갱신

<!-- codex-turn:01a046f9-f06b-74e3-8aef-d8cdf5255de6 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이다. Jira를 조회하거나 변경하지 않았고 Learning Core Challenge backend 구현 Jira는 아직 없다.
- 작업 목표: 실제 `challenge_10s_questions` document와 기존 `to-teacher-app` MongoDB cluster 사용 사실을 10초 챌린지 계획서·계약 결정서와 프론트 인계 문서에 반영한다.
- 사용자 제공 저장 구조: `dayNumber` document 안에 questionNumber 1~3의 `questionId`, `korean`, `referenceAnswer`, `difficulty`가 embedded array로 저장된다. 실제 Mongo URI·credential·logical database 이름은 기록하지 않았다.
- 변경 파일: `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/contracts/FRONTEND_API_HANDOFF.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 변경했다. 애플리케이션·테스트 코드는 변경하지 않았다.
- 인프라 계획: 별도 Atlas cluster와 Mongo connection을 만들지 않고 Learning Core의 기존 `to-teacher-app` cluster 연결·credential boundary를 재사용한다. production collection/index는 migration으로 준비하고 startup에서 catalog/index 정의를 검증한다.
- catalog 계약: collection은 `challenge_10s_questions`, document 하나는 양의 정수 dayNumber와 정확히 세 문제를 가진다. questionNumber 집합 `{1,2,3}`, dayNumber unique, catalog 전역 questionId 중복 없음, non-blank korean/referenceAnswer와 유효 difficulty를 검증한다. published day는 append-only로 관리한다.
- 프론트 매핑: `questions[].questionNumber → questionNumber`, `questions[].korean → promptKo`, `questions[].referenceAnswer → terminal 이후 referenceAnswer`로 매핑한다. `_id`, dayNumber, questionId와 difficulty는 내부 전용이며 프론트가 보내거나 계산하지 않는다. 실제 day 1 Q2 예시를 프론트 응답 예시에 반영했다.
- 보안·노출: referenceAnswer는 DB에 있어도 문제 조회, attempt 생성과 upload-url 응답에는 포함하지 않고 SUBMITTED 또는 EXPIRED terminal 이후에만 반환한다. 콘텐츠 누락·구조 오류 시 다른 날 문제를 섞지 않고 `404 CHALLENGE_CONTENT_NOT_FOUND`로 fail-closed한다.
- 결과 재현성: ChallengeAttempt 생성 시 dayNumber, questionId, questionNumber, korean, referenceAnswer와 difficulty snapshot을 저장해 catalog가 나중에 변경돼도 S3 submit·AI 요청·과거 결과가 동일 콘텐츠를 사용하게 계획했다.
- 날짜 resolver: 권장 MVP는 KST contentBaseDate를 고정하고 `daysBetween(contentBaseDate, challengeDate)+1`로 dayNumber를 계산한다. 없는 dayNumber에 modulo·random fallback은 하지 않는다. 정확한 base date와 콘텐츠 소진 후 순환은 아직 사용자 확정이 필요하다.
- 프론트 문서: 상세 계약을 Draft v0.8로 올리고 전체 API 인계서의 상태·field mapping·미확정 항목을 동기화했다. 공개 endpoint 수와 기존 request/response shape은 변경하지 않았다.
- 실행한 테스트와 결과: 문서 변경이라 Gradle 테스트는 실행하지 않았다. active challenge 문서의 Draft 버전·collection/cluster·field mapping·오래된 ChallengeDefinition 표현을 검색했고 종료 전에 `git diff --check`와 marker 단일 포함을 검증한다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback `user_id=examId`, challenge attempt 1시간과 분리 upload-url 계약을 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 남아 있는 위험 요소: `dayNumber=1`의 KST 기준일, 소진 시 순환 여부, difficulty scale, sample rate·channel·최대 파일 크기, AI result와 retry/timeout, MEMBER/Guest 정책이 미확정이다. 실제 collection index와 전체 catalog 데이터 품질은 이번 문서 작업에서 DB에 접속해 검증하지 않았다.
- 다음 작업 전 확인 사항: contentBaseDate·순환·difficulty를 확정한 뒤 Learning Core Challenge backend Jira와 migration/catalog validator·repository·attempt snapshot 구현 계획을 생성한다.

## 2026-08-28 — 10초 챌린지 자동 Day 1·difficulty 계약 확정

<!-- codex-turn:01a046ff-dc5f-7942-b098-08edbb4674de -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이다. Jira를 조회하거나 변경하지 않았고 Learning Core Challenge backend 구현 Jira는 아직 없다.
- 작업 목표: dayNumber 1 기준일을 업데이트 시점부터 자동으로 시작하고 비순환으로 운영하며, Mongo `difficulty`는 프론트에 그대로 전달하되 AI 요청에서는 제외하도록 계획·프론트 계약을 확정한다.
- 자동 기준일: `app.challenge.enabled=true`로 처음 성공 기동한 KST 날짜를 Mongo `challenge_10s_catalog_state` singleton에 원자 `setOnInsert`로 한 번만 저장한다. disabled 배포는 초기화하지 않고 재시작·재배포·ECS scale-out도 기준일을 바꾸지 않는다.
- 날짜 선택: 저장된 `contentBaseDate`와 요청 `challengeDate`의 KST 날짜 차이에 1을 더해 dayNumber를 계산한다. modulo·random·이전 날짜 fallback 없이 비순환으로 운영하고, 없는 dayNumber는 `404 CHALLENGE_CONTENT_NOT_FOUND`와 운영 alert 대상으로 처리한다.
- difficulty: BSON integer 여부만 검증하고 범위·의미는 해석하지 않는다. 문제 조회, 제출 terminal 응답과 상세 결과에 정수를 그대로 반환하며 attempt snapshot에는 저장한다. attempt·upload-url 응답과 AI 요청·grading job payload에는 포함하지 않는다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/contracts/FRONTEND_API_HANDOFF.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 변경했다. 상세 프론트 계약은 Draft v0.9다.
- 변경하지 않은 항목: 애플리케이션·테스트 코드, Mongo 데이터, Jira, Git commit·push와 배포는 변경하지 않았다. 기존 시험 API·DTO·`BaseResponse`, S3·Redis, Python AI/Callback `user_id=examId` 계약도 유지했다.
- 테스트: 문서 변경이므로 Gradle 테스트는 실행하지 않았다. 문서 간 정책과 marker 단일 포함, `git diff --check`를 종료 전에 검증한다.
- 남아 있는 위험 요소: 첫 활성화가 의도한 출시 시점보다 먼저 실행되면 기준일이 조기 고정될 수 있으므로 feature flag 활성화 시점을 배포 절차에서 통제해야 한다. 콘텐츠가 준비된 마지막 dayNumber 다음 날에는 fallback 없이 404가 발생한다.
- 다음 작업 전 확인 사항: Challenge backend 구현 Jira를 만든 뒤 metadata initializer·비순환 resolver·catalog validator·attempt snapshot·공개 DTO와 AI payload exclusion 테스트를 구현한다. sample rate·channel·최대 파일 크기와 AI 결과 상세 계약도 확정해야 한다.

## 2026-08-28 — 10초 챌린지 Learning Core–AI 계약 미확정 항목 검토

<!-- codex-turn:01a04706-3a6b-7e60-843d-32b7e83dceb6 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이다. Jira를 조회하거나 변경하지 않았고 Learning Core Challenge backend·AI 연동 전용 Jira는 아직 없다.
- 작업 목표: 현재 Draft v0.9 프론트 계약과 10초 챌린지 결정서에서 Learning Core–AI 서버 간 계약으로 추가 확정해야 하는 경계를 식별한다.
- 현재 확정된 방향: 시험 Feedback Callback과 분리된 challenge 전용 versioned 비동기 계약을 사용한다. AI에는 실제 userId와 difficulty를 보내지 않고 attempt·문제 식별값, 한국어 prompt와 audio만 전달하며, 결정적 grading job과 callback 멱등성을 사용한다.
- 필수 확정 항목: contract version, AI request endpoint·인증·multipart 또는 object reference 전송 방식, M4A/AAC sample rate·channel·maxBytes, request field와 nullability, callback endpoint·인증·field, 결과 enum과 null 의미, no-speech·unsupported audio, timeout·retry·최종 실패 시간, HTTP 오류 분류와 payload 제한을 동결해야 한다.
- stale callback 방지 권고: outbound와 callback에 `attemptId`, 결정적 `jobId`, 양의 정수 `gradingAttempt`를 필수로 넣고 AI가 값을 그대로 echo해야 한다. 현재 generation과 불일치하는 늦은 callback은 기존 상태를 덮어쓰지 않는 성공 no-op으로 처리한다.
- 결과 권고: MVP는 숫자 점수 없이 transcript, `correct|needs_improvement` verdict, correctedAnswer, 짧은 meaning·grammar·pronunciation feedback을 사용한다. 내부 `no_speech`는 시스템 실패와 구분된 정상 terminal 결과로 저장하되 공개 `feedbackType`은 추가하지 않는다.
- 변경 파일: 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·테스트·계약 문서, Mongo 데이터, Jira, Git commit·push와 배포는 변경하지 않았다.
- 유지한 계약: 기존 프론트 Draft v0.9와 시험 API·DTO·`BaseResponse`, S3·Redis, Python 시험 AI/Callback `user_id=examId` 계약을 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 테스트: 코드 변경이 없는 계약 분석이라 Gradle 테스트는 실행하지 않았다. marker 단일 포함과 `git diff --check`를 종료 전에 확인한다.
- 남아 있는 위험 요소: 인증과 audio 전달 방식이 확정되지 않으면 네트워크·권한 설계가 달라지고, 결과 null/enum과 retry ownership이 확정되지 않으면 AI와 Learning Core가 동일 실패를 서로 재시도하거나 최종 상태가 불일치할 수 있다.
- 다음 작업 전 확인 사항: AI 팀과 request/callback 예시 payload, 인증, audio 규격, 결과 schema, 멱등성·retry 표를 합의한 뒤 challenge 전용 계약 문서를 v1로 동결하고 양쪽 contract test fixture를 공유한다.

## 2026-08-28 — 10초 챌린지 promptKo 의미 확인

<!-- codex-turn:01a0470f-48b5-7552-80e4-0ba94a426cd1 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이며 Jira를 조회하거나 변경하지 않았다.
- 확인 결과: `promptKo`는 Mongo `challenge_10s_questions.questions[].korean`에서 가져오는 한국어 문제 문장이다. 사용자는 이 문장을 보고 영어 문장을 만들어 발화한다.
- 구분: `referenceAnswer`는 참고 영어 답안이며 문제 조회 시에는 숨기고 제출 또는 만료 terminal 이후에만 공개한다.
- AI 계약: 앞서 제안한 `prompt_ko`는 `promptKo`와 같은 한국어 문장을 뜻하는 snake_case wire field 후보다. 실제 field명은 AI 계약 v1에서 최종 확정해야 한다.
- 변경 범위: 확인 결과를 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`에만 기록했다. 애플리케이션·테스트·계약 문서, Mongo 데이터, Jira와 배포는 변경하지 않았다.
- 유지한 계약: 기존 프론트 Draft v0.9, 시험 API·DTO·`BaseResponse`, Python 시험 AI/Callback `user_id=examId` 계약을 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 테스트: 코드 변경이 없는 용어 확인이라 Gradle 테스트는 실행하지 않았다. marker 단일 포함과 `git diff --check`를 종료 전에 확인한다.

## 2026-08-28 — 10초 챌린지 Learning Core–AI API 계약서 작성

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이다. Jira를 조회하거나 변경하지 않았고 Learning Core Challenge backend·AI 연동 전용 Jira는 아직 없다.
- 작업 목표: 앞서 제안한 권장안을 프론트 명세와 같은 수준의 Learning Core–AI 서버 간 독립 계약 문서로 작성한다.
- 신규 문서: `docs/contracts/ten-second-challenge-ai-api.md` Draft v0.1을 추가했다. 기존 시험 `/evaluations`와 Feedback Callback은 재사용·변경하지 않고 challenge 전용 versioned request·Callback endpoint를 사용한다.
- 요청 계약: Learning Core가 S3에서 audio를 내려받아 multipart로 전송한다. `attempt_id`, 결정적 `job_id`, `grading_attempt`, `question_id`, `question_number`, `prompt_ko`, `reference_answer`, `audio_file`을 필수로 두고 실제 userId·difficulty·dayNumber·challengeDate·S3 위치는 보내지 않는다.
- audio 계약: M4A/AAC-LC·`audio/mp4`, 16/44.1/48 kHz, mono/stereo, 최대 2 MiB를 허용하고 AI 내부에서 16 kHz mono PCM으로 정규화한다.
- 결과 계약: `completed`, `no_speech`, `failed` outcome과 transcript, `correct|needs_improvement`, corrected answer, 짧은 meaning·grammar·pronunciation feedback을 사용하며 숫자 점수는 제외한다. no-speech는 시스템 실패가 아닌 completed terminal이다.
- 보안·신뢰성: 방향별 service Bearer credential, private service discovery·TLS, 고정 endpoint, `Idempotency-Key`, callback UUID, `attemptId/jobId/gradingAttempt` fencing, duplicate·stale 204 no-op, 오류 분류·retry ownership, Callback 120초 deadline과 최대 3 generation 권장값을 문서화했다.
- 동기화 파일: `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`에서 새 계약을 참조하고 AI 팀 승인·contract test 후 v1 동결하도록 상태와 미확정 항목을 정리했다. 작업 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`도 갱신했다.
- 유지한 계약: 기존 프론트 Draft v0.9, 시험 API·DTO·`BaseResponse`, S3·Redis, Python 시험 AI/Callback `user_id=examId` 계약을 변경하지 않았다. 애플리케이션·테스트 코드, Mongo 데이터, Jira, Git commit·push와 배포도 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 테스트: 계약서의 JSON code block 네 개를 Ruby JSON parser로 검증했고 모두 통과했다. `git diff --check`도 통과했다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.
- 남아 있는 위험 요소: service Bearer credential은 payload를 유지한 채 workload JWT 또는 AWS_IAM으로 후속 강화할 수 있다. 모바일 실제 audio가 허용 profile과 호환되는지, AI 처리 p95가 120초 이내인지와 Callback retry/DLQ 구현 가능성을 staging에서 검증해야 한다.
- 다음 작업 전 확인 사항: AI 팀이 Draft v0.1을 검토하고 실제 audio fixture·인증·timeout/retry·결과 schema contract test를 승인한 뒤 v1로 동결한다. 프론트 `aiResult` DTO도 AI 결과 projection에 맞춰 최종 확정하고 Challenge backend·AI 구현 Jira를 생성한다.

## 2026-08-28 — 10초 챌린지 AI 계약서 작업 종료 기록 동기화

<!-- codex-turn:01a04711-3c1d-7e33-b1e3-d33c957a1fe5 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이며 Jira를 조회하거나 변경하지 않았다. Learning Core Challenge backend·AI 연동 전용 Jira는 아직 없다.
- 완료 상태: `docs/contracts/ten-second-challenge-ai-api.md` Draft v0.1 작성과 `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md` 동기화가 완료됐다.
- 계약 핵심: challenge 전용 multipart 요청·Callback, 방향별 서비스 인증, M4A/AAC-LC 허용 profile, `attemptId/jobId/gradingAttempt` fencing, `completed|no_speech|failed` 결과, 멱등성·timeout·retry·contract test를 문서화했다.
- 유지한 계약: 기존 프론트 Draft v0.9, 시험 API·DTO·`BaseResponse`, S3·Redis와 Python 시험 AI/Callback `user_id=examId` 계약을 변경하지 않았다.
- 테스트 결과: JSON 예시 4개가 parser 검증을 통과했고 `git diff --check`도 통과했다. 애플리케이션 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.
- 변경하지 않은 항목: 애플리케이션·테스트 코드, Mongo 데이터, Jira, Git commit·push와 배포를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 남은 확인 사항: AI 팀의 실제 모바일 audio fixture, 서비스 인증·TLS, 처리 SLA·retry 검증과 프론트 `aiResult` DTO 승인을 거쳐 계약을 v1로 동결해야 한다.

## 2026-08-28 — 10초 챌린지 AI 계약 v1 승인 반영·잔여 결정 검토

<!-- codex-turn:01a04791-d975-7dd1-acb2-7494bbc26047 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이며 Jira를 조회하거나 변경하지 않았다. Learning Core Challenge backend·AI 연동 전용 Jira는 아직 없다.
- 작업 목표: AI 팀이 기존 계약서 내용대로 구현하기로 합의한 사실을 반영하고 추가로 동결해야 할 계약·제품·운영 항목을 구분한다.
- 승인 반영: `docs/contracts/ten-second-challenge-ai-api.md`를 Draft v0.1에서 v1·담당 팀 승인·미구현 상태로 변경했다. challenge 전용 endpoint·multipart audio·service credential·결과 schema·fencing·timeout/retry 계약은 승인 범위다.
- 문서 동기화: `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`의 AI Draft 표현을 v1 승인·구현 및 contract test 잔여 상태로 수정했다. `docs/contracts/ten-second-challenge-frontend-api.md`에는 서버 Callback deadline 120초와 최대 3 generation 확정을 반영했다.
- 남은 계약·제품 결정: 프론트 `aiResult`와 no-speech null 표현, MEMBER 전용/Guest preview, 날짜 rollover 보호 승인, foreground polling 상한, 사용자 audio 재생 여부, AI text field와 전체 Callback payload 길이 상한이 남아 있다.
- 권장값: no-speech는 `gradingStatus=completed`와 null 하위 field의 `aiResult` 객체, MEMBER 전용, 기존 rollover안 승인, foreground polling 60초, MVP audio 재생 제외, transcript/corrected answer 각 1000자·feedback 각 500자·Callback JSON 16 KiB를 권장한다. 아직 사용자 승인값으로 문서에 고정하지 않았다.
- 운영 준비: 실제 모바일 audio fixture, credential 생성·주입·rotation, private routing·TLS/security group, contract test와 retry/DLQ·staging latency 검증은 wire 계약 재결정이 아닌 구현·배포 gate로 남겼다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis, Python 시험 AI/Callback `user_id=examId`와 프론트의 기존 response shape은 변경하지 않았다. 애플리케이션·테스트 코드, Mongo 데이터, Jira, Git commit·push와 배포도 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 테스트: 문서 변경이라 Gradle 테스트는 실행하지 않았다. `git diff --check`가 통과했고 종료 전에 현재 marker 단일 포함을 확인한다.
- 다음 작업 전 확인 사항: 사용자가 잔여 권장안을 승인하면 프론트 계약의 `aiResult`·no-speech·polling·audioUrl과 AI 계약의 text/payload 상한을 같은 버전 기준으로 동결한다. 이후 Challenge backend·AI 구현 Jira와 공유 contract fixture를 만든다.

## 2026-08-28 — 10초 챌린지 프론트 v1 잔여 계약 확정

<!-- codex-turn:01a04795-e33d-72b0-b54c-2523b758b101 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이며 Jira를 조회하거나 변경하지 않았다. Learning Core Challenge backend·AI 연동 전용 Jira는 아직 없다.
- 작업 목표: `aiResult`에 DB 사전 정의 답안을 추가하고 no-speech에서도 이를 유지하며, MEMBER 전용과 앞서 제안한 잔여 권장안을 프론트·AI v1 계약으로 확정한다.
- 결과 DTO: `aiResult.referenceAnswer`를 추가했다. AI Callback field가 아니라 Learning Core가 attempt 생성 시 Mongo `questions[].referenceAnswer`에서 저장한 snapshot을 조립하며 정상 완료와 no-speech에서 항상 non-blank다.
- no-speech: `gradingStatus=completed`, `aiResult` non-null, `referenceAnswer` non-blank로 반환한다. transcript·verdict·correctedAnswer·feedback은 null이고 공개 `feedbackType`은 추가하지 않는다.
- 기존 참고 답안: 제출 직후 제공하는 top-level `question.referenceAnswer`는 유지한다. 완료 결과의 `aiResult.referenceAnswer`와 같은 snapshot 값이어야 하며 불일치하면 attempt snapshot을 authoritative 값으로 사용한다.
- 제품·프론트 확정: MEMBER 전용·Guest `403`, 기존 KST rollover 보호, foreground polling 최대 60초(처음 20초 2초·이후 5초), MVP 사용자 녹음 재생과 `audioUrl` 제외를 확정했다.
- AI 제한: transcript·corrected answer 각 1000자, meaning·grammar·pronunciation 각 500자, Callback JSON 전체 UTF-8 16 KiB로 제한하고 초과 시 `413 CALLBACK_PAYLOAD_TOO_LARGE`로 정했다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/contracts/FRONTEND_API_HANDOFF.md`, `docs/contracts/ten-second-challenge-ai-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 변경했다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis, Python 시험 AI/Callback `user_id=examId`를 변경하지 않았다. 애플리케이션·테스트 코드, Mongo 데이터, Jira, Git commit·push와 배포도 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 테스트: 프론트·AI 계약의 모든 JSON code block을 Ruby JSON parser로 검증했고 통과했다. `git diff --check`도 통과했다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.
- 남아 있는 위험 요소: top-level과 `aiResult`에 referenceAnswer가 중복되므로 구현이 서로 다른 source를 조회하면 값이 어긋날 수 있다. 반드시 동일 attempt snapshot 하나로 두 field를 조립해야 한다.
- 다음 작업 전 확인 사항: Challenge backend·AI 구현 Jira를 만든 뒤 MEMBER authorization, attempt snapshot, no-speech projection, text/payload validation, polling과 audioUrl 비노출을 공유 contract fixture와 staging E2E로 검증한다.

## 2026-08-28 — 10초 챌린지 구현 착수 가능성 점검

<!-- codex-turn:01a0479c-1f0d-7180-9944-90b14c0bfbbd -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이며 Jira를 조회하거나 변경하지 않았다. Learning Core Challenge backend 전용 Jira는 아직 없다.
- 작업 목표: 승인된 프론트·AI v1 계약을 기준으로 실제 Learning Core 구현에 즉시 착수할 수 있는지 저장소 규칙과 준비 상태를 점검한다.
- 결론: 기술 계약은 구현 가능한 수준으로 준비됐다. 그러나 현재 `AGENTS.md`의 “현재 추가하지 않을 기능”에 10초 챌린지가 포함돼 있고 이 범위를 허용하는 명시적 Jira 예외가 없어 지금 애플리케이션 구현을 시작하면 저장소 규칙을 위반한다.
- 필수 선행 작업: Learning Core backend 구현 Jira를 만들고 해당 이슈에만 적용되는 AGENTS 명시적 예외를 추가해야 한다. 현재 계약 문서 변경은 사용자가 commit·push하고 전용 branch에서 구현하는 것이 안전하다.
- 구현 범위: content catalog/state와 KST day resolver, ChallengeAttempt·DB snapshot, S3 upload-url·submit, MEMBER authorization, GradingJob·AI request/callback fencing, result/history API, migration/index와 contract/integration test의 vertical slice다.
- 운영 준비: 실제 값을 문서나 코드에 넣지 않고 방향별 service credential 환경변수·Secrets Manager, private routing·TLS/security group, feature flag 기본 off와 staging E2E를 준비해야 한다.
- 변경 파일: 계약 승인 상태와 착수 blocker 기록을 위해 `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 변경했다. 애플리케이션·테스트 코드와 `AGENTS.md`는 변경하지 않았다.
- 유지한 계약: 프론트·AI v1, 기존 시험 API·DTO·`BaseResponse`, S3·Redis와 Python 시험 AI/Callback `user_id=examId` 계약을 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 테스트: 코드 변경이 없는 착수 점검이라 Gradle 테스트는 실행하지 않았다. 종료 전에 marker 단일 포함과 `git diff --check`를 확인한다.
- 다음 작업 전 확인 사항: Learning Core backend Jira 키와 그 키에 한정한 AGENTS 예외를 확정한 뒤 구현을 시작한다. 구현 완료 후 `./gradlew clean test`, 양쪽 contract fixture와 staging mobile/audio E2E를 수행한다.

## 2026-08-28 — AGENTS.md 10초 챌린지 구현 범위 승인 반영

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 키는 없으며 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이다. Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 사용자의 명시 결정에 따라 10초 챌린지를 Learning Core의 현재 추가 기능으로 허용하고 이후 구현자가 따라야 할 저장소 규칙을 확정한다.
- 제외 해제: `AGENTS.md`의 “현재 추가하지 않을 기능”에서 10초 챌린지를 제거했다. 이전 점검에서 확인한 저장소 범위 blocker는 해소됐다.
- 계약 기준: `docs/contracts/ten-second-challenge-frontend-api.md` v1, `docs/contracts/ten-second-challenge-ai-api.md` v1과 상세 결정서를 authoritative source로 지정했다.
- 구현 경계: 기존 시험 aggregate·retryCount·Job·AI Callback과 Challenge를 분리하고, 기존 시험 공개 API·DTO·BaseResponse·Redis·S3 key와 `user_id=examId` 계약을 변경하지 않도록 했다.
- Challenge 규칙: MEMBER·소유권, Mongo 콘텐츠와 referenceAnswer snapshot, 자동 KST Day 1·비순환 resolver, 1시간 attempt·분리 upload URL, M4A/AAC, no-speech `aiResult.referenceAnswer`, AI request/callback fencing·payload 상한과 secret·로그 비노출을 명시했다.
- 테스트·리뷰: catalog/date, authorization, attempt/S3, snapshot/no-speech, AI idempotency·stale callback을 Mock·fixture로 검증하고 전체 `./gradlew clean test`를 실행하도록 했다. 코드 리뷰 우선순위에도 Challenge 전용 8개 항목을 추가했다.
- 변경 파일: `AGENTS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 변경했다. 애플리케이션·테스트 코드는 변경하지 않았다.
- 유지한 계약: 승인된 프론트·AI v1와 기존 시험 계약을 변경하지 않았다. 실제 secret·credential과 사용자 음성·transcript를 기록하지 않았다.
- 테스트: 규칙·문서 변경이라 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`와 10초 챌린지 제외 항목 제거·허용 섹션 존재를 확인한다.
- 남아 있는 위험 요소: 아직 Challenge backend·AI 구현 전용 Jira가 없어 작업 추적 범위가 넓을 수 있다. 구현은 vertical slice와 feature flag 기본 off로 진행하고 관련 없는 기존 시험 코드를 건드리지 않아야 한다.
- 다음 작업 전 확인 사항: 사용자가 구현을 요청하면 승인된 AGENTS와 v1 계약을 기준으로 application code·migration·test를 구현할 수 있다. Jira 생성은 권장하지만 더 이상 저장소 범위 blocker는 아니다.

## 2026-08-28 — staging·production 월 인프라 비용 추정

<!-- codex-turn:01a04738-52f5-7981-89fe-502b5e861de5 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 기존 AI 멱등성 관련 `TMI-25`는 비용 산정 범위가 아니다. Jira를 조회하거나 변경하지 않았다.
- 사용자 요청에 따라 서울 리전의 AWS 공식 가격과 MongoDB 공식 가격을 읽기 전용으로 확인하고 staging+production 월 비용을 계산했다. 확인 단가는 Fargate vCPU `$0.04656/hour`, memory `$0.00511/GB-hour`, ALB `$0.0225/hour + $0.008/LCU-hour`, public IPv4 `$0.005/address-hour`, NAT `$0.059/hour + $0.059/GB`, Atlas M10 시작 `$56.94/month`, Flex 최저 `$8/month`, ElastiCache Serverless for Valkey 시작 `$6/month`이다.
- 실제 확인된 AI Task `2 vCPU/4GB`를 사용하고 Identity/Learning Core `0.5 vCPU/1GB`, Billing `0.25 vCPU/0.5GB`, 환경별 Task 한 개·ALB 한 개·NAT 한 개, production Atlas M10, staging Flex와 낮은 초기 트래픽을 가정했다.
- 기준 결과는 월 `$515.82`, 환율 `1 USD=1,400 KRW`와 VAT 10% 가정 약 79만원이며 안전 예산은 월 80만~90만원이다. staging 시간제 운영·NAT 대체 비용 최적화안은 약 59만원, production Task 이중화·NAT 2AZ·Atlas M30 보수적 HA안은 약 164만원이다.
- `docs/codex/MONTHLY_INFRA_COST_ESTIMATE.md`를 추가하고 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 갱신했다. 외부 AI/provider 호출료, Apple/Google 수수료, Atlas backup/egress, 대량 S3·CloudWatch 사용은 합계에서 제외하고 실제 청구 보정에 필요한 inventory를 명시했다.
- 애플리케이션·설정·테스트 코드, AWS/MongoDB 리소스, 외부 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3와 retryCount 계약을 변경하지 않았고 Secret·Token을 조회하거나 기록하지 않았다.
- 분석·문서 작업이므로 Gradle 테스트는 실행하지 않았다. 공식 가격 페이지는 읽기 전용으로 확인했으며 `git diff --check`와 marker 단일 포함을 종료 전에 검증한다.

## 2026-08-28 — production 상시·staging 테스트 시 운영 비용 재산정

<!-- codex-turn:01a0473d-f452-783f-8573-998322083865 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자가 production만 24시간 운영하고 staging은 테스트할 때만 켠다고 확정했다. staging 월 40시간을 기본 가정으로 월 비용을 다시 계산했다.
- production Fargate는 약 `$134.71/month`, staging 네 서비스 Fargate는 40시간에 약 `$7.38`이다. staging ALB·NAT도 테스트 때 IaC로 생성·제거하는 최저 비용안은 전체 약 `$317.80`, 환율 1,400원과 VAT 10% 가정 약 49만원으로 안전 예산 월 50만~55만원이다.
- staging ECS Task만 `desiredCount=0`으로 내리고 ALB·NAT·Atlas Flex·Valkey를 유지하면 고정비가 남아 전체 약 `$383.49`, 약 59만원이다. 따라서 현재 현실적인 월 서버 예산을 50만~60만원으로 수정했다.
- ALB와 NAT Gateway는 ECS Task가 꺼져 있어도 남아 있으면 시간 요금이 계속 발생한다. staging 네트워크까지 매번 제거하려면 수동 작업이 아니라 IaC 재생성·삭제와 데이터 초기화가 필요하다.
- `docs/codex/MONTHLY_INFRA_COST_ESTIMATE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·설정·테스트 코드, AWS/MongoDB 리소스, 외부 API·DTO·AI/Callback·Redis/S3 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 분석·문서 작업이므로 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — production 상시·staging 필요시 운영 비용 기록 동기화

<!-- codex-turn:01a0473d-f452-75f2-adcc-031539a4d6eb -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 종료 훅 요구에 따라 이번 turn의 비용 재산정 기록을 WORKLOG 끝에 추가했다. production은 24시간, staging은 월 40시간 테스트 사용을 가정한다.
- staging ALB·NAT까지 필요시에만 IaC로 생성·제거하는 안은 약 `$317.80/month`, 환율 1,400원과 VAT 10% 가정 약 49만원이다. staging ECS만 중지하고 ALB·NAT를 유지하는 안은 약 `$383.49/month`, 약 59만원이다.
- 현실적인 월 서버 안전 예산은 50만~60만원이다. ECS Task 중지만으로 ALB·NAT 시간 요금은 중단되지 않는다는 전제와 외부 AI/provider 호출료 제외 범위는 기존 상세 산정과 동일하다.
- `docs/codex/CURRENT_STATE.md`와 `docs/codex/WORKLOG.md` 기록만 동기화했다. 애플리케이션·설정·테스트 코드, AWS/MongoDB 리소스, 외부 API·DTO·AI/Callback·Redis/S3 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — AI 축소·무료 Mongo 기준 월 비용 재산정

<!-- codex-turn:01a04741-71aa-7162-9d95-83149039f479 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자가 AI Task를 `2 vCPU/4GB`에서 `1 vCPU/2GB`로 낮추고 MongoDB는 현재 무료 plan을 유지하며 기타 예비비는 제외해 달라고 요청했다.
- 서울 Fargate 단가 기준 AI production 24시간 비용은 `$41.45/month`이고 Identity·Learning Core·Billing까지 포함한 production Fargate는 `$93.26`이다. 동일 크기의 staging을 월 40시간 실행하면 Fargate 비용은 `$5.11`이다.
- staging ALB·NAT도 테스트 때만 IaC로 생성·제거하면 Valkey 두 환경 최소 `$12`를 포함한 전체는 약 `$184.14`, 환율 1,400원과 VAT 10% 가정 약 28.4만원으로 월 29만~30만원 예산이다. staging ECS만 중지하고 ALB·NAT를 유지하면 약 `$249.83`, 약 38.5만원으로 월 39만~40만원이다.
- 현재 실제 AI Task Definition은 `2 vCPU/4GB`이고 API와 worker 4개가 한 Task에서 실행된다. `1 vCPU/2GB`는 아직 검증되지 않은 목표값이므로 적용 전 staging에서 CPU throttling, peak RSS/OOM, queue backlog, 동시 채점과 처리 p95를 확인해야 한다.
- `docs/codex/MONTHLY_INFRA_COST_ESTIMATE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·Task Definition·AWS/MongoDB 리소스, 외부 API·DTO·AI/Callback·Redis/S3 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 분석·문서 작업이므로 Gradle 테스트는 실행하지 않았으며 산술 재검증, `git diff --check`와 marker 단일 포함을 종료 전에 검증한다.

## 2026-08-28 — 토스트 가격 기준 무제한 멤버십 BEP 산정

<!-- codex-turn:01a04748-8ba9-7de3-bcfe-dd6498f32265 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자 제공 토스트 앱 화면에서 24시간 9,000원, 3일 19,000원, 7일 29,000원, 2주 49,000원, 4주 69,000원의 일회성 무제한 멤버십 가격을 확인했다. 사용자가 말한 한 달은 화면상 4주(28일)로 해석했다.
- 월 고정비 380,000원, 모의고사 1회당 AI API 250원, 구매자당 하루 평균 1회 응시, 표시가격 VAT 10%와 IAP 15% 차감 가정으로 상품 단독 판매 BEP를 계산했다. 24시간 57건, 3일 28건, 7일 19건, 2주 12건, 4주 9건이다.
- 수수료 미반영 단순 BEP는 44·21·14·9·7건이지만 실제 운영 판단에는 부적합하다. IAP 30% 민감도는 70·34·23·14·11건이다. 혼합 판매는 상품별 `판매수 × 공헌이익` 합계가 380,000원 이상인 지점이 BEP다.
- 무제한 상품은 구매자당 평균 시험 수 없이는 단일 BEP가 정해지지 않으므로 실제 사용량 공식과 4주 56회·24시간 5회 예시를 추가했다. 무료시험·추천·쿠폰·환불·광고비와 외부 운영비는 제외했다.
- `docs/codex/SUBSCRIPTION_BEP_ESTIMATE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·Billing·Task Definition·AWS/MongoDB 리소스, 외부 API·DTO·AI/Callback·Redis/S3 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 분석·문서 작업이므로 Gradle 테스트는 실행하지 않았다. 산술, `git diff --check`와 marker 단일 포함을 종료 전에 검증한다.

## 2026-08-28 — production 실제 Task 크기와 일 $12.6 비용 차이 분석

<!-- codex-turn:01a04747-90c7-7a42-ac17-0e494c530ca7 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자가 production의 실제 크기를 Identity `1 vCPU/3GB`, Learning Core `1 vCPU/3GB`, AI `2 vCPU/4GB`이며 관측 비용은 `$12.6/day`라고 정정했다. 이전 표의 `$20.72/$20.72/$41.45`는 이전 작은 크기의 월 비용이라 현재 크기와 불일치했다.
- 서울 Fargate 단가로 Task 각 한 개의 정확한 비용은 Identity `$45.18/month`, Learning Core `$45.18`, AI `$82.90`, 합계 `$173.26/month` 또는 `$5.696/day`다. 관측값과 하루 `$6.904` 차이가 난다.
- 세 ECS Service가 각각 desired/running count 2라면 compute가 `$11.392/day`이고 ALB base, public IPv4 두 개와 평균 0.25 LCU를 합친 약 `$12.22/day + logs/data`가 되어 관측 `$12.6/day`와 매우 가깝다. 다만 Cost Explorer 분해 전에는 가설이다.
- 확정 확인 항목은 ECS Service별 desired/running/deployment Task 수와 Cost Explorer의 Fargate vCPU·GB hours, NAT hours/bytes, ALB hours/LCU, public IPv4, CloudWatch usage type이다. 분해 전 월 고정비는 실제 관측 `$12.6×30=$378`, 환율 1,400원 기준 VAT 전 약 529,200원을 우선한다.
- `docs/codex/MONTHLY_INFRA_COST_ESTIMATE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·Task Definition·AWS 리소스, 외부 API·DTO·AI/Callback·Redis/S3 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 분석·문서 작업이므로 Gradle 테스트는 실행하지 않았다. 산술, `git diff --check`와 marker 단일 포함을 종료 전에 검증한다.

## 2026-08-28 — production 일 $12.6 관측 비용 설명 보완

<!-- codex-turn:01a0474b-72d4-7193-a857-8b5f5d3a92d0 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자에게 이전 비용표가 현재 Task 크기와 과거 단가를 혼용해 잘못됐음을 명시하고, 서울 Fargate 기준 Task 각 1개 실행 시 compute가 약 `$5.696/day`임을 설명했다.
- 실제 `$12.6/day`는 각 서비스 Task 2개 실행과 ALB·public IPv4·LCU·로그 비용 조합으로 설명될 가능성이 높지만, 이는 ECS desired/running count와 Cost Explorer usage type을 확인하기 전까지 가설임을 명시했다.
- 실제 관측 run rate는 월 `$378`, 환율 1,400원 기준 VAT 전 약 529,200원이며 원인 분해 전에는 기존 38만원보다 이 값을 운영 기준으로 삼도록 안내했다.
- 이번 후속 작업은 기록 동기화뿐이며 애플리케이션·설정·테스트 코드, AWS 리소스, 외부 API·DTO·AI/Callback·Redis/S3 계약을 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — production 관측 비용 $1.26 재정정

<!-- codex-turn:01a0474e-6693-7380-9cd1-bd2f16492912 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자가 관측 비용의 소수점을 정정하여 `$12.6/day`가 아니라 `$1.26`이라고 확인했다.
- 단순 월 환산은 `$37.80`, 환율 1,400원 기준 VAT 전 약 52,920원이지만, 명시된 세 Task가 각 1개씩 24시간 실행될 때 Fargate compute만 `$5.696/day`이므로 `$1.26`을 완전한 하루 총비용이나 고정비로 확정할 수 없다.
- Cost Explorer의 조회 기간, 서비스·usage type 필터, cost type, credit·discount와 ECS Task 실제 실행 시간을 확인해야 한다. 분해 전에는 크기 기준 Fargate compute `$173.26/month`에 네트워크·로그 비용을 더한 값을 계획 기준으로 유지한다.
- 비용 문서와 CURRENT_STATE만 정정했고 애플리케이션·테스트 코드, AWS 리소스, 외부 API·DTO·AI/Callback·Redis/S3 계약은 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — 단기 이용권 집중 사용을 반영한 BEP 보정

<!-- codex-turn:01a04750-efcd-77b0-bf68-586f4eaf7175 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 짧은 무제한 이용권일수록 구매 직후 응시가 집중된다는 가정으로 평균 응시를 24시간 4회, 3일 8회, 7일 14회, 2주 21회, 4주 28회로 보정했다.
- VAT 10%·IAP 15%, 월 고정비 380,000원, 완료 시험당 AI 250원 기준 공헌이익은 5,955원·12,682원·18,909원·32,614원·46,318원이며 월 BEP는 64건·30건·21건·12건·9건이다.
- 24시간권은 평균 3~5회에서 BEP 62~67건, 3일권은 평균 6~10회에서 29~32건이다. 이는 출시 전 계획값이므로 출시 후 상품별 구매 cohort의 completed exam 평균·p95로 교체해야 한다.
- `docs/codex/SUBSCRIPTION_BEP_ESTIMATE.md`와 CURRENT_STATE만 갱신했으며 애플리케이션·AWS·Jira·외부 계약은 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 문서·산술 작업이라 Gradle 테스트는 실행하지 않았으며 산술, `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — 단기권 집중 사용 BEP 종료 기록 동기화

<!-- codex-turn:01a04750-114e-73c1-ae17-f970e7f7f276 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 종료 훅 요구에 따라 이번 turn의 단기 이용권 집중 사용 BEP 보정 결과를 WORKLOG 끝에 별도 기록했다.
- 기준 평균 응시는 24시간 4회, 3일 8회, 7일 14회, 2주 21회, 4주 28회이며 VAT 10%·IAP 15%·월 고정비 380,000원·AI 250원/회 기준 BEP는 64건·30건·21건·12건·9건이다.
- 비용 문서와 CURRENT_STATE 기록만 동기화했으며 애플리케이션·AWS·Jira·외부 계약을 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — 조직 계정 실제 단가 기반 4서비스 축소 비용 추정

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- `1/2, 1/2, 1/1, 1/2`를 Identity·Learning Core·Billing·AI 순서의 vCPU/GB로 해석했다.
- 조정 후 정상 단가 자원비는 현재 `1/3 + 1/3 + 2/4` 구성의 93.54%다. 조직 계정 실제 관측 `$1.26/day`와 동일한 정산 효과가 유지되면 약 `$1.179/day`, `$35.36/30일`, 환율 1,400원·VAT 포함 약 54,500원이다.
- 조직 혜택이 사라질 경우 compute 정상 단가는 `$162.07/month`, 환율·VAT 적용 약 249,600원이며 ALB·NAT·IPv4·로그·전송료는 별도다. Organizations 자체가 자동 할인을 의미하지 않으므로 실제 credit·Savings Plans·private pricing·cost type 확인이 필요하다.
- 비용 문서와 CURRENT_STATE만 갱신했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 문서·산술 작업이라 Gradle 테스트는 실행하지 않았으며 산술과 `git diff --check`를 검증한다.

## 2026-08-28 — 조직 계정 단가 기반 축소 비용 종료 기록

<!-- codex-turn:01a04753-22e2-7502-a9f5-4651e2a8fd32 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 종료 훅 요구에 따라 Identity `1 vCPU/2GB`, Learning Core `1 vCPU/2GB`, Billing `1 vCPU/1GB`, AI `1 vCPU/2GB` 조정 비용 추정 결과를 WORKLOG 끝에 기록했다.
- 현재 조직 계정 관측 단가 효과가 유지되면 약 `$1.179/day`, `$35.36/30일`, 환율 1,400원·VAT 포함 약 54,500원이다. 정상 단가 compute는 `$162.07/month`, 환율·VAT 적용 약 249,600원이며 네트워크·로그는 별도다.
- 비용 문서와 CURRENT_STATE 기록만 동기화했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — 조정 사양 기준 전체 서버비 재계산

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 조정 사양 `1/2, 1/2, 1/1, 1/2`와 조직 계정 실제 관측 `$1.26/day`를 기준으로 서버비를 다시 계산했다.
- `$1.26`이 AWS 전체 비용이면 production 약 `$35.36/month`, staging 10% 여유 포함 VAT 기준 약 6만원이며 운영 예산은 월 7만원이다.
- `$1.26`이 Fargate compute 필터 결과라면 주변 인프라를 더해 staging ALB·NAT 시간제 생성 시 VAT 포함 약 19만원, 상시 유지 시 약 29만원이다. Cost Explorer의 필터 없는 Service별 합계로 구분해야 한다.
- 비용 문서와 CURRENT_STATE만 갱신했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 문서·산술 작업이라 Gradle 테스트는 실행하지 않았으며 산술과 `git diff --check`를 검증한다.

## 2026-08-28 — 조정 사양 전체 서버비 종료 기록

<!-- codex-turn:01a04755-0c1b-7f93-92d5-b9c6c2bebc46 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 종료 훅 요구에 따라 조정 사양 기준 전체 서버비 재계산 결과를 WORKLOG 끝에 기록했다.
- `$1.26/day`가 AWS 전체 합계면 staging 여유 포함 운영 예산은 월 약 7만원이고, Fargate만의 비용이면 주변 인프라를 포함해 staging 네트워크 시간제 생성 약 19만원 또는 상시 유지 약 29만원이다.
- 비용 문서와 CURRENT_STATE 기록만 동기화했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — AWS 전체 관측 비용 기준 서버비 최종 재계산

<!-- codex-turn:01a04756-facd-7cb0-87fe-2bede7713b5c -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자가 `$1.26/day`가 Fargate뿐 아니라 ALB·NAT·로그 등 다른 항목까지 포함한 AWS 전체 비용이라고 확정했다.
- 조정 사양에서 compute 정상 단가는 6.46% 감소하지만 고정비는 유지되므로 전체 비용은 `$1.179~1.260/day`, `$35.36~37.80/30일`, 환율 1,400원·VAT 포함 약 54,500~58,200원이다. compute 비중 70% 기준은 약 55,600원이다.
- 운영 예산은 청구 지연·환율·staging 테스트 여유를 포함해 월 7만원으로 유지한다. 기존 월 19만~29만원 시나리오는 Fargate 외 비용을 중복 가산한 것이므로 폐기했다.
- 비용 문서와 CURRENT_STATE만 갱신했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 문서·산술 작업이라 Gradle 테스트는 실행하지 않았으며 산술, `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — 월 7만원 서버 운영 예산 표 작성

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 조정 사양의 월 운영 예산 70,000원을 production 예상액 55,600원, staging 테스트 여유 5,600원, 환율·청구 지연·사용량 변동 완충액 8,800원으로 표로 분해했다.
- 완충액은 확정 청구 항목이 아니라 환율 상승, Cost Explorer 반영 지연, 배포 중 Task 중복과 소량 로그·전송량 변동을 흡수하는 예산이다.
- 비용 문서와 CURRENT_STATE만 갱신했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 문서 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`를 검증한다.

## 2026-08-28 — AWS 전체 비용 $5.49 기준 최종 재계산

<!-- codex-turn:01a04759-200b-7991-8588-b8bb70aeee34 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자가 다른 항목을 모두 포함한 AWS 전체 비용을 `$1.26/day`가 아니라 `$5.49/day`로 재정정했다.
- 조정 사양에서 compute는 최대 6.46% 줄지만 고정비는 유지되므로 production 전체 비용은 `$5.135~5.490/day`, `$154.06~164.70/30일`, 환율 1,400원·VAT 포함 약 237,300~253,600원이다.
- compute 비중 70% 기준 production 약 242,200원, staging 10% 약 24,200원, 환율·청구 지연·사용량 변동 완충액 약 33,600원으로 월 운영 예산을 300,000원으로 수정했다. 과거 월 7만원 예산은 폐기한다.
- 비용 문서와 CURRENT_STATE만 갱신했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 문서·산술 작업이라 Gradle 테스트는 실행하지 않았으며 산술, `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — 월 30만원 서버 예산 최종 표 정리

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 서버 예산을 production 242,200원, staging 24,200원, 변동 대응 33,600원으로 나누어 월 300,000원으로 확정한 관리 표를 작성했다.
- 완료 모의고사 1회당 AI API 250원은 서버 고정비에 포함하지 않고 별도 변동비로 유지했다.
- 비용 문서와 CURRENT_STATE만 갱신했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 문서 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`를 검증한다.

## 2026-08-28 — 월 30만원 서버 예산표 종료 기록

<!-- codex-turn:01a0475a-2bcc-79e1-b170-b34293edc771 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 종료 훅 요구에 따라 월 서버 운영 예산 300,000원 표 작성 결과를 WORKLOG 끝에 기록했다.
- 구성은 production 242,200원, staging 24,200원, 변동 대응 33,600원이며 AI API 250원/완료 모의고사는 별도 변동비다.
- 비용 문서와 CURRENT_STATE 기록만 동기화했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — 고정비 30만원·AI 실측 275.28원 기준 BEP 재계산

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자가 제공한 STT·Azure·LLM·최종 요약 비용 합계인 모의고사 1회당 275.28원과 월 서버 고정비 300,000원을 적용했다.
- 단기권 집중 사용량 4·8·14·21·28회, VAT 10%·IAP 15% 기준 공헌이익은 약 5,854원·12,480원·18,555원·32,083원·45,610원이며 월 BEP는 52건·25건·17건·10건·7건이다.
- IAP 30% 민감도는 65건·31건·21건·12건·9건이다. 무료 시험·추천·쿠폰·환불·광고비는 제외했으며 실제 cohort 사용량으로 추후 보정해야 한다.
- BEP 문서와 CURRENT_STATE만 갱신했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 문서·산술 작업이라 Gradle 테스트는 실행하지 않았으며 산술과 `git diff --check`를 검증한다.

## 2026-08-28 — 고정비 30만원·AI 실측 BEP 종료 기록

<!-- codex-turn:01a0475c-3e3b-75c0-9177-448a96ac1cdc -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 종료 훅 요구에 따라 월 고정비 300,000원과 모의고사당 AI 실측 275.28원 기준 BEP 재계산 결과를 WORKLOG 끝에 기록했다.
- 단기권 집중 사용과 VAT 10%·IAP 15% 기준 BEP는 24시간 52건, 3일 25건, 7일 17건, 2주 10건, 4주 7건이며 IAP 30%에서는 65건·31건·21건·12건·9건이다.
- BEP 문서와 CURRENT_STATE 기록만 동기화했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — 전체 앱 프론트 API 인계서 재점검

<!-- codex-turn:01a04797-fa12-7192-8c0d-b6c6bb402085 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 완료 상태로 기록된 Billing `TMI-110`, `TMI-112`, `TMI-113`과 Challenge 관련 `TMI-102`, `TMI-105`, `TMI-106`을 현재 구현 경계 확인에 사용했다. Learning Core withdrawal `TMI-109`와 후속 Identity publisher `TMI-111`의 내부 endpoint 경계도 유지했으며 Jira를 조회하거나 변경하지 않았다.
- 작업 목표: `docs/contracts/FRONTEND_API_HANDOFF.md`를 세 저장소의 현재 모든 Controller·DTO·Security 설정과 승인된 Challenge v1 계약에 역대조해 누락·시차·과도한 보장을 찾고 정정한다.
- 재점검 결과: Identity 프론트 API 17개와 Learning Core 프론트 API 11개는 누락이 없었다. Billing 공개 프론트 API는 0개이며 앱이 직접 호출해서는 안 된다는 기존 경계도 유효하다.
- 정정 내용: Billing은 `TMI-110` trial eligibility consumer, `TMI-112`의 `TrialClaim`·`FREE_EXAM_ONCE` initial reserve, `TMI-113`의 confirm/cancel/status·expiry lifecycle까지 구현돼 있어 “TrialClaim·Reservation 미구현” 문구를 현재 상태로 교체했다. `/internal/v1/reservations`, confirm, cancel, status 네 route를 프론트 호출 금지 표에 추가했다.
- Challenge 정렬: 프론트·AI 계약이 Draft가 아니라 승인된 v1·미구현 상태이므로 잔존 Draft/초안 표현을 제거했다. MEMBER, M4A/AAC-LC profile, 최대 2 MiB, AI result와 polling 등 승인 항목은 유지했다.
- 추가 위험 확인: 기존 모의고사 Presigned PUT은 코드상 5분 signature지만 DTO `expiresIn=60`이고, `.wav` Object Key를 사용하면서 Presigned 요청이 업로드 Content-Type이나 실제 codec을 고정·응답하지 않는다. 프론트가 만료·포맷을 과신하지 않도록 문서에 명시했다.
- 변경 파일: `docs/contracts/FRONTEND_API_HANDOFF.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드, 다른 계약 문서, Identity·Billing 저장소와 Jira는 변경하지 않았다.
- 유지한 계약: 기존 공개 URL·Method·Parameter·DTO·BaseResponse, userId 비전달, S3 Object Key, Redis, Python AI callback과 `user_id=examId` 계약을 변경하지 않았다. Secret·실제 Token·운영 credential을 기록하지 않았다.
- 테스트: 문서 재점검이므로 Gradle 테스트는 실행하지 않았다. 세 저장소의 Controller mapping 수, Security allowlist/default deny, DTO 소스와 계약 문서를 읽기 전용으로 대조했으며 종료 전에 `git diff --check`, trailing whitespace, 링크 대상과 marker 단일 포함을 검증한다.
- 남은 production 경계: Learning Core의 필수 lowercase UUID v4 `Idempotency-Key`와 Billing client/saga·reconciliation, Billing AttemptGroup event·owner rebind, Lattice/IAM/SG staging E2E, Challenge API·AI 양쪽 구현과 contract test가 남아 있다.

## 2026-08-28 — 1차 업데이트 진행 체크리스트 최신화

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 완료 기록이 있는 Identity `TMI-109`·`TMI-111`, Billing `TMI-110`·`TMI-112`·`TMI-113`과 Challenge `TMI-102`·`TMI-105`·`TMI-106`, Identity 후속 `TMI-114`를 현재 구현 경계 판정에 사용했다. Atlassian 실시간 조회는 connector 미설치 403으로 불가능해 저장소의 최근 Jira 확인 기록을 사용했으며 Jira는 변경하지 않았다.
- 작업 목표: 사용자가 전달한 과거 1차 업데이트 체크리스트를 현재 Identity·Billing·Learning Core 코드와 병합·테스트 기록에 맞게 갱신하고, 프론트가 읽을 수 있는 기존 API 인계서와 연결한다.
- 변경 파일: `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`를 추가하고 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 동기화했다. 애플리케이션 코드, 기존 공개 API·DTO, Identity·Billing 저장소와 Jira는 변경하지 않았다.
- 변경한 상태 판정: `TMI-109`는 consumer/gate 분리 flag·marker race 409·startup Transaction probe와 402개 테스트 기록을 근거로 완료, `TMI-111`은 outbox·publisher·bounded backfill과 109 suite·591개 테스트 기록을 근거로 완료로 정정했다. Billing은 eligibility consumer뿐 아니라 TrialClaim·`FREE_EXAM_ONCE` grant/ledger와 Reservation reserve/confirm/cancel/status/expiry까지 구현된 것으로 반영했다.
- 유지한 외부 계약: 기존 공개 URL·Method·Parameter·DTO·`BaseResponse`, 실제 userId 비전달, 기존 시험 S3·Redis·Python AI Callback과 `user_id=examId` 계약은 변경하지 않았다. Secret·Token·운영 credential도 기록하지 않았다.
- 현재 production 차단 항목: Learning Core Billing client·필수 lowercase UUID v4 `Idempotency-Key`·reserve/commit/confirm saga, Billing AttemptGroup terminal event·owner rebind, Challenge backend·AI 구현, 실제 모바일 SNS와 workload/Lattice·replica set·multi-instance staging E2E, response-loss·rollback·canary 검증이 남아 있다.
- 테스트: 문서 작업이라 Gradle 테스트는 실행하지 않았다. 문서 링크 대상, trailing whitespace, 기존 turn marker 단일 포함과 `git diff --check`를 종료 전에 검증한다.
- 다음 작업 전 확인 사항: `TMI-114`의 1차 출시 gate 여부를 정하고, Billing AttemptGroup/owner rebind와 Learning Core Billing saga를 먼저 연결한 뒤 Challenge backend Jira·구현과 서비스 간 staging E2E를 진행한다. Git commit과 push는 사용자가 수행한다.

## 2026-08-28 — 1차 업데이트 진행 체크리스트 종료 기록

<!-- codex-turn:01a0479c-e790-70a3-b7c5-f2e6ab6b8626 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: Identity `TMI-109`·`TMI-111`, Billing `TMI-110`·`TMI-112`·`TMI-113`, Challenge `TMI-102`·`TMI-105`·`TMI-106`, Identity 후속 `TMI-114`를 진행 상태 근거로 기록했다. Jira를 변경하지 않았다.
- 종료 결과: `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`에 현재 1차 업데이트 상태를 체크리스트로 정리하고 `docs/codex/CURRENT_STATE.md`와 `docs/codex/WORKLOG.md`를 동기화했다.
- 상태 요약: Identity withdrawal producer/consumer와 Billing TrialClaim·`FREE_EXAM_ONCE`·Reservation lifecycle 기반은 완료됐지만 Learning Core Billing saga, Billing AttemptGroup/owner rebind, Challenge backend·AI 구현, 실제 모바일·workload·staging E2E와 canary가 남아 production 출시는 차단 상태다.
- 외부 계약: 애플리케이션 코드와 기존 공개 API·DTO·`BaseResponse`, S3·Redis·Python AI의 `user_id=examId` 계약을 변경하지 않았다. Secret·Token·운영 credential을 기록하지 않았다.
- 검증: 링크 대상과 trailing whitespace를 확인했고 `git diff --check`가 통과했다. 문서 변경만 있어 Gradle 테스트는 실행하지 않았다.
- 다음 작업: `TMI-114`의 출시 gate 여부를 확정하고 Billing AttemptGroup/owner rebind, Learning Core Billing saga, Challenge backend, staging 종단 검증 순으로 진행한다. Git commit과 push는 사용자가 수행한다.

## 2026-08-28 — AGENTS 10초 챌린지 범위 승인 종료 기록 동기화

<!-- codex-turn:01a0479d-f036-7ef1-855f-0ac8a70af0f5 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 키는 없으며 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이다. Jira를 조회하거나 변경하지 않았다.
- 완료 상태: `AGENTS.md`의 현재 제외 목록에서 10초 챌린지를 제거하고 프론트·AI v1 기반의 구현 허용, domain 격리, MEMBER·콘텐츠·attempt/S3·AI/Callback·테스트·리뷰 규칙을 추가했다.
- 착수 상태: 저장소 범위 blocker는 해소됐으며 사용자의 구현 요청이 있으면 application code·migration·test 작업을 시작할 수 있다. 전용 Jira는 추적을 위해 권장하지만 필수 blocker는 아니다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, retryCount, Redis·S3와 Python 시험 AI/Callback `user_id=examId` 계약은 변경하지 않았다.
- 변경하지 않은 항목: 애플리케이션·테스트 코드, Mongo 데이터, Jira, Git commit·push와 배포는 변경하지 않았다. Secret과 Token을 기록하지 않았다.
- 테스트 결과: 규칙·문서 변경이라 Gradle 테스트는 실행하지 않았다. `git diff --check`는 통과했다.
- 다음 작업 전 확인 사항: 승인된 v1 계약과 AGENTS 규칙에 따라 Challenge를 feature flag 기본 off의 vertical slice로 구현하고 전체 Gradle·contract·staging E2E를 수행한다.

## 2026-08-28 — Challenge 제외 기존 시험·Billing 잔여 구현 재확인

<!-- codex-turn:01a04856-1523-7f23-acea-0ef42f5c543e -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 완료 근거는 Learning Core `TMI-109`, Identity `TMI-111`, Billing `TMI-110`·`TMI-112`·`TMI-113`이며 후속 Identity 계획은 `TMI-114`다. Challenge 관련 기존 키는 `TMI-102`, `TMI-105`, `TMI-106`이다. Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 사용자가 제시한 Learning Core 잔여 목록이 Challenge를 제외한 현재 실제 구현 공백과 일치하는지 Learning Core·Identity·Billing 코드와 최신 체크리스트에 대조한다.
- Learning Core 확인: `POST /api/v1/exams`는 `Idempotency-Key` header 없이 바로 `createExamSession()`을 호출하고, Session 생성은 Billing reserve/confirm 없이 즉시 수행된다. Billing client·reservation/operation metadata·UserMerged·AttemptGroup event·reconciliation 코드는 없다.
- Identity 확인: UserMerged outbox·publisher는 구현돼 있지만 Learning Core 수신 consumer가 없다. 따라서 학습 데이터 owner 이동과 source actor deny 수렴은 남은 구현이 맞다.
- Billing 확인: TrialClaim·FREE_EXAM_ONCE grant/ledger, Reservation reserve/confirm/cancel/status·expiry와 AttemptGroup/AttemptSession 기반은 구현돼 있다. 남은 것은 Learning Core client/saga와 AttemptGroup event consumer, owner rebind, repair/reconciliation 및 실제 service transport다.
- 목록 판정: `UserMerged` consumer, Billing client+saga, 필수 공개 시험 생성 `Idempotency-Key`와 replay, AttemptGroup outbox/publisher와 R3 연결, Billing 장애 reconciliation은 모두 실제 잔여 구현이 맞다. 채점 dispatch의 기존 `Idempotency-Key`는 시험 생성 operation key가 아니다.
- 범위 보완: 이 목록은 Learning Core 기존 시험 연동 기준이다. 1차 출시 전체에는 Billing owner rebind, `TMI-114` 포함 여부·구현, 실제 모바일 SNS/phone, workload/Lattice/IAM/SG, replica-set·multi-instance, response-loss·rollback·canary E2E도 남아 있다.
- 변경 파일: 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·테스트 코드와 Identity·Billing 저장소는 수정하지 않았다.
- 유지한 계약: 기존 공개 API·DTO·`BaseResponse`, 실제 userId 비전달, S3·Redis와 Python 시험 AI/Callback `user_id=examId` 계약을 변경하지 않았다. Secret과 Token을 기록하지 않았다.
- 테스트: 읽기 전용 코드·문서 대조라 Gradle 테스트는 실행하지 않았다. 종료 전에 marker 단일 포함과 `git diff --check`를 확인한다.
- 다음 작업 전 확인 사항: 구현 순서는 Billing AttemptGroup consumer → Billing owner rebind → Learning Core Idempotency-Key·Reservation saga·AttemptGroup outbox·reconciliation → service transport와 staging E2E가 의존성상 안전하다. `TMI-114`는 1차 출시 gate인지 별도 결정이 필요하다.

## 2026-08-28 — 다음 작업 Billing AttemptGroup 상태 event consumer 계획

<!-- codex-turn:01a04858-ea2d-7f03-a80c-8ccca8d3420c -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다. 선행 완료 근거는 Billing `TMI-110`·`TMI-112`·`TMI-113`이다.
- 다음 구현 작업은 Billing의 `POST /internal/v1/attempt-group-events` consumer vertical slice로 선정했다. 기존 `AttemptGroup`·`AttemptSession`과 Reservation 기반은 있으나 상태 event 수신 코드가 없어 실제 상태가 `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE`로 수렴하지 않는 것이 근거다.
- 구현 범위는 schema v1 strict decode와 16 KiB 제한, event·enum·UTC 시각 검증, `eventId + canonical digest` inbox 멱등성, active Session fencing, group/session version CAS, 상태 전이·terminal 보호, workload route/security 설정과 transaction·동시성·중복·stale·역순 테스트다.
- 정책 권장안은 terminal evidence가 확인되면 `OPEN`에서도 전진 수렴을 허용하고 `COMPLETED`는 다시 열지 않으며, 존재하지 않는 group/session은 선행 projection 지연으로 보아 retryable 응답, owner 불일치는 계약 위반으로 non-retryable 격리, stale Session은 `204` 성공 no-op으로 처리하는 것이다. `RETAKE_AVAILABLE.failureCode`는 저 cardinality allowlist만 허용한다.
- 이번 계획에서는 Learning Core outbox/publisher, Billing owner rebind, Reservation saga·reconciliation, 실제 AWS Lattice 배포와 결제 기능을 제외했다. 애플리케이션 코드와 외부 API 계약은 변경하지 않았다.
- 분석·계획 작업이라 Gradle 테스트는 실행하지 않았다. 종료 전에 문서 형식과 `git diff --check`, marker 단일 포함을 검증한다. Git commit과 push는 사용자가 수행한다.

## 2026-08-28 — PLAN-005 AttemptGroup 상태 event consumer 계획서 작성

<!-- codex-turn:01a0485b-f6bb-7d93-8244-d326b71900f3 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira는 생성하지 않았고 기존 Jira 상태도 변경하지 않았다. Billing 선행 완료는 `TMI-113`, 독립된 PLAN-004 Jira는 `TMI-115`다.
- 작업 목표: 앞서 승인된 Billing AttemptGroup 상태 event consumer 방향을 구현 가능한 상세 계획서로 작성한다.
- 변경 파일: Billing `docs/plans/PLAN-005-attempt-group-status-event-consumer.md`와 Billing·Learning Core의 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·ADR·계약·Jira·AWS는 변경하지 않았다.
- 계획 내용: strict schema v1·16 KiB, canonical digest, shared inbox, group/session/owner fencing, 상태 전이와 terminal 보호, Mongo Transaction·version CAS, default-off route/security, 오류·retry·privacy metric과 replica-set 동시성 테스트를 단계별로 고정했다.
- 세부 결정: terminal evidence는 `OPEN`에서도 직접 전진, stale/terminal 역행은 204, missing projection은 retryable 503, 구조적 target conflict는 non-retryable 409로 처리한다. RETAKE failureCode는 저 cardinality 네 값만 초안 allowlist로 둔다.
- 유지한 계약: 기존 공개 API·DTO·`BaseResponse`, 실제 userId 비전달, 시험 S3·Redis·Python AI Callback과 `user_id=examId`, Billing Reservation·Claim·grant·ledger 계약을 변경하지 않았다.
- 테스트: 문서 계획 작업이라 Gradle 테스트는 실행하지 않았다. Billing 실제 entity/repository/inbox/security/index와 ADR·통합 계약을 읽기 전용으로 대조했고 종료 전에 두 저장소 `git diff --check`, 계획서 구조와 marker 단일 포함을 검증한다.
- 위험 요소: sequence 없는 상충 terminal event는 먼저 commit된 terminal이 승리하므로 Learning Core publisher contract test가 필요하다. shared inbox 일반화는 기존 Identity revision dedupe 전체 회귀를 반드시 통과해야 한다.
- 다음 작업: 사용자가 PLAN-005를 승인하면 Jira 생성을 별도로 승인받고, 구현 전 Phase 0 계약 보정 후 Step 1부터 Billing 코드 구현을 시작한다. Git commit과 push는 사용자가 수행한다.

## 2026-08-28 — PLAN-005 계획서 종료 기록 동기화

<!-- codex-turn:01a0485b-8a41-7943-9e78-492ec39a17ac -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira는 생성하지 않았고 상태도 변경하지 않았다. 관련 기존 키는 Billing `TMI-113`, 독립 계획 `TMI-115`다.
- 종료 결과: Billing `docs/plans/PLAN-005-attempt-group-status-event-consumer.md`에 AttemptGroup 상태 event consumer의 wire, 멱등성, owner resolution, 상태 전이, Transaction·CAS, security, 오류·retry, 테스트와 production gate를 작성했다.
- 정합성 보정: `AttemptGroup.subjectRefId`와 실제 `userId`를 직접 비교하지 않고 active·unexpired `BillingSubjectLink`로 owner를 해석하도록 계획했다. retention 뒤 mapping은 복원하지 않고 stale 처리한다.
- 변경 범위: Billing 계획서와 Billing·Learning Core의 CURRENT_STATE/WORKLOG 문서만 변경했다. 애플리케이션·ADR·외부 계약·Jira·AWS·Git commit/push는 변경하지 않았다.
- 유지한 계약: 기존 공개 API·DTO·`BaseResponse`, 시험 S3·Redis·Python AI Callback의 `user_id=examId`, Billing Reservation·Claim·grant·ledger 계약을 유지했다. Secret과 Token을 기록하지 않았다.
- 테스트 결과: 문서 작업이라 Gradle 테스트는 실행하지 않았다. 두 저장소의 `git diff --check`, Markdown code fence 짝과 이번 marker 단일 포함을 검증한다.
- 다음 작업: 사용자 승인 후 PLAN-005 Jira 생성을 별도 승인받고 Phase 0 계약 보정부터 구현한다.

## 2026-08-28 — Billing 대상 오해 정정·Learning Core Reservation saga 계획서 작성

<!-- codex-turn:01a0485f-e8cf-7ae3-8327-e72920c5d9a2 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira는 생성하지 않았고 기존 상태도 변경하지 않았다. Billing 선행 완료는 `TMI-110`·`TMI-112`·`TMI-113`, 별도 BenefitDefinition 계획은 `TMI-115`다.
- 작업 목표: 사용자의 정정에 따라 수정 대상을 Billing consumer가 아닌 Learning Core로 바로잡고, AttemptGroup publisher의 선행 조건인 필수 Idempotency-Key와 Billing Reservation 시험 생성 saga 계획을 작성한다.
- 변경 파일: `docs/codex/BILLING_RESERVATION_SAGA_IMPLEMENTATION_PLAN.md`, Learning Core `docs/codex/CURRENT_STATE.md`·`WORKLOG.md`; 잘못 만든 Billing PLAN-005 파일은 삭제하고 Billing CURRENT_STATE/WORKLOG에 철회 이력만 남겼다.
- 계획 내용: 공개 `POST /api/v1/exams`의 flag 기반 필수 lowercase UUID v4 header, `ExamCreationOperation`, 고정 sessionId/mockExamId, reserve→Session commit→confirm, cancel/status 복구, same-operation replay, ExamSession의 reservation/attemptGroup mapping, SigV4 client와 Mongo Transaction/index·테스트를 단계별로 고정했다.
- 핵심 순서: 현재 ExamSession에는 `attemptGroupId`가 없어 상태 outbox를 먼저 만들 수 없다. 따라서 Reservation saga로 durable mapping을 만든 뒤 `GRADING`·`COMPLETED`·`RETAKE_AVAILABLE` outbox/publisher를 구현한다.
- 유지한 계약: Request Body 없음, 기존 성공 DTO와 `BaseResponse`, 실제 userId 비노출, retryCount·S3·Redis·AI request/Callback과 `user_id=examId`를 유지한다. Billing 활성 flag 기본값은 off이며 구버전 앱 rollout 전에 header를 강제하지 않는다.
- 애플리케이션 동작: 이번 턴에는 계획서만 작성했고 Java/config/migration·실제 API·Billing 애플리케이션·Jira·AWS·배포·Git commit/push를 변경하지 않았다. Secret과 Token을 기록하지 않았다.
- 테스트 결과: 문서 작업이라 Gradle 테스트는 실행하지 않았다. 실제 Learning Core Controller/SessionManager/ExamSession/index와 Billing reserve/confirm/cancel/status DTO·SigV4 ADR을 읽기 전용으로 대조했고 종료 전에 두 저장소 `git diff --check`, code fence와 marker 단일 포함을 검증한다.
- 위험 요소: public header 강제는 프론트 선배포가 필요하고, confirm 응답 유실을 실패로 단정하면 Session/consumption이 갈라진다. 외부 호출을 Mongo Transaction에 넣지 않고 영속 operation과 status recovery로 수렴해야 한다.
- 다음 작업: 사용자가 새 Learning Core 계획을 승인하면 Jira 생성을 별도 승인받고 AGENTS의 명시적 Billing saga 범위를 추가한 뒤 Phase 0부터 구현한다.

## 2026-08-28 — 수정 대상 Learning Core 재확인

<!-- codex-turn:01a04865-5bb4-7702-94ae-a420f9f2b1a2 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 이슈를 생성하거나 기존 상태를 변경하지 않았다.
- 사용자 정정에 따라 구현 대상이 Billing 애플리케이션이 아니라 Learning Core임을 다시 확인했다. Billing은 이미 확정된 Reservation 계약을 제공하는 외부 의존 서비스로만 취급한다.
- 현재 활성 계획서는 Learning Core의 `docs/codex/BILLING_RESERVATION_SAGA_IMPLEMENTATION_PLAN.md`다. `POST /api/v1/exams`의 `Idempotency-Key`, `ExamCreationOperation`, Billing reserve/confirm/cancel/status client, `ExamSession` 연결 정보와 same-operation replay를 Learning Core에 구현한다.
- Billing AttemptGroup consumer, Billing 애플리케이션·계약, Jira, AWS와 Java 런타임 코드는 변경하지 않았다.
- 기존 공개 API 성공 DTO·`BaseResponse`, 실제 userId 비노출, 시험 `retryCount`, S3·Redis와 Python AI `user_id=examId` 계약을 유지한다.
- 테스트: 범위 확인과 문서 검증만 수행했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`를 실행했다.
- 다음 작업 전 확인 사항: 계획 승인 후 Learning Core AGENTS 예외 범위를 명시하고 Phase 0 계약·설정부터 구현한다. Git commit과 push는 사용자가 수행한다.

## 2026-08-28 — Billing 후속 변경 필요성·구현 순서 구분

<!-- codex-turn:01a04869-billing-scope-order -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 이슈를 생성하거나 기존 상태를 변경하지 않았다.
- Billing에는 `TMI-112`·`TMI-113`의 TrialClaim, 무료 grant/ledger와 Reservation reserve/confirm/cancel/status·expiry 기반이 이미 있으므로 Learning Core 시험 생성 saga를 위해 동일 기능을 Billing에 다시 구현할 필요는 없다.
- 현재 우선 작업은 Learning Core의 공개 시험 생성 `Idempotency-Key`, Billing client, reserve→Session commit→confirm saga, same-operation replay와 `ExamSession.attemptGroupId` mapping이다.
- 후속 전체 연동에는 Learning Core의 AttemptGroup 상태 outbox/publisher와 이에 대응하는 Billing event consumer가 모두 필요하다. Billing owner rebind/UserMerged 정합성과 Billing 측 repair·reconciliation도 별도 Billing 후속 범위다.
- 따라서 앞서 작성했다가 철회한 Billing consumer 계획의 기능 자체가 불필요한 것은 아니지만, 사용자가 요청한 현재 Learning Core 계획 대신 다음 작업으로 선정한 것은 대상과 순서가 잘못된 것이었다.
- 애플리케이션·외부 계약·Jira·AWS를 변경하지 않았다. 문서 분석 작업이라 Gradle 테스트는 실행하지 않았고 `git diff --check`로 형식을 확인한다.
- 다음 작업 전 확인 사항: Learning Core saga를 먼저 구현한 뒤 durable `attemptGroupId` mapping과 상태 event 계약을 기준으로 Learning Core publisher와 Billing consumer를 각각의 저장소에서 후속 구현한다.

## 2026-08-28 — Billing 필요 범위 최종 확인

<!-- codex-turn:01a0486c-b3c8-7312-8be3-71a67a4e9658 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 완료 근거는 Billing `TMI-112`·`TMI-113`이며 신규 Jira 생성이나 상태 변경은 수행하지 않았다.
- 이번 확인의 결론은 Billing Reservation reserve/confirm/cancel/status 기반은 이미 구현돼 있어 현재 Learning Core 시험 생성 saga를 위해 Billing에 동일 기능을 다시 추가할 필요가 없다는 것이다.
- 현재 우선 구현 대상은 Learning Core의 `Idempotency-Key`, Billing client, reserve→Session commit→confirm saga, same-operation replay와 `ExamSession.attemptGroupId` durable mapping이다.
- Learning Core AttemptGroup 상태 outbox/publisher가 준비된 뒤에는 이를 수신하는 Billing 상태 event consumer가 별도로 필요하다. Billing owner rebind와 Billing 측 reconciliation도 후속 Billing 범위다.
- 따라서 앞선 Billing consumer 선택은 기능 자체가 불필요했던 것이 아니라 현재 Learning Core 작업 대신 먼저 선정한 대상과 순서가 잘못된 것이었다.
- 변경 파일: 분석 기록을 위해 Learning Core `docs/codex/WORKLOG.md`와 `docs/codex/CURRENT_STATE.md`만 갱신했다. 애플리케이션·테스트·외부 API·Billing 저장소·AWS·Git·Jira는 변경하지 않았다.
- 유지한 계약: 기존 공개 API·DTO·`BaseResponse`, 실제 userId 비노출, 시험 `retryCount`, S3·Redis와 Python AI `user_id=examId` 계약을 유지했다. Secret과 Token을 기록하지 않았다.
- 테스트 결과: 문서 분석 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 문서 형식을 검증한다.
- 다음 작업: Learning Core saga를 먼저 구현하고, 그 후 Learning Core publisher와 Billing consumer를 각 저장소의 별도 작업으로 진행한다.

## 2026-08-28 — Learning Core 시험 생성 saga 계획 설명

<!-- codex-turn:01a04875-5454-7533-b77c-f68ca8c00189 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 Billing 선행 완료 근거는 `TMI-112`·`TMI-113`이며 신규 Jira 생성이나 상태 변경은 수행하지 않았다.
- 사용자 요청에 따라 `docs/codex/BILLING_RESERVATION_SAGA_IMPLEMENTATION_PLAN.md`의 목적과 동작을 설명했다. 이 계획은 Billing 기능을 새로 만드는 것이 아니라 Learning Core의 `POST /api/v1/exams`를 기존 Billing Reservation API와 연결하는 작업이다.
- 핵심 목적은 앱의 한 번의 시험 시작 동작을 `Idempotency-Key`로 고정해 중복 클릭·transport retry·응답 유실·process crash에서도 같은 `examId`와 operation으로 수렴시키고 Session 및 무료 사용권의 이중 생성·이중 소비를 막는 것이다.
- 정상 흐름은 Learning Core operation 준비→Billing reserve→기존 Session 교체와 새 confirming Session의 Mongo Transaction commit→Billing confirm→Session `IN_PROGRESS` 전환과 기존 성공 DTO 반환이다.
- 실패 흐름은 reserve 전 기존 Session 보존, local commit 실패 시 cancel/expiry, confirm 응답 불명 시 status 조회와 같은 key replay로 복구한다. 외부 HTTP는 Mongo Transaction 안에서 실행하지 않는다.
- `ExamSession`에는 operation/reservation/attemptGroup mapping을 내부 저장하지만 공개 성공 DTO, `BaseResponse`, 실제 userId 비노출, retryCount, S3·Redis와 Python AI `user_id=examId` 계약은 유지한다.
- feature flag 기본값은 off이며 프론트의 header 선배포와 staging 검증 전에는 기존 무헤더 생성 흐름을 유지한다. AttemptGroup outbox/publisher, Billing consumer, UserMerged·owner rebind와 background reconciliation은 후속 범위다.
- 변경 파일: 설명 기록을 위해 Learning Core `docs/codex/WORKLOG.md`와 `docs/codex/CURRENT_STATE.md`만 갱신했다. 애플리케이션·테스트·Billing 저장소·외부 API·AWS·Git·Jira는 변경하지 않았다.
- 테스트 결과: 문서 설명 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 문서 형식을 검증한다.
- 다음 작업 전 확인 사항: 사용자가 계획을 승인하면 AGENTS의 명시적 saga 예외와 프론트 header 계약을 먼저 정렬한 뒤 operation/header 기반부터 단계적으로 구현한다.

## 2026-08-28 — TMI-116 Learning Core 시험 생성 saga Jira 생성

<!-- codex-turn:01a04885-643c-7e92-b8fd-81a5b7fe80b0 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: `TMI-116` `[Learning Core] Billing Reservation 시험 생성 saga 구현`을 작업 유형으로 생성했고 재조회 결과 상태는 `해야 할 일`, 담당자는 미지정이다.
- 중복 검색에서 동일한 Learning Core saga 이슈는 없었고, 선행 Billing `TMI-112`·`TMI-113`만 완료 상태로 확인했다.
- Jira 설명에 목표, reserve→Session commit→confirm 정상 흐름, 필수 `Idempotency-Key`, operation·Session 내부 모델, INITIAL/REPLACEMENT, 실패·응답 유실 복구, SigV4/Lattice, feature flag, 공개 오류, 완료 조건과 production 활성화 gate를 기록했다.
- Billing Reservation 재구현, AttemptGroup publisher/consumer, UserMerged·owner rebind, background reconciliation과 실제 AWS 배포는 이슈 범위에서 제외했다.
- 계획서 `docs/codex/BILLING_RESERVATION_SAGA_IMPLEMENTATION_PLAN.md`의 상태를 `계획 승인·Jira 생성, 구현 대기`로 바꾸고 Jira를 `TMI-116`으로 기록했다.
- 변경 파일: 계획서와 Learning Core `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. 애플리케이션·테스트·Billing 저장소·AWS·Git은 변경하지 않았다.
- 유지한 계약: 기존 공개 API 성공 DTO·`BaseResponse`, 실제 userId 비노출, retryCount, S3·Redis와 Python AI `user_id=examId` 계약을 유지했다. Secret과 Token을 기록하지 않았다.
- 테스트 결과: Jira·문서 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 문서 형식을 검증한다.
- 다음 작업 전 확인 사항: `TMI-116` 구현 시작 전에 AGENTS의 명시적 Billing saga 허용 범위와 프론트 `Idempotency-Key` rollout 계약을 반영한다.

## 2026-08-29 — TMI-116 Billing Reservation 시험 생성 saga 구현

<!-- codex-turn:01a0488c-588f-7f60-9907-6e6859ff596b -->

- 날짜: 2026-08-29
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116` `[Learning Core] Billing Reservation 시험 생성 saga 구현`; 구현과 로컬 검증은 완료했지만 Jira 상태는 변경하지 않았다.
- 작업 목표: 기존 Billing `TMI-112`·`TMI-113` Reservation API를 Learning Core `POST /api/v1/exams`에 연결해 사용자 시작 요청, transport retry, confirm 응답 유실과 process crash가 하나의 operation·Session·사용권 소비로 수렴하게 한다.
- 변경 파일: 시험 생성 controller/service·Session manager, `ExamSession`·상태·repository, 신규 `ExamCreationOperation`과 repository, `BillingExamCreationSaga`·Mongo Transaction service·Idempotency-Key parser, `domain/exams/billing` 설정·SigV4 client·startup validator/probe, 오류 mapping, `application.yml`·`.env.example`, Mongo migration script, 프론트 인계서·구현 계획서·AGENTS와 관련 단위·MVC·통합 테스트를 변경·추가했다.
- 변경 동작: feature flag on에서 lowercase UUID v4 `Idempotency-Key`를 필수화하고 operation 준비→Billing reserve→기존 Session abandon 및 confirming Session Transaction commit→Billing confirm→`IN_PROGRESS` finalize를 구현했다. reserve 실패는 기존 Session을 보존하고, local commit 실패는 cancel/expiry, confirm 응답 불명은 status와 same-key replay로 복구한다.
- 멱등성과 동시성: command에는 fixed session/mock exam과 reservation/group mapping을 저장한다. active operation unique guard와 optimistic version을 사용하고, command TTL 뒤에는 사용자 범위 `(userId, creationOperationId)` Session unique mapping으로 replay한다. same-key concurrent commit의 optimistic/duplicate 충돌에서는 공유 Billing reservation을 취소하지 않고 processing retry를 반환한다.
- Billing 계약: AWS default credential provider를 공유해 SigV4 `vpc-lattice-svcs`/`ap-northeast-2`로 서명하고 redirect를 따르지 않는다. strict JSON, response 16 KiB 상한, timeout·오류·`Retry-After` mapping을 적용했으며 `sessionCommittedAt`은 Billing decoder가 요구하는 UTC 밀리초 3자리로 고정했다. 실제 credential, user payload와 authorization header를 로그·문서에 기록하지 않았다.
- 유지한 외부 계약: 기존 URL·HTTP Method·Request Body 없음, 성공 `CreateSessionResult`와 `BaseResponse`를 유지했다. 실제 userId·reservationId·attemptGroupId는 외부에 노출하지 않았고, 기존 retryCount·S3 Object Key·Redis Key·submit/polling과 Python AI `user_id=examId`·Callback JSON을 변경하지 않았다. flag off에서는 기존 무헤더 Session 생성 흐름을 유지한다.
- 테스트: saga·transaction·Idempotency-Key·SigV4·configuration/index/transaction probe와 기존 service/MVC/security 회귀를 검증했다. 최종 `./gradlew clean test` 결과는 424 tests, failures 0, errors 0, skipped 0이다. `node --check scripts/mongodb/tmi-116-migrate-billing-exam-saga.js`와 `git diff --check`도 성공했다.
- 남은 위험 요소: Mock 기반 Transaction 검증만 완료했으므로 실제 Mongo replica-set의 index migration·동시 transaction/failure injection이 필요하다. Lattice route, ECS task role/IAM/SG, Billing expiry worker와 INITIAL/R3 REPLACEMENT response-loss E2E는 아직 검증하지 않았다. background reconciliation과 AttemptGroup outbox/publisher는 의도적으로 후속 범위다.
- 다음 작업 전 확인 사항: 프론트가 매 시작 operation에 UUID v4 key를 생성하고 transport retry에서 재사용하도록 먼저 배포한다. 이후 staging DB migration dry-run/apply, Lattice 최소 권한·경로, reserve 실패·commit rollback·confirm timeout/status와 INITIAL/REPLACEMENT E2E를 통과한 뒤에만 `BILLING_CREATION_SAGA_ENABLED=true`로 활성화한다. Git commit과 push는 사용자가 수행한다.

## 2026-08-29 — TMI-116 코드 증가량·구현 범위 설명

<!-- codex-turn:01a04909-d7c1-73a0-8fe5-4d75e29695f7 -->

- 날짜: 2026-08-29
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116`; 상태는 변경하지 않았다.
- 작업 목표: 사용자가 우려한 코드 증가량이 어떤 기능에서 발생했는지 이번 구현과 기존 작업 트리 변경을 구분해 설명한다.
- 분석 결과: 중심 구현은 saga orchestration 511줄, Mongo Transaction service 186줄, operation entity 149줄, Idempotency-Key parser와 기존 시험 생성 연결부다. 큰 부가 영역은 SigV4/strict Billing client 387줄, configuration·index·transaction startup 검증, 163줄 migration과 실패·동시성·계약 테스트다.
- 변경량이 큰 이유: 정상 reserve→commit→confirm만 구현한 것이 아니라 reserve 실패, local rollback/cancel, confirm response-loss/status reconciliation, same-key replay, concurrent commit, command TTL 뒤 replay와 운영 fail-closed까지 포함했다.
- 작업 트리 구분: 10초 챌린지 계약, 비용 산정과 진행 체크 문서는 이전 작업에서 존재하던 별도 변경이며 TMI-116 런타임 구현량으로 합산하면 안 된다.
- 변경 파일·동작: 이번 설명 턴에는 Java·설정·migration·외부 계약을 추가 수정하지 않았고 기록 문서만 갱신했다. 기존 공개 API·BaseResponse·AI/S3/Redis 계약도 변경하지 않았다.
- 테스트: 코드 변경이 없는 설명 작업이므로 Gradle 테스트를 다시 실행하지 않았다. 직전 최종 결과는 424 tests, failures/errors/skipped 0이다.
- 남은 판단: 현 구조는 운영 안전성을 우선한 완성형 범위다. MVP 단순화가 필요하면 startup validator/probe와 migration을 제거하기보다 orchestration/client 내부를 작은 class로 분리해 가독성을 개선하는 편이 안전하다.

## 2026-08-29 — TMI-116 코드 증가량 설명 종료 기록 동기화

<!-- codex-turn:01a04909-6d3b-7e02-8539-e40daa27fbc6 -->

- 날짜: 2026-08-29
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116`; 상태는 변경하지 않았다.
- 종료 훅 요청에 따라 이번 설명 작업의 WORKLOG와 CURRENT_STATE 기록을 동기화했다.
- 설명 결론: 큰 변경량은 정상 Billing 호출 자체보다 영속 operation, 실패·응답 유실·동시 요청 복구, Mongo Transaction, SigV4 strict client, startup index/transaction 검증과 테스트에서 발생했다.
- 작업 트리의 10초 챌린지·비용 관련 문서는 이전 작업에서 존재한 별도 변경이며 이번 saga 운영 코드와 구분한다.
- 이번 동기화에서는 애플리케이션·테스트·외부 API·Jira·AWS·Git commit/push를 변경하지 않았고 Secret과 Token을 기록하지 않았다.
- 코드 변경이 없는 기록 작업이므로 Gradle 테스트는 다시 실행하지 않았다. 직전 최종 검증은 424 tests, failures/errors/skipped 0이다.

## 2026-08-29 — TMI-116 구현 구조 상세 설명

<!-- codex-turn:01a0490b-53e2-7ab2-b662-42f6dbfb7b0c -->

- 날짜: 2026-08-29
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116`; 상태는 변경하지 않았다.
- 작업 목표: 사용자의 추가 요청에 따라 코드 증가 원인을 정상 요청 흐름, 영속 상태, 실패 복구, SigV4 client, Mongo 운영 보호와 테스트로 나누어 상세히 설명한다.
- 핵심 구조: Billing과 Learning Core MongoDB 사이에는 하나의 분산 Transaction을 걸 수 없으므로 `ExamCreationOperation` 상태를 복구 지점으로 삼는다. `PREPARED → RESERVED → SESSION_COMMITTED → SUCCEEDED` 정상 전이와 cancel/expiry/terminal 전이를 저장해 process crash와 same-key replay를 이어서 처리한다.
- 정상 흐름: 고정 session/mock exam 준비, Billing 사용권 hold, Mongo Transaction에서 기존 active Session abandon과 새 confirming Session insert, Billing confirm, 두 번째 Mongo Transaction의 Session `IN_PROGRESS` finalize 순서다.
- 실패 복구: reserve 실패는 기존 Session을 보존하고, local commit 실패는 cancel 또는 `CANCEL_PENDING`, confirm 응답 유실은 status 조회로 confirmed/reserved/canceled/expired에 수렴한다. same-key 동시 commit 충돌에서는 공유 reservation을 취소하지 않는다.
- 운영 보호: SigV4 Lattice 서명, strict JSON·16 KiB 상한·timeout·redirect 금지, UTC 밀리초 timestamp, 외부 UUID 검증, Mongo unique/TTL index migration, staging/prod startup index와 Transaction capability 검증을 포함한다.
- 범위 구분: AttemptGroup 상태 outbox/publisher, Billing consumer, background reconciliation scheduler와 실제 AWS/Lattice/IAM/SG·운영 DB 적용은 이번 구현에 포함하지 않았다.
- 변경 파일·테스트: 이번 상세 설명 턴에는 애플리케이션·외부 계약을 추가 수정하지 않고 기록 문서만 갱신했다. 코드 변경이 없어 Gradle 테스트를 재실행하지 않았으며 직전 최종 결과는 424 tests, failures/errors/skipped 0이다.

## 2026-08-29 — TMI-116 상세 설명 종료 기록 동기화

<!-- codex-turn:01a0490b-53e2-7dc2-b1b4-6de27b041a50 -->

- 날짜: 2026-08-29
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116`; 상태는 변경하지 않았다.
- 종료 훅 요청에 따라 이번 상세 설명 작업의 WORKLOG와 CURRENT_STATE를 다시 동기화했다.
- 설명 범위는 기존 시험 생성의 문제, 정상 reserve→Mongo commit→confirm 흐름, `ExamCreationOperation` 상태, 장애·동시성 복구, SigV4 strict client, Mongo index/startup gate와 코드량 구분이다.
- 이번 종료 기록에서는 Java·설정·migration·테스트·외부 API·Jira·AWS·Git commit/push를 변경하지 않았으며 Secret과 Token을 기록하지 않았다.
- 코드 변경이 없는 기록 작업이므로 Gradle 테스트를 다시 실행하지 않았다. 직전 최종 검증은 424 tests, failures/errors/skipped 0이다.

## 2026-08-31 — TMI-116 Saga·SigV4 Billing client 책임 설명

<!-- codex-turn:01a05577-534f-7f52-97c8-a164ab1ff531 -->

- 날짜: 2026-08-31
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116`; 상태는 변경하지 않았다.
- 작업 목표: `BillingExamCreationSaga`와 `SigV4BillingReservationClient`가 각각 무엇을 담당하고 어떻게 연결되는지 설명한다.
- 결론: Saga는 영속 operation 상태를 기반으로 시험 생성의 reserve→Session commit→confirm 순서와 cancel/status 장애 복구를 결정하는 application orchestration이다. SigV4 client는 reserve·confirm·cancel·status 요청을 Billing wire 계약으로 보내고 AWS 서명·timeout·strict decode·오류 변환을 처리하는 infrastructure adapter다.
- 책임 경계: Saga는 `BillingReservationClient` interface에만 의존해 SigV4와 HTTP 세부사항을 모르며, client는 ExamSession 저장·상태 전이·재시도 정책을 결정하지 않는다.
- 변경 파일·테스트: 이번 설명 작업에서는 Java·설정·migration·외부 API를 수정하지 않고 기록 문서만 갱신했다. 코드 변경이 없어 Gradle 테스트를 다시 실행하지 않았으며 직전 최종 결과는 424 tests, failures/errors/skipped 0이다.
- Secret과 Token을 기록하지 않았고 AWS·Git commit/push도 변경하지 않았다.

## 2026-08-31 — Billing VPC Lattice AWS_IAM 선택 근거 재확인

<!-- codex-turn:01a0557a-8851-7003-aa5f-f7c2e463e7af -->

- 날짜: 2026-08-31
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116`; 상태는 변경하지 않았다.
- 작업 목표: Billing 내부 호출에 VPC Lattice `AWS_IAM`과 ECS task role SigV4를 선택했던 이유를 Billing 통합 계약, 계약 결정서와 ADR-002에서 재확인한다.
- 선택 근거: Billing은 앱이 직접 호출하지 않는 내부 서비스이고 caller가 이미 ECS에 있어, 별도 workload 인증 시스템 대신 자동 회전되는 task role 임시 credential을 서비스 principal로 사용할 수 있다. Lattice policy는 principal·Method·Path를 함께 검사해 Identity/Learning Core 및 production/staging 권한을 최소 범위로 분리한다.
- 네트워크 경계: Billing을 ALB 없이 Lattice target으로 연결하고 SG에서 Lattice 경로 외 task 직접 접근을 차단해, 서명 검증을 우회하는 내부 경로를 남기지 않는 것이 계약의 필수 조건이다.
- 대안 비교: 플랫폼 JWT는 ECS가 issuer/JWKS를 자동 제공하지 않고, Identity workload JWT는 client-credentials·Secret rotation·token cache를 새로 구현해야 하며, mTLS는 인증서 운영이 복잡하고 shared API key/HMAC은 정적 Secret과 세밀한 route 권한에 불리해 채택하지 않았다.
- tradeoff: Lattice 비용과 AWS 종속성, IAM/auth policy·SG·SigV4 client·local fake adapter 운영 복잡도가 추가된다. 따라서 실제 Lattice/IAM/SG 연결과 wrong-role·unsigned·direct-bypass negative test가 production gate다.
- 변경 파일·테스트: 이번 분석은 문서 근거 재확인과 기록 갱신만 수행했으며 Java·외부 계약·AWS·Git commit/push를 변경하지 않았다. 코드 변경이 없어 Gradle 테스트를 다시 실행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-08-31 — TMI-116 이후 다음 작업 설명

- 날짜: 2026-08-31
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: 기존 `TMI-116`은 상태 변경하지 않았고 신규 Jira는 생성하지 않았다.
- 작업 목표: Billing Reservation saga 다음에 진행할 애플리케이션 작업과 운영 활성화 작업을 구분해 설명한다.
- 확인 결과: Learning Core의 AttemptGroup outbox/publisher와 Billing `POST /internal/v1/attempt-group-events` consumer가 모두 없다. Saga가 저장한 `ExamSession.attemptGroupId`를 사용해 `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE` 상태를 Billing consumption projection으로 전달하는 것이 다음 vertical slice다.
- 권장 순서: event schema·전이 동결, Billing inbox consumer 선배포, Learning Core local state와 같은 Transaction에서 outbox 기록, lease 기반 publisher, migration/index, SigV4 staging E2E, consumer→publisher 순 활성화다.
- TMI-116 운영 gate: 프론트 UUID v4 key 선배포, replica-set index migration, Billing expiry worker, Lattice/IAM/SG, reserve/commit/confirm failure injection과 INITIAL/REPLACEMENT E2E를 병행한다.
- 변경 파일·테스트: 이번 설명에서는 Java·외부 계약·Billing 저장소·AWS를 수정하지 않고 기록 문서만 갱신했다. 코드 변경이 없어 Gradle 테스트를 다시 실행하지 않았으며 Secret과 Token을 기록하지 않았다.

## 2026-08-31 — TMI-116 이후 다음 작업 설명 종료 기록

<!-- codex-turn:01a0557d-9caf-7fa1-a849-af29908846f7 -->

- 날짜: 2026-08-31
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: 기존 `TMI-116`은 상태 변경하지 않았고 신규 Jira는 생성하지 않았다.
- 종료 훅 요청에 따라 AttemptGroup 상태 연동을 다음 작업으로 확인한 이번 턴의 WORKLOG와 CURRENT_STATE를 동기화했다.
- 다음 vertical slice는 Billing `POST /internal/v1/attempt-group-events` consumer와 Learning Core의 `GRADING`·`COMPLETED`·`RETAKE_AVAILABLE` durable outbox/publisher다. 안전한 배포는 consumer 선배포 후 publisher 활성화 순서다.
- TMI-116의 프론트 key, Mongo migration, Lattice/IAM/SG와 staging failure-injection E2E는 별도 운영 gate로 병행한다.
- 이번 종료 기록에서는 Java·외부 계약·Billing 저장소·AWS·Git commit/push를 변경하지 않았으며 Secret과 Token을 기록하지 않았다. 코드 변경이 없어 Gradle 테스트를 재실행하지 않았다.

## 2026-08-31 — TMI-116 독립 리뷰 P1/P2 검증

<!-- codex-turn:01a055a1-20a0-7e91-b3a7-2e38fb9c56f0 -->

- 날짜: 2026-08-31
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116`; 상태는 변경하지 않았다.
- 작업 목표: 사용자가 전달한 confirm 영구 정체, concurrent reservation 오취소, Billing 성공 응답 strict 검증 및 PR 범위 지적을 현재 코드와 Billing ADR에 대조한다.
- Finding 1: 유효한 P1이다. `start()`의 Session-first durable replay가 confirming Session에서 즉시 processing을 반환해 `SESSION_COMMITTED` operation의 confirm/status 복구 루프를 영구 차단한다. operation-first drive와 command purge 이후 Session fallback이 필요하다.
- Finding 2: 유효한 P1이다. 명시된 두 예외 외 Mongo transient/unknown Transaction 결과는 generic Runtime 경로에서 단 한 번 re-read 후 아직 `RESERVED`이면 cancel된다. 같은 key concurrent commit이 아직 가시화되지 않은 reservation을 취소할 수 있으므로 unknown 결과에는 cancel을 금지하고 operation/Session 재조회와 same-key retry로 수렴해야 한다.
- Finding 3: 유효한 P2다. Jackson mapper는 scalar coercion·enum ordinal과 missing/null creator property를 완전히 거절하지 않으며 confirm semantic validation에 `attemptGroupStatus=OPEN`과 필수 confirmed timestamp가 빠져 있다. status/cancel의 조건부 timestamp를 포함한 endpoint별 검증과 malformed fixture 테스트가 필요하다.
- PR hygiene: 비용 추정·10초 챌린지 문서는 TMI-116과 무관하다. frontend handoff의 Idempotency-Key 변경은 관련 있지만 파일 전체 포함은 의도를 확인하고, 사용자 변경을 보존한 채 selective staging 또는 별도 commit으로 분리해야 한다.
- 변경 파일·테스트: 이번 요청은 진단이므로 Java·테스트·외부 계약을 수정하지 않고 기록 문서만 갱신했다. 코드 변경이 없어 Gradle 테스트를 실행하지 않았고 AWS·Git commit/push·Secret·Token을 변경하거나 기록하지 않았다.
- 다음 작업: 사용자가 수정을 요청하면 세 finding의 regression test를 먼저 추가하고 operation-first recovery, unknown commit no-cancel, strict response validation을 구현한 뒤 전체 `./gradlew clean test`를 실행한다.

## 2026-08-31 — ENTITLEMENT_CONFIRMING·SESSION_COMMITTED 관계 설명

<!-- codex-turn:01a055bb-5235-7f21-a5a3-57f85cb2aa67 -->

- 날짜: 2026-08-31
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116`; 상태는 변경하지 않았다.
- 작업 목표: `ENTITLEMENT_CONFIRMING`이 충돌 상태인지와 동시에 operation이 `SESSION_COMMITTED`가 될 수 있는지 설명한다.
- 결론: `ENTITLEMENT_CONFIRMING`은 충돌이 아니라 local Session commit 완료·Billing confirm 미확정 상태다. `commitReservedSession()`의 같은 Mongo Transaction에서 Session insert와 operation `SESSION_COMMITTED` 저장을 수행하므로 정상적인 중간 pair다.
- 정상 전이: `RESERVED + Session 없음`에서 Transaction 성공 후 `SESSION_COMMITTED + ENTITLEMENT_CONFIRMING`, Billing confirm/finalize 성공 후 `SUCCEEDED + IN_PROGRESS/CONFIRMED`가 된다. confirm/status가 실패하면 중간 pair가 남아 same-key recovery 대상이 된다.
- 불변식: Transaction rollback이면 새 Session insert와 operation 전이가 함께 rollback돼야 한다. 한쪽만 존재하면 Transaction 미지원·unknown outcome·수동 데이터 변경 등 비정상 정합성 문제로 취급해야 한다.
- 변경 파일·테스트: 이번 설명에서는 Java·테스트·외부 계약을 수정하지 않고 기록 문서만 갱신했다. 코드 변경이 없어 Gradle 테스트를 실행하지 않았고 AWS·Git commit/push·Secret·Token을 변경하거나 기록하지 않았다.

## 2026-08-31 — 앱 문제 응답의 Part 4 표 처리 현황 분석

<!-- codex-turn:01a055ae-7860-77d2-adec-ea270e5008bd -->

- 날짜: 2026-08-31
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: 이번 분석의 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 문제 생성 응답과 `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`, 문항 결과 응답에서 Part 4 표는 MongoDB `table_context`를 비정형 `Map<String,Object>`로 읽어 `tableContext` JSON 객체로 그대로 전달한다.
- 서버는 table_context를 title/items 같은 고정 schema로 변환하거나 HTML·Markdown으로 렌더링하지 않는다. 앱이 전달된 중첩 object·array 구조를 해석해 표를 표시해야 한다.
- Mongo `table_image_url` 필드는 내부 entity에 남아 있지만 공개 QuestionDTO에 필드가 없어 앱 응답에서 제외된다. Part 4는 tableContext가 null이면 `EXAM_CATALOG_CONFIGURATION_ERROR`로 실패하고 빈 object는 허용한다.
- 일반 `imageUrl`은 기본 QuestionDTO mapping에 유지되지만 Part 4 문항 결과의 축약 questionInfo에는 text와 tableContext만 포함된다. AI 채점 multipart에도 table_image_url과 table_context를 보내지 않는다.
- 분석과 기록만 수행했으며 애플리케이션·테스트·AWS·Mongo 데이터·Jira·외부 API 계약을 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-31 — TMI-116 P1/P2 리뷰 finding 구현

<!-- codex-turn:01a055bd-c248-7960-a926-28646e1d99ea -->

- 날짜: 2026-08-31
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116`; 이슈 상태는 변경하지 않았다.
- 작업 목표: confirm 복구 영구 정체 P1, Mongo unknown commit 시 shared reservation 오취소 P1, Billing 성공 응답 strict 검증 부족 P2를 구현한다.
- 변경 파일: `BillingExamCreationSaga.java`, `BillingSagaConfiguration.java`, `SigV4BillingReservationClient.java`, 대응 Saga·SigV4 client 테스트와 현재 기록 문서를 수정했다.
- operation-first 복구: 동일 key operation을 먼저 조회·drive하고 operation이 없을 때만 Session durable replay를 사용한다. 이로써 `ENTITLEMENT_CONFIRMING + SESSION_COMMITTED` 상태의 재요청이 processing에서 고착되지 않고 confirm/status reconciliation으로 복구된다.
- commit 안전성: transaction 예외 뒤 operation과 `(userId, creationOperationId)` Session을 재조회한다. operation 전이가 관측되면 다음 단계로 진행하고 Session만 보이면 processing으로 수렴한다. transient/unknown Runtime과 동시성 예외에서는 reservation cancel을 금지했으며, 확정적인 local `IllegalStateException`에서 operation·Session이 모두 보이지 않을 때만 기존 cancel 보상을 수행한다.
- strict 계약: Jackson scalar coercion과 enum ordinal을 차단하고 문자열·날짜 입력 shape를 강화했다. reserve/confirm/cancel/status response record에 endpoint별 필수 field 검증을 추가하고, Saga에서 confirm `attemptGroupStatus=OPEN`·필수 timestamp 및 status/cancel identity·terminal timestamp를 검증하며 timestamp의 현재 시각 fallback을 제거했다.
- 테스트: `ENTITLEMENT_CONFIRMING + SESSION_COMMITTED` 복구, unknown commit no-cancel, unknown commit 후 관측된 전이 계속 진행, confirm group status/timestamp fail-closed, 숫자→문자열 coercion·숫자 enum·필수 confirm field 누락 거절 회귀 테스트를 추가했다.
- 실행 결과: 집중 테스트 18개 통과 후 `./gradlew clean test`를 실행했고 전체 432개 테스트가 통과했다. `git diff --check`도 통과했다.
- 유지 계약: 기존 공개 API URL·Method·Request/Response·`BaseResponse`, AI `user_id=examId`, callback, retryCount, S3와 Redis 계약은 변경하지 않았다. Billing 저장소·AWS·Jira·Git commit/push도 변경하지 않았다.
- 남은 위험: Mock 기반 테스트이므로 실제 Mongo replica set의 transient transaction label·unknown commit failure injection, VPC Lattice/IAM/SG 연결과 staging reserve/commit/confirm/status E2E는 운영 활성화 전에 별도로 검증해야 한다. PR에는 기존에 섞인 비용 추정·10초 챌린지 등 TMI-116 무관 문서를 selective staging 또는 별도 commit으로 분리해야 한다.

## 2026-09-01 — TMI-118 Jira 완료 전환

<!-- codex-turn:01a05b67-4319-7441-a178-1d8d7333970f -->

- 날짜: 2026-09-01
- 현재 브랜치: `develop`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`
- 수행 작업: Jira의 현재 상태와 workflow transition을 확인하고 사용자 요청에 따라 transition ID 41 `완료`를 적용했다.
- Jira 결과: TMI-118이 `해야 할 일`에서 `완료`로 전환됐으며 Jira 응답의 status category가 `done`임을 확인했다.
- Git 확인: 구현 commit `63d0f7d`은 로컬·원격 `feat/TMI-118-attempt-group-outbox-publisher`에 존재한다. `git merge-base --is-ancestor 63d0f7d develop` 결과는 false이므로 현재 develop에는 아직 병합되지 않았다.
- 판단: Jira는 사용자 요청대로 완료 상태로 유지한다. feature branch의 PR/merge와 production writer 활성화는 Jira 상태 전환과 별개의 후속 단계다.
- 변경 파일: 상태 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 수정했다. 애플리케이션·테스트·설정 코드는 변경하지 않았다.
- 유지한 계약: 공개 API, BaseResponse, AI·S3·Redis와 Billing schema v1을 변경하지 않았다.
- 테스트·검증: 애플리케이션 변경이 없어 Gradle 테스트를 다시 실행하지 않았다. 문서 변경은 `git diff --check`와 marker 1회 확인으로 검증한다.
- 보안·범위: Secret·Token과 사용자·Session 식별값을 기록하지 않았다. DB·AWS·Git commit·push·PR·merge·배포는 변경하지 않았고 Jira 상태 전환 외의 외부 변경은 없다.

## 2026-08-31 — TMI-116 로컬 develop 반영 확인

<!-- codex-turn:01a0561a-135f-70c2-982b-dbf9eab4b0b1 -->

- 날짜: 2026-08-31
- 브랜치: `develop`
- Jira: `TMI-116`; Jira 상태는 조회하거나 변경하지 않았다.
- 현재 checkout과 ref를 비교한 결과 `HEAD`, 로컬 `develop`, `origin/develop`이 모두 merge commit `d95d18b42a47383c2237fdb7eae536b7495136fb`를 가리킨다.
- TMI-116 feature 최신 commit `c3e3c8296316b1e49014413eb3dc32efaad76aba`는 해당 merge commit의 parent이며 로컬 `develop` ancestry에도 포함된다.
- 결론: PR `#24`의 TMI-116 구현과 P1/P2 보완은 원격뿐 아니라 현재 로컬 작업 폴더에도 반영됐다.
- 변경 범위: 상태 확인과 기록 문서 갱신만 수행했다. 애플리케이션·외부 계약·AWS·Jira·Git commit/push는 변경하지 않았고 코드 변경이 없어 Gradle 테스트를 재실행하지 않았다. 기존 WORKLOG 미커밋 변경은 수정하거나 삭제하지 않고 보존했다.

## 2026-08-28 — TMI-109 develop 병합 확인 및 Jira 완료 처리

<!-- codex-turn:01a0460c-0342-77a1-b78a-5f667e2cb6da -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: Learning Core `TMI-109`; 후속 Identity `TMI-111`과의 `TMI-109 blocks TMI-111` 관계를 유지했다.
- 작업 목표: TMI-109 구현이 원격 `develop`에 정상 병합됐는지 검증하고, 조건을 충족하면 Jira 이슈를 완료한다.
- GitHub 검증: PR [#23](https://github.com/Too-Much-I/app-back-end-learning-core/pull/23)은 head `feat/TMI-109-withdrawal-deny-gate-consumer`, base `develop`, 상태 `MERGED`이며 2026-08-28 10:44:51 KST에 merge commit `4baa4f20b7b179290dd743325ef7b251a408da47`로 병합됐다. CodeRabbit check는 `SUCCESS`다.
- Git 검증: `git fetch origin develop --prune` 후 `origin/develop`이 위 merge commit을 가리키는지 확인했다. merge commit의 두 parent는 기존 develop `514fb49c43342fb968b32b071cd0fadc2ad143dc`와 TMI-109 구현 `96e5c2021ef4ffa6a3e1069c69c08d31e974bb31`이다. 병합 diff에 withdrawal 코드·테스트·설정·runbook이 포함됐고 `git diff --check 4baa4f2^1..4baa4f2`가 통과했다. 깨끗한 로컬 `develop`을 `origin/develop`로 fast-forward해 동일 커밋으로 맞췄다.
- 테스트 근거: 구현 종료 시 `./gradlew clean test --no-daemon` 전체 Java 테스트 402개가 failures 0, errors 0, skipped 0으로 성공했다. 이번 병합 확인 turn에서는 같은 merge commit에 대해 Gradle 전체 테스트를 중복 실행하지 않았다.
- Jira 변경: TMI-109의 기존 상태 `해야 할 일`에서 사용 가능한 transition 41 `완료`를 실행했다. 변경 후 재조회에서 status `완료`, resolution `완료`를 확인했다. 이슈 본문·댓글·링크는 변경하지 않았다.
- 유지한 계약: 공개 API·DTO·`BaseResponse`, retryCount, S3·Redis·Polling, Python AI 요청·Callback JSON과 `user_id=examId` 계약은 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 남아 있는 위험 요소: merge와 Jira 구현 완료는 확인됐지만 production feature flag 활성화 조건은 별도다. replica set, probe collection, 정확한 TTL index, workload 인증 profile·운영 duration, 실제 Transaction·다중 instance·staging E2E가 남아 있다.
- 다음 작업 전 확인 사항: 운영 준비를 완료한 뒤 Learning Core consumer/gate를 단계적으로 활성화하고 smoke test 후 후속 Identity `TMI-111` publisher/outbox/backfill을 진행한다. 이번 기록 문서 변경은 로컬 미커밋 상태이며 Codex는 commit·push를 수행하지 않는다.

## 2026-08-28 — 10초 챌린지 attempt 생성 시점과 자정 경계 검토

<!-- codex-turn:01a04620-2d43-7c50-9e74-1b9a66f7290f -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없다. Jira 상태·본문·댓글·링크를 변경하지 않았다.
- 작업 목표: 프론트가 녹음을 완료한 뒤 제출 과정에서 attempt를 생성할 때 KST 날짜 변경으로 응시가 막히는지 현재 10초 챌린지 계약에 대조한다.
- 확인 문서: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`를 읽기 전용으로 확인했다.
- 결론: 우려가 유효하다. 프론트가 23:59:55에 이전 날짜 문제 녹음을 시작하고 00:00:05에 attempt 생성을 요청하면, server는 이전 `X-Challenge-Date`와 현재 KST 날짜 불일치로 mutation 없이 `409 CHALLENGE_DATE_CHANGED`를 반환한다. 이 경우 아직 기존 attempt가 없으므로 방금 녹음한 audio를 이전 날짜 문제에 제출할 수 없고, 새 날짜 문제도 달라질 수 있어 녹음을 재사용하면 안 된다.
- 확정된 호출 순서: `오늘 진행도 조회 → 문제 조회 → attempt 생성 및 Presigned PUT URL 발급 → attemptId/challengeDate 로컬 보관 → 최대 10초 녹음 → S3 PUT → answer 제출`이다. recorder는 attempt 생성 성공 뒤에만 시작해야 한다.
- 자정 처리: 자정 전에 생성된 attempt는 생성 당시 challengeDate에 고정하며 `submissionDeadlineAt=attemptCreatedAt+5분`까지 자정 이후에도 이전 날짜 제출을 허용한다. answer 처리는 현재 KST 날짜가 아니라 attemptId에 저장된 challengeDate와 S3 key를 기준으로 해야 한다. deadline을 넘긴 경우에만 `410 CHALLENGE_ATTEMPT_EXPIRED`로 종료한다.
- 프론트 오류 처리: attempt 생성 전에 날짜가 바뀌어 `CHALLENGE_DATE_CHANGED`가 오면 녹음을 시작하지 않고 캐시된 문제를 폐기한 뒤 오늘 진행도를 재조회한다. 이미 잘못된 순서로 녹음했다면 그 파일은 이전·새 날짜 어디에도 자동 제출하지 않고 안내 후 새 문제로 다시 녹음해야 한다.
- 변경 파일: 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·테스트·공개 계약 문서는 수정하지 않았다.
- 실행한 테스트와 결과: 코드 변경이 없는 계약 분석이므로 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`와 marker 단일 포함을 검증한다.
- 유지한 계약: 기존 시험 공개 API·DTO·`BaseResponse`, S3·Redis·AI/Callback과 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 다음 작업 전 확인 사항: 프론트의 실제 recorder 시작 handler가 attempt API 성공을 await하는지 확인하고, `23:59:55 녹음 시작`과 `23:59:59 attempt 응답 후 00:00:09 제출` 두 자정 경계 E2E를 반드시 추가한다.

## 2026-08-28 — 10초 챌린지 backend-only 자정 rollover 대안 검토

<!-- codex-turn:01a04620-2d43-7c50-9e74-1b9a66f7290f -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없다. Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 프론트의 현재 `녹음 후 attempt 생성` 순서를 바꾸지 않고 백엔드만으로 자정 경계 녹음 유실을 방지할 수 있는지 검토한다.
- 결론: attempt 생성 요청이 기존 `X-Challenge-Date`로 녹음 대상 날짜를 전달한다면 backend-only 해결이 가능하다. 서버가 현재 날짜 불일치 요청을 모두 409로 거절하지 않고, 요청 날짜가 직전 KST 날짜이며 자정 후 제한된 creation grace 안인 경우 이전 날짜 attempt 생성을 허용하면 된다.
- 저장 규칙: rollover attempt는 현재 날짜가 아니라 요청한 이전 `challengeDate`의 ChallengeDefinition, 문제 번호와 `(userId, challengeDate, questionNumber)` unique key에 귀속한다. 이전 날짜의 순차 진행 조건과 문제당 1회 제한도 동일하게 적용하고 새 날짜 attempt로 잘못 저장하지 않는다.
- 권장 안전장치: 프론트 계약을 늘리지 않으려면 question 조회 시 서버가 `(userId, challengeDate, questionNumber, viewedAt, expiresAt)` 형태의 짧은 view/recording lease를 내부에 기록한다. 자정 이후 이전 날짜 attempt 생성은 자정 전에 발급된 유효 lease가 있을 때만 허용한다. 이를 통해 임의의 과거 날짜 header로 뒤늦게 응시하는 것을 막는다.
- deadline 정책: creation grace와 제출 deadline을 분리해야 한다. 예를 들어 이전 날짜 attempt를 자정 후 짧은 creation grace 동안만 만들게 하고, 생성 성공 후에는 서버가 정한 upload/submission 복구 시간을 부여한다. 정확한 grace와 deadline duration은 운영·UX 계약으로 구현 전에 확정해야 하며 임의 값으로 고정하지 않았다.
- 한계: 현재 프론트가 `X-Challenge-Date` 또는 동등한 기존 날짜 식별자를 attempt 요청에 전혀 보내지 않는다면 서버는 녹음이 이전 날짜 문제인지 새 날짜 문제인지 알 수 없다. 이 경우 정확한 backend-only 처리는 불가능하고 최소한 기존 날짜 정보 전달이 필요하다.
- 변경 파일: 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션 코드·테스트·challenge 계약 문서는 수정하지 않았다.
- 실행한 테스트와 결과: 코드 변경이 없는 설계 검토라 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`와 marker 단일 포함을 검증한다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback 및 `user_id=examId` 계약을 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 다음 작업 전 확인 사항: 프론트 attempt 요청에 실제로 `X-Challenge-Date`가 포함되는지 먼저 확인하고, 포함된다면 backend rollover acceptance와 server-side lease, 자정 경계 통합 테스트를 구현 계획에 반영한다.

## 2026-08-28 — 10초 챌린지 backend-only rollover 종료 훅 동기화

<!-- codex-turn:01a04621-8c70-7893-a9ea-3fa2b8952e3f -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없으며 Jira를 변경하지 않았다.
- 작업 목표: 프론트의 `녹음 후 attempt 생성` 순서를 유지하는 backend-only 자정 rollover 대안과 종료 상태를 현재 turn marker로 기록한다.
- 결과: 기존 attempt 요청이 `X-Challenge-Date`를 전달한다면, 서버는 요청 날짜가 직전 KST 날짜이고 자정 후 제한된 creation grace 안이며 자정 전에 발급된 server-side question view/recording lease가 있을 때 이전 날짜 attempt 생성을 허용할 수 있다. attempt는 요청 날짜의 콘텐츠와 `(userId, challengeDate, questionNumber)` unique key에 귀속한다.
- 안전 조건: creation grace와 upload/submission deadline을 분리하고, 기존 순차 진행·문제당 1회 제한을 유지한다. 날짜 식별자가 없다면 녹음 대상 날짜를 판별할 수 없어 정확한 backend-only 처리는 불가능하다.
- 변경 파일: 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·테스트·공개 challenge 계약은 변경하지 않았다.
- 실행한 테스트와 결과: 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. `git diff --check`와 이 marker의 단일 포함을 종료 전에 검증한다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback과 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 다음 작업 전 확인 사항: 프론트의 실제 attempt 요청에 `X-Challenge-Date`가 포함되는지 확인하고, creation grace·submission deadline 운영값을 확정한 뒤 backend rollover와 자정 경계 테스트 구현 여부를 결정한다.

## 2026-08-28 — 10초 챌린지 임시 recording session 기반 rollover 검토

<!-- codex-turn:01a04625-d3fa-77f3-91b9-c6c93ed5f0af -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 자정 전에 기록한 임시 session 또는 시작 시각을 근거로, 자정 후에도 이전 날짜 attempt 생성을 안전하게 허용하는 방식을 검토한다.
- 결론: server-issued 임시 recording session을 사용하는 방식이 단순한 이전 날짜 grace보다 안전하다. 서버가 `userId`, `challengeDate`, `questionNumber`, `startedAt`, `expiresAt`을 저장하고 attempt 생성 시 session의 KST `startedAt` 날짜가 요청 `challengeDate`와 같으며 현재 시각이 `expiresAt` 이내인지 검증한다.
- 신뢰 경계: `startedAt`은 client Request 값이 아니라 server `Clock`으로 기록해야 한다. client timestamp만 받아 이전 날짜를 허용하면 변조로 과거 challenge를 시작할 수 있다.
- 원자성: session 확인·소비와 ChallengeAttempt insert는 같은 Mongo Transaction으로 처리한다. attempt의 `(userId, challengeDate, questionNumber)` unique 제약을 유지하고, 중복 요청은 기존 attempt로 멱등 수렴시킨다. session collection의 TTL은 정리 수단일 뿐 authorization 근거로 사용하지 않고 `expiresAt`을 직접 비교한다.
- deadline: 자정 뒤 attempt가 생성됐다는 이유로 제출 시간이 새로 5분 연장되면 안 된다. `submissionDeadlineAt=session.startedAt+허용시간`으로 고정하면 23:59:55에 시작한 session은 자정을 지나도 정해진 복구 시간까지만 제출할 수 있다.
- 프론트 무변경 대안: 기존 문제 GET 시 session을 server-side로 생성하거나 갱신하면 Request/Response 변경 없이 적용할 수 있다. 다만 이 시각은 실제 녹음 시작이 아니라 문제 조회 시각이므로 사용자가 문제를 오래 보고 있으면 session이 먼저 만료될 수 있다.
- 정확한 대안: 실제 녹음 시작 시각이 필요하면 녹음 직전에 server start/session API를 호출해야 하므로 최소한의 프론트 변경이 필요하다. 또는 프론트가 녹음 직전에 이미 호출하는 기존 endpoint가 있다면 그 요청에 session 생성을 결합할 수 있다.
- 변경 파일: 설계 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·테스트·challenge 공개 계약은 변경하지 않았다.
- 실행한 테스트와 결과: 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`와 marker 단일 포함을 검증한다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback 및 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 다음 작업 전 확인 사항: 프론트가 녹음 직전에 호출하는 기존 API가 있는지 확인한다. 없으면 문제 GET 시 session 생성의 UX 한계를 수용할지, 명시적 start API를 추가할지 결정하고 session TTL·submission deadline 값을 확정한다.

## 2026-08-28 — 10초 챌린지 attempt와 S3 URL 발급 분리 검토

<!-- codex-turn:01a0462c-e587-7d60-9f84-d3300c88e662 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 녹음 시작 시 attempt를 생성하고 녹음 완료 후 같은 attempt에 S3 Presigned URL을 연결하는 분리 계약을 검토한다.
- 권장 호출 순서: `오늘 진행도/문제 조회 → POST attempt → 최대 10초 녹음 → POST attempt upload-url → S3 PUT → POST answer/submit → 결과 polling`이다.
- 자정 처리: attempt 생성 시 서버 `Clock`으로 `createdAt`, `challengeDate`, `submissionDeadlineAt`을 확정한다. 23:59:55에 생성한 attempt는 날짜가 바뀌어도 deadline 전까지 동일 attemptId로 upload-url을 받고 이전 날짜 문제로 제출할 수 있다. 현재 날짜와 다시 연결하거나 이전 날짜의 새 attempt를 자정 후 생성할 필요가 없다.
- S3 연결: object key는 attemptId를 포함해 attempt 생성 시 결정·저장하거나 동일 입력에서 결정적으로 계산한다. upload-url endpoint는 JWT 소유권, attempt 상태와 deadline을 확인하고 같은 object key의 URL만 발급한다. URL 만료·응답 유실 시 동일 attemptId로 재발급하며 새 사용자 응시로 계산하지 않는다.
- 멱등성·동시성: attempt unique key `(userId, challengeDate, questionNumber)`를 유지해 중복 시작 요청은 기존 CREATED attempt를 반환한다. upload-url 재발급은 같은 key, submit은 동일 Idempotency-Key와 attemptId로 같은 결과에 수렴한다. 이미 SUBMITTED/EXPIRED인 attempt에는 새 upload target을 만들지 않는다.
- 상태: 내부 상태는 `CREATED → UPLOAD_READY/UPLOADING → SUBMITTED` 또는 deadline 후 `EXPIRED`로 관리할 수 있다. 기존 Draft의 공개 projection을 유지한다면 제출 전은 `not_started`, terminal 후는 `submitted`다.
- 제품 결정 필요: attempt 생성이 문제당 1회를 점유하므로 녹음 취소·앱 종료·S3 미업로드도 deadline 후 EXPIRED로 처리된다. 이 경우 참고 답안을 공개하고 history 풀이 수에 포함할지, 참여만 기록할지 구현 전에 확정해야 한다.
- 계약 영향: 현재 draft의 `POST attempt`가 attempt와 upload URL을 함께 반환하는 구조를 두 endpoint로 나누므로 프론트 호출 순서와 draft API 계약은 변경된다. 다만 해당 challenge API는 아직 구현·배포되지 않아 기존 운영 호환성 문제는 없다.
- 변경 파일: 설계 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·테스트·challenge 계약 문서는 변경하지 않았다.
- 실행한 테스트와 결과: 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`와 marker 단일 포함을 검증한다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback 및 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 다음 작업 전 확인 사항: attempt 생성이 녹음 취소에도 1회를 소비하는 정책, upload-url endpoint 경로, submission deadline과 Presigned URL TTL을 확정한 뒤 challenge draft 계약을 갱신한다.

## 2026-08-28 — 10초 챌린지 attempt·S3 URL 분리 계약 확정

<!-- codex-turn:01a0462c-e587-7d60-9f84-d3300c88e662 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 사용자가 승인한 `녹음 시작 attempt 생성`과 `녹음 완료 후 S3 Presigned URL 발급` 분리 구조를 구현 기준 challenge Draft 계약으로 확정한다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 변경했다. 애플리케이션·테스트 코드는 변경하지 않았다.
- 확정 호출 순서: `오늘 진행도 → 문제 조회 → POST attempt → 최대 10초 녹음 → POST /attempts/{attemptId}/upload-url → S3 PUT → answer 제출 → 결과 polling`이다.
- attempt 계약: `POST /api/v1/challenges/today/questions/{questionNumber}/attempt`는 upload 정보를 반환하지 않고 `attemptId`, `challengeDate`, `questionNumber`, 공개 `attemptStatus`, `submissionDeadlineAt`만 반환한다. 서버는 생성 시 `createdAt`, challengeDate, deadline과 attemptId 기반 `.m4a` S3 object key를 내부에 고정한다.
- upload-url 계약: `POST /api/v1/challenges/attempts/{attemptId}/upload-url`을 추가했다. JWT 소유권, attempt 상태와 deadline을 확인하고 동일 object key의 Presigned PUT URL만 발급한다. URL 만료·응답 유실 시 같은 attemptId로 재발급하며 새 응시로 계산하지 않고 URL 만료 시각은 submission deadline을 넘지 않는다.
- 자정 계약: 자정 전에 생성한 attempt는 저장된 challengeDate와 `attemptCreatedAt+5분` deadline을 유지한다. 자정 후 upload-url·answer 요청은 현재 날짜로 다시 판정하지 않아 기존 attempt를 이전 날짜 문제로 정상 완료할 수 있다. 자정 이후 이전 날짜의 새 attempt 생성은 계속 금지한다.
- 상태·멱등성: 내부 상태를 `NOT_CREATED → CREATED → UPLOAD_READY/UPLOADING → SUBMITTED 또는 EXPIRED`로 정리했다. attempt 중복 시작은 같은 attempt와 deadline, upload-url 재발급은 같은 object key, answer 재전송은 같은 Idempotency-Key로 수렴한다.
- 계약 영향: 프론트 명세를 Draft v0.5에서 v0.6으로 올렸다. 아직 challenge API가 구현·배포되지 않았으므로 기존 운영 API 호환성 영향은 없다.
- 실행한 테스트와 결과: 문서 계약만 변경해 Gradle 테스트는 실행하지 않았다. 오래된 결합 발급 문구와 heading 번호를 검색해 정리했고 종료 전에 `git diff --check`를 실행한다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback과 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 남아 있는 결정: sample rate·channel·최대 파일 크기, AI 결과 필드, grading retry/timeout과 MEMBER/Guest 범위는 여전히 미확정이다. 기존 Draft의 attempt 만료 terminal·참고 답안·history 반영 정책은 이번 분리 작업에서 변경하지 않았다.
- 다음 작업 전 확인 사항: 프론트·백엔드가 Draft v0.6 endpoint와 호출 순서를 함께 기준으로 삼는지 확인한 뒤 Challenge domain/API 구현 Jira와 구현 계획에 반영한다.

## 2026-08-28 — 10초 챌린지 Draft v0.6 계약 확정 종료 훅 기록

<!-- codex-turn:01a0462e-7adf-72a2-be13-1cffdd18a7c5 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없으며 Jira를 변경하지 않았다.
- 작업 목표: 사용자가 승인한 attempt 생성과 S3 upload-url 분리 계약의 반영 결과를 현재 turn marker로 기록한다.
- 결과: 프론트 명세를 Draft v0.6으로 갱신했다. 녹음 시작 전 `POST /api/v1/challenges/today/questions/{questionNumber}/attempt`로 attempt와 날짜·deadline을 확정하고, 녹음 후 `POST /api/v1/challenges/attempts/{attemptId}/upload-url`로 동일 S3 object key의 Presigned PUT URL을 발급·재발급한다.
- 자정·멱등성: 자정 이후에도 기존 attempt의 저장된 challengeDate와 deadline을 사용한다. 중복 attempt 시작은 같은 attempt, URL 재발급은 같은 object key, answer 재전송은 같은 Idempotency-Key로 수렴한다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 변경했다. 애플리케이션·테스트 코드는 변경하지 않았다.
- 테스트: 문서 계약 변경이라 Gradle 테스트는 실행하지 않았고 `git diff --check`가 통과했다. 오래된 attempt·URL 결합 발급 문구와 heading 번호도 검색해 정리했다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback 및 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 남은 사항: Challenge API는 아직 구현·배포되지 않았다. sample rate·channel·최대 파일 크기, AI 결과 필드, grading timeout/retry와 MEMBER/Guest 범위는 계속 미확정이다.
- 다음 작업 전 확인 사항: Draft v0.6을 프론트·백엔드 공통 기준으로 확인한 뒤 Challenge domain/API 구현 Jira와 구현 계획에 반영한다.

## 2026-08-28 — 10초 챌린지 프론트 계약 문서 위치 확인

<!-- codex-turn:01a04634-3cdc-7201-a69f-3a04f4e4b516 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 사용자가 요청한 10초 챌린지 프론트 계약 문서를 확인하고 이 창에서 열 수 있도록 제공한다.
- 확인 결과: 대상 문서는 `docs/contracts/ten-second-challenge-frontend-api.md`이고 현재 버전은 Draft v0.6, 작성일은 2026-08-28이다. 녹음 시작 attempt와 녹음 후 S3 upload-url 발급 분리, server 기준 날짜 rollover 보호가 반영돼 있다.
- 변경 파일: 이번 조회 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 프론트 계약 문서·애플리케이션·테스트 코드는 수정하지 않았다.
- 실행한 테스트와 결과: 문서 위치와 heading을 확인했으며 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`와 marker 단일 포함을 검증한다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback 및 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 다음 작업 전 확인 사항: 프론트는 Draft v0.6의 전체 호출 순서와 6.3 attempt 생성, 6.4 upload-url 발급 항목을 우선 확인한다.

## 2026-08-28 — 10초 챌린지 attempt 유효시간 1시간 확정

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 작업 목표: attempt 생성 시점부터 제출 가능한 유효시간을 5분에서 1시간으로 변경한다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 변경했다. 애플리케이션·테스트 코드는 변경하지 않았다.
- 계약 결과: `submissionDeadlineAt=attemptCreatedAt+1시간`으로 확정하고 프론트 명세를 Draft v0.7로 올렸다. 자정 전 생성된 attempt는 자정 후에도 deadline까지 저장된 challengeDate로 upload-url 발급·S3 PUT·answer 제출을 허용한다.
- 자정 예시: 23:59:50 KST attempt는 다음 날 00:59:50까지 유효하다. 22:50 KST attempt는 23:50 KST에 만료되어 자정까지 자동 연장되지 않는다.
- URL과 attempt 분리: attempt는 1시간 유효하지만 Presigned URL은 보안상 짧게 발급한다. 예시 URL은 5분 유효하며 만료 시 같은 attemptId와 S3 object key로 재발급하고 URL 만료 시각은 attempt deadline을 넘지 않는다.
- 상태·history: 1시간 안에 제출하지 못하면 EXPIRED terminal로 처리하고 기존 Draft대로 공개 `attemptStatus=submitted`, 참고 답안 제공과 history 풀이 수 반영 정책을 유지한다.
- 실행한 테스트와 결과: 문서 계약만 변경해 Gradle 테스트는 실행하지 않았다. 대상 계약 문서에서 이전 5분 표현과 자정 예시를 검색·교정했고 `git diff --check`를 종료 전에 검증한다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback 및 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 다음 작업 전 확인 사항: 프론트·백엔드가 Draft v0.7의 1시간 attempt deadline과 짧은 Presigned URL 재발급 정책을 공통 기준으로 사용해야 한다.

## 2026-08-28 — 10초 챌린지 attempt 1시간 계약 종료 훅 기록

<!-- codex-turn:01a04636-3385-7f22-8ab2-8000a8178d9a -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 신규 이슈 키는 없으며 Jira를 변경하지 않았다.
- 작업 목표: 10초 챌린지 attempt 제출 유효시간을 1시간으로 확정한 결과를 현재 turn marker로 기록한다.
- 결과: 프론트 명세 Draft v0.7, API·상태 결정서와 출시 계획에서 `submissionDeadlineAt=attemptCreatedAt+1시간`으로 동기화했다. 23:59:50 KST 생성 attempt는 다음 날 00:59:50까지 원래 challengeDate로 제출할 수 있다.
- URL 정책: Presigned URL은 attempt보다 짧게 발급하고 만료 시 attempt deadline 전까지 동일 attemptId와 object key로 재발급한다. URL 만료 시각은 attempt deadline을 넘지 않는다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 변경했다. 애플리케이션·테스트 코드는 변경하지 않았다.
- 테스트: 문서 변경이라 Gradle 테스트는 실행하지 않았다. 이전 5분 계약 표현과 자정 예시를 검색해 교정했고 `git diff --check`가 통과했다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback 및 `user_id=examId`를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 다음 작업 전 확인 사항: 프론트·백엔드가 Draft v0.7의 attempt 1시간 deadline과 Presigned URL 재발급 규칙을 구현 기준으로 사용해야 한다.

## 2026-08-28 — 전체 앱 프론트엔드 API 인계서 작성

<!-- codex-turn:01a0463a-cc04-73a1-8c34-a2fb851f8d7e -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: `TMI-102`, `TMI-105`, `TMI-106`, `TMI-109`, `TMI-110`, `TMI-111`의 현재 구현 경계를 문서에 반영했으며 Jira 자체는 조회하거나 변경하지 않았다.
- 작업 목표: 프론트가 현재 호출 가능한 Identity·Learning Core API와 1차 업데이트 예정 API를 상태·인증·요청·응답·호출 흐름별로 한 문서에서 확인하게 한다.
- 변경 파일: `docs/contracts/FRONTEND_API_HANDOFF.md`를 추가하고 이 작업 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 갱신했다. 기존 애플리케이션·테스트 코드와 다른 계약 문서는 변경하지 않았다.
- 변경한 내용: Identity의 LOCAL·Guest·Firebase/SNS·Token·사용자 API 17개와 Learning Core 모의고사 API 11개를 현재 컨트롤러·DTO·Security 설정 기준으로 정리했다. 공통 BaseResponse 차이, Bearer Token, Firebase enrollment·Guest upgrade/merge, 정책 동의, 탈퇴, S3 PUT·submit·polling, 시험 이력·재답변·채점 복구 예시를 포함했다.
- 계획/내부 경계: Billing 공개 프론트 API는 현재 0개임을 명시하고 무료 1회 TrialClaim·Entitlement·Reservation과 Learning Core Billing saga를 미구현으로 표시했다. 10초 챌린지 7개 endpoint는 Draft v0.7 링크와 함께 미구현으로 분리했다. AI callback, `UserWithdrawn`, eligibility workload와 JWKS는 프론트 호출 금지로 분류했다.
- 유지한 외부 계약: 기존 공개 API URL·Method·Parameter·DTO·BaseResponse, S3 Object Key, Redis, Python AI callback과 `user_id=examId` 계약은 변경하지 않았다. Secret·실제 Token·실제 운영 host를 문서에 넣지 않았다.
- 실행한 테스트와 결과: 문서 변경이므로 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`, 링크 대상 존재, endpoint 대조와 이 marker의 단일 포함을 검증한다.
- 남아 있는 위험 요소: Learning Core Presigned PUT URL은 코드상 5분 signature지만 응답 `expiresIn`은 60으로 생성돼 의미·단위가 일치하지 않는다. Billing 공개 계약과 10초 챌린지 API는 아직 구현 전이며 Challenge의 sample rate·channel·최대 파일 크기·AI result·rollover 최종 승인이 남아 있다.
- 다음 작업 전 확인 사항: backend가 upload URL 만료 계약을 정리하고, Billing/Challenge 구현 Jira와 최종 공개 DTO가 동결된 뒤 프론트 타입과 feature flag를 production 연동한다.

## 2026-08-28 — TMI-109·TMI-111 workload JWT 계약안 코드 대조 검토

<!-- codex-turn:01a046a4-67d1-7751-bca1-8a6f2052e37f -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: Learning Core `TMI-109`, 후속 Identity publisher `TMI-111`. Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 제안된 UserWithdrawn workload JWT 계약을 Learning Core의 실제 validator와 Identity의 기존 RS256/JWKS 발급 구조에 대조해 승인 가능 여부와 보완점을 판단한다.
- 확인 범위: Learning Core `UserWithdrawnConfiguration`, workload properties, audience/principal/maximum-lifetime validator와 security integration test, Identity의 JWT issuer/configuration/JWKS/controller·workload credential interface·publisher properties·JWT 계약을 읽기 전용으로 확인했다. Identity 파일은 수정하지 않았다.
- 승인 가능한 내용: RS256·`typ=JWT`·`kid`, 사용자 issuer와 분리한 workload issuer, 전용 audience `learning-core-user-withdrawn`, Identity 기존 RSA signing infrastructure와 JWKS 재사용, TTL PT2M·clock skew PT30S, 내부 JwtEncoder 로컬 발급·공개 token API 없음·요청별 새 token, HTTPS·redirect 금지와 제안된 retry 분류 방향은 적절하다.
- 실제 Learning Core 검증: RS256 알고리즘, issuer, audience list에 필수 값 포함, 설정된 단일 principal claim/value 일치, timestamp, 필수 `iat/exp`와 `exp-iat <= max-token-lifetime`을 검증한다. audience 배열은 현재 validator와 호환된다.
- 보완 1 principal: 제안대로 설정하면 실제 allowlist는 `service=identity` 하나이고 `sub=identity-service`는 강제되지 않는다. 권장안은 표준 `sub=identity-service`로 principal을 통일해 `principal-claim=sub`, `principal-value=identity-service`를 쓰는 것이다. custom `service`를 유지하려면 `sub`까지 별도 validator로 함께 강제하고 두 값 불일치를 거절해야 한다.
- 보완 2 시간 claim: 현재 maximum-lifetime validator는 `exp-iat` 길이만 제한하고 `iat`가 현재보다 먼 미래인지 직접 검증하지 않는다. 계약에 `nbf=iat`을 필수로 추가해 기존 timestamp validator가 PT30S skew로 검사하게 하거나 future-iat validator를 추가해야 한다. PT2M은 nominal lifetime이고 verifier skew 때문에 만료 수용 경계는 최대 약 2분 30초임을 운영 문서에 명시한다.
- 보완 3 header·replay: 현재 workload decoder는 RS256을 고정하지만 `typ=JWT`, `kid` 존재, `jti` 존재를 명시적 validator로 강제하지 않는다. 이를 필수 계약으로 둘 경우 rejection test와 validator 보완이 필요하다. `jti`는 token 고유성용이며 event replay 멱등성은 계속 payload `eventId` inbox가 담당하고 별도 jti 저장소를 추가할 필요는 없다.
- Identity 현황: 기존 user Access Token은 RS256, `kid`, issuer, audience, UUID sub, iat/exp/jti를 발급하고 표준 JWKS endpoint는 현재 RSA public key 한 개만 노출한다. `WorkloadIdentityCredentialProvider`는 interface만 있고 production 구현체가 없으므로 TMI-111에서 workload issuer·claim 발급을 구현해야 한다. 동일 signing key/JWKS 재사용은 가능하지만 old/new public key 동시 노출 rotation은 현재 미지원이다.
- 오류 정책 보완: 모든 2xx 성공, 408/425/429/5xx·timeout·connection failure retry, 400/409/413/422 영구 payload 실패 DLQ가 적절하다. 401/403뿐 아니라 404/405와 예상하지 못한 4xx도 payload DLQ가 아니라 endpoint/auth 배포 설정 오류로 격리·경보하는 편이 안전하다.
- 변경 파일: 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. Learning Core·Identity 애플리케이션과 계약 문서, Jira는 변경하지 않았다.
- 실행한 테스트와 결과: 코드 변경이 없는 분석이라 Gradle 테스트는 실행하지 않았다. 양 저장소 코드를 읽기 전용 대조했고 종료 전에 `git diff --check`와 marker 단일 포함을 검증한다.
- 유지한 계약: 사용자 Access Token의 `aud=tosunsaeng-learning-core`, Python AI `user_id=examId`, UserWithdrawn event wire·endpoint와 기존 공개 API를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 남아 있는 위험 요소: Identity JWKS 다중 키 rotation, workload provider 구현, `nbf/future iat`, principal 단일화 또는 이중 검증, header 필수성, 실제 HTTPS/no-redirect와 staging E2E가 완료되지 않았다.
- 다음 작업 전 확인 사항: principal을 `sub` 하나로 통일할지 `sub+service` 이중 검증할지 결정하고, `nbf=iat`과 header 필수 검증 범위를 확정한 뒤 TMI-111 Identity issuer와 TMI-109 Learning Core 설정·테스트를 같은 golden token으로 고정한다.

## 2026-08-28 — 10초 챌린지 실제 Mongo 콘텐츠 기반 계획·프론트 문서 갱신

<!-- codex-turn:01a046f9-f06b-74e3-8aef-d8cdf5255de6 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이다. Jira를 조회하거나 변경하지 않았고 Learning Core Challenge backend 구현 Jira는 아직 없다.
- 작업 목표: 실제 `challenge_10s_questions` document와 기존 `to-teacher-app` MongoDB cluster 사용 사실을 10초 챌린지 계획서·계약 결정서와 프론트 인계 문서에 반영한다.
- 사용자 제공 저장 구조: `dayNumber` document 안에 questionNumber 1~3의 `questionId`, `korean`, `referenceAnswer`, `difficulty`가 embedded array로 저장된다. 실제 Mongo URI·credential·logical database 이름은 기록하지 않았다.
- 변경 파일: `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/contracts/FRONTEND_API_HANDOFF.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 변경했다. 애플리케이션·테스트 코드는 변경하지 않았다.
- 인프라 계획: 별도 Atlas cluster와 Mongo connection을 만들지 않고 Learning Core의 기존 `to-teacher-app` cluster 연결·credential boundary를 재사용한다. production collection/index는 migration으로 준비하고 startup에서 catalog/index 정의를 검증한다.
- catalog 계약: collection은 `challenge_10s_questions`, document 하나는 양의 정수 dayNumber와 정확히 세 문제를 가진다. questionNumber 집합 `{1,2,3}`, dayNumber unique, catalog 전역 questionId 중복 없음, non-blank korean/referenceAnswer와 유효 difficulty를 검증한다. published day는 append-only로 관리한다.
- 프론트 매핑: `questions[].questionNumber → questionNumber`, `questions[].korean → promptKo`, `questions[].referenceAnswer → terminal 이후 referenceAnswer`로 매핑한다. `_id`, dayNumber, questionId와 difficulty는 내부 전용이며 프론트가 보내거나 계산하지 않는다. 실제 day 1 Q2 예시를 프론트 응답 예시에 반영했다.
- 보안·노출: referenceAnswer는 DB에 있어도 문제 조회, attempt 생성과 upload-url 응답에는 포함하지 않고 SUBMITTED 또는 EXPIRED terminal 이후에만 반환한다. 콘텐츠 누락·구조 오류 시 다른 날 문제를 섞지 않고 `404 CHALLENGE_CONTENT_NOT_FOUND`로 fail-closed한다.
- 결과 재현성: ChallengeAttempt 생성 시 dayNumber, questionId, questionNumber, korean, referenceAnswer와 difficulty snapshot을 저장해 catalog가 나중에 변경돼도 S3 submit·AI 요청·과거 결과가 동일 콘텐츠를 사용하게 계획했다.
- 날짜 resolver: 권장 MVP는 KST contentBaseDate를 고정하고 `daysBetween(contentBaseDate, challengeDate)+1`로 dayNumber를 계산한다. 없는 dayNumber에 modulo·random fallback은 하지 않는다. 정확한 base date와 콘텐츠 소진 후 순환은 아직 사용자 확정이 필요하다.
- 프론트 문서: 상세 계약을 Draft v0.8로 올리고 전체 API 인계서의 상태·field mapping·미확정 항목을 동기화했다. 공개 endpoint 수와 기존 request/response shape은 변경하지 않았다.
- 실행한 테스트와 결과: 문서 변경이라 Gradle 테스트는 실행하지 않았다. active challenge 문서의 Draft 버전·collection/cluster·field mapping·오래된 ChallengeDefinition 표현을 검색했고 종료 전에 `git diff --check`와 marker 단일 포함을 검증한다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis·AI/Callback `user_id=examId`, challenge attempt 1시간과 분리 upload-url 계약을 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 남아 있는 위험 요소: `dayNumber=1`의 KST 기준일, 소진 시 순환 여부, difficulty scale, sample rate·channel·최대 파일 크기, AI result와 retry/timeout, MEMBER/Guest 정책이 미확정이다. 실제 collection index와 전체 catalog 데이터 품질은 이번 문서 작업에서 DB에 접속해 검증하지 않았다.
- 다음 작업 전 확인 사항: contentBaseDate·순환·difficulty를 확정한 뒤 Learning Core Challenge backend Jira와 migration/catalog validator·repository·attempt snapshot 구현 계획을 생성한다.

## 2026-08-28 — 10초 챌린지 자동 Day 1·difficulty 계약 확정

<!-- codex-turn:01a046ff-dc5f-7942-b098-08edbb4674de -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이다. Jira를 조회하거나 변경하지 않았고 Learning Core Challenge backend 구현 Jira는 아직 없다.
- 작업 목표: dayNumber 1 기준일을 업데이트 시점부터 자동으로 시작하고 비순환으로 운영하며, Mongo `difficulty`는 프론트에 그대로 전달하되 AI 요청에서는 제외하도록 계획·프론트 계약을 확정한다.
- 자동 기준일: `app.challenge.enabled=true`로 처음 성공 기동한 KST 날짜를 Mongo `challenge_10s_catalog_state` singleton에 원자 `setOnInsert`로 한 번만 저장한다. disabled 배포는 초기화하지 않고 재시작·재배포·ECS scale-out도 기준일을 바꾸지 않는다.
- 날짜 선택: 저장된 `contentBaseDate`와 요청 `challengeDate`의 KST 날짜 차이에 1을 더해 dayNumber를 계산한다. modulo·random·이전 날짜 fallback 없이 비순환으로 운영하고, 없는 dayNumber는 `404 CHALLENGE_CONTENT_NOT_FOUND`와 운영 alert 대상으로 처리한다.
- difficulty: BSON integer 여부만 검증하고 범위·의미는 해석하지 않는다. 문제 조회, 제출 terminal 응답과 상세 결과에 정수를 그대로 반환하며 attempt snapshot에는 저장한다. attempt·upload-url 응답과 AI 요청·grading job payload에는 포함하지 않는다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/contracts/FRONTEND_API_HANDOFF.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 변경했다. 상세 프론트 계약은 Draft v0.9다.
- 변경하지 않은 항목: 애플리케이션·테스트 코드, Mongo 데이터, Jira, Git commit·push와 배포는 변경하지 않았다. 기존 시험 API·DTO·`BaseResponse`, S3·Redis, Python AI/Callback `user_id=examId` 계약도 유지했다.
- 테스트: 문서 변경이므로 Gradle 테스트는 실행하지 않았다. 문서 간 정책과 marker 단일 포함, `git diff --check`를 종료 전에 검증한다.
- 남아 있는 위험 요소: 첫 활성화가 의도한 출시 시점보다 먼저 실행되면 기준일이 조기 고정될 수 있으므로 feature flag 활성화 시점을 배포 절차에서 통제해야 한다. 콘텐츠가 준비된 마지막 dayNumber 다음 날에는 fallback 없이 404가 발생한다.
- 다음 작업 전 확인 사항: Challenge backend 구현 Jira를 만든 뒤 metadata initializer·비순환 resolver·catalog validator·attempt snapshot·공개 DTO와 AI payload exclusion 테스트를 구현한다. sample rate·channel·최대 파일 크기와 AI 결과 상세 계약도 확정해야 한다.

## 2026-08-28 — 10초 챌린지 Learning Core–AI 계약 미확정 항목 검토

<!-- codex-turn:01a04706-3a6b-7e60-843d-32b7e83dceb6 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이다. Jira를 조회하거나 변경하지 않았고 Learning Core Challenge backend·AI 연동 전용 Jira는 아직 없다.
- 작업 목표: 현재 Draft v0.9 프론트 계약과 10초 챌린지 결정서에서 Learning Core–AI 서버 간 계약으로 추가 확정해야 하는 경계를 식별한다.
- 현재 확정된 방향: 시험 Feedback Callback과 분리된 challenge 전용 versioned 비동기 계약을 사용한다. AI에는 실제 userId와 difficulty를 보내지 않고 attempt·문제 식별값, 한국어 prompt와 audio만 전달하며, 결정적 grading job과 callback 멱등성을 사용한다.
- 필수 확정 항목: contract version, AI request endpoint·인증·multipart 또는 object reference 전송 방식, M4A/AAC sample rate·channel·maxBytes, request field와 nullability, callback endpoint·인증·field, 결과 enum과 null 의미, no-speech·unsupported audio, timeout·retry·최종 실패 시간, HTTP 오류 분류와 payload 제한을 동결해야 한다.
- stale callback 방지 권고: outbound와 callback에 `attemptId`, 결정적 `jobId`, 양의 정수 `gradingAttempt`를 필수로 넣고 AI가 값을 그대로 echo해야 한다. 현재 generation과 불일치하는 늦은 callback은 기존 상태를 덮어쓰지 않는 성공 no-op으로 처리한다.
- 결과 권고: MVP는 숫자 점수 없이 transcript, `correct|needs_improvement` verdict, correctedAnswer, 짧은 meaning·grammar·pronunciation feedback을 사용한다. 내부 `no_speech`는 시스템 실패와 구분된 정상 terminal 결과로 저장하되 공개 `feedbackType`은 추가하지 않는다.
- 변경 파일: 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·테스트·계약 문서, Mongo 데이터, Jira, Git commit·push와 배포는 변경하지 않았다.
- 유지한 계약: 기존 프론트 Draft v0.9와 시험 API·DTO·`BaseResponse`, S3·Redis, Python 시험 AI/Callback `user_id=examId` 계약을 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 테스트: 코드 변경이 없는 계약 분석이라 Gradle 테스트는 실행하지 않았다. marker 단일 포함과 `git diff --check`를 종료 전에 확인한다.
- 남아 있는 위험 요소: 인증과 audio 전달 방식이 확정되지 않으면 네트워크·권한 설계가 달라지고, 결과 null/enum과 retry ownership이 확정되지 않으면 AI와 Learning Core가 동일 실패를 서로 재시도하거나 최종 상태가 불일치할 수 있다.
- 다음 작업 전 확인 사항: AI 팀과 request/callback 예시 payload, 인증, audio 규격, 결과 schema, 멱등성·retry 표를 합의한 뒤 challenge 전용 계약 문서를 v1로 동결하고 양쪽 contract test fixture를 공유한다.

## 2026-08-28 — 10초 챌린지 promptKo 의미 확인

<!-- codex-turn:01a0470f-48b5-7552-80e4-0ba94a426cd1 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이며 Jira를 조회하거나 변경하지 않았다.
- 확인 결과: `promptKo`는 Mongo `challenge_10s_questions.questions[].korean`에서 가져오는 한국어 문제 문장이다. 사용자는 이 문장을 보고 영어 문장을 만들어 발화한다.
- 구분: `referenceAnswer`는 참고 영어 답안이며 문제 조회 시에는 숨기고 제출 또는 만료 terminal 이후에만 공개한다.
- AI 계약: 앞서 제안한 `prompt_ko`는 `promptKo`와 같은 한국어 문장을 뜻하는 snake_case wire field 후보다. 실제 field명은 AI 계약 v1에서 최종 확정해야 한다.
- 변경 범위: 확인 결과를 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`에만 기록했다. 애플리케이션·테스트·계약 문서, Mongo 데이터, Jira와 배포는 변경하지 않았다.
- 유지한 계약: 기존 프론트 Draft v0.9, 시험 API·DTO·`BaseResponse`, Python 시험 AI/Callback `user_id=examId` 계약을 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 테스트: 코드 변경이 없는 용어 확인이라 Gradle 테스트는 실행하지 않았다. marker 단일 포함과 `git diff --check`를 종료 전에 확인한다.

## 2026-08-28 — 10초 챌린지 Learning Core–AI API 계약서 작성

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이다. Jira를 조회하거나 변경하지 않았고 Learning Core Challenge backend·AI 연동 전용 Jira는 아직 없다.
- 작업 목표: 앞서 제안한 권장안을 프론트 명세와 같은 수준의 Learning Core–AI 서버 간 독립 계약 문서로 작성한다.
- 신규 문서: `docs/contracts/ten-second-challenge-ai-api.md` Draft v0.1을 추가했다. 기존 시험 `/evaluations`와 Feedback Callback은 재사용·변경하지 않고 challenge 전용 versioned request·Callback endpoint를 사용한다.
- 요청 계약: Learning Core가 S3에서 audio를 내려받아 multipart로 전송한다. `attempt_id`, 결정적 `job_id`, `grading_attempt`, `question_id`, `question_number`, `prompt_ko`, `reference_answer`, `audio_file`을 필수로 두고 실제 userId·difficulty·dayNumber·challengeDate·S3 위치는 보내지 않는다.
- audio 계약: M4A/AAC-LC·`audio/mp4`, 16/44.1/48 kHz, mono/stereo, 최대 2 MiB를 허용하고 AI 내부에서 16 kHz mono PCM으로 정규화한다.
- 결과 계약: `completed`, `no_speech`, `failed` outcome과 transcript, `correct|needs_improvement`, corrected answer, 짧은 meaning·grammar·pronunciation feedback을 사용하며 숫자 점수는 제외한다. no-speech는 시스템 실패가 아닌 completed terminal이다.
- 보안·신뢰성: 방향별 service Bearer credential, private service discovery·TLS, 고정 endpoint, `Idempotency-Key`, callback UUID, `attemptId/jobId/gradingAttempt` fencing, duplicate·stale 204 no-op, 오류 분류·retry ownership, Callback 120초 deadline과 최대 3 generation 권장값을 문서화했다.
- 동기화 파일: `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`에서 새 계약을 참조하고 AI 팀 승인·contract test 후 v1 동결하도록 상태와 미확정 항목을 정리했다. 작업 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`도 갱신했다.
- 유지한 계약: 기존 프론트 Draft v0.9, 시험 API·DTO·`BaseResponse`, S3·Redis, Python 시험 AI/Callback `user_id=examId` 계약을 변경하지 않았다. 애플리케이션·테스트 코드, Mongo 데이터, Jira, Git commit·push와 배포도 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 테스트: 계약서의 JSON code block 네 개를 Ruby JSON parser로 검증했고 모두 통과했다. `git diff --check`도 통과했다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.
- 남아 있는 위험 요소: service Bearer credential은 payload를 유지한 채 workload JWT 또는 AWS_IAM으로 후속 강화할 수 있다. 모바일 실제 audio가 허용 profile과 호환되는지, AI 처리 p95가 120초 이내인지와 Callback retry/DLQ 구현 가능성을 staging에서 검증해야 한다.
- 다음 작업 전 확인 사항: AI 팀이 Draft v0.1을 검토하고 실제 audio fixture·인증·timeout/retry·결과 schema contract test를 승인한 뒤 v1로 동결한다. 프론트 `aiResult` DTO도 AI 결과 projection에 맞춰 최종 확정하고 Challenge backend·AI 구현 Jira를 생성한다.

## 2026-08-28 — 10초 챌린지 AI 계약서 작업 종료 기록 동기화

<!-- codex-turn:01a04711-3c1d-7e33-b1e3-d33c957a1fe5 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이며 Jira를 조회하거나 변경하지 않았다. Learning Core Challenge backend·AI 연동 전용 Jira는 아직 없다.
- 완료 상태: `docs/contracts/ten-second-challenge-ai-api.md` Draft v0.1 작성과 `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md` 동기화가 완료됐다.
- 계약 핵심: challenge 전용 multipart 요청·Callback, 방향별 서비스 인증, M4A/AAC-LC 허용 profile, `attemptId/jobId/gradingAttempt` fencing, `completed|no_speech|failed` 결과, 멱등성·timeout·retry·contract test를 문서화했다.
- 유지한 계약: 기존 프론트 Draft v0.9, 시험 API·DTO·`BaseResponse`, S3·Redis와 Python 시험 AI/Callback `user_id=examId` 계약을 변경하지 않았다.
- 테스트 결과: JSON 예시 4개가 parser 검증을 통과했고 `git diff --check`도 통과했다. 애플리케이션 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.
- 변경하지 않은 항목: 애플리케이션·테스트 코드, Mongo 데이터, Jira, Git commit·push와 배포를 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 남은 확인 사항: AI 팀의 실제 모바일 audio fixture, 서비스 인증·TLS, 처리 SLA·retry 검증과 프론트 `aiResult` DTO 승인을 거쳐 계약을 v1로 동결해야 한다.

## 2026-08-28 — 10초 챌린지 AI 계약 v1 승인 반영·잔여 결정 검토

<!-- codex-turn:01a04791-d975-7dd1-acb2-7494bbc26047 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이며 Jira를 조회하거나 변경하지 않았다. Learning Core Challenge backend·AI 연동 전용 Jira는 아직 없다.
- 작업 목표: AI 팀이 기존 계약서 내용대로 구현하기로 합의한 사실을 반영하고 추가로 동결해야 할 계약·제품·운영 항목을 구분한다.
- 승인 반영: `docs/contracts/ten-second-challenge-ai-api.md`를 Draft v0.1에서 v1·담당 팀 승인·미구현 상태로 변경했다. challenge 전용 endpoint·multipart audio·service credential·결과 schema·fencing·timeout/retry 계약은 승인 범위다.
- 문서 동기화: `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`의 AI Draft 표현을 v1 승인·구현 및 contract test 잔여 상태로 수정했다. `docs/contracts/ten-second-challenge-frontend-api.md`에는 서버 Callback deadline 120초와 최대 3 generation 확정을 반영했다.
- 남은 계약·제품 결정: 프론트 `aiResult`와 no-speech null 표현, MEMBER 전용/Guest preview, 날짜 rollover 보호 승인, foreground polling 상한, 사용자 audio 재생 여부, AI text field와 전체 Callback payload 길이 상한이 남아 있다.
- 권장값: no-speech는 `gradingStatus=completed`와 null 하위 field의 `aiResult` 객체, MEMBER 전용, 기존 rollover안 승인, foreground polling 60초, MVP audio 재생 제외, transcript/corrected answer 각 1000자·feedback 각 500자·Callback JSON 16 KiB를 권장한다. 아직 사용자 승인값으로 문서에 고정하지 않았다.
- 운영 준비: 실제 모바일 audio fixture, credential 생성·주입·rotation, private routing·TLS/security group, contract test와 retry/DLQ·staging latency 검증은 wire 계약 재결정이 아닌 구현·배포 gate로 남겼다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis, Python 시험 AI/Callback `user_id=examId`와 프론트의 기존 response shape은 변경하지 않았다. 애플리케이션·테스트 코드, Mongo 데이터, Jira, Git commit·push와 배포도 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 테스트: 문서 변경이라 Gradle 테스트는 실행하지 않았다. `git diff --check`가 통과했고 종료 전에 현재 marker 단일 포함을 확인한다.
- 다음 작업 전 확인 사항: 사용자가 잔여 권장안을 승인하면 프론트 계약의 `aiResult`·no-speech·polling·audioUrl과 AI 계약의 text/payload 상한을 같은 버전 기준으로 동결한다. 이후 Challenge backend·AI 구현 Jira와 공유 contract fixture를 만든다.

## 2026-08-28 — 10초 챌린지 프론트 v1 잔여 계약 확정

<!-- codex-turn:01a04795-e33d-72b0-b54c-2523b758b101 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이며 Jira를 조회하거나 변경하지 않았다. Learning Core Challenge backend·AI 연동 전용 Jira는 아직 없다.
- 작업 목표: `aiResult`에 DB 사전 정의 답안을 추가하고 no-speech에서도 이를 유지하며, MEMBER 전용과 앞서 제안한 잔여 권장안을 프론트·AI v1 계약으로 확정한다.
- 결과 DTO: `aiResult.referenceAnswer`를 추가했다. AI Callback field가 아니라 Learning Core가 attempt 생성 시 Mongo `questions[].referenceAnswer`에서 저장한 snapshot을 조립하며 정상 완료와 no-speech에서 항상 non-blank다.
- no-speech: `gradingStatus=completed`, `aiResult` non-null, `referenceAnswer` non-blank로 반환한다. transcript·verdict·correctedAnswer·feedback은 null이고 공개 `feedbackType`은 추가하지 않는다.
- 기존 참고 답안: 제출 직후 제공하는 top-level `question.referenceAnswer`는 유지한다. 완료 결과의 `aiResult.referenceAnswer`와 같은 snapshot 값이어야 하며 불일치하면 attempt snapshot을 authoritative 값으로 사용한다.
- 제품·프론트 확정: MEMBER 전용·Guest `403`, 기존 KST rollover 보호, foreground polling 최대 60초(처음 20초 2초·이후 5초), MVP 사용자 녹음 재생과 `audioUrl` 제외를 확정했다.
- AI 제한: transcript·corrected answer 각 1000자, meaning·grammar·pronunciation 각 500자, Callback JSON 전체 UTF-8 16 KiB로 제한하고 초과 시 `413 CALLBACK_PAYLOAD_TOO_LARGE`로 정했다.
- 변경 파일: `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/contracts/FRONTEND_API_HANDOFF.md`, `docs/contracts/ten-second-challenge-ai-api.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 변경했다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, S3·Redis, Python 시험 AI/Callback `user_id=examId`를 변경하지 않았다. 애플리케이션·테스트 코드, Mongo 데이터, Jira, Git commit·push와 배포도 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 테스트: 프론트·AI 계약의 모든 JSON code block을 Ruby JSON parser로 검증했고 통과했다. `git diff --check`도 통과했다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.
- 남아 있는 위험 요소: top-level과 `aiResult`에 referenceAnswer가 중복되므로 구현이 서로 다른 source를 조회하면 값이 어긋날 수 있다. 반드시 동일 attempt snapshot 하나로 두 field를 조립해야 한다.
- 다음 작업 전 확인 사항: Challenge backend·AI 구현 Jira를 만든 뒤 MEMBER authorization, attempt snapshot, no-speech projection, text/payload validation, polling과 audioUrl 비노출을 공유 contract fixture와 staging E2E로 검증한다.

## 2026-08-28 — 10초 챌린지 구현 착수 가능성 점검

<!-- codex-turn:01a0479c-1f0d-7180-9944-90b14c0bfbbd -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이며 Jira를 조회하거나 변경하지 않았다. Learning Core Challenge backend 전용 Jira는 아직 없다.
- 작업 목표: 승인된 프론트·AI v1 계약을 기준으로 실제 Learning Core 구현에 즉시 착수할 수 있는지 저장소 규칙과 준비 상태를 점검한다.
- 결론: 기술 계약은 구현 가능한 수준으로 준비됐다. 그러나 현재 `AGENTS.md`의 “현재 추가하지 않을 기능”에 10초 챌린지가 포함돼 있고 이 범위를 허용하는 명시적 Jira 예외가 없어 지금 애플리케이션 구현을 시작하면 저장소 규칙을 위반한다.
- 필수 선행 작업: Learning Core backend 구현 Jira를 만들고 해당 이슈에만 적용되는 AGENTS 명시적 예외를 추가해야 한다. 현재 계약 문서 변경은 사용자가 commit·push하고 전용 branch에서 구현하는 것이 안전하다.
- 구현 범위: content catalog/state와 KST day resolver, ChallengeAttempt·DB snapshot, S3 upload-url·submit, MEMBER authorization, GradingJob·AI request/callback fencing, result/history API, migration/index와 contract/integration test의 vertical slice다.
- 운영 준비: 실제 값을 문서나 코드에 넣지 않고 방향별 service credential 환경변수·Secrets Manager, private routing·TLS/security group, feature flag 기본 off와 staging E2E를 준비해야 한다.
- 변경 파일: 계약 승인 상태와 착수 blocker 기록을 위해 `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 변경했다. 애플리케이션·테스트 코드와 `AGENTS.md`는 변경하지 않았다.
- 유지한 계약: 프론트·AI v1, 기존 시험 API·DTO·`BaseResponse`, S3·Redis와 Python 시험 AI/Callback `user_id=examId` 계약을 변경하지 않았다. Secret과 Token은 기록하지 않았다.
- 테스트: 코드 변경이 없는 착수 점검이라 Gradle 테스트는 실행하지 않았다. 종료 전에 marker 단일 포함과 `git diff --check`를 확인한다.
- 다음 작업 전 확인 사항: Learning Core backend Jira 키와 그 키에 한정한 AGENTS 예외를 확정한 뒤 구현을 시작한다. 구현 완료 후 `./gradlew clean test`, 양쪽 contract fixture와 staging mobile/audio E2E를 수행한다.

## 2026-08-28 — AGENTS.md 10초 챌린지 구현 범위 승인 반영

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 키는 없으며 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이다. Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 사용자의 명시 결정에 따라 10초 챌린지를 Learning Core의 현재 추가 기능으로 허용하고 이후 구현자가 따라야 할 저장소 규칙을 확정한다.
- 제외 해제: `AGENTS.md`의 “현재 추가하지 않을 기능”에서 10초 챌린지를 제거했다. 이전 점검에서 확인한 저장소 범위 blocker는 해소됐다.
- 계약 기준: `docs/contracts/ten-second-challenge-frontend-api.md` v1, `docs/contracts/ten-second-challenge-ai-api.md` v1과 상세 결정서를 authoritative source로 지정했다.
- 구현 경계: 기존 시험 aggregate·retryCount·Job·AI Callback과 Challenge를 분리하고, 기존 시험 공개 API·DTO·BaseResponse·Redis·S3 key와 `user_id=examId` 계약을 변경하지 않도록 했다.
- Challenge 규칙: MEMBER·소유권, Mongo 콘텐츠와 referenceAnswer snapshot, 자동 KST Day 1·비순환 resolver, 1시간 attempt·분리 upload URL, M4A/AAC, no-speech `aiResult.referenceAnswer`, AI request/callback fencing·payload 상한과 secret·로그 비노출을 명시했다.
- 테스트·리뷰: catalog/date, authorization, attempt/S3, snapshot/no-speech, AI idempotency·stale callback을 Mock·fixture로 검증하고 전체 `./gradlew clean test`를 실행하도록 했다. 코드 리뷰 우선순위에도 Challenge 전용 8개 항목을 추가했다.
- 변경 파일: `AGENTS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 변경했다. 애플리케이션·테스트 코드는 변경하지 않았다.
- 유지한 계약: 승인된 프론트·AI v1와 기존 시험 계약을 변경하지 않았다. 실제 secret·credential과 사용자 음성·transcript를 기록하지 않았다.
- 테스트: 규칙·문서 변경이라 Gradle 테스트는 실행하지 않았다. 종료 전에 `git diff --check`와 10초 챌린지 제외 항목 제거·허용 섹션 존재를 확인한다.
- 남아 있는 위험 요소: 아직 Challenge backend·AI 구현 전용 Jira가 없어 작업 추적 범위가 넓을 수 있다. 구현은 vertical slice와 feature flag 기본 off로 진행하고 관련 없는 기존 시험 코드를 건드리지 않아야 한다.
- 다음 작업 전 확인 사항: 사용자가 구현을 요청하면 승인된 AGENTS와 v1 계약을 기준으로 application code·migration·test를 구현할 수 있다. Jira 생성은 권장하지만 더 이상 저장소 범위 blocker는 아니다.

## 2026-08-28 — staging·production 월 인프라 비용 추정

<!-- codex-turn:01a04738-52f5-7981-89fe-502b5e861de5 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 기존 AI 멱등성 관련 `TMI-25`는 비용 산정 범위가 아니다. Jira를 조회하거나 변경하지 않았다.
- 사용자 요청에 따라 서울 리전의 AWS 공식 가격과 MongoDB 공식 가격을 읽기 전용으로 확인하고 staging+production 월 비용을 계산했다. 확인 단가는 Fargate vCPU `$0.04656/hour`, memory `$0.00511/GB-hour`, ALB `$0.0225/hour + $0.008/LCU-hour`, public IPv4 `$0.005/address-hour`, NAT `$0.059/hour + $0.059/GB`, Atlas M10 시작 `$56.94/month`, Flex 최저 `$8/month`, ElastiCache Serverless for Valkey 시작 `$6/month`이다.
- 실제 확인된 AI Task `2 vCPU/4GB`를 사용하고 Identity/Learning Core `0.5 vCPU/1GB`, Billing `0.25 vCPU/0.5GB`, 환경별 Task 한 개·ALB 한 개·NAT 한 개, production Atlas M10, staging Flex와 낮은 초기 트래픽을 가정했다.
- 기준 결과는 월 `$515.82`, 환율 `1 USD=1,400 KRW`와 VAT 10% 가정 약 79만원이며 안전 예산은 월 80만~90만원이다. staging 시간제 운영·NAT 대체 비용 최적화안은 약 59만원, production Task 이중화·NAT 2AZ·Atlas M30 보수적 HA안은 약 164만원이다.
- `docs/codex/MONTHLY_INFRA_COST_ESTIMATE.md`를 추가하고 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 갱신했다. 외부 AI/provider 호출료, Apple/Google 수수료, Atlas backup/egress, 대량 S3·CloudWatch 사용은 합계에서 제외하고 실제 청구 보정에 필요한 inventory를 명시했다.
- 애플리케이션·설정·테스트 코드, AWS/MongoDB 리소스, 외부 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, Redis/S3와 retryCount 계약을 변경하지 않았고 Secret·Token을 조회하거나 기록하지 않았다.
- 분석·문서 작업이므로 Gradle 테스트는 실행하지 않았다. 공식 가격 페이지는 읽기 전용으로 확인했으며 `git diff --check`와 marker 단일 포함을 종료 전에 검증한다.

## 2026-08-28 — production 상시·staging 테스트 시 운영 비용 재산정

<!-- codex-turn:01a0473d-f452-783f-8573-998322083865 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자가 production만 24시간 운영하고 staging은 테스트할 때만 켠다고 확정했다. staging 월 40시간을 기본 가정으로 월 비용을 다시 계산했다.
- production Fargate는 약 `$134.71/month`, staging 네 서비스 Fargate는 40시간에 약 `$7.38`이다. staging ALB·NAT도 테스트 때 IaC로 생성·제거하는 최저 비용안은 전체 약 `$317.80`, 환율 1,400원과 VAT 10% 가정 약 49만원으로 안전 예산 월 50만~55만원이다.
- staging ECS Task만 `desiredCount=0`으로 내리고 ALB·NAT·Atlas Flex·Valkey를 유지하면 고정비가 남아 전체 약 `$383.49`, 약 59만원이다. 따라서 현재 현실적인 월 서버 예산을 50만~60만원으로 수정했다.
- ALB와 NAT Gateway는 ECS Task가 꺼져 있어도 남아 있으면 시간 요금이 계속 발생한다. staging 네트워크까지 매번 제거하려면 수동 작업이 아니라 IaC 재생성·삭제와 데이터 초기화가 필요하다.
- `docs/codex/MONTHLY_INFRA_COST_ESTIMATE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·설정·테스트 코드, AWS/MongoDB 리소스, 외부 API·DTO·AI/Callback·Redis/S3 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 분석·문서 작업이므로 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — production 상시·staging 필요시 운영 비용 기록 동기화

<!-- codex-turn:01a0473d-f452-75f2-adcc-031539a4d6eb -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 종료 훅 요구에 따라 이번 turn의 비용 재산정 기록을 WORKLOG 끝에 추가했다. production은 24시간, staging은 월 40시간 테스트 사용을 가정한다.
- staging ALB·NAT까지 필요시에만 IaC로 생성·제거하는 안은 약 `$317.80/month`, 환율 1,400원과 VAT 10% 가정 약 49만원이다. staging ECS만 중지하고 ALB·NAT를 유지하는 안은 약 `$383.49/month`, 약 59만원이다.
- 현실적인 월 서버 안전 예산은 50만~60만원이다. ECS Task 중지만으로 ALB·NAT 시간 요금은 중단되지 않는다는 전제와 외부 AI/provider 호출료 제외 범위는 기존 상세 산정과 동일하다.
- `docs/codex/CURRENT_STATE.md`와 `docs/codex/WORKLOG.md` 기록만 동기화했다. 애플리케이션·설정·테스트 코드, AWS/MongoDB 리소스, 외부 API·DTO·AI/Callback·Redis/S3 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — AI 축소·무료 Mongo 기준 월 비용 재산정

<!-- codex-turn:01a04741-71aa-7162-9d95-83149039f479 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자가 AI Task를 `2 vCPU/4GB`에서 `1 vCPU/2GB`로 낮추고 MongoDB는 현재 무료 plan을 유지하며 기타 예비비는 제외해 달라고 요청했다.
- 서울 Fargate 단가 기준 AI production 24시간 비용은 `$41.45/month`이고 Identity·Learning Core·Billing까지 포함한 production Fargate는 `$93.26`이다. 동일 크기의 staging을 월 40시간 실행하면 Fargate 비용은 `$5.11`이다.
- staging ALB·NAT도 테스트 때만 IaC로 생성·제거하면 Valkey 두 환경 최소 `$12`를 포함한 전체는 약 `$184.14`, 환율 1,400원과 VAT 10% 가정 약 28.4만원으로 월 29만~30만원 예산이다. staging ECS만 중지하고 ALB·NAT를 유지하면 약 `$249.83`, 약 38.5만원으로 월 39만~40만원이다.
- 현재 실제 AI Task Definition은 `2 vCPU/4GB`이고 API와 worker 4개가 한 Task에서 실행된다. `1 vCPU/2GB`는 아직 검증되지 않은 목표값이므로 적용 전 staging에서 CPU throttling, peak RSS/OOM, queue backlog, 동시 채점과 처리 p95를 확인해야 한다.
- `docs/codex/MONTHLY_INFRA_COST_ESTIMATE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·Task Definition·AWS/MongoDB 리소스, 외부 API·DTO·AI/Callback·Redis/S3 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 분석·문서 작업이므로 Gradle 테스트는 실행하지 않았으며 산술 재검증, `git diff --check`와 marker 단일 포함을 종료 전에 검증한다.

## 2026-08-28 — 토스트 가격 기준 무제한 멤버십 BEP 산정

<!-- codex-turn:01a04748-8ba9-7de3-bcfe-dd6498f32265 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자 제공 토스트 앱 화면에서 24시간 9,000원, 3일 19,000원, 7일 29,000원, 2주 49,000원, 4주 69,000원의 일회성 무제한 멤버십 가격을 확인했다. 사용자가 말한 한 달은 화면상 4주(28일)로 해석했다.
- 월 고정비 380,000원, 모의고사 1회당 AI API 250원, 구매자당 하루 평균 1회 응시, 표시가격 VAT 10%와 IAP 15% 차감 가정으로 상품 단독 판매 BEP를 계산했다. 24시간 57건, 3일 28건, 7일 19건, 2주 12건, 4주 9건이다.
- 수수료 미반영 단순 BEP는 44·21·14·9·7건이지만 실제 운영 판단에는 부적합하다. IAP 30% 민감도는 70·34·23·14·11건이다. 혼합 판매는 상품별 `판매수 × 공헌이익` 합계가 380,000원 이상인 지점이 BEP다.
- 무제한 상품은 구매자당 평균 시험 수 없이는 단일 BEP가 정해지지 않으므로 실제 사용량 공식과 4주 56회·24시간 5회 예시를 추가했다. 무료시험·추천·쿠폰·환불·광고비와 외부 운영비는 제외했다.
- `docs/codex/SUBSCRIPTION_BEP_ESTIMATE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·Billing·Task Definition·AWS/MongoDB 리소스, 외부 API·DTO·AI/Callback·Redis/S3 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 분석·문서 작업이므로 Gradle 테스트는 실행하지 않았다. 산술, `git diff --check`와 marker 단일 포함을 종료 전에 검증한다.

## 2026-08-28 — production 실제 Task 크기와 일 $12.6 비용 차이 분석

<!-- codex-turn:01a04747-90c7-7a42-ac17-0e494c530ca7 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자가 production의 실제 크기를 Identity `1 vCPU/3GB`, Learning Core `1 vCPU/3GB`, AI `2 vCPU/4GB`이며 관측 비용은 `$12.6/day`라고 정정했다. 이전 표의 `$20.72/$20.72/$41.45`는 이전 작은 크기의 월 비용이라 현재 크기와 불일치했다.
- 서울 Fargate 단가로 Task 각 한 개의 정확한 비용은 Identity `$45.18/month`, Learning Core `$45.18`, AI `$82.90`, 합계 `$173.26/month` 또는 `$5.696/day`다. 관측값과 하루 `$6.904` 차이가 난다.
- 세 ECS Service가 각각 desired/running count 2라면 compute가 `$11.392/day`이고 ALB base, public IPv4 두 개와 평균 0.25 LCU를 합친 약 `$12.22/day + logs/data`가 되어 관측 `$12.6/day`와 매우 가깝다. 다만 Cost Explorer 분해 전에는 가설이다.
- 확정 확인 항목은 ECS Service별 desired/running/deployment Task 수와 Cost Explorer의 Fargate vCPU·GB hours, NAT hours/bytes, ALB hours/LCU, public IPv4, CloudWatch usage type이다. 분해 전 월 고정비는 실제 관측 `$12.6×30=$378`, 환율 1,400원 기준 VAT 전 약 529,200원을 우선한다.
- `docs/codex/MONTHLY_INFRA_COST_ESTIMATE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·Task Definition·AWS 리소스, 외부 API·DTO·AI/Callback·Redis/S3 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 분석·문서 작업이므로 Gradle 테스트는 실행하지 않았다. 산술, `git diff --check`와 marker 단일 포함을 종료 전에 검증한다.

## 2026-08-28 — production 일 $12.6 관측 비용 설명 보완

<!-- codex-turn:01a0474b-72d4-7193-a857-8b5f5d3a92d0 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자에게 이전 비용표가 현재 Task 크기와 과거 단가를 혼용해 잘못됐음을 명시하고, 서울 Fargate 기준 Task 각 1개 실행 시 compute가 약 `$5.696/day`임을 설명했다.
- 실제 `$12.6/day`는 각 서비스 Task 2개 실행과 ALB·public IPv4·LCU·로그 비용 조합으로 설명될 가능성이 높지만, 이는 ECS desired/running count와 Cost Explorer usage type을 확인하기 전까지 가설임을 명시했다.
- 실제 관측 run rate는 월 `$378`, 환율 1,400원 기준 VAT 전 약 529,200원이며 원인 분해 전에는 기존 38만원보다 이 값을 운영 기준으로 삼도록 안내했다.
- 이번 후속 작업은 기록 동기화뿐이며 애플리케이션·설정·테스트 코드, AWS 리소스, 외부 API·DTO·AI/Callback·Redis/S3 계약을 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — production 관측 비용 $1.26 재정정

<!-- codex-turn:01a0474e-6693-7380-9cd1-bd2f16492912 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자가 관측 비용의 소수점을 정정하여 `$12.6/day`가 아니라 `$1.26`이라고 확인했다.
- 단순 월 환산은 `$37.80`, 환율 1,400원 기준 VAT 전 약 52,920원이지만, 명시된 세 Task가 각 1개씩 24시간 실행될 때 Fargate compute만 `$5.696/day`이므로 `$1.26`을 완전한 하루 총비용이나 고정비로 확정할 수 없다.
- Cost Explorer의 조회 기간, 서비스·usage type 필터, cost type, credit·discount와 ECS Task 실제 실행 시간을 확인해야 한다. 분해 전에는 크기 기준 Fargate compute `$173.26/month`에 네트워크·로그 비용을 더한 값을 계획 기준으로 유지한다.
- 비용 문서와 CURRENT_STATE만 정정했고 애플리케이션·테스트 코드, AWS 리소스, 외부 API·DTO·AI/Callback·Redis/S3 계약은 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — 단기 이용권 집중 사용을 반영한 BEP 보정

<!-- codex-turn:01a04750-efcd-77b0-bf68-586f4eaf7175 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 짧은 무제한 이용권일수록 구매 직후 응시가 집중된다는 가정으로 평균 응시를 24시간 4회, 3일 8회, 7일 14회, 2주 21회, 4주 28회로 보정했다.
- VAT 10%·IAP 15%, 월 고정비 380,000원, 완료 시험당 AI 250원 기준 공헌이익은 5,955원·12,682원·18,909원·32,614원·46,318원이며 월 BEP는 64건·30건·21건·12건·9건이다.
- 24시간권은 평균 3~5회에서 BEP 62~67건, 3일권은 평균 6~10회에서 29~32건이다. 이는 출시 전 계획값이므로 출시 후 상품별 구매 cohort의 completed exam 평균·p95로 교체해야 한다.
- `docs/codex/SUBSCRIPTION_BEP_ESTIMATE.md`와 CURRENT_STATE만 갱신했으며 애플리케이션·AWS·Jira·외부 계약은 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 문서·산술 작업이라 Gradle 테스트는 실행하지 않았으며 산술, `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — 단기권 집중 사용 BEP 종료 기록 동기화

<!-- codex-turn:01a04750-114e-73c1-ae17-f970e7f7f276 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 종료 훅 요구에 따라 이번 turn의 단기 이용권 집중 사용 BEP 보정 결과를 WORKLOG 끝에 별도 기록했다.
- 기준 평균 응시는 24시간 4회, 3일 8회, 7일 14회, 2주 21회, 4주 28회이며 VAT 10%·IAP 15%·월 고정비 380,000원·AI 250원/회 기준 BEP는 64건·30건·21건·12건·9건이다.
- 비용 문서와 CURRENT_STATE 기록만 동기화했으며 애플리케이션·AWS·Jira·외부 계약을 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — 조직 계정 실제 단가 기반 4서비스 축소 비용 추정

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- `1/2, 1/2, 1/1, 1/2`를 Identity·Learning Core·Billing·AI 순서의 vCPU/GB로 해석했다.
- 조정 후 정상 단가 자원비는 현재 `1/3 + 1/3 + 2/4` 구성의 93.54%다. 조직 계정 실제 관측 `$1.26/day`와 동일한 정산 효과가 유지되면 약 `$1.179/day`, `$35.36/30일`, 환율 1,400원·VAT 포함 약 54,500원이다.
- 조직 혜택이 사라질 경우 compute 정상 단가는 `$162.07/month`, 환율·VAT 적용 약 249,600원이며 ALB·NAT·IPv4·로그·전송료는 별도다. Organizations 자체가 자동 할인을 의미하지 않으므로 실제 credit·Savings Plans·private pricing·cost type 확인이 필요하다.
- 비용 문서와 CURRENT_STATE만 갱신했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 문서·산술 작업이라 Gradle 테스트는 실행하지 않았으며 산술과 `git diff --check`를 검증한다.

## 2026-08-28 — 조직 계정 단가 기반 축소 비용 종료 기록

<!-- codex-turn:01a04753-22e2-7502-a9f5-4651e2a8fd32 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 종료 훅 요구에 따라 Identity `1 vCPU/2GB`, Learning Core `1 vCPU/2GB`, Billing `1 vCPU/1GB`, AI `1 vCPU/2GB` 조정 비용 추정 결과를 WORKLOG 끝에 기록했다.
- 현재 조직 계정 관측 단가 효과가 유지되면 약 `$1.179/day`, `$35.36/30일`, 환율 1,400원·VAT 포함 약 54,500원이다. 정상 단가 compute는 `$162.07/month`, 환율·VAT 적용 약 249,600원이며 네트워크·로그는 별도다.
- 비용 문서와 CURRENT_STATE 기록만 동기화했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — 조정 사양 기준 전체 서버비 재계산

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 조정 사양 `1/2, 1/2, 1/1, 1/2`와 조직 계정 실제 관측 `$1.26/day`를 기준으로 서버비를 다시 계산했다.
- `$1.26`이 AWS 전체 비용이면 production 약 `$35.36/month`, staging 10% 여유 포함 VAT 기준 약 6만원이며 운영 예산은 월 7만원이다.
- `$1.26`이 Fargate compute 필터 결과라면 주변 인프라를 더해 staging ALB·NAT 시간제 생성 시 VAT 포함 약 19만원, 상시 유지 시 약 29만원이다. Cost Explorer의 필터 없는 Service별 합계로 구분해야 한다.
- 비용 문서와 CURRENT_STATE만 갱신했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 문서·산술 작업이라 Gradle 테스트는 실행하지 않았으며 산술과 `git diff --check`를 검증한다.

## 2026-08-28 — 조정 사양 전체 서버비 종료 기록

<!-- codex-turn:01a04755-0c1b-7f93-92d5-b9c6c2bebc46 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 종료 훅 요구에 따라 조정 사양 기준 전체 서버비 재계산 결과를 WORKLOG 끝에 기록했다.
- `$1.26/day`가 AWS 전체 합계면 staging 여유 포함 운영 예산은 월 약 7만원이고, Fargate만의 비용이면 주변 인프라를 포함해 staging 네트워크 시간제 생성 약 19만원 또는 상시 유지 약 29만원이다.
- 비용 문서와 CURRENT_STATE 기록만 동기화했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — AWS 전체 관측 비용 기준 서버비 최종 재계산

<!-- codex-turn:01a04756-facd-7cb0-87fe-2bede7713b5c -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자가 `$1.26/day`가 Fargate뿐 아니라 ALB·NAT·로그 등 다른 항목까지 포함한 AWS 전체 비용이라고 확정했다.
- 조정 사양에서 compute 정상 단가는 6.46% 감소하지만 고정비는 유지되므로 전체 비용은 `$1.179~1.260/day`, `$35.36~37.80/30일`, 환율 1,400원·VAT 포함 약 54,500~58,200원이다. compute 비중 70% 기준은 약 55,600원이다.
- 운영 예산은 청구 지연·환율·staging 테스트 여유를 포함해 월 7만원으로 유지한다. 기존 월 19만~29만원 시나리오는 Fargate 외 비용을 중복 가산한 것이므로 폐기했다.
- 비용 문서와 CURRENT_STATE만 갱신했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 문서·산술 작업이라 Gradle 테스트는 실행하지 않았으며 산술, `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — 월 7만원 서버 운영 예산 표 작성

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 조정 사양의 월 운영 예산 70,000원을 production 예상액 55,600원, staging 테스트 여유 5,600원, 환율·청구 지연·사용량 변동 완충액 8,800원으로 표로 분해했다.
- 완충액은 확정 청구 항목이 아니라 환율 상승, Cost Explorer 반영 지연, 배포 중 Task 중복과 소량 로그·전송량 변동을 흡수하는 예산이다.
- 비용 문서와 CURRENT_STATE만 갱신했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 문서 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`를 검증한다.

## 2026-08-28 — AWS 전체 비용 $5.49 기준 최종 재계산

<!-- codex-turn:01a04759-200b-7991-8588-b8bb70aeee34 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자가 다른 항목을 모두 포함한 AWS 전체 비용을 `$1.26/day`가 아니라 `$5.49/day`로 재정정했다.
- 조정 사양에서 compute는 최대 6.46% 줄지만 고정비는 유지되므로 production 전체 비용은 `$5.135~5.490/day`, `$154.06~164.70/30일`, 환율 1,400원·VAT 포함 약 237,300~253,600원이다.
- compute 비중 70% 기준 production 약 242,200원, staging 10% 약 24,200원, 환율·청구 지연·사용량 변동 완충액 약 33,600원으로 월 운영 예산을 300,000원으로 수정했다. 과거 월 7만원 예산은 폐기한다.
- 비용 문서와 CURRENT_STATE만 갱신했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 문서·산술 작업이라 Gradle 테스트는 실행하지 않았으며 산술, `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — 월 30만원 서버 예산 최종 표 정리

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 서버 예산을 production 242,200원, staging 24,200원, 변동 대응 33,600원으로 나누어 월 300,000원으로 확정한 관리 표를 작성했다.
- 완료 모의고사 1회당 AI API 250원은 서버 고정비에 포함하지 않고 별도 변동비로 유지했다.
- 비용 문서와 CURRENT_STATE만 갱신했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 문서 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`를 검증한다.

## 2026-08-28 — 월 30만원 서버 예산표 종료 기록

<!-- codex-turn:01a0475a-2bcc-79e1-b170-b34293edc771 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 종료 훅 요구에 따라 월 서버 운영 예산 300,000원 표 작성 결과를 WORKLOG 끝에 기록했다.
- 구성은 production 242,200원, staging 24,200원, 변동 대응 33,600원이며 AI API 250원/완료 모의고사는 별도 변동비다.
- 비용 문서와 CURRENT_STATE 기록만 동기화했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — 고정비 30만원·AI 실측 275.28원 기준 BEP 재계산

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자가 제공한 STT·Azure·LLM·최종 요약 비용 합계인 모의고사 1회당 275.28원과 월 서버 고정비 300,000원을 적용했다.
- 단기권 집중 사용량 4·8·14·21·28회, VAT 10%·IAP 15% 기준 공헌이익은 약 5,854원·12,480원·18,555원·32,083원·45,610원이며 월 BEP는 52건·25건·17건·10건·7건이다.
- IAP 30% 민감도는 65건·31건·21건·12건·9건이다. 무료 시험·추천·쿠폰·환불·광고비는 제외했으며 실제 cohort 사용량으로 추후 보정해야 한다.
- BEP 문서와 CURRENT_STATE만 갱신했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았다. Secret·Token도 기록하지 않았다.
- 문서·산술 작업이라 Gradle 테스트는 실행하지 않았으며 산술과 `git diff --check`를 검증한다.

## 2026-08-28 — 고정비 30만원·AI 실측 BEP 종료 기록

<!-- codex-turn:01a0475c-3e3b-75c0-9177-448a96ac1cdc -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 종료 훅 요구에 따라 월 고정비 300,000원과 모의고사당 AI 실측 275.28원 기준 BEP 재계산 결과를 WORKLOG 끝에 기록했다.
- 단기권 집중 사용과 VAT 10%·IAP 15% 기준 BEP는 24시간 52건, 3일 25건, 7일 17건, 2주 10건, 4주 7건이며 IAP 30%에서는 65건·31건·21건·12건·9건이다.
- BEP 문서와 CURRENT_STATE 기록만 동기화했으며 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-28 — 전체 앱 프론트 API 인계서 재점검

<!-- codex-turn:01a04797-fa12-7192-8c0d-b6c6bb402085 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 완료 상태로 기록된 Billing `TMI-110`, `TMI-112`, `TMI-113`과 Challenge 관련 `TMI-102`, `TMI-105`, `TMI-106`을 현재 구현 경계 확인에 사용했다. Learning Core withdrawal `TMI-109`와 후속 Identity publisher `TMI-111`의 내부 endpoint 경계도 유지했으며 Jira를 조회하거나 변경하지 않았다.
- 작업 목표: `docs/contracts/FRONTEND_API_HANDOFF.md`를 세 저장소의 현재 모든 Controller·DTO·Security 설정과 승인된 Challenge v1 계약에 역대조해 누락·시차·과도한 보장을 찾고 정정한다.
- 재점검 결과: Identity 프론트 API 17개와 Learning Core 프론트 API 11개는 누락이 없었다. Billing 공개 프론트 API는 0개이며 앱이 직접 호출해서는 안 된다는 기존 경계도 유효하다.
- 정정 내용: Billing은 `TMI-110` trial eligibility consumer, `TMI-112`의 `TrialClaim`·`FREE_EXAM_ONCE` initial reserve, `TMI-113`의 confirm/cancel/status·expiry lifecycle까지 구현돼 있어 “TrialClaim·Reservation 미구현” 문구를 현재 상태로 교체했다. `/internal/v1/reservations`, confirm, cancel, status 네 route를 프론트 호출 금지 표에 추가했다.
- Challenge 정렬: 프론트·AI 계약이 Draft가 아니라 승인된 v1·미구현 상태이므로 잔존 Draft/초안 표현을 제거했다. MEMBER, M4A/AAC-LC profile, 최대 2 MiB, AI result와 polling 등 승인 항목은 유지했다.
- 추가 위험 확인: 기존 모의고사 Presigned PUT은 코드상 5분 signature지만 DTO `expiresIn=60`이고, `.wav` Object Key를 사용하면서 Presigned 요청이 업로드 Content-Type이나 실제 codec을 고정·응답하지 않는다. 프론트가 만료·포맷을 과신하지 않도록 문서에 명시했다.
- 변경 파일: `docs/contracts/FRONTEND_API_HANDOFF.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드, 다른 계약 문서, Identity·Billing 저장소와 Jira는 변경하지 않았다.
- 유지한 계약: 기존 공개 URL·Method·Parameter·DTO·BaseResponse, userId 비전달, S3 Object Key, Redis, Python AI callback과 `user_id=examId` 계약을 변경하지 않았다. Secret·실제 Token·운영 credential을 기록하지 않았다.
- 테스트: 문서 재점검이므로 Gradle 테스트는 실행하지 않았다. 세 저장소의 Controller mapping 수, Security allowlist/default deny, DTO 소스와 계약 문서를 읽기 전용으로 대조했으며 종료 전에 `git diff --check`, trailing whitespace, 링크 대상과 marker 단일 포함을 검증한다.
- 남은 production 경계: Learning Core의 필수 lowercase UUID v4 `Idempotency-Key`와 Billing client/saga·reconciliation, Billing AttemptGroup event·owner rebind, Lattice/IAM/SG staging E2E, Challenge API·AI 양쪽 구현과 contract test가 남아 있다.

## 2026-08-28 — 1차 업데이트 진행 체크리스트 최신화

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 완료 기록이 있는 Identity `TMI-109`·`TMI-111`, Billing `TMI-110`·`TMI-112`·`TMI-113`과 Challenge `TMI-102`·`TMI-105`·`TMI-106`, Identity 후속 `TMI-114`를 현재 구현 경계 판정에 사용했다. Atlassian 실시간 조회는 connector 미설치 403으로 불가능해 저장소의 최근 Jira 확인 기록을 사용했으며 Jira는 변경하지 않았다.
- 작업 목표: 사용자가 전달한 과거 1차 업데이트 체크리스트를 현재 Identity·Billing·Learning Core 코드와 병합·테스트 기록에 맞게 갱신하고, 프론트가 읽을 수 있는 기존 API 인계서와 연결한다.
- 변경 파일: `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`를 추가하고 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 동기화했다. 애플리케이션 코드, 기존 공개 API·DTO, Identity·Billing 저장소와 Jira는 변경하지 않았다.
- 변경한 상태 판정: `TMI-109`는 consumer/gate 분리 flag·marker race 409·startup Transaction probe와 402개 테스트 기록을 근거로 완료, `TMI-111`은 outbox·publisher·bounded backfill과 109 suite·591개 테스트 기록을 근거로 완료로 정정했다. Billing은 eligibility consumer뿐 아니라 TrialClaim·`FREE_EXAM_ONCE` grant/ledger와 Reservation reserve/confirm/cancel/status/expiry까지 구현된 것으로 반영했다.
- 유지한 외부 계약: 기존 공개 URL·Method·Parameter·DTO·`BaseResponse`, 실제 userId 비전달, 기존 시험 S3·Redis·Python AI Callback과 `user_id=examId` 계약은 변경하지 않았다. Secret·Token·운영 credential도 기록하지 않았다.
- 현재 production 차단 항목: Learning Core Billing client·필수 lowercase UUID v4 `Idempotency-Key`·reserve/commit/confirm saga, Billing AttemptGroup terminal event·owner rebind, Challenge backend·AI 구현, 실제 모바일 SNS와 workload/Lattice·replica set·multi-instance staging E2E, response-loss·rollback·canary 검증이 남아 있다.
- 테스트: 문서 작업이라 Gradle 테스트는 실행하지 않았다. 문서 링크 대상, trailing whitespace, 기존 turn marker 단일 포함과 `git diff --check`를 종료 전에 검증한다.
- 다음 작업 전 확인 사항: `TMI-114`의 1차 출시 gate 여부를 정하고, Billing AttemptGroup/owner rebind와 Learning Core Billing saga를 먼저 연결한 뒤 Challenge backend Jira·구현과 서비스 간 staging E2E를 진행한다. Git commit과 push는 사용자가 수행한다.

## 2026-08-28 — 1차 업데이트 진행 체크리스트 종료 기록

<!-- codex-turn:01a0479c-e790-70a3-b7c5-f2e6ab6b8626 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: Identity `TMI-109`·`TMI-111`, Billing `TMI-110`·`TMI-112`·`TMI-113`, Challenge `TMI-102`·`TMI-105`·`TMI-106`, Identity 후속 `TMI-114`를 진행 상태 근거로 기록했다. Jira를 변경하지 않았다.
- 종료 결과: `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`에 현재 1차 업데이트 상태를 체크리스트로 정리하고 `docs/codex/CURRENT_STATE.md`와 `docs/codex/WORKLOG.md`를 동기화했다.
- 상태 요약: Identity withdrawal producer/consumer와 Billing TrialClaim·`FREE_EXAM_ONCE`·Reservation lifecycle 기반은 완료됐지만 Learning Core Billing saga, Billing AttemptGroup/owner rebind, Challenge backend·AI 구현, 실제 모바일·workload·staging E2E와 canary가 남아 production 출시는 차단 상태다.
- 외부 계약: 애플리케이션 코드와 기존 공개 API·DTO·`BaseResponse`, S3·Redis·Python AI의 `user_id=examId` 계약을 변경하지 않았다. Secret·Token·운영 credential을 기록하지 않았다.
- 검증: 링크 대상과 trailing whitespace를 확인했고 `git diff --check`가 통과했다. 문서 변경만 있어 Gradle 테스트는 실행하지 않았다.
- 다음 작업: `TMI-114`의 출시 gate 여부를 확정하고 Billing AttemptGroup/owner rebind, Learning Core Billing saga, Challenge backend, staging 종단 검증 순으로 진행한다. Git commit과 push는 사용자가 수행한다.

## 2026-08-28 — AGENTS 10초 챌린지 범위 승인 종료 기록 동기화

<!-- codex-turn:01a0479d-f036-7ef1-855f-0ac8a70af0f5 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 키는 없으며 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이다. Jira를 조회하거나 변경하지 않았다.
- 완료 상태: `AGENTS.md`의 현재 제외 목록에서 10초 챌린지를 제거하고 프론트·AI v1 기반의 구현 허용, domain 격리, MEMBER·콘텐츠·attempt/S3·AI/Callback·테스트·리뷰 규칙을 추가했다.
- 착수 상태: 저장소 범위 blocker는 해소됐으며 사용자의 구현 요청이 있으면 application code·migration·test 작업을 시작할 수 있다. 전용 Jira는 추적을 위해 권장하지만 필수 blocker는 아니다.
- 유지한 계약: 기존 시험 API·DTO·`BaseResponse`, retryCount, Redis·S3와 Python 시험 AI/Callback `user_id=examId` 계약은 변경하지 않았다.
- 변경하지 않은 항목: 애플리케이션·테스트 코드, Mongo 데이터, Jira, Git commit·push와 배포는 변경하지 않았다. Secret과 Token을 기록하지 않았다.
- 테스트 결과: 규칙·문서 변경이라 Gradle 테스트는 실행하지 않았다. `git diff --check`는 통과했다.
- 다음 작업 전 확인 사항: 승인된 v1 계약과 AGENTS 규칙에 따라 Challenge를 feature flag 기본 off의 vertical slice로 구현하고 전체 Gradle·contract·staging E2E를 수행한다.

## 2026-08-28 — Challenge 제외 기존 시험·Billing 잔여 구현 재확인

<!-- codex-turn:01a04856-1523-7f23-acea-0ef42f5c543e -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 완료 근거는 Learning Core `TMI-109`, Identity `TMI-111`, Billing `TMI-110`·`TMI-112`·`TMI-113`이며 후속 Identity 계획은 `TMI-114`다. Challenge 관련 기존 키는 `TMI-102`, `TMI-105`, `TMI-106`이다. Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 사용자가 제시한 Learning Core 잔여 목록이 Challenge를 제외한 현재 실제 구현 공백과 일치하는지 Learning Core·Identity·Billing 코드와 최신 체크리스트에 대조한다.
- Learning Core 확인: `POST /api/v1/exams`는 `Idempotency-Key` header 없이 바로 `createExamSession()`을 호출하고, Session 생성은 Billing reserve/confirm 없이 즉시 수행된다. Billing client·reservation/operation metadata·UserMerged·AttemptGroup event·reconciliation 코드는 없다.
- Identity 확인: UserMerged outbox·publisher는 구현돼 있지만 Learning Core 수신 consumer가 없다. 따라서 학습 데이터 owner 이동과 source actor deny 수렴은 남은 구현이 맞다.
- Billing 확인: TrialClaim·FREE_EXAM_ONCE grant/ledger, Reservation reserve/confirm/cancel/status·expiry와 AttemptGroup/AttemptSession 기반은 구현돼 있다. 남은 것은 Learning Core client/saga와 AttemptGroup event consumer, owner rebind, repair/reconciliation 및 실제 service transport다.
- 목록 판정: `UserMerged` consumer, Billing client+saga, 필수 공개 시험 생성 `Idempotency-Key`와 replay, AttemptGroup outbox/publisher와 R3 연결, Billing 장애 reconciliation은 모두 실제 잔여 구현이 맞다. 채점 dispatch의 기존 `Idempotency-Key`는 시험 생성 operation key가 아니다.
- 범위 보완: 이 목록은 Learning Core 기존 시험 연동 기준이다. 1차 출시 전체에는 Billing owner rebind, `TMI-114` 포함 여부·구현, 실제 모바일 SNS/phone, workload/Lattice/IAM/SG, replica-set·multi-instance, response-loss·rollback·canary E2E도 남아 있다.
- 변경 파일: 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·테스트 코드와 Identity·Billing 저장소는 수정하지 않았다.
- 유지한 계약: 기존 공개 API·DTO·`BaseResponse`, 실제 userId 비전달, S3·Redis와 Python 시험 AI/Callback `user_id=examId` 계약을 변경하지 않았다. Secret과 Token을 기록하지 않았다.
- 테스트: 읽기 전용 코드·문서 대조라 Gradle 테스트는 실행하지 않았다. 종료 전에 marker 단일 포함과 `git diff --check`를 확인한다.
- 다음 작업 전 확인 사항: 구현 순서는 Billing AttemptGroup consumer → Billing owner rebind → Learning Core Idempotency-Key·Reservation saga·AttemptGroup outbox·reconciliation → service transport와 staging E2E가 의존성상 안전하다. `TMI-114`는 1차 출시 gate인지 별도 결정이 필요하다.

## 2026-08-28 — 다음 작업 Billing AttemptGroup 상태 event consumer 계획

<!-- codex-turn:01a04858-ea2d-7f03-a80c-8ccca8d3420c -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다. 선행 완료 근거는 Billing `TMI-110`·`TMI-112`·`TMI-113`이다.
- 다음 구현 작업은 Billing의 `POST /internal/v1/attempt-group-events` consumer vertical slice로 선정했다. 기존 `AttemptGroup`·`AttemptSession`과 Reservation 기반은 있으나 상태 event 수신 코드가 없어 실제 상태가 `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE`로 수렴하지 않는 것이 근거다.
- 구현 범위는 schema v1 strict decode와 16 KiB 제한, event·enum·UTC 시각 검증, `eventId + canonical digest` inbox 멱등성, active Session fencing, group/session version CAS, 상태 전이·terminal 보호, workload route/security 설정과 transaction·동시성·중복·stale·역순 테스트다.
- 정책 권장안은 terminal evidence가 확인되면 `OPEN`에서도 전진 수렴을 허용하고 `COMPLETED`는 다시 열지 않으며, 존재하지 않는 group/session은 선행 projection 지연으로 보아 retryable 응답, owner 불일치는 계약 위반으로 non-retryable 격리, stale Session은 `204` 성공 no-op으로 처리하는 것이다. `RETAKE_AVAILABLE.failureCode`는 저 cardinality allowlist만 허용한다.
- 이번 계획에서는 Learning Core outbox/publisher, Billing owner rebind, Reservation saga·reconciliation, 실제 AWS Lattice 배포와 결제 기능을 제외했다. 애플리케이션 코드와 외부 API 계약은 변경하지 않았다.
- 분석·계획 작업이라 Gradle 테스트는 실행하지 않았다. 종료 전에 문서 형식과 `git diff --check`, marker 단일 포함을 검증한다. Git commit과 push는 사용자가 수행한다.

## 2026-08-28 — PLAN-005 AttemptGroup 상태 event consumer 계획서 작성

<!-- codex-turn:01a0485b-f6bb-7d93-8244-d326b71900f3 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira는 생성하지 않았고 기존 Jira 상태도 변경하지 않았다. Billing 선행 완료는 `TMI-113`, 독립된 PLAN-004 Jira는 `TMI-115`다.
- 작업 목표: 앞서 승인된 Billing AttemptGroup 상태 event consumer 방향을 구현 가능한 상세 계획서로 작성한다.
- 변경 파일: Billing `docs/plans/PLAN-005-attempt-group-status-event-consumer.md`와 Billing·Learning Core의 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·ADR·계약·Jira·AWS는 변경하지 않았다.
- 계획 내용: strict schema v1·16 KiB, canonical digest, shared inbox, group/session/owner fencing, 상태 전이와 terminal 보호, Mongo Transaction·version CAS, default-off route/security, 오류·retry·privacy metric과 replica-set 동시성 테스트를 단계별로 고정했다.
- 세부 결정: terminal evidence는 `OPEN`에서도 직접 전진, stale/terminal 역행은 204, missing projection은 retryable 503, 구조적 target conflict는 non-retryable 409로 처리한다. RETAKE failureCode는 저 cardinality 네 값만 초안 allowlist로 둔다.
- 유지한 계약: 기존 공개 API·DTO·`BaseResponse`, 실제 userId 비전달, 시험 S3·Redis·Python AI Callback과 `user_id=examId`, Billing Reservation·Claim·grant·ledger 계약을 변경하지 않았다.
- 테스트: 문서 계획 작업이라 Gradle 테스트는 실행하지 않았다. Billing 실제 entity/repository/inbox/security/index와 ADR·통합 계약을 읽기 전용으로 대조했고 종료 전에 두 저장소 `git diff --check`, 계획서 구조와 marker 단일 포함을 검증한다.
- 위험 요소: sequence 없는 상충 terminal event는 먼저 commit된 terminal이 승리하므로 Learning Core publisher contract test가 필요하다. shared inbox 일반화는 기존 Identity revision dedupe 전체 회귀를 반드시 통과해야 한다.
- 다음 작업: 사용자가 PLAN-005를 승인하면 Jira 생성을 별도로 승인받고, 구현 전 Phase 0 계약 보정 후 Step 1부터 Billing 코드 구현을 시작한다. Git commit과 push는 사용자가 수행한다.

## 2026-08-28 — PLAN-005 계획서 종료 기록 동기화

<!-- codex-turn:01a0485b-8a41-7943-9e78-492ec39a17ac -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira는 생성하지 않았고 상태도 변경하지 않았다. 관련 기존 키는 Billing `TMI-113`, 독립 계획 `TMI-115`다.
- 종료 결과: Billing `docs/plans/PLAN-005-attempt-group-status-event-consumer.md`에 AttemptGroup 상태 event consumer의 wire, 멱등성, owner resolution, 상태 전이, Transaction·CAS, security, 오류·retry, 테스트와 production gate를 작성했다.
- 정합성 보정: `AttemptGroup.subjectRefId`와 실제 `userId`를 직접 비교하지 않고 active·unexpired `BillingSubjectLink`로 owner를 해석하도록 계획했다. retention 뒤 mapping은 복원하지 않고 stale 처리한다.
- 변경 범위: Billing 계획서와 Billing·Learning Core의 CURRENT_STATE/WORKLOG 문서만 변경했다. 애플리케이션·ADR·외부 계약·Jira·AWS·Git commit/push는 변경하지 않았다.
- 유지한 계약: 기존 공개 API·DTO·`BaseResponse`, 시험 S3·Redis·Python AI Callback의 `user_id=examId`, Billing Reservation·Claim·grant·ledger 계약을 유지했다. Secret과 Token을 기록하지 않았다.
- 테스트 결과: 문서 작업이라 Gradle 테스트는 실행하지 않았다. 두 저장소의 `git diff --check`, Markdown code fence 짝과 이번 marker 단일 포함을 검증한다.
- 다음 작업: 사용자 승인 후 PLAN-005 Jira 생성을 별도 승인받고 Phase 0 계약 보정부터 구현한다.

## 2026-08-28 — Billing 대상 오해 정정·Learning Core Reservation saga 계획서 작성

<!-- codex-turn:01a0485f-e8cf-7ae3-8327-e72920c5d9a2 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira는 생성하지 않았고 기존 상태도 변경하지 않았다. Billing 선행 완료는 `TMI-110`·`TMI-112`·`TMI-113`, 별도 BenefitDefinition 계획은 `TMI-115`다.
- 작업 목표: 사용자의 정정에 따라 수정 대상을 Billing consumer가 아닌 Learning Core로 바로잡고, AttemptGroup publisher의 선행 조건인 필수 Idempotency-Key와 Billing Reservation 시험 생성 saga 계획을 작성한다.
- 변경 파일: `docs/codex/BILLING_RESERVATION_SAGA_IMPLEMENTATION_PLAN.md`, Learning Core `docs/codex/CURRENT_STATE.md`·`WORKLOG.md`; 잘못 만든 Billing PLAN-005 파일은 삭제하고 Billing CURRENT_STATE/WORKLOG에 철회 이력만 남겼다.
- 계획 내용: 공개 `POST /api/v1/exams`의 flag 기반 필수 lowercase UUID v4 header, `ExamCreationOperation`, 고정 sessionId/mockExamId, reserve→Session commit→confirm, cancel/status 복구, same-operation replay, ExamSession의 reservation/attemptGroup mapping, SigV4 client와 Mongo Transaction/index·테스트를 단계별로 고정했다.
- 핵심 순서: 현재 ExamSession에는 `attemptGroupId`가 없어 상태 outbox를 먼저 만들 수 없다. 따라서 Reservation saga로 durable mapping을 만든 뒤 `GRADING`·`COMPLETED`·`RETAKE_AVAILABLE` outbox/publisher를 구현한다.
- 유지한 계약: Request Body 없음, 기존 성공 DTO와 `BaseResponse`, 실제 userId 비노출, retryCount·S3·Redis·AI request/Callback과 `user_id=examId`를 유지한다. Billing 활성 flag 기본값은 off이며 구버전 앱 rollout 전에 header를 강제하지 않는다.
- 애플리케이션 동작: 이번 턴에는 계획서만 작성했고 Java/config/migration·실제 API·Billing 애플리케이션·Jira·AWS·배포·Git commit/push를 변경하지 않았다. Secret과 Token을 기록하지 않았다.
- 테스트 결과: 문서 작업이라 Gradle 테스트는 실행하지 않았다. 실제 Learning Core Controller/SessionManager/ExamSession/index와 Billing reserve/confirm/cancel/status DTO·SigV4 ADR을 읽기 전용으로 대조했고 종료 전에 두 저장소 `git diff --check`, code fence와 marker 단일 포함을 검증한다.
- 위험 요소: public header 강제는 프론트 선배포가 필요하고, confirm 응답 유실을 실패로 단정하면 Session/consumption이 갈라진다. 외부 호출을 Mongo Transaction에 넣지 않고 영속 operation과 status recovery로 수렴해야 한다.
- 다음 작업: 사용자가 새 Learning Core 계획을 승인하면 Jira 생성을 별도 승인받고 AGENTS의 명시적 Billing saga 범위를 추가한 뒤 Phase 0부터 구현한다.

## 2026-08-28 — 수정 대상 Learning Core 재확인

<!-- codex-turn:01a04865-5bb4-7702-94ae-a420f9f2b1a2 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 이슈를 생성하거나 기존 상태를 변경하지 않았다.
- 사용자 정정에 따라 구현 대상이 Billing 애플리케이션이 아니라 Learning Core임을 다시 확인했다. Billing은 이미 확정된 Reservation 계약을 제공하는 외부 의존 서비스로만 취급한다.
- 현재 활성 계획서는 Learning Core의 `docs/codex/BILLING_RESERVATION_SAGA_IMPLEMENTATION_PLAN.md`다. `POST /api/v1/exams`의 `Idempotency-Key`, `ExamCreationOperation`, Billing reserve/confirm/cancel/status client, `ExamSession` 연결 정보와 same-operation replay를 Learning Core에 구현한다.
- Billing AttemptGroup consumer, Billing 애플리케이션·계약, Jira, AWS와 Java 런타임 코드는 변경하지 않았다.
- 기존 공개 API 성공 DTO·`BaseResponse`, 실제 userId 비노출, 시험 `retryCount`, S3·Redis와 Python AI `user_id=examId` 계약을 유지한다.
- 테스트: 범위 확인과 문서 검증만 수행했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`를 실행했다.
- 다음 작업 전 확인 사항: 계획 승인 후 Learning Core AGENTS 예외 범위를 명시하고 Phase 0 계약·설정부터 구현한다. Git commit과 push는 사용자가 수행한다.

## 2026-08-28 — Billing 후속 변경 필요성·구현 순서 구분

<!-- codex-turn:01a04869-billing-scope-order -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 이슈를 생성하거나 기존 상태를 변경하지 않았다.
- Billing에는 `TMI-112`·`TMI-113`의 TrialClaim, 무료 grant/ledger와 Reservation reserve/confirm/cancel/status·expiry 기반이 이미 있으므로 Learning Core 시험 생성 saga를 위해 동일 기능을 Billing에 다시 구현할 필요는 없다.
- 현재 우선 작업은 Learning Core의 공개 시험 생성 `Idempotency-Key`, Billing client, reserve→Session commit→confirm saga, same-operation replay와 `ExamSession.attemptGroupId` mapping이다.
- 후속 전체 연동에는 Learning Core의 AttemptGroup 상태 outbox/publisher와 이에 대응하는 Billing event consumer가 모두 필요하다. Billing owner rebind/UserMerged 정합성과 Billing 측 repair·reconciliation도 별도 Billing 후속 범위다.
- 따라서 앞서 작성했다가 철회한 Billing consumer 계획의 기능 자체가 불필요한 것은 아니지만, 사용자가 요청한 현재 Learning Core 계획 대신 다음 작업으로 선정한 것은 대상과 순서가 잘못된 것이었다.
- 애플리케이션·외부 계약·Jira·AWS를 변경하지 않았다. 문서 분석 작업이라 Gradle 테스트는 실행하지 않았고 `git diff --check`로 형식을 확인한다.
- 다음 작업 전 확인 사항: Learning Core saga를 먼저 구현한 뒤 durable `attemptGroupId` mapping과 상태 event 계약을 기준으로 Learning Core publisher와 Billing consumer를 각각의 저장소에서 후속 구현한다.

## 2026-08-28 — Billing 필요 범위 최종 확인

<!-- codex-turn:01a0486c-b3c8-7312-8be3-71a67a4e9658 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 완료 근거는 Billing `TMI-112`·`TMI-113`이며 신규 Jira 생성이나 상태 변경은 수행하지 않았다.
- 이번 확인의 결론은 Billing Reservation reserve/confirm/cancel/status 기반은 이미 구현돼 있어 현재 Learning Core 시험 생성 saga를 위해 Billing에 동일 기능을 다시 추가할 필요가 없다는 것이다.
- 현재 우선 구현 대상은 Learning Core의 `Idempotency-Key`, Billing client, reserve→Session commit→confirm saga, same-operation replay와 `ExamSession.attemptGroupId` durable mapping이다.
- Learning Core AttemptGroup 상태 outbox/publisher가 준비된 뒤에는 이를 수신하는 Billing 상태 event consumer가 별도로 필요하다. Billing owner rebind와 Billing 측 reconciliation도 후속 Billing 범위다.
- 따라서 앞선 Billing consumer 선택은 기능 자체가 불필요했던 것이 아니라 현재 Learning Core 작업 대신 먼저 선정한 대상과 순서가 잘못된 것이었다.
- 변경 파일: 분석 기록을 위해 Learning Core `docs/codex/WORKLOG.md`와 `docs/codex/CURRENT_STATE.md`만 갱신했다. 애플리케이션·테스트·외부 API·Billing 저장소·AWS·Git·Jira는 변경하지 않았다.
- 유지한 계약: 기존 공개 API·DTO·`BaseResponse`, 실제 userId 비노출, 시험 `retryCount`, S3·Redis와 Python AI `user_id=examId` 계약을 유지했다. Secret과 Token을 기록하지 않았다.
- 테스트 결과: 문서 분석 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 문서 형식을 검증한다.
- 다음 작업: Learning Core saga를 먼저 구현하고, 그 후 Learning Core publisher와 Billing consumer를 각 저장소의 별도 작업으로 진행한다.

## 2026-08-28 — Learning Core 시험 생성 saga 계획 설명

<!-- codex-turn:01a04875-5454-7533-b77c-f68ca8c00189 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 관련 Billing 선행 완료 근거는 `TMI-112`·`TMI-113`이며 신규 Jira 생성이나 상태 변경은 수행하지 않았다.
- 사용자 요청에 따라 `docs/codex/BILLING_RESERVATION_SAGA_IMPLEMENTATION_PLAN.md`의 목적과 동작을 설명했다. 이 계획은 Billing 기능을 새로 만드는 것이 아니라 Learning Core의 `POST /api/v1/exams`를 기존 Billing Reservation API와 연결하는 작업이다.
- 핵심 목적은 앱의 한 번의 시험 시작 동작을 `Idempotency-Key`로 고정해 중복 클릭·transport retry·응답 유실·process crash에서도 같은 `examId`와 operation으로 수렴시키고 Session 및 무료 사용권의 이중 생성·이중 소비를 막는 것이다.
- 정상 흐름은 Learning Core operation 준비→Billing reserve→기존 Session 교체와 새 confirming Session의 Mongo Transaction commit→Billing confirm→Session `IN_PROGRESS` 전환과 기존 성공 DTO 반환이다.
- 실패 흐름은 reserve 전 기존 Session 보존, local commit 실패 시 cancel/expiry, confirm 응답 불명 시 status 조회와 같은 key replay로 복구한다. 외부 HTTP는 Mongo Transaction 안에서 실행하지 않는다.
- `ExamSession`에는 operation/reservation/attemptGroup mapping을 내부 저장하지만 공개 성공 DTO, `BaseResponse`, 실제 userId 비노출, retryCount, S3·Redis와 Python AI `user_id=examId` 계약은 유지한다.
- feature flag 기본값은 off이며 프론트의 header 선배포와 staging 검증 전에는 기존 무헤더 생성 흐름을 유지한다. AttemptGroup outbox/publisher, Billing consumer, UserMerged·owner rebind와 background reconciliation은 후속 범위다.
- 변경 파일: 설명 기록을 위해 Learning Core `docs/codex/WORKLOG.md`와 `docs/codex/CURRENT_STATE.md`만 갱신했다. 애플리케이션·테스트·Billing 저장소·외부 API·AWS·Git·Jira는 변경하지 않았다.
- 테스트 결과: 문서 설명 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 문서 형식을 검증한다.
- 다음 작업 전 확인 사항: 사용자가 계획을 승인하면 AGENTS의 명시적 saga 예외와 프론트 header 계약을 먼저 정렬한 뒤 operation/header 기반부터 단계적으로 구현한다.

## 2026-08-28 — TMI-116 Learning Core 시험 생성 saga Jira 생성

<!-- codex-turn:01a04885-643c-7e92-b8fd-81a5b7fe80b0 -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: `TMI-116` `[Learning Core] Billing Reservation 시험 생성 saga 구현`을 작업 유형으로 생성했고 재조회 결과 상태는 `해야 할 일`, 담당자는 미지정이다.
- 중복 검색에서 동일한 Learning Core saga 이슈는 없었고, 선행 Billing `TMI-112`·`TMI-113`만 완료 상태로 확인했다.
- Jira 설명에 목표, reserve→Session commit→confirm 정상 흐름, 필수 `Idempotency-Key`, operation·Session 내부 모델, INITIAL/REPLACEMENT, 실패·응답 유실 복구, SigV4/Lattice, feature flag, 공개 오류, 완료 조건과 production 활성화 gate를 기록했다.
- Billing Reservation 재구현, AttemptGroup publisher/consumer, UserMerged·owner rebind, background reconciliation과 실제 AWS 배포는 이슈 범위에서 제외했다.
- 계획서 `docs/codex/BILLING_RESERVATION_SAGA_IMPLEMENTATION_PLAN.md`의 상태를 `계획 승인·Jira 생성, 구현 대기`로 바꾸고 Jira를 `TMI-116`으로 기록했다.
- 변경 파일: 계획서와 Learning Core `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. 애플리케이션·테스트·Billing 저장소·AWS·Git은 변경하지 않았다.
- 유지한 계약: 기존 공개 API 성공 DTO·`BaseResponse`, 실제 userId 비노출, retryCount, S3·Redis와 Python AI `user_id=examId` 계약을 유지했다. Secret과 Token을 기록하지 않았다.
- 테스트 결과: Jira·문서 작업이라 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 문서 형식을 검증한다.
- 다음 작업 전 확인 사항: `TMI-116` 구현 시작 전에 AGENTS의 명시적 Billing saga 허용 범위와 프론트 `Idempotency-Key` rollout 계약을 반영한다.

## 2026-08-29 — TMI-116 Billing Reservation 시험 생성 saga 구현

<!-- codex-turn:01a0488c-588f-7f60-9907-6e6859ff596b -->

- 날짜: 2026-08-29
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116` `[Learning Core] Billing Reservation 시험 생성 saga 구현`; 구현과 로컬 검증은 완료했지만 Jira 상태는 변경하지 않았다.
- 작업 목표: 기존 Billing `TMI-112`·`TMI-113` Reservation API를 Learning Core `POST /api/v1/exams`에 연결해 사용자 시작 요청, transport retry, confirm 응답 유실과 process crash가 하나의 operation·Session·사용권 소비로 수렴하게 한다.
- 변경 파일: 시험 생성 controller/service·Session manager, `ExamSession`·상태·repository, 신규 `ExamCreationOperation`과 repository, `BillingExamCreationSaga`·Mongo Transaction service·Idempotency-Key parser, `domain/exams/billing` 설정·SigV4 client·startup validator/probe, 오류 mapping, `application.yml`·`.env.example`, Mongo migration script, 프론트 인계서·구현 계획서·AGENTS와 관련 단위·MVC·통합 테스트를 변경·추가했다.
- 변경 동작: feature flag on에서 lowercase UUID v4 `Idempotency-Key`를 필수화하고 operation 준비→Billing reserve→기존 Session abandon 및 confirming Session Transaction commit→Billing confirm→`IN_PROGRESS` finalize를 구현했다. reserve 실패는 기존 Session을 보존하고, local commit 실패는 cancel/expiry, confirm 응답 불명은 status와 same-key replay로 복구한다.
- 멱등성과 동시성: command에는 fixed session/mock exam과 reservation/group mapping을 저장한다. active operation unique guard와 optimistic version을 사용하고, command TTL 뒤에는 사용자 범위 `(userId, creationOperationId)` Session unique mapping으로 replay한다. same-key concurrent commit의 optimistic/duplicate 충돌에서는 공유 Billing reservation을 취소하지 않고 processing retry를 반환한다.
- Billing 계약: AWS default credential provider를 공유해 SigV4 `vpc-lattice-svcs`/`ap-northeast-2`로 서명하고 redirect를 따르지 않는다. strict JSON, response 16 KiB 상한, timeout·오류·`Retry-After` mapping을 적용했으며 `sessionCommittedAt`은 Billing decoder가 요구하는 UTC 밀리초 3자리로 고정했다. 실제 credential, user payload와 authorization header를 로그·문서에 기록하지 않았다.
- 유지한 외부 계약: 기존 URL·HTTP Method·Request Body 없음, 성공 `CreateSessionResult`와 `BaseResponse`를 유지했다. 실제 userId·reservationId·attemptGroupId는 외부에 노출하지 않았고, 기존 retryCount·S3 Object Key·Redis Key·submit/polling과 Python AI `user_id=examId`·Callback JSON을 변경하지 않았다. flag off에서는 기존 무헤더 Session 생성 흐름을 유지한다.
- 테스트: saga·transaction·Idempotency-Key·SigV4·configuration/index/transaction probe와 기존 service/MVC/security 회귀를 검증했다. 최종 `./gradlew clean test` 결과는 424 tests, failures 0, errors 0, skipped 0이다. `node --check scripts/mongodb/tmi-116-migrate-billing-exam-saga.js`와 `git diff --check`도 성공했다.
- 남은 위험 요소: Mock 기반 Transaction 검증만 완료했으므로 실제 Mongo replica-set의 index migration·동시 transaction/failure injection이 필요하다. Lattice route, ECS task role/IAM/SG, Billing expiry worker와 INITIAL/R3 REPLACEMENT response-loss E2E는 아직 검증하지 않았다. background reconciliation과 AttemptGroup outbox/publisher는 의도적으로 후속 범위다.
- 다음 작업 전 확인 사항: 프론트가 매 시작 operation에 UUID v4 key를 생성하고 transport retry에서 재사용하도록 먼저 배포한다. 이후 staging DB migration dry-run/apply, Lattice 최소 권한·경로, reserve 실패·commit rollback·confirm timeout/status와 INITIAL/REPLACEMENT E2E를 통과한 뒤에만 `BILLING_CREATION_SAGA_ENABLED=true`로 활성화한다. Git commit과 push는 사용자가 수행한다.

## 2026-08-29 — TMI-116 코드 증가량·구현 범위 설명

<!-- codex-turn:01a04909-d7c1-73a0-8fe5-4d75e29695f7 -->

- 날짜: 2026-08-29
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116`; 상태는 변경하지 않았다.
- 작업 목표: 사용자가 우려한 코드 증가량이 어떤 기능에서 발생했는지 이번 구현과 기존 작업 트리 변경을 구분해 설명한다.
- 분석 결과: 중심 구현은 saga orchestration 511줄, Mongo Transaction service 186줄, operation entity 149줄, Idempotency-Key parser와 기존 시험 생성 연결부다. 큰 부가 영역은 SigV4/strict Billing client 387줄, configuration·index·transaction startup 검증, 163줄 migration과 실패·동시성·계약 테스트다.
- 변경량이 큰 이유: 정상 reserve→commit→confirm만 구현한 것이 아니라 reserve 실패, local rollback/cancel, confirm response-loss/status reconciliation, same-key replay, concurrent commit, command TTL 뒤 replay와 운영 fail-closed까지 포함했다.
- 작업 트리 구분: 10초 챌린지 계약, 비용 산정과 진행 체크 문서는 이전 작업에서 존재하던 별도 변경이며 TMI-116 런타임 구현량으로 합산하면 안 된다.
- 변경 파일·동작: 이번 설명 턴에는 Java·설정·migration·외부 계약을 추가 수정하지 않았고 기록 문서만 갱신했다. 기존 공개 API·BaseResponse·AI/S3/Redis 계약도 변경하지 않았다.
- 테스트: 코드 변경이 없는 설명 작업이므로 Gradle 테스트를 다시 실행하지 않았다. 직전 최종 결과는 424 tests, failures/errors/skipped 0이다.
- 남은 판단: 현 구조는 운영 안전성을 우선한 완성형 범위다. MVP 단순화가 필요하면 startup validator/probe와 migration을 제거하기보다 orchestration/client 내부를 작은 class로 분리해 가독성을 개선하는 편이 안전하다.

## 2026-08-29 — TMI-116 코드 증가량 설명 종료 기록 동기화

<!-- codex-turn:01a04909-6d3b-7e02-8539-e40daa27fbc6 -->

- 날짜: 2026-08-29
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116`; 상태는 변경하지 않았다.
- 종료 훅 요청에 따라 이번 설명 작업의 WORKLOG와 CURRENT_STATE 기록을 동기화했다.
- 설명 결론: 큰 변경량은 정상 Billing 호출 자체보다 영속 operation, 실패·응답 유실·동시 요청 복구, Mongo Transaction, SigV4 strict client, startup index/transaction 검증과 테스트에서 발생했다.
- 작업 트리의 10초 챌린지·비용 관련 문서는 이전 작업에서 존재한 별도 변경이며 이번 saga 운영 코드와 구분한다.
- 이번 동기화에서는 애플리케이션·테스트·외부 API·Jira·AWS·Git commit/push를 변경하지 않았고 Secret과 Token을 기록하지 않았다.
- 코드 변경이 없는 기록 작업이므로 Gradle 테스트는 다시 실행하지 않았다. 직전 최종 검증은 424 tests, failures/errors/skipped 0이다.

## 2026-08-29 — TMI-116 구현 구조 상세 설명

<!-- codex-turn:01a0490b-53e2-7ab2-b662-42f6dbfb7b0c -->

- 날짜: 2026-08-29
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116`; 상태는 변경하지 않았다.
- 작업 목표: 사용자의 추가 요청에 따라 코드 증가 원인을 정상 요청 흐름, 영속 상태, 실패 복구, SigV4 client, Mongo 운영 보호와 테스트로 나누어 상세히 설명한다.
- 핵심 구조: Billing과 Learning Core MongoDB 사이에는 하나의 분산 Transaction을 걸 수 없으므로 `ExamCreationOperation` 상태를 복구 지점으로 삼는다. `PREPARED → RESERVED → SESSION_COMMITTED → SUCCEEDED` 정상 전이와 cancel/expiry/terminal 전이를 저장해 process crash와 same-key replay를 이어서 처리한다.
- 정상 흐름: 고정 session/mock exam 준비, Billing 사용권 hold, Mongo Transaction에서 기존 active Session abandon과 새 confirming Session insert, Billing confirm, 두 번째 Mongo Transaction의 Session `IN_PROGRESS` finalize 순서다.
- 실패 복구: reserve 실패는 기존 Session을 보존하고, local commit 실패는 cancel 또는 `CANCEL_PENDING`, confirm 응답 유실은 status 조회로 confirmed/reserved/canceled/expired에 수렴한다. same-key 동시 commit 충돌에서는 공유 reservation을 취소하지 않는다.
- 운영 보호: SigV4 Lattice 서명, strict JSON·16 KiB 상한·timeout·redirect 금지, UTC 밀리초 timestamp, 외부 UUID 검증, Mongo unique/TTL index migration, staging/prod startup index와 Transaction capability 검증을 포함한다.
- 범위 구분: AttemptGroup 상태 outbox/publisher, Billing consumer, background reconciliation scheduler와 실제 AWS/Lattice/IAM/SG·운영 DB 적용은 이번 구현에 포함하지 않았다.
- 변경 파일·테스트: 이번 상세 설명 턴에는 애플리케이션·외부 계약을 추가 수정하지 않고 기록 문서만 갱신했다. 코드 변경이 없어 Gradle 테스트를 재실행하지 않았으며 직전 최종 결과는 424 tests, failures/errors/skipped 0이다.

## 2026-08-29 — TMI-116 상세 설명 종료 기록 동기화

<!-- codex-turn:01a0490b-53e2-7dc2-b1b4-6de27b041a50 -->

- 날짜: 2026-08-29
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116`; 상태는 변경하지 않았다.
- 종료 훅 요청에 따라 이번 상세 설명 작업의 WORKLOG와 CURRENT_STATE를 다시 동기화했다.
- 설명 범위는 기존 시험 생성의 문제, 정상 reserve→Mongo commit→confirm 흐름, `ExamCreationOperation` 상태, 장애·동시성 복구, SigV4 strict client, Mongo index/startup gate와 코드량 구분이다.
- 이번 종료 기록에서는 Java·설정·migration·테스트·외부 API·Jira·AWS·Git commit/push를 변경하지 않았으며 Secret과 Token을 기록하지 않았다.
- 코드 변경이 없는 기록 작업이므로 Gradle 테스트를 다시 실행하지 않았다. 직전 최종 검증은 424 tests, failures/errors/skipped 0이다.

## 2026-08-31 — TMI-116 Saga·SigV4 Billing client 책임 설명

<!-- codex-turn:01a05577-534f-7f52-97c8-a164ab1ff531 -->

- 날짜: 2026-08-31
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116`; 상태는 변경하지 않았다.
- 작업 목표: `BillingExamCreationSaga`와 `SigV4BillingReservationClient`가 각각 무엇을 담당하고 어떻게 연결되는지 설명한다.
- 결론: Saga는 영속 operation 상태를 기반으로 시험 생성의 reserve→Session commit→confirm 순서와 cancel/status 장애 복구를 결정하는 application orchestration이다. SigV4 client는 reserve·confirm·cancel·status 요청을 Billing wire 계약으로 보내고 AWS 서명·timeout·strict decode·오류 변환을 처리하는 infrastructure adapter다.
- 책임 경계: Saga는 `BillingReservationClient` interface에만 의존해 SigV4와 HTTP 세부사항을 모르며, client는 ExamSession 저장·상태 전이·재시도 정책을 결정하지 않는다.
- 변경 파일·테스트: 이번 설명 작업에서는 Java·설정·migration·외부 API를 수정하지 않고 기록 문서만 갱신했다. 코드 변경이 없어 Gradle 테스트를 다시 실행하지 않았으며 직전 최종 결과는 424 tests, failures/errors/skipped 0이다.
- Secret과 Token을 기록하지 않았고 AWS·Git commit/push도 변경하지 않았다.

## 2026-08-31 — Billing VPC Lattice AWS_IAM 선택 근거 재확인

<!-- codex-turn:01a0557a-8851-7003-aa5f-f7c2e463e7af -->

- 날짜: 2026-08-31
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116`; 상태는 변경하지 않았다.
- 작업 목표: Billing 내부 호출에 VPC Lattice `AWS_IAM`과 ECS task role SigV4를 선택했던 이유를 Billing 통합 계약, 계약 결정서와 ADR-002에서 재확인한다.
- 선택 근거: Billing은 앱이 직접 호출하지 않는 내부 서비스이고 caller가 이미 ECS에 있어, 별도 workload 인증 시스템 대신 자동 회전되는 task role 임시 credential을 서비스 principal로 사용할 수 있다. Lattice policy는 principal·Method·Path를 함께 검사해 Identity/Learning Core 및 production/staging 권한을 최소 범위로 분리한다.
- 네트워크 경계: Billing을 ALB 없이 Lattice target으로 연결하고 SG에서 Lattice 경로 외 task 직접 접근을 차단해, 서명 검증을 우회하는 내부 경로를 남기지 않는 것이 계약의 필수 조건이다.
- 대안 비교: 플랫폼 JWT는 ECS가 issuer/JWKS를 자동 제공하지 않고, Identity workload JWT는 client-credentials·Secret rotation·token cache를 새로 구현해야 하며, mTLS는 인증서 운영이 복잡하고 shared API key/HMAC은 정적 Secret과 세밀한 route 권한에 불리해 채택하지 않았다.
- tradeoff: Lattice 비용과 AWS 종속성, IAM/auth policy·SG·SigV4 client·local fake adapter 운영 복잡도가 추가된다. 따라서 실제 Lattice/IAM/SG 연결과 wrong-role·unsigned·direct-bypass negative test가 production gate다.
- 변경 파일·테스트: 이번 분석은 문서 근거 재확인과 기록 갱신만 수행했으며 Java·외부 계약·AWS·Git commit/push를 변경하지 않았다. 코드 변경이 없어 Gradle 테스트를 다시 실행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-08-31 — TMI-116 이후 다음 작업 설명

- 날짜: 2026-08-31
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: 기존 `TMI-116`은 상태 변경하지 않았고 신규 Jira는 생성하지 않았다.
- 작업 목표: Billing Reservation saga 다음에 진행할 애플리케이션 작업과 운영 활성화 작업을 구분해 설명한다.
- 확인 결과: Learning Core의 AttemptGroup outbox/publisher와 Billing `POST /internal/v1/attempt-group-events` consumer가 모두 없다. Saga가 저장한 `ExamSession.attemptGroupId`를 사용해 `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE` 상태를 Billing consumption projection으로 전달하는 것이 다음 vertical slice다.
- 권장 순서: event schema·전이 동결, Billing inbox consumer 선배포, Learning Core local state와 같은 Transaction에서 outbox 기록, lease 기반 publisher, migration/index, SigV4 staging E2E, consumer→publisher 순 활성화다.
- TMI-116 운영 gate: 프론트 UUID v4 key 선배포, replica-set index migration, Billing expiry worker, Lattice/IAM/SG, reserve/commit/confirm failure injection과 INITIAL/REPLACEMENT E2E를 병행한다.
- 변경 파일·테스트: 이번 설명에서는 Java·외부 계약·Billing 저장소·AWS를 수정하지 않고 기록 문서만 갱신했다. 코드 변경이 없어 Gradle 테스트를 다시 실행하지 않았으며 Secret과 Token을 기록하지 않았다.

## 2026-08-31 — TMI-116 이후 다음 작업 설명 종료 기록

<!-- codex-turn:01a0557d-9caf-7fa1-a849-af29908846f7 -->

- 날짜: 2026-08-31
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: 기존 `TMI-116`은 상태 변경하지 않았고 신규 Jira는 생성하지 않았다.
- 종료 훅 요청에 따라 AttemptGroup 상태 연동을 다음 작업으로 확인한 이번 턴의 WORKLOG와 CURRENT_STATE를 동기화했다.
- 다음 vertical slice는 Billing `POST /internal/v1/attempt-group-events` consumer와 Learning Core의 `GRADING`·`COMPLETED`·`RETAKE_AVAILABLE` durable outbox/publisher다. 안전한 배포는 consumer 선배포 후 publisher 활성화 순서다.
- TMI-116의 프론트 key, Mongo migration, Lattice/IAM/SG와 staging failure-injection E2E는 별도 운영 gate로 병행한다.
- 이번 종료 기록에서는 Java·외부 계약·Billing 저장소·AWS·Git commit/push를 변경하지 않았으며 Secret과 Token을 기록하지 않았다. 코드 변경이 없어 Gradle 테스트를 재실행하지 않았다.

## 2026-08-31 — TMI-116 독립 리뷰 P1/P2 검증

<!-- codex-turn:01a055a1-20a0-7e91-b3a7-2e38fb9c56f0 -->

- 날짜: 2026-08-31
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116`; 상태는 변경하지 않았다.
- 작업 목표: 사용자가 전달한 confirm 영구 정체, concurrent reservation 오취소, Billing 성공 응답 strict 검증 및 PR 범위 지적을 현재 코드와 Billing ADR에 대조한다.
- Finding 1: 유효한 P1이다. `start()`의 Session-first durable replay가 confirming Session에서 즉시 processing을 반환해 `SESSION_COMMITTED` operation의 confirm/status 복구 루프를 영구 차단한다. operation-first drive와 command purge 이후 Session fallback이 필요하다.
- Finding 2: 유효한 P1이다. 명시된 두 예외 외 Mongo transient/unknown Transaction 결과는 generic Runtime 경로에서 단 한 번 re-read 후 아직 `RESERVED`이면 cancel된다. 같은 key concurrent commit이 아직 가시화되지 않은 reservation을 취소할 수 있으므로 unknown 결과에는 cancel을 금지하고 operation/Session 재조회와 same-key retry로 수렴해야 한다.
- Finding 3: 유효한 P2다. Jackson mapper는 scalar coercion·enum ordinal과 missing/null creator property를 완전히 거절하지 않으며 confirm semantic validation에 `attemptGroupStatus=OPEN`과 필수 confirmed timestamp가 빠져 있다. status/cancel의 조건부 timestamp를 포함한 endpoint별 검증과 malformed fixture 테스트가 필요하다.
- PR hygiene: 비용 추정·10초 챌린지 문서는 TMI-116과 무관하다. frontend handoff의 Idempotency-Key 변경은 관련 있지만 파일 전체 포함은 의도를 확인하고, 사용자 변경을 보존한 채 selective staging 또는 별도 commit으로 분리해야 한다.
- 변경 파일·테스트: 이번 요청은 진단이므로 Java·테스트·외부 계약을 수정하지 않고 기록 문서만 갱신했다. 코드 변경이 없어 Gradle 테스트를 실행하지 않았고 AWS·Git commit/push·Secret·Token을 변경하거나 기록하지 않았다.
- 다음 작업: 사용자가 수정을 요청하면 세 finding의 regression test를 먼저 추가하고 operation-first recovery, unknown commit no-cancel, strict response validation을 구현한 뒤 전체 `./gradlew clean test`를 실행한다.

## 2026-08-31 — ENTITLEMENT_CONFIRMING·SESSION_COMMITTED 관계 설명

<!-- codex-turn:01a055bb-5235-7f21-a5a3-57f85cb2aa67 -->

- 날짜: 2026-08-31
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116`; 상태는 변경하지 않았다.
- 작업 목표: `ENTITLEMENT_CONFIRMING`이 충돌 상태인지와 동시에 operation이 `SESSION_COMMITTED`가 될 수 있는지 설명한다.
- 결론: `ENTITLEMENT_CONFIRMING`은 충돌이 아니라 local Session commit 완료·Billing confirm 미확정 상태다. `commitReservedSession()`의 같은 Mongo Transaction에서 Session insert와 operation `SESSION_COMMITTED` 저장을 수행하므로 정상적인 중간 pair다.
- 정상 전이: `RESERVED + Session 없음`에서 Transaction 성공 후 `SESSION_COMMITTED + ENTITLEMENT_CONFIRMING`, Billing confirm/finalize 성공 후 `SUCCEEDED + IN_PROGRESS/CONFIRMED`가 된다. confirm/status가 실패하면 중간 pair가 남아 same-key recovery 대상이 된다.
- 불변식: Transaction rollback이면 새 Session insert와 operation 전이가 함께 rollback돼야 한다. 한쪽만 존재하면 Transaction 미지원·unknown outcome·수동 데이터 변경 등 비정상 정합성 문제로 취급해야 한다.
- 변경 파일·테스트: 이번 설명에서는 Java·테스트·외부 계약을 수정하지 않고 기록 문서만 갱신했다. 코드 변경이 없어 Gradle 테스트를 실행하지 않았고 AWS·Git commit/push·Secret·Token을 변경하거나 기록하지 않았다.

## 2026-08-31 — 앱 문제 응답의 Part 4 표 처리 현황 분석

<!-- codex-turn:01a055ae-7860-77d2-adec-ea270e5008bd -->

- 날짜: 2026-08-31
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: 이번 분석의 신규 Jira 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 문제 생성 응답과 `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`, 문항 결과 응답에서 Part 4 표는 MongoDB `table_context`를 비정형 `Map<String,Object>`로 읽어 `tableContext` JSON 객체로 그대로 전달한다.
- 서버는 table_context를 title/items 같은 고정 schema로 변환하거나 HTML·Markdown으로 렌더링하지 않는다. 앱이 전달된 중첩 object·array 구조를 해석해 표를 표시해야 한다.
- Mongo `table_image_url` 필드는 내부 entity에 남아 있지만 공개 QuestionDTO에 필드가 없어 앱 응답에서 제외된다. Part 4는 tableContext가 null이면 `EXAM_CATALOG_CONFIGURATION_ERROR`로 실패하고 빈 object는 허용한다.
- 일반 `imageUrl`은 기본 QuestionDTO mapping에 유지되지만 Part 4 문항 결과의 축약 questionInfo에는 text와 tableContext만 포함된다. AI 채점 multipart에도 table_image_url과 table_context를 보내지 않는다.
- 분석과 기록만 수행했으며 애플리케이션·테스트·AWS·Mongo 데이터·Jira·외부 API 계약을 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-08-31 — TMI-116 P1/P2 리뷰 finding 구현

<!-- codex-turn:01a055bd-c248-7960-a926-28646e1d99ea -->

- 날짜: 2026-08-31
- 브랜치: `feat/TMI-116-billing-reservation-exam-saga`
- Jira: `TMI-116`; 이슈 상태는 변경하지 않았다.
- 작업 목표: confirm 복구 영구 정체 P1, Mongo unknown commit 시 shared reservation 오취소 P1, Billing 성공 응답 strict 검증 부족 P2를 구현한다.
- 변경 파일: `BillingExamCreationSaga.java`, `BillingSagaConfiguration.java`, `SigV4BillingReservationClient.java`, 대응 Saga·SigV4 client 테스트와 현재 기록 문서를 수정했다.
- operation-first 복구: 동일 key operation을 먼저 조회·drive하고 operation이 없을 때만 Session durable replay를 사용한다. 이로써 `ENTITLEMENT_CONFIRMING + SESSION_COMMITTED` 상태의 재요청이 processing에서 고착되지 않고 confirm/status reconciliation으로 복구된다.
- commit 안전성: transaction 예외 뒤 operation과 `(userId, creationOperationId)` Session을 재조회한다. operation 전이가 관측되면 다음 단계로 진행하고 Session만 보이면 processing으로 수렴한다. transient/unknown Runtime과 동시성 예외에서는 reservation cancel을 금지했으며, 확정적인 local `IllegalStateException`에서 operation·Session이 모두 보이지 않을 때만 기존 cancel 보상을 수행한다.
- strict 계약: Jackson scalar coercion과 enum ordinal을 차단하고 문자열·날짜 입력 shape를 강화했다. reserve/confirm/cancel/status response record에 endpoint별 필수 field 검증을 추가하고, Saga에서 confirm `attemptGroupStatus=OPEN`·필수 timestamp 및 status/cancel identity·terminal timestamp를 검증하며 timestamp의 현재 시각 fallback을 제거했다.
- 테스트: `ENTITLEMENT_CONFIRMING + SESSION_COMMITTED` 복구, unknown commit no-cancel, unknown commit 후 관측된 전이 계속 진행, confirm group status/timestamp fail-closed, 숫자→문자열 coercion·숫자 enum·필수 confirm field 누락 거절 회귀 테스트를 추가했다.
- 실행 결과: 집중 테스트 18개 통과 후 `./gradlew clean test`를 실행했고 전체 432개 테스트가 통과했다. `git diff --check`도 통과했다.
- 유지 계약: 기존 공개 API URL·Method·Request/Response·`BaseResponse`, AI `user_id=examId`, callback, retryCount, S3와 Redis 계약은 변경하지 않았다. Billing 저장소·AWS·Jira·Git commit/push도 변경하지 않았다.
- 남은 위험: Mock 기반 테스트이므로 실제 Mongo replica set의 transient transaction label·unknown commit failure injection, VPC Lattice/IAM/SG 연결과 staging reserve/commit/confirm/status E2E는 운영 활성화 전에 별도로 검증해야 한다. PR에는 기존에 섞인 비용 추정·10초 챌린지 등 TMI-116 무관 문서를 selective staging 또는 별도 commit으로 분리해야 한다.

## 2026-08-31 — TMI-116 로컬 develop 반영 확인 종료 기록

<!-- codex-turn:01a05620-13d7-7212-bba3-2215d0f62c28 -->

- 날짜: 2026-08-31
- 브랜치: `develop`
- Jira: `TMI-116`; Jira 상태는 조회하거나 변경하지 않았다.
- `HEAD`, 로컬 `develop`, `origin/develop`이 모두 PR `#24` merge commit `d95d18b42a47383c2237fdb7eae536b7495136fb`를 가리키는 것을 재확인했다.
- TMI-116 최신 feature commit `c3e3c8296316b1e49014413eb3dc32efaad76aba`가 로컬 `develop` ancestry에 포함되므로 구현과 P1/P2 보완은 현재 로컬 코드에도 반영됐다.
- 확인과 기록 문서 동기화만 수행했다. 애플리케이션·외부 계약·AWS·Jira·Git commit/push는 변경하지 않았고 코드 변경이 없어 Gradle 테스트를 재실행하지 않았다. 기존 WORKLOG 변경은 수정하거나 삭제하지 않고 보존했다.

## 2026-08-31 — 멘토링용 저장소 구조 조사 산출물 사전 정리

<!-- codex-turn:01a05623-68cb-7591-b798-199eb1243c76 -->

- 날짜: 2026-08-31
- 브랜치: `develop`
- Jira: 이번 사전 정리의 별도 Jira 이슈 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 작업 목표: draw.io 작성 전에 컨셉맵, 아키텍처, Feature Map, IA와 네이밍 사전, 컨벤션·code smell, 진화수렴·디팩토 비교 조사의 범위와 산출물 구성을 정리한다.
- 현재 코드의 주요 관점을 시험·채점, Billing Reservation 시험 생성 saga, 회원 탈퇴 이벤트, 인증·보안·관측성, MongoDB·Redis·S3·Python AI·Billing 연동으로 식별했다. 서로 다른 목적의 도식을 한 장에 혼합하지 않고 개념·런타임·기능·정보 탐색 관점으로 분리하는 방향을 제안한다.
- 본 조사는 현재 앱 Learning Core 저장소만 대상으로 하며 별도 웹 POC 저장소는 조회하지 않는다. 구현 상태와 문서상 계획을 구분하고, 신규 10초 챌린지는 계약·계획과 실제 코드 존재 여부를 구별해 표시할 예정이다.
- 이번 턴에서는 실제 draw.io 파일이나 본 조사 문서를 만들지 않았고 애플리케이션 코드·외부 API·AI Callback·Mongo/Redis/S3·AWS·Git commit/push를 변경하지 않았다.
- 기록 문서만 갱신했으므로 Gradle 테스트는 실행하지 않았다. 다음 작업에서 사용자와 범위·표현 기준을 확인한 뒤 실제 저장소 근거, 위반 위치, 위험도와 개선 우선순위를 포함한 산출물을 작성한다.

## 2026-08-31 — 멘토링 구조 조사 범위를 앱 서버 전체로 확장

<!-- codex-turn:01a05628-0a29-7902-9079-503a69201215 -->

- 날짜: 2026-08-31
- 브랜치: `develop`
- Jira: 이번 범위 결정에 별도 Jira 이슈 키는 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자 의도에 따라 조사 범위를 웹을 제외한 앱 서버 전체, 즉 Learning Core·Identity·Billing으로 함께 보는 방향이 적절하다고 정리했다.
- 세 저장소를 한 번에 조사하되 모든 내용을 한 장이나 하나의 표에 합치지 않는다. 공통 시스템 관점과 서버별 내부 관점을 계층화하고, 통합 용어 사전과 서버별 용례, 공통 컨벤션과 저장소별 편차를 함께 제공하는 방식을 권고한다.
- 통합 조사에서 인증 주체와 사용자 lifecycle, Billing 사용권·Reservation·AttemptGroup, Learning Core 시험 생성·채점 흐름 등 서비스 간 책임과 계약을 우선 확인한다. 기존 웹 POC와 웹 프론트·백엔드는 계속 범위에서 제외한다.
- 이번 턴은 범위와 표현 전략을 설명하고 기록 문서만 갱신했다. 세 저장소의 애플리케이션 코드·외부 계약·데이터·AWS·Jira·Git commit/push를 변경하지 않았으며 Secret과 Token을 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았고 `git diff --check`로 기록 문서 형식만 확인한다.

## 2026-08-31 — 웹 제외 앱 서버 통합 구조 조사와 멘토링 산출물 작성

<!-- codex-turn:01a05646-e535-73c1-b816-cfe68e3e63d2 -->

- 날짜: 2026-08-31
- 브랜치·기준 snapshot: Learning Core `develop@d95d18b`, Identity `feat/TMI-116-billing-reservation-exam-saga@8c4f3ca`, Billing `develop@39e424d`.
- Jira: 이번 통합 조사에 신규 Jira 이슈 키는 없다. 현재 구현 문맥의 `TMI-115`·`TMI-116`을 읽기 전용 근거로 사용했으며 Jira를 조회·수정·댓글·상태 변경하지 않았다.
- 작업 목표: 기존 웹을 제외한 세 앱 서버를 조사해 컨셉맵, 아키텍처, Feature Map, IA, 네이밍 사전, 컨벤션·code smell, 진화수렴·사실상 표준 비교를 한국어 산출물로 제공한다.
- 변경 파일: `docs/architecture/app-server-mentoring.drawio`, `APP_SERVER_SYSTEM_OVERVIEW.md`, `APP_SERVER_NAMING_DICTIONARY.md`, `APP_SERVER_CONVENTIONS_AND_CODE_SMELLS.md`, `APP_SERVER_EVOLUTION_CONVERGENCE_REVIEW.md`를 추가하고 세 저장소의 WORKLOG/CURRENT_STATE에 분석 기록을 반영했다.
- 구현 내용: draw.io에 통합 컨셉맵, 시스템 아키텍처, Feature Map, 앱 IA, Learning Core·Identity·Billing 상세, 서비스 간 흐름과 Gap의 8개 편집 가능 페이지를 작성했다. Markdown에는 서비스 경계, 식별자, 기능 구현/조건부/계약 상태, suffix 사전, 저장소별 컨벤션과 우선순위별 위험, 대표적 진화 방향과 fitness function을 정리했다.
- 핵심 결론: 서비스 분리와 data ownership은 대체로 적절하다. 신규 기반 기술보다 Identity→Billing SigV4 transport 정렬, AttemptGroup 상태 consumer/outbox/publisher, AI Callback trust boundary, Learning Core capability 분리와 문서 freshness가 우선이다.
- 외부 계약: 공개 API URL·Method·Request/Response·`BaseResponse`, JWT/JWKS, Python AI `user_id=examId`, retryCount, S3/Redis key, Billing internal wire 계약을 변경하지 않았다. 세 저장소의 애플리케이션·설정·테스트·데이터·AWS도 변경하지 않았다.
- 검증: `xmllint --noout`으로 draw.io XML을 검증했고 `<diagram>` 8개를 확인했다. `git diff --check -- docs/architecture`와 전체 기록 반영 후 `git diff --check`를 실행한다. 애플리케이션 코드 변경이 없는 분석·문서 작업이므로 Gradle 테스트는 실행하지 않았다.
- 위험 요소: draw.io CLI가 현재 환경에 없어 diagrams.net 실제 렌더 export는 수행하지 못했다. 분석은 checkout snapshot 기준이며 실제 배포 flag·AWS route·Mongo migration 상태는 staging에서 별도 확인해야 한다.
- 다음 작업: 멘토링에서 용어와 우선순위를 검토한 뒤, 출시 gate는 별도 Jira/계획으로 분리하고 Learning Core 내부 모듈화는 외부 계약을 유지하는 작은 vertical slice로 진행한다.
- Git commit·push는 수행하지 않았고 Secret, Token, credential, 실제 URI와 개인정보를 기록하지 않았다.

## 2026-08-31 — 멘토링 draw.io 문서 여는 방법 안내

<!-- codex-turn:01a0566f-10ef-78f3-b8f2-e862c15972c7 -->

- 날짜: 2026-08-31
- Jira: 별도 Jira 이슈 키가 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자가 첨부한 `<mxfile>` XML이 `docs/architecture/app-server-mentoring.drawio`와 동일한 8페이지짜리 완전한 draw.io 문서임을 확인했다.
- 빈 캔버스의 XML 도형 삽입 기능이 아니라 diagrams.net의 `File → Open From → Device`로 `.drawio` 파일 전체를 열어야 함을 안내한다. XML 텍스트만 보유한 경우 UTF-8 `.drawio` 파일로 저장해 같은 방식으로 열 수 있다.
- 애플리케이션 코드·외부 API·AI 계약·데이터·Jira·Git commit/push는 변경하지 않았다. 설명과 기록 문서 갱신만 수행하므로 Gradle 테스트는 실행하지 않는다.

## 2026-08-31 — 현재 앱 서버 개발 방식 설명

<!-- codex-turn:01a056b8-0667-7ce2-81dc-5cae0ca70b05 -->

- 날짜: 2026-08-31
- Jira: 별도 Jira 이슈 키가 없으며 Jira를 조회하거나 변경하지 않았다.
- 현재 개발 방식을 외부 계약 우선, business capability·data ownership 기준의 서비스 분리, vertical slice 단위 점진 구현, saga·멱등성·outbox/inbox를 통한 분산 정합성, feature flag와 startup validation을 포함한 운영 안전성 중심의 진화적 개발로 정리했다.
- Identity·Learning Core·Billing은 독립 서비스로 분리하지만, 각 서비스 내부에서는 새로운 microservice를 늘리기보다 모듈 경계를 정리하는 실용적 접근임을 설명했다.
- 강점은 호환성·복구 가능성·책임 경계가 명확하다는 점이며, 현재 보완점은 Learning Core 내부 책임 집중, 미완성 서비스 간 event chain, cross-service E2E·tracing·문서 freshness라고 정리했다.
- 애플리케이션 코드·외부 계약·데이터·Jira·Git commit/push는 변경하지 않았다. 분석 설명과 기록 문서 갱신만 수행하므로 Gradle 테스트는 실행하지 않는다.

## 2026-08-31 — AI를 활용한 현재 개발 방식 설명

<!-- codex-turn:01a056bb-60c7-7ed3-b372-0332154a544a -->

- 날짜: 2026-08-31
- Jira: 별도 Jira 이슈 키가 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자의 개발 방식을 AI에게 곧바로 코드 생성을 위임하는 방식이 아니라, 저장소 조사·구조화·시각화·비판을 먼저 맡기고 산출물을 사람이 다시 읽고 옮겨 그리며 판단한 뒤 구현으로 내려가는 AI 매개 개발 방식으로 정리했다.
- AI의 역할을 저장소 탐색자, 시스템 지도 제작자, 용어·컨벤션 감사자, 대안 비교자, 구현·검증 보조자로 구분하고, 사용자는 문제 정의·범위·계약·우선순위·최종 판단을 소유하는 형태임을 설명한다.
- 이 방식은 artifact-driven, repository-grounded, human-in-the-loop 개발로 볼 수 있으며, 강점과 함께 AI 결과의 사실성 검증·인지적 외주화·문서 drift를 주의점으로 기록한다.
- 애플리케이션 코드·외부 계약·데이터·Jira·Git commit/push는 변경하지 않았다. 설명과 기록 문서 갱신만 수행하므로 Gradle 테스트는 실행하지 않는다.

## 2026-08-31 — AI 개발 루프의 Codex 스킬 도입 제안

<!-- codex-turn:01a056be-80c9-7413-96a6-40c6d887646b -->

- 날짜: 2026-08-31
- Jira: 별도 Jira 이슈 키가 없으며 Jira를 조회하거나 변경하지 않았다.
- 공식 OpenAI Codex 스킬 문서를 확인하고, 스킬은 반복 가능한 조사·검증·산출물 워크플로에 적용하되 프로젝트의 상시 제약이나 변경 가능한 사실 전체를 복제하지 않는 방향을 제안했다.
- 권장 최소 구성은 `repo-cartographer`, `contract-guardian`, `vertical-slice-planner`, `release-readiness-reviewer`이며, 먼저 조사·멘토링 준비와 계약 검증 두 스킬로 시작해 실제 반복 과정에서 확장하는 방식을 권고한다.
- `AGENTS.md`는 상시 안전 규칙, 계약 문서는 source of truth, Skill은 조건부 실행 playbook, script는 결정적 검사, template은 산출물 형식으로 책임을 분리해야 한다.
- 사용자 공통 사고법은 user scope 스킬로, Learning Core 고유 계약 검사는 저장소 `.agents/skills`로 두고, 하나의 거대한 만능 스킬이나 Secret·Token·일시적 구현 상태를 스킬에 넣지 않는 방향을 제안한다.
- 이번 작업에서는 스킬을 생성·설치하지 않았고 애플리케이션 코드·외부 계약·데이터·Jira·Git commit/push도 변경하지 않았다. 설명과 기록 문서 갱신만 수행하므로 Gradle 테스트는 실행하지 않는다.

## 2026-08-31 — 토선생 개발과 병행하는 코딩 학습 방식 제안

<!-- codex-turn:01a056de-0d49-7f51-be4a-a0265db7f886 -->

- 날짜: 2026-08-31
- Jira: 별도 Jira 이슈 키가 없으며 Jira를 조회하거나 변경하지 않았다.
- AI 중심 개발에서 시스템 설계·조사 능력과 직접 코드를 회상·작성하는 능력이 서로 다르게 성장할 수 있음을 설명하고, 별도 강의보다 현재 토선생 작업에 예측·직접 작성·AI 리뷰·재구현·회고 단계를 삽입하는 학습 방식을 제안한다.
- 사용자가 핵심 도메인 로직과 테스트의 작은 단위를 먼저 작성하고, AI는 정답 선제시보다 질문·힌트·리뷰·테스트 케이스 제공자로 사용하는 방향을 권고한다.
- 주당 하나의 학습 주제, 30~60분 제한의 작은 실습, 다음 날 무자료 재구현, 주간 설명·회고를 통해 retrieval practice와 feedback loop를 확보하는 구체적인 운영안을 제시한다.
- 학습 진척은 AI 없이 큰 기능을 완성했는지가 아니라 코드를 읽고 실행 결과를 예측하는 능력, 작은 메서드·테스트를 직접 작성하는 능력, 설계 선택과 실패 경로를 설명하는 능력으로 측정한다.
- 애플리케이션 코드·외부 계약·데이터·Jira·Git commit/push는 변경하지 않았다. 학습 제안과 기록 문서 갱신만 수행하므로 Gradle 테스트는 실행하지 않는다.

## 2026-08-31 — AI 구현과 별도 소규모 학습의 이중 트랙 제안

<!-- codex-turn:01a056e3-d91d-7da1-88ce-2621938076c0 -->

- 날짜: 2026-08-31
- Jira: 별도 Jira 이슈 키가 없으며 Jira를 조회하거나 변경하지 않았다.
- 출시해야 할 기능이 많은 현재 상황에서는 생산 코드 구현은 AI에게 적극 맡겨 속도를 유지하고, 최근 구현에서 핵심 개념 하나를 추출해 별도의 짧은 연습으로 복습하는 이중 트랙 방식이 현실적이라고 정리했다.
- 학습이 제품 개발과 분리되지 않도록 무관한 강의 진도보다 그 주에 실제 등장한 테스트, 상태 전이, 멱등성, Spring 경계, Mongo 동작 중 하나를 20~40분짜리 toy exercise 또는 무자료 재구현으로 연습하는 방식을 권고한다.
- 운영안은 구현 완료 후 5분 학습 후보 기록, 주 2~3회 20~40분 연습, 주말 60분 코드 해부·설명으로 구성하고, 학습 결과를 production 코드에 실험적으로 반영하지 않아 출시 위험을 분리한다.
- 이 방식의 위험인 학습 무기한 연기와 수동적 코드 감상을 막기 위해 고정된 최소 시간, 직접 타이핑·예측·실행, 한 주 한 개념 원칙을 제안한다.
- 애플리케이션 코드·외부 계약·데이터·Jira·Git commit/push는 변경하지 않았다. 학습 방식 설명과 기록 문서 갱신만 수행하므로 Gradle 테스트는 실행하지 않는다.

## 2026-08-31 — 코딩 외 개발 역량의 최소 학습 범위 정리

<!-- codex-turn:01a056e9-caee-70a0-a384-3d5baeac1ac9 -->

- 날짜: 2026-08-31
- Jira: 별도 Jira 이슈 키가 없으며 Jira를 조회하거나 변경하지 않았다.
- 사용자는 현재 AI 개발 과정에서 요구사항 범위 설정, 계약 보호, 시스템 구조화, 문서화와 기술적 의사결정을 이미 반복하고 있으므로 이 영역을 별도 강의로 크게 추가할 필요는 없다고 정리했다.
- 직접 코딩 연습 외에 반드시 보완할 최소 영역은 실행 결과 예측, 로그·stack trace·HTTP·DB 상태를 통한 디버깅, 테스트 의도 판단, 로컬·staging 운영 흐름 이해다.
- 각 기능마다 정상 요청 1회와 실패 요청 1회를 직접 관찰하고, 주 1회 요청 하나를 Controller부터 DB·외부 연동까지 추적하며, 월 1회 timeout·중복·의존성 장애 시나리오를 분석하는 낮은 부담의 운영안을 권고한다.
- 네트워크·DB·보안·배포 이론을 별도 대형 커리큘럼으로 선행하지 않고 실제 문제에 등장할 때 관련 개념을 짧게 학습하는 just-in-time 방식을 제안한다.
- 애플리케이션 코드·외부 계약·데이터·Jira·Git commit/push는 변경하지 않았다. 학습 범위 설명과 기록 문서 갱신만 수행하므로 Gradle 테스트는 실행하지 않는다.

## 2026-08-31 — 긴 개발 문서의 선택적 읽기 방식 제안

<!-- codex-turn:01a056f8-e7ad-70b3-a5c4-b4ce99098dbe -->

- 날짜: 2026-08-31
- Jira: 별도 Jira 이슈 키가 없으며 Jira를 조회하거나 변경하지 않았다.
- 모든 문서를 처음부터 끝까지 정독할 필요는 없고, 현재 변경의 계약·결정·위험·실행 방법은 정독하고 배경·비교 자료는 훑으며 사전·전체 inventory·과거 WORKLOG는 필요할 때 조회하는 계층적 읽기 방식을 제안했다.
- 문서를 넘기는 행위보다 중요도를 판단하지 않은 채 핵심 계약과 미결정 사항까지 함께 건너뛰는 것이 위험하다고 정리했다.
- AI 산출물은 결론 5줄, 반드시 읽을 내용, 사용자 결정 필요, 주요 위험, 상세 근거 부록으로 계층화하고, 사용자는 현재 작업에 대해 목적·보호 계약·변경 범위·실패 방식·검증 방법을 설명할 수 있으면 상세 참조 자료를 모두 기억할 필요가 없다고 제안했다.
- `WORKLOG` 전체나 네이밍 사전은 매번 정독하지 않고 검색·참조하며, `CURRENT_STATE`, 관련 계약, 현재 Jira 요구사항과 변경 diff를 우선 읽는 방식을 권고한다.
- 애플리케이션 코드·외부 계약·데이터·Jira·Git commit/push는 변경하지 않았다. 문서 읽기 방식 설명과 기록 문서 갱신만 수행하므로 Gradle 테스트는 실행하지 않는다.

## 2026-08-31 — 10초 챌린지 AI Callback 목적지 확인

<!-- codex-turn:01a05673-23e0-79e0-bae0-302bfbbe32fe -->

- 날짜: 2026-08-31
- 브랜치: `develop`
- Jira: 계약 문서의 관련 이슈는 `TMI-102`, `TMI-105`, `TMI-106`이며 Jira를 조회하거나 변경하지 않았다.
- 승인된 Learning Core–AI v1 계약을 확인한 결과 Callback endpoint는 `POST {LEARNING_CORE_INTERNAL_BASE_URL}/internal/v1/challenges/grading/callback`이다.
- AI는 AI→Learning Core 방향 전용 Bearer credential, `X-Challenge-Contract-Version: v1`, `Content-Type: application/json`을 전송해야 한다. 사용자 Access Token, 기존 시험 `/api/v1/exams/callback/feedback`과 `BaseResponse`는 재사용하지 않는다.
- base URL은 같은 ECS cluster의 private Service Connect 또는 동등한 private discovery 주소를 환경 설정으로 고정하며 Callback request Body에서 받지 않는다. 성공·동일 duplicate·stale generation은 `204 No Content`를 기대한다.
- 현재 Java source와 test를 검색했지만 `/internal/v1/challenges/grading/callback` Controller 구현은 없다. 승인 계약은 구현 전 상태이므로 실제 AI Callback 활성화 전에 Learning Core endpoint 구현·배포, 환경별 internal base URL과 방향별 credential secret 주입이 필요하다.
- 변경 범위: 계약·구현 현황 분석과 기록 문서 갱신만 수행했다. 애플리케이션·공개 API·AI payload 계약·AWS·Jira·Git commit/push는 변경하지 않았고 코드 변경이 없어 Gradle 테스트를 실행하지 않았다. Secret과 Token은 기록하지 않았다.

## 2026-08-31 — 10초 챌린지 사용자 Token과 서비스 인증 구분

- 날짜: 2026-08-31
- 브랜치: `feat/TMI-117-attempt-group-event-consumer`
- Jira: 계약 문서 관련 이슈는 `TMI-102`, `TMI-105`, `TMI-106`이며 Jira를 조회하거나 변경하지 않았다.
- 앱의 사용자 Access Token은 앱→Learning Core 공개 API의 사용자 인증에만 사용하며 Learning Core→AI 평가 요청과 AI→Learning Core Callback으로 전달하지 않는다.
- Learning Core→AI와 AI→Learning Core는 서로 다른 전용 service credential을 사용한다. AI Callback credential은 AWS Secrets Manager 등 secret store에서 AI ECS task 환경으로 직접 주입하므로 AI가 사용자 Token을 알 필요가 없다.
- AI request/Callback에는 `attempt_id`, `job_id`, `grading_attempt` 등 작업 식별자만 전달하고 실제 userId와 사용자 Access Token은 제외한다.
- 변경 범위: 인증 계약 설명과 기록 문서 갱신만 수행했다. 애플리케이션·외부 계약·AWS·Jira·Git commit/push는 변경하지 않았고 코드 변경이 없어 Gradle 테스트를 실행하지 않았다. 실제 Secret과 Token은 기록하지 않았다.

## 2026-08-31 — 10초 챌린지 서비스 인증 설명 종료 기록

<!-- codex-turn:01a05675-ddfa-7672-82f7-87fd40ea67ec -->

- 날짜: 2026-08-31
- 브랜치: `feat/TMI-117-attempt-group-event-consumer`
- Jira: 계약 문서 관련 이슈는 `TMI-102`, `TMI-105`, `TMI-106`이며 Jira를 조회하거나 변경하지 않았다.
- 앱의 사용자 Access Token은 앱→Learning Core 인증에만 사용하고 AI에 전달하지 않는다는 계약을 재확인했다.
- Learning Core→AI와 AI→Learning Core Callback은 서로 다른 방향별 service credential을 사용하며, AI Callback credential은 secret store에서 AI ECS task와 Learning Core 검증 설정에 주입한다. 요청 Body로 전달하지 않는다.
- AI request/Callback에는 Challenge 작업 식별자만 포함하고 실제 userId와 사용자 credential은 제외한다.
- 설명과 기록 동기화만 수행했으며 애플리케이션·계약·AWS·Jira·Git commit/push를 변경하지 않았다. 코드 변경이 없어 Gradle 테스트를 실행하지 않았고 실제 Secret과 Token은 기록하지 않았다.

## 2026-08-31 — Billing TMI-117 완료 확인과 Learning Core 후속 작업 분석

<!-- codex-turn:01a056f2-5591-7a12-a883-97b3cf7f1cb9 -->

- 날짜: 2026-08-31
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: Billing `TMI-117`은 완료 상태이며 이번 작업에서 Jira를 조회하거나 변경하지 않았다. Learning Core 후속 Jira는 아직 생성하지 않았다.
- 확인 결과: Billing `develop@37a3e1df2a0c09e2f804e33a361e0e98335f342f`은 PR `#4` merge commit이고 TMI-117 feature `96a5727a669dd98048bf35581af3faeb1ee5ab07`을 포함한다. merge diff의 `git diff --check`가 통과했다.
- Billing 구현 범위: `POST /internal/v1/attempt-group-events`, 16 KiB strict schema v1 decoder, canonical digest, shared inbox eventId 멱등성, active group/session/subject fencing, `GRADING`·`COMPLETED`·`RETAKE_AVAILABLE` Transaction/CAS 전이, 204/400/409/422/503 응답, Learning Core workload route, trace/metric과 replica-set 테스트가 존재한다. Billing 기록의 전체 테스트 137개 성공을 확인했다.
- 판단: consumer-first 선행 코드가 완료됐으므로 다음 코드 개발은 Learning Core가 맞다. 현재 Learning Core에는 AttemptGroup event outbox/writer/publisher가 없다.
- 다음 작업 범위: `AttemptGroupStatusChanged` outbox entity·index·writer, `GRADING`/terminal 판정과 동일 Mongo Transaction/CAS, Session당 `COMPLETED` 또는 `RETAKE_AVAILABLE` terminal event 하나의 불변식, lease publisher, SigV4·traceparent, HTTP 결과별 retry/dead-letter·보존·replay와 feature flag를 구현한다.
- 배포 순서: Billing consumer staging 선배포·flag/route 검증 → Learning Core writer 배포·publisher off → outbox 적재/중복/terminal 불변식 검증 → SigV4 failure-injection E2E → publisher staging 활성화 순서를 유지한다. Billing staging 활성화 전 publisher activation은 금지한다.
- 범위 제약: 현재 AGENTS의 TMI-116 예외는 AttemptGroup outbox/publisher를 제외하므로 이 작업을 TMI-116에 포함하거나 TMI-117을 재사용하지 않는다. Learning Core 전용 PLAN·신규 Jira와 명시적 허용 범위를 구현 전에 확정해야 한다.
- 변경 파일·테스트: Billing과 Learning Core 코드는 읽기 전용으로 확인하고 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 코드 변경이 없어 Gradle 테스트를 새로 실행하지 않았고 외부 API·AI·S3·Redis 계약, AWS, Jira, Git commit/push와 Secret/Token을 변경하거나 기록하지 않았다.

## 2026-08-31 — AttemptGroup outbox 보존 기간 근거 설명

<!-- codex-turn:01a056f9-51e8-7b32-8145-490b80fa526a -->

- 날짜: 2026-08-31
- 브랜치: `develop`
- Jira: 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 생성하지 않았다. Jira를 조회하거나 변경하지 않았다.
- `DELIVERED` 30일은 발행·수신 eventId와 trace를 대조해 응답 유실, duplicate, projection 불일치와 배포 사고를 조사하는 운영 증거 기간이다. 정상 event를 영구 보존하지 않아 저장 비용과 userId/sessionId 보유 기간을 제한한다.
- `DEAD_LETTER` 90일은 자동 재시도가 중단된 계약·관계·인증 문제를 수정하고 동일 eventId와 payload로 수동 replay할 시간을 확보한다. 미해결 event를 조기에 삭제하면 Billing projection을 복구할 원본과 실패 이력을 잃는다.
- Billing inbox는 120일 보존하므로 Learning Core의 30일·90일 replay 창은 consumer 멱등성 창 안에 있다. 이미 적용된 event의 응답이 유실된 경우에도 same eventId/digest replay가 duplicate 204로 수렴한다.
- `PENDING`은 미전달 업무 event라 TTL 삭제하지 않는다. 30일·90일은 업무 상태나 authorization의 근거가 아니라 운영 기본값이며 on-call SLA·감사·개인정보 정책이 달라지면 양 서비스 계약과 TTL/index를 함께 변경해야 한다.
- 설명과 기록 문서만 갱신했다. 애플리케이션·외부 API·AI·S3·Redis 계약, AWS, Jira, Git commit/push를 변경하지 않았고 코드 변경이 없어 Gradle 테스트를 실행하지 않았다. Secret과 Token은 기록하지 않았다.

## 2026-08-31 — 세 앱 서버 문서 계층·완료 보고 규칙 통일

<!-- codex-turn:01a056fd-926d-7f72-ac19-40c993a65317 -->

- 날짜: 2026-08-31
- 브랜치: Learning Core·Identity·Billing 모두 `develop`
- Jira: 별도 Jira 이슈 키가 없으며 Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 세 앱 서버의 계획·조사 문서를 5줄 결론부터 상세 부록까지 계층화하고, 모든 구현 완료 보고에 변경·계약·테스트·위험·배포 전 확인·예상 밖 diff 확인을 포함하도록 공통 규칙을 추가한다.
- 변경 파일: 세 저장소의 `AGENTS.md`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 변경 내용: 계획·조사·분석·리뷰 결과는 6단계 읽기 계층, 파일 근거, 구현 사실·계획·추론 구분을 따르게 했다. 구현 완료 보고에는 변경 파일·동작, 유지/변경 계약, 테스트와 결과, 위험·미확인, 배포 전 확인, 예상 밖 diff, 다음 확인 사항을 명시하게 했다.
- 유지한 계약: 애플리케이션 코드, 공개 API, Identity JWT/JWKS, Learning Core AI·S3·Redis, Billing internal wire·원장 계약을 변경하지 않았다.
- 검증: 세 저장소에서 `git diff --check`와 변경된 `AGENTS.md` diff를 확인한다. 규칙·기록 문서만 변경했으므로 Gradle 테스트는 실행하지 않는다.
- 위험·배포 전 확인: 문서 규칙 변경이므로 별도 배포는 없다. 향후 보고가 지나치게 길어지지 않도록 상세 근거는 부록으로 이동하고 결론과 위험을 우선 제시해야 한다.
- 예상 밖 diff: 이번 작업과 무관한 기존 Learning Core Controller·architecture 문서와 Billing PLAN·trace handoff 문서 변경이 작업 트리에 있으며 수정하지 않았다. Identity에는 기존 기록 문서 변경 외 예상 밖 애플리케이션 파일이 없다.
- Git commit·push는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-08-31 — AttemptGroup 분산 trace·구조화 관측 계약 검토

<!-- codex-turn:01a056fe-f711-7852-a1b8-610b128672ad -->

- 날짜: 2026-08-31
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core outbox/publisher 후속 Jira는 아직 생성하지 않았다. Jira를 조회하거나 변경하지 않았다.
- 검토 결론: W3C `traceparent`/선택적 `tracestate`, baggage 제외, event JSON·canonical digest·idempotency key에서 trace context 분리, publisher의 새 span context inject, trace 장애와 업무 delivery 분리, 저카디널리티 metric 원칙은 타당하다.
- 필수 보정 1: outbox에는 raw inbound header가 아니라 propagator로 검증한 최소 context인 `traceId`, parent `spanId`, `traceFlags`를 transport metadata로 저장한다. v1에서는 필요성이 확인되지 않은 `tracestate`를 생략하고, 향후 사용 시 W3C 검증·크기 제한·로그 금지를 적용한다.
- 필수 보정 2: `link`는 같은 traceId를 보장하지 않는다. 재시도까지 같은 traceId를 유지하려면 각 publish attempt를 저장된 origin context의 자식인 sibling span으로 만들고, publisher가 만든 현재 span의 context를 HTTP에 inject한다.
- 필수 보정 3: Billing production controller는 inbound traceId를 현재 HTTP server span에 이어받지만 `attempt_group_event_consume`라는 명시적 span은 생성하지 않는다. 정확한 span name이 계약이면 inner internal span 또는 ObservationConvention 보완이 필요하며 현재 통합 테스트의 수동 span 이름을 production 보장으로 해석하지 않는다.
- 필수 보정 4: 401/403은 개별 event를 즉시 DEAD_LETTER로 만들지 않고 전역 auth circuit·alert와 `BLOCKED_AUTH` 또는 장기 backoff PENDING으로 격리한 뒤 같은 eventId로 재개한다. `temporary_failure`의 의미는 `retry_scheduled`와 겹치지 않게 정의하거나 allowlist에서 제거하고, invalid trace는 delivery outcome 대신 별도 counter로 기록한다.
- 필수 보정 5: SigV4 요청에서 trace header의 최종 inject·서명·전송 순서와 자동 HTTP client instrumentation 소유권을 고정한다. 서명 뒤 `traceparent`가 바뀌지 않는지, 저장된 header replay가 아닌 publisher/client 현재 context가 전송되는지 contract test로 검증한다.
- 구조화 로그와 metric 판단: `eventId`는 로그 correlation에만 허용하고 span attribute와 metric tag에서는 제외하는 것이 안전하다. `durationMs`는 `System.nanoTime()` 차이, event age는 wall-clock 값으로 음수 clamp와 clock-skew counter를 유지한다. 사용자·session·group ID, payload/digest, credential/header와 provider 원문은 log/MDC/span에서 제외한다.
- 범위·변경 파일: Learning Core와 Billing 코드는 읽기 전용으로 대조하고 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 현재 TMI-116 예외는 AttemptGroup outbox/publisher를 제외하므로 구현 전 신규 Jira·PLAN·명시적 허용 범위가 필요하다.
- 테스트: 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. `git diff --check`로 기록 문서 형식만 검증한다. 공개 API·AI·S3·Redis·event wire, AWS, Jira, Git commit/push와 Secret/Token은 변경하거나 기록하지 않았다.

## 2026-08-31 — 1차 업데이트 진행 체크리스트 재점검

<!-- codex-turn:01a05701-883a-7f73-858e-70690acc75a2 -->

- 날짜: 2026-08-31
- 브랜치: Learning Core·Identity·Billing 모두 `develop`
- Jira: Identity `TMI-109`·`TMI-111`·`TMI-114`, Billing `TMI-110`·`TMI-112`·`TMI-113`·`TMI-115`·`TMI-117`, Learning Core `TMI-116`, Challenge `TMI-102`·`TMI-105`·`TMI-106`을 현재 상태 근거로 사용했다. Jira를 조회하거나 변경하지 않았다.
- 작업 목표: `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`를 세 저장소의 현재 코드·병합·테스트·최근 Jira 기록에 맞게 갱신해 1차 업데이트의 완료 범위와 production 차단 항목을 한눈에 확인하게 한다.
- 변경 파일: `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드, Identity·Billing 저장소, Jira와 AWS는 변경하지 않았다.
- 최신 완료 반영: Identity `TMI-114`는 PR #36·113 suite/600 tests·Jira 완료, Billing `TMI-115`는 PR #3 병합, Learning Core `TMI-116`은 PR #24 병합·P1/P2 보완·432 tests, Billing `TMI-117`은 PR #4·137 tests·Jira 완료로 반영했다.
- 현재 판정: Identity lifecycle, Billing TrialClaim·BenefitDefinition·Reservation·AttemptGroup consumer와 Learning Core 시험 생성 Billing saga까지 핵심 코드는 구현됐다. 그러나 Learning Core AttemptGroup outbox/publisher, Billing owner rebind, Challenge backend, 실제 Identity→Billing SigV4 transport와 Lattice·migration·mobile/staging E2E가 남아 production 출시는 차단 상태다.
- 유지한 외부 계약: 기존 공개 API URL·Method·Request/Response·`BaseResponse`, 실제 userId 비노출, 기존 시험 retryCount·S3·Redis·Python AI request/Callback의 `user_id=examId` 계약을 변경하지 않았다. Secret과 Token을 기록하지 않았다.
- 테스트·검증: 문서 상태 점검이라 Gradle 테스트는 실행하지 않았다. 세 저장소의 현재 branch·merge commit·구현 파일과 최근 전체 테스트 기록을 읽기 전용 대조했으며 종료 전에 링크·trailing whitespace·marker 단일 포함과 `git diff --check`를 확인한다.
- 남은 위험과 다음 작업: Learning Core AttemptGroup publisher의 PLAN·신규 Jira·명시적 범위를 먼저 만들고 Billing owner rebind를 구현한다. 이어 Identity eligibility SigV4 transport, `TMI-116` Mongo migration·Lattice/IAM/SG·failure-injection E2E, Challenge backend와 모바일 종단 E2E를 통과한 뒤 canary로 feature flag를 연다. Git commit과 push는 사용자가 수행한다.

## 2026-08-31 — AttemptGroup publish 재시도 span 관계 설명

<!-- codex-turn:01a05706-0824-7930-9d85-72b6e0887ca2 -->

- 날짜: 2026-08-31
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core outbox/publisher 후속 Jira는 아직 생성하지 않았다. Jira를 조회하거나 변경하지 않았다.
- 설명: `traceId`를 사건 전체의 폴더 번호, `spanId`를 그 안의 개별 작업 번호로 비유했다. 저장된 origin span을 공통 parent로 사용하면 최초 publish와 모든 retry가 같은 traceId의 sibling span이 되고 각 시도와 Billing 처리는 서로 다른 spanId를 가진다.
- link와의 차이: OpenTelemetry link는 다른 trace에서 원본 trace를 참조하는 연결이므로 같은 traceId 상속을 보장하지 않는다. 동일 traceId 검색이 요구사항이면 retry attempt는 link-only가 아니라 저장된 origin context의 자식으로 생성해야 한다.
- 실패 시나리오: attempt 1이 실패해도 attempt 2를 attempt 1의 자식으로 만들지 않고 둘 다 origin의 자식으로 둔다. 그러면 재시도가 실패 작업 내부 단계가 아니라 같은 event의 독립된 전달 시도라는 의미가 명확해진다.
- missing context: 원본 context가 없는데 재시도끼리도 같은 traceId를 요구한다면 최초 전송 전에 fallback delivery root context를 한 번 생성·저장하고 모든 attempt의 parent로 사용해야 한다.
- 변경·검증: 설명과 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md` 기록만 갱신했다. 애플리케이션·공개 API·event wire·Billing 코드·AWS·Jira·Git commit/push와 Secret/Token은 변경하거나 기록하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.

## 2026-08-31 — AttemptGroup outbox trace metadata 저장 방식 설명

<!-- codex-turn:01a0570b-820f-7442-a3d7-9fd3419eca2b -->

- 날짜: 2026-08-31
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core outbox/publisher 후속 Jira는 아직 생성하지 않았다. Jira를 조회하거나 변경하지 않았다.
- 이해 확인: 같은 사건의 origin, 최초 publish, retry publish와 Billing consume은 동일한 `traceId`를 사용하고 각 실행 단계를 서로 다른 `spanId`로 구분한다.
- 2번 설명: `traceparent`는 version·traceId·spanId·flags가 포장된 HTTP 운송장과 같다. 외부 운송장 문자열 전체를 outbox에 복사하지 않고 W3C propagator로 검증한 뒤 `traceId`, parent `spanId`, `traceFlags`라는 핵심 주소만 transport metadata로 저장한다.
- 전송 동작: publisher는 저장한 parent context로 새로운 publish span을 생성한다. 따라서 outbound `traceparent`는 같은 traceId와 새 publish spanId를 포함하며, 저장된 inbound `traceparent`를 그대로 replay하지 않는다.
- 이유: raw header replay는 publish 단계의 새 span을 표현하지 못하고 malformed/untrusted header를 재전파할 수 있다. trace metadata를 event JSON·digest·idempotency key에서 분리하면 observability 정보 변경이 업무 event 멱등성에도 영향을 주지 않는다.
- 범위·검증: 설명과 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·공개 API·event wire·Billing 코드·AWS·Jira·Git commit/push와 Secret/Token은 변경하거나 기록하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.

## 2026-08-31 — AttemptGroup 서비스 경계 span과 Billing span name 설명

- 날짜: 2026-08-31
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core outbox/publisher 후속 Jira는 아직 생성하지 않았다. Jira를 조회하거나 변경하지 않았다.
- 서비스 경계 설명: 새 서버로 넘어갈 때 같은 spanId를 수정·재사용하는 것이 아니라, 동일한 traceId를 상속하면서 해당 서버의 새 작업을 나타내는 새로운 spanId를 발급한다.
- Billing 현재 동작: W3C inbound context로 Learning Core traceId를 이어받고 Spring HTTP server span을 새로 만들기 때문에 trace 연결은 이미 가능하다.
- 3번의 핵심: 구조화 로그의 `operation=attempt_group_event_consume`과 실제 tracing span name은 별개다. 현재 production 코드는 명시적인 `attempt_group_event_consume` span을 만들지 않아 trace UI에서는 일반 HTTP server 이름으로 보일 수 있다.
- 권장 구조: Billing HTTP server span 아래 `attempt_group_event_consume` internal span을 추가해 HTTP 수신과 업무 event decode·처리 단계를 구분한다. 두 span은 같은 traceId와 서로 다른 spanId를 가진다.
- 범위·검증: 설명과 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·공개 API·event wire·Billing 코드·AWS·Jira·Git commit/push와 Secret/Token은 변경하거나 기록하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.

## 2026-08-31 — AttemptGroup 서비스 경계 span 설명 종료 기록

<!-- codex-turn:01a0570e-b556-7653-9665-626d3a77d77c -->

- 날짜: 2026-08-31
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core outbox/publisher 후속 Jira는 아직 생성하지 않았다. Jira를 조회하거나 변경하지 않았다.
- 최종 설명: 새 서버나 처리 단계는 기존 spanId를 수정하지 않고 같은 traceId를 이어받은 새로운 spanId를 생성한다. 이를 통해 하나의 사건 안에서 Learning Core publish, HTTP 전송, Billing 수신과 업무 처리를 구분한다.
- Billing 현황: W3C inbound traceId 연동과 HTTP server span 생성은 이미 동작한다. 다만 로그의 `operation=attempt_group_event_consume`은 tracing span name과 별개이므로 trace UI에 명시적인 업무 span을 표시하려면 HTTP server span 아래 internal span을 추가해야 한다.
- 변경·검증: 설명과 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·공개 API·event wire·Billing 코드·AWS·Jira·Git commit/push와 Secret/Token은 변경하거나 기록하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.

## 2026-08-31 — AttemptGroup Billing 업무 span과 인증 장애 처리 설명

<!-- codex-turn:01a05714-38f7-7b51-8c64-2970fddc3ebf -->

- 날짜: 2026-08-31
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core outbox/publisher 후속 Jira는 아직 생성하지 않았다. Jira를 조회하거나 변경하지 않았다.
- 3번 설명: 구조화 로그를 장문으로 늘리는 것이 아니라 HTTP 수신 span 아래 명시적인 `attempt_group_event_consume` 업무 span을 추가해 trace 화면에서 transport와 decode·DB 반영의 시간·실패 지점을 구분한다. 기존 고정 필드 로그 규격은 유지한다.
- 4번 설명: 400·409·422는 특정 event의 계약·충돌 문제이므로 DEAD_LETTER 대상이지만 401·403은 대체로 IAM·SigV4·route 같은 전역 출입키 문제다. 모든 정상 event를 DEAD_LETTER로 이동시키지 않는다.
- 권장 수렴: 401·403 발생 시 `auth_failure` 로그·counter와 긴급 경보를 남기고 publisher circuit을 열어 추가 호출을 멈춘다. event는 `BLOCKED_AUTH` 또는 장기 backoff PENDING으로 보존하고 운영 복구 후 동일 eventId·payload로 재전송한다.
- 범위·검증: 설명과 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·공개 API·event wire·Billing 코드·AWS·Jira·Git commit/push와 Secret/Token은 변경하거나 기록하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.

## 2026-08-31 — AttemptGroup span 추가와 오류 분류 의미 재확인

- 날짜: 2026-08-31
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core outbox/publisher 후속 Jira는 아직 생성하지 않았다. Jira를 조회하거나 변경하지 않았다.
- 3번 재확인: append할 대상은 로그 line이 아니라 trace의 internal span 하나다. Billing HTTP server span 아래 `attempt_group_event_consume` span을 추가하며 기존 구조화 로그 field 수와 message는 유지한다.
- 구간 해석: Learning Core publish/client span은 전송 전체, Billing HTTP server span은 서버 요청 처리 전체, consume span은 event decode·멱등성·Mongo 반영 구간을 나타낸다. 세 duration을 비교해 지연 위치를 좁힌다.
- 4번 재확인: 오류를 더 자세히 분류하는 동시에 실제 동작도 다르게 한다. network·408·425·429·5xx는 retry schedule, 400·409·422는 event DEAD_LETTER, 401·403은 전역 auth circuit·alert와 event 보존 후 복구 재개로 수렴한다.
- 범위·검증: 설명과 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·공개 API·event wire·Billing 코드·AWS·Jira·Git commit/push와 Secret/Token은 변경하거나 기록하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.

## 2026-08-31 — AttemptGroup span·오류 분류 설명 종료 기록

<!-- codex-turn:01a05716-2c75-7f33-8e06-21fa5d3f6f1b -->

- 날짜: 2026-08-31
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core outbox/publisher 후속 Jira는 아직 생성하지 않았다. Jira를 조회하거나 변경하지 않았다.
- 최종 이해: 3번은 구조화 로그를 append하는 것이 아니라 Billing HTTP server span 아래 업무 internal span을 하나 더 만드는 관측성 보완이다. 4번은 오류 종류를 세분화하고 retry·DEAD_LETTER·auth circuit이라는 실제 후속 동작도 다르게 하는 정책이다.
- 변경·검증: 설명과 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·공개 API·event wire·Billing 코드·AWS·Jira·Git commit/push와 Secret/Token은 변경하거나 기록하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.

## 2026-08-31 — AttemptGroup trace header와 SigV4 순서 설명

- 날짜: 2026-08-31
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core outbox/publisher 후속 Jira는 아직 생성하지 않았다. Jira를 조회하거나 변경하지 않았다.
- 5번 설명: SigV4를 최종 요청에 찍는 봉인 도장으로 비유했다. publish span 생성 → 새 `traceparent` inject → 완성된 header·body SigV4 서명 → 이후 변경 없이 전송 순서로 고정한다.
- 실패 위험: 서명 후 자동 HTTP tracing이 `traceparent`를 추가하거나 바꾸면 실제 전송 요청이 서명 계산 당시와 달라져 Billing 인증이 실패할 수 있다. 이 SigV4 client에서는 manual publisher tracing과 자동 client instrumentation 중 inject 주체를 하나로 고정해야 한다.
- 재시도: 동일 eventId와 canonical payload는 유지하지만 publish attempt별 spanId와 서명 시각이 다르므로 `traceparent`와 SigV4 서명은 매번 새로 만든다. 이전에 서명한 HTTP request를 replay하지 않는다.
- 범위·검증: 설명과 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·공개 API·event wire·Billing 코드·AWS·Jira·Git commit/push와 Secret/Token은 변경하거나 기록하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.

## 2026-08-31 — AttemptGroup SigV4 순서 설명 종료 기록

<!-- codex-turn:01a05718-6795-7882-a996-1343bde5718d -->

- 날짜: 2026-08-31
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core outbox/publisher 후속 Jira는 아직 생성하지 않았다. Jira를 조회하거나 변경하지 않았다.
- 최종 설명: publish span 생성과 `traceparent` inject를 먼저 완료하고, 그 최종 요청을 SigV4로 서명한 뒤 header를 변경하지 않고 보낸다. 서명 후 자동 instrumentation이 trace header를 바꾸지 않도록 inject 소유자를 하나로 제한한다.
- 재시도 원칙: eventId와 canonical payload는 유지하지만 새 publish spanId와 서명 시각을 사용하므로 HTTP request와 SigV4 서명은 매 attempt 새로 만든다.
- 변경·검증: 설명과 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·공개 API·event wire·Billing 코드·AWS·Jira·Git commit/push와 Secret/Token은 변경하거나 기록하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.

## 2026-08-31 — AttemptGroup SigV4 최종 단계 확인

- 날짜: 2026-08-31
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core outbox/publisher 후속 Jira는 아직 생성하지 않았다. Jira를 조회하거나 변경하지 않았다.
- 확인: SigV4 서명은 request method·URI·body·일반 header와 `traceparent`가 모두 확정된 뒤 전송 직전 마지막 논리적 변경 단계에서 수행한다.
- 허용 경계: 서명 결과를 실제 HTTP client request로 복사·변환할 수는 있지만 서명 대상 header·body·path를 바꾸거나 자동 instrumentation이 trace header를 다시 쓰면 안 된다.
- 범위·검증: 설명과 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·공개 API·event wire·Billing 코드·AWS·Jira·Git commit/push와 Secret/Token은 변경하거나 기록하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.

## 2026-08-31 — AttemptGroup SigV4 마지막 서명 원칙 종료 기록

<!-- codex-turn:01a0571b-2678-7332-a60b-e44850c9f501 -->

- 날짜: 2026-08-31
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core outbox/publisher 후속 Jira는 아직 생성하지 않았다. Jira를 조회하거나 변경하지 않았다.
- 최종 확인: URL·method·body·일반 header와 `traceparent`를 모두 확정하고 SigV4를 마지막 논리적 변경 단계에서 서명한 뒤 Billing 전송 전 요청 값을 바꾸지 않는다.
- 허용 경계: signed request를 실제 HTTP client 객체로 복사하는 형식 변환은 허용하지만 서명 대상 값이나 trace header 재주입은 허용하지 않는다.
- 변경·검증: 설명과 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·공개 API·event wire·Billing 코드·AWS·Jira·Git commit/push와 Secret/Token은 변경하거나 기록하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.

## 2026-08-31 — AttemptGroup 분산 trace 계약 최종 확정 판단

- 날짜: 2026-08-31
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core outbox/publisher 후속 Jira는 아직 생성하지 않았다. Jira를 조회하거나 변경하지 않았다.
- 확정 판단: 사용자와 검토한 다섯 보정사항은 권장안 기준으로 구현 계획에 그대로 고정할 수 있다. 동일 traceId·단계별 새 spanId, retry sibling, 검증된 최소 trace metadata 저장, Billing internal consume span, 오류별 상태·행동 분리와 trace inject 후 최종 SigV4 서명을 사용한다.
- 최종 세부값: v1 `tracestate`·baggage·raw trace header는 저장하지 않는다. missing/invalid context에는 최초 fallback trace anchor를 한 번 CAS 보존한다. publisher outcome은 `delivered`, `retry_scheduled`, `dead_letter`, `auth_failure`, `lease_lost`이며 `temporary_failure`는 제거한다.
- 인증 오류: 401·403은 event를 `BLOCKED_AUTH`로 보존하고 publisher 전역 circuit·alert 후 복구 시 같은 eventId·payload로 재개한다. network·408·425·429·5xx는 retry, 400·409·422는 DEAD_LETTER로 수렴한다.
- 구현 전 경계: 계약 검토는 끝났지만 Learning Core publisher 코드 작업 전 신규 Jira·PLAN과 현재 TMI-116 제외 범위를 해소하는 AGENTS 명시적 예외가 필요하다. exporter/backend·dashboard·alert 인프라는 별도 운영 범위다.
- 범위·검증: 분석과 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·공개 API·event wire·Billing 코드·AWS·Jira·Git commit/push와 Secret/Token은 변경하거나 기록하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.

## 2026-08-31 — AttemptGroup 분산 trace 계약 동결 종료 기록

<!-- codex-turn:01a0571d-159e-7f21-96c1-380a0cf82836 -->

- 날짜: 2026-08-31
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core outbox/publisher 후속 Jira는 아직 생성하지 않았다. Jira를 조회하거나 변경하지 않았다.
- 종료 상태: 동일 traceId·단계별 새 spanId, 최소 trace metadata, retry sibling, Billing internal consume span, 오류별 retry/dead-letter/auth-block, trace inject 후 최종 SigV4 서명을 권장안으로 동결했다.
- 다음 단계: Learning Core 신규 Jira·PLAN과 TMI-116 제외 범위를 해소하는 AGENTS 명시적 예외를 작성한 뒤 구현한다. exporter/backend·dashboard·alert 인프라는 별도 운영 범위로 유지한다.
- 변경·검증: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·공개 API·event wire·Billing 코드·AWS·Jira·Git commit/push와 Secret/Token은 변경하거나 기록하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.

## 2026-08-31 — AttemptGroup trace Billing 전달 범위 확인

- 날짜: 2026-08-31
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: 관련 완료 이슈는 Billing `TMI-117`이며 Billing 보완 또는 Learning Core outbox/publisher 후속 Jira는 아직 생성하지 않았다. Jira를 조회하거나 변경하지 않았다.
- 확인 결과: Billing은 W3C-only context propagation, inbound traceId 연속성, 구조화 로그와 저카디널리티 metric 기반이 이미 있어 endpoint·event JSON·HTTP status 계약을 바꿀 필요는 없다.
- Billing 전달 필수사항: production HTTP server span 아래 `attempt_group_event_consume` internal span을 decode·service 처리 범위에 추가한다. 현재 통합 테스트의 수동 span 생성만으로 production span name을 보장하지 않으므로 실제 controller 요청에서 origin/Billing traceId 동일, HTTP/consume spanId 상이, 정확한 inner span name을 검증한다.
- 책임 분리: outbox 최소 trace metadata, retry sibling/fallback anchor, publisher outcome, `BLOCKED_AUTH`·circuit, trace inject 후 SigV4 최종 서명은 Learning Core 책임이다. Billing은 401·403 등 status 경계와 baggage 미전파·민감정보 비기록을 유지한다.
- 선택 보완: missing과 invalid를 함께 담는 trace-context metric 이름을 명확히 하고, inner span이 정상·예외 모두 종료되며 payload·식별자·credential을 attribute로 기록하지 않는 테스트를 추가한다. 실제 exporter/backend·dashboard·alert는 별도 운영 작업이다.
- 범위·검증: Billing 코드는 읽기 전용으로 확인하고 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·공개 API·event wire·AWS·Jira·Git commit/push와 Secret/Token은 변경하거나 기록하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.

## 2026-08-31 — AttemptGroup trace Billing 전달 종료 기록

<!-- codex-turn:01a0571f-f787-74d3-9581-cfed73a4f1df -->

- 날짜: 2026-08-31
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: 관련 완료 이슈는 Billing `TMI-117`이며 Billing 보완 또는 Learning Core outbox/publisher 후속 Jira는 아직 생성하지 않았다. Jira를 조회하거나 변경하지 않았다.
- 전달 결과: Billing에는 production HTTP server span 아래 `attempt_group_event_consume` internal span 추가와 실제 Controller 요청 기반 same traceId·different spanId·exact span name·baggage 미전파 테스트를 전달한다.
- 계약 유지: Billing endpoint, event JSON, HTTP status, 기존 구조화 로그와 metric 의미는 유지한다. outbox trace metadata·retry·`BLOCKED_AUTH`·circuit과 SigV4 최종 서명은 Learning Core 책임이다.
- 변경·검증: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. Billing·Learning Core 애플리케이션·공개 API·event wire·AWS·Jira·Git commit/push와 Secret/Token은 변경하거나 기록하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.

## 2026-09-01 — Billing AttemptGroup consume span 구현 확인과 Learning Core 다음 작업

- 날짜: 2026-09-01
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: 관련 완료 이슈는 Billing `TMI-117`이며 Billing trace 보완 또는 Learning Core outbox/publisher 후속 Jira는 아직 생성하지 않았다. Jira를 조회하거나 변경하지 않았다.
- Billing 구현 확인: 로컬 `develop@37a3e1d` 위 미커밋 변경에서 `AttemptGroupEventTracing`이 `attempt_group_event_consume` span을 생성하고 Controller의 strict decode·service 범위를 감싼다. 정상·RuntimeException·Error에서 scope를 닫고 span을 종료하며 예외는 error로 기록한다.
- 테스트 확인: 실제 embedded Tomcat 요청으로 inbound traceId 유지, HTTP SERVER와 INTERNAL consume의 다른 spanId 및 descendant 관계, 정확한 이름, decoder/service 동일 scope, baggage 미전파, 정상·409 오류 span 종료와 금지 attribute 부재를 검증한다.
- 실행 테스트와 결과: Billing에서 `./gradlew clean test`를 직접 재실행해 총 138개 성공, 실패 0, 오류 0, skip 0과 `BUILD SUCCESSFUL`을 확인했다. Billing과 Learning Core 기록 문서의 `git diff --check`도 통과했다.
- 완료 판정: 요청한 Billing 코드 보완은 구현·검증됐으나 아직 commit·push·PR·merge되지 않은 로컬 변경이다. 사용자 또는 Billing 팀이 Git 반영과 배포를 완료해야 실제 환경 기준 완료다.
- 다음 작업 설명: Billing 변경을 먼저 merge·배포한 뒤 Learning Core 신규 Jira·PLAN·AGENTS 예외를 만든다. 이후 시험 상태와 동일 Transaction/CAS로 outbox 영수증을 저장하고 lease publisher가 이를 가져가 W3C trace와 최종 SigV4 요청으로 Billing에 전달한다.
- 구현 전 결정: GRADING 시작 trigger, COMPLETED의 requiredFeedbackQueryable·validScoreQueryable·summaryQueryable source, RETAKE_AVAILABLE failureCode, Session당 terminal event 하나, lease/retry/`BLOCKED_AUTH`·retention과 feature flag를 계획서에서 코드 경계에 고정한다.
- 유지한 계약: 기존 공개 API·BaseResponse·사용자 식별·retryCount·S3·Redis·Python AI request/Callback과 Billing endpoint·event JSON·HTTP status를 변경하지 않았다.
- 예상 밖 diff: Learning Core에는 기존 사용자 변경인 `AGENTS.md`, `FIRST_UPDATE_PROGRESS_CHECKLIST.md`, withdrawal Controller와 `docs/architecture/`가 남아 있어 수정하지 않았다. Billing에는 이번 trace 보완과 문서 변경이 모두 미커밋 상태이며 별도 예상 밖 애플리케이션 변경은 확인하지 않았다.
- 범위: Billing은 읽기 전용 검토와 테스트만 수행했고 코드를 수정하지 않았다. Learning Core는 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. AWS·Jira·Git commit/push와 Secret/Token을 변경하거나 기록하지 않았다.

## 2026-09-01 — Billing consume span 검증 종료 기록

<!-- codex-turn:01a05a8d-72ef-7183-bca6-07cbc40f94c5 -->

- 날짜: 2026-09-01
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: 관련 완료 이슈는 Billing `TMI-117`이며 Billing trace 보완 또는 Learning Core outbox/publisher 후속 Jira는 아직 생성하지 않았다. Jira를 조회하거나 변경하지 않았다.
- 종료 판정: Billing production consume span, actual HTTP trace/span·baggage·privacy·오류 테스트와 전체 138개 회귀 테스트 성공을 확인했다. 새 merge 차단 결함은 확인되지 않았다.
- 현재 경계: Billing 변경은 로컬 `develop` 위 미커밋 상태이므로 commit·push·PR·merge·배포 전에는 원격 및 실제 환경 완료로 보지 않는다.
- 다음 작업: Billing 반영 후 Learning Core 신규 Jira·PLAN·AGENTS 예외를 확정하고 상태 전이+outbox 동일 Transaction/CAS, lease publisher, W3C trace, 최종 SigV4와 오류 수렴을 구현한다.
- 변경·검증: Billing은 읽기 전용 검토와 `./gradlew clean test`만 수행했다. Learning Core는 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했고 `git diff --check`를 통과했다. 공개 API·AI·S3·Redis·event wire·AWS·Jira·Git commit/push와 Secret/Token을 변경하거나 기록하지 않았다.

## 2026-09-01 — IntelliJ 학습 프로젝트 구성 방향

<!-- codex-turn:01a05a9b-acf1-7a30-b575-e2a6e4a7c285 -->

- 날짜: 2026-09-01
- 브랜치: `develop`
- Jira: 별도 Jira 이슈 키가 없으며 Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 토선생 개발과 병행할 별도 IntelliJ 학습 프로젝트를 Spring Boot로 시작해야 하는지 판단한다.
- 결론: Java 문법, 도메인 로직, 상태 전이, 멱등성, JUnit·Mockito 연습은 Java 21+Gradle+JUnit의 가벼운 프로젝트로 시작하고, DI·Controller·Validation·Transaction·Repository 등 Spring 동작을 학습할 때만 별도 Spring Boot 모듈을 추가하는 구성을 권고한다.
- 권장 구조: 하나의 학습 저장소 안에 `java-lab`을 기본으로 두고 필요 시 `spring-lab`을 추가해 production 저장소와 연습 코드를 분리한다. 처음부터 MongoDB·Redis·AWS 같은 운영 의존성을 넣지 않는다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. 애플리케이션·학습 프로젝트 파일은 생성하지 않았다.
- 유지한 계약: Learning Core 공개 API, AI·S3·Redis와 사용자 식별 계약을 변경하지 않았다.
- 테스트·검증: 설명과 기록 문서만 변경했으므로 Gradle 테스트는 실행하지 않고 `git diff --check`로 검증한다.
- 위험·미확인: 실제 학습 프로젝트의 단일 프로젝트/멀티 모듈 선택과 package 이름은 생성 시 확정하면 된다. 배포 전 확인 사항은 없으며 이번 작업과 무관한 기존 dirty worktree 파일은 수정하지 않았다.
- Git commit·push는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — Billing trace merge 확인과 Learning Core AttemptGroup outbox 계획

<!-- codex-turn:01a05a9a-1ecf-7022-84dc-f6ee28e86766 -->

- 날짜: 2026-09-01
- 브랜치: Learning Core `develop`; Billing `develop`
- Jira: 선행 Billing 이슈는 `TMI-117`이며 Learning Core AttemptGroup outbox/publisher 신규 Jira는 아직 생성되지 않았다. Jira를 조회하거나 변경하지 않았다.
- Billing merge 확인: Billing `develop == origin/develop == a34766e`, PR #5 merge와 포함 commit `b1f6fbd`, clean worktree를 확인했다. production `attempt_group_event_consume` span과 실제 HTTP same traceId/different spanId·baggage 차단·오류 종료·privacy 테스트가 병합됐다. 병합 직전 동일 코드의 전체 138개 테스트 성공 기록을 근거로 사용했으며 이번 turn에는 Billing 테스트를 다시 실행하지 않았다.
- 계획 문서: `docs/codex/ATTEMPT_GROUP_OUTBOX_PUBLISHER_IMPLEMENTATION_PLAN.md`를 추가했다. GRADING/COMPLETED/RETAKE_AVAILABLE 판정, strict evidence와 failureCode, Session당 terminal 하나, ExamSession+outbox Mongo Transaction/CAS, Summary callback transaction 재구성, RETAKE replacement source snapshot, lease·retry·retention·auth circuit을 코드 경계에 매핑했다.
- trace·전송: outbox에는 검증된 `traceId`, `parentSpanId`, `traceFlags`만 저장하고 raw traceparent/tracestate와 baggage를 저장하지 않는다. retry마다 같은 eventId/payload와 새 publish span/SigV4 서명을 사용하며 trace inject 후 마지막 논리 단계에서 서명한다.
- 사용자 결정: 구현 전 신규 Jira와 `AGENTS.md` 명시적 예외가 필요하다. GRADING final deadline은 `PT30M`, 신규 Billing-linked Summary source는 `exam_summaries` only, poll 1초·batch 20·lease 30초·auth probe 15분을 권장안으로 문서화했다.
- 변경 파일: `docs/codex/ATTEMPT_GROUP_OUTBOX_PUBLISHER_IMPLEMENTATION_PLAN.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 유지한 외부 계약: 공개 API URL·Method·Parameter·Request/Response DTO·BaseResponse, 실제 userId 비노출, Python AI `user_id=examId`와 Callback JSON, retryCount, Redis, S3, 음성 submit·Polling과 Billing event wire를 변경하지 않았다.
- 테스트·검증: 문서만 변경했으므로 Learning Core Gradle 테스트는 실행하지 않는다. `git diff --check`와 문서 marker·Git diff 범위 확인으로 검증한다.
- 위험·배포 전 확인: 현재 TMI-116 예외는 outbox/publisher를 제외하므로 코드 구현 전 신규 Jira·예외가 필수다. Billing consumer 포함 image 배포·feature flag, Mongo replica-set/index, Lattice IAM과 failure-injection E2E가 production gate다.
- 예상 밖 diff: 기존 사용자 변경인 `AGENTS.md`, `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`, withdrawal Controller와 `docs/architecture/`는 수정하지 않았다. Git commit·push, AWS와 Secret/Token 변경·기록은 수행하지 않았다.

## 2026-09-01 — AttemptGroup 구현 전 선택지와 장단점 정리

<!-- codex-turn:01a05aba-d737-7343-8726-3456d1453467 -->

- 날짜: 2026-09-01
- 브랜치: Learning Core `develop`
- Jira: 선행 Billing 이슈는 `TMI-117`이며 Learning Core AttemptGroup outbox/publisher 신규 Jira는 아직 생성되지 않았다. Jira를 조회하거나 변경하지 않았다.
- 작업 목표: AttemptGroup outbox/publisher 구현 전에 확정해야 할 제품·운영 선택지를 대안별 장단점과 권장안으로 설명한다.
- 필수 선택: GRADING deadline은 15분/30분/60분/무제한 중 `PT30M`, 최종 실패는 retry 소진 즉시/deadline only/단계적 확정 중 단계적 확정, Summary source는 strict/legacy fallback/cutover 혼합 중 신규 Billing-linked `exam_summaries` only를 권장했다.
- 운영 선택: 401/403은 15분 단일 half-open/수동 해제/일반 retry 중 half-open을 권장하고, 기존 linked Session은 cutover 이후만/전부 자동/allowlist backfill 중 inventory·dry-run 후 allowlist backfill을 권장했다.
- 기술 기본값: poll 1초, batch 20, lease 30초, writer/publisher 기본 off는 설정으로 조정할 수 있어 별도 제품 결정 없이 권장값으로 분리했다.
- 변경 파일: `docs/codex/ATTEMPT_GROUP_OUTBOX_PUBLISHER_IMPLEMENTATION_PLAN.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 유지한 계약: 공개 API URL·Method·Parameter·DTO·BaseResponse, 사용자 식별, retryCount, Redis·S3, Python AI `user_id=examId`와 Callback JSON, Billing event wire를 변경하지 않았다.
- 테스트·검증: 문서만 변경했으므로 Gradle 테스트는 실행하지 않고 `git diff --check`, Markdown fence와 marker 중복 검사를 수행한다.
- 위험·배포 전 확인: 신규 Jira와 해당 Jira용 `AGENTS.md` 예외가 구현 전 필요하다. 선택값 확정 후 계획서 권장안을 확정값으로 바꾸고 Billing consumer 배포, Mongo index/Transaction, Lattice IAM과 failure-injection E2E를 확인해야 한다.
- 예상 밖 diff: 기존 사용자 변경 파일은 수정하지 않았다. 애플리케이션·AWS·Jira·Git commit/push와 Secret/Token 변경·기록은 수행하지 않았다.

## 2026-09-01 — AttemptGroup 정책 확정과 AGENTS 영구 허용

<!-- codex-turn:01a05abe-9b11-7050-a521-c033828af412 -->

- 날짜: 2026-09-01
- 브랜치: Learning Core `develop`
- Jira: 선행 Billing 이슈는 `TMI-117`이며 Learning Core AttemptGroup outbox/publisher 신규 Jira는 아직 생성되지 않았다. Jira를 조회하거나 변경하지 않았다.
- 사용자 확정: `1B·2C·3A·4A·5C` 조합을 승인했다. GRADING `PT30M`, 단계적 최종 실패, 신규 linked Session의 `exam_summaries` only, 401/403 15분 단일 half-open, inventory/dry-run 후 allowlist backfill을 확정값으로 반영했다.
- AGENTS 변경: Jira별 일회성 예외 대신 AttemptGroup 상태 판정·durable outbox·lease publisher·제한된 reconciliation·RETAKE replacement 연결을 지속 허용하는 규칙을 추가했다. TMI-116의 제외는 해당 Jira 범위 제한일 뿐 후속 영구 허용을 취소하지 않는다고 명시했다.
- 허용 경계: Learning Core 내부 Transaction/CAS, outbox, SigV4, W3C trace, retry·retention·auth circuit과 테스트만 허용한다. 공개 API·AI·S3·Redis 계약, Billing consumer/저장소, UserMerged·owner rebind·결제 보상과 실제 AWS 리소스 작업은 금지 상태를 유지한다.
- Jira 역할: 신규 Jira는 기능 허가 조건이 아니라 작업 추적·완료 조건 관리용이다. Jira 키가 정해지면 계획·CURRENT_STATE·WORKLOG에 기록하되 Jira별 AGENTS 예외는 추가하지 않는다.
- 변경 파일: `AGENTS.md`, `docs/codex/ATTEMPT_GROUP_OUTBOX_PUBLISHER_IMPLEMENTATION_PLAN.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 유지한 외부 계약: 공개 API URL·Method·Parameter·DTO·BaseResponse, 사용자 식별, retryCount, Redis·S3, Python AI `user_id=examId`와 Callback JSON, Billing event wire를 변경하지 않았다.
- 테스트·검증: 문서와 작업 지침만 변경했으므로 Gradle 테스트는 실행하지 않고 `git diff --check`, Markdown fence, marker 중복과 AGENTS/계획서 상호 일치 검사를 수행한다.
- 위험·배포 전 확인: 영구 허용이 관련 없는 Billing·결제·인프라 변경으로 확대되지 않도록 각 구현 diff를 금지 범위와 대조한다. Billing consumer 배포, Mongo Transaction/index, Lattice IAM과 failure-injection E2E 전 production writer를 활성화하지 않는다.
- 예상 밖 diff: 기존 사용자 변경인 progress checklist, withdrawal Controller와 `docs/architecture/`는 수정하지 않았다. 애플리케이션·AWS·Jira·Git commit/push와 Secret/Token 변경·기록은 수행하지 않았다.

## 2026-09-01 — TMI-118 AttemptGroup outbox/publisher Jira 생성

- 날짜: 2026-09-01
- 브랜치: Learning Core `develop`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`을 `작업` 유형과 `해야 할 일` 상태로 생성했다. 선행 이슈는 Learning Core `TMI-116`과 Billing `TMI-117`이다.
- 이슈 범위: GRADING/COMPLETED/RETAKE_AVAILABLE 판정, Session당 terminal 하나, local 상태+outbox Mongo Transaction/CAS, Summary callback transaction 수렴, RETAKE replacement 연결, lease publisher, retry·retention·auth circuit, W3C trace와 trace inject 후 SigV4를 포함했다.
- 확정 정책: GRADING `PT30M`, 단계적 최종 실패, 신규 Billing-linked `exam_summaries` only, 401/403 15분 단일 half-open, inventory/dry-run 후 allowlist backfill과 feature flag 기본 off를 기록했다.
- 유지·제외 범위: 공개 API·BaseResponse·사용자 식별·retryCount·Redis·S3·Python AI 계약을 유지하고 Billing consumer, UserMerged·owner rebind, 결제·보상, Challenge, 실제 AWS 리소스와 exporter/dashboard를 제외했다.
- 완료 조건: strict evidence/failureCode, Transaction·unknown commit, terminal race, lease·retry·retention·auth, same trace/different span, privacy, 전체 회귀와 production activation gate를 Jira에 명시했다.
- 변경 파일: `docs/codex/ATTEMPT_GROUP_OUTBOX_PUBLISHER_IMPLEMENTATION_PLAN.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트·검증: Jira 생성과 문서 갱신만 수행했으므로 Gradle 테스트는 실행하지 않고 Jira 응답, `git diff --check`, 계획서 Jira 키와 작업 기록을 검증한다.
- 예상 밖 diff: 기존 사용자 변경 파일은 수정하지 않았다. 애플리케이션·AWS·Git commit/push와 Secret/Token 변경·기록은 수행하지 않았다.

## 2026-09-01 — TMI-118 Jira 생성 종료 기록 동기화

<!-- codex-turn:01a05ac0-e3e8-7eb2-9dc6-2735e3a3e2e2 -->

- 날짜: 2026-09-01
- 브랜치: Learning Core `develop`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`을 생성 완료했으며 상태는 `해야 할 일`이다. 선행 이슈는 `TMI-116`, `TMI-117`이다.
- 종료 상태: 확정 정책 `1B·2C·3A·4A·5C`, 구현·제외 범위, 완료 조건과 production 활성화 gate가 Jira와 구현 계획서에 반영돼 있다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`에 종료 동기화 기록을 추가했다.
- 검증: 애플리케이션 변경이 없어 Gradle 테스트는 실행하지 않고 marker 단일성, Jira 키 기록과 `git diff --check`를 확인한다.
- 범위: Jira를 추가 수정하거나 애플리케이션·AWS·Git commit/push를 변경하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — TMI-118 AttemptGroup durable outbox/publisher 구현

<!-- codex-turn:01a05ac3-3ae0-7583-86de-3cad2b909487 -->

- 날짜: 2026-09-01
- 브랜치: `develop`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`
- 구현 결과: Billing-linked·CONFIRMED Session에만 local `OPEN|GRADING|COMPLETED|RETAKE_AVAILABLE` projection을 적용했다. 필수 retry 0 제출, strict feedback·score·결정적 `exam_summaries` evidence와 단계적 고정 failureCode를 판정하고 Session 상태와 GRADING/TERMINAL outbox를 같은 Mongo Transaction·optimistic version·unique slot으로 저장한다.
- Summary 수렴: writer 대상 Summary Callback은 결정적 Summary insert, Summary Job COMPLETED, ExamSession terminal과 terminal outbox를 같은 Mongo Transaction으로 처리하고 Redis 상태 projection은 commit 뒤 갱신한다. 중복 Callback과 reconciler는 결정적 ID·Session version·outbox slot으로 수렴한다.
- publisher: `attempt_group_event_outbox` lease claim/reclaim, 동일 eventId·canonical payload, 5초→15초→1분→5분→15분 bounded retry, 모든 2xx DELIVERED, 400/409/422 및 기타 non-retryable 4xx DEAD_LETTER, 401/403 BLOCKED_AUTH와 durable global circuit·15분 단일 half-open probe를 구현했다. DELIVERED는 30일, DEAD_LETTER는 90일 TTL이며 PENDING/IN_FLIGHT/BLOCKED_AUTH는 TTL이 없다.
- trace·보안: outbox에는 `traceId`, `parentSpanId`, `traceFlags`만 보존한다. publish attempt마다 origin/fallback context의 새 CLIENT span을 생성해 `traceparent`를 넣은 뒤 마지막 논리 단계에서 SigV4 `vpc-lattice-svcs` 서명한다. raw traceparent/tracestate, baggage, credential과 사용자·Session·group ID, payload/digest를 publisher log/span/metric tag에 기록하지 않는다.
- RETAKE 연결: `RETAKE_AVAILABLE` Session을 다음 시험의 replacement source로 선택하고 operation에 source Session, expected attemptGroupId와 mockExamId를 snapshot한다. Billing이 동일 group/mockExam의 `REPLACEMENT`를 반환할 때만 진행하며 INITIAL 우회를 거절한다. 기존 linked Session은 자동 backfill하지 않고 `AttemptGroupBackfillService`의 명시적 allowlist dry-run/apply만 제공한다.
- 설정·운영: `ATTEMPT_GROUP_EVENT_WRITER_ENABLED`와 `ATTEMPT_GROUP_EVENT_PUBLISHER_ENABLED` 기본값은 false다. writer에는 Mongo transaction manager·staging/prod capability probe와 outbox unique/claim/TTL index가, publisher에는 HTTPS Billing URL·AWS region·credential provider가 필요하다.
- 변경 파일: `build.gradle`, `application.yml`, `application-test.yml`, ExamSession·ExamCreationOperation·SessionManager·Billing saga/transaction·Grading/Callback 연동 코드, 신규 `domain/exams/attemptgroup/**`, 관련 테스트와 TMI-118 계획·상태·작업 기록 문서다.
- 유지한 외부 계약: 공개 API URL·HTTP Method·Path/Query/Body·Response DTO·BaseResponse, 실제 userId 비노출, 시험 retryCount, Redis key, S3 key·Presigned URL·submit/Polling, Python AI `user_id=examId`와 request/Callback JSON을 변경하지 않았다. Billing event wire는 승인된 schema v1을 사용한다.
- 테스트: `./gradlew clean test`를 실행해 총 439개, failures 0, errors 0, skipped 0, `BUILD SUCCESSFUL`을 확인했다. 실제 AWS, Billing, Mongo Atlas, Redis와 Python AI는 테스트에서 호출하지 않았다. `git diff --check`도 통과했다.
- 남은 위험·배포 gate: Billing `TMI-117` consumer 포함 image와 flag, Mongo replica-set Transaction과 index 권한, Learning Core task role/Lattice auth policy, publisher idle enable 후 writer canary, GRADING→COMPLETED·RETAKE_AVAILABLE, INITIAL/REPLACEMENT, 401/403·409·503·timeout failure-injection E2E가 필요하다. trace exporter/backend와 dashboard/alert는 별도 운영 작업이다.
- 예상 밖 diff: 이번 구현 전부터 존재한 사용자 변경 `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`, `src/main/java/web/tosunsaeng/domain/withdrawal/api/UserWithdrawnEventController.java`, `docs/architecture/`는 수정하지 않았다. `AGENTS.md`, PLAN, CURRENT_STATE와 기존 WORKLOG 변경은 TMI-118 계획/Jira 단계에서 이미 존재했으며 이번 구현은 과거 WORKLOG 항목을 수정하지 않고 이 항목만 끝에 추가했다.
- Git·외부 상태: commit·push·PR·merge·배포와 AWS 리소스 변경은 수행하지 않았다. Jira 상태도 이번 turn에서는 변경하지 않았고 Secret·Token은 기록하지 않았다.

## 2026-09-01 — TMI-118 구현 파일 역할 설명

<!-- codex-turn:01a05b31-679d-79f2-8a52-eddcf6fbd8c2 -->

- 날짜: 2026-09-01
- 브랜치: `develop`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`
- 작업 목표: 사용자가 구현된 파일별 책임과 전체 호출 흐름을 이해할 수 있도록 신규 AttemptGroup 패키지, 기존 Exam/Billing 수정 파일, 설정·테스트 파일을 다시 대조해 설명한다.
- 핵심 흐름: `ExamServiceImpl`의 최초 제출·Question/Summary Callback이 coordinator를 호출하고, `AttemptGroupEvidenceEvaluator`가 GRADING/COMPLETED/RETAKE_AVAILABLE 근거를 판정한다. `AttemptGroupStateCoordinator`가 ExamSession projection과 immutable outbox를 같은 Mongo Transaction으로 저장하며, reconciler가 동기 trigger 누락을 복구한다. publisher는 lease를 얻어 저장된 origin context의 새 CLIENT span을 만든 뒤 traceparent를 주입하고 SigV4 서명된 Billing 요청을 전송한다.
- 파일 분류: domain은 상태 enum·완료 evidence·wire payload·outbox·auth circuit 상태를 정의한다. application은 evidence 판정, 상태 전이, Summary Transaction, 주기 복구, publisher, metric과 명시적 allowlist backfill을 담당한다. infrastructure는 설정·검증·Mongo index/lease/CAS·transaction capability probe·trace context·canonical JSON/digest·SigV4 HTTP 전송을 담당한다.
- 기존 파일 연동: `ExamSession`은 local projection과 terminal CAS metadata를, `ExamCreationOperation`은 RETAKE replacement source snapshot을 보유한다. `ExamSessionManager`와 `BillingExamCreationSaga`는 동일 group/mockExam의 exact REPLACEMENT만 허용하며, `BillingExamCreationTransactionService`는 writer 활성 시 신규 Billing Session을 OPEN projection으로 시작한다. `ExamGradingService`와 `ExamServiceImpl`은 RETAKE terminal fencing과 submit/Callback trigger를 연결한다.
- 변경 파일: 이번 설명 작업에서는 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·테스트·설정 코드는 수정하지 않았다.
- 유지한 계약: 공개 API URL·Method·Parameter·Response DTO·BaseResponse, 실제 userId 비노출, retryCount, Redis/S3, Python AI `user_id=examId`와 Callback JSON, Billing schema v1을 변경하지 않았다.
- 테스트·검증: 설명과 기록 문서만 갱신했으므로 Gradle 테스트를 다시 실행하지 않았다. 직전 TMI-118 구현 검증 `./gradlew clean test` 439개 성공, failures/errors/skipped 0 상태를 근거로 사용하고 이번 문서 diff에는 `git diff --check`를 실행한다.
- 위험·배포 전 확인: 실제 staging Billing consumer, Mongo replica-set·index, Lattice IAM과 COMPLETED/RETAKE/401/403/409/503/timeout E2E는 여전히 남아 있다. writer/publisher feature flag 기본값은 false다.
- 예상 밖 diff: 기존 사용자 변경인 progress checklist, withdrawal Controller와 `docs/architecture/`는 수정하지 않았다. Git commit·push·PR·merge·배포, Jira 상태와 AWS 리소스는 변경하지 않았으며 Secret·Token을 기록하지 않았다.

## 2026-09-01 — TMI-118 publisher lease 설명

<!-- codex-turn:01a05b42-6ba3-71f1-bfeb-94a28ea9fd84 -->

- 날짜: 2026-09-01
- 브랜치: `develop`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`
- 질문: AttemptGroup outbox publisher에서 사용하는 lease의 의미를 쉽게 설명한다.
- 설명: lease는 여러 ECS Task가 같은 event를 동시에 가져가지 않도록 한 Task에 일정 시간만 부여하는 처리권이다. claim 시 outbox에 `IN_FLIGHT`, `leaseOwner`, random `leaseToken`, `leaseUntil`과 attemptCount를 원자적으로 기록한다.
- 정상 처리: HTTP 결과를 받은 worker는 자신이 받은 동일 leaseToken을 아직 보유한 경우에만 DELIVERED, retry, DEAD_LETTER 또는 BLOCKED_AUTH 상태로 바꿀 수 있다. lease를 잃은 오래된 worker는 DB 상태를 변경하지 않는다.
- 장애 복구: Task가 처리 중 종료돼도 영구 lock이 남지 않는다. 기본 30초 lease가 만료되면 다른 Task가 같은 event를 회수해 다시 처리한다. 네트워크 성공 후 local 상태 갱신 전에 종료되면 재전송될 수 있으므로 Billing의 동일 eventId·payload digest 멱등성이 최종 중복 적용을 차단한다.
- 변경 파일: 이번 설명 작업에서는 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·테스트·설정 코드는 변경하지 않았다.
- 유지한 계약: 공개 API·BaseResponse·AI·S3·Redis·Billing event schema와 publisher 정책을 변경하지 않았다.
- 테스트·검증: 문서 설명만 추가했으므로 Gradle 테스트는 다시 실행하지 않았다. 직전 `./gradlew clean test` 439개 성공 상태를 유지하며 `git diff --check`로 문서 형식을 확인한다.
- 예상 밖 diff: 기존 사용자 변경을 수정하지 않았고 Git commit·push·PR·merge·배포, Jira 상태와 AWS 리소스를 변경하지 않았다. Secret·Token은 기록하지 않았다.

## 2026-09-01 — TMI-118 outbox Mongo findAndModify·CAS 설명

<!-- codex-turn:01a05b4e-8c69-7302-9ecf-6135fd5c6e3b -->

- 날짜: 2026-09-01
- 브랜치: `develop`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`
- 질문: `AttemptGroupOutboxStore`가 Mongo `findAndModify`와 CAS로 claim, lease 회수, 성공·retry·격리·인증 차단과 trace 보정을 처리하는 의미를 설명한다.
- findAndModify: `PENDING && nextAttemptAt<=now` 또는 `IN_FLIGHT && leaseUntil<=now`인 event 하나를 정렬해 찾고, 같은 원자 연산에서 `IN_FLIGHT`, 새 owner/token/until과 attemptCount를 기록한다. 여러 ECS Task가 동시에 실행돼도 한 Task만 동일 문서를 claim한다.
- CAS: 전송 결과 갱신 query에 event ID, `status=IN_FLIGHT`, 현재 worker의 `leaseToken`을 함께 요구한다. 조건이 그대로일 때만 DELIVERED, PENDING retry, DEAD_LETTER 또는 BLOCKED_AUTH로 변경하므로 lease를 잃은 stale worker는 새 owner의 상태를 덮어쓰지 못한다.
- 항목별 의미: claim은 처리권 획득, 만료 lease 회수는 종료된 worker의 작업 인계, 성공 처리는 전달 완료+30일 TTL, 재시도 예약은 다음 시각+실패 분류 저장, DEAD_LETTER는 비재시도 오류+90일 보존, 인증 차단은 401/403 event 무TTL 보존과 전역 circuit 차단이다.
- trace 보정: 저장된 traceId/spanId가 유효하지 않으면 현재 leaseToken을 가진 worker만 fallback context를 한 번 저장한다. 이후 retry는 보정된 동일 trace anchor에서 새 publish span을 만든다.
- 변경 파일: 이번 설명에서는 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경하고 애플리케이션·테스트·설정은 수정하지 않았다.
- 유지한 계약: 공개 API, BaseResponse, AI·S3·Redis, Billing schema v1과 outbox 정책을 변경하지 않았다.
- 테스트·검증: 문서 설명만 추가했으므로 Gradle 테스트를 다시 실행하지 않았다. 직전 `./gradlew clean test` 439개 성공 상태를 유지하며 `git diff --check`로 형식을 검증한다.
- 예상 밖 diff: 기존 사용자 변경을 수정하지 않았고 Git commit·push·PR·merge·배포, Jira 상태와 AWS 리소스를 변경하지 않았다. Secret·Token은 기록하지 않았다.

## 2026-09-01 — TMI-118 기존 Session allowlist backfill 설명

<!-- codex-turn:01a05b51-aabb-7940-bf03-d8afbe4abf80 -->

- 날짜: 2026-09-01
- 브랜치: `develop`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`
- 질문: 기존 Billing-linked Session을 자동 처리하지 않고 명시적 allowlist의 dry-run, projection 활성화와 coordinator 실행으로 편입하는 의미를 설명한다.
- 대상 경계: Billing CONFIRMED와 attemptGroupId가 있지만 writer cutover 전에 생성돼 `attemptGroupProjectionStatus`가 null/missing인 Session만 backfill 후보이다. 신규 writer 대상 Session은 생성 시 OPEN projection이 있으므로 일반 reconciler가 자동 처리한다.
- 자동 backfill 금지 이유: legacy Session의 Summary·feedback·Job metadata가 신규 strict evidence와 다를 수 있어 잘못 COMPLETED 또는 RETAKE_AVAILABLE로 확정할 위험이 있고, 많은 event를 한 번에 Billing으로 보내는 burst도 발생할 수 있다.
- dry-run: 운영자가 전달한 Session ID allowlist와 DB 후보의 교집합만 읽어 evaluator를 실행한다. DB 상태는 변경하지 않고 Session별 gradingReady, completed와 failureCode 후보를 반환해 사람이 적용 전 결과를 확인하게 한다.
- apply: 승인된 Session만 Mongo Transaction에서 projection을 OPEN, version 0으로 초기화하고 commit 뒤 coordinator를 실행한다. coordinator는 최신 evidence를 다시 판정해 필요한 GRADING 또는 terminal event와 outbox를 정상 규칙으로 생성한다. 같은 Session 재실행은 projection 존재 확인으로 no-op 수렴한다.
- 현재 실행 경계: `AttemptGroupBackfillService`는 내부 application service이며 공개 API, admin endpoint와 자동 scheduled runner를 추가하지 않았다. 실제 staging 실행 command/도구, allowlist 산출·승인과 batch 크기는 배포 운영 절차에서 별도로 정해야 한다.
- 변경 파일: 이번 설명에서는 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했고 애플리케이션·테스트·설정은 수정하지 않았다.
- 유지한 계약: 공개 API, BaseResponse, AI·S3·Redis, Billing schema v1과 backfill 정책을 변경하지 않았다.
- 테스트·검증: 설명 문서만 추가했으므로 Gradle 테스트를 다시 실행하지 않았다. 직전 `./gradlew clean test` 439개 성공 상태를 유지하며 `git diff --check`로 형식을 검증한다.
- 예상 밖 diff: 기존 사용자 변경을 수정하지 않았고 Git commit·push·PR·merge·배포, Jira 상태와 AWS 리소스를 변경하지 않았다. Secret·Token은 기록하지 않았다.

## 2026-09-01 — TMI-118 backfill 후보 batch 처리 설명

<!-- codex-turn:01a05b58-0084-7ce3-8ecc-ac2f0dca22dc -->

- 날짜: 2026-09-01
- 브랜치: `develop`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`
- 질문: 기존 Billing-linked Session을 사람이 하나씩 확인해 allowlist에 넣어야 하는지 설명한다.
- 결론: Mongo 문서를 하나씩 수동으로 여는 방식은 필요하지 않다. repository 조건으로 projection 없는 기존 Billing-linked 후보를 전체 inventory하고, 후보 ID를 batch dry-run한 결과를 분류해 승인된 ID 그룹을 한 번에 apply하는 것이 권장 운영 방식이다.
- 권장 분류: strict completed evidence가 완전한 후보는 batch 승인할 수 있고, grading 진행 후보는 소량 canary 후 batch 처리한다. `RESULT_INTEGRITY_VIOLATION`, Summary/feedback 불완전과 모호한 legacy 후보만 사람이 개별 조사한다.
- 현재 구현 경계: `findAttemptGroupBackfillCandidates`가 후보 전체를 찾고 `AttemptGroupBackfillService.dryRun(Set<String>)`·`apply(Set<String>)`가 ID 집합을 처리한다. 다만 inventory report export, batch 크기 조절, 승인 파일 입력과 실행용 CLI/admin runner는 아직 구현하지 않았다.
- 운영 선택: 후보가 0개면 backfill을 생략한다. 후보가 적으면 개별 승인, 많으면 자동 분류+batch allowlist+소량 canary 순으로 진행하고 한 번에 전부 자동 terminal 처리하지 않는다.
- 변경 파일: 이번 설명에서는 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했고 애플리케이션·테스트·설정은 수정하지 않았다.
- 유지한 계약: 공개 API, BaseResponse, AI·S3·Redis, Billing schema v1과 backfill 정책을 변경하지 않았다.
- 테스트·검증: 설명 문서만 추가했으므로 Gradle 테스트를 다시 실행하지 않았다. 직전 `./gradlew clean test` 439개 성공 상태를 유지하며 `git diff --check`로 형식을 검증한다.
- 예상 밖 diff: 기존 사용자 변경을 수정하지 않았고 Git commit·push·PR·merge·배포, Jira 상태와 AWS 리소스를 변경하지 않았다. Secret·Token은 기록하지 않았다.

## 2026-09-01 — TMI-118 backfill inventory 조회 시점 설명

<!-- codex-turn:01a05b5c-0229-7b02-b717-ded525507acf -->

- 날짜: 2026-09-01
- 브랜치: `develop`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`
- 질문: 기존 Billing-linked Session 후보 조회를 지금 해야 하는지 설명한다.
- 결론: 지금은 backfill apply가 아니라 환경별 read-only count inventory를 수행할 시점이다. 특히 Billing creation saga가 한 번이라도 활성화된 staging/prod는 AttemptGroup writer를 켜기 전에 후보 수를 확인해야 한다.
- 조회 조건: `exam_sessions`에서 `entitlementState=CONFIRMED`, nonblank string attemptGroupId, `attemptGroupProjectionStatus` null/missing인 Session이다. userId와 credential 등 민감값은 inventory 출력에 포함할 필요가 없다.
- 결과별 행동: count 0이면 backfill을 생략하고 신규 writer cutover로 진행한다. count가 있으면 writer 활성화 전에 후보를 batch dry-run해 strict complete, grading, 불완전·integrity 후보로 나누고 소량 canary 뒤 승인 batch를 적용한다.
- 환경 경계: `BILLING_CREATION_SAGA_ENABLED`가 해당 환경에서 한 번도 true가 아니었다면 후보가 생성될 수 없지만, 배포 체크 증적으로 count 0을 남기는 것을 권장한다. production과 staging은 각각 별도로 조회한다.
- 변경 파일: 이번 설명에서는 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했고 애플리케이션·테스트·설정은 수정하지 않았다.
- 유지한 계약: 공개 API, BaseResponse, AI·S3·Redis, Billing schema v1과 backfill 정책을 변경하지 않았다.
- 테스트·검증: 문서 설명만 추가했으므로 Gradle 테스트를 다시 실행하지 않았다. 직전 `./gradlew clean test` 439개 성공 상태를 유지하며 `git diff --check`로 형식을 검증한다.
- 예상 밖 diff: DB 조회나 backfill apply, 기존 사용자 변경, Git commit·push·PR·merge·배포, Jira 상태와 AWS 리소스 변경을 수행하지 않았다. Secret·Token은 기록하지 않았다.

### 실제 read-only inventory 실행 결과

- 사용자 승인 후 `.env.docker.local`의 local profile이 참조하는 Atlas 연결을 사용했다. URI, credential, userId와 Session ID는 출력·기록하지 않았다.
- 1차 조회 `CONFIRMED + nonblank attemptGroupId + attemptGroupProjectionStatus null/missing` 결과는 `candidateCount=0`이었다.
- 2차 확인 `CONFIRMED + nonblank attemptGroupId` 전체 결과도 `confirmedBillingLinkedCount=0`이어서, 후보 0의 원인은 기존 linked Session이 이미 projection 처리된 것이 아니라 현재 연결 대상에 CONFIRMED Billing-linked Session 자체가 없기 때문이다.
- 결론: 현재 local profile Atlas DB에는 TMI-118 backfill apply가 필요하지 않다. staging/production이 별도 연결이면 각 환경의 count는 별도로 확인해야 한다.
- 변경·검증: 두 Mongo 작업은 `countDocuments`와 aggregate `$match/$group`만 사용한 읽기 전용 집계다. DB 문서, 애플리케이션·테스트·설정·AWS·Jira·Git 상태는 변경하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-01 — TMI-118 backfill inventory 종료 훅 동기화

<!-- codex-turn:01a05b5c-b144-7ef3-9ed0-296dbbb5274a -->

- 날짜: 2026-09-01
- 브랜치: `develop`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`
- 수행 작업: 사용자 승인 후 local profile이 참조하는 Atlas DB에서 TMI-118 기존 Session backfill inventory를 읽기 전용으로 실행했다.
- 결과: `CONFIRMED + nonblank attemptGroupId + projection null/missing` 후보는 0개였고, CONFIRMED Billing-linked Session 전체도 0개였다. 따라서 현재 연결 대상에는 backfill apply가 필요하지 않다.
- 환경 경계: 결과는 local profile 연결에만 해당한다. staging/production이 별도 Mongo 연결이면 AttemptGroup writer 활성화 전에 각각 같은 count를 확인해야 한다.
- 보안·변경 범위: URI, credential, userId, Session ID, Secret과 Token은 출력하거나 기록하지 않았다. `countDocuments`와 aggregate 읽기만 수행했고 DB 문서, 애플리케이션·테스트·설정·AWS·Jira·Git 상태는 변경하지 않았다.
- 테스트·검증: 애플리케이션 변경이 없어 Gradle 테스트를 다시 실행하지 않았다. 직전 전체 439개 성공 상태를 유지했고 문서 변경은 `git diff --check`로 검증한다.

## 2026-09-01 — TMI-118 추가 작업 필요 여부 정리

- 날짜: 2026-09-01
- 브랜치: `develop`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`
- 결론: 현재 local profile Atlas의 backfill 후보와 CONFIRMED Billing-linked Session이 모두 0개이므로 이 DB를 위한 allowlist 작성, backfill runner와 apply 작업은 필요 없다.
- 남은 필수 작업: staging/production이 별도 Mongo 연결이면 각 환경 inventory를 다시 확인한다. 이후 Billing TMI-117 consumer 포함 배포, Mongo replica-set Transaction·index, Lattice IAM, publisher idle 활성화, writer canary와 GRADING/COMPLETED/RETAKE_AVAILABLE·401/403/409/503/timeout E2E를 수행해야 한다.
- 코드 판단: 추가 제품 기능이나 공개 API 개발은 현재 필수가 아니다. 실제 후보가 있는 환경이 발견될 때만 일회성 운영 runner/CLI를 추가하면 된다.
- 변경 파일: 이번 정리에서는 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했고 애플리케이션·테스트·설정은 수정하지 않았다.
- 유지한 계약: 공개 API, BaseResponse, AI·S3·Redis와 Billing schema v1을 변경하지 않았다.
- 테스트·검증: 설명 문서만 변경해 Gradle 테스트는 다시 실행하지 않았고, 직전 전체 439개 성공 상태를 유지하며 `git diff --check`로 문서 형식을 확인한다.
- 보안·범위: DB 조회·apply, Git commit·push·PR·merge·배포, Jira 상태와 AWS 리소스를 변경하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-01 — TMI-118 추가 작업 여부 최종 확인 종료 훅

<!-- codex-turn:01a05b61-2b64-7f63-aee2-2d64a6819eca -->

- 날짜: 2026-09-01
- 브랜치: `develop`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`
- 결론: local profile Atlas의 backfill 후보와 CONFIRMED Billing-linked Session이 모두 0개이므로 현재 연결 DB에는 추가 제품 코드, 세션별 allowlist와 backfill apply가 필요 없다.
- 남은 범위: staging/production이 별도 DB라면 환경별 inventory를 다시 확인해야 한다. Billing TMI-117 consumer 배포, Mongo replica-set Transaction·index, Lattice IAM, publisher idle 선활성, writer canary와 GRADING/COMPLETED/RETAKE_AVAILABLE·401/403/409/503/timeout E2E는 배포·운영 단계에서 수행한다.
- 변경 파일: 종료 훅 동기화를 위해 `docs/codex/CURRENT_STATE.md`와 `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·테스트·설정 코드는 변경하지 않았다.
- 유지한 계약: 공개 API, BaseResponse, AI·S3·Redis와 Billing schema v1을 변경하지 않았다.
- 테스트·검증: 애플리케이션 변경이 없어 Gradle 테스트를 다시 실행하지 않았으며 직전 `./gradlew clean test` 439개 성공 상태를 유지한다. 문서 변경은 `git diff --check`와 marker 1회 검증으로 확인한다.
- 보안·범위: DB 조회·변경, Git commit·push·PR·merge·배포, Jira 상태와 AWS 리소스를 변경하지 않았고 Secret·Token을 기록하지 않았다. 예상 밖의 신규 변경 범위는 없다.

## 2026-09-01 — TMI-118 backfill 역할 설명

<!-- codex-turn:01a05b64-d787-77b0-897f-5a4c11da17c9 -->

- 날짜: 2026-09-01
- 브랜치: `develop`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`
- 질문: AttemptGroup backfill이 구체적으로 무엇을 하는지 설명한다.
- 설명: backfill은 새 writer가 배포되기 전에 생성된 기존 CONFIRMED Billing-linked ExamSession 중 `attemptGroupProjectionStatus`가 없는 데이터에 새 projection 초기 상태를 일회성으로 보충하는 데이터 이관이다.
- 처리 흐름: 후보를 조회하고 dry-run으로 현재 시험 evidence를 판정한 뒤, 운영자가 명시적으로 승인한 Session만 projection OPEN/version 0으로 초기화하고 coordinator를 실행해 적절한 GRADING·COMPLETED·RETAKE_AVAILABLE 상태와 Billing 전송용 outbox event를 생성한다.
- 안전 경계: 모든 기존 Session을 자동 변경하지 않고 allowlist 대상만 처리한다. 신규 Session은 정상 writer가 자동 처리하며, 현재 local profile Atlas의 후보는 0개라 실제 backfill apply가 필요 없다. staging/production이 별도 DB이면 환경별 후보 수를 별도로 확인한다.
- 변경 파일: 설명 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·테스트·설정과 DB는 변경하지 않았다.
- 유지한 계약: 공개 API, BaseResponse, AI·S3·Redis와 Billing schema v1을 변경하지 않았다.
- 테스트·검증: 설명 문서만 변경해 Gradle 테스트를 다시 실행하지 않았으며 직전 전체 439개 성공 상태를 유지한다. 문서 변경은 `git diff --check`와 marker 1회 확인으로 검증한다.
- 보안·범위: Secret·Token과 사용자·Session 식별값을 기록하지 않았고 Git commit·push·PR·merge·배포, Jira 상태와 AWS 리소스를 변경하지 않았다.

## 2026-09-01 — TMI-118 로컬 develop 반영 재확인

- 날짜: 2026-09-01
- 현재 브랜치: `develop`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`; 현재 Jira 상태는 `완료`다.
- 확인 결과: 현재 `HEAD`, 로컬 `develop`, `origin/develop`이 모두 PR #25 merge commit `c00d872179414864f0820e88721c9b0babe2cc35`를 가리킨다.
- 포함 관계: 구현 commit `63d0f7d68f804875859b1c8897130458b1d8083a`에 대해 develop ancestor 검사가 성공했다. 로컬 feature branch도 같은 구현 commit을 유지한다.
- 파일 확인: 현재 worktree의 `src/main/java/web/tosunsaeng/domain/exams/attemptgroup`에 publisher, coordinator, evidence evaluator, backfill service와 관련 domain·infrastructure 소스가 존재한다.
- 결론: TMI-118은 원격 feature branch에만 있는 상태가 아니라 현재 로컬 develop과 origin/develop 모두에 병합·반영돼 있다.
- 변경 파일: 확인 결과 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·테스트·설정 코드는 변경하지 않았다.
- 유지한 계약: 공개 API, BaseResponse, AI·S3·Redis와 Billing schema v1을 변경하지 않았다.
- 테스트·검증: 코드 변경이 없어 Gradle 테스트를 재실행하지 않았다. Git ref·ancestor·현재 source path를 읽기 전용으로 확인하고 문서 변경은 `git diff --check`로 검증한다.
- 보안·범위: Secret·Token을 기록하지 않았고 DB·AWS·Jira·Git commit·push·PR·merge·배포를 변경하지 않았다. 예상 밖의 애플리케이션 변경은 없다.

## 2026-09-01 — TMI-118 로컬 반영 확인 종료 훅 동기화

<!-- codex-turn:01a05b69-518e-7b82-a0ae-36eca6577358 -->

- 날짜: 2026-09-01
- 현재 브랜치: `develop`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`; 현재 상태는 `완료`다.
- 확인 결과: `HEAD`, 로컬 `develop`, `origin/develop`이 PR #25 merge commit `c00d872179414864f0820e88721c9b0babe2cc35`로 일치하고 구현 commit `63d0f7d68f804875859b1c8897130458b1d8083a`이 develop에 포함돼 있다.
- 파일 결과: 현재 worktree에 AttemptGroup publisher, coordinator, evaluator, backfill과 domain·infrastructure 소스가 실제로 존재한다.
- 변경 범위: 종료 훅 요구에 따라 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 보강했다. 애플리케이션·테스트·설정과 외부 계약은 변경하지 않았다.
- 테스트·검증: 코드 변경이 없어 Gradle 테스트를 재실행하지 않았고, Git ref·ancestor·소스 경로 확인 결과를 유지한다. 문서는 `git diff --check`와 marker 1회 검사로 검증한다.
- 보안·운영: Secret·Token을 기록하지 않았으며 DB·AWS·Jira 상태·Git commit·push·PR·merge·배포를 추가로 변경하지 않았다.

## 2026-09-01 — TMI-118 이후 다음 작업 우선순위 재검토

<!-- codex-turn:01a05b6e-04a9-7661-8e45-de9eca7d6862 -->

- 날짜: 2026-09-01
- 현재 브랜치: `develop`
- 관련 Jira: 완료된 `TMI-118`, 선행 Identity `TMI-98`, 구현·병합됐으나 Jira가 `해야 할 일`인 `TMI-116`을 확인했다. Billing owner rebind 전용 Jira는 아직 확인되지 않았다.
- 결론: 다음 제품 개발 1순위는 Billing의 탈퇴·재가입 `UserMerged` retained subject owner rebind다. Billing의 TrialClaim, entitlement/grant, Reservation과 AttemptGroup 소유권을 source Guest에서 최종 Member로 멱등 이전하는 작업이다.
- 이유: owner rebind가 없으면 계정 merge 뒤 Member가 기존 무료 권리와 사용 이력을 잃거나, source·target이 분리돼 무료 권리 중복 판단과 Reservation/AttemptGroup 복구가 어긋날 수 있다.
- 권장 착수 순서: Billing 저장소의 현재 owner 필드·unique index·Identity UserMerged schema를 재조사하고 계약·충돌 정책·transaction 경계를 계획서로 고정한 뒤 신규 Jira를 생성해 구현한다.
- 후속 순서: Identity→Billing eligibility publisher의 SigV4/Lattice 정렬과 TMI-116 migration·failure-injection E2E, Learning Core UserMerged consumer, Challenge backend·AI 양방향 구현, 전체 모바일/staging E2E와 canary다.
- Jira 조회: TMI 프로젝트의 summary에 `owner rebind` 또는 `UserMerged`가 포함된 이슈를 JQL로 조회했으며 Identity `TMI-98`만 확인됐다. 최신 이슈 목록에서도 TMI-118이 가장 최신이므로 owner rebind 신규 Jira가 필요하다.
- 변경 파일: 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·테스트·설정과 외부 계약은 변경하지 않았다.
- 테스트·검증: 코드 변경이 없어 Gradle 테스트를 재실행하지 않았다. 문서는 `git diff --check`와 marker 1회 검사로 검증한다.
- 보안·범위: Secret·Token을 기록하지 않았고 Jira 생성·상태 전환, DB·AWS·Git commit·push·PR·merge·배포를 수행하지 않았다.

## 2026-09-01 — TMI-116 Jira 완료 전환

- 날짜: 2026-09-01
- 현재 브랜치: `develop`
- Jira: `TMI-116` `[Learning Core] Billing Reservation 시험 생성 saga 구현`
- 수행 작업: Jira의 현재 상태가 `해야 할 일`임을 확인하고 사용자 요청에 따라 transition ID 41 `완료`를 적용했다.
- 결과: TMI-116이 `완료`로 전환됐으며 Jira 응답의 status category가 `done`임을 확인했다.
- 완료 근거: 구현은 PR #24로 develop에 병합됐고 confirm 복구, unknown commit no-cancel과 Billing 성공 응답 strict 검증 보완 후 `./gradlew clean test` 전체 432개 성공 기록이 있다.
- 변경 파일: Jira 전환 결과를 반영하기 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·테스트·설정은 수정하지 않았다.
- 유지한 계약: 공개 API, BaseResponse, AI `user_id=examId`, retryCount, S3·Redis와 Billing wire 계약을 변경하지 않았다.
- 테스트·검증: 코드 변경이 없어 Gradle 테스트를 재실행하지 않았으며 Jira 전환 응답과 문서 `git diff --check`로 검증한다.
- 남은 운영 경계: feature flag 활성화 전 실제 Mongo migration·replica-set failure injection, Lattice/IAM/SG와 reserve/commit/confirm staging E2E가 필요하다.
- 보안·범위: Secret·Token을 기록하지 않았고 DB·AWS·Git commit·push·PR·merge·배포는 변경하지 않았다.

## 2026-09-01 — TMI-116 Jira 완료 종료 훅 동기화

<!-- codex-turn:01a05b6f-53a8-72d1-8c6a-c34f36bf52ae -->

- 날짜: 2026-09-01
- 현재 브랜치: `develop`
- Jira: `TMI-116` `[Learning Core] Billing Reservation 시험 생성 saga 구현`; `완료` 상태다.
- 결과: 사용자 요청으로 transition ID 41을 적용했고 Jira 응답의 status category `done`을 확인했다.
- 완료 근거: PR #24 develop 병합과 P1/P2 보완 후 `./gradlew clean test` 432개 성공 기록을 유지한다.
- 변경 범위: 종료 훅 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 보강했다. 애플리케이션·테스트·설정과 외부 계약은 변경하지 않았다.
- 테스트·검증: 코드 변경이 없어 Gradle 테스트를 재실행하지 않았으며 문서는 `git diff --check`와 marker 1회 검사로 확인한다.
- 보안·운영: Secret·Token을 기록하지 않았고 DB·AWS·Jira 상태·Git commit·push·PR·merge·배포를 추가 변경하지 않았다.

## 2026-09-01 — TMI-118 Summary Transaction 및 통합 검증 gap 리뷰

<!-- codex-turn:01a05b74-c954-7ae0-aa67-d98720f6d664 -->

- 날짜: 2026-09-01
- 현재 브랜치: `develop`
- Jira: 완료 상태인 `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`의 병합 코드를 사후 리뷰했다.
- P1 결론: `AttemptGroupSummaryCompletionService.persistAndComplete()`는 Mongo Transaction callback 안에서 `summaryRepository.insert()`의 `DuplicateKeyException`을 잡고 같은 Transaction으로 `completeSummary()`와 `coordinator.reconcile()`을 계속한다. Mongo duplicate key는 Transaction을 abort하므로 이 catch는 멱등 replay를 만들지 못하고 이후 작업·commit 실패로 이어질 수 있다.
- 인접 위험: `AttemptGroupStateCoordinator.reconcile()`의 execute 바깥 race catch는 독립 Transaction에서는 rollback 뒤 예외를 받으므로 안전하지만, Summary의 기존 Transaction에 참여하면 참여 실패가 outer를 rollback-only로 만든 뒤 예외만 삼킬 수 있다. 따라서 예상 duplicate를 Transaction 안에서 삼키지 말고 전체 unit을 Transaction 밖에서 재실행·수렴시켜야 한다.
- 권장 수정: Transaction 안에서 결정적 Summary를 먼저 조회·검증하고 없을 때 insert한다. insert race의 duplicate는 callback 밖으로 전파해 rollback을 끝낸 뒤 새 Transaction으로 전체 Summary+Job+Session+outbox 단위를 재시도한다. unknown commit은 durable Summary·Job·Session·outbox 상태를 Transaction 밖에서 재조회해 완료면 성공, 미완료면 bounded full-unit retry로 수렴시킨다.
- 테스트 gap: AttemptGroup 전용 테스트는 Mockito 기반이며 `TransactionOperations`, outbox store, HTTP client와 tracer를 mock한다. `AttemptGroupSummaryCompletionService` 전용 테스트와 Testcontainers/embedded replica-set 의존성도 없다.
- 필요한 검증: replica-set commit/rollback, duplicate와 unknown commit result 수렴, COMPLETED/RETAKE terminal race, multi-instance lease expiry reclaim와 stale token fencing, traceparent 주입 후 SigV4 최종 서명, Learning Core/Billing 동일 traceId·서로 다른 spanId, baggage 미전파와 금지 개인정보·payload·credential 부재를 실제 경계에서 검증해야 한다.
- 우선순위: Billing UserMerged owner rebind의 wire 계약·활성 Reservation 정책 ADR은 다음 제품 작업으로 유효하지만, 현재 P1 hotfix와 replica-set 핵심 integration test를 먼저 완료한 뒤 진행하는 것이 안전하다. Billing owner rebind 전용 Jira는 아직 없다.
- 변경 파일: 리뷰 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·테스트·설정 코드는 수정하지 않았다.
- 유지한 계약: 공개 API, BaseResponse, AI `user_id=examId`, retryCount, S3·Redis와 Billing wire 계약을 변경하지 않았다.
- 테스트·검증: 코드 변경 없는 정적 리뷰이므로 Gradle 테스트를 실행하지 않았다. 관련 구현·테스트·Gradle 의존성을 대조하고 문서는 `git diff --check`와 marker 1회 검사로 검증한다.
- 보안·범위: Secret·Token을 기록하지 않았고 Jira 상태, DB·AWS·Git commit·push·PR·merge·배포를 변경하지 않았다.

## 2026-09-01 — TMI-118 Summary Transaction P1 hotfix 구현

- 날짜: 2026-09-01
- 브랜치: 사용자 요청에 따라 `develop@c00d872`에서 새 로컬 브랜치 `codex/fix-tmi-118-summary-transaction`을 생성하고 전환했다.
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`; Jira 상태는 변경하지 않았다.
- 수정 파일: `AttemptGroupSummaryCompletionService.java`, `AttemptGroupStateCoordinator.java`와 신규 `AttemptGroupSummaryCompletionServiceTest.java`, `AttemptGroupStateCoordinatorTransactionTest.java`를 변경·추가했다.
- Summary 저장: Transaction 안에서 결정적 Summary ID를 먼저 조회하고 existing의 id·examId·userId·mockExamId identity를 검증한다. 없을 때만 insert하고 같은 ID의 다른 owner/시험 관계는 fail-closed한다.
- 전체 재시도: duplicate key, optimistic conflict와 원인 chain의 Mongo `TransientTransactionError`·`UnknownTransactionCommitResult` label을 abort된 Transaction 밖에서 분류하고, rollback 이후 최대 3회의 새 Transaction으로 Summary 저장·Job 완료·Session terminal·outbox 전체 단위를 다시 실행한다.
- rollback 보완: `completeSummary()`가 false면 TransactionStatus를 rollback-only로 설정해 Summary만 저장되고 Job이 미완료인 부분 commit을 막는다.
- coordinator 경계: standalone `reconcile()`은 자기 Transaction 종료 후 race를 기존처럼 수렴시킨다. Summary outer Transaction에서는 package-local `reconcileWithinTransaction()`을 호출해 duplicate/optimistic 예외를 삼키지 않고 바깥 전체-unit retry 경계로 전파한다.
- 신규 테스트: duplicate insert 첫 Transaction 실패 후 두 번째 전체 재시도, Summary Job false rollback과 coordinator 미호출, unknown commit wrapper 재시도, 기존 Summary identity conflict fail-closed, participating coordinator duplicate 전파를 검증하는 5개 테스트를 추가했다.
- 실행 테스트: 집중 테스트 5개가 성공했고 `./gradlew clean test` 전체 444개가 failures/errors 없이 성공했다. `git diff --check`도 통과했다.
- 유지한 계약: 공개 API URL·Method·Request/Response·BaseResponse, AI `user_id=examId`, Callback JSON, retryCount, Redis Key, S3 Object Key·Presigned URL과 Billing schema v1을 변경하지 않았다.
- 남은 위험: 이번 테스트는 transaction control-flow 회귀를 mock으로 검증한다. 실제 replica-set commit/rollback·unknown commit failure injection, terminal race, multi-instance lease reclaim, SigV4 최종 서명, Learning Core/Billing traceId·spanId와 baggage/privacy 통합 검증은 배포 전 별도로 수행해야 한다.
- 배포 전 확인: writer/publisher는 기본 off를 유지하며 Billing consumer, Mongo index·Transaction, Lattice IAM과 cross-service E2E 없이 production flag를 활성화하지 않는다.
- 예상 밖 diff: 기존 작업 기록 문서 변경은 보존했다. 이번 hotfix에서 예상 밖의 애플리케이션·설정·외부 계약 변경은 없으며 build output은 추적 대상이 아니다.
- 다음 작업 전 확인: hotfix PR·develop 병합 후 replica-set 핵심 integration suite를 추가하고, 그 다음 Billing UserMerged owner rebind ADR·계획서·신규 Jira로 진행한다.
- 보안·Git 범위: Secret·Token을 기록하지 않았고 DB·AWS·Jira, Git commit·push·PR·merge·배포를 변경하지 않았다.

## 2026-09-01 — TMI-118 Transaction 결함 쉬운 설명

<!-- codex-turn:01a05b79-e347-7990-889f-136feb87e3c9 -->

- 날짜: 2026-09-01
- 현재 브랜치: `develop`
- Jira: 완료 상태인 `TMI-118`의 사후 리뷰 P1 두 건을 사용자에게 쉬운 흐름으로 설명했다.
- 공통 원리: Mongo Transaction은 한 묶음의 작업 봉투다. duplicate key가 발생하면 Mongo가 봉투 전체를 abort하며, Java에서 예외를 catch해도 이미 폐기된 Transaction은 다시 유효해지지 않는다.
- 1번 결함: Summary insert duplicate를 catch하고 같은 Transaction에서 Summary Job 완료, Session 전이와 outbox 생성을 계속한다. 코드는 계속 실행되는 것처럼 보여도 이후 명령 또는 최종 commit이 실패해 전체 변경이 저장되지 않는다.
- 2번 결함: coordinator의 TransactionTemplate은 Summary outer Transaction 안에서 호출되면 새 Transaction을 만들지 않고 같은 Transaction에 참여한다. outbox duplicate나 optimistic conflict가 outer를 rollback-only로 만든 뒤 coordinator가 예외를 삼키면, 바깥 로직은 성공처럼 계속되다가 마지막 commit에서 전체 rollback된다.
- 차이: 1번은 동일 callback이 abort된 Transaction을 직접 계속 쓰는 문제이고, 2번은 내부 서비스가 자기 Transaction만 실패한 것으로 오해하지만 실제로 바깥 Transaction까지 함께 죽는 문제다.
- 해결 방향: duplicate/race 예외를 Transaction 밖까지 전파해 rollback을 끝낸 다음, durable 상태를 재조회하고 Summary+Job+Session+outbox 전체를 새로운 Transaction으로 재시도한다.
- 변경 파일: 설명 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·테스트·설정은 수정하지 않았다.
- 유지한 계약: 공개 API, BaseResponse, AI·S3·Redis와 Billing wire 계약을 변경하지 않았다.
- 테스트·검증: 설명·정적 분석만 수행해 Gradle 테스트를 실행하지 않았다. 문서는 `git diff --check`와 marker 1회 검사로 확인한다.
- 보안·범위: Secret·Token을 기록하지 않았고 Jira 상태, DB·AWS·Git commit·push·PR·merge·배포를 변경하지 않았다.

## 2026-09-01 — TMI-118 Summary Transaction hotfix 최종 동기화

- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: `TMI-118`; Jira 상태는 변경하지 않았다.
- 최종 결과: abort된 Mongo Transaction 내부에서 duplicate를 삼키지 않고 rollback 뒤 Summary·Job·Session·outbox 전체를 새 Transaction으로 최대 3회 재시도하도록 구현했다. nested coordinator는 outer Transaction 전용 경계에서 race 예외를 전파한다.
- 회귀 방어: Summary Job 완료 실패 rollback, 기존 Summary identity 검증, duplicate와 unknown commit 전체 재시도, nested terminal duplicate 전파 테스트 5개를 추가했다.
- 검증: `./gradlew clean test` 전체 444개 성공, `git diff --check` 통과다.
- 변경 범위: 애플리케이션 2개 파일, 신규 테스트 2개 파일과 필수 기록 문서만 변경했다. 공개 API·AI·S3·Redis·Billing 계약과 설정은 유지했다.
- 잔여 gate: 실제 replica-set·unknown commit failure injection, multi-instance lease, SigV4와 cross-service trace/privacy 통합 검증은 별도 후속 작업이다.
- 보안·Git: Secret·Token을 기록하지 않았고 DB·AWS·Jira·commit·push·PR·merge·배포는 변경하지 않았다.

## 2026-09-01 — TMI-118 Summary Transaction hotfix 종료 훅 동기화

<!-- codex-turn:01a05b7c-681e-7943-8dbd-920bf394b0b8 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`; Jira 상태는 변경하지 않았다.
- 구현 결과: abort된 Mongo Transaction에서 duplicate를 삼키던 흐름을 제거하고 rollback 뒤 Summary·Job·Session·outbox 전체를 새 Transaction으로 최대 3회 재시도한다. Summary Job false는 rollback-only 처리하며 nested coordinator race는 outer 경계로 전파한다.
- 테스트 결과: 회귀 테스트 5개를 추가했고 `./gradlew clean test` 전체 444개와 `git diff --check`가 성공했다.
- 변경 범위: 애플리케이션 2개, 신규 테스트 2개와 작업 기록 문서만 변경했다. 외부 API·BaseResponse·AI·S3·Redis·Billing 계약과 설정은 유지했다.
- 잔여 위험: 실제 replica-set failure injection, multi-instance lease, SigV4와 cross-service trace/privacy 통합 검증은 후속 gate다.
- 보안·Git: Secret·Token을 기록하지 않았고 DB·AWS·Jira 상태, commit·push·PR·merge·배포를 변경하지 않았다.

## 2026-09-01 — TMI-118 hotfix commit·push 명령 안내

<!-- codex-turn:01a05b84-b832-7c40-b305-154a0726fc83 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`
- 확인 상태: 변경 파일은 Summary completion/coordinator 애플리케이션 2개, 신규 application package 테스트 2개와 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`다. `git diff --check`가 통과했다.
- 안내 내용: `git add .`를 사용하지 않고 위 파일을 정확히 stage한 뒤 commit message `fix(TMI-118): retry aborted summary transactions`를 사용하고 현재 branch를 `origin`에 `-u` 옵션으로 push하는 명령을 제공한다.
- 테스트 근거: 직전 `./gradlew clean test` 전체 444개 성공 상태를 유지한다.
- 변경 범위: 이번 작업은 명령 안내와 기록 문서 갱신뿐이며 애플리케이션·테스트·설정 코드는 추가 수정하지 않았다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·PR·merge·배포를 직접 실행하지 않았다.

## 2026-09-01 — 시스템 구성도 표현 도구 선택

<!-- codex-turn:01a05bcd-56e8-76c0-9114-249362fbacc0 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 시스템 구성도 도구 선택은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 5줄 결론: 토선생처럼 서비스·외부 시스템·데이터 저장소·신뢰 경계를 한눈에 설명하고 직접 편집해야 하는 시스템 구성도는 draw.io를 원본으로 사용한다. Mermaid는 sequence·state·간단한 코드 인접 흐름에 사용하며, 생성 이미지는 발표 표지나 개념적 삽화에만 사용하고 정확한 시스템 원본으로 사용하지 않는다.
- 결정 근거: draw.io는 자유 배치, grouping, 색상 범례, 서비스 경계와 수동 편집에 유리하다. Mermaid는 Git diff와 Markdown 동기화는 좋지만 큰 시스템 지도에서 배치 제어가 약하다. 생성 이미지는 편집·버전 관리·정확한 label 유지가 어렵다.
- 권장 운영: 같은 도식을 세 형식으로 중복 유지하지 않고, 통합 시스템 구성도는 draw.io 하나를 source of truth로 두며 별개의 sequence/state 도식만 Mermaid로 작성한다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. draw.io, Mermaid 또는 이미지 파일은 새로 만들지 않았다.
- 유지한 계약: 애플리케이션 코드, 공개 API, `BaseResponse`, AI·S3·Redis·Billing 계약을 변경하지 않았다.
- 테스트·검증: 설명과 기록 문서만 변경했으므로 Gradle 테스트는 실행하지 않고 `git diff --check`로 검증한다.
- 위험·배포 전 확인: draw.io와 Mermaid로 동일한 관계를 중복 작성하면 drift가 생길 수 있으므로 도식별 원본을 하나만 정해야 한다. 배포 사항은 없고 이번 작업과 무관한 기존 dirty worktree는 수정하지 않았다.
- Git commit·push는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 토선생 앱 시스템 구성도 draw.io 작성

<!-- codex-turn:01a05bd6-9b87-7a42-bf18-10b2e61d38b8 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 시스템 구성도 작성은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 5줄 결론: 웹 POC를 제외한 토선생 앱 시스템을 한 페이지 draw.io로 작성했다. 앱, Identity, Learning Core, Billing, Python AI, 서비스별 MongoDB, Redis와 S3를 배치했다. 공개 HTTPS, JWKS, SigV4·VPC Lattice, Presigned PUT, AI 요청·Callback을 연결했다. 구현·조건부·외부 연동과 데이터 소유권을 색상·선 스타일로 구분했다. 기존 멘토링용 8페이지 draw.io는 수정하지 않았다.
- 변경 파일: `docs/architecture/tosunsaeng-app-system-configuration.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 구현 내용: 앱이 Identity와 Learning Core만 직접 호출하고 Billing은 내부 전용임을 표현했다. `examId→userId`, `AI user_id=examId`, 실제 userId 비전송, 서비스별 DB 소유권과 Billing rollout gate를 도식에 명시했다.
- 유지한 계약: 애플리케이션 코드, 공개 API, `BaseResponse`, 사용자 식별, AI request/Callback, S3·Redis와 Billing wire 계약을 변경하지 않았다.
- 테스트·검증: `xmllint --noout` 성공, diagram 1개, vertex 29개, edge 14개, 누락 source/target 0개를 확인했다. 애플리케이션 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 최종 `git diff --check`를 수행한다.
- 위험·미확인: draw.io CLI가 없어 diagrams.net 실제 렌더링 화면 export는 수행하지 못했다. 사용자가 diagrams.net에서 열어 label 겹침과 선 배치를 확인한 뒤 필요하면 위치를 미세 조정할 수 있다.
- 배포 전 확인: 문서 산출물이므로 애플리케이션 배포 사항은 없다.
- 예상 밖 diff: 이번 작업은 신규 draw.io와 두 기록 문서만 변경했다. 그 밖의 애플리케이션·설정 파일 변경은 없다.
- Git commit·push는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 시스템 구성도 연결선 라우팅 개선

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 draw.io 가독성 개선은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 사용자 PNG 확인 결과 기본 자동 라우팅이 앱→Learning Core, Identity→Billing과 Learning Core↔Python AI 연결선을 서비스 내부 도형 위로 통과시키고 긴 label을 겹치게 하는 문제를 확인했다.
- `tosunsaeng-app-system-configuration.drawio`의 주요 연결선 9개에 entry/exit 지점과 고정 경유점을 지정했다. 상단 공개·eligibility 통로, 서비스 하단 Billing 통로, 데이터 저장소 사이 AI 요청 통로, 우측 외곽 Callback 통로로 분리했다.
- 긴 edge label을 짧게 줄이고 offset을 지정했으며 앱→Identity와 인증 Provider→Identity는 서비스 사이의 짧은 전용 통로만 사용하게 했다.
- XML 검증, edge 14개, 수동 routing edge 9개, 누락 source/target 0개와 `git diff --check` 성공을 확인했다. 애플리케이션 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.
- 공개 API·`BaseResponse`·사용자 식별·AI·S3·Redis·Billing 계약과 시스템 의미는 변경하지 않았다. diagrams.net 최종 화면에서 label 미세 위치는 사용자가 확인할 수 있다.
- 이번 작업은 draw.io와 기록 문서만 변경했으며 애플리케이션·설정 파일의 예상 밖 변경은 없다. 배포 사항과 Git commit·push는 없고 Secret·Token을 기록하지 않았다.

## 2026-09-01 — 시스템 구성도 연결선 개선 종료 기록

<!-- codex-turn:01a05bdb-4bf7-7e83-b7ce-45d895c810c4 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 draw.io 연결선 개선은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 변경 파일은 `docs/architecture/tosunsaeng-app-system-configuration.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`다.
- 주요 연결선을 상단·서비스 하단·데이터 저장소 사이·우측 외곽 통로로 분리하고 label을 축약·offset 처리했다. 시스템 구성의 의미와 외부 계약은 변경하지 않았다.
- `xmllint --noout`, edge 14개·수동 route 9개·누락 source/target 0개와 `git diff --check` 성공을 확인했다. 문서 변경이라 Gradle 테스트는 실행하지 않았다.
- diagrams.net 실제 화면에서의 최종 미세 배치는 사용자가 다시 연 파일로 확인할 수 있다. 배포·Git commit·push는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 시스템 구성도의 백엔드 편향 검토

<!-- codex-turn:01a05be3-191e-7291-8986-3060546fa60c -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 도식 관점 검토는 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 5줄 결론: 현재 draw.io는 정확하지만 일반적인 전체 시스템 구성도보다 앱 백엔드 기술 구성도에 가깝다. 백엔드 개발자·멘토 대상에는 적절하다. 제품·비개발 이해관계자 대상에는 내부 aggregate·Job·원장 명칭이 과하다. 기존 그림은 백엔드 상세로 보존하는 것이 좋다. 사용자→앱 기능→서비스→외부 연동만 보이는 상위 시스템 구성도를 첫 페이지로 추가하는 방식을 권고한다.
- 반드시 읽을 내용: 하나의 도식으로 모든 독자를 만족시키기보다 1페이지 제품·시스템 관점과 2페이지 백엔드 기술 관점을 분리한다. 상위 페이지에는 로그인, 시험 시작, 녹음, AI 채점, 결과 확인의 사용자 흐름과 Identity·Learning Core·Billing의 역할만 표시한다.
- 사용자 결정 사항: 현재 파일의 기존 페이지를 `백엔드 기술 구성도`로 이름 변경하고, 별도 첫 페이지 `전체 시스템 구성도`를 추가할지 결정하면 된다. 권고는 두 페이지 유지다.
- 위험·미확인: 상위 도식에 실제로 존재하지 않는 프론트 인프라, API Gateway, CDN 등을 추정해 넣지 않아야 한다. 대상 독자와 발표 목적이 확정되면 정보 밀도를 조정해야 한다.
- 변경·검증: 이번 턴은 관점 분석과 기록 문서 갱신만 수행했고 draw.io·애플리케이션·설정·외부 계약을 변경하지 않았다. 코드 변경이 없어 Gradle 테스트를 실행하지 않고 `git diff --check`로 검증한다.
- 배포·예상 밖 diff: 배포 사항은 없으며 기존 draw.io와 기록 문서 외 예상 밖 파일은 수정하지 않았다. Git commit·push를 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 전체 시스템·AWS 구성도 첫 페이지 추가

<!-- codex-turn:01a05be6-7b2e-76a2-a981-17a5f2fe903e -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 AWS 구성도 개선은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 5줄 결론: `tosunsaeng-app-system-configuration.drawio`를 2페이지로 확장했다. 첫 페이지는 사용자·앱과 AWS runtime/deployment 구조를 함께 보여준다. 둘째 페이지는 기존 백엔드 기술 구성도를 보존했다. 확인된 현재 리소스와 승인된 목표 리소스를 선·색으로 구분했다. 실제 inventory가 없는 AWS 경로는 추정하지 않고 미확인으로 표시했다.
- 반드시 읽을 내용: 현재 Identity·Learning Core는 ALB와 `tosunsaeng-staging-cluster` ECS Service를 사용하고 GitHub Actions OIDC→ECR→ECS Task Definition revision 배포를 수행한다. Billing/VPC Lattice는 승인 목표지만 아직 미배포이며 Billing은 private Fargate·no ALB/public IP·Lattice AWS_IAM 경계다.
- 근거: Learning Core·Identity `.github/workflows/deploy-staging.yml`, Billing `docs/adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`의 현재 사실·목표 topology·IAM/SG 계약을 사용했다.
- 변경 파일: `docs/architecture/tosunsaeng-app-system-configuration.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 구현 내용: AWS Cloud·VPC·ALB ingress·ECS Fargate·ECR·S3·IAM/Secret reference·관측 영역, 외부 앱/인증/AI/데이터 의존성을 배치했다. production/staging 분리 목표와 현재 staging 명칭 cluster가 실제 트래픽을 처리하는 상태를 명시했다.
- 유지한 계약: 공개 API·`BaseResponse`·사용자 식별·AI·S3·Redis·Billing wire와 배포 workflow를 변경하지 않았다.
- 테스트·검증: draw.io XML, 페이지 2개, AWS 페이지 vertex 25개·edge 16개, 누락 source/target 0개와 `git diff --check` 성공을 확인했다. 애플리케이션 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.
- 위험·미확인: 실제 ALB listener/target/DNS, VPC/subnet/SG/role ARN, MongoDB Atlas·Redis·NAT 경로와 Secret 서비스는 AWS read-only inventory 전까지 확정하지 않았다. draw.io CLI가 없어 실제 렌더 export는 수행하지 못했다.
- 배포·예상 밖 diff: 문서 변경이므로 배포 사항은 없다. 사용자가 별도로 만든 것으로 보이는 untracked `docs/architecture/제목 없는 다이어그램.drawio`는 수정하지 않았다.
- Git commit·push는 수행하지 않았고 Secret과 Token을 기록하지 않았다.
- 종료 전 라우팅 보완: Identity→Lattice는 서비스 상단과 cluster 사이 통로, Learning Core↔AI는 ECS 영역 우측과 VPC 하단 통로, 앱→S3는 managed resource 하단, 데이터 의존성은 ECS/Lattice 하단 통로로 우회시켰다. AWS 페이지 16개 edge 중 14개에 고정 경유점을 지정해 다른 핵심 도형을 관통하지 않도록 했다.

## 2026-09-01 — 1차 업데이트 완료 기준 시스템·AWS 구성도

<!-- codex-turn:01a05bed-0554-7680-a519-b62c5618369d -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 구성도 시점 변경은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 5줄 결론: 첫 페이지를 현재와 목표의 비교가 아니라 `1차 업데이트 완료 시점`의 단일 운영 스냅샷으로 변경했다. SNS/Phone 로그인, 무료 모의고사, 기존 시험·AI 채점, Billing Reservation·AttemptGroup lifecycle, 10초 챌린지를 완료 기능으로 표현했다. Production ECS Fargate, public ALB, private Billing과 VPC Lattice AWS_IAM 경계를 완료 구조로 표시했다. Identity·Learning Core·Billing별 MongoDB, Redis, S3와 기존 시험·Challenge AI 연동을 포함했다. 기존 둘째 페이지 백엔드 기술 구성도와 고정 연결선 routing은 보존했다.
- 완료 기준 근거: `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`의 출시 차단 조건, Learning Core·Identity staging workflow의 GitHub Actions OIDC→ECR→ECS 배포 방식, Billing `docs/adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`의 production topology를 반영했다.
- 변경 파일: `docs/architecture/tosunsaeng-app-system-configuration.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 유지한 계약: 애플리케이션 코드, 공개 API·`BaseResponse`, 사용자 식별, AI request/Callback, S3·Redis·Billing wire와 배포 workflow를 변경하지 않았다.
- 테스트·검증: `xmllint --noout` 성공, 페이지 2개, 첫 페이지 vertex 25개·edge 16개, 누락 source/target 0개, 과도기 표기 미검출과 `git diff --check` 성공을 확인했다. 문서 변경이라 Gradle 테스트는 실행하지 않았다.
- 위험·미확인: 이 그림은 1차 업데이트의 release gate와 production canary까지 완료됐다는 전제의 논리 구성도다. 실제 ALB listener/target/DNS, subnet·SG·ARN 같은 물리 resource ID는 AWS inventory 없이 추정하지 않았다. diagrams.net에서 실제 렌더링한 뒤 label의 미세 위치를 확인할 수 있다.
- 배포 전 확인: 이 문서 자체의 배포 사항은 없다. 실제 1차 업데이트 배포 시에는 production/staging 격리, Mongo replica-set, Lattice/IAM/SG, 장애 복구 E2E, canary와 rollback 검증이 완료 조건이다.
- 예상 밖 diff: 사용자가 별도로 만든 untracked `docs/architecture/제목 없는 다이어그램.drawio`는 수정하지 않았다. 애플리케이션·설정 파일의 예상 밖 변경은 없다.
- Git commit·push는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 앱 프론트·AI 근거 기반 전체 제품 구성도 개편

<!-- codex-turn:01a05bf3-4e4a-7c11-ac0b-097a1b4a180f -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 구성도 개편은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 5줄 결론: `app-front-end`와 `web-ai`를 직접 조사해 구성도를 백엔드 중심 2페이지에서 제품 중심 3페이지로 개편했다. 첫 페이지는 학습자가 앱에서 로그인·시험·챌린지·피드백·재답변을 경험하는 전체 흐름을 보여준다. 둘째 페이지는 앱과 네 서비스의 AWS 배포·데이터 구조를 보여준다. 셋째 페이지는 기존 백엔드 기술 상세를 보존한다. AI를 AWS 외부 시스템이 아니라 ECS의 FastAPI API·Redis queue·4개 worker 서비스로 바로잡았다.
- 조사 근거: `Too-Much-I/app-front-end` commit `4e6c5957f72a92025ac0eb6d9bd7beff51eb1783`의 navigation, auth, exam/challenge API·hook, WebView native bridge, CI/EAS·관측 설정과 `Too-Much-I/web-ai` commit `ee9db665ddadd9b830c3d39988157ed535ccaa04`의 FastAPI route, scoring pipeline, Redis worker, Callback, Docker Compose와 app ECS workflow를 읽었다.
- 확인된 구현 사실: 앱은 Expo 57 React Native이며 홈·모의고사·피드백 탭, 마이크/사운드 점검, 11문항 녹음·S3 upload·Polling, 피드백 WebView/native data bridge, 재답변과 10초 챌린지 화면/API 경계를 가진다. AI는 현재 시험 `/evaluations`, Q1 Azure, Q2~Q11 STT+Azure+LLM/VLM, 결정론 checklist score, 한국어 feedback/summary Callback과 ECS API+worker 배포를 구현한다.
- 완료 시점 전제: 조사한 프론트 개발 모드의 Challenge mock은 제거되고 실제 Learning Core API를 사용하며, 조사한 AI 저장소에는 아직 없는 Challenge 전용 평가 endpoint·양방향 credential/Callback이 1차 업데이트 완료 조건에 따라 구현·E2E 검증된 상태로 표현했다. SNS/Phone 로그인도 같은 완료 조건으로 표시했다.
- 변경 파일: `docs/architecture/tosunsaeng-app-system-configuration.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 변경한 동작: draw.io 페이지를 `1. 제품·사용자 흐름 구성도`, `2. AWS·배포 구성도`, `3. 백엔드 기술 구성도`로 재구성했다. 첫 페이지에 사용자 가치, 앱의 여섯 기능 영역, Identity/Learning/Billing/AI, WebView 콘텐츠, S3, AI provider, 서비스별 데이터, 관측과 앱·서버 배포를 연결했다.
- 유지한 외부 계약: 애플리케이션 코드, 공개 API·`BaseResponse`, userId 식별, `AI user_id=examId`, Callback JSON, retryCount, S3·Redis·Billing 계약과 배포 workflow를 변경하지 않았다.
- 테스트·결과: `xmllint --noout` 성공, 페이지 3개, 페이지별 `24/17`, `25/17`, `29/14` vertex/edge, 전체 누락 source/target 0개와 `git diff --check` 성공을 확인했다. 문서만 변경해 Gradle·프론트·AI 테스트는 실행하지 않았다.
- 주요 위험·미확인: draw.io CLI가 없어 실제 PNG 렌더 export는 수행하지 못했다. 정확한 production ALB/target/subnet/SG/ARN과 AI Redis·runtime의 실제 AWS resource topology는 read-only inventory 없이 추정하지 않았다. 피드백 Web 콘텐츠 저장소는 이번에 제공되지 않아 앱의 WebView·bridge 사용 사실까지만 표현했다.
- 배포 전 확인: Challenge mock 제거, Challenge AI contract/E2E, SNS/Phone mobile E2E, AI ECS queue/runtime 공유 방식, production/staging 격리, canary/rollback을 실제 환경에서 다시 확인해야 한다.
- 예상 밖 diff: 사용자가 별도로 만든 untracked `docs/architecture/제목 없는 다이어그램.drawio`는 수정하지 않았다. 애플리케이션·설정 파일의 예상 밖 변경은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 읽거나 기록하지 않았다.

## 2026-09-01 — 전체 제품·AWS 구성도 화살표 정리

<!-- codex-turn:01a05bff-985b-7ca2-82a5-61264ce40cd7 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 구성도 가독성 개선은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 변경 파일: `docs/architecture/tosunsaeng-app-system-configuration.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 변경한 동작: 제품 페이지의 연결선을 17개에서 14개로 줄였다. AI 평가 요청과 문항·요약 Callback을 양방향 한 줄로 합치고, 긴 결과 회귀선과 의미가 박스 본문에 이미 있는 데이터 소유권 선을 제거했다. 앱 음성 Presigned PUT은 Identity/Learning 사이의 빈 세로 통로와 하단 전용 통로를 거쳐 S3에 연결했다.
- AWS 페이지: Learning Core↔AI 평가 요청/Callback 두 줄을 양방향 한 줄로 합쳐 17개에서 16개로 줄였다. 기존 ingress, Lattice, S3, 배포와 데이터선의 고정 routing은 보존했다.
- 유지한 외부 계약: 도식의 기능·서비스·AWS 내용과 애플리케이션 코드, 공개 API·`BaseResponse`, userId, AI request/Callback, S3·Redis·Billing 계약을 변경하지 않았다.
- 테스트·결과: `xmllint --noout` 성공, 페이지별 vertex/edge/routed가 `24/14/8`, `25/16/13`, `29/14/9`, 전체 누락 source/target 0개이며 `git diff --check`가 통과했다. 문서만 변경해 Gradle·프론트·AI 테스트는 실행하지 않았다.
- 위험·미확인: draw.io CLI가 없어 PNG 렌더링 기반 육안 검증은 수행하지 못했다. diagrams.net에서 열었을 때 label의 미세 offset은 추가 조정할 수 있다.
- 배포 전 확인: 문서 변경이라 배포 사항은 없다. 구성도 사용 전 첫 페이지와 둘째 페이지를 100% zoom에서 열어 label 겹침 여부만 확인하면 된다.
- 예상 밖 diff: 사용자가 만든 untracked `docs/architecture/제목 없는 다이어그램.drawio`는 수정하지 않았다. 애플리케이션·설정 파일의 예상 밖 변경은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 구성도 화살표를 도형 뒤 레이어로 이동

<!-- codex-turn:01a05c03-d993-7102-9367-82eed9c99fb0 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 draw.io 레이어 조정은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 변경 파일: `docs/architecture/tosunsaeng-app-system-configuration.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 변경한 동작: 세 페이지의 mxCell 저장 순서를 `큰 영역 배경 → edge → 실제 도형·텍스트`로 재배치했다. 연결선은 AWS/VPC/서비스 영역 배경 위에는 보이지만 기능 카드·서비스 박스·글자보다 뒤에서 렌더링된다.
- 유지한 내용: 연결선 수, source/target, 고정 경유점, 기능·서비스·AWS 설명과 페이지 구조는 변경하지 않았다.
- 유지한 외부 계약: 애플리케이션 코드, 공개 API·`BaseResponse`, 사용자 식별, AI request/Callback, S3·Redis·Billing 계약을 변경하지 않았다.
- 테스트·결과: `xmllint --noout` 성공, 3페이지와 페이지별 vertex/edge `24/14`, `25/16`, `29/14`를 유지했다. foreground 도형보다 앞에 남은 edge 0개, edge보다 뒤에 잘못 배치된 영역 배경 0개, 누락 source/target 각각 0개와 `git diff --check` 성공을 확인했다. 문서만 변경해 Gradle·프론트·AI 테스트는 실행하지 않았다.
- 위험·미확인: draw.io CLI가 없어 실제 PNG export 기반 육안 검증은 수행하지 못했다. diagrams.net renderer가 mxCell z-order를 따르는 일반 동작을 기준으로 적용했다.
- 배포 전 확인: 문서 변경이라 배포 사항은 없다. diagrams.net에서 첫 페이지를 열어 선이 카드 뒤로 가려지는지만 확인하면 된다.
- 예상 밖 diff: 임시 layer 변환 stylesheet는 작업 후 삭제했다. 사용자가 만든 untracked `docs/architecture/제목 없는 다이어그램.drawio`는 수정하지 않았고 애플리케이션·설정 파일의 예상 밖 변경은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — draw.io 실제 레이어 분리와 렌더링 재검증

<!-- codex-turn:01a05c07-5d0e-74f1-ac32-847056a0d2ae -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 구성도 렌더링 수정은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 문제 원인: 동일 draw.io layer 안에서 mxCell XML 순서만 바꾸는 이전 방식은 diagrams.net renderer가 edge를 다시 위에 그릴 수 있어 실제 화면에서 도형 뒤 배치를 보장하지 못했다.
- 복구 기록: 최초 실제 layer 변환의 임시 출력 검증이 실패했는데 shell이 후속 이동을 계속해 target이 일시적으로 빈 파일이 됐다. 빈 결과를 최종 산출물로 사용하지 않고 제품·AWS 페이지를 재생성했으며, 사용자가 별도로 보관한 `docs/architecture/제목 없는 다이어그램.drawio`를 읽기 전용 원본으로 사용해 백엔드 상세 페이지를 복원했다. 원본 파일은 수정하지 않았다.
- 변경 파일: `docs/architecture/tosunsaeng-app-system-configuration.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 변경한 동작: 각 페이지에 draw.io top-level layer `배경`, `연결선`, `도형·텍스트`를 실제로 추가했다. 모든 edge의 parent는 `edge-layer`, 영역 배경은 `background-layer`, 실제 카드·텍스트는 `foreground-layer` 또는 그 하위 cell이다.
- 실제 렌더 검증: Browser 스킬을 사용해 첫 페이지 mxGraphModel을 diagrams.net 편집기에 로드하고 스크린샷으로 확인했다. 기능 카드가 연결선보다 위에 렌더링되고, 선은 별도 하위 layer에 존재하는 것을 확인했다.
- 유지한 내용: 제품 페이지의 사용자·앱·Identity/Learning/Billing/AI·S3·관측 흐름, AWS 페이지의 production 논리 구조와 백엔드 상세 페이지를 유지했다. 복원 과정에서 AWS 페이지 표현을 동일 의미의 간결한 14개 edge 구조로 수렴시켰다.
- 유지한 외부 계약: 애플리케이션 코드, 공개 API·`BaseResponse`, 사용자 식별, AI request/Callback, S3·Redis·Billing 계약을 변경하지 않았다.
- 테스트·결과: `xmllint --noout` 성공. 페이지별 `3 layers / 24 vertices / 14 edges`, `3 / 21 / 14`, `3 / 28 / 15`이며 모든 edge가 `edge-layer`에 있다. 잘못된 edge parent 0개, 누락 source/target 각각 0개와 `git diff --check` 성공을 확인했다. 문서만 변경해 Gradle·프론트·AI 테스트는 실행하지 않았다.
- 위험·배포 전 확인: 문서 변경이라 배포 사항은 없다. 사용자가 로컬 diagrams.net에서 기존 열린 탭이 아니라 수정된 파일을 다시 열어 layer 효과를 확인해야 한다.
- 예상 밖 diff: 임시 generator·stylesheet는 삭제했다. 사용자 원본과 애플리케이션·설정 파일의 예상 밖 변경은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 데일리 학습 콘텐츠 수행 방법 문구 작성

<!-- codex-turn:01a05c24-a3d0-7231-b2cf-20eb9242f4f6 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 문구 작성은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 작업 내용: 사용자가 작성 중인 수행 방법 표의 `개발 단계 > 데일리 학습 콘텐츠` 항목에 넣을 문구를 토선생의 10초 챌린지와 실제 구현 방향에 맞게 정리했다.
- 권장 문구: TOEIC Speaking 유형과 학습자 취약 영역을 반영한 짧은 일일 말하기 문제를 자체 기획·제작하고, 앱에서 매일 새로운 문제 제공, 10초 음성 녹음, AI 발화 분석, 교정 문장·모범 답안·맞춤 피드백 확인까지 이어지는 반복 학습 기능을 구현한다. 초기 콘텐츠는 전문가 검수와 자체 제작으로 확보하고 이용·정답률·오류 유형 데이터를 분석해 난이도와 문항을 지속 개선한다.
- 표현 원칙: 단순히 콘텐츠를 제공한다고 쓰지 않고 콘텐츠 확보 방법, 사용자 학습 절차, AI 피드백, 운영 개선 순서가 드러나게 했다. 구현된 사실과 향후 운영 계획을 과장하지 않도록 구분한다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 유지한 외부 계약: 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약과 draw.io를 변경하지 않았다.
- 테스트·결과: 문구 작성과 기록 문서만 변경해 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 문서 형식을 검증한다.
- 위험·배포 전 확인: 제출처의 글자 수 제한과 10초 챌린지 명칭 공개 가능 여부가 확인되지 않았다. 제한이 있으면 축약형을 사용한다. 배포 사항은 없다.
- 예상 밖 diff: 이번 작업에서 기록 문서 외 파일을 수정하지 않았다. 기존 untracked draw.io 파일은 건드리지 않았다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 데일리 학습 콘텐츠 예상 문제점·결과물 문구

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 문구 작성은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 작업 내용: 수행 방법 표의 데일리 학습 콘텐츠에 대응하는 `예상 문제점-개발 측면`과 `결과물 형태` 문구를 작성했다.
- 예상 문제점: 매일 제공할 문항의 지속적 확보와 난이도·품질 관리, 다양한 정답 표현에 대한 AI 판정 일관성, 모바일 음성 녹음·업로드 실패, 비동기 채점 지연과 중복 제출, AI 비용과 개인정보 보호를 핵심 개발 위험으로 정리했다.
- 대응 방향: 콘텐츠 snapshot·전문가 검수, 정답 허용 범위와 평가 기준 표준화, 멱등 제출·Polling·재시도, 음성·Transcript 최소 보관과 민감정보 비기록, 사용 데이터 기반 개선을 제시한다.
- 결과물 형태: 모바일 앱의 오늘 문제·10초 녹음·결과/이력 화면, 일일 문항 catalog DB, 음성 업로드·채점·결과 API, AI 교정·모범답안·피드백 결과, 테스트·운영 지표 문서로 구분한다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 유지한 외부 계약: 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약과 draw.io를 변경하지 않았다.
- 테스트·결과: 문구와 기록 문서만 변경해 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 문서 형식을 검증한다.
- 위험·배포 전 확인: 제출 양식의 글자 수와 결과물 분류 기준이 확인되지 않았다. 제한이 있으면 축약형을 사용한다. 배포 사항은 없다.
- 예상 밖 diff: 이번 작업에서 기록 문서 외 파일을 수정하지 않았다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 데일리 학습 콘텐츠 문제점·결과물 문구 종료 기록

<!-- codex-turn:01a05c26-be32-78e0-bc4a-8b4de8084d53 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 문구 작성은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 완료 내용: 데일리 학습 콘텐츠의 개발 측면 예상 문제점을 콘텐츠 확보·품질과 난이도, AI 평가 일관성, 음성 업로드, 비동기 채점 지연·중복 제출, AI 비용·개인정보 보호로 정리했다.
- 결과물 형태: 오늘 문제·10초 녹음·채점 대기·결과/이력 앱 화면, 문항 catalog DB, 음성 업로드·채점·결과 API, AI 교정 문장·모범 답안·맞춤 피드백과 운영·테스트 문서로 제시했다.
- 사용자 전달: 표에 넣을 기본 문장과 공간이 좁을 때 사용할 축약형을 함께 제공했다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약: 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약과 draw.io를 변경하지 않았다.
- 테스트: 문구와 기록 문서만 변경해 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 검증한다.
- 위험·배포: 제출 양식의 글자 수 제한은 확인되지 않았으며 문서 작업이므로 배포 사항은 없다.
- 예상 밖 변경: 기록 문서 외 이번 작업으로 변경한 파일은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 중간 발표용 개발 문제점 2개 선정

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 발표 문구 선정은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 선정 결과: 전체 개발 위험 중 중간 발표에는 토선생의 핵심 가치와 직접 연결되는 `AI 채점 품질·신뢰도`와 사업 지속 가능성을 보여주는 `외부 AI API 비용·의존성` 두 가지를 권장했다.
- 해결 방향: 채점 sample 검수·품질 지표·이상 결과 조기 보정과 오픈소스/복수 모델 사전 검증·비용/성능 비교·유연한 모델 선택 구조를 각각 대응 방안으로 제시했다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약: 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약과 draw.io를 변경하지 않았다.
- 테스트: 문구와 기록 문서만 변경해 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 검증한다.
- 위험·배포: 발표 자료의 글자 수 제한은 확인되지 않았고 배포 사항은 없다.
- 예상 밖 변경: 기록 문서 외 이번 작업으로 변경한 파일은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 전체 서비스 예상 문제점·해결 방안·결과물 문구 재정리

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 문구 작성은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 정정 내용: `예상 문제점-개발 측면`과 `결과물 형태`를 데일리 학습 콘텐츠 한 기능이 아니라 모의고사, 피드백, 학습 로드맵·챗봇, 데일리 학습 콘텐츠와 피드백 챗봇을 포함한 전체 토선생 서비스 기준으로 재작성했다.
- 예상 문제점과 해결: 다기능 범위 확대는 MVP·단계별 roadmap과 acceptance criteria로 통제하고, 앱·Identity·Learning·Billing·AI 간 계약 불일치는 계약 문서·contract test·staging E2E로 방지한다. AI 품질 편차는 표준화 rubric·benchmark·전문가 검수·fallback으로, 음성·비동기 실패는 형식 표준화·멱등 처리·retry/Polling·복구 Job으로 대응한다.
- 추가 해결: 개인정보는 최소 수집·보관 기간·암호화·로그 비식별화로 보호하고, AI 비용과 확장성은 사용량 metric·cache·worker scale-out·모델별 비용 기준으로 관리한다. 콘텐츠 품질은 catalog·snapshot·검수 workflow와 학습 데이터 분석으로 개선한다.
- 결과물 형태: iOS/Android 앱, Identity·Learning Core·Billing 서버, AI 채점/피드백 서버, 모의고사·데일리 콘텐츠 catalog, 학습 로드맵·피드백 챗봇, API/데이터 계약 문서, 관리자·운영 지표, 테스트 결과와 배포·모니터링 체계를 전체 산출물로 정리했다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약: 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약과 draw.io를 변경하지 않았다.
- 테스트: 문구와 기록 문서만 변경해 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 검증한다.
- 위험·배포: 제출 양식의 글자 수 제한은 확인되지 않았으며 문서 작업이므로 배포 사항은 없다.
- 예상 밖 변경: 기록 문서 외 이번 작업으로 변경한 파일은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 전체 서비스 문제점·해결 방안·결과물 문구 종료 기록

<!-- codex-turn:01a05c28-6f95-7123-b3ed-db4f3386f883 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 문구 작성은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 완료 내용: 개발 측면 예상 문제점과 결과물 형태의 범위를 데일리 콘텐츠 한 기능이 아닌 토선생 전체 서비스로 정정했다.
- 문제점·해결 방안: 범위 확대, 서비스 간 계약 불일치, AI 평가 편차, 음성·비동기 실패, 콘텐츠 확보, 개인정보, 비용·확장성과 배포 복잡성을 식별하고 각각 단계별 MVP, 계약·E2E 테스트, 표준 평가·전문가 검수, 멱등·retry·복구, catalog 운영, 데이터 보호, 사용량 모니터링과 자동 배포·rollback으로 연결했다.
- 결과물 형태: iOS·Android 앱, Identity·Learning Core·Billing·AI 서버, 모의고사·데일리 콘텐츠 DB, AI 피드백·학습 로드맵·챗봇, API·데이터 계약서, 테스트·분석·운영·배포 문서를 전체 산출물로 정리했다.
- 사용자 전달: 제출용 상세 문단, 문제점/해결 방안 대응표와 글자 수가 짧을 때 사용할 축약형을 제공했다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약: 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약과 draw.io를 변경하지 않았다.
- 테스트: 문구와 기록 문서만 변경해 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 검증한다.
- 위험·배포: 제출 양식의 글자 수 제한은 확인되지 않았으며 문서 작업이므로 배포 사항은 없다.
- 예상 밖 변경: 기록 문서 외 이번 작업으로 변경한 파일은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 전체 서비스 개발 측면 예상 문제점 문체 정리

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 문구 작성은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 작업 내용: 사용자가 제시한 `발생 가능성 → 모니터링·검증·대체 수단 구축` 문체에 맞춰 토선생 전체 서비스의 개발 측면 예상 문제점과 해결 방안을 대응 순서로 작성했다.
- 문제 범위: AI 채점 품질, 외부 AI 비용, 앱·서버·AI 계약 불일치, 음성 업로드·비동기 채점 장애, 개인정보·보안과 이용량 증가에 따른 성능 저하를 포함한다.
- 해결 방향: 품질 sample 검수·지표·benchmark, 대체 모델 검증·비용 routing, 계약 문서·자동 contract/E2E test, 멱등·retry·Polling·복구 처리, 최소 수집·암호화·비식별화와 scale-out·관측 체계를 각 문제에 연결한다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약: 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약과 draw.io를 변경하지 않았다.
- 테스트: 문구와 기록 문서만 변경해 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 검증한다.
- 위험·배포: 제출 양식의 글자 수 제한은 확인되지 않았고 배포 사항은 없다.
- 예상 밖 변경: 기록 문서 외 이번 작업으로 변경한 파일은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 전체 서비스 개발 문제점·해결 방안 제출 문구 종료

<!-- codex-turn:01a05c2c-0d6f-7bf2-914f-7d9e5b500501 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 문구 작성은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 완료 내용: 전체 토선생 서비스의 개발 측면 예상 문제점 7개와 같은 순서로 대응하는 해결 방안 7개를 제출용 문체로 작성했다.
- 문제점: AI 채점 품질·일관성, 외부 AI 비용, 다중 서비스 계약 불일치, 모바일 음성·비동기 Callback 장애, 트래픽 집중, 개인정보·학습 데이터 보호와 콘텐츠 버전·품질 불일치를 포함했다.
- 해결 방안: sample 검수·품질 지표, 대체 모델 사전 검증, API 계약·자동 테스트, 멱등·retry·Polling·Callback 복구, 비동기 queue·scale-out, 최소 수집·암호화·비식별화와 콘텐츠 version·전문가 검수를 각 항목에 연결했다.
- 사용자 전달: 사용자가 제시한 `발생할 가능성`과 `지속적으로 모니터링·구축` 문체를 유지하고 문제점 목록과 해결 방안 목록의 순서를 일치시켰다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약: 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약과 draw.io를 변경하지 않았다.
- 테스트: 문구와 기록 문서만 변경해 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 검증한다.
- 위험·배포: 제출 양식의 글자 수 제한은 확인되지 않았고 문서 작업이므로 배포 사항은 없다.
- 예상 밖 변경: 기록 문서 외 이번 작업으로 변경한 파일은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 중간 발표용 개발 문제점 2개 최종 선정

<!-- codex-turn:01a05c2d-d987-7791-b62f-57222333ac4e -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 발표 문구 선정은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 완료 내용: 중간 발표에 사용할 개발 측면 예상 문제점으로 `AI 채점 품질·신뢰도`와 `외부 AI API 비용·의존성` 두 가지를 최종 추천했다.
- 해결 방안: 채점 결과 표본 검수·품질 지표 모니터링·이상 결과 조기 보정과 오픈소스·복수 모델 사전 검증·비용/성능 기반 선택 구조를 각각 연결했다.
- 선정 근거: 두 항목이 토선생의 핵심 경쟁력인 채점 신뢰도와 서비스의 지속 가능한 운영을 가장 직접적으로 설명한다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 유지한 외부 계약: 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약과 draw.io를 변경하지 않았다.
- 테스트·결과: 문서 기록만 변경하여 Gradle 테스트는 실행하지 않았고 `git diff --check`로 형식을 검증한다.
- 위험·배포 전 확인: 발표 자료의 글자 수 제한은 확인되지 않았으며 코드 배포 사항은 없다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 이번 작업에서 수정하지 않았다.
- 다음 작업 전 확인: 실제 발표 양식의 분량에 따라 문장을 축약할지 확인한다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-02 — TMI-118 hotfix 이후 다음 작업 확정

<!-- codex-turn:01a05fa8-6bd9-7521-a97f-cb3c26961017 -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`; 상태는 완료이며 이번 작업에서 변경하지 않았다.
- Git 확인: hotfix commit `4781723`이 PR #26 merge commit `4f9e74c`로 병합됐고 현재 `HEAD`, 로컬 `develop`, `origin/develop`이 `4f9e74c`로 일치한다.
- 다음 즉시 작업: AttemptGroup 실제 경계 통합 검증을 신규 후속 Jira로 분리한다. 격리된 replica-set Mongo에서 transaction commit/rollback, duplicate·unknown commit 수렴, COMPLETED/RETAKE terminal race와 multi-instance lease reclaim/stale token fencing을 검증한다.
- 전송·관측 검증: fake signer와 local HTTP component test로 publish span traceparent 주입 후 SigV4가 마지막 변경인지 확인하고, payload·credential·사용자 식별자가 log/span/metric에 없는지 검사한다. 이후 Learning Core/Billing staging E2E에서 같은 traceId, 다른 spanId와 baggage 미전파를 확인한다.
- 다음 제품 기능: 위 rollout gate 이후 Billing `UserMerged` retained subject owner rebind의 wire 계약·활성 Reservation 충돌 정책을 ADR과 계획서로 확정하고 전용 Jira를 생성한다.
- 변경 파일: 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·테스트·설정과 외부 계약은 변경하지 않았다.
- 테스트·검증: 코드 변경이 없어 Gradle 테스트를 재실행하지 않았다. hotfix PR 포함 관계와 Git ref를 읽기 전용으로 확인하고 문서는 `git diff --check`와 marker 1회 검사로 검증한다.
- 예상 밖 변경: 기존 미추적 `docs/architecture` draw.io 파일 2개는 사용자 변경으로 보존했으며 수정하지 않았다.
- 보안·범위: Secret·Token을 기록하지 않았고 Jira 생성·상태 전환, DB·AWS·Git commit·push·PR·merge·배포를 수행하지 않았다.

## 2026-09-02 — 학습 로드맵·챗봇 무료/유료 설명 정리

<!-- codex-turn:01a060e0-a105-72f3-a7ce-66fc65f476ec -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 문구 작성 작업이다.
- 완료 내용: 학습 로드맵·챗봇 기능을 무료형 `표준 로드맵 안내`와 유료형 `학습 데이터 기반 맞춤형 코칭`으로 구분해 제출용 문장으로 정리했다.
- 무료 기능: 최초 목표 설정 시 입력받은 목표 등급과 시험 준비 기간을 기준으로, 사전에 분류·설계한 학습 로드맵 중 적합한 경로를 안내한다.
- 유료 기능: 기존 로드맵을 기준으로 사용자의 학습 이력, 모의고사 결과와 피드백을 함께 분석하여 현재 수준과 취약점을 반영한 세부 학습 방법과 우선순위를 챗봇이 안내한다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약·테스트: 애플리케이션 코드와 공개 API·AI·S3·Redis·Billing 계약은 변경하지 않았다. 문서 기록만 변경하여 Gradle 테스트는 실행하지 않고 `git diff --check`로 검증한다.
- 위험·미확인: 유료 로드맵의 갱신 주기, 추천 범위, 사용자에게 보여줄 근거 수준과 무료/유료 전환 조건은 아직 확정되지 않았다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 이번 작업에서 수정하지 않았다.
- 보안·범위: Secret과 Token을 기록하지 않았고 Git commit·push·배포를 수행하지 않았다.

## 2026-09-02 — 학습 로드맵·챗봇 상세 설명 확장

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 문구 작성 작업이다.
- 완료 내용: 학습 로드맵·챗봇을 목표 설정, 무료 표준 경로 제공, 유료 학습 데이터 분석, 챗봇 코칭, 로드맵 재조정과 품질 통제의 전체 흐름으로 상세화했다.
- 무료 기능: 목표 등급과 준비 기간을 입력받아 전문가가 사전 설계·분류한 로드맵을 규칙 기반으로 매칭하고, 기간별 학습 순서와 권장 학습량을 안내하는 구조로 설명했다.
- 유료 기능: 학습 이력, 모의고사 점수, 파트·문항별 피드백과 반복 취약점을 표준 로드맵에 결합해 우선순위·실행 과제·복습 방향을 개인화하고 이후 성과에 따라 재조정하는 구조로 설명했다.
- 품질 원칙: AI가 검증되지 않은 학습법을 새로 생성하지 않고 사전 설계된 로드맵 범위 안에서 근거 데이터를 바탕으로 안내하며, 데이터가 부족할 때는 표준 경로를 유지하도록 정리했다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약·테스트: 애플리케이션 코드와 공개 API·AI·S3·Redis·Billing 계약은 변경하지 않았다. 문서 기록만 변경하여 Gradle 테스트는 실행하지 않고 `git diff --check`로 검증한다.
- 위험·미확인: 목표 등급 체계, 준비 기간 구간, 로드맵 분류표, 개인화 갱신 시점과 챗봇이 제시할 세부 항목은 제품 정책으로 확정해야 한다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 이번 작업에서 수정하지 않았다.
- 보안·범위: Secret과 Token을 기록하지 않았고 Git commit·push·배포를 수행하지 않았다.

## 2026-09-02 — 학습 로드맵·챗봇 상세 설명 종료

<!-- codex-turn:01a060e1-de6c-7fa3-8a5a-b013e5d1c568 -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 문구 작성 작업이다.
- 완료 내용: 모의고사 기능 설명과 유사한 상세도로 학습 로드맵·챗봇의 무료/유료 기능, 데이터 활용, 개인화 갱신과 품질 원칙을 최종 문안으로 작성했다.
- 변경한 설명: 무료는 목표 등급·준비 기간에 따른 검증된 표준 로드맵 매칭으로, 유료는 학습·모의고사·AI 피드백을 근거로 우선순위와 다음 행동을 조정하는 개인 맞춤형 코칭으로 구분했다.
- 유지한 원칙: 학습 데이터가 부족하면 표준 경로를 유지하고, 챗봇은 검증되지 않은 학습법을 임의 생성하지 않고 사전 설계된 로드맵 범위 안에서 안내한다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약·테스트: 코드 및 공개 API·AI·S3·Redis·Billing 계약은 변경하지 않았다. 문서 기록만 변경하여 Gradle 테스트는 실행하지 않았고 `git diff --check`로 검증한다.
- 남은 위험·결정: 로드맵 분류표, 갱신 시점, 추천의 세부 단위와 무료/유료 전환 정책은 후속 제품 결정이 필요하다.
- 배포 전 확인: 문안 작업이므로 배포 사항은 없으며 발표·제출 양식의 분량만 확인하면 된다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 이번 작업에서 수정하지 않았다.
- 보안·범위: Secret과 Token을 기록하지 않았고 Git commit·push·배포를 수행하지 않았다.

## 2026-09-02 — 간결한 실제 AWS 시스템 구성도 사전 확인

<!-- codex-turn:01a060e9-4db5-7aa0-a5bd-3e20cff8f82d -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 구성도 사전 분석 작업이다.
- 사용자 요청: 첨부 예시처럼 구성요소와 핵심 흐름만 남긴 간결한 draw.io를 만들되, 개념적 백엔드 구조보다 실제 AWS 배포 구조를 중심으로 표현한다.
- 확인된 사실: Learning Core staging workflow에는 GitHub Actions OIDC, ECR과 ECS Fargate 배포가 있으며 코드·기존 문서에는 S3 Presigned 업로드, MongoDB와 Redis 연동이 존재한다.
- 문서상 계획·미확정: 기존 구성도에는 Public DNS/ALB, VPC Lattice AWS_IAM, private Billing, AI ECS, CloudWatch와 환경별 data plane이 표현돼 있으나 실제 콘솔 배포 상태와 일치하는지는 확인이 필요하다.
- 다음 입력 요청: 대상 환경·리전, AWS IAM Identity Center 계정/권한, ECS 서비스, 공개 ingress, VPC/subnet/NAT, Lattice 적용 상태, 데이터 저장소 공급자와 배포·관측 리소스를 사용자에게 확인한다.
- 보안 안내: 로그인 비밀번호, MFA 코드, Access Key, Secret과 Token은 요청하거나 기록하지 않는다. 사용자가 로그인한 AWS 콘솔은 승인된 범위에서 읽기 전용으로 확인한다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약·테스트: 구성도와 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약은 아직 변경하지 않았다. 문서 기록만 변경해 Gradle 테스트는 실행하지 않고 `git diff --check`로 검증한다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 이번 작업에서 수정하지 않았다.
- Git commit·push·AWS 변경·배포는 수행하지 않았다.

## 2026-09-02 — Production AWS 구성 확인 준비

<!-- codex-turn:01a060ee-242d-78f1-bac2-e9477c411b86 -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 구성도 확인 작업이다.
- 사용자 확정: 구성도는 서울 리전 Production만 대상으로 하며, 현재 AWS에는 Billing을 제외한 Identity·Learning Core·AI가 배포되어 있다.
- 사용자 확정: 가비아에서 관리하는 도메인이 공유 ALB로 진입하며 CloudFront·API Gateway·WAF는 사용하지 않는다. Cache는 Valkey이고 MongoDB Atlas는 클러스터로 분리돼 있다.
- 콘솔 확인: 사용자가 실제 AWS 콘솔을 읽기 전용으로 확인하도록 승인했다. 별도 브라우저에서 AWS 로그인 페이지를 열고 인증 단계는 사용자에게 넘겼다.
- 보안: 사용자가 전달한 AWS 계정 식별 정보와 federation 사용자 정보는 기록하지 않았으며, 비밀번호·MFA·Access Key·Secret·Token을 요청하거나 입력하지 않는다.
- 다음 단계: 사용자가 로그인하면 ECS service/task, shared ALB listener/target group, VPC/subnet/NAT/public IP, S3, ECR, Valkey와 CloudWatch를 읽기 전용으로 확인하고 간결한 draw.io를 작성한다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약·테스트: 애플리케이션 코드, 구성도와 공개 API·AI·S3·Redis·Billing 계약은 변경하지 않았다. 기록 문서만 변경하여 Gradle 테스트는 실행하지 않았고 `git diff --check`로 검증한다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 이번 작업에서 수정하지 않았다.
- Git commit·push·AWS 설정 변경·배포는 수행하지 않았다.

## 2026-09-02 — 실제 AWS 기반 간결 Production 구성도 작성

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 구성도 작성 작업이다.
- 결과물: `docs/architecture/tosunsaeng-production-aws-simple.drawio` 한 페이지를 새로 작성했다. 첨부 예시처럼 계층과 핵심 통신만 남기고 연결선은 별도 하위 레이어에 배치했다.
- 콘솔 확인 사실: 서울 리전에 ECS Fargate cluster 1개, Identity·Learning Core·AI service와 실행 task 각 1개, Internet-facing ALB 1개, private task subnet 2개, public ALB subnet 2개와 NAT Gateway 1개가 있다.
- 서비스 연결: ALB HTTPS 443은 Identity target `8081`과 Learning Core target `8080`으로 라우팅한다. Learning Core는 ECS Service Connect client로 private AI alias `8000`을 호출하며 AI에는 ALB와 VPC Lattice가 구성되지 않았다.
- AI 구성: AI task 하나에 `ai-api`, `ai-worker-1`부터 `ai-worker-4`와 Service Connect sidecar가 함께 실행된다.
- 데이터·배포: S3 앱 음성 bucket, 단일 node형 ElastiCache for Valkey, 서비스별 MongoDB Atlas cluster, CloudWatch Container Insights, GitHub Actions OIDC → ECR → ECS 흐름을 구성도에 반영했다.
- 제외: 미배포 Billing, 미사용 CloudFront·API Gateway·WAF·VPC Lattice와 기존 웹 POC bucket은 구성도에서 제외했다.
- 명칭 위험: 실제 AWS resource는 `staging` 접두어를 사용하고 별도 production-named cluster·ALB는 확인되지 않았다. 요청한 Production 대상 도식에는 이 사실을 주의 문구로 표시했다.
- 보안 위험: Valkey는 단일 node·Multi-AZ 비활성 상태이고 전송 중 암호화가 비활성화된 것으로 확인됐다. 이번 작업에서는 AWS 설정을 변경하지 않았다.
- 보안·개인정보: AWS 계정 ID, federation 사용자, resource ARN, IP와 세부 식별자는 결과물·기록에 넣지 않았으며 Secret과 Token을 조회하거나 기록하지 않았다.
- 변경 파일: `docs/architecture/tosunsaeng-production-aws-simple.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 테스트·결과: `xmllint --noout` 성공, 1 page·23 vertex·13 edge, mxCell 중복 ID 없음과 `git diff --check` 통과를 확인했다. draw.io renderer CLI가 없어 PNG 렌더 기반 육안 검증은 수행하지 못했다.
- 외부 계약·배포: 애플리케이션 코드와 공개 API·AI·S3·Redis·Billing 계약은 변경하지 않았고 Git commit·push·AWS 설정 변경·배포를 수행하지 않았다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 사용자 작업으로 보존했으며 수정하지 않았다.

## 2026-09-02 — 실제 AWS 기반 간결 구성도 작업 종료

<!-- codex-turn:01a060f0-1711-7692-a8c1-daf770598502 -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 구성도 작성 작업이다.
- 완료 결과: 로그인된 AWS 콘솔을 읽기 전용으로 확인하고 `docs/architecture/tosunsaeng-production-aws-simple.drawio` 한 페이지를 작성·검증했다.
- 반영 범위: 가비아 DNS, Internet-facing 공유 ALB, private subnet의 Identity·Learning Core·AI ECS Fargate, Service Connect, S3, Valkey, MongoDB Atlas, ECR와 CloudWatch를 간결하게 표현했다.
- 제외 범위: 미배포 Billing과 미사용 CloudFront·API Gateway·WAF·VPC Lattice는 제외했다.
- 확인된 위험: 실제 AWS resource 이름은 `staging`이며 별도 production-named cluster·ALB가 없다. Valkey는 단일 node·Multi-AZ 비활성·전송 암호화 비활성 상태다.
- 변경 파일: `docs/architecture/tosunsaeng-production-aws-simple.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 유지한 계약: 애플리케이션 코드와 공개 API·AI·S3·Redis·Billing 계약을 변경하지 않았다.
- 테스트·결과: `xmllint --noout`, mxCell ID 중복 검사와 `git diff --check`가 통과했다. 1 page·23 vertex·13 edge이며 모든 edge는 도형보다 아래 레이어에 있다.
- 미확인·배포 전 확인: draw.io renderer CLI가 없어 PNG 육안 검증을 하지 못했다. 발표 전 diagrams.net에서 열어 선·라벨 겹침과 `staging` 명칭 처리 방침을 확인해야 한다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 수정하지 않았다.
- 보안·범위: 계정 ID·federation 사용자·ARN·IP·Secret·Token을 결과물과 기록에 넣지 않았고 AWS 설정 변경, Git commit·push와 배포를 수행하지 않았다.

## 2026-09-02 — 1차 업데이트 목표 AWS 구성도 수정

<!-- codex-turn:01a060fb-71ba-77a2-85c7-09bd996afe92 -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 구성도 수정 작업이다.
- 사용자 요청: 현재 실배포 구성이 아니라 1차 업데이트 완료 시점을 기준으로 앱 서버를 각각 하나씩 표현하고, AI는 Worker 없이 단일 서버로 표시한다.
- 변경한 파일: `docs/architecture/tosunsaeng-production-aws-simple.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 변경한 동작·표현: Identity·Learning Core·Billing·AI를 같은 ECS Fargate 영역의 서비스별 태스크 1개로 통일했다. AI는 FastAPI 단일 서버와 `Worker 없음`으로, Billing은 Spring Boot 결제·이용권 서버로 추가했다.
- 서비스 연결: 공개 ALB는 Identity와 Learning Core에만 연결했다. Learning Core↔AI는 ECS Service Connect, Learning Core→VPC Lattice↔Billing은 SigV4/AWS_IAM 기반 비공개 통신으로 표현했다.
- 데이터·배포: Valkey에서 AI Job Queue를 제거하고 Learning 상태·Lock만 남겼다. MongoDB Atlas의 Identity·Learning·Billing 서비스별 Cluster와 ECR의 네 서비스 image를 반영했다.
- 구현 사실·목표 구분: 현재 콘솔에는 staging 접두어 리소스, 미배포 Billing과 Worker 4개 포함 AI가 있다. 구성도 본문은 1차 업데이트 이후 Billing 태스크 1개와 Worker 없는 AI 태스크 1개라는 목표 구조이며 차이를 하단 주의 문구에 명시했다.
- 유지한 외부 계약: 애플리케이션 코드와 공개 API·`BaseResponse`·AI Callback·S3·Redis·Billing 계약을 변경하지 않았다.
- 테스트·결과: `xmllint --noout`, mxCell 중복 ID 검사와 레이어 검사를 통과했다. 1 page·25 vertex·15 edge이고 모든 edge의 parent는 도형보다 앞서 선언된 `edge-layer`다. `git diff --check`도 통과했다.
- 남은 위험·배포 전 확인: draw.io renderer CLI가 없어 PNG 육안 검증은 수행하지 못했다. diagrams.net에서 파일을 열어 Lattice 라벨과 연결선 꺾임을 최종 확인해야 한다. 실제 production 배포 전에는 Billing ECS/Lattice/IAM 구성과 AI 단일 프로세스의 처리량·장애 복구 방식을 별도로 검증해야 한다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개를 포함한 사용자 파일은 수정하지 않았다. 이번 요청 범위 밖의 애플리케이션·인프라 변경은 없다.
- 다음 작업 전 확인: 1차 업데이트의 AI 단일 서버가 동기 처리만 하는지, 내부 비동기 작업을 별도 프로세스 없이 같은 서버에서 처리하는지 운영 모델을 확정한다.
- 보안·범위: 계정 ID·federation 사용자·ARN·IP·Secret·Token을 기록하지 않았고 Git commit·push, AWS 설정 변경과 배포를 수행하지 않았다.

## 2026-09-02 — 간결 구성도 AWS Architecture 아이콘 적용

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 draw.io 시각 표현 작업이다.
- 사용자 요청: 첨부 예시와 같은 AWS Architecture 아이콘으로 현재 1차 업데이트 간결 구성도의 요소를 표현한다.
- 변경한 파일: `docs/architecture/tosunsaeng-production-aws-simple.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 변경한 표현: ALB에 AWS Application Load Balancer 아이콘을, Identity·Learning Core·AI·Billing에 Fargate 아이콘을, S3와 ElastiCache for Valkey에 각 AWS 서비스 아이콘을 적용했다.
- 비AWS 요소: 토선생 앱, MongoDB Atlas와 외부 AI Provider는 AWS 관리 리소스가 아니므로 일반 도형을 유지했다. 가비아 DNS는 ALB 라벨에 연결 출처로 표시했다.
- 유지한 구조: 1 page·16 vertex·10 edge와 기존 서비스 간 연결을 유지했고 모든 edge는 도형보다 뒤의 `edge-layer`에 있다.
- 유지한 외부 계약: 애플리케이션 코드와 공개 API·`BaseResponse`·AI Callback·S3·Redis·Billing 계약을 변경하지 않았다.
- 테스트·결과: `xmllint --noout`, mxCell ID 중복과 layer 순서를 확인했고 AWS4 resource icon style이 7개 대상에 적용된 것을 정적으로 검증했다. `git diff --check`도 실행한다.
- 남은 위험·배포 전 확인: 로컬 draw.io renderer CLI가 없어 실제 아이콘 렌더와 글자 간격은 diagrams.net에서 최종 육안 확인해야 한다. 사용 환경의 AWS4 stencil 지원 버전에 따라 매우 오래된 diagrams.net에서는 아이콘 fallback 여부를 확인한다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 수정하지 않았고 애플리케이션·AWS 설정 변경도 없다.
- 다음 작업 전 확인: 가비아 DNS도 별도 일반 아이콘으로 분리할지, 현재처럼 ALB 설명에 통합해 간결함을 유지할지 발표 화면에서 판단한다.
- 보안·범위: 계정·사용자 식별자, ARN, IP, Secret과 Token을 기록하지 않았고 Git commit·push, AWS 설정 변경과 배포를 수행하지 않았다.

## 2026-09-02 — 1차 업데이트 구성도 발표용 단순화

<!-- codex-turn:01a06104-c9f5-7cb2-bbb1-21f608eb8075 -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 draw.io 단순화 작업이다.
- 사용자 요청: 1차 업데이트 목표 시스템 구성도를 발표 화면에서 더 빠르게 이해할 수 있는 구조로 단순화한다.
- 변경한 파일: `docs/architecture/tosunsaeng-production-aws-simple.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 변경한 표현: VPC, Public/Private Subnet, NAT Gateway, ECR, GitHub Actions와 운영·관측 도형을 제거했다. 앱→DNS·ALB→공개 앱 서버, 네 개 Fargate 서비스, 데이터 저장소와 외부 AI 연동만 남겼다.
- 유지한 핵심 구조: Identity·Learning Core·AI·Billing은 각각 태스크 1개이며 AI에는 Worker가 없다. Learning Core↔AI의 Service Connect, Learning Core↔Billing의 VPC Lattice·SigV4, S3 Presigned 업로드, Valkey 상태·Lock과 서비스별 MongoDB Atlas Cluster는 유지했다.
- 단순화 결과: 도형은 25개에서 16개로, 연결선은 15개에서 10개로 줄였다. 별도 VPC Lattice 도형은 제거하고 연결선 라벨로 통합했다.
- 유지한 외부 계약: 애플리케이션 코드와 공개 API·`BaseResponse`·AI Callback·S3·Redis·Billing 계약을 변경하지 않았다.
- 테스트·결과: `xmllint --noout`이 성공했고 1 page·16 vertex·10 edge, mxCell ID 중복 없음, layer 순서와 모든 edge의 `edge-layer` 소속을 확인했다. `git diff --check`도 실행한다.
- 남은 위험·배포 전 확인: draw.io renderer CLI가 없어 PNG 렌더 기반 육안 검증은 하지 못했다. 발표 전에 diagrams.net에서 연결선과 라벨 간격만 확인해야 한다. 생략된 Subnet·NAT·배포·관측 구조는 운영 상세 설명용 별도 도식이 필요할 때만 다시 제공한다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 수정하지 않았다. 이번 작업 범위 밖의 애플리케이션·AWS·인프라 변경은 없다.
- 다음 작업 전 확인: 발표 자료가 시스템의 개념적 흐름을 묻는지 AWS 네트워크 상세를 묻는지에 따라 현재 간결본과 이전 상세 수준 중 사용할 버전을 결정한다.
- 보안·범위: 계정 ID·federation 사용자·ARN·IP·Secret·Token을 기록하지 않았고 Git commit·push, AWS 설정 변경과 배포를 수행하지 않았다.

## 2026-09-02 — AWS 아이콘 적용 작업 종료 동기화

- 날짜: 2026-09-02
- Jira: 별도 Jira 이슈가 없다.
- 완료 결과: `docs/architecture/tosunsaeng-production-aws-simple.drawio`의 ALB·Fargate 4개·S3·ElastiCache에 AWS4 Architecture 아이콘을 적용하고, 앱·Atlas·외부 Provider는 비AWS 일반 도형으로 유지했다.
- 검증 결과: XML 파싱, 7개 AWS icon style, 중복 ID 없음, 16 vertex·10 edge와 후면 edge layer, `git diff --check`를 확인했다.
- 외부 계약·변경 범위: 애플리케이션 코드·공개 API·AI·S3·Redis·Billing 계약과 실제 AWS 리소스는 변경하지 않았다. 기존 미추적 draw.io 파일 2개도 수정하지 않았다.
- 잔여 확인: diagrams.net에서 실제 아이콘 렌더와 글자 간격을 육안 확인한다.
- 보안·Git: Secret·Token·계정 식별 정보를 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-02 — AWS 아이콘 구성도 종료 훅 동기화

<!-- codex-turn:01a06108-827d-7d02-98d6-9adbf949aab1 -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 구성도 작업이다.
- 완료 결과: 1차 업데이트 간결 구성도의 ALB·Fargate 4개·S3·ElastiCache에 AWS4 Architecture 아이콘을 적용한 상태를 종료 기록에 동기화했다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. 구성도 파일은 이번 종료 동기화에서 추가 수정하지 않았다.
- 유지한 동작·계약: 16개 도형·10개 연결선, 후면 edge layer와 기존 서비스 연결을 유지했다. 애플리케이션 코드와 공개 API·AI Callback·S3·Redis·Billing 계약을 변경하지 않았다.
- 검증: XML 파싱, AWS icon style 7개, 중복 ID와 layer 순서, `git diff --check`를 다시 확인한다. Gradle 대상 코드 변경이 없어 `./gradlew clean test`는 실행하지 않는다.
- 위험·배포 전 확인: diagrams.net에서 실제 아이콘 렌더와 글자 간격을 육안 확인해야 한다. 실제 AWS 설정·배포 변경은 수행하지 않았다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 수정하지 않았고 이번 종료 동기화에 예상 밖 범위는 없다.
- 다음 작업 전 확인: 발표 화면에서 가비아 DNS를 ALB 설명에 통합한 현재 표현을 유지할지 확인한다.
- 보안·Git: Secret·Token·계정 식별 정보를 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-02 — 토선생 이미지와 라벨 연결 방식 적용

<!-- codex-turn:01a0610d-9892-7ca2-86d4-82778ea64bb3 -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 draw.io 시각 개선 작업이다.
- 사용자 요청: 토선생은 제공한 토끼 PNG를 사용하고 다른 구성요소는 역할에 맞는 아이콘을 사용하며, 화살표 때문에 글자가 가려지지 않도록 화살표를 글자에 연결한다.
- 변경한 파일: `docs/architecture/tosunsaeng-production-aws-simple.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 변경한 표현: 제공된 2048×2048 PNG를 256×256로 축소한 68,167 byte PNG로 만들어 draw.io의 토선생 앱 이미지에 base64 내장했다. 외부 Desktop 파일 경로에 의존하지 않는다.
- 아이콘 체계: ALB·Fargate·S3·ElastiCache는 공식 AWS4 아이콘을 유지했다. MongoDB Atlas는 데이터베이스 실린더, 외부 AI Provider는 AWS 경계 밖의 별도 서비스 도형으로 유지해 공급자 소속을 혼동하지 않게 했다.
- 화살표 개선: 앱·ALB·Identity·Learning Core·AI·Billing·S3·Valkey의 아이콘과 라벨을 별도 도형으로 분리했다. 주요 8개 연결선의 source와 target을 라벨 도형으로 바꾸고 라벨에는 불투명한 흰 배경과 테두리를 적용해 선이 글자를 가리지 않게 했다.
- 유지한 구조: 1 page·10 edge와 후면 `edge-layer`, 앱→ALB→Identity/Learning, Learning↔AI·Billing·S3·Valkey, 앱→S3, ECS→Atlas 흐름을 유지했다. 라벨 분리로 vertex는 24개가 됐다.
- 유지한 외부 계약: 애플리케이션 코드와 공개 API·`BaseResponse`·AI Callback·S3·Redis·Billing 계약을 변경하지 않았다.
- 테스트·결과: `xmllint --noout`, 내장 base64의 PNG signature와 68,167 byte 복호화, 8개 label-to-label edge, ID 중복 없음, 모든 edge의 후면 layer 소속과 `git diff --check`를 확인했다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.
- 남은 위험·배포 전 확인: 로컬 draw.io renderer CLI가 없어 diagrams.net에서 토끼 이미지의 실제 크기·라벨 간격과 연결선 꺾임을 최종 육안 확인해야 한다. 제공 이미지 자체에 포함된 표식도 그대로 내장되어 있다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 수정하지 않았고 작업 범위 밖의 코드·AWS 설정 변경은 없다.
- 다음 작업 전 확인: 발표 화면에서 토끼 이미지의 흰 여백과 원본에 포함된 표식을 그대로 사용할지 결정한다. 필요하면 사용자가 권리를 보유한 투명 배경 원본으로 교체한다.
- 보안·Git: Secret·Token·계정·사용자 식별 정보를 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-03 — Learning Core phone 재가입 시험 승계 인계안 검토

<!-- codex-turn:01a06571-ae0e-7530-bdb6-1d7a1034fb5e -->

- 날짜: 2026-09-03
- 현재 브랜치: `develop`
- Jira: Learning Core 전용 이슈는 아직 없다. 상류 Billing owner rebind 구현은 `TMI-120`이다.
- 사용자 요청: 첨부된 `Learning Core phone 재가입 시험 승계 구현 요청`을 현재 Learning Core 코드와 Billing 계약에 대조해 수정 방향을 검토한다.
- 검토 결론: 과거 `ExamSession`·답안·결과를 이전하지 않고 Billing이 명시적으로 승인한 기존 `AttemptGroup`·`mockExamId`에 새 target `examId`를 연결하는 새 방향은 타당하다. continuation 204 시 기존 INITIAL, 200 시 `PHONE_REJOIN` REPLACEMENT, 세 continuation field exact echo, 응답·status strict 검증과 SigV4/Lattice 경계도 Billing 계약과 일치한다.
- 필수 보완 1: phone 재가입은 Billing-only `TrialOwnerRebindApproved` lifecycle이며 ACTIVE GUEST→MEMBER의 `UserMerged` consumer와 다르다. 본 작업이 기존 `USER_MERGED_CONSUMER_IMPLEMENTATION_PLAN.md`의 Guest 학습 이력 migration을 대체하거나 폐기하지 않는다고 문서에 명시해야 한다.
- 필수 보완 2: continuation 조회 조건을 단순한 `INITIAL 예상`이 아니라 `같은 operation의 최초 PREPARED 생성이고 target userId의 ExamSession이 전혀 없음`으로 고정해야 한다. 기존 operation·durable Session replay에서는 continuation을 재조회하지 않아야 한다.
- 필수 보완 3: continuation 결과와 reserve exact payload은 존재하지 않는 별도 outbox가 아니라 `ExamCreationOperation`에 reserve 전 영속화한다. `continuationReason`, `continuationId`, `expectedAttemptGroupId`와 Billing이 반환한 `mockExamId`를 같은 operation의 불변 snapshot으로 두어 응답 유실·동시 요청에서도 같은 key·session·payload를 재사용해야 한다.
- 필수 보완 4: 현재 `SigV4BillingReservationClient` 공통 success decoder는 empty body를 계약 오류로 처리하므로 continuation 204 전용 분기가 필요하다. phone caller에는 `app.billing.phone-continuation-enabled=false` 성격의 별도 flag·startup validation을 두고 creation saga OFF 시 기존 흐름을 유지해야 한다.
- 필수 보완 5: reserve/status의 optional `continuationReason`·`continuationId`를 operation snapshot과 비교하고, phone 응답에서 누락·불일치나 예상 밖 kind를 거절해야 한다. 계약 불일치 시 응답의 untrusted `reservationId`를 바로 cancel하지 말고 `userId+operationId` status로 authoritative reservation을 재조회한 뒤 정합하는 경우만 cancel하도록 보상 규칙을 구체화해야 한다.
- 필수 보완 6: 현재 시험 생성용 `SigV4BillingReservationClient`에는 W3C context injection이 없으므로 첨부 문서의 `traceparent 전파 유지` 표현은 실제 구현과 다르다. 이번 작업에서 허용된 trace metadata만 unsigned request에 inject한 뒤 SigV4를 마지막 논리적 변경 단계에서 수행하고, 서명 후 header·URI·body를 변경하지 않는 component test를 추가한다고 명시해야 한다.
- 추가 테스트 권고: continuation 조회 응답 유실·시간 차로 204→200이 변해도 기존 operation snapshot이 바뀌지 않음, 같은 user의 다른 idempotency key 동시 요청, 204 empty-body strict handling, malformed 200, status optional field 누락·불일치, contract mismatch status-first cancel, flag OFF 회귀를 검증한다.
- 변경 파일: 검토 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·테스트·설정·Billing 저장소 파일은 수정하지 않았다.
- 유지한 외부 계약: 공개 API·`BaseResponse`·AI `user_id=examId`·Callback·S3·Redis·`retryCount`는 변경하지 않았다.
- 테스트·검증: 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. 첨부 문서, Learning Core saga·operation·SigV4 client와 Billing ADR·계약·phone continuation 구현을 읽기 전용으로 대조했고 문서 diff는 `git diff --check`로 검증한다.
- 남은 위험·배포 전 확인: Billing 참조 문서와 phone continuation 구현이 현재 Billing `develop` 작업 트리의 미커밋 변경이므로 Billing PR 병합 후 exact wire·error·IAM route를 다시 고정해야 한다. reader-first→Billing·Lattice→caller→Identity producer 순서와 staging E2E 전에 활성화하지 않는다.
- 예상 밖 변경: 기존 사용자 소유 draw.io 미추적 파일 3개와 기존 기록 문서 변경을 보존했으며 수정·삭제하지 않았다.
- 다음 작업 전 확인: 본 검토 보완을 반영한 Learning Core 전용 계획서와 신규 Jira를 먼저 확정한 뒤 구현한다.
- Git·배포: commit·push·PR·merge·배포를 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-03 — Billing phone continuation 병합 확인과 Learning Core 계획서 작성

<!-- codex-turn:01a06579-062f-7e43-81ad-ff133940d876 -->

- 날짜: 2026-09-03
- 현재 브랜치: `develop`
- Jira: 상류 Billing 구현은 `TMI-120`이며 Learning Core 전용 Jira는 아직 없다.
- 병합 확인: Billing 저장소의 `HEAD`, 로컬 `develop`, `origin/develop`이 모두 PR #8 merge commit `7138810`을 가리키며 작업 트리가 clean임을 확인했다. 실제 phone continuation 구현 commit은 `b61ebb9`이다.
- 상류 구현 확인: Billing에 `POST /internal/v1/reservations/continuations/phone`, `PHONE_REJOIN` discovery, reserve의 세 continuation field, reserve/status optional response field, payload hash와 security route 및 관련 테스트가 병합돼 있다.
- 작성 파일: `docs/codex/PHONE_REJOIN_CONTINUATION_IMPLEMENTATION_PLAN.md`를 신규 작성하고 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 갱신했다.
- 계획 핵심: target userId의 Session이 0건이고 같은 operation의 최초 준비일 때만 discovery를 실행한다. 204는 기존 INITIAL, 200은 Billing이 반환한 기존 AttemptGroup·mockExamId를 사용하는 새 target `examId`의 PHONE_REJOIN REPLACEMENT로 처리한다.
- 멱등성 계획: `continuationReason`, `continuationId`, expected group/mock을 reserve 전에 `ExamCreationOperation`의 불변 snapshot으로 저장한다. duplicate operation insert loser는 winner snapshot을 reload하며 PREPARED 이후 discovery·sessionId·mock 선택을 다시 하지 않는다.
- client·saga 계획: continuation의 정확한 204 empty-body 처리, strict 200 decoder, normal 3-field와 phone 6-field request 분리, reserve/status optional context 검증, reserve 응답 유실 status 복구와 contract mismatch의 untrusted reservationId 직접 cancel 금지를 포함했다.
- 데이터 경계: phone 재가입은 Billing-only `TrialOwnerRebindApproved` lifecycle이며 Guest `UserMerged` consumer를 대체하지 않는다. source Session owner, 답안, 결과, Summary, grading Job과 audio를 이전하거나 복사하지 않고 새 target Session은 cycleNumber 1로 시작한다.
- 보안·관측 계획: `app.billing.phone-continuation-enabled=false` 기본값과 creation saga 선행 조건, W3C 새 CLIENT span의 traceparent를 unsigned request에 넣은 뒤 SigV4를 마지막에 수행하는 순서, baggage·식별자·payload·credential 비로깅을 확정했다.
- 유지한 외부 계약: 기존 `POST /api/v1/exams`, Request Body 없음, 성공 DTO·`BaseResponse`, 실제 userId 비노출, `retryCount`, S3·Redis·Polling과 AI `user_id=examId` 계약을 변경하지 않았다.
- 테스트·검증: 문서 작업이므로 Gradle 테스트는 실행하지 않았다. Billing merge ref와 최종 계약·구현을 읽기 전용으로 확인했고 계획서의 wire·상태·오류·rollout 항목을 현재 Learning Core saga/client/operation과 대조했다. 문서 형식과 whitespace는 `git diff --check`로 검증한다.
- 남은 위험·배포 전 확인: Learning Core 전용 Jira를 생성하고 해당 범위의 `AGENTS.md` 허용을 추가해야 한다. 구현·staging E2E 전에는 flag를 켜지 않으며 Billing `7138810` 포함 버전의 실제 환경 배포와 Lattice exact route를 별도로 확인해야 한다.
- 예상 밖 변경: 기존 사용자 소유 미추적 draw.io 파일 3개와 이번 작업 이전부터 존재한 CURRENT_STATE·WORKLOG 변경을 보존했으며 수정·삭제하지 않았다. 애플리케이션·설정·테스트와 Billing 저장소는 변경하지 않았다.
- 다음 작업 전 확인: 계획서를 사용자 승인한 뒤 Learning Core 전용 Jira를 만들고 `AGENTS.md` 범위를 확정한 다음 구현한다.
- Git·배포: commit·push·PR·merge·배포, Jira 생성·상태 변경과 AWS 리소스 변경을 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-03 — Jira TMI-122 생성

<!-- codex-turn:01a06589-235a-7302-bbf5-8f91dbb9c563 -->

- 날짜: 2026-09-03
- 현재 브랜치: `develop`
- Jira: `TMI-122` `[Learning Core] phone 재가입 시험 continuation 연동`
- 생성 결과: TMI 프로젝트에 이슈 유형 `작업`, 상태 `해야 할 일`로 생성했고 assignee는 지정하지 않았다.
- 중복 확인: 프로젝트 내 phone·재가입·continuation 관련 기존 이슈를 조회했으며 동일한 Learning Core 후속 이슈는 없었다.
- Jira 본문: Billing `TMI-120`/PR #8을 배경으로 target Session 0건 discovery, 204 INITIAL, 200 PHONE_REJOIN REPLACEMENT, operation 불변 snapshot, strict client·reserve·status, 응답 유실 복구, status-first cancel, 데이터 격리, trace·SigV4·보안, 완료 조건, 필수 테스트와 rollout을 기록했다.
- 이슈 연결: link type `Blocks`를 사용해 `TMI-122 is blocked by TMI-120` 관계를 만들고 `TMI-122` 조회에서 outward issue `TMI-120` 연결을 확인했다.
- 상류 상태 주의: Billing 저장소에는 PR #8 merge commit `7138810`이 반영돼 있지만 Jira `TMI-120` 상태는 현재 `해야 할 일`이다. 사용자 요청 없이 `TMI-120` 상태를 변경하지 않았다.
- 계획서 갱신: `docs/codex/PHONE_REJOIN_CONTINUATION_IMPLEMENTATION_PLAN.md`의 Learning Core Jira를 `TMI-122`로 기록하고 Phase 0의 Jira 생성·상류 연결을 완료 표시했다. `AGENTS.md` 허용 범위 추가는 구현 전 남은 단계로 유지했다.
- 변경 파일: `docs/codex/PHONE_REJOIN_CONTINUATION_IMPLEMENTATION_PLAN.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 유지한 외부 계약: 애플리케이션 코드와 공개 API·`BaseResponse`·AI `user_id=examId`·Callback·S3·Redis·`retryCount`를 변경하지 않았다.
- 테스트·검증: 코드 변경이 없어 Gradle 테스트를 실행하지 않았다. 생성된 Jira의 key·summary·type·status·description과 issue link를 재조회했고 문서는 `git diff --check`와 marker 1회 검사로 검증한다.
- 남은 위험·배포 전 확인: 구현 전에 `TMI-122` 범위의 `AGENTS.md` 허용을 추가해야 한다. Billing `7138810` 포함 버전의 실제 환경 배포, Lattice exact route와 staging E2E 전에는 phone flag를 활성화하지 않는다.
- 예상 밖 변경: 기존 사용자 소유 미추적 draw.io 파일 3개와 이번 작업 이전부터 존재한 기록 문서 변경을 보존했으며 수정·삭제하지 않았다. 애플리케이션·설정·테스트 파일은 변경하지 않았다.
- 다음 작업 전 확인: 사용자가 구현 진행을 승인하면 먼저 `AGENTS.md`에 `TMI-122` 제한적 허용 범위를 반영한 뒤 계획서 순서로 구현한다.
- Git·배포: commit·push·PR·merge·배포와 AWS 리소스 변경을 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-03 — TMI-122 AGENTS 범위 의미 설명 및 구현 일시 중단

<!-- codex-turn:01a0658e-fd29-76d3-93ff-ca1f8adc4b53 -->

- 날짜: 2026-09-03
- 현재 브랜치: `develop`
- Jira: `TMI-122` `[Learning Core] phone 재가입 시험 continuation 연동`
- 사용자 질문: 기존 `AGENTS.md`가 phone 재가입 owner continuation을 허용하지 않는다는 표현의 의미를 설명한다.
- 설명 결과: 이는 제품 기능 자체를 금지한다는 뜻이 아니다. 기존 TMI-116 예외가 최초 Billing Reservation saga의 reserve·confirm·cancel·status와 당시 확정 범위에만 적용되고 다른 Jira에 자동 적용되지 않는다는 저장소 작업 거버넌스 의미다.
- 기존 경계: TMI-116은 UserMerged·owner rebind와 후속 확장을 제외했고 AttemptGroup 영구 허용도 UserMerged·owner rebind 및 Billing saga 전체 reconciliation을 자동 허용하지 않는다. 따라서 Codex가 새 계약을 별도 승인 없이 구현하지 못하도록 막혀 있었다.
- 현재 승인: 사용자가 phone continuation 계획을 승인하고 Jira `TMI-122` 생성과 구현을 명시적으로 요청했으므로 직전 중단된 구현 턴에서 `AGENTS.md`에 TMI-122 전용 제한적 예외를 추가했다.
- TMI-122 예외가 허용하는 것: Billing continuation discovery, operation 내부 snapshot, phone 6-field reserve, reserve/status strict 검증·복구, 새 target Session의 기존 AttemptGroup 연결, default-off flag, traceparent와 SigV4 최종 서명 및 관련 테스트다.
- 계속 금지하는 것: source Session·답안·결과 이전, TrialOwnerRebindApproved consumer, Guest UserMerged 변경, Billing 저장소 수정, 공개 API·AI·S3·Redis 계약 변경과 실제 AWS 리소스 변경이다.
- 구현 상태: 사용자의 `잠깐` 요청에 따라 애플리케이션 구현을 진행하지 않았다. 현재 이번 구현 시도에서 바뀐 것은 `AGENTS.md`의 TMI-122 범위뿐이다.
- 변경 파일: `AGENTS.md`, 기록용 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 이전 작업에서 생성된 계획서와 사용자 소유 draw.io 파일은 보존했다.
- 유지한 외부 계약: 공개 API·`BaseResponse`·AI `user_id=examId`·Callback·S3·Redis·`retryCount`와 Billing wire를 변경하지 않았다.
- 테스트·검증: 애플리케이션 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. `AGENTS.md` diff와 TMI-116·TMI-122·AttemptGroup 범위를 대조하고 문서는 `git diff --check`와 marker 1회 검사로 검증한다.
- 남은 위험·배포 전 확인: 구현은 아직 시작하지 않았으며 사용자가 계속 진행을 확인하면 TMI-122 계획서 순서로 재개한다. feature flag 활성화·AWS·배포 작업은 범위 밖이다.
- 예상 밖 변경: 기존 사용자 소유 미추적 draw.io 파일 3개와 이전 CURRENT_STATE·WORKLOG·계획서 변경을 수정·삭제하지 않았다.
- Git·배포: commit·push·PR·merge·배포, Jira 상태 변경과 AWS 리소스 변경을 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-03 — phone continuation 영구 허용 범위 검토

<!-- codex-turn:01a06590-a78f-7883-a3b3-acc628e25e24 -->

- 날짜: 2026-09-03
- 현재 브랜치: `develop`
- Jira: `TMI-122` `[Learning Core] phone 재가입 시험 continuation 연동`
- 사용자 질문: TMI-122를 Jira 단건 예외로 두지 않고 `AGENTS.md`에서 일반적으로 허용하는 방식이 나은지 검토한다.
- 결론: 모든 Billing·owner rebind 변경을 포괄적으로 허용하는 것은 권장하지 않는다. 결제 권리, 과거 데이터 소유권, 서버 간 인증과 보상 로직까지 별도 승인 없이 확대될 수 있기 때문이다.
- 권장안: phone 재가입 시험 continuation이라는 확정 capability 자체와 동일 경계 안의 후속 버그 수정·테스트·운영 안정화를 영구 허용한다. TMI-122는 최초 구현 이력과 기준 계획서로 기록하되 허용 효력은 특정 Jira에 종속시키지 않는다.
- 유지할 허용 범위: Billing continuation discovery, operation snapshot, phone 6-field reserve, reserve/status 검증·복구, 새 target Session 연결, default-off flag, W3C trace와 SigV4 최종 서명이다.
- 유지할 금지 범위: 전체 Billing 기능, 새로운 entitlement·결제 정책, source 시험 데이터 이전, TrialOwnerRebindApproved/UserMerged consumer, Billing 저장소 수정, 공개 API·AI·S3·Redis 계약 변경과 AWS 리소스 직접 변경이다.
- 문서 권고: 현재 `## TMI-122 명시적 예외`를 `# Phone 재가입 시험 continuation 구현 허용 규칙`으로 바꾸고, TMI-122와 계획서를 최초 기준으로 연결하며 후속 작업도 동일 경계 안에서만 허용한다고 명시한다.
- 변경 파일: 검토 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. `AGENTS.md`의 직전 TMI-122 예외와 애플리케이션 코드는 이번 작업에서 추가 수정하지 않았다.
- 테스트·검증: 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. 기존 TMI-116·TMI-122·AttemptGroup 허용/금지 경계를 문서상 대조하고 `git diff --check`와 marker 1회 검사를 수행한다.
- 외부 계약·보안: 공개 API·`BaseResponse`·AI `user_id=examId`·S3·Redis·`retryCount`와 Billing wire를 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 남은 확인: 사용자가 권장안을 승인하면 `AGENTS.md`의 TMI-122 단건 예외를 capability 기반 영구 허용 절로 교체한 뒤 구현을 재개한다.
- 예상 밖 변경: 기존 사용자 소유 미추적 draw.io 파일 3개와 이전 계획·기록 변경을 보존했고 수정·삭제하지 않았다.
- Git·배포: commit·push·PR·merge·배포, Jira·AWS 상태 변경을 수행하지 않았다.

## 2026-09-03 — TMI-122 phone 재가입 시험 continuation 구현

<!-- codex-turn:01a06592-36fc-7041-a4a9-0471c1762732 -->

- 날짜: 2026-09-03
- 현재 브랜치: `feat/TMI-122-phone-rejoin-continuation`
- Jira: `TMI-122` `[Learning Core] phone 재가입 시험 continuation 연동`
- 범위 규칙: `AGENTS.md`의 TMI-122 단건 예외를 `Phone 재가입 시험 continuation 구현 허용 규칙`으로 교체했다. 확정 capability와 동일 경계의 후속 버그 수정·테스트·운영 안정화는 영구 허용하지만 Billing 전체, source 데이터 이전, TrialOwnerRebindApproved/UserMerged consumer와 Billing 저장소 수정은 계속 제외한다.
- discovery와 준비: `phone-continuation-enabled`가 켜지고 target user 소유 ExamSession이 0건일 때만 Billing `POST /internal/v1/reservations/continuations/phone`을 호출한다. body 없는 204는 기존 INITIAL 흐름, strict 200은 Billing 기존 group/mock을 쓰는 cycle 1 PHONE_REJOIN REPLACEMENT 준비로 연결한다.
- operation 멱등성: `ExamCreationOperation`에 `continuationReason`, `continuationId`, expected group/mock snapshot을 reserve 전에 저장한다. 일반 INITIAL·local REPLACEMENT·PHONE_REJOIN 조합을 strict 검증하고 같은 key insert 경쟁에서는 기존 unique winner reload 흐름을 유지한다.
- Billing wire: 일반 reserve는 기존 3필드, phone reserve는 continuation 세 필드를 더한 정확한 6필드로 분리했다. reserve·status response의 optional reason/id를 reader-first로 해석하고 200/204 discovery, unknown·duplicate·trailing·coercion, response size와 redirect 금지 규칙을 기존 strict mapper·HTTP 경계에 연결했다.
- 복구와 보상: PREPARED reserve의 timeout·일시 실패·processing·contract error는 status를 먼저 조회한다. exact RESERVED만 operation을 전진시키고, 계약 불일치는 응답의 reservationId를 사용하지 않은 채 authoritative status가 증명한 ID로만 CANCEL_PENDING을 저장한다. phone stale context 409는 terminal failure로 수렴한다.
- Session 격리: 새 target Session은 새 examId, target userId, cycle 1, Billing 기존 AttemptGroup·mockExamId와 REPLACEMENT kind를 저장한다. source Session owner, 답안, 결과, Summary, grading Job과 audio는 조회·복사·수정하지 않는다.
- 보안·관측: Billing 호출마다 현재 server span의 child CLIENT span을 만들고 그 span의 `traceparent`만 unsigned request에 넣은 뒤 SigV4를 마지막 논리적 변경으로 수행한다. baggage를 전파하지 않고 user/session/group/continuation ID와 payload·credential 없는 고정 outcome 로그와 저카디널리티 counter/timer를 추가했다. 401/403은 body code보다 우선해 `AUTH_FAILURE`로 분류한다.
- 설정: `BILLING_PHONE_CONTINUATION_ENABLED` 기본값은 false이며 creation saga가 꺼진 상태에서 phone flag만 켜면 startup validation이 실패한다. local/test도 false를 유지한다.
- 변경 파일: `AGENTS.md`, phone 계획서·Billing saga 계획서, Billing client·properties·configuration·validator, `BillingExamCreationSaga`, `ExamCreationOperation`, `ExamSessionManager`, `ExamSessionRepository`, 신규 `BillingContinuationReason`, application 설정과 관련 테스트, CURRENT_STATE·WORKLOG.
- 유지한 외부 계약: `POST /api/v1/exams` URL·Method·body 없는 요청·성공 DTO·BaseResponse·optional Idempotency-Key 규칙, 실제 userId 비노출, 시험 retryCount, S3·Redis·Polling과 Python AI `user_id=examId` 및 Callback 계약은 변경하지 않았다.
- 테스트·검증: phone 204/200 discovery, exact 6필드 reserve, optional echo, target history gate, operation snapshot, reserve 응답 유실 status 복구, status continuation 누락 거절, untrusted reservationId 미사용, target Session group/mock 연결, flag 조합, client span traceparent의 SigV4 포함을 추가·검증했다. `./gradlew clean test` 전체 457개가 성공했고 `git diff --check`도 통과했다.
- 남은 위험·배포 전 확인: Billing `7138810` 포함 이미지 배포, Lattice exact phone route IAM, Mongo index·Transaction 환경과 204/200/응답 유실/stale/trace staging E2E 전에는 phone flag를 켜지 않는다. reader-first 배포 후 caller flag를 canary로 활성화해야 한다.
- 예상 밖 변경: 기존 사용자 소유 미추적 draw.io 파일 3개를 수정·삭제하지 않았다. 이번 기능 밖의 애플리케이션 리팩터링과 Billing 저장소 변경은 없다.
- Git·배포: commit·push·PR·merge·배포, Jira 상태 변경과 AWS 리소스 변경을 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-01 — 시스템 구성도 표현 도구 선택

<!-- codex-turn:01a05bcd-56e8-76c0-9114-249362fbacc0 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 시스템 구성도 도구 선택은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 5줄 결론: 토선생처럼 서비스·외부 시스템·데이터 저장소·신뢰 경계를 한눈에 설명하고 직접 편집해야 하는 시스템 구성도는 draw.io를 원본으로 사용한다. Mermaid는 sequence·state·간단한 코드 인접 흐름에 사용하며, 생성 이미지는 발표 표지나 개념적 삽화에만 사용하고 정확한 시스템 원본으로 사용하지 않는다.
- 결정 근거: draw.io는 자유 배치, grouping, 색상 범례, 서비스 경계와 수동 편집에 유리하다. Mermaid는 Git diff와 Markdown 동기화는 좋지만 큰 시스템 지도에서 배치 제어가 약하다. 생성 이미지는 편집·버전 관리·정확한 label 유지가 어렵다.
- 권장 운영: 같은 도식을 세 형식으로 중복 유지하지 않고, 통합 시스템 구성도는 draw.io 하나를 source of truth로 두며 별개의 sequence/state 도식만 Mermaid로 작성한다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. draw.io, Mermaid 또는 이미지 파일은 새로 만들지 않았다.
- 유지한 계약: 애플리케이션 코드, 공개 API, `BaseResponse`, AI·S3·Redis·Billing 계약을 변경하지 않았다.
- 테스트·검증: 설명과 기록 문서만 변경했으므로 Gradle 테스트는 실행하지 않고 `git diff --check`로 검증한다.
- 위험·배포 전 확인: draw.io와 Mermaid로 동일한 관계를 중복 작성하면 drift가 생길 수 있으므로 도식별 원본을 하나만 정해야 한다. 배포 사항은 없고 이번 작업과 무관한 기존 dirty worktree는 수정하지 않았다.
- Git commit·push는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 토선생 앱 시스템 구성도 draw.io 작성

<!-- codex-turn:01a05bd6-9b87-7a42-bf18-10b2e61d38b8 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 시스템 구성도 작성은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 5줄 결론: 웹 POC를 제외한 토선생 앱 시스템을 한 페이지 draw.io로 작성했다. 앱, Identity, Learning Core, Billing, Python AI, 서비스별 MongoDB, Redis와 S3를 배치했다. 공개 HTTPS, JWKS, SigV4·VPC Lattice, Presigned PUT, AI 요청·Callback을 연결했다. 구현·조건부·외부 연동과 데이터 소유권을 색상·선 스타일로 구분했다. 기존 멘토링용 8페이지 draw.io는 수정하지 않았다.
- 변경 파일: `docs/architecture/tosunsaeng-app-system-configuration.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 구현 내용: 앱이 Identity와 Learning Core만 직접 호출하고 Billing은 내부 전용임을 표현했다. `examId→userId`, `AI user_id=examId`, 실제 userId 비전송, 서비스별 DB 소유권과 Billing rollout gate를 도식에 명시했다.
- 유지한 계약: 애플리케이션 코드, 공개 API, `BaseResponse`, 사용자 식별, AI request/Callback, S3·Redis와 Billing wire 계약을 변경하지 않았다.
- 테스트·검증: `xmllint --noout` 성공, diagram 1개, vertex 29개, edge 14개, 누락 source/target 0개를 확인했다. 애플리케이션 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 최종 `git diff --check`를 수행한다.
- 위험·미확인: draw.io CLI가 없어 diagrams.net 실제 렌더링 화면 export는 수행하지 못했다. 사용자가 diagrams.net에서 열어 label 겹침과 선 배치를 확인한 뒤 필요하면 위치를 미세 조정할 수 있다.
- 배포 전 확인: 문서 산출물이므로 애플리케이션 배포 사항은 없다.
- 예상 밖 diff: 이번 작업은 신규 draw.io와 두 기록 문서만 변경했다. 그 밖의 애플리케이션·설정 파일 변경은 없다.
- Git commit·push는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 시스템 구성도 연결선 라우팅 개선

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 draw.io 가독성 개선은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 사용자 PNG 확인 결과 기본 자동 라우팅이 앱→Learning Core, Identity→Billing과 Learning Core↔Python AI 연결선을 서비스 내부 도형 위로 통과시키고 긴 label을 겹치게 하는 문제를 확인했다.
- `tosunsaeng-app-system-configuration.drawio`의 주요 연결선 9개에 entry/exit 지점과 고정 경유점을 지정했다. 상단 공개·eligibility 통로, 서비스 하단 Billing 통로, 데이터 저장소 사이 AI 요청 통로, 우측 외곽 Callback 통로로 분리했다.
- 긴 edge label을 짧게 줄이고 offset을 지정했으며 앱→Identity와 인증 Provider→Identity는 서비스 사이의 짧은 전용 통로만 사용하게 했다.
- XML 검증, edge 14개, 수동 routing edge 9개, 누락 source/target 0개와 `git diff --check` 성공을 확인했다. 애플리케이션 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.
- 공개 API·`BaseResponse`·사용자 식별·AI·S3·Redis·Billing 계약과 시스템 의미는 변경하지 않았다. diagrams.net 최종 화면에서 label 미세 위치는 사용자가 확인할 수 있다.
- 이번 작업은 draw.io와 기록 문서만 변경했으며 애플리케이션·설정 파일의 예상 밖 변경은 없다. 배포 사항과 Git commit·push는 없고 Secret·Token을 기록하지 않았다.

## 2026-09-01 — 시스템 구성도 연결선 개선 종료 기록

<!-- codex-turn:01a05bdb-4bf7-7e83-b7ce-45d895c810c4 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 draw.io 연결선 개선은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 변경 파일은 `docs/architecture/tosunsaeng-app-system-configuration.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`다.
- 주요 연결선을 상단·서비스 하단·데이터 저장소 사이·우측 외곽 통로로 분리하고 label을 축약·offset 처리했다. 시스템 구성의 의미와 외부 계약은 변경하지 않았다.
- `xmllint --noout`, edge 14개·수동 route 9개·누락 source/target 0개와 `git diff --check` 성공을 확인했다. 문서 변경이라 Gradle 테스트는 실행하지 않았다.
- diagrams.net 실제 화면에서의 최종 미세 배치는 사용자가 다시 연 파일로 확인할 수 있다. 배포·Git commit·push는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 시스템 구성도의 백엔드 편향 검토

<!-- codex-turn:01a05be3-191e-7291-8986-3060546fa60c -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 도식 관점 검토는 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 5줄 결론: 현재 draw.io는 정확하지만 일반적인 전체 시스템 구성도보다 앱 백엔드 기술 구성도에 가깝다. 백엔드 개발자·멘토 대상에는 적절하다. 제품·비개발 이해관계자 대상에는 내부 aggregate·Job·원장 명칭이 과하다. 기존 그림은 백엔드 상세로 보존하는 것이 좋다. 사용자→앱 기능→서비스→외부 연동만 보이는 상위 시스템 구성도를 첫 페이지로 추가하는 방식을 권고한다.
- 반드시 읽을 내용: 하나의 도식으로 모든 독자를 만족시키기보다 1페이지 제품·시스템 관점과 2페이지 백엔드 기술 관점을 분리한다. 상위 페이지에는 로그인, 시험 시작, 녹음, AI 채점, 결과 확인의 사용자 흐름과 Identity·Learning Core·Billing의 역할만 표시한다.
- 사용자 결정 사항: 현재 파일의 기존 페이지를 `백엔드 기술 구성도`로 이름 변경하고, 별도 첫 페이지 `전체 시스템 구성도`를 추가할지 결정하면 된다. 권고는 두 페이지 유지다.
- 위험·미확인: 상위 도식에 실제로 존재하지 않는 프론트 인프라, API Gateway, CDN 등을 추정해 넣지 않아야 한다. 대상 독자와 발표 목적이 확정되면 정보 밀도를 조정해야 한다.
- 변경·검증: 이번 턴은 관점 분석과 기록 문서 갱신만 수행했고 draw.io·애플리케이션·설정·외부 계약을 변경하지 않았다. 코드 변경이 없어 Gradle 테스트를 실행하지 않고 `git diff --check`로 검증한다.
- 배포·예상 밖 diff: 배포 사항은 없으며 기존 draw.io와 기록 문서 외 예상 밖 파일은 수정하지 않았다. Git commit·push를 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 전체 시스템·AWS 구성도 첫 페이지 추가

<!-- codex-turn:01a05be6-7b2e-76a2-a981-17a5f2fe903e -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 AWS 구성도 개선은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 5줄 결론: `tosunsaeng-app-system-configuration.drawio`를 2페이지로 확장했다. 첫 페이지는 사용자·앱과 AWS runtime/deployment 구조를 함께 보여준다. 둘째 페이지는 기존 백엔드 기술 구성도를 보존했다. 확인된 현재 리소스와 승인된 목표 리소스를 선·색으로 구분했다. 실제 inventory가 없는 AWS 경로는 추정하지 않고 미확인으로 표시했다.
- 반드시 읽을 내용: 현재 Identity·Learning Core는 ALB와 `tosunsaeng-staging-cluster` ECS Service를 사용하고 GitHub Actions OIDC→ECR→ECS Task Definition revision 배포를 수행한다. Billing/VPC Lattice는 승인 목표지만 아직 미배포이며 Billing은 private Fargate·no ALB/public IP·Lattice AWS_IAM 경계다.
- 근거: Learning Core·Identity `.github/workflows/deploy-staging.yml`, Billing `docs/adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`의 현재 사실·목표 topology·IAM/SG 계약을 사용했다.
- 변경 파일: `docs/architecture/tosunsaeng-app-system-configuration.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 구현 내용: AWS Cloud·VPC·ALB ingress·ECS Fargate·ECR·S3·IAM/Secret reference·관측 영역, 외부 앱/인증/AI/데이터 의존성을 배치했다. production/staging 분리 목표와 현재 staging 명칭 cluster가 실제 트래픽을 처리하는 상태를 명시했다.
- 유지한 계약: 공개 API·`BaseResponse`·사용자 식별·AI·S3·Redis·Billing wire와 배포 workflow를 변경하지 않았다.
- 테스트·검증: draw.io XML, 페이지 2개, AWS 페이지 vertex 25개·edge 16개, 누락 source/target 0개와 `git diff --check` 성공을 확인했다. 애플리케이션 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.
- 위험·미확인: 실제 ALB listener/target/DNS, VPC/subnet/SG/role ARN, MongoDB Atlas·Redis·NAT 경로와 Secret 서비스는 AWS read-only inventory 전까지 확정하지 않았다. draw.io CLI가 없어 실제 렌더 export는 수행하지 못했다.
- 배포·예상 밖 diff: 문서 변경이므로 배포 사항은 없다. 사용자가 별도로 만든 것으로 보이는 untracked `docs/architecture/제목 없는 다이어그램.drawio`는 수정하지 않았다.
- Git commit·push는 수행하지 않았고 Secret과 Token을 기록하지 않았다.
- 종료 전 라우팅 보완: Identity→Lattice는 서비스 상단과 cluster 사이 통로, Learning Core↔AI는 ECS 영역 우측과 VPC 하단 통로, 앱→S3는 managed resource 하단, 데이터 의존성은 ECS/Lattice 하단 통로로 우회시켰다. AWS 페이지 16개 edge 중 14개에 고정 경유점을 지정해 다른 핵심 도형을 관통하지 않도록 했다.

## 2026-09-01 — 1차 업데이트 완료 기준 시스템·AWS 구성도

<!-- codex-turn:01a05bed-0554-7680-a519-b62c5618369d -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 구성도 시점 변경은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 5줄 결론: 첫 페이지를 현재와 목표의 비교가 아니라 `1차 업데이트 완료 시점`의 단일 운영 스냅샷으로 변경했다. SNS/Phone 로그인, 무료 모의고사, 기존 시험·AI 채점, Billing Reservation·AttemptGroup lifecycle, 10초 챌린지를 완료 기능으로 표현했다. Production ECS Fargate, public ALB, private Billing과 VPC Lattice AWS_IAM 경계를 완료 구조로 표시했다. Identity·Learning Core·Billing별 MongoDB, Redis, S3와 기존 시험·Challenge AI 연동을 포함했다. 기존 둘째 페이지 백엔드 기술 구성도와 고정 연결선 routing은 보존했다.
- 완료 기준 근거: `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`의 출시 차단 조건, Learning Core·Identity staging workflow의 GitHub Actions OIDC→ECR→ECS 배포 방식, Billing `docs/adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`의 production topology를 반영했다.
- 변경 파일: `docs/architecture/tosunsaeng-app-system-configuration.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 유지한 계약: 애플리케이션 코드, 공개 API·`BaseResponse`, 사용자 식별, AI request/Callback, S3·Redis·Billing wire와 배포 workflow를 변경하지 않았다.
- 테스트·검증: `xmllint --noout` 성공, 페이지 2개, 첫 페이지 vertex 25개·edge 16개, 누락 source/target 0개, 과도기 표기 미검출과 `git diff --check` 성공을 확인했다. 문서 변경이라 Gradle 테스트는 실행하지 않았다.
- 위험·미확인: 이 그림은 1차 업데이트의 release gate와 production canary까지 완료됐다는 전제의 논리 구성도다. 실제 ALB listener/target/DNS, subnet·SG·ARN 같은 물리 resource ID는 AWS inventory 없이 추정하지 않았다. diagrams.net에서 실제 렌더링한 뒤 label의 미세 위치를 확인할 수 있다.
- 배포 전 확인: 이 문서 자체의 배포 사항은 없다. 실제 1차 업데이트 배포 시에는 production/staging 격리, Mongo replica-set, Lattice/IAM/SG, 장애 복구 E2E, canary와 rollback 검증이 완료 조건이다.
- 예상 밖 diff: 사용자가 별도로 만든 untracked `docs/architecture/제목 없는 다이어그램.drawio`는 수정하지 않았다. 애플리케이션·설정 파일의 예상 밖 변경은 없다.
- Git commit·push는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 앱 프론트·AI 근거 기반 전체 제품 구성도 개편

<!-- codex-turn:01a05bf3-4e4a-7c11-ac0b-097a1b4a180f -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 구성도 개편은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 5줄 결론: `app-front-end`와 `web-ai`를 직접 조사해 구성도를 백엔드 중심 2페이지에서 제품 중심 3페이지로 개편했다. 첫 페이지는 학습자가 앱에서 로그인·시험·챌린지·피드백·재답변을 경험하는 전체 흐름을 보여준다. 둘째 페이지는 앱과 네 서비스의 AWS 배포·데이터 구조를 보여준다. 셋째 페이지는 기존 백엔드 기술 상세를 보존한다. AI를 AWS 외부 시스템이 아니라 ECS의 FastAPI API·Redis queue·4개 worker 서비스로 바로잡았다.
- 조사 근거: `Too-Much-I/app-front-end` commit `4e6c5957f72a92025ac0eb6d9bd7beff51eb1783`의 navigation, auth, exam/challenge API·hook, WebView native bridge, CI/EAS·관측 설정과 `Too-Much-I/web-ai` commit `ee9db665ddadd9b830c3d39988157ed535ccaa04`의 FastAPI route, scoring pipeline, Redis worker, Callback, Docker Compose와 app ECS workflow를 읽었다.
- 확인된 구현 사실: 앱은 Expo 57 React Native이며 홈·모의고사·피드백 탭, 마이크/사운드 점검, 11문항 녹음·S3 upload·Polling, 피드백 WebView/native data bridge, 재답변과 10초 챌린지 화면/API 경계를 가진다. AI는 현재 시험 `/evaluations`, Q1 Azure, Q2~Q11 STT+Azure+LLM/VLM, 결정론 checklist score, 한국어 feedback/summary Callback과 ECS API+worker 배포를 구현한다.
- 완료 시점 전제: 조사한 프론트 개발 모드의 Challenge mock은 제거되고 실제 Learning Core API를 사용하며, 조사한 AI 저장소에는 아직 없는 Challenge 전용 평가 endpoint·양방향 credential/Callback이 1차 업데이트 완료 조건에 따라 구현·E2E 검증된 상태로 표현했다. SNS/Phone 로그인도 같은 완료 조건으로 표시했다.
- 변경 파일: `docs/architecture/tosunsaeng-app-system-configuration.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 변경한 동작: draw.io 페이지를 `1. 제품·사용자 흐름 구성도`, `2. AWS·배포 구성도`, `3. 백엔드 기술 구성도`로 재구성했다. 첫 페이지에 사용자 가치, 앱의 여섯 기능 영역, Identity/Learning/Billing/AI, WebView 콘텐츠, S3, AI provider, 서비스별 데이터, 관측과 앱·서버 배포를 연결했다.
- 유지한 외부 계약: 애플리케이션 코드, 공개 API·`BaseResponse`, userId 식별, `AI user_id=examId`, Callback JSON, retryCount, S3·Redis·Billing 계약과 배포 workflow를 변경하지 않았다.
- 테스트·결과: `xmllint --noout` 성공, 페이지 3개, 페이지별 `24/17`, `25/17`, `29/14` vertex/edge, 전체 누락 source/target 0개와 `git diff --check` 성공을 확인했다. 문서만 변경해 Gradle·프론트·AI 테스트는 실행하지 않았다.
- 주요 위험·미확인: draw.io CLI가 없어 실제 PNG 렌더 export는 수행하지 못했다. 정확한 production ALB/target/subnet/SG/ARN과 AI Redis·runtime의 실제 AWS resource topology는 read-only inventory 없이 추정하지 않았다. 피드백 Web 콘텐츠 저장소는 이번에 제공되지 않아 앱의 WebView·bridge 사용 사실까지만 표현했다.
- 배포 전 확인: Challenge mock 제거, Challenge AI contract/E2E, SNS/Phone mobile E2E, AI ECS queue/runtime 공유 방식, production/staging 격리, canary/rollback을 실제 환경에서 다시 확인해야 한다.
- 예상 밖 diff: 사용자가 별도로 만든 untracked `docs/architecture/제목 없는 다이어그램.drawio`는 수정하지 않았다. 애플리케이션·설정 파일의 예상 밖 변경은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 읽거나 기록하지 않았다.

## 2026-09-01 — 전체 제품·AWS 구성도 화살표 정리

<!-- codex-turn:01a05bff-985b-7ca2-82a5-61264ce40cd7 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 구성도 가독성 개선은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 변경 파일: `docs/architecture/tosunsaeng-app-system-configuration.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 변경한 동작: 제품 페이지의 연결선을 17개에서 14개로 줄였다. AI 평가 요청과 문항·요약 Callback을 양방향 한 줄로 합치고, 긴 결과 회귀선과 의미가 박스 본문에 이미 있는 데이터 소유권 선을 제거했다. 앱 음성 Presigned PUT은 Identity/Learning 사이의 빈 세로 통로와 하단 전용 통로를 거쳐 S3에 연결했다.
- AWS 페이지: Learning Core↔AI 평가 요청/Callback 두 줄을 양방향 한 줄로 합쳐 17개에서 16개로 줄였다. 기존 ingress, Lattice, S3, 배포와 데이터선의 고정 routing은 보존했다.
- 유지한 외부 계약: 도식의 기능·서비스·AWS 내용과 애플리케이션 코드, 공개 API·`BaseResponse`, userId, AI request/Callback, S3·Redis·Billing 계약을 변경하지 않았다.
- 테스트·결과: `xmllint --noout` 성공, 페이지별 vertex/edge/routed가 `24/14/8`, `25/16/13`, `29/14/9`, 전체 누락 source/target 0개이며 `git diff --check`가 통과했다. 문서만 변경해 Gradle·프론트·AI 테스트는 실행하지 않았다.
- 위험·미확인: draw.io CLI가 없어 PNG 렌더링 기반 육안 검증은 수행하지 못했다. diagrams.net에서 열었을 때 label의 미세 offset은 추가 조정할 수 있다.
- 배포 전 확인: 문서 변경이라 배포 사항은 없다. 구성도 사용 전 첫 페이지와 둘째 페이지를 100% zoom에서 열어 label 겹침 여부만 확인하면 된다.
- 예상 밖 diff: 사용자가 만든 untracked `docs/architecture/제목 없는 다이어그램.drawio`는 수정하지 않았다. 애플리케이션·설정 파일의 예상 밖 변경은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 구성도 화살표를 도형 뒤 레이어로 이동

<!-- codex-turn:01a05c03-d993-7102-9367-82eed9c99fb0 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 draw.io 레이어 조정은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 변경 파일: `docs/architecture/tosunsaeng-app-system-configuration.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 변경한 동작: 세 페이지의 mxCell 저장 순서를 `큰 영역 배경 → edge → 실제 도형·텍스트`로 재배치했다. 연결선은 AWS/VPC/서비스 영역 배경 위에는 보이지만 기능 카드·서비스 박스·글자보다 뒤에서 렌더링된다.
- 유지한 내용: 연결선 수, source/target, 고정 경유점, 기능·서비스·AWS 설명과 페이지 구조는 변경하지 않았다.
- 유지한 외부 계약: 애플리케이션 코드, 공개 API·`BaseResponse`, 사용자 식별, AI request/Callback, S3·Redis·Billing 계약을 변경하지 않았다.
- 테스트·결과: `xmllint --noout` 성공, 3페이지와 페이지별 vertex/edge `24/14`, `25/16`, `29/14`를 유지했다. foreground 도형보다 앞에 남은 edge 0개, edge보다 뒤에 잘못 배치된 영역 배경 0개, 누락 source/target 각각 0개와 `git diff --check` 성공을 확인했다. 문서만 변경해 Gradle·프론트·AI 테스트는 실행하지 않았다.
- 위험·미확인: draw.io CLI가 없어 실제 PNG export 기반 육안 검증은 수행하지 못했다. diagrams.net renderer가 mxCell z-order를 따르는 일반 동작을 기준으로 적용했다.
- 배포 전 확인: 문서 변경이라 배포 사항은 없다. diagrams.net에서 첫 페이지를 열어 선이 카드 뒤로 가려지는지만 확인하면 된다.
- 예상 밖 diff: 임시 layer 변환 stylesheet는 작업 후 삭제했다. 사용자가 만든 untracked `docs/architecture/제목 없는 다이어그램.drawio`는 수정하지 않았고 애플리케이션·설정 파일의 예상 밖 변경은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — draw.io 실제 레이어 분리와 렌더링 재검증

<!-- codex-turn:01a05c07-5d0e-74f1-ac32-847056a0d2ae -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 구성도 렌더링 수정은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 문제 원인: 동일 draw.io layer 안에서 mxCell XML 순서만 바꾸는 이전 방식은 diagrams.net renderer가 edge를 다시 위에 그릴 수 있어 실제 화면에서 도형 뒤 배치를 보장하지 못했다.
- 복구 기록: 최초 실제 layer 변환의 임시 출력 검증이 실패했는데 shell이 후속 이동을 계속해 target이 일시적으로 빈 파일이 됐다. 빈 결과를 최종 산출물로 사용하지 않고 제품·AWS 페이지를 재생성했으며, 사용자가 별도로 보관한 `docs/architecture/제목 없는 다이어그램.drawio`를 읽기 전용 원본으로 사용해 백엔드 상세 페이지를 복원했다. 원본 파일은 수정하지 않았다.
- 변경 파일: `docs/architecture/tosunsaeng-app-system-configuration.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 변경한 동작: 각 페이지에 draw.io top-level layer `배경`, `연결선`, `도형·텍스트`를 실제로 추가했다. 모든 edge의 parent는 `edge-layer`, 영역 배경은 `background-layer`, 실제 카드·텍스트는 `foreground-layer` 또는 그 하위 cell이다.
- 실제 렌더 검증: Browser 스킬을 사용해 첫 페이지 mxGraphModel을 diagrams.net 편집기에 로드하고 스크린샷으로 확인했다. 기능 카드가 연결선보다 위에 렌더링되고, 선은 별도 하위 layer에 존재하는 것을 확인했다.
- 유지한 내용: 제품 페이지의 사용자·앱·Identity/Learning/Billing/AI·S3·관측 흐름, AWS 페이지의 production 논리 구조와 백엔드 상세 페이지를 유지했다. 복원 과정에서 AWS 페이지 표현을 동일 의미의 간결한 14개 edge 구조로 수렴시켰다.
- 유지한 외부 계약: 애플리케이션 코드, 공개 API·`BaseResponse`, 사용자 식별, AI request/Callback, S3·Redis·Billing 계약을 변경하지 않았다.
- 테스트·결과: `xmllint --noout` 성공. 페이지별 `3 layers / 24 vertices / 14 edges`, `3 / 21 / 14`, `3 / 28 / 15`이며 모든 edge가 `edge-layer`에 있다. 잘못된 edge parent 0개, 누락 source/target 각각 0개와 `git diff --check` 성공을 확인했다. 문서만 변경해 Gradle·프론트·AI 테스트는 실행하지 않았다.
- 위험·배포 전 확인: 문서 변경이라 배포 사항은 없다. 사용자가 로컬 diagrams.net에서 기존 열린 탭이 아니라 수정된 파일을 다시 열어 layer 효과를 확인해야 한다.
- 예상 밖 diff: 임시 generator·stylesheet는 삭제했다. 사용자 원본과 애플리케이션·설정 파일의 예상 밖 변경은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 데일리 학습 콘텐츠 수행 방법 문구 작성

<!-- codex-turn:01a05c24-a3d0-7231-b2cf-20eb9242f4f6 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 문구 작성은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 작업 내용: 사용자가 작성 중인 수행 방법 표의 `개발 단계 > 데일리 학습 콘텐츠` 항목에 넣을 문구를 토선생의 10초 챌린지와 실제 구현 방향에 맞게 정리했다.
- 권장 문구: TOEIC Speaking 유형과 학습자 취약 영역을 반영한 짧은 일일 말하기 문제를 자체 기획·제작하고, 앱에서 매일 새로운 문제 제공, 10초 음성 녹음, AI 발화 분석, 교정 문장·모범 답안·맞춤 피드백 확인까지 이어지는 반복 학습 기능을 구현한다. 초기 콘텐츠는 전문가 검수와 자체 제작으로 확보하고 이용·정답률·오류 유형 데이터를 분석해 난이도와 문항을 지속 개선한다.
- 표현 원칙: 단순히 콘텐츠를 제공한다고 쓰지 않고 콘텐츠 확보 방법, 사용자 학습 절차, AI 피드백, 운영 개선 순서가 드러나게 했다. 구현된 사실과 향후 운영 계획을 과장하지 않도록 구분한다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 유지한 외부 계약: 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약과 draw.io를 변경하지 않았다.
- 테스트·결과: 문구 작성과 기록 문서만 변경해 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 문서 형식을 검증한다.
- 위험·배포 전 확인: 제출처의 글자 수 제한과 10초 챌린지 명칭 공개 가능 여부가 확인되지 않았다. 제한이 있으면 축약형을 사용한다. 배포 사항은 없다.
- 예상 밖 diff: 이번 작업에서 기록 문서 외 파일을 수정하지 않았다. 기존 untracked draw.io 파일은 건드리지 않았다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 데일리 학습 콘텐츠 예상 문제점·결과물 문구

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 문구 작성은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 작업 내용: 수행 방법 표의 데일리 학습 콘텐츠에 대응하는 `예상 문제점-개발 측면`과 `결과물 형태` 문구를 작성했다.
- 예상 문제점: 매일 제공할 문항의 지속적 확보와 난이도·품질 관리, 다양한 정답 표현에 대한 AI 판정 일관성, 모바일 음성 녹음·업로드 실패, 비동기 채점 지연과 중복 제출, AI 비용과 개인정보 보호를 핵심 개발 위험으로 정리했다.
- 대응 방향: 콘텐츠 snapshot·전문가 검수, 정답 허용 범위와 평가 기준 표준화, 멱등 제출·Polling·재시도, 음성·Transcript 최소 보관과 민감정보 비기록, 사용 데이터 기반 개선을 제시한다.
- 결과물 형태: 모바일 앱의 오늘 문제·10초 녹음·결과/이력 화면, 일일 문항 catalog DB, 음성 업로드·채점·결과 API, AI 교정·모범답안·피드백 결과, 테스트·운영 지표 문서로 구분한다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 유지한 외부 계약: 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약과 draw.io를 변경하지 않았다.
- 테스트·결과: 문구와 기록 문서만 변경해 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 문서 형식을 검증한다.
- 위험·배포 전 확인: 제출 양식의 글자 수와 결과물 분류 기준이 확인되지 않았다. 제한이 있으면 축약형을 사용한다. 배포 사항은 없다.
- 예상 밖 diff: 이번 작업에서 기록 문서 외 파일을 수정하지 않았다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 데일리 학습 콘텐츠 문제점·결과물 문구 종료 기록

<!-- codex-turn:01a05c26-be32-78e0-bc4a-8b4de8084d53 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 문구 작성은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 완료 내용: 데일리 학습 콘텐츠의 개발 측면 예상 문제점을 콘텐츠 확보·품질과 난이도, AI 평가 일관성, 음성 업로드, 비동기 채점 지연·중복 제출, AI 비용·개인정보 보호로 정리했다.
- 결과물 형태: 오늘 문제·10초 녹음·채점 대기·결과/이력 앱 화면, 문항 catalog DB, 음성 업로드·채점·결과 API, AI 교정 문장·모범 답안·맞춤 피드백과 운영·테스트 문서로 제시했다.
- 사용자 전달: 표에 넣을 기본 문장과 공간이 좁을 때 사용할 축약형을 함께 제공했다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약: 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약과 draw.io를 변경하지 않았다.
- 테스트: 문구와 기록 문서만 변경해 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 검증한다.
- 위험·배포: 제출 양식의 글자 수 제한은 확인되지 않았으며 문서 작업이므로 배포 사항은 없다.
- 예상 밖 변경: 기록 문서 외 이번 작업으로 변경한 파일은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 중간 발표용 개발 문제점 2개 선정

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 발표 문구 선정은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 선정 결과: 전체 개발 위험 중 중간 발표에는 토선생의 핵심 가치와 직접 연결되는 `AI 채점 품질·신뢰도`와 사업 지속 가능성을 보여주는 `외부 AI API 비용·의존성` 두 가지를 권장했다.
- 해결 방향: 채점 sample 검수·품질 지표·이상 결과 조기 보정과 오픈소스/복수 모델 사전 검증·비용/성능 비교·유연한 모델 선택 구조를 각각 대응 방안으로 제시했다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약: 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약과 draw.io를 변경하지 않았다.
- 테스트: 문구와 기록 문서만 변경해 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 검증한다.
- 위험·배포: 발표 자료의 글자 수 제한은 확인되지 않았고 배포 사항은 없다.
- 예상 밖 변경: 기록 문서 외 이번 작업으로 변경한 파일은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 전체 서비스 예상 문제점·해결 방안·결과물 문구 재정리

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 문구 작성은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 정정 내용: `예상 문제점-개발 측면`과 `결과물 형태`를 데일리 학습 콘텐츠 한 기능이 아니라 모의고사, 피드백, 학습 로드맵·챗봇, 데일리 학습 콘텐츠와 피드백 챗봇을 포함한 전체 토선생 서비스 기준으로 재작성했다.
- 예상 문제점과 해결: 다기능 범위 확대는 MVP·단계별 roadmap과 acceptance criteria로 통제하고, 앱·Identity·Learning·Billing·AI 간 계약 불일치는 계약 문서·contract test·staging E2E로 방지한다. AI 품질 편차는 표준화 rubric·benchmark·전문가 검수·fallback으로, 음성·비동기 실패는 형식 표준화·멱등 처리·retry/Polling·복구 Job으로 대응한다.
- 추가 해결: 개인정보는 최소 수집·보관 기간·암호화·로그 비식별화로 보호하고, AI 비용과 확장성은 사용량 metric·cache·worker scale-out·모델별 비용 기준으로 관리한다. 콘텐츠 품질은 catalog·snapshot·검수 workflow와 학습 데이터 분석으로 개선한다.
- 결과물 형태: iOS/Android 앱, Identity·Learning Core·Billing 서버, AI 채점/피드백 서버, 모의고사·데일리 콘텐츠 catalog, 학습 로드맵·피드백 챗봇, API/데이터 계약 문서, 관리자·운영 지표, 테스트 결과와 배포·모니터링 체계를 전체 산출물로 정리했다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약: 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약과 draw.io를 변경하지 않았다.
- 테스트: 문구와 기록 문서만 변경해 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 검증한다.
- 위험·배포: 제출 양식의 글자 수 제한은 확인되지 않았으며 문서 작업이므로 배포 사항은 없다.
- 예상 밖 변경: 기록 문서 외 이번 작업으로 변경한 파일은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 전체 서비스 문제점·해결 방안·결과물 문구 종료 기록

<!-- codex-turn:01a05c28-6f95-7123-b3ed-db4f3386f883 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 문구 작성은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 완료 내용: 개발 측면 예상 문제점과 결과물 형태의 범위를 데일리 콘텐츠 한 기능이 아닌 토선생 전체 서비스로 정정했다.
- 문제점·해결 방안: 범위 확대, 서비스 간 계약 불일치, AI 평가 편차, 음성·비동기 실패, 콘텐츠 확보, 개인정보, 비용·확장성과 배포 복잡성을 식별하고 각각 단계별 MVP, 계약·E2E 테스트, 표준 평가·전문가 검수, 멱등·retry·복구, catalog 운영, 데이터 보호, 사용량 모니터링과 자동 배포·rollback으로 연결했다.
- 결과물 형태: iOS·Android 앱, Identity·Learning Core·Billing·AI 서버, 모의고사·데일리 콘텐츠 DB, AI 피드백·학습 로드맵·챗봇, API·데이터 계약서, 테스트·분석·운영·배포 문서를 전체 산출물로 정리했다.
- 사용자 전달: 제출용 상세 문단, 문제점/해결 방안 대응표와 글자 수가 짧을 때 사용할 축약형을 제공했다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약: 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약과 draw.io를 변경하지 않았다.
- 테스트: 문구와 기록 문서만 변경해 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 검증한다.
- 위험·배포: 제출 양식의 글자 수 제한은 확인되지 않았으며 문서 작업이므로 배포 사항은 없다.
- 예상 밖 변경: 기록 문서 외 이번 작업으로 변경한 파일은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 전체 서비스 개발 측면 예상 문제점 문체 정리

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 문구 작성은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 작업 내용: 사용자가 제시한 `발생 가능성 → 모니터링·검증·대체 수단 구축` 문체에 맞춰 토선생 전체 서비스의 개발 측면 예상 문제점과 해결 방안을 대응 순서로 작성했다.
- 문제 범위: AI 채점 품질, 외부 AI 비용, 앱·서버·AI 계약 불일치, 음성 업로드·비동기 채점 장애, 개인정보·보안과 이용량 증가에 따른 성능 저하를 포함한다.
- 해결 방향: 품질 sample 검수·지표·benchmark, 대체 모델 검증·비용 routing, 계약 문서·자동 contract/E2E test, 멱등·retry·Polling·복구 처리, 최소 수집·암호화·비식별화와 scale-out·관측 체계를 각 문제에 연결한다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약: 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약과 draw.io를 변경하지 않았다.
- 테스트: 문구와 기록 문서만 변경해 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 검증한다.
- 위험·배포: 제출 양식의 글자 수 제한은 확인되지 않았고 배포 사항은 없다.
- 예상 밖 변경: 기록 문서 외 이번 작업으로 변경한 파일은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 전체 서비스 개발 문제점·해결 방안 제출 문구 종료

<!-- codex-turn:01a05c2c-0d6f-7bf2-914f-7d9e5b500501 -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 문구 작성은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 완료 내용: 전체 토선생 서비스의 개발 측면 예상 문제점 7개와 같은 순서로 대응하는 해결 방안 7개를 제출용 문체로 작성했다.
- 문제점: AI 채점 품질·일관성, 외부 AI 비용, 다중 서비스 계약 불일치, 모바일 음성·비동기 Callback 장애, 트래픽 집중, 개인정보·학습 데이터 보호와 콘텐츠 버전·품질 불일치를 포함했다.
- 해결 방안: sample 검수·품질 지표, 대체 모델 사전 검증, API 계약·자동 테스트, 멱등·retry·Polling·Callback 복구, 비동기 queue·scale-out, 최소 수집·암호화·비식별화와 콘텐츠 version·전문가 검수를 각 항목에 연결했다.
- 사용자 전달: 사용자가 제시한 `발생할 가능성`과 `지속적으로 모니터링·구축` 문체를 유지하고 문제점 목록과 해결 방안 목록의 순서를 일치시켰다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약: 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약과 draw.io를 변경하지 않았다.
- 테스트: 문구와 기록 문서만 변경해 Gradle 테스트는 실행하지 않았으며 `git diff --check`로 검증한다.
- 위험·배포: 제출 양식의 글자 수 제한은 확인되지 않았고 문서 작업이므로 배포 사항은 없다.
- 예상 밖 변경: 기록 문서 외 이번 작업으로 변경한 파일은 없다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-01 — 중간 발표용 개발 문제점 2개 최종 선정

<!-- codex-turn:01a05c2d-d987-7791-b62f-57222333ac4e -->

- 날짜: 2026-09-01
- 브랜치: `codex/fix-tmi-118-summary-transaction`
- Jira: 현재 브랜치는 `TMI-118` 문맥이지만 이번 발표 문구 선정은 별도 Jira 작업이 아니며 Jira를 조회하거나 변경하지 않았다.
- 완료 내용: 중간 발표에 사용할 개발 측면 예상 문제점으로 `AI 채점 품질·신뢰도`와 `외부 AI API 비용·의존성` 두 가지를 최종 추천했다.
- 해결 방안: 채점 결과 표본 검수·품질 지표 모니터링·이상 결과 조기 보정과 오픈소스·복수 모델 사전 검증·비용/성능 기반 선택 구조를 각각 연결했다.
- 선정 근거: 두 항목이 토선생의 핵심 경쟁력인 채점 신뢰도와 서비스의 지속 가능한 운영을 가장 직접적으로 설명한다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 유지한 외부 계약: 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약과 draw.io를 변경하지 않았다.
- 테스트·결과: 문서 기록만 변경하여 Gradle 테스트는 실행하지 않았고 `git diff --check`로 형식을 검증한다.
- 위험·배포 전 확인: 발표 자료의 글자 수 제한은 확인되지 않았으며 코드 배포 사항은 없다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 이번 작업에서 수정하지 않았다.
- 다음 작업 전 확인: 실제 발표 양식의 분량에 따라 문장을 축약할지 확인한다.
- Git commit·push·배포는 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## 2026-09-02 — TMI-118 hotfix 이후 다음 작업 확정

<!-- codex-turn:01a05fa8-6bd9-7521-a97f-cb3c26961017 -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`; 상태는 완료이며 이번 작업에서 변경하지 않았다.
- Git 확인: hotfix commit `4781723`이 PR #26 merge commit `4f9e74c`로 병합됐고 현재 `HEAD`, 로컬 `develop`, `origin/develop`이 `4f9e74c`로 일치한다.
- 다음 즉시 작업: AttemptGroup 실제 경계 통합 검증을 신규 후속 Jira로 분리한다. 격리된 replica-set Mongo에서 transaction commit/rollback, duplicate·unknown commit 수렴, COMPLETED/RETAKE terminal race와 multi-instance lease reclaim/stale token fencing을 검증한다.
- 전송·관측 검증: fake signer와 local HTTP component test로 publish span traceparent 주입 후 SigV4가 마지막 변경인지 확인하고, payload·credential·사용자 식별자가 log/span/metric에 없는지 검사한다. 이후 Learning Core/Billing staging E2E에서 같은 traceId, 다른 spanId와 baggage 미전파를 확인한다.
- 다음 제품 기능: 위 rollout gate 이후 Billing `UserMerged` retained subject owner rebind의 wire 계약·활성 Reservation 충돌 정책을 ADR과 계획서로 확정하고 전용 Jira를 생성한다.
- 변경 파일: 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·테스트·설정과 외부 계약은 변경하지 않았다.
- 테스트·검증: 코드 변경이 없어 Gradle 테스트를 재실행하지 않았다. hotfix PR 포함 관계와 Git ref를 읽기 전용으로 확인하고 문서는 `git diff --check`와 marker 1회 검사로 검증한다.
- 예상 밖 변경: 기존 미추적 `docs/architecture` draw.io 파일 2개는 사용자 변경으로 보존했으며 수정하지 않았다.
- 보안·범위: Secret·Token을 기록하지 않았고 Jira 생성·상태 전환, DB·AWS·Git commit·push·PR·merge·배포를 수행하지 않았다.

## 2026-09-02 — 학습 로드맵·챗봇 무료/유료 설명 정리

<!-- codex-turn:01a060e0-a105-72f3-a7ce-66fc65f476ec -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 문구 작성 작업이다.
- 완료 내용: 학습 로드맵·챗봇 기능을 무료형 `표준 로드맵 안내`와 유료형 `학습 데이터 기반 맞춤형 코칭`으로 구분해 제출용 문장으로 정리했다.
- 무료 기능: 최초 목표 설정 시 입력받은 목표 등급과 시험 준비 기간을 기준으로, 사전에 분류·설계한 학습 로드맵 중 적합한 경로를 안내한다.
- 유료 기능: 기존 로드맵을 기준으로 사용자의 학습 이력, 모의고사 결과와 피드백을 함께 분석하여 현재 수준과 취약점을 반영한 세부 학습 방법과 우선순위를 챗봇이 안내한다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약·테스트: 애플리케이션 코드와 공개 API·AI·S3·Redis·Billing 계약은 변경하지 않았다. 문서 기록만 변경하여 Gradle 테스트는 실행하지 않고 `git diff --check`로 검증한다.
- 위험·미확인: 유료 로드맵의 갱신 주기, 추천 범위, 사용자에게 보여줄 근거 수준과 무료/유료 전환 조건은 아직 확정되지 않았다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 이번 작업에서 수정하지 않았다.
- 보안·범위: Secret과 Token을 기록하지 않았고 Git commit·push·배포를 수행하지 않았다.

## 2026-09-02 — 학습 로드맵·챗봇 상세 설명 확장

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 문구 작성 작업이다.
- 완료 내용: 학습 로드맵·챗봇을 목표 설정, 무료 표준 경로 제공, 유료 학습 데이터 분석, 챗봇 코칭, 로드맵 재조정과 품질 통제의 전체 흐름으로 상세화했다.
- 무료 기능: 목표 등급과 준비 기간을 입력받아 전문가가 사전 설계·분류한 로드맵을 규칙 기반으로 매칭하고, 기간별 학습 순서와 권장 학습량을 안내하는 구조로 설명했다.
- 유료 기능: 학습 이력, 모의고사 점수, 파트·문항별 피드백과 반복 취약점을 표준 로드맵에 결합해 우선순위·실행 과제·복습 방향을 개인화하고 이후 성과에 따라 재조정하는 구조로 설명했다.
- 품질 원칙: AI가 검증되지 않은 학습법을 새로 생성하지 않고 사전 설계된 로드맵 범위 안에서 근거 데이터를 바탕으로 안내하며, 데이터가 부족할 때는 표준 경로를 유지하도록 정리했다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약·테스트: 애플리케이션 코드와 공개 API·AI·S3·Redis·Billing 계약은 변경하지 않았다. 문서 기록만 변경하여 Gradle 테스트는 실행하지 않고 `git diff --check`로 검증한다.
- 위험·미확인: 목표 등급 체계, 준비 기간 구간, 로드맵 분류표, 개인화 갱신 시점과 챗봇이 제시할 세부 항목은 제품 정책으로 확정해야 한다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 이번 작업에서 수정하지 않았다.
- 보안·범위: Secret과 Token을 기록하지 않았고 Git commit·push·배포를 수행하지 않았다.

## 2026-09-02 — 학습 로드맵·챗봇 상세 설명 종료

<!-- codex-turn:01a060e1-de6c-7fa3-8a5a-b013e5d1c568 -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 문구 작성 작업이다.
- 완료 내용: 모의고사 기능 설명과 유사한 상세도로 학습 로드맵·챗봇의 무료/유료 기능, 데이터 활용, 개인화 갱신과 품질 원칙을 최종 문안으로 작성했다.
- 변경한 설명: 무료는 목표 등급·준비 기간에 따른 검증된 표준 로드맵 매칭으로, 유료는 학습·모의고사·AI 피드백을 근거로 우선순위와 다음 행동을 조정하는 개인 맞춤형 코칭으로 구분했다.
- 유지한 원칙: 학습 데이터가 부족하면 표준 경로를 유지하고, 챗봇은 검증되지 않은 학습법을 임의 생성하지 않고 사전 설계된 로드맵 범위 안에서 안내한다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약·테스트: 코드 및 공개 API·AI·S3·Redis·Billing 계약은 변경하지 않았다. 문서 기록만 변경하여 Gradle 테스트는 실행하지 않았고 `git diff --check`로 검증한다.
- 남은 위험·결정: 로드맵 분류표, 갱신 시점, 추천의 세부 단위와 무료/유료 전환 정책은 후속 제품 결정이 필요하다.
- 배포 전 확인: 문안 작업이므로 배포 사항은 없으며 발표·제출 양식의 분량만 확인하면 된다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 이번 작업에서 수정하지 않았다.
- 보안·범위: Secret과 Token을 기록하지 않았고 Git commit·push·배포를 수행하지 않았다.

## 2026-09-02 — 간결한 실제 AWS 시스템 구성도 사전 확인

<!-- codex-turn:01a060e9-4db5-7aa0-a5bd-3e20cff8f82d -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 구성도 사전 분석 작업이다.
- 사용자 요청: 첨부 예시처럼 구성요소와 핵심 흐름만 남긴 간결한 draw.io를 만들되, 개념적 백엔드 구조보다 실제 AWS 배포 구조를 중심으로 표현한다.
- 확인된 사실: Learning Core staging workflow에는 GitHub Actions OIDC, ECR과 ECS Fargate 배포가 있으며 코드·기존 문서에는 S3 Presigned 업로드, MongoDB와 Redis 연동이 존재한다.
- 문서상 계획·미확정: 기존 구성도에는 Public DNS/ALB, VPC Lattice AWS_IAM, private Billing, AI ECS, CloudWatch와 환경별 data plane이 표현돼 있으나 실제 콘솔 배포 상태와 일치하는지는 확인이 필요하다.
- 다음 입력 요청: 대상 환경·리전, AWS IAM Identity Center 계정/권한, ECS 서비스, 공개 ingress, VPC/subnet/NAT, Lattice 적용 상태, 데이터 저장소 공급자와 배포·관측 리소스를 사용자에게 확인한다.
- 보안 안내: 로그인 비밀번호, MFA 코드, Access Key, Secret과 Token은 요청하거나 기록하지 않는다. 사용자가 로그인한 AWS 콘솔은 승인된 범위에서 읽기 전용으로 확인한다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약·테스트: 구성도와 애플리케이션 코드, 공개 API·AI·S3·Redis·Billing 계약은 아직 변경하지 않았다. 문서 기록만 변경해 Gradle 테스트는 실행하지 않고 `git diff --check`로 검증한다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 이번 작업에서 수정하지 않았다.
- Git commit·push·AWS 변경·배포는 수행하지 않았다.

## 2026-09-02 — Production AWS 구성 확인 준비

<!-- codex-turn:01a060ee-242d-78f1-bac2-e9477c411b86 -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 구성도 확인 작업이다.
- 사용자 확정: 구성도는 서울 리전 Production만 대상으로 하며, 현재 AWS에는 Billing을 제외한 Identity·Learning Core·AI가 배포되어 있다.
- 사용자 확정: 가비아에서 관리하는 도메인이 공유 ALB로 진입하며 CloudFront·API Gateway·WAF는 사용하지 않는다. Cache는 Valkey이고 MongoDB Atlas는 클러스터로 분리돼 있다.
- 콘솔 확인: 사용자가 실제 AWS 콘솔을 읽기 전용으로 확인하도록 승인했다. 별도 브라우저에서 AWS 로그인 페이지를 열고 인증 단계는 사용자에게 넘겼다.
- 보안: 사용자가 전달한 AWS 계정 식별 정보와 federation 사용자 정보는 기록하지 않았으며, 비밀번호·MFA·Access Key·Secret·Token을 요청하거나 입력하지 않는다.
- 다음 단계: 사용자가 로그인하면 ECS service/task, shared ALB listener/target group, VPC/subnet/NAT/public IP, S3, ECR, Valkey와 CloudWatch를 읽기 전용으로 확인하고 간결한 draw.io를 작성한다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 외부 계약·테스트: 애플리케이션 코드, 구성도와 공개 API·AI·S3·Redis·Billing 계약은 변경하지 않았다. 기록 문서만 변경하여 Gradle 테스트는 실행하지 않았고 `git diff --check`로 검증한다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 이번 작업에서 수정하지 않았다.
- Git commit·push·AWS 설정 변경·배포는 수행하지 않았다.

## 2026-09-02 — 실제 AWS 기반 간결 Production 구성도 작성

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 구성도 작성 작업이다.
- 결과물: `docs/architecture/tosunsaeng-production-aws-simple.drawio` 한 페이지를 새로 작성했다. 첨부 예시처럼 계층과 핵심 통신만 남기고 연결선은 별도 하위 레이어에 배치했다.
- 콘솔 확인 사실: 서울 리전에 ECS Fargate cluster 1개, Identity·Learning Core·AI service와 실행 task 각 1개, Internet-facing ALB 1개, private task subnet 2개, public ALB subnet 2개와 NAT Gateway 1개가 있다.
- 서비스 연결: ALB HTTPS 443은 Identity target `8081`과 Learning Core target `8080`으로 라우팅한다. Learning Core는 ECS Service Connect client로 private AI alias `8000`을 호출하며 AI에는 ALB와 VPC Lattice가 구성되지 않았다.
- AI 구성: AI task 하나에 `ai-api`, `ai-worker-1`부터 `ai-worker-4`와 Service Connect sidecar가 함께 실행된다.
- 데이터·배포: S3 앱 음성 bucket, 단일 node형 ElastiCache for Valkey, 서비스별 MongoDB Atlas cluster, CloudWatch Container Insights, GitHub Actions OIDC → ECR → ECS 흐름을 구성도에 반영했다.
- 제외: 미배포 Billing, 미사용 CloudFront·API Gateway·WAF·VPC Lattice와 기존 웹 POC bucket은 구성도에서 제외했다.
- 명칭 위험: 실제 AWS resource는 `staging` 접두어를 사용하고 별도 production-named cluster·ALB는 확인되지 않았다. 요청한 Production 대상 도식에는 이 사실을 주의 문구로 표시했다.
- 보안 위험: Valkey는 단일 node·Multi-AZ 비활성 상태이고 전송 중 암호화가 비활성화된 것으로 확인됐다. 이번 작업에서는 AWS 설정을 변경하지 않았다.
- 보안·개인정보: AWS 계정 ID, federation 사용자, resource ARN, IP와 세부 식별자는 결과물·기록에 넣지 않았으며 Secret과 Token을 조회하거나 기록하지 않았다.
- 변경 파일: `docs/architecture/tosunsaeng-production-aws-simple.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 테스트·결과: `xmllint --noout` 성공, 1 page·23 vertex·13 edge, mxCell 중복 ID 없음과 `git diff --check` 통과를 확인했다. draw.io renderer CLI가 없어 PNG 렌더 기반 육안 검증은 수행하지 못했다.
- 외부 계약·배포: 애플리케이션 코드와 공개 API·AI·S3·Redis·Billing 계약은 변경하지 않았고 Git commit·push·AWS 설정 변경·배포를 수행하지 않았다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 사용자 작업으로 보존했으며 수정하지 않았다.

## 2026-09-02 — 실제 AWS 기반 간결 구성도 작업 종료

<!-- codex-turn:01a060f0-1711-7692-a8c1-daf770598502 -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 구성도 작성 작업이다.
- 완료 결과: 로그인된 AWS 콘솔을 읽기 전용으로 확인하고 `docs/architecture/tosunsaeng-production-aws-simple.drawio` 한 페이지를 작성·검증했다.
- 반영 범위: 가비아 DNS, Internet-facing 공유 ALB, private subnet의 Identity·Learning Core·AI ECS Fargate, Service Connect, S3, Valkey, MongoDB Atlas, ECR와 CloudWatch를 간결하게 표현했다.
- 제외 범위: 미배포 Billing과 미사용 CloudFront·API Gateway·WAF·VPC Lattice는 제외했다.
- 확인된 위험: 실제 AWS resource 이름은 `staging`이며 별도 production-named cluster·ALB가 없다. Valkey는 단일 node·Multi-AZ 비활성·전송 암호화 비활성 상태다.
- 변경 파일: `docs/architecture/tosunsaeng-production-aws-simple.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 유지한 계약: 애플리케이션 코드와 공개 API·AI·S3·Redis·Billing 계약을 변경하지 않았다.
- 테스트·결과: `xmllint --noout`, mxCell ID 중복 검사와 `git diff --check`가 통과했다. 1 page·23 vertex·13 edge이며 모든 edge는 도형보다 아래 레이어에 있다.
- 미확인·배포 전 확인: draw.io renderer CLI가 없어 PNG 육안 검증을 하지 못했다. 발표 전 diagrams.net에서 열어 선·라벨 겹침과 `staging` 명칭 처리 방침을 확인해야 한다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 수정하지 않았다.
- 보안·범위: 계정 ID·federation 사용자·ARN·IP·Secret·Token을 결과물과 기록에 넣지 않았고 AWS 설정 변경, Git commit·push와 배포를 수행하지 않았다.

## 2026-09-02 — 1차 업데이트 목표 AWS 구성도 수정

<!-- codex-turn:01a060fb-71ba-77a2-85c7-09bd996afe92 -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 구성도 수정 작업이다.
- 사용자 요청: 현재 실배포 구성이 아니라 1차 업데이트 완료 시점을 기준으로 앱 서버를 각각 하나씩 표현하고, AI는 Worker 없이 단일 서버로 표시한다.
- 변경한 파일: `docs/architecture/tosunsaeng-production-aws-simple.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 변경한 동작·표현: Identity·Learning Core·Billing·AI를 같은 ECS Fargate 영역의 서비스별 태스크 1개로 통일했다. AI는 FastAPI 단일 서버와 `Worker 없음`으로, Billing은 Spring Boot 결제·이용권 서버로 추가했다.
- 서비스 연결: 공개 ALB는 Identity와 Learning Core에만 연결했다. Learning Core↔AI는 ECS Service Connect, Learning Core→VPC Lattice↔Billing은 SigV4/AWS_IAM 기반 비공개 통신으로 표현했다.
- 데이터·배포: Valkey에서 AI Job Queue를 제거하고 Learning 상태·Lock만 남겼다. MongoDB Atlas의 Identity·Learning·Billing 서비스별 Cluster와 ECR의 네 서비스 image를 반영했다.
- 구현 사실·목표 구분: 현재 콘솔에는 staging 접두어 리소스, 미배포 Billing과 Worker 4개 포함 AI가 있다. 구성도 본문은 1차 업데이트 이후 Billing 태스크 1개와 Worker 없는 AI 태스크 1개라는 목표 구조이며 차이를 하단 주의 문구에 명시했다.
- 유지한 외부 계약: 애플리케이션 코드와 공개 API·`BaseResponse`·AI Callback·S3·Redis·Billing 계약을 변경하지 않았다.
- 테스트·결과: `xmllint --noout`, mxCell 중복 ID 검사와 레이어 검사를 통과했다. 1 page·25 vertex·15 edge이고 모든 edge의 parent는 도형보다 앞서 선언된 `edge-layer`다. `git diff --check`도 통과했다.
- 남은 위험·배포 전 확인: draw.io renderer CLI가 없어 PNG 육안 검증은 수행하지 못했다. diagrams.net에서 파일을 열어 Lattice 라벨과 연결선 꺾임을 최종 확인해야 한다. 실제 production 배포 전에는 Billing ECS/Lattice/IAM 구성과 AI 단일 프로세스의 처리량·장애 복구 방식을 별도로 검증해야 한다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개를 포함한 사용자 파일은 수정하지 않았다. 이번 요청 범위 밖의 애플리케이션·인프라 변경은 없다.
- 다음 작업 전 확인: 1차 업데이트의 AI 단일 서버가 동기 처리만 하는지, 내부 비동기 작업을 별도 프로세스 없이 같은 서버에서 처리하는지 운영 모델을 확정한다.
- 보안·범위: 계정 ID·federation 사용자·ARN·IP·Secret·Token을 기록하지 않았고 Git commit·push, AWS 설정 변경과 배포를 수행하지 않았다.

## 2026-09-02 — 간결 구성도 AWS Architecture 아이콘 적용

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 draw.io 시각 표현 작업이다.
- 사용자 요청: 첨부 예시와 같은 AWS Architecture 아이콘으로 현재 1차 업데이트 간결 구성도의 요소를 표현한다.
- 변경한 파일: `docs/architecture/tosunsaeng-production-aws-simple.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 변경한 표현: ALB에 AWS Application Load Balancer 아이콘을, Identity·Learning Core·AI·Billing에 Fargate 아이콘을, S3와 ElastiCache for Valkey에 각 AWS 서비스 아이콘을 적용했다.
- 비AWS 요소: 토선생 앱, MongoDB Atlas와 외부 AI Provider는 AWS 관리 리소스가 아니므로 일반 도형을 유지했다. 가비아 DNS는 ALB 라벨에 연결 출처로 표시했다.
- 유지한 구조: 1 page·16 vertex·10 edge와 기존 서비스 간 연결을 유지했고 모든 edge는 도형보다 뒤의 `edge-layer`에 있다.
- 유지한 외부 계약: 애플리케이션 코드와 공개 API·`BaseResponse`·AI Callback·S3·Redis·Billing 계약을 변경하지 않았다.
- 테스트·결과: `xmllint --noout`, mxCell ID 중복과 layer 순서를 확인했고 AWS4 resource icon style이 7개 대상에 적용된 것을 정적으로 검증했다. `git diff --check`도 실행한다.
- 남은 위험·배포 전 확인: 로컬 draw.io renderer CLI가 없어 실제 아이콘 렌더와 글자 간격은 diagrams.net에서 최종 육안 확인해야 한다. 사용 환경의 AWS4 stencil 지원 버전에 따라 매우 오래된 diagrams.net에서는 아이콘 fallback 여부를 확인한다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 수정하지 않았고 애플리케이션·AWS 설정 변경도 없다.
- 다음 작업 전 확인: 가비아 DNS도 별도 일반 아이콘으로 분리할지, 현재처럼 ALB 설명에 통합해 간결함을 유지할지 발표 화면에서 판단한다.
- 보안·범위: 계정·사용자 식별자, ARN, IP, Secret과 Token을 기록하지 않았고 Git commit·push, AWS 설정 변경과 배포를 수행하지 않았다.

## 2026-09-02 — 1차 업데이트 구성도 발표용 단순화

<!-- codex-turn:01a06104-c9f5-7cb2-bbb1-21f608eb8075 -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 draw.io 단순화 작업이다.
- 사용자 요청: 1차 업데이트 목표 시스템 구성도를 발표 화면에서 더 빠르게 이해할 수 있는 구조로 단순화한다.
- 변경한 파일: `docs/architecture/tosunsaeng-production-aws-simple.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 변경한 표현: VPC, Public/Private Subnet, NAT Gateway, ECR, GitHub Actions와 운영·관측 도형을 제거했다. 앱→DNS·ALB→공개 앱 서버, 네 개 Fargate 서비스, 데이터 저장소와 외부 AI 연동만 남겼다.
- 유지한 핵심 구조: Identity·Learning Core·AI·Billing은 각각 태스크 1개이며 AI에는 Worker가 없다. Learning Core↔AI의 Service Connect, Learning Core↔Billing의 VPC Lattice·SigV4, S3 Presigned 업로드, Valkey 상태·Lock과 서비스별 MongoDB Atlas Cluster는 유지했다.
- 단순화 결과: 도형은 25개에서 16개로, 연결선은 15개에서 10개로 줄였다. 별도 VPC Lattice 도형은 제거하고 연결선 라벨로 통합했다.
- 유지한 외부 계약: 애플리케이션 코드와 공개 API·`BaseResponse`·AI Callback·S3·Redis·Billing 계약을 변경하지 않았다.
- 테스트·결과: `xmllint --noout`이 성공했고 1 page·16 vertex·10 edge, mxCell ID 중복 없음, layer 순서와 모든 edge의 `edge-layer` 소속을 확인했다. `git diff --check`도 실행한다.
- 남은 위험·배포 전 확인: draw.io renderer CLI가 없어 PNG 렌더 기반 육안 검증은 하지 못했다. 발표 전에 diagrams.net에서 연결선과 라벨 간격만 확인해야 한다. 생략된 Subnet·NAT·배포·관측 구조는 운영 상세 설명용 별도 도식이 필요할 때만 다시 제공한다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 수정하지 않았다. 이번 작업 범위 밖의 애플리케이션·AWS·인프라 변경은 없다.
- 다음 작업 전 확인: 발표 자료가 시스템의 개념적 흐름을 묻는지 AWS 네트워크 상세를 묻는지에 따라 현재 간결본과 이전 상세 수준 중 사용할 버전을 결정한다.
- 보안·범위: 계정 ID·federation 사용자·ARN·IP·Secret·Token을 기록하지 않았고 Git commit·push, AWS 설정 변경과 배포를 수행하지 않았다.

## 2026-09-02 — AWS 아이콘 적용 작업 종료 동기화

- 날짜: 2026-09-02
- Jira: 별도 Jira 이슈가 없다.
- 완료 결과: `docs/architecture/tosunsaeng-production-aws-simple.drawio`의 ALB·Fargate 4개·S3·ElastiCache에 AWS4 Architecture 아이콘을 적용하고, 앱·Atlas·외부 Provider는 비AWS 일반 도형으로 유지했다.
- 검증 결과: XML 파싱, 7개 AWS icon style, 중복 ID 없음, 16 vertex·10 edge와 후면 edge layer, `git diff --check`를 확인했다.
- 외부 계약·변경 범위: 애플리케이션 코드·공개 API·AI·S3·Redis·Billing 계약과 실제 AWS 리소스는 변경하지 않았다. 기존 미추적 draw.io 파일 2개도 수정하지 않았다.
- 잔여 확인: diagrams.net에서 실제 아이콘 렌더와 글자 간격을 육안 확인한다.
- 보안·Git: Secret·Token·계정 식별 정보를 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-02 — AWS 아이콘 구성도 종료 훅 동기화

<!-- codex-turn:01a06108-827d-7d02-98d6-9adbf949aab1 -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 구성도 작업이다.
- 완료 결과: 1차 업데이트 간결 구성도의 ALB·Fargate 4개·S3·ElastiCache에 AWS4 Architecture 아이콘을 적용한 상태를 종료 기록에 동기화했다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`. 구성도 파일은 이번 종료 동기화에서 추가 수정하지 않았다.
- 유지한 동작·계약: 16개 도형·10개 연결선, 후면 edge layer와 기존 서비스 연결을 유지했다. 애플리케이션 코드와 공개 API·AI Callback·S3·Redis·Billing 계약을 변경하지 않았다.
- 검증: XML 파싱, AWS icon style 7개, 중복 ID와 layer 순서, `git diff --check`를 다시 확인한다. Gradle 대상 코드 변경이 없어 `./gradlew clean test`는 실행하지 않는다.
- 위험·배포 전 확인: diagrams.net에서 실제 아이콘 렌더와 글자 간격을 육안 확인해야 한다. 실제 AWS 설정·배포 변경은 수행하지 않았다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 수정하지 않았고 이번 종료 동기화에 예상 밖 범위는 없다.
- 다음 작업 전 확인: 발표 화면에서 가비아 DNS를 ALB 설명에 통합한 현재 표현을 유지할지 확인한다.
- 보안·Git: Secret·Token·계정 식별 정보를 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-02 — 토선생 이미지와 라벨 연결 방식 적용

<!-- codex-turn:01a0610d-9892-7ca2-86d4-82778ea64bb3 -->

- 날짜: 2026-09-02
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 draw.io 시각 개선 작업이다.
- 사용자 요청: 토선생은 제공한 토끼 PNG를 사용하고 다른 구성요소는 역할에 맞는 아이콘을 사용하며, 화살표 때문에 글자가 가려지지 않도록 화살표를 글자에 연결한다.
- 변경한 파일: `docs/architecture/tosunsaeng-production-aws-simple.drawio`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 변경한 표현: 제공된 2048×2048 PNG를 256×256로 축소한 68,167 byte PNG로 만들어 draw.io의 토선생 앱 이미지에 base64 내장했다. 외부 Desktop 파일 경로에 의존하지 않는다.
- 아이콘 체계: ALB·Fargate·S3·ElastiCache는 공식 AWS4 아이콘을 유지했다. MongoDB Atlas는 데이터베이스 실린더, 외부 AI Provider는 AWS 경계 밖의 별도 서비스 도형으로 유지해 공급자 소속을 혼동하지 않게 했다.
- 화살표 개선: 앱·ALB·Identity·Learning Core·AI·Billing·S3·Valkey의 아이콘과 라벨을 별도 도형으로 분리했다. 주요 8개 연결선의 source와 target을 라벨 도형으로 바꾸고 라벨에는 불투명한 흰 배경과 테두리를 적용해 선이 글자를 가리지 않게 했다.
- 유지한 구조: 1 page·10 edge와 후면 `edge-layer`, 앱→ALB→Identity/Learning, Learning↔AI·Billing·S3·Valkey, 앱→S3, ECS→Atlas 흐름을 유지했다. 라벨 분리로 vertex는 24개가 됐다.
- 유지한 외부 계약: 애플리케이션 코드와 공개 API·`BaseResponse`·AI Callback·S3·Redis·Billing 계약을 변경하지 않았다.
- 테스트·결과: `xmllint --noout`, 내장 base64의 PNG signature와 68,167 byte 복호화, 8개 label-to-label edge, ID 중복 없음, 모든 edge의 후면 layer 소속과 `git diff --check`를 확인했다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.
- 남은 위험·배포 전 확인: 로컬 draw.io renderer CLI가 없어 diagrams.net에서 토끼 이미지의 실제 크기·라벨 간격과 연결선 꺾임을 최종 육안 확인해야 한다. 제공 이미지 자체에 포함된 표식도 그대로 내장되어 있다.
- 예상 밖 변경: 기존 미추적 draw.io 파일 2개는 수정하지 않았고 작업 범위 밖의 코드·AWS 설정 변경은 없다.
- 다음 작업 전 확인: 발표 화면에서 토끼 이미지의 흰 여백과 원본에 포함된 표식을 그대로 사용할지 결정한다. 필요하면 사용자가 권리를 보유한 투명 배경 원본으로 교체한다.
- 보안·Git: Secret·Token·계정·사용자 식별 정보를 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-03 — Learning Core phone 재가입 시험 승계 인계안 검토

<!-- codex-turn:01a06571-ae0e-7530-bdb6-1d7a1034fb5e -->

- 날짜: 2026-09-03
- 현재 브랜치: `develop`
- Jira: Learning Core 전용 이슈는 아직 없다. 상류 Billing owner rebind 구현은 `TMI-120`이다.
- 사용자 요청: 첨부된 `Learning Core phone 재가입 시험 승계 구현 요청`을 현재 Learning Core 코드와 Billing 계약에 대조해 수정 방향을 검토한다.
- 검토 결론: 과거 `ExamSession`·답안·결과를 이전하지 않고 Billing이 명시적으로 승인한 기존 `AttemptGroup`·`mockExamId`에 새 target `examId`를 연결하는 새 방향은 타당하다. continuation 204 시 기존 INITIAL, 200 시 `PHONE_REJOIN` REPLACEMENT, 세 continuation field exact echo, 응답·status strict 검증과 SigV4/Lattice 경계도 Billing 계약과 일치한다.
- 필수 보완 1: phone 재가입은 Billing-only `TrialOwnerRebindApproved` lifecycle이며 ACTIVE GUEST→MEMBER의 `UserMerged` consumer와 다르다. 본 작업이 기존 `USER_MERGED_CONSUMER_IMPLEMENTATION_PLAN.md`의 Guest 학습 이력 migration을 대체하거나 폐기하지 않는다고 문서에 명시해야 한다.
- 필수 보완 2: continuation 조회 조건을 단순한 `INITIAL 예상`이 아니라 `같은 operation의 최초 PREPARED 생성이고 target userId의 ExamSession이 전혀 없음`으로 고정해야 한다. 기존 operation·durable Session replay에서는 continuation을 재조회하지 않아야 한다.
- 필수 보완 3: continuation 결과와 reserve exact payload은 존재하지 않는 별도 outbox가 아니라 `ExamCreationOperation`에 reserve 전 영속화한다. `continuationReason`, `continuationId`, `expectedAttemptGroupId`와 Billing이 반환한 `mockExamId`를 같은 operation의 불변 snapshot으로 두어 응답 유실·동시 요청에서도 같은 key·session·payload를 재사용해야 한다.
- 필수 보완 4: 현재 `SigV4BillingReservationClient` 공통 success decoder는 empty body를 계약 오류로 처리하므로 continuation 204 전용 분기가 필요하다. phone caller에는 `app.billing.phone-continuation-enabled=false` 성격의 별도 flag·startup validation을 두고 creation saga OFF 시 기존 흐름을 유지해야 한다.
- 필수 보완 5: reserve/status의 optional `continuationReason`·`continuationId`를 operation snapshot과 비교하고, phone 응답에서 누락·불일치나 예상 밖 kind를 거절해야 한다. 계약 불일치 시 응답의 untrusted `reservationId`를 바로 cancel하지 말고 `userId+operationId` status로 authoritative reservation을 재조회한 뒤 정합하는 경우만 cancel하도록 보상 규칙을 구체화해야 한다.
- 필수 보완 6: 현재 시험 생성용 `SigV4BillingReservationClient`에는 W3C context injection이 없으므로 첨부 문서의 `traceparent 전파 유지` 표현은 실제 구현과 다르다. 이번 작업에서 허용된 trace metadata만 unsigned request에 inject한 뒤 SigV4를 마지막 논리적 변경 단계에서 수행하고, 서명 후 header·URI·body를 변경하지 않는 component test를 추가한다고 명시해야 한다.
- 추가 테스트 권고: continuation 조회 응답 유실·시간 차로 204→200이 변해도 기존 operation snapshot이 바뀌지 않음, 같은 user의 다른 idempotency key 동시 요청, 204 empty-body strict handling, malformed 200, status optional field 누락·불일치, contract mismatch status-first cancel, flag OFF 회귀를 검증한다.
- 변경 파일: 검토 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·테스트·설정·Billing 저장소 파일은 수정하지 않았다.
- 유지한 외부 계약: 공개 API·`BaseResponse`·AI `user_id=examId`·Callback·S3·Redis·`retryCount`는 변경하지 않았다.
- 테스트·검증: 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. 첨부 문서, Learning Core saga·operation·SigV4 client와 Billing ADR·계약·phone continuation 구현을 읽기 전용으로 대조했고 문서 diff는 `git diff --check`로 검증한다.
- 남은 위험·배포 전 확인: Billing 참조 문서와 phone continuation 구현이 현재 Billing `develop` 작업 트리의 미커밋 변경이므로 Billing PR 병합 후 exact wire·error·IAM route를 다시 고정해야 한다. reader-first→Billing·Lattice→caller→Identity producer 순서와 staging E2E 전에 활성화하지 않는다.
- 예상 밖 변경: 기존 사용자 소유 draw.io 미추적 파일 3개와 기존 기록 문서 변경을 보존했으며 수정·삭제하지 않았다.
- 다음 작업 전 확인: 본 검토 보완을 반영한 Learning Core 전용 계획서와 신규 Jira를 먼저 확정한 뒤 구현한다.
- Git·배포: commit·push·PR·merge·배포를 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-03 — Billing phone continuation 병합 확인과 Learning Core 계획서 작성

<!-- codex-turn:01a06579-062f-7e43-81ad-ff133940d876 -->

- 날짜: 2026-09-03
- 현재 브랜치: `develop`
- Jira: 상류 Billing 구현은 `TMI-120`이며 Learning Core 전용 Jira는 아직 없다.
- 병합 확인: Billing 저장소의 `HEAD`, 로컬 `develop`, `origin/develop`이 모두 PR #8 merge commit `7138810`을 가리키며 작업 트리가 clean임을 확인했다. 실제 phone continuation 구현 commit은 `b61ebb9`이다.
- 상류 구현 확인: Billing에 `POST /internal/v1/reservations/continuations/phone`, `PHONE_REJOIN` discovery, reserve의 세 continuation field, reserve/status optional response field, payload hash와 security route 및 관련 테스트가 병합돼 있다.
- 작성 파일: `docs/codex/PHONE_REJOIN_CONTINUATION_IMPLEMENTATION_PLAN.md`를 신규 작성하고 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`를 갱신했다.
- 계획 핵심: target userId의 Session이 0건이고 같은 operation의 최초 준비일 때만 discovery를 실행한다. 204는 기존 INITIAL, 200은 Billing이 반환한 기존 AttemptGroup·mockExamId를 사용하는 새 target `examId`의 PHONE_REJOIN REPLACEMENT로 처리한다.
- 멱등성 계획: `continuationReason`, `continuationId`, expected group/mock을 reserve 전에 `ExamCreationOperation`의 불변 snapshot으로 저장한다. duplicate operation insert loser는 winner snapshot을 reload하며 PREPARED 이후 discovery·sessionId·mock 선택을 다시 하지 않는다.
- client·saga 계획: continuation의 정확한 204 empty-body 처리, strict 200 decoder, normal 3-field와 phone 6-field request 분리, reserve/status optional context 검증, reserve 응답 유실 status 복구와 contract mismatch의 untrusted reservationId 직접 cancel 금지를 포함했다.
- 데이터 경계: phone 재가입은 Billing-only `TrialOwnerRebindApproved` lifecycle이며 Guest `UserMerged` consumer를 대체하지 않는다. source Session owner, 답안, 결과, Summary, grading Job과 audio를 이전하거나 복사하지 않고 새 target Session은 cycleNumber 1로 시작한다.
- 보안·관측 계획: `app.billing.phone-continuation-enabled=false` 기본값과 creation saga 선행 조건, W3C 새 CLIENT span의 traceparent를 unsigned request에 넣은 뒤 SigV4를 마지막에 수행하는 순서, baggage·식별자·payload·credential 비로깅을 확정했다.
- 유지한 외부 계약: 기존 `POST /api/v1/exams`, Request Body 없음, 성공 DTO·`BaseResponse`, 실제 userId 비노출, `retryCount`, S3·Redis·Polling과 AI `user_id=examId` 계약을 변경하지 않았다.
- 테스트·검증: 문서 작업이므로 Gradle 테스트는 실행하지 않았다. Billing merge ref와 최종 계약·구현을 읽기 전용으로 확인했고 계획서의 wire·상태·오류·rollout 항목을 현재 Learning Core saga/client/operation과 대조했다. 문서 형식과 whitespace는 `git diff --check`로 검증한다.
- 남은 위험·배포 전 확인: Learning Core 전용 Jira를 생성하고 해당 범위의 `AGENTS.md` 허용을 추가해야 한다. 구현·staging E2E 전에는 flag를 켜지 않으며 Billing `7138810` 포함 버전의 실제 환경 배포와 Lattice exact route를 별도로 확인해야 한다.
- 예상 밖 변경: 기존 사용자 소유 미추적 draw.io 파일 3개와 이번 작업 이전부터 존재한 CURRENT_STATE·WORKLOG 변경을 보존했으며 수정·삭제하지 않았다. 애플리케이션·설정·테스트와 Billing 저장소는 변경하지 않았다.
- 다음 작업 전 확인: 계획서를 사용자 승인한 뒤 Learning Core 전용 Jira를 만들고 `AGENTS.md` 범위를 확정한 다음 구현한다.
- Git·배포: commit·push·PR·merge·배포, Jira 생성·상태 변경과 AWS 리소스 변경을 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-03 — Jira TMI-122 생성

<!-- codex-turn:01a06589-235a-7302-bbf5-8f91dbb9c563 -->

- 날짜: 2026-09-03
- 현재 브랜치: `develop`
- Jira: `TMI-122` `[Learning Core] phone 재가입 시험 continuation 연동`
- 생성 결과: TMI 프로젝트에 이슈 유형 `작업`, 상태 `해야 할 일`로 생성했고 assignee는 지정하지 않았다.
- 중복 확인: 프로젝트 내 phone·재가입·continuation 관련 기존 이슈를 조회했으며 동일한 Learning Core 후속 이슈는 없었다.
- Jira 본문: Billing `TMI-120`/PR #8을 배경으로 target Session 0건 discovery, 204 INITIAL, 200 PHONE_REJOIN REPLACEMENT, operation 불변 snapshot, strict client·reserve·status, 응답 유실 복구, status-first cancel, 데이터 격리, trace·SigV4·보안, 완료 조건, 필수 테스트와 rollout을 기록했다.
- 이슈 연결: link type `Blocks`를 사용해 `TMI-122 is blocked by TMI-120` 관계를 만들고 `TMI-122` 조회에서 outward issue `TMI-120` 연결을 확인했다.
- 상류 상태 주의: Billing 저장소에는 PR #8 merge commit `7138810`이 반영돼 있지만 Jira `TMI-120` 상태는 현재 `해야 할 일`이다. 사용자 요청 없이 `TMI-120` 상태를 변경하지 않았다.
- 계획서 갱신: `docs/codex/PHONE_REJOIN_CONTINUATION_IMPLEMENTATION_PLAN.md`의 Learning Core Jira를 `TMI-122`로 기록하고 Phase 0의 Jira 생성·상류 연결을 완료 표시했다. `AGENTS.md` 허용 범위 추가는 구현 전 남은 단계로 유지했다.
- 변경 파일: `docs/codex/PHONE_REJOIN_CONTINUATION_IMPLEMENTATION_PLAN.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 유지한 외부 계약: 애플리케이션 코드와 공개 API·`BaseResponse`·AI `user_id=examId`·Callback·S3·Redis·`retryCount`를 변경하지 않았다.
- 테스트·검증: 코드 변경이 없어 Gradle 테스트를 실행하지 않았다. 생성된 Jira의 key·summary·type·status·description과 issue link를 재조회했고 문서는 `git diff --check`와 marker 1회 검사로 검증한다.
- 남은 위험·배포 전 확인: 구현 전에 `TMI-122` 범위의 `AGENTS.md` 허용을 추가해야 한다. Billing `7138810` 포함 버전의 실제 환경 배포, Lattice exact route와 staging E2E 전에는 phone flag를 활성화하지 않는다.
- 예상 밖 변경: 기존 사용자 소유 미추적 draw.io 파일 3개와 이번 작업 이전부터 존재한 기록 문서 변경을 보존했으며 수정·삭제하지 않았다. 애플리케이션·설정·테스트 파일은 변경하지 않았다.
- 다음 작업 전 확인: 사용자가 구현 진행을 승인하면 먼저 `AGENTS.md`에 `TMI-122` 제한적 허용 범위를 반영한 뒤 계획서 순서로 구현한다.
- Git·배포: commit·push·PR·merge·배포와 AWS 리소스 변경을 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-03 — TMI-122 AGENTS 범위 의미 설명 및 구현 일시 중단

<!-- codex-turn:01a0658e-fd29-76d3-93ff-ca1f8adc4b53 -->

- 날짜: 2026-09-03
- 현재 브랜치: `develop`
- Jira: `TMI-122` `[Learning Core] phone 재가입 시험 continuation 연동`
- 사용자 질문: 기존 `AGENTS.md`가 phone 재가입 owner continuation을 허용하지 않는다는 표현의 의미를 설명한다.
- 설명 결과: 이는 제품 기능 자체를 금지한다는 뜻이 아니다. 기존 TMI-116 예외가 최초 Billing Reservation saga의 reserve·confirm·cancel·status와 당시 확정 범위에만 적용되고 다른 Jira에 자동 적용되지 않는다는 저장소 작업 거버넌스 의미다.
- 기존 경계: TMI-116은 UserMerged·owner rebind와 후속 확장을 제외했고 AttemptGroup 영구 허용도 UserMerged·owner rebind 및 Billing saga 전체 reconciliation을 자동 허용하지 않는다. 따라서 Codex가 새 계약을 별도 승인 없이 구현하지 못하도록 막혀 있었다.
- 현재 승인: 사용자가 phone continuation 계획을 승인하고 Jira `TMI-122` 생성과 구현을 명시적으로 요청했으므로 직전 중단된 구현 턴에서 `AGENTS.md`에 TMI-122 전용 제한적 예외를 추가했다.
- TMI-122 예외가 허용하는 것: Billing continuation discovery, operation 내부 snapshot, phone 6-field reserve, reserve/status strict 검증·복구, 새 target Session의 기존 AttemptGroup 연결, default-off flag, traceparent와 SigV4 최종 서명 및 관련 테스트다.
- 계속 금지하는 것: source Session·답안·결과 이전, TrialOwnerRebindApproved consumer, Guest UserMerged 변경, Billing 저장소 수정, 공개 API·AI·S3·Redis 계약 변경과 실제 AWS 리소스 변경이다.
- 구현 상태: 사용자의 `잠깐` 요청에 따라 애플리케이션 구현을 진행하지 않았다. 현재 이번 구현 시도에서 바뀐 것은 `AGENTS.md`의 TMI-122 범위뿐이다.
- 변경 파일: `AGENTS.md`, 기록용 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 이전 작업에서 생성된 계획서와 사용자 소유 draw.io 파일은 보존했다.
- 유지한 외부 계약: 공개 API·`BaseResponse`·AI `user_id=examId`·Callback·S3·Redis·`retryCount`와 Billing wire를 변경하지 않았다.
- 테스트·검증: 애플리케이션 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. `AGENTS.md` diff와 TMI-116·TMI-122·AttemptGroup 범위를 대조하고 문서는 `git diff --check`와 marker 1회 검사로 검증한다.
- 남은 위험·배포 전 확인: 구현은 아직 시작하지 않았으며 사용자가 계속 진행을 확인하면 TMI-122 계획서 순서로 재개한다. feature flag 활성화·AWS·배포 작업은 범위 밖이다.
- 예상 밖 변경: 기존 사용자 소유 미추적 draw.io 파일 3개와 이전 CURRENT_STATE·WORKLOG·계획서 변경을 수정·삭제하지 않았다.
- Git·배포: commit·push·PR·merge·배포, Jira 상태 변경과 AWS 리소스 변경을 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-03 — phone continuation 영구 허용 범위 검토

<!-- codex-turn:01a06590-a78f-7883-a3b3-acc628e25e24 -->

- 날짜: 2026-09-03
- 현재 브랜치: `develop`
- Jira: `TMI-122` `[Learning Core] phone 재가입 시험 continuation 연동`
- 사용자 질문: TMI-122를 Jira 단건 예외로 두지 않고 `AGENTS.md`에서 일반적으로 허용하는 방식이 나은지 검토한다.
- 결론: 모든 Billing·owner rebind 변경을 포괄적으로 허용하는 것은 권장하지 않는다. 결제 권리, 과거 데이터 소유권, 서버 간 인증과 보상 로직까지 별도 승인 없이 확대될 수 있기 때문이다.
- 권장안: phone 재가입 시험 continuation이라는 확정 capability 자체와 동일 경계 안의 후속 버그 수정·테스트·운영 안정화를 영구 허용한다. TMI-122는 최초 구현 이력과 기준 계획서로 기록하되 허용 효력은 특정 Jira에 종속시키지 않는다.
- 유지할 허용 범위: Billing continuation discovery, operation snapshot, phone 6-field reserve, reserve/status 검증·복구, 새 target Session 연결, default-off flag, W3C trace와 SigV4 최종 서명이다.
- 유지할 금지 범위: 전체 Billing 기능, 새로운 entitlement·결제 정책, source 시험 데이터 이전, TrialOwnerRebindApproved/UserMerged consumer, Billing 저장소 수정, 공개 API·AI·S3·Redis 계약 변경과 AWS 리소스 직접 변경이다.
- 문서 권고: 현재 `## TMI-122 명시적 예외`를 `# Phone 재가입 시험 continuation 구현 허용 규칙`으로 바꾸고, TMI-122와 계획서를 최초 기준으로 연결하며 후속 작업도 동일 경계 안에서만 허용한다고 명시한다.
- 변경 파일: 검토 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. `AGENTS.md`의 직전 TMI-122 예외와 애플리케이션 코드는 이번 작업에서 추가 수정하지 않았다.
- 테스트·검증: 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. 기존 TMI-116·TMI-122·AttemptGroup 허용/금지 경계를 문서상 대조하고 `git diff --check`와 marker 1회 검사를 수행한다.
- 외부 계약·보안: 공개 API·`BaseResponse`·AI `user_id=examId`·S3·Redis·`retryCount`와 Billing wire를 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 남은 확인: 사용자가 권장안을 승인하면 `AGENTS.md`의 TMI-122 단건 예외를 capability 기반 영구 허용 절로 교체한 뒤 구현을 재개한다.
- 예상 밖 변경: 기존 사용자 소유 미추적 draw.io 파일 3개와 이전 계획·기록 변경을 보존했고 수정·삭제하지 않았다.
- Git·배포: commit·push·PR·merge·배포, Jira·AWS 상태 변경을 수행하지 않았다.

## 2026-09-03 — TMI-122 phone 재가입 시험 continuation 구현

<!-- codex-turn:01a06592-36fc-7041-a4a9-0471c1762732 -->

- 날짜: 2026-09-03
- 현재 브랜치: `feat/TMI-122-phone-rejoin-continuation`
- Jira: `TMI-122` `[Learning Core] phone 재가입 시험 continuation 연동`
- 범위 규칙: `AGENTS.md`의 TMI-122 단건 예외를 `Phone 재가입 시험 continuation 구현 허용 규칙`으로 교체했다. 확정 capability와 동일 경계의 후속 버그 수정·테스트·운영 안정화는 영구 허용하지만 Billing 전체, source 데이터 이전, TrialOwnerRebindApproved/UserMerged consumer와 Billing 저장소 수정은 계속 제외한다.
- discovery와 준비: `phone-continuation-enabled`가 켜지고 target user 소유 ExamSession이 0건일 때만 Billing `POST /internal/v1/reservations/continuations/phone`을 호출한다. body 없는 204는 기존 INITIAL 흐름, strict 200은 Billing 기존 group/mock을 쓰는 cycle 1 PHONE_REJOIN REPLACEMENT 준비로 연결한다.
- operation 멱등성: `ExamCreationOperation`에 `continuationReason`, `continuationId`, expected group/mock snapshot을 reserve 전에 저장한다. 일반 INITIAL·local REPLACEMENT·PHONE_REJOIN 조합을 strict 검증하고 같은 key insert 경쟁에서는 기존 unique winner reload 흐름을 유지한다.
- Billing wire: 일반 reserve는 기존 3필드, phone reserve는 continuation 세 필드를 더한 정확한 6필드로 분리했다. reserve·status response의 optional reason/id를 reader-first로 해석하고 200/204 discovery, unknown·duplicate·trailing·coercion, response size와 redirect 금지 규칙을 기존 strict mapper·HTTP 경계에 연결했다.
- 복구와 보상: PREPARED reserve의 timeout·일시 실패·processing·contract error는 status를 먼저 조회한다. exact RESERVED만 operation을 전진시키고, 계약 불일치는 응답의 reservationId를 사용하지 않은 채 authoritative status가 증명한 ID로만 CANCEL_PENDING을 저장한다. phone stale context 409는 terminal failure로 수렴한다.
- Session 격리: 새 target Session은 새 examId, target userId, cycle 1, Billing 기존 AttemptGroup·mockExamId와 REPLACEMENT kind를 저장한다. source Session owner, 답안, 결과, Summary, grading Job과 audio는 조회·복사·수정하지 않는다.
- 보안·관측: Billing 호출마다 현재 server span의 child CLIENT span을 만들고 그 span의 `traceparent`만 unsigned request에 넣은 뒤 SigV4를 마지막 논리적 변경으로 수행한다. baggage를 전파하지 않고 user/session/group/continuation ID와 payload·credential 없는 고정 outcome 로그와 저카디널리티 counter/timer를 추가했다. 401/403은 body code보다 우선해 `AUTH_FAILURE`로 분류한다.
- 설정: `BILLING_PHONE_CONTINUATION_ENABLED` 기본값은 false이며 creation saga가 꺼진 상태에서 phone flag만 켜면 startup validation이 실패한다. local/test도 false를 유지한다.
- 변경 파일: `AGENTS.md`, phone 계획서·Billing saga 계획서, Billing client·properties·configuration·validator, `BillingExamCreationSaga`, `ExamCreationOperation`, `ExamSessionManager`, `ExamSessionRepository`, 신규 `BillingContinuationReason`, application 설정과 관련 테스트, CURRENT_STATE·WORKLOG.
- 유지한 외부 계약: `POST /api/v1/exams` URL·Method·body 없는 요청·성공 DTO·BaseResponse·optional Idempotency-Key 규칙, 실제 userId 비노출, 시험 retryCount, S3·Redis·Polling과 Python AI `user_id=examId` 및 Callback 계약은 변경하지 않았다.
- 테스트·검증: phone 204/200 discovery, exact 6필드 reserve, optional echo, target history gate, operation snapshot, reserve 응답 유실 status 복구, status continuation 누락 거절, untrusted reservationId 미사용, target Session group/mock 연결, flag 조합, client span traceparent의 SigV4 포함을 추가·검증했다. `./gradlew clean test` 전체 457개가 성공했고 `git diff --check`도 통과했다.
- 남은 위험·배포 전 확인: Billing `7138810` 포함 이미지 배포, Lattice exact phone route IAM, Mongo index·Transaction 환경과 204/200/응답 유실/stale/trace staging E2E 전에는 phone flag를 켜지 않는다. reader-first 배포 후 caller flag를 canary로 활성화해야 한다.
- 예상 밖 변경: 기존 사용자 소유 미추적 draw.io 파일 3개를 수정·삭제하지 않았다. 이번 기능 밖의 애플리케이션 리팩터링과 Billing 저장소 변경은 없다.
- Git·배포: commit·push·PR·merge·배포, Jira 상태 변경과 AWS 리소스 변경을 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-03 — 현재 앱 전체 사용자·서비스 흐름 정리

<!-- codex-turn:01a065a5-a8cd-7bb0-ae41-cd833f345109 -->

- 날짜: 2026-09-03
- 브랜치: `develop`
- Jira: 관련 구현 이력은 `TMI-116`, `TMI-118`, `TMI-122`이며 이번 분석에서 Jira를 조회하거나 상태를 변경하지 않았다.
- 인증 흐름은 Identity의 LOCAL·Guest·Firebase SNS에서 Identity Access/Refresh Token을 발급하고 앱이 Access Token만 Learning Core Bearer로 전달하는 구조다. 실제 userId는 앱 Request/Response에 추가하지 않는다.
- 시험 생성은 새 lowercase UUID v4 Idempotency-Key를 사용한다. Billing saga flag가 켜지면 reserve → ExamSession durable commit → confirm으로 권리를 확정하며 transport retry는 같은 key를 재사용한다. flag off이면 기존 session 생성 흐름이고 새 시험 시작 시 기존 진행 Session은 ABANDONED되어 이어풀기하지 않는다.
- 문항 흐름은 prompt → Presigned URL → S3 raw audio PUT → submit → 문항/시험 polling → Summary·문항 결과·이력 조회다. Part 4 표는 비정형 tableContext JSON으로 앱에 전달하고 table image는 공개하지 않는다. AI는 examId를 user_id로 사용하며 비동기 Callback과 Job 멱등성을 유지한다.
- Billing-linked 시험은 최초 retryCount=0의 제출·채점·Summary 증거로 AttemptGroup GRADING/COMPLETED/RETAKE_AVAILABLE을 판정하고 Transaction/CAS outbox를 통해 Billing에 전달한다. phone 재가입 target Session이 0건이면 별도 flag 아래 기존 AttemptGroup·mockExam에 새 examId만 연결하며 과거 답안·결과·audio는 복사하지 않는다.
- Billing creation saga, phone continuation, AttemptGroup writer/publisher와 UserWithdrawn consumer 관련 flag는 기본 off다. 코드가 develop에 병합된 것과 production 사용자 흐름 활성화는 구분해야 하며 Lattice/IAM/Mongo migration·Billing consumer·staging failure E2E가 남아 있다.
- 결제 상품·스토어 구매/복원, Guest UserMerged owner rebind와 10초 챌린지 공개 API는 아직 완성·활성화된 앱 흐름이 아니다. Challenge는 v1 계약과 콘텐츠만 준비된 상태다.
- 분석과 기록만 수행했다. Java·테스트·공개 API·BaseResponse·AI/Callback·S3·Redis·AWS·DB·Git 이력을 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-09-03 — 앱 기능별 동작 로직 종합 정리

<!-- codex-turn:01a065ac-25db-78d2-b608-6d726d513070 -->

- 날짜: 2026-09-03
- 브랜치: `develop`
- Jira: 관련 구현 이력은 `TMI-116`, `TMI-118`, `TMI-122`이며 이번 정리에서 Jira를 조회하거나 상태를 변경하지 않았다.
- 신규 `docs/codex/APP_FEATURE_LOGIC_OVERVIEW.md`에 5줄 결론, 필독 상태표, 사용자 결정, 위험, 기능별 사용자 동작·서버 로직·실패 처리와 서비스 책임·rollout 부록을 작성했다.
- Identity LOCAL·Guest·Firebase SNS, 프로필·동의·탈퇴, Billing 무료 TrialClaim·Reservation, Learning Core 시험 생성·ABANDONED restart·문제·S3·AI·Summary·복구·AttemptGroup, phone continuation을 구현 사실과 기본 비활성 상태로 구분했다.
- 인앱결제·구매 복원, Guest UserMerged owner 이전과 10초 Challenge는 구현 완료 기능이 아니라 후속 또는 계약 완료 상태로 명시했다.
- 분석·문서 작업만 수행했다. Java·테스트·공개 API·BaseResponse·AI/Callback·S3·Redis·AWS·DB·Git 이력을 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-09-03 — Firebase 가입의 필수 휴대전화 인증 경계 정정

<!-- codex-turn:01a065be-3a1a-7232-943a-525cdb081d05 -->

- 날짜: 2026-09-03
- 브랜치: `develop`
- Jira: 신규 Jira 키는 없으며 Jira를 조회하거나 상태를 변경하지 않았다.
- 사용자 지적에 따라 `APP_FEATURE_LOGIC_OVERVIEW.md`의 “필요하면 phone credential 연결” 표현을 “신규 MEMBER 가입 또는 Guest 승격 전에 동일 Firebase UID에 반드시 연결”로 정정했다.
- Identity 실제 verifier는 enrollment evidence가 필요한 목적에서 linked PHONE, phoneVerified와 nonblank verifiedPhoneNumber를 모두 요구하고 하나라도 없으면 `FIREBASE_PHONE_VERIFICATION_REQUIRED`로 거절한다. signup과 Guest upgrade는 검증 번호를 정규화·fingerprint하여 PhoneIdentity와 eligibility candidate를 확정한다.
- 필수 인증은 매 로그인마다 반복한다는 뜻이 아니다. 가입을 완료한 기존 MEMBER는 일반 exchange에서 기존 PhoneIdentity를 사용하며, phone 변경·재가입 등 새 proof가 필요한 흐름에서만 재인증한다. phone-only 로그인과 phone 기반 자동 merge는 계속 금지한다.
- 분석·문서 정정만 수행했다. Java·테스트·공개 API·BaseResponse·AI/Callback·S3·Redis·AWS·DB·Git 이력을 변경하지 않았고 Secret·Token을 기록하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. `git diff --check`와 marker 단일 포함을 검증한다.

## 2026-09-03 — 미완료 시험의 무제한 무료 재시작 계약 정정

<!-- codex-turn:01a065c7-8ab9-7af3-b50f-912a238eb6a7 -->

- 날짜: 2026-09-03
- 브랜치: `develop`
- Jira: 관련 구현 이력은 `TMI-116`, `TMI-118`이며 이번 작업에서 Jira를 조회하거나 상태를 변경하지 않았다.
- 사용자 지적에 따라 `APP_FEATURE_LOGIC_OVERVIEW.md`의 “단순 앱 종료는 무료 replacement의 충분조건이 아니다”라는 잘못된 설명을 확정 계약에 맞게 정정했다.
- 앱 종료 자체는 즉시 Session을 폐기하지 않지만, 이후 사용자가 새 시험 시작을 요청했을 때 AttemptGroup이 `OPEN`이면 기존 Session을 `ABANDONED_RESTARTED` 처리하고 같은 consumption·attemptGroupId·mockExamId로 횟수·기간 제한 없는 무료 REPLACEMENT를 만든다.
- 필수 제출을 모두 마친 `GRADING` 상태에서는 새 Session을 잠시 막고 기존 채점·Summary 복구를 우선한다. 복구가 최종 실패해 `RETAKE_AVAILABLE`이 되면 무료 REPLACEMENT를 허용하며, 필수 feedback·유효 점수와 조회 가능한 Summary가 모두 있는 `COMPLETED`에서만 다음 시험에 새 entitlement가 필요하다.
- 재시작은 이어풀기가 아니므로 매번 새 examId·새 Idempotency-Key로 처음부터 시작하고 기존 답안·결과·업로드·Job·Summary·audio를 복사하지 않는다.
- 변경 파일: `docs/codex/APP_FEATURE_LOGIC_OVERVIEW.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 애플리케이션 코드는 변경하지 않았고 공개 API·BaseResponse·AI `user_id=examId`·S3·Redis·Billing wire 계약도 변경하지 않았다.
- 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. `git diff --check`와 WORKLOG marker 단일 포함 및 항목의 EOF 위치를 검증한다.
- commit·push·PR·merge·배포와 AWS·DB 상태 변경을 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-03 — 미완료 시험 재시작의 사용자 표현 명확화

<!-- codex-turn:01a065ca-3c6c-7587-832a-680c7b61ad9b -->

- 날짜: 2026-09-03
- 브랜치: `develop`
- Jira: 관련 구현 이력은 `TMI-116`, `TMI-118`이며 Jira를 조회하거나 상태를 변경하지 않았다.
- “무제한 무료 새 시험”이 별도의 무료 응시권을 반복 지급하는 의미로 오해될 수 있어 “최초 응시에서 1회만 차감하고, 완료할 때까지 추가 차감 없이 처음부터 재시작”으로 표현을 통일했다.
- `OPEN`과 `RETAKE_AVAILABLE`에서는 동일 consumption·AttemptGroup·mockExamId를 유지하고 새 examId의 Session으로 교체한다. `GRADING`에서는 기존 채점·Summary 복구를 우선하며 `COMPLETED` 이후에만 다음 entitlement가 필요하다.
- 애플리케이션 로직과 공개 API·BaseResponse·AI·S3·Redis·Billing wire 계약은 변경하지 않았다.
- 변경 파일은 `docs/codex/APP_FEATURE_LOGIC_OVERVIEW.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`이며 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.
- `git diff --check`, marker 단일 포함과 WORKLOG 항목의 EOF 위치를 검증한다.

## 2026-09-03 — 미완료 시험 재시작 표현 정정 종료 훅 동기화

<!-- codex-turn:01a065ca-6e51-7e61-b821-8720bbbe55f7 -->

- 날짜: 2026-09-03
- 브랜치: `develop`
- Jira: 관련 구현 이력은 `TMI-116`, `TMI-118`이며 Jira를 조회하거나 상태를 변경하지 않았다.
- 이번 턴에 확정한 사용자 표현은 “최초 응시에서 1회만 차감하고, 완료할 때까지 추가 차감 없이 처음부터 재시작”이다.
- 새로운 무료 응시권을 반복 지급하는 의미가 아니며, `OPEN` 또는 `RETAKE_AVAILABLE`인 동일 consumption·AttemptGroup·mockExamId에 새 examId의 Session을 연결한다.
- `GRADING`에서는 기존 채점·Summary 복구를 우선하고 `COMPLETED` 이후에만 다음 entitlement가 필요하다는 경계는 유지한다.
- 종료 훅 기록 동기화만 수행했으며 애플리케이션 코드, 공개 API, BaseResponse, AI·S3·Redis·Billing wire 계약은 변경하지 않았다.
- Secret·Token을 기록하지 않았고 commit·push·PR·merge·배포와 AWS·DB 상태 변경을 수행하지 않았다.

## 2026-09-03 — 1차 업데이트 진행 체크리스트 최신화

<!-- codex-turn:01a065ca-ef74-7e21-84d4-e6e250c5c756 -->

- 날짜: 2026-09-03
- 브랜치: Learning Core·Identity·Billing 모두 `develop`; Identity에는 `TMI-123` 관련 미커밋 구현 작업이 존재한다.
- Jira: Identity `TMI-109`·`TMI-111`·`TMI-114`·`TMI-123`, Billing `TMI-110`·`TMI-112`·`TMI-113`·`TMI-115`·`TMI-117`·`TMI-120`, Learning Core `TMI-116`·`TMI-118`·`TMI-122`, Challenge `TMI-102`·`TMI-105`·`TMI-106`을 현재 상태 근거로 사용했다. Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 세 저장소의 최신 merge commit, 구현 파일, 작업 트리와 최근 Jira·테스트 기록을 대조해 `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`의 완료·진행·미착수·출시 차단 판정을 2026-09-03 기준으로 갱신한다.
- 변경 파일: `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드, Identity·Billing 저장소, Jira, AWS와 DB는 이번 점검에서 변경하지 않았다.
- 최신 완료 반영: Learning Core `TMI-118` outbox/publisher와 Summary Transaction hotfix는 PR #25·#26 및 444개 테스트·Jira 완료, Billing `TMI-120` owner rebind/continuation은 PR #6~#8 병합, Learning Core `TMI-122` phone continuation은 PR #27·457개 테스트와 `develop` 병합으로 반영했다.
- 현재 판정: 무료시험의 reserve→Session commit→confirm, GRADING/COMPLETED/RETAKE_AVAILABLE 전달, 추가 차감 없는 실패 재시작과 phone 재가입 target Session 생성 코드는 양쪽 서버에 대부분 존재한다. Identity `TMI-123` fan-out/SigV4는 작업 트리 구현 진행 중이며 완료·병합 전이고 Learning Core `UserMerged`, 실제 Lattice·Mongo·staging 검증과 Challenge backend는 남아 있어 production 출시는 차단 상태다.
- 유지한 외부 계약: 기존 공개 API URL·Method·Request/Response·`BaseResponse`, 실제 userId 비노출, 기존 시험 retryCount·S3·Redis와 Python AI request/Callback `user_id=examId` 계약을 변경하지 않았다. Secret과 Token을 기록하지 않았다.
- 테스트·검증: 이번 작업은 문서 상태 점검이므로 Gradle 테스트를 실행하지 않았다. 세 저장소의 현재 branch·commit·구현 및 최근 테스트 기록을 읽기 전용으로 확인했고 종료 전에 링크·trailing whitespace·marker 단일 포함과 `git diff --check`를 검증한다.
- 남은 위험·배포 전 확인: `TMI-123` 구현·테스트·병합, Billing `TMI-120` replica-set Testcontainers 4개, Learning Core `UserMerged`, 환경별 Mongo migration·Lattice/IAM/SG·multi-instance·response-loss E2E, 모바일 SNS와 Challenge 종단 검증 전에는 관련 feature flag를 활성화하지 않는다.
- 예상 밖 diff: Learning Core에는 이번 작업 전부터 `docs/codex/APP_FEATURE_LOGIC_OVERVIEW.md` 미추적 파일과 기존 CURRENT_STATE/WORKLOG 변경이 있었고, Identity에는 `TMI-123` 애플리케이션·설정·테스트·신규 파일 작업이 진행 중이며, Billing에는 기존 기록·계약 작업이 있었다. 모두 보존하고 수정하지 않았다.
- 다음 작업: Identity `TMI-123`을 먼저 완료·병합하고 Billing `TMI-120` replica-set 검증과 Jira 상태를 정리한 뒤 Learning Core `UserMerged`, cross-service staging E2E, Challenge backend 순으로 진행한다. Git commit과 push는 사용자가 수행한다.

## 2026-09-03 — 미제출 시험의 AttemptGroup 상태 경로 명확화

<!-- codex-turn:01a065ce-2e2f-79c3-9a9c-e246b49315de -->

- 날짜: 2026-09-03
- 브랜치: `develop`
- Jira: 관련 구현 이력은 `TMI-116`, `TMI-118`이며 Jira를 조회하거나 상태를 변경하지 않았다.
- 사용자 질문에 따라 AttemptGroup 상태표에 `OPEN`을 추가하고 미제출 중단과 채점 실패의 상태 전이를 구분했다.
- 필수 retry 0 제출이 모두 접수되지 않은 시험은 `GRADING`에 진입하지 않고 `OPEN`에 남는다. 다시 시작하면 `OPEN → OPEN` Session 교체로 기존 Session을 `ABANDONED_RESTARTED` 처리하고 추가 차감 없이 새 examId로 처음부터 시작한다.
- 모든 필수 제출과 durable Question Job이 확인된 뒤에만 `OPEN → GRADING`으로 전환한다. 그 뒤 채점 retry 소진, Summary 복구 불가 또는 deadline 초과 시 `RETAKE_AVAILABLE`로 전환한다.
- 데이터 중복 등 자동 복구 불가능한 결과 정합성 위반은 잘못된 완료 결과를 노출하지 않기 위해 발견 시점과 무관하게 즉시 `RETAKE_AVAILABLE`로 종료할 수 있음을 함께 명시했다.
- 변경 파일: `docs/codex/APP_FEATURE_LOGIC_OVERVIEW.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 애플리케이션 코드와 공개 API·BaseResponse·AI·S3·Redis·Billing wire 계약은 변경하지 않았고 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.
- Secret·Token을 기록하지 않았으며 commit·push·PR·merge·배포와 AWS·DB 상태 변경을 수행하지 않았다.

## 2026-09-03 — Learning Core UserMerged consumer 역할 설명

<!-- codex-turn:01a065d0-73d1-7082-be36-7086589870a3 -->

- 날짜: 2026-09-03
- 브랜치: `develop`
- Jira: Learning Core 전용 이슈 키는 아직 없으며, 선행 Identity 발행·fan-out 작업은 `TMI-123`이다.
- 사용자 요청: 앞으로 구현할 Learning Core `UserMerged` consumer가 필요한 이유와 동작 범위를 쉽게 설명한다.
- 확인 결과: consumer는 Identity가 Guest source를 MEMBER target으로 최종 병합한 뒤 보내는 schema v1 event를 받아 source의 학습 데이터 소유권과 API actor 권한을 target으로 안전하게 수렴시키는 내부 기능이다.
- 핵심 처리: 한 Mongo Transaction에서 source/target ownership guard를 함께 획득하고, 활성 시험 충돌 정책을 적용하며, `exam_sessions`, `exam_results`, `exam_summaries`의 직접 `userId`를 target으로 이전하고 source guard를 `MERGED`로 차단한 뒤 inbox event를 `PROCESSED`로 기록한다.
- 유지 범위: `examId`는 변경하지 않으며 Question/Summary Job, Azure/SpeechAce 결과, Redis key와 S3 object는 `examId` 간접 귀속이므로 이동하지 않는다. 기존 공개 API·`BaseResponse`, `retryCount`, S3·Redis와 Python AI `user_id=examId` 계약도 유지한다.
- 활성 시험 정책: target 활성 시험이 있으면 target을 우선하고 source 활성은 폐기 이력으로 이전한다. target 활성 없이 source만 활성이라면 같은 `examId`를 유지한 채 target 소유로 넘긴다. 완료·폐기 이력은 합집합으로 보존한다.
- 동시성·멱등성: 모든 user-owned write와 Callback이 ownership guard를 같은 Transaction에서 touch해야 merge와 경합해도 source 또는 target 한쪽으로 일관되게 수렴한다. 같은 eventId·같은 payload 재전송은 `204` no-op, 상충 event는 `409`, 일시적 DB 경합은 `503`으로 처리하는 계획이다.
- 보안: 사용자 Access Token과 분리된 workload JWT/JWKS로 internal endpoint를 보호하며, source 계정의 기존 사용자 Token은 merge commit 이후 Learning Core API에서 거절한다. merge 전에 발급된 S3 PUT URL은 v1 정책상 최대 5분 잔여 가능성을 수용한다.
- 선행 조건: Identity `TMI-123` 발행·consumer별 fan-out 완료, workload issuer/JWKS/audience/principal 운영값 확정, Mongo Transaction 지원·성능 gate, guard backfill·구버전 writer drain과 staging E2E가 필요하다. 별도 Learning Core Jira를 만든 뒤 구현하는 것이 다음 단계다.
- 변경 파일: 설명 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션 코드·설정·테스트, Jira, AWS와 DB는 변경하지 않았다.
- 테스트·검증: 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 문서 diff와 marker 단일 포함을 검증한다. Secret·Token을 기록하지 않았고 git commit·push·PR·merge·배포를 수행하지 않았다.

## 2026-09-03 — TMI-125 Learning Core UserMerged consumer Jira 생성

<!-- codex-turn:01a065d6-1077-7d51-987a-e04c8676bc02 -->

- 날짜: 2026-09-03
- 브랜치: `develop`
- Jira: `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`
- 사용자 요청: 확정된 Learning Core `UserMerged` consumer 계획을 구현 작업으로 관리할 Jira를 생성한다.
- 생성 결과: TMI 프로젝트에 `작업` 유형, `해야 할 일`, Medium 우선순위, 미할당 상태로 `TMI-125`를 생성했고 보드와 생성 성공 알림에서 이슈 키·제목·상태를 확인했다.
- 이슈 범위: Identity `TMI-123`을 선행 작업으로 명시하고 internal endpoint, schema 검증, 4 KiB body 제한, inbox 멱등성, source/target ownership guard, Mongo Transaction owner migration, 활성 시험 충돌 정책, user-owned writer·Callback guard 전환과 workload JWT 보안을 기록했다.
- 완료 기준: duplicate/conflict/status 계약, Transaction rollback·unknown commit 수렴, writer·Callback 경합, target 우선·source-only 이전·history 합집합, source actor 차단, 공개 API·AI·S3·Redis 계약 불변과 전체 Gradle 테스트를 포함했다.
- 배포 gate: Identity publisher/fan-out과 workload 계약, Mongo Transaction·index, guard-aware writer 선배포, 구버전 drain·backfill, direct Transaction 성능과 staging E2E 전에는 consumer flag를 활성화하지 않도록 기록했다.
- 제외 범위: Identity·Billing 서버 수정, phone continuation 변경, 공개 API·DTO·AI·S3·Redis 계약 변경, Presigned URL 즉시 취소, Kafka/SQS, AWS 배포와 Git commit·push를 포함하지 않았다.
- 변경 파일: `docs/codex/USER_MERGED_CONSUMER_IMPLEMENTATION_PLAN.md`, `docs/codex/USER_MERGED_CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·설정·테스트는 변경하지 않았다.
- 테스트·검증: 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. Jira 생성 결과, 문서 Jira 키, `git diff --check`와 marker 단일 포함을 검증한다.
- Git·배포: commit·push·PR·merge·배포와 AWS·DB 상태 변경을 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-03 — TMI-125 선행 조건 상세 확인

- 날짜: 2026-09-03
- 브랜치: Learning Core `develop`; Identity `feat/TMI-123-owner-event-fanout-sigv4`
- Jira: Learning Core `TMI-125`, 선행 Identity `TMI-123`
- 사용자 질문: Identity 발행·fan-out, workload JWT 운영값과 Mongo Transaction 지원에서 구체적으로 무엇을 확인해야 하는지 설명한다.
- TMI-123 현재 상태: Jira 보드에서는 `해야 할 일`이고 Identity 로컬 브랜치에는 owner event core/delivery/publisher와 관련 설정·테스트가 미커밋 상태로 존재한다. 따라서 구현 방향은 보이지만 전체 테스트·commit·PR merge·환경 배포가 확인될 때까지 선행 완료로 간주할 수 없다.
- fan-out 확인 기준: `UserMerged` 한 core에서 BILLING과 LEARNING_CORE delivery가 각각 독립 생성되고 consumer별 lease·retry·dead-letter·성공 상태가 분리돼야 한다. `TrialOwnerRebindApproved`는 Billing에만 가야 하며 legacy와 신규 capture가 한 merge에서 중복 생성되면 안 된다.
- wire 불일치: Identity 로컬 `LearningCoreOwnerEventDeliveryAdapter`는 exact path `/internal/v1/owners/merge/events`를 요구하지만 현재 TMI-125 계획은 `/internal/v1/events/user-merged`다. 구현 전에 한 경로로 계약과 양쪽 테스트를 고정해야 한다.
- JWT 불일치: Identity의 기존 `JwtWorkloadIdentityCredentialProvider`는 `WorkloadJwtProperties`의 단일 audience `learning-core-user-withdrawn`만 발급하도록 고정돼 있다. UserMerged 전용 audience를 쓰려면 임의 audience가 아니라 명시적 다중 allowlist 또는 별도 credential profile로 확장해야 한다.
- 권장 workload profile: RS256, 환경별 HTTPS workload issuer, 기존 Identity JWKS, UserMerged 전용 audience 예시 `learning-core-user-merged`, principal claim/value `sub=identity-service`, TTL `PT2M`, verifier skew `PT30S`, non-blank `kid`와 active/retiring key overlap을 양쪽에서 exact 검증한다. 실제 host와 credential은 문서에 기록하지 않는다.
- Mongo 확인 기준: 실제 staging/prod Mongo가 standalone이 아니라 replica set 또는 transaction 가능한 sharded cluster인지 확인하고, startup rollback canary, 필수 guard/inbox/index migration, replica-set commit/rollback·duplicate·unknown commit·source/target writer 경합 테스트를 통과해야 한다.
- 성능 기준: 현재 Identity TMI-123 owner event read timeout 기본값은 `PT3S`이므로 direct owner migration은 production 유사 이력에서 예시 P99 `2s` 이하 등 충분한 여유를 증명해야 한다. 실패하면 timeout만 늘리지 않고 durable inbox + worker 계약으로 재결정한다.
- 기존 기반: Learning Core에는 Billing saga, AttemptGroup와 UserWithdrawn에서 `MongoTransactionManager`, `TransactionTemplate`과 staging/prod rollback capability probe를 사용하는 구현이 이미 있어 기술 기반은 존재한다. 다만 이것이 UserMerged 전체 migration의 실제 topology·성능 증명을 대신하지 않는다.
- 변경 파일: 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션 코드·설정·테스트, Identity 작업 트리, Jira, AWS와 DB는 변경하지 않았다.
- 테스트·검증: 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. 로컬 양 저장소의 branch·worktree·설정·publisher·JWT provider·Transaction probe를 읽기 전용으로 대조하고 문서 `git diff --check`를 수행한다. Secret·Token을 기록하지 않았다.

## 2026-09-03 — TMI-125 선행 조건 검토 종료 훅 동기화

<!-- codex-turn:01a065d8-7cd8-7012-9cfc-4e3c51c85c88 -->

- 날짜: 2026-09-03
- 브랜치: Learning Core `develop`; Identity `feat/TMI-123-owner-event-fanout-sigv4`
- Jira: Learning Core `TMI-125`, 선행 Identity `TMI-123`
- 종료 훅에 따라 이번 turn의 상세 검토 결과가 이미 기록됐음을 확인하고 current marker를 포함한 동기화 항목을 WORKLOG 끝에 추가했다.
- 핵심 결론: Identity `TMI-123`은 아직 미커밋·미병합 상태이며, `/internal/v1/owners/merge/events`와 `/internal/v1/events/user-merged` 경로 불일치 및 단일 `learning-core-user-withdrawn` audience 발급 제한을 먼저 해결해야 한다.
- Mongo gate: 실제 staging/prod replica set 또는 transaction 가능한 sharded topology, rollback canary, 필수 index, unknown commit·동시성 통합 테스트와 현재 Identity 3초 read timeout 대비 direct migration P99 증빙이 필요하다.
- 변경 범위: 기록용 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션 코드·설정·테스트, Identity 작업 트리, Jira, AWS와 DB는 변경하지 않았다.
- 테스트: 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다. Secret·Token을 기록하지 않았다.

## 2026-09-03 — TMI-123 Identity 전달 내용 정리

<!-- codex-turn:01a065de-3967-7893-81b8-1dc20d32ad23 -->

- 날짜: 2026-09-03
- 브랜치: Learning Core `develop`
- Jira: Learning Core `TMI-125`, 선행 Identity `TMI-123`
- 사용자 요청: Learning Core UserMerged consumer 구현 전에 Identity 팀에 전달해야 할 수정·확인사항을 복사 가능한 인계 문구로 정리한다.
- 책임 분리: Identity에는 owner event fan-out, exact endpoint·wire, workload JWT 발급 profile과 publisher retry·flag를 요청한다. staging/prod Mongo topology·Transaction rollback·동시성·migration P99는 Learning Core·인프라 책임으로 Identity 요청에서 제외한다.
- endpoint 요청: Identity 로컬 구현의 `/internal/v1/owners/merge/events`를 Learning Core 확정 경로 `/internal/v1/events/user-merged`로 통일하고 query·fragment·redirect 없는 HTTPS exact endpoint만 허용한다.
- JWT 요청: UserMerged 전용 audience `learning-core-user-merged`를 도입하고 기존 단일 `learning-core-user-withdrawn` 제한을 목적별 별도 profile 또는 고정 allowlist로 확장한다. 호출자가 임의 audience를 선택하게 하지 않는다.
- 공통 profile: RS256, 환경별 HTTPS workload issuer, Identity JWKS, `sub=identity-service`, TTL `PT2M`, verifier skew `PT30S`, `typ=JWT`, non-blank `kid`, 전달마다 새 `jti`와 multi-key rotation overlap을 유지한다. 실제 host·key·credential은 문서에 기록하지 않는다.
- fan-out 요청: `UserMerged`는 BILLING·LEARNING_CORE 독립 delivery 두 건, `TrialOwnerRebindApproved`는 BILLING 한 건만 생성하고, consumer별 lease·retry·dead-letter·retention·circuit을 분리한다. legacy/new capture 이중 생성은 금지한다.
- transport 경계: Billing owner event는 VPC Lattice SigV4, Learning Core UserMerged는 별도 변경 승인 전 Bearer workload JWT를 유지하고 사용자 Access Token을 재사용하지 않는다.
- 완료 증빙: TMI-123 전체 테스트, exact payload/route/audience negative test, duplicate·응답 유실·consumer 한쪽 장애 테스트, feature flag 기본 off, develop merge commit과 staging 배포 가능 설정을 Identity에서 공유하도록 요청한다.
- 변경 파일: 전달 내용 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션 코드·설정·테스트, Identity 저장소, Jira, AWS와 DB는 변경하지 않았다.
- 테스트·검증: 코드 변경이 없어 Gradle 테스트는 실행하지 않았으며 `git diff --check`와 marker 단일 포함을 검증한다. Secret·Token을 기록하지 않았다.

## 2026-09-03 — TMI-123 UserMerged 후속 구현 검토

<!-- codex-turn:01a065f8-cfc7-7a90-b49f-ecb90e3a778a -->

- 날짜: 2026-09-03
- 브랜치: Learning Core `develop`; Identity 로컬 `feat/TMI-123-owner-event-fanout-sigv4`
- Jira: 선행 Identity `TMI-123`, 관련 Learning Core `TMI-125`
- 사용자 요청: Identity에 전달한 Learning Core UserMerged endpoint·workload audience·fan-out 후속 요구가 구현됐는지 확인한다.
- Jira 확인: TMI-123 설명에는 TMI-125 후속 보완으로 exact `/internal/v1/events/user-merged`, typed purpose, `learning-core-user-merged`, HTTP 415, legacy drain과 추가 완료 테스트가 반영돼 있다. 상태는 `해야 할 일`이다.
- 코드 확인: 신규 OwnerEvent와 legacy UserMerged adapter가 exact HTTPS endpoint와 `USER_MERGED` purpose를 사용한다. UserWithdrawn은 `USER_WITHDRAWN` purpose를 유지하고, 단일 provider가 enum의 고정 audience만 발급해 raw audience 환경변수와 임의 입력을 제거했다.
- JWT 확인: RS256, environment workload issuer, `sub=identity-service`, `iat=nbf`, TTL `PT2M`, 요청별 UUID jti, JWT typ·kid와 민감 claim 부재 계약이 유지된다.
- fan-out·오류: UserMerged의 BILLING·LEARNING_CORE 독립 delivery와 TrialOwnerRebindApproved BILLING-only shape가 유지된다. Billing 성공과 Learning Core 5xx는 독립 완료·retry로 수렴하며 415는 contract dead-letter에 추가됐다.
- 테스트: Identity에서 `./gradlew clean test`를 실행해 총 630개가 실패·오류·건너뜀 없이 성공했다. `git diff --check`도 성공했다. 최초 sandbox cache lock 실패는 테스트 실행 전 발생했고 승인된 재실행은 성공했다.
- 발견사항: `OwnerEventCoreTests.retrySerializationKeepsTheSameEventIdAndPayload`는 같은 event 객체를 두 번 직렬화해 비교할 뿐 첫 publish 실패→재예약→재claim→두 번째 전송을 실행하지 않는다. 기능 구현상 같은 core를 재조회해 deterministic mapper로 보내므로 즉시 확인된 결함은 없지만, Jira의 실제 재전송 완료 조건을 증명하는 publisher-level test로 보강해야 한다.
- 병합 상태: 원 TMI-123은 Identity PR #37 merge commit `391b55f`로 develop에 병합됐다. 이번 TMI-125 연동 후속 변경은 로컬 feature branch에 미커밋·미추적 상태이고 연결된 원격 브랜치는 삭제돼 아직 develop에 반영되지 않았다.
- 변경 파일: 이번 검토에서 애플리케이션·설정·테스트 코드는 수정하지 않았다. 기록용 Identity·Learning Core의 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다.
- 유지 계약: 공개 API, 사용자 JWT, UserMerged v1, Billing SigV4, TrialOwnerRebindApproved Billing-only, feature flag 기본 false와 Secret·Token 비기록을 유지했다.
- 남은 작업: publisher-level 동일 eventId·payload retry test 보강, 사용자 주도 후속 commit·push·PR merge, workload 운영값·golden-token과 Learning Core TMI-125 Mongo/staging E2E 후 TMI-123 완료 상태를 검토한다.

## 2026-09-03 — 중간 발표용 개발 예상 문제점 재작성

<!-- codex-turn:01a06608-824d-7dd0-a969-c94714a0f04b -->

- 날짜: 2026-09-03
- 현재 브랜치: `develop`
- Jira: 별도 Jira 이슈가 없는 발표 문안 작성 작업이다.
- 사용자 요청: 개발 측면 예상 문제점을 `기능 범위 확대로 인한 일정 지연`과 `콘텐츠 품질`로 다시 작성한다.
- 작성 결과: 기능 범위 확대는 개발·연동·검증 범위 증가로 일정이 지연될 가능성으로, 콘텐츠 품질은 AI 생성 문항·답안·피드백의 정확도와 난이도 편차로 학습 신뢰도가 낮아질 가능성으로 정리했다.
- 해결 방향: 1차 출시 필수 기능 우선순위와 단계별 개발·범위 변경 관리를 적용하고, 콘텐츠 생성 기준·자동 유효성 검사·전문가/운영자 수동 검수·배포 후 품질 모니터링을 결합한다.
- 변경 파일: `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 유지한 외부 계약: 애플리케이션 코드와 공개 API·AI Callback·S3·Redis·Billing 계약을 변경하지 않았다.
- 테스트·결과: 코드 변경이 없는 문안 작업이라 Gradle 테스트는 실행하지 않고 `git diff --check`로 문서 형식을 확인한다.
- 남은 위험·배포 전 확인: 발표 양식의 글자 수에 맞춰 문장 길이를 조정할 수 있으며 실제 일정 기준과 콘텐츠 검수 담당·승인 기준은 별도 확정이 필요하다.
- 예상 밖 변경: 이번 작업 범위 밖의 파일과 기존 사용자 변경은 수정하지 않았다.
- 다음 작업 전 확인: 발표 표에서 문제점과 해결 방안을 같은 행에 배치할지 각각 분리할지 확인한다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-03 — 프론트엔드 로그인 연동 인계서 작성

<!-- codex-turn:01a06614-601a-7ca0-b702-21850f4c0753 -->

- 날짜: 2026-09-03
- 브랜치: `develop`
- Jira: Identity `TMI-109`, `TMI-111`, `TMI-114`, `TMI-123`, Learning Core `TMI-116`, `TMI-118`, `TMI-122`, Billing `TMI-120`을 구현 상태 근거로 사용했으며 Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 프론트가 로그인·회원가입·Guest 전환·Token lifecycle을 구현할 때 필요한 API 순서, 분기와 오류 처리를 현재 Identity 코드·계약 기준으로 정리한다.
- 신규 `docs/codex/FRONTEND_LOGIN_INTEGRATION_GUIDE.md`에 Firebase ID Token과 Identity Token의 역할 분리, 앱 시작 세션 복원, Google·Apple exchange, 필수 phone link와 force refresh, Guest prepare/upgrade/merge, local login, reissue single-flight, logout과 오류별 UX를 기록했다.
- Firebase와 provider flag 기본 off, legacy password/Firebase 동시 운영 주의, Guest merge의 downstream owner 이전·staging E2E 출시 gate를 구현 완료 사실과 구분했다.
- 변경 파일: `docs/codex/FRONTEND_LOGIN_INTEGRATION_GUIDE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 애플리케이션 코드와 공개 API·BaseResponse·JWT·AI·S3·Redis·Billing wire 계약은 변경하지 않았다.
- 코드 변경이 없는 문서 작업이므로 Gradle 테스트는 실행하지 않았다. `git diff --check`, marker 단일 포함과 WORKLOG 항목 EOF 위치를 검증한다.
- Secret·Token·비밀번호·개인정보를 기록하지 않았고 commit·push·PR·merge·배포와 AWS·DB 상태 변경을 수행하지 않았다.

## 2026-09-03 — 로그인 제외 프론트 1차 업데이트 인계서 작성

<!-- codex-turn:01a0664d-8b70-7e10-bfe8-e1a55305ce16 -->

- 날짜: 2026-09-03
- 브랜치: `develop`
- Jira: Learning Core `TMI-116`, `TMI-118`, `TMI-122`와 Billing `TMI-120`을 구현 상태 근거로 사용했으며 Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 로그인 내용을 제외하고 시험 Session 생성 변경을 중심으로 프론트가 반영해야 할 1차 업데이트 계약과 상태·오류 처리를 정리한다.
- 신규 `docs/codex/FRONTEND_NON_LOGIN_UPDATE_GUIDE.md`에 시험 시작별 lowercase UUID v4 `Idempotency-Key`, same-key transport retry, 생성 오류별 key 유지/폐기, 중복 시작 방지와 성공 응답 불변을 기록했다.
- 최초 1회 권리 확정 뒤 `OPEN`·`GRADING`·`RETAKE_AVAILABLE`·`COMPLETED` 상태에 따른 추가 차감 없는 처음부터 재시작, grading retry·Summary 완료 gate, 폐기 Session 처리도 정리했다.
- 기존 S3 raw audio PUT·submit·polling, Part 4 `tableContext`, phone 재가입 continuation, Billing 내부 API 금지와 결제·Challenge·Guest UserMerged의 미완료 출시 경계를 포함했다.
- 변경 파일: `docs/codex/FRONTEND_NON_LOGIN_UPDATE_GUIDE.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 애플리케이션 코드와 기존 공개 API URL·Method·Request Body·성공 DTO·BaseResponse·AI·S3·Redis·Billing wire 계약은 변경하지 않았다.
- 코드 변경이 없는 문서 작업이므로 Gradle 테스트는 실행하지 않았다. `git diff --check`, marker 단일 포함과 WORKLOG 항목 EOF 위치를 검증한다.
- Secret·Token·비밀번호·개인정보를 기록하지 않았고 commit·push·PR·merge·배포와 AWS·DB 상태 변경을 수행하지 않았다.

## 2026-09-04 — Identity TMI-123 병합·Jira 완료 확인 및 다음 작업 점검

<!-- codex-turn:01a069f8-df1d-7a70-a88a-a769d36f7491 -->

- 날짜: 2026-09-04
- 브랜치: Learning Core `develop`; Identity `develop`.
- Jira: 완료된 Identity `TMI-123`, 다음 작업 Learning Core `TMI-125`.
- 작업 목표: 사용자가 병합·완료 처리한 TMI-123의 실제 저장소와 Jira 상태를 확인하고 다음 개발 작업을 확정한다.
- 병합 확인: Identity의 `HEAD`, `develop`, `origin/develop`은 PR #38 merge commit `fa9843e`로 일치하며 후속 구현 commit `1110b8a`를 포함한다. 애플리케이션 변경은 병합됐고 Identity 작업 트리의 남은 변경은 기록 문서뿐이다.
- 구현 확인: exact `POST /internal/v1/events/user-merged`, typed `USER_MERGED` purpose와 `learning-core-user-merged` audience, UserMerged Billing·Learning Core 독립 delivery, TrialOwnerRebindApproved Billing-only와 HTTP 415 contract dead-letter 분류가 최종 계약에 반영됐다.
- Jira 확인: 브라우저 재조회에서 TMI-123 상태 `완료`, Resolution `완료`, 완료 댓글과 해결 시각을 확인했다. 이번 turn에서는 Jira를 변경하지 않았다.
- 테스트 결과: 병합 전 Identity `./gradlew clean test` 결과 총 630개가 실패·오류·건너뜀 없이 통과한 증빙을 확인했다. 이번 상태 확인에서는 Gradle 테스트를 다시 실행하지 않았다.
- 다음 작업: 현재 Jira `해야 할 일`인 TMI-125에서 Learning Core UserMerged consumer, source/target ownership guard, Session·Result·Summary owner migration, inbox 멱등성, workload JWT verifier와 Mongo Transaction 경합 수렴을 구현한다.
- 유지한 외부 계약: 공개 API·DTO·`BaseResponse`, AI `user_id=examId`, 기존 S3·Redis key와 phone continuation·Billing 계약을 변경하지 않았다.
- 남은 위험·배포 전 확인: Identity publisher의 실제 실패→재claim→재전송 동일 eventId·payload 테스트 보강, workload issuer/JWKS와 환경별 key 운영값, Mongo replica-set Transaction·index·P99 및 cross-service staging E2E가 필요하다. feature flag는 검증 전까지 기본 OFF를 유지한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 기존 사용자 변경과 다른 문서는 수정하지 않았다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-04 — TMI-125 UserMerged 구현 계획 최신 코드 재검토

<!-- codex-turn:01a06a19-f55e-7c23-861b-94f191d8e946 -->

- 날짜: 2026-09-04
- 브랜치: Learning Core `develop`; 비교 대상 Identity `develop`과 Billing 로컬 계약 문서.
- Jira: `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`; 선행 Identity `TMI-123`은 완료 상태다.
- 작업 목표: 2026-08-20 작성된 UserMerged 구현 계획과 결정서를 이후 병합된 Identity·Billing·Learning Core 코드에 다시 대조해 구현 착수 가능 여부를 판정한다.
- 결론: source/target ownership guard, direct Mongo Transaction, target 활성 우선, Session·Result·Summary owner migration, inbox 멱등성과 204/409/503 계약의 기본 방향은 유효하다. 다만 신규 Billing saga·AttemptGroup 코드와 최종 workload 계약이 반영되지 않아 현재 문서는 구현 기준으로 바로 사용할 수 없고 먼저 개정해야 한다.
- 누락된 직접 userId: `exam_creation_operations`와 `attempt_group_event_outbox`가 현재 코드에 추가됐지만 계획의 세 컬렉션 inventory에는 없다. 전자는 active reserve/confirm/status saga와 결합되고 후자는 canonical payload·digest를 가진 immutable delivery snapshot이라 일반 owner rewrite에 포함하면 안 된다.
- 권장 수렴: source 또는 target에 non-terminal `ExamCreationOperation`이 있으면 operation/userId를 rewrite하지 않고 merge consumer가 `503`과 bounded `Retry-After`로 재시도하게 해 saga가 terminal이 된 뒤 migration한다. 이미 생성된 AttemptGroup outbox는 userId·canonical payload·digest를 바꾸지 않고 Billing TMI-120의 exact legacy-source fence로 전송하며, migration 이후 새 event만 target owner로 생성한다. 이 정책은 계획서에 명시하고 경합 테스트로 고정해야 한다.
- writer/Transaction gap: 계획의 writer 목록에는 `BillingExamCreationSaga`, `BillingExamCreationTransactionService`, `AttemptGroupStateCoordinator`, `AttemptGroupSummaryCompletionService`, reconciler/backfill/outbox store가 빠져 있다. guard touch가 각 기존 Mongo Transaction에 실제 참여하도록 설계하고 서로 다른 feature `TransactionTemplate`을 중첩하지 않는 규칙 및 billing+attempt-group+user-merged 동시 flag context test가 필요하다.
- workload 최신값: Identity 구현은 RS256, 환경별 workload issuer, 기존 Identity signing/JWKS, `aud=learning-core-user-merged`, `sub=identity-service`, `iat=nbf`, `exp=iat+PT2M`, 요청별 UUID `jti`, `typ=JWT`, `kid`다. `service` claim은 의도적으로 없다. 따라서 Learning Core principal allowlist는 `sub=identity-service`로 고정하고 audience·algorithm·TTL을 자유 설정처럼 남기지 않아야 하며 실제 issuer/JWKS URL·환경별 key/rotation만 배포 gate로 남긴다.
- timeout 최신값: 신규 owner-event publisher는 connect `PT1S`, read `PT3S`다. 계획·결정서의 5초 전제와 최대 HTTP 5초 기준은 낡았으므로 3초 안에 충분한 여유가 남도록 P99/hard-limit을 다시 합의해야 한다.
- security gap: 현재 UserWithdrawn internal chain이 이미 `@Order(1)`, 사용자/Legacy chain이 `@Order(2)`다. 계획대로 UserMerged chain을 다시 `@Order(1)`로 추가하지 말고 명시적 우선순위 또는 통합 internal chain 전략을 확정해 두 endpoint·Legacy 우회·사용자 JWT·principal 401/403을 함께 검증해야 한다. 기존 withdrawal deny와 merged guard가 동시에 존재할 때 target/source withdrawal marker 충돌도 fail-closed 정책이 필요하다.
- governance/test gap: 현재 `AGENTS.md`의 phone/AttemptGroup 허용 범위는 Guest UserMerged를 명시적으로 제외하므로 구현 전 별도 영구 허용 절이 필요하다. 계획의 `./gradlew <mongo-transaction-integration-task>` placeholder도 실제 Gradle task와 CI 필수 실행 방식으로 확정해야 한다. Challenge는 아직 runtime이 없으므로 TMI-125에서 구현하지 않되 향후 Challenge 활성화 전에 동일 guard/migration 계약을 적용한다는 rollout gate가 필요하다.
- 문서 정합성: Identity 인계서의 workload 표에는 여전히 TBD가 남아 있지만 병합 코드와 테스트에서 위 고정 profile이 확정됐다. Learning Core 계획과 결정서를 먼저 최신화하고 Identity 인계서도 이후 같은 값으로 동기화하는 것이 좋다.
- 변경 파일: 검토 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 계획서·결정서·애플리케이션·설정·테스트·Jira·Billing·Identity 코드는 변경하지 않았다.
- 유지한 계약: 공개 API·DTO·`BaseResponse`, AI `user_id=examId`, retryCount, 기존 S3·Redis key, phone continuation과 AttemptGroup wire를 변경하지 않았다.
- 테스트와 결과: 코드 변경 없는 계획 검토라 Gradle 테스트는 실행하지 않았다. 기록 후 `git diff --check`, marker 단일 포함과 WORKLOG EOF 위치를 확인한다.
- 남은 작업: 사용자 승인 후 `AGENTS.md`, UserMerged 계획서와 결정서를 위 정책으로 개정하고, active creation operation 처리와 3초 성능 예산을 확정한 뒤에만 구현을 시작한다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-04 — TMI-125 SecurityFilterChain 순서 설명

<!-- codex-turn:01a06aae-20a6-7a21-aa79-b51bab8f2184 -->

- 날짜: 2026-09-04
- 브랜치: Learning Core `develop`.
- Jira: `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 작업 목표: UserMerged와 UserWithdrawn workload endpoint에 별도 `SecurityFilterChain` 순서가 필요한 이유를 쉽게 설명한다.
- 설명: Spring Security는 숫자가 작은 `@Order`의 chain부터 matcher를 검사하고, 한 요청에 처음 매칭된 chain 하나만 적용한다. 따라서 UserMerged 요청이 일반 사용자/Legacy catch-all chain보다 먼저 전용 workload chain에 도달하도록 해야 하며, UserWithdrawn과 UserMerged가 각자의 audience decoder를 사용하도록 경계를 고정해야 한다.
- 현재 구조: UserWithdrawn exact endpoint chain은 `@Order(1)`, 사용자 JWT와 local/test Legacy catch-all chain은 `@Order(2)`다. 계획처럼 UserMerged를 다시 `@Order(1)`로 두면 당장 반드시 실패하는 것은 아니지만 같은 우선순위의 bean 정렬에 의존하는 취약한 구성이 된다.
- 권장안: 기존 chain을 건드리지 않고 UserMerged exact endpoint chain을 `@Order(0)`으로 추가한다. 최종 순서는 UserMerged 0 → UserWithdrawn 1 → 사용자 JWT/Legacy 2이고, 각 internal chain은 정확한 POST path만 match한다.
- 인증 의미: UserMerged는 audience `learning-core-user-merged`, UserWithdrawn은 `learning-core-user-withdrawn`을 검사한다. signature·issuer·audience·expiry가 잘못된 credential은 인증 실패 401이고, 그 검증은 성공했지만 허용 principal인 `sub=identity-service`가 아니면 권한 실패 403이다.
- 필수 회귀: 두 internal path가 서로의 token을 거절하는지, 사용자 Access JWT가 internal path에 들어오지 못하는지, Legacy permit-all보다 internal chain이 먼저 선택되는지, 일반 사용자 API와 callback 범위가 유지되는지 테스트한다.
- 변경 파일: 설명 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·계획서·설정·테스트·Jira는 변경하지 않았다.
- 테스트와 결과: 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. 기록 후 `git diff --check`, marker 단일 포함과 WORKLOG EOF 위치를 확인한다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-04 — TMI-125 UserMerged chain을 Order 3으로 둘 수 있는지 설명

<!-- codex-turn:01a06ab0-aaab-7f30-92a8-932a11ac74c3 -->

- 날짜: 2026-09-04
- 브랜치: Learning Core `develop`.
- Jira: `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 작업 목표: UserMerged 전용 `SecurityFilterChain`을 `@Order(0)`이 아니라 `@Order(3)`으로 둘 수 있는지 현재 코드 기준으로 설명한다.
- 확인 결과: 현재 `@Order(2)`의 JWT와 Legacy chain에는 `securityMatcher`가 없으며 `anyRequest`를 처리하는 catch-all이다. Spring Security는 낮은 order부터 확인하여 처음 매칭된 chain 하나만 사용하므로, UserMerged가 `@Order(3)`이면 internal 요청도 먼저 `@Order(2)`에서 처리되어 UserMerged 전용 audience decoder가 선택되지 않는다.
- 권장안: 현 구조의 최소 변경은 UserMerged `@Order(0)`, UserWithdrawn `@Order(1)`, 일반 JWT/Legacy catch-all `@Order(2)`다. UserMerged를 `@Order(3)`으로 두려면 `@Order(2)`가 해당 internal 경로에 매칭되지 않도록 matcher 제외를 추가하는 별도 보안 재구성이 필요하다.
- 변경 파일: 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·설정·계획서·테스트·Jira는 변경하지 않았다.
- 테스트와 결과: 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. 기록 후 `git diff --check`와 marker 단일 포함을 확인한다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-04 — TMI-125 SecurityFilterChain order 선택 근거 확정

<!-- codex-turn:01a06ab0-ec4e-7041-8011-155db0bb29d7 -->

- 날짜: 2026-09-04
- 브랜치: Learning Core `develop`.
- Jira: `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 작업 목표: UserMerged chain에 `@Order(0)`을 권장하고 `@Order(3)`을 현 구조에서 권장하지 않는 근거를 확정한다.
- 결론: 0 자체가 특별한 값은 아니다. 현재 UserWithdrawn exact chain이 1이고 일반 JWT/Legacy catch-all이 2이므로, 2보다 먼저 평가되면서 1과 중복되지 않는 최소 변경 값이 0이다.
- Order 3 영향: UserMerged 요청은 3에 도달하기 전에 `securityMatcher`가 없는 Order 2 catch-all에서 처리된다. JWT 모드에서는 잘못된 사용자 decoder가 적용되고 Legacy 모드에서는 internal endpoint가 permit-all로 열릴 수 있다.
- 대안: Order 3을 사용하려면 Order 2 matcher에서 internal path를 확실히 제외하도록 전체 보안 경계를 함께 재구성해야 한다. 현재는 exact matcher를 가진 UserMerged 0 → UserWithdrawn 1 → catch-all 2가 더 단순하고 안전하다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·보안 설정·테스트·Jira는 변경하지 않았다.
- 유지한 계약: 공개 API·DTO·`BaseResponse`, AI, S3·Redis와 기존 internal endpoint 계약을 변경하지 않았다.
- 테스트와 결과: 코드 변경이 없는 설명 작업이라 Gradle 테스트는 실행하지 않았다. `git diff --check`, marker 단일 포함과 WORKLOG EOF 위치를 확인한다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-04 — TMI-125 `@Order` 동작 설명

<!-- codex-turn:01a06ab4-8b03-7da1-883f-5d263d699a8b -->

- 날짜: 2026-09-04
- 브랜치: Learning Core `develop`.
- Jira: `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 작업 목표: Spring의 `@Order()`가 무엇이며 여러 `SecurityFilterChain`에서 어떻게 작동하는지 설명한다.
- 설명: `@Order(n)`는 여러 후보 bean의 검사 우선순위를 나타내고 숫자가 작을수록 우선한다. SecurityFilterChain에서는 요청을 낮은 order부터 matcher와 대조하여 처음 맞는 chain 하나만 적용하고 나머지는 검사하지 않는다.
- 현재 적용: UserMerged exact chain 0, UserWithdrawn exact chain 1, 일반 JWT/Legacy catch-all 2 순으로 구성하면 internal 요청은 각 전용 decoder를 사용하고 나머지 요청만 catch-all로 전달된다.
- 주의: `@Order`는 API 실행 순서나 Controller 호출 순서를 정하는 값이 아니며, 같은 숫자를 여러 chain에 주면 상대 순서가 명확하지 않다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·보안 설정·테스트·Jira는 변경하지 않았다.
- 유지한 계약: 공개 API·DTO·`BaseResponse`, AI, S3·Redis와 기존 internal endpoint 계약을 변경하지 않았다.
- 테스트와 결과: 코드 변경이 없는 설명 작업이라 Gradle 테스트는 실행하지 않았다. `git diff --check`, marker 단일 포함과 WORKLOG EOF 위치를 확인한다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-04 — TMI-125 여러 SecurityFilterChain 구성 설명

<!-- codex-turn:01a06ab5-989e-7ee3-acea-226505566ff7 -->

- 날짜: 2026-09-04
- 브랜치: Learning Core `develop`.
- Jira: `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 작업 목표: Spring에서 “같은 종류의 구성요소가 여러 개”라는 표현을 현재 보안 코드에 맞춰 쉽게 설명한다.
- 설명: 현재 애플리케이션은 요청 전체에 보안 규칙 하나만 두는 대신 UserMerged, UserWithdrawn, 일반 사용자 API용 `SecurityFilterChain` bean을 각각 등록한다. 이들은 모두 같은 타입이지만 담당 경로와 JWT 검증 규칙이 서로 다른 보안 검사대다.
- `@Order` 역할: Spring Security가 여러 검사대 중 경로가 맞는 하나를 찾을 때 확인할 순서를 정한다. UserMerged 0과 UserWithdrawn 1은 exact path만 받고, 일반 JWT/Legacy 2는 앞에서 선택되지 않은 나머지를 받는다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·보안 설정·테스트·Jira는 변경하지 않았다.
- 유지한 계약: 공개 API·DTO·`BaseResponse`, AI, S3·Redis와 기존 internal endpoint 계약을 변경하지 않았다.
- 테스트와 결과: 코드 변경이 없는 설명 작업이라 Gradle 테스트는 실행하지 않았다. `git diff --check`, marker 단일 포함과 WORKLOG EOF 위치를 확인한다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-04 — TMI-125 SecurityFilterChain 검사 범위 설명

- 날짜: 2026-09-04
- 브랜치: Learning Core `develop`.
- Jira: `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 작업 목표: `SecurityFilterChain`이 HTTP 요청에서 무엇을 검사하고 무엇을 검사하지 않는지 설명한다.
- 설명: chain은 먼저 요청 경로와 HTTP method가 자신의 담당 범위인지 판단하고, 선택되면 인증 정보 존재 여부, JWT signature·expiry·issuer·audience 등 유효성, 인증 필요 여부와 principal 권한을 Controller 전에 검사한다.
- 경계: chain은 UserMerged payload의 source/target owner migration 같은 업무 규칙을 처리하지 않는다. 인증·인가를 통과한 뒤 Controller와 Service가 요청 body 검증, 멱등성, ownership migration과 Transaction을 처리한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·보안 설정·테스트·Jira는 변경하지 않았다.
- 유지한 계약: 공개 API·DTO·`BaseResponse`, AI, S3·Redis와 기존 internal endpoint 계약을 변경하지 않았다.
- 테스트와 결과: 코드 변경이 없는 설명 작업이라 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-04 — TMI-125 SecurityFilterChain 인증 경계 설명 완료

<!-- codex-turn:01a06ab7-60a1-7a83-8591-c53dd2022b37 -->

- 날짜: 2026-09-04
- 브랜치: Learning Core `develop`.
- Jira: `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 작업 목표: `SecurityFilterChain`이 검사하는 보안 범위와 Controller·Service가 처리하는 업무 범위를 구분하여 설명한다.
- 결론: chain은 요청 path·method 선택, Bearer credential 존재, JWT signature·expiry·issuer·audience와 호출 권한을 Controller 전에 검사한다. 통과한 요청만 업무 계층으로 전달된다.
- 업무 경계: UserMerged payload, event 멱등성, source/target ownership migration과 Mongo Transaction은 chain이 아니라 Controller·Service에서 처리한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·보안 설정·테스트·Jira는 변경하지 않았다.
- 유지한 계약: 공개 API·DTO·`BaseResponse`, AI, S3·Redis와 기존 internal endpoint 계약을 변경하지 않았다.
- 테스트와 결과: 코드 변경이 없는 설명 작업이라 Gradle 테스트는 실행하지 않았다. `git diff --check`, marker 단일 포함과 WORKLOG EOF 위치를 확인한다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-04 — TMI-125 UserMerged workload 검사 분리 이유 설명

<!-- codex-turn:01a06aba-1fcd-71e2-bbd7-a86ed3539575 -->

- 날짜: 2026-09-04
- 브랜치: Learning Core `develop`.
- Jira: `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 작업 목표: 앱→Identity→Learning Core UserMerged 흐름에서 Learning Core가 workload JWT를 검사하는 이유와 보안 chain을 나누는 이유를 설명한다.
- 확인한 흐름: 앱의 사용자 요청은 Identity가 인증·정책 검증 후 merge를 확정한다. 이후 Identity가 별도의 workload JWT로 Learning Core internal endpoint를 호출하며, 앱의 사용자 Access Token을 그대로 전달하지 않는다.
- 검사 목적: Learning Core는 호출자가 Identity인지, Token이 자신에게 발급됐는지, UserMerged 목적의 Token인지, 서명·issuer·expiry가 유효한지를 확인하여 앱 사용자나 다른 서비스·다른 목적 Token의 직접 호출을 막는다.
- 분리 경계: 일반 사용자 Token, UserMerged workload Token과 UserWithdrawn workload Token은 권한 목적이 다르다. merge Token은 merge endpoint에만, withdrawal Token은 withdrawal endpoint에만 사용할 수 있어야 한다.
- 대안 판단: 하나의 internal `SecurityFilterChain`으로 합치는 것은 가능하지만 그 안에서 요청 path별 decoder/audience/authorization 선택을 다시 구현해야 한다. 모든 internal endpoint에 동일 Token·audience를 허용하는 방식은 한 credential의 유출·오용 범위를 넓히고 목적 제한을 없애므로 채택하지 않는다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·보안 설정·테스트·Jira는 변경하지 않았다.
- 유지한 계약: 공개 API·DTO·`BaseResponse`, AI, S3·Redis와 기존 internal endpoint 계약을 변경하지 않았다.
- 테스트와 결과: 코드 변경이 없는 설명 작업이라 Gradle 테스트는 실행하지 않았다. `git diff --check`, marker 단일 포함과 WORKLOG EOF 위치를 확인한다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-04 — TMI-125 구현 전 잔여 선택지 재정리

<!-- codex-turn:01a06abc-f34f-7d13-9809-359cf076d3ba -->

- 날짜: 2026-09-04
- 브랜치: Learning Core `develop`.
- Jira: `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 작업 목표: 2026-08-20에 이미 확정된 C1~C11을 다시 열지 않고, 이후 추가된 Billing creation saga·AttemptGroup·UserWithdrawn·Identity TMI-123 구현 때문에 새로 확정할 선택지와 장단점을 정리한다.
- 이미 확정된 범위: RS256 workload JWT와 UserMerged 전용 audience·Identity subject, source/target 양쪽 guard, target 활성 시험 우선, source/target history 합집합, 기존 PUT URL 최대 5분 위험 수용, Callback 전체 Transaction, fail-closed event 멱등성, 204/409/503 status 방향과 단계적 rollout은 유지한다.
- 신규 권장 1: source 또는 target에 non-terminal `ExamCreationOperation`이 있으면 owner를 중간에 rewrite하거나 일부 문서만 옮기지 않고 `503 + bounded Retry-After`로 Identity가 재시도하게 한다. terminal 이후 전체 migration하여 Billing reservation/confirm과 owner가 갈라지는 것을 막는다.
- 신규 권장 2: merge 전에 생성된 `attempt_group_event_outbox`의 userId·canonical payload·digest는 수정하지 않고 Billing TMI-120 legacy-source fence로 기존 event를 전달한다. 재작성은 같은 eventId의 불변 payload·digest 계약을 깨므로 제외한다.
- 신규 권장 3: UserMerged exact chain `@Order(0)`, UserWithdrawn exact chain `@Order(1)`, 사용자 JWT/Legacy catch-all `@Order(2)`를 사용한다. 하나의 internal chain도 path별 decoder/audience resolver를 두면 가능하지만 복잡도가 높고, 모든 internal API에 같은 generic Token을 허용하지 않는다.
- 신규 권장 4: source 또는 target의 durable withdrawal marker가 merge event와 충돌하면 mutation 없이 `409` fail-closed와 경보로 격리한다. 아직 commit winner가 불명확한 일시 Transaction 경합만 `503`으로 재시도한다. Learning Core가 withdrawal marker를 임의로 해제하거나 merge 우선으로 덮지 않는다.
- 신규 권장 5: Identity publisher read timeout `PT3S`에 맞춰 direct Transaction 초기 gate를 P99 1초 이하, 전체 HTTP 2초 미만으로 제안한다. production 유사 staging에서 실패하면 timeout 연장이나 direct/async 혼합 대신 durable inbox + worker 계약으로 개정한다.
- 신규 권장 6: 실제 replica-set Mongo를 사용하는 전용 Gradle `mongoIntegrationTest` task를 두고 CI 필수 gate로 실행한다. 단위 `clean test`에 모두 섞거나 공유 staging DB에 의존하는 방식보다 실행 목적·장애 격리가 명확하다.
- 거버넌스 권장: `AGENTS.md`에 TMI-125 단건 예외가 아니라 확정 UserMerged 계약 범위와 후속 버그 수정·테스트·운영 안정화만 허용하는 영구 절을 추가한다. Billing 저장소 수정, 새 owner event, 공개 API 변경과 Challenge는 계속 금지한다.
- 운영값: 실제 issuer/JWKS URI, 환경별 key rotation, Mongo topology·index와 Lattice/TLS/network 증빙은 선택지가 아니라 production 활성화 전 외부 gate다.
- 변경 파일: 분석 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. `AGENTS.md`, 계획서·결정서, 애플리케이션·설정·테스트·Jira는 아직 변경하지 않았다.
- 유지한 계약: 공개 API·DTO·`BaseResponse`, AI `user_id=examId`, retryCount, S3·Redis key, Billing·Identity wire 계약을 변경하지 않았다.
- 테스트와 결과: 코드 변경 없는 계획 분석이라 Gradle 테스트는 실행하지 않았다. `git diff --check`, marker 단일 포함과 WORKLOG EOF 위치를 확인한다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-04 — TMI-125 승인 권장안 기반 구현 계획 갱신

- 날짜: 2026-09-04
- 브랜치: Learning Core `develop`.
- Jira: `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`; 선행 Identity `TMI-123`, Billing legacy-source fence `TMI-120` 계약을 근거로 사용했다.
- 사용자 승인: 기존 C1~C11과 신규 C12~C18 권장 조합을 구현 기준으로 확정했다. 이번 승인은 문서와 구현 착수 범위 승인이고 production publisher·merge flag 활성화 승인은 아니다.
- 계획 핵심: source/target non-terminal `ExamCreationOperation`은 partial migration과 owner rewrite 없이 `503 + Retry-After: 5`로 미루고 terminal 뒤 동일 event를 재처리한다. terminal operation은 기존 owner·reservation snapshot과 purge 정책을 유지한다.
- outbox 핵심: merge 전에 생성된 AttemptGroup outbox의 eventId·userId·canonical payload·digest는 불변 유지해 Billing TMI-120 legacy-source fence로 전달하고, merge 이후 새 event만 target owner를 snapshot한다.
- security/lifecycle: UserMerged `@Order(0)`, UserWithdrawn `@Order(1)`, 사용자 JWT/Legacy catch-all `@Order(2)`로 고정했다. source/target active withdrawal marker는 mutation 없는 `409`, commit winner가 미확정인 일시 Transaction 경합만 `503`이다.
- Transaction/writer: Billing creation saga·Transaction, AttemptGroup coordinator·summary completion·reconciler·backfill·outbox store를 guard inventory에 추가하고 서로 다른 feature `TransactionTemplate` 중첩을 금지했다. Session·Result·Summary만 직접 owner migration하며 operation/outbox/withdrawal marker는 rewrite하지 않는다.
- 인증/성능: workload profile은 RS256, `aud=learning-core-user-merged`, `sub=identity-service`, `iat=nbf`, TTL `PT2M`, UUID `jti`, `typ=JWT`, `kid`로 고정하고 environment별 issuer/JWKS·rotation만 운영값으로 남겼다. Identity connect/read `PT1S/PT3S` 아래 direct Transaction P99 1초·전체 HTTP 2초 미만 초기 gate를 확정했다.
- 테스트/거버넌스: Testcontainers replica-set 전용 Gradle `mongoIntegrationTest` task를 CI 필수 gate로 확정했다. `AGENTS.md`에는 TMI-125 단건 예외 대신 동일 계약의 후속 버그 수정·테스트·운영 안정화까지 허용하는 제한형 영구 절과 금지 범위를 추가했다.
- 변경 파일: `AGENTS.md`, `docs/codex/USER_MERGED_CONSUMER_IMPLEMENTATION_PLAN.md`, `docs/codex/USER_MERGED_CONTRACT_DECISIONS.md`, `docs/codex/USER_MERGED_CONSUMER_REVIEW.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 변경한 동작: 애플리케이션 동작은 변경하지 않았다. 사전 검토 문서는 원문을 유지하면서 2026-09-04 최신 계획이 구현 기준임을 상단에 표시했다.
- 유지한 외부 계약: 공개 API·DTO·`BaseResponse`, retryCount, AI `user_id=examId`, S3 Object Key·Presigned 흐름, Redis Key/TTL과 Identity/Billing wire schema를 변경하지 않았다.
- 실행한 검증과 결과: 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. stale 5초/P99 2초/Order 1/Gradle placeholder 검색 결과가 없고 `git diff --check`가 성공했다.
- 남은 위험·배포 전 확인: environment별 issuer/JWKS·rotation, Mongo replica-set/sharded transaction과 필수 index, 실제 `mongoIntegrationTest` 구현, TLS/network allowlist, writer drain/backfill, Identity retry와 cross-service staging E2E, P99/HTTP 성능 gate가 남아 있다. 이 조건 전 production publisher와 merge flag는 OFF다.
- 예상 밖 diff: 이번 범위 밖의 기존 수정 `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`와 기존 미추적 `APP_FEATURE_LOGIC_OVERVIEW.md`, `FRONTEND_LOGIN_INTEGRATION_GUIDE.md`, `FRONTEND_NON_LOGIN_UPDATE_GUIDE.md`는 수정하지 않았다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-04 — TMI-125 승인 계획 문서 종료 기록 동기화

<!-- codex-turn:01a06ad0-4f1d-7082-969f-ed042c35655d -->

- 날짜: 2026-09-04
- 브랜치: Learning Core `develop`.
- Jira: `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`; Identity `TMI-123`과 Billing `TMI-120` 계약을 선행 근거로 사용했다.
- 완료 결과: 사용자가 승인한 C12~C18 권장안을 `AGENTS.md`, UserMerged 구현 계획서와 계약 결정서에 반영했고 사전 검토 문서는 역사적 snapshot으로 표시했다.
- 확정 핵심: non-terminal creation operation은 `503 + Retry-After: 5`, 기존 operation/outbox snapshot 불변, SecurityFilterChain Order 0/1/2, withdrawal marker `409`, direct P99 1초·HTTP 2초 gate, 전용 `mongoIntegrationTest`, 제한형 영구 구현 허용이다.
- 변경한 동작: 애플리케이션 동작은 변경하지 않았고 구현도 시작하지 않았다.
- 유지한 계약: 공개 API·DTO·`BaseResponse`, retryCount, AI `user_id=examId`, S3·Redis와 Identity/Billing wire를 유지했다.
- 검증: stale timeout/order/Gradle placeholder 검색 결과가 없고 `git diff --check`가 성공했다. 문서 전용 작업이라 Gradle 테스트는 실행하지 않았다.
- 남은 위험·배포 전 확인: issuer/JWKS·rotation, Mongo topology/index와 실제 통합 task 구현, TLS/network, writer drain/backfill, staging E2E와 성능 gate가 남아 있으며 그전에는 production flag를 활성화하지 않는다.
- 예상 밖 diff: 기존 범위 밖 수정·미추적 문서는 보존했고 이번 종료 동기화에서 수정하지 않았다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-04 — TMI-125 구현 승인과 production 활성화 승인 차이 설명

<!-- codex-turn:01a06af4-f343-77a1-b304-992ae69405c9 -->

- 날짜: 2026-09-04
- 브랜치: Learning Core `develop`.
- Jira: `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 작업 목표: “구현 착수 기준을 확정했지만 production publisher·merge 기능 활성화 승인은 아니다”라는 계획 문구의 의미를 설명한다.
- 설명: 사용자 승인은 C1~C18 설계대로 애플리케이션 코드, 테스트, migration과 설정을 만드는 권한을 확정한 것이다. 추가 제품 설계 결정을 요구한다는 뜻은 아니다.
- production 경계: 실제 운영 Identity publisher와 merge feature flag를 켜 사용자 event를 보내고 Learning Core가 처리하게 하는 go-live는 코드 병합·배포, Mongo Transaction/index, workload issuer/JWKS·rotation, staging E2E·성능 gate, 관측·rollback 준비를 통과한 뒤 별도로 수행한다.
- 현재 상태: 계획 문서만 갱신됐고 애플리케이션 구현, Gradle 테스트, migration 실행, 배포와 운영 flag 활성화는 아직 수행하지 않았다.
- 변경 파일: 설명 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 계획서 본문·애플리케이션·설정·테스트·Jira는 변경하지 않았다.
- 유지한 계약: 공개 API·DTO·`BaseResponse`, retryCount, AI `user_id=examId`, S3·Redis와 Identity/Billing wire를 변경하지 않았다.
- 검증: 코드 변경 없는 설명 작업이라 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·배포·운영 flag 변경을 수행하지 않았다.

## 2026-09-04 — TMI-125 ownership guard와 Transaction 중첩 금지 설명

<!-- codex-turn:01a06b03-27c4-7ab1-a712-71e96d4c46b6 -->

- 날짜: 2026-09-04
- 브랜치: Learning Core `develop`.
- Jira: `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 작업 목표: 순수 조회의 merged marker 확인 시점, 쓰기의 guard revision touch와 기존 command Transaction에 참여시키는 이유를 쉽게 설명한다.
- 조회 경계: 순수 조회는 guard 상태만 읽고 revision을 증가시키지 않는다. guard를 읽은 시점을 선형화 기준으로 삼아 merge commit 전에 `ACTIVE`를 확인한 in-flight 조회는 완료할 수 있고, merge commit 뒤 시작한 guard 확인은 `MERGED` source를 거절한다.
- 쓰기 경계: 모든 사용자 소유 쓰기는 업무 데이터와 같은 Mongo Transaction에서 guard를 실제 update한다. merge도 같은 guard를 수정하므로 동시 실행 시 write conflict가 발생하고, 쓰기 선행이면 merge가 재시도해 새 데이터까지 이전하며 merge 선행이면 source 쓰기가 재시도 후 거절된다.
- Transaction 경계: Billing saga, Summary completion과 AttemptGroup coordinator처럼 이미 Transaction이 있는 command는 별도 feature `TransactionTemplate`을 중첩하지 않는다. 기존 Transaction body에 guard touch를 추가해 guard와 업무 변경을 함께 commit 또는 rollback한다. 기존 Transaction이 없는 command만 공통 executor가 Transaction을 한 번 연다.
- 변경 파일: 설명 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·계획서·설정·테스트·Jira는 변경하지 않았다.
- 유지한 계약: 공개 API·DTO·`BaseResponse`, retryCount, AI `user_id=examId`, S3·Redis와 Identity/Billing wire 계약을 변경하지 않았다.
- 테스트와 결과: 코드 변경 없는 설명 작업이라 Gradle 테스트는 실행하지 않았다. `git diff --check`, marker 단일 포함과 WORKLOG EOF 위치를 확인한다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-04 — TMI-125 ownership guard와 revision 개념 설명

<!-- codex-turn:01a06b1b-88fa-7c52-9914-849b69edbede -->

- 날짜: 2026-09-04
- 브랜치: Learning Core `develop`.
- Jira: `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 작업 목표: UserMerged 설계에서 guard가 담당하는 접근 제어와 revision이 담당하는 동시성 충돌 감지를 쉽게 설명한다.
- guard: 사용자별로 `ACTIVE` 또는 `MERGED` 상태와 revision을 보관하는 작은 Mongo 문서다. `ACTIVE`이면 정상 접근할 수 있고 source가 `MERGED`이면 새 요청을 거절하거나 비동기 처리를 target으로 수렴시킨다.
- revision: guard가 실제로 수정될 때 증가하는 버전 번호다. 값 자체의 업무 의미보다, 사용자 쓰기와 merge가 같은 guard를 동시에 update하게 만들어 Mongo write conflict를 발생시키는 역할이 중요하다.
- 적용: 순수 조회는 guard 상태만 읽고 revision을 올리지 않는다. 데이터 쓰기는 업무 데이터와 같은 Transaction에서 revision을 touch하고, merge도 같은 guard를 수정해 둘이 동시에 누락 없이 성공하지 못하도록 한다.
- 변경 파일: 설명 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·계획서·설정·테스트·Jira는 변경하지 않았다.
- 유지한 계약: 공개 API·DTO·`BaseResponse`, retryCount, AI `user_id=examId`, S3·Redis와 Identity/Billing wire 계약을 변경하지 않았다.
- 테스트와 결과: 코드 변경 없는 설명 작업이라 Gradle 테스트는 실행하지 않았다. `git diff --check`, marker 단일 포함과 WORKLOG EOF 위치를 확인한다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-04 — TMI-125 UserMerged Transaction 처리 순서 설명

<!-- codex-turn:01a06b23-1c3f-7a90-8cc8-ace42d97ffef -->

- 날짜: 2026-09-04
- 브랜치: Learning Core `develop`.
- Jira: `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 작업 목표: inbox 판정부터 guard 잠금, active Session 정책, owner migration, source deny와 inbox 완료 기록까지의 실행 순서와 이유를 설명한다.
- 사전 판정: eventId와 digest로 duplicate/conflict를 먼저 판정하고, source/target withdrawal marker는 durable `409`, non-terminal ExamCreationOperation은 retryable `503`으로 mutation 전에 종료한다.
- 동시성 경계: 두 UUID를 canonical 순서로 정렬한 뒤 source/target guard를 같은 순서로 insert/touch하여 교차 merge의 lock 순서를 통일하고 write conflict로 동시 writer를 직렬화한다.
- migration 경계: target active Session 우선 정책을 적용한 후 Result, Summary와 Session owner를 target으로 이전하고, 마지막에 source guard를 `MERGED`로 전환한다. operation과 기존 AttemptGroup outbox snapshot은 수정하지 않는다.
- 멱등성과 원자성: inbox `PROCESSED` 기록을 업무 변경과 같은 Mongo Transaction에 넣어 둘이 함께 commit/rollback되게 한다. commit 실패 시 동일 event가 안전하게 재처리되고, unique eventId 경합도 재조회하여 duplicate/conflict로 수렴한다.
- 변경 파일: 설명 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·계획서·설정·테스트·Jira는 변경하지 않았다.
- 유지한 계약: 공개 API·DTO·`BaseResponse`, retryCount, AI `user_id=examId`, S3·Redis와 Identity/Billing wire 계약을 변경하지 않았다.
- 테스트와 결과: 코드 변경 없는 설명 작업이라 Gradle 테스트는 실행하지 않았다. `git diff --check`, marker 단일 포함과 WORKLOG EOF 위치를 확인한다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-04 — TMI-125 구현 착수 전 Jira·잔여 조건 확인

<!-- codex-turn:01a06b2f-9eb0-7652-a39c-991939020594 -->

- 날짜: 2026-09-04
- 브랜치: Learning Core `develop`.
- Jira: 기존 `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`을 사용하며 중복 이슈를 생성하지 않는다.
- 확인 결과: `USER_MERGED_CONSUMER_IMPLEMENTATION_PLAN.md`와 `USER_MERGED_CONTRACT_DECISIONS.md`에 `TMI-125`가 이미 연결돼 있고 C12~C18 승인 이후 구현 전 추가 제품 선택은 없다고 명시돼 있다.
- 다음 단계: 새 Jira 생성이 아니라 기존 TMI-125에서 승인 계획대로 구현을 시작한다. 구현 범위에는 코드·단위/계약 테스트, guard writer 전환, Mongo replica-set 통합 테스트 task와 feature flag/startup validation이 포함된다.
- 남은 gate: 환경별 issuer/JWKS·rotation, Mongo topology/index, production 유사 staging P99·전체 HTTP latency, Identity 재시도, writer drain/backfill, Lattice/TLS와 cross-service E2E는 구현 및 production 활성화 전에 단계별로 확인한다. 이는 현재 코드 착수를 막는 추가 제품 결정은 아니다.
- 변경 파일: 상태 기록을 위해 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·계획서·설정·테스트·Jira는 변경하지 않았다.
- 유지한 계약: 공개 API·DTO·`BaseResponse`, retryCount, AI `user_id=examId`, S3·Redis와 Identity/Billing wire 계약을 변경하지 않았다.
- 테스트와 결과: 문서 확인 작업이라 Gradle 테스트는 실행하지 않았다. `git diff --check`, marker 단일 포함과 WORKLOG EOF 위치를 확인한다.
- 보안·Git: Secret·Token을 기록하지 않았고 commit·push·배포를 수행하지 않았다.

## 2026-09-04 — TMI-125 UserMerged consumer·ownership migration 구현

<!-- codex-turn:01a06b32-274f-79e3-adc9-dcc6ccd84c31 -->

- 날짜·브랜치·Jira: 2026-09-04, Learning Core `develop`, `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 구현 결과: exact `POST /internal/v1/events/user-merged` internal endpoint, schema v1 normalization·semantic digest·영구 inbox 멱등성, source/target ownership guard와 source token deny gate를 추가했다. source/target withdrawal marker는 mutation 없는 `409`, non-terminal `ExamCreationOperation`은 `503 + Retry-After: 5`, 신규·동일 duplicate는 빈 `204`로 처리한다.
- 보안: UserMerged/UserWithdrawn/사용자 catch-all chain을 `@Order(0/1/2)`로 분리했다. UserMerged workload는 RS256, 환경별 issuer/JWKS, `aud=learning-core-user-merged`, `sub=identity-service`, `iat=nbf`, 최대 TTL `PT2M`, canonical UUID `jti`, `typ=JWT`, nonblank `kid`를 검증한다. 인증·principal authorization 뒤에만 4096-byte body filter가 실행되고 사용자 JWT·다른 principal·Legacy permit-all 우회를 거절한다.
- Transaction과 migration: source/target guard를 canonical UUID 순서로 touch한 뒤 target 활성 시험 우선 정책, `exam_results`, `exam_summaries`, `exam_sessions`의 owner 이전, source guard `MERGED`, inbox `PROCESSED`를 하나의 Mongo Transaction으로 commit한다. operation과 기존 AttemptGroup outbox payload·digest·owner snapshot은 rewrite하지 않는다.
- writer 전환: ExamSession 생성·완료·legacy 보정, Billing creation operation/Session commit, Question·Summary Job claim·완료·복구, Feedback·Summary·SpeechAce·Azure Callback, AttemptGroup coordinator·summary completion·backfill을 current Session owner guard와 같은 Transaction 경계에 연결했다. 기존 Transaction이 있는 command에는 guard touch만 참여시키고 새 중첩 Transaction을 열지 않았다. AI·S3 network와 Redis projection은 Mongo commit 밖에서 실행하도록 유지·분리했다.
- 운영 준비: 기본 OFF `writer-enabled`, `consumer-enabled`, `source-deny-enabled` 설정, issuer/JWKS/skew 설정 검증, Mongo rollback capability probe와 필수 index fail-closed validator를 추가했다. `user-merged-prepare.js`는 기본 dry-run이며 명시적 writer drain·apply 확인 뒤 ACTIVE guard backfill, 전용 collection과 Result/Summary owner index를 만들고 최종 검증한다.
- 테스트: workload chain 상호 격리, wrong principal 403, source merged 403, deny store fail-closed, auth-before-body, 정확히 4096/4097 bytes, Legacy 우회 차단, JWT header/lifetime/jti, guard 상태, digest/duplicate/conflict/precondition과 저카디널리티 metric을 추가했다. Testcontainers Mongo replica-set에는 원자 owner 이전, withdrawal rollback, 양쪽 활성 target 우선, merged target rollback 4개를 추가했다.
- 실행 결과: `./gradlew clean test` Java 482개가 failures/errors/skipped 0으로 성공했다. 이후 body-filter order targeted security test도 성공했다. `node --test scripts/mongodb/user-merged-prepare.test.js` 6개와 `git diff --check`가 성공했다. 현재 host에서 `./gradlew mongoIntegrationTest`는 Docker daemon 부재로 initialization 단계에서 실패했으며, 테스트를 skip하지 않도록 CI 필수 gate로 유지했다.
- 유지한 외부 계약: 기존 공개 API URL·method·parameter·DTO·`BaseResponse`, 실제 userId 비노출, retryCount, S3 Object Key·Presigned/submit/Polling, Redis Key/TTL과 Python AI request/Callback `user_id=examId`를 변경하지 않았다. 신규 internal 성공 응답만 계약대로 빈 `204`다.
- 남은 위험·배포 전 확인: Docker/CI에서 replica-set 통합 테스트 실행, staging/prod Mongo topology와 index migration, 환경별 workload issuer/JWKS·rotation, 구버전 writer drain과 ACTIVE guard backfill, Identity 동일 event 재시도·UserWithdrawn 교차 E2E, TLS/network allowlist, direct Transaction P99 1초·전체 HTTP 2초 미만 측정이 남아 있다. 그전에는 production publisher·consumer·source deny flag를 켜지 않는다.
- 예상 밖 diff: 작업 시작 전부터 수정돼 있던 `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`와 미추적 `docs/codex/APP_FEATURE_LOGIC_OVERVIEW.md`, `docs/codex/FRONTEND_LOGIN_INTEGRATION_GUIDE.md`, `docs/codex/FRONTEND_NON_LOGIN_UPDATE_GUIDE.md`는 이번 구현에서 수정하지 않았다.
- Git·외부 작업: commit, push, 배포, AWS·Mongo 운영 변경과 Jira 상태 변경을 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-04 — TMI-125 구현 최종 재검증

<!-- codex-turn:01a06b32-274f-79e3-adc9-dcc6ccd84c31-final-verification -->

- 날짜·브랜치·Jira: 2026-09-04, Learning Core `develop`, `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 재검증 결과: 마지막 security filter-order 변경까지 포함한 `./gradlew clean test`가 Java 483개, failures/errors/skipped 0으로 성공했다.
- migration 검증: `node --test scripts/mongodb/user-merged-prepare.test.js` 6개가 모두 성공했고 `git diff --check`도 성공했다.
- 통합 테스트 경계: 현재 host에는 Docker daemon이 없어 Testcontainers replica-set `mongoIntegrationTest` 4개를 실행할 수 없다. 자동 skip으로 숨기지 않았으며 Docker가 있는 CI/환경에서 필수 gate로 실행해야 한다.
- 외부 계약·운영 상태: 공개 API·AI·S3·Redis 계약은 유지했고 feature flag는 기본 OFF다. commit, push, Jira 변경, 배포와 운영 migration/flag 활성화는 수행하지 않았다.
- 예상 밖 diff: 작업 시작 전부터 수정돼 있던 `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`와 기존 미추적 문서 3개는 수정하지 않고 보존했다.

## 2026-09-04 — TMI-125 구현 결과 설명

<!-- codex-turn:01a06b61-d4a7-74a3-bdee-72b67f41c467 -->

- 날짜·브랜치·Jira: 2026-09-04, Learning Core `develop`, `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 작업 목표: 구현된 UserMerged consumer가 실제로 무엇을 보호하고 어떤 순서로 소유권을 이전하는지 사용자 요청 흐름 기준으로 재확인해 설명한다.
- 확인 결과: Identity 전용 workload JWT 인증 뒤 schema v1 event를 정규화하고 eventId/digest로 duplicate와 conflict를 판정한다. withdrawal marker는 `409`, non-terminal ExamCreationOperation은 `503 + Retry-After: 5`로 mutation 전에 종료한다.
- Transaction 결과: source/target guard를 canonical UUID 순서로 touch하고 target active Session 우선 정책을 적용한 뒤 `exam_results`, `exam_summaries`, `exam_sessions` owner를 target으로 이전한다. source guard `MERGED`와 inbox `PROCESSED`까지 같은 Mongo Transaction에 포함돼 전부 함께 commit 또는 rollback된다.
- 동시성 결과: 기존 Session·Billing saga·채점 Job·AI Callback·AttemptGroup writer가 업무 write와 같은 Transaction에서 current owner guard를 touch한다. merge와 동시 실행되면 Mongo write conflict와 제한된 retry로 한쪽 순서에 수렴하고, merge 완료 뒤 source JWT의 공개 API 접근은 deny gate가 거절한다.
- 운영 경계: feature flag는 기본 OFF다. Mongo migration·writer drain/backfill, Docker replica-set 통합 테스트, 환경별 workload issuer/JWKS·rotation, Identity 재시도와 staging E2E·성능 gate 전에는 production consumer와 source deny를 활성화하지 않는다.
- 변경·검증: 이번 작업은 구현 설명과 상태 문서 갱신만 수행했고 애플리케이션 코드는 변경하지 않았다. 직전 최종 검증인 Java 483개와 Node 6개 성공 결과를 유지하며 추가 Gradle 테스트는 실행하지 않았다.
- 계약·Git: 기존 공개 API·DTO·`BaseResponse`, retryCount, S3·Redis와 AI `user_id=examId` 계약을 유지했다. commit, push, 배포, 운영 DB·AWS·Jira 상태 변경을 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-04 — TMI-125 이후 다음 작업 판정

<!-- codex-turn:01a06b65-0577-7540-9310-450843b526a9 -->

- 날짜·브랜치·Jira: 2026-09-04, Learning Core `develop`; 현재 구현 Jira는 `TMI-125`, 다음 Challenge 전용 Jira는 아직 없다.
- 사용자 요청: UserMerged consumer 구현 다음에 진행할 작업을 최신 코드와 로드맵 기준으로 설명한다.
- 확인 결과: 2026-09-03 체크리스트의 Identity `TMI-123` 진행 중과 Learning Core UserMerged 미구현 표시는 현재보다 오래됐다. 최신 기록상 Identity `TMI-123`은 병합·완료됐고 Learning Core `TMI-125`는 로컬 구현과 비-Docker 테스트가 완료됐다.
- 즉시 마무리: 사용자가 TMI-125 변경을 commit/push하고 PR을 `develop`에 병합한 뒤 Docker CI에서 `./gradlew mongoIntegrationTest` 4개를 통과시켜야 한다. 이 검증 전 production UserMerged flag는 활성화하지 않는다.
- 다음 신규 개발: Learning Core 10초 챌린지 runtime은 현재 `src/main`에 구현 파일이 없다. 승인된 프론트·AI v1 계약을 기준으로 Challenge backend 계획과 Jira를 만들고 구현하는 것이 다음 기능 우선순위다.
- 권장 첫 단위: `challenge_10s_questions` validator, 최초 활성 KST 날짜 baseDate singleton, 비순환 day resolver, MEMBER/ownership 경계, ChallengeAttempt snapshot·1시간 deadline·고정 S3 key, today/question/attempt/upload-url/answer의 내구성 있는 제출 foundation을 먼저 만든다.
- 후속 단위: Challenge AI multipart dispatch와 전용 service credential/Callback, 결정적 job·최대 3 generation·120초 deadline, stale/duplicate fencing, no-speech 결과, history/results API와 expiry·index·staging E2E를 연결한다.
- 별도 운영 작업: Billing 장애 reconciliation과 TMI-116/118/120/122·TMI-125의 Lattice/Mongo migration/failure-injection E2E는 Challenge 기능과 섞지 않고 release 안정화 트랙으로 유지한다.
- 변경·검증: 이번 작업은 분석과 상태 문서 갱신만 수행했고 애플리케이션·계약·Jira·AWS·DB를 변경하지 않았다. 코드 변경이 없어 Gradle 테스트는 재실행하지 않았고 `git diff --check`만 확인한다.
- Git·보안: commit, push, 배포와 Jira 상태 변경을 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-05 — 1차 업데이트 유료 인앱결제 포함 범위 분석

<!-- codex-turn:01a0705c-9c5e-7c32-99b2-894428aa0e81 -->

- 날짜·브랜치: 2026-09-05, `feat/TMI-125-user-merged-ownership-migration`.
- Jira: Billing `TMI-110`, `TMI-112`, `TMI-113`, `TMI-115`, `TMI-117`, `TMI-120`, Learning Core `TMI-116`, `TMI-118`, `TMI-122`, `TMI-125`를 현재 기반 구현 이력으로 사용했으며 Jira를 조회하거나 변경하지 않았다.
- 작업 목표: 유료 인앱결제를 1차 업데이트에 포함할 때 기존 무료시험·Reservation 범위 대비 추가되는 개발·검증·운영 범위를 추정한다.
- 확인 결과: Billing에는 무료 entitlement·Reservation·AttemptGroup·owner rebind 기반은 있지만 Apple/Google server verification adapter, notification inbox, payment/order/refund 원장과 앱용 구매·복원 API는 없다.
- 범위 추정: credit 중심 최소 결제 MVP는 현재 남은 출시 작업 대비 약 40~70%, 1 backend+1 mobile 병렬 기준 약 3~5주가 추가될 수 있다. 3일 pass·첫 구매 2배·출석·추천·coupon까지 전부 포함하면 약 80~150%, 6~10주 이상으로 커질 수 있다. 이는 스토어 계정·상품 준비 완료를 가정한 거친 추정이다.
- 권장 결정: 결제를 반드시 1차에 넣는다면 credit 상품, server 검증, notification, 구매 복원과 미사용 전액 환불의 최소 폐쇄 루프만 포함하고 pass·보상·추천·coupon은 후속으로 분리한다.
- 신규 문서: `docs/codex/FIRST_RELEASE_PAYMENT_SCOPE_IMPACT.md`에 현재 상태, 선택지 A/B/C, 작업 목록, 일정 추정, 줄이면 안 되는 안전 항목, 결정사항과 출시 gate를 기록했다.
- 변경 파일: `docs/codex/FIRST_RELEASE_PAYMENT_SCOPE_IMPACT.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·공개 API·AI·S3·Redis·Billing wire는 변경하지 않았다.
- 검증: 코드 변경이 없는 분석 문서 작업이므로 Gradle 테스트는 실행하지 않았고 `git diff --check`, marker 단일 포함과 WORKLOG EOF 위치를 확인한다.
- 보안·Git: Secret·Token·결제 credential을 기록하지 않았고 commit·push·PR·merge·배포와 AWS·DB 상태 변경을 수행하지 않았다.

## 2026-09-05 — credit 대신 5종 기간형 이용권으로 범위 재산정

<!-- codex-turn:01a0705f-1d15-7a3f-9325-088a00b4ebd2 -->

- 날짜·브랜치: 2026-09-05, `feat/TMI-125-user-merged-ownership-migration`.
- Jira: Billing `TMI-110`, `TMI-112`, `TMI-113`, `TMI-115`, `TMI-117`, `TMI-120`, Learning Core `TMI-116`, `TMI-118`, `TMI-122`, `TMI-125`를 기반 이력으로 기록했으며 Jira를 조회하거나 변경하지 않았다.
- 사용자 결정: 유료 상품은 credit 충전이 아니라 1일·3일·7일·14일·30일 동안 모의고사를 무제한 이용하는 기간형 상품으로 변경한다.
- 핵심 미확정: 각 상품이 구매한 기간만 사용하는 기간제 이용권인지 만료 때 반복 결제되는 자동 갱신 구독인지 최종 확정이 필요하다. 현재 기간 조합에는 기간제 이용권을 권장한다.
- 계약 영향: credit 잔액·10-credit 차감 UI 대신 plan, activatedAt, expiresAt, status 기반 `SubscriptionEntitlement` authorization이 필요하다. 무료시험은 별도 `FREE_EXAM_ONCE`로 유지할 수 있다.
- 시험 정책: 이용권 유효 중 새 AttemptGroup을 횟수 차감 없이 열고, 만료 전에 시작한 AttemptGroup은 완료될 때까지 추가 결제 없이 처음부터 재시작할 수 있도록 기존 정책을 유지한다.
- 범위 추정: 5종 기간제 이용권은 약 4~6주/+50~80%, 모두 자동 갱신이면 renewal·cancel·grace·billing retry까지 포함해 약 6~10주 이상/+80~140%의 거친 추가 범위로 재산정했다.
- 변경 파일: `docs/codex/FIRST_RELEASE_PAYMENT_SCOPE_IMPACT.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션·공개 API·스토어 설정·Jira·AWS·DB는 변경하지 않았다.
- 검증: 코드 변경이 없는 계약·범위 문서 작업이므로 Gradle 테스트는 실행하지 않았고 `git diff --check`, marker 단일 포함과 WORKLOG EOF 위치를 확인한다.
- 보안·Git: Secret·Token·결제 credential을 기록하지 않았고 commit·push·PR·merge·배포를 수행하지 않았다.

## 2026-09-05 — 5종 기간형 이용권 결정 종료 훅 동기화

<!-- codex-turn:01a0705f-1d15-76c3-95cb-a7fc395a4099 -->

- 날짜·브랜치: 2026-09-05, `feat/TMI-125-user-merged-ownership-migration`.
- Jira: Billing `TMI-110`, `TMI-112`, `TMI-113`, `TMI-115`, `TMI-117`, `TMI-120`, Learning Core `TMI-116`, `TMI-118`, `TMI-122`, `TMI-125`를 기반 이력으로 기록했으며 Jira를 조회하거나 변경하지 않았다.
- 사용자 결정은 credit 충전 대신 1일·3일·7일·14일·30일 동안 무제한 응시하는 기간형 상품을 사용한다는 것이다.
- 기간형 상품을 한 번 구매 후 만료되는 이용권으로 할지 자동 결제되는 갱신 구독으로 할지는 미확정이며, 현재 상품 구성에는 전자를 권장한다.
- 무료시험 1회는 별도 `FREE_EXAM_ONCE` entitlement로 유지하고, 이용권 유효 중 새 시험을 횟수 차감 없이 시작하며 만료 전에 연 AttemptGroup은 완료 전 재시작을 보장하는 정책을 유지한다.
- 종료 훅 기록 동기화만 추가했으며 애플리케이션·공개 API·스토어 설정·Jira·AWS·DB는 변경하지 않았다.
- Secret·Token·결제 credential을 기록하지 않았고 commit·push·PR·merge·배포를 수행하지 않았다.

## 2026-09-05 — 1차 업데이트 5종 기간제 이용권 범위 확정

<!-- codex-turn:01a07063-f1fb-7f50-a976-5a5a0b50e7d1 -->

- 날짜·브랜치: 2026-09-05, `feat/TMI-125-user-merged-ownership-migration`.
- Jira: 관련 기반 이력은 Billing `TMI-110`, `TMI-112`, `TMI-113`, `TMI-115`, `TMI-117`, `TMI-120`, Learning Core `TMI-116`, `TMI-118`, `TMI-122`, `TMI-125`다. 기간제 결제 전용 신규 Jira는 아직 없고 Jira를 조회하거나 변경하지 않았다.
- 사용자 결정: 1차 유료 상품은 credit이나 자동 갱신 구독이 아니라 결제한 기간만 사용하는 1일·3일·7일·14일·30일 무제한 이용권이다.
- 기간 계약: Billing server가 거래를 검증해 `CAPTURED`로 확정한 시점부터 24·72·168·336·720시간을 계산하고 만료 시 자동 결제 없이 종료한다. client clock은 권한 근거로 사용하지 않는다.
- 시험 계약: 활성 이용권 중 새 AttemptGroup을 횟수 차감 없이 열고 usage audit만 남긴다. 만료 전에 시작한 AttemptGroup은 `OPEN/GRADING/RETAKE_AVAILABLE` 정책에 따라 완료 전 추가 결제 없는 처음부터 재시작을 보장한다.
- 1차 포함: 5종 상품 catalog, Apple/Google 구매·server 검증·notification, order/payment/transaction/entitlement 원장, 구매 상태·현재 이용권·복원 API, refund/revoke, Learning Core evaluator, iOS/Android UI와 staging E2E다.
- 1차 제외: credit, 자동 갱신, grace/billing retry, 활성 이용권 추가 구매·합산, 출석 연장, 첫 구매 배수, 추천, coupon, plan 변경, 웹 PG다.
- 신규 `docs/codex/FIRST_RELEASE_FIXED_TERM_PASS_SCOPE.md`에 서비스별 범위, 상태, 오류 UX, 구현 순서, 완료 조건, 약 4~6주 일정 추정과 남은 최종 결정을 기록했고 기존 영향 분석도 확정 내용에 맞게 갱신했다.
- 변경 파일: `docs/codex/FIRST_RELEASE_FIXED_TERM_PASS_SCOPE.md`, `docs/codex/FIRST_RELEASE_PAYMENT_SCOPE_IMPACT.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션·공개 API·스토어·Jira·AWS·DB는 변경하지 않았다.
- 검증: 코드 변경이 없는 범위 문서 작업이므로 Gradle 테스트는 실행하지 않았고 `git diff --check`, marker 단일 포함과 WORKLOG EOF 위치를 확인한다.
- 보안·Git: Secret·Token·결제 credential을 기록하지 않았고 commit·push·PR·merge·배포를 수행하지 않았다.

## 2026-09-05 — 1차 기간제 이용권 범위 종료 훅 동기화

<!-- codex-turn:01a07063-f1fb-76e1-a46e-ab6a1e4100bd -->

- 날짜·브랜치: 2026-09-05, `feat/TMI-125-user-merged-ownership-migration`.
- Jira: Billing `TMI-110`, `TMI-112`, `TMI-113`, `TMI-115`, `TMI-117`, `TMI-120`, Learning Core `TMI-116`, `TMI-118`, `TMI-122`, `TMI-125`를 기반 이력으로 기록했다. 결제 전용 신규 Jira는 없으며 Jira를 조회하거나 변경하지 않았다.
- 확정 상품은 Billing server 검증 시점부터 1·3·7·14·30일간 무제한 사용하는 비자동갱신 기간제 이용권이다.
- 1차 범위는 Identity/SNS·필수 phone, 무료시험 1회, Apple/Google 구매와 검증·notification·복원·환불, 기간 entitlement, 시험 생성 saga·완료 전 재시작, owner lifecycle와 production 통합 gate다.
- credit·자동 갱신·출석 연장·첫 구매 배수·추천·coupon·plan 변경·웹 PG는 1차에서 제외한다.
- 10초 Challenge는 기존 진행표상 1차지만 runtime이 없어 같은 배포에 포함하면 별도 개발 일정이 추가되며 1.1 분리 여부가 남은 제품 결정이다.
- 종료 훅 기록 동기화만 추가했고 애플리케이션·공개 API·스토어 설정·Jira·AWS·DB는 변경하지 않았다.
- Secret·Token·결제 credential을 기록하지 않았고 commit·push·PR·merge·배포를 수행하지 않았다.

## 2026-09-05 — credit 및 첫 구매 2배 영구 제거 계약 확정

<!-- codex-turn:01a0706c-78cb-7193-8006-4f70081a9823 -->

- 날짜·브랜치: 2026-09-05, `feat/TMI-125-user-merged-ownership-migration`.
- 사용자 결정에 따라 credit 상품·잔액·grant·ledger·reservation·consumption·충전 API/UI를 목표 제품에서 완전히 제거하고, 첫 구매 2배도 후속 범위가 아니라 완전히 제거했다.
- 유료 권리는 Billing 검증 `CAPTURED` 시점부터 1·3·7·14·30일간 무제한 사용하는 비자동갱신 기간제 이용권 5종만 유지한다. 검증 phone당 무료시험 `FREE_EXAM_ONCE`는 별도 권리로 유지한다.
- 출석 연장·연속 로그인·추천인·coupon은 이후로 미뤘으며 다시 도입할 때는 폐기된 credit 계약을 재사용하지 않고 별도 계약을 확정해야 한다.
- 기존 Billing 계약서에는 현행 우선 공지를 추가하고, 1차 범위·영향 분석·프론트 비로그인 가이드·현재 상태의 상충 표현을 현행 정책에 맞게 수정했다.
- 관련 기반 Jira는 Billing `TMI-110`, `TMI-112`, `TMI-113`, `TMI-115`, `TMI-117`, `TMI-120`, Learning Core `TMI-116`, `TMI-118`, `TMI-122`, `TMI-125`다. 유료 기간제 이용권 구현 전용 Jira는 아직 없으며 Jira를 조회하거나 변경하지 않았다.
- 애플리케이션 코드·공개 API·스토어 설정·AWS·DB·Git 이력은 변경하지 않았다. Secret·Token·receipt·결제 credential을 기록하지 않았다.

## 2026-09-05 — TMI-125 Jira 완료 처리

<!-- codex-turn:01a0707d-d8dd-7261-ac67-a92e16f83aef -->

- 날짜·브랜치·Jira: 2026-09-05, Learning Core `develop`, `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 사용자 요청: 구현과 병합이 끝난 TMI-125 Jira를 닫는다.
- 사전 확인: Jira는 `해야 할 일`, Resolution 없음이었고 workflow의 `완료` transition을 사용할 수 있었다.
- 처리 결과: TMI-125를 `완료`로 전환했다. 재조회 결과 status category는 `done`, Resolution은 `완료`, 갱신 시각은 2026-09-05로 확인됐다.
- Git 확인: PR #28 merge commit `8c8208b`가 현재 `develop`과 `origin/develop`의 HEAD이며 TMI-125 구현 commit `60742be`를 포함한다. Jira 처리 전 작업 트리는 clean이었다.
- 운영 경계: Jira 완료는 코드 구현·병합 완료를 의미하며 production UserMerged flag 활성화 승인은 아니다. Docker replica-set `mongoIntegrationTest`, Mongo migration·writer drain/backfill, workload issuer/JWKS와 staging E2E·성능 gate는 계속 남아 있다.
- 변경 파일: Jira 완료 결과 기록을 위해 `docs/codex/CURRENT_STATE.md`와 `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·계약·테스트 코드는 변경하지 않았다.
- 테스트: 애플리케이션 코드 변경이 없어 Gradle 테스트를 재실행하지 않았고 문서 갱신 후 `git diff --check`를 수행한다.
- Git·보안: commit, push, 배포, AWS·Mongo 운영 변경은 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-05 — Challenge 제외 1차 잔여 기능 재점검

<!-- codex-turn:01a0708b-40be-78c2-bd10-b67e9d899a7d -->

- 날짜·브랜치: 2026-09-05, Learning Core `develop@8c8208b`, Identity `develop@fa9843e`, Billing `develop@7138810`.
- 사용자 요청: 10초 챌린지를 제외해도 추가 기능 개발이 필요한지 최신 구현·병합 기록과 1차 확정 범위 기준으로 재점검한다.
- 핵심 판정: 필요하다. 새로 확정한 1·3·7·14·30일 비자동갱신 무제한 이용권은 전용 Jira와 runtime이 없어 Billing 상품·Apple/Google server 검증·payment/transaction/entitlement 원장·공개 구매/상태/현재 이용권/복원 API·notification/refund/reconciliation, Learning Core evaluator와 모바일 StoreKit 2·Google Play 구매/복원 전체가 신규 기능으로 남는다.
- 기존 범위 판정: Identity `TMI-123` fan-out과 Learning Core `TMI-125` UserMerged까지 병합돼 SNS lifecycle, 검증 phone당 무료시험, Reservation·AttemptGroup, phone continuation과 owner migration의 큰 서버 기능은 구현됐다. 무료시험만 범위로 보면 남은 중심은 신규 대형 도메인보다 통합·rollout이다.
- 후속 코드 3건: Learning Core staging workflow에 `mongoIntegrationTest` required gate 추가, UserMerged Mongo 준비 script에 orphan Result/Summary와 Session-owner 불일치 preflight 추가, phone continuation discovery의 `attemptGroupId` lowercase UUID v4 strict decode와 회귀 테스트가 필요하다.
- 운영·검증: Learning Core와 Billing의 Docker replica-set 미실행 테스트, Mongo migration dry-run/apply, 실제 Lattice/IAM/SG·workload issuer/JWKS, multi-instance·response-loss·rollback·stale event와 모바일 SNS/phone/무료시험 staging E2E, canary·feature flag 순차 활성화가 남는다.
- 문서 변경: `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션·공개 API·계약·Jira·AWS·DB는 변경하지 않았다.
- 테스트·Git·보안: 분석 문서만 변경해 Gradle 테스트는 재실행하지 않는다. `git diff --check`, WORKLOG EOF와 marker 단일 포함을 확인하며 commit·push·배포를 수행하지 않고 Secret·Token을 기록하지 않는다.

## 2026-09-05 — TMI-125 후속 결함 4건 검토

<!-- codex-turn:01a0709b-ce26-75d1-8ea7-7bbd9b8850c0 -->

- 날짜·브랜치·Jira: 2026-09-05, Learning Core `develop`; 완료 상태인 `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`의 후속 리뷰다.
- 사용자 요청: attemptGroupId 검증, Mongo 사전검사, unknown commit 수렴과 replica-set 통합 테스트 누락 지적이 실제 결함인지 현재 코드에 대조한다.
- 결론: 네 항목 모두 수정 또는 검증 보강이 필요하며 production UserMerged 활성화를 막는 유효한 finding이다. 단, 1번은 모든 reserve 경로가 아니라 phone continuation discovery에 남은 경계 누락으로 범위를 정정한다.
- finding 1: `BillingExamCreationSaga.validateReserved()`는 reserve snapshot의 `reservationId`와 `attemptGroupId`를 lowercase UUID v4로 검사한 뒤에만 `markReserved()`를 호출하므로 일반 reserve 저장은 보호된다. 반면 `PhoneContinuationResponse`와 `validatePhoneContinuation()`은 `attemptGroupId`를 opaque text로만 검사하고, 실제 test도 `group-existing`을 성공값으로 허용한다. 이 discovery snapshot은 `ExamCreationOperation.expectedAttemptGroupId`에 저장되므로 client decode와 Saga pre-persist 양쪽에서 UUID v4를 검사해야 한다.
- finding 2: migration inventory는 owner UUID, 활성 Session 중복, 기존 MERGED guard와 index만 검사한다. `exam_results`/`exam_summaries.examId`가 가리키는 Session 부재와 양쪽 `userId` 불일치를 검사하지 않아 오염된 데이터를 그대로 guard backfill·migration 대상으로 만들 수 있다. `$lookup` 기반 count-only 검사로 apply를 차단하고 원시 user/exam 식별자는 출력하지 않아야 한다.
- finding 3: `UserOwnedTransactionExecutor`는 unknown commit label을 non-retry로 분류해 blind mutation 재실행은 막지만, `UserMergedConsumerService`는 DuplicateKey만 inbox 재조회하고 unknown commit은 재조회하지 않는다. internal advice도 `DataAccessException`만 503으로 매핑하므로 Spring이 `TransactionSystemException` 등으로 감싸면 500 가능성이 있다. eventId/digest inbox bounded recheck로 204 duplicate/409 conflict/503 unknown에 수렴하고 wrapper 계층과 HTTP 매핑을 테스트해야 한다.
- finding 4: 현재 replica-set 통합 테스트 4개는 정상 원자 이전·withdrawal 선검사·양쪽 active target 우선·merged target 충돌을 검증한다. non-terminal operation은 mock 단위 테스트만 있고 HTTP 503/Transaction 통합 증명은 없으며, sequential duplicate 외 동시 duplicate, mutation 중간/후반 failure rollback, source/target writer·네 Callback 경합과 unknown commit은 없다. 계획의 완료 gate 대비 실제 통합 테스트가 부족하다.
- 우선순위: 1·3은 잘못된 durable state와 불명확 응답을 막는 P1, 2는 migration apply P1 gate, 4는 위 동시성 불변식의 release P1 검증 gate로 본다. TMI-125를 다시 열거나 별도 후속 Bug를 만들어 한 묶음으로 수정한 뒤 Jira 완료를 재판정하는 것이 적절하다.
- 변경 파일·테스트: 리뷰 기록을 위해 `docs/codex/CURRENT_STATE.md`와 `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·migration·test 코드는 수정하지 않았고 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. `git diff --check`만 확인한다.
- 계약·Git·보안: 기존 공개 API·AI·S3·Redis와 Billing wire 계약을 변경하지 않았고 commit, push, 배포, DB/AWS와 Jira 상태를 변경하지 않았다. Secret·Token과 raw 식별자를 기록하지 않았다.

## 2026-09-05 — TMI-125 후속 수정 위해 재개

- 날짜·브랜치·Jira: 2026-09-05, Learning Core `develop`, `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 사용자 결정: 후속 리뷰에서 확인된 네 가지 production blocker를 기존 Jira에서 수정하기 위해 TMI-125를 다시 연다.
- Jira 처리: workflow transition `진행 중`을 적용해 기존 `완료` 상태를 재개했다. Jira 댓글에 attemptGroupId strict 검증, migration 사전 정합성, unknown commit 수렴과 replica-set 경합 테스트 보강 범위를 기록했다.
- 완료 재판정 조건: 네 항목 구현, 비-Docker 전체 테스트와 migration Node test, Docker replica-set 통합 테스트, 공개 API·Billing·AI·S3·Redis 계약 불변 확인 후 다시 완료로 전환한다.
- 운영 경계: 수정과 검증 전 production UserMerged writer/consumer/source deny flag는 OFF로 유지한다.
- 변경 파일: Jira 재개 결과 기록을 위해 `docs/codex/CURRENT_STATE.md`와 `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·migration·test 코드는 아직 수정하지 않았다.
- 테스트·Git·보안: 코드 변경이 없어 Gradle 테스트를 실행하지 않았고 문서 형식만 확인한다. commit, push, 배포, AWS·Mongo 운영 변경은 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-05 — TMI-125 재개 종료 훅 동기화

<!-- codex-turn:01a070a0-e037-7a51-826a-0ffd7013797a -->

- 날짜·브랜치·Jira: 2026-09-05, Learning Core `develop`, `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 현재 상태: 후속 리뷰에서 확인된 production blocker 4건을 수정하기 위해 Jira가 `진행 중`으로 재개됐고 수정 범위와 완료 조건이 Jira 댓글에 기록돼 있다.
- 남은 구현: phone continuation `attemptGroupId` UUID v4 strict 검증, migration orphan/owner mismatch preflight, unknown commit inbox 수렴·503 매핑, replica-set rollback·duplicate·writer/Callback 경합 테스트 보강이다.
- 운영 경계: 수정과 전체 검증 전 production UserMerged writer/consumer/source deny flag는 OFF로 유지한다.
- 변경 파일: 종료 훅 동기화를 위해 `docs/codex/CURRENT_STATE.md`와 `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·migration·test 코드는 수정하지 않았다.
- 테스트·외부 작업: 코드 변경이 없어 Gradle 테스트를 실행하지 않았고 `git diff --check`만 확인한다. Jira·AWS·Mongo·배포 상태를 추가로 변경하지 않았다.
- Git·보안: commit과 push는 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-05 — TMI-125 후속 production safety 수정 계획 작성

<!-- codex-turn:01a070a2-d6fe-7e01-a2ea-8e8fddbbded1 -->

- 날짜·브랜치·Jira: 2026-09-05, Learning Core `develop`, 진행 중인 `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 사용자 요청: 재검토에서 확인한 `attemptGroupId` 검증, Mongo 데이터 사전검사, unknown commit 수렴과 replica-set 통합 테스트 부족을 수정하기 위한 구현 계획서를 작성한다.
- 작성 결과: `docs/codex/TMI-125_FOLLOWUP_PRODUCTION_SAFETY_FIX_PLAN.md`를 신규 작성했다. 5줄 결론, 필독 사항, 확정 결정, 위험, 단계별 구현, 테스트 매트릭스, rollout·rollback과 파일별 변경 지도를 포함한다.
- strict 검증: 일반 reserve는 기존 Saga 검증이 있음을 반영해 실제 누락을 phone continuation discovery로 좁혔다. Billing 성공 응답 decode와 Saga의 durable operation 저장 직전에서 `attemptGroupId` lowercase UUID v4를 이중 검증하며 invalid 값은 저장·후속 Billing 호출 전에 fail-closed한다.
- migration: orphan `ExamResult`/`ExamSummary`와 참조 Session owner 불일치를 `$lookup` count-only로 검사하고 한 건이라도 있으면 dry-run/apply를 모두 막는다. 원시 식별자를 출력하거나 데이터를 자동 보정·삭제하지 않는다.
- unknown commit: owner migration을 blind retry하지 않고 `eventId + canonical digest` inbox를 bounded recheck하여 같은 digest는 `204`, 다른 digest는 `409`, 미확정 또는 조회 실패는 `503 + Retry-After: 1`로 수렴한다. Spring Transaction wrapper의 500 누출도 UserMerged 전용 advice에서 차단한다.
- 검증 gate: 실제 replica-set에서 후반 rollback, non-terminal operation, 동시 duplicate/conflict, source/target writer와 Callback 경합, unknown commit을 검증하고 unit·Node migration·`mongoIntegrationTest`·`git diff --check`를 PR 및 staging deploy 필수 gate로 두는 계획이다.
- 결정·운영 경계: 구현 전 추가 제품·wire 결정은 없다. 기존 공개 API·Billing field·AI `user_id=examId`·S3/Redis key는 유지하고 production UserMerged flag는 수정·검증·운영 gate 완료 전 OFF로 둔다.
- 변경·테스트·Git: 계획서와 상태 기록 문서만 변경했다. 애플리케이션·migration·test 코드는 수정하지 않아 Gradle/Node 테스트는 실행하지 않고 문서 형식과 `git diff --check`만 확인한다. commit, push, 배포, Jira 추가 변경, AWS·Mongo 운영 변경은 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-05 — TMI-125 후속 수정 계획 Jira 반영

<!-- codex-turn:01a070a6-0860-7e62-b9c2-f37aad69e5cd -->

- 날짜·브랜치·Jira: 2026-09-05, Learning Core `develop`, `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 사용자 요청: 작성한 후속 production safety 수정 계획을 Jira에 반영한다.
- 사전 확인: TMI-125는 `진행 중`이었고 기존 댓글에는 결함 네 건의 개요만 기록돼 있었다. 기존 설명과 댓글 이력을 보존하기 위해 설명 전체를 덮어쓰지 않고 상세 계획 댓글을 추가했다.
- Jira 처리: 댓글 ID `10047`로 phone continuation 및 Billing 성공 응답의 `attemptGroupId` lowercase UUID v4 이중 검증, migration orphan Result/Summary·Session owner mismatch count-only blocker, unknown commit의 eventId+digest inbox 204/409/503 수렴과 Transaction wrapper 503 mapping을 기록했다.
- 검증·완료 조건: 실제 replica-set의 rollback·non-terminal operation·동시 duplicate/conflict·source/target writer 및 Callback 경합·unknown commit 테스트, unit·Node migration·`mongoIntegrationTest`·`git diff --check`의 PR/staging 필수 gate를 Jira 댓글에 포함했다.
- 계약·운영 경계: 공개 API·Billing field·AI `user_id=examId`·retryCount·S3/Redis key 불변, orphan 자동 보정 금지, production UserMerged flag OFF 유지와 추가 제품·wire 결정 없음도 기록했다.
- 상태: Jira 상태는 `진행 중`으로 유지했다. 이 작업에서 애플리케이션·migration·test 코드, Jira 설명·workflow 상태, AWS·Mongo 운영 리소스와 배포는 변경하지 않았다.
- 테스트·Git·보안: 외부 Jira 기록과 문서 동기화만 수행해 Gradle/Node 테스트는 실행하지 않는다. `git diff --check`와 turn marker 단일 포함을 확인하고 commit·push는 수행하지 않으며 Secret·Token을 기록하지 않는다.

## 2026-09-05 — TMI-125 Jira 계획 반영 종료 훅 동기화

<!-- codex-turn:01a070a6-dbc5-7e61-a4b3-078576ace4b4 -->

- 날짜·브랜치·Jira: 2026-09-05, Learning Core `develop`, `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 현재 상태: TMI-125는 `진행 중`이고 후속 production safety 계획이 상세 댓글 `10047`로 반영돼 있다.
- 반영 범위: `attemptGroupId` UUID v4 strict 검증, migration orphan/owner mismatch 차단, unknown commit inbox 수렴, replica-set 경합 테스트와 CI gate다.
- 운영 경계: 구현과 전체 검증 전 production UserMerged writer/consumer/source-deny flag는 OFF로 유지한다.
- 변경 범위: 종료 훅 동기화를 위해 `docs/codex/CURRENT_STATE.md`와 `docs/codex/WORKLOG.md`만 추가 갱신했다. Jira·애플리케이션·AWS·Mongo·배포 상태는 추가 변경하지 않았다.
- 테스트·Git·보안: 코드 변경이 없어 Gradle/Node 테스트는 실행하지 않고 `git diff --check`만 확인한다. commit·push를 수행하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-05 — TMI-125 후속 production safety 구현

<!-- codex-turn:01a070a7-853f-7f81-8550-0ad3928d6fd0 -->

- 날짜·브랜치·Jira: 2026-09-05, Learning Core `feat/TMI-125-user-merged-ownership-migration`, 진행 중인 `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 사용자 요청: 확정하고 Jira에 반영한 후속 production safety 계획의 네 결함을 구현한다.
- Billing 경계: PhoneContinuation/Reserve/Confirm/Status 성공 응답 decode와 Saga의 discovery·PREPARED operation 저장 직전에 `attemptGroupId` lowercase canonical UUID v4를 검증한다. invalid discovery는 Session 준비·operation insert·reserve 전에 차단하며 기존 invalid operation replay도 Billing 호출 없이 fail-closed한다.
- migration: `user-merged-prepare.js`가 `exam_results`·`exam_summaries`와 `exam_sessions._id`를 `$lookup`해 orphan 및 owner mismatch 네 건수를 계산한다. 하나라도 있으면 dry-run/apply를 모두 막고 실제 식별자는 출력하지 않으며 자동 수정·삭제하지 않는다.
- unknown commit: `UserOwnedTransactionExecutor`가 cause chain의 unknown commit label을 transient보다 우선 분류해 mutation replay 없이 전용 예외로 전달한다. consumer는 eventId+digest inbox를 250ms bounded recheck하여 같은 digest는 duplicate `204`, 다른 digest는 `409`, 미확정은 `503 + Retry-After: 1`에 수렴한다. UserMerged 전용 advice는 Spring `TransactionException`도 빈 503으로 매핑한다.
- 통합 검증: replica-set 테스트를 기존 4개에서 11개로 늘려 마지막 inbox 실패 전체 rollback, non-terminal operation 무변경 재시도, concurrent duplicate, committed-response-loss inbox 수렴, merge 후 source writer 차단과 Feedback/Summary/Azure/SpeechAce Callback의 merge 전·후 owner 수렴을 추가했다.
- CI: 신규 `.github/workflows/verify.yml`이 develop/main PR에서 unit·Node migration·Mongo replica-set·diff 검사를 실행하고, staging deploy도 image build 전에 Node와 `mongoIntegrationTest`를 통과해야 한다.
- 테스트 결과: `./gradlew clean test --no-daemon` Java 496개 failures/errors 0, `node --test scripts/mongodb/user-merged-prepare.test.js` 7개 성공, `compileMongoIntegrationTestJava` 성공, workflow YAML parse와 `git diff --check` 성공이다. 실제 `./gradlew mongoIntegrationTest --no-daemon`은 로컬 Docker daemon 부재로 Testcontainers initialization error가 발생했으며 코드 실패로 완료 처리하지 않고 CI Docker 환경의 필수 gate로 남긴다.
- Jira 반영: 구현 범위와 테스트 결과, Docker CI 잔여 gate를 TMI-125 댓글 `10048`에 기록했고 이슈 상태는 `진행 중`으로 유지했다.
- 계약·운영 경계: 공개 API·DTO·`BaseResponse`, Billing wire field, AI `user_id=examId`, retryCount, S3/Redis key를 변경하지 않았다. production UserMerged writer/consumer/source-deny flag는 CI·migration·workload·staging 성능 gate 전 OFF로 유지한다.
- Git·외부 작업·보안: commit, push, PR, merge, 배포와 AWS·Mongo 운영 변경은 수행하지 않았다. 기존 사용자 변경 `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`는 수정하지 않았고 Secret·Token을 기록하지 않았다.

## 2026-09-05 — TMI-125 후속 구현 종료 훅 동기화

<!-- codex-turn:01a070a9-adeb-7ad0-85ec-cbaa8fe988fd -->

- 날짜·브랜치·Jira: 2026-09-05, Learning Core `feat/TMI-125-user-merged-ownership-migration`, `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`.
- 구현 상태: `attemptGroupId` UUID v4 strict 검증, migration orphan/owner mismatch 차단, unknown commit inbox 수렴·Transaction wrapper 503 mapping, replica-set/Callback 테스트와 CI gate 보강을 완료했다.
- 검증 상태: Java 496개와 Node 7개가 성공했고 integration test source compile, workflow YAML parse와 `git diff --check`도 성공했다. 실제 `mongoIntegrationTest`는 로컬 Docker daemon 부재로 Testcontainers initialization 단계에서 중단돼 CI Docker gate로 남아 있다.
- Jira 상태: 구현·검증 결과와 잔여 Docker gate를 댓글 `10048`에 기록했고 TMI-125는 `진행 중`으로 유지했다.
- 운영 경계: CI replica-set, migration, workload와 staging 성능 검증 전 production UserMerged writer/consumer/source-deny flag는 OFF로 유지한다.
- 외부 작업·Git·보안: commit, push, PR, merge, 배포와 AWS·Mongo 운영 변경은 수행하지 않았고 Secret·Token을 기록하지 않았다.
