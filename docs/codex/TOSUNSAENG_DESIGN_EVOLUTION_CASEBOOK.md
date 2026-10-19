# 토선생: 구현 후 수정·설계 진화 사례집

- 조사일: 2026-09-07. 코드 기준: Learning Core `develop@cb5f6ee`.
- 목적: “무엇을 구현했나”에 더해 **처음 방식 → 변경 계기 → 수정 이유 → 새 방식 → 대가와 검증**을 포트폴리오 소재로 복원한다.
- 범위: Learning Core의 WORKLOG·현재 소스·테스트·로컬 Git 이력, 저장된 서비스 간 계약·상품 결정 문서. Identity/Billing 전체 변경 이력이나 모든 채팅을 새로 전수 조사한 것은 아니다.
- 연결: [포트폴리오 목록](TOSUNSAENG_PORTFOLIO_CONTENT_INVENTORY.md), [트러블슈팅](TOSUNSAENG_TROUBLESHOOTING_CASEBOOK.md), [팀원 회고 대조](TOSUNSAENG_TEAM_RETROSPECTIVE_BACKEND_SUPPLEMENT.md).
- 표기: **[구현]** 코드·수정 기록 확인, **[결정]** 사용자 요구·계약 결정, **[추론]** 코드에서 해석한 의미, **[미확인]** 직접 동기·운영 성과 증거 부족. 구현 확인은 배포 확인과 다르다.

## 1. 5줄 결론

1. **[구현]** 세션 재사용에서 매 시작 새 세션으로 바꾸고, Billing 도입 후 같은 시작 요청의 재전송은 같은 Session으로 수렴시키는 변화가 있다. [8월 변경 기록](WORKLOG.md:1560), [Saga 목표](BILLING_RESERVATION_SAGA_IMPLEMENTATION_PLAN.md).
2. **[구현·결정]** Part 4는 이미지 URL 방식에서 비정형 표 원본 전달로 바뀌었다. 세 API 불일치와 고정 DTO의 정보 손실 가능성을 해결하는 계약 변경 소재다. [변경·검증 기록](WORKLOG.md:1829), [현재 Converter](../../src/main/java/web/tosunsaeng/domain/exams/converter/ExamConverter.java).
3. **[구현]** 문항·종합 결과를 함께 저장하던 구조를 분리하고, 정렬 없는 조회를 명시적 최신 조회로 바꿨다. MongoDB 선택 자체보다 구체적인 데이터 모델 개선 경험이다. [원인·수정 기록](WORKLOG.md:109), [저장소 분리](WORKLOG.md:146).
4. **[구현]** 채점 완료 판단·Summary 복구·로그·Sentry도 초기 구현 후 수정됐다. 기존 트러블슈팅과 중복 사건으로 세지 않고 변경 이유를 보강했다. [채점 복구](FEEDBACK_GENERATION_RECOVERY_PLAN.md), [관측성 변경](WORKLOG.md:1870), [Sentry 변경](WORKLOG.md:1969).
5. **[분류]** 총 18개 소재 중 EV-01~15는 구현 변경·확장, EV-16~17은 구현 전 계약 보정 후 구현, EV-18은 상품 계획 변경이다. 모두 “완성한 코드를 갈아엎은 사례”로 묶지 않는다. [전체 표](#61-전체-18개-선택표), [상품 결정](WORKLOG.md:8620).

## 2. 내가 반드시 읽어야 하는 내용

가장 먼저 고를 소재는 **EV-03/12 세션·멱등성 진화**, **EV-04 Part 4 표현 변경**, **EV-02 종합 결과 저장소 분리**다. 각각 도메인 의미, 서비스 간 계약, 데이터 책임을 설명하기 좋다.

“처음 코드를 잘못 짰다”로 시작할 필요는 없다. 예를 들어 세션 재사용은 이어풀기에 적합하지만 새로 시작한다는 제품 의도와는 다를 수 있다. 새 세션 정책도 결제·무료권이 붙으면 네트워크 재전송을 구별해야 한다. 중요한 것은 **새 조건을 발견했을 때 어떤 경계를 다시 정의했는지**다. 다만 초기 설계 의도가 명시되지 않은 경우에는 이를 당시 본인의 생각이라고 단정하지 않는다.

각 사례는 다음 여섯 질문에 답할 수 있을 때 본문에 넣는다.

1. 이전 코드가 실제로 어떻게 동작했는가?
2. 장애·리뷰·요구사항 중 무엇이 변경을 촉발했는가?
3. 유지하거나 다른 대안을 쓰지 않고 왜 이 방향으로 바꿨는가?
4. 무엇은 유지하고 무엇은 승인받아 바꿨는가?
5. 복잡도·배포·데이터 호환성에서 어떤 대가가 생겼는가?
6. 어떤 테스트로 확인했으며 운영 결과는 어디까지 확인했는가?

## 3. 내가 결정해야 하는 사항

- **본인 판단:** 문제 발견, 요구사항 결정, 대안 비교, AI 구현 지시, diff 리뷰, 검증 중 직접 수행한 역할을 선택한다. 코드 작성자 표시만으로 모든 설계 판단을 본인 성과로 확정하지 않는다.
- **직접 동기 보완:** 매번 새 시험으로 바꾼 제품 배경, 표 이미지를 처음 선택한 이유, 유료 상품을 기간제로 바꾼 사업적 이유는 기록보다 본인 설명이 더 필요하다.
- **본문 분량:** 대표 2~3개만 상세하게 쓰고 나머지는 선택 표·부록으로 둔다. 동일 기능의 변화 여러 개를 서로 독립적인 장애 건수로 계산하지 않는다.
- **표현 선택:** “수정했다”, “기존 기능을 확장했다”, “계약 단계에서 보정했다”, “계획을 폐기했다”를 실제 단계에 맞게 사용한다.

## 4. 주요 위험과 미확인 사항

- 커밋 제목과 WORKLOG 날짜는 사건의 정확한 운영 발생일을 보장하지 않는다. 한 커밋에 여러 작업이 들어 있고 중복·종료 훅 기록도 있으므로 사건 수로 중복 집계하지 않았다.
- 변경 전후 코드가 있어도 실제 사용자 불만·사업적 선택 이유까지 자동으로 증명되지는 않는다. 명시되지 않은 이유는 아래에서 추론 또는 미확인으로 표시했다.
- 조회에 쓰는 `_id DESC`는 이 코드가 선택한 legacy 최신성 기준이지 분산 환경에서 실제 생성 시각의 절대 순서를 보장한다는 뜻이 아니다. 이후 결정적 ID·멱등 저장 개선도 함께 존재한다.
- 과거의 “매 요청 신규 생성”은 현재 모든 flag/경로에 무조건 적용되지 않는다. Billing saga 활성화 경로는 같은 Idempotency-Key 재전송을 replay한다.
- Part 4 응답 삭제·대체는 당시 승인된 breaking change다. “모든 수정에서 외부 계약을 전혀 바꾸지 않았다”는 포트폴리오 문장은 부정확하다. 이번 조사 자체는 계약을 변경하지 않는다.
- 무료시험용 Billing Reservation/AttemptGroup 구현과 유료 credit 상품은 다르다. credit 상품 폐기 결정을 근거로 기존 무료시험 saga까지 삭제됐다고 설명하지 않는다.
- 테스트 수는 당시 기록이며 이번에 다시 실행하지 않았다. 성능·비용·장애율·개발시간 개선율은 추가 측정 없이는 쓰지 않는다. 운영 gate는 현재 rollout 문서를 따른다.

## 5. 현재 작업과 직접 관련된 설명

### EV-01. 첫 번째 결과 조회 → 요청 회차 안의 명시적 최신 조회

- **이전 [구현]:** `findByExamId`로 가져온 결과를 Stream으로 필터링해 첫 항목을 반환했다. Azure만 최신 정렬이 있었고 AI 결과는 같은 문항·회차의 중복 문서 중 무엇을 고를지 보장하지 않았다.
- **계기·이유 [기록]:** “현재 문항별 피드백이 최신 결과를 보장하는가”를 조사하며 차이를 발견했다. 최신 retryCount를 요청하는 것과 그 회차 안의 최신 문서를 고르는 것은 별개다.
- **수정 [구현]:** 식별 조건을 Repository 조회로 옮기고 `_id DESC`를 명시했다. 최초 회차는 기존 null을 0으로 해석하는 `[0, null]` 호환을 유지했다.
- **대가·검증:** 최신성 기준을 명시해야 하며 중복 저장 자체의 해결과는 다르다. 구·신 결과 동시 존재와 0/null 조건을 테스트했다.
- **근거:** [조사→구현](WORKLOG.md:109), [현재 조회](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamServiceImpl.java:742), [테스트](../../src/test/java/web/tosunsaeng/domain/exams/application/ExamOwnershipServiceTest.java). 실제 변경 커밋 `f640256`.

### EV-02. 문항·종합 피드백 혼합 저장 → 별도 ExamSummary

- **이전 [구현]:** `exam_results`에 문항 결과와 종합 결과를 함께 저장하고 `totalScore != null` 같은 조건으로 종합 문서를 구분했다.
- **계기·이유 [기록]:** 종합 결과의 최신 조회와 저장 책임 분리를 요구했다. **[추론]** 문항 집계와 시험 종합 상태를 구분하면 결과 의미와 조회 경로를 더 명확하게 설명할 수 있다. DB 성능 장애 때문에 분리했다는 근거는 없다.
- **수정 [구현]:** `exam_summaries`로 종합 쓰기를 분리하고 새 컬렉션 우선 조회, 새 데이터가 없는 시험에만 legacy 종합 결과 fallback을 적용했다. 파트 점수는 문항 데이터에서 계산한다.
- **대가·검증:** 이행 기간에는 두 저장 형식을 읽어야 한다. Callback 저장 대상 분리, 사용자 식별 보존, 새·legacy 조회를 테스트했다. 이후 Summary 유효성·generation·결정적 저장 개선과 구분한다.
- **근거:** [작업 기록](WORKLOG.md:146), [현재 Summary Repository](../../src/main/java/web/tosunsaeng/domain/exams/domain/repository/ExamSummaryRepository.java), [Callback 테스트](../../src/test/java/web/tosunsaeng/domain/exams/application/FeedbackCallbackServiceTest.java). 커밋 `f640256`의 전후 diff 확인.

### EV-03. 진행 중 세션 재사용 → 새 시작과 폐기를 명시

- **이전 [구현]:** TMI-31에서는 진행 중인 시험이 있으면 같은 examId·시험지를 반환하고 Presigned URL만 갱신했다. 동시 요청도 승자 Session으로 수렴했다.
- **계기 [결정]:** 8월 6일 “시험 볼 때마다 무조건 새로운 세션 발급”으로 동작을 바꿨다. **[미확인]** 이를 요청한 구체적 사용자 불편·제품 판단 배경은 해당 기록에 충분히 남아 있지 않다.
- **수정 [구현]:** `findOrCreate`를 `startNew`로 바꾸고 이전 진행 시험을 ABANDONED 처리한 뒤 새 examId를 만든다. 과거 답안·음성·Job을 복사하지 않고 폐기 시험의 늦은 Callback도 no-op 처리한다.
- **의미·대가 [추론]:** “이어풀기”와 “처음부터 새 응시”를 분리한 도메인 정책 변경이다. 새로운 활성 Session을 만드는 것뿐 아니라 이전 비동기 작업의 결과 차단까지 필요하다. 동시 요청에서 각각 새 ID를 만들되 최종 활성 시험은 하나인지 검증했다.
- **현재 경계:** flag off 경로는 `startNew`, Billing saga on은 EV-12를 따른다. 같은 HTTP 전송 재시도를 언제나 새로운 사용자 의도로 보면 안 된다.
- **근거:** [이전 구현](WORKLOG.md:703), [변경 구현](WORKLOG.md:1560), [현재 분기](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamServiceImpl.java:214), [Session 테스트](../../src/test/java/web/tosunsaeng/domain/exams/application/ExamSessionManagerTest.java). `e3f5280` 전후 diff 확인.

### EV-04. Part 4 표 이미지 → 세 API의 비정형 원본 표 데이터

- **이전 [구현]:** 시험 시작·결과 응답은 `tableImageUrl`, prompt는 `tableContext`여서 같은 문항의 표현이 API마다 달랐다. 도메인 표 객체도 고정 필드 DTO였다.
- **계기·이유 [기록·결정]:** 경로 불일치 조사 후 세 API를 tableContext로 통일하기로 했다. 처음 계획은 내부 필드 이름도 변환하는 방식이었지만, 사용자가 임의 중첩 구조를 원본 그대로 전달하도록 보정했다.
- **수정 [구현]:** `Map<String,Object>`로 바꿔 배열·객체·null·임의 키와 내부 snake_case를 보존한다. 외곽 `table_context → tableContext` 매핑만 유지한다. Part 4의 tableImageUrl 응답은 제거했다.
- **대가·검증:** 고정 DTO의 컴파일타임 형태 보장을 포기한다. 하위 의미 검증을 백엔드가 완전히 수행하지 않으므로 콘텐츠·앱의 책임 합의가 필요하다. 실제 Mongo 매핑 및 세 API JSON deep equality를 테스트했다. 배포 순서와 기존 표 데이터 준비가 필요한 breaking change였다.
- **후속 수정:** 전용 결과 변환에서 질문 text가 빠지는 별도 문제가 이후 `514fb49`에서 수정됐다(TS-09). 원본 Map 전환만으로 모든 필드 누락이 해결됐다고 적지 않는다.
- **근거:** [불일치 확인](WORKLOG.md:1749), [계획 보정](WORKLOG.md:1801), [구현](WORKLOG.md:1829), [매핑 테스트](../../src/test/java/web/tosunsaeng/domain/exams/QuestionTableImageMappingTest.java), [Converter](../../src/main/java/web/tosunsaeng/domain/exams/converter/ExamConverter.java). `804b224`의 이미지 대체 제거·Map 변경 diff 확인.

### EV-05. 11번 Callback을 완료 신호로 사용 → 필수 결과 전체를 근거로 판단

- **이전 [구현]:** `fb354b6` 이전에는 11번 문항 Callback 도착을 조건으로 종합 요청을 보냈다.
- **이유 [추론·계약]:** 마지막 문항 번호의 도착과 모든 채점 작업의 완료는 다르다. 비동기 응답 순서와 일부 실패를 고려하면 필수 최초 응시 결과 전체를 확인해야 한다.
- **수정 [구현]:** Question/Summary Job을 나누고 필수 retry 0 완료 조건을 판정하도록 변경했다. 이후 Job만 COMPLETED이고 결과가 없는 경우도 성공으로 보지 않도록 보강했고, Billing-linked terminal 판정은 결과·유효 점수·조회 가능한 Summary를 요구한다.
- **대가·검증:** 완료 조건과 상태 복구가 복잡해지는 대신 마지막 Callback만 믿는 가정을 제거한다. 순서 역전·누락 결과·중복 trigger·terminal 경쟁 테스트가 중요하다.
- **근거:** `fb354b6^`/`fb354b6`의 Callback diff, [현재 채점 서비스](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamGradingService.java), [후속 복구 원인](FEEDBACK_GENERATION_RECOVERY_PLAN.md), [AttemptGroup 계획](ATTEMPT_GROUP_OUTBOX_PUBLISHER_IMPLEMENTATION_PLAN.md). 기존 TS-01·03과 연결.

### EV-06. 같은 Summary 재전송 → transport retry와 새 생성 세대 분리

- **이전·계기 [기록]:** 빈 종합 피드백도 성공 처리됐고, 같은 키 재전송은 AI의 빈 결과 캐시를 다시 받을 수 있었다. 단순 재시도 횟수 증가만으로는 실제 재생성과 늦은 결과 구분이 어렵다.
- **수정 [구현]:** 빈 피드백은 실패로 판정하고, 사용자 명시적 재생성에서 generation을 증가시킨다. 같은 generation의 transport retry와 구분하고 stale Callback을 차단한다. 문항 결과가 모두 있으면 Summary만 재생성한다.
- **대가·계약:** Summary의 generation wire 합의가 필요했다. “AI 계약을 하나도 바꾸지 않고 해결했다”라고 쓰지 않는다. 새 녹음 `retryCount`, dispatch 횟수, Summary generation의 세 의미가 다르다.
- **근거:** [계획의 원인·계약](FEEDBACK_GENERATION_RECOVERY_PLAN.md), [구현 기록](WORKLOG.md:2019), [서비스 테스트](../../src/test/java/web/tosunsaeng/domain/exams/application/ExamGradingServiceTest.java). TS-01·02 보강이며 새 장애 건수가 아니다.

### EV-07. 호출마다 쌓는 진단 로그 → 상태 전이 중심 구조화 로그

- **이전·계기 [기록]:** submit·호출 직전·HTTP 시작/완료·성공 등 같은 작업에 중복 로그가 있었고 실제 사용자 ID·S3 Key·예외 원문 등의 노출 및 검색 일관성 문제를 정리 대상으로 확인했다.
- **수정 [구현]:** 성공·실제 실패 전이 중심으로 집계하고 중복·보조 Callback과 정상 polling은 DEBUG로 낮췄다. event/outcome/reason·duration, requestId와 비동기 MDC 전달을 추가했다. 이후 설명 문장은 한글로 바꾸되 검색용 키는 유지했다.
- **이유·대가:** 로그량 자체보다 실패 단계의 식별과 민감정보 최소화가 목표다. 원문을 제거한 만큼 안전한 분류·예외 타입·추적 문맥이 필요하다. 자동 대시보드·경보 완성을 뜻하지는 않는다.
- **근거:** [분석→정리](WORKLOG.md:1860), [구조화 구현](WORKLOG.md:1894), [요청 필터](../../src/main/java/web/tosunsaeng/global/logging/RequestCorrelationFilter.java), [비동기 전달](../../src/main/java/web/tosunsaeng/global/logging/MdcTaskDecorator.java). `aa68a18`, `798f3b3` 구현 이력. 기존 포트폴리오 로그 소재와 연결.

### EV-08. Sentry 연결 → 예상하지 못한 예외만 stack 보존·정제해 1회 수집

- **이전·계기 [기록]:** Sentry를 붙인 뒤 stack 보존, ERROR 자동 수집·중복, 민감 필드 전파를 다시 검토했다.
- **수정 [구현]:** 일반 grading ERROR는 CloudWatch에 남기고 Logback 자동 Sentry 수집은 끈다. 예기치 않은 5xx를 원본 stack이 있는 예외로 수집하되 allowlist 정제·호출별 scope 격리·중복 방지를 적용했다.
- **대가·검증:** 수집 범위를 줄이면 모든 실패가 Sentry Issue로 뜨지는 않는다. 구조화 로그와 역할 분담이 필요하다. 최종 envelope 민감 marker 부재·stack 보존·1회 수집을 recording transport 테스트로 검증했다.
- **근거:** [구현 기록](WORKLOG.md:1969), [운영 보완 계획](SENTRY_PRODUCTION_HARDENING_PLAN.md), [수집 테스트](../../src/test/java/web/tosunsaeng/global/sentry/SentryPipelineIntegrationTest.java). TS-11과 같은 소재다.

### EV-09. 프로젝트 전용 AWS 고정 키 → Default Credentials Provider

- **이전 [기록]:** S3 설정이 프로젝트 전용 Access/Secret Key property와 static provider에 의존했다.
- **변경 이유 [기록]:** 로컬 JVM·Docker·ECS에서 각 환경의 SDK 표준 인증을 사용하고 앱이 장기 키를 직접 요구하지 않게 전환했다.
- **수정 [구현]:** S3Client/Presigner에 같은 Default Provider를 주입하고 로컬 Profile/SSO, ECS Task Role을 사용하도록 설정·안내를 변경했다. Object Key·URL 만료·업로드 흐름은 유지했다.
- **대가·검증:** profile/SSO 모듈과 Docker mount·권한 차이를 관리해야 한다. Bean 기동 성공이 실제 S3 권한 성공을 뜻하지 않는다. static provider 배제·설정 계약 테스트가 있다.
- **근거:** [전환 기록](WORKLOG.md:1057), [현재 S3Config](../../src/main/java/web/tosunsaeng/global/config/S3Config.java), [테스트](../../src/test/java/web/tosunsaeng/global/config/S3ConfigTest.java), [현재 운영 안내](../../README.md).

### EV-10. 개발 편의 Legacy·미사용 HMAC 코드 → 운영 JWT fail-closed

- **이전·계기 [기록]:** 개발용 인증 기본값이 운영 설정 누락을 숨길 수 있었고, 연결되지 않은 HMAC 필터/provider·의존성이 남아 있었다.
- **수정 [구현]:** local/test Legacy는 유지하되 staging/prod는 JWT와 유효한 issuer/JWKS/audience를 필수 검증한다. 오타·누락 시 Legacy로 fallback하지 않고 기동을 거부한다. 미사용 HMAC 코드만 제거했다.
- **주의·대가:** **실제 운영 인증을 HMAC에서 RS256으로 교체한 사례가 아니다.** 이미 사용하지 않던 경로 정리와 환경 안전성 강화다. 운영 설정 오류는 가용성보다 fail-closed를 우선하게 된다.
- **근거:** [TMI-14 사용처·검증](WORKLOG.md:364), [Startup validator](../../src/main/java/web/tosunsaeng/global/config/auth/AuthStartupValidator.java), [JWT 계약](../contracts/identity-learning-jwt.md). 커밋 `4bc324c`.

### EV-11. 코드에 고정한 AI endpoint → 검증되는 환경별 base URL

- **이전·계기 [기록]:** timeout은 설정 가능했지만 AI 주소는 정적 상수여서 환경별 교체가 불가능했다.
- **수정 [구현]:** `AI_SERVER_URL`을 URI로 바인딩하고 absolute HTTP(S)·host·user-info/query/fragment 부재를 검증했다. `/evaluations`는 정확히 한 번 결합한다.
- **대가·검증:** 코드 수정 없이 배포 환경을 바꿀 수 있지만 설정 오입력을 검증해야 한다. 문항 multipart·Summary JSON과 경로 계약은 유지했다. URL 조합과 다른 host 테스트를 추가했다.
- **근거:** [분석·구현](WORKLOG.md:1432), [설정 검증](../../src/main/java/web/tosunsaeng/global/config/GradingProperties.java), [전송](../../src/main/java/web/tosunsaeng/domain/exams/application/GradingDispatchService.java). 변경 이력 `4ea6ca1`.

### EV-12. 로컬 새 시험 생성 → Billing Reservation Saga와 command 멱등성

- **이전 [구현]:** Learning Core가 새 Session을 만들었고 Billing의 권리 hold·확정과 연결되지 않았다.
- **변경 이유 [계획에 명시]:** 네트워크 재전송에 의한 Session·무료권 중복, Session 저장 실패, confirm 응답 유실을 같은 작업으로 수렴시켜야 했다.
- **수정 [구현]:** operation → reserve → Session Transaction → confirm → finalize로 확장했다. 같은 Idempotency-Key는 같은 시작 의도이며 status 조회로 불명 상태를 복구한다. reserve 실패 전 기존 Session을 폐기하지 않는다.
- **EV-03과 관계:** 새로 응시하려는 의도에는 새 key·examId를 사용하고, 같은 의도의 전송 재시도에는 같은 key를 사용한다. 과거 “항상 새 세션” 정책을 전면 철회한 것이 아니라 그 정책에 command 경계를 추가한 것이다.
- **대가·검증:** 원장·보상·중간 상태·Mongo Transaction·내부 인증이 필요하다. flag off는 기존 무헤더 흐름을 유지한다. timeout/status·동시 중복·rollback 테스트와 운영 failure-injection은 구분한다.
- **근거:** [목표·구현 계획](BILLING_RESERVATION_SAGA_IMPLEMENTATION_PLAN.md), [구현 기록](WORKLOG.md:4385), [현재 Saga](../../src/main/java/web/tosunsaeng/domain/exams/application/BillingExamCreationSaga.java), [테스트](../../src/test/java/web/tosunsaeng/domain/exams/application/BillingExamCreationSagaTest.java). TMI-116, `9241a39`/`c3e3c82` 구현 묶음.

### EV-13. 시험 생성 연동까지만 → 결과 상태의 durable outbox 전달

- **이전 [기록]:** TMI-116에서는 Session의 Billing 연결까지만 구현했고 완료·재응시 가능 상태 publisher는 명시적으로 후속 범위였다. 기존 직접 전송 코드를 교체한 증거는 아니다.
- **변경 이유 [계약·추론]:** Billing이 채점 중·완료·복구 불가를 알아야 다음 시작 권리를 판정할 수 있다. 로컬 상태 저장과 외부 호출을 별도로만 처리하면 장애 구간에서 전달이 누락될 수 있다.
- **수정 [구현]:** 상태와 outbox를 같은 Transaction/CAS로 저장하고 lease publisher가 재전송한다. event ID/payload 유지, terminal 하나, auth circuit·보존 정책을 적용했다.
- **대가·검증:** 즉시 일관성이 아닌 지연 전달과 중복 수신을 고려해야 한다. consumer 멱등성·lease 경쟁·인증 장애·terminal race가 추가 책임이다. “exactly-once 전달 보장”이라고 쓰지 않는다.
- **근거:** [계획·경계](ATTEMPT_GROUP_OUTBOX_PUBLISHER_IMPLEMENTATION_PLAN.md), [구현 기록](WORKLOG.md:6185), [publisher](../../src/main/java/web/tosunsaeng/domain/exams/attemptgroup/application/AttemptGroupOutboxPublisher.java). TMI-118, `63d0f7d`. TS-08의 Transaction hotfix는 후속 결함 수정이다.

### EV-14. 재가입 사용자의 과거 Session 없는 경로 → Billing 권리 continuation

- **이전·계기 [기록]:** target userId에 Session이 없다는 사실만으로 기존 Billing 권리가 없다고 볼 수 없다. 동일 phone 재가입 시 Billing이 승인한 기존 group·시험지를 이어받는 경로가 필요했다.
- **수정 [구현 확장]:** target Session 0건 조건에서 discovery를 호출하고 204는 기존 INITIAL, 200은 PHONE_REJOIN REPLACEMENT로 연결한다. continuation snapshot을 operation에 고정하고 응답 유실은 status-first로 복구한다.
- **대안 경계:** 과거 답안·Session owner를 복사/변경하는 방식과 달리 새 target examId로 처음부터 응시한다. Guest UserMerged의 학습 이력 이전은 별도 기능으로 유지한다. 이전에 phone 데이터 이전 코드까지 완성했다가 폐기한 사례로 쓰지 않는다.
- **대가·검증:** 200/204 strict decode, snapshot 일치, stale context, 보상 대상 검증, 별도 flag가 필요하다.
- **근거:** [검토 이유](WORKLOG.md:7098), [구현](WORKLOG.md:7206), [계획](PHONE_REJOIN_CONTINUATION_IMPLEMENTATION_PLAN.md), [현재 Saga](../../src/main/java/web/tosunsaeng/domain/exams/application/BillingExamCreationSaga.java). TMI-122, `233b63e`.

### EV-15. 초기 History/Retry 목록 → 앱 표시 정보와 고유 재답변 수 보강

- **이전 [기록]:** 초기 History에는 retriedQuestionCount, status/maxScore/startedAt이 없었고 Retries도 점수·완료 시각이 없는 목록이었다.
- **계기·이유 [요청·추론]:** 앱이 필요한 이력 정보를 확인하는 과정에서 필드가 추가됐다. **[추론]** 재답변 “횟수”가 아니라 재답변한 “고유 문항 수”를 알려줘야 여러 회차·Job/legacy 중복으로 부풀지 않는다.
- **수정 [구현]:** retriedQuestionCount를 retryCount≥1의 고유 questionNumber 수로 산정하고 status·고정 만점 200·startedAt을 추가했다. batch 조회를 유지했다.
- **주의·검증:** 처음부터 batch 조회였으므로 “N+1 장애를 발견해 없앴다”는 근거는 없다. 추가된 응답 필드와 기존 총점/시간 의미를 API 테스트로 검증했다. pagination까지 구현했다고 표현하지 않는다.
- **근거:** [초기 구현](WORKLOG.md:1307), [고유 문항 수](WORKLOG.md:1690), [추가 필드](WORKLOG.md:1718), [읽기 서비스](../../src/main/java/web/tosunsaeng/domain/exams/application/ExamReadService.java), [API 테스트](../../src/test/java/web/tosunsaeng/domain/exams/api/ExamReadApiContractTest.java). TMI-61.

### EV-16. Challenge 진행 종료 수 → 실제 제출 수 — 구현 전 정책 보정

- **이전 [계획]:** 만료로 진행이 종료된 문제도 풀이 수에 포함하는 설계였다. 이전 runtime이 운영된 증거는 없다.
- **계기·이유 [결정]:** 사용자가 “미제출 만료를 제외하고 실제 제출한 문제만 센다”고 요청했다.
- **보정 후 구현:** `solvedQuestionCount`는 내부 SUBMITTED만 세고 EXPIRED·단순 업로드는 제외한다. 무음·AI 실패는 제출 자체는 접수됐으므로 포함한다. 진행 종료와 학습 참여를 다른 기준으로 둔다.
- **대가·검증:** 하루 진행이 completed여도 실제 풀이 수는 0일 수 있어 앱이 목록 길이로 참여를 계산하면 안 된다. 전체 만료·일부 제출·replay 케이스가 필요하다.
- **근거:** [계약 수정 당시 runtime 미구현 명시](WORKLOG.md:8956), [현재 집계](../../src/main/java/web/tosunsaeng/domain/challenge/ChallengeService.java:35), [통합 테스트](../../src/mongoIntegrationTest/java/web/tosunsaeng/domain/challenge/ChallengeMongoIntegrationTest.java). TMI-126, 최종 구현 `4050ee3`.

### EV-17. 개별 timeout 합의 → 접수 전 총 시간 예산·최종 상태 고정 — 구현 전 보완

- **이전 [계획]:** 연결·접수·Callback 대기와 generation 횟수는 정했지만 접수 전 전체 예산·최종 실패 후 늦은 결과 정책은 미확정이었다.
- **계기·이유 [기록·추론]:** 재전송·backoff·재시작이 개별 timeout 밖에서 전체 대기를 늘릴 수 있고, 늦은 Callback이 최종 실패를 뒤집을 수 있어 경계를 확정했다.
- **보정 후 구현:** Job 최초 dispatch 기준 총 5분에 연결·응답·backoff를 포함하고 재시작/lease 회수로 늘리지 않는다. 정상 202 뒤 결과 대기는 최초 acceptedAt+120초다. 최종 실패 뒤 known Job의 유효 Callback은 204 no-op이며 검증 오류까지 무조건 허용하지 않는다.
- **대가·검증:** 늦게 도착한 유효 결과도 버릴 수 있다는 UX 선택이다. deadline·Retry-After·terminal 경합 테스트가 필요하다. 이 정책을 기존 모의고사에 그대로 적용했다고 쓰지 않는다.
- **근거:** [정책 확정](WORKLOG.md:9050), [현재 worker](../../src/main/java/web/tosunsaeng/domain/challenge/ChallengeWorker.java), [Callback](../../src/main/java/web/tosunsaeng/domain/challenge/ChallengeCallbackService.java), [계약](../contracts/ten-second-challenge-ai-api.md). TMI-126, 최종 구현 `4050ee3`.

### EV-18. credit 상품 구상 → 5종 비자동갱신 기간제 이용권 — 제품 계획 변경

- **이전 [계획]:** credit 충전·잔액·차감 및 첫 구매 배수 같은 상품 구상이 있었다.
- **계기 [결정]:** 9월 5일 유료 상품을 1·3·7·14·30일 무제한 이용권으로 정했고, credit과 첫 구매 2배는 단순 연기가 아니라 목표 제품에서 제거했다. **[미확인]** 가격 조사·전환율 등 사업적 선택의 직접 근거는 이 기록만으로 확인되지 않는다.
- **바뀐 책임:** Billing 검증 CAPTURED 시점부터 기간을 계산하며 자동 갱신은 하지 않는다. 무료시험 FREE_EXAM_ONCE는 별도다. 횟수/잔액 UI와 권리 검증을 기간·만료 중심으로 다시 설계해야 한다.
- **상태·대가:** 해당 기록은 문서·계약 수정이며 결제 runtime 교체 완료가 아니다. 구매 검증·복원·환불·스토어 E2E는 따로 필요하다. 자동 갱신을 제외해도 결제 구현 전체가 간단해졌다는 뜻은 아니다.
- **근거:** [선택 과정](WORKLOG.md:8564), [최종 제거 결정](WORKLOG.md:8620), [기간제 범위](FIRST_RELEASE_FIXED_TERM_PASS_SCOPE.md). 기간제 전용 신규 Jira는 당시 미정; 기존 TMI-116/118 등의 무료시험 기반을 그대로 폐기한 것이 아니다.

## 6. 부록: 상세 조사 근거와 전체 표

### 6.1 전체 18개 선택표

| ID | 전 → 후 | 변경 성격 | 이유 근거 수준 | 포트폴리오 포인트 |
|---|---|---|---|---|
| EV-01 | 정렬 없는 첫 결과 → 회차 내 최신 조회 | 구현 수정 | 코드 공백·수정 목적 명시 | 조회 의미·legacy 호환 |
| EV-02 | 문항/종합 혼합 → Summary 컬렉션 | 구현 수정 | 분리·최신 조회 요청 | 데이터 책임·점진 이행 |
| EV-03 | 진행 Session 재사용 → 새 시작·폐기 | 제품 정책에 따른 구현 수정 | 동작 결정 확인, 직접 UX 동기 미확인 | lifecycle·늦은 Callback |
| EV-04 | 표 이미지/고정 DTO → 원본 Map | 계약·구현 수정 | API 불일치·원본 보존 결정 | 표현 책임·breaking change |
| EV-05 | Q11 도착 → 필수 결과 완료 evidence | 구현 수정 | 이전 코드·승인 조건 확인 | 비동기 순서·완료 판정 |
| EV-06 | 같은 Summary 재전송 → 생성 세대 | 구현 수정 | 빈 결과·캐시·stale 위험 명시 | retry 의미·멱등성 |
| EV-07 | 호출별 중복 로그 → 상태 이벤트 | 구현 수정·보강 | 노이즈·노출 후보 분석 | 관측성·개인정보 |
| EV-08 | Sentry 연결 → 분류·정제·단일 수집 | 구현 수정 | stack·중복·민감정보 검토 | 진단성과 수집 범위 |
| EV-09 | static key → Default Provider | 구현 수정 | 환경별 인증 전환 목적 | 운영 보안 |
| EV-10 | 운영 Legacy 위험·미사용 HMAC → fail-closed | 구현 수정 | 사용처 분석·기동 정책 | 안전한 환경 경계 |
| EV-11 | AI 고정 주소 → 설정 URI | 구현 수정 | 환경 교체 불가 확인 | 설정·wire 계약 분리 |
| EV-12 | 로컬 시험 생성 → Reservation Saga | 기존 구현 확장 | 중복·부분 실패 목표 명시 | 분산 정합성 |
| EV-13 | 생성 연동만 → 상태 outbox | 기존 구현 확장 | 후속 범위·전달 계약 명시 | 원자 저장·중복 전달 |
| EV-14 | target 이력 없는 생성 → continuation | 기존 구현 확장 | Billing 권리/학습 데이터 경계 | 재가입 lifecycle |
| EV-15 | 초기 이력 목록 → 앱 필드·고유 문항 수 | 기존 구현 확장 | 필드 요청, UX 해석은 추론 | 제품 의미·batch 유지 |
| EV-16 | 만료 포함 풀이 수 → 실제 제출 수 | 구현 전 계약 보정, 최종 구현 확인 | 사용자 결정 명시 | 진행과 학습 성과 구분 |
| EV-17 | 개별 timeout → 총 예산·terminal 고정 | 구현 전 계약 보완, 최종 구현 확인 | 미확정 경계·최종 결정 | bounded retry·UX 선택 |
| EV-18 | credit → 기간제 pass | 상품 계획 변경 | 결정 명시, 사업 동기 미확인 | 범위 조정·권리 모델 |

### 6.2 수정처럼 보여도 구분해야 하는 기록

- 9월 3일 “미완료 시험 무료 재시작”은 **기능 변경이 아니라 잘못된 설명 문서의 정정**이었다. OPEN group의 동일 consumption·group·mockExam REPLACEMENT 정책을 이미 확정한 계약에 맞췄다. 구현을 바꿨다고 쓰지 않는다. [기록](WORKLOG.md:7966).
- CI flaky, Mongo aborted transaction, Part 4 text 누락 등은 [기존 TS 사례집](TOSUNSAENG_TROUBLESHOOTING_CASEBOOK.md)의 결함 수정이다. 설계 진화 소재와 함께 쓸 수 있지만 같은 diff를 별도 장애·성과로 중복 집계하지 않는다.
- “동기 채점 → 비동기 완료”의 최초 운영 장애는 사용자 진술·기존 조사에 있다. 이번 새 근거는 Callback 완료 조건·Summary 예약·멱등성 보강이며, thread dump나 당시 스레드 고갈 수치를 새로 확보한 것은 아니다.
- 예전 release 계획의 “credit 동결”·“Challenge 미구현”은 당시 상태다. credit은 9월 5일 목표 제품에서 제거됐고 Challenge는 9월 7일 로컬 구현·검증 완료다. 과거 문서를 현재 상태로 복사하지 않는다.

### 6.3 면접용 문장 후보 — 본인 역할 확인 후 사용

- **세션 진화:** “처음에는 진행 중인 시험을 재사용했지만 새로 응시하는 흐름에 맞춰 폐기와 새 세션 생성을 명시했습니다. 이후 사용권을 연결하면서 새로운 시작 의도와 네트워크 재전송을 구분해야 했고, Idempotency-Key와 Reservation Saga로 같은 요청의 중복 생성을 제어했습니다.” EV-03·12.
- **표 데이터:** “Part 4가 API마다 이미지 또는 표 객체로 다르게 제공되고 있었습니다. 임의 구조를 보존한다는 계약을 확정해 세 경로를 원본 Map 전달로 통일했고, Mongo 매핑과 API JSON 동등성을 검증했습니다. 대신 하위 스키마 보장과 프론트 배포 순서를 별도 책임으로 관리했습니다.” EV-04.
- **저장소 분리:** “문항 결과와 종합 결과를 같은 컬렉션에서 조건으로 구분하던 구조를 분리했습니다. 종합 데이터의 쓰기·조회 책임을 분명히 하고, 기존 시험은 fallback으로 조회해 데이터 이행 중 접근을 유지했습니다.” EV-02.
- **로그 진화:** “장애 진단을 위해 로그를 늘린 뒤 중복 이벤트와 민감정보 위험을 다시 검토했습니다. 상태 전이·원인·소요 시간 중심으로 정리하고 비동기 문맥을 전달해, 로그량보다 필요한 사건을 추적하는 데 집중했습니다.” EV-07.

### 6.4 조사 방법·검증·남은 작업

- WORKLOG에서 결정·이전 구현·수정·검증 순서를 확인하고, 핵심 `f640256`, `e3f5280`, `804b224`, `fb354b6`의 실제 diff/부모 코드를 대조했다. 그 밖의 커밋은 관련 이력이며 모든 커밋 전체 diff를 읽었다는 뜻은 아니다.
- 현재 Repository/Converter/Session·Saga/설정/Challenge 코드와 테스트를 읽고 근거 파일·지정 행의 존재와 문서 whitespace를 검사한다. 커밋 제목만으로 원인이나 변경 범위를 판단하지 않는다.
- 이번 변경은 이 문서, 포트폴리오 목록·TS 사례집 탐색 링크, CURRENT_STATE와 WORKLOG EOF append뿐이다. runtime·API·AI/S3/Redis 계약, 운영 DB·AWS, 다른 저장소는 변경하지 않는다. 기존 dirty 변경은 보존한다.
- 문서 작업이므로 Gradle 테스트를 새로 실행하지 않는다. 당시 테스트 결과와 이번 문서 검증을 구분하며 이번 작업 자체에 서비스 배포는 필요 없다.
- 다음 단계는 대표 소재 선택·본인 역할·직접 동기 확인이다. 운영 성과 수치와 현재 기능 flag/배포 여부는 별도 근거가 필요하다.
