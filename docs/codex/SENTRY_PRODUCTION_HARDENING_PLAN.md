# Sentry 운영 보완 계획서

- 최초 작성일: 2026-08-10
- 최종 보정일: 2026-08-11
- Jira 이슈: 없음
- 상태: P0 애플리케이션 구현·자동 검증 완료, staging 운영 검증 대기
- 대상 저장소: `Too-Much-I/app-back-end-learning-core`

## 1. 목적

현재 Sentry 설정의 안전한 기본값은 유지하면서 다음 운영 공백을 보완한다.

1. 예상하지 못한 5xx의 스택트레이스를 보존한다.
2. 예외 메시지와 HTTP payload 등 민감할 수 있는 값은 Sentry로 전송하지 않는다.
3. 이벤트를 배포 환경과 배포 버전에 정확히 연결한다.
4. Logback ERROR 자동 전송을 끄고 같은 장애가 명시적 capture와 자동 resolver로 중복 수집되지 않도록 한다.
5. 실제 외부 Sentry를 호출하지 않는 자동 테스트와 staging 검증 절차를 마련한다.

초기 운영의 역할은 다음과 같이 고정한다.

- Sentry: 개발자가 조사해야 하는 예외 목록
- CloudWatch: 정상 흐름과 grading·AI dispatch·Callback을 포함한 구조화 운영 로그

## 2. 구현 전 상태와 SDK 근거

현재 저장소에는 다음 구성이 이미 존재한다.

- `sentry-spring-boot-starter-jakarta:7.14.0`
- `sentry.dsn`의 `SENTRY_DSN` 환경변수 참조
- `send-default-pii=false`
- `logging.minimum-event-level=error`
- `traces-sample-rate=0.0` 기본값
- active Spring profile 기반 environment
- 전역 예외 처리에서 예상하지 못한 5xx를 고정 메시지와 예외 타입 tag로 명시적 수집

현재 장점은 실제 사용자 ID와 요청 payload를 의도적으로 수집하지 않고 4xx WARN을 Sentry 이벤트에서 제외한다는 점이다. 가장 큰 한계는 `captureMessage`를 사용하므로 예외 타입 tag는 남지만 원본 스택 프레임이 없다는 점이다.

추가로 SDK 7.14.0 소스 확인 결과 `sentry.logging.enabled`의 기본값은 true이며, 현재 `minimum-event-level=error`는 Sentry LogbackAppender가 ERROR 로그를 별도 event로 전송하게 한다. 명시적 `captureException`과 함께 사용하면 중복 Issue나 grading 운영 로그의 과도한 Issue 승격이 생길 수 있으므로 초기 운영에서는 이 자동 전송을 비활성화한다.

현재 SDK 소스 기준으로 다음 확장 지점을 사용할 수 있다.

- Spring Bean 형태의 `SentryOptions.BeforeSendCallback`
- `SentryEvent.getExceptions()`의 예외 `value`와 `stacktrace` 독립 수정
- Spring Bean으로 제공되는 `IHub`와 호출별 local scope를 만드는 `captureException(Throwable, ScopeCallback)` overload
- 기본 order 1의 `SentryExceptionResolver`
- `sentry.logging.enabled=false`에 의한 Logback SentryAppender auto-configuration 비활성화

따라서 예외 메시지만 제거하고 타입·module·mechanism·stacktrace를 보존하는 방식이 기술적으로 가능하다.

## 3. 변경 원칙

- 실제 DSN은 코드, 테스트, 문서와 Git 이력에 저장하지 않는다.
- 실제 userId, Authorization/JWT, Secret, URL 서명값, S3 Key, 음성·Transcript, Callback·채점·tableContext payload를 Sentry에 보내지 않는다.
- 공개 API URL·Method·Request/Response DTO·`BaseResponse`를 변경하지 않는다.
- AI/Callback `user_id=examId`, `retryCount`, Redis Key/TTL과 S3 Object Key 계약을 변경하지 않는다.
- 4xx와 정상 Polling은 Sentry 이벤트로 승격하지 않는다.
- 예상하지 못한 5xx는 Sentry exception 1건과 CloudWatch ERROR 로그 1건을 남긴다.
- grading dispatch·Callback 운영 ERROR는 초기에는 CloudWatch 전용으로 유지한다.
- Logback ERROR를 Sentry Issue로 자동 승격하지 않고, 추후 승인된 오류만 reporter로 명시적 수집한다.
- tracing은 오류 수집이 안정화될 때까지 기본 0을 유지한다.
- 운영용 오류 발생 API를 새로 만들지 않는다.
- 테스트는 실제 Sentry 네트워크를 호출하지 않는다.

## 4. 목표 구조

```text
예상하지 못한 5xx
        │
        ▼
GlobalExceptionAdvice
        ├── report(exception, safe metadata)
        │           │
        │           ▼
        │  UnexpectedExceptionReporter ──► IHub.captureException
        │                                         │
        │                                         ▼
        │                            BeforeSendCallback sanitizer
        │                                         │
        │                          메시지·요청값 제거 / stack 유지
        │                                         │
        │                                         ▼
        │                                 Sentry transport
        │
        └── 안전한 구조화 ERROR 1건 ─────────────► CloudWatch
```

전역 예외 처리기는 Sentry SDK의 static API를 직접 호출하지 않고 reporter 추상화에 의존한다. reporter는 SDK 호출만 담당하고, 모든 이벤트의 최종 정보 제거는 `BeforeSendCallback`에서 방어적으로 수행한다. CloudWatch ERROR에는 exception 객체를 logger 인자로 넘기지 않고 고정 분류와 예외 타입만 남겨 예외 메시지 노출을 막는다.

## 5. 단계별 구현 계획

| 단계 | 우선순위 | 작업 | 완료 기준 |
|---|---:|---|---|
| 1 | P0 | 환경·배포 버전 명시 및 Logback 자동 event 전송 비활성화 | staging/prod에 정확한 environment/release가 표시되고 일반 ERROR 로그의 Sentry event가 0건임 |
| 2 | P0 | 전역 Sentry event sanitizer 추가 | 예외 메시지·사용자·요청값 제거, stack frame 보존 |
| 3 | P0 | 예외 reporter 추상화 및 5xx capture 변경 | 예상하지 못한 5xx가 stack과 함께 정확히 1건 수집 |
| 4 | P0 | 자동·통합 테스트 보강 | 외부 네트워크 없이 PII 부재·중복 방지 검증 |
| 5 | P0 | staging smoke 검증 | 환경·release·stack·event count·PII 부재 확인 |
| 6 | P1 | SDK 업데이트 검토 | 별도 변경으로 호환 버전 선택 후 전체 회귀 통과 |
| 7 | P1 | Sentry Alert Rule 구성 | prod 신규 issue·회귀·오류 급증 알림 연결 |

### 5.1 설정 명시

`application.yml`은 다음 정책으로 정리한다.

```yaml
sentry:
  dsn: ${SENTRY_DSN:}
  environment: ${SENTRY_ENVIRONMENT:${spring.profiles.active:local}}
  release: ${SENTRY_RELEASE:}
  send-default-pii: false
  max-request-body-size: none
  exception-resolver-order: 1
  logging:
    enabled: false
  traces-sample-rate: ${SENTRY_TRACES_SAMPLE_RATE:0.0}
```

- local/test는 빈 DSN 또는 비운영 transport를 사용한다.
- staging/prod는 배포 환경에서 `SENTRY_DSN`, `SENTRY_ENVIRONMENT`, `SENTRY_RELEASE`를 주입한다.
- release 형식은 `app-back-end-learning-core@<git-sha>`로 고정한다.
- CI source context와 ECS runtime에 동일한 `SENTRY_RELEASE` 값을 주입한다. 실제 배포 workflow 변경은 별도 명시 요청 범위에서만 수행한다.
- staging/prod에서 environment 또는 release가 비어 있으면 배포 파이프라인 검증 단계에서 실패시키는 방식을 우선한다. 애플리케이션의 기존 startup 계약 변경은 별도 합의 없이 추가하지 않는다.
- `exception-resolver-order=1`을 명시해 현재의 handled exception 제외 의도를 문서화한다. 정확한 동작은 통합 테스트로 고정한다.
- `logging.enabled=false`는 Sentry LogbackAppender만 비활성화하며 기존 CloudWatch/콘솔 로깅은 유지한다. `minimum-event-level`은 함께 두지 않는다.

### 5.2 전역 event sanitizer

`SentryOptions.BeforeSendCallback` Bean을 추가한다. 초기 정책은 가독성보다 정보 최소화를 우선한다.

sanitizer는 반드시 fail-closed로 동작한다.

```java
@Override
public SentryEvent execute(SentryEvent event, Hint hint) {
    try {
        return sanitize(event);
    } catch (RuntimeException sanitizerFailure) {
        return null;
    }
}
```

- 정제 성공: 정제된 event 전송
- 정제 실패: event drop
- 금지: 정제 실패 시 원본 event 반환

sanitizer 실패 관측이 필요하면 원본 event나 예외 메시지를 포함하지 않는 별도 안전 카운터 또는 CloudWatch 분류 로그만 사용한다. Logback SentryAppender를 다시 활성화해서는 안 된다.

`BeforeSendCallback`은 오류 `SentryEvent`의 최종 방어선이지 attachment나 tracing transaction의 sanitizer가 아니다. 초기 reporter와 scope에는 attachment를 추가하지 않고, recording transport에서 attachment envelope item이 0건인지 확인한다. tracing은 0을 유지하며 추후 활성화할 때 `BeforeSendTransactionCallback`과 transaction span·description·context의 별도 정보 제거 정책을 먼저 설계한다. callback의 `Hint`에 들어 있는 servlet request/response도 로그로 출력하지 않는다.

예외 이벤트:

- 각 `SentryException.value`를 null 또는 고정 안전 문구로 교체한다.
- 예외 `type`, `module`, `threadId`와 진단에 필요한 stack frame의 class/module·method·file·line 정보는 유지한다.
- stack frame의 로컬 변수 `vars`, 절대 경로, source context, register, lock 정보와 SDK `unknown` 확장 필드는 제거한다.
- mechanism은 `type`, `handled`, `synthetic` 같은 고정 속성만 유지하고 자유 형식 description·helpLink·meta·data·unknown은 제거한다.
- event의 자유 형식 message 객체 전체와 custom fingerprint는 제거하거나 고정 오류 분류로 교체한다.
- 별도의 thread collection은 초기에는 제거한다. 예외 stacktrace만으로 필요한 발생 위치를 보존한다.

HTTP 정보:

- framework integration이 추가한 request 객체는 body/data, URL, query string, cookies와 headers를 포함할 수 있으므로 초기에는 객체 전체를 제거한다.
- raw URL이나 path parameter가 섞일 수 있는 transaction도 초기에는 제거한다. HTTP method/status가 필요하면 reporter가 검증된 고정값을 allowlist tag로만 추가한다.
- 사용자 객체는 항상 제거한다. `send-default-pii=false`와 별개로 sanitizer에서 한 번 더 보장한다.

부가 정보:

- breadcrumb, 임의 extra/context와 event·exception·stacktrace·frame·thread·mechanism의 `unknown` map은 첫 적용에서 제거하고 명시적 allowlist만 다시 구성한다.
- 허용 tag는 `error.type`, `error.root_cause_type`, `http.method`, `http.status_code`, `capture.source`, `service` 등 고정 분류에 한정한다.
- 실제 userId, examId, jobId, URL, Object Key와 payload는 tag나 extra로 추가하지 않는다.
- 내부 requestId는 Sentry tag가 아닌 `correlation.request_id` 단일 context로 전달해 CloudWatch 검색 연결에 사용한다. 값은 현재 요청 필터가 생성한 UUID 형식만 허용하고 형식 검증에 실패하면 context 자체를 제거한다.

sanitizer는 `event.message`, 모든 `SentryException.value`, stack frame의 동적 값, mechanism map, breadcrumb, tag, extra/context, request, user와 SDK unknown field를 각각 검사한다. 단일 필드만 지우고 안전하다고 간주하지 않는다. Logback SentryAppender는 비활성화하므로 grading·Callback의 ERROR 로그는 sanitizer를 거치는 Sentry event가 되지 않고 CloudWatch에만 남는다.

### 5.3 예외 reporter와 capture 변경

다음 역할을 분리한다.

- `UnexpectedExceptionReporter`: 전역 예외 처리기가 의존하는 내부 추상화
- `SentryUnexpectedExceptionReporter`: `IHub`를 사용해 실제 Sentry event를 생성하는 구현
- `SentryEventSanitizer`: 전송 직전 모든 event를 정리하는 최종 방어선

처리 규칙:

1. JSON 파싱 오류와 `GeneralException` 계열 4xx는 reporter를 호출하지 않는다.
2. 예상하지 못한 5xx만 reporter를 호출한다.
3. reporter는 `error.type`, root cause type, HTTP 500, `capture.source`, `service=learning-core` 같은 저카디널리티 분류만 tag로 설정한다.
4. metadata는 `captureException(Throwable, ScopeCallback)`의 호출별 local scope에만 설정한다. 현재 request scope나 global scope를 직접 변경해 다음 요청의 event로 값이 누출되지 않게 한다.
5. `captureMessage`를 `captureException` 기반으로 교체한다.
6. sanitizer가 활성화되고 테스트로 검증되기 전에는 `captureException` 전환을 먼저 배포하지 않는다.
7. 예상하지 못한 5xx의 기존 WARN 로그는 안전한 구조화 ERROR 1건으로 변경한다. logger에 throwable을 전달하지 않아 CloudWatch에 예외 메시지와 cause message가 출력되지 않게 한다.
8. 기존 4xx WARN/INFO와 grading·Callback ERROR 구조화 로그는 CloudWatch에서 유지한다.
9. grading 등 특정 운영 실패를 Sentry Issue로 올릴 필요가 생기면 자동 로그 승격 대신 `capture.source`를 지정한 reporter 호출을 별도 승인 후 추가한다.
10. reporter 또는 SDK 호출이 실패해도 기존 500 응답 생성 흐름을 바꾸지 않도록 Sentry 경계를 비즈니스 흐름과 격리하고, 이 동작을 실패 주입 테스트로 고정한다.

### 5.4 중복 수집 방지

Sentry starter의 기본 `SentryExceptionResolver`는 현재 request scope를 그대로 사용한다. `BeforeSendCallback`은 event 본문을 정제하지만 scope attachment·session과 envelope trace baggage까지 제거할 수 없으므로, 구현에서는 기본 resolver를 `SanitizedSentryExceptionResolver`로 교체해 unhandled 예외도 호출별 격리 reporter를 통하도록 했다. resolver는 보고 후 null을 반환해 기존 Spring 예외 처리 흐름을 유지한다. Logback SentryAppender는 `sentry.logging.enabled=false`로 비활성화한다.

현재 Spring MVC 6.2.2 소스에서 기본 `HandlerExceptionResolverComposite`의 order는 0이고, Sentry resolver는 설정값 1이다. 따라서 ControllerAdvice가 처리해 응답을 반환한 예외는 뒤쪽 Sentry resolver까지 가지 않고, 앞선 resolver가 처리하지 못한 예외만 자동 경로에 도달한다. 이 순서는 framework·SDK 업데이트로 바뀔 수 있으므로 설정값만 신뢰하지 않고 다음 transport 건수 테스트로 고정한다.

- ControllerAdvice가 처리한 예상하지 못한 5xx: reporter 경로 Sentry 1건, CloudWatch ERROR 1건, Logback Sentry event 0건
- Advice가 처리한 4xx: Sentry 0건, 기존 CloudWatch WARN/INFO
- Advice 밖으로 최종 빠져나간 MVC·하위 필터의 `ServletException`/`RuntimeException`: 격리 reporter Sentry 1건, 안전한 CloudWatch ERROR 1건
- 기존 grading dispatch·Callback ERROR 구조화 로그: CloudWatch 유지, Sentry 0건
- 동일 throwable을 reporter와 자동 resolver가 동시에 보내지 않는지 확인

event의 mechanism/capture source와 최종 recording transport 건수를 기준으로 중복을 검증한다. 단순 시간 기반 dedupe에는 의존하지 않는다.

### 5.5 SDK 업데이트

안전한 capture 동작과 SDK 업그레이드를 한 번에 섞지 않는다.

1. 우선 현재 7.14.0에서 sanitizer와 reporter 동작을 테스트로 고정한다.
2. 별도 이슈에서 현재 8.x 계열의 호환 release와 Spring Boot 3.4.2·Java 21 지원 범위를 확인한다.
3. 7.14.0에서 P0 작업을 먼저 완료하고 8.x 업데이트는 별도 변경으로 진행한다.
4. property 이름, auto configuration, exception resolver order와 BeforeSend Bean 연결을 다시 검증한다.
5. 집중 테스트와 전체 `./gradlew clean test`를 실행한다.

### 5.6 최종 수집 정책

| 상황 | Sentry | CloudWatch |
|---|---|---|
| 2xx 정상 처리 | 0건 | 정상 구조화 로그 |
| 400/401/403/404/409 | 0건 | 기존 WARN/INFO |
| ControllerAdvice 예상 밖 5xx | 명시적 exception 1건 | 안전한 ERROR 1건 |
| Servlet/filter까지 빠져나간 unhandled | 격리 reporter 1건 | 안전한 ERROR |
| AI dispatch·Callback 운영 실패 | 초기 0건 | 기존 ERROR |
| 추후 승인된 특정 운영 실패 | 명시적 reporter 1건 | 기존 ERROR |

Sentry에는 저카디널리티 분류와 안전한 requestId context만 두고, examId·jobId·questionNumber·retryCount를 포함한 처리 흐름은 CloudWatch에서 조회한다.

## 6. 예상 변경 파일

구현 시 실제 패키지 구조를 다시 확인하되 현재 예상 범위는 다음과 같다.

신규 후보:

- `src/main/java/web/tosunsaeng/global/sentry/UnexpectedExceptionReporter.java`
- `src/main/java/web/tosunsaeng/global/sentry/SentryUnexpectedExceptionReporter.java`
- `src/main/java/web/tosunsaeng/global/sentry/SentryEventSanitizer.java`
- `src/main/java/web/tosunsaeng/global/sentry/SanitizedSentryExceptionResolver.java`
- `src/main/java/web/tosunsaeng/global/sentry/UnhandledExceptionCaptureFilter.java`
- 위 컴포넌트의 단위 테스트

수정 후보:

- `src/main/java/web/tosunsaeng/global/exception/GlobalExceptionAdvice.java`
- `src/main/resources/application.yml`
- `src/test/resources/application-test.yml`
- `src/test/java/web/tosunsaeng/global/exception/GlobalExceptionAdviceLoggingTest.java`
- Sentry event pipeline용 통합 테스트
- 작업 기록 문서

## 7. 테스트 계획

### 7.1 sanitizer 단위 테스트

민감한 형태의 값을 가진 가짜 event를 메모리에서 생성하고 다음을 검증한다.

- 서로 다른 위치에 password, refresh token, Mongo URI와 S3 signature를 뜻하는 `SENSITIVE_MARKER_A`부터 `SENSITIVE_MARKER_D`까지의 가짜 marker를 넣는다. 실제 형식이나 실제 자격정보는 사용하지 않는다.
- exception type과 stack frame의 class/method/file/line 정보는 남는다.
- exception value와 event 자유 형식 message는 제거 또는 고정값으로 교체된다.
- request·transaction, user, breadcrumb payload와 임의 tag·extra·context가 남지 않는다.
- stack frame vars·절대 경로·source context·register·lock, mechanism의 자유 형식 map과 모든 SDK unknown field가 남지 않는다.
- release, environment와 허용 tag는 유지된다.
- UUID 형식의 `correlation.request_id` 외의 고카디널리티 처리 식별자는 남지 않으며 잘못된 requestId도 제거된다.
- sanitizer가 반환한 최종 event를 SDK serializer로 직렬화한 전체 JSON 어디에도 marker가 없어야 한다.
- sanitizer 처리 중 예외가 발생하면 반환값은 null이고 recording transport에는 0건이어야 한다. 원본 event를 보내는 fail-open은 금지한다.

### 7.2 reporter·전역 예외 테스트

- 예상하지 못한 5xx가 reporter를 정확히 1회 호출한다.
- JSON 파싱 오류, validation 오류, 인증 401/403과 비즈니스 4xx는 reporter를 호출하지 않는다.
- 응답 상태와 기존 `BaseResponse` 구조는 유지된다.
- 로그에 예외 메시지·사용자 ID·URL·payload가 추가되지 않는다.
- 예상하지 못한 5xx의 안전한 CloudWatch ERROR 이벤트명이 정확히 1회 출력된다.
- CloudWatch ERROR logger에 throwable을 넘기지 않아 예외·cause message가 출력되지 않는다.
- reporter가 RuntimeException을 발생시키는 실패 주입에서도 기존 HTTP status와 `BaseResponse`가 유지되고 민감한 예외 원문이 추가로 기록되지 않는다.
- 서로 다른 requestId와 분류를 가진 두 event를 연속 capture해 첫 호출의 local scope metadata가 두 번째 event에 남지 않는지 검증한다.

### 7.3 인메모리 transport 통합 테스트

실제 네트워크 대신 recording transport를 주입해 최종 Sentry event를 검사한다.

- 5xx 이벤트 수가 정확히 1이다.
- stack frame에 애플리케이션 패키지가 포함된다.
- 최종 직렬화 event의 모든 필드에 exception message와 가짜 marker가 없다.
- event·exception·stacktrace·frame·mechanism의 unknown map과 stack local 값이 최종 JSON에 없다.
- 최종 envelope에 attachment item과 transaction item이 없다.
- environment/release가 예상값이다.
- 4xx 이벤트 수는 0이다.
- `GlobalExceptionAdvice`가 처리한 5xx에서 reporter 1회, 자동 resolver 추가 0회, Logback Sentry event 0회, 최종 transport event 1회, CloudWatch ERROR 1회를 한 통합 테스트에서 검증한다.
- grading ERROR는 CloudWatch에 남지만 final Sentry transport event는 0건이다.
- servlet/filter까지 빠져나간 unhandled 예외는 resolver·filter 중복 없이 격리 reporter event 1건이다.

### 7.4 회귀 테스트

- 기존 공개 API request/response JSON 비교
- AI/Callback, retryCount, Redis/S3 계약 회귀
- 전체 `./gradlew clean test`
- `git diff --check`

## 8. staging 검증 및 배포

1. staging 배포 환경에 DSN·environment·release를 등록한다.
2. `SENTRY_RELEASE=app-back-end-learning-core@<git-sha>`를 CI source context와 ECS runtime에 동일하게 주입했는지 확인한다.
3. public 오류 유발 API를 추가하지 않고 승인된 일회성 smoke 방법으로 안전한 synthetic 5xx를 1회 발생시킨다.
4. Sentry에서 다음을 확인한다.
   - environment가 staging인지
   - release가 실제 배포 식별자와 일치하는지
   - event가 정확히 1건인지
   - 애플리케이션 stack frame이 존재하는지
   - 사용자·요청 본문·query·cookie·인증 정보·금지 payload가 없는지
   - requestId context로 동일 CloudWatch 흐름을 찾을 수 있는지
5. 동일 조건의 4xx와 grading ERROR가 Sentry 이벤트를 만들지 않는지 확인한다.
6. Sentry 프로젝트의 IP 주소 저장 방지 설정이 활성화되어 있는지 확인한다.
7. 검증 후 prod에 같은 설정을 적용하되 DSN·environment·release 값은 prod 환경에서 별도로 주입한다.
8. tracing은 0으로 유지하고 오류 수집 안정화 이후 별도 계획으로 활성화한다.

## 9. Sentry 프로젝트 운영 설정

저장소 밖 Sentry 프로젝트에서 다음 Alert Rule을 구성한다.

- prod의 신규 issue
- 해결된 issue의 regression
- 일정 시간 내 오류 이벤트 급증
- 핵심 5xx error type 반복

Sentry 프로젝트의 **Prevent Storing of IP Addresses** 설정을 활성화한다. 애플리케이션의 `send-default-pii=false`와 sanitizer를 유지하면서 서버 측 저장 정책을 추가 방어선으로 사용한다.

local/test/staging은 prod 호출 알림에서 제외한다. 알림 채널, 임계값과 담당자는 배포 전 운영자가 확정한다.

## 10. 롤백 계획

- 긴급 중단은 배포 환경에서 `SENTRY_DSN` 주입을 제거하고 애플리케이션을 재시작한다.
- sanitizer가 정상 이벤트까지 제거하면 직전 애플리케이션 버전으로 롤백한다.
- tracing은 기본 0이므로 tracing 비용·성능 문제의 별도 롤백은 필요하지 않다.
- Sentry 중단이 시험 생성·채점·Callback 응답을 실패시키지 않도록 reporter는 애플리케이션 비즈니스 흐름과 격리한다.

## 11. 완료 조건

- 실제 DSN과 자격정보가 Git에 없다.
- 예상하지 못한 5xx가 정확히 1건 수집된다.
- 같은 5xx가 CloudWatch 안전한 ERROR로 정확히 1건 기록된다.
- Sentry LogbackAppender가 비활성화되어 일반 ERROR 로그가 자동 Issue로 승격되지 않는다.
- grading dispatch·Callback ERROR는 초기 Sentry event를 만들지 않는다.
- Sentry event에 애플리케이션 stack frame이 있다.
- 최종 직렬화 event에 exception message, 가짜 marker, 실제 userId, 인증 정보, URL 서명값, S3 Key와 payload가 없다.
- 4xx는 Sentry event가 되지 않는다.
- environment와 `app-back-end-learning-core@<git-sha>` release가 CI와 ECS runtime의 배포값과 일치한다.
- Sentry 프로젝트의 IP 주소 저장 방지 설정이 활성화되어 있다.
- Sentry 전송 실패가 공개 API 응답과 채점 흐름에 영향을 주지 않는다.
- 오류 event 외 attachment를 전송하지 않으며 tracing 활성화 전 별도 transaction sanitizing 검토를 거친다.
- 자동 테스트는 실제 외부 Sentry를 호출하지 않는다.
- 기존 공개 API·AI/Callback·Redis/S3 계약이 유지된다.
- 집중 테스트, 전체 `./gradlew clean test`, `git diff --check`가 성공한다.

## 12. 확정된 정책

1. Sentry는 예외 관리, CloudWatch는 운영 로그 관리에 사용한다.
2. `sentry.logging.enabled=false`로 Logback 자동 event 전송을 끈다.
3. 예상하지 못한 5xx만 명시적 `captureException`으로 보내고 grading·Callback ERROR는 초기 CloudWatch 전용으로 둔다.
4. sanitizer는 실패 시 원본 event를 보내지 않고 drop한다.
5. 현재 7.14.0에서 동작을 고정한 후 8.x 업그레이드는 별도 이슈로 진행한다.
6. release 형식은 `app-back-end-learning-core@<git-sha>`로 통일한다.

## 13. 운영 적용 전 확인할 결정

1. staging/prod에서 환경변수를 주입하는 실제 배포 방식
2. CI source context와 ECS runtime에 동일 commit SHA를 주입하는 구체적인 배포 단계
3. 공개 오류 API를 추가하지 않고 실행할 staging synthetic 5xx smoke 방법과 승인 절차
4. 운영 Alert Rule의 알림 채널과 임계값
5. 추후 Sentry로 명시적 승격할 grading·AI·Callback 오류 분류가 있는지

## 14. 별도 위험 메모

현재 전역 500 응답 생성 경로에는 내부 예외 메시지가 전달되는 부분이 있다. 이는 Sentry sanitizing만으로 해결되지 않으며 기존 외부 오류 응답 동작에 영향을 줄 수 있으므로, 본 계획에 묶어 임의 변경하지 않고 별도 보안·호환성 검토 대상으로 기록한다.

## 15. 구현 결과

2026-08-11 기준 저장소 내부 P0 구현과 자동 검증을 완료했다.

- `SENTRY_ENVIRONMENT`·`SENTRY_RELEASE` 환경변수 연결, request body 비수집, resolver order 1과 Logback SentryAppender 비활성화를 설정했다.
- 예상하지 못한 ControllerAdvice 5xx는 명시적 `captureException` 1건과 예외 원문 없는 CloudWatch ERROR 1건을 남긴다. 4xx와 grading·AI dispatch·Callback ERROR는 Logback 자동 전송 경로가 없어 초기 Sentry event 0건이다.
- reporter는 호출별 clone scope에서 request/user/breadcrumb/extra/tag/context뿐 아니라 attachment·session·propagation baggage·replay ID를 초기화하고 안전한 분류와 UUID requestId context만 다시 넣는다.
- `BeforeSendCallback`은 자유 형식 event·exception·request·user·breadcrumb·context와 stack local·절대 경로·source context·mechanism map·unknown field를 제거하며 실패 시 event를 drop한다.
- 기본 resolver는 같은 격리 reporter를 사용하는 `SanitizedSentryExceptionResolver`로 교체했다. `UnhandledExceptionCaptureFilter`는 하위 필터의 `ServletException`·`RuntimeException`을 보고하고 request attribute로 resolver와 중복 수집을 막는다. 연결 종료 가능성이 있는 `IOException`은 자동 Issue 대상에서 제외한다.
- recording transport에서 event 본문과 envelope header/item 전체를 검사해 예외 메시지, 가짜 민감 marker, parent attachment·session·baggage가 없고 애플리케이션 stack frame은 남는 것을 확인했다.
- 집중 테스트와 최종 `./gradlew clean test --no-daemon`이 성공했고 tests/failures/errors/skipped는 `332/0/0/0`이다. `git diff --check`와 민감정보 패턴 정적 검사도 성공했다.

저장소 밖 작업은 아직 남아 있다. 실제 배포 환경의 DSN·environment·release 주입, staging synthetic 5xx smoke, CI와 ECS release 일치 확인, Sentry 프로젝트 IP 저장 방지·Alert Rule 설정은 운영자가 별도로 수행해야 한다. 실제 DSN과 배포 workflow는 이번 구현에서 변경하지 않았다.
