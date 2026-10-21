# Mongo failure diagnostics

## 결론

- TMI-118/TMI-125 관련 운영 guard 실패의 관측만 보강한다.
- CloudWatch `event=mongo.failure`로 필터링하고 기존 requestId로 HTTP 실패와 연결한다.
- `boundary=USER_OWNED|NOTIFICATION`의 attempt는 1부터 시작하는 실제 실행 횟수다.
- `boundary=HTTP attempt=0`은 HTTP 경계가 횟수를 알 수 없다는 뜻이며 재시도 0회라는 뜻이 아니다.
- 재시도·예외 전달·API·Sentry 개인정보 정제 동작을 변경하지 않는다.

## 해석과 안전 경계

`mongoCode`는 cause chain에서 처음 발견한 MongoException 정수 코드다.
`transientTransaction`과 `unknownCommit`은 최대20단계 cause chain의 알려진 두 label 유무만 기록한다.
`outcome=RETRYING|EXHAUSTED|NON_RETRYABLE|UNKNOWN_COMMIT`으로 재시도 결정 경계를 구별한다.
HTTP는 `PROPAGATED`로 남긴다. 같은 실패의 retry 경계와 HTTP 로그가 함께 발생할 수 있으므로 로그 줄 수를 사용자 실패 수로 세지 않는다.

원문 exception/command/document/임의 label, 사용자·시험 식별자 및 credential은 신규 로그에 넣지 않는다.
기존 requestId MDC만 상속한다. 스택이나 throwable 객체를 logger에 전달하지 않는다.
과거 누락된 Mongo 코드와 재시도 내역은 소급 복원할 수 없다.

## 검증 및 배포

- wrapped Mongo 오류, unknown commit, 비Mongo 오류, 순환 cause, 민감 원문 비노출 테스트.
- 기존 owner/notification 재시도 회귀 및 전체 unit/격리 Mongo CI로 동작 보존 확인.
- 운영에서는 실제 사용자 오류를 인위적으로 유발하지 않는다. 다음 자연 발생 오류의 metadata로 원인을 확인한다.
- 배포 직전 운영은 prod13이며 롤백은 이전 task definition/image를 보존해 수행한다.
