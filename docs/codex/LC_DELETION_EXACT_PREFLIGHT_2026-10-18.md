# LC 삭제 전달 한정 사전검증 도구

> 2026-10-18 후속 사용자 승인으로 운영 실검증은 배포 후 프론트에서 수행한다. 본 도구는 선택적 보조 수단이며 배포 선행 필수가 아니다. BillingDeletionConfiguration의 billingDeliveryVerified 기동 요구는 제거했고, 기존 storage/drain/index 및 인증/worker/fence 요구는 유지했다. 아래 기동 gate 설명은 도구 준비 당시 이력이며 이 후속 결정을 우선한다. 실제 검증 완료 값을 임의로 true로 기록하지 않는다.

## 5줄 결론

1. TMI-193/TMI-217: 전체 scheduler 없이 지정된 기존 삭제 target 하나만 발신하는 별도 main을 준비했다.
2. 기본은 inspect(읽기 전용), send-once는 명시 승인 및 다른 writer 배출 확인이 있어야 최대 한 번 발신한다.
3. 동일 target/user/event/payload SHA256과 prepared/sealed/무TTL, 기존 owner·lease·재시도/Transaction 검사를 유지한다.
4. storage/index/drain gate는 그대로 요구하며 billingDeliveryVerified를 쓰거나 전체 앱 기동 조건을 완화하지 않는다.
5. 실제 운영 실행·정상 검증 Session 생성/삭제는 미실행이다. 검증자료와 Billing 업무 준비는 별도 선행조건이다.

## 반드시 읽을 내용

진입점은 `web.tosunsaeng.domain.learningrecorddeletion.tools.DeletionDeliveryPreflight`다. Spring Boot 앱이나 scheduler를 시작하지 않는다. 준비 당시 후보에는 없었으나 2026-10-19 대조에서 prod10 배포 기록의 JAR와 해당 코드를 포함한 재빌드 hash가 일치했고 `4a7341a`로 커밋했다. 실제 도구 실행 검증은 미완료이며 실행 시 산출물·classpath/hash를 확인한다. [최신 동기화 기록](LC_PRODUCTION_CODE_SYNC_2026-10-19.md). 공개 API/DTO·Billing wire·JWT 규칙은 바뀌지 않는다.

기존 sender의 선택부만 분리했다. 일반 tick의 전체 큐 선택은 그대로 유지하고, 사전검증은 exact target 조회만 한다. 정상 코드로 이미 생성·봉인된 target을 요구하며 event/삭제 operation/Session/owner를 만들어 넣지 않는다. 다른 대상, 원문, 권리, 원장, rollout 값은 수정하지 않는다. 한정 발신도 선택 대상의 attempt/lease/ACK와 기존 owner guard revision을 변경하는 실제 업무 쓰기다.

## 운영자가 확정할 사항

- 삭제 가능한 검증용 계정/시험과 영향 범위, 정상 삭제 command·target 준비 경로를 확정한다. 현재 준비된 운영 target은 없다. 이 도구는 삭제 command를 접수하거나 원문 정리를 실행하지 않는다.
- 동일 환경 Billing 업무/route/OPEN·Identity 선행조건 및 다른 writer 배출은 양측 확인 대상이다. 입력의 승인/drain 값은 자동 검증 결과가 아니라 운영자의 명시 확인이다.
- LC 기존 rollout의 writersDrained/inventoryApproved/writerPathsVerified/storageVerified와 필수 인덱스를 먼저 충족한다. billingDeliveryVerified만 이 한정 도구에서 생성할 증거이며, 실행 뒤에도 자동으로 true를 쓰지 않는다.
- 빈 inventory/모의204/잘못된 body400을 실제 정상 삭제 ACK로 대신하지 않는다. 최초 정상 시험·target을 만들 수 있는 제한된 준비 절차도 아직 실행 승인/완료되지 않았다.

## 입력 및 실행

비밀/식별자는 shell 명령행·로그가 아닌 승인된 실행환경에 주입한다. 파일에 환경변수 값을 저장하거나 문서에 실제 값을 복사하지 않는다.

| 환경변수 | 의미 |
| --- | --- |
| MONGODB_URI | 운영 exact SRV host 및 to-teacher-app DB; 다른 환경 거절 |
| LC_PREFLIGHT_TARGET_ID | 승인된 기존 deletionId:EXAM:sessionId |
| LC_PREFLIGHT_USER_ID | 해당 target의 소유자 |
| LC_PREFLIGHT_EVENT_ID | 최초 영속 event ID |
| LC_PREFLIGHT_PAYLOAD_SHA256 | 최초 canonical payload digest |
| LC_PREFLIGHT_MODE | 미설정/inspect는 읽기, send-once만 발신 가능 |
| LC_PREFLIGHT_APPROVAL | 발신 시 SEND_EXACT_APPROVED_TARGET exact |
| LC_PREFLIGHT_OTHER_WRITERS_DRAINED | 발신 시 true, 배출 근거 별도 보관 |
| BILLING_WORKLOAD_JWT_PRIVATE_JWK | 발신 시에만 필요한 기존 LC 운영 키; inspect는 미파싱 |

서명 환경은 production, origin은 기존 운영 Billing HTTPS 주소로 고정한다. HTTP redirect/fallback 없음. target/environment 불일치·lease/격리/재시도 소진을 해제하지 않는다. 설정된 Mongo client는 primary/majority를 사용한다.

검증된 새 bootJar를 전용 작업 디렉터리에 해제한 경우의 진입 명령 형식은 다음과 같다. 실제 실행은 현재 승인하지 않았고 해당 산출물도 아직 전달하지 않았다.

```sh
java -cp 'BOOT-INF/classes:BOOT-INF/lib/*' web.tosunsaeng.domain.learningrecorddeletion.tools.DeletionDeliveryPreflight
```

## 결과 해석·위험

- PREFLIGHT_READ_ONLY_*: DB의 현재 상태 관측만 의미한다. 운영 HTTP 검증 아님.
- PREFLIGHT_STORED_ACKED: 동일 target의 ACK가 majority readback됐다. 이미 ACKED였던 경우도 포함하므로 이번 HTTP 발신/상대 fact 확인과 구분해 기록한다.
- PREFLIGHT_STORED_PENDING/NEEDS_REVIEW: 성공 아님. 지연·lease·재시도/격리 상태를 그대로 유지한다.
- PREFLIGHT_FAILED_OR_OUTCOME_UNKNOWN 또는 비정상 종료: 실패/성공을 추정하지 않는다. inspect 및 양측 동일 event 근거를 확인하고 자동 새 event/무조건 재전송 금지.
- 출력은 고정 결과 코드뿐이다. 토큰, payload, 식별자, Mongo 예외 원문을 출력하지 않는다. 프로세스 외부의 실행환경/로그 수집도 개인정보 비노출 구성이 필요하다.
- 기존 target TTL·owner 삭제/merge 및 writer 경합을 피하기 위해 전용 승인 구간을 사용한다. 도구만으로 전체 운영 준비 완료를 주장하지 않는다.

## 구현·검증 근거

- `BillingDeletionSender.sendExact`: 선택·불변 연결 검증과 기존 deliver/Tx/lease/ACK 재사용.
- `BillingDeletionConfiguration.verifyDeliveryStorage`: 기존 필수 전달 인덱스/무TTL 검사 공통화. 후속 승인으로 앱의 billingDeliveryVerified 기동 선행 요구는 제거했으며 검증 완료 값을 임의 true로 쓰지는 않는다.
- `DeletionDeliveryPreflightTest`: 기본 inspect, 명시 승인/drain, 모드 오타, 다른 DB 거절.
- `DeletionFlowMongoTest`: 다른 대기 target 무접촉, ACK 재관측, 잘못된 선택, 지연/응답유실 같은 payload, stale version, owner 부재, lease/격리, unknown commit, 환경 불일치. 격리 Mongo와 모의 Billing만 사용.
- 기존 사용자 파일/dirty 보존. 이 변경은 로컬 코드·문서이며 운영 배포/DB 변경·키 변경·commit/push 없음.

### 실행 결과 및 사용자 범위 확인

- 사용자 후속 선택: 전용 계정은 나중에 지정하며 이번에는 도구 준비까지만 수행한다. 실제 운영 main/발신은 실행하지 않았다.
- `./gradlew clean test` 성공, owner 보호 후 최종 `./gradlew test` 성공. 최종 XML 총1403개 중1402통과/1skip/실패0/오류0. skip은 기존 LearningImportedContentTest이며 이번 도구 테스트가 아니다.
- `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew mongoIntegrationTest --tests 'web.tosunsaeng.domain.learningrecorddeletion.DeletionFlowMongoTest' --tests 'web.tosunsaeng.domain.learningrecorddeletion.DeletionWiringMongoTest'`:33개 통과, 실패/오류/skip0. 임시 Mongo7.0.14 및 모의 Billing 사용.
- `git diff --check` 통과. 새 제품 코드·테스트·본 문서 및 준비 상태 기록만 변경했으며 기존 dirty/DS_Store는 보존했다. Gradle 캐시 sandbox 거절 후 승인된 실행으로 검증했으며 시스템 Docker 설정은 바꾸지 않았다.
