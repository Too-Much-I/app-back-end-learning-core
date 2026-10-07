# 일일 모의고사 미제출 알림 구현 계획

작성일: 2026-10-07 · 상태: 서버 구현 및 로컬 검증 완료, 출시 비활성 · 연결 Jira: [TMI-198](https://to-teacher.atlassian.net/browse/TMI-198)

구현 시 구체화한 최신 계약은 [기기 API](../contracts/notification-device-api.md), 설정·기술 절충·출시 gate는 [runbook](DAILY_EXAM_REMINDER_RUNBOOK.md)을 우선한다. 아래 제안 모델/초안은 계획 이력을 보존한다. 실제 구현은 TMI-63 PUT 경로/body를 사용하고 설치 secret을 추가했으며, claim 전 PENDING 없이 Transaction에서 SENDING을 바로 기록한다. 순차 bounded sweep을 사용하고 운영 allowlist는 추가하지 않았다.

## 1. 5줄 결론

1. 매일 한국 시간(`Asia/Seoul`) 밤 9시에 하루 한 번 모의고사 학습 알림을 보낸다.
2. 오늘 시험을 시작하지 않은 사용자와 일부만 제출한 사용자를 포함하고, 오늘 전체 필수 문항을 최초 제출한 사용자는 제외한다.
3. 회원의 기기별 OS 알림 권한을 확인하고, 이용권 유무와 관계없이 등록된 모든 적격 기기에 보낸다.
4. Learning Core에 예약 작업·기기별 발송 이력을 추가하고 FCM을 사용한다. 모바일은 OS 권한·기기 등록·화면 이동을 구현한다.
5. TMI-198 서버 구현과 로컬 검증을 완료했다(단위600/Mongo134/migration117 통과). 모든 flag는 기본OFF이며 운영 FCM 설정·실제 발송·배포는 수행하지 않았다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 확정한 정책

| 항목 | 정책 |
| --- | --- |
| 발송 시각 | 매일 21:00, Asia/Seoul |
| 횟수 | 사용자당 일일 알림 한 건을 모든 적격 기기에 각각 한 번 시도; 22시 추가 발송 없음 |
| 대상 | 오늘 미시작 또는 제출 미완료 MEMBER; OS 권한 허용·유효 기기 등록 필요; 이용권 무관 |
| 제외 | 오늘 모의고사 한 회라도 모든 필수 문항 최초 제출 완료 |
| 완료 의미 | 채점 결과가 아니라 서버의 제출 접수 완료 |
| 재응시 | `retryCount>0` 제출은 최초 제출 완료 판단에 포함하지 않음 |
| 발송 직전 | 각 기기마다 제출 완료·최신 OS 권한 보고·기기 소유자·계정 차단 상태 재확인 |

### 사용자 확정 세부 정책

2026-10-07 사용자 선택을 반영했다. 알림 문구와 클릭 목적지도 사용자 승인으로 확정했으며 기술 구현 세부값은 구현 전 구체화한다.

- 하루의 기준은 KST 00:00 이상 다음 날 00:00 미만이다. 한 시험의 마지막 필수 최초 제출을 접수한 시각으로 날짜를 정한다. 전날 시작해 오늘 제출을 끝내면 오늘 완료다.
- 등록된 모든 적격 기기에 보낸다. 한 사용자의 여러 휴대폰에서 각각 울리는 것은 의도된 동작이며 동일 기기 반복 전송과 구분한다.
- 발송 창은 KST `[21:00, 21:15)`다. 재시작 시 아직 전송 시도하지 않은 대상만 이 창 안에서 처리하고 다음 날 과거 알림을 보내지 않는다.
- 발송 실패·접수 결과 불명 모두 당일 재발송하지 않는다. 기기별 전송 시도는 최대1회이며 SDK/HTTP client 자동 재시도도 비활성화·검증한다. 다음 날 새 일일 알림은 정상 처리한다.
- 최신 사용자 결정으로 OS 알림 권한만 사용하는 범위로 확정한다. 가입 시 별도 수신 동의 체크박스·앱 내 알림 토글·동의 API/저장은 이번 구현 범위에서 제외한다. OS 권한을 거부해도 가입/시험 이용은 가능하다. 실제 발송 전 야간 발송 요건 확인은 유지한다.
- 발송 이력은 30일 보관 후 자동 삭제한다. 시험 제출 기록과 기기 토큰의 보존기간은 이 30일 정책의 대상이 아니다.
- 확정 문구: 제목 `오늘의 모의고사`, 본문 `오늘 모의고사로 영어 연습을 이어가 볼까요?`. 클릭 시 모의고사 홈으로 이동하고 실제 이용 가능 여부는 기존 앱 흐름으로 확인한다.

### 범위와 호환성

신규 notification domain, 기기 등록/해제 API, 제출 완료 내부 기록, 예약 발송 및 테스트를 계획한다. 기존 시험 생성·submit·Polling·응답 DTO·BaseResponse·retryCount·S3/Redis key·AI `user_id=examId`와 Callback JSON은 유지한다. 별도 알림 서버, 전체 회원 동기화, 신규 메시지 큐 및 마케팅 캠페인 시스템은 MVP에 포함하지 않는다. 새 dependency/API와 기존 lifecycle 연계는 구현 단계의 명시적 범위로 검토한다.

## 3. 사용자가 결정해야 하는 사항

8개 제품 정책은 사용자 선택으로 확정했다. 아래 표를 이전 권장안보다 우선한다.

| 결정 | 확정 정책 | 상태 |
| --- | --- | --- |
| MEMBER/GUEST | 회원만 | 확정 |
| 시험 이용권 | 이용권 유무와 관계없이 발송 | 확정 |
| 다중 기기 | 등록된 모든 적격 기기 | 확정 |
| 완료 날짜 | 마지막 필수 최초 제출 접수일 | 확정 |
| 재발송 | 실패·불명 결과 모두 재발송 없음 | 확정 |
| 지연 허용 | 21시부터 15분간 최초 전송 허용 | 확정 |
| 발송 이력 보존 | 30일 | 확정 |
| 알림 허용 | 기기별 OS 권한만 확인; 별도 가입 동의 없음 | 최신 사용자 결정 |

알림 대상 선정에 Billing 이용권 조회를 추가하지 않는다. 이용권이 없는 사용자도 받으며 클릭 이후 기존 앱의 이용권/시험 시작 흐름을 따른다. 문구와 모의고사 홈 이동은 확정했다. 설치 소유권 증명, OS별 허용 상태 매핑, 기기 토큰 보존과 단기 억제 정보 정리 방식은 구현 전 기술 계약으로 구체화한다. OS 권한은 법적 동의의 포괄적 증거로 간주하지 않으며 실제 야간 발송 요건은 출시 전에 확인한다.

## 4. 주요 위험과 미확인 사항

- FCM 성공 응답은 기기 표시/열람 성공이 아니다. OS 설정, 네트워크, 앱 삭제로 실제 표시되지 않을 수 있다.
- FCM은 업무 멱등성 key를 보장하지 않는다. DB unique 제약만으로 네트워크 경계의 정확히 한 번 전달을 보장할 수 없다. SDK 자동 재시도까지 확인하고 불명 결과를 재발송하지 않는 정책을 적용한다.
- 마지막 확인 직후 사용자가 제출을 완료하거나 OS 권한을 끌 수 있고 이미 접수된 푸시는 회수할 수 없다. TTL과 발송 창을 제한해 오래된 푸시를 줄인다.
- OS 권한 변경은 앱이 서버에 다시 보고하기 전까지 알 수 없다. 기기별 마지막 관측값과 관측 시각을 저장한다. OS 권한 보고를 실시간 OS 조회나 별도 수신 동의의 증거로 취급하지 않는다.
- 기존 Job의 `pendingAt`이나 Session의 `completedAt`을 제출 완료 시각으로 단순 재사용하면 복구/채점 시각을 잘못 집계할 수 있다.
- 학습 기록 삭제 이후 완료 이력이 사라져 같은 날 재알림하는 문제를 피해야 한다. 삭제 요청 시 당일 알림만 억제하는 운영 기록을 만들고 학습 성적·시험 ID는 보존하지 않는 방안을 삭제 정책과 맞춘다.
- 탈퇴/병합 차단 기능이 운영에서 실제 연결돼 있어야 한다. lifecycle 연계가 준비되지 않은 환경에서 알림만 활성화하지 않는다.
- 기존 배포의 Mongo Transaction/owner guard 설정과 알림 writer의 조건 조합을 확인해야 한다. 특정 다른 기능 flag를 켜야만 알림이 동작하는 숨은 의존성을 만들지 않는다.

## 5. 현재 작업과 직접 관련된 설명

### 5.1 대상의 출발점과 앱 역할

시험 Session 목록을 모집단으로 사용하지 않는다. 인증된 MEMBER가 OS 권한을 허용하고 기기를 등록하면 후보 목록에 들어간다. 기기 목록을 cursor로 조회하고 사용자별 당일 제출 완료·계정 차단을 확인한다. 권한 거부/미확인·유효 토큰 없음·연결 해제 기기는 제외하며 이용권은 조회하지 않는다.

앱은 OS 알림 권한 요청, FCM 토큰 발급/갱신, 로그인 후 연결, 로그아웃/계정 변경 시 연결 해제, 앱 복귀 시 권한 상태 동기화, 푸시 클릭 시 인증 및 모의고사 홈 이동을 구현한다. iOS는 APNs 연결과 실제 기기 검증이 필요하다. 프론트 저장소는 이번 조사에서 열람하지 않았으므로 기존 SDK 탑재 여부는 미확인이다.

가입 후 또는 앱 진입 시 목적을 안내하고 OS 권한을 요청하는 흐름을 제안한다. 기존 회원도 같은 기기 등록 흐름을 사용하며 권한 거부가 가입/로그인을 막지 않는다. Identity signup API와 회원별 동의 원본 연계는 이번 범위에 추가하지 않는다. 앱 내에서 별도 학습 알림만 끄는 기능은 없으며 사용자는 OS 설정에서 해당 기기의 앱 알림을 끈다.

### 5.2 신규 API 초안

정확한 DTO·오류코드·설치 식별자 규격은 모바일 계약 문서에서 구현 전에 고정한다. 기존 공개 API는 수정하지 않는다.

Jira 등록 시 확인한 TMI-63(채점 완료 Push)은 `PUT /api/v1/notifications/devices`에 body `installationId`/`pushToken`을 명시해 아래 초안과 차이가 있다. 구현 전 기존 적용 여부 및 모바일 계약을 확인해 공통 기기 계약 하나로 정합화한다. TMI-63의 Guest/재시도/채점 완료 정책과 이번 MEMBER/무재발송/일일 정책을 분리하고 기존 계약을 임의 변경하지 않는다. TMI-58(푸시 알람 연결)은 설명이 없어 모바일 범위 확인이 필요하다. 두 기존 이슈는 수정하지 않았다.

| Method / URL 제안 | 용도 | 요청/응답 개요 |
| --- | --- | --- |
| PUT `/api/v1/notifications/devices/{installationId}` | 기기 등록/토큰 교체 | platform, fcmToken, permission 상태; 최소 성공 응답 |
| DELETE `/api/v1/notifications/devices/{installationId}` | 현재 사용자 기기 연결 해제 | 멱등 성공 |

userId는 JWT `sub`/CurrentUserProvider에서 얻고 클라이언트가 전달하지 않는다. Legacy 환경의 고정 UUID로 실제 푸시를 보내지 않는다. 인증·소유권·withdrawn/merged deny를 적용하고 토큰 원문을 응답에 돌려주지 않는다. 설치 ID 자체를 소유권 증거로 취급하지 않는다. 계정 전환 시 다른 사용자의 설치를 무조건 덮어쓰지 않도록 서버 발급 설치 credential 또는 동등한 검증 방식을 모바일과 확정한다. 토큰 길이/형식 제한과 등록 rate limit을 둔다.

### 5.3 Mongo 저장 모델 제안

| Collection / 내부 필드 | 목적 및 index |
| --- | --- |
| `notification_devices`: installationId, userId, platform, token, tokenHash, permission, permissionObservedAt, active, lastSeenAt, version | installation unique, tokenHash unique, active/permission/userId/installationId 조회 |
| `exam_submission_receipts`: examId, questionNumber, acceptedAt, expiresAt | 최초 실제 retry0 submit만 기록, examId/questionNumber unique; 전체 제출 완료 후72시간 absolute TTL, 미완료에는 expiresAt 없음 |
| ExamSession 내부 `submissionCompletedAt` | 전체 필수 retry0 접수 완료 Instant; 기존 공개 DTO에 노출하지 않음 |
| `daily_exam_reminder_deliveries`: userId, dateKst, type, installationId, status, lease/version, attemptedAt, expiresAt | userId/dateKst/type/installationId unique, status/leaseUntil, expiresAt TTL; 당일 종료+30일 후 만료 |
| `daily_reminder_suppressions`: userId, dateKst, fixed reason, expiresAt | 삭제/병합 등으로 당일 알림 억제; userId/dateKst unique 및 TTL |

FCM token은 전송에 필요하므로 hash만으로 대체할 수 없다. 제한된 저장소 권한과 암호화를 적용하고 원문을 로그·metric·문서에 넣지 않는다. 영구 무효 토큰은 비활성화하고 보존 기한 후 제거한다. 구체적인 저장 암호화/Secret 주입은 기존 환경에 맞춰 결정한다.

### 5.4 제출 완료 기록

1. 실제 사용자 submit이 접수되는 경계에서 최초 retry0 receipt를 `setOnInsert`한다. Job 복구, Callback, 재채점, 같은 submit replay로 acceptedAt을 바꾸지 않는다.
2. Session/mock exam에서 얻은 비어 있지 않은 필수 문항 집합을 기준으로 receipt를 확인한다. 마지막 번호 제출만으로 완료를 판정하지 않는다.
3. 모든 receipt가 있으면 `submissionCompletedAt=max(acceptedAt)`를 한 번 기록한다. 채점 Job 상태·점수·Summary는 필요하지 않다. 사용자 후속 승인으로 같은 Transaction에서 해당 시험 receipt에 완료 시각+72시간의 expiresAt을 설정한다. Session 완료 시각은 유지하고, 완료 후 replay로 receipt를 재생성하거나 만료를 연장하지 않는다.
4. 동시 마지막 문항 제출의 write-skew를 막도록 같은 Session/owner guard에 write conflict를 만들고 동일 Mongo Transaction에서 수렴한다. 기존 Transaction manager에 참여하고 외부 FCM/AI 호출을 그 안에 넣지 않는다.
5. guard 없는 기존 submit 경로도 누락 없이 지원한다. 알림 제출 추적이 ON이면 Transaction 지원을 검증하고 Job/receipt/Session 기록의 원자성을 확보한다. feature OFF 경로를 회귀 검증한다.
6. 도입 전 데이터의 정확한 제출 시각을 Job 복구/채점 시각에서 추정하지 않는다. 추적 writer를 먼저 배포하고 구 writer drain 후 KST 하루 전체의 기록이 확보된 다음 발송을 활성화한다. 기존 활성 시험에 대해서는 별도 inventory로 receipt 보완 근거를 검증하고, 시각을 확정할 수 없는 진행 시험의 사용자는 당일 보수적으로 제외한다.

Session 사용자와 완료 시각으로 해당 KST 날짜 범위를 조회한다. 새 시험을 추가로 시작했어도 오늘 완료한 시험이 하나 있으면 제외한다. 제출 사실은 이후 채점 실패로 취소하지 않는다.

### 5.5 예약 및 발송 흐름

1. 21:00에 실행하고, bounded sweep으로 21:15까지 미처리 후보를 회수한다. timezone을 명시하고 서버 기본 timezone에 의존하지 않는다.
2. OS 권한 허용 기기 목록을 stable cursor로 읽는다. batch100/동시 발송5를 초기 제안값으로 두고 FCM quota와 사용량으로 조정한다. 창 안의 sweep에서 새로 등록된 적격 기기도 아직 시도하지 않았다면 포함한다.
3. 사용자/날짜/유형/설치ID unique 발송 record를 생성하고 CAS lease로 한 worker만 claim한다. 사용자당 일일 알림은 하나이고 기기별 전송 결과는 독립 기록한다. 토큰 교체는 같은 설치의 새 발송 기회가 아니다.
4. 각 기기 전송 직전에 오늘 완료, 당일 억제, 최신 OS 권한 보고, MEMBER 및 소유자/탈퇴/병합 deny, 기기 활성/version, 발송 창을 다시 확인한다. 조회 오류는 발송 허용으로 해석하지 않는다. 이용권 검사는 없다.
5. 조건 충족 시 기기별 HTTP 전 `SENDING` durable marker를 저장한다. FCM은 Mongo Transaction 밖에서 호출한다. 동일 tokenHash를 중복 대상으로 전개하지 않는다.
6. 성공은 `ACCEPTED`, 실패는 `FAILED`, 제외는 `SKIPPED`로 기록한다. 영구 무효 토큰은 해당 전송의 token/version과 현재 값이 같을 때만 비활성화한다. 다른 기기들의 최초 전송은 별도로 수행하며 부분 성공 전체를 다시 보내지 않는다.
7. timeout/연결 단절/접수 후 DB 저장 실패는 `UNKNOWN`으로 보존한다. lease 만료 SENDING은 다시 보내지 않고 UNKNOWN으로 수렴한다. 외부 전송 전 crash도 일부 누락될 수 있다는 정책을 명시한다.
8. 일시/영구 실패·불명 결과 모두 재전송하지 않는다. SDK/HTTP client의 내부 재시도도 금지한다. quota 응답의 Retry-After는 아직 시도하지 않은 다른 기기의 전송 속도에 반영하며 창 밖이면 만료 처리한다. auth 오류는 발송을 중단하고 운영 경보를 보낸다. lease 회수는 전송 전 준비 단계만 허용하고 SENDING 이후에는 재호출하지 않는다.

payload는 고정 문구, notificationId, 알림 유형과 모의고사 홈 route만 포함한다. userId/점수/시험 결과/음성을 넣지 않는다. TTL은 남은 발송 창 이하로 설정하며 Android/iOS 표시·collapse 정책을 실제 기기로 확인한다. collapse는 업무 중복 방지 수단으로 간주하지 않는다.

### 5.6 계정과 데이터 lifecycle

- 로그아웃/계정 전환: 설치 연결 해제와 version 증가, 기존 claim은 발송 직전 version 불일치로 취소한다. 이미 FCM에 접수된 메시지는 취소 불가다.
- 탈퇴: 즉시 발송 deny, 기기 토큰 제거/비활성화 및 이력의 승인된 삭제 정책 적용. 철회 후 오래된 worker snapshot으로 보내지 않는다.
- OS 권한 해제: 서버가 보고받으면 해당 기기의 발송을 제외한다. 다른 허용 기기는 계속 대상이며 다시 권한을 켜도 당일 이미 시도한 기기는 재발송하지 않는다. 이미 접수된 푸시는 회수할 수 없다.
- UserMerged: source 전송 차단, 기기는 target 재로그인 후 OS 권한을 보고하고 재등록한다. 별도 회원 수신 동의는 저장/승계하지 않는다. 당일 source 완료/발송 사실이 있으면 최소 억제 정보로 target의 추가 알림을 막는다. 기존 완료 Session owner 이전과 일관되게 처리한다.
- 학습 기록 삭제: receipt와 Session 완료 metadata는 학습 기록 삭제 대상에 포함한다. 요청 당일은 별도 단기 억제 기록으로 재알림을 막고 다음 날 정상 정책을 적용한다. 삭제 중 신규 학습이 막힌 사용자는 알림 제외한다. 기존 삭제 target inventory와 테스트도 확장한다.
- delivery와 suppression은 학습 history source로 사용하지 않는다. 발송 이력은 해당 KST 일자 종료+30일에 만료하도록 모든 상태에 expiresAt을 설정한다. SENDING은 bounded 정리로 UNKNOWN, 창 종료의 미전송 건은 EXPIRED로 수렴한다. 당일 억제 정보는 다음 날 이후 정리하는 안을 유지한다. TTL 지연에 의존하지 않고 날짜 조건으로 대상 여부를 판단한다.

### 5.7 구현 순서

1. 확정 정책 기반 모바일 계약, OS별 허용 상태 및 설치 소유권 증명 구체화.
2. 기기 모델/API, OS 권한 보고, MEMBER 인증·설치 연결·무효 토큰 처리, migration dry-run/apply 추가.
3. submit receipt와 완료 시각 기록, 기존 guarded/unguarded 및 멱등 replay 경로 연결.
4. lifecycle 연계와 일별 후보 조회·발송 직전 판정 구현.
5. unique 발송 이력·lease·SENDING/UNKNOWN 복구 및 fake FCM adapter 구현.
6. FCM 실제 adapter, 예약 작업, TTL·quota·운영 metric·runbook 추가.
7. 격리 테스트와 실제 모바일 테스트 후 writer 선배포 → dry-run → 테스트 allowlist 발송 → 단계적 일반 활성화.

기능 설정은 제출 추적, API, dry-run, 실제 발송을 분리하고 실제 발송은 기본 OFF로 둔다. 발송 ON 시 JWT·Transaction·index·추적 준비·lifecycle·FCM 설정을 startup validator에서 검증한다. 알림 OFF로 롤백해도 기존 시험 제출은 유지한다.

### 5.8 검증 및 완료 기준

- 미시작/부분 제출은 포함, 전체 제출/채점 대기/채점 실패는 제외, retry>0·replay·Job 복구는 날짜를 바꾸지 않음.
- KST 자정, 전날 시작/오늘 완료, 오늘 완료 후 다른 시험 시작, 빈 필수 문항·잘못된 증거 fail-closed 검증.
- concurrent 마지막 submit, Job/receipt rollback, unknown commit, 다중 worker claim, 발송 전/후 crash와 SENDING lease 회수 검증.
- OS 권한 철회·로그아웃·토큰 교체·다중 기기·탈퇴·병합·기록 삭제와 발송 경합 검증. MEMBER만 대상이며 이용권 유무가 선정에 영향을 주지 않음을 검증한다.
- OS 권한 거부/미확인 시 해당 기기 발송0, 가입/로그인 정상 유지, 기기별 권한 차이, 권한 재허용 시 당일 중복 방지와 기존 회원 기기 등록을 검증한다.
- FCM accepted/invalid token/auth/quota/timeout, 모든 결과에서 기기별 최대1회 시도, UNKNOWN 재전송0 및 창 밖 발송0 검증. 기기 A 성공/B 실패/C 불명일 때 모두 재전송0, 새 미시도 기기만 최초 전송 가능, 토큰 교체로 당일 추가 발송되지 않음 및 전송 시도 전후 crash 검증.
- Android/iOS 실제 허용·거부·앱 종료·백그라운드·클릭 인증·TTL 검증. 접수 성공과 기기 표시 결과를 따로 기록.
- `./gradlew clean test`, `./gradlew mongoIntegrationTest`, 신규 migration Node tests, `git diff --check` 수행. 일반 자동 테스트는 fake FCM과 격리 Mongo를 사용하고 실제 사용자에게 전송하지 않는다.
- metric에는 상태/고정 사유만 사용한다. 후보/제외/접수/불명/실패 수, 큐 지연, 인증 오류를 관측하고 token/PII 비노출 테스트를 추가한다.

출시 전 Firebase 프로젝트·APNs·앱 환경 일치, credential 안전 주입, 최종 안내 문구·야간 발송 요건, 기존 writer drain/완료 추적, index, 사용자 lifecycle, 실제 기기 표시와 kill switch를 확인한다. OS 권한만 사용하는 제품 결정을 별도 광고성/야간 동의 충족의 증거로 취급하지 않는다. 운영 설정과 배포는 계획 승인만으로 수행하지 않는다.

## 6. 부록: 현재 코드 근거와 조사 한계

| 파일 | 확인한 사실 / 연결 지점 |
| --- | --- |
| `src/main/java/web/tosunsaeng/domain/exams/attemptgroup/application/AttemptGroupEvidenceEvaluator.java` | 필수 retry0 Job/결과로 gradingReady를 만들고 실제 결과 완료와 구분한다. 제출 시각 원장 자체는 아니다. |
| `src/main/java/web/tosunsaeng/domain/exams/domain/entity/ExamSession.java` | userId, createdAt, completedAt, gradingStartedAt과 version이 있다. 알림용 submissionCompletedAt은 확인되지 않는다. |
| `src/main/java/web/tosunsaeng/domain/exams/domain/entity/QuestionGradingJob.java` | pendingAt, recoveryCycle, activityUserSubmission이 있다. 실제 제출과 legacy 복구 구분 필드는 활용 가능하나 기존 timestamp를 무조건 최초 제출 시각으로 쓰지 않는다. |
| `src/main/java/web/tosunsaeng/domain/exams/application/ExamGradingService.java` | submitQuestion과 guard 없는 경로, Job 생성·replay·복구가 존재하므로 모든 제출 경로 연계가 필요하다. |
| `src/main/java/web/tosunsaeng/domain/usermerge/application/UserMergedTransactionService.java` | 기존 owner 이전 기능과 새 알림 저장 모델의 연계 검토 대상. 이번에는 상세 수정 설계를 실행하지 않았다. |
| `src/main/java/web/tosunsaeng/domain/withdrawal/application/UserWithdrawnEventTransactionService.java` | 탈퇴 전송 차단/토큰 정리 연계 검토 대상. |
| `src/main/java/web/tosunsaeng/domain/learningrecorddeletion/application/DeletionWorker.java` | sealed target별 자식 collection 정리가 있어 신규 receipt를 삭제 inventory에 포함해야 한다. |

현재 src/main 및 build.gradle 검색에서 FCM client·기기 토큰·알림 설정 구현은 확인되지 않았다. Identity/Billing/모바일 저장소 및 운영 Firebase 설정은 이번에 조사하지 않았다. 기재한 collection, 신규 API, 제한 수치와 세부 정책은 구현 제안이며 이미 배포된 계약으로 취급하지 않는다. 기존 작업의 dirty 파일은 수정하지 않는다.
