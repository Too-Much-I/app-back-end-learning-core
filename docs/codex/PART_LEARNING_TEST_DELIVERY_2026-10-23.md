# 파트별 학습·청크 테스트 전달

## 1. 결론

- 사용자 승인: feat/part-learning-access-chunks 구현 및 테스트 서버 기능 ON 배포. 운영은 제외.
- LC 유료 본문/음성만 전용 POST part-learning-eligibility 조회, 전용 JWT audience, 기존 재답변 정책 불변.
- 청크 경계·v3→v2 미디어 재사용, 명시 무료 unit 5개 및 학습300회/실패 retry 무차감 구현.
- 새 진도 collection/receipt/worker·실DB 변경은 없다. 원본 작업트리 dirty는 별도 보존.
- 코드 테스트 배포는 완료했지만 기능ON은 미완료다. 브라우저 제어 연결 복구 후 설정 적용이 남았다. 앱 퍼즐·이어 풀기 UI와 실제 유료 사용자 E2E는 별도다.

## 2. 설정

- ANSWER_LEARNING_ENABLED=true, ANSWER_LEARNING_GRADING_ENABLED=true는 기존 test 학습 설정을 유지한다.
- ANSWER_LEARNING_PAID_ACCESS_ENABLED=true, ANSWER_LEARNING_DAILY_SUBMISSIONS=300.
- ANSWER_LEARNING_FREE_UNIT_1..5는 현재 ready 버전의 실제 첫 세트 ID를 확인해 설정한다. 파트별 ID/ready 검증 실패 시 기동을 거절한다.
- dataset: ts-level-bank-40-per-part-20261023-v3 / test version: 20261023-a6d6884c69c9.
- 기존 workload sender/key/Billing HTTPS origin 및 Lambda URL을 재사용한다. 새 IAM·네트워크 권한은 추가하지 않는다.
- test 태스크 현행값을 읽어 학습 설정만 갱신하고 다른 서비스/운영 설정은 변경하지 않는다.

## 3. 검증 이력

- 초기 단위 실행은 신규 JWT purpose의 기존 exhaustive switch 보완이 필요해 compile 실패 후 수정했다.
- 다음 단위 실행의 테스트 mock 재설정1건 실패를 수정했다. 최종 clean test1516건(1515통과/기존1skip) 성공.
- 초기 Mongo 실행은 Docker API1.32 최소버전 오류로 실패했다. 테스트 프로세스 한정 JAVA_TOOL_OPTIONS=-Dapi.version=1.44로 실행 가능했다.
- Mongo의 이전 retry 차감 정책 기대2건을 승인된 무차감 정책으로 교체하고 300/301 경계 테스트를 추가했다.
- Node 배포/마이그레이션211개 통과. 원본 review-2 hash c27b981a1c1ec6f208089a1e3eae02f8f2bcefdaa3196d82fcc35dc70a545b99 유지.
- test 읽기 전용 ECS 점검 exit0: v3 ready manifest, unit200/lesson360/answer1000, 기존 v2 mediaSource 확인. Billing test26의 전용 조회ON·배포COMPLETED 확인.
- 최종 Mongo375건(373통과/기존2skip), 실패0. Swagger의 이전 청크 미제공·retry 차감 설명도 새 정책에 맞췄다. PR67에서 CI 확인 중.
- PR67 최종 CI39901685090 성공(3분35초), develop merge9b4614a. test 배포39902118001 시작. main/prod 제외.
- 추가 DB 읽기 검증 exit0: 문장3011/청크7208 경계 정상, 참조 미디어3459/3459 해소. 원문/키는 출력하지 않았으며 데이터 쓰기 없음.
- 전환 전 test는 task58·v2·daily60·학습/채점ON이었다. 코드 전달 후 현재 task를 다시 읽고 학습 설정만 바꾼다. 복구는 이전 task와 콘텐츠/설정을 함께 되돌리며 다른 서비스 설정을 덮어쓰지 않는다.
- test workflow39902118001 SUCCESS, 새 코드 task59. 공개 health UP·Swagger chunks 존재·무인증 학습401 확인. 첫 설정 적용 시도는 deployment stable assertion에서 변경 없이 중지됐으며 최신 안정 상태 확인 후 재개한다.
- 이후 브라우저 제어 도구 사용 불가·로컬 AWS NoCredentials로 설정 재개 불가. v3/paid-access ON/daily300/freeID 설정은 미적용이며 기존 v2/daily60 유지. 새로운 task 등록·서비스 변경 전 assertion이므로 이 시도로 새 설정은 생성되지 않았다.
- 다음 담당은 현재 test service/image를 다시 조회해 merge9b4614a ECR digest와 대조하고, 위 학습 환경변수만 변경한다. 다른 task 설정/secret/role/port를 그대로 보존한다. 신규 태스크 단일 COMPLETED/healthy 및 실제 앱 무료·유료 콘텐츠/제출을 확인한 뒤 완료로 판정한다.
- 전용 inventory task 두 개 exit0, lc-test-learning-inventory-20261023:1은 INACTIVE로 정리했다. 로그·콘텐츠는 삭제하지 않았다.

### 확인된 무료 unit ID

1. unit:ts-level-bank-40-per-part-20261023-v3:p1_reference_003_variant_2_reading
2. unit:ts-level-bank-40-per-part-20261023-v3:p2_set_021_easy_station_waiting
3. unit:ts-level-bank-40-per-part-20261023-v3:p3_reference_variant_100_q5
4. unit:ts-level-bank-40-per-part-20261023-v3:p4_reference_05_variant_1_q8
5. unit:ts-level-bank-40-per-part-20261023-v3:p5_item_021

## 4. 배포 전후 확인

- test DB ready manifest·원본 미디어 source 및 무료 세트 ID 읽기 검증.
- CI 통과 후 develop 병합으로 기존 test workflow 배포. main/prod로 전달하지 않는다.
- 코드 배포 후 기능ON·v3 설정 적용 및 ECS 단일 안정 상태/health/학습 기동 확인.
- 정상 로그인한 앱에서 무료 상세/청크/음성, 유료402/허용, 제출 미조회·기존 채점·이어 풀기를 검증한다.
- 문제 발생 시 새 버전/설정 전환을 멈추고 이전 태스크/버전으로 복구한다. 권한 확인 장애를 임의 무료 허용으로 바꾸지 않는다.
