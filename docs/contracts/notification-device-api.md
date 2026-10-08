# 알림 기기 API · TMI-198 / TMI-63 공통 기반

## 핵심 계약

- 2026-10-08 전환 정책: 구서버 제출 추적/종료를 기다리지 않는다. `app.notifications.tracking-ready-at`은 신서버 제출 추적 준비 시각이며, 해당 시각 이후 당일 21:00–21:15 KST에도 발송 가능하다. 과거 시험의 제출 완료 시각 누락만으로 사용자를 제외하지 않는다. 구앱에서 완료 후 신앱으로 전환한 사용자에게 일일 알림이 갈 수 있음은 허용한다. 확인된 당일 제출, 명시적 일일 suppression, 탈퇴·병합·삭제 guard, 기기 동의/활성 및 일일 중복 방지는 유지한다. 실제 발송 기본 OFF/dry-run ON과 기동 검증은 유지한다.

- 신규 Learning Core API다. 기존 시험·회원가입·AI API는 변경하지 않는다.
- TMI-63에 기재된 `PUT /api/v1/notifications/devices`와 body `installationId`, `platform`, `pushToken`을 사용한다. 일일 알림은 MEMBER만 발송하며 이 기기 API는 향후 채점 완료 알림의 GUEST 기기도 등록할 수 있다. 채점 완료 알림·재시도 자체는 구현하지 않았다.
- 검증된 JWT의 `sub`와 `account_type`만 사용한다. MEMBER/GUEST 이외, claim 누락, Legacy 인증은 403이다. 요청·응답에 userId를 넣지 않는다.
- 설치 소유권 보강: 모든 요청에 `X-Installation-Secret`이 필요하다. 앱은 설치당 암호학적 난수 32바이트를 생성하고 padding 없는 base64url 43자로 전송한다. 설치 UUIDv4와 함께 안전하게 보관하며 계정 전환 시 유지한다. 서버에는 SHA-256만 저장한다. 값은 로그·분석·크래시 리포트에 넣지 않는다.
- 별도 가입 동의, 수신 동의 테이블, 설정 토글 API는 없다. OS 권한이 원본이고 서버 값은 앱의 마지막 보고다.

## PUT `/api/v1/notifications/devices`

필수 body: `installationId`(소문자 UUIDv4), `platform`(`IOS` 또는 `ANDROID`), `pushToken`(20~4096자 FCM 토큰).

추가 optional body:

| 필드 | 계약 |
| --- | --- |
| `permission` | `AUTHORIZED`, `DENIED`, `UNKNOWN`; 생략은 UNKNOWN(발송 제외) |
| `permissionObservedAt` | UTC offset 포함 ISO Instant 문자열. AUTHORIZED/DENIED 보고 시 필수. UNKNOWN 생략 시 서버 접수 시각. 미래60초 초과/30일 이전 거절. 기존 관측값보다 오래된 보고409 |

body 최대8 KiB, 알 수 없는 필드 거절. 인증 사용자당 분당60회, 활성 기기 최대20개. 업데이트는 같은 설치ID와 secret으로 수행한다. 다른 설치에 연결된 동일 토큰은409이며 자동으로 뺏지 않는다. 계정 전환은 동일 설치 secret을 가진 인증된 새 계정만 소유자를 바꿀 수 있다. 앱은 로그아웃 전 DELETE 후 로그인 계정으로 PUT한다.

OS 매핑:

- iOS authorized → AUTHORIZED. denied → DENIED. notDetermined/provisional/ephemeral → UNKNOWN(보수적으로 제외).
- Android 앱 알림 허용 + 학습 알림 채널 활성 → AUTHORIZED. 앱 또는 채널 거부 → DENIED. 미확인 → UNKNOWN. 알림 표시 여부는 OS가 최종 결정한다.
- 로그인 후, FCM token refresh, 앱 foreground 복귀 때 OS 상태를 다시 읽고 PUT한다. 보고가30일 이상 오래되면 일일 발송 제외다.

## DELETE `/api/v1/notifications/devices/{installationId}`

JWT + `X-Installation-Secret`, body 없음. 현재 사용자 소유와 secret을 확인한다. 존재하지 않거나 이미 비활성인 기기는 멱등 성공(기존 tombstone이 있으면 secret 검증 유지). 다른 활성 소유자는403.

토큰·token hash·owner·권한 관측 시각을 제거하고 version을 증가시킨다. 설치ID와 proof hash 등 최소 tombstone은 설치 소유권 보호를 위해 유지한다. 앱 재설치는 새 installationId/secret을 발급한다.

## 응답과 오류

성공은 기존 `BaseResponse.onSuccess(SuccessStatus.OK, null)` 구조다. userId/토큰/secret은 반환하지 않는다.

오류 HTTP/code: 400/`NOTIFICATION_400` 잘못된 요청, 403/`NOTIFICATION_403` 인증·소유권·기능 비활성, 409/`NOTIFICATION_409` 설치 증명·토큰 충돌/오래된 관측, 429/`NOTIFICATION_429` 등록 제한, 503/`NOTIFICATION_503` DB 일시 실패 또는 불명 결과. 원문 예외·토큰을 응답하지 않는다. 네트워크 불명 시 동일 설치 정보로 PUT/DELETE 재요청 가능하나 이는 푸시 재발송과 별개다.

## 수신 payload

notification: 제목 `오늘의 모의고사`, 본문 `오늘 모의고사로 영어 연습을 이어가 볼까요?`.

data: `notificationId`(opaque SHA-256), `type=DAILY_EXAM_REMINDER`, `route=MOCK_EXAM_HOME`. userId·examId·점수·음성을 포함하지 않는다. 앱은 클릭 시 현재 로그인 상태를 확인하고 모의고사 홈으로 이동한다. TTL은 KST21:15까지 남은 시간(최대900초), collapse key는 일일 알림 유형이다. collapse는 업무 멱등성 보장이 아니다.

TMI-58 모바일 SDK/권한·클릭 처리와 TMI-63 설치 secret 추가의 모바일 반영은 출시 전 합의·연동 테스트 대상이다. 이 문서는 모바일 코드가 이미 구현되었다는 의미가 아니다.
