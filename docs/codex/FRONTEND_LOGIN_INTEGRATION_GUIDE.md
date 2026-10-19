# 프론트엔드 로그인·회원 전환 연동 가이드

## 1. 5줄 결론

1. 앱은 Firebase credential을 Learning Core에 보내지 않고, Identity에서 교환한 Identity Access Token만 `Authorization: Bearer`로 보낸다.
2. 기존 MEMBER 로그인은 `Firebase 로그인 → ID Token → POST /api/v1/auth/firebase/exchange → Identity Token 저장` 순서다.
3. 신규 MEMBER는 Google·Apple 등 primary 로그인 뒤 **같은 Firebase User에 휴대전화 credential을 반드시 link**하고 강제 갱신한 ID Token으로 가입을 완료한다.
4. Access Token은 기본 30분, Refresh Token은 기본 14일이며 `/reissue` 성공 때 두 Token을 모두 원자적으로 교체해야 한다.
5. Firebase flag는 기본 off이고 Guest merge의 downstream 이전도 출시 전 종단 검증이 필요하므로, 환경별 활성화 확인 없이 로그인 UI를 노출하면 안 된다.

## 2. 프론트가 반드시 알아야 하는 내용

### 2.1 Token의 역할

| Token | 사용처 | 프론트 처리 |
|---|---|---|
| Firebase ID Token | Identity의 Firebase 인증·가입 API에만 제출 | Firebase SDK에서 필요 시 fresh token을 얻고 장기 인증 토큰처럼 사용하지 않음 |
| Identity Access Token | Identity 인증 API와 Learning Core API | 모든 보호 API에 `Authorization: Bearer {accessToken}` |
| Identity Refresh Token | Identity `/reissue`, 단일 `/logout` | OS 보안 저장소에 저장하고 일반 API에는 전송하지 않음 |

- 앱 Request Body·Path·Query에 `userId`를 넣지 않는다. 서버가 Identity Access Token의 `sub`에서 사용자를 식별한다.
- Token, Firebase credential, 비밀번호를 로그·analytics·crash report에 남기지 않는다.
- 성공과 실패는 HTTP status만 보지 말고 공통 응답의 `isSuccess`, `code`, `result`를 함께 확인한다.

```json
{
  "isSuccess": true,
  "code": "SUCCESS",
  "message": "요청에 성공했습니다.",
  "result": {}
}
```

### 2.2 앱 시작 시 인증 복원

1. 저장된 Identity Refresh Token이 없으면 비로그인 화면으로 간다.
2. 있으면 `POST /api/v1/auth/reissue`로 세션을 복원한다.
3. 성공하면 응답의 새 Access Token과 새 Refresh Token을 **한 번에 교체 저장**한다.
4. `INVALID_REFRESH_TOKEN`, `REFRESH_TOKEN_EXPIRED`, `REFRESH_TOKEN_REUSE_DETECTED`, `ACCOUNT_WITHDRAWN`, `ACCOUNT_MERGED_TOKEN_REJECTED`이면 로컬 Token을 모두 지우고 로그인 화면으로 간다.
5. 네트워크 오류·5xx는 로그아웃으로 확정하지 말고 재시도 가능한 일시 장애로 처리한다.

Refresh Token은 rotation된다. `/reissue` 성공 후 이전 Refresh Token을 다시 사용하면 reuse로 거절될 수 있으므로 동시에 여러 reissue를 보내지 말고 앱 전체에서 single-flight로 묶는다.

## 3. 로그인 수단별 화면 흐름

### 3.1 기존 Google·Apple MEMBER 로그인

```text
Firebase SDK로 Google 또는 Apple 로그인
→ Firebase ID Token 발급
→ POST /api/v1/auth/firebase/exchange
→ result.type 확인
```

요청:

```json
{ "firebaseIdToken": "<firebase-id-token>" }
```

응답 분기:

| `result.type` | 의미 | 프론트 동작 |
|---|---|---|
| `AUTHENTICATED` | 기존 ACTIVE MEMBER | Identity Access/Refresh Token 저장 후 앱 진입 |
| `ENROLLMENT_REQUIRED` | 아직 내부 MEMBER가 없음 | `enrollmentId`, `expiresIn`, `missingRequirements`를 보관하고 가입 화면 진행 |

기존 MEMBER는 매 로그인마다 SMS 인증을 반복하지 않는다. Firebase에서 phone-only sign-in한 credential은 로그인 수단으로 인정하지 않는다.

### 3.2 신규 Google·Apple MEMBER 가입

`ENROLLMENT_REQUIRED`를 받은 뒤 다음 순서를 지킨다.

```text
닉네임·필수 약관 입력
→ SMS 인증으로 PhoneAuthCredential 획득
→ 현재 Firebase User에 phone credential link
→ link 전후 Firebase UID 동일성 확인
→ getIdToken(forceRefresh=true)
→ POST /api/v1/auth/firebase/signup
```

가입 요청:

```json
{
  "enrollmentId": "<uuid>",
  "firebaseIdToken": "<force-refreshed-token>",
  "nickname": "사용자닉네임",
  "isPrivacyConsented": true,
  "privacyConsentVersion": "<server-current-version>",
  "isTermConsented": true,
  "termConsentVersion": "<server-current-version>"
}
```

- enrollment 기본 유효시간은 10분이다. 만료·충돌 시 `/exchange`부터 다시 시작한다.
- 가입·Guest 승격용 Firebase 인증은 기본 5분 recent-auth 조건을 만족해야 한다.
- phone credential로 별도 Firebase User에 로그인하면 안 된다. 처음 로그인한 **동일 Firebase User에 link**해야 한다.
- 휴대전화 번호는 ACTIVE MEMBER당 하나의 소유권만 허용한다. 충돌했다고 자동으로 계정을 합치지 않는다.
- 가입 성공 응답의 Identity Token을 저장한 후 Firebase ID Token을 Learning Core에 보내지 않는다.

### 3.3 Guest 최초 이용

`POST /api/v1/auth/guest`에 앱이 생성한 UUID v4 `installationId`와 현재 약관 동의를 보낸다.

- installationId는 중복 방지 값이지 인증 credential이 아니다.
- 같은 installationId로 Guest Token을 복구할 수 없다.
- 응답 유실이나 Token 분실 시 installationId만으로 기존 Guest를 되찾을 수 없다는 UX를 고려해야 한다.
- Guest 응답의 Identity Access/Refresh Token도 MEMBER와 동일한 방식으로 저장·갱신한다.

### 3.4 Guest가 새 MEMBER로 승격

1. 현재 Guest Identity Access Token을 Bearer로 유지한다.
2. Firebase SDK로 Google·Apple 등에 인증한다.
3. `POST /api/v1/auth/firebase/guest/prepare`에 Firebase ID Token을 보내고 결과를 분기한다.

| prepare 결과 | 프론트 동작 |
|---|---|
| `ENROLLMENT_REQUIRED` | 동일 Firebase User에 phone을 link하고 fresh token을 받아 `/guest/upgrade` 호출 |
| `ALREADY_LINKED` | 이미 연결된 상태이므로 프로필 재조회 또는 정상 앱 진입 |
| `MERGE_REQUIRED` | 신규 승격을 중단하고 기존 MEMBER 통합 확인 흐름으로 이동 |

`/guest/upgrade`에는 Guest Bearer Token과 `enrollmentId`, force-refreshed Firebase ID Token, 닉네임, 필수 동의를 함께 보낸다. 성공하면 기존 Guest Refresh Token은 폐기되므로 응답의 MEMBER Token으로 모두 교체한다. 같은 내부 userId가 유지된다.

### 3.5 Guest를 기존 MEMBER로 통합

`MERGE_REQUIRED`인 경우 사용자의 명시적 확인 뒤 `POST /api/v1/auth/firebase/guest/merge`를 호출한다.

- Header: 현재 Guest의 Identity Access Token
- Body: 기존 MEMBER로 새로 인증한 fresh Firebase ID Token
- 성공: 응답의 target MEMBER Identity Token으로 로컬 Token을 전부 교체
- 기존 Guest Token은 이후 `ACCOUNT_MERGED_TOKEN_REJECTED`가 될 수 있다.
- phone이나 이메일 일치만 보고 프론트에서 target 계정을 추정하면 안 된다.
- 이 흐름은 Identity 성공만으로 출시하지 말고 Billing·Learning Core의 Guest 소유 데이터 이전과 staging E2E가 끝난 뒤 활성화한다.

### 3.6 이메일 로그인

현재 코드에는 `POST /api/v1/auth/login`이 있으며 body는 `email`, `password`다. 성공 응답에는 Access Token, Refresh Token, `grantType=Bearer`, Access Token 만료시간이 있다.

다만 Firebase 전환 계획은 운영에서 legacy password route와 Firebase dual credential writer를 동시에 열지 않는 방향이다. 프론트는 출시 전에 제품 결정과 해당 환경의 활성 경로를 확인해야 하며, 신규 UI가 임의로 두 가입 방식을 동시에 제공하면 안 된다.

## 4. Token 갱신·로그아웃

### 4.1 재발급

```http
POST /api/v1/auth/reissue
Content-Type: application/json
```

```json
{ "refreshToken": "<identity-refresh-token>" }
```

- 성공하면 Access/Refresh Token을 모두 교체한다.
- 보호 API의 401에서 reissue는 한 번만 수행하고 원 요청도 한 번만 재시도한다.
- reissue 자체가 401이면 무한 반복하지 않고 인증 상태를 초기화한다.

### 4.2 단일 기기 로그아웃

`POST /api/v1/auth/logout` body에 Refresh Token을 보낸다. 서버 처리는 멱등이다. 응답 성공 후 Identity Token을 지우고 Firebase SDK sign-out도 수행한다.

### 4.3 전체 로그아웃

`POST /api/v1/auth/logout-all`은 Identity Access Token Bearer가 필요하며 사용자의 모든 Identity RefreshSession을 폐기한다. 이후 현재 기기의 로컬 Token과 Firebase 로그인 상태도 지운다.

## 5. 주요 오류별 프론트 처리

| code | 권장 UX |
|---|---|
| `INVALID_CREDENTIALS` | 이메일·비밀번호 오류 표시 |
| `INVALID_FIREBASE_ID_TOKEN` | Firebase 재로그인 후 fresh token으로 한 번 재시도 |
| `FIREBASE_RECENT_AUTH_REQUIRED` | Google·Apple 재인증 또는 SMS 재인증 후 재시도 |
| `FIREBASE_PHONE_VERIFICATION_REQUIRED` | 동일 Firebase User의 휴대전화 인증·link 화면으로 이동 |
| `FIREBASE_EMAIL_VERIFICATION_REQUIRED` | 이메일 인증 안내 후 token 강제 갱신 |
| `FIREBASE_PROVIDER_NOT_ALLOWED` | 해당 로그인 수단이 현재 환경에서 지원되지 않음을 표시 |
| `PHONE_ALREADY_LINKED` | 자동 merge하지 말고 기존 계정 로그인·복구 안내 |
| `MERGE_REQUIRED` | 사용자 확인이 있는 Guest→기존 MEMBER 통합 화면으로 이동 |
| `FIREBASE_ENROLLMENT_RESTART_REQUIRED`, `FIREBASE_ENROLLMENT_CONFLICT` | enrollment를 버리고 Firebase 로그인 교환부터 재시작 |
| `FIREBASE_IDENTITY_CONFLICT`, `SOCIAL_IDENTITY_CONFLICT` | 로컬에서 임의 복구하지 말고 재로그인 안내; 반복 시 고객지원 |
| `FIREBASE_RATE_LIMITED` | countdown/backoff 후 재시도, 연속 버튼 입력 차단 |
| `FIREBASE_UNAVAILABLE` | 계정 없음으로 처리하지 말고 일시 장애 안내 |
| `ACCOUNT_WITHDRAWN` | Token 삭제 후 탈퇴 계정 안내 |
| `WITHDRAWAL_CLEANUP_PENDING` | 재가입을 완료하지 말고 정리 중 안내 후 나중에 재시도 |
| `ACCOUNT_MERGED_TOKEN_REJECTED` | Guest Token 삭제 후 target MEMBER 재로그인 |

## 6. 프론트 구현 체크리스트

- [ ] Firebase ID Token과 Identity Token을 타입 수준에서 구분한다.
- [ ] Access Token만 Learning Core Bearer로 보낸다.
- [ ] Refresh Token은 OS 보안 저장소에 보관한다.
- [ ] reissue single-flight와 원 요청 1회 재시도를 구현한다.
- [ ] signup/upgrade 전 동일 Firebase UID에 phone credential을 link한다.
- [ ] phone link 후 `forceRefresh=true` Token을 사용한다.
- [ ] `AUTHENTICATED`와 `ENROLLMENT_REQUIRED`를 HTTP status가 아닌 `result.type`으로 분기한다.
- [ ] enrollment 만료 시 `/exchange`부터 재시작한다.
- [ ] Guest `MERGE_REQUIRED`에는 사용자 확인을 받는다.
- [ ] 로그아웃 시 Identity Token과 Firebase SDK 상태를 함께 정리한다.
- [ ] Token·credential·비밀번호가 log, analytics, crash report에 포함되지 않게 한다.
- [ ] dev/staging에서 Google·Apple·phone flag와 실제 Firebase project 설정을 확인한다.
- [ ] Guest merge는 Identity·Billing·Learning Core 종단 검증 뒤에만 노출한다.

## 7. 현재 구현 상태와 출시 전 확인

- Identity의 LOCAL·Guest·Firebase exchange/signup/guest prepare/upgrade/merge, reissue, logout API 코드는 존재한다.
- Firebase 전체와 Google·Apple·phone provider flag 기본값은 off다.
- 기본 설정은 Access Token 30분, Refresh Token 14일, login recent-auth 15분, 고위험 작업 recent-auth 5분, enrollment 10분이다. 배포 환경 override를 최종 기준으로 삼아야 한다.
- 관련 구현 이력은 Identity `TMI-109`, `TMI-111`, `TMI-114`, `TMI-123`, Learning Core `TMI-116`, `TMI-118`, `TMI-122`, Billing `TMI-120`이다.
- `TMI-123` owner-event fan-out과 Learning Core Guest `UserMerged` consumer, cross-service staging E2E 완료 여부를 확인하기 전에는 Guest merge를 production에 노출하지 않는다.

## 부록 A. 코드·계약 근거

- Identity Firebase API: `/Users/msde76/identity/src/main/java/web/tosunsaeng/identity/domain/auth/federation/api/FirebaseExchangeController.java`
- LOCAL·Guest·Session API: `/Users/msde76/identity/src/main/java/web/tosunsaeng/identity/domain/auth/common/api/AuthController.java`
- Token 설정: `/Users/msde76/identity/src/main/resources/application.yml`
- Firebase·phone 계약: `/Users/msde76/identity/docs/adr/ADR-001-firebase-authentication-broker.md`
- SNS 구현 계획: `/Users/msde76/identity/docs/contracts/social-login-implementation-plan.md`
- Identity–Learning Core JWT 계약: `/Users/msde76/identity/docs/contracts/identity-learning-jwt.md`
