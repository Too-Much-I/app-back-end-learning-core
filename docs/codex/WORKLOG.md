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
