# 파트별 학습·청크 테스트 전달

## 1. 결론

- 사용자 승인: feat/part-learning-access-chunks 구현 및 테스트 서버 기능 ON 배포. 운영은 제외.
- LC 유료 본문/음성만 전용 POST part-learning-eligibility 조회, 전용 JWT audience, 기존 재답변 정책 불변.
- 청크 경계·v3→v2 미디어 재사용, 명시 무료 unit 5개 및 학습300회/실패 retry 무차감 구현.
- 새 진도 collection/receipt/worker·실DB 변경은 없다. 원본 작업트리 dirty는 별도 보존.
- 아래 검증·실배포 결과는 진행 상태에 따라 추가한다. 앱 퍼즐·이어 풀기 UI와 실제 유료 사용자 E2E는 별도다.

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
