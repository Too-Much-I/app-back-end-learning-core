# Identity–Learning Core JWT 계약

이 문서는 Identity가 발급한 Access Token을 Learning Core가 검증하고 사용자 식별에 사용하는 서버 간 계약을 정의한다.

## Access Token

JWT Header 계약은 다음과 같다.

| Header | 계약 |
| --- | --- |
| `alg` | `RS256` |
| `kid` | 필수. Identity JWKS의 서명 공개키를 식별한다. |

필수 Claim 계약은 다음과 같다.

| Claim | 계약 |
| --- | --- |
| `sub` | 실제 사용자 `userId`인 UUID 문자열 |
| `iss` | 환경별 Identity issuer 설정값. Learning Core 설정과 정확히 일치해야 한다. |
| `aud` | `tosunsaeng-learning-core`를 포함한다. |
| `iat` | 발급 시각 |
| `exp` | 만료 시각이며 `iat`보다 뒤여야 한다. |
| `jti` | Access Token별 고유 식별자 |
| `scope` | 권한을 공백으로 구분한 문자열 |

Identity만 RSA Private Key를 보유한다. Private Key, 비밀번호, Refresh Token, 사용자 개인정보는 Access Token이나 JWKS에 포함하지 않는다.

## JWKS와 Learning Core 검증

- Identity JWKS URL은 `${IDENTITY_BASE_URL}/.well-known/jwks.json`이며 로컬 기본값은 `http://localhost:8081/.well-known/jwks.json`이다.
- JWKS에는 RSA Public Key의 `kty`, `use`, `alg`, `kid`, `n`, `e`만 필요한 범위로 공개하고 `d`, `p`, `q`, `dp`, `dq`, `qi`를 공개하지 않는다.
- Learning Core는 `kid`에 맞는 JWKS Public Key로 RS256 서명을 로컬 검증한다.
- Learning Core는 서명과 함께 issuer, audience, `exp`, `nbf`, UUID `sub`를 검증한다.
- Learning Core는 매 요청마다 Identity에 Token 검증 또는 사용자 조회 요청을 보내지 않는다.
- Learning Core JWT 모드의 audience는 `tosunsaeng-learning-core`다.

## 사용자 식별과 요청 계약

- 검증된 JWT `sub`가 Learning Core의 실제 `userId`다.
- 클라이언트 Request Body, Path Parameter, Query Parameter에 `userId`를 추가하지 않는다.
- 클라이언트는 보호 API에 `Authorization: Bearer <ACCESS_TOKEN>`만 전달한다.
- Learning Core는 시험 생성 시 `examId -> userId`를 `ExamSession`에 저장하고, 사용자용 `examId` API에서 소유권을 검증한다.
- 외부 Learning Core Response DTO에 실제 `userId`를 추가하지 않는다.

## Python AI 경계

Python AI의 기존 `user_id`는 인증 사용자 식별자가 아니라 시험 식별자다.

| 경로 | `user_id` 의미 |
| --- | --- |
| Learning Core → Python AI | `examId` |
| Python AI → Learning Core Callback | `examId` |

실제 `userId`를 Python AI 요청이나 Callback JSON에 넣지 않는다. Callback은 `user_id`를 `examId`로 해석하고 `ExamSession`에서 실제 `userId`를 찾는다.

## 인증 모드와 로그아웃

- `legacy` 모드는 기존 웹 POC 호환과 제한된 개발 단계 전용이다.
- 운영 앱 환경에서는 `legacy` 모드를 금지하고 Learning Core를 JWT 모드로 실행한다.
- 단일 `logout`은 전달된 Refresh Session을 폐기한다.
- `logout-all`은 검증된 JWT `sub` 사용자의 활성 Refresh Session을 모두 폐기한다.
- 현재 로그아웃은 Access Token 즉시 무효화를 보장하지 않는다. 이미 발급된 Access Token은 `exp`까지 유효할 수 있다.

