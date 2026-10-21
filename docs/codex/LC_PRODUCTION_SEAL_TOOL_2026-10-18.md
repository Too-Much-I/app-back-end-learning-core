# 운영 목록 봉인 도구·복구 검증 준비

## 후속: 실제 로컬 Mongo 검증 완료 (2026-10-18)

- 기본 sandbox의 Docker/Gradle 접근 제한은 승인된 권한 실행으로 해결했다. 기존 로컬 mongo:7.0.14 이미지를 사용해 loopback 전용 임시 단일노드 replica set `lcseal`을 생성했다. 운영 URI/Secret 사용 없음.
- `seal-production-legacy.mongo-test.js`는 localhost/directConnection만 허용하고 hello의 setName/primary, 빈 DB를 확인한다. 테스트는 production 대상 검증을 완화하지 않고 별도 VM에서 동일 tool 함수를 로컬 연결에만 전달한다. API/URI 승인 검사는 Node 테스트와 구분한다.
- 최초 실제 실행에서 mongosh 커서의 find 결과가 비동기여서 `.limit` 체인이 실패함을 발견했다. 도구의 find/limit/maxTimeMS 단계를 각각 await하도록 수정하고 모의 비동기 커서 회귀1개를 추가했다. 테스트 harness의 top-level await도 async 함수로 수정했다.
- 실제 Mongo9개 통과: snapshot 읽기, active operation 거절, 예상 hash 불일치 무쓰기, entries 후 실패의 실제 Transaction rollback, 실제 commit 뒤 응답 유실 모의/증거 재확인, 비어 있지 않은 목록+Int32+원 시험 불변, replay 최초시각 보존, late writer 거절, 변조 거절. commit 유실은 네트워크 장애가 아닌 commit 후 오류 주입이다.
- 가짜 ID2개(한글 포함)의 실제 도구 hash `ce6c343a0d26f685a9f84054dcc498a69b80dbb6bcfeeede857e39b81c16da8a`를 Java LegacyExamManifest로 독립 대조했다. Java 실제 DB verifySealed 통합 실행까지 의미하지 않는다.
- Node 관련50개 및 Java 대상61개(13+43+5) 실패/skip0. 임시 컨테이너는 --rm stop 후 부재 확인했고 가짜 DB 자료도 제거했다. 기존 Docker 컨테이너·이미지는 변경하지 않았다.
- 실제 prod 서버의 drain·in-flight/Callback·스케줄러 재기동 및 다중노드 failover 리허설은 미완료다. 운영 봉인/DB·배포·기능ON·commit/push 없음. 아래 최초 기록의 Docker 제한 및 실제 Mongo 미실행은 이 후속 결과로 갱신한다.

## 1. 5줄 결론

1. 사용자 후속 구현 요청으로 `scripts/deployment/seal-production-legacy.js`를 추가했다. 실제 DB에는 실행하지 않았다.
2. 기본 inspect는 읽기 전용이며 count/revision/hash만 출력한다. apply는 별도 승인·drain/reader 확인·예상 hash/count가 모두 필요하다.
3. 기존 시험은 수정하지 않고 manifest entries와 SEALED state만 한 Transaction으로 insert한다. 기존/부분 봉인을 덮어쓰지 않는다.
4. 모의 DB 경계22개와 Java 대상 테스트60개 통과. Java에는 프로세스 교체 상황을 모델링한 Summary 복구2개를 추가했다.
5. 실제 replica-set/mongosh 실행·ECS 요청 배출/Callback 리허설은 미완료다. 이 도구만으로 운영 전환 준비 완료를 주장하지 않는다.

## 2. 반드시 읽을 내용

대상은 production / cluster0.nuuovhj.mongodb.net / to-teacher-app로 고정한다. URI는 환경변수 `LC_SEAL_URI`로만 받아 명령행/출력에 넣지 않는다. test 빈 목록 도구는 변경하지 않았다. collection/index는 사전 설치된 것을 검사할 뿐 만들거나 바꾸지 않는다.

이 도구는 분산 writer fence가 아니다. 승인 플래그나 evidence reference는 실제 확인을 대신하지 않는다. 봉인 전부터 새 writer 전환 완료까지 신규 구 writer가 생성할 수 없도록 외부 통제가 유지돼야 한다. readback 이후 새 시험이 생기는 상황까지 도구가 차단하지 않는다.

새 billing/공존 데이터가 이미 생성됐거나 봉인 후 시험이 삭제돼 현재 전체 집합이 달라지면 이 초기 전환 도구의 replay는 실패할 수 있다. 정상 runtime의 삭제 허용과 별개로 보수적으로 중단한다. 이때 봉인 변경/삭제·새 무료 소비로 해결하지 않는다.

## 3. 입력과 출력

로컬/격리 검증 이후 별도 실행 승인 아래 `mongosh --nodb --quiet --file scripts/deployment/seal-production-legacy.js`를 사용한다. 아래는 인터페이스 설명이지 운영 실행 명령 승인이 아니다.

| 환경변수 | 규칙 |
| --- | --- |
| LC_SEAL_URI | exact 운영 SRV URI. TLS 해제/검증 우회 옵션 거절 |
| LC_SEAL_MODE | 기본 inspect, 명시 apply만 쓰기 허용 |
| LC_SEAL_REVISION | 소문자/숫자로 시작, 소문자/숫자/점/밑줄/하이픈 최대80자 |
| LC_SEAL_EXPECTED_SHA256 | apply 필수, 별도 검토한 inspect hash64자리 |
| LC_SEAL_EXPECTED_COUNT | apply 필수,0~100000 정수 문자열 |
| LC_SEAL_APPLY_APPROVED | apply에 exact true 필요 |
| LC_SEAL_WRITERS_DRAINED | 실제 모든 생성 writer 배출 확인 뒤 true |
| LC_SEAL_READER_COVERAGE | 배포할 reader/lifecycle 검증 증거 확인 뒤 true |
| LC_SEAL_DRAIN_EVIDENCE_REF | 외부 작업 기록의 비밀 아닌 영문/숫자/점/밑줄/하이픈 식별자1~128자 |

inspect는 `INSPECTED_NOT_APPLIED`, apply+readback은 `SEALED_VERIFIED`를 출력한다. summary의 `writerDrainVerified:false`는 inspect가 실제 drain을 입증하지 않는다는 뜻이다. 실패는 일반 `STOPPED_REQUIRES_REVIEW`, commit 응답 불명은 `COMMIT_OUTCOME_UNKNOWN`만 출력하며 raw exception/URI/개별ID를 출력하지 않는다.

## 4. 봉인·재실행 안전 경계

- snapshot/primary·majority Transaction으로 미완료 operation/activeGuard 부재, 기존 시험의 Billing 연결 부재를 확인한다.필드 존재만이 아닌 non-null 연결을 거절하며 source backfill 없음.
- Java LegacyExamManifest의 UTF-16 정렬·UTF-8 length framing/SHA256 형식 사용. 중복·잘못된 surrogate·제어문자·길이/개수 상한 거절.
- count/hash가 예상값과 다르면 쓰지 않는다. manifest `_id_` 외 index도 보수적으로 거절한다. business collection 옵션과 `_id_` 검사.
- entries+state를 한 Transaction에 넣고 count는 BSON Int32로 저장한다. state의 최초 sealedAt을 replay 때 보존한다.
- commit 불명에 자동 재시도·재insert·복구 삭제 없음. 먼저 새 실행의 inspect로 영속 증거를 확인한다. 재검토한 동일 summary의 apply만 동일 봉인으로 재현한다.
- readback 실패는 commit이 취소됐다는 뜻이 아니다. 제한을 해제하지 말고 저장 증거와 late writer를 조사한다. 모의 테스트에서 commit 뒤 새 시험 유입을 감지해 중단하는 케이스 포함.
- 세션 종료/Transaction 한도·충돌·연결 실패는 원문을 출력하지 않고 중단한다. 큰 manifest의 실제 Transaction 용량/시간과 Mongo 구현 적합성은 격리 DB 검증이 선행돼야 한다.

## 5. 실제 요청 배출·복구 리허설 체크리스트

다음은 아직 실행하지 않았다. 하나라도 증거가 없으면 봉인 승인을 내리지 않는다.

1. 실행 prod 이미지와 후보 image/commit 매핑, HTTP/worker 모든 Session writer 확인. 실제 ECS stopTimeout/TG deregistration/Boot override 및 진행 요청 관측 수단 대조.
2. 격리 환경에서 구 생성 요청을 insert 직전 멈춘 채 신규 요청 차단. 후속 요청은 차단되지만 기존 요청은 완료할 수 있음을 재현하고 완료 뒤에만 snapshot을 확정한다. 단순 고정 sleep/건수 반복 비교를 drain 근거로 삼지 않는다.
3. 모든 기존 writer·복구 worker가 다시 쓰지 못하는 상태 및 직접 네트워크 우회 부재를 확인한다. 미완료 operation 존재 시 도구가 거절함을 실제 Mongo에서 검증한다.
4. 생성 외 submit/Callback/요약 Job을 유지한 채 구 태스크 종료→새 태스크 재개를 리허설한다. 이번 단위 테스트는 새 scheduler를 명시 호출한 모델이며 자동 scheduler 연결이나 AI/Callback HTTP 재전송 보장은 아니다.
5. pending queue 유실 후 timeout 경계 재개, PROCESSING timeout 전 비재개/정각 재개, 늦은 이전 작업/Callback의 generation/attempt fence, 삭제/owner deny 및 중복 결과 방지 확인.
6. 비어 있지 않은 격리 replica-set에서 inspect/apply/readback·같은 summary replay·동시 insert conflict·unknown commit 증거 확인. actual mongosh+BSON Int32와 Java verifySealed 대조.

## 6. 수행 검증과 한계

- `node --test scripts/deployment/seal-production-legacy.test.js`:22/22 통과. 실제 Mongo가 아닌 Transaction 모의 adapter.
- `./gradlew test --tests '*SummaryDispatchSchedulerTest' --tests '*PaidExamTestActivationTest' --tests '*LegacyExamManifestTest'`:60개, 실패/오류/skip0. Summary13(신규2 포함), activation43, manifest4.
- Docker socket 기본 접근이 permission denied여서 실제 Docker/replica-set 검증은 이번에 수행하지 않았다. 운영 DB로 대체 검증하지 않았다.
- 런타임 API/성공DTO/AI/S3/Redis 변경 없음. 새 도구/단위 테스트/문서만 추가. 기존 dirty 보존·commit/push·실DB·봉인·배포·기능ON 없음.
