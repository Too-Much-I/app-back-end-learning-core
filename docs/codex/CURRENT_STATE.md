# Learning Core Current State

## Last updated

- 2026-07-28

## Current branch

- `chore/learning-core-codex-worklog`

## Current Jira issue

- `TMI-10` — [Learning Core] Identity JWKS 기반 JWT 인증 연동

## Completed

- 기존 웹 POC 백엔드에서 앱용 Learning Core 분리
- trial API 제거
- terminate API 제거
- `ExamSession` 추가 및 `examId`와 실제 `userId` 저장
- `CurrentUserProvider` 추상화
- 고정 개발 UUID를 반환하는 `LegacyCurrentUserProvider`
- `ExamResult.userId` 추가
- Feedback Callback에서 `examId`로 `ExamSession` 조회
- `ExamSession.userId`를 `ExamResult.userId`에 저장
- 사용자용 시험 API 6개에 시험 소유권 검증 적용
- Python AI 요청의 `user_id = examId` 계약 유지
- 기존 API URL·DTO·`retryCount`·Redis·S3 흐름 유지
- Learning Core용 append-only WORKLOG, 최신 상태 문서와 Codex 작업 기록 Hook 구성

## In progress

- Jira `TMI-10` 구현 착수 전 상태다.
- 이번 작업에서는 애플리케이션 Java 코드와 인증 설정을 변경하지 않는다.

## Next

- OAuth2 Resource Server 추가
- Identity JWKS 기반 RS256 서명 검증
- issuer 검증
- audience `tosunsaeng-learning-core` 검증
- `JwtCurrentUserProvider` 추가
- Legacy와 JWT 인증 모드 분리
- JWT `sub`를 실제 `userId`로 사용
- 사용자용 API 인증 강제
- AI Callback 공개 유지
- 기존 HMAC JWT 코드 사용 여부 정리

## Important contracts

- JWT `sub`는 실제 `userId`다.
- JWT audience는 `tosunsaeng-learning-core`다.
- JWT 서명은 Identity JWKS endpoint를 이용해 검증한다.
- Learning Core는 매 요청마다 Identity의 토큰 검증 API를 호출하지 않는다.
- Python AI의 `user_id`는 계속 `examId`다.
- 실제 `userId`를 Python AI 서버로 보내지 않는다.
- 클라이언트 Request와 Response에 `userId`를 추가하지 않는다.
- AI Callback의 `user_id`도 `examId`로 해석한다.
- 기존 공개 API 계약과 `retryCount` 방식을 유지한다.
- 기존 Redis, S3 Presigned URL, 음성 제출과 Polling 흐름을 유지한다.

## Test status

- `./gradlew clean test` 성공: 34개 테스트, 실패·오류·건너뜀 0개
- Python Hook 3개의 `python3 -m py_compile` 문법 검사 성공
- `.codex/hooks.json` JSON 형식 검사 성공
- 격리된 임시 Git 저장소에서 SessionStart 상태 주입·상태 파일 부재 처리, UserPromptSubmit marker 주입, Stop 1차 차단·2차 fallback·기존 marker 허용 시나리오 성공
- `git diff --check` 성공
- Identity와 Learning Core의 실제 E2E 연동은 아직 수행하지 않았다.

## Known risks

- `LegacyCurrentUserProvider`가 아직 존재한다.
- 기존 HMAC 기반 `JwtAuthenticationFilter`와 `JwtTokenProvider`가 남아 있을 수 있다.
- AI Callback 서비스 간 인증은 아직 없다.
- Legacy와 JWT 모드를 동시에 활성화하면 Bean 충돌이 발생할 수 있다.
- 운영 앱 환경에서는 Legacy 모드를 비활성화해야 한다.
- 프로젝트와 각 Hook 정의를 신뢰하기 전에는 로컬 Hook이 실행되지 않는다.
- Hook 명령이 변경되면 정의의 hash가 바뀌므로 다시 검토하고 신뢰해야 한다.
