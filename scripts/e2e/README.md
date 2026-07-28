# Identity–Learning Core JWT E2E 테스트

`auth-integration-test.sh`는 실제로 실행 중인 Identity와 Learning Core 사이의 JWT 인증 계약을 로컬에서 검증한다. 테스트 사용자를 두 명 생성하므로 운영 환경에서는 실행하지 않는다.

## 사전 조건

- Bash, `curl`, `jq`
- Identity가 기본 `http://localhost:8081`에서 실행 중이어야 한다.
- Identity의 `/actuator/health`와 `/.well-known/jwks.json`에 접근할 수 있어야 한다.
- Learning Core가 기본 `http://localhost:8080`에서 `APP_AUTH_MODE=jwt`로 실행 중이어야 한다.
- Identity의 issuer와 Learning Core의 `IDENTITY_ISSUER`가 Identity Base URL과 정확히 같아야 한다. 기본값은 `http://localhost:8081`이다.
- Learning Core의 `IDENTITY_JWK_SET_URI`는 Identity의 `/.well-known/jwks.json`, audience는 `tosunsaeng-learning-core`여야 한다.
- 두 서버가 사용하는 로컬 MongoDB, Redis, S3 설정 등 시험 생성에 필요한 개발 인프라가 준비돼 있어야 한다.
- 테스트 전용 또는 폐기 가능한 로컬 데이터베이스를 사용해야 한다. 운영 데이터와 실제 사용자 개인정보를 사용하지 않는다.

Identity는 8081에서 실행하고 Learning Core는 다음과 같이 JWT 모드로 실행하는 구성이 기준이다. 실제 자격증명이나 URI는 각 서버의 안전한 로컬 환경 설정으로 주입한다.

```bash
APP_AUTH_MODE=jwt \
IDENTITY_ISSUER=http://localhost:8081 \
IDENTITY_JWK_SET_URI=http://localhost:8081/.well-known/jwks.json \
IDENTITY_AUDIENCE=tosunsaeng-learning-core \
./gradlew bootRun
```

## 환경변수

| 변수 | 기본값/동작 |
| --- | --- |
| `IDENTITY_BASE_URL` | `http://localhost:8081` |
| `LEARNING_CORE_BASE_URL` | `http://localhost:8080` |
| `E2E_TEST_PASSWORD` | 테스트 사용자 비밀번호. 없으면 대화형 터미널에서 숨김 입력을 요청하며, 비대화형 실행에서는 명확한 오류로 종료한다. |
| `E2E_KEEP_TEST_DATA` | 기본 `false`. `true`이면 시나리오 외의 추가 Refresh Session 정리를 건너뛰고 수동 확인용 식별자를 출력한다. |

비밀번호는 코드, 문서, 명령행 예시에 직접 쓰지 않는다. CI나 비대화형 환경에서는 Secret 저장소가 환경변수로 주입하도록 구성하고, 로컬에서는 환경변수를 생략해 스크립트의 숨김 입력을 사용하는 편이 안전하다.

## 실행

저장소 루트에서 실행한다.

```bash
./scripts/e2e/auth-integration-test.sh
```

서버 주소가 다르면 다음처럼 Base URL만 지정한다. 비밀번호는 이어지는 숨김 입력 프롬프트에서 입력한다.

```bash
IDENTITY_BASE_URL=http://localhost:8081 \
LEARNING_CORE_BASE_URL=http://localhost:8080 \
./scripts/e2e/auth-integration-test.sh
```

성공하면 exit code `0`, 실패하면 실패 단계와 안전한 HTTP 상태·`BaseResponse` 요약을 출력하고 non-zero로 종료한다. Access Token, Refresh Token, Token이 포함된 전체 Identity 응답, URL을 포함할 수 있는 전체 시험 생성 응답은 출력하지 않는다.

## 자동 검증 시나리오

스크립트 한 번으로 다음을 검증한다.

1. Identity health, 표준 JWKS, Learning Core HTTP 접근 가능 여부
2. JWKS의 RSA/서명/RS256 공개키 필드와 Private Key 파라미터 비노출
3. 첫 번째 사용자 회원가입·로그인·`/api/v1/users/me`
4. JWT Header의 `alg`, `kid`와 Payload의 `sub`, `iss`, `aud`, `iat`, `exp`, `jti`, `scope`
5. Learning Core 무토큰 시험 생성 `401`과 `COMMON401`
6. 유효한 Token의 시험 생성 `200`, 동일 사용자 상태 조회 `200`
7. 두 번째 사용자의 첫 번째 사용자 시험 조회 `403`과 `COMMON403`
8. 임의 문자열 Token, signature 일부를 바꾼 Token, Token 없음의 `401`
9. Refresh Token Rotation, 이전 Token 재사용 탐지, 새 Access Token의 `/users/me` 성공
10. 단일 로그아웃 후 재발급 실패
11. 다시 로그인해 활성 Session을 만든 뒤 전체 로그아웃과 재발급 실패
12. 존재하지 않는 안전한 `examId`를 사용한 Feedback Callback 무인증 접근과 도메인 오류

만료 Token, 잘못된 issuer, 잘못된 audience는 공개 API만으로 안전하게 발급할 수 없으므로 E2E 스크립트에서 서명되지 않은 가짜 JWT를 만들지 않는다. 이 조건들은 Learning Core의 `JwtSecurityIntegrationTest`에서 합성 RSA 키와 테스트 JWKS를 사용해 검증된다.

서명 자체는 스크립트가 검증하지 않는다. Payload 디코딩은 Claim 계약을 확인하기 위한 것이며, 실제 RS256 서명·issuer·audience 검증은 Learning Core Resource Server가 시험 생성 요청에서 수행한다.

## 테스트 데이터 정리 정책

- 모든 임시 요청·응답·Token Header 파일은 권한이 제한된 임시 디렉터리에 저장되고 `trap`으로 삭제된다.
- 기본값 `E2E_KEEP_TEST_DATA=false`에서는 시나리오 종료 또는 실패 시 남아 있을 수 있는 Refresh Session에 best-effort 로그아웃을 수행한다.
- Rotation, 단일 로그아웃, 전체 로그아웃은 검증 시나리오 자체이므로 `E2E_KEEP_TEST_DATA=true`여도 해당 Session 폐기를 되돌리지 않는다.
- Identity에는 테스트 사용자 삭제 API가 없고 Learning Core에는 시험 삭제 API가 없으므로 사용자 계정, `ExamSession`, 시험 관련 문서는 자동 삭제하지 않는다.
- 로컬 폐기 가능 DB에서 `e2e-auth-` 이메일 prefix와 스크립트가 출력한 수동 확인 식별자를 기준으로 운영자가 정리한다. MongoDB URI나 자격증명을 스크립트에 추가하지 않는다.

단일 로그아웃과 전체 로그아웃은 Refresh Session을 폐기한다. 이미 발급된 Access Token을 블랙리스트에 넣지 않으므로 기존 Access Token은 `exp`까지 유효할 수 있다.

## 수동 MongoDB 확인

`ExamSession.userId`와 JWT `sub`의 직접 DB 비교는 Secret이 필요한 자동화에 넣지 않는다. 폐기 가능한 로컬 DB에서 확인할 때만 `E2E_KEEP_TEST_DATA=true`로 실행하고, 성공 로그에 표시된 `examId`와 기대 `userId`를 사용한다.

승인된 로컬 MongoDB 도구로 `exam_sessions` 컬렉션에서 `_id`가 출력된 `examId`인 문서를 조회한다. 조회된 `userId`가 로그의 기대 `userId` 및 첫 번째 사용자의 `/users/me` userId와 같고, `examId`와는 다른지 확인한다. 실제 MongoDB URI, 비밀번호 또는 Token을 명령·문서·로그에 복사하지 않는다.

## 알려진 경계와 위험

- `/api/v1/exams/callback/**`은 Python AI 연동을 위해 사용자 JWT 없이 공개돼 있다. 서비스 간 인증은 아직 없으므로 별도 위험으로 관리한다.
- Callback 외부 JSON의 `user_id`는 실제 사용자 UUID가 아니라 `examId`다.
- Learning Core가 Python AI로 보내는 `user_id`도 `examId`이며 실제 `userId`를 포함하지 않는다. 이 outbound 계약은 기존 Java 단위 테스트가 검증하며, 본 스크립트는 실제 음성·AI 서버 호출을 만들지 않는다.
- JWKS key rotation, 캐시 동작, Identity 장애 중 기존 키 사용은 이 로컬 시나리오의 범위 밖이다.
- 이 스크립트는 로컬 통합 검증 전용이다. 운영 환경에서 실행하지 않는다.

